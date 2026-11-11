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
package org.apache.activemq.artemis.core.server.routing.policies;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.core.server.routing.KeyResolver;
import org.junit.jupiter.api.Test;

public class ConsistentHashModuloPolicyTest {

   @Test
   public void transformKey() {
      ConsistentHashModuloPolicy underTest = new ConsistentHashModuloPolicy();

      assertEquals(KeyResolver.NULL_KEY_VALUE, underTest.transformKey(KeyResolver.NULL_KEY_VALUE));

      assertEquals("AA", underTest.transformKey("AA")); // default modulo 0 does nothing

      Map<String, String> properties = new HashMap<>();

      final int modulo = 2;
      properties.put(ConsistentHashModuloPolicy.MODULO, String.valueOf(modulo));
      underTest.init(properties);

      String hash1 = underTest.transformKey("AAA");
      int v1 = Integer.parseInt(hash1);

      String hash2 = underTest.transformKey("BBB");
      int v2 = Integer.parseInt(hash2);

      assertNotEquals(hash1, hash2);
      assertNotEquals(v1, v2);
      assertTrue(v1 < modulo && v2 < modulo);
   }

   @Test
   public void transformKeyNotNegative() {
      ConsistentHashModuloPolicy underTest = new ConsistentHashModuloPolicy();
      Map<String, String> properties = new HashMap<>();
      final int modulo = 2;
      properties.put(ConsistentHashModuloPolicy.MODULO, String.valueOf(modulo));
      underTest.init(properties);

      assertNotNull(underTest.getProperties());

      String[] values = new String[]{"ONE", "TWO", "THREE", "FOUR"};
      for (String v : values) {
         assertTrue(Integer.parseInt(underTest.transformKey(v)) >= 0, "non negative for: " + v);
      }
   }

   @Test
   public void transformKeyNotNegativeWithExplicitNegativeHash() {
      final int[] negs = {-1, Integer.MAX_VALUE, Integer.MIN_VALUE, 100, 500, 22, 2, 1};
      ConsistentHashModuloPolicy underTest = new ConsistentHashModuloPolicy() {
         int v = 0;
         @Override
         protected int getHash(String str) {
            return negs[v++ % negs.length];
         }
      };
      Map<String, String> properties = new HashMap<>();
      final int modulo = 2;
      properties.put(ConsistentHashModuloPolicy.MODULO, String.valueOf(modulo));
      underTest.init(properties);

      assertNotNull(underTest.getProperties());

      for (int i = 0; i < negs.length; i++) {
         assertTrue(Integer.parseInt(underTest.transformKey("BLA")) >= 0, "non negative for: " + i);
      }
   }
}
