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
package org.apache.activemq.artemis.core.server;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ClusterTopologyListener;

/**
 * A class that will locate a particular server running in a cluster. How this server is chosen is a job for the
 * implementation.
 * <p>
 * This is used for replication (which needs a QuorumManager) and scaling-down (which does not need a QuorumManager).
 */
public abstract class NodeLocator implements ClusterTopologyListener {

   @FunctionalInterface
   public interface BackupRegistrationListener {

      void onBackupRegistrationFailed(boolean alreadyReplicating);
   }

   private final BackupRegistrationListener backupRegistrationListener;

   public NodeLocator(BackupRegistrationListener backupRegistrationListener) {
      this.backupRegistrationListener = backupRegistrationListener;
   }

   /**
    * Use this constructor when the NodeLocator is used for scaling down rather than replicating
    */
   public NodeLocator() {
      this(null);
   }

   /**
    * Locates a server in a cluster with a timeout
    */
   public abstract void locateNode(long timeout) throws ActiveMQException;

   /**
    * Locates a server in a cluster
    */
   public abstract void locateNode() throws ActiveMQException;

   /**
    * {@return the current connector}
    */
   public abstract Pair<TransportConfiguration, TransportConfiguration> getPrimaryConfiguration();

   /**
    * {@return the node id for the current connector}
    */
   public abstract String getNodeID();

   /**
    * tells the locator the current connector has failed.
    */
   public void notifyRegistrationFailed(boolean alreadyReplicating) {
      if (backupRegistrationListener != null) {
         backupRegistrationListener.onBackupRegistrationFailed(alreadyReplicating);
      }
   }
}
