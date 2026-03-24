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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.StoreConfiguration;
import org.apache.activemq.artemis.core.persistence.config.PersistedSecuritySetting;
import org.junit.Before;
import org.junit.Test;

public class RolesConfigurationStorageTest extends StorageManagerTestBase {

   private Map<SimpleString, PersistedSecuritySetting> mapExpectedSets;

   public RolesConfigurationStorageTest(StoreConfiguration.StoreType storeType) {
      super(storeType);
   }

   @Override
   @Before
   public void setUp() throws Exception {
      super.setUp();
      mapExpectedSets = new HashMap<>();
   }

   protected void addSetting(PersistedSecuritySetting setting) throws Exception {
      mapExpectedSets.put(setting.getAddressMatch(), setting);
      journal.storeSecuritySetting(setting);
   }

   @Test
   public void testStoreSecuritySettings() throws Exception {
      createStorage();

      addSetting(new PersistedSecuritySetting("a#", "a1", "a1", "a1", "a1", "a1", "a1", "a1", "a1", "a1", "a1"));

      addSetting(new PersistedSecuritySetting("a2", "a1", null, "a1", "a1", "a1", "a1", "a1", "a1", "a1", "a1"));

      journal.stop();

      checkSettings();

      createStorage();

      checkSettings();

      addSetting(new PersistedSecuritySetting("a2", "a1", null, "a1", "a1", "a1", "a1", "a1", "a1", "a1", "a1"));

      addSetting(new PersistedSecuritySetting("a3", "a1", null, "a1", "a1", "a1", "a1", "a1", "a1", "a1", "a1"));

      checkSettings();

      journal.stop();

      createStorage();

      checkSettings();

      journal.stop();

      journal = null;

   }

   @Test
   public void testStoreSecuritySettings2() throws Exception {
      createStorage();

      checkSettings();

      journal.stop();

      createStorage();

      checkSettings();

      addSetting(new PersistedSecuritySetting("a#", "a1", "a1", "a1", "a1", "a1", "a1", "a1", "a1", "a1", "a1"));

      journal.stop();

      createStorage();

      checkSettings();

      journal.stop();

      createStorage();

      checkSettings();
   }

   /**
    * @param journal
    * @throws Exception
    */
   private void checkSettings() throws Exception {
      List<PersistedSecuritySetting> listSetting = journal.recoverSecuritySettings();

      assertEquals(mapExpectedSets.size(), listSetting.size());

      for (PersistedSecuritySetting el : listSetting) {
         PersistedSecuritySetting el2 = mapExpectedSets.get(el.getAddressMatch());

         assertEquals(el, el2);
      }
   }

}
