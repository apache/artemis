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
package org.apache.activemq.artemis.core.server.management.impl;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.NotificationBroadcasterSupport;
import javax.management.ObjectName;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.micrometer.core.instrument.Tag;
import org.apache.activemq.artemis.api.core.BroadcastEndpointFactory;
import org.apache.activemq.artemis.api.core.BroadcastGroupConfiguration;
import org.apache.activemq.artemis.api.core.ChannelBroadcastEndpointFactory;
import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.JGroupsFileBroadcastEndpointFactory;
import org.apache.activemq.artemis.api.core.JsonUtil;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.UDPBroadcastEndpointFactory;
import org.apache.activemq.artemis.api.core.management.AcceptorControl;
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl;
import org.apache.activemq.artemis.api.core.management.AddressControl;
import org.apache.activemq.artemis.api.core.management.BaseBroadcastGroupControl;
import org.apache.activemq.artemis.api.core.management.BridgeControl;
import org.apache.activemq.artemis.api.core.management.BrokerConnectionControl;
import org.apache.activemq.artemis.api.core.management.ClusterConnectionControl;
import org.apache.activemq.artemis.api.core.management.ConnectionRouterControl;
import org.apache.activemq.artemis.api.core.management.DivertControl;
import org.apache.activemq.artemis.api.core.management.ManagementHelper;
import org.apache.activemq.artemis.api.core.management.ObjectNameBuilder;
import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.api.core.management.RemoteBrokerConnectionControl;
import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.core.config.ClusterConnectionConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.management.impl.AcceptorControlImpl;
import org.apache.activemq.artemis.core.management.impl.ActiveMQServerControlImpl;
import org.apache.activemq.artemis.core.management.impl.AddressControlImpl;
import org.apache.activemq.artemis.core.management.impl.BaseBroadcastGroupControlImpl;
import org.apache.activemq.artemis.core.management.impl.BridgeControlImpl;
import org.apache.activemq.artemis.core.management.impl.BroadcastGroupControlImpl;
import org.apache.activemq.artemis.core.management.impl.BrokerConnectionControlImpl;
import org.apache.activemq.artemis.core.management.impl.ClusterConnectionControlImpl;
import org.apache.activemq.artemis.core.management.impl.ConnectionRouterControlImpl;
import org.apache.activemq.artemis.core.management.impl.DivertControlImpl;
import org.apache.activemq.artemis.core.management.impl.JGroupsChannelBroadcastGroupControlImpl;
import org.apache.activemq.artemis.core.management.impl.JGroupsFileBroadcastGroupControlImpl;
import org.apache.activemq.artemis.core.management.impl.QueueControlImpl;
import org.apache.activemq.artemis.core.management.impl.RemoteBrokerConnectionControlImpl;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.messagecounter.MessageCounter;
import org.apache.activemq.artemis.core.messagecounter.MessageCounterManager;
import org.apache.activemq.artemis.core.messagecounter.impl.MessageCounterManagerImpl;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.postoffice.PostOffice;
import org.apache.activemq.artemis.core.remoting.server.RemotingService;
import org.apache.activemq.artemis.core.security.CheckType;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.security.SecurityAuth;
import org.apache.activemq.artemis.core.security.SecurityStore;
import org.apache.activemq.artemis.core.server.ActiveMQMessageBundle;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.BrokerConnection;
import org.apache.activemq.artemis.core.server.Divert;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.QueueFactory;
import org.apache.activemq.artemis.core.server.RemoteBrokerConnection;
import org.apache.activemq.artemis.core.server.cluster.Bridge;
import org.apache.activemq.artemis.core.server.cluster.BroadcastGroup;
import org.apache.activemq.artemis.core.server.cluster.ClusterConnection;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.impl.CleaningActivateCallback;
import org.apache.activemq.artemis.core.server.management.GuardInvocationHandler;
import org.apache.activemq.artemis.core.server.management.HawtioSecurityControl;
import org.apache.activemq.artemis.core.server.management.ManagementService;
import org.apache.activemq.artemis.core.server.management.Notification;
import org.apache.activemq.artemis.core.server.management.NotificationListener;
import org.apache.activemq.artemis.core.server.metrics.AddressMetricNames;
import org.apache.activemq.artemis.core.server.metrics.BrokerMetricNames;
import org.apache.activemq.artemis.core.server.metrics.MetricsManager;
import org.apache.activemq.artemis.core.server.metrics.QueueMetricNames;
import org.apache.activemq.artemis.core.server.routing.ConnectionRouter;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.transaction.ResourceManager;
import org.apache.activemq.artemis.spi.core.remoting.Acceptor;
import org.apache.activemq.artemis.utils.collections.ConcurrentHashSet;
import org.apache.activemq.artemis.utils.collections.TypedProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.activemq.artemis.api.core.FilterConstants.NATIVE_MESSAGE_ID;

public class ManagementServiceImpl implements ManagementService {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final MBeanServer mbeanServer;

   private final boolean jmxManagementEnabled;

   private final Map<String, Object> registry;

   // we keep a second map just for queues for quick searching when many exist
   private final Map<String, QueueControl> queueControls;

   // we keep a second map just for queues for quick searching when many exist
   private final Map<String, AddressControl> addressControls;

   private final NotificationBroadcasterSupport broadcaster;

   private PostOffice postOffice;

   private SecurityStore securityStore;

   private PagingManager pagingManager;

   private StorageManager storageManager;

   private ActiveMQServer messagingServer;

   private HierarchicalRepository<Set<Role>> securityRepository;

   private HierarchicalRepository<AddressSettings> addressSettingsRepository;

   private ActiveMQServerControlImpl messagingServerControl;

   private MessageCounterManager messageCounterManager;

   private final SimpleString managementNotificationAddress;

