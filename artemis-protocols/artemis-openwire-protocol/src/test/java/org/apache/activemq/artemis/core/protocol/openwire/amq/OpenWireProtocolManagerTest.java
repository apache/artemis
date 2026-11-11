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

import java.util.concurrent.Executors;

import io.netty.buffer.Unpooled;
import org.apache.activemq.artemis.api.core.client.TopologyMember;
import org.apache.activemq.artemis.core.buffers.impl.ChannelBufferWrapper;
import org.apache.activemq.artemis.core.client.impl.TopologyMemberImpl;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.persistence.impl.nullpm.NullStorageManager;
import org.apache.activemq.artemis.core.protocol.openwire.OpenWireConnection;
import org.apache.activemq.artemis.core.protocol.openwire.OpenWireProtocolManager;
import org.apache.activemq.artemis.core.security.SecurityStore;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.server.cluster.ClusterManager;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.apache.activemq.artemis.utils.ActiveMQThreadFactory;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.actors.ArtemisExecutor;
import org.apache.activemq.command.ConnectionId;
import org.apache.activemq.command.ConnectionInfo;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class OpenWireProtocolManagerTest {

   @Test
   public void testNullPrimaryOnNodeUp() throws Exception {

      ArtemisExecutor executor = ArtemisExecutor.delegate(Executors.newSingleThreadExecutor(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName())));

      try {
         ClusterManager clusterManager = Mockito.mock(ClusterManager.class);
         ActiveMQServer server = Mockito.mock(ActiveMQServer.class);
         StorageManager storageManager = new NullStorageManager();
         Mockito.when(server.getStorageManager()).thenReturn(storageManager);
         Mockito.when(server.newOperationContext()).thenReturn(storageManager.newContext(executor));
         Mockito.when(server.getClusterManager()).thenReturn(clusterManager);
         Mockito.when(clusterManager.getDefaultConnection(Mockito.any())).thenReturn(null);
         SecurityStore securityStore = Mockito.mock(SecurityStore.class);
         Mockito.when(server.getSecurityStore()).thenReturn(securityStore);
         Mockito.when(securityStore.authenticate(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(null);
         ServerSession serverSession = Mockito.mock(ServerSession.class);
         Mockito.when(serverSession.getName()).thenReturn("session");
         Mockito.doReturn(serverSession).when(server).createSession(Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.anyInt(), Mockito.any(), Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.any(), Mockito.any(), Mockito.anyBoolean(), Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.any(), Mockito.anyBoolean());

         OpenWireProtocolManager openWireProtocolManager = new OpenWireProtocolManager(null, server, null, null);
         openWireProtocolManager.setSecurityDomain("securityDomain");
         openWireProtocolManager.setSupportAdvisory(false);
         Connection connection = Mockito.mock(Connection.class);
         Mockito.doReturn(new ChannelBufferWrapper(Unpooled.buffer(1024))).when(connection).createTransportBuffer(Mockito.anyInt());
         OpenWireConnection openWireConnection = new OpenWireConnection(connection, server, openWireProtocolManager, openWireProtocolManager.wireFormat(), executor);
         ConnectionInfo connectionInfo = new ConnectionInfo(new ConnectionId("1:1"));
         connectionInfo.setClientId(RandomUtil.randomUUIDString());
         openWireProtocolManager.addConnection(openWireConnection, connectionInfo);

         TopologyMember topologyMember = new TopologyMemberImpl(RandomUtil.randomUUIDString(), null, null, null, null);
         openWireProtocolManager.nodeUP(topologyMember, false);
      } finally {
         executor.shutdown();
      }
   }
}
