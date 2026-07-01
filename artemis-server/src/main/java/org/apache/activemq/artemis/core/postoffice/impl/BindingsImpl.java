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
package org.apache.activemq.artemis.core.postoffice.impl;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.Bindings;
import org.apache.activemq.artemis.core.postoffice.QueueBinding;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.RoutingContext;
import org.apache.activemq.artemis.core.server.RoutingContext.MirrorOption;
import org.apache.activemq.artemis.core.server.cluster.RemoteQueueBinding;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.server.group.GroupingHandler;
import org.apache.activemq.artemis.core.server.group.impl.Proposal;
import org.apache.activemq.artemis.core.server.group.impl.Response;
import org.apache.activemq.artemis.core.transaction.impl.TransactionImpl;
import org.apache.activemq.artemis.utils.CompositeAddress;
import org.apache.activemq.artemis.utils.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.BiConsumer;

public final class BindingsImpl implements Bindings {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   // This is public as we use on test assertions
   public static final int MAX_GROUP_RETRY = 10;

   private final CopyOnWriteBindings routingNameBindingMap = new CopyOnWriteBindings();

   private final Map<Long, Binding> bindingsIdMap = new ConcurrentHashMap<>();

   /**
    * This is the same as bindingsIdMap but indexed on the binding's uniqueName rather than ID. Two maps are maintained
    * to speed routing, otherwise we'd have to loop through the bindingsIdMap when routing to an FQQN.
    */
   private final Map<String, Binding> bindingsNameMap = new ConcurrentHashMap<>();

   private final Set<Binding> exclusiveBindings = new CopyOnWriteArraySet<>();

   private volatile MessageLoadBalancingType messageLoadBalancingType = MessageLoadBalancingType.OFF;

   private final GroupingHandler groupingHandler;

   private final SimpleString name;

   private final StorageManager storageManager;

   private static final AtomicInteger sequenceVersion = new AtomicInteger(Integer.MIN_VALUE);

   /**
    * This has a version about adds and removes
    */
   private volatile int version;

   private static final AtomicIntegerFieldUpdater<BindingsImpl> VERSION_UPDATER = AtomicIntegerFieldUpdater
      .newUpdater(BindingsImpl.class, "version");

   private volatile boolean hasLocal;

   public BindingsImpl(final SimpleString name, final GroupingHandler groupingHandler, StorageManager storageManager) {
      this.groupingHandler = groupingHandler;
      this.storageManager = storageManager;
      this.name = name;
   }

   @Override
   public SimpleString getName() {
      return name;
   }

   @Override
   public void setMessageLoadBalancingType(final MessageLoadBalancingType messageLoadBalancingType) {
      this.messageLoadBalancingType = messageLoadBalancingType;
   }

   @Override
   public MessageLoadBalancingType getMessageLoadBalancingType() {
      return this.messageLoadBalancingType;
   }

   @Override
   public Collection<Binding> getBindings() {
      return bindingsIdMap.values();
   }

   @Override
   public void unproposed(SimpleString groupID) {
      for (Binding binding : bindingsIdMap.values()) {
         binding.unproposed(groupID);
      }
   }

   @Override
   public Binding getBinding(String name) {
      return bindingsNameMap.get(name);
   }

   @Override
   public void addBinding(final Binding binding) {
      try {
         logger.trace("addBinding({}) being called", binding);

         if (binding.isExclusive()) {
            exclusiveBindings.add(binding);
         } else {
            routingNameBindingMap.addBindingIfAbsent(binding);
         }

         bindingsIdMap.put(binding.getID(), binding);
         bindingsNameMap.put(String.valueOf(binding.getUniqueName()), binding);

         if (binding instanceof RemoteQueueBinding remoteQueueBinding) {
            setMessageLoadBalancingType(remoteQueueBinding.getMessageLoadBalancingType());
         }
         if (logger.isTraceEnabled()) {
            logger.trace("Adding binding {} into {} bindingTable: {}", binding, this, debugBindings());
         }
      } finally {
         updated();
      }
   }

   @Override
   public void updated(QueueBinding binding) {
      updated();
   }

   private void updated() {
      VERSION_UPDATER.set(this, sequenceVersion.incrementAndGet());
      checkBindingsIdMap();
   }

   private void checkBinding(Long bindingID, Binding binding) {
      if (binding instanceof LocalQueueBinding) {
         hasLocal = true;
      }
   }

   private void checkBindingsIdMap() {
      hasLocal = false;
      bindingsIdMap.forEach(this::checkBinding);
   }

