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

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.QueueBinding;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerConsumerPlugin;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeReceiverInfo.ReceiverRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AMQP Bridge policy manager that tracks local queues that match the policy configurations
 * for local demand and creates consumers to the remote peer configured.
 */
public final class AMQPBridgeFromQueuePolicyManager extends AMQPBridgeFromPolicyManager implements ActiveMQServerConsumerPlugin {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final AMQPBridgeQueuePolicy policy;
   private final Map<AMQPBridgeReceiverInfo, AMQPBridgeQueueReceiverManager> demandTracking = new HashMap<>();

   /*
    * Unique for the lifetime of this policy manager which is either the lifetime of the broker or until the
    * configuration is reloaded and this bridge instance happens to be updated but not removed in which
    * case the full bridge instance is shutdown and then removed and re-added as if it was new.
    */
   private final String RECEIVER_INFO_ATTACHMENT_KEY = UUID.randomUUID().toString();

   public AMQPBridgeFromQueuePolicyManager(AMQPBridgeManager bridge, AMQPBridgeMetrics metric, AMQPBridgeQueuePolicy policy) {
      super(bridge, metric, policy.getPolicyName(), AMQPBridgeType.BRIDGE_FROM_QUEUE);

      Objects.requireNonNull(policy, "The Queue match policy cannot be null");

      this.policy = policy;
   }

   /**
    * {@return the policy that defines the bridged queue this policy manager monitors}
    */
   @Override
   public AMQPBridgeQueuePolicy getPolicy() {
      return policy;
   }

   @Override
   protected void scanManagedResources() {
      server.getPostOffice()
            .getAllBindings()
            .filter(b -> b instanceof QueueBinding)
            .map(b -> (QueueBinding) b)
            .forEach(b -> {
               if (configuration.isReceiverDemandTrackingDisabled()) {
                  reactIfQueueMatchesPolicy(b.getQueue());
               } else {
                  checkQueueWithConsumerForMatch(b.getQueue());
               }
            });
   }

   @Override
   protected void safeCleanupManagerResources(boolean force) {
      try {
         demandTracking.values().forEach((manager) -> {
            if (manager != null) {
               if (isConnected() && !force) {
                  manager.shutdown();
               } else {
                  manager.shutdownNow();
               }
            }
         });
      } finally {
         demandTracking.clear();
      }
   }

   @Override
   public synchronized void afterCreateConsumer(ServerConsumer consumer) {
      if (isActive() && !configuration.isReceiverDemandTrackingDisabled()) {
         reactIfQueueWithConsumerMatchesPolicy(consumer);
      }
   }

   @Override
   public synchronized void afterCloseConsumer(ServerConsumer consumer, boolean failed) {
      if (isActive() && !configuration.isReceiverDemandTrackingDisabled()) {

         final AMQPBridgeReceiverInfo receiverInfo = (AMQPBridgeReceiverInfo) consumer.getAttachment(RECEIVER_INFO_ATTACHMENT_KEY);

         if (receiverInfo != null && demandTracking.containsKey(receiverInfo)) {
            final AMQPBridgeQueueReceiverManager manager = demandTracking.get(receiverInfo);
            logger.trace("Reducing demand on bridged queue {}", manager.getQueueName());
            manager.removeDemand(consumer);
         }
      }
   }

   @Override
   public synchronized void afterAddBinding(Binding binding) throws ActiveMQException {
      if (isActive() && configuration.isReceiverDemandTrackingDisabled() && binding instanceof QueueBinding queueBinding) {
         reactIfQueueMatchesPolicy(queueBinding.getQueue());
      }
   }

   @Override
   public synchronized void afterRemoveBinding(Binding binding, Transaction tx, boolean deleteData) throws ActiveMQException {
      if (isActive() && binding instanceof QueueBinding queueBinding) {
         final String queueName = queueBinding.getQueue().getName().toString();

         demandTracking.values().removeIf((manager) -> {
            if (manager.getQueueName().equals(queueName)) {
               logger.trace("Bridged queue {} was removed, closing bridge receiver", queueName);

               // Demand is gone because the Queue binding is gone and any in-flight messages
               // can be allowed to be released back to the remote as they will not be processed.
               // We removed the receiver information from demand tracking to prevent build up
               // of data for entries that may never return and to prevent interference from the
               // next set of events which will be the close of all local receivers for this now
               // removed Queue.
               manager.shutdownNow();

               return true;
            } else {
               return false;
            }
         });
      }
   }

   private void checkQueueWithConsumerForMatch(Queue queue) {
      queue.getConsumers()
           .stream()
           .filter(consumer -> consumer instanceof ServerConsumer)
           .map(c -> (ServerConsumer) c)
           .forEach(this::reactIfQueueWithConsumerMatchesPolicy);
   }

   private void reactIfQueueWithConsumerMatchesPolicy(ServerConsumer consumer) {
      reactIfDemandMatchesPolicy(consumer.getQueue(), consumer, false);
   }

   private void reactIfQueueMatchesPolicy(Queue queue) {
      reactIfDemandMatchesPolicy(queue, null, true);
   }

