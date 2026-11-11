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
package org.apache.activemq.artemis.tests.integration.amqp.connect;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.ClusterConnectionConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPMirrorBrokerConnectionElement;
import org.apache.activemq.artemis.core.config.ha.PrimaryOnlyPolicyConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.integration.amqp.AmqpClientTestSupport;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.Test;

import java.util.Collections;

public class AMQPClusterReplicaTest extends AmqpClientTestSupport {

   protected static final int NODE_1_PORT = 5673;
   protected static final int NODE_2_PORT = 5674;

   @Test
   public void testReplicaWithCluster() throws Exception {
      ActiveMQServer node_1 = createNode1(MessageLoadBalancingType.ON_DEMAND);
      ActiveMQServer node_2 = createNode2(MessageLoadBalancingType.ON_DEMAND);

      server.start();

      // Set node_1 mirror to target
      node_1.getConfiguration().addAMQPConnection(new AMQPBrokerConnectConfiguration("mirror", "tcp://localhost:" + AMQP_PORT).setReconnectAttempts(-1).setRetryInterval(100).addConnectionElement(new AMQPMirrorBrokerConnectionElement().setDurable(true)));

      node_1.start();
      node_2.start();

      configureAddressAndQueue(node_1);
      configureAddressAndQueue(node_2);

      waitForTopology(node_1, 2);
      waitForTopology(node_2, 2);

      {
         // sender
         ClientSessionFactory sessionFactory = addSessionFactory(getNode1ServerLocator().createSessionFactory());
         ClientSession session = addClientSession(sessionFactory.createSession());
         sendMessages(session, addClientProducer(session.createProducer("test")), 10);
      }

      {
         // receiver
         ClientSessionFactory sessionFactory = addSessionFactory(getNode2ServerLocator().createSessionFactory());
         ClientSession session = addClientSession(sessionFactory.createSession());
         session.start();
         receiveMessages(addClientConsumer(session.createConsumer("test")), 0, 10, true);
      }

      // Wait to mirror target to read all messages
      Wait.waitFor(() -> node_1.locateQueue("$ACTIVEMQ_ARTEMIS_MIRROR_mirror").getMessageCount() == 0);

      // Expect no messages in mirrored test queue
      Wait.assertEquals(0, () -> server.locateQueue("test").getMessageCount());
   }



   @Test
   public void testReplicaWithClusterTargetStrict() throws Exception {
      ActiveMQServer node_1 = createNode1(MessageLoadBalancingType.STRICT);
      ActiveMQServer node_2 = createNode2(MessageLoadBalancingType.STRICT);

      server.stop();
      // Set node_1 mirror to target
      server.getConfiguration().addAMQPConnection(new AMQPBrokerConnectConfiguration("mirror1", "tcp://localhost:" + NODE_1_PORT).setReconnectAttempts(-1).setRetryInterval(100).addConnectionElement(new AMQPMirrorBrokerConnectionElement().setDurable(true)));
      server.start();

      node_1.start();
      node_2.start();

      configureAddressAndQueue(node_1);
      configureAddressAndQueue(server);
      configureAddressAndQueue(node_2);

      waitForTopology(node_1, 2);
      waitForTopology(node_2, 2);

      {
         ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);
         Connection connection = factory.createConnection();
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queue = session.createQueue("test");
         MessageProducer producer = session.createProducer(queue);
         for (int i = 0; i < 10; i++) {
            producer.send(session.createTextMessage("hello"));
         }

         org.apache.activemq.artemis.core.server.Queue node1Queue = node_1.locateQueue("test");

         Wait.assertEquals(10L, node1Queue::getMessageCount, 5000, 10);

         connection.close();
      }
   }

   private ServerLocator getNode1ServerLocator() throws Exception {
      return addServerLocator(ActiveMQClient.createServerLocator("tcp://localhost:" + NODE_1_PORT));
   }

   private ServerLocator getNode2ServerLocator() throws Exception {
      return addServerLocator(ActiveMQClient.createServerLocator("tcp://localhost:" + NODE_2_PORT));
   }

   private ActiveMQServer createNode1(MessageLoadBalancingType loadBalancingType) throws Exception {
      ActiveMQServer node_1 = createServer(NODE_1_PORT, false);

      ClusterConnectionConfiguration clusterConfiguration = new ClusterConnectionConfiguration().setName("cluster").setConnectorName("node1").setMessageLoadBalancingType(loadBalancingType).setStaticConnectors(Collections.singletonList("node2"));

      node_1.setIdentity("node_1");
      node_1.getConfiguration().setName("node_1").setHAPolicyConfiguration(new PrimaryOnlyPolicyConfiguration()).addConnectorConfiguration("node1", "tcp://localhost:" + NODE_1_PORT).addConnectorConfiguration("node2", "tcp://localhost:" + NODE_2_PORT).addClusterConfiguration(clusterConfiguration);

      return node_1;
   }

   private ActiveMQServer createNode2(MessageLoadBalancingType loadBalancingType) throws Exception {
      ActiveMQServer node_2 = createServer(NODE_2_PORT, false);

      ClusterConnectionConfiguration clusterConfiguration = new ClusterConnectionConfiguration().setName("cluster").setConnectorName("node2").setMessageLoadBalancingType(loadBalancingType).setStaticConnectors(Collections.singletonList("node1"));

      node_2.setIdentity("node_2");
      node_2.getConfiguration().setName("node_2").setHAPolicyConfiguration(new PrimaryOnlyPolicyConfiguration()).addConnectorConfiguration("node1", "tcp://localhost:" + NODE_1_PORT).addConnectorConfiguration("node2", "tcp://localhost:" + NODE_2_PORT).addClusterConfiguration(clusterConfiguration);

      return node_2;
   }

   private void configureAddressAndQueue(ActiveMQServer node) throws Exception {
      node.addAddressInfo(new AddressInfo("test").setAutoCreated(false));
      node.getAddressSettingsRepository().addMatch("test", new AddressSettings().setRedistributionDelay(0));
      node.createQueue(QueueConfiguration.of("test").setAddress("test").setRoutingType(RoutingType.ANYCAST).setDurable(true));
   }

   @Override
   protected String getConfiguredProtocols() {
      return "AMQP,CORE";
   }
}
