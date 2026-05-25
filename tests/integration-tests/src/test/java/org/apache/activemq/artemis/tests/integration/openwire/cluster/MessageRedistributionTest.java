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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.Bindings;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.cluster.RemoteQueueBinding;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.tests.integration.cluster.distribution.ClusterTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.command.ActiveMQDestination;
import org.apache.activemq.util.ConsumerThread;
import org.junit.jupiter.api.Test;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MessageRedistributionTest extends ClusterTestBase {

   @Override
   protected boolean isForceUniqueStorageManagerIds() {
      // we want to verify messageId uniqueness across brokers
      return false;
   }

   @Test
   public void testRemoteConsumerClose() throws Exception {

      setupServer(0, true, true);
      setupServer(1, true, true);

      setupClusterConnection("cluster0", "queues", MessageLoadBalancingType.ON_DEMAND, 1, true, 0, 1);
      setupClusterConnection("cluster1", "queues", MessageLoadBalancingType.ON_DEMAND, 1, true, 1, 0);

      startServers(0, 1);

      waitForTopology(servers[0], 2);
      waitForTopology(servers[1], 2);

      setupSessionFactory(0, true);
      setupSessionFactory(1, true);

      createAddressInfo(0, "queues.testAddress", RoutingType.ANYCAST, -1, false);
      createAddressInfo(1, "queues.testAddress", RoutingType.ANYCAST, -1, false);
      createQueue(0, "queues.testaddress", "queue0", null, true);
      createQueue(1, "queues.testaddress", "queue0", null, true);

      //alternately create consumers to the 2 nodes
      //close the connection then close consumer quickly
      //check server's consumer count
      for (int i = 0; i < 50; i++) {
         int target = i % 2;
         int remote = (i + 1) % 2;
         closeConsumerAndConnectionConcurrently(target, remote);
      }
   }

   @Test
   public void testFailoverNonClusteredBrokersInteropWithCoreProducer() throws Exception {

      setupServer(0, true, true);
      setupServer(1, true, true);

      startServers(0, 1);

      servers[0].getAddressSettingsRepository().getMatch("#").setRedeliveryDelay(0).setRedistributionDelay(0);
      servers[1].getAddressSettingsRepository().getMatch("#").setRedeliveryDelay(0).setRedistributionDelay(0);

      setupSessionFactory(0, true);
      setupSessionFactory(1, true);

      createAddressInfo(0, "q", RoutingType.ANYCAST, -1, false);
      createAddressInfo(1, "q", RoutingType.ANYCAST, -1, false);
      createQueue(0, "q", "q", null, true, RoutingType.ANYCAST);
      createQueue(1, "q", "q", null, true, RoutingType.ANYCAST);


      final int numMessagesPerNode = 1000;
      produceWithCoreTo(0, numMessagesPerNode);
      produceWithCoreTo(1, numMessagesPerNode);

      // consume with openwire from both brokers which both start with journal id = 0, should be in lock step

      String zero = getServerUri(0);
      String one = getServerUri(1);

      ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory("failover:(" + zero + "," + one + ")?jms.prefetchPolicy.all=10&randomize=false&timeout=400&reconnectDelay=500&useExponentialBackOff=false&initialReconnectDelay=500&nested.wireFormat.maxInactivityDuration=500&nested.wireFormat.maxInactivityDurationInitalDelay=500&nested.ignoreRemoteWireFormat=true&nested.soTimeout=500&nested.connectionTimeout=400&jms.connectResponseTimeout=400&jms.sendTimeout=400&jms.closeTimeout=400");
      factory.setWatchTopicAdvisories(false);

      CountDownLatch continueLatch = new CountDownLatch(1);
      CountDownLatch received = new CountDownLatch(numMessagesPerNode * 2);
      final Connection conn = factory.createConnection();
      conn.start();

      ((ActiveMQConnection)conn).setClientInternalExceptionListener(Throwable::printStackTrace);

      Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Destination dest = ActiveMQDestination.createDestination("q", ActiveMQDestination.QUEUE_TYPE);
      session.createConsumer(dest).setMessageListener(message -> {
         try {
            received.countDown();
         } catch (Exception exception) {
            exception.printStackTrace();
         }
      });


      assertTrue(Wait.waitFor(() -> received.getCount() <= numMessagesPerNode));

      // force a failover to the other broker
      servers[0].stop(false, true);

      // get all the messages, our openwire audit does not detect any duplicate
      assertTrue(Wait.waitFor(() -> {
         return received.await(1, TimeUnit.SECONDS);
      }));

      conn.close();
   }

   private void produceWithCoreTo(int serveId, final int numMessagesPerNode) throws Exception {

      String targetUrl = getServerUri(serveId);
      Connection jmsConn = null;
      try {
         org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory coreCf = ActiveMQJMSClient.createConnectionFactory(targetUrl, "cf" + serveId);
         jmsConn = coreCf.createConnection();
         jmsConn.setClientID("theProducer");
         Session coreSession = jmsConn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         TextMessage msg = coreSession.createTextMessage("TEXT");
         Queue queue = coreSession.createQueue("q");
         MessageProducer producer = coreSession.createProducer(queue);
         for (int i = 0; i < numMessagesPerNode; i++) {
            msg.setIntProperty("MM", i);
            msg.setIntProperty("SN", serveId);
            producer.send(msg);
         }
      } finally {
         if (jmsConn != null) {
            jmsConn.close();
         }
      }
   }

   @Test
   public void testAdvisoriesNotClustered() throws Exception {

      setupServer(0, true, true);
      setupServer(1, true, true);

      setupClusterConnection("cluster0", "", MessageLoadBalancingType.ON_DEMAND, 1, true, 0, 1);
      setupClusterConnection("cluster1", "", MessageLoadBalancingType.ON_DEMAND, 1, true, 1, 0);

      startServers(0, 1);

      waitForTopology(servers[0], 2);
      waitForTopology(servers[1], 2);

      setupSessionFactory(0, true);
      setupSessionFactory(1, true);

      createAddressInfo(0, "testAddress", RoutingType.MULTICAST, -1, false);
      createAddressInfo(1, "testAddress", RoutingType.MULTICAST, -1, false);

      ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory();
      ActiveMQConnectionFactory factory2 = new ActiveMQConnectionFactory(getServerUri(1));
      Connection conn = null;
      Connection conn2 = null;
      CountDownLatch active = new CountDownLatch(1);
      try {
         conn = factory.createConnection();
         conn2 = factory2.createConnection();
         conn2.setClientID("id");
         conn2.start();
         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session session2 = conn2.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Topic dest = (Topic) ActiveMQDestination.createDestination("testAddress", ActiveMQDestination.TOPIC_TYPE);
         TopicSubscriber mySubscriber = session2.createDurableSubscriber(dest, "mySubscriber");
         MessageProducer producer = session.createProducer(dest);
         producer.send(session.createTextMessage("test message"));
         Message message = mySubscriber.receive(5000);
         SimpleString advQueue = SimpleString.of("ActiveMQ.Advisory.TempQueue");
         SimpleString advTopic = SimpleString.of("ActiveMQ.Advisory.TempTopic");
         //we create a consumer on node 2 and assert that the advisory subscription queue is not clustered
         assertEquals(1, servers[0].getPostOffice().getBindingsForAddress(advQueue).getBindings().size(), "");
         assertEquals(1, servers[0].getPostOffice().getBindingsForAddress(advTopic).getBindings().size(), "");
         assertEquals(1, servers[1].getPostOffice().getBindingsForAddress(advQueue).getBindings().size(), "");
         assertEquals(1, servers[1].getPostOffice().getBindingsForAddress(advTopic).getBindings().size(), "");

      } finally {
         conn.close();
         conn2.close();
      }
   }

   @Override
   protected boolean isResolveProtocols() {
      return true;
   }

   private void closeConsumerAndConnectionConcurrently(int targetNode, int remoteNode) throws Exception {

      String targetUri = getServerUri(targetNode);
      ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(targetUri);
      Connection conn = null;
      CountDownLatch active = new CountDownLatch(1);
      try {
         conn = factory.createConnection();
         conn.start();
         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Destination dest = ActiveMQDestination.createDestination("queue0", ActiveMQDestination.QUEUE_TYPE);
         ConsumerThread consumer = new ConsumerThread(session, dest);
         consumer.setMessageCount(0);
         consumer.setFinished(active);
         consumer.start();

         assertTrue(active.await(5, TimeUnit.SECONDS), "consumer takes too long to finish!");
      } finally {
         conn.close();
      }

      Wait.waitFor(() -> getRemoteQueueBinding(servers[remoteNode]) != null);

      //check remote server's consumer count
      RemoteQueueBinding remoteBinding = getRemoteQueueBinding(servers[remoteNode]);

      assertNotNull(remoteBinding);

      Wait.waitFor(() -> remoteBinding.consumerCount() >= 0);
      int count = remoteBinding.consumerCount();
      assertTrue(count >= 0, "consumer count should never be negative " + count);
   }

   private RemoteQueueBinding getRemoteQueueBinding(ActiveMQServer server) throws Exception {
      ActiveMQServer remoteServer = server;
      Bindings bindings = remoteServer.getPostOffice().getBindingsForAddress(SimpleString.of("queues.testaddress"));
      Collection<Binding> bindingSet = bindings.getBindings();

      return getRemoteQueueBinding(bindingSet);
   }

   private RemoteQueueBinding getRemoteQueueBinding(Collection<Binding> bindingSet) {
      RemoteQueueBinding remoteBinding = null;
      for (Binding b : bindingSet) {
         if (b instanceof RemoteQueueBinding remoteQueueBinding) {
            remoteBinding = remoteQueueBinding;
            break;
         }
      }
      return remoteBinding;
   }
}
