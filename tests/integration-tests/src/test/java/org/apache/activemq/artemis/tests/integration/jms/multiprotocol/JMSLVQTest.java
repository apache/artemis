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

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.Test;

public class JMSLVQTest extends MultiprotocolJMSClientTestSupport {

   private static final String NORMAL_QUEUE_NAME = "NORMAL";
   private static final String LVQ_QUEUE_NAME = "LVQ";
   private static final String LVQ_CUSTOM_KEY_QUEUE_NAME = "LVQ_CUSTOM_KEY_QUEUE";
   private static final String CUSTOM_KEY = "KEY";

   @Override
   protected void addConfiguration(ActiveMQServer server) {
      server.getConfiguration().setPersistenceEnabled(false);
      server.getConfiguration().setMessageExpiryScanPeriod(1000);
      server.getAddressSettingsRepository().addMatch(NORMAL_QUEUE_NAME, new AddressSettings());
      server.getAddressSettingsRepository().addMatch(LVQ_QUEUE_NAME, new AddressSettings().setDefaultLastValueQueue(true));
      server.getAddressSettingsRepository().addMatch(LVQ_CUSTOM_KEY_QUEUE_NAME, new AddressSettings().setDefaultLastValueQueue(true).setDefaultLastValueKey(SimpleString.of(CUSTOM_KEY)));
   }

