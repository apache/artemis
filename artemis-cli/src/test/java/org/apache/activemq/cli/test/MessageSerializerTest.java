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
package org.apache.activemq.cli.test;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.cli.commands.address.CreateAddress;
import org.apache.activemq.artemis.cli.commands.messages.Consumer;
import org.apache.activemq.artemis.cli.commands.messages.Producer;
import org.apache.activemq.artemis.cli.commands.queue.StatQueue;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQDestination;
import org.apache.activemq.artemis.utils.CompositeAddress;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.apache.activemq.cli.test.ArtemisTest.getOutputLines;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Test to validate that the CLI doesn't throw improper exceptions when invoked.
 */
public class MessageSerializerTest extends CliTestBase {

   private Connection connection;
   private ActiveMQConnectionFactory cf;
   private static final int TEST_MESSAGE_COUNT = 10;

   @Before
   @Override
   public void setup() throws Exception {
      setupAuth();
      super.setup();
      startServer();
      cf = getConnectionFactory(61616);
      connection = cf.createConnection("admin", "admin");
   }

   @After
   @Override
   public void tearDown() throws Exception {
      closeConnection(cf, connection);
      super.tearDown();
   }

   private File createMessageFile() throws IOException {
      return temporaryFolder.newFile("messages.xml");
   }

   private List<Message> generateTextMessages(Session session, Destination destination) throws Exception {
      List<Message> messages = new ArrayList<>(TEST_MESSAGE_COUNT);
      for (int i = 0; i < TEST_MESSAGE_COUNT; i++) {
         messages.add(session.createTextMessage(RandomUtil.randomString()));
      }

      sendMessages(session, destination, messages);

      return messages;
   }

   private List<Message> generateLargeTextMessages(Session session, Destination destination) throws Exception {
      List<Message> messages = new ArrayList<>(TEST_MESSAGE_COUNT);
      for (int i = 0; i < TEST_MESSAGE_COUNT; i++) {
         messages.add(session.createTextMessage(new String(RandomUtil.randomBytes(ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE * 2))));
      }

      sendMessages(session, destination, messages);

      return messages;
   }

   private void checkSentMessages(Session session, List<Message> messages, String address) throws Exception {
      checkSentMessages(session, messages, address, null);
   }

   private void checkSentMessages(Session session, List<Message> messages, String address, String key) throws Exception {
      List<Message> received = consumeMessages(session, address, TEST_MESSAGE_COUNT, CompositeAddress.isFullyQualified(address));
      for (int i = 0; i < TEST_MESSAGE_COUNT; i++) {
         Message m = messages.get(i);
         if (m instanceof TextMessage) {
            assertEquals(((TextMessage) m).getText(), ((TextMessage) received.get(i)).getText());
         } else if (m instanceof ObjectMessage) {
            assertEquals(((ObjectMessage) m).getObject(), ((ObjectMessage) received.get(i)).getObject());
         } else if (m instanceof MapMessage) {
            assertEquals(((MapMessage) m).getString(key), ((MapMessage) received.get(i)).getString(key));
         }
      }
   }

   private boolean verifyMessageCount(String address, int messageCount) throws Exception {
      TestActionContext context = new TestActionContext();
      new StatQueue()
         .setQueueName(address)
         .setUser("admin")
         .setPassword("admin")
         .execute(context);
      int currentMessageCount;
      try {
         // parse the value for MESSAGE_COUNT from the output
         currentMessageCount = Integer.parseInt(getOutputLines(context, false).get(1).split("\\|")[4].trim());
      } catch (Exception e) {
         currentMessageCount = 0;
      }
      return (messageCount == currentMessageCount);
   }

   private void sendMessages(Session session, Destination destination, List<Message> messages) throws Exception {
      MessageProducer producer = session.createProducer(destination);
      for (Message m : messages) {
         producer.send(m);
      }
   }

   private void exportMessages(String address, File output) throws Exception {
      exportMessages(address, TEST_MESSAGE_COUNT, false, "test-client", output);
   }

   private void exportMessages(String address, int messageCount, boolean durable, String clientId, File output) throws Exception {
      new Consumer()
         .setFile(output.getAbsolutePath())
         .setDurable(durable)
         .setDestination(address)
         .setMessageCount(messageCount)
         .setUser("admin")
         .setPassword("admin")
         .setClientID(clientId)
         .execute(new TestActionContext());
   }

