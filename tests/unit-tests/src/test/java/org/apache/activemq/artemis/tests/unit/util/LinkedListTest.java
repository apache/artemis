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

import java.lang.invoke.MethodHandles;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.util.collection.LongObjectHashMap;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.collections.LinkedListImpl;
import org.apache.activemq.artemis.utils.collections.LinkedListIterator;
import org.apache.activemq.artemis.utils.collections.NodeStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class LinkedListTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private int scans = 0;
   private LinkedListImpl<Integer> list;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      list = new LinkedListImpl<>(integerComparator) {
         @Override
         protected boolean addSortedScan(Integer e) {
            scans++;
            return super.addSortedScan(e);
         }
      };
   }

   Comparator<Integer> integerComparator = (o1, o2) -> {
      logger.trace("Compare {} and {}", o1, o2);
      if (o1.intValue() == o2.intValue()) {
         logger.trace("Return 0");
         return 0;
      }
      if (o2.intValue() > o1.intValue()) {
         logger.trace("o2 is greater than, returning 1");
         return 1;
      } else {
         logger.trace("o2 is lower than, returning -1");
         return -1;
      }
   };

   @Test
   public void addSorted() {
      assertEquals(0, scans); // sanity check

      list.addSorted(1);
      list.addSorted(3);
      list.addSorted(2);
      list.addSorted(0);

      assertEquals(0, scans); // all adds were somewhat ordered, it shouldn't be doing any scans

      validateOrder(null);
      assertEquals(4, list.size());

   }

   @Test
   public void addSortedCachedLast() {
      assertEquals(0, scans); // just a sanity check
      list.addSorted(5);
      list.addSorted(1);
      list.addSorted(3);
      list.addSorted(4);
      list.addSorted(2);
      list.addSorted(10);
      list.addSorted(20);
      list.addSorted(19);
      list.addSorted(7);
      list.addSorted(8);
      assertEquals(0, scans); // no full scans should be done
      assertEquals(1, (int)list.poll());
      list.addSorted(9);
      assertEquals(1, scans); // remove (poll) should clear the last added cache, a scan will be needed

      printDebug();
      validateOrder(null);
   }

   @Test
   public void scanDirectionalTest() {
      list.addSorted(9);
      assertEquals(1, list.size());
      list.addSorted(5);
      assertEquals(2, list.size());
      list.addSorted(6);
      assertEquals(3, list.size());
      list.addSorted(2);
      assertEquals(4, list.size());
      list.addSorted(7);
      assertEquals(5, list.size());
      list.addSorted(4);
      assertEquals(6, list.size());
      list.addSorted(8);
      assertEquals(7, list.size());
      list.addSorted(1);
      assertEquals(8, list.size());
      list.addSorted(10);
      assertEquals(9, list.size());
      list.addSorted(3);
      assertEquals(10, list.size());
      printDebug();
      validateOrder(null);
   }

   private void printDebug() {
      if (logger.isDebugEnabled()) {
         logger.debug("**** list output:");
         LinkedListIterator<Integer> integerIterator = list.iterator();
         while (integerIterator.hasNext()) {
            logger.debug("list {}", integerIterator.next());
         }
         integerIterator.close();
      }
   }

   @Test
   public void testDuplicateSorted() {
      list.addSorted(1);
      list.addSorted(2);
      list.addSorted(1);

      try (LinkedListIterator<Integer> listIterator = list.iterator()) {
         assertEquals(1, listIterator.next().intValue());
         assertEquals(1, listIterator.next().intValue());
         assertEquals(2, listIterator.next().intValue());
      }

      list.clear();

      list.addSorted(10);
      list.addSorted(2);
      list.addSorted(1);
      list.addSorted(2);

      try (LinkedListIterator<Integer> listIterator = list.iterator()) {
         assertEquals(1, listIterator.next().intValue());
         assertEquals(2, listIterator.next().intValue());
         assertEquals(2, listIterator.next().intValue());
         assertEquals(10, listIterator.next().intValue());
      }

   }

   @Test
   public void testDuplicateScan() {
      list.addSorted(0);
      list.addSorted(1);
      list.addSorted(2);
      list.addSorted(3);

      try (LinkedListIterator<Integer> listIterator = list.iterator()) {
         assertEquals(0, listIterator.next().intValue());
         assertEquals(1, listIterator.next().intValue());
         assertEquals(2, listIterator.next().intValue());
         assertEquals(3, listIterator.next().intValue());
         // removing an element to clear the lastAdded element from the List
         assertEquals(3, listIterator.removeLastElement().intValue());
      }

      scans = 0;
      list.addSorted(1);
      assertEquals(1, scans, "The test expects a scan to be made");

      try (LinkedListIterator<Integer> listIterator = list.iterator()) {
         assertEquals(0, listIterator.next().intValue());
         assertEquals(1, listIterator.next().intValue());
         assertEquals(1, listIterator.next().intValue());
         assertEquals(2, listIterator.next().intValue());
      }
   }

   @Test
   public void randomSorted() {

      int elements = 10_000;

      Set<Integer> values = new HashSet<>();
      for (int i = 0; i < elements; i++) {
         for (;;) { // a retry loop, if a random give me the same value twice, I would retry
            int value = RandomUtil.randomInt();
            if (!values.contains(value)) { // validating if the random is repeated or not, and retrying otherwise
               if (logger.isDebugEnabled()) {
                  logger.debug("Adding {}", value);
               }
               values.add(value);
               list.addSorted(value);
               break;
            }
         }
      }

      assertEquals(values.size(), list.size());

      validateOrder(values);

      assertEquals(0, values.size());

   }

   private void validateOrder(Set<Integer> values) {
      Integer previous = null;
      LinkedListIterator<Integer> integerIterator = list.iterator();
      while (integerIterator.hasNext()) {
         Integer value = integerIterator.next();
         logger.debug("Reading {}", value);
         if (previous != null) {
            assertTrue(integerComparator.compare(previous, value) > 0, value + " should be > " + previous);
            assertTrue(value.intValue() > previous.intValue(), value + " should be > " + previous);
         }

         if (values != null) {
            values.remove(value);
         }
         previous = value;
      }
      integerIterator.close();
   }

   private static final class ObservableNode extends LinkedListImpl.Node<ObservableNode> {

      public String serverID;
      public int id;
      ObservableNode(String serverID, int id) {
         this.id = id;
         this.serverID = serverID;
      }

      public LinkedListImpl.Node<ObservableNode> publicNext() {
         return next();
      }

      public LinkedListImpl.Node<ObservableNode> publicPrev() {
         return prev();
      }

   }

   static class ListNodeStore implements NodeStore<ObservableNode> {
      // this is for serverID = null;
      LongObjectHashMap<LinkedListImpl.Node<ObservableNode>> nodeLongObjectHashMap = new LongObjectHashMap<>();

      Map<Object, LongObjectHashMap<LinkedListImpl.Node<ObservableNode>>> mapList = new HashMap<>();

      @Override
      public void storeNode(ObservableNode element, LinkedListImpl.Node<ObservableNode> node) {
         LongObjectHashMap<LinkedListImpl.Node<ObservableNode>> map = getNodesMap(element.serverID);
         map.put(element.id, node);
      }

      @Override
      public LinkedListImpl.Node<ObservableNode> getNode(String listID, long id) {
         LongObjectHashMap<LinkedListImpl.Node<ObservableNode>> map = getNodesMap(listID);
         if (map == null) {
            return null;
         }
         return map.get(id);
      }

      @Override
      public void removeNode(ObservableNode element, LinkedListImpl.Node<ObservableNode> node) {
         LongObjectHashMap<LinkedListImpl.Node<ObservableNode>> map = getNodesMap(element.serverID);
         if (map != null) {
            map.remove(element.id);
         }
      }

      private LongObjectHashMap<LinkedListImpl.Node<ObservableNode>> getNodesMap(String listID) {
         if (listID == null) {
            return nodeLongObjectHashMap;
         } else {
            LongObjectHashMap<LinkedListImpl.Node<ObservableNode>> theMap = mapList.get(listID);

            if (theMap == null) {
               theMap = new LongObjectHashMap<>();
               mapList.put(listID, theMap);
            }
            return theMap;
         }
      }

      @Override
      public void clear() {
         nodeLongObjectHashMap.clear();
         mapList.clear();
      }

      @Override
      public int size() {
         int size = 0;
         for (LongObjectHashMap list : mapList.values()) {
            size = +list.size();
         }
         return nodeLongObjectHashMap.size() + size;
      }
   }


   @Test
   public void testAddAndRemove() {
      LinkedListImpl<ObservableNode> objs = new LinkedListImpl<>();

      // Initial add
      for (int i = 0; i < 100; i++) {
         final ObservableNode o = new ObservableNode(null, i);
         objs.addTail(o);
      }

      try (LinkedListIterator<ObservableNode> iter = objs.iterator()) {

         for (int i = 0; i < 500; i++) {

            for (int add = 0; add < 1000; add++) {
               final ObservableNode o = new ObservableNode(null, add);
               objs.addTail(o);
               assertNotNull(o.publicPrev(), "prev");
               assertNull(o.publicNext(), "next");
            }

            for (int remove = 0; remove < 1000; remove++) {
               final ObservableNode next = iter.next();
               assertNotNull(next);
               assertNotNull(next.publicPrev(), "prev");
               //it's ok to check this, because we've *at least* 100 elements left!
               assertNotNull(next.publicNext(), "next");
               iter.remove();
               assertNull(next.publicPrev(), "prev");
               assertNull(next.publicNext(), "next");
            }
            assertEquals(100, objs.size());
         }

         while (iter.hasNext()) {
            final ObservableNode next = iter.next();
            assertNotNull(next);
            iter.remove();
            assertNull(next.publicPrev(), "prev");
            assertNull(next.publicNext(), "next");
         }
      }
      assertEquals(0, objs.size());

   }


   @Test
   public void testAddAndRemoveWithIDs() {
      internalAddWithID(true);
   }

   @Test
   public void testAddAndRemoveWithIDsDeferredSupplier() {
      internalAddWithID(false);
   }

   private void internalAddWithID(boolean deferSupplier) {

      for (int sid = 1; sid <= 2; sid++) {
         LinkedListImpl<ObservableNode> objs = new LinkedListImpl<>();
         objs.clearID();

         String serverID = sid == 1 ? null : "" + sid;

         ListNodeStore nodeStore = new ListNodeStore();

         if (!deferSupplier) {
            objs.setNodeStore(nodeStore);
         }

         // Initial add
         for (int i = 1; i <= 1000; i++) {
            final ObservableNode o = new ObservableNode(serverID, i);
            objs.addTail(o);
         }

         assertEquals(1000, objs.size());

         if (deferSupplier) {
            assertEquals(0, nodeStore.size());
            objs.setNodeStore(nodeStore);
         } else {
            // clear the ID supplier
            objs.clearID();
            // and redo it
            assertEquals(0, nodeStore.size());
            nodeStore = new ListNodeStore();
            objs.setNodeStore(nodeStore);
            assertEquals(1000, objs.size());
         }

         assertEquals(1000, nodeStore.size());

         // remove all even items
         for (int i = 1; i <= 1000; i += 2) {
            objs.removeWithID(serverID, i);
         }

         assertEquals(500, objs.size());
         assertEquals(500, nodeStore.size());

         Iterator<ObservableNode> iterator = objs.iterator();

         {
            int i = 2;
            while (iterator.hasNext()) {
               ObservableNode value = iterator.next();
               assertEquals(i, value.id);
               i += 2;
            }
         }

         for (int i = 2; i <= 1000; i += 2) {
            assertNotNull(objs.removeWithID(serverID, i));
         }

         assertEquals(0, nodeStore.size());
         assertEquals(0, objs.size());

      }
   }


   @Test
   public void testRaceRemovingID() throws Exception {
      ExecutorService executor = Executors.newFixedThreadPool(2);

      int elements = 1000;
      try {
         LinkedListImpl<ObservableNode> objs = new LinkedListImpl<>();
         ListNodeStore nodeStore = new ListNodeStore();
         objs.setNodeStore(nodeStore);
         final String serverID = RandomUtil.randomUUIDString();

         for (int i = 0; i < elements; i++) {
            objs.addHead(new ObservableNode(serverID, i));
         }
         assertEquals(elements, objs.size());

         CyclicBarrier barrier = new CyclicBarrier(2);

         CountDownLatch latch = new CountDownLatch(2);
         AtomicInteger errors = new AtomicInteger(0);

         executor.execute(() -> {
            try {
               barrier.await(10, TimeUnit.SECONDS);
            } catch (Exception e) {
               e.printStackTrace();
            }

            try {
               for (int i = 0; i < elements; i++) {
                  objs.removeWithID(serverID, i);
               }
            } catch (Exception e) {
               e.printStackTrace();
               errors.incrementAndGet();
            } finally {
               latch.countDown();
            }
         });

         executor.execute(() -> {
            LinkedListIterator iterator = objs.iterator();

            try {
               barrier.await(10, TimeUnit.SECONDS);
            } catch (Exception e) {
               e.printStackTrace();
            }

            try {
               while (iterator.hasNext()) {
                  Object value = iterator.next();
                  iterator.remove();
               }
            } catch (Exception e) {
               if (e instanceof NoSuchElementException) {
                  // this is ok
               } else {
                  e.printStackTrace();
                  errors.incrementAndGet();
               }
            } finally {
               latch.countDown();
            }
         });

         assertTrue(latch.await(10, TimeUnit.SECONDS));

         assertEquals(0, objs.size());

         assertEquals(0, errors.get());
      } finally {
         executor.shutdownNow();
      }
   }

   @Test
   public void testAddHeadAndRemove() {
      LinkedListImpl<ObservableNode> objs = new LinkedListImpl<>();

      // Initial add
      for (int i = 0; i < 1001; i++) {
         final ObservableNode o = new ObservableNode(null, i);
         objs.addHead(o);
      }
      assertEquals(1001, objs.size());

      int countLoop = 0;

      try (LinkedListIterator<ObservableNode> iter = objs.iterator()) {
         int removed = 0;
         for (countLoop = 0; countLoop <= 1000; countLoop++) {
            final ObservableNode obj = iter.next();
            assertNotNull(obj);
            if (countLoop == 500 || countLoop == 1000) {
               assertNotNull(obj.publicPrev(), "prev");
               iter.remove();
               assertNull(obj.publicPrev(), "prev");
               assertNull(obj.publicNext(), "next");
               removed++;
            }
         }
         assertEquals(1001 - removed, objs.size());
      }

      final int expectedSize = objs.size();
      try (LinkedListIterator<ObservableNode> iter = objs.iterator()) {
         countLoop = 0;
         while (iter.hasNext()) {
            final ObservableNode obj = iter.next();
            assertNotNull(obj);
            countLoop++;
         }
         assertEquals(expectedSize, countLoop);
      }
      // it's needed to add this line here because IBM JDK calls finalize on all objects in list
      // before previous assert is called and fails the test, this will prevent it
      objs.clear();
   }

   @Test
   public void testAddTail() {
      int num = 10;

      assertEquals(0, list.size());

      for (int i = 0; i < num; i++) {
         list.addTail(i);

         assertEquals(i + 1, list.size());
      }

      for (int i = 0; i < num; i++) {
         assertEquals(i, list.poll().intValue());

         assertEquals(num - i - 1, list.size());
      }
   }


   @Test
   public void testPeek() {
      assertEquals(0, list.size());
      assertNull(list.peek());
      list.addTail(10);
      assertEquals(10, (int)list.peek());
      assertEquals(10, (int)list.poll());
      assertNull(list.peek());
      list.addTail(12);
      assertEquals(12, (int)list.peek());
      list.addHead(5);
      assertEquals(5, (int)list.peek());
      list.poll();
      assertEquals(12, (int)list.peek());
      list.poll();
      assertNull(list.peek());
   }

   @Test
   public void testAddHead() {
      int num = 10;

      assertEquals(0, list.size());

      for (int i = 0; i < num; i++) {
         list.addHead(i);

         assertEquals(i + 1, list.size());
      }

      for (int i = num - 1; i >= 0; i--) {
         assertEquals(i, list.poll().intValue());

         assertEquals(i, list.size());
      }
   }

   @Test
   public void testAddHeadAndTail() {
      int num = 10;

      for (int i = 0; i < num; i++) {
         list.addHead(i);
      }

      for (int i = num; i < num * 2; i++) {
         list.addTail(i);
      }

      for (int i = num * 2; i < num * 3; i++) {
         list.addHead(i);
      }

      for (int i = num * 3; i < num * 4; i++) {
         list.addTail(i);
      }

      for (int i = num * 3 - 1; i >= num * 2; i--) {
         assertEquals(i, list.poll().intValue());
      }

      for (int i = num - 1; i >= 0; i--) {
         assertEquals(i, list.poll().intValue());
      }

      for (int i = num; i < num * 2; i++) {
         assertEquals(i, list.poll().intValue());
      }

      for (int i = num * 3; i < num * 4; i++) {
         assertEquals(i, list.poll().intValue());
      }

   }

   @Test
   public void testPoll() {
      int num = 10;

      assertNull(list.poll());
      assertNull(list.poll());
      assertNull(list.poll());

      for (int i = 0; i < num; i++) {
         list.addTail(i);
      }

      for (int i = 0; i < num; i++) {
         assertEquals(i, list.poll().intValue());
      }

      assertNull(list.poll());
      assertNull(list.poll());
      assertNull(list.poll());

      for (int i = num; i < num * 2; i++) {
         list.addHead(i);
      }

      for (int i = num * 2 - 1; i >= num; i--) {
         assertEquals(i, list.poll().intValue());
      }

      assertNull(list.poll());
      assertNull(list.poll());
      assertNull(list.poll());

   }

   @Test
   public void testIterateNoElements() {
      LinkedListIterator<Integer> iter = list.iterator();

      assertNotNull(iter);

      assertNoSuchElementIsThrown(iter);

      try {
         iter.remove();

         fail("Should throw NoSuchElementException");
      } catch (NoSuchElementException e) {
         // OK
      }
   }

   @Test
   public void testCreateIteratorBeforeAddElements() {
      int num = 10;

      LinkedListIterator<Integer> iter = list.iterator();

      assertNotNull(iter);

      for (int i = 0; i < num; i++) {
         list.addTail(i);
      }

      testIterate1(num, iter);
   }

   @Test
   public void testCreateIteratorAfterAddElements() {
      int num = 10;

      for (int i = 0; i < num; i++) {
         list.addTail(i);
      }

      LinkedListIterator<Integer> iter = list.iterator();

      assertNotNull(iter);

      testIterate1(num, iter);
   }

   @Test
   public void testIterateThenAddMoreAndIterateAgain() {
      int num = 10;

      for (int i = 0; i < num; i++) {
         list.addTail(i);
      }

      LinkedListIterator<Integer> iter = list.iterator();

      assertNotNull(iter);

      for (int i = 0; i < num; i++) {
         assertTrue(iter.hasNext());
         assertEquals(i, iter.next().intValue());
      }
      assertFalse(iter.hasNext());

      assertNoSuchElementIsThrown(iter);

      // Add more

      for (int i = num; i < num * 2; i++) {
         list.addTail(i);
      }

      for (int i = num; i < num * 2; i++) {
         assertTrue(iter.hasNext());
         assertEquals(i, iter.next().intValue());
      }
      assertFalse(iter.hasNext());

      assertNoSuchElementIsThrown(iter);

      // Add some more at head

      for (int i = num * 2; i < num * 3; i++) {
         list.addHead(i);
      }

      iter = list.iterator();

      for (int i = num * 3 - 1; i >= num * 2; i--) {
         assertTrue(iter.hasNext());
         assertEquals(i, iter.next().intValue());
      }
      for (int i = 0; i < num * 2; i++) {
         assertTrue(iter.hasNext());
         assertEquals(i, iter.next().intValue());
      }
      assertFalse(iter.hasNext());
   }

   private void testIterate1(int num, LinkedListIterator<Integer> iter) {
      for (int i = 0; i < num; i++) {
         assertTrue(iter.hasNext());
         assertEquals(i, iter.next().intValue());
      }
      assertFalse(iter.hasNext());

      assertNoSuchElementIsThrown(iter);
   }

   private void assertNoSuchElementIsThrown(LinkedListIterator<Integer> iter) {
      try {
         iter.next();

         fail("Should throw NoSuchElementException");
      } catch (NoSuchElementException e) {
         // OK
      }
   }

   @Test
   public void testRemoveAll() {
      int num = 10;

      LinkedListIterator<Integer> iter = list.iterator();

      try {
         iter.remove();

         fail("Should throw NoSuchElementException");
      } catch (NoSuchElementException e) {
         // OK
      }

      for (int i = 0; i < num; i++) {
         list.addTail(i);
      }

      assertEquals(num, list.size());

      try {
         iter.remove();

         fail("Should throw NoSuchElementException");
      } catch (NoSuchElementException e) {
         // OK
      }

      for (int i = 0; i < num; i++) {
         assertTrue(iter.hasNext());
         assertEquals(i, iter.next().intValue());
         iter.remove();
         assertEquals(num - i - 1, list.size());
      }

      assertFalse(iter.hasNext());
   }

   @Test
   public void testRemoveOdd() {
      int num = 10;

      LinkedListIterator<Integer> iter = list.iterator();

      try {
         iter.remove();

         fail("Should throw NoSuchElementException");
      } catch (NoSuchElementException e) {
         // OK
      }

      for (int i = 0; i < num; i++) {
         list.addTail(i);
      }

      try {
         iter.remove();

         fail("Should throw NoSuchElementException");
      } catch (NoSuchElementException e) {
         // OK
      }

      int size = num;
      for (int i = 0; i < num; i++) {
         assertTrue(iter.hasNext());
         assertEquals(i, iter.next().intValue());
         if (i % 2 == 0) {
            iter.remove();
            size--;
         }
         assertEquals(list.size(), size);
      }

      iter = list.iterator();
      for (int i = 0; i < num; i++) {
         if (i % 2 == 1) {
            assertTrue(iter.hasNext());
            assertEquals(i, iter.next().intValue());
         }
      }

      assertFalse(iter.hasNext());
   }

   @Test
   public void testRemoveHead1() {
      int num = 10;

      LinkedListIterator<Integer> iter = list.iterator();

      for (int i = 0; i < num; i++) {
         list.addTail(i);
      }

      iter.next();
      iter.remove();

      for (int i = 1; i < num; i++) {
         assertTrue(iter.hasNext());
         assertEquals(i, iter.next().intValue());
      }

      assertFalse(iter.hasNext());
   }

   @Test
   public void testRemoveHead2() {
      int num = 10;

      for (int i = 0; i < num; i++) {
         list.addTail(i);
      }

      LinkedListIterator<Integer> iter = list.iterator();

      iter.next();
      iter.remove();

      iter = list.iterator();

      for (int i = 1; i < num; i++) {
         assertTrue(iter.hasNext());
         assertEquals(i, iter.next().intValue());
      }

      assertFalse(iter.hasNext());
   }

   @Test
   public void testRemoveHead3() {
      int num = 10;

      LinkedListIterator<Integer> iter = list.iterator();

      for (int i = 0; i < num; i++) {
         list.addTail(i);
      }

      for (int i = 0; i < num; i++) {
         assertTrue(iter.hasNext());
         assertEquals(i, iter.next().intValue());
         iter.remove();
      }

      for (int i = num; i < num * 2; i++) {
         list.addTail(i);
      }

      for (int i = num; i < num * 2; i++) {
         assertTrue(iter.hasNext());
         assertEquals(i, iter.next().intValue());
         iter.remove();
      }

   }

   @Test
   public void testRemoveTail1() {
      int num = 10;

      LinkedListIterator<Integer> iter = list.iterator();

      for (int i = 0; i < num; i++) {
         list.addTail(i);
      }

      for (int i = 0; i < num; i++) {
         assertTrue(iter.hasNext());
         assertEquals(i, iter.next().intValue());
      }

      assertFalse(iter.hasNext());

      // Remove the last one, that's element 9
      iter.remove();

      iter = list.iterator();

      for (int i = 0; i < num - 1; i++) {
         assertTrue(iter.hasNext());
         assertEquals(i, iter.next().intValue());
      }

      assertFalse(iter.hasNext());
   }

   @Test
   public void testRemoveMiddle() {
      int num = 10;

      for (int i = 0; i < num; i++) {
         list.addTail(i);
      }

      LinkedListIterator<Integer> iter = list.iterator();

      for (int i = 0; i < num / 2; i++) {
         assertTrue(iter.hasNext());
         assertEquals(i, iter.next().intValue());
      }

      // Remove the 4th element
      iter.remove();

      iter = list.iterator();

      for (int i = 0; i < num; i++) {
         if (i != num / 2 - 1) {
            assertTrue(iter.hasNext());
            assertEquals(i, iter.next().intValue());
         }
      }

      assertFalse(iter.hasNext());
   }

   @Test
   public void testRemoveTail2() {
      int num = 10;

      for (int i = 0; i < num; i++) {
         list.addTail(i);
      }

      LinkedListIterator<Integer> iter = list.iterator();

      for (int i = 0; i < num; i++) {
         assertTrue(iter.hasNext());
         assertEquals(i, iter.next().intValue());
      }

      assertFalse(iter.hasNext());

      // Remove the last one, that's element 9
      iter.remove();

      try {
         iter.remove();
         fail("Should throw exception");
      } catch (NoSuchElementException e) {
      }

      iter = list.iterator();

      for (int i = 0; i < num - 1; i++) {
         assertTrue(iter.hasNext());
         assertEquals(i, iter.next().intValue());
      }

      assertFalse(iter.hasNext());
   }

   @Test
   public void testRemoveTail3() {
      int num = 10;

      LinkedListIterator<Integer> iter = list.iterator();

      for (int i = 0; i < num; i++) {
         list.addTail(i);
      }

      for (int i = 0; i < num; i++) {
         assertTrue(iter.hasNext());
         assertEquals(i, iter.next().intValue());
      }

      assertFalse(iter.hasNext());

      // This should remove the 9th element and move the iterator back to position 8
      iter.remove();

      for (int i = num; i < num * 2; i++) {
         list.addTail(i);
      }

      assertTrue(iter.hasNext());
      assertEquals(8, iter.next().intValue());

      for (int i = num; i < num * 2; i++) {
         assertTrue(iter.hasNext());
         assertEquals(i, iter.next().intValue());
      }
   }

   @Test
   public void testRemoveHeadAndTail1() {
      LinkedListIterator<Integer> iter = list.iterator();

      int num = 10;

      for (int i = 0; i < num; i++) {
         list.addTail(i);
         assertTrue(iter.hasNext());
         assertEquals(i, iter.next().intValue());
         iter.remove();
      }

   }

   @Test
   public void testRemoveHeadAndTail2() {
      LinkedListIterator<Integer> iter = list.iterator();

      int num = 10;

      for (int i = 0; i < num; i++) {
         list.addHead(i);
         assertEquals(1, list.size());
         assertTrue(iter.hasNext());
         assertEquals(i, iter.next().intValue());
         iter.remove();
      }

   }

   @Test
   public void testRemoveHeadAndTail3() {
      LinkedListIterator<Integer> iter = list.iterator();

      int num = 10;

      for (int i = 0; i < num; i++) {
         if (i % 2 == 0) {
            list.addHead(i);
         } else {
            list.addTail(i);
         }
         assertEquals(1, list.size());
         assertTrue(iter.hasNext());
         assertEquals(i, iter.next().intValue());
         iter.remove();
      }

   }

   @Test
   public void testRemoveInTurn() {
      LinkedListIterator<Integer> iter = list.iterator();

      int num = 10;

      for (int i = 0; i < num; i++) {
         list.addTail(i);
      }

      for (int i = 0; i < num; i++) {
         assertTrue(iter.hasNext());
         assertEquals(i, iter.next().intValue());
         iter.remove();
      }

      assertFalse(iter.hasNext());
      assertEquals(0, list.size());

   }

   @Test
   public void testRemoveLastNudgeNoReplay() {
      for (int i = 1; i < 3; i++) {
         doTestRemoveLastNudgeNoReplay(i);
      }
   }

   private void doTestRemoveLastNudgeNoReplay(int num) {

      LinkedListIterator<Integer> iter = list.iterator();

      for (int i = 0; i < num; i++) {
         list.addTail(i);
      }

      // exhaust iterator
      for (int i = 0; i < num; i++) {
         assertTrue(iter.hasNext());
         assertEquals(i, iter.next().intValue());
      }

      // remove last
      LinkedListIterator<Integer> pruneIterator = list.iterator();
      while (pruneIterator.hasNext()) {
         int v = pruneIterator.next();
         if (v == num - 1) {
            pruneIterator.remove();
         }
      }

      // ensure existing iterator does not reset or replay
      assertFalse(iter.hasNext());
      assertEquals(num - 1, list.size());
   }

   @Test
   public void testGCNepotismPoll() {
      final int count = 100;
      final LinkedListImpl<ObservableNode> list = new LinkedListImpl<>();
      for (int i = 0; i < count; i++) {
         final ObservableNode node = new ObservableNode(null, i);
         assertNull(node.publicPrev());
         assertNull(node.publicNext());
         list.addTail(node);
         assertNotNull(node.publicPrev());
      }
      ObservableNode node;
      int removed = 0;
      while ((node = list.poll()) != null) {
         assertNull(node.publicPrev());
         assertNull(node.publicNext());
         removed++;
      }
      assertEquals(count, removed);
      assertEquals(0, list.size());
   }

   @Test
   public void testGCNepotismClear() {
      final int count = 100;
      final ObservableNode[] nodes = new ObservableNode[count];
      final LinkedListImpl<ObservableNode> list = new LinkedListImpl<>();
      for (int i = 0; i < count; i++) {
         final ObservableNode node = new ObservableNode(null, i);
         assertNull(node.publicPrev());
         assertNull(node.publicNext());
         nodes[i] = node;
         list.addTail(node);
         assertNotNull(node.publicPrev());
      }
      list.clear();
      for (ObservableNode node : nodes) {
         assertNull(node.publicPrev());
         assertNull(node.publicNext());
      }
      assertEquals(0, list.size());
   }

   @Test
   public void testClear() {

      int num = 10;

      for (int i = 0; i < num; i++) {
         list.addTail(i);
      }

      assertEquals(num, list.size());

      list.clear();

      assertEquals(0, list.size());

      assertNull(list.poll());

      LinkedListIterator<Integer> iter = list.iterator();

      assertFalse(iter.hasNext());

      try {
         iter.next();
      } catch (NoSuchElementException e) {
      }

      for (int i = 0; i < num; i++) {
         list.addTail(i);
      }

      assertEquals(num, list.size());

      iter = list.iterator();

      for (int i = 0; i < num; i++) {
         assertTrue(iter.hasNext());
         assertEquals(i, iter.next().intValue());
      }
      assertFalse(iter.hasNext());

      for (int i = 0; i < num; i++) {
         assertEquals(i, list.poll().intValue());
      }
      assertNull(list.poll());
      assertEquals(0, list.size());

   }

   @Test
   public void testMultipleIterators1() {
      int num = 10;

      for (int i = 0; i < num; i++) {
         list.addTail(i);
      }

      LinkedListIterator<Integer> iter1 = list.iterator();
      LinkedListIterator<Integer> iter2 = list.iterator();
      LinkedListIterator<Integer> iter3 = list.iterator();

      for (int i = 0; i < num; ) {
         assertTrue(iter1.hasNext());
         assertEquals(i++, iter1.next().intValue());
         iter1.remove();

         if (i == 10) {
            break;
         }

         assertTrue(iter2.hasNext());
         assertEquals(i++, iter2.next().intValue());
         iter2.remove();

         assertTrue(iter3.hasNext());
         assertEquals(i++, iter3.next().intValue());
         iter3.remove();
      }
   }

   @Test
   public void testRepeat() {
      int num = 10;

      for (int i = 0; i < num; i++) {
         list.addTail(i);
      }

      LinkedListIterator<Integer> iter = list.iterator();

      assertTrue(iter.hasNext());
      assertEquals(0, iter.next().intValue());

      iter.repeat();
      assertTrue(iter.hasNext());
      assertEquals(0, iter.next().intValue());

      iter.next();
      iter.next();
      iter.next();
      assertTrue(iter.hasNext());
      assertEquals(4, iter.next().intValue());

      iter.repeat();
      assertTrue(iter.hasNext());
      assertEquals(4, iter.next().intValue());

      iter.next();
      iter.next();
      iter.next();
      iter.next();
      assertEquals(9, iter.next().intValue());
      assertFalse(iter.hasNext());

      iter.repeat();
      assertTrue(iter.hasNext());
      assertEquals(9, iter.next().intValue());
      assertFalse(iter.hasNext());
   }

   @Test
   public void testRepeatAndRemove() {
      int num = 10;

      for (int i = 0; i < num; i++) {
         list.addTail(i);
      }

      LinkedListIterator<Integer> iter1 = list.iterator();

      LinkedListIterator<Integer> iter2 = list.iterator();

      assertTrue(iter1.hasNext());
      assertEquals(0, iter1.next().intValue());

      assertTrue(iter2.hasNext());
      assertEquals(0, iter2.next().intValue());

      iter2.remove();

      iter1.repeat();

      // Should move to the next one
      assertTrue(iter1.hasNext());
      assertEquals(1, iter1.next().intValue());

      iter1.next();
      iter1.next();
      iter1.next();
      iter1.next();
      iter1.next();
      iter1.next();
      iter1.next();
      iter1.next();
      assertEquals(9, iter1.next().intValue());

      iter2.next();
      iter2.next();
      iter2.next();
      iter2.next();
      iter2.next();
      iter2.next();
      iter2.next();
      iter2.next();
      assertEquals(9, iter2.next().intValue());

      iter1.remove();

      iter2.repeat();

      // Go back one since can't go forward
      assertEquals(8, iter2.next().intValue());

   }

   @Test
   public void testMultipleIterators2() {
      int num = 10;

      for (int i = 0; i < num; i++) {
         list.addTail(i);
      }

      LinkedListIterator<Integer> iter1 = list.iterator();
      LinkedListIterator<Integer> iter2 = list.iterator();
      LinkedListIterator<Integer> iter3 = list.iterator();
      LinkedListIterator<Integer> iter4 = list.iterator();
      LinkedListIterator<Integer> iter5 = list.iterator();

      assertTrue(iter1.hasNext());
      assertTrue(iter2.hasNext());
      assertTrue(iter3.hasNext());
      assertTrue(iter4.hasNext());
      assertTrue(iter5.hasNext());

      assertEquals(0, iter2.next().intValue());
      assertTrue(iter2.hasNext());
      assertEquals(1, iter2.next().intValue());

      assertEquals(0, iter1.next().intValue());
      iter1.remove();

      assertTrue(iter1.hasNext());
      assertEquals(1, iter1.next().intValue());

      // The others should get nudged onto the next value up
      assertEquals(1, iter3.next().intValue());
      assertEquals(1, iter4.next().intValue());
      assertEquals(1, iter5.next().intValue());

      assertTrue(iter4.hasNext());
      assertEquals(2, iter4.next().intValue());
      assertEquals(3, iter4.next().intValue());
      assertEquals(4, iter4.next().intValue());
      assertEquals(5, iter4.next().intValue());
      assertEquals(6, iter4.next().intValue());
      assertEquals(7, iter4.next().intValue());
      assertEquals(8, iter4.next().intValue());
      assertEquals(9, iter4.next().intValue());
      assertFalse(iter4.hasNext());

      assertTrue(iter5.hasNext());
      assertEquals(2, iter5.next().intValue());
      assertEquals(3, iter5.next().intValue());
      assertEquals(4, iter5.next().intValue());
      assertEquals(5, iter5.next().intValue());
      assertEquals(6, iter5.next().intValue());

      assertTrue(iter3.hasNext());
      assertEquals(2, iter3.next().intValue());
      assertEquals(3, iter3.next().intValue());
      assertEquals(4, iter3.next().intValue());

      assertTrue(iter2.hasNext());
      assertEquals(2, iter2.next().intValue());
      assertEquals(3, iter2.next().intValue());
      assertEquals(4, iter2.next().intValue());

      assertTrue(iter1.hasNext());
      assertEquals(2, iter1.next().intValue());
      assertEquals(3, iter1.next().intValue());
      assertEquals(4, iter1.next().intValue());

      // 1, 2, 3 are on element 4

      iter2.remove();
      assertEquals(5, iter2.next().intValue());
      iter2.remove();

      // Should be nudged to element 6

      assertTrue(iter1.hasNext());
      assertEquals(6, iter1.next().intValue());
      assertTrue(iter2.hasNext());
      assertEquals(6, iter2.next().intValue());
      assertTrue(iter3.hasNext());
      assertEquals(6, iter3.next().intValue());

      iter5.remove();
      assertTrue(iter5.hasNext());
      assertEquals(7, iter5.next().intValue());

      // Should be nudged to 7

      assertTrue(iter1.hasNext());
      assertEquals(7, iter1.next().intValue());
      assertTrue(iter2.hasNext());
      assertEquals(7, iter2.next().intValue());
      assertTrue(iter3.hasNext());
      assertEquals(7, iter3.next().intValue());

      // Delete last element

      assertTrue(iter5.hasNext());
      assertEquals(8, iter5.next().intValue());
      assertTrue(iter5.hasNext());
      assertEquals(9, iter5.next().intValue());
      assertFalse(iter5.hasNext());

      iter5.remove();

      // iter4 should be nudged back to 8, now remove element 8
      iter4.remove();

      // add a new element on tail

      list.addTail(10);

      // should be nudged back to 7

      assertTrue(iter5.hasNext());
      assertEquals(7, iter5.next().intValue());
      assertTrue(iter5.hasNext());
      assertEquals(10, iter5.next().intValue());

      assertTrue(iter4.hasNext());
      assertEquals(7, iter4.next().intValue());
      assertTrue(iter4.hasNext());
      assertEquals(10, iter4.next().intValue());

      assertTrue(iter3.hasNext());
      assertEquals(10, iter3.next().intValue());

      assertTrue(iter2.hasNext());
      assertEquals(10, iter2.next().intValue());

      assertTrue(iter1.hasNext());
      assertEquals(10, iter1.next().intValue());

   }


   @Test
   public void testGetElement() {

      for (int i = 0; i < 100; i++) {
         list.addTail(i);
      }

      for (int i = 0; i < 100; i++) {
         assertEquals(i, list.get(i).intValue());
      }

      boolean expectedException = false;

      try {
         list.get(100);
      } catch (IndexOutOfBoundsException e) {
         expectedException = true;
      }

      assertTrue(expectedException);
   }

   @Test
   public void testResizing() {
      int numIters = 1000;

      List<LinkedListIterator<Integer>> iters = new java.util.LinkedList<>();

      int num = 10;

      for (int i = 0; i < num; i++) {
         list.addTail(i);
      }

      for (int i = 0; i < numIters; i++) {
         LinkedListIterator<Integer> iter = list.iterator();

         iters.add(iter);

         for (int j = 0; j < num / 2; j++) {
            assertTrue(iter.hasNext());

            assertEquals(j, iter.next().intValue());
         }
      }

      assertEquals(numIters, list.numIters());

      // Close the odd ones

      boolean b = false;
      for (LinkedListIterator<Integer> iter : iters) {
         if (b) {
            iter.close();
         }
         b = !b;
      }

      assertEquals(numIters / 2, list.numIters());

      // close the even ones

      b = true;
      for (LinkedListIterator<Integer> iter : iters) {
         if (b) {
            iter.close();
         }
         b = !b;
      }

      assertEquals(0, list.numIters());

   }
}
