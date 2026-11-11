/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.activemq.artemis.tests.integration.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.persistence.config.PersistedSecuritySetting;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.Test;

public class PersistedSecuritySettingTest {

   @Test
   public void testNPE() {
      PersistedSecuritySetting persistedSecuritySetting = new PersistedSecuritySetting();
      ActiveMQBuffer buffer = ActiveMQBuffers.fixedBuffer(persistedSecuritySetting.getEncodeSize());
      persistedSecuritySetting.encode(buffer);
      persistedSecuritySetting.decode(buffer);
      persistedSecuritySetting.getBrowseRoles();
      persistedSecuritySetting.getConsumeRoles();
      persistedSecuritySetting.getCreateAddressRoles();
      persistedSecuritySetting.getCreateDurableQueueRoles();
      persistedSecuritySetting.getCreateNonDurableQueueRoles();
      persistedSecuritySetting.getDeleteAddressRoles();
      persistedSecuritySetting.getDeleteDurableQueueRoles();
      persistedSecuritySetting.getDeleteNonDurableQueueRoles();
      persistedSecuritySetting.getManageRoles();
      persistedSecuritySetting.getSendRoles();
      persistedSecuritySetting.getViewRoles();
      persistedSecuritySetting.getEditRoles();
   }

   @Test
   public void testUpgradeAfterARTEMIS_4582() {
      // this buffer simulates a PersistedSecuritySetting journal entry from *before* ARTEMIS-4582
      SimpleString match = RandomUtil.randomUUIDSimpleString();
      ActiveMQBuffer buffer = ActiveMQBuffers.fixedBuffer((SimpleString.sizeofNullableString(match)) + 10);
      buffer.writeSimpleString(match);
      for (int i = 0; i < 10; i++) {
         buffer.writeNullableSimpleString(null);
      }

      PersistedSecuritySetting persistedSecuritySetting = new PersistedSecuritySetting();
      persistedSecuritySetting.decode(buffer);
      assertEquals(match, persistedSecuritySetting.getAddressMatch());
      assertNull(persistedSecuritySetting.getBrowseRoles());
      assertNull(persistedSecuritySetting.getConsumeRoles());
      assertNull(persistedSecuritySetting.getCreateAddressRoles());
      assertNull(persistedSecuritySetting.getCreateDurableQueueRoles());
      assertNull(persistedSecuritySetting.getCreateNonDurableQueueRoles());
      assertNull(persistedSecuritySetting.getDeleteAddressRoles());
      assertNull(persistedSecuritySetting.getDeleteDurableQueueRoles());
      assertNull(persistedSecuritySetting.getDeleteNonDurableQueueRoles());
      assertNull(persistedSecuritySetting.getManageRoles());
      assertNull(persistedSecuritySetting.getSendRoles());
      assertNull(persistedSecuritySetting.getViewRoles());
      assertNull(persistedSecuritySetting.getEditRoles());
   }
}