   private final SimpleString managementAddress;

   private volatile boolean started = false;

   private final boolean messageCounterEnabled;

   private boolean notificationsEnabled;

   private final Set<NotificationListener> listeners = new ConcurrentHashSet<>();

   private final ObjectNameBuilder objectNameBuilder;

   private final SimpleString managementMessageRbacResourceNamePrefix;

   private final Pattern viewPermissionMatcher;

   private final Set<ObjectName> registeredNames = new ConcurrentHashSet<>();

   public ManagementServiceImpl(final MBeanServer mbeanServer, final Configuration configuration) {
      this.mbeanServer = mbeanServer;
      jmxManagementEnabled = configuration.isJMXManagementEnabled();
      messageCounterEnabled = configuration.isMessageCounterEnabled();
      managementAddress = configuration.getManagementAddress();
      managementNotificationAddress = configuration.getManagementNotificationAddress();

      registry = new ConcurrentHashMap<>();
      queueControls = new ConcurrentHashMap<>();
      addressControls = new ConcurrentHashMap<>();
      broadcaster = new NotificationBroadcasterSupport();
      notificationsEnabled = true;
      objectNameBuilder = ObjectNameBuilder.create(configuration.getJMXDomain(), configuration.getName(), configuration.isJMXUseBrokerName());
      managementMessageRbacResourceNamePrefix = configuration.isManagementMessageRbac() ? SimpleString.of(configuration.getManagementRbacPrefix()).concat('.') : null;
      viewPermissionMatcher = Pattern.compile(configuration.getViewPermissionMethodMatchPattern());
   }


   // ManagementService implementation -------------------------

   @Override
   public ObjectNameBuilder getObjectNameBuilder() {
      return objectNameBuilder;
   }

   @Override
   public MessageCounterManager getMessageCounterManager() {
      return messageCounterManager;
   }

   @Override
   public void setStorageManager(final StorageManager storageManager) {
      this.storageManager = storageManager;
   }

   @Override
   public ActiveMQServerControlImpl registerServer(final PostOffice postOffice,
                                                   final SecurityStore securityStore,
                                                   final StorageManager storageManager1,
                                                   final Configuration configuration,
                                                   final HierarchicalRepository<AddressSettings> addressSettingsRepository,
                                                   final HierarchicalRepository<Set<Role>> securityRepository,
                                                   final ResourceManager resourceManager,
                                                   final RemotingService remotingService,
                                                   final ActiveMQServer messagingServer,
                                                   final QueueFactory queueFactory,
                                                   final ScheduledExecutorService scheduledThreadPool,
                                                   final PagingManager pagingManager,
                                                   final boolean backup) throws Exception {
      this.postOffice = postOffice;
      this.securityStore = securityStore;
      this.addressSettingsRepository = addressSettingsRepository;
      this.securityRepository = securityRepository;
      this.storageManager = storageManager1;
      this.messagingServer = messagingServer;
      this.pagingManager = pagingManager;

      messageCounterManager = new MessageCounterManagerImpl(scheduledThreadPool, messagingServer.getExecutorFactory().getExecutor());
      messageCounterManager.setMaxDayCount(configuration.getMessageCounterMaxDayHistory());
      messageCounterManager.reschedule(configuration.getMessageCounterSamplePeriod());

      messagingServerControl = new ActiveMQServerControlImpl(postOffice, configuration, resourceManager, remotingService, messagingServer, messageCounterManager, storageManager1, broadcaster);
      ObjectName objectName = objectNameBuilder.getActiveMQServerObjectName();
      registerInJMX(objectName, messagingServerControl);
      registerInRegistry(ResourceNames.BROKER, messagingServerControl);
      registerBrokerMeters();

      return messagingServerControl;
   }

   private void registerBrokerMeters() {
      MetricsManager metricsManager = messagingServer.getMetricsManager();
      if (metricsManager != null) {
         metricsManager.registerBrokerGauge(builder -> {
            builder.build(BrokerMetricNames.CONNECTION_COUNT, messagingServer, metrics -> (double) messagingServer.getConnectionCount(), ActiveMQServerControl.CONNECTION_COUNT_DESCRIPTION, Collections.emptyList());
            builder.build(BrokerMetricNames.TOTAL_CONNECTION_COUNT, messagingServer, metrics -> (double) messagingServer.getTotalConnectionCount(), ActiveMQServerControl.TOTAL_CONNECTION_COUNT_DESCRIPTION, Collections.emptyList());
            builder.build(BrokerMetricNames.SESSION_COUNT, messagingServer, metrics -> (double) messagingServer.getSessionCount(), ActiveMQServerControl.SESSION_COUNT_DESCRIPTION, Collections.emptyList());
            builder.build(BrokerMetricNames.TOTAL_SESSION_COUNT, messagingServer, metrics -> (double) messagingServer.getTotalSessionCount(), ActiveMQServerControl.TOTAL_SESSION_COUNT_DESCRIPTION, Collections.emptyList());
            builder.build(BrokerMetricNames.ADDRESS_MEMORY_USAGE, messagingServer, metrics -> (double) messagingServerControl.getAddressMemoryUsage(), ActiveMQServerControl.ADDRESS_MEMORY_USAGE_DESCRIPTION, Collections.emptyList());
            builder.build(BrokerMetricNames.ADDRESS_MEMORY_USAGE_PERCENTAGE, messagingServer, metrics -> (double) messagingServerControl.getAddressMemoryUsagePercentage(), ActiveMQServerControl.ADDRESS_MEMORY_USAGE_PERCENTAGE_DESCRIPTION, Collections.emptyList());
            builder.build(BrokerMetricNames.DISK_STORE_USAGE, messagingServer, metrics -> messagingServer.getDiskStoreUsage(), ActiveMQServerControl.DISK_STORE_USAGE_DESCRIPTION, Collections.emptyList());
            builder.build(BrokerMetricNames.REPLICA_SYNC, messagingServer, metrics -> messagingServer.isReplicaSync() ? 1D : 0D, ActiveMQServerControl.REPLICA_SYNC_DESCRIPTION, Collections.emptyList());
            builder.build(BrokerMetricNames.ACTIVE, messagingServer, metrics -> messagingServer.isActive() ? 1D : 0D, ActiveMQServerControl.IS_ACTIVE_DESCRIPTION, Collections.emptyList());
            builder.build(BrokerMetricNames.AUTHENTICATION_COUNT, securityStore, metrics -> (double) securityStore.getAuthenticationSuccessCount(), ActiveMQServerControl.AUTHENTICATION_SUCCESS_COUNT, Arrays.asList(Tag.of("result", "success")));
            builder.build(BrokerMetricNames.AUTHENTICATION_COUNT, securityStore, metrics -> (double) securityStore.getAuthenticationFailureCount(), ActiveMQServerControl.AUTHENTICATION_FAILURE_COUNT, Arrays.asList(Tag.of("result", "failure")));
            builder.build(BrokerMetricNames.AUTHORIZATION_COUNT, securityStore, metrics -> (double) securityStore.getAuthorizationSuccessCount(), ActiveMQServerControl.AUTHORIZATION_SUCCESS_COUNT, Arrays.asList(Tag.of("result", "success")));
            builder.build(BrokerMetricNames.AUTHORIZATION_COUNT, securityStore, metrics -> (double) securityStore.getAuthorizationFailureCount(), ActiveMQServerControl.AUTHORIZATION_FAILURE_COUNT, Arrays.asList(Tag.of("result", "failure")));
         });
      }
   }

