/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.tests.integration.amqp;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.BridgeConfiguration;
import org.apache.activemq.artemis.core.config.ClusterConnectionConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.DivertConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ComponentConfigurationRoutingType;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.integration.amqp.largemessages.AMQPLargeMessagesTestUtil;
import org.apache.activemq.artemis.tests.integration.cluster.distribution.ClusterTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class AmqpBridgeClusterRedistributionTest extends AmqpClientTestSupport {

   protected ActiveMQServer[] servers = new ActiveMQServer[3];
   private ActiveMQServer server0;
   private ActiveMQServer server1;
   private ActiveMQServer server2;
   private SimpleString customNotificationQueue;
   private SimpleString frameworkNotificationsQueue;
   private SimpleString bridgeNotificationsQueue;
   private SimpleString notificationsQueue;

   protected String getServer0URL() {
      return "tcp://localhost:61616";
   }

   protected String getServer1URL() {
      return "tcp://localhost:61617";
   }

   protected String getServer2URL() {
      return "tcp://localhost:61618";
   }

   @Override
   public URI getBrokerAmqpConnectionURI() {
      try {
         return new URI(getServer0URL());
      } catch (URISyntaxException e) {
         throw new RuntimeException(e);
      }
   }

   @Override
   protected void applySettings(ActiveMQServer server,
                                         final Configuration configuration,
                                         final int pageSize,
                                         final long maxAddressSize,
                                         final Integer pageSize1,
                                         final Integer pageSize2,
                                         final Map<String, AddressSettings> settings) {
      AddressSettings defaultSetting = new AddressSettings().setPageSizeBytes(pageSize).setMaxSizeBytes(maxAddressSize).setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE).setRedeliveryDelay(0).setRedistributionDelay(0).setAutoCreateQueues(true).setAutoCreateAddresses(true);
      server.getAddressSettingsRepository().addMatch("#", defaultSetting);
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      server0 = createServer(true, createBasicConfig(0));
      server1 = createServer(true, createBasicConfig(1));
      server2 = createServer(true, createBasicConfig(2));

      servers[0] = server0;
      servers[1] = server1;
      servers[2] = server2;

      server0.getConfiguration().addAcceptorConfiguration("acceptor", getServer0URL());
      server0.getConfiguration().addConnectorConfiguration("notification-broker", getServer1URL());

      server1.getConfiguration().addAcceptorConfiguration("acceptor", getServer1URL());
      server2.getConfiguration().addAcceptorConfiguration("acceptor", getServer2URL());

      DivertConfiguration customNotificationsDivert = new DivertConfiguration().setName("custom-notifications-divert").setAddress("*.Provider.*.Agent.*.CustomNotification").setForwardingAddress("FrameworkNotifications").setExclusive(true);

      DivertConfiguration frameworkNotificationsDivertServer1 = new DivertConfiguration().setName("framework-notifications-divert").setAddress("BridgeNotifications").setForwardingAddress("Notifications").setRoutingType(ComponentConfigurationRoutingType.MULTICAST).setExclusive(true);
      DivertConfiguration frameworkNotificationsDivertServer2 = new DivertConfiguration().setName("framework-notifications-divert").setAddress("BridgeNotifications").setForwardingAddress("Notifications").setRoutingType(ComponentConfigurationRoutingType.MULTICAST).setExclusive(true);

      server0.getConfiguration().addDivertConfiguration(customNotificationsDivert);

      server1.getConfiguration().addDivertConfiguration(frameworkNotificationsDivertServer1);
      server2.getConfiguration().addDivertConfiguration(frameworkNotificationsDivertServer2);

      customNotificationQueue = SimpleString.of("*.Provider.*.Agent.*.CustomNotification");
      frameworkNotificationsQueue = SimpleString.of("FrameworkNotifications");
      bridgeNotificationsQueue = SimpleString.of("BridgeNotifications");
      notificationsQueue = SimpleString.of("Notifications");

      setupClusterConnection("cluster-1->2", "", MessageLoadBalancingType.ON_DEMAND, 1, true, 1, 2);
      setupClusterConnection("cluster-2->1", "", MessageLoadBalancingType.ON_DEMAND, 1, true, 2, 1);

      server0.start();

      server1.start();
      server2.start();

      server0.createQueue(QueueConfiguration.of(customNotificationQueue).setRoutingType(RoutingType.ANYCAST));
      server0.createQueue(QueueConfiguration.of(frameworkNotificationsQueue).setRoutingType(RoutingType.ANYCAST));

      server1.createQueue(QueueConfiguration.of(bridgeNotificationsQueue).setRoutingType(RoutingType.ANYCAST));
      server1.createQueue(QueueConfiguration.of(notificationsQueue));

      server2.createQueue(QueueConfiguration.of(bridgeNotificationsQueue).setRoutingType(RoutingType.ANYCAST));
      server2.createQueue(QueueConfiguration.of(notificationsQueue));

      server0.deployBridge(new BridgeConfiguration().setName("notifications-bridge").setQueueName(frameworkNotificationsQueue.toString()).setForwardingAddress(bridgeNotificationsQueue.toString()).setConfirmationWindowSize(10).setStaticConnectors(Arrays.asList("notification-broker")));
   }

   @AfterEach
   @Override
   public void tearDown() throws Exception {
      try {
         if (server0 != null) {
            server0.stop();
         }
         if (server1 != null) {
            server1.stop();
         }
         if (server2 != null) {
            server2.stop();
         }
      } finally {
         super.tearDown();
      }
   }

   @Test
   public void testSendMessageToBroker0GetFromBroker1() throws Exception {
      try (ServerLocator locator = ActiveMQClient.createServerLocator(getServer1URL());
           ClientSessionFactory sessionFactory = locator.createSessionFactory();
           ClientSession session = sessionFactory.createSession();
           ClientConsumer consumer = session.createConsumer(notificationsQueue)) {

         session.start();

         sendMessages("uswest.Provider.AMC.Agent.DIVERTED.CustomNotification", 1, RoutingType.ANYCAST, true);

         AMQPLargeMessagesTestUtil.validateAllTemporaryBuffers(server0);
         AMQPLargeMessagesTestUtil.validateAllTemporaryBuffers(server1);

         ClientMessage message = consumer.receive(5000);
         assertNotNull(message);

         message = consumer.receiveImmediate();
         assertNull(message);
      }

      AMQPLargeMessagesTestUtil.validateAllTemporaryBuffers(server0);
      AMQPLargeMessagesTestUtil.validateAllTemporaryBuffers(server1);
   }

   @Test
   public void testSendMessageToBroker0GetFromBroker2() throws Exception {
      try (ServerLocator locator = ActiveMQClient.createServerLocator(getServer2URL());
           ClientSessionFactory sessionFactory = locator.createSessionFactory();
           ClientSession session = sessionFactory.createSession();
           ClientConsumer consumer = session.createConsumer(notificationsQueue)) {

         session.start();

         sendMessages("uswest.Provider.AMC.Agent.DIVERTED.CustomNotification", 1, RoutingType.ANYCAST, true);

         AMQPLargeMessagesTestUtil.validateAllTemporaryBuffers(server0);
         AMQPLargeMessagesTestUtil.validateAllTemporaryBuffers(server1);

         ClientMessage message = consumer.receive(5000);
         assertNotNull(message);

         message = consumer.receiveImmediate();
         assertNull(message);
      }

      AMQPLargeMessagesTestUtil.validateAllTemporaryBuffers(server0);
      AMQPLargeMessagesTestUtil.validateAllTemporaryBuffers(server1);
   }

   protected void setupClusterConnection(final String name,
                                         final String address,
                                         final MessageLoadBalancingType messageLoadBalancingType,
                                         final int maxHops,
                                         final boolean netty,
                                         final int nodeFrom,
                                         final int... nodesTo) {
      setupClusterConnection(name, address, messageLoadBalancingType, maxHops, netty, null, nodeFrom, nodesTo);
   }

   protected void setupClusterConnection(final String name,
                                         final String address,
                                         final MessageLoadBalancingType messageLoadBalancingType,
                                         final int maxHops,
                                         final boolean netty,
                                         final ClusterTestBase.ClusterConfigCallback cb,
                                         final int nodeFrom,
                                         final int... nodesTo) {
      ActiveMQServer serverFrom = servers[nodeFrom];

      if (serverFrom == null) {
         throw new IllegalStateException("No server at node " + nodeFrom);
      }

      TransportConfiguration connectorFrom = createTransportConfiguration(netty, false, generateParams(nodeFrom, netty));
      serverFrom.getConfiguration().getConnectorConfigurations().put(connectorFrom.getName(), connectorFrom);

      List<String> pairs = getClusterConnectionTCNames(netty, serverFrom, nodesTo);
      Configuration config = serverFrom.getConfiguration();
      ClusterConnectionConfiguration clusterConf = createClusterConfig(name, address, messageLoadBalancingType, maxHops, connectorFrom, pairs);

      if (cb != null) {
         cb.configure(clusterConf);
      }
      config.getClusterConfigurations().add(clusterConf);
   }

   private List<String> getClusterConnectionTCNames(boolean netty, ActiveMQServer serverFrom, int[] nodesTo) {
      List<String> pairs = new ArrayList<>();
      for (int element : nodesTo) {
         TransportConfiguration serverTotc = createTransportConfiguration(netty, false, generateParams(element, netty));
         serverFrom.getConfiguration().getConnectorConfigurations().put(serverTotc.getName(), serverTotc);
         pairs.add(serverTotc.getName());
      }
      return pairs;
   }

   private ClusterConnectionConfiguration createClusterConfig(final String name,
                                                              final String address,
                                                              final MessageLoadBalancingType messageLoadBalancingType,
                                                              final int maxHops,
                                                              TransportConfiguration connectorFrom,
                                                              List<String> pairs) {
      return new ClusterConnectionConfiguration().setName(name).setAddress(address).setConnectorName(connectorFrom.getName()).setRetryInterval(250).setMessageLoadBalancingType(messageLoadBalancingType).setMaxHops(maxHops).setConfirmationWindowSize(1024).setStaticConnectors(pairs);
   }
}
