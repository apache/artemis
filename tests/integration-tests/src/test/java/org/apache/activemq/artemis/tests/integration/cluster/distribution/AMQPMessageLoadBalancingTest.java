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

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import javax.jms.Connection;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.protocol.amqp.broker.ProtonProtocolManagerFactory;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AMQPMessageLoadBalancingTest extends ClusterTestBase {

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
   public void testLoadBalanceAMQP() throws Exception {
      setupCluster(MessageLoadBalancingType.STRICT);

      startServers(0, 1);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());

      createQueue(0, "queues.0", "queues.0", null, true, null, null, RoutingType.ANYCAST);
      createQueue(1, "queues.0", "queues.0", null, true, null, null, RoutingType.ANYCAST);

      waitForBindings(0, "queues.0", 1, 0, true);
      waitForBindings(1, "queues.0", 1, 0, true);

      waitForBindings(0, "queues.0", 1, 0, false);
      waitForBindings(1, "queues.0", 1, 0, false);


      final int NUMBER_OF_MESSAGES = 100;

      // sending AMQP Messages.. they should be load balanced
      {
         JmsConnectionFactory factory = new JmsConnectionFactory("amqp://localhost:61616");
         Connection connection = factory.createConnection();
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer producer = session.createProducer(session.createQueue("queues.0"));

         for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            producer.send(session.createTextMessage("hello " + i));
         }
         session.commit();

         connection.close();
      }

      receiveOnBothNodes(NUMBER_OF_MESSAGES);

      // If a user used a message type = 7, for messages that are not embedded,
      // it should still be treated as a normal message
      {
         ClientSession sessionProducer = sfs[0].createSession();
         ClientProducer producer = sessionProducer.createProducer("queues.0");

         for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            // The user is mistakenly using the same type we used for embedded messages. it should still work
            ClientMessage message = sessionProducer.createMessage(Message.EMBEDDED_TYPE, true).putIntProperty("i", i);
            message.getBodyBuffer().writeString("hello!");
            producer.send(message);

            // will send 2 messages.. one with stuff, another empty
            message = sessionProducer.createMessage(Message.EMBEDDED_TYPE, true);
            producer.send(message);
         }
         receiveOnBothNodes(NUMBER_OF_MESSAGES * 2);
      }

   }

   private void receiveOnBothNodes(int NUMBER_OF_MESSAGES) throws ActiveMQException {
      for (int x = 0; x <= 1; x++) {
         ClientSession sessionX = sfs[x].createSession();
         ClientConsumer consumerX = sessionX.createConsumer("queues.0");
         sessionX.start();

         for (int i = 0; i < NUMBER_OF_MESSAGES / 2; i++) {
            ClientMessage msg = consumerX.receive(5000);
            assertNotNull(msg);
            msg.acknowledge();
         }
         assertNull(consumerX.receiveImmediate());
         sessionX.commit();
         sessionX.close();
      }
   }

   protected void setupCluster(final MessageLoadBalancingType messageLoadBalancingType) throws Exception {
      setupClusterConnection("cluster0", "queues", messageLoadBalancingType, 1, isNetty(), 0, 1);

      setupClusterConnection("cluster1", "queues", messageLoadBalancingType, 1, isNetty(), 1, 0);
   }

   protected void setRedistributionDelay(final long delay) {
      AddressSettings as = new AddressSettings().setRedistributionDelay(delay);

      getServer(0).getAddressSettingsRepository().addMatch("queues.*", as);
      getServer(1).getAddressSettingsRepository().addMatch("queues.*", as);
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
