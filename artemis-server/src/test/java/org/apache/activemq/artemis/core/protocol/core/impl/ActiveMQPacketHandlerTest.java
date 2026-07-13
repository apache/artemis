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
package org.apache.activemq.artemis.core.protocol.core.impl;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.ActiveMQSecurityException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.mockito.ArgumentCaptor;
import org.apache.activemq.artemis.core.protocol.core.Channel;
import org.apache.activemq.artemis.core.protocol.core.CoreRemotingConnection;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ActiveMQExceptionMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ConnectMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ConnectResponseMessage;
import org.apache.activemq.artemis.core.security.SecurityStore;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.cluster.ha.HAPolicy;
import org.apache.activemq.artemis.core.version.Version;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.apache.activemq.artemis.utils.actors.ArtemisExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

public class ActiveMQPacketHandlerTest {

   private static final long LATCH_TIMEOUT_MS = 5000;

   private ActiveMQServer server;
   private Channel channel1;
   private CoreRemotingConnection connection;
   private CoreProtocolManager protocolManager;
   private SecurityStore securityStore;

   @BeforeEach
   public void setUp() {
      server = mock(ActiveMQServer.class);
      channel1 = mock(Channel.class);
      connection = mock(CoreRemotingConnection.class);
      protocolManager = mock(CoreProtocolManager.class);
      securityStore = mock(SecurityStore.class);

      Version version = mock(Version.class);
      when(version.getIncrementingVersion()).thenReturn(1);

      ExecutorFactory executorFactory = () -> ArtemisExecutor.delegate(Executors.newSingleThreadExecutor());
      when(server.getExecutorFactory()).thenReturn(executorFactory);
      when(server.getSecurityStore()).thenReturn(securityStore);
      when(server.getVersion()).thenReturn(version);
      when(server.getNodeID()).thenReturn(SimpleString.of("test-node"));
      when(server.getHAPolicy()).thenReturn(mock(HAPolicy.class));
   }

   /**
    * When security is enabled, coreConnectionSecurity is enabled, and PLAIN credentials
    * are supplied, validateUser must be called with the decoded username and password.
    */
   @Test
   public void testHandleConnectPlainCredentialsCallsValidateUser() throws Exception {
      when(securityStore.isSecurityEnabled()).thenReturn(true);
      when(protocolManager.isCoreConnectionSecurityEnabled()).thenReturn(true);
      when(server.validateUser(any(), any(), any(), any())).thenReturn("cluster-user");

      CountDownLatch latch = countdownOnChannelSend();

      ConnectMessage msg = ConnectMessage.withPlainCredentials("test-node", 1, "cluster-user", "cluster-pass");
      newHandler().handlePacket(msg);

      latch.await(LATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);

      verify(server).validateUser(eq("cluster-user"), eq("cluster-pass"), eq(connection), isNull());
      verify(channel1).send(any(ConnectResponseMessage.class));
   }

   /**
    * When authMechanism is null (legacy client), authentication must be skipped
    * regardless of security/coreConnectionSecurity settings.
    */
   @Test
   public void testHandleConnectLegacyClientSkipsAuth() throws Exception {
      when(securityStore.isSecurityEnabled()).thenReturn(true);
      when(protocolManager.isCoreConnectionSecurityEnabled()).thenReturn(true);

      CountDownLatch latch = countdownOnChannelSend();

      ConnectMessage legacyMsg = new ConnectMessage("test-node", 1);
      newHandler().handlePacket(legacyMsg);

      latch.await(LATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);

      verify(server, never()).validateUser(any(), any(), any(), any());
      verify(channel1).send(any(ConnectResponseMessage.class));
   }

   /**
    * When security is disabled, validateUser must not be called even when PLAIN
    * credentials are present.
    */
   @Test
   public void testHandleConnectSecurityDisabledSkipsAuth() throws Exception {
      when(securityStore.isSecurityEnabled()).thenReturn(false);
      when(protocolManager.isCoreConnectionSecurityEnabled()).thenReturn(true);

      CountDownLatch latch = countdownOnChannelSend();

      ConnectMessage msg = ConnectMessage.withPlainCredentials("test-node", 1, "u", "p");
      newHandler().handlePacket(msg);

      latch.await(LATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);

      verify(server, never()).validateUser(any(), any(), any(), any());
      verify(channel1).send(any(ConnectResponseMessage.class));
   }

