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
package org.apache.activemq.artemis.core.config;

import java.net.URI;
import java.util.List;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.ActiveMQIllegalStateException;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicationBackupPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicationPrimaryPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.ColocatedPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.PrimaryOnlyPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicaPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicatedPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.SharedStorePrimaryPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.SharedStoreBackupPolicyConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQMessageBundle;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.cluster.ha.ReplicationBackupPolicy;
import org.apache.activemq.artemis.core.server.cluster.ha.ReplicationPrimaryPolicy;
import org.apache.activemq.artemis.core.server.cluster.ha.BackupPolicy;
import org.apache.activemq.artemis.core.server.cluster.ha.ColocatedPolicy;
import org.apache.activemq.artemis.core.server.cluster.ha.HAPolicy;
import org.apache.activemq.artemis.core.server.cluster.ha.PrimaryOnlyPolicy;
import org.apache.activemq.artemis.core.server.cluster.ha.ReplicaPolicy;
import org.apache.activemq.artemis.core.server.cluster.ha.ReplicatedPolicy;
import org.apache.activemq.artemis.core.server.cluster.ha.ScaleDownPolicy;
import org.apache.activemq.artemis.core.server.cluster.ha.SharedStorePrimaryPolicy;
import org.apache.activemq.artemis.core.server.cluster.ha.SharedStoreBackupPolicy;
import org.apache.activemq.artemis.uri.AcceptorTransportConfigurationParser;
import org.apache.activemq.artemis.uri.ConnectorTransportConfigurationParser;

public final class ConfigurationUtils {

   private ConfigurationUtils() {
      // Utility class
   }

   public static ClusterConnectionConfiguration getReplicationClusterConfiguration(Configuration conf,
                                                                                   String replicationCluster) throws ActiveMQIllegalStateException {
      if (replicationCluster == null || replicationCluster.isEmpty())
         return conf.getClusterConfigurations().get(0);
      for (ClusterConnectionConfiguration clusterConf : conf.getClusterConfigurations()) {
         if (replicationCluster.equals(clusterConf.getName()))
            return clusterConf;
      }
      throw new ActiveMQIllegalStateException("Missing cluster-configuration for replication-clustername '" + replicationCluster + "'.");
   }

