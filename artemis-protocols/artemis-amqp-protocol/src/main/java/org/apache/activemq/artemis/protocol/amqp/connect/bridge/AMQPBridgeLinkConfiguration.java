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

import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.LINK_ATTACH_TIMEOUT;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.LINK_RECOVERY_DELAY;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.LINK_RECOVERY_INITIAL_DELAY;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.MAX_LINK_RECOVERY_ATTEMPTS;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.PRESETTLE_SEND_MODE;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport;

/**
 * Base bridge link configuration that carries configuration common to both sender or receiver
 * bridge links created by the bridge managers.
 */
public abstract class AMQPBridgeLinkConfiguration {

   protected final Map<String, Object> properties;
   protected final AMQPBridgeConfiguration configuration;

   @SuppressWarnings("unchecked")
   public AMQPBridgeLinkConfiguration(AMQPBridgeConfiguration configuration, Map<String, ?> properties) {
      Objects.requireNonNull(configuration, "Bridge configuration cannot be null");

      this.configuration = configuration;

      if (properties == null || properties.isEmpty()) {
         this.properties = Collections.emptyMap();
      } else {
         this.properties = (Map<String, Object>) Collections.unmodifiableMap(new HashMap<>(properties));
      }
   }

   /**
    * {@return the timeout value to use when waiting for a corresponding link attach from the remote}
    */
   public final int getLinkAttachTimeout() {
      final Object property = properties.get(LINK_ATTACH_TIMEOUT);
      if (property instanceof Number) {
         return ((Number) property).intValue();
      } else if (property instanceof String) {
         return Integer.parseInt((String) property);
      } else {
         return configuration.getLinkAttachTimeout();
      }
   }

   /**
    * {@return <code>true</code> if bridge is configured to create links with the sender settle mode set to settled}
    */
   public final boolean isUsingPresettledSenders() {
      final Object property = properties.get(PRESETTLE_SEND_MODE);
      if (property instanceof Boolean) {
         return (Boolean) property;
      } else if (property instanceof String) {
         return Boolean.parseBoolean((String) property);
      } else {
         return configuration.isUsingPresettledSenders();
      }
   }

   /**
    * {@return <code>true</code> if link recovery support is enabled, <code>false</code> otherwise}
    */
   public final boolean isLinkRecoveryEnabled() {
      return getMaxLinkRecoveryAttempts() != 0;
   }

   /**
    * {@return the maximum number of link recovery attempts, or zero if no attempts allowed}
    */
   public final int getMaxLinkRecoveryAttempts() {
      final Object property = properties.get(MAX_LINK_RECOVERY_ATTEMPTS);
      if (property instanceof Number) {
         return ((Number) property).intValue();
      } else if (property instanceof String) {
         return Integer.parseInt((String) property);
      } else {
         return configuration.getMaxLinkRecoveryAttempts();
      }
   }

   /**
    * {@return the initial delay before a link recovery attempt is made}
    */
   public final long getLinkRecoveryInitialDelay() {
      final Object property = properties.get(LINK_RECOVERY_INITIAL_DELAY);
      if (property instanceof Number) {
         return ((Number) property).intValue();
      } else if (property instanceof String) {
         return Integer.parseInt((String) property);
      } else {
         return configuration.getLinkRecoveryInitialDelay();
      }
   }

   /**
    * {@return the delay that will be used between successive link recovery attempts}
    */
   public final long getLinkRecoveryDelay() {
      final Object property = properties.get(LINK_RECOVERY_DELAY);
      if (property instanceof Number) {
         return ((Number) property).intValue();
      } else if (property instanceof String) {
         return Integer.parseInt((String) property);
      } else {
         return configuration.getLinkRecoveryDelay();
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
         return configuration.isCoreMessageTunnelingEnabled();
      }
   }
}
