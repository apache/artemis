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

package org.apache.activemq.artemis.protocol.amqp.connect.federation;

import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.ADDRESS_RECEIVER_IDLE_TIMEOUT;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.IGNORE_ADDRESS_BINDING_FILTERS;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.IGNORE_QUEUE_CONSUMER_FILTERS;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.IGNORE_QUEUE_CONSUMER_PRIORITIES;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.LARGE_MESSAGE_THRESHOLD;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.LINK_ATTACH_TIMEOUT;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.PULL_RECEIVER_BATCH_SIZE;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.QUEUE_RECEIVER_IDLE_TIMEOUT;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.RECEIVER_CREDITS;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.RECEIVER_CREDITS_LOW;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.RECEIVER_DRAIN_ON_TRANSIENT_DELIVERY_ERRORS;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.RECEIVER_LINK_QUIESCE_TIMEOUT;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.RECEIVER_QUIESCE_TIMEOUT;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.USE_MODIFIED_FOR_TRANSIENT_DELIVERY_ERRORS;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.activemq.artemis.protocol.amqp.proton.AMQPConnectionContext;
import org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport;

/**
 * A configuration class that contains API for getting federation specific configuration either from a {@link Map} of
 * configuration elements or from the connection associated with the federation instance, or possibly from a set default
 * value.
 */
public final class AMQPFederationConfiguration {

   /**
    * Default timeout value (in seconds) used to control when a link attach is considered to have failed due to not
    * responding to an attach request.
    */
   public static final int DEFAULT_LINK_ATTACH_TIMEOUT = 30;

   /**
    * Default credits granted to a receiver that is in pull mode.
    */
   public static final int DEFAULT_PULL_CREDIT_BATCH_SIZE = 100;

   /**
    * Default value for the core message tunneling feature that indicates if core protocol messages should be streamed
    * as binary blobs as the payload of an custom AMQP message which avoids any conversions of the messages to / from
    * AMQP.
    */
   public static final boolean DEFAULT_CORE_MESSAGE_TUNNELING_ENABLED = true;

   /**
    * Default value for the filtering applied to federation address consumers that controls if the filter specified by an
    * address binding is used or if the filters are ignored and a single conduit type subscription is used for all messages
    * sent to the remote address.
    */
   public static final boolean DEFAULT_IGNORE_ADDRESS_BINDING_FILTERS = true;

   /**
    * Default value for the filtering applied to federation queue consumers that controls if the filter specified by a
    * consumer subscription is used or if the higher level queue filter only is applied when creating a federation queue
    * consumer.
    */
   public static final boolean DEFAULT_IGNORE_QUEUE_CONSUMER_FILTERS = false;

   /**
    * Default value for the priority applied to federation queue consumers that controls if the priority specified by a
    * consumer subscription is used or if the policy priority offset value is simply applied to the default consumer
    * priority value.
    */
   public static final boolean DEFAULT_IGNORE_QUEUE_CONSUMER_PRIORITIES = true;

   /**
    * Default timeout (milliseconds) applied to federation receivers that are being stopped due to removal of local
    * demand and need to drain link credit and process any in-flight deliveries before closure. If the timeout elapses
    * before the link has quiesced the link is forcibly closed.
    */
   public static final int DEFAULT_RECEIVER_QUIESCE_TIMEOUT = 60_000;

   /**
    * Default timeout (milliseconds) applied to federation address receivers that have been stopped due to lack of local
    * demand. The close delay prevent a link from detaching in cases where demand drops and returns in quick succession
    * allowing for faster recovery. The idle timeout kicks in once the link has completed its drain of outstanding
    * credit.
    */
   public static final int DEFAULT_ADDRESS_RECEIVER_IDLE_TIMEOUT = 5_000;

   /**
    * Default timeout (milliseconds) applied to federation queue receivers that have been stopped due to lack of local
    * demand. The close delay prevent a link from detaching in cases where demand drops and returns in quick succession
    * allowing for faster recovery. The idle timeout kicks in once the link has completed its drain of outstanding
    * credit.
    */
   public static final int DEFAULT_QUEUE_RECEIVER_IDLE_TIMEOUT = 60_000;