   private void importMessages(String address, File input) throws Exception {
      new Producer()
         .setFile(input.getAbsolutePath())
         .setDestination(address)
         .setUser("admin")
         .setPassword("admin")
         .execute(new TestActionContext());
   }

   private void createBothTypeAddress(String address) throws Exception {
      new CreateAddress()
         .setAnycast(true)
         .setMulticast(true)
         .setName(address)
         .setUser("admin")
         .setPassword("admin")
         .execute(new TestActionContext());
   }

   @Test
   public void testTextMessageImportExport() throws Exception {
      String address = "test";
      File file = createMessageFile();

      Session session = createSession(connection);

      List<Message> sent = generateTextMessages(session, getDestination(address));

      exportMessages(address, file);

      Wait.assertTrue(() -> verifyMessageCount(address, 0), 2000, 100);

      importMessages(address, file);
      Wait.assertTrue(() -> verifyMessageCount(address, TEST_MESSAGE_COUNT), 2000, 100);
      checkSentMessages(session, sent, address);
   }

   @Test
   public void testLargeMessageExport() throws Exception {
      String address = "test";
      File file = createMessageFile();

      Session session = createSession(connection);

      generateLargeTextMessages(session, getDestination(address));

      exportMessages(address, file);

      Wait.assertTrue(() -> verifyMessageCount(address, 0), 2000, 100);
   }

   @Test
   public void testObjectMessageImportExport() throws Exception {
      String address = "test";
      File file = createMessageFile();

      Session session = createSession(connection);

      // Send initial messages.
      List<Message> sent = new ArrayList<>(TEST_MESSAGE_COUNT);
      for (int i = 0; i < TEST_MESSAGE_COUNT; i++) {
         sent.add(session.createObjectMessage(UUID.randomUUID()));
      }

      sendMessages(session, getDestination(address), sent);
      exportMessages(address, file);

      Wait.assertTrue(() -> verifyMessageCount(address, 0), 2000, 100);

      importMessages(address, file);
      Wait.assertTrue(() -> verifyMessageCount(address, TEST_MESSAGE_COUNT), 2000, 100);
      checkSentMessages(session, sent, address);
   }

   @Test
   public void testMapMessageImportExport() throws Exception {
      String address = "test";
      String key = "testKey";
      File file = createMessageFile();

      Session session = createSession(connection);

      List<Message> sent = new ArrayList<>(TEST_MESSAGE_COUNT);
      for (int i = 0; i < TEST_MESSAGE_COUNT; i++) {
         MapMessage m = session.createMapMessage();
         m.setString(key, RandomUtil.randomString());
         sent.add(m);
      }

      sendMessages(session, getDestination(address), sent);
      exportMessages(address, file);

      Wait.assertTrue(() -> verifyMessageCount(address, 0), 2000, 100);

      importMessages(address, file);
      Wait.assertTrue(() -> verifyMessageCount(address, TEST_MESSAGE_COUNT), 2000, 100);
      checkSentMessages(session, sent, address, key);
   }

   @Test
   public void testSendDirectToMulticastQueue() throws Exception {
      internalTestSendDirectToQueue(RoutingType.MULTICAST);
   }

   @Test
   public void testSendDirectToAnycastQueue() throws Exception {
      internalTestSendDirectToQueue(RoutingType.ANYCAST);
   }

   private void internalTestSendDirectToQueue(RoutingType routingType) throws Exception {

      String address = "test";
      String queue1Name = "queue1";
      String queue2Name = "queue2";

      createQueue(routingType, address, queue1Name);
      createQueue(routingType, address, queue2Name);

      try (ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory("tcp://localhost:61616"); Connection connection = cf.createConnection("admin", "admin");) {

         // send messages to queue
         Session session = createSession(connection);

         Destination queue1 = session.createQueue(CompositeAddress.toFullyQualified(address, queue1Name));
         Destination queue2 = session.createQueue(CompositeAddress.toFullyQualified(address, queue2Name));

         MessageConsumer consumer1 = session.createConsumer(queue1);
         MessageConsumer consumer2 = session.createConsumer(queue2);

         new Producer()
            .setDestination((routingType == RoutingType.ANYCAST ? ActiveMQDestination.QUEUE_QUALIFIED_PREFIX : ActiveMQDestination.TOPIC_QUALIFIED_PREFIX) + CompositeAddress.toFullyQualified(address, queue1Name))
            .setMessageCount(5)
            .setUser("admin")
            .setPassword("admin")
            .execute(new TestActionContext());

         assertNull(consumer2.receive(1000));
         assertNotNull(consumer1.receive(1000));
      }
   }

