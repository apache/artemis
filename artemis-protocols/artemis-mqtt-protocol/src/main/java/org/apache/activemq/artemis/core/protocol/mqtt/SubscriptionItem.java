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
package org.apache.activemq.artemis.core.protocol.mqtt;

import io.netty.handler.codec.mqtt.MqttTopicSubscription;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.postoffice.Address;
import org.apache.activemq.artemis.core.postoffice.impl.AddressImpl;
import org.apache.activemq.artemis.core.server.ServerConsumer;

public class SubscriptionItem {

   private MqttTopicSubscription subscription;
   private Integer id;
   private Address address;
   private volatile ServerConsumer consumer;

   public static SubscriptionItem of(MqttTopicSubscription subscription, Integer id) {
      return new SubscriptionItem(subscription, id, null);
   }

   public static SubscriptionItem of(MqttTopicSubscription subscription, Integer id, ServerConsumer consumer) {
      return new SubscriptionItem(subscription, id, consumer);
   }

   private SubscriptionItem(MqttTopicSubscription subscription, Integer id, ServerConsumer consumer) {
      update(subscription, id);
      this.consumer = consumer;
   }

   public MqttTopicSubscription getSubscription() {
      return subscription;
   }

   public Integer getId() {
      return id;
   }

   public ServerConsumer getConsumer() {
      return consumer;
   }

   public SubscriptionItem setConsumer(ServerConsumer consumer) {
      this.consumer = consumer;
      return this;
   }

   public Integer getMatchingId(Address topicToMatch) {
      if (id != null && topicToMatch.matches(address)) {
         return id;
      } else {
         return null;
      }
   }

   public void update(MqttTopicSubscription newSub, Integer newId) {
      if (newId != null && !newId.equals(id)) {
         if (this.address == null || !subscription.topicFilter().equals(newSub.topicFilter())) {
            String topicFilter = newSub.topicFilter();
            if (MQTTUtil.isSharedSubscription(topicFilter)) {
               topicFilter = MQTTUtil.decomposeSharedSubscriptionTopicFilter(newSub.topicFilter()).getB();
            }
            address = new AddressImpl(SimpleString.of(topicFilter), MQTTUtil.MQTT_WILDCARD);
         }
      }
      subscription = newSub;
      id = newId;
   }
}