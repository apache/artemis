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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryInternal;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorInternal;
import org.apache.activemq.artemis.core.postoffice.DuplicateIDCache;
import org.apache.activemq.artemis.core.postoffice.impl.PostOfficeImpl;
import org.apache.activemq.artemis.core.remoting.server.RemotingService;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.NodeLocator;
import org.apache.activemq.artemis.core.server.NodeManager;
import org.apache.activemq.artemis.core.server.cluster.ActiveMQServerSideProtocolManagerFactory;
import org.apache.activemq.artemis.core.server.cluster.ha.PrimaryOnlyPolicy;
import org.apache.activemq.artemis.core.server.cluster.ha.ScaleDownPolicy;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class PrimaryOnlyActivation extends Activation {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   //this is how we act when we initially start as primary
   private PrimaryOnlyPolicy primaryOnlyPolicy;

   private final ActiveMQServerImpl activeMQServer;

   private ServerLocatorInternal scaleDownServerLocator;

   private ClientSessionFactoryInternal scaleDownClientSessionFactory;

   public PrimaryOnlyActivation(ActiveMQServerImpl server, PrimaryOnlyPolicy primaryOnlyPolicy) {
      this.activeMQServer = server;
      this.primaryOnlyPolicy = primaryOnlyPolicy;
   }

   public PrimaryOnlyPolicy getPrimaryOnlyPolicy() {
      return primaryOnlyPolicy;
   }

   @Override
   public void run() {
      try {
         if (activeMQServer.getConfiguration().getConfigurationFileRefreshPeriod() > 0) {
            // we may have stale config after waiting for a lock for a while
            if (activeMQServer.getUptimeMillis() > activeMQServer.getConfiguration().getConfigurationFileRefreshPeriod()) {
               activeMQServer.reloadConfigurationFile();
            }
         }
         activeMQServer.initialisePart1(false);

         activeMQServer.registerActivateCallback(activeMQServer.getNodeManager().startPrimaryNode());

         if (activeMQServer.getState() == ActiveMQServerImpl.SERVER_STATE.STOPPED || activeMQServer.getState() == ActiveMQServerImpl.SERVER_STATE.STOPPING) {
            return;
         }

         activeMQServer.initialisePart2(false);

         activeMQServer.completeActivation(false);

         if (activeMQServer.getIdentity() != null) {
            ActiveMQServerLogger.LOGGER.serverIsActive(activeMQServer.getIdentity());
         } else {
            ActiveMQServerLogger.LOGGER.serverIsActive();
         }
      } catch (Exception e) {
         ActiveMQServerLogger.LOGGER.initializationError(e);
         activeMQServer.callActivationFailureListeners(e);
      }
   }

   @Override
   public void close(boolean permanently, boolean restarting) throws Exception {
      if (scaleDownServerLocator != null) {
         scaleDownServerLocator.close();
         scaleDownServerLocator = null;
      }

      NodeManager nodeManagerInUse = activeMQServer.getNodeManager();

      if (nodeManagerInUse != null) {
         if (permanently) {
            nodeManagerInUse.crashPrimaryServer();
         } else {
            nodeManagerInUse.pausePrimaryServer();
         }
      }
   }

   @Override
   public void freezeConnections(RemotingService remotingService) {
      // connect to the scale-down target first so that when we freeze/disconnect the clients we can tell them where
      // we're sending the messages
      if (primaryOnlyPolicy.getScaleDownPolicy() != null && primaryOnlyPolicy.getScaleDownPolicy().isEnabled()) {
         connectToScaleDownTarget(primaryOnlyPolicy.getScaleDownPolicy());
      }

      RemotingConnection rc = scaleDownClientSessionFactory == null ? null : scaleDownClientSessionFactory.getConnection();
      String nodeID = rc == null ? null : scaleDownClientSessionFactory.getServerLocator().getTopology().getMember(rc).getNodeId();
      if (remotingService != null) {
         remotingService.freeze(nodeID, null);
      }
   }

   @Override
   public void postConnectionFreeze() {
      if (primaryOnlyPolicy.getScaleDownPolicy() != null && primaryOnlyPolicy.getScaleDownPolicy().isEnabled() && scaleDownClientSessionFactory != null) {
         try {
            scaleDown(primaryOnlyPolicy.getScaleDownPolicy().getCommitInterval());
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.failedToScaleDown(e);
         } finally {
            scaleDownClientSessionFactory.close();
            scaleDownServerLocator.close();
         }
      }
   }

   public void connectToScaleDownTarget(ScaleDownPolicy scaleDownPolicy) {
      try {
         scaleDownServerLocator = ScaleDownPolicy.getScaleDownConnector(scaleDownPolicy, activeMQServer);
         //use a Node Locator to connect to the cluster
         scaleDownServerLocator.setProtocolManagerFactory(ActiveMQServerSideProtocolManagerFactory.getInstance(scaleDownServerLocator, activeMQServer.getStorageManager()));
         NodeLocator nodeLocator = scaleDownPolicy.getGroupName() == null ? new AnyNodeLocatorForScaleDown(activeMQServer) : new NamedNodeLocatorForScaleDown(scaleDownPolicy.getGroupName(), activeMQServer);
         scaleDownServerLocator.addClusterTopologyListener(nodeLocator);

         nodeLocator.connectToCluster(scaleDownServerLocator);
         // a timeout is necessary here in case we use a NamedNodeLocatorForScaleDown and there's no matching node in the cluster
         // should the timeout be configurable?
         nodeLocator.locateNode(ActiveMQClient.DEFAULT_DISCOVERY_INITIAL_WAIT_TIMEOUT);

         ClientSessionFactoryInternal clientSessionFactory = null;
         if (nodeLocator instanceof AnyNodeLocatorForScaleDown && scaleDownPolicy.getConnectors() != null) {
            try {
               clientSessionFactory = scaleDownServerLocator.connect();
            } catch (Exception e) {
               logger.trace("Failed to connect to {}", scaleDownPolicy.getConnectors().get(0));
               if (clientSessionFactory != null) {
                  clientSessionFactory.close();
               }
               clientSessionFactory = null;
            }
         }

         while (clientSessionFactory == null) {
            Pair<TransportConfiguration, TransportConfiguration> possiblePrimary = null;
            possiblePrimary = nodeLocator.getPrimaryConfiguration();
            if (possiblePrimary == null) {  // we've tried every connector
               break;
            }
            try {
               clientSessionFactory = (ClientSessionFactoryInternal) scaleDownServerLocator.createSessionFactory(possiblePrimary.getA(), 0, false);
            } catch (Exception e) {
               logger.trace("Failed to connect to {}", possiblePrimary.getA());
               nodeLocator.notifyRegistrationFailed(false);
               if (clientSessionFactory != null) {
                  clientSessionFactory.close();
               }
               clientSessionFactory = null;
               // should I try the backup (i.e. getB()) from possiblePrimary?
            }
         }
         if (clientSessionFactory != null) {
            scaleDownClientSessionFactory = clientSessionFactory;
         } else {
            throw new ActiveMQException("Unable to connect to server for scale-down");
         }
      } catch (Exception e) {
         ActiveMQServerLogger.LOGGER.failedToScaleDown(e);
      }
   }

   public long scaleDown(int commitInterval) throws Exception {
      ScaleDownHandler scaleDownHandler = new ScaleDownHandler(activeMQServer.getPagingManager(), activeMQServer.getPostOffice(), activeMQServer.getNodeManager(), activeMQServer.getClusterManager().getClusterController(), activeMQServer.getStorageManager(), commitInterval);
      ConcurrentMap<SimpleString, DuplicateIDCache> duplicateIDCaches = ((PostOfficeImpl) activeMQServer.getPostOffice()).getDuplicateIDCaches();
      Map<SimpleString, List<Pair<byte[], Long>>> duplicateIDMap = new HashMap<>();
      for (SimpleString address : duplicateIDCaches.keySet()) {
         DuplicateIDCache duplicateIDCache = activeMQServer.getPostOffice().getDuplicateIDCache(address);
         duplicateIDMap.put(address, duplicateIDCache.getMap());
      }
      return scaleDownHandler.scaleDown(scaleDownClientSessionFactory, activeMQServer.getResourceManager(), duplicateIDMap, activeMQServer.getManagementService().getManagementAddress(), null);
   }
}
