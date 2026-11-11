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
package org.apache.activemq.artemis.utils.collections;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Array;
import java.util.Comparator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A linked list implementation which allows multiple iterators to exist at the same time on the queue, and which see
 * any elements added or removed from the queue either directly or via iterators.
 * <p>
 * This class is not thread safe.
 */
public class LinkedListImpl<E> implements LinkedList<E> {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final int INITIAL_ITERATOR_ARRAY_SIZE = 10;

   private final Node<E> head = new NodeHolder<>(null);
   private final Comparator<E> comparator;
   private Node<E> tail = null;
   private int size;
   // We store in an array rather than a Map for the best performance
   private volatile Iterator[] iters;
   private int numIters;
   private int nextIndex;
   private NodeStore<E> nodeStore;

   private volatile Node<E> lastAdd;

   public LinkedListImpl() {
      this(null, null);
   }

   public LinkedListImpl(Comparator<E> comparator) {
      this(comparator, null);
   }

   public LinkedListImpl(Comparator<E> comparator, NodeStore<E> supplier) {
      iters = createIteratorArray(INITIAL_ITERATOR_ARRAY_SIZE);
      this.comparator = comparator;
      this.nodeStore = supplier;
   }

   @Override
   public void clearID() {
      if (nodeStore != null) {
         nodeStore.clear();
      }
      nodeStore = null;
   }

   @Override
   public void setNodeStore(NodeStore<E> supplier) {
      this.nodeStore = supplier;

      try (Iterator iterator = (Iterator) iterator()) {
         while (iterator.hasNext()) {
            E value = iterator.next();
            Node<E> position = iterator.last;
            putID(value, position);
         }
      }
   }

   private void putID(E element, Node<E> node) {
      nodeStore.storeNode(element, node);
   }

   @Override
   public void addHead(E e) {
      Node<E> node = Node.with(e);

      node.next = head.next;

      node.prev = head;

      head.next = node;

      if (size == 0) {
         tail = node;
      } else {
         // Need to set the previous element on the former head
         node.next.prev = node;
      }

      itemAdded(node, e);

      size++;
   }

   @Override
   public E peek() {
      Node<E> current = head.next;
      if (current == null) {
         return null;
      } else {
         return current.val();
      }
   }

   @Override
   public E get(int position) {
      Node<E> current = head.next;

      for (int i = 0; i < position && current != null; i++) {
         current = current.next;
      }

      if (current == null) {
         throw new IndexOutOfBoundsException(position + " > " + size());
      }

      return current.val();
   }

   @Override
   public synchronized E removeWithID(String listID, long id) {
      assert nodeStore != null; // it is assumed the code will call setNodeStore before callin removeWithID

      Node<E> node = nodeStore.getNode(listID, id);

      if (node == null) {
         return null;
      }

      // the node will always have a prev element
      removeAfter(node.prev);
      return node.val();
   }


   @Override
   public void forEach(Consumer<E> consumer) {
      try (LinkedListIterator<E> iter = iterator()) {
         while (iter.hasNext()) {
            E nextValue = iter.next();
            consumer.accept(nextValue);
         }
      }
   }

   private void itemAdded(Node<E> node, E item) {
      assert node.val() == item;
      lastAdd = node;
      if (logger.isTraceEnabled()) {
         logger.trace("Setting lastAdd as {}, e={}", lastAdd, lastAdd.val());
      }
      if (nodeStore != null) {
         putID(item, node);
      }
   }

   private void itemRemoved(Node<E> node) {
      lastAdd = null;
      if (nodeStore != null) {
         nodeStore.removeNode(node.val(), node);
      }
   }

   @Override
   public void addTail(E e) {
      if (size == 0) {
         addHead(e);
      } else {
         Node<E> node = Node.with(e);

         node.prev = tail;

         tail.next = node;

         tail = node;

         itemAdded(node, e);

         size++;
      }
   }

