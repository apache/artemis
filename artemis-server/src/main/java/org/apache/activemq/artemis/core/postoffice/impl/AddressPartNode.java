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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class AddressPartNode<T> {

   protected final String ANY_CHILD;
   protected final String ANY_DESCENDENT;

   private final AddressPartNode<T> parent;
   private final List<T> values = new CopyOnWriteArrayList<>();
   private final Map<String, AddressPartNode<T>> childNodes = new ConcurrentHashMap<>();
   private final String path;

   public AddressPartNode(final String path, final AddressPartNode<T> parent) {
      this.parent = parent;
      this.ANY_DESCENDENT = parent.ANY_DESCENDENT;
      this.ANY_CHILD = parent.ANY_CHILD;

      // allow '==' equality wildcard nodes
      if (ANY_DESCENDENT.equals(path)) {
         this.path = ANY_DESCENDENT;
      } else if (ANY_CHILD.equals(path)) {
         this.path = ANY_CHILD;
      } else {
         this.path = path;
      }
   }

   public AddressPartNode(String anyDescendent, String anyChild) {
      ANY_DESCENDENT = anyDescendent;
      ANY_CHILD = anyChild;
      path = "Root";
      parent = null;
   }

   public AddressPartNode<T> getChild(final String path) {
      return childNodes.get(path);
   }

   public Collection<AddressPartNode<T>> getChildren() {
      return childNodes.values();
   }

   public AddressPartNode<T> getChildOrCreate(final String path) {
      AddressPartNode<T> answer = childNodes.get(path);
      if (answer == null) {
         answer = new AddressPartNode<>(path, this);
         childNodes.put(path, answer);
      }
      return answer;
   }

   public void add(final String[] paths, final int idx, final T value) {
      if (idx >= paths.length) {
         values.add(value);
      } else {
         getChildOrCreate(paths[idx]).add(paths, idx + 1, value);
      }
   }

   public void remove(final String[] paths, final int idx, final T value) {
      if (idx >= paths.length) {
         values.remove(value);
         pruneIfEmpty();
      } else {
         getChildOrCreate(paths[idx]).remove(paths, idx + 1, value);
      }
   }

   public void visitDescendantNonWildcardValues(final AddressMapVisitor<T> collector) throws Exception {
      visitValues(collector);
      for (AddressPartNode<T> child : childNodes.values()) {
         if (ANY_CHILD == child.getPath() || ANY_DESCENDENT == child.getPath()) {
            continue;
         }
         child.visitDescendantNonWildcardValues(collector);
      }
   }

   public void visitPathTailNonWildcard(final String[] paths,
                                  final int startIndex,
                                  final AddressMapVisitor<T> collector) throws Exception {

      if (childNodes.isEmpty()) {
         return;
      }

      // look for a path match after 0-N skips among children
      AddressPartNode<T> match = null;
      for (int i = startIndex; i < paths.length; i++) {
         match = getChild(paths[i]);
         if (match != null) {
            if (ANY_CHILD == match.getPath() || ANY_DESCENDENT == match.getPath()) {
               continue;
            }
            match.visitNonWildcard(paths, i + 1, collector);
            break;
         }
      }

      // walk the rest of the sub tree to find a tail path match
      for (AddressPartNode<T> child : childNodes.values()) {
         // instance equality arranged in node creation getChildOrCreate
         if (child != match && ANY_DESCENDENT != child.getPath() && ANY_CHILD != child.getPath()) {

            child.visitPathTailNonWildcard(paths, startIndex, collector);
         }
      }
   }

   public void visitPathTailMatch(final String[] paths, final int startIndex, final AddressMapVisitor<T> collector) throws Exception {

      if (childNodes.isEmpty()) {
         return;
      }

      // look for a path match after 0-N skips among immediate children
      List<AddressPartNode> visitedSet = new ArrayList<>(paths.length);
      for (int i = startIndex; i < paths.length; i++) {
         final AddressPartNode<T> match = getChild(paths[i]);
         if (match != null) {
            match.visitMatchingWildcards(paths, i + 1, collector);
            visitedSet.add(match);
         }
      }

      // walk the rest of the sub tree to find a tail path match
      for (AddressPartNode<T> child : childNodes.values()) {
         if (alreadyVisited(child, visitedSet)) {
            continue;
         }
         child.visitPathTailMatch(paths, startIndex, collector);
      }
   }

   private boolean alreadyVisited(final AddressPartNode<T> child, final List<AddressPartNode> matches) {
      if (!matches.isEmpty()) {
         for (AddressPartNode alreadyMatched : matches) {
            if (child == alreadyMatched) {
               return true;
            }
         }
      }
      return false;
   }

   // wildcards in the paths, ignore wildcard expansions in the map
   public void visitNonWildcard(final String[] paths, final int startIndex, final AddressMapVisitor<T> collector) throws Exception {
      boolean canVisitAnyDescendent = true;
      AddressPartNode<T> node = this;
      final int size = paths.length;
      for (int i = startIndex; i < size && node != null; i++) {

         final String path = paths[i];

         // snuff out any descendant postfix in the paths ....#
         if (ANY_DESCENDENT.equals(path)) {
            if (i == size - 1) {
               node.visitDescendantNonWildcardValues(collector);
               canVisitAnyDescendent = false;
               break;
            }
         }

         if (ANY_CHILD.equals(path)) {

            for (AddressPartNode<T> anyChild : node.getChildren()) {

               if (ANY_CHILD == anyChild.getPath() || ANY_DESCENDENT == anyChild.getPath()) {
                  continue;
               }

               anyChild.visitNonWildcard(paths, i + 1, collector);

            }
            // once we have done depth first on all children we are done with our paths
            return;

         } else if (ANY_DESCENDENT.equals(path)) {

            node.visitValues(collector);
            node.visitPathTailNonWildcard(paths, i + 1, collector);
            // once we have done depth first on all children we are done with our paths
            return;

         } else {

            node = node.getChild(path);

         }
      }
      if (node != null) {

         if (canVisitAnyDescendent) {

            node.visitValues(collector);

         }
      }
   }


   // non wildcard paths, match any expanded wildcards in the map
   public void visitMatchingWildcards(final String[] paths, final int startIndex, final AddressMapVisitor<T> collector) throws Exception {
      AddressPartNode<T> node = this;
      AddressPartNode<T> anyDescendentNode;
      AddressPartNode<T> anyChildNode;
      final int size = paths.length;
      for (int i = startIndex; i < size && node != null; i++) {

         final String path = paths[i];

         anyDescendentNode = node.getChild(ANY_DESCENDENT);
         if (anyDescendentNode != null) {

            anyDescendentNode.visitValues(collector);
            // match tail with current path, such that ANY_DESCENDENT can match zero
            anyDescendentNode.visitPathTailMatch(paths, i, collector);
         }

         anyChildNode = node.getChild(ANY_CHILD);
         if (anyChildNode != null) {
            anyChildNode.visitMatchingWildcards(paths, i + 1, collector);
         }

         node = node.getChild(path);

         if (node != null && (node == anyChildNode || node == anyDescendentNode)) {
            // down that path before, out of here
            return;
         }

      }
      if (node != null) {

         node.visitValues(collector);

         // allow zero node any descendant at the end of path node
         anyDescendentNode = node.getChild(ANY_DESCENDENT);
         if (anyDescendentNode != null) {
            anyDescendentNode.visitValues(collector);
         }
      }
   }

   public void visitValues(final AddressMapVisitor<T> collector) throws Exception {
      for (T o : values) {
         collector.visit(o);
      }
   }

   public String getPath() {
      return path;
   }

   protected void pruneIfEmpty() {
      if (parent != null && childNodes.isEmpty() && values.isEmpty()) {
         parent.removeChild(this);
      }
   }

   protected void removeChild(final AddressPartNode<T> node) {
      childNodes.remove(node.getPath());
      pruneIfEmpty();
   }

   public void reset() {
      values.clear();
      childNodes.clear();
   }
}