   @Override
   public Binding removeBindingByUniqueName(final SimpleString bindingUniqueName) {
      final Binding binding = bindingsNameMap.remove(String.valueOf(bindingUniqueName));
      if (binding == null) {
         return null;
      }
      try {
         if (binding.isExclusive()) {
            exclusiveBindings.remove(binding);
         } else {
            routingNameBindingMap.removeBinding(binding);
         }

         bindingsIdMap.remove(binding.getID());
         assert !bindingsNameMap.containsKey(String.valueOf(binding.getUniqueName()));

         if (logger.isTraceEnabled()) {
            logger.trace("Removing binding {} from {} bindingTable: {}", binding, this, debugBindings());
         }
         return binding;
      } finally {
         updated();
      }
   }

   @Override
   public boolean allowRedistribute() {
      return messageLoadBalancingType.equals(MessageLoadBalancingType.ON_DEMAND) || messageLoadBalancingType.equals(MessageLoadBalancingType.OFF_WITH_REDISTRIBUTION);
   }

   @Override
   public void forEach(BiConsumer<String, Binding> bindingConsumer) {
      bindingsNameMap.forEach(bindingConsumer);
   }

   @Override
   public int size() {
      return bindingsNameMap.size();
   }

   @Override
   public boolean hasLocalBinding() {
      return hasLocal;
   }


   @Override
   public Message redistribute(final Message message,
                               final Queue originatingQueue,
                               final RoutingContext context) throws Exception {
      final MessageLoadBalancingType loadBalancingType = this.messageLoadBalancingType;
      if (loadBalancingType.equals(MessageLoadBalancingType.STRICT) || loadBalancingType.equals(MessageLoadBalancingType.OFF)) {
         logger.debug("Rejecting redistribution of message={} as the loadBalancingType is {}", message, loadBalancingType);
         return null;
      }

      if (logger.isDebugEnabled()) {
         logger.debug("Redistributing message {}, originatingQueue={}, currentBindings={}", message, originatingQueue.getName(), this.name);
      }

      final SimpleString routingName = CompositeAddress.isFullyQualified(message.getAddress()) && originatingQueue.getRoutingType() == RoutingType.ANYCAST ? CompositeAddress.extractAddressName(message.getAddressSimpleString()) : originatingQueue.getName();

      final Pair<Binding[], CopyOnWriteBindings.BindingIndex> bindingsAndPosition = routingNameBindingMap.getBindings(routingName);

      if (bindingsAndPosition == null) {
         logger.debug("Rejecting message redistribution for message={} as bindingsAndPosition is null", message);
         // The value can become null if it's concurrently removed while we're iterating - this is expected
         // ConcurrentHashMap behaviour!
         return null;
      }

      final Binding[] bindings = bindingsAndPosition.getA();

      final CopyOnWriteBindings.BindingIndex bindingIndex = bindingsAndPosition.getB();

      assert bindings.length > 0;

      final int bindingsCount = bindings.length;

      int nextPosition = bindingIndex.getRedistributorIndex();

      if (nextPosition >= bindingsCount) {
         nextPosition = 0;
      }

      Binding nextBinding = null;
      for (int i = 0; i < bindingsCount; i++) {
         final Binding binding = bindings[nextPosition];
         nextPosition = moveNextPosition(nextPosition, bindingsCount);
         final Filter filter = binding.getFilter();
         final boolean highPrior = binding.isHighAcceptPriority(message);
         if (highPrior && binding.getBindable() != originatingQueue && (filter == null || filter.match(message))) {
            nextBinding = binding;
            break;
         }
      }
      if (nextBinding == null) {
         logger.debug("there was no nextBinding, returning null while redistributing message={}", message);
         return null;
      }

      // The message needs a new ID during the redistribution
      // We have to create the new ID only after we can guarantee it will be routed
      // otherwise we may leave large messages stranded in the folder
      final Message copyRedistribute = message.copy(storageManager.generateID());
      if (logger.isDebugEnabled()) {
         logger.debug("Message {} being copied as {}", message.getMessageID(), copyRedistribute.getMessageID());
      }

      copyRedistribute.setAddress(message.getAddress());
      copyRedistribute.clearInternalProperties();

      if (context.getTransaction() == null) {
         context.setTransaction(new TransactionImpl(storageManager));
      }

      bindingIndex.setRedistributorIndex(nextPosition);
      nextBinding.route(copyRedistribute, context);
      logger.debug("Redistribution successful on message={}, towards bindings={}", message, bindings);
      return copyRedistribute;
   }

