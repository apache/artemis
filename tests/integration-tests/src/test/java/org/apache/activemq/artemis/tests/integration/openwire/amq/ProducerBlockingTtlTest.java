/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.tests.integration.openwire.amq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.integration.openwire.BasicOpenWireTest;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.transport.tcp.TcpTransport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProducerBlockingTtlTest extends BasicOpenWireTest {

   ActiveMQQueue queueA = new ActiveMQQueue("QUEUE.A");
   protected ActiveMQConnection flowControlConnection;

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
            }
         }
      } finally {
         super.tearDown();
      }
   }

   //set ttl to 1000
   @Override
   protected String getConnectionUrl() {
      return urlString + "&wireFormat.maxInactivityDuration=1000&wireFormat.maxInactivityDurationInitalDelay=1000";
   }

   @Test
   public void testProducerBlockWontGetTimeout() throws Exception {

      flowControlConnection = (ActiveMQConnection) factory.createConnection();
      Connection consumerConnection = factory.createConnection();
      Thread fillThread = null;
      AtomicBoolean keepGoing = new AtomicBoolean(true);
      try {
         flowControlConnection.start();

         final Session session = flowControlConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         final MessageProducer producer = session.createProducer(queueA);
         producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
         final String text = "Hello World";
         final int num = 10;

         fillThread = new Thread("Fill thread.") {
            @Override
            public void run() {
               try {
                  for (int i = 0; i < num && keepGoing.get(); i++) {
                     producer.send(session.createTextMessage(text + i));
                  }
               } catch (JMSException e) {
               }
            }
         };

         fillThread.start();

         //longer enough than TTL (1000)
         Thread.sleep(4000);

         //receive messages and unblock the producer
         consumerConnection.start();
         Session consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer consumer = consumerSession.createConsumer(queueA);

         for (int i = 0; i < num; i++) {
            TextMessage m = (TextMessage) consumer.receive(5000);
            assertNotNull(m);
            assertEquals("Hello World" + i, m.getText());
         }
         assertNull(consumer.receive(3));

      } catch (Exception e) {
         e.printStackTrace();
      } finally {

         if (fillThread != null) {
            keepGoing.set(false);
            fillThread.interrupt();
            fillThread.join();
         }
         try {
            flowControlConnection.close();
            flowControlConnection = null;
         } catch (Throwable t) {
         }
         try {
            consumerConnection.close();
         } catch (Throwable t) {
         }
      }
   }
}
