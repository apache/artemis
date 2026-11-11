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

import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RemoteBindingWithoutLoadBalancingTest extends ClusterTestBase {

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      setupServers();
   }

   protected boolean isNetty() {
      return false;
   }

   /**
    * It's possible that when a cluster has disabled message load balancing then a message sent to a node that only has
    * a corresponding remote queue binding will trigger a stack overflow.
    */
   @Test
   public void testStackOverflow() throws Exception {
      setupCluster();

      startServers();

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());

      createQueue(0, "queues.testaddress", "queue0", null, false);

      waitForBindings(0, "queues.testaddress", 1, 0, true);

      waitForBindings(1, "queues.testaddress", 1, 0, false);

      send(1, "queues.testaddress", 1, false, null);
   }

   @Test
   public void testStackOverflowWithLocalConsumerAndFilter() throws Exception {
      setupCluster();

      startServers();

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());

      createQueue(0, "queues.testaddress", "queue0", "0", true);
      createQueue(1, "queues.testaddress", "queue0", "1", true);

      waitForBindings(0, "queues.testaddress", 1, 0, true);

      waitForBindings(1, "queues.testaddress", 1, 0, false);

      for (int i = 0; i < 10; i++) {
         send(1, "queues.testaddress", 10, false, "" + i % 2);
      }
   }

   @Test
   public void testStackOverflowJMS() throws Exception {
      final String QUEUE_NAME = "queues.queue0";

      setupCluster();

      startServers();

      ConnectionFactory cf1 = new ActiveMQConnectionFactory("vm://0");
      Connection c1 = cf1.createConnection();
      c1.start();
      Session s1 = c1.createSession();
      MessageConsumer mc1 = s1.createConsumer(s1.createQueue(QUEUE_NAME));

      waitForBindings(0, QUEUE_NAME, 1, 1, true);
      waitForBindings(1, QUEUE_NAME, 1, 1, false);

      ConnectionFactory cf2 = new ActiveMQConnectionFactory("vm://1");
      Connection c2 = cf2.createConnection();
      Session s2 = c2.createSession();
      MessageProducer mp2 = s2.createProducer(s2.createQueue(QUEUE_NAME));
      mp2.send(s2.createMessage());

      waitForBindings(1, QUEUE_NAME, 1, 0, true);

      assertTrue(Wait.waitFor(() -> servers[1].locateQueue(SimpleString.of(QUEUE_NAME)).getMessageCount() == 1, 2000, 100));

      c1.close();
      c2.close();
   }

   protected void setupCluster() throws Exception {
      setupClusterConnection("cluster0", "queues", MessageLoadBalancingType.OFF, 1, isNetty(), 0, 1);

      setupClusterConnection("cluster1", "queues", MessageLoadBalancingType.OFF, 1, isNetty(), 1, 0);
   }

   protected void setupServers() throws Exception {
      setupServer(0, isFileStorage(), isNetty());
      setupServer(1, isFileStorage(), isNetty());
   }

   protected void startServers() throws Exception {
      startServers(0, 1);
   }

   protected void stopServers() throws Exception {
      closeAllConsumers();

      closeAllSessionFactories();

      closeAllServerLocatorsFactories();

      stopServers(0, 1);
   }

   @Override
   protected boolean isFileStorage() {
      return false;
   }
}
