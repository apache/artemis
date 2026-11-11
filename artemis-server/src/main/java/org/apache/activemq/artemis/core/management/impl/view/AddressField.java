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

import java.util.Map;
import java.util.TreeMap;

public enum AddressField {
   ID("id"),
   NAME("name"),
   ROUTING_TYPES("routingTypes"),
   QUEUE_COUNT("queueCount"),
   INTERNAL("internal"),
   TEMPORARY("temporary"),
   AUTO_CREATED("autoCreated"),
   PAUSED("paused"),
   CURRENT_DUPLICATE_ID_CACHE_SIZE("currentDuplicateIdCacheSize"),
   RETROACTIVE_RESOURCE("retroactiveResource"),
   UNROUTED_MESSAGE_COUNT("unroutedMessageCount"),
   ROUTED_MESSAGE_COUNT("routedMessageCount"),
   MESSAGE_COUNT("messageCount"),
   NUMBER_OF_BYTES_PER_PAGE("numberOfBytesPerPage"),
   ADDRESS_LIMIT_PERCENT("addressLimitPercent"),
   PAGING("paging"),
   NUMBER_OF_PAGES("numberOfPages"),
   ADDRESS_SIZE("addressSize"),
   MAX_PAGE_READ_BYTES("maxPageReadBytes"),
   MAX_PAGE_READ_MESSAGES("maxPageReadMessages"),
   PREFETCH_PAGE_BYTES("prefetchPageBytes"),
   PREFETCH_PAGE_MESSAGES("prefetchPageMessages");

   private static final Map<String, AddressField> lookup = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

   static {
      for (AddressField e: values()) {
         lookup.put(e.name, e);
      }
   }

   private final String name;

   public String getName() {
      return name;
   }

   AddressField(String name) {
      this.name = name;
   }

   public static AddressField valueOfName(String name) {
      return lookup.get(name);
   }
}
