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
package org.apache.activemq.artemis.tests.integration.paging;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.ClusterConnectionConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.HAPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.SharedStorePrimaryPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.SharedStoreBackupPolicyConfiguration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptorFactory;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;

/**
 * Support class for server that are using an external process on the testsuite
 */
public class SpawnedServerSupport {

   static ActiveMQServer createServer(String folder) {
      return ActiveMQServers.newActiveMQServer(createConfig(folder), true);
   }

   static Configuration createConfig(String folder) {
      AddressSettings settings = new AddressSettings().setMaxDeliveryAttempts(-1).setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE).setPageSizeBytes(10 * 1024).setMaxSizeBytes(100 * 1024).setMaxReadPageBytes(-1).setMaxReadPageMessages(-1);

      Configuration config = new ConfigurationImpl().setSecurityEnabled(false).setJournalMinFiles(2).setJournalFileSize(100 * 1024).setJournalType(ActiveMQTestBase.getDefaultJournalType()).setJournalCompactMinFiles(0).setJournalCompactPercentage(0).setClusterPassword(ActiveMQTestBase.CLUSTER_PASSWORD).setJournalDirectory(ActiveMQTestBase.getJournalDir(folder, 0, false)).setBindingsDirectory(ActiveMQTestBase.getBindingsDir(folder, 0, false)).setPagingDirectory(ActiveMQTestBase.getPageDir(folder, 0, false)).setLargeMessagesDirectory(ActiveMQTestBase.getLargeMessagesDir(folder, 0, false)).setPersistenceEnabled(true).addAddressSetting("#", settings).addAcceptorConfiguration(new TransportConfiguration(ActiveMQTestBase.NETTY_ACCEPTOR_FACTORY));

      return config;
   }

   static Configuration createSharedFolderConfig(String folder, int thisport, int otherport, boolean isBackup) {
      HAPolicyConfiguration haPolicyConfiguration = null;

      if (isBackup) {
         haPolicyConfiguration = new SharedStoreBackupPolicyConfiguration();
         ((SharedStoreBackupPolicyConfiguration) haPolicyConfiguration).setAllowFailBack(false);
      } else {
         haPolicyConfiguration = new SharedStorePrimaryPolicyConfiguration();
      }

      Configuration config = createConfig(folder).clearAcceptorConfigurations().setJournalFileSize(15 * 1024 * 1024).addAcceptorConfiguration(createTransportConfigiguration(true, thisport)).addConnectorConfiguration("thisServer", createTransportConfigiguration(false, thisport)).addConnectorConfiguration("otherServer", createTransportConfigiguration(false, otherport)).setMessageExpiryScanPeriod(500).addClusterConfiguration(isBackup ? setupClusterConn("thisServer", "otherServer") : setupClusterConn("thisServer")).setHAPolicyConfiguration(haPolicyConfiguration);

      return config;
   }

   protected static final ClusterConnectionConfiguration setupClusterConn(String connectorName, String... connectors) {
      List<String> connectorList = new LinkedList<>();
      for (String conn : connectors) {
         connectorList.add(conn);
      }

      ClusterConnectionConfiguration ccc = new ClusterConnectionConfiguration().setName("cluster1").setAddress("jms").setConnectorName(connectorName).setRetryInterval(10).setDuplicateDetection(false).setMessageLoadBalancingType(MessageLoadBalancingType.STRICT).setConfirmationWindowSize(1).setStaticConnectors(connectorList);

      return ccc;
   }

   public static ServerLocator createLocator(int port) {
      TransportConfiguration config = createTransportConfigiguration(false, port);
      return ActiveMQClient.createServerLocator(true, config);
   }

   static TransportConfiguration createTransportConfigiguration(boolean acceptor, int port) {
      String className;

      if (acceptor) {
         className = NettyAcceptorFactory.class.getName();
      } else {
         className = NettyConnectorFactory.class.getName();
      }
      Map<String, Object> serverParams = new HashMap<>();
      serverParams.put(org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.PORT_PROP_NAME, port);
      return new TransportConfiguration(className, serverParams);
   }

   static ActiveMQServer createSharedFolderServer(String folder, int thisPort, int otherPort, boolean isBackup) {
      return ActiveMQServers.newActiveMQServer(createSharedFolderConfig(folder, thisPort, otherPort, isBackup), true);
   }
}
