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

import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import java.lang.invoke.MethodHandles;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.config.CoreAddressConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.DatabaseStorageMessageReader;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.db.dbstorage.statements.AbstractStatementTest;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisabledIf("isNoDatabaseSelected")
@ExtendWith(ParameterizedTestExtension.class)
public class FullMessageIntegrationTest extends AbstractStatementTest {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final int MESSAGE_SIZE = 1024;
   private static final int LARGE_MESSAGE_SIZE = 300 * 1024;
   private static final int TOTAL_MESSAGES = 300;
   private static final int NON_TX_MESSAGES = 100;
   private static final int TX_COMMIT_INTERVAL = 10;

   @BeforeEach
   @Override
   public void setupTest() throws Exception {
      super.setupTest();
      configuration.addAddressConfiguration(new CoreAddressConfiguration()
         .setName("DLQ")
         .addRoutingType(RoutingType.ANYCAST)
         .addQueueConfiguration(QueueConfiguration.of("DLQ").setRoutingType(RoutingType.ANYCAST)));
      configuration.addAddressConfiguration(new CoreAddressConfiguration()
         .setName("ExpiryQueue")
         .addRoutingType(RoutingType.ANYCAST)
         .addQueueConfiguration(QueueConfiguration.of("ExpiryQueue").setRoutingType(RoutingType.ANYCAST)));
   }

   // Regular size - Core

   @TestTemplate
   public void testCoreNonPaged() throws Exception {
      testMessages("CORE", false, MESSAGE_SIZE, false);
   }

   @TestTemplate
   public void testCoreNonPagedRestart() throws Exception {
      testMessages("CORE", false, MESSAGE_SIZE, true);
   }

   @TestTemplate
   public void testCorePaged() throws Exception {
      testMessages("CORE", true, MESSAGE_SIZE, false);
   }

   @TestTemplate
   public void testCorePagedRestart() throws Exception {
      testMessages("CORE", true, MESSAGE_SIZE, true);
   }

   // Regular size - AMQP

   @TestTemplate
   public void testAMQPNonPaged() throws Exception {
      testMessages("AMQP", false, MESSAGE_SIZE, false);
   }

   @TestTemplate
   public void testAMQPNonPagedRestart() throws Exception {
      testMessages("AMQP", false, MESSAGE_SIZE, true);
   }

   @TestTemplate
   public void testAMQPPaged() throws Exception {
      testMessages("AMQP", true, MESSAGE_SIZE, false);
   }

   @TestTemplate
   public void testAMQPPagedRestart() throws Exception {
      testMessages("AMQP", true, MESSAGE_SIZE, true);
   }

   // Regular size - OpenWire

   @TestTemplate
   public void testOpenWireNonPaged() throws Exception {
      testMessages("OPENWIRE", false, MESSAGE_SIZE, false);
   }

   @TestTemplate
   public void testOpenWireNonPagedRestart() throws Exception {
      testMessages("OPENWIRE", false, MESSAGE_SIZE, true);
   }

   @TestTemplate
   public void testOpenWirePaged() throws Exception {
      testMessages("OPENWIRE", true, MESSAGE_SIZE, false);
   }

   @TestTemplate
   public void testOpenWirePagedRestart() throws Exception {
      testMessages("OPENWIRE", true, MESSAGE_SIZE, true);
   }

   // Large message size - Core

   @TestTemplate
   public void testCoreLargeNonPaged() throws Exception {
      testMessages("CORE", false, LARGE_MESSAGE_SIZE, false);
   }

   @TestTemplate
   public void testCoreLargeNonPagedRestart() throws Exception {
      testMessages("CORE", false, LARGE_MESSAGE_SIZE, true);
   }

   @TestTemplate
   public void testCoreLargePaged() throws Exception {
      testMessages("CORE", true, LARGE_MESSAGE_SIZE, false);
   }

   @TestTemplate
   public void testCoreLargePagedRestart() throws Exception {
      testMessages("CORE", true, LARGE_MESSAGE_SIZE, true);
   }

   @TestTemplate
   public void testAMQPLargeNonPaged() throws Exception {
      testMessages("AMQP", false, LARGE_MESSAGE_SIZE, false);
   }

   @TestTemplate
   public void testAMQPLargeNonPagedRestart() throws Exception {
      testMessages("AMQP", false, LARGE_MESSAGE_SIZE, true);
   }

   @TestTemplate
   public void testAMQPLargePaged() throws Exception {
      testMessages("AMQP", true, LARGE_MESSAGE_SIZE, false);
   }

   @TestTemplate
   public void testAMQPLargePagedRestart() throws Exception {
      testMessages("AMQP", true, LARGE_MESSAGE_SIZE, true);
   }

   @TestTemplate
   public void testOpenWireLargeNonPaged() throws Exception {
      testMessages("OPENWIRE", false, LARGE_MESSAGE_SIZE, false);
   }

   @TestTemplate
   public void testOpenWireLargeNonPagedRestart() throws Exception {
      testMessages("OPENWIRE", false, LARGE_MESSAGE_SIZE, true);
   }

