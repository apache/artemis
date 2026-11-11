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
package org.apache.activemq.artemis.tests.performance.jmh;

import java.util.concurrent.atomic.AtomicLong;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.persistence.impl.nullpm.NullStorageManager;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.BindingType;
import org.apache.activemq.artemis.core.postoffice.Bindings;
import org.apache.activemq.artemis.core.postoffice.BindingsFactory;
import org.apache.activemq.artemis.core.postoffice.impl.BindingsImpl;
import org.apache.activemq.artemis.core.postoffice.impl.WildcardAddressManager;
import org.apache.activemq.artemis.core.server.Bindable;
import org.apache.activemq.artemis.core.server.RoutingContext;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Benchmark)
@Fork(2)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 8, time = 1)
public class WildcardAddressManagerPerfTest {

   private static class BindingFactoryFake implements BindingsFactory {

      @Override
      public Bindings createBindings(SimpleString address) {
         return new BindingsImpl(address, null, new NullStorageManager(1000));
      }
   }

   private static class BindingFake implements Binding {

      final SimpleString address;
      final SimpleString id;
      final Long idl;

      BindingFake(SimpleString addressParameter, SimpleString id, long idl) {
         this.address = addressParameter;
         this.id = id;
         this.idl = idl;
      }

      @Override
      public void unproposed(SimpleString groupID) {

      }

      @Override
      public SimpleString getAddress() {
         return address;
      }

      @Override
      public Bindable getBindable() {
         return null;
      }

      @Override
      public BindingType getType() {
         return BindingType.LOCAL_QUEUE;
      }

      @Override
      public SimpleString getUniqueName() {
         return id;
      }

      @Override
      public SimpleString getRoutingName() {
         return id;
      }

      @Override
      public SimpleString getClusterName() {
         return null;
      }

      @Override
      public Filter getFilter() {
         return null;
      }

      @Override
      public boolean isHighAcceptPriority(Message message) {
         return false;
      }

      @Override
      public boolean isExclusive() {
         return false;
      }

      @Override
      public Long getID() {
         return idl;
      }

      @Override
      public int getDistance() {
         return 0;
      }

      @Override
      public void route(Message message, RoutingContext context) {
      }

      @Override
      public void close() {
      }

      @Override
      public String toManagementString() {
         return "FakeBiding Address=" + this.address;
      }

      @Override
      public boolean isConnected() {
         return true;
      }

      @Override
      public void routeWithAck(Message message, RoutingContext context) {

      }
   }

   public WildcardAddressManager addressManager;

   @Param({"2", "8", "10"})
   int topicsLog2;
   int topics;
   AtomicLong topicCounter;
   private static final WildcardConfiguration WILDCARD_CONFIGURATION;
   SimpleString[] addresses;

   static {
      WILDCARD_CONFIGURATION = new WildcardConfiguration();
      WILDCARD_CONFIGURATION.setAnyWords('>');
   }

   private static final SimpleString WILDCARD = SimpleString.of("Topic1.>");

   @Setup
   public void init() throws Exception {
      addressManager = new WildcardAddressManager(new BindingFactoryFake(), WILDCARD_CONFIGURATION, null, null);

      addressManager.addAddressInfo(new AddressInfo(WILDCARD, RoutingType.MULTICAST));

      topics = 1 << topicsLog2;
      addresses = new SimpleString[topics];
      for (int i = 0; i < topics; i++) {
         Binding binding = new BindingFake(WILDCARD, SimpleString.of("" + i), i);
         addressManager.addBinding(binding);
         addresses[i] = SimpleString.of("Topic1." + i);
         addressManager.getBindingsForRoutingAddress(addresses[i]);
      }
      topicCounter = new AtomicLong(0);
      topicCounter.set(topics);
   }

   private long nextId() {
      return topicCounter.getAndIncrement();
   }

   @State(value = Scope.Thread)
   public static class ThreadState {

      Binding binding;
      long next;
      SimpleString[] addresses;

      @Setup
      public void init(WildcardAddressManagerPerfTest benchmarkState) {
         final long id = benchmarkState.nextId();
         binding = new BindingFake(WILDCARD, SimpleString.of("" + id), id);
         addresses = benchmarkState.addresses;
      }

      public SimpleString nextAddress() {
         final long current = next;
         next = current + 1;
         final int index = (int) (current & (addresses.length - 1));
         return addresses[index];
      }
   }

   @Benchmark
   @Group("both")
   @GroupThreads(2)
   public Bindings testPublishWhileAddRemoveNewBinding(ThreadState state) throws Exception {
      return addressManager.getBindingsForRoutingAddress(state.nextAddress());
   }

   @Benchmark
   @Group("both")
   @GroupThreads(2)
   public Binding testAddRemoveNewBindingWhilePublish(ThreadState state) throws Exception {
      final Binding binding = state.binding;
      addressManager.addBinding(binding);
      return addressManager.removeBinding(binding.getUniqueName(), null);
   }

   @Benchmark
   @GroupThreads(4)
   public Bindings testJustPublish(ThreadState state) throws Exception {
      return addressManager.getBindingsForRoutingAddress(state.nextAddress());
   }

   @Benchmark
   @GroupThreads(4)
   public Binding testJustAddRemoveNewBinding(ThreadState state) throws Exception {
      final Binding binding = state.binding;
      addressManager.addBinding(binding);
      return addressManager.removeBinding(binding.getUniqueName(), null);
   }

}

