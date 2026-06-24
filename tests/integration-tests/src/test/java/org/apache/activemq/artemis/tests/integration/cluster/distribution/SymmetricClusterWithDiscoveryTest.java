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
package org.apache.activemq.artemis.tests.integration.cluster.distribution;

import java.io.EOFException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.BroadcastEndpoint;
import org.apache.activemq.artemis.api.core.BroadcastEndpointFactory;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SymmetricClusterWithDiscoveryTest extends SymmetricClusterTest {

   protected final String groupAddress = ActiveMQTestBase.getUDPDiscoveryAddress();

   protected final int groupPort = ActiveMQTestBase.getUDPDiscoveryPort();

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      enableDiscoveryForTest();
      super.setUp();
   }

   @Override
   protected boolean isNetty() {
      return false;
   }

   @Override
   @Test
   public void testStartStopServers() throws Exception {
      // When using discovery starting and stopping it too fast could have a race condition with UDP
      doTestStartStopServers(false);
   }

   @Override
   @Test
   public void testStartStopServersWithPartition() throws Exception {
      // When using discovery starting and stopping it too fast could have a race condition with UDP
      doTestStartStopServers(true);
   }

   @Override
   protected void setupCluster() throws Exception {
      setupCluster(MessageLoadBalancingType.ON_DEMAND);
   }

   @Override
   protected void setupCluster(final MessageLoadBalancingType messageLoadBalancingType) throws Exception {
      setupDiscoveryClusterConnection("cluster0", 0, "dg1", "queues", messageLoadBalancingType, 1, isNetty());

      setupDiscoveryClusterConnection("cluster1", 1, "dg1", "queues", messageLoadBalancingType, 1, isNetty());

      setupDiscoveryClusterConnection("cluster2", 2, "dg1", "queues", messageLoadBalancingType, 1, isNetty());

      setupDiscoveryClusterConnection("cluster3", 3, "dg1", "queues", messageLoadBalancingType, 1, isNetty());

      setupDiscoveryClusterConnection("cluster4", 4, "dg1", "queues", messageLoadBalancingType, 1, isNetty());
   }

   @Override
   protected void setupServers() throws Exception {
      setupPrimaryServerWithDiscovery(0, groupAddress, groupPort, isFileStorage(), isNetty(), false);
      setupPrimaryServerWithDiscovery(1, groupAddress, groupPort, isFileStorage(), isNetty(), false);
      setupPrimaryServerWithDiscovery(2, groupAddress, groupPort, isFileStorage(), isNetty(), false);
      setupPrimaryServerWithDiscovery(3, groupAddress, groupPort, isFileStorage(), isNetty(), false);
      setupPrimaryServerWithDiscovery(4, groupAddress, groupPort, isFileStorage(), isNetty(), false);
   }

   /*
    * This is like testStopStartServers but we make sure we pause longer than discovery group timeout
    * before restarting (5 seconds)
    */
   @Test
   public void testStartStopServersWithPauseBeforeRestarting() throws Exception {
      doTestStartStopServers(false);
   }

   @Test
   public void testStartStopServersWithWrongConnectorConfigurations() throws Exception {
      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         setupCluster();

         for (int node = 0; node < 5; node++) {
            final int serverNode = node;

            // Set wrong connector configurations
            Map<String, TransportConfiguration> wrongConnectorConfigurations = new HashMap<>();
            getServer(node).getConfiguration().getConnectorConfigurations().forEach((key, transportConfiguration) -> {
               TransportConfiguration wrongtransportConfiguration = new TransportConfiguration(
                       transportConfiguration.getFactoryClassName(),
                       new HashMap<>(transportConfiguration.getParams()),
                       transportConfiguration.getName(),
                       new HashMap<>(transportConfiguration.getExtraParams()));
               if (isNetty()) {
                  wrongtransportConfiguration.getParams().put("port", org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.DEFAULT_PORT + serverNode + 10);
               } else {
                  wrongtransportConfiguration.getParams().put("serverId", String.valueOf(serverNode + 10));
               }
               wrongConnectorConfigurations.put(key, wrongtransportConfiguration);
            });
            getServer(node).getConfiguration().setConnectorConfigurations(wrongConnectorConfigurations);

            // Reduce the discovery stopping timeout to speed up the test
            getServer(node).getConfiguration().getDiscoveryGroupConfigurations().forEach((s, discoveryGroupConfiguration) -> discoveryGroupConfiguration.setStoppingTimeout(1));

            // Reduce the topology scanner attempts to speed up the test
            getServer(node).getConfiguration().getClusterConfigurations().forEach(
                    clusterConnectionConfiguration -> clusterConnectionConfiguration.setTopologyScannerAttempts(1));
         }

         startServers();

         validateTopologySize(1, 0, 1, 2, 3, 4);

         Wait.assertTrue(() -> loggerHandler.findText("AMQ224144"));

         stopServers();
      }
   }

   @Override
   protected void setupProxy(int nodeId) {
      getServer(nodeId).getConfiguration().getDiscoveryGroupConfigurations().get("dg1").
          setBroadcastEndpointFactory(new ProxyBroadcastEndpointFactory(nodeId, getServer(nodeId).
          getConfiguration().getDiscoveryGroupConfigurations().get("dg1").getBroadcastEndpointFactory()));
   }

   @Override
   protected void enablePartition() {
      super.enablePartition();

      ProxyBroadcastEndpointFactory.interceptor = (nodeId, data) -> {
         ActiveMQBuffer buffer = ActiveMQBuffers.wrappedBuffer(data);
         SimpleString originatingNodeID = SimpleString.of(buffer.readString());

         for (int i = 0; i < servers.length; i++) {
            if (servers[i] != null && originatingNodeID.equals(servers[i].getNodeID())) {
               int partitionId = (i % 5) / 3;
               int targetPartitionId = (nodeId % 5) / 3;

               return partitionId == targetPartitionId;
            }
         }

         return false;
      };
   }

   @Override
   protected void disablePartition() {
      super.disablePartition();

      ProxyBroadcastEndpointFactory.interceptor = null;
   }

   public interface ProxyBroadcastEndpointInterceptor {
      boolean allowBroadcast(int nodeId, byte[] data);
   }

   public class ProxyBroadcastEndpointFactory implements BroadcastEndpointFactory {

      public static volatile ProxyBroadcastEndpointInterceptor interceptor;

      private int nodeId;
      private final BroadcastEndpointFactory rawBroadcastEndpointFactory;

      public ProxyBroadcastEndpointFactory(int nodeId, BroadcastEndpointFactory rawBroadcastEndpointFactory) {
         this.nodeId = nodeId;
         this.rawBroadcastEndpointFactory = rawBroadcastEndpointFactory;
      }

      @Override
      public BroadcastEndpoint createBroadcastEndpoint() throws Exception {
         return new ProxyBroadcastEndpoint(nodeId, rawBroadcastEndpointFactory.createBroadcastEndpoint());
      }

      private class ProxyBroadcastEndpoint implements BroadcastEndpoint {

         private int nodeId;
         private volatile boolean open;
         private final BroadcastEndpoint rawBroadcastEndpoint;

         ProxyBroadcastEndpoint(int nodeId, BroadcastEndpoint rawBroadcastEndpoint) {
            this.nodeId = nodeId;
            this.rawBroadcastEndpoint = rawBroadcastEndpoint;
         }

         @Override
         public void openClient() throws Exception {
            open = true;
            rawBroadcastEndpoint.openClient();
         }

         @Override
         public void openBroadcaster() throws Exception {
            open = true;
            rawBroadcastEndpoint.openBroadcaster();
         }

         @Override
         public void close(boolean isBroadcast) throws Exception {
            open = false;
            rawBroadcastEndpoint.close(isBroadcast);
         }

         @Override
         public void broadcast(byte[] data) throws Exception {
            rawBroadcastEndpoint.broadcast(data);
         }

         @Override
         public byte[] receiveBroadcast() throws Exception {
            return receiveBroadcast(Long.MAX_VALUE, TimeUnit.DAYS);
         }

         @Override
         public byte[] receiveBroadcast(long time, TimeUnit unit) throws Exception {
            while (open) {
               byte[] data = rawBroadcastEndpoint.receiveBroadcast(time, unit);

               ProxyBroadcastEndpointInterceptor interceptor = ProxyBroadcastEndpointFactory.interceptor;
               if (interceptor == null || interceptor.allowBroadcast(nodeId, data)) {
                  return data;
               }
            }
            throw new EOFException();
         }
      }
   }
}
