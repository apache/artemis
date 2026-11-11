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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.DivertConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ExclusiveDivertWithClusterTest extends ClusterTestBase {

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      start();
   }

   @Override
   protected void applySettings(ActiveMQServer server,
                                final Configuration configuration,
                                final int pageSize,
                                final long maxAddressSize,
                                final Integer pageSize1,
                                final Integer pageSize2,
                                final Map<String, AddressSettings> settings) {
      DivertConfiguration divertConf = new DivertConfiguration().setName("notifications-divert").setAddress("*.Provider.*.Agent.*.Status").setForwardingAddress("Notifications").setExclusive(true);

      configuration.addDivertConfiguration(divertConf);
   }

   @Test
   public void testExclusiveDivertDoesNotDuplicateMessageInCluster() throws Exception {
      setupCluster(MessageLoadBalancingType.ON_DEMAND);

      startServers(0, 1);

      setupSessionFactory(0, isNetty(), false);
      setupSessionFactory(1, isNetty(), false);

      createQueue(0, "Notifications", "Notifications", null, false, RoutingType.ANYCAST);
      createQueue(1, "Notifications", "Notifications", null, false, RoutingType.ANYCAST);

      addConsumer(0, 0, "Notifications", null, true);

      createQueue(0, "x.Provider.y.Agent.z.Status", "x.Provider.y.Agent.z.Status", null, false, RoutingType.ANYCAST);
      createQueue(1, "x.Provider.y.Agent.z.Status", "x.Provider.y.Agent.z.Status", null, false, RoutingType.ANYCAST);

      waitForBindings(0, "Notifications", 1, 1, true);
      waitForBindings(0, "Notifications", 1, 0, false);

      waitForBindings(1, "Notifications", 1, 0, true);
      waitForBindings(1, "Notifications", 1, 1, false);

      send(0, "x.Provider.y.Agent.z.Status", 1, false, null);

      int messagesAdded = getMessagesAdded(servers[0].getPostOffice(), "Notifications");

      assertEquals(1, messagesAdded);
   }

   protected void start() throws Exception {
      setupServers();
   }

   protected void setupServers() throws Exception {
      setupServer(0, isFileStorage(), isNetty());
      setupServer(1, isFileStorage(), isNetty());
   }

   protected void setupCluster(final MessageLoadBalancingType messageLoadBalancingType) throws Exception {
      setupClusterConnection("cluster0", "", messageLoadBalancingType, 1, isNetty(), 0, 1);
      setupClusterConnection("cluster1", "", messageLoadBalancingType, 1, isNetty(), 1, 0);
   }

   protected void stopServers() throws Exception {
      closeAllConsumers();

      closeAllSessionFactories();

      closeAllServerLocatorsFactories();

      stopServers(0, 1);

      clearServer(0, 1);
   }

   protected boolean isNetty() {
      return false;
   }
}
