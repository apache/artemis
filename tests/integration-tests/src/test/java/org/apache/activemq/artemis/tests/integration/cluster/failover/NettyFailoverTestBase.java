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
package org.apache.activemq.artemis.tests.integration.cluster.failover;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryInternal;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.ha.ReplicaPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.SharedStoreBackupPolicyConfiguration;
import org.apache.activemq.artemis.core.config.storage.DatabaseStorageConfiguration;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.NodeManager;
import org.apache.activemq.artemis.core.server.impl.InVMNodeManager;
import org.apache.activemq.artemis.core.server.impl.jdbc.JdbcNodeManager;
import org.apache.activemq.artemis.tests.extensions.ThreadLeakCheckExtension;
import org.apache.activemq.artemis.tests.integration.cluster.util.SameProcessActiveMQServer;
import org.apache.activemq.artemis.tests.integration.cluster.util.TestableServer;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.apache.activemq.artemis.utils.actors.OrderedExecutorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class NettyFailoverTestBase extends FailoverTest {

   public enum NodeManagerType {
      InVM, Jdbc
   }

   public final NodeManagerType nodeManagerType;

   public NettyFailoverTestBase(NodeManagerType nodeManagerType) {
      this.nodeManagerType = nodeManagerType;
   }

   @Override
   protected TransportConfiguration getAcceptorTransportConfiguration(final boolean primary) {
      return getNettyAcceptorTransportConfiguration(primary);
   }

   @Override
   protected TransportConfiguration getConnectorTransportConfiguration(final boolean primary) {
      return getNettyConnectorTransportConfiguration(primary);
   }

   private List<ScheduledExecutorService> scheduledExecutorServices = new ArrayList<>();
   private List<ExecutorService> executors = new ArrayList<>();

   @Override
   protected NodeManager createReplicatedBackupNodeManager(Configuration backupConfig) {
      assumeTrue(nodeManagerType == NodeManagerType.InVM, "Replicated backup is supported only by " + NodeManagerType.InVM + " Node Manager");
      return super.createReplicatedBackupNodeManager(backupConfig);
   }

   @Override
   protected Configuration createDefaultInVMConfig() throws Exception {
      final Configuration config = super.createDefaultInVMConfig();
      return config;
   }

   @Override
   protected NodeManager createNodeManager() throws Exception {

      switch (nodeManagerType) {

         case InVM:
            return new InVMNodeManager(false);
         case Jdbc:
            final ThreadFactory daemonThreadFactory = t -> {
               final Thread th = new Thread(t);
               th.setDaemon(true);
               return th;
            };
            final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(daemonThreadFactory);
            scheduledExecutorServices.add(scheduledExecutorService);
            final ExecutorService executor = Executors.newFixedThreadPool(2, daemonThreadFactory);
            executors.add(executor);
            final DatabaseStorageConfiguration dbConf = createDefaultDatabaseStorageConfiguration();
            final ExecutorFactory executorFactory = new OrderedExecutorFactory(executor);
            return JdbcNodeManager.with(dbConf, scheduledExecutorService, executorFactory);
         default:
            throw new AssertionError("enum type not supported!");
      }
   }


   @Override
   protected TestableServer createTestableServer(Configuration config) throws Exception {
      final boolean isBackup = config.getHAPolicyConfiguration() instanceof ReplicaPolicyConfiguration || config.getHAPolicyConfiguration() instanceof SharedStoreBackupPolicyConfiguration;
      NodeManager nodeManager = this.nodeManager;
      //create a separate NodeManager for the backup
      if (isBackup && (nodeManagerType == NodeManagerType.Jdbc)) {
         nodeManager = createNodeManager();
      }
      return new SameProcessActiveMQServer(createInVMFailoverServer(true, config, nodeManager, isBackup ? 2 : 1));
   }


   @AfterEach
   public void shutDownExecutors() {
      if (!scheduledExecutorServices.isEmpty()) {
         ThreadLeakCheckExtension.addKownThread("oracle.jdbc.driver.BlockSource.ThreadedCachingBlockSource.BlockReleaser");
         executors.forEach(ExecutorService::shutdown);
         scheduledExecutorServices.forEach(ExecutorService::shutdown);
         executors.clear();
         scheduledExecutorServices.clear();
      }
   }

   @Test
   @Timeout(120)
   public void testFailoverWithHostAlias() throws Exception {
      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.HOST_PROP_NAME, "127.0.0.1");
      TransportConfiguration tc = createTransportConfiguration(true, false, params);

      ServerLocator locator = addServerLocator(ActiveMQClient.createServerLocatorWithHA(tc)).setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true).setReconnectAttempts(15);

      ClientSessionFactoryInternal sf = createSessionFactoryAndWaitForTopology(locator, 2);

      ClientSession session = createSession(sf, true, true, 0);

      session.createQueue(QueueConfiguration.of(ADDRESS));

      ClientProducer producer = session.createProducer(ADDRESS);

      final int numMessages = 10;

      ClientConsumer consumer = session.createConsumer(ADDRESS);

      session.start();

      crash(session);

      sendMessages(session, producer, numMessages);
      receiveMessages(consumer, 0, numMessages, true);

      session.close();

      sf.close();

      assertEquals(0, sf.numSessions());

      assertEquals(0, sf.numConnections());
   }

}
