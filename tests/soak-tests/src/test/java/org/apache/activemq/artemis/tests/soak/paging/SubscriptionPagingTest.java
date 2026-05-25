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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.soak.SoakTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.TestParameters;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * Refer to ./scripts/parameters.sh for suggested parameters. You may choose to use zip files to save some time on
 * producing if you want to run this test over and over when debugging:
 * <pre>
 * export TEST_FLOW_ZIP_LOCATION=a folder
 * </pre>
 */
@ExtendWith(ParameterizedTestExtension.class)
public class SubscriptionPagingTest extends SoakTestBase {

   private static final String TEST_NAME = "SUBSCRIPTION";

   private final String protocol;
   private static final String ZIP_LOCATION = testProperty(null, "ZIP_LOCATION", null);
   private static final int SERVER_START_TIMEOUT = testProperty(TEST_NAME, "SERVER_START_TIMEOUT", 300_000);
   private static final int TIMEOUT_MINUTES = testProperty(TEST_NAME, "TIMEOUT_MINUTES", 120);
   private static final String PROTOCOL_LIST = testProperty(TEST_NAME, "PROTOCOL_LIST", "CORE");
   private static final int PRINT_INTERVAL = testProperty(TEST_NAME, "PRINT_INTERVAL", 100);
   private static final boolean TEST_ENABLED = Boolean.parseBoolean(testProperty(TEST_NAME, "TEST_ENABLED", "true"));

   private final int MESSAGES;
   private final int COMMIT_INTERVAL;
   // if 0 will use AUTO_ACK
   private final int RECEIVE_COMMIT_INTERVAL;
   private final int MESSAGE_SIZE;
   private final int SLOW_SUBSCRIPTIONS;
   private final int SLEEP_SLOW;

   final String TOPIC_NAME = "SUB_TEST";

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final String SERVER_NAME_0 = "subscriptionPaging";

   @BeforeAll
   public static void createServers() throws Exception {
      {
         File serverLocation = getFileServerLocation(SERVER_NAME_0);
         deleteDirectory(serverLocation);

         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer.setRole("amq").setUser("admin").setPassword("admin").setAllowAnonymous(true).setNoWeb(false).setArtemisInstance(serverLocation);
         // some limited memory to make it more likely to fail
         cliCreateServer.setArgs("--java-memory", "512M");
         cliCreateServer.setConfiguration("./src/main/resources/servers/subscriptionPaging");
         cliCreateServer.createServer();
      }
   }


   @Parameters(name = "protocol={0}")
   public static Collection<Object[]> parameters() {
      String[] protocols = PROTOCOL_LIST.split(",");

      List<Object[]> parameters = new ArrayList<>();
      for (String str : protocols) {
         logger.info("Adding {} to the list for the test", str);
         parameters.add(new Object[]{str});
      }

      return parameters;
   }

   public SubscriptionPagingTest(String protocol) {
      this.protocol = protocol;
      MESSAGES = TestParameters.testProperty(TEST_NAME, protocol + "_MESSAGES", 1000);
      COMMIT_INTERVAL = TestParameters.testProperty(TEST_NAME, protocol + "_COMMIT_INTERVAL", 100);
      // if 0 will use AUTO_ACK
      RECEIVE_COMMIT_INTERVAL = TestParameters.testProperty(TEST_NAME, protocol + "_RECEIVE_COMMIT_INTERVAL", 100);
      MESSAGE_SIZE = TestParameters.testProperty(TEST_NAME, protocol + "_MESSAGE_SIZE", 1000);
      SLOW_SUBSCRIPTIONS = TestParameters.testProperty(TEST_NAME, "SLOW_SUBSCRIPTIONS", 100);
      SLEEP_SLOW = testProperty(TEST_NAME, "SLEEP_SLOW", 1000);
   }

   Process serverProcess;

   boolean unzipped = false;

   private String getZipName() {
      return "subscription-" + protocol + "-" + MESSAGES + "-" + MESSAGE_SIZE + "-" + SLOW_SUBSCRIPTIONS + ".zip";
   }

   @BeforeEach
   public void before() throws Exception {
      assumeTrue(TEST_ENABLED);
      cleanupData(SERVER_NAME_0);

      boolean useZip = ZIP_LOCATION != null;
      String zipName = getZipName();
      File zipFile = useZip ? new File(ZIP_LOCATION + "/" + zipName) : null;

      if (ZIP_LOCATION != null && zipFile.exists()) {
         unzipped = true;
         unzip(zipFile, new File(getServerLocation(SERVER_NAME_0)));
      }

      serverProcess = startServer(SERVER_NAME_0, 0, SERVER_START_TIMEOUT);
   }

