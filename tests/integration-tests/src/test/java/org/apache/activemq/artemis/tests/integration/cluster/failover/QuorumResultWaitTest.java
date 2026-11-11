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
package org.apache.activemq.artemis.tests.integration.cluster.failover;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicaPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicatedPolicyConfiguration;
import org.apache.activemq.artemis.core.server.cluster.ha.ReplicatedPolicy;
import org.junit.jupiter.api.Test;

public class QuorumResultWaitTest extends StaticClusterWithBackupFailoverTest {

   public static final int QUORUM_VOTE_WAIT_CONFIGURED_TIME_SEC = 12;
   @Override
   protected void setupServers() throws Exception {
      super.setupServers();
      //we need to know who is connected to who
      ((ReplicatedPolicyConfiguration) servers[0].getConfiguration().getHAPolicyConfiguration()).setGroupName("group0");
      ((ReplicatedPolicyConfiguration) servers[1].getConfiguration().getHAPolicyConfiguration()).setGroupName("group1");
      ((ReplicatedPolicyConfiguration) servers[2].getConfiguration().getHAPolicyConfiguration()).setGroupName("group2");
      ((ReplicaPolicyConfiguration) servers[4].getConfiguration().getHAPolicyConfiguration()).setGroupName("group1");
      ((ReplicaPolicyConfiguration) servers[5].getConfiguration().getHAPolicyConfiguration()).setGroupName("group2");
      ReplicatedPolicyConfiguration replicatedPolicyConf = new ReplicatedPolicyConfiguration().setQuorumVoteWait(QUORUM_VOTE_WAIT_CONFIGURED_TIME_SEC);
      replicatedPolicyConf.setGroupName("group0");
      replicatedPolicyConf.setVoteRetries(5);
      replicatedPolicyConf.setVoteRetryWait(100);
      servers[3].getConfiguration().setHAPolicyConfiguration(replicatedPolicyConf);
   }

   @Test
   public void testQuorumVotingResultWait() throws Exception {
      setupCluster();
      try {
         startServers(0, 1, 2);
         startServers(3, 4, 5);
         //Assert if the default time 30 sec is used
         assertEquals(ActiveMQDefaultConfiguration.getDefaultQuorumVoteWait(), ((ReplicatedPolicy) (servers[0].getHAPolicy())).getQuorumVoteWait());
         //Assert if the configured time is used.
         assertEquals(QUORUM_VOTE_WAIT_CONFIGURED_TIME_SEC, ((ReplicatedPolicy) (servers[3].getHAPolicy())).getQuorumVoteWait());
      } finally {
         stopServers(0, 1, 2, 3, 4, 5);
      }
   }

   @Override
   protected boolean isSharedStorage() {
      return false;
   }
}
