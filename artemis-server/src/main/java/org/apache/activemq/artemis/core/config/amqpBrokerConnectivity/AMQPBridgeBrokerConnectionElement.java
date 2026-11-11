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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Configuration for broker AMQP message bridge policy managed by a broker connection.
 */
public class AMQPBridgeBrokerConnectionElement extends AMQPBrokerConnectionElement {

   private static final long serialVersionUID = 2519807622833191168L;

   private Set<AMQPBridgeAddressPolicyElement> bridgeFromAddressPolicies = new HashSet<>();
   private Set<AMQPBridgeQueuePolicyElement> bridgeFromQueuePolicies = new HashSet<>();

   private Set<AMQPBridgeAddressPolicyElement> bridgeToAddressPolicies = new HashSet<>();
   private Set<AMQPBridgeQueuePolicyElement> bridgeToQueuePolicies = new HashSet<>();

   private Map<String, Object> properties = new HashMap<>();

   public AMQPBridgeBrokerConnectionElement() {
      this.setType(AMQPBrokerConnectionAddressType.BRIDGE);
   }

   public AMQPBridgeBrokerConnectionElement(String name) {
      this.setType(AMQPBrokerConnectionAddressType.BRIDGE);
      this.setName(name);
   }

   @Override
   public AMQPBridgeBrokerConnectionElement setType(AMQPBrokerConnectionAddressType type) {
      if (AMQPBrokerConnectionAddressType.BRIDGE.equals(type)) {
         return (AMQPBridgeBrokerConnectionElement) super.setType(type);
      } else {
         throw new IllegalArgumentException("Cannot change a BRIDGE connection element to type: " + type);
      }
   }

   /**
    * @return the configured bridge from address policy set
    */
   public Set<AMQPBridgeAddressPolicyElement> getBridgeFromAddressPolicies() {
      return bridgeFromAddressPolicies;
   }

   /**
    * @param fromAddressPolicy
    *       the policy to add to the set of bridge from address policies set
    *
    * @return this configuration element instance.
    */
   public AMQPBridgeBrokerConnectionElement addBridgeFromAddressPolicy(AMQPBridgeAddressPolicyElement fromAddressPolicy) {
      this.bridgeFromAddressPolicies.add(fromAddressPolicy);
      return this;
   }

   /**
    * @return the configured bridge to address policy set
    */
   public Set<AMQPBridgeAddressPolicyElement> getBridgeToAddressPolicies() {
      return bridgeToAddressPolicies;
   }

   /**
    * @param toAddressPolicy
    *       the policy to add to the set of bridge to address policies set
    *
    * @return this configuration element instance.
    */
   public AMQPBridgeBrokerConnectionElement addBridgeToAddressPolicy(AMQPBridgeAddressPolicyElement toAddressPolicy) {
      this.bridgeToAddressPolicies.add(toAddressPolicy);
      return this;
   }

   /**
    * @return the configured bridge from queue policy set
    */
   public Set<AMQPBridgeQueuePolicyElement> getBridgeFromQueuePolicies() {
      return bridgeFromQueuePolicies;
   }

   /**
    * @param fromQueuePolicy
    *       the policy to add to the set of bridge from queue policies set
    *
    * @return this configuration element instance.
    */
   public AMQPBridgeBrokerConnectionElement addBridgeFromQueuePolicy(AMQPBridgeQueuePolicyElement fromQueuePolicy) {
      this.bridgeFromQueuePolicies.add(fromQueuePolicy);
      return this;
   }

   /**
    * @return the configured bridge to queue policy set
    */
   public Set<AMQPBridgeQueuePolicyElement> getBridgeToQueuePolicies() {
      return bridgeToQueuePolicies;
   }

   /**
    * @param toQueuePolicy
    *       the policy to add to the set of bridge to queue policies set
    *
    * @return this configuration element instance.
    */
   public AMQPBridgeBrokerConnectionElement addBridgeToQueuePolicy(AMQPBridgeQueuePolicyElement toQueuePolicy) {
      this.bridgeToQueuePolicies.add(toQueuePolicy);
      return this;
   }
   /**
    * Adds the given property key and value to the bridge configuration element.
    *
    * @param key
    *    The key that identifies the property
    * @param value
    *    The value associated with the property key.
    *
    * @return this configuration element instance.
    */
   public AMQPBridgeBrokerConnectionElement addProperty(String key, String value) {
      properties.put(key, value);
      return this;
   }

   /**
    * Adds the given property key and value to the bridge configuration element.
    *
    * @param key
    *    The key that identifies the property
    * @param value
    *    The value associated with the property key.
    *
    * @return this configuration element instance.
    */
   public AMQPBridgeBrokerConnectionElement addProperty(String key, Number value) {
      properties.put(key, value);
      return this;
   }

   /**
    * @return the collection of configuration properties associated with this bridge element.
    */
   public Map<String, Object> getProperties() {
      return properties;
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), bridgeFromAddressPolicies, bridgeFromQueuePolicies, properties,
                          bridgeToAddressPolicies, bridgeToQueuePolicies);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!super.equals(obj)) {
         return false;
      }
      if (!(obj instanceof AMQPBridgeBrokerConnectionElement other)) {
         return false;
      }

      return Objects.equals(bridgeFromAddressPolicies, other.bridgeFromAddressPolicies) &&
             Objects.equals(bridgeFromQueuePolicies, other.bridgeFromQueuePolicies) &&
             Objects.equals(properties, other.properties) &&
             Objects.equals(bridgeToAddressPolicies, other.bridgeToAddressPolicies) &&
             Objects.equals(bridgeToQueuePolicies, other.bridgeToQueuePolicies);
   }
}