   private void reactIfDemandMatchesPolicy(Queue queue, ServerConsumer consumer, boolean forceDemand) {
      final String queueName = queue.getName().toString();
      final String addressName = queue.getAddress().toString();

      if (testIfQueueMatchesPolicy(addressName, queueName)) {
         if (consumer != null) {
            logger.trace("AMQP Bridge from Queue Policy matched on consumer for Queue: {}", consumer.getQueue());
         } else {
            logger.trace("AMQP Bridge Queue Policy matched on Queue: {} when demand tracking disabled", queue.getName());
         }

         final AMQPBridgeQueueReceiverManager manager;
         final AMQPBridgeReceiverInfo receiverInfo = createReceiverInfo(consumer, queue);

         // Check for existing receiver and add demand from a additional local consumer to ensure
         // the remote receiver remains active until all local demand is withdrawn.
         if (demandTracking.containsKey(receiverInfo)) {
            logger.trace("AMQP Bridge from Queue Policy manager found existing demand for queue: {}, adding demand", queueName);
            manager = demandTracking.get(receiverInfo);
         } else {
            demandTracking.put(receiverInfo, manager = new AMQPBridgeQueueReceiverManager(this, RECEIVER_INFO_ATTACHMENT_KEY, configuration, receiverInfo, queue));
         }

         if (forceDemand) {
            manager.forceDemand();
         } else {
            manager.addDemand(consumer);
         }
      }
   }

   private boolean testIfQueueMatchesPolicy(String address, String queueName) {
      return policy.test(address, queueName);
   }

   private AMQPBridgeReceiver createBridgeReceiver(AMQPBridgeReceiverInfo receiverInfo) {
      Objects.requireNonNull(receiverInfo, "AMQP Bridge Queue receiver information object was null");

      if (logger.isTraceEnabled()) {
         logger.trace("AMQP Bridge {} creating queue receiver: {} for policy: {}", bridge.getName(), receiverInfo, policy.getPolicyName());
      }

      // Don't initiate anything yet as the caller might need to register error handlers etc
      // before the attach is sent otherwise they could miss the failure case.
      return new AMQPBridgeFromQueueReceiver(this, configuration, session, receiverInfo, policy, metrics.newReceiverMetrics());
   }

   private AMQPBridgeReceiverInfo createReceiverInfo(ServerConsumer consumer, Queue queue) {
      final StringBuilder remoteAddressBuilder = new StringBuilder();

      if (policy.getRemoteAddressPrefix() != null) {
         remoteAddressBuilder.append(policy.getRemoteAddressPrefix());
      }

      if (policy.getRemoteAddress() != null && !policy.getRemoteAddress().isBlank()) {
         remoteAddressBuilder.append(policy.getRemoteAddress());
      } else {
         remoteAddressBuilder.append(queue.getName().toString());
      }

      if (policy.getRemoteAddressSuffix() != null) {
         remoteAddressBuilder.append(policy.getRemoteAddressSuffix());
      }

      final String filterString = selectFilter(
         policy.getFilter(),
         configuration.isIgnoreQueueFilters() ? null : queue.getFilter(),
         configuration.isIgnoreSubscriptionFilters() || consumer == null ? null : consumer.getFilter());

      final Integer priority = selectPriority();

      return new AMQPBridgeReceiverInfo(ReceiverRole.QUEUE_RECEIVER,
                                        queue.getAddress().toString(),
                                        queue.getName().toString(),
                                        queue.getRoutingType(),
                                        remoteAddressBuilder.toString(),
                                        filterString,
                                        priority);
   }

   private Integer selectPriority() {
      if (configuration.isReceiverPriorityDisabled()) {
         return null;
      } else if (policy.getPriority() != null) {
         return policy.getPriority();
      } else {
         return ActiveMQDefaultConfiguration.getDefaultConsumerPriority() + policy.getPriorityAdjustment();
      }
   }

   private static String selectFilter(String policyFilter, Filter queueFilter, Filter consumerFilter) {
      if (policyFilter != null && !policyFilter.isBlank()) {
         return policyFilter;
      } else if (consumerFilter != null) {
         return consumerFilter.getFilterString().toString();
      } else {
         return queueFilter != null ? queueFilter.getFilterString().toString() : null;
      }
   }

   private static class AMQPBridgeQueueReceiverManager extends AMQPBridgeReceiverManager<ServerConsumer> {

      private final AMQPBridgeFromQueuePolicyManager manager;
      private final Queue queue;
      private final AMQPBridgeReceiverInfo receiverInfo;
      private final String receiverInfoKey;

      AMQPBridgeQueueReceiverManager(AMQPBridgeFromQueuePolicyManager manager, String receiverInfoKey, AMQPBridgeReceiverConfiguration configuration, AMQPBridgeReceiverInfo receiverInfo, Queue queue) {
         super(manager, configuration);

         this.manager = manager;
         this.queue = queue;
         this.receiverInfo = receiverInfo;
         this.receiverInfoKey = receiverInfoKey;
      }

      /**
       * @return the name of the Queue this bridge receiver manager is attached to.
       */
      public String getQueueName() {
         return queue.getName().toString();
      }

      @Override
      protected AMQPBridgeReceiver createBridgeReceiver() {
         return manager.createBridgeReceiver(receiverInfo);
      }

      @Override
      protected void whenDemandTrackingEntryAdded(ServerConsumer consumer) {
         // Attach the consumer info to the server consumer for later use on receiver close or other
         // operations that need to retrieve the data used to create the bridge receiver identity.
         consumer.addAttachment(receiverInfoKey, receiverInfo);
      }

      @Override
      protected void whenDemandTrackingEntryRemoved(ServerConsumer consumer) {
         consumer.removeAttachment(receiverInfoKey);
      }
   }
}
