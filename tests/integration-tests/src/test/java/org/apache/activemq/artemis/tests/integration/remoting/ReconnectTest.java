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
package org.apache.activemq.artemis.tests.integration.remoting;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQNotConnectedException;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.FailoverEventListener;
import org.apache.activemq.artemis.api.core.client.FailoverEventType;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.client.SessionFailureListener;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryInternal;
import org.apache.activemq.artemis.core.client.impl.ClientSessionInternal;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RetryRule;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

public class ReconnectTest extends ActiveMQTestBase {

   @Rule
   public RetryRule retryRule = new RetryRule(2);

   @Test
   public void testReconnectNetty() throws Exception {
      internalTestReconnect(true);
   }

   @Test
   public void testReconnectInVM() throws Exception {
      internalTestReconnect(false);
   }

   public void internalTestReconnect(final boolean isNetty) throws Exception {
      final int pingPeriod = 1000;

      ActiveMQServer server = createServer(false, isNetty);

      server.start();

      ClientSessionInternal session = null;

      try {
         ServerLocator locator = createFactory(isNetty).setClientFailureCheckPeriod(pingPeriod).setRetryInterval(500).setRetryIntervalMultiplier(1d).setReconnectAttempts(-1).setConfirmationWindowSize(1024 * 1024);
         ClientSessionFactory factory = createSessionFactory(locator);

         session = (ClientSessionInternal) factory.createSession();

         final AtomicInteger count = new AtomicInteger(0);

         final CountDownLatch latch = new CountDownLatch(1);

         session.addFailureListener(new SessionFailureListener() {
            @Override
            public void connectionFailed(final ActiveMQException me, boolean failedOver) {
               count.incrementAndGet();
               latch.countDown();
            }

            @Override
            public void connectionFailed(final ActiveMQException me, boolean failedOver, String scaleDownTargetNodeID) {
               connectionFailed(me, failedOver);
            }

            @Override
            public void beforeReconnect(final ActiveMQException exception) {
            }

         });

         server.stop();

         Thread.sleep((pingPeriod * 2));

         server.start();

         Assert.assertTrue(latch.await(5, TimeUnit.SECONDS));

         // Some time to let possible loops to occur
         Thread.sleep(500);

         Assert.assertEquals(1, count.get());

         locator.close();
      } finally {
         try {
            session.close();
         } catch (Throwable e) {
         }

         server.stop();
      }

   }

   @Test
   public void testMetadataAfterReconnectionNetty() throws Exception {
      internalMetadataAfterRetry(true);
   }

   @Test
   public void testMetadataAfterReconnectionInVM() throws Exception {
      internalMetadataAfterRetry(false);
   }

   public void internalMetadataAfterRetry(final boolean isNetty) throws Exception {
      final int pingPeriod = 1000;

      ActiveMQServer server = createServer(false, isNetty);

      server.start();

      ClientSessionInternal session = null;

      try {
         for (int i = 0; i < 100; i++) {
            ServerLocator locator = createFactory(isNetty);
            locator.setClientFailureCheckPeriod(pingPeriod);
            locator.setRetryInterval(1);
            locator.setRetryIntervalMultiplier(1d);
            locator.setReconnectAttempts(-1);
            locator.setConfirmationWindowSize(-1);
            ClientSessionFactory factory = createSessionFactory(locator);

            session = (ClientSessionInternal) factory.createSession();

            session.addMetaData("meta1", "meta1");

            ServerSession[] sessions = countMetadata(server, "meta1", 1);
            Assert.assertEquals(1, sessions.length);

            final AtomicInteger count = new AtomicInteger(0);

            final CountDownLatch latch = new CountDownLatch(1);

            session.addFailoverListener(new FailoverEventListener() {
               @Override
               public void failoverEvent(FailoverEventType eventType) {
                  if (eventType == FailoverEventType.FAILOVER_COMPLETED) {
                     latch.countDown();
                  }
               }
            });

            sessions[0].getRemotingConnection().fail(new ActiveMQException("failure!"));

            Assert.assertTrue(latch.await(5, TimeUnit.SECONDS));

            sessions = countMetadata(server, "meta1", 1);

            Assert.assertEquals(1, sessions.length);

            locator.close();
         }
      } finally {
         try {
            session.close();
         } catch (Throwable e) {
         }

         server.stop();
      }

   }

