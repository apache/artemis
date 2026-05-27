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
import java.util.ArrayList;
import java.util.List;
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
import org.apache.activemq.artemis.core.postoffice.impl.WildcardAddressManager;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.settings.impl.HierarchicalObjectRepository;
import org.apache.activemq.artemis.utils.FileUtil;
import org.apache.activemq.artemis.utils.actors.OrderedExecutorFactory;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
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
public class WildcardAddressManagerValidateHierarchicalRoutingTest {

   public WildcardAddressManager addressManager;

   int topics = 100;
   AtomicLong topicCounter;
   private static final WildcardConfiguration WILDCARD_CONFIGURATION = new WildcardConfiguration();
   SimpleString[] addresses;
   PagingStoreImpl[] stores;

   @Param({"2", "5", "10", "100"})
   int levels;

   @Param({"true", "false"})
   boolean useHierarchy;

   PagingManagerImpl pagingManager;

   File tempDirectory;
   private ExecutorService executorService;
   private ScheduledExecutorService scheduledExecutorService;

   List<SimpleString> wildcardLevels = new ArrayList<>();

   @Setup
   public void init() throws Exception {

      String basicAddress = "a.".repeat(levels - 1);

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

      for (int i = 1; i < levels; i++) {
         StringBuilder levelBuilder = new StringBuilder();
         levelBuilder.append("a.".repeat(i));
         levelBuilder.append("*.".repeat(levels - i - 1) + "*");
         String levelString = levelBuilder.toString();

         wildcardLevels.add(SimpleString.of(levelString));

         addressManager.reloadAddressInfo(new AddressInfo(levelString).addRoutingType(RoutingType.MULTICAST));
      }

      addresses = new SimpleString[topics];
      stores = new PagingStoreImpl[topics];

      for (int i = 0; i < topics; i++) {
         SimpleString addressName = SimpleString.of(basicAddress + i);

         Binding binding = new BindingFactoryFake.BindingFake(addressName, SimpleString.of("" + i), i);
         addressManager.reloadAddressInfo(new AddressInfo(addressName).addRoutingType(RoutingType.MULTICAST));
         addressManager.addBinding(binding);
         addresses[i] = addressName;
         addressManager.getBindingsForRoutingAddress(addresses[i]);
         stores[i] = (PagingStoreImpl) pagingManager.getPageStore(addresses[i]);
      }

      if (useHierarchy) {
         for (int i = 0; i < topics; i++) {
            if (stores[i].getHierarchy().size() != levels - 1) {
               throw new IllegalStateException("We are supposed to have " + levels + " on each store, store " + stores[i] + " had " + stores[i].getHierarchy().size() + " on " + stores[i]);
            }
         }
      } else {
         for (int i = 0; i < topics; i++) {
            if (!stores[i].getHierarchy().isEmpty()) {
               throw new IllegalStateException("Hierarchy on store " + stores[i] + " is supposed to be empty");
            }
         }

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

      long next;
      PagingStoreImpl[] stores;

      @Setup
      public void init(WildcardAddressManagerValidateHierarchicalRoutingTest benchmarkState) {
         stores = benchmarkState.stores;
      }

      public PagingStoreImpl nextPagingStore() {
         final long current = next;
         next = current + 1;
         final int index = (int) (current & (stores.length - 1));
         return stores[index];
      }
   }

   @Benchmark
   public PagingStoreImpl testMeasureDepth(ThreadState state) throws Exception {
      PagingStoreImpl store = state.nextPagingStore();
      store.addSize(1, false, true);
      store.addSize(1, true, true);
      return store;
   }

}

