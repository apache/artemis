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
package org.apache.activemq.artemis.core.server.cluster.quorum;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.client.impl.Topology;

/**
 * the vote itself. the vote can be decided by the enquirer or sent out to each node in the quorum.
 */
public abstract class QuorumVote<V extends Vote, T> {

   private final SimpleString name;

   private final SimpleString oldName;

   public QuorumVote(SimpleString name, SimpleString oldName) {
      this.name = name;
      this.oldName = oldName;
   }

   /**
    * called by the {@link org.apache.activemq.artemis.core.server.cluster.quorum.QuorumManager} when one of the nodes
    * in the quorum is successfully connected to. The QuorumVote can then decide whether a decision can be made with
    * just that information.
    *
    * @return the vote to use
    */
   public abstract Vote connected();

   /**
    * called by the {@link org.apache.activemq.artemis.core.server.cluster.quorum.QuorumManager} fails to connect to a
    * node in the quorum. The QuorumVote can then decide whether a decision can be made with just that information
    * however the node cannot cannot be asked.
    *
    * @return the vote to use
    */
   public abstract Vote notConnected();

   /**
    * called by the {@link org.apache.activemq.artemis.core.server.cluster.quorum.QuorumManager} when a vote can be
    * made, either from the cluster or decided by itself.
    *
    * @param vote the vote to make.
    */
   public abstract void vote(V vote);

   /**
    * get the decion of the vote
    *
    * @return the voting decision
    */
   public abstract T getDecision();

   /**
    * called by the {@link org.apache.activemq.artemis.core.server.cluster.quorum.QuorumManager} when all the votes have
    * been cast and received.
    *
    * @param voteTopology the topology of where the votes were sent.
    */
   public abstract void allVotesCast(Topology voteTopology);

   /**
    * the name of this quorum vote, used for identifying the correct
    * {@link org.apache.activemq.artemis.core.server.cluster.quorum.QuorumVoteHandler}
    *
    * @return the name of the wuorum vote
    */
   public SimpleString getName() {
      return name;
   }

   public SimpleString getOldName() {
      return oldName;
   }
}
