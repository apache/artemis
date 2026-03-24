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

import java.util.Iterator;

import org.apache.activemq.artemis.utils.collections.ConcurrentHashSet;
import org.apache.activemq.artemis.utils.collections.ConcurrentSet;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ConcurrentHashSetTest extends Assert {


   private ConcurrentSet<String> set;

   private String element;


   @Test
   public void testAdd() throws Exception {
      Assert.assertTrue(set.add(element));
      Assert.assertFalse(set.add(element));
   }

   @Test
   public void testAddIfAbsent() throws Exception {
      Assert.assertTrue(set.addIfAbsent(element));
      Assert.assertFalse(set.addIfAbsent(element));
   }

   @Test
   public void testRemove() throws Exception {
      Assert.assertTrue(set.add(element));

      Assert.assertTrue(set.remove(element));
      Assert.assertFalse(set.remove(element));
   }

   @Test
   public void testContains() throws Exception {
      Assert.assertFalse(set.contains(element));

      Assert.assertTrue(set.add(element));
      Assert.assertTrue(set.contains(element));

      Assert.assertTrue(set.remove(element));
      Assert.assertFalse(set.contains(element));
   }

   @Test
   public void testSize() throws Exception {
      Assert.assertEquals(0, set.size());

      Assert.assertTrue(set.add(element));
      Assert.assertEquals(1, set.size());

      Assert.assertTrue(set.remove(element));
      Assert.assertEquals(0, set.size());
   }

   @Test
   public void testClear() throws Exception {
      Assert.assertTrue(set.add(element));

      Assert.assertTrue(set.contains(element));
      set.clear();
      Assert.assertFalse(set.contains(element));
   }

   @Test
   public void testIsEmpty() throws Exception {
      Assert.assertTrue(set.isEmpty());

      Assert.assertTrue(set.add(element));
      Assert.assertFalse(set.isEmpty());

      set.clear();
      Assert.assertTrue(set.isEmpty());
   }

   @Test
   public void testIterator() throws Exception {
      set.add(element);

      Iterator<String> iterator = set.iterator();
      while (iterator.hasNext()) {
         String e = iterator.next();
         Assert.assertEquals(element, e);
      }
   }

   @Before
   public void setUp() throws Exception {
      set = new ConcurrentHashSet<>();
      element = RandomUtil.randomString();
   }
}
