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
package org.apache.activemq.artemis.tests.integration.cluster.distribution;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.BroadcastGroupConfiguration;
import org.apache.activemq.artemis.api.core.DiscoveryGroupConfiguration;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.UDPBroadcastEndpointFactory;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorInternal;
import org.apache.activemq.artemis.core.client.impl.Topology;
import org.apache.activemq.artemis.core.client.impl.TopologyMemberImpl;
import org.apache.activemq.artemis.core.config.ClusterConnectionConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.HAPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.DistributedPrimitiveManagerConfiguration;
import org.apache.activemq.artemis.core.config.ha.LiveOnlyPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicaPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicatedPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicationBackupPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicationPrimaryPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.SharedStoreMasterPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.SharedStoreSlavePolicyConfiguration;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.Bindings;
import org.apache.activemq.artemis.core.postoffice.PostOffice;
import org.apache.activemq.artemis.core.postoffice.QueueBinding;
import org.apache.activemq.artemis.core.postoffice.impl.LocalQueueBinding;
import org.apache.activemq.artemis.core.protocol.core.impl.CoreProtocolManagerFactory;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.server.NodeManager;
import org.apache.activemq.artemis.core.server.cluster.ActiveMQServerSideProtocolManagerFactory;
import org.apache.activemq.artemis.core.server.cluster.ClusterConnection;
import org.apache.activemq.artemis.core.server.cluster.ClusterManager;
import org.apache.activemq.artemis.core.server.cluster.RemoteQueueBinding;
import org.apache.activemq.artemis.core.server.cluster.impl.BridgeMetrics;
import org.apache.activemq.artemis.core.server.cluster.impl.ClusterConnectionImpl;
import org.apache.activemq.artemis.core.server.cluster.impl.ClusterConnectionMetrics;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.server.cluster.qourum.SharedNothingBackupQuorum;
import org.apache.activemq.artemis.core.server.group.GroupingHandler;
import org.apache.activemq.artemis.core.server.group.impl.GroupingHandlerConfiguration;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.impl.InVMNodeManager;
import org.apache.activemq.artemis.quorum.file.FileBasedPrimitiveManager;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.PortCheckRule;
import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;

public abstract class ClusterTestBase extends ActiveMQTestBase {

   private static final Logger log = Logger.getLogger(ClusterTestBase.class);

   private static final int[] PORTS = {TransportConstants.DEFAULT_PORT, TransportConstants.DEFAULT_PORT + 1, TransportConstants.DEFAULT_PORT + 2, TransportConstants.DEFAULT_PORT + 3, TransportConstants.DEFAULT_PORT + 4, TransportConstants.DEFAULT_PORT + 5, TransportConstants.DEFAULT_PORT + 6, TransportConstants.DEFAULT_PORT + 7, TransportConstants.DEFAULT_PORT + 8, TransportConstants.DEFAULT_PORT + 9,};

   @ClassRule
   public static PortCheckRule rule = new PortCheckRule(PORTS);

   protected int getLargeMessageSize() {
      return 500;
   }

   protected boolean isLargeMessage() {
      return false;
   }

   private static final long TIMEOUT_START_SERVER = 10;

   private static final SimpleString COUNT_PROP = new SimpleString("count_prop");

   protected static final SimpleString FILTER_PROP = new SimpleString("animal");

   private static final int MAX_SERVERS = 10;

   protected ConsumerHolder[] consumers;

   protected ActiveMQServer[] servers;

   protected NodeManager[] nodeManagers;

   protected ClientSessionFactory[] sfs;

   protected long[] timeStarts;

   protected ServerLocator[] locators;

   protected boolean isForceUniqueStorageManagerIds() {
      return true;
   }

   private DistributedPrimitiveManagerConfiguration pluggableQuorumConfiguration = null;

   private DistributedPrimitiveManagerConfiguration getOrCreatePluggableQuorumConfiguration() {
      if (pluggableQuorumConfiguration != null) {
         return pluggableQuorumConfiguration;
      }
      try {
         pluggableQuorumConfiguration = new DistributedPrimitiveManagerConfiguration(FileBasedPrimitiveManager.class.getName(), Collections.singletonMap("locks-folder", temporaryFolder.newFolder("manager").toString()));
      } catch (IOException ioException) {
         log.error(ioException);
         return null;
      }
      return pluggableQuorumConfiguration;
   }

   @Override
   @Before
   public void setUp() throws Exception {
      super.setUp();

      forceGC();

      consumers = new ConsumerHolder[ClusterTestBase.MAX_CONSUMERS];

      servers = new ActiveMQServer[ClusterTestBase.MAX_SERVERS];

      timeStarts = new long[ClusterTestBase.MAX_SERVERS];

      sfs = new ClientSessionFactory[ClusterTestBase.MAX_SERVERS];

      nodeManagers = new NodeManager[ClusterTestBase.MAX_SERVERS];

      for (int i = 0, nodeManagersLength = nodeManagers.length; i < nodeManagersLength; i++) {
         nodeManagers[i] = new InVMNodeManager(isSharedStore(), new File(getJournalDir(i, true)));
      }

      locators = new ServerLocator[ClusterTestBase.MAX_SERVERS];

   }

   public enum HAType {
      SharedStore, SharedNothingReplication, PluggableQuorumReplication
   }

   protected HAType haType() {
      return HAType.SharedNothingReplication;
   }

   /**
    * Whether the servers share the storage or not.
    */
   protected final boolean isSharedStore() {
      return HAType.SharedStore.equals(haType());
   }

   @Override
   @After
   public void tearDown() throws Exception {
      logTopologyDiagram();
      for (int i = 0; i < MAX_SERVERS; i++) {
         addActiveMQComponent(nodeManagers[i]);
      }
      servers = null;

      sfs = null;

      consumers = new ConsumerHolder[ClusterTestBase.MAX_CONSUMERS];

      nodeManagers = null;

      super.tearDown();

   }

   private static final int MAX_CONSUMERS = 100;

   protected static class ConsumerHolder {

      final ClientConsumer consumer;

      final ClientSession session;

      final int id;

      final int node;

      public ClientConsumer getConsumer() {
         return consumer;
      }

      public ClientSession getSession() {
         return session;
      }

      public int getId() {
         return id;
      }

      public int getNode() {
         return node;
      }

      ConsumerHolder(final int id, final ClientConsumer consumer, final ClientSession session, int node) {
         this.id = id;
         this.node = node;

         this.consumer = consumer;
         this.session = session;
      }

      void close() {
         if (consumer != null) {
            try {
               consumer.close();
            } catch (ActiveMQException e) {
               // ignore
            }
         }
         if (session != null) {
            try {
               session.close();
            } catch (ActiveMQException e) {
               // ignore
            }
         }
      }

      @Override
      public String toString() {
         return "id=" + id + ", consumer=" + consumer + ", session=" + session;
      }
   }

   protected ClientConsumer getConsumer(final int node) {
      return consumers[node].consumer;
   }

   protected void waitForFailoverTopology(final int bNode, final int... nodes) throws Exception {
      ActiveMQServer server = servers[bNode];

      log.debug("waiting for " + Arrays.toString(nodes) + " on the topology for server = " + server);

      long start = System.currentTimeMillis();

      final int waitMillis = 2000;
      final int sleepTime = 50;
      int nWaits = 0;
      while (server.getClusterManager() == null && nWaits++ < waitMillis / sleepTime) {
         Thread.sleep(sleepTime);
      }
      Set<ClusterConnection> ccs = server.getClusterManager().getClusterConnections();

      if (ccs.size() != 1) {
         throw new IllegalStateException("You need a single cluster connection on this version of waitForTopology on ServiceTestBase");
      }

      boolean exists = false;

      for (int node : nodes) {
         ClusterConnectionImpl clusterConnection = (ClusterConnectionImpl) ccs.iterator().next();
         Topology topology = clusterConnection.getTopology();
         TransportConfiguration nodeConnector = servers[node].getClusterManager().getClusterConnections().iterator().next().getConnector();
         do {
            Collection<TopologyMemberImpl> members = topology.getMembers();
            for (TopologyMemberImpl member : members) {
               if (member.getConnector().getA() != null && member.getConnector().getA().equals(nodeConnector)) {
                  exists = true;
                  break;
               }
            }
            if (exists) {
               break;
            }
            Thread.sleep(10);
         }
         while (System.currentTimeMillis() - start < WAIT_TIMEOUT);
         if (!exists) {
            String msg = "Timed out waiting for cluster topology of " + Arrays.toString(nodes) +
               " (received " +
               topology.getMembers().size() +
               ") topology = " +
               topology +
               ")";

            log.error(msg);

            logTopologyDiagram();

            throw new Exception(msg);
         }
      }
   }

