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
package org.apache.activemq.artemis.core.protocol.openwire.amq;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.netty.buffer.Unpooled;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.core.buffers.impl.ChannelBufferWrapper;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.persistence.impl.nullpm.NullStorageManager;
import org.apache.activemq.artemis.core.protocol.openwire.OpenWireConnection;
import org.apache.activemq.artemis.core.protocol.openwire.OpenWireProtocolManager;
import org.apache.activemq.artemis.core.security.SecurityStore;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.server.cluster.ClusterManager;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.apache.activemq.artemis.utils.actors.OrderedExecutorFactory;
import org.apache.activemq.command.Command;
import org.apache.activemq.command.ConnectionId;
import org.apache.activemq.command.ConnectionInfo;
import org.apache.activemq.command.ProducerInfo;
import org.apache.activemq.command.RemoveInfo;
import org.apache.activemq.command.Response;
import org.apache.activemq.command.SessionInfo;
import org.apache.activemq.command.WireFormatInfo;
import org.apache.activemq.openwire.OpenWireFormat;
import org.apache.activemq.util.ByteSequence;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class OpenWireConnectionTest {

   @Test
   public void testActorStateVisibility() throws Exception {

      OrderedExecutorFactory orderedExecutorFactory = new OrderedExecutorFactory(Executors.newFixedThreadPool(5));
      Executor orderedDelegate = orderedExecutorFactory.getExecutor();

      ClusterManager clusterManager = Mockito.mock(ClusterManager.class);
      ActiveMQServer server = Mockito.mock(ActiveMQServer.class);
      StorageManager storageManager = new NullStorageManager();
      Mockito.when(server.getStorageManager()).thenReturn(storageManager);
      Mockito.when(server.newOperationContext()).thenReturn(storageManager.newContext(orderedExecutorFactory.getExecutor()));
      Mockito.when(server.getClusterManager()).thenReturn(clusterManager);
      Mockito.when(clusterManager.getDefaultConnection(Mockito.any())).thenReturn(null);
      SecurityStore securityStore = Mockito.mock(SecurityStore.class);
      Mockito.when(server.getSecurityStore()).thenReturn(securityStore);
      Mockito.when(securityStore.authenticate(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(null);
      ServerSession serverSession = Mockito.mock(ServerSession.class);
      Mockito.when(serverSession.getName()).thenReturn("session");
      Mockito.doReturn(serverSession).when(server).createSession(Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.anyInt(), Mockito.any(), Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.anyBoolean(),
                                                                 Mockito.anyBoolean(), Mockito.any(), Mockito.any(), Mockito.anyBoolean(), Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.any(), Mockito.anyBoolean());

      OpenWireProtocolManager openWireProtocolManager = new OpenWireProtocolManager(null, server, null, null);
      openWireProtocolManager.setSecurityDomain("securityDomain");
      openWireProtocolManager.setSupportAdvisory(false);

      int commandId = 0;
      WireFormatInfo wireFormatInfo = new WireFormatInfo();
      wireFormatInfo.setVersion(OpenWireFormat.DEFAULT_WIRE_VERSION);
      wireFormatInfo.setMaxFrameSize(1024);
      wireFormatInfo.setMaxInactivityDuration(0); // disable keepalive to simplify
      wireFormatInfo.setCommandId(commandId++);
      OpenWireFormat wf = openWireProtocolManager.wireFormat();

      ConnectionInfo connectionInfo = new ConnectionInfo(new ConnectionId("1:1"));
      connectionInfo.setClientId("client1");
      connectionInfo.setResponseRequired(true);
      connectionInfo.setCommandId(commandId++);

      SessionInfo sessionInfo = new SessionInfo(connectionInfo, 1);
      sessionInfo.setResponseRequired(true);
      sessionInfo.setCommandId(commandId++);

      ProducerInfo producerInfo = new ProducerInfo(sessionInfo, 1);
      producerInfo.setResponseRequired(true);
      producerInfo.setCommandId(commandId++);

      ByteSequence bytes = wf.marshal(wireFormatInfo);
      final ActiveMQBuffer wireFormatInfoBuffer = new ChannelBufferWrapper(Unpooled.buffer(bytes.length));
      wireFormatInfoBuffer.writeBytes(bytes.data, bytes.offset, bytes.length);

      bytes = wf.marshal(connectionInfo);
      final ActiveMQBuffer connectionInfoBuffer = new ChannelBufferWrapper(Unpooled.buffer(bytes.length));
      connectionInfoBuffer.writeBytes(bytes.data, bytes.offset, bytes.length);

      bytes = wf.marshal(sessionInfo);
      final ActiveMQBuffer sessionInfoBuffer = new ChannelBufferWrapper(Unpooled.buffer(bytes.length));
      sessionInfoBuffer.writeBytes(bytes.data, bytes.offset, bytes.length);

      bytes = wf.marshal(producerInfo);
      final ActiveMQBuffer producerInfoBuffer = new ChannelBufferWrapper(Unpooled.buffer(bytes.length));
      producerInfoBuffer.writeBytes(bytes.data, bytes.offset, bytes.length);

      RemoveInfo removeInfo = connectionInfo.createRemoveCommand();
      removeInfo.setCommandId(commandId++);
      removeInfo.setResponseRequired(true);
      bytes = wf.marshal(removeInfo);
      final ActiveMQBuffer removeInfoBuffer = new ChannelBufferWrapper(Unpooled.buffer(bytes.length));
      removeInfoBuffer.writeBytes(bytes.data, bytes.offset, bytes.length);

      Connection connection = Mockito.mock(Connection.class);
      Mockito.doReturn(new ChannelBufferWrapper(Unpooled.buffer(1024))).when(connection).createTransportBuffer(Mockito.anyInt());

      // in a loop do create connection/session/producer/remove to expose thread state sharing visibility
      for (int i = 0; i < 5000; i++) {

         final CountDownLatch okResponses = new CountDownLatch(3);
         final CountDownLatch okResponsesWithRemove = new CountDownLatch(4);

         OpenWireConnection openWireConnection = new OpenWireConnection(connection, server, openWireProtocolManager, wf, orderedDelegate) {
            @Override
            public void physicalSend(Command command) throws IOException {
               if (command.isResponse()) {
                  if (!((Response)command).isException()) {
                     okResponses.countDown();
                     okResponsesWithRemove.countDown();
                  }
               }
            }
         };

         openWireConnection.bufferReceived(openWireConnection, wireFormatInfoBuffer);
         openWireConnection.bufferReceived(openWireConnection, connectionInfoBuffer);
         // actor tasks
         openWireConnection.bufferReceived(openWireConnection, sessionInfoBuffer);
         openWireConnection.bufferReceived(openWireConnection, producerInfoBuffer);

         assertTrue(okResponses.await(10, TimeUnit.SECONDS), "fail on ok response check, iteration: " + i);

         openWireConnection.bufferReceived(openWireConnection, removeInfoBuffer);

         assertTrue(okResponsesWithRemove.await(10, TimeUnit.SECONDS), "fail on ok response check with remove, iteration: " + i);

         wireFormatInfoBuffer.resetReaderIndex();
         connectionInfoBuffer.resetReaderIndex();
         sessionInfoBuffer.resetReaderIndex();
         producerInfoBuffer.resetReaderIndex();
         removeInfoBuffer.resetReaderIndex();

      }
   }

}
