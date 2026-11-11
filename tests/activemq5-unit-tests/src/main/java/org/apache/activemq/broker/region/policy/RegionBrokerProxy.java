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
package org.apache.activemq.broker.region.policy;

import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.broker.Broker;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.Connection;
import org.apache.activemq.broker.ConnectionContext;
import org.apache.activemq.broker.ConsumerBrokerExchange;
import org.apache.activemq.broker.ProducerBrokerExchange;
import org.apache.activemq.broker.artemiswrapper.ArtemisBrokerWrapper;
import org.apache.activemq.broker.artemiswrapper.RegionProxy;
import org.apache.activemq.broker.region.Destination;
import org.apache.activemq.broker.region.MessageReference;
import org.apache.activemq.broker.region.Region;
import org.apache.activemq.broker.region.RegionBroker;
import org.apache.activemq.broker.region.Subscription;
import org.apache.activemq.broker.region.virtual.VirtualDestination;
import org.apache.activemq.command.ActiveMQDestination;
import org.apache.activemq.command.BrokerId;
import org.apache.activemq.command.BrokerInfo;
import org.apache.activemq.command.ConnectionInfo;
import org.apache.activemq.command.ConsumerControl;
import org.apache.activemq.command.ConsumerInfo;
import org.apache.activemq.command.DestinationInfo;
import org.apache.activemq.command.Message;
import org.apache.activemq.command.MessageAck;
import org.apache.activemq.command.MessageDispatch;
import org.apache.activemq.command.MessageDispatchNotification;
import org.apache.activemq.command.MessagePull;
import org.apache.activemq.command.ProducerInfo;
import org.apache.activemq.command.RemoveSubscriptionInfo;
import org.apache.activemq.command.Response;
import org.apache.activemq.command.SessionInfo;
import org.apache.activemq.command.TransactionId;
import org.apache.activemq.store.PListStore;
import org.apache.activemq.thread.Scheduler;
import org.apache.activemq.usage.Usage;
import org.mockito.Mockito;

import javax.management.MBeanServer;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;

import static org.mockito.AdditionalAnswers.delegatesTo;

public class RegionBrokerProxy implements Broker {

   private final ActiveMQServer server;
   private final MBeanServer mBeanServer;

   private RegionBrokerProxy(ArtemisBrokerWrapper wrapper) {
      this.server = wrapper.getServer();
      this.mBeanServer = wrapper.getMbeanServer();
   }

