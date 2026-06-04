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
package org.apache.activemq.artemis.core.server;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.server.mirror.MirrorController;
import org.apache.activemq.artemis.core.transaction.Transaction;

public interface RoutingContext {

   /*
     This will return true if the RoutingContext can be reused
     false if it cannot
     null, if we don't know.


     Once false, it can't be set to true
   */
   boolean isReusable();

   MirrorOption getMirrorOption();

   boolean isDivertDisabled();

   void disableDivert();

   void forEachDurable(Consumer<Queue> consumer);

   RoutingContext setMirrorOption(MirrorOption option);

   boolean isMirrorDisabled();

   boolean isMirrorIndividualRoute();

   /**
    * return true if every queue routed is internal or temporary
    */
   boolean isMirrorIgnore();

   MirrorController getMirrorSource();

   RoutingContext setMirrorSource(MirrorController mirrorController);

   int getPreviousBindingsVersion();

   SimpleString getPreviousAddress();

   RoutingContext setReusable(boolean reusable);

   RoutingContext setReusable(boolean reusable, int version);

   Transaction getTransaction();

   void setTransaction(Transaction transaction);

   void addQueue(SimpleString address, Queue queue);

   Map<SimpleString, RouteContextList> getContexListing();

   RouteContextList getContextListing(SimpleString address, PagingStore addressStore);

   List<Queue> getNonDurableQueues(SimpleString address, PagingStore addressStore);

   List<Queue> getDurableQueues(SimpleString address, PagingStore addressStore);

   int getQueueCount();

   RoutingContext clear();

   void addQueueWithAck(SimpleString address, Queue queue);

   boolean isAlreadyAcked(Message message, Queue queue);

   void setAddress(SimpleString address);

   RoutingContext setRoutingType(RoutingType routingType);

   SimpleString getAddress(Message message);

   SimpleString getAddress();

   RoutingType getRoutingType();

   RoutingType getPreviousRoutingType();

   boolean isReusable(Message message, int version);

   boolean isDuplicateDetection();

   RoutingContext setDuplicateDetection(boolean value);

   RoutingContext setLoadBalancingType(MessageLoadBalancingType messageLoadBalancingType);

   MessageLoadBalancingType getLoadBalancingType();

   RoutingContext setServerSession(ServerSession session);

   ServerSession getServerSession();

   enum MirrorOption {
      enabled,
      disabled,
      individualRoute
   }


}
