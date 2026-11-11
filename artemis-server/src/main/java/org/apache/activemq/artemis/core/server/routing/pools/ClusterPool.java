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
package org.apache.activemq.artemis.core.server.routing.pools;

import org.apache.activemq.artemis.api.core.client.ClusterTopologyListener;
import org.apache.activemq.artemis.api.core.client.TopologyMember;
import org.apache.activemq.artemis.core.server.routing.targets.TargetFactory;
import org.apache.activemq.artemis.core.server.cluster.ClusterConnection;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

public class ClusterPool extends AbstractPool implements ClusterTopologyListener {
   private final ClusterConnection clusterConnection;

   private final Map<String, TopologyMember> clusterMembers = new ConcurrentHashMap<>();

   public ClusterPool(TargetFactory targetFactory, ScheduledExecutorService scheduledExecutor,
                      int checkPeriod, ClusterConnection clusterConnection) {
      super(targetFactory, scheduledExecutor, checkPeriod);

      this.clusterConnection = clusterConnection;
   }

   @Override
   public void start() throws Exception {
      super.start();

      clusterConnection.addClusterTopologyListener(this);
   }

   @Override
   public void stop() throws Exception {
      clusterConnection.removeClusterTopologyListener(this);

      super.stop();
   }

   @Override
   public void nodeUP(TopologyMember member, boolean last) {
      if (!clusterConnection.getNodeID().equals(member.getNodeId()) &&
         clusterMembers.putIfAbsent(member.getNodeId(), member) == null) {
         addTarget(member.getPrimary(), member.getNodeId());
      }
   }

   @Override
   public void nodeDown(long eventUID, String nodeID) {
      if (clusterMembers.remove(nodeID) != null) {
         removeTarget(getTarget(nodeID));
      }
   }
}
