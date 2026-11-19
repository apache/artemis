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
package org.apache.activemq.artemis.protocol.amqp.broker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.api.core.ActiveMQSecurityException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.client.impl.Topology;
import org.apache.activemq.artemis.core.client.impl.TopologyMemberImpl;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnection;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnection;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.security.SecurityStore;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.cluster.ClusterConnection;
import org.apache.activemq.artemis.core.server.cluster.ClusterManager;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.protocol.amqp.sasl.AnonymousServerSASL;
import org.apache.activemq.artemis.protocol.amqp.sasl.GSSAPIServerSASL;
import org.apache.activemq.artemis.protocol.amqp.sasl.PlainSASL;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.apache.activemq.artemis.utils.actors.ArtemisExecutor;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class AMQPConnectionCallbackTest {

   @Test
   public void getServerSASLOnlyAllowedMechs() throws Exception {
      ProtonProtocolManager protonProtocolManager = new ProtonProtocolManager(new ProtonProtocolManagerFactory(), null, null, null);
      protonProtocolManager.setSaslMechanisms(new String[]{PlainSASL.NAME});
      AMQPConnectionCallback connectionCallback = new AMQPConnectionCallback(protonProtocolManager, new InVMConnection(1, null, null, null), null, new ActiveMQServerImpl());
      assertEquals(1, connectionCallback.getSaslMechanisms().length);
      for (String mech: connectionCallback.getSaslMechanisms()) {
         assertNotNull(connectionCallback.getServerSASL(mech));
      }
      assertNull(connectionCallback.getServerSASL(GSSAPIServerSASL.NAME), "can't get mechanism not in the list");
   }

   @Test
   public void getServerSASLAnonDefault() throws Exception {
      ProtonProtocolManager protonProtocolManager = new ProtonProtocolManager(new ProtonProtocolManagerFactory(), null, null, null);
      protonProtocolManager.setSaslMechanisms(new String[]{});
      AMQPConnectionCallback connectionCallback = new AMQPConnectionCallback(protonProtocolManager, null, null, new ActiveMQServerImpl());
      assertNotNull(connectionCallback.getServerSASL(AnonymousServerSASL.NAME), "can get anon with empty list");
   }

   @Test
   public void testAnonymousSupportCheck() throws Exception {
      ArtemisExecutor executor = Mockito.mock(ArtemisExecutor.class);
      ExecutorFactory executorFactory = Mockito.mock(ExecutorFactory.class);
      Mockito.when(executorFactory.getExecutor()).thenReturn(executor);

      SecurityStore securityStore = Mockito.mock(SecurityStore.class);

      ActiveMQServer server = Mockito.mock(ActiveMQServer.class);
      Mockito.when(server.getExecutorFactory()).thenReturn(executorFactory);
      Mockito.when(server.getSecurityStore()).thenReturn(securityStore);

      NettyConnection transportConnection = Mockito.mock(NettyConnection.class);
      ProtonProtocolManager protocolManager = Mockito.mock(ProtonProtocolManager.class);
      Mockito.when(protocolManager.getServer()).thenReturn(server);

      AMQPConnectionCallback callback = new AMQPConnectionCallback(protocolManager, transportConnection, executor, server);
      ActiveMQProtonRemotingConnection connectionDelegate = Mockito.mock(ActiveMQProtonRemotingConnection.class);
      callback.setProtonConnectionDelegate(connectionDelegate);

      // Make it succeed
      Mockito.when(securityStore.authenticate(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn("validatedAnonUser");

      // Verify result and expected args are passed
      assertTrue(callback.isSupportsAnonymous());
      Mockito.verify(securityStore).authenticate(Mockito.any(), Mockito.any(), Mockito.same(connectionDelegate));

      // Make it fail
      Mockito.reset(securityStore);
      Mockito.when(securityStore.authenticate(Mockito.any(), Mockito.any(), Mockito.any())).thenThrow(new ActiveMQSecurityException("auth-failed"));

      // Verify result and expected args are passed
      assertFalse(callback.isSupportsAnonymous());
      Mockito.verify(securityStore).authenticate(Mockito.any(), Mockito.any(), Mockito.same(connectionDelegate));
   }

   @Test
   public void testGetFailoverListFailoverEnabled() throws Exception {
      ActiveMQServer server = Mockito.mock(ActiveMQServer.class);

      TransportConfiguration backup = createBackupTransportConfiguration();
      backup.getParams().put(TransportConstants.CLIENT_FAILOVER_ADVERTISING_ENABLED_PROP_NAME, true);

      AMQPConnectionCallback callback = createCallback(server, backup);

      URI failoverList = callback.getFailoverList();

      assertNotNull(failoverList);
      assertEquals("tcp", failoverList.getScheme());
      assertEquals("backup", failoverList.getHost());
      assertEquals(61617, failoverList.getPort());
      assertEquals("sslEnabled=false", failoverList.getQuery());
   }

   @Test
   public void testGetFailoverListFailoverDisabled() throws Exception {
      ActiveMQServer server = Mockito.mock(ActiveMQServer.class);

      TransportConfiguration backup = createBackupTransportConfiguration();
      backup.getParams().put(TransportConstants.CLIENT_FAILOVER_ADVERTISING_ENABLED_PROP_NAME, false);

      AMQPConnectionCallback callback = createCallback(server, backup);

      assertNull(callback.getFailoverList());
   }

   @Test
   public void testGetFailoverListFailoverEnabledByDefault() throws Exception {
      ActiveMQServer server = Mockito.mock(ActiveMQServer.class);

      TransportConfiguration backup = createBackupTransportConfiguration();

      // CLIENT_FAILOVER_ADVERTISING_ENABLED_PROP_NAME is intentionally not set.
      // The default value is true.
      AMQPConnectionCallback callback = createCallback(server, backup);

      URI failoverList = callback.getFailoverList();

      assertNotNull(failoverList);
      assertEquals("tcp", failoverList.getScheme());
      assertEquals("backup", failoverList.getHost());
      assertEquals(61617, failoverList.getPort());
      assertEquals("sslEnabled=false", failoverList.getQuery());
   }

   @Test
   public void testGetFailoverListIncludesSslEnabled() throws Exception {
      ActiveMQServer server = Mockito.mock(ActiveMQServer.class);

      TransportConfiguration backup = createBackupTransportConfiguration();
      backup.getParams().put(
         TransportConstants.SSL_ENABLED_PROP_NAME,
         true);
      backup.getParams().put(
         TransportConstants.CLIENT_FAILOVER_ADVERTISING_ENABLED_PROP_NAME,
         true);

      AMQPConnectionCallback callback = createCallback(server, backup);

      URI failoverList = callback.getFailoverList();

      assertNotNull(failoverList);
      assertEquals("tcp", failoverList.getScheme());
      assertEquals("backup", failoverList.getHost());
      assertEquals(61617, failoverList.getPort());
      assertEquals("sslEnabled=true", failoverList.getQuery());
   }

   private TransportConfiguration createBackupTransportConfiguration() {
      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.HOST_PROP_NAME, "backup");
      params.put(TransportConstants.PORT_PROP_NAME, 61617);

      return new TransportConfiguration(
         NettyConnectorFactory.class.getName(),
         params);
   }


   private AMQPConnectionCallback createCallback(
      ActiveMQServer server,
      TransportConfiguration backup) throws Exception {

      String nodeId = "test-node";

      Mockito.when(server.getNodeID()).thenReturn(SimpleString.of(nodeId));

      ClusterManager clusterManager = Mockito.mock(ClusterManager.class);
      ClusterConnection clusterConnection = Mockito.mock(ClusterConnection.class);
      Topology topology = Mockito.mock(Topology.class);

      Mockito.when(server.getClusterManager()).thenReturn(clusterManager);
      Mockito.when(clusterManager.getDefaultConnection(null)).thenReturn(clusterConnection);
      Mockito.when(clusterConnection.getTopology()).thenReturn(topology);

      TopologyMemberImpl member = new TopologyMemberImpl(
         nodeId,
         null,
         null,
         null,
         backup);

      Mockito.when(topology.getMember(nodeId)).thenReturn(member);

      ProtonProtocolManager protocolManager = Mockito.mock(ProtonProtocolManager.class);
      Mockito.when(protocolManager.getServer()).thenReturn(server);

      return new AMQPConnectionCallback(
         protocolManager,
         null,
         null,
         server);
   }
}