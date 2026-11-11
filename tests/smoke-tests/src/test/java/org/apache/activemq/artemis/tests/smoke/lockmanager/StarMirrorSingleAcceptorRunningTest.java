/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.activemq.artemis.tests.smoke.lockmanager;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.function.Consumer;

import org.apache.activemq.artemis.api.core.management.SimpleManagement;
import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.apache.activemq.artemis.tests.smoke.common.SmokeTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.FileUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

public class StarMirrorSingleAcceptorRunningTest extends SmokeTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final String SERVER_NAME_WITH_ZK_A = "lockmanager/starMirrorSingleAcceptor/ZK/A";
   public static final String SERVER_NAME_WITH_ZK_B = "lockmanager/starMirrorSingleAcceptor/ZK/B";
   public static final String SERVER_NAME_WITH_ZK_C = "lockmanager/starMirrorSingleAcceptor/ZK/C";

   public static final String SERVER_NAME_WITH_FILE_A = "lockmanager/starMirrorSingleAcceptor/file/A";
   public static final String SERVER_NAME_WITH_FILE_B = "lockmanager/starMirrorSingleAcceptor/file/B";
   public static final String SERVER_NAME_WITH_FILE_C = "lockmanager/starMirrorSingleAcceptor/file/C";


   public static final String SERVER_NAME_BRIDGE_TARGET = "lockmanager/starMirrorSingleAcceptor/bridgeTarget";

   // Test constants
   private static final int ALTERNATING_TEST_ITERATIONS = 3;
   private static final int MESSAGES_SENT_PER_ITERATION = 100;
   private static final int BRIDGE_MESSAGES_SENT_PER_ITERATION = 7;
   private static final int MESSAGES_CONSUMED_PER_ITERATION = 17;
   private static final int MESSAGES_REMAINING_PER_ITERATION = MESSAGES_SENT_PER_ITERATION - MESSAGES_CONSUMED_PER_ITERATION;
   private static final int EXPECTED_FINAL_MESSAGE_COUNT = ALTERNATING_TEST_ITERATIONS * MESSAGES_REMAINING_PER_ITERATION;


   private static final int ZK_BASE_PORT = 2181;

   Process processA;
   Process processB;
   Process processC;
   Process bridgeTargetProcess;

   private static void customizeFileServer(File serverLocation, File fileLock) {
      try {
         FileUtil.findReplace(new File(serverLocation, "/etc/broker.xml"), "CHANGEME", fileLock.getAbsolutePath());
      } catch (Throwable e) {
         throw new RuntimeException(e.getMessage(), e);
      }
   }

   private static void createServers(String serverNameA, String serverNameB, String serverNameC,
                                        String configPathA, String configPathB, String configPathC,
                                        Consumer<File> customizeServer) throws Exception {
      File serverLocationA = getFileServerLocation(serverNameA);
      File serverLocationB = getFileServerLocation(serverNameB);
      File serverLocationC = getFileServerLocation(serverNameC);
      deleteDirectory(serverLocationA);
      deleteDirectory(serverLocationB);
      deleteDirectory(serverLocationC);

      createSingleServer(serverLocationA, configPathA, customizeServer);
      createSingleServer(serverLocationB, configPathB, customizeServer);
      createSingleServer(serverLocationC, configPathC, customizeServer);

      File bridgeTarget = getFileServerLocation(SERVER_NAME_BRIDGE_TARGET);
      createBridgeTarget(bridgeTarget);
   }

   private static void createSingleServer(File serverLocation, String configPath,
                                           Consumer<File> customizeServer) throws Exception {
      HelperCreate cliCreateServer = helperCreate();
      cliCreateServer.setAllowAnonymous(true)
                     .setUser("admin")
                     .setPassword("adming")
                     .setNoWeb(true)
                     .setConfiguration(configPath)
                     .setArtemisInstance(serverLocation);
      cliCreateServer.createServer();

      if (customizeServer != null) {
         customizeServer.accept(serverLocation);
      }
   }


   private static void createBridgeTarget(File serverLocation) throws Exception {
      HelperCreate cliCreateServer = helperCreate();
      cliCreateServer.setAllowAnonymous(true)
         .setUser("admin")
         .setPassword("admin")
         .setNoWeb(true)
         .setPortOffset(10)
         .setArtemisInstance(serverLocation);
      cliCreateServer.addArgs("--queues", "bridgeQueue");
      cliCreateServer.createServer();
   }



   @BeforeEach
   public void prepareServers() throws Exception {

   }

   @Test
   public void testAlternatingZK() throws Throwable {
      {
         createServers(SERVER_NAME_WITH_ZK_A, SERVER_NAME_WITH_ZK_B, SERVER_NAME_WITH_ZK_C,
                          "./src/main/resources/servers/lockmanager/starMirrorSingleAcceptor/ZK/A",
                          "./src/main/resources/servers/lockmanager/starMirrorSingleAcceptor/ZK/B",
                          "./src/main/resources/servers/lockmanager/starMirrorSingleAcceptor/ZK/C",
                          null);

         cleanupData(SERVER_NAME_WITH_ZK_A);
         cleanupData(SERVER_NAME_WITH_ZK_B);
         cleanupData(SERVER_NAME_WITH_ZK_C);
         cleanupData(SERVER_NAME_BRIDGE_TARGET);
      }

      // starting zookeeper
      ZookeeperCluster zkCluster = new ZookeeperCluster(temporaryFolder, 1, ZK_BASE_PORT, 100);
      zkCluster.start();
      runAfter(zkCluster::stop);

      testAlternating(SERVER_NAME_WITH_ZK_A, SERVER_NAME_WITH_ZK_B, SERVER_NAME_WITH_ZK_C);
   }

   @Test
   public void testAlternatingFile() throws Throwable {
      File fileLock = new File("./target/serverLock");
      fileLock.mkdirs();

      {
         createServers(SERVER_NAME_WITH_FILE_A, SERVER_NAME_WITH_FILE_B, SERVER_NAME_WITH_FILE_C,
                          "./src/main/resources/servers/lockmanager/starMirrorSingleAcceptor/file/A",
                          "./src/main/resources/servers/lockmanager/starMirrorSingleAcceptor/file/B",
                          "./src/main/resources/servers/lockmanager/starMirrorSingleAcceptor/file/C",
                          s -> customizeFileServer(s, fileLock));

         cleanupData(SERVER_NAME_WITH_FILE_A);
         cleanupData(SERVER_NAME_WITH_FILE_B);
         cleanupData(SERVER_NAME_WITH_FILE_C);
         cleanupData(SERVER_NAME_BRIDGE_TARGET);
      }

      testAlternating(SERVER_NAME_WITH_FILE_A, SERVER_NAME_WITH_FILE_B, SERVER_NAME_WITH_FILE_C);
   }

   public void testAlternating(String nameServerA, String nameServerB, String nameServerC) throws Throwable {
      processA = startServer(nameServerA, 0, 60_000);
      processB = startServer(nameServerB, 0, -1);
      processC = startServer(nameServerC, 0, -1);
      bridgeTargetProcess = startServer(SERVER_NAME_BRIDGE_TARGET, 0, -1);

      ConnectionFactory cfX = CFUtil.createConnectionFactory("amqp", "tcp://localhost:61616");

      final String queueName = "myQueue";
      final String bridgeQueue = "bridgeQueue";

      validateStar(cfX);

      for (int i = 0; i < ALTERNATING_TEST_ITERATIONS; i++) {
         String activeServer = (i % 3 == 0) ? "A" : (i % 3 == 1) ? "B" : "C";
         logger.info("Iteration {}: Server {} active", i, activeServer);

         if (i % 3 == 0) {
            // Iteration 0, 3, 6, ...: Server A active, kill Server B and C
            killServer(processB);
            killServer(processC);
         } else if (i % 3 == 1) {
            // Iteration 1, 4, 7, ...: Server B active, kill Server A and C
            killServer(processA);
            killServer(processC);
         } else {
            // Iteration 2, 5, 8, ...: Server C active, kill Server A and B
            killServer(processA);
            killServer(processB);
         }

         // Send messages through the shared acceptor
         sendMessages(cfX, queueName, MESSAGES_SENT_PER_ITERATION);

         // Consume some messages
         receiveMessages(cfX, queueName, MESSAGES_CONSUMED_PER_ITERATION);

         // Restart the killed servers
         if (i % 3 == 0) {
            processB = startServer(nameServerB, 0, -1);
            processC = startServer(nameServerC, 0, -1);
         } else if (i % 3 == 1) {
            processA = startServer(nameServerA, 0, -1);
            processC = startServer(nameServerC, 0, -1);
         } else {
            processA = startServer(nameServerA, 0, -1);
            processB = startServer(nameServerB, 0, -1);
         }

         assertEmptySNFs();

         assertMessageCount("tcp://localhost:61000", queueName, MESSAGES_REMAINING_PER_ITERATION * (i + 1));
         assertMessageCount("tcp://localhost:61001", queueName, MESSAGES_REMAINING_PER_ITERATION * (i + 1));
         assertMessageCount("tcp://localhost:61002", queueName, MESSAGES_REMAINING_PER_ITERATION * (i + 1));

         sendMessages(cfX, bridgeQueue, BRIDGE_MESSAGES_SENT_PER_ITERATION);
         assertMessageCount("tcp://localhost:61626", bridgeQueue, BRIDGE_MESSAGES_SENT_PER_ITERATION * (i + 1));
         assertMessageCount("tcp://localhost:61000", bridgeQueue, 0);
         assertMessageCount("tcp://localhost:61001", bridgeQueue, 0);
         assertMessageCount("tcp://localhost:61002", bridgeQueue, 0);
      }

      // Verify they all have the expected message count (iterations × (sent - consumed))
      assertMessageCount("tcp://localhost:61000", queueName, EXPECTED_FINAL_MESSAGE_COUNT);
      assertMessageCount("tcp://localhost:61001", queueName, EXPECTED_FINAL_MESSAGE_COUNT);
      assertMessageCount("tcp://localhost:61002", queueName, EXPECTED_FINAL_MESSAGE_COUNT);

      assertMessageCount("tcp://localhost:61000", bridgeQueue, 0);
      assertMessageCount("tcp://localhost:61001", bridgeQueue, 0);
      assertMessageCount("tcp://localhost:61002", bridgeQueue, 0);

      assertMessageCount("tcp://localhost:61626", bridgeQueue, BRIDGE_MESSAGES_SENT_PER_ITERATION * ALTERNATING_TEST_ITERATIONS);

      receiveMessages(cfX, queueName, EXPECTED_FINAL_MESSAGE_COUNT);
      assertMessageCount("tcp://localhost:61000", queueName, 0);
      assertMessageCount("tcp://localhost:61001", queueName, 0);
      assertMessageCount("tcp://localhost:61002", queueName, 0);
      assertEmptySNFs();

      validateStar(cfX);
   }

   private void assertEmptySNFs() throws Exception {
      assertMessageCount("tcp://localhost:61000", "$ACTIVEMQ_ARTEMIS_MIRROR_mirrorB", 0);
      assertMessageCount("tcp://localhost:61000", "$ACTIVEMQ_ARTEMIS_MIRROR_mirrorC", 0);

      assertMessageCount("tcp://localhost:61001", "$ACTIVEMQ_ARTEMIS_MIRROR_mirrorA", 0);
      assertMessageCount("tcp://localhost:61001", "$ACTIVEMQ_ARTEMIS_MIRROR_mirrorC", 0);

      assertMessageCount("tcp://localhost:61002", "$ACTIVEMQ_ARTEMIS_MIRROR_mirrorA", 0);
      assertMessageCount("tcp://localhost:61002", "$ACTIVEMQ_ARTEMIS_MIRROR_mirrorB", 0);
   }

   private void validateStar(ConnectionFactory cfX) throws Exception {
      // validate the star combination
      sendMessages(cfX, "myQueue", 1_000);
      assertMessageCount("tcp://localhost:61000", "myQueue", 1_000);
      assertMessageCount("tcp://localhost:61001", "myQueue", 1_000);
      assertMessageCount("tcp://localhost:61002", "myQueue", 1_000);

      // validate the star combination
      receiveMessages(cfX, "myQueue", 1_000);
      assertMessageCount("tcp://localhost:61000", "myQueue", 0);
      assertMessageCount("tcp://localhost:61001", "myQueue", 0);
      assertMessageCount("tcp://localhost:61002", "myQueue", 0);
   }

   private static void sendMessages(ConnectionFactory cfX, String queueName, int nmessages) throws JMSException {
      try (Connection connectionX = retryUntilIsLive(cfX)) {
         Session sessionX = connectionX.createSession(true, Session.SESSION_TRANSACTED);
         Queue queue = sessionX.createQueue(queueName);
         MessageProducer producerX = sessionX.createProducer(queue);
         for (int i = 0; i < nmessages; i++) {
            producerX.send(sessionX.createTextMessage("hello " + i));
         }
         sessionX.commit();
      }
   }

   private static Connection retryUntilIsLive(ConnectionFactory cfX) {
      final int maxRetry = 1000;
      for (int i = 0; i < maxRetry; i++) {
         try {
            return cfX.createConnection();
         } catch (Exception ex) {
            logger.info("Exception during connection, retrying the connection... {} out of {} retries, message = {}", i, maxRetry, ex.getMessage());
            try {
               Thread.sleep(500);
            } catch (Throwable e) {
            }
         }
      }
      fail("Could not connect after " + maxRetry + " retries");
      return null; // never happening, fail will throw an exception
   }

   private static void receiveMessages(ConnectionFactory cfX, String queueName, int nmessages) throws JMSException {
      try (Connection connectionX = retryUntilIsLive(cfX)) {
         connectionX.start();
         Session sessionX = connectionX.createSession(true, Session.SESSION_TRANSACTED);
         Queue queue = sessionX.createQueue(queueName);
         MessageConsumer consumerX = sessionX.createConsumer(queue);
         for (int i = 0; i < nmessages; i++) {
            TextMessage message = (TextMessage) consumerX.receive(5000);
            assertNotNull(message, "Expected message " + i + " but got null");
         }
         sessionX.commit();
      }
   }

   protected void assertMessageCount(String uri, String queueName, long count) throws Exception {
      SimpleManagement simpleManagement = new SimpleManagement(uri, null, null);
      Wait.assertEquals(count, () -> {
         try {
            long result = simpleManagement.getMessageCountOnQueue(queueName);
            if (count != result) {
               logger.info("validating {} on queue {} expecting count = {}, result = {}", uri, queueName, count, result);
            }
            return result;
         } catch (Throwable e) {
            return -1;
         }
      });
   }

}
