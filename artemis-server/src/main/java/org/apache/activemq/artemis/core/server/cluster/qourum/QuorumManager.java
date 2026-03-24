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
package org.apache.activemq.artemis.core.server.cluster.qourum;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ClusterTopologyListener;
import org.apache.activemq.artemis.api.core.client.TopologyMember;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryInternal;
import org.apache.activemq.artemis.core.client.impl.TopologyMemberImpl;
import org.apache.activemq.artemis.core.protocol.core.Channel;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.QuorumVoteMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.QuorumVoteReplyMessage;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.cluster.ClusterControl;
import org.apache.activemq.artemis.core.server.cluster.ClusterController;

import static org.apache.activemq.artemis.utils.Preconditions.checkNotNull;

/**
 * A QourumManager can be used to register a {@link org.apache.activemq.artemis.core.server.cluster.qourum.Quorum} to receive notifications
 * about changes to the cluster. A {@link org.apache.activemq.artemis.core.server.cluster.qourum.Quorum} can then issue a vote to the
 * remaining nodes in a cluster for a specific outcome
 */
public final class QuorumManager implements ClusterTopologyListener, ActiveMQComponent {

   private final ExecutorService executor;

   private final ClusterController clusterController;

   /**
    * all the current registered {@link org.apache.activemq.artemis.core.server.cluster.qourum.Quorum}'s
    */
   private final Map<String, Quorum> quorums = new HashMap<>();

   /**
    * any currently running runnables.
    */
   private final Map<QuorumVote, VoteRunnableHolder> voteRunnables = new HashMap<>();

   private final Map<SimpleString, QuorumVoteHandler> handlers = new HashMap<>();

   private boolean started = false;

   /**
    * this is the max size that the cluster has been.
    */
   private int maxClusterSize = 0;

   public QuorumManager(ExecutorService threadPool, ClusterController clusterController) {
      checkNotNull(threadPool);
      checkNotNull(clusterController);

      this.clusterController = clusterController;
      this.executor = threadPool;
   }

   /**
    * we start by simply creating the server locator and connecting in a separate thread
    *
    * @throws Exception
    */
   @Override
   public void start() throws Exception {
      if (started)
         return;
      started = true;
   }

   /**
    * stops the server locator
    *
    * @throws Exception
    */
   @Override
   public void stop() throws Exception {
      if (!started)
         return;
      synchronized (voteRunnables) {
         started = false;
         for (VoteRunnableHolder voteRunnableHolder : voteRunnables.values()) {
            for (VoteRunnable runnable : voteRunnableHolder.runnables) {
               runnable.close();
            }
         }
      }
      for (Quorum quorum : quorums.values()) {
         quorum.close();
      }
      quorums.clear();
   }

   /**
    * are we started
    *
    * @return
    */
   @Override
   public boolean isStarted() {
      return started;
   }

   /**
    * registers a {@link org.apache.activemq.artemis.core.server.cluster.qourum.Quorum} so that it can be notified of changes in the cluster.
    *
    * @param quorum
    */
   public void registerQuorum(Quorum quorum) {
      quorums.put(quorum.getName(), quorum);
      quorum.setQuorumManager(this);
   }

   /**
    * unregisters a {@link org.apache.activemq.artemis.core.server.cluster.qourum.Quorum}.
    *
    * @param quorum
    */
   public void unRegisterQuorum(Quorum quorum) {
      quorums.remove(quorum.getName());
   }

   /**
    * called by the {@link org.apache.activemq.artemis.core.client.impl.ServerLocatorInternal} when the topology changes. we update the
    * {@code maxClusterSize} if needed and inform the {@link org.apache.activemq.artemis.core.server.cluster.qourum.Quorum}'s.
    *
    * @param topologyMember the topolgy changed
    * @param last           if the whole cluster topology is being transmitted (after adding the listener to
    *                       the cluster connection) this parameter will be {@code true} for the last topology
    */
   @Override
   public void nodeUP(TopologyMember topologyMember, boolean last) {
      final int newClusterSize = clusterController.getDefaultClusterSize();
      maxClusterSize = newClusterSize > maxClusterSize ? newClusterSize : maxClusterSize;
      for (Quorum quorum : quorums.values()) {
         quorum.nodeUp(clusterController.getDefaultClusterTopology());
      }
   }

   /**
    * notify the {@link org.apache.activemq.artemis.core.server.cluster.qourum.Quorum} of a topology change.
    *
    * @param eventUID
    * @param nodeID   the id of the node leaving the cluster
    */
   @Override
   public void nodeDown(long eventUID, String nodeID) {
      for (Quorum quorum : quorums.values()) {
         quorum.nodeDown(clusterController.getDefaultClusterTopology(), eventUID, nodeID);
      }
   }

