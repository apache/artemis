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
package org.apache.activemq.artemis.tests.integration.jms.multiprotocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import javax.jms.Connection;
import javax.jms.JMSException;
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
import org.junit.jupiter.api.Test;

public class JMSSelectorTest extends MultiprotocolJMSClientTestSupport {

   private static final String NORMAL_QUEUE_NAME = "NORMAL";

   @Override
   protected void addConfiguration(ActiveMQServer server) {
      server.getConfiguration().setPersistenceEnabled(false);
      server.getAddressSettingsRepository().addMatch(NORMAL_QUEUE_NAME, new AddressSettings());
   }

   @Override
   protected void createAddressAndQueues(ActiveMQServer server) throws Exception {
      super.createAddressAndQueues(server);

      //Add Standard Queue
      server.addAddressInfo(new AddressInfo(SimpleString.of(NORMAL_QUEUE_NAME), RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(NORMAL_QUEUE_NAME).setRoutingType(RoutingType.ANYCAST));
   }

   @Test
   public void testJMSSelectorsAMQPProducerAMQPConsumer() throws Exception {
      testJMSSelectors(AMQPConnection, AMQPConnection);
   }

   @Test
   public void testJMSSelectorsCoreProducerCoreConsumer() throws Exception {
      testJMSSelectors(CoreConnection, CoreConnection);
   }

   @Test
   public void testJMSSelectorsCoreProducerAMQPConsumer() throws Exception {
      testJMSSelectors(CoreConnection, AMQPConnection);
   }

   @Test
   public void testJMSSelectorsAMQPProducerCoreConsumer() throws Exception {
      testJMSSelectors(AMQPConnection, CoreConnection);
   }

   @Test
   public void testJMSSelectorsOpenWireProducerOpenWireConsumer() throws Exception {
      testJMSSelectors(OpenWireConnection, OpenWireConnection);
   }

   @Test
   public void testJMSSelectorsCoreProducerOpenWireConsumer() throws Exception {
      testJMSSelectors(CoreConnection, OpenWireConnection);
   }

   @Test
   public void testJMSSelectorsOpenWireProducerCoreConsumer() throws Exception {
      testJMSSelectors(OpenWireConnection, CoreConnection);
   }

   @Test
   public void testJMSSelectorsAMQPProducerOpenWireConsumer() throws Exception {
      testJMSSelectors(AMQPConnection, OpenWireConnection);
   }

   @Test
   public void testJMSSelectorsOpenWireProducerAMQPConsumer() throws Exception {
      testJMSSelectors(OpenWireConnection, AMQPConnection);
   }

   public void testJMSSelectors(ConnectionSupplier producerConnectionSupplier, ConnectionSupplier consumerConnectionSupplier) throws Exception {
      testJMSSelector(producerConnectionSupplier, consumerConnectionSupplier, NORMAL_QUEUE_NAME, message -> message.setStringProperty("color", "blue"), "color = 'blue'");
      testJMSSelector(producerConnectionSupplier, consumerConnectionSupplier, NORMAL_QUEUE_NAME, message -> message.setJMSCorrelationID("correlation"), "JMSCorrelationID = 'correlation'");
      testJMSSelector(producerConnectionSupplier, consumerConnectionSupplier, NORMAL_QUEUE_NAME, null, "JMSPriority = 1", Message.DEFAULT_DELIVERY_MODE, 1, Message.DEFAULT_TIME_TO_LIVE);
      testJMSSelector(producerConnectionSupplier, consumerConnectionSupplier, NORMAL_QUEUE_NAME, message -> message.setStringProperty("JMSXGroupID", "groupA"), "JMSXGroupID = 'groupA'");
   }

   public void testJMSSelector(ConnectionSupplier producerConnectionSupplier, ConnectionSupplier consumerConnectionSupplier, String queueName, MessageSetter setValue, String selector) throws Exception {
      testJMSSelector(producerConnectionSupplier, consumerConnectionSupplier, queueName, setValue, selector, Message.DEFAULT_DELIVERY_MODE, Message.DEFAULT_PRIORITY, Message.DEFAULT_TIME_TO_LIVE);
   }

   public void testJMSSelector(ConnectionSupplier producerConnectionSupplier, ConnectionSupplier consumerConnectionSupplier, String queueName, MessageSetter setValue, String selector, int deliveryMode, int priority, long timeToLive) throws Exception {

      sendMessage(producerConnectionSupplier, queueName, setValue, deliveryMode, priority, timeToLive);

      receiveLVQ(consumerConnectionSupplier, queueName, selector);
   }

   private void receiveLVQ(ConnectionSupplier consumerConnectionSupplier, String queueName, String selector) throws JMSException {
      try (Connection consumerConnection = consumerConnectionSupplier.createConnection()) {

         Session consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue consumerQueue = consumerSession.createQueue(queueName);
         MessageConsumer consumer = consumerSession.createConsumer(consumerQueue, selector);
         TextMessage msg = (TextMessage) consumer.receive(1000);
         assertNotNull(msg);
         assertEquals("how are you", msg.getText());
         assertNull(consumer.receiveNoWait());
         consumer.close();
      }
   }

   private void sendMessage(ConnectionSupplier producerConnectionSupplier, String queueName, MessageSetter setValue, int deliveryMode, int priority, long timeToLive) throws JMSException {
      try (Connection producerConnection = producerConnectionSupplier.createConnection()) {
         Session producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queue1 = producerSession.createQueue(queueName);
         MessageProducer p = producerSession.createProducer(null);

         TextMessage message1 = producerSession.createTextMessage();
         message1.setText("hello");
         p.send(queue1, message1);

         TextMessage message2 = producerSession.createTextMessage();
         if (setValue != null) {
            setValue.accept(message2);
         }
         message2.setText("how are you");
         p.send(queue1, message2, deliveryMode, priority, timeToLive);
      }
   }

   public interface MessageSetter {

      void accept(javax.jms.Message message) throws JMSException;
   }
}