   public static HAPolicy getHAPolicy(HAPolicyConfiguration conf,
                                      ActiveMQServer server) throws ActiveMQIllegalStateException {
      if (conf == null) {
         return new PrimaryOnlyPolicy();
      }

      switch (conf.getType()) {
         case PRIMARY_ONLY: {
            PrimaryOnlyPolicyConfiguration pc = (PrimaryOnlyPolicyConfiguration) conf;
            return new PrimaryOnlyPolicy(getScaleDownPolicy(pc.getScaleDownConfiguration()));
         }
         case REPLICATION_PRIMARY_QUORUM_VOTING: {
            ReplicatedPolicyConfiguration pc = (ReplicatedPolicyConfiguration) conf;
            return new ReplicatedPolicy(pc.isCheckForActiveServer(), pc.getGroupName(), pc.getClusterName(), pc.getMaxSavedReplicatedJournalsSize(), pc.getInitialReplicationSyncTimeout(), server.getNetworkHealthCheck(), pc.getVoteOnReplicationFailure(), pc.getQuorumSize(), pc.getVoteRetries(), pc.getVoteRetryWait(), pc.getQuorumVoteWait(), pc.getRetryReplicationWait());
         }
         case REPLICATION_BACKUP_QUORUM_VOTING: {
            ReplicaPolicyConfiguration pc = (ReplicaPolicyConfiguration) conf;
            return new ReplicaPolicy(pc.getClusterName(), pc.getMaxSavedReplicatedJournalsSize(), pc.getGroupName(), pc.isRestartBackup(), pc.isAllowFailBack(), pc.getInitialReplicationSyncTimeout(), getScaleDownPolicy(pc.getScaleDownConfiguration()), server.getNetworkHealthCheck(), pc.getVoteOnReplicationFailure(), pc.getQuorumSize(), pc.getVoteRetries(), pc.getVoteRetryWait(), pc.getQuorumVoteWait(), pc.getRetryReplicationWait());
         }
         case REPLICATION_PRIMARY_LOCK_MANAGER: {
            return ReplicationPrimaryPolicy.with((ReplicationPrimaryPolicyConfiguration) conf);
         }
         case REPLICATION_BACKUP_LOCK_MANAGER: {
            return ReplicationBackupPolicy.with((ReplicationBackupPolicyConfiguration) conf);
         }
         case SHARED_STORE_PRIMARY: {
            SharedStorePrimaryPolicyConfiguration pc = (SharedStorePrimaryPolicyConfiguration) conf;
            return new SharedStorePrimaryPolicy(pc.isFailoverOnServerShutdown(), pc.isWaitForActivation());
         }
         case SHARED_STORE_BACKUP: {
            SharedStoreBackupPolicyConfiguration pc = (SharedStoreBackupPolicyConfiguration) conf;
            return new SharedStoreBackupPolicy(pc.isFailoverOnServerShutdown(), pc.isRestartBackup(), pc.isAllowFailBack(), getScaleDownPolicy(pc.getScaleDownConfiguration()));
         }
         case COLOCATED: {
            ColocatedPolicyConfiguration pc = (ColocatedPolicyConfiguration) conf;

            HAPolicyConfiguration primaryConfig = pc.getPrimaryConfig();
            HAPolicy primaryPolicy;
            //if null default to colocated
            if (primaryConfig == null) {
               primaryPolicy = new ReplicatedPolicy(server.getNetworkHealthCheck(), ActiveMQDefaultConfiguration.getDefaultQuorumVoteWait());
            } else {
               primaryPolicy = getHAPolicy(primaryConfig, server);
            }
            HAPolicyConfiguration backupConf = pc.getBackupConfig();
            BackupPolicy backupPolicy;
            if (backupConf == null) {
               if (primaryPolicy instanceof ReplicatedPolicy) {
                  backupPolicy = new ReplicaPolicy(server.getNetworkHealthCheck(), ActiveMQDefaultConfiguration.getDefaultQuorumVoteWait());
               } else if (primaryPolicy instanceof SharedStorePrimaryPolicy) {
                  backupPolicy = new SharedStoreBackupPolicy();
               } else {
                  throw ActiveMQMessageBundle.BUNDLE.primaryBackupMismatch();
               }
            } else {
               backupPolicy = (BackupPolicy) getHAPolicy(backupConf, server);
            }

            if ((primaryPolicy instanceof ReplicatedPolicy && !(backupPolicy instanceof ReplicaPolicy)) || (primaryPolicy instanceof SharedStorePrimaryPolicy && !(backupPolicy instanceof SharedStoreBackupPolicy))) {
               throw ActiveMQMessageBundle.BUNDLE.primaryBackupMismatch();
            }
            return new ColocatedPolicy(pc.isRequestBackup(), pc.getBackupRequestRetries(), pc.getBackupRequestRetryInterval(), pc.getMaxBackups(), pc.getBackupPortOffset(), pc.getExcludedConnectors(), primaryPolicy, backupPolicy);
         }

      }
      throw ActiveMQMessageBundle.BUNDLE.unsupportedHAPolicyConfiguration(conf);
   }

   public static ScaleDownPolicy getScaleDownPolicy(ScaleDownConfiguration scaleDownConfiguration) {
      if (scaleDownConfiguration != null) {
         if (scaleDownConfiguration.getDiscoveryGroup() != null) {
            return new ScaleDownPolicy(scaleDownConfiguration.getDiscoveryGroup(), scaleDownConfiguration.getGroupName(), scaleDownConfiguration.getClusterName(), scaleDownConfiguration.isEnabled(), scaleDownConfiguration.getCommitInterval());
         } else {
            return new ScaleDownPolicy(scaleDownConfiguration.getConnectors(), scaleDownConfiguration.getGroupName(), scaleDownConfiguration.getClusterName(), scaleDownConfiguration.isEnabled(), scaleDownConfiguration.getCommitInterval());
         }
      }
      return null;
   }

   // A method to check the passed Configuration object and warn users if semantically unwise parameters are present
   public static void validateConfiguration(Configuration configuration) {
      // Warn if connection-ttl-override/connection-ttl == check-period
      compareTTLWithCheckPeriod(configuration);
   }

