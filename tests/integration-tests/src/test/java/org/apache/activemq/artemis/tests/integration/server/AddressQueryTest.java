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
package org.apache.activemq.artemis.tests.integration.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.ConnectionFactory;
import javax.jms.JMSContext;
import java.util.UUID;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.AddressQueryResult;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AddressQueryTest extends ActiveMQTestBase {

   private ActiveMQServer server;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      server = createServer(false);
      server.start();
   }

   @Test
   public void testAddressQueryDefaultsOnStaticAddress() throws Exception {
      SimpleString addressName = SimpleString.of(UUID.randomUUID().toString());
      server.addAddressInfo(new AddressInfo(addressName));
      AddressQueryResult addressQueryResult = server.addressQuery(addressName);
      assertTrue(addressQueryResult.isExists());
      assertFalse(addressQueryResult.getRoutingTypes().contains(RoutingType.ANYCAST));
      assertFalse(addressQueryResult.getRoutingTypes().contains(RoutingType.MULTICAST));
      assertEquals(addressName, addressQueryResult.getName());
      assertTrue(addressQueryResult.isAutoCreateAddresses());
      assertEquals(-1, addressQueryResult.getDefaultMaxConsumers());
      assertFalse(addressQueryResult.isAutoCreated());
      assertFalse(addressQueryResult.isDefaultPurgeOnNoConsumers());
   }

   @Test
   public void testAddressQueryOnStaticAddressWithFQQN() throws Exception {
      SimpleString addressName = SimpleString.of(UUID.randomUUID().toString());
      SimpleString fqqn = addressName.concat("::").concat(SimpleString.of(UUID.randomUUID().toString()));
      server.addAddressInfo(new AddressInfo(fqqn));
      assertEquals(addressName, server.addressQuery(addressName).getName());
      assertEquals(addressName, server.addressQuery(fqqn).getName());
   }

   @Test
   public void testAddressQueryNonExistentAddress() throws Exception {
      SimpleString addressName = SimpleString.of(UUID.randomUUID().toString());
      AddressQueryResult addressQueryResult = server.addressQuery(addressName);
      assertFalse(addressQueryResult.isExists());
      assertEquals(addressName, addressQueryResult.getName());
   }

   @Test
   public void testAddressQueryNonExistentAddressWithFQQN() throws Exception {
      SimpleString addressName = SimpleString.of(UUID.randomUUID().toString());
      SimpleString fqqn = addressName.concat("::").concat(SimpleString.of(UUID.randomUUID().toString()));
      AddressQueryResult addressQueryResult = server.addressQuery(fqqn);
      assertFalse(addressQueryResult.isExists());
      assertEquals(addressName, addressQueryResult.getName());
   }

   @Test
   public void testAddressQueryNonDefaultsOnStaticAddress() throws Exception {
      SimpleString addressName = SimpleString.of(UUID.randomUUID().toString());
      server.getAddressSettingsRepository().addMatch(addressName.toString(), new AddressSettings().setAutoCreateAddresses(false).setDefaultMaxConsumers(1).setDefaultPurgeOnNoConsumers(true));
      server.addAddressInfo(new AddressInfo(addressName).addRoutingType(RoutingType.ANYCAST));
      AddressQueryResult addressQueryResult = server.addressQuery(addressName);
      assertTrue(addressQueryResult.isExists());
      assertTrue(addressQueryResult.getRoutingTypes().contains(RoutingType.ANYCAST));
      assertFalse(addressQueryResult.getRoutingTypes().contains(RoutingType.MULTICAST));
      assertEquals(addressName, addressQueryResult.getName());
      assertFalse(addressQueryResult.isAutoCreateAddresses());
      assertEquals(1, addressQueryResult.getDefaultMaxConsumers());
      assertFalse(addressQueryResult.isAutoCreated());
      assertTrue(addressQueryResult.isDefaultPurgeOnNoConsumers());
   }

   @Test
   public void testAddressQueryDefaultsOnAutoCreatedAddress() throws Exception {
      SimpleString addressName = SimpleString.of(UUID.randomUUID().toString());
      server.getAddressSettingsRepository().addMatch(addressName.toString(), new AddressSettings());
      ConnectionFactory cf = new ActiveMQConnectionFactory("vm://0");
      JMSContext c = cf.createContext();
      c.createProducer().send(c.createTopic(addressName.toString()), c.createMessage());
      AddressQueryResult addressQueryResult = server.addressQuery(addressName);
      assertTrue(addressQueryResult.isExists());
      assertFalse(addressQueryResult.getRoutingTypes().contains(RoutingType.ANYCAST));
      assertTrue(addressQueryResult.getRoutingTypes().contains(RoutingType.MULTICAST));
      assertEquals(addressName, addressQueryResult.getName());
      assertTrue(addressQueryResult.isAutoCreateAddresses());
      assertEquals(-1, addressQueryResult.getDefaultMaxConsumers());
      assertTrue(addressQueryResult.isAutoCreated());
      assertFalse(addressQueryResult.isDefaultPurgeOnNoConsumers());
   }

   @Test
   public void testAddressQueryOnAutoCreatedAddressWithFQQN() throws Exception {
      SimpleString addressName = SimpleString.of(UUID.randomUUID().toString());
      SimpleString fqqn = addressName.concat("::").concat(SimpleString.of(UUID.randomUUID().toString()));
      ConnectionFactory cf = new ActiveMQConnectionFactory("vm://0");
      JMSContext c = cf.createContext();
      c.createProducer().send(c.createTopic(fqqn.toString()), c.createMessage());
      assertEquals(addressName, server.addressQuery(addressName).getName());
      assertEquals(addressName, server.addressQuery(fqqn).getName());
   }

   @Test
   public void testAddressQueryNonDefaultsOnAutoCreatedAddress() throws Exception {
      SimpleString addressName = SimpleString.of(UUID.randomUUID().toString());
      server.getAddressSettingsRepository().addMatch(addressName.toString(), new AddressSettings().setAutoCreateAddresses(true).setDefaultMaxConsumers(1).setDefaultPurgeOnNoConsumers(true));
      ConnectionFactory cf = new ActiveMQConnectionFactory("vm://0");
      JMSContext c = cf.createContext();
      c.createProducer().send(c.createTopic(addressName.toString()), c.createMessage());
      AddressQueryResult addressQueryResult = server.addressQuery(addressName);
      assertTrue(addressQueryResult.isExists());
      assertTrue(addressQueryResult.getRoutingTypes().contains(RoutingType.MULTICAST));
      assertEquals(addressName, addressQueryResult.getName());
      assertTrue(addressQueryResult.isAutoCreateAddresses());
      assertEquals(1, addressQueryResult.getDefaultMaxConsumers());
      assertTrue(addressQueryResult.isAutoCreated());
      assertTrue(addressQueryResult.isDefaultPurgeOnNoConsumers());
   }
}
