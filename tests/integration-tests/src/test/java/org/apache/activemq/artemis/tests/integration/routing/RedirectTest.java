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
package org.apache.activemq.artemis.tests.integration.routing;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.server.routing.KeyType;
import org.apache.activemq.artemis.core.server.routing.policies.ConsistentHashPolicy;
import org.apache.activemq.artemis.core.server.routing.policies.FirstElementPolicy;
import org.apache.activemq.artemis.core.server.routing.policies.LeastConnectionsPolicy;
import org.apache.activemq.artemis.core.server.routing.policies.RoundRobinPolicy;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(ParameterizedTestExtension.class)
public class RedirectTest extends RoutingTestBase {

   @Parameters(name = "pool: {0}")
   public static Collection<Object[]> parameters() {
      return Arrays.asList(new Object[][] {{CLUSTER_POOL}, {DISCOVERY_POOL}, {STATIC_POOL} });
   }


   private final String pool;


   public RedirectTest(String pool) {
      this.pool = pool;
   }

   @TestTemplate
   public void testSimpleRedirect() throws Exception {
      final String queueName = "RedirectTestQueue";

      setupPrimaryServerWithDiscovery(0, GROUP_ADDRESS, GROUP_PORT, true, true, false);
      setupPrimaryServerWithDiscovery(1, GROUP_ADDRESS, GROUP_PORT, true, true, false);
      if (CLUSTER_POOL.equals(pool)) {
         setupDiscoveryClusterConnection("cluster0", 0, "dg1", "queues", MessageLoadBalancingType.OFF, 1, true);
         setupDiscoveryClusterConnection("cluster1", 1, "dg1", "queues", MessageLoadBalancingType.OFF, 1, true);
         setupRouterServerWithCluster(0, KeyType.USER_NAME, FirstElementPolicy.NAME, null, false, "ACTIVEMQ.CLUSTER.ADMIN.USER", 1, "cluster0");
      } else if (DISCOVERY_POOL.equals(pool)) {
         setupRouterServerWithDiscovery(0, KeyType.USER_NAME, FirstElementPolicy.NAME, null, false, null, 1);
      } else {
         setupRouterServerWithStaticConnectors(0, KeyType.USER_NAME, FirstElementPolicy.NAME, null, false, null, 1, 1);
      }

      startServers(0, 1);

      getServer(0).createQueue(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST));
      getServer(1).createQueue(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST));

      testSimpleRedirectWithProtocol(AMQP_PROTOCOL, queueName);
      testSimpleRedirectWithProtocol(CORE_PROTOCOL, queueName);
      testSimpleRedirectWithProtocol(OPENWIRE_PROTOCOL, queueName);

      stopServers(0, 1);
   }

   private void testSimpleRedirectWithProtocol(final String protocol, final String queueName) throws Exception {
      QueueControl queueControl0 = getServer(0).getManagementService().getQueueControl(queueName);
      QueueControl queueControl1 = getServer(1).getManagementService().getQueueControl(queueName);

      assertEquals(0, queueControl0.countMessages());
      assertEquals(0, queueControl1.countMessages());

      ConnectionFactory connectionFactory = createFactory(protocol, false, TransportConstants.DEFAULT_HOST,
          TransportConstants.DEFAULT_PORT + 0, null, "admin", "admin");


      try (Connection connection = connectionFactory.createConnection()) {
         connection.start();
         try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
            javax.jms.Queue queue = session.createQueue(queueName);
            try (MessageProducer producer = session.createProducer(queue)) {
               producer.send(session.createTextMessage("TEST" + protocol));
            }
         }
      }

      assertEquals(0, queueControl0.countMessages());
      assertEquals(1, queueControl1.countMessages());

      try (Connection connection = connectionFactory.createConnection()) {
         connection.start();
         try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
            try (MessageConsumer consumer = session.createConsumer(session.createQueue(queueName))) {
               TextMessage message = (TextMessage) consumer.receive(1000);
               assertNotNull(message);
               assertEquals("TEST" + protocol, message.getText());
            }
         }
      }

      assertEquals(0, queueControl0.countMessages());
      assertEquals(0, queueControl1.countMessages());
   }

   @TestTemplate
   public void testRoundRobinRedirect() throws Exception {
      testEvenlyRedirect(RoundRobinPolicy.NAME, null, false);
   }

   @TestTemplate
   public void testLeastConnectionsRedirect() throws Exception {
      testEvenlyRedirect(LeastConnectionsPolicy.NAME, Collections.singletonMap(
         LeastConnectionsPolicy.CONNECTION_COUNT_THRESHOLD, String.valueOf(30)), false);
   }

   @TestTemplate
   public void testRoundRobinRedirectWithFailure() throws Exception {
      testEvenlyRedirect(RoundRobinPolicy.NAME, null, true);
   }

   @TestTemplate
   public void testLeastConnectionsRedirectWithFailure() throws Exception {
      testEvenlyRedirect(LeastConnectionsPolicy.NAME, Collections.singletonMap(
         LeastConnectionsPolicy.CONNECTION_COUNT_THRESHOLD, String.valueOf(30)), true);
   }

   private void testEvenlyRedirect(final String policyName, final Map<String, String> properties, final boolean withFailure) throws Exception {
      final String queueName = "RedirectTestQueue";
      final int[] nodes = new int[] {0, 1, 2, 3};

      for (int node : nodes) {
         setupPrimaryServerWithDiscovery(node, GROUP_ADDRESS, GROUP_PORT, true, true, false);
      }

      if (CLUSTER_POOL.equals(pool)) {
         for (int node : nodes) {
            setupDiscoveryClusterConnection("cluster" + node, node, "dg1", "queues", MessageLoadBalancingType.OFF, 1, true);
         }
         setupRouterServerWithCluster(0, KeyType.USER_NAME, policyName, properties, false, "ACTIVEMQ.CLUSTER.ADMIN.USER", nodes.length - 1, "cluster0");
      } else if (DISCOVERY_POOL.equals(pool)) {
         setupRouterServerWithDiscovery(0, KeyType.USER_NAME, policyName, properties, false, null, nodes.length - 1);
      } else {
         setupRouterServerWithStaticConnectors(0, KeyType.USER_NAME, policyName, properties, false, null, nodes.length - 1, 1, 2, 3);
      }

      if (withFailure) {
         setupRouterLocalCache(0, true, 0);
      }

      startServers(nodes);

      for (int node : nodes) {
         getServer(node).createQueue(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST));
      }

      testEvenlyRedirectWithProtocol(AMQP_PROTOCOL, queueName, nodes, withFailure);
      testEvenlyRedirectWithProtocol(CORE_PROTOCOL, queueName, nodes, withFailure);
      testEvenlyRedirectWithProtocol(OPENWIRE_PROTOCOL, queueName, nodes, withFailure);

      stopServers(nodes);
   }

   private void testEvenlyRedirectWithProtocol(final String protocol, final String queueName, final int[] nodes, final boolean withFailure) throws Exception {
      QueueControl[] queueControls = new QueueControl[nodes.length];

      for (int node : nodes) {
         queueControls[node] = getServer(node).getManagementService()
             .getQueueControl(queueName);

         assertEquals(0, queueControls[node].countMessages(), "Unexpected message count for node " + node);
      }

      ConnectionFactory[] connectionFactories = new ConnectionFactory[nodes.length - 1];
      Connection[] connections = new Connection[nodes.length - 1];
      Session[] sessions = new Session[nodes.length - 1];

      for (int i = 0; i < nodes.length - 1; i++) {
         connectionFactories[i] = createFactory(protocol, false, TransportConstants.DEFAULT_HOST,
             TransportConstants.DEFAULT_PORT + 0, null, "user" + i, "user" + i);

         connections[i] = connectionFactories[i].createConnection();
         connections[i].start();

         sessions[i] = connections[i].createSession(false, Session.AUTO_ACKNOWLEDGE);
      }

      for (int i = 0; i < nodes.length - 1; i++) {
         try (MessageProducer producer = sessions[i].createProducer(sessions[i].createQueue(queueName))) {
            producer.send(sessions[i].createTextMessage("TEST" + protocol + i));
         }

         sessions[i].close();
         connections[i].close();
      }

      assertEquals(0, queueControls[0].countMessages());
      for (int targetNode = 1; targetNode < nodes.length - 1; targetNode++) {
         assertEquals(1, queueControls[targetNode].countMessages(), "Messages of node " + targetNode);
      }

      if (withFailure) {
         crashAndWaitForFailure(getServer(0));

         startServers(0);

         queueControls[0] = getServer(0).getManagementService()
             .getQueueControl(queueName);
      }

      for (int i = 0; i < nodes.length - 1; i++) {
         try (Connection connection = connectionFactories[i].createConnection()) {
            connection.start();
            try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
               try (MessageConsumer consumer = session.createConsumer(session.createQueue(queueName))) {
                  TextMessage message = (TextMessage) consumer.receive(1000);
                  assertNotNull(message);
                  assertEquals("TEST" + protocol + i, message.getText());
               }
            }
         }
      }

      for (int node : nodes) {
         assertEquals(0, queueControls[node].countMessages(), "Unexpected message count for node " + node);
      }
   }

   @TestTemplate
   public void testSymmetricRedirect() throws Exception {
      final String queueName = "RedirectTestQueue";

      setupPrimaryServerWithDiscovery(0, GROUP_ADDRESS, GROUP_PORT, true, true, false);
      setupPrimaryServerWithDiscovery(1, GROUP_ADDRESS, GROUP_PORT, true, true, false);
      if (CLUSTER_POOL.equals(pool)) {
         setupDiscoveryClusterConnection("cluster0", 0, "dg1", "queues", MessageLoadBalancingType.OFF, 1, true);
         setupDiscoveryClusterConnection("cluster1", 1, "dg1", "queues", MessageLoadBalancingType.OFF, 1, true);
         setupRouterServerWithCluster(0, KeyType.USER_NAME, ConsistentHashPolicy.NAME, null, true, "ACTIVEMQ.CLUSTER.ADMIN.USER", 2, "cluster0");
         setupRouterServerWithCluster(1, KeyType.USER_NAME, ConsistentHashPolicy.NAME, null, true, "ACTIVEMQ.CLUSTER.ADMIN.USER", 2, "cluster1");
      } else if (DISCOVERY_POOL.equals(pool)) {
         setupRouterServerWithDiscovery(0, KeyType.USER_NAME, ConsistentHashPolicy.NAME, null, true, null, 2);
         setupRouterServerWithDiscovery(1, KeyType.USER_NAME, ConsistentHashPolicy.NAME, null, true, null, 2);
      } else {
         setupRouterServerWithStaticConnectors(0, KeyType.USER_NAME, ConsistentHashPolicy.NAME, null, true, null, 2, 1);
         setupRouterServerWithStaticConnectors(1, KeyType.USER_NAME, ConsistentHashPolicy.NAME, null, true, null, 2, 0);
      }

      startServers(0, 1);

      assertNotEquals(getServer(0).getNodeID(), getServer(1).getNodeID());

      getServer(0).createQueue(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST));
      getServer(1).createQueue(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST));

      testSymmetricRedirectWithProtocol(AMQP_PROTOCOL, queueName);
      testSymmetricRedirectWithProtocol(CORE_PROTOCOL, queueName);
      testSymmetricRedirectWithProtocol(OPENWIRE_PROTOCOL, queueName);

      stopServers(0, 1);
   }

   private void testSymmetricRedirectWithProtocol(final String protocol, final String queueName) throws Exception {
      QueueControl queueControl0 = getServer(0).getManagementService()
          .getQueueControl(queueName);
      QueueControl queueControl1 = getServer(1).getManagementService()
          .getQueueControl(queueName);

      assertEquals(0, queueControl0.countMessages(), "Unexpected message count for node 0");
      assertEquals(0, queueControl1.countMessages(), "Unexpected message count for node 1");

      ConnectionFactory connectionFactory0 = createFactory(protocol, false, TransportConstants.DEFAULT_HOST,
          TransportConstants.DEFAULT_PORT + 0, null, "admin", "admin");


      try (Connection connection = connectionFactory0.createConnection()) {
         connection.start();
         try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
            javax.jms.Queue queue = session.createQueue(queueName);
            try (MessageProducer producer = session.createProducer(queue)) {
               producer.send(session.createTextMessage("TEST" + protocol));
            }
         }
      }

      assertTrue((queueControl0.countMessages() == 0 && queueControl1.countMessages() == 1) ||
          (queueControl0.countMessages() == 1 && queueControl1.countMessages() == 0));

      assertNotEquals(getServer(0).getNodeID(), getServer(1).getNodeID());

      ConnectionFactory connectionFactory1 = createFactory(protocol, false, TransportConstants.DEFAULT_HOST,
          TransportConstants.DEFAULT_PORT + 1, null, "admin", "admin");

      try (Connection connection = connectionFactory1.createConnection()) {
         connection.start();
         try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
            try (MessageConsumer consumer = session.createConsumer(session.createQueue(queueName))) {
               TextMessage message = (TextMessage) consumer.receive(1000);
               assertNotNull(message);
               assertEquals("TEST" + protocol, message.getText());
            }
         }
      }

      assertEquals(0, queueControl0.countMessages(), "Unexpected message count for node 0");
      assertEquals(0, queueControl1.countMessages(), "Unexpected message count for node 1");
   }

   @TestTemplate
   public void testRedirectAfterFailure() throws Exception {
      final String queueName = "RedirectTestQueue";
      final int[] nodes = new int[] {0, 1, 2};

      for (int node : nodes) {
         setupPrimaryServerWithDiscovery(node, GROUP_ADDRESS, GROUP_PORT, true, true, false);
      }
      if (CLUSTER_POOL.equals(pool)) {
         for (int node : nodes) {
            setupDiscoveryClusterConnection("cluster" + node, node, "dg1", "queues", MessageLoadBalancingType.OFF, 1, true);
         }
         setupRouterServerWithCluster(0, KeyType.USER_NAME, FirstElementPolicy.NAME, null, false, "ACTIVEMQ.CLUSTER.ADMIN.USER", 1, "cluster0");
      } else if (DISCOVERY_POOL.equals(pool)) {
         setupRouterServerWithDiscovery(0, KeyType.USER_NAME, FirstElementPolicy.NAME, null, false, null, 1);
      } else {
         setupRouterServerWithStaticConnectors(0, KeyType.USER_NAME, FirstElementPolicy.NAME, null, false, null, 1, 1, 2);
      }

      startServers(0, 1, 2);

      for (int node : nodes) {
         getServer(node).createQueue(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST));
      }

      testRedirectAfterFailureWithProtocol(AMQP_PROTOCOL, queueName, nodes);
      testRedirectAfterFailureWithProtocol(CORE_PROTOCOL, queueName, nodes);
      testRedirectAfterFailureWithProtocol(OPENWIRE_PROTOCOL, queueName, nodes);

      stopServers(0, 1, 2);
   }

   private void testRedirectAfterFailureWithProtocol(final String protocol, final String queueName, final int[] nodes) throws Exception {
      QueueControl[] queueControls = new QueueControl[nodes.length];
      for (int node : nodes) {
         queueControls[node] = getServer(node).getManagementService().getQueueControl(queueName);

         assertEquals(0, queueControls[node].countMessages(), "Unexpected message count for node " + node);
      }

      int failedNode;
      ConnectionFactory connectionFactory = createFactory(protocol, false, TransportConstants.DEFAULT_HOST,
          TransportConstants.DEFAULT_PORT + 0, null, "admin", "admin");


      try (Connection connection = connectionFactory.createConnection()) {
         connection.start();
         try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
            javax.jms.Queue queue = session.createQueue(queueName);
            try (MessageProducer producer = session.createProducer(queue)) {
               producer.send(session.createTextMessage("TEST_BEFORE_FAILURE"));

               if (queueControls[1].countMessages() > 0) {
                  failedNode = 1;
               } else {
                  failedNode = 2;
               }

               stopServers(failedNode);

               producer.send(session.createTextMessage("TEST_AFTER_FAILURE"));
            }
         }
      }

      startServers(failedNode);

      queueControls[failedNode] = getServer(failedNode).getManagementService().getQueueControl(queueName);

      try (Connection connection = connectionFactory.createConnection()) {
         connection.start();
         try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
            try (MessageConsumer consumer = session.createConsumer(session.createQueue(queueName))) {
               TextMessage message = (TextMessage) consumer.receive(1000);
               assertNotNull(message);
               assertEquals("TEST_AFTER_FAILURE", message.getText());
            }
         }
      }

      for (int node : nodes) {
         if (node == failedNode) {
            assertEquals(1, queueControls[node].countMessages(), "Unexpected message count for node " + node);
            queueControls[node].removeAllMessages();
         } else {
            assertEquals(0, queueControls[node].countMessages(), "Unexpected message count for node " + node);
         }
      }
   }
}
