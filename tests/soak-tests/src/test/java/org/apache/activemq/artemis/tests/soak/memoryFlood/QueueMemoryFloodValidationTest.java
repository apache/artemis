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

package org.apache.activemq.artemis.tests.soak.memoryFlood;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.io.File;
import java.lang.invoke.MethodHandles;

import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.api.core.management.SimpleManagement;
import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.apache.activemq.artemis.tests.soak.SoakTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.FileUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class QueueMemoryFloodValidationTest extends SoakTestBase {
   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final String QUEUE_NAME = "simpleTest";
   public static final String SERVER_NAME = "validate-iterations";
   private static File serverLocation;

   private static final long MESSAGE_COUNT = 10_000;
   private static final int MESSAGE_TO_REMOVE_START = 9_500;
   private static final int MESSAGE_TO_REMOVE_END = 9_600;
   private static final int MESSAGES_REMOVED = MESSAGE_TO_REMOVE_END - MESSAGE_TO_REMOVE_START + 1;

   private Process server;

   @BeforeAll
   public static void createServers() throws Exception {
      serverLocation = getFileServerLocation(SERVER_NAME);
      deleteDirectory(serverLocation);

      HelperCreate cliCreateServer = helperCreate();
      cliCreateServer.setUseAIO(false).setAllowAnonymous(true).setNoWeb(true).setArtemisInstance(serverLocation);
      // to speedup producers
      cliCreateServer.addArgs("--no-fsync");
      cliCreateServer.addArgs("--queues", QUEUE_NAME);
      // limiting memory to make the test more predictable
      cliCreateServer.addArgs("--java-memory", "512M");
      cliCreateServer.createServer();

      FileUtil.findReplace(new File(serverLocation, "/etc/broker.xml"), "<max-size-messages>-1</max-size-messages>", " <max-size-messages>1000</max-size-messages>");
   }


   /** It will call a few management operations making sure they will not flood the memory with the entire page dataset */
   @Test
   public void testPreventMemoryFlood() throws Exception {
      server = startServer(SERVER_NAME, 0, 5000);

      ConnectionFactory factory = CFUtil.createConnectionFactory("core", "tcp://localhost:61616");
      sendMessages(factory);

      SimpleManagement simpleManagement = new SimpleManagement("tcp://localhost:61616", null, null);
      simpleManagement.simpleManagementInt(ResourceNames.QUEUE + QUEUE_NAME, "changeMessagesPriority", "", (int)3);

      File logLocation = new File(serverLocation, "log/artemis.log");
      assertTrue(FileUtil.find(logLocation, l -> l.contains("AMQ224162")), "Expected AMQ224158 warning log message from ActiveMQServerLogger.preventQueueManagementToFloodMemory");

      Wait.assertEquals(MESSAGE_COUNT, () -> simpleManagement.getMessageCountOnQueue(QUEUE_NAME), 5000, 100);

      int removed = simpleManagement.simpleManagementInt(ResourceNames.QUEUE + QUEUE_NAME, "removeMessages", "i >= " + MESSAGE_TO_REMOVE_START + " AND i <= " + MESSAGE_TO_REMOVE_END);

      assertEquals(MESSAGES_REMOVED, removed);
      Wait.assertEquals(MESSAGE_COUNT - MESSAGES_REMOVED, () -> simpleManagement.getMessageCountOnQueue(QUEUE_NAME), 5000, 100);

      int messagesRemoved = simpleManagement.simpleManagementInt(ResourceNames.QUEUE + QUEUE_NAME, "removeAllMessages");
      assertEquals(MESSAGE_COUNT - MESSAGES_REMOVED, messagesRemoved);

      try (Connection connection = factory.createConnection()) {
         connection.start();
         Session session = connection.createSession(Session.SESSION_TRANSACTED);
         MessageConsumer consumer = session.createConsumer(session.createQueue(QUEUE_NAME));
         Message message = consumer.receiveNoWait();
         assertNull(message);
      }

      sendMessages(factory);

      removed = simpleManagement.simpleManagementInt(ResourceNames.QUEUE + QUEUE_NAME, "removeMessages", "i >= " + MESSAGE_TO_REMOVE_START + " AND i <= " + MESSAGE_TO_REMOVE_END);
      assertEquals(MESSAGES_REMOVED, removed);
      Wait.assertEquals(MESSAGE_COUNT - MESSAGES_REMOVED, () -> simpleManagement.getMessageCountOnQueue(QUEUE_NAME), 5000, 100);

      try (Connection connection = factory.createConnection()) {
         connection.start();
         Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
         MessageConsumer consumer = session.createConsumer(session.createQueue(QUEUE_NAME));
         for (int i = 0; i < MESSAGE_COUNT - MESSAGES_REMOVED; i++) {
            Message message = consumer.receive(5000);
            assertNotNull(message);
            assertTrue(message.getIntProperty("i") < MESSAGE_TO_REMOVE_START || message.getIntProperty("i") > MESSAGE_TO_REMOVE_END);
         }
      }

      Wait.assertEquals(0L, () -> simpleManagement.getMessageCountOnQueue(QUEUE_NAME), 5000, 100);

   }

   private static void sendMessages(ConnectionFactory factory) throws JMSException {
      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(Session.SESSION_TRANSACTED);
         MessageProducer producer = session.createProducer(session.createQueue(QUEUE_NAME));

         for (int i = 0; i < MESSAGE_COUNT; i++) {
            TextMessage message = session.createTextMessage(RandomUtil.randomAlphaNumericString(1024 * 40));
            message.setIntProperty("i", i);
            producer.send(message);

            if (i % 1000 == 0) {
               logger.info("sent {} out of {}", i, MESSAGE_COUNT);
               session.commit();
            }
         }
         session.commit();
      }
   }

}