   @Override
   public void unregisterServer() throws Exception {
      unregisterFromJMX(objectNameBuilder.getActiveMQServerObjectName());
      unregisterFromRegistry(ResourceNames.BROKER);
      if (messagingServer != null) {
         unregisterMeters(ResourceNames.BROKER + "." + messagingServer.getConfiguration().getName());
      }
   }

   @Override
   public void registerAddress(AddressInfo addressInfo) throws Exception {
      AddressControlImpl addressControl = new AddressControlImpl(addressInfo, messagingServer, pagingManager, storageManager, securityRepository, securityStore, this);
      registerInJMX(objectNameBuilder.getAddressObjectName(addressInfo.getName()), addressControl);
      registerInRegistry(ResourceNames.ADDRESS + addressInfo.getName(), addressControl);
      registerAddressControls(ResourceNames.ADDRESS + addressInfo.getName(), addressControl);
      registerAddressMeters(addressInfo, addressControl);
   }

   @Override
   public void registerAddressMeters(AddressInfo addressInfo, AddressControl addressControl) {
      if (messagingServer != null) { // it could be null on tests, but never on a real server
         MetricsManager metricsManager = messagingServer.getMetricsManager();
         if (metricsManager != null) {
            metricsManager.registerAddressGauge(addressInfo.getName().toString(), builder -> {
               builder.build(AddressMetricNames.ROUTED_MESSAGE_COUNT, addressInfo, metrics -> (double) addressInfo.getRoutedMessageCount(), AddressControl.ROUTED_MESSAGE_COUNT_DESCRIPTION, Collections.emptyList());
               builder.build(AddressMetricNames.UNROUTED_MESSAGE_COUNT, addressInfo, metrics -> (double) addressInfo.getUnRoutedMessageCount(), AddressControl.UNROUTED_MESSAGE_COUNT_DESCRIPTION, Collections.emptyList());
               builder.build(AddressMetricNames.ADDRESS_SIZE, addressInfo, metrics -> (double) addressControl.getAddressSize(), AddressControl.ADDRESS_SIZE_DESCRIPTION, Collections.emptyList());
               builder.build(AddressMetricNames.PAGES_COUNT, addressInfo, metrics -> (double) addressControl.getNumberOfPages(), AddressControl.NUMBER_OF_PAGES_DESCRIPTION, Collections.emptyList());
               builder.build(AddressMetricNames.LIMIT_PERCENT, addressInfo, metrics -> (double) addressControl.getAddressLimitPercent(), AddressControl.LIMIT_PERCENT_DESCRIPTION, Collections.emptyList());
            });
         }
      }
   }

   @Override
   public void unregisterAddress(final SimpleString address) throws Exception {
      unregisterFromJMX(objectNameBuilder.getAddressObjectName(address));
      unregisterFromRegistry(ResourceNames.ADDRESS + address);
      unregisterAddressControls(ResourceNames.ADDRESS + address);
      unregisterMeters(ResourceNames.ADDRESS + address);
   }

   @Override
   public void registerQueue(final Queue queue, final SimpleString address, final StorageManager storageManager) throws Exception {
      QueueControlImpl queueControl = new QueueControlImpl(queue, address.toString(), messagingServer, storageManager, securityStore, addressSettingsRepository);
      if (messageCounterManager != null) {
         MessageCounter counter = new MessageCounter(queue.getName().toString(), null, queue, false, queue.isDurable(), messageCounterManager.getMaxDayCount());
         queueControl.setMessageCounter(counter);
         messageCounterManager.registerMessageCounter(queue.getName().toString(), counter);
      }
      registerInJMX(objectNameBuilder.getQueueObjectName(address, queue.getName(), queue.getRoutingType()), queueControl);
      registerQueueControls(ResourceNames.QUEUE + queue.getName(), queueControl);
      registerQueueMeters(queue);
   }

