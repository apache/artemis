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
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;

public class PersistedAddressSettingJSON extends AbstractPersistedAddressSetting {

   SimpleString jsonSetting;

   public PersistedAddressSettingJSON() {
      super();
   }

   @Override
   public AddressSettings getSetting() {
      if (setting == null) {
         setting = AddressSettings.fromJSON(jsonSetting.toString());
      }
      return super.getSetting();
   }

   public PersistedAddressSettingJSON(SimpleString addressMatch, AddressSettings setting, SimpleString jsonSetting) {
      super(addressMatch, setting);
      this.jsonSetting = jsonSetting;
   }

   public PersistedAddressSettingJSON(SimpleString addressMatch, AddressSettings setting, String jsonSetting) {
      this(addressMatch, setting, SimpleString.of(jsonSetting));
   }

   @Override
   public void decode(ActiveMQBuffer buffer) {
      addressMatch = buffer.readSimpleString();
      jsonSetting = buffer.readSimpleString();
      setting = AddressSettings.fromJSON(jsonSetting.toString());
   }

   @Override
   public void encode(ActiveMQBuffer buffer) {
      buffer.writeSimpleString(addressMatch);
      buffer.writeSimpleString(jsonSetting);
   }

   @Override
   public int getEncodeSize() {
      return addressMatch.sizeof() + jsonSetting.sizeof();
   }

   @Override
   public String toString() {
      return "PersistedAddressSettingJSON{" + "jsonSetting=" + jsonSetting + ", storeId=" + storeId + ", addressMatch=" + addressMatch + ", setting=" + setting + '}';
   }

   @Override
   public byte getRecordType() {
      return JournalRecordIds.ADDRESS_SETTING_RECORD_JSON;
   }
}
