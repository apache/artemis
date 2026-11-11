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

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.core.io.IOCriticalErrorListener;
import org.apache.activemq.artemis.core.server.NetworkHealthCheck;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.core.server.impl.PrimaryActivation;
import org.apache.activemq.artemis.core.server.impl.SharedNothingPrimaryActivation;

public class ReplicatedPolicy implements HAPolicy<PrimaryActivation> {

   private boolean checkForPrimaryServer = ActiveMQDefaultConfiguration.isDefaultCheckForActiveServer();

   private String groupName = null;

   private String clusterName;

   private int maxSavedReplicatedJournalsSize = ActiveMQDefaultConfiguration.getDefaultMaxSavedReplicatedJournalsSize();

   private long initialReplicationSyncTimeout = ActiveMQDefaultConfiguration.getDefaultInitialReplicationSyncTimeout();

   /*
    * these are only set by the ReplicaPolicy after failover to decide if the primary server can failback, these should
    * not be exposed in configuration.
    */
   private boolean allowAutoFailBack = ActiveMQDefaultConfiguration.isDefaultAllowAutoFailback();

   // whether this broker should vote to remain active
   private boolean voteOnReplicationFailure;

   // what quorum size to use for voting
   private int quorumSize;

   private int voteRetries;

   private long voteRetryWait;

   private long retryReplicationWait;

   // this is only used as the policy when the server is started as a backup after a failover
   private ReplicaPolicy replicaPolicy;

   private final NetworkHealthCheck networkHealthCheck;

   private final int quorumVoteWait;

   public ReplicatedPolicy(NetworkHealthCheck networkHealthCheck, int quorumVoteWait) {
      replicaPolicy = new ReplicaPolicy(networkHealthCheck, this, quorumVoteWait);
      this.networkHealthCheck = networkHealthCheck;
      this.quorumVoteWait = quorumVoteWait;
   }

   public ReplicatedPolicy(boolean checkForPrimaryServer,
                           String groupName,
                           String clusterName,
                           int maxSavedReplicatedJournalsSize,
                           long initialReplicationSyncTimeout,
                           NetworkHealthCheck networkHealthCheck,
                           boolean voteOnReplicationFailure,
                           int quorumSize,
                           int voteRetries,
                           long voteRetryWait,
                           int quorumVoteWait,
                           long retryReplicationWait) {
      this.checkForPrimaryServer = checkForPrimaryServer;
      this.groupName = groupName;
      this.clusterName = clusterName;
      this.maxSavedReplicatedJournalsSize = maxSavedReplicatedJournalsSize;
      this.initialReplicationSyncTimeout = initialReplicationSyncTimeout;
      this.networkHealthCheck = networkHealthCheck;
      this.voteOnReplicationFailure = voteOnReplicationFailure;
      this.quorumSize = quorumSize;
      this.voteRetries = voteRetries;
      this.voteRetryWait = voteRetryWait;
      this.quorumVoteWait = quorumVoteWait;
      this.retryReplicationWait = retryReplicationWait;
   }

   public ReplicatedPolicy(boolean checkForPrimaryServer,
                           boolean allowAutoFailBack,
                           long initialReplicationSyncTimeout,
                           String groupName,
                           String clusterName,
                           ReplicaPolicy replicaPolicy,
                           NetworkHealthCheck networkHealthCheck,
                           boolean voteOnReplicationFailure,
                           int quorumSize,
                           int voteRetries,
                           long voteRetryWait,
                           int quorumVoteWait) {
      this.checkForPrimaryServer = checkForPrimaryServer;
      this.clusterName = clusterName;
      this.groupName = groupName;
      this.allowAutoFailBack = allowAutoFailBack;
      this.initialReplicationSyncTimeout = initialReplicationSyncTimeout;
      this.replicaPolicy = replicaPolicy;
      this.networkHealthCheck = networkHealthCheck;
      this.voteOnReplicationFailure = voteOnReplicationFailure;
      this.quorumSize = quorumSize;
      this.quorumVoteWait = quorumVoteWait;
   }

   public boolean isCheckForPrimaryServer() {
      return checkForPrimaryServer;
   }

   public void setCheckForPrimaryServer(boolean checkForPrimaryServer) {
      this.checkForPrimaryServer = checkForPrimaryServer;
   }

   public boolean isAllowAutoFailBack() {
      return allowAutoFailBack;
   }

   @Deprecated
   public long getFailbackDelay() {
      return -1;
   }

   @Deprecated
   public void setFailbackDelay(long failbackDelay) {
   }

   public long getInitialReplicationSyncTimeout() {
      return initialReplicationSyncTimeout;
   }

   public void setInitialReplicationSyncTimeout(long initialReplicationSyncTimeout) {
      this.initialReplicationSyncTimeout = initialReplicationSyncTimeout;
   }

   public String getClusterName() {
      return clusterName;
   }

   public void setClusterName(String clusterName) {
      this.clusterName = clusterName;
   }

   public ReplicaPolicy getReplicaPolicy() {
      if (replicaPolicy == null) {
         replicaPolicy = new ReplicaPolicy(networkHealthCheck, this, quorumVoteWait);
         replicaPolicy.setQuorumSize(quorumSize);
         replicaPolicy.setVoteOnReplicationFailure(voteOnReplicationFailure);
         replicaPolicy.setVoteRetries(voteRetries);
         replicaPolicy.setVoteRetryWait(voteRetryWait);
         replicaPolicy.setretryReplicationWait(retryReplicationWait);
         replicaPolicy.setMaxSavedReplicatedJournalsSize(maxSavedReplicatedJournalsSize);
         if (clusterName != null && !clusterName.isEmpty()) {
            replicaPolicy.setClusterName(clusterName);
         }
         if (groupName != null && !groupName.isEmpty()) {
            replicaPolicy.setGroupName(groupName);
         }
      }
      return replicaPolicy;
   }

   public void setReplicaPolicy(ReplicaPolicy replicaPolicy) {
      this.replicaPolicy = replicaPolicy;
   }

   @Override
   public String getBackupGroupName() {
      return groupName;
   }

   public String getGroupName() {
      return groupName;
   }

   public void setGroupName(String groupName) {
      this.groupName = groupName;
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
   public boolean canScaleDown() {
      return false;
   }

   public void setAllowAutoFailBack(boolean allowAutoFailBack) {
      this.allowAutoFailBack = allowAutoFailBack;
   }

   public boolean isVoteOnReplicationFailure() {
      return voteOnReplicationFailure;
   }

   @Override
   public PrimaryActivation createActivation(ActiveMQServerImpl server,
                                             boolean wasPrimary,
                                             Map<String, Object> activationParams,
                                             IOCriticalErrorListener ioCriticalErrorListener) {
      return new SharedNothingPrimaryActivation(server, this);
   }

   public int getQuorumSize() {
      return quorumSize;
   }

   public void setQuorumSize(int quorumSize) {
      this.quorumSize = quorumSize;
   }

   public int getQuorumVoteWait() {
      return quorumVoteWait;
   }

   public long getRetryReplicationWait() {
      return retryReplicationWait;
   }

   public int getMaxSavedReplicatedJournalsSize() {
      return maxSavedReplicatedJournalsSize;
   }
}
