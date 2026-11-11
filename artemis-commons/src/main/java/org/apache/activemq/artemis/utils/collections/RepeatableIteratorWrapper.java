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

import java.util.function.Consumer;

public class RepeatableIteratorWrapper<E> implements RepeatableIterator<E>, ResettableIterator<E> {

   private ResettableIterator<E> iterator;
   private E last;
   private boolean repeat;

   public RepeatableIteratorWrapper(ResettableIterator<E> iterator) {
      this.iterator = iterator;
   }

   @Override
   public void repeat() {
      if (last != null) {
         repeat = true;
      }
   }

   @Override
   public void removed(E removed) {
      if (last == removed) {
         last = null;
      }
   }

   @Override
   public boolean hasNext() {
      return repeat || iterator.hasNext();
   }

   @Override
   public E next() {
      if (repeat) {
         repeat = false;
         return last;
      }
      return last = iterator.next();
   }

   @Override
   public void remove() {
      iterator.remove();
   }

   @Override
   public void forEachRemaining(Consumer<? super E> action) {
      iterator.forEachRemaining(action);
   }

   @Override
   public void reset() {
      iterator.reset();
   }
}
