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
package org.apache.activemq.artemis.tests.integration.client;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.ActiveMQDisconnectedException;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.ActiveMQIOErrorException;
import org.apache.activemq.artemis.api.core.ActiveMQInternalErrorException;
import org.apache.activemq.artemis.api.core.ActiveMQNonExistentQueueException;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.MessageHandler;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.client.impl.ClientProducerImpl;
import org.apache.activemq.artemis.core.client.impl.ClientSessionInternal;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.core.protocol.core.impl.RemotingConnectionImpl;
import org.apache.activemq.artemis.core.remoting.CloseListener;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.server.impl.ServerSessionImpl;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.SingleServerTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.jboss.logging.Logger;
import org.junit.Test;

public class TemporaryQueueTest extends SingleServerTestBase {

   private static final Logger log = Logger.getLogger(TemporaryQueueTest.class);

   private static final long CONNECTION_TTL = 2000;

   @Override
   protected ActiveMQServer createServer() throws Exception {
      ActiveMQServer server = super.createServer();
      server.getConfiguration().setAddressQueueScanPeriod(100);
      return server;
   }

   @Test
   public void testConsumeFromTemporaryQueue() throws Exception {
      SimpleString queue = RandomUtil.randomSimpleString();
      SimpleString address = RandomUtil.randomSimpleString();

      session.createQueue(new QueueConfiguration(queue).setAddress(address).setDurable(false).setTemporary(true));

      ClientProducer producer = session.createProducer(address);
      ClientMessage msg = session.createMessage(false);

      producer.send(msg);

      session.start();
      ClientConsumer consumer = session.createConsumer(queue);
      ClientMessage message = consumer.receive(500);
      assertNotNull(message);
      message.acknowledge();

      consumer.close();
      session.deleteQueue(queue);

      session.close();
   }

   @Test
   public void testMemoryLeakOnAddressSettingForTemporaryQueue() throws Exception {
      for (int i = 0; i < 1000; i++) {
         SimpleString queue = RandomUtil.randomSimpleString();
         SimpleString address = RandomUtil.randomSimpleString();
         session.createQueue(new QueueConfiguration(queue).setAddress(address).setDurable(false).setTemporary(true));

         session.close();
         session = sf.createSession();
      }

      session.close();

      sf.close();

      Wait.assertTrue("server.getAddressSettingsRepository().getCacheSize() = " + server.getAddressSettingsRepository().getCacheSize(), () -> server.getAddressSettingsRepository().getCacheSize() < 10);
   }

   @Test
   public void testPaginStoreIsRemovedWhenQueueIsDeleted() throws Exception {
      SimpleString queue = RandomUtil.randomSimpleString();
      SimpleString address = RandomUtil.randomSimpleString();

      session.createQueue(new QueueConfiguration(queue).setAddress(address).setDurable(false).setTemporary(true));

      ClientProducer producer = session.createProducer(address);
      ClientMessage msg = session.createMessage(false);

      producer.send(msg);

      session.start();
      ClientConsumer consumer = session.createConsumer(queue);
      ClientMessage message = consumer.receive(500);
      assertNotNull(message);
      message.acknowledge();

      Wait.assertTrue(() -> Arrays.asList(server.getPagingManager().getStoreNames()).contains(address));

      consumer.close();

      session.deleteQueue(queue);
      session.close();

      Wait.assertFalse(() -> Arrays.asList(server.getPagingManager().getStoreNames()).contains(address));
   }

   @Test
   public void testConsumeFromTemporaryQueueCreatedByOtherSession() throws Exception {
      SimpleString queue = RandomUtil.randomSimpleString();
      SimpleString address = RandomUtil.randomSimpleString();

      session.createQueue(new QueueConfiguration(queue).setAddress(address).setDurable(false).setTemporary(true));

      ClientProducer producer = session.createProducer(address);
      producer.send(session.createMessage(false));

      ClientSession session2 = sf.createSession(false, true, true);
      session2.start();

      ClientConsumer consumer = session2.createConsumer(queue);
      ClientMessage message = consumer.receive(500);
      assertNotNull(message);

      session2.close();
      session.close();
   }

