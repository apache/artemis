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

import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorInternal;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.journal.Journal;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.persistence.GroupingInfo;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.postoffice.PostOffice;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.NodeManager;
import org.apache.activemq.artemis.core.server.QueueFactory;
import org.apache.activemq.artemis.core.server.cluster.ActiveMQServerSideProtocolManagerFactory;
import org.apache.activemq.artemis.core.server.cluster.ClusterController;
import org.apache.activemq.artemis.core.server.group.GroupingHandler;
import org.apache.activemq.artemis.core.server.management.ManagementService;
import org.apache.activemq.artemis.core.transaction.ResourceManager;

/**
 * Instead of loading into its own post office this will use its parent server (the active server) and load into that.
 * Since the server is already running we have to make sure we don't route any message that may subsequently get deleted
 * or acked.
 */
public class BackupRecoveryJournalLoader extends PostOfficeJournalLoader {

   private ActiveMQServer parentServer;
   private ServerLocator locator;
   private final ClusterController clusterController;
   private final StorageManager storageManager;
   private final Configuration configuration;

   public BackupRecoveryJournalLoader(PostOffice postOffice,
                                      PagingManager pagingManager,
                                      StorageManager storageManager,
                                      QueueFactory queueFactory,
                                      NodeManager nodeManager,
                                      ManagementService managementService,
                                      GroupingHandler groupingHandler,
                                      Configuration configuration,
                                      ActiveMQServer parentServer,
                                      ServerLocatorInternal locator,
                                      ClusterController clusterController) {

      super(postOffice, pagingManager, storageManager, queueFactory, nodeManager, managementService, groupingHandler, configuration, parentServer.getResourceQuotaService());
      this.parentServer = parentServer;
      this.locator = locator;
      this.clusterController = clusterController;
      this.storageManager = storageManager;
      this.configuration = configuration;
   }

   @Override
   public void handleGroupingBindings(List<GroupingInfo> groupingInfos) {
      //currently only the node that is configured with the local group handler can recover these as all other nodes are
      //remote handlers, this means that you can only use FULL backup server when using group handlers.
      //todo maybe in the future we can restart the handler on the active server as a local handler and redistribute the state
      if (groupingInfos != null && !groupingInfos.isEmpty()) {
         ActiveMQServerLogger.LOGGER.groupBindingsOnRecovery();
      }
   }

   @Override
   public void handleDuplicateIds(Map<SimpleString, List<Pair<byte[], Long>>> duplicateIDMap) throws Exception {
      //nothing to do here so override so we dont bother creating the caches
   }

   @Override
   public void postLoad(Journal messageJournal,
                        ResourceManager resourceManager,
                        Map<SimpleString, List<Pair<byte[], Long>>> duplicateIDMap) throws Exception {
      ScaleDownHandler scaleDownHandler = new ScaleDownHandler(pagingManager, postOffice, nodeManager, clusterController, parentServer != null ? parentServer.getStorageManager() : storageManager, parentServer != null ? parentServer.getHAPolicy().getScaleDownCommitInterval() : -1);
      locator.setProtocolManagerFactory(ActiveMQServerSideProtocolManagerFactory.getInstance(locator, storageManager));

      try (ClientSessionFactory sessionFactory = locator.createSessionFactory()) {
         scaleDownHandler.scaleDown(sessionFactory, resourceManager, duplicateIDMap, parentServer != null ? parentServer.getConfiguration().getManagementAddress() : configuration.getManagementAddress(), parentServer != null ? parentServer.getNodeID() : null);
      }
   }

   @Override
   public void cleanUp() {
      super.cleanUp();
      locator.close();
   }
}