   private ServerSession[] countMetadata(ActiveMQServer server, String parameter, int expected) throws Exception {
      List<ServerSession> sessionList = new LinkedList<>();

      for (int i = 0; i < 10 && sessionList.size() != expected; i++) {
         sessionList.clear();
         for (ServerSession sess : server.getSessions()) {
            if (sess.getMetaData(parameter) != null) {
               sessionList.add(sess);
            }
         }

         if (sessionList.size() != expected) {
            Thread.sleep(100);
         }
      }

      return sessionList.toArray(new ServerSession[sessionList.size()]);
   }

   @Test
   public void testInterruptReconnectNetty() throws Exception {
      internalTestInterruptReconnect(true, false);
   }

   @Test
   public void testInterruptReconnectInVM() throws Exception {
      internalTestInterruptReconnect(false, false);
   }

   @Test
   public void testInterruptReconnectNettyInterruptMainThread() throws Exception {
      internalTestInterruptReconnect(true, true);
   }

   @Test
   public void testInterruptReconnectInVMInterruptMainThread() throws Exception {
      internalTestInterruptReconnect(false, true);
   }

   public void internalTestInterruptReconnect(final boolean isNetty,
                                              final boolean interruptMainThread) throws Exception {
      final int pingPeriod = 1000;

      ActiveMQServer server = createServer(false, isNetty);

      server.start();

      try {
         ServerLocator locator = createFactory(isNetty).setClientFailureCheckPeriod(pingPeriod).setRetryInterval(500).setRetryIntervalMultiplier(1d).setReconnectAttempts(-1).setConfirmationWindowSize(1024 * 1024);
         ClientSessionFactoryInternal factory = (ClientSessionFactoryInternal) locator.createSessionFactory();

         // One for beforeReconnecto from the Factory, and one for the commit about to be done
         final CountDownLatch latchCommit = new CountDownLatch(2);

         final ArrayList<Thread> threadToBeInterrupted = new ArrayList<>();

         factory.addFailureListener(new SessionFailureListener() {

            @Override
            public void connectionFailed(ActiveMQException exception, boolean failedOver) {
            }

            @Override
            public void connectionFailed(final ActiveMQException me, boolean failedOver, String scaleDownTargetNodeID) {
               connectionFailed(me, failedOver);
            }

            @Override
            public void beforeReconnect(ActiveMQException exception) {
               threadToBeInterrupted.add(Thread.currentThread());
               latchCommit.countDown();
            }
         });

         final ClientSessionInternal session = (ClientSessionInternal) factory.createSession();

         final AtomicInteger count = new AtomicInteger(0);

         final CountDownLatch latch = new CountDownLatch(1);

         session.addFailureListener(new SessionFailureListener() {

            @Override
            public void connectionFailed(final ActiveMQException me, boolean failedOver) {
               count.incrementAndGet();
               latch.countDown();
            }

            @Override
            public void connectionFailed(final ActiveMQException me, boolean failedOver, String scaleDownTargetNodeID) {
               connectionFailed(me, failedOver);
            }

            @Override
            public void beforeReconnect(final ActiveMQException exception) {
            }

         });

         server.stop();

         Thread tcommitt = new Thread() {
            @Override
            public void run() {
               latchCommit.countDown();
               try {
                  session.commit();
               } catch (ActiveMQException e) {
                  e.printStackTrace();
               }
            }
         };

         tcommitt.start();
         assertTrue(latchCommit.await(10, TimeUnit.SECONDS));

         // There should be only one thread
         assertEquals(1, threadToBeInterrupted.size());

         if (interruptMainThread) {
            tcommitt.interrupt();
         } else {
            for (Thread tint : threadToBeInterrupted) {
               tint.interrupt();
            }
         }
         tcommitt.join(5000);

         assertFalse(tcommitt.isAlive());

         locator.close();
      } finally {
      }

   }