   public boolean hasLive(String nodeID, int quorumSize, int voteTimeout, TimeUnit voteTimeoutUnit) {
      Objects.requireNonNull(nodeID, "nodeID");
      if (!started) {
         throw new IllegalStateException("QuorumManager must start first");
      }
      int size = quorumSize == -1 ? maxClusterSize : quorumSize;
      QuorumVoteServerConnect quorumVote = new QuorumVoteServerConnect(size, nodeID);
      // A positive decision means that there is no live with nodeID
      boolean noLive = awaitVoteComplete(quorumVote, voteTimeout, voteTimeoutUnit);
      return !noLive;
   }

   public boolean isStillLive(String nodeID,
                              TransportConfiguration liveConnector,
                              int quorumSize,
                              int voteTimeout,
                              TimeUnit voteTimeoutUnit) {
      Objects.requireNonNull(nodeID, "nodeID");
      Objects.requireNonNull(liveConnector, "liveConnector");
      if (!started) {
         throw new IllegalStateException("QuorumManager must start first");
      }
      int size = quorumSize == -1 ? maxClusterSize : quorumSize;
      QuorumVoteServerConnect quorumVote = new QuorumVoteServerConnect(size, nodeID, true, liveConnector.toString());
      return awaitVoteComplete(quorumVote, voteTimeout, voteTimeoutUnit);
   }

   private boolean awaitVoteComplete(QuorumVoteServerConnect quorumVote, int voteTimeout, TimeUnit voteTimeoutUnit) {
      final int maxClusterSize = this.maxClusterSize;
      vote(quorumVote);
      if (maxClusterSize > 1) {
         try {
            quorumVote.await(voteTimeout, voteTimeoutUnit);
         } catch (InterruptedException interruption) {
            // No-op. The best the quorum can do now is to return the latest number it has
            ActiveMQServerLogger.LOGGER.quorumVoteAwaitInterrupted();
         }
      } else {
         ActiveMQServerLogger.LOGGER.ignoringQuorumVote(maxClusterSize);
      }
      voteComplete(quorumVote);

      return quorumVote.getDecision();
   }

   /**
    * returns the maximum size this cluster has been.
    *
    * @return max size
    */
   public int getMaxClusterSize() {
      return maxClusterSize;
   }

   /**
    * ask the quorum to vote within a specific quorum.
    *
    * @param quorumVote the vote to acquire
    */
   public void vote(final QuorumVote quorumVote) {
      List<VoteRunnable> runnables = new ArrayList<>();
      synchronized (voteRunnables) {
         if (!started)
            return;
         //send a vote to each node
         ActiveMQServerLogger.LOGGER.initiatingQuorumVote(quorumVote.getName());
         for (TopologyMemberImpl tm : clusterController.getDefaultClusterTopology().getMembers()) {
            //but not ourselves
            if (!tm.getNodeId().equals(clusterController.getNodeID().toString())) {
               Pair<TransportConfiguration, TransportConfiguration> pair = tm.getConnector();

               final TransportConfiguration serverTC = pair.getA();

               VoteRunnable voteRunnable = new VoteRunnable(serverTC, quorumVote);

               runnables.add(voteRunnable);
            }
         }
         final int votes = runnables.size();
         ActiveMQServerLogger.LOGGER.requestedQuorumVotes(votes);
         if (votes > 0) {
            voteRunnables.put(quorumVote, new VoteRunnableHolder(quorumVote, runnables, votes));

            for (VoteRunnable runnable : runnables) {
               executor.submit(runnable);
            }
         } else {
            quorumVote.allVotesCast(clusterController.getDefaultClusterTopology());
         }
      }
   }

   /**
    * handle a vote received on the quorum
    *
    * @param handler the name of the handler to use for the vote
    * @param vote    the vote
    * @return the updated vote
    */
   private Vote vote(SimpleString handler, Vote vote) {
      QuorumVoteHandler quorumVoteHandler = handlers.get(handler);
      return quorumVoteHandler.vote(vote);
   }

   /**
    * must be called by the quorum when it is happy on an outcome. only one vote can take place at anyone time for a
    * specific quorum
    *
    * @param quorumVote the vote
    */
   private void voteComplete(QuorumVoteServerConnect quorumVote) {
      VoteRunnableHolder holder = voteRunnables.remove(quorumVote);
      if (holder != null) {
         for (VoteRunnable runnable : holder.runnables) {
            runnable.close();
         }
      }
   }

   /**
    * called to register vote handlers on the quorum
    *
    * @param quorumVoteHandler the vote handler
    */
   public void registerQuorumHandler(QuorumVoteHandler quorumVoteHandler) {
      handlers.put(quorumVoteHandler.getQuorumName(), quorumVoteHandler);
   }

