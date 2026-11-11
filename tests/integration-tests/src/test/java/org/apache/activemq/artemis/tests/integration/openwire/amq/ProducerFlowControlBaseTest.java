/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.tests.integration.openwire.amq;

import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.integration.openwire.BasicOpenWireTest;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.transport.tcp.TcpTransport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public class ProducerFlowControlBaseTest extends BasicOpenWireTest {
   ActiveMQQueue queueA = new ActiveMQQueue("QUEUE.A");
   ActiveMQQueue queueB = new ActiveMQQueue("QUEUE.B");
   protected ActiveMQConnection flowControlConnection;
   // used to test sendFailIfNoSpace on SystemUsage
   protected final AtomicBoolean gotResourceException = new AtomicBoolean(false);
   private Thread asyncThread = null;


   protected void fillQueue(final ActiveMQQueue queue) throws JMSException, InterruptedException {
      final AtomicBoolean done = new AtomicBoolean(true);
      final AtomicBoolean keepGoing = new AtomicBoolean(true);

      try {
         // Starts an async thread that every time it publishes it sets the done
         // flag to false.
         // Once the send starts to block it will not reset the done flag
         // anymore.
         asyncThread = new Thread("Fill thread.") {
            @Override
            public void run() {
               Session session = null;
               try {
                  session = flowControlConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                  MessageProducer producer = session.createProducer(queue);
                  producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
                  while (keepGoing.get()) {
                     done.set(false);
                     producer.send(session.createTextMessage("Hello World"));
                  }
               } catch (JMSException e) {
               } finally {
                  safeClose(session);
               }
            }
         };
         asyncThread.start();

         waitForBlockedOrResourceLimit(done);
      } finally {
         keepGoing.set(false);
      }
   }

   protected void waitForBlockedOrResourceLimit(final AtomicBoolean done) throws InterruptedException {
      while (true) {
         Thread.sleep(100);
         // the producer is blocked once the done flag stays true or there is a
         // resource exception
         if (done.get() || gotResourceException.get()) {
            break;
         }
         done.set(true);
      }
   }

   protected CountDownLatch asyncSendTo(final ActiveMQQueue queue, final String message) throws JMSException {
      final CountDownLatch done = new CountDownLatch(1);
      new Thread("Send thread.") {
         @Override
         public void run() {
            Session session = null;
            try {
               session = flowControlConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
               MessageProducer producer = session.createProducer(queue);
               producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
               producer.send(session.createTextMessage(message));
               done.countDown();
            } catch (JMSException e) {
               e.printStackTrace();
            } finally {
               safeClose(session);
            }
         }
      }.start();
      return done;
   }

   @Override
   protected void extraServerConfig(Configuration serverConfig) {
      String match = "#";
      Map<String, AddressSettings> asMap = serverConfig.getAddressSettings();
      asMap.get(match).setMaxSizeBytes(1).setAddressFullMessagePolicy(AddressFullMessagePolicy.BLOCK);
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      this.makeSureCoreQueueExist("QUEUE.A");
      this.makeSureCoreQueueExist("QUEUE.B");
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      try {
         if (flowControlConnection != null) {
            TcpTransport t = flowControlConnection.getTransport().narrow(TcpTransport.class);
            try {
               flowControlConnection.getTransport().stop();
               flowControlConnection.close();
            } catch (Throwable ignored) {
               // sometimes the disposed up can make the test to fail
               // even worse I have seen this breaking every single test after this
               // if not caught here
            }
            t.getTransportListener().onException(new IOException("Disposed."));
         }
         if (asyncThread != null) {
            asyncThread.join();
            asyncThread = null;
         }
      } finally {
         super.tearDown();
      }
   }


}