   public void addSorted(E e) {
      final Node<E> localLastAdd = lastAdd;

      logger.trace("**** addSorted element {}", e);

      Objects.requireNonNull(comparator, "comparator=null");

      if (size == 0) {
         logger.trace("adding head as there are no elements {}", e);
         addHead(e);
      } else {
         if (comparator.compare(head.next.val(), e) <= 0) {
            if (logger.isTraceEnabled()) {
               logger.trace("addHead as e={} and head={}", e, head.next.val());
            }
            addHead(e);
            return;
         }

         // In our usage, most of the times we will just add to the end as the QueueImpl cancellations in AMQP will
         // return the buffer back to the queue in the order they were consumed. There is an exception to that case,
         // when there are more messages on the queue. This would be an optimization for our usage - avoiding scanning
         // the entire List just to add at the end, so we compare the end first.
         if (comparator.compare(tail.val(), e) >= 0) {
            logger.trace("addTail as e={} and tail={}", e, tail.val());
            addTail(e);
            return;
         }

         if (localLastAdd != null) { // as an optimization we check against the last add rather than always scan.
            if (logger.isDebugEnabled()) {
               logger.debug("localLastAdd Value = {}, we are adding {}", localLastAdd.val(), e);
            }

            int compareLastAdd = comparator.compare(localLastAdd.val(), e);

            if (compareLastAdd > 0) {
               if (scanRight(localLastAdd, e)) {
                  return;
               }
            }

            if (compareLastAdd < 0) {
               if (scanLeft(localLastAdd, e)) {
                  return;
               }
            }
         }

         if (addSortedScan(e)) {
            return;
         }

         // This shouldn't happen as the tail was compared before iterating the only possibilities for this to happen
         // are:
         // - there is a bug on the comparator
         // - This method is buggy
         // - The list wasn't properly synchronized as this list does't support concurrent access
         //
         // Also I'm not bothering about creating a Logger ID for this, because the only reason for this code to exist
         // is because my OCD level is not letting this out.
         throw new IllegalStateException("Cannot find a suitable place for your element, There's a mismatch in the comparator or there was concurrent adccess on the queue");
      }
   }

   protected boolean scanRight(Node<E> position, E e) {
      Node<E> fetching = position.next;
      while (fetching != null) {
         if (comparator.compare(fetching.val(), e) <= 0) {
            addAfter(position, e);
            return true;
         }
         position = fetching;
         fetching = fetching.next;
      }
      return false; // unlikely to happen, using this just to be safe
   }

   protected boolean scanLeft(Node<E> position, E e) {
      Node<E> fetching = position.prev;
      while (fetching != null) {
         if (comparator.compare(fetching.val(), e) >= 0) {
            addAfter(fetching, e);
            return true;
         }
         fetching = fetching.prev;
      }
      return false; // unlikely to happen, using this just to be safe
   }

   protected boolean addSortedScan(E e) {
      logger.trace("addSortedScan {}...", e);
      Node<E> fetching = head.next;
      while (fetching.next != null) {
         int compareNext = comparator.compare(fetching.next.val(), e);
         if (compareNext <= 0) {
            addAfter(fetching, e);
            logger.trace("... addSortedScan done, returning true");
            return true;
         }
         fetching = fetching.next;
      }
      logger.trace("... addSortedScan done, could not find a spot, returning false");
      return false;
   }

   private void addAfter(Node<E> node, E e) {
      Node<E> newNode = Node.with(e);
      Node<E> nextNode = node.next;
      node.next = newNode;
      newNode.prev = node;
      newNode.next = nextNode;
      nextNode.prev = newNode;
      itemAdded(newNode, e);
      size++;
   }

   @Override
   public E poll() {
      Node<E> ret = head.next;

      if (ret != null) {
         removeAfter(head);

         return ret.val();
      } else {
         return null;
      }
   }

   @Override
   public void clear() {
      // Clearing all of the links between nodes is "unnecessary", but:
      // - helps a generational GC if the discarded nodes inhabit more than one generation
      // - is sure to free memory even if there is a reachable Iterator
      while (poll() != null) {

      }
   }

   @Override
   public int size() {
      return size;
   }

   @Override
   public LinkedListIterator<E> iterator() {
      if (logger.isTraceEnabled()) {
         logger.trace("Creating new iterator at", new Exception("trace location"));
      }
      return new Iterator();
   }

   @Override
   public String toString() {
      StringBuilder str = new StringBuilder("LinkedListImpl [ ");

      Node<E> node = head;

      while (node != null) {
         str.append(node.toString());

         if (node.next != null) {
            str.append(", ");
         }

         node = node.next;
      }

      return str.toString();
   }

   public int numIters() {
      return numIters;
   }

   private Iterator[] createIteratorArray(int size) {
      return (Iterator[]) Array.newInstance(Iterator.class, size);
   }

   private void removeAfter(Node<E> node) {
      Node<E> toRemove = node.next;

      node.next = toRemove.next;

      if (toRemove.next != null) {
         toRemove.next.prev = node;
      }

      itemRemoved(toRemove);

      if (toRemove == tail) {
         tail = node;
      }

      size--;

      if (toRemove.iterCount != 0) {
         LinkedListImpl.this.nudgeIterators(toRemove);
      }

      // Help GC - otherwise GC potentially has to traverse a very long list to see if elements are reachable, this can
      // result in OOM
      toRemove.next = toRemove.prev = null;
   }

   private synchronized void nudgeIterators(Node<E> node) {
      for (int i = 0; i < numIters; i++) {
         Iterator iter = iters[i];
         if (iter != null) {
            iter.nudged(node);
         }
      }
   }

