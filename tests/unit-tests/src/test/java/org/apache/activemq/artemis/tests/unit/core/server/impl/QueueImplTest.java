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
package org.apache.activemq.artemis.tests.unit.core.server.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.filter.impl.FilterImpl;
import org.apache.activemq.artemis.core.postoffice.impl.LocalQueueBinding;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.Consumer;
import org.apache.activemq.artemis.core.server.HandleStatus;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.QueueImpl;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.selector.filter.Filterable;
import org.apache.activemq.artemis.tests.unit.core.server.impl.fakes.FakeConsumer;
import org.apache.activemq.artemis.tests.unit.core.server.impl.fakes.FakeFilter;
import org.apache.activemq.artemis.tests.unit.core.server.impl.fakes.FakePostOffice;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.ActiveMQThreadFactory;
import org.apache.activemq.artemis.utils.FutureLatch;
import org.apache.activemq.artemis.utils.actors.ArtemisExecutor;
import org.apache.activemq.artemis.utils.collections.LinkedListIterator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class QueueImplTest extends ActiveMQTestBase {
   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   // The tests ----------------------------------------------------------------

   private ScheduledExecutorService scheduledExecutor;

   private ExecutorService executor;

   private ActiveMQServer defaultServer;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      scheduledExecutor = Executors.newSingleThreadScheduledExecutor(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName()));
      executor = Executors.newSingleThreadExecutor(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName()));
      defaultServer = createServer(createDefaultConfig(1, false));
      defaultServer.start();
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      scheduledExecutor.shutdownNow();
      executor.shutdownNow();
      super.tearDown();
   }

   private static final SimpleString queue1 = SimpleString.of("queue1");

   private static final SimpleString address1 = SimpleString.of("address1");

   @Test
   public void testName() {
      final SimpleString name = SimpleString.of("oobblle");

      QueueImpl queue = getNamedQueue(name);

      assertEquals(name, queue.getName());
   }

   @Test
   public void testDurable() {
      QueueImpl queue = getNonDurableQueue();

      assertFalse(queue.isDurable());

      queue = getDurableQueue();

      assertTrue(queue.isDurable());
   }

   @Test
   public void testAddRemoveConsumer() throws Exception {
      Consumer cons1 = new FakeConsumer();

      Consumer cons2 = new FakeConsumer();

      Consumer cons3 = new FakeConsumer();

      QueueImpl queue = getTemporaryQueue();

      assertEquals(0, queue.getConsumerCount());

      queue.addConsumer(cons1);

      assertEquals(1, queue.getConsumerCount());

      queue.removeConsumer(cons1);

      assertEquals(0, queue.getConsumerCount());

      queue.addConsumer(cons1);

      queue.addConsumer(cons2);

      queue.addConsumer(cons3);

      assertEquals(3, queue.getConsumerCount());

      queue.removeConsumer(new FakeConsumer());

      assertEquals(3, queue.getConsumerCount());

      queue.removeConsumer(cons1);

      assertEquals(2, queue.getConsumerCount());

      queue.removeConsumer(cons2);

      assertEquals(1, queue.getConsumerCount());

      queue.removeConsumer(cons3);

      assertEquals(0, queue.getConsumerCount());

      queue.removeConsumer(cons3);
   }

   @Test
   public void testGetFilter() {
      QueueImpl queue = getTemporaryQueue();

      assertNull(queue.getFilter());

      Filter filter = new Filter() {
         @Override
         public boolean match(final Message message) {
            return false;
         }

         @Override
         public boolean match(Map<String, String> map) {
            return false;
         }

         @Override
         public boolean match(Filterable filterable) {
            return false;
         }

         @Override
         public SimpleString getFilterString() {
            return null;
         }
      };

      queue = getFilteredQueue(filter);

      assertEquals(filter, queue.getFilter());

   }

   @Test
   public void testSimpleadd() {
      QueueImpl queue = getTemporaryQueue();

      final int numMessages = 10;

      for (int i = 0; i < numMessages; i++) {
         MessageReference ref = generateReference(queue, i);

         queue.addTail(ref);
      }

      assertEquals(numMessages, getMessageCount(queue));
      assertEquals(0, queue.getScheduledCount());
      assertEquals(0, queue.getDeliveringCount());

   }

   @Test
   public void testRate() throws Exception {
      QueueImpl queue = getTemporaryQueue();

      final int numMessages = 10;

      for (int i = 0; i < numMessages; i++) {
         MessageReference ref = generateReference(queue, i);

         queue.addTail(ref);
      }

      Thread.sleep(1000);

      Method getRate = QueueImpl.class.getDeclaredMethod("getRate", null);
      getRate.setAccessible(true);
      float rate = (float) getRate.invoke(queue, null);

      assertTrue(rate <= 10.0f);
      logger.debug("Rate: {}", rate);
   }

   @Test
   public void testSimpleNonDirectDelivery() throws Exception {
      QueueImpl queue = getTemporaryQueue();

      final int numMessages = 10;

      List<MessageReference> refs = new ArrayList<>();

      for (int i = 0; i < numMessages; i++) {
         MessageReference ref = generateReference(queue, i);

         refs.add(ref);

         queue.addTail(ref);
      }

      assertEquals(10, getMessageCount(queue));
      assertEquals(0, queue.getScheduledCount());
      assertEquals(0, queue.getDeliveringCount());

      // Now add a consumer
      FakeConsumer consumer = new FakeConsumer();

      queue.addConsumer(consumer);

      assertTrue(consumer.getReferences().isEmpty());
      assertEquals(10, getMessageCount(queue));
      assertEquals(0, queue.getScheduledCount());

      queue.deliverNow();

      assertRefListsIdenticalRefs(refs, consumer.getReferences());
      assertEquals(numMessages, getMessageCount(queue));
      assertEquals(0, queue.getScheduledCount());
      assertEquals(numMessages, queue.getDeliveringCount());
   }

   @Test
   public void testBusyConsumer() throws Exception {
      QueueImpl queue = getTemporaryQueue();

      FakeConsumer consumer = new FakeConsumer();

      consumer.setStatusImmediate(HandleStatus.BUSY);

      queue.addConsumer(consumer);

      final int numMessages = 10;

      List<MessageReference> refs = new ArrayList<>();

      for (int i = 0; i < numMessages; i++) {
         MessageReference ref = generateReference(queue, i);

         refs.add(ref);

         queue.addTail(ref);
      }

      assertEquals(10, getMessageCount(queue));
      assertEquals(0, queue.getScheduledCount());
      assertEquals(0, queue.getDeliveringCount());

      queue.deliverNow();

      assertEquals(10, getMessageCount(queue));
      assertEquals(0, queue.getScheduledCount());
      assertEquals(0, queue.getDeliveringCount());
      assertTrue(consumer.getReferences().isEmpty());

      consumer.setStatusImmediate(HandleStatus.HANDLED);

      queue.deliverNow();

      assertRefListsIdenticalRefs(refs, consumer.getReferences());
      assertEquals(10, getMessageCount(queue));
      assertEquals(0, queue.getScheduledCount());
      assertEquals(10, queue.getDeliveringCount());
   }

   @Test
   public void testBusyConsumerThenAddMoreMessages() throws Exception {
      QueueImpl queue = getTemporaryQueue();

      FakeConsumer consumer = new FakeConsumer();

      consumer.setStatusImmediate(HandleStatus.BUSY);

      queue.addConsumer(consumer);

      final int numMessages = 10;

      List<MessageReference> refs = new ArrayList<>();

      for (int i = 0; i < numMessages; i++) {
         MessageReference ref = generateReference(queue, i);

         refs.add(ref);

         queue.addTail(ref);
      }

      assertEquals(10, getMessageCount(queue));
      assertEquals(0, queue.getScheduledCount());
      assertEquals(0, queue.getDeliveringCount());

      queue.deliverNow();

      assertEquals(10, getMessageCount(queue));
      assertEquals(0, queue.getScheduledCount());
      assertEquals(0, queue.getDeliveringCount());
      assertTrue(consumer.getReferences().isEmpty());

      for (int i = numMessages; i < numMessages * 2; i++) {
         MessageReference ref = generateReference(queue, i);

         refs.add(ref);

         queue.addTail(ref);
      }

      assertEquals(20, getMessageCount(queue));
      assertEquals(0, queue.getScheduledCount());
      assertEquals(0, queue.getDeliveringCount());
      assertTrue(consumer.getReferences().isEmpty());

      consumer.setStatusImmediate(HandleStatus.HANDLED);

      for (int i = numMessages * 2; i < numMessages * 3; i++) {
         MessageReference ref = generateReference(queue, i);

         refs.add(ref);

         queue.addTail(ref);
      }

      queue.deliverNow();

      assertRefListsIdenticalRefs(refs, consumer.getReferences());
      assertEquals(30, getMessageCount(queue));
      assertEquals(0, queue.getScheduledCount());
      assertEquals(30, queue.getDeliveringCount());
   }

   @Test
   public void testaddHeadadd() throws Exception {
      QueueImpl queue = getTemporaryQueue();

      final int numMessages = 10;

      List<MessageReference> refs1 = new ArrayList<>();

      for (int i = 0; i < numMessages; i++) {
         MessageReference ref = generateReference(queue, i);

         refs1.add(ref);

         queue.addTail(ref);
      }

      LinkedList<MessageReference> refs2 = new LinkedList<>();

      for (int i = 0; i < numMessages; i++) {
         MessageReference ref = generateReference(queue, i + numMessages);

         refs2.addFirst(ref);

         queue.addHead(ref, false);
      }

      List<MessageReference> refs3 = new ArrayList<>();

      for (int i = 0; i < numMessages; i++) {
         MessageReference ref = generateReference(queue, i + 2 * numMessages);

         refs3.add(ref);

         queue.addTail(ref);
      }

      FakeConsumer consumer = new FakeConsumer();

      queue.addConsumer(consumer);

      queue.deliverNow();

      List<MessageReference> allRefs = new ArrayList<>();

      allRefs.addAll(refs2);
      allRefs.addAll(refs1);
      allRefs.addAll(refs3);

      assertRefListsIdenticalRefs(allRefs, consumer.getReferences());
   }

   @Test
   public void testChangeConsumersAndDeliver() throws Exception {
      QueueImpl queue = getTemporaryQueue();

      final int numMessages = 10;

      List<MessageReference> refs = new ArrayList<>();

      for (int i = 0; i < numMessages; i++) {
         MessageReference ref = generateReference(queue, i);

         refs.add(ref);

         queue.addTail(ref);
      }

      assertEquals(numMessages, getMessageCount(queue));
      assertEquals(0, queue.getScheduledCount());
      assertEquals(0, queue.getDeliveringCount());

      FakeConsumer cons1 = new FakeConsumer();

      queue.addConsumer(cons1);

      queue.deliverNow();

      assertEquals(numMessages, getMessageCount(queue));
      assertEquals(0, queue.getScheduledCount());
      assertEquals(numMessages, queue.getDeliveringCount());

      assertRefListsIdenticalRefs(refs, cons1.getReferences());

      FakeConsumer cons2 = new FakeConsumer();

      queue.addConsumer(cons2);

      assertEquals(2, queue.getConsumerCount());

      cons1.getReferences().clear();

      for (MessageReference ref : refs) {
         ref.getMessage().refUp();
         queue.acknowledge(ref);
      }

      refs.clear();

      for (int i = 0; i < 2 * numMessages; i++) {
         MessageReference ref = generateReference(queue, i);

         refs.add(ref);

         queue.addTail(ref);
      }

      queue.deliverNow();

      assertEquals(numMessages * 2, getMessageCount(queue));
      assertEquals(0, queue.getScheduledCount());
      assertEquals(numMessages * 2, queue.getDeliveringCount());

      assertEquals(numMessages, cons1.getReferences().size());

      assertEquals(numMessages, cons2.getReferences().size());

      cons1.getReferences().clear();
      cons2.getReferences().clear();

      for (MessageReference ref : refs) {
         queue.acknowledge(ref);
      }
      refs.clear();

      FakeConsumer cons3 = new FakeConsumer();

      queue.addConsumer(cons3);

      assertEquals(3, queue.getConsumerCount());

      for (int i = 0; i < 3 * numMessages; i++) {
         MessageReference ref = generateReference(queue, i);

         refs.add(ref);

         queue.addTail(ref);
      }

      queue.deliverNow();

      assertEquals(numMessages * 3, getMessageCount(queue));
      assertEquals(0, queue.getScheduledCount());
      assertEquals(numMessages * 3, queue.getDeliveringCount());

      assertEquals(numMessages, cons1.getReferences().size());

      assertEquals(numMessages, cons2.getReferences().size());

      assertEquals(numMessages, cons3.getReferences().size());

      queue.removeConsumer(cons1);

      cons3.getReferences().clear();
      cons2.getReferences().clear();

      for (MessageReference ref : refs) {
         queue.acknowledge(ref);
      }
      refs.clear();

      for (int i = 0; i < 2 * numMessages; i++) {
         MessageReference ref = generateReference(queue, i);

         refs.add(ref);

         queue.addTail(ref);
      }

      queue.deliverNow();

      assertEquals(numMessages * 2, getMessageCount(queue));
      assertEquals(0, queue.getScheduledCount());
      assertEquals(numMessages * 2, queue.getDeliveringCount());

      assertEquals(numMessages, cons2.getReferences().size());

      assertEquals(numMessages, cons3.getReferences().size());

      queue.removeConsumer(cons3);

      cons2.getReferences().clear();

      for (MessageReference ref : refs) {
         queue.acknowledge(ref);
      }
      refs.clear();

      for (int i = 0; i < numMessages; i++) {
         MessageReference ref = generateReference(queue, i);

         refs.add(ref);

         queue.addTail(ref);
      }

      queue.deliverNow();

      assertEquals(numMessages, getMessageCount(queue));
      assertEquals(0, queue.getScheduledCount());
      assertEquals(numMessages, queue.getDeliveringCount());

      assertEquals(numMessages, cons2.getReferences().size());

   }

   @Test
   public void testRoundRobinWithQueueing() throws Exception {
      QueueImpl queue = getTemporaryQueue();

      final int numMessages = 10;

      List<MessageReference> refs = new ArrayList<>();

      queue.pause();

      // Test first with queueing

      FakeConsumer cons1 = new FakeConsumer();

      FakeConsumer cons2 = new FakeConsumer();

      queue.addConsumer(cons1);

      queue.addConsumer(cons2);

      for (int i = 0; i < numMessages; i++) {
         MessageReference ref = generateReference(queue, i);

         refs.add(ref);

         queue.addTail(ref);
      }

      queue.resume();

      // Need to make sure the consumers will receive the messages before we do these assertions
      long timeout = System.currentTimeMillis() + 5000;
      while (cons1.getReferences().size() != numMessages / 2 && timeout > System.currentTimeMillis()) {
         Thread.sleep(1);
      }

      while (cons2.getReferences().size() != numMessages / 2 && timeout > System.currentTimeMillis()) {
         Thread.sleep(1);
      }

      assertEquals(numMessages / 2, cons1.getReferences().size());

      assertEquals(numMessages / 2, cons2.getReferences().size());

      for (int i = 0; i < numMessages; i++) {
         MessageReference ref;

         ref = i % 2 == 0 ? cons1.getReferences().get(i / 2) : cons2.getReferences().get(i / 2);

         assertEquals(refs.get(i), ref);
      }
   }

   @Test
   public void testWithPriorities() throws Exception {
      QueueImpl queue = getTemporaryQueue();

      final int numMessages = 10;

      List<MessageReference> refs = new ArrayList<>();

      for (int i = 0; i < numMessages; i++) {
         MessageReference ref = generateReference(queue, i);

         ref.getMessage().setPriority((byte) i);

         refs.add(ref);

         queue.addTail(ref);
      }

      queue.deliverNow();

      FakeConsumer consumer = new FakeConsumer();

      queue.addConsumer(consumer);

      queue.deliverNow();

      List<MessageReference> receivedRefs = consumer.getReferences();

      // Should be in reverse order

      assertEquals(refs.size(), receivedRefs.size());

      for (int i = 0; i < numMessages; i++) {
         assertEquals(refs.get(i), receivedRefs.get(9 - i));
      }

   }

   @Test
   public void testConsumerWithFiltersDirect() throws Exception {
      testConsumerWithFilters(true);
   }

   @Test
   public void testConsumerWithFiltersQueueing() throws Exception {
      testConsumerWithFilters(false);
   }

   @Test
   public void testConsumerWithFilterAddAndRemove() {
      QueueImpl queue = getTemporaryQueue();

      Filter filter = new FakeFilter("fruit", "orange");

      FakeConsumer consumer = new FakeConsumer(filter);
   }

   @Test
   public void testIterator() {
      QueueImpl queue = getTemporaryQueue();

      final int numMessages = 20;

      List<MessageReference> refs = new ArrayList<>();

      for (int i = 0; i < numMessages; i++) {
         MessageReference ref = generateReference(queue, i);

         queue.addTail(ref);

         refs.add(ref);
      }

      assertEquals(numMessages, getMessageCount(queue));

      Iterator<MessageReference> iterator = queue.iterator();
      List<MessageReference> list = new ArrayList<>();
      while (iterator.hasNext()) {
         list.add(iterator.next());
      }
      assertRefListsIdenticalRefs(refs, list);
   }

   private void awaitExecution() {
      FutureLatch future = new FutureLatch();

      executor.execute(future);

      future.await(10000);
   }

   @Test
   public void testConsumeWithFiltersAddAndRemoveConsumer() throws Exception {

      QueueImpl queue = getTemporaryQueue();

      Filter filter = new FakeFilter("fruit", "orange");

      FakeConsumer consumer = new FakeConsumer(filter);

      queue.addConsumer(consumer);

      List<MessageReference> refs = new ArrayList<>();

      MessageReference ref1 = generateReference(queue, 1);

      ref1.getMessage().putStringProperty(SimpleString.of("fruit"), SimpleString.of("banana"));

      queue.addTail(ref1);

      MessageReference ref2 = generateReference(queue, 2);

      ref2.getMessage().putStringProperty(SimpleString.of("fruit"), SimpleString.of("orange"));

      queue.addTail(ref2);

      refs.add(ref2);

      assertEquals(2, getMessageCount(queue));

      awaitExecution();

      assertEquals(1, consumer.getReferences().size());

      assertEquals(1, queue.getDeliveringCount());

      assertRefListsIdenticalRefs(refs, consumer.getReferences());

      queue.acknowledge(ref2);

      queue.removeConsumer(consumer);

      queue.addConsumer(consumer);

      queue.deliverNow();

      refs.clear();

      consumer.clearReferences();

      MessageReference ref3 = generateReference(queue, 3);

      ref3.getMessage().putStringProperty(SimpleString.of("fruit"), SimpleString.of("banana"));

      queue.addTail(ref3);

      MessageReference ref4 = generateReference(queue, 4);

      ref4.getMessage().putStringProperty(SimpleString.of("fruit"), SimpleString.of("orange"));

      queue.addTail(ref4);

      refs.add(ref4);

      assertEquals(3, getMessageCount(queue));

      awaitExecution();

      assertEquals(1, consumer.getReferences().size());

      assertEquals(1, queue.getDeliveringCount());

      assertRefListsIdenticalRefs(refs, consumer.getReferences());
   }

   @Test
   public void testBusyConsumerWithFilterFirstCallBusy() throws Exception {
      QueueImpl queue = getTemporaryQueue();

      FakeConsumer consumer = new FakeConsumer(FilterImpl.createFilter("color = 'green'"));

      consumer.setStatusImmediate(HandleStatus.BUSY);

      queue.addConsumer(consumer);

      final int numMessages = 10;

      List<MessageReference> refs = new ArrayList<>();

      for (int i = 0; i < numMessages; i++) {
         MessageReference ref = generateReference(queue, i);
         ref.getMessage().putStringProperty("color", "green");
         refs.add(ref);

         queue.addTail(ref);
      }

      assertEquals(10, getMessageCount(queue));
      assertEquals(0, queue.getScheduledCount());
      assertEquals(0, queue.getDeliveringCount());

      queue.deliverNow();

      consumer.setStatusImmediate(null);

      queue.deliverNow();

      List<MessageReference> receeivedRefs = consumer.getReferences();
      int currId = 0;
      for (MessageReference receeivedRef : receeivedRefs) {
         assertEquals(receeivedRef.getMessage().getMessageID(), currId++, "messages received out of order");
      }
   }

   @Test
   public void testBusyConsumerWithFilterThenAddMoreMessages() throws Exception {
      QueueImpl queue = getTemporaryQueue();

      FakeConsumer consumer = new FakeConsumer(FilterImpl.createFilter("color = 'green'"));

      consumer.setStatusImmediate(HandleStatus.BUSY);

      queue.addConsumer(consumer);

      final int numMessages = 10;

      List<MessageReference> refs = new ArrayList<>();

      for (int i = 0; i < numMessages; i++) {
         MessageReference ref = generateReference(queue, i);
         ref.getMessage().putStringProperty("color", "red");
         refs.add(ref);

         queue.addTail(ref);
      }

      assertEquals(10, getMessageCount(queue));
      assertEquals(0, queue.getScheduledCount());
      assertEquals(0, queue.getDeliveringCount());

      queue.deliverNow();

      assertEquals(10, getMessageCount(queue));
      assertEquals(0, queue.getScheduledCount());
      assertEquals(0, queue.getDeliveringCount());
      assertTrue(consumer.getReferences().isEmpty());

      for (int i = numMessages; i < numMessages * 2; i++) {
         MessageReference ref = generateReference(queue, i);

         refs.add(ref);
         ref.getMessage().putStringProperty("color", "green");
         queue.addTail(ref);
      }

      assertEquals(20, getMessageCount(queue));
      assertEquals(0, queue.getScheduledCount());
      assertEquals(0, queue.getDeliveringCount());
      assertTrue(consumer.getReferences().isEmpty());

      consumer.setStatusImmediate(null);

      for (int i = numMessages * 2; i < numMessages * 3; i++) {
         MessageReference ref = generateReference(queue, i);

         refs.add(ref);

         queue.addTail(ref);
      }

      queue.deliverNow();

      assertEquals(numMessages, consumer.getReferences().size());
      assertEquals(30, getMessageCount(queue));
      assertEquals(0, queue.getScheduledCount());
      assertEquals(10, queue.getDeliveringCount());

      List<MessageReference> receeivedRefs = consumer.getReferences();
      int currId = 10;
      for (MessageReference receeivedRef : receeivedRefs) {
         assertEquals(receeivedRef.getMessage().getMessageID(), currId++, "messages received out of order");
      }
   }

   @Test
   public void testConsumerWithFilterThenAddMoreMessages() throws Exception {
      QueueImpl queue = getTemporaryQueue();

      final int numMessages = 10;
      List<MessageReference> refs = new ArrayList<>();

      for (int i = 0; i < numMessages; i++) {
         MessageReference ref = generateReference(queue, i);
         ref.getMessage().putStringProperty("color", "red");
         refs.add(ref);

         queue.addTail(ref);
      }

      assertEquals(10, getMessageCount(queue));
      assertEquals(0, queue.getScheduledCount());
      assertEquals(0, queue.getDeliveringCount());

      queue.deliverNow();

      assertEquals(10, getMessageCount(queue));
      assertEquals(0, queue.getScheduledCount());
      assertEquals(0, queue.getDeliveringCount());

      for (int i = numMessages; i < numMessages * 2; i++) {
         MessageReference ref = generateReference(queue, i);

         refs.add(ref);
         ref.getMessage().putStringProperty("color", "green");
         queue.addTail(ref);
      }

      FakeConsumer consumer = new FakeConsumer(FilterImpl.createFilter("color = 'green'"));

      queue.addConsumer(consumer);

      queue.deliverNow();

      assertEquals(20, getMessageCount(queue));
      assertEquals(0, queue.getScheduledCount());
      assertEquals(10, queue.getDeliveringCount());

      for (int i = numMessages * 2; i < numMessages * 3; i++) {
         MessageReference ref = generateReference(queue, i);

         refs.add(ref);
         ref.getMessage().putStringProperty("color", "green");
         queue.addTail(ref);
      }

      queue.deliverNow();

      assertEquals(20, consumer.getReferences().size());
      assertEquals(30, getMessageCount(queue));
      assertEquals(0, queue.getScheduledCount());
      assertEquals(20, queue.getDeliveringCount());
   }


   @Test
   public void testNoMatchConsumersAllowsRedistribution() throws Exception {
      QueueImpl queue = getTemporaryQueue();

      final int numMessages = 2;
      List<MessageReference> refs = new ArrayList<>();

      for (int i = 0; i < numMessages; i++) {
         MessageReference ref = generateReference(queue, i);
         ref.getMessage().putStringProperty("color", "red");
         refs.add(ref);

         queue.addTail(ref);
      }

      assertEquals(numMessages, getMessageCount(queue));
      queue.deliverNow();

      assertEquals(numMessages, getMessageCount(queue));

      FakeConsumer consumer = new FakeConsumer(FilterImpl.createFilter("color = 'green'"));
      queue.addConsumer(consumer);

      FakeConsumer consumer2 = new FakeConsumer(FilterImpl.createFilter("color = 'orange'"));
      queue.addConsumer(consumer2);

      queue.deliverNow();
      assertEquals(0, consumer.getReferences().size());
      assertEquals(0, consumer2.getReferences().size());

      // verify redistributor is doing some work....
      try {
         // should attempt to add due to unmatched
         queue.addRedistributor(0);
         fail("expect error on attempt to add addRedistributor - npe b/c no storage etc");
      } catch (NullPointerException expected) {
      }

      // verify with odd number as check depends on order/reset/wrap of consumers
      FakeConsumer consumer3 = new FakeConsumer(FilterImpl.createFilter("color = 'blue'"));
      queue.addConsumer(consumer3);

      queue.deliverNow();

      assertEquals(0, consumer.getReferences().size());
      assertEquals(0, consumer2.getReferences().size());
      assertEquals(0, consumer3.getReferences().size());

      // verify redistributor is doing some work....
      try {
         // adding a consumer gives every existing consumer a fresh look at pending messages, so this should attempt to add due to unmatched
         queue.addRedistributor(0);
         fail("expect error on attempt to add addRedistributor - npe b/c no storage etc");
      } catch (NullPointerException expected) {
      }

      // on new message dispatch, need for redistributor will kick in
      MessageReference ref = generateReference(queue, numMessages);
      ref.getMessage().putStringProperty("color", "red");
      refs.add(ref);

      queue.addTail(ref);
      queue.deliverNow();

      // verify redistributor is doing some work....
      try {
         // should attempt to add due to unmatched
         queue.addRedistributor(0);
         fail("expect error on attempt to add addRedistributor - npe b/c no storage etc");
      } catch (NullPointerException expected) {
      }

      assertEquals(numMessages + 1, getMessageCount(queue));
   }

   @Test
   public void testNoMatchOn3AllowsRedistribution() throws Exception {
      QueueImpl queue = getTemporaryQueue();

      int i = 0;
      MessageReference ref = generateReference(queue, i++);
      ref.getMessage().putStringProperty("color", "red");
      queue.addTail(ref);

      ref = generateReference(queue, i++);
      ref.getMessage().putStringProperty("color", "red");
      queue.addTail(ref);

      ref = generateReference(queue, i++);
      ref.getMessage().putStringProperty("color", "blue");
      queue.addTail(ref);

      assertEquals(3, getMessageCount(queue));

      FakeConsumer consumerRed = new FakeConsumer(FilterImpl.createFilter("color = 'red'"));
      queue.addConsumer(consumerRed);

      FakeConsumer consumerOrange = new FakeConsumer(FilterImpl.createFilter("color = 'orange'"));
      queue.addConsumer(consumerOrange);

      queue.deliverNow();
      assertEquals(2, consumerRed.getReferences().size());
      assertEquals(0, consumerOrange.getReferences().size());

      // verify redistributor is doing some work....
      try {
         // should attempt to add due to unmatched
         queue.addRedistributor(0);
         fail("expect error on attempt to add addRedistributor - npe b/c no storage etc");
      } catch (NullPointerException expected) {
      }
   }

   @Test
   public void testNoRedistributorInternalQueue() throws Exception {
      QueueImpl queue = getTemporaryQueue();
      queue.setInternalQueue(true);

      queue.addRedistributor(0);
      assertNull(queue.getRedistributor());
   }

   private void testConsumerWithFilters(final boolean direct) throws Exception {
      QueueImpl queue = getTemporaryQueue();

      Filter filter = new FakeFilter("fruit", "orange");

      FakeConsumer consumer = new FakeConsumer(filter);

      if (direct) {
         queue.addConsumer(consumer);
      }

      List<MessageReference> refs = new ArrayList<>();

      MessageReference ref1 = generateReference(queue, 1);

      ref1.getMessage().putStringProperty(SimpleString.of("fruit"), SimpleString.of("banana"));

      queue.addTail(ref1);

      MessageReference ref2 = generateReference(queue, 2);

      ref2.getMessage().putStringProperty(SimpleString.of("cheese"), SimpleString.of("stilton"));

      queue.addTail(ref2);

      MessageReference ref3 = generateReference(queue, 3);

      ref3.getMessage().putStringProperty(SimpleString.of("cake"), SimpleString.of("sponge"));

      queue.addTail(ref3);

      MessageReference ref4 = generateReference(queue, 4);

      ref4.getMessage().putStringProperty(SimpleString.of("fruit"), SimpleString.of("orange"));

      refs.add(ref4);

      queue.addTail(ref4);

      MessageReference ref5 = generateReference(queue, 5);

      ref5.getMessage().putStringProperty(SimpleString.of("fruit"), SimpleString.of("apple"));

      queue.addTail(ref5);

      MessageReference ref6 = generateReference(queue, 6);

      ref6.getMessage().putStringProperty(SimpleString.of("fruit"), SimpleString.of("orange"));

      refs.add(ref6);

      queue.addTail(ref6);

      if (!direct) {
         queue.addConsumer(consumer);

         queue.deliverNow();
      }

      assertEquals(6, getMessageCount(queue));

      awaitExecution();

      assertEquals(2, consumer.getReferences().size());

      assertEquals(2, queue.getDeliveringCount());

      assertRefListsIdenticalRefs(refs, consumer.getReferences());

      queue.acknowledge(ref5);
      queue.acknowledge(ref6);

      queue.removeConsumer(consumer);

      consumer = new FakeConsumer();

      queue.addConsumer(consumer);

      queue.deliverNow();

      assertEquals(4, getMessageCount(queue));

      assertEquals(4, consumer.getReferences().size());

      assertEquals(4, queue.getDeliveringCount());
   }

   @Test
   public void testMessageOrder() throws Exception {
      FakeConsumer consumer = new FakeConsumer();
      QueueImpl queue = getTemporaryQueue();
      MessageReference messageReference = generateReference(queue, 1);
      MessageReference messageReference2 = generateReference(queue, 2);
      MessageReference messageReference3 = generateReference(queue, 3);
      queue.addHead(messageReference, false);
      queue.addTail(messageReference2);
      queue.addHead(messageReference3, false);

      assertEquals(0, consumer.getReferences().size());
      queue.addConsumer(consumer);
      queue.deliverNow();

      assertEquals(3, consumer.getReferences().size());
      assertEquals(messageReference3, consumer.getReferences().get(0));
      assertEquals(messageReference, consumer.getReferences().get(1));
      assertEquals(messageReference2, consumer.getReferences().get(2));
   }

   @Test
   public void testMessagesAdded() throws Exception {
      QueueImpl queue = getTemporaryQueue();
      MessageReference messageReference = generateReference(queue, 1);
      MessageReference messageReference2 = generateReference(queue, 2);
      MessageReference messageReference3 = generateReference(queue, 3);
      queue.addTail(messageReference);
      queue.addTail(messageReference2);
      queue.addTail(messageReference3);
      assertEquals(3, getMessagesAdded(queue));
   }

   /**
    * Test the paused and resumed states with async deliveries.
    */
   @Test
   public void testPauseAndResumeWithAsync() throws Exception {
      QueueImpl queue = getTemporaryQueue();

      // pauses the queue
      queue.pause();

      final int numMessages = 10;

      List<MessageReference> refs = new ArrayList<>();

      for (int i = 0; i < numMessages; i++) {
         MessageReference ref = generateReference(queue, i);

         refs.add(ref);

         queue.addTail(ref);
      }
      // even as this queue is paused, it will receive the messages anyway
      assertEquals(10, getMessageCount(queue));
      assertEquals(0, queue.getScheduledCount());
      assertEquals(0, queue.getDeliveringCount());

      // Now add a consumer
      FakeConsumer consumer = new FakeConsumer();

      queue.addConsumer(consumer);

      assertTrue(consumer.getReferences().isEmpty());
      assertEquals(10, getMessageCount(queue));
      assertEquals(0, queue.getScheduledCount());
      // explicit order of delivery
      queue.deliverNow();
      // As the queue is paused, even an explicit order of delivery will not work.
      assertEquals(0, consumer.getReferences().size());
      assertEquals(numMessages, getMessageCount(queue));
      assertEquals(0, queue.getScheduledCount());
      assertEquals(0, queue.getDeliveringCount());
      // resuming work
      queue.resume();

      awaitExecution();
      // after resuming the delivery begins.
      assertRefListsIdenticalRefs(refs, consumer.getReferences());
      assertEquals(numMessages, getMessageCount(queue));
      assertEquals(0, queue.getScheduledCount());
      assertEquals(numMessages, queue.getDeliveringCount());

   }

   /**
    * Test the paused and resumed states with direct deliveries.
    */

   @Test
   public void testPauseAndResumeWithDirect() throws Exception {
      QueueImpl queue = getTemporaryQueue();

      // Now add a consumer
      FakeConsumer consumer = new FakeConsumer();

      queue.addConsumer(consumer);

      // brings to queue to paused state
      queue.pause();

      final int numMessages = 10;

      List<MessageReference> refs = new ArrayList<>();

      for (int i = 0; i < numMessages; i++) {
         MessageReference ref = generateReference(queue, i);
         refs.add(ref);
         queue.addTail(ref);
      }

      // the queue even if it's paused will receive the message but won't forward
      // directly to the consumer until resumed.

      assertEquals(numMessages, getMessageCount(queue));
      assertEquals(0, queue.getScheduledCount());
      assertEquals(0, queue.getDeliveringCount());
      assertTrue(consumer.getReferences().isEmpty());

      // brings the queue to resumed state.
      queue.resume();

      awaitExecution();

      // resuming delivery of messages
      assertRefListsIdenticalRefs(refs, consumer.getReferences());
      assertEquals(numMessages, getMessageCount(queue));
      assertEquals(numMessages, queue.getDeliveringCount());

   }

   @Test
   public void testResetMessagesAdded() throws Exception {
      QueueImpl queue = getTemporaryQueue();
      MessageReference messageReference = generateReference(queue, 1);
      MessageReference messageReference2 = generateReference(queue, 2);
      queue.addTail(messageReference);
      queue.addTail(messageReference2);
      assertEquals(2, getMessagesAdded(queue));
      queue.resetMessagesAdded();
      assertEquals(0, getMessagesAdded(queue));
   }

   class AddtoQueueRunner implements Runnable {

      QueueImpl queue;

      MessageReference messageReference;

      boolean added = false;

      CountDownLatch countDownLatch;

      boolean first;

      AddtoQueueRunner(final boolean first,
                       final QueueImpl queue,
                       final MessageReference messageReference,
                       final CountDownLatch countDownLatch) {
         this.queue = queue;
         this.messageReference = messageReference;
         this.countDownLatch = countDownLatch;
         this.first = first;
      }

      @Override
      public void run() {
         if (first) {
            queue.addHead(messageReference, false);
         } else {
            queue.addTail(messageReference);
         }
         added = true;
         countDownLatch.countDown();
      }
   }

   @Test
   public void testTotalIteratorOrder() throws Exception {
      final String MY_ADDRESS = "myAddress";
      final String MY_QUEUE = "myQueue";

      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(createDefaultInVMConfig(), true));

      AddressSettings defaultSetting = new AddressSettings().setPageSizeBytes(10 * 1024).setMaxSizeBytes(20 * 1024);
      server.getAddressSettingsRepository().addMatch("#", defaultSetting);
      server.start();

      ServerLocator locator = createInVMNonHALocator().setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

      ClientSessionFactory factory = createSessionFactory(locator);
      ClientSession session = addClientSession(factory.createSession(false, true, true));

      session.createQueue(QueueConfiguration.of(MY_QUEUE).setAddress(MY_ADDRESS));

      ClientProducer producer = addClientProducer(session.createProducer(MY_ADDRESS));

      for (int i = 0; i < 50; i++) {
         ClientMessage message = session.createMessage(true);
         message.getBodyBuffer().writeBytes(new byte[1024]);
         message.putIntProperty("order", i);
         producer.send(message);
      }

      producer.close();
      session.close();
      factory.close();
      locator.close();

      Queue queue = ((LocalQueueBinding) server.getPostOffice().getBinding(SimpleString.of(MY_QUEUE))).getQueue();
      LinkedListIterator<MessageReference> totalIterator = queue.browserIterator();

      try {
         int i = 0;
         while (totalIterator.hasNext()) {
            MessageReference ref = totalIterator.next();
            assertEquals(i++, ref.getMessage().getIntProperty("order").intValue());
         }
      } finally {
         totalIterator.close();
         server.stop();
      }
   }

   @Test
   public void testGroupMessageWithManyConsumers() throws Exception {
      final CountDownLatch firstMessageHandled = new CountDownLatch(1);
      final CountDownLatch finished = new CountDownLatch(2);
      final Consumer groupConsumer = new FakeConsumer() {

         int count = 0;

         @Override
         public synchronized HandleStatus handle(MessageReference reference) {
            if (count == 0) {
               //the first message is handled and will be used to determine this consumer
               //to be the group consumer
               count++;
               firstMessageHandled.countDown();
               return HandleStatus.HANDLED;
            } else if (count <= 2) {
               //the next two attempts to send the second message will be done
               //attempting a direct delivery and an async one after that
               count++;
               finished.countDown();
               return HandleStatus.BUSY;
            } else {
               //this shouldn't happen, because the last attempt to deliver
               //the second message should have stop the delivery loop:
               //it will succeed just to let the message being handled and
               //reduce the message count to 0
               return HandleStatus.HANDLED;
            }
         }
      };
      final Consumer noConsumer = new FakeConsumer() {
         @Override
         public synchronized HandleStatus handle(MessageReference reference) {
            fail("this consumer isn't allowed to consume any message");
            throw new AssertionError();
         }
      };
      final QueueImpl queue = new QueueImpl(QueueConfiguration.of(QueueImplTest.queue1).setAddress("address1").setRoutingType(RoutingType.MULTICAST).setId(1L).setDurable(false).setTemporary(true), null, null, null, scheduledExecutor, null, null, null, ArtemisExecutor.delegate(executor), defaultServer, null);
      queue.addConsumer(groupConsumer);
      queue.addConsumer(noConsumer);
      final MessageReference firstMessageReference = generateReference(queue, 1);
      final SimpleString groupName = SimpleString.of("group");
      firstMessageReference.getMessage().putStringProperty(Message.HDR_GROUP_ID, groupName);
      final MessageReference secondMessageReference = generateReference(queue, 2);
      secondMessageReference.getMessage().putStringProperty(Message.HDR_GROUP_ID, groupName);
      queue.addTail(firstMessageReference, true);
      assertTrue(firstMessageHandled.await(3000, TimeUnit.MILLISECONDS), "first message isn't handled");
      assertEquals(groupConsumer, queue.getGroups().get(groupName), "group consumer isn't correctly set");
      queue.addTail(secondMessageReference, true);
      final boolean atLeastTwoDeliverAttempts = finished.await(3000, TimeUnit.MILLISECONDS);
      assertTrue(atLeastTwoDeliverAttempts);
      Thread.sleep(1000);
      assertEquals(1, queue.getMessageCount(), "The second message should be in the queue");
   }

   private QueueImpl getNonDurableQueue() {
      return getQueue(QueueImplTest.queue1, false, false, null);
   }

   private QueueImpl getDurableQueue() {
      return getQueue(QueueImplTest.queue1, true, false, null);
   }

   private QueueImpl getNamedQueue(SimpleString name) {
      return getQueue(name, false, true, null);
   }

   private QueueImpl getFilteredQueue(Filter filter) {
      return getQueue(QueueImplTest.queue1, false, true, filter);
   }

   private QueueImpl getTemporaryQueue() {
      return getQueue(QueueImplTest.queue1, false, true, null);
   }

   private QueueImpl getQueue(SimpleString name, boolean durable, boolean temporary, Filter filter) {
      return new QueueImpl(QueueConfiguration.of(name)
                              .setAddress("address1")
                              .setRoutingType(RoutingType.MULTICAST)
                              .setId(1L)
                              .setDurable(durable)
                              .setTemporary(temporary),
                           filter, null, null, scheduledExecutor, new FakePostOffice(), null,
                           null, ArtemisExecutor.delegate(executor), defaultServer, null);
   }
}
