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
package org.apache.activemq.artemis.utils.collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

public class NoOpMapTest {

   @Test
   public void testPut() {
      Map<String, String> map = NoOpMap.instance();
      assertNull(map.put("hello", "world"));
      assertNull(map.put("hello", "world2"));

      assertEquals(0, map.size());
   }

   @Test
   public void testGet() {
      Map<String, String> map = NoOpMap.instance();
      map.put("hello", "world");

      assertNull(map.get("hello"));
   }

   @Test
   public void testValues() {
      Map<String, String> map = NoOpMap.instance();
      map.put("hello", "world");

      Collection<String> values = map.values();

      assertEquals(0, values.size());
   }

   @Test
   public void testKeys() {
      Map<String, String> map = NoOpMap.instance();
      map.put("hello", "world");

      Set<String> keySet = map.keySet();

      assertEquals(0, keySet.size());
   }

   @Test
   public void testEntrySet() {
      Map<String, String> map = NoOpMap.instance();
      map.put("hello", "world");

      Set<Map.Entry<String, String>> entrySet = map.entrySet();

      assertEquals(0, entrySet.size());
   }


   @Test
   public void testIsEmpty() {
      Map<String, String> map = NoOpMap.instance();
      map.put("hello", "world");

      assertTrue(map.isEmpty());
   }

   @Test
   public void testRemove() {
      Map<String, String> map = NoOpMap.instance();
      map.put("hello", "world");

      assertNull(map.remove("hello"));
   }

   @Test
   public void testReplace() {
      Map<String, String> map = NoOpMap.instance();
      map.put("hello", "world");

      assertNull(map.replace("hello", "world2"));

      map.put("hello", "world");

      assertFalse(map.replace("hello", "world", "world2"));
   }

}