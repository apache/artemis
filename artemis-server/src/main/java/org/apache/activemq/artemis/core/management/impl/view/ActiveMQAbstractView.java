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
package org.apache.activemq.artemis.core.management.impl.view;

import org.apache.activemq.artemis.core.management.impl.view.predicate.PredicateFilterPart;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.activemq.artemis.api.core.JsonUtil;
import org.apache.activemq.artemis.core.management.impl.view.predicate.ActiveMQFilterPredicate;
import org.apache.activemq.artemis.json.JsonArray;
import org.apache.activemq.artemis.json.JsonArrayBuilder;
import org.apache.activemq.artemis.json.JsonObject;
import org.apache.activemq.artemis.json.JsonObjectBuilder;
import org.apache.activemq.artemis.json.JsonValue;
import org.apache.activemq.artemis.utils.JsonLoader;

public abstract class ActiveMQAbstractView<T, V extends PredicateFilterPart<T>> {

   // use this for values which couldn't be retrieved (e.g. an exception was thrown)
   protected static final String N_A = "n/a";

   private static final String FILTER_ARRAY_FIELD = "searchFilters";

   private static final String FILTER_FIELD = "field";

   private static final String FILTER_OPERATION = "operation";

   private static final String FILTER_VALUE = "value";

   private static final String SORT_ORDER = "sortOrder";

   private static final String ASCENDING = "asc";

   private static final String DESCENDING = "desc";

   @Deprecated(forRemoval = true)
   private static final String SORT_COLUMN = "sortColumn";

   private static final String SORT_FIELD = "sortField";

   private static final JsonObject DEFAULT_FILTER = JsonUtil.toJsonObject(Map.of(FILTER_FIELD, "", FILTER_OPERATION, "", FILTER_VALUE, ""));

   protected Collection<T> collection;

   protected ActiveMQFilterPredicate<T, V> predicate;

   protected String sortField;

   protected String sortOrder;

   public ActiveMQAbstractView() {
      this.sortField = getDefaultOrderColumn();
      this.sortOrder = ASCENDING;
   }

   public void setCollection(Collection<T> collection) {
      this.collection = collection;
   }

   public List<T> filter() {
      List<T> collect = collection.stream().filter(getPredicate()).collect(Collectors.toList());
      return collect;
   }

   public String getResultsAsJson(int page, int pageSize) {
      JsonObjectBuilder obj = JsonLoader.createObjectBuilder();
      JsonArrayBuilder array = JsonLoader.createArrayBuilder();
      collection = collection.stream().filter(getPredicate()).collect(Collectors.toList());
      for (T element : getPagedResult(page, pageSize)) {
         JsonObjectBuilder jsonObjectBuilder = toJson(element);
         //toJson() may return a null
         if (jsonObjectBuilder != null) {
            array.add(jsonObjectBuilder);
         }
      }
      obj.add("data", array);
      obj.add("count", collection.size());
      return obj.build().toString();
   }

   public String AsJson(int page, int pageSize) {
      JsonObjectBuilder obj = JsonLoader.createObjectBuilder();
      JsonArrayBuilder array = JsonLoader.createArrayBuilder();
      for (T element : getPagedResult(page, pageSize)) {
         JsonObjectBuilder jsonObjectBuilder = toJson(element);
         //toJson() may return a null
         if (jsonObjectBuilder != null) {
            array.add(jsonObjectBuilder);
         }
      }
      obj.add("data", array);
      obj.add("count", collection.size());
      return obj.build().toString();
   }

   public List<T> getPagedResult(int page, int pageSize) {
      List<T> builder = new ArrayList<>();
      final int start;
      final int end;
      if (page == -1 || pageSize == -1) {
         start = 0;
         end = collection.size();
      } else {
         start = (page - 1) * pageSize;
         end = Math.min(page * pageSize, collection.size());
      }
      int i = 0;
      for (T e : collection.stream().sorted(getComparator()).collect(Collectors.toList())) {
         if (i >= start && i < end) {
            builder.add(e);
         }
         i++;
      }
      return Collections.unmodifiableList(builder);
   }

   public Predicate<T> getPredicate() {
      return predicate;
   }

   public Comparator<T> getComparator() {
      return (left, right) -> {
         try {
            Object leftValue = getField(left, sortField);
            Object rightValue = getField(right, sortField);
            if (leftValue instanceof Comparable l && rightValue instanceof Comparable r) {
               if (sortOrder.equalsIgnoreCase(DESCENDING)) {
                  return r.compareTo(leftValue);
               } else {
                  return l.compareTo(rightValue);
               }
            }
            return 0;
         } catch (Exception e) {
            //LOG.info("Exception sorting destinations", e);
            return 0;
         }
      };
   }

   abstract Object getField(T t, String fieldName);

   public void setOptions(String options) {
      JsonObject json;
      if (options == null || options.isBlank()) {
         json = DEFAULT_FILTER;
      } else {
         json = JsonUtil.readJsonObject(options);
      }
      if (predicate != null) {
         predicate.addFilterParts(createFilterPredicates(json));
         if ((json.containsKey(SORT_COLUMN) || json.containsKey(SORT_FIELD)) && json.containsKey(SORT_ORDER)) {
            if (json.containsKey(SORT_COLUMN)) {
               this.sortField = json.getString(SORT_COLUMN);
            } else {
               this.sortField = json.getString(SORT_FIELD);
            }
            this.sortOrder = json.getString(SORT_ORDER);
         }
      }
   }

   private List<V> createFilterPredicates(JsonObject json) {
      ArrayList<V> predicates = new ArrayList<>();
      JsonArray jsonArray = json.getJsonArray(FILTER_ARRAY_FIELD);
      if (jsonArray == null) {
         predicates.add(predicate.createFilterPart(json.getString(FILTER_FIELD), json.getString(FILTER_OPERATION), json.getString(FILTER_VALUE)));
      } else {
         for (JsonValue jsonValue : jsonArray) {
            predicates.add(predicate.createFilterPart(((JsonObject)jsonValue).getString(FILTER_FIELD), ((JsonObject)jsonValue).getString(FILTER_OPERATION), ((JsonObject)jsonValue).getString(FILTER_VALUE)));
         }
      }
      return predicates;
   }

   public abstract Class getClassT();

   public abstract JsonObjectBuilder toJson(T obj);

   public abstract String getDefaultOrderColumn();

   public String getSortField() {
      return sortField;
   }

   public String getSortOrder() {
      return sortOrder;
   }

   /**
    * JsonObjectBuilder will throw an NPE if a null value is added.  For this reason we check for null explicitly when
    * adding objects.
    */
   protected String toString(Object o) {
      return o == null ? "" : o.toString();
   }
}
