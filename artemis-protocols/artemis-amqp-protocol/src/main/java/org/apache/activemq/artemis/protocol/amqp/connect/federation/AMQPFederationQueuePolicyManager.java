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

import static org.apache.activemq.artemis.protocol.amqp.federation.FederationConstants.FEDERATION_NAME;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.QueueBinding;
import org.apache.activemq.artemis.core.security.CheckType;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerBindingPlugin;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerConsumerPlugin;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.protocol.amqp.federation.FederationConsumerInfo;
import org.apache.activemq.artemis.protocol.amqp.federation.FederationReceiveFromQueuePolicy;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPSessionContext;
import org.apache.activemq.artemis.protocol.amqp.federation.FederationConsumerInfo.Role;
import org.apache.activemq.artemis.protocol.amqp.logger.ActiveMQAMQPProtocolLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The AMQP Federation implementation of an federation queue policy manager.
 */
public final class AMQPFederationQueuePolicyManager extends AMQPFederationLocalPolicyManager implements ActiveMQServerConsumerPlugin, ActiveMQServerBindingPlugin {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   protected final FederationReceiveFromQueuePolicy policy;
   protected final Map<FederationConsumerInfo, AMQPFederationQueueConsumerManager> federationConsumers = new HashMap<>();

   /*
    * Unique for the lifetime of this policy manager which is either the lifetime of the broker or until the
    * configuration is reloaded and this federation instance happens to be updated but not removed in which
    * case the full federation instance is shutdown and then removed and re-added as if it was new.
    */
   private final String CONSUMER_INFO_ATTACHMENT_KEY = UUID.randomUUID().toString();

   public AMQPFederationQueuePolicyManager(AMQPFederation federation, AMQPFederationMetrics metrics, FederationReceiveFromQueuePolicy queuePolicy) throws ActiveMQException {
      super(federation, metrics, queuePolicy);

      Objects.requireNonNull(queuePolicy, "The Queue match policy cannot be null");

      this.policy = queuePolicy;
   }

   /**
    * {@return the receive from address policy that backs the address policy manager}
    */
   @Override
   public FederationReceiveFromQueuePolicy getPolicy() {
      return policy;
   }

   @Override
   protected void safeCleanupManagerResources(boolean force) {
      try {
         federationConsumers.values().forEach((entry) -> {
            if (entry != null) {
               if (isConnected() && !force) {
                  entry.shutdown();
               } else {
                  entry.shutdownNow();
               }
            }
         });
      } finally {
         federationConsumers.clear();
      }
   }

   @Override
   public synchronized void afterCreateConsumer(ServerConsumer consumer) {
      if (isActive()) {
         reactIfConsumerMatchesPolicy(consumer);
      }
   }

   @Override
   public synchronized void afterCloseConsumer(ServerConsumer consumer, boolean failed) {
      if (isActive()) {
         final FederationConsumerInfo consumerInfo = (FederationConsumerInfo) consumer.getAttachment(CONSUMER_INFO_ATTACHMENT_KEY);

         if (consumerInfo != null && federationConsumers.containsKey(consumerInfo)) {
            final AMQPFederationQueueConsumerManager entry = federationConsumers.get(consumerInfo);
            logger.trace("Reducing demand on federated queue {}", entry.getQueueName());
            entry.removeDemand(consumer);
         }
      }
   }

   @Override
   public synchronized void afterRemoveBinding(Binding binding, Transaction tx, boolean deleteData) throws ActiveMQException {
      if (binding instanceof QueueBinding queueBinding) {
         final String queueName = queueBinding.getQueue().getName().toString();

         federationConsumers.values().removeIf((entry) -> {
            if (entry.getQueueName().equals(queueName)) {
               logger.trace("Federated queue {} was removed, closing federation consumer", queueName);

               // Demand is gone because the Queue binding is gone and any in-flight messages
               // can be allowed to be released back to the remote as they will not be processed.
               // We removed the consumer information from demand tracking to prevent build up
               // of data for entries that may never return and to prevent interference from the
               // next set of events which will be the close of all local consumers for this now
               // removed Queue.
               entry.shutdownNow();

               return true;
            } else {
               return false;
            }
         });
      }
   }

   @Override
   protected void scanAllBindings() {
      server.getPostOffice()
            .getAllBindings()
            .filter(b -> b instanceof QueueBinding)
            .map(b -> (QueueBinding) b)
            .forEach(b -> checkQueueForMatch(b.getQueue()));
   }

   @Override
   protected void updateStateAfterConnect(AMQPFederationConsumerConfiguration configuration, AMQPSessionContext session) {
      // No state needs updated currently on a per connection basis.
   }

   private void checkQueueForMatch(Queue queue) {
      queue.getConsumers()
           .stream()
           .filter(consumer -> consumer instanceof ServerConsumer)
           .map(c -> (ServerConsumer) c)
           .forEach(this::reactIfConsumerMatchesPolicy);
   }

