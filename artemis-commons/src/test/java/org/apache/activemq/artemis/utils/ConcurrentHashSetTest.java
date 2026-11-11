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
package org.apache.activemq.artemis.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Iterator;

import org.apache.activemq.artemis.utils.collections.ConcurrentHashSet;
import org.apache.activemq.artemis.utils.collections.ConcurrentSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ConcurrentHashSetTest {


   private ConcurrentSet<String> set;

   private String element;


   @Test
   public void testAdd() throws Exception {
      assertTrue(set.add(element));
      assertFalse(set.add(element));
   }

   @Test
   public void testAddIfAbsent() throws Exception {
      assertTrue(set.addIfAbsent(element));
      assertFalse(set.addIfAbsent(element));
   }

   @Test
   public void testRemove() throws Exception {
      assertTrue(set.add(element));

      assertTrue(set.remove(element));
      assertFalse(set.remove(element));
   }

   @Test
   public void testContains() throws Exception {
      assertFalse(set.contains(element));

      assertTrue(set.add(element));
      assertTrue(set.contains(element));

      assertTrue(set.remove(element));
      assertFalse(set.contains(element));
   }

   @Test
   public void testSize() throws Exception {
      assertEquals(0, set.size());

      assertTrue(set.add(element));
      assertEquals(1, set.size());

      assertTrue(set.remove(element));
      assertEquals(0, set.size());
   }

   @Test
   public void testClear() throws Exception {
      assertTrue(set.add(element));

      assertTrue(set.contains(element));
      set.clear();
      assertFalse(set.contains(element));
   }

   @Test
   public void testIsEmpty() throws Exception {
      assertTrue(set.isEmpty());

      assertTrue(set.add(element));
      assertFalse(set.isEmpty());

      set.clear();
      assertTrue(set.isEmpty());
   }

   @Test
   public void testIterator() throws Exception {
      set.add(element);

      Iterator<String> iterator = set.iterator();
      while (iterator.hasNext()) {
         String e = iterator.next();
         assertEquals(element, e);
      }
   }

   @BeforeEach
   public void setUp() throws Exception {
      set = new ConcurrentHashSet<>();
      element = RandomUtil.randomUUIDString();
   }
}
