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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.JMSContext;
import java.util.UUID;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.QueueQueryResult;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class QueueQueryTest extends ActiveMQTestBase {

   private ActiveMQServer server;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      server = createServer(false);
      server.start();
   }

   @Test
   public void testQueueQueryOnManagement() throws Exception {
      SimpleString addressName = server.getConfiguration().getManagementAddress();
      SimpleString queueName = server.getConfiguration().getManagementAddress();
      QueueQueryResult queueQueryResult = server.queueQuery(queueName);
      assertTrue(queueQueryResult.isExists());
      assertEquals(RoutingType.MULTICAST, queueQueryResult.getRoutingType());
      assertEquals(queueName, queueQueryResult.getName());
      assertTrue(queueQueryResult.isAutoCreateQueues());
      assertNull(queueQueryResult.getFilterString());
      assertFalse(queueQueryResult.isAutoCreated());
      assertEquals(addressName, queueQueryResult.getAddress());
      assertEquals(-1, queueQueryResult.getMessageCount());
      assertEquals(-1, queueQueryResult.getConsumerCount());
      assertEquals(ActiveMQDefaultConfiguration.DEFAULT_MAX_QUEUE_CONSUMERS, queueQueryResult.getMaxConsumers());
      assertEquals(ActiveMQDefaultConfiguration.DEFAULT_CONSUMERS_BEFORE_DISPATCH, queueQueryResult.getConsumersBeforeDispatch().intValue());
      assertEquals(ActiveMQClient.DEFAULT_CONSUMER_WINDOW_SIZE, queueQueryResult.getDefaultConsumerWindowSize().intValue());
      assertEquals(ActiveMQDefaultConfiguration.DEFAULT_DELAY_BEFORE_DISPATCH, queueQueryResult.getDelayBeforeDispatch().longValue());
      assertNull(queueQueryResult.getLastValueKey());
      assertTrue(queueQueryResult.isDurable());
      assertFalse(queueQueryResult.isPurgeOnNoConsumers());
      assertFalse(queueQueryResult.isTemporary());
      assertFalse(queueQueryResult.isExclusive());
      assertFalse(queueQueryResult.isNonDestructive());
   }

   @Test
   public void testQueueQueryDefaultsOnStaticQueue() throws Exception {
      SimpleString addressName = SimpleString.of(UUID.randomUUID().toString());
      SimpleString queueName = SimpleString.of(UUID.randomUUID().toString());
      server.createQueue(QueueConfiguration.of(queueName).setAddress(addressName));
      QueueQueryResult queueQueryResult = server.queueQuery(queueName);
      assertTrue(queueQueryResult.isExists());
      assertEquals(RoutingType.MULTICAST, queueQueryResult.getRoutingType());
      assertEquals(queueName, queueQueryResult.getName());
      assertTrue(queueQueryResult.isAutoCreateQueues());
      assertNull(queueQueryResult.getFilterString());
      assertFalse(queueQueryResult.isAutoCreated());
      assertEquals(addressName, queueQueryResult.getAddress());
      assertEquals(0, queueQueryResult.getMessageCount());
      assertEquals(0, queueQueryResult.getConsumerCount());
      assertEquals(ActiveMQDefaultConfiguration.DEFAULT_MAX_QUEUE_CONSUMERS, queueQueryResult.getMaxConsumers());
      assertEquals(ActiveMQDefaultConfiguration.DEFAULT_CONSUMERS_BEFORE_DISPATCH, queueQueryResult.getConsumersBeforeDispatch().intValue());
      assertEquals(ActiveMQClient.DEFAULT_CONSUMER_WINDOW_SIZE, queueQueryResult.getDefaultConsumerWindowSize().intValue());
      assertEquals(ActiveMQDefaultConfiguration.DEFAULT_DELAY_BEFORE_DISPATCH, queueQueryResult.getDelayBeforeDispatch().longValue());
      assertNull(queueQueryResult.getLastValueKey());
      assertTrue(queueQueryResult.isDurable());
      assertFalse(queueQueryResult.isPurgeOnNoConsumers());
      assertFalse(queueQueryResult.isTemporary());
      assertFalse(queueQueryResult.isExclusive());
      assertFalse(queueQueryResult.isNonDestructive());
   }

   @Test
   public void testQueueQueryOnStaticQueueWithFQQN() throws Exception {
//      SimpleString addressName = SimpleString.of(UUID.randomUUID().toString());
//      SimpleString queueName = SimpleString.of(UUID.randomUUID().toString());
      SimpleString addressName = SimpleString.of("myAddress");
      SimpleString queueName = SimpleString.of("myQueue");
      SimpleString fqqn = addressName.concat("::").concat(queueName);
//      server.createQueue(fqqn, RoutingType.MULTICAST, fqqn, null, true, false);
      server.createQueue(QueueConfiguration.of(fqqn));
      QueueQueryResult queueQueryResult = server.queueQuery(fqqn);
      assertEquals(queueName, queueQueryResult.getName());
      assertEquals(addressName, queueQueryResult.getAddress());
      queueQueryResult = server.queueQuery(queueName);
      assertEquals(queueName, queueQueryResult.getName());
      assertEquals(addressName, queueQueryResult.getAddress());
   }

   @Test
   public void testQueueQueryNonDefaultsOnStaticQueue() throws Exception {
      SimpleString addressName = SimpleString.of(UUID.randomUUID().toString());
      SimpleString queueName = SimpleString.of(UUID.randomUUID().toString());
      SimpleString filter = SimpleString.of("x = 'y'");
      SimpleString lastValueKey = SimpleString.of("myLastValueKey");
      server.getAddressSettingsRepository().addMatch(addressName.toString(), new AddressSettings()
         .setAutoCreateAddresses(true)
         .setDefaultMaxConsumers(1)
         .setDefaultPurgeOnNoConsumers(true)
         .setDefaultConsumersBeforeDispatch(13)
         .setDefaultConsumerWindowSize(51)
         .setDefaultDelayBeforeDispatch(19L)
         .setDefaultLastValueQueue(true)
         .setDefaultLastValueKey(lastValueKey)
         .setDefaultExclusiveQueue(true)
         .setDefaultNonDestructive(true));
      server.createQueue(QueueConfiguration.of(queueName).setAddress(addressName).setRoutingType(RoutingType.ANYCAST).setFilterString(filter).setDurable(false).setTemporary(true));
      QueueQueryResult queueQueryResult = server.queueQuery(queueName);
      assertTrue(queueQueryResult.isExists());
      assertEquals(RoutingType.ANYCAST, queueQueryResult.getRoutingType());
      assertEquals(queueName, queueQueryResult.getName());
      assertTrue(queueQueryResult.isAutoCreateQueues());
      assertEquals(filter, queueQueryResult.getFilterString());
      assertFalse(queueQueryResult.isAutoCreated());
      assertEquals(addressName, queueQueryResult.getAddress());
      assertEquals(0, queueQueryResult.getMessageCount());
      assertEquals(0, queueQueryResult.getConsumerCount());
      assertEquals(1, queueQueryResult.getMaxConsumers());
      assertEquals(13, queueQueryResult.getConsumersBeforeDispatch().intValue());
      assertEquals(51, queueQueryResult.getDefaultConsumerWindowSize().intValue());
      assertEquals(19L, queueQueryResult.getDelayBeforeDispatch().longValue());
      assertTrue(queueQueryResult.isLastValue());
      assertEquals(lastValueKey, queueQueryResult.getLastValueKey());
      assertFalse(queueQueryResult.isDurable());
      assertTrue(queueQueryResult.isPurgeOnNoConsumers());
      assertTrue(queueQueryResult.isTemporary());
      assertTrue(queueQueryResult.isExclusive());
      assertTrue(queueQueryResult.isNonDestructive());
   }

   @Test
   public void testQueueQueryDefaultsOnAutoCreatedQueue() throws Exception {
      SimpleString queueName = SimpleString.of(UUID.randomUUID().toString());
      server.getAddressSettingsRepository().addMatch(queueName.toString(), new AddressSettings());
      JMSContext c = new ActiveMQConnectionFactory("vm://0").createContext();
      c.createProducer().send(c.createQueue(queueName.toString()), c.createMessage());
      Wait.assertEquals(1, server.locateQueue(queueName)::getMessageCount);
      QueueQueryResult queueQueryResult = server.queueQuery(queueName);
      assertTrue(queueQueryResult.isAutoCreateQueues());
      assertNull(queueQueryResult.getFilterString());
      assertTrue(queueQueryResult.isAutoCreated());
      assertEquals(queueName, queueQueryResult.getAddress());
      assertEquals(1, queueQueryResult.getMessageCount());
      assertEquals(0, queueQueryResult.getConsumerCount());
      assertEquals(ActiveMQDefaultConfiguration.DEFAULT_MAX_QUEUE_CONSUMERS, queueQueryResult.getMaxConsumers());
      assertEquals(ActiveMQDefaultConfiguration.DEFAULT_CONSUMERS_BEFORE_DISPATCH, queueQueryResult.getConsumersBeforeDispatch().intValue());
      assertEquals(ActiveMQClient.DEFAULT_CONSUMER_WINDOW_SIZE, queueQueryResult.getDefaultConsumerWindowSize().intValue());
      assertEquals(ActiveMQDefaultConfiguration.DEFAULT_DELAY_BEFORE_DISPATCH, queueQueryResult.getDelayBeforeDispatch().longValue());
      assertNull(queueQueryResult.getLastValueKey());
      assertTrue(queueQueryResult.isDurable());
      assertFalse(queueQueryResult.isPurgeOnNoConsumers());
      assertFalse(queueQueryResult.isTemporary());
      assertFalse(queueQueryResult.isExclusive());
      assertFalse(queueQueryResult.isNonDestructive());
   }

   @Test
   public void testQueueQueryOnAutoCreatedQueueWithFQQN() throws Exception {
      SimpleString addressName = SimpleString.of(UUID.randomUUID().toString());
      SimpleString queueName = SimpleString.of(UUID.randomUUID().toString());
      SimpleString fqqn = addressName.concat("::").concat(queueName);
      try (JMSContext c = new ActiveMQConnectionFactory("vm://0").createContext()) {
         c.createProducer().send(c.createQueue(fqqn.toString()), c.createMessage());
         QueueQueryResult queueQueryResult = server.queueQuery(fqqn);
         assertEquals(queueName, queueQueryResult.getName());
         assertEquals(addressName, queueQueryResult.getAddress());
         Wait.assertEquals(1, () -> server.queueQuery(fqqn).getMessageCount());
         queueQueryResult = server.queueQuery(queueName);
         assertEquals(queueName, queueQueryResult.getName());
         assertEquals(addressName, queueQueryResult.getAddress());
         assertEquals(1, queueQueryResult.getMessageCount());
         c.createProducer().send(c.createQueue(addressName.toString()), c.createMessage());
         Wait.assertEquals(2, () -> server.queueQuery(fqqn).getMessageCount());
         Wait.assertEquals(2, () -> server.queueQuery(queueName).getMessageCount());
      }
   }

   @Test
   public void testQueueQueryNonDefaultsOnAutoCreatedQueue() throws Exception {
      SimpleString queueName = SimpleString.of(UUID.randomUUID().toString());
      SimpleString lastValueKey = SimpleString.of("myLastValueKey");
      server.getAddressSettingsRepository().addMatch(queueName.toString(), new AddressSettings()
         .setAutoCreateAddresses(true)
         .setAutoCreateQueues(true)
         .setDefaultMaxConsumers(1)
         .setDefaultPurgeOnNoConsumers(true)
         .setDefaultConsumersBeforeDispatch(13)
         .setDefaultConsumerWindowSize(51)
         .setDefaultDelayBeforeDispatch(19L)
         .setDefaultLastValueQueue(true)
         .setDefaultLastValueKey(lastValueKey)
         .setDefaultExclusiveQueue(true)
         .setDefaultNonDestructive(true));
      JMSContext c = new ActiveMQConnectionFactory("vm://0").createContext();
      c.createProducer().send(c.createQueue(queueName.toString()), c.createMessage());
      QueueQueryResult queueQueryResult = server.queueQuery(queueName);
      assertTrue(queueQueryResult.isExists());
      assertEquals(RoutingType.ANYCAST, queueQueryResult.getRoutingType());
      assertEquals(queueName, queueQueryResult.getName());
      assertTrue(queueQueryResult.isAutoCreateQueues());
      assertNull(queueQueryResult.getFilterString());
      assertTrue(queueQueryResult.isAutoCreated());
      assertEquals(queueName, queueQueryResult.getAddress());
      assertEquals(0, queueQueryResult.getMessageCount()); // 0 since purgeOnNoConsumers = true
      assertEquals(0, queueQueryResult.getConsumerCount());
      assertEquals(1, queueQueryResult.getMaxConsumers());
      assertEquals(13, queueQueryResult.getConsumersBeforeDispatch().intValue());
      assertEquals(51, queueQueryResult.getDefaultConsumerWindowSize().intValue());
      assertEquals(19L, queueQueryResult.getDelayBeforeDispatch().longValue());
      assertTrue(queueQueryResult.isLastValue());
      assertEquals(lastValueKey, queueQueryResult.getLastValueKey());
      assertTrue(queueQueryResult.isDurable());
      assertTrue(queueQueryResult.isPurgeOnNoConsumers());
      assertFalse(queueQueryResult.isTemporary());
      assertTrue(queueQueryResult.isExclusive());
      assertTrue(queueQueryResult.isNonDestructive());
   }

   @Test
   public void testQueueQueryNonExistentQueue() throws Exception {
      SimpleString queueName = SimpleString.of(UUID.randomUUID().toString());
      QueueQueryResult queueQueryResult = server.queueQuery(queueName);
      assertFalse(queueQueryResult.isExists());
      assertEquals(queueName, queueQueryResult.getName());
   }

   @Test
   public void testQueueQueryNonExistentQueueWithFQQN() throws Exception {
      SimpleString addressName = SimpleString.of(UUID.randomUUID().toString());
      SimpleString queueName = SimpleString.of(UUID.randomUUID().toString());
      SimpleString fqqn = addressName.concat("::").concat(queueName);
      QueueQueryResult queueQueryResult = server.queueQuery(fqqn);
      assertFalse(queueQueryResult.isExists());
      assertEquals(queueName, queueQueryResult.getName());
      assertEquals(addressName, queueQueryResult.getAddress());
   }
}