   private void reactIfConsumerMatchesPolicy(ServerConsumer consumer) {
      final String queueName = consumer.getQueue().getName().toString();
      final SimpleString addressName = consumer.getQueueAddress();

      if (testIfQueueMatchesPolicy(addressName.toString(), queueName)) {
         final boolean federationConsumer = isFederationConsumer(consumer);

         // We should ignore federation consumers from remote peers but configuration does allow
         // these to be federated again for some very specific use cases so we check before then
         // moving onto any server plugin checks kick in.
         if (federationConsumer && !policy.isIncludeFederated()) {
            return;
         }

         // Target brokers which have been sent remote federation policies might not have write
         // access via the logged in user to the queue with demand which we are attempting to
         // federate messages to so instead of creating a receiver that will fail when the remote
         // routes a message to it we can just omit creating the link in the first place.
         if (federation.isFederationTarget()) {
            try {
               session.getSessionSPI().check(addressName, consumer.getQueue().getName(), CheckType.SEND, federation.getConnectionContext().getSecurityAuth());
            } catch (ActiveMQSecurityException e) {
               ActiveMQAMQPProtocolLogger.LOGGER.federationTargetSkippedQueueFederation(
                  queueName, "User does not have send permission to configured queue.");
               return;
            } catch (Exception ex) {
               logger.warn("Caught unknown exception from security check on queue:{} send permissions: cannot federate:", queueName, ex);
               return;
            }
         }

         logger.trace("Federation Policy matched on consumer for binding: {}", consumer.getBinding());

         final AMQPFederationQueueConsumerManager entry;
         final FederationConsumerInfo consumerInfo = createConsumerInfo(consumer, federationConsumer);

         // Check for existing consumer add demand from a additional local consumer to ensure
         // the remote consumer remains active until all local demand is withdrawn.
         if (federationConsumers.containsKey(consumerInfo)) {
            logger.trace("Federation Queue Policy manager found existing demand for queue: {}, adding demand", queueName);
            entry = federationConsumers.get(consumerInfo);
         } else {
            federationConsumers.put(consumerInfo, entry = new AMQPFederationQueueConsumerManager(this, CONSUMER_INFO_ATTACHMENT_KEY, consumerInfo, consumer.getQueue()));
         }

         // Demand passed all binding plugin blocking checks so we track it, plugin can still
         // stop federation of the queue based on some external criteria but once it does
         // (if ever) allow it we will have tracked all allowed demand.
         entry.addDemand(consumer);
      }
   }

   /**
    * Checks if the remote queue added falls within the set of queues that match the configured queue policy and if so
    * scans for local demand on that queue to see if a new attempt to federate the queue is needed.
    *
    * @param addressName The address that was added on the remote.
    * @param queueName   The queue that was added on the remote.
    *
    * @throws Exception if an error occurs while processing the queue added event.
    */
   public synchronized void afterRemoteQueueAdded(String addressName, String queueName) throws Exception {
      // We ignore the remote address as locally the policy can be a wild card match and we can
      // try to federate based on the Queue only, if the remote rejects the federation consumer
      // binding again the request will once more be recorded and we will get another event if
      // the queue were recreated such that a match could be made. We retain all the current
      // demand and don't need to re-check the server state before trying to create the
      // remote queue consumer.
      if (isActive() && testIfQueueMatchesPolicy(queueName)) {
         final Queue queue = server.locateQueue(queueName);

         if (queue != null) {
            federationConsumers.forEach((k, v) -> {
               if (k.getQueueName().equals(queueName)) {
                  v.recover();
               }
            });
         }
      }
   }

   /**
    * Performs the test against the configured queue policy to check if the target queue and its associated address is a
    * match or not. A subclass can override this method and provide its own match tests in combination with the
    * configured matching policy.
    *
    * @param address   The address that is being tested for a policy match.
    * @param queueName The name of the queue that is being tested for a policy match.
    *
    * @return {@code true} if the address given is a match against the policy
    */
   private boolean testIfQueueMatchesPolicy(String address, String queueName) {
      return policy.test(address, queueName);
   }

   /**
    * Performs the test against the configured queue policy to check if the target queue minus its associated address is
    * a match or not. A subclass can override this method and provide its own match tests in combination with the
    * configured matching policy.
    *
    * @param queueName The name of the queue that is being tested for a policy match.
    *
    * @return {@code true} if the address given is a match against the policy
    */
   private boolean testIfQueueMatchesPolicy(String queueName) {
      return policy.testQueue(queueName);
   }