   @Override
   public void unregisterQueue(final SimpleString name, final SimpleString address, RoutingType routingType) throws Exception {
      unregisterFromJMX(objectNameBuilder.getQueueObjectName(address, name, routingType));
      unregisterQueueControls(ResourceNames.QUEUE + name);
      unregisterMeters(ResourceNames.QUEUE + name);
      if (messageCounterManager != null) {
         messageCounterManager.unregisterMessageCounter(name.toString());
      }
   }

   @Override
   public List<QueueControl> getQueueControls(Predicate<QueueControl> predicate) {
      if (predicate == null) {
         return queueControls.values().stream().toList();
      }
      return queueControls.values().stream().filter(predicate).collect(Collectors.toList());
   }

   @Override
   public List<AddressControl> getAddressControls(Predicate<AddressControl> predicate) {
      if (predicate == null) {
         return addressControls.values().stream().toList();
      }
      return addressControls.values().stream().filter(predicate).collect(Collectors.toList());
   }

   private void registerQueueMeters(final Queue queue) {
      if (messagingServer != null) { // messagingServer could be null on certain unit tests where metrics are not relevant
         MetricsManager metricsManager = messagingServer.getMetricsManager();
         if (metricsManager != null) {
            metricsManager.registerQueueGauge(queue.getAddress().toString(), queue.getName().toString(), (builder) -> {
               builder.build(QueueMetricNames.MESSAGE_COUNT, queue, metrics -> (double) queue.getMessageCount(), QueueControl.MESSAGE_COUNT_DESCRIPTION, Collections.emptyList());
               builder.build(QueueMetricNames.DURABLE_MESSAGE_COUNT, queue, metrics -> (double) queue.getDurableMessageCount(), QueueControl.DURABLE_MESSAGE_COUNT_DESCRIPTION, Collections.emptyList());
               builder.build(QueueMetricNames.PERSISTENT_SIZE, queue, metrics -> (double) queue.getPersistentSize(), QueueControl.PERSISTENT_SIZE_DESCRIPTION, Collections.emptyList());
               builder.build(QueueMetricNames.DURABLE_PERSISTENT_SIZE, queue, metrics -> (double) queue.getDurablePersistentSize(), QueueControl.DURABLE_PERSISTENT_SIZE_DESCRIPTION, Collections.emptyList());

               builder.build(QueueMetricNames.DELIVERING_MESSAGE_COUNT, queue, metrics -> (double) queue.getDeliveringCount(), QueueControl.DELIVERING_MESSAGE_COUNT_DESCRIPTION, Collections.emptyList());
               builder.build(QueueMetricNames.DELIVERING_DURABLE_MESSAGE_COUNT, queue, metrics -> (double) queue.getDurableDeliveringCount(), QueueControl.DURABLE_DELIVERING_MESSAGE_COUNT_DESCRIPTION, Collections.emptyList());
               builder.build(QueueMetricNames.DELIVERING_PERSISTENT_SIZE, queue, metrics -> (double) queue.getDeliveringSize(), QueueControl.DELIVERING_SIZE_DESCRIPTION, Collections.emptyList());
               builder.build(QueueMetricNames.DELIVERING_DURABLE_PERSISTENT_SIZE, queue, metrics -> (double) queue.getDurableDeliveringSize(), QueueControl.DURABLE_DELIVERING_SIZE_DESCRIPTION, Collections.emptyList());

               builder.build(QueueMetricNames.SCHEDULED_MESSAGE_COUNT, queue, metrics -> (double) queue.getScheduledCount(), QueueControl.SCHEDULED_MESSAGE_COUNT_DESCRIPTION, Collections.emptyList());
               builder.build(QueueMetricNames.SCHEDULED_DURABLE_MESSAGE_COUNT, queue, metrics -> (double) queue.getDurableScheduledCount(), QueueControl.DURABLE_SCHEDULED_MESSAGE_COUNT_DESCRIPTION, Collections.emptyList());
               builder.build(QueueMetricNames.SCHEDULED_PERSISTENT_SIZE, queue, metrics -> (double) queue.getScheduledSize(), QueueControl.SCHEDULED_SIZE_DESCRIPTION, Collections.emptyList());
               builder.build(QueueMetricNames.SCHEDULED_DURABLE_PERSISTENT_SIZE, queue, metrics -> (double) queue.getDurableScheduledSize(), QueueControl.DURABLE_SCHEDULED_SIZE_DESCRIPTION, Collections.emptyList());

               builder.build(QueueMetricNames.MESSAGES_ACKNOWLEDGED, queue, metrics -> (double) queue.getMessagesAcknowledged(), QueueControl.MESSAGES_ACKNOWLEDGED_DESCRIPTION, Collections.emptyList());
               builder.build(QueueMetricNames.MESSAGES_ADDED, queue, metrics -> (double) queue.getMessagesAdded(), QueueControl.MESSAGES_ADDED_DESCRIPTION, Collections.emptyList());
               builder.build(QueueMetricNames.MESSAGES_KILLED, queue, metrics -> (double) queue.getMessagesKilled(), QueueControl.MESSAGES_KILLED_DESCRIPTION, Collections.emptyList());
               builder.build(QueueMetricNames.MESSAGES_EXPIRED, queue, metrics -> (double) queue.getMessagesExpired(), QueueControl.MESSAGES_EXPIRED_DESCRIPTION, Collections.emptyList());
               builder.build(QueueMetricNames.CONSUMER_COUNT, queue, metrics -> (double) queue.getConsumerCount(), QueueControl.CONSUMER_COUNT_DESCRIPTION, Collections.emptyList());
            });
         }
      }
   }

   private void unregisterMeters(final String name) {
      if (messagingServer != null) {
         MetricsManager metricsManager = messagingServer.getMetricsManager();
         if (metricsManager != null) {
            metricsManager.remove(name);
         }
      }
   }

