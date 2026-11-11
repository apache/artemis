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

package org.apache.activemq.artemis.core.postoffice.impl;

import java.util.ArrayList;
import java.util.List;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.Bindings;
import org.apache.activemq.artemis.core.postoffice.BindingsFactory;
import org.apache.activemq.artemis.core.server.Queue;
import org.junit.jupiter.api.Test;

import static org.apache.activemq.artemis.utils.RandomUtil.randomUUIDSimpleString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AddressManagerGetMatchingQueueTest {

   @Test
   public void testCorrectNameCorrectRoutingTypeSingleQueue() throws Exception {
      SimpleString addressName = SimpleString.of("myAddress");
      SimpleString queueName = randomUUIDSimpleString();
      SimpleAddressManager am = getSimpleAddressManager();
      am.addBinding(getLocalQueueBinding(addressName, queueName, RoutingType.ANYCAST));

      assertEquals(queueName, am.getMatchingQueue(addressName, RoutingType.ANYCAST));
   }

   @Test
   public void testCorrectNameCorrectRoutingTypeMultipleQueues() throws Exception {
      SimpleString addressName = SimpleString.of("myAddress");
      SimpleString queueName1 = randomUUIDSimpleString();
      SimpleString queueName2 = randomUUIDSimpleString();
      SimpleAddressManager am = getSimpleAddressManager();
      am.addBinding(getLocalQueueBinding(addressName, queueName1, RoutingType.ANYCAST));
      am.addBinding(getLocalQueueBinding(addressName, queueName2, RoutingType.ANYCAST));

      assertThat(am.getMatchingQueue(addressName, RoutingType.ANYCAST), anyOf(is(queueName1), is(queueName2)));
   }

   @Test
   public void testCorrectNameCorrectRoutingTypeMultipleQueuesMixedRoutingTypes() throws Exception {
      SimpleString addressName = SimpleString.of("myAddress");
      SimpleString queueName1 = randomUUIDSimpleString();
      SimpleString queueName2 = randomUUIDSimpleString();
      SimpleAddressManager am = getSimpleAddressManager();
      am.addBinding(getLocalQueueBinding(addressName, queueName1, RoutingType.ANYCAST));
      am.addBinding(getLocalQueueBinding(addressName, queueName2, RoutingType.MULTICAST));

      assertEquals(queueName1, am.getMatchingQueue(addressName, RoutingType.ANYCAST));
   }

   @Test
   public void testCorrectNameIncorrectRoutingTypeSingleQueue() throws Exception {
      SimpleString addressName = SimpleString.of("myAddress");
      SimpleAddressManager am = getSimpleAddressManager();
      am.addBinding(getLocalQueueBinding(addressName, RoutingType.MULTICAST));

      assertNull(am.getMatchingQueue(addressName, RoutingType.ANYCAST));
   }

   @Test
   public void testCorrectNameIncorrectRoutingTypeMultipleQueues() throws Exception {
      SimpleString addressName = SimpleString.of("myAddress");
      SimpleAddressManager am = getSimpleAddressManager();
      am.addBinding(getLocalQueueBinding(addressName, RoutingType.MULTICAST));
      am.addBinding(getLocalQueueBinding(addressName, RoutingType.MULTICAST));

      assertNull(am.getMatchingQueue(addressName, RoutingType.ANYCAST));
   }

   @Test
   public void testIncorrectNameCorrectRoutingTypeSingleQueue() throws Exception {
      SimpleAddressManager am = getSimpleAddressManager();
      am.addBinding(getLocalQueueBinding(randomUUIDSimpleString(), RoutingType.ANYCAST));

      assertNull(am.getMatchingQueue(randomUUIDSimpleString(), RoutingType.ANYCAST));
   }

   @Test
   public void testIncorrectNameCorrectRoutingTypeMultipleQueues() throws Exception {
      SimpleAddressManager am = getSimpleAddressManager();
      am.addBinding(getLocalQueueBinding(randomUUIDSimpleString(), RoutingType.ANYCAST));
      am.addBinding(getLocalQueueBinding(randomUUIDSimpleString(), RoutingType.ANYCAST));

      assertNull(am.getMatchingQueue(randomUUIDSimpleString(), RoutingType.ANYCAST));
   }

   @Test
   public void testIncorrectNameIncorrectRoutingTypeSingleQueue() throws Exception {
      SimpleAddressManager am = getSimpleAddressManager();
      am.addBinding(getLocalQueueBinding(randomUUIDSimpleString(), RoutingType.MULTICAST));

      assertNull(am.getMatchingQueue(randomUUIDSimpleString(), RoutingType.ANYCAST));
   }

   @Test
   public void testIncorrectNameIncorrectRoutingTypeMultipleQueues() throws Exception {
      SimpleAddressManager am = getSimpleAddressManager();
      am.addBinding(getLocalQueueBinding(randomUUIDSimpleString(), RoutingType.MULTICAST));
      am.addBinding(getLocalQueueBinding(randomUUIDSimpleString(), RoutingType.MULTICAST));

      assertNull(am.getMatchingQueue(randomUUIDSimpleString(), RoutingType.ANYCAST));
   }

   private static SimpleAddressManager getSimpleAddressManager() throws Exception {
      return new SimpleAddressManager(getBindingsFactory(), new WildcardConfiguration(), null, null);
   }

   private static LocalQueueBinding getLocalQueueBinding(SimpleString addressName, RoutingType routingType) {
      return getLocalQueueBinding(addressName, randomUUIDSimpleString(), routingType);
   }

   private static LocalQueueBinding getLocalQueueBinding(SimpleString addressName, SimpleString queueName, RoutingType routingType) {
      return new LocalQueueBinding(addressName, getQueue(queueName, routingType), randomUUIDSimpleString());
   }

   private static BindingsFactory getBindingsFactory() throws Exception {
      List<Binding> bindingsList = new ArrayList<>();
      Bindings bindings = mock(Bindings.class);
      doAnswer(invocation -> {
         bindingsList.add(invocation.getArgument(0));
         return null;
      }).when(bindings).addBinding(any(Binding.class));
      when(bindings.getBindings()).thenReturn(bindingsList);

      BindingsFactory bindingsFactory = mock(BindingsFactory.class);
      when(bindingsFactory.createBindings(any(SimpleString.class))).thenReturn(bindings);
      return bindingsFactory;
   }

   private static Queue getQueue(SimpleString queueName, RoutingType anycast) {
      Queue q = mock(Queue.class);
      when(q.getName()).thenReturn(queueName);
      when(q.getID()).thenReturn(1L);
      when(q.getRoutingType()).thenReturn(anycast);
      return q;
   }
}
