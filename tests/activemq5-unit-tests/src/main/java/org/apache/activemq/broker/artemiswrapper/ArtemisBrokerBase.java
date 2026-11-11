/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.broker.artemiswrapper;

import java.io.File;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMAcceptorFactory;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptorFactory;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.nativo.jlibaio.LibaioContext;
import org.apache.activemq.broker.Broker;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.Connection;
import org.apache.activemq.broker.ConnectionContext;
import org.apache.activemq.broker.ConsumerBrokerExchange;
import org.apache.activemq.broker.ProducerBrokerExchange;
import org.apache.activemq.broker.region.Destination;
import org.apache.activemq.broker.region.MessageReference;
import org.apache.activemq.broker.region.Subscription;
import org.apache.activemq.command.ActiveMQDestination;
import org.apache.activemq.command.BrokerId;
import org.apache.activemq.command.BrokerInfo;
import org.apache.activemq.command.ConnectionInfo;
import org.apache.activemq.command.ConsumerControl;
import org.apache.activemq.command.ConsumerInfo;
import org.apache.activemq.command.DestinationInfo;
import org.apache.activemq.command.Message;
import org.apache.activemq.command.MessageAck;
import org.apache.activemq.command.MessageDispatch;
import org.apache.activemq.command.MessageDispatchNotification;
import org.apache.activemq.command.MessagePull;
import org.apache.activemq.command.ProducerInfo;
import org.apache.activemq.command.RemoveSubscriptionInfo;
import org.apache.activemq.command.Response;
import org.apache.activemq.command.SessionInfo;
import org.apache.activemq.command.TransactionId;
import org.apache.activemq.store.PListStore;
import org.apache.activemq.thread.Scheduler;
import org.apache.activemq.usage.Usage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ArtemisBrokerBase implements Broker {

   private static final Logger LOG = LoggerFactory.getLogger(ArtemisBrokerBase.class);
   public static final String INVM_ACCEPTOR_FACTORY = InVMAcceptorFactory.class.getCanonicalName();

   public static final String NETTY_ACCEPTOR_FACTORY = NettyAcceptorFactory.class.getCanonicalName();

   public static final String NETTY_CONNECTOR_FACTORY = NettyConnectorFactory.class.getCanonicalName();

   protected static final String CLUSTER_PASSWORD = "UnitTestsClusterPassword";

   protected volatile boolean stopped;
   protected BrokerId brokerId = new BrokerId("Artemis Broker");
   protected BrokerService bservice;

   protected final File temporaryFolder;
   protected final String testDir;
   protected boolean realStore = false;

   protected ActiveMQServer server;

   protected boolean enableSecurity = false;

   public ArtemisBrokerBase(File temporaryFolder) {
      this.temporaryFolder = temporaryFolder;
      this.testDir = temporaryFolder.getAbsolutePath();

   }

   @Override
   public Destination addDestination(ConnectionContext context,
                                     ActiveMQDestination destination,
                                     boolean createIfTemporary) throws Exception {
      throw new RuntimeException("Don't call me!");
   }

   @Override
   public void removeDestination(ConnectionContext context,
                                 ActiveMQDestination destination,
                                 long timeout) throws Exception {
      throw new RuntimeException("Don't call me!");
   }

   @Override
   public Map<ActiveMQDestination, Destination> getDestinationMap() {
      throw new RuntimeException("Don't call me!");
   }

   @Override
   public Subscription addConsumer(ConnectionContext context, ConsumerInfo info) throws Exception {
      throw new RuntimeException("Don't call me!");
   }

   @Override
   public void removeConsumer(ConnectionContext context, ConsumerInfo info) throws Exception {
      throw new RuntimeException("Don't call me!");
   }

   @Override
   public void removeSubscription(ConnectionContext context, RemoveSubscriptionInfo info) throws Exception {
      throw new RuntimeException("Don't call me!");
   }

   @Override
   public void send(ProducerBrokerExchange producerExchange, Message message) throws Exception {
      throw new RuntimeException("Don't call me!");
   }

   @Override
   public void acknowledge(ConsumerBrokerExchange consumerExchange, MessageAck ack) throws Exception {
      throw new RuntimeException("Don't call me!");
   }

   @Override
   public Response messagePull(ConnectionContext context, MessagePull pull) throws Exception {
      throw new RuntimeException("Don't call me!");
   }

   @Override
   public void processDispatchNotification(MessageDispatchNotification messageDispatchNotification) throws Exception {
      throw new RuntimeException("Don't call me!");
   }

   @Override
   public void gc() {
      throw new RuntimeException("Don't call me!");
   }

   @Override
   public Set<Destination> getDestinations(ActiveMQDestination destination) {
      throw new RuntimeException("Don't call me!");
   }

   @Override
   public void processConsumerControl(ConsumerBrokerExchange consumerExchange, ConsumerControl control) {
      throw new RuntimeException("Don't call me!");
   }

   @Override
   public void reapplyInterceptor() {
      throw new RuntimeException("Don't call me!");
   }

   @Override
   public Broker getAdaptor(Class type) {
      throw new RuntimeException("Don't call me!");
   }

   @Override
   public BrokerId getBrokerId() {
      return brokerId;
   }

   @Override
   public String getBrokerName() {
      return "Artemis Broker";
   }

   @Override
   public void addBroker(Connection connection, BrokerInfo info) {
      throw new RuntimeException("Don't call me!");
   }

   @Override
   public void removeBroker(Connection connection, BrokerInfo info) {
      throw new RuntimeException("Don't call me!");
   }

   @Override
   public void addConnection(ConnectionContext context, ConnectionInfo info) throws Exception {
      throw new RuntimeException("Don't call me!");
   }

   @Override
   public void removeConnection(ConnectionContext context, ConnectionInfo info, Throwable error) throws Exception {
      throw new RuntimeException("Don't call me!");

   }

   @Override
   public void addSession(ConnectionContext context, SessionInfo info) throws Exception {
      throw new RuntimeException("Don't call me!");
   }

   @Override
   public void removeSession(ConnectionContext context, SessionInfo info) throws Exception {
      throw new RuntimeException("Don't call me!");
   }

   @Override
   public void addProducer(ConnectionContext context, ProducerInfo info) throws Exception {
      throw new RuntimeException("Don't call me!");
   }

   @Override
   public void removeProducer(ConnectionContext context, ProducerInfo info) throws Exception {
      throw new RuntimeException("Don't call me!");

   }

   @Override
   public Connection[] getClients() throws Exception {
      throw new RuntimeException("Don't call me!");
   }

   @Override
   public ActiveMQDestination[] getDestinations() throws Exception {
      throw new RuntimeException("Don't call me!");
   }

   @Override
   public Map<ActiveMQDestination, Destination> getDestinationMap(ActiveMQDestination destination) {
      throw new RuntimeException("Don't call me!");
   }

   @Override
   public TransactionId[] getPreparedTransactions(ConnectionContext context) throws Exception {
      throw new RuntimeException("Don't call me!");
   }

   @Override
   public void beginTransaction(ConnectionContext context, TransactionId xid) throws Exception {
      throw new RuntimeException("Don't call me!");
   }

   @Override
   public int prepareTransaction(ConnectionContext context, TransactionId xid) throws Exception {
      throw new RuntimeException("Don't call me!");
   }

   @Override
   public void rollbackTransaction(ConnectionContext context, TransactionId xid) throws Exception {
      throw new RuntimeException("Don't call me!");
   }

   @Override
   public void commitTransaction(ConnectionContext context, TransactionId xid, boolean onePhase) throws Exception {
      throw new RuntimeException("Don't call me!");
   }

   @Override
   public void forgetTransaction(ConnectionContext context, TransactionId transactionId) throws Exception {
      throw new RuntimeException("Don't call me!");
   }

   @Override
   public BrokerInfo[] getPeerBrokerInfos() {
      return null;
   }

   @Override
   public void preProcessDispatch(MessageDispatch messageDispatch) {
      throw new RuntimeException("Don't call me!");
   }

   @Override
   public void postProcessDispatch(MessageDispatch messageDispatch) {
      throw new RuntimeException("Don't call me!");
   }

   @Override
   public boolean isStopped() {
      return stopped;
   }

   @Override
   public Set<ActiveMQDestination> getDurableDestinations() {
      throw new RuntimeException("Don't call me!");
   }

   @Override
   public void addDestinationInfo(ConnectionContext context, DestinationInfo info) throws Exception {
      throw new RuntimeException("Don't call me!");

   }

   @Override
   public void removeDestinationInfo(ConnectionContext context, DestinationInfo info) throws Exception {
      throw new RuntimeException("Don't call me!");

   }

   @Override
   public boolean isFaultTolerantConfiguration() {
      return false;
   }

   @Override
   public ConnectionContext getAdminConnectionContext() {
      return null;
   }

   @Override
   public void setAdminConnectionContext(ConnectionContext adminConnectionContext) {
      //
   }

   @Override
   public PListStore getTempDataStore() {
      throw new RuntimeException("Don't call me!");
   }

   @Override
   public URI getVmConnectorURI() {
      throw new RuntimeException("Don't call me!");
   }

   @Override
   public void brokerServiceStarted() {
      stopped = false;
   }

   @Override
   public BrokerService getBrokerService() {
      return this.bservice;
   }

   @Override
   public Broker getRoot() {
      return this;
   }

   @Override
   public boolean isExpired(MessageReference messageReference) {
      throw new RuntimeException("Don't call me!");
   }

   @Override
   public void messageExpired(ConnectionContext context, MessageReference messageReference, Subscription subscription) {
      throw new RuntimeException("Don't call me!");

   }

   @Override
   public boolean sendToDeadLetterQueue(ConnectionContext context,
                                        MessageReference messageReference,
                                        Subscription subscription,
                                        Throwable poisonCause) {
      throw new RuntimeException("Don't call me!");
   }

   @Override
   public long getBrokerSequenceId() {
      throw new RuntimeException("Don't call me!");
   }

   @Override
   public void messageConsumed(ConnectionContext context, MessageReference messageReference) {
      throw new RuntimeException("Don't call me!");
   }

   @Override
   public void messageDelivered(ConnectionContext context, MessageReference messageReference) {
      throw new RuntimeException("Don't call me!");
   }

   @Override
   public void messageDiscarded(ConnectionContext context, Subscription sub, MessageReference messageReference) {
      throw new RuntimeException("Don't call me!");
   }

   @Override
   public void slowConsumer(ConnectionContext context, Destination destination, Subscription subs) {
      throw new RuntimeException("Don't call me!");
   }

   @Override
   public void fastProducer(ConnectionContext context, ProducerInfo producerInfo, ActiveMQDestination destination) {
      throw new RuntimeException("Don't call me!");
   }

   @Override
   public void isFull(ConnectionContext context, Destination destination, Usage usage) {
      throw new RuntimeException("Don't call me!");
   }

   @Override
   public void nowMasterBroker() {
   }

   @Override
   public Scheduler getScheduler() {
      throw new RuntimeException("Don't call me!");
   }

   @Override
   public ThreadPoolExecutor getExecutor() {
      throw new RuntimeException("Don't call me!");
   }

   @Override
   public void networkBridgeStarted(BrokerInfo brokerInfo, boolean createdByDuplex, String remoteIp) {
      throw new RuntimeException("Don't call me!");
   }

   @Override
   public void networkBridgeStopped(BrokerInfo brokerInfo) {
      throw new RuntimeException("Don't call me!");
   }

   protected final ActiveMQServer createServer(final boolean realFiles, final boolean netty) throws Exception {
      return createServer(realFiles, createDefaultConfig(netty), -1, -1, new HashMap<>());
   }

   protected final ActiveMQServer createServer(final boolean realFiles,
                                               final Configuration configuration,
                                               final int pageSize,
                                               final int maxAddressSize,
                                               final Map<String, AddressSettings> settings) {
      return createServer(realFiles, configuration, pageSize, maxAddressSize, AddressFullMessagePolicy.PAGE, settings);
   }

   protected final ActiveMQServer createServer(final boolean realFiles,
                                               final Configuration configuration,
                                               final int pageSize,
                                               final int maxAddressSize,
                                               final AddressFullMessagePolicy fullPolicy,
                                               final Map<String, AddressSettings> settings) {
      ActiveMQServer server = ActiveMQServers.newActiveMQServer(configuration, realFiles);
      if (settings != null) {
         for (Map.Entry<String, AddressSettings> setting : settings.entrySet()) {
            server.getAddressSettingsRepository().addMatch(setting.getKey(), setting.getValue());
         }
      }

      AddressSettings defaultSetting = new AddressSettings();
      defaultSetting.setPageSizeBytes(pageSize);
      defaultSetting.setMaxSizeBytes(maxAddressSize);
      defaultSetting.setAddressFullMessagePolicy(fullPolicy);

      server.getAddressSettingsRepository().addMatch("#", defaultSetting);

      return server;
   }

   protected Configuration createDefaultConfig(final boolean netty) throws Exception {
      if (netty) {
         return createDefaultConfig(new HashMap<>(), NETTY_ACCEPTOR_FACTORY);
      } else {
         return createDefaultConfig(new HashMap<>(), INVM_ACCEPTOR_FACTORY);
      }
   }

   protected Configuration createDefaultConfig(final Map<String, Object> params,
                                               final String... acceptors) throws Exception {
      ConfigurationImpl configuration = createBasicConfig(-1).setJMXManagementEnabled(false).clearAcceptorConfigurations();

      for (String acceptor : acceptors) {
         TransportConfiguration transportConfig = new TransportConfiguration(acceptor, params);
         configuration.addAcceptorConfiguration(transportConfig);
      }

      return configuration;
   }

   protected final ConfigurationImpl createBasicConfig(final int serverID) {
      ConfigurationImpl configuration = new ConfigurationImpl().setSecurityEnabled(false).setJournalMinFiles(2).setJournalFileSize(100 * 1024).setJournalType(getDefaultJournalType()).setJournalDirectory(getJournalDir(serverID, false)).setBindingsDirectory(getBindingsDir(serverID, false)).setPagingDirectory(getPageDir(serverID, false)).setLargeMessagesDirectory(getLargeMessagesDir(serverID, false)).setJournalCompactMinFiles(0).setJournalCompactPercentage(0).setClusterPassword(CLUSTER_PASSWORD);

      return configuration;
   }

   protected String getLargeMessagesDir(final int index, final boolean backup) {
      return getLargeMessagesDir(testDir, index, backup);
   }

   protected static String getLargeMessagesDir(final String testDir, final int index, final boolean backup) {
      return getLargeMessagesDir(testDir) + directoryNameSuffix(index, backup);
   }

   protected String getPageDir(final int index, final boolean backup) {
      return getPageDir(testDir, index, backup);
   }

   protected static String getPageDir(final String testDir, final int index, final boolean backup) {
      return getPageDir(testDir) + directoryNameSuffix(index, backup);
   }

   protected String getBindingsDir(final int index, final boolean backup) {
      return getBindingsDir(testDir, index, backup);
   }

   protected static String getBindingsDir(final String testDir, final int index, final boolean backup) {
      return getBindingsDir(testDir) + directoryNameSuffix(index, backup);
   }

   protected String getJournalDir(final int index, final boolean backup) {
      return getJournalDir(testDir, index, backup);
   }

   protected static String getJournalDir(final String testDir, final int index, final boolean backup) {
      return getJournalDir(testDir) + directoryNameSuffix(index, backup);
   }

   private static String directoryNameSuffix(int index, boolean backup) {
      if (index == -1)
         return "";
      return index + "-" + (backup ? "B" : "L");
   }

   protected static JournalType getDefaultJournalType() {
      if (LibaioContext.isLoaded()) {
         return JournalType.ASYNCIO;
      } else {
         return JournalType.NIO;
      }
   }

   protected final void clearDataRecreateServerDirs() {
      clearDataRecreateServerDirs(testDir);
   }

   protected void clearDataRecreateServerDirs(final String testDir1) {
      // Need to delete the root

      File file = new File(testDir1);
      deleteDirectory(file);
      file.mkdirs();

      recreateDirectory(getJournalDir(testDir1));
      recreateDirectory(getBindingsDir(testDir1));
      recreateDirectory(getPageDir(testDir1));
      recreateDirectory(getLargeMessagesDir(testDir1));
      recreateDirectory(getClientLargeMessagesDir(testDir1));
      recreateDirectory(getTemporaryDir(testDir1));
   }

   protected String getTemporaryDir(final String testDir1) {
      return testDir1 + "/temp";
   }

   protected String getClientLargeMessagesDir(final String testDir1) {
      return testDir1 + "/client-large-msg";
   }

   protected static String getLargeMessagesDir(final String testDir1) {
      return testDir1 + "/large-msg";
   }

   protected static String getPageDir(final String testDir1) {
      return testDir1 + "/page";
   }

   protected static String getBindingsDir(final String testDir1) {
      return testDir1 + "/bindings";
   }

   protected static String getJournalDir(final String testDir1) {
      return testDir1 + "/journal";
   }

   protected static final void recreateDirectory(final String directory) {
      File file = new File(directory);
      deleteDirectory(file);
      file.mkdirs();
   }

   protected static final boolean deleteDirectory(final File directory) {
      if (directory.isDirectory()) {
         String[] files = directory.list();
         int num = 5;
         int attempts = 0;
         while (files == null && (attempts < num)) {
            try {
               Thread.sleep(100);
            } catch (InterruptedException e) {
            }
            files = directory.list();
            attempts++;
         }

         for (String file : files) {
            File f = new File(directory, file);
            if (!deleteDirectory(f)) {
               LOG.warn("Failed to clean up file: " + f.getAbsolutePath());
            }
         }
      }

      return directory.delete();
   }

   public ActiveMQServer getServer() {
      return server;
   }

}