   @Override
   public void registerDivert(final Divert divert) throws Exception {
      DivertControl divertControl = new DivertControlImpl(divert, storageManager, messagingServer.getInternalNamingPrefix());
      registerInJMX(objectNameBuilder.getDivertObjectName(divert.getUniqueName().toString(), divert.getAddress().toString()), divertControl);
      registerInRegistry(ResourceNames.DIVERT + divert.getUniqueName(), divertControl);
   }

   @Override
   public void unregisterDivert(final SimpleString name, final SimpleString address) throws Exception {
      unregisterFromJMX(objectNameBuilder.getDivertObjectName(name.toString(), address.toString()));
      unregisterFromRegistry(ResourceNames.DIVERT + name);
   }

   @Override
   public void registerAcceptor(final Acceptor acceptor, final TransportConfiguration configuration) throws Exception {
      AcceptorControl control = new AcceptorControlImpl(acceptor, storageManager, configuration);
      registerInJMX(objectNameBuilder.getAcceptorObjectName(configuration.getName()), control);
      registerInRegistry(ResourceNames.ACCEPTOR + configuration.getName(), control);
   }

   @Override
   public void unregisterAcceptors() {
      for (String resourceName : new HashSet<>(registry.keySet())) {
         if (resourceName.startsWith(ResourceNames.ACCEPTOR)) {
            String name = resourceName.substring(ResourceNames.ACCEPTOR.length());
            try {
               unregisterAcceptor(name);
            } catch (Exception e) {
               ActiveMQServerLogger.LOGGER.failedToUnregisterAcceptor(name, e);
            }
         }
      }
   }

   @Override
   public void unregisterAcceptor(final String name) throws Exception {
      unregisterFromJMX(objectNameBuilder.getAcceptorObjectName(name));
      unregisterFromRegistry(ResourceNames.ACCEPTOR + name);
   }

   @Override
   public void registerBroadcastGroup(final BroadcastGroup broadcastGroup, final BroadcastGroupConfiguration configuration) throws Exception {
      broadcastGroup.setNotificationService(this);
      BroadcastEndpointFactory endpointFactory = configuration.getEndpointFactory();
      BaseBroadcastGroupControl control = null;
      if (endpointFactory instanceof UDPBroadcastEndpointFactory factory) {
         control = new BroadcastGroupControlImpl(broadcastGroup, storageManager, configuration, factory);
      } else if (endpointFactory instanceof JGroupsFileBroadcastEndpointFactory factory) {
         control = new JGroupsFileBroadcastGroupControlImpl(broadcastGroup, storageManager, configuration, factory);
      } else if (endpointFactory instanceof ChannelBroadcastEndpointFactory factory) {
         control = new JGroupsChannelBroadcastGroupControlImpl(broadcastGroup, storageManager, configuration, factory);
      } else {
         control = new BaseBroadcastGroupControlImpl(broadcastGroup, storageManager, configuration);
      }
      registerInJMX(objectNameBuilder.getBroadcastGroupObjectName(configuration.getName()), control);
      registerInRegistry(ResourceNames.BROADCAST_GROUP + configuration.getName(), control);
   }

   @Override
   public void unregisterBroadcastGroup(final String name) throws Exception {
      unregisterFromJMX(objectNameBuilder.getBroadcastGroupObjectName(name));
      unregisterFromRegistry(ResourceNames.BROADCAST_GROUP + name);
   }

   @Override
   public void registerBrokerConnection(BrokerConnection brokerConnection) throws Exception {
      BrokerConnectionControl control = new BrokerConnectionControlImpl(brokerConnection, storageManager);
      registerInJMX(objectNameBuilder.getBrokerConnectionObjectName(brokerConnection.getName()), control);
      registerInRegistry(ResourceNames.BROKER_CONNECTION + brokerConnection.getName(), control);
   }

   @Override
   public void unregisterBrokerConnection(String name) throws Exception {
      unregisterFromJMX(objectNameBuilder.getBrokerConnectionObjectName(name));
      unregisterFromRegistry(ResourceNames.BROKER_CONNECTION + name);
   }

   @Override
   public void registerRemoteBrokerConnection(RemoteBrokerConnection brokerConnection) throws Exception {
      final String nodeId = brokerConnection.getNodeId();
      final String name = brokerConnection.getName();

      Objects.requireNonNull(nodeId, "Remote Node ID must be a non-null value");
      Objects.requireNonNull(name, "Remote connection name must be a non-null value");

      if (nodeId.isBlank()) {
         throw new IllegalArgumentException("Remote node ID must be a non-empty string value");
      }

      if (name.isBlank()) {
         throw new IllegalArgumentException("Remote connection name must be a non-empty string value");
      }

      RemoteBrokerConnectionControl control = new RemoteBrokerConnectionControlImpl(brokerConnection, storageManager);
      registerInJMX(objectNameBuilder.getRemoteBrokerConnectionObjectName(nodeId, name), control);
      registerInRegistry(ResourceNames.REMOTE_BROKER_CONNECTION + nodeId + "." + name, control);
   }

   @Override
   public void unregisterRemoteBrokerConnection(String nodeId, String name) throws Exception {
      unregisterFromJMX(objectNameBuilder.getRemoteBrokerConnectionObjectName(nodeId, name));
      unregisterFromRegistry(ResourceNames.REMOTE_BROKER_CONNECTION + nodeId + "." + name);
   }

   @Override
   public void registerBridge(final Bridge bridge) throws Exception {
      bridge.setNotificationService(this);
      BridgeControl control = new BridgeControlImpl(bridge, storageManager);
      registerInJMX(objectNameBuilder.getBridgeObjectName(bridge.getConfiguration().getName()), control);
      registerInRegistry(ResourceNames.BRIDGE + bridge.getName(), control);
   }

