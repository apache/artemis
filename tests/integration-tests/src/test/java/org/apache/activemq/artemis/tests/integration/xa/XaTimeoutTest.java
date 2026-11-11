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

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.MessageHandler;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.StoreConfiguration;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionXAStartMessage;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.core.transaction.TransactionOperationAbstract;
import org.apache.activemq.artemis.core.transaction.impl.XidImpl;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.UUIDGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(ParameterizedTestExtension.class)
public class XaTimeoutTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final Map<String, AddressSettings> addressSettings = new HashMap<>();

   private ActiveMQServer server;

   private ClientSession clientSession;

   private ClientProducer clientProducer;

   private ClientConsumer clientConsumer;

   private ClientSessionFactory sessionFactory;

   private Configuration configuration;

   private final SimpleString atestq = SimpleString.of("atestq");

   private ServerLocator locator;

   private StoreConfiguration.StoreType storeType;

   public XaTimeoutTest(StoreConfiguration.StoreType storeType) {
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
         configuration = createBasicConfig();
      }
      configuration.setTransactionTimeoutScanPeriod(500).addAcceptorConfiguration(new TransportConfiguration(ActiveMQTestBase.INVM_ACCEPTOR_FACTORY));

      server = addServer(ActiveMQServers.newActiveMQServer(configuration, false));
      // start the server
      server.start();
      // then we create a client as normal
      locator = createInVMNonHALocator();
      sessionFactory = createSessionFactory(locator);
      clientSession = sessionFactory.createSession(true, false, false);
      clientSession.createQueue(QueueConfiguration.of(atestq));
      clientProducer = clientSession.createProducer(atestq);
      clientConsumer = clientSession.createConsumer(atestq);
   }

   @TestTemplate
   public void testSimpleTimeoutOnSendOnCommit() throws Exception {
      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      ClientMessage m1 = createTextMessage(clientSession, "m1");
      ClientMessage m2 = createTextMessage(clientSession, "m2");
      ClientMessage m3 = createTextMessage(clientSession, "m3");
      ClientMessage m4 = createTextMessage(clientSession, "m4");
      clientSession.setTransactionTimeout(1);
      clientSession.start(xid, XAResource.TMNOFLAGS);
      clientProducer.send(m1);
      clientProducer.send(m2);
      clientProducer.send(m3);
      clientProducer.send(m4);
      clientSession.end(xid, XAResource.TMSUCCESS);
      CountDownLatch latch = new CountDownLatch(1);
      server.getResourceManager().getTransaction(xid).addOperation(new RollbackCompleteOperation(latch));
      assertTrue(latch.await(5, TimeUnit.SECONDS));
      try {
         clientSession.commit(xid, true);
      } catch (XAException e) {
         assertEquals(XAException.XAER_NOTA, e.errorCode);
      }
      clientSession.start();
      ClientMessage m = clientConsumer.receiveImmediate();
      assertNull(m);
   }

   @TestTemplate
   public void testSimpleTimeoutOnReceive() throws Exception {
      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      ClientMessage m1 = createTextMessage(clientSession, "m1");
      ClientMessage m2 = createTextMessage(clientSession, "m2");
      ClientMessage m3 = createTextMessage(clientSession, "m3");
      ClientMessage m4 = createTextMessage(clientSession, "m4");
      ClientSession clientSession2 = sessionFactory.createSession(false, true, true);
      ClientProducer clientProducer2 = clientSession2.createProducer(atestq);
      clientProducer2.send(m1);
      clientProducer2.send(m2);
      clientProducer2.send(m3);
      clientProducer2.send(m4);
      clientSession2.close();
      clientSession.setTransactionTimeout(2);
      clientSession.start(xid, XAResource.TMNOFLAGS);
      clientSession.start();
      ClientMessage m = clientConsumer.receive(500);
      m.acknowledge();
      assertNotNull(m);
      assertEquals("m1", m.getBodyBuffer().readString());
      m = clientConsumer.receive(500);
      assertNotNull(m);
      m.acknowledge();
      assertEquals("m2", m.getBodyBuffer().readString());
      m = clientConsumer.receive(500);
      m.acknowledge();
      assertNotNull(m);
      assertEquals("m3", m.getBodyBuffer().readString());
      m = clientConsumer.receive(500);
      m.acknowledge();
      assertNotNull(m);
      assertEquals("m4", m.getBodyBuffer().readString());
      clientSession.end(xid, XAResource.TMSUCCESS);
      CountDownLatch latch = new CountDownLatch(1);
      server.getResourceManager().getTransaction(xid).addOperation(new RollbackCompleteOperation(latch));
      assertTrue(latch.await(5, TimeUnit.SECONDS));
      try {
         clientSession.commit(xid, true);
      } catch (XAException e) {
         assertEquals(XAException.XAER_NOTA, e.errorCode);
      }
      clientSession.setTransactionTimeout(0);
      clientConsumer.close();
      clientSession2 = sessionFactory.createSession(false, true, true);
      ClientConsumer consumer = clientSession2.createConsumer(atestq);
      clientSession2.start();
      m = consumer.receive(500);
      m.acknowledge();
      assertNotNull(m);
      assertEquals("m1", m.getBodyBuffer().readString());
      m = consumer.receive(500);
      assertNotNull(m);
      m.acknowledge();
      assertEquals("m2", m.getBodyBuffer().readString());
      m = consumer.receive(500);
      m.acknowledge();
      assertNotNull(m);
      assertEquals("m3", m.getBodyBuffer().readString());
      m = consumer.receive(500);
      m.acknowledge();
      assertNotNull(m);
      assertEquals("m4", m.getBodyBuffer().readString());
      clientSession2.close();
   }

   @TestTemplate
   public void testSimpleTimeoutOnSendAndReceive() throws Exception {
      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      ClientMessage m1 = createTextMessage(clientSession, "m1");
      ClientMessage m2 = createTextMessage(clientSession, "m2");
      ClientMessage m3 = createTextMessage(clientSession, "m3");
      ClientMessage m4 = createTextMessage(clientSession, "m4");
      ClientMessage m5 = createTextMessage(clientSession, "m5");
      ClientMessage m6 = createTextMessage(clientSession, "m6");
      ClientMessage m7 = createTextMessage(clientSession, "m7");
      ClientMessage m8 = createTextMessage(clientSession, "m8");
      ClientSession clientSession2 = sessionFactory.createSession(false, true, true);
      ClientProducer clientProducer2 = clientSession2.createProducer(atestq);
      clientProducer2.send(m1);
      clientProducer2.send(m2);
      clientProducer2.send(m3);
      clientProducer2.send(m4);
      clientSession2.close();
      clientSession.setTransactionTimeout(2);
      clientSession.start(xid, XAResource.TMNOFLAGS);
      clientSession.start();
      clientProducer.send(m5);
      clientProducer.send(m6);
      clientProducer.send(m7);
      clientProducer.send(m8);
      ClientMessage m = clientConsumer.receive(500);
      m.acknowledge();
      assertNotNull(m);
      assertEquals("m1", m.getBodyBuffer().readString());
      m = clientConsumer.receive(500);
      assertNotNull(m);
      m.acknowledge();
      assertEquals("m2", m.getBodyBuffer().readString());
      m = clientConsumer.receive(500);
      m.acknowledge();
      assertNotNull(m);
      assertEquals("m3", m.getBodyBuffer().readString());
      m = clientConsumer.receive(500);
      m.acknowledge();
      assertNotNull(m);
      assertEquals("m4", m.getBodyBuffer().readString());
      clientSession.end(xid, XAResource.TMSUCCESS);
      CountDownLatch latch = new CountDownLatch(1);
      server.getResourceManager().getTransaction(xid).addOperation(new RollbackCompleteOperation(latch));
      assertTrue(latch.await(5, TimeUnit.SECONDS));
      try {
         clientSession.commit(xid, true);
      } catch (XAException e) {
         assertEquals(XAException.XAER_NOTA, e.errorCode);
      }
      clientSession.setTransactionTimeout(0);
      clientConsumer.close();
      clientSession2 = sessionFactory.createSession(false, true, true);
      ClientConsumer consumer = clientSession2.createConsumer(atestq);
      clientSession2.start();
      m = consumer.receive(500);
      m.acknowledge();
      assertNotNull(m);
      assertEquals("m1", m.getBodyBuffer().readString());
      m = consumer.receive(500);
      assertNotNull(m);
      m.acknowledge();
      assertEquals("m2", m.getBodyBuffer().readString());
      m = consumer.receive(500);
      m.acknowledge();
      assertNotNull(m);
      assertEquals("m3", m.getBodyBuffer().readString());
      m = consumer.receive(500);
      m.acknowledge();
      assertNotNull(m);
      assertEquals("m4", m.getBodyBuffer().readString());
      m = consumer.receiveImmediate();
      assertNull(m);
      clientSession2.close();
   }

   @TestTemplate
   public void testPreparedTransactionNotTimedOut() throws Exception {
      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      ClientMessage m1 = createTextMessage(clientSession, "m1");
      ClientMessage m2 = createTextMessage(clientSession, "m2");
      ClientMessage m3 = createTextMessage(clientSession, "m3");
      ClientMessage m4 = createTextMessage(clientSession, "m4");
      ClientMessage m5 = createTextMessage(clientSession, "m5");
      ClientMessage m6 = createTextMessage(clientSession, "m6");
      ClientMessage m7 = createTextMessage(clientSession, "m7");
      ClientMessage m8 = createTextMessage(clientSession, "m8");
      ClientSession clientSession2 = sessionFactory.createSession(false, true, true);
      ClientProducer clientProducer2 = clientSession2.createProducer(atestq);
      clientProducer2.send(m1);
      clientProducer2.send(m2);
      clientProducer2.send(m3);
      clientProducer2.send(m4);
      clientSession2.close();
      clientSession.setTransactionTimeout(2);
      clientSession.start(xid, XAResource.TMNOFLAGS);
      clientSession.start();
      clientProducer.send(m5);
      clientProducer.send(m6);
      clientProducer.send(m7);
      clientProducer.send(m8);
      ClientMessage m = clientConsumer.receive(500);
      m.acknowledge();
      assertNotNull(m);
      assertEquals("m1", m.getBodyBuffer().readString());
      m = clientConsumer.receive(500);
      assertNotNull(m);
      m.acknowledge();
      assertEquals("m2", m.getBodyBuffer().readString());
      m = clientConsumer.receive(500);
      m.acknowledge();
      assertNotNull(m);
      assertEquals("m3", m.getBodyBuffer().readString());
      m = clientConsumer.receive(500);
      m.acknowledge();
      assertNotNull(m);
      assertEquals("m4", m.getBodyBuffer().readString());
      clientSession.end(xid, XAResource.TMSUCCESS);
      clientSession.prepare(xid);
      CountDownLatch latch = new CountDownLatch(1);
      server.getResourceManager().getTransaction(xid).addOperation(new RollbackCompleteOperation(latch));
      assertFalse(latch.await(2600, TimeUnit.MILLISECONDS));
      clientSession.commit(xid, false);

      clientSession.setTransactionTimeout(0);
      clientConsumer.close();
      clientSession2 = sessionFactory.createSession(false, true, true);
      ClientConsumer consumer = clientSession2.createConsumer(atestq);
      clientSession2.start();
      m = consumer.receive(500);
      m.acknowledge();
      assertNotNull(m);
      assertEquals("m5", m.getBodyBuffer().readString());
      m = consumer.receive(500);
      assertNotNull(m);
      m.acknowledge();
      assertEquals("m6", m.getBodyBuffer().readString());
      m = consumer.receive(500);
      m.acknowledge();
      assertNotNull(m);
      assertEquals("m7", m.getBodyBuffer().readString());
      m = consumer.receive(500);
      m.acknowledge();
      assertNotNull(m);
      assertEquals("m8", m.getBodyBuffer().readString());
      m = consumer.receiveImmediate();
      assertNull(m);
      clientSession2.close();
   }

   @TestTemplate
   public void testTimeoutOnConsumerResend() throws Exception {

      int numberOfMessages = 100;

      String outQueue = "outQueue";
      {
         ClientSession simpleTXSession = sessionFactory.createTransactedSession();
         ClientProducer producerTX = simpleTXSession.createProducer(atestq);
         for (int i = 0; i < numberOfMessages; i++) {
            ClientMessage m = createTextMessage(clientSession, "m-" + i);
            m.putIntProperty("msg", i);
            producerTX.send(m);
         }
         simpleTXSession.commit();

         // This test needs 2 queues
         simpleTXSession.createQueue(QueueConfiguration.of(outQueue));

         simpleTXSession.close();
      }

      final ClientSession outProducerSession = sessionFactory.createSession(true, false, false);
      final ClientProducer outProducer = outProducerSession.createProducer(outQueue);
      final AtomicInteger errors = new AtomicInteger(0);

      final AtomicInteger msgCount = new AtomicInteger(0);

      final CountDownLatch latchReceives = new CountDownLatch(numberOfMessages + 1); // since the first message will be rolled back

      outProducerSession.setTransactionTimeout(2);
      clientSession.setTransactionTimeout(2);

      MessageHandler handler = message -> {
         try {
            latchReceives.countDown();

            Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());
            Xid xidOut = new XidImpl("xa2".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

            clientSession.start(xid, XAResource.TMNOFLAGS);
            outProducerSession.start(xidOut, XAResource.TMNOFLAGS);

            message.acknowledge();

            int msgInt = message.getIntProperty("msg");

            ClientMessage msgOut = createTextMessage(outProducerSession, "outMsg=" + msgInt);
            msgOut.putIntProperty("msg", msgInt);
            outProducer.send(msgOut);

            boolean rollback = false;
            if (msgCount.getAndIncrement() == 0) {
               rollback = true;
               logger.debug("Forcing first message to time out");
               Thread.sleep(5000);
            }

            try {
               clientSession.end(xid, XAResource.TMSUCCESS);
            } catch (Exception e) {
               e.printStackTrace();
            }

            try {
               outProducerSession.end(xidOut, XAResource.TMSUCCESS);
            } catch (Exception e) {
               e.printStackTrace();
            }

            if (rollback) {
               try {
                  clientSession.rollback(xid);
               } catch (Exception e) {
                  e.printStackTrace();
                  clientSession.rollback();
               }

               try {
                  outProducerSession.rollback(xidOut);
               } catch (Exception e) {
                  e.printStackTrace();
                  outProducerSession.rollback();
               }
            } else {
               clientSession.prepare(xid);
               outProducerSession.prepare(xidOut);
               clientSession.commit(xid, false);
               outProducerSession.commit(xidOut, false);
            }
         } catch (Exception e) {
            e.printStackTrace();
            errors.incrementAndGet();
         }
      };

      clientConsumer.setMessageHandler(handler);

      clientSession.start();

      assertTrue(latchReceives.await(20, TimeUnit.SECONDS));

      clientConsumer.close();

      clientConsumer = clientSession.createConsumer(this.atestq);
      assertNull(clientConsumer.receiveImmediate());

      clientConsumer.close();

      clientConsumer = clientSession.createConsumer(outQueue);

      Set<Integer> msgsIds = new HashSet<>();

      for (int i = 0; i < numberOfMessages; i++) {
         ClientMessage msg = clientConsumer.receive(1000);
         assertNotNull(msg);
         msg.acknowledge();
         msgsIds.add(msg.getIntProperty("msg"));
      }

      assertNull(clientConsumer.receiveImmediate());

      for (int i = 0; i < numberOfMessages; i++) {
         assertTrue(msgsIds.contains(i));
      }

      outProducerSession.close();

   }

   /**
    * In case a timeout happens the server's object may still have the previous XID. for that reason a new start call is
    * supposed to clean it up with a log.warn but it should still succeed
    */
   @TestTemplate
   public void testChangeXID() throws Exception {
      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());
      Xid xid2 = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      clientSession.start(xid, XAResource.TMNOFLAGS);
      clientSession.start(xid2, XAResource.TMNOFLAGS);

   }

   @TestTemplate
   public void testChangingTimeoutGetsPickedUp() throws Exception {
      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      ClientMessage m1 = createTextMessage(clientSession, "m1");
      ClientMessage m2 = createTextMessage(clientSession, "m2");
      ClientMessage m3 = createTextMessage(clientSession, "m3");
      ClientMessage m4 = createTextMessage(clientSession, "m4");
      clientSession.start(xid, XAResource.TMNOFLAGS);
      clientProducer.send(m1);
      clientProducer.send(m2);
      clientProducer.send(m3);
      clientProducer.send(m4);
      clientSession.end(xid, XAResource.TMSUCCESS);

      clientSession.commit(xid, true);

      clientSession.setTransactionTimeout(1);
      clientSession.start(xid, XAResource.TMNOFLAGS);
      CountDownLatch latch = new CountDownLatch(1);
      server.getResourceManager().getTransaction(xid).addOperation(new RollbackCompleteOperation(latch));
      clientProducer.send(m1);
      clientProducer.send(m2);
      clientProducer.send(m3);
      clientProducer.send(m4);
      clientSession.end(xid, XAResource.TMSUCCESS);
      assertTrue(latch.await(2600, TimeUnit.MILLISECONDS));
      try {
         clientSession.commit(xid, true);
      } catch (XAException e) {
         assertEquals(XAException.XAER_NOTA, e.errorCode);
      }
      clientSession.start();
      ClientMessage m = clientConsumer.receiveImmediate();
      assertNotNull(m);
      m = clientConsumer.receiveImmediate();
      assertNotNull(m);
      m = clientConsumer.receiveImmediate();
      assertNotNull(m);
      m = clientConsumer.receiveImmediate();
      assertNotNull(m);
      m = clientConsumer.receiveImmediate();
      assertNull(m);
   }

   @TestTemplate
   public void testMultipleTransactionsTimedOut() throws Exception {
      Xid[] xids = new XidImpl[100];
      for (int i = 0; i < xids.length; i++) {
         xids[i] = new XidImpl(("xa" + i).getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());
      }
      ClientSession[] clientSessions = new ClientSession[xids.length];
      for (int i = 0; i < clientSessions.length; i++) {
         clientSessions[i] = sessionFactory.createSession(true, false, false);
         clientSessions[i].setTransactionTimeout(i < 50 ? 2 : 5000);
      }

      ClientProducer[] clientProducers = new ClientProducer[xids.length];
      for (int i = 0; i < clientProducers.length; i++) {
         clientProducers[i] = clientSessions[i].createProducer(atestq);
      }

      ClientMessage[] messages = new ClientMessage[xids.length];

      for (int i = 0; i < messages.length; i++) {
         messages[i] = createTextMessage(clientSession, "m" + i);
      }
      for (int i = 0; i < clientSessions.length; i++) {
         clientSessions[i].start(xids[i], XAResource.TMNOFLAGS);
      }
      for (int i = 0; i < clientProducers.length; i++) {
         clientProducers[i].send(messages[i]);
      }
      for (int i = 0; i < clientSessions.length; i++) {
         clientSessions[i].end(xids[i], XAResource.TMSUCCESS);
      }
      CountDownLatch[] latches = new CountDownLatch[xids.length];
      for (int i1 = 0; i1 < latches.length; i1++) {
         latches[i1] = new CountDownLatch(1);
         server.getResourceManager().getTransaction(xids[i1]).addOperation(new RollbackCompleteOperation(latches[i1]));
      }
      for (int i1 = 0; i1 < latches.length / 2; i1++) {
         assertTrue(latches[i1].await(5, TimeUnit.SECONDS));
      }

      for (int i = 0; i < clientSessions.length / 2; i++) {
         try {
            clientSessions[i].commit(xids[i], true);
         } catch (XAException e) {
            assertEquals(XAException.XAER_NOTA, e.errorCode);
         }
      }
      for (int i = 50; i < clientSessions.length; i++) {
         clientSessions[i].commit(xids[i], true);
      }
      for (ClientSession session : clientSessions) {
         session.close();
      }
      clientSession.start();
      for (int i = 0; i < clientSessions.length / 2; i++) {
         ClientMessage m = clientConsumer.receiveImmediate();
         assertNotNull(m);
      }
      ClientMessage m = clientConsumer.receiveImmediate();
      assertNull(m);
   }

   // Test that will timeout on a XA transaction and then will perform another XA operation
   @TestTemplate
   public void testTimeoutOnXACall() throws Exception {
      final CountDownLatch latch = new CountDownLatch(1);
      class SomeInterceptor implements Interceptor {

         @Override
         public boolean intercept(Packet packet, RemotingConnection connection) throws ActiveMQException {
            if (packet instanceof SessionXAStartMessage) {
               try {
                  latch.await(1, TimeUnit.MINUTES);
               } catch (InterruptedException e) {
                  e.printStackTrace();
               }
            }
            return true;
         }

      }
      server.getRemotingService().addIncomingInterceptor(new SomeInterceptor());

      ServerLocator locatorTimeout = createInVMNonHALocator().setCallTimeout(300).setReconnectAttempts(-1);
      ClientSessionFactory factoryTimeout = locatorTimeout.createSessionFactory();

      final ClientSession sessionTimeout = factoryTimeout.createSession(true, false, false);

      Xid xid = newXID();

      boolean expectedException = false;

      try {
         sessionTimeout.start(xid, XAResource.TMNOFLAGS);
      } catch (Exception e) {
         expectedException = true;
         e.printStackTrace();
      }

      assertTrue(expectedException);

      // this will release the interceptor and the next response will be out of sync unless we do something about
      latch.countDown();

      sessionTimeout.setTransactionTimeout(30);

      sessionTimeout.close();

      factoryTimeout.close();

      locatorTimeout.close();
   }

   final class RollbackCompleteOperation extends TransactionOperationAbstract {

      final CountDownLatch latch;

      RollbackCompleteOperation(final CountDownLatch latch) {
         this.latch = latch;
      }

      @Override
      public void afterRollback(final Transaction tx) {
         latch.countDown();
      }
   }
}
