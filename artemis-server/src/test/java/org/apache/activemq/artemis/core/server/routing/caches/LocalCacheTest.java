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
package org.apache.activemq.artemis.core.server.routing.caches;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.persistence.config.PersistedKeyValuePair;
import org.apache.activemq.artemis.core.persistence.impl.nullpm.NullStorageManager;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LocalCacheTest {
   private static final String CACHE_NAME = "TEST";
   private static final int CACHE_TIMEOUT = 500;
   private static final String CACHE_ENTRY_KEY = "TEST_KEY";
   private static final String CACHE_ENTRY_VALUE = "TEST_VALUE";

   @Test
   public void testValidEntry() {
      LocalCache cache = new LocalCache(CACHE_NAME, false, 0, null);

      cache.start();

      try {
         cache.put(CACHE_ENTRY_KEY, CACHE_ENTRY_VALUE);
         assertEquals(CACHE_ENTRY_VALUE, cache.get(CACHE_ENTRY_KEY));
      } finally {
         cache.stop();
      }
   }

   @Test
   public void testExpiration() throws Exception {
      LocalCache cache = new LocalCache(CACHE_NAME, false, CACHE_TIMEOUT, null);

      cache.start();

      try {
         cache.put(CACHE_ENTRY_KEY, CACHE_ENTRY_VALUE);
         assertEquals(CACHE_ENTRY_VALUE, cache.get(CACHE_ENTRY_KEY));
         Wait.assertTrue(() -> cache.get(CACHE_ENTRY_KEY) == null, CACHE_TIMEOUT * 2, CACHE_TIMEOUT);
      } finally {
         cache.stop();
      }
   }

   @Test
   public void testPersistedEntry() {
      StorageManager storageManager = new DummyKeyValuePairStorageManager();

      LocalCache cacheBeforeStop = new LocalCache(CACHE_NAME, true, 0, storageManager);

      cacheBeforeStop.start();

      try {
         cacheBeforeStop.put(CACHE_ENTRY_KEY, CACHE_ENTRY_VALUE);
         assertEquals(CACHE_ENTRY_VALUE, cacheBeforeStop.get(CACHE_ENTRY_KEY));
      } finally {
         cacheBeforeStop.stop();
      }

      assertEquals(CACHE_ENTRY_VALUE, storageManager.getPersistedKeyValuePairs(CACHE_NAME).get(CACHE_ENTRY_KEY).getValue());

      LocalCache cacheAfterStop = new LocalCache(CACHE_NAME, true, 0, storageManager);

      cacheAfterStop.start();

      try {
         assertEquals(CACHE_ENTRY_VALUE, cacheAfterStop.get(CACHE_ENTRY_KEY));
      } finally {
         cacheAfterStop.stop();
      }

      assertEquals(CACHE_ENTRY_VALUE, storageManager.getPersistedKeyValuePairs(CACHE_NAME).get(CACHE_ENTRY_KEY).getValue());
   }

   @Test
   public void testPersistedExpiration() throws Exception {
      StorageManager storageManager = new DummyKeyValuePairStorageManager();

      LocalCache cacheBeforeStop = new LocalCache(CACHE_NAME, true, CACHE_TIMEOUT, storageManager);

      cacheBeforeStop.start();

      try {
         cacheBeforeStop.put(CACHE_ENTRY_KEY, CACHE_ENTRY_VALUE);
         assertEquals(CACHE_ENTRY_VALUE, cacheBeforeStop.get(CACHE_ENTRY_KEY));
      } finally {
         cacheBeforeStop.stop();
      }

      assertEquals(CACHE_ENTRY_VALUE, storageManager.getPersistedKeyValuePairs(CACHE_NAME).get(CACHE_ENTRY_KEY).getValue());

      LocalCache cacheAfterStop = new LocalCache(CACHE_NAME, true, CACHE_TIMEOUT, storageManager);

      cacheAfterStop.start();

      try {
         assertEquals(CACHE_ENTRY_VALUE, cacheAfterStop.get(CACHE_ENTRY_KEY));
         Thread.sleep(CACHE_TIMEOUT * 2);
         assertNull(cacheAfterStop.get(CACHE_ENTRY_KEY));
      } finally {
         cacheAfterStop.stop();
      }

      assertNull(storageManager.getPersistedKeyValuePairs(CACHE_NAME).get(CACHE_ENTRY_KEY));
   }

   static class DummyKeyValuePairStorageManager extends NullStorageManager {
      private Map<String, Map<String, PersistedKeyValuePair>> mapPersistedKeyValuePairs = new ConcurrentHashMap<>();

      @Override
      public void storeKeyValuePair(PersistedKeyValuePair persistedKeyValuePair) throws Exception {
         Map<String, PersistedKeyValuePair> persistedKeyValuePairs = mapPersistedKeyValuePairs.get(persistedKeyValuePair.getMapId());
         if (persistedKeyValuePairs == null) {
            persistedKeyValuePairs = new HashMap<>();
            mapPersistedKeyValuePairs.put(persistedKeyValuePair.getMapId(), persistedKeyValuePairs);
         }
         persistedKeyValuePairs.put(persistedKeyValuePair.getKey(), persistedKeyValuePair);
      }

      @Override
      public void deleteKeyValuePair(String mapId, String key) throws Exception {
         Map<String, PersistedKeyValuePair> persistedKeyValuePairs = mapPersistedKeyValuePairs.get(mapId);
         if (persistedKeyValuePairs != null) {
            persistedKeyValuePairs.remove(key);
         }
      }

      @Override
      public Map<String, PersistedKeyValuePair> getPersistedKeyValuePairs(String mapId) {
         Map<String, PersistedKeyValuePair> persistedKeyValuePairs = mapPersistedKeyValuePairs.get(mapId);
         return persistedKeyValuePairs != null ? new HashMap<>(persistedKeyValuePairs) : new HashMap<>();
      }
   }
}