   @Override
   public void route(final Message message, final RoutingContext context) throws Exception {
      route(message, context, true);
   }

   private void route(final Message message,
                      final RoutingContext context,
                      final boolean groupRouting) throws Exception {
      final int currentVersion = VERSION_UPDATER.get(this);
      final boolean reusableContext = context.isReusable(message, currentVersion);

      if (!reusableContext) {
         context.clear();
      }

      /*
       * This is a special treatment for scaled-down messages involving SnF queues.
       * See org.apache.activemq.artemis.core.server.impl.ScaleDownHandler.scaleDownMessages() for the logic that sends messages with this property
       */
      final byte[] ids = message.removeExtraBytesProperty(Message.HDR_SCALEDOWN_TO_IDS);

      if (ids != null) {
         handleScaledDownMessage(message, ids);
      }

      final boolean routed;
      // despite the double check can lead to some race, this can save allocating an iterator for an empty set
      if (!exclusiveBindings.isEmpty()) {
         routed = routeToExclusiveBindings(message, context);
      } else {
         routed = false;
      }
      if (!routed) {
         // Remove the ids now, in order to avoid double check
         final byte[] routeToIds = message.removeExtraBytesProperty(Message.HDR_ROUTE_TO_IDS);

         SimpleString groupId;
         if (routeToIds != null) {
            context.clear().setReusable(false);
            routeFromCluster(message, context, routeToIds);
         } else if (groupRouting && groupingHandler != null && (groupId = message.getGroupID()) != null) {
            context.clear().setReusable(false);
            routeUsingStrictOrdering(message, context, groupingHandler, groupId, 0);
         } else {
            // in a optimization, we are reusing the previous context if everything is right for it
            // so the simpleRouting will only happen if needed
            if (!reusableContext) {
               simpleRouting(message, context, currentVersion);
            }
         }
      }
   }

   private boolean routeToExclusiveBindings(final Message message, final RoutingContext context) throws Exception {
      boolean hasExclusives = false;
      boolean routed = false;
      for (Binding binding : exclusiveBindings) {
         if (!hasExclusives) {
            context.clear().setReusable(false);
            hasExclusives = true;
         }
         final Filter filter = binding.getFilter();
         if (filter == null || filter.match(message)) {
            if (!(binding instanceof DivertBinding && context.isDivertDisabled())) {
               binding.getBindable().route(message, context);
            }
            routed = true;
         }
      }
      return routed;
   }

   private void handleScaledDownMessage(final Message message, final byte[] ids) {
      ByteBuffer buffer = ByteBuffer.wrap(ids);
      while (buffer.hasRemaining()) {
         long id = buffer.getLong();
         for (Map.Entry<Long, Binding> entry : bindingsIdMap.entrySet()) {
            if (entry.getValue() instanceof RemoteQueueBinding remoteQueueBinding) {
               if (remoteQueueBinding.getRemoteQueueID() == id) {
                  message.putExtraBytesProperty(Message.HDR_ROUTE_TO_IDS, ByteBuffer.allocate(8).putLong(remoteQueueBinding.getID()).array());
               }
            }
         }
      }
   }

   private MessageLoadBalancingType getMessageLoadBalancingType(RoutingContext context) {
      if (context.getLoadBalancingType() != null) {
         return context.getLoadBalancingType();
      } else {
         return this.messageLoadBalancingType;
      }
   }

   private void simpleRouting(final Message message,
                              final RoutingContext context,
                              final int currentVersion) throws Exception {
      if (logger.isTraceEnabled()) {
         logger.trace("Routing message {} on binding={} current context::{}", message, this, context);
      }

      routingNameBindingMap.forEachBindings((bindings, nextPosition) -> {
         final Binding nextBinding = getNextBinding(message, bindings, nextPosition, getMessageLoadBalancingType(context));
         if (nextBinding != null && nextBinding.getFilter() == null && nextBinding.isLocal() && bindings.length == 1) {
            context.setReusable(true, currentVersion);
         } else {
            // notice that once this is set to false, any calls to setReusable(true) will be moot as the context will ignore it
            context.setReusable(false, currentVersion);
         }

         if (nextBinding != null) {
            if (!(context.isDivertDisabled() && nextBinding instanceof DivertBinding)) {
               nextBinding.route(message, context);
            }
         }
      });
   }

   @Override
   public String toString() {
      return "BindingsImpl [name=" + name + "]";
   }

