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
package org.apache.activemq.artemis.core.persistence.config;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.core.journal.EncodingSupport;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;

/**
 * This class is only kept for compatibility reasons. The encode method should be dead code at this point and only the
 * decode should be used when versioning is at play.
 *
 * @deprecated Use PersistedAddressSettingJSON instead
 */
@Deprecated
public class PersistedAddressSetting extends AbstractPersistedAddressSetting implements EncodingSupport {


   public PersistedAddressSetting() {
      super();
   }

   @Override
   public String toString() {
      return "PersistedAddressSetting [storeId=" + storeId +
         ", addressMatch=" +
         addressMatch +
         ", setting=" +
         setting +
         "]";
   }

   @Override
   public void decode(ActiveMQBuffer buffer) {
      addressMatch = buffer.readSimpleString();

      setting = new AddressSettings();
      setting.decode(buffer);
   }

   @Override
   public void encode(ActiveMQBuffer buffer) {
      // no encode, this class is supposed to be for compatibility and reading old records only.
      throw new UnsupportedOperationException("PersistedAddressSetting should not be in use. Use PersistedAddressSettingJSON instead");
   }

   @Override
   public int getEncodeSize() {
      return addressMatch.sizeof() + setting.getEncodeSize();
   }

   @Override
   public byte getRecordType() {
      return JournalRecordIds.ADDRESS_SETTING_RECORD;
   }
}
