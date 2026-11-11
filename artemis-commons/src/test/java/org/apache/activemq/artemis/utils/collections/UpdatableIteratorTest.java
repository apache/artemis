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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

public class UpdatableIteratorTest {

   @Test
   public void testUnderlyingIterator() {

      List<Integer> arrayList = new ArrayList<>();
      for (int i = 0; i < 1000; i++) {
         arrayList.add(i);
      }

      UpdatableIterator<Integer> iterator = new UpdatableIterator(ArrayResettableIterator.iterator(arrayList));
      for (int i = 0; i < 1000; i++) {
         assertTrue(iterator.hasNext());
         assertEquals(Integer.valueOf(i), iterator.next());
      }
      assertFalse(iterator.hasNext());

      iterator.reset();

      for (int i = 0; i < 1000; i++) {
         assertTrue(iterator.hasNext());
         assertEquals(Integer.valueOf(i), iterator.next());
      }
      assertFalse(iterator.hasNext());
   }

   @Test
   public void testUpdateIterator() {

      List<Integer> arrayList = new ArrayList<>();
      for (int i = 0; i < 1000; i++) {
         arrayList.add(i);
      }

      List<Integer> arrayList2 = new ArrayList<>();
      for (int i = 4000; i < 5000; i++) {
         arrayList2.add(i);
      }

      UpdatableIterator<Integer> iterator = new UpdatableIterator(ArrayResettableIterator.iterator(arrayList));
      for (int i = 0; i < 100; i++) {
         assertTrue(iterator.hasNext());
         assertEquals(Integer.valueOf(i), iterator.next());
      }

      //Update the iterator
      iterator.update(ArrayResettableIterator.iterator(arrayList2));

      //Ensure the current iterator in use is not updated until reset, and we iterate remaining.
      for (int i = 100; i < 1000; i++) {
         assertTrue(iterator.hasNext());
         assertEquals(Integer.valueOf(i), iterator.next());
      }
      assertFalse(iterator.hasNext());

      //Reset the iterator, we now expect to act on the updated iterator.
      iterator.reset();

      for (int i = 4000; i < 5000; i++) {
         assertTrue(iterator.hasNext());
         assertEquals(Integer.valueOf(i), iterator.next());
      }
      assertFalse(iterator.hasNext());
   }
}