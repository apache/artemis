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

package org.apache.activemq.artemis.tests.soak.owleak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.soak.SoakTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.SpawnedVMSupport;
import org.apache.activemq.artemis.utils.TestParameters;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.activemq.artemis.utils.TestParameters.testProperty;

/**
 * Refer to ./scripts/parameters.sh for suggested parameters
 * <p>
 * Even though this test is not testing Paging, it will use Page just to generate enough load to the server to compete
 * for resources in Native Buffers.
 */
@ExtendWith(ParameterizedTestExtension.class)
public class OWLeakTest extends SoakTestBase {

   private static final int OK = 33; // arbitrary code. if the spawn returns this the test went fine

   public static final String SERVER_NAME_0 = "openwire-leaktest";

   @BeforeAll
   public static void createServers() throws Exception {
      {
         File serverLocation = getFileServerLocation(SERVER_NAME_0);
         deleteDirectory(serverLocation);

         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer.setRole("amq").setUser("admin").setPassword("admin").setAllowAnonymous(true).setNoWeb(false).setArtemisInstance(serverLocation);
         cliCreateServer.createServer();
      }
   }

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
   private static final String TEST_NAME = "OW_LEAK";
   private static final boolean TEST_ENABLED = Boolean.parseBoolean(testProperty(TEST_NAME, "TEST_ENABLED", "true"));
   private static final String PROTOCOL_LIST = testProperty(TEST_NAME, "PROTOCOL_LIST", "OPENWIRE");
   private static final int TEST_TIMEOUT_MINUTES = testProperty(TEST_NAME, "TIMETOUT_MINUTES", 10);
   private final String protocol;
   private final int NUMBER_OF_MESSAGES;
   private final int PRODUCERS;
   private final int MESSAGE_SIZE;
   Process serverProcess;

   public OWLeakTest(String protocol) {
      this.protocol = protocol;
      NUMBER_OF_MESSAGES = TestParameters.testProperty(TEST_NAME, protocol + "_NUMBER_OF_MESSAGES", 50);
      PRODUCERS = TestParameters.testProperty(TEST_NAME, protocol + "_PRODUCERS", 5);
      MESSAGE_SIZE = TestParameters.testProperty(TEST_NAME, protocol + "_MESSAGE_SIZE", 10_000);
   }

   @Parameters(name = "protocol={0}")
   public static Collection<Object[]> parameters() {
      String[] protocols = PROTOCOL_LIST.split(",");

      List<Object[]> parameters = new ArrayList<>();
      for (String str : protocols) {
         logger.debug("Adding {} to the list for the test", str);
         parameters.add(new Object[]{str});
      }

      return parameters;
   }

   @BeforeEach
   public void before() throws Exception {
      assumeTrue(TEST_ENABLED);
      cleanupData(SERVER_NAME_0);

      serverProcess = startServer(SERVER_NAME_0, 0, 10_000);
   }


   private static String createLMBody(int messageSize, int producer, int sequence) {
      StringBuilder sb = new StringBuilder();
      String baseString = "A Large body from producer " + producer + ", sequence " + sequence;

      while (sb.length() < messageSize) {
         sb.append(baseString);
      }
      return sb.toString();
   }


