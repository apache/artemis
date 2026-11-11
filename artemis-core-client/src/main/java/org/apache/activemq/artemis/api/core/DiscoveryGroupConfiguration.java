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
package org.apache.activemq.artemis.api.core;

import java.io.Serializable;
import java.util.Objects;

import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.utils.UUIDGenerator;

/**
 * This file represents how we are using Discovery.
 * <p>
 * The discovery configuration could either use plain UDP or JGroups.
 * <p>
 * If using UDP, all the UDP properties will be filled and the jgroups properties will be {@code null}.
 * <p>
 * If using JGroups, all the UDP properties will be -1 or {@code null} and the jgroups properties will be filled.
 * <p>
 * If by any reason, both properties are filled, the JGroups takes precedence. That means, if
 * {@code jgroupsFile != null} then the Grouping method used will be JGroups.
 */
public final class DiscoveryGroupConfiguration implements Serializable {

   private static final long serialVersionUID = 8657206421727863400L;

   private String name = UUIDGenerator.getInstance().generateStringUUID();

   private long refreshTimeout = ActiveMQClient.DEFAULT_DISCOVERY_REFRESH_TIMEOUT;

   private long discoveryInitialWaitTimeout = ActiveMQClient.DEFAULT_DISCOVERY_INITIAL_WAIT_TIMEOUT;

   private long stoppingTimeout = ActiveMQClient.DEFAULT_DISCOVERY_STOPPING_TIMEOUT;

   // This is the actual object used by the class, it has to be transient so we can handle deserialization with a 2.2 client
   private BroadcastEndpointFactory endpointFactory;

   public DiscoveryGroupConfiguration() {
   }

   public String getName() {
      return name;
   }

   public long getRefreshTimeout() {
      return refreshTimeout;
   }

   public DiscoveryGroupConfiguration setName(final String name) {
      this.name = name;
      return this;
   }

   public DiscoveryGroupConfiguration setRefreshTimeout(final long refreshTimeout) {
      this.refreshTimeout = refreshTimeout;
      return this;
   }

   public long getDiscoveryInitialWaitTimeout() {
      return discoveryInitialWaitTimeout;
   }

   public DiscoveryGroupConfiguration setDiscoveryInitialWaitTimeout(long discoveryInitialWaitTimeout) {
      this.discoveryInitialWaitTimeout = discoveryInitialWaitTimeout;
      return this;
   }

   public long getStoppingTimeout() {
      return stoppingTimeout;
   }

   public void setStoppingTimeout(long stoppingTimeout) {
      this.stoppingTimeout = stoppingTimeout;
   }

   public BroadcastEndpointFactory getBroadcastEndpointFactory() {
      return endpointFactory;
   }

   public DiscoveryGroupConfiguration setBroadcastEndpointFactory(BroadcastEndpointFactory endpointFactory) {
      this.endpointFactory = endpointFactory;
      return this;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof DiscoveryGroupConfiguration other)) {
         return false;
      }

      return discoveryInitialWaitTimeout == other.discoveryInitialWaitTimeout &&
             refreshTimeout == other.refreshTimeout &&
             Objects.equals(name, other.name);
   }

   @Override
   public int hashCode() {
      return Objects.hash(name, refreshTimeout, discoveryInitialWaitTimeout);
   }

   @Override
   public String toString() {
      return "DiscoveryGroupConfiguration{" +
         "name='" + name + '\'' +
         ", refreshTimeout=" + refreshTimeout +
         ", discoveryInitialWaitTimeout=" + discoveryInitialWaitTimeout +
         '}';
   }
}