   private void logTopologyDiagram() {
      try {
         StringBuffer topologyDiagram = new StringBuffer();
         for (ActiveMQServer activeMQServer : servers) {
            if (activeMQServer != null) {
               topologyDiagram.append("\n").append(activeMQServer.getIdentity()).append("\n");
               if (activeMQServer.isStarted()) {
                  Set<ClusterConnection> ccs = activeMQServer.getClusterManager().getClusterConnections();

                  if (ccs.size() >= 1) {
                     ClusterConnectionImpl clusterConnection = (ClusterConnectionImpl) ccs.iterator().next();
                     Collection<TopologyMemberImpl> members = clusterConnection.getTopology().getMembers();
                     for (TopologyMemberImpl member : members) {
                        String nodeId = member.getNodeId();
                        String liveServer = null;
                        String backupServer = null;
                        for (ActiveMQServer server : servers) {
                           if (server != null && server.getNodeID() != null && server.isActive() && server.getNodeID().toString().equals(nodeId)) {
                              if (server.isActive()) {
                                 liveServer = server.getIdentity();
                                 if (member.getLive() != null) {
                                    liveServer += "(notified)";
                                 } else {
                                    liveServer += "(not notified)";
                                 }
                              } else {
                                 backupServer = server.getIdentity();
                                 if (member.getBackup() != null) {
                                    liveServer += "(notified)";
                                 } else {
                                    liveServer += "(not notified)";
                                 }
                              }
                           }
                        }

                        topologyDiagram.append("\t").append("|\n").append("\t->").append(liveServer).append("/").append(backupServer).append("\n");
                     }
                  } else {
                     topologyDiagram.append("-> no cluster connections\n");
                  }
               } else {
                  topologyDiagram.append("-> stopped\n");
               }
            }
         }
         topologyDiagram.append("\n");
         log.debug(topologyDiagram.toString());
      } catch (Throwable e) {
         log.warn("error printing the topology::" + e.getMessage(), e);
      }
   }

   protected void waitForMessages(final int node, final String address, final int count) throws Exception {
      ActiveMQServer server = servers[node];

      if (server == null) {
         throw new IllegalArgumentException("No server at " + node);
      }

      PostOffice po = server.getPostOffice();

      long start = System.currentTimeMillis();

      int messageCount = 0;

      do {
         messageCount = getMessageCount(po, address);

         if (messageCount == count) {
            return;
         }

         Thread.sleep(10);
      }
      while (System.currentTimeMillis() - start < ActiveMQTestBase.WAIT_TIMEOUT);

      throw new IllegalStateException("Timed out waiting for messages (messageCount = " + messageCount +
                                         ", expecting = " +
                                         count);
   }

   protected void waitForServerRestart(final int node) throws Exception {
      long waitTimeout = ActiveMQTestBase.WAIT_TIMEOUT;
      if (!isSharedStore()) {
         //it should be greater than
         //QuorumManager.WAIT_TIME_AFTER_FIRST_LIVE_STOPPING_MSG (60 sec)
         waitTimeout = 1000 * (SharedNothingBackupQuorum.WAIT_TIME_AFTER_FIRST_LIVE_STOPPING_MSG + 5);
      }
      if (!servers[node].waitForActivation(waitTimeout, TimeUnit.MILLISECONDS)) {
         String msg = "Timed out waiting for server starting = " + node;

         log.error(msg);

         throw new IllegalStateException(msg);
      }
   }

   protected void waitForBindings(final int node,
                                  final String address,
                                  final int expectedBindingCount,
                                  final int expectedConsumerCount,
                                  final boolean local) throws Exception {
      log.debug("waiting for bindings on node " + node +
                   " address " +
                   address +
                   " expectedBindingCount " +
                   expectedBindingCount +
                   " consumerCount " +
                   expectedConsumerCount +
                   " local " +
                   local);

      ActiveMQServer server = servers[node];

      if (server == null) {
         throw new IllegalArgumentException("No server at " + node);
      }

      long timeout = ActiveMQTestBase.WAIT_TIMEOUT;

      if (waitForBindings(server, address, local, expectedBindingCount, expectedConsumerCount, timeout)) {
         return;
      }

      PostOffice po = server.getPostOffice();
      Bindings bindings = po.getBindingsForAddress(new SimpleString(address));

      log.debug("=======================================================================");
      log.debug("Binding information for address = " + address + " on node " + node);

      for (Binding binding : bindings.getBindings()) {
         if (binding.isConnected() && (binding instanceof LocalQueueBinding && local || binding instanceof RemoteQueueBinding && !local)) {
            QueueBinding qBinding = (QueueBinding) binding;

            log.debug("Binding = " + qBinding + ", queue=" + qBinding.getQueue());
         }
      }

      StringWriter writer = new StringWriter();
      PrintWriter out = new PrintWriter(writer);

      try {
         for (ActiveMQServer activeMQServer : servers) {
            if (activeMQServer != null) {
               out.println(clusterDescription(activeMQServer));
               out.println(debugBindings(activeMQServer, activeMQServer.getConfiguration().getManagementNotificationAddress().toString()));
            }
         }

         for (ActiveMQServer activeMQServer : servers) {
            out.println("Management bindings on " + activeMQServer);
            if (activeMQServer != null) {
               out.println(debugBindings(activeMQServer, activeMQServer.getConfiguration().getManagementNotificationAddress().toString()));
            }
         }
      } catch (Throwable dontCare) {
      }

      logAndSystemOut(writer.toString());

      throw new IllegalStateException("Didn't get the expected number of bindings, look at the logging for more information");
   }

   protected String debugBindings(final ActiveMQServer server, final String address) throws Exception {

      StringWriter str = new StringWriter();
      PrintWriter out = new PrintWriter(str);

      if (server == null) {
         return "server is shutdown";
      }
      PostOffice po = server.getPostOffice();

      if (po == null) {
         return "server is shutdown";
      }
      Bindings bindings = po.getBindingsForAddress(new SimpleString(address));

      out.println("=======================================================================");
      out.println("Binding information for address = " + address + " on " + server);

      for (Binding binding : bindings.getBindings()) {
         QueueBinding qBinding = (QueueBinding) binding;

         out.println("Binding = " + qBinding + ", queue=" + qBinding.getQueue());
      }
      out.println("=======================================================================");

      return str.toString();

   }

   protected void createQueue(final int node,
                              final String address,
                              final String queueName,
                              final String filterVal,
                              final boolean durable) throws Exception {
      createQueue(node, address, queueName, filterVal, durable, null, null);
   }

   protected void createQueue(final int node,
                              final String address,
                              final String queueName,
                              final String filterVal,
                              final boolean durable,
                              RoutingType routingType) throws Exception {
      createQueue(node, address, queueName, filterVal, durable, null, null, routingType);
   }

   protected void createQueue(final int node,
                              final String address,
                              final String queueName,
                              final String filterVal,
                              final boolean durable,
                              final String user,
                              final String password) throws Exception {
      createQueue(node, address, queueName, filterVal, durable, user, password, RoutingType.MULTICAST);
   }

   protected void createQueue(final int node,
                              final String address,
                              final String queueName,
                              final String filterVal,
                              final boolean durable,
                              final String user,
                              final String password,
                              RoutingType routingType) throws Exception {
      ClientSessionFactory sf = sfs[node];

      if (sf == null) {
         throw new IllegalArgumentException("No sf at " + node);
      }

      ClientSession session = addClientSession(sf.createSession(user, password, false, true, true, ActiveMQClient.DEFAULT_PRE_ACKNOWLEDGE, ActiveMQClient.DEFAULT_ACK_BATCH_SIZE));

      String filterString = null;

      if (filterVal != null) {
         filterString = ClusterTestBase.FILTER_PROP.toString() + "='" + filterVal + "'";
      }

      log.debug("Creating " + queueName + " , address " + address + " on " + servers[node]);

      session.createQueue(new QueueConfiguration(queueName).setAddress(address).setRoutingType(routingType).setFilterString(filterString).setDurable(durable));

      session.close();
   }

   protected void createAddressInfo(final int node,
                                    final String address,
                                    final RoutingType routingType,
                                    final int defaulMaxConsumers,
                                    boolean defaultPurgeOnNoConsumers) throws Exception {
      AddressInfo addressInfo = new AddressInfo(new SimpleString(address));
      addressInfo.addRoutingType(routingType);
      servers[node].addOrUpdateAddressInfo(addressInfo);
   }

