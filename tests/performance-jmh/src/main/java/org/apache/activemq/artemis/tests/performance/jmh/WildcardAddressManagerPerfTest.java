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

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.apache.activemq.artemis.core.paging.impl.PagingManagerImpl;
import org.apache.activemq.artemis.core.paging.impl.PagingStoreFactoryNIO;
import org.apache.activemq.artemis.core.paging.impl.PagingStoreImpl;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.OperationContextImpl;
import org.apache.activemq.artemis.core.persistence.impl.nullpm.NullStorageManager;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.Bindings;
import org.apache.activemq.artemis.core.postoffice.impl.WildcardAddressManager;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.settings.impl.HierarchicalObjectRepository;
import org.apache.activemq.artemis.utils.FileUtil;
import org.apache.activemq.artemis.utils.actors.OrderedExecutorFactory;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Benchmark)
@Fork(2)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 8, time = 1)
public class WildcardAddressManagerPerfTest {

   public WildcardAddressManager addressManager;

   @Param({"2", "8", "10"})
   int topicsLog2;
   int topics;
   AtomicLong topicCounter;
   private static final WildcardConfiguration WILDCARD_CONFIGURATION;
   SimpleString[] addresses;
   PagingStoreImpl[] stores;

   static {
      WILDCARD_CONFIGURATION = new WildcardConfiguration();
      WILDCARD_CONFIGURATION.setAnyWords('>');
   }

   private static final SimpleString WILDCARD = SimpleString.of("Topic1.>");

   @Param({"false", "true"})
   boolean useHierarchy;

   PagingManagerImpl pagingManager;


   File tempDirectory;
   private ExecutorService executorService;
   private ScheduledExecutorService scheduledExecutorService;

   @Setup
   public void init() throws Exception {

      tempDirectory = Files.createTempDirectory("jmh-artemis-").toFile();
      tempDirectory.deleteOnExit();
      HierarchicalRepository<AddressSettings> addressSettings = new HierarchicalObjectRepository<>();
      addressSettings.addMatch("#", new AddressSettings());
      scheduledExecutorService = Executors.newScheduledThreadPool(1);
      executorService = Executors.newFixedThreadPool(1);
      OrderedExecutorFactory orderedExecutorFactory = new OrderedExecutorFactory(executorService);
      final StorageManager storageManager = new NullStorageManager().setContextSupplier(() -> OperationContextImpl.getContext(orderedExecutorFactory));
      PagingStoreFactoryNIO storeFactory = new PagingStoreFactoryNIO(storageManager, tempDirectory, 100, scheduledExecutorService, orderedExecutorFactory, true, null);
      pagingManager = new PagingManagerImpl(storeFactory, addressSettings);
      pagingManager.start();

      addressManager = new WildcardAddressManager(new BindingFactoryFake(), WILDCARD_CONFIGURATION, null, null, useHierarchy ? pagingManager : null, useHierarchy);

      addressManager.addAddressInfo(new AddressInfo(WILDCARD, RoutingType.MULTICAST));

      topics = 1 << topicsLog2;
      addresses = new SimpleString[topics];
      stores = new PagingStoreImpl[topics];

      for (int i = 0; i < topics; i++) {
         Binding binding = new BindingFactoryFake.BindingFake(WILDCARD, SimpleString.of("" + i), i);
         addressManager.addBinding(binding);
         addresses[i] = SimpleString.of("Topic1." + i);
         addressManager.getBindingsForRoutingAddress(addresses[i]);
         stores[i] = (PagingStoreImpl) pagingManager.getPageStore(addresses[i]);
      }
      topicCounter = new AtomicLong(0);
      topicCounter.set(topics);
   }

   @TearDown
   public void shutdownExecutors() {
      if (scheduledExecutorService != null) {
         scheduledExecutorService.shutdownNow();
      }
      if (executorService != null) {
         executorService.shutdownNow();
      }

      if (tempDirectory != null && tempDirectory.exists()) {
         FileUtil.deleteDirectory(tempDirectory);
      }

   }

   private long nextId() {
      return topicCounter.getAndIncrement();
   }

   @State(value = Scope.Thread)
   public static class ThreadState {

      Binding binding;
      long next;
      SimpleString[] addresses;
      PagingStoreImpl[] stores;

      @Setup
      public void init(WildcardAddressManagerPerfTest benchmarkState) {
         final long id = benchmarkState.nextId();
         binding = new BindingFactoryFake.BindingFake(WILDCARD, SimpleString.of("" + id), id);
         addresses = benchmarkState.addresses;
         stores = benchmarkState.stores;
      }

      public PagingStoreImpl nextPagingStore() {
         final long current = next;
         next = current + 1;
         final int index = (int) (current & (addresses.length - 1));
         return stores[index];
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
      PagingStoreImpl store = state.nextPagingStore();
      Bindings bindings = addressManager.getBindingsForRoutingAddress(store.getAddress());
      store.addSize(1, false, true);
      store.addSize(1, true, true);
      return bindings;
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
      PagingStoreImpl store = state.nextPagingStore();
      Bindings bindings = addressManager.getBindingsForRoutingAddress(store.getAddress());
      store.addSize(1, false, true);
      store.addSize(1, true, true);
      return bindings;
   }

   @Benchmark
   @GroupThreads(4)
   public Binding testJustAddRemoveNewBinding(ThreadState state) throws Exception {
      final Binding binding = state.binding;
      addressManager.addBinding(binding);
      return addressManager.removeBinding(binding.getUniqueName(), null);
   }

}

