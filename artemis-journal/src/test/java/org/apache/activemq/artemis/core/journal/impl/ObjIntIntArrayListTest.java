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
package org.apache.activemq.artemis.core.journal.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

public class ObjIntIntArrayListTest {

   @Test
   public void addShouldAppendTuplesInOrder() {
      List<Integer> expectedAList = new ArrayList<>();
      List<Integer> expectedBList = new ArrayList<>();
      List<Integer> expectedCList = new ArrayList<>();
      ObjIntIntArrayList<Integer> list = new ObjIntIntArrayList<>(0);
      final int elementsToAdd = 10;
      assertEquals(0, list.size());
      for (int i = 0; i < elementsToAdd; i++) {
         Integer a = i;
         Integer b = i + 1;
         Integer c = i + 2;
         list.add(a, b, c);
         assertEquals(i + 1, list.size());
         expectedAList.add(a);
         expectedBList.add(b);
         expectedCList.add(c);
      }
      List<Integer> aList = new ArrayList<>();
      List<Integer> bList = new ArrayList<>();
      List<Integer> cList = new ArrayList<>();
      final Object expectedArg = new Object();
      list.forEach((a, b, c, arg) -> {
         aList.add(a);
         bList.add(b);
         cList.add(c);
         assertSame(expectedArg, arg);
      }, expectedArg);
      assertEquals(expectedAList, aList);
      assertEquals(expectedBList, bList);
      assertEquals(expectedCList, cList);
   }

   @Test
   public void addShouldFailAppendNull() {
      assertThrows(NullPointerException.class, () -> {
         ObjIntIntArrayList<Integer> list = new ObjIntIntArrayList<>(0);
         list.add(null, 1, 2);
      });
   }

   @Test
   public void addShouldFailAppendNegativeA() {
      assertThrows(IllegalArgumentException.class, () -> {
         ObjIntIntArrayList<Integer> list = new ObjIntIntArrayList<>(0);
         list.add(0, -1, 1);
      });
   }

   @Test
   public void addShouldFailAppendNegativeB() {
      assertThrows(IllegalArgumentException.class, () -> {
         ObjIntIntArrayList<Integer> list = new ObjIntIntArrayList<>(0);
         list.add(0, 1, -1);
      });
   }

   @Test
   public void clearShouldDeleteAllElements() {
      ObjIntIntArrayList<Integer> list = new ObjIntIntArrayList<>(0);
      list.add(1, 3, 3);
      assertEquals(1, list.size());
      list.clear();
      assertEquals(0, list.size());
      list.forEach((a, b, c, ignored) -> {
         fail("the list should be empty");
      }, null);
   }

   @Test
   public void updateIfMatchShouldReturnFalseOnMissingElement() {
      ObjIntIntArrayList<Integer> list = new ObjIntIntArrayList<>(0);
      Integer e = 1;
      list.add(e, 2, 3);
      assertFalse(list.addToIntsIfMatch(0, 2, 1, 1));
      list.forEach((a, b, c, ignored) -> {
         assertEquals(e, a);
         assertEquals(2, b);
         assertEquals(3, c);
      }, null);
   }

   @Test
   public void updateIfMatchShouldReturnFalseOnOverflowOfA() {
      ObjIntIntArrayList<Integer> list = new ObjIntIntArrayList<>(0);
      Integer e = 1;
      list.add(e, Integer.MAX_VALUE, 3);
      assertFalse(list.addToIntsIfMatch(0, e, Integer.MAX_VALUE, 1));
      list.forEach((a, b, c, ignored) -> {
         assertEquals(e, a);
         assertEquals(Integer.MAX_VALUE, b);
         assertEquals(3, c);
      }, null);
   }

   @Test
   public void updateIfMatchShouldReturnFalseOnOverflowOfB() {
      ObjIntIntArrayList<Integer> list = new ObjIntIntArrayList<>(0);
      Integer e = 1;
      list.add(e, 2, Integer.MAX_VALUE);
      assertFalse(list.addToIntsIfMatch(0, e, 1, Integer.MAX_VALUE));
      list.forEach((a, b, c, ignored) -> {
         assertEquals(e, a);
         assertEquals(2, b);
         assertEquals(Integer.MAX_VALUE, c);
      }, null);
   }

   @Test
   public void updateIfMatchShouldFailOnNegativeIndex() {
      assertThrows(IndexOutOfBoundsException.class, () -> {
         ObjIntIntArrayList<Integer> list = new ObjIntIntArrayList<>(0);
         list.addToIntsIfMatch(-1, 1, 1, 1);
      });
   }

   @Test
   public void updateIfMatchShouldFailBeyondSize() {
      assertThrows(IndexOutOfBoundsException.class, () -> {
         ObjIntIntArrayList<Integer> list = new ObjIntIntArrayList<>(0);
         list.addToIntsIfMatch(0, 1, 1, 1);
      });
   }

   @Test
   public void updateIfMatchShouldFailOnNull() {
      assertThrows(NullPointerException.class, () -> {
         ObjIntIntArrayList<Integer> list = new ObjIntIntArrayList<>(0);
         list.add(1, 2, 3);
         list.addToIntsIfMatch(0, null, 1, 1);
      });
   }

   @Test
   public void updateIfMatchShouldFailOnNegativeDeltaA() {
      assertThrows(IllegalArgumentException.class, () -> {
         ObjIntIntArrayList<Integer> list = new ObjIntIntArrayList<>(0);
         Integer e = 1;
         list.add(1, 2, 3);
         list.addToIntsIfMatch(0, e, -1, 1);
      });
   }

   @Test
   public void updateIfMatchShouldFailOnNegativeDeltaB() {
      assertThrows(IllegalArgumentException.class, () -> {
         ObjIntIntArrayList<Integer> list = new ObjIntIntArrayList<>(0);
         Integer e = 1;
         list.add(e, 2, 3);
         list.addToIntsIfMatch(0, e, 1, -1);
      });
   }

   @Test
   public void updateIfMatchShouldModifyExistingTuple() {
      ObjIntIntArrayList<Integer> list = new ObjIntIntArrayList<>(0);
      list.add(1, 2, 3);
      list.add(2, 3, 4);
      list.add(3, 4, 5);
      assertTrue(list.addToIntsIfMatch(0, 1, 1, 1));
      assertTrue(list.addToIntsIfMatch(1, 2, 1, 1));
      assertTrue(list.addToIntsIfMatch(2, 3, 1, 1));
      assertEquals(3, list.size());
      List<Integer> eList = new ArrayList<>();
      List<Integer> aList = new ArrayList<>();
      List<Integer> bList = new ArrayList<>();
      list.forEach((e, a, b, ignore) -> {
         eList.add(e);
         aList.add(a);
         bList.add(b);
      }, null);
      assertEquals(3, eList.size());
      assertEquals(3, aList.size());
      assertEquals(3, bList.size());
      for (int i = 0; i < 3; i++) {
         final int index = i;
         final Integer expectedE = index + 1;
         final Integer expectedA = expectedE + 2;
         final Integer expectedB = expectedE + 3;
         assertEquals(expectedE, eList.get(index));
         assertEquals(expectedA, aList.get(index));
         assertEquals(expectedB, bList.get(index));
      }
   }

}
