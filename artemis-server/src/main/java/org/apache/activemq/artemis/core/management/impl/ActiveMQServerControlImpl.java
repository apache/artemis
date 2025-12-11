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
package org.apache.activemq.artemis.core.management.impl;

import javax.management.ListenerNotFoundException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanOperationInfo;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationEmitter;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.transaction.xa.Xid;
import java.io.File;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.ActiveMQAddressDoesNotExistException;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQIllegalStateException;
import org.apache.activemq.artemis.api.core.JsonUtil;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl;
import org.apache.activemq.artemis.api.core.management.AddressControl;
import org.apache.activemq.artemis.api.core.management.BridgeControl;
import org.apache.activemq.artemis.api.core.management.CoreNotificationType;
import org.apache.activemq.artemis.api.core.management.DivertControl;
import org.apache.activemq.artemis.api.core.management.ManagementHelper;
import org.apache.activemq.artemis.api.core.management.Parameter;
import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.core.client.impl.Topology;
import org.apache.activemq.artemis.core.client.impl.TopologyMemberImpl;
import org.apache.activemq.artemis.core.config.BridgeConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.ConnectorServiceConfiguration;
import org.apache.activemq.artemis.core.config.DivertConfiguration;
import org.apache.activemq.artemis.core.config.HAPolicyConfiguration;
import org.apache.activemq.artemis.core.config.TransformerConfiguration;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.management.impl.view.AddressView;
import org.apache.activemq.artemis.core.management.impl.view.ConnectionView;
import org.apache.activemq.artemis.core.management.impl.view.ConsumerField;
import org.apache.activemq.artemis.core.management.impl.view.ConsumerView;
import org.apache.activemq.artemis.core.management.impl.view.ProducerView;
import org.apache.activemq.artemis.core.management.impl.view.QueueView;
import org.apache.activemq.artemis.core.management.impl.view.SessionView;
import org.apache.activemq.artemis.core.messagecounter.MessageCounterManager;
import org.apache.activemq.artemis.core.messagecounter.impl.MessageCounterManagerImpl;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.persistence.config.PersistedAddressSettingJSON;
import org.apache.activemq.artemis.core.persistence.config.PersistedConnector;
import org.apache.activemq.artemis.core.persistence.config.PersistedSecuritySetting;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.Bindings;
import org.apache.activemq.artemis.core.postoffice.DuplicateIDCache;
import org.apache.activemq.artemis.core.postoffice.PostOffice;
import org.apache.activemq.artemis.core.postoffice.impl.LocalQueueBinding;
import org.apache.activemq.artemis.core.remoting.server.RemotingService;
import org.apache.activemq.artemis.core.security.CheckType;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.security.impl.SecurityStoreImpl;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.core.server.ActiveMQMessageBundle;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.BrokerConnection;
import org.apache.activemq.artemis.core.server.ComponentConfigurationRoutingType;
import org.apache.activemq.artemis.core.server.ConnectorServiceFactory;
import org.apache.activemq.artemis.core.server.Consumer;
import org.apache.activemq.artemis.core.server.Divert;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.core.server.ServerProducer;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.server.ServiceComponent;
import org.apache.activemq.artemis.core.server.cluster.ClusterConnection;
import org.apache.activemq.artemis.core.server.cluster.ClusterManager;
import org.apache.activemq.artemis.core.server.cluster.ha.HAPolicy;
import org.apache.activemq.artemis.core.server.cluster.ha.PrimaryOnlyPolicy;
import org.apache.activemq.artemis.core.server.cluster.ha.ScaleDownPolicy;
import org.apache.activemq.artemis.core.server.cluster.ha.SharedStoreBackupPolicy;
import org.apache.activemq.artemis.core.server.group.GroupingHandler;
import org.apache.activemq.artemis.core.server.impl.Activation;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.impl.SharedNothingPrimaryActivation;
import org.apache.activemq.artemis.core.server.replay.ReplayManager;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.settings.impl.DeletionPolicy;
import org.apache.activemq.artemis.core.settings.impl.SlowConsumerPolicy;
import org.apache.activemq.artemis.core.transaction.ResourceManager;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.core.transaction.TransactionDetail;
import org.apache.activemq.artemis.core.transaction.TransactionDetailFactory;
import org.apache.activemq.artemis.core.transaction.impl.CoreTransactionDetail;
import org.apache.activemq.artemis.core.transaction.impl.XidImpl;
import org.apache.activemq.artemis.json.JsonArray;
import org.apache.activemq.artemis.json.JsonArrayBuilder;
import org.apache.activemq.artemis.json.JsonObject;
import org.apache.activemq.artemis.json.JsonObjectBuilder;
import org.apache.activemq.artemis.logs.AuditLogger;
import org.apache.activemq.artemis.marker.WebServerComponentMarker;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.security.ActiveMQBasicSecurityManager;
import org.apache.activemq.artemis.spi.core.security.jaas.PropertiesLoginModuleConfigurator;
import org.apache.activemq.artemis.utils.JsonLoader;
import org.apache.activemq.artemis.utils.ListUtil;
import org.apache.activemq.artemis.utils.PasswordMaskingUtil;
import org.apache.activemq.artemis.utils.SecurityFormatter;
import org.apache.activemq.artemis.utils.collections.TypedProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ActiveMQServerControlImpl extends AbstractControl implements ActiveMQServerControl, NotificationEmitter, org.apache.activemq.artemis.core.server.management.NotificationListener {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
   public static final String CONFIG_AS_PROPERTIES_FILE = "broker_config_as_properties.txt";
   public static final String TMP_DIR_SYSTEM_PROPERTY = "java.io.tmpdir";

   private final PostOffice postOffice;

   private final Configuration configuration;

   private final ResourceManager resourceManager;

   private final RemotingService remotingService;

   private final ActiveMQServer server;

   private final MessageCounterManager messageCounterManager;

   private final NotificationBroadcasterSupport broadcaster;

   private final AtomicLong notifSeq = new AtomicLong(0);

   private final Object userLock = new Object();

   private final Object embeddedWebServerLock = new Object();

   public ActiveMQServerControlImpl(final PostOffice postOffice,
                                    final Configuration configuration,
                                    final ResourceManager resourceManager,
                                    final RemotingService remotingService,
                                    final ActiveMQServer messagingServer,
                                    final MessageCounterManager messageCounterManager,
                                    final StorageManager storageManager,
                                    final NotificationBroadcasterSupport broadcaster) throws Exception {
      super(ActiveMQServerControl.class, storageManager);
      this.postOffice = postOffice;
      this.configuration = configuration;
      this.resourceManager = resourceManager;
      this.remotingService = remotingService;
      server = messagingServer;
      this.messageCounterManager = messageCounterManager;
      this.broadcaster = broadcaster;
      server.getManagementService().addNotificationListener(this);
   }

   // ActiveMQServerControlMBean implementation --------------------

   @Override
   public boolean isStarted() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.isStarted(this.server);
      }
      clearIO();
      try {
         return server.isStarted();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getName() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getName(this.server);
      }
      checkStarted();

      clearIO();
      try {
         return server.getConfiguration().getName();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public long getCurrentTimeMillis() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getCurrentTimeMillis(this.server);
      }
      return System.currentTimeMillis();
   }

   @Override
   public String getVersion() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getVersion(this.server);
      }
      checkStarted();

      clearIO();
      try {
         return server.getVersion().getFullVersion();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public boolean isActive() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getVersion(this.server);
      }
      checkStarted();

      clearIO();
      try {
         return server.isActive();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public boolean isBackup() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.isBackup(this.server);
      }
      checkStarted();

      clearIO();
      try {
         return server.getHAPolicy().isBackup();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public boolean isSharedStore() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.isSharedStore(this.server);
      }
      checkStarted();

      clearIO();
      try {
         return server.getHAPolicy().isSharedStore();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getBindingsDirectory() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getBindingsDirectory(this.server);
      }
      checkStarted();

      clearIO();
      try {
         return configuration.getBindingsDirectory();
      } finally {
         blockOnIO();
      }
   }

   public String[] getInterceptorClassNames() {
      checkStarted();

      clearIO();
      try {
         return configuration.getIncomingInterceptorClassNames().toArray(new String[configuration.getIncomingInterceptorClassNames().size()]);
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String[] getIncomingInterceptorClassNames() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getIncomingInterceptorClassNames(this.server);
      }
      checkStarted();

      clearIO();
      try {
         return configuration.getIncomingInterceptorClassNames().toArray(new String[configuration.getIncomingInterceptorClassNames().size()]);
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String[] getOutgoingInterceptorClassNames() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getOutgoingInterceptorClassNames(this.server);
      }
      checkStarted();

      clearIO();
      try {
         return configuration.getOutgoingInterceptorClassNames().toArray(new String[configuration.getOutgoingInterceptorClassNames().size()]);
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String[] getBrokerPluginClassNames() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getBrokerPluginClassNames(this.server);
      }
      checkStarted();

      clearIO();
      try {
         return configuration.getBrokerPlugins().stream()
            .map(brokerPlugin -> Objects.requireNonNullElse(brokerPlugin.getClass().getCanonicalName(), brokerPlugin.getClass().getName()))
            .toArray(String[]::new);
      } finally {
         blockOnIO();
      }
   }

   @Override
   public int getJournalPoolFiles() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getJournalBufferSize(this.server);
      }
      checkStarted();

      clearIO();
      try {
         return configuration.getJournalPoolFiles();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public int getJournalBufferSize() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getJournalBufferSize(this.server);
      }
      checkStarted();

      clearIO();
      try {
         return configuration.getJournalType() == JournalType.ASYNCIO ? configuration.getJournalBufferSize_AIO() : configuration.getJournalBufferSize_NIO();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public int getJournalBufferTimeout() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getJournalBufferTimeout(this.server);
      }
      checkStarted();

      clearIO();
      try {
         return configuration.getJournalType() == JournalType.ASYNCIO ? configuration.getJournalBufferTimeout_AIO() : configuration.getJournalBufferTimeout_NIO();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public void setFailoverOnServerShutdown(boolean failoverOnServerShutdown) {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.setFailoverOnServerShutdown(this.server, failoverOnServerShutdown);
      }
      checkStarted();

      clearIO();
      try {
         HAPolicy haPolicy = server.getHAPolicy();
         if (haPolicy instanceof SharedStoreBackupPolicy sharedStoreBackupPolicy) {
            sharedStoreBackupPolicy.setFailoverOnServerShutdown(failoverOnServerShutdown);
         }
      } finally {
         blockOnIO();
      }
   }

   @Override
   public boolean isFailoverOnServerShutdown() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.isFailoverOnServerShutdown(this.server);
      }
      checkStarted();

      clearIO();
      try {
         HAPolicy haPolicy = server.getHAPolicy();
         if (haPolicy instanceof SharedStoreBackupPolicy sharedStoreBackupPolicy) {
            return sharedStoreBackupPolicy.isFailoverOnServerShutdown();
         } else {
            return false;
         }
      } finally {
         blockOnIO();
      }
   }

   @Override
   public int getJournalMaxIO() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getJournalMaxIO(this.server);
      }
      checkStarted();

      clearIO();
      try {
         return configuration.getJournalType() == JournalType.ASYNCIO ? configuration.getJournalMaxIO_AIO() : configuration.getJournalMaxIO_NIO();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getJournalDirectory() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getJournalDirectory(this.server);
      }
      checkStarted();

      clearIO();
      try {
         return configuration.getJournalDirectory();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public int getJournalFileSize() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getJournalFileSize(this.server);
      }
      checkStarted();

      clearIO();
      try {
         return configuration.getJournalFileSize();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public int getJournalMinFiles() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getJournalMinFiles(this.server);
      }
      checkStarted();

      clearIO();
      try {
         return configuration.getJournalMinFiles();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public int getJournalCompactMinFiles() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getJournalCompactMinFiles(this.server);
      }
      checkStarted();

      clearIO();
      try {
         return configuration.getJournalCompactMinFiles();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public int getJournalCompactPercentage() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getJournalCompactPercentage(this.server);
      }
      checkStarted();

      clearIO();
      try {
         return configuration.getJournalCompactPercentage();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public boolean isPersistenceEnabled() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.isPersistenceEnabled(this.server);
      }
      checkStarted();

      clearIO();
      try {
         return configuration.isPersistenceEnabled();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getJournalType() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getJournalType(this.server);
      }
      checkStarted();

      clearIO();
      try {
         return configuration.getJournalType().toString();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getPagingDirectory() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getPagingDirectory(this.server);
      }
      checkStarted();

      clearIO();
      try {
         return configuration.getPagingDirectory();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public int getScheduledThreadPoolMaxSize() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getScheduledThreadPoolMaxSize(this.server);
      }
      checkStarted();

      clearIO();
      try {
         return configuration.getScheduledThreadPoolMaxSize();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public int getThreadPoolMaxSize() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getThreadPoolMaxSize(this.server);
      }
      checkStarted();

      clearIO();
      try {
         return configuration.getThreadPoolMaxSize();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public long getSecurityInvalidationInterval() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getSecurityInvalidationInterval(this.server);
      }
      checkStarted();

      clearIO();
      try {
         return configuration.getSecurityInvalidationInterval();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public boolean isClustered() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.isClustered(this.server);
      }
      checkStarted();

      clearIO();
      try {
         return configuration.isClustered();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public boolean isCreateBindingsDir() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.isCreateBindingsDir(this.server);
      }
      checkStarted();

      clearIO();
      try {
         return configuration.isCreateBindingsDir();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public boolean isCreateJournalDir() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.isCreateJournalDir(this.server);
      }
      checkStarted();

      clearIO();
      try {
         return configuration.isCreateJournalDir();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public boolean isJournalSyncNonTransactional() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.isJournalSyncNonTransactional(this.server);
      }
      checkStarted();

      clearIO();
      try {
         return configuration.isJournalSyncNonTransactional();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public boolean isJournalSyncTransactional() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.isJournalSyncTransactional(this.server);
      }
      checkStarted();

      clearIO();
      try {
         return configuration.isJournalSyncTransactional();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public boolean isSecurityEnabled() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.isSecurityEnabled(this.server);
      }
      checkStarted();

      clearIO();
      try {
         return configuration.isSecurityEnabled();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public boolean isAsyncConnectionExecutionEnabled() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.isAsyncConnectionExecutionEnabled(this.server);
      }
      checkStarted();

      clearIO();
      try {
         return configuration.isAsyncConnectionExecutionEnabled();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public int getDiskScanPeriod() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getDiskScanPeriod(this.server);
      }
      checkStarted();

      clearIO();
      try {
         return configuration.getDiskScanPeriod();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public int getMaxDiskUsage() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getMaxDiskUsage(this.server);
      }
      checkStarted();

      clearIO();
      try {
         return configuration.getMaxDiskUsage();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public long getGlobalMaxSize() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getGlobalMaxSize(this.server);
      }
      checkStarted();

      clearIO();
      try {
         return configuration.getGlobalMaxSize();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public long getAddressMemoryUsage() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getAddressMemoryUsage(this.server);
      }
      checkStarted();
      clearIO();
      try {
         //this should not happen but if it does, return -1 to highlight it is not working
         if (server.getPagingManager() == null) {
            return -1L;
         }
         return server.getPagingManager().getGlobalSize();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public double getDiskStoreUsage() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getDiskStoreUsage(this.server);
      }
      checkStarted();
      clearIO();
      try {
         return server.getDiskStoreUsage();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public int  getAddressMemoryUsagePercentage() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getAddressMemoryUsagePercentage(this.server);
      }
      long globalMaxSize = getGlobalMaxSize();
      // no max size set implies 0% used
      if (globalMaxSize <= 0) {
         return 0;
      }

      long memoryUsed = getAddressMemoryUsage();
      if (memoryUsed <= 0) {
         return 0;
      }
      double result = (100D * memoryUsed) / globalMaxSize;
      return (int) result;
   }

   @Override
   public String getHAPolicy() {
      HAPolicyConfiguration haConfig = configuration.getHAPolicyConfiguration();
      return haConfig == null ? null : haConfig.getType().getName();
   }

   @Override
   public long getAuthenticationCacheSize() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getAuthenticationCacheSize(this.server);
      }
      return ((SecurityStoreImpl)server.getSecurityStore()).getAuthenticationCacheSize();
   }

   @Override
   public long getAuthorizationCacheSize() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getAuthorizationCacheSize(this.server);
      }
      return ((SecurityStoreImpl)server.getSecurityStore()).getAuthorizationCacheSize();
   }

   @Override
   public boolean freezeReplication() throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.freezeReplication(this.server);
      }
      try (AutoCloseable lock = server.managementLock()) {
         Activation activation = server.getActivation();
         if (activation instanceof SharedNothingPrimaryActivation sharedNothingPrimaryActivation) {
            sharedNothingPrimaryActivation.freezeReplication();
            return true;
         }
         return false;
      }
   }

   private enum AddressInfoTextFormatter {
      Long {
         @Override
         public StringBuilder format(AddressInfo addressInfo, StringBuilder output) {
            output.append("Address [name=").append(addressInfo.getName());
            output.append(", routingTypes={");
            final EnumSet<RoutingType> routingTypes = addressInfo.getRoutingTypes();
            if (!routingTypes.isEmpty()) {
               for (RoutingType routingType : routingTypes) {
                  output.append(routingType).append(',');
               }
               // delete hanging comma
               output.deleteCharAt(output.length() - 1);
            }
            output.append('}');
            output.append(", autoCreated=").append(addressInfo.isAutoCreated());
            output.append(']');
            return output;
         }
      };

      public abstract StringBuilder format(AddressInfo addressInfo, StringBuilder output);
   }

   public enum QueueTextFormatter {
      Long {
         @Override
         StringBuilder format(Queue queue, StringBuilder output) {
            output.append("Queue [name=").append(queue.getName());
            output.append(", address=").append(queue.getAddress());
            output.append(", routingType=").append(queue.getRoutingType());
            final Filter filter = queue.getFilter();
            if (filter != null) {
               output.append(", filter=").append(filter.getFilterString());
            }
            output.append(", durable=").append(queue.isDurable());
            final int maxConsumers = queue.getMaxConsumers();
            if (maxConsumers != Queue.MAX_CONSUMERS_UNLIMITED) {
               output.append(", maxConsumers=").append(queue.getMaxConsumers());
            }
            output.append(", purgeOnNoConsumers=").append(queue.isPurgeOnNoConsumers());
            output.append(", autoCreateAddress=").append(queue.isAutoCreated());
            output.append(", exclusive=").append(queue.isExclusive());
            output.append(", lastValue=").append(queue.isLastValue());
            output.append(", lastValueKey=").append(queue.getLastValueKey());
            output.append(", nonDestructive=").append(queue.isNonDestructive());
            output.append(", consumersBeforeDispatch=").append(queue.getConsumersBeforeDispatch());
            output.append(", delayBeforeDispatch=").append(queue.getDelayBeforeDispatch());
            output.append(", autoCreateAddress=").append(queue.isAutoCreated());

            output.append(']');
            return output;
         }
      };

      abstract StringBuilder format(Queue queue, StringBuilder output);
   }

   @Override
   public String createAddress(String name, String routingTypes) throws Exception {

      checkStarted();

      clearIO();
      try {
         EnumSet<RoutingType> set = EnumSet.noneOf(RoutingType.class);
         for (String routingType : ListUtil.toList(routingTypes)) {
            set.add(RoutingType.valueOf(routingType));
         }
         final AddressInfo addressInfo = new AddressInfo(SimpleString.of(name), set);
         if (server.addAddressInfo(addressInfo)) {
            String result = AddressInfoTextFormatter.Long.format(addressInfo, new StringBuilder()).toString();
            if (AuditLogger.isResourceLoggingEnabled()) {
               AuditLogger.createAddressSuccess(name, routingTypes);
            }
            return result;
         } else {
            if (AuditLogger.isResourceLoggingEnabled()) {
               AuditLogger.createAddressFailure(name, routingTypes);
            }
            throw ActiveMQMessageBundle.BUNDLE.addressAlreadyExists(addressInfo.getName());
         }
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String updateAddress(String name, String routingTypes) throws Exception {

      checkStarted();

      clearIO();
      try {
         final EnumSet<RoutingType> routingTypeSet;
         if (routingTypes == null) {
            routingTypeSet = null;
         } else {
            routingTypeSet = EnumSet.noneOf(RoutingType.class);
            final String[] routingTypeNames = routingTypes.split(",");
            for (String routingTypeName : routingTypeNames) {
               routingTypeSet.add(RoutingType.valueOf(routingTypeName));
            }
         }
         if (!server.updateAddressInfo(SimpleString.of(name), routingTypeSet)) {
            if (AuditLogger.isResourceLoggingEnabled()) {
               AuditLogger.updateAddressFailure(name, routingTypes);
            }
            throw ActiveMQMessageBundle.BUNDLE.addressDoesNotExist(SimpleString.of(name));
         }
         if (AuditLogger.isResourceLoggingEnabled()) {
            AuditLogger.updateAddressSuccess(name, routingTypes);
         }
         return AddressInfoTextFormatter.Long.format(server.getAddressInfo(SimpleString.of(name)), new StringBuilder()).toString();
      } finally {
         blockOnIO();
      }
   }


   @Override
   public void deleteAddress(String name) throws Exception {
      deleteAddress(name, false);
   }

   @Override
   public void deleteAddress(String name, boolean force) throws Exception {

      // delete might be a long running task, we ensure only one large task running
      try (AutoCloseable lock = server.managementLock()) {
         checkStarted();

         clearIO();
         try {
            server.removeAddressInfo(SimpleString.of(name), null, force);
            if (AuditLogger.isResourceLoggingEnabled()) {
               AuditLogger.deleteAddressSuccess(name);
            }
         } catch (ActiveMQException e) {
            if (AuditLogger.isResourceLoggingEnabled()) {
               AuditLogger.deleteAddressFailure(name);
            }
            throw new IllegalStateException(e.getMessage());
         } finally {
            blockOnIO();
         }
      }
   }

   @Deprecated
   @Override
   public void deployQueue(final String address, final String name, final String filterString) throws Exception {
      deployQueue(address, name, filterString, true);
   }

   @Deprecated
   @Override
   public void deployQueue(final String address,
                           final String name,
                           final String filterStr,
                           final boolean durable) throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.deployQueue(this.server, address, name, filterStr, durable);
      }
      checkStarted();

      clearIO();
      try {
         server.createQueue(QueueConfiguration.of(name).setAddress(address).setFilterString(filterStr).setDurable(durable));
      } finally {
         blockOnIO();
      }
   }

   @Override
   public void createQueue(final String address, final String name) throws Exception {
      createQueue(address, name, true);
   }

   @Override
   public void createQueue(final String address, final String name, final String routingType) throws Exception {
      createQueue(address, name, true, routingType);
   }

   @Override
   public void createQueue(final String address, final String name, final boolean durable) throws Exception {
      createQueue(address, name, null, durable);
   }

   @Override
   public void createQueue(final String address, final String name, final boolean durable, final String routingType) throws Exception {
      createQueue(address, name, null, durable, routingType);
   }

   @Override
   public void createQueue(final String address, final String name, final String filterStr, final boolean durable) throws Exception {
      createQueue(address, name, filterStr, durable, server.getAddressSettingsRepository().getMatch(address == null ? name : address).getDefaultQueueRoutingType().toString());
   }


   @Override
   public void createQueue(final String address, final String name, final String filterStr, final boolean durable, final String routingType) throws Exception {
      AddressSettings addressSettings = server.getAddressSettingsRepository().getMatch(address == null ? name : address);
      createQueue(address, routingType, name, filterStr, durable, addressSettings.getDefaultMaxConsumers(), addressSettings.isDefaultPurgeOnNoConsumers(), addressSettings.isAutoCreateAddresses());
   }

   @Override
   public String createQueue(String address,
                             String routingType,
                             String name,
                             String filterStr,
                             boolean durable,
                             int maxConsumers,
                             boolean purgeOnNoConsumers,
                             boolean autoCreateAddress) throws Exception {
      AddressSettings addressSettings = server.getAddressSettingsRepository().getMatch(address == null ? name : address);
      return createQueue(
              address,
              routingType,
              name,
              filterStr,
              durable,
              maxConsumers,
              purgeOnNoConsumers,
              addressSettings.isDefaultExclusiveQueue(),
              addressSettings.isDefaultGroupRebalance(),
              addressSettings.getDefaultGroupBuckets(),
              addressSettings.isDefaultLastValueQueue(),
              Objects.toString(addressSettings.getDefaultLastValueKey(), null),
              addressSettings.isDefaultNonDestructive(),
              addressSettings.getDefaultConsumersBeforeDispatch(),
              addressSettings.getDefaultDelayBeforeDispatch(),
              addressSettings.isAutoDeleteCreatedQueues(),
              addressSettings.getAutoDeleteQueuesDelay(),
              addressSettings.getAutoDeleteQueuesMessageCount(),
              autoCreateAddress
      );
   }

   @Override
   public String createQueue(String address,
                             String routingType,
                             String name,
                             String filterStr,
                             boolean durable,
                             int maxConsumers,
                             boolean purgeOnNoConsumers,
                             boolean exclusive,
                             boolean groupRebalance,
                             int groupBuckets,
                             boolean lastValue,
                             String lastValueKey,
                             boolean nonDestructive,
                             int consumersBeforeDispatch,
                             long delayBeforeDispatch,
                             boolean autoCreateAddress) throws Exception {
      AddressSettings addressSettings = server.getAddressSettingsRepository().getMatch(address == null ? name : address);
      return createQueue(
            address,
            routingType,
            name,
            filterStr,
            durable,
            maxConsumers,
            purgeOnNoConsumers,
            exclusive,
            groupRebalance,
            groupBuckets,
            lastValue,
            lastValueKey,
            nonDestructive,
            consumersBeforeDispatch,
            delayBeforeDispatch,
            addressSettings.isAutoDeleteCreatedQueues(),
            addressSettings.getAutoDeleteQueuesDelay(),
            addressSettings.getAutoDeleteQueuesMessageCount(),
            autoCreateAddress);
   }

   @Override
   public String createQueue(String address,
                             String routingType,
                             String name,
                             String filterStr,
                             boolean durable,
                             int maxConsumers,
                             boolean purgeOnNoConsumers,
                             boolean exclusive,
                             boolean groupRebalance,
                             int groupBuckets,
                             boolean lastValue,
                             String lastValueKey,
                             boolean nonDestructive,
                             int consumersBeforeDispatch,
                             long delayBeforeDispatch,
                             boolean autoDelete,
                             long autoDeleteDelay,
                             long autoDeleteMessageCount,
                             boolean autoCreateAddress) throws Exception {
      AddressSettings addressSettings = server.getAddressSettingsRepository().getMatch(address == null ? name : address);
      return createQueue(
            address,
            routingType,
            name,
            filterStr,
            durable,
            maxConsumers,
            purgeOnNoConsumers,
            exclusive,
            groupRebalance,
            groupBuckets,
            Objects.toString(addressSettings.getDefaultGroupFirstKey(), null),
            lastValue,
            lastValueKey,
            nonDestructive,
            consumersBeforeDispatch,
            delayBeforeDispatch,
            autoDelete,
            autoDeleteDelay,
            autoDeleteMessageCount,
            autoCreateAddress
      );
   }

   @Override
   public String createQueue(String address,
                             String routingType,
                             String name,
                             String filterStr,
                             boolean durable,
                             int maxConsumers,
                             boolean purgeOnNoConsumers,
                             boolean exclusive,
                             boolean groupRebalance,
                             int groupBuckets,
                             String groupFirstKey,
                             boolean lastValue,
                             String lastValueKey,
                             boolean nonDestructive,
                             int consumersBeforeDispatch,
                             long delayBeforeDispatch,
                             boolean autoDelete,
                             long autoDeleteDelay,
                             long autoDeleteMessageCount,
                             boolean autoCreateAddress) throws Exception {
      AddressSettings addressSettings = server.getAddressSettingsRepository().getMatch(address == null ? name : address);
      return createQueue(
         address,
         routingType,
         name,
         filterStr,
         durable,
         maxConsumers,
         purgeOnNoConsumers,
         exclusive,
         groupRebalance,
         groupBuckets,
         groupFirstKey,
         lastValue,
         lastValueKey,
         nonDestructive,
         consumersBeforeDispatch,
         delayBeforeDispatch,
         autoDelete,
         autoDeleteDelay,
         autoDeleteMessageCount,
         autoCreateAddress,
         addressSettings.getDefaultRingSize()
      );
   }

   @Override
   public String createQueue(String address,
                             String routingType,
                             String name,
                             String filterStr,
                             boolean durable,
                             int maxConsumers,
                             boolean purgeOnNoConsumers,
                             boolean exclusive,
                             boolean groupRebalance,
                             int groupBuckets,
                             String groupFirstKey,
                             boolean lastValue,
                             String lastValueKey,
                             boolean nonDestructive,
                             int consumersBeforeDispatch,
                             long delayBeforeDispatch,
                             boolean autoDelete,
                             long autoDeleteDelay,
                             long autoDeleteMessageCount,
                             boolean autoCreateAddress,
                             long ringSize) throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.createQueue(this.server, null, null, address, routingType, name, filterStr, durable, maxConsumers, purgeOnNoConsumers, exclusive, groupRebalance, groupBuckets, groupFirstKey, lastValue, lastValueKey, nonDestructive, consumersBeforeDispatch, delayBeforeDispatch, autoDelete, autoDeleteDelay, autoDeleteMessageCount, autoCreateAddress, ringSize);
      }
      checkStarted();

      clearIO();

      SimpleString filter = filterStr == null ? null : SimpleString.of(filterStr);
      try {
         if (filterStr != null && !filterStr.trim().isEmpty()) {
            filter = SimpleString.of(filterStr);
         }

         final Queue queue = server.createQueue(QueueConfiguration.of(name).setAddress(address).setRoutingType(RoutingType.valueOf(routingType.toUpperCase())).setFilterString(filter).setDurable(durable).setMaxConsumers(maxConsumers).setPurgeOnNoConsumers(purgeOnNoConsumers).setExclusive(exclusive).setGroupRebalance(groupRebalance).setGroupBuckets(groupBuckets).setGroupFirstKey(groupFirstKey).setLastValue(lastValue).setLastValueKey(lastValueKey).setNonDestructive(nonDestructive).setConsumersBeforeDispatch(consumersBeforeDispatch).setDelayBeforeDispatch(delayBeforeDispatch).setAutoDelete(autoDelete).setAutoDeleteDelay(autoDeleteDelay).setAutoDeleteMessageCount(autoDeleteMessageCount).setAutoCreateAddress(autoCreateAddress).setRingSize(ringSize));
         if (AuditLogger.isResourceLoggingEnabled()) {
            AuditLogger.createQueueSuccess(name, address, routingType);
         }
         return QueueTextFormatter.Long.format(queue, new StringBuilder()).toString();
      } catch (ActiveMQException e) {
         if (AuditLogger.isResourceLoggingEnabled()) {
            AuditLogger.createQueueFailure(name, address, routingType);
         }
         throw new IllegalStateException(e.getMessage());
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String createQueue(String queueConfigurationAsJson) throws Exception {
      return createQueue(queueConfigurationAsJson, false);
   }

   @Override
   public String createQueue(String queueConfigurationAsJson, boolean ignoreIfExists) throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.createQueue(this.server, null, null, queueConfigurationAsJson, ignoreIfExists);
      }
      checkStarted();

      clearIO();

      try {
         // when the QueueConfiguration is passed through createQueue all of its defaults get set which we return to the caller
         QueueConfiguration queueConfiguration = QueueConfiguration.fromJSON(queueConfigurationAsJson);
         if (queueConfiguration == null) {
            throw ActiveMQMessageBundle.BUNDLE.failedToParseJson(queueConfigurationAsJson);
         }
         server.createQueue(queueConfiguration, ignoreIfExists);
         return queueConfiguration.toJSON();
      } catch (ActiveMQException e) {
         throw new IllegalStateException(e.getMessage());
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String updateQueue(String queueConfigurationAsJson) throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.updateQueue(this.server, queueConfigurationAsJson);
      }
      checkStarted();

      clearIO();

      try {
         QueueConfiguration queueConfiguration = QueueConfiguration.fromJSON(queueConfigurationAsJson);
         if (queueConfiguration == null) {
            throw ActiveMQMessageBundle.BUNDLE.failedToParseJson(queueConfigurationAsJson);
         }
         final Queue queue = server.updateQueue(queueConfiguration);
         if (queue == null) {
            throw ActiveMQMessageBundle.BUNDLE.noSuchQueue(queueConfiguration.getName());
         }
         return server.locateQueue(queueConfiguration.getName()).getQueueConfiguration().toJSON();
      } finally {
         blockOnIO();
      }
   }

   @Deprecated
   @Override
   public String updateQueue(String name,
                             String routingType,
                             Integer maxConsumers,
                             Boolean purgeOnNoConsumers) throws Exception {
      return updateQueue(name, routingType, maxConsumers, purgeOnNoConsumers, null);
   }

   @Deprecated
   @Override
   public String updateQueue(String name,
                             String routingType,
                             Integer maxConsumers,
                             Boolean purgeOnNoConsumers,
                             Boolean exclusive) throws Exception {
      return updateQueue(name, routingType, maxConsumers, purgeOnNoConsumers, exclusive, null);
   }

   @Override
   public String updateQueue(String name,
                             String routingType,
                             Integer maxConsumers,
                             Boolean purgeOnNoConsumers,
                             Boolean exclusive,
                             String user) throws Exception {
      return updateQueue(name, routingType, null, maxConsumers, purgeOnNoConsumers, exclusive, null, null, null, null, null, user);
   }

   @Override
   public String updateQueue(String name,
                             String routingType,
                             String filter,
                             Integer maxConsumers,
                             Boolean purgeOnNoConsumers,
                             Boolean exclusive,
                             Boolean groupRebalance,
                             Integer groupBuckets,
                             Boolean nonDestructive,
                             Integer consumersBeforeDispatch,
                             Long delayBeforeDispatch,
                             String user) throws Exception {
      return updateQueue(name, routingType, filter, maxConsumers, purgeOnNoConsumers, exclusive, groupRebalance, groupBuckets, null, nonDestructive, consumersBeforeDispatch, delayBeforeDispatch, user);
   }

   @Override
   public String updateQueue(String name,
                             String routingType,
                             String filter,
                             Integer maxConsumers,
                             Boolean purgeOnNoConsumers,
                             Boolean exclusive,
                             Boolean groupRebalance,
                             Integer groupBuckets,
                             String groupFirstKey,
                             Boolean nonDestructive,
                             Integer consumersBeforeDispatch,
                             Long delayBeforeDispatch,
                             String user) throws Exception {
      return updateQueue(name, routingType, filter, maxConsumers, purgeOnNoConsumers, exclusive, groupRebalance, groupBuckets, null, nonDestructive, consumersBeforeDispatch, delayBeforeDispatch, user, null);
   }

   @Override
   public String updateQueue(String name,
                             String routingType,
                             String filter,
                             Integer maxConsumers,
                             Boolean purgeOnNoConsumers,
                             Boolean exclusive,
                             Boolean groupRebalance,
                             Integer groupBuckets,
                             String groupFirstKey,
                             Boolean nonDestructive,
                             Integer consumersBeforeDispatch,
                             Long delayBeforeDispatch,
                             String user,
                             Long ringSize) throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.updateQueue(this.server, name, routingType, filter, maxConsumers, purgeOnNoConsumers, exclusive, groupRebalance, groupBuckets, groupFirstKey, nonDestructive, consumersBeforeDispatch, delayBeforeDispatch, user, ringSize);
      }
      checkStarted();

      clearIO();

      try {
         final Queue queue = server.updateQueue(name, routingType != null ? RoutingType.valueOf(routingType) : null, filter, maxConsumers, purgeOnNoConsumers, exclusive, groupRebalance, groupBuckets, groupFirstKey, nonDestructive, consumersBeforeDispatch, delayBeforeDispatch, user, ringSize);
         if (queue == null) {
            if (AuditLogger.isResourceLoggingEnabled()) {
               AuditLogger.updateQueueFailure(name, routingType);
            }
            throw ActiveMQMessageBundle.BUNDLE.noSuchQueue(SimpleString.of(name));
         }
         if (AuditLogger.isResourceLoggingEnabled()) {
            AuditLogger.updateQueueSuccess(name, routingType);
         }

         return QueueTextFormatter.Long.format(queue, new StringBuilder()).toString();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public int getQueueCount() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getQueueCount(this.server);
      }
      checkStarted();

      clearIO();
      try {
         Object[] queueControls = server.getManagementService().getResources(QueueControl.class);
         return queueControls.length;
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String[] getQueueNames() {
      return getQueueNames(null);
   }

   @Override
   public String[] getQueueNames(String routingType) {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getQueueNames(this.server, routingType);
      }
      checkStarted();

      clearIO();
      try {
         Object[] queueControls = server.getManagementService().getResources(QueueControl.class);
         List<String> names = new ArrayList<>();
         for (int i = 0; i < queueControls.length; i++) {
            QueueControl queueControl = (QueueControl) queueControls[i];
            if (routingType != null && routingType.length() > 1 && queueControl.getRoutingType().equals(routingType.toUpperCase())) {
               names.add(queueControl.getName());
            } else if (routingType == null || routingType.isEmpty()) {
               names.add(queueControl.getName());
            }
         }

         String[] result = new String[names.size()];
         return names.toArray(result);
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String[] getClusterConnectionNames() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getClusterConnectionNames(this.server);
      }
      checkStarted();

      clearIO();
      try {
         List<String> names = new ArrayList<>();
         for (ClusterConnection clusterConnection : server.getClusterManager().getClusterConnections()) {
            names.add(clusterConnection.getName().toString());
         }

         String[] result = new String[names.size()];
         return names.toArray(result);
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getUptime() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getUptime(this.server);
      }
      checkStarted();

      clearIO();
      try {
         return server.getUptime();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public long getUptimeMillis() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getUptimeMillis(this.server);
      }
      checkStarted();

      clearIO();
      try {
         return server.getUptimeMillis();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public boolean isReplicaSync() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.isReplicaSync(this.server);
      }
      checkStarted();

      clearIO();
      try {
         return server.isReplicaSync();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public int getAddressCount() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getAddressCount(this.server);
      }
      checkStarted();

      clearIO();
      try {
         Object[] addresses = server.getManagementService().getResources(AddressControl.class);
         return addresses.length;
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String[] getAddressNames() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getAddressNames(this.server);
      }
      checkStarted();

      clearIO();
      try {
         Object[] addresses = server.getManagementService().getResources(AddressControl.class);
         String[] names = new String[addresses.length];
         for (int i = 0; i < addresses.length; i++) {
            AddressControl address = (AddressControl) addresses[i];
            names[i] = address.getAddress();
         }
         return names;
      } finally {
         blockOnIO();
      }
   }

   @Override
   public void destroyQueue(final String name, final boolean removeConsumers, final boolean forceAutoDeleteAddress) throws Exception {
      // destroy might be a long running task, we prevent multiple running tasks in this case
      try (AutoCloseable lock = server.managementLock()) {
         if (AuditLogger.isBaseLoggingEnabled()) {
            AuditLogger.destroyQueue(this.server, null, null, name, removeConsumers, forceAutoDeleteAddress);
         }
         checkStarted();

         clearIO();
         try {
            SimpleString queueName = SimpleString.of(name);
            try {
               server.destroyQueue(queueName, null, !removeConsumers, removeConsumers, forceAutoDeleteAddress);
            } catch (Exception e) {
               if (AuditLogger.isResourceLoggingEnabled()) {
                  AuditLogger.destroyQueueFailure(name);
               }
               throw e;
            }
            if (AuditLogger.isResourceLoggingEnabled()) {
               AuditLogger.destroyQueueSuccess(name);
            }
         } finally {
            blockOnIO();
         }
      }
   }

   @Override
   public void destroyQueue(final String name, final boolean removeConsumers) throws Exception {
      destroyQueue(name, removeConsumers, false);
   }

   @Override
   public void destroyQueue(final String name) throws Exception {
      destroyQueue(name, false);
   }

   @Override
   public String getAddressInfo(String address) throws ActiveMQAddressDoesNotExistException {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getAddressInfo(this.server, address);
      }
      checkStarted();

      clearIO();
      try {
         final AddressInfo addressInfo = server.getAddressInfo(SimpleString.of(address));
         if (addressInfo == null) {
            throw ActiveMQMessageBundle.BUNDLE.addressDoesNotExist(SimpleString.of(address));
         } else {
            return AddressInfoTextFormatter.Long.format(addressInfo, new StringBuilder()).toString();
         }
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String listBindingsForAddress(String address) throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.listBindingsForAddress(this.server, address);
      }
      checkStarted();

      clearIO();
      try {
         final Bindings bindings = server.getPostOffice().lookupBindingsForAddress(SimpleString.of(address));
         return bindings == null ? "" : bindings.getBindings().stream().map(Binding::toManagementString).collect(Collectors.joining(","));
      } finally {
         blockOnIO();
      }
   }


   @Override
   public String listAddresses(String separator) throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.listAddresses(this.server, separator);
      }
      checkStarted();

      clearIO();
      try {
         final Set<SimpleString> addresses = server.getPostOffice().getAddresses();
         TreeSet<SimpleString> sortAddress = new TreeSet<>((o1, o2) -> o1.toString().compareToIgnoreCase(o2.toString()));

         sortAddress.addAll(addresses);

         StringBuilder result = new StringBuilder();
         for (SimpleString string : sortAddress) {
            if (!result.isEmpty()) {
               result.append(separator);
            }
            result.append(string);
         }

         return result.toString();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public int getConnectionCount() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getConnectionCount(this.server);
      }
      checkStarted();

      clearIO();
      try {
         return server.getConnectionCount();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public long getTotalConnectionCount() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getTotalConnectionCount(this.server);
      }
      checkStarted();

      clearIO();
      try {
         return server.getTotalConnectionCount();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public int getSessionCount() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getSessionCount(this.server);
      }
      checkStarted();

      clearIO();
      try {
         return server.getSessionCount();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public long getTotalSessionCount() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getTotalSessionCount(this.server);
      }
      checkStarted();

      clearIO();
      try {
         return server.getTotalSessionCount();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public long getTotalMessageCount() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getTotalMessageCount(this.server);
      }
      checkStarted();

      clearIO();
      try {
         return server.getTotalMessageCount();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public long getTotalMessagesAdded() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getTotalMessagesAdded(this.server);
      }
      checkStarted();

      clearIO();
      try {
         return server.getTotalMessagesAdded();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public long getTotalMessagesAcknowledged() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getTotalMessagesAcknowledged(this.server);
      }
      checkStarted();

      clearIO();
      try {
         return server.getTotalMessagesAcknowledged();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public long getTotalConsumerCount() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getTotalConsumerCount(this.server);
      }
      checkStarted();

      clearIO();
      try {
         return server.getTotalConsumerCount();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public void enableMessageCounters() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.enableMessageCounters(this.server);
      }
      checkStarted();

      clearIO();
      try {
         setMessageCounterEnabled(true);
      } finally {
         blockOnIO();
      }
   }

   @Override
   public void disableMessageCounters() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.disableMessageCounters(this.server);
      }
      checkStarted();

      clearIO();
      try {
         setMessageCounterEnabled(false);
      } finally {
         blockOnIO();
      }
   }

   @Override
   public void resetAllMessageCounters() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.resetAllMessageCounters(this.server);
      }
      checkStarted();

      clearIO();
      try {
         messageCounterManager.resetAllCounters();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public void resetAllMessageCounterHistories() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.resetAllMessageCounterHistories(this.server);
      }
      checkStarted();

      clearIO();
      try {
         messageCounterManager.resetAllCounterHistories();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public boolean isMessageCounterEnabled() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.isMessageCounterEnabled(this.server);
      }
      checkStarted();

      clearIO();
      try {
         return configuration.isMessageCounterEnabled();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public synchronized long getMessageCounterSamplePeriod() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getMessageCounterSamplePeriod(this.server);
      }
      checkStarted();

      clearIO();
      try {
         return messageCounterManager.getSamplePeriod();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public synchronized void setMessageCounterSamplePeriod(final long newPeriod) {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.setMessageCounterSamplePeriod(this.server, newPeriod);
      }

      checkStarted();

      clearIO();
      try {
         if (newPeriod < MessageCounterManagerImpl.MIN_SAMPLE_PERIOD) {
            if (newPeriod <= 0) {
               throw ActiveMQMessageBundle.BUNDLE.periodMustGreaterThanZero(newPeriod);
            }
            ActiveMQServerLogger.LOGGER.invalidMessageCounterPeriod(newPeriod);
         }

         if (messageCounterManager != null && newPeriod != messageCounterManager.getSamplePeriod()) {
            messageCounterManager.reschedule(newPeriod);
         }
      } finally {
         blockOnIO();
      }
   }

   @Override
   public int getMessageCounterMaxDayCount() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getMessageCounterMaxDayCount(this.server);
      }
      checkStarted();

      clearIO();
      try {
         return messageCounterManager.getMaxDayCount();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public void setMessageCounterMaxDayCount(final int count) {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.setMessageCounterMaxDayCount(this.server, count);
      }
      checkStarted();

      clearIO();
      try {
         if (count <= 0) {
            throw ActiveMQMessageBundle.BUNDLE.greaterThanZero(count);
         }
         messageCounterManager.setMaxDayCount(count);
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String[] listPreparedTransactions() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.listPreparedTransactions(this.server);
      }
      checkStarted();

      clearIO();
      try {
         DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM);

         Map<Xid, Long> xids = resourceManager.getPreparedTransactionsWithCreationTime();
         List<Entry<Xid, Long>> xidsSortedByCreationTime = new ArrayList<>(xids.entrySet());
         Collections.sort(xidsSortedByCreationTime, Entry.comparingByValue());
         String[] s = new String[xidsSortedByCreationTime.size()];
         int i = 0;
         for (Map.Entry<Xid, Long> entry : xidsSortedByCreationTime) {
            Date creation = new Date(entry.getValue());
            Xid xid = entry.getKey();
            s[i++] = dateFormat.format(creation) + " base64: " + XidImpl.toBase64String(xid) + " " + xid.toString();
         }
         return s;
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String listPreparedTransactionDetailsAsJSON() throws Exception {
      return listPreparedTransactionDetailsAsJSON((xid, tx, creation) -> new CoreTransactionDetail(xid, tx, creation));
   }

   public String listPreparedTransactionDetailsAsJSON(TransactionDetailFactory factory) throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.listPreparedTransactionDetailsAsJSON(this.server);
      }
      checkStarted();

      clearIO();
      try {
         Map<Xid, Long> xids = resourceManager.getPreparedTransactionsWithCreationTime();
         if (xids == null || xids.isEmpty()) {
            return "";
         }

         List<Entry<Xid, Long>> xidsSortedByCreationTime = new ArrayList<>(xids.entrySet());
         Collections.sort(xidsSortedByCreationTime, Entry.comparingByValue());

         JsonArrayBuilder txDetailListJson = JsonLoader.createArrayBuilder();
         for (Map.Entry<Xid, Long> entry : xidsSortedByCreationTime) {
            Xid xid = entry.getKey();

            Transaction tx = resourceManager.getTransaction(xid);

            if (tx == null) {
               continue;
            }

            TransactionDetail detail = factory.createTransactionDetail(xid, tx, entry.getValue());

            txDetailListJson.add(detail.toJSON());
         }
         return txDetailListJson.build().toString();
      } finally {
         blockOnIO();
      }
   }

   @Deprecated
   @Override
   public String listPreparedTransactionDetailsAsHTML() throws Exception {
      return listPreparedTransactionDetailsAsHTML((xid, tx, creation) -> new CoreTransactionDetail(xid, tx, creation));
   }

   @Deprecated
   public String listPreparedTransactionDetailsAsHTML(TransactionDetailFactory factory) throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.listPreparedTransactionDetailsAsHTML(this.server, factory);
      }
      checkStarted();

      clearIO();
      try {
         Map<Xid, Long> xids = resourceManager.getPreparedTransactionsWithCreationTime();
         if (xids == null || xids.isEmpty()) {
            return "<h3>*** Prepared Transaction Details ***</h3><p>No entry.</p>";
         }

         List<Entry<Xid, Long>> xidsSortedByCreationTime = new ArrayList<>(xids.entrySet());
         Collections.sort(xidsSortedByCreationTime, new Comparator<Entry<Xid, Long>>() {
            @Override
            public int compare(final Entry<Xid, Long> entry1, final Entry<Xid, Long> entry2) {
               // sort by creation time, oldest first
               return entry1.getValue().compareTo(entry2.getValue());
            }
         });

         StringBuilder html = new StringBuilder();
         html.append("<h3>*** Prepared Transaction Details ***</h3>");

         for (Map.Entry<Xid, Long> entry : xidsSortedByCreationTime) {
            Xid xid = entry.getKey();

            Transaction tx = resourceManager.getTransaction(xid);

            if (tx == null) {
               continue;
            }

            TransactionDetail detail = factory.createTransactionDetail(xid, tx, entry.getValue());

            JsonObject txJson = detail.toJSON();

            html.append("<table border=\"1\">");
            html.append("<tr><th>creation_time</th>");
            html.append("<td>" + txJson.get(TransactionDetail.KEY_CREATION_TIME) + "</td>");
            html.append("<th>xid_as_base_64</th>");
            html.append("<td colspan=\"3\">" + txJson.get(TransactionDetail.KEY_XID_AS_BASE64) + "</td></tr>");
            html.append("<tr><th>xid_format_id</th>");
            html.append("<td>" + txJson.get(TransactionDetail.KEY_XID_FORMAT_ID) + "</td>");
            html.append("<th>xid_global_txid</th>");
            html.append("<td>" + txJson.get(TransactionDetail.KEY_XID_GLOBAL_TXID) + "</td>");
            html.append("<th>xid_branch_qual</th>");
            html.append("<td>" + txJson.get(TransactionDetail.KEY_XID_BRANCH_QUAL) + "</td></tr>");

            html.append("<tr><th colspan=\"6\">Message List</th></tr>");
            html.append("<tr><td colspan=\"6\">");
            html.append("<table border=\"1\" cellspacing=\"0\" cellpadding=\"0\">");

            JsonArray msgs = txJson.getJsonArray(TransactionDetail.KEY_TX_RELATED_MESSAGES);
            for (int i = 0; i < msgs.size(); i++) {
               JsonObject msgJson = msgs.getJsonObject(i);
               JsonObject props = msgJson.getJsonObject(TransactionDetail.KEY_MSG_PROPERTIES);
               StringBuilder propstr = new StringBuilder();
               Set<String> keys = props.keySet();
               for (String key : keys) {
                  propstr.append(key);
                  propstr.append("=");
                  propstr.append(props.get(key));
                  propstr.append(", ");
               }

               html.append("<th>operation_type</th>");
               html.append("<td>" + msgJson.get(TransactionDetail.KEY_MSG_OP_TYPE) + "</th>");
               html.append("<th>message_type</th>");
               html.append("<td>" + msgJson.get(TransactionDetail.KEY_MSG_TYPE) + "</td></tr>");
               html.append("<tr><th>properties</th>");
               html.append("<td colspan=\"3\">" + propstr.toString() + "</td></tr>");
            }
            html.append("</table></td></tr>");
            html.append("</table><br>");
         }

         return html.toString();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String[] listHeuristicCommittedTransactions() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.listHeuristicCommittedTransactions(this.server);
      }
      checkStarted();

      clearIO();
      try {
         List<Xid> xids = resourceManager.getHeuristicCommittedTransactions();
         String[] s = new String[xids.size()];
         int i = 0;
         for (Xid xid : xids) {
            s[i++] = XidImpl.toBase64String(xid);
         }
         return s;
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String[] listHeuristicRolledBackTransactions() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.listHeuristicRolledBackTransactions(this.server);
      }
      checkStarted();

      clearIO();
      try {
         List<Xid> xids = resourceManager.getHeuristicRolledbackTransactions();
         String[] s = new String[xids.size()];
         int i = 0;
         for (Xid xid : xids) {
            s[i++] = XidImpl.toBase64String(xid);
         }
         return s;
      } finally {
         blockOnIO();
      }
   }

   @Override
   public synchronized boolean commitPreparedTransaction(final String transactionAsBase64) throws Exception {
      // commit might be a long running task if dealing with a large transaction
      // ensuring a single one just in case
      try (AutoCloseable lock = server.managementLock()) {
         if (AuditLogger.isBaseLoggingEnabled()) {
            AuditLogger.commitPreparedTransaction(this.server, transactionAsBase64);
         }
         checkStarted();

         clearIO();
         try {
            List<Xid> xids = resourceManager.getPreparedTransactions();

            for (Xid xid : xids) {
               if (XidImpl.toBase64String(xid).equals(transactionAsBase64)) {
                  Transaction transaction = resourceManager.removeTransaction(xid, null);
                  transaction.commit(false);
                  long recordID = server.getStorageManager().storeHeuristicCompletion(xid, true);
                  storageManager.waitOnOperations();
                  resourceManager.putHeuristicCompletion(recordID, xid, true);
                  return true;
               }
            }
            return false;
         } finally {
            blockOnIO();
         }
      }
   }

   @Override
   public synchronized boolean rollbackPreparedTransaction(final String transactionAsBase64) throws Exception {
      // rollback might be a long running task if dealing with a large transaction
      // ensuring a single task just in case
      try (AutoCloseable lock = server.managementLock()) {
         if (AuditLogger.isBaseLoggingEnabled()) {
            AuditLogger.rollbackPreparedTransaction(this.server, transactionAsBase64);
         }
         checkStarted();

         clearIO();
         try {

            List<Xid> xids = resourceManager.getPreparedTransactions();

            for (Xid xid : xids) {
               if (XidImpl.toBase64String(xid).equals(transactionAsBase64)) {
                  Transaction transaction = resourceManager.removeTransaction(xid, null);
                  transaction.rollback();
                  long recordID = server.getStorageManager().storeHeuristicCompletion(xid, false);
                  server.getStorageManager().waitOnOperations();
                  resourceManager.putHeuristicCompletion(recordID, xid, false);
                  return true;
               }
            }
            return false;
         } finally {
            blockOnIO();
         }
      }
   }

   @Override
   public String[] listRemoteAddresses() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.listRemoteAddresses(this.server);
      }
      checkStarted();

      clearIO();
      try {
         Set<RemotingConnection> connections = remotingService.getConnections();

         String[] remoteAddresses = new String[connections.size()];
         int i = 0;
         for (RemotingConnection connection : connections) {
            remoteAddresses[i++] = connection.getRemoteAddress();
         }
         return remoteAddresses;
      } finally {
         blockOnIO();
      }

   }

   @Override
   public String[] listRemoteAddresses(final String ipAddress) {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.listRemoteAddresses(this.server, ipAddress);
      }
      checkStarted();

      clearIO();
      try {
         Set<RemotingConnection> connections = remotingService.getConnections();
         List<String> remoteConnections = new ArrayList<>();
         for (RemotingConnection connection : connections) {
            String remoteAddress = connection.getRemoteAddress();
            if (remoteAddress.contains(ipAddress)) {
               remoteConnections.add(connection.getRemoteAddress());
            }
         }
         return remoteConnections.toArray(new String[remoteConnections.size()]);
      } finally {
         blockOnIO();
      }

   }

   @Override
   public boolean closeConnectionsForAddress(final String ipAddress) {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.closeConnectionsForAddress(this.server, ipAddress);
      }
      checkStarted();

      clearIO();
      try {
         boolean closed = false;
         Set<RemotingConnection> connections = remotingService.getConnections();
         for (RemotingConnection connection : connections) {
            String remoteAddress = connection.getRemoteAddress();
            if (remoteAddress.contains(ipAddress)) {
               connection.fail(ActiveMQMessageBundle.BUNDLE.connectionsClosedByManagement(ipAddress));
               remotingService.removeConnection(connection.getID());
               closed = true;
            }
         }

         return closed;
      } finally {
         blockOnIO();
      }

   }

   @Override
   public boolean closeConsumerConnectionsForAddress(final String address) {
      // this could be a long running task, ensuring a single task
      try (AutoCloseable lock = server.managementLock()) {
         if (AuditLogger.isBaseLoggingEnabled()) {
            AuditLogger.closeConsumerConnectionsForAddress(this.server, address);
         }
         boolean closed = false;
         checkStarted();

         clearIO();
         try {
            for (Binding binding : postOffice.getMatchingBindings(SimpleString.of(address))) {
               if (binding instanceof LocalQueueBinding localQueueBinding) {
                  Queue queue = localQueueBinding.getQueue();
                  for (Consumer consumer : queue.getConsumers()) {
                     if (consumer instanceof ServerConsumer serverConsumer) {
                        RemotingConnection connection = null;

                        for (RemotingConnection potentialConnection : remotingService.getConnections()) {
                           if (potentialConnection.getID().toString().equals(String.valueOf(serverConsumer.getConnectionID()))) {
                              connection = potentialConnection;
                           }
                        }

                        if (connection != null) {
                           remotingService.removeConnection(connection.getID());
                           connection.fail(ActiveMQMessageBundle.BUNDLE.consumerConnectionsClosedByManagement(address));
                           closed = true;
                        }
                     }
                  }
               }
            }
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.failedToCloseConsumerConnectionsForAddress(address, e);
         } finally {
            blockOnIO();
         }
         return closed;
      } catch (Throwable e) {
         throw new RuntimeException(e.getMessage(), e);
      }
   }

   @Override
   public boolean closeConnectionsForUser(final String userName) {
      // possibly a long running task
      try (AutoCloseable lock = server.managementLock()) {
         if (AuditLogger.isBaseLoggingEnabled()) {
            AuditLogger.closeConnectionsForUser(this.server, userName);
         }
         boolean closed = false;
         checkStarted();

         clearIO();
         try {
            for (ServerSession serverSession : server.getSessions()) {
               if (serverSession.getUsername() != null && serverSession.getUsername().equals(userName)) {
                  RemotingConnection connection = null;

                  for (RemotingConnection potentialConnection : remotingService.getConnections()) {
                     if (potentialConnection.getID().toString().equals(serverSession.getConnectionID().toString())) {
                        connection = potentialConnection;
                     }
                  }

                  if (connection != null) {
                     remotingService.removeConnection(connection.getID());
                     connection.fail(ActiveMQMessageBundle.BUNDLE.connectionsForUserClosedByManagement(userName));
                     closed = true;
                  }
               }
            }
         } finally {
            blockOnIO();
         }
         return closed;
      } catch (Throwable e) {
         throw new RuntimeException(e.getMessage(), e);
      }
   }

   @Override
   public boolean closeConnectionWithID(final String ID) {
      // possibly a long running task
      try (AutoCloseable lock = server.managementLock()) {
         if (AuditLogger.isBaseLoggingEnabled()) {
            AuditLogger.closeConnectionWithID(this.server, ID);
         }
         checkStarted();

         clearIO();
         try {
            for (RemotingConnection connection : remotingService.getConnections()) {
               if (connection.getID().toString().equals(ID)) {
                  remotingService.removeConnection(connection.getID());
                  connection.fail(ActiveMQMessageBundle.BUNDLE.connectionWithIDClosedByManagement(ID));
                  return true;
               }
            }
         } finally {
            blockOnIO();
         }
         return false;
      } catch (Throwable e) {
         throw new RuntimeException(e.getMessage(), e);
      }
   }

   @Override
   public boolean closeSessionWithID(final String connectionID, final String ID) throws Exception {
      return closeSessionWithID(connectionID, ID, false);
   }

   @Override
   public boolean closeSessionWithID(final String connectionID, final String ID, final boolean force) throws Exception {
      // possibly a long running task
      try (AutoCloseable lock = server.managementLock()) {
         if (AuditLogger.isBaseLoggingEnabled()) {
            AuditLogger.closeSessionWithID(this.server, connectionID, ID);
         }
         checkStarted();

         clearIO();
         try {
            List<ServerSession> sessions = server.getSessions(connectionID);
            for (ServerSession session : sessions) {
               if (session.getName().equals(ID)) {
                  for (ServerConsumer consumer : session.getServerConsumers()) {
                     try {
                        consumer.disconnect();
                     } catch (Exception e) {
                        ActiveMQServerLogger.LOGGER.unexpectedResultDisconnectingConsumer(consumer.getID(), consumer.getSessionID(), e.getMessage());
                     }
                  }
                  session.close(true, force);
                  return true;
               }
            }

         } finally {
            blockOnIO();
         }
         return false;
      }
   }

   @Override
   public boolean closeConsumerWithID(final String sessionID, final String ID) throws Exception {
      // possibly a long running task
      try (AutoCloseable lock = server.managementLock()) {
         if (AuditLogger.isBaseLoggingEnabled()) {
            AuditLogger.closeConsumerWithID(this.server, sessionID, ID);
         }
         checkStarted();

         clearIO();
         try {
            Set<ServerSession> sessions = server.getSessions();
            for (ServerSession session : sessions) {
               if (session.getName().equals(sessionID)) {
                  Set<ServerConsumer> serverConsumers = session.getServerConsumers();
                  for (ServerConsumer serverConsumer : serverConsumers) {
                     if (serverConsumer.sequentialID() == Long.parseLong(ID)) {
                        serverConsumer.disconnect();
                        return true;
                     }
                  }
               }
            }

         } finally {
            blockOnIO();
         }
         return false;
      }
   }

   @Override
   public String[] listConnectionIDs() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.listConnectionIDs(this.server);
      }
      checkStarted();

      clearIO();
      try {
         Set<RemotingConnection> connections = remotingService.getConnections();
         String[] connectionIDs = new String[connections.size()];
         int i = 0;
         for (RemotingConnection connection : connections) {
            connectionIDs[i++] = connection.getID().toString();
         }
         return connectionIDs;
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String[] listSessions(final String connectionID) {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.listSessions(this.server, connectionID);
      }
      checkStarted();

      clearIO();
      try {
         List<ServerSession> sessions = server.getSessions(connectionID);
         String[] sessionIDs = new String[sessions.size()];
         int i = 0;
         for (ServerSession serverSession : sessions) {
            sessionIDs[i++] = serverSession.getName();
         }
         return sessionIDs;
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String listProducersInfoAsJSON() throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.listProducersInfoAsJSON(this.server);
      }
      JsonArrayBuilder producers = JsonLoader.createArrayBuilder();

      for (ServerSession session : server.getSessions()) {
         session.describeProducersInfo(producers);
      }

      return producers.build().toString();
   }

   @Override
   public String listConnections(String options, int page, int pageSize) throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.listConnections(this.server, options, page, pageSize);
      }
      checkStarted();
      clearIO();
      try {
         server.getPostOffice().getAddresses();
         ConnectionView view = new ConnectionView(server);
         view.setCollection(server.getRemotingService().getConnections());
         view.setOptions(options);
         return view.getResultsAsJson(page, pageSize);
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String listSessions(String options, int page, int pageSize) throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.listSessions(this.server, options, page, pageSize);
      }
      checkStarted();
      clearIO();
      try {
         SessionView view = new SessionView();
         view.setCollection(server.getSessions());
         view.setOptions(options);
         return view.getResultsAsJson(page, pageSize);
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String listConsumers(String options, int page, int pageSize) throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.listConsumers(this.server, options, page, pageSize);
      }
      checkStarted();
      clearIO();
      try {
         Set<ServerConsumer> consumers = new HashSet();
         for (ServerSession session : server.getSessions()) {
            consumers.addAll(session.getServerConsumers());
         }
         ConsumerView view = new ConsumerView(server);
         view.setCollection(consumers);
         view.setOptions(options);
         return view.getResultsAsJson(page, pageSize);
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String listAddresses(String options, int page, int pageSize) throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.listAddresses(this.server, options, page, pageSize);
      }
      checkStarted();
      clearIO();
      try {
         List<AddressControl> addresses = new ArrayList<>();
         Object[] qs = server.getManagementService().getResources(AddressControl.class);
         for (int i = 0; i < qs.length; i++) {
            addresses.add((AddressControl) qs[i]);
         }
         AddressView view = new AddressView(server);
         view.setCollection(addresses);
         view.setOptions(options);
         return view.getResultsAsJson(page, pageSize);
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String listQueues(String options, int page, int pageSize) throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.listQueues(this.server, options, page, pageSize);
      }
      checkStarted();

      clearIO();
      try {
         List<QueueControl> queues = new ArrayList<>();
         Object[] qs = server.getManagementService().getResources(QueueControl.class);
         for (int i = 0; i < qs.length; i++) {
            queues.add((QueueControl) qs[i]);
         }
         QueueView view = new QueueView(server);
         view.setCollection(queues);
         view.setOptions(options);
         return view.getResultsAsJson(page, pageSize);
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String listProducers(@Parameter(name = "Options") String options,
                               @Parameter(name = "Page Number") int page,
                               @Parameter(name = "Page Size") int pageSize) throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.listProducers(this.server, options, page, pageSize);
      }
      checkStarted();
      clearIO();
      try {
         Set<ServerProducer> producers = new HashSet<>();
         for (ServerSession session : server.getSessions()) {
            producers.addAll(session.getServerProducers());
         }
         ProducerView view = new ProducerView(server);
         view.setCollection(producers);
         view.setOptions(options);
         return view.getResultsAsJson(page, pageSize);
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String listConnectionsAsJSON() throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.listConnectionsAsJSON(this.server);
      }
      checkStarted();

      clearIO();

      try {
         JsonArrayBuilder array = JsonLoader.createArrayBuilder();

         Set<RemotingConnection> connections = server.getRemotingService().getConnections();

         for (RemotingConnection connection : connections) {
            JsonObjectBuilder obj = JsonLoader.createObjectBuilder().add("connectionID", connection.getID().toString()).add("clientAddress", connection.getRemoteAddress()).add("creationTime", connection.getCreationTime()).add("implementation", connection.getClass().getSimpleName()).add("sessionCount", server.getSessions(connection.getID().toString()).size());
            array.add(obj);
         }
         return array.build().toString();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String listSessionsAsJSON(final String connectionID) throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.listSessionsAsJSON(this.server, connectionID);
      }
      checkStarted();

      clearIO();

      JsonArrayBuilder array = JsonLoader.createArrayBuilder();
      try {
         List<ServerSession> sessions = server.getSessions(connectionID);
         for (ServerSession sess : sessions) {
            buildSessionJSON(array, sess);
         }
      } finally {
         blockOnIO();
      }
      return array.build().toString();
   }

   @Override
   public String listAllSessionsAsJSON() throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.listAllSessionsAsJSON(this.server);
      }
      checkStarted();

      clearIO();

      JsonArrayBuilder array = JsonLoader.createArrayBuilder();
      try {
         Set<ServerSession> sessions = server.getSessions();
         for (ServerSession sess : sessions) {
            buildSessionJSON(array, sess);
         }
      } finally {
         blockOnIO();
      }
      return array.build().toString();
   }

   public void buildSessionJSON(JsonArrayBuilder array, ServerSession sess) {
      JsonObjectBuilder obj = JsonLoader.createObjectBuilder().add("sessionID", sess.getName()).add("creationTime", sess.getCreationTime()).add("consumerCount", sess.getServerConsumers().size());

      if (sess.getValidatedUser() != null) {
         obj.add("validatedUser", sess.getValidatedUser());
         obj.add("principal", sess.getValidatedUser());
      }

      if (sess.getMetaData() != null) {
         final JsonObjectBuilder metadata = JsonLoader.createObjectBuilder();
         for (Entry<String, String> entry : sess.getMetaData().entrySet()) {
            metadata.add(entry.getKey(), entry.getValue());
         }
         obj.add("metadata", metadata);
      }

      array.add(obj);
   }

   @Override
   public String listConsumersAsJSON(String connectionID) throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.listConsumersAsJSON(this.server, connectionID);
      }
      checkStarted();

      clearIO();

      try {
         JsonArrayBuilder array = JsonLoader.createArrayBuilder();

         Set<RemotingConnection> connections = server.getRemotingService().getConnections();
         for (RemotingConnection connection : connections) {
            if (connectionID.equals(connection.getID().toString())) {
               List<ServerSession> sessions = server.getSessions(connectionID);
               for (ServerSession session : sessions) {
                  Set<ServerConsumer> consumers = session.getServerConsumers();
                  for (ServerConsumer consumer : consumers) {
                     JsonObject obj = toJSONObject(consumer);
                     if (obj != null) {
                        array.add(obj);
                     }
                  }
               }
            }
         }
         return array.build().toString();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String listAllConsumersAsJSON() throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.listAllConsumersAsJSON(this.server);
      }
      checkStarted();

      clearIO();

      try {
         JsonArrayBuilder array = JsonLoader.createArrayBuilder();

         Set<ServerSession> sessions = server.getSessions();
         for (ServerSession session : sessions) {
            Set<ServerConsumer> consumers = session.getServerConsumers();
            for (ServerConsumer consumer : consumers) {
               JsonObject obj = toJSONObject(consumer);
               if (obj != null) {
                  array.add(obj);
               }
            }
         }
         return array.build().toString();
      } finally {
         blockOnIO();
      }
   }

   private JsonObject toJSONObject(ServerConsumer consumer) throws Exception {
      List<MessageReference> deliveringMessages = consumer.getDeliveringMessages();
      JsonObjectBuilder obj = JsonLoader.createObjectBuilder()
            .add(ConsumerField.ID.getAlternativeName(), consumer.getID())
            .add(ConsumerField.SEQUENTIAL_ID.getAlternativeName(), consumer.getSequentialID())
            .add(ConsumerField.CONNECTION.getAlternativeName(), consumer.getConnectionID().toString())
            .add(ConsumerField.SESSION.getAlternativeName(), consumer.getSessionID())
            .add(ConsumerField.QUEUE.getAlternativeName(), consumer.getQueue().getName().toString())
            .add(ConsumerField.BROWSE_ONLY.getName(), consumer.isBrowseOnly())
            .add(ConsumerField.CREATION_TIME.getName(), consumer.getCreationTime())
            // deliveringCount is renamed as MESSAGES_IN_TRANSIT but left in json for backward compatibility
            .add(ConsumerField.MESSAGES_IN_TRANSIT.getAlternativeName(), consumer.getMessagesInTransit())
            .add(ConsumerField.MESSAGES_IN_TRANSIT.getName(), consumer.getMessagesInTransit())
            .add(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName(), consumer.getMessagesInTransitSize())
            .add(ConsumerField.MESSAGES_DELIVERED.getName(), consumer.getMessagesDelivered())
            .add(ConsumerField.MESSAGES_DELIVERED_SIZE.getName(), consumer.getMessagesDeliveredSize())
            .add(ConsumerField.MESSAGES_ACKNOWLEDGED.getName(), consumer.getMessagesAcknowledged())
            .add(ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName(), consumer.getMessagesAcknowledgedAwaitingCommit())
            .add(ConsumerField.LAST_DELIVERED_TIME.getName(), consumer.getLastDeliveredTime())
            .add(ConsumerField.LAST_ACKNOWLEDGED_TIME.getName(), consumer.getLastAcknowledgedTime())
            .add(ConsumerField.STATUS.getName(), ConsumerView.checkConsumerStatus(consumer, server));
      if (consumer.getFilter() != null) {
         obj.add("filter", consumer.getFilter().getFilterString().toString());
      }

      return obj.build();
   }

   @Override
   public Object[] getConnectors() throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getConnectors(this.server);
      }
      return getNetworkConfigs(configuration.getConnectorConfigurations().values());
   }

   @Override
   public Object[] getAcceptors() throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getAcceptors(this.server);
      }
      return getNetworkConfigs(configuration.getAcceptorConfigurations());
   }

   private Object[] getNetworkConfigs(Collection<TransportConfiguration> configs) throws Exception {
      checkStarted();

      clearIO();
      try {
         Object[] ret = new Object[configs.size()];

         int i = 0;
         for (TransportConfiguration config : configs) {
            Object[] tc = new Object[3];

            tc[0] = config.getName();
            tc[1] = config.getFactoryClassName();
            tc[2] = config.getParams();

            ret[i++] = tc;
         }
         return ret;
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getConnectorsAsJSON() throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getConnectorsAsJSON(this.server);
      }

      return getNetworkConfigsAsJSON(configuration.getConnectorConfigurations().values());
   }

   @Override
   public String getAcceptorsAsJSON() throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getAcceptorsAsJSON(this.server);
      }

      return getNetworkConfigsAsJSON(configuration.getAcceptorConfigurations());
   }

   private String getNetworkConfigsAsJSON(Collection<TransportConfiguration> configs) throws Exception {
      checkStarted();

      clearIO();
      try {
         JsonArrayBuilder array = JsonLoader.createArrayBuilder();

         for (TransportConfiguration config : configs) {
            array.add(config.toJson());
         }

         return array.build().toString();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public void addSecuritySettings(final String addressMatch,
                                   final String sendRoles,
                                   final String consumeRoles,
                                   final String createDurableQueueRoles,
                                   final String deleteDurableQueueRoles,
                                   final String createNonDurableQueueRoles,
                                   final String deleteNonDurableQueueRoles,
                                   final String manageRoles) throws Exception {
      addSecuritySettings(addressMatch, sendRoles, consumeRoles, createDurableQueueRoles, deleteDurableQueueRoles, createNonDurableQueueRoles, deleteNonDurableQueueRoles, manageRoles, "");
   }

   @Override
   public void addSecuritySettings(final String addressMatch,
                                   final String sendRoles,
                                   final String consumeRoles,
                                   final String createDurableQueueRoles,
                                   final String deleteDurableQueueRoles,
                                   final String createNonDurableQueueRoles,
                                   final String deleteNonDurableQueueRoles,
                                   final String manageRoles,
                                   final String browseRoles) throws Exception {
      addSecuritySettings(addressMatch, sendRoles, consumeRoles, createDurableQueueRoles, deleteDurableQueueRoles, createNonDurableQueueRoles, deleteNonDurableQueueRoles, manageRoles, browseRoles, "", "");
   }

   @Override
   public void addSecuritySettings(final String addressMatch,
                                   final String sendRoles,
                                   final String consumeRoles,
                                   final String createDurableQueueRoles,
                                   final String deleteDurableQueueRoles,
                                   final String createNonDurableQueueRoles,
                                   final String deleteNonDurableQueueRoles,
                                   final String manageRoles,
                                   final String browseRoles,
                                   final String createAddressRoles,
                                   final String deleteAddressRoles) throws Exception {
      addSecuritySettings(addressMatch, sendRoles, consumeRoles, createDurableQueueRoles, deleteDurableQueueRoles, createNonDurableQueueRoles, deleteNonDurableQueueRoles, manageRoles, browseRoles, createAddressRoles, deleteAddressRoles, "", "");
   }

   @Override
   public void addSecuritySettings(final String addressMatch,
                                   final String sendRoles,
                                   final String consumeRoles,
                                   final String createDurableQueueRoles,
                                   final String deleteDurableQueueRoles,
                                   final String createNonDurableQueueRoles,
                                   final String deleteNonDurableQueueRoles,
                                   final String manageRoles,
                                   final String browseRoles,
                                   final String createAddressRoles,
                                   final String deleteAddressRoles,
                                   final String viewRoles,
                                   final String editRoles) throws Exception {
      addSecuritySettings(addressMatch, SecurityFormatter.toJSON(sendRoles, consumeRoles, createDurableQueueRoles, deleteDurableQueueRoles, createNonDurableQueueRoles, deleteNonDurableQueueRoles, manageRoles, browseRoles, createAddressRoles, deleteAddressRoles, viewRoles, editRoles));
   }

   @Override
   public void addSecuritySettings(final String addressMatch,
                                   final String securitySettingsAsJson) throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.addSecuritySettings(this.server, addressMatch, securitySettingsAsJson);
      }
      checkStarted();

      clearIO();
      try {
         JsonObject o = JsonUtil.readJsonObject(securitySettingsAsJson);
         server.getSecurityRepository().addMatch(addressMatch, SecurityFormatter.fromJSON(o));
         storageManager.storeSecuritySetting(new PersistedSecuritySetting(addressMatch, o));
      } finally {
         blockOnIO();
      }
   }

   @Override
   public void removeSecuritySettings(final String addressMatch) throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.removeSecuritySettings(this.server, addressMatch);
      }
      checkStarted();

      clearIO();
      try {
         server.getSecurityRepository().removeMatch(addressMatch);
         storageManager.deleteSecuritySetting(SimpleString.of(addressMatch));
      } finally {
         blockOnIO();
      }
   }

   @Override
   public Object[] getRoles(final String addressMatch) throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getRoles(this.server, addressMatch);
      }

      checkStarted();

      clearIO();
      try {
         Set<Role> roles = server.getSecurityRepository().getMatch(addressMatch);

         Object[] objRoles = new Object[roles.size()];

         int i = 0;
         for (Role role : roles) {
            objRoles[i++] = CheckType.asObjectArray(role);
         }
         return objRoles;
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getRolesAsJSON(final String addressMatch) throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getRolesAsJSON(this.server, addressMatch);
      }
      checkStarted();

      clearIO();
      try {
         JsonArrayBuilder json = JsonLoader.createArrayBuilder();
         Set<Role> roles = server.getSecurityRepository().getMatch(addressMatch);

         for (Role role : roles) {
            json.add(role.toJson());
         }
         return json.build().toString();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getAddressSettingsAsJSON(final String address) throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getAddressSettingsAsJSON(this.server, address);
      }
      checkStarted();

      AddressSettings addressSettings = server.getAddressSettingsRepository().getMatch(address);
      return addressSettings.toJSON();
   }

   @Override
   @Deprecated
   public void addAddressSettings(final String address,
                                  final String DLA,
                                  final String expiryAddress,
                                  final long expiryDelay,
                                  final boolean lastValueQueue,
                                  final int deliveryAttempts,
                                  final long maxSizeBytes,
                                  final int pageSizeBytes,
                                  final int pageMaxCacheSize,
                                  final long redeliveryDelay,
                                  final double redeliveryMultiplier,
                                  final long maxRedeliveryDelay,
                                  final long redistributionDelay,
                                  final boolean sendToDLAOnNoRoute,
                                  final String addressFullMessagePolicy,
                                  final long slowConsumerThreshold,
                                  final long slowConsumerCheckPeriod,
                                  final String slowConsumerPolicy,
                                  final boolean autoCreateJmsQueues,
                                  final boolean autoDeleteJmsQueues,
                                  final boolean autoCreateJmsTopics,
                                  final boolean autoDeleteJmsTopics) throws Exception {
      addAddressSettings(address,
                         DLA,
                         expiryAddress,
                         expiryDelay,
                         lastValueQueue,
                         deliveryAttempts,
                         maxSizeBytes,
                         pageSizeBytes,
                         pageMaxCacheSize,
                         redeliveryDelay,
                         redeliveryMultiplier,
                         maxRedeliveryDelay,
                         redistributionDelay,
                         sendToDLAOnNoRoute,
                         addressFullMessagePolicy,
                         slowConsumerThreshold,
                         slowConsumerCheckPeriod,
                         slowConsumerPolicy,
                         autoCreateJmsQueues,
                         autoDeleteJmsQueues,
                         autoCreateJmsTopics,
                         autoDeleteJmsTopics,
                         AddressSettings.DEFAULT_AUTO_CREATE_QUEUES,
                         AddressSettings.DEFAULT_AUTO_DELETE_QUEUES,
                         AddressSettings.DEFAULT_AUTO_CREATE_ADDRESSES,
                         AddressSettings.DEFAULT_AUTO_DELETE_ADDRESSES);
   }

   @Override
   @Deprecated
   public void addAddressSettings(final String address,
                                  final String DLA,
                                  final String expiryAddress,
                                  final long expiryDelay,
                                  final boolean defaultLastValueQueue,
                                  final int maxDeliveryAttempts,
                                  final long maxSizeBytes,
                                  final int pageSizeBytes,
                                  final int pageMaxCacheSize,
                                  final long redeliveryDelay,
                                  final double redeliveryMultiplier,
                                  final long maxRedeliveryDelay,
                                  final long redistributionDelay,
                                  final boolean sendToDLAOnNoRoute,
                                  final String addressFullMessagePolicy,
                                  final long slowConsumerThreshold,
                                  final long slowConsumerCheckPeriod,
                                  final String slowConsumerPolicy,
                                  final boolean autoCreateJmsQueues,
                                  final boolean autoDeleteJmsQueues,
                                  final boolean autoCreateJmsTopics,
                                  final boolean autoDeleteJmsTopics,
                                  final boolean autoCreateQueues,
                                  final boolean autoDeleteQueues,
                                  final boolean autoCreateAddresses,
                                  final boolean autoDeleteAddresses) throws Exception {
      addAddressSettings(address,
                         DLA,
                         expiryAddress,
                         expiryDelay,
                         defaultLastValueQueue,
                         maxDeliveryAttempts,
                         maxSizeBytes,
                         pageSizeBytes,
                         pageMaxCacheSize,
                         redeliveryDelay,
                         redeliveryMultiplier,
                         maxRedeliveryDelay,
                         redistributionDelay,
                         sendToDLAOnNoRoute,
                         addressFullMessagePolicy,
                         slowConsumerThreshold,
                         slowConsumerCheckPeriod,
                         slowConsumerPolicy,
                         autoCreateJmsQueues,
                         autoDeleteJmsQueues,
                         autoCreateJmsTopics,
                         autoDeleteJmsTopics,
                         autoCreateQueues,
                         autoDeleteQueues,
                         autoCreateAddresses,
                         autoDeleteAddresses,
                         AddressSettings.DEFAULT_CONFIG_DELETE_QUEUES.toString(),
                         AddressSettings.DEFAULT_CONFIG_DELETE_ADDRESSES.toString(),
                         AddressSettings.DEFAULT_ADDRESS_REJECT_THRESHOLD,
                         Objects.toString(ActiveMQDefaultConfiguration.getDefaultLastValueKey(), null),
                         ActiveMQDefaultConfiguration.getDefaultNonDestructive(),
                         ActiveMQDefaultConfiguration.getDefaultExclusive(),
                         ActiveMQDefaultConfiguration.getDefaultGroupRebalance(),
                         ActiveMQDefaultConfiguration.getDefaultGroupBuckets(),
                         Objects.toString(ActiveMQDefaultConfiguration.getDefaultGroupFirstKey(), null),
                         ActiveMQDefaultConfiguration.getDefaultMaxQueueConsumers(),
                         ActiveMQDefaultConfiguration.getDefaultPurgeOnNoConsumers(),
                         ActiveMQDefaultConfiguration.getDefaultConsumersBeforeDispatch(),
                         ActiveMQDefaultConfiguration.getDefaultDelayBeforeDispatch(),
                         ActiveMQDefaultConfiguration.getDefaultRoutingType().toString(),
                         ActiveMQDefaultConfiguration.getDefaultRoutingType().toString(),
                         ActiveMQClient.DEFAULT_CONSUMER_WINDOW_SIZE,
                         ActiveMQDefaultConfiguration.getDefaultRingSize(),
                         AddressSettings.DEFAULT_AUTO_DELETE_CREATED_QUEUES,
                         AddressSettings.DEFAULT_AUTO_DELETE_QUEUES_DELAY,
                         AddressSettings.DEFAULT_AUTO_DELETE_QUEUES_MESSAGE_COUNT,
                         AddressSettings.DEFAULT_AUTO_DELETE_ADDRESSES_DELAY,
                         AddressSettings.DEFAULT_REDELIVER_COLLISION_AVOIDANCE_FACTOR,
                         ActiveMQDefaultConfiguration.getDefaultRetroactiveMessageCount());
   }

   @Override
   @Deprecated
   public void addAddressSettings(final String address,
                                  final String DLA,
                                  final String expiryAddress,
                                  final long expiryDelay,
                                  final boolean defaultLastValueQueue,
                                  final int maxDeliveryAttempts,
                                  final long maxSizeBytes,
                                  final int pageSizeBytes,
                                  final int pageMaxCacheSize,
                                  final long redeliveryDelay,
                                  final double redeliveryMultiplier,
                                  final long maxRedeliveryDelay,
                                  final long redistributionDelay,
                                  final boolean sendToDLAOnNoRoute,
                                  final String addressFullMessagePolicy,
                                  final long slowConsumerThreshold,
                                  final long slowConsumerCheckPeriod,
                                  final String slowConsumerPolicy,
                                  final boolean autoCreateJmsQueues,
                                  final boolean autoDeleteJmsQueues,
                                  final boolean autoCreateJmsTopics,
                                  final boolean autoDeleteJmsTopics,
                                  final boolean autoCreateQueues,
                                  final boolean autoDeleteQueues,
                                  final boolean autoCreateAddresses,
                                  final boolean autoDeleteAddresses,
                                  final String configDeleteQueues,
                                  final String configDeleteAddresses,
                                  final long maxSizeBytesRejectThreshold,
                                  final String defaultLastValueKey,
                                  final boolean defaultNonDestructive,
                                  final boolean defaultExclusiveQueue,
                                  final boolean defaultGroupRebalance,
                                  final int defaultGroupBuckets,
                                  final String defaultGroupFirstKey,
                                  final int defaultMaxConsumers,
                                  final boolean defaultPurgeOnNoConsumers,
                                  final int defaultConsumersBeforeDispatch,
                                  final long defaultDelayBeforeDispatch,
                                  final String defaultQueueRoutingType,
                                  final String defaultAddressRoutingType,
                                  final int defaultConsumerWindowSize,
                                  final long defaultRingSize,
                                  final boolean autoDeleteCreatedQueues,
                                  final long autoDeleteQueuesDelay,
                                  final long autoDeleteQueuesMessageCount,
                                  final long autoDeleteAddressesDelay,
                                  final double redeliveryCollisionAvoidanceFactor,
                                  final long retroactiveMessageCount) throws Exception {
      addAddressSettings(address,
                         DLA,
                         expiryAddress,
                         expiryDelay,
                         defaultLastValueQueue,
                         maxDeliveryAttempts,
                         maxSizeBytes,
                         pageSizeBytes,
                         pageMaxCacheSize,
                         redeliveryDelay,
                         redeliveryMultiplier,
                         maxRedeliveryDelay,
                         redistributionDelay,
                         sendToDLAOnNoRoute,
                         addressFullMessagePolicy,
                         slowConsumerThreshold,
                         slowConsumerCheckPeriod,
                         slowConsumerPolicy,
                         autoCreateJmsQueues,
                         autoDeleteJmsQueues,
                         autoCreateJmsTopics,
                         autoDeleteJmsTopics,
                         autoCreateQueues,
                         autoDeleteQueues,
                         autoCreateAddresses,
                         autoDeleteAddresses,
                         configDeleteQueues,
                         configDeleteAddresses,
                         maxSizeBytesRejectThreshold,
                         defaultLastValueKey,
                         defaultNonDestructive,
                         defaultExclusiveQueue,
                         defaultGroupRebalance,
                         defaultGroupBuckets,
                         defaultGroupFirstKey,
                         defaultMaxConsumers,
                         defaultPurgeOnNoConsumers,
                         defaultConsumersBeforeDispatch,
                         defaultDelayBeforeDispatch,
                         defaultQueueRoutingType,
                         defaultAddressRoutingType,
                         defaultConsumerWindowSize,
                         defaultRingSize,
                         autoDeleteCreatedQueues,
                         autoDeleteQueuesDelay,
                         autoDeleteQueuesMessageCount,
                         autoDeleteAddressesDelay,
                         redeliveryCollisionAvoidanceFactor,
                         retroactiveMessageCount,
                         AddressSettings.DEFAULT_AUTO_CREATE_DEAD_LETTER_RESOURCES,
                         AddressSettings.DEFAULT_DEAD_LETTER_QUEUE_PREFIX.toString(),
                         AddressSettings.DEFAULT_DEAD_LETTER_QUEUE_SUFFIX.toString(),
                         AddressSettings.DEFAULT_AUTO_CREATE_EXPIRY_RESOURCES,
                         AddressSettings.DEFAULT_EXPIRY_QUEUE_PREFIX.toString(),
                         AddressSettings.DEFAULT_EXPIRY_QUEUE_SUFFIX.toString());
   }

   @Override
   @Deprecated
   public void addAddressSettings(final String address,
                                  final String DLA,
                                  final String expiryAddress,
                                  final long expiryDelay,
                                  final boolean defaultLastValueQueue,
                                  final int maxDeliveryAttempts,
                                  final long maxSizeBytes,
                                  final int pageSizeBytes,
                                  final int pageMaxCacheSize,
                                  final long redeliveryDelay,
                                  final double redeliveryMultiplier,
                                  final long maxRedeliveryDelay,
                                  final long redistributionDelay,
                                  final boolean sendToDLAOnNoRoute,
                                  final String addressFullMessagePolicy,
                                  final long slowConsumerThreshold,
                                  final long slowConsumerCheckPeriod,
                                  final String slowConsumerPolicy,
                                  final boolean autoCreateJmsQueues,
                                  final boolean autoDeleteJmsQueues,
                                  final boolean autoCreateJmsTopics,
                                  final boolean autoDeleteJmsTopics,
                                  final boolean autoCreateQueues,
                                  final boolean autoDeleteQueues,
                                  final boolean autoCreateAddresses,
                                  final boolean autoDeleteAddresses,
                                  final String configDeleteQueues,
                                  final String configDeleteAddresses,
                                  final long maxSizeBytesRejectThreshold,
                                  final String defaultLastValueKey,
                                  final boolean defaultNonDestructive,
                                  final boolean defaultExclusiveQueue,
                                  final boolean defaultGroupRebalance,
                                  final int defaultGroupBuckets,
                                  final String defaultGroupFirstKey,
                                  final int defaultMaxConsumers,
                                  final boolean defaultPurgeOnNoConsumers,
                                  final int defaultConsumersBeforeDispatch,
                                  final long defaultDelayBeforeDispatch,
                                  final String defaultQueueRoutingType,
                                  final String defaultAddressRoutingType,
                                  final int defaultConsumerWindowSize,
                                  final long defaultRingSize,
                                  final boolean autoDeleteCreatedQueues,
                                  final long autoDeleteQueuesDelay,
                                  final long autoDeleteQueuesMessageCount,
                                  final long autoDeleteAddressesDelay,
                                  final double redeliveryCollisionAvoidanceFactor,
                                  final long retroactiveMessageCount,
                                  final boolean autoCreateDeadLetterResources,
                                  final String deadLetterQueuePrefix,
                                  final String deadLetterQueueSuffix,
                                  final boolean autoCreateExpiryResources,
                                  final String expiryQueuePrefix,
                                  final String expiryQueueSuffix) throws Exception {
      addAddressSettings(address,
                         DLA,
                         expiryAddress,
                         expiryDelay,
                         defaultLastValueQueue,
                         maxDeliveryAttempts,
                         maxSizeBytes,
                         pageSizeBytes,
                         pageMaxCacheSize,
                         redeliveryDelay,
                         redeliveryMultiplier,
                         maxRedeliveryDelay,
                         redistributionDelay,
                         sendToDLAOnNoRoute,
                         addressFullMessagePolicy,
                         slowConsumerThreshold,
                         slowConsumerCheckPeriod,
                         slowConsumerPolicy,
                         autoCreateJmsQueues,
                         autoDeleteJmsQueues,
                         autoCreateJmsTopics,
                         autoDeleteJmsTopics,
                         autoCreateQueues,
                         autoDeleteQueues,
                         autoCreateAddresses,
                         autoDeleteAddresses,
                         configDeleteQueues,
                         configDeleteAddresses,
                         maxSizeBytesRejectThreshold,
                         defaultLastValueKey,
                         defaultNonDestructive,
                         defaultExclusiveQueue,
                         defaultGroupRebalance,
                         defaultGroupBuckets,
                         defaultGroupFirstKey,
                         defaultMaxConsumers,
                         defaultPurgeOnNoConsumers,
                         defaultConsumersBeforeDispatch,
                         defaultDelayBeforeDispatch,
                         defaultQueueRoutingType,
                         defaultAddressRoutingType,
                         defaultConsumerWindowSize,
                         defaultRingSize,
                         autoDeleteCreatedQueues,
                         autoDeleteQueuesDelay,
                         autoDeleteQueuesMessageCount,
                         autoDeleteAddressesDelay,
                         redeliveryCollisionAvoidanceFactor,
                         retroactiveMessageCount,
                         AddressSettings.DEFAULT_AUTO_CREATE_DEAD_LETTER_RESOURCES,
                         AddressSettings.DEFAULT_DEAD_LETTER_QUEUE_PREFIX.toString(),
                         AddressSettings.DEFAULT_DEAD_LETTER_QUEUE_SUFFIX.toString(),
                         AddressSettings.DEFAULT_AUTO_CREATE_EXPIRY_RESOURCES,
                         AddressSettings.DEFAULT_EXPIRY_QUEUE_PREFIX.toString(),
                         AddressSettings.DEFAULT_EXPIRY_QUEUE_SUFFIX.toString(),
                         AddressSettings.DEFAULT_MIN_EXPIRY_DELAY,
                         AddressSettings.DEFAULT_MAX_EXPIRY_DELAY,
                         AddressSettings.DEFAULT_ENABLE_METRICS);
   }

   @Override
   @Deprecated
   public void addAddressSettings(final String address,
                                  final String DLA,
                                  final String expiryAddress,
                                  final long expiryDelay,
                                  final boolean defaultLastValueQueue,
                                  final int maxDeliveryAttempts,
                                  final long maxSizeBytes,
                                  final int pageSizeBytes,
                                  final int pageMaxCacheSize,
                                  final long redeliveryDelay,
                                  final double redeliveryMultiplier,
                                  final long maxRedeliveryDelay,
                                  final long redistributionDelay,
                                  final boolean sendToDLAOnNoRoute,
                                  final String addressFullMessagePolicy,
                                  final long slowConsumerThreshold,
                                  final long slowConsumerCheckPeriod,
                                  final String slowConsumerPolicy,
                                  final boolean autoCreateJmsQueues,
                                  final boolean autoDeleteJmsQueues,
                                  final boolean autoCreateJmsTopics,
                                  final boolean autoDeleteJmsTopics,
                                  final boolean autoCreateQueues,
                                  final boolean autoDeleteQueues,
                                  final boolean autoCreateAddresses,
                                  final boolean autoDeleteAddresses,
                                  final String configDeleteQueues,
                                  final String configDeleteAddresses,
                                  final long maxSizeBytesRejectThreshold,
                                  final String defaultLastValueKey,
                                  final boolean defaultNonDestructive,
                                  final boolean defaultExclusiveQueue,
                                  final boolean defaultGroupRebalance,
                                  final int defaultGroupBuckets,
                                  final String defaultGroupFirstKey,
                                  final int defaultMaxConsumers,
                                  final boolean defaultPurgeOnNoConsumers,
                                  final int defaultConsumersBeforeDispatch,
                                  final long defaultDelayBeforeDispatch,
                                  final String defaultQueueRoutingType,
                                  final String defaultAddressRoutingType,
                                  final int defaultConsumerWindowSize,
                                  final long defaultRingSize,
                                  final boolean autoDeleteCreatedQueues,
                                  final long autoDeleteQueuesDelay,
                                  final long autoDeleteQueuesMessageCount,
                                  final long autoDeleteAddressesDelay,
                                  final double redeliveryCollisionAvoidanceFactor,
                                  final long retroactiveMessageCount,
                                  final boolean autoCreateDeadLetterResources,
                                  final String deadLetterQueuePrefix,
                                  final String deadLetterQueueSuffix,
                                  final boolean autoCreateExpiryResources,
                                  final String expiryQueuePrefix,
                                  final String expiryQueueSuffix,
                                  final long minExpiryDelay,
                                  final long maxExpiryDelay,
                                  final boolean enableMetrics) throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.addAddressSettings(this.server, address, DLA, expiryAddress, expiryDelay, defaultLastValueQueue, maxDeliveryAttempts,
                  maxSizeBytes, pageSizeBytes, pageMaxCacheSize, redeliveryDelay, redeliveryMultiplier,
                  maxRedeliveryDelay, redistributionDelay, sendToDLAOnNoRoute, addressFullMessagePolicy,
                  slowConsumerThreshold, slowConsumerCheckPeriod, slowConsumerPolicy, autoCreateJmsQueues,
                  autoDeleteJmsQueues, autoCreateJmsTopics, autoDeleteJmsTopics, autoCreateQueues, autoDeleteQueues,
                  autoCreateAddresses, autoDeleteAddresses, configDeleteQueues, configDeleteAddresses, maxSizeBytesRejectThreshold,
                  defaultLastValueKey, defaultNonDestructive, defaultExclusiveQueue, defaultGroupRebalance, defaultGroupBuckets,
                  defaultGroupFirstKey, defaultMaxConsumers, defaultPurgeOnNoConsumers, defaultConsumersBeforeDispatch,
                  defaultDelayBeforeDispatch, defaultQueueRoutingType, defaultAddressRoutingType, defaultConsumerWindowSize,
                  defaultRingSize, autoDeleteCreatedQueues, autoDeleteQueuesDelay, autoDeleteQueuesMessageCount,
                  autoDeleteAddressesDelay, redeliveryCollisionAvoidanceFactor, retroactiveMessageCount, autoCreateDeadLetterResources,
                  deadLetterQueuePrefix, deadLetterQueueSuffix, autoCreateExpiryResources, expiryQueuePrefix,
                  expiryQueueSuffix, minExpiryDelay, maxExpiryDelay, enableMetrics);
      }
      checkStarted();

      if (pageSizeBytes > maxSizeBytes && maxSizeBytes > 0) {
         throw new IllegalStateException("pageSize has to be lower than maxSizeBytes. Invalid argument (" + pageSizeBytes + " < " + maxSizeBytes + ")");
      }

      if (maxSizeBytes < -1) {
         throw new IllegalStateException("Invalid argument on maxSizeBytes");
      }

      AddressSettings addressSettings = new AddressSettings();
      addressSettings.setDeadLetterAddress(SimpleString.of(DLA));
      addressSettings.setExpiryAddress(SimpleString.of(expiryAddress));
      addressSettings.setExpiryDelay(expiryDelay);
      addressSettings.setMinExpiryDelay(minExpiryDelay);
      addressSettings.setMaxExpiryDelay(maxExpiryDelay);
      addressSettings.setDefaultLastValueQueue(defaultLastValueQueue);
      addressSettings.setMaxDeliveryAttempts(maxDeliveryAttempts);
      addressSettings.setPageCacheMaxSize(pageMaxCacheSize);
      addressSettings.setMaxSizeBytes(maxSizeBytes);
      addressSettings.setPageSizeBytes(pageSizeBytes);
      addressSettings.setRedeliveryDelay(redeliveryDelay);
      addressSettings.setRedeliveryMultiplier(redeliveryMultiplier);
      addressSettings.setMaxRedeliveryDelay(maxRedeliveryDelay);
      addressSettings.setRedistributionDelay(redistributionDelay);
      addressSettings.setSendToDLAOnNoRoute(sendToDLAOnNoRoute);
      addressSettings.setAddressFullMessagePolicy(addressFullMessagePolicy == null || addressFullMessagePolicy.isEmpty() ? AddressSettings.DEFAULT_ADDRESS_FULL_MESSAGE_POLICY : AddressFullMessagePolicy.valueOf(addressFullMessagePolicy.toUpperCase()));
      addressSettings.setSlowConsumerThreshold(slowConsumerThreshold);
      addressSettings.setSlowConsumerCheckPeriod(slowConsumerCheckPeriod);
      addressSettings.setSlowConsumerPolicy(slowConsumerPolicy == null || slowConsumerPolicy.isEmpty() ? AddressSettings.DEFAULT_SLOW_CONSUMER_POLICY : SlowConsumerPolicy.valueOf(slowConsumerPolicy.toUpperCase()));
      addressSettings.setAutoCreateJmsQueues(autoCreateJmsQueues);
      addressSettings.setAutoDeleteJmsQueues(autoDeleteJmsQueues);
      addressSettings.setAutoCreateJmsTopics(autoCreateJmsTopics);
      addressSettings.setAutoDeleteJmsTopics(autoDeleteJmsTopics);
      addressSettings.setAutoCreateQueues(autoCreateQueues);
      addressSettings.setAutoDeleteQueues(autoDeleteQueues);
      addressSettings.setAutoCreateAddresses(autoCreateAddresses);
      addressSettings.setAutoDeleteAddresses(autoDeleteAddresses);
      addressSettings.setConfigDeleteQueues(configDeleteQueues == null || configDeleteQueues.isEmpty() ? AddressSettings.DEFAULT_CONFIG_DELETE_QUEUES : DeletionPolicy.valueOf(configDeleteQueues.toUpperCase()));
      addressSettings.setConfigDeleteAddresses(configDeleteAddresses == null || configDeleteAddresses.isEmpty() ? AddressSettings.DEFAULT_CONFIG_DELETE_ADDRESSES : DeletionPolicy.valueOf(configDeleteAddresses.toUpperCase()));
      addressSettings.setMaxSizeBytesRejectThreshold(maxSizeBytesRejectThreshold);
      addressSettings.setDefaultLastValueKey(defaultLastValueKey == null || defaultLastValueKey.isEmpty() ? ActiveMQDefaultConfiguration.getDefaultLastValueKey() : SimpleString.of(defaultLastValueKey));
      addressSettings.setDefaultNonDestructive(defaultNonDestructive);
      addressSettings.setDefaultExclusiveQueue(defaultExclusiveQueue);
      addressSettings.setDefaultGroupRebalance(defaultGroupRebalance);
      addressSettings.setDefaultGroupBuckets(defaultGroupBuckets);
      addressSettings.setDefaultGroupFirstKey(defaultGroupFirstKey == null || defaultGroupFirstKey.isEmpty() ? ActiveMQDefaultConfiguration.getDefaultGroupFirstKey() : SimpleString.of(defaultGroupFirstKey));
      addressSettings.setDefaultMaxConsumers(defaultMaxConsumers);
      addressSettings.setDefaultPurgeOnNoConsumers(defaultPurgeOnNoConsumers);
      addressSettings.setDefaultConsumersBeforeDispatch(defaultConsumersBeforeDispatch);
      addressSettings.setDefaultDelayBeforeDispatch(defaultDelayBeforeDispatch);
      addressSettings.setDefaultQueueRoutingType(defaultQueueRoutingType == null || defaultQueueRoutingType.isEmpty() ? ActiveMQDefaultConfiguration.getDefaultRoutingType() : RoutingType.valueOf(defaultQueueRoutingType.toUpperCase()));
      addressSettings.setDefaultAddressRoutingType(defaultAddressRoutingType == null || defaultAddressRoutingType.isEmpty() ? ActiveMQDefaultConfiguration.getDefaultRoutingType() : RoutingType.valueOf(defaultAddressRoutingType.toUpperCase()));
      addressSettings.setDefaultConsumerWindowSize(defaultConsumerWindowSize);
      addressSettings.setDefaultRingSize(defaultRingSize);
      addressSettings.setAutoDeleteCreatedQueues(autoDeleteCreatedQueues);
      addressSettings.setAutoDeleteQueuesDelay(autoDeleteQueuesDelay);
      addressSettings.setAutoDeleteQueuesMessageCount(autoDeleteQueuesMessageCount);
      addressSettings.setAutoDeleteAddressesDelay(autoDeleteAddressesDelay);
      addressSettings.setRedeliveryCollisionAvoidanceFactor(redeliveryCollisionAvoidanceFactor);
      addressSettings.setRetroactiveMessageCount(retroactiveMessageCount);
      addressSettings.setAutoCreateDeadLetterResources(autoCreateDeadLetterResources);
      addressSettings.setDeadLetterQueuePrefix(deadLetterQueuePrefix == null || deadLetterQueuePrefix.isEmpty() ? null : SimpleString.of(deadLetterQueuePrefix));
      addressSettings.setDeadLetterQueueSuffix(deadLetterQueueSuffix == null || deadLetterQueueSuffix.isEmpty() ? null : SimpleString.of(deadLetterQueueSuffix));
      addressSettings.setAutoCreateExpiryResources(autoCreateExpiryResources);
      addressSettings.setExpiryQueuePrefix(expiryQueuePrefix == null || expiryQueuePrefix.isEmpty() ? null : SimpleString.of(expiryQueuePrefix));
      addressSettings.setExpiryQueueSuffix(expiryQueueSuffix == null || expiryQueueSuffix.isEmpty() ? null : SimpleString.of(expiryQueueSuffix));
      addressSettings.setEnableMetrics(enableMetrics);

      server.getAddressSettingsRepository().addMatch(address, addressSettings);

      storageManager.storeAddressSetting(new PersistedAddressSettingJSON(SimpleString.of(address), addressSettings, addressSettings.toJSON()));

   }

   @Override
   public String addAddressSettings(String address, String addressSettingsConfigurationAsJson) throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.addAddressSettings(this.server, addressSettingsConfigurationAsJson);
      }
      checkStarted();

      clearIO();

      try {
         AddressSettings addressSettingsConfiguration = AddressSettings.fromJSON(addressSettingsConfigurationAsJson);

         if (addressSettingsConfiguration == null) {
            throw ActiveMQMessageBundle.BUNDLE.failedToParseJson(addressSettingsConfigurationAsJson);
         }
         if (addressSettingsConfiguration.getPageSizeBytes() > addressSettingsConfiguration.getMaxSizeBytes() && addressSettingsConfiguration.getMaxSizeBytes() > 0) {
            throw new IllegalStateException("pageSize has to be lower than maxSizeBytes. Invalid argument (" + addressSettingsConfiguration.getPageSizeBytes() + " < " + addressSettingsConfiguration.getMaxSizeBytes() + ")");
         }

         if (addressSettingsConfiguration.getMaxSizeBytes() < -1) {
            throw new IllegalStateException("Invalid argument on maxSizeBytes");
         }
         server.getAddressSettingsRepository().addMatch(address, addressSettingsConfiguration);

         storageManager.storeAddressSetting(new PersistedAddressSettingJSON(SimpleString.of(address), addressSettingsConfiguration, SimpleString.of(addressSettingsConfigurationAsJson)));
         return addressSettingsConfiguration.toJSON();
      } catch (ActiveMQException e) {
         throw new IllegalStateException(e.getMessage());
      } finally {
         blockOnIO();
      }
   }

   @Override
   public void removeAddressSettings(final String addressMatch) throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.removeAddressSettings(this.server, addressMatch);
      }
      checkStarted();

      server.getAddressSettingsRepository().removeMatch(addressMatch);
      storageManager.deleteAddressSetting(SimpleString.of(addressMatch));
   }

   public void sendQueueInfoToQueue(final String queueName, final String address) throws Exception {
      checkStarted();

      clearIO();
      try {
         postOffice.sendQueueInfoToQueue(SimpleString.of(queueName), SimpleString.of(Objects.requireNonNullElse(address, "")));

         GroupingHandler handler = server.getGroupingHandler();
         if (handler != null) {
            // the group handler would miss responses if the group was requested before the reset was done
            // on that case we ask the groupinghandler to replay its send in case it's waiting for the information
            handler.resendPending();
         }
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String[] getDivertNames() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getDivertNames(this.server);
      }
      checkStarted();

      clearIO();
      try {
         Object[] diverts = server.getManagementService().getResources(DivertControl.class);
         String[] names = new String[diverts.length];
         for (int i = 0; i < diverts.length; i++) {
            DivertControl divert = (DivertControl) diverts[i];
            names[i] = divert.getUniqueName();
         }

         return names;
      } finally {
         blockOnIO();
      }
   }

   @Override
   public void createDivert(final String name,
                            final String routingName,
                            final String address,
                            final String forwardingAddress,
                            final boolean exclusive,
                            final String filterString,
                            final String transformerClassName) throws Exception {
      createDivert(name, routingName, address, forwardingAddress, exclusive, filterString, transformerClassName, ActiveMQDefaultConfiguration.getDefaultDivertRoutingType());
   }

   @Override
   public void createDivert(final String name,
                            final String routingName,
                            final String address,
                            final String forwardingAddress,
                            final boolean exclusive,
                            final String filterString,
                            final String transformerClassName,
                            final String routingType) throws Exception {
      createDivert(name, routingName, address, forwardingAddress, exclusive, filterString, transformerClassName, (String) null, routingType);
   }

   @Override
   public void createDivert(final String name,
                            final String routingName,
                            final String address,
                            final String forwardingAddress,
                            final boolean exclusive,
                            final String filterString,
                            final String transformerClassName,
                            final String transformerPropertiesAsJSON,
                            final String routingType) throws Exception {
      createDivert(name, routingName, address, forwardingAddress, exclusive, filterString, transformerClassName, JsonUtil.readJsonProperties(transformerPropertiesAsJSON), routingType);
   }

   @Override
   public void createDivert(final String name,
                            final String routingName,
                            final String address,
                            final String forwardingAddress,
                            final boolean exclusive,
                            final String filterString,
                            final String transformerClassName,
                            final Map<String, String> transformerProperties,
                            final String routingType) throws Exception {
      TransformerConfiguration transformerConfiguration = transformerClassName == null || transformerClassName.isEmpty() ? null : new TransformerConfiguration(transformerClassName).setProperties(transformerProperties);
      createDivert(new DivertConfiguration().setName(name).setRoutingName(routingName).setAddress(address).setForwardingAddress(forwardingAddress).setExclusive(exclusive).setFilterString(filterString).setTransformerConfiguration(transformerConfiguration).setRoutingType(ComponentConfigurationRoutingType.valueOf(routingType)));
   }

   @Override
   public void createDivert(final String divertConfiguration) throws Exception {
      createDivert(DivertConfiguration.fromJSON(divertConfiguration));
   }


   private void createDivert(final DivertConfiguration config) throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.createDivert(this.server, config);
      }
      checkStarted();

      clearIO();
      try {
         server.deployDivert(config);
      } finally {
         blockOnIO();
      }
   }

   @Override
   public void updateDivert(final String name,
                            final String forwardingAddress,
                            final String filterString,
                            final String transformerClassName,
                            final Map<String, String> transformerProperties,
                            final String routingType) throws Exception {
      TransformerConfiguration transformerConfiguration = transformerClassName == null || transformerClassName.isEmpty() ? null : new TransformerConfiguration(transformerClassName).setProperties(transformerProperties);

      DivertConfiguration config = new DivertConfiguration()
         .setName(name)
         .setForwardingAddress(forwardingAddress)
         .setFilterString(filterString)
         .setTransformerConfiguration(transformerConfiguration)
         .setRoutingType(ComponentConfigurationRoutingType.valueOf(routingType));

      updateDivert(config);
   }

   @Override
   public void updateDivert(final String divertConfiguration) throws Exception {
      updateDivert(DivertConfiguration.fromJSON(divertConfiguration));
   }

   private void updateDivert(final DivertConfiguration config) throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.updateDivert(this.server, config);
      }
      checkStarted();

      clearIO();

      try {
         final Divert divert = server.updateDivert(config);
         if (divert == null) {
            throw ActiveMQMessageBundle.BUNDLE.divertDoesNotExist(config.getName());
         }
      } finally {
         blockOnIO();
      }
   }

   @Override
   public void destroyDivert(final String name) throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.destroyDivert(this.server, name);
      }
      checkStarted();

      clearIO();
      try {
         server.destroyDivert(SimpleString.of(name), true);
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String[] getBridgeNames() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getBridgeNames(this.server);
      }
      checkStarted();

      clearIO();
      try {
         Object[] bridges = server.getManagementService().getResources(BridgeControl.class);
         String[] names = new String[bridges.length];
         for (int i = 0; i < bridges.length; i++) {
            BridgeControl bridge = (BridgeControl) bridges[i];
            names[i] = bridge.getName();
         }

         return names;
      } finally {
         blockOnIO();
      }
   }

   @Override
   public void createBridge(final String name,
                            final String queueName,
                            final String forwardingAddress,
                            final String filterString,
                            final String transformerClassName,
                            final long retryInterval,
                            final double retryIntervalMultiplier,
                            final int initialConnectAttempts,
                            final int reconnectAttempts,
                            final boolean useDuplicateDetection,
                            final int confirmationWindowSize,
                            final int producerWindowSize,
                            final long clientFailureCheckPeriod,
                            final String staticConnectorsOrDiscoveryGroup,
                            boolean useDiscoveryGroup,
                            final boolean ha,
                            final String user,
                            final String password) throws Exception {
      createBridge(name,
                   queueName,
                   forwardingAddress,
                   filterString,
                   transformerClassName,
                   (String) null,
                   retryInterval,
                   retryIntervalMultiplier,
                   initialConnectAttempts,
                   reconnectAttempts,
                   useDuplicateDetection,
                   confirmationWindowSize,
                   producerWindowSize,
                   clientFailureCheckPeriod,
                   staticConnectorsOrDiscoveryGroup,
                   useDiscoveryGroup,
                   ha,
                   user,
                   password);
   }

   @Override
   public void createBridge(final String name,
                            final String queueName,
                            final String forwardingAddress,
                            final String filterString,
                            final String transformerClassName,
                            final String transformerPropertiesAsJSON,
                            final long retryInterval,
                            final double retryIntervalMultiplier,
                            final int initialConnectAttempts,
                            final int reconnectAttempts,
                            final boolean useDuplicateDetection,
                            final int confirmationWindowSize,
                            final int producerWindowSize,
                            final long clientFailureCheckPeriod,
                            final String staticConnectorsOrDiscoveryGroup,
                            boolean useDiscoveryGroup,
                            final boolean ha,
                            final String user,
                            final String password) throws Exception {
      createBridge(name,
                   queueName,
                   forwardingAddress,
                   filterString,
                   transformerClassName,
                   JsonUtil.readJsonProperties(transformerPropertiesAsJSON),
                   retryInterval,
                   retryIntervalMultiplier,
                   initialConnectAttempts,
                   reconnectAttempts,
                   useDuplicateDetection,
                   confirmationWindowSize,
                   producerWindowSize,
                   clientFailureCheckPeriod,
                   staticConnectorsOrDiscoveryGroup,
                   useDiscoveryGroup,
                   ha,
                   user,
                   password);
   }

   @Override
   public void createBridge(final String name,
                            final String queueName,
                            final String forwardingAddress,
                            final String filterString,
                            final String transformerClassName,
                            final Map<String, String> transformerProperties,
                            final long retryInterval,
                            final double retryIntervalMultiplier,
                            final int initialConnectAttempts,
                            final int reconnectAttempts,
                            final boolean useDuplicateDetection,
                            final int confirmationWindowSize,
                            final int producerWindowSize,
                            final long clientFailureCheckPeriod,
                            final String staticConnectorsOrDiscoveryGroup,
                            boolean useDiscoveryGroup,
                            final boolean ha,
                            final String user,
                            final String password) throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.createBridge(this.server, name, queueName, forwardingAddress, filterString,
                  transformerClassName, transformerProperties, retryInterval, retryIntervalMultiplier,
                  initialConnectAttempts, reconnectAttempts, useDuplicateDetection, confirmationWindowSize,
                  producerWindowSize, clientFailureCheckPeriod, staticConnectorsOrDiscoveryGroup,
                  useDiscoveryGroup, ha, user, "****");
      }
      checkStarted();

      clearIO();

      try {
         TransformerConfiguration transformerConfiguration = transformerClassName == null || transformerClassName.isEmpty()  ? null : new TransformerConfiguration(transformerClassName).setProperties(transformerProperties);
         BridgeConfiguration config = new BridgeConfiguration().setName(name).setQueueName(queueName).setForwardingAddress(forwardingAddress).setFilterString(filterString).setTransformerConfiguration(transformerConfiguration).setClientFailureCheckPeriod(clientFailureCheckPeriod).setRetryInterval(retryInterval).setRetryIntervalMultiplier(retryIntervalMultiplier).setInitialConnectAttempts(initialConnectAttempts).setReconnectAttempts(reconnectAttempts).setUseDuplicateDetection(useDuplicateDetection).setConfirmationWindowSize(confirmationWindowSize).setProducerWindowSize(producerWindowSize).setHA(ha).setUser(user).setPassword(password).setConfigurationManaged(false);

         if (useDiscoveryGroup) {
            config.setDiscoveryGroupName(staticConnectorsOrDiscoveryGroup);
         } else {
            config.setStaticConnectors(ListUtil.toList(staticConnectorsOrDiscoveryGroup));
         }

         server.deployBridge(config);
      } finally {
         blockOnIO();
      }
   }

   @Override
   public void createBridge(final String name,
                            final String queueName,
                            final String forwardingAddress,
                            final String filterString,
                            final String transformerClassName,
                            final long retryInterval,
                            final double retryIntervalMultiplier,
                            final int initialConnectAttempts,
                            final int reconnectAttempts,
                            final boolean useDuplicateDetection,
                            final int confirmationWindowSize,
                            final long clientFailureCheckPeriod,
                            final String staticConnectorsOrDiscoveryGroup,
                            boolean useDiscoveryGroup,
                            final boolean ha,
                            final String user,
                            final String password) throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.createBridge(this.server, name, queueName, forwardingAddress, filterString,
                  transformerClassName, retryInterval, retryIntervalMultiplier, initialConnectAttempts,
                  reconnectAttempts, useDuplicateDetection, confirmationWindowSize, clientFailureCheckPeriod,
                  staticConnectorsOrDiscoveryGroup, useDiscoveryGroup, ha, user, "****");
      }
      checkStarted();

      clearIO();

      try {
         TransformerConfiguration transformerConfiguration = transformerClassName == null || transformerClassName.isEmpty()  ? null : new TransformerConfiguration(transformerClassName);
         BridgeConfiguration config = new BridgeConfiguration().setName(name).setQueueName(queueName).setForwardingAddress(forwardingAddress).setFilterString(filterString).setTransformerConfiguration(transformerConfiguration).setClientFailureCheckPeriod(clientFailureCheckPeriod).setRetryInterval(retryInterval).setRetryIntervalMultiplier(retryIntervalMultiplier).setInitialConnectAttempts(initialConnectAttempts).setReconnectAttempts(reconnectAttempts).setUseDuplicateDetection(useDuplicateDetection).setConfirmationWindowSize(confirmationWindowSize).setHA(ha).setUser(user).setPassword(password).setConfigurationManaged(false);

         if (useDiscoveryGroup) {
            config.setDiscoveryGroupName(staticConnectorsOrDiscoveryGroup);
         } else {
            config.setStaticConnectors(ListUtil.toList(staticConnectorsOrDiscoveryGroup));
         }

         server.deployBridge(config);
      } finally {
         blockOnIO();
      }
   }

   @Override
   public void createBridge(String bridgeConfigurationAsJson) throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.createBridge(this.server, bridgeConfigurationAsJson);
      }
      checkStarted();

      clearIO();

      try {
         // when the BridgeConfiguration is passed through createBridge all of its defaults get set which we return to the caller
         BridgeConfiguration bridgeConfiguration = BridgeConfiguration.fromJSON(bridgeConfigurationAsJson);
         if (bridgeConfiguration == null) {
            throw ActiveMQMessageBundle.BUNDLE.failedToParseJson(bridgeConfigurationAsJson);
         }
         bridgeConfiguration.setConfigurationManaged(false);
         server.deployBridge(bridgeConfiguration);
      } catch (ActiveMQException e) {
         throw new IllegalStateException(e.getMessage());
      } finally {
         blockOnIO();
      }
   }

   @Override
   public void addConnector(String name, String url) throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.addConnector(this.server, name, url);
      }
      checkStarted();

      clearIO();

      try {
         server.getConfiguration().addConnectorConfiguration(name, url);
         storageManager.storeConnector(new PersistedConnector(name, url));
      } finally {
         blockOnIO();
      }
   }

   @Override
   public void removeConnector(String name) throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.removeConnector(this.server, name);
      }
      checkStarted();

      clearIO();

      try {
         server.getConfiguration().getConnectorConfigurations().remove(name);
         storageManager.deleteConnector(name);
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String listBrokerConnections() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.listBrokerConnections();
      }
      checkStarted();

      clearIO();
      try {

         JsonArrayBuilder connections = JsonLoader.createArrayBuilder();
         for (BrokerConnection brokerConnection : server.getBrokerConnections()) {
            JsonObjectBuilder obj = JsonLoader.createObjectBuilder();
            obj.add("name", brokerConnection.getName());
            obj.add("protocol", brokerConnection.getProtocol());
            obj.add("started", brokerConnection.isStarted());
            obj.add("uri", brokerConnection.getConfiguration().getUri());
            obj.add("connected", brokerConnection.isConnected());
            connections.add(obj.build());
         }
         return connections.build().toString();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public void startBrokerConnection(String name) throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.startBrokerConnection(name);
      }
      checkStarted();

      clearIO();
      try {
         server.startBrokerConnection(name);
      } finally {
         blockOnIO();
      }
   }

   @Override
   public void stopBrokerConnection(String name) throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.stopBrokerConnection(name);
      }
      checkStarted();

      clearIO();
      try {
         server.stopBrokerConnection(name);
      } finally {
         blockOnIO();
      }
   }

   @Override
   public void destroyBridge(final String name) throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.destroyBridge(this.server, name);
      }
      checkStarted();

      clearIO();
      try {
         server.destroyBridge(name);
      } finally {
         blockOnIO();
      }
   }

   @Override
   public void createConnectorService(final String name, final String factoryClass, final Map<String, Object> parameters) throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.createConnectorService(this.server, name, factoryClass, parameters);
      }
      checkStarted();

      clearIO();

      try {
         final ConnectorServiceConfiguration config = new ConnectorServiceConfiguration().setName(name).setFactoryClassName(factoryClass).setParams(parameters);
         ConnectorServiceFactory factory = server.getServiceRegistry().getConnectorService(config);
         server.getConnectorsService().createService(config, factory);
      } finally {
         blockOnIO();
      }
   }

   @Override
   public void destroyConnectorService(final String name) throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.destroyConnectorService(this.server, name);
      }
      checkStarted();

      clearIO();

      try {
         server.getConnectorsService().destroyService(name);
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String[] getConnectorServices() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getConnectorServices(this.server);
      }
      checkStarted();

      clearIO();

      try {
         return server.getConnectorsService().getConnectors().keySet().toArray(new String[0]);
      } finally {
         blockOnIO();
      }
   }

   @Override
   public void forceFailover() throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.forceFailover(this.server);
      }
      checkStarted();

      clearIO();

      Thread t = new Thread(() -> {
         try {
            server.stop(true, true);
         } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
         }
      });
      t.start();
   }

   public void updateDuplicateIdCache(String address, Object[] ids) throws Exception {
      clearIO();
      try {
         DuplicateIDCache duplicateIDCache = server.getPostOffice().getDuplicateIDCache(SimpleString.of(address));
         for (Object id : ids) {
            duplicateIDCache.addToCache(((String) id).getBytes(), null);
         }
      } finally {
         blockOnIO();
      }
   }

   @Override
   public void scaleDown(String connector) throws Exception {
      try (AutoCloseable lock = server.managementLock()) {
         if (AuditLogger.isBaseLoggingEnabled()) {
            AuditLogger.scaleDown(this.server, connector);
         }
         checkStarted();

         clearIO();
         HAPolicy haPolicy = server.getHAPolicy();
         if (haPolicy instanceof PrimaryOnlyPolicy primaryOnlyPolicy) {

            if (primaryOnlyPolicy.getScaleDownPolicy() == null) {
               primaryOnlyPolicy.setScaleDownPolicy(new ScaleDownPolicy());
            }

            primaryOnlyPolicy.getScaleDownPolicy().setEnabled(true);

            if (connector != null) {
               primaryOnlyPolicy.getScaleDownPolicy().getConnectors().add(0, connector);
            }

            server.stop(true, true);
         }
      }
   }


   @Override
   public String listNetworkTopology() throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.listNetworkTopology(this.server);
      }
      checkStarted();

      clearIO();
      try {
         JsonArrayBuilder brokers = JsonLoader.createArrayBuilder();
         ClusterManager clusterManager = server.getClusterManager();
         if (clusterManager != null) {
            Set<ClusterConnection> clusterConnections = clusterManager.getClusterConnections();
            for (ClusterConnection clusterConnection : clusterConnections) {
               Topology topology = clusterConnection.getTopology();
               Collection<TopologyMemberImpl> members = topology.getMembers();
               for (TopologyMemberImpl member : members) {

                  TransportConfiguration primary = member.getPrimary();
                  if (primary != null) {
                     JsonObjectBuilder obj = JsonLoader.createObjectBuilder();
                     // keep "live" here for backwards compatibility
                     obj.add("nodeID", member.getNodeId()).add("live", primary.getParams().get("host") + ":" + primary.getParams().get("port"));
                     obj.add("nodeID", member.getNodeId()).add("primary", primary.getParams().get("host") + ":" + primary.getParams().get("port"));
                     TransportConfiguration backup = member.getBackup();
                     if (backup != null) {
                        obj.add("backup", backup.getParams().get("host") + ":" + backup.getParams().get("port"));
                     }
                     brokers.add(obj);
                  }
               }
            }
         }
         return brokers.build().toString();
      } finally {
         blockOnIO();
      }
   }


   // NotificationEmitter implementation ----------------------------

   @Override
   public void removeNotificationListener(final NotificationListener listener,
                                          final NotificationFilter filter,
                                          final Object handback) throws ListenerNotFoundException {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.removeNotificationListener(this.server, listener, filter, handback);
      }
      clearIO();
      try {
         broadcaster.removeNotificationListener(listener, filter, handback);
      } finally {
         blockOnIO();
      }
   }

   @Override
   public void removeNotificationListener(final NotificationListener listener) throws ListenerNotFoundException {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.removeNotificationListener(this.server, listener);
      }
      clearIO();
      try {
         broadcaster.removeNotificationListener(listener);
      } finally {
         blockOnIO();
      }
   }

   @Override
   public void addNotificationListener(final NotificationListener listener,
                                       final NotificationFilter filter,
                                       final Object handback) throws IllegalArgumentException {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.addNotificationListener(this.server, listener, filter, handback);
      }
      clearIO();
      try {
         broadcaster.addNotificationListener(listener, filter, handback);
      } finally {
         blockOnIO();
      }
   }

   @Override
   public MBeanNotificationInfo[] getNotificationInfo() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getNotificationInfo(this.server);
      }
      CoreNotificationType[] values = CoreNotificationType.values();
      String[] names = new String[values.length];
      for (int i = 0; i < values.length; i++) {
         names[i] = values[i].toString();
      }
      return new MBeanNotificationInfo[]{new MBeanNotificationInfo(names, this.getClass().getName(), "Notifications emitted by a Core Server")};
   }




   private synchronized void setMessageCounterEnabled(final boolean enable) {
      if (isStarted()) {
         if (configuration.isMessageCounterEnabled() && !enable) {
            stopMessageCounters();
         } else if (!configuration.isMessageCounterEnabled() && enable) {
            startMessageCounters();
         }
      }
      configuration.setMessageCounterEnabled(enable);
   }

   private void startMessageCounters() {
      messageCounterManager.start();
   }

   private void stopMessageCounters() {
      messageCounterManager.stop();

      messageCounterManager.resetAllCounters();

      messageCounterManager.resetAllCounterHistories();
   }

   @Override
   public long getConnectionTTLOverride() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getConnectionTTLOverride(this.server);
      }
      return configuration.getConnectionTTLOverride();
   }

   @Override
   public int getIDCacheSize() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getIDCacheSize(this.server);
      }
      return configuration.getIDCacheSize();
   }

   @Override
   public String getLargeMessagesDirectory() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getLargeMessagesDirectory(this.server);
      }
      return configuration.getLargeMessagesDirectory();
   }

   @Override
   public String getManagementAddress() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getManagementAddress(this.server);
      }
      return configuration.getManagementAddress().toString();
   }

   @Override
   public String getNodeID() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getNodeID(this.server);
      }
      return Objects.toString(server.getNodeID(), null);
   }

   @Override
   public long getActivationSequence() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getActivationSequence(this.server);
      }
      if (server.getNodeManager() != null) {
         return server.getNodeManager().getNodeActivationSequence();
      }
      return 0;
   }

   @Override
   public String getManagementNotificationAddress() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getManagementNotificationAddress(this.server);
      }
      return configuration.getManagementNotificationAddress().toString();
   }

   @Override
   public long getMessageExpiryScanPeriod() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getMessageExpiryScanPeriod(this.server);
      }
      return configuration.getMessageExpiryScanPeriod();
   }

   @Override
   @Deprecated
   public long getMessageExpiryThreadPriority() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getMessageExpiryThreadPriority(this.server);
      }
      return configuration.getMessageExpiryThreadPriority();
   }

   @Override
   public long getTransactionTimeout() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getTransactionTimeout(this.server);
      }
      return configuration.getTransactionTimeout();
   }

   @Override
   public long getTransactionTimeoutScanPeriod() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getTransactionTimeoutScanPeriod(this.server);
      }
      return configuration.getTransactionTimeoutScanPeriod();
   }

   @Override
   public boolean isPersistDeliveryCountBeforeDelivery() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.isPersistDeliveryCountBeforeDelivery(this.server);
      }
      return configuration.isPersistDeliveryCountBeforeDelivery();
   }

   @Override
   public boolean isPersistIDCache() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.isPersistIDCache(this.server);
      }
      return configuration.isPersistIDCache();
   }

   @Override
   public boolean isWildcardRoutingEnabled() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.isWildcardRoutingEnabled(this.server);
      }
      return configuration.isWildcardRoutingEnabled();
   }

   @Override
   protected MBeanOperationInfo[] fillMBeanOperationInfo() {
      return MBeanInfoHelper.getMBeanOperationsInfo(ActiveMQServerControl.class);
   }

   @Override
   protected MBeanAttributeInfo[] fillMBeanAttributeInfo() {
      return MBeanInfoHelper.getMBeanAttributesInfo(ActiveMQServerControl.class);
   }

   private void checkStarted() {
      if (!server.isStarted()) {
         throw new IllegalStateException("Broker is not started. It can not be managed yet");
      }
   }

   @Override
   public void onNotification(org.apache.activemq.artemis.core.server.management.Notification notification) {
      if (!(notification.getType() instanceof CoreNotificationType type))
         return;
      if (type == CoreNotificationType.SESSION_CREATED) {
         TypedProperties props = notification.getProperties();
         /*
          * If the SESSION_CREATED notification is received from another node in the cluster, no broadcast call is made.
          * To keep the original logic to avoid calling the broadcast multiple times for the same SESSION_CREATED notification in the cluster.
          */
         if (props.getIntProperty(ManagementHelper.HDR_DISTANCE) > 0) {
            return;
         }
      }

      this.broadcaster.sendNotification(new Notification(type.toString(), this, notifSeq.incrementAndGet(), notification.toString()));
   }

   @Override
   public void addUser(String username, String password, String roles, boolean plaintext) throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.addUser(this.server, username, "****", roles, plaintext);
      }

      String passwordToUse = plaintext ? password : PasswordMaskingUtil.getHashProcessor().hash(password);

      if (server.getSecurityManager() instanceof ActiveMQBasicSecurityManager) {
         ((ActiveMQBasicSecurityManager) server.getSecurityManager()).addNewUser(username, passwordToUse, roles.split(","));
      } else {
         synchronized (userLock) {
            tcclInvoke(ActiveMQServerControlImpl.class.getClassLoader(), () -> {
               PropertiesLoginModuleConfigurator config = getPropertiesLoginModuleConfigurator();
               config.addNewUser(username, passwordToUse, roles.split(","));
               config.save();
            });
         }
      }
   }

   private String getSecurityDomain() {
      return server.getSecurityManager().getDomain();
   }

   @Override
   public String listUser(String username) throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.listUser(this.server, username);
      }
      if (server.getSecurityManager() instanceof ActiveMQBasicSecurityManager) {
         return buildJsonUserList(((ActiveMQBasicSecurityManager) server.getSecurityManager()).listUser(username));
      } else {
         synchronized (userLock) {
            return (String) tcclCall(ActiveMQServerControlImpl.class.getClassLoader(), () -> {
               return buildJsonUserList(getPropertiesLoginModuleConfigurator().listUser(username));
            });
         }
      }
   }

   private String buildJsonUserList(Map<String, Set<String>> info) {
      JsonArrayBuilder users = JsonLoader.createArrayBuilder();
      for (Entry<String, Set<String>> entry : info.entrySet()) {
         JsonObjectBuilder user = JsonLoader.createObjectBuilder();
         user.add("username", entry.getKey());
         JsonArrayBuilder roles = JsonLoader.createArrayBuilder();
         for (String role : entry.getValue()) {
            roles.add(role);
         }
         user.add("roles", roles);
         users.add(user);
      }
      return users.build().toString();
   }

   @Override
   public void removeUser(String username) throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.removeUser(this.server, username);
      }
      if (server.getSecurityManager() instanceof ActiveMQBasicSecurityManager) {
         ((ActiveMQBasicSecurityManager) server.getSecurityManager()).removeUser(username);
      } else {
         synchronized (userLock) {
            tcclInvoke(ActiveMQServerControlImpl.class.getClassLoader(), () -> {
               PropertiesLoginModuleConfigurator config = getPropertiesLoginModuleConfigurator();
               config.removeUser(username);
               config.save();
            });
         }
      }
   }

   @Override
   public String getStatus() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getStatus(this.server);
      }
      checkStarted();

      return server.getStatus();
   }

   @Override
   public void resetUser(String username, String password, String roles, boolean plaintext) throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.resetUser(this.server, username, "****", roles, plaintext);
      }

      String passwordToUse = password == null || password.isEmpty() ? password : plaintext ? password : PasswordMaskingUtil.getHashProcessor().hash(password);

      if (server.getSecurityManager() instanceof ActiveMQBasicSecurityManager) {
         ((ActiveMQBasicSecurityManager) server.getSecurityManager()).updateUser(username, passwordToUse, roles == null ? null : roles.split(","));
      } else {
         synchronized (userLock) {
            tcclInvoke(ActiveMQServerControlImpl.class.getClassLoader(), () -> {
               PropertiesLoginModuleConfigurator config = getPropertiesLoginModuleConfigurator();
               // don't hash a null password even if plaintext = false
               config.updateUser(username, passwordToUse, roles == null ? null : roles.split(","));
               config.save();
            });
         }
      }
   }

   @Override
   public void resetUser(String username, String password, String roles) throws Exception {
      // no need to synchronize here because the method we call is synchronized
      resetUser(username, password, roles, true);
   }

   private PropertiesLoginModuleConfigurator getPropertiesLoginModuleConfigurator() throws Exception {
      URL configurationUrl = server.getConfiguration().getConfigurationUrl();
      if (configurationUrl == null) {
         throw ActiveMQMessageBundle.BUNDLE.failedToLocateConfigURL();
      }
      String path = Path.of(configurationUrl.toURI()).toString();
      return new PropertiesLoginModuleConfigurator(getSecurityDomain(), path.substring(0, path.lastIndexOf(File.separator)));
   }

   @Override
   public void reloadConfigurationFile() throws Exception {
      server.reloadConfigurationFile();
   }

   @Override
   public void replay(String address, String target, String filter) throws Exception {
      server.replay(null, null, address, target, filter);
   }

   @Override
   public void replay(String startScan, String endScan, String address, String target, String filter) throws Exception {

      SimpleDateFormat format = ReplayManager.newRetentionSimpleDateFormat();

      Date startScanDate = format.parse(startScan);
      Date endScanDate = format.parse(endScan);

      server.replay(startScanDate, endScanDate, address, target, filter);
   }

   @Override
   public void stopEmbeddedWebServer() throws Exception {
      synchronized (embeddedWebServerLock) {
         getEmbeddedWebServerComponent().stop(true);
      }
   }

   @Override
   public void startEmbeddedWebServer() throws Exception {
      synchronized (embeddedWebServerLock) {
         getEmbeddedWebServerComponent().start();
      }
   }

   @Override
   public void restartEmbeddedWebServer() throws Exception {
      restartEmbeddedWebServer(ActiveMQDefaultConfiguration.getDefaultEmbeddedWebServerRestartTimeout());
   }

   @Override
   public void restartEmbeddedWebServer(long timeout) throws Exception {
      final CountDownLatch latch = new CountDownLatch(1);
      final AtomicReference<Exception> exception = new AtomicReference<>();
      /*
       * This needs to be run in its own thread managed by the broker because if it is run on a thread managed by Jetty
       * (e.g. if it is invoked from the web console) then the thread will die before Jetty can be restarted.
       */
      server.getThreadPool().execute(() -> {
         try {
            synchronized (embeddedWebServerLock) {
               stopEmbeddedWebServer();
               startEmbeddedWebServer();
            }
         } catch (Exception e) {
            exception.set(e);
         } finally {
            latch.countDown();
         }
      });
      if (!latch.await(timeout, TimeUnit.MILLISECONDS)) {
         throw ActiveMQMessageBundle.BUNDLE.embeddedWebServerRestartTimeout(timeout);
      }
      if (exception.get() != null) {
         throw ActiveMQMessageBundle.BUNDLE.embeddedWebServerRestartFailed(exception.get());
      }
   }

   @Override
   public boolean isEmbeddedWebServerStarted() {
      try {
         return getEmbeddedWebServerComponent().isStarted();
      } catch (Exception e) {
         logger.trace(e.getMessage());
         return false;
      }
   }

   @Override
   public void rebuildPageCounters() throws Exception {
      // managementLock will guarantee there's only one management operation being called
      try (AutoCloseable lock = server.managementLock()) {
         Future<Object> task = server.getPagingManager().rebuildCounters(null);
         task.get();
      }
   }

   private ServiceComponent getEmbeddedWebServerComponent() throws ActiveMQIllegalStateException {
      for (ActiveMQComponent component : server.getExternalComponents()) {
         if (component instanceof WebServerComponentMarker) {
            return (ServiceComponent) component;
         }
      }
      throw ActiveMQMessageBundle.BUNDLE.embeddedWebServerNotFound();
   }

   @Override
   public void clearAuthenticationCache() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.clearAuthenticationCache(this.server);
      }
      ((SecurityStoreImpl)server.getSecurityStore()).invalidateAuthenticationCache();
   }

   @Override
   public void clearAuthorizationCache() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.clearAuthorizationCache(this.server);
      }
      ((SecurityStoreImpl)server.getSecurityStore()).invalidateAuthorizationCache();
   }

   @Override
   public long getAuthenticationSuccessCount() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getAuthenticationSuccessCount(this.server);
      }
      return server.getSecurityStore().getAuthenticationSuccessCount();
   }

   @Override
   public long getAuthenticationFailureCount() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getAuthenticationFailureCount(this.server);
      }
      return server.getSecurityStore().getAuthenticationFailureCount();
   }

   @Override
   public long getAuthorizationSuccessCount() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getAuthorizationSuccessCount(this.server);
      }
      return server.getSecurityStore().getAuthorizationSuccessCount();
   }

   @Override
   public long getAuthorizationFailureCount() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getAuthorizationFailureCount(this.server);
      }
      return server.getSecurityStore().getAuthorizationFailureCount();
   }

   @Override
   public int getPendingMirrorAcks() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getPendingMirrorAcks(this.server);
      }
      return server.getPendingMirrorAcks();
   }

   @Override
   public void exportConfigAsProperties() throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.exportConfigAsProperties(this.server);
      }

      try (AutoCloseable lock = server.managementLock()) {
         if (System.getProperty(TMP_DIR_SYSTEM_PROPERTY) == null) {
            throw new IllegalStateException(TMP_DIR_SYSTEM_PROPERTY + " system property must be set");
         } else {
            File exportFileInTmp = new File(System.getProperty(TMP_DIR_SYSTEM_PROPERTY), CONFIG_AS_PROPERTIES_FILE);
            this.server.getConfiguration().exportAsProperties(exportFileInTmp);
         }
      }
   }

   public ActiveMQServer getServer() {
      return server;
   }
}

