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
package org.apache.activemq.artemis.core.server.cluster;

import java.util.Map;

import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ClusterTopologyListener;
import org.apache.activemq.artemis.core.client.impl.Topology;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.cluster.impl.BridgeMetrics;
import org.apache.activemq.artemis.core.server.cluster.impl.ClusterConnectionMetrics;

public interface ClusterConnection extends ActiveMQComponent, ClusterTopologyListener {

   SimpleString getName();

   String getNodeID();

   ActiveMQServer getServer();

   void nodeAnnounced(long eventUID,
                      String nodeID,
                      String backupGroupName,
                      String scaleDownGroupName,
                      Pair<TransportConfiguration, TransportConfiguration> connectorPair,
                      boolean backup);

   void addClusterTopologyListener(ClusterTopologyListener listener);

   void removeClusterTopologyListener(ClusterTopologyListener listener);

   /**
    * This is needed on replication, however we don't need it on shared storage.
    */
   void setSplitBrainDetection(boolean splitBrainDetection);

   boolean isSplitBrainDetection();

   /**
    * Only used for tests?
    *
    * @return a Map of node ID and addresses
    */
   Map<String, String> getNodes();

   TransportConfiguration getConnector();

   Topology getTopology();

   void flushExecutor();

   // for debug
   String describe();

   void informClusterOfBackup();

   boolean isNodeActive(String id);

   void removeRecord(String targetNodeID);

   void disconnectRecord(String targetNodeID);

   long getCallTimeout();

   long getProducerWindowSize();

   Bridge[] getBridges();

   /**
    * The metric for this cluster connection
    */
   ClusterConnectionMetrics getMetrics();

   /**
    * {@return the BridgeMetrics for the bridge to the given node if exists}
    */
   BridgeMetrics getBridgeMetrics(String nodeId);
}
