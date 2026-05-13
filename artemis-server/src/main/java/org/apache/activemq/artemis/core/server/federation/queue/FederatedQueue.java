/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.server.federation.queue;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.filter.impl.FilterImpl;
import org.apache.activemq.artemis.core.postoffice.QueueBinding;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.core.config.federation.FederationQueuePolicyConfiguration;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.server.federation.FederatedAbstract;
import org.apache.activemq.artemis.core.server.federation.FederatedConsumerKey;
import org.apache.activemq.artemis.core.server.federation.FederatedQueueConsumer;
import org.apache.activemq.artemis.core.server.federation.Federation;
import org.apache.activemq.artemis.core.server.federation.FederationUpstream;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerConsumerPlugin;
import org.apache.activemq.artemis.core.server.transformer.Transformer;
import org.apache.activemq.artemis.core.settings.impl.Match;

/**
 * Federated Queue, connect to upstream queues routing them to the local queue when a local consumer exist.
 * <p>
 * By default we connect to -1 the current consumer priority on the remote broker, so that if consumers also exist on
 * the remote broker they a dispatched to first. This though is configurable to change this behaviour.
 */
public class FederatedQueue extends FederatedAbstract implements ActiveMQServerConsumerPlugin, Serializable {

   private final Set<Matcher> includes;
   private final Set<Matcher> excludes;
   private final Filter metaDataFilter;
   private final int priorityAdjustment;

   private final FederationQueuePolicyConfiguration config;

   public FederatedQueue(Federation federation, FederationQueuePolicyConfiguration config, ActiveMQServer server, FederationUpstream federationUpstream) throws ActiveMQException {
      super(federation, server, federationUpstream);
      Objects.requireNonNull(config.getName());
      this.config = config;
      this.priorityAdjustment = federationUpstream.getPriorityAdjustment() + Objects.requireNonNullElse(config.getPriorityAdjustment(), -1);
      String metaDataFilterString = config.isIncludeFederated() ? null : "hyphenated_props:" + FederatedQueueConsumer.FEDERATION_NAME + " IS NOT NULL";
      metaDataFilter = FilterImpl.createFilter(metaDataFilterString);
      if (config.getIncludes().isEmpty()) {
         includes = Collections.emptySet();
      } else {
         includes = new HashSet<>(config.getIncludes().size());
         for (FederationQueuePolicyConfiguration.Matcher include : config.getIncludes()) {
            includes.add(new Matcher(include, wildcardConfiguration));
         }
      }

      if (config.getExcludes().isEmpty()) {
         excludes = Collections.emptySet();
      } else {
         excludes = new HashSet<>(config.getExcludes().size());
         for (FederationQueuePolicyConfiguration.Matcher exclude : config.getExcludes()) {
            excludes.add(new Matcher(exclude, wildcardConfiguration));
         }
      }
   }

   @Override
   public void start() {
      super.start();
      server.getPostOffice()
            .getAllBindings()
            .filter(b -> b instanceof QueueBinding)
            .map(b -> (QueueBinding) b)
            .forEach(b -> conditionalCreateRemoteConsumer(b.getQueue()));
   }

   /**
    * After a consumer has been created
    *
    * @param consumer the created consumer
    */
   @Override
   public synchronized void afterCreateConsumer(ServerConsumer consumer) {
      conditionalCreateRemoteConsumer(consumer);
   }

   public FederationQueuePolicyConfiguration getConfig() {
      return config;
   }

   private void conditionalCreateRemoteConsumer(ServerConsumer consumer) {
      if (server.hasBrokerFederationPlugins()) {
         final AtomicBoolean conditionalCreate = new AtomicBoolean(true);
         try {
            server.callBrokerFederationPlugins(plugin -> {
               conditionalCreate.set(conditionalCreate.get() && plugin.federatedQueueConditionalCreateConsumer(consumer));
            });
         } catch (ActiveMQException t) {
            ActiveMQServerLogger.LOGGER.federationPluginExecutionError("federatedQueueConditionalCreateConsumer", t);
            throw new IllegalStateException(t.getMessage(), t.getCause());
         }
         if (!conditionalCreate.get()) {
            return;
         }
      }
      createRemoteConsumer(consumer);
   }

   private void conditionalCreateRemoteConsumer(Queue queue) {
      queue.getConsumers()
            .stream()
            .filter(consumer -> consumer instanceof ServerConsumer)
            .map(c -> (ServerConsumer) c).forEach(this::conditionalCreateRemoteConsumer);
   }

   private void createRemoteConsumer(ServerConsumer consumer) {

      //We check the session meta data to see if its a federation session, if so by default we ignore these.
      //To not ignore these, set include-federated to true, which will mean no meta data filter.
      ServerSession serverSession = server.getSessionByID(consumer.getSessionID());
      if (metaDataFilter != null && serverSession != null && serverSession.getMetaData() != null &&
          metaDataFilter.match(serverSession.getMetaData())) {
         return;
      }
      if (match(consumer)) {
         FederatedConsumerKey key = getKey(consumer);
         Transformer transformer = getTransformer(config.getTransformerRef());
         Transformer fqqnTransformer = message -> message == null ? null : message.setAddress(key.getFqqn());
         createRemoteConsumer(key, mergeTransformers(fqqnTransformer, transformer), null);
      }
   }

   private boolean match(ServerConsumer consumer) {
      for (Matcher exclude : excludes) {
         if (exclude.test(consumer)) {
            return false;
         }
      }
      if (includes.isEmpty()) {
         return true;
      } else {
         for (Matcher include : includes) {
            if (include.test(consumer)) {
               return true;
            }
         }
         return false;
      }
   }

   /**
    * Before a consumer is closed
    */
   @Override
   public synchronized void beforeCloseConsumer(ServerConsumer consumer, boolean failed) {
      FederatedConsumerKey key = getKey(consumer);
      removeRemoteConsumer(key);
   }

   private FederatedConsumerKey getKey(ServerConsumer consumer) {
      Queue queue = consumer.getQueue();
      int priority = consumer.getPriority() + priorityAdjustment;
      return new FederatedQueueConsumerKey(queue.getAddress(), queue.getRoutingType(), queue.getName(), Filter.toFilterString(queue.getFilter()), Filter.toFilterString(consumer.getFilter()), priority);
   }

   public static class Matcher {

      Predicate<String> queuePredicate;
      Predicate<String> addressPredicate;

      Matcher(FederationQueuePolicyConfiguration.Matcher config, WildcardConfiguration wildcardConfiguration) {
         if (config.getQueueMatch() != null && !config.getQueueMatch().isEmpty()) {
            queuePredicate = new Match<>(config.getQueueMatch(), null, wildcardConfiguration).getPattern().asPredicate();
         }
         if (config.getAddressMatch() != null && !config.getAddressMatch().isEmpty()) {
            addressPredicate = new Match<>(config.getAddressMatch(), null, wildcardConfiguration).getPattern().asPredicate();
         }
      }

      public boolean test(ServerConsumer consumer) {
         return (queuePredicate == null || queuePredicate.test(consumer.getQueueName().toString()))
               && (addressPredicate == null || addressPredicate.test(consumer.getQueueAddress().toString()));
      }

   }
}