   /**
    * When coreConnectionSecurity is disabled, validateUser must not be called even
    * when PLAIN credentials are present.
    */
   @Test
   public void testHandleConnectCoreConnectionSecurityDisabledSkipsAuth() throws Exception {
      when(securityStore.isSecurityEnabled()).thenReturn(true);
      when(protocolManager.isCoreConnectionSecurityEnabled()).thenReturn(false);

      CountDownLatch latch = countdownOnChannelSend();

      ConnectMessage msg = ConnectMessage.withPlainCredentials("test-node", 1, "u", "p");
      newHandler().handlePacket(msg);

      latch.await(LATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);

      verify(server, never()).validateUser(any(), any(), any(), any());
      verify(channel1).send(any(ConnectResponseMessage.class));
   }

   /**
    * When validateUser throws, an ActiveMQExceptionMessage containing an
    * ActiveMQSecurityException must be sent back and the connection must be closed.
    */
   @Test
   public void testHandleConnectBadCredentialsSendsExceptionAndClosesConnection() throws Exception {
      when(securityStore.isSecurityEnabled()).thenReturn(true);
      when(protocolManager.isCoreConnectionSecurityEnabled()).thenReturn(true);
      when(server.validateUser(any(), any(), any(), any()))
         .thenThrow(new ActiveMQSecurityException("bad credentials"));

      CountDownLatch latch = countdownOnChannelSendAndFlush();

      ConnectMessage msg = ConnectMessage.withPlainCredentials("test-node", 1, "wrong", "wrong");
      newHandler().handlePacket(msg);

      latch.await(LATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);

      ArgumentCaptor<ActiveMQExceptionMessage> captor = ArgumentCaptor.forClass(ActiveMQExceptionMessage.class);
      verify(channel1).sendAndFlush(captor.capture());
      assertInstanceOf(ActiveMQSecurityException.class, captor.getValue().getException(),
         "exception message must carry an ActiveMQSecurityException");
      verify(connection).close();
      verify(channel1, never()).send(any(ConnectResponseMessage.class));
   }

   /**
    * When an unsupported auth mechanism is sent, an ActiveMQExceptionMessage containing
    * an ActiveMQSecurityException must be sent back and the connection must be closed.
    */
   @Test
   public void testHandleConnectUnsupportedMechanismSendsExceptionAndClosesConnection() throws Exception {
      when(securityStore.isSecurityEnabled()).thenReturn(true);
      when(protocolManager.isCoreConnectionSecurityEnabled()).thenReturn(true);

      CountDownLatch latch = countdownOnChannelSendAndFlush();

      ConnectMessage msg = new ConnectMessage("test-node", 1);
      msg.setAuthMechanism("GSSAPI");

      newHandler().handlePacket(msg);

      latch.await(LATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);

      ArgumentCaptor<ActiveMQExceptionMessage> captor = ArgumentCaptor.forClass(ActiveMQExceptionMessage.class);
      verify(channel1).sendAndFlush(captor.capture());
      assertInstanceOf(ActiveMQSecurityException.class, captor.getValue().getException(),
         "exception message must carry an ActiveMQSecurityException");
      verify(connection).close();
      verify(channel1, never()).send(any(ConnectResponseMessage.class));
   }

   /**
    * A second CONNECT packet on the same connection must be ignored and the
    * connection must be closed — only one ConnectResponseMessage must be sent.
    */
   @Test
   public void testHandleConnectDuplicateConnectClosesConnection() throws Exception {
      when(securityStore.isSecurityEnabled()).thenReturn(false);

      // Two packets: first latch counts down on the response, second triggers connection.close()
      CountDownLatch responseLatch = countdownOnChannelSend();
      CountDownLatch closeLatch = new CountDownLatch(1);
      doAnswer((Answer<Void>) inv -> {
         closeLatch.countDown();
         return null;
      }).when(connection).close();

      ConnectMessage msg = new ConnectMessage("test-node", 1);
      ActiveMQPacketHandler handler = newHandler();
      handler.handlePacket(msg);
      responseLatch.await(LATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);

      handler.handlePacket(msg);
      closeLatch.await(LATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);

      verify(channel1).send(any(ConnectResponseMessage.class));
      verify(connection).close();
   }

   private ActiveMQPacketHandler newHandler() {
      return new ActiveMQPacketHandler(protocolManager, server, channel1, connection);
   }

   private CountDownLatch countdownOnChannelSend() {
      CountDownLatch latch = new CountDownLatch(1);
      doAnswer((Answer<Void>) inv -> {
         latch.countDown();
         return null;
      }).when(channel1).send(any());
      return latch;
   }

   private CountDownLatch countdownOnChannelSendAndFlush() {
      CountDownLatch latch = new CountDownLatch(1);
      doAnswer((Answer<Void>) inv -> {
         latch.countDown();
         return null;
      }).when(channel1).sendAndFlush(any());
      return latch;
   }
}
