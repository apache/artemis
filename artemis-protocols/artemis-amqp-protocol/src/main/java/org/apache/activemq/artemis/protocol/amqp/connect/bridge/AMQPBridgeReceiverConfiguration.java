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

import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.IGNORE_QUEUE_CONSUMER_FILTERS;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.IGNORE_QUEUE_FILTERS;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.RECEIVER_CREDITS;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.RECEIVER_CREDITS_LOW;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.RECEIVER_DRAIN_ON_TRANSIENT_DELIVERY_ERRORS;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.RECEIVER_LINK_QUIESCE_TIMEOUT;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.RECEIVER_QUIESCE_TIMEOUT;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.USE_MODIFIED_FOR_TRANSIENT_DELIVERY_ERRORS;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.PULL_RECEIVER_BATCH_SIZE;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.QUEUE_RECEIVER_IDLE_TIMEOUT;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.LARGE_MESSAGE_THRESHOLD;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.PREFER_SHARED_DURABLE_SUBSCRIPTIONS;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.ADDRESS_RECEIVER_IDLE_TIMEOUT;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.DISABLE_RECEIVER_DEMAND_TRACKING;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.DISABLE_RECEIVER_PRIORITY;
import java.util.Map;

/**
 * Configuration options applied to a receiver created from bridge from policies for
 * address or queue bridging. The options first check the policy properties for
 * matching configuration settings before looking at the bridgeManager's own configuration
 * for the options managed here.
 */
public final class AMQPBridgeReceiverConfiguration extends AMQPBridgeLinkConfiguration {

   public AMQPBridgeReceiverConfiguration(AMQPBridgeConfiguration configuration, Map<String, ?> properties) {
      super(configuration, properties);
   }

   /**
    * {@return the credit batch size offered to a Receiver link}
    */
   public int getReceiverCredits() {
      final Object property = properties.get(RECEIVER_CREDITS);
      if (property instanceof Number) {
         return ((Number) property).intValue();
      } else if (property instanceof String) {
         return Integer.parseInt((String) property);
      } else {
         return configuration.getReceiverCredits();
      }
   }

   /**
    * {@return the number of remaining credits on a Receiver before the batch is replenished}
    */
   public int getReceiverCreditsLow() {
      final Object property = properties.get(RECEIVER_CREDITS_LOW);
      if (property instanceof Number) {
         return ((Number) property).intValue();
      } else if (property instanceof String) {
         return Integer.parseInt((String) property);
      } else {
         return configuration.getReceiverCreditsLow();
      }
   }

   /**
    * {@return the credit batch size offered to a Receiver link that is in pull mode}
    */
   public int getPullReceiverBatchSize() {
      final Object property = properties.get(PULL_RECEIVER_BATCH_SIZE);
      if (property instanceof Number) {
         return ((Number) property).intValue();
      } else if (property instanceof String) {
         return Integer.parseInt((String) property);
      } else {
         return configuration.getPullReceiverBatchSize();
      }
   }

   /**
    * {@return the configured receiver quiesce timeout before the operation is assumed to have failed}
    */
   public int getReceiverQuiesceTimeout() {
      final Object property = properties.get(RECEIVER_QUIESCE_TIMEOUT);
      if (property instanceof Number number) {
         return number.intValue();
      } else if (property instanceof String string) {
         return Integer.parseInt(string);
      } else {
         return configuration.getReceiverQuiesceTimeout();
      }
   }

   /**
    * {@return the idle timeout for a drained bridge address receiver before it is closed}
    */
   public int getAddressReceiverIdleTimeout() {
      final Object property = properties.get(ADDRESS_RECEIVER_IDLE_TIMEOUT);
      if (property instanceof Number number) {
         return number.intValue();
      } else if (property instanceof String string) {
         return Integer.parseInt(string);
      } else {
         return configuration.getAddressReceiverIdleTimeout();
      }
   }

   /**
    * {@return the idle timeout for a drained bridge queue receiver before it is closed}
    */
   public int getQueueReceiverIdleTimeout() {
      final Object property = properties.get(QUEUE_RECEIVER_IDLE_TIMEOUT);
      if (property instanceof Number number) {
         return number.intValue();
      } else if (property instanceof String string) {
         return Integer.parseInt(string);
      } else {
         return configuration.getQueueReceiverIdleTimeout();
      }
   }

