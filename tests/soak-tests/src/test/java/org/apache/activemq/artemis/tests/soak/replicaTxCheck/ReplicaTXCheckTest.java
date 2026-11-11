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

package org.apache.activemq.artemis.tests.soak.replicaTxCheck;

import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TransactionRolledBackException;
import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.HashSet;
import java.util.Set;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.tests.soak.SoakTestBase;
import org.apache.activemq.artemis.util.ServerUtil;
import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReplicaTXCheckTest extends SoakTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final String SERVER_NAME_0 = "replica-tx-check/backup-zero";
   public static final String SERVER_NAME_1 = "replica-tx-check/live-zero";
   public static final String SERVER_NAME_2 = "replica-tx-check/backup-one";
   public static final String SERVER_NAME_3 = "replica-tx-check/live-one";
   public static final String SERVER_NAME_4 = "replica-tx-check/backup-two";
   public static final String SERVER_NAME_5 = "replica-tx-check/live-two";

   private static void createServer(String name) throws Exception {
      File serverLocation = getFileServerLocation(name);
      deleteDirectory(serverLocation);

      HelperCreate cliCreateServer = helperCreate();
      cliCreateServer.setAllowAnonymous(true).setNoWeb(true).setArtemisInstance(serverLocation);
      cliCreateServer.setConfiguration("./src/main/resources/servers/" + name);
      cliCreateServer.createServer();
   }

   @BeforeAll
   public static void createServers() throws Exception {
      createServer(SERVER_NAME_0);
      createServer(SERVER_NAME_1);
      createServer(SERVER_NAME_2);
      createServer(SERVER_NAME_3);
      createServer(SERVER_NAME_4);
      createServer(SERVER_NAME_5);
   }

   private static Process server0;
   private static Process server1;
   private static Process server2;
   private static Process server3;
   private static Process server4;

   int NUMBER_OF_MESSAGES = 1000;
   int KILL_AT = 100;

   @BeforeEach
   public void before() throws Exception {
      cleanupData(SERVER_NAME_0);
      cleanupData(SERVER_NAME_1);
      cleanupData(SERVER_NAME_2);
      cleanupData(SERVER_NAME_3);
      cleanupData(SERVER_NAME_4);
      cleanupData(SERVER_NAME_5);
      disableCheckThread();

      server0 = startServer(SERVER_NAME_0, 0, 0);
      server1 = startServer(SERVER_NAME_1, 0, 0);
      assertTrue(ServerUtil.waitForServerToStartOnPort(61000, null, null, 15000));

      server2 = startServer(SERVER_NAME_2, 0, 0);
      server3 = startServer(SERVER_NAME_3, 0, 0);
      assertTrue(ServerUtil.waitForServerToStartOnPort(61001, null, null, 15000));

      server4 = startServer(SERVER_NAME_4, 0, 0);
      server4 = startServer(SERVER_NAME_5, 0, 0);
      assertTrue(ServerUtil.waitForServerToStartOnPort(61002, null, null, 15000));
   }

   @AfterEach
   @Override
   public void after() throws Exception {
      super.after();
   }

   @Test
   public void testTXCheckAMQP() throws Exception {
      testTXCheck("AMQP", true, true);
   }

   @Test
   public void testTXCheckCORE() throws Exception {
      testTXCheck("CORE", true, true);
   }

   // a second variation of the test will invert the servers used and use a hard kill (halt) instead of stop
   @Test
   public void testTXCheckAMQP_2() throws Exception {
      testTXCheck("AMQP", false, false);
   }

   // a second variation of the test will invert the servers used and use a hard kill (halt) instead of stop
   @Test
   public void testTXCheckCORE_2() throws Exception {
      testTXCheck("CORE", false, false);
   }

   /**
    * This test is using three pairs of servers. It will send messages to one pair, then it consumes from that pair and
    * sends to a second pair if killTarget==true the target pair is the one that's being killed, otherwise is the one
    * with the consumers if useStop==true then the server is stopped with a regular stop call, otherwise it's halted
    */
   void testTXCheck(String protocol, boolean killTarget, boolean useStop) throws Exception {

      ConnectionFactory pair0;
      ConnectionFactory pair1 = switch (protocol) {
         case "AMQP" -> {
            pair0 = new JmsConnectionFactory("failover:(amqp://localhost:61000,amqp://localhost:61100)");
            yield new JmsConnectionFactory("amqp://localhost:61001");
         }
         default -> {
            pair0 = new ActiveMQConnectionFactory("tcp://localhost:61000?ha=true&reconnectAttempts=-1");
            yield new ActiveMQConnectionFactory("tcp://localhost:61001");
         }
      };

      ConnectionFactory sourceCF;
      ConnectionFactory targetCF;


      if (killTarget) {
         sourceCF = pair1;
         targetCF = pair0;
      } else {
         sourceCF = pair0;
         targetCF = pair1;
      }

      try (Connection sourceConnetion = sourceCF.createConnection()) {
         Session session = sourceConnetion.createSession(true, Session.SESSION_TRANSACTED);
         Queue queue = session.createQueue("exampleQueue");
         MessageProducer producer = session.createProducer(queue);
         for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            TextMessage message = session.createTextMessage("hello " + i);
            message.setIntProperty("i", i);
            producer.send(message);
         }
         session.commit();
      }

      try (Connection sourceConnection = sourceCF.createConnection();
           Session sourceSession = sourceConnection.createSession(true, Session.SESSION_TRANSACTED);
           Connection targetConnection = targetCF.createConnection();
           Session targetSession = targetConnection.createSession(true, Session.SESSION_TRANSACTED)) {

         sourceConnection.start();
         targetConnection.start();
         MessageConsumer consumer = sourceSession.createConsumer(targetSession.createQueue("exampleQueue"));

         Topic topic = targetSession.createTopic("exampleTopic");
         MessageConsumer subscription = targetSession.createSharedDurableConsumer(topic, "durable-consumer");
         MessageProducer producer = targetSession.createProducer(topic);

         int i = 0;
         while (true) {
            try {
               TextMessage message = (TextMessage)consumer.receive(5000);
               i++;
               if (message == null) {
                  logger.info("repeating receive i={}", i);
                  i--;
                  continue;
               }
               producer.send(message);
               if (i % 10 == 0) {
                  logger.info("Commit {}", i);
                  targetSession.commit();
                  sourceSession.commit();
               }
               if (i == KILL_AT) {
                  if (useStop) {
                     stopServerWithFile(getServerLocation(SERVER_NAME_1));
                  } else {
                     server1.destroyForcibly();
                  }
               }
               if (message.getText().equals("hello " + (NUMBER_OF_MESSAGES - 1))) {
                  logger.info("got to the end");
                  break;
               }
            } catch (Exception e) {
               logger.warn(e.getMessage(), e);
               sourceSession.rollback();
               targetSession.rollback();
            }
         }

         Set<Integer> received = new HashSet<>();
         int rec = 0;
         for (long timeout = System.currentTimeMillis() + 60_000; System.currentTimeMillis() < timeout;) {
            rec = 0;
            while (true) {
               TextMessage message = (TextMessage) subscription.receive(100);
               if (message == null) {
                  logger.info("Received {} messages", rec);
                  break;
               }
               received.add(message.getIntProperty("i"));
               rec++;
            }
            try {
               targetSession.commit();
               break;
            } catch (TransactionRolledBackException ok) {
            }
         }

         for (i = 0; i < NUMBER_OF_MESSAGES; i++) {
            assertTrue(received.contains(i));
         }
         // we could receive duplicates, but not lose messages
         assertTrue(rec >= NUMBER_OF_MESSAGES);
      }
   }
}