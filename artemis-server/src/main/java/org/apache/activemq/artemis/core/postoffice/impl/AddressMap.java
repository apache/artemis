/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.activemq.artemis.core.postoffice.impl;

import org.apache.activemq.artemis.api.core.SimpleString;

public class AddressMap<T> {

   private final AddressPartNode<T> rootNode;
   private final char DELIMITER;

   public AddressMap(final String any, String single, char delimiter) {
      rootNode = new AddressPartNode<>(any, single);
      this.DELIMITER = delimiter;
   }

   public void put(final SimpleString key, T value) {
      rootNode.add(getPaths(key), 0, value);
   }

   public void remove(final SimpleString key, T value) {
      rootNode.remove(getPaths(key), 0, value);
   }

   public void reset() {
      rootNode.reset();
   }

   public String[] getPaths(final SimpleString address) {
      return address.getPaths(DELIMITER);
   }

   public AddressPartNode<T> getRootNode() {
      return rootNode;
   }

   /**
    * @param address a non wildcard to match against wildcards in the map
    */
   public void visitMatchingWildcards(SimpleString address,
                                      AddressMapVisitor<T> collector) throws Exception {

      final String[] paths = getPaths(address);
      rootNode.visitMatchingWildcards(paths, 0, collector);
   }

   /**
    * @param wildcardAddress a wildcard address to match against non wildcards in the map
    */
   public void visitMatching(SimpleString wildcardAddress,
                             AddressMapVisitor<T> collector) throws Exception {
      final String[] paths = getPaths(wildcardAddress);
      rootNode.visitNonWildcard(paths, 0, collector);
   }

}

