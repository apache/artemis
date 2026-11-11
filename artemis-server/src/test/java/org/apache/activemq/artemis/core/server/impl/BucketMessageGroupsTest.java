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
package org.apache.activemq.artemis.core.server.impl;

import static org.apache.activemq.artemis.core.server.impl.BucketMessageGroups.toGroupBucketIntKey;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Collection;

import org.junit.jupiter.api.Test;

public class BucketMessageGroupsTest {

   @Test
   public void testEnsureBucketCountHonoured() {
      //Test a range of bucket counts
      for (int count = 1; count < 100; count++) {
         MessageGroups<String> messageGroups = new BucketMessageGroups<>(count);
         //Use a range of keys
         for (int i = 0; i < 100; i++) {
            messageGroups.put(toGroupBucketIntKey(i), "world" + i);
         }
         assertEquals(count, messageGroups.size());
      }
   }

   @Test
   public void testBucketCountNotGreaterThanZero() {
      try {
         MessageGroups<String> messageGroups = new BucketMessageGroups<>(0);
         fail("IllegalArgumentException was expected as bucket count is NOT greater than 0");
      } catch (IllegalArgumentException iae) {
         //Pass we expect exception thrown if count is not greater than 0;
      }

      try {
         MessageGroups<String> messageGroups = new BucketMessageGroups<>(-1);
         fail("IllegalArgumentException was expected as bucket count is NOT greater than 0");
      } catch (IllegalArgumentException iae) {
         //Pass we expect exception thrown if count is not greater than 0;
      }
   }

   @Test
   public void testPut() {
      MessageGroups<String> messageGroups = new BucketMessageGroups<>(2);
      assertEquals(0, messageGroups.size());


      messageGroups.put(toGroupBucketIntKey(1), "world");

      assertEquals(1, messageGroups.size());


      messageGroups.put(toGroupBucketIntKey(2), "world2");

      assertEquals(2, messageGroups.size());

      //This as we have 2 buckets, max size will be 2 always
      messageGroups.put(toGroupBucketIntKey(3), "world3");
      assertEquals(2, messageGroups.size());
   }

   @Test
   public void testGet() {
      MessageGroups<String> messageGroups = new BucketMessageGroups<>(2);

      assertNull(messageGroups.get(toGroupBucketIntKey(1)));


      messageGroups.put(toGroupBucketIntKey(1), "world");

      assertEquals("world", messageGroups.get(toGroupBucketIntKey(1)));

      messageGroups.put(toGroupBucketIntKey(2), "world2");

      assertEquals("world2", messageGroups.get(toGroupBucketIntKey(2)));

      //This as we have 2 buckets, and key 3 will has mod into the same bucket as key 1, overwriting its value.
      messageGroups.put(toGroupBucketIntKey(3), "world3");
      assertEquals("world3", messageGroups.get(toGroupBucketIntKey(3)));

      //Ensure that on calling get for key 1, will return same value as key 3 now.
      assertEquals("world3", messageGroups.get(toGroupBucketIntKey(1)));

      //Ensure that negative hash's are made positive and bucket onto the expected bucket groups.
      assertEquals("world3", messageGroups.get(toGroupBucketIntKey(-1)));
      //Ensure that negative hash's are made positive and bucket onto the expected bucket groups.
      assertEquals("world2", messageGroups.get(toGroupBucketIntKey(-2)));
   }

   @Test
   public void testToMap() {
      MessageGroups<String> messageGroups = new BucketMessageGroups<>(2);

      messageGroups.put(toGroupBucketIntKey(1), "world");

      assertEquals(1, messageGroups.toMap().size());

      messageGroups.put(toGroupBucketIntKey(2), "world2");

      assertEquals(2, messageGroups.toMap().size());

      Collection<String> values = messageGroups.toMap().values();
      assertTrue(values.contains("world"));
      assertTrue(values.contains("world2"));

      messageGroups.put(toGroupBucketIntKey(3), "world3");
      messageGroups.put(toGroupBucketIntKey(4), "world4");

      values = messageGroups.toMap().values();
      assertFalse(values.contains("world"));
      assertFalse(values.contains("world2"));
      assertTrue(values.contains("world3"));
      assertTrue(values.contains("world4"));
   }

   @Test
   public void testRemove() {
      MessageGroups<String> messageGroups = new BucketMessageGroups<>(2);
      assertNull(messageGroups.remove(toGroupBucketIntKey(1)));

      messageGroups.put(toGroupBucketIntKey(1), "world");

      assertEquals("world", messageGroups.remove(toGroupBucketIntKey(1)));
      assertNull(messageGroups.remove(toGroupBucketIntKey(1)));

      messageGroups.put(toGroupBucketIntKey(1), "world1");
      messageGroups.put(toGroupBucketIntKey(2), "world2");
      messageGroups.put(toGroupBucketIntKey(3), "world3");
      messageGroups.put(toGroupBucketIntKey(4), "world4");
      messageGroups.put(toGroupBucketIntKey(5), "world5");

      assertEquals(2, messageGroups.size());


      assertEquals("world5", messageGroups.remove(toGroupBucketIntKey(3)));
      assertNull(messageGroups.remove(toGroupBucketIntKey(5)));
      assertEquals(1, messageGroups.size());

      assertEquals("world4", messageGroups.remove(toGroupBucketIntKey(4)));
      assertEquals(0, messageGroups.size());

   }

   @Test
   public void testRemoveIf() {
      MessageGroups<String> messageGroups = new BucketMessageGroups<>(10);

      messageGroups.put(toGroupBucketIntKey(1), "world1");
      messageGroups.put(toGroupBucketIntKey(2), "world2");
      messageGroups.put(toGroupBucketIntKey(3), "world1");
      messageGroups.put(toGroupBucketIntKey(4), "world2");
      messageGroups.put(toGroupBucketIntKey(5), "world1");
      messageGroups.put(toGroupBucketIntKey(6), "world2");
      messageGroups.put(toGroupBucketIntKey(7), "world1");
      messageGroups.put(toGroupBucketIntKey(8), "world2");
      messageGroups.put(toGroupBucketIntKey(9), "world3");
      messageGroups.put(toGroupBucketIntKey(10), "world4");

      assertEquals(10, messageGroups.size());

      messageGroups.removeIf("world1"::equals);

      assertEquals(6, messageGroups.size());

      messageGroups.removeIf("world4"::equals);

      assertEquals(5, messageGroups.size());

      messageGroups.removeIf("world3"::equals);

      assertEquals(4, messageGroups.size());

      messageGroups.removeIf("world2"::equals);

      assertEquals(0, messageGroups.size());

   }

}