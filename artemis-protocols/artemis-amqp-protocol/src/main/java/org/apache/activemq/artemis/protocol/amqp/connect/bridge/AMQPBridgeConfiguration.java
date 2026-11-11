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

import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.LARGE_MESSAGE_THRESHOLD;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.LINK_ATTACH_TIMEOUT;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.LINK_RECOVERY_DELAY;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.LINK_RECOVERY_INITIAL_DELAY;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.MAX_LINK_RECOVERY_ATTEMPTS;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.PREFER_SHARED_DURABLE_SUBSCRIPTIONS;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.PRESETTLE_SEND_MODE;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.DISABLE_RECEIVER_PRIORITY;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.PULL_RECEIVER_BATCH_SIZE;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.QUEUE_RECEIVER_IDLE_TIMEOUT;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.RECEIVER_CREDITS;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.RECEIVER_CREDITS_LOW;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.RECEIVER_QUIESCE_TIMEOUT;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.USE_MODIFIED_FOR_TRANSIENT_DELIVERY_ERRORS;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.RECEIVER_DRAIN_ON_TRANSIENT_DELIVERY_ERRORS;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.RECEIVER_LINK_QUIESCE_TIMEOUT;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.IGNORE_QUEUE_FILTERS;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.ADDRESS_RECEIVER_IDLE_TIMEOUT;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.AUTO_DELETE_DURABLE_SUBSCRIPTION;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.AUTO_DELETE_DURABLE_SUBSCRIPTION_DELAY;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.AUTO_DELETE_DURABLE_SUBSCRIPTION_MSG_COUNT;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.DEFAULT_ADDRESS_RECEIVER_IDLE_TIMEOUT;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.DEFAULT_AUTO_DELETE_DURABLE_SUBSCRIPTION;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.DEFAULT_AUTO_DELETE_DURABLE_SUBSCRIPTION_DELAY;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.DEFAULT_AUTO_DELETE_DURABLE_SUBSCRIPTION_MSG_COUNT;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.DEFAULT_CORE_MESSAGE_TUNNELING_ENABLED;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.DEFAULT_DISABLE_RECEIVER_DEMAND_TRACKING;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.DEFAULT_IGNNORE_QUEUE_CONSUMER_FILTERS;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.DEFAULT_IGNNORE_QUEUE_FILTERS;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.DEFAULT_LINK_ATTACH_TIMEOUT;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.DEFAULT_LINK_RECOVERY_DELAY;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.DEFAULT_LINK_RECOVERY_INITIAL_DELAY;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.DEFAULT_MAX_LINK_RECOVERY_ATTEMPTS;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.DEFAULT_PREFER_SHARED_DURABLE_SUBSCRIPTIONS;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.DEFAULT_DISABLE_RECEIVER_PRIORITY;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.DEFAULT_PULL_CREDIT_BATCH_SIZE;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.DEFAULT_QUEUE_RECEIVER_IDLE_TIMEOUT;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.DEFAULT_RECEIVER_QUIESCE_TIMEOUT;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.DEFAULT_SEND_SETTLED;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.DISABLE_RECEIVER_DEMAND_TRACKING;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.IGNORE_QUEUE_CONSUMER_FILTERS;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.DEFAULT_USE_MODIFIED_FOR_TRANSIENT_DELIVERY_ERRORS;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.activemq.artemis.protocol.amqp.proton.AMQPConnectionContext;
import org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport;

/**
 * A configuration class that contains API for getting AMQP bridge specific configuration
 * either from a {@link Map} of configuration elements or from the connection associated
 * with the bridge instance, or possibly from a set default value.
 */
public class AMQPBridgeConfiguration {

   private final Map<String, Object> properties;
   private final AMQPConnectionContext connection;

   public AMQPBridgeConfiguration(AMQPConnectionContext connection, Map<String, Object> properties) {
      Objects.requireNonNull(connection, "Connection provided cannot be null");

      this.connection = connection;

      if (properties != null && !properties.isEmpty()) {
         this.properties = new HashMap<>(properties);
      } else {
         this.properties = Collections.emptyMap();
      }
   }

   /**
    * {@return the credit batch size offered to a receiver link}
    */
   public int getReceiverCredits() {
      final Object property = properties.get(RECEIVER_CREDITS);
      if (property instanceof Number) {
         return ((Number) property).intValue();
      } else if (property instanceof String) {
         return Integer.parseInt((String) property);
      } else {
         return connection.getAmqpCredits();
      }
   }

