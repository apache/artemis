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
 * Configuration for broker federation that is managed over an AMQP broker connection.
 */
public class AMQPFederatedBrokerConnectionElement extends AMQPBrokerConnectionElement {

   private static final long serialVersionUID = -6701394020085679414L;

   private Set<AMQPFederationAddressPolicyElement> remoteAddressPolicies = new HashSet<>();
   private Set<AMQPFederationQueuePolicyElement> remoteQueuePolicies = new HashSet<>();

   private Set<AMQPFederationAddressPolicyElement> localAddressPolicies = new HashSet<>();
   private Set<AMQPFederationQueuePolicyElement> localQueuePolicies = new HashSet<>();

   private Map<String, Object> properties = new HashMap<>();

   public AMQPFederatedBrokerConnectionElement() {
      this.setType(AMQPBrokerConnectionAddressType.FEDERATION);
   }

   public AMQPFederatedBrokerConnectionElement(String name) {
      this.setType(AMQPBrokerConnectionAddressType.FEDERATION);
      this.setName(name);
   }

   @Override
   public AMQPFederatedBrokerConnectionElement setType(AMQPBrokerConnectionAddressType type) {
      if (!AMQPBrokerConnectionAddressType.FEDERATION.equals(type)) {
         throw new IllegalArgumentException("Cannot change the type for this broker connection element");
      } else {
         return (AMQPFederatedBrokerConnectionElement) super.setType(type);
      }
   }

   /**
    * @return the configured remote address policy set
    */
   public Set<AMQPFederationAddressPolicyElement> getRemoteAddressPolicies() {
      return remoteAddressPolicies;
   }

   /**
    * @param remoteAddressPolicy the policy to add to the set of remote address policies set
    * @return this configuration element instance
    */
   public AMQPFederatedBrokerConnectionElement addRemoteAddressPolicy(AMQPFederationAddressPolicyElement remoteAddressPolicy) {
      this.remoteAddressPolicies.add(remoteAddressPolicy);
      return this;
   }

   /**
    * @return the configured remote queue policy set
    */
   public Set<AMQPFederationQueuePolicyElement> getRemoteQueuePolicies() {
      return remoteQueuePolicies;
   }

   /**
    * @param remoteQueuePolicy the policy to add to the set of remote queue policies set
    * @return this configuration element instance
    */
   public AMQPFederatedBrokerConnectionElement addRemoteQueuePolicy(AMQPFederationQueuePolicyElement remoteQueuePolicy) {
      this.remoteQueuePolicies.add(remoteQueuePolicy);
      return this;
   }

   /**
    * @return the configured local address policy set
    */
   public Set<AMQPFederationAddressPolicyElement> getLocalAddressPolicies() {
      return localAddressPolicies;
   }

   /**
    * @param localAddressPolicy the policy to add to the set of local address policies set
    * @return this configuration element instance
    */
   public AMQPFederatedBrokerConnectionElement addLocalAddressPolicy(AMQPFederationAddressPolicyElement localAddressPolicy) {
      this.localAddressPolicies.add(localAddressPolicy);
      return this;
   }

   /**
    * @return the configured local queue policy set
    */
   public Set<AMQPFederationQueuePolicyElement> getLocalQueuePolicies() {
      return localQueuePolicies;
   }

   /**
    * @param localQueuePolicy the policy to add to the set of local queue policies set
    * @return this configuration element instance
    */
   public AMQPFederatedBrokerConnectionElement addLocalQueuePolicy(AMQPFederationQueuePolicyElement localQueuePolicy) {
      this.localQueuePolicies.add(localQueuePolicy);
      return this;
   }

   /**
    * Adds the given property key and value to the federation configuration element.
    *
    * @param key   The key that identifies the property
    * @param value The value associated with the property key.
    * @return this configuration element instance
    */
   public AMQPFederatedBrokerConnectionElement addProperty(String key, String value) {
      properties.put(key, value);
      return this;
   }

   /**
    * Adds the given property key and value to the federation configuration element.
    *
    * @param key   The key that identifies the property
    * @param value The value associated with the property key.
    * @return this configuration element instance
    */
   public AMQPFederatedBrokerConnectionElement addProperty(String key, Number value) {
      properties.put(key, value);
      return this;
   }

   /**
    * @return the collection of configuration properties associated with this federation element
    */
   public Map<String, Object> getProperties() {
      return properties;
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), localAddressPolicies, localQueuePolicies, properties, remoteAddressPolicies,
                          remoteQueuePolicies);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!super.equals(obj)) {
         return false;
      }
      if (!(obj instanceof AMQPFederatedBrokerConnectionElement other)) {
         return false;
      }

      return Objects.equals(localAddressPolicies, other.localAddressPolicies) &&
             Objects.equals(localQueuePolicies, other.localQueuePolicies) &&
             Objects.equals(properties, other.properties) &&
             Objects.equals(remoteAddressPolicies, other.remoteAddressPolicies) &&
             Objects.equals(remoteQueuePolicies, other.remoteQueuePolicies);
   }
}
