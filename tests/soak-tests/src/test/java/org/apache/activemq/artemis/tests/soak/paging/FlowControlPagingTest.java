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

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.soak.SoakTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.TestParameters;
import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

import static org.apache.activemq.artemis.utils.TestParameters.testProperty;

/**
 * Refer to ./scripts/parameters.sh for suggested parameters #You may choose to use zip files to save some time on
 * producing if you want to run this test over and over when debugging export TEST_FLOW_ZIP_LOCATION=a folder
 */
@ExtendWith(ParameterizedTestExtension.class)
public class FlowControlPagingTest extends SoakTestBase {

   private static final String TEST_NAME = "FLOW";

   private final String protocol;
   private static final String ZIP_LOCATION = testProperty(null, "ZIP_LOCATION", null);
   private static final int SERVER_START_TIMEOUT = testProperty(TEST_NAME, "SERVER_START_TIMEOUT", 300_000);
   private static final int TIMEOUT_MINUTES = testProperty(TEST_NAME, "TIMEOUT_MINUTES", 120);
   private static final String PROTOCOL_LIST = testProperty(TEST_NAME, "PROTOCOL_LIST", "CORE"); // this test was about CORE mainly
   private static final int PRINT_INTERVAL = testProperty(TEST_NAME, "PRINT_INTERVAL", 100);
   private static final boolean TEST_ENABLED = Boolean.parseBoolean(testProperty(TEST_NAME, "TEST_ENABLED", "true"));

   private final int MESSAGES;
   private final int COMMIT_INTERVAL;
   // if 0 will use AUTO_ACK
   private final int RECEIVE_COMMIT_INTERVAL;
   private final int MESSAGE_SIZE;


   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final String SERVER_NAME_0 = "flowControlPaging";


   @BeforeAll
   public static void createServers() throws Exception {
      {
         File serverLocation = getFileServerLocation(SERVER_NAME_0);
         deleteDirectory(serverLocation);

         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer.setRole("amq").setUser("admin").setPassword("admin").setAllowAnonymous(true).setNoWeb(false).setArtemisInstance(serverLocation);
         // some limited memory to make it more likely to fail
         cliCreateServer.setArgs("--java-memory", "512M");
         cliCreateServer.setConfiguration("./src/main/resources/servers/flowControlPaging");
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

   public FlowControlPagingTest(String protocol) {
      this.protocol = protocol;
      MESSAGES = TestParameters.testProperty(TEST_NAME, protocol + "_MESSAGES", 50_000);
      COMMIT_INTERVAL = TestParameters.testProperty(TEST_NAME, protocol + "_COMMIT_INTERVAL", 1000);
      // if 0 will use AUTO_ACK
      RECEIVE_COMMIT_INTERVAL = TestParameters.testProperty(TEST_NAME, protocol + "_RECEIVE_COMMIT_INTERVAL", 0);
      MESSAGE_SIZE = TestParameters.testProperty(TEST_NAME, protocol + "_MESSAGE_SIZE", 10000);
   }

   Process serverProcess;

   boolean unzipped = false;

   private String getZipName() {
      return "flow-data-" + protocol + "-" + MESSAGES + "-" + MESSAGE_SIZE + ".zip";
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

   @TestTemplate
   public void testFlow() throws Exception {
      String QUEUE_NAME = "QUEUE_FLOW";
      ConnectionFactory factory = CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616");
      AtomicInteger errors = new AtomicInteger(0);

      ExecutorService service = Executors.newFixedThreadPool(1);
      runAfter(service::shutdownNow);

      if (!unzipped) {
         Connection connection = factory.createConnection();
         runAfter(connection::close);

         String text = RandomUtil.randomAlphaNumericString(MESSAGE_SIZE);

         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         Queue queue = session.createQueue(QUEUE_NAME);
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
         connection.close();
         killServer(serverProcess);
      }


      if (ZIP_LOCATION != null && !unzipped) {
         String fileName = getZipName();
         zip(new File(ZIP_LOCATION, fileName), new File(getServerLocation(SERVER_NAME_0)));
      }

      serverProcess = startServer(SERVER_NAME_0, 0, SERVER_START_TIMEOUT);


      final ConnectionFactory factoryClient;

      if (protocol.equals("CORE")) {
         factoryClient = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616?consumerWindowSize=-1");
      } else if (protocol.equals("AMQP")) {
         factoryClient = CFUtil.createConnectionFactory("AMQP", "amqp://localhost:61616?jms.prefetchPolicy.queuePrefetch=100000");
      } else if (protocol.equals("OPENWIRE")) {
         factoryClient = CFUtil.createConnectionFactory("OPENWIRE", "tcp://localhost:61616");  // no flow control on openwire by default
      } else {
         throw new IllegalStateException("protocol not defined!");
      }

      Connection connectionConsumer = factoryClient.createConnection();

      runAfter(connectionConsumer::close);

      AtomicInteger completedFine = new AtomicInteger(0);

      service.execute(() -> {
         try {
            Session sessionConsumer;

            if (RECEIVE_COMMIT_INTERVAL <= 0) {
               sessionConsumer = connectionConsumer.createSession(false, Session.AUTO_ACKNOWLEDGE);
            } else {
               sessionConsumer = connectionConsumer.createSession(true, Session.SESSION_TRANSACTED);
            }

            MessageConsumer messageConsumer = sessionConsumer.createConsumer(sessionConsumer.createQueue(QUEUE_NAME));
            for (int m = 0; m < MESSAGES; m++) {
               TextMessage message = (TextMessage) messageConsumer.receive(5_000);
               if (message == null) {
                  sessionConsumer.commit();
                  System.out.println("TX was flow controlled, no message received, consider lowering your TX interval, m = " + m);
                  m--;
                  continue;
               }

               assertEquals(m, message.getIntProperty("m"));

               // The sending commit interval here will be used for printing
               if (PRINT_INTERVAL > 0 && m % PRINT_INTERVAL == 0) {
                  logger.info("Destination {} received {} {} messages", QUEUE_NAME, m, protocol);
               }

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
         }
      });

      connectionConsumer.start();

      service.shutdown();
      assertTrue(service.awaitTermination(TIMEOUT_MINUTES, TimeUnit.MINUTES), "Test Timed Out");
      assertEquals(0, errors.get());

      connectionConsumer.close();
   }

}
