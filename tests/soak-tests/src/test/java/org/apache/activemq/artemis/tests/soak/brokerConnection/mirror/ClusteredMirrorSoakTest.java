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

package org.apache.activemq.artemis.tests.soak.brokerConnection.mirror;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import java.io.File;
import java.io.StringWriter;
import java.lang.invoke.MethodHandles;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.management.SimpleManagement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectionAddressType;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.tests.soak.SoakTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.util.ServerUtil;
import org.apache.activemq.artemis.utils.FileUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClusteredMirrorSoakTest extends SoakTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static String largeBody;
   private static String smallBody = "This is a small body";

   static {
      StringWriter writer = new StringWriter();
      while (writer.getBuffer().length() < 1024 * 1024) {
         writer.append("This is a large string ..... ");
      }
      largeBody = writer.toString();
   }

   public static final String DC1_NODE_A = "ClusteredMirrorSoakTest/DC1/A";
   public static final String DC2_NODE_A = "ClusteredMirrorSoakTest/DC2/A";
   public static final String DC1_NODE_B = "ClusteredMirrorSoakTest/DC1/B";
   public static final String DC2_NODE_B = "ClusteredMirrorSoakTest/DC2/B";

   Process processDC1_node_A;
   Process processDC1_node_B;
   Process processDC2_node_A;
   Process processDC2_node_B;

   private static String DC1_NODEA_URI = "tcp://localhost:61616";
   private static String DC1_NODEB_URI = "tcp://localhost:61617";
   private static String DC2_NODEA_URI = "tcp://localhost:61618";
   private static String DC2_NODEB_URI = "tcp://localhost:61619";

   private static void createServer(String serverName, String connectionName, String clusterURI, String mirrorURI, int portOffset, boolean paging) throws Exception {
      File serverLocation = getFileServerLocation(serverName);
      deleteDirectory(serverLocation);

      HelperCreate cliCreateServer = helperCreate();
      cliCreateServer.setAllowAnonymous(true).setArtemisInstance(serverLocation);
      cliCreateServer.setMessageLoadBalancing("ON_DEMAND");
      cliCreateServer.setClustered(true);
      cliCreateServer.setNoWeb(true);
      cliCreateServer.setStaticCluster(clusterURI);
      cliCreateServer.setArgs("--no-stomp-acceptor", "--no-hornetq-acceptor", "--no-mqtt-acceptor", "--no-amqp-acceptor", "--max-hops", "1", "--name", DC1_NODE_A);
      cliCreateServer.addArgs("--addresses", "order");
      cliCreateServer.addArgs("--queues", "myQueue");
      cliCreateServer.setPortOffset(portOffset);
      cliCreateServer.createServer();

      Properties brokerProperties = new Properties();
      brokerProperties.put("AMQPConnections." + connectionName + ".uri", mirrorURI);
      brokerProperties.put("AMQPConnections." + connectionName + ".retryInterval", "1000");
      brokerProperties.put("AMQPConnections." + connectionName + ".type", AMQPBrokerConnectionAddressType.MIRROR.toString());
      brokerProperties.put("AMQPConnections." + connectionName + ".connectionElements.mirror.sync", "false");
      brokerProperties.put("largeMessageSync", "false");
      File brokerPropertiesFile = new File(serverLocation, "broker.properties");
      saveProperties(brokerProperties, brokerPropertiesFile);

      File brokerXml = new File(serverLocation, "/etc/broker.xml");
      assertTrue(brokerXml.exists());
      // Adding redistribution delay to broker configuration
      assertTrue(FileUtil.findReplace(brokerXml, "<address-setting match=\"#\">", "<address-setting match=\"#\">\n\n" + "            <redistribution-delay>0</redistribution-delay> <!-- added by ClusteredMirrorSoakTest.java --> \n"));
      if (paging) {
         assertTrue(FileUtil.findReplace(brokerXml, "<max-size-messages>-1</max-size-messages>", "<max-size-messages>1</max-size-messages>"));
      }
   }

   public static void createRealServers(boolean paging) throws Exception {
      createServer(DC1_NODE_A, "mirror", DC1_NODEB_URI, DC2_NODEA_URI, 0, paging);
      createServer(DC1_NODE_B, "mirror", DC1_NODEA_URI, DC2_NODEB_URI, 1, paging);
      createServer(DC2_NODE_A, "mirror", DC2_NODEB_URI, DC1_NODEA_URI, 2, paging);
      createServer(DC2_NODE_B, "mirror", DC2_NODEA_URI, DC1_NODEB_URI, 3, paging);
   }

   private void startServers() throws Exception {
      processDC1_node_A = startServer(DC1_NODE_A, -1, -1, new File(getServerLocation(DC1_NODE_A), "broker.properties"));
      processDC1_node_B = startServer(DC1_NODE_B, -1, -1, new File(getServerLocation(DC1_NODE_B), "broker.properties"));
      processDC2_node_A = startServer(DC2_NODE_A, -1, -1, new File(getServerLocation(DC2_NODE_A), "broker.properties"));
      processDC2_node_B = startServer(DC2_NODE_B, -1, -1, new File(getServerLocation(DC2_NODE_B), "broker.properties"));

      ServerUtil.waitForServerToStart(0, 10_000);
      ServerUtil.waitForServerToStart(1, 10_000);
      ServerUtil.waitForServerToStart(2, 10_000);
      ServerUtil.waitForServerToStart(3, 10_000);
   }

   @Test
   public void testAvoidReflections() throws Exception {
      createRealServers(true);

      String internalQueue = "INTERNAL_QUEUE";

      ActiveMQServer tempServer = createServer(true);
      tempServer.getConfiguration().setBindingsDirectory(getServerLocation(DC1_NODE_A) + "/data/bindings");
      tempServer.getConfiguration().setJournalDirectory(getServerLocation(DC1_NODE_A) + "/data/journal");
      tempServer.getConfiguration().setJournalFileSize(10 * 1024 * 1024);
      tempServer.start();
      tempServer.addAddressInfo(new AddressInfo(internalQueue).addRoutingType(RoutingType.ANYCAST).setInternal(true));
      tempServer.createQueue(QueueConfiguration.of(internalQueue).setDurable(true).setRoutingType(RoutingType.ANYCAST).setInternal(true).setAddress(internalQueue));
      tempServer.stop();

      startServers();

      SimpleManagement simpleManagementDC1A = new SimpleManagement(DC1_NODEA_URI, null, null);
      SimpleManagement simpleManagementDC2A = new SimpleManagement(DC2_NODEA_URI, null, null);
      SimpleManagement simpleManagementDC1B = new SimpleManagement(DC1_NODEA_URI, null, null);
      SimpleManagement simpleManagementDC2B = new SimpleManagement(DC2_NODEB_URI, null, null);

      String snfQueue = "$ACTIVEMQ_ARTEMIS_MIRROR_mirror";

      String queueName = "myQueue";
      String topicName = "order";

      for (int i = 0; i < 5; i++) {
         logger.info("DC1A={}", simpleManagementDC1A.getMessageAddedOnQueue(snfQueue));
         logger.info("DC1B={}", simpleManagementDC1B.getMessageAddedOnQueue(snfQueue));
         logger.info("DC2A={}", simpleManagementDC2A.getMessageAddedOnQueue(snfQueue));
         logger.info("DC2B={}", simpleManagementDC2B.getMessageAddedOnQueue(snfQueue));

         // no load generated.. just initial queues should have been sent
         assertTrue(simpleManagementDC1A.getMessageAddedOnQueue(snfQueue) < 20);
         assertTrue(simpleManagementDC2A.getMessageAddedOnQueue(snfQueue) < 20);
         assertTrue(simpleManagementDC1B.getMessageAddedOnQueue(snfQueue) < 20);
         assertTrue(simpleManagementDC2B.getMessageAddedOnQueue(snfQueue) < 20);
         Thread.sleep(100);
      }

      assertEquals(0, simpleManagementDC2A.getMessageCountOnQueue(queueName));
      assertEquals(0, simpleManagementDC1A.getMessageCountOnQueue(internalQueue));
      try {
         simpleManagementDC2A.getMessageCountOnQueue(internalQueue);
         fail("Exception expected");
      } catch (Exception expected) {
      }

      ConnectionFactory connectionFactoryDC1A = CFUtil.createConnectionFactory("amqp", DC1_NODEA_URI);

      int numberOfMessages = 1_000;

      try (Connection connection = connectionFactoryDC1A.createConnection()) {
         connection.setClientID("conn1");
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         Topic topic = session.createTopic(topicName);
         MessageConsumer con = session.createDurableConsumer(topic, "hello1");
         MessageConsumer con2 = session.createDurableConsumer(topic, "hello2");

         MessageProducer producer = session.createProducer(topic);
         for (int i = 0; i < numberOfMessages; i++) {
            if (i % 100 == 0) {
               logger.info("Sent topic {}", i);
            }
            producer.send(session.createTextMessage("hello " + i));
         }
         session.commit();

      }

      try (Connection connection = connectionFactoryDC1A.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         Queue queue = session.createQueue(queueName);

         MessageProducer producer = session.createProducer(queue);
         for (int i = 0; i < numberOfMessages; i++) {
            if (i % 100 == 0) {
               logger.info("Sent queue {}", i);
            }
            producer.send(session.createTextMessage("hello " + i));
         }
         session.commit();
      }

      Wait.assertEquals(numberOfMessages, () -> simpleManagementDC1A.getMessageCountOnQueue(queueName), 5000);
      Wait.assertEquals(numberOfMessages, () -> simpleManagementDC2A.getMessageCountOnQueue(queueName), 5000);
      Wait.assertEquals(numberOfMessages, () -> simpleManagementDC1A.getMessageCountOnQueue("conn1.hello2"), 5000);
      Wait.assertEquals(numberOfMessages, () -> simpleManagementDC1A.getMessageCountOnQueue("conn1.hello2"), 5000);
      Wait.assertEquals(numberOfMessages, () -> simpleManagementDC2A.getMessageCountOnQueue("conn1.hello2"), 5000);
      Wait.assertEquals(numberOfMessages, () -> simpleManagementDC2A.getMessageCountOnQueue("conn1.hello2"), 5000);

      try (Connection connection = connectionFactoryDC1A.createConnection()) {
         connection.setClientID("conn1");
         connection.start();
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         Topic topic = session.createTopic(topicName);
         Queue queue = session.createQueue(queueName);
         MessageConsumer[] consumers = new MessageConsumer[] {session.createDurableSubscriber(topic, "hello1"), session.createDurableSubscriber(topic, "hello2"), session.createConsumer(queue)};

         for (MessageConsumer c : consumers) {
            for (int i = 0; i < numberOfMessages; i++) {
               assertNotNull(c.receive(5000));
               if (i % 100 == 0) {
                  session.commit();
               }
            }
            session.commit();
         }
      }

      Wait.assertEquals(0, () -> simpleManagementDC1A.getMessageCountOnQueue(queueName), 5000);
      Wait.assertEquals(0, () -> simpleManagementDC2A.getMessageCountOnQueue(queueName), 5000);
      Wait.assertEquals(0, () -> simpleManagementDC1A.getMessageCountOnQueue("conn1.hello2"), 5000);
      Wait.assertEquals(0, () -> simpleManagementDC1A.getMessageCountOnQueue("conn1.hello2"), 5000);
      Wait.assertEquals(0, () -> simpleManagementDC2A.getMessageCountOnQueue("conn1.hello2"), 5000);
      Wait.assertEquals(0, () -> simpleManagementDC2A.getMessageCountOnQueue("conn1.hello2"), 5000);

      long countDC1A = simpleManagementDC1A.getMessageAddedOnQueue(snfQueue);
      long countDC1B = simpleManagementDC1B.getMessageAddedOnQueue(snfQueue);

      for (int i = 0; i < 10; i++) {
         // DC1 should be quiet and nothing moving out of it
         assertEquals(countDC1A, simpleManagementDC1A.getMessageAddedOnQueue(snfQueue));
         assertEquals(countDC1B, simpleManagementDC1B.getMessageAddedOnQueue(snfQueue));

         // DC2 is totally passive, nothing should have been generated
         assertTrue(simpleManagementDC2A.getMessageAddedOnQueue(snfQueue) < 20);
         assertTrue(simpleManagementDC2B.getMessageAddedOnQueue(snfQueue) < 20);
         // we take intervals, allowing to make sure it doesn't grow
         Thread.sleep(100);
         logger.info("DC1A={}", simpleManagementDC1A.getMessageAddedOnQueue(snfQueue));
         logger.info("DC1B={}", simpleManagementDC1B.getMessageAddedOnQueue(snfQueue));
         logger.info("DC2A={}", simpleManagementDC2A.getMessageAddedOnQueue(snfQueue));
         logger.info("DC2B={}", simpleManagementDC2B.getMessageAddedOnQueue(snfQueue));
      }
   }

   @Test
   public void testSimpleQueue() throws Exception {
      createRealServers(false);
      startServers();

      final int numberOfMessages = 200;

      assertEquals(0, numberOfMessages % 2, "numberOfMessages must be even");

      ConnectionFactory connectionFactoryDC1A = CFUtil.createConnectionFactory("amqp", DC1_NODEA_URI);
      ConnectionFactory connectionFactoryDC2A = CFUtil.createConnectionFactory("amqp", DC2_NODEA_URI);
      String snfQueue = "$ACTIVEMQ_ARTEMIS_MIRROR_mirror";

      SimpleManagement simpleManagementDC1A = new SimpleManagement(DC1_NODEA_URI, null, null);

      try (Connection connection = connectionFactoryDC1A.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         Queue queue = session.createQueue("myQueue");
         MessageProducer producer = session.createProducer(queue);

         for (int i = 0; i < numberOfMessages; i++) {
            TextMessage message;
            boolean large;
            if (i % 1 == 2) {
               message = session.createTextMessage(largeBody);
               large = true;
            } else {
               message = session.createTextMessage(smallBody);
               large = false;
            }
            message.setIntProperty("i", i);
            message.setBooleanProperty("large", large);
            producer.send(message);
            if (i % 100 == 0) {
               logger.debug("commit {}", i);
               session.commit();
            }
         }
         session.commit();
      }

      logger.debug("All messages were sent");

      try (Connection connection = connectionFactoryDC1A.createConnection()) {
         connection.start();
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         Queue queue = session.createQueue("myQueue");
         MessageConsumer consumer = session.createConsumer(queue);

         for (int i = 0; i < numberOfMessages / 2; i++) {
            TextMessage message = (TextMessage) consumer.receive(5000);
            assertNotNull(message);
            logger.debug("Received message {}, large={}", message.getIntProperty("i"), message.getBooleanProperty("large"));
         }
         session.commit();
      }

      processDC2_node_A.destroyForcibly();
      processDC2_node_A.waitFor();
      processDC2_node_A = startServer(DC2_NODE_A, 2, 5000, new File(getServerLocation(DC2_NODE_A), "broker.properties"));

      Wait.assertEquals(0L, () -> getMessageCount(simpleManagementDC1A, snfQueue), 250_000, 1000);

      try (Connection connection = connectionFactoryDC2A.createConnection()) {
         connection.start();
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         Queue queue = session.createQueue("myQueue");
         MessageConsumer consumer = session.createConsumer(queue);

         for (int i = 0; i < numberOfMessages / 2; i++) {
            TextMessage message = (TextMessage) consumer.receive(5000);
            assertNotNull(message);
            logger.debug("Received message {}, large={}", message.getIntProperty("i"), message.getBooleanProperty("large"));
         }
         session.commit();
      }
   }


   private CountDownLatch startConsumer(Executor executor, ConnectionFactory factory, String queue, AtomicBoolean running, AtomicInteger errorCount, AtomicInteger receivedCount) {
      CountDownLatch done = new CountDownLatch(1);

      Set<Integer> receivedMessages = new HashSet<>();

      executor.execute(() -> {
         try {
            try (Connection connection = factory.createConnection()) {
               Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
               MessageConsumer consumer = session.createConsumer(session.createQueue(queue));
               connection.start();
               while (running.get()) {
                  Message message = consumer.receive(100);
                  if (message != null) {
                     receivedCount.incrementAndGet();
                     Integer receivedI = message.getIntProperty("i");
                     if (!receivedMessages.add(receivedI)) {
                        errorCount.incrementAndGet();
                        logger.warn("Message {}, isLarge={} received in duplicate", receivedI, message.getBooleanProperty("large"));
                     }
                  }
               }
            }
         } catch (Exception e) {
            logger.warn(e.getMessage(), e);
            errorCount.incrementAndGet();
         } finally {
            done.countDown();
         }

      });

      return done;
   }

   private boolean findQueue(SimpleManagement simpleManagement, String queue) {
      try {
         simpleManagement.getMessageCountOnQueue(queue);
         return true;
      } catch (Exception e) {
         return false;
      }
   }

   private void sendMessages(ConnectionFactory factory, String queueName, int messages, int commitInterval) throws Exception {

      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         Queue queue = session.createQueue(queueName);
         MessageProducer producer = session.createProducer(queue);

         for (int i = 0; i < messages; i++) {
            TextMessage message;
            boolean large;
            if (i % 1 == 2) {
               message = session.createTextMessage(largeBody);
               large = true;
            } else {
               message = session.createTextMessage(smallBody);
               large = false;
            }
            message.setIntProperty("i", i);
            message.setBooleanProperty("large", large);
            producer.send(message);
            if (i > 0 && i % commitInterval == 0) {
               logger.debug("commit {}", i);
               session.commit();
            }
         }
         session.commit();
      }
   }

   @Test
   public void testAutoCreateQueue() throws Exception {
      ExecutorService executorService = Executors.newFixedThreadPool(2);
      runAfter(executorService::shutdownNow);

      createRealServers(false);
      startServers();

      String queueName = "testqueue" + RandomUtil.randomUUIDString();

      final int numberOfMessages = 50;

      ConnectionFactory connectionFactoryDC1A = CFUtil.createConnectionFactory("amqp", DC1_NODEA_URI);
      ConnectionFactory connectionFactoryDC2A = CFUtil.createConnectionFactory("amqp", DC2_NODEA_URI);
      ConnectionFactory connectionFactoryDC2B = CFUtil.createConnectionFactory("amqp", DC2_NODEB_URI);

      AtomicBoolean runningConsumers = new AtomicBoolean(true);
      runAfter(() -> runningConsumers.set(false));
      AtomicInteger errors = new AtomicInteger(0);
      AtomicInteger receiverCount = new AtomicInteger(0);

      try (SimpleManagement simpleManagementDC1A = new SimpleManagement(DC1_NODEA_URI, null, null);
           SimpleManagement simpleManagementDC1B = new SimpleManagement(DC1_NODEB_URI, null, null);
           SimpleManagement simpleManagementDC2A = new SimpleManagement(DC2_NODEA_URI, null, null);
           SimpleManagement simpleManagementDC2B = new SimpleManagement(DC2_NODEB_URI, null, null)) {

         assertFalse(findQueue(simpleManagementDC1A, queueName));
         assertFalse(findQueue(simpleManagementDC1B, queueName));
         assertFalse(findQueue(simpleManagementDC2A, queueName));
         assertFalse(findQueue(simpleManagementDC2B, queueName));

         // just to allow auto-creation to kick in....
         assertTrue(startConsumer(executorService, connectionFactoryDC2A, queueName, new AtomicBoolean(false), errors, receiverCount).await(1, TimeUnit.MINUTES));
         assertTrue(startConsumer(executorService, connectionFactoryDC2B, queueName, new AtomicBoolean(false), errors, receiverCount).await(1, TimeUnit.MINUTES));

         Wait.assertTrue(() -> findQueue(simpleManagementDC1A, queueName));
         Wait.assertTrue(() -> findQueue(simpleManagementDC1B, queueName));
         Wait.assertTrue(() -> findQueue(simpleManagementDC2A, queueName));
         Wait.assertTrue(() -> findQueue(simpleManagementDC2B, queueName));

         sendMessages(connectionFactoryDC1A, queueName, numberOfMessages, 10);

         Wait.assertEquals(numberOfMessages, () -> simpleManagementDC1A.getMessageCountOnQueue(queueName));
         Wait.assertEquals(0, () -> simpleManagementDC1B.getMessageCountOnQueue(queueName));
         Wait.assertEquals(numberOfMessages, () -> simpleManagementDC2A.getMessageCountOnQueue(queueName));
         Wait.assertEquals(0, () -> simpleManagementDC2B.getMessageCountOnQueue(queueName));

         CountDownLatch doneDC2B = startConsumer(executorService, connectionFactoryDC2B, queueName, runningConsumers, errors, receiverCount);
         Wait.assertEquals(numberOfMessages, receiverCount::get, 30_000);

         Wait.assertTrue(() -> findQueue(simpleManagementDC1A, queueName));
         Wait.assertTrue(() -> findQueue(simpleManagementDC1B, queueName));
         Wait.assertTrue(() -> findQueue(simpleManagementDC2A, queueName));
         Wait.assertTrue(() -> findQueue(simpleManagementDC2B, queueName));

         Wait.assertEquals(0, () -> simpleManagementDC1A.getDeliveringCountOnQueue(queueName), 5000);
         Wait.assertEquals(0, () -> simpleManagementDC1B.getDeliveringCountOnQueue(queueName), 5000);
         Wait.assertEquals(0, () -> simpleManagementDC2A.getDeliveringCountOnQueue(queueName), 5000);
         Wait.assertEquals(0, () -> simpleManagementDC2B.getDeliveringCountOnQueue(queueName), 5000);
         Wait.assertEquals(0, () -> simpleManagementDC1A.getMessageCountOnQueue(queueName), 5000);
         Wait.assertEquals(0, () -> simpleManagementDC1B.getMessageCountOnQueue(queueName), 5000);
         Wait.assertEquals(0, () -> simpleManagementDC2A.getMessageCountOnQueue(queueName), 5000);
         Wait.assertEquals(0, () -> simpleManagementDC2B.getMessageCountOnQueue(queueName), 5000);

         runningConsumers.set(false);

         assertTrue(doneDC2B.await(5, TimeUnit.SECONDS));
         assertEquals(0, errors.get());
      }
   }

   @Test
   public void testMirroredTopics() throws Exception {
      createRealServers(false);
      startServers();

      final int numberOfMessages = 200;

      assertEquals(0, numberOfMessages % 2, "numberOfMessages must be even");

      String clientIDA = "nodeA";
      String clientIDB = "nodeB";
      String subscriptionID = "my-order";
      String snfQueue = "$ACTIVEMQ_ARTEMIS_MIRROR_mirror";

      ConnectionFactory connectionFactoryDC1A = CFUtil.createConnectionFactory("amqp", "tcp://localhost:61616");
      ConnectionFactory connectionFactoryDC1B = CFUtil.createConnectionFactory("amqp", "tcp://localhost:61617");
      ConnectionFactory connectionFactoryDC2A = CFUtil.createConnectionFactory("amqp", "tcp://localhost:61618");
      ConnectionFactory connectionFactoryDC2B = CFUtil.createConnectionFactory("amqp", "tcp://localhost:61619");

      SimpleManagement simpleManagementDC1B = new SimpleManagement(DC1_NODEB_URI, null, null);
      SimpleManagement simpleManagementDC2B = new SimpleManagement(DC2_NODEB_URI, null, null);

      consume(connectionFactoryDC1A, clientIDA, subscriptionID, 0, 0, false);
      consume(connectionFactoryDC1B, clientIDB, subscriptionID, 0, 0, false);

      try (Connection connection = connectionFactoryDC1B.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         Topic topic = session.createTopic("order");
         MessageProducer producer = session.createProducer(topic);

         for (int i = 0; i < numberOfMessages; i++) {
            TextMessage message;
            boolean large;
            message = session.createTextMessage(largeBody);
            large = true;
            message.setIntProperty("i", i);
            message.setBooleanProperty("large", large);
            producer.send(message);
            if (i % 100 == 0) {
               logger.debug("commit {}", i);
               session.commit();
            }
         }
         session.commit();
      }

      logger.debug("Consuming from DC1B");
      consume(connectionFactoryDC1B, clientIDB, subscriptionID, 0, numberOfMessages / 2, false);

      processDC2_node_B.destroyForcibly();
      processDC2_node_B.waitFor();
      processDC2_node_B = startServer(DC2_NODE_B, 3, 5000, new File(getServerLocation(DC2_NODE_B), "broker.properties"));

      Wait.assertEquals(0L, () -> getMessageCount(simpleManagementDC1B, snfQueue), 250_000, 1000);
      Wait.assertEquals(numberOfMessages / 2, () -> simpleManagementDC2B.getMessageCountOnQueue("nodeB.my-order"), 10000);

      logger.debug("Consuming from DC2B with {}", simpleManagementDC2B.getMessageCountOnQueue("nodeB.my-order"));

      consume(connectionFactoryDC2B, clientIDB, subscriptionID, numberOfMessages / 2, numberOfMessages / 2, true);

      Wait.assertEquals(0, () -> simpleManagementDC2B.getMessageCountOnQueue("nodeB.my-order"), 10000);

      Wait.assertEquals(0, () -> simpleManagementDC1B.getMessageCountOnQueue("nodeB.my-order"), 10000);
      consume(connectionFactoryDC1B, clientIDB, subscriptionID, numberOfMessages, 0, true);
      logger.debug("DC1B nodeB.my-order=0");
   }

   private static void consume(ConnectionFactory factory, String clientID, String subscriptionID, int start, int numberOfMessages, boolean expectEmpty) throws Exception {
      try (Connection connection = factory.createConnection()) {
         connection.setClientID(clientID);
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         Topic topic = session.createTopic("order");
         connection.start();
         MessageConsumer consumer = session.createDurableConsumer(topic, subscriptionID);
         boolean failed = false;

         for (int i = start; i < start + numberOfMessages; i++) {
            TextMessage message = (TextMessage) consumer.receive(10_000);
            assertNotNull(message);
            logger.debug("Received message {}, large={}", message.getIntProperty("i"), message.getBooleanProperty("large"));
            if (message.getIntProperty("i") != i) {
               failed = true;
               logger.warn("Expected message {} but got {}", i, message.getIntProperty("i"));
            }
            if (message.getBooleanProperty("large")) {
               assertEquals(largeBody, message.getText());
            } else {
               assertEquals(smallBody, message.getText());
            }
            logger.debug("Consumed {}, large={}", i, message.getBooleanProperty("large"));
         }
         session.commit();

         assertFalse(failed);

         if (expectEmpty) {
            assertNull(consumer.receiveNoWait());
         }
      }

   }

}
