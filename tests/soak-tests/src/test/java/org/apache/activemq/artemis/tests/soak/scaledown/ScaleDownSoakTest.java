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
package org.apache.activemq.artemis.tests.soak.scaledown;

import static org.apache.activemq.artemis.utils.TestParameters.testProperty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.api.core.management.SimpleManagement;
import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.io.nio.NIOSequentialFileFactory;
import org.apache.activemq.artemis.core.journal.RecordInfo;
import org.apache.activemq.artemis.core.journal.impl.JournalFile;
import org.apache.activemq.artemis.core.journal.impl.JournalImpl;
import org.apache.activemq.artemis.core.journal.impl.JournalReaderCallback;
import org.apache.activemq.artemis.core.persistence.impl.journal.DescribeJournal;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds;
import org.apache.activemq.artemis.json.JsonArray;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.tests.soak.SoakTestBase;
import org.apache.activemq.artemis.util.ServerUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Soak test verifying that scale-down (message migration to surviving nodes on graceful shutdown)
 * preserves message integrity under concurrent producers and consumers across various cluster
 * topologies and restart patterns.
 *
 * <p>Each broker is configured with {@code scaleDownConfiguration.enabled=true} so that when a
 * node shuts down gracefully it drains its queues to a peer rather than losing messages.
 * Redistribution delay is set to zero to keep messages moving immediately between nodes.
 *
 * <p>Test parameters can be overridden at runtime via system properties prefixed with
 * {@code SCALE_DOWN_} (e.g. {@code -DSCALE_DOWN_NUMBER_OF_SERVERS=3}).
 */