   /**
    * Default value for how a federation receiver should respond to delivery errors indicating that an address is full
    * and cannot accept messages at this time. By default we want to send Modified outcomes with the delivery failed
    * value set to true such that the remote will deliver the message again after incrementing the delivery count of
    * the message. This is an opinionated choice and the value set on the connector URI is not referenced by federation
    * as we want to maintain this behavior unless specifically set on federation configuration explicitly.
    */
   public static final boolean DEFAULT_USE_MODIFIED_FOR_TRANSIENT_DELIVERY_ERRORS = true;

   private final Map<String, Object> properties;
   private final AMQPConnectionContext connection;

   public AMQPFederationConfiguration(AMQPConnectionContext connection, Map<String, Object> properties) {
      Objects.requireNonNull(connection, "Connection provided cannot be null");

      this.connection = connection;

      if (properties != null && !properties.isEmpty()) {
         this.properties = new HashMap<>(properties);
      } else {
         this.properties = Collections.emptyMap();
      }
   }

   /**
    * {@return the credit batch size offered to a {@link org.apache.qpid.proton.engine.Receiver} link}
    */
   public int getReceiverCredits() {
      final Object property = properties.get(RECEIVER_CREDITS);
      if (property instanceof Number number) {
         return number.intValue();
      } else if (property instanceof String string) {
         return Integer.parseInt(string);
      } else {
         return connection.getAmqpCredits();
      }
   }

   /**
    * {@return the number of remaining credits on a {@link org.apache.qpid.proton.engine.Receiver} before the batch is
    * replenished}
    */
   public int getReceiverCreditsLow() {
      final Object property = properties.get(RECEIVER_CREDITS_LOW);
      if (property instanceof Number number) {
         return number.intValue();
      } else if (property instanceof String string) {
         return Integer.parseInt(string);
      } else {
         return connection.getAmqpLowCredits();
      }
   }

   /**
    * {@return the credit batch size offered to a {@link org.apache.qpid.proton.engine.Receiver} link that is in pull
    * mode}
    */
   public int getPullReceiverBatchSize() {
      final Object property = properties.get(PULL_RECEIVER_BATCH_SIZE);
      if (property instanceof Number number) {
         return number.intValue();
      } else if (property instanceof String string) {
         return Integer.parseInt(string);
      } else {
         return DEFAULT_PULL_CREDIT_BATCH_SIZE;
      }
   }

   /**
    * {@return the size in bytes of an incoming message after which the {@link org.apache.qpid.proton.engine.Receiver}
    * treats it as large}
    */
   public int getLargeMessageThreshold() {
      final Object property = properties.get(LARGE_MESSAGE_THRESHOLD);
      if (property instanceof Number number) {
         return number.intValue();
      } else if (property instanceof String string) {
         return Integer.parseInt(string);
      } else {
         return connection.getProtocolManager().getAmqpMinLargeMessageSize();
      }
   }

   /**
    * {@return the timeout value to use when waiting for a corresponding link attach from the remote}
    */
   public int getLinkAttachTimeout() {
      final Object property = properties.get(LINK_ATTACH_TIMEOUT);
      if (property instanceof Number number) {
         return number.intValue();
      } else if (property instanceof String string) {
         return Integer.parseInt(string);
      } else {
         return DEFAULT_LINK_ATTACH_TIMEOUT;
      }
   }

   /**
    * {@return {@code true} if the federation is configured to tunnel core messages as AMQP custom messages}
    */
   public boolean isCoreMessageTunnelingEnabled() {
      final Object property = properties.get(AmqpSupport.TUNNEL_CORE_MESSAGES);
      if (property instanceof Boolean booleanValue) {
         return booleanValue;
      } else if (property instanceof String string) {
         return Boolean.parseBoolean(string);
      } else {
         return DEFAULT_CORE_MESSAGE_TUNNELING_ENABLED;
      }
   }

