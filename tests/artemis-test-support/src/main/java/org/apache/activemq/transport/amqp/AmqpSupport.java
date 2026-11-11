/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.transport.amqp;

import java.util.AbstractMap;
import java.util.Map;

import org.apache.qpid.proton.amqp.DescribedType;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.UnsignedLong;

/**
 * Set of useful methods and definitions used in the AMQP protocol handling
 */
public class AmqpSupport {

   // Identification values used to locating JMS selector types.
   public static final UnsignedLong JMS_SELECTOR_CODE = UnsignedLong.valueOf(0x0000468C00000004L);
   public static final Symbol JMS_SELECTOR_NAME = Symbol.valueOf("apache.org:selector-filter:string");
   public static final Object[] JMS_SELECTOR_FILTER_IDS = new Object[]{JMS_SELECTOR_CODE, JMS_SELECTOR_NAME};
   public static final UnsignedLong NO_LOCAL_CODE = UnsignedLong.valueOf(0x0000468C00000003L);
   public static final Symbol NO_LOCAL_NAME = Symbol.valueOf("apache.org:no-local-filter:list");
   public static final Object[] NO_LOCAL_FILTER_IDS = new Object[]{NO_LOCAL_CODE, NO_LOCAL_NAME};

   // Capabilities used to identify destination type in some requests.
   public static final Symbol TEMP_QUEUE_CAPABILITY = Symbol.valueOf("temporary-queue");
   public static final Symbol TEMP_TOPIC_CAPABILITY = Symbol.valueOf("temporary-topic");

   // Symbols used to announce connection information to remote peer.
   public static final Symbol INVALID_FIELD = Symbol.valueOf("invalid-field");
   public static final Symbol CONTAINER_ID = Symbol.valueOf("container-id");

   // Symbols used to announce connection information to remote peer.
   public static final Symbol ANONYMOUS_RELAY = Symbol.valueOf("ANONYMOUS-RELAY");
   public static final Symbol QUEUE_PREFIX = Symbol.valueOf("queue-prefix");
   public static final Symbol TOPIC_PREFIX = Symbol.valueOf("topic-prefix");
   public static final Symbol CONNECTION_OPEN_FAILED = Symbol.valueOf("amqp:connection-establishment-failed");
   public static final Symbol PRODUCT = Symbol.valueOf("product");
   public static final Symbol VERSION = Symbol.valueOf("version");
   public static final Symbol PLATFORM = Symbol.valueOf("platform");

   // Symbols used in configuration of newly opened links.
   public static final Symbol COPY = Symbol.getSymbol("copy");

   // Lifetime policy symbols
   public static final Symbol LIFETIME_POLICY = Symbol.valueOf("lifetime-policy");

   /**
    * Search for a given Symbol in a given array of Symbol object.
    *
    * @param symbols the set of Symbols to search.
    * @param key     the value to try and find in the Symbol array.
    * @return {@code true} if the key is found in the given Symbol array
    */
   public static boolean contains(Symbol[] symbols, Symbol key) {
      if (symbols == null || symbols.length == 0) {
         return false;
      }

      for (Symbol symbol : symbols) {
         if (symbol.equals(key)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Search for a particular filter using a set of known indentification values
    * in the Map of filters.
    *
    * @param filters   The filters map that should be searched.
    * @param filterIds The aliases for the target filter to be located.
    * @return the filter if found in the mapping or null if not found
    */
   public static Map.Entry<Symbol, DescribedType> findFilter(Map<Symbol, Object> filters, Object[] filterIds) {

      if (filterIds == null || filterIds.length == 0) {
         throw new IllegalArgumentException("Invalid empty Filter Ids array passed: ");
      }

      if (filters == null || filters.isEmpty()) {
         return null;
      }

      for (Map.Entry<Symbol, Object> filter : filters.entrySet()) {
         if (filter.getValue() instanceof DescribedType describedType) {
            Object descriptor = describedType.getDescriptor();

            for (Object filterId : filterIds) {
               if (descriptor.equals(filterId)) {
                  return new AbstractMap.SimpleImmutableEntry<>(filter.getKey(), describedType);
               }
            }
         }
      }

      return null;
   }
}
