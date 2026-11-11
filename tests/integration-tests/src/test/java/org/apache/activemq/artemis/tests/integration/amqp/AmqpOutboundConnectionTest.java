/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.tests.integration.amqp;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.nullValue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQRemoteDisconnectException;
import org.apache.activemq.artemis.core.remoting.FailureListener;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnector;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.protocol.amqp.broker.ProtonProtocolManagerFactory;
import org.apache.activemq.artemis.protocol.amqp.client.AMQPClientConnectionFactory;
import org.apache.activemq.artemis.protocol.amqp.client.ProtonClientConnectionManager;
import org.apache.activemq.artemis.protocol.amqp.client.ProtonClientProtocolManager;
import org.apache.activemq.artemis.protocol.amqp.proton.handler.EventHandler;
import org.apache.activemq.artemis.protocol.amqp.sasl.ClientSASL;
import org.apache.activemq.artemis.protocol.amqp.sasl.ClientSASLFactory;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.engine.Connection;
import org.apache.qpid.protonj2.test.driver.ProtonTestServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class AmqpOutboundConnectionTest extends AmqpClientTestSupport {

   private boolean securityEnabled;

   @Test
   @Timeout(60)
   public void testOutboundConnection() throws Throwable {
      runOutboundConnectionTest(false, true);
   }

   @Test
   @Timeout(60)
   public void testOutboundConnectionServerClose() throws Throwable {
      runOutboundConnectionTest(false, false);
   }

   @Test
   @Timeout(60)
   public void testOutboundConnectionWithSecurity() throws Throwable {
      runOutboundConnectionTest(true, true);
   }

   private void runOutboundConnectionTest(boolean withSecurity, boolean closeFromClient) throws Exception {
      final ActiveMQServer remote;
      try {
         securityEnabled = withSecurity;
         remote = createServer(AMQP_PORT + 1);
      } finally {
         securityEnabled = false;
      }

      Wait.assertTrue(remote::isActive);

      final Map<String, Object> config = new LinkedHashMap<>(); config.put(TransportConstants.HOST_PROP_NAME, "localhost");
      config.put(TransportConstants.PORT_PROP_NAME, String.valueOf(AMQP_PORT + 1));
      final ClientSASLFactory clientSASLFactory;
      if (withSecurity) {
         clientSASLFactory = availableMechanims -> {
            if (availableMechanims != null && Arrays.asList(availableMechanims).contains("PLAIN")) {
               return new PlainSASLMechanism(fullUser, fullPass);
            } else {
               return null;
            }
         };
      } else {
         clientSASLFactory = null;
      }
      final AtomicBoolean connectionOpened = new AtomicBoolean();

      EventHandler eventHandler = new EventHandler() {
         @Override
         public void onRemoteOpen(Connection connection) throws Exception {
            connectionOpened.set(true);
         }
      };

      ProtonClientConnectionManager lifeCycleListener = new ProtonClientConnectionManager(new AMQPClientConnectionFactory(server, "myid", Collections.singletonMap(Symbol.getSymbol("myprop"), "propvalue"), 5000), Optional.of(eventHandler), clientSASLFactory);
      ProtonClientProtocolManager protocolManager = new ProtonClientProtocolManager(new ProtonProtocolManagerFactory(), server);
      NettyConnector connector = new NettyConnector(config, lifeCycleListener, lifeCycleListener, server.getExecutorFactory().getExecutor(), server.getExecutorFactory().getExecutor(), server.getScheduledPool(), protocolManager);
      connector.start();

      Object connectionId = connector.createConnection().getID();
      assertNotNull(connectionId);
      RemotingConnection remotingConnection = lifeCycleListener.getConnection(connectionId);

      AtomicReference<ActiveMQException> ex = new AtomicReference<>();
      AtomicBoolean closed = new AtomicBoolean(false);
      remotingConnection.addCloseListener(() -> closed.set(true));
      remotingConnection.addFailureListener(new FailureListener() {
         @Override
         public void connectionFailed(ActiveMQException exception, boolean failedOver) {
            ex.set(exception);
         }

         @Override
         public void connectionFailed(ActiveMQException exception, boolean failedOver, String scaleDownTargetNodeID) {
            ex.set(exception);
         }
      });

      try {
         Wait.assertEquals(1, remote::getConnectionCount);
         Wait.assertTrue(connectionOpened::get);
         if (closeFromClient) {
            lifeCycleListener.stop();
         } else {
            remote.stop();
         }

         Wait.assertEquals(0, remote::getConnectionCount);
         assertTrue(remotingConnection.isDestroyed());
         if (!closeFromClient) {
            assertInstanceOf(ActiveMQRemoteDisconnectException.class, ex.get());
         } else {
            assertNull(ex.get());
         }
      } finally {
         if (closeFromClient) {
            remote.stop();
         } else {
            lifeCycleListener.stop();
         }
      }
   }

   @Test
   @Timeout(20)
   public void testOutboundConnectsWithOfferedAndDesiredCapabilities() throws Exception {
      // Tests that the underlying AMQPConnectionContext will honor the set offered and desired capabilities
      // and place them in the outgoing Open performative.
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect("PLAIN", "ANONYMOUS");
         peer.expectOpen().withOfferedCapability("ANONYMOUS_RELAY")
                          .withDesiredCapability("SHARED_SUBS")
                          .respond();
         peer.start();

         final Map<String, Object> config = new LinkedHashMap<>(); config.put(TransportConstants.HOST_PROP_NAME, "localhost");
         config.put(TransportConstants.PORT_PROP_NAME, String.valueOf(peer.getServerURI().getPort()));

         final ClientSASLFactory clientSASLFactory = availableMechanims -> {
            if (availableMechanims != null && Arrays.asList(availableMechanims).contains("ANONYMOUS")) {
               return new AnonymousSASLMechanism();
            } else {
               return null;
            }
         };

         final AtomicBoolean connectionOpened = new AtomicBoolean();

         EventHandler eventHandler = new EventHandler() {
            @Override
            public void onRemoteOpen(Connection connection) throws Exception {
               connectionOpened.set(true);
            }
         };

         final Symbol[] offeredCapabilities = new Symbol[] {Symbol.valueOf("ANONYMOUS_RELAY")};
         final Symbol[] desiredCapabilities = new Symbol[] {Symbol.valueOf("SHARED_SUBS")};

         AMQPClientConnectionFactory clientFactory = new AMQPClientConnectionFactory(server, "myid", Collections.singletonMap(Symbol.getSymbol("myprop"), "propvalue"), 5000, offeredCapabilities, desiredCapabilities);
         ProtonClientConnectionManager lifeCycleListener = new ProtonClientConnectionManager(clientFactory, Optional.of(eventHandler), clientSASLFactory);
         ProtonClientProtocolManager protocolManager = new ProtonClientProtocolManager(new ProtonProtocolManagerFactory(), server);
         NettyConnector connector = new NettyConnector(config, lifeCycleListener, lifeCycleListener, server.getExecutorFactory().getExecutor(), server.getExecutorFactory().getExecutor(), server.getScheduledPool(), protocolManager);

         try {
            connector.start();

            assertNotNull(connector.createConnection().getID());

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            Wait.assertTrue(connectionOpened::get);
         } finally {
            lifeCycleListener.stop();
         }
      }
   }

   @Test
   @Timeout(20)
   public void testOutboundTreatsEmptyOfferedAndDesiredAsNoCapabilities() throws Exception {
      // Tests that the underlying AMQPConnectionContext will treat empty offered and desired capabilities
      // arrays as being nothing to send and proceeding as normal with old default behavior of sending nothing.
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect("PLAIN", "ANONYMOUS");
         peer.expectOpen().withOfferedCapabilities(nullValue())
                          .withDesiredCapabilities(nullValue())
                          .respond();
         peer.start();

         final Map<String, Object> config = new LinkedHashMap<>(); config.put(TransportConstants.HOST_PROP_NAME, "localhost");
         config.put(TransportConstants.PORT_PROP_NAME, String.valueOf(peer.getServerURI().getPort()));

         final ClientSASLFactory clientSASLFactory = availableMechanims -> {
            if (availableMechanims != null && Arrays.asList(availableMechanims).contains("ANONYMOUS")) {
               return new AnonymousSASLMechanism();
            } else {
               return null;
            }
         };

         final AtomicBoolean connectionOpened = new AtomicBoolean();

         EventHandler eventHandler = new EventHandler() {
            @Override
            public void onRemoteOpen(Connection connection) throws Exception {
               connectionOpened.set(true);
            }
         };

         final Symbol[] offeredCapabilities = new Symbol[0];
         final Symbol[] desiredCapabilities = new Symbol[0];

         AMQPClientConnectionFactory clientFactory = new AMQPClientConnectionFactory(server, "myid", Collections.singletonMap(Symbol.getSymbol("myprop"), "propvalue"), 5000, offeredCapabilities, desiredCapabilities);
         ProtonClientConnectionManager lifeCycleListener = new ProtonClientConnectionManager(clientFactory, Optional.of(eventHandler), clientSASLFactory);
         ProtonClientProtocolManager protocolManager = new ProtonClientProtocolManager(new ProtonProtocolManagerFactory(), server);
         NettyConnector connector = new NettyConnector(config, lifeCycleListener, lifeCycleListener, server.getExecutorFactory().getExecutor(), server.getExecutorFactory().getExecutor(), server.getScheduledPool(), protocolManager);

         try {
            connector.start();

            assertNotNull(connector.createConnection().getID());

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            Wait.assertTrue(connectionOpened::get);
         } finally {
            lifeCycleListener.stop();
         }
      }
   }

   @Test
   @Timeout(20)
   public void testOutboundRemainsDefaultedToNoOfferedOrDesiredCapabilities() throws Exception {
      // Tests that the underlying AMQPConnectionContext retains its default behavior of not sending
      // offered or desired capabilities if not told to do so.
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect("PLAIN", "ANONYMOUS");
         peer.expectOpen().withOfferedCapabilities(nullValue())
                          .withDesiredCapabilities(nullValue())
                          .respond();
         peer.start();

         final Map<String, Object> config = new LinkedHashMap<>(); config.put(TransportConstants.HOST_PROP_NAME, "localhost");
         config.put(TransportConstants.PORT_PROP_NAME, String.valueOf(peer.getServerURI().getPort()));

         final ClientSASLFactory clientSASLFactory = availableMechanims -> {
            if (availableMechanims != null && Arrays.asList(availableMechanims).contains("ANONYMOUS")) {
               return new AnonymousSASLMechanism();
            } else {
               return null;
            }
         };

         final AtomicBoolean connectionOpened = new AtomicBoolean();

         EventHandler eventHandler = new EventHandler() {
            @Override
            public void onRemoteOpen(Connection connection) throws Exception {
               connectionOpened.set(true);
            }
         };

         AMQPClientConnectionFactory clientFactory = new AMQPClientConnectionFactory(server, "myid", Collections.singletonMap(Symbol.getSymbol("myprop"), "propvalue"), 5000);
         ProtonClientConnectionManager lifeCycleListener = new ProtonClientConnectionManager(clientFactory, Optional.of(eventHandler), clientSASLFactory);
         ProtonClientProtocolManager protocolManager = new ProtonClientProtocolManager(new ProtonProtocolManagerFactory(), server);
         NettyConnector connector = new NettyConnector(config, lifeCycleListener, lifeCycleListener, server.getExecutorFactory().getExecutor(), server.getExecutorFactory().getExecutor(), server.getScheduledPool(), protocolManager);

         try {
            connector.start();

            assertNotNull(connector.createConnection().getID());

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            Wait.assertTrue(connectionOpened::get);
         } finally {
            lifeCycleListener.stop();
         }
      }
   }
   @Override
   protected boolean isSecurityEnabled() {
      return securityEnabled;
   }

   private static class PlainSASLMechanism implements ClientSASL {

      private final byte[] initialResponse;

      PlainSASLMechanism(String username, String password) {
         byte[] usernameBytes = username.getBytes(StandardCharsets.UTF_8);
         byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);
         byte[] encoded = new byte[usernameBytes.length + passwordBytes.length + 2];
         System.arraycopy(usernameBytes, 0, encoded, 1, usernameBytes.length);
         System.arraycopy(passwordBytes, 0, encoded, usernameBytes.length + 2, passwordBytes.length);
         initialResponse = encoded;
      }

      @Override
      public String getName() {
         return "PLAIN";
      }

      @Override
      public byte[] getInitialResponse() {
         return initialResponse;
      }

      @Override
      public byte[] getResponse(byte[] challenge) {
         return new byte[0];
      }
   }

   private static class AnonymousSASLMechanism implements ClientSASL {

      @Override
      public String getName() {
         return "ANONYMOUS";
      }

      @Override
      public byte[] getInitialResponse() {
         return null;
      }

      @Override
      public byte[] getResponse(byte[] challenge) {
         return new byte[0];
      }
   }
}