   @Override
   public String toString() {
      return QuorumManager.class.getSimpleName() + "(server=" + clusterController.getIdentity() + ")";
   }

   private QuorumVoteHandler getVoteHandler(SimpleString handler) {
      return handlers.get(handler);
   }

   public void handleQuorumVote(Channel clusterChannel, Packet packet) {
      QuorumVoteMessage quorumVoteMessage = (QuorumVoteMessage) packet;
      QuorumVoteHandler voteHandler = getVoteHandler(quorumVoteMessage.getHandler());
      if (voteHandler == null) {
         ActiveMQServerLogger.LOGGER.noVoteHandlerConfigured();
         return;
      }
      quorumVoteMessage.decode(voteHandler);
      ActiveMQServerLogger.LOGGER.receivedQuorumVoteRequest(quorumVoteMessage.getVote().toString());
      Vote vote = vote(quorumVoteMessage.getHandler(), quorumVoteMessage.getVote());
      ActiveMQServerLogger.LOGGER.sendingQuorumVoteResponse(vote.toString());
      clusterChannel.send(new QuorumVoteReplyMessage(quorumVoteMessage.getHandler(), vote));
   }

   private final class VoteRunnableHolder {

      private final QuorumVote quorumVote;
      private final List<VoteRunnable> runnables;
      private int size;

      private VoteRunnableHolder(QuorumVote quorumVote, List<VoteRunnable> runnables, int size) {
         this.quorumVote = quorumVote;

         this.runnables = runnables;
         this.size = size;
      }

      public synchronized void voteComplete() {
         size--;
         if (size <= 0) {
            quorumVote.allVotesCast(clusterController.getDefaultClusterTopology());
         }
      }
   }

   private Vote sendQuorumVote(ClusterControl clusterControl, SimpleString handler, Vote vote) {
      try {
         final ClientSessionFactoryInternal sessionFactory = clusterControl.getSessionFactory();
         final String remoteAddress = sessionFactory.getConnection().getRemoteAddress();
         ActiveMQServerLogger.LOGGER.sendingQuorumVoteRequest(remoteAddress, vote.toString());
         QuorumVoteReplyMessage replyMessage = (QuorumVoteReplyMessage) clusterControl.getClusterChannel().get()
            .sendBlocking(new QuorumVoteMessage(handler, vote), PacketImpl.QUORUM_VOTE_REPLY);
         QuorumVoteHandler voteHandler = getVoteHandler(replyMessage.getHandler());
         replyMessage.decodeRest(voteHandler);
         Vote voteResponse = replyMessage.getVote();
         ActiveMQServerLogger.LOGGER.receivedQuorumVoteResponse(remoteAddress, voteResponse.toString());
         return voteResponse;
      } catch (ActiveMQException e) {
         return null;
      }
   }

   /**
    * this will connect to a node and then cast a vote. whether or not this vote is asked of the target node is dependent
    * on {@link org.apache.activemq.artemis.core.server.cluster.qourum.Vote#isRequestServerVote()}
    */
   private final class VoteRunnable implements Runnable {

      private final TransportConfiguration serverTC;
      private final QuorumVote quorumVote;
      private ClusterControl clusterControl;

      private VoteRunnable(TransportConfiguration serverTC, QuorumVote quorumVote) {
         this.serverTC = serverTC;
         this.quorumVote = quorumVote;
      }

      @Override
      public void run() {
         try {
            Vote vote;
            if (!started)
               return;
            //try to connect to the node i want to send a vote to
            clusterControl = clusterController.connectToNode(serverTC);
            clusterControl.authorize();
            //if we are successful get the vote and check whether we need to send it to the target server,
            //just connecting may be enough

            vote = quorumVote.connected();
            if (vote.isRequestServerVote()) {
               vote = sendQuorumVote(clusterControl, quorumVote.getName(), vote);
               quorumVote.vote(vote);
            } else {
               quorumVote.vote(vote);
            }
         } catch (Exception e) {
            Vote vote = quorumVote.notConnected();
            quorumVote.vote(vote);
         } finally {
            try {
               if (clusterControl != null) {
                  clusterControl.close();
               }
            } catch (Exception e) {
               //ignore
            }
            QuorumManager.this.votingComplete(quorumVote);
         }
      }

      public void close() {
         if (clusterControl != null) {
            clusterControl.close();
         }
      }
   }

   private void votingComplete(QuorumVote quorumVote) {
      VoteRunnableHolder voteRunnableHolder = voteRunnables.get(quorumVote);
      if (voteRunnableHolder != null) {
         voteRunnableHolder.voteComplete();
      }
   }
}
