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
package org.apache.activemq.artemis.tests.integration.cluster.failover;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.ActiveMQDuplicateIdException;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.ActiveMQInternalErrorException;
import org.apache.activemq.artemis.api.core.ActiveMQObjectClosedException;
import org.apache.activemq.artemis.api.core.ActiveMQTransactionOutcomeUnknownException;
import org.apache.activemq.artemis.api.core.ActiveMQTransactionRolledBackException;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.FailoverEventType;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.client.SessionFailureListener;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryInternal;
import org.apache.activemq.artemis.core.client.impl.ClientSessionInternal;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorImpl;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.paging.impl.Page;
import org.apache.activemq.artemis.core.protocol.core.Channel;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.impl.ActiveMQSessionContext;
import org.apache.activemq.artemis.core.protocol.core.impl.ChannelImpl;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.core.protocol.core.impl.RemotingConnectionImpl;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ActiveMQExceptionMessage;
import org.apache.activemq.artemis.core.server.cluster.BackupManager;
import org.apache.activemq.artemis.core.server.cluster.ClusterController;
import org.apache.activemq.artemis.core.server.cluster.ha.BackupPolicy;
import org.apache.activemq.artemis.core.server.cluster.ha.ReplicatedPolicy;
import org.apache.activemq.artemis.core.server.cluster.ha.ReplicationBackupPolicy;
import org.apache.activemq.artemis.core.server.cluster.ha.ReplicationPrimaryPolicy;
import org.apache.activemq.artemis.core.server.cluster.ha.SharedStoreBackupPolicy;
import org.apache.activemq.artemis.core.server.cluster.ha.SharedStorePrimaryPolicy;
import org.apache.activemq.artemis.core.server.cluster.impl.ClusterConnectionImpl;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.core.server.impl.InVMNodeManager;
import org.apache.activemq.artemis.core.server.impl.SharedNothingBackupActivation;
import org.apache.activemq.artemis.core.transaction.impl.XidImpl;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.tests.integration.cluster.util.SameProcessActiveMQServer;
import org.apache.activemq.artemis.tests.integration.cluster.util.TestableServer;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.tests.util.CountDownSessionFailureListener;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class NettyReplicatedFailoverTest extends NettyFailoverInVMTest {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Override
   protected TestableServer createTestableServer(Configuration config) {
      return new SameProcessActiveMQServer(createServer(true, config));
   }

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      super.setUp();
      locator = getServerLocator();
   }


   @Override
   protected void createConfigs() throws Exception {
      createReplicatedConfigs();
   }

   @Override
   protected final void crash(boolean waitFailure, ClientSession... sessions) throws Exception {
      if (sessions.length > 0) {
         for (ClientSession session : sessions) {
            waitForRemoteBackup(session.getSessionFactory(), 5, true, backupServer.getServer());
         }
      } else {
         waitForRemoteBackup(null, 5, true, backupServer.getServer());
      }
      super.crash(waitFailure, sessions);
   }

   @Override
   protected final void crash(ClientSession... sessions) throws Exception {
      crash(true, sessions);
   }

   @Test
   public void testPagedInSync() throws Exception {

      String queueName = "testPagedInSync";

      ConnectionFactory factory = CFUtil.createConnectionFactory("core", "tcp://localhost:61616");
      try (Connection conn = factory.createConnection()) {
         Session session = conn.createSession(true, Session.SESSION_TRANSACTED);
         Queue queue = session.createQueue(queueName);
         MessageProducer producer = session.createProducer(queue);
         producer.send(session.createTextMessage("hello"));
         session.commit();

         org.apache.activemq.artemis.core.server.Queue serverQueue = primaryServer.getServer().locateQueue(queueName);
         Assertions.assertNotNull(serverQueue);

         serverQueue.getPagingStore().startPaging();

         for (int i = 0; i < 50; i++) {
            producer.send(session.createTextMessage("hello"));
            session.commit();
            serverQueue.getPagingStore().forceAnotherPage(true);
         }
         backupServer.stop();
         backupServer.start();
         Wait.assertTrue(backupServer.getServer()::isReplicaSync);

         SharedNothingBackupActivation activation = (SharedNothingBackupActivation) backupServer.getServer().getActivation();
         Map<Long, Page> currentPages = activation.getReplicationEndpoint().getPageIndex().get(SimpleString.of(queueName));

         logger.info("There are {} page files open", currentPages.size());
         Wait.assertTrue(() -> currentPages.size() <= 1, 10_000);

         producer.send(session.createTextMessage("on currentPage"));
         session.commit();

         PagingStore store = primaryServer.getServer().getPagingManager().getPageStore(SimpleString.of(queueName));
         Page currentPage = store.getCurrentPage();
         logger.info("Page {}", currentPage.getPageId());

         Page depaged = null;
         for (; ; ) {
            depaged = store.depage();
            if (depaged == null || currentPage.getPageId() == depaged.getPageId()) {
               break;
            }
            logger.info("depage :: {} and currentPageID={}", depaged.getPageId(), currentPage.getPageId());
         }

         Assertions.assertNotNull(depaged);

         logger.info("Depaged:: {}", depaged.getPageId());

         for (int i = 0;  i < 10; i++) {
            producer.send(session.createTextMessage("on current page"));
            session.commit();
            store.depage();
         }

         logger.info("Size:: {}", currentPages.size());

         Wait.assertTrue(() -> currentPages.size() <= 1, 10_000);

      }
   }


   @Test
   @Timeout(120)
   public void testTimeoutOnFailover() throws Exception {
      locator.setCallTimeout(1000).setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setAckBatchSize(0).setReconnectAttempts(300).setRetryInterval(10);

      if (nodeManager instanceof InVMNodeManager manager) {
         manager.failoverPause = 500L;
      }

      ClientSessionFactoryInternal sf1 = (ClientSessionFactoryInternal) createSessionFactory(locator);

      final ClientSession session = createSession(sf1, true, true);

      session.createQueue(QueueConfiguration.of(FailoverTestBase.ADDRESS));

      final ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      final CountDownLatch latch = new CountDownLatch(10);
      final CountDownLatch latchFailed = new CountDownLatch(1);

      Runnable r = () -> {
         for (int i = 0; i < 500; i++) {
            ClientMessage message = session.createMessage(true);
            message.putIntProperty("counter", i);
            try {
               producer.send(message);
               if (i < 10) {
                  latch.countDown();
                  if (latch.getCount() == 0) {
                     latchFailed.await(10, TimeUnit.SECONDS);
                  }
               }
            } catch (Exception e) {
               // this is our retry
               try {
                  if (!producer.isClosed()) {
                     producer.send(message);
                  }
               } catch (ActiveMQException e1) {
                  e1.printStackTrace();
               }
            }
         }
      };
      Thread t = new Thread(r);
      t.start();
      assertTrue(latch.await(10, TimeUnit.SECONDS), "latch released");
      crash(session);
      latchFailed.countDown();
      t.join(30000);
      if (t.isAlive()) {
         t.interrupt();
         fail("Thread still alive");
      }
      assertTrue(backupServer.getServer().waitForActivation(5, TimeUnit.SECONDS));
      ClientConsumer consumer = session.createConsumer(FailoverTestBase.ADDRESS);
      session.start();
      for (int i = 0; i < 500; i++) {
         ClientMessage m = consumer.receive(1000);
         assertNotNull(m, "message #=" + i);
         // assertEquals(i, m.getIntProperty("counter").intValue());
      }
   }

   @Test
   @Timeout(120)
   public void testTimeoutOnFailoverConsume() throws Exception {
      locator.setCallTimeout(1000).setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setAckBatchSize(0).setBlockOnAcknowledge(true).setReconnectAttempts(-1).setRetryInterval(10).setAckBatchSize(0);

      if (nodeManager instanceof InVMNodeManager manager) {
         manager.failoverPause = 2000L;
      }

      ClientSessionFactoryInternal sf1 = (ClientSessionFactoryInternal) createSessionFactory(locator);

      final ClientSession session = createSession(sf1, true, false);

      session.createQueue(QueueConfiguration.of(FailoverTestBase.ADDRESS));

      final ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      for (int i = 0; i < 500; i++) {
         ClientMessage message = session.createMessage(true);
         message.putIntProperty("counter", i);
         producer.send(message);
      }

      final CountDownLatch latch = new CountDownLatch(1);
      final CountDownLatch endLatch = new CountDownLatch(1);

      final ClientConsumer consumer = session.createConsumer(FailoverTestBase.ADDRESS);
      session.start();

      final Map<Integer, ClientMessage> received = new HashMap<>();

      consumer.setMessageHandler(message -> {

         Integer counter = message.getIntProperty("counter");
         received.put(counter, message);
         try {
            logger.debug("acking message = id = {}, counter = {}", message.getMessageID(), message.getIntProperty("counter"));
            message.acknowledge();
            session.commit();
         } catch (ActiveMQException e) {
            try {
               session.rollback();
            } catch (Exception e2) {
               e.printStackTrace();
            }
            e.printStackTrace();
            return;
         }
         logger.debug("Acked counter = {}", counter);
         if (counter.equals(10)) {
            latch.countDown();
         }
         if (received.size() == 100) {
            endLatch.countDown();
         }
      });
      latch.await(10, TimeUnit.SECONDS);
      logger.debug("crashing session");
      crash(session);
      assertTrue(endLatch.await(60, TimeUnit.SECONDS));

      session.close();
   }

   @Test
   @Timeout(120)
   public void testTimeoutOnFailoverConsumeBlocked() throws Exception {
      locator.setCallTimeout(1000).setBlockOnNonDurableSend(true).setConsumerWindowSize(0).setBlockOnDurableSend(true).setAckBatchSize(0).setBlockOnAcknowledge(true).setReconnectAttempts(-1).setAckBatchSize(0).setRetryInterval(10);

      if (nodeManager instanceof InVMNodeManager manager) {
         manager.failoverPause = 200L;
      }

      ClientSessionFactoryInternal sf1 = (ClientSessionFactoryInternal) createSessionFactory(locator);

      final ClientSession session = createSession(sf1, true, true);

      session.createQueue(QueueConfiguration.of(FailoverTestBase.ADDRESS));

      final ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      for (int i = 0; i < 500; i++) {
         ClientMessage message = session.createMessage(true);
         message.putIntProperty("counter", i);
         message.putBooleanProperty("end", i == 499);
         producer.send(message);
      }

      final CountDownLatch latch = new CountDownLatch(1);
      final CountDownLatch endLatch = new CountDownLatch(1);

      final ClientConsumer consumer = session.createConsumer(FailoverTestBase.ADDRESS);
      session.start();

      final Map<Integer, ClientMessage> received = new HashMap<>();

      Thread t = new Thread() {
         @Override
         public void run() {
            ClientMessage message = null;
            try {
               while ((message = getMessage()) != null) {
                  Integer counter = message.getIntProperty("counter");
                  received.put(counter, message);
                  try {
                     logger.debug("acking message = id = {}, counter = {}", message.getMessageID(), message.getIntProperty("counter"));
                     message.acknowledge();
                  } catch (ActiveMQException e) {
                     e.printStackTrace();
                     continue;
                  }
                  logger.debug("Acked counter = {}", counter);
                  if (counter.equals(10)) {
                     latch.countDown();
                  }
                  if (received.size() == 500) {
                     endLatch.countDown();
                  }

                  if (message.getBooleanProperty("end")) {
                     break;
                  }
               }
            } catch (Exception e) {
               fail("failing due to exception " + e);
            }

         }

         private ClientMessage getMessage() {
            while (true) {
               try {
                  ClientMessage msg = consumer.receive(20000);
                  if (msg == null) {
                     logger.debug("Returning null message on consuming");
                  }
                  return msg;
               } catch (ActiveMQObjectClosedException oce) {
                  throw new RuntimeException(oce);
               } catch (ActiveMQException ignored) {
                  // retry
                  ignored.printStackTrace();
               }
            }
         }
      };
      t.start();
      latch.await(10, TimeUnit.SECONDS);
      logger.debug("crashing session");
      crash(session);
      endLatch.await(60, TimeUnit.SECONDS);
      t.join();
      assertEquals(500, received.size(), "received only " + received.size());

      session.close();
   }

   @Test
   @Timeout(120)
   public void testTimeoutOnFailoverTransactionCommit() throws Exception {
      locator.setCallTimeout(1000).setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setAckBatchSize(0).setReconnectAttempts(300).setRetryInterval(10);

      if (nodeManager instanceof InVMNodeManager manager) {
         manager.failoverPause = 2000L;
      }

      ClientSessionFactoryInternal sf1 = (ClientSessionFactoryInternal) createSessionFactory(locator);

      final ClientSession session = createSession(sf1, true, false, false);

      session.createQueue(QueueConfiguration.of(FailoverTestBase.ADDRESS));

      final CountDownLatch connectionFailed = new CountDownLatch(1);

      session.addFailureListener(new SessionFailureListener() {
         @Override
         public void beforeReconnect(ActiveMQException exception) {
         }

         @Override
         public void connectionFailed(ActiveMQException exception, boolean failedOver) {
         }

         @Override
         public void connectionFailed(ActiveMQException exception, boolean failedOver, String scaleDownTargetNodeID) {
            connectionFailed.countDown();
         }
      });

      final ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      Xid xid = new XidImpl("uhuhuhu".getBytes(), 126512, "auhsduashd".getBytes());

      session.start(xid, XAResource.TMNOFLAGS);

      for (int i = 0; i < 500; i++) {
         ClientMessage message = session.createMessage(true);
         message.putIntProperty("counter", i);

         producer.send(message);

      }
      session.end(xid, XAResource.TMSUCCESS);
      session.prepare(xid);
      crash(true, session);

      try {
         session.commit(xid, false);
      } catch (XAException e) {
         //there is still an edge condition that we must deal with
         assertTrue(connectionFailed.await(10, TimeUnit.SECONDS));
         session.commit(xid, false);
      }

      ClientConsumer consumer = session.createConsumer(FailoverTestBase.ADDRESS);
      session.start();
      for (int i = 0; i < 500; i++) {
         ClientMessage m = consumer.receive(1000);
         assertNotNull(m);
         assertEquals(i, m.getIntProperty("counter").intValue());
      }
   }

   /**
    * This test would fail one in three or five times, where the commit would leave the session dirty after a timeout.
    */
   @Test
   @Timeout(120)
   public void testTimeoutOnFailoverTransactionCommitTimeoutCommunication() throws Exception {
      locator.setCallTimeout(1000).setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setAckBatchSize(0).setReconnectAttempts(300).setRetryInterval(50);

      if (nodeManager instanceof InVMNodeManager manager) {
         manager.failoverPause = 2000L;
      }

      ClientSessionFactoryInternal sf1 = (ClientSessionFactoryInternal) createSessionFactory(locator);
      final ClientSession session = createSession(sf1, false, false, false);

      session.createQueue(QueueConfiguration.of(FailoverTestBase.ADDRESS));

      final CountDownLatch connectionFailed = new CountDownLatch(1);

      session.addFailureListener(new SessionFailureListener() {
         @Override
         public void beforeReconnect(ActiveMQException exception) {
         }

         @Override
         public void connectionFailed(ActiveMQException exception, boolean failedOver) {
         }

         @Override
         public void connectionFailed(ActiveMQException exception, boolean failedOver, String scaleDownTargetNodeID) {
            connectionFailed.countDown();
         }
      });

      final ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      for (int i = 0; i < 500; i++) {
         ClientMessage message = session.createMessage(true);
         message.putIntProperty("counter", i);

         producer.send(message);

      }

      session.commit();


      ClientConsumer consumer = session.createConsumer(FailoverTestBase.ADDRESS);
      session.start();
      ClientMessage m = null;
      for (int i = 0; i < 500; i++) {
         m = consumer.receive(1000);
         assertNotNull(m);
         assertEquals(i, m.getIntProperty("counter").intValue());
      }

      m.acknowledge();

      crash(false, session);
      try {
         session.commit();
         fail("Exception expected");
      } catch (Exception expected) {
         expected.printStackTrace();
      }

      Thread.sleep(1000);

      m = null;
      for (int i = 0; i < 500; i++) {
         m = consumer.receive(1000);
         assertNotNull(m);
         assertEquals(i, m.getIntProperty("counter").intValue());
      }

      m.acknowledge();

      session.commit();

   }

   @Test
   @Timeout(120)
   public void testTimeoutOnFailoverTransactionRollback() throws Exception {
      locator.setCallTimeout(2000).setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setAckBatchSize(0).setReconnectAttempts(300).setRetryInterval(10);

      if (nodeManager instanceof InVMNodeManager manager) {
         manager.failoverPause = 1000L;
      }

      ClientSessionFactoryInternal sf1 = (ClientSessionFactoryInternal) createSessionFactory(locator);

      final ClientSession session = createSession(sf1, true, false, false);

      session.createQueue(QueueConfiguration.of(FailoverTestBase.ADDRESS));

      final ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      Xid xid = new XidImpl("uhuhuhu".getBytes(), 126512, "auhsduashd".getBytes());

      session.start(xid, XAResource.TMNOFLAGS);

      for (int i = 0; i < 500; i++) {
         ClientMessage message = session.createMessage(true);
         message.putIntProperty("counter", i);

         producer.send(message);
      }

      session.end(xid, XAResource.TMSUCCESS);
      session.prepare(xid);
      crash(true, session);

      try {
         session.rollback(xid);
      } catch (XAException e) {
         try {
            //there is still an edge condition that we must deal with
            session.rollback(xid);
         } catch (Exception ignored) {
            logger.trace(ignored.getMessage(), ignored);
         }
      }

      ClientConsumer consumer = session.createConsumer(FailoverTestBase.ADDRESS);
      session.start();

      ClientMessage m = consumer.receiveImmediate();
      assertNull(m);

   }

   @Test
   @Timeout(120)
   public void testNonTransactedWithZeroConsumerWindowSize() throws Exception {
      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setAckBatchSize(0).setReconnectAttempts(300).setRetryInterval(10);

      createClientSessionFactory();

      ClientSession session = createSession(sf, true, true);

      session.createQueue(QueueConfiguration.of(FailoverTestBase.ADDRESS));

      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      for (int i = 0; i < NUM_MESSAGES; i++) {
         ClientMessage message = session.createMessage(true);

         setBody(i, message);

         message.putIntProperty("counter", i);

         producer.send(message);
      }

      ClientConsumer consumer = session.createConsumer(FailoverTestBase.ADDRESS);

      final CountDownLatch latch = new CountDownLatch(NUM_MESSAGES);

      consumer.setMessageHandler(message -> latch.countDown());

      session.start();

      crash(session);

      assertTrue(latch.await(10, TimeUnit.SECONDS));

   }


   @Test
   @Timeout(60)
   public void testFailBothRestartPrimary() throws Exception {
      ServerLocator locator = getServerLocator();

      locator.setReconnectAttempts(-1).setRetryInterval(10);

      sf = (ClientSessionFactoryInternal)locator.createSessionFactory();

      ClientSession session = createSession(sf, true, true);

      session.createQueue(QueueConfiguration.of(FailoverTestBase.ADDRESS));

      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      sendMessagesSomeDurable(session, producer);

      crash(session);

      ClientConsumer consumer = session.createConsumer(FailoverTestBase.ADDRESS);

      session.start();

      receiveDurableMessages(consumer);

      backupServer.getServer().fail(true);

      decrementActivationSequenceForForceRestartOf(primaryServer);

      primaryServer.start();

      consumer.close();

      producer.close();

      producer = session.createProducer(FailoverTestBase.ADDRESS);

      sendMessagesSomeDurable(session, producer);

      sf.close();
      assertEquals(0, sf.numSessions());
      assertEquals(0, sf.numConnections());
   }

   @Test
   public void testFailPrimaryTooSoon() throws Exception {
      ServerLocator locator = getServerLocator();

      locator.setReconnectAttempts(-1);
      locator.setRetryInterval(10);

      sf = (ClientSessionFactoryInternal)locator.createSessionFactory();

      waitForBackupConfig(sf);

      TransportConfiguration initialPrimary = getFieldFromSF(sf, "currentConnectorConfig");
      TransportConfiguration initialBackup = getFieldFromSF(sf, "backupConnectorConfig");

      logger.debug("initprimary: {}", initialPrimary);
      logger.debug("initback: {}", initialBackup);

      TransportConfiguration last = getFieldFromSF(sf, "connectorConfig");
      TransportConfiguration current = getFieldFromSF(sf, "currentConnectorConfig");

      logger.debug("now last: {}", last);
      logger.debug("now current: {}", current);
      assertEquals(current, initialPrimary);

      ClientSession session = createSession(sf, true, true);

      session.createQueue(QueueConfiguration.of(FailoverTestBase.ADDRESS));

      //crash 1
      crash();

      //make sure failover is ok
      createSession(sf, true, true).close();

      last = getFieldFromSF(sf, "connectorConfig");
      current = getFieldFromSF(sf, "currentConnectorConfig");

      logger.debug("now after primary crashed last: {}", last);
      logger.debug("now current: {}", current);

      assertEquals(current, initialBackup);

      //fail back
      beforeRestart(primaryServer);
      adaptPrimaryConfigForReplicatedFailBack(primaryServer);
      primaryServer.getServer().start();

      assertTrue(primaryServer.getServer().waitForActivation(40, TimeUnit.SECONDS), "primary initialized...");
      org.apache.activemq.artemis.utils.Wait.assertTrue(backupServer::isStarted);
      primaryServer.getServer().waitForActivation(5, TimeUnit.SECONDS);
      assertTrue(backupServer.isStarted());

      //make sure failover is ok
      createSession(sf, true, true).close();

      last = getFieldFromSF(sf, "connectorConfig");
      current = getFieldFromSF(sf, "currentConnectorConfig");

      logger.debug("now after primary back again last: {}", last);
      logger.debug("now current: {}", current);

      //cannot use equals here because the config's name (uuid) changes
      //after failover
      assertTrue(current.isSameParams(initialPrimary));

      //now manually corrupt the backup in sf
      setSFFieldValue(sf, "backupConnectorConfig", null);

      //crash 2
      crash();

      beforeRestart(backupServer);
      createSession(sf, true, true).close();

      sf.close();
      assertEquals(0, sf.numSessions());
      assertEquals(0, sf.numConnections());
   }


   @Test
   @Timeout(120)
   public void testFailBackPrimaryRestartsBackupIsGone() throws Exception {
      createSessionFactory();
      ClientSession session = createSessionAndQueue();

      ClientProducer producer = addClientProducer(session.createProducer(FailoverTestBase.ADDRESS));

      sendMessages(session, producer, NUM_MESSAGES);
      producer.close();
      session.commit();
      SimpleString primaryId = primaryServer.getServer().getNodeID();
      crash(session);

      session.start();
      ClientConsumer consumer = addClientConsumer(session.createConsumer(FailoverTestBase.ADDRESS));
      receiveMessages(consumer);
      assertNoMoreMessages(consumer);
      consumer.close();
      session.commit();

      assertEquals(primaryId, backupServer.getServer().getNodeID(), "backup must be running with the same nodeID");
      sf.close();

      backupServer.crash();
      Thread.sleep(100);
      assertFalse(backupServer.isStarted(), "backup is not running");

      final boolean isBackup = primaryServer.getServer().getHAPolicy() instanceof BackupPolicy ||
         primaryServer.getServer().getHAPolicy() instanceof ReplicationBackupPolicy;
      assertFalse(isBackup, "must NOT be a backup");
      adaptPrimaryConfigForReplicatedFailBack(primaryServer);
      beforeRestart(primaryServer);
      decrementActivationSequenceForForceRestartOf(primaryServer);
      primaryServer.start();
      assertTrue(primaryServer.getServer().waitForActivation(15, TimeUnit.SECONDS), "primary initialized...");

      sf = (ClientSessionFactoryInternal) createSessionFactory(locator);

      ClientSession session2 = createSession(sf, false, false);
      session2.start();
      ClientConsumer consumer2 = session2.createConsumer(FailoverTestBase.ADDRESS);
      final boolean replication = primaryServer.getServer().getHAPolicy() instanceof ReplicatedPolicy ||
         primaryServer.getServer().getHAPolicy() instanceof ReplicationPrimaryPolicy;
      if (replication) {
         receiveMessages(consumer2, 0, NUM_MESSAGES, true);
      }
      assertNoMoreMessages(consumer2);
      session2.commit();
   }

   @Test
   @Timeout(120)
   public void testConsumeTransacted() throws Exception {
      createSessionFactory();

      ClientSession session = createSessionAndQueue();

      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      final int numMessages = 10;

      sendMessages(session, producer, numMessages);

      session.commit();

      ClientConsumer consumer = session.createConsumer(FailoverTestBase.ADDRESS);

      session.start();

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer.receive(1000);
         assertNotNull(message, "Just crashed? " + (i == 6) + " " + i);

         message.acknowledge();

         // TODO: The test won't pass if you uncomment this line
         // assertEquals(i, (int)message.getIntProperty("counter"));

         if (i == 5) {
            crash(session);
         }
      }

      try {
         session.commit();
         fail("session must have rolled back on failover");
      } catch (ActiveMQTransactionRolledBackException trbe) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }

      consumer.close();

      consumer = session.createConsumer(FailoverTestBase.ADDRESS);

      session.start();

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer.receive(1000);

         assertNotNull(message, "Expecting message #" + i);

         message.acknowledge();
      }

      session.commit();

      session.close();
   }

   @Test
   @Timeout(120)
   public void testFailoverOnInitialConnection() throws Exception {
      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setReconnectAttempts(300).setRetryInterval(100);

      sf = createSessionFactoryAndWaitForTopology(locator, 2);

      // Crash primary server
      crash();

      ClientSession session = createSession(sf);

      session.createQueue(QueueConfiguration.of(FailoverTestBase.ADDRESS));

      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      sendMessages(session, producer, NUM_MESSAGES);

      ClientConsumer consumer = session.createConsumer(FailoverTestBase.ADDRESS);

      session.start();

      receiveMessages(consumer);

      session.close();
   }


   @Test
   @Timeout(120)
   public void testTransactedMessagesSentSoRollback() throws Exception {
      createSessionFactory();

      ClientSession session = createSessionAndQueue();

      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      sendMessagesSomeDurable(session, producer);

      crash(session);

      assertTrue(session.isRollbackOnly());

      try {
         session.commit();

         fail("Should throw exception");
      } catch (ActiveMQTransactionRolledBackException trbe) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }

      ClientConsumer consumer = session.createConsumer(FailoverTestBase.ADDRESS);

      session.start();

      ClientMessage message = consumer.receiveImmediate();

      assertNull(message, "message should be null! Was: " + message);

      session.close();
   }

   /**
    * Test that once the transacted session has throw a TRANSACTION_ROLLED_BACK exception, it can be reused again
    */
   @Test
   @Timeout(120)
   public void testTransactedMessagesSentSoRollbackAndContinueWork() throws Exception {
      createSessionFactory();

      ClientSession session = createSessionAndQueue();

      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      sendMessagesSomeDurable(session, producer);

      crash(session);

      assertTrue(session.isRollbackOnly());

      try {
         session.commit();

         fail("Should throw exception");
      } catch (ActiveMQTransactionRolledBackException trbe) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }

      ClientMessage message = session.createMessage(false);
      int counter = RandomUtil.randomInt();
      message.putIntProperty("counter", counter);

      producer.send(message);

      // session is working again
      session.commit();

      session.start();

      ClientConsumer consumer = session.createConsumer(FailoverTestBase.ADDRESS);

      session.start();
      message = consumer.receive(1000);

      assertNotNull(message, "expecting a message");
      assertEquals(counter, message.getIntProperty("counter").intValue());

      session.close();
   }

   @Test
   @Timeout(120)
   public void testTransactedMessagesNotSentSoNoRollback() throws Exception {
      try {
         createSessionFactory();

         ClientSession session = createSessionAndQueue();

         ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

         sendMessagesSomeDurable(session, producer);

         session.commit();

         crash(session);

         // committing again should work since didn't send anything since last commit

         assertFalse(session.isRollbackOnly());

         session.commit();

         ClientConsumer consumer = session.createConsumer(FailoverTestBase.ADDRESS);

         session.start();

         receiveDurableMessages(consumer);

         assertNull(consumer.receiveImmediate());

         session.commit();

         session.close();
      } finally {
         try {
            primaryServer.getServer().stop();
         } catch (Throwable ignored) {
         }
         try {
            backupServer.getServer().stop();
         } catch (Throwable ignored) {
         }
      }
   }

   @Test
   @Timeout(120)
   public void testTransactedMessagesWithConsumerStartedBeforeFailover() throws Exception {
      createSessionFactory();

      ClientSession session = createSessionAndQueue();

      // create a consumer and start the session before failover
      ClientConsumer consumer = session.createConsumer(FailoverTestBase.ADDRESS);

      session.start();

      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      sendMessagesSomeDurable(session, producer);

      // messages will be delivered to the consumer when the session is committed
      session.commit();

      assertFalse(session.isRollbackOnly());

      crash(session);

      session.commit();

      session.close();

      session = createSession(sf, false, false);

      consumer = session.createConsumer(FailoverTestBase.ADDRESS);

      session.start();

      receiveDurableMessages(consumer);

      assertNull(consumer.receiveImmediate());

      session.commit();
   }

   @Test
   @Timeout(120)
   public void testTransactedMessagesConsumedSoRollback() throws Exception {
      createSessionFactory();

      ClientSession session1 = createSessionAndQueue();

      ClientProducer producer = session1.createProducer(FailoverTestBase.ADDRESS);

      sendMessagesSomeDurable(session1, producer);

      session1.commit();

      ClientSession session2 = createSession(sf, false, false);

      ClientConsumer consumer = session2.createConsumer(FailoverTestBase.ADDRESS);

      session2.start();

      receiveMessages(consumer);

      crash(session2);

      assertTrue(session2.isRollbackOnly());

      try {
         session2.commit();

         fail("Should throw exception");
      } catch (ActiveMQTransactionRolledBackException trbe) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }
   }

   @Test
   @Timeout(120)
   public void testTransactedMessagesNotConsumedSoNoRollback() throws Exception {
      createSessionFactory();

      ClientSession session1 = createSessionAndQueue();

      ClientProducer producer = session1.createProducer(FailoverTestBase.ADDRESS);

      sendMessages(session1, producer, NUM_MESSAGES);
      session1.commit();

      ClientSession session2 = createSession(sf, false, false);

      ClientConsumer consumer = session2.createConsumer(FailoverTestBase.ADDRESS);

      session2.start();

      receiveMessages(consumer, 0, NUM_MESSAGES / 2, true);

      session2.commit();

      consumer.close();

      crash(session2);

      assertFalse(session2.isRollbackOnly());

      consumer = session2.createConsumer(FailoverTestBase.ADDRESS);

      for (int i = NUM_MESSAGES / 2; i < NUM_MESSAGES; i++) {
         ClientMessage message = consumer.receive(1000);

         assertNotNull(message, "expecting message " + i);

         assertMessageBody(i, message);

         assertEquals(i, message.getIntProperty("counter").intValue());

         message.acknowledge();
      }

      session2.commit();

      assertNull(consumer.receiveImmediate());
   }

   @Test
   @Timeout(120)
   public void testXAMessagesSentSoRollbackOnEnd() throws Exception {
      createSessionFactory();

      ClientSession session = createSession(sf, true, false, false);

      Xid xid = new XidImpl("uhuhuhu".getBytes(), 126512, "auhsduashd".getBytes());

      session.createQueue(QueueConfiguration.of(FailoverTestBase.ADDRESS));

      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      session.start(xid, XAResource.TMNOFLAGS);

      sendMessagesSomeDurable(session, producer);

      crash(session);

      try {
         session.end(xid, XAResource.TMSUCCESS);

         fail("Should throw exception");
      } catch (XAException e) {
         assertEquals(XAException.XAER_RMFAIL, e.errorCode);
      }

      ClientConsumer consumer = session.createConsumer(FailoverTestBase.ADDRESS);

      session.start();

      ClientMessage message = consumer.receiveImmediate();

      assertNull(message);
   }

   @Test
   @Timeout(120)
   //start a tx but sending messages after crash
   public void testXAMessagesSentSoRollbackOnEnd2() throws Exception {
      createSessionFactory();

      ClientSession session = createSession(sf, true, false, false);

      Xid xid = new XidImpl("uhuhuhu".getBytes(), 126512, "auhsduashd".getBytes());

      session.createQueue(QueueConfiguration.of(FailoverTestBase.ADDRESS));

      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      session.start(xid, XAResource.TMNOFLAGS);

      crash(session);

      // sendMessagesSomeDurable(session, producer);

      producer.send(createMessage(session, 1, true));

      try {
         session.end(xid, XAResource.TMSUCCESS);

         fail("Should throw exception");
      } catch (XAException e) {
         //         Assert.assertEquals(XAException.XAER_NOTA, e.errorCode);
      }

      ClientConsumer consumer = session.createConsumer(FailoverTestBase.ADDRESS);

      session.start();

      ClientMessage message = consumer.receiveImmediate();

      assertNull(message);
   }

   @Test
   @Timeout(120)
   public void testXAMessagesSentSoRollbackOnPrepare() throws Exception {
      createSessionFactory();

      final ClientSession session = createSession(sf, true, false, false);

      Xid xid = new XidImpl("uhuhuhu".getBytes(), 126512, "auhsduashd".getBytes());

      session.createQueue(QueueConfiguration.of(FailoverTestBase.ADDRESS));

      final ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      session.start(xid, XAResource.TMNOFLAGS);

      sendMessagesSomeDurable(session, producer);

      session.end(xid, XAResource.TMSUCCESS);

      crash(session);

      try {
         session.prepare(xid);

         fail("Should throw exception");
      } catch (XAException e) {
         assertEquals(XAException.XAER_RMFAIL, e.errorCode);
      }

      ClientConsumer consumer = session.createConsumer(FailoverTestBase.ADDRESS);

      session.start();

      ClientMessage message = consumer.receiveImmediate();

      assertNull(message);

      producer.close();
      consumer.close();
   }

   // This might happen if 1PC optimisation kicks in
   @Test
   @Timeout(120)
   public void testXAMessagesSentSoRollbackOnCommit() throws Exception {
      createSessionFactory();

      ClientSession session = createSession(sf, true, false, false);

      Xid xid = new XidImpl("uhuhuhu".getBytes(), 126512, "auhsduashd".getBytes());

      session.createQueue(QueueConfiguration.of(FailoverTestBase.ADDRESS));

      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      session.start(xid, XAResource.TMNOFLAGS);

      sendMessagesSomeDurable(session, producer);

      session.end(xid, XAResource.TMSUCCESS);

      crash(session);

      try {
         session.commit(xid, false);

         fail("Should throw exception");
      } catch (XAException e) {
         assertEquals(XAException.XAER_NOTA, e.errorCode);
      }

      ClientConsumer consumer = session.createConsumer(FailoverTestBase.ADDRESS);

      session.start();

      ClientMessage message = consumer.receiveImmediate();

      assertNull(message);
   }

   @Test
   @Timeout(120)
   public void testXAMessagesNotSentSoNoRollbackOnCommit() throws Exception {
      createSessionFactory();

      ClientSession session = createSession(sf, true, false, false);

      Xid xid = new XidImpl("uhuhuhu".getBytes(), 126512, "auhsduashd".getBytes());

      session.createQueue(QueueConfiguration.of(FailoverTestBase.ADDRESS));

      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      session.start(xid, XAResource.TMNOFLAGS);

      sendMessagesSomeDurable(session, producer);

      session.end(xid, XAResource.TMSUCCESS);

      session.prepare(xid);

      session.commit(xid, false);

      crash(session);

      ClientConsumer consumer = session.createConsumer(FailoverTestBase.ADDRESS);

      session.start();

      Xid xid2 = new XidImpl("tfytftyf".getBytes(), 54654, "iohiuohiuhgiu".getBytes());

      session.start(xid2, XAResource.TMNOFLAGS);

      receiveDurableMessages(consumer);

      session.end(xid2, XAResource.TMSUCCESS);

      session.prepare(xid2);

      session.commit(xid2, false);
   }

   @Test
   @Timeout(120)
   public void testXAMessagesConsumedSoRollbackOnEnd() throws Exception {
      createSessionFactory();

      ClientSession session1 = createSessionAndQueue();

      ClientProducer producer = session1.createProducer(FailoverTestBase.ADDRESS);

      sendMessagesSomeDurable(session1, producer);

      session1.commit();

      ClientSession session2 = createSession(sf, true, false, false);

      ClientConsumer consumer = session2.createConsumer(FailoverTestBase.ADDRESS);

      session2.start();

      Xid xid = new XidImpl("uhuhuhu".getBytes(), 126512, "auhsduashd".getBytes());

      session2.start(xid, XAResource.TMNOFLAGS);

      receiveMessages(consumer);

      crash(session2);

      try {
         session2.end(xid, XAResource.TMSUCCESS);

         fail("Should throw exception");
      } catch (XAException e) {
         assertEquals(XAException.XAER_RMFAIL, e.errorCode);
      }
   }

   @Test
   @Timeout(120)
   public void testXAMessagesConsumedSoRollbackOnEnd2() throws Exception {
      createSessionFactory();

      ClientSession session1 = createSessionAndQueue();

      ClientProducer producer = session1.createProducer(FailoverTestBase.ADDRESS);

      for (int i = 0; i < NUM_MESSAGES; i++) {
         // some are durable, some are not!
         producer.send(createMessage(session1, i, true));
      }

      session1.commit();

      ClientSession session2 = createSession(sf, true, false, false);

      ClientConsumer consumer = session2.createConsumer(FailoverTestBase.ADDRESS);

      session2.start();

      Xid xid = new XidImpl("uhuhuhu".getBytes(), 126512, "auhsduashd".getBytes());

      session2.start(xid, XAResource.TMNOFLAGS);

      crash(session2);

      receiveMessages(consumer);

      try {
         session2.end(xid, XAResource.TMSUCCESS);

         fail("Should throw exception");
      } catch (XAException e) {
      }

      // Since the end was not accepted, the messages should be redelivered
      receiveMessages(consumer);
   }

   @Test
   @Timeout(120)
   public void testXAMessagesConsumedSoRollbackOnPrepare() throws Exception {
      createSessionFactory();

      ClientSession session1 = createSessionAndQueue();

      ClientProducer producer = session1.createProducer(FailoverTestBase.ADDRESS);

      sendMessagesSomeDurable(session1, producer);

      session1.commit();

      ClientSession session2 = createSession(sf, true, false, false);

      ClientConsumer consumer = session2.createConsumer(FailoverTestBase.ADDRESS);

      session2.start();

      Xid xid = new XidImpl("uhuhuhu".getBytes(), 126512, "auhsduashd".getBytes());

      session2.start(xid, XAResource.TMNOFLAGS);

      receiveMessages(consumer);

      session2.end(xid, XAResource.TMSUCCESS);

      crash(session2);

      try {
         session2.prepare(xid);

         fail("Should throw exception");
      } catch (XAException e) {
         assertEquals(XAException.XAER_RMFAIL, e.errorCode);
      }
   }

   // 1PC optimisation
   @Test
   @Timeout(120)
   public void testXAMessagesConsumedSoRollbackOnCommit() throws Exception {
      createSessionFactory();
      ClientSession session1 = createSessionAndQueue();

      ClientProducer producer = session1.createProducer(FailoverTestBase.ADDRESS);

      sendMessagesSomeDurable(session1, producer);

      session1.commit();

      ClientSession session2 = createSession(sf, true, false, false);

      ClientConsumer consumer = session2.createConsumer(FailoverTestBase.ADDRESS);

      session2.start();

      Xid xid = new XidImpl("uhuhuhu".getBytes(), 126512, "auhsduashd".getBytes());

      session2.start(xid, XAResource.TMNOFLAGS);

      receiveMessages(consumer);

      session2.end(xid, XAResource.TMSUCCESS);

      // session2.prepare(xid);

      crash(session2);

      try {
         session2.commit(xid, false);

         fail("Should throw exception");
      } catch (XAException e) {
         // it should be rolled back
         assertEquals(XAException.XAER_NOTA, e.errorCode);
      }

      session1.close();

      session2.close();
   }

   @Test
   @Timeout(120)
   public void testCreateNewFactoryAfterFailover() throws Exception {
      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true);
      sf = createSessionFactoryAndWaitForTopology(locator, 2);

      ClientSession session = sendAndConsume(sf, true);

      crash(true, session);

      session.close();

      long timeout;
      timeout = System.currentTimeMillis() + 5000;
      while (timeout > System.currentTimeMillis()) {
         try {
            createClientSessionFactory();
            break;
         } catch (Exception e) {
            // retrying
            Thread.sleep(100);
         }
      }

      session = sendAndConsume(sf, false);
   }

   @Test
   @Timeout(120)
   public void testFailoverMultipleSessionsWithConsumers() throws Exception {
      createSessionFactory();

      final int numSessions = 5;

      final int numConsumersPerSession = 5;

      Map<ClientSession, List<ClientConsumer>> sessionConsumerMap = new HashMap<>();

      for (int i = 0; i < numSessions; i++) {
         ClientSession session = createSession(sf, true, true);

         List<ClientConsumer> consumers = new ArrayList<>();

         for (int j = 0; j < numConsumersPerSession; j++) {
            SimpleString queueName = SimpleString.of("queue" + i + "-" + j);

            session.createQueue(QueueConfiguration.of(queueName).setAddress(FailoverTestBase.ADDRESS));

            ClientConsumer consumer = session.createConsumer(queueName);

            consumers.add(consumer);
         }

         sessionConsumerMap.put(session, consumers);
      }

      ClientSession sendSession = createSession(sf, true, true);

      ClientProducer producer = sendSession.createProducer(FailoverTestBase.ADDRESS);

      sendMessages(sendSession, producer, NUM_MESSAGES);

      Set<ClientSession> sessionSet = sessionConsumerMap.keySet();
      ClientSession[] sessions = new ClientSession[sessionSet.size()];
      sessionSet.toArray(sessions);
      crash(sessions);

      for (ClientSession session : sessionConsumerMap.keySet()) {
         session.start();
      }

      for (List<ClientConsumer> consumerList : sessionConsumerMap.values()) {
         for (ClientConsumer consumer : consumerList) {
            receiveMessages(consumer);
         }
      }
   }

   /**
    * Browser will get reset to beginning after failover
    */
   @Test
   @Timeout(120)
   public void testFailWithBrowser() throws Exception {
      createSessionFactory();
      ClientSession session = createSession(sf, true, true);

      session.createQueue(QueueConfiguration.of(FailoverTestBase.ADDRESS));

      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      sendMessagesSomeDurable(session, producer);

      ClientConsumer consumer = session.createConsumer(FailoverTestBase.ADDRESS, true);

      session.start();

      receiveMessages(consumer, 0, NUM_MESSAGES, false);

      crash(session);

      receiveDurableMessages(consumer);
   }


   @Test
   @Timeout(120)
   public void testFailThenReceiveMoreMessagesAfterFailover() throws Exception {
      createSessionFactory();

      ClientSession session = createSession(sf, true, true);

      session.createQueue(QueueConfiguration.of(FailoverTestBase.ADDRESS));

      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      sendMessagesSomeDurable(session, producer);

      ClientConsumer consumer = session.createConsumer(FailoverTestBase.ADDRESS);

      session.start();

      // Receive MSGs but don't ack!
      for (int i = 0; i < NUM_MESSAGES; i++) {
         ClientMessage message = consumer.receive(1000);

         assertNotNull(message);

         assertMessageBody(i, message);

         assertEquals(i, message.getIntProperty("counter").intValue());
      }

      crash(session);

      // Should get the same ones after failover since we didn't ack

      receiveDurableMessages(consumer);
   }

   @Test
   @Timeout(120)
   public void testFailThenReceiveMoreMessagesAfterFailover2() throws Exception {
      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true).setReconnectAttempts(300).setRetryInterval(100);

      sf = createSessionFactoryAndWaitForTopology(locator, 2);

      ClientSession session = createSession(sf, true, true, 0);

      session.createQueue(QueueConfiguration.of(FailoverTestBase.ADDRESS));

      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      sendMessagesSomeDurable(session, producer);

      ClientConsumer consumer = session.createConsumer(FailoverTestBase.ADDRESS);

      session.start();
      receiveMessages(consumer);

      crash(session);

      // Send some more

      for (int i = NUM_MESSAGES; i < NUM_MESSAGES * 2; i++) {
         producer.send(createMessage(session, i, isDurable(i)));
      }
      receiveMessages(consumer, NUM_MESSAGES, NUM_MESSAGES * 2, true);
   }

   @Test
   @Timeout(120)
   public void testMultipleSessionFailover() throws Exception {
      final String address = "TEST";
      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true).setReconnectAttempts(300).setRetryInterval(100);

      sf = createSessionFactoryAndWaitForTopology(locator, 2);

      ClientSession session1 = createSession(sf, true, true, 0);
      ClientSession session2 = createSession(sf, true, true, 0);

      backupServer.addInterceptor(
         new Interceptor() {
            private int index = 0;

            @Override
            public boolean intercept(Packet packet, RemotingConnection connection) throws ActiveMQException {
               if (packet.getType() == PacketImpl.CREATESESSION) {
                  index++;
                  if (index == 2 || index == 3) {
                     Channel sessionChannel = ((RemotingConnectionImpl) connection).getChannel(ChannelImpl.CHANNEL_ID.SESSION.id, -1);
                     sessionChannel.send(new ActiveMQExceptionMessage(new ActiveMQInternalErrorException()));
                     return false;
                  }
               }
               return true;
            }
         });

      session1.start();
      session2.start();

      crash(session1, session2);

      session1.createQueue(QueueConfiguration.of(address).setAddress(address));

      ClientProducer clientProducer = session1.createProducer(address);
      clientProducer.send(session1.createMessage(false));

      ClientConsumer clientConsumer = session2.createConsumer(address);
      ClientMessage message = clientConsumer.receive(3000);
      assertNotNull(message);
   }

   @Test
   @Timeout(120)
   public void testChannelStateDuringFailover() throws Exception {
      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true).setReconnectAttempts(300).setRetryInterval(100);

      sf = createSessionFactoryAndWaitForTopology(locator, 2);

      final int reconnectFailures = 3;
      final AtomicInteger reconnectRetries = new AtomicInteger(0);
      final AtomicBoolean channelLockedDuringFailover = new AtomicBoolean(true);

      ClientSession session = createSession(sf, true, true, 0);

      backupServer.addInterceptor((packet, connection) -> {
         if (packet.getType() == PacketImpl.CREATESESSION) {
            if (reconnectRetries.getAndIncrement() < reconnectFailures) {
               Channel sessionChannel = ((RemotingConnectionImpl)connection).getChannel(ChannelImpl.CHANNEL_ID.SESSION.id, -1);
               sessionChannel.send(new ActiveMQExceptionMessage(new ActiveMQInternalErrorException()));
               return false;
            }

            ActiveMQSessionContext sessionContext = (ActiveMQSessionContext)((ClientSessionInternal)session).getSessionContext();
            channelLockedDuringFailover.compareAndSet(true, sessionContext.getSessionChannel().isLocked());
         }
         return true;
      });

      session.start();

      crash(session);

      assertTrue(channelLockedDuringFailover.get());
      assertEquals(reconnectFailures + 1, reconnectRetries.get());
   }

   @Test
   @Timeout(120)
   public void testForceBlockingReturn() throws Exception {
      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true).setReconnectAttempts(300).setRetryInterval(100);

      createClientSessionFactory();

      // Add an interceptor to delay the send method so we can get time to cause failover before it returns
      DelayInterceptor interceptor = new DelayInterceptor();
      primaryServer.getServer().getRemotingService().addIncomingInterceptor(interceptor);

      final ClientSession session = createSession(sf, true, true, 0);

      session.createQueue(QueueConfiguration.of(FailoverTestBase.ADDRESS));

      final ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      class Sender extends Thread {

         @Override
         public void run() {
            ClientMessage message = session.createMessage(true);

            message.getBodyBuffer().writeString("message");

            try {
               producer.send(message);
            } catch (ActiveMQException e1) {
               this.e = e1;
            }
         }

         volatile ActiveMQException e;
      }

      Sender sender = new Sender();

      sender.start();

      //if server crash too early,
      //sender will directly send to backup. so
      //need some waiting here.
      assertTrue(interceptor.await());

      crash(session);

      sender.join();

      assertNotNull(sender.e);

      assertNotNull(sender.e.getCause());

      assertEquals(ActiveMQExceptionType.UNBLOCKED, sender.e.getType());

      assertEquals(ActiveMQExceptionType.DISCONNECTED, ((ActiveMQException) sender.e.getCause()).getType());

      session.close();
   }

   @Test
   @Timeout(120)
   public void testCommitOccurredUnblockedAndResendNoDuplicates() throws Exception {
      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setReconnectAttempts(300).setRetryInterval(100).setBlockOnAcknowledge(true);

      sf = createSessionFactoryAndWaitForTopology(locator, 2);

      final ClientSession session = createSession(sf, false, false);

      session.createQueue(QueueConfiguration.of(FailoverTestBase.ADDRESS));

      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      String txID = "my-tx-id";

      for (int i = 0; i < NUM_MESSAGES; i++) {
         ClientMessage message = session.createMessage(true);

         if (i == 0) {
            // Only need to add it on one message per tx
            message.putStringProperty(Message.HDR_DUPLICATE_DETECTION_ID, SimpleString.of(txID));
         }

         setBody(i, message);

         message.putIntProperty("counter", i);

         producer.send(message);
      }

      class Committer extends Thread {

         DelayInterceptor2 interceptor = new DelayInterceptor2();

         @Override
         public void run() {
            try {
               sf.getServerLocator().addIncomingInterceptor(interceptor);

               session.commit();
            } catch (ActiveMQTransactionRolledBackException trbe) {
               // Ok - now we retry the commit after removing the interceptor

               sf.getServerLocator().removeIncomingInterceptor(interceptor);

               try {
                  session.commit();

                  failed = false;
               } catch (ActiveMQException e2) {
                  throw new RuntimeException(e2);
               }
            } catch (ActiveMQTransactionOutcomeUnknownException toue) {
               // Ok - now we retry the commit after removing the interceptor

               sf.getServerLocator().removeIncomingInterceptor(interceptor);

               try {
                  session.commit();

                  failed = false;
               } catch (ActiveMQException e2) {
                  throw new RuntimeException(e2);
               }
            } catch (ActiveMQException e) {
               //ignore
            }
         }

         volatile boolean failed = true;
      }

      Committer committer = new Committer();

      // Commit will occur, but response will never get back, connection is failed, and commit
      // should be unblocked with transaction rolled back

      committer.start();

      // Wait for the commit to occur and the response to be discarded
      assertTrue(committer.interceptor.await());

      crash(session);

      committer.join();

      assertFalse(committer.failed, "second attempt succeed?");

      session.close();

      ClientSession session2 = createSession(sf, false, false);

      producer = session2.createProducer(FailoverTestBase.ADDRESS);

      // We now try and resend the messages since we get a transaction rolled back exception
      // but the commit actually succeeded, duplicate detection should kick in and prevent dups

      for (int i = 0; i < NUM_MESSAGES; i++) {
         ClientMessage message = session2.createMessage(true);

         if (i == 0) {
            // Only need to add it on one message per tx
            message.putStringProperty(Message.HDR_DUPLICATE_DETECTION_ID, SimpleString.of(txID));
         }

         setBody(i, message);

         message.putIntProperty("counter", i);

         producer.send(message);
      }

      try {
         session2.commit();
         fail("expecting DUPLICATE_ID_REJECTED exception");
      } catch (ActiveMQDuplicateIdException dide) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }

      ClientConsumer consumer = session2.createConsumer(FailoverTestBase.ADDRESS);

      session2.start();

      receiveMessages(consumer);

      ClientMessage message = consumer.receiveImmediate();

      assertNull(message);
   }

   @Test
   @Timeout(120)
   public void testCommitDidNotOccurUnblockedAndResend() throws Exception {
      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true).setReconnectAttempts(300).setRetryInterval(100);

      sf = createSessionFactoryAndWaitForTopology(locator, 2);

      final ClientSession session = createSession(sf, false, false);

      session.createQueue(QueueConfiguration.of(FailoverTestBase.ADDRESS));

      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);
      sendMessages(session, producer, NUM_MESSAGES);

      class Committer extends Thread {

         @Override
         public void run() {
            Interceptor interceptor = new DelayInterceptor3();

            try {
               primaryServer.addInterceptor(interceptor);

               session.commit();
            } catch (ActiveMQTransactionRolledBackException trbe) {
               // Ok - now we retry the commit after removing the interceptor

               primaryServer.removeInterceptor(interceptor);

               try {
                  session.commit();

                  failed = false;
               } catch (ActiveMQException e2) {
               }
            } catch (ActiveMQTransactionOutcomeUnknownException toue) {
               // Ok - now we retry the commit after removing the interceptor

               primaryServer.removeInterceptor(interceptor);

               try {
                  session.commit();

                  failed = false;
               } catch (ActiveMQException e2) {
               }
            } catch (ActiveMQException e) {
               //ignore
            }
         }

         volatile boolean failed = true;
      }

      Committer committer = new Committer();

      committer.start();

      crash(session);

      committer.join();

      assertFalse(committer.failed, "commiter failed should be false");

      session.close();

      ClientSession session2 = createSession(sf, false, false);

      producer = session2.createProducer(FailoverTestBase.ADDRESS);

      // We now try and resend the messages since we get a transaction rolled back exception
      sendMessages(session2, producer, NUM_MESSAGES);

      session2.commit();

      ClientConsumer consumer = session2.createConsumer(FailoverTestBase.ADDRESS);

      session2.start();

      receiveMessages(consumer);

      ClientMessage message = consumer.receiveImmediate();

      assertNull(message, "expecting null message");
   }

   @Test
   @Timeout(120)
   public void testBackupServerNotRemoved() throws Exception {
      if (!(backupServer.getServer().getHAPolicy() instanceof SharedStoreBackupPolicy)) {
         return;
      }
      createSessionFactory();

      ClientSession session = sendAndConsume(sf, true);
      CountDownSessionFailureListener listener = new CountDownSessionFailureListener(session);

      session.addFailureListener(listener);

      backupServer.stop();

      primaryServer.crash();

      // To reload security or other settings that are read during startup
      beforeRestart(backupServer);

      backupServer.start();

      assertTrue(listener.getLatch().await(5, TimeUnit.SECONDS), "session failure listener");

      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      ClientMessage message = session.createMessage(true);

      setBody(0, message);

      producer.send(message);
   }

   @Test
   @Timeout(120)
   public void testPrimaryAndBackupPrimaryComesBack() throws Exception {
      createSessionFactory();
      final CountDownLatch latch = new CountDownLatch(1);

      ClientSession session = sendAndConsume(sf, true);

      session.addFailureListener(new CountDownSessionFailureListener(latch, session));

      backupServer.stop();

      primaryServer.crash();

      beforeRestart(primaryServer);

      // To reload security or other settings that are read during startup
      beforeRestart(primaryServer);

      primaryServer.start();

      assertTrue(latch.await(5, TimeUnit.SECONDS));

      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      ClientMessage message = session.createMessage(true);

      setBody(0, message);

      producer.send(message);
   }

   @Test
   @Timeout(120)
   public void testPrimaryAndBackupPrimaryComesBackNewFactory() throws Exception {
      createSessionFactory();

      final CountDownLatch latch = new CountDownLatch(1);

      ClientSession session = sendAndConsume(sf, true);

      session.addFailureListener(new CountDownSessionFailureListener(latch, session));

      backupServer.stop();

      primaryServer.crash();

      // To reload security or other settings that are read during startup
      beforeRestart(primaryServer);

      primaryServer.start();

      assertTrue(latch.await(5, TimeUnit.SECONDS));

      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      ClientMessage message = session.createMessage(true);

      setBody(0, message);

      producer.send(message);

      session.close();

      sf.close();

      createClientSessionFactory();

      session = createSession(sf);

      ClientConsumer cc = session.createConsumer(FailoverTestBase.ADDRESS);

      session.start();

      ClientMessage cm = cc.receive(5000);

      assertNotNull(cm);

      assertEquals("message0", cm.getBodyBuffer().readString());
   }

   @Test
   @Timeout(120)
   public void testPrimaryAndBackupBackupComesBackNewFactory() throws Exception {
      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setReconnectAttempts(300).setRetryInterval(100);

      sf = createSessionFactoryAndWaitForTopology(locator, 2);

      ClientSession session = sendAndConsume(sf, true);

      CountDownSessionFailureListener listener = new CountDownSessionFailureListener(session);

      session.addFailureListener(listener);

      backupServer.stop();

      primaryServer.crash();

      // To reload security or other settings that are read during startup
      beforeRestart(backupServer);

      if (!backupServer.getServer().getHAPolicy().isSharedStore()) {
         // XXX
         // this test would not make sense in the remote replication use case, without the following
         backupServer.getServer().setHAPolicy(new SharedStorePrimaryPolicy());
      }

      backupServer.start();

      assertTrue(listener.getLatch().await(5, TimeUnit.SECONDS), "session failure listener");

      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      ClientMessage message = session.createMessage(true);

      setBody(0, message);

      producer.send(message);

      session.close();

      sf.close();

      createClientSessionFactory();

      session = createSession(sf);

      ClientConsumer cc = session.createConsumer(FailoverTestBase.ADDRESS);

      session.start();

      ClientMessage cm = cc.receive(5000);

      assertNotNull(cm);

      assertEquals("message0", cm.getBodyBuffer().readString());
   }

   @Test
   @Timeout(120)
   public void testBackupConnections() throws Exception {
      assumeTrue(backupServer.getServer().getHAPolicy().isBackup());

      createSessionFactory();

      CountDownLatch latch = new CountDownLatch(1);
      sf.addFailoverListener(eventType -> {
         if (eventType == FailoverEventType.FAILOVER_COMPLETED) {
            latch.countDown();
         }
      });

      BackupManager backupManager = ((ActiveMQServerImpl)backupServer.getServer()).getBackupManager();
      ClusterController backupClusterController = backupServer.getServer().getClusterManager().getClusterController();
      ClusterConnectionImpl backupClusterConnection = (ClusterConnectionImpl)backupServer.getServer().getClusterManager().getClusterConnections().stream().findFirst().get();

      for (BackupManager.BackupConnector backupConnector : backupManager.getBackupConnectors()) {
         for (ClientSessionFactoryInternal factory : ((ServerLocatorImpl)backupConnector.getBackupServerLocator()).getFactories()) {
            assertNotNull(factory.getConnection());
         }
      }

      for (ClientSessionFactoryInternal factory : ((ServerLocatorImpl)backupClusterController.getDefaultLocator()).getFactories()) {
         assertNotNull(factory.getConnection());
      }

      assertNull(backupClusterConnection.getServerLocator());

      assertNotNull(sf.getConnection());

      crash();

      latch.await();

      for (BackupManager.BackupConnector backupConnector : backupManager.getBackupConnectors()) {
         assertNull(backupConnector.getBackupServerLocator());
      }

      for (ClientSessionFactoryInternal factory : ((ServerLocatorImpl)backupServer.getServer().getClusterManager().getClusterController().getDefaultLocator()).getFactories()) {
         assertNull(factory.getConnection());
      }

      for (ClientSessionFactoryInternal factory : ((ServerLocatorImpl)backupClusterConnection.getServerLocator()).getFactories()) {
         assertNull(factory.getConnection());
      }

      assertNotNull(sf.getConnection());
   }



}
