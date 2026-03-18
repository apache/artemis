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

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

public class SingletonIterator<E> implements Iterator<E> {

   private E value;

   public static <E> Iterator<E> newInstance(E e) {
      return new SingletonIterator<>(e);
   }

   private SingletonIterator(E value) {
      this.value = value;
   }

   @Override
   public boolean hasNext() {
      return value != null;
   }

   @Override
   public E next() {
      if (value != null) {
         E result = value;
         value = null;
         return result;
      } else {
         throw new NoSuchElementException();
      }
   }

   @Override
   public void remove() {
      value = null;
   }

   @Override
   public void forEachRemaining(Consumer<? super E> action) {
      if (value != null) {
         action.accept(value);
      }
   }
}
