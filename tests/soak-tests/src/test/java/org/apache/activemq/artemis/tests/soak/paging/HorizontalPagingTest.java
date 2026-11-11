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

package org.apache.activemq.artemis.tests.soak.paging;

import static org.apache.activemq.artemis.utils.TestParameters.testProperty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.io.File;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.tests.soak.SoakTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.util.ServerUtil;
import org.apache.activemq.artemis.utils.FileUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.TestParameters;
import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

/**
 * Refer to ./scripts/parameters.sh for suggested parameters.
 *
 * It is recommended to set ARTEMIS_NFS to a mounted NFS folder in order to run this test.
 */
public class HorizontalPagingTest extends SoakTestBase {

   private static final String TEST_NAME = "HORIZONTAL";

   private static final boolean TEST_ENABLED = Boolean.parseBoolean(testProperty(TEST_NAME, "TEST_ENABLED", "true"));
   private static final int SERVER_START_TIMEOUT = testProperty(TEST_NAME, "SERVER_START_TIMEOUT", 20_000);
   private static final int TIMEOUT_MINUTES = testProperty(TEST_NAME, "TIMEOUT_MINUTES", 5);
   private static final int PRINT_INTERVAL = testProperty(TEST_NAME, "PRINT_INTERVAL", 100);
   // This property is useful if you want to validate a setup on a different data folder, for example a remote directory on a NFS server or anything like that
   private static final String DATA_FOLDER = testProperty(TEST_NAME, "DATA_FOLDER", null);
   private static final int EXECUTOR_SIZE = testProperty(TEST_NAME, "EXECUTOR_SIZE", 50);

   private final int DESTINATIONS;
   private final int MESSAGES;
   private final int COMMIT_INTERVAL;
   // if 0 will use AUTO_ACK
   private final int RECEIVE_COMMIT_INTERVAL;
   private final int MESSAGE_SIZE;

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final String SERVER_NAME_0 = "horizontalPaging";

   public static void createLocalServer(boolean purgePage) throws Exception {
      File serverLocation = getFileServerLocation(SERVER_NAME_0);
      deleteDirectory(serverLocation);

      File dataFolder = selectedDataFolder();
      deleteDirectory(dataFolder);

      HelperCreate cliCreateServer = helperCreate();
      cliCreateServer.setRole("amq").setUser("admin").setPassword("admin").setAllowAnonymous(true).setNoWeb(false).setArtemisInstance(serverLocation);
      cliCreateServer.setArgs("--java-memory", "2g").setPurgePageFolders(purgePage);
      cliCreateServer.createServer();

      File brokerXml = new File(getServerLocation(SERVER_NAME_0), "/etc/broker.xml");

      assertNotNull(dataFolder);

      assertTrue(FileUtil.findReplace(brokerXml, "<max-size-messages>-1</max-size-messages>", "<max-size-messages>0</max-size-messages>"));
      assertTrue(FileUtil.findReplace(new File(getFileServerLocation(SERVER_NAME_0), "/etc/broker.xml"), "./data/", dataFolder.getAbsolutePath() + "/"));

   }

   private static File selectedDataFolder() {
      if (DATA_FOLDER != null) {
         return new File(DATA_FOLDER);
      } else {
         if (TestParameters.NFS_FOLDER != null) {
            return new File(TestParameters.NFS_FOLDER + "/horizontalPagingTest");
         } else {
            return new File(getServerLocation(SERVER_NAME_0), "data");
         }
      }
   }

   public HorizontalPagingTest() {
      DESTINATIONS = TestParameters.testProperty(TEST_NAME, "DESTINATIONS", 100);
      MESSAGES = TestParameters.testProperty(TEST_NAME, "MESSAGES", 100);
      COMMIT_INTERVAL = TestParameters.testProperty(TEST_NAME, "COMMIT_INTERVAL", 100);
      // if 0 will use AUTO_ACK
      RECEIVE_COMMIT_INTERVAL = TestParameters.testProperty(TEST_NAME, "RECEIVE_COMMIT_INTERVAL", 100);
      MESSAGE_SIZE = TestParameters.testProperty(TEST_NAME, "MESSAGE_SIZE", 10_000);

   }

   Process serverProcess;

   @BeforeEach
   public void before() throws Exception {
      assumeTrue(TEST_ENABLED);
      cleanupData(SERVER_NAME_0);
   }

   /// ///////////////////////////////////////////////////
   /// It is important to keep separate tests here
   /// as the server has to be killed within the timeframe of protocol being executed
   /// to validate proper callbacks are in place
   @Test
   public void testHorizontalAMQPAndPurge() throws Exception {

      // we will use purge in just one of the options.. no need to mix the protocols, one is enough
      testHorizontal("AMQP", true);
   }

   @Test
   public void testHorizontalCORE() throws Exception {
      testHorizontal("CORE", false);
   }

   @Test
   public void testHorizontalOPENWIRE() throws Exception {
      testHorizontal("OPENWIRE", false);
   }