   @Test
   public void exportFromFQQN() throws Exception {
      String addr = "address";
      String queue = "queue";
      String fqqn = CompositeAddress.toFullyQualified(addr, queue);
      String destination = ActiveMQDestination.TOPIC_QUALIFIED_PREFIX + fqqn;

      File file = createMessageFile();

      createQueue(RoutingType.MULTICAST, addr, queue);

      Session session = createSession(connection);

      Topic topic = session.createTopic(addr);

      List<Message> messages = generateTextMessages(session, topic);

      exportMessages(fqqn, file);
      importMessages(destination, file);

      checkSentMessages(session, messages, fqqn);
   }

   @Test
   public void testAnycastToMulticastTopic() throws Exception {
      String mAddress = "testMulticast";
      String aAddress = "testAnycast";
      String queueM1Name = "queueM1";
      String queueM2Name = "queueM2";

      File file = createMessageFile();

      createQueue(RoutingType.MULTICAST, mAddress, queueM1Name);
      createQueue(RoutingType.MULTICAST, mAddress, queueM2Name);

      Session session = createSession(connection);

      List<Message> messages = generateTextMessages(session, getDestination(aAddress));

      exportMessages(aAddress, file);
      importMessages(ActiveMQDestination.TOPIC_QUALIFIED_PREFIX + mAddress, file);

      checkSentMessages(session, messages, queueM1Name);
      checkSentMessages(session, messages, queueM2Name);
   }

   @Test
   public void testAnycastToMulticastFQQN() throws Exception {
      String mAddress = "testMulticast";
      String aAddress = "testAnycast";
      String queueM1Name = "queueM1";
      String queueM2Name = "queueM2";
      String fqqnMulticast1 = CompositeAddress.toFullyQualified(mAddress, queueM1Name);
      String fqqnMulticast2 = CompositeAddress.toFullyQualified(mAddress, queueM2Name);

      File file = createMessageFile();

      createQueue(RoutingType.MULTICAST, mAddress, queueM1Name);
      createQueue(RoutingType.MULTICAST, mAddress, queueM2Name);

      Session session = createSession(connection);

      List<Message> messages = generateTextMessages(session, getDestination(aAddress));

      exportMessages(aAddress, file);
      importMessages(ActiveMQDestination.TOPIC_QUALIFIED_PREFIX + fqqnMulticast1, file);

      checkSentMessages(session, messages, fqqnMulticast1);

      MessageConsumer consumer = session.createConsumer(getDestination(fqqnMulticast2));
      assertNull(consumer.receive(1000));
   }

   @Test
   public void testMulticastTopicToAnycastQueueBothAddress() throws Exception {
      String address = "testBoth";
      String clientId = "test-client-id";
      File file = createMessageFile();

      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      createBothTypeAddress(address);

      exportMessages(ActiveMQDestination.TOPIC_QUALIFIED_PREFIX + address, 0, true, clientId, file);

      connection.start();

      List<Message> messages = generateTextMessages(session, getTopicDestination(address));

      exportMessages(ActiveMQDestination.TOPIC_QUALIFIED_PREFIX + address, TEST_MESSAGE_COUNT, true, clientId, file);

      importMessages(address, file);

      checkSentMessages(session, messages, address);
   }

   @Test
   public void testAnycastQueueToMulticastTopicBothAddress() throws Exception {
      String address = "testBoth";
      String clientId = "test-client-id";

      File file = createMessageFile();

      connection.setClientID(clientId);
      createBothTypeAddress(address);
      createQueue(RoutingType.ANYCAST, address, address);
      Session session = createSession(connection);

      TopicSubscriber subscriber = session.createDurableSubscriber(session.createTopic(address), "test-subscriber");

      List<Message> messages = generateTextMessages(session, getDestination(address));

      exportMessages(address, file);

      importMessages(ActiveMQDestination.TOPIC_QUALIFIED_PREFIX + address, file);
      for (int i = 0; i < TEST_MESSAGE_COUNT; i++) {
         TextMessage messageReceived = (TextMessage) subscriber.receive(1000);
         assertNotNull(messageReceived);
         assertEquals(((TextMessage) messages.get(i)).getText(), messageReceived.getText());
      }
   }
}
