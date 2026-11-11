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
package org.apache.activemq.artemis.core.server.federation.address;

import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.apache.activemq.artemis.core.config.federation.FederationAddressPolicyConfiguration;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.QueueBinding;
import org.apache.activemq.artemis.core.postoffice.impl.DivertBinding;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.federation.FederatedAbstract;
import org.apache.activemq.artemis.core.server.federation.FederatedConsumerKey;
import org.apache.activemq.artemis.core.server.federation.Federation;
import org.apache.activemq.artemis.core.server.federation.FederationUpstream;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerAddressPlugin;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerBindingPlugin;
import org.apache.activemq.artemis.core.server.transformer.Transformer;
import org.apache.activemq.artemis.core.settings.impl.Match;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.utils.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Federated Address, replicate messages from the remote brokers address to itself.
 * <p>
 * Only when a queue exists on the local broker do we replicate, this is to avoid un-needed replication
 * <p>
 * All messages are replicated, this is on purpose so should a number queues exist with different filters we dont have
 * have a consumer per queue filter.
 */
public class FederatedAddress extends FederatedAbstract implements ActiveMQServerBindingPlugin, ActiveMQServerAddressPlugin, Serializable {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final String FEDERATED_QUEUE_PREFIX = "federated";

   public static final SimpleString HDR_HOPS = SimpleString.of("_AMQ_Hops");
   private final SimpleString queueNameFormat;
   private final SimpleString filterString;
   private final Set<Matcher> includes;
   private final Set<Matcher> excludes;
   private final FederationAddressPolicyConfiguration config;
   private final Map<DivertBinding, Set<SimpleString>> matchingDiverts = new HashMap<>();
   private final boolean hasPullConnectionConfig;

   public FederatedAddress(Federation federation, FederationAddressPolicyConfiguration config, ActiveMQServer server, FederationUpstream upstream) {
      super(federation, server, upstream);
      Objects.requireNonNull(config.getName());
      this.config = config;
      if (config.getMaxHops() == -1) {
         this.filterString = null;
      } else {
         this.filterString = HDR_HOPS.concat(" IS NULL OR ").concat(HDR_HOPS).concat("<").concat(Integer.toString(config.getMaxHops()));
      }
      this.queueNameFormat = SimpleString.of(FEDERATED_QUEUE_PREFIX + ".${federation}.${upstream}.${address}.${routeType}");
      if (config.getIncludes().isEmpty()) {
         includes = Collections.emptySet();
      } else {
         includes = new HashSet<>(config.getIncludes().size());
         for (FederationAddressPolicyConfiguration.Matcher include : config.getIncludes()) {
            includes.add(new Matcher(include, wildcardConfiguration));
         }
      }

      if (config.getExcludes().isEmpty()) {
         excludes = Collections.emptySet();
      } else {
         excludes = new HashSet<>(config.getExcludes().size());
         for (FederationAddressPolicyConfiguration.Matcher exclude : config.getExcludes()) {
            excludes.add(new Matcher(exclude, wildcardConfiguration));
         }
      }
      hasPullConnectionConfig = upstream.getConnection().isPull();
   }

   @Override
   public synchronized void start() {
      if (!isStarted()) {
         super.start();
         server.getPostOffice()
             .getAllBindings()
             .filter(b -> b instanceof QueueBinding || b instanceof DivertBinding)
             .forEach(this::afterAddBinding);
      }
   }

   private void conditionalCreateRemoteConsumer(Queue queue) {
      if (server.hasBrokerFederationPlugins()) {
         final AtomicBoolean conditionalCreate = new AtomicBoolean(true);
         try {
            server.callBrokerFederationPlugins(plugin -> {
               conditionalCreate.set(conditionalCreate.get() && plugin.federatedAddressConditionalCreateConsumer(queue));
            });
         } catch (ActiveMQException t) {
            ActiveMQServerLogger.LOGGER.federationPluginExecutionError("federatedAddressConditionalCreateConsumer", t);
            throw new IllegalStateException(t.getMessage(), t.getCause());
         }
         if (!conditionalCreate.get()) {
            return;
         }
      }
      createRemoteConsumer(queue);
   }

   @Override
   public void afterAddAddress(AddressInfo addressInfo, boolean reload) {
      if (match(addressInfo)) {
         try {
            //Diverts can be added without the source address existing yet so
            //if a new address is added we need to see if there are matching divert bindings
            server.getPostOffice()
               .getDirectBindings(addressInfo.getName())
               .stream().filter(binding -> binding instanceof DivertBinding)
               .forEach(this::afterAddBinding);
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.federationBindingsLookupError(addressInfo.getName(), e);
         }
      }
   }

