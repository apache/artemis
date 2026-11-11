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

/**
 * Extends MultiIterator, adding the ability if the underlying iterators are resettable, then its self can reset. It
 * achieves this by going back to the first iterator, and as moves to another iterator it resets it.
 *
 * @param <E> type of the class of the iterator.
 */
public class MultiResettableIterator<E> extends MultiIteratorBase<E, ResettableIterator<E>> implements ResettableIterator<E> {

   public MultiResettableIterator(ResettableIterator<E>[] iterators) {
      super(iterators);
   }

   @Override
   protected void moveTo(int index) {
      super.moveTo(index);
      if (index > -1) {
         get(index).reset();
      }
   }

   @Override
   public void reset() {
      moveTo(-1);
   }
}
