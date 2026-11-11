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

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.core.config.HAPolicyConfiguration;

public class ReplicationBackupPolicyConfiguration implements HAPolicyConfiguration {

   private String clusterName = null;

   private int maxSavedReplicatedJournalsSize = ActiveMQDefaultConfiguration.getDefaultMaxSavedReplicatedJournalsSize();

   private String groupName = null;

   // used in the replicated policy after failover
   private boolean allowFailBack = false;

   private long initialReplicationSyncTimeout = ActiveMQDefaultConfiguration.getDefaultInitialReplicationSyncTimeout();

   private long retryReplicationWait = ActiveMQDefaultConfiguration.getDefaultRetryReplicationWait();

   private DistributedLockManagerConfiguration distributedManagerConfiguration = null;

   public static final ReplicationBackupPolicyConfiguration withDefault() {
      return new ReplicationBackupPolicyConfiguration();
   }

   private ReplicationBackupPolicyConfiguration() {
   }

   @Override
   public HAPolicyConfiguration.TYPE getType() {
      return TYPE.REPLICATION_BACKUP_LOCK_MANAGER;
   }

   public String getClusterName() {
      return clusterName;
   }

   public ReplicationBackupPolicyConfiguration setClusterName(String clusterName) {
      this.clusterName = clusterName;
      return this;
   }

   public int getMaxSavedReplicatedJournalsSize() {
      return maxSavedReplicatedJournalsSize;
   }

   public ReplicationBackupPolicyConfiguration setMaxSavedReplicatedJournalsSize(int maxSavedReplicatedJournalsSize) {
      this.maxSavedReplicatedJournalsSize = maxSavedReplicatedJournalsSize;
      return this;
   }

   public String getGroupName() {
      return groupName;
   }

   public ReplicationBackupPolicyConfiguration setGroupName(String groupName) {
      this.groupName = groupName;
      return this;
   }

   public boolean isAllowFailBack() {
      return allowFailBack;
   }

   public ReplicationBackupPolicyConfiguration setAllowFailBack(boolean allowFailBack) {
      this.allowFailBack = allowFailBack;
      return this;
   }

   public long getInitialReplicationSyncTimeout() {
      return initialReplicationSyncTimeout;
   }

   public ReplicationBackupPolicyConfiguration setInitialReplicationSyncTimeout(long initialReplicationSyncTimeout) {
      this.initialReplicationSyncTimeout = initialReplicationSyncTimeout;
      return this;
   }

   public long getRetryReplicationWait() {
      return retryReplicationWait;
   }

   public ReplicationBackupPolicyConfiguration setRetryReplicationWait(long retryReplicationWait) {
      this.retryReplicationWait = retryReplicationWait;
      return this;
   }

   public ReplicationBackupPolicyConfiguration setDistributedManagerConfiguration(DistributedLockManagerConfiguration configuration) {
      this.distributedManagerConfiguration = configuration;
      return this;
   }

   public DistributedLockManagerConfiguration getDistributedManagerConfiguration() {
      return distributedManagerConfiguration;
   }
}
