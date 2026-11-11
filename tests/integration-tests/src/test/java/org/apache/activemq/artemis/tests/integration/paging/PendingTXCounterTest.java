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
package org.apache.activemq.artemis.tests.integration.paging;

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
import javax.jms.XAConnection;
import javax.jms.XAConnectionFactory;
import javax.jms.XASession;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import java.lang.invoke.MethodHandles;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.impl.QueueImplTestAccessor;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PendingTXCounterTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final String ADDRESS = "PendingTXCounterTest";

   ActiveMQServer server;

   protected static final int PAGE_MAX = 10 * 1024;

   protected static final int PAGE_SIZE = 1 * 1024;



   @BeforeEach
   @Override
   public void setUp() throws Exception {
      super.setUp();

      Configuration config = createDefaultConfig(0, true).setJournalSyncNonTransactional(false);

      config.setMessageExpiryScanPeriod(-1);
      server = createServer(true, config, PAGE_SIZE, PAGE_MAX);

      server.getAddressSettingsRepository().clear();

      AddressSettings defaultSetting = new AddressSettings().setPageSizeBytes(PAGE_SIZE).setMaxSizeBytes(PAGE_MAX).setMaxReadPageBytes(-1).setMaxSizeMessages(0).setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE).setAutoCreateAddresses(false).setAutoCreateQueues(false);

      server.getAddressSettingsRepository().addMatch("#", defaultSetting);


      server.start();


      server.addAddressInfo(new AddressInfo(ADDRESS).addRoutingType(RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(ADDRESS).setRoutingType(RoutingType.ANYCAST));

   }

   @Test
   public void testPendingSendCoreCommit() throws Exception {
      pendingSend("CORE", false, true);
   }

   @Test
   public void testPendingSendCoreCommitNoRestart() throws Exception {
      pendingSend("CORE", false, false);
   }


   @Test
   public void testPendingSendCoreRollback() throws Exception {
      pendingSend("CORE", true, true);
   }

   @Test
   public void testPendingSendCoreRollbackNoRestart() throws Exception {
      pendingSend("CORE", false, false);
   }

   private void pendingSend(String protocol, boolean rollback, boolean restart) throws Exception {
      AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler();
      runAfter(() -> loggerHandler.close());

      org.apache.activemq.artemis.core.server.Queue serverQueue = server.locateQueue(ADDRESS);


      final int INITIAL_NUMBER_OF_MESSAGES = 10;

      final int EXTRA_SEND = 20;

      final int TOTAL_MESSAGES = rollback ? INITIAL_NUMBER_OF_MESSAGES : INITIAL_NUMBER_OF_MESSAGES + EXTRA_SEND;

      ConnectionFactory cf = CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616");
      try (Connection connection = cf.createConnection();
           Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
         Queue queue = session.createQueue(ADDRESS);
         MessageProducer producer = session.createProducer(queue);
         for (int i = 0; i < INITIAL_NUMBER_OF_MESSAGES; i++) {
            Message message = session.createTextMessage("hello " + i);
            message.setIntProperty("i", i);
            producer.send(message);
         }
      }

      Wait.assertTrue(() -> loggerHandler.findText("AMQ222038"), 2000);

      Wait.assertEquals(INITIAL_NUMBER_OF_MESSAGES, serverQueue::getMessageCount, 2000);

      Xid xid = newXID();

      try (XAConnection connection = ((XAConnectionFactory)cf).createXAConnection();
           XASession session = connection.createXASession()) {
         Queue queue = session.createQueue(ADDRESS);
         MessageProducer producer = session.createProducer(queue);
         session.getXAResource().start(xid, XAResource.TMNOFLAGS);

         for (int i = INITIAL_NUMBER_OF_MESSAGES; i < INITIAL_NUMBER_OF_MESSAGES + EXTRA_SEND; i++) {
            Message message = session.createTextMessage("hello " + i);
            message.setIntProperty("i", i);
            producer.send(message);
         }
         session.getXAResource().end(xid, XAResource.TMSUCCESS);
         session.getXAResource().prepare(xid);
      }

      Wait.assertEquals(INITIAL_NUMBER_OF_MESSAGES, serverQueue::getMessageCount, 2000);

      if (restart) {
         server.stop();

         server.start();
      }

      serverQueue = server.locateQueue(ADDRESS);

      Wait.assertEquals(INITIAL_NUMBER_OF_MESSAGES, serverQueue::getMessageCount, 2000);

      try (XAConnection connection = ((XAConnectionFactory)cf).createXAConnection();
           XASession session = connection.createXASession()) {
         if (rollback) {
            session.getXAResource().rollback(xid);
         } else {
            session.getXAResource().commit(xid, false);
         }
      }

      Wait.assertEquals(TOTAL_MESSAGES, serverQueue::getMessageCount, 2000);

      try (Connection connection = cf.createConnection();
           Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
         Queue queue = session.createQueue(ADDRESS);
         MessageConsumer consumer = session.createConsumer(queue);
         connection.start();

         for (int i = 0; i < TOTAL_MESSAGES; i++) {
            TextMessage message = (TextMessage) consumer.receive(1000);
            assertNotNull(message);
            assertEquals("hello " + i, message.getText());
            assertEquals(i, message.getIntProperty("i"));
         }

         assertTrue(serverQueue.getMessageCount() >= 0);
      }

      Wait.assertEquals(0, serverQueue::getMessageCount, 2000);
      {
         org.apache.activemq.artemis.core.server.Queue localRefQueue = serverQueue;
         Wait.assertEquals(0L, () -> QueueImplTestAccessor.getQueueMemorySize(localRefQueue));
      }

   }

   @Test
   public void testPendingACKTXRollbackCore() throws Exception {
      pendingACKTXRollback("CORE", true, true);
   }

   @Test
   public void testPendingACKTXCommitCore() throws Exception {
      pendingACKTXRollback("CORE", false, true);
   }

   @Test
   public void testPendingACKTXRollbackCoreNoRestart() throws Exception {
      pendingACKTXRollback("CORE", true, false);
   }

   @Test
   public void testPendingACKTXCommitCoreNoRestart() throws Exception {
      pendingACKTXRollback("CORE", false, false);
   }

   private void pendingACKTXRollback(String protocol, boolean rollback, boolean restart) throws Exception {
      AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler();
      runAfter(() -> loggerHandler.close());

      org.apache.activemq.artemis.core.server.Queue serverQueue = server.locateQueue(ADDRESS);

      final int NUMBER_OF_MESSAGES = 15;

      ConnectionFactory cf = CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616");
      try (Connection connection = cf.createConnection();
           Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
         Queue queue = session.createQueue(ADDRESS);
         MessageProducer producer = session.createProducer(queue);
         for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            Message message = session.createTextMessage("hello " + i);
            message.setIntProperty("i", i);
            message.setStringProperty("text", "hello " + i);
            producer.send(message);
         }
      }

      Wait.assertTrue(() -> loggerHandler.findText("AMQ222038"), 2000);

      Wait.assertEquals(NUMBER_OF_MESSAGES, serverQueue::getMessageCount, 2000);

      Xid xid1 = newXID();
      Xid xid2 = newXID();

      for (int repeat = 0; repeat < 2; repeat++) {

         Xid xid = repeat == 0 ? xid1 : xid2;

         int startPosition = 5 * repeat;
         int endPosition = startPosition + 5;

         try (XAConnection connection = ((XAConnectionFactory) cf).createXAConnection(); XASession session = connection.createXASession()) {
            Queue queue = session.createQueue(ADDRESS);
            MessageConsumer consumer = session.createConsumer(queue);
            connection.start();
            session.getXAResource().start(xid, XAResource.TMNOFLAGS);

            for (int i = startPosition; i < endPosition; i++) {
               TextMessage message = (TextMessage) consumer.receive(1000);
               assertEquals("hello " + i, message.getText());
               assertEquals(i, message.getIntProperty("i"));
            }

            session.getXAResource().end(xid, XAResource.TMSUCCESS);
            session.getXAResource().prepare(xid);

            if (repeat == 0) {
               session.getXAResource().commit(xid, false);
            }
         }
      }

      Wait.assertEquals(NUMBER_OF_MESSAGES - 5, serverQueue::getMessageCount, 2000);

      try (Connection connection = cf.createConnection();
           Session session = connection.createSession(true, Session.SESSION_TRANSACTED)) {
         Queue queue = session.createQueue(ADDRESS);
         connection.start();
         MessageConsumer consumer = session.createConsumer(queue);
         for (int i = 10; i < NUMBER_OF_MESSAGES; i++) {
            TextMessage message = (TextMessage) consumer.receive(1000);
            assertNotNull(message);
            logger.info("Received {}", message.getText());
            assertEquals("hello " + i, message.getText());
            assertEquals(i, message.getIntProperty("i"));
         }
         assertNull(consumer.receiveNoWait());
         session.rollback();
      }

      Wait.assertEquals(NUMBER_OF_MESSAGES - 5, serverQueue::getMessageCount, 2000);

      if (restart) {
         server.stop();

         server.start();
      }

      serverQueue = server.locateQueue(ADDRESS);

      Wait.assertEquals(NUMBER_OF_MESSAGES - 5, serverQueue::getMessageCount, 2000);

      logger.info("Before tx = {}", serverQueue.getMessageCount());

      try (XAConnection connection = ((XAConnectionFactory)cf).createXAConnection();
           XASession session = connection.createXASession()) {
         if (rollback) {
            session.getXAResource().rollback(xid2);
         } else {
            session.getXAResource().commit(xid2, false);
         }
      }

      if (rollback) {
         Wait.assertEquals(NUMBER_OF_MESSAGES - 5, serverQueue::getMessageCount, 2000);
      } else {
         Wait.assertEquals(NUMBER_OF_MESSAGES - 10, serverQueue::getMessageCount, 2000);
      }

      try (Connection connection = cf.createConnection();
           Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
         Queue queue = session.createQueue(ADDRESS);
         MessageConsumer consumer = session.createConsumer(queue);
         connection.start();

         int start = rollback ? 5 : 10;
         logger.debug("start is at {}, since rollback={}", start, rollback);

         for (int i = start; i < NUMBER_OF_MESSAGES; i++) {
            TextMessage message = (TextMessage) consumer.receive(1000);
            assertNotNull(message);
            logger.debug("Received message {}", message.getText());
            assertEquals("hello " + i, message.getText());
            assertEquals(i, message.getIntProperty("i"));
         }
         assertNull(consumer.receiveNoWait());

         assertTrue(serverQueue.getMessageCount() >= 0);
      }

      Wait.assertEquals(0, serverQueue::getMessageCount, 2000);

      {
         org.apache.activemq.artemis.core.server.Queue localRefQueue = serverQueue;
         Wait.assertEquals(0L, () -> QueueImplTestAccessor.getQueueMemorySize(localRefQueue), 2000);
      }

   }

}