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

package org.apache.activemq.artemis.tests.soak.retention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl;
import org.apache.activemq.artemis.api.core.management.ObjectNameBuilder;
import org.apache.activemq.artemis.api.core.management.SimpleManagement;
import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.apache.activemq.artemis.tests.soak.SoakTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// The server used by this test has the journal retention configured.
// The server should not enter into a deadlock state just because retention is being used.
// The focus of this test is to make sure all messages are sent and received normally
public class LargeMessageRetentionTest extends SoakTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final String JMX_SERVER_HOSTNAME = "localhost";
   private static final int JMX_SERVER_PORT_0 = 1099;
   static String liveURI = "service:jmx:rmi:///jndi/rmi://" + JMX_SERVER_HOSTNAME + ":" + JMX_SERVER_PORT_0 + "/jmxrmi";
   static ObjectNameBuilder nameBuilder = ObjectNameBuilder.create(ActiveMQDefaultConfiguration.getDefaultJmxDomain(), "replay", true);

   public static final String SERVER_NAME_0 = "replay/large-message";

   @BeforeAll
   public static void createServers() throws Exception {
      {
         File serverLocation = getFileServerLocation(SERVER_NAME_0);
         deleteDirectory(serverLocation);

         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer.setRole("amq").setUser("artemis").setPassword("artemis").setAllowAnonymous(true).setNoWeb(false);
         cliCreateServer.setAllowAnonymous(true).setNoWeb(true).setArtemisInstance(serverLocation);
         cliCreateServer.setConfiguration("./src/main/resources/servers/replay/large-message");
         cliCreateServer.setArgs("--java-options", "-Djava.rmi.server.hostname=localhost", "--journal-retention", "1", "--queues", "RetentionTest", "--name", "large-message");
         cliCreateServer.createServer();
      }
   }

   @BeforeEach
   public void before() throws Exception {
      cleanupData(SERVER_NAME_0);
      startServer(SERVER_NAME_0, 0, 30000);
      disableCheckThread();
   }

   @Test
   public void testRetentionOpenWire() throws Throwable {
      testRetention("OPENWIRE", 100, 10, 200 * 1024, 10);
   }

   @Test
   public void testRetentionAMQP() throws Throwable {
      testRetention("AMQP", 100, 10, 50 * 1024, 10);
   }

   @Test
   public void testRetentionAMQPRealLarge() throws Throwable {
      testRetention("AMQP", 100, 10, 300 * 1024, 10);
   }

   // in this case messages are not really > min-large-message-size, but they will be converted because of the journal small buffer size
   @Test
   public void testRetentionCore() throws Throwable {
      testRetention("CORE", 100, 10, 50 * 1024, 10);
   }

   // in this case the messages are actually large
   @Test
   public void testRetentionCoreRealLarge() throws Throwable {
      testRetention("CORE", 100, 10, 300 * 1024, 10);
   }

   private void testRetention(String protocol, int NUMBER_OF_MESSAGES, int backlog, int bodySize, int producers) throws Throwable {
      assertEquals(0, NUMBER_OF_MESSAGES % producers); // checking that it is a multiple

      ActiveMQServerControl serverControl = getServerControl(liveURI, nameBuilder, 5000);

      final Semaphore consumerCredits = new Semaphore(-backlog);
      final String queueName = "RetentionTest";
      final AtomicInteger errors = new AtomicInteger(0);
      final CountDownLatch latchReceiver = new CountDownLatch(1);
      final CountDownLatch latchSender = new CountDownLatch(producers);

      String bufferStr = RandomUtil.randomUUIDString() + "*".repeat(bodySize);

      ConnectionFactory factory = CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616");

      ExecutorService executor = Executors.newFixedThreadPool(1 * producers);
      runAfter(executor::shutdownNow);

      executor.execute(() -> {
         try (Connection consumerConnection = factory.createConnection()) {
            Map<Integer, AtomicInteger> messageSequences = new HashMap<>();
            Session consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = consumerSession.createQueue(queueName);
            consumerConnection.start();
            MessageConsumer consumer = consumerSession.createConsumer(queue);
            for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
               logger.debug("Acquiring semop at {}", i);
               assertTrue(consumerCredits.tryAcquire(1, TimeUnit.MINUTES));
               TextMessage message = (TextMessage) consumer.receive(60_000);
               assertNotNull(message);
               int producerI = message.getIntProperty("producerI");
               AtomicInteger messageSequence = messageSequences.get(producerI);
               if (messageSequence == null) {
                  messageSequence = new AtomicInteger(0);
                  messageSequences.put(producerI, messageSequence);
               }
               assertEquals(messageSequence.getAndIncrement(), message.getIntProperty("messageI"));
               logger.info("Received message {}", i);
               assertEquals(bufferStr, message.getText());
            }
         } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
            errors.incrementAndGet();
         } finally {
            latchReceiver.countDown();
         }
      });

      for (int producerID = 0; producerID < producers; producerID++) {
         int theProducerID = producerID; // to be used within the executor's inner method
         executor.execute(() -> {
            try (Connection producerConnection = factory.createConnection()) {
               Session producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
               Queue queue = producerSession.createQueue(queueName);
               MessageProducer producer = producerSession.createProducer(queue);

               for (int messageI = 0; messageI < NUMBER_OF_MESSAGES / producers; messageI++) {
                  logger.info("Sending message {} from producerID", messageI, theProducerID);
                  Message message = producerSession.createTextMessage(bufferStr);
                  message.setIntProperty("messageI", messageI);
                  message.setIntProperty("producerI", theProducerID);
                  producer.send(message);
                  consumerCredits.release();
               }
            } catch (Throwable e) {
               logger.warn(e.getMessage(), e);
               errors.incrementAndGet();
            } finally {
               latchSender.countDown();
            }
         });

      }

      assertTrue(latchSender.await(10, TimeUnit.MINUTES));
      consumerCredits.release(backlog);
      assertTrue(latchReceiver.await(10, TimeUnit.MINUTES));
      assertEquals(0, errors.get());

      try (Connection consumerConnection = factory.createConnection()) {
         Map<Integer, AtomicInteger> messageSequences = new HashMap<>();
         Session consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queue = consumerSession.createQueue(queueName);
         consumerConnection.start();
         MessageConsumer consumer = consumerSession.createConsumer(queue);
         assertNull(consumer.receiveNoWait());

         serverControl.replay(queueName, queueName, "producerI=0 AND messageI>=0 AND messageI<10");
         SimpleManagement simpleManagement = new SimpleManagement("tcp://localhost:61616", null, null);
         Wait.assertEquals(10, () -> simpleManagement.getMessageCountOnQueue(queueName), 5000);

         Set<Integer> receivedMessages = new HashSet<>();

         for (int i = 0; i < 10; i++) {
            TextMessage message = (TextMessage) consumer.receive(300_000);
            assertNotNull(message);
            logger.info("Received replay message {}", i);
            assertEquals(0, message.getIntProperty("producerI"));
            receivedMessages.add(message.getIntProperty("messageI"));
            assertEquals(bufferStr, message.getText());
         }
         assertNull(consumer.receiveNoWait());

         assertEquals(10, receivedMessages.size());
         for (int i = 0; i < 10; i++) {
            assertTrue(receivedMessages.contains(i));
         }

      }
   }

}
