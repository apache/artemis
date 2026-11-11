/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.server.impl;

import java.util.Collections;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryInternal;
import org.apache.activemq.artemis.core.client.impl.Topology;
import org.apache.activemq.artemis.core.client.impl.TopologyMemberImpl;
import org.apache.activemq.artemis.core.postoffice.PostOffice;
import org.apache.activemq.artemis.core.server.NodeManager;
import org.apache.activemq.artemis.core.server.cluster.ClusterControl;
import org.apache.activemq.artemis.core.server.cluster.ClusterController;
import org.apache.activemq.artemis.core.transaction.ResourceManager;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers how {@link ScaleDownHandler#scaleDown} determines the scale-down target node ID: either it is provided by the
 * caller, or it is resolved from the topology of the session factory connected to the target.
 */
public class ScaleDownHandlerTest {

   private static final String SELF_NODE_ID = "selfNodeID";
   private static final String TARGET_NODE_ID = "targetNodeID";

   private ClusterController clusterController;
   private ClusterControl clusterControl;
   private ClientSessionFactoryInternal sessionFactory;
   private Topology topology;
   private RemotingConnection connection;
   private ScaleDownHandler handler;

   @BeforeEach
   public void setUp() throws Exception {
      PostOffice postOffice = mock(PostOffice.class);
      when(postOffice.getAddresses()).thenReturn(Collections.emptySet());

      NodeManager nodeManager = mock(NodeManager.class);
      when(nodeManager.getNodeId()).thenReturn(SimpleString.of(SELF_NODE_ID));

      clusterControl = mock(ClusterControl.class);
      clusterController = mock(ClusterController.class);

      connection = mock(RemotingConnection.class);
      topology = mock(Topology.class);
      ServerLocator serverLocator = mock(ServerLocator.class);
      when(serverLocator.getTopology()).thenReturn(topology);

      sessionFactory = mock(ClientSessionFactoryInternal.class);
      when(sessionFactory.getServerLocator()).thenReturn(serverLocator);
      when(sessionFactory.getConnection()).thenReturn(connection);
      when(sessionFactory.createSession(any(), any(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyInt())).thenReturn(mock(ClientSession.class));

      when(clusterController.connectToNodeInCluster(sessionFactory)).thenReturn(clusterControl);

      handler = new ScaleDownHandler(null, postOffice, nodeManager, clusterController, null, -1);
   }

   @Test
   public void testProvidedTargetNodeIDIsUsedWithoutLookingUpTheTopology() throws Exception {
      scaleDown(SimpleString.of(TARGET_NODE_ID));

      verify(sessionFactory, never()).getServerLocator();
      verify(clusterControl).announceScaleDown(SimpleString.of(TARGET_NODE_ID), SimpleString.of(SELF_NODE_ID));
   }

   @Test
   public void testMissingTargetNodeIDIsResolvedFromTheTopology() throws Exception {
      TopologyMemberImpl member = mock(TopologyMemberImpl.class);
      when(member.getNodeId()).thenReturn(TARGET_NODE_ID);
      when(topology.getMember(connection)).thenReturn(member);

      scaleDown(null);

      verify(clusterControl).announceScaleDown(SimpleString.of(TARGET_NODE_ID), SimpleString.of(SELF_NODE_ID));
   }

   @Test
   public void testScaleDownFailsBeforeMovingAnythingWhenTheTargetIsNotInTheTopology() throws Exception {
      // e.g. the topology of the scale-down target was not received in time
      when(topology.getMember(connection)).thenReturn(null);

      NullPointerException e = assertThrows(NullPointerException.class, () -> scaleDown(null));

      assertTrue(e.getMessage().contains("not found"), e.getMessage());
      verifyNoInteractions(clusterController);
      verifyNoInteractions(clusterControl);
      verify(sessionFactory, never()).createSession(any(), any(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyInt());
   }

   private void scaleDown(SimpleString targetNodeId) throws Exception {
      handler.scaleDown(sessionFactory, mock(ResourceManager.class), Collections.emptyMap(), SimpleString.of("activemq.management"), targetNodeId);
   }
}
