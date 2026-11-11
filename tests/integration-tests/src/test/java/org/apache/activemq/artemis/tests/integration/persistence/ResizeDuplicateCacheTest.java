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
package org.apache.activemq.artemis.tests.integration.persistence;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds;
import org.apache.activemq.artemis.core.postoffice.DuplicateIDCache;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ResizeDuplicateCacheTest extends ActiveMQTestBase {

   @Test
   public void testReloadCache() throws Exception {
      internalReloadCache(false, null);
   }

   @Test
   public void testReloadCacheAdditionalSettings() throws Exception {
      // this variance will add a settings on the same namespace for something else
      // this will validate that the setting should be merged
      internalReloadCache(true, null);
   }

   @Test
   public void testReloadCacheAdditionalSettingsValueSet() throws Exception {
      // this variance will add a settings on the same namespace for something else
      // this will validate that the setting should be merged
      internalReloadCache(true, 50_000);
   }

   public void internalReloadCache(boolean additionalSettings, Integer preExistingCacheValue) throws Exception {
      int duplicateSize = 30;
      SimpleString randomString = RandomUtil.randomUUIDSimpleString();

      ActiveMQServer server = createServer(true, false);
      server.start();

      ActiveMQServerControl serverControl = server.getActiveMQServerControl();

      if (additionalSettings) {
         AddressSettings settings = new AddressSettings().setDefaultRingSize(3333);
         if (preExistingCacheValue != null) {
            settings.setIDCacheSize(preExistingCacheValue);
         }
         serverControl.addAddressSettings(randomString.toString(), settings.toJSON());
         String json = serverControl.getAddressSettingsAsJSON(randomString.toString());
         AddressSettings settingsFromJson = AddressSettings.fromJSON(json);
         Assertions.assertEquals(preExistingCacheValue, settingsFromJson.getIDCacheSize());
         Assertions.assertEquals(3333, settingsFromJson.getDefaultRingSize());
      }

      DuplicateIDCache duplicateIDCache = server.getPostOffice().getDuplicateIDCache(randomString, duplicateSize);

      for (int i = 0; i < duplicateSize * 2; i++) {
         duplicateIDCache.addToCache(("a" + i).getBytes(StandardCharsets.UTF_8));
      }

      server.stop();
      server = createServer(true, false);
      server.start();
      serverControl = server.getActiveMQServerControl();

      duplicateIDCache = server.getPostOffice().getDuplicateIDCache(randomString, duplicateSize);

      Assertions.assertEquals(duplicateSize, duplicateIDCache.getSize());

      server.getStorageManager().getMessageJournal().scheduleCompactAndBlock(10_000);
      Map<Integer, AtomicInteger> records = countJournal(server.getConfiguration());

      AtomicInteger duplicateRecordsCount = records.get((int) JournalRecordIds.DUPLICATE_ID);
      Assertions.assertNotNull(duplicateRecordsCount);
      Assertions.assertEquals(duplicateSize, duplicateRecordsCount.get());

      if (additionalSettings) {
         String json = serverControl.getAddressSettingsAsJSON(randomString.toString());

         // checking if the previous value was merged as expected
         AddressSettings settingsFromJson = AddressSettings.fromJSON(json);
         Assertions.assertEquals(Integer.valueOf(duplicateSize), settingsFromJson.getIDCacheSize());
         Assertions.assertEquals(3333, settingsFromJson.getDefaultRingSize());
      }

      server.stop();
   }
}