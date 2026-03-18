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
package org.apache.activemq.artemis.core.server.impl;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.RouteContextList;
import org.apache.activemq.artemis.core.server.RoutingContext;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.server.mirror.MirrorController;
import org.apache.activemq.artemis.core.transaction.Transaction;

public class RoutingContextImpl implements RoutingContext {

   private final Map<SimpleString, RouteContextList> map = new HashMap<>();

   private Transaction transaction;

   private int queueCount;

   private SimpleString address;

   private SimpleString previousAddress;

   private RoutingType previousRoutingType;

   // if we wanted to bypass the load balancing configured elsewhere
   private MessageLoadBalancingType loadBalancingType;

   // To be set by the Mirror target on the server, to avoid ping pongs or reflections of messages between mirrors
   private MirrorController mirrorControllerSource;

   private RoutingType routingType;

   Boolean reusable = null;

   Boolean mirrorIgnore = null;

   boolean divertDisabled = false;

   volatile int version;

   MirrorOption mirrorOption = MirrorOption.enabled;

   private boolean duplicateDetection = true;

   private ServerSession serverSession;

   @Override
   public boolean isDuplicateDetection() {
      return duplicateDetection;
   }

   @Override
   public RoutingContextImpl setDuplicateDetection(boolean value) {
      this.duplicateDetection = value;
      return this;
   }

   public RoutingContextImpl(final Transaction transaction) {
      this.transaction = transaction;
   }

   @Override
   public MirrorOption getMirrorOption() {
      return mirrorOption;
   }

   @Override
   public boolean isDivertDisabled() {
      return divertDisabled;
   }

   @Override
   public void disableDivert() {
      divertDisabled = true;
   }

   @Override
   public boolean isMirrorDisabled() {
      return mirrorOption == MirrorOption.disabled;
   }

   @Override
   public void forEachDurable(Consumer<Queue> queueConsumer) {
      map.forEach((a, b) -> {
         b.getDurableQueues().forEach(queueConsumer);
      });
   }

   @Override
   public boolean isMirrorIndividualRoute() {
      return mirrorOption == MirrorOption.individualRoute;
   }

   @Override
   public RoutingContextImpl setMirrorOption(MirrorOption mirrorOption) {
      this.mirrorOption = mirrorOption;
      return this;
   }

   @Override
   public boolean isReusable() {
      return reusable != null && reusable;
   }

   @Override
   public boolean isMirrorIgnore() {
      return mirrorIgnore != null && mirrorIgnore;
   }


   @Override
   public int getPreviousBindingsVersion() {
      return version;
   }

   @Override
   public SimpleString getPreviousAddress() {
      return previousAddress;
   }

   @Override
   public RoutingContextImpl setReusable(boolean reusable) {
      if (this.reusable != null && !this.reusable.booleanValue()) {
         // cannot set to Reusable once it was set to false
         return this;
      }

      this.reusable = reusable;
      return this;
   }
   @Override
   public RoutingContextImpl setReusable(boolean reusable, int previousBindings) {
      this.version = previousBindings;
      this.previousAddress = address;
      this.previousRoutingType = routingType;
      if (this.reusable != null && !this.reusable.booleanValue()) {
         // cannot set to Reusable once it was set to false
         return this;
      }
      this.reusable = reusable;
      return this;
   }

   @Override
   public RoutingContextImpl clear() {
      map.clear();

      queueCount = 0;

      this.version = 0;

      this.reusable = null;

      this.mirrorIgnore = null;

      // once we set to disabled, we keep it always disabled.
      // This is because the routing object used to route commands will disable this
      // and it should stay that way no matter what
      if (mirrorOption != MirrorOption.disabled) {
         mirrorOption = MirrorOption.enabled;
      }

      return this;
   }

   @Override
   public MirrorController getMirrorSource() {
      return mirrorControllerSource;
   }

   @Override
   public RoutingContext setMirrorSource(MirrorController mirrorController) {
      this.mirrorControllerSource = mirrorController;
      return this;
   }

   @Override
   public void addQueue(final SimpleString address, final Queue queue) {

      RouteContextList listing = getContextListing(address);

      if (queue.isDurable()) {
         listing.getDurableQueues().add(queue);
      } else {
         listing.getNonDurableQueues().add(queue);
      }

      if (mirrorIgnore == null) {
         mirrorIgnore = queue.isInternalQueue() || queue.isTemporary();
      } else {
         // making sure that every queue added matches the mirrorIgnore
         mirrorIgnore = mirrorIgnore && (queue.isInternalQueue() || queue.isTemporary());
      }

      queueCount++;
   }

