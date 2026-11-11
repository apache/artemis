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
package org.apache.activemq.artemis.core.postoffice.impl;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.function.IntFunction;

final class IntegerCache {

   private static final boolean DISABLE_INTEGER_CACHE = Boolean.valueOf(System.getProperty("disable.integer.cache", Boolean.FALSE.toString()));

   // we're not interested into safe publication here: we need to scale, be fast and save "some" GC to happen
   private static WeakReference<Integer[]> INDEXES = null;

   private static Integer[] ints(int size) {
      final Reference<Integer[]> indexesRef = INDEXES;
      final Integer[] indexes = indexesRef == null ? null : indexesRef.get();
      if (indexes != null && size <= indexes.length) {
         return indexes;
      }
      final int newSize = size + (indexes == null ? 0 : size / 2);
      final Integer[] newIndexes = new Integer[newSize];
      if (indexes != null) {
         System.arraycopy(indexes, 0, newIndexes, 0, indexes.length);
      }
      INDEXES = new WeakReference<>(newIndexes);
      return newIndexes;
   }

   public static IntFunction<Integer> boxedInts(int size) {
      if (DISABLE_INTEGER_CACHE) {
         return Integer::valueOf;
      }
      // use a lambda to have an trusted const field for free
      final Integer[] cachedInts = ints(size);
      return index -> {
         Integer boxedInt = cachedInts[index];
         if (boxedInt == null) {
            boxedInt = index;
            cachedInts[index] = boxedInt;
         }
         assert boxedInt != null;
         return boxedInt;
      };
   }

}
