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
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.QueueBinding;
import org.apache.activemq.artemis.core.postoffice.impl.DivertBinding;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerAddressPlugin;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeReceiverInfo.ReceiverRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AMQP Bridge policy manager that tracks local addresses that match the policy configurations
 * for local demand and creates receivers to the remote peer.
 */
public final class AMQPBridgeFromAddressPolicyManager extends AMQPBridgeFromPolicyManager implements ActiveMQServerAddressPlugin {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final AMQPBridgeAddressPolicy policy;
   private final Map<String, AMQPBridgeAddressReceiverManager> bridgeReceivers = new HashMap<>();
   private final Map<DivertBinding, Set<QueueBinding>> divertsTracking = new HashMap<>();

   public AMQPBridgeFromAddressPolicyManager(AMQPBridgeManager bridge, AMQPBridgeMetrics metrics, AMQPBridgeAddressPolicy addressPolicy) {
      super(bridge, metrics, addressPolicy.getPolicyName(), AMQPBridgeType.BRIDGE_FROM_ADDRESS);

      Objects.requireNonNull(addressPolicy, "The Address match policy cannot be null");

      this.policy = addressPolicy;
   }

   @Override
   public AMQPBridgeAddressPolicy getPolicy() {
      return policy;
   }

   @Override
   protected void scanManagedResources() {
      if (configuration.isReceiverDemandTrackingDisabled()) {
         scanAllAddresses();
      } else {
         scanAllBindings();
      }
   }

   @Override
   protected void safeCleanupManagerResources(boolean force) {
      try {
         bridgeReceivers.values().forEach((manager) -> {
            if (manager != null) {
               if (isConnected() && !force) {
                  manager.shutdown();
               } else {
                  manager.shutdownNow();
               }
            }
         });
      } finally {
         bridgeReceivers.clear();
         divertsTracking.clear();
      }
   }

   @Override
   public synchronized void afterRemoveAddress(SimpleString address, AddressInfo addressInfo) throws ActiveMQException {
      if (isActive()) {
         final AMQPBridgeAddressReceiverManager manager = bridgeReceivers.remove(address.toString());

         if (manager != null) {
            // Demand is gone because the Address is gone and any in-flight messages can be
            // allowed to be released back to the remote as they will not be processed.
            // We removed the receiver information from demand tracking to prevent build up
            // of data for entries that may never return and to prevent interference from the
            // next set of events which will be the close of all local receivers for this now
            // removed Address.
            manager.shutdownNow();
         }
      }
   }

   @Override
   public synchronized void afterRemoveBinding(Binding binding, Transaction tx, boolean deleteData) throws ActiveMQException {
      if (isActive() && !configuration.isReceiverDemandTrackingDisabled()) {
         if (binding instanceof QueueBinding) {
            final AMQPBridgeAddressReceiverManager manager = bridgeReceivers.get(binding.getAddress().toString());

            if (manager != null) {
               // This is QueueBinding that was mapped to a bridged address so we can directly remove
               // demand from the bridge receiver and close it if demand is now gone.
               tryRemoveDemandOnAddress(manager, binding);
            } else if (policy.isIncludeDivertBindings()) {
               // See if there is any matching diverts that are forwarding to an address where this QueueBinding
               // is bound and remove the mapping for any matches, diverts can have a composite set of address
               // forwards so each divert must be checked in turn to see if it contains the address the removed
               // binding was bound to.
               divertsTracking.entrySet().forEach(divertEntry -> {
                  final String sourceAddress = divertEntry.getKey().getAddress().toString();
                  final SimpleString forwardAddress = divertEntry.getKey().getDivert().getForwardAddress();

                  if (isAddressInDivertForwards(binding.getAddress(), forwardAddress)) {
                     // Try and remove the queue binding from the set of registered bindings
                     // for the divert and if that removes all mapped bindings then we can
                     // remove the divert from the bridged address entry and check if that
                     // removed all local demand which means we can close the receiver.
                     divertEntry.getValue().remove(binding);

                     if (divertEntry.getValue().isEmpty()) {
                        tryRemoveDemandOnAddress(bridgeReceivers.get(sourceAddress), divertEntry.getKey());
                     }
                  }
               });
            }
         } else if (policy.isIncludeDivertBindings() && binding instanceof DivertBinding divert) {

            if (divertsTracking.remove(divert) != null) {
               // The divert binding is treated as one unit of demand on a bridged address and when
               // the divert is removed that unit of demand is removed regardless of existing bindings
               // still remaining on the divert forwards. If the divert demand was the only thing
               // keeping the bridge address receiver open this will result in it bring closed.
               try {
                  tryRemoveDemandOnAddress(bridgeReceivers.get(divert.getAddress().toString()), divert);
               } catch (Exception e) {
                  logger.warn("Error looking up binding for divert forward address {}", divert.getDivert().getForwardAddress(), e);
               }
            }
         }
      }
   }

   private void tryRemoveDemandOnAddress(AMQPBridgeAddressReceiverManager manager, Binding binding) {
      if (manager != null) {
         logger.trace("Reducing demand on bridged address {}", manager.getAddress());
         manager.removeDemand(binding);
      }
   }

