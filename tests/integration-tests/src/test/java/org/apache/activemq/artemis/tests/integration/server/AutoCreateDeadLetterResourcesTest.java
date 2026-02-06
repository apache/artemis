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
package org.apache.activemq.artemis.tests.integration.server;

import javax.jms.JMSContext;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.core.config.DivertConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.CompositeAddress;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class AutoCreateDeadLetterResourcesTest extends ActiveMQTestBase {
   public final SimpleString addressA = SimpleString.of("addressA");
   public final SimpleString queueA = SimpleString.of("queueA");
   public final SimpleString dla = SimpleString.of("myDLA");

   private ActiveMQServer server;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      server = createServer(false);
      server.getConfiguration().setAddressQueueScanPeriod(100);

      // set common address settings needed for all tests; make sure to use getMatch instead of addMatch in invidual tests or these will be overwritten
      server.getAddressSettingsRepository().addMatch("#", new AddressSettings().setAutoCreateDeadLetterResources(true).setDeadLetterAddress(dla).setMaxDeliveryAttempts(1));

      server.start();
   }

   @Test
   public void testAutoCreationOfDeadLetterResources() throws Exception {
      int before = server.getActiveMQServerControl().getQueueNames().length;
      triggerDlaDelivery();
      assertNotNull(server.getAddressInfo(dla));
      assertNotNull(server.locateQueue(AddressSettings.DEFAULT_DEAD_LETTER_QUEUE_PREFIX.concat(addressA).concat(AddressSettings.DEFAULT_DEAD_LETTER_QUEUE_SUFFIX)));
      assertEquals(2, server.getActiveMQServerControl().getQueueNames().length - before);
   }

   @Test
   public void testAutoCreationOfDeadLetterResourcesWithNullDLA() throws Exception {
      testAutoCreationOfDeadLetterResourcesWithNoDLA(null);
   }

   @Test
   public void testAutoCreationOfDeadLetterResourcesWithEmptyDLA() throws Exception {
      testAutoCreationOfDeadLetterResourcesWithNoDLA(SimpleString.of(""));
   }

   private void testAutoCreationOfDeadLetterResourcesWithNoDLA(SimpleString dla) throws Exception {
      server.getAddressSettingsRepository().getMatch("#").setDeadLetterAddress(dla);
      int before = server.getActiveMQServerControl().getQueueNames().length;
      triggerDlaDelivery();
      if (dla != null) {
         assertNull(server.getAddressInfo(dla));
      }
      assertNull(server.locateQueue(AddressSettings.DEFAULT_DEAD_LETTER_QUEUE_PREFIX.concat(addressA).concat(AddressSettings.DEFAULT_DEAD_LETTER_QUEUE_SUFFIX)));
      assertEquals(1, server.getActiveMQServerControl().getQueueNames().length - before);
   }

   @Test
   public void testAutoCreateDeadLetterQueuePrefix() throws Exception {
      SimpleString prefix = RandomUtil.randomUUIDSimpleString();
      server.getAddressSettingsRepository().getMatch("#").setDeadLetterQueuePrefix(prefix);
      triggerDlaDelivery();
      assertNotNull(server.locateQueue(prefix.concat(addressA).concat(AddressSettings.DEFAULT_DEAD_LETTER_QUEUE_SUFFIX)));
   }

   @Test
   public void testAutoCreateDeadLetterQueueSuffix() throws Exception {
      SimpleString suffix = RandomUtil.randomUUIDSimpleString();
      server.getAddressSettingsRepository().getMatch("#").setDeadLetterQueueSuffix(suffix);
      triggerDlaDelivery();
      assertNotNull(server.locateQueue(AddressSettings.DEFAULT_DEAD_LETTER_QUEUE_PREFIX.concat(addressA).concat(suffix)));
   }

   @Test
   public void testAutoCreateDeadLetterQueuePrefixAndSuffix() throws Exception {
      SimpleString prefix = RandomUtil.randomUUIDSimpleString();
      SimpleString suffix = RandomUtil.randomUUIDSimpleString();
      server.getAddressSettingsRepository().getMatch("#").setDeadLetterQueuePrefix(prefix).setDeadLetterQueueSuffix(suffix);
      triggerDlaDelivery();
      assertNotNull(server.locateQueue(prefix.concat(addressA).concat(suffix)));
   }

   @Test
   public void testAutoCreatedDeadLetterFilterAnycast() throws Exception {
      testAutoCreatedDeadLetterFilter(RoutingType.ANYCAST);
   }

   @Test
   public void testAutoCreatedDeadLetterFilterMulticast() throws Exception {
      testAutoCreatedDeadLetterFilter(RoutingType.MULTICAST);
   }

   private void testAutoCreatedDeadLetterFilter(RoutingType routingType) throws Exception {
      final int ITERATIONS = 100;
      final int MESSAGE_COUNT = 10;

      for (int i = 0; i < ITERATIONS; i++) {
         SimpleString address = RandomUtil.randomUUIDSimpleString();
         SimpleString queue = RandomUtil.randomUUIDSimpleString();
         server.createQueue(QueueConfiguration.of(queue).setAddress(address).setRoutingType(routingType));
         ServerLocator locator = createInVMNonHALocator();
         ClientSessionFactory cf = createSessionFactory(locator);
         ClientSession s = addClientSession(cf.createSession(true, false));
         ClientProducer p = s.createProducer(address);
         for (int j = 0; j < MESSAGE_COUNT; j++) {
            p.send(s.createMessage(true).setRoutingType(routingType));
         }
         p.close();
         ClientConsumer consumer = s.createConsumer(queue);
         s.start();
         for (int j = 0; j < MESSAGE_COUNT; j++) {
            ClientMessage message = consumer.receive();
            assertNotNull(message);
            message.acknowledge();
         }
         s.rollback();
         Queue dlq = server.locateQueue(AddressSettings.DEFAULT_DEAD_LETTER_QUEUE_PREFIX.concat(address).concat(AddressSettings.DEFAULT_DEAD_LETTER_QUEUE_SUFFIX));
         assertNotNull(dlq);
         Wait.assertEquals(MESSAGE_COUNT, dlq::getMessageCount);
      }

      assertEquals(ITERATIONS, server.getPostOffice().getBindingsForAddress(dla).getBindings().size());
   }

   @Test
   public void testAutoDeletionAndRecreationOfDeadLetterResources() throws Exception {
      SimpleString dlqName = AddressSettings.DEFAULT_DEAD_LETTER_QUEUE_PREFIX.concat(addressA).concat(AddressSettings.DEFAULT_DEAD_LETTER_QUEUE_SUFFIX);

      triggerDlaDelivery();

      // consume the message from the DLQ so it will be auto-deleted
      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory sessionFactory = createSessionFactory(locator);
      ClientSession session = addClientSession(sessionFactory.createSession(true, true));
      ClientConsumer consumer = session.createConsumer(dlqName);
      session.start();
      ClientMessage message = consumer.receive();
      assertNotNull(message);
      message.acknowledge();
      consumer.close();
      session.close();
      sessionFactory.close();
      locator.close();

      Wait.assertTrue(() -> server.locateQueue(dlqName) == null, 2000, 100);

      server.destroyQueue(queueA);

      triggerDlaDelivery();
      assertNotNull(server.getAddressInfo(dla));
      assertNotNull(server.locateQueue(dlqName));
   }

   @Test
   public void testWithJMSFQQN() throws Exception {
      SimpleString dlqName = AddressSettings.DEFAULT_DEAD_LETTER_QUEUE_PREFIX.concat(addressA).concat(AddressSettings.DEFAULT_DEAD_LETTER_QUEUE_SUFFIX);
      String fqqn = CompositeAddress.toFullyQualified(dla, dlqName).toString();

      triggerDlaDelivery();

      JMSContext context = new ActiveMQConnectionFactory("vm://0").createContext();
      context.start();
      assertNotNull(context.createConsumer(context.createQueue(fqqn)).receive(2000));
   }

   @Test
   public void testDivertedMessage() throws Exception {
      SimpleString dlqName = AddressSettings.DEFAULT_DEAD_LETTER_QUEUE_PREFIX.concat(addressA).concat(AddressSettings.DEFAULT_DEAD_LETTER_QUEUE_SUFFIX);
      String divertAddress = "divertAddress";

      server.deployDivert(new DivertConfiguration().setName("testDivert").setAddress(divertAddress).setForwardingAddress(addressA.toString()));

      server.createQueue(QueueConfiguration.of(queueA).setAddress(addressA).setRoutingType(RoutingType.ANYCAST));

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory sessionFactory = createSessionFactory(locator);
      ClientSession session = addClientSession(sessionFactory.createSession(true, true));
      ClientProducer producer = addClientProducer(session.createProducer(divertAddress));
      producer.send(session.createMessage(true));
      producer.close();

      Wait.assertEquals(1L, () -> server.locateQueue(queueA).getMessageCount(), 2000, 100);

      triggerDlaDelivery();

      Wait.assertTrue(() -> server.locateQueue(dlqName).getMessageCount() == 1, 2000, 100);

      ClientConsumer consumer = session.createConsumer(dlqName);
      session.start();
      ClientMessage message = consumer.receive(1000);
      assertNotNull(message);
      message.acknowledge();
   }

   @Test
   public void testMovedMessage() throws Exception {
      SimpleString dlqName = AddressSettings.DEFAULT_DEAD_LETTER_QUEUE_PREFIX.concat(addressA).concat(AddressSettings.DEFAULT_DEAD_LETTER_QUEUE_SUFFIX);
      final SimpleString moveFromAddress = SimpleString.of("moveFromAddress");
      final SimpleString moveFromQueue = SimpleString.of("moveFromQueue");
      server.createQueue(QueueConfiguration.of(moveFromQueue).setAddress(moveFromAddress).setRoutingType(RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(queueA).setAddress(addressA).setRoutingType(RoutingType.ANYCAST));

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory sessionFactory = createSessionFactory(locator);
      ClientSession session = addClientSession(sessionFactory.createSession(true, true));
      ClientProducer producer = addClientProducer(session.createProducer(moveFromAddress));
      producer.send(session.createMessage(true));
      producer.close();
      Wait.assertEquals(1L, server.locateQueue(moveFromQueue)::getMessageCount, 2000, 100);
      server.locateQueue(moveFromQueue).moveReferences(null, addressA, null);

      Wait.assertEquals(1L, server.locateQueue(queueA)::getMessageCount, 2000, 100);

      triggerDlaDelivery();

      Wait.assertEquals(1L, server.locateQueue(dlqName)::getMessageCount, 2000, 100);

      ClientConsumer consumer = session.createConsumer(dlqName);
      session.start();
      ClientMessage message = consumer.receive(1000);
      assertNotNull(message);
      message.acknowledge();
   }

   @Test
   public void testOngoingSendToDLAPreventAutoDelete() throws Exception {
      final int messageCount = 100;
      SimpleString dlqName = AddressSettings.DEFAULT_DEAD_LETTER_QUEUE_PREFIX.concat(addressA).concat(AddressSettings.DEFAULT_DEAD_LETTER_QUEUE_SUFFIX);

      server.createQueue(QueueConfiguration.of(queueA).setAddress(addressA).setRoutingType(RoutingType.ANYCAST));

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory sessionFactory = createSessionFactory(locator);
      ClientSession session = addClientSession(sessionFactory.createSession(true, true));
      ClientProducer producer = addClientProducer(session.createProducer(addressA));

      ClientMessage message = session.createMessage(true);
      message.getBodyBuffer().writeBytes(createFakeLargeStream(1024 * 1024).readAllBytes());

      for (int i = 0; i < messageCount; i++) {
         producer.send(message);
      }

      QueueControl queueControl = server.getManagementService().getQueueControl(queueA.toString());
      queueControl.sendMessagesToDeadLetterAddress(null);

      QueueControl dlqControl = server.getManagementService().getQueueControl(dlqName.toString());
      dlqControl.retryMessages();

      for (int i = 0; i < 10; i++) {
         queueControl.sendMessagesToDeadLetterAddress(null);
         dlqControl.retryMessages();
      }

      Wait.assertTrue(() -> queueControl.getMessageCount() == messageCount, 2000);
      Wait.assertTrue(() -> server.locateQueue(dlqName) == null, 2000);

   }

   private void triggerDlaDelivery() throws Exception {
      try {
         server.createQueue(QueueConfiguration.of(queueA).setAddress(addressA).setRoutingType(RoutingType.ANYCAST));
      } catch (Exception e) {
         // ignore
      }
      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory sessionFactory = createSessionFactory(locator);
      ClientSession session = addClientSession(sessionFactory.createSession(true, false));
      ClientProducer producer = addClientProducer(session.createProducer(addressA));
      producer.send(session.createMessage(true));
      producer.close();
      ClientConsumer consumer = addClientConsumer(session.createConsumer(queueA));
      session.start();
      ClientMessage message = consumer.receive();
      assertNotNull(message);
      message.acknowledge();
      session.rollback();
      session.close();
      sessionFactory.close();
      locator.close();
   }
}
