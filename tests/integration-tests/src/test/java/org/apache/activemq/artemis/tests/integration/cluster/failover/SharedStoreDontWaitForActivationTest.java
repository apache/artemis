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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.ha.SharedStorePrimaryPolicyConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.tests.integration.cluster.distribution.ClusterTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SharedStoreDontWaitForActivationTest extends ClusterTestBase {

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      setupServers();
   }

   private void setupServers() throws Exception {
      // Two primary servers with same shared storage, using a shared lock file

      // 1. configure 0 as backup of one to share the same node manager and file
      // storage locations
      setupBackupServer(0, 1, isFileStorage(), HAType.SharedStore, isNetty());
      setupPrimaryServer(1, isFileStorage(), HAType.SharedStore, isNetty(), false);

      // now reconfigure the HA policy for both servers to primary with automatic
      // failover and wait-for-activation disabled.
      setupSharedStorePrimaryPolicy(0);
      setupSharedStorePrimaryPolicy(1);

      // configure cluster for bother servers
      setupClusterConnection("cluster", "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 0, 1);
      setupClusterConnection("cluster", "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 1, 0);
   }

   private void setupSharedStorePrimaryPolicy(int node) {
      ActiveMQServer server = getServer(node);
      SharedStorePrimaryPolicyConfiguration primaryConfiguration = new SharedStorePrimaryPolicyConfiguration();
      primaryConfiguration.setFailoverOnServerShutdown(true);
      primaryConfiguration.setWaitForActivation(false);

      Configuration config = server.getConfiguration();

      config.setHAPolicyConfiguration(primaryConfiguration);
   }

   private boolean isNetty() {
      return true;
   }

   @Test
   public void startupPrimaryAndBackups() throws Exception {
      ActiveMQServer server0 = getServer(0);
      ActiveMQServer server1 = getServer(1);

      server0.start();
      // server 0 is live
      assertTrue(server0.waitForActivation(5, TimeUnit.SECONDS));
      server1.start();
      // server 1 is backup
      assertFalse(server1.waitForActivation(1, TimeUnit.SECONDS));

      setupSessionFactory(0, isNetty());
      createQueue(0, "queues.testaddress", "queue0", null, false);

      server0.stop();
      // now server 1 becomes live
      assertTrue(server1.waitForActivation(5, TimeUnit.SECONDS));

      server0.start();
      // after restart, server 0 becomes backup
      assertFalse(server0.waitForActivation(1, TimeUnit.SECONDS));

      server1.stop();
      // now server 0 becomes live again
      assertTrue(server0.waitForActivation(5, TimeUnit.SECONDS));

      server1.start();

      // after restart, server 1 becomes backup again
      assertFalse(server1.waitForActivation(1, TimeUnit.SECONDS));
   }
}
