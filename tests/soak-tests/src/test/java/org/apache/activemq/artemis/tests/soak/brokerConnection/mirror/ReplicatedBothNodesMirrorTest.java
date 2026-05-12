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
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.lang.invoke.MethodHandles;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.apache.activemq.artemis.api.core.management.SimpleManagement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectionAddressType;
import org.apache.activemq.artemis.tests.soak.SoakTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.util.ServerUtil;
import org.apache.activemq.artemis.utils.FileUtil;
import org.apache.activemq.artemis.utils.TestParameters;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReplicatedBothNodesMirrorTest extends SoakTestBase {

   private static final String TEST_NAME = "REPLICATED_BOTH_NODES_MIRROR_SOAK";

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());


   private static final String SNF_QUEUE = "$ACTIVEMQ_ARTEMIS_MIRROR_mirror";

   // Set this to true and log4j will be configured with some relevant log.trace for the AckManager at the server's
   private static final boolean TRACE_LOGS = Boolean.parseBoolean(TestParameters.testProperty(TEST_NAME, "TRACE_LOGS", "false"));
   private static final int NUMBER_MESSAGES = TestParameters.testProperty(TEST_NAME, "NUMBER_MESSAGES", 200);

   private static final boolean REUSE_SERVERS = Boolean.parseBoolean(TestParameters.testProperty(TEST_NAME, "REUSE_SERVERS", "false"));

   private static final int SEND_COMMIT = TestParameters.testProperty(TEST_NAME, "SEND_COMMIT", 50);
   private static final String QUEUE_NAME = "queueTest";

   private static String body;

   static {
      StringWriter writer = new StringWriter();
      while (writer.getBuffer().length() < 30 * 1024) {
         writer.append("The sky is blue, ..... watch out for poop from the birds though!...");
      }
      body = writer.toString();
   }

   public static final String DC1_NODE = "ReplicatedBothNodesMirrorTest/DC1";
   public static final String DC2_NODE = "ReplicatedBothNodesMirrorTest/DC2";
   public static final String DC2_REPLICA_NODE = "ReplicatedBothNodesMirrorTest/DC2_REPLICA";
   public static final String DC1_REPLICA_NODE = "ReplicatedBothNodesMirrorTest/DC1_REPLICA";

   volatile Process processDC1;
   volatile Process processDC2;
   volatile Process processDC1_REPLICA;
   volatile Process processDC2_REPLICA;

   @AfterEach
   public void destroyServers() throws Exception {
      if (processDC2_REPLICA != null) {
         processDC2_REPLICA.destroyForcibly();
         processDC2_REPLICA.waitFor(1, TimeUnit.MINUTES);
         processDC2_REPLICA = null;
      }
      if (processDC1_REPLICA != null) {
         processDC1_REPLICA.destroyForcibly();
         processDC1_REPLICA.waitFor(1, TimeUnit.MINUTES);
         processDC1_REPLICA = null;
      }
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
   }

   private static final String DC1_IP = "localhost:61616";
   private static final String DC1_BACKUP_IP = "localhost:61617";
   private static final String DC2_IP = "localhost:61618";
   private static final String DC2_BACKUP_IP = "localhost:61619";

   private static String uri(String ip) {
      return "tcp://" + ip;
   }
   private static String uriWithAlternate(String ip, String alternate) {
      return "tcp://" + ip + "#tcp://" + alternate;
   }

   private void startDC2(SimpleManagement managementDC2) throws Exception {
      processDC2 = startServer(DC2_NODE, -1, -1, new File(getServerLocation(DC2_NODE), "broker.properties"));
      processDC2_REPLICA = startServer(DC2_REPLICA_NODE, -1, -1, new File(getServerLocation(DC2_REPLICA_NODE), "broker.properties"));
      ServerUtil.waitForServerToStart(2, 10_000);
      Wait.assertTrue(managementDC2::isReplicaSync);
   }

   private void startDC1(SimpleManagement managementDC1) throws Exception {
      processDC1 = startServer(DC1_NODE, -1, -1, new File(getServerLocation(DC1_NODE), "broker.properties"));
      processDC1_REPLICA = startServer(DC1_REPLICA_NODE, -1, -1, new File(getServerLocation(DC1_REPLICA_NODE), "broker.properties"));
      ServerUtil.waitForServerToStart(0, 10_000);
      Wait.assertTrue(managementDC1::isReplicaSync);
   }

   private static void createMirroredServer(boolean paging, String serverName,
                                    String connectionName,
                                    String mirrorURI,
                                    int portOffset,
                                    boolean replicated,
                                    String clusterStatic) throws Exception {
      File serverLocation = getFileServerLocation(serverName);
      if (REUSE_SERVERS && serverLocation.exists()) {
         deleteDirectory(new File(serverLocation, "data"));
         return;
      }
      deleteDirectory(serverLocation);

      HelperCreate cliCreateServer = helperCreate();
      cliCreateServer.setAllowAnonymous(true).setArtemisInstance(serverLocation);
      cliCreateServer.setArgs("--no-stomp-acceptor", "--no-hornetq-acceptor", "--no-mqtt-acceptor", "--no-amqp-acceptor", "--max-hops", "1", "--name", DC1_NODE, "--cluster-user", "my-cluster-user", "--cluster-password", "my-cluster-password");
      cliCreateServer.addArgs("--queues", QUEUE_NAME);
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
         brokerProperties.put("addressSettings.#.maxSizeMessages", "50");
         brokerProperties.put("addressSettings.#.maxReadPageMessages", "2000");
         brokerProperties.put("addressSettings.#.maxReadPageBytes", "-1");
         brokerProperties.put("addressSettings.#.prefetchPageMessages", "500");
         brokerProperties.put("mirrorPageTransaction", "true");
      }

      File brokerPropertiesFile = new File(serverLocation, "broker.properties");
      saveProperties(brokerProperties, brokerPropertiesFile);

      File brokerXml = new File(serverLocation, "/etc/broker.xml");
      assertTrue(brokerXml.exists());
      assertTrue(FileUtil.findReplace(brokerXml, "<page-size-bytes>10M</page-size-bytes>", "<page-size-bytes>100K</page-size-bytes>"));
      assertTrue(FileUtil.findReplace(brokerXml, "amqpDuplicateDetection=true;", "amqpDuplicateDetection=true;ackManagerFlushTimeout=" + TimeUnit.MINUTES.toMillis(10) + ";"));
      assertTrue(FileUtil.findReplace(brokerXml, "<vote-on-replication-failure>true</vote-on-replication-failure>", "<vote-on-replication-failure>false</vote-on-replication-failure> <check-for-active-server>true</check-for-active-server>"));

      if (TRACE_LOGS) {
         replaceLogs(serverLocation);
      }

   }

   private static void replaceLogs(File serverLocation) throws Exception {
      File log4j = new File(serverLocation, "/etc/log4j2.properties");
      assertTrue(FileUtil.findReplace(log4j, "logger.artemis_utils.level=INFO", """
         logger.artemis_utils.level=INFO

         logger.endpoint.name=org.apache.activemq.artemis.core.replication.ReplicationEndpoint
         logger.endpoint.level=INFO
         logger.ack.name=org.apache.activemq.artemis.protocol.amqp.connect.mirror.AckManager
         logger.ack.level=TRACE
         logger.mirrorTarget.name=org.apache.activemq.artemis.protocol.amqp.connect.mirror.AMQPMirrorControllerTarget
         logger.mirrorTarget.level=INFO
         appender.console.filter.threshold.type = ThresholdFilter
         appender.console.filter.threshold.level = info"""));
   }

   private static void createMirroredBackupServer(boolean paging, String serverName, int portOffset, String clusterStatic, String mirrorURI) throws Exception {
      File serverLocation = getFileServerLocation(serverName);
      if (REUSE_SERVERS && serverLocation.exists()) {
         deleteDirectory(new File(serverLocation, "data"));
         return;
      }
      deleteDirectory(serverLocation);

      HelperCreate cliCreateServer = helperCreate();
      cliCreateServer.setAllowAnonymous(true).setArtemisInstance(serverLocation);
      cliCreateServer.setMessageLoadBalancing("ON_DEMAND");
      cliCreateServer.setArgs("--no-stomp-acceptor", "--no-hornetq-acceptor", "--no-mqtt-acceptor", "--no-amqp-acceptor", "--max-hops", "1", "--name", DC1_NODE, "--cluster-user", "my-cluster-user", "--cluster-password", "my-cluster-password");
      cliCreateServer.setPortOffset(portOffset);
      cliCreateServer.setClustered(true);
      cliCreateServer.setReplicated(true);
      cliCreateServer.setBackup(true);
      cliCreateServer.setStaticCluster(clusterStatic);
      cliCreateServer.createServer();

      Properties brokerProperties = new Properties();
      brokerProperties.put("messageExpiryScanPeriod", "1000");
      brokerProperties.put("AMQPConnections.mirror.uri", mirrorURI);
      brokerProperties.put("AMQPConnections.mirror.retryInterval", "1000");
      brokerProperties.put("AMQPConnections.mirror.type", AMQPBrokerConnectionAddressType.MIRROR.toString());
      brokerProperties.put("AMQPConnections.mirror.connectionElements.mirror.sync", "false");
      brokerProperties.put("largeMessageSync", "false");

      brokerProperties.put("mirrorAckManagerQueueAttempts", "5");
      brokerProperties.put("mirrorAckManagerPageAttempts", "500000");
      brokerProperties.put("mirrorAckManagerRetryDelay", "500");

      // if we don't use pageTransactions we may eventually get a few duplicates
      brokerProperties.put("mirrorPageTransaction", "true");

      if (paging) {
         brokerProperties.put("addressSettings.#.maxSizeMessages", "1");
         brokerProperties.put("addressSettings.#.maxReadPageMessages", "2000");
         brokerProperties.put("addressSettings.#.maxReadPageBytes", "-1");
         brokerProperties.put("addressSettings.#.prefetchPageMessages", "500");
         // if we don't use pageTransactions we may eventually get a few duplicates
         brokerProperties.put("mirrorPageTransaction", "true");
      }

      File brokerPropertiesFile = new File(serverLocation, "broker.properties");
      saveProperties(brokerProperties, brokerPropertiesFile);

      File brokerXml = new File(serverLocation, "/etc/broker.xml");
      assertTrue(brokerXml.exists());
      assertTrue(FileUtil.findReplace(brokerXml, "amqpDuplicateDetection=true;", "amqpDuplicateDetection=true;ackManagerFlushTimeout=" + TimeUnit.MINUTES.toMillis(10) + ";"));
      assertTrue(FileUtil.findReplace(brokerXml, "<backup/>", "<backup><allow-failback>true</allow-failback></backup>"));

      if (TRACE_LOGS) {
         replaceLogs(serverLocation);
      }
   }

   public static void createRealServers(boolean paging) throws Exception {
      createMirroredServer(paging, DC1_NODE, "mirror", uriWithAlternate(DC2_IP, DC2_BACKUP_IP), 0, true, uri(DC1_BACKUP_IP));
      createMirroredBackupServer(paging, DC1_REPLICA_NODE, 1, uri(DC1_IP), uriWithAlternate(DC2_IP, DC2_BACKUP_IP));
      createMirroredServer(paging, DC2_NODE, "mirror", uriWithAlternate(DC1_IP, DC1_BACKUP_IP), 2, true, uri(DC2_BACKUP_IP));
      createMirroredBackupServer(paging, DC2_REPLICA_NODE, 3, uri(DC2_IP), uriWithAlternate(DC1_IP, DC1_BACKUP_IP));
   }

   @Test
   public void testFailoverLaterStart() throws Exception {
      testMirror(true);
   }

   @Test
   public void testFailoverWhileMirroring() throws Exception {
      testMirror(false);
   }

   private void testMirror(boolean laterStart) throws Exception {
      createRealServers(true);

      SimpleManagement managementDC1 = new SimpleManagement(uri(DC1_IP), null, null);
      SimpleManagement managementDC2 = new SimpleManagement(uri(DC2_IP), null, null);

      startDC1(managementDC1);

      if (!laterStart) {
         startDC2(managementDC2);
      }

      runAfter(() -> managementDC1.close());
      runAfter(() -> managementDC2.close());

      sendMessages(QUEUE_NAME);

      processDC1.destroyForcibly();
      processDC1.waitFor(10, TimeUnit.SECONDS);

      if (laterStart) {
         startDC2(managementDC2);
      }

      // Mirror failover could challenge the order
      Set<Integer> receivedIDs = new HashSet<>();

      ConnectionFactory connectionFactoryDC2 = CFUtil.createConnectionFactory("amqp", uri(DC2_IP));
      try (Connection connection = connectionFactoryDC2.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         connection.start();
         MessageConsumer consumer = session.createConsumer(session.createQueue(QUEUE_NAME));
         for (int i = 0; i < NUMBER_MESSAGES; i++) {
            TextMessage message = (TextMessage) consumer.receive(30_000);
            assertNotNull(message);
            receivedIDs.add(message.getIntProperty("i"));
            if (i > 0 && i % SEND_COMMIT == 0) {
               logger.info("Received {} messages", i);
               session.commit();
            }
         }

         session.commit();
      }

      assertEquals(NUMBER_MESSAGES, receivedIDs.size());
      for (int i = 0; i < NUMBER_MESSAGES; i++) {
         assertTrue(receivedIDs.contains(i));
      }
   }

   @Test
   public void testQuickACKRandomProtocol() throws Exception {
      String protocol = randomProtocol();
      logger.info("using protocol {}", protocol);

      // There shouldn't be any semantic difference for the test based on the protocol we choose to run
      // However I will make this a random choice to make sure we cover eventually all the protocols.
      // I didn't want to waste time on the testsuite/CI running all 3 protocols on every run for this case
      testQuickACK(protocol);
   }

   private void testQuickACK(final String protocol) throws Exception {
      createRealServers(false);


      SimpleManagement managementDC1 = new SimpleManagement(uri(DC1_IP), null, null);
      SimpleManagement managementDC2 = new SimpleManagement(uri(DC2_IP), null, null);
      SimpleManagement managementDC2Backup = new SimpleManagement(uri(DC2_BACKUP_IP), null, null);

      startDC1(managementDC1);
      ConnectionFactory connectionFactoryDC1A = CFUtil.createConnectionFactory(protocol, uri(DC1_IP));

      final int startAt = 300;
      final int killAt = 500;
      final int failbackAt = 700;
      final int lastKillAt = 1200;
      final int totalMessages = 1_800;

      try (Connection connection = connectionFactoryDC1A.createConnection()) {
         connection.start();
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queue = session.createQueue(QUEUE_NAME);
         MessageProducer producer = session.createProducer(queue);
         MessageConsumer consumer = session.createConsumer(queue);

         for (int i = 0; i < totalMessages; i++) {
            if (i % 100 == 0) {
               logger.info("Sent and received {}", i);
            }

            if (i == startAt) {
               // lazy start to allow messages accumulated in the SNF
               processDC2 = startServer(DC2_NODE, -1, -1, new File(getServerLocation(DC2_NODE), "broker.properties"));
               processDC2_REPLICA = startServer(DC2_REPLICA_NODE, -1, -1, new File(getServerLocation(DC2_REPLICA_NODE), "broker.properties"));
            } else if (i == killAt || i == lastKillAt) { // kill the live on DC2
               logger.info("KillAt {}", killAt);
               ServerUtil.waitForServerToStart(2, 10_000);
               Wait.assertTrue(managementDC2::isReplicaSync);
               processDC2.destroyForcibly();
               assertTrue(processDC2.waitFor(10, TimeUnit.SECONDS));
            } else if (i == failbackAt) {
               logger.info("Failing back at {}", failbackAt);
               processDC2 = startServer(DC2_NODE, -1, -1, new File(getServerLocation(DC2_NODE), "broker.properties"));
               ServerUtil.waitForServerToStart(2, 10_000);
            }

            String text = "hello hello hello " + i;
            TextMessage message = session.createTextMessage(text);
            message.setIntProperty("i", i);
            producer.send(message);
            TextMessage textMessage = (TextMessage) consumer.receive(5000);
            assertNotNull(textMessage);
            assertEquals(text, textMessage.getText());
         }

      }

      final int oddSend = 33;
      try (Connection connection = connectionFactoryDC1A.createConnection()) {
         connection.start();
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         Queue queue = session.createQueue(QUEUE_NAME);
         MessageProducer producer = session.createProducer(queue);
         for (int i = 0; i < oddSend; i++) {
            TextMessage message = session.createTextMessage("oddSend " + i);
            message.setIntProperty("oddSend", i);
            producer.send(message);
         }
         session.commit();
      }

      Wait.assertEquals(0, () -> getMessageCount(managementDC1, SNF_QUEUE));
      Wait.assertEquals(oddSend, () -> getMessageCount(managementDC1, QUEUE_NAME));
      Wait.assertEquals(oddSend, () -> getMessageCount(managementDC2Backup, QUEUE_NAME));

      ConnectionFactory cfDC2Backup = CFUtil.createConnectionFactory(protocol, uri(DC2_BACKUP_IP));

      try (Connection connection = cfDC2Backup.createConnection()) {
         connection.start();
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         Queue queue = session.createQueue(QUEUE_NAME);
         MessageConsumer consumer = session.createConsumer(queue);
         for (int i = 0; i < oddSend; i++) {
            TextMessage message = (TextMessage) consumer.receive(5000);
            assertNotNull(message);
            assertEquals("oddSend " + i, message.getText());
         }
         assertNull(consumer.receiveNoWait());
         session.commit();
      }

      Wait.assertEquals(0, () -> getMessageCount(managementDC1, SNF_QUEUE));
      Wait.assertEquals(0, () -> getMessageCount(managementDC2Backup, SNF_QUEUE));
      Wait.assertEquals(0, () -> getMessageCount(managementDC1, QUEUE_NAME));
      Wait.assertEquals(0, () -> getMessageCount(managementDC2Backup, QUEUE_NAME));

      LogAssert.assertServerLogsForMirror(getFileServerLocation(DC1_NODE));
      LogAssert.assertServerLogsForMirror(getFileServerLocation(DC1_REPLICA_NODE));
      LogAssert.assertServerLogsForMirror(getFileServerLocation(DC2_NODE));
      LogAssert.assertServerLogsForMirror(getFileServerLocation(DC2_REPLICA_NODE));

   }

   @Test
   public void testMultipleSenders() throws Exception {
      try {
         lsof();
      } catch (IOException e) {
         logger.warn("lsof is not available in this platform, we will ignore this test - {}", e.getMessage(), e);
         Assumptions.abort("lsof is not available");
      }
      createRealServers(true);

      SimpleManagement managementDC1 = new SimpleManagement(uri(DC1_IP), null, null);
      SimpleManagement managementDC2 = new SimpleManagement(uri(DC2_IP), null, null);

      startDC1(managementDC1);
      startDC2(managementDC2);

      runAfter(managementDC1::close);
      runAfter(managementDC2::close);

      int destinations = 5;
      ExecutorService executorService = Executors.newFixedThreadPool(destinations);
      runAfter(executorService::shutdownNow);

      CountDownLatch latch = new CountDownLatch(destinations);
      AtomicInteger errors = new AtomicInteger(0);

      for (int i = 0; i < destinations; i++) {
         String destination = "queue" + i;
         executorService.execute(() -> {
            try {
               sendMessages(destination);
            } catch (Throwable e) {
               logger.warn(e.getMessage(), e);
               errors.incrementAndGet();
            } finally {
               latch.countDown();
            }
         });
      }

      assertTrue(latch.await(5, TimeUnit.MINUTES));

      int openFiles = lsof();

      logger.info("There are {} open files", openFiles);

      // lsof is showing a file descriptor associated with multiple threads. So it is expected to have quite a few repetitions
      // when the issue is happening we would have around 40k, 50k entries or a lot more if you add more messages.
      assertTrue(openFiles < 4000, () -> "There was " + openFiles + " open files");
      assertEquals(0, errors.get(), "There are errors on the senders");

   }

   private int lsof() throws IOException, InterruptedException {
      ProcessBuilder lsofBuilder = new ProcessBuilder();
      lsofBuilder.command("lsof", "-n", "-P");

      Process process = lsofBuilder.start();
      runAfter(process::destroyForcibly);

      InputStream inputStream = process.getInputStream();
      BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
      AtomicInteger filesCounter = new AtomicInteger();
      try (Stream<String> lines = reader.lines()) {
         lines.filter(line -> line.contains(basedir)).forEach(l -> {
            logger.info("file {}", l);
            filesCounter.incrementAndGet();
         });
      }
      assertTrue(process.waitFor(10, TimeUnit.SECONDS));
      return filesCounter.get();
   }

   private static void sendMessages(String queueName) throws JMSException {
      long start = System.currentTimeMillis();
      ConnectionFactory connectionFactoryDC1A = CFUtil.createConnectionFactory("amqp", uri(DC1_IP));
      try (Connection connection = connectionFactoryDC1A.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer producer = session.createProducer(session.createQueue(queueName));
         for (int i = 0; i < NUMBER_MESSAGES; i++) {
            TextMessage message = session.createTextMessage(body);
            message.setIntProperty("i", i);
            message.setBooleanProperty("large", false);
            producer.send(message);
            if (i > 0 && i % SEND_COMMIT == 0) {
               logger.info("Sent {} messages on {}", i, queueName);
               session.commit();

               long timePassed = System.currentTimeMillis() - start;
               double secondsPassed = timePassed / 1000f;
               logger.info("sent {} messages, msgs/second = {}", i, (i / secondsPassed));
            }
         }

         session.commit();
      }
   }
}