   @Override
   public void afterAddBinding(Binding binding) {
      if (binding instanceof QueueBinding queueBinding) {
         conditionalCreateRemoteConsumer(queueBinding.getQueue());

         if (config.isEnableDivertBindings()) {
            synchronized (this) {
               for (Map.Entry<DivertBinding, Set<SimpleString>> entry : matchingDiverts.entrySet()) {
                  //for each divert check the new QueueBinding to see if the divert matches and is not already tracking
                  if (!entry.getValue().contains(queueBinding.getQueue().getName())) {
                     //conditionalCreateRemoteConsumer will check if the queue is a target of the divert before adding
                     conditionalCreateRemoteConsumer(entry.getKey(), entry.getValue(), queueBinding);
                  }
               }
            }
         }
      } else if (config.isEnableDivertBindings() && binding instanceof DivertBinding divertBinding) {
         final AddressInfo addressInfo = server.getPostOffice().getAddressInfo(binding.getAddress());

         synchronized (this) {
            if (match(addressInfo) && matchingDiverts.get(divertBinding) == null) {
               final Set<SimpleString> matchingQueues = new HashSet<>();
               matchingDiverts.put(divertBinding, matchingQueues);

               //find existing matching queue bindings for the divert to create consumers for
               final SimpleString forwardAddress = divertBinding.getDivert().getForwardAddress();
               try {
                  //create demand for each matching queue binding that isn't already tracked by the divert
                  //conditionalCreateRemoteConsumer will check if the queue is a target of the divert before adding
                  server.getPostOffice().getBindingsForAddress(forwardAddress).getBindings()
                     .stream().filter(b -> b instanceof QueueBinding).map(b -> (QueueBinding) b)
                     .forEach(queueBinding -> conditionalCreateRemoteConsumer(divertBinding, matchingQueues, queueBinding));
               } catch (Exception e) {
                  ActiveMQServerLogger.LOGGER.federationBindingsLookupError(forwardAddress, e);
               }
            }
         }
      }
   }

   private void conditionalCreateRemoteConsumer(DivertBinding divertBinding, Set<SimpleString> matchingQueues, QueueBinding queueBinding) {
      if (server.hasBrokerFederationPlugins()) {
         final AtomicBoolean conditionalCreate = new AtomicBoolean(true);
         try {
            server.callBrokerFederationPlugins(plugin -> {
               conditionalCreate.set(conditionalCreate.get() && plugin.federatedAddressConditionalCreateDivertConsumer(divertBinding, queueBinding));
            });
         } catch (ActiveMQException t) {
            ActiveMQServerLogger.LOGGER.federationPluginExecutionError("federatedAddressConditionalCreateDivertConsumer", t);
            throw new IllegalStateException(t.getMessage(), t.getCause());
         }
         if (!conditionalCreate.get()) {
            return;
         }
      }
      createRemoteConsumer(divertBinding, matchingQueues, queueBinding);
   }

   private void createRemoteConsumer(DivertBinding divertBinding, final Set<SimpleString> matchingQueues, QueueBinding queueBinding) {
      final AddressInfo addressInfo = server.getPostOffice().getAddressInfo(divertBinding.getAddress());

      //If the divert address matches and if the new queueBinding matches the forwarding address of the divert
      //then create a remote consumer if not already being tracked by the divert
      if (match(addressInfo) && queueBinding.getAddress().equals(divertBinding.getDivert().getForwardAddress())
         && matchingQueues.add(queueBinding.getQueue().getName())) {
         FederatedConsumerKey key = getKey(addressInfo);
         Transformer transformer = getTransformer(config.getTransformerRef());
         Transformer addHop = FederatedAddress::addHop;
         createRemoteConsumer(key, mergeTransformers(addHop, transformer), clientSession -> createRemoteQueue(clientSession, key));
      }
   }

   @Override
   public void beforeRemoveBinding(SimpleString uniqueName, Transaction tx, boolean deleteData) {
      final Binding binding = server.getPostOffice().getBinding(uniqueName);
      if (binding instanceof QueueBinding queueBinding) {
         final Queue queue = queueBinding.getQueue();

         //Remove any direct queue demand
         removeRemoteConsumer(getKey(queue));

         if (config.isEnableDivertBindings()) {
            //See if there is any matching diverts that match this queue binding and remove demand now that
            //the queue is going away
            synchronized (this) {
               matchingDiverts.entrySet().forEach(entry -> {
                  if (entry.getKey().getDivert().getForwardAddress().equals(queue.getAddress())) {
                     final AddressInfo addressInfo = server.getPostOffice().getAddressInfo(entry.getKey().getAddress());
                     //check if the queue has been tracked by this divert and if so remove the consumer
                     if (entry.getValue().remove(queue.getAddress())) {
                        removeRemoteConsumer(getKey(addressInfo));
                     }
                  }
               });
            }
         }
      } else if (config.isEnableDivertBindings() && binding instanceof DivertBinding divertBinding) {
         final SimpleString forwardAddress = divertBinding.getDivert().getForwardAddress();

         //Check if we have added this divert binding as a matching binding
         //If we have then we need to look for any still existing queue bindings that map to this divert
         //and remove consumers if they haven't already been removed
         synchronized (this) {
            final Set<SimpleString> matchingQueues;
            if ((matchingQueues = matchingDiverts.remove(binding)) != null) {
               try {
                  final AddressInfo addressInfo = server.getPostOffice().getAddressInfo(binding.getAddress());
                  if (addressInfo != null) {
                     //remove queue binding demand if tracked by the divert
                     server.getPostOffice().getBindingsForAddress(forwardAddress)
                        .getBindings().stream().filter(b -> b instanceof QueueBinding qb && matchingQueues.remove(qb.getQueue().getName()))
                        .forEach(queueBinding -> removeRemoteConsumer(getKey(addressInfo)));
                  }
               } catch (Exception e) {
                  ActiveMQServerLogger.LOGGER.federationBindingsLookupError(forwardAddress, e);
               }
            }
         }
      }
   }

