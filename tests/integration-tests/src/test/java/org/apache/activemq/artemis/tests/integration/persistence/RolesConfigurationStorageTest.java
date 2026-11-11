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
import org.apache.activemq.artemis.core.persistence.config.PersistedSecuritySetting;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(ParameterizedTestExtension.class)
public class RolesConfigurationStorageTest extends StorageManagerTestBase {

   private Map<SimpleString, PersistedSecuritySetting> mapExpectedSets;

   @Parameters(name = "storeType={0}")
   public static Collection<Object[]> data() {
      Object[][] params = new Object[][]{{StoreConfiguration.StoreType.FILE}, {StoreConfiguration.StoreType.DATABASE}};
      return Arrays.asList(params);
   }

   public RolesConfigurationStorageTest(StoreConfiguration.StoreType storeType) {
      super(storeType);
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      mapExpectedSets = new HashMap<>();
   }

   protected void addSetting(PersistedSecuritySetting setting) throws Exception {
      mapExpectedSets.put(setting.getAddressMatch(), setting);
      journal.storeSecuritySetting(setting);
   }

   @TestTemplate
   public void testStoreSecuritySettings() throws Exception {
      addSetting(new PersistedSecuritySetting("a#", "a1", "a1", "a1", "a1", "a1", "a1", "a1", "a1", "a1", "a1", null, null));

      addSetting(new PersistedSecuritySetting("a2", "a1", null, "a1", "a1", "a1", "a1", "a1", "a1", "a1", "a1", null, null));

      checkSettings();

      rebootStorage();

      checkSettings();

      addSetting(new PersistedSecuritySetting("a2", "a1", null, "a1", "a1", "a1", "a1", "a1", "a1", "a1", "a1", null, null));

      addSetting(new PersistedSecuritySetting("a3", "a1", null, "a1", "a1", "a1", "a1", "a1", "a1", "a1", "a1", null, null));

      checkSettings();

      rebootStorage();

      checkSettings();
   }

   @TestTemplate
   public void testStoreSecuritySettings2() throws Exception {
      checkSettings();

      rebootStorage();

      checkSettings();

      addSetting(new PersistedSecuritySetting("a#", "a1", "a1", "a1", "a1", "a1", "a1", "a1", "a1", "a1", "a1", null, null));

      rebootStorage();

      checkSettings();

      rebootStorage();

      checkSettings();
   }

   private void checkSettings() throws Exception {
      List<PersistedSecuritySetting> listSetting = journal.recoverSecuritySettings();

      assertEquals(mapExpectedSets.size(), listSetting.size());

      for (PersistedSecuritySetting el : listSetting) {
         PersistedSecuritySetting el2 = mapExpectedSets.get(el.getAddressMatch());

         assertEquals(el, el2);
      }
   }
}
