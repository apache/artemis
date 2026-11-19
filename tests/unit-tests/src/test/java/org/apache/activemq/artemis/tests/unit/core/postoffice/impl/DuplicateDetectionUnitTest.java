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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.IntFunction;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager;
import org.apache.activemq.artemis.core.persistence.impl.nullpm.NullStorageManager;
import org.apache.activemq.artemis.core.postoffice.DuplicateIDCache;
import org.apache.activemq.artemis.core.postoffice.PostOffice;
import org.apache.activemq.artemis.core.postoffice.impl.DuplicateIDCaches;
import org.apache.activemq.artemis.core.server.impl.PostOfficeJournalLoader;
import org.apache.activemq.artemis.core.transaction.impl.ResourceManagerImpl;
import org.apache.activemq.artemis.tests.unit.core.server.impl.fakes.FakePostOffice;
import org.apache.activemq.artemis.tests.unit.util.FakePagingManager;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.ActiveMQThreadFactory;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.actors.OrderedExecutorFactory;
import org.apache.activemq.artemis.utils.critical.EmptyCriticalAnalyzer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DuplicateDetectionUnitTest extends ActiveMQTestBase {


   ExecutorService executor;

   ExecutorFactory factory;

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      executor.shutdown();
      super.tearDown();
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      executor = Executors.newFixedThreadPool(10, ActiveMQThreadFactory.defaultThreadFactory(getClass().getName()));
      factory = new OrderedExecutorFactory(executor);
   }


   @Test
   public void testReloadDuplication() throws Exception {

      JournalStorageManager journal = null;

      try {
         clearDataRecreateServerDirs();

         SimpleString ADDRESS = SimpleString.of("address");

         Configuration configuration = createDefaultInVMConfig();

         PostOffice postOffice = new FakePostOffice();

         ScheduledExecutorService scheduledThreadPool = Executors.newScheduledThreadPool(ActiveMQDefaultConfiguration.getDefaultScheduledThreadPoolMaxSize(), ActiveMQThreadFactory.defaultThreadFactory(getClass().getName()));

         journal = new JournalStorageManager(configuration, EmptyCriticalAnalyzer.getInstance(), factory, factory);

         journal.start();
         journal.loadBindingJournal(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

         Map<SimpleString, List<Pair<byte[], Long>>> mapDups = new HashMap<>();

         FakePagingManager pagingManager = new FakePagingManager();
         journal.loadMessageJournal(postOffice, pagingManager, new ResourceManagerImpl(null, 0, 0, scheduledThreadPool), null, mapDups, null, null, new PostOfficeJournalLoader(postOffice, pagingManager, null, null, null, null, null, null));

         assertEquals(0, mapDups.size());

         DuplicateIDCache cacheID = DuplicateIDCaches.persistent(ADDRESS, 10, journal);

         for (int i = 0; i < 100; i++) {
            cacheID.addToCache(RandomUtil.randomBytes());
         }

         journal.stop();

         journal = new JournalStorageManager(configuration, EmptyCriticalAnalyzer.getInstance(), factory, factory);
         journal.start();
         journal.loadBindingJournal(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

         journal.loadMessageJournal(postOffice, pagingManager, new ResourceManagerImpl(null, 0, 0, scheduledThreadPool), null, mapDups, null, null, new PostOfficeJournalLoader(postOffice, pagingManager, null, null, null, null, null, null));

         assertEquals(1, mapDups.size());

         List<Pair<byte[], Long>> values = mapDups.get(ADDRESS);

         assertEquals(10, values.size());

         cacheID = DuplicateIDCaches.persistent(ADDRESS, 10, journal);
         cacheID.load(values);

         for (int i = 0; i < 100; i++) {
            cacheID.addToCache(RandomUtil.randomBytes(), null);
         }

         journal.stop();

         mapDups.clear();

         journal = new JournalStorageManager(configuration, EmptyCriticalAnalyzer.getInstance(), factory, factory);
         journal.start();
         journal.loadBindingJournal(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

         journal.loadMessageJournal(postOffice, pagingManager, new ResourceManagerImpl(null, 0, 0, scheduledThreadPool), null, mapDups, null, null, new PostOfficeJournalLoader(postOffice, pagingManager, null, null, null, null, null, null));

         assertEquals(1, mapDups.size());

         values = mapDups.get(ADDRESS);

         assertEquals(10, values.size());

         scheduledThreadPool.shutdown();
      } finally {
         if (journal != null) {
            try {
               journal.stop();
            } catch (Throwable ignored) {
            }
         }
      }

   }

   @Test
   public void testIdHeldTwiceInMemory() throws Exception {
      testIdHeldTwice(size -> DuplicateIDCaches.inMemory(SimpleString.of("address"), size));
   }

   @Test
   public void testIdHeldTwicePersistent() throws Exception {
      testIdHeldTwice(size -> DuplicateIDCaches.persistent(SimpleString.of("address"), size, new NullStorageManager()));
   }

   /*
    * An id the cache holds in two slots stays a duplicate until its newer slot is overwritten
    */
   private void testIdHeldTwice(IntFunction<DuplicateIDCache> caches) throws Exception {
      final byte[] same = "same".getBytes(StandardCharsets.UTF_8);

      DuplicateIDCache cache = caches.apply(3);
      cache.addToCache(same);
      cache.addToCache(same);
      cache.addToCache("other".getBytes(StandardCharsets.UTF_8));
      // Overwrites the older slot holding "same"
      cache.addToCache("new".getBytes(StandardCharsets.UTF_8));
      assertTrue(cache.contains(same));
      // Overwrites the newer one
      cache.addToCache("newer".getBytes(StandardCharsets.UTF_8));
      assertFalse(cache.contains(same));

      // The same id overwriting itself
      cache = caches.apply(1);
      cache.addToCache(same);
      cache.addToCache(same);
      assertTrue(cache.contains(same));
   }
}
