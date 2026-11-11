/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.config.amqpBrokerConnectivity;

import java.io.Serializable;
import java.util.Objects;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.apache.activemq.artemis.core.postoffice.impl.AddressImpl;

public class AMQPBrokerConnectionElement implements Serializable {

   private static final long serialVersionUID = 3653295602796835937L;

   String name = "default";
   SimpleString matchAddress;
   SimpleString queueName;
   AMQPBrokerConnectionAddressType type;
   AMQPBrokerConnectConfiguration parent;

   public AMQPBrokerConnectionElement() {
   }

   public AMQPBrokerConnectConfiguration getParent() {
      return parent;
   }

   public AMQPBrokerConnectionElement setParent(AMQPBrokerConnectConfiguration parent) {
      this.parent = parent;
      return this;
   }

   public SimpleString getQueueName() {
      return queueName;
   }

   public AMQPBrokerConnectionElement setQueueName(String queueName) {
      return setQueueName(SimpleString.of(queueName));
   }

   public AMQPBrokerConnectionElement setQueueName(SimpleString queueName) {
      this.queueName = queueName;
      return this;
   }

   public SimpleString getMatchAddress() {
      return matchAddress;
   }

   public boolean match(SimpleString checkAddress, WildcardConfiguration wildcardConfig) {
      return match(matchAddress, checkAddress, wildcardConfig);
   }

   public static boolean match(SimpleString matchAddressString, SimpleString checkAddressString, WildcardConfiguration wildcardConfig) {
      AddressImpl matchAddress = new AddressImpl(matchAddressString, wildcardConfig);
      AddressImpl checkAddress = new AddressImpl(checkAddressString, wildcardConfig);
      return checkAddress.matches(matchAddress);
   }

   public AMQPBrokerConnectionElement setMatchAddress(String matchAddress) {
      return this.setMatchAddress(SimpleString.of(matchAddress));
   }

   public AMQPBrokerConnectionElement setMatchAddress(SimpleString matchAddress) {
      this.matchAddress = matchAddress;
      return this;
   }

   public AMQPBrokerConnectionAddressType getType() {
      return type;
   }

   public AMQPBrokerConnectionElement setType(AMQPBrokerConnectionAddressType type) {
      this.type = type;
      return this;
   }

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   @Override
   public int hashCode() {
      // Don't pass the parent into hash or you will get a loop of hash code computations.
      return Objects.hash(matchAddress, name, queueName, type);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof AMQPBrokerConnectionElement other)) {
         return false;
      }

      return Objects.equals(type, other.type) &&
             Objects.equals(name, other.name) &&
             Objects.equals(matchAddress, other.matchAddress) &&
             Objects.equals(queueName, other.queueName);
   }
}
