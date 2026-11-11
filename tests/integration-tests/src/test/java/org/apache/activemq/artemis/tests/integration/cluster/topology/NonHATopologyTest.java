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
import java.util.List;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorInternal;
import org.apache.activemq.artemis.core.client.impl.Topology;
import org.apache.activemq.artemis.core.client.impl.TopologyMemberImpl;
import org.apache.activemq.artemis.core.config.ClusterConnectionConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

/**
 * I have added this test to help validate if the connectors from Recovery will be properly updated
 */
public class NonHATopologyTest extends ActiveMQTestBase {

   @Test
   public void testNetty() throws Exception {
      internalTest(true);
   }

   @Test
   public void testInVM() throws Exception {
      internalTest(false);
   }

   public void internalTest(boolean isNetty) throws Exception {

      ActiveMQServer server = null;
      ServerLocatorInternal locator = null;

      try {

         server = createServer(false, isNetty);

         if (!isNetty) {
            server.getConfiguration().getAcceptorConfigurations().add(new TransportConfiguration(NETTY_ACCEPTOR_FACTORY));
            server.getConfiguration().getConnectorConfigurations().put("netty", new TransportConfiguration(NETTY_CONNECTOR_FACTORY));

            List<String> list = new ArrayList<>();
            list.add("netty");
            Configuration config = server.getConfiguration();
            config.getClusterConfigurations().add(new ClusterConnectionConfiguration().setName("tst").setAddress("jms").setConnectorName("netty").setRetryInterval(1000).setConfirmationWindowSize(1000).setMessageLoadBalancingType(MessageLoadBalancingType.ON_DEMAND).setStaticConnectors(list).setAllowDirectConnectionsOnly(true));
         }

         server.start();

         locator = (ServerLocatorInternal) createNonHALocator(isNetty);

         ClientSessionFactory factory = createSessionFactory(locator);

         Topology topology = locator.getTopology();

         assertEquals(1, topology.getMembers().size());

         factory.close();

         TopologyMemberImpl member = topology.getMembers().iterator().next();
         if (isNetty) {
            assertEquals(NettyConnectorFactory.class.getName(), member.getPrimary().getFactoryClassName());
         } else {
            assertEquals(InVMConnectorFactory.class.getName(), member.getPrimary().getFactoryClassName());
         }

      } finally {
         try {
            locator.close();
         } catch (Exception ignored) {
         }

         try {
            server.stop();
         } catch (Exception ignored) {
         }

         server = null;

         locator = null;
      }

   }
}
