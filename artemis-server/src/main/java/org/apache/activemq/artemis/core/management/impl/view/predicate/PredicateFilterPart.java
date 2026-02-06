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

import java.util.Collection;

public class PredicateFilterPart<T> {

   private final  String value;

   private final  String operation;

   private final ActiveMQFilterPredicate.Operation filterOperation;

   public PredicateFilterPart(String operation, String value) {
      this.value = value;
      this.operation = operation;
      if (operation != null && !operation.isBlank()) {
         this.filterOperation = ActiveMQFilterPredicate.Operation.valueOf(operation);
      } else {
         filterOperation = null;
      }
   }

   public String getOperation() {
      return operation;
   }

   public String getValue() {
      return value;
   }

   public ActiveMQFilterPredicate.Operation getFilterOperation() {
      return filterOperation;
   }

   public boolean filterPart(T input) throws Exception {
      return true;
   }
   public boolean matchesLong(long field) {
      long longValue;
      if (operation != null) {

         try {
            longValue = Long.parseLong(value);
         } catch (NumberFormatException ex) {
            //cannot compare
            if (filterOperation == ActiveMQFilterPredicate.Operation.NOT_EQUALS || filterOperation == ActiveMQFilterPredicate.Operation.NOT_CONTAINS) {
               return true;
            } else {
               return false;
            }
         }

         return switch (filterOperation) {
            case EQUALS -> field == longValue;
            case NOT_EQUALS -> field != longValue;
            case CONTAINS -> false;
            case NOT_CONTAINS -> true;
            case LESS_THAN -> field < longValue;
            case GREATER_THAN -> field > longValue;
         };
      }
      return true;
   }

   public boolean matches(Object field) {
      if (filterOperation != null) {
         return switch (filterOperation) {
            case EQUALS -> equals(field, value);
            case NOT_EQUALS -> !equals(field, value);
            case CONTAINS -> contains(field, value);
            case NOT_CONTAINS -> !contains(field, value);
            case GREATER_THAN -> false;
            case LESS_THAN -> false;
         };
      }
      return true;
   }


   public boolean matchesInt(int field) {
      int intValue;
      if (filterOperation != null) {

         try {
            intValue = Integer.parseInt(value);
         } catch (NumberFormatException ex) {
            //cannot compare
            if (filterOperation == ActiveMQFilterPredicate.Operation.NOT_EQUALS || filterOperation == ActiveMQFilterPredicate.Operation.NOT_CONTAINS) {
               return true;
            } else {
               return false;
            }
         }

         return switch (filterOperation) {
            case EQUALS -> field == intValue;
            case NOT_EQUALS -> field != intValue;
            case CONTAINS -> false;
            case NOT_CONTAINS -> true;
            case LESS_THAN -> field < intValue;
            case GREATER_THAN -> field > intValue;
         };
      }
      return true;
   }


   public boolean matchesFloat(float field) {
      float floatValue;
      if (operation != null) {

         try {
            floatValue = Float.parseFloat(value);
         } catch (NumberFormatException ex) {
            //cannot compare
            if (filterOperation == ActiveMQFilterPredicate.Operation.NOT_EQUALS || filterOperation == ActiveMQFilterPredicate.Operation.NOT_CONTAINS) {
               return true;
            } else {
               return false;
            }
         }

         return switch (filterOperation) {
            case EQUALS -> field == floatValue;
            case NOT_EQUALS -> field != floatValue;
            case CONTAINS -> false;
            case NOT_CONTAINS -> true;
            case LESS_THAN -> field < floatValue;
            case GREATER_THAN -> field > floatValue;
         };
      }
      return true;
   }
   public boolean matchAny(Collection objects) {
      for (Object o : objects) {
         if (matches(o))
            return true;
      }
      return false;
   }

   private boolean equals(Object field, Object value) {
      if (field == null) {
         return (value == null || value.equals(""));
      }
      return field.toString().equals(value);
   }

   private boolean contains(Object field, Object value) {
      if (field == null) {
         return (value == null || value.equals(""));
      }
      return field.toString().contains(value.toString());
   }
}