   /**
    * {@return the number of remaining credits on a receiver before the batch is replenished}
    */
   public int getReceiverCreditsLow() {
      final Object property = properties.get(RECEIVER_CREDITS_LOW);
      if (property instanceof Number) {
         return ((Number) property).intValue();
      } else if (property instanceof String) {
         return Integer.parseInt((String) property);
      } else {
         return connection.getAmqpLowCredits();
      }
   }

   /**
    * {@return the credit batch size offered to a receiver link that is in pull mode}
    */
   public int getPullReceiverBatchSize() {
      final Object property = properties.get(PULL_RECEIVER_BATCH_SIZE);
      if (property instanceof Number) {
         return ((Number) property).intValue();
      } else if (property instanceof String) {
         return Integer.parseInt((String) property);
      } else {
         return DEFAULT_PULL_CREDIT_BATCH_SIZE;
      }
   }

   /**
    * {@return the size in bytes of an incoming message after which the receiver treats it as large}
    */
   public int getLargeMessageThreshold() {
      final Object property = properties.get(LARGE_MESSAGE_THRESHOLD);
      if (property instanceof Number) {
         return ((Number) property).intValue();
      } else if (property instanceof String) {
         return Integer.parseInt((String) property);
      } else {
         return connection.getProtocolManager().getAmqpMinLargeMessageSize();
      }
   }

   /**
    * {@return the timeout value to use when waiting for a corresponding link attach from the remote}
    */
   public int getLinkAttachTimeout() {
      final Object property = properties.get(LINK_ATTACH_TIMEOUT);
      if (property instanceof Number) {
         return ((Number) property).intValue();
      } else if (property instanceof String) {
         return Integer.parseInt((String) property);
      } else {
         return DEFAULT_LINK_ATTACH_TIMEOUT;
      }
   }

   /**
    * {@return true if the bridge is configured to tunnel core messages as AMQP custom messages}
    */
   public boolean isCoreMessageTunnelingEnabled() {
      final Object property = properties.get(AmqpSupport.TUNNEL_CORE_MESSAGES);
      if (property instanceof Boolean) {
         return (Boolean) property;
      } else if (property instanceof String) {
         return Boolean.parseBoolean((String) property);
      } else {
         return DEFAULT_CORE_MESSAGE_TUNNELING_ENABLED;
      }
   }

   /**
    * {@return <code>true</code> if the bridge is configured to ignore filters on individual queue consumers}
    */
   public boolean isIgnoreSubscriptionFilters() {
      final Object property = properties.get(IGNORE_QUEUE_CONSUMER_FILTERS);
      if (property instanceof Boolean) {
         return (Boolean) property;
      } else if (property instanceof String) {
         return Boolean.parseBoolean((String) property);
      } else {
         return DEFAULT_IGNNORE_QUEUE_CONSUMER_FILTERS;
      }
   }

   /**
    * {@return <code>true</code> if the bridge is configured to ignore filters on the bridged Queue}
    */
   public boolean isIgnoreQueueFilters() {
      final Object property = properties.get(IGNORE_QUEUE_FILTERS);
      if (property instanceof Boolean) {
         return (Boolean) property;
      } else if (property instanceof String) {
         return Boolean.parseBoolean((String) property);
      } else {
         return DEFAULT_IGNNORE_QUEUE_FILTERS;
      }
   }

   /**
    * {@return <code>true</code> if bridge is configured to omit any priority properties on receiver links}
    */
   public boolean isReceiverPriorityDisabled() {
      final Object property = properties.get(DISABLE_RECEIVER_PRIORITY);
      if (property instanceof Boolean) {
         return (Boolean) property;
      } else if (property instanceof String) {
         return Boolean.parseBoolean((String) property);
      } else {
         return DEFAULT_DISABLE_RECEIVER_PRIORITY;
      }
   }

   /**
    * {@return <code>true</code> if bridge is configured to ignore local demand and always create a receiver}
    */
   public boolean isReceiverDemandTrackingDisabled() {
      final Object property = properties.get(DISABLE_RECEIVER_DEMAND_TRACKING);
      if (property instanceof Boolean) {
         return (Boolean) property;
      } else if (property instanceof String) {
         return Boolean.parseBoolean((String) property);
      } else {
         return DEFAULT_DISABLE_RECEIVER_DEMAND_TRACKING;
      }
   }