   protected void deleteQueue(final int node, final String queueName) throws Exception {
      ClientSessionFactory sf = sfs[node];

      if (sf == null) {
         throw new IllegalArgumentException("No sf at " + node);
      }

      ClientSession session = sf.createSession(false, true, true);

      session.deleteQueue(queueName);

      session.close();
   }

   protected void addConsumer(final int consumerID,
                              final int node,
                              final String queueName,
                              final String filterVal) throws Exception {
      addConsumer(consumerID, node, queueName, filterVal, true);
   }

   protected void addConsumer(final int consumerID,
                              final int node,
                              final String queueName,
                              final String filterVal,
                              boolean autoCommitAcks) throws Exception {
      addConsumer(consumerID, node, queueName, filterVal, autoCommitAcks, null, null);
   }

   protected void addConsumer(final int consumerID,
                              final int node,
                              final String queueName,
                              final String filterVal,
                              boolean autoCommitAcks,
                              final String user,
                              final String password) throws Exception {
      try {
         if (consumers[consumerID] != null) {
            throw new IllegalArgumentException("Already a consumer at " + node);
         }

         ClientSessionFactory sf = sfs[node];

         if (sf == null) {
            throw new IllegalArgumentException("No sf at " + node);
         }

         ClientSession session = addClientSession(sf.createSession(user, password, false, false, autoCommitAcks, ActiveMQClient.DEFAULT_PRE_ACKNOWLEDGE, ActiveMQClient.DEFAULT_ACK_BATCH_SIZE));

         String filterString = null;

         if (filterVal != null) {
            filterString = ClusterTestBase.FILTER_PROP.toString() + "='" + filterVal + "'";
         }

         ClientConsumer consumer = addClientConsumer(session.createConsumer(queueName, filterString));

         session.start();

         consumers[consumerID] = new ConsumerHolder(consumerID, consumer, session, node);
      } catch (Exception e) {
         // Proxy the failure and print a dump into System.out, so it is captured by Jenkins reports
         e.printStackTrace();
         System.out.println(ActiveMQTestBase.threadDump(" - fired by ClusterTestBase::addConsumer"));

         throw e;
      }
   }

   protected void removeConsumer(final int consumerID) {
      ConsumerHolder holder = consumers[consumerID];

      if (holder == null) {
         throw new IllegalArgumentException("No consumer at " + consumerID);
      }

      holder.close();

      consumers[consumerID] = null;
   }

   protected void closeAllConsumers() {
      if (consumers == null)
         return;
      for (int i = 0; i < consumers.length; i++) {
         ConsumerHolder holder = consumers[i];

         if (holder != null) {
            holder.close();
            consumers[i] = null;
         }
      }
   }

   @Override
   protected void closeAllSessionFactories() {
      if (sfs != null) {
         for (int i = 0; i < sfs.length; i++) {
            closeSessionFactory(sfs[i]);
            sfs[i] = null;
         }
      }
      super.closeAllSessionFactories();
   }

   @Override
   protected void closeAllServerLocatorsFactories() {
      for (int i = 0; i < locators.length; i++) {
         closeServerLocator(locators[i]);
         locators[i] = null;
      }
      super.closeAllServerLocatorsFactories();
   }

   protected void closeSessionFactory(final int node) {
      ClientSessionFactory sf = sfs[node];

      if (sf == null) {
         throw new IllegalArgumentException("No sf at " + node);
      }

      sf.close();

      sfs[node] = null;
   }

   protected void sendInRange(final int node,
                              final String address,
                              final int msgStart,
                              final int msgEnd,
                              final boolean durable,
                              final String filterVal) throws Exception {
      sendInRange(node, address, msgStart, msgEnd, durable, filterVal, null);
   }

   protected void sendInRange(final int node,
                              final String address,
                              final int msgStart,
                              final int msgEnd,
                              final boolean durable,
                              final String filterVal,
                              final AtomicInteger duplicateDetectionSeq) throws Exception {
      ClientSessionFactory sf = sfs[node];

      if (sf == null) {
         throw new IllegalArgumentException("No sf at " + node);
      }

      ClientSession session = sf.createSession(false, false, false);

      try {
         ClientProducer producer = session.createProducer(address);

         for (int i = msgStart; i < msgEnd; i++) {
            ClientMessage message = session.createMessage(durable);

            if (filterVal != null) {
               message.putStringProperty(ClusterTestBase.FILTER_PROP, new SimpleString(filterVal));
            }

            if (duplicateDetectionSeq != null) {
               String str = Integer.toString(duplicateDetectionSeq.incrementAndGet());
               message.putStringProperty(Message.HDR_DUPLICATE_DETECTION_ID, new SimpleString(str));
            }

            message.putIntProperty(ClusterTestBase.COUNT_PROP, i);

            if (isLargeMessage()) {
               message.setBodyInputStream(createFakeLargeStream(getLargeMessageSize()));
            }

            producer.send(message);

            if (i % 100 == 0) {
               session.commit();
            }
         }

         session.commit();
      } finally {
         session.close();
      }
   }

   protected void sendWithProperty(final int node,
                                   final String address,
                                   final int numMessages,
                                   final boolean durable,
                                   final SimpleString key,
                                   final SimpleString val) throws Exception {
      sendInRange(node, address, 0, numMessages, durable, key, val);
   }

   protected void sendInRange(final int node,
                              final String address,
                              final int msgStart,
                              final int msgEnd,
                              final boolean durable,
                              final SimpleString key,
                              final SimpleString val) throws Exception {
      ClientSessionFactory sf = sfs[node];

      if (sf == null) {
         throw new IllegalArgumentException("No sf at " + node);
      }

      ClientSession session = sf.createSession(false, true, true);

      try {
         ClientProducer producer = session.createProducer(address);

         for (int i = msgStart; i < msgEnd; i++) {
            ClientMessage message = session.createMessage(durable);

            if (isLargeMessage()) {
               message.setBodyInputStream(createFakeLargeStream(getLargeMessageSize()));
            }

            message.putStringProperty(key, val);
            message.putIntProperty(ClusterTestBase.COUNT_PROP, i);

            producer.send(message);
         }
      } finally {
         session.close();
      }
   }

   protected void setUpGroupHandler(final GroupingHandlerConfiguration.TYPE type, final int node) {
      setUpGroupHandler(type, node, 5000);
   }

   protected void setUpGroupHandler(final GroupingHandlerConfiguration.TYPE type, final int node, final int timeout) {
      setUpGroupHandler(type, node, timeout, -1, ActiveMQDefaultConfiguration.getDefaultGroupingHandlerReaperPeriod());
   }

   protected void setUpGroupHandler(final GroupingHandlerConfiguration.TYPE type,
                                    final int node,
                                    final int timeout,
                                    final long groupTimeout,
                                    final long reaperPeriod) {
      servers[node].getConfiguration().setGroupingHandlerConfiguration(new GroupingHandlerConfiguration().setName(new SimpleString("grouparbitrator")).setType(type).setAddress(new SimpleString("queues")).setTimeout(timeout).setGroupTimeout(groupTimeout).setReaperPeriod(reaperPeriod));
   }

   protected void setUpGroupHandler(final GroupingHandler groupingHandler, final int node) {
      servers[node].setGroupingHandler(groupingHandler);
   }

   protected void send(final int node,
                       final String address,
                       final int numMessages,
                       final boolean durable,
                       final String filterVal) throws Exception {
      send(node, address, numMessages, durable, filterVal, null);
   }

   protected void send(final int node,
                       final String address,
                       final int numMessages,
                       final boolean durable,
                       final String filterVal,
                       final AtomicInteger duplicateDetectionCounter) throws Exception {
      sendInRange(node, address, 0, numMessages, durable, filterVal, duplicateDetectionCounter);
   }

   protected void verifyReceiveAllInRange(final boolean ack,
                                          final int msgStart,
                                          final int msgEnd,
                                          final int... consumerIDs) throws Exception {
      verifyReceiveAllInRangeNotBefore(ack, -1, msgStart, msgEnd, consumerIDs);
   }

   protected void verifyReceiveAllInRange(final int msgStart,
                                          final int msgEnd,
                                          final int... consumerIDs) throws Exception {
      verifyReceiveAllInRangeNotBefore(false, -1, msgStart, msgEnd, consumerIDs);
   }

   protected void verifyReceiveAllWithGroupIDRoundRobin(final int msgStart,
                                                        final int msgEnd,
                                                        final int... consumerIDs) throws Exception {
      verifyReceiveAllWithGroupIDRoundRobin(true, -1, msgStart, msgEnd, consumerIDs);
   }

   protected int verifyReceiveAllOnSingleConsumer(final int msgStart,
                                                  final int msgEnd,
                                                  final int... consumerIDs) throws Exception {
      return verifyReceiveAllOnSingleConsumer(true, msgStart, msgEnd, consumerIDs);
   }

