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
package org.apache.activemq.artemis.core.server.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.client.impl.Topology;
import org.apache.activemq.artemis.core.client.impl.TopologyMemberImpl;
import org.apache.activemq.artemis.core.protocol.core.Channel;
import org.apache.activemq.artemis.core.protocol.core.ChannelHandler;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.BackupRequestMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.BackupResponseMessage;
import org.apache.activemq.artemis.core.remoting.server.RemotingService;
import org.apache.activemq.artemis.core.replication.ReplicationManager;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.cluster.ha.ColocatedHAManager;
import org.apache.activemq.artemis.core.server.cluster.ha.ColocatedPolicy;
import org.apache.activemq.artemis.core.server.cluster.ha.HAManager;
import org.apache.activemq.artemis.core.server.cluster.quorum.QuorumVote;
import org.apache.activemq.artemis.core.server.cluster.quorum.QuorumVoteHandler;
import org.apache.activemq.artemis.core.server.cluster.quorum.Vote;
import org.apache.activemq.artemis.spi.core.remoting.Acceptor;

public class ColocatedActivation extends PrimaryActivation {

   private static final SimpleString REQUEST_BACKUP_QUORUM_VOTE = SimpleString.of("RequestBackupQuorumVote");

   private final ColocatedHAManager colocatedHAManager;

   private final ColocatedPolicy colocatedPolicy;

   PrimaryActivation primaryActivation;

   private final ActiveMQServerImpl server;

   public ColocatedActivation(ActiveMQServerImpl activeMQServer,
                              ColocatedPolicy colocatedPolicy,
                              PrimaryActivation primaryActivation) {
      server = activeMQServer;
      this.colocatedPolicy = colocatedPolicy;
      this.primaryActivation = primaryActivation;
      colocatedHAManager = new ColocatedHAManager(colocatedPolicy, server);
   }

   @Override
   public void haStarted() {
      server.getClusterManager().getQuorumManager().registerQuorumHandler(new RequestBackupQuorumVoteHandler());
      //vote for a backup if required
      if (colocatedPolicy.isRequestBackup()) {
         server.getClusterManager().getQuorumManager().vote(new RequestBackupQuorumVote());
      }
   }

   @Override
   public void freezeConnections(RemotingService remotingService) {
      primaryActivation.freezeConnections(remotingService);
   }

   @Override
   public void postConnectionFreeze() {
      primaryActivation.postConnectionFreeze();
   }

   @Override
   public void preStorageClose() throws Exception {
      primaryActivation.preStorageClose();
   }

   @Override
   public void sendPrimaryIsStopping() {
      primaryActivation.sendPrimaryIsStopping();
   }

   @Override
   public ReplicationManager getReplicationManager() {
      return primaryActivation.getReplicationManager();
   }

   @Override
   public HAManager getHAManager() {
      return colocatedHAManager;
   }

   @Override
   public void run() {
      primaryActivation.run();
   }

   @Override
   public void close(boolean permanently, boolean restarting) throws Exception {
      primaryActivation.close(permanently, restarting);
   }

   @Override
   public ChannelHandler getActivationChannelHandler(final Channel channel, final Acceptor acceptorUsed) {
      final ChannelHandler activationChannelHandler = primaryActivation.getActivationChannelHandler(channel, acceptorUsed);
      return packet -> {
         if (packet.getType() == PacketImpl.BACKUP_REQUEST) {
            BackupRequestMessage backupRequestMessage = (BackupRequestMessage) packet;
            boolean started = false;
            try {
               started = colocatedHAManager.activateBackup(backupRequestMessage.getBackupSize(), backupRequestMessage.getJournalDirectory(), backupRequestMessage.getBindingsDirectory(), backupRequestMessage.getLargeMessagesDirectory(), backupRequestMessage.getPagingDirectory(), backupRequestMessage.getNodeID());
            } catch (Exception e) {
               ActiveMQServerLogger.LOGGER.failedToActivateBackup(e);
            }
            channel.send(new BackupResponseMessage(started));
         } else if (activationChannelHandler != null) {
            activationChannelHandler.handlePacket(packet);
         }
      };
   }

   /**
    * A vote handler for incoming backup request votes
    */
   private final class RequestBackupQuorumVoteHandler implements QuorumVoteHandler {

