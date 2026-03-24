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

import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;

public class TwoWayTwoNodeClusterWithDiscoveryTest extends TwoWayTwoNodeClusterTest {


   protected static final String groupAddress = getUDPDiscoveryAddress();

   protected static final int groupPort = getUDPDiscoveryPort();





   @Override
   protected void setupClusters() {
      setupDiscoveryClusterConnection("cluster0", 0, "dg1", "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty());
      setupDiscoveryClusterConnection("cluster1", 1, "dg1", "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty());
   }

   @Override
   protected void setupServers() throws Exception {
      setupLiveServerWithDiscovery(0, groupAddress, groupPort, isFileStorage(), isNetty(), false);
      setupLiveServerWithDiscovery(1, groupAddress, groupPort, isFileStorage(), isNetty(), false);
   }



}