   /**
    * {@return the size in bytes of an incoming message after which the Receiver treats it as large}
    */
   public int getLargeMessageThreshold() {
      final Object property = properties.get(LARGE_MESSAGE_THRESHOLD);
      if (property instanceof Number) {
         return ((Number) property).intValue();
      } else if (property instanceof String) {
         return Integer.parseInt((String) property);
      } else {
         return configuration.getLargeMessageThreshold();
      }
   }

   /**
    * {@return <code>true</code> if the bridgeManager is configured to ignore filters on individual queue consumers}
    */
   public boolean isIgnoreSubscriptionFilters() {
      final Object property = properties.get(IGNORE_QUEUE_CONSUMER_FILTERS);
      if (property instanceof Boolean) {
         return (Boolean) property;
      } else if (property instanceof String) {
         return Boolean.parseBoolean((String) property);
      } else {
         return configuration.isIgnoreSubscriptionFilters();
      }
   }

   /**
    * {@return <code>true</code> if the bridgeManager is configured to ignore filters on the bridged Queue}
    */
   public boolean isIgnoreQueueFilters() {
      final Object property = properties.get(IGNORE_QUEUE_FILTERS);
      if (property instanceof Boolean) {
         return (Boolean) property;
      } else if (property instanceof String) {
         return Boolean.parseBoolean((String) property);
      } else {
         return configuration.isIgnoreQueueFilters();
      }
   }

   /**
    * {@return <code>true</code> if bridgeManager is configured to omit any priority properties on receiver links}
    */
   public boolean isReceiverPriorityDisabled() {
      final Object property = properties.get(DISABLE_RECEIVER_PRIORITY);
      if (property instanceof Boolean) {
         return (Boolean) property;
      } else if (property instanceof String) {
         return Boolean.parseBoolean((String) property);
      } else {
         return configuration.isReceiverPriorityDisabled();
      }
   }

   /**
    * {@return <code>true</code> if bridgeManager is configured to ignore local demand and always create a receiver}
    */
   public boolean isReceiverDemandTrackingDisabled() {
      final Object property = properties.get(DISABLE_RECEIVER_DEMAND_TRACKING);
      if (property instanceof Boolean) {
         return (Boolean) property;
      } else if (property instanceof String) {
         return Boolean.parseBoolean((String) property);
      } else {
         return configuration.isReceiverDemandTrackingDisabled();
      }
   }

   /**
    * {@return <code>true</code> if bridge from address policies are configured to prefer using shared durable address subscriptions}
    */
   public boolean isPreferSharedDurableSubscriptions() {
      final Object property = properties.get(PREFER_SHARED_DURABLE_SUBSCRIPTIONS);
      if (property instanceof Boolean) {
         return (Boolean) property;
      } else if (property instanceof String) {
         return Boolean.parseBoolean((String) property);
      } else {
         return configuration.isPreferSharedDurableSubscriptions();
      }
   }

   /**
    * (@return the use modified for transient delivery errors configuration}
    */
   public boolean isUseModifiedForTransientDeliveryErrors() {
      final Object property = properties.get(USE_MODIFIED_FOR_TRANSIENT_DELIVERY_ERRORS);
      if (property instanceof Boolean booleanValue) {
         return booleanValue;
      } else if (property instanceof String string) {
         return Boolean.parseBoolean(string);
      } else {
         return configuration.isUseModifiedForTransientDeliveryErrors();
      }
   }

   /**
    * (@return the drain link credit on transient delivery errors configuration}
    */
   public boolean isDrainOnTransientDeliveryErrors() {
      final Object property = properties.get(RECEIVER_DRAIN_ON_TRANSIENT_DELIVERY_ERRORS);
      if (property instanceof Boolean booleanValue) {
         return booleanValue;
      } else if (property instanceof String string) {
         return Boolean.parseBoolean(string);
      } else {
         return configuration.isDrainOnTransientDeliveryErrors();
      }
   }

   /**
    * {@return the federation receiver link quiesce timeout configuration}
    */
   public int getLinkQuiesceTimeout() {
      final Object property = properties.get(RECEIVER_LINK_QUIESCE_TIMEOUT);
      if (property instanceof Number number) {
         return number.intValue();
      } else if (property instanceof String string) {
         return Integer.parseInt(string);
      } else {
         return configuration.getLinkQuiesceTimeout();
      }
   }
}
