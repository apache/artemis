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

public class ReplicatedPolicyConfiguration implements HAPolicyConfiguration {

   private boolean checkForPrimaryServer = ActiveMQDefaultConfiguration.isDefaultCheckForActiveServer();

   private String groupName = null;

   private String clusterName = null;

   private int maxSavedReplicatedJournalsSize = ActiveMQDefaultConfiguration.getDefaultMaxSavedReplicatedJournalsSize();

   private long initialReplicationSyncTimeout = ActiveMQDefaultConfiguration.getDefaultInitialReplicationSyncTimeout();

   private boolean voteOnReplicationFailure = ActiveMQDefaultConfiguration.getDefaultVoteOnReplicationFailure();

   private int quorumSize = ActiveMQDefaultConfiguration.getDefaultQuorumSize();

   private int voteRetries = ActiveMQDefaultConfiguration.getDefaultVoteRetries();

   private long voteRetryWait = ActiveMQDefaultConfiguration.getDefaultVoteRetryWait();

   private int quorumVoteWait = ActiveMQDefaultConfiguration.getDefaultQuorumVoteWait();

   private Long retryReplicationWait = ActiveMQDefaultConfiguration.getDefaultRetryReplicationWait();

   public ReplicatedPolicyConfiguration() {
   }


   @Override
   public TYPE getType() {
      return TYPE.REPLICATION_PRIMARY_QUORUM_VOTING;
   }

   public boolean isCheckForActiveServer() {
      return checkForPrimaryServer;
   }

   public ReplicatedPolicyConfiguration setCheckForActiveServer(boolean checkForPrimaryServer) {
      this.checkForPrimaryServer = checkForPrimaryServer;
      return this;
   }

   public String getGroupName() {
      return groupName;
   }

   public ReplicatedPolicyConfiguration setGroupName(String groupName) {
      this.groupName = groupName;
      return this;
   }

   public String getClusterName() {
      return clusterName;
   }

   public ReplicatedPolicyConfiguration setClusterName(String clusterName) {
      this.clusterName = clusterName;
      return this;
   }

   public int getMaxSavedReplicatedJournalsSize() {
      return maxSavedReplicatedJournalsSize;
   }

   public ReplicatedPolicyConfiguration setMaxSavedReplicatedJournalsSize(int maxSavedReplicatedJournalsSize) {
      this.maxSavedReplicatedJournalsSize = maxSavedReplicatedJournalsSize;
      return this;
   }

   public long getInitialReplicationSyncTimeout() {
      return initialReplicationSyncTimeout;
   }

   public ReplicatedPolicyConfiguration setInitialReplicationSyncTimeout(long initialReplicationSyncTimeout) {
      this.initialReplicationSyncTimeout = initialReplicationSyncTimeout;
      return this;
   }

   public boolean getVoteOnReplicationFailure() {
      return voteOnReplicationFailure;
   }

   public ReplicatedPolicyConfiguration setVoteOnReplicationFailure(boolean voteOnReplicationFailure) {
      this.voteOnReplicationFailure = voteOnReplicationFailure;
      return this;
   }

   public int getQuorumSize() {
      return quorumSize;
   }

   public ReplicatedPolicyConfiguration setQuorumSize(int quorumSize) {
      this.quorumSize = quorumSize;
      return this;
   }


   public int getVoteRetries() {
      return voteRetries;
   }

   public ReplicatedPolicyConfiguration setVoteRetries(int voteRetries) {
      this.voteRetries = voteRetries;
      return this;
   }

   public ReplicatedPolicyConfiguration setVoteRetryWait(long voteRetryWait) {
      this.voteRetryWait = voteRetryWait;
      return this;
   }

   public long getVoteRetryWait() {
      return voteRetryWait;
   }

   public int getQuorumVoteWait() {
      return quorumVoteWait;
   }

   public ReplicatedPolicyConfiguration setQuorumVoteWait(int quorumVoteWait) {
      this.quorumVoteWait = quorumVoteWait;
      return this;
   }

   public void setRetryReplicationWait(Long retryReplicationWait) {
      this.retryReplicationWait = retryReplicationWait;
   }

   public Long getRetryReplicationWait() {
      return retryReplicationWait;
   }
}
