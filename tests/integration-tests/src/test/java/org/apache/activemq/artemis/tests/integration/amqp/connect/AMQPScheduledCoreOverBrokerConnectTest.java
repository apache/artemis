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

import static org.junit.jupiter.api.Assertions.assertNull;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.CoreAddressConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectionAddressType;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPMirrorBrokerConnectionElement;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.tests.integration.amqp.AmqpClientTestSupport;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.Test;

public class AMQPScheduledCoreOverBrokerConnectTest extends AmqpClientTestSupport {

   protected static final int AMQP_PORT_2 = 5673;

   ActiveMQServer server_2;

   @Override
   protected ActiveMQServer createServer() throws Exception {
      return createServer(AMQP_PORT, false);
   }

   @Override
   protected String getConfiguredProtocols() {
      return "AMQP,OPENWIRE,CORE";
   }

   @Test
   public void testWithDeliveryDelayCoreSendingConversion() throws Exception {
      String queueName = "withScheduled";
      server.setIdentity("targetServer");
      server.start();
      server.addAddressInfo(new AddressInfo(SimpleString.of(queueName), RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST));

      server_2 = createServer(AMQP_PORT_2, false);

      AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration("test", "tcp://localhost:" + AMQP_PORT);
      amqpConnection.addElement(new AMQPMirrorBrokerConnectionElement().setType(AMQPBrokerConnectionAddressType.MIRROR));
      server_2.getConfiguration().addAMQPConnection(amqpConnection);
      server_2.getConfiguration().addAddressConfiguration(new CoreAddressConfiguration().setName(queueName).addRoutingType(RoutingType.ANYCAST));
      server_2.getConfiguration().addQueueConfiguration(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST));
      server_2.setIdentity("serverWithBridge");

      server_2.start();
      Wait.assertTrue(server_2::isStarted);

      ConnectionFactory factory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:" + AMQP_PORT_2);
      Connection connection = factory.createConnection();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageProducer producer = session.createProducer(session.createQueue(queueName));
      producer.setDeliveryMode(DeliveryMode.PERSISTENT);
      producer.setDeliveryDelay(300_000);
      producer.send(session.createMessage());

      ConnectionFactory factory2 = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);
      Connection connection2 = factory2.createConnection();
      Session session2 = connection2.createSession(false, Session.AUTO_ACKNOWLEDGE);
      connection2.start();

      MessageConsumer consumer = session2.createConsumer(session2.createQueue(queueName));
      assertNull(consumer.receive(500));
   }
}
