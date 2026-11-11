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

public final class AMQPBridgeQueuePolicyElement implements Serializable {

   private static final long serialVersionUID = 226560733949177716L;

   private final Set<QueueMatch> includes = new HashSet<>();
   private final Set<QueueMatch> excludes = new HashSet<>();
   private final Map<String, Object> properties = new HashMap<>();

   private String name;
   private String remoteAddress;
   private String remoteAddressPrefix;
   private String remoteAddressSuffix;
   private String[] remoteTerminusCapabilities;
   private Integer priority;
   private Integer priorityAdjustment;
   private String filter;
   private TransformerConfiguration transformerConfig;

   public String getName() {
      return name;
   }

   public AMQPBridgeQueuePolicyElement setName(String name) {
      this.name = name;
      return this;
   }

   public Set<QueueMatch> getIncludes() {
      return includes;
   }

   public AMQPBridgeQueuePolicyElement addToIncludes(String addressMatch, String queueMatch) {
      includes.add(new QueueMatch().setAddressMatch(addressMatch).setQueueMatch(queueMatch));
      return this;
   }

   public AMQPBridgeQueuePolicyElement addInclude(QueueMatch match) {
      includes.add(match);
      return this;
   }

   public AMQPBridgeQueuePolicyElement setIncludes(Set<QueueMatch> includes) {
      this.includes.clear();
      if (includes != null) {
         this.includes.addAll(includes);
      }

      return this;
   }

   public Set<QueueMatch> getExcludes() {
      return excludes;
   }

   public AMQPBridgeQueuePolicyElement addExclude(QueueMatch match) {
      excludes.add(match);
      return this;
   }

   public AMQPBridgeQueuePolicyElement addToExcludes(String addressMatch, String queueMatch) {
      excludes.add(new QueueMatch().setAddressMatch(addressMatch).setQueueMatch(queueMatch));
      return this;
   }

   public AMQPBridgeQueuePolicyElement setExcludes(Set<QueueMatch> excludes) {
      this.excludes.clear();
      if (excludes != null) {
         this.excludes.addAll(excludes);
      }

      return this;
   }

   public AMQPBridgeQueuePolicyElement addProperty(String key, String value) {
      properties.put(key, value);
      return this;
   }

   public AMQPBridgeQueuePolicyElement addProperty(String key, Number value) {
      properties.put(key, value);
      return this;
   }

   public Map<String, Object> getProperties() {
      return properties;
   }

   public AMQPBridgeQueuePolicyElement setProperties(Map<String, Object> properties) {
      this.properties.clear();
      if (properties != null) {
         this.properties.putAll(properties);
      }

      return this;
   }

   public AMQPBridgeQueuePolicyElement setTransformerConfiguration(TransformerConfiguration transformerConfig) {
      this.transformerConfig = transformerConfig;
      return this;
   }

   public TransformerConfiguration getTransformerConfiguration() {
      return transformerConfig;
   }

   public String getRemoteAddress() {
      return remoteAddress;
   }

   public AMQPBridgeQueuePolicyElement setRemoteAddress(String remoteAddress) {
      this.remoteAddress = remoteAddress;
      return this;
   }

   public String getRemoteAddressPrefix() {
      return remoteAddressPrefix;
   }

   public AMQPBridgeQueuePolicyElement setRemoteAddressPrefix(String remoteAddressPrefix) {
      this.remoteAddressPrefix = remoteAddressPrefix;
      return this;
   }

   public String getRemoteAddressSuffix() {
      return remoteAddressSuffix;
   }

   public AMQPBridgeQueuePolicyElement setRemoteAddressSuffix(String remoteAddressSuffix) {
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

   public AMQPBridgeQueuePolicyElement setRemoteTerminusCapabilities(String[] remoteTerminusCapabilities) {
      this.remoteTerminusCapabilities = remoteTerminusCapabilities;
      return this;
   }

   public Integer getPriority() {
      return priority;
   }

   public AMQPBridgeQueuePolicyElement setPriority(Integer priority) {
      this.priority = priority;
      return this;
   }

   public Integer getPriorityAdjustment() {
      return priorityAdjustment;
   }

   public AMQPBridgeQueuePolicyElement setPriorityAdjustment(Integer priorityAdjustment) {
      this.priorityAdjustment = priorityAdjustment;
      return this;
   }

   public String getFilter() {
      return filter;
   }

   public AMQPBridgeQueuePolicyElement setFilter(String filter) {
      this.filter = filter;
      return this;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof AMQPBridgeQueuePolicyElement other)) {
         return false;
      }

      return Objects.equals(name, other.name) &&
             Objects.equals(includes, other.includes) &&
             Objects.equals(excludes, other.excludes) &&
             Objects.equals(properties, other.properties) &&
             Objects.equals(transformerConfig, other.transformerConfig) &&
             Objects.equals(priority, other.priority) &&
             Objects.equals(priorityAdjustment, other.priorityAdjustment) &&
             Objects.equals(filter, other.filter) &&
             Objects.equals(remoteAddress, other.remoteAddress) &&
             Objects.equals(remoteAddressPrefix, other.remoteAddressPrefix) &&
             Objects.equals(remoteAddressSuffix, other.remoteAddressSuffix) &&
             Arrays.equals(remoteTerminusCapabilities, other.remoteTerminusCapabilities);
   }

   @Override
   public int hashCode() {
      return Objects.hash(name, includes, excludes, properties, priority, priorityAdjustment, filter, remoteAddress,
                          remoteAddressPrefix, remoteAddressSuffix, Arrays.hashCode(remoteTerminusCapabilities),
                          transformerConfig);
   }

   // We are required to implement a named match type so that we can perform this configuration
   // from the broker properties mechanism where there is no means of customizing the property
   // set to parse address and queue names from some string encoded value. This could be simplified
   // at some point if another configuration mechanism is created. The name value is not used
   // internally in the AMQP bridge implementation.

   public static class QueueMatch implements Serializable {

      private static final long serialVersionUID = -1641189627591828008L;

      private String name;
      private String addressMatch;
      private String queueMatch;

      public String getName() {
         return name;
      }

      public QueueMatch setName(String name) {
         this.name = name;
         return this;
      }

      public String getAddressMatch() {
         return addressMatch;
      }

      public QueueMatch setAddressMatch(String addressMatch) {
         this.addressMatch = addressMatch;
         return this;
      }

      public String getQueueMatch() {
         return queueMatch;
      }

      public QueueMatch setQueueMatch(String queueMatch) {
         this.queueMatch = queueMatch;
         return this;
      }

      @Override
      public boolean equals(Object obj) {
         if (this == obj) {
            return true;
         }
         if (!(obj instanceof QueueMatch other)) {
            return false;
         }

         return Objects.equals(queueMatch, other.queueMatch) &&
                Objects.equals(addressMatch, other.addressMatch);
      }

      @Override
      public int hashCode() {
         return Objects.hash(queueMatch, addressMatch);
      }
   }
}
