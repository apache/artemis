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
package org.apache.activemq.artemis.tests.unit.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.UUID;
import org.apache.activemq.artemis.utils.UUIDGenerator;
import org.junit.jupiter.api.Test;

public class UUIDTest extends ActiveMQTestBase {

   static final int MANY_TIMES = 100000;

   @Test
   public void testManyUUIDs() throws Exception {
      Set<String> uuidsSet = new HashSet<>();

      UUIDGenerator gen = UUIDGenerator.getInstance();
      for (int i = 0; i < getTimes(); i++) {
         uuidsSet.add(gen.generateStringUUID());
      }

      // we put them in a set to check duplicates
      assertEquals(getTimes(), uuidsSet.size());
   }

   protected int getTimes() {
      return MANY_TIMES;
   }

   @Test
   public void testStringToUuidConversion() {
      UUIDGenerator gen = UUIDGenerator.getInstance();
      for (int i = 0; i < MANY_TIMES; i++) {
         final UUID uuid = gen.generateUUID();
         final String uuidString = uuid.toString();
         byte[] data2 = UUID.stringToBytes(uuidString);
         final UUID uuid2 = new UUID(UUID.TYPE_TIME_BASED, data2);
         assertEqualsByteArrays(uuid.asBytes(), data2);
         assertEquals(uuid, uuid2, uuidString);
         assertEquals(uuidString, uuid2.toString(), uuidString);
      }
   }

   @Test
   public void testIsUUID() {
      String id;
      for (int i = 0; i < 10000; i++) {
         id = UUIDGenerator.getInstance().generateStringUUID();
         assertTrue(UUID.isUUID(id), "Invalid id: " + id);
         id = java.util.UUID.randomUUID().toString();
         assertTrue(UUID.isUUID(id), "Invalid id: " + id);
      }

      // contains special character
      assertFalse(UUID.isUUID(SimpleString.of("35a8cb5e-1baa-11f0-b57f-3ce1a1d1293!")));

      // contains upper-case hex character
      assertFalse(UUID.isUUID(SimpleString.of("35a8cb5e-1bAa-11f0-b57f-3ce1a1d12934")));

      // correct length but missing first hyphen
      assertFalse(UUID.isUUID(SimpleString.of("35a8cb5e1baa-11f0-b57f-3ce1a1d12934a")));

      // correct length but missing second hyphen
      assertFalse(UUID.isUUID(SimpleString.of("35a8cb5e-1baa11f0-b57f-3ce1a1d12934a")));

      // correct length but missing third hyphen
      assertFalse(UUID.isUUID(SimpleString.of("35a8cb5e-1baa-11f0b57f-3ce1a1d12934a")));

      // correct length but missing fourth hyphen
      assertFalse(UUID.isUUID(SimpleString.of("35a8cb5e-1baa-11f0-b57f3ce1a1d12934a")));

      // too long
      assertFalse(UUID.isUUID(SimpleString.of("35a8cb5e-1baa-11f0-b57f-3ce1a1d12934a")));

      // too short
      assertFalse(UUID.isUUID(SimpleString.of("35a8cb5e-1baa-11f0-b57f-3ce1a1d1293")));

      // null
      assertFalse(UUID.isUUID(null));
   }

   @Test
   public void testStringTrailingUUID() {
      SimpleString uuid = RandomUtil.randomUUIDSimpleString();
      assertEquals(uuid, UUID.stripTrailingUUID(uuid.concat(RandomUtil.randomUUIDSimpleString())));

      SimpleString foo = SimpleString.of("foo");
      assertEquals(foo, UUID.stripTrailingUUID(foo));
   }
}
