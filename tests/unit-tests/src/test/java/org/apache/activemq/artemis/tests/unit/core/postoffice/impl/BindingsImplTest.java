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
package org.apache.activemq.artemis.tests.unit.core.postoffice.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.transaction.xa.Xid;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.persistence.impl.nullpm.NullStorageManager;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.BindingType;
import org.apache.activemq.artemis.core.postoffice.Bindings;
import org.apache.activemq.artemis.core.postoffice.impl.BindingsImpl;
import org.apache.activemq.artemis.core.server.Bindable;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.RoutingContext;
import org.apache.activemq.artemis.core.server.cluster.RemoteQueueBinding;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.server.group.GroupingHandler;
import org.apache.activemq.artemis.core.server.group.UnproposalListener;
import org.apache.activemq.artemis.core.server.group.impl.GroupBinding;
import org.apache.activemq.artemis.core.server.group.impl.Proposal;
import org.apache.activemq.artemis.core.server.group.impl.Response;
import org.apache.activemq.artemis.core.server.management.Notification;
import org.apache.activemq.artemis.core.server.impl.AckReason;
import org.apache.activemq.artemis.core.server.impl.RefsOperation;
import org.apache.activemq.artemis.core.server.impl.RoutingContextImpl;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.core.transaction.TransactionOperation;
import org.apache.activemq.artemis.selector.filter.Filterable;
import org.apache.activemq.artemis.tests.unit.core.postoffice.impl.fakes.FakeQueue;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

public class BindingsImplTest extends ActiveMQTestBase {

   @Test
   public void testGetNextBindingWithLoadBalancingOnDemand() throws Exception {
      final FakeRemoteBinding fake = new FakeRemoteBinding(SimpleString.of("a"));
      fake.filter = null;  // such that it wil match all messages
      fake.messageLoadBalancingType = MessageLoadBalancingType.ON_DEMAND;
      final Bindings bind = new BindingsImpl(null, null, new NullStorageManager(1000));
      bind.addBinding(fake);
      bind.route(new CoreMessage(0, 100), new RoutingContextImpl(new FakeTransaction()));
      assertEquals(1, fake.routedCount.get());
   }

   @Test
   public void testGetNextBindingWithLoadBalancingOff() throws Exception {
      final FakeRemoteBinding fake = new FakeRemoteBinding(SimpleString.of("a"));
      fake.filter = null;  // such that it wil match all messages
      fake.messageLoadBalancingType = MessageLoadBalancingType.OFF;
      final Bindings bind = new BindingsImpl(null, null, new NullStorageManager(1000));
      bind.addBinding(fake);
      bind.route(new CoreMessage(0, 100), new RoutingContextImpl(new FakeTransaction()));
      assertEquals(0, fake.routedCount.get());
   }

   @Test
   public void testGetNextBindingWithLoadBalancingOffWithRedistribution() throws Exception {
      final FakeRemoteBinding fake = new FakeRemoteBinding(SimpleString.of("a"));
      fake.filter = null;  // such that it wil match all messages
      fake.messageLoadBalancingType = MessageLoadBalancingType.OFF_WITH_REDISTRIBUTION;
      final Bindings bind = new BindingsImpl(null, null, new NullStorageManager(1000));
      bind.addBinding(fake);
      bind.route(new CoreMessage(0, 100), new RoutingContextImpl(new FakeTransaction()));
      assertEquals(0, fake.routedCount.get());
   }

   /**
    * Pins the critical invariant that {@link Binding#isEnabled()} defaults to {@code true}: a binding that does not
    * override it (like a local queue binding or a divert) must still be routed to. If the default ever regresses to
    * {@code false}, ordinary local delivery breaks and this test fails immediately.
    */
   @Test
   public void testDefaultBindingIsEnabledAndRoutes() throws Exception {
      final FakeBinding fake = new FakeBinding(SimpleString.of("a"));
      fake.filter = null;  // such that it will match all messages
      final Bindings bind = new BindingsImpl(null, null, new NullStorageManager(1000));
      bind.addBinding(fake);
      bind.route(new CoreMessage(0, 100), new RoutingContextImpl(new FakeTransaction()));
      assertEquals(1, fake.routedCount.get());
   }

   @Test
   public void testDisabledRemoteBindingIsNotRouted() throws Exception {
      final FakeRemoteBinding fake = new FakeRemoteBinding(SimpleString.of("a"));
      fake.filter = null;  // such that it will match all messages
      fake.messageLoadBalancingType = MessageLoadBalancingType.ON_DEMAND;
      fake.setEnabled(false);
      final Bindings bind = new BindingsImpl(null, null, new NullStorageManager(1000));
      bind.addBinding(fake);
      bind.route(new CoreMessage(0, 100), new RoutingContextImpl(new FakeTransaction()));
      assertEquals(0, fake.routedCount.get());
   }

