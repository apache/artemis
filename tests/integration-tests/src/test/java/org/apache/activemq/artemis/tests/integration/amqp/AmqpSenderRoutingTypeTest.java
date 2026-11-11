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
package org.apache.activemq.artemis.tests.integration.amqp;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.client.ActiveMQConnection;
import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpSender;
import org.apache.activemq.transport.amqp.client.AmqpSession;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;

public class AmqpSenderRoutingTypeTest extends JMSClientTestSupport {

   @Override
   protected void configureAddressPolicy(ActiveMQServer server) {
      Configuration serverConfig = server.getConfiguration();
      serverConfig.setJournalType(JournalType.NIO);
      Map<String, AddressSettings> map = serverConfig.getAddressSettings();
      if (map.isEmpty()) {
         AddressSettings as = new AddressSettings();
         as.setDefaultAddressRoutingType(RoutingType.ANYCAST);
         map.put("#", as);
      }
   }

   @Override
   protected String getConfiguredProtocols() {
      return "AMQP,CORE";
   }

   @Test
   public void testAMQPSenderHonourRoutingTypeOfExistingAddress() throws Exception {
      RoutingType routingType = server.getConfiguration().getAddressSettings().get("#").getDefaultAddressRoutingType();
      assertEquals(RoutingType.ANYCAST, routingType);
      try (ActiveMQConnection coreConnection = (ActiveMQConnection) createCoreConnection();
           ClientSession clientSession = coreConnection.getSessionFactory().createSession()) {
         RoutingType addressRoutingType = RoutingType.MULTICAST;
         SimpleString address = SimpleString.of("myTopic_" + UUID.randomUUID().toString());
         clientSession.createAddress(address, addressRoutingType, false);
         ClientSession.AddressQuery addressQuery = clientSession.addressQuery(address);
         assertTrue(addressQuery.isExists());
         assertTrue(addressQuery.getQueueNames().isEmpty());
         AmqpClient client = createAmqpClient(guestPass, guestUser);
         AmqpConnection connection = addConnection(client.connect());
         AmqpSession session = connection.createSession();
         AmqpSender sender = session.createSender(address.toString());
         try {
            ClientSession.QueueQuery queueQuery = clientSession.queueQuery(address);
            assertFalse(queueQuery.isExists());
            assertEquals(addressRoutingType, queueQuery.getRoutingType());
         } finally {
            sender.close();
            session.close();
            connection.close();
         }
      }

   }

   @Test
   public void testAMQPSenderCreateQueueWithDefaultRoutingTypeIfAddressDoNotExist() throws Exception {
      RoutingType defaultRoutingType = server.getConfiguration().getAddressSettings().get("#").getDefaultAddressRoutingType();
      assertEquals(RoutingType.ANYCAST, defaultRoutingType);
      try (ActiveMQConnection coreConnection = (ActiveMQConnection) createCoreConnection();
           ClientSession clientSession = coreConnection.getSessionFactory().createSession()) {
         SimpleString address = SimpleString.of("myTopic_" + UUID.randomUUID().toString());
         ClientSession.AddressQuery addressQuery = clientSession.addressQuery(address);
         assertFalse(addressQuery.isExists());
         assertTrue(addressQuery.getQueueNames().isEmpty());
         AmqpClient client = createAmqpClient(guestPass, guestUser);
         AmqpConnection connection = addConnection(client.connect());
         AmqpSession session = connection.createSession();
         AmqpSender sender = session.createSender(address.toString());
         try {
            addressQuery = clientSession.addressQuery(address);
            assertTrue(addressQuery.isExists());
            assertThat(addressQuery.getQueueNames(), CoreMatchers.hasItem(address));
            ClientSession.QueueQuery queueQuery = clientSession.queueQuery(address);
            assertTrue(queueQuery.isExists());
            assertEquals(defaultRoutingType, queueQuery.getRoutingType());
         } finally {
            sender.close();
            session.close();
            connection.close();
         }
      }

   }
}
