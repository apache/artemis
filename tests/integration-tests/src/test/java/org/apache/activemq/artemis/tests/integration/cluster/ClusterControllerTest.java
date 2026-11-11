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
package org.apache.activemq.artemis.tests.integration.cluster;

import java.util.List;

import org.apache.activemq.artemis.api.core.ActiveMQClusterSecurityException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryInternal;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorImpl;
import org.apache.activemq.artemis.core.config.ClusterConnectionConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.cluster.ActiveMQServerSideProtocolManagerFactory;
import org.apache.activemq.artemis.core.server.cluster.ClusterControl;
import org.apache.activemq.artemis.core.server.cluster.ClusterController;
import org.apache.activemq.artemis.core.server.cluster.ClusterManager;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.tests.integration.cluster.distribution.ClusterTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.fail;

public class ClusterControllerTest extends ClusterTestBase {

   private ClusterConnectionConfiguration clusterConf0;
   private ClusterConnectionConfiguration clusterConf1;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      setupServer(0, isFileStorage(), true);
      setupServer(1, isFileStorage(), true);

      getServer(0).getConfiguration().getAcceptorConfigurations().add(createTransportConfiguration(false, true, generateParams(0, false)));
      getServer(1).getConfiguration().getAcceptorConfigurations().add(createTransportConfiguration(false, true, generateParams(1, false)));

      getServer(0).getConfiguration().setSecurityEnabled(true);
      getServer(1).getConfiguration().setSecurityEnabled(true);

      getServer(1).getConfiguration().setClusterPassword("something different");

      clusterConf0 = new ClusterConnectionConfiguration()
         .setName("cluster0")
         .setAddress("queues")
         .setMessageLoadBalancingType(MessageLoadBalancingType.ON_DEMAND)
         .setMaxHops(1)
         .setInitialConnectAttempts(8)
         .setReconnectAttempts(10)
         .setRetryInterval(250)
         .setMaxRetryInterval(4000)
         .setRetryIntervalMultiplier(2.0);

      clusterConf1 = new ClusterConnectionConfiguration()
         .setName("cluster0")
         .setAddress("queues")
         .setMessageLoadBalancingType(MessageLoadBalancingType.ON_DEMAND)
         .setMaxHops(1)
         .setInitialConnectAttempts(8)
         .setReconnectAttempts(10)
         .setRetryInterval(250)
         .setMaxRetryInterval(4000)
         .setRetryIntervalMultiplier(2.0);

      setupClusterConnection(clusterConf0, true, 0);
      setupClusterConnection(clusterConf1, true, 1);

      startServers(0);
      startServers(1);
   }

   private boolean clusterConnectionConfigurationIsSameBeforeAfterStart(ClusterConnectionConfiguration clusterConnectionConfigurationBeforeStart, int node) {
      boolean clusterConnectionConfigurationIsSame = false;

      Configuration serverNodeConfiguration = getServer(node).getConfiguration();
      ActiveMQServer serverNode = getServer(node);
      ClusterManager clusterManager = serverNode.getClusterManager();
      ClusterController clusterController = clusterManager.getClusterController();
      ServerLocator serverNodeLocator = clusterController.getServerLocator(SimpleString.of(clusterConnectionConfigurationBeforeStart.getName()));
      List<ClusterConnectionConfiguration> serverNodeClusterConnectionConfigurations = serverNodeConfiguration.getClusterConfigurations();

      do {
         if (serverNodeLocator.getInitialConnectAttempts() != clusterConnectionConfigurationBeforeStart.getInitialConnectAttempts()) {
            break;
         }

         if (serverNodeLocator.getReconnectAttempts() != clusterConnectionConfigurationBeforeStart.getReconnectAttempts()) {
            break;
         }

         if (serverNodeLocator.getRetryInterval() != clusterConnectionConfigurationBeforeStart.getRetryInterval()) {
            break;
         }
         if (serverNodeLocator.getMaxRetryInterval() != clusterConnectionConfigurationBeforeStart.getMaxRetryInterval()) {
            break;
         }

         Double serverNodeClusterConnectionConfigurationRIM = serverNodeLocator.getRetryIntervalMultiplier();
         Double clusterConnectionConfigurationBeforeStartRIM = clusterConnectionConfigurationBeforeStart.getRetryIntervalMultiplier();
         if (0 != serverNodeClusterConnectionConfigurationRIM.compareTo(clusterConnectionConfigurationBeforeStartRIM)) {
            break;
         }

         clusterConnectionConfigurationIsSame = true;
      }
      while (false);

      return clusterConnectionConfigurationIsSame;
   }

   @Test
   public void controlWithDifferentConnector() throws Exception {
      try (ServerLocatorImpl locator = (ServerLocatorImpl) createInVMNonHALocator()) {
         locator.setProtocolManagerFactory(ActiveMQServerSideProtocolManagerFactory.getInstance(locator, servers[0].getStorageManager()));
         ClusterController controller = new ClusterController(getServer(0), getServer(0).getScheduledPool());
         ClusterControl clusterControl = controller.connectToNodeInCluster((ClientSessionFactoryInternal) locator.createSessionFactory());
         clusterControl.authorize();
      }
   }

   @Test
   public void controlWithDifferentPassword() throws Exception {
      try (ServerLocatorImpl locator = (ServerLocatorImpl) createInVMNonHALocator()) {
         locator.setProtocolManagerFactory(ActiveMQServerSideProtocolManagerFactory.getInstance(locator, servers[0].getStorageManager()));
         ClusterController controller = new ClusterController(getServer(1), getServer(1).getScheduledPool());
         ClusterControl clusterControl = controller.connectToNodeInCluster((ClientSessionFactoryInternal) locator.createSessionFactory());
         try {
            clusterControl.authorize();
            fail("should throw ActiveMQClusterSecurityException");
         } catch (Exception e) {
            assertInstanceOf(ActiveMQClusterSecurityException.class, e, "should throw ActiveMQClusterSecurityException");
         }
      }
   }

   @Test
   public void verifyServerLocatorsClusterConfiguration() {
      if (false == clusterConnectionConfigurationIsSameBeforeAfterStart(clusterConf0, 0)) {
         fail("serverLocator is not configured as per clusterConf0");
      }
      if (false == clusterConnectionConfigurationIsSameBeforeAfterStart(clusterConf1, 1)) {
         fail("serverLocator is not configured as per clusterConf1");
      }
   }
}
