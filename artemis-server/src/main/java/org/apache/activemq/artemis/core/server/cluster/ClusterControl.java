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

import java.util.Optional;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryInternal;
import org.apache.activemq.artemis.core.config.ClusterConnectionConfiguration;
import org.apache.activemq.artemis.core.config.ConfigurationUtils;
import org.apache.activemq.artemis.core.protocol.core.Channel;
import org.apache.activemq.artemis.core.protocol.core.CoreRemotingConnection;
import org.apache.activemq.artemis.core.protocol.core.impl.ChannelImpl;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.BackupRegistrationMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.BackupRequestMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.BackupResponseMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ClusterConnectMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ClusterConnectReplyMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.NodeAnnounceMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ScaleDownAnnounceMessage;
import org.apache.activemq.artemis.core.server.ActiveMQMessageBundle;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;

/**
 * handles the communication between a cluster node and the cluster, either the whole cluster or a specific node in the
 * cluster such as a replicating node.
 */
public class ClusterControl implements AutoCloseable {

   private Channel clusterChannel;

   private final ClientSessionFactoryInternal sessionFactory;

   private final ActiveMQServer server;

   private final String clusterUser;

   private final String clusterPassword;

   public ClusterControl(ClientSessionFactoryInternal sessionFactory, ActiveMQServer server) {
      this.sessionFactory = sessionFactory;
      this.server = server;
      this.clusterUser = server.getConfiguration().getClusterUser();
      this.clusterPassword = server.getConfiguration().getClusterPassword();
   }

   public Optional<Channel> getClusterChannel() {
      return Optional.ofNullable(clusterChannel);
   }

   /**
    * authorise this cluster control so it can communicate with the cluster, it will set the cluster channel on a
    * successful authentication.
    *
    * @throws ActiveMQException if authorisation wasn't successful.
    */
   public void authorize() throws ActiveMQException {
      CoreRemotingConnection connection = (CoreRemotingConnection) sessionFactory.getConnection();

      clusterChannel = connection.getChannel(ChannelImpl.CHANNEL_ID.CLUSTER.id, -1);

      ClusterConnectReplyMessage packet = (ClusterConnectReplyMessage) clusterChannel.sendBlocking(new ClusterConnectMessage(clusterUser, clusterPassword), PacketImpl.CLUSTER_CONNECT_REPLY);

      if (!packet.isAuthorized()) {
         throw ActiveMQMessageBundle.BUNDLE.unableToValidateClusterUser(clusterUser);
      }
   }

   /**
    * Announce replicating backup to primary server.
    *
    * @param attemptingFailBack if {@code true} then this server wants to trigger a fail-back when up-to-date, that is
    *                           it wants to take over the role of 'live' from the current 'live' server.
    */
   public void announceReplicatingBackupToPrimary(final boolean attemptingFailBack,
                                                  String replicationClusterName) throws ActiveMQException {

      ClusterConnectionConfiguration config = ConfigurationUtils.getReplicationClusterConfiguration(server.getConfiguration(), replicationClusterName);
      if (config == null) {
         ActiveMQServerLogger.LOGGER.announceBackupNoClusterConnections();
         throw new ActiveMQException("lacking cluster connection");

      }
      TransportConfiguration connector = server.getConfiguration().getConnectorConfigurations().get(config.getConnectorName());

      if (connector == null) {
         ActiveMQServerLogger.LOGGER.announceBackupNoConnector(config.getConnectorName());
         throw new ActiveMQException("lacking cluster connection");
      }

      clusterChannel.send(new BackupRegistrationMessage(connector, clusterUser, clusterPassword, attemptingFailBack));
   }

   /**
    * Announce this node to the cluster.
    *
    * @param currentEventID     used if multiple announcements about this node are made.
    * @param nodeID             the node id if the announcing node
    * @param backupGroupName    the backup group name.
    * @param scaleDownGroupName the scaledown group name
    * @param isBackup           are we a backup
    * @param config             the transports config
    * @param backupConfig       the transports backup config
    */
   public void sendNodeAnnounce(final long currentEventID,
                                String nodeID,
                                String backupGroupName,
                                String scaleDownGroupName,
                                boolean isBackup,
                                TransportConfiguration config,
                                TransportConfiguration backupConfig) {
      clusterChannel.send(new NodeAnnounceMessage(currentEventID, nodeID, backupGroupName, scaleDownGroupName, isBackup, config, backupConfig));
   }

   public Channel createReplicationChannel() {
      CoreRemotingConnection connection = (CoreRemotingConnection) sessionFactory.getConnection();
      return connection.getChannel(ChannelImpl.CHANNEL_ID.REPLICATION.id, -1);
   }

   public ClientSessionFactoryInternal getSessionFactory() {
      return sessionFactory;
   }

   @Override
   public void close() {
      sessionFactory.close();
   }

   public boolean requestReplicatedBackup(int backupSize, SimpleString nodeID) {
      BackupRequestMessage backupRequestMessage = new BackupRequestMessage(backupSize, nodeID);
      return requestBackup(backupRequestMessage);
   }

   private boolean requestBackup(BackupRequestMessage backupRequestMessage) {
      BackupResponseMessage packet;
      try {
         packet = (BackupResponseMessage) clusterChannel.sendBlocking(backupRequestMessage, PacketImpl.BACKUP_REQUEST_RESPONSE);
      } catch (ActiveMQException e) {
         return false;
      }
      return packet.isBackupStarted();
   }

   public boolean requestSharedStoreBackup(int backupSize,
                                           String journalDirectory,
                                           String bindingsDirectory,
                                           String largeMessagesDirectory,
                                           String pagingDirectory) {
      BackupRequestMessage backupRequestMessage = new BackupRequestMessage(backupSize, journalDirectory, bindingsDirectory, largeMessagesDirectory, pagingDirectory);
      return requestBackup(backupRequestMessage);
   }

   public void announceScaleDown(SimpleString targetNodeId, SimpleString scaledDownNodeId) {
      ScaleDownAnnounceMessage announceMessage = new ScaleDownAnnounceMessage(targetNodeId, scaledDownNodeId);
      clusterChannel.send(announceMessage);
   }

   public String getClusterUser() {
      return clusterUser;
   }

   public String getClusterPassword() {
      return clusterPassword;
   }
}
