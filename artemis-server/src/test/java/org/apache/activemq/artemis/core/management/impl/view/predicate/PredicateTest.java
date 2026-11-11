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
package org.apache.activemq.artemis.core.management.impl.view.predicate;

import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.Test;

import static org.apache.activemq.artemis.core.management.impl.view.predicate.ActiveMQFilterPredicate.Operation.CONTAINS;
import static org.apache.activemq.artemis.core.management.impl.view.predicate.ActiveMQFilterPredicate.Operation.EQUALS;
import static org.apache.activemq.artemis.core.management.impl.view.predicate.ActiveMQFilterPredicate.Operation.GREATER_THAN;
import static org.apache.activemq.artemis.core.management.impl.view.predicate.ActiveMQFilterPredicate.Operation.LESS_THAN;
import static org.apache.activemq.artemis.core.management.impl.view.predicate.ActiveMQFilterPredicate.Operation.NOT_CONTAINS;
import static org.apache.activemq.artemis.core.management.impl.view.predicate.ActiveMQFilterPredicate.Operation.NOT_EQUALS;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PredicateTest {

   @Test
   public void testBasePredicateEquals() {
      String string = RandomUtil.randomUUIDString();
      PredicateFilterPart<String> predicate = new PredicateFilterPart<>(EQUALS.name(), string);
      assertTrue(predicate.matches(string));
      assertFalse(predicate.matches(RandomUtil.randomUUIDString()));
      assertFalse(predicate.matchesLong(0L));
      assertFalse(predicate.matchesFloat(0f));
      assertFalse(predicate.matchesInt(0));
   }

   @Test
   public void testBasePredicateNotEquals() {
      String string = RandomUtil.randomUUIDString();
      PredicateFilterPart<String> predicate = new PredicateFilterPart<>(NOT_EQUALS.name(), string);
      assertFalse(predicate.matches(string));
      assertTrue(predicate.matches(RandomUtil.randomUUIDString()));
      assertTrue(predicate.matchesLong(0L));
      assertTrue(predicate.matchesFloat(0f));
      assertTrue(predicate.matchesInt(0));
   }

   @Test
   public void testBasePredicateContains() {
      PredicateFilterPart<String> predicate = new PredicateFilterPart<>(CONTAINS.name(), "12");
      assertTrue(predicate.matches("0123"));
      assertFalse(predicate.matches("43"));
      assertFalse(predicate.matchesLong(0L));
      assertFalse(predicate.matchesFloat(0f));
      assertFalse(predicate.matchesInt(0));
   }

   @Test
   public void testBasePredicateNotContains() {
      PredicateFilterPart<String> predicate = new PredicateFilterPart<>(NOT_CONTAINS.name(), "12");
      assertFalse(predicate.matches("0123"));
      assertTrue(predicate.matches("42"));
      assertTrue(predicate.matchesLong(0L));
      assertTrue(predicate.matchesFloat(0f));
      assertTrue(predicate.matchesInt(0));
   }

   @Test
   public void testBasePredicateLessThan() {
      PredicateFilterPart<Integer> predicate = new PredicateFilterPart<>(LESS_THAN.name(), "12");
      assertFalse(predicate.matches("foo"));
      assertFalse(predicate.matchesInt(42));
      assertTrue(predicate.matchesLong(0L));
      assertTrue(predicate.matchesFloat(0f));
      assertTrue(predicate.matchesInt(0));
   }

   @Test
   public void testBasePredicateGreaterThan() {
      PredicateFilterPart<Integer> predicate = new PredicateFilterPart<>(GREATER_THAN.name(), "12");
      assertFalse(predicate.matches("foo"));
      assertTrue(predicate.matchesInt(42));
      assertFalse(predicate.matchesLong(0L));
      assertFalse(predicate.matchesFloat(0f));
      assertFalse(predicate.matchesInt(0));
   }
}
