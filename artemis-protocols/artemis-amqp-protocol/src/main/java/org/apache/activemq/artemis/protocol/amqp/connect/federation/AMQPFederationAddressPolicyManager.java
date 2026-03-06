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

import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationPolicySupport.generateAddressFilter;

import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.QueueBinding;
import org.apache.activemq.artemis.core.postoffice.impl.DivertBinding;
import org.apache.activemq.artemis.core.security.CheckType;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerAddressPlugin;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.protocol.amqp.federation.FederationConsumerInfo;
import org.apache.activemq.artemis.protocol.amqp.federation.FederationReceiveFromAddressPolicy;
import org.apache.activemq.artemis.protocol.amqp.logger.ActiveMQAMQPProtocolLogger;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPSessionContext;
import org.apache.activemq.artemis.utils.CompositeAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The AMQP Federation implementation of an federation address policy manager.
 */
public final class AMQPFederationAddressPolicyManager extends AMQPFederationLocalPolicyManager implements ActiveMQServerAddressPlugin {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   // Matches on federation addresses bindings only and will be used on connect to create a pattern that
   // matches against the Node ID of the remote server obtained from the remote container Id.
   // The pattern matches only if the format is right and there is at least one character after each
   // matched section, where the filter Id portion will fall into the address matching portion if present.
   private static final String OPPOSING_PEER_MATCHING_TEMPLATE =
      "^federation\\..+?\\.policy\\..+?\\.address\\..+?\\.node\\.%s$";

   protected final String baseConsumerFilter;
   protected final FederationReceiveFromAddressPolicy policy;
   protected final Map<String, AMQPFederationAddressConsumerRegistry> addressTracking = new HashMap<>();
   protected final Map<DivertBinding, Set<QueueBinding>> divertsTracking = new HashMap<>();

   private Pattern opposingPeerBindingPattern; // Initialized on each connect to the remote.

   public AMQPFederationAddressPolicyManager(AMQPFederation federation, AMQPFederationMetrics metrics, FederationReceiveFromAddressPolicy addressPolicy) throws ActiveMQException {
      super(federation, metrics, addressPolicy);

      Objects.requireNonNull(addressPolicy, "The Address match policy cannot be null");

      this.policy = addressPolicy;
      this.baseConsumerFilter = generateAddressFilter(policy.getMaxHops());
   }

   /**
    * @return the receive from address policy that backs the address policy manager
    */
   @Override
   public FederationReceiveFromAddressPolicy getPolicy() {
      return policy;
   }

   @Override
   protected void updateStateAfterConnect(AMQPFederationConsumerConfiguration configuration, AMQPSessionContext session) {
      final String remoteNodeId = session.getSession().getConnection().getRemoteContainer();

      opposingPeerBindingPattern = Pattern.compile(String.format(OPPOSING_PEER_MATCHING_TEMPLATE, Pattern.quote(remoteNodeId)));
   }

   @Override
   protected void safeCleanupManagerResources(boolean force) {
      try {
         addressTracking.values().forEach((registry) -> {
            if (registry != null) {
               if (isConnected() && !force) {
                  registry.shutdown();
               } else {
                  registry.shutdownNow();
               }
            }
         });
      } finally {
         addressTracking.clear();
         divertsTracking.clear();
      }
   }

   @Override
   public synchronized void afterRemoveAddress(SimpleString address, AddressInfo addressInfo) throws ActiveMQException {
      if (isActive()) {
         final AMQPFederationAddressConsumerRegistry registry = addressTracking.remove(address.toString());

         if (registry != null) {
            logger.trace("Federated address {} was removed, closing federation consumer", address);

            // Demand is gone because the Address is gone and any in-flight messages can be
            // allowed to be released back to the remote as they will not be processed.
            // We removed the tracking information from matched address data to prevent build up
            // of data for entries that may never return and to prevent interference from the
            // next set of events which will be the close of all local consumers for this now
            // removed Address.
            registry.shutdownNow();
         }
      }
   }