   @Override
   public void unregisterBridge(final String name) throws Exception {
      unregisterFromJMX(objectNameBuilder.getBridgeObjectName(name));
      unregisterFromRegistry(ResourceNames.BRIDGE + name);
   }

   @Override
   public void registerCluster(final ClusterConnection cluster, final ClusterConnectionConfiguration configuration) throws Exception {
      ClusterConnectionControl control = new ClusterConnectionControlImpl(cluster, storageManager, configuration);
      registerInJMX(objectNameBuilder.getClusterConnectionObjectName(configuration.getName()), control);
      registerInRegistry(ResourceNames.CORE_CLUSTER_CONNECTION + configuration.getName(), control);
   }

   @Override
   public void unregisterCluster(final String name) throws Exception {
      unregisterFromJMX(objectNameBuilder.getClusterConnectionObjectName(name));
      unregisterFromRegistry(ResourceNames.CORE_CLUSTER_CONNECTION + name);
   }

   @Override
   public void registerConnectionRouter(final ConnectionRouter router) throws Exception {
      ConnectionRouterControl connectionRouterControl = new ConnectionRouterControlImpl(router, storageManager);
      registerInJMX(objectNameBuilder.getConnectionRouterObjectName(router.getName()), connectionRouterControl);
      registerInRegistry(ResourceNames.CONNECTION_ROUTER + router.getName(), connectionRouterControl);
   }

   @Override
   public void unregisterConnectionRouter(final String name) throws Exception {
      unregisterFromJMX(objectNameBuilder.getConnectionRouterObjectName(name));
      unregisterFromRegistry(ResourceNames.CONNECTION_ROUTER + name);
   }

   @Override
   public void registerHawtioSecurity(GuardInvocationHandler guard) throws Exception {
      HawtioSecurityControl control = new HawtioSecurityControlImpl(guard, storageManager);
      registerInJMX(objectNameBuilder.getSecurityObjectName(), control);
      registerInRegistry(ResourceNames.MANAGEMENT_SECURITY, control);
   }

   @Override
   public void unregisterHawtioSecurity() throws Exception {
      unregisterFromJMX(objectNameBuilder.getSecurityObjectName());
      unregisterFromRegistry(ResourceNames.MANAGEMENT_SECURITY);
   }

   @Override
   public ICoreMessage handleMessage(SecurityAuth auth, Message message) throws Exception {
      message = message.toCore();
      // a reply message is sent with the result stored in the message body.
      CoreMessage reply = new CoreMessage(storageManager.generateID(), 512);
      reply.setType(Message.TEXT_TYPE);
      reply.setReplyTo(message.getReplyTo());

      Object correlationID = getCorrelationIdentity(message);
      if (correlationID != null) {
         reply.setCorrelationID(correlationID);
      }

      String resourceName = message.getStringProperty(ManagementHelper.HDR_RESOURCE_NAME);

      logger.debug("handling management message for {}", resourceName);

      String operation = message.getStringProperty(ManagementHelper.HDR_OPERATION_NAME);

      if (operation != null) {
         Object[] params = ManagementHelper.retrieveOperationParameters(message);

         if (params == null) {
            params = new Object[0];
         }

         try {
            Object result = invokeOperation(resourceName, operation, params, auth);

            ManagementHelper.storeResult(reply, result);

            reply.putBooleanProperty(ManagementHelper.HDR_OPERATION_SUCCEEDED, true);
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.managementOperationError(operation, resourceName, e);
            reply.putBooleanProperty(ManagementHelper.HDR_OPERATION_SUCCEEDED, false);
            String exceptionMessage;
            if (e instanceof InvocationTargetException invocationTargetException) {
               exceptionMessage = invocationTargetException.getTargetException().getMessage();
            } else {
               exceptionMessage = e.getMessage();
            }
            ManagementHelper.storeResult(reply, exceptionMessage);
         }
      } else {
         String attribute = message.getStringProperty(ManagementHelper.HDR_ATTRIBUTE);

         if (attribute != null) {
            try {
               Object result = getAttribute(resourceName, attribute, auth);

               ManagementHelper.storeResult(reply, result);

               reply.putBooleanProperty(ManagementHelper.HDR_OPERATION_SUCCEEDED, true);
            } catch (Exception e) {
               ActiveMQServerLogger.LOGGER.managementAttributeError(attribute, resourceName, e);
               reply.putBooleanProperty(ManagementHelper.HDR_OPERATION_SUCCEEDED, false);
               String exceptionMessage;
               if (e instanceof InvocationTargetException invocationTargetException) {
                  exceptionMessage = invocationTargetException.getTargetException().getMessage();
               } else {
                  exceptionMessage = e.getMessage();
               }
               ManagementHelper.storeResult(reply, exceptionMessage);
            }
         }
      }

      return reply;
   }

   protected void securityCheck(String controlName, CheckType permission, SecurityAuth auth) throws Exception {
      if (managementMessageRbacResourceNamePrefix == null) {
         return;
      }
      final SimpleString address = managementMessageRbacResourceNamePrefix.concat(controlName);
      securityStore.check(address, permission, auth);
   }

   protected CheckType permissionForInvoke(String method) {
      if (viewPermissionMatcher.matcher(method).matches()) {
         return CheckType.VIEW;
      }
      return CheckType.EDIT;
   }

   @Override
   public Object getResource(final String resourceName) {
      return registry.get(resourceName);
   }

   @Override
   public Object[] getResources(final Class<?> resourceType) {
      List<Object> resources = new ArrayList<>();
      for (Object entry : new ArrayList<>(registry.values())) {
         if (resourceType.isAssignableFrom(entry.getClass())) {
            resources.add(entry);
         }
      }
      return resources.toArray(new Object[resources.size()]);
   }

