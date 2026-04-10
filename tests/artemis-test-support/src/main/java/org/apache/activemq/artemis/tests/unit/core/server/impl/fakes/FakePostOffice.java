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
package org.apache.activemq.artemis.tests.unit.core.server.impl.fakes;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.Bindings;
import org.apache.activemq.artemis.core.postoffice.DuplicateIDCache;
import org.apache.activemq.artemis.core.postoffice.PostOffice;
import org.apache.activemq.artemis.core.postoffice.QueueBinding;
import org.apache.activemq.artemis.core.postoffice.RoutingStatus;
import org.apache.activemq.artemis.core.postoffice.impl.DuplicateIDCaches;
import org.apache.activemq.artemis.core.postoffice.impl.LocalQueueBinding;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.RoutingContext;
import org.apache.activemq.artemis.core.server.impl.AckReason;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.impl.MessageReferenceImpl;
import org.apache.activemq.artemis.core.server.mirror.MirrorController;
import org.apache.activemq.artemis.core.transaction.Transaction;

public class FakePostOffice implements PostOffice {

   @Override
   public QueueBinding updateQueue(SimpleString name,
                                   RoutingType routingType,
                                   Filter filter,
                                   Integer maxConsumers,
                                   Boolean purgeOnNoConsumers,
                                   Boolean exclusive,
                                   Boolean groupRebalance,
                                   Integer groupBuckets,
                                   SimpleString groupFirstKey,
                                   Boolean lastValue,
                                   Integer consumersBeforeDispatch,
                                   Long delayBeforeDispatch,
                                   SimpleString user,
                                   Boolean configurationManaged) throws Exception {
      return null;
   }

   @Override
   public QueueBinding updateQueue(SimpleString name,
                                   RoutingType routingType,
                                   Filter filter,
                                   Integer maxConsumers,
                                   Boolean purgeOnNoConsumers,
                                   Boolean exclusive,
                                   Boolean groupRebalance,
                                   Integer groupBuckets,
                                   SimpleString groupFirstKey,
                                   Boolean nonDestructive,
                                   Integer consumersBeforeDispatch,
                                   Long delayBeforeDispatch,
                                   SimpleString user,
                                   Boolean configurationManaged,
                                   Long ringSize) throws Exception {
      return null;
   }

   @Override
   public QueueBinding updateQueue(QueueConfiguration queueConfiguration) throws Exception {
      return updateQueue(queueConfiguration, false);
   }

   @Override
   public QueueBinding updateQueue(QueueConfiguration queueConfiguration, boolean forceUpdate) throws Exception {
      return null;
   }

   @Override
   public AddressInfo updateAddressInfo(SimpleString addressName,
                                        EnumSet<RoutingType> routingTypes) throws Exception {
      return null;
   }

   @Override
   public boolean isStarted() {
      return false;
   }

   @Override
   public Set<SimpleString> getAddresses() {
      return null;
   }

   @Override
   public SimpleString getMatchingQueue(SimpleString address, RoutingType routingType) {

      return null;
   }

   @Override
   public void start() throws Exception {

   }

   @Override
   public void stop() throws Exception {

   }

   @Override
   public AddressInfo removeAddressInfo(SimpleString address, boolean force) throws Exception {
      return null;
   }

   @Override
   public MirrorController getMirrorControlSource() {
      return null;
   }

   @Override
   public PostOffice setMirrorControlSource(MirrorController mirrorControllerSource) {
      return null;
   }

   @Override
   public void postAcknowledge(MessageReference ref, AckReason reason) {

   }

   @Override
   public boolean addAddressInfo(AddressInfo addressInfo) {
      return false;
   }

   @Override
   public AddressInfo removeAddressInfo(SimpleString address) {
      return null;
   }

   @Override
   public AddressInfo getAddressInfo(SimpleString addressName) {
      return null;
   }

   @Override
   public List<Queue> listQueuesForAddress(SimpleString address) throws Exception {
      return null;
   }

   @Override
   public void addBinding(final Binding binding) throws Exception {

   }

   @Override
   public Binding getBinding(final SimpleString uniqueName) {

      return null;
   }

   @Override
   public Bindings getBindingsForAddress(final SimpleString address) throws Exception {

      return null;
   }

   @Override
   public Stream<Binding> getAllBindings() {
      return null;
   }

   @Override
   public Bindings lookupBindingsForAddress(final SimpleString address) throws Exception {

      return null;
   }

   @Override
   public DuplicateIDCache getDuplicateIDCache(final SimpleString address) {
      return getDuplicateIDCache(address, 2000);
   }

   @Override
   public DuplicateIDCache getDuplicateIDCache(final SimpleString address, int idSize) {
      return DuplicateIDCaches.inMemory(address, idSize);
   }

   @Override
   public Collection<Binding> getMatchingBindings(final SimpleString address) {

      return null;
   }

   @Override
   public Collection<Binding> getDirectBindings(final SimpleString address) {

      return null;
   }

   @Override
   public Object getNotificationLock() {

      return null;
   }

   @Override
   public void startExpiryScanner() {
   }

   @Override
   public void startAddressQueueScanner() {
   }

   @Override
   public boolean isAddressBound(SimpleString address) throws Exception {
      return false;
   }

   @Override
   public Binding removeBinding(SimpleString uniqueName, Transaction tx, boolean deleteData) throws Exception {
      return null;
   }

   @Override
   public void sendQueueInfoToQueue(final SimpleString queueName, final SimpleString address) throws Exception {

   }

   @Override
   public Pair<RoutingContext, Message> redistribute(final Message message,
                                                     final Queue originatingQueue) throws Exception {
      return null;
   }

   @Override
   public MessageReference reload(final Message message,
                                   final Queue queue,
                                   final Transaction tx) throws Exception {
      message.refUp();
      return new MessageReferenceImpl();
   }

   @Override
   public RoutingStatus route(Message message,
                              Transaction tx,
                              boolean direct) throws Exception {
      return RoutingStatus.OK;
   }

   @Override
   public RoutingStatus route(Message message,
                              Transaction tx,
                              boolean direct,
                              boolean rejectDuplicates) throws Exception {
      return RoutingStatus.OK;
   }

   @Override
   public RoutingStatus route(Message message, Transaction tx, boolean direct, boolean rejectDuplicates, Binding binding) throws Exception {
      return null;
   }

   @Override
   public RoutingStatus route(Message message, RoutingContext context, boolean direct) throws Exception {
      return null;
   }

   @Override
   public RoutingStatus route(Message message, RoutingContext context, boolean direct, boolean rejectDuplicates, Binding binding) throws Exception {
      return null;
   }

   @Override
   public void processRoute(Message message, RoutingContext context, boolean direct) throws Exception {
   }

   @Override
   public RoutingStatus route(Message message, boolean direct) throws Exception {
      return RoutingStatus.OK;
   }

   @Override
   public LocalQueueBinding findLocalBinding(long bindingID) {
      return null;
   }
}
