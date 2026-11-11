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
import org.apache.activemq.artemis.core.config.ScaleDownConfiguration;

public class ReplicaPolicyConfiguration implements HAPolicyConfiguration {

   private String clusterName = null;

   private int maxSavedReplicatedJournalsSize = ActiveMQDefaultConfiguration.getDefaultMaxSavedReplicatedJournalsSize();

   private String groupName = null;

   private boolean restartBackup = ActiveMQDefaultConfiguration.isDefaultRestartBackup();

   private ScaleDownConfiguration scaleDownConfiguration;

   // used in the replicated policy after failover
   private boolean allowFailBack = false;

   private long initialReplicationSyncTimeout = ActiveMQDefaultConfiguration.getDefaultInitialReplicationSyncTimeout();

   private boolean voteOnReplicationFailure = ActiveMQDefaultConfiguration.getDefaultVoteOnReplicationFailure();

   private int quorumSize = ActiveMQDefaultConfiguration.getDefaultQuorumSize();

   private int voteRetries = ActiveMQDefaultConfiguration.getDefaultVoteRetries();

   private long voteRetryWait = ActiveMQDefaultConfiguration.getDefaultVoteRetryWait();

   private int quorumVoteWait = ActiveMQDefaultConfiguration.getDefaultQuorumVoteWait();

   private long retryReplicationWait = ActiveMQDefaultConfiguration.getDefaultRetryReplicationWait();

   public ReplicaPolicyConfiguration() {
   }


   @Override
   public TYPE getType() {
      return TYPE.REPLICATION_BACKUP_QUORUM_VOTING;
   }

   public ScaleDownConfiguration getScaleDownConfiguration() {
      return scaleDownConfiguration;
   }

   public ReplicaPolicyConfiguration setScaleDownConfiguration(ScaleDownConfiguration scaleDownConfiguration) {
      this.scaleDownConfiguration = scaleDownConfiguration;
      return this;
   }

   public String getClusterName() {
      return clusterName;
   }

   public ReplicaPolicyConfiguration setClusterName(String clusterName) {
      this.clusterName = clusterName;
      return this;
   }

   public int getMaxSavedReplicatedJournalsSize() {
      return maxSavedReplicatedJournalsSize;
   }

   public ReplicaPolicyConfiguration setMaxSavedReplicatedJournalsSize(int maxSavedReplicatedJournalsSize) {
      this.maxSavedReplicatedJournalsSize = maxSavedReplicatedJournalsSize;
      return this;
   }

   public String getGroupName() {
      return groupName;
   }

   public ReplicaPolicyConfiguration setGroupName(String groupName) {
      this.groupName = groupName;
      return this;
   }

   public boolean isRestartBackup() {
      return restartBackup;
   }

   public ReplicaPolicyConfiguration setRestartBackup(boolean restartBackup) {
      this.restartBackup = restartBackup;
      return this;
   }

   public boolean isAllowFailBack() {
      return allowFailBack;
   }

   public ReplicaPolicyConfiguration setAllowFailBack(boolean allowFailBack) {
      this.allowFailBack = allowFailBack;
      return this;
   }

   @Deprecated
   public ReplicaPolicyConfiguration setFailbackDelay(long failbackDelay) {
      return this;
   }

   @Deprecated
   public long getFailbackDelay() {
      return -1;
   }

   public long getInitialReplicationSyncTimeout() {
      return initialReplicationSyncTimeout;
   }

   public ReplicaPolicyConfiguration setInitialReplicationSyncTimeout(long initialReplicationSyncTimeout) {
      this.initialReplicationSyncTimeout = initialReplicationSyncTimeout;
      return this;
   }

   public boolean getVoteOnReplicationFailure() {
      return voteOnReplicationFailure;
   }

   public void setVoteOnReplicationFailure(boolean voteOnReplicationFailure) {
      this.voteOnReplicationFailure = voteOnReplicationFailure;
   }

   public int getQuorumSize() {
      return quorumSize;
   }

   public void setQuorumSize(int quorumSize) {
      this.quorumSize = quorumSize;
   }

   public int getVoteRetries() {
      return voteRetries;
   }

   public void setVoteRetries(int voteRetries) {
      this.voteRetries = voteRetries;
   }

   public void setVoteRetryWait(long voteRetryWait) {
      this.voteRetryWait = voteRetryWait;
   }

   public long getVoteRetryWait() {
      return voteRetryWait;
   }

   public int getQuorumVoteWait() {
      return quorumVoteWait;
   }

   public void setQuorumVoteWait(int quorumVoteWait) {
      this.quorumVoteWait = quorumVoteWait;
   }

   public long getRetryReplicationWait() {
      return retryReplicationWait;
   }

   public void setRetryReplicationWait(long retryReplicationWait) {
      this.retryReplicationWait = retryReplicationWait;
   }
}
