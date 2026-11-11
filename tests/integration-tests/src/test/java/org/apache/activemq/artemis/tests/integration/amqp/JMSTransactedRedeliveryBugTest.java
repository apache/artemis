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
package org.apache.activemq.artemis.tests.integration.amqp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.Test;

public class JMSTransactedRedeliveryBugTest extends JMSClientTestSupport {

   private static final String INITIAL_QUEUE_NAME = "InitialQueue";
   private static final String FINAL_QUEUE_NAME = "FinalQueue";

   private static final SimpleString INITIAL_QUEUE_SS = SimpleString.of(INITIAL_QUEUE_NAME);
   private static final SimpleString FINAL_QUEUE_SS = SimpleString.of(FINAL_QUEUE_NAME);

   @Override
   protected void addConfiguration(ActiveMQServer server) {
      server.getAddressSettingsRepository().addMatch(INITIAL_QUEUE_NAME, new AddressSettings().setExpiryAddress(FINAL_QUEUE_SS));
   }

   @Override
   protected void createAddressAndQueues(ActiveMQServer server) throws Exception {
      super.createAddressAndQueues(server);
      server.addAddressInfo(new AddressInfo(INITIAL_QUEUE_SS, RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(INITIAL_QUEUE_SS).setRoutingType(RoutingType.ANYCAST));
      server.addAddressInfo(new AddressInfo(FINAL_QUEUE_SS, RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(FINAL_QUEUE_SS).setRoutingType(RoutingType.ANYCAST));
   }

   @Override
   protected String getJmsConnectionURIOptions() {
      return "amqp.traceFrames=true";
   }

   @Test
   public void testAMQPProducerAMQPConsumer() throws Exception {
      Connection producerConnection = createConnection();
      Connection consumerConnection = createConnection();

      try {
         Session producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue producerQueue = producerSession.createQueue(INITIAL_QUEUE_NAME);
         MessageProducer producer = producerSession.createProducer(producerQueue);

         TextMessage sentMessage = producerSession.createTextMessage();
         sentMessage.setStringProperty("something", "KEY");
         sentMessage.setText("how are you");
         producer.send(sentMessage, DeliveryMode.PERSISTENT, 4, 10);

         // Simulate a small pause, else both messages could be consumed if
         // consumer is fast enough
         Wait.assertTrue("Message should have expired", () -> getProxyToQueue(FINAL_QUEUE_NAME).getMessageCount() == 1);

         Session consumerSession = consumerConnection.createSession(true, Session.SESSION_TRANSACTED);
         Queue consumerQueue = consumerSession.createQueue(FINAL_QUEUE_NAME);
         MessageConsumer consumer = consumerSession.createConsumer(consumerQueue);

         Message msg = consumer.receive(1_000);
         assertNotNull(msg);
         assertEquals("1", msg.getStringProperty("JMSXDeliveryCount"));
         assertEquals("KEY", msg.getStringProperty("something"));
         assertEquals("how are you", ((TextMessage) msg).getText());

         consumerSession.rollback();

         msg = consumer.receive(1_000);
         assertNotNull(msg);
         assertEquals("2", msg.getStringProperty("JMSXDeliveryCount"));
         assertEquals("KEY", msg.getStringProperty("something"));
         assertEquals("how are you", ((TextMessage) msg).getText());

         consumerSession.rollback();

         msg = consumer.receive(1_000);
         assertNotNull(msg);
         assertEquals("3", msg.getStringProperty("JMSXDeliveryCount"));
         assertEquals("KEY", msg.getStringProperty("something"));
         assertEquals("how are you", ((TextMessage) msg).getText());

         consumerSession.rollback();

         msg = consumer.receive(1_000);
         assertNotNull(msg);
         assertEquals("4", msg.getStringProperty("JMSXDeliveryCount"));
         assertEquals("KEY", msg.getStringProperty("something"));
         assertEquals("how are you", ((TextMessage) msg).getText());

         consumerSession.rollback();

         msg = consumer.receive(1_000);
         assertNotNull(msg);
         assertEquals("5", msg.getStringProperty("JMSXDeliveryCount"));
         assertEquals("KEY", msg.getStringProperty("something"));
         assertEquals("how are you", ((TextMessage) msg).getText());

         consumerSession.commit();

         consumer.close();
      } finally {
         producerConnection.close();
         consumerConnection.close();
      }
   }
}