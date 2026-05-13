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

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import java.io.File;
import java.io.StringWriter;
import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.management.SimpleManagement;
import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectionAddressType;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.soak.SoakTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.util.ServerUtil;
import org.apache.activemq.artemis.utils.FileUtil;
import org.apache.activemq.artemis.utils.TestParameters;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.artemis.utils.actors.OrderedExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReplicatedMirrorTargetTest extends SoakTestBase {

   private static final String TEST_NAME = "REPLICATED_MIRROR_SOAK";

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   // Set this to true and log4j will be configured with some relevant log.trace for the AckManager at the server's
   private static final boolean TRACE_LOGS = Boolean.parseBoolean(TestParameters.testProperty(TEST_NAME, "TRACE_LOGS", "false"));
   private static final int NUMBER_MESSAGES = TestParameters.testProperty(TEST_NAME, "NUMBER_MESSAGES", 2_000);

   // By default consuming 90% of the messages
   private static final int NUMBER_MESSAGES_RECEIVE = TestParameters.testProperty(TEST_NAME, "NUMBER_MESSAGES_RECEIVE", 2_000);
   private static final int RECEIVE_COMMIT = TestParameters.testProperty(TEST_NAME, "RECEIVE_COMMIT", 200);
   private static final int SEND_COMMIT = TestParameters.testProperty(TEST_NAME, "SEND_COMMIT", 200);

   // If -1 means to never kill the target broker
   private static final int KILL_INTERVAL = TestParameters.testProperty(TEST_NAME, "KILL_INTERVAL", 1_000);
   private static final int SNF_TIMEOUT = TestParameters.testProperty(TEST_NAME, "SNF_TIMEOUT", 300_000);
   private static final int GENERAL_WAIT_TIMEOUT = TestParameters.testProperty(TEST_NAME, "GENERAL_TIMEOUT", 10_000);

   /*
    * Time each consumer takes to process a message received to allow some messages accumulating.
    * This sleep happens right before the commit.
    */
   private static final int CONSUMER_PROCESSING_TIME = TestParameters.testProperty(TEST_NAME, "CONSUMER_PROCESSING_TIME", 0);

   private static final String TOPIC_NAME = "topicTest";

   private static String body;

   static {
      StringWriter writer = new StringWriter();
      while (writer.getBuffer().length() < 30 * 1024) {
         writer.append("The sky is blue, ..... watch out for poop from the birds though!...");
      }
      body = writer.toString();
   }

   public static final String DC1_NODE = "ReplicatedMirrorTargetTest/DC1";
   public static final String DC2_NODE = "ReplicatedMirrorTargetTest/DC2";
   public static final String DC2_REPLICA_NODE = "ReplicatedMirrorTargetTest/DC2_REPLICA";

   volatile Process processDC1;
   volatile Process processDC2;
   volatile Process processDC2_REPLICA;

   @AfterEach
   public void destroyServers() throws Exception {
      if (processDC1 != null) {
         processDC1.destroyForcibly();
         processDC1.waitFor(1, TimeUnit.MINUTES);
         processDC1 = null;
      }
      if (processDC2 != null) {
         processDC2.destroyForcibly();
         processDC2.waitFor(1, TimeUnit.MINUTES);
         processDC2 = null;
      }

      if (processDC2_REPLICA != null) {
         processDC2_REPLICA.destroyForcibly();
         processDC2_REPLICA.waitFor(1, TimeUnit.MINUTES);
         processDC2_REPLICA = null;
      }

   }

   private static final String DC1_URI = "tcp://localhost:61616";
   private static final String DC2_URI = "tcp://localhost:61618";

   private static final String DC2_REPLICA_URI = "tcp://localhost:61619";

   private static void createServer(String serverName,
                                    String connectionName,
                                    String mirrorURI,
                                    int portOffset,
                                    boolean paging,
                                    boolean replicated,
                                    String clusterStatic) throws Exception {
      File serverLocation = getFileServerLocation(serverName);
      deleteDirectory(serverLocation);

      HelperCreate cliCreateServer = helperCreate();
      cliCreateServer.setAllowAnonymous(true).setArtemisInstance(serverLocation);
      cliCreateServer.setNoWeb(false);
      cliCreateServer.setArgs("--no-stomp-acceptor", "--no-hornetq-acceptor", "--no-mqtt-acceptor", "--no-amqp-acceptor", "--max-hops", "1", "--name", DC1_NODE);
      cliCreateServer.addArgs("--addresses", TOPIC_NAME);
      cliCreateServer.setPortOffset(portOffset);
      if (replicated) {
         cliCreateServer.setReplicated(true);
         cliCreateServer.setStaticCluster(clusterStatic);
         cliCreateServer.setClustered(true);
      } else {
         cliCreateServer.setClustered(false);
      }

      cliCreateServer.createServer();

      Properties brokerProperties = new Properties();
      brokerProperties.put("AMQPConnections." + connectionName + ".uri", mirrorURI);
      brokerProperties.put("AMQPConnections." + connectionName + ".retryInterval", "1000");
      brokerProperties.put("AMQPConnections." + connectionName + ".type", AMQPBrokerConnectionAddressType.MIRROR.toString());
      brokerProperties.put("AMQPConnections." + connectionName + ".connectionElements.mirror.sync", "false");
      brokerProperties.put("largeMessageSync", "false");

      if (paging) {
         brokerProperties.put("addressSettings.#.maxSizeMessages", "1");
         brokerProperties.put("addressSettings.#.maxReadPageMessages", "2000");
         brokerProperties.put("addressSettings.#.maxReadPageBytes", "-1");
         brokerProperties.put("addressSettings.#.prefetchPageMessages", "500");
      }
      // if we don't use pageTransactions we may eventually get a few duplicates
      brokerProperties.put("mirrorPageTransaction", "true");
      File brokerPropertiesFile = new File(serverLocation, "broker.properties");
      saveProperties(brokerProperties, brokerPropertiesFile);

      File brokerXml = new File(serverLocation, "/etc/broker.xml");
      assertTrue(brokerXml.exists());
      // Adding redistribution delay to broker configuration
      assertTrue(FileUtil.findReplace(brokerXml, "<address-setting match=\"#\">", "<address-setting match=\"#\">\n\n" + "            <redistribution-delay>0</redistribution-delay> <!-- added by SimpleMirrorSoakTest.java --> \n"));

      if (TRACE_LOGS) {
         replaceLogs(serverLocation);
      }

   }

   private static void replaceLogs(File serverLocation) throws Exception {
      File log4j = new File(serverLocation, "/etc/log4j2.properties");
      assertTrue(FileUtil.findReplace(log4j, "logger.artemis_utils.level=INFO", "logger.artemis_utils.level=INFO\n" +
         "\n" + "logger.endpoint.name=org.apache.activemq.artemis.core.replication.ReplicationEndpoint\n"
         + "logger.endpoint.level=DEBUG\n"
         + "appender.console.filter.threshold.type = ThresholdFilter\n"
         + "appender.console.filter.threshold.level = info"));
   }

   private static void createBackupServer(String serverName,
                                    int portOffset,
                                    String clusterStatic) throws Exception {
      File serverLocation = getFileServerLocation(serverName);
      deleteDirectory(serverLocation);

      HelperCreate cliCreateServer = helperCreate();
      cliCreateServer.setAllowAnonymous(true).setArtemisInstance(serverLocation);
      cliCreateServer.setMessageLoadBalancing("ON_DEMAND");
      cliCreateServer.setNoWeb(false);
      cliCreateServer.setArgs("--no-stomp-acceptor", "--no-hornetq-acceptor", "--no-mqtt-acceptor", "--no-amqp-acceptor", "--max-hops", "1", "--name", DC1_NODE);
      cliCreateServer.addArgs("--addresses", TOPIC_NAME);
      cliCreateServer.setPortOffset(portOffset);
      cliCreateServer.setClustered(true);
      cliCreateServer.setReplicated(true);
      cliCreateServer.setBackup(true);
      cliCreateServer.setStaticCluster(clusterStatic);
      cliCreateServer.createServer();

      File brokerXml = new File(serverLocation, "/etc/broker.xml");
      assertTrue(brokerXml.exists());
      // Adding redistribution delay to broker configuration
      assertTrue(FileUtil.findReplace(brokerXml, "<address-setting match=\"#\">", "<address-setting match=\"#\">\n\n" + "            <redistribution-delay>0</redistribution-delay> <!-- added by SimpleMirrorSoakTest.java --> \n"));

      if (TRACE_LOGS) {
         replaceLogs(serverLocation);
      }
   }

   public static void createRealServers(boolean paging) throws Exception {
      createServer(DC1_NODE, "mirror", DC2_URI, 0, paging, false, null);
      createServer(DC2_NODE, "mirror", DC1_URI, 2, paging, true, DC2_REPLICA_URI);
      createBackupServer(DC2_REPLICA_NODE, 3, DC2_URI);
   }

   private void startServers() throws Exception {
      processDC1 = startServer(DC1_NODE, -1, -1, new File(getServerLocation(DC1_NODE), "broker.properties"));
      processDC2 = startServer(DC2_NODE, -1, -1, new File(getServerLocation(DC2_NODE), "broker.properties"));

      ServerUtil.waitForServerToStart(0, 10_000);
      ServerUtil.waitForServerToStart(2, 10_000);

      processDC2_REPLICA = startServer(DC2_REPLICA_NODE, -1, -1, new File(getServerLocation(DC2_NODE), "broker.properties"));

      Thread.sleep(5000);
   }

   @Test
   public void testMirrorOnReplica() throws Exception {
      createRealServers(true);
      startServers();


      assertTrue(KILL_INTERVAL > SEND_COMMIT || KILL_INTERVAL < 0);

      String clientIDA = "nodeA";
      String clientIDB = "nodeB";
      String subscriptionID = "my-order";
      String snfQueue = "$ACTIVEMQ_ARTEMIS_MIRROR_mirror";

      ConnectionFactory connectionFactoryDC1A = CFUtil.createConnectionFactory("amqp", DC1_URI);

      consume(connectionFactoryDC1A, clientIDA, subscriptionID, 0, 0, false, false, RECEIVE_COMMIT);
      consume(connectionFactoryDC1A, clientIDB, subscriptionID, 0, 0, false, false, RECEIVE_COMMIT);

      SimpleManagement managementDC1 = new SimpleManagement(DC1_URI, null, null);
      SimpleManagement managementDC2 = new SimpleManagement(DC2_URI, null, null);

      runAfter(() -> managementDC1.close());
      runAfter(() -> managementDC2.close());

      Wait.assertEquals(0, () -> getMessageCount(managementDC1, clientIDA + "." + subscriptionID));
      Wait.assertEquals(0, () -> getMessageCount(managementDC2, clientIDA + "." + subscriptionID));
      Wait.assertEquals(0, () -> getMessageCount(managementDC1, clientIDB + "." + subscriptionID));
      Wait.assertEquals(0, () -> getMessageCount(managementDC2, clientIDB + "." + subscriptionID));

      ExecutorService executorService = Executors.newFixedThreadPool(3);
      runAfter(executorService::shutdownNow);
      CountDownLatch consumerDone = new CountDownLatch(2);
      executorService.execute(() -> {
         try {
            consume(connectionFactoryDC1A, clientIDA, subscriptionID, 0, NUMBER_MESSAGES_RECEIVE, false, false, RECEIVE_COMMIT);
         } catch (Exception e) {
            logger.warn(e.getMessage(), e);
         } finally {
            consumerDone.countDown();
         }
      });
      executorService.execute(() -> {
         try {
            consume(connectionFactoryDC1A, clientIDB, subscriptionID, 0, NUMBER_MESSAGES_RECEIVE, false, false, RECEIVE_COMMIT);
         } catch (Exception e) {
            logger.warn(e.getMessage(), e);
         } finally {
            consumerDone.countDown();
         }
      });

      OrderedExecutor restartExeuctor = new OrderedExecutor(executorService);
      AtomicBoolean running = new AtomicBoolean(true);
      runAfter(() -> running.set(false));

      try (Connection connection = connectionFactoryDC1A.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer producer = session.createProducer(session.createTopic(TOPIC_NAME));
         for (int i = 0; i < NUMBER_MESSAGES; i++) {
            TextMessage message = session.createTextMessage(body);
            message.setIntProperty("i", i);
            message.setBooleanProperty("large", false);
            producer.send(message);
            if (i > 0 && i % SEND_COMMIT == 0) {
               logger.info("Sent {} messages", i);
               session.commit();
            }
            if (KILL_INTERVAL > 0 && i > 0 && i % KILL_INTERVAL == 0) {
               restartExeuctor.execute(() -> {
                  if (running.get()) {
                     try {
                        logger.info("Restarting target server (DC2)");
                        if (processDC2_REPLICA != null) {
                           processDC2_REPLICA.destroyForcibly();
                           processDC2_REPLICA.waitFor(1, TimeUnit.MINUTES);
                           processDC2_REPLICA = null;
                        }
                        if (processDC2 != null) {
                           processDC2.destroyForcibly();
                           processDC2.waitFor(1, TimeUnit.MINUTES);
                           processDC2 = null;
                        }
                        processDC2 = startServer(DC2_NODE, 2, 10_000, new File(getServerLocation(DC2_NODE), "broker.properties"));
                        processDC2_REPLICA = startServer(DC2_REPLICA_NODE, -1, -1);
                     } catch (Exception e) {
                        logger.warn(e.getMessage(), e);
                     }
                  }
               });
            }
         }
         session.commit();
         running.set(false);
      }

      consumerDone.await(SNF_TIMEOUT, TimeUnit.MILLISECONDS);

      Wait.assertEquals(0, () -> getMessageCount(managementDC1, snfQueue), SNF_TIMEOUT);
      Wait.assertEquals(0, () -> getMessageCount(managementDC2, snfQueue), SNF_TIMEOUT);
      Wait.assertEquals(NUMBER_MESSAGES - NUMBER_MESSAGES_RECEIVE, () -> getMessageCount(managementDC1, clientIDA + "." + subscriptionID), GENERAL_WAIT_TIMEOUT);
      Wait.assertEquals(NUMBER_MESSAGES - NUMBER_MESSAGES_RECEIVE, () -> getMessageCount(managementDC1, clientIDB + "." + subscriptionID), GENERAL_WAIT_TIMEOUT);
      Wait.assertEquals(NUMBER_MESSAGES - NUMBER_MESSAGES_RECEIVE, () -> getMessageCount(managementDC2, clientIDA + "." + subscriptionID), GENERAL_WAIT_TIMEOUT);
      Wait.assertEquals(NUMBER_MESSAGES - NUMBER_MESSAGES_RECEIVE, () -> getMessageCount(managementDC2, clientIDB + "." + subscriptionID), GENERAL_WAIT_TIMEOUT);

      destroyServers();

      // counting the number of records on duplicate cache
      // to validate if ARTEMIS-4765 is fixed
      ActiveMQServer server = createServer(true, false);
      server.getConfiguration().setJournalDirectory(getServerLocation(DC2_NODE) + "/data/journal");
      server.getConfiguration().setBindingsDirectory(getServerLocation(DC2_NODE) + "/data/bindings");
      server.getConfiguration().setPagingDirectory(getServerLocation(DC2_NODE) + "/data/paging");
      server.start();
      server.getStorageManager().getMessageJournal().scheduleCompactAndBlock(10_000);
      Map<Integer, AtomicInteger> records = countJournal(server.getConfiguration());
      AtomicInteger duplicateRecordsCount = records.get((int) JournalRecordIds.DUPLICATE_ID);
      assertNotNull(duplicateRecordsCount);
      // 1000 credits by default
      assertTrue(duplicateRecordsCount.get() <= 1000);

   }

   private static void consume(ConnectionFactory factory,
                               String clientID,
                               String subscriptionID,
                               int start,
                               int numberOfMessages,
                               boolean expectEmpty,
                               boolean assertBody,
                               int batchCommit) throws Exception {
      try (Connection connection = factory.createConnection()) {
         connection.setClientID(clientID);
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         Topic topic = session.createTopic(TOPIC_NAME);
         connection.start();
         MessageConsumer consumer = session.createDurableConsumer(topic, subscriptionID);
         boolean failed = false;

         int pendingCommit = 0;

         for (int i = start; i < start + numberOfMessages; i++) {
            TextMessage message = (TextMessage) consumer.receive(10_000);
            assertNotNull(message);
            logger.debug("Received message {}, large={}", message.getIntProperty("i"), message.getBooleanProperty("large"));
            if (message.getIntProperty("i") != i) {
               failed = true;
               logger.warn("Expected message {} but got {}", i, message.getIntProperty("i"));
            }
            logger.debug("Consumed {}, large={}", i, message.getBooleanProperty("large"));
            pendingCommit++;
            if (pendingCommit >= batchCommit) {
               if (CONSUMER_PROCESSING_TIME > 0) {
                  Thread.sleep(CONSUMER_PROCESSING_TIME);
               }
               logger.info("received {}", i);
               session.commit();
               pendingCommit = 0;
            }
         }
         session.commit();

         assertFalse(failed);

         if (expectEmpty) {
            assertNull(consumer.receiveNoWait());
         }
      }
   }
}
