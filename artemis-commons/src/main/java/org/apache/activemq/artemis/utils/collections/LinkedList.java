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

public interface LinkedList<E> {

   void addHead(E e);

   void addTail(E e);

   E get(int position);

   E poll();

   E peek();

   LinkedListIterator<E> iterator();

   void clear();

   int size();

   void clearID();

   /**
    * this makes possibl to use {@link #removeWithID(String, long)}
    */
   void setNodeStore(NodeStore<E> store);

   /**
    * you need to call {@link #setNodeStore(NodeStore)} before you are able to call this method.
    */
   E removeWithID(String listID, long id);

   void forEach(Consumer<E> consumer);
}