      @Override
      public Vote vote(Vote vote) {
         int size = colocatedHAManager.getBackupServers().size();
         return new RequestBackupVote(size, server.getNodeID().toString(), size < colocatedPolicy.getMaxBackups());
      }

      @Override
      public SimpleString getQuorumName() {
         return REQUEST_BACKUP_QUORUM_VOTE;
      }

      @Override
      public Vote decode(ActiveMQBuffer voteBuffer) {
         RequestBackupVote requestBackupVote = new RequestBackupVote();
         requestBackupVote.decode(voteBuffer);
         return requestBackupVote;
      }
   }

   /**
    * a quorum vote for backup requests
    */
   private final class RequestBackupQuorumVote extends QuorumVote<RequestBackupVote, Pair<String, Integer>> {

      //the available nodes that we can request
      private final List<Pair<String, Integer>> nodes = new ArrayList<>();

      private RequestBackupQuorumVote() {
         // there's no old name, we never renamed the ColocatedQuorumVote String
         super(REQUEST_BACKUP_QUORUM_VOTE, null);
      }

      @Override
      public Vote connected() {
         return new RequestBackupVote();
      }

      @Override
      public Vote notConnected() {
         return new RequestBackupVote();
      }

      @Override
      public void vote(RequestBackupVote vote) {
         //if the returned vote is available add it to the nodes we can request
         if (vote.backupAvailable) {
            nodes.add(vote.getVote());
         }
      }

      @Override
      public Pair<String, Integer> getDecision() {
         //sort the nodes by how many backups they have and choose the first
         Collections.sort(nodes, Comparator.comparing(Pair::getB));
         return nodes.get(0);
      }

      @Override
      public void allVotesCast(Topology voteTopology) {
         //if we have any nodes that we can request then send a request
         if (!nodes.isEmpty()) {
            Pair<String, Integer> decision = getDecision();
            TopologyMemberImpl member = voteTopology.getMember(decision.getA());
            try {
               boolean backupStarted = colocatedHAManager.requestBackup(member.getConnector(), decision.getB().intValue(), !colocatedPolicy.isSharedStore());
               if (!backupStarted) {
                  nodes.clear();
                  server.getScheduledPool().schedule(() -> server.getClusterManager().getQuorumManager().vote(new RequestBackupQuorumVote()), colocatedPolicy.getBackupRequestRetryInterval(), TimeUnit.MILLISECONDS);
               }
            } catch (Exception e) {
               ActiveMQServerLogger.LOGGER.failedToSendRequestToNode(e);
            }
         } else {
            nodes.clear();
            server.getScheduledPool().schedule(() -> server.getClusterManager().getQuorumManager().vote(RequestBackupQuorumVote.this), colocatedPolicy.getBackupRequestRetryInterval(), TimeUnit.MILLISECONDS);
         }
      }

      @Override
      public SimpleString getName() {
         return REQUEST_BACKUP_QUORUM_VOTE;
      }
   }

   class RequestBackupVote extends Vote<Pair<String, Integer>> {

      private int backupsSize;
      private String nodeID;
      private boolean backupAvailable;

      RequestBackupVote() {
         backupsSize = -1;
      }

      RequestBackupVote(int backupsSize, String nodeID, boolean backupAvailable) {
         this.backupsSize = backupsSize;
         this.nodeID = nodeID;
         this.backupAvailable = backupAvailable;
      }

      @Override
      public void encode(ActiveMQBuffer buff) {
         buff.writeInt(backupsSize);
         buff.writeNullableString(nodeID);
         buff.writeBoolean(backupAvailable);
      }

      @Override
      public void decode(ActiveMQBuffer buff) {
         backupsSize = buff.readInt();
         nodeID = buff.readNullableString();
         backupAvailable = buff.readBoolean();
      }

      @Override
      public boolean isRequestServerVote() {
         return true;
      }

      @Override
      public Pair<String, Integer> getVote() {
         return new Pair<>(nodeID, backupsSize);
      }

      @Override
      public String toString() {
         return "RequestBackupVote [backupsSize=" + backupsSize + ", nodeID=" + nodeID + ", backupAvailable=" + backupAvailable + "]";
      }
   }
}
