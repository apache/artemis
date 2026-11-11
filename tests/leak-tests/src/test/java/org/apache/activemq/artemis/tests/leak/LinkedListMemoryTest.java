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

package org.apache.activemq.artemis.tests.leak;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import io.github.checkleak.core.CheckLeak;
import org.apache.activemq.artemis.utils.collections.LinkedListImpl;
import org.apache.activemq.artemis.utils.collections.LinkedListIterator;
import org.junit.jupiter.api.Test;

public class LinkedListMemoryTest extends AbstractLeakTest {

   Random random = new Random();

   CheckLeak checkLeak = new CheckLeak();

   public int randomInt(int x, int y) {

      int randomNumber = random.nextInt(y - x + 1) + x;

      return randomNumber;
   }

   @Test
   public void testRemoveIteratorsRandom() throws Exception {
      LinkedListImpl<String> linkedList = new LinkedListImpl<>((a, b) -> a.compareTo(b));

      linkedList.addSorted("Test");

      int iterators = 100;
      List<LinkedListIterator> listIerators = new ArrayList();

      for (int i = 0; i < iterators; i++) {
         listIerators.add(linkedList.iterator());
      }

      int countRemoved = 0;

      while (!listIerators.isEmpty()) {
         int removeElement = randomInt(0, listIerators.size() - 1);
         countRemoved++;
         LinkedListIterator toRemove = listIerators.remove(removeElement);
         toRemove.close();
         toRemove = null;
         MemoryAssertions.assertMemory(checkLeak, iterators - countRemoved, LinkedListImpl.Iterator.class.getName());
      }
      MemoryAssertions.assertMemory(checkLeak, 0, LinkedListImpl.Iterator.class.getName());
   }
}