   @TestTemplate
   public void testOpenWireLargePaged() throws Exception {
      testMessages("OPENWIRE", true, LARGE_MESSAGE_SIZE, false);
   }

   @TestTemplate
   public void testOpenWireLargePagedRestart() throws Exception {
      testMessages("OPENWIRE", true, LARGE_MESSAGE_SIZE, true);
   }

   @TestTemplate
   public void testSingleLargeMessageCore() throws Exception {
      testSingleLargeMessage("CORE");
   }

   @TestTemplate
   public void testSingleLargeMessageAMQP() throws Exception {
      testSingleLargeMessage("AMQP");
   }

   @TestTemplate
   public void testSingleLargeMessageOpenWire() throws Exception {
      testSingleLargeMessage("OPENWIRE");
   }

   private void testSingleLargeMessage(String protocol) throws Exception {
      String queueName = "singleLargeMsg" + RandomUtil.randomUUIDString();

      String body = "a".repeat(LARGE_MESSAGE_SIZE);

      configuration.addAddressConfiguration(new CoreAddressConfiguration()
         .setName(queueName)
         .addRoutingType(RoutingType.ANYCAST)
         .addQueueConfiguration(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST)));

      ActiveMQServer server = createServer(true, configuration);
      server.start();

      ConnectionFactory factory = CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616");

      try (javax.jms.Connection connection = factory.createConnection()) {
         try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
            MessageProducer producer = session.createProducer(session.createQueue(queueName));
            producer.send(session.createTextMessage(body));
         }
      }

      checkMessageCounts(1, true);


      server.stop();
      server.start();

      try (javax.jms.Connection connection = factory.createConnection()) {
         connection.start();
         try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
            MessageConsumer consumer = session.createConsumer(session.createQueue(queueName));
            TextMessage received = (TextMessage) consumer.receive(10_000);
            assertNotNull(received);
            assertEquals(LARGE_MESSAGE_SIZE, received.getText().length());
         }
      }

      checkMessageCounts(0, true);
      server.stop();
   }

   private void testMessages(String protocol, boolean paged, int messageSize, boolean restart) throws Exception {
      String queueName = "testMsg" + RandomUtil.randomUUIDString();

      String body = "a".repeat(messageSize);

      if (paged) {
         configuration.getAddressSettings().clear();
         configuration.addAddressSetting("#", new AddressSettings()
            .setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE)
            .setMaxSizeMessages(100));
      }

      configuration.addAddressConfiguration(new CoreAddressConfiguration()
         .setName(queueName)
         .addRoutingType(RoutingType.ANYCAST)
         .addQueueConfiguration(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST)));

      ActiveMQServer server = createServer(true, configuration);
      server.start();

      ConnectionFactory factory = CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616");

      try (javax.jms.Connection connection = factory.createConnection()) {
         try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
            MessageProducer producer = session.createProducer(session.createQueue(queueName));
            for (int i = 0; i < NON_TX_MESSAGES; i++) {
               producer.send(session.createTextMessage(body));
               if ((i + 1) % 10 == 0) {
                  logger.debug("Sent {} non-tx messages with {}", i + 1, protocol);
               }
            }
         }

         try (Session session = connection.createSession(true, Session.SESSION_TRANSACTED)) {
            MessageProducer producer = session.createProducer(session.createQueue(queueName));
            int txMessages = TOTAL_MESSAGES - NON_TX_MESSAGES;
            for (int i = 0; i < txMessages; i++) {
               producer.send(session.createTextMessage(body));
               if ((i + 1) % TX_COMMIT_INTERVAL == 0) {
                  session.commit();
                  logger.debug("Committed batch {} with {}", (i + 1) / TX_COMMIT_INTERVAL, protocol);
               }
            }
            session.commit();
         }
      }

      Queue queue = server.locateQueue(queueName);
      assertNotNull(queue);
      DatabaseStorageMessageReader storageMessageReader = (DatabaseStorageMessageReader) queue.getStorageMessageReader();
      if (paged) {
         Wait.assertTrue(() -> storageMessageReader.getPagedMessages() > 0, 5000, 100);
      } else {
         assertEquals(0, storageMessageReader.getPagedMessages());
      }

      if (restart) {
         server.stop();
         server.start();
      }

      try (javax.jms.Connection connection = factory.createConnection()) {
         connection.start();
         try (Session session = connection.createSession(true, Session.SESSION_TRANSACTED)) {
            MessageConsumer consumer = session.createConsumer(session.createQueue(queueName));
            for (int i = 0; i < TOTAL_MESSAGES; i++) {
               TextMessage received = (TextMessage) consumer.receive(10_000);
               assertNotNull(received, "Missing message at index " + i);
               assertEquals(messageSize, received.getText().length());
               assertEquals(body, received.getText());
               if ((i + 1) % TX_COMMIT_INTERVAL == 0) {
                  session.commit();
               }
            }
            session.commit();
         }
      }

      checkMessageCounts(0, true);
      server.stop();
   }
}