   protected void verifyReceiveAllWithGroupIDRoundRobin(final boolean ack,
                                                        final long firstReceiveTime,
                                                        final int msgStart,
                                                        final int msgEnd,
                                                        final int... consumerIDs) throws Exception {
      HashMap<SimpleString, Integer> groupIdsReceived = new HashMap<>();
      for (int i = 0; i < consumerIDs.length; i++) {
         ConsumerHolder holder = consumers[consumerIDs[i]];

         if (holder == null) {
            throw new IllegalArgumentException("No consumer at " + consumerIDs[i]);
         }

         for (int j = msgStart; j < msgEnd; j++) {
            ClientMessage message = holder.consumer.receive(2000);

            if (message == null) {
               log.debug("*** dumping consumers:");

               dumpConsumers();

               Assert.assertNotNull("consumer " + consumerIDs[i] + " did not receive message " + j, message);
            }

            if (ack) {
               message.acknowledge();
            }

            if (firstReceiveTime != -1) {
               Assert.assertTrue("Message received too soon", System.currentTimeMillis() >= firstReceiveTime);
            }

            SimpleString id = (SimpleString) message.getObjectProperty(Message.HDR_GROUP_ID);

            if (groupIdsReceived.get(id) == null) {
               groupIdsReceived.put(id, i);
            } else if (groupIdsReceived.get(id) != i) {
               Assert.fail("consumer " + groupIdsReceived.get(id) +
                              " already bound to groupid " +
                              id +
                              " received on consumer " +
                              i);
            }

         }

      }

   }

   protected int verifyReceiveAllOnSingleConsumer(final boolean ack,
                                                  final int msgStart,
                                                  final int msgEnd,
                                                  final int... consumerIDs) throws Exception {
      int groupIdsReceived = -1;
      for (int i = 0; i < consumerIDs.length; i++) {
         ConsumerHolder holder = consumers[consumerIDs[i]];

         if (holder == null) {
            throw new IllegalArgumentException("No consumer at " + consumerIDs[i]);
         }
         ClientMessage message = holder.consumer.receive(2000);
         if (message != null) {
            groupIdsReceived = i;
            for (int j = msgStart + 1; j < msgEnd; j++) {
               message = holder.consumer.receive(2000);

               if (message == null) {
                  Assert.fail("consumer " + i + " did not receive all messages");
               }

               if (ack) {
                  message.acknowledge();
               }
            }
         }

      }
      return groupIdsReceived;

   }

   protected void verifyReceiveAllInRangeNotBefore(final boolean ack,
                                                   final long firstReceiveTime,
                                                   final int msgStart,
                                                   final int msgEnd,
                                                   final int... consumerIDs) throws Exception {
      boolean outOfOrder = false;
      String firstOutOfOrderMessage = null;
      for (int consumerID : consumerIDs) {
         ConsumerHolder holder = consumers[consumerID];

         if (holder == null) {
            throw new IllegalArgumentException("No consumer at " + consumerID);
         }

         for (int j = msgStart; j < msgEnd; j++) {

            ClientMessage message = holder.consumer.receive(WAIT_TIMEOUT);

            if (message == null) {
               log.debug("*** dumping consumers:");

               dumpConsumers();

               Assert.fail("consumer " + consumerID + " did not receive message " + j);
            }

            if (isLargeMessage()) {
               checkMessageBody(message);
            }

            if (ack) {
               message.acknowledge();
            }

            if (firstReceiveTime != -1) {
               Assert.assertTrue("Message received too soon", System.currentTimeMillis() >= firstReceiveTime);
            }

            if (j != (Integer) message.getObjectProperty(ClusterTestBase.COUNT_PROP)) {
               if (firstOutOfOrderMessage == null) {
                  firstOutOfOrderMessage = "expected " + j +
                     " received " +
                     message.getObjectProperty(ClusterTestBase.COUNT_PROP);
               }
               outOfOrder = true;
               log.debug("Message j=" + j + " was received out of order = " +
                                     message.getObjectProperty(ClusterTestBase.COUNT_PROP));
               log.debug("Message j=" + j +
                           " was received out of order = " +
                           message.getObjectProperty(ClusterTestBase.COUNT_PROP));
            }
         }
      }

      Assert.assertFalse("Messages were consumed out of order::" + firstOutOfOrderMessage, outOfOrder);
   }

   private void dumpConsumers() throws Exception {
      for (int i = 0; i < consumers.length; i++) {
         if (consumers[i] != null && !consumers[i].consumer.isClosed()) {
            log.debug("Dumping consumer " + i);

            checkReceive(i);
         }
      }
   }

   protected String clusterDescription(ActiveMQServer server) {
      String br = "-------------------------\n";
      String out = br;
      out += "ActiveMQ Artemis server " + server + "\n";
      ClusterManager clusterManager = server.getClusterManager();
      if (clusterManager == null) {
         out += "N/A";
      } else {
         for (ClusterConnection cc : clusterManager.getClusterConnections()) {
            out += cc.describe() + "\n";
            out += cc.getTopology().describe();
         }
      }
      out += "\n\nfull topology:";
      return out + br;
   }

   protected void verifyReceiveAll(final boolean ack,
                                   final int numMessages,
                                   final int... consumerIDs) throws Exception {
      verifyReceiveAllInRange(ack, 0, numMessages, consumerIDs);
   }

   protected void verifyReceiveAll(final int numMessages, final int... consumerIDs) throws Exception {
      verifyReceiveAllInRange(false, 0, numMessages, consumerIDs);
   }

   protected void verifyReceiveAllNotBefore(final long firstReceiveTime,
                                            final int numMessages,
                                            final int... consumerIDs) throws Exception {
      verifyReceiveAllInRangeNotBefore(false, firstReceiveTime, 0, numMessages, consumerIDs);
   }

   protected void checkReceive(final int... consumerIDs) throws Exception {
      for (int consumerID : consumerIDs) {
         ConsumerHolder holder = consumers[consumerID];

         if (holder == null) {
            throw new IllegalArgumentException("No consumer at " + consumerID);
         }

         ClientMessage message;
         do {
            message = holder.consumer.receive(500);

            if (message != null) {
               log.debug("check receive Consumer " + consumerID +
                           " received message " +
                           message.getObjectProperty(ClusterTestBase.COUNT_PROP));
            } else {
               log.debug("check receive Consumer " + consumerID + " null message");
            }
         }
         while (message != null);

      }
   }

   protected void verifyReceiveRoundRobin(final int numMessages, final int... consumerIDs) throws Exception {
      int count = 0;

      for (int i = 0; i < numMessages; i++) {
         // We may use a negative number in some tests to ignore the consumer, case we know the server is down
         if (consumerIDs[count] >= 0) {
            ConsumerHolder holder = consumers[consumerIDs[count]];

            if (holder == null) {
               throw new IllegalArgumentException("No consumer at " + consumerIDs[i]);
            }

            ClientMessage message = holder.consumer.receive(WAIT_TIMEOUT);

            Assert.assertNotNull("consumer " + consumerIDs[count] + " did not receive message " + i, message);

            Assert.assertEquals("consumer " + consumerIDs[count] + " message " + i, i, message.getObjectProperty(ClusterTestBase.COUNT_PROP));

            message.acknowledge();

            consumers[consumerIDs[count]].session.commit();

         }

         count++;

         if (count == consumerIDs.length) {
            count = 0;
         }
      }
   }

   /*
    * With some tests we cannot guarantee the order in which the bridges in the cluster startup so the round robin order is not predefined.
    * In which case we test the messages are round robin'd in any specific order that contains all the consumers
    */
   protected void verifyReceiveRoundRobinInSomeOrder(final int numMessages, final int... consumerIDs) throws Exception {
      if (numMessages < consumerIDs.length) {
         throw new IllegalStateException("You must send more messages than consumers specified or the algorithm " + "won't work");
      }

      verifyReceiveRoundRobinInSomeOrder(true, numMessages, consumerIDs);
   }

   class OrderedConsumerHolder implements Comparable<OrderedConsumerHolder> {

      ConsumerHolder consumer;

      int order;

      @Override
      public int compareTo(final OrderedConsumerHolder o) {
         int thisOrder = order;
         int otherOrder = o.order;
         return Integer.compare(thisOrder, otherOrder);
      }
   }

