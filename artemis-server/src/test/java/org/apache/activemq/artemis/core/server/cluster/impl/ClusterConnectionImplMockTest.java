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

package org.apache.activemq.artemis.core.server.cluster.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.Executors;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.TopologyMember;
import org.apache.activemq.artemis.core.client.impl.TopologyMemberImpl;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.ActivateCallback;
import org.apache.activemq.artemis.core.server.NodeManager;
import org.apache.activemq.artemis.core.server.cluster.ClusterManager;
import org.apache.activemq.artemis.core.server.impl.Activation;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.core.server.impl.CleaningActivateCallback;
import org.apache.activemq.artemis.tests.util.ServerTestBase;
import org.apache.activemq.artemis.utils.ActiveMQThreadFactory;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.UUIDGenerator;
import org.apache.activemq.artemis.utils.actors.ArtemisExecutor;
import org.junit.jupiter.api.Test;

public class ClusterConnectionImplMockTest extends ServerTestBase {

   /**
    * Verification for the fix https://issues.apache.org/jira/browse/ARTEMIS-1946
    */
   @Test
   public void testRemvalOfLocalParameters() throws Exception {
      TransportConfiguration tc = new TransportConfiguration();
      tc.setFactoryClassName("mock");
      tc.getParams().put(TransportConstants.LOCAL_ADDRESS_PROP_NAME, "localAddress");
      tc.getParams().put(TransportConstants.LOCAL_PORT_PROP_NAME, "localPort");

      ArtemisExecutor executor = ArtemisExecutor.delegate(Executors.newSingleThreadExecutor(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName())));

      ClusterConnectionImpl cci = new ClusterConnectionImpl(
                null, //final ClusterManager manager,
                new TransportConfiguration[]{tc}, //final TransportConfiguration[] staticTranspConfigs,
                null, //final TransportConfiguration connector,
                null, //final SimpleString name,
                null, //final SimpleString address,
                0, //final int minLargeMessageSize,
                0L, //final long clientFailureCheckPeriod,
                0L, //final long connectionTTL,
                0L, //final long retryInterval,
                0, //final double retryIntervalMultiplier,
                0L, //final long maxRetryInterval,
                0, //final int initialConnectAttempts,
                0, //final int reconnectAttempts,
                0L, //final long callTimeout,
                0L, //final long callFailoverTimeout,
                false, //final boolean useDuplicateDetection,
                null, //final MessageLoadBalancingType messageLoadBalancingType,
                0, //final int confirmationWindowSize,
                0, //final int producerWindowSize,
         () -> executor, //final ExecutorFactory executorFactory,
                new MockServer(), //final ActiveMQServer server,
                null, //final PostOffice postOffice,
                null, //final ManagementService managementService,
                null, //final ScheduledExecutorService scheduledExecutor,
                0, //final int maxHops,
                null, //final NodeManager nodeManager,
                null, //final String clusterUser,
                null, //final String clusterPassword,
                true, //final boolean allowDirectConnectionsOnly,
                0, //final long clusterNotificationInterval,
                0, //final int clusterNotificationAttempts)
                null,
                0,
                0L, //final long scaleDownReaperPeriod,
                0, //final int scaleDownReaperGraceTicks
                0 //final int scaleDownReaperMaxAttempts
      );