   @Test
   public void testDeleteTemporaryQueueAfterConnectionIsClosed() throws Exception {
      SimpleString queue = RandomUtil.randomSimpleString();
      SimpleString address = RandomUtil.randomSimpleString();

      session.createQueue(new QueueConfiguration(queue).setAddress(address).setDurable(false).setTemporary(true));
      RemotingConnectionImpl conn = (RemotingConnectionImpl) server.getRemotingService().getConnections().iterator().next();

      final CountDownLatch latch = new CountDownLatch(1);
      conn.addCloseListener(new CloseListener() {
         @Override
         public void connectionClosed() {
            latch.countDown();
         }
      });
      session.close();
      sf.close();
      // wait for the closing listeners to be fired
      assertTrue("connection close listeners not fired", latch.await(2 * TemporaryQueueTest.CONNECTION_TTL, TimeUnit.MILLISECONDS));

      sf = addSessionFactory(createSessionFactory(locator));
      session = sf.createSession(false, true, true);
      session.start();

      try {
         session.createConsumer(queue);
         fail("temp queue must not exist after the remoting connection is closed");
      } catch (ActiveMQNonExistentQueueException neqe) {
         //ol
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }

      session.close();
   }

   @Test
   public void testQueueWithWildcard() throws Exception {
      session.createQueue(new QueueConfiguration("queue1").setAddress("a.b"));
      session.createQueue(new QueueConfiguration("queue2").setAddress("a.#").setDurable(false).setTemporary(true));
      session.createQueue(new QueueConfiguration("queue3").setAddress("a.#").setDurable(false).setTemporary(true));

      ClientProducer producer = session.createProducer("a.b");
      producer.send(session.createMessage(false));

      ClientConsumer cons = session.createConsumer("queue2");

      session.start();

      ClientMessage msg = cons.receive(5000);

      assertNotNull(msg);

      msg.acknowledge();

      cons.close();

      cons = session.createConsumer("queue3");

      session.start();

      msg = cons.receive(5000);

      assertNotNull(msg);

      msg.acknowledge();

      cons.close();

      session.deleteQueue("queue2");
      session.deleteQueue("queue3");

      session.close();
   }

   @Test
   public void testQueueWithWildcard2() throws Exception {
      session.createQueue(new QueueConfiguration("queue1").setAddress("a.b"));
      session.createQueue(new QueueConfiguration("queue2").setAddress("a.#").setDurable(false).setTemporary(true));
      session.createQueue(new QueueConfiguration("queue3").setAddress("a.#").setDurable(false).setTemporary(true));

      ClientProducer producer = session.createProducer("a.b");
      producer.send(session.createMessage(false));

      ClientConsumer cons = session.createConsumer("queue2");

      session.start();

      ClientMessage msg = cons.receive(5000);

      assertNotNull(msg);

      msg.acknowledge();

      cons.close();

      cons = session.createConsumer("queue3");

      session.start();

      msg = cons.receive(5000);

      assertNotNull(msg);

      msg.acknowledge();

      cons.close();

      session.deleteQueue("queue2");
      session.deleteQueue("queue3");

      session.close();
   }

   @Test
   public void testQueueWithWildcard3() throws Exception {
      session.createQueue(new QueueConfiguration("queue1").setAddress("a.b"));
      session.createQueue(new QueueConfiguration("queue2").setAddress("a.#").setDurable(false).setTemporary(true));
      session.createQueue(new QueueConfiguration("queue2.1").setAddress("a.#").setDurable(false).setTemporary(true));

      session.deleteQueue("queue2");
   }

   @Test
   public void testDeleteTemporaryQueueAfterConnectionIsClosed_2() throws Exception {
      SimpleString queue = RandomUtil.randomSimpleString();
      SimpleString address = RandomUtil.randomSimpleString();

      session.createQueue(new QueueConfiguration(queue).setAddress(address));
      assertEquals(1, server.getConnectionCount());

      // we create a second session. the temp queue must be present
      // even after we closed the session which created it
      ClientSession session2 = sf.createSession(false, true, true);

      session.close();

      // let some time for the server to clean the connections
      // Thread.sleep(1000);

      session2.start();

      session2.createConsumer(queue);

      session2.close();
   }

   @Test
   public void testRecreateConsumerOverServerFailure() throws Exception {
      ServerLocator serverWithReattach = createInVMNonHALocator().setReconnectAttempts(30).setRetryInterval(1000).setConfirmationWindowSize(-1).setConnectionTTL(TemporaryQueueTest.CONNECTION_TTL).setClientFailureCheckPeriod(TemporaryQueueTest.CONNECTION_TTL / 3);
      ClientSessionFactory reattachSF = createSessionFactory(serverWithReattach);

      ClientSession session = reattachSF.createSession(false, false);
      session.createQueue(new QueueConfiguration("tmpQ").setAddress("tmpAd").setDurable(false).setTemporary(true));
      ClientConsumer consumer = session.createConsumer("tmpQ");

      ClientProducer prod = session.createProducer("tmpAd");

      session.start();

      RemotingConnectionImpl conn = (RemotingConnectionImpl) ((ClientSessionInternal) session).getConnection();

      conn.fail(new ActiveMQIOErrorException());

      prod.send(session.createMessage(false));
      session.commit();

      assertNotNull(consumer.receive(1000));

      session.close();

      reattachSF.close();

      serverWithReattach.close();

   }

