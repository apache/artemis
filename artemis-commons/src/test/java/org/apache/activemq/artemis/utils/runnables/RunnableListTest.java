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

package org.apache.activemq.artemis.utils.runnables;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

public class RunnableListTest {

   Set<AtomicRunnable> masterList = new HashSet<>();

   @Test
   public void testRunning() {
      AtomicInteger result = new AtomicInteger();

      RunnableList listA = new RunnableList();
      RunnableList listB = new RunnableList();
      RunnableList listC = new RunnableList();

      RunnableList[] lists = new RunnableList[]{listA, listB, listC};
      for (RunnableList l : lists) {
         for (int i = 0; i < 10; i++) {
            AtomicRunnable runnable = new AtomicRunnable() {
               @Override
               public void atomicRun() {
                  result.incrementAndGet();
                  masterList.remove(this);
               }
            };
            addItem(l, runnable);
         }
      }

      assertEquals(30, masterList.size());

      runList(listA);

      assertEquals(10, result.get());

      assertEquals(20, masterList.size());
      assertEquals(0, listA.size());
      assertEquals(10, listB.size());
      assertEquals(10, listC.size());

      Set<AtomicRunnable> copyList = new HashSet<>();
      copyList.addAll(masterList);

      copyList.forEach(r -> r.run());

      for (RunnableList l : lists) {
         assertEquals(0, l.size());
      }

      assertEquals(30, result.get());
   }

   @Test
   public void testCancel() {
      AtomicInteger result = new AtomicInteger();

      RunnableList listA = new RunnableList();
      RunnableList listB = new RunnableList();
      RunnableList listC = new RunnableList();

      RunnableList[] lists = new RunnableList[]{listA, listB, listC};
      for (RunnableList l : lists) {
         for (int i = 0; i < 10; i++) {
            AtomicRunnable runnable = new AtomicRunnable() {
               @Override
               public void atomicRun() {
                  result.incrementAndGet();
                  masterList.remove(this);
               }
            };
            addItem(l, runnable);
         }
      }

      assertEquals(30, masterList.size());

      listA.cancel();

      assertEquals(0, result.get());

      assertEquals(20, masterList.size());
      assertEquals(0, listA.size());
      assertEquals(10, listB.size());
      assertEquals(10, listC.size());

      listB.cancel();
      listC.cancel();

      for (RunnableList l : lists) {
         assertEquals(0, l.size());
      }

      assertEquals(0, masterList.size());
   }

   // runs all AtomicRunnables inside the list
   private void runList(RunnableList list) {
      // make a copy of all the tasks to a new list
      List<AtomicRunnable> toRun = new ArrayList<>();
      list.forEach(toRun::add);

      // run all the elements
      toRun.forEach(r -> r.run());
   }

   private void addItem(RunnableList list, AtomicRunnable runnable) {
      list.add(runnable);
      runnable.setCancel(masterList::remove);
      masterList.add(runnable);
   }

}