   public static RegionBroker newRegionBroker(ArtemisBrokerWrapper broker) {
      Broker brokerProxy = null;
      try {
         brokerProxy = new RegionBrokerProxy(broker);
         RegionBroker regionBroker = Mockito.mock(RegionBroker.class, delegatesTo(brokerProxy));
         return regionBroker;
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   // RegionBroker methods called by enabled tests

   public Region getTopicRegion() {
      return RegionProxy.newTopicRegion(server);
   }

   public Region getQueueRegion() {
      return RegionProxy.newQueueRegion(server);
   }

   //everything else, to satisfy the Broker interface
   //we don't actually implement (wrap) many of these for now,
   //just to make test compile pass.
   @Override
   public Broker getAdaptor(Class aClass) {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public BrokerId getBrokerId() {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public String getBrokerName() {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void addBroker(Connection connection, BrokerInfo brokerInfo) {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void removeBroker(Connection connection, BrokerInfo brokerInfo) {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void addConnection(ConnectionContext connectionContext, ConnectionInfo connectionInfo) throws Exception {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void removeConnection(ConnectionContext connectionContext, ConnectionInfo connectionInfo, Throwable throwable) throws Exception {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void addSession(ConnectionContext connectionContext, SessionInfo sessionInfo) throws Exception {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void removeSession(ConnectionContext connectionContext, SessionInfo sessionInfo) throws Exception {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public Destination addDestination(ConnectionContext connectionContext, ActiveMQDestination activeMQDestination, boolean b) throws Exception {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void removeDestination(ConnectionContext connectionContext, ActiveMQDestination activeMQDestination, long l) throws Exception {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public Map<ActiveMQDestination, Destination> getDestinationMap() {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public Subscription addConsumer(ConnectionContext connectionContext, ConsumerInfo consumerInfo) throws Exception {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void removeConsumer(ConnectionContext connectionContext, ConsumerInfo consumerInfo) throws Exception {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void addProducer(ConnectionContext connectionContext, ProducerInfo producerInfo) throws Exception {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void removeProducer(ConnectionContext connectionContext, ProducerInfo producerInfo) throws Exception {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void removeSubscription(ConnectionContext connectionContext, RemoveSubscriptionInfo removeSubscriptionInfo) throws Exception {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void send(ProducerBrokerExchange producerBrokerExchange, Message message) throws Exception {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void acknowledge(ConsumerBrokerExchange consumerBrokerExchange, MessageAck messageAck) throws Exception {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public Response messagePull(ConnectionContext connectionContext, MessagePull messagePull) throws Exception {
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
   public Set<Destination> getDestinations(ActiveMQDestination activeMQDestination) {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void processConsumerControl(ConsumerBrokerExchange consumerBrokerExchange, ConsumerControl consumerControl) {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void reapplyInterceptor() {
      throw new UnsupportedOperationException("Not implemented yet");

   }

   @Override
   public Connection[] getClients() throws Exception {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public ActiveMQDestination[] getDestinations() throws Exception {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public Map<ActiveMQDestination, Destination> getDestinationMap(ActiveMQDestination activeMQDestination) {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public TransactionId[] getPreparedTransactions(ConnectionContext connectionContext) throws Exception {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void beginTransaction(ConnectionContext connectionContext, TransactionId transactionId) throws Exception {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public int prepareTransaction(ConnectionContext connectionContext, TransactionId transactionId) throws Exception {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void rollbackTransaction(ConnectionContext connectionContext, TransactionId transactionId) throws Exception {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void commitTransaction(ConnectionContext connectionContext, TransactionId transactionId, boolean b) throws Exception {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void forgetTransaction(ConnectionContext connectionContext, TransactionId transactionId) throws Exception {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public BrokerInfo[] getPeerBrokerInfos() {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void preProcessDispatch(MessageDispatch messageDispatch) {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void postProcessDispatch(MessageDispatch messageDispatch) {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public boolean isStopped() {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public Set<ActiveMQDestination> getDurableDestinations() {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void addDestinationInfo(ConnectionContext connectionContext, DestinationInfo destinationInfo) throws Exception {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void removeDestinationInfo(ConnectionContext connectionContext, DestinationInfo destinationInfo) throws Exception {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public boolean isFaultTolerantConfiguration() {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public ConnectionContext getAdminConnectionContext() {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void setAdminConnectionContext(ConnectionContext connectionContext) {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public PListStore getTempDataStore() {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public URI getVmConnectorURI() {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void brokerServiceStarted() {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public BrokerService getBrokerService() {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public Broker getRoot() {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public boolean isExpired(MessageReference messageReference) {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void messageExpired(ConnectionContext connectionContext, MessageReference messageReference, Subscription subscription) {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public boolean sendToDeadLetterQueue(ConnectionContext connectionContext, MessageReference messageReference, Subscription subscription, Throwable throwable) {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public long getBrokerSequenceId() {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void messageConsumed(ConnectionContext connectionContext, MessageReference messageReference) {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void messageDelivered(ConnectionContext connectionContext, MessageReference messageReference) {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void messageDiscarded(ConnectionContext connectionContext, Subscription subscription, MessageReference messageReference) {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void slowConsumer(ConnectionContext connectionContext, Destination destination, Subscription subscription) {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void fastProducer(ConnectionContext connectionContext, ProducerInfo producerInfo, ActiveMQDestination activeMQDestination) {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void isFull(ConnectionContext connectionContext, Destination destination, Usage usage) {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void virtualDestinationAdded(ConnectionContext connectionContext, VirtualDestination virtualDestination) {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void virtualDestinationRemoved(ConnectionContext connectionContext, VirtualDestination virtualDestination) {
      throw new UnsupportedOperationException("Not implemented yet");

   }

   @Override
   public void messageDispatched(ConnectionContext context, Subscription sub, MessageReference messageReference) {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void messageNoConsumers(ConnectionContext context, MessageReference messageReference) {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void nowMasterBroker() {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public Scheduler getScheduler() {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public ThreadPoolExecutor getExecutor() {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void networkBridgeStarted(BrokerInfo brokerInfo, boolean b, String s) {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void networkBridgeStopped(BrokerInfo brokerInfo) {
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