   @Test
   public void testDisabledRemoteBindingIsNotRoutedWithStrictOrdering() throws Exception {
      final FakeRemoteBinding fake = new FakeRemoteBinding(SimpleString.of("a"));
      fake.filter = null;  // such that it will match all messages
      fake.messageLoadBalancingType = MessageLoadBalancingType.STRICT;
      fake.setEnabled(false);
      final Bindings bind = new BindingsImpl(null, null, new NullStorageManager(1000));
      bind.addBinding(fake);
      bind.route(new CoreMessage(0, 100), new RoutingContextImpl(new FakeTransaction()));
      assertEquals(0, fake.routedCount.get());
   }

   @Test
   public void testReEnabledRemoteBindingIsRouted() throws Exception {
      final FakeRemoteBinding fake = new FakeRemoteBinding(SimpleString.of("a"));
      fake.filter = null;  // such that it will match all messages
      fake.messageLoadBalancingType = MessageLoadBalancingType.ON_DEMAND;
      fake.setEnabled(false);
      final Bindings bind = new BindingsImpl(null, null, new NullStorageManager(1000));
      bind.addBinding(fake);
      bind.route(new CoreMessage(0, 100), new RoutingContextImpl(new FakeTransaction()));
      assertEquals(0, fake.routedCount.get());

      fake.setEnabled(true);
      bind.route(new CoreMessage(1, 100), new RoutingContextImpl(new FakeTransaction()));
      assertEquals(1, fake.routedCount.get());
   }

   @Test
   public void testDisabledBindingIsSkippedDuringRedistribution() throws Exception {
      final FakeRemoteBinding fake = new FakeRemoteBinding(SimpleString.of("a"));
      fake.filter = null;  // such that it will match all messages
      fake.highAcceptPriority = true;
      fake.messageLoadBalancingType = MessageLoadBalancingType.ON_DEMAND;
      fake.setEnabled(false);
      final Bindings bind = new BindingsImpl(null, null, new NullStorageManager(1000));
      bind.addBinding(fake);

      final Queue originatingQueue = new FakeQueue(SimpleString.of("a"));

      assertNull(bind.redistribute(new CoreMessage(0, 100), originatingQueue, new RoutingContextImpl(new FakeTransaction())));
      assertEquals(0, fake.routedCount.get());

      fake.setEnabled(true);
      assertNotNull(bind.redistribute(new CoreMessage(1, 100), originatingQueue, new RoutingContextImpl(new FakeTransaction())));
      assertEquals(1, fake.routedCount.get());
   }

   /**
    * Covers the strict-ordering (message grouping) route path. When a group is already pinned to a binding that has
    * since been disabled (e.g. a remote queue binding whose node is being drained during scale-down), the message must
    * not be routed to that disabled binding. Instead the stale group proposal is force-removed and the group is
    * re-proposed to an enabled binding.
    */
   @Test
   public void testDisabledPinnedBindingIsReRoutedToEnabledBindingDuringStrictOrdering() throws Exception {
      final SimpleString routingName = SimpleString.of("a");

      final FakeRemoteBinding disabled = new FakeRemoteBinding(routingName);
      disabled.filter = null;  // such that it will match all messages
      disabled.highAcceptPriority = true;
      disabled.messageLoadBalancingType = MessageLoadBalancingType.ON_DEMAND;
      disabled.clusterName = SimpleString.of("a_disabled");
      disabled.setEnabled(false);

      final FakeRemoteBinding enabled = new FakeRemoteBinding(routingName);
      enabled.filter = null;  // such that it will match all messages
      enabled.highAcceptPriority = true;
      enabled.messageLoadBalancingType = MessageLoadBalancingType.ON_DEMAND;
      enabled.clusterName = SimpleString.of("a_enabled");

      // the grouping handler starts with the group pinned to the now-disabled binding's cluster name
      final FakeGroupingHandler groupingHandler = new FakeGroupingHandler(disabled.clusterName);

      final Bindings bind = new BindingsImpl(null, groupingHandler, new NullStorageManager(1000));
      bind.addBinding(disabled);
      bind.addBinding(enabled);

      final CoreMessage message = new CoreMessage(0, 100);
      message.setGroupID(SimpleString.of("group0"));

      bind.route(message, new RoutingContextImpl(new FakeTransaction()));

      assertEquals(0, disabled.routedCount.get(), "disabled binding must not receive the grouped message");
      assertEquals(1, enabled.routedCount.get(), "group must be re-routed to the enabled binding");
      assertTrue(groupingHandler.forceRemoveCalled, "the stale proposal pinned to the disabled binding must be removed");
   }