   private void testHorizontal(String protocol, boolean purgePage) throws Exception {
      createLocalServer(purgePage);
      serverProcess = startServer(SERVER_NAME_0, 0, SERVER_START_TIMEOUT);
      AtomicInteger errors = new AtomicInteger(0);

      ExecutorService service = Executors.newFixedThreadPool(EXECUTOR_SIZE);
      runAfter(service::shutdownNow);

      String text = RandomUtil.randomAlphaNumericString(MESSAGE_SIZE);

      {
         CountDownLatch latchDone = new CountDownLatch(DESTINATIONS);

         ConnectionFactory factory = CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616");
         Connection connection = factory.createConnection();
         runAfter(connection::close);

         for (int i = 0; i < DESTINATIONS; i++) {
            Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
            Queue queue = session.createQueue("queue_" + i + "_" + protocol);
            service.execute(() -> {
               try {
                  logger.info("*******************************************************************************************************************************\ndestination {}", queue.getQueueName());
                  MessageProducer producer = session.createProducer(queue);
                  for (int m = 0; m < MESSAGES; m++) {
                     TextMessage message = session.createTextMessage(text);
                     message.setIntProperty("m", m);
                     producer.send(message);
                     if (m > 0 && m % COMMIT_INTERVAL == 0) {
                        logger.info("Sent {} {} messages on queue {}", m, protocol, queue.getQueueName());
                        session.commit();
                     }
                  }

                  session.commit();
                  session.close();
               } catch (Throwable e) {
                  logger.warn(e.getMessage(), e);
                  errors.incrementAndGet();
               } finally {
                  latchDone.countDown();
               }
            });
         }
         assertTrue(latchDone.await(TIMEOUT_MINUTES, TimeUnit.MINUTES));
      }

      killServer(serverProcess, true);

      serverProcess = startServer(SERVER_NAME_0, 0, SERVER_START_TIMEOUT);
      assertTrue(ServerUtil.waitForServerToStart(0, SERVER_START_TIMEOUT));
      assertEquals(0, errors.get());

      AtomicInteger completedFine = new AtomicInteger(0);

      {
         CountDownLatch latchDone = new CountDownLatch(DESTINATIONS);

         ConnectionFactory factory = CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616");
         Connection connectionConsumer = factory.createConnection();
         runAfter(connectionConsumer::close);

         for (int i = 0; i < DESTINATIONS; i++) {
            int destination = i;
            service.execute(() -> {
               try {
                  Session sessionConsumer;

                  if (RECEIVE_COMMIT_INTERVAL <= 0) {
                     sessionConsumer = connectionConsumer.createSession(false, Session.AUTO_ACKNOWLEDGE);
                  } else {
                     sessionConsumer = connectionConsumer.createSession(true, Session.SESSION_TRANSACTED);
                  }

                  String queueName = "queue_" + destination + "_" + protocol;

                  MessageConsumer messageConsumer = sessionConsumer.createConsumer(sessionConsumer.createQueue(queueName));
                  for (int m = 0; m < MESSAGES; m++) {
                     TextMessage message = (TextMessage) messageConsumer.receive(1_000);
                     if (message == null) {
                        logger.info("message is null on {}, m={}", queueName, m);
                        m--;
                        continue;
                     }

                     // The sending commit interval here will be used for printing
                     if (PRINT_INTERVAL > 0 && m % PRINT_INTERVAL == 0) {
                        logger.info("Destination {} received {} {} messages", destination, m, protocol);
                     }

                     assertEquals(m, message.getIntProperty("m"));

                     if (RECEIVE_COMMIT_INTERVAL > 0 && (m + 1) % RECEIVE_COMMIT_INTERVAL == 0) {
                        sessionConsumer.commit();
                     }
                  }

                  if (RECEIVE_COMMIT_INTERVAL > 0) {
                     sessionConsumer.commit();
                  }

                  completedFine.incrementAndGet();

               } catch (Throwable e) {
                  logger.warn(e.getMessage(), e);
                  errors.incrementAndGet();
               } finally {
                  latchDone.countDown();
               }
            });
         }

         connectionConsumer.start();

         assertTrue(latchDone.await(TIMEOUT_MINUTES, TimeUnit.MINUTES));
      }

      service.shutdown();
      assertTrue(service.awaitTermination(TIMEOUT_MINUTES, TimeUnit.MINUTES), "Test Timed Out");
      assertEquals(0, errors.get());
      assertEquals(DESTINATIONS, completedFine.get());

      validatePageFolders(purgePage);

      killServer(serverProcess, false);

      serverProcess = startServer(SERVER_NAME_0, 0, -1);
      assertTrue(ServerUtil.waitForServerToStart(0, SERVER_START_TIMEOUT));

      validatePageFolders(purgePage);
   }

   private static void validatePageFolders(boolean purgePage) throws Exception {
      File dataFolder = new File(selectedDataFolder(), "paging");
      if (purgePage) {
         Wait.assertEquals(0, () -> (Objects.requireNonNull(dataFolder.listFiles())).length, 5000, 100);
      } else {
         Wait.assertTrue(() -> Objects.requireNonNull(dataFolder.listFiles()).length > 0, 5000, 100);
      }
   }

}
