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
package org.apache.activemq.artemis.core.client.impl;

import java.util.concurrent.Executor;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.cluster.DiscoveryListener;
import org.apache.activemq.artemis.spi.core.remoting.ClientProtocolManager;

public interface ServerLocatorInternal extends ServerLocator {

   void start(Executor executor) throws Exception;

   void factoryClosed(ClientSessionFactory factory);

   AfterConnectInternalListener getAfterConnectInternalListener();

   ServerLocatorInternal setAfterConnectionInternalListener(AfterConnectInternalListener listener);

   ServerLocatorInternal setDiscoveryListener(DiscoveryListener listener);

   /**
    * Used to better identify Cluster Connection Locators on logs. To facilitate eventual debugging.
    * <p>
    * This method used to be on tests interface, but I'm now making it part of the public interface since
    */
   ServerLocatorInternal setIdentity(String identity);

   ServerLocatorInternal setNodeID(String nodeID);

   String getNodeID();

   void cleanup();

   // Reset this Locator back as if it never received any topology
   void resetToInitialConnectors();

   ClientSessionFactoryInternal connect() throws ActiveMQException;

   /**
    * Like {@link #connect()} but it does not log warnings if it fails to connect.
    */
   ClientSessionFactoryInternal connectNoWarnings() throws ActiveMQException;

   void notifyNodeUp(long uniqueEventID,
                     String nodeID,
                     String backupGroupName,
                     String scaleDownGroupName,
                     Pair<TransportConfiguration, TransportConfiguration> connectorPair,
                     boolean last);

   /**
    * Notify this about a node down event.
    *
    * @param uniqueEventID 0 means get the previous ID +1
    */
   default void notifyNodeDown(long uniqueEventID, String nodeID) {
      notifyNodeDown(uniqueEventID, nodeID, false);
   }

   void notifyNodeDown(long uniqueEventID, String nodeID, boolean disconnect);

   ServerLocatorInternal setClusterConnection(boolean clusterConnection);

   boolean isClusterConnection();

   TransportConfiguration getClusterTransportConfiguration();

   ServerLocatorInternal setClusterTransportConfiguration(TransportConfiguration tc);

   @Override
   Topology getTopology();

   ClientProtocolManager newProtocolManager();

   boolean isConnectable();

   int getConnectorsSize();

   Pair<TransportConfiguration, TransportConfiguration> selectNextConnectorPair();

   long getNextRetryInterval(long retryInterval, double retryIntervalMultiplier, long maxRetryInterval);
}
