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
package org.apache.activemq.artemis.tests.integration.xa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.MessageHandler;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.StoreConfiguration;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.transaction.impl.XidImpl;
import org.apache.activemq.artemis.ra.ActiveMQRAXAResource;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.UUIDGenerator;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

@ExtendWith(ParameterizedTestExtension.class)
public class BasicXaTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final Map<String, AddressSettings> addressSettings = new HashMap<>();

   private ActiveMQServer messagingService;

   private ClientSession clientSession;

   private ClientSessionFactory sessionFactory;

   private Configuration configuration;

   private final SimpleString atestq = SimpleString.of("BasicXaTestq");

   private ServerLocator locator;

   private StoreConfiguration.StoreType storeType;

   public BasicXaTest(StoreConfiguration.StoreType storeType) {
      this.storeType = storeType;
   }

   @Parameters(name = "storeType={0}")
   public static Collection<Object[]> data() {
      Object[][] params = new Object[][]{{StoreConfiguration.StoreType.FILE}, {StoreConfiguration.StoreType.DATABASE}};
      return Arrays.asList(params);
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      addressSettings.clear();

      if (storeType == StoreConfiguration.StoreType.DATABASE) {
         configuration = createDefaultJDBCConfig(true);
      } else {
         configuration = createDefaultNettyConfig();
      }

      messagingService = createServer(true, configuration, -1, -1, addressSettings);

      // start the server
      messagingService.start();

      locator = createInVMNonHALocator();
      sessionFactory = createSessionFactory(locator);

      clientSession = addClientSession(sessionFactory.createSession(true, false, false));

      clientSession.createQueue(QueueConfiguration.of(atestq));
   }

   @TestTemplate
   public void testSendWithoutXID() throws Exception {
      // Since both resources have same RM, TM will probably use 1PC optimization

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory factory = createSessionFactory(locator);

      ClientSession session = addClientSession(factory.createSession(true, false, false));

      session.createQueue(QueueConfiguration.of("Test").setRoutingType(RoutingType.ANYCAST));

      ClientProducer prod = session.createProducer("Test");

      prod.send(session.createMessage(true));

      session.start();

      ClientConsumer cons = session.createConsumer("Test");

      assertNotNull(cons.receiveImmediate(), "Send went through an invalid XA Session");
   }

   @TestTemplate
   public void testACKWithoutXID() throws Exception {
      // Since both resources have same RM, TM will probably use 1PC optimization

      ClientSessionFactory factory = createSessionFactory(locator);

      ClientSession session = addClientSession(factory.createSession(false, true, true));

      session.createQueue(QueueConfiguration.of("Test").setRoutingType(RoutingType.ANYCAST));

      ClientProducer prod = session.createProducer("Test");

      prod.send(session.createMessage(true));

      session.close();

      session = addClientSession(factory.createSession(true, false, false));

      session.start();

      ClientConsumer cons = session.createConsumer("Test");

      ClientMessage msg = cons.receive(5000);

      assertNotNull(msg);

      msg.acknowledge();

      session.close();

      session = addClientSession(factory.createSession(false, false, false));

      session.start();

      cons = session.createConsumer("Test");

      msg = cons.receiveImmediate();

      assertNull(msg, "Acknowledge went through invalid XA Session");
   }

   @TestTemplate
   public void testIsSameRM() throws Exception {

      try (ServerLocator locator = createNettyNonHALocator();
           ServerLocator locator2 = createNettyNonHALocator()) {
         ClientSessionFactory nettyFactory = createSessionFactory(locator);
         ClientSessionFactory nettyFactory2 = createSessionFactory(locator2);
         ClientSession session1 = nettyFactory.createSession(true, false, false);
         ClientSession session2 = nettyFactory2.createSession(true, false, false);
         assertTrue(session1.isSameRM(session2));
         ActiveMQRAXAResource activeMQRAXAResource = new ActiveMQRAXAResource(null, session2);
         assertTrue(session1.isSameRM(activeMQRAXAResource));
      }
   }

   @TestTemplate
   public void testXAInterleaveResourceSuspendWorkCommit() throws Exception {
      Xid xid = newXID();
      Xid xid2 = newXID();
      ClientProducer clientProducer = clientSession.createProducer(atestq);
      ClientSession recSession = sessionFactory.createSession();
      recSession.start();
      ClientConsumer clientConsumer = recSession.createConsumer(atestq);
      ClientMessage m1 = createTextMessage(clientSession, "m1");
      ClientMessage m2 = createTextMessage(clientSession, "m2");
      clientSession.start(xid, XAResource.TMNOFLAGS);
      clientProducer.send(m1);
      clientSession.end(xid, XAResource.TMSUSPEND);
      clientSession.start(xid2, XAResource.TMNOFLAGS);
      clientProducer.send(m2);
      clientSession.end(xid, XAResource.TMSUCCESS);

      // this is calling resume twice
      // the TM may eventually do it, and if the state is ACTIVE, the
      // broker should just ignore the call and keep going
      clientSession.end(xid, XAResource.TMSUCCESS);

      clientSession.commit(xid, true);
      ClientMessage message = clientConsumer.receiveImmediate();
      assertNotNull(message);
      message = clientConsumer.receiveImmediate();
      assertNull(message);
      clientSession.end(xid2, XAResource.TMSUCCESS);
      clientSession.commit(xid2, true);
      message = clientConsumer.receiveImmediate();
      assertNotNull(message);
   }

   @TestTemplate
   public void testInterleavedXaTxRollbackSuccess() throws Exception {
      Xid xid1 = newXID();
      Xid xid2 = newXID();
      ClientSession producerSession = sessionFactory.createSession();
      ClientProducer producer = producerSession.createProducer(atestq);
      ClientConsumer consumer = clientSession.createConsumer(atestq);
      clientSession.start();
      Queue testQueue = messagingService.locateQueue(atestq);

      clientSession.start(xid1, XAResource.TMNOFLAGS);
      ClientMessage message = consumer.receiveImmediate();
      assertNull(message);
      clientSession.end(xid1, XAResource.TMSUCCESS);

      clientSession.start(xid2, XAResource.TMNOFLAGS);

      clientSession.rollback(xid1);

      // send message outside any tx
      producer.send(createTextMessage(clientSession, "m1"));
      Wait.assertEquals(1, () -> testQueue.getMessageCount());

      message = consumer.receiveImmediate();
      assertNotNull(message);
      assertEquals(1, testQueue.getDeliveringCount());
      message.acknowledge();

      clientSession.end(xid2, XAResource.TMSUCCESS);
      clientSession.prepare(xid2);
      clientSession.commit(xid2, false);
      assertEquals(1, testQueue.getMessagesAcknowledged());
      assertEquals(0, testQueue.getDeliveringCount());
      assertEquals(0, testQueue.getMessageCount());
   }

   @TestTemplate
   public void testInterleavedXaTxRollbackFailed() throws Exception {
      Xid xid1 = newXID();
      Xid xid2 = newXID();
      ClientSession producerSession = sessionFactory.createSession();
      ClientProducer producer = producerSession.createProducer(atestq);
      ClientConsumer consumer = clientSession.createConsumer(atestq);
      clientSession.start();
      clientSession.setTransactionTimeout(1);
      Queue testQueue = messagingService.locateQueue(atestq);

      clientSession.start(xid1, XAResource.TMNOFLAGS);
      ClientMessage message = consumer.receive(2000);
      // TX will time out here
      assertNull(message);

      clientSession.start(xid2, XAResource.TMNOFLAGS);

      try {
         clientSession.rollback(xid1);
         fail("This rollback should have failed due to the time out");
      } catch (XAException e) {
         assertEquals(XAException.XAER_NOTA, e.errorCode);
      }

      // send message outside any tx
      producer.send(createTextMessage(clientSession, "m1"));
      Wait.assertEquals(1, () -> testQueue.getMessageCount());

      message = consumer.receiveImmediate();
      assertNotNull(message);
      assertEquals(1, testQueue.getDeliveringCount());
      message.acknowledge();

      clientSession.end(xid2, XAResource.TMSUCCESS);
      clientSession.rollback(xid2);
      assertEquals(0, testQueue.getMessagesAcknowledged());
      assertEquals(0, testQueue.getDeliveringCount());
      assertEquals(1, testQueue.getMessageCount());
   }

   @TestTemplate
   public void testRestartWithTXPrepareDeletedQueue() throws Exception {

      ClientSession clientSession2 = sessionFactory.createSession(false, true, true);
      ClientProducer clientProducer = clientSession2.createProducer(atestq);
      ClientMessage m1 = createTextMessage(clientSession2, "m1");
      clientProducer.send(m1);

      Xid xid = newXID();
      clientSession.start(xid, XAResource.TMNOFLAGS);
      clientSession.start();

      ClientConsumer clientConsumer = clientSession.createConsumer(atestq);

      ClientMessage message = clientConsumer.receive(5000);
      message.acknowledge();
      clientSession.end(xid, XAResource.TMSUCCESS);
      clientSession.prepare(xid);

      Queue queueAtestQ = messagingService.locateQueue(atestq);
      assertNotNull(queueAtestQ);

      clientSession.getSessionFactory().getConnection().destroy();

      Wait.assertEquals(0, queueAtestQ::getConsumerCount);

      messagingService.destroyQueue(atestq);

      messagingService.stop();
      messagingService.start();

      messagingService.waitForActivation(10, TimeUnit.SECONDS);

      assertTrue(messagingService.isStarted());
   }

   @TestTemplate
   public void testXAInterleaveResourceRollbackAfterPrepare() throws Exception {
      Xid xid = newXID();
      Xid xid2 = newXID();
      Xid xid3 = newXID();
      ClientProducer clientProducer = clientSession.createProducer(atestq);
      ClientConsumer clientConsumer = clientSession.createConsumer(atestq);
      ClientMessage m1 = createTextMessage(clientSession, "m1");
      clientSession.start(xid, XAResource.TMNOFLAGS);
      clientProducer.send(m1);
      clientSession.end(xid, XAResource.TMSUCCESS);
      clientSession.prepare(xid);
      clientSession.commit(xid, false);
      clientSession.start();
      clientSession.start(xid2, XAResource.TMNOFLAGS);
      ClientMessage m2 = clientConsumer.receiveImmediate();
      assertNotNull(m2);
      clientSession.end(xid2, XAResource.TMSUCCESS);
      clientSession.prepare(xid2);
      clientSession.rollback(xid2);

      clientSession.start(xid3, XAResource.TMNOFLAGS);
      m2 = clientConsumer.receiveImmediate();
      assertNotNull(m2);
      clientSession.end(xid3, XAResource.TMSUCCESS);
      clientSession.prepare(xid3);
      clientSession.commit(xid3, false);
   }

   @TestTemplate
   public void testSendPrepareDoesntRollbackOnClose() throws Exception {
      Xid xid = newXID();

      ClientMessage m1 = createTextMessage(clientSession, "m1");
      ClientMessage m2 = createTextMessage(clientSession, "m2");
      ClientMessage m3 = createTextMessage(clientSession, "m3");
      ClientMessage m4 = createTextMessage(clientSession, "m4");
      ClientProducer clientProducer = clientSession.createProducer(atestq);
      clientSession.start(xid, XAResource.TMNOFLAGS);
      clientProducer.send(m1);
      clientProducer.send(m2);
      clientProducer.send(m3);
      clientProducer.send(m4);
      clientSession.end(xid, XAResource.TMSUCCESS);
      clientSession.prepare(xid);

      clientSession.close();

      clientSession = sessionFactory.createSession(true, false, false);

      logger.debug("committing");

      clientSession.commit(xid, false);
      clientSession.start();
      ClientConsumer clientConsumer = clientSession.createConsumer(atestq);
      ClientMessage m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals("m1", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals("m2", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals("m3", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals("m4", m.getBodyBuffer().readString());
   }

   @TestTemplate
   public void testReceivePrepareDoesntRollbackOnClose() throws Exception {
      Xid xid = newXID();

      ClientSession clientSession2 = sessionFactory.createSession(false, true, true);
      ClientProducer clientProducer = clientSession2.createProducer(atestq);
      ClientMessage m1 = createTextMessage(clientSession2, "m1");
      ClientMessage m2 = createTextMessage(clientSession2, "m2");
      ClientMessage m3 = createTextMessage(clientSession2, "m3");
      ClientMessage m4 = createTextMessage(clientSession2, "m4");
      clientProducer.send(m1);
      clientProducer.send(m2);
      clientProducer.send(m3);
      clientProducer.send(m4);

      clientSession.start(xid, XAResource.TMNOFLAGS);
      clientSession.start();
      ClientConsumer clientConsumer = clientSession.createConsumer(atestq);
      ClientMessage m = clientConsumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      assertEquals("m1", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      assertEquals("m2", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      assertEquals("m3", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      assertEquals("m4", m.getBodyBuffer().readString());
      clientSession.end(xid, XAResource.TMSUCCESS);
      clientSession.prepare(xid);

      clientSession.close();

      clientSession = sessionFactory.createSession(true, false, false);

      clientSession.commit(xid, false);
      clientSession.start();
      clientConsumer = clientSession.createConsumer(atestq);
      m = clientConsumer.receiveImmediate();
      assertNull(m);

      clientSession2.close();

   }

   @TestTemplate
   public void testPrepareError() throws Exception {
      Xid xid = newXID();

      ClientSession clientSession2 = sessionFactory.createSession(false, true, true);
      ClientProducer clientProducer = clientSession2.createProducer(atestq);
      ClientMessage m1 = createTextMessage(clientSession2, "m1");
      ClientMessage m2 = createTextMessage(clientSession2, "m2");
      ClientMessage m3 = createTextMessage(clientSession2, "m3");
      ClientMessage m4 = createTextMessage(clientSession2, "m4");
      clientProducer.send(m1);
      clientProducer.send(m2);
      clientProducer.send(m3);
      clientProducer.send(m4);

      clientSession.start(xid, XAResource.TMNOFLAGS);
      clientSession.start();
      ClientConsumer clientConsumer = clientSession.createConsumer(atestq);
      ClientMessage m = clientConsumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      assertEquals("m1", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      assertEquals("m2", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      assertEquals("m3", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      assertEquals("m4", m.getBodyBuffer().readString());
      clientSession.end(xid, XAResource.TMSUCCESS);

      StorageManager journalStorageManager = messagingService.getStorageManager();

      clientSession.prepare(xid);

      journalStorageManager.getMessageJournal().stop();
      try {
         clientSession.commit(xid, false);
         fail("Exception exptected");
      } catch (XAException e) {
         assertEquals(XAException.XA_RETRY, e.errorCode);
      }
   }


   @TestTemplate
   public void testRollbackError() throws Exception {
      Xid xid = newXID();

      ClientSession clientSession2 = sessionFactory.createSession(false, true, true);
      ClientProducer clientProducer = clientSession2.createProducer(atestq);
      ClientMessage m1 = createTextMessage(clientSession2, "m1");
      ClientMessage m2 = createTextMessage(clientSession2, "m2");
      ClientMessage m3 = createTextMessage(clientSession2, "m3");
      ClientMessage m4 = createTextMessage(clientSession2, "m4");
      clientProducer.send(m1);
      clientProducer.send(m2);
      clientProducer.send(m3);
      clientProducer.send(m4);

      clientSession.start(xid, XAResource.TMNOFLAGS);
      clientSession.start();
      ClientConsumer clientConsumer = clientSession.createConsumer(atestq);
      ClientMessage m = clientConsumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      assertEquals("m1", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      assertEquals("m2", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      assertEquals("m3", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      assertEquals("m4", m.getBodyBuffer().readString());
      clientSession.end(xid, XAResource.TMSUCCESS);

      StorageManager journalStorageManager = messagingService.getStorageManager();

      clientSession.prepare(xid);

      journalStorageManager.getMessageJournal().stop();
      try {
         clientSession.rollback(xid);
         fail("Exception exptected");
      } catch (XAException e) {
         assertEquals(XAException.XAER_RMFAIL, e.errorCode);
      }
   }

   @TestTemplate
   public void testReceiveRollback() throws Exception {
      int numSessions = 100;
      ClientSession clientSession2 = sessionFactory.createSession(false, true, true);
      ClientProducer clientProducer = clientSession2.createProducer(atestq);
      for (int i = 0; i < numSessions; i++) {
         clientProducer.send(createTextMessage(clientSession2, "m" + i));
      }
      ClientSession[] clientSessions = new ClientSession[numSessions];
      ClientConsumer[] clientConsumers = new ClientConsumer[numSessions];
      TxMessageHandler[] handlers = new TxMessageHandler[numSessions];
      CountDownLatch latch = new CountDownLatch(numSessions * AddressSettings.DEFAULT_MAX_DELIVERY_ATTEMPTS);
      for (int i = 0; i < clientSessions.length; i++) {
         clientSessions[i] = sessionFactory.createSession(true, false, false);
         clientConsumers[i] = clientSessions[i].createConsumer(atestq);
         handlers[i] = new TxMessageHandler(clientSessions[i], latch);
         clientConsumers[i].setMessageHandler(handlers[i]);
      }
      for (ClientSession session : clientSessions) {
         session.start();
      }

      boolean ok = latch.await(10, TimeUnit.SECONDS);
      assertTrue(ok);
      for (TxMessageHandler messageHandler : handlers) {
         assertFalse(messageHandler.failedToAck);
      }

      clientSession2.close();
      for (ClientSession session : clientSessions) {
         session.stop();
         session.close();
      }

   }

   @TestTemplate
   public void testSendMultipleQueues() throws Exception {
      multipleQueuesInternalTest(true, false, false, false, false);
   }

   @TestTemplate
   public void testSendMultipleQueuesOnePhase() throws Exception {
      multipleQueuesInternalTest(true, false, false, false, true);
      multipleQueuesInternalTest(false, false, true, false, true);
   }

   @TestTemplate
   public void testSendMultipleQueuesOnePhaseJoin() throws Exception {
      multipleQueuesInternalTest(true, false, false, true, true);
      multipleQueuesInternalTest(false, false, true, true, true);
   }

   @TestTemplate
   public void testSendMultipleQueuesTwoPhaseJoin() throws Exception {
      multipleQueuesInternalTest(true, false, false, true, false);
      multipleQueuesInternalTest(false, false, true, true, false);
   }

   @TestTemplate
   public void testSendMultipleQueuesRecreate() throws Exception {
      multipleQueuesInternalTest(true, false, true, false, false);
   }

   @TestTemplate
   public void testSendMultipleSuspend() throws Exception {
      multipleQueuesInternalTest(true, true, false, false, false);
   }

   @TestTemplate
   public void testSendMultipleSuspendRecreate() throws Exception {
      multipleQueuesInternalTest(true, true, true, false, false);
   }

   @TestTemplate
   public void testSendMultipleSuspendErrorCheck() throws Exception {
      ClientSession session = null;

      session = sessionFactory.createSession(true, false, false);

      Xid xid = newXID();

      session.start(xid, XAResource.TMNOFLAGS);

      try {
         session.start(xid, XAResource.TMRESUME);
         fail("XAException expected");
      } catch (XAException e) {
         assertEquals(XAException.XAER_PROTO, e.errorCode);
      }

      session.close();
   }

   @TestTemplate
   public void testEmptyXID() throws Exception {
      Xid xid = newXID();
      ClientSession session = sessionFactory.createSession(true, false, false);
      session.start(xid, XAResource.TMNOFLAGS);
      session.end(xid, XAResource.TMSUCCESS);
      session.rollback(xid);

      session.close();

      messagingService.stop();

      // do the same test with a file persistence now
      messagingService = createServer(true, configuration, -1, -1, addressSettings);

      messagingService.start();

      sessionFactory = createSessionFactory(locator);

      xid = newXID();
      session = sessionFactory.createSession(true, false, false);
      session.start(xid, XAResource.TMNOFLAGS);
      session.end(xid, XAResource.TMSUCCESS);
      session.rollback(xid);

      xid = newXID();
      session.start(xid, XAResource.TMNOFLAGS);
      session.end(xid, XAResource.TMSUCCESS);
      session.prepare(xid);
      session.commit(xid, false);

      session.close();

      xid = newXID();
      session = sessionFactory.createSession(true, false, false);
      session.start(xid, XAResource.TMNOFLAGS);
      session.end(xid, XAResource.TMSUCCESS);
      session.prepare(xid);
      session.rollback(xid);

      session.close();

      messagingService.start();

      sessionFactory = createSessionFactory(locator);

      xid = newXID();
      session = sessionFactory.createSession(true, false, false);
      session.start(xid, XAResource.TMNOFLAGS);
      session.end(xid, XAResource.TMSUCCESS);
      session.rollback(xid);

      session.close();

      messagingService.stop();
      messagingService.start();

      // This is not really necessary... But since the server has stopped, I would prefer to keep recreating the factory
      sessionFactory = createSessionFactory(locator);

      session = sessionFactory.createSession(true, false, false);

      Xid[] xids = session.recover(XAResource.TMSTARTRSCAN);

      assertEquals(0, xids.length);

      session.close();

   }

   @TestTemplate
   public void testFailXID() throws Exception {
      Xid xid = newXID();
      ClientSession session = sessionFactory.createSession(true, false, false);
      session.start(xid, XAResource.TMNOFLAGS);
      session.end(xid, XAResource.TMFAIL);
      session.rollback(xid);

      session.close();

   }

   @TestTemplate
   public void testForgetUnknownXID() throws Exception {
      try {
         clientSession.forget(newXID());
         fail("should throw a XAERR_NOTA XAException");
      } catch (XAException e) {
         assertEquals(XAException.XAER_NOTA, e.errorCode);
      }
   }

   @TestTemplate
   public void testForgetHeuristicallyCommittedXID() throws Exception {
      Xid xid = newXID();
      clientSession.start(xid, XAResource.TMNOFLAGS);
      clientSession.end(xid, XAResource.TMSUCCESS);
      clientSession.prepare(xid);

      String[] preparedTransactions = messagingService.getActiveMQServerControl().listPreparedTransactions();
      assertEquals(1, preparedTransactions.length);
      logger.debug(preparedTransactions[0]);
      assertTrue(messagingService.getActiveMQServerControl().commitPreparedTransaction(XidImpl.toBase64String(xid)));
      assertEquals(1, messagingService.getActiveMQServerControl().listHeuristicCommittedTransactions().length);

      clientSession.forget(xid);

      assertEquals(0, messagingService.getActiveMQServerControl().listHeuristicCommittedTransactions().length);
   }

   @TestTemplate
   public void testForgetHeuristicallyRolledBackXID() throws Exception {
      Xid xid = newXID();
      clientSession.start(xid, XAResource.TMNOFLAGS);
      clientSession.end(xid, XAResource.TMSUCCESS);
      clientSession.prepare(xid);

      String[] preparedTransactions = messagingService.getActiveMQServerControl().listPreparedTransactions();
      assertEquals(1, preparedTransactions.length);
      logger.debug(preparedTransactions[0]);

      assertTrue(messagingService.getActiveMQServerControl().rollbackPreparedTransaction(XidImpl.toBase64String(xid)));
      assertEquals(1, messagingService.getActiveMQServerControl().listHeuristicRolledBackTransactions().length);

      clientSession.forget(xid);

      assertEquals(0, messagingService.getActiveMQServerControl().listHeuristicRolledBackTransactions().length);
   }

   @TestTemplate
   public void testCommitHeuristicallyCommittedXID() throws Exception {
      doCompleteHeuristicallyCompletedXID(true, true);
   }

   @TestTemplate
   public void testCommitHeuristicallyRolledBackXID() throws Exception {
      doCompleteHeuristicallyCompletedXID(true, false);
   }

   @TestTemplate
   public void testRollbacktHeuristicallyCommittedXID() throws Exception {
      doCompleteHeuristicallyCompletedXID(false, true);
   }

   @TestTemplate
   public void testRollbackHeuristicallyRolledBackXID() throws Exception {
      doCompleteHeuristicallyCompletedXID(false, false);
   }

   @TestTemplate
   public void testSimpleJoin() throws Exception {
      SimpleString ADDRESS1 = SimpleString.of("Address-1");
      SimpleString ADDRESS2 = SimpleString.of("Address-2");

      clientSession.createQueue(QueueConfiguration.of(ADDRESS1));
      clientSession.createQueue(QueueConfiguration.of(ADDRESS2));

      Xid xid = newXID();

      ClientSession sessionA = sessionFactory.createSession(true, false, false);
      sessionA.start(xid, XAResource.TMNOFLAGS);

      ClientSession sessionB = sessionFactory.createSession(true, false, false);
      sessionB.start(xid, XAResource.TMJOIN);

      ClientProducer prodA = sessionA.createProducer(ADDRESS1);
      ClientProducer prodB = sessionB.createProducer(ADDRESS2);

      for (int i = 0; i < 100; i++) {
         prodA.send(createTextMessage(sessionA, "A" + i));
         prodB.send(createTextMessage(sessionB, "B" + i));
      }

      sessionA.end(xid, XAResource.TMSUCCESS);
      sessionB.end(xid, XAResource.TMSUCCESS);

      sessionB.close();

      sessionA.commit(xid, true);

      sessionA.close();

      xid = newXID();

      clientSession.start(xid, XAResource.TMNOFLAGS);

      ClientConsumer cons1 = clientSession.createConsumer(ADDRESS1);
      ClientConsumer cons2 = clientSession.createConsumer(ADDRESS2);
      clientSession.start();

      for (int i = 0; i < 100; i++) {
         ClientMessage msg = cons1.receive(1000);
         assertNotNull(msg);
         assertEquals("A" + i, getTextMessage(msg));
         msg.acknowledge();

         msg = cons2.receive(1000);
         assertNotNull(msg);
         assertEquals("B" + i, getTextMessage(msg));
         msg.acknowledge();
      }

      assertNull(cons1.receiveImmediate());
      assertNull(cons2.receiveImmediate());

      clientSession.end(xid, XAResource.TMSUCCESS);

      clientSession.commit(xid, true);

      clientSession.close();
   }

   protected void multipleQueuesInternalTest(final boolean createQueues,
                                             final boolean suspend,
                                             final boolean recreateSession,
                                             final boolean isJoinSession,
                                             final boolean onePhase) throws Exception {
      int NUMBER_OF_MSGS = 100;
      int NUMBER_OF_QUEUES = 10;
      ClientSession session = null;

      SimpleString ADDRESS = SimpleString.of("Address");

      ClientSession newJoinSession = null;

      try {

         session = sessionFactory.createSession(true, false, false);

         if (createQueues) {
            for (int i = 0; i < NUMBER_OF_QUEUES; i++) {
               session.createQueue(QueueConfiguration.of(ADDRESS.concat(Integer.toString(i))).setAddress(ADDRESS));
               if (isJoinSession) {
                  clientSession.createQueue(QueueConfiguration.of(ADDRESS.concat("-join." + i)).setAddress(ADDRESS.concat("-join")).setDurable(true));
               }

            }
         }

         for (int tr = 0; tr < 2; tr++) {

            Xid xid = newXID();

            session.start(xid, XAResource.TMNOFLAGS);

            ClientProducer prod = session.createProducer(ADDRESS);
            for (int nmsg = 0; nmsg < NUMBER_OF_MSGS; nmsg++) {
               ClientMessage msg = createTextMessage(session, "SimpleMessage" + nmsg);
               prod.send(msg);
            }

            if (suspend) {
               session.end(xid, XAResource.TMSUSPEND);
               session.start(xid, XAResource.TMRESUME);
            }

            prod.send(createTextMessage(session, "one more"));

            prod.close();

            if (isJoinSession) {
               newJoinSession = sessionFactory.createSession(true, false, false);

               // This is a basic condition, or a real TM wouldn't be able to join both sessions in a single
               // transactions
               assertTrue(session.isSameRM(newJoinSession));

               newJoinSession.start(xid, XAResource.TMJOIN);

               // The Join Session will have its own queue, as it's not possible to guarantee ordering since this
               // producer will be using a different session
               ClientProducer newProd = newJoinSession.createProducer(ADDRESS.concat("-join"));
               newProd.send(createTextMessage(newJoinSession, "After Join"));
            }

            session.end(xid, XAResource.TMSUCCESS);

            if (isJoinSession) {
               newJoinSession.end(xid, XAResource.TMSUCCESS);
               newJoinSession.close();
            }

            if (!onePhase) {
               session.prepare(xid);
            }

            if (recreateSession) {
               session.close();
               session = sessionFactory.createSession(true, false, false);
            }

            if (tr == 0) {
               session.rollback(xid);
            } else {
               session.commit(xid, onePhase);
            }

         }

         for (int i = 0; i < 2; i++) {

            Xid xid = newXID();

            session.start(xid, XAResource.TMNOFLAGS);

            for (int nqueues = 0; nqueues < NUMBER_OF_QUEUES; nqueues++) {

               ClientConsumer consumer = session.createConsumer(ADDRESS.concat(Integer.toString(nqueues)));

               session.start();

               for (int nmsg = 0; nmsg < NUMBER_OF_MSGS; nmsg++) {
                  ClientMessage msg = consumer.receive(1000);

                  assertNotNull(msg);

                  assertEquals("SimpleMessage" + nmsg, getTextMessage(msg));

                  msg.acknowledge();
               }

               ClientMessage msg = consumer.receive(1000);
               assertNotNull(msg);
               assertEquals("one more", getTextMessage(msg));
               msg.acknowledge();

               if (suspend) {
                  session.end(xid, XAResource.TMSUSPEND);
                  session.start(xid, XAResource.TMRESUME);
               }

               assertEquals("one more", getTextMessage(msg));

               if (isJoinSession) {
                  ClientSession newSession = sessionFactory.createSession(true, false, false);

                  newSession.start(xid, XAResource.TMJOIN);

                  newSession.start();

                  ClientConsumer newConsumer = newSession.createConsumer(ADDRESS.concat("-join." + nqueues));

                  msg = newConsumer.receive(1000);
                  assertNotNull(msg);

                  assertEquals("After Join", getTextMessage(msg));
                  msg.acknowledge();

                  newSession.end(xid, XAResource.TMSUCCESS);

                  newSession.close();
               }

               assertNull(consumer.receiveImmediate());
               consumer.close();

            }

            session.end(xid, XAResource.TMSUCCESS);

            session.prepare(xid);

            if (recreateSession) {
               session.close();
               session = sessionFactory.createSession(true, false, false);
            }

            if (i == 0) {
               session.rollback(xid);
            } else {
               session.commit(xid, false);
            }
         }
      } finally {
         if (session != null) {
            session.close();
         }
      }
   }

   private void doCompleteHeuristicallyCompletedXID(final boolean isCommit,
                                                    final boolean heuristicCommit) throws Exception {
      Xid xid = newXID();
      clientSession.start(xid, XAResource.TMNOFLAGS);
      clientSession.end(xid, XAResource.TMSUCCESS);
      clientSession.prepare(xid);

      String[] preparedTransactions = messagingService.getActiveMQServerControl().listPreparedTransactions();
      assertEquals(1, preparedTransactions.length);

      if (heuristicCommit) {
         assertTrue(messagingService.getActiveMQServerControl().commitPreparedTransaction(XidImpl.toBase64String(xid)));
         assertEquals(1, messagingService.getActiveMQServerControl().listHeuristicCommittedTransactions().length);
      } else {
         assertTrue(messagingService.getActiveMQServerControl().rollbackPreparedTransaction(XidImpl.toBase64String(xid)));
         assertEquals(1, messagingService.getActiveMQServerControl().listHeuristicRolledBackTransactions().length);
      }
      assertEquals(0, messagingService.getActiveMQServerControl().listPreparedTransactions().length);

      try {
         if (isCommit) {
            clientSession.commit(xid, false);
         } else {
            clientSession.rollback(xid);
         }
         fail("neither commit not rollback must succeed on a heuristically completed tx");
      } catch (XAException e) {
         if (heuristicCommit) {
            assertEquals(XAException.XA_HEURCOM, e.errorCode);
         } else {
            assertEquals(XAException.XA_HEURRB, e.errorCode);
         }
      }

      if (heuristicCommit) {
         assertEquals(1, messagingService.getActiveMQServerControl().listHeuristicCommittedTransactions().length);
      } else {
         assertEquals(1, messagingService.getActiveMQServerControl().listHeuristicRolledBackTransactions().length);
      }
   }

   class TxMessageHandler implements MessageHandler {

      boolean failedToAck = false;

      final ClientSession session;

      private final CountDownLatch latch;

      TxMessageHandler(final ClientSession session, final CountDownLatch latch) {
         this.latch = latch;
         this.session = session;
      }

      @Override
      public void onMessage(final ClientMessage message) {
         Xid xid = new XidImpl(UUIDGenerator.getInstance().generateStringUUID().getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());
         try {
            session.start(xid, XAResource.TMNOFLAGS);
         } catch (XAException e) {
            e.printStackTrace();
         }

         try {
            message.acknowledge();
         } catch (ActiveMQException e) {
            BasicXaTest.logger.error("Failed to process message", e);
         }
         try {
            session.end(xid, XAResource.TMSUCCESS);
            session.rollback(xid);
         } catch (Exception e) {
            e.printStackTrace();
            failedToAck = true;
            try {
               session.close();
            } catch (ActiveMQException e1) {
               //
            }
         }
         latch.countDown();

      }
   }
}
