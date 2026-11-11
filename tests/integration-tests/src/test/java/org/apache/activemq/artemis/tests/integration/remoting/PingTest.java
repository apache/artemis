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

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.client.SessionFailureListener;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryImpl;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryInternal;
import org.apache.activemq.artemis.core.protocol.core.CoreRemotingConnection;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.Ping;
import org.apache.activemq.artemis.core.remoting.CloseListener;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PingTest extends ActiveMQTestBase {

   private static final long CLIENT_FAILURE_CHECK_PERIOD = 500;

   private ActiveMQServer server;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      server = createServer(false, createDefaultNettyConfig());
      server.start();
   }

   class Listener implements SessionFailureListener {

      volatile ActiveMQException me;

      @Override
      public void connectionFailed(final ActiveMQException me, boolean failedOver) {
         this.me = me;
      }

      @Override
      public void connectionFailed(final ActiveMQException me, boolean failedOver, String scaleDownTargetNodeID) {
         connectionFailed(me, failedOver);
      }

      public ActiveMQException getException() {
         return me;
      }

      @Override
      public void beforeReconnect(final ActiveMQException exception) {
      }
   }

   /**
    * Test that no failure listeners are triggered in a non failure case with pinging going on
    */
   @Test
   public void testNoFailureWithPinging() throws Exception {
      ServerLocator locator = createNettyNonHALocator();

      locator.setClientFailureCheckPeriod(PingTest.CLIENT_FAILURE_CHECK_PERIOD);
      locator.setConnectionTTL(PingTest.CLIENT_FAILURE_CHECK_PERIOD * 2);

      ClientSessionFactory csf = createSessionFactory(locator);

      ClientSession session = csf.createSession(false, true, true);

      assertEquals(1, ((ClientSessionFactoryInternal) csf).numConnections());

      Listener clientListener = new Listener();

      session.addFailureListener(clientListener);

      RemotingConnection serverConn = null;
      while (serverConn == null) {
         Set<RemotingConnection> conns = server.getRemotingService().getConnections();

         if (!conns.isEmpty()) {
            serverConn = server.getRemotingService().getConnections().iterator().next();
         } else {
            // It's async so need to wait a while
            Thread.sleep(10);
         }
      }

      Listener serverListener = new Listener();

      serverConn.addFailureListener(serverListener);

      Thread.sleep(PingTest.CLIENT_FAILURE_CHECK_PERIOD * 10);

      assertNull(clientListener.getException());

      assertNull(serverListener.getException());

      RemotingConnection serverConn2 = server.getRemotingService().getConnections().iterator().next();

      assertSame(serverConn, serverConn2);

      session.close();

      csf.close();

      locator.close();
   }

   /**
    * Test that no failure listeners are triggered in a non failure case with no pinging going on
    */
   @Test
   public void testNoFailureNoPinging() throws Exception {
      TransportConfiguration transportConfig = new TransportConfiguration(INVM_CONNECTOR_FACTORY);
      ServerLocator locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(transportConfig));
      locator.setClientFailureCheckPeriod(-1);
      locator.setConnectionTTL(-1);
      ClientSessionFactory csf = createSessionFactory(locator);

      ClientSession session = csf.createSession(false, true, true);

      assertEquals(1, ((ClientSessionFactoryInternal) csf).numConnections());

      Listener clientListener = new Listener();

      session.addFailureListener(clientListener);

      RemotingConnection serverConn = null;
      while (serverConn == null) {
         Set<RemotingConnection> conns = server.getRemotingService().getConnections();

         if (!conns.isEmpty()) {
            serverConn = server.getRemotingService().getConnections().iterator().next();
         } else {
            // It's async so need to wait a while
            Thread.sleep(10);
         }
      }

      Listener serverListener = new Listener();

      serverConn.addFailureListener(serverListener);

      Thread.sleep(ActiveMQClient.DEFAULT_CLIENT_FAILURE_CHECK_PERIOD);

      assertNull(clientListener.getException());

      assertNull(serverListener.getException());

      RemotingConnection serverConn2 = server.getRemotingService().getConnections().iterator().next();

      assertSame(serverConn, serverConn2);

      session.close();

      csf.close();

      locator.close();
   }

   /**
    * Test that pinging is disabled for in-vm connection when using the default settings
    */
   @Test
   public void testNoPingingOnInVMConnection() throws Exception {
      // server should receive one and only one ping from the client so that
      // the server connection TTL is configured with the client value
      final CountDownLatch requiredPings = new CountDownLatch(1);
      final CountDownLatch unwantedPings = new CountDownLatch(2);
      server.getRemotingService().addIncomingInterceptor((Interceptor) (packet, connection) -> {
         if (packet.getType() == PacketImpl.PING) {
            assertEquals(ActiveMQClient.DEFAULT_CONNECTION_TTL_INVM, ((Ping) packet).getConnectionTTL());
            unwantedPings.countDown();
            requiredPings.countDown();
         }
         return true;
      });

      TransportConfiguration transportConfig = new TransportConfiguration("org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory");
      ServerLocator locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(transportConfig));
      ClientSessionFactory csf = createSessionFactory(locator);

      ClientSession session = csf.createSession(false, true, true);

      assertEquals(1, ((ClientSessionFactoryInternal) csf).numConnections());

      assertTrue(requiredPings.await(5000, TimeUnit.MILLISECONDS), "server didn't received an expected ping from the client");

      assertFalse(unwantedPings.await(ActiveMQClient.DEFAULT_CONNECTION_TTL * 2, TimeUnit.MILLISECONDS), "server received an unexpected ping from the client");

      session.close();

      csf.close();

      locator.close();
   }

   /**
    * Test the server timing out a connection since it doesn't receive a ping in time
    */
   @Test
   public void testServerFailureNoPing() throws Exception {
      TransportConfiguration transportConfig = new TransportConfiguration(INVM_CONNECTOR_FACTORY);
      ServerLocator locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(transportConfig));
      locator.setClientFailureCheckPeriod(PingTest.CLIENT_FAILURE_CHECK_PERIOD);
      locator.setConnectionTTL(PingTest.CLIENT_FAILURE_CHECK_PERIOD * 2);
      ClientSessionFactoryImpl csf = (ClientSessionFactoryImpl) createSessionFactory(locator);

      Listener clientListener = new Listener();

      ClientSession session = csf.createSession(false, true, true);

      assertEquals(1, csf.numConnections());

      session.addFailureListener(clientListener);

      // We need to get it to stop pinging after one

      csf.stopPingingAfterOne();

      RemotingConnection serverConn = null;

      while (serverConn == null) {
         Set<RemotingConnection> conns = server.getRemotingService().getConnections();

         if (!conns.isEmpty()) {
            serverConn = server.getRemotingService().getConnections().iterator().next();
         } else {
            // It's async so need to wait a while
            Thread.sleep(10);
         }
      }

      Listener serverListener = new Listener();

      serverConn.addFailureListener(serverListener);

      for (int i = 0; i < 1000; i++) {
         // a few tries to avoid a possible race caused by GCs or similar issues
         if (server.getRemotingService().getConnections().isEmpty() && clientListener.getException() != null) {
            break;
         }

         Thread.sleep(10);
      }

      if (!server.getRemotingService().getConnections().isEmpty()) {
         RemotingConnection serverConn2 = server.getRemotingService().getConnections().iterator().next();
      }

      assertTrue(server.getRemotingService().getConnections().isEmpty());

      // The client listener should be called too since the server will close it from the server side which will result
      // in the
      // netty detecting closure on the client side and then calling failure listener
      assertNotNull(clientListener.getException());

      assertNotNull(serverListener.getException());

      session.close();

      csf.close();

      locator.close();
   }

   /**
    * Test the client triggering failure due to no ping from server received in time
    */
   @Test
   public void testClientFailureNoServerPing() throws Exception {
      // server must received at least one ping from the client to pass
      // so that the server connection TTL is configured with the client value
      final CountDownLatch pingOnServerLatch = new CountDownLatch(2);
      server.getRemotingService().addIncomingInterceptor((Interceptor) (packet, connection) -> {
         if (packet.getType() == PacketImpl.PING) {
            pingOnServerLatch.countDown();
         }
         return true;
      });

      TransportConfiguration transportConfig = new TransportConfiguration(INVM_CONNECTOR_FACTORY);
      ServerLocator locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(transportConfig));
      locator.setClientFailureCheckPeriod(PingTest.CLIENT_FAILURE_CHECK_PERIOD);
      locator.setConnectionTTL(PingTest.CLIENT_FAILURE_CHECK_PERIOD * 2);
      ClientSessionFactory csf = createSessionFactory(locator);

      ClientSession session = csf.createSession(false, true, true);

      assertEquals(1, ((ClientSessionFactoryInternal) csf).numConnections());

      final CountDownLatch clientLatch = new CountDownLatch(1);
      SessionFailureListener clientListener = new SessionFailureListener() {
         @Override
         public void connectionFailed(final ActiveMQException me, boolean failedOver) {
            clientLatch.countDown();
         }

         @Override
         public void connectionFailed(final ActiveMQException me, boolean failedOver, String scaleDownTargetNodeID) {
            connectionFailed(me, failedOver);
         }

         @Override
         public void beforeReconnect(final ActiveMQException exception) {
         }
      };

      final CountDownLatch serverLatch = new CountDownLatch(1);
      CloseListener serverListener = () -> serverLatch.countDown();

      session.addFailureListener(clientListener);

      CoreRemotingConnection serverConn = null;
      while (serverConn == null) {
         Set<RemotingConnection> conns = server.getRemotingService().getConnections();

         if (!conns.isEmpty()) {
            serverConn = (CoreRemotingConnection) server.getRemotingService().getConnections().iterator().next();
         } else {
            // It's async so need to wait a while
            Thread.sleep(10);
         }
      }

      serverConn.addCloseListener(serverListener);
      assertTrue(pingOnServerLatch.await(4000, TimeUnit.MILLISECONDS), "server has not received any ping from the client");

      // we let the server receives at least 1 ping (so that it uses the client ConnectionTTL value)

      // Setting the handler to null will prevent server sending pings back to client
      serverConn.getChannel(0, -1).setHandler(null);

      assertTrue(clientLatch.await(8 * PingTest.CLIENT_FAILURE_CHECK_PERIOD, TimeUnit.MILLISECONDS));

      // Server connection will be closed too, when client closes client side connection after failure is detected
      assertTrue(serverLatch.await(2 * server.getConfiguration().getConnectionTtlCheckInterval(), TimeUnit.MILLISECONDS));

      long start = System.currentTimeMillis();
      while (true) {
         if (!server.getRemotingService().getConnections().isEmpty() && System.currentTimeMillis() - start < 10000) {
            Thread.sleep(500);
         } else {
            break;
         }
      }
      assertTrue(server.getRemotingService().getConnections().isEmpty());

      session.close();

      csf.close();

      locator.close();
   }
}