   public static void main(String[] arg) {
      int PRODUCERS = Integer.parseInt(arg[0]);
      int NUMBER_OF_MESSAGES = Integer.parseInt(arg[1]);
      int MESSAGE_SIZE = Integer.parseInt(arg[2]);
      String protocol = arg[3];
      ExecutorService service = Executors.newFixedThreadPool(PRODUCERS + 1 + 1);

      String QUEUE_NAME = "some_queue";

      Semaphore semaphore = new Semaphore(PRODUCERS);

      CountDownLatch latch = new CountDownLatch(PRODUCERS + 1 + 1);

      AtomicBoolean running = new AtomicBoolean(true);

      AtomicInteger errors = new AtomicInteger(0);

      try {

         for (int i = 0; i < PRODUCERS; i++) {
            final int producerID = i;
            ConnectionFactory factory = CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616");
            service.execute(() -> {
               try {
                  for (int msg = 0; msg < NUMBER_OF_MESSAGES; msg++) {
                     Connection connection = factory.createConnection();
                     Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                     MessageProducer producer = session.createProducer(session.createQueue(QUEUE_NAME));
                     TextMessage message = session.createTextMessage(createLMBody(MESSAGE_SIZE, producerID, msg));
                     message.setIntProperty("producerID", producerID);
                     message.setIntProperty("sequence", msg);
                     semaphore.acquire();
                     producer.send(message);
                     logger.debug("Thread {} Sent message with size {} with the total number of {} messages of {}", producerID, MESSAGE_SIZE, msg, NUMBER_OF_MESSAGES);
                     producer.close();
                     session.close();
                     connection.close();
                  }
               } catch (Exception e) {
                  errors.incrementAndGet();
                  e.printStackTrace();
                  logger.warn(e.getMessage(), e);
               } finally {
                  latch.countDown();
               }
            });
         }


         service.execute(() -> {

            int[] producerSequence = new int[PRODUCERS];

            try {
               ConnectionFactory factory = CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616");
               Connection connection = factory.createConnection();
               Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
               MessageConsumer consumer = session.createConsumer(session.createQueue(QUEUE_NAME));
               connection.start();

               for (int i = 0; i < NUMBER_OF_MESSAGES * PRODUCERS; i++) {
                  TextMessage message = (TextMessage) consumer.receive(60_000);
                  assertNotNull(message);
                  int producerID = message.getIntProperty("producerID");
                  int sequence = message.getIntProperty("sequence");
                  logger.debug("Received message {} from producer {}", sequence, producerID);
                  assertEquals(producerSequence[producerID], sequence);
                  producerSequence[producerID]++;
                  assertEquals(createLMBody(MESSAGE_SIZE, producerID, sequence), message.getText());
                  semaphore.release();
               }

            } catch (Throwable e) {
               errors.incrementAndGet();
               logger.warn(e.getMessage(), e);
            } finally {
               running.set(false);
               latch.countDown();
            }
         });

         service.execute(() -> {
            // this is just creating enough loading somewhere else to compete for resources
            ConnectionFactory factory = CFUtil.createConnectionFactory("core", "tcp://localhost:61616");
            try {
               Connection connection = factory.createConnection();
               Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
               MessageProducer producer = session.createProducer(session.createQueue("fastQueue"));
               MessageConsumer consumer = session.createConsumer(session.createQueue("fastQueue"));
               connection.start();
               long msg = 0;
               char[] msgStr = new char[1024];
               String buffer = new String(msgStr);
               Arrays.fill(msgStr, 'a');
               while (running.get()) {
                  TextMessage message = session.createTextMessage(buffer);
                  producer.send(message);
                  if (++msg % 10000L == 0L) {
                     logger.debug("Sent and receive {} fast messages", msg);
                  }

                  if (msg > 5000L) {
                     message = (TextMessage) consumer.receive(10000);
                     assertNotNull(message);
                  }

                  if (msg % 100L == 0L) {
                     session.commit();
                  }
               }
               session.commit();
               producer.close();
               consumer.close();
               session.close();
               connection.close();
            } catch (Exception e) {
               errors.incrementAndGet();
               e.printStackTrace();
               logger.warn(e.getMessage(), e);
            } finally {
               latch.countDown();
               running.set(false);
            }
         });


         assertTrue(latch.await(TEST_TIMEOUT_MINUTES, TimeUnit.MINUTES));

         assertEquals(0, errors.get());

         System.exit(OK);
      } catch (Throwable e) {
         e.printStackTrace();
         System.exit(-1);
      }
   }

   @TestTemplate
   public void testValidateLeaks() throws Exception {
      // I am using a spawn for the test client, as this test will need a big VM for the client.
      // so I need control over the memory size for the VM.
      Process process = SpawnedVMSupport.spawnVM(OWLeakTest.class.getName(), new String[]{"-Xmx3G"}, "" + PRODUCERS, "" + NUMBER_OF_MESSAGES, "" + MESSAGE_SIZE, protocol);
      logger.debug("Process PID::{}", process.pid());
      assertTrue(process.waitFor(TEST_TIMEOUT_MINUTES, TimeUnit.MINUTES));
      assertEquals(OK, process.exitValue());

   }

}
