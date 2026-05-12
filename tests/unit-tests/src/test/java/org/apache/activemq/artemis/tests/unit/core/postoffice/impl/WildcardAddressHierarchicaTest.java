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

import java.lang.invoke.MethodHandles;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.paging.impl.PagingManagerImpl;
import org.apache.activemq.artemis.core.paging.impl.PagingStoreFactoryNIO;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.OperationContextImpl;
import org.apache.activemq.artemis.core.persistence.impl.nullpm.NullStorageManager;
import org.apache.activemq.artemis.core.postoffice.impl.WildcardAddressManager;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.settings.impl.HierarchicalObjectRepository;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.actors.OrderedExecutorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WildcardAddressHierarchicaTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   PagingManager pagingManager;

   WildcardAddressManager wildcardAddressManager;


   @BeforeEach
   public void setupPagingManager() throws Exception {
      HierarchicalRepository<AddressSettings> addressSettings = new HierarchicalObjectRepository<>();
      addressSettings.addMatch("#", new AddressSettings());
      ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
      runAfter(scheduledExecutorService::shutdownNow);
      OrderedExecutorFactory orderedExecutorFactory = getOrderedExecutor();
      final StorageManager storageManager = new NullStorageManager().setContextSupplier(() -> OperationContextImpl.getContext(orderedExecutorFactory));
      PagingStoreFactoryNIO storeFactory = new PagingStoreFactoryNIO(storageManager, getPageDirFile(), 100, scheduledExecutorService, orderedExecutorFactory, true, null);
      this.pagingManager = new PagingManagerImpl(storeFactory, addressSettings);
      this.pagingManager.start();
      wildcardAddressManager = new WildcardAddressManager(new BindingFactoryFake(), new WildcardConfiguration(), null, null, pagingManager, true);
   }

   @Test
   public void testHierarchy() throws Exception {
      int repeats = 1000;
      wildcardAddressManager.reloadAddressInfo(new AddressInfo("a.*.*"));
      for (int i = 0; i < repeats; i++) {
         wildcardAddressManager.reloadAddressInfo(new AddressInfo("a.b." + i));
      }
      for (int i = 0; i < repeats; i++) {
         wildcardAddressManager.reloadAddressInfo(new AddressInfo("a.c." + i));
      }

      for (int i = 0; i < 1000; i++) {
         PagingStore store = pagingManager.getPageStore(SimpleString.of("a.b." + i));
         assertEquals(1, store.getSizeMetric().getHierarchy().size());
      }

      for (int i = 0; i < 1000; i++) {
         PagingStore store = pagingManager.getPageStore(SimpleString.of("a.c." + i));
         assertEquals(1, store.getSizeMetric().getHierarchy().size());
      }

      wildcardAddressManager.reloadAddressInfo(new AddressInfo("a.b.*"));
      wildcardAddressManager.reloadAddressInfo(new AddressInfo("a.c.*"));

      for (int i = 0; i < 1000; i++) {
         PagingStore store = pagingManager.getPageStore(SimpleString.of("a.b." + i));
         assertEquals(2, store.getSizeMetric().getHierarchy().size());
      }

      for (int i = 0; i < 1000; i++) {
         PagingStore store = pagingManager.getPageStore(SimpleString.of("a.c." + i));
         assertEquals(2, store.getSizeMetric().getHierarchy().size());
      }

      for (int i = 0; i < 1000; i++) {
         PagingStore store = pagingManager.getPageStore(SimpleString.of("a.b." + i));
         store.addSize(1, false, true);
         assertEquals(1, store.getAddressSize());
         assertEquals(1, store.getAddressElements());
      }

      for (int i = 0; i < 1000; i++) {
         PagingStore store = pagingManager.getPageStore(SimpleString.of("a.c." + i));
         store.addSize(1, false, true);
         assertEquals(1, store.getAddressSize());
         assertEquals(1, store.getAddressElements());
      }

      PagingStore storeAB = pagingManager.getPageStore(SimpleString.of("a.b.*"));
      PagingStore storeAC = pagingManager.getPageStore(SimpleString.of("a.c.*"));
      PagingStore storeA = pagingManager.getPageStore(SimpleString.of("a.*.*"));

      assertEquals(repeats, storeAB.getAddressElements());
      assertEquals(repeats, storeAB.getAddressSize());

      assertEquals(repeats, storeAC.getAddressElements());
      assertEquals(repeats, storeAC.getAddressSize());

      assertEquals(repeats * 2, storeA.getAddressElements());
      assertEquals(repeats * 2, storeA.getAddressSize());

      wildcardAddressManager.removeAddressInfo(SimpleString.of("a.*.*"));
      wildcardAddressManager.removeAddressInfo(SimpleString.of("a.b.*"));
      wildcardAddressManager.removeAddressInfo(SimpleString.of("a.c.*"));


      for (int i = 0; i < 1000; i++) {
         PagingStore storeABChild = pagingManager.getPageStore(SimpleString.of("a.b." + i));
         PagingStore storeACChild = pagingManager.getPageStore(SimpleString.of("a.c." + i));

         assertTrue(storeABChild.getSizeMetric().getHierarchy().isEmpty());
         assertTrue(storeACChild.getSizeMetric().getHierarchy().isEmpty());

      }



   }
}