   @Test
   public void testReattachTimeout() throws Exception {
      ActiveMQServer server = createServer(true, true);
      server.start();
      // imitate session reattach timeout
      Interceptor reattachInterceptor = new Interceptor() {

         boolean reattached;

         @Override
         public boolean intercept(Packet packet, RemotingConnection connection) throws ActiveMQException {
            if (!reattached && packet.getType() == PacketImpl.REATTACH_SESSION) {
               reattached = true;
               return false;
            } else {
               return true;
            }

         }
      };
      server.getRemotingService().addIncomingInterceptor(reattachInterceptor);

      final long retryInterval = 50;
      final double retryMultiplier = 1d;
      final int reconnectAttempts = 1;
      ServerLocator locator = createFactory(true).setCallTimeout(2000).setRetryInterval(retryInterval).setRetryIntervalMultiplier(retryMultiplier).setReconnectAttempts(reconnectAttempts).setConfirmationWindowSize(-1);
      ClientSessionFactoryInternal sf = (ClientSessionFactoryInternal) createSessionFactory(locator);
      final CountDownLatch latch = new CountDownLatch(1);
      sf.addFailoverListener(eventType -> {
         if (eventType == FailoverEventType.FAILOVER_FAILED) {
            latch.countDown();
         }
      });

      ClientSession session = sf.createSession(false, true, true);
      RemotingConnection conn = ((ClientSessionInternal) session).getConnection();
      conn.fail(new ActiveMQNotConnectedException());

      assertTrue(latch.await(1000, TimeUnit.MILLISECONDS));
      assertTrue(session.isClosed());

      session.close();
      sf.close();
      server.stop();
   }

   @Test
   public void testClosingConsumerTimeout() throws Exception {
      ActiveMQServer server = createServer(true, true);
      server.start();

      final AtomicBoolean consumerClosed = new AtomicBoolean(false);
      // imitate consumer close timeout
      Interceptor reattachInterceptor = new Interceptor() {

         @Override
         public boolean intercept(Packet packet, RemotingConnection connection) throws ActiveMQException {
            if (!consumerClosed.get() && packet.getType() == PacketImpl.SESS_CONSUMER_CLOSE) {
               consumerClosed.set(true);
               return false;
            } else {
               return true;
            }

         }
      };
      server.getRemotingService().addIncomingInterceptor(reattachInterceptor);

      final long retryInterval = 500;
      final double retryMultiplier = 1d;
      final int reconnectAttempts = 10;
      ServerLocator locator = createFactory(true).setCallTimeout(200).setRetryInterval(retryInterval).setRetryIntervalMultiplier(retryMultiplier).setReconnectAttempts(reconnectAttempts).setConfirmationWindowSize(-1);
      ClientSessionFactoryInternal sf = (ClientSessionFactoryInternal) createSessionFactory(locator);

      ClientSessionInternal session = (ClientSessionInternal)sf.createSession(false, true, true);
      SimpleString queueName1 = new SimpleString("my_queue_one");
      SimpleString addressName1 = new SimpleString("my_address_one");

      server.addAddressInfo(new AddressInfo(addressName1, RoutingType.ANYCAST));
      server.createQueue(new QueueConfiguration(queueName1).setAddress(addressName1).setRoutingType(RoutingType.ANYCAST));
      ClientConsumer clientConsumer1 = session.createConsumer(queueName1);
      ClientConsumer clientConsumer2 = session.createConsumer(queueName1);
      clientConsumer1.close();

      Wait.assertTrue(consumerClosed::get);

      Wait.assertEquals(1, () -> getConsumerCount(server, session));

      Set<ServerConsumer> serverConsumers = server.getSessionByID(session.getName()).getServerConsumers();
      ServerConsumer serverConsumer = serverConsumers.iterator().next();
      assertEquals(clientConsumer2.getConsumerContext().getId(), serverConsumer.getID());


      session.close();
      sf.close();
      server.stop();
   }

   private int getConsumerCount(ActiveMQServer server, ClientSessionInternal session) {
      ServerSession serverSession = server.getSessionByID(session.getName());
      if (serverSession == null) {
         return 0;
      }
      Set<ServerConsumer> serverConsumers = serverSession.getServerConsumers();
      return serverConsumers.size();

   }

}
