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
package org.apache.activemq.artemis.tests.integration.amqp.largemessages;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.lang.invoke.MethodHandles;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.config.ClusterConnectionConfiguration;
import org.apache.activemq.artemis.protocol.amqp.broker.ProtonProtocolManagerFactory;
import org.apache.activemq.artemis.tests.integration.cluster.distribution.ClusterTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class AmqpEmbeddedLargeCoreMessageClusterTest extends ClusterTestBase {

   protected static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
   private final String CLUSTER_CONNECTION_NAME = "myCluster";

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      for (int i = 0; i < 2; i++) {
         setupServer(i, true, true);
         getServer(i).getConfiguration().setJournalFileSize(10 * 1024 * 1024);

         // make sure we can use the AMQP protocol (Proton = AMQP)
         getServer(i).addProtocolManagerFactory(new ProtonProtocolManagerFactory());
      }
      setupClusterConnection(new ClusterConnectionConfiguration().setName(CLUSTER_CONNECTION_NAME).setReconnectAttempts(5), true, 0, 1);
      setupClusterConnection(new ClusterConnectionConfiguration().setName(CLUSTER_CONNECTION_NAME).setReconnectAttempts(5), true, 1, 0);

      startServers(0);
      startServers(1);

      waitForTopology(servers[0], 2);
      waitForTopology(servers[1], 2);
   }

   @Test
   public void testAmqpMessageEmbeddedInLargeCoreMessageAcrossClusterBridge() throws Exception {
      final String queueName = getName();

      servers[0].createQueue(QueueConfiguration.of(getName()).setRoutingType(RoutingType.ANYCAST));
      servers[1].createQueue(QueueConfiguration.of(getName()).setRoutingType(RoutingType.ANYCAST));

      waitForBindings(0, queueName, 1, 0, true);
      waitForBindings(1, queueName, 1, 0, true);

      waitForBindings(0, queueName, 1, 0, false);
      waitForBindings(1, queueName, 1, 0, false);

      final ConnectionFactory producerConnectionFactory = new JmsConnectionFactory("amqp://localhost:61616");
      final ConnectionFactory consumerConnectionFactory = new JmsConnectionFactory("amqp://localhost:61617");

      try (Connection producerConnection = producerConnectionFactory.createConnection();
           Connection consumerConnection = consumerConnectionFactory.createConnection()) {

         // create consumer
         Session consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer consumer = consumerSession.createConsumer(consumerSession.createQueue(queueName));

         // create producer & send message
         Session producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer producer = producerSession.createProducer(producerSession.createQueue(queueName));
         TextMessage msg = producerSession.createTextMessage();

         // this is the magic number - small enough to be small, but large enough to make the Core message which embeds it large
         msg.setText("x".repeat(102176));

         producer.send(msg);

         Wait.assertEquals(1L, () -> servers[0].getClusterManager().getClusterConnection(CLUSTER_CONNECTION_NAME).getBridges()[0].getQueue().getMessagesAdded(), 2000, 20);
         Wait.assertEquals(1L, () -> servers[0].getClusterManager().getClusterConnection(CLUSTER_CONNECTION_NAME).getBridges()[0].getQueue().getMessagesAcknowledged(), 2000, 20);
         Wait.assertEquals(1L, () -> servers[1].locateQueue(queueName).getMessagesAdded(), 2000, 20);

         // receive the message
         consumerConnection.start();
         assertNotNull(consumer.receive(1000));
      }

      validateNoFilesOnLargeDir(servers[1].getConfiguration().getLargeMessagesDirectory(), 0);
   }
}