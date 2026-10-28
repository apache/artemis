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
package org.apache.activemq.artemis.tests.db.dbstorage.integration;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;
import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientRequestor;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.management.ManagementHelper;
import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.cli.commands.ActionContext;
import org.apache.activemq.artemis.cli.commands.tools.xml.XmlDataExporter;
import org.apache.activemq.artemis.cli.commands.tools.xml.XmlDataImporter;
import org.apache.activemq.artemis.core.config.DivertConfiguration;
import org.apache.activemq.artemis.tests.db.common.Database;
import org.apache.activemq.artemis.tests.db.common.ParameterDBTestBase;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.util.ServerUtil;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(ParameterizedTestExtension.class)
public class XmlExportImportTest extends ParameterDBTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final int NUM_MESSAGES = 100;

   private static final String QUEUE_NAME = "TEST_QUEUE";
   private static final String TOPIC_NAME = "TEST_TOPIC";
   private static final String SUB1_NAME = "sub1";
   private static final String SUB2_NAME = "sub2";
   private static final String CLIENT_ID = "exportTestClient";
   private static final String DIVERT_NAME = "testDivert";
   private static final String DIVERT_ADDRESS = "DIVERT_SOURCE";
   private static final String DIVERT_FORWARDING = "DIVERT_TARGET";

   @Parameters(name = "db={0}")
   public static Collection<Object[]> parameters() {
      List<Database> dbList = Database.selectedList();
      dbList.remove(Database.JOURNAL);
      return convertParameters(dbList);
   }

   private String getServerName() {
      return "new-" + database.getName();
   }

   @TestTemplate
   public void testExportImport() throws Exception {
      String serverName = getServerName();

      cleanupData(serverName);

      Process serverProcess = startServer(serverName, 0, 60_000);

      try {
         ConnectionFactory factory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616");

         // Phase 1: Create destinations, send messages, create divert
         try (Connection connection = factory.createConnection()) {
            connection.setClientID(CLIENT_ID);
            Session session = connection.createSession(true, Session.SESSION_TRANSACTED);

            // Create durable subscriptions before sending topic messages
            Topic topic = session.createTopic(TOPIC_NAME);
            TopicSubscriber sub1 = session.createDurableSubscriber(topic, SUB1_NAME);
            TopicSubscriber sub2 = session.createDurableSubscriber(topic, SUB2_NAME);

            // Send 100 messages to JMS queue
            MessageProducer queueProducer = session.createProducer(session.createQueue(QUEUE_NAME));
            for (int i = 0; i < NUM_MESSAGES; i++) {
               queueProducer.send(session.createTextMessage("queue-msg-" + i));
            }
            session.commit();
            logger.debug("Sent {} messages to queue {}", NUM_MESSAGES, QUEUE_NAME);

            // Send 100 messages to topic (each goes to both subscriptions)
            MessageProducer topicProducer = session.createProducer(topic);
            for (int i = 0; i < NUM_MESSAGES; i++) {
               topicProducer.send(session.createTextMessage("topic-msg-" + i));
            }
            session.commit();
            logger.debug("Sent {} messages to topic {}", NUM_MESSAGES, TOPIC_NAME);

            sub1.close();
            sub2.close();
         }

         // Create divert via management
         createDivert();
         ServerUtil.killServer(serverProcess);

         // Phase 2: Export data from the database
         XmlDataExporter xmlDataExporter = new XmlDataExporter();
         xmlDataExporter.setHomeValues(null, new File(getServerLocation(serverName)), null);
         File exportFile = new File(getServerLocation(serverName), "export.xml");
         xmlDataExporter.setOutput(exportFile);
         xmlDataExporter.setIgnoreLock(true);
         xmlDataExporter.execute(new ActionContext());
         assertNull(xmlDataExporter.getLastError(), "Export should not have errors");
         assertTrue(exportFile.exists(), "Export file should exist");
         logger.debug("Exported data to {}", exportFile.getAbsolutePath());

         // Phase 3: Drop the database
         if (database == Database.HSQL) {
            cleanupData(serverName);
         } else {
            dropDatabase();
         }
         logger.debug("Dropped database");

         // Phase 4: Restart server (creates fresh empty schema)
         serverProcess = startServer(serverName, 0, 60_000);

         // Phase 5: Import data
         XmlDataImporter dataImporter = new XmlDataImporter();
         dataImporter.setHomeValues(null, new File(getServerLocation(serverName)), null);
         dataImporter.input = exportFile;
         dataImporter.execute(new ActionContext());
         logger.debug("Imported data from {}", exportFile.getAbsolutePath());

         // Phase 6: Verify all data

         // Consume messages from queue
         try (Connection connection = factory.createConnection()) {
            connection.start();
            Session session = connection.createSession(true, Session.SESSION_TRANSACTED);

            MessageConsumer queueConsumer = session.createConsumer(session.createQueue(QUEUE_NAME));
            for (int i = 0; i < NUM_MESSAGES; i++) {
               TextMessage msg = (TextMessage) queueConsumer.receive(5000);
               assertNotNull(msg, "Expected queue message " + i);
            }
            assertNull(queueConsumer.receiveNoWait(), "Queue should be empty after consuming all messages");
            session.commit();
            logger.debug("Consumed {} messages from queue {}", NUM_MESSAGES, QUEUE_NAME);
         }

         // Consume messages from topic subscriptions
         try (Connection connection = factory.createConnection()) {
            connection.setClientID(CLIENT_ID);
            connection.start();
            Session session = connection.createSession(true, Session.SESSION_TRANSACTED);

            Topic topic = session.createTopic(TOPIC_NAME);

            TopicSubscriber sub1 = session.createDurableSubscriber(topic, SUB1_NAME);
            for (int i = 0; i < NUM_MESSAGES; i++) {
               TextMessage msg = (TextMessage) sub1.receive(5000);
               assertNotNull(msg, "Expected topic message on " + SUB1_NAME + " index " + i);
            }
            assertNull(sub1.receiveNoWait(), SUB1_NAME + " should be empty");

            TopicSubscriber sub2 = session.createDurableSubscriber(topic, SUB2_NAME);
            for (int i = 0; i < NUM_MESSAGES; i++) {
               TextMessage msg = (TextMessage) sub2.receive(5000);
               assertNotNull(msg, "Expected topic message on " + SUB2_NAME + " index " + i);
            }
            assertNull(sub2.receiveNoWait(), SUB2_NAME + " should be empty");

            session.commit();
            logger.debug("Consumed {} messages from each subscription", NUM_MESSAGES);
         }

         // Verify divert exists after import
         verifyDivertExists();

         stopServerWithFile(getServerLocation(serverName), serverProcess, 1, TimeUnit.MINUTES);
         serverProcess.waitFor(10, TimeUnit.SECONDS);
         assertFalse(serverProcess.isAlive());
         serverProcess = null;

      } finally {
         ServerUtil.killServer(serverProcess, true);
      }
   }

   private void createDivert() throws Exception {
      try (ServerLocator locator = ActiveMQClient.createServerLocator("tcp://localhost:61616");
           ClientSessionFactory sf = locator.createSessionFactory();
           ClientSession session = sf.createSession(false, true, true)) {

         DivertConfiguration divertConfig = new DivertConfiguration()
            .setName(DIVERT_NAME)
            .setRoutingName("testRouting")
            .setAddress(DIVERT_ADDRESS)
            .setForwardingAddress(DIVERT_FORWARDING)
            .setExclusive(false);

         try (ClientRequestor requestor = new ClientRequestor(session, "activemq.management")) {
            ClientMessage message = session.createMessage(false);
            ManagementHelper.putOperationInvocation(message, ResourceNames.BROKER, "createDivert", divertConfig.toJSON());
            session.start();
            ClientMessage reply = requestor.request(message);
            assertTrue(ManagementHelper.hasOperationSucceeded(reply), "Failed to create divert: " + ManagementHelper.getResult(reply, String.class));
            logger.debug("Created divert {}", DIVERT_NAME);
         }
      }
   }

   private void verifyDivertExists() throws Exception {
      try (ServerLocator locator = ActiveMQClient.createServerLocator("tcp://localhost:61616");
           ClientSessionFactory sf = locator.createSessionFactory();
           ClientSession session = sf.createSession(false, true, true)) {

         try (ClientRequestor requestor = new ClientRequestor(session, "activemq.management")) {
            ClientMessage message = session.createMessage(false);
            ManagementHelper.putOperationInvocation(message, ResourceNames.BROKER, "getDivertNames");
            session.start();
            ClientMessage reply = requestor.request(message);
            assertTrue(ManagementHelper.hasOperationSucceeded(reply), "getDivertNames should succeed");

            Object result = ManagementHelper.getResult(reply);
            assertNotNull(result, "getDivertNames result should not be null");

            Object[] divertNames;
            if (result instanceof Object[]) {
               divertNames = (Object[]) result;
            } else {
               divertNames = new Object[]{result};
            }

            boolean found = false;
            for (Object name : divertNames) {
               if (DIVERT_NAME.equals(name.toString())) {
                  found = true;
                  break;
               }
            }
            assertTrue(found, "Divert '" + DIVERT_NAME + "' should exist after import");
            logger.debug("Verified divert {} exists", DIVERT_NAME);
         }
      }
   }
}
