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

import java.util.NoSuchElementException;
import java.util.function.Consumer;

public class EmptyList<E> implements LinkedList<E> {


   private static final LinkedList EMPTY_LIST = new EmptyList();

   public static final <T> LinkedList<T> getEmptyList() {
      return (LinkedList<T>) EMPTY_LIST;
   }

   private EmptyList() {
   }

   @Override
   public E peek() {
      return null;
   }

   @Override
   public void addHead(E e) {
      throw new UnsupportedOperationException("method not supported");
   }

   @Override
   public void addTail(E e) {
      throw new UnsupportedOperationException("method not supported");
   }

   @Override
   public E get(int position) {
      throw new IndexOutOfBoundsException("position = " + position);
   }

   @Override
   public E poll() {
      return null;
   }

   LinkedListIterator<E> emptyIterator = new LinkedListIterator<>() {
      @Override
      public void repeat() {
      }

      @Override
      public E removeLastElement() {
         return null;
      }

      @Override
      public void close() {
      }

      @Override
      public boolean hasNext() {
         return false;
      }

      @Override
      public E next() {
         throw new NoSuchElementException();
      }
   };

   @Override
   public LinkedListIterator<E> iterator() {
      return emptyIterator;
   }

   @Override
   public void clear() {
      throw new UnsupportedOperationException("method not supported");
   }

   @Override
   public int size() {
      return 0;
   }

   @Override
   public void clearID() {
      throw new UnsupportedOperationException("method not supported");
   }

   @Override
   public void setNodeStore(NodeStore<E> store) {
      throw new UnsupportedOperationException("method not supported");
   }

   @Override
   public E removeWithID(String listID, long id) {
      throw new UnsupportedOperationException("method not supported");
   }

   @Override
   public void forEach(Consumer<E> consumer) {
   }
}
