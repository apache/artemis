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

import org.apache.activemq.artemis.core.config.ha.ReplicationPrimaryPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.DistributedLockManagerConfiguration;
import org.apache.activemq.artemis.core.io.IOCriticalErrorListener;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.core.server.impl.ReplicationPrimaryActivation;
import org.apache.activemq.artemis.lockmanager.DistributedLockManager;

public class ReplicationPrimaryPolicy implements HAPolicy<ReplicationPrimaryActivation> {

   private final ReplicationBackupPolicy backupPolicy;
   private final String clusterName;
   private final String groupName;
   private final long initialReplicationSyncTimeout;
   private final DistributedLockManagerConfiguration distributedManagerConfiguration;
   private final boolean allowAutoFailBack;
   private final String coordinationId;

   private ReplicationPrimaryPolicy(ReplicationPrimaryPolicyConfiguration configuration,
                                    ReplicationBackupPolicy backupPolicy,
                                    boolean allowAutoFailBack) {
      Objects.requireNonNull(backupPolicy);
      clusterName = configuration.getClusterName();
      groupName = configuration.getGroupName();
      initialReplicationSyncTimeout = configuration.getInitialReplicationSyncTimeout();
      distributedManagerConfiguration = configuration.getDistributedManagerConfiguration();
      coordinationId = configuration.getCoordinationId();
      this.allowAutoFailBack = allowAutoFailBack;
      this.backupPolicy = backupPolicy;
   }

   private ReplicationPrimaryPolicy(ReplicationPrimaryPolicyConfiguration config) {
      clusterName = config.getClusterName();
      groupName = config.getGroupName();
      coordinationId = config.getCoordinationId();
      initialReplicationSyncTimeout = config.getInitialReplicationSyncTimeout();
      distributedManagerConfiguration = config.getDistributedManagerConfiguration();
      this.allowAutoFailBack = false;
      backupPolicy = ReplicationBackupPolicy.failback(config.getRetryReplicationWait(), config.getMaxSavedReplicatedJournalsSize(), config.getClusterName(),
                                                      config.getGroupName(), this,
                                                      config.getDistributedManagerConfiguration());
   }

   /**
    * It creates a companion failing-over primary policy for a natural-born backup: it's allowed to allow auto fail-back
    * only if configured to do it.
    */
   static ReplicationPrimaryPolicy failoverPolicy(long initialReplicationSyncTimeout,
                                                  String groupName,
                                                  String clusterName,
                                                  ReplicationBackupPolicy replicaPolicy,
                                                  boolean allowAutoFailback,
                                                  DistributedLockManagerConfiguration distributedManagerConfiguration) {
      return new ReplicationPrimaryPolicy(ReplicationPrimaryPolicyConfiguration.withDefault()
                                             .setInitialReplicationSyncTimeout(initialReplicationSyncTimeout)
                                             .setGroupName(groupName)
                                             .setClusterName(clusterName)
                                             .setDistributedManagerConfiguration(distributedManagerConfiguration),
                                          replicaPolicy, allowAutoFailback);
   }

   /**
    * It creates a primary policy that never allow auto fail-back.
    * <p>
    * It's meant to be used for natural-born primary brokers: its backup policy is set to always try to fail-back.
    */
   public static ReplicationPrimaryPolicy with(ReplicationPrimaryPolicyConfiguration configuration) {
      return new ReplicationPrimaryPolicy(configuration);
   }

   public ReplicationBackupPolicy getBackupPolicy() {
      return backupPolicy;
   }

   @Override
   public ReplicationPrimaryActivation createActivation(ActiveMQServerImpl server,
                                                        boolean wasPrimary,
                                                        Map<String, Object> activationParams,
                                                        IOCriticalErrorListener shutdownOnCriticalIO) throws Exception {
      return new ReplicationPrimaryActivation(server,
                                              DistributedLockManager.newInstanceOf(
                                                 distributedManagerConfiguration.getClassName(),
                                                 distributedManagerConfiguration.getProperties()), this);
   }

   @Override
   public boolean isSharedStore() {
      return false;
   }

   @Override
   public boolean isBackup() {
      return false;
   }

   @Override
   public boolean isWaitForActivation() {
      return true;
   }

   @Override
   public boolean canScaleDown() {
      return false;
   }

   @Override
   public String getBackupGroupName() {
      return groupName;
   }

   public boolean isAllowAutoFailBack() {
      return allowAutoFailBack;
   }

   public String getClusterName() {
      return clusterName;
   }

   public long getInitialReplicationSyncTimeout() {
      return initialReplicationSyncTimeout;
   }

   public String getGroupName() {
      return groupName;
   }

   @Override
   public boolean useQuorumManager() {
      return false;
   }

   public String getCoordinationId() {
      return coordinationId;
   }
}
