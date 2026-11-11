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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.activemq.artemis.api.core.SimpleString;

public class AMQPMirrorBrokerConnectionElement extends AMQPBrokerConnectionElement {

   private static final long serialVersionUID = -6171198691682381614L;

   boolean durable = true;

   boolean queueCreation = true;

   boolean queueRemoval = true;

   boolean messageAcknowledgements = true;

   boolean sync = false;

   SimpleString mirrorSNF;

   String addressFilter;

   private Map<String, Object> properties = new HashMap<>();

   public SimpleString getMirrorSNF() {
      return mirrorSNF;
   }

   public AMQPMirrorBrokerConnectionElement setMirrorSNF(SimpleString mirrorSNF) {
      this.mirrorSNF = mirrorSNF;
      return this;
   }

   public AMQPMirrorBrokerConnectionElement() {
      this.setType(AMQPBrokerConnectionAddressType.MIRROR);
   }

   /**
    * There is no setter for this property. Basically by setting a sourceMirrorAddress we are automatically setting this
    * to true.
    */
   public boolean isDurable() {
      return durable;
   }

   public AMQPMirrorBrokerConnectionElement setDurable(boolean durable) {
      this.durable = durable;
      return this;
   }

   public boolean isQueueCreation() {
      return queueCreation;
   }

   public AMQPMirrorBrokerConnectionElement setQueueCreation(boolean queueCreation) {
      this.queueCreation = queueCreation;
      return this;
   }

   public boolean isQueueRemoval() {
      return queueRemoval;
   }

   public AMQPMirrorBrokerConnectionElement setQueueRemoval(boolean queueRemoval) {
      this.queueRemoval = queueRemoval;
      return this;
   }

   @Override
   public AMQPMirrorBrokerConnectionElement setType(AMQPBrokerConnectionAddressType type) {
      super.setType(type);
      return this;
   }

   public boolean isMessageAcknowledgements() {
      return messageAcknowledgements;
   }

   public AMQPMirrorBrokerConnectionElement setMessageAcknowledgements(boolean messageAcknowledgements) {
      this.messageAcknowledgements = messageAcknowledgements;
      return this;
   }

   public String getAddressFilter() {
      return addressFilter;
   }

   public AMQPMirrorBrokerConnectionElement setAddressFilter(String addressFilter) {
      this.addressFilter = addressFilter;
      return this;
   }

   public boolean isSync() {
      return sync;
   }

   public AMQPMirrorBrokerConnectionElement setSync(boolean sync) {
      this.sync = sync;
      return this;
   }

   /**
    * Adds the given property key and value to the mirror broker configuration element.
    *
    * @param key   The key that identifies the property
    * @param value The value associated with the property key.
    * @return this configuration element instance
    */
   public AMQPMirrorBrokerConnectionElement addProperty(String key, String value) {
      properties.put(key, value);
      return this;
   }

   /**
    * Adds the given property key and value to the mirror broker configuration element.
    *
    * @param key   The key that identifies the property
    * @param value The value associated with the property key.
    * @return this configuration element instance
    */
   public AMQPMirrorBrokerConnectionElement addProperty(String key, Number value) {
      properties.put(key, value);
      return this;
   }

   /**
    * @return the collection of configuration properties associated with this mirror configuration element
    */
   public Map<String, Object> getProperties() {
      return properties;
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), addressFilter, durable, messageAcknowledgements, mirrorSNF, queueCreation,
                          queueRemoval, sync);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!super.equals(obj)) {
         return false;
      }
      if (!(obj instanceof AMQPMirrorBrokerConnectionElement other)) {
         return false;
      }

      return Objects.equals(addressFilter, other.addressFilter) &&
             Objects.equals(durable, other.durable) &&
             Objects.equals(messageAcknowledgements, other.messageAcknowledgements) &&
             Objects.equals(mirrorSNF, other.mirrorSNF) &&
             Objects.equals(queueCreation, other.queueCreation) &&
             Objects.equals(queueRemoval, other.queueRemoval) &&
             Objects.equals(sync, other.sync);
   }
}
