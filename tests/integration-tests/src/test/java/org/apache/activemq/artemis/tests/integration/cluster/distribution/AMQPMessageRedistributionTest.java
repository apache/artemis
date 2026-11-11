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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.protocol.amqp.broker.ProtonProtocolManagerFactory;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

public class AMQPMessageRedistributionTest extends ClusterTestBase {

   final String queue = "exampleQueue";
   final String broker0 = "amqp://localhost:61616";
   final String broker1 = "amqp://localhost:61617";

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      start();
   }

   private void start() throws Exception {
      setupServers();

      setRedistributionDelay(0);
   }

   protected boolean isNetty() {
      return true;
   }

   @Test
   public void testMessageRedistributionWithoutDupAMQP() throws Exception {
      setupCluster(MessageLoadBalancingType.ON_DEMAND);

      startServers(0, 1);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());

      createQueue(0, queue, queue, null, true, null, null, RoutingType.ANYCAST);
      createQueue(1, queue, queue, null, true, null, null, RoutingType.ANYCAST);

      waitForBindings(0, queue, 1, 0, true);
      waitForBindings(1, queue, 1, 0, true);

      waitForBindings(0, queue, 1, 0, false);
      waitForBindings(1, queue, 1, 0, false);

      final int NUMBER_OF_MESSAGES = 20;

      JmsConnectionFactory factory = new JmsConnectionFactory(broker0);
      Connection connection = factory.createConnection();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageProducer producer = session.createProducer(session.createQueue(queue));

      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         producer.send(session.createTextMessage("hello " + i));
      }
      connection.close();

      receiveOnBothNodes(NUMBER_OF_MESSAGES);
   }

   private void receiveOnBothNodes(int NUMBER_OF_MESSAGES) throws Exception {
      receiveBroker(broker1, 1);
      receiveBroker(broker0, 1);
      receiveBrokerAll(broker1, NUMBER_OF_MESSAGES - 2);
   }

   private void receiveBrokerAll(String brokerurl, int num) throws Exception {
      JmsConnectionFactory factory = new JmsConnectionFactory(brokerurl);
      Connection connection = factory.createConnection();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageConsumer consumer = session.createConsumer(session.createQueue(queue));
      connection.start();

      for (int i = 0; i < num; i++) {
         TextMessage msg = (TextMessage) consumer.receive(5000);
         assertNotNull(msg);
      }
      Message msg = consumer.receiveNoWait();
      assertNull(msg);
      connection.close();
   }

   private void receiveBroker(String brokerurl, int num) throws Exception {
      JmsConnectionFactory factory = new JmsConnectionFactory(brokerurl);
      Connection connection = factory.createConnection();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageConsumer consumer = session.createConsumer(session.createQueue(queue));
      connection.start();

      for (int i = 0; i < num; i++) {
         Message msg = consumer.receive(5000);
         assertNotNull(msg);
      }
      connection.close();
   }

   protected void setupCluster(final MessageLoadBalancingType messageLoadBalancingType) throws Exception {
      setupClusterConnection("cluster0", queue, messageLoadBalancingType, 1, isNetty(), 0, 1);

      setupClusterConnection("cluster1", queue, messageLoadBalancingType, 1, isNetty(), 1, 0);
   }

   protected void setRedistributionDelay(final long delay) {
      AddressSettings as = new AddressSettings().setRedistributionDelay(delay);

      getServer(0).getAddressSettingsRepository().addMatch("*", as);
      getServer(1).getAddressSettingsRepository().addMatch("*", as);
   }

   protected void setupServers() throws Exception {
      setupServer(0, isFileStorage(), isNetty());
      setupServer(1, isFileStorage(), isNetty());

      servers[0].addProtocolManagerFactory(new ProtonProtocolManagerFactory());
      servers[1].addProtocolManagerFactory(new ProtonProtocolManagerFactory());
   }

   protected void stopServers() throws Exception {
      closeAllConsumers();

      closeAllSessionFactories();

      closeAllServerLocatorsFactories();

      stopServers(0, 1);

      clearServer(0, 1);
   }
}
