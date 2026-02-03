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

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;

public class HAClientTopologyTest extends TopologyClusterTestBase {

   @Override
   protected boolean isNetty() {
      return false;
   }

   @Override
   protected void setupCluster() throws Exception {
      setupCluster(MessageLoadBalancingType.ON_DEMAND);
   }

   protected void setupCluster(final MessageLoadBalancingType messageLoadBalancingType) throws Exception {
      setupClusterConnection("cluster0", "queues", messageLoadBalancingType, 1, isNetty(), 0, 1, 2, 3, 4);
      setupClusterConnection("cluster1", "queues", messageLoadBalancingType, 1, isNetty(), 1, 0, 2, 3, 4);
      setupClusterConnection("cluster2", "queues", messageLoadBalancingType, 1, isNetty(), 2, 0, 1, 3, 4);
      setupClusterConnection("cluster3", "queues", messageLoadBalancingType, 1, isNetty(), 3, 0, 1, 2, 4);
      setupClusterConnection("cluster4", "queues", messageLoadBalancingType, 1, isNetty(), 4, 0, 1, 2, 3);
   }

   @Override
   protected void setupServers() throws Exception {
      setupServer(0, isFileStorage(), isNetty());
      setupServer(1, isFileStorage(), isNetty());
      setupServer(2, isFileStorage(), isNetty());
      setupServer(3, isFileStorage(), isNetty());
      setupServer(4, isFileStorage(), isNetty());
   }

   @Override
   protected ServerLocator createHAServerLocator() {
      TransportConfiguration tc = createTransportConfiguration(isNetty(), false, ActiveMQTestBase.generateParams(0, isNetty()));
      ServerLocator locator = addServerLocator(ActiveMQClient.createServerLocatorWithHA(tc));
      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true);
      return locator;
   }
}
