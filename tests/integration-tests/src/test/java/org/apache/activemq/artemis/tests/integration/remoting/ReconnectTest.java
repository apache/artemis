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
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.FailoverEventType;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.client.SessionFailureListener;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryInternal;
import org.apache.activemq.artemis.core.client.impl.ClientSessionInternal;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReconnectTest extends ActiveMQTestBase {

   @Test
   public void testReconnectNetty() throws Exception {
      internalTestReconnect(true);
   }

   @Test
   public void testReconnectInVM() throws Exception {
      internalTestReconnect(false);
   }

   public void internalTestReconnect(final boolean isNetty) throws Exception {
      final int pingPeriod = 100;

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

         assertTrue(latch.await(5, TimeUnit.SECONDS));

         Wait.assertEquals(1, count::get, 5000, 100);

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
         ServerLocator locator = createFactory(isNetty);
         locator.setClientFailureCheckPeriod(pingPeriod);
         locator.setRetryInterval(1);
         locator.setRetryIntervalMultiplier(1d);
         locator.setReconnectAttempts(-1);
         locator.setConfirmationWindowSize(-1);
         ClientSessionFactory factory = createSessionFactory(locator);

         session = (ClientSessionInternal) factory.createSession();

         session.addMetaData("meta1", "meta1");

         Wait.assertEquals(1, () -> getSessionsWithMetadata(server, "meta1").length);

         ServerSession[] sessions = getSessionsWithMetadata(server, "meta1");

         final CountDownLatch latch = new CountDownLatch(1);

         session.addFailoverListener(eventType -> {
            if (eventType == FailoverEventType.FAILOVER_COMPLETED) {
               latch.countDown();
            }
         });

         sessions[0].getRemotingConnection().fail(new ActiveMQException("failure!"));

         assertTrue(latch.await(5, TimeUnit.SECONDS));

         Wait.assertEquals(1, () -> getSessionsWithMetadata(server, "meta1").length);

         locator.close();

         Wait.assertEquals(0, () -> getSessionsWithMetadata(server, "meta1").length);
      } finally {
         try {
            session.close();
         } catch (Throwable e) {
         }

         server.stop();
      }

   }

   private ServerSession[] getSessionsWithMetadata(ActiveMQServer server, String parameter) throws Exception {
      List<ServerSession> sessionList = new LinkedList<>();

      for (ServerSession sess : server.getSessions()) {
         if (sess.getMetaData(parameter) != null) {
            sessionList.add(sess);
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

         Thread tcommitt = new Thread(() -> {
            latchCommit.countDown();
            try {
               session.commit();
            } catch (ActiveMQException e) {
               e.printStackTrace();
            }
         });

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
   public void testClosingConsumerTimeout() throws Exception {
      ActiveMQServer server = createServer(true, true);
      server.start();

      final AtomicBoolean consumerClosed = new AtomicBoolean(false);
      // imitate consumer close timeout
      Interceptor reattachInterceptor = (packet, connection) -> {
         if (!consumerClosed.get() && packet.getType() == PacketImpl.SESS_CONSUMER_CLOSE) {
            consumerClosed.set(true);
            return false;
         } else {
            return true;
         }

      };
      server.getRemotingService().addIncomingInterceptor(reattachInterceptor);

      final long retryInterval = 500;
      final double retryMultiplier = 1d;
      final int reconnectAttempts = 10;
      ServerLocator locator = createFactory(true).setCallTimeout(200).setRetryInterval(retryInterval).setRetryIntervalMultiplier(retryMultiplier).setReconnectAttempts(reconnectAttempts).setConfirmationWindowSize(-1);
      ClientSessionFactoryInternal sf = (ClientSessionFactoryInternal) createSessionFactory(locator);
      runAfter(sf::close);

      ClientSessionInternal session = (ClientSessionInternal)sf.createSession(false, true, true);
      runAfter(session::close);

      SimpleString queueName1 = SimpleString.of("my_queue_one");
      SimpleString addressName1 = SimpleString.of("my_address_one");

      server.addAddressInfo(new AddressInfo(addressName1, RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(queueName1).setAddress(addressName1).setRoutingType(RoutingType.ANYCAST));
      ClientConsumer clientConsumer1 = session.createConsumer(queueName1);
      ClientConsumer clientConsumer2 = session.createConsumer(queueName1);
      clientConsumer1.close();

      Wait.assertTrue(consumerClosed::get);

      Wait.assertEquals(1, () -> getConsumerCount(server, session));

      Wait.assertTrue(() -> matchConsumers(server, session, clientConsumer2), 5000, 100);

      session.close();
      sf.close();
      server.stop();
   }

   boolean matchConsumers(ActiveMQServer server, ClientSessionInternal session, ClientConsumer clientConsumer) {
      ServerSession serverSession = server.getSessionByID(session.getName());
      if (serverSession == null) {
         return false;
      }
      Set<ServerConsumer> serverConsumers = serverSession.getServerConsumers();
      if (serverConsumers == null || serverConsumers.size() != 1) {
         return false;
      }
      ServerConsumer serverConsumer = serverConsumers.iterator().next();
      return clientConsumer.getConsumerContext().getId() == serverConsumer.getID();
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