   /**
    * When address demand tracking is disabled we just scan for any address the matches the policy and
    * create a receiver for that address, we don't monitor any other demand as we just want to receive
    * for as long as the lifetime of the address.
    */
   private void scanAllAddresses() {
      server.getPostOffice()
            .getAddresses()
            .stream()
            .map(address -> server.getAddressInfo(address))
            .filter(addressInfo -> testIfAddressMatchesPolicy(addressInfo))
            .forEach(addressInfo -> createOrUpdateAddressReceiverForUnboundDemand(addressInfo));
   }

   /**
    * Scans all bindings and push them through the normal bindings checks that
    * would be done on an add. We filter here based on whether diverts are enabled
    * just to reduce the result set but the check call should also filter as
    * during normal operations divert bindings could be added.
    */
   private void scanAllBindings() {
      server.getPostOffice()
            .getAllBindings()
            .filter(binding -> binding instanceof QueueBinding || (policy.isIncludeDivertBindings() && binding instanceof DivertBinding))
            .forEach(binding -> afterAddBinding(binding));
   }

   @Override
   public synchronized void afterAddAddress(AddressInfo addressInfo, boolean reload) {
      if (isActive() && policy.test(addressInfo)) {
         // If demand tracking is disabled we create a receiver regardless of other demand
         if (configuration.isReceiverDemandTrackingDisabled()) {
            createOrUpdateAddressReceiverForUnboundDemand(addressInfo);
         } else if (policy.isIncludeDivertBindings()) {
            try {
               // A Divert can exist in configuration prior to the address having been auto created etc so
               // upon address add this check needs to be run to capture addresses that now match the divert.
               server.getPostOffice()
                     .getDirectBindings(addressInfo.getName())
                     .stream()
                     .filter(binding -> binding instanceof DivertBinding)
                     .forEach(this::checkBindingForMatch);
            } catch (Exception e) {
               ActiveMQServerLogger.LOGGER.bridgeBindingsLookupError(addressInfo.getName(), e);
            }
         }
      }
   }

   @Override
   public synchronized void afterAddBinding(Binding binding) {
      if (isActive() && !configuration.isReceiverDemandTrackingDisabled()) {
         checkBindingForMatch(binding);
      }
   }

   /**
    * Called under lock this method should check if the given {@link Binding} matches the
    * configured address bridging policy and bridge the address if so. The incoming
    * {@link Binding} can be either a {@link QueueBinding} or a {@link DivertBinding} so
    * the code should check both.
    *
    * @param binding
    *       The binding that should be checked against the bridge from address policy,
    */
   private void checkBindingForMatch(Binding binding) {
      if (binding instanceof QueueBinding queueBinding) {
         final AddressInfo addressInfo = server.getPostOffice().getAddressInfo(binding.getAddress());

         if (testIfAddressMatchesPolicy(addressInfo)) {
            createOrUpdateAddressReceiverForBinding(addressInfo, queueBinding);
         } else {
            reactIfQueueBindingMatchesAnyDivertTarget(queueBinding);
         }
      } else if (binding instanceof DivertBinding divertBinding) {
         reactIfAnyQueueBindingMatchesDivertTarget(divertBinding);
      }
   }

   private void reactIfAnyQueueBindingMatchesDivertTarget(DivertBinding divertBinding) {
      if (!policy.isIncludeDivertBindings()) {
         return;
      }

      final AddressInfo addressInfo = server.getPostOffice().getAddressInfo(divertBinding.getAddress());

      if (!testIfAddressMatchesPolicy(addressInfo)) {
         return;
      }

      // We only need to check if we've never seen the divert before, afterwards we will
      // be checking it any time a new QueueBinding is added instead.
      if (divertsTracking.get(divertBinding) == null) {
         final Set<QueueBinding> matchingQueues = new HashSet<>();
         divertsTracking.put(divertBinding, matchingQueues);

         // We must account for the composite divert case by splitting the address and
         // getting the bindings on each one.
         final SimpleString forwardAddress = divertBinding.getDivert().getForwardAddress();
         final SimpleString[] forwardAddresses = forwardAddress.split(',');

         try {
            for (SimpleString forward : forwardAddresses) {
               server.getPostOffice().getBindingsForAddress(forward).getBindings()
                     .stream()
                     .filter(b -> b instanceof QueueBinding)
                     .map(b -> (QueueBinding) b)
                     .forEach(queueBinding -> {
                        matchingQueues.add(queueBinding);
                        createOrUpdateAddressReceiverForBinding(addressInfo, divertBinding);
                     });
            }
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.bridgeBindingsLookupError(forwardAddress, e);
         }
      }
   }

