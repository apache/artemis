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
package org.apache.activemq.artemis.protocol.amqp.connect.bridge;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.activemq.artemis.core.config.TransformerConfiguration;
import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.apache.qpid.proton.amqp.Symbol;

/**
 * Base class for AMQP bridge sender and receiver policies that configure bridging
 * to or from addresses and queues.
 */
public abstract class AMQPBridgePolicy {

   private final Map<String, Object> properties;
   private final String policyName;
   private final String remoteAddress;
   private final String remoteAddressPrefix;
   private final String remoteAddressSuffix;
   private final Collection<Symbol> remoteTerminusCapabilities;
   private final Integer priority;
   private final String filter;
   private final TransformerConfiguration transformerConfig;

   public AMQPBridgePolicy(String policyName, Integer priority,
                           String filter, String remoteAddress,
                           String remoteAddressPrefix, String remoteAddressSuffix,
                           Collection<Symbol> remoteTerminusCapabilities,
                           Map<String, Object> properties,
                           TransformerConfiguration transformerConfig,
                           WildcardConfiguration wildcardConfig) {
      Objects.requireNonNull(policyName, "The provided policy name cannot be null");
      Objects.requireNonNull(wildcardConfig, "The provided wild card configuration cannot be null");

      this.policyName = policyName;
      this.remoteAddress = remoteAddress;
      this.remoteAddressPrefix = remoteAddressPrefix;
      this.remoteAddressSuffix = remoteAddressSuffix;
      this.remoteTerminusCapabilities = remoteTerminusCapabilities;
      this.priority = priority;
      this.filter = filter;
      this.transformerConfig = transformerConfig;

      if (properties == null || properties.isEmpty()) {
         this.properties = Collections.emptyMap();
      } else {
         this.properties = Collections.unmodifiableMap(new HashMap<>(properties));
      }
   }

   public String getPolicyName() {
      return policyName;
   }

   public Map<String, Object> getProperties() {
      return properties;
   }

   public Integer getPriority() {
      return priority;
   }

   public String getFilter() {
      return filter;
   }

   public String getRemoteAddress() {
      return remoteAddress;
   }

   public String getRemoteAddressPrefix() {
      return remoteAddressPrefix;
   }

   public String getRemoteAddressSuffix() {
      return remoteAddressSuffix;
   }

   public Collection<Symbol> getRemoteTerminusCapabilities() {
      return remoteTerminusCapabilities;
   }

   public TransformerConfiguration getTransformerConfiguration() {
      return transformerConfig;
   }
}
