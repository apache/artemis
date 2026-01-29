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
import java.io.FileOutputStream;
import java.lang.invoke.MethodHandles;
import java.util.Properties;
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

public class DualMirrorSingleAcceptorRunningTest extends SmokeTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final String SERVER_NAME_WITH_ZK_A = "lockmanager/dualMirrorSingleAcceptor/ZK/A";
   public static final String SERVER_NAME_WITH_ZK_B = "lockmanager/dualMirrorSingleAcceptor/ZK/B";

   public static final String SERVER_NAME_WITH_FILE_A = "lockmanager/dualMirrorSingleAcceptor/file/A";
   public static final String SERVER_NAME_WITH_FILE_B = "lockmanager/dualMirrorSingleAcceptor/file/B";

   // Test constants
   private static final int ALTERNATING_TEST_ITERATIONS = 2;
   private static final int MESSAGES_SENT_PER_ITERATION = 100;
   private static final int MESSAGES_CONSUMED_PER_ITERATION = 17;
   private static final int MESSAGES_REMAINING_PER_ITERATION = MESSAGES_SENT_PER_ITERATION - MESSAGES_CONSUMED_PER_ITERATION;
   private static final int EXPECTED_FINAL_MESSAGE_COUNT = ALTERNATING_TEST_ITERATIONS * MESSAGES_REMAINING_PER_ITERATION;

   private static final int ZK_BASE_PORT = 2181;

   Process processA;
   Process processB;

   private static void customizeFileServer(File serverLocation, File fileLock) {
      try {
         FileUtil.findReplace(new File(serverLocation, "/etc/broker.xml"), "CHANGEME", fileLock.getAbsolutePath());
      } catch (Throwable e) {
         throw new RuntimeException(e.getMessage(), e);
      }
   }

   private static void createServerPair(String serverNameA, String serverNameB,
                                         String configPathA, String configPathB,
                                         Consumer<File> customizeServer) throws Exception {
      File serverLocationA = getFileServerLocation(serverNameA);
      File serverLocationB = getFileServerLocation(serverNameB);
      deleteDirectory(serverLocationB);
      deleteDirectory(serverLocationA);

      createSingleServer(serverLocationA, configPathA, "A", customizeServer);
      createSingleServer(serverLocationB, configPathB, "B", customizeServer);
   }

   private static void createSingleServer(File serverLocation, String configPath,
                                           String userAndPassword, Consumer<File> customizeServer) throws Exception {
      HelperCreate cliCreateServer = helperCreate();
      cliCreateServer.setAllowAnonymous(true)
                     .setUser(userAndPassword)
                     .setPassword(userAndPassword)
                     .setNoWeb(true)
                     .setConfiguration(configPath)
                     .setArtemisInstance(serverLocation);
      cliCreateServer.createServer();

      if (customizeServer != null) {
         customizeServer.accept(serverLocation);
      }
   }

   @BeforeEach
   public void prepareServers() throws Exception {

   }

   @Test
   public void testAlternatingZK() throws Throwable {
      {
         createServerPair(SERVER_NAME_WITH_ZK_A, SERVER_NAME_WITH_ZK_B,
                          "./src/main/resources/servers/lockmanager/dualMirrorSingleAcceptor/ZK/A",
                          "./src/main/resources/servers/lockmanager/dualMirrorSingleAcceptor/ZK/B",
                          null);

         cleanupData(SERVER_NAME_WITH_ZK_A);
         cleanupData(SERVER_NAME_WITH_ZK_B);
      }

      // starting zookeeper
      ZookeeperCluster zkCluster = new ZookeeperCluster(temporaryFolder, 1, ZK_BASE_PORT, 100);
      zkCluster.start();
      runAfter(zkCluster::stop);

      testAlternating(SERVER_NAME_WITH_ZK_A, SERVER_NAME_WITH_ZK_B, null, null);
   }

   @Test
   public void testAlternatingFile() throws Throwable {
      File fileLock = new File("./target/serverLock");
      fileLock.mkdirs();

      {
         createServerPair(SERVER_NAME_WITH_FILE_A, SERVER_NAME_WITH_FILE_B,
                          "./src/main/resources/servers/lockmanager/dualMirrorSingleAcceptor/file/A",
                          "./src/main/resources/servers/lockmanager/dualMirrorSingleAcceptor/file/B",
                          s -> customizeFileServer(s, fileLock));

         cleanupData(SERVER_NAME_WITH_FILE_A);
         cleanupData(SERVER_NAME_WITH_FILE_B);
      }

      Properties properties = new Properties();

      properties.put("acceptorConfigurations.artemis.extraParams.amqpCredits", "1000");
      properties.put("acceptorConfigurations.artemis.extraParams.amqpLowCredits", "300");
      properties.put("acceptorConfigurations.artemis.factoryClassName", "org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptorFactory");
      properties.put("acceptorConfigurations.artemis.lockCoordinator", "failover");
      properties.put("acceptorConfigurations.artemis.name", "artemis");
      properties.put("acceptorConfigurations.artemis.params.scheme", "tcp");
      properties.put("acceptorConfigurations.artemis.params.tcpReceiveBufferSize", "1048576");
      properties.put("acceptorConfigurations.artemis.params.port", "61616");
      properties.put("acceptorConfigurations.artemis.params.host", "localhost");
      properties.put("acceptorConfigurations.artemis.params.protocols", "CORE,AMQP,STOMP,HORNETQ,MQTT,OPENWIRE");
      properties.put("acceptorConfigurations.artemis.params.useEpoll", "true");
      properties.put("acceptorConfigurations.artemis.params.tcpSendBufferSize", "1048576");

      properties.put("lockCoordinatorConfigurations.failover.checkPeriod", "5000");
      properties.put("lockCoordinatorConfigurations.failover.className", "org.apache.activemq.artemis.lockmanager.file.FileBasedLockManager");
      properties.put("lockCoordinatorConfigurations.failover.lockId", "fail");
      properties.put("lockCoordinatorConfigurations.failover.name", "failover");
      properties.put("lockCoordinatorConfigurations.failover.properties.locks-folder", fileLock.getAbsolutePath());

      try (FileOutputStream fileOutputStream = new FileOutputStream(new File(getServerLocation(SERVER_NAME_WITH_FILE_A), "broker.properties"))) {
         properties.store(fileOutputStream, null);
      }

      try (FileOutputStream fileOutputStream = new FileOutputStream(new File(getServerLocation(SERVER_NAME_WITH_FILE_B), "broker.properties"))) {
         properties.store(fileOutputStream, null);
      }

         // I'm using broker properties in one of the tests, to help validating it
      File propertiesA = new File(getServerLocation(SERVER_NAME_WITH_FILE_A), "broker.properties");
      File propertiesB = new File(getServerLocation(SERVER_NAME_WITH_FILE_B), "broker.properties");

      testAlternating(SERVER_NAME_WITH_FILE_A, SERVER_NAME_WITH_FILE_B, propertiesA, propertiesB);
   }

   public void testAlternating(String nameServerA, String nameServerB, File brokerPropertiesA, File brokerPropertiesB) throws Throwable {
      processA = startServer(nameServerA, 0, -1, brokerPropertiesA);
      waitForXToStart();
      processB = startServer(nameServerB, 0, -1, brokerPropertiesB);
      ConnectionFactory cfX = CFUtil.createConnectionFactory("amqp", "tcp://localhost:61616");

      for (int i = 0; i < ALTERNATING_TEST_ITERATIONS; i++) {
         logger.info("Iteration {}: Server {} active", i, (i % 2 == 0) ? "A" : "B");

         if (i % 2 == 0) {
            // Even iteration: Server A active, kill Server B
            killServer(processB);
            waitForXToStart();
         } else {
            // Odd iteration: Server B active, kill Server A
            killServer(processA);
            waitForXToStart();
         }

         // Send messages through the shared acceptor
         cfX = CFUtil.createConnectionFactory("amqp", "tcp://localhost:61616");
         sendMessages(cfX, MESSAGES_SENT_PER_ITERATION);

         // Consume some messages
         receiveMessages(cfX, MESSAGES_CONSUMED_PER_ITERATION);

         // Restart the killed server
         if (i % 2 == 0) {
            processB = startServer(nameServerB, 0, -1, brokerPropertiesB);
         } else {
            processA = startServer(nameServerA, 0, -1, brokerPropertiesA);
         }
      }

      // Verify they both have the expected message count (iterations × (sent - consumed))
      assertMessageCount("tcp://localhost:61000", "myQueue", EXPECTED_FINAL_MESSAGE_COUNT);
      assertMessageCount("tcp://localhost:61001", "myQueue", EXPECTED_FINAL_MESSAGE_COUNT);
   }

   private static void sendMessages(ConnectionFactory cfX, int nmessages) throws JMSException {
      try (Connection connectionX = cfX.createConnection("A", "A")) {
         Session sessionX = connectionX.createSession(true, Session.SESSION_TRANSACTED);
         Queue queue = sessionX.createQueue("myQueue");
         MessageProducer producerX = sessionX.createProducer(queue);
         for (int i = 0; i < nmessages; i++) {
            producerX.send(sessionX.createTextMessage("hello " + i));
         }
         sessionX.commit();
      }
   }

   private static void receiveMessages(ConnectionFactory cfX, int nmessages) throws JMSException {
      try (Connection connectionX = cfX.createConnection("A", "A")) {
         connectionX.start();
         Session sessionX = connectionX.createSession(true, Session.SESSION_TRANSACTED);
         Queue queue = sessionX.createQueue("myQueue");
         MessageConsumer consumerX = sessionX.createConsumer(queue);
         for (int i = 0; i < nmessages; i++) {
            TextMessage message = (TextMessage) consumerX.receive(5000);
            assertNotNull(message, "Expected message " + i + " but got null");
         }
         sessionX.commit();
      }
   }

   private void waitForXToStart() {
      for (int i = 0; i < 20; i++) {
         try {
            ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:61616");
            Connection connection = factory.createConnection();
            connection.close();
            return;
         } catch (Throwable e) {
            logger.debug(e.getMessage(), e);
            try {
               Thread.sleep(500);
            } catch (Throwable ignored) {
            }
         }
      }
   }

   protected void assertMessageCount(String uri, String queueName, int count) throws Exception {
      SimpleManagement simpleManagement = new SimpleManagement(uri, null, null);
      Wait.assertEquals(count, () -> {
         try {
            return simpleManagement.getMessageCountOnQueue(queueName);
         } catch (Throwable e) {
            return -1;
         }
      });
   }

}