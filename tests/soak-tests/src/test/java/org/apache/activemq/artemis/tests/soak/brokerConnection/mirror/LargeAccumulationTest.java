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
import javax.jms.Destination;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import java.io.File;
import java.io.StringWriter;
import java.lang.invoke.MethodHandles;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.management.SimpleManagement;
import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectionAddressType;
import org.apache.activemq.artemis.tests.soak.SoakTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.util.ServerUtil;
import org.apache.activemq.artemis.utils.FileUtil;
import org.apache.activemq.artemis.utils.TestParameters;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LargeAccumulationTest extends SoakTestBase {

   private static final String TEST_NAME = "LARGE_ACCUMULATION";
   private static final String AMQP_CREDITS = TestParameters.testProperty(TEST_NAME, "AMQP_CREDITS", "1000");
   private static final String MAX_PENDING_ACKS = TestParameters.testProperty(TEST_NAME, "MAX_PENDING_ACKS", "20");
   private static final boolean NO_WEB = Boolean.parseBoolean(TestParameters.testProperty(TEST_NAME, "NO_WEB", "true"));
   private static final int NUMBER_OF_THREADS = Integer.parseInt(TestParameters.testProperty(TEST_NAME, "THREADS", "20"));
   private static final int NUMBER_OF_SUBSCRIPTIONS = Integer.parseInt(TestParameters.testProperty(TEST_NAME, "NUMBER_OF_SUBSCRIPTIONS", "2"));
   private static final int NUMBER_OF_LARGE_MESSAGES = Integer.parseInt(TestParameters.testProperty(TEST_NAME, "NUMBER_OF_LARGE_MESSAGES", "25"));
   private static final int SIZE_OF_LARGE_MESSAGE = Integer.parseInt(TestParameters.testProperty(TEST_NAME, "SIZE_OF_LARGE_MESSAGE", "200000"));
   private static final int NUMBER_OF_REGULAR_MESSAGES = Integer.parseInt(TestParameters.testProperty(TEST_NAME, "NUMBER_OF_REGULAR_MESSAGES", "500"));
   private static final int SIZE_OF_REGULAR_MESSAGE = Integer.parseInt(TestParameters.testProperty(TEST_NAME, "SIZE_OF_REGULAR_MESSAGE", "30000"));
   private static final int LARGE_TIMEOUT_MINUTES = Integer.parseInt(TestParameters.testProperty(TEST_NAME, "LARGE_TIMEOUT_MINUTES", "1"));
   private static final boolean USE_DEBUG = Boolean.parseBoolean(TestParameters.testProperty(TEST_NAME, "DEBUG", "false"));

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static String body;

   static {
      StringWriter writer = new StringWriter();
      while (writer.getBuffer().length() < 10 * 1024) {
         writer.append("This is a string ..... ");
      }
      body = writer.toString();
   }

   private static final String TOPIC_NAME = "LargeTopic";
   private static final String QUEUE_NAME = "LargeQueue";

   public static final String DC1_NODE_A = "LargeAccumulationTest/DC1";
   public static final String DC2_NODE_A = "LargeAccumulationTest/DC2";

   private static final String SNF_QUEUE = "$ACTIVEMQ_ARTEMIS_MIRROR_mirror";

   Process processDC1_node_A;
   Process processDC2_node_A;

   private static String DC1_NODEA_URI = "tcp://localhost:61616";
   private static String DC2_NODEA_URI = "tcp://localhost:61618";

   private static void createServer(String serverName,
                                    String connectionName,
                                    String mirrorURI,
                                    int portOffset) throws Exception {

      File serverLocation = getFileServerLocation(serverName);
      deleteDirectory(serverLocation);

      HelperCreate cliCreateServer = helperCreate();
      cliCreateServer.setAllowAnonymous(true).setNoWeb(true).setArtemisInstance(serverLocation);
      cliCreateServer.setMessageLoadBalancing("ON_DEMAND");
      cliCreateServer.setClustered(false);
      cliCreateServer.setNoWeb(NO_WEB);
      cliCreateServer.setArgs("--no-stomp-acceptor", "--no-hornetq-acceptor", "--no-mqtt-acceptor", "--no-amqp-acceptor", "--max-hops", "1", "--name", DC1_NODE_A);
      cliCreateServer.addArgs("--addresses", TOPIC_NAME);
      cliCreateServer.addArgs("--queues", QUEUE_NAME);
      cliCreateServer.addArgs("--java-memory", "2G");
      cliCreateServer.setPortOffset(portOffset);
      cliCreateServer.createServer();

      Properties brokerProperties = new Properties();
      brokerProperties.put("AMQPConnections." + connectionName + ".uri", mirrorURI);
      brokerProperties.put("AMQPConnections." + connectionName + ".retryInterval", "1000");
      brokerProperties.put("AMQPConnections." + connectionName + ".type", AMQPBrokerConnectionAddressType.MIRROR.toString());
      brokerProperties.put("AMQPConnections." + connectionName + ".connectionElements.mirror.sync", "false");
      brokerProperties.put("largeMessageSync", "false");
      brokerProperties.put("pageSyncTimeout", "" + TimeUnit.MILLISECONDS.toNanos(1));
      brokerProperties.put("messageExpiryScanPeriod", "-1");

      brokerProperties.put("addressSettings.#.maxSizeBytes", "100MB");
      brokerProperties.put("addressSettings.#.maxSizeMessages", "1000");
      brokerProperties.put("addressSettings.#.addressFullMessagePolicy", "PAGING");
      brokerProperties.put("addressSettings.#.maxReadPageMessages", "15000");
      brokerProperties.put("addressSettings.#.maxReadPageBytes", "-1");
      brokerProperties.put("addressSettings.#.prefetchPageMessages", "100");
      brokerProperties.put("mirrorPageTransaction", "true");
      brokerProperties.put("mirrorAckManagerQueueAttempts", "1");
      brokerProperties.put("mirrorAckManagerPageAttempts", "1");
      brokerProperties.put("acceptorConfigurations.artemis.extraParams.amqpCredits", AMQP_CREDITS);
      brokerProperties.put("acceptorConfigurations.artemis.extraParams.mirrorMaxPendingAcks", MAX_PENDING_ACKS);  // 200_000

      if (USE_DEBUG) {
         replaceLogs(serverLocation);
      }

      File brokerPropertiesFile = new File(serverLocation, "broker.properties");
      saveProperties(brokerProperties, brokerPropertiesFile);
   }

   private static void replaceLogs(File serverLocation) throws Exception {
      File log4j = new File(serverLocation, "/etc/log4j2.properties");
      // usual places to debug while dealing with this test
      assertTrue(FileUtil.findReplace(log4j, "logger.artemis_utils.level=INFO", "logger.artemis_utils.level=INFO\n" +
         "\n" + "logger.db1.name=org.apache.activemq.artemis.protocol.amqp.connect.mirror.AckManager\n"
         + "logger.db1.level=DEBUG"));
   }


   @BeforeAll
   public static void createServers() throws Exception {
      createServer(DC1_NODE_A, "mirror", DC2_NODEA_URI, 0);
      createServer(DC2_NODE_A, "mirror", DC1_NODEA_URI, 2);
   }

   @BeforeEach
   public void cleanupServers() {
      cleanupData(DC1_NODE_A);
      cleanupData(DC2_NODE_A);
   }

   public CountDownLatch send(Executor executor,
                              AtomicInteger errors,
                              int threads,
                              ConnectionFactory connectionFactory,
                              int numberOfMessgesPerThread,
                              int commitInterval,
                              int sizePerMessage,
                              Destination destination,
                              String sendDescription) {
      CountDownLatch done = new CountDownLatch(threads);
      AtomicInteger messageSent = new AtomicInteger(0);
      String body = "a".repeat(sizePerMessage);
      for (int i = 0; i < threads; i++) {
         executor.execute(() -> {
            int commitControl = 0;
            try (Connection connection = connectionFactory.createConnection()) {
               Session session;

               session = connection.createSession(true, Session.SESSION_TRANSACTED);
               MessageProducer producer = session.createProducer(destination);
               for (int m = 0; m < numberOfMessgesPerThread; m++) {
                  producer.send(session.createTextMessage(body));
                  int sent = messageSent.incrementAndGet();
                  if (commitControl++ % commitInterval == 0) {
                     session.commit();
                  }
                  if (sent % 100 == 0) {
                     logger.info("message sent {} on {} from {}", sent, destination, sendDescription);
                  }
               }
               session.commit();
            } catch (Exception e) {
               logger.warn(e.getMessage(), e);
               errors.incrementAndGet();
            } finally {
               done.countDown();
            }
         });
      }
      return done;
   }

   public void consume(Executor executor,
                       AtomicInteger errors,
                       int threads,
                       ConnectionFactory connectionFactory,
                       int numberOfMessgesPerThread,
                       int commitInterval,
                       String subscriptionID,
                       Destination destination,
                       CountDownLatch done) {
      AtomicInteger messagesConsumed = new AtomicInteger(0);
      for (int i = 0; i < threads; i++) {
         executor.execute(() -> {
            int commitControl = 0;
            try (Connection connection = connectionFactory.createConnection()) {

               Session session;

               session = connection.createSession(true, Session.SESSION_TRANSACTED);
               MessageConsumer consumer;

               if (subscriptionID != null) {
                  consumer = session.createSharedDurableConsumer((Topic) destination, subscriptionID);
               } else {
                  consumer = session.createConsumer(destination);
               }

               connection.start();

               for (int m = 0; m < numberOfMessgesPerThread; m++) {
                  TextMessage message = (TextMessage) consumer.receive(TimeUnit.MINUTES.toMillis(LARGE_TIMEOUT_MINUTES));
                  assertNotNull(message);
                  if (commitControl++ % commitInterval == 0) {
                     session.commit();
                  }
                  int consumed = messagesConsumed.incrementAndGet();
                  if (consumed % 100 == 0) {
                     logger.info("message consumed {} on {}", consumed, destination);
                  }
               }
               session.commit();
            } catch (Throwable e) {
               logger.warn(e.getMessage(), e);
               errors.incrementAndGet();
            } finally {
               done.countDown();
            }
         });
      }
   }

   @Test
   public void testLargeAccumulation() throws Exception {

      final boolean useTopic = true;
      final boolean useQueue = true;

      AtomicInteger errors = new AtomicInteger(0);

      // producers will have 2 sets of producers (queue and topic)
      // while consumers will have 1 consumer for the queue, and one consumer for each topic subscription
      // and the same is used for both consumers and producers
      ExecutorService service = Executors.newFixedThreadPool(NUMBER_OF_THREADS * (1 + NUMBER_OF_SUBSCRIPTIONS));
      runAfter(service::shutdownNow);

      String protocol = "AMQP";
      startDC1();
      startDC2();

      final int commitInterval = 1;

      ConnectionFactory[] cfs = new ConnectionFactory[]{CFUtil.createConnectionFactory(protocol, DC1_NODEA_URI + "?jms.prefetchPolicy.queuePrefetch=10"), CFUtil.createConnectionFactory(protocol, DC2_NODEA_URI + "?jms.prefetchPolicy.queuePrefetch=10")};
      SimpleManagement[] sm = new SimpleManagement[]{new SimpleManagement(DC1_NODEA_URI, null, null), new SimpleManagement(DC2_NODEA_URI, null, null)};

      Queue largeQueue = null;
      Topic largeTopic = null;

      // creating subscriptions and lookup queue names with session.createTopic and session.createQueue
      for (int i = 0; i < NUMBER_OF_SUBSCRIPTIONS; i++) {
         try (Connection connection = cfs[0].createConnection()) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            if (largeTopic == null) {
               largeTopic = session.createTopic(TOPIC_NAME);
               largeQueue = session.createQueue(QUEUE_NAME);
            }
            MessageConsumer consumer = session.createSharedDurableConsumer(largeTopic, "sub_" + i);
         }
      }

      CountDownLatch doneTopic = null, doneQueue = null;

      if (useTopic) {
         doneTopic = send(service, errors, NUMBER_OF_THREADS, cfs[0], NUMBER_OF_LARGE_MESSAGES, 10, SIZE_OF_LARGE_MESSAGE, largeTopic, "LargeMessageTopic");
      }
      if (useQueue) {
         doneQueue = send(service, errors, NUMBER_OF_THREADS, cfs[0], NUMBER_OF_LARGE_MESSAGES, 10, SIZE_OF_LARGE_MESSAGE, largeQueue, "LargeMessageQueue");
      }

      if (useTopic) {
         assertTrue(doneTopic.await(LARGE_TIMEOUT_MINUTES, TimeUnit.MINUTES));
      }
      if (useQueue) {
         assertTrue(doneQueue.await(LARGE_TIMEOUT_MINUTES, TimeUnit.MINUTES));
      }

      assertEquals(0, errors.get());

      if (useTopic) {
         doneTopic = send(service, errors, NUMBER_OF_THREADS, cfs[0], NUMBER_OF_REGULAR_MESSAGES, 100, SIZE_OF_REGULAR_MESSAGE, largeTopic, "MediumMessageTopic");
      }
      if (useQueue) {
         doneQueue = send(service, errors, NUMBER_OF_THREADS, cfs[0], NUMBER_OF_REGULAR_MESSAGES, 100, SIZE_OF_REGULAR_MESSAGE, largeQueue, "MediumMessageQueue");
      }

      if (useTopic) {
         assertTrue(doneTopic.await(LARGE_TIMEOUT_MINUTES, TimeUnit.MINUTES));
      }
      if (useQueue) {
         assertTrue(doneQueue.await(LARGE_TIMEOUT_MINUTES, TimeUnit.MINUTES));
      }
      assertEquals(0, errors.get());

      matchMessageCounts(sm, (long) (NUMBER_OF_LARGE_MESSAGES + NUMBER_OF_REGULAR_MESSAGES) * NUMBER_OF_THREADS, useTopic, useQueue, true);

      if (useQueue) {
         doneQueue = new CountDownLatch(NUMBER_OF_THREADS);
         consume(service, errors, NUMBER_OF_THREADS, cfs[0], NUMBER_OF_LARGE_MESSAGES + NUMBER_OF_REGULAR_MESSAGES, 100, null, largeQueue, doneQueue);
      }
      if (useTopic) {
         doneTopic = new CountDownLatch(NUMBER_OF_THREADS * NUMBER_OF_SUBSCRIPTIONS);
         for (int i = 0; i < NUMBER_OF_SUBSCRIPTIONS; i++) {
            consume(service, errors, NUMBER_OF_THREADS, cfs[0], NUMBER_OF_LARGE_MESSAGES + NUMBER_OF_REGULAR_MESSAGES, 100, "sub_" + i, largeTopic, doneTopic);
         }
      }

      if (useTopic) {
         assertTrue(doneTopic.await(LARGE_TIMEOUT_MINUTES, TimeUnit.MINUTES));
      }

      if (useQueue) {
         assertTrue(doneQueue.await(LARGE_TIMEOUT_MINUTES, TimeUnit.MINUTES));
      }

      assertEquals(0, errors.get());

      matchMessageCounts(sm, 0, useTopic, useQueue, true);
   }

   private boolean matchMessageCounts(SimpleManagement[] sm,
                                      long numberOfMessages,
                                      boolean useTopic,
                                      boolean useQueue,
                                      boolean useWait) throws Exception {
      for (SimpleManagement s : sm) {
         logger.debug("Checking counts on SNF for {}", s.getUri());
         if (useWait) {
            Wait.assertEquals((long) 0, () -> s.getMessageCountOnQueue(SNF_QUEUE), TimeUnit.MINUTES.toMillis(LARGE_TIMEOUT_MINUTES), 100);
         } else {
            if (s.getMessageCountOnQueue(SNF_QUEUE) != 0) {
               return false;
            }
         }

         if (useTopic) {
            for (int i = 0; i < NUMBER_OF_SUBSCRIPTIONS; i++) {
               String subscriptionName = "sub_" + i + ":global";
               logger.debug("Checking counts on {} on {}", subscriptionName, s.getUri());
               if (useWait) {
                  Wait.assertEquals(numberOfMessages, () -> s.getMessageCountOnQueue(subscriptionName), TimeUnit.MINUTES.toMillis(LARGE_TIMEOUT_MINUTES), 100);
               } else {
                  if (s.getMessageCountOnQueue(subscriptionName) != numberOfMessages) {
                     return false;
                  }
               }
            }
         }

         if (useQueue) {
            if (useWait) {
               Wait.assertEquals(numberOfMessages, () -> s.getMessageCountOnQueue(QUEUE_NAME), TimeUnit.MINUTES.toMillis(LARGE_TIMEOUT_MINUTES), 100);
            } else {
               if (s.getMessageCountOnQueue(QUEUE_NAME) != numberOfMessages) {
                  return false;
               }
            }
         }
      }
      return true;
   }

   int getNumberOfLargeMessages(String serverName) throws Exception {
      File lmFolder = new File(getServerLocation(serverName) + "/data/large-messages");
      assertTrue(lmFolder.exists());
      return lmFolder.list().length;
   }

   private void startDC1() throws Exception {
      processDC1_node_A = startServer(DC1_NODE_A, -1, -1, new File(getServerLocation(DC1_NODE_A), "broker.properties"));
      ServerUtil.waitForServerToStart(0, 10_000);
   }

   private void stopDC1() throws Exception {
      processDC1_node_A.destroyForcibly();
      assertTrue(processDC1_node_A.waitFor(10, TimeUnit.SECONDS));
   }

   private void stopDC2() throws Exception {
      processDC2_node_A.destroyForcibly();
      assertTrue(processDC2_node_A.waitFor(10, TimeUnit.SECONDS));
   }

   private void startDC2() throws Exception {
      processDC2_node_A = startServer(DC2_NODE_A, -1, -1, new File(getServerLocation(DC2_NODE_A), "broker.properties"));
      ServerUtil.waitForServerToStart(2, 10_000);
   }
}