   @Test
   public void testTemporaryQueuesWithFilter() throws Exception {

      int countTmpQueue = 0;

      final AtomicInteger errors = new AtomicInteger(0);

      class MyHandler implements MessageHandler {

         final String color;

         final CountDownLatch latch;

         final ClientSession sess;

         MyHandler(ClientSession sess, String color, int expectedMessages) {
            this.sess = sess;
            latch = new CountDownLatch(expectedMessages);
            this.color = color;
         }

         public boolean waitCompletion() throws Exception {
            return latch.await(10, TimeUnit.SECONDS);
         }

         @Override
         public void onMessage(ClientMessage message) {
            try {
               message.acknowledge();
               sess.commit();
               latch.countDown();

               if (!message.getStringProperty("color").equals(color)) {
                  log.warn("Unexpected color " + message.getStringProperty("color") + " when we were expecting " + color);
                  errors.incrementAndGet();
               }
            } catch (Exception e) {
               log.warn(e.getMessage(), e);
               errors.incrementAndGet();
            }
         }

      }

      String address = "AD_test";
      int iterations = 100;
      int msgs = 100;

      for (int i = 0; i < iterations; i++) {
         ClientSessionFactory clientsConnecton = addSessionFactory(createSessionFactory(locator));
         ClientSession localSession = clientsConnecton.createSession();

         ClientProducer prod = localSession.createProducer(address);

         localSession.start();

         String queueRed = address + "_red_" + (countTmpQueue++);
         String queueBlue = address + "_blue_" + (countTmpQueue++);

         ClientSession sessConsumerRed = clientsConnecton.createSession();
         sessConsumerRed.createQueue(new QueueConfiguration(queueRed).setAddress(address).setFilterString("color='red'").setDurable(false).setTemporary(true));
         MyHandler redHandler = new MyHandler(sessConsumerRed, "red", msgs);
         ClientConsumer redClientConsumer = sessConsumerRed.createConsumer(queueRed);
         redClientConsumer.setMessageHandler(redHandler);
         sessConsumerRed.start();

         ClientSession sessConsumerBlue = clientsConnecton.createSession();
         sessConsumerBlue.createQueue(new QueueConfiguration(queueBlue).setAddress(address).setFilterString("color='blue'").setDurable(false).setTemporary(true));
         MyHandler blueHandler = new MyHandler(sessConsumerBlue, "blue", msgs);
         ClientConsumer blueClientConsumer = sessConsumerBlue.createConsumer(queueBlue);
         blueClientConsumer.setMessageHandler(blueHandler);
         sessConsumerBlue.start();

         try {
            ClientMessage msgBlue = session.createMessage(false);
            msgBlue.putStringProperty("color", "blue");

            ClientMessage msgRed = session.createMessage(false);
            msgRed.putStringProperty("color", "red");

            for (int nmsg = 0; nmsg < msgs; nmsg++) {
               prod.send(msgBlue);

               prod.send(msgRed);

               session.commit();
            }

            blueHandler.waitCompletion();
            redHandler.waitCompletion();

            assertEquals(0, errors.get());

         } finally {
            localSession.close();
            clientsConnecton.close();
         }
      }

   }