public class ScaleDownSoakTest extends SoakTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final String TEST_NAME = "SCALE_DOWN";
   private static final String TEST_MSG_ID = "TEST_MSG_ID";
   private static final int NUMBER_OF_RESTARTS = testProperty(TEST_NAME, "NUMBER_OF_RESTARTS", 9);
   private static final int NUMBER_OF_SERVERS = testProperty(TEST_NAME, "NUMBER_OF_SERVERS", 9);
   private static final String MESSAGE_LOAD_BALANCING = testProperty(TEST_NAME, "MESSAGE_LOAD_BALANCING", "ON_DEMAND");
   private static final int CONSUMER_TIMEOUT = testProperty(TEST_NAME, "CONSUMER_TIMEOUT", 1000);
   // Steady-state run between restarts. This must be long enough for the just-restarted node to
   // rejoin the cluster and for consumers to actually drain the queue back down; otherwise (e.g. in
   // the single-consumer scenario with redistributionDelay=0) the backlog on the restarted node only
   // grows, every shutdown has more to scale down, and the node is restarted again mid-churn while
   // its scale-down target is still saturated. A couple of seconds keeps each scale-down bounded.
   private static final int UP_TIME = testProperty(TEST_NAME, "UP_TIME", 5000);
   // Budget for a graceful stop to complete. Shutdown drains queues via scale-down over the SnF
   // bridges (each of which can hit its ~60s stop timeout) and then transfers messages to a peer, so
   // the budget must sit comfortably above the bridge-stop timeout. It is also a hard upper bound: if
   // a drain stalls (the scale-down authorise/transfer can block while the connection stays alive),
   // waitForServer() force-kills the node and the restart fails fast with a clear message rather than
   // hanging. Keep this >= CLUSTER_TIMEOUT_MS so topology convergence is not the first thing to trip.
   private static final int CLIENT_STOP_TIMEOUT_MS = testProperty(TEST_NAME, "CLIENT_STOP_TIMEOUT_MS", 30_000);
   private static final int SHUTDOWN_TIMEOUT_MS = testProperty(TEST_NAME, "SHUTDOWN_TIMEOUT_MS", 180_000);
   private static final int CLUSTER_TIMEOUT_MS = testProperty(TEST_NAME, "CLUSTER_TIMEOUT_MS", 120_000);
   private static final int SERVER_START_TIMEOUT_MS = testProperty(TEST_NAME, "SERVER_START_TIMEOUT_MS", 30_000);
   // When enabled, the brokers log DEBUG for the cluster/scale-down classes so a focused
   // reproduction can trace the SnF relocate drain (moved counts, flow-record lifecycle).
   // Off by default to keep ordinary soak runs quiet; enable with -DTEST_SCALE_DOWN_DEBUG_LOGGING=true.
   private static final boolean DEBUG_LOGGING =
      Boolean.parseBoolean(testProperty(TEST_NAME, "DEBUG_LOGGING", "false"));
   private static final boolean HALT_ON_OTHER_DUPLICATES =
      Boolean.parseBoolean(testProperty(TEST_NAME, "HALT_ON_OTHER_DUPLICATES", "false"));

   private static final String QUEUE_NAME = "TEST_SD";
   private static final String SERVER_NAME_BASE = "scaledown/broker-";
   private static final int SERVER_PORT_BASE = 61616;
   private static final String CLUSTER_CONNECTION_NAME = "my-cluster";

   static class ServerRecord {
      String serverName;
      File serverLocation;
      File brokerProperties;
      Process process;
      int serverId;
      boolean running;

      ServerRecord(File serverLocation, String serverName, int id) {
         this.serverLocation = serverLocation;
         this.brokerProperties = new File(serverLocation, "broker.properties");
         this.serverName = serverName;
         this.serverId = id;
      }

      public void startServer() throws Exception {
         running = true;
         process = ServerUtil.startServer(serverLocation.getAbsolutePath(), serverName, 0, 0, brokerProperties, null);
      }

      public SimpleManagement getManagement() {
         return new SimpleManagement("tcp://localhost:" + (SERVER_PORT_BASE + serverId), null, null);
      }

      public void stopServer() throws IOException {
         running = false;
         if (process.isAlive()) {
            stopServerWithFile(serverLocation.getAbsolutePath());
         }
      }

      public static int[] getStartedNodeIds() {
         return Arrays.stream(serverRecords).filter(serverRecord -> serverRecord.running).mapToInt(value -> value.serverId).toArray();
      }

      public static int[] getStoppedNodeIds() {
         return Arrays.stream(serverRecords).filter(serverRecord -> !serverRecord.running).mapToInt(value -> value.serverId).toArray();
      }

      public void waitForServer() throws InterruptedException {
         assertTrue(process.waitFor(SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS),
            "Server " + serverName + " did not stop within " + SHUTDOWN_TIMEOUT_MS + "s");
         assertEquals(0, process.exitValue(), "Server " + serverName + " exited with non-zero code");
      }

      private String serverURI() {
         return "tcp://localhost:" + (SERVER_PORT_BASE + serverId) + "?ha=false&useTopologyForLoadBalancing=false";
      }
   }

   private static final ServerRecord[] serverRecords = new ServerRecord[NUMBER_OF_SERVERS];

   AtomicInteger totalReceived;
   AtomicInteger totalSent;
   AtomicBoolean producersStopping;
   AtomicBoolean consumersStopping;
   Set<String> sentMessageIds;
   Map<String, MessageRecord> messageRecords;

   static final class MessageRecord {
      final String id;
      final int firstConsumerId;
      final int firstServerId;
      final String firstJMSMessageId;
      final AtomicBoolean duplicate = new AtomicBoolean();
      volatile int dupConsumerId = -1;
      volatile int dupServerId = -1;
      volatile String dupJMSMessageId;

      MessageRecord(String id, int firstConsumerId, int firstServerId, String firstJMSMessageId) {
         this.id = id;
         this.firstConsumerId = firstConsumerId;
         this.firstServerId = firstServerId;
         this.firstJMSMessageId = firstJMSMessageId == null ? "<null>" : firstJMSMessageId;
      }

      boolean markDuplicate(int consumerId, int serverId, String jmsMessageId) {
         if (!duplicate.compareAndSet(false, true)) {
            return false;
         }
         dupConsumerId = consumerId;
         dupServerId = serverId;
         dupJMSMessageId = jmsMessageId == null ? "<null>" : jmsMessageId;
         return true;
      }

      boolean isDuplicate() {
         return duplicate.get();
      }
   }

   CountDownLatch consumersDone;
   CountDownLatch producersDone;

   /**
    * Skips the remaining tests of this class (including their {@code @BeforeEach}/{@code @AfterEach})
    * once one of them has failed, since each test is very long and a failure usually makes the
    * following ones meaningless.
    */
   private static class SkipAfterFirstFailure implements TestWatcher, ExecutionCondition {

      private volatile String failedTest;

      void reset() {
         failedTest = null;
      }

      @Override
      public void testFailed(ExtensionContext context, Throwable cause) {
         if (failedTest == null) {
            failedTest = context.getDisplayName();
         }
      }

      @Override
      public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
         if (failedTest != null && context.getTestMethod().isPresent()) {
            return ConditionEvaluationResult.disabled("Skipped because " + failedTest + " failed");
         }
         return ConditionEvaluationResult.enabled("No previous failure");
      }
   }

   @RegisterExtension
   static final SkipAfterFirstFailure skipAfterFirstFailure = new SkipAfterFirstFailure();

   @BeforeAll
   public static void createServers() throws Exception {
      skipAfterFirstFailure.reset();

      for (int s = 0; s < NUMBER_OF_SERVERS; s++) {
         String serverName = SERVER_NAME_BASE + s;

         File serverLocation = getFileServerLocation(serverName);
         deleteDirectory(serverLocation);

         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer
            .setRole("amq")
            .setUser("admin")
            .setPassword("admin")
            .setAllowAnonymous(true)
            .setNoWeb(true)
            .setArtemisInstance(serverLocation)
            .setPortOffset(s)
            .setClustered(true);

         cliCreateServer.setMessageLoadBalancing(MESSAGE_LOAD_BALANCING);
         cliCreateServer.setStaticCluster(buildStaticClusterURI(s));
         cliCreateServer.setArgs("--no-stomp-acceptor", "--no-hornetq-acceptor",
            "--no-mqtt-acceptor", "--no-amqp-acceptor",
            "--max-hops", "1",
            "--cluster-user", "cluster-user",
            "--cluster-password", "cluster-password",
            "--queues", QUEUE_NAME);

         cliCreateServer.createServer();

         if (DEBUG_LOGGING) {
            enableClusterDebugLogging(serverLocation);
         }

         Properties brokerProperties = new Properties();
         brokerProperties.put("addressesSettings.#.redistributionDelay", "0");
         // 0 disables compaction so history stays in the journal for duplicate analysis
         brokerProperties.put("journalCompactMinFiles", "0");
         brokerProperties.put("HAPolicyConfiguration", "PRIMARY_ONLY");
         brokerProperties.put("HAPolicyConfiguration.scaleDownConfiguration.enabled", "true");
         brokerProperties.put("HAPolicyConfiguration.scaleDownConfiguration.connectors", buildScaleDownConnectors());
         saveProperties(brokerProperties, new File(serverLocation, "broker.properties"));
         serverRecords[s] = new ServerRecord(serverLocation, serverName, s);
      }
   }

   /**
    * Append DEBUG logger config for the cluster/scale-down classes to a broker instance's
    * {@code etc/log4j2.properties}. This surfaces the SnF relocate drain iterations
    * ({@code BridgeImpl}), the flow-record / SnF-delete lifecycle ({@code ClusterConnectionBridge},
    * {@code ClusterConnectionImpl}) and the node self scale-down ({@code ScaleDownHandler}).
    */
   private static void enableClusterDebugLogging(File serverLocation) throws Exception {
      String debugLoggers = String.join(System.lineSeparator(),
         "",
         "# Enabled by ScaleDownSoakTest (-DTEST_SCALE_DOWN_DEBUG_LOGGING=true) for scale-down reproduction",
         "logger.scaledown_cluster.name=org.apache.activemq.artemis.core.server.cluster",
         "logger.scaledown_cluster.level=DEBUG",
         "logger.scaledown_handler.name=org.apache.activemq.artemis.core.server.impl.ScaleDownHandler",
         "logger.scaledown_handler.level=DEBUG",
         "");
      File log4j = new File(serverLocation, "etc/log4j2.properties");
      Files.writeString(log4j.toPath(), debugLoggers, StandardOpenOption.APPEND);
   }

   private static String joinServers(int excludeIndex, IntFunction<String> token) {
      return IntStream.range(0, NUMBER_OF_SERVERS)
         .filter(i -> i != excludeIndex)
         .mapToObj(token::apply)
         .collect(Collectors.joining(","));
   }

   private static String buildStaticClusterURI(int excludeIndex) {
      return joinServers(excludeIndex, i -> "tcp://localhost:" + (SERVER_PORT_BASE + i));
   }

   private static String buildScaleDownConnectors() {
      // The static-cluster helper names the N-1 peer connectors positionally as
      // node0..node(N-2) on every broker (independent of the broker's own id), so the
      // scale-down list must reference those actual connector names. Using id-based
      // names here (e.g. node8) references a connector that does not exist, which makes
      // scale-down fail with an NPE on every shutdown and silently loses any messages
      // that still need migrating once consumers have stopped.
      return IntStream.range(0, NUMBER_OF_SERVERS - 1)
         .mapToObj(i -> "node" + i)
         .collect(Collectors.joining(","));
   }

   @BeforeEach
   public void beforeEach() throws Exception {
      totalReceived = new AtomicInteger(0);
      totalSent = new AtomicInteger(0);
      producersStopping = new AtomicBoolean(false);
      consumersStopping = new AtomicBoolean(false);
      sentMessageIds = ConcurrentHashMap.newKeySet();
      messageRecords = new ConcurrentHashMap<>();

      for (int i = 0; i < NUMBER_OF_SERVERS; i++) {
         cleanupData(SERVER_NAME_BASE + i);
      }

      for (ServerRecord serverRecord : serverRecords) {
         serverRecord.startServer();
      }

      waitForCluster(IntStream.range(0, NUMBER_OF_SERVERS).toArray(), new int[0]);
   }

   @AfterEach
   public void afterEach() throws Exception {
      stopProducers();

      stopConsumers();

      stopServers();
   }

   /**
    * All nodes produce and consume from the same queue. Node 0 is restarted repeatedly to verify
    * that scale-down migrates its messages to the remaining nodes and that those messages are
    * eventually consumed without loss.
    */
   @Test
   public void testSymmetricNodesWithSingleNodeRestarts() throws Exception {
      int[] allServers = IntStream.range(0, NUMBER_OF_SERVERS).toArray();
      runRestartScenario(allServers, allServers, i -> 0);
   }

   /**
    * All nodes produce and consume from the same queue. Nodes are restarted in round-robin order
    * to stress scale-down across every broker in turn, ensuring no node is a special case for
    * message migration.
    */
   @Test
   public void testSymmetricNodesWithSequentialNodeRestarts() throws Exception {
      int[] allServers = IntStream.range(0, NUMBER_OF_SERVERS).toArray();
      runRestartScenario(allServers, allServers, i -> i % serverRecords.length);
   }

   /**
    * All nodes produce, but only node 0 consumes. Node 0 is restarted repeatedly, forcing
    * scale-down to migrate its in-flight messages to a peer on each shutdown and verifying that
    * the consumer picks them up again after restart without duplication or loss.
    */
   @Test
   public void testSingleConsumerNodeWithConsumerNodeRestarts() throws Exception {
      int[] allServers = IntStream.range(0, NUMBER_OF_SERVERS).toArray();
      runRestartScenario(allServers, new int[]{0}, i -> 0);
   }

   /**
    * All nodes produce, but only node 0 consumes. Nodes are restarted in round-robin order
    * to stress scale-down across every broker in turn, ensuring no node is a special case for
    * message migration.
    */
   @Test
   public void testSingleConsumerNodeWithSequentialNodeRestarts() throws Exception {
      int[] allServers = IntStream.range(0, NUMBER_OF_SERVERS).toArray();
      runRestartScenario(allServers, new int[]{0}, i -> i % serverRecords.length);
   }

   /**
    * Nodes have different roles: some produce only, some consume only, some do both, and two act
    * as pure routing nodes with no local producers or consumers. Nodes are restarted in round-robin
    * order to verify that scale-down correctly migrates messages regardless of whether the
    * restarted node was a producer, consumer, or pure relay.
    */
   @Test
   public void testAsymmetricNodesWithRestarts() throws Exception {
      // nodes 0,1: consumers only
      // nodes 2,3,4: producers only
      // nodes 5,6: both producers and consumers
      // nodes 7,8: pure routing (neither producer nor consumer)
      int[] producerNodes = {2, 3, 4, 5, 6};
      int[] consumerNodes = {0, 1, 5, 6};
      runRestartScenario(producerNodes, consumerNodes, i -> i % serverRecords.length);
   }

   /**
    * Shared scenario driver: wire up producers/consumers, then repeatedly let the cluster run for
    * {@code UP_TIME} and restart a node chosen by {@code nodeToRestart} (given the iteration index).
    * Finally stop producers, drain, stop consumers/servers and assert integrity via {@link #checkStatus()}.
    */
   private void runRestartScenario(int[] producerNodes, int[] consumerNodes, IntUnaryOperator nodeToRestart) throws Exception {
      setupNodes(producerNodes, consumerNodes);

      for (int i = 0; i < NUMBER_OF_RESTARTS; i++) {
         // intentional uptime wait — lets the cluster run normally before each restart cycle
         Thread.sleep(UP_TIME);
         stopServer(nodeToRestart.applyAsInt(i));
         startServer(nodeToRestart.applyAsInt(i));
      }

      for (int i = 1; i < NUMBER_OF_RESTARTS; i++) {
         stopServer(i);
      }

      stopProducers();
      waitForMessages();
      stopConsumers();
      stopServers();
      checkStatus();
   }

   private void setupNodes(int[] producerNodes, int[] consumerNodes) throws Exception {
      ExecutorService executorService = Executors.newFixedThreadPool(producerNodes.length + consumerNodes.length);
      runAfter(executorService::shutdownNow);

      producersDone = new CountDownLatch(producerNodes.length);
      consumersDone = new CountDownLatch(consumerNodes.length);

      int consumerId = 0;
      for (int nodeId : consumerNodes) {
         final int idx = nodeId;
         final int id = consumerId++;
         executorService.execute(() -> consumeMessages(id, serverRecords[idx]));
      }

      int producerId = 0;
      for (int nodeId : producerNodes) {
         final int idx = nodeId;
         final int id = producerId++;
         executorService.execute(() -> produceMessages(id, serverRecords[idx]));
      }
   }

   private void startServer(int serverId) throws Exception {
      logger.info("Starting server {}", serverId);
      serverRecords[serverId].startServer();
      logger.info("Started server {}", serverId);
   }

   private void stopServer(int serverId) throws Exception {
      logger.info("Stopping server {}", serverId);
      waitForCluster(ServerRecord.getStartedNodeIds(), ServerRecord.getStoppedNodeIds());
      serverRecords[serverId].stopServer();
      serverRecords[serverId].waitForServer();
      waitForCluster(ServerRecord.getStartedNodeIds(), ServerRecord.getStoppedNodeIds());
      logger.info("Stopped server {}", serverId);
   }

   private void produceMessages(int producerId, ServerRecord serverRecord) {
      Thread.currentThread().setName("Producer " + producerId + " server=" + serverRecord.serverId);
      try {
         int localSent = 0;
         int messageIndex = 0;
         while (!producersStopping.get()) {
            try (ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(serverRecord.serverURI());
                 Connection connection = factory.createConnection()) {
               Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
               MessageProducer producer = session.createProducer(session.createQueue(QUEUE_NAME));

               while (!producersStopping.get()) {
                  String messageId = "prd-" + producerId + "-srv-" + serverRecord.serverId + "-msg-" + messageIndex++;
                  TextMessage message = session.createTextMessage(String.valueOf(localSent));
                  message.setStringProperty(TEST_MSG_ID, messageId);
                  producer.send(message);
                  sentMessageIds.add(messageId);
                  localSent++;
                  int sent = totalSent.incrementAndGet();
                  if (localSent % 100 == 0) {
                     logger.info("Producer {} server={} localSent={} totalSent={}", producerId, serverRecord.serverId, localSent, sent);
                  }
               }
            } catch (Exception e) {
               logger.info("Producer {} server={} reconnecting after: {}", producerId, serverRecord.serverId, e.getMessage());
               Thread.sleep(1000);
            }
         }
         logger.info("Producer {} server={} finished localSent={} totalSent={}", producerId, serverRecord.serverId, localSent, totalSent.get());
      } catch (InterruptedException e) {
         logger.info("Producer {} server={} interrupted", producerId, serverRecord.serverId);
      } finally {
         producersDone.countDown();
      }
   }

   private void consumeMessages(int consumerId, ServerRecord serverRecord) {
      Thread.currentThread().setName("Consumer " + consumerId + " server=" + serverRecord.serverId);
      try {
         int localReceived = 0;
         while (!consumersStopping.get()) {
            try (ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(serverRecord.serverURI());
                 Connection connection = factory.createConnection()) {
               connection.start();
               Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
               MessageConsumer consumer = session.createConsumer(session.createQueue(QUEUE_NAME));

               int missedReceive = 0;
               while (!consumersStopping.get()) {
                  Message msg = consumer.receive(CONSUMER_TIMEOUT);
                  if (msg != null) {
                     missedReceive = 0;
                     localReceived++;
                     String messageID = msg.getStringProperty(TEST_MSG_ID);
                     MessageRecord record = new MessageRecord(messageID, consumerId, serverRecord.serverId, msg.getJMSMessageID());
                     MessageRecord existing = messageRecords.putIfAbsent(messageID, record);
                     if (existing == null) {
                        totalReceived.incrementAndGet();
                        if (!sentMessageIds.contains(messageID)) {
                           logger.warn("Consumer {} server={} received ghost-send (producer lost ACK): id={}",
                              consumerId, serverRecord.serverId, messageID);
                        }
                     } else if (existing.markDuplicate(consumerId, serverRecord.serverId, msg.getJMSMessageID())) {
                        logger.warn("Consumer {} server={} received duplicate: --id '{}' --first-consumer-id {} --first-server-id {} --first-jms-id '{}' --consumer-id {} --server-id {} --jms-id '{}'",
                           consumerId, serverRecord.serverId, messageID, existing.firstConsumerId, existing.firstServerId, existing.firstJMSMessageId, consumerId, serverRecord.serverId, existing.dupJMSMessageId);
                     }
                     if (localReceived % 100 == 0) {
                        logger.info("Consumer {} server={} locallyReceived={} totalReceived={} duplicates={}",
                           consumerId, serverRecord.serverId, localReceived, totalReceived, countDuplicates());
                     }
                  } else {
                     missedReceive++;
                     logger.info("Consumer {} server={} received null, totalSent={}, totalReceived={}, retried for {}", consumerId, serverRecord.serverId, totalSent.get(), totalReceived.get(), missedReceive);
                  }
               }
            } catch (Exception e) {
               logger.info("Consumer {} server={} reconnecting after: {}", consumerId, serverRecord.serverId, e.getMessage());
               Thread.sleep(1000);
            }
         }
         logger.info("Consumer {} server={} finished, received {} messages, duplicates={}",
            consumerId, serverRecord.serverId, localReceived, countDuplicates());
      } catch (InterruptedException e) {
         logger.info("Consumer {} server={} interrupted", consumerId, serverRecord.serverId);
      } finally {
         consumersDone.countDown();
      }
   }

   private void stopProducers() throws Exception {
      producersStopping.set(true);

      assertTrue(producersDone.await(CLIENT_STOP_TIMEOUT_MS, TimeUnit.MINUTES), "Producers did not complete in time");
      logger.info("Test producers done: totalSent={}, totalReceived={}, duplicates={}",
         totalSent.get(), totalReceived.get(), countDuplicates());
   }

   private void waitForMessages() {
      Wait.assertTrue(() -> {
         long duplicates = countDuplicates();
         long ghostSends = countGhostSends();
         long lostMessages = countLostMessages();
         long queueMessages = countQueueMessages();
         long snfMessages = countSnFMessages();

         logger.info("Test waiting: sent={}, received={}, lostMessages={}, ghostSends={}, duplicates={}, queueMessages={}, snfMessages={}",
            totalSent.get(), totalReceived.get(), lostMessages, ghostSends, duplicates, queueMessages, snfMessages);

         return lostMessages == 0 && queueMessages == 0 && snfMessages == 0;
      }, 30_000, 1_000);
   }

   private void stopConsumers() throws Exception {
      consumersStopping.set(true);

      assertTrue(consumersDone.await(CLIENT_STOP_TIMEOUT_MS, TimeUnit.MINUTES), "Consumers did not complete in time");
      logger.info("Test consumers done: totalSent={}, totalReceived={}, duplicates={}",
         totalSent.get(), totalReceived.get(), countDuplicates());
   }

   private void stopServers() throws Exception {
      for (int i = 0; i < NUMBER_OF_SERVERS; i++) {
         serverRecords[i].stopServer();
      }
      for (int i = 0; i < NUMBER_OF_SERVERS; i++) {
         serverRecords[i].waitForServer();
      }
   }

   private void checkStatus() throws Exception {
      Set<String> receivedIds = messageRecords.keySet();
      Set<String> duplicateIds = messageRecords.values().stream()
         .filter(MessageRecord::isDuplicate)
         .map(r -> r.id)
         .collect(Collectors.toCollection(HashSet::new));

      Set<String> ghostSends = new HashSet<>(receivedIds);
      ghostSends.removeAll(sentMessageIds);

      Set<String> lostMessages = new HashSet<>(sentMessageIds);
      lostMessages.removeAll(receivedIds);

      Set<String> unackedDeliveryDupIds = scanJournalsForConsumerRedelivery(duplicateIds);
      Set<String> otherDupIds = new HashSet<>(duplicateIds);
      otherDupIds.removeAll(unackedDeliveryDupIds);

      logger.info("Test finished: sent={}, received={}, lostMessages={}, ghostSends={}, duplicates={}, unackedDeliveryDuplicates={}, otherDuplicates={}",
         sentMessageIds.size(), receivedIds.size(), lostMessages.size(), ghostSends.size(), duplicateIds.size(), unackedDeliveryDupIds.size(), otherDupIds.size());

      for (String dupId : unackedDeliveryDupIds) {
         MessageRecord record = messageRecords.get(dupId);
         logger.warn("  unacked-delivery dup: --id '{}' --first-consumer-id {} --first-server-id {} --first-jms-id '{}' --consumer-id {} --server-id {} --jms-id '{}'",
            dupId, record.firstConsumerId, record.firstServerId, record.firstJMSMessageId,
            record.dupConsumerId, record.dupServerId, record.dupJMSMessageId);
      }
      for (String dupId : otherDupIds) {
         MessageRecord record = messageRecords.get(dupId);
         logger.warn("  other dup: --id '{}' --first-consumer-id {} --first-server-id {} --first-jms-id '{}' --consumer-id {} --server-id {} --jms-id '{}'",
            dupId, record.firstConsumerId, record.firstServerId, record.firstJMSMessageId,
            record.dupConsumerId, record.dupServerId, record.dupJMSMessageId);
      }

      if (!otherDupIds.isEmpty() && HALT_ON_OTHER_DUPLICATES) {
         Runtime.getRuntime().halt(-1);
      }
   }

   private long countLostMessages() {
      Set<String> lostMessages = new HashSet<>(sentMessageIds);
      lostMessages.removeAll(messageRecords.keySet());
      if (!lostMessages.isEmpty()) {
         logger.info("Test lost messages: {}", lostMessages);
      }
      return lostMessages.size();
   }

   private long countDuplicates() {
      return messageRecords.values().stream().filter(MessageRecord::isDuplicate).count();
   }

   private long countGhostSends() {
      Set<String> ghostSends = new HashSet<>(messageRecords.keySet());
      ghostSends.removeAll(sentMessageIds);
      if (!ghostSends.isEmpty()) {
         logger.info("Test ghost sends: {}", ghostSends);
      }
      return ghostSends.size();
   }

   private long countQueueMessages() {
      long total = 0;
      for (int i = 0; i < NUMBER_OF_SERVERS; i++) {
         try {
            long count = serverRecords[i].getManagement().getMessageCountOnQueue(QUEUE_NAME);
            if (count > 0) {
               logger.info("Test server {} queueMessageCount: {}", i, count);
            }
            total += count;
         } catch (Exception e) {
            logger.debug("Could not get message count from server {}: {}", i, e.getMessage());
         }
      }
      return total;
   }

   private long countSnFMessages() {
      long total = 0;
      for (int i = 0; i < NUMBER_OF_SERVERS; i++) {
         try {
            for (Map.Entry<String, Long> entry : serverRecords[i].getManagement().getQueueCounts(1000).entrySet()) {
               if (entry.getKey().startsWith("sf.") && entry.getValue() > 0) {
                  logger.info("Test server {} SnF queue {} count: {}", i, entry.getKey(), entry.getValue());
                  total += entry.getValue();
               }
            }
         } catch (Exception e) {
            logger.debug("Could not get SnF counts from server {}: {}", i, e.getMessage());
         }
      }
      return total;
   }

   /**
    * Scan journals for {@code UPDATE_DELIVERY_COUNT} on each duplicate's {@code firstServerId} only.
    * Evidence on any other broker does not count as broker-consumer for that id.
    *
    * @return the set of duplicate ids with broker-consumer evidence
    */
   private Set<String> scanJournalsForConsumerRedelivery(Set<String> dupIds) {
      Set<String> unackedDeliveryDupIds = new HashSet<>();
      if (dupIds == null || dupIds.isEmpty()) {
         return unackedDeliveryDupIds;
      }

      Map<Integer, Set<String>> dupIdsByFirstServer = new HashMap<>();
      for (String dupId : dupIds) {
         MessageRecord record = messageRecords.get(dupId);
         if (record == null) {
            logger.warn("Duplicate id={} has no MessageRecord; treating as other", dupId);
            continue;
         }
         dupIdsByFirstServer.computeIfAbsent(record.firstServerId, s -> new HashSet<>()).add(dupId);
      }

      for (Map.Entry<Integer, Set<String>> entry : dupIdsByFirstServer.entrySet()) {
         int serverId = entry.getKey();
         if (serverId < 0 || serverId >= serverRecords.length) {
            continue;
         }
         File journalDir = new File(serverRecords[serverId].serverLocation, "data/journal");
         if (!journalDir.isDirectory()) {
            continue;
         }
         try {
            scanBrokerJournal(journalDir, entry.getValue(), unackedDeliveryDupIds);
         } catch (Exception e) {
            logger.warn("Failed to scan journal on broker-{} at {}: {}", serverId, journalDir, e.getMessage());
            logger.debug("Journal scan failure", e);
         }
      }
      return unackedDeliveryDupIds;
   }

   private static void scanBrokerJournal(File journalDir,
                                         Set<String> dupIds,
                                         Set<String> unackedDeliveryDupIds) throws Exception {
      ConfigurationImpl config = new ConfigurationImpl();
      NIOSequentialFileFactory fileFactory = new NIOSequentialFileFactory(journalDir, null, 1);
      JournalImpl journal = new JournalImpl(config.getJournalFileSize(), config.getJournalMinFiles(), config.getJournalPoolFiles(),
         0, 0, fileFactory, "activemq-data", "amq", 1);

      Map<Long, String> midToDupId = new HashMap<>();
      Set<Long> midsWithDeliveryCount = new HashSet<>();

      JournalReaderCallback reader = new JournalReaderCallback() {
         private void onAdd(RecordInfo info) {
            if (info.getUserRecordType() != JournalRecordIds.ADD_MESSAGE_PROTOCOL
               && info.getUserRecordType() != JournalRecordIds.ADD_MESSAGE) {
               return;
            }
            String dupId = extractDupId(info);
            if (dupId == null || !dupIds.contains(dupId)) {
               return;
            }
            midToDupId.put(info.id, dupId);
         }

         private void onUpdate(RecordInfo info) {
            if (info.getUserRecordType() == JournalRecordIds.UPDATE_DELIVERY_COUNT) {
               midsWithDeliveryCount.add(info.id);
            }
         }

         @Override
         public void onReadAddRecord(RecordInfo info) {
            onAdd(info);
         }

         @Override
         public void onReadAddRecordTX(long transactionID, RecordInfo info) {
            onAdd(info);
         }

         @Override
         public void onReadUpdateRecord(RecordInfo info) {
            onUpdate(info);
         }

         @Override
         public void onReadUpdateRecordTX(long transactionID, RecordInfo info) {
            onUpdate(info);
         }
      };

      for (JournalFile file : journal.orderFiles()) {
         // reclaimed=true: include history after ACK/Delete (same as data print --reclaimed)
         JournalImpl.readJournalFile(fileFactory, file, reader, null, true, null);
      }

      for (Long mid : midsWithDeliveryCount) {
         String dupId = midToDupId.get(mid);
         if (dupId == null) {
            continue;
         }
         unackedDeliveryDupIds.add(dupId);
      }
   }

   private static String extractDupId(RecordInfo info) {
      Object encoded = DescribeJournal.newObjectEncoding(info);
      if (encoded instanceof DescribeJournal.MessageDescribe messageDescribe) {
         org.apache.activemq.artemis.api.core.Message msg = messageDescribe.getMsg();
         if (msg == null) {
            return null;
         }
         Object value = msg.getObjectProperty(TEST_MSG_ID);
         return value == null ? null : value.toString();
      }
      return null;
   }

   private void waitForCluster(int[] startedNodes, int[] stoppedNodes) throws Exception {
      for (int nodeId : startedNodes) {
         ServerUtil.waitForServerToStart(nodeId, SERVER_START_TIMEOUT_MS);
      }
      for (int nodeId : startedNodes) {
         final int serverIndex = nodeId;

         Wait.assertTrue(() -> {
            try {
               JsonArray topology = serverRecords[serverIndex].getManagement().listNetworkTopology();
               for (int s : startedNodes) {
                  String expectedConnector = "localhost:" + (SERVER_PORT_BASE + s);
                  boolean found = false;
                  for (int j = 0; j < topology.size(); j++) {
                     if (topology.getJsonObject(j).getString("primary", "").contains(expectedConnector)) {
                        found = true;
                        break;
                     }
                  }
                  if (!found) {
                     logger.info("Server {} listNetworkTopology missing started connector {}", serverIndex, expectedConnector);
                     return false;
                  }
               }
               for (int s : stoppedNodes) {
                  String stoppedConnector = "localhost:" + (SERVER_PORT_BASE + s);
                  for (int j = 0; j < topology.size(); j++) {
                     if (topology.getJsonObject(j).getString("primary", "").contains(stoppedConnector)) {
                        logger.info("Server {} listNetworkTopology still contains stopped server {}", serverIndex, s);
                        return false;
                     }
                  }
               }
               return true;
            } catch (Exception e) {
               logger.info("Could not get listNetworkTopology from server {}: {}", serverIndex, e.getMessage());
               return false;
            }
         }, CLUSTER_TIMEOUT_MS, 1000);

         Wait.assertTrue(() -> {
            try {
               Map<String, String> nodes = serverRecords[serverIndex].getManagement()
                  .simpleManagementMap(ResourceNames.CORE_CLUSTER_CONNECTION + CLUSTER_CONNECTION_NAME, "getNodes");
               for (int s : startedNodes) {
                  if (s == serverIndex) {
                     continue;
                  }
                  String expectedPort = ":" + (SERVER_PORT_BASE + s);
                  boolean found = false;
                  for (String connector : nodes.values()) {
                     if (connector.contains(expectedPort)) {
                        found = true;
                        break;
                     }
                  }
                  if (!found) {
                     logger.info("Server {} getNodes missing started connector for port {}", serverIndex, expectedPort);
                     return false;
                  }
               }
               for (int s : stoppedNodes) {
                  String stoppedPort = ":" + (SERVER_PORT_BASE + s);
                  for (String connector : nodes.values()) {
                     if (connector.contains(stoppedPort)) {
                        logger.info("Server {} getNodes still contains stopped server {}", serverIndex, s);
                        return false;
                     }
                  }
               }
               return true;
            } catch (Exception e) {
               logger.info("Could not get getNodes from server {}: {}", serverIndex, e.getMessage());
               return false;
            }
         }, CLUSTER_TIMEOUT_MS, 1000);
      }
   }
}