   /**
    * {@return the receive quiesce timeout when shutting down a receiver when local demand is removed}
    */
   public int getReceiverQuiesceTimeout() {
      final Object property = properties.get(RECEIVER_QUIESCE_TIMEOUT);
      if (property instanceof Number number) {
         return number.intValue();
      } else if (property instanceof String string) {
         return Integer.parseInt(string);
      } else {
         return DEFAULT_RECEIVER_QUIESCE_TIMEOUT;
      }
   }

   /**
    * {@return the receive idle timeout when shutting down a address Receiver when local demand is removed}
    */
   public int getAddressReceiverIdleTimeout() {
      final Object property = properties.get(ADDRESS_RECEIVER_IDLE_TIMEOUT);
      if (property instanceof Number number) {
         return number.intValue();
      } else if (property instanceof String string) {
         return Integer.parseInt(string);
      } else {
         return DEFAULT_ADDRESS_RECEIVER_IDLE_TIMEOUT;
      }
   }

   /**
    * {@return the receive idle timeout when shutting down a queue Receiver when local demand is removed}
    */
   public int getQueueReceiverIdleTimeout() {
      final Object property = properties.get(QUEUE_RECEIVER_IDLE_TIMEOUT);
      if (property instanceof Number number) {
         return number.intValue();
      } else if (property instanceof String string) {
         return Integer.parseInt(string);
      } else {
         return DEFAULT_QUEUE_RECEIVER_IDLE_TIMEOUT;
      }
   }

   /**
    * {@return <code>true</code> if bridge is configured to create links with the sender settle mode set to settled}
    */
   public boolean isUsingPresettledSenders() {
      final Object property = properties.get(PRESETTLE_SEND_MODE);
      if (property instanceof Boolean) {
         return (Boolean) property;
      } else if (property instanceof String) {
         return Boolean.parseBoolean((String) property);
      } else {
         return DEFAULT_SEND_SETTLED;
      }
   }

   /**
    * {@return the maximum number of link recovery attempts, or zero if no attempts allowed}
    */
   public int getMaxLinkRecoveryAttempts() {
      final Object property = properties.get(MAX_LINK_RECOVERY_ATTEMPTS);
      if (property instanceof Number) {
         return ((Number) property).intValue();
      } else if (property instanceof String) {
         return Integer.parseInt((String) property);
      } else {
         return DEFAULT_MAX_LINK_RECOVERY_ATTEMPTS;
      }
   }

   /**
    * {@return the initial delay before a link recovery attempt is made}
    */
   public long getLinkRecoveryInitialDelay() {
      final Object property = properties.get(LINK_RECOVERY_INITIAL_DELAY);
      if (property instanceof Number) {
         return ((Number) property).intValue();
      } else if (property instanceof String) {
         return Integer.parseInt((String) property);
      } else {
         return DEFAULT_LINK_RECOVERY_INITIAL_DELAY;
      }
   }

   /**
    * {@return the delay that will be used between successive link recovery attempts}
    */
   public long getLinkRecoveryDelay() {
      final Object property = properties.get(LINK_RECOVERY_DELAY);
      if (property instanceof Number) {
         return ((Number) property).intValue();
      } else if (property instanceof String) {
         return Integer.parseInt((String) property);
      } else {
         return DEFAULT_LINK_RECOVERY_DELAY;
      }
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
         return DEFAULT_AUTO_DELETE_DURABLE_SUBSCRIPTION;
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
         return DEFAULT_AUTO_DELETE_DURABLE_SUBSCRIPTION_MSG_COUNT;
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
         return DEFAULT_AUTO_DELETE_DURABLE_SUBSCRIPTION_DELAY;
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
         return DEFAULT_PREFER_SHARED_DURABLE_SUBSCRIPTIONS;
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
         return DEFAULT_USE_MODIFIED_FOR_TRANSIENT_DELIVERY_ERRORS;
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
         return connection.getProtocolManager().isDrainOnTransientDeliveryErrors();
      }
   }

   /**
    * {@return the bridge receiver link quiesce timeout configuration}
    */
   public int getLinkQuiesceTimeout() {
      final Object property = properties.get(RECEIVER_LINK_QUIESCE_TIMEOUT);
      if (property instanceof Number number) {
         return number.intValue();
      } else if (property instanceof String string) {
         return Integer.parseInt(string);
      } else {
         return connection.getProtocolManager().getLinkQuiesceTimeout();
      }
   }
}
