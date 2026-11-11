/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.server.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.core.PriorityAware;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class QueueConsumersImplTest {

   private QueueConsumers<TestPriority> queueConsumers;

   @BeforeEach
   public void setUp() {
      queueConsumers = new QueueConsumersImpl<>();
   }

   @Test
   public void addTest() {
      TestPriority testPriority = new TestPriority("hello", 0);
      assertFalse(queueConsumers.hasNext());

      queueConsumers.add(testPriority);
      // not visible till reset
      assertFalse(queueConsumers.hasNext());

      queueConsumers.reset();
      assertTrue(queueConsumers.hasNext());

      assertEquals(testPriority, queueConsumers.next());
   }

   @Test
   public void removeTest() {
      TestPriority testPriority = new TestPriority("hello", 0);
      assertFalse(queueConsumers.hasNext());

      queueConsumers.add(testPriority);
      queueConsumers.reset();
      assertTrue(queueConsumers.hasNext());

      queueConsumers.remove(testPriority);
      queueConsumers.reset();
      assertFalse(queueConsumers.hasNext());

      assertEquals(0, queueConsumers.getPriorites().size());
      queueConsumers.remove(testPriority);
      queueConsumers.remove(testPriority);

   }



   @Test
   public void roundRobinTest() {
      queueConsumers.add(new TestPriority("A", 127));
      queueConsumers.add(new TestPriority("B", 127));
      queueConsumers.add(new TestPriority("E", 0));
      queueConsumers.add(new TestPriority("D", 20));
      queueConsumers.add(new TestPriority("C", 127));
      queueConsumers.reset();
      assertTrue(queueConsumers.hasNext());

      assertEquals("A", queueConsumers.next().getName());

      //Reset iterator should mark start as current position
      queueConsumers.reset();
      assertTrue(queueConsumers.hasNext());
      assertEquals("B", queueConsumers.next().getName());

      assertTrue(queueConsumers.hasNext());
      assertEquals("C", queueConsumers.next().getName());

      //Expect another A as after reset, we started at B so after A we then expect the next level
      assertTrue(queueConsumers.hasNext());
      assertEquals("A", queueConsumers.next().getName());

      assertTrue(queueConsumers.hasNext());
      assertEquals("D", queueConsumers.next().getName());

      assertTrue(queueConsumers.hasNext());
      assertEquals("E", queueConsumers.next().getName());

      //We have iterated all.
      assertFalse(queueConsumers.hasNext());

      //Reset to iterate again.
      queueConsumers.reset();

      //We expect the iteration to round robin from last returned at the level.
      assertTrue(queueConsumers.hasNext());
      assertEquals("B", queueConsumers.next().getName());


   }

   @Test
   public void roundRobinEqualPriorityResetTest() {
      queueConsumers.add(new TestPriority("A", 0));
      queueConsumers.add(new TestPriority("B", 0));
      queueConsumers.add(new TestPriority("C", 0));
      queueConsumers.reset();
      assertTrue(queueConsumers.hasNext());

      assertEquals("A", queueConsumers.next().getName());

      //Reset iterator should mark start as current position
      queueConsumers.reset();
      assertTrue(queueConsumers.hasNext());
      assertEquals("B", queueConsumers.next().getName());

      assertTrue(queueConsumers.hasNext());
      assertEquals("C", queueConsumers.next().getName());

      //Expect another A as after reset, we started at B so after A, we then expect the next level
      assertTrue(queueConsumers.hasNext());
      assertEquals("A", queueConsumers.next().getName());

      //We have iterated all.
      assertFalse(queueConsumers.hasNext());
   }




   private class TestPriority implements PriorityAware {

      private final int priority;
      private final String name;

      private TestPriority(String name, int priority) {
         this.priority = priority;
         this.name = name;
      }

      @Override
      public int getPriority() {
         return priority;
      }

      public String getName() {
         return name;
      }
   }

}