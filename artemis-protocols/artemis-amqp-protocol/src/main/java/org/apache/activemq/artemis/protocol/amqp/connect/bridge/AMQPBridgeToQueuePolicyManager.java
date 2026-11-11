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

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.postoffice.QueueBinding;
import org.apache.activemq.artemis.core.security.SecurityAuth;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerQueuePlugin;
import org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeSenderInfo.Role;
import org.apache.activemq.artemis.utils.CompositeAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AMQP Bridge policy manager that tracks local queues that match the policy configurations
 * and creates senders to the remote peer for that address until such time as the queue is
 * removed locally.
 */
public class AMQPBridgeToQueuePolicyManager extends AMQPBridgeToPolicyManager implements ActiveMQServerQueuePlugin {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final AMQPBridgeQueuePolicy policy;
   private final Map<String, AMQPBridgeQueueSenderManager> queueSenders = new HashMap<>();

   public AMQPBridgeToQueuePolicyManager(AMQPBridgeManager bridge, AMQPBridgeMetrics metrics, AMQPBridgeQueuePolicy policy) {
      super(bridge, metrics, policy.getPolicyName(), AMQPBridgeType.BRIDGE_TO_QUEUE);

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
            .filter(binding -> binding instanceof QueueBinding)
            .forEach(binding -> checkQueueForMatch(((QueueBinding) binding).getQueue()));
   }

   @Override
   protected void safeCleanupManagerResources() {
      try {
         queueSenders.forEach((k, v) -> {
            v.shutdownNow();
         });
      } finally {
         queueSenders.clear();
      }
   }

   @Override
   public synchronized void afterCreateQueue(Queue queue) throws ActiveMQException {
      if (isActive()) {
         checkQueueForMatch(queue);
      }
   }

   @Override
   public synchronized void afterDestroyQueue(Queue queue, SimpleString address, final SecurityAuth session, boolean checkConsumerCount,
                                              boolean removeConsumers, boolean autoDeleteAddress) throws ActiveMQException {
      if (isActive()) {
         final String fqqn = CompositeAddress.toFullyQualified(queue.getAddress(), queue.getName()).toString();
         final AMQPBridgeQueueSenderManager manager = queueSenders.remove(fqqn);

         if (manager != null) {
            logger.trace("Clearing sender tracking for removed bridged Queues {}", fqqn);
            manager.shutdownNow();
         }
      }
   }

   private boolean testIfQueueMatchesPolicy(String address, String queueName) {
      return policy.test(address, queueName);
   }

   protected final void checkQueueForMatch(Queue queue) {
      if (testIfQueueMatchesPolicy(queue.getAddress().toString(), queue.getName().toString())) {
         final AddressInfo addressInfo = server.getPostOffice().getAddressInfo(queue.getAddress());
         logger.trace("AMQP Bridge To Queue Policy matched on Queue: {}:{}", addressInfo, queue);

         final AMQPBridgeQueueSenderManager manager;
         final AMQPBridgeSenderInfo info = createSenderInfo(addressInfo, queue);

         if (!queueSenders.containsKey(info.getLocalFqqn())) {
            manager = new AMQPBridgeQueueSenderManager(this, configuration, info);
            queueSenders.put(info.getLocalFqqn(), manager);
            manager.start();
         }
      }
   }

   private AMQPBridgeSenderInfo createSenderInfo(AddressInfo addressInfo, Queue queue) {
      final String queueName = queue.getName().toString();
      final StringBuilder remoteAddressBuilder = new StringBuilder();

      if (policy.getRemoteAddressPrefix() != null) {
         remoteAddressBuilder.append(policy.getRemoteAddressPrefix());
      }

      if (policy.getRemoteAddress() != null && !policy.getRemoteAddress().isBlank()) {
         remoteAddressBuilder.append(policy.getRemoteAddress());
      } else {
         remoteAddressBuilder.append(queueName);
      }

      if (policy.getRemoteAddressSuffix() != null) {
         remoteAddressBuilder.append(policy.getRemoteAddressSuffix());
      }

      return new AMQPBridgeSenderInfo(Role.QUEUE_SENDER,
                                      addressInfo.getName().toString(),
                                      queueName,
                                      addressInfo.getRoutingType(),
                                      remoteAddressBuilder.toString());
   }

   private AMQPBridgeSender createBridgeSender(AMQPBridgeSenderInfo senderInfo) {
      Objects.requireNonNull(senderInfo, "AMQP Bridge Queue sender information object was null");

      if (logger.isTraceEnabled()) {
         logger.trace("AMQP Bridge {} creating Queue sender: {} for policy: {}", bridge.getName(), senderInfo, policy.getPolicyName());
      }

      // Don't initiate anything yet as the caller might need to register error handlers etc
      // before the attach is sent otherwise they could miss the failure case.
      return new AMQPBridgeToQueueSender(this, configuration, session, senderInfo, metrics.newSenderMetrics());
   }

   private static class AMQPBridgeQueueSenderManager extends AMQPBridgeSenderManager {

      private final AMQPBridgeToQueuePolicyManager manager;
      private final AMQPBridgeSenderInfo senderInfo;

      AMQPBridgeQueueSenderManager(AMQPBridgeToQueuePolicyManager manager, AMQPBridgeSenderConfiguration configuration, AMQPBridgeSenderInfo senderInfo) {
         super(manager, configuration);

         this.manager = manager;
         this.senderInfo = senderInfo;
      }

      @Override
      protected AMQPBridgeSender createBridgeSender() {
         return manager.createBridgeSender(senderInfo);
      }
   }
}
