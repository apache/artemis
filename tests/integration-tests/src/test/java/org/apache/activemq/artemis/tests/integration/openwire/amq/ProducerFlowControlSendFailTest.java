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

import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.ResourceAllocationException;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * adapted from: org.apache.activemq.ProducerFlowControlSendFailTest
 */
public class ProducerFlowControlSendFailTest extends ProducerFlowControlBaseTest {

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      super.tearDown();
   }

   @Override
   protected void extraServerConfig(Configuration serverConfig) {
      String match = "#";
      Map<String, AddressSettings> asMap = serverConfig.getAddressSettings();
      asMap.get(match).setMaxSizeBytes(1).setAddressFullMessagePolicy(AddressFullMessagePolicy.FAIL);
   }

   @Test
   public void testPublishWithTX() throws Exception {
      ActiveMQConnectionFactory factory = (ActiveMQConnectionFactory) getConnectionFactory();
      // with sendFail, there must be no flowControllwindow
      // sendFail is an alternative flow control mechanism that does not block
      factory.setUseAsyncSend(true);
      this.flowControlConnection = (ActiveMQConnection) factory.createConnection();
      this.flowControlConnection.start();

      final Session session = this.flowControlConnection.createSession(true, Session.SESSION_TRANSACTED);
      final MessageProducer producer = session.createProducer(queueA);

      int successSent = 0;
      boolean exception = false;
      try {
         for (int i = 0; i < 5000; i++) {
            producer.send(session.createTextMessage("Test message"));
            session.commit();
            successSent++;
         }
      } catch (Exception e) {
         exception = true;
         // with async send, there will be no exceptions
         e.printStackTrace();
      }

      assertTrue(exception);

      // resourceException on second message, resumption if we
      // can receive 10
      MessageConsumer consumer = session.createConsumer(queueA);
      TextMessage msg;
      for (int idx = 0; idx < successSent; ++idx) {
         msg = (TextMessage) consumer.receive(1000);
         assertNotNull(msg);
         if (msg != null) {
            msg.acknowledge();
         }
         session.commit();
      }
      consumer.close();
   }

   @Test
   public void testPublisherRecoverAfterBlockWithSyncSend() throws Exception {
      ActiveMQConnectionFactory factory = (ActiveMQConnectionFactory) getConnectionFactory();
      factory.setExceptionListener(null);
      factory.setUseAsyncSend(false);
      this.flowControlConnection = (ActiveMQConnection) factory.createConnection();
      this.flowControlConnection.start();

      final Session session = this.flowControlConnection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
      final MessageProducer producer = session.createProducer(queueA);

      final AtomicBoolean keepGoing = new AtomicBoolean(true);
      final AtomicInteger exceptionCount = new AtomicInteger(0);
      Thread thread = new Thread("Filler") {
         @Override
         public void run() {
            while (keepGoing.get()) {
               try {
                  producer.send(session.createTextMessage("Test message"));
               } catch (JMSException arg0) {
                  if (arg0 instanceof ResourceAllocationException) {
                     gotResourceException.set(true);
                     exceptionCount.incrementAndGet();
                  }
               }
            }
         }
      };
      thread.start();
      waitForBlockedOrResourceLimit(new AtomicBoolean(false));

      // resourceException on second message, resumption if we
      // can receive 10
      MessageConsumer consumer = session.createConsumer(queueA);
      TextMessage msg;
      for (int idx = 0; idx < 10; ++idx) {
         msg = (TextMessage) consumer.receive(1000);
         if (msg != null) {
            msg.acknowledge();
         }
      }
      assertTrue(5 < exceptionCount.get(), "we were blocked at least 5 times");
      keepGoing.set(false);
   }

   protected ConnectionFactory getConnectionFactory() throws Exception {
      factory.setExceptionListener(arg0 -> {
         if (arg0 instanceof ResourceAllocationException) {
            gotResourceException.set(true);
         }
      });
      return factory;
   }

}