   private void receive(ConnectionFactory factory, String clientID, String name, int txInterval, AtomicInteger errors, AtomicInteger sleepTime, int numberOfMessages, Runnable callbackDone) {
      try {

         Connection connection = factory.createConnection();
         try {
            connection.setClientID(clientID);
            connection.start();

            Session session;

            if (txInterval == 0) {
               session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            } else {
               session = connection.createSession(true, Session.SESSION_TRANSACTED);
            }

            Topic topic = session.createTopic(TOPIC_NAME);

            TopicSubscriber subscriber = session.createDurableSubscriber(topic, name);

            int commitPending = 0;
            for (int i = 0; i < numberOfMessages; i++) {
               Message message = subscriber.receive(5_000);
               if (message == null) {
                  logger.info("Receiver {} subscription {} did not receive a message", clientID, name);
                  i--;
                  continue;
               }

               if (i % PRINT_INTERVAL == 0) {
                  logger.info("Received {} on {}_{}", i, clientID, name);
               }

               assertEquals(i, message.getIntProperty("m"));

               if (txInterval > 0) {
                  commitPending++;

                  if (commitPending >= txInterval) {
                     session.commit();
                     commitPending = 0;
                  }
               }


               int timeout = sleepTime.get();

               if (timeout > 0) {
                  logger.info("Sleeping for {} on {}_{}", timeout, clientID, name);
                  Thread.sleep(timeout);
               }
            }


         } finally {
            connection.close();

            if (callbackDone != null) {
               callbackDone.run();
            }
         }
      } catch (Throwable e) {
         logger.warn(e.getMessage(), e);
         errors.incrementAndGet();
      }


   }

   @TestTemplate
   public void testSubscription() throws Exception {
      ConnectionFactory factory = CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616");
      AtomicInteger errors = new AtomicInteger(0);

      ExecutorService service = Executors.newFixedThreadPool(SLOW_SUBSCRIPTIONS + 1);
      runAfter(service::shutdownNow);


      final ConnectionFactory factoryClient;

      if (protocol.equals("CORE")) {
         factoryClient = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616?consumerWindowSize=-1");
      } else if (protocol.equals("AMQP")) {
         factoryClient = CFUtil.createConnectionFactory("AMQP", "amqp://localhost:61616?jms.prefetchPolicy.queuePrefetch=-1");
      } else if (protocol.equals("OPENWIRE")) {
         factoryClient = CFUtil.createConnectionFactory("OPENWIRE", "tcp://localhost:61616");  // no flow control on openwire by default
      } else {
         throw new IllegalStateException("protocol not defined!");
      }

      {
         // just creating the subscriptions before we actually send messages
         CountDownLatch countDownLatch = new CountDownLatch(SLOW_SUBSCRIPTIONS + 1);
         service.execute(() -> receive(factoryClient, "fast", "fast", 0, errors, new AtomicInteger(0), 0, countDownLatch::countDown));
         for (int i = 0; i < SLOW_SUBSCRIPTIONS; i++) {
            final int finalI = i;
            service.execute(() -> receive(factoryClient, "slow_" + finalI, "slow_" + finalI, 0, errors, new AtomicInteger(0), 0, countDownLatch::countDown));
         }

         assertTrue(countDownLatch.await(1, TimeUnit.MINUTES));
         assertEquals(0, errors.get());
      }

      if (!unzipped) {
         Connection connection = factory.createConnection();
         runAfter(connection::close);

         String text = RandomUtil.randomAlphaNumericString(MESSAGE_SIZE);

         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         Destination destination = session.createTopic(TOPIC_NAME);
         MessageProducer producer = session.createProducer(destination);
         for (int m = 0; m < MESSAGES; m++) {
            TextMessage message = session.createTextMessage(text);
            message.setIntProperty("m", m);
            producer.send(message);
            if (m > 0 && m % COMMIT_INTERVAL == 0) {
               logger.info("Sent {} {} messages on queue {}", m, protocol, destination);
               session.commit();
            }
         }

         session.commit();
         session.close();
         connection.close();
         killServer(serverProcess);
      }

      if (ZIP_LOCATION != null && !unzipped) {
         String fileName = getZipName();
         zip(new File(ZIP_LOCATION, fileName), new File(getServerLocation(SERVER_NAME_0)));
      }

      serverProcess = startServer(SERVER_NAME_0, 0, SERVER_START_TIMEOUT);

      // just creating the subscriptions before we actually send messages
      CountDownLatch countDownLatch = new CountDownLatch(SLOW_SUBSCRIPTIONS + 1);
      AtomicInteger sleepInterval = new AtomicInteger(SLEEP_SLOW);
      service.execute(() -> receive(factoryClient, "fast", "fast", RECEIVE_COMMIT_INTERVAL, errors, new AtomicInteger(0), MESSAGES, () -> {
         // after the fast consumer is done, the slow consumers will become fast
         sleepInterval.set(0);
         countDownLatch.countDown();
      }));
      for (int i = 0; i < SLOW_SUBSCRIPTIONS; i++) {
         final int finalI = i;
         service.execute(() -> receive(factoryClient, "slow_" + finalI, "slow_" + finalI, RECEIVE_COMMIT_INTERVAL, errors, sleepInterval, MESSAGES, countDownLatch::countDown));
      }

      assertTrue(countDownLatch.await(TIMEOUT_MINUTES, TimeUnit.MINUTES));
      assertEquals(0, errors.get());

      service.shutdown();
      assertTrue(service.awaitTermination(1, TimeUnit.MINUTES), "Test Timed Out");
      assertEquals(0, errors.get());

   }

}
