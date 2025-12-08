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
package org.apache.activemq.artemis.tests.integration.client;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.postoffice.impl.PostOfficeImpl;
import org.apache.activemq.artemis.core.postoffice.impl.PostOfficeTestAccessor;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AutoDeleteAddressTest extends ActiveMQTestBase {

   public final SimpleString addressA = SimpleString.of("addressA");
   public final SimpleString queueA = SimpleString.of("queueA");

   private ServerLocator locator;
   private ActiveMQServer server;
   private ClientSessionFactory cf;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      locator = createInVMNonHALocator();
      server = createServer(false);
      server.getConfiguration().setAddressQueueScanPeriod(0);

      server.start();
      cf = createSessionFactory(locator);
   }

   @Test
   public void testAutoDeleteAutoCreatedAddress() throws Exception {
      // auto-delete-addresses defaults to true
      server.createQueue(QueueConfiguration.of(queueA).setAddress(addressA).setRoutingType(RoutingType.ANYCAST).setAutoCreated(true));
      assertNotNull(server.getAddressInfo(addressA));
      cf.createSession().createConsumer(queueA).close();
      PostOfficeTestAccessor.sweepAndReapAddresses((PostOfficeImpl) server.getPostOffice());
      Wait.assertTrue(() -> server.getAddressInfo(addressA) == null);
   }

   @Test
   public void testNegativeAutoDeleteAutoCreatedAddress() throws Exception {
      server.getAddressSettingsRepository().addMatch(addressA.toString(), new AddressSettings().setAutoDeleteAddresses(false));
      server.createQueue(QueueConfiguration.of(queueA).setAddress(addressA).setRoutingType(RoutingType.ANYCAST).setAutoCreated(true));
      assertNotNull(server.getAddressInfo(addressA));
      cf.createSession().createConsumer(queueA).close();
      PostOfficeTestAccessor.sweepAndReapAddresses((PostOfficeImpl) server.getPostOffice());
      assertNotNull(server.getAddressInfo(addressA));
   }

   @Test
   public void testAutoDeleteAutoCreatedAddressSkipUsageCheckWithDelay() throws Exception {
      final long DELAY = 1500;
      server.getAddressSettingsRepository().addMatch(addressA.toString(), new AddressSettings().setAutoDeleteAddresses(true).setAutoDeleteAddressesSkipUsageCheck(true).setAutoDeleteAddressesDelay(DELAY));
      server.addAddressInfo(new AddressInfo(addressA).setAutoCreated(true));
      long start = System.currentTimeMillis();
      assertNotNull(server.getAddressInfo(addressA));
      while (System.currentTimeMillis() - start <= DELAY) {
         PostOfficeTestAccessor.sweepAndReapAddresses((PostOfficeImpl) server.getPostOffice());
         assertNotNull(server.getAddressInfo(addressA));
         Thread.sleep(100);
      }
      PostOfficeTestAccessor.sweepAndReapAddresses((PostOfficeImpl) server.getPostOffice());
      assertNull(server.getAddressInfo(addressA));
   }

   @Test
   public void testAutoDeleteAddressWithWildcardAddress() throws Exception {
      String prefix = "topic";
      server.getAddressSettingsRepository().addMatch(prefix + ".#", new AddressSettings().setAutoDeleteAddresses(true).setAutoDeleteAddressesSkipUsageCheck(true));
      String wildcardAddress = prefix + ".#";
      String queue = RandomUtil.randomUUIDString();
      final int MESSAGE_COUNT = 10;
      final CountDownLatch latch = new CountDownLatch(MESSAGE_COUNT);

      server.createQueue(QueueConfiguration.of(queue).setAddress(wildcardAddress).setRoutingType(RoutingType.ANYCAST).setAutoCreated(true));

      ClientSession consumerSession = cf.createSession();
      ClientConsumer consumer = consumerSession.createConsumer(queue);
      consumer.setMessageHandler(message -> latch.countDown());
      consumerSession.start();

      ClientSession producerSession = cf.createSession();
      ClientProducer producer = producerSession.createProducer();

      List<String> addresses = new ArrayList<>();
      for (int i = 0; i < MESSAGE_COUNT; i++) {
         String address = prefix + "." + RandomUtil.randomUUIDString();
         addresses.add(address);
         server.addAddressInfo(new AddressInfo(address).setAutoCreated(true));
         producer.send(address, producerSession.createMessage(false));
      }
      producerSession.close();

      assertTrue(latch.await(2, TimeUnit.SECONDS));

      for (String address : addresses) {
         assertNotNull(server.getAddressInfo(SimpleString.of(address)));
         Wait.assertTrue(() -> Arrays.asList(server.getPagingManager().getStoreNames()).contains(SimpleString.of(address)), 2000, 100);
      }

      PostOfficeTestAccessor.sweepAndReapAddresses((PostOfficeImpl) server.getPostOffice());

      for (String address : addresses) {
         assertNull(server.getAddressInfo(SimpleString.of(address)));
         Wait.assertFalse(() -> Arrays.asList(server.getPagingManager().getStoreNames()).contains(SimpleString.of(address)), 2000, 100);
      }

      consumerSession.close();
   }

   /**
    * Test that addresses with only wildcard subscriptions are auto-deleted when messages
    * are routed through them. This tests the fix for ARTEMIS-5773 where addresses would
    * accumulate when:
    * 1. A wildcard queue exists (e.g., publish/#)
    * 2. Messages are sent to specific addresses (e.g., publish/uuid1/uuid2)
    * 3. No direct queue binding is created on those addresses
    * 4. The address should still be considered "used" due to routed messages
    *
    * Without the fix, these addresses would never be deleted because getRoutedMessageCount()
    * was not checked in addressWasUsed().
    */
   @Test
   public void testAutoDeleteAddressWithWildcardSubscriptionAndUsageCheck() throws Exception {
      String prefix = "publish";
      // Enable auto-delete but WITHOUT skipUsageCheck - this is the key difference
      server.getAddressSettingsRepository().addMatch(prefix + ".#", new AddressSettings().setAutoDeleteAddresses(true).setAutoDeleteAddressesSkipUsageCheck(false).setAutoDeleteAddressesDelay(0));
      String wildcardAddress = prefix + ".#";
      String queue = RandomUtil.randomUUIDString();
      final int MESSAGE_COUNT = 10;
      final CountDownLatch latch = new CountDownLatch(MESSAGE_COUNT);

      // Create a wildcard queue
      server.createQueue(QueueConfiguration.of(queue).setAddress(wildcardAddress).setRoutingType(RoutingType.MULTICAST).setAutoCreated(true));

      ClientSession consumerSession = cf.createSession();
      ClientConsumer consumer = consumerSession.createConsumer(queue);
      consumer.setMessageHandler(message -> {
         try {
            message.acknowledge();
         } catch (Exception e) {
            e.printStackTrace();
         }
         latch.countDown();
      });
      consumerSession.start();

      ClientSession producerSession = cf.createSession();
      ClientProducer producer = producerSession.createProducer();

      List<String> addresses = new ArrayList<>();
      for (int i = 0; i < MESSAGE_COUNT; i++) {
         String address = prefix + "." + RandomUtil.randomUUIDString() + "." + RandomUtil.randomUUIDString();
         addresses.add(address);
         // Auto-create the address when sending the message
         producer.send(address, producerSession.createMessage(false));
      }
      producerSession.close();

      assertTrue(latch.await(2, TimeUnit.SECONDS));

      // Verify addresses were created and have paging stores
      for (String address : addresses) {
         assertNotNull(server.getAddressInfo(SimpleString.of(address)), "Address should exist: " + address);
         Wait.assertTrue(() -> Arrays.asList(server.getPagingManager().getStoreNames()).contains(SimpleString.of(address)), 2000, 100);
      }

      // Run the reaper - this would fail without the fix because addressWasUsed() would return false
      PostOfficeTestAccessor.sweepAndReapAddresses((PostOfficeImpl) server.getPostOffice());

      // Verify addresses are deleted
      for (String address : addresses) {
         String finalAddress = address; // for lambda
         Wait.assertTrue("Address should be deleted: " + address, () -> server.getAddressInfo(SimpleString.of(finalAddress)) == null, 2000, 100);
         Wait.assertFalse(() -> Arrays.asList(server.getPagingManager().getStoreNames()).contains(SimpleString.of(finalAddress)), 2000, 100);
      }

      consumerSession.close();
   }
}
