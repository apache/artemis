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
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.DivertConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectionAddressType;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPMirrorBrokerConnectionElement;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class AMQPMirrorOneSideDisconnectedTest extends ActiveMQTestBase {

   protected TransportConfiguration newAcceptorConfig(int port, String name) {
      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.PORT_PROP_NAME, String.valueOf(port));
      params.put(TransportConstants.PROTOCOLS_PROP_NAME, "AMQP,CORE,OPENWIRE");
      Map<String, Object> amqpParams = new HashMap<>();
      TransportConfiguration tc = new TransportConfiguration(NETTY_ACCEPTOR_FACTORY, params, name, amqpParams);
      return tc;
   }

   protected ActiveMQServer createServer(int port, String brokerName) throws Exception {

      final ActiveMQServer server = this.createServer(true, true);

      server.getConfiguration().getAcceptorConfigurations().clear();
      server.getConfiguration().getAcceptorConfigurations().add(newAcceptorConfig(port, "netty-acceptor"));
      server.getConfiguration().setName(brokerName);
      server.getConfiguration().setJournalDirectory(server.getConfiguration().getJournalDirectory() + port);
      server.getConfiguration().setBindingsDirectory(server.getConfiguration().getBindingsDirectory() + port);
      server.getConfiguration().setPagingDirectory(server.getConfiguration().getPagingDirectory() + port);
      server.getConfiguration().setJMXManagementEnabled(true);
      server.getConfiguration().setMessageExpiryScanPeriod(100);
      return server;
   }


   @Test
   public void testOneSideDisconnected() throws Exception {
      ActiveMQServer serverA = createServer(5671, "serverA");
      ActiveMQServer serverB = createServer(6671, "serverB");

      {
         AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration("A_to_B", "tcp://localhost:6671").setReconnectAttempts(-1).setRetryInterval(10);
         AMQPMirrorBrokerConnectionElement replica1 = new AMQPMirrorBrokerConnectionElement().setType(AMQPBrokerConnectionAddressType.MIRROR).setDurable(true);
         amqpConnection.addElement(replica1);
         serverA.getConfiguration().addAMQPConnection(amqpConnection);
      }

      {
         AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration("B_to_A", "tcp://localhost:5680").setReconnectAttempts(-1).setRetryInterval(10);
         AMQPMirrorBrokerConnectionElement replica1 = new AMQPMirrorBrokerConnectionElement().setType(AMQPBrokerConnectionAddressType.MIRROR).setDurable(true);
         amqpConnection.addElement(replica1);
         serverB.getConfiguration().addAMQPConnection(amqpConnection);
      }

      String queueName = "queue" + RandomUtil.randomUUIDString();
      String divertedQueue = "queue" + RandomUtil.randomUUIDString();

      serverA.getConfiguration().addDivertConfiguration(new DivertConfiguration().setAddress(queueName).setForwardingAddress(divertedQueue).setExclusive(false).setName("divertOne"));
      serverB.getConfiguration().addDivertConfiguration(new DivertConfiguration().setAddress(queueName).setForwardingAddress(divertedQueue).setExclusive(false).setName("divertOne"));

      serverA.setIdentity("serverA");
      serverB.setIdentity("serverB");
      serverA.start();
      serverB.start();
      serverA.createQueue(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST));
      serverA.createQueue(QueueConfiguration.of(divertedQueue).setRoutingType(RoutingType.ANYCAST));
      try {
         serverB.createQueue(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST));
         serverB.createQueue(QueueConfiguration.of(divertedQueue).setRoutingType(RoutingType.ANYCAST));
      } catch (Exception ignored) {
      }

      Wait.assertTrue(() -> serverA.queueQuery(SimpleString.of(queueName)).isExists(), 5000, 100);
      Wait.assertTrue(() -> serverB.queueQuery(SimpleString.of(queueName)).isExists(), 5000, 100);
      Wait.assertTrue(() -> serverA.queueQuery(SimpleString.of(divertedQueue)).isExists(), 5000, 100);
      Wait.assertTrue(() -> serverB.queueQuery(SimpleString.of(divertedQueue)).isExists(), 5000, 100);

      Queue divertedQueueA = serverA.locateQueue(divertedQueue);
      Queue divertedQueueB = serverB.locateQueue(divertedQueue);
      Queue queueA = serverA.locateQueue(queueName);
      Queue queueB = serverB.locateQueue(queueName);

      long nmessages = 10;

      ConnectionFactory factoryA = CFUtil.createConnectionFactory(randomProtocol(), "tcp://localhost:5671");
      try (Connection connection = factoryA.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer producer = session.createProducer(session.createQueue(queueName));
         for (int i = 0; i < nmessages; i++) {
            producer.send(session.createTextMessage("hello " + i));
         }
         session.commit();
      }

      Wait.assertEquals(nmessages, divertedQueueA::getMessageCount, 5000, 100);
      Wait.assertEquals(nmessages, divertedQueueB::getMessageCount, 5000, 100);
      Wait.assertEquals(nmessages, queueA::getMessageCount, 5000, 100);
      Wait.assertEquals(nmessages, queueB::getMessageCount, 5000, 100);

      Queue serverAMirrorSNF = serverA.locateQueue("$ACTIVEMQ_ARTEMIS_MIRROR_A_to_B");
      Wait.assertEquals(0L, serverAMirrorSNF::getMessageCount, 5000, 100);

      TransportConfiguration newConfig = newAcceptorConfig(5680, "lateAcceptor");
      serverA.getRemotingService().createAcceptor(newConfig);
      serverA.getRemotingService().getAcceptor("lateAcceptor").start();

      Queue serverBMirrorSNF = serverB.locateQueue("$ACTIVEMQ_ARTEMIS_MIRROR_B_to_A");
      Wait.assertEquals(0L, serverBMirrorSNF::getMessageCount, 5000, 100);

      try (Connection connection = factoryA.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         MessageConsumer consumer = session.createConsumer(session.createQueue(queueName));
         connection.start();
         for (int i = 0; i < nmessages; i++) {
            TextMessage message = (TextMessage) consumer.receive(5000);
            assertNotNull(message);
            assertEquals("hello " + i, message.getText());
         }
         session.commit();
         assertNull(consumer.receiveNoWait());
         consumer.close();

         consumer = session.createConsumer(session.createQueue(divertedQueue));
         connection.start();
         for (int i = 0; i < nmessages; i++) {
            TextMessage message = (TextMessage) consumer.receive(5000);
            assertNotNull(message);
            assertEquals("hello " + i, message.getText());
         }
         session.commit();
         assertNull(consumer.receiveNoWait());
      }

      Wait.assertEquals(0L, divertedQueueA::getMessageCount, 5000, 100);
      Wait.assertEquals(0L, divertedQueueB::getMessageCount, 5000, 100);
      Wait.assertEquals(0L, queueA::getMessageCount, 5000, 100);
      Wait.assertEquals(0L, queueB::getMessageCount, 5000, 100);

   }

}