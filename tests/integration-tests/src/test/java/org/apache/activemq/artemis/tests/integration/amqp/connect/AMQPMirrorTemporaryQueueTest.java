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
import javax.jms.Session;
import javax.jms.TemporaryQueue;
import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.CoreAddressConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectionAddressType;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPMirrorBrokerConnectionElement;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.jms.client.ActiveMQMessageConsumer;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

public class AMQPMirrorTemporaryQueueTest extends ActiveMQTestBase {

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
   public void testTemporaryOnMirror() throws Exception {
      ActiveMQServer serverA = createServer(5671, "serverA");
      ActiveMQServer serverB = createServer(6671, "serverB");

      {
         AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration("A_to_B", "tcp://localhost:6671").setReconnectAttempts(-1).setRetryInterval(10);
         AMQPMirrorBrokerConnectionElement replica1 = new AMQPMirrorBrokerConnectionElement().setType(AMQPBrokerConnectionAddressType.MIRROR).setDurable(true);
         amqpConnection.addElement(replica1);
         serverA.getConfiguration().addAMQPConnection(amqpConnection);
      }

      {
         AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration("B_to_A", "tcp://localhost:5671").setReconnectAttempts(-1).setRetryInterval(10);
         AMQPMirrorBrokerConnectionElement replica1 = new AMQPMirrorBrokerConnectionElement().setType(AMQPBrokerConnectionAddressType.MIRROR).setDurable(true);
         amqpConnection.addElement(replica1);
         serverB.getConfiguration().addAMQPConnection(amqpConnection);
      }

      String topicName = "topic" + RandomUtil.randomUUIDString();

      serverA.getConfiguration().addAddressConfiguration(new CoreAddressConfiguration().setName(topicName).addRoutingType(RoutingType.ANYCAST));
      serverB.getConfiguration().addAddressConfiguration(new CoreAddressConfiguration().setName(topicName).addRoutingType(RoutingType.ANYCAST));
      serverA.setIdentity("serverA");
      serverB.setIdentity("serverB");
      serverA.start();
      serverB.start();

      Queue serverAMirrorSNF = serverA.locateQueue("$ACTIVEMQ_ARTEMIS_MIRROR_A_to_B");
      Queue serverBMirrorSNF = serverB.locateQueue("$ACTIVEMQ_ARTEMIS_MIRROR_B_to_A");

      ConnectionFactory factoryA = CFUtil.createConnectionFactory("CORE", "tcp://localhost:5671");
      try (Connection connection = factoryA.createConnection()) {
         connection.setClientID("clientID");
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         ActiveMQMessageConsumer consumer = (ActiveMQMessageConsumer) session.createConsumer(session.createTopic(topicName));

         SimpleString temporaryQueueNameOnSubscribe = consumer.getAutoDeleteQueueName();

         TemporaryQueue temporaryQueue = session.createTemporaryQueue();

         Wait.assertEquals(0L, serverAMirrorSNF::getMessageCount, 5000, 100);
         Wait.assertEquals(0L, serverBMirrorSNF::getMessageCount, 5000, 100);

         assertNull(serverB.locateQueue(temporaryQueueNameOnSubscribe));
         assertNull(serverB.locateQueue(temporaryQueue.getQueueName()));
      }

      // TODO: We could make the server to ignore sends on temporary queues as well, and we should add an assertion here
      // https://issues.apache.org/jira/browse/ARTEMIS-5069
   }

}