   /**
    * {@return {@code true} if federation is configured to ignore filters on individual address bindings}
    */
   public boolean isIgnoreAddressBindingFilters() {
      final Object property = properties.get(IGNORE_ADDRESS_BINDING_FILTERS);
      if (property instanceof Boolean booleanValue) {
         return booleanValue;
      } else if (property instanceof String string) {
         return Boolean.parseBoolean(string);
      } else {
         return DEFAULT_IGNORE_ADDRESS_BINDING_FILTERS;
      }
   }

   /**
    * {@return {@code true} if federation is configured to ignore filters on individual queue consumers}
    */
   public boolean isIgnoreSubscriptionFilters() {
      final Object property = properties.get(IGNORE_QUEUE_CONSUMER_FILTERS);
      if (property instanceof Boolean booleanValue) {
         return booleanValue;
      } else if (property instanceof String string) {
         return Boolean.parseBoolean(string);
      } else {
         return DEFAULT_IGNORE_QUEUE_CONSUMER_FILTERS;
      }
   }

   /**
    * {@return {@code true} if federation is configured to ignore priorities on individual queue consumers}
    */
   public boolean isIgnoreSubscriptionPriorities() {
      final Object property = properties.get(IGNORE_QUEUE_CONSUMER_PRIORITIES);
      if (property instanceof Boolean booleanValue) {
         return booleanValue;
      } else if (property instanceof String string) {
         return Boolean.parseBoolean(string);
      } else {
         return DEFAULT_IGNORE_QUEUE_CONSUMER_PRIORITIES;
      }
   }

   /**
    * {@return the receive quiesce timeout when shutting down a {@link org.apache.qpid.proton.engine.Receiver} when
    * local demand is removed}
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
    * {@return the receive idle timeout when shutting down a address {@link org.apache.qpid.proton.engine.Receiver} when
    * local demand is removed}
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
    * {@return the receive idle timeout when shutting down a queue {@link org.apache.qpid.proton.engine.Receiver} when
    * local demand is removed}
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
    * {@return the federation receiver link quiesce timeout configuration}
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

   /**
    * Enumerate the configuration options in this configuration object and return a {@link Map} that contains the values
    * which can be sent to a remote peer
    *
    * @return a Map that contains the values of each configuration option
    */
   public Map<String, Object> toConfigurationMap() {
      final Map<String, Object> configMap = new HashMap<>();

      configMap.put(RECEIVER_CREDITS, getReceiverCredits());
      configMap.put(RECEIVER_CREDITS_LOW, getReceiverCreditsLow());
      configMap.put(RECEIVER_QUIESCE_TIMEOUT, getReceiverQuiesceTimeout());
      configMap.put(ADDRESS_RECEIVER_IDLE_TIMEOUT, getAddressReceiverIdleTimeout());
      configMap.put(QUEUE_RECEIVER_IDLE_TIMEOUT, getQueueReceiverIdleTimeout());
      configMap.put(PULL_RECEIVER_BATCH_SIZE, getPullReceiverBatchSize());
      configMap.put(LARGE_MESSAGE_THRESHOLD, getLargeMessageThreshold());
      configMap.put(LINK_ATTACH_TIMEOUT, getLinkAttachTimeout());
      configMap.put(IGNORE_ADDRESS_BINDING_FILTERS, isIgnoreAddressBindingFilters());
      configMap.put(IGNORE_QUEUE_CONSUMER_FILTERS, isIgnoreSubscriptionFilters());
      configMap.put(IGNORE_QUEUE_CONSUMER_PRIORITIES, isIgnoreSubscriptionPriorities());
      configMap.put(USE_MODIFIED_FOR_TRANSIENT_DELIVERY_ERRORS, isUseModifiedForTransientDeliveryErrors());
      configMap.put(RECEIVER_LINK_QUIESCE_TIMEOUT, getLinkQuiesceTimeout());
      configMap.put(RECEIVER_DRAIN_ON_TRANSIENT_DELIVERY_ERRORS, isDrainOnTransientDeliveryErrors());
      configMap.put(AmqpSupport.TUNNEL_CORE_MESSAGES, isCoreMessageTunnelingEnabled());

      return configMap;
   }
}
