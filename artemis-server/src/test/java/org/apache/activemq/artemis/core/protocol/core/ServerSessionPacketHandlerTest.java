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
package org.apache.activemq.artemis.core.protocol.core;

import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionCommitMessage_V2;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.apache.activemq.artemis.utils.actors.ArtemisExecutor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ServerSessionPacketHandlerTest {

   @Test
   public void testReleaseNullResponse() throws Exception {
      int numberOfMessages = 10;
      CountDownLatch latch = new CountDownLatch(numberOfMessages);

      // Mock storage manager
      StorageManager storageManager = Mockito.mock(StorageManager.class);

      // just call done
      Mockito.doAnswer(invocation -> {
         invocation.getArgument(0, IOCallback.class).done();

         latch.countDown();
         return null;
      }).when(storageManager).afterCompleteOperations(Mockito.any(IOCallback.class));

      // Mock server
      ActiveMQServer server = Mockito.mock(ActiveMQServer.class);

      Mockito.when(server.getExecutorFactory()).thenReturn(() -> ArtemisExecutor.delegate(Executors.newSingleThreadExecutor()));
      Mockito.when(server.getStorageManager()).thenReturn(storageManager);

      // Mock session
      ServerSession session = Mockito.mock(ServerSession.class);

      // Mock connection
      Connection connection = Mockito.mock(Connection.class);

      // Mock remoting connection
      CoreRemotingConnection remotingConnection = Mockito.mock(CoreRemotingConnection.class);

      Mockito.when(remotingConnection.getTransportConnection()).thenReturn(connection);
      Mockito.when(remotingConnection.isVersionBeforeAsyncResponseChange()).thenReturn(true);

      // Mock channel
      Channel channel = Mockito.mock(Channel.class);

      Mockito.when(channel.getConfirmationWindowSize()).thenReturn(-1);
      Mockito.when(channel.getConnection()).thenReturn(remotingConnection);

      ServerSessionPacketHandler handler = new ServerSessionPacketHandler(server, session, channel);

      for (int i = 0; i < numberOfMessages; i++) {
         handler.handlePacket(requiresNullResponseMessage(i));
      }

      Assertions.assertTrue(latch.await(100, TimeUnit.MILLISECONDS));
      Assertions.assertNotEquals(0, handler.poolNullResponse.size());

      Mockito.verify(channel, Mockito.times(numberOfMessages)).send(Mockito.argThat(packet -> packet.getCorrelationID() == -1));
   }

   @Test
   public void testReleaseNullResponse_V2() throws Exception {
      int numberOfMessages = 10;
      CountDownLatch latch = new CountDownLatch(numberOfMessages);

      // Mock storage manager
      StorageManager storageManager = Mockito.mock(StorageManager.class);

      // just call done
      Mockito.doAnswer(invocation -> {
         invocation.getArgument(0, IOCallback.class).done();

         latch.countDown();
         return null;
      }).when(storageManager).afterCompleteOperations(Mockito.any(IOCallback.class));

      // Mock server
      ActiveMQServer server = Mockito.mock(ActiveMQServer.class);

      Mockito.when(server.getExecutorFactory()).thenReturn(() -> ArtemisExecutor.delegate(Executors.newSingleThreadExecutor()));
      Mockito.when(server.getStorageManager()).thenReturn(storageManager);

      // Mock session
      ServerSession session = Mockito.mock(ServerSession.class);

      // Mock connection
      Connection connection = Mockito.mock(Connection.class);

      // Mock remoting connection
      CoreRemotingConnection remotingConnection = Mockito.mock(CoreRemotingConnection.class);

      Mockito.when(remotingConnection.getTransportConnection()).thenReturn(connection);
      Mockito.when(remotingConnection.isVersionBeforeAsyncResponseChange()).thenReturn(false);

      // Mock channel
      Channel channel = Mockito.mock(Channel.class);

      Mockito.when(channel.getConfirmationWindowSize()).thenReturn(-1);
      Mockito.when(channel.getConnection()).thenReturn(remotingConnection);

      ServerSessionPacketHandler handler = new ServerSessionPacketHandler(server, session, channel);

      for (int i = 0; i < numberOfMessages; i++) {
         handler.handlePacket(requiresNullResponseMessage(i));
      }

      Assertions.assertTrue(latch.await(100, TimeUnit.MILLISECONDS));
      Assertions.assertNotEquals(0, handler.poolNullResponse.size());

      Mockito.verify(channel, Mockito.times(numberOfMessages)).send(Mockito.argThat(packet -> packet.getCorrelationID() != -1));
   }

   private Packet requiresNullResponseMessage(long correlationID) {
      SessionCommitMessage_V2 message = new SessionCommitMessage_V2();
      message.setCorrelationID(correlationID);
      return message;
   }
}