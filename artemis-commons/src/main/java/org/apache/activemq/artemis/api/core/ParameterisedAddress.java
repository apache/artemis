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
package org.apache.activemq.artemis.api.core;

import static org.apache.activemq.artemis.utils.uri.URISupport.appendParameters;
import static org.apache.activemq.artemis.utils.uri.URISupport.parseQuery;

import java.util.Collections;
import java.util.Map;

import org.apache.activemq.artemis.utils.uri.URISupport;

public class ParameterisedAddress {

   public static SimpleString toParameterisedAddress(SimpleString address, Map<String, String> parameters) {
      if (parameters != null && !parameters.isEmpty()) {
         return SimpleString.of(toParameterisedAddress(address.toString(), parameters));
      } else {
         return address;
      }
   }

   public static String toParameterisedAddress(String address, Map<String, String> parameters) {
      if (parameters != null && !parameters.isEmpty()) {
         return appendParameters(new StringBuilder(address), parameters).toString();
      } else {
         return address;
      }
   }

   private final SimpleString address;
   private final QueueConfiguration queueConfiguration;

   public SimpleString getAddress() {
      return address;
   }

   @Deprecated
   public QueueAttributes getQueueAttributes() {
      return QueueAttributes.fromQueueConfiguration(queueConfiguration);
   }

   public QueueConfiguration getQueueConfiguration() {
      return queueConfiguration;
   }

   @Deprecated
   public ParameterisedAddress(SimpleString address, QueueAttributes queueAttributes) {
      this.address = address;
      this.queueConfiguration = queueAttributes.toQueueConfiguration();
   }

   public ParameterisedAddress(SimpleString address, QueueConfiguration queueConfiguration) {
      this.address = address;
      this.queueConfiguration = queueConfiguration;
   }

   @Deprecated
   public ParameterisedAddress(String address, QueueAttributes queueAttributes) {
      this(SimpleString.of(address), queueAttributes.toQueueConfiguration());
   }

   public ParameterisedAddress(String address, QueueConfiguration queueConfiguration) {
      this(SimpleString.of(address), queueConfiguration);
   }

   public ParameterisedAddress(SimpleString address) {
      this(address.toString());
   }

   public ParameterisedAddress(String address) {
      int index = address.indexOf('?');
      if (index == -1) {
         this.address = SimpleString.of(address);
         this.queueConfiguration = null;
      } else {
         this.address = SimpleString.of(address.substring(0, index));
         QueueConfiguration queueConfiguration = QueueConfiguration.of(address);
         parseQuery(address).forEach(queueConfiguration::set);
         this.queueConfiguration = queueConfiguration;
      }
   }

   public boolean isParameterised() {
      return this.queueConfiguration != null;
   }

   public static boolean isParameterised(String address) {
      return URISupport.containsQuery(address);
   }

   public static boolean isParameterised(SimpleString address) {
      return URISupport.containsQuery(address);
   }

   public static SimpleString extractAddress(SimpleString address) {
      return SimpleString.of(extractAddress(address.toString()));
   }

   /**
    * Given an address string, extract only the query portion if the address is parameterized, otherwise return an empty
    * {@link Map}.
    *
    * @param address The address to operate on.
    * @return a {@link Map} containing the parameters associated with the given address
    */
   @SuppressWarnings("unchecked")
   public static Map<String, String> extractParameters(String address) {
      final int index = address != null ? address.indexOf('?') : -1;

      if (index == -1) {
         return Collections.emptyMap();
      } else {
         return parseQuery(address);
      }
   }

   /**
    * Given an address string, extract only the address portion if the address is parameterized, otherwise just return
    * the provided address.
    *
    * @param address The address to operate on.
    * @return the original address minus any appended parameters
    */
   public static String extractAddress(String address) {
      final int index = address != null ? address.indexOf('?') : -1;

      if (index == -1) {
         return address;
      } else {
         return address.substring(0, index);
      }
   }
}
