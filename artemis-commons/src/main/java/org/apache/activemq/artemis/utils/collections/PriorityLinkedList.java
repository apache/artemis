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

import java.util.function.Supplier;

/**
 * A type of linked list which maintains items according to a priority and allows adding and removing of elements at
 * both ends, and peeking.
 * <p>
 * Only {@link #size()} and {@link #isEmpty()} are safe to be called concurrently.
 */
public interface PriorityLinkedList<E> {

   void addHead(E e, int priority);

   void addTail(E e, int priority);

   void addSorted(E e, int priority);

   E poll();

   /**
    * just look at the first element on the list
    */
   E peek();

   void clear();

   /**
    * @see LinkedList#setNodeStore(NodeStore)
    */
   void setNodeStore(Supplier<NodeStore<E>> supplier);

   E removeWithID(String listID, long id);

   /**
    * {@return the size of this list; safe to be called concurrently}
    */
   int size();

   LinkedListIterator<E> iterator();

   /**
    * {@return {@code true} if empty, {@code false} otherwise; safe to be called concurrently}
    */
   boolean isEmpty();
}
