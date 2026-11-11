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
 * This interface is meant to encapsulate the usage of {@code HashMap<ListID, LongObjectHashMap<ElementType>>}
 * The implementation should store the node in such way that you can access it through {@link #getNode(String, long)}
 */
public interface NodeStore<E> {

   /**
    * When you store the node, make sure you find what is the ID and ListID for the element you are storing as later one
    * you will need to provide the node based on list and id as specified on {@link #getNode(String, long)}
    */
   void storeNode(E element, LinkedListImpl.Node<E> node);

   LinkedListImpl.Node<E> getNode(String listID, long id);

   void removeNode(E element, LinkedListImpl.Node<E> node);

   default NodeStore<E> setName(String name) {
      return this;
   }

   default String getName() {
      return null;
   }

   /**
    * This is meant to be a quick help to Garbage Collection. Whenever the IDSupplier list is being cleared, you should
    * first call the clear method and empty every list before you let the instance go.
    */
   void clear();

   /**
    * ths should return the sum of all the sizes. for test assertions.
    */
   int size();
}