   /**
    * This code has a race on the assigned value to routing names.
    * <p>
    * This is not that much of an issue because say you have the same queue name bound into two servers. The routing
    * will load balance between these two servers. This will eventually send more messages to one server than the other
    * (depending if you are using multi-thread), and not lose messages.
    */
   private Binding getNextBinding(final Message message,
                                  final Binding[] bindings,
                                  final CopyOnWriteBindings.BindingIndex bindingIndex,
                                  final MessageLoadBalancingType loadBalancingType) {
      int nextPosition = bindingIndex.getIndex();

      final int bindingsCount = bindings.length;

      if (nextPosition >= bindingsCount) {
         nextPosition = 0;
      }

      Binding nextBinding = null;
      int lastLowPriorityBinding = -1;

      for (int i = 0; i < bindingsCount; i++) {
         final Binding binding = bindings[nextPosition];
         if (matchBinding(message, binding, loadBalancingType)) {
            // bindings.length == 1 ==> only a local queue so we don't check for matching consumers (it's an
            // unnecessary overhead)
            if (bindingsCount == 1 || (binding.isConnected() && (loadBalancingType.equals(MessageLoadBalancingType.STRICT) || binding.isHighAcceptPriority(message)))) {
               nextBinding = binding;
               nextPosition = moveNextPosition(nextPosition, bindingsCount);
               break;
            }
            // When !routeWhenNoConsumers, the localQueue should always have the priority over the secondary bindings
            if (lastLowPriorityBinding == -1 || loadBalancingType.equals(MessageLoadBalancingType.ON_DEMAND) && binding instanceof LocalQueueBinding) {
               lastLowPriorityBinding = nextPosition;
            }
         }
         nextPosition = moveNextPosition(nextPosition, bindingsCount);
      }
      if (nextBinding == null) {
         // if no bindings were found, we will apply a secondary level on the routing logic
         if (lastLowPriorityBinding != -1) {
            nextBinding = bindings[lastLowPriorityBinding];
            nextPosition = moveNextPosition(lastLowPriorityBinding, bindingsCount);
         }
      }
      if (nextBinding != null) {
         bindingIndex.setIndex(nextPosition);
      }
      return nextBinding;
   }

   private static boolean matchBinding(final Message message,
                                       final Binding binding,
                                       final MessageLoadBalancingType loadBalancingType) {
      if (loadBalancingType.equals(MessageLoadBalancingType.OFF) || loadBalancingType.equals(MessageLoadBalancingType.OFF_WITH_REDISTRIBUTION)) {
         if (!Objects.equals(message.getRoutingType(), RoutingType.MULTICAST) && binding instanceof RemoteQueueBinding) {
            return false;
         }
      }

      if (loadBalancingType.equals(MessageLoadBalancingType.LOCAL_ONLY) && binding instanceof RemoteQueueBinding) {
         return false;
      }

      if (CompositeAddress.isFullyQualified(message.getAddress()) && binding instanceof QueueBinding && !UUID.stripTrailingUUID(binding.getClusterName()).equals(CompositeAddress.extractQueueName(message.getAddressSimpleString()))) {
         return false;
      }

      final Filter filter = binding.getFilter();
      if (filter == null || filter.match(message)) {
         return true;
      }
      return false;
   }

   private void routeUsingStrictOrdering(final Message message,
                                         final RoutingContext context,
                                         final GroupingHandler groupingGroupingHandler,
                                         final SimpleString groupId,
                                         final int tries) throws Exception {
      routingNameBindingMap.forEach((routingName, bindings, nextPosition) -> {
         // concat a full group id, this is for when a binding has multiple bindings
         // NOTE: In case a dev ever change this rule, QueueImpl::unproposed is using this rule to determine if
         //       the binding belongs to its Queue before removing it
         SimpleString fullID = groupId.concat(".").concat(routingName);

         // see if there is already a response
         Response resp = groupingGroupingHandler.getProposal(fullID, true);

         if (resp == null) {
            // ok let's find the next binding to propose
            Binding theBinding = getNextBinding(message, bindings, nextPosition, getMessageLoadBalancingType(context));
            if (theBinding == null) {
               return;
            }

            resp = groupingGroupingHandler.propose(new Proposal(fullID, theBinding.getClusterName()));

            if (resp == null) {
               logger.debug("it got a timeout on propose, trying again, number of retries: {}", tries);
               // it timed out, so we will check it through routeAndcheckNull
               theBinding = null;
            }

            // alternativeClusterName will be != null when by the time we looked at the cachedProposed,
            // another thread already set the proposal, so we use the new alternativeclusterName that's set there
            // if our proposal was declined find the correct binding to use
            if (resp != null && resp.getAlternativeClusterName() != null) {
               theBinding = locateBinding(resp.getAlternativeClusterName(), bindings);
            }

            routeAndCheckNull(message, context, resp, theBinding, groupId, tries);
         } else {
            // ok, we need to find the binding and route it
            Binding chosen = locateBinding(resp.getChosenClusterName(), bindings);

            routeAndCheckNull(message, context, resp, chosen, groupId, tries);
         }
      });
   }