   @Test
   public void testRemoveWhileRouting() throws Exception {
      // It would require many iterations before getting a failure
      for (int i = 0; i < 500; i++) {
         internalTest(true);
      }
   }

   @Test
   public void testRemoveWhileRedistributing() throws Exception {
      // It would require many iterations before getting a failure
      for (int i = 0; i < 500; i++) {
         internalTest(false);
      }
   }

   private void internalTest(final boolean route) throws Exception {
      final FakeBinding fake = new FakeBinding(SimpleString.of("a"));

      final Bindings bind = new BindingsImpl(null, null, new NullStorageManager(1000));
      bind.addBinding(fake);
      bind.addBinding(new FakeBinding(SimpleString.of("a")));
      bind.addBinding(new FakeBinding(SimpleString.of("a")));

      Thread t = new Thread(() -> {
         try {
            bind.removeBindingByUniqueName(fake.getUniqueName());
         } catch (Exception e) {
            e.printStackTrace();
         }
      });

      Queue queue = new FakeQueue(SimpleString.of("a"));
      t.start();

      for (int i = 0; i < 100; i++) {
         if (route) {
            bind.route(new CoreMessage(i, 100), new RoutingContextImpl(new FakeTransaction()));
         } else {
            bind.redistribute(new CoreMessage(i, 100), queue, new RoutingContextImpl(new FakeTransaction()));
         }
      }
   }

   private final class FakeTransaction implements Transaction {

      @Override
      public Object getProtocolData() {
         return null;
      }

      @Override
      public boolean isAsync() {
         return false;
      }

      @Override
      public Transaction setAsync(boolean async) {
         return null;
      }

      @Override
      public void setProtocolData(Object data) {

      }

      @Override
      public void afterWired(Runnable runnable) {

      }

      @Override
      public void afterStore(TransactionOperation sync) {

      }

      @Override
      public void addOperation(final TransactionOperation sync) {

      }

      @Override
      public boolean isEffective() {
         return false;
      }

      @Override
      public boolean hasTimedOut(long currentTime, int defaultTimeout) {
         return false;
      }

      @Override
      public void commit() throws Exception {

      }

      @Override
      public boolean tryRollback() {
         return true;
      }

      @Override
      public void commit(final boolean onePhase) throws Exception {

      }

      @Override
      public long getCreateTime() {

         return 0;
      }

      @Override
      public long getID() {

         return 0;
      }

      @Override
      public Object getProperty(final int index) {

         return null;
      }

      @Override
      public boolean isContainsPersistent() {
         return false;
      }

      @Override
      public State getState() {

         return null;
      }

      @Override
      public Xid getXid() {
         return null;
      }

      @Override
      public void markAsRollbackOnly(final ActiveMQException exception) {

      }

      @Override
      public void prepare() throws Exception {

      }

      @Override
      public void putProperty(final int index, final Object property) {

      }

      public void removeOperation(final TransactionOperation sync) {

      }

      @Override
      public void resume() {

      }

      @Override
      public void rollback() throws Exception {

      }

      @Override
      public void setState(final State state) {

      }

      @Override
      public void suspend() {

      }

      public Set<Queue> getDistinctQueues() {
         return Collections.emptySet();
      }

      @Override
      public void setContainsPersistent() {

      }

      @Override
      public void setTimeout(int timeout) {

      }

      @Override
      public List<TransactionOperation> getAllOperations() {
         return null;
      }

      public void setWaitBeforeCommit(boolean waitBeforeCommit) {
      }

      @Override
      public RefsOperation createRefsOperation(Queue queue, AckReason reason) {
         return null;
      }

      @Override
      public boolean hasTimedOut() {
         return false;
      }

      @Override
      public void delay() {

      }

      @Override
      public void delayDone() {

      }
   }

   private final class FakeFilter implements Filter {

      @Override
      public SimpleString getFilterString() {
         return null;
      }

      @Override
      public boolean match(final Message message) {
         return false;
      }

      @Override
      public boolean match(Map<String, String> map) {
         return false;

      }

      @Override
      public boolean match(Filterable filterable) {
         return false;
      }
   }

   private class FakeBinding implements Binding {