   @Override
   public String toString() {
      StringWriter stringWriter = new StringWriter();
      PrintWriter printWriter = new PrintWriter(stringWriter);
      printWriter.println("RoutingContextImpl(Address=" + this.address + ", routingType=" + this.routingType + ", PreviousAddress=" + previousAddress + " previousRoute:" + previousRoutingType + ", reusable=" + this.reusable + ", version=" + version + ")");
      for (Map.Entry<SimpleString, RouteContextList> entry : map.entrySet()) {
         printWriter.println("..................................................");
         printWriter.println("***** durable queues " + entry.getKey() + ":");
         for (Queue queue : entry.getValue().getDurableQueues()) {
            printWriter.println("- queueID=" + queue.getID() + " address:" + queue.getAddress() + " name:" + queue.getName() + " filter:" + queue.getFilter());
         }
         printWriter.println("***** non durable for " + entry.getKey() + ":");
         for (Queue queue : entry.getValue().getNonDurableQueues()) {
            printWriter.println("- queueID=" + queue.getID() + " address:" + queue.getAddress() + " name:" + queue.getName() + " filter:" + queue.getFilter());
         }
      }
      printWriter.println("..................................................");

      return stringWriter.toString();
   }

   @Override
   public RoutingContextImpl setLoadBalancingType(MessageLoadBalancingType messageLoadBalancingType) {
      this.loadBalancingType = messageLoadBalancingType;
      return this;
   }

   @Override
   public MessageLoadBalancingType getLoadBalancingType() {
      return loadBalancingType;
   }

   @Override
   public void addQueueWithAck(SimpleString address, Queue queue) {
      addQueue(address, queue);
      RouteContextList listing = getContextListing(address);
      listing.addAckedQueue(queue);
   }

   @Override
   public boolean isAlreadyAcked(Message message, Queue queue) {
      final RouteContextList listing = map.get(getAddress(message));
      return listing == null ? false : listing.isAlreadyAcked(queue);
   }

   @Override
   public boolean isReusable(Message message, int version) {
      if (getPreviousBindingsVersion() != version) {
         this.reusable = false;
      }
      return isReusable() && queueCount > 0 && address.equals(previousAddress) && previousRoutingType == routingType;
   }

   @Override
   public void setAddress(SimpleString address) {
      if (this.address == null || !this.address.equals(address)) {
         this.clear();
      }
      this.address = address;
   }

   @Override
   public RoutingContextImpl setRoutingType(RoutingType routingType) {
      if (this.routingType == null && routingType != null || this.routingType != routingType) {
         this.clear();
      }
      this.routingType = routingType;
      return this;
   }

   @Override
   public SimpleString getAddress(Message message) {
      if (address == null && message != null) {
         return message.getAddressSimpleString();
      }
      return address;
   }

   @Override
   public SimpleString getAddress() {
      return address;
   }

   @Override
   public RoutingType getRoutingType() {
      return routingType;
   }

   @Override
   public RoutingType getPreviousRoutingType() {
      return previousRoutingType;
   }

   @Override
   public RouteContextList getContextListing(SimpleString address) {
      RouteContextList listing = map.get(address);
      if (listing == null) {
         listing = new ContextListing();
         map.put(address, listing);
      }
      return listing;
   }

   @Override
   public Transaction getTransaction() {
      return transaction;
   }

   @Override
   public void setTransaction(final Transaction tx) {
      transaction = tx;
   }

   @Override
   public List<Queue> getNonDurableQueues(SimpleString address) {
      return getContextListing(address).getNonDurableQueues();
   }

   @Override
   public List<Queue> getDurableQueues(SimpleString address) {
      return getContextListing(address).getDurableQueues();
   }

   @Override
   public RoutingContextImpl setServerSession(ServerSession session) {
      this.serverSession = session;
      return this;
   }

   @Override
   public ServerSession getServerSession() {
      return serverSession;
   }

   @Override
   public int getQueueCount() {
      return queueCount;
   }

   @Override
   public Map<SimpleString, RouteContextList> getContexListing() {
      return this.map;
   }

   public static class ContextListing implements RouteContextList {

      private final List<Queue> durableQueue = new ArrayList<>(1);

      private final List<Queue> nonDurableQueue = new ArrayList<>(1);

      private final List<Queue> ackedQueues = new ArrayList<>();

      @Override
      public int getNumberOfDurableQueues() {
         return durableQueue.size();
      }

      @Override
      public int getNumberOfNonDurableQueues() {
         return nonDurableQueue.size();
      }

      @Override
      public List<Queue> getDurableQueues() {
         return durableQueue;
      }

      @Override
      public List<Queue> getNonDurableQueues() {
         return nonDurableQueue;
      }

      @Override
      public void addAckedQueue(Queue queue) {
         ackedQueues.add(queue);
      }

      @Override
      public boolean isAlreadyAcked(Queue queue) {
         return ackedQueues.contains(queue);
      }
   }

}