   private static Binding locateBinding(SimpleString clusterName, Binding[] bindings) {
      for (Binding binding : bindings) {
         if (binding.getClusterName().equals(clusterName)) {
            return binding;
         }
      }

      return null;
   }

   private void routeAndCheckNull(Message message,
                                  RoutingContext context,
                                  Response resp,
                                  Binding theBinding,
                                  SimpleString groupId,
                                  int tries) throws Exception {
      // and let's route it
      if (theBinding != null) {
         theBinding.route(message, context);
      } else {
         if (resp != null) {
            groupingHandler.forceRemove(resp.getGroupId(), resp.getClusterName());
         }

         //there may be a chance that the binding has been removed from the post office before it is removed from the grouping handler.
         //in this case all we can do is remove it and try again.
         if (tries < MAX_GROUP_RETRY) {
            routeUsingStrictOrdering(message, context, groupingHandler, groupId, tries + 1);
         } else {
            ActiveMQServerLogger.LOGGER.impossibleToRouteGrouped();
            route(message, context, false);
         }
      }
   }

   private String debugBindings() {
      StringWriter writer = new StringWriter();

      PrintWriter out = new PrintWriter(writer);

      out.println("\n**************************************************");

      out.println("routingNameBindingMap:");
      if (routingNameBindingMap.isEmpty()) {
         out.println("\tEMPTY!");
      }
      routingNameBindingMap.forEach((routingName, bindings, nextPosition) -> {
         out.println("\tkey=" + routingName + ",\tposition=" + nextPosition.getIndex() + "\tvalue(s):");
         for (Binding bind : bindings) {
            out.println("\t\t" + bind);
         }
         out.println();
      });

      out.println();

      out.println("bindingsMap:");

      if (bindingsIdMap.isEmpty()) {
         out.println("\tEMPTY!");
      }
      for (Map.Entry<Long, Binding> entry : bindingsIdMap.entrySet()) {
         out.println("\tkey=" + entry.getKey() + ", value=" + entry.getValue());
      }

      out.println();

      out.println("exclusiveBindings:");
      if (exclusiveBindings.isEmpty()) {
         out.println("\tEMPTY!");
      }

      for (Binding binding : exclusiveBindings) {
         out.println("\t" + binding);
      }

      out.println("####################################################");

      return writer.toString();
   }

   private void routeFromCluster(final Message message,
                                 final RoutingContext context,
                                 final byte[] ids) throws Exception {
      if (!context.isMirrorDisabled()) {
         context.setMirrorOption(MirrorOption.individualRoute);
         context.setReusable(false);
      }
      byte[] idsToAck = (byte[]) message.removeProperty(Message.HDR_ROUTE_TO_ACK_IDS);

      List<Long> idsToAckList = new ArrayList<>();

      if (idsToAck != null) {
         ByteBuffer buff = ByteBuffer.wrap(idsToAck);
         while (buff.hasRemaining()) {
            long bindingID = buff.getLong();
            idsToAckList.add(bindingID);
         }
      }

      ByteBuffer buff = ByteBuffer.wrap(ids);

      while (buff.hasRemaining()) {
         long bindingID = buff.getLong();

         Binding binding = bindingsIdMap.get(bindingID);
         if (binding != null) {
            if (idsToAckList.contains(bindingID)) {
               binding.routeWithAck(message, context);
            } else {
               binding.route(message, context);
            }
         } else {
            ActiveMQServerLogger.LOGGER.bindingNotFound(bindingID, message.toString(), this.toString());
         }
      }
   }

   private static int moveNextPosition(int position, final int length) {
      position++;

      if (position == length) {
         position = 0;
      }

      return position;
   }

   /**
    * debug method: used just for tests!!
    */
   public Map<SimpleString, List<Binding>> getRoutingNameBindingMap() {
      return routingNameBindingMap.copyAsMap();
   }
}
