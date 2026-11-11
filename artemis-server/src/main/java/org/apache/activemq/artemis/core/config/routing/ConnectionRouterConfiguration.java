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
package org.apache.activemq.artemis.core.config.routing;

import org.apache.activemq.artemis.core.server.routing.KeyType;

import java.io.Serializable;
import java.util.Objects;

public class ConnectionRouterConfiguration implements Serializable {

   private String name = null;
   private KeyType keyType = KeyType.SOURCE_IP;
   private String keyFilter = null;
   private String localTargetFilter = null;
   private CacheConfiguration cacheConfiguration = null;
   private PoolConfiguration poolConfiguration = null;
   private NamedPropertyConfiguration policyConfiguration = null;

   public String getName() {
      return name;
   }

   public ConnectionRouterConfiguration setName(String name) {
      this.name = name;
      return this;
   }

   public KeyType getKeyType() {
      return keyType;
   }

   public ConnectionRouterConfiguration setKeyType(KeyType keyType) {
      this.keyType = keyType;
      return this;
   }

   public String getKeyFilter() {
      return keyFilter;
   }

   public ConnectionRouterConfiguration setKeyFilter(String keyFilter) {
      this.keyFilter = keyFilter;
      return this;
   }

   public String getLocalTargetFilter() {
      return localTargetFilter;
   }

   public ConnectionRouterConfiguration setLocalTargetFilter(String localTargetFilter) {
      this.localTargetFilter = localTargetFilter;
      return this;
   }

   public CacheConfiguration getCacheConfiguration() {
      return cacheConfiguration;
   }

   public ConnectionRouterConfiguration setCacheConfiguration(CacheConfiguration cacheConfiguration) {
      this.cacheConfiguration = cacheConfiguration;
      return this;
   }

   public NamedPropertyConfiguration getPolicyConfiguration() {
      return policyConfiguration;
   }

   public ConnectionRouterConfiguration setPolicyConfiguration(NamedPropertyConfiguration policyConfiguration) {
      this.policyConfiguration = policyConfiguration;
      return this;
   }

   public PoolConfiguration getPoolConfiguration() {
      return poolConfiguration;
   }

   public ConnectionRouterConfiguration setPoolConfiguration(PoolConfiguration poolConfiguration) {
      this.poolConfiguration = poolConfiguration;
      return this;
   }

   @Override
   public int hashCode() {
      return Objects.hash(cacheConfiguration, keyFilter, keyType, localTargetFilter, name, policyConfiguration, poolConfiguration);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }

      if (obj instanceof ConnectionRouterConfiguration other) {
         return Objects.equals(cacheConfiguration, other.cacheConfiguration) &&
                Objects.equals(keyFilter, other.keyFilter) &&
                keyType == other.keyType &&
                Objects.equals(localTargetFilter, other.localTargetFilter) &&
                Objects.equals(name, other.name) &&
                Objects.equals(policyConfiguration, other.policyConfiguration) &&
                Objects.equals(poolConfiguration, other.poolConfiguration);
      }

      return false;
   }
}
