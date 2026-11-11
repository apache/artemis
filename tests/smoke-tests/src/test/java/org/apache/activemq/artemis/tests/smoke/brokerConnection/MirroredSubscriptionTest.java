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
package org.apache.activemq.artemis.tests.smoke.brokerConnection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;
import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.management.SimpleManagement;
import org.apache.activemq.artemis.tests.smoke.common.SmokeTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.util.ServerUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MirroredSubscriptionTest extends SmokeTestBase {

   public static final String SERVER_NAME_A = "mirrored-subscriptions/broker1";
   public static final String SERVER_NAME_B = "mirrored-subscriptions/broker2";
   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @BeforeAll
   public static void createServers() throws Exception {

      File server0Location = getFileServerLocation(SERVER_NAME_A);
      File server1Location = getFileServerLocation(SERVER_NAME_B);
      deleteDirectory(server1Location);
      deleteDirectory(server0Location);

      {
         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer.setAllowAnonymous(true).setRole("amq").setUser("admin").setPassword("admin").setNoWeb(true).setConfiguration("./src/main/resources/servers/mirrored-subscriptions/broker1").setArtemisInstance(server0Location);
         cliCreateServer.createServer();
      }

      {
         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer.setAllowAnonymous(true).setRole("amq").setUser("admin").setPassword("admin").setNoWeb(true).setConfiguration("./src/main/resources/servers/mirrored-subscriptions/broker2").setArtemisInstance(server1Location);
         cliCreateServer.createServer();
      }
   }


   Process processB;
   Process processA;

   @BeforeEach
   public void beforeClass() throws Exception {

      startServers();
   }

   private void startServers() throws Exception {
      processB = startServer(SERVER_NAME_B, 1, 0);
      processA = startServer(SERVER_NAME_A, 0, 0);

      ServerUtil.waitForServerToStart(1, "B", "B", 30000);
      ServerUtil.waitForServerToStart(0, "A", "A", 30000);
   }

   @Test
   public void testConsumeAll() throws Throwable {
      int COMMIT_INTERVAL = 100;
      long NUMBER_OF_MESSAGES = 300;
      int CLIENTS = 5;
      String mainURI = "tcp://localhost:61616";
      String secondURI = "tcp://localhost:61617";

      SimpleManagement mainManagement = new SimpleManagement(mainURI, null, null);

      String topicName = "myTopic";

      ConnectionFactory cf = CFUtil.createConnectionFactory("amqp", mainURI);

      for (int i = 0; i < CLIENTS; i++) {
         try (Connection connection = cf.createConnection()) {
            connection.setClientID("client" + i);
            Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
            Topic topic = session.createTopic(topicName);
            session.createDurableSubscriber(topic, "subscription" + i);
         }
      }

      try (Connection connection = cf.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         Topic topic = session.createTopic(topicName);
         MessageProducer producer = session.createProducer(topic);
         for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            producer.send(session.createTextMessage("hello " + i));
            if (i % COMMIT_INTERVAL == 0) {
               session.commit();
            }
         }
         session.commit();
      }

      Map<String, Long> result = mainManagement.getQueueCounts(100);
      result.entrySet().forEach(entry -> logger.info("Queue {} = {}", entry.getKey(), entry.getValue()));

      checkMessages(NUMBER_OF_MESSAGES, CLIENTS, mainURI, secondURI);

      ExecutorService executorService = Executors.newFixedThreadPool(CLIENTS);
      runAfter(executorService::shutdownNow);

      CountDownLatch done = new CountDownLatch(CLIENTS);
      AtomicInteger errors = new AtomicInteger(0);

      for (int i = 0; i < CLIENTS; i++) {
         final int clientID = i;
         CountDownLatch threadDone = new CountDownLatch(1);
         executorService.execute(() -> {
            try (Connection connection = cf.createConnection()) {
               connection.setClientID("client" + clientID);
               connection.start();
               Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
               Topic topic = session.createTopic(topicName);
               TopicSubscriber subscriber = session.createDurableSubscriber(topic, "subscription" + clientID);
               for (int messageI = 0; messageI < NUMBER_OF_MESSAGES; messageI++) {
                  TextMessage message = (TextMessage) subscriber.receive(5000);
                  assertNotNull(message);
                  if (messageI % COMMIT_INTERVAL == 0) {
                     session.commit();
                     logger.info("Received {} messages on receiver {}", messageI, clientID);
                  }
               }
               session.commit();
            } catch (Throwable e) {
               logger.warn(e.getMessage(), e);
               errors.incrementAndGet();
            } finally {
               done.countDown();
               threadDone.countDown();
            }
         });

         if (clientID == 0) {
            // The first execution will block until finished, we will then kill all the servers and make sure
            // all the counters are preserved.
            assertTrue(threadDone.await(300, TimeUnit.SECONDS));
            processA.destroyForcibly();
            processB.destroyForcibly();
            Wait.assertFalse(processA::isAlive);
            Wait.assertFalse(processB::isAlive);
            startServers();
            Wait.assertEquals(0, () -> getMessageCount(mainURI, "client0.subscription0"));
            Wait.assertEquals(0, () -> getMessageCount(secondURI, "client0.subscription0"));
            for (int checkID = 1; checkID < CLIENTS; checkID++) {
               int checkFinal = checkID;
               Wait.assertEquals(NUMBER_OF_MESSAGES, () -> getMessageCount(mainURI, "client" + checkFinal + ".subscription" + checkFinal), 2000, 100);
               Wait.assertEquals(NUMBER_OF_MESSAGES, () -> getMessageCount(secondURI, "client" + checkFinal + ".subscription" + checkFinal), 2000, 100);
            }
         }
      }

      assertTrue(done.await(300, TimeUnit.SECONDS));
      assertEquals(0, errors.get());
      checkMessages(0, CLIENTS, mainURI, secondURI);
   }

   private void checkMessages(long NUMBER_OF_MESSAGES, int CLIENTS, String mainURI, String secondURI) throws Exception {
      for (int i = 0; i < CLIENTS; i++) {
         final int clientID = i;
         Wait.assertEquals(NUMBER_OF_MESSAGES, () -> getMessageCount(mainURI, "client" + clientID + ".subscription" + clientID));
         Wait.assertEquals(NUMBER_OF_MESSAGES, () -> getMessageCount(secondURI, "client" + clientID + ".subscription" + clientID));
      }
   }
}