      assertEquals(1, cci.allowableConnections.size());
      assertFalse(cci.allowableConnections.iterator().next().getParams().containsKey(TransportConstants.LOCAL_ADDRESS_PROP_NAME), "Local address can not be part of allowable connection.");
      assertFalse(cci.allowableConnections.iterator().next().getParams().containsKey(TransportConstants.LOCAL_PORT_PROP_NAME), "Local port can not be part of allowable connection.");

   }

   @Test
   public void testNullPrimaryOnNodeUp() throws Exception {
      TransportConfiguration tc = new TransportConfiguration();
      tc.setFactoryClassName("mock");
      tc.getParams().put(TransportConstants.LOCAL_ADDRESS_PROP_NAME, "localAddress");
      tc.getParams().put(TransportConstants.LOCAL_PORT_PROP_NAME, "localPort");

      ArtemisExecutor executor = ArtemisExecutor.delegate(Executors.newSingleThreadExecutor(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName())));

      try {
         ClusterConnectionImpl cci = new ClusterConnectionImpl(null, new TransportConfiguration[]{tc}, null, null, null, 0, 0L, 0L, 0L, 0, 0L, 0, 0, 0L, 0L, false, null, 0, 0, () -> executor, new MockServer(), null, null, null, 0, new FakeNodeManager(UUIDGenerator.getInstance().generateStringUUID()), null, null, true, 0, 0, null, 0, 0L, 0, 0);

         TopologyMember topologyMember = new TopologyMemberImpl(RandomUtil.randomUUIDString(), null, null, null, null);
         cci.nodeUP(topologyMember, false);
      } finally {
         executor.shutdownNow();
      }
   }

   @Test
   public void testCompareTCs() {
      TransportConfiguration netty1 = new TransportConfiguration(NettyConnectorFactory.class.getName(), Map.of("host", "localhost", "port", 61616));
      assertTrue(ClusterConnectionImpl.TopologyScanner.compareTCs(netty1, netty1));

      TransportConfiguration netty2 = new TransportConfiguration(NettyConnectorFactory.class.getName(), Map.of("host", "127.0.0.1", "port", 61616));
      assertTrue(ClusterConnectionImpl.TopologyScanner.compareTCs(netty2, netty2));

      assertTrue(ClusterConnectionImpl.TopologyScanner.compareTCs(netty1, netty2));

      TransportConfiguration netty3 = new TransportConfiguration(NettyConnectorFactory.class.getName(), Map.of("host", "my-host", "port", 61617));
      assertTrue(ClusterConnectionImpl.TopologyScanner.compareTCs(netty3, netty3));

      TransportConfiguration netty4 = new TransportConfiguration(NettyConnectorFactory.class.getName(), Map.of("host", "192.168.0.1", "port", 61617));
      assertTrue(ClusterConnectionImpl.TopologyScanner.compareTCs(netty4, netty4));

      assertFalse(ClusterConnectionImpl.TopologyScanner.compareTCs(netty1, netty3));
      assertFalse(ClusterConnectionImpl.TopologyScanner.compareTCs(netty1, netty4));
      assertFalse(ClusterConnectionImpl.TopologyScanner.compareTCs(netty2, netty3));
      assertFalse(ClusterConnectionImpl.TopologyScanner.compareTCs(netty2, netty4));
      assertFalse(ClusterConnectionImpl.TopologyScanner.compareTCs(netty3, netty4));
   }

   static final class MockServer extends ActiveMQServerImpl {

      @Override
      public ClusterManager getClusterManager() {
         return new ClusterManager(getExecutorFactory(), this, null, null, null, null, null, false);
      }

      @Override
      public ExecutorFactory getExecutorFactory() {
         return () -> null;
      }

      @Override
      public Activation getActivation() {
         return new Activation() {
            @Override
            public void close(boolean permanently, boolean restarting) throws Exception {

            }

            @Override
            public void run() {

            }
         };
      }
   }

   protected final class FakeNodeManager extends NodeManager {

      public FakeNodeManager(String nodeID) {
         super(false);
         this.setNodeID(nodeID);
      }

      @Override
      public void awaitPrimaryNode() {
      }

      @Override
      public void awaitActiveStatus() {
      }

      @Override
      public void startBackup() {
      }

      @Override
      public ActivateCallback startPrimaryNode() {
         return new CleaningActivateCallback() {
         };
      }

      @Override
      public void pausePrimaryServer() {
      }

      @Override
      public void crashPrimaryServer() {
      }

      @Override
      public void releaseBackup() {
      }

      @Override
      public SimpleString readNodeId() {
         return null;
      }

      @Override
      public boolean isAwaitingFailback() {
         return false;
      }

      @Override
      public boolean isBackupActive() {
         return false;
      }

      @Override
      public void interrupt() {
      }
   }
}
