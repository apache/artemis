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

public class ReplicationPrimaryPolicyConfiguration implements HAPolicyConfiguration {

   private String groupName = null;

   private String clusterName = null;

   private long initialReplicationSyncTimeout = ActiveMQDefaultConfiguration.getDefaultInitialReplicationSyncTimeout();

   private Long retryReplicationWait = ActiveMQDefaultConfiguration.getDefaultRetryReplicationWait();

   private DistributedLockManagerConfiguration distributedManagerConfiguration = null;

   private String coordinationId = null;

   private int maxSavedReplicatedJournalsSize = ActiveMQDefaultConfiguration.getDefaultMaxSavedReplicatedJournalsSize();

   public static ReplicationPrimaryPolicyConfiguration withDefault() {
      return new ReplicationPrimaryPolicyConfiguration();
   }

   private ReplicationPrimaryPolicyConfiguration() {
   }

   @Override
   public TYPE getType() {
      return TYPE.REPLICATION_PRIMARY_LOCK_MANAGER;
   }

   public String getGroupName() {
      return groupName;
   }

   public ReplicationPrimaryPolicyConfiguration setGroupName(String groupName) {
      this.groupName = groupName;
      return this;
   }

   public String getClusterName() {
      return clusterName;
   }

   public ReplicationPrimaryPolicyConfiguration setClusterName(String clusterName) {
      this.clusterName = clusterName;
      return this;
   }

   public long getInitialReplicationSyncTimeout() {
      return initialReplicationSyncTimeout;
   }

   public ReplicationPrimaryPolicyConfiguration setInitialReplicationSyncTimeout(long initialReplicationSyncTimeout) {
      this.initialReplicationSyncTimeout = initialReplicationSyncTimeout;
      return this;
   }

   public void setRetryReplicationWait(Long retryReplicationWait) {
      this.retryReplicationWait = retryReplicationWait;
   }

   public Long getRetryReplicationWait() {
      return retryReplicationWait;
   }

   public ReplicationPrimaryPolicyConfiguration setDistributedManagerConfiguration(DistributedLockManagerConfiguration configuration) {
      this.distributedManagerConfiguration = configuration;
      return this;
   }

   public DistributedLockManagerConfiguration getDistributedManagerConfiguration() {
      return distributedManagerConfiguration;
   }

   public String getCoordinationId() {
      return coordinationId;
   }

   public void setCoordinationId(String newCoordinationId) {
      if (newCoordinationId == null) {
         return;
      }
      final int len = newCoordinationId.length();
      if (len >= 16) {
         this.coordinationId = newCoordinationId.substring(0, 16);
      } else if (len % 2 != 0) {
         // must be even for conversion to uuid, extend to next even
         this.coordinationId = newCoordinationId + "+";
      } else if (len > 0) {
         // run with it
         this.coordinationId = newCoordinationId;
      }
      if (this.coordinationId != null) {
         this.coordinationId = this.coordinationId.replace('-', '.');
      }
   }

   public int getMaxSavedReplicatedJournalsSize() {
      return maxSavedReplicatedJournalsSize;
   }

   public ReplicationPrimaryPolicyConfiguration setMaxSavedReplicatedJournalsSize(int maxSavedReplicatedJournalsSize) {
      this.maxSavedReplicatedJournalsSize = maxSavedReplicatedJournalsSize;
      return this;
   }
}
