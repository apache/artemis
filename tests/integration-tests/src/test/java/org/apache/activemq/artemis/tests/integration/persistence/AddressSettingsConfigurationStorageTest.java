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

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.StoreConfiguration;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.persistence.config.AbstractPersistedAddressSetting;
import org.apache.activemq.artemis.core.persistence.config.PersistedAddressSettingJSON;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.settings.impl.DeletionPolicy;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(ParameterizedTestExtension.class)
public class AddressSettingsConfigurationStorageTest extends StorageManagerTestBase {

   private Map<SimpleString, PersistedAddressSettingJSON> mapExpectedAddresses;

   @Parameters(name = "storeType={0}")
   public static Collection<Object[]> data() {
      Object[][] params = new Object[][]{{StoreConfiguration.StoreType.FILE}, {StoreConfiguration.StoreType.DATABASE}};
      return Arrays.asList(params);
   }

   public AddressSettingsConfigurationStorageTest(StoreConfiguration.StoreType storeType) {
      super(storeType);
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      mapExpectedAddresses = new HashMap<>();
   }

   protected void addAddress(StorageManager journal1, String address, AddressSettings setting) throws Exception {
      SimpleString str = SimpleString.of(address);
      PersistedAddressSettingJSON persistedSetting = new PersistedAddressSettingJSON(str, setting, setting.toJSON());
      mapExpectedAddresses.put(str, persistedSetting);
      journal1.storeAddressSetting(persistedSetting);
   }

   @TestTemplate
   public void testStoreSecuritySettings() throws Exception {
      AddressSettings setting = new AddressSettings()
         .setAddressFullMessagePolicy(AddressFullMessagePolicy.BLOCK)
         .setDeadLetterAddress(SimpleString.of("some-test"));

      addAddress(journal, "a2", setting);

      rebootStorage();

      checkAddresses(journal);

      setting = new AddressSettings().setDeadLetterAddress(SimpleString.of("new-adddress"));

      // Replacing the first setting
      addAddress(journal, "a1", setting);

      rebootStorage();

      checkAddresses(journal);
   }

   @TestTemplate
   public void testStoreConfigDeleteSettings() throws Exception {
      AddressSettings setting = new AddressSettings()
         .setConfigDeleteDiverts(DeletionPolicy.FORCE)
         .setConfigDeleteAddresses(DeletionPolicy.FORCE)
         .setConfigDeleteQueues(DeletionPolicy.FORCE);

      addAddress(journal, "a1", setting);

      rebootStorage();

      checkAddresses(journal);
   }

   private void checkAddresses(StorageManager journal1) throws Exception {
      List<AbstractPersistedAddressSetting> listSetting = journal1.recoverAddressSettings();

      assertEquals(mapExpectedAddresses.size(), listSetting.size());

      for (AbstractPersistedAddressSetting el : listSetting) {
         PersistedAddressSettingJSON el2 = mapExpectedAddresses.get(el.getAddressMatch());

         assertEquals(el.getAddressMatch(), el2.getAddressMatch());
         assertEquals(el.getSetting(), el2.getSetting());
      }
   }
}
