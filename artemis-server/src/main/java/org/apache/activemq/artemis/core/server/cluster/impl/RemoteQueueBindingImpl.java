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
package org.apache.activemq.artemis.core.server.cluster.impl;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.filter.impl.FilterImpl;
import org.apache.activemq.artemis.core.postoffice.BindingType;
import org.apache.activemq.artemis.core.server.Bindable;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.RoutingContext;
import org.apache.activemq.artemis.core.server.cluster.RemoteQueueBinding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class RemoteQueueBindingImpl implements RemoteQueueBinding {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final SimpleString address;

   private final Queue storeAndForwardQueue;

   private final SimpleString uniqueName;

   private final SimpleString routingName;

   private final long remoteQueueID;

   private final Set<Filter> filters = new HashSet<>();

   private final Map<SimpleString, Integer> filterCounts = new HashMap<>();

   private Filter queueFilter;

   private int consumerCount;

   private final SimpleString idsHeaderName;

   private final Long id;

   private final int distance;

   private final MessageLoadBalancingType messageLoadBalancingType;

   private boolean connected = true;

   public RemoteQueueBindingImpl(final long id,
                                 final SimpleString address,
                                 final SimpleString uniqueName,
                                 final SimpleString routingName,
                                 final Long remoteQueueID,
                                 final SimpleString filterString,
                                 final Queue storeAndForwardQueue,
                                 final SimpleString bridgeName,
                                 final int distance,
                                 final MessageLoadBalancingType messageLoadBalancingType) throws Exception {
      this.id = id;

      this.address = address;

      this.storeAndForwardQueue = storeAndForwardQueue;

      this.uniqueName = uniqueName;

      this.routingName = routingName;

      this.remoteQueueID = remoteQueueID;

      queueFilter = FilterImpl.createFilter(filterString);

      idsHeaderName = Message.HDR_ROUTE_TO_IDS.concat(bridgeName);

      this.distance = distance;

      this.messageLoadBalancingType = messageLoadBalancingType;
   }

   @Override
   public Long getID() {
      return id;
   }

   @Override
   public SimpleString getAddress() {
      return address;
   }

   @Override
   public Bindable getBindable() {
      return storeAndForwardQueue;
   }

   @Override
   public Queue getQueue() {
      return storeAndForwardQueue;
   }

   @Override
   public SimpleString getRoutingName() {
      return routingName;
   }

   @Override
   public SimpleString getUniqueName() {
      return uniqueName;
   }

   @Override
   public SimpleString getClusterName() {
      return uniqueName;
   }

   @Override
   public boolean isExclusive() {
      return false;
   }

   @Override
   public BindingType getType() {
      return BindingType.REMOTE_QUEUE;
   }

   @Override
   public Filter getFilter() {
      return queueFilter;
   }

   @Override
   public int getDistance() {
      return distance;
   }

   @Override
   public synchronized boolean isHighAcceptPriority(final Message message) {
      if (consumerCount <= 0 || messageLoadBalancingType.equals(MessageLoadBalancingType.OFF)) {
         return false;
      }

      if (filters.isEmpty()) {
         return true;
      } else {
         for (Filter filter : filters) {
            assert filter != null : "filters contains a null filter";
            if (filter.match(message)) {
               return true;
            }
         }
      }

      return false;
   }

   @Override
   public void unproposed(SimpleString groupID) {
   }

   @Override
   public void route(final Message message, final RoutingContext context) {
      addRouteContextToMessage(message);

      List<Queue> durableQueuesOnContext = context.getDurableQueues(storeAndForwardQueue.getAddress());

      if (!durableQueuesOnContext.contains(storeAndForwardQueue)) {
         // There can be many remote bindings for the same node, we only want to add the message once to
         // the s & f queue for that node
         context.addQueue(storeAndForwardQueue.getAddress(), storeAndForwardQueue);
      }
   }

   @Override
   public void routeWithAck(Message message, RoutingContext context) {
      addRouteContextToMessage(message);

      List<Queue> durableQueuesOnContext = context.getDurableQueues(storeAndForwardQueue.getAddress());

      if (!durableQueuesOnContext.contains(storeAndForwardQueue)) {
         // There can be many remote bindings for the same node, we only want to add the message once to
         // the s & f queue for that node
         context.addQueueWithAck(storeAndForwardQueue.getAddress(), storeAndForwardQueue);
      }
   }

   @Override
   public synchronized void addConsumer(final SimpleString filterString) throws Exception {
      if (filterString != null && !filterString.isEmpty()) {
         // There can actually be many consumers on the same queue with the same filter, so we need to maintain a ref
         // count

         Integer i = filterCounts.get(filterString);

         if (i == null) {
            filterCounts.put(filterString, 1);

            filters.add(FilterImpl.createFilter(filterString));
         } else {
            filterCounts.put(filterString, i + 1);
         }
      }

      consumerCount++;
   }

   @Override
   public synchronized void removeConsumer(final SimpleString filterString) throws Exception {
      if (filterString != null && !filterString.isEmpty()) {
         Integer i = filterCounts.get(filterString);

         if (i != null) {
            int ii = i - 1;

            if (ii == 0) {
               filterCounts.remove(filterString);

               filters.remove(FilterImpl.createFilter(filterString));
            } else {
               filterCounts.put(filterString, ii);
            }
         }
      }

      if (--consumerCount < 0) {
         consumerCount = 0;
      }

   }

   @Override
   public void reset() {
      consumerCount = 0;
      filterCounts.clear();
      filters.clear();
   }

   @Override
   public synchronized int consumerCount() {
      return consumerCount;
   }

   @Override
   public String toString() {
      return "RemoteQueueBindingImpl(" +
         (connected ? "connected" : "disconnected") + ")[address=" + address +
         ", consumerCount=" +
         consumerCount +
         ", distance=" +
         distance +
         ", filters=" +
         filters +
         ", id=" +
         id +
         ", idsHeaderName=" +
         idsHeaderName +
         ", queueFilter=" +
         queueFilter +
         ", remoteQueueID=" +
         remoteQueueID +
         ", routingName=" +
         routingName +
         ", storeAndForwardQueue=" +
         storeAndForwardQueue +
         ", uniqueName=" +
         uniqueName +
         "]";
   }

   @Override
   public String toManagementString() {
      return "RemoteQueueBindingImpl [address=" + address +
         ", storeAndForwardQueue=" + storeAndForwardQueue.getName() +
         ", remoteQueueID=" +
         remoteQueueID + "]";
   }

   @Override
   public void disconnect() {
      connected = false;
   }

   @Override
   public boolean isConnected() {
      return connected;
   }

   @Override
   public void connect() {
      connected = true;
   }

   public Set<Filter> getFilters() {
      return filters;
   }

   @Override
   public void close() throws Exception {
      storeAndForwardQueue.close();
   }

   /**
    * This will add routing information to the message. This will be later processed during the delivery between the
    * nodes. Because of that this has to be persisted as a property on the message.
    */
   private void addRouteContextToMessage(final Message message) {
      byte[] ids = message.getExtraBytesProperty(idsHeaderName);

      if (ids == null) {
         ids = new byte[8];
      } else {
         byte[] newIds = new byte[ids.length + 8];

         System.arraycopy(ids, 0, newIds, 8, ids.length);

         ids = newIds;
      }

      ByteBuffer buff = ByteBuffer.wrap(ids);

      buff.putLong(remoteQueueID);

      message.putExtraBytesProperty(idsHeaderName, ids);

      if (logger.isTraceEnabled()) {
         logger.trace("Adding remoteQueue ID = {} into message={} store-forward-queue={}", remoteQueueID, message, storeAndForwardQueue);
      }
   }

   @Override
   public long getRemoteQueueID() {
      return remoteQueueID;
   }

   @Override
   public void setFilter(Filter filter) {
      this.queueFilter = filter;
   }

   @Override
   public MessageLoadBalancingType getMessageLoadBalancingType() {
      return messageLoadBalancingType;
   }
}