   public static void throwIfReloadableConfigProvidedWithoutFileAndBrokerPropertiesUrlNonNullAndReload(Configuration configuration, String propertiesFileUrl) {
      if (configuration.getConfigurationUrl() == null && configuration.getConfigurationFileRefreshPeriod() > 0 && configuration.resolvePropertiesSources(propertiesFileUrl) != null) {
         // if any non xml (programmatic) provided config is reloadable, on the first properties source reload it will whack that config as the reload has the source of truth for reloadable attributes
         if (!configuration.getSecurityRoles().isEmpty() ||
               !configuration.getAddressSettings().isEmpty() ||
               !configuration.getDivertConfigurations().isEmpty() ||
               !configuration.getAddressConfigurations().isEmpty() ||
               !configuration.getQueueConfigs().isEmpty() ||
               !configuration.getBridgeConfigurations().isEmpty() ||
               !configuration.getConnectorConfigurations().isEmpty() ||
               !configuration.getAcceptorConfigurations().isEmpty() ||
               !configuration.getAMQPConnection().isEmpty() ||
               !configuration.getConnectionRouters().isEmpty()) {

            throw new IllegalArgumentException(String.format("a properties source (%s) is illegal, programmatic config contains reloadable elements and configurationFileRefreshPeriod > 0; your programmatic config will be replaced on reload of the properties source", propertiesFileUrl));
         }
      }
   }

   public static List<TransportConfiguration> parseAcceptorURI(String name, String uri) {
      try {
         // remove all whitespace
         uri = uri.replaceAll("\\s", "");

         AcceptorTransportConfigurationParser parser = new AcceptorTransportConfigurationParser();

         List<TransportConfiguration> configurations = parser.newObject(parser.expandURI(uri), name);

         return configurations;
      } catch (Exception e) {
         throw new RuntimeException(e.getMessage(), e);
      }
   }

   public static List<TransportConfiguration> parseAcceptorURI(String name, URI uri)  {
      try {
         AcceptorTransportConfigurationParser parser = new AcceptorTransportConfigurationParser();

         List<TransportConfiguration> configurations = parser.newObject(uri, name);

         return configurations;
      } catch (Exception e) {
         throw new RuntimeException(e.getMessage(), e);
      }

   }

   public static List<TransportConfiguration> parseConnectorURI(String name, String uri) {
      try {
         // remove all whitespace
         uri = uri.replaceAll("\\s", "");

         ConnectorTransportConfigurationParser parser = new ConnectorTransportConfigurationParser();

         List<TransportConfiguration> configurations = parser.newObject(parser.expandURI(uri), name);

         return configurations;
      } catch (Exception e) {
         throw new RuntimeException(e.getMessage(), e);
      }
   }

   public static List<TransportConfiguration> parseConnectorURI(String name, URI uri) {
      try {
         ConnectorTransportConfigurationParser parser = new ConnectorTransportConfigurationParser();

         List<TransportConfiguration> configurations = parser.newObject(uri, name);

         return configurations;
      } catch (Exception e) {
         throw new RuntimeException(e.getMessage(), e);
      }

   }

   private static void compareTTLWithCheckPeriod(Configuration configuration) {
      for (ClusterConnectionConfiguration c : configuration.getClusterConfigurations())
         compareTTLWithCheckPeriod(c.getName(), c.getConnectionTTL(), configuration.getConnectionTTLOverride(), c.getClientFailureCheckPeriod());

      for (BridgeConfiguration c : configuration.getBridgeConfigurations())
         compareTTLWithCheckPeriod(c.getName(), c.getConnectionTTL(), configuration.getConnectionTTLOverride(), c.getClientFailureCheckPeriod());
   }

   private static void compareTTLWithCheckPeriod(String name,
                                                 long connectionTTL,
                                                 long connectionTTLOverride,
                                                 long checkPeriod) {
      if (connectionTTLOverride == checkPeriod)
         ActiveMQServerLogger.LOGGER.connectionTTLEqualsCheckPeriod(name, "connection-ttl-override", "check-period");

      if (connectionTTL == checkPeriod)
         ActiveMQServerLogger.LOGGER.connectionTTLEqualsCheckPeriod(name, "connection-ttl", "check-period");
   }
}