      Filter filter = new FakeFilter();
      AtomicInteger routedCount = new AtomicInteger();
      boolean highAcceptPriority = false;
      SimpleString clusterName;
      @Override
      public void close() throws Exception {

      }

      @Override
      public void unproposed(SimpleString groupID) {

      }

      final SimpleString name;
      final SimpleString uniqueName = SimpleString.of(UUID.randomUUID().toString());

      FakeBinding(final SimpleString name) {
         this.name = name;
      }

      @Override
      public SimpleString getAddress() {
         return null;
      }

      @Override
      public Bindable getBindable() {

         return null;
      }

      @Override
      public SimpleString getClusterName() {

         return clusterName;
      }

      @Override
      public int getDistance() {
         return 0;
      }

      @Override
      public Filter getFilter() {
         return filter;
      }

      @Override
      public Long getID() {
         return 0L;
      }

      @Override
      public SimpleString getRoutingName() {
         return name;
      }

      @Override
      public BindingType getType() {
         return null;
      }

      @Override
      public SimpleString getUniqueName() {
         return uniqueName;
      }

      @Override
      public boolean isExclusive() {
         return false;
      }

      @Override
      public boolean isHighAcceptPriority(final Message message) {
         return highAcceptPriority;
      }

      @Override
      public void route(final Message message, final RoutingContext context) throws Exception {
         routedCount.incrementAndGet();
      }

      @Override
      public String toManagementString() {
         return null;
      }

      @Override
      public boolean isConnected() {
         return true;
      }

      @Override
      public void routeWithAck(Message message, RoutingContext context) {
      }
   }

   private final class FakeRemoteBinding extends FakeBinding implements RemoteQueueBinding {
      MessageLoadBalancingType messageLoadBalancingType;
      boolean enabled = true;
      FakeRemoteBinding(SimpleString name) {
         super(name);
      }

      @Override
      public boolean isEnabled() {
         return enabled;
      }

      @Override
      public void setEnabled(boolean enabled) {
         this.enabled = enabled;
      }

      @Override
      public boolean isLocal() {
         return false;
      }

      @Override
      public int consumerCount() {
         return 0;
      }

      @Override
      public Queue getQueue() {
         return null;
      }

      @Override
      public void addConsumer(SimpleString filterString) throws Exception {

      }

      @Override
      public void removeConsumer(SimpleString filterString) throws Exception {
      }

      @Override
      public void reset() {
      }

      @Override
      public void disconnect() {
      }

      @Override
      public void connect() {
      }

      @Override
      public long getRemoteQueueID() {
         return 0;
      }

      @Override
      public void setFilter(Filter filter) {
      }

      @Override
      public MessageLoadBalancingType getMessageLoadBalancingType() {
         return messageLoadBalancingType;
      }
   }

   /**
    * Minimal grouping handler for the strict-ordering route path: a group is pinned to {@code pinnedClusterName} until
    * {@link #forceRemove} evicts it, after which {@link #propose} accepts whatever binding is proposed next.
    */
   private final class FakeGroupingHandler implements GroupingHandler {

      private SimpleString pinnedClusterName;
      private boolean forceRemoveCalled = false;

      private FakeGroupingHandler(SimpleString pinnedClusterName) {
         this.pinnedClusterName = pinnedClusterName;
      }

      @Override
      public Response getProposal(SimpleString fullID, boolean touchTime) {
         return pinnedClusterName == null ? null : new Response(fullID, pinnedClusterName);
      }

      @Override
      public Response propose(Proposal proposal) {
         return new Response(proposal.getGroupId(), proposal.getClusterName());
      }

      @Override
      public void forceRemove(SimpleString groupid, SimpleString clusterName) {
         forceRemoveCalled = true;
         pinnedClusterName = null;
      }

      @Override
      public void addListener(UnproposalListener listener) {
      }

      @Override
      public SimpleString getName() {
         return null;
      }

      @Override
      public void resendPending() {
      }

      @Override
      public void proposed(Response response) {
      }

      @Override
      public void sendProposalResponse(Response response, int distance) {
      }

      @Override
      public Response receive(Proposal proposal, int distance) {
         return null;
      }

      @Override
      public void addGroupBinding(GroupBinding groupBinding) {
      }

      @Override
      public void awaitBindings() {
      }

      @Override
      public void remove(SimpleString groupid, SimpleString clusterName) {
      }

      @Override
      public void remove(SimpleString groupid, SimpleString clusterName, int distance) {
      }

      @Override
      public void onNotification(Notification notification) {
      }

      @Override
      public void start() {
      }

      @Override
      public void stop() {
      }

      @Override
      public boolean isStarted() {
         return true;
      }
   }
}