   @Override
   public synchronized void afterRemoveBinding(Binding binding, Transaction tx, boolean deleteData) throws ActiveMQException {
      if (isActive()) {
         if (binding instanceof QueueBinding) {
            final AMQPFederationAddressConsumerRegistry registry = addressTracking.get(binding.getAddress().toString());

            logger.trace("Federated address {} binding was removed, reducing demand.", binding.getAddress());

            if (registry != null) {
               // This is QueueBinding that was mapped to a federated address so we can directly remove
               // demand from the federation consumer and close it if demand is now gone.
               tryRemoveDemandOnAddress(registry, binding);
            } else if (policy.isEnableDivertBindings()) {
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
                     // remove the divert from the federated address entry and check if that
                     // removed all local demand which means we can close the consumer.
                     divertEntry.getValue().remove(binding);

                     if (divertEntry.getValue().isEmpty()) {
                        tryRemoveDemandOnAddress(addressTracking.get(sourceAddress), divertEntry.getKey());
                     }
                  }
               });
            }
         } else if (policy.isEnableDivertBindings() && binding instanceof DivertBinding divert) {
            if (divertsTracking.remove(divert) != null) {
               // The divert binding is treated as one unit of demand on a federated address and
               // when the divert is removed that unit of demand is removed regardless of existing
               // bindings still remaining on the divert forwards. If the divert demand was the
               // only thing keeping the federated address consumer open this will result in it
               // being closed.
               try {
                  tryRemoveDemandOnAddress(addressTracking.get(divert.getAddress().toString()), divert);
               } catch (Exception e) {
                  ActiveMQServerLogger.LOGGER.federationBindingsLookupError(divert.getDivert().getForwardAddress(), e);
               }
            }
         }
      }
   }

   private void tryRemoveDemandOnAddress(AMQPFederationAddressConsumerRegistry registry, Binding binding) {
      if (registry != null) {
         logger.trace("Reducing demand on federated address {}", registry.getAddress());
         registry.removeDemand(binding);
      }
   }

   @Override
   protected void scanAllBindings() {
      server.getPostOffice()
            .getAllBindings()
            .filter(binding -> binding instanceof QueueBinding || (policy.isEnableDivertBindings() && binding instanceof DivertBinding))
            .forEach(binding -> afterAddBinding(binding));
   }

   @Override
   public synchronized void afterAddAddress(AddressInfo addressInfo, boolean reload) {
      if (isActive() && policy.isEnableDivertBindings() && policy.test(addressInfo)) {
         try {
            // A Divert can exist in configuration prior to the address having been auto created
            // etc so upon address add this check needs to be run to capture addresses that now
            // match the divert.
            server.getPostOffice()
                  .getDirectBindings(addressInfo.getName())
                  .stream()
                  .filter(binding -> binding instanceof DivertBinding)
                  .forEach(this::checkBindingForMatch);
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.federationBindingsLookupError(addressInfo.getName(), e);
         }
      }
   }

   @Override
   public synchronized void afterAddBinding(Binding binding) {
      if (isActive()) {
         checkBindingForMatch(binding);
      }
   }

   /**
    * Called under lock this method should check if the given {@link Binding} matches the configured address federation
    * policy and federate the address if so.  The incoming {@link Binding} can be either a {@link QueueBinding} or a
    * {@link DivertBinding} so the code should check both.
    *
    * @param binding The binding that should be checked against the federated address policy,
    */
   private void checkBindingForMatch(Binding binding) {
      if (binding instanceof QueueBinding queueBinding) {
         final AddressInfo addressInfo = server.getPostOffice().getAddressInfo(binding.getAddress());

         logger.trace("Binding Added on address: {}, filter={}", queueBinding.getAddress(), queueBinding.getFilter());

         if (testIfAddressMatchesPolicy(addressInfo)) {
            // A plugin can block address federation for a given queue and if another queue
            // binding does trigger address federation we don't want to track the rejected
            // queue as demand so we always run this check before trying to create the address
            // consumer.
            if (isPluginBlockingFederationConsumerCreate(queueBinding.getQueue())) {
               return;
            }

            // Don't treat bindings from the target as demand only local or remote bindings not
            // from the target are real demand that should move messages from the target to the
            // source.
            if (isBindingFromOpposingFederationTarget(queueBinding)) {
               return;
            }

            // Target brokers which have been sent remote federation policies might not have write
            // access via the logged in user to the address with demand which we are attempting to
            // federate messages to so instead of creating a receiver that will fail when the remote
            // routes a message to it we can just omit creating the link in the first place.
            if (federation.isFederationTarget()) {
               try {
                  session.getSessionSPI().check(addressInfo.getName(), CheckType.SEND, federation.getConnectionContext().getSecurityAuth());
               } catch (ActiveMQSecurityException e) {
                  ActiveMQAMQPProtocolLogger.LOGGER.federationTargetSkippedAddressFederation(
                     addressInfo.getName().toString(), "User does not have send permission to configured address.");
                  return;
               } catch (Exception ex) {
                  logger.warn("Caught unknown exception from security check on address:{} send permissions: cannot federate:", addressInfo.getName(), ex);
                  return;
               }
            }

            createOrUpdateFederatedAddressConsumerForBinding(addressInfo, queueBinding);
         } else {
            reactIfQueueBindingMatchesAnyDivertTarget(queueBinding);
         }
      } else if (binding instanceof DivertBinding divertBinding) {
         reactIfAnyQueueBindingMatchesDivertTarget(divertBinding);
      }
   }

   private boolean isBindingFromOpposingFederationTarget(QueueBinding queueBinding) {
      final String queueName = queueBinding.getQueue().getName().toString();

      // If the binding is from the remote peer we are connected to then we shouldn't treat this as
      // demand as we would really only need to pull messages from the remote if there was some other
      // local binding or a binding from a peer that isn't the target of this federation policy manager.
      // If we do treat that as demand we could just be creating a dead loop where the remote sends any
      // messages sent to its address to this peer but there could be no actual local bindings that care
      // and we never loop messages back to their source so they would just be dropped here. This also
      // acts to prevent bi-directional federation links from keeping the links between two brokers alive
      // even when there is no other demand on either side.
      if (opposingPeerBindingPattern.matcher(queueName).matches()) {
         return true;
      }

      return false;
   }

   private void reactIfAnyQueueBindingMatchesDivertTarget(DivertBinding divertBinding) {
      if (!policy.isEnableDivertBindings()) {
         return;
      }

      final AddressInfo addressInfo = server.getPostOffice().getAddressInfo(divertBinding.getAddress());

      if (!testIfAddressMatchesPolicy(addressInfo)) {
         return;
      }

      // Target brokers which have been sent remote federation policies might not have write access
      // via the logged in user to the address this divert is attached to which means we don't need
      // to track this divert. Since we don't add the divert to the tracking map future demand on
      // divert that would otherwise match the address includes won't trigger federation attempts.
      if (federation.isFederationTarget()) {
         try {
            session.getSessionSPI().check(addressInfo.getName(), CheckType.SEND, federation.getConnectionContext().getSecurityAuth());
         } catch (ActiveMQSecurityException e) {
            ActiveMQAMQPProtocolLogger.LOGGER.federationTargetSkippedAddressFederation(
               addressInfo.getName().toString(), "User does not have send permission to configured address.");
            return;
         } catch (Exception ex) {
            logger.warn("Caught unknown exception from security check on address:{} send permissions: cannot federate:", addressInfo.getName(), ex);
            return;
         }
      }

      // We only need to check if we've never seen the divert before, afterwards we will
      // be checking it any time a new QueueBinding is added instead.
      if (!divertsTracking.containsKey(divertBinding)) {
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
                        // The plugin can block the demand totally here either based on the divert itself
                        // or the queue that's attached to the divert.
                        if (isPluginBlockingFederationConsumerCreate(divertBinding.getDivert(), queueBinding.getQueue())) {
                           return;
                        }

                        // The plugin can block the demand selectively based on a single queue attached to
                        // the divert target(s).
                        if (isPluginBlockingFederationConsumerCreate(queueBinding.getQueue())) {
                           return;
                        }

                        matchingQueues.add(queueBinding);

                        createOrUpdateFederatedAddressConsumerForBinding(addressInfo, divertBinding);
                     });
            }
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.federationBindingsLookupError(forwardAddress, e);
         }
      }
   }

   private void reactIfQueueBindingMatchesAnyDivertTarget(QueueBinding queueBinding) {
      if (!policy.isEnableDivertBindings()) {
         return;
      }

      final SimpleString queueAddress = queueBinding.getAddress();

      divertsTracking.entrySet().forEach((divert) -> {
         final SimpleString forwardAddress = divert.getKey().getDivert().getForwardAddress();
         final DivertBinding divertBinding = divert.getKey();

         // Check matched diverts to see if the QueueBinding address matches the address or
         // addresses (composite diverts) of the Divert and if so then we can check if we need
         // to create demand on the source address on the remote if we haven't done so already.

         if (!divert.getValue().contains(queueBinding) && isAddressInDivertForwards(queueAddress, forwardAddress)) {
            // The plugin can block the demand totally here either based on the divert itself
            // or the queue that's attached to the divert.
            if (isPluginBlockingFederationConsumerCreate(divertBinding.getDivert(), queueBinding.getQueue())) {
               return;
            }

            // The plugin can block the demand selectively based on a single queue attached to
            // the divert target(s).
            if (isPluginBlockingFederationConsumerCreate(queueBinding.getQueue())) {
               return;
            }

            // Each divert that forwards to the address the queue is bound to we add demand
            // in the diverts tracker.
            divert.getValue().add(queueBinding);

            final AddressInfo addressInfo = server.getPostOffice().getAddressInfo(divertBinding.getAddress());

            createOrUpdateFederatedAddressConsumerForBinding(addressInfo, divertBinding);
         }
      });
   }

   private void createOrUpdateFederatedAddressConsumerForBinding(AddressInfo addressInfo, Binding binding) {
      logger.trace("Federation Address Policy matched on for demand on address: {} : binding: {}", addressInfo, binding);

      final String addressName = addressInfo.getName().toString();
      final AMQPFederationAddressConsumerRegistry consumerRegistry;

      // Check for existing consumer add demand from a additional local consumer to ensure
      // the remote consumer remains active until all local demand is withdrawn.
      if (addressTracking.containsKey(addressName)) {
         consumerRegistry = addressTracking.get(addressName);
      } else {
         consumerRegistry = new AMQPFederationAddressConsumerRegistry(this, addressInfo);
         addressTracking.put(addressName, consumerRegistry);
      }

      // Demand passed all binding plugin blocking checks so we track it, plugin can still
      // stop federation of the address based on some external criteria but once it does
      // (if ever) allow it we will have tracked all allowed demand.
      consumerRegistry.addDemand(binding);
   }

   /**
    * Checks if the remote address added falls within the set of addresses that match the configured address policy and
    * if so scans for local demand on that address to see if a new attempt to federate the address is needed.
    *
    * @param addressName The address that was added on the remote.
    * @throws Exception if an error occurs while processing the address added event.
    */
   synchronized void afterRemoteAddressAdded(String addressName) throws Exception {
      // Assume that the remote address that matched a previous federation attempt is MULTICAST
      // so that we retry if current local state matches the policy and if it isn't we will once
      // again record the federation attempt with the remote and be updated if the remote removes
      // and adds the address again (hopefully with the correct routing type). We retrain all the
      // current demand and don't need to re-check the server state before trying to create the
      // remote address consumer.
      if (isActive() && testIfAddressMatchesPolicy(addressName, RoutingType.MULTICAST)) {
         final AMQPFederationAddressConsumerRegistry consumerRegistry = addressTracking.get(addressName);

         if (consumerRegistry != null) {
            consumerRegistry.recover();
         }
      }
   }

   /**
    * Performs the test against the configured address policy to check if the target address is a match or not. A
    * subclass can override this method and provide its own match tests in combination with the configured matching
    * policy.
    *
    * @param addressInfo The address that is being tested for a policy match.
    * @return {@code true} if the address given is a match against the policy
    */
   private boolean testIfAddressMatchesPolicy(AddressInfo addressInfo) {
      if (!policy.test(addressInfo)) {
         return false;
      }

      // Address consumers can't pull as we have no real metric to indicate when / how much
      // we should pull so instead we refuse to match if credit set to zero.
      if (configuration.getReceiverCredits() <= 0) {
         logger.debug("Federation address policy rejecting match on {} because credit is set to zero:", addressInfo.getName());
         return false;
      } else {
         return true;
      }
   }

   /**
    * Performs the test against the configured address policy to check if the target address is a match or not. A
    * subclass can override this method and provide its own match tests in combination with the configured matching
    * policy.
    *
    * @param address The address that is being tested for a policy match.
    * @param type    The routing type of the address to test against the policy.
    * @return {@code true} if the address given is a match against the policy
    */
   private boolean testIfAddressMatchesPolicy(String address, RoutingType type) {
      return policy.test(address, type);
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

   private static class AMQPFederationConsumerKey {

      private final String address;
      private final Filter filter;

      AMQPFederationConsumerKey(String address) {
         this(address, null);
      }

      AMQPFederationConsumerKey(String address, Filter filter) {
         this.address = Objects.requireNonNull(address);
         this.filter = filter;
      }

      @Override
      public int hashCode() {
         return Objects.hashCode(address) + Objects.hashCode(filter);
      }

      @Override
      public boolean equals(Object obj) {
         if (this == obj) {
            return true;
         }
         if (obj == null) {
            return false;
         }
         if (getClass() != obj.getClass()) {
            return false;
         }

         final AMQPFederationConsumerKey other = (AMQPFederationConsumerKey) obj;

         return Objects.equals(address, other.address) && Objects.equals(filter, other.filter);
      }
   }

   /*
    * Tracks federation consumers for a given address. When the federation policy enabled consumers
    * with filters there can be multiple federation consumers to the remote that each apply a different
    * message filter each of which is tracked here.
    */
   private static class AMQPFederationAddressConsumerRegistry {

      private final AMQPFederationConsumerKey unfilteredConsumerKey;
      private final AMQPFederationAddressPolicyManager manager;
      private final Map<AMQPFederationConsumerKey, AMQPFederationAddressConsumerManager<?>> registry = new HashMap<>();
      private final AddressInfo addressInfo;
      private final String baseConsumerFilter;
      private final AMQPFederationConsumerConfiguration configuration;

      AMQPFederationAddressConsumerRegistry(AMQPFederationAddressPolicyManager manager, AddressInfo addressInfo) {
         this.manager = manager;
         this.addressInfo = addressInfo;
         this.baseConsumerFilter = manager.baseConsumerFilter;
         this.configuration = manager.configuration;
         this.unfilteredConsumerKey = new AMQPFederationConsumerKey(addressInfo.getName().toString());
      }

      public void removeDemand(Binding binding) {
         final AMQPFederationConsumerKey key = createConsumerKey(binding);
         final AMQPFederationAddressConsumerManager<?> manager = registry.get(key);

         if (manager != null) {
            manager.removeDemand(binding);
         }
      }

      public void addDemand(Binding binding) {
         final AMQPFederationConsumerKey key = createConsumerKey(binding);

         AMQPFederationAddressConsumerManager<?> consumerManager = registry.get(key);

         if (consumerManager == null) {
            if (isUseConduitConsumer()) {
               registry.put(key, consumerManager = new AMQPFederationAddressConduitConsumerManager(manager, createConsumerInfo(addressInfo, binding), addressInfo));
            } else {
               registry.put(key, consumerManager = new AMQPFederationAddressBindingsConsumerManager(manager, createConsumerInfo(addressInfo, binding), addressInfo));
            }
         }

         consumerManager.addDemand(binding);
      }

      public String getAddress() {
         return addressInfo.getName().toString();
      }

      public void recover() {
         registry.values().forEach((entry) -> {
            if (entry != null) {
               entry.recover();
            }
         });
      }

      public void shutdown() {
         registry.values().forEach((entry) -> {
            if (entry != null) {
               entry.shutdown();
            }
         });
      }

      public void shutdownNow() {
         registry.values().forEach((entry) -> {
            if (entry != null) {
               entry.shutdownNow();
            }
         });
      }

      private AMQPFederationConsumerKey createConsumerKey(Binding binding) {
         if (isUseConduitConsumer() || binding.getFilter() == null) {
            return unfilteredConsumerKey;
         } else {
            return new AMQPFederationConsumerKey(binding.getAddress().toString(), binding.getFilter());
         }
      }

      /**
       * Create a new {@link FederationConsumerInfo} based on the given {@link AddressInfo} and the configured
       * {@link FederationReceiveFromAddressPolicy}.
       *
       * @param address
       *       The {@link AddressInfo} to use as a basis for the consumer information object.
       * @param binding
       *       The {@link Binding} that is the source of demand for this consumer info object
       *
       * @return a new {@link FederationConsumerInfo} instance based on the given address
       */
      private FederationConsumerInfo createConsumerInfo(AddressInfo address, Binding binding) {
         final String addressName = address.getName().toString();
         final boolean ignoreBindingFilters = isUseConduitConsumer() || binding.getFilter() == null;
         final String generatedQueueName = generateQueueName(address, binding, ignoreBindingFilters);
         final String consumerFilter;

         if (ignoreBindingFilters) {
            consumerFilter = baseConsumerFilter;
         } else if (baseConsumerFilter != null) {
            consumerFilter = "(" + binding.getFilter().getFilterString() + ") AND " + baseConsumerFilter;
         } else {
            consumerFilter = binding.getFilter().getFilterString().toString();
         }

         return new AMQPFederationGenericConsumerInfo(FederationConsumerInfo.Role.ADDRESS_CONSUMER,
            addressName,
            generatedQueueName,
            address.getRoutingType(),
            consumerFilter,
            CompositeAddress.toFullyQualified(addressName, generatedQueueName),
            ActiveMQDefaultConfiguration.getDefaultConsumerPriority());
      }

      private boolean isUseConduitConsumer() {
         if (!manager.getCapabilities().isUseFQQNAddressSubscriptions()) {
            // prior to FQQN subscription support we used a simple link name that would not be unique amongst
            // multiple consumers on the same address so for most features or behaviors that come later we cannot
            // action them properly and we must fallback to a conduit consumer strategy.
            return true;
         } else if (manager.getWildcardConfiguration().isWild(addressInfo.getName())) {
            // For a wildcard subscription we want to treat it as a bindings aware consumer and route messages only
            // to the bindings we have tracked as demand on the address. The broker wild-card code is not known to
            // support direct sends to the literal wildcard address and could fail to route messages correctly.
            return false;
         } else {
            // If we get here then the only consideration is if we are honoring consumers filters on the address
            // which decides if we can route straight to the address or must route to specific bindings groups that
            // all share the same filter.
            return configuration.isIgnoreAddressBindingFilters();
         }
      }

      private String generateQueueName(AddressInfo address, Binding binding, boolean ignoreFilters) {
         if (ignoreFilters) {
            return "federation." + manager.getFederation().getName() +
                   ".policy." + manager.getPolicyName() +
                   ".address." + address.getName() +
                   ".node." + manager.server.getNodeID();
         } else {
            return "federation." + manager.getFederation().getName() +
                   ".policy." + manager.getPolicyName() +
                   ".address." + address.getName() +
                   ".filterId." + generateFilterId(binding.getFilter().getFilterString().toString()) +
                   ".node." + manager.server.getNodeID();
         }
      }
   }

   private static String generateFilterId(String filterString) {
      final byte[] filterUTF8 = filterString.getBytes(StandardCharsets.UTF_8);

      try {
         return filterString.hashCode() + "." + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(filterUTF8));
      } catch (Exception e) {
         throw new UnsupportedOperationException(
            "Could not create filter ID SHA, enable ignore address filters required to create federation consumers", e);
      }
   }

   /*
    * This base class is the manager for a single AMQP federation address consumer which ensure that consumer are
    * started and stopped with proper error handling and recovery mechanics
    */
   private abstract static class AMQPFederationAddressConsumerManager<Consumer extends AMQPFederationConsumer> extends AMQPFederationConsumerManager<Binding, Consumer> {

      protected final AMQPFederation federation;
      protected final AMQPFederationAddressPolicyManager manager;
      protected final FederationReceiveFromAddressPolicy policy;
      protected final AddressInfo addressInfo;
      protected final FederationConsumerInfo consumerInfo;

      AMQPFederationAddressConsumerManager(AMQPFederationAddressPolicyManager manager, FederationConsumerInfo consumerInfo, AddressInfo addressInfo) {
         super(manager);

         this.manager = manager;
         this.federation = manager.getFederation();
         this.policy = manager.getPolicy();
         this.addressInfo = addressInfo;
         this.consumerInfo = consumerInfo;
      }

      @Override
      protected boolean isPluginBlockingFederationConsumerCreate() {
         return manager.isPluginBlockingFederationConsumerCreate(addressInfo);
      }

      @Override
      protected void whenDemandTrackingEntryAdded(Binding entry, Consumer consumer) {
         // No current action needed
      }

      @Override
      protected void whenDemandTrackingEntryRemoved(Binding entry, Consumer consumer) {
         // No current action needed
      }
   }

   /**
    * Manager type that creates address conduit consumers that routes to the address proper.
    */
   private static class AMQPFederationAddressConduitConsumerManager extends AMQPFederationAddressConsumerManager<AMQPFederationAddressConduitConsumer> {

      AMQPFederationAddressConduitConsumerManager(AMQPFederationAddressPolicyManager manager, FederationConsumerInfo consumerInfo, AddressInfo addressInfo) {
         super(manager, consumerInfo, addressInfo);
      }

      @Override
      protected AMQPFederationAddressConduitConsumer createFederationConsumer(Set<Binding> demand) {
         if (logger.isTraceEnabled()) {
            logger.trace("AMQP Federation {} creating address consumer: {} for policy: {}", federation.getName(), consumerInfo, policy.getPolicyName());
         }

         // Don't initiate anything yet as the caller might need to register error handlers etc
         // before the attach is sent otherwise they could miss the failure case.
         return new AMQPFederationAddressConduitConsumer(
            manager, manager.getConfiguration(), federation.getSessionContext(), consumerInfo, manager.getMetrics().newConsumerMetrics());
      }
   }

   /**
    * Manager type that creates address consumers that will route to bindings
    */
   private static class AMQPFederationAddressBindingsConsumerManager extends AMQPFederationAddressConsumerManager<AMQPFederationAddressBindingsConsumer> {

      AMQPFederationAddressBindingsConsumerManager(AMQPFederationAddressPolicyManager manager, FederationConsumerInfo consumerInfo, AddressInfo addressInfo) {
         super(manager, consumerInfo, addressInfo);
      }

      @Override
      protected AMQPFederationAddressBindingsConsumer createFederationConsumer(Set<Binding> demand) {
         if (logger.isTraceEnabled()) {
            logger.trace("AMQP Federation {} creating address consumer: {} for policy: {}", federation.getName(), consumerInfo, policy.getPolicyName());
         }

         // Don't initiate anything yet as the caller might need to register error handlers etc
         // before the attach is sent otherwise they could miss the failure case.
         final AMQPFederationAddressBindingsConsumer consumer = new AMQPFederationAddressBindingsConsumer(
            manager, manager.getConfiguration(), federation.getSessionContext(), consumerInfo, manager.getMetrics().newConsumerMetrics());

         consumer.addBindings(demand);

         return consumer;
      }

      @Override
      protected void whenDemandTrackingEntryAdded(Binding binding, AMQPFederationAddressBindingsConsumer consumer) {
         // When a consumer exists it needs to know about new bindings, when created it will be given the current set.
         if (consumer != null) {
            consumer.addBinding(binding);
         }
      }

      @Override
      protected void whenDemandTrackingEntryRemoved(Binding binding, AMQPFederationAddressBindingsConsumer consumer) {
         // When a consumer exists it needs to know about removed bindings, when created it will be given the current set.
         if (consumer != null) {
            consumer.removeBinding(binding);
         }
      }
   }
}
