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
import org.apache.activemq.artemis.core.server.impl.Activation;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.core.server.impl.SharedNothingBackupActivation;

public class ReplicaPolicy extends BackupPolicy {

   private String clusterName;

   private int maxSavedReplicatedJournalsSize = ActiveMQDefaultConfiguration.getDefaultMaxSavedReplicatedJournalsSize();

   private String groupName = null;

   private boolean restartBackup = ActiveMQDefaultConfiguration.isDefaultRestartBackup();

   //used if we create a replicated policy for when we become live.
   private boolean allowFailback = ActiveMQDefaultConfiguration.isDefaultAllowAutoFailback();

   private long initialReplicationSyncTimeout = ActiveMQDefaultConfiguration.getDefaultInitialReplicationSyncTimeout();

   // what quorum size to use for voting
   private int quorumSize;

   // whether this broker should vote to remain active
   private boolean voteOnReplicationFailure;

   private ReplicatedPolicy replicatedPolicy;

   private final NetworkHealthCheck networkHealthCheck;

   private int voteRetries;

   private long voteRetryWait;

   private final int quorumVoteWait;

   private long retryReplicationWait;

   public ReplicaPolicy(final NetworkHealthCheck networkHealthCheck, int quorumVoteWait) {
      this.networkHealthCheck = networkHealthCheck;
      this.quorumVoteWait = quorumVoteWait;
   }

   public ReplicaPolicy(final NetworkHealthCheck networkHealthCheck,
                        ReplicatedPolicy replicatedPolicy,
                        int quorumVoteWait) {
      this.networkHealthCheck = networkHealthCheck;
      this.replicatedPolicy = replicatedPolicy;
      this.quorumVoteWait = quorumVoteWait;
   }

   public ReplicaPolicy(String clusterName,
                        int maxSavedReplicatedJournalsSize,
                        String groupName,
                        boolean restartBackup,
                        boolean allowFailback,
                        long initialReplicationSyncTimeout,
                        ScaleDownPolicy scaleDownPolicy,
                        NetworkHealthCheck networkHealthCheck,
                        boolean voteOnReplicationFailure,
                        int quorumSize,
                        int voteRetries,
                        long voteRetryWait,
                        int quorumVoteWait,
                        long retryReplicationWait) {
      this.clusterName = clusterName;
      this.maxSavedReplicatedJournalsSize = maxSavedReplicatedJournalsSize;
      this.groupName = groupName;
      this.restartBackup = restartBackup;
      this.allowFailback = allowFailback;
      this.initialReplicationSyncTimeout = initialReplicationSyncTimeout;
      this.quorumSize = quorumSize;
      this.voteRetries = voteRetries;
      this.voteRetryWait = voteRetryWait;
      this.retryReplicationWait = retryReplicationWait;
      this.scaleDownPolicy = scaleDownPolicy;
      this.networkHealthCheck = networkHealthCheck;
      this.voteOnReplicationFailure = voteOnReplicationFailure;
      this.quorumVoteWait = quorumVoteWait;
      this.retryReplicationWait = retryReplicationWait;
   }

   public ReplicaPolicy(String clusterName,
                        int maxSavedReplicatedJournalsSize,
                        String groupName,
                        ReplicatedPolicy replicatedPolicy,
                        NetworkHealthCheck networkHealthCheck,
                        int quorumVoteWait) {
      this.clusterName = clusterName;
      this.maxSavedReplicatedJournalsSize = maxSavedReplicatedJournalsSize;
      this.groupName = groupName;
      this.replicatedPolicy = replicatedPolicy;
      this.networkHealthCheck = networkHealthCheck;
      this.quorumVoteWait = quorumVoteWait;
   }

   public String getClusterName() {
      return clusterName;
   }

   public void setClusterName(String clusterName) {
      this.clusterName = clusterName;
   }

   public int getMaxSavedReplicatedJournalsSize() {
      return maxSavedReplicatedJournalsSize;
   }

   public void setMaxSavedReplicatedJournalsSize(int maxSavedReplicatedJournalsSize) {
      this.maxSavedReplicatedJournalsSize = maxSavedReplicatedJournalsSize;
   }

   public ReplicatedPolicy getReplicatedPolicy() {
      if (replicatedPolicy == null) {
         replicatedPolicy = new ReplicatedPolicy(false, allowFailback, initialReplicationSyncTimeout, groupName, clusterName, this, networkHealthCheck, voteOnReplicationFailure, quorumSize, voteRetries, voteRetryWait, quorumVoteWait);
      }
      return replicatedPolicy;
   }

   public void setReplicatedPolicy(ReplicatedPolicy replicatedPolicy) {
      this.replicatedPolicy = replicatedPolicy;
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
   public boolean isRestartBackup() {
      return restartBackup;
   }

   @Override
   public void setRestartBackup(boolean restartBackup) {
      this.restartBackup = restartBackup;
   }

   @Override
   public boolean isSharedStore() {
      return false;
   }

   @Override
   public boolean canScaleDown() {
      return scaleDownPolicy != null;
   }

   public boolean isAllowFailback() {
      return allowFailback;
   }

   public void setAllowFailback(boolean allowFailback) {
      this.allowFailback = allowFailback;
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

   @Override
   public Activation createActivation(ActiveMQServerImpl server,
                                      boolean wasPrimary,
                                      Map<String, Object> activationParams,
                                      IOCriticalErrorListener ioCriticalErrorListener) throws Exception {
      SharedNothingBackupActivation backupActivation = new SharedNothingBackupActivation(server, wasPrimary, activationParams, ioCriticalErrorListener, this, networkHealthCheck);
      backupActivation.init();
      return backupActivation;
   }

   public void setQuorumSize(int quorumSize) {
      this.quorumSize = quorumSize;
   }

   public int getQuorumSize() {
      return quorumSize;
   }

   public void setVoteOnReplicationFailure(boolean voteOnReplicationFailure) {
      this.voteOnReplicationFailure = voteOnReplicationFailure;
   }

   public boolean isVoteOnReplicationFailure() {
      return voteOnReplicationFailure;
   }

   public void setVoteRetries(int voteRetries) {
      this.voteRetries = voteRetries;
   }

   public void setVoteRetryWait(long voteRetryWait) {
      this.voteRetryWait = voteRetryWait;
   }

   public int getVoteRetries() {
      return voteRetries;
   }

   public long getVoteRetryWait() {
      return voteRetryWait;
   }

   public int getQuorumVoteWait() {
      return quorumVoteWait;
   }

   public long getRetryReplicationWait() {
      return retryReplicationWait;
   }

   public void setretryReplicationWait(long retryReplicationWait) {
      this.retryReplicationWait = retryReplicationWait;
   }
}
