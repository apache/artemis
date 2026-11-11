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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

public class SparseArrayLinkedListTest {

   private static final int SPARSE_ARRAY_CAPACITY = 4;
   private static final int ELEMENTS = SPARSE_ARRAY_CAPACITY * 4;

   private final SparseArrayLinkedList<Integer> list;

   public SparseArrayLinkedListTest() {
      list = new SparseArrayLinkedList<>(SPARSE_ARRAY_CAPACITY);
   }

   @Test
   public void shouldFailToCreateZeroArrayCapacityCollection() {
      assertThrows(IllegalArgumentException.class, () -> {
         new SparseArrayLinkedList<>(0);
      });
   }

   @Test
   public void shouldFailToCreateNegativeArrayCapacityCollection() {
      assertThrows(IllegalArgumentException.class, () -> {
         new SparseArrayLinkedList<>(-1);
      });
   }

   @Test
   public void shouldFailToAddNull() {
      assertThrows(NullPointerException.class, () -> {
         list.add(null);
      });
   }

   @Test
   public void shouldNumberOfElementsBeTheSameOfTheAddedElements() {
      final int elements = ELEMENTS;
      for (int i = 0; i < elements; i++) {
         assertEquals(i, list.size());
         list.add(i);
      }
      assertEquals(elements, list.size());
   }

   @Test
   public void shouldClearConsumeElementsInOrder() {
      final int elements = ELEMENTS;
      assertEquals(0, list.clear(null));
      final ArrayList<Integer> expected = new ArrayList<>(elements);
      for (int i = 0; i < elements; i++) {
         final Integer added = i;
         list.add(added);
         expected.add(added);
      }
      final List<Integer> removed = new ArrayList<>(elements);
      assertEquals(elements, list.clear(removed::add));
      assertEquals(1, list.sparseArraysCount());
      assertEquals(0, list.size());
      assertEquals(expected, removed);
   }

   @Test
   public void shouldRemoveMatchingElements() {
      final int elements = ELEMENTS;
      for (int i = 0; i < elements; i++) {
         list.add(i);
      }
      assertEquals(1, list.remove(e -> e.intValue() == 0));
      assertEquals(elements - 1, list.size());
      assertEquals(0, list.remove(e -> e.intValue() == 0));
      assertEquals(elements - 1, list.size());
      assertEquals(elements - 1, list.remove(e -> true));
      assertEquals(0, list.size());
      assertEquals(1, list.sparseArraysCount());
      assertEquals(0, list.remove(e -> true));
      assertEquals(1, list.sparseArraysCount());
   }

   @Test
   public void shouldRemoveDetachSparseArrays() {
      final int elements = list.sparseArrayCapacity() * 3;
      for (int i = 0; i < elements; i++) {
         list.add(i);
      }
      // remove elements in the middle
      final int startInclusiveMiddle = list.sparseArrayCapacity();
      final int endNotInclusiveMiddle = startInclusiveMiddle + list.sparseArrayCapacity();
      assertEquals(list.sparseArrayCapacity(),
                          list.remove(e -> e.intValue() >= startInclusiveMiddle && e.intValue() < endNotInclusiveMiddle));
      assertEquals(2, list.sparseArraysCount());
      // remove elements at the beginning
      final int startInclusiveFirst = 0;
      final int endNotInclusiveFirst = startInclusiveMiddle;
      assertEquals(list.sparseArrayCapacity(),
                          list.remove(e -> e.intValue() >= startInclusiveFirst && e.intValue() < endNotInclusiveFirst));
      assertEquals(1, list.sparseArraysCount());
      // remove all elements at the end
      final int startInclusiveLast = endNotInclusiveMiddle;
      final int endNotInclusiveLast = elements;
      assertEquals(list.sparseArrayCapacity(),
                          list.remove(e -> e.intValue() >= startInclusiveLast && e.intValue() < endNotInclusiveLast));
      assertEquals(1, list.sparseArraysCount());
   }

   @Test
   public void shouldAddAfterRemoveAtTheEndReusingTheAvailableSpace() {
      final int elements = list.sparseArrayCapacity();
      for (int i = 0; i < elements; i++) {
         list.add(i);
      }
      assertEquals(1, list.sparseArraysCount());
      // removing last element
      assertEquals(1, list.remove(e -> e.intValue() == elements - 1));
      list.add(elements - 1);
      assertEquals(1, list.sparseArraysCount());
      assertEquals(1, list.remove(e -> e.intValue() == 0));
      list.add(elements);
      assertEquals(2, list.sparseArraysCount());
   }

   @Test
   public void shouldReuseAllTheAvailableSpaceInTheSameArray() {
      final int elements = list.sparseArrayCapacity();
      for (int i = 0; i < elements; i++) {
         list.add(i);
      }
      assertEquals(1, list.sparseArraysCount());
      // removing all elements
      assertEquals(elements, list.remove(e -> true));
      assertEquals(1, list.sparseArraysCount());
      for (int i = 0; i < elements; i++) {
         list.add(i);
      }
      assertEquals(1, list.sparseArraysCount());
   }

   @Test
   public void shouldClearConsumeRemainingElementsInOrder() {
      final int elements = ELEMENTS;
      final Integer zero = 0;
      list.add(zero);
      for (int i = 1; i < elements; i++) {
         list.add(i);
      }
      assertEquals(elements - 1, list.remove(e -> !zero.equals(e)));
      final ArrayList<Integer> remaining = new ArrayList<>();
      assertEquals(1, list.clear(remaining::add));
      assertEquals(0, list.size());
      assertEquals(1, remaining.size());
      assertEquals(zero, remaining.get(0));
   }

}
