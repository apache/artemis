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

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerAddressPlugin;
import org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeSenderInfo.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AMQP Bridge policy manager that tracks local addresses that match the policy configurations
 * and creates senders to the remote peer for that address until such time as the address is
 * removed locally.
 */
public class AMQPBridgeToAddressPolicyManager extends AMQPBridgeToPolicyManager implements ActiveMQServerAddressPlugin {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final AMQPBridgeAddressPolicy policy;
   private final Map<String, AMQPBridgeAddressSenderManager> addressTracking = new HashMap<>();

   public AMQPBridgeToAddressPolicyManager(AMQPBridgeManager bridge, AMQPBridgeMetrics metrics, AMQPBridgeAddressPolicy policy) {
      super(bridge, metrics, policy.getPolicyName(), AMQPBridgeType.BRIDGE_TO_ADDRESS);

      Objects.requireNonNull(policy, "The Address match policy cannot be null");

      this.policy = policy;
   }

   /**
    * {@return the policy that defines the bridged address this policy manager monitors}
    */
   @Override
   public AMQPBridgeAddressPolicy getPolicy() {
      return policy;
   }

   @Override
   protected void scanManagedResources() {
      server.getPostOffice()
            .getAddresses()
            .stream()
            .map(address -> server.getAddressInfo(address))
            .forEach(addressInfo -> afterAddAddress(addressInfo, false));
   }

   @Override
   protected void safeCleanupManagerResources() {
      try {
         addressTracking.forEach((k, v) -> {
            v.shutdownNow();
         });
      } finally {
         addressTracking.clear();
      }
   }

   @Override
   public synchronized void afterAddAddress(AddressInfo addressInfo, boolean reload) {
      if (isActive() && policy.test(addressInfo) && !addressTracking.containsKey(addressInfo.getName().toString())) {
         try {
            final AMQPBridgeAddressSenderManager manager = new AMQPBridgeAddressSenderManager(this, configuration, addressInfo);
            addressTracking.put(manager.getAddress(), manager);
            manager.start();
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.bridgeBindingsLookupError(addressInfo.getName(), e);
         }
      }
   }

   @Override
   public synchronized void afterRemoveAddress(SimpleString address, AddressInfo addressInfo) throws ActiveMQException {
      if (isActive()) {
         final AMQPBridgeAddressSenderManager manager = addressTracking.remove(address.toString());

         if (manager != null) {
            logger.trace("Clearing sender tracking for removed bridged Address {}", addressInfo.getName());
            manager.shutdownNow();
         }
      }
   }

   private AMQPBridgeSender createBridgeSender(AMQPBridgeSenderInfo senderInfo) {
      Objects.requireNonNull(senderInfo, "AMQP Bridge Address sender information object was null");

      if (logger.isTraceEnabled()) {
         logger.trace("AMQP Bridge {} creating address sender: {} for policy: {}", bridge.getName(), senderInfo, policy.getPolicyName());
      }

      // Don't initiate anything yet as the caller might need to register error handlers etc
      // before the attach is sent otherwise they could miss the failure case.
      return new AMQPBridgeToAddressSender(this, configuration, session, senderInfo, metrics.newSenderMetrics());
   }

   private String generateTempQueueName(String remoteAddress) {
      return "amqp-bridge-" + bridge.getName() +
             "-policy-" + policyName +
             "-address-sender-to-" + remoteAddress +
             "-" + UUID.randomUUID().toString();
   }

   private String generateDurableSubscriptionQueueName(String remoteAddress) {
      return "amqp-bridge-" + bridge.getName() +
             "-policy-" + policyName +
             "-address-sender-to-" + remoteAddress +
             "-" + server.getNodeID();
   }

   private AMQPBridgeSenderInfo createSenderInfo(AddressInfo address) {
      final String addressName = address.getName().toString();
      final StringBuilder remoteAddressBuilder = new StringBuilder();

      if (policy.getRemoteAddressPrefix() != null) {
         remoteAddressBuilder.append(policy.getRemoteAddressPrefix());
      }

      if (policy.getRemoteAddress() != null && !policy.getRemoteAddress().isBlank()) {
         remoteAddressBuilder.append(policy.getRemoteAddress());
      } else {
         remoteAddressBuilder.append(addressName);
      }

      if (policy.getRemoteAddressSuffix() != null) {
         remoteAddressBuilder.append(policy.getRemoteAddressSuffix());
      }

      final String remoteAddress = remoteAddressBuilder.toString();
      final String addressBindingName = policy.isUseDurableSubscriptions() ?
         generateDurableSubscriptionQueueName(remoteAddress) : generateTempQueueName(remoteAddress);

      return new AMQPBridgeSenderInfo(Role.ADDRESS_SENDER,
                                      addressName,
                                      addressBindingName,
                                      address.getRoutingType(),
                                      remoteAddress);
   }

   private static class AMQPBridgeAddressSenderManager extends AMQPBridgeSenderManager {

      private final AMQPBridgeToAddressPolicyManager manager;
      private final AddressInfo addressInfo;

      AMQPBridgeAddressSenderManager(AMQPBridgeToAddressPolicyManager manager, AMQPBridgeSenderConfiguration configuration, AddressInfo addressInfo) {
         super(manager, configuration);

         this.manager = manager;
         this.addressInfo = addressInfo;
      }

      /**
       * @return the address that this entry is acting to bridge.
       */
      public String getAddress() {
         return addressInfo.getName().toString();
      }

      @Override
      protected AMQPBridgeSender createBridgeSender() {
         return manager.createBridgeSender(manager.createSenderInfo(addressInfo));
      }
   }
}
