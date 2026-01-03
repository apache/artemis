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
package org.apache.activemq.artemis.core.management.impl.view.predicate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public abstract class ActiveMQFilterPredicate<T, V extends PredicateFilterPart<T>> implements Predicate<T> {

   public enum Operation {
      CONTAINS, NOT_CONTAINS, EQUALS, NOT_EQUALS, GREATER_THAN, LESS_THAN;
   }

   private final List<V> filterParts = new ArrayList<>();

   public ActiveMQFilterPredicate() {
   }

   @Override
   public boolean test(T input) {
      if (filterParts.isEmpty())
         return true;
      try {
         boolean matches = true;
         for (V filterPart : filterParts) {
            matches = filter(input, filterPart);
            if (!matches) {
               return matches;
            }
         }
         return matches;
      } catch (Exception e) {
         return true;
      }
   }

   public void addFilterParts(List<V> filterParts) {
      this.filterParts.addAll(filterParts);
   }

   protected abstract boolean filter(T input, V filterPart) throws Exception;

   public abstract V createFilterPart(String field, String operation, String value);
}
