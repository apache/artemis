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
package org.apache.activemq.artemis.tests.db.dbstorage.xa;

import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.XAConnection;
import javax.jms.XAConnectionFactory;
import javax.jms.XASession;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.lang.invoke.MethodHandles;
import java.sql.Connection;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.config.CoreAddressConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.db.dbstorage.statements.AbstractStatementTest;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.artemis.database.sql.SQLProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledIf("isNoDatabaseSelected")
@ExtendWith(ParameterizedTestExtension.class)
public class DBXAIntegrationTest extends AbstractStatementTest {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @BeforeEach
   @Override
   public void setupTest() throws Exception {
      super.setupTest();
      configuration.addAddressConfiguration(new CoreAddressConfiguration().setName("DLQ").addRoutingType(RoutingType.ANYCAST).addQueueConfiguration(QueueConfiguration.of("DLQ").setRoutingType(RoutingType.ANYCAST)));
      configuration.addAddressConfiguration(new CoreAddressConfiguration().setName("ExpiryQueue").addRoutingType(RoutingType.ANYCAST).addQueueConfiguration(QueueConfiguration.of("ExpiryQueue").setRoutingType(RoutingType.ANYCAST)));
   }

   @Override
   protected boolean waitForBindings(ActiveMQServer server,
                                     String address,
                                     boolean local,
                                     int expectedBindingCount,
                                     int expectedConsumerCount,
                                     long timeout) throws Exception {
      return super.waitForBindings(server, address, local, expectedBindingCount, expectedConsumerCount, timeout);
   }

   @TestTemplate
   public void testSimpleXATXSend() throws Exception {
      testSimpleXATXSend(false);
   }

   @TestTemplate
   public void testSimpleXATXSendRestart() throws Exception {
      testSimpleXATXSend(true);
   }

