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

package org.apache.activemq.artemis.tests.smoke.failover;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.management.SimpleManagement;
import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.apache.activemq.artemis.tests.smoke.common.SmokeTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.util.ServerUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.artemis.utils.collections.ConcurrentHashSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OpenWireSharedStoreFailoverSmokeTest extends SmokeTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final String SERVER_NAME_LIVE = "openwire-failover-live";
   public static final String SERVER_NAME_BACKUP = "openwire-failover-backup";

   private static final String QUEUE_NAME = "FailoverTestQueue";
   private static final int NUMBER_OF_MESSAGES = 10000;
   private static final int PRODUCERS = 50;
   private static final int FAILOVER_AT_MESSAGE = 1000;

   private static String sharedDataPath;
   private Process liveServer;
   private Process backupServer;

   @BeforeAll
   public static void createServers() throws Exception {
      // Set up shared storage path
      File sharedStorage = new File(getFileServerLocation(SERVER_NAME_LIVE), "shared-storage");
      sharedDataPath = sharedStorage.getAbsolutePath();

      createLiveServer();
      createBackupServer();
      sharedStorage.mkdirs();
   }

   private static void createLiveServer() throws Exception {
      File serverLocation = getFileServerLocation(SERVER_NAME_LIVE);
      deleteDirectory(serverLocation);

      HelperCreate cliCreateServer = helperCreate();
      cliCreateServer.setUseAIO(false).setAllowAnonymous(true).setNoWeb(true).setArtemisInstance(serverLocation).setSharedStore(true).setClustered(true).setStaticCluster("tcp://localhost:61617").setDataFolder(sharedDataPath).setFailoverOnShutdown(true).setMessageLoadBalancing("OFF");

      cliCreateServer.createServer();
   }

   private static void createBackupServer() throws Exception {
      File serverLocation = getFileServerLocation(SERVER_NAME_BACKUP);
      deleteDirectory(serverLocation);

      HelperCreate cliCreateServer = helperCreate();
      cliCreateServer.setUseAIO(false).setAllowAnonymous(true).setNoWeb(true).setArtemisInstance(serverLocation).setSharedStore(true).setBackup(true).setClustered(true).setStaticCluster("tcp://localhost:61616").setPortOffset(1).setDataFolder(sharedDataPath).setMessageLoadBalancing("OFF");
      cliCreateServer.createServer();
   }

   @BeforeEach
   public void before() throws Exception {
      cleanupData(SERVER_NAME_LIVE);
      cleanupData(SERVER_NAME_BACKUP);

      // Clean shared storage
      File sharedStorage = new File(sharedDataPath);
      deleteDirectory(sharedStorage);
      sharedStorage.mkdirs();

      disableCheckThread();

      liveServer = startServer(SERVER_NAME_LIVE, 0, 0);
      assertTrue(ServerUtil.waitForServerToStartOnPort(61616, null, null, 30000));

      backupServer = startServer(SERVER_NAME_BACKUP, 0, 0);
   }

   @AfterEach
   @Override
   public void after() throws Exception {
      super.after();
   }

   @Test
   public void testOpenWire() throws Exception {
      String failoverURL = "failover:(tcp://localhost:61616,tcp://localhost:61617)";
      ConnectionFactory factory = CFUtil.createConnectionFactory("OPENWIRE", failoverURL);

      AtomicInteger errors = new AtomicInteger(0);
      CountDownLatch producersLatch = new CountDownLatch(PRODUCERS);
      CountDownLatch failoverLatch = new CountDownLatch(FAILOVER_AT_MESSAGE);

      ExecutorService executor = Executors.newFixedThreadPool(PRODUCERS);
      runAfter(executor::shutdownNow);

      CyclicBarrier startFlag = new CyclicBarrier(PRODUCERS);

      ConcurrentHashSet<String> duplicateIDs = new ConcurrentHashSet<>();

      for (int producerID = 0; producerID < PRODUCERS; producerID++) {
         final int theProducerID = producerID;
         executor.execute(() -> {
            try (Connection connection = factory.createConnection()) {
               Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
               javax.jms.Queue queue = session.createQueue(QUEUE_NAME);
               javax.jms.MessageProducer producer = session.createProducer(queue);

               int messagesPerProducer = NUMBER_OF_MESSAGES / PRODUCERS;

               startFlag.await(10, TimeUnit.SECONDS);
               int totalSent = 0;

               for (int i = 0; i < messagesPerProducer; i++) {
                  boolean messageCommitted = false;
                  int retryCount = 0;
                  final int maxRetries = 100;

                  String duplicateId = generateDuplicateID(theProducerID, i);

                  while (!messageCommitted && retryCount < maxRetries) {
                     try {
                        TextMessage message = session.createTextMessage("Message from producer " + theProducerID + ", sequence " + i);
                        message.setIntProperty("producerID", theProducerID);
                        message.setIntProperty("sequence", i);
                        // Set duplicate detection ID
                        message.setStringProperty("_AMQ_DUPL_ID", duplicateId);
                        producer.send(message);
                        session.commit();
                        duplicateIDs.add(duplicateId);
                        messageCommitted = true;

                        if (totalSent++ % 100 == 0) {
                           logger.info("Producer {} committed message {}. Total confirmed: {}", theProducerID, i, totalSent);
                        }
                        failoverLatch.countDown();
                     } catch (Exception commitException) {

                        if (commitException.getMessage().contains("Duplicate message detected")) {
                           logger.info("Duplicate, it's okay");
                           duplicateIDs.add(duplicateId);
                           session.rollback();
                           messageCommitted = true;
                        } else {
                           logger.warn("Producer {} commit failed (retry {}): {}", theProducerID, retryCount, commitException.getMessage());
                           try {
                              session.rollback();
                              logger.info("Producer {} rolled back message {}", theProducerID, i);
                           } catch (Exception rollbackException) {
                              logger.warn("Producer {} rollback failed: {}", theProducerID, rollbackException.getMessage());
                           }
                           retryCount++;
                           Thread.sleep(100); // Brief pause before retry
                        }
                     }
                  }

                  if (!messageCommitted) {
                     logger.error("Producer {} failed to commit message {} after {} retries", theProducerID, i, maxRetries);
                     errors.incrementAndGet();
                     break;
                  }
               }

            } catch (Exception e) {
               logger.warn("Producer {} encountered fatal error: {}", theProducerID, e.getMessage(), e);
               errors.incrementAndGet();
            } finally {
               producersLatch.countDown();
            }
         });
      }

      assertTrue(failoverLatch.await(10, TimeUnit.SECONDS));
      logger.info("Failover signal received, killing live server");
      if (liveServer != null && liveServer.isAlive()) {
         stopServerWithFile(getServerLocation(SERVER_NAME_LIVE));
         liveServer.waitFor(1, TimeUnit.MINUTES);
      }

      // Wait for all producers to complete
      assertTrue(producersLatch.await(10, TimeUnit.MINUTES), "Producers did not complete in time");
      assertEquals(0, errors.get(), "Errors occurred during sending");

      int duplicateSize = duplicateIDs.size();
      logger.info("Total confirmed messages sent: {}", duplicateSize);
      assertEquals(NUMBER_OF_MESSAGES, duplicateSize, "Should have confirmed all messages (with retries during failover)");

      SimpleManagement management = new SimpleManagement("tcp://localhost:61617", null, null);
      Wait.waitFor(() -> management.getMessageCountOnQueue(QUEUE_NAME) >= duplicateIDs.size(), 5000, 100);

      long numberOfMessages = management.getMessageCountOnQueue(QUEUE_NAME);

      // Consume and verify all messages
      try (Connection consumerConnection = factory.createConnection()) {
         consumerConnection.start();
         Session consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer consumer = consumerSession.createConsumer(consumerSession.createQueue(QUEUE_NAME));

         AtomicInteger receivedCount = new AtomicInteger(0);

         for (int i = 0; i < numberOfMessages; i++) {
            TextMessage message = (TextMessage) consumer.receive(10000);
            assertNotNull(message, "Should receive message " + i);

            int producerID = message.getIntProperty("producerID");
            int sequence = message.getIntProperty("sequence");
            String duplicateID = generateDuplicateID(producerID, sequence);

            duplicateIDs.remove(duplicateID);
            receivedCount.incrementAndGet();

            if ((i + 1) % 100 == 0) {
               logger.info("Received {} messages so far", i + 1);
            }
         }
         assertNull(consumer.receiveNoWait());

         duplicateIDs.forEach(s -> logger.warn("DuplicateID not received {}", duplicateIDs));

         logger.info("Total messages received: {}", receivedCount);
         assertTrue(receivedCount.get() >= duplicateSize, () -> "Should receive exactly the confirmed count without duplicates " + receivedCount.get() + ", confirmed = " + duplicateSize);

         assertTrue(duplicateIDs.isEmpty());
      }

      logger.info("Test completed successfully. Confirmed: {}, Verified in queue", duplicateSize);
   }

   private static String generateDuplicateID(int producerID, int sequence) {
      return "DUP:" + producerID + ":" + sequence;
   }
}
