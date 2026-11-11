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
package org.apache.activemq.artemis.core.server.cluster.ha;

import java.util.Map;
import java.util.Objects;

import org.apache.activemq.artemis.core.config.ha.ReplicationBackupPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.DistributedLockManagerConfiguration;
import org.apache.activemq.artemis.core.io.IOCriticalErrorListener;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.core.server.impl.ReplicationBackupActivation;
import org.apache.activemq.artemis.lockmanager.DistributedLockManager;

public class ReplicationBackupPolicy implements HAPolicy<ReplicationBackupActivation> {

   private final ReplicationPrimaryPolicy primaryPolicy;
   private final String groupName;
   private final String clusterName;
   private final int maxSavedReplicatedJournalsSize;
   private final long retryReplicationWait;
   private final DistributedLockManagerConfiguration managerConfiguration;
   private final boolean tryFailback;

   private ReplicationBackupPolicy(ReplicationBackupPolicyConfiguration configuration,
                                   ReplicationPrimaryPolicy primaryPolicy) {
      Objects.requireNonNull(primaryPolicy);
      this.clusterName = configuration.getClusterName();
      this.maxSavedReplicatedJournalsSize = configuration.getMaxSavedReplicatedJournalsSize();
      this.groupName = configuration.getGroupName();
      this.retryReplicationWait = configuration.getRetryReplicationWait();
      this.managerConfiguration = configuration.getDistributedManagerConfiguration();
      this.tryFailback = true;
      this.primaryPolicy = primaryPolicy;
   }

   private ReplicationBackupPolicy(ReplicationBackupPolicyConfiguration configuration) {
      this.clusterName = configuration.getClusterName();
      this.maxSavedReplicatedJournalsSize = configuration.getMaxSavedReplicatedJournalsSize();
      this.groupName = configuration.getGroupName();
      this.retryReplicationWait = configuration.getRetryReplicationWait();
      this.managerConfiguration = configuration.getDistributedManagerConfiguration();
      this.tryFailback = false;
      primaryPolicy = ReplicationPrimaryPolicy.failoverPolicy(
         configuration.getInitialReplicationSyncTimeout(),
         configuration.getGroupName(),
         configuration.getClusterName(),
         this,
         configuration.isAllowFailBack(),
         configuration.getDistributedManagerConfiguration());
   }

   public boolean isTryFailback() {
      return tryFailback;
   }

   /**
    * It creates a policy which primary policy won't cause to broker to try failback.
    */
   public static ReplicationBackupPolicy with(ReplicationBackupPolicyConfiguration configuration) {
      return new ReplicationBackupPolicy(configuration);
   }

   /**
    * It creates a companion backup policy for a natural-born primary: it would cause the broker to try failback.
    */
   static ReplicationBackupPolicy failback(long retryReplicationWait,
                                           int maxSavedReplicatedJournalsSize,
                                           String clusterName,
                                           String groupName,
                                           ReplicationPrimaryPolicy primaryPolicy,
                                           DistributedLockManagerConfiguration distributedManagerConfiguration) {
      return new ReplicationBackupPolicy(ReplicationBackupPolicyConfiguration.withDefault()
                                            .setRetryReplicationWait(retryReplicationWait)
                                            .setMaxSavedReplicatedJournalsSize(maxSavedReplicatedJournalsSize)
                                            .setClusterName(clusterName)
                                            .setGroupName(groupName)
                                            .setDistributedManagerConfiguration(distributedManagerConfiguration),
                                         primaryPolicy);
   }

   @Override
   public ReplicationBackupActivation createActivation(ActiveMQServerImpl server,
                                                       boolean wasPrimary,
                                                       Map<String, Object> activationParams,
                                                       IOCriticalErrorListener shutdownOnCriticalIO) throws Exception {
      return new ReplicationBackupActivation(server, DistributedLockManager.newInstanceOf(
         managerConfiguration.getClassName(), managerConfiguration.getProperties()), this);
   }

   @Override
   public boolean isSharedStore() {
      return false;
   }

   @Override
   public boolean isBackup() {
      return true;
   }

   @Override
   public boolean canScaleDown() {
      return false;
   }

   public String getClusterName() {
      return clusterName;
   }

   @Override
   public String getBackupGroupName() {
      return groupName;
   }

   public String getGroupName() {
      return groupName;
   }

   public ReplicationPrimaryPolicy getPrimaryPolicy() {
      return primaryPolicy;
   }

   public int getMaxSavedReplicatedJournalsSize() {
      return maxSavedReplicatedJournalsSize;
   }

   public long getRetryReplicationWait() {
      return retryReplicationWait;
   }

   @Override
   public boolean useQuorumManager() {
      return false;
   }
}