   public void testSimpleXATXSend(boolean restart) throws Exception {

      final String QUEUE_NAME = "testXAQueue";
      configuration.addAddressConfiguration(new CoreAddressConfiguration().setName(QUEUE_NAME).addRoutingType(RoutingType.ANYCAST).addQueueConfiguration(QueueConfiguration.of(QUEUE_NAME).setRoutingType(RoutingType.ANYCAST)));
      ActiveMQServer server = createServer(true, configuration);
      server.start();

      runAfter(server::stop);

      final int NUM_MESSAGES = 10;

      ConnectionFactory factory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616");
      SQLProvider sqlProvider = storageConfiguration.getDatabaseProvider().getSqlProvider();

      Xid xid = newXID();

      for (int repeat = 0; repeat < 2; repeat++) {
         // Send messages within an XA transaction and prepare
         try (XAConnection xaConnection = ((XAConnectionFactory) factory).createXAConnection()) {
            XASession xaSession = xaConnection.createXASession();
            javax.jms.Queue queue = xaSession.createQueue(QUEUE_NAME);
            MessageProducer producer = xaSession.createProducer(queue);

            xaSession.getXAResource().start(xid, XAResource.TMNOFLAGS);
            for (int i = 0; i < NUM_MESSAGES; i++) {
               TextMessage message = xaSession.createTextMessage("message-" + i);
               message.setStringProperty("_AMQ_DUPL_ID", "DUP-" + i);
               producer.send(message);
            }
            xaSession.getXAResource().end(xid, XAResource.TMSUCCESS);
            xaSession.getXAResource().prepare(xid);
         }

         if (restart) {
            server.stop();
            server.start();
         }

         if (repeat == 0) {
            try (XAConnection xaConnection = ((XAConnectionFactory) factory).createXAConnection()) {
               XASession xaSession = xaConnection.createXASession();
               xaSession.getXAResource().rollback(xid);
            }
            assertTrue(server.getStorageManager().waitOnOperations(5000));
            try (Connection connection = storageConfiguration.getDatabaseProvider().getConnection()) {
               Wait.assertEquals(0, () -> selectCount(connection, sqlProvider.getPrepTx()), 5000, 100);
               Wait.assertEquals(0, () -> selectNumber(connection, "SELECT COUNT(*) FROM " + sqlProvider.getMessages()), 5000, 100);
               Wait.assertEquals(0, () -> selectNumber(connection, "SELECT COUNT(*) FROM " + sqlProvider.getRefs()), 5000, 100);
               Wait.assertEquals(0, () -> selectNumber(connection, "SELECT COUNT(*) FROM " + sqlProvider.getBrokerData()), 5000, 100);
            }
         }
      }

      // Verify prepare record and messages with TX_ID set
      try (Connection connection = storageConfiguration.getDatabaseProvider().getConnection()) {
         Wait.assertEquals(1, () -> selectCount(connection, sqlProvider.getPrepTx()), 5000, 100);
         Wait.assertEquals(NUM_MESSAGES, () -> selectNumber(connection, "SELECT COUNT(*) FROM " + sqlProvider.getMessages() + " WHERE TX_ID IS NOT NULL"), 5000, 100);
      }

      // Verify messages are not available for consumption before commit
      try (javax.jms.Connection connection = factory.createConnection()) {
         connection.start();
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         javax.jms.Queue queue = session.createQueue(QUEUE_NAME);
         MessageConsumer consumer = session.createConsumer(queue);
         assertNull(consumer.receiveNoWait());
      }

      logger.info("Going to commit......................................................");

      // Commit the XA transaction
      try (XAConnection xaConnection = ((XAConnectionFactory) factory).createXAConnection()) {
         XASession xaSession = xaConnection.createXASession();
         xaSession.getXAResource().commit(xid, false);
      }

      // Verify TX_ID is now null (messages committed) and prepare record removed
      try (Connection connection = storageConfiguration.getDatabaseProvider().getConnection()) {
         Wait.assertEquals(0, () -> selectCount(connection, sqlProvider.getPrepTx()), 5000, 100);
         Wait.assertEquals(NUM_MESSAGES, () -> selectNumber(connection, "SELECT COUNT(*) FROM " + sqlProvider.getMessages() + " WHERE TX_ID IS NULL"), 5000, 100);
         Wait.assertEquals(NUM_MESSAGES, () -> selectNumber(connection, "SELECT COUNT(*) FROM " + sqlProvider.getRefs() + " WHERE TX_ID IS NULL"), 5000, 100);
      }

      // Receive all messages and verify
      try (javax.jms.Connection connection = factory.createConnection()) {
         connection.start();
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         javax.jms.Queue queue = session.createQueue(QUEUE_NAME);
         MessageConsumer consumer = session.createConsumer(queue);
         for (int i = 0; i < NUM_MESSAGES; i++) {
            TextMessage msg = (TextMessage) consumer.receive(5000);
            logger.info("Received {} on {}", msg, i);
            assertNotNull(msg, "Expected message " + i);
         }
         assertNull(consumer.receiveNoWait());
         session.commit();
      }

      // Verify all messages consumed
      try (Connection connection = storageConfiguration.getDatabaseProvider().getConnection()) {
         Wait.assertEquals(0, () -> selectCount(connection, sqlProvider.getMessages()), 5000, 100);
      }
   }

   @TestTemplate
   public void testSimplaXATXReceive() throws Exception {
      testSimpleXATXReceive(false);
   }

   @TestTemplate
   public void testSimplaXATXReceiveRestart() throws Exception {
      testSimpleXATXReceive(true);
   }

