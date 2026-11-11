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
package org.apache.activemq.artemis.tests.unit.core.server.cluster.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.server.cluster.impl.RemoteQueueBindingImpl;
import org.apache.activemq.artemis.tests.unit.core.postoffice.impl.fakes.FakeQueue;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.Test;

public class RemoteQueueBindImplTest extends ActiveMQTestBase {

   private void testAddRemoveConsumerWithFilter(IntFunction<SimpleString> filterFactory,
                                                int size,
                                                int expectedSize) throws Exception {
      final long id = RandomUtil.randomLong();
      final SimpleString address = RandomUtil.randomUUIDSimpleString();
      final SimpleString uniqueName = RandomUtil.randomUUIDSimpleString();
      final SimpleString routingName = RandomUtil.randomUUIDSimpleString();
      final Long remoteQueueID = RandomUtil.randomLong();
      final SimpleString filterString = SimpleString.of("A>B");
      final Queue storeAndForwardQueue = new FakeQueue(null);
      final SimpleString bridgeName = RandomUtil.randomUUIDSimpleString();
      final int distance = 0;
      RemoteQueueBindingImpl binding = new RemoteQueueBindingImpl(id, address, uniqueName, routingName, remoteQueueID, filterString, storeAndForwardQueue, bridgeName, distance, MessageLoadBalancingType.ON_DEMAND);

      final List<SimpleString> filters = new ArrayList<>(size);
      for (int i = 0; i < size; i++) {
         final SimpleString filter = filterFactory.apply(i);
         filters.add(filter);
         binding.addConsumer(filter);
      }

      assertEquals(expectedSize, binding.getFilters().size());

      for (int i = 0; i < size; i++) {
         binding.removeConsumer(filters.get(i));
      }

      assertEquals(0, binding.getFilters().size());
   }

   @Test
   public void testAddRemoveConsumer() throws Exception {
      testAddRemoveConsumerWithFilter(i -> SimpleString.of("B" + i + "<A"), 100, 100);
   }

   @Test
   public void testAddRemoveConsumerUsingSameFilter() throws Exception {
      testAddRemoveConsumerWithFilter(i -> SimpleString.of("B" + 0 + "<A"), 100, 1);
   }

   @Test
   public void testAddRemoveConsumerUsingEmptyFilters() throws Exception {
      testAddRemoveConsumerWithFilter(i -> SimpleString.of(""), 1, 0);
   }

   @Test
   public void testAddRemoveConsumerUsingNullFilters() throws Exception {
      testAddRemoveConsumerWithFilter(i -> null, 1, 0);
   }

   @Test
   public void testIsHighAcceptPriority() throws Exception {
      final long id = RandomUtil.randomLong();
      final SimpleString address = RandomUtil.randomUUIDSimpleString();
      final SimpleString uniqueName = RandomUtil.randomUUIDSimpleString();
      final SimpleString routingName = RandomUtil.randomUUIDSimpleString();
      final Long remoteQueueID = RandomUtil.randomLong();
      final SimpleString filterString = SimpleString.of("A>B");
      final Queue storeAndForwardQueue = new FakeQueue(null);
      final SimpleString bridgeName = RandomUtil.randomUUIDSimpleString();
      final int distance = 0;
      RemoteQueueBindingImpl bindingOff = new RemoteQueueBindingImpl(id, address, uniqueName, routingName, remoteQueueID, filterString, storeAndForwardQueue, bridgeName, distance, MessageLoadBalancingType.OFF);
      bindingOff.addConsumer(null);
      assertFalse(bindingOff.isHighAcceptPriority(null));

      RemoteQueueBindingImpl bindingOffWithRedistribution = new RemoteQueueBindingImpl(id, address, uniqueName, routingName, remoteQueueID, filterString, storeAndForwardQueue, bridgeName, distance, MessageLoadBalancingType.OFF_WITH_REDISTRIBUTION);
      bindingOffWithRedistribution.addConsumer(null);
      // not really intuitive, but via getNextBinding (initial routing) BindingsImpl.matchBinding() traps remote bindings
      // with OFF_WITH_REDISTRIBUTION which makes the need for change in isHighAcceptPriority redundant
      // and ensures that redistribution can occur as isHighAcceptPriority is invoked from redistribute
      assertTrue(bindingOffWithRedistribution.isHighAcceptPriority(null));
   }
}