   private void reactIfQueueBindingMatchesAnyDivertTarget(QueueBinding queueBinding) {
      if (!policy.isIncludeDivertBindings()) {
         return;
      }

      final SimpleString queueAddress = queueBinding.getAddress();

      divertsTracking.entrySet().forEach((e) -> {
         final SimpleString forwardAddress = e.getKey().getDivert().getForwardAddress();
         final DivertBinding divertBinding = e.getKey();

         // Check matched diverts to see if the QueueBinding address matches the address or
         // addresses (composite diverts) of the Divert and if so then we can check if we need
         // to create demand on the source address on the remote if we haven't done so already.

         if (!e.getValue().contains(queueBinding) && isAddressInDivertForwards(queueAddress, forwardAddress)) {
            // Each divert that forwards to the address the queue is bound to we add demand
            // in the diverts tracker.
            e.getValue().add(queueBinding);

            final AddressInfo addressInfo = server.getPostOffice().getAddressInfo(divertBinding.getAddress());

            createOrUpdateAddressReceiverForBinding(addressInfo, divertBinding);
         }
      });
   }

   private static boolean isAddressInDivertForwards(final SimpleString targetAddress, final SimpleString forwardAddress) {
      final SimpleString[] forwardAddresses = forwardAddress.split(',');

      for (SimpleString forward : forwardAddresses) {
         if (targetAddress.equals(forward)) {
            return true;
         }
      }

      return false;
   }

   private void createOrUpdateAddressReceiverForUnboundDemand(AddressInfo addressInfo) {
      logger.trace("AMQP Bridge Address Policy matched on address: {} when demand tracking disabled", addressInfo);
      createOrUpdateAddressReceiver(addressInfo, null, true);
   }

   private void createOrUpdateAddressReceiverForBinding(AddressInfo addressInfo, Binding binding) {
      logger.trace("AMQP Bridge Address Policy matched on for demand on address: {} : binding: {}", addressInfo, binding);
      createOrUpdateAddressReceiver(addressInfo, binding, false);
   }

   private void createOrUpdateAddressReceiver(AddressInfo addressInfo, Binding binding, boolean forceDemand) {
      final String addressName = addressInfo.getName().toString();
      final AMQPBridgeAddressReceiverManager manager;

      // Check for existing receiver add demand from a additional local consumer to ensure
      // the remote receiver remains active until all local demand is withdrawn.
      if (bridgeReceivers.containsKey(addressName)) {
         manager = bridgeReceivers.get(addressName);
      } else {
         manager = new AMQPBridgeAddressReceiverManager(this, configuration, addressInfo);
         bridgeReceivers.put(addressName, manager);
      }

      if (forceDemand) {
         manager.forceDemand();
      } else {
         manager.addDemand(binding);
      }
   }

   private AMQPBridgeReceiverInfo createReceiverInfo(AddressInfo address) {
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

      return new AMQPBridgeReceiverInfo(ReceiverRole.ADDRESS_RECEIVER,
                                        addressName,
                                        null,
                                        address.getRoutingType(),
                                        remoteAddressBuilder.toString(),
                                        policy.getFilter(),
                                        configuration.isReceiverPriorityDisabled() ? null : policy.getPriority());
   }

   private AMQPBridgeReceiver createBridgeReceiver(AMQPBridgeReceiverInfo receiverInfo) {
      Objects.requireNonNull(receiverInfo, "AMQP Bridge Address receiver information object was null");

      if (logger.isTraceEnabled()) {
         logger.trace("AMQP Bridge {} creating address receiver: {} for policy: {}", bridge.getName(), receiverInfo, policy.getPolicyName());
      }

      // Don't initiate anything yet as the caller might need to register error handlers etc
      // before the attach is sent otherwise they could miss the failure case.
      return new AMQPBridgeFromAddressReceiver(this, configuration, session, receiverInfo, policy, metrics.newReceiverMetrics());
   }

   private boolean testIfAddressMatchesPolicy(AddressInfo addressInfo) {
      if (!policy.test(addressInfo)) {
         return false;
      }

      // Address receivers can't pull as we have no real metric to indicate when / how much
      // we should pull so instead we refuse to match if credit set to zero.
      if (configuration.getReceiverCredits() <= 0) {
         logger.debug("AMQP Bridge address policy rejecting match on {} because credit is set to zero:", addressInfo.getName());
         return false;
      } else {
         return true;
      }
   }

   private static class AMQPBridgeAddressReceiverManager extends AMQPBridgeReceiverManager<Binding> {

      private final AMQPBridgeFromAddressPolicyManager manager;
      private final AddressInfo addressInfo;

      AMQPBridgeAddressReceiverManager(AMQPBridgeFromAddressPolicyManager manager, AMQPBridgeReceiverConfiguration configuration, AddressInfo addressInfo) {
         super(manager, configuration);

         this.manager = manager;
         this.addressInfo = addressInfo;
      }

      /**
       * @return the address information that this entry is acting to bridge.
       */
      public AddressInfo getAddressInfo() {
         return addressInfo;
      }

      /**
       * @return the address that this entry is acting to bridge.
       */
      public String getAddress() {
         return getAddressInfo().getName().toString();
      }

      @Override
      protected AMQPBridgeReceiver createBridgeReceiver() {
         return manager.createBridgeReceiver(manager.createReceiverInfo(addressInfo));
      }

      @Override
      protected void whenDemandTrackingEntryAdded(Binding entry) {
         // Nothing to do for this implementation
      }

      @Override
      protected void whenDemandTrackingEntryRemoved(Binding entry) {
         // Nothing to do for this implementation
      }
   }
}