   public void testSimpleXATXReceive(boolean restart) throws Exception {

      final String QUEUE_NAME = "testXAQueue";
      configuration.addAddressConfiguration(new CoreAddressConfiguration().setName(QUEUE_NAME).addRoutingType(RoutingType.ANYCAST).addQueueConfiguration(QueueConfiguration.of(QUEUE_NAME).setRoutingType(RoutingType.ANYCAST)));
      ActiveMQServer server = createServer(true, configuration);
      server.start();

      runAfter(server::stop);
      final int NUM_MESSAGES = 10;

      ConnectionFactory factory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616");
      SQLProvider sqlProvider = storageConfiguration.getDatabaseProvider().getSqlProvider();

      // Send messages outside XA
      try (javax.jms.Connection connection = factory.createConnection()) {
         connection.start();
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         javax.jms.Queue queue = session.createQueue(QUEUE_NAME);
         MessageProducer producer = session.createProducer(queue);
         for (int i = 0; i < NUM_MESSAGES; i++) {
            producer.send(session.createTextMessage("message-" + i));
         }
         session.commit();
      }

      // Verify messages are stored
      try (Connection connection = storageConfiguration.getDatabaseProvider().getConnection()) {
         Wait.assertEquals(NUM_MESSAGES, () -> selectCount(connection, sqlProvider.getMessages()), 5000, 100);
         Wait.assertEquals(NUM_MESSAGES, () -> selectCount(connection, sqlProvider.getRefs()), 5000, 100);
      }

      Xid xid = newXID();

      for (int repeat = 0; repeat < 2; repeat++) {
         // Receive all messages within an XA transaction and prepare
         try (XAConnection xaConnection = ((XAConnectionFactory) factory).createXAConnection()) {
            xaConnection.start();
            XASession xaSession = xaConnection.createXASession();
            javax.jms.Queue queue = xaSession.createQueue(QUEUE_NAME);
            MessageConsumer consumer = xaSession.createConsumer(queue);

            xaSession.getXAResource().start(xid, XAResource.TMNOFLAGS);
            for (int i = 0; i < NUM_MESSAGES; i++) {
               TextMessage msg = (TextMessage) consumer.receive(5000);
               assertNotNull(msg, "Expected message " + i);
            }
            xaSession.getXAResource().end(xid, XAResource.TMSUCCESS);
            xaSession.getXAResource().prepare(xid);
         }

         if (restart) {
            server.stop();
            server.start();
         }

         if (repeat == 0) {
            try (XAConnection xaConnection = ((XAConnectionFactory) factory).createXAConnection()) {
               XASession xaSession = xaConnection.createXASession();
               xaSession.getXAResource().rollback(xid);

               try (Connection connection = storageConfiguration.getDatabaseProvider().getConnection()) {
                  Wait.assertEquals(0, () -> selectCount(connection, sqlProvider.getPrepTx()), 5000, 100);
                  Wait.assertEquals(NUM_MESSAGES, () -> selectNumber(connection, "SELECT COUNT(*) FROM " + sqlProvider.getMessages() + " WHERE TX_ID IS NULL"), 5000, 100);
                  Wait.assertEquals(NUM_MESSAGES, () -> selectNumber(connection, "SELECT COUNT(*) FROM " + sqlProvider.getRefs() + " WHERE TX_ID IS NULL AND ACK_TX_ID IS NULL"), 5000, 100);
               }

               xid = newXID();
            }
         }
      }

      // Verify prepare record exists and messages still present
      try (Connection connection = storageConfiguration.getDatabaseProvider().getConnection()) {
         Wait.assertEquals(1, () -> selectCount(connection, sqlProvider.getPrepTx()), 5000, 100);
         Wait.assertEquals(NUM_MESSAGES, () -> selectCount(connection, sqlProvider.getMessages()), 5000, 100);
         Wait.assertEquals(NUM_MESSAGES, () -> selectCount(connection, sqlProvider.getRefs()), 5000, 100);
      }

      // Verify messages are not available for consumption before commit
      try (javax.jms.Connection connection = factory.createConnection()) {
         connection.start();
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         javax.jms.Queue queue = session.createQueue(QUEUE_NAME);
         MessageConsumer consumer = session.createConsumer(queue);
         assertNull(consumer.receiveNoWait());
      }

      logger.info("Going to commit......................................................");

      // Commit the XA transaction
      try (XAConnection xaConnection = ((XAConnectionFactory) factory).createXAConnection()) {
         XASession xaSession = xaConnection.createXASession();
         xaSession.getXAResource().commit(xid, false);
      }

      // Verify prepare record removed and messages consumed
      try (Connection connection = storageConfiguration.getDatabaseProvider().getConnection()) {
         Wait.assertEquals(0, () -> selectCount(connection, sqlProvider.getPrepTx()), 5000, 100);
         Wait.assertEquals(0, () -> selectCount(connection, sqlProvider.getRefs()), 5000, 100);
         Wait.assertEquals(0, () -> selectCount(connection, sqlProvider.getMessages()), 5000, 100);
      }
   }
}