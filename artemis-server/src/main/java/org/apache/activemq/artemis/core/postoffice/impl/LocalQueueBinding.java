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

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.postoffice.BindingType;
import org.apache.activemq.artemis.core.postoffice.QueueBinding;
import org.apache.activemq.artemis.core.server.Bindable;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.RoutingContext;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class LocalQueueBinding implements QueueBinding {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final SimpleString address;

   private final Queue queue;

   private final SimpleString clusterName;

   private SimpleString name;

   public LocalQueueBinding(final SimpleString address, final Queue queue, final SimpleString nodeID) {
      this.address = address;

      this.queue = queue;

      this.name = queue.getName();

      clusterName = queue.getName().concat(nodeID);
   }

   @Override
   public boolean isLocal() {
      return true;
   }

   @Override
   public Long getID() {
      return queue.getID();
   }

   @Override
   public Filter getFilter() {
      return queue.getFilter();
   }

   @Override
   public SimpleString getAddress() {
      return address;
   }

   @Override
   public Bindable getBindable() {
      return queue;
   }

   @Override
   public Queue getQueue() {
      return queue;
   }

   @Override
   public SimpleString getRoutingName() {
      if (queue.getRoutingType() == RoutingType.ANYCAST) {
         return address;
      }
      return name;
   }

   @Override
   public SimpleString getUniqueName() {
      return queue.getName();
   }

   @Override
   public SimpleString getClusterName() {
      return clusterName;
   }

   @Override
   public boolean isExclusive() {
      return false;
   }

   @Override
   public int getDistance() {
      return 0;
   }

   @Override
   public boolean isHighAcceptPriority(final Message message) {
      // It's a high accept priority if the queue has at least one matching consumer

      return queue.hasMatchingConsumer(message);
   }

   @Override
   public void unproposed(SimpleString groupID) {
      queue.unproposed(groupID);
   }

   @Override
   public void route(final Message message, final RoutingContext context) throws Exception {
      if (isMatchRoutingType(context)) {
         logger.trace("adding routing {} on message {}", queue.getID(), message);
         queue.route(message, context);
      } else {
         logger.trace("routing {} is ignored as routing type did not match", queue.getID());
      }
   }

   @Override
   public void routeWithAck(Message message, RoutingContext context) throws Exception {
      if (isMatchRoutingType(context)) {
         logger.trace("Message {} routed with ack on queue {}", message, queue.getID());
         queue.routeWithAck(message, context);
      }
   }

   private boolean isMatchRoutingType(RoutingContext context) {
      return (context.getRoutingType() == null || context.getRoutingType() == queue.getRoutingType());
   }

   public boolean isQueueBinding() {
      return true;
   }

   @Override
   public int consumerCount() {
      return queue.getConsumerCount();
   }

   @Override
   public BindingType getType() {
      return BindingType.LOCAL_QUEUE;
   }

   @Override
   public void close() throws Exception {
      queue.close();
   }

   @Override
   public String toString() {
      return "LocalQueueBinding [address=" + address +
         ", queue=" +
         queue +
         ", filter=" +
         getFilter() +
         ", name=" +
         name +
         ", clusterName=" +
         clusterName +
         "]";
   }

   @Override
   public String toManagementString() {
      return this.getClass().getSimpleName() + " [address=" + address + ", queue=" + queue + "]";
   }

   @Override
   public boolean isConnected() {
      return true;
   }
}
