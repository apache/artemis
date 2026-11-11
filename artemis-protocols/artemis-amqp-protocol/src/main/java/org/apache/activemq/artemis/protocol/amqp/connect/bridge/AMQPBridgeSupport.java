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

import static org.apache.activemq.artemis.core.config.WildcardConfiguration.DEFAULT_WILDCARD_CONFIGURATION;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.DEFAULT_PRIORITY_ADJUSTMENT_VALUE;

import java.lang.invoke.MethodHandles;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBridgeAddressPolicyElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBridgeBrokerConnectionElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBridgeQueuePolicyElement;
import org.apache.activemq.artemis.protocol.amqp.connect.AMQPBrokerConnection;
import org.apache.qpid.proton.amqp.Symbol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Support class for operations involving the AMQP Bridge.
 */
public class AMQPBridgeSupport {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   /**
    * Create a new AMQPBridgeManager from the bridge configuration element.
    *
    * @param connection
    *    The broker connection that will contain the AMQP bridge.
    * @param bridgeElement
    *    Configuration of the AMQP bridge.
    *
    * @return a configured AMQP bridge manager instance for use by the parent broker connection.
    */
   public static AMQPBridgeManager createManager(AMQPBrokerConnection connection, AMQPBridgeBrokerConnectionElement bridgeElement) {
      final WildcardConfiguration wildcardConfiguration;

      if (connection.getServer().getConfiguration().getWildcardConfiguration() != null) {
         wildcardConfiguration = connection.getServer().getConfiguration().getWildcardConfiguration();
      } else {
         wildcardConfiguration = DEFAULT_WILDCARD_CONFIGURATION;
      }

      Set<AMQPBridgeAddressPolicy> fromAddressPolicies = Collections.emptySet();
      Set<AMQPBridgeAddressPolicy> toAddressPolicies = Collections.emptySet();
      Set<AMQPBridgeQueuePolicy> fromQueuePolicies = Collections.emptySet();
      Set<AMQPBridgeQueuePolicy> toQueuePolicies = Collections.emptySet();

      final Set<AMQPBridgeAddressPolicyElement> fromAddress = bridgeElement.getBridgeFromAddressPolicies();
      if (!fromAddress.isEmpty()) {
         fromAddressPolicies = new HashSet<>();

         for (AMQPBridgeAddressPolicyElement element : fromAddress) {
            logger.debug("AMQP Bridge {} adding bridge from address policy: {}", bridgeElement.getName(), element.getName());
            fromAddressPolicies.add(createBridgeAddressPolicy(element, wildcardConfiguration));
         }
      }
      final Set<AMQPBridgeAddressPolicyElement> toAddress = bridgeElement.getBridgeToAddressPolicies();
      if (!toAddress.isEmpty()) {
         toAddressPolicies = new HashSet<>();

         for (AMQPBridgeAddressPolicyElement element : toAddress) {
            logger.debug("AMQP Bridge {} adding bridge to address policy: {}", bridgeElement.getName(), element.getName());
            toAddressPolicies.add(createBridgeAddressPolicy(element, wildcardConfiguration));
         }
      }
      final Set<AMQPBridgeQueuePolicyElement> fromQueue = bridgeElement.getBridgeFromQueuePolicies();
      if (!fromQueue.isEmpty()) {
         fromQueuePolicies = new HashSet<>();

         for (AMQPBridgeQueuePolicyElement element : fromQueue) {
            logger.debug("AMQP Bridge {} adding bridge from Queue policy: {}", bridgeElement.getName(), element.getName());
            fromQueuePolicies.add(createBridgeQueuePolicy(element, wildcardConfiguration));
         }
      }
      final Set<AMQPBridgeQueuePolicyElement> toQueue = bridgeElement.getBridgeToQueuePolicies();
      if (!toQueue.isEmpty()) {
         toQueuePolicies = new HashSet<>();

         for (AMQPBridgeQueuePolicyElement element : toQueue) {
            logger.debug("AMQP Bridge {} adding bridge to Queue policy: {}", bridgeElement.getName(), element.getName());
            toQueuePolicies.add(createBridgeQueuePolicy(element, wildcardConfiguration));
         }
      }

      final AMQPBridgeManager bridge = new AMQPBridgeManager(bridgeElement.getName(),
                                                             connection,
                                                             fromAddressPolicies,
                                                             toAddressPolicies,
                                                             fromQueuePolicies,
                                                             toQueuePolicies,
                                                             bridgeElement.getProperties());

      return bridge;
   }

