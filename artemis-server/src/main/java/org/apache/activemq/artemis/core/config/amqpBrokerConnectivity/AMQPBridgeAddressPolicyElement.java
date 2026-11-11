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
package org.apache.activemq.artemis.core.config.amqpBrokerConnectivity;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.activemq.artemis.core.config.TransformerConfiguration;

public final class AMQPBridgeAddressPolicyElement implements Serializable {

   private static final long serialVersionUID = 6515270736587053708L;

   private final Set<AddressMatch> includes = new HashSet<>();
   private final Set<AddressMatch> excludes = new HashSet<>();
   private final Map<String, Object> properties = new HashMap<>();

   private String name;
   private String remoteAddress;
   private String remoteAddressPrefix;
   private String remoteAddressSuffix;
   private String[] remoteTerminusCapabilities;
   private boolean includeDivertBindings;
   private boolean useDurableSubscriptions;
   private Integer priority;
   private String filter;
   private TransformerConfiguration transformerConfig;

   public String getName() {
      return name;
   }

   public AMQPBridgeAddressPolicyElement setName(String name) {
      this.name = name;
      return this;
   }

   public Set<AddressMatch> getIncludes() {
      return includes;
   }

   public AMQPBridgeAddressPolicyElement addToIncludes(String include) {
      includes.add(new AddressMatch().setAddressMatch(include));
      return this;
   }

   public AMQPBridgeAddressPolicyElement addInclude(AddressMatch include) {
      includes.add(include);
      return this;
   }

   public AMQPBridgeAddressPolicyElement setIncludes(Set<AddressMatch> includes) {
      this.includes.clear();
      if (includes != null) {
         this.includes.addAll(includes);
      }

      return this;
   }

   public Set<AddressMatch> getExcludes() {
      return excludes;
   }

   public AMQPBridgeAddressPolicyElement addToExcludes(String exclude) {
      excludes.add(new AddressMatch().setAddressMatch(exclude));
      return this;
   }

   public AMQPBridgeAddressPolicyElement addExclude(AddressMatch exclude) {
      excludes.add(exclude);
      return this;
   }

   public AMQPBridgeAddressPolicyElement setExcludes(Set<AddressMatch> excludes) {
      this.excludes.clear();
      if (excludes != null) {
         this.excludes.addAll(excludes);
      }

      return this;
   }

   public Map<String, Object> getProperties() {
      return properties;
   }

   public AMQPBridgeAddressPolicyElement addProperty(String key, String value) {
      properties.put(key, value);
      return this;
   }

   public AMQPBridgeAddressPolicyElement addProperty(String key, Number value) {
      properties.put(key, value);
      return this;
   }

   public AMQPBridgeAddressPolicyElement setProperties(Map<String, Object> properties) {
      this.properties.clear();
      if (properties != null) {
         this.properties.putAll(properties);
      }

      return this;
   }

   public boolean isIncludeDivertBindings() {
      return includeDivertBindings;
   }

   public AMQPBridgeAddressPolicyElement setIncludeDivertBindings(boolean includeDivertBindings) {
      this.includeDivertBindings = includeDivertBindings;
      return this;
   }

   public boolean isUseDurableSubscriptions() {
      return useDurableSubscriptions;
   }

   public AMQPBridgeAddressPolicyElement setUseDurableSubscriptions(boolean useDurableSubscriptions) {
      this.useDurableSubscriptions = useDurableSubscriptions;
      return this;
   }

   public AMQPBridgeAddressPolicyElement setTransformerConfiguration(TransformerConfiguration transformerConfig) {
      this.transformerConfig = transformerConfig;
      return this;
   }

   public TransformerConfiguration getTransformerConfiguration() {
      return transformerConfig;
   }

   public String getRemoteAddress() {
      return remoteAddress;
   }

   public AMQPBridgeAddressPolicyElement setRemoteAddress(String remoteAddress) {
      this.remoteAddress = remoteAddress;
      return this;
   }

   public String getRemoteAddressPrefix() {
      return remoteAddressPrefix;
   }

   public AMQPBridgeAddressPolicyElement setRemoteAddressPrefix(String remoteAddressPrefix) {
      this.remoteAddressPrefix = remoteAddressPrefix;
      return this;
   }

   public String getRemoteAddressSuffix() {
      return remoteAddressSuffix;
   }

   public AMQPBridgeAddressPolicyElement setRemoteAddressSuffix(String remoteAddressSuffix) {
      this.remoteAddressSuffix = remoteAddressSuffix;
      return this;
   }

   public String[] getRemoteTerminusCapabilities() {
      if (remoteTerminusCapabilities != null) {
         return Arrays.copyOf(remoteTerminusCapabilities, remoteTerminusCapabilities.length);
      } else {
         return null;
      }
   }

   public AMQPBridgeAddressPolicyElement setRemoteTerminusCapabilities(String[] remoteTerminusCapabilities) {
      this.remoteTerminusCapabilities = remoteTerminusCapabilities;
      return this;
   }

   public Integer getPriority() {
      return priority;
   }

   public AMQPBridgeAddressPolicyElement setPriority(Integer priority) {
      this.priority = priority;
      return this;
   }

   public String getFilter() {
      return filter;
   }

   public AMQPBridgeAddressPolicyElement setFilter(String filter) {
      this.filter = filter;
      return this;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof AMQPBridgeAddressPolicyElement other)) {
         return false;
      }

      return Objects.equals(name, other.name) &&
             Objects.equals(includes, other.includes) &&
             Objects.equals(excludes, other.excludes) &&
             Objects.equals(properties, other.properties) &&
             Objects.equals(priority, other.priority) &&
             Objects.equals(includeDivertBindings, other.includeDivertBindings) &&
             Objects.equals(useDurableSubscriptions, other.useDurableSubscriptions) &&
             Objects.equals(filter, other.filter) &&
             Objects.equals(transformerConfig, other.transformerConfig) &&
             Objects.equals(remoteAddress, other.remoteAddress) &&
             Objects.equals(remoteAddressPrefix, other.remoteAddressPrefix) &&
             Objects.equals(remoteAddressSuffix, other.remoteAddressSuffix) &&
             Arrays.equals(remoteTerminusCapabilities, other.remoteTerminusCapabilities);
   }

   @Override
   public int hashCode() {
      return Objects.hash(name, includes, excludes, properties, filter, priority, remoteAddress, remoteAddressPrefix,
                          remoteAddressSuffix, includeDivertBindings, useDurableSubscriptions, transformerConfig,
                          Arrays.hashCode(remoteTerminusCapabilities));
   }

   // We are required to implement a named match type so that we can perform this configuration
   // from the broker properties mechanism where there is no means of customizing the property
   // set to parse address and queue names from some string encoded value. This could be simplified
   // at some point if another configuration mechanism is created. The name value is not used
   // internally in the AMQP bridge implementation.

   public static class AddressMatch implements Serializable {

      private static final long serialVersionUID = 8517154638045698017L;

      private String name;
      private String addressMatch;

      public String getName() {
         return name;
      }

      public AddressMatch setName(String name) {
         this.name = name;
         return this;
      }

      public String getAddressMatch() {
         return addressMatch;
      }

      public AddressMatch setAddressMatch(String addressMatch) {
         this.addressMatch = addressMatch;
         return this;
      }

      @Override
      public boolean equals(Object obj) {
         if (this == obj) {
            return true;
         }
         if (!(obj instanceof AddressMatch other)) {
            return false;
         }

         return Objects.equals(addressMatch, other.addressMatch);
      }

      @Override
      public int hashCode() {
         return Objects.hashCode(addressMatch);
      }
   }
}
