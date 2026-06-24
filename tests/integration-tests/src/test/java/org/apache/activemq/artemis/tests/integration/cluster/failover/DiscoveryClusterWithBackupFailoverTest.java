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
package org.apache.activemq.artemis.tests.integration.cluster.failover;

import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.junit.jupiter.api.BeforeEach;

public class DiscoveryClusterWithBackupFailoverTest extends ClusterWithBackupFailoverTest {

   protected final String groupAddress = getUDPDiscoveryAddress();

   protected final int groupPort = getUDPDiscoveryPort();

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      enableDiscoveryForTest();
      super.setUp();
   }

   @Override
   protected void setupCluster(final MessageLoadBalancingType messageLoadBalancingType) throws Exception {
      // The lives

      setupDiscoveryClusterConnection("cluster0", 0, "dg1", "queues", messageLoadBalancingType, 1, isNetty());
      setupDiscoveryClusterConnection("cluster1", 1, "dg1", "queues", messageLoadBalancingType, 1, isNetty());
      setupDiscoveryClusterConnection("cluster2", 2, "dg1", "queues", messageLoadBalancingType, 1, isNetty());

      // The backups

      setupDiscoveryClusterConnection("cluster0", 3, "dg1", "queues", messageLoadBalancingType, 1, isNetty());
      setupDiscoveryClusterConnection("cluster1", 4, "dg1", "queues", messageLoadBalancingType, 1, isNetty());
      setupDiscoveryClusterConnection("cluster2", 5, "dg1", "queues", messageLoadBalancingType, 1, isNetty());
   }

   @Override
   protected void setupServers() throws Exception {
      // The lives
      setupPrimaryServerWithDiscovery(0, groupAddress, groupPort, isFileStorage(), isNetty(), true);
      setupPrimaryServerWithDiscovery(1, groupAddress, groupPort, isFileStorage(), isNetty(), true);
      setupPrimaryServerWithDiscovery(2, groupAddress, groupPort, isFileStorage(), isNetty(), true);

      // The backups
      setupBackupServerWithDiscovery(3, 0, groupAddress, groupPort, isFileStorage(), isNetty(), true);
      setupBackupServerWithDiscovery(4, 1, groupAddress, groupPort, isFileStorage(), isNetty(), true);
      setupBackupServerWithDiscovery(5, 2, groupAddress, groupPort, isFileStorage(), isNetty(), true);
   }

}
