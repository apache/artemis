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

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.postoffice.PostOffice;
import org.apache.activemq.artemis.core.protocol.core.Channel;
import org.apache.activemq.artemis.core.protocol.core.ChannelHandler;
import org.apache.activemq.artemis.core.remoting.server.RemotingService;
import org.apache.activemq.artemis.core.replication.ReplicationManager;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.NodeManager;
import org.apache.activemq.artemis.core.server.QueueFactory;
import org.apache.activemq.artemis.core.server.cluster.ha.HAManager;
import org.apache.activemq.artemis.core.server.cluster.ha.StandaloneHAManager;
import org.apache.activemq.artemis.core.server.group.GroupingHandler;
import org.apache.activemq.artemis.core.server.management.ManagementService;
import org.apache.activemq.artemis.spi.core.remoting.Acceptor;

/**
 * An activation controls the lifecycle of the server and any components specific to the Activation itself.
 */
public abstract class Activation implements Runnable {

   public abstract void close(boolean permanently, boolean restarting) throws Exception;

   /**
    * freeze the connection but allow the Activation to over ride this and decide if any connections should be left open.
    */
   public void freezeConnections(RemotingService remotingService) {
      if (remotingService != null) {
         remotingService.freeze(null, null);
      }
   }

   /**
    * allow the activation to override this if it needs to tidy up after freezing the connection. it's a different
    * method as it's called outside of the lock that the previous method is.
    */
   public void postConnectionFreeze() {
   }

   /**
    * called before the server is closing the journals so the activation can tidy up stuff
    */
   public void preStorageClose() throws Exception {
   }

   /**
    * called by the server to notify the Activation that the server is stopping
    */
   public void sendPrimaryIsStopping() {
   }

   /**
    * called by the ha manager to notify the Activation that HA is now active
    */
   public void haStarted() {
   }

   /**
    * allows the Activation to register a channel handler so it can handle any packets that are unique to the Activation
    */
   public ChannelHandler getActivationChannelHandler(Channel channel, Acceptor acceptorUsed) {
      return null;
   }

   /**
    * {@return the HA manager used for this Activation}
    */
   public HAManager getHAManager() {
      return new StandaloneHAManager();
   }

   /**
    * create the Journal loader needed for this Activation.
    */
   public JournalLoader createJournalLoader(PostOffice postOffice,
                                            PagingManager pagingManager,
                                            StorageManager storageManager,
                                            QueueFactory queueFactory,
                                            NodeManager nodeManager,
                                            ManagementService managementService,
                                            GroupingHandler groupingHandler,
                                            Configuration configuration,
                                            ActiveMQServer parentServer) throws ActiveMQException {
      return new PostOfficeJournalLoader(postOffice, pagingManager, storageManager, queueFactory, nodeManager, managementService, groupingHandler, configuration);
   }

   // todo, remove this, its only needed for JMSServerManagerImpl, it should be sought elsewhere
   public ReplicationManager getReplicationManager() {
      return null;
   }

   public boolean isReplicaSync() {
      return false;
   }
}
