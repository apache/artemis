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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.activemq.artemis.core.config.TransformerConfiguration;

public final class AMQPFederationAddressPolicyElement implements Serializable {

   private static final long serialVersionUID = -5205164803216061323L;

   private final Set<AddressMatch> includes = new HashSet<>();
   private final Set<AddressMatch> excludes = new HashSet<>();
   private final Map<String, Object> properties = new HashMap<>();

   private String name;
   private Boolean autoDelete;
   private Long autoDeleteDelay;
   private Long autoDeleteMessageCount;
   private int maxHops;
   private Boolean enableDivertBindings;
   private Boolean allowWildcardGroupings;
   private TransformerConfiguration transformerConfig;

   public String getName() {
      return name;
   }

   public AMQPFederationAddressPolicyElement setName(String name) {
      this.name = name;
      return this;
   }

   public Set<AddressMatch> getIncludes() {
      return includes;
   }

   public AMQPFederationAddressPolicyElement addToIncludes(String include) {
      includes.add(new AddressMatch().setAddressMatch(include));
      return this;
   }

   public AMQPFederationAddressPolicyElement addInclude(AddressMatch include) {
      includes.add(include);
      return this;
   }

   public AMQPFederationAddressPolicyElement setIncludes(Set<AddressMatch> includes) {
      this.includes.clear();
      if (includes != null) {
         this.includes.addAll(includes);
      }

      return this;
   }

   public Set<AddressMatch> getExcludes() {
      return excludes;
   }

   public AMQPFederationAddressPolicyElement addToExcludes(String exclude) {
      excludes.add(new AddressMatch().setAddressMatch(exclude));
      return this;
   }

   public AMQPFederationAddressPolicyElement addExclude(AddressMatch exclude) {
      excludes.add(exclude);
      return this;
   }

   public AMQPFederationAddressPolicyElement setExcludes(Set<AddressMatch> excludes) {
      this.excludes.clear();
      if (excludes != null) {
         this.excludes.addAll(excludes);
      }

      return this;
   }

   public Map<String, Object> getProperties() {
      return properties;
   }

   public AMQPFederationAddressPolicyElement addProperty(String key, String value) {
      properties.put(key, value);
      return this;
   }

   public AMQPFederationAddressPolicyElement addProperty(String key, Number value) {
      properties.put(key, value);
      return this;
   }

   public AMQPFederationAddressPolicyElement setProperties(Map<String, Object> properties) {
      this.properties.clear();
      if (properties != null) {
         this.properties.putAll(properties);
      }

      return this;
   }

   public int getMaxHops() {
      return maxHops;
   }

   public AMQPFederationAddressPolicyElement setMaxHops(int maxHops) {
      this.maxHops = maxHops;
      return this;
   }

   public Long getAutoDeleteMessageCount() {
      return autoDeleteMessageCount;
   }

   public AMQPFederationAddressPolicyElement setAutoDeleteMessageCount(Long autoDeleteMessageCount) {
      this.autoDeleteMessageCount = autoDeleteMessageCount;
      return this;
   }

   public Long getAutoDeleteDelay() {
      return autoDeleteDelay;
   }

   public AMQPFederationAddressPolicyElement setAutoDeleteDelay(Long autoDeleteDelay) {
      this.autoDeleteDelay = autoDeleteDelay;
      return this;
   }

   public Boolean getAutoDelete() {
      return autoDelete;
   }

   public AMQPFederationAddressPolicyElement setAutoDelete(Boolean autoDelete) {
      this.autoDelete = autoDelete;
      return this;
   }

   public Boolean isEnableDivertBindings() {
      return enableDivertBindings;
   }

   public AMQPFederationAddressPolicyElement setEnableDivertBindings(Boolean enableDivertBindings) {
      this.enableDivertBindings = enableDivertBindings;
      return this;
   }

   public AMQPFederationAddressPolicyElement setTransformerConfiguration(TransformerConfiguration transformerConfig) {
      this.transformerConfig = transformerConfig;
      return this;
   }

   public TransformerConfiguration getTransformerConfiguration() {
      return transformerConfig;
   }

   public Boolean isAllowWildcardGroupings() {
      return allowWildcardGroupings;
   }

   public AMQPFederationAddressPolicyElement setAllowWildcardGroupings(Boolean allowWildcardGroupings) {
      this.allowWildcardGroupings = allowWildcardGroupings;
      return this;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof AMQPFederationAddressPolicyElement other)) {
         return false;
      }

      return Objects.equals(name, other.name) &&
             Objects.equals(includes, other.includes) &&
             Objects.equals(excludes, other.excludes) &&
             Objects.equals(properties, other.properties) &&
             Objects.equals(autoDelete, other.autoDelete) &&
             Objects.equals(autoDeleteDelay, other.autoDeleteDelay) &&
             Objects.equals(autoDeleteMessageCount, other.autoDeleteMessageCount) &&
             Objects.equals(enableDivertBindings, other.enableDivertBindings) &&
             Objects.equals(allowWildcardGroupings, other.allowWildcardGroupings) &&
             Objects.equals(transformerConfig, other.transformerConfig) &&
             maxHops == other.maxHops;
   }

   @Override
   public int hashCode() {
      return Objects.hash(name, includes, excludes, properties, autoDelete, autoDeleteDelay, autoDeleteMessageCount,
                          maxHops, enableDivertBindings, allowWildcardGroupings, transformerConfig);
   }

   // We are required to implement a named match type so that we can perform this configuration
   // from the broker properties mechanism where there is no means of customizing the property
   // set to parse address and queue names from some string encoded value. This could be simplified
   // at some point if another configuration mechanism is created. The name value is not used
   // internally in the AMQP federation implementation.

   public static class AddressMatch implements Serializable {

      private static final long serialVersionUID = 8517154638045698017L;

      private String name;
      private String addressMatch;

      public String getName() {
         if (name == null) {
            return addressMatch;
         }
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
