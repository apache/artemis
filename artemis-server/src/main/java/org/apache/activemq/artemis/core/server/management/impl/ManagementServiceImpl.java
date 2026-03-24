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

import static org.apache.activemq.artemis.api.core.FilterConstants.NATIVE_MESSAGE_ID;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.NotificationBroadcasterSupport;
import javax.management.ObjectName;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.activemq.artemis.api.core.BroadcastEndpointFactory;
import org.apache.activemq.artemis.api.core.BroadcastGroupConfiguration;
import org.apache.activemq.artemis.api.core.ChannelBroadcastEndpointFactory;
import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.JGroupsFileBroadcastEndpointFactory;
import org.apache.activemq.artemis.api.core.JGroupsPropertiesBroadcastEndpointFactory;
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
import org.apache.activemq.artemis.api.core.management.BrokerBalancerControl;
import org.apache.activemq.artemis.api.core.management.ClusterConnectionControl;
import org.apache.activemq.artemis.api.core.management.DivertControl;
import org.apache.activemq.artemis.api.core.management.ManagementHelper;
import org.apache.activemq.artemis.api.core.management.ObjectNameBuilder;
import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.core.config.BridgeConfiguration;
import org.apache.activemq.artemis.core.config.ClusterConnectionConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.management.impl.AcceptorControlImpl;
import org.apache.activemq.artemis.core.management.impl.ActiveMQServerControlImpl;
import org.apache.activemq.artemis.core.management.impl.AddressControlImpl;
import org.apache.activemq.artemis.core.management.impl.BaseBroadcastGroupControlImpl;
import org.apache.activemq.artemis.core.management.impl.BridgeControlImpl;
import org.apache.activemq.artemis.core.management.impl.BroadcastGroupControlImpl;
import org.apache.activemq.artemis.core.management.impl.BrokerBalancerControlImpl;
import org.apache.activemq.artemis.core.management.impl.ClusterConnectionControlImpl;
import org.apache.activemq.artemis.core.management.impl.DivertControlImpl;
import org.apache.activemq.artemis.core.management.impl.JGroupsChannelBroadcastGroupControlImpl;
import org.apache.activemq.artemis.core.management.impl.JGroupsFileBroadcastGroupControlImpl;
import org.apache.activemq.artemis.core.management.impl.JGroupsPropertiesBroadcastGroupControlImpl;
import org.apache.activemq.artemis.core.management.impl.QueueControlImpl;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.messagecounter.MessageCounter;
import org.apache.activemq.artemis.core.messagecounter.MessageCounterManager;
import org.apache.activemq.artemis.core.messagecounter.impl.MessageCounterManagerImpl;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.postoffice.PostOffice;
import org.apache.activemq.artemis.core.remoting.server.RemotingService;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.security.SecurityStore;
import org.apache.activemq.artemis.core.server.ActiveMQMessageBundle;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.Divert;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.QueueFactory;
import org.apache.activemq.artemis.core.server.balancing.BrokerBalancer;
import org.apache.activemq.artemis.core.server.cluster.Bridge;
import org.apache.activemq.artemis.core.server.cluster.BroadcastGroup;
import org.apache.activemq.artemis.core.server.cluster.ClusterConnection;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.impl.CleaningActivateCallback;
import org.apache.activemq.artemis.core.server.management.ArtemisMBeanServerGuard;
import org.apache.activemq.artemis.core.server.management.HawtioSecurityControl;
import org.apache.activemq.artemis.core.server.management.ManagementService;
import org.apache.activemq.artemis.core.server.management.Notification;
import org.apache.activemq.artemis.core.server.management.NotificationListener;
import org.apache.activemq.artemis.core.server.metrics.AddressMetricNames;
import org.apache.activemq.artemis.core.server.metrics.BrokerMetricNames;
import org.apache.activemq.artemis.core.server.metrics.MetricsManager;
import org.apache.activemq.artemis.core.server.metrics.QueueMetricNames;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.transaction.ResourceManager;
import org.apache.activemq.artemis.spi.core.remoting.Acceptor;
import org.apache.activemq.artemis.utils.collections.ConcurrentHashSet;
import org.apache.activemq.artemis.utils.collections.TypedProperties;
import org.jboss.logging.Logger;

public class ManagementServiceImpl implements ManagementService {

   private static final Logger logger = Logger.getLogger(ManagementServiceImpl.class);

