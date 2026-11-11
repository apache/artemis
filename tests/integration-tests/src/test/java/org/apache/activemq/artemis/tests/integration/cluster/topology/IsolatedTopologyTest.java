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
package org.apache.activemq.artemis.tests.integration.cluster.topology;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.client.impl.Topology;
import org.apache.activemq.artemis.core.client.impl.TopologyMemberImpl;
import org.apache.activemq.artemis.core.config.ClusterConnectionConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

public class IsolatedTopologyTest extends ActiveMQTestBase {

   @Test
   public void testIsolatedClusters() throws Exception {

      ActiveMQServer server1 = createServer1();

      ActiveMQServer server2 = createServer2();

      server1.start();
      server2.start();

      waitForTopology(server1, "cc1", 2, 5000);

      waitForTopology(server1, "cc2", 2, 5000);

      waitForTopology(server2, "cc1", 2, 5000);

      waitForTopology(server2, "cc2", 2, 5000);

      String node1 = server1.getNodeID().toString();
      String node2 = server2.getNodeID().toString();

      checkTopology(server1, "cc1", node1, node2, createInVMTransportConnectorConfig(1, "srv1"), createInVMTransportConnectorConfig(3, "srv1"));

      checkTopology(server2, "cc1", node1, node2, createInVMTransportConnectorConfig(1, "srv1"), createInVMTransportConnectorConfig(3, "srv1"));

      checkTopology(server1, "cc2", node1, node2, createInVMTransportConnectorConfig(2, "srv1"), createInVMTransportConnectorConfig(4, "srv1"));

      checkTopology(server2, "cc2", node1, node2, createInVMTransportConnectorConfig(2, "srv1"), createInVMTransportConnectorConfig(4, "srv1"));
      Thread.sleep(500);
   }

   private void checkTopology(final ActiveMQServer serverParameter,
                              final String clusterName,
                              final String nodeId1,
                              final String nodeId2,
                              final TransportConfiguration cfg1,
                              final TransportConfiguration cfg2) {
      Topology topology = serverParameter.getClusterManager().getClusterConnection(clusterName).getTopology();

      TopologyMemberImpl member1 = topology.getMember(nodeId1);
      TopologyMemberImpl member2 = topology.getMember(nodeId2);
      assertEquals(member1.getPrimary().getParams().toString(), cfg1.getParams().toString());
      assertEquals(member2.getPrimary().getParams().toString(), cfg2.getParams().toString());
   }

   private ActiveMQServer createServer1() throws Exception {
      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.CLUSTER_CONNECTION, "cc1");
      params.put(org.apache.activemq.artemis.core.remoting.impl.invm.TransportConstants.SERVER_ID_PROP_NAME, "1");

      TransportConfiguration acceptor1VM1 = new TransportConfiguration(ActiveMQTestBase.INVM_ACCEPTOR_FACTORY, params, "acceptor-cc1");

      params = new HashMap<>();
      params.put(TransportConstants.CLUSTER_CONNECTION, "cc2");
      params.put(org.apache.activemq.artemis.core.remoting.impl.invm.TransportConstants.SERVER_ID_PROP_NAME, "2");

      TransportConfiguration acceptor2VM1 = new TransportConfiguration(ActiveMQTestBase.INVM_ACCEPTOR_FACTORY, params, "acceptor-cc2");

      List<String> connectTo = new ArrayList<>();
      connectTo.add("other-cc1");

      ClusterConnectionConfiguration server1CC1 = new ClusterConnectionConfiguration().setName("cc1").setAddress("jms").setConnectorName("local-cc1").setRetryInterval(250).setConfirmationWindowSize(1024).setMessageLoadBalancingType(MessageLoadBalancingType.ON_DEMAND).setStaticConnectors(connectTo);

      List<String> connectTo2 = new ArrayList<>();
      connectTo2.add("other-cc2");

      ClusterConnectionConfiguration server1CC2 = new ClusterConnectionConfiguration().setName("cc2").setAddress("jms").setConnectorName("local-cc2").setRetryInterval(250).setConfirmationWindowSize(1024).setMessageLoadBalancingType(MessageLoadBalancingType.ON_DEMAND).setStaticConnectors(connectTo2);

      // Server1 with two acceptors, each acceptor on a different cluster connection
      // talking to a different connector.
      // i.e. two cluster connections isolated on the same node
      Configuration config1 = createBasicConfig(1).addConnectorConfiguration("local-cc1", createInVMTransportConnectorConfig(1, "local-cc1")).addConnectorConfiguration("local-cc2", createInVMTransportConnectorConfig(2, "local-cc2")).addConnectorConfiguration("other-cc1", createInVMTransportConnectorConfig(3, "other-cc1")).addConnectorConfiguration("other-cc2", createInVMTransportConnectorConfig(4, "other-cc2")).addAcceptorConfiguration(acceptor1VM1).addAcceptorConfiguration(acceptor2VM1).addClusterConfiguration(server1CC1).addClusterConfiguration(server1CC2);

      return createServer(false, config1);
   }

   private ActiveMQServer createServer2() throws Exception {

      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.CLUSTER_CONNECTION, "cc1");
      params.put(org.apache.activemq.artemis.core.remoting.impl.invm.TransportConstants.SERVER_ID_PROP_NAME, "3");

      TransportConfiguration acceptor1VM1 = new TransportConfiguration(ActiveMQTestBase.INVM_ACCEPTOR_FACTORY, params, "acceptor-cc1");

      params = new HashMap<>();
      params.put(TransportConstants.CLUSTER_CONNECTION, "cc2");
      params.put(org.apache.activemq.artemis.core.remoting.impl.invm.TransportConstants.SERVER_ID_PROP_NAME, "4");

      TransportConfiguration acceptor2VM1 = new TransportConfiguration(ActiveMQTestBase.INVM_ACCEPTOR_FACTORY, params, "acceptor-cc2");

      List<String> connectTo = new ArrayList<>();
      connectTo.add("other-cc1");

      ClusterConnectionConfiguration server1CC1 = new ClusterConnectionConfiguration().setName("cc1").setAddress("jms").setConnectorName("local-cc1").setRetryInterval(250).setConfirmationWindowSize(1024).setMessageLoadBalancingType(MessageLoadBalancingType.ON_DEMAND).setStaticConnectors(connectTo);

      List<String> connectTo2 = new ArrayList<>();
      connectTo2.add("other-cc2");

      ClusterConnectionConfiguration server1CC2 = new ClusterConnectionConfiguration().setName("cc2").setAddress("jms").setConnectorName("local-cc2").setRetryInterval(250).setConfirmationWindowSize(1024).setMessageLoadBalancingType(MessageLoadBalancingType.ON_DEMAND).setStaticConnectors(connectTo2);

      // Server2 with two acceptors, each acceptor on a different cluster connection
      // talking to a different connector.
      // i.e. two cluster connections isolated on the same node
      Configuration config1 = createBasicConfig(2).addAcceptorConfiguration(acceptor1VM1).addAcceptorConfiguration(acceptor2VM1).addConnectorConfiguration("local-cc1", createInVMTransportConnectorConfig(3, "local-cc1")).addConnectorConfiguration("local-cc2", createInVMTransportConnectorConfig(4, "local-cc2")).addConnectorConfiguration("other-cc1", createInVMTransportConnectorConfig(1, "other-cc1")).addConnectorConfiguration("other-cc2", createInVMTransportConnectorConfig(2, "other-cc2")).addClusterConfiguration(server1CC1).addClusterConfiguration(server1CC2);

      return createServer(false, config1);
   }

}