   /**
    * From the broker AMQP broker connection configuration element and the configured wild-card
    * settings create an queue match policy.
    *
    * @param element
    *    The broker connections element configuration that creates this policy.
    * @param wildcards
    *    The configured wild-card settings for the broker or defaults.
    *
    * @return a new queue match and handling policy for use in the broker connection.
    */
   public static AMQPBridgeQueuePolicy createBridgeQueuePolicy(AMQPBridgeQueuePolicyElement element, WildcardConfiguration wildcards) {
      final Set<Map.Entry<String, String>> includes;
      final Set<Map.Entry<String, String>> excludes;

      final int priorityAdjustment = element.getPriorityAdjustment() != null ?
         element.getPriorityAdjustment() : DEFAULT_PRIORITY_ADJUSTMENT_VALUE;

      if (element.getIncludes() != null && !element.getIncludes().isEmpty()) {
         includes = new HashSet<>(element.getIncludes().size());

         element.getIncludes().forEach(queueMatch ->
            includes.add(new AbstractMap.SimpleImmutableEntry<String, String>(queueMatch.getAddressMatch(), queueMatch.getQueueMatch())));
      } else {
         includes = Collections.emptySet();
      }

      if (element.getExcludes() != null && !element.getExcludes().isEmpty()) {
         excludes = new HashSet<>(element.getExcludes().size());

         element.getExcludes().forEach(queueMatch ->
            excludes.add(new AbstractMap.SimpleImmutableEntry<String, String>(queueMatch.getAddressMatch(), queueMatch.getQueueMatch())));
      } else {
         excludes = Collections.emptySet();
      }

      // We translate from broker configuration to actual implementation to avoid any coupling here
      // as broker configuration could change and or be updated.

      final AMQPBridgeQueuePolicy policy = new AMQPBridgeQueuePolicy(
         element.getName(),
         element.getPriority(),
         priorityAdjustment,
         element.getFilter(),
         element.getRemoteAddress(),
         element.getRemoteAddressPrefix(),
         element.getRemoteAddressSuffix(),
         toSymbolCollection(element.getRemoteTerminusCapabilities()),
         includes,
         excludes,
         element.getProperties(),
         element.getTransformerConfiguration(),
         wildcards);

      return policy;
   }

   /**
    * From the broker AMQP broker connection configuration element and the configured wild-card
    * settings create an address match policy.
    *
    * @param element
    *    The broker connections element configuration that creates this policy.
    * @param wildcards
    *    The configured wild-card settings for the broker or defaults.
    *
    * @return a new address match and handling policy for use in the broker connection.
    */
   public static AMQPBridgeAddressPolicy createBridgeAddressPolicy(AMQPBridgeAddressPolicyElement element, WildcardConfiguration wildcards) {
      final Set<String> includes;
      final Set<String> excludes;

      if (element.getIncludes() != null && !element.getIncludes().isEmpty()) {
         includes = new HashSet<>(element.getIncludes().size());

         element.getIncludes().forEach(addressMatch -> includes.add(addressMatch.getAddressMatch()));
      } else {
         includes = Collections.emptySet();
      }

      if (element.getExcludes() != null && !element.getExcludes().isEmpty()) {
         excludes = new HashSet<>(element.getExcludes().size());

         element.getExcludes().forEach(addressMatch -> excludes.add(addressMatch.getAddressMatch()));
      } else {
         excludes = Collections.emptySet();
      }

      // We translate from broker configuration to actual implementation to avoid any coupling here
      // as broker configuration could change and or be updated.

      final AMQPBridgeAddressPolicy policy = new AMQPBridgeAddressPolicy(
         element.getName(),
         element.isIncludeDivertBindings(),
         element.isUseDurableSubscriptions(),
         element.getPriority(),
         element.getFilter(),
         element.getRemoteAddress(),
         element.getRemoteAddressPrefix(),
         element.getRemoteAddressSuffix(),
         toSymbolCollection(element.getRemoteTerminusCapabilities()),
         includes,
         excludes,
         element.getProperties(),
         element.getTransformerConfiguration(),
         wildcards);

      return policy;
   }

   private static Collection<Symbol> toSymbolCollection(String[] symbols) {
      if (symbols == null || symbols.length == 0) {
         return Collections.emptySet();
      }

      final Set<Symbol> collection = new HashSet<>(symbols.length);

      for (String symbol : symbols) {
         if (symbol == null || symbol.isBlank()) {
            continue;
         }

         collection.add(Symbol.valueOf(symbol));
      }

      return collection.isEmpty() ? Collections.emptySet() : collection;
   }
}
