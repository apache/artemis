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

import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.AUTO_DELETE_DURABLE_SUBSCRIPTION;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.AUTO_DELETE_DURABLE_SUBSCRIPTION_DELAY;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.AUTO_DELETE_DURABLE_SUBSCRIPTION_MSG_COUNT;
import java.util.Map;

/**
 * Configuration options applied to a sender created from bridge from policies for
 * address or queue bridging. The options first check the policy properties for
 * matching configuration settings before looking at the bridge's own configuration
 * for the options managed here.
 */
public final class AMQPBridgeSenderConfiguration extends AMQPBridgeLinkConfiguration {

   public AMQPBridgeSenderConfiguration(AMQPBridgeConfiguration configuration, Map<String, ?> properties) {
      super(configuration, properties);
   }

   /**
    * {@return <code>true</code> if bridge is configured to auto delete address senders using durable subscription bindings}
    */
   public boolean isAutoDeleteDurableSubscriptions() {
      final Object property = properties.get(AUTO_DELETE_DURABLE_SUBSCRIPTION);
      if (property instanceof Boolean) {
         return (Boolean) property;
      } else if (property instanceof String) {
         return Boolean.parseBoolean((String) property);
      } else {
         return configuration.isAutoDeleteDurableSubscriptions();
      }
   }

   /**
    * {@return the auto delete subscription message count for address senders using durable bindings}
    */
   public long getAutoDeleteDurableSubscriptionMsgCount() {
      final Object property = properties.get(AUTO_DELETE_DURABLE_SUBSCRIPTION_MSG_COUNT);
      if (property instanceof Number) {
         return ((Number) property).intValue();
      } else if (property instanceof String) {
         return Integer.parseInt((String) property);
      } else {
         return configuration.getAutoDeleteDurableSubscriptionMsgCount();
      }
   }

   /**
    * {@return the auto delete subscription delay for address senders using durable bindings}
    */
   public long getAutoDeleteDurableSubscriptionDelay() {
      final Object property = properties.get(AUTO_DELETE_DURABLE_SUBSCRIPTION_DELAY);
      if (property instanceof Number) {
         return ((Number) property).intValue();
      } else if (property instanceof String) {
         return Integer.parseInt((String) property);
      } else {
         return configuration.getAutoDeleteDurableSubscriptionDelay();
      }
   }
}