   @Override
   public void registerInJMX(final ObjectName objectName, final Object managedResource) throws Exception {
      if (!jmxManagementEnabled) {
         return;
      }

      synchronized (mbeanServer) {
         unregisterFromJMX(objectName);
         mbeanServer.registerMBean(managedResource, objectName);
         registeredNames.add(objectName);
      }
      logger.debug("Registered in JMX: {} as {}", objectName, managedResource);
   }

   // the JMX unregistration is synchronized to avoid race conditions if 2 clients try to
   // unregister the same resource (e.g. a queue) at the same time since unregisterMBean()
   // will throw an exception if the MBean has already been unregistered
   @Override
   public void unregisterFromJMX(final ObjectName objectName) throws MBeanRegistrationException, InstanceNotFoundException {
      if (!jmxManagementEnabled) {
         return;
      }

      synchronized (mbeanServer) {
         if (mbeanServer.isRegistered(objectName)) {
            mbeanServer.unregisterMBean(objectName);
            registeredNames.remove(objectName);
         }
      }
      logger.debug("Unregistered from JMX: {}", objectName);
   }

   @Override
   public void registerInRegistry(final String resourceName, final Object managedResource) {
      Object replaced = registry.put(resourceName, managedResource);
      String addendum = "";
      if (replaced != null) {
         addendum = ". Replaced: " + replaced;
      }
      logger.debug("Registered in management: {} as {}{}", resourceName, managedResource, addendum);
   }

   @Override
   public void unregisterFromRegistry(final String resourceName) {
      Object removed = registry.remove(resourceName);
      if (removed != null) {
         logger.debug("Unregistered from management: {} as {}", resourceName, removed);
      } else {
         logger.debug("Attempted to unregister {} from management, but it was not registered.");
      }
   }

   private void unregisterQueueControls(final String resourceName) {
      unregisterFromRegistry(resourceName);
      queueControls.remove(resourceName);
   }

   private void registerQueueControls(final String resourceName, final QueueControl queueControl) {
      registerInRegistry(resourceName, queueControl);
      queueControls.put(resourceName, queueControl);
   }

   private void unregisterAddressControls(final String resourceName) {
      unregisterFromRegistry(resourceName);
      addressControls.remove(resourceName);
   }

   private void registerAddressControls(final String resourceName, final AddressControl addressControl) {
      registerInRegistry(resourceName, addressControl);
      addressControls.put(resourceName, addressControl);
   }

   @Override
   public void addNotificationListener(final NotificationListener listener) {
      listeners.add(listener);
   }

   @Override
   public void removeNotificationListener(final NotificationListener listener) {
      listeners.remove(listener);
   }

   @Override
   public SimpleString getManagementAddress() {
      return managementAddress;
   }

   @Override
   public SimpleString getManagementNotificationAddress() {
      return managementNotificationAddress;
   }

   @Override
   public synchronized void start() throws Exception {
      if (started) {
         return;
      }

      if (messageCounterEnabled) {
         messageCounterManager.start();
      }

      /*
       * Ensure the management notification address is created otherwise if auto-create-address = false then cluster
       * bridges won't be able to connect.
       */
      messagingServer.registerActivateCallback(new CleaningActivateCallback() {
         @Override
         public void activated() {
            try {
               ActiveMQServer usedServer = messagingServer;
               if (usedServer != null) {
                  usedServer.addAddressInfo(new AddressInfo(managementNotificationAddress, RoutingType.MULTICAST));
               }
            } catch (Exception e) {
               ActiveMQServerLogger.LOGGER.unableToCreateManagementNotificationAddress(managementNotificationAddress, e);
            }
         }
      });

      started = true;
   }

   @Override
   public synchronized void stop() throws Exception {
      if (!started) {
         return;
      }

      started = false;

      Set<String> resourceNames = new HashSet<>(registry.keySet());

      for (String resourceName : resourceNames) {
         unregisterFromRegistry(resourceName);
         unregisterMeters(resourceName);
      }

      if (jmxManagementEnabled) {
         if (!registeredNames.isEmpty()) {
            List<String> unexpectedResourceNames = new ArrayList<>();
            for (String name : resourceNames) {
               // only addresses, queues, and diverts should still be registered
               if (!(name.startsWith(ResourceNames.ADDRESS) || name.startsWith(ResourceNames.QUEUE) || name.startsWith(ResourceNames.DIVERT))) {
                  unexpectedResourceNames.add(name);
               }
            }
            if (!unexpectedResourceNames.isEmpty()) {
               ActiveMQServerLogger.LOGGER.managementStopError(unexpectedResourceNames.size(), unexpectedResourceNames);
            }

            for (ObjectName on : registeredNames) {
               try {
                  mbeanServer.unregisterMBean(on);
               } catch (Exception ignore) {
               }
            }
         }
      }

      if (messageCounterManager != null) {
         messageCounterManager.stop();

         messageCounterManager.resetAllCounters();

         messageCounterManager.resetAllCounterHistories();

         messageCounterManager.clear();
      }

      listeners.clear();

      registry.clear();

      queueControls.clear();

      messagingServer = null;

      securityRepository = null;

      addressSettingsRepository = null;

      messagingServerControl = null;

      messageCounterManager = null;

      postOffice = null;

      pagingManager = null;

      storageManager = null;

      registeredNames.clear();
   }

   @Override
   public boolean isStarted() {
      return started;
   }

