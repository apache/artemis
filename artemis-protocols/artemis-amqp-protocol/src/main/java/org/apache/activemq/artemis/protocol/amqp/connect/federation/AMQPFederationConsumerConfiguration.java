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

import org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport;

/**
 * Configuration options applied to a consumer created from federation policies for address or queue federation. The
 * options first check the policy properties for matching configuration settings before looking at the federation's own
 * configuration for the options managed here.
 */
public final class AMQPFederationConsumerConfiguration {

   private final Map<String, Object> properties;
   private final AMQPFederationConfiguration configuration;

   @SuppressWarnings("unchecked")
   public AMQPFederationConsumerConfiguration(AMQPFederationConfiguration configuration, Map<String, ?> properties) {
      Objects.requireNonNull(configuration, "Federation configuration cannot be null");

      this.configuration = configuration;

      if (properties == null || properties.isEmpty()) {
         this.properties = Collections.emptyMap();
      } else {
         this.properties = (Map<String, Object>) Collections.unmodifiableMap(new HashMap<>(properties));
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
         return configuration.getReceiverCredits();
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
         return configuration.getReceiverCreditsLow();
      }
   }

   /**
    * {@return the receiver drain timeout for a stopping federation consumer before it is closed}
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
    * {@return the idle timeout for a drained federation address consumer before it is closed}
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
    * {@return the idle timeout for a drained federation queue consumer before it is closed}
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
         return configuration.getPullReceiverBatchSize();
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
         return configuration.getLargeMessageThreshold();
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
         return configuration.getLinkAttachTimeout();
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
         return configuration.isCoreMessageTunnelingEnabled();
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
         return configuration.isIgnoreAddressBindingFilters();
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
         return configuration.isIgnoreSubscriptionFilters();
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
         return configuration.isIgnoreSubscriptionPriorities();
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
