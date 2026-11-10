/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.config;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.core.config.federation.FederationAddressPolicyConfiguration;
import org.apache.activemq.artemis.core.config.federation.FederationDownstreamConfiguration;
import org.apache.activemq.artemis.core.config.federation.FederationPolicy;
import org.apache.activemq.artemis.core.config.federation.FederationPolicySet;
import org.apache.activemq.artemis.core.config.federation.FederationQueuePolicyConfiguration;
import org.apache.activemq.artemis.core.config.federation.FederationTransformerConfiguration;
import org.apache.activemq.artemis.core.config.federation.FederationUpstreamConfiguration;

@Deprecated(forRemoval = true)
public class FederationConfiguration implements Serializable {

   private String name;

   private Credentials credentials = new FederationConfiguration.Credentials();

   private List<FederationUpstreamConfiguration> upstreamConfigurations = new ArrayList<>();

   private List<FederationDownstreamConfiguration> downstreamConfigurations = new ArrayList<>();

   private Map<String, FederationPolicy> federationPolicyMap = new HashMap<>();

   private Map<String, FederationTransformerConfiguration> transformerConfigurationMap = new HashMap<>();

   @Deprecated(forRemoval = true)
   public List<FederationUpstreamConfiguration> getUpstreamConfigurations() {
      return upstreamConfigurations;
   }

   @Deprecated(forRemoval = true)
   public FederationConfiguration addUpstreamConfiguration(FederationUpstreamConfiguration federationUpstreamConfiguration) {
      this.upstreamConfigurations.add(federationUpstreamConfiguration);
      return this;
   }

   @Deprecated(forRemoval = true)
   public List<FederationDownstreamConfiguration> getDownstreamConfigurations() {
      return downstreamConfigurations;
   }

   @Deprecated(forRemoval = true)
   public FederationConfiguration addDownstreamConfiguration(FederationDownstreamConfiguration federationDownstreamConfiguration) {
      this.downstreamConfigurations.add(federationDownstreamConfiguration);
      return this;
   }

   @Deprecated(forRemoval = true)
   public FederationConfiguration addFederationPolicy(FederationPolicy federationPolicy) {
      federationPolicyMap.put(federationPolicy.getName(), federationPolicy);
      return this;
   }

   @Deprecated(forRemoval = true)
   public Map<String, FederationPolicy> getQueuePolicies() {
      return federationPolicyMap;
   }

   @Deprecated(forRemoval = true)
   public FederationConfiguration addQueuePolicy(FederationQueuePolicyConfiguration federationPolicy) {
      federationPolicyMap.put(federationPolicy.getName(), federationPolicy);
      return this;
   }

   @Deprecated(forRemoval = true)
   public Map<String, FederationPolicy> getAddressPolicies() {
      return federationPolicyMap;
   }

   @Deprecated(forRemoval = true)
   public FederationConfiguration addAddressPolicy(FederationAddressPolicyConfiguration federationPolicy) {
      federationPolicyMap.put(federationPolicy.getName(), federationPolicy);
      return this;
   }

   @Deprecated(forRemoval = true)
   public Map<String, FederationPolicy> getPolicySets() {
      return federationPolicyMap;
   }

   @Deprecated(forRemoval = true)
   public FederationConfiguration addPolicySet(FederationPolicySet federationPolicy) {
      federationPolicyMap.put(federationPolicy.getName(), federationPolicy);
      return this;
   }

   @Deprecated(forRemoval = true)
   public void clearDownstreamConfigurations() {
      this.downstreamConfigurations.clear();
   }

   @Deprecated(forRemoval = true)
   public void clearUpstreamConfigurations() {
      this.upstreamConfigurations.clear();
   }

   @Deprecated(forRemoval = true)
   public Map<String, FederationPolicy> getFederationPolicyMap() {
      return federationPolicyMap;
   }

   @Deprecated(forRemoval = true)
   public FederationConfiguration addTransformerConfiguration(FederationTransformerConfiguration transformerConfiguration) {
      transformerConfigurationMap.put(transformerConfiguration.getName(), transformerConfiguration);
      return this;
   }

   @Deprecated(forRemoval = true)
   public Map<String, FederationTransformerConfiguration> getTransformerConfigurationMap() {
      return transformerConfigurationMap;
   }

   @Deprecated(forRemoval = true)
   public Map<String, FederationTransformerConfiguration> getTransformerConfigurations() {
      return transformerConfigurationMap;
   }

   @Deprecated(forRemoval = true)
   public String getName() {
      return name;
   }

   @Deprecated(forRemoval = true)
   public FederationConfiguration setName(String name) {
      this.name = name;
      return this;
   }

   @Deprecated(forRemoval = true)
   public Credentials getCredentials() {
      return credentials;
   }

   @Deprecated(forRemoval = true)
   public FederationConfiguration setCredentials(Credentials credentials) {
      this.credentials = credentials;
      return this;
   }

   @Deprecated(forRemoval = true)
   public static class Credentials implements Serializable {

      private String user;

      private String password;

      public String getUser() {
         return user;
      }

      public Credentials setUser(String user) {
         this.user = user;
         return this;
      }

      public String getPassword() {
         return password;
      }

      public Credentials setPassword(String password) {
         this.password = password;
         return this;
      }

      @Override
      public boolean equals(Object obj) {
         if (this == obj) {
            return true;
         }
         if (!(obj instanceof Credentials other)) {
            return false;
         }

         return Objects.equals(user, other.user) &&
                Objects.equals(password, other.password);
      }

      @Override
      public int hashCode() {
         return Objects.hash(user, password);
      }

      public void encode(ActiveMQBuffer buffer) {
         buffer.writeNullableString(user);
         buffer.writeNullableString(password);
      }

      public void decode(ActiveMQBuffer buffer) {
         user = buffer.readNullableString();
         password = buffer.readNullableString();
      }
   }

   @Deprecated(forRemoval = true)
   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof FederationConfiguration other)) {
         return false;
      }

      return Objects.equals(name, other.name) &&
             Objects.equals(credentials, other.credentials) &&
             Objects.equals(upstreamConfigurations, other.upstreamConfigurations) &&
             Objects.equals(federationPolicyMap, other.federationPolicyMap) &&
             Objects.equals(transformerConfigurationMap, other.transformerConfigurationMap);
   }

   @Deprecated(forRemoval = true)
   @Override
   public int hashCode() {
      return Objects.hash(name, credentials, upstreamConfigurations, federationPolicyMap, transformerConfigurationMap);
   }
}