   /**
    * Create a new {@link FederationConsumerInfo} based on the given {@link ServerConsumer} and the configured
    * {@link FederationReceiveFromQueuePolicy}. This should only be called once when a consumer is added and
    * we begin tracking it as demand on a federated queue.
    *
    * @param consumer The {@link ServerConsumer} to use as a basis for the consumer information object.
    * @param federationConsumer Is the consumer one that was created by a remote federation controller
    *
    * @return a new {@link FederationConsumerInfo} instance based on the server consumer
    */
   private FederationConsumerInfo createConsumerInfo(ServerConsumer consumer, boolean federationConsumer) {
      final Queue queue = consumer.getQueue();
      final String queueName = queue.getName().toString();
      final String address = queue.getAddress().toString();
      final int priority = selectPriority(consumer, federationConsumer);
      final String filterString = selectFilter(consumer);

      return new AMQPFederationGenericConsumerInfo(Role.QUEUE_CONSUMER,
                                                   address, // Source and target address are the same for Queue consumers
                                                   address,
                                                   queueName,
                                                   queue.getRoutingType(),
                                                   filterString,
                                                   priority);
   }

   private String selectFilter(ServerConsumer consumer) {
      final Filter consumerFilter;
      final Filter queueFilter = consumer.getQueue().getFilter();

      if (!configuration.isIgnoreSubscriptionFilters()) {
         consumerFilter = consumer.getFilter();
      } else {
         consumerFilter = null;
      }

      if (consumerFilter != null) {
         return consumerFilter.getFilterString().toString();
      } else if (queueFilter != null) {
         return queueFilter.getFilterString().toString();
      } else {
         return null;
      }
   }

   private int selectPriority(ServerConsumer consumer, boolean federationConsumer) {
      // Use the priority from the federation consumer as indicated and only choose values for
      // non-federation consumers to help avoid an infinite descending priority loop when the
      // federation consumers are included and the configured brokers loop or are bi-directional.
      if (federationConsumer) {
         return consumer.getPriority();
      } else if (configuration.isIgnoreSubscriptionPriorities()) {
         return ActiveMQDefaultConfiguration.getDefaultConsumerPriority() + policy.getPriorityAjustment();
      } else {
         return consumer.getPriority() + policy.getPriorityAjustment();
      }
   }

   /*
    * This method matches on the same criteria as the original Core client based Federation code which
    * allows this implementation to see those consumers as well as its own which in this methods
    * implementation must also use this same mechanism to mark federation resources.
    */
   private boolean isFederationConsumer(ServerConsumer consumer) {
      final ServerSession serverSession = server.getSessionByID(consumer.getSessionID());

      // Care must be taken to only check this on consumer added and not on other consumer removed
      // events as the session can be removed before those events are triggered and this will falsely
      // indicate that the consumer is not a federation consumer. This check works for both AMQP and
      // Core federation consumers.
      if (serverSession != null && serverSession.getMetaData() != null) {
         return serverSession.getMetaData(FEDERATION_NAME) != null;
      } else {
         return false;
      }
   }

   private static class AMQPFederationQueueConsumerManager extends AMQPFederationConsumerManager<ServerConsumer, AMQPFederationQueueConsumer> {

      private final AMQPFederation federation;
      private final AMQPFederationQueuePolicyManager manager;
      private final FederationReceiveFromQueuePolicy policy;
      private final Queue queue;
      private final FederationConsumerInfo consumerInfo;
      private final String policyInfoKey;

      AMQPFederationQueueConsumerManager(AMQPFederationQueuePolicyManager manager, String policyInfoKey, FederationConsumerInfo consumerInfo, Queue queue) {
         super(manager);

         this.federation = manager.getFederation();
         this.policy = manager.getPolicy();
         this.manager = manager;
         this.queue = queue;
         this.consumerInfo = consumerInfo;
         this.policyInfoKey = policyInfoKey;
      }

      /**
       * {@return the name of the Queue this federation consumer manager is attached to}
       */
      public String getQueueName() {
         return queue.getName().toString();
      }

      @Override
      protected AMQPFederationQueueConsumer createFederationConsumer(Set<ServerConsumer> demand) {
         if (logger.isTraceEnabled()) {
            logger.trace("AMQP Federation {} creating queue consumer: {} for policy: {}", federation.getName(), consumerInfo, policy.getPolicyName());
         }

         // Don't initiate anything yet as the caller might need to register error handlers etc
         // before the attach is sent otherwise they could miss the failure case.
         return new AMQPFederationQueueConsumer(manager, manager.getConfiguration(), federation.getSessionContext(), consumerInfo, manager.getMetrics().newConsumerMetrics());
      }

      @Override
      protected boolean isPluginBlockingFederationConsumerCreate() {
         return manager.isPluginBlockingFederationConsumerCreate(queue);
      }

      @Override
      protected void whenDemandTrackingEntryRemoved(ServerConsumer serverConsumer, AMQPFederationQueueConsumer consumer) {
         serverConsumer.removeAttachment(policyInfoKey);
      }

      @Override
      protected void whenDemandTrackingEntryAdded(ServerConsumer serverConsumer, AMQPFederationQueueConsumer consumer) {
         // Attach the consumer info to the server consumer for later use on consumer close or other
         // operations that need to retrieve the data used to create the federation consumer identity.
         serverConsumer.addAttachment(policyInfoKey, consumerInfo);
      }
   }
}
