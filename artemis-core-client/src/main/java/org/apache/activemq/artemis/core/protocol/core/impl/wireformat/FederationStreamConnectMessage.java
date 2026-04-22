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
package org.apache.activemq.artemis.core.protocol.core.impl.wireformat;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.core.config.FederationConfiguration.Credentials;
import org.apache.activemq.artemis.core.config.federation.FederationPolicy;
import org.apache.activemq.artemis.core.config.federation.FederationStreamConfiguration;
import org.apache.activemq.artemis.core.config.federation.FederationTransformerConfiguration;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.utils.ClassloadingUtil;

public abstract class FederationStreamConnectMessage<T extends FederationStreamConfiguration> extends PacketImpl {

   private String name;
   private Credentials credentials;
   private Map<String, FederationPolicy> federationPolicyMap = new HashMap<>();
   private Map<String, FederationTransformerConfiguration> transformerConfigurationMap = new HashMap<>();
   private T streamConfiguration;

   public FederationStreamConnectMessage(final byte type) {
      super(type);
   }

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public Credentials getCredentials() {
      return credentials;
   }

   public void setCredentials(
      Credentials credentials) {
      this.credentials = credentials;
   }

   public T getStreamConfiguration() {
      return streamConfiguration;
   }

   public void setStreamConfiguration(T streamConfiguration) {
      this.streamConfiguration = streamConfiguration;
   }

   public Map<String, FederationPolicy> getFederationPolicyMap() {
      return federationPolicyMap;
   }

   public void setFederationPolicyMap(
      Map<String, FederationPolicy> federationPolicyMap) {
      this.federationPolicyMap = federationPolicyMap;
   }

   public Map<String, FederationTransformerConfiguration> getTransformerConfigurationMap() {
      return transformerConfigurationMap;
   }

   public void setTransformerConfigurationMap(
      Map<String, FederationTransformerConfiguration> transformerConfigurationMap) {
      this.transformerConfigurationMap = transformerConfigurationMap;
   }

   @Override
   public void encodeRest(ActiveMQBuffer buffer) {
      Objects.requireNonNull(streamConfiguration);

      super.encodeRest(buffer);
      buffer.writeString(name);

      if (credentials != null) {
         buffer.writeBoolean(true);
         credentials.encode(buffer);
      } else {
         buffer.writeBoolean(false);
      }

      buffer.writeInt(federationPolicyMap == null ? 0 : federationPolicyMap.size());
      if (federationPolicyMap != null) {
         for (FederationPolicy policy : federationPolicyMap.values()) {
            buffer.writeString(policy.getClass().getName());
            policy.encode(buffer);
         }
      }

      buffer.writeInt(transformerConfigurationMap == null ? 0 : transformerConfigurationMap.size());
      if (transformerConfigurationMap != null) {
         for (FederationTransformerConfiguration transformerConfiguration : transformerConfigurationMap.values()) {
            transformerConfiguration.encode(buffer);
         }
      }

      streamConfiguration.encode(buffer);
   }

   @Override
   public void decodeRest(ActiveMQBuffer buffer) {
      super.decodeRest(buffer);

      this.name = buffer.readString();
      boolean hasCredentials = buffer.readBoolean();
      if (hasCredentials) {
         credentials = new Credentials();
         credentials.decode(buffer);
      }

      int policySize = buffer.readInt();
      for (int i = 0; i < policySize; i++) {
         String clazz = buffer.readString();
         FederationPolicy policy = getFederationPolicy(clazz);
         policy.decode(buffer);
         federationPolicyMap.put(policy.getName(), policy);
      }

      int transformerSize = buffer.readInt();
      for (int i = 0; i < transformerSize; i++) {
         FederationTransformerConfiguration transformerConfiguration
            = new FederationTransformerConfiguration();
         transformerConfiguration.decode(buffer);
         transformerConfigurationMap.put(transformerConfiguration.getName(), transformerConfiguration);
      }

      streamConfiguration = decodeStreamConfiguration(buffer);
   }

   protected abstract T decodeStreamConfiguration(ActiveMQBuffer buffer);

   private FederationPolicy getFederationPolicy(String clazz) {
      try {
         return (FederationPolicy) ClassloadingUtil.getInstanceWithTypeCheck(clazz, FederationPolicy.class, this.getClass().getClassLoader());
      } catch (Exception e) {
         throw new IllegalStateException("Error. Unable to instantiate FederationPolicy: " + e.getMessage(), e);
      }
   }
}
