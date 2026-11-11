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
package org.apache.activemq.broker.artemiswrapper;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.postoffice.QueueBinding;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.broker.ConnectionContext;
import org.apache.activemq.broker.ConsumerBrokerExchange;
import org.apache.activemq.broker.ProducerBrokerExchange;
import org.apache.activemq.broker.region.Destination;
import org.apache.activemq.broker.region.QueueRegion;
import org.apache.activemq.broker.region.Region;
import org.apache.activemq.broker.region.Subscription;
import org.apache.activemq.broker.region.TopicRegion;
import org.apache.activemq.broker.region.policy.DestinationProxy;
import org.apache.activemq.command.ActiveMQDestination;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.command.ConsumerControl;
import org.apache.activemq.command.ConsumerInfo;
import org.apache.activemq.command.Message;
import org.apache.activemq.command.MessageAck;
import org.apache.activemq.command.MessageDispatchNotification;
import org.apache.activemq.command.MessagePull;
import org.apache.activemq.command.ProducerInfo;
import org.apache.activemq.command.RemoveSubscriptionInfo;
import org.apache.activemq.command.Response;
import org.mockito.AdditionalAnswers;
import org.mockito.Mockito;

public class RegionProxy implements Region {
   private final ActiveMQServer server;
   private final RoutingType routingType;

   private RegionProxy(ActiveMQServer activeMQServer, RoutingType routingType) {
      this.server = activeMQServer;
      this.routingType = routingType;
   }

   public static Region newQueueRegion(ActiveMQServer activeMQServer) {
      return Mockito.mock(QueueRegion.class, AdditionalAnswers.delegatesTo(new RegionProxy(activeMQServer, RoutingType.ANYCAST)));
   }

   public static Region newTopicRegion(ActiveMQServer activeMQServer) {
      return Mockito.mock(TopicRegion.class, AdditionalAnswers.delegatesTo(new RegionProxy(activeMQServer, RoutingType.MULTICAST)));
   }

   @Override
   public Destination addDestination(ConnectionContext context, ActiveMQDestination destination, boolean createIfTemporary) throws Exception {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void removeDestination(ConnectionContext context, ActiveMQDestination destination, long timeout) throws Exception {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public Map<ActiveMQDestination, Destination> getDestinationMap() {
      return server.getPostOffice().getAllBindings()
         .filter(QueueBinding.class::isInstance)
         .filter(e -> {
               final SimpleString address = ((QueueBinding) e).getQueue().getAddress();
               return server.getAddressInfo(address).getRoutingType() == routingType;
            }
         )
         .collect(Collectors.toMap(
            e -> {
               final String uniqueName = e.getUniqueName().toString();
               return new ActiveMQQueue(uniqueName);
            },
            e -> {
               final Queue queue = ((QueueBinding) e).getQueue();
               final String address = e.getAddress().toString();
               return new DestinationProxy(queue, address, server);
            }));
   }

   @Override
   public Subscription addConsumer(ConnectionContext context, ConsumerInfo info) throws Exception {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void removeConsumer(ConnectionContext context, ConsumerInfo info) throws Exception {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void addProducer(ConnectionContext context, ProducerInfo info) throws Exception {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void removeProducer(ConnectionContext context, ProducerInfo info) throws Exception {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void removeSubscription(ConnectionContext context, RemoveSubscriptionInfo info) throws Exception {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void send(ProducerBrokerExchange producerExchange, Message message) throws Exception {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void acknowledge(ConsumerBrokerExchange consumerExchange, MessageAck ack) throws Exception {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public Response messagePull(ConnectionContext context, MessagePull pull) throws Exception {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void processDispatchNotification(MessageDispatchNotification messageDispatchNotification) throws Exception {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void gc() {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public Set<Destination> getDestinations(ActiveMQDestination destination) {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void processConsumerControl(ConsumerBrokerExchange consumerExchange, ConsumerControl control) {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void reapplyInterceptor() {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void start() throws Exception {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void stop() throws Exception {
      throw new UnsupportedOperationException("Not implemented yet");
   }
}
