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
package org.apache.activemq.artemis.api.core.management;

import java.util.Map;

/**
 * A ClusterConnectionControl is used to manage a cluster connection.
 */
public interface ClusterConnectionControl extends ActiveMQComponentControl {

   /**
    * {@return the configuration name of this cluster connection}
    */
   @Attribute(desc = "name of this cluster connection")
   String getName();

   /**
    * {@return the address used by this cluster connection}
    */
   @Attribute(desc = "address used by this cluster connection")
   String getAddress();

   /**
    * {@return the node ID used by this cluster connection}
    */
   @Attribute(desc = "node ID used by this cluster connection")
   String getNodeID();

   /**
    * {@return whether this cluster connection use duplicate detection}
    */
   @Attribute(desc = "whether this cluster connection use duplicate detection")
   boolean isDuplicateDetection();

   /**
    * {@return the type of message load balancing strategy this bridge will use}
    */
   @Attribute(desc = "type of message load balancing strategy this bridge will use")
   String getMessageLoadBalancingType();

   /**
    * {@return the Topology that this Cluster Connection knows about}
    */
   @Attribute(desc = "Topology that this Cluster Connection knows about")
   String getTopology();

   /**
    * {@return the maximum number of hops used by this cluster connection}
    */
   @Attribute(desc = "maximum number of hops used by this cluster connection")
   int getMaxHops();

   /**
    * {@return the list of static connectors}
    */
   @Attribute(desc = "list of static connectors")
   Object[] getStaticConnectors();

   /**
    * {@return the list of static connectors as JSON}
    */
   @Attribute(desc = "list of static connectors as JSON")
   String getStaticConnectorsAsJSON() throws Exception;

   /**
    * {@return the name of the discovery group used by this cluster connection}
    */
   @Attribute(desc = "name of the discovery group used by this cluster connection")
   String getDiscoveryGroupName();

   /**
    * {@return the connection retry interval used by this cluster connection}
    */
   @Attribute(desc = "connection retry interval used by this cluster connection")
   long getRetryInterval();

   /**
    * {@return a map of the nodes connected to this cluster connection; keys are node IDs, values are the addresses used
    * to connect to the nodes}
    */
   @Attribute(desc = "map of the nodes connected to this cluster connection (keys are node IDs, values are the addresses used to connect to the nodes)")
   Map<String, String> getNodes() throws Exception;

   /**
    * The messagesPendingAcknowledgement counter is incremented when any bridge in the cluster connection has forwarded
    * a message and is waiting acknowledgement from the other broker. (aggregate over all bridges)
    * <p>
    * This is a cumulative total and the number of outstanding pending messages for the cluster connection can be
    * computed by subtracting messagesAcknowledged from messagesPendingAcknowledgement.
    */
   @Attribute(desc = "The messagesPendingAcknowledgement counter is incremented when any bridge in the cluster connection has forwarded a message and is waiting acknowledgement from the other broker. (aggregate over all bridges)")
   long getMessagesPendingAcknowledgement();

   /**
    * The messagesAcknowledged counter is the number of messages actually received by a remote broker for all bridges in
    * this cluster connection
    * <p>
    * This is a cumulative total and the number of outstanding pending messages for the cluster connection can be
    * computed by subtracting messagesAcknowledged from messagesPendingAcknowledgement.
    */
   @Attribute(desc = "The messagesAcknowledged counter is the number of messages actually received by a remote broker for all bridges in this cluster connection")
   long getMessagesAcknowledged();

   /**
    * The current metrics for this cluster connection (aggregate over all bridges to other nodes)
    * <p>
    * The messagesPendingAcknowledgement counter is incremented when any bridge in the cluster connection has forwarded
    * a message and is waiting acknowledgement from the other broker.
    * <p>
    * The messagesAcknowledged counter is the number of messages actually received by a remote broker for all bridges in
    * this cluster connection
    */
   @Attribute(desc = "The metrics for this cluster connection. The messagesPendingAcknowledgement counter is incremented when any bridge in the cluster connection has forwarded a message and is waiting acknowledgement from the other broker. The messagesAcknowledged counter is the number of messages actually received by a remote broker for all bridges in this cluster connection")
   Map<String, Object> getMetrics();

   /**
    * The bridge metrics for the given node in the cluster connection
    * <p>
    * The messagesPendingAcknowledgement counter is incremented when the bridge is has forwarded a message but is
    * waiting acknowledgement from the other broker. The messagesAcknowledged counter is the number of messages actually
    * received by the remote broker for this bridge.
    */
   @Operation(desc = "The metrics for the bridge by nodeId. The messagesPendingAcknowledgement counter is incremented when the bridge is has forwarded a message but is waiting acknowledgement from the other broker. The messagesAcknowledged counter is the number of messages actually received by the remote broker for this bridge.")
   Map<String, Object> getBridgeMetrics(@Parameter(name = "nodeId", desc = "The target node ID") String nodeId) throws Exception;

   @Attribute(desc = "The Producer Window Size used by the Cluster Connection")
   long getProducerWindowSize();

}
