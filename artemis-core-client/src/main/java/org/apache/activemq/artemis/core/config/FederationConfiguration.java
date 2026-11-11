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

public class FederationConfiguration implements Serializable {

   private String name;

   private Credentials credentials = new FederationConfiguration.Credentials();

   private List<FederationUpstreamConfiguration> upstreamConfigurations = new ArrayList<>();

   private List<FederationDownstreamConfiguration> downstreamConfigurations = new ArrayList<>();

   private Map<String, FederationPolicy> federationPolicyMap = new HashMap<>();

   private Map<String, FederationTransformerConfiguration> transformerConfigurationMap = new HashMap<>();

   public List<FederationUpstreamConfiguration> getUpstreamConfigurations() {
      return upstreamConfigurations;
   }

   public FederationConfiguration addUpstreamConfiguration(FederationUpstreamConfiguration federationUpstreamConfiguration) {
      this.upstreamConfigurations.add(federationUpstreamConfiguration);
      return this;
   }

   public List<FederationDownstreamConfiguration> getDownstreamConfigurations() {
      return downstreamConfigurations;
   }

   public FederationConfiguration addDownstreamConfiguration(FederationDownstreamConfiguration federationDownstreamConfiguration) {
      this.downstreamConfigurations.add(federationDownstreamConfiguration);
      return this;
   }

   public FederationConfiguration addFederationPolicy(FederationPolicy federationPolicy) {
      federationPolicyMap.put(federationPolicy.getName(), federationPolicy);
      return this;
   }

   public Map<String, FederationPolicy> getQueuePolicies() {
      return federationPolicyMap;
   }

   public FederationConfiguration addQueuePolicy(FederationQueuePolicyConfiguration federationPolicy) {
      federationPolicyMap.put(federationPolicy.getName(), federationPolicy);
      return this;
   }

   public Map<String, FederationPolicy> getAddressPolicies() {
      return federationPolicyMap;
   }

   public FederationConfiguration addAddressPolicy(FederationAddressPolicyConfiguration federationPolicy) {
      federationPolicyMap.put(federationPolicy.getName(), federationPolicy);
      return this;
   }

   public Map<String, FederationPolicy> getPolicySets() {
      return federationPolicyMap;
   }

   public FederationConfiguration addPolicySet(FederationPolicySet federationPolicy) {
      federationPolicyMap.put(federationPolicy.getName(), federationPolicy);
      return this;
   }

   public void clearDownstreamConfigurations() {
      this.downstreamConfigurations.clear();
   }

   public void clearUpstreamConfigurations() {
      this.upstreamConfigurations.clear();
   }

   public Map<String, FederationPolicy> getFederationPolicyMap() {
      return federationPolicyMap;
   }

   public FederationConfiguration addTransformerConfiguration(FederationTransformerConfiguration transformerConfiguration) {
      transformerConfigurationMap.put(transformerConfiguration.getName(), transformerConfiguration);
      return this;
   }

   public Map<String, FederationTransformerConfiguration> getTransformerConfigurationMap() {
      return transformerConfigurationMap;
   }

   public Map<String, FederationTransformerConfiguration> getTransformerConfigurations() {
      return transformerConfigurationMap;
   }

   public String getName() {
      return name;
   }

   public FederationConfiguration setName(String name) {
      this.name = name;
      return this;
   }

   public Credentials getCredentials() {
      return credentials;
   }

   public FederationConfiguration setCredentials(Credentials credentials) {
      this.credentials = credentials;
      return this;
   }

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

   @Override
   public int hashCode() {
      return Objects.hash(name, credentials, upstreamConfigurations, federationPolicyMap, transformerConfigurationMap);
   }
}