   @Override
   public void sendNotification(final Notification notification) throws Exception {
      if (messagingServerControl == null || !notificationsEnabled) {
         return;
      }

      // This needs to be synchronized since we need to ensure notifications are processed in strict sequence.
      // Furthermore, this needs to synchronize on the PostOffice notificationLock otherwise we can get notifications
      // arriving in the wrong order or missing notifications if one occurs at same time as sendQueueInfoToQueue is
      // processed.
      synchronized (postOffice.getNotificationLock()) {
         logger.trace("Sending notification={}", notification);

         // First send to any local listeners
         for (NotificationListener listener : listeners) {
            try {
               listener.onNotification(notification);
            } catch (Exception e) {
               // Exception thrown from one listener should not stop execution of others
               ActiveMQServerLogger.LOGGER.errorCallingNotifListener(e);
            }
         }

         // Start sending notification *messages* only when server has initialised. Note at backup initialisation we
         // don't want to send notifications either.
         if (messagingServer == null || !messagingServer.isActive()) {
            logger.debug("ignoring message {} as the server is not initialized", notification);
            return;
         }

         long messageID = storageManager.generateID();

         Message notificationMessage = new CoreMessage(messageID, 512);

         // Notification messages are always durable so the user can choose whether to add a durable queue to
         // consume them in
         notificationMessage.setDurable(true);
         notificationMessage.setAddress(managementNotificationAddress);

         if (notification.getProperties() != null) {
            TypedProperties props = notification.getProperties();
            props.forEach(notificationMessage::putObjectProperty);
         }

         notificationMessage.putStringProperty(ManagementHelper.HDR_NOTIFICATION_TYPE, SimpleString.of(notification.getType().toString()));

         long timestamp = System.currentTimeMillis();
         notificationMessage.putLongProperty(ManagementHelper.HDR_NOTIFICATION_TIMESTAMP, timestamp);
         notificationMessage.setTimestamp(timestamp);

         postOffice.route(notificationMessage, false);
      }
   }

   @Override
   public void enableNotifications(final boolean enabled) {
      notificationsEnabled = enabled;
   }

   @Override
   public Object getAttribute(final String resourceName, final String attribute, SecurityAuth auth) {
      try {
         Object resource = registry.get(resourceName);

         if (resource == null) {
            throw ActiveMQMessageBundle.BUNDLE.cannotFindResource(resourceName);
         }

         Method method;

         String upperCaseAttribute = attribute.substring(0, 1).toUpperCase() + attribute.substring(1);
         try {
            method = resource.getClass().getMethod("get" + upperCaseAttribute, new Class[0]);
         } catch (NoSuchMethodException nsme) {
            try {
               method = resource.getClass().getMethod("is" + upperCaseAttribute, new Class[0]);
            } catch (NoSuchMethodException nsme2) {
               throw ActiveMQMessageBundle.BUNDLE.noGetterMethod(attribute);
            }
         }

         final String methodName = method.getName();

         securityCheck(resourceName + "." + methodName, permissionForInvoke(methodName), auth);

         return method.invoke(resource, new Object[0]);
      } catch (Throwable t) {
         throw new IllegalStateException("Problem while retrieving attribute " + attribute, t);
      }
   }

   @Override
   public Object invokeOperation(final String resourceName,
                                 final String operation,
                                 final Object[] params,
                                 SecurityAuth auth) throws Exception {
      Object resource = registry.get(resourceName);

      if (resource == null) {
         throw ActiveMQMessageBundle.BUNDLE.cannotFindResource(resourceName);
      }

      Method method = null;

      Method[] methods = resource.getClass().getMethods();
      for (Method m : methods) {
         if (m.getName().equals(operation) && m.getParameterTypes().length == params.length) {
            boolean match = true;

            Class<?>[] paramTypes = m.getParameterTypes();

            for (int i = 0; i < paramTypes.length; i++) {
               if (params[i] == null) {
                  continue;
               }

               params[i] = JsonUtil.convertJsonValue(params[i], paramTypes[i]);

               if (paramTypes[i].isAssignableFrom(params[i].getClass()) || paramTypes[i] == Long.TYPE && params[i].getClass() == Integer.class ||
                  paramTypes[i] == Double.TYPE && params[i].getClass() == Integer.class ||
                  paramTypes[i] == Long.TYPE && params[i].getClass() == Long.class ||
                  paramTypes[i] == Double.TYPE && params[i].getClass() == Double.class ||
                  paramTypes[i] == Integer.TYPE && params[i].getClass() == Integer.class ||
                  paramTypes[i] == Boolean.TYPE && params[i].getClass() == Boolean.class ||
                  paramTypes[i] == Object[].class && params[i].getClass() == Object[].class) {
                  // parameter match
               } else {
                  match = false;
                  break; // parameter check loop
               }
            }

            if (match) {
               method = m;
               break; // method match loop
            }
         }
      }

      if (method == null) {
         throw ActiveMQMessageBundle.BUNDLE.noOperation(operation, params.length);
      }

      final String methodName = method.getName();
      securityCheck(resourceName + "." + methodName, permissionForInvoke(methodName), auth);

      return method.invoke(resource, params);
   }

   /**
    * Correlate management responses using the Correlation ID Pattern, if the request supplied a correlation id, or
    * fallback to the Message ID Pattern providing the request had a message id.
    *
    * @return correlation identify
    */
   private Object getCorrelationIdentity(final Message request) {
      Object correlationId = request.getCorrelationID();
      if (correlationId == null) {
         // CoreMessage#getUserId returns UUID, so to implement this part a alternative API that returned object. This part of the
         // change is a nice to have for my point of view. I suggested it for completeness.  The application could
         // always supply unique correl ids on the request and achieve the same effect.  I'd be happy to drop this part.
         correlationId = String.valueOf(request.getStringProperty(NATIVE_MESSAGE_ID) != null ? request.getStringProperty(NATIVE_MESSAGE_ID) : request.getUserID());
      }

      return correlationId;
   }
}
