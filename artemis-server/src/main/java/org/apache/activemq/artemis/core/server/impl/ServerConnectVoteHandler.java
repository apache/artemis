/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.server.impl;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.client.impl.TopologyMemberImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.cluster.quorum.QuorumVoteHandler;
import org.apache.activemq.artemis.core.server.cluster.quorum.QuorumVoteServerConnect;
import org.apache.activemq.artemis.core.server.cluster.quorum.ServerConnectVote;
import org.apache.activemq.artemis.core.server.cluster.quorum.Vote;

public class ServerConnectVoteHandler implements QuorumVoteHandler {
   private final ActiveMQServerImpl server;

   public ServerConnectVoteHandler(ActiveMQServerImpl server) {
      this.server = server;
   }

   @Override
   public Vote vote(Vote vote) {
      ServerConnectVote serverConnectVote = (ServerConnectVote) vote;
      String nodeid = serverConnectVote.getNodeId();
      try {
         TopologyMemberImpl member = server.getClusterManager().getDefaultConnection(null).getTopology().getMember(nodeid);

         if (member != null && member.getPrimary() != null) {
            ActiveMQServerLogger.LOGGER.nodeFoundInClusterTopology(nodeid);
            return new ServerConnectVote(nodeid, (Boolean) vote.getVote(), member.getPrimary().toString());
         }
         ActiveMQServerLogger.LOGGER.nodeNotFoundInClusterTopology(nodeid);
      } catch (Exception e) {
         e.printStackTrace();
      }
      return new ServerConnectVote(nodeid, !((Boolean) vote.getVote()), null);
   }

   @Override
   public SimpleString getQuorumName() {
      return QuorumVoteServerConnect.PRIMARY_FAILOVER_VOTE;
   }

   @Override
   public Vote decode(ActiveMQBuffer voteBuffer) {
      ServerConnectVote vote = new ServerConnectVote();
      vote.decode(voteBuffer);
      return vote;
   }
}