   @Override
   protected void createAddressAndQueues(ActiveMQServer server) throws Exception {
      super.createAddressAndQueues(server);

      //Add Standard Queue
      server.addAddressInfo(new AddressInfo(SimpleString.of(NORMAL_QUEUE_NAME), RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(NORMAL_QUEUE_NAME).setRoutingType(RoutingType.ANYCAST));


      //Add LVQ using Default Message.HDR_LAST_VALUE_NAME
      server.addAddressInfo(new AddressInfo(SimpleString.of(LVQ_QUEUE_NAME), RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(LVQ_QUEUE_NAME).setRoutingType(RoutingType.ANYCAST));

      //Add LVQ using Custom Key
      server.addAddressInfo(new AddressInfo(SimpleString.of(LVQ_CUSTOM_KEY_QUEUE_NAME), RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(LVQ_CUSTOM_KEY_QUEUE_NAME).setRoutingType(RoutingType.ANYCAST));
   }


   @Test
   public void testLVQAMQPProducerAMQPConsumer() throws Exception {
      testLVQ(AMQPConnection, AMQPConnection);
   }

   @Test
   public void testLVQCoreProducerCoreConsumer() throws Exception {
      testLVQ(CoreConnection, CoreConnection);
   }

   @Test
   public void testLVQCoreProducerAMQPConsumer() throws Exception {
      testLVQ(CoreConnection, AMQPConnection);
   }

   @Test
   public void testLVQAMQPProducerCoreConsumer() throws Exception {
      testLVQ(AMQPConnection, CoreConnection);
   }

   @Test
   public void testLVQOpenWireProducerOpenWireConsumer() throws Exception {
      testLVQ(OpenWireConnection, OpenWireConnection);
   }

   @Test
   public void testLVQCoreProducerOpenWireConsumer() throws Exception {
      testLVQ(CoreConnection, OpenWireConnection);
   }

   @Test
   public void testLVQOpenWireProducerCoreConsumer() throws Exception {
      testLVQ(OpenWireConnection, CoreConnection);
   }

   @Test
   public void testLVQAMQPProducerOpenWireConsumer() throws Exception {
      testLVQ(AMQPConnection, OpenWireConnection);
   }

   @Test
   public void testLVQOpenWireProducerAMQPConsumer() throws Exception {
      testLVQ(OpenWireConnection, AMQPConnection);
   }

   public void testLVQ(ConnectionSupplier producerConnectionSupplier, ConnectionSupplier consumerConnectionSupplier) throws Exception {
      testLVQDefaultKey(producerConnectionSupplier, consumerConnectionSupplier);
      testLVQCustomKey(producerConnectionSupplier, consumerConnectionSupplier);
   }



   public void testLVQDefaultKey(ConnectionSupplier producerConnectionSupplier, ConnectionSupplier consumerConnectionSupplier) throws Exception {
      testLVQ(producerConnectionSupplier, consumerConnectionSupplier, LVQ_QUEUE_NAME, Message.HDR_LAST_VALUE_NAME.toString());
   }

   public void testLVQCustomKey(ConnectionSupplier producerConnectionSupplier, ConnectionSupplier consumerConnectionSupplier) throws Exception {
      testLVQ(producerConnectionSupplier, consumerConnectionSupplier, LVQ_CUSTOM_KEY_QUEUE_NAME, CUSTOM_KEY);
   }

   public void testLVQ(ConnectionSupplier producerConnectionSupplier, ConnectionSupplier consumerConnectionSupplier, String queueName, String lastValueKey) throws Exception {

      sendLVQ(producerConnectionSupplier, queueName, lastValueKey);

      //Simulate a small pause, else both messages could be consumed if consumer is fast enough
      Thread.sleep(10);

      receiveLVQ(consumerConnectionSupplier, queueName, lastValueKey);
   }

   private void receiveLVQ(ConnectionSupplier consumerConnectionSupplier, String queueName, String lastValueKey) throws JMSException {
      try (Connection consumerConnection = consumerConnectionSupplier.createConnection()) {

         Session consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue consumerQueue = consumerSession.createQueue(queueName);
         MessageConsumer consumer = consumerSession.createConsumer(consumerQueue);
         TextMessage msg = (TextMessage) consumer.receive(1000);
         assertNotNull(msg);
         assertEquals("KEY", msg.getStringProperty(lastValueKey));
         assertEquals("how are you", msg.getText());
         consumer.close();
      }
   }

   private void sendLVQ(ConnectionSupplier producerConnectionSupplier, String queueName, String lastValueKey) throws JMSException {
      try (Connection producerConnection = producerConnectionSupplier.createConnection()) {
         Session producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queue1 = producerSession.createQueue(queueName);
         MessageProducer p = producerSession.createProducer(null);

         TextMessage message1 = producerSession.createTextMessage();
         message1.setStringProperty(lastValueKey, "KEY");
         message1.setText("hello");
         p.send(queue1, message1);

         TextMessage message2 = producerSession.createTextMessage();
         message2.setStringProperty(lastValueKey, "KEY");
         message2.setText("how are you");
         p.send(queue1, message2);
      }
   }

   @Test
   public void testNonDestructiveWithSelector() throws Exception {
      final String MY_QUEUE = RandomUtil.randomUUIDString();
      final boolean NON_DESTRUCTIVE = true;
      server.createQueue(QueueConfiguration.of(MY_QUEUE).setRoutingType(RoutingType.ANYCAST).setNonDestructive(NON_DESTRUCTIVE).setLastValue(true));

      ConnectionSupplier connectionSupplier = CoreConnection;

      Connection consumerConnection1 = connectionSupplier.createConnection();
      Session consumerSession1 = consumerConnection1.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Queue myQueue = consumerSession1.createQueue(MY_QUEUE);
      MessageConsumer consumer1 = consumerSession1.createConsumer(myQueue);
      consumerConnection1.start();

      Connection consumerConnection2 = connectionSupplier.createConnection();
      Session consumerSession2 = consumerConnection2.createSession(false, Session.AUTO_ACKNOWLEDGE);
      myQueue = consumerSession2.createQueue(MY_QUEUE);
      MessageConsumer consumer2 = consumerSession2.createConsumer(myQueue, "foo='bar'");

      Connection producerConnection = connectionSupplier.createConnection();
      Session producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageProducer p = producerSession.createProducer(myQueue);

      for (int i = 0; i < 1000; i++) {
         TextMessage m = producerSession.createTextMessage();
         m.setStringProperty(Message.HDR_LAST_VALUE_NAME.toString(), "abc");
         p.send(m);
         assertNotNull(consumer1.receive(500));
      }
   }
}