   protected void verifyReceiveRoundRobinInSomeOrder(final boolean ack,
                                                     final int numMessages,
                                                     final int... consumerIDs) throws Exception {
      if (numMessages < consumerIDs.length) {
         throw new IllegalStateException("not enough messages");
      }

      // First get one from each consumer to determine the order, then we sort them in this order

      List<OrderedConsumerHolder> sorted = new ArrayList<>();

      for (int consumerID : consumerIDs) {
         ConsumerHolder holder = consumers[consumerID];

         ClientMessage msg = holder.consumer.receive(10000);

         Assert.assertNotNull("msg must exist", msg);

         int count = msg.getIntProperty(ClusterTestBase.COUNT_PROP);

         OrderedConsumerHolder orderedHolder = new OrderedConsumerHolder();

         orderedHolder.consumer = holder;
         orderedHolder.order = count;

         sorted.add(orderedHolder);

         if (ack) {
            msg.acknowledge();
         }
      }

      // Now sort them

      Collections.sort(sorted);

      // First verify the first lot received are ok

      int count = 0;

      for (OrderedConsumerHolder holder : sorted) {
         if (holder.order != count) {
            throw new IllegalStateException("Out of order");
         }

         count++;
      }

      // Now check the rest are in order too

   outer:
      while (count < numMessages) {
         for (OrderedConsumerHolder holder : sorted) {
            ClientMessage msg = holder.consumer.consumer.receive(10000);

            Assert.assertNotNull("msg must exist", msg);

            int p = msg.getIntProperty(ClusterTestBase.COUNT_PROP);

            if (p != count) {
               throw new IllegalStateException("Out of order 2");
            }

            if (ack) {
               msg.acknowledge();
            }

            count++;

            if (count == numMessages) {
               break outer;
            }

         }
      }
   }

   protected void verifyReceiveRoundRobinInSomeOrderWithCounts(final boolean ack,
                                                               final int[] messageCounts,
                                                               final int... consumerIDs) throws Exception {
      List<LinkedList<Integer>> receivedCounts = new ArrayList<>();

      Set<Integer> counts = new HashSet<>();

      for (int consumerID : consumerIDs) {
         ConsumerHolder holder = consumers[consumerID];

         if (holder == null) {
            throw new IllegalArgumentException("No consumer at " + consumerID);
         }

         LinkedList<Integer> list = new LinkedList<>();

         receivedCounts.add(list);

         ClientMessage message;
         do {
            message = holder.consumer.receive(1000);

            if (message != null) {
               int count = (Integer) message.getObjectProperty(ClusterTestBase.COUNT_PROP);

               checkMessageBody(message);

               // log.debug("consumer " + consumerIDs[i] + " received message " + count);

               Assert.assertFalse(counts.contains(count));

               counts.add(count);

               list.add(count);

               if (ack) {
                  message.acknowledge();
               }
            }
         }
         while (message != null);
      }

      for (int messageCount : messageCounts) {
         Assert.assertTrue(counts.contains(messageCount));
      }

      @SuppressWarnings("unchecked")
      LinkedList<Integer>[] lists = new LinkedList[consumerIDs.length];

      for (int i = 0; i < messageCounts.length; i++) {
         for (LinkedList<Integer> list : receivedCounts) {
            int elem = list.get(0);

            if (elem == messageCounts[i]) {
               lists[i] = list;

               break;
            }
         }
      }
      int index = 0;

      for (int messageCount : messageCounts) {
         LinkedList<Integer> list = lists[index];

         Assert.assertNotNull(list);

         int elem = list.poll();

         Assert.assertEquals(messageCount, elem);

         index++;

         if (index == consumerIDs.length) {
            index = 0;
         }
      }

   }

   /**
    * @param message
    */
   private void checkMessageBody(ClientMessage message) {
      if (isLargeMessage()) {
         for (int posMsg = 0; posMsg < getLargeMessageSize(); posMsg++) {
            assertEquals(getSamplebyte(posMsg), message.getBodyBuffer().readByte());
         }
      }
   }

   protected void verifyReceiveRoundRobinInSomeOrderNoAck(final int numMessages,
                                                          final int... consumerIDs) throws Exception {
      if (numMessages < consumerIDs.length) {
         throw new IllegalStateException("You must send more messages than consumers specified or the algorithm " + "won't work");
      }

      verifyReceiveRoundRobinInSomeOrder(false, numMessages, consumerIDs);
   }

   protected void verifyClusterMetrics(final int node, final String clusterName, final long expectedMessagesPendingAcknowledgement,
         final long expectedMessagesAcknowledged) {
      final ClusterConnection clusterConnection = servers[node].getClusterManager().getClusterConnection(clusterName);
      final ClusterConnectionMetrics clusterMetrics = clusterConnection.getMetrics();
      assertEquals(expectedMessagesPendingAcknowledgement, clusterMetrics.getMessagesPendingAcknowledgement());
      assertEquals(expectedMessagesAcknowledged, clusterMetrics.getMessagesAcknowledged());
   }

   protected void verifyBridgeMetrics(final int node, final String clusterName, final String bridgeNodeId,
         final long expectedMessagesPendingAcknowledgement, final long expectedMessagesAcknowledged) {
      final ClusterConnection clusterConnection = servers[node].getClusterManager().getClusterConnection(clusterName);
      final BridgeMetrics bridgeMetrics = clusterConnection.getBridgeMetrics(bridgeNodeId);
      assertEquals(expectedMessagesPendingAcknowledgement, bridgeMetrics.getMessagesPendingAcknowledgement());
      assertEquals(expectedMessagesAcknowledged, bridgeMetrics.getMessagesAcknowledged());
   }

   protected int[] getReceivedOrder(final int consumerID) throws Exception {
      return getReceivedOrder(consumerID, false);
   }

   protected int[] getReceivedOrder(final int consumerID, final boolean ack) throws Exception {
      ConsumerHolder consumer = consumers[consumerID];

      if (consumer == null) {
         throw new IllegalArgumentException("No consumer at " + consumerID);
      }

      List<Integer> ints = new ArrayList<>();

      ClientMessage message = null;

      do {
         message = consumer.consumer.receive(500);
         if (message != null) {

            if (isLargeMessage()) {
               checkMessageBody(message);
            }

            if (ack) {
               message.acknowledge();
            }

            int count = (Integer) message.getObjectProperty(ClusterTestBase.COUNT_PROP);

            ints.add(count);
         }
      }
      while (message != null);

      int[] res = new int[ints.size()];

      int j = 0;

      for (Integer i : ints) {
         res[j++] = i;
      }

      if (ack) {
         // just to flush acks
         consumers[consumerID].session.commit();
      }

      return res;
   }

   protected void verifyNotReceive(final int... consumerIDs) throws Exception {
      for (int i = 0; i < consumerIDs.length; i++) {
         ConsumerHolder holder = consumers[consumerIDs[i]];

         if (holder == null) {
            throw new IllegalArgumentException("No consumer at " + consumerIDs[i]);
         }

         Assert.assertNull("consumer " + i + " received message", holder.consumer.receiveImmediate());
      }
   }

   protected void setupSessionFactory(final int node, final boolean netty) throws Exception {
      setupSessionFactory(node, netty, false);
   }

   protected void setupSessionFactory(final int node, final boolean netty, boolean ha) throws Exception {
      setupSessionFactory(node, netty, ha, null, null);
   }

   protected void setupSessionFactory(final int node,
                                      final boolean netty,
                                      boolean ha,
                                      final String user,
                                      final String password) throws Exception {
      if (sfs[node] != null) {
         throw new IllegalArgumentException("Already a factory at " + node);
      }

      Map<String, Object> params = generateParams(node, netty);

      TransportConfiguration serverTotc;

      if (netty) {
         serverTotc = new TransportConfiguration(ActiveMQTestBase.NETTY_CONNECTOR_FACTORY, params);
      } else {
         serverTotc = new TransportConfiguration(INVM_CONNECTOR_FACTORY, params);
      }

      setSessionFactoryCreateLocator(node, ha, serverTotc);

      locators[node].setProtocolManagerFactory(ActiveMQServerSideProtocolManagerFactory.getInstance(locators[node], servers[node].getStorageManager()));

      locators[node].setBlockOnNonDurableSend(true).setBlockOnDurableSend(true);
      addServerLocator(locators[node]);
      ClientSessionFactory sf = createSessionFactory(locators[node]);

      ClientSession session = sf.createSession(user, password, false, true, true, ActiveMQClient.DEFAULT_PRE_ACKNOWLEDGE, ActiveMQClient.DEFAULT_ACK_BATCH_SIZE);
      session.close();
      sfs[node] = sf;
   }

   protected void setSessionFactoryCreateLocator(int node, boolean ha, TransportConfiguration serverTotc) {
      if (ha) {
         locators[node] = ActiveMQClient.createServerLocatorWithHA(serverTotc);
      } else {
         locators[node] = ActiveMQClient.createServerLocatorWithoutHA(serverTotc);
      }
   }

   protected void setupSessionFactory(final int node, final boolean netty, int reconnectAttempts) throws Exception {
      if (sfs[node] != null) {
         throw new IllegalArgumentException("Already a server at " + node);
      }

      Map<String, Object> params = generateParams(node, netty);

      TransportConfiguration serverTotc;

      if (netty) {
         serverTotc = new TransportConfiguration(ActiveMQTestBase.NETTY_CONNECTOR_FACTORY, params);
      } else {
         serverTotc = new TransportConfiguration(INVM_CONNECTOR_FACTORY, params);
      }

      locators[node] = ActiveMQClient.createServerLocatorWithoutHA(serverTotc).setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setReconnectAttempts(reconnectAttempts);

      addServerLocator(locators[node]);
      ClientSessionFactory sf = createSessionFactory(locators[node]);

      sfs[node] = sf;
   }

   protected void setupSessionFactory(final int node,
                                      final int backupNode,
                                      final boolean netty,
                                      final boolean blocking) throws Exception {
      if (sfs[node] != null) {
         throw new IllegalArgumentException("Already a server at " + node);
      }

      Map<String, Object> params = generateParams(node, netty);

      TransportConfiguration serverToTC = createTransportConfiguration(netty, false, params);

      locators[node] = addServerLocator(ActiveMQClient.createServerLocatorWithHA(serverToTC)).setRetryInterval(100).setRetryIntervalMultiplier(1d).setReconnectAttempts(300).setBlockOnNonDurableSend(blocking).setBlockOnDurableSend(blocking);

      final String identity = "TestClientConnector,live=" + node + ",backup=" + backupNode;
      ((ServerLocatorInternal) locators[node]).setIdentity(identity);

      ClientSessionFactory sf = createSessionFactory(locators[node]);
      sfs[node] = sf;
   }

   protected void setupSessionFactory(final int node, final int backupNode, final boolean netty) throws Exception {
      this.setupSessionFactory(node, backupNode, netty, true);
   }

   protected ActiveMQServer getServer(final int node) {
      if (servers[node] == null) {
         throw new IllegalArgumentException("No server at node " + node);
      }

      return servers[node];
   }

   protected void setupServer(final int node, final boolean fileStorage, final boolean netty) throws Exception {
      setupLiveServer(node, fileStorage, HAType.SharedNothingReplication, netty, false);
   }

   protected void setupLiveServer(final int node,
                                  final boolean fileStorage,
                                  final boolean netty,
                                  boolean isLive) throws Exception {
      setupLiveServer(node, fileStorage, HAType.SharedNothingReplication, netty, isLive);
   }

   protected boolean isResolveProtocols() {
      return false;
   }

   protected void setupLiveServer(final int node,
                                  final boolean fileStorage,
                                  final HAType haType,
                                  final boolean netty,
                                  boolean liveOnly) throws Exception {
      if (servers[node] != null) {
         throw new IllegalArgumentException("Already a server at node " + node);
      }

      final HAPolicyConfiguration haPolicyConfiguration;
      if (liveOnly) {
         haPolicyConfiguration = new LiveOnlyPolicyConfiguration();
      } else {
         haPolicyConfiguration = haPolicyLiveConfiguration(haType);
      }

      Configuration configuration = createBasicConfig(node).setJournalMaxIO_AIO(1000).setThreadPoolMaxSize(10).clearAcceptorConfigurations().addAcceptorConfiguration(createTransportConfiguration(netty, true, generateParams(node, netty))).setHAPolicyConfiguration(haPolicyConfiguration).setResolveProtocols(isResolveProtocols());

      ActiveMQServer server;

      final boolean sharedStorage = HAType.SharedStore.equals(haType);

      if (fileStorage) {
         if (sharedStorage) {
            server = createInVMFailoverServer(true, configuration, nodeManagers[node], node);
         } else {
            server = createServer(configuration);
         }
      } else {
         if (sharedStorage) {
            server = createInVMFailoverServer(false, configuration, nodeManagers[node], node);
         } else {
            server = createServer(false, configuration);
         }
      }

      server.addProtocolManagerFactory(new CoreProtocolManagerFactory());

      server.setIdentity(this.getClass().getSimpleName() + "/Live(" + node + ")");
      servers[node] = addServer(server);
   }

   private HAPolicyConfiguration haPolicyLiveConfiguration(HAType haType) {
      switch (haType) {
         case SharedStore:
            return new SharedStoreMasterPolicyConfiguration();
         case SharedNothingReplication:
            return new ReplicatedPolicyConfiguration();
         case PluggableQuorumReplication:
            return ReplicationPrimaryPolicyConfiguration.withDefault()
               .setDistributedManagerConfiguration(getOrCreatePluggableQuorumConfiguration());
         default:
            throw new AssertionError("Unsupported haType = " + haType);
      }
   }

   /**
    * Server lacks a {@link ClusterConnectionConfiguration} necessary for the remote (replicating)
    * backup case.
    * <br>
    * Use
    * {@link #setupClusterConnectionWithBackups(String, String, org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType, int, boolean, int, int[])}
    * to add it.
    *
    * @param node
    * @param liveNode
    * @param fileStorage
    * @param haType
    * @param netty
    * @throws Exception
    */
   protected void setupBackupServer(final int node,
                                    final int liveNode,
                                    final boolean fileStorage,
                                    final HAType haType,
                                    final boolean netty) throws Exception {
      if (servers[node] != null) {
         throw new IllegalArgumentException("Already a server at node " + node);
      }

      TransportConfiguration liveConfig = createTransportConfiguration(netty, false, generateParams(liveNode, netty));
      TransportConfiguration backupConfig = createTransportConfiguration(netty, false, generateParams(node, netty));
      TransportConfiguration acceptorConfig = createTransportConfiguration(netty, true, generateParams(node, netty));

      final boolean sharedStorage = HAType.SharedStore.equals(haType);

      Configuration configuration = createBasicConfig(sharedStorage ? liveNode : node).clearAcceptorConfigurations().addAcceptorConfiguration(acceptorConfig).addConnectorConfiguration(liveConfig.getName(), liveConfig).addConnectorConfiguration(backupConfig.getName(), backupConfig).setHAPolicyConfiguration(haPolicyBackupConfiguration(haType));

      ActiveMQServer server;

      if (sharedStorage) {
         server = createInVMFailoverServer(true, configuration, nodeManagers[liveNode], liveNode);
      } else {
         boolean enablePersistency = fileStorage ? true : configuration.isPersistenceEnabled();
         server = addServer(ActiveMQServers.newActiveMQServer(configuration, enablePersistency));
      }
      server.setIdentity(this.getClass().getSimpleName() + "/Backup(" + node + " of live " + liveNode + ")");
      servers[node] = addServer(server);
   }

   private HAPolicyConfiguration haPolicyBackupConfiguration(HAType haType) {
      switch (haType) {

         case SharedStore:
            return new SharedStoreSlavePolicyConfiguration();
         case SharedNothingReplication:
            return new ReplicaPolicyConfiguration();
         case PluggableQuorumReplication:
            return ReplicationBackupPolicyConfiguration.withDefault()
               .setDistributedManagerConfiguration(getOrCreatePluggableQuorumConfiguration());
         default:
            throw new AssertionError("Unsupported ha type = " + haType);
      }
   }

   protected void setupLiveServerWithDiscovery(final int node,
                                               final String groupAddress,
                                               final int port,
                                               final boolean fileStorage,
                                               final boolean netty,
                                               final boolean sharedStorage) throws Exception {
      if (servers[node] != null) {
         throw new IllegalArgumentException("Already a server at node " + node);
      }

      Map<String, Object> params = generateParams(node, netty);

      TransportConfiguration connector = createTransportConfiguration(netty, false, params);

      List<String> connectorPairs = new ArrayList<>();
      connectorPairs.add(connector.getName());

      UDPBroadcastEndpointFactory endpoint = new UDPBroadcastEndpointFactory().setGroupAddress(groupAddress).setGroupPort(port);

      BroadcastGroupConfiguration bcConfig = new BroadcastGroupConfiguration().setName("bg1").setBroadcastPeriod(200).setConnectorInfos(connectorPairs).setEndpointFactory(endpoint);

      DiscoveryGroupConfiguration dcConfig = new DiscoveryGroupConfiguration().setName("dg1").setRefreshTimeout(1000).setDiscoveryInitialWaitTimeout(1000).setBroadcastEndpointFactory(endpoint);

      Configuration configuration = createBasicConfig(node).setJournalMaxIO_AIO(1000).clearAcceptorConfigurations().addAcceptorConfiguration(createTransportConfiguration(netty, true, params)).addConnectorConfiguration(connector.getName(), connector).addBroadcastGroupConfiguration(bcConfig).addDiscoveryGroupConfiguration(dcConfig.getName(), dcConfig).setHAPolicyConfiguration(sharedStorage ? new SharedStoreMasterPolicyConfiguration() : new ReplicatedPolicyConfiguration());

      ActiveMQServer server;
      if (fileStorage) {
         if (sharedStorage) {
            server = createInVMFailoverServer(true, configuration, nodeManagers[node], node);
         } else {
            server = addServer(ActiveMQServers.newActiveMQServer(configuration));
            server.setIdentity("Server " + node);
         }
      } else {
         if (sharedStorage) {
            server = createInVMFailoverServer(false, configuration, nodeManagers[node], node);
         } else {
            server = addServer(ActiveMQServers.newActiveMQServer(configuration, false));
            server.setIdentity("Server " + node);
         }
      }
      servers[node] = server;
   }

   protected void setupBackupServerWithDiscovery(final int node,
                                                 final int liveNode,
                                                 final String groupAddress,
                                                 final int port,
                                                 final boolean fileStorage,
                                                 final boolean netty,
                                                 final boolean sharedStorage) throws Exception {
      if (servers[node] != null) {
         throw new IllegalArgumentException("Already a server at node " + node);
      }

      Map<String, Object> params = generateParams(node, netty);

      TransportConfiguration connector = createTransportConfiguration(netty, false, params);

      List<String> connectorPairs = new ArrayList<>();
      connectorPairs.add(connector.getName());

      UDPBroadcastEndpointFactory endpoint = new UDPBroadcastEndpointFactory().setGroupAddress(groupAddress).setGroupPort(port);

      BroadcastGroupConfiguration bcConfig = new BroadcastGroupConfiguration().setName("bg1").setBroadcastPeriod(1000).setConnectorInfos(connectorPairs).setEndpointFactory(endpoint);

      DiscoveryGroupConfiguration dcConfig = new DiscoveryGroupConfiguration().setName("dg1").setRefreshTimeout(5000).setDiscoveryInitialWaitTimeout(5000).setBroadcastEndpointFactory(endpoint);

      Configuration configuration = createBasicConfig(sharedStorage ? liveNode : node).clearAcceptorConfigurations().addAcceptorConfiguration(createTransportConfiguration(netty, true, params)).addConnectorConfiguration(connector.getName(), connector).addBroadcastGroupConfiguration(bcConfig).addDiscoveryGroupConfiguration(dcConfig.getName(), dcConfig).setHAPolicyConfiguration(sharedStorage ? new SharedStoreSlavePolicyConfiguration() : new ReplicatedPolicyConfiguration());

      ActiveMQServer server;
      if (sharedStorage) {
         server = createInVMFailoverServer(fileStorage, configuration, nodeManagers[liveNode], liveNode);
      } else {
         boolean enablePersistency = fileStorage ? configuration.isPersistenceEnabled() : false;
         server = addServer(ActiveMQServers.newActiveMQServer(configuration, enablePersistency));
      }
      servers[node] = server;
   }

   protected void clearServer(final int... nodes) {
      for (int i = 0; i < nodes.length; i++) {
         if (servers[nodes[i]] == null) {
            throw new IllegalArgumentException("No server at node " + nodes[i]);
         }

         servers[nodes[i]] = null;
      }
   }

   protected void clearAllServers() {
      for (int i = 0; i < servers.length; i++) {
         servers[i] = null;
      }
   }

   protected void setupClusterConnection(final String name,
                                         final int nodeFrom,
                                         final int nodeTo,
                                         final String address,
                                         final MessageLoadBalancingType messageLoadBalancingType,
                                         final int maxHops,
                                         final boolean netty,
                                         final boolean allowDirectConnectionsOnly) {
      ActiveMQServer serverFrom = servers[nodeFrom];

      if (serverFrom == null) {
         throw new IllegalStateException("No server at node " + nodeFrom);
      }

      TransportConfiguration connectorFrom = createTransportConfiguration(netty, false, generateParams(nodeFrom, netty));
      serverFrom.getConfiguration().getConnectorConfigurations().put(name, connectorFrom);

      List<String> pairs = null;

      if (nodeTo != -1) {
         TransportConfiguration serverTotc = createTransportConfiguration(netty, false, generateParams(nodeTo, netty));
         serverFrom.getConfiguration().getConnectorConfigurations().put(serverTotc.getName(), serverTotc);
         pairs = new ArrayList<>();
         pairs.add(serverTotc.getName());
      }
      Configuration config = serverFrom.getConfiguration();

      ClusterConnectionConfiguration clusterConf = new ClusterConnectionConfiguration().setName(name).setAddress(address).setConnectorName(name).setRetryInterval(100).setMessageLoadBalancingType(messageLoadBalancingType).setMaxHops(maxHops).setConfirmationWindowSize(1024).setStaticConnectors(pairs).setAllowDirectConnectionsOnly(allowDirectConnectionsOnly);

      config.getClusterConfigurations().add(clusterConf);
   }

   protected void setupClusterConnection(final String name,
                                         final int nodeFrom,
                                         final int nodeTo,
                                         final String address,
                                         final MessageLoadBalancingType messageLoadBalancingType,
                                         final int maxHops,
                                         final int reconnectAttempts,
                                         final long retryInterval,
                                         final boolean netty,
                                         final boolean allowDirectConnectionsOnly) {
      ActiveMQServer serverFrom = servers[nodeFrom];

      if (serverFrom == null) {
         throw new IllegalStateException("No server at node " + nodeFrom);
      }

      TransportConfiguration connectorFrom = createTransportConfiguration(netty, false, generateParams(nodeFrom, netty));
      serverFrom.getConfiguration().getConnectorConfigurations().put(name, connectorFrom);

      List<String> pairs = null;

      if (nodeTo != -1) {
         TransportConfiguration serverTotc = createTransportConfiguration(netty, false, generateParams(nodeTo, netty));
         serverFrom.getConfiguration().getConnectorConfigurations().put(serverTotc.getName(), serverTotc);
         pairs = new ArrayList<>();
         pairs.add(serverTotc.getName());
      }
      Configuration config = serverFrom.getConfiguration();

      ClusterConnectionConfiguration clusterConf = new ClusterConnectionConfiguration().setName(name).setAddress(address).setConnectorName(name).setReconnectAttempts(reconnectAttempts).setRetryInterval(retryInterval).setMessageLoadBalancingType(messageLoadBalancingType).setMaxHops(maxHops).setConfirmationWindowSize(1024).setStaticConnectors(pairs).setAllowDirectConnectionsOnly(allowDirectConnectionsOnly);

      config.getClusterConfigurations().add(clusterConf);
   }

   protected void setupClusterConnection(final String name, final String uri, int server) throws Exception {
      ActiveMQServer serverFrom = servers[server];

      if (serverFrom == null) {
         throw new IllegalStateException("No server at node " + server);
      }

      ClusterConnectionConfiguration configuration = new ClusterConnectionConfiguration(new URI(uri)).setName(name);

      serverFrom.getConfiguration().addClusterConfiguration(configuration);

   }

   protected void setupClusterConnection(final String name,
                                         final String address,
                                         final MessageLoadBalancingType messageLoadBalancingType,
                                         final int maxHops,
                                         final boolean netty,
                                         final int nodeFrom,
                                         final int... nodesTo) {
      setupClusterConnection(name, address, messageLoadBalancingType, maxHops, netty, null, nodeFrom, nodesTo);
   }

   private List<String> getClusterConnectionTCNames(boolean netty, ActiveMQServer serverFrom, int[] nodesTo) {
      List<String> pairs = new ArrayList<>();
      for (int element : nodesTo) {
         TransportConfiguration serverTotc = createTransportConfiguration(netty, false, generateParams(element, netty));
         serverFrom.getConfiguration().getConnectorConfigurations().put(serverTotc.getName(), serverTotc);
         pairs.add(serverTotc.getName());
      }
      return pairs;
   }

   protected void setupClusterConnection(ClusterConnectionConfiguration clusterConf,
                                         final boolean netty,
                                         final int nodeFrom,
                                         final int... nodesTo) {
      ActiveMQServer serverFrom = servers[nodeFrom];

      if (serverFrom == null) {
         throw new IllegalStateException("No server at node " + nodeFrom);
      }

      TransportConfiguration connectorFrom = createTransportConfiguration(netty, false, generateParams(nodeFrom, netty));
      serverFrom.getConfiguration().getConnectorConfigurations().put(connectorFrom.getName(), connectorFrom);

      List<String> pairs = getClusterConnectionTCNames(netty, serverFrom, nodesTo);
      Configuration config = serverFrom.getConfiguration();
      clusterConf.setConnectorName(connectorFrom.getName()).setConfirmationWindowSize(1024).setStaticConnectors(pairs);

      config.getClusterConfigurations().add(clusterConf);
   }

   protected void setupClusterConnection(final String name,
                                         final String address,
                                         final MessageLoadBalancingType messageLoadBalancingType,
                                         final int maxHops,
                                         final boolean netty,
                                         final ClusterConfigCallback cb,
                                         final int nodeFrom,
                                         final int... nodesTo) {
      ActiveMQServer serverFrom = servers[nodeFrom];

      if (serverFrom == null) {
         throw new IllegalStateException("No server at node " + nodeFrom);
      }

      TransportConfiguration connectorFrom = createTransportConfiguration(netty, false, generateParams(nodeFrom, netty));
      serverFrom.getConfiguration().getConnectorConfigurations().put(connectorFrom.getName(), connectorFrom);

      List<String> pairs = getClusterConnectionTCNames(netty, serverFrom, nodesTo);
      Configuration config = serverFrom.getConfiguration();
      ClusterConnectionConfiguration clusterConf = createClusterConfig(name, address, messageLoadBalancingType, maxHops, connectorFrom, pairs);

      if (cb != null) {
         cb.configure(clusterConf);
      }
      config.getClusterConfigurations().add(clusterConf);
   }

   protected void setupClusterConnection(final String name,
                                         final String address,
                                         final MessageLoadBalancingType messageLoadBalancingType,
                                         final int maxHops,
                                         final int reconnectAttempts,
                                         final long retryInterval,
                                         final boolean netty,
                                         final int nodeFrom,
                                         final int... nodesTo) {
      ActiveMQServer serverFrom = servers[nodeFrom];

      if (serverFrom == null) {
         throw new IllegalStateException("No server at node " + nodeFrom);
      }

      TransportConfiguration connectorFrom = createTransportConfiguration(netty, false, generateParams(nodeFrom, netty));
      serverFrom.getConfiguration().getConnectorConfigurations().put(connectorFrom.getName(), connectorFrom);

      List<String> pairs = getClusterConnectionTCNames(netty, serverFrom, nodesTo);
      Configuration config = serverFrom.getConfiguration();

      ClusterConnectionConfiguration clusterConf = new ClusterConnectionConfiguration()
         .setName(name)
         .setAddress(address)
         .setConnectorName(connectorFrom.getName())
         .setRetryInterval(retryInterval)
         .setReconnectAttempts(reconnectAttempts)
         .setCallTimeout(100)
         .setCallFailoverTimeout(100)
         .setMessageLoadBalancingType(messageLoadBalancingType)
         .setMaxHops(maxHops)
         .setConfirmationWindowSize(1024)
         .setStaticConnectors(pairs);

      config.getClusterConfigurations().add(clusterConf);
   }

   private ClusterConnectionConfiguration createClusterConfig(final String name,
                                                              final String address,
                                                              final MessageLoadBalancingType messageLoadBalancingType,
                                                              final int maxHops,
                                                              TransportConfiguration connectorFrom,
                                                              List<String> pairs) {
      return new ClusterConnectionConfiguration()
         .setName(name)
         .setAddress(address)
         .setConnectorName(connectorFrom.getName())
         .setRetryInterval(250)
         .setMessageLoadBalancingType(messageLoadBalancingType)
         .setMaxHops(maxHops)
         .setConfirmationWindowSize(1024)
         .setStaticConnectors(pairs);
   }

   protected void setupClusterConnectionWithBackups(final String name,
                                                    final String address,
                                                    final MessageLoadBalancingType messageLoadBalancingType,
                                                    final int maxHops,
                                                    final boolean netty,
                                                    final int nodeFrom,
                                                    final int[] nodesTo) {
      ActiveMQServer serverFrom = servers[nodeFrom];

      if (serverFrom == null) {
         throw new IllegalStateException("No server at node " + nodeFrom);
      }

      TransportConfiguration connectorFrom = createTransportConfiguration(netty, false, generateParams(nodeFrom, netty));
      serverFrom.getConfiguration().getConnectorConfigurations().put(name, connectorFrom);

      List<String> pairs = getClusterConnectionTCNames(netty, serverFrom, nodesTo);
      Configuration config = serverFrom.getConfiguration();

      ClusterConnectionConfiguration clusterConf = new ClusterConnectionConfiguration().setName(name).setAddress(address).setConnectorName(name).setRetryInterval(250).setMessageLoadBalancingType(messageLoadBalancingType).setMaxHops(maxHops).setConfirmationWindowSize(1024).setStaticConnectors(pairs);

      config.getClusterConfigurations().add(clusterConf);
   }

   protected void setupDiscoveryClusterConnection(final String name,
                                                  final int node,
                                                  final String discoveryGroupName,
                                                  final String address,
                                                  final MessageLoadBalancingType messageLoadBalancingType,
                                                  final int maxHops,
                                                  final boolean netty) {
      ActiveMQServer server = servers[node];

      if (server == null) {
         throw new IllegalStateException("No server at node " + node);
      }

      TransportConfiguration connectorConfig = createTransportConfiguration(netty, false, generateParams(node, netty));
      server.getConfiguration().getConnectorConfigurations().put(name, connectorConfig);
      Configuration config = server.getConfiguration();
      ClusterConnectionConfiguration clusterConf = new ClusterConnectionConfiguration().setName(name).setAddress(address).setConnectorName(name).setRetryInterval(100).setDuplicateDetection(true).setMessageLoadBalancingType(messageLoadBalancingType).setMaxHops(maxHops).setConfirmationWindowSize(1024).setDiscoveryGroupName(discoveryGroupName);
      List<ClusterConnectionConfiguration> clusterConfs = config.getClusterConfigurations();

      clusterConfs.add(clusterConf);
   }

   protected void startServers(final int... nodes) throws Exception {
      for (int node : nodes) {
         log.debug("#test start node " + node);
         final long currentTime = System.currentTimeMillis();
         boolean waitForSelf = currentTime - timeStarts[node] < TIMEOUT_START_SERVER;
         boolean waitForPrevious = node > 0 && currentTime - timeStarts[node - 1] < TIMEOUT_START_SERVER;
         if (waitForPrevious || waitForSelf) {
            Thread.sleep(TIMEOUT_START_SERVER);
         }
         timeStarts[node] = System.currentTimeMillis();
         log.debug("starting server " + servers[node]);
         servers[node].start();

         log.debug("started server " + servers[node]);
         waitForServerToStart(servers[node]);

         if (servers[node].getStorageManager() != null && isForceUniqueStorageManagerIds()) {
            for (int i = 0; i < node * 1000; i++) {
               // it is common to have messages landing with similar IDs on separate nodes, which could hide a few issues.
               // so we make them unequal
               servers[node].getStorageManager().generateID();
            }
         }
      }
   }

   protected void stopClusterConnections(final int... nodes) throws Exception {
      for (int node : nodes) {
         if (servers[node].isStarted()) {
            for (ClusterConnection cc : servers[node].getClusterManager().getClusterConnections()) {
               cc.stop();
               cc.flushExecutor();
            }
         }
      }
   }

   protected void stopServers(final int... nodes) throws Exception {
      log.debug("Stopping nodes " + Arrays.toString(nodes));
      Exception exception = null;
      for (int node : nodes) {
         if (servers[node] != null && servers[node].isStarted()) {
            try {
               if (System.currentTimeMillis() - timeStarts[node] < TIMEOUT_START_SERVER) {
                  // We can't stop and start a node too fast (faster than what the Topology could realize about this
                  Thread.sleep(TIMEOUT_START_SERVER);
               }

               timeStarts[node] = System.currentTimeMillis();

               log.debug("stopping server " + node);
               servers[node].stop();
               log.debug("server " + node + " stopped");
            } catch (Exception e) {
               exception = e;
            }
         }
      }
      if (exception != null)
         throw exception;
   }

   protected boolean isFileStorage() {
      return true;
   }

   protected String getServerUri(int node) throws URISyntaxException {
      ActiveMQServer server = servers[node];
      if (server == null) {
         throw new IllegalStateException("No server at node " + server);
      }
      int port = TransportConstants.DEFAULT_PORT + node;
      return "tcp://localhost:" + port;
   }

   public interface ClusterConfigCallback {
      void configure(ClusterConnectionConfiguration config);
   }
}