   public FederationAddressPolicyConfiguration getConfig() {
      return config;
   }

   private void createRemoteConsumer(Queue queue) {
      if (match(queue)) {
         FederatedConsumerKey key = getKey(queue);
         Transformer transformer = getTransformer(config.getTransformerRef());
         Transformer addHop = FederatedAddress::addHop;
         createRemoteConsumer(key, mergeTransformers(addHop, transformer), clientSession -> createRemoteQueue(clientSession, key));
      }
   }

   private void createRemoteQueue(ClientSession clientSession, FederatedConsumerKey key) throws ActiveMQException {
      if (!clientSession.queueQuery(key.getQueueName()).isExists()) {
         clientSession.createQueue(QueueConfiguration.of(key.getQueueName())
                                      .setAddress(key.getAddress())
                                      .setRoutingType(key.getRoutingType())
                                      .setFilterString(key.getQueueFilterString())
                                      .setDurable(true)
                                      .setAutoDelete(Objects.requireNonNullElse(config.getAutoDelete(), true))
                                      .setAutoDeleteDelay(Objects.requireNonNullElse(config.getAutoDeleteDelay(), TimeUnit.HOURS.toMillis(1)))
                                      .setAutoDeleteMessageCount(Objects.requireNonNullElse(config.getAutoDeleteMessageCount(), -1L))
                                      .setMaxConsumers(-1)
                                      .setPurgeOnNoConsumers(false)
                                      .setAutoCreated(false));
      }
   }

   private boolean match(Queue queue) {
      return match(queue.getAddress(), queue.getRoutingType());
   }

   private boolean match(AddressInfo addressInfo) {
      return addressInfo != null ? match(addressInfo.getName(), addressInfo.getRoutingType()) : false;
   }

   private boolean match(SimpleString address, RoutingType routingType) {
      if (RoutingType.ANYCAST.equals(routingType)) {
         logger.debug("ignoring unsupported ANYCAST address {}", address);
         return false;
      }
      if (hasPullConnectionConfig) {
         // multicast address federation has no local queue to trigger batch pull requests, a regular fast consumer with credit window is necessary
         // otherwise the upstream would fill up and block.
         logger.debug("ignoring MULTICAST address {} on unsupported pull connection, consumerWindowSize=0 ", address);
         return false;
      }
      for (Matcher exclude : excludes) {
         if (exclude.test(address.toString())) {
            return false;
         }
      }
      if (includes.isEmpty()) {
         return true;
      } else {
         for (Matcher include : includes) {
            if (include.test(address.toString())) {
               return true;
            }
         }
         return false;
      }
   }

   private static Message addHop(Message message) {
      if (message != null) {
         int hops = toInt(message.getExtraBytesProperty(HDR_HOPS));
         hops++;
         message.putExtraBytesProperty(HDR_HOPS, ByteUtil.intToBytes(hops));
      }
      return message;
   }

   private static int toInt(byte[] bytes) {
      if (bytes != null && bytes.length == 4) {
         return ByteUtil.bytesToInt(bytes);
      } else {
         return 0;
      }
   }

   private FederatedConsumerKey getKey(Queue queue) {
      return new FederatedAddressConsumerKey(federation.getName(), upstream.getName(), queue.getAddress(), queue.getRoutingType(), queueNameFormat, filterString);
   }

   private FederatedConsumerKey getKey(AddressInfo address) {
      return new FederatedAddressConsumerKey(federation.getName(), upstream.getName(), address.getName(), address.getRoutingType(), queueNameFormat, filterString);
   }

   public static class Matcher {
      Predicate<String> addressPredicate;

      Matcher(FederationAddressPolicyConfiguration.Matcher config, WildcardConfiguration wildcardConfiguration) {
         if (config.getAddressMatch() != null && !config.getAddressMatch().isEmpty()) {
            addressPredicate = new Match<>(config.getAddressMatch(), null, wildcardConfiguration).getPattern().asPredicate();
         }
      }

      public boolean test(String address) {
         return addressPredicate == null || addressPredicate.test(address);
      }
   }
}
