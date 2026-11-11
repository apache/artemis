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
import javax.jms.Session;
import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectionAddressType;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPMirrorBrokerConnectionElement;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class AMQPMirrorExpiryQueueTest extends ActiveMQTestBase {

   private static final String EXPIRY_QUEUE = AMQPMirrorExpiryQueueTest.class.getName() + "_ExpiryOut";

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
      server.getConfiguration().setMessageExpiryScanPeriod(5);

      server.getConfiguration().addAddressSetting("#", new AddressSettings().setExpiryAddress(SimpleString.of(EXPIRY_QUEUE)));
      return server;
   }

   @Test
   public void testExpiryOnMirrorSNF() throws Exception {

      final long numberOfMessages = 100;
      ActiveMQServer serverA = createServer(5671, getTestMethodName() + "_A");

      {
         AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration(getTestMethodName() + "_willNeverConnect", "tcp://localhost:6671").setReconnectAttempts(1).setRetryInterval(10);
         AMQPMirrorBrokerConnectionElement replica = new AMQPMirrorBrokerConnectionElement().setType(AMQPBrokerConnectionAddressType.MIRROR).setDurable(true);
         amqpConnection.addElement(replica);
         serverA.getConfiguration().addAMQPConnection(amqpConnection);
      }

      String queueName = getTestMethodName() + "_" + RandomUtil.randomUUIDString();

      serverA.setIdentity(getTestMethodName() + "_A");
      serverA.start();

      serverA.createQueue(QueueConfiguration.of(queueName).setName(queueName).setRoutingType(RoutingType.ANYCAST));
      Queue expiryA = serverA.createQueue(QueueConfiguration.of(EXPIRY_QUEUE).setName(EXPIRY_QUEUE).setRoutingType(RoutingType.ANYCAST));
      Queue snfQueue = serverA.locateQueue(QueueConfiguration.MIRROR_ADDRESS + "_" + getTestMethodName() + "_willNeverConnect");
      assertNotNull(snfQueue);
      assertNotNull(expiryA);

      ConnectionFactory factoryA = CFUtil.createConnectionFactory("CORE", "tcp://localhost:5671");
      try (Connection connection = factoryA.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer producer = session.createProducer(session.createQueue(queueName));
         producer.setTimeToLive(1);

         for (int i = 0; i < numberOfMessages; i++) {
            producer.send(session.createTextMessage("hello" + i));
         }

         session.commit();
      }

      Thread.sleep(10); // waiting a little more than the expiry scan period
      Wait.assertEquals(numberOfMessages, expiryA::getMessageCount, 5000, 100);

      // We should still have the message sends and the acks from the expired messages in the SNF
      Wait.assertTrue(() -> snfQueue.getMessageCount() >= (numberOfMessages * 2), 5000, 100);
   }

}