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
package org.apache.activemq.artemis.tests.integration.divert;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.ActiveMQAddressDoesNotExistException;
import org.apache.activemq.artemis.api.core.ActiveMQIllegalStateException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.client.impl.ClientLargeMessageImpl;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.DivertConfiguration;
import org.apache.activemq.artemis.core.config.TransformerConfiguration;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.impl.DivertBinding;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.ComponentConfigurationRoutingType;
import org.apache.activemq.artemis.core.server.Divert;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.core.server.impl.QueueManagerImpl;
import org.apache.activemq.artemis.core.server.impl.ServiceRegistryImpl;
import org.apache.activemq.artemis.core.server.transformer.AddHeadersTransformer;
import org.apache.activemq.artemis.core.server.transformer.Transformer;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.settings.impl.DeletionPolicy;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.command.ActiveMQTopic;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class DivertTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final int TIMEOUT = 3000;

   @Test
   public void testDivertedNotificationMessagePropertiesOpenWire() throws Exception {
      final String testAddress = ActiveMQDefaultConfiguration.getDefaultManagementNotificationAddress().toString();

      final String forwardAddress = "forwardAddress";

      DivertConfiguration divertConf = new DivertConfiguration().setName("divert1").setRoutingName("divert1").setAddress(testAddress).setForwardingAddress(forwardAddress).setFilterString("_AMQ_NotifType = 'CONSUMER_CREATED' OR _AMQ_NotifType = 'CONSUMER_CLOSED'");

      Configuration config = createDefaultNettyConfig().addDivertConfiguration(divertConf);

      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(config, false));

      server.start();

      ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("tcp://localhost:61616");

      connectionFactory.setClientID("myClientID");

      Topic forwardTopic = new ActiveMQTopic(forwardAddress);
      Connection connection = connectionFactory.createConnection();

      connection.start();

      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      TopicSubscriber subscriber = session.createDurableSubscriber(forwardTopic, "mySubscriptionName");

      javax.jms.Message message = subscriber.receive(DivertTest.TIMEOUT);

      connection.close();

      assertNotNull(message);

      assertEquals("CONSUMER_CREATED", message.getStringProperty("_AMQ_NotifType"));
   }

   @Test
   public void testSingleNonExclusiveDivert() throws Exception {
      final String testAddress = "testAddress";

      final String forwardAddress = "forwardAddress";

      DivertConfiguration divertConf = new DivertConfiguration().setName("divert1").setRoutingName("divert1").setAddress(testAddress).setForwardingAddress(forwardAddress);

      Configuration config = createDefaultInVMConfig().addDivertConfiguration(divertConf);

      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(config, false));

      server.start();

      ServerLocator locator = createInVMNonHALocator();

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, true);

      final SimpleString queueName1 = SimpleString.of("queue1");

      final SimpleString queueName2 = SimpleString.of("queue2");

      session.createQueue(QueueConfiguration.of(queueName1).setAddress(forwardAddress).setDurable(false));

      session.createQueue(QueueConfiguration.of(queueName2).setAddress(testAddress).setDurable(false));

      session.start();

      ClientProducer producer = session.createProducer(SimpleString.of(testAddress));

      ClientConsumer consumer1 = session.createConsumer(queueName1);

      ClientConsumer consumer2 = session.createConsumer(queueName2);

      final int numMessages = 1;

      final SimpleString propKey = SimpleString.of("testkey");

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(false);

         message.putIntProperty(propKey, i);

         producer.send(message);
      }

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer1.receive(DivertTest.TIMEOUT);

         assertNotNull(message);

         assertEquals(i, message.getObjectProperty(propKey));

         assertEquals("forwardAddress", message.getAddress());

         assertEquals("testAddress", message.getStringProperty(Message.HDR_ORIGINAL_ADDRESS));

         message.acknowledge();
      }

      assertNull(consumer1.receiveImmediate());

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer2.receive(DivertTest.TIMEOUT);

         assertNotNull(message);

         assertEquals(i, message.getObjectProperty(propKey));

         assertEquals("testAddress", message.getAddress());

         message.acknowledge();
      }

      assertNull(consumer2.receiveImmediate());
   }

   @Test
   public void testDivertAndQueueWithSameName() throws Exception {
      final String name = RandomUtil.randomUUIDString();

      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(createDefaultInVMConfig()
                                                                             .addDivertConfiguration(new DivertConfiguration()
                                                                                                        .setName(name)
                                                                                                        .setRoutingName(RandomUtil.randomUUIDString())
                                                                                                        .setAddress(RandomUtil.randomUUIDString())
                                                                                                        .setForwardingAddress(RandomUtil.randomUUIDString())), false));

      server.start();

      try {
         server.createQueue(QueueConfiguration.of(name));
         fail();
      } catch (ActiveMQIllegalStateException e) {
         // expected
      }
   }

   @Test
   public void testCrossProtocol() throws Exception {
      final String testForConvert = "testConvert";

      final String testAddress = "testAddress";

      final String forwardAddress = "forwardAddress";

      DivertConfiguration divertConf = new DivertConfiguration().setName("divert1").setRoutingName("divert1").setAddress(testAddress).setForwardingAddress(forwardAddress).
         setRoutingType(ComponentConfigurationRoutingType.ANYCAST);

      Configuration config = createDefaultNettyConfig().addDivertConfiguration(divertConf);

      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(config, false));

      server.start();

      final SimpleString queueName1 = SimpleString.of(testAddress);

      final SimpleString queueName2 = SimpleString.of(forwardAddress);

      { // this is setting up the queues
         ServerLocator locator = createInVMNonHALocator();

         ClientSessionFactory sf = createSessionFactory(locator);

         ClientSession session = sf.createSession(false, true, true);

         session.createQueue(QueueConfiguration.of(queueName1).setAddress(testAddress).setRoutingType(RoutingType.ANYCAST));

         session.createQueue(QueueConfiguration.of(SimpleString.of(testForConvert)).setAddress(testForConvert).setRoutingType(RoutingType.ANYCAST));

         session.createQueue(QueueConfiguration.of(queueName2).setAddress(forwardAddress).setRoutingType(RoutingType.ANYCAST));
      }

      ConnectionFactory coreCF = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616");
      Connection coreConnection = coreCF.createConnection();
      Session coreSession = coreConnection.createSession(Session.AUTO_ACKNOWLEDGE);
      MessageProducer producerCore = coreSession.createProducer(coreSession.createQueue(testForConvert));

      for (int i = 0; i < 10; i++) {
         TextMessage textMessage = coreSession.createTextMessage("text" + i);
         //if (i % 2 == 0) textMessage.setIntProperty("key", i);
         producerCore.send(textMessage);
      }

      producerCore.close();

      ConnectionFactory amqpCF = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:61616");

      Connection amqpConnection = amqpCF.createConnection();
      Session amqpSession = amqpConnection.createSession(Session.AUTO_ACKNOWLEDGE);
      Queue amqpQueue = amqpSession.createQueue(testAddress);
      MessageProducer producer = amqpSession.createProducer(amqpQueue);
      MessageConsumer consumerFromConvert = amqpSession.createConsumer(amqpSession.createQueue(testForConvert));
      amqpConnection.start();

      for (int i = 0; i < 10; i++) {
         javax.jms.Message received = consumerFromConvert.receive(5000);
         assertNotNull(received);
         producer.send(received);
      }


      Queue outQueue = coreSession.createQueue(queueName2.toString());
      MessageConsumer consumer = coreSession.createConsumer(outQueue);
      coreConnection.start();

      for (int i = 0; i < 10; i++) {
         TextMessage textMessage = (TextMessage)consumer.receive(5000);
         assertNotNull(textMessage);
         assertEquals("text" + i, textMessage.getText());
         //if (i % 2 == 0) Assert.assertEquals(i, textMessage.getIntProperty("key"));
      }

      assertNull(consumer.receiveNoWait());

   }

   @Test
   public void testSingleNonExclusiveDivertWithRoutingType() throws Exception {
      final String testAddress = "testAddress";

      final String forwardAddress = "forwardAddress";

      DivertConfiguration divertConf = new DivertConfiguration().setName("divert1").setRoutingName("divert1").setAddress(testAddress).setForwardingAddress(forwardAddress);

      Configuration config = createDefaultInVMConfig().addDivertConfiguration(divertConf);

      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(config, false));

      server.start();

      ServerLocator locator = createInVMNonHALocator();

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, true);

      final SimpleString queueName1 = SimpleString.of("queue1");

      final SimpleString queueName2 = SimpleString.of("queue2");

      session.createQueue(QueueConfiguration.of(queueName1).setAddress(forwardAddress).setRoutingType(RoutingType.ANYCAST).setDurable(false));

      session.createQueue(QueueConfiguration.of(queueName2).setAddress(testAddress).setDurable(false));

      session.start();

      ClientProducer producer = session.createProducer(SimpleString.of(testAddress));

      ClientConsumer consumer1 = session.createConsumer(queueName1);

      ClientConsumer consumer2 = session.createConsumer(queueName2);

      final int numMessages = 1;

      final SimpleString propKey = SimpleString.of("testkey");

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(false);

         message.setRoutingType(RoutingType.MULTICAST);

         message.putIntProperty(propKey, i);

         producer.send(message);
      }

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer1.receive(DivertTest.TIMEOUT);

         assertNotNull(message);

         assertEquals(i, message.getObjectProperty(propKey));

         message.acknowledge();
      }

      assertNull(consumer1.receiveImmediate());

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer2.receive(DivertTest.TIMEOUT);

         assertNotNull(message);

         assertEquals(i, message.getObjectProperty(propKey));

         message.acknowledge();
      }

      assertNull(consumer2.receiveImmediate());
   }

   @Test
   public void testSingleExclusiveDivertWithRoutingType() throws Exception {
      final String testAddress = "testAddress";

      final String forwardAddress = "forwardAddress";

      DivertConfiguration divertConf = new DivertConfiguration().setName("divert1").setRoutingName("divert1").setAddress(testAddress).setForwardingAddress(forwardAddress).setExclusive(true);

      Configuration config = createDefaultInVMConfig().addDivertConfiguration(divertConf);

      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(config, false));

      server.start();

      ServerLocator locator = createInVMNonHALocator();

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, true);

      final SimpleString queueName1 = SimpleString.of("queue1");

      final SimpleString queueName2 = SimpleString.of("queue2");

      session.createQueue(QueueConfiguration.of(queueName1).setAddress(forwardAddress).setRoutingType(RoutingType.ANYCAST).setDurable(false));

      session.createQueue(QueueConfiguration.of(queueName2).setAddress(testAddress).setDurable(false));

      session.start();

      ClientProducer producer = session.createProducer(SimpleString.of(testAddress));

      ClientConsumer consumer1 = session.createConsumer(queueName1);

      final int numMessages = 1;

      final SimpleString propKey = SimpleString.of("testkey");

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(false);

         message.setRoutingType(RoutingType.MULTICAST);

         message.putIntProperty(propKey, i);

         producer.send(message);
      }

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer1.receive(DivertTest.TIMEOUT);

         assertNotNull(message);

         assertEquals(i, message.getObjectProperty(propKey));

         message.acknowledge();
      }

      assertNull(consumer1.receiveImmediate());
   }

   @Test
   public void testSingleDivertWithExpiry() throws Exception {
      final String testAddress = "testAddress";

      final String forwardAddress = "forwardAddress";

      final String expiryAddress = "expiryAddress";

      AddressSettings expirySettings = new AddressSettings().setExpiryAddress(SimpleString.of(expiryAddress));

      DivertConfiguration divertConf = new DivertConfiguration().setName("divert1").setRoutingName("divert1").setAddress(testAddress).setForwardingAddress(forwardAddress);

      Configuration config = createDefaultInVMConfig().addDivertConfiguration(divertConf).clearAddressSettings().addAddressSetting("#", expirySettings);

      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(config, true));

      server.start();

      ServerLocator locator = createInVMNonHALocator();

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, false, false);

      final SimpleString queueName1 = SimpleString.of("queue1");

      final SimpleString queueName2 = SimpleString.of("queue2");

      session.createQueue(QueueConfiguration.of(queueName1).setAddress(forwardAddress));

      session.createQueue(QueueConfiguration.of(queueName2).setAddress(testAddress));

      session.createQueue(QueueConfiguration.of(expiryAddress));

      session.start();

      ClientProducer producer = session.createProducer(SimpleString.of(testAddress));

      ClientConsumer consumer1 = session.createConsumer(queueName1);

      ClientConsumer consumer2 = session.createConsumer(queueName2);

      final int numMessages = 1;

      final SimpleString propKey = SimpleString.of("testkey");

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(true);

         message.putIntProperty(propKey, i);

         message.setExpiration(System.currentTimeMillis() + 1000);

         producer.send(message);
      }
      session.commit();

      // this context is validating if these messages are routed correctly
      {
         int count1 = 0;
         ClientMessage message = null;
         while ((message = consumer1.receiveImmediate()) != null) {
            message.acknowledge();
            count1++;
         }

         int count2 = 0;
         while ((message = consumer2.receiveImmediate()) != null) {
            message.acknowledge();
            count2++;
         }

         assertEquals(1, count1);
         assertEquals(1, count2);
         session.rollback();
      }
      Thread.sleep(2000);

      // it must been expired by now
      assertNull(consumer1.receiveImmediate());
      // it must been expired by now
      assertNull(consumer2.receiveImmediate());

      int countOriginal1 = 0;
      int countOriginal2 = 0;
      ClientConsumer consumerExpiry = session.createConsumer(expiryAddress);

      for (int i = 0; i < numMessages * 2; i++) {
         ClientMessage message = consumerExpiry.receive(5000);
         logger.debug("Received message {}", message);
         assertNotNull(message);

         if (message.getStringProperty(Message.HDR_ORIGINAL_QUEUE).equals("queue1")) {
            countOriginal1++;
         } else if (message.getStringProperty(Message.HDR_ORIGINAL_QUEUE).equals("queue2")) {
            countOriginal2++;
         } else {
            logger.debug("message not part of any expired queue {}", message);
         }
      }

      assertEquals(numMessages, countOriginal1);
      assertEquals(numMessages, countOriginal2);
   }

   @Test
   public void testSingleNonExclusiveDivert2() throws Exception {
      final String testAddress = "testAddress";

      final String forwardAddress = "forwardAddress";

      DivertConfiguration divertConf = new DivertConfiguration().setName("divert1").setRoutingName("divert1").setAddress(testAddress).setForwardingAddress(forwardAddress);

      Configuration config = createDefaultInVMConfig().addDivertConfiguration(divertConf);

      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(config, false));

      server.start();

      ServerLocator locator = createInVMNonHALocator();

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, true);

      final SimpleString queueName1 = SimpleString.of("queue1");

      final SimpleString queueName2 = SimpleString.of("queue2");

      final SimpleString queueName3 = SimpleString.of("queue3");

      final SimpleString queueName4 = SimpleString.of("queue4");

      session.createQueue(QueueConfiguration.of(queueName1).setAddress(forwardAddress).setDurable(false));

      session.createQueue(QueueConfiguration.of(queueName2).setAddress(testAddress).setDurable(false));

      session.createQueue(QueueConfiguration.of(queueName3).setAddress(testAddress).setDurable(false));

      session.createQueue(QueueConfiguration.of(queueName4).setAddress(testAddress).setDurable(false));

      session.start();

      ClientProducer producer = session.createProducer(SimpleString.of(testAddress));

      ClientConsumer consumer1 = session.createConsumer(queueName1);

      ClientConsumer consumer2 = session.createConsumer(queueName2);

      ClientConsumer consumer3 = session.createConsumer(queueName3);

      ClientConsumer consumer4 = session.createConsumer(queueName4);

      final int numMessages = 10;

      final SimpleString propKey = SimpleString.of("testkey");

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(false);

         message.putIntProperty(propKey, i);

         producer.send(message);
      }

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer1.receive(DivertTest.TIMEOUT);

         assertNotNull(message);

         assertEquals(i, message.getObjectProperty(propKey));

         message.acknowledge();
      }

      assertNull(consumer1.receiveImmediate());

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer2.receive(DivertTest.TIMEOUT);

         assertNotNull(message);

         assertEquals(i, message.getObjectProperty(propKey));

         message.acknowledge();
      }

      assertNull(consumer2.receiveImmediate());

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer3.receive(DivertTest.TIMEOUT);

         assertNotNull(message);

         assertEquals(i, message.getObjectProperty(propKey));

         message.acknowledge();
      }

      assertNull(consumer3.receiveImmediate());

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer4.receive(DivertTest.TIMEOUT);

         assertNotNull(message);

         assertEquals(i, message.getObjectProperty(propKey));

         message.acknowledge();
      }

      assertNull(consumer4.receiveImmediate());
   }

   @Test
   public void testSingleNonExclusiveDivert3() throws Exception {
      final String testAddress = "testAddress";

      final String forwardAddress = "forwardAddress";

      DivertConfiguration divertConf = new DivertConfiguration().setName("divert1").setRoutingName("divert1").setAddress(testAddress).setForwardingAddress(forwardAddress);

      Configuration config = createDefaultInVMConfig().addDivertConfiguration(divertConf);

      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(config, false));

      server.start();

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, true);

      final SimpleString queueName1 = SimpleString.of("queue1");

      session.createQueue(QueueConfiguration.of(queueName1).setAddress(forwardAddress).setDurable(false));

      session.start();

      ClientProducer producer = session.createProducer(SimpleString.of(testAddress));

      ClientConsumer consumer1 = session.createConsumer(queueName1);

      final int numMessages = 10;

      final SimpleString propKey = SimpleString.of("testkey");

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(false);

         message.putIntProperty(propKey, i);

         producer.send(message);
      }

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer1.receive(DivertTest.TIMEOUT);

         assertNotNull(message);

         assertEquals(i, message.getObjectProperty(propKey));

         message.acknowledge();
      }

      assertNull(consumer1.receiveImmediate());
   }

   @Test
   public void testSingleExclusiveDivert() throws Exception {
      final String testAddress = "testAddress";

      final String forwardAddress = "forwardAddress";

      DivertConfiguration divertConf = new DivertConfiguration().setName("divert1").setRoutingName("divert1").setAddress(testAddress).setForwardingAddress(forwardAddress).setExclusive(true);

      Configuration config = createDefaultInVMConfig().addDivertConfiguration(divertConf);

      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(config, false));

      server.start();

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, true);

      final SimpleString queueName1 = SimpleString.of("queue1");

      final SimpleString queueName2 = SimpleString.of("queue2");

      final SimpleString queueName3 = SimpleString.of("queue3");

      final SimpleString queueName4 = SimpleString.of("queue4");

      session.createQueue(QueueConfiguration.of(queueName1).setAddress(forwardAddress).setDurable(false));

      session.createQueue(QueueConfiguration.of(queueName2).setAddress(testAddress).setDurable(false));
      session.createQueue(QueueConfiguration.of(queueName3).setAddress(testAddress).setDurable(false));
      session.createQueue(QueueConfiguration.of(queueName4).setAddress(testAddress).setDurable(false));

      session.start();

      ClientProducer producer = session.createProducer(SimpleString.of(testAddress));

      ClientConsumer consumer1 = session.createConsumer(queueName1);

      ClientConsumer consumer2 = session.createConsumer(queueName2);

      ClientConsumer consumer3 = session.createConsumer(queueName3);

      ClientConsumer consumer4 = session.createConsumer(queueName4);

      final int numMessages = 10;

      final SimpleString propKey = SimpleString.of("testkey");

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(false);

         message.putIntProperty(propKey, i);

         producer.send(message);
      }

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer1.receive(DivertTest.TIMEOUT);

         assertNotNull(message);

         assertEquals(i, message.getObjectProperty(propKey));

         assertEquals("forwardAddress", message.getAddress());

         assertEquals("testAddress", message.getStringProperty(Message.HDR_ORIGINAL_ADDRESS));

         message.acknowledge();
      }

      assertNull(consumer1.receiveImmediate());

      assertNull(consumer2.receiveImmediate());

      assertNull(consumer3.receiveImmediate());

      assertNull(consumer4.receiveImmediate());
   }

   @Test
   public void testCompositeDivert() throws Exception {
      final String testAddress = "testAddress";
      final String forwardAddress1 = "forwardAddress1";
      final String forwardAddress2 = "forwardAddress2";
      final String forwardAddress3 = "forwardAddress3";
      final String forwardAddresses = forwardAddress1 + ", " + forwardAddress2 + ", " + forwardAddress3;

      Configuration config = createDefaultInVMConfig().addDivertConfiguration(new DivertConfiguration()
                                                                                 .setName("divert1")
                                                                                 .setRoutingName("divert1")
                                                                                 .setAddress(testAddress)
                                                                                 .setForwardingAddress(forwardAddresses)
                                                                                 .setExclusive(true));

      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(config, false));

      server.start();

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, true);

      final SimpleString queueName1 = SimpleString.of("queue1");
      final SimpleString queueName2 = SimpleString.of("queue2");
      final SimpleString queueName3 = SimpleString.of("queue3");

      session.createQueue(QueueConfiguration.of(queueName1).setAddress(forwardAddress1).setDurable(false));
      session.createQueue(QueueConfiguration.of(queueName2).setAddress(forwardAddress2).setDurable(false));
      session.createQueue(QueueConfiguration.of(queueName3).setAddress(forwardAddress3).setDurable(false));

      session.start();

      ClientProducer producer = session.createProducer(SimpleString.of(testAddress));

      final int numMessages = 10;

      final SimpleString propKey = SimpleString.of("testkey");

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(false);
         message.putIntProperty(propKey, i);
         producer.send(message);
      }

      ClientConsumer consumer1 = session.createConsumer(queueName1);
      ClientConsumer consumer2 = session.createConsumer(queueName2);
      ClientConsumer consumer3 = session.createConsumer(queueName3);

      ClientConsumer[] consumers = new ClientConsumer[] {consumer1, consumer2, consumer3};

      for (int i = 0; i < numMessages; i++) {
         for (int j = 0; j < consumers.length; j++) {
            ClientMessage message = consumers[j].receive(DivertTest.TIMEOUT);
            assertNotNull(message);
            assertEquals(i, message.getObjectProperty(propKey));
            assertEquals("forwardAddress" + (j + 1), message.getAddress());
            assertEquals("testAddress", message.getStringProperty(Message.HDR_ORIGINAL_ADDRESS));
            message.acknowledge();
         }
      }

      assertNull(consumer1.receiveImmediate());
      assertNull(consumer2.receiveImmediate());
      assertNull(consumer3.receiveImmediate());
   }

   @Test
   public void testSinglePersistedDivert() throws Exception {
      final String testAddress = "testAddress";

      final String forwardAddress = "forwardAddress";

      DivertConfiguration divertConf = new DivertConfiguration().setName("divert1").setRoutingName("divert1").setAddress(testAddress).setForwardingAddress(forwardAddress).setExclusive(true);


      QueueConfiguration q1 = QueueConfiguration.of("forwardAddress1").setDurable(true).setRoutingType(RoutingType.ANYCAST);

      Configuration config = createDefaultInVMConfig().addDivertConfiguration(divertConf).addQueueConfiguration(q1);

      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(config, true));

      server.start();

      server.stop();

      divertConf.setRoutingName("divert2");

      server.start();

      Binding divert1 = server.getPostOffice().getBinding(SimpleString.of("divert1"));

      assertNotNull(divert1);

      assertEquals(divert1.getRoutingName(), SimpleString.of("divert2"));
   }

   @Test
   public void testSinglePersistedNewDivert() throws Exception {
      final String testAddress = "testAddress";

      final String forwardAddress = "forwardAddress";

      DivertConfiguration divertConf = new DivertConfiguration().setName("divert1").setRoutingName("divert1").setAddress(testAddress).setForwardingAddress(forwardAddress).setExclusive(true);


      QueueConfiguration q1 = QueueConfiguration.of("forwardAddress1").setDurable(true).setRoutingType(RoutingType.ANYCAST);

      Configuration config = createDefaultInVMConfig().addDivertConfiguration(divertConf).addQueueConfiguration(q1);

      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(config, true));

      server.start();

      server.stop();

      divertConf = new DivertConfiguration().setName("divert1").setRoutingName("divert2").setAddress(testAddress).setForwardingAddress(forwardAddress).setExclusive(true);

      config.getDivertConfigurations().clear();

      config.getDivertConfigurations().add(divertConf);

      server.start();

      Binding divert1 = server.getPostOffice().getBinding(SimpleString.of("divert1"));

      assertNotNull(divert1);

      assertEquals(divert1.getRoutingName(), SimpleString.of("divert2"));
   }

   @Test
   public void testSinglePersistedNoDeleteDivert() throws Exception {
      final String testAddress = "testAddress";

      final String forwardAddress = "forwardAddress";

      DivertConfiguration divertConf = new DivertConfiguration().setName("divert1").setRoutingName("divert1").setAddress(testAddress).setForwardingAddress(forwardAddress).setExclusive(true);


      QueueConfiguration q1 = QueueConfiguration.of("forwardAddress1").setDurable(true).setRoutingType(RoutingType.ANYCAST);

      Configuration config = createDefaultInVMConfig().addDivertConfiguration(divertConf).addQueueConfiguration(q1);

      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(config, true));

      server.start();

      server.stop();

      config.getDivertConfigurations().clear();

      server.start();

      Binding divert1 = server.getPostOffice().getBinding(SimpleString.of("divert1"));

      assertNotNull(divert1);

      assertEquals(divert1.getRoutingName(), SimpleString.of("divert1"));
   }

   @Test
   public void testSinglePersistedDeleteDivert() throws Exception {
      final String testAddress = "testAddress";

      final String forwardAddress = "forwardAddress";

      DivertConfiguration divertConf = new DivertConfiguration().setName("divert1").setRoutingName("divert1").setAddress(testAddress).setForwardingAddress(forwardAddress).setExclusive(true);


      QueueConfiguration q1 = QueueConfiguration.of("forwardAddress1").setDurable(true).setRoutingType(RoutingType.ANYCAST);

      AddressSettings addressSettings = new AddressSettings();

      addressSettings.setConfigDeleteDiverts(DeletionPolicy.FORCE);



      Configuration config = createDefaultInVMConfig().addDivertConfiguration(divertConf).addQueueConfiguration(q1);

      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(config, true));

      server.getAddressSettingsRepository().addMatch(testAddress, addressSettings);

      server.start();

      server.stop();

      config.getDivertConfigurations().clear();

      server.start();

      Binding divert1 = server.getPostOffice().getBinding(SimpleString.of("divert1"));

      assertNull(divert1);
   }

   @Test
   public void testMixedPersistedDeleteDivert() throws Exception {
      final String testAddress = "testAddress";

      final String testAddress2 = "testAddress2";

      final String forwardAddress = "forwardAddress";

      final String forwardAddress2 = "forwardAddress2";

      DivertConfiguration divertConf = new DivertConfiguration().setName("divert1").setRoutingName("divert1").setAddress(testAddress).setForwardingAddress(forwardAddress).setExclusive(true);

      DivertConfiguration divertConf2 = new DivertConfiguration().setName("divert2").setRoutingName("divert2").setAddress(testAddress2).setForwardingAddress(forwardAddress2).setExclusive(true);


      QueueConfiguration q1 = QueueConfiguration.of("forwardAddress1").setDurable(true).setRoutingType(RoutingType.ANYCAST);

      AddressSettings addressSettings = new AddressSettings();

      addressSettings.setConfigDeleteDiverts(DeletionPolicy.FORCE);

      AddressSettings addressSettings2 = new AddressSettings();

      addressSettings2.setConfigDeleteDiverts(DeletionPolicy.OFF);

      Configuration config = createDefaultInVMConfig().addDivertConfiguration(divertConf).addDivertConfiguration(divertConf2).addQueueConfiguration(q1);

      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(config, true));

      server.getAddressSettingsRepository().addMatch(testAddress, addressSettings);

      server.getAddressSettingsRepository().addMatch(testAddress2, addressSettings2);

      server.start();

      server.stop();

      config.getDivertConfigurations().clear();

      server.start();

      Binding divert1 = server.getPostOffice().getBinding(SimpleString.of("divert1"));

      assertNull(divert1);

      Binding divert2 = server.getPostOffice().getBinding(SimpleString.of("divert2"));

      assertNotNull(divert2);
   }


   @Test
   public void testMultipleNonExclusiveDivert() throws Exception {
      final String testAddress = "testAddress";

      final String forwardAddress1 = "forwardAddress1";
      final String forwardAddress2 = "forwardAddress2";
      final String forwardAddress3 = "forwardAddress3";

      DivertConfiguration divertConf1 = new DivertConfiguration().setName("divert1").setRoutingName("divert1").setAddress(testAddress).setForwardingAddress(forwardAddress1);

      DivertConfiguration divertConf2 = new DivertConfiguration().setName("divert2").setRoutingName("divert2").setAddress(testAddress).setForwardingAddress(forwardAddress2);

      DivertConfiguration divertConf3 = new DivertConfiguration().setName("divert3").setRoutingName("divert3").setAddress(testAddress).setForwardingAddress(forwardAddress3);

      Configuration config = createDefaultInVMConfig().addDivertConfiguration(divertConf1).addDivertConfiguration(divertConf2).addDivertConfiguration(divertConf3);

      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(config, false));

      server.start();

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, true);

      final SimpleString queueName1 = SimpleString.of("queue1");

      final SimpleString queueName2 = SimpleString.of("queue2");

      final SimpleString queueName3 = SimpleString.of("queue3");

      final SimpleString queueName4 = SimpleString.of("queue4");

      session.createQueue(QueueConfiguration.of(queueName1).setAddress(forwardAddress1).setDurable(false));

      session.createQueue(QueueConfiguration.of(queueName2).setAddress(forwardAddress2).setDurable(false));

      session.createQueue(QueueConfiguration.of(queueName3).setAddress(forwardAddress3).setDurable(false));

      session.createQueue(QueueConfiguration.of(queueName4).setAddress(testAddress).setDurable(false));

      session.start();

      ClientProducer producer = session.createProducer(SimpleString.of(testAddress));

      ClientConsumer consumer1 = session.createConsumer(queueName1);

      ClientConsumer consumer2 = session.createConsumer(queueName2);

      ClientConsumer consumer3 = session.createConsumer(queueName3);

      ClientConsumer consumer4 = session.createConsumer(queueName4);

      final int numMessages = 10;

      final SimpleString propKey = SimpleString.of("testkey");

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(false);

         message.putIntProperty(propKey, i);

         producer.send(message);
      }

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer1.receive(DivertTest.TIMEOUT);

         assertNotNull(message);

         assertEquals(i, message.getIntProperty(propKey).intValue());

         message.acknowledge();
      }

      assertNull(consumer1.receiveImmediate());

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer2.receive(DivertTest.TIMEOUT);

         assertNotNull(message);

         assertEquals(i, message.getIntProperty(propKey).intValue());

         message.acknowledge();
      }

      assertNull(consumer2.receiveImmediate());

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer3.receive(DivertTest.TIMEOUT);

         assertNotNull(message);

         assertEquals(i, message.getIntProperty(propKey).intValue());

         message.acknowledge();
      }

      assertNull(consumer3.receiveImmediate());

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer4.receive(DivertTest.TIMEOUT);

         assertNotNull(message);

         assertEquals(i, message.getIntProperty(propKey).intValue());

         message.acknowledge();
      }

      assertNull(consumer4.receiveImmediate());
   }

   @Test
   public void testMultipleExclusiveDivert() throws Exception {
      final String testAddress = "testAddress";

      final String forwardAddress1 = "forwardAddress1";
      final String forwardAddress2 = "forwardAddress2";
      final String forwardAddress3 = "forwardAddress3";

      DivertConfiguration divertConf1 = new DivertConfiguration().setName("divert1").setRoutingName("divert1").setAddress(testAddress).setForwardingAddress(forwardAddress1).setExclusive(true);

      DivertConfiguration divertConf2 = new DivertConfiguration().setName("divert2").setRoutingName("divert2").setAddress(testAddress).setForwardingAddress(forwardAddress2).setExclusive(true);

      DivertConfiguration divertConf3 = new DivertConfiguration().setName("divert3").setRoutingName("divert3").setAddress(testAddress).setForwardingAddress(forwardAddress3).setExclusive(true);

      Configuration config = createDefaultInVMConfig().addDivertConfiguration(divertConf1).addDivertConfiguration(divertConf2).addDivertConfiguration(divertConf3);

      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(config, false));

      server.start();

      ServerLocator locator = createInVMNonHALocator();

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, true);

      final SimpleString queueName1 = SimpleString.of("queue1");

      final SimpleString queueName2 = SimpleString.of("queue2");

      final SimpleString queueName3 = SimpleString.of("queue3");

      final SimpleString queueName4 = SimpleString.of("queue4");

      session.createQueue(QueueConfiguration.of(queueName1).setAddress(forwardAddress1).setDurable(false));

      session.createQueue(QueueConfiguration.of(queueName2).setAddress(forwardAddress2).setDurable(false));

      session.createQueue(QueueConfiguration.of(queueName3).setAddress(forwardAddress3).setDurable(false));

      session.createQueue(QueueConfiguration.of(queueName4).setAddress(testAddress).setDurable(false));

      session.start();

      ClientProducer producer = session.createProducer(SimpleString.of(testAddress));

      ClientConsumer consumer1 = session.createConsumer(queueName1);

      ClientConsumer consumer2 = session.createConsumer(queueName2);

      ClientConsumer consumer3 = session.createConsumer(queueName3);

      ClientConsumer consumer4 = session.createConsumer(queueName4);

      final int numMessages = 10;

      final SimpleString propKey = SimpleString.of("testkey");

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(false);

         message.putIntProperty(propKey, i);

         producer.send(message);
      }

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer1.receive(DivertTest.TIMEOUT);

         assertNotNull(message);

         assertEquals(i, message.getIntProperty(propKey).intValue());

         message.acknowledge();
      }

      assertNull(consumer1.receiveImmediate());

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer2.receive(DivertTest.TIMEOUT);

         assertNotNull(message);

         assertEquals(i, message.getIntProperty(propKey).intValue());

         message.acknowledge();
      }

      assertNull(consumer2.receiveImmediate());

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer3.receive(DivertTest.TIMEOUT);

         assertNotNull(message);

         assertEquals(i, message.getIntProperty(propKey).intValue());

         message.acknowledge();
      }

      assertNull(consumer3.receiveImmediate());

      assertNull(consumer4.receiveImmediate());
   }

   @Test
   public void testMixExclusiveAndNonExclusiveDiverts() throws Exception {
      final String testAddress = "testAddress";

      final String forwardAddress1 = "forwardAddress1";
      final String forwardAddress2 = "forwardAddress2";
      final String forwardAddress3 = "forwardAddress3";

      DivertConfiguration divertConf1 = new DivertConfiguration().setName("divert1").setRoutingName("divert1").setAddress(testAddress).setForwardingAddress(forwardAddress1).setExclusive(true);

      DivertConfiguration divertConf2 = new DivertConfiguration().setName("divert2").setRoutingName("divert2").setAddress(testAddress).setForwardingAddress(forwardAddress2).setExclusive(true);

      DivertConfiguration divertConf3 = new DivertConfiguration().setName("divert3").setRoutingName("divert3").setAddress(testAddress).setForwardingAddress(forwardAddress3);

      Configuration config = createDefaultInVMConfig().addDivertConfiguration(divertConf1).addDivertConfiguration(divertConf2).addDivertConfiguration(divertConf3);

      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(config, false));

      server.start();

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, true);

      final SimpleString queueName1 = SimpleString.of("queue1");

      final SimpleString queueName2 = SimpleString.of("queue2");

      final SimpleString queueName3 = SimpleString.of("queue3");

      final SimpleString queueName4 = SimpleString.of("queue4");

      session.createQueue(QueueConfiguration.of(queueName1).setAddress(forwardAddress1).setDurable(false));

      session.createQueue(QueueConfiguration.of(queueName2).setAddress(forwardAddress2).setDurable(false));

      session.createQueue(QueueConfiguration.of(queueName3).setAddress(forwardAddress3).setDurable(false));

      session.createQueue(QueueConfiguration.of(queueName4).setAddress(testAddress).setDurable(false));

      session.start();

      ClientProducer producer = session.createProducer(SimpleString.of(testAddress));

      ClientConsumer consumer1 = session.createConsumer(queueName1);

      ClientConsumer consumer2 = session.createConsumer(queueName2);

      ClientConsumer consumer3 = session.createConsumer(queueName3);

      ClientConsumer consumer4 = session.createConsumer(queueName4);

      final int numMessages = 10;

      final SimpleString propKey = SimpleString.of("testkey");

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(false);

         message.putIntProperty(propKey, i);

         producer.send(message);
      }

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer1.receive(DivertTest.TIMEOUT);

         assertNotNull(message);

         assertEquals(i, message.getIntProperty(propKey).intValue());

         message.acknowledge();
      }

      assertNull(consumer1.receiveImmediate());

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer2.receive(DivertTest.TIMEOUT);

         assertNotNull(message);

         assertEquals(i, message.getIntProperty(propKey).intValue());

         message.acknowledge();
      }

      assertNull(consumer2.receiveImmediate());

      assertNull(consumer3.receiveImmediate());

      assertNull(consumer4.receiveImmediate());
   }

   // If no exclusive diverts match then non exclusive ones should be called
   @Test
   public void testSingleExclusiveNonMatchingAndNonExclusiveDiverts() throws Exception {
      final String testAddress = "testAddress";

      final String forwardAddress1 = "forwardAddress1";
      final String forwardAddress2 = "forwardAddress2";
      final String forwardAddress3 = "forwardAddress3";

      final String filter = "animal='antelope'";

      DivertConfiguration divertConf1 = new DivertConfiguration().setName("divert1").setRoutingName("divert1").setAddress(testAddress).setForwardingAddress(forwardAddress1).setExclusive(true).setFilterString(filter);

      DivertConfiguration divertConf2 = new DivertConfiguration().setName("divert2").setRoutingName("divert2").setAddress(testAddress).setForwardingAddress(forwardAddress2);

      DivertConfiguration divertConf3 = new DivertConfiguration().setName("divert3").setRoutingName("divert3").setAddress(testAddress).setForwardingAddress(forwardAddress3);

      Configuration config = createDefaultInVMConfig().addDivertConfiguration(divertConf1).addDivertConfiguration(divertConf2).addDivertConfiguration(divertConf3);

      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(config, false));

      server.start();

      ServerLocator locator = createInVMNonHALocator();

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, true);

      final SimpleString queueName1 = SimpleString.of("queue1");

      final SimpleString queueName2 = SimpleString.of("queue2");

      final SimpleString queueName3 = SimpleString.of("queue3");

      final SimpleString queueName4 = SimpleString.of("queue4");

      session.createQueue(QueueConfiguration.of(queueName1).setAddress(forwardAddress1).setDurable(false));

      session.createQueue(QueueConfiguration.of(queueName2).setAddress(forwardAddress2).setDurable(false));

      session.createQueue(QueueConfiguration.of(queueName3).setAddress(forwardAddress3).setDurable(false));

      session.createQueue(QueueConfiguration.of(queueName4).setAddress(testAddress).setDurable(false));

      session.start();

      ClientProducer producer = session.createProducer(SimpleString.of(testAddress));

      ClientConsumer consumer1 = session.createConsumer(queueName1);

      ClientConsumer consumer2 = session.createConsumer(queueName2);

      ClientConsumer consumer3 = session.createConsumer(queueName3);

      ClientConsumer consumer4 = session.createConsumer(queueName4);

      final int numMessages = 10;

      final SimpleString propKey = SimpleString.of("testkey");

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(false);

         message.putStringProperty(SimpleString.of("animal"), SimpleString.of("giraffe"));

         message.putIntProperty(propKey, i);

         producer.send(message);
      }

      // for (int i = 0; i < numMessages; i++)
      // {
      // ClientMessage message = consumer1.receive(200);
      //
      // assertNotNull(message);
      //
      // assertEquals((Integer)i, (Integer)message.getProperty(propKey));
      //
      // message.acknowledge();
      // }

      assertNull(consumer1.receiveImmediate());

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer2.receive(DivertTest.TIMEOUT);

         assertNotNull(message);

         assertEquals(i, message.getObjectProperty(propKey));

         message.acknowledge();
      }

      assertNull(consumer2.receiveImmediate());

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer3.receive(DivertTest.TIMEOUT);

         assertNotNull(message);

         assertEquals(i, message.getObjectProperty(propKey));

         message.acknowledge();
      }

      assertNull(consumer3.receiveImmediate());

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer4.receive(DivertTest.TIMEOUT);

         assertNotNull(message);

         assertEquals(i, message.getObjectProperty(propKey));

         message.acknowledge();
      }

      assertNull(consumer4.receiveImmediate());

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(false);

         message.putStringProperty(SimpleString.of("animal"), SimpleString.of("antelope"));

         message.putIntProperty(propKey, i);

         producer.send(message);
      }

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer1.receive(DivertTest.TIMEOUT);

         assertNotNull(message);

         assertEquals(i, message.getObjectProperty(propKey));

         message.acknowledge();
      }

      assertNull(consumer1.receiveImmediate());

      assertNull(consumer2.receiveImmediate());

      assertNull(consumer3.receiveImmediate());

      assertNull(consumer4.receiveImmediate());
   }

   @Test
   public void testRoundRobinDiverts() throws Exception {
      final String testAddress = "testAddress";

      final String forwardAddress1 = "forwardAddress1";
      final String forwardAddress2 = "forwardAddress2";
      final String forwardAddress3 = "forwardAddress3";

      DivertConfiguration divertConf1 = new DivertConfiguration().setName("divert1").setRoutingName("thename").setAddress(testAddress).setForwardingAddress(forwardAddress1);

      DivertConfiguration divertConf2 = new DivertConfiguration().setName("divert2").setRoutingName("thename").setAddress(testAddress).setForwardingAddress(forwardAddress2);

      DivertConfiguration divertConf3 = new DivertConfiguration().setName("divert3").setRoutingName("thename").setAddress(testAddress).setForwardingAddress(forwardAddress3);

      Configuration config = createDefaultInVMConfig().addDivertConfiguration(divertConf1).addDivertConfiguration(divertConf2).addDivertConfiguration(divertConf3);

      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(config, false));

      server.start();

      ServerLocator locator = createInVMNonHALocator();

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, true);

      final SimpleString queueName1 = SimpleString.of("queue1");

      final SimpleString queueName2 = SimpleString.of("queue2");

      final SimpleString queueName3 = SimpleString.of("queue3");

      final SimpleString queueName4 = SimpleString.of("queue4");

      session.createQueue(QueueConfiguration.of(queueName1).setAddress(forwardAddress1).setDurable(false));

      session.createQueue(QueueConfiguration.of(queueName2).setAddress(forwardAddress2).setDurable(false));

      session.createQueue(QueueConfiguration.of(queueName3).setAddress(forwardAddress3).setDurable(false));

      session.createQueue(QueueConfiguration.of(queueName4).setAddress(testAddress).setDurable(false));

      session.start();

      ClientProducer producer = session.createProducer(SimpleString.of(testAddress));

      ClientConsumer consumer1 = session.createConsumer(queueName1);

      ClientConsumer consumer2 = session.createConsumer(queueName2);

      ClientConsumer consumer3 = session.createConsumer(queueName3);

      ClientConsumer consumer4 = session.createConsumer(queueName4);

      final int numMessages = 10;

      final SimpleString propKey = SimpleString.of("testkey");

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(false);

         message.putIntProperty(propKey, i);

         producer.send(message);
      }

      for (int i = 0; i < numMessages; ) {
         ClientMessage message = consumer1.receive(DivertTest.TIMEOUT);

         assertNotNull(message);

         assertEquals(i, message.getObjectProperty(propKey));

         message.acknowledge();

         i++;

         if (i == numMessages) {
            break;
         }

         message = consumer2.receive(DivertTest.TIMEOUT);

         assertNotNull(message);

         assertEquals(i, message.getObjectProperty(propKey));

         message.acknowledge();

         i++;

         if (i == numMessages) {
            break;
         }

         message = consumer3.receive(DivertTest.TIMEOUT);

         assertNotNull(message);

         assertEquals(i, message.getObjectProperty(propKey));

         message.acknowledge();

         i++;
      }

      assertNull(consumer1.receiveImmediate());
      assertNull(consumer2.receiveImmediate());
      assertNull(consumer3.receiveImmediate());

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer4.receive(DivertTest.TIMEOUT);

         assertNotNull(message);

         assertEquals(i, message.getObjectProperty(propKey));

         message.acknowledge();
      }

      assertNull(consumer4.receiveImmediate());
   }

   @Test
   public void testDeployDivertsSameUniqueName() throws Exception {
      final String testAddress = "testAddress";

      final String forwardAddress1 = "forwardAddress1";
      final String forwardAddress2 = "forwardAddress2";
      final String forwardAddress3 = "forwardAddress3";

      DivertConfiguration divertConf1 = new DivertConfiguration().setName("divert1").setRoutingName("thename1").setAddress(testAddress).setForwardingAddress(forwardAddress1);

      DivertConfiguration divertConf2 = new DivertConfiguration().setName("divert1").setRoutingName("thename2").setAddress(testAddress).setForwardingAddress(forwardAddress2);

      DivertConfiguration divertConf3 = new DivertConfiguration().setName("divert2").setRoutingName("thename3").setAddress(testAddress).setForwardingAddress(forwardAddress3);

      Configuration config = createDefaultInVMConfig().addDivertConfiguration(divertConf1).addDivertConfiguration(divertConf2).addDivertConfiguration(divertConf3);

      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(config, false));

      server.start();

      // Only the first and third should be deployed

      ServerLocator locator = createInVMNonHALocator();

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, true);

      final SimpleString queueName1 = SimpleString.of("queue1");

      final SimpleString queueName2 = SimpleString.of("queue2");

      final SimpleString queueName3 = SimpleString.of("queue3");

      final SimpleString queueName4 = SimpleString.of("queue4");

      session.createQueue(QueueConfiguration.of(queueName1).setAddress(forwardAddress1).setDurable(false));

      session.createQueue(QueueConfiguration.of(queueName2).setAddress(forwardAddress2).setDurable(false));

      session.createQueue(QueueConfiguration.of(queueName3).setAddress(forwardAddress3).setDurable(false));

      session.createQueue(QueueConfiguration.of(queueName4).setAddress(testAddress).setDurable(false));

      session.start();

      ClientProducer producer = session.createProducer(SimpleString.of(testAddress));

      ClientConsumer consumer1 = session.createConsumer(queueName1);

      ClientConsumer consumer2 = session.createConsumer(queueName2);

      ClientConsumer consumer3 = session.createConsumer(queueName3);

      ClientConsumer consumer4 = session.createConsumer(queueName4);

      final int numMessages = 10;

      final SimpleString propKey = SimpleString.of("testkey");

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(false);

         message.putIntProperty(propKey, i);

         producer.send(message);
      }

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer1.receive(DivertTest.TIMEOUT);

         assertNotNull(message);

         assertEquals(i, message.getIntProperty(propKey).intValue());

         message.acknowledge();
      }

      assertNull(consumer1.receiveImmediate());

      assertNull(consumer2.receiveImmediate());

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer3.receive(DivertTest.TIMEOUT);

         assertNotNull(message);

         assertEquals(i, message.getIntProperty(propKey).intValue());

         message.acknowledge();
      }

      assertNull(consumer3.receiveImmediate());

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer4.receive(DivertTest.TIMEOUT);

         assertNotNull(message);

         assertEquals(i, message.getIntProperty(propKey).intValue());

         message.acknowledge();
      }

      assertNull(consumer4.receiveImmediate());
   }

   @Test
   public void testInjectedTransformer() throws Exception {
      final SimpleString ADDRESS = SimpleString.of("myAddress");
      final String DIVERT = "myDivert";

      ServiceRegistryImpl serviceRegistry = new ServiceRegistryImpl();
      Transformer transformer = message -> null;
      serviceRegistry.addDivertTransformer(DIVERT, transformer);

      ActiveMQServer server = addServer(new ActiveMQServerImpl(createBasicConfig(), null, null, null, serviceRegistry));
      server.start();
      server.waitForActivation(100, TimeUnit.MILLISECONDS);
      server.createQueue(QueueConfiguration.of("myQueue").setAddress(ADDRESS).setDurable(false));
      server.deployDivert(new DivertConfiguration().setName(DIVERT).setAddress(ADDRESS.toString()).setForwardingAddress(ADDRESS.toString()));
      Collection<Binding> bindings = server.getPostOffice().getBindingsForAddress(ADDRESS).getBindings();
      Divert divert = null;
      for (Binding binding : bindings) {
         if (binding instanceof DivertBinding divertBinding) {
            divert = divertBinding.getDivert();
         }
      }
      assertNotNull(divert);
      assertEquals(transformer, divert.getTransformer());

      server.destroyDivert(SimpleString.of(DIVERT));
      assertNull(serviceRegistry.getDivertTransformer(DIVERT, null));
   }

   @Test
   public void testProperties() throws Exception {
      final String testAddress = "testAddress";
      final SimpleString queue = SimpleString.of("queue");
      final int COUNT = 25;

      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(createDefaultInVMConfig(), false));
      server.start();

      server.createQueue(QueueConfiguration.of(queue).setAddress(testAddress + (COUNT)).setRoutingType(RoutingType.ANYCAST));
      for (int i = 0; i < COUNT; i++) {
         server.deployDivert(new DivertConfiguration()
                                .setName("divert" + i)
                                .setAddress(testAddress + i)
                                .setForwardingAddress(testAddress + (i + 1)));
      }

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession session = sf.createSession(false, true, true);
      session.start();

      ClientProducer producer = session.createProducer(SimpleString.of(testAddress + "0"));
      ClientConsumer consumer1 = session.createConsumer(queue);
      ClientMessage message = session.createMessage(false);
      producer.send(message);

      message = consumer1.receive(DivertTest.TIMEOUT);
      assertNotNull(message);
      message.acknowledge();
      assertEquals("testAddress" + COUNT, message.getAddress());
      assertEquals("testAddress" + (COUNT - 1), message.getStringProperty(Message.HDR_ORIGINAL_ADDRESS));
   }

   @Test
   public void testTransformedToLarge() throws Exception {
      final String address = "address";
      final SimpleString queue = SimpleString.of("queue");
      final String forwardingAddress = "forwardingAddress";
      final SimpleString forwardingQueue = SimpleString.of("forwardingQueue");
      final int journalBufferSize = 1024;
      final int headerCount = 5;

      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(createDefaultInVMConfig().setJournalBufferSize_NIO(journalBufferSize).setJournalType(JournalType.NIO), true));
      server.start();

      // configure the transformer to add headers to increase the size of the message past the journal buffer size
      Map<String, String> headers = new HashMap<>();
      for (int i = 0; i < headerCount; i++) {
         headers.put(RandomUtil.randomAlphaNumericString(32), RandomUtil.randomAlphaNumericString(32));
      }
      TransformerConfiguration transformerConfiguration = new TransformerConfiguration();
      transformerConfiguration.setClassName(AddHeadersTransformer.class.getName());
      transformerConfiguration.setProperties(headers);

      server.createQueue(QueueConfiguration.of(queue).setAddress(address).setRoutingType(RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(forwardingQueue).setAddress(forwardingAddress).setRoutingType(RoutingType.ANYCAST));
      server.deployDivert(new DivertConfiguration()
                             .setName("divert")
                             .setAddress(address)
                             .setForwardingAddress(forwardingAddress)
                             .setTransformerConfiguration(transformerConfiguration));

      ServerLocator locator = createInVMNonHALocator();
      locator.setMinLargeMessageSize(Integer.MAX_VALUE);
      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession session = sf.createSession(false, true, true);
      session.start();

      ClientProducer producer = session.createProducer(SimpleString.of(address));
      ClientConsumer consumer = session.createConsumer(queue);
      ClientConsumer divertedConsumer = session.createConsumer(forwardingQueue);
      ClientMessage message = session.createMessage(true);
      message.getBodyBuffer().writeBytes(RandomUtil.randomBytes(journalBufferSize / 4));
      producer.send(message);

      // ensure the non-diverted message is not turned into a large message
      message = consumer.receive(DivertTest.TIMEOUT);
      assertNotNull(message);
      message.acknowledge();
      assertFalse(message instanceof ClientLargeMessageImpl);

      // ensure the diverted message is turned into a large message
      message = divertedConsumer.receive(DivertTest.TIMEOUT);
      assertNotNull(message);
      message.acknowledge();
      assertTrue(message instanceof ClientLargeMessageImpl);
   }

   @Test
   public void testDivertToNewAddress() throws Exception {
      final String queueName = "queue";
      final String dummyQueueName = "dummy";
      final String noDivertAutoCreateQName = "notAllowed";
      final String propKey = "newQueue";
      final String DIVERT = "myDivert";
      final int numMessages = 10;

      Transformer transformer = message -> message.setAddress(message.getStringProperty(propKey));

      ServiceRegistryImpl serviceRegistry = new ServiceRegistryImpl();
      serviceRegistry.addDivertTransformer(DIVERT, transformer);

      AddressSettings autoCreateDestinationsAS = new AddressSettings().setAutoCreateAddresses(true).setAutoCreateQueues(true);
      AddressSettings noAutoCreateDestinationsAS = new AddressSettings().setAutoCreateAddresses(false).setAutoCreateQueues(false);

      ActiveMQServer server = addServer(new ActiveMQServerImpl(createDefaultInVMConfig(), null, null, null, serviceRegistry));

      server.getConfiguration().addAddressSetting("#", autoCreateDestinationsAS);
      server.getConfiguration().addAddressSetting(noDivertAutoCreateQName, noAutoCreateDestinationsAS);

      server.start();

      server.createQueue(QueueConfiguration.of(queueName));
      server.deployDivert(new DivertConfiguration()
                             .setName(DIVERT)
                             .setAddress(queueName)
                             .setRoutingType(ComponentConfigurationRoutingType.ANYCAST)
                             .setForwardingAddress(dummyQueueName)
                             .setExclusive(true));

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession session = sf.createSession(false, true, true);
      session.start();

      ClientMessage message;
      ClientProducer producer = session.createProducer(queueName);

      for (int i = 0; i < numMessages; i++) {
         message = session.createMessage(true);
         message.putStringProperty(propKey, queueName + "." + i);
         producer.send(message);
      }

      for (int i = 0; i < numMessages; i++) {
         ClientConsumer consumer = session.createConsumer(queueName + "." + i);
         message = consumer.receive(DivertTest.TIMEOUT);
         assertNotNull(message);
         message.acknowledge();
         consumer.close();
      }

      ClientMessage failMessage = session.createMessage(true);

      assertThrows(ActiveMQAddressDoesNotExistException.class, () -> {
         failMessage.putStringProperty(propKey, noDivertAutoCreateQName);
         producer.send(failMessage);
      });

      producer.close();

      assertNull(server.locateQueue(noDivertAutoCreateQName));
      assertNull(server.locateQueue(dummyQueueName));

   }

   @Test
   public void testHandleAutoDeleteDestination() throws Exception {
      final String testAddress = "testAddress";
      final String forwardAddress = "forwardAddress";

      DivertConfiguration divertConf = new DivertConfiguration()
         .setName("divert")
         .setRoutingType(ComponentConfigurationRoutingType.ANYCAST)
         .setExclusive(true)
         .setAddress(testAddress)
         .setForwardingAddress(forwardAddress);

      AddressSettings addressSettings = new AddressSettings()
         .setAutoCreateAddresses(true)
         .setAutoCreateQueues(true)
         .setAutoDeleteAddresses(true)
         .setAutoDeleteQueues(true);

      Configuration config = createDefaultInVMConfig().addDivertConfiguration(divertConf).addAddressSetting("#", addressSettings);
      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(config, false));
      server.start();

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession session = sf.createSession(false, true, true);

      session.createQueue(QueueConfiguration.of(testAddress).setAddress(testAddress).setRoutingType(RoutingType.ANYCAST).setDurable(true));
      session.createQueue(QueueConfiguration.of(forwardAddress).setAddress(forwardAddress).setRoutingType(RoutingType.ANYCAST).setDurable(true));
      session.start();

      ClientProducer producer = session.createProducer(testAddress);
      ClientConsumer consumer = session.createConsumer(forwardAddress);

      final int numMessages = 5;

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(true);
         message.setRoutingType(RoutingType.ANYCAST);
         producer.send(message);
      }

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer.receive(DivertTest.TIMEOUT);
         assertNotNull(message);
         message.acknowledge();
      }

      assertNull(consumer.receiveImmediate());
      consumer.close();

      //Trigger autoDelete instead of waiting
      QueueManagerImpl.performAutoDeleteQueue(server, server.locateQueue(forwardAddress));
      Wait.assertTrue(() -> server.getPostOffice().getAddressInfo(SimpleString.of(forwardAddress))
         .getBindingRemovedTimestamp() != -1, DivertTest.TIMEOUT, 100);

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(true);
         producer.send(message);
      }

      consumer = session.createConsumer(forwardAddress);
      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer.receive(DivertTest.TIMEOUT);
         assertNotNull(message);
         message.acknowledge();
      }

      assertNull(consumer.receiveImmediate());
   }

}