   @Test
   public void testDeleteTemporaryQueueWhenClientCrash() throws Exception {
      session.close();
      sf.close();

      final SimpleString queue = RandomUtil.randomSimpleString();
      SimpleString address = RandomUtil.randomSimpleString();

      // server must received at least one ping from the client to pass
      // so that the server connection TTL is configured with the client value
      final CountDownLatch pingOnServerLatch = new CountDownLatch(1);
      server.getRemotingService().addIncomingInterceptor(new Interceptor() {

         @Override
         public boolean intercept(final Packet packet, final RemotingConnection connection) throws ActiveMQException {
            if (packet.getType() == PacketImpl.PING) {
               pingOnServerLatch.countDown();
            }
            return true;
         }
      });

      ServerLocator locator = createInVMNonHALocator();
      locator.setConnectionTTL(TemporaryQueueTest.CONNECTION_TTL);
      sf = addSessionFactory(createSessionFactory(locator));
      session = sf.createSession(false, true, true);

      session.createQueue(new QueueConfiguration(queue).setAddress(address).setDurable(false).setTemporary(true));
      assertTrue("server has not received any ping from the client", pingOnServerLatch.await(2 * server.getConfiguration().getConnectionTtlCheckInterval(), TimeUnit.MILLISECONDS));
      assertEquals(1, server.getConnectionCount());

      RemotingConnection remotingConnection = server.getRemotingService().getConnections().iterator().next();
      final CountDownLatch serverCloseLatch = new CountDownLatch(1);
      remotingConnection.addCloseListener(new CloseListener() {
         @Override
         public void connectionClosed() {
            serverCloseLatch.countDown();
         }
      });

      ((ClientSessionInternal) session).getConnection().fail(new ActiveMQInternalErrorException("simulate a client failure"));

      // let some time for the server to clean the connections
      assertTrue("server has not closed the connection", serverCloseLatch.await(2 * server.getConfiguration().getConnectionTtlCheckInterval() + 2 * TemporaryQueueTest.CONNECTION_TTL, TimeUnit.MILLISECONDS));

      // The next getCount will be asynchronously done at the end of failure. We will wait some time until it has reached there.
      for (long timeout = System.currentTimeMillis() + 5000; timeout > System.currentTimeMillis() && server.getConnectionCount() > 0; ) {
         Thread.sleep(1);
      }

      assertEquals(0, server.getConnectionCount());

      session.close();

      sf.close();
      ServerLocator locator2 = createInVMNonHALocator();

      sf = addSessionFactory(createSessionFactory(locator2));
      session = sf.createSession(false, true, true);
      session.start();

      ActiveMQAction activeMQAction = new ActiveMQAction() {
         @Override
         public void run() throws ActiveMQException {
            session.createConsumer(queue);
         }
      };

      ActiveMQTestBase.expectActiveMQException("temp queue must not exist after the server detected the client crash", ActiveMQExceptionType.QUEUE_DOES_NOT_EXIST, activeMQAction);

      session.close();

      locator2.close();
   }

   @Test
   public void testBlockingWithTemporaryQueue() throws Exception {

      AddressSettings setting = new AddressSettings().setAddressFullMessagePolicy(AddressFullMessagePolicy.BLOCK).setMaxSizeBytes(1024 * 1024);

      server.getAddressSettingsRepository().addMatch("TestAD", setting);

      ClientSessionFactory consumerCF = createSessionFactory(locator);
      ClientSession consumerSession = consumerCF.createSession(true, true);
      consumerSession.addMetaData("consumer", "consumer");
      consumerSession.createQueue(new QueueConfiguration("Q1").setAddress("TestAD").setDurable(false).setTemporary(true));
      consumerSession.createConsumer("Q1");
      consumerSession.start();

      final ClientProducerImpl prod = (ClientProducerImpl) session.createProducer("TestAD");

      final AtomicInteger errors = new AtomicInteger(0);

      final AtomicInteger msgs = new AtomicInteger(0);

      final int TOTAL_MSG = 1000;

      Thread t = new Thread() {
         @Override
         public void run() {
            try {
               for (int i = 0; i < TOTAL_MSG; i++) {
                  ClientMessage msg = session.createMessage(false);
                  msg.getBodyBuffer().writeBytes(new byte[1024]);
                  prod.send(msg);
                  msgs.incrementAndGet();
               }
            } catch (Throwable e) {
               e.printStackTrace();
               errors.incrementAndGet();
            }

         }
      };

      t.start();

      while (msgs.get() == 0) {
         Thread.sleep(100);
      }

      int blockedTime = 0;

      // https://issues.apache.org/jira/browse/ARTEMIS-368
      while (t.isAlive() && errors.get() == 0 && (!prod.getProducerCredits().isBlocked() || blockedTime < 60)) {
         if (prod.getProducerCredits().isBlocked()) {
            blockedTime++;
         } else {
            blockedTime = 0;
         }
         Thread.sleep(100);
      }

      assertEquals(0, errors.get());

      ClientSessionFactory newConsumerCF = createSessionFactory(locator);
      ClientSession newConsumerSession = newConsumerCF.createSession(true, true);
      newConsumerSession.createQueue(new QueueConfiguration("Q2").setAddress("TestAD").setDurable(false).setTemporary(true));
      ClientConsumer newConsumer = newConsumerSession.createConsumer("Q2");
      newConsumerSession.start();

      int toReceive = TOTAL_MSG - msgs.get();

      for (ServerSession sessionIterator : server.getSessions()) {
         if (sessionIterator.getMetaData("consumer") != null) {
            ServerSessionImpl impl = (ServerSessionImpl) sessionIterator;
            impl.getRemotingConnection().fail(new ActiveMQDisconnectedException("failure e"));
         }
      }

      int secondReceive = 0;

      ClientMessage msg = null;
      while (secondReceive < toReceive && (msg = newConsumer.receive(5000)) != null) {
         msg.acknowledge();
         secondReceive++;
      }

      assertNull(newConsumer.receiveImmediate());

      assertEquals(toReceive, secondReceive);

      t.join();

   }


}
