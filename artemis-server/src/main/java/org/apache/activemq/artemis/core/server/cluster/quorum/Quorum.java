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

import org.apache.activemq.artemis.core.client.impl.Topology;

/**
 * A quorum can be registered with the {@link QuorumManager} to receive notifications about the state of a cluster. It
 * can then use the {@link QuorumManager} for the quorum within a cluster to vote on a specific outcome.
 */
public interface Quorum {

   /**
    * the name of the Quorum. this should be unique and is used to locate the correct quorum to use for voting
    */
   String getName();

   /**
    * called by the quorum manager when a quorum is registered
    */
   void setQuorumManager(QuorumManager quorumManager);

   /**
    * called by the quorum when a node in the quorum goes down
    */
   void nodeDown(Topology topology, long eventUID, String nodeID);

   /**
    * called by the quorum when a node in the quorum goes up
    */
   void nodeUp(Topology topology);

   /**
    * called if the quorum manager is stopping so we can clean up
    */
   void close();
}
