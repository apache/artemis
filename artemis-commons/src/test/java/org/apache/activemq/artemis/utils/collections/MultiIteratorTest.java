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
import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.Test;

public class MultiIteratorTest {

   @Test
   public void testSingleIterator() {

      List<Integer> arrayList = new ArrayList<>();
      for (int i = 0; i < 1000; i++) {
         arrayList.add(i);
      }

      MultiIterator<Integer> iterator = new MultiIterator<>(new Iterator[]{arrayList.iterator()});
      for (int i = 0; i < 1000; i++) {
         assertTrue(iterator.hasNext());
         assertEquals(Integer.valueOf(i), iterator.next());
      }
      assertFalse(iterator.hasNext());

   }

   @Test
   public void testMutlipleIterators() {

      List<Integer> arrayList = new ArrayList<>();
      for (int i = 0; i < 1000; i++) {
         arrayList.add(i);
      }
      List<Integer> arrayList2 = new ArrayList<>();
      for (int i = 1000; i < 2000; i++) {
         arrayList2.add(i);
      }
      List<Integer> arrayList3 = new ArrayList<>();
      for (int i = 2000; i < 3000; i++) {
         arrayList3.add(i);
      }

      MultiIterator<Integer> iterator = new MultiIterator<>(new Iterator[]{arrayList.iterator(), arrayList2.iterator(), arrayList3.iterator()});
      for (int i = 0; i < 3000; i++) {
         assertTrue(iterator.hasNext());
         assertEquals(Integer.valueOf(i), iterator.next());
      }
      assertFalse(iterator.hasNext());

   }
}