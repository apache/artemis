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
package org.apache.activemq.artemis.core.persistence.config;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.core.config.BridgeConfiguration;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds;

public class PersistedBridgeConfiguration extends PersistedConfiguration {

   private BridgeConfiguration bridgeConfiguration;

   @Override
   public String toString() {
      return "PersistedBridgeConfiguration{" + "storeId=" + storeId + ", bridgeConfiguration=" + bridgeConfiguration + '}';
   }

   public PersistedBridgeConfiguration(BridgeConfiguration bridgeConfiguration) {
      this.bridgeConfiguration = bridgeConfiguration;
   }

   public PersistedBridgeConfiguration() {
      bridgeConfiguration = new BridgeConfiguration();
   }

   @Override
   public int getEncodeSize() {
      return bridgeConfiguration.getEncodeSize();
   }

   @Override
   public void encode(ActiveMQBuffer buffer) {
      bridgeConfiguration.encode(buffer);
   }

   @Override
   public void decode(ActiveMQBuffer buffer) {
      bridgeConfiguration.decode(buffer);
   }

   @Override
   public String getName() {
      return bridgeConfiguration.getParentName();
   }

   @Override
   public byte getRecordType() {
      return JournalRecordIds.BRIDGE_RECORD;
   }

   public BridgeConfiguration getBridgeConfiguration() {
      return bridgeConfiguration;
   }
}
