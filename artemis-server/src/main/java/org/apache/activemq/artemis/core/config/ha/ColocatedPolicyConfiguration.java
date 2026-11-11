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
package org.apache.activemq.artemis.core.config.ha;

import java.util.ArrayList;
import java.util.List;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.core.config.HAPolicyConfiguration;

public class ColocatedPolicyConfiguration implements HAPolicyConfiguration {

   private boolean requestBackup = ActiveMQDefaultConfiguration.isDefaultHapolicyRequestBackup();

   private int backupRequestRetries = ActiveMQDefaultConfiguration.getDefaultHapolicyBackupRequestRetries();

   private long backupRequestRetryInterval = ActiveMQDefaultConfiguration.getDefaultHapolicyBackupRequestRetryInterval();

   private int maxBackups = ActiveMQDefaultConfiguration.getDefaultHapolicyMaxBackups();

   private int backupPortOffset = ActiveMQDefaultConfiguration.getDefaultHapolicyBackupPortOffset();

   private List<String> excludedConnectors = new ArrayList<>();

   private int portOffset = ActiveMQDefaultConfiguration.getDefaultHapolicyBackupPortOffset();

   private HAPolicyConfiguration primaryConfig;

   private HAPolicyConfiguration backupConfig;

   public ColocatedPolicyConfiguration() {
   }

   @Override
   public TYPE getType() {
      return TYPE.COLOCATED;
   }

   public boolean isRequestBackup() {
      return requestBackup;
   }

   public ColocatedPolicyConfiguration setRequestBackup(boolean requestBackup) {
      this.requestBackup = requestBackup;
      return this;
   }

   public int getBackupRequestRetries() {
      return backupRequestRetries;
   }

   public ColocatedPolicyConfiguration setBackupRequestRetries(int backupRequestRetries) {
      this.backupRequestRetries = backupRequestRetries;
      return this;
   }

   public long getBackupRequestRetryInterval() {
      return backupRequestRetryInterval;
   }

   public ColocatedPolicyConfiguration setBackupRequestRetryInterval(long backupRequestRetryInterval) {
      this.backupRequestRetryInterval = backupRequestRetryInterval;
      return this;
   }

   public int getMaxBackups() {
      return maxBackups;
   }

   public ColocatedPolicyConfiguration setMaxBackups(int maxBackups) {
      this.maxBackups = maxBackups;
      return this;
   }

   public int getBackupPortOffset() {
      return backupPortOffset;
   }

   public ColocatedPolicyConfiguration setBackupPortOffset(int backupPortOffset) {
      this.backupPortOffset = backupPortOffset;
      return this;
   }

   public List<String> getExcludedConnectors() {
      return excludedConnectors;
   }

   public ColocatedPolicyConfiguration setExcludedConnectors(List<String> excludedConnectors) {
      this.excludedConnectors = excludedConnectors;
      return this;
   }

   public int getPortOffset() {
      return portOffset;
   }

   public ColocatedPolicyConfiguration setPortOffset(int portOffset) {
      this.portOffset = portOffset;
      return this;
   }

   public HAPolicyConfiguration getPrimaryConfig() {
      return primaryConfig;
   }

   public ColocatedPolicyConfiguration setPrimaryConfig(HAPolicyConfiguration primaryConfig) {
      this.primaryConfig = primaryConfig;
      return this;
   }

   public HAPolicyConfiguration getBackupConfig() {
      return backupConfig;
   }

   public ColocatedPolicyConfiguration setBackupConfig(HAPolicyConfiguration backupConfig) {
      this.backupConfig = backupConfig;
      return this;
   }
}
