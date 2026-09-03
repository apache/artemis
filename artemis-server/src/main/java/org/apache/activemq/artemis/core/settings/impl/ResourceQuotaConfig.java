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
package org.apache.activemq.artemis.core.settings.impl;

import java.io.Serializable;
import java.util.Objects;

/**
 * Configuration for resource quota limits.
 * <p>
 * This class defines the LIMITS for a quota (configuration) without any runtime state.
 * It is serializable and stored in broker.xml. At runtime, ResourceQuota instances
 * are created from these configs and track actual usage.
 * <p>
 * Three types of limits can be configured:
 * <ul>
 *   <li>max-message-bytes: Total bytes for messages across all addresses in this quota</li>
 *   <li>max-addresses: Maximum number of addresses in this quota</li>
 *   <li>max-queues: Maximum number of queues in this quota</li>
 * </ul>
 * <p>
 * Quotas can be organized in a parent-child hierarchy via the 'partOf' field.
 * Child quota usage counts toward parent quota limits.
 * <p>
 * Quotas support wildcard templates (e.g., "EU.*") that create runtime instances
 * on-demand when addresses match patterns.
 *
 * @see ResourceQuota for the runtime tracking implementation
 */
public class ResourceQuotaConfig implements Serializable {

   private static final long serialVersionUID = 1L;

   public static final long DEFAULT_MAX_MESSAGE_BYTES = -1;
   public static final int DEFAULT_MAX_ADDRESSES = -1;
   public static final int DEFAULT_MAX_QUEUES = -1;

   private String name;
   private String partOf;
   private Long maxMessageBytes;
   private Integer maxAddresses;
   private Integer maxQueues;

   public ResourceQuotaConfig() {
      this.name = null;
      this.partOf = null;
      this.maxMessageBytes = null;
      this.maxAddresses = null;
      this.maxQueues = null;
   }

   public ResourceQuotaConfig(String name) {
      if (name == null || name.trim().isEmpty()) {
         throw new IllegalArgumentException("Quota name cannot be null or empty");
      }
      this.name = name;
      this.partOf = null;
      this.maxMessageBytes = null;
      this.maxAddresses = null;
      this.maxQueues = null;
   }

   public String getName() {
      return name;
   }

   public ResourceQuotaConfig setName(String name) {
      this.name = name;
      return this;
   }

   public String getPartOf() {
      return partOf;
   }

   public ResourceQuotaConfig setPartOf(String partOf) {
      this.partOf = partOf;
      return this;
   }

   public long getMaxMessageBytes() {
      return maxMessageBytes != null ? maxMessageBytes : DEFAULT_MAX_MESSAGE_BYTES;
   }

   public ResourceQuotaConfig setMaxMessageBytes(long maxMessageBytes) {
      this.maxMessageBytes = maxMessageBytes;
      return this;
   }

   public int getMaxAddresses() {
      return maxAddresses != null ? maxAddresses : DEFAULT_MAX_ADDRESSES;
   }

   public ResourceQuotaConfig setMaxAddresses(int maxAddresses) {
      this.maxAddresses = maxAddresses;
      return this;
   }

   public int getMaxQueues() {
      return maxQueues != null ? maxQueues : DEFAULT_MAX_QUEUES;
   }

   public ResourceQuotaConfig setMaxQueues(int maxQueues) {
      this.maxQueues = maxQueues;
      return this;
   }

   /**
    * Check if this config has any limits configured.
    *
    * @return true if at least one limit is configured
    */
   public boolean hasLimits() {
      return (maxMessageBytes != null && maxMessageBytes >= 0) ||
             (maxAddresses != null && maxAddresses >= 0) ||
             (maxQueues != null && maxQueues >= 0);
   }

   /**
    * Create a runtime ResourceQuota instance from this configuration.
    * The runtime instance will have the same limits but fresh counters (starting at zero).
    *
    * @return new ResourceQuota instance with these limits and zero counters
    */
   public ResourceQuota createRuntimeQuota() {
      ResourceQuota runtime = new ResourceQuota(this.name);
      runtime.setMaxMessageBytes(this.maxMessageBytes);
      runtime.setMaxAddresses(this.maxAddresses);
      runtime.setMaxQueues(this.maxQueues);
      runtime.setPartOf(this.partOf);
      return runtime;
   }

   /**
    * Create a copy of this config for wildcard template instantiation.
    * For example, template "EU.*" with address "eu.fr.orders" creates config "EU.fr".
    *
    * @param newName the name for the new config instance
    * @return copy with new name but same limits and partOf
    */
   public ResourceQuotaConfig copy(String newName) {
      ResourceQuotaConfig copy = new ResourceQuotaConfig(newName);
      copy.maxMessageBytes = this.maxMessageBytes;
      copy.maxAddresses = this.maxAddresses;
      copy.maxQueues = this.maxQueues;
      copy.partOf = this.partOf;
      return copy;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      }
      if (!(o instanceof ResourceQuotaConfig)) {
         return false;
      }
      ResourceQuotaConfig that = (ResourceQuotaConfig) o;
      return Objects.equals(name, that.name) &&
             Objects.equals(partOf, that.partOf) &&
             Objects.equals(maxMessageBytes, that.maxMessageBytes) &&
             Objects.equals(maxAddresses, that.maxAddresses) &&
             Objects.equals(maxQueues, that.maxQueues);
   }

   @Override
   public int hashCode() {
      return Objects.hash(name, partOf, maxMessageBytes, maxAddresses, maxQueues);
   }

   @Override
   public String toString() {
      return "ResourceQuotaConfig{" +
             "name='" + name + '\'' +
             ", partOf='" + partOf + '\'' +
             ", maxMessageBytes=" + maxMessageBytes +
             ", maxAddresses=" + maxAddresses +
             ", maxQueues=" + maxQueues +
             '}';
   }
}
