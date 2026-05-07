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

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import org.apache.activemq.artemis.api.core.management.SimpleManagement;
import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.apache.activemq.artemis.tests.soak.SoakTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.FileUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class AMQPGlobalMaxTest extends SoakTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final String QUEUE_NAME = "simpleTest";
   public static final String SERVER_NAME = "global-max-test";
   private static File serverLocation;

   private int lastMessageSent;

   volatile String memoryUsed;
   volatile long highestMemoryUsed;
   volatile boolean hasOME = false;

   private Process server;

   private long avgSizeFirstEstimate;

   public static void createServers(long globalMaxSize, String vmSize) throws Exception {
      serverLocation = getFileServerLocation(SERVER_NAME);
      deleteDirectory(serverLocation);

      HelperCreate cliCreateServer = helperCreate();
      cliCreateServer.setUseAIO(false).setAllowAnonymous(true).setNoWeb(true).setArtemisInstance(serverLocation);
      // to speedup producers
      cliCreateServer.addArgs("--no-fsync");
      cliCreateServer.addArgs("--queues", QUEUE_NAME);
      cliCreateServer.addArgs("--global-max-size", String.valueOf(globalMaxSize));
      // limiting memory to make the test more predictable
      cliCreateServer.addArgs("--java-memory", vmSize);
      cliCreateServer.createServer();

      // Setting deduplication off, to not cause noise on the memory usage calculations
      FileUtil.findReplace(new File(serverLocation, "/etc/artemis.profile"), "-Xms512M", "-Xms10M -verbose:gc -XX:-UseStringDeduplication");
   }

   @Test
   public void testValidateMemoryEstimateLargeAMQP() throws Exception {
      validateOME("AMQP", (s, i) -> {
         try {
            Message m = s.createTextMessage("a".repeat(200 * 1024) + RandomUtil.randomUUIDString());
            for (int propCount = 0; propCount < 5; propCount++) {
               // making each string unique to avoid string deduplication from Garbage Collection
               m.setStringProperty("prop" + propCount, "a".repeat(10 * 1024) + RandomUtil.randomUUIDString());
            }
            return m;
         } catch (Exception e) {
            fail(e.getMessage());
            return null;
         }
      }, "30M", 1, 100);
   }

   private void validateOME(String protocol, BiFunction<Session, Integer, Message> messageCreator, String vmSize, int commitInterval, int printInterval) throws Exception {
      // making the broker OME on purpose
      // this is to help us calculate the size of each message
      // for this reason the global-max-size is set really high on this test
      createServers(1024 * 1024 * 1024, vmSize);
      startServerWithLog();

      executeTest(protocol, messageCreator, (a, b) -> { }, commitInterval, printInterval, 20_000_000, TimeUnit.MINUTES.toMillis(10), true);
   }

   @Test
   public void testValidateMemoryEstimateAMQP() throws Exception {
      validateOME("AMQP", (s, i) -> {
         try {
            Message m = s.createMessage();
            for (int propCount = 0; propCount < 10; propCount++) {
               // making each string unique to avoid string deduplication from Garbage Collection
               m.setStringProperty("string" + propCount, RandomUtil.randomUUIDString());
               m.setLongProperty("myLong" + propCount, RandomUtil.randomLong());
            }
            return m;
         } catch (Throwable e) {
            Assertions.fail(e.getMessage());
            return null;
         }
      },  "30M", 1000, 1000);
   }

   private void startServerWithLog() throws Exception {
      server = startServer(SERVER_NAME, 0, 5000, null, s -> {
         logger.debug("{}", s);
         if (s.contains("GC") && s.contains("->")) {
            AMQPGlobalMaxTest.this.memoryUsed = parseMemoryUsageFromGCLOG(s);
            long memoryUsedBytes = parseMemoryToBytes(memoryUsed);
            if (memoryUsedBytes > highestMemoryUsed) {
               highestMemoryUsed = memoryUsedBytes;
               logger.info("Currently using {} on the server", memoryUsed);
            }
         }

         // Stop Page, start page or anything important
         if (s.contains("INFO") || s.contains("WARN")) {
            logger.info("{}", s);
         }

         if (s.contains("OutOfMemoryError")) {
            logger.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> OME!!!");
            AMQPGlobalMaxTest.this.hasOME = true;
         }
      });
   }

   @Test
   public void testGlobalMax() throws Exception {
      createServers(100 * 1024 * 1024, "200M");
      startServerWithLog();

      executeTest("AMQP", (s, i) -> {
         try {
            Message message;
            if (i % 100 == 0) {
               // introduce a few large messages in the mix
               message = s.createTextMessage("a".repeat(101 * 1024) + RandomUtil.randomUUIDString());
               message.setStringProperty("i", "a".repeat(10 * 1024) + RandomUtil.randomUUIDString());
            } else {
               message = s.createMessage();
            }
            message.setIntProperty("i", i);
            // Making strings unique to avoid deduplication on GC
            message.setStringProperty("someString", "a".repeat(1024) + RandomUtil.randomUUIDString());
            return message;
         } catch (Throwable e) {
            Assertions.fail(e.getMessage());
            return null;
         }
      }, (i, m) -> {
         try {
            assertEquals(i, m.getIntProperty("i"));
         } catch (Throwable e) {
            Assertions.fail(e.getMessage());
         }
      }, 10_000, 10_000, 80_000, TimeUnit.MINUTES.toMillis(10), false);
   }

   private void executeTest(String protocol, BiFunction<Session, Integer, Message> messageCreator,
                            BiConsumer<Integer, Message> messageVerifier,
                            int commitInterval,
                            int printInterval,
                            int maxMessages,
                            long timeoutMilliseconds,
                            boolean expectOME) throws Exception {
      ExecutorService service = Executors.newFixedThreadPool(2);
      runAfter(service::shutdownNow);

      CountDownLatch latchDone = new CountDownLatch(1);
      AtomicInteger errors = new AtomicInteger(0);

      // use some management operation to make AMQPMessages to unmarshal their application properties
      service.execute(() -> {
         try (SimpleManagement simpleManagement = new SimpleManagement("tcp://localhost:61616", null, null)) {
            while (!latchDone.await(1, TimeUnit.SECONDS)) {
               // this filter will not remove any messages, but it will force the AMQPMessage to unmarshal the applicationProperties
               simpleManagement.removeMessagesOnQueue(QUEUE_NAME, "i=-1");
            }
         } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
         }
      });

      String initialMemory = this.memoryUsed;

      service.execute(() -> {
         try {
            theTest(protocol, messageCreator, messageVerifier, maxMessages, commitInterval, printInterval);
         } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
            errors.incrementAndGet();
         } finally {
            latchDone.countDown();
         }
      });

      Wait.waitFor(() -> latchDone.await(1, TimeUnit.SECONDS) || hasOME, timeoutMilliseconds, 100);

      if (errors.get() != 0) {
         logger.info("The client had an error, probably because the server is going slow as it's close to OME. We are assuming the expected OME from the test");
         hasOME = true; // we will assume the server was slow and the client failed badly
      }

      if (hasOME) {
         assertTrue(lastMessageSent > 0);
         long allocatedBytes = parseMemoryToBytes(memoryUsed);
         long initialBytes = parseMemoryToBytes(initialMemory);
         long finalMemory = allocatedBytes - initialBytes;
         long avgSizeUsed = finalMemory / lastMessageSent;
         if (!expectOME) {
            Assertions.fail("OME on broker. Current memory = " + memoryUsed + ", with " + lastMessageSent + " messages sent, possible AVG for each message is " + (finalMemory / lastMessageSent) + " while initial estimate advertised by AMQPMessage is " + avgSizeFirstEstimate);
         }
         assertTrue(avgSizeFirstEstimate >= avgSizeUsed, "Estimated message size is below actually used. First estimate = " + avgSizeFirstEstimate + " supposed to be >= " + avgSizeUsed);
         logger.info("Original AVG Size estimate: {}, actual AVG after OME: {}", avgSizeFirstEstimate, avgSizeUsed);

         server.destroyForcibly();
      } else {
         assertTrue(latchDone.await(1, TimeUnit.MINUTES));
         if (expectOME) {
            Assertions.fail("Broker was supposed to fail with OME on this test");
         }
      }
   }

   private void theTest(String protocol, BiFunction<Session, Integer, Message> messageCreator,
                        BiConsumer<Integer, Message> messageVerifier, int maxMessages, int commitInterval, int printInterval) throws Exception {

      logger.info("CommitInterval = {}", commitInterval, printInterval);
      assert printInterval % commitInterval == 0;

      ConnectionFactory factory = CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616");

      boolean firstTX = true;

      SimpleManagement simpleManagement = new SimpleManagement("tcp://localhost:61616", null, null);
      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(Session.SESSION_TRANSACTED);
         MessageProducer producer = session.createProducer(session.createQueue(QUEUE_NAME));

         for (int i = 0; i < maxMessages; i++) {
            Message message = messageCreator.apply(session, i);
            producer.send(message);
            lastMessageSent = i;

            if (firstTX) {
               // we measure one message in the queue to get how much we estimate for each message
               session.commit();
               Wait.assertEquals(1, () -> simpleManagement.getMessageCountOnAddress(QUEUE_NAME));
               long addressSize = simpleManagement.getAddressSize(QUEUE_NAME);
               this.avgSizeFirstEstimate = addressSize;
               logger.info("addressSize={}", addressSize);
               firstTX = false;
            }

            if ((i + 1) % commitInterval == 0) {
               if ((i + 1) % printInterval == 0) {
                  logger.info("sent {} out of {}", i, maxMessages);
               }
               session.commit();
            }
         }
         session.commit();
      }

      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
         MessageConsumer consumer = session.createConsumer(session.createQueue(QUEUE_NAME));
         connection.start();

         for (int i = 0; i < maxMessages; i++) {
            if (i % 1000 == 0) {
               logger.info("Received {}", i);
            }
            Message message = consumer.receive(5000);
            assertNotNull(message);
            messageVerifier.accept(i, message);
         }
         assertNull(consumer.receiveNoWait());
      }
   }

   /**
    * Parses GC log output to extract memory after GC.
    * Example: "GC(25) Pause Remark 15M->15M(32M) 2.351ms" returns "15M"
    *
    * @param gcLogLine the GC log line
    * @return the memory value after GC (e.g., "15M"), or null if not found
    */
   private static String parseMemoryUsageFromGCLOG(String gcLogLine) {
      int arrowIndex = gcLogLine.indexOf("->");
      if (arrowIndex == -1) {
         return null;
      }
      int startIndex = arrowIndex + 2;
      int endIndex = gcLogLine.indexOf('(', startIndex);
      if (endIndex == -1) {
         return null;
      }
      return gcLogLine.substring(startIndex, endIndex);
   }

   /**
    * Parses memory string (e.g., "256M", "15M", "512K", "2G") to bytes.
    *
    * @param memoryStr the memory string
    * @return the memory value in bytes
    */
   private static long parseMemoryToBytes(String memoryStr) {
      if (memoryStr == null || memoryStr.isEmpty()) {
         return 0;
      }

      char unit = memoryStr.charAt(memoryStr.length() - 1);
      long value = Long.parseLong(memoryStr.substring(0, memoryStr.length() - 1));

      switch (unit) {
         case 'K':
         case 'k':
            return value * 1024;
         case 'M':
         case 'm':
            return value * 1024 * 1024;
         case 'G':
         case 'g':
            return value * 1024 * 1024 * 1024;
         default:
            return value;
      }
   }

}
