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
package org.apache.activemq.artemis.api.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.utils.CompositeAddress;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.Test;

public class QueueConfigurationTest {

   @Test
   public void testSetGroupRebalancePauseDispatch() {
      QueueConfiguration queueConfiguration = QueueConfiguration.of("TEST");

      assertNull(queueConfiguration.isGroupRebalancePauseDispatch());

      queueConfiguration.setGroupRebalancePauseDispatch(true);
      assertNotNull(queueConfiguration.isGroupRebalancePauseDispatch());
      assertTrue(queueConfiguration.isGroupRebalancePauseDispatch());

      queueConfiguration.setGroupRebalancePauseDispatch(false);
      assertNotNull(queueConfiguration.isGroupRebalancePauseDispatch());
      assertFalse(queueConfiguration.isGroupRebalancePauseDispatch());

      queueConfiguration.set(QueueConfiguration.GROUP_REBALANCE_PAUSE_DISPATCH, Boolean.toString(true));
      assertNotNull(queueConfiguration.isGroupRebalancePauseDispatch());
      assertTrue(queueConfiguration.isGroupRebalancePauseDispatch());

      queueConfiguration.set(QueueConfiguration.GROUP_REBALANCE_PAUSE_DISPATCH, Boolean.toString(false));
      assertNotNull(queueConfiguration.isGroupRebalancePauseDispatch());
      assertFalse(queueConfiguration.isGroupRebalancePauseDispatch());
   }

   @Test
   public void testFqqn() {
      final SimpleString ADDRESS = RandomUtil.randomUUIDSimpleString();
      final SimpleString QUEUE = RandomUtil.randomUUIDSimpleString();
      QueueConfiguration queueConfiguration = QueueConfiguration.of(CompositeAddress.toFullyQualified(ADDRESS, QUEUE));
      assertEquals(ADDRESS, queueConfiguration.getAddress());
      assertEquals(QUEUE, queueConfiguration.getName());
      assertTrue(queueConfiguration.isFqqn());
   }

   @Test
   public void testFqqnNegative() {
      final SimpleString ADDRESS = RandomUtil.randomUUIDSimpleString();
      final SimpleString QUEUE = RandomUtil.randomUUIDSimpleString();
      QueueConfiguration queueConfiguration = QueueConfiguration.of(QUEUE).setAddress(ADDRESS);
      assertEquals(ADDRESS, queueConfiguration.getAddress());
      assertEquals(QUEUE, queueConfiguration.getName());
      assertFalse(queueConfiguration.isFqqn());
   }

   @Test
   public void testFqqnViaAddress() {
      final SimpleString ADDRESS = RandomUtil.randomUUIDSimpleString();
      final SimpleString QUEUE = RandomUtil.randomUUIDSimpleString();
      QueueConfiguration queueConfiguration = QueueConfiguration.of(RandomUtil.randomUUIDSimpleString()).setAddress(CompositeAddress.toFullyQualified(ADDRESS, QUEUE));
      assertEquals(ADDRESS, queueConfiguration.getAddress());
      assertEquals(QUEUE, queueConfiguration.getName());
      assertTrue(queueConfiguration.isFqqn());
   }
}