   private synchronized void addIter(Iterator iter) {
      if (numIters == iters.length) {
         resize(2 * numIters);
      }

      iters[nextIndex++] = iter;

      numIters++;
   }

   private synchronized void resize(int newSize) {
      Iterator[] newIters = createIteratorArray(newSize);

      System.arraycopy(iters, 0, newIters, 0, numIters);

      iters = newIters;
   }

   private synchronized void removeIter(Iterator iter) {
      if (logger.isTraceEnabled()) {
         logger.trace("Removing iterator at", new Exception("trace location"));
      }
      for (int i = 0; i < numIters; i++) {
         if (iter == iters[i]) {
            iters[i] = null;

            if (i != numIters - 1) {
               // Fill in the hole

               System.arraycopy(iters, i + 1, iters, i, numIters - i - 1);
            }

            numIters--;

            if (numIters >= INITIAL_ITERATOR_ARRAY_SIZE && numIters == iters.length / 2) {
               resize(numIters);
            }
            nextIndex--;
            if (nextIndex < iters.length) {
               iters[nextIndex] = null;
            }

            return;
         }
      }

      throw new IllegalStateException("Cannot find iter to remove");
   }

   private static final class NodeHolder<E> extends Node<E> {

      private final E val;

      //only the head is allowed to hold a null
      private NodeHolder(E e) {
         val = e;
      }

      @Override
      protected E val() {
         return val;
      }
   }

   public static class Node<E> {

      private Node<E> next;

      private Node<E> prev;

      private int iterCount;

      private static <E> Node<E> with(final E o) {
         Objects.requireNonNull(o, "Only HEAD nodes are allowed to hold null values");
         if (o instanceof Node node) {
            //only a node that not belong already to a list is allowed to be reused
            if (node.prev == null && node.next == null) {
               //reset the iterCount
               node.iterCount = 0;
               return node;
            }
         }
         return new NodeHolder<>(o);
      }

      @SuppressWarnings("unchecked")
      protected E val() {
         return (E) this;
      }

      protected final LinkedListImpl.Node<E> next() {
         return next;
      }

      protected final LinkedListImpl.Node<E> prev() {
         return prev;
      }

      @Override
      public String toString() {
         return val() == this ? "Intrusive Node" : "Node, value = " + val();
      }
   }

   public class Iterator implements LinkedListIterator<E> {
      Node<E> last;

      Node<E> current = head.next;

      boolean repeat;

      Iterator() {
         if (current != null) {
            current.iterCount++;
         }

         addIter(this);
      }

      @Override
      public void repeat() {
         repeat = true;
      }

      @Override
      public boolean hasNext() {
         synchronized (LinkedListImpl.this) {
            Node<E> e = getNode();

            if (e != null && (e != last || repeat)) {
               return true;
            }

            return canAdvance();
         }
      }

      @Override
      public E next() {
         synchronized (LinkedListImpl.this) {
            Node<E> e = getNode();

            if (repeat) {
               repeat = false;

               if (e != null) {
                  return e.val();
               } else {
                  if (canAdvance()) {
                     advance();

                     e = getNode();

                     return e.val();
                  } else {
                     throw new NoSuchElementException();
                  }
               }
            }

            if (e == null || e == last) {
               if (canAdvance()) {
                  advance();

                  e = getNode();
               } else {
                  throw new NoSuchElementException();
               }
            }

            last = e;

            repeat = false;

            return e.val();
         }
      }

      @Override
      public void remove() {
         removeLastElement();
      }

      @Override
      public E removeLastElement() {
         synchronized (LinkedListImpl.this) {
            if (last == null) {
               throw new NoSuchElementException();
            }

            if (current == null) {
               return null;
            }

            E returningElement = current.val();

            Node<E> prev = current.prev;

            if (prev != null) {
               LinkedListImpl.this.removeAfter(prev);

               last = null;
            }

            return returningElement;
         }
      }

      @Override
      public void close() {
         removeIter(this);
      }

      public void nudged(Node<E> node) {
         if (current == node) {
            if (canAdvance()) {
               advance();
            } else {
               if (current.prev != head) {
                  current.iterCount--;

                  current = current.prev;

                  current.iterCount++;

                  if (last == node) {
                     last = current;
                  }
               } else {
                  current = null;
               }
            }
         }
      }

      private Node<E> getNode() {
         if (current == null) {
            current = head.next;

            if (current != null) {
               current.iterCount++;
            }
         }

         if (current != null) {
            return current;
         } else {
            return null;
         }
      }

      private boolean canAdvance() {
         if (current == null) {
            current = head.next;

            if (current != null) {
               current.iterCount++;
            }
         }

         return current != null && current.next != null;
      }

      private void advance() {
         if (current == null || current.next == null) {
            throw new NoSuchElementException();
         }

         current.iterCount--;

         current = current.next;

         current.iterCount++;
      }

   }
}