   private final MBeanServer mbeanServer;

   private final boolean jmxManagementEnabled;

   private final Map<String, Object> registry;

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

   private boolean started = false;

   private final boolean messageCounterEnabled;

   private boolean notificationsEnabled;

   private final Set<NotificationListener> listeners = new ConcurrentHashSet<>();

   private final ObjectNameBuilder objectNameBuilder;


   public ManagementServiceImpl(final MBeanServer mbeanServer, final Configuration configuration) {
      this.mbeanServer = mbeanServer;
      jmxManagementEnabled = configuration.isJMXManagementEnabled();
      messageCounterEnabled = configuration.isMessageCounterEnabled();
      managementAddress = configuration.getManagementAddress();
      managementNotificationAddress = configuration.getManagementNotificationAddress();

      registry = new ConcurrentHashMap<>();
      broadcaster = new NotificationBroadcasterSupport();
      notificationsEnabled = true;
      objectNameBuilder = ObjectNameBuilder.create(configuration.getJMXDomain(), configuration.getName(), configuration.isJMXUseBrokerName());
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
            builder.register(BrokerMetricNames.CONNECTION_COUNT, this, metrics -> Double.valueOf(messagingServer.getConnectionCount()), ActiveMQServerControl.CONNECTION_COUNT_DESCRIPTION);
            builder.register(BrokerMetricNames.TOTAL_CONNECTION_COUNT, this, metrics -> Double.valueOf(messagingServer.getTotalConnectionCount()), ActiveMQServerControl.TOTAL_CONNECTION_COUNT_DESCRIPTION);
            builder.register(BrokerMetricNames.ADDRESS_MEMORY_USAGE, this, metrics -> Double.valueOf(messagingServerControl.getAddressMemoryUsage()), ActiveMQServerControl.ADDRESS_MEMORY_USAGE_DESCRIPTION);
            builder.register(BrokerMetricNames.ADDRESS_MEMORY_USAGE_PERCENTAGE, this, metrics -> Double.valueOf(messagingServerControl.getAddressMemoryUsagePercentage()), ActiveMQServerControl.ADDRESS_MEMORY_USAGE_PERCENTAGE_DESCRIPTION);
            builder.register(BrokerMetricNames.DISK_STORE_USAGE, this, metrics -> Double.valueOf(messagingServer.getDiskStoreUsage()), ActiveMQServerControl.DISK_STORE_USAGE_DESCRIPTION);
         });
      }
   }

   @Override
   public synchronized void unregisterServer() throws Exception {
      ObjectName objectName = objectNameBuilder.getActiveMQServerObjectName();
      unregisterFromJMX(objectName);
      unregisterFromRegistry(ResourceNames.BROKER);
      if (messagingServer != null) {
         unregisterMeters(ResourceNames.BROKER + "." + messagingServer.getConfiguration().getName());
      }
   }

   @Override
   public void registerAddress(AddressInfo addressInfo) throws Exception {
      ObjectName objectName = objectNameBuilder.getAddressObjectName(addressInfo.getName());
      AddressControlImpl addressControl = new AddressControlImpl(addressInfo, messagingServer, pagingManager, storageManager, securityRepository, securityStore, this);

      registerInJMX(objectName, addressControl);

      registerInRegistry(ResourceNames.ADDRESS + addressInfo.getName(), addressControl);

      registerAddressMeters(addressInfo, addressControl);

      if (logger.isDebugEnabled()) {
         logger.debug("registered address " + objectName);
      }
   }

   @Override
   public void registerAddressMeters(AddressInfo addressInfo, AddressControl addressControl) {
      if (messagingServer != null) { // it could be null on tests, but never on a real server
         MetricsManager metricsManager = messagingServer.getMetricsManager();
         if (metricsManager != null) {
            metricsManager.registerAddressGauge(addressInfo.getName().toString(), builder -> {
               builder.register(AddressMetricNames.ROUTED_MESSAGE_COUNT, this, metrics -> Double.valueOf(addressInfo.getRoutedMessageCount()), AddressControl.ROUTED_MESSAGE_COUNT_DESCRIPTION);
               builder.register(AddressMetricNames.UNROUTED_MESSAGE_COUNT, this, metrics -> Double.valueOf(addressInfo.getUnRoutedMessageCount()), AddressControl.UNROUTED_MESSAGE_COUNT_DESCRIPTION);
               builder.register(AddressMetricNames.ADDRESS_SIZE, this, metrics -> Double.valueOf(addressControl.getAddressSize()), AddressControl.ADDRESS_SIZE_DESCRIPTION);
               builder.register(AddressMetricNames.PAGES_COUNT, this, metrics -> Double.valueOf(addressControl.getNumberOfPages()), AddressControl.NUMBER_OF_PAGES_DESCRIPTION);
            });
         }
      }
   }

   @Override
   public synchronized void unregisterAddress(final SimpleString address) throws Exception {
      ObjectName objectName = objectNameBuilder.getAddressObjectName(address);

      unregisterFromJMX(objectName);
      unregisterFromRegistry(ResourceNames.ADDRESS + address);
      unregisterMeters(ResourceNames.ADDRESS + address);
   }

   public synchronized void registerQueue(final Queue queue,
                                          final AddressInfo addressInfo,
                                          final StorageManager storageManager) throws Exception {

      if (addressInfo.isInternal()) {
         if (logger.isDebugEnabled()) {
            logger.debug("won't register internal queue: " + queue);
         }
         return;
      }

      QueueControlImpl queueControl = new QueueControlImpl(queue, addressInfo.getName().toString(), messagingServer, storageManager, securityStore, addressSettingsRepository);
      if (messageCounterManager != null) {
         MessageCounter counter = new MessageCounter(queue.getName().toString(), null, queue, false, queue.isDurable(), messageCounterManager.getMaxDayCount());
         queueControl.setMessageCounter(counter);
         messageCounterManager.registerMessageCounter(queue.getName().toString(), counter);
      }
      ObjectName objectName = objectNameBuilder.getQueueObjectName(addressInfo.getName(), queue.getName(), queue.getRoutingType());
      registerInJMX(objectName, queueControl);
      registerInRegistry(ResourceNames.QUEUE + queue.getName(), queueControl);
      registerQueueMeters(queue);

      if (logger.isDebugEnabled()) {
         logger.debug("registered queue " + objectName);
      }
   }

   @Override
   public synchronized void registerQueue(final Queue queue,
                                          final SimpleString address,
                                          final StorageManager storageManager) throws Exception {
      registerQueue(queue, new AddressInfo(address), storageManager);
   }

   @Override
   public synchronized void unregisterQueue(final SimpleString name, final SimpleString address, RoutingType routingType) throws Exception {
      ObjectName objectName = objectNameBuilder.getQueueObjectName(address, name, routingType);
      unregisterFromJMX(objectName);
      unregisterFromRegistry(ResourceNames.QUEUE + name);
      unregisterMeters(ResourceNames.QUEUE + name);
      if (messageCounterManager != null) {
         messageCounterManager.unregisterMessageCounter(name.toString());
      }
   }

   private void registerQueueMeters(final Queue queue) {
      if (messagingServer != null) { // messagingServer could be null on certain unit tests where metrics are not relevant
         MetricsManager metricsManager = messagingServer.getMetricsManager();
         if (metricsManager != null) {
            metricsManager.registerQueueGauge(queue.getAddress().toString(), queue.getName().toString(), (builder) -> {
               builder.register(QueueMetricNames.MESSAGE_COUNT, this, metrics -> Double.valueOf(queue.getMessageCount()), QueueControl.MESSAGE_COUNT_DESCRIPTION);
               builder.register(QueueMetricNames.DURABLE_MESSAGE_COUNT, this, metrics -> Double.valueOf(queue.getDurableMessageCount()), QueueControl.DURABLE_MESSAGE_COUNT_DESCRIPTION);
               builder.register(QueueMetricNames.PERSISTENT_SIZE, this, metrics -> Double.valueOf(queue.getPersistentSize()), QueueControl.PERSISTENT_SIZE_DESCRIPTION);
               builder.register(QueueMetricNames.DURABLE_PERSISTENT_SIZE, this, metrics -> Double.valueOf(queue.getDurablePersistentSize()), QueueControl.DURABLE_PERSISTENT_SIZE_DESCRIPTION);

               builder.register(QueueMetricNames.DELIVERING_MESSAGE_COUNT, this, metrics -> Double.valueOf(queue.getDeliveringCount()), QueueControl.DELIVERING_MESSAGE_COUNT_DESCRIPTION);
               builder.register(QueueMetricNames.DELIVERING_DURABLE_MESSAGE_COUNT, this, metrics -> Double.valueOf(queue.getDurableDeliveringCount()), QueueControl.DURABLE_DELIVERING_MESSAGE_COUNT_DESCRIPTION);
               builder.register(QueueMetricNames.DELIVERING_PERSISTENT_SIZE, this, metrics -> Double.valueOf(queue.getDeliveringSize()), QueueControl.DELIVERING_SIZE_DESCRIPTION);
               builder.register(QueueMetricNames.DELIVERING_DURABLE_PERSISTENT_SIZE, this, metrics -> Double.valueOf(queue.getDurableDeliveringSize()), QueueControl.DURABLE_DELIVERING_SIZE_DESCRIPTION);

               builder.register(QueueMetricNames.SCHEDULED_MESSAGE_COUNT, this, metrics -> Double.valueOf(queue.getScheduledCount()), QueueControl.SCHEDULED_MESSAGE_COUNT_DESCRIPTION);
               builder.register(QueueMetricNames.SCHEDULED_DURABLE_MESSAGE_COUNT, this, metrics -> Double.valueOf(queue.getDurableScheduledCount()), QueueControl.DURABLE_SCHEDULED_MESSAGE_COUNT_DESCRIPTION);
               builder.register(QueueMetricNames.SCHEDULED_PERSISTENT_SIZE, this, metrics -> Double.valueOf(queue.getScheduledSize()), QueueControl.SCHEDULED_SIZE_DESCRIPTION);
               builder.register(QueueMetricNames.SCHEDULED_DURABLE_PERSISTENT_SIZE, this, metrics -> Double.valueOf(queue.getDurableScheduledSize()), QueueControl.DURABLE_SCHEDULED_SIZE_DESCRIPTION);

               builder.register(QueueMetricNames.MESSAGES_ACKNOWLEDGED, this, metrics -> Double.valueOf(queue.getMessagesAcknowledged()), QueueControl.MESSAGES_ACKNOWLEDGED_DESCRIPTION);
               builder.register(QueueMetricNames.MESSAGES_ADDED, this, metrics -> Double.valueOf(queue.getMessagesAdded()), QueueControl.MESSAGES_ADDED_DESCRIPTION);
               builder.register(QueueMetricNames.MESSAGES_KILLED, this, metrics -> Double.valueOf(queue.getMessagesKilled()), QueueControl.MESSAGES_KILLED_DESCRIPTION);
               builder.register(QueueMetricNames.MESSAGES_EXPIRED, this, metrics -> Double.valueOf(queue.getMessagesExpired()), QueueControl.MESSAGES_EXPIRED_DESCRIPTION);
               builder.register(QueueMetricNames.CONSUMER_COUNT, this, metrics -> Double.valueOf(queue.getConsumerCount()), QueueControl.CONSUMER_COUNT_DESCRIPTION);
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
   public synchronized void registerDivert(final Divert divert) throws Exception {
      ObjectName objectName = objectNameBuilder.getDivertObjectName(divert.getUniqueName().toString(), divert.getAddress().toString());
      DivertControl divertControl = new DivertControlImpl(divert, storageManager, messagingServer.getInternalNamingPrefix());
      registerInJMX(objectName, divertControl);
      registerInRegistry(ResourceNames.DIVERT + divert.getUniqueName(), divertControl);

      if (logger.isDebugEnabled()) {
         logger.debug("registered divert " + objectName);
      }
   }

   @Override
   public synchronized void unregisterDivert(final SimpleString name, final SimpleString address) throws Exception {
      ObjectName objectName = objectNameBuilder.getDivertObjectName(name.toString(), address.toString());
      unregisterFromJMX(objectName);
      unregisterFromRegistry(ResourceNames.DIVERT + name);
   }

   @Override
   public synchronized void registerAcceptor(final Acceptor acceptor,
                                             final TransportConfiguration configuration) throws Exception {
      ObjectName objectName = objectNameBuilder.getAcceptorObjectName(configuration.getName());
      AcceptorControl control = new AcceptorControlImpl(acceptor, storageManager, configuration);
      registerInJMX(objectName, control);
      registerInRegistry(ResourceNames.ACCEPTOR + configuration.getName(), control);
   }

   @Override
   public void unregisterAcceptors() {
      List<String> acceptors = new ArrayList<>();
      synchronized (this) {
         for (String resourceName : registry.keySet()) {
            if (resourceName.startsWith(ResourceNames.ACCEPTOR)) {
               acceptors.add(resourceName);
            }
         }
      }

      for (String acceptor : acceptors) {
         String name = acceptor.substring(ResourceNames.ACCEPTOR.length());
         try {
            unregisterAcceptor(name);
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.failedToUnregisterAcceptors(e);
         }
      }
   }

   public synchronized void unregisterAcceptor(final String name) throws Exception {
      ObjectName objectName = objectNameBuilder.getAcceptorObjectName(name);
      unregisterFromJMX(objectName);
      unregisterFromRegistry(ResourceNames.ACCEPTOR + name);
   }

   @Override
   public synchronized void registerBroadcastGroup(final BroadcastGroup broadcastGroup,
                                                   final BroadcastGroupConfiguration configuration) throws Exception {
      broadcastGroup.setNotificationService(this);
      ObjectName objectName = objectNameBuilder.getBroadcastGroupObjectName(configuration.getName());
      BroadcastEndpointFactory endpointFactory = configuration.getEndpointFactory();
      BaseBroadcastGroupControl control = null;
      if (endpointFactory instanceof UDPBroadcastEndpointFactory) {
         control = new BroadcastGroupControlImpl(broadcastGroup, storageManager, configuration, (UDPBroadcastEndpointFactory) endpointFactory);
      } else if (endpointFactory instanceof JGroupsFileBroadcastEndpointFactory) {
         control = new JGroupsFileBroadcastGroupControlImpl(broadcastGroup, storageManager, configuration, (JGroupsFileBroadcastEndpointFactory) endpointFactory);
      } else if (endpointFactory instanceof ChannelBroadcastEndpointFactory) {
         control = new JGroupsChannelBroadcastGroupControlImpl(broadcastGroup, storageManager, configuration, (ChannelBroadcastEndpointFactory) endpointFactory);
      } else if (endpointFactory instanceof JGroupsPropertiesBroadcastEndpointFactory) {
         control = new JGroupsPropertiesBroadcastGroupControlImpl(broadcastGroup, storageManager, configuration, (JGroupsPropertiesBroadcastEndpointFactory) endpointFactory);
      } else {
         control = new BaseBroadcastGroupControlImpl(broadcastGroup, storageManager, configuration);
      }
      //shouldnt ever be null
      if (control != null) {
         registerInJMX(objectName, control);
         registerInRegistry(ResourceNames.BROADCAST_GROUP + configuration.getName(), control);
      }
   }

   @Override
   public synchronized void unregisterBroadcastGroup(final String name) throws Exception {
      ObjectName objectName = objectNameBuilder.getBroadcastGroupObjectName(name);
      unregisterFromJMX(objectName);
      unregisterFromRegistry(ResourceNames.BROADCAST_GROUP + name);
   }

   @Override
   public synchronized void registerBridge(final Bridge bridge,
                                           final BridgeConfiguration configuration) throws Exception {
      bridge.setNotificationService(this);
      ObjectName objectName = objectNameBuilder.getBridgeObjectName(configuration.getName());
      BridgeControl control = new BridgeControlImpl(bridge, storageManager, configuration);
      registerInJMX(objectName, control);
      registerInRegistry(ResourceNames.BRIDGE + configuration.getName(), control);
   }

   @Override
   public synchronized void unregisterBridge(final String name) throws Exception {
      ObjectName objectName = objectNameBuilder.getBridgeObjectName(name);
      unregisterFromJMX(objectName);
      unregisterFromRegistry(ResourceNames.BRIDGE + name);
   }

   @Override
   public synchronized void registerCluster(final ClusterConnection cluster,
                                            final ClusterConnectionConfiguration configuration) throws Exception {
      ObjectName objectName = objectNameBuilder.getClusterConnectionObjectName(configuration.getName());
      ClusterConnectionControl control = new ClusterConnectionControlImpl(cluster, storageManager, configuration);
      registerInJMX(objectName, control);
      registerInRegistry(ResourceNames.CORE_CLUSTER_CONNECTION + configuration.getName(), control);
   }

   @Override
   public synchronized void unregisterCluster(final String name) throws Exception {
      ObjectName objectName = objectNameBuilder.getClusterConnectionObjectName(name);
      unregisterFromJMX(objectName);
      unregisterFromRegistry(ResourceNames.CORE_CLUSTER_CONNECTION + name);
   }

   @Override
   public synchronized void registerBrokerBalancer(final BrokerBalancer balancer) throws Exception {
      ObjectName objectName = objectNameBuilder.getBrokerBalancerObjectName(balancer.getName());
      BrokerBalancerControl brokerBalancerControl = new BrokerBalancerControlImpl(balancer, storageManager);
      registerInJMX(objectName, brokerBalancerControl);
      registerInRegistry(ResourceNames.BROKER_BALANCER + balancer.getName(), brokerBalancerControl);

      if (logger.isDebugEnabled()) {
         logger.debug("registered broker balancer " + objectName);
      }
   }

   @Override
   public synchronized void unregisterBrokerBalancer(final String name) throws Exception {
      ObjectName objectName = objectNameBuilder.getBrokerBalancerObjectName(name);
      unregisterFromJMX(objectName);
      unregisterFromRegistry(ResourceNames.BROKER_BALANCER + name);
   }

   @Override
   public void registerHawtioSecurity(ArtemisMBeanServerGuard mBeanServerGuard) throws Exception {
      ObjectName objectName = objectNameBuilder.getManagementContextObjectName();
      HawtioSecurityControl control = new HawtioSecurityControlImpl(mBeanServerGuard, storageManager);
      registerInJMX(objectName, control);
      registerInRegistry(ResourceNames.MANAGEMENT_SECURITY, control);
   }

   @Override
   public void unregisterHawtioSecurity() throws Exception {
      ObjectName objectName = objectNameBuilder.getManagementContextObjectName();
      unregisterFromJMX(objectName);
      unregisterFromRegistry(ResourceNames.MANAGEMENT_SECURITY);
   }

   @Override
   public ICoreMessage handleMessage(Message message) throws Exception {
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
      if (logger.isDebugEnabled()) {
         logger.debug("handling management message for " + resourceName);
      }

      String operation = message.getStringProperty(ManagementHelper.HDR_OPERATION_NAME);

      if (operation != null) {
         Object[] params = ManagementHelper.retrieveOperationParameters(message);

         if (params == null) {
            params = new Object[0];
         }

         try {
            Object result = invokeOperation(resourceName, operation, params);

            ManagementHelper.storeResult(reply, result);

            reply.putBooleanProperty(ManagementHelper.HDR_OPERATION_SUCCEEDED, true);
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.managementOperationError(e, operation, resourceName);
            reply.putBooleanProperty(ManagementHelper.HDR_OPERATION_SUCCEEDED, false);
            String exceptionMessage;
            if (e instanceof InvocationTargetException) {
               exceptionMessage = ((InvocationTargetException) e).getTargetException().getMessage();
            } else {
               exceptionMessage = e.getMessage();
            }
            ManagementHelper.storeResult(reply, exceptionMessage);
         }
      } else {
         String attribute = message.getStringProperty(ManagementHelper.HDR_ATTRIBUTE);

         if (attribute != null) {
            try {
               Object result = getAttribute(resourceName, attribute);

               ManagementHelper.storeResult(reply, result);

               reply.putBooleanProperty(ManagementHelper.HDR_OPERATION_SUCCEEDED, true);
            } catch (Exception e) {
               ActiveMQServerLogger.LOGGER.managementAttributeError(e, attribute, resourceName);
               reply.putBooleanProperty(ManagementHelper.HDR_OPERATION_SUCCEEDED, false);
               String exceptionMessage;
               if (e instanceof InvocationTargetException) {
                  exceptionMessage = ((InvocationTargetException) e).getTargetException().getMessage();
               } else {
                  exceptionMessage = e.getMessage();
               }
               ManagementHelper.storeResult(reply, exceptionMessage);
            }
         }
      }

      return reply;
   }

   @Override
   public synchronized Object getResource(final String resourceName) {
      return registry.get(resourceName);
   }

   @Override
   public synchronized Object[] getResources(final Class<?> resourceType) {
      List<Object> resources = new ArrayList<>();
      Collection<Object> clone = new ArrayList<>(registry.values());
      for (Object entry : clone) {
         if (resourceType.isAssignableFrom(entry.getClass())) {
            resources.add(entry);
         }
      }
      return resources.toArray(new Object[resources.size()]);
   }

   private final Set<ObjectName> registeredNames = new HashSet<>();

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
   }

   @Override
   public synchronized void registerInRegistry(final String resourceName, final Object managedResource) {
      unregisterFromRegistry(resourceName);

      registry.put(resourceName, managedResource);
   }

   @Override
   public synchronized void unregisterFromRegistry(final String resourceName) {
      registry.remove(resourceName);
   }

   // the JMX unregistration is synchronized to avoid race conditions if 2 clients tries to
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

   // ActiveMQComponent implementation -----------------------------

   @Override
   public void start() throws Exception {
      if (messageCounterEnabled) {
         messageCounterManager.start();
      }

      started = true;

      /**
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
   }

   @Override
   public synchronized void stop() throws Exception {
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

      messagingServer = null;

      securityRepository = null;

      addressSettingsRepository = null;

      messagingServerControl = null;

      messageCounterManager = null;

      postOffice = null;

      pagingManager = null;

      storageManager = null;

      messagingServer = null;

      registeredNames.clear();

      started = false;
   }

   @Override
   public boolean isStarted() {
      return started;
   }

   @Override
   public void sendNotification(final Notification notification) throws Exception {
      if (logger.isTraceEnabled()) {
         logger.trace("Sending Notification = " + notification +
                         ", notificationEnabled=" + notificationsEnabled +
                         " messagingServerControl=" + messagingServerControl);
      }
      // This needs to be synchronized since we need to ensure notifications are processed in strict sequence
      synchronized (this) {
         if (messagingServerControl != null && notificationsEnabled) {
            // We also need to synchronize on the post office notification lock
            // otherwise we can get notifications arriving in wrong order / missing
            // if a notification occurs at same time as sendQueueInfoToQueue is processed
            synchronized (postOffice.getNotificationLock()) {

               // First send to any local listeners
               for (NotificationListener listener : listeners) {
                  try {
                     listener.onNotification(notification);
                  } catch (Exception e) {
                     // Exception thrown from one listener should not stop execution of others
                     ActiveMQServerLogger.LOGGER.errorCallingNotifListener(e);
                  }
               }

               // start sending notification *messages* only when server has initialised
               // Note at backup initialisation we don't want to send notifications either
               // https://jira.jboss.org/jira/browse/HORNETQ-317
               if (messagingServer == null || !messagingServer.isActive()) {
                  if (logger.isDebugEnabled()) {
                     logger.debug("ignoring message " + notification + " as the server is not initialized");
                  }
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

               notificationMessage.putStringProperty(ManagementHelper.HDR_NOTIFICATION_TYPE, new SimpleString(notification.getType().toString()));

               long timestamp = System.currentTimeMillis();
               notificationMessage.putLongProperty(ManagementHelper.HDR_NOTIFICATION_TIMESTAMP, timestamp);
               notificationMessage.setTimestamp(timestamp);

               postOffice.route(notificationMessage, false);
            }
         }
      }
   }

   @Override
   public void enableNotifications(final boolean enabled) {
      notificationsEnabled = enabled;
   }

   @Override
   public Object getAttribute(final String resourceName, final String attribute) {
      try {
         Object resource = registry.get(resourceName);

         if (resource == null) {
            throw ActiveMQMessageBundle.BUNDLE.cannotFindResource(resourceName);
         }

         Method method = null;

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
         return method.invoke(resource, new Object[0]);
      } catch (Throwable t) {
         throw new IllegalStateException("Problem while retrieving attribute " + attribute, t);
      }
   }

   @Override
   public Object invokeOperation(final String resourceName,
                                  final String operation,
                                  final Object[] params) throws Exception {
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

      Object result = method.invoke(resource, params);
      return result;
   }

   /**
    * Correlate management responses using the Correlation ID Pattern, if the request supplied a correlation id,
    * or fallback to the Message ID Pattern providing the request had a message id.

    * @param request
    * @return correlation identify
    */
   private Object getCorrelationIdentity(final Message request) {
      Object correlationId = request.getCorrelationID();
      if (correlationId == null) {
         // CoreMessage#getUserId returns UUID, so to implement this part a alternative API that returned object. This part of the
         // change is a nice to have for my point of view. I suggested it for completeness.  The application could
         // always supply unique correl ids on the request and achieve the same effect.  I'd be happy to drop this part.
         Object underlying = request.getStringProperty(NATIVE_MESSAGE_ID) != null ? request.getStringProperty(NATIVE_MESSAGE_ID) : request.getUserID();
         correlationId = underlying == null ? null : String.valueOf(underlying);
      }

      return correlationId;
   }

}
