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
package org.apache.activemq.artemis.tests.integration.openwire.cluster;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.tests.integration.cluster.distribution.ClusterTestBase;
import org.junit.jupiter.api.BeforeEach;

public class OpenWireJMSClusteredTestBase extends ClusterTestBase {

   protected ActiveMQConnectionFactory openWireCf1;

   protected ActiveMQConnectionFactory openWireCf2;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      setupServer(0, isFileStorage(), true);
      setupServer(1, isFileStorage(), true);

      setupClusterConnection("cluster0", "", MessageLoadBalancingType.ON_DEMAND, 1, true, 0, 1);
      setupClusterConnection("cluster1", "", MessageLoadBalancingType.ON_DEMAND, 1, true, 1, 0);

      startServers(0, 1);

      waitForTopology(servers[0], 2);
      waitForTopology(servers[1], 2);

      setupSessionFactory(0, true);
      setupSessionFactory(1, true);

      String uri1 = getServerUri(0);
      String uri2 = getServerUri(1);
      openWireCf1 = new ActiveMQConnectionFactory(uri1);
      openWireCf2 = new ActiveMQConnectionFactory(uri2);
   }

   @Override
   protected boolean isResolveProtocols() {
      return true;
   }
}