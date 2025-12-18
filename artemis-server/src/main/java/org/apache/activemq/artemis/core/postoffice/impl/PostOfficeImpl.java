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
package org.apache.activemq.artemis.core.postoffice.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.apache.activemq.artemis.api.core.ActiveMQAddressDoesNotExistException;
import org.apache.activemq.artemis.api.core.ActiveMQAddressFullException;
import org.apache.activemq.artemis.api.core.ActiveMQDuplicateIdException;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQNonExistentQueueException;
import org.apache.activemq.artemis.api.core.ActiveMQShutdownException;
import org.apache.activemq.artemis.api.core.AutoCreateResult;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.management.CoreNotificationType;
import org.apache.activemq.artemis.api.core.management.ManagementHelper;
import org.apache.activemq.artemis.api.core.management.NotificationType;
import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.core.config.DivertConfiguration;
import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.filter.impl.FilterImpl;
import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.persistence.config.AbstractPersistedAddressSetting;
import org.apache.activemq.artemis.core.persistence.config.PersistedAddressSettingJSON;
import org.apache.activemq.artemis.core.postoffice.AddressManager;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.BindingType;
import org.apache.activemq.artemis.core.postoffice.Bindings;
import org.apache.activemq.artemis.core.postoffice.BindingsFactory;
import org.apache.activemq.artemis.core.postoffice.DuplicateIDCache;
import org.apache.activemq.artemis.core.postoffice.PostOffice;
import org.apache.activemq.artemis.core.postoffice.QueueBinding;
import org.apache.activemq.artemis.core.postoffice.QueueInfo;
import org.apache.activemq.artemis.core.postoffice.RoutingStatus;
import org.apache.activemq.artemis.core.server.ActiveMQMessageBundle;
import org.apache.activemq.artemis.core.server.ActiveMQScheduledComponent;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.ComponentConfigurationRoutingType;
import org.apache.activemq.artemis.core.server.LargeServerMessage;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.QueueFactory;
import org.apache.activemq.artemis.core.server.RouteContextList;
import org.apache.activemq.artemis.core.server.RoutingContext;
import org.apache.activemq.artemis.core.server.cluster.RemoteQueueBinding;
import org.apache.activemq.artemis.core.server.group.GroupingHandler;
import org.apache.activemq.artemis.core.server.impl.AckReason;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.impl.QueueManagerImpl;
import org.apache.activemq.artemis.core.server.impl.RoutingContextImpl;
import org.apache.activemq.artemis.core.server.management.ManagementService;
import org.apache.activemq.artemis.core.server.management.Notification;
import org.apache.activemq.artemis.core.server.management.NotificationListener;
import org.apache.activemq.artemis.core.server.mirror.MirrorController;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.core.settings.HierarchicalRepositoryChangeListener;
import org.apache.activemq.artemis.core.settings.impl.NamedHierarchicalRepositoryChangeListener;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.core.transaction.TransactionOperation;
import org.apache.activemq.artemis.core.transaction.TransactionOperationAbstract;
import org.apache.activemq.artemis.core.transaction.TransactionPropertyIndexes;
import org.apache.activemq.artemis.core.transaction.impl.TransactionImpl;
import org.apache.activemq.artemis.utils.CompositeAddress;
import org.apache.activemq.artemis.utils.UUIDGenerator;
import org.apache.activemq.artemis.utils.collections.TypedProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

import static org.apache.activemq.artemis.utils.collections.IterableStream.iterableOf;

/**
 * This is the class that will make the routing to Queues and decide which consumer will get the messages It's the queue
 * component on distributing the messages * *
 */
public class PostOfficeImpl implements PostOffice, NotificationListener, BindingsFactory {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final SimpleString HDR_RESET_QUEUE_DATA = SimpleString.of("_AMQ_RESET_QUEUE_DATA");

   public static final SimpleString HDR_RESET_QUEUE_DATA_COMPLETE = SimpleString.of("_AMQ_RESET_QUEUE_DATA_COMPLETE");

   public static final SimpleString BRIDGE_CACHE_STR = SimpleString.of("BRIDGE.");

   private final AddressManager addressManager;

   private final QueueFactory queueFactory;

   private final StorageManager storageManager;

   private final PagingManager pagingManager;

   private volatile boolean started;

   private final ManagementService managementService;

   private ExpiryReaper expiryReaperRunnable;

   private final long expiryReaperPeriod;

   private AddressQueueReaper addressQueueReaperRunnable;

   private final long addressQueueReaperPeriod;

   private final ConcurrentMap<SimpleString, DuplicateIDCache> duplicateIDCaches = new ConcurrentHashMap<>();

   private final int idCacheSize;

   private final boolean persistIDCache;

   private final Map<SimpleString, QueueInfo> queueInfos = new HashMap<>();

   private final Object notificationLock = new Object();

   private final HierarchicalRepository<AddressSettings> addressSettingsRepository;

   private final ActiveMQServer server;

   private MirrorController mirrorControllerSource;

   public PostOfficeImpl(final ActiveMQServer server,
                         final StorageManager storageManager,
                         final PagingManager pagingManager,
                         final QueueFactory bindableFactory,
                         final ManagementService managementService,
                         final long expiryReaperPeriod,
                         final long addressQueueReaperPeriod,
                         final WildcardConfiguration wildcardConfiguration,
                         final int idCacheSize,
                         final boolean persistIDCache,
                         final HierarchicalRepository<AddressSettings> addressSettingsRepository) {
      this.storageManager = storageManager;

      queueFactory = bindableFactory;

      this.managementService = managementService;

      this.pagingManager = pagingManager;

      this.expiryReaperPeriod = expiryReaperPeriod;

      this.addressQueueReaperPeriod = addressQueueReaperPeriod;

      if (wildcardConfiguration.isRoutingEnabled()) {
         addressManager = new WildcardAddressManager(this, wildcardConfiguration, storageManager, server.getMetricsManager());
      } else {
         addressManager = new SimpleAddressManager(this, wildcardConfiguration, storageManager, server.getMetricsManager());
      }

      this.idCacheSize = idCacheSize;

      this.persistIDCache = persistIDCache;

      this.addressSettingsRepository = addressSettingsRepository;

      this.server = server;
   }

   // ActiveMQComponent implementation ---------------------------------------

   @Override
   public synchronized void start() throws Exception {
      if (started)
         return;

      managementService.addNotificationListener(this);

      // Injecting the postoffice (itself) on queueFactory for paging-control
      queueFactory.setPostOffice(this);

      // The flag started needs to be set before starting the Reaper Thread
      // This is to avoid thread leakages where the Reaper would run beyond the life cycle of the
      // PostOffice
      started = true;
   }

   @Override
   public synchronized void stop() throws Exception {
      started = false;

      managementService.removeNotificationListener(this);

      if (expiryReaperRunnable != null)
         expiryReaperRunnable.stop();

      if (addressQueueReaperRunnable != null)
         addressQueueReaperRunnable.stop();

      addressManager.clear();

      queueInfos.clear();
   }

   @Override
   public boolean isStarted() {
      return started;
   }

   @Override
   public MirrorController getMirrorControlSource() {
      return mirrorControllerSource;
   }

   @Override
   public PostOfficeImpl setMirrorControlSource(MirrorController mirrorControllerSource) {
      this.mirrorControllerSource = mirrorControllerSource;
      return this;
   }

   @Override
   public void preAcknowledge(final Transaction tx, final MessageReference ref, AckReason reason) {
      if (mirrorControllerSource != null && reason != AckReason.REPLACED) { // we don't send replacements on LVQ as they are replaced themselves on the target
         try {
            mirrorControllerSource.preAcknowledge(tx, ref, reason);
         } catch (Exception e) {
            logger.warn(e.getMessage(), e);
         }
      }
   }


   @Override
   public void postAcknowledge(MessageReference ref, AckReason reason) {
      if (mirrorControllerSource != null && reason != AckReason.REPLACED) { // we don't send replacements on LVQ as they are replaced themselves on the target
         try {
            mirrorControllerSource.postAcknowledge(ref, reason);
         } catch (Exception e) {
            logger.warn(e.getMessage(), e);
         }
      }
   }

   @Override
   public void scanAddresses(MirrorController mirrorController) throws Exception {
      addressManager.scanAddresses(mirrorController);

   }

   // NotificationListener implementation -------------------------------------

   @Override
   public void onNotification(final Notification notification) {
      if (!(notification.getType() instanceof CoreNotificationType))
         return;

      logger.trace("Receiving notification : {} on server {}", notification, server);

      synchronized (notificationLock) {
         CoreNotificationType type = (CoreNotificationType) notification.getType();

         switch (type) {
            case BINDING_ADDED: {
               TypedProperties props = notification.getProperties();

               if (!props.containsProperty(ManagementHelper.HDR_BINDING_TYPE)) {
                  throw ActiveMQMessageBundle.BUNDLE.bindingTypeNotSpecified();
               }

               Integer bindingType = props.getIntProperty(ManagementHelper.HDR_BINDING_TYPE);

               if (bindingType == BindingType.DIVERT_INDEX) {
                  // We don't propagate diverts
                  return;
               }

               SimpleString routingName = props.getSimpleStringProperty(ManagementHelper.HDR_ROUTING_NAME);

               SimpleString clusterName = props.getSimpleStringProperty(ManagementHelper.HDR_CLUSTER_NAME);

               SimpleString address = props.getSimpleStringProperty(ManagementHelper.HDR_ADDRESS);

               if (!props.containsProperty(ManagementHelper.HDR_BINDING_ID)) {
                  throw ActiveMQMessageBundle.BUNDLE.bindingIdNotSpecified();
               }

               long id = props.getLongProperty(ManagementHelper.HDR_BINDING_ID);

               SimpleString filterString = props.getSimpleStringProperty(ManagementHelper.HDR_FILTERSTRING);

               if (!props.containsProperty(ManagementHelper.HDR_DISTANCE)) {
                  logger.debug("PostOffice notification / BINDING_ADDED: HDR_DISANCE not specified, giving up propagation on notifications");
                  return;
               }

               int distance = props.getIntProperty(ManagementHelper.HDR_DISTANCE);

               QueueInfo info = new QueueInfo(routingName, clusterName, address, filterString, id, distance);

               queueInfos.put(clusterName, info);

               if (distance < 1) {
                  //Binding added locally. If a matching remote binding with consumers exist, add a redistributor
                  Binding binding = addressManager.getBinding(routingName);

                  if (binding != null) {

                     Queue queue = (Queue) binding.getBindable();
                     AddressSettings addressSettings = addressSettingsRepository.getMatch(binding.getAddress().toString());
                     long redistributionDelay = addressSettings.getRedistributionDelay();

                     if (redistributionDelay == -1) {
                        //No need to keep looking since redistribution is not enabled
                        break;
                     }

                     try {
                        Bindings bindings = getBindingsForAddress(address);

                        for (Binding bind : bindings.getBindings()) {
                           if (bind.isConnected() && bind instanceof RemoteQueueBinding remoteBinding) {

                              if (remoteBinding.consumerCount() > 0) {
                                 queue.addRedistributor(redistributionDelay);
                                 break;
                              }
                           }
                        }
                     } catch (Exception ignore) {
                     }
                  }
               }

               break;
            }
            case BINDING_REMOVED: {
               TypedProperties props = notification.getProperties();

               if (!props.containsProperty(ManagementHelper.HDR_CLUSTER_NAME)) {
                  logger.debug("PostOffice notification / BINDING_REMOVED: HDR_CLUSTER_NAME not specified, giving up propagation on notifications");
                  return;
               }

               SimpleString clusterName = props.getSimpleStringProperty(ManagementHelper.HDR_CLUSTER_NAME);

               QueueInfo info = queueInfos.remove(clusterName);

               if (info == null) {
                  logger.debug("PostOffice notification / BINDING_REMOVED: Cannot find queue info for clusterName {}", clusterName);
                  return;
               }

               break;
            }
            case CONSUMER_CREATED: {
               TypedProperties props = notification.getProperties();

               if (!props.containsProperty(ManagementHelper.HDR_CLUSTER_NAME)) {
                  logger.debug("PostOffice notification / CONSUMER_CREATED: No clusterName defined");
                  return;
               }

               SimpleString clusterName = props.getSimpleStringProperty(ManagementHelper.HDR_CLUSTER_NAME);

               SimpleString filterString = props.getSimpleStringProperty(ManagementHelper.HDR_FILTERSTRING);

               QueueInfo info = queueInfos.get(clusterName);

               if (info == null) {
                  logger.debug("PostOffice notification / CONSUMER_CREATED: Could not find queue created on clusterName = {}", clusterName);
                  return;
               }

               info.incrementConsumers();

               if (filterString != null) {
                  List<SimpleString> filterStrings = info.getFilterStrings();

                  if (filterStrings == null) {
                     filterStrings = new ArrayList<>();

                     info.setFilterStrings(filterStrings);
                  }

                  filterStrings.add(filterString);
               }

               if (!props.containsProperty(ManagementHelper.HDR_DISTANCE)) {
                  logger.debug("PostOffice notification / CONSUMER_CREATED: No distance specified");
                  return;
               }

               int distance = props.getIntProperty(ManagementHelper.HDR_DISTANCE);

               if (distance > 0) {
                  SimpleString queueName = props.getSimpleStringProperty(ManagementHelper.HDR_ROUTING_NAME);

                  if (queueName == null) {
                     logger.debug("PostOffice notification / CONSUMER_CREATED: No queue defined");
                     return;
                  }

                  SimpleString addressName = props.getSimpleStringProperty(ManagementHelper.HDR_ADDRESS);
                  Binding binding = addressManager.getBinding(CompositeAddress.isFullyQualified(addressName) ? addressName : queueName);

                  if (binding != null) {
                     // We have a local queue
                     Queue queue = (Queue) binding.getBindable();

                     AddressSettings addressSettings = addressSettingsRepository.getMatch(binding.getAddress().toString());

                     long redistributionDelay = addressSettings.getRedistributionDelay();

                     if (redistributionDelay != -1) {
                        queue.addRedistributor(redistributionDelay);
                     }
                  }
               }

               break;
            }
            case CONSUMER_CLOSED: {
               TypedProperties props = notification.getProperties();

               SimpleString clusterName = props.getSimpleStringProperty(ManagementHelper.HDR_CLUSTER_NAME);

               if (clusterName == null) {
                  logger.debug("PostOffice notification / CONSUMER_CLOSED: No cluster name");
                  return;
               }

               SimpleString filterString = props.getSimpleStringProperty(ManagementHelper.HDR_FILTERSTRING);

               QueueInfo info = queueInfos.get(clusterName);

               if (info == null) {
                  return;
               }

               info.decrementConsumers();

               if (filterString != null) {
                  List<SimpleString> filterStrings = info.getFilterStrings();

                  filterStrings.remove(filterString);
               }

               // The consumer count should never be < 0 but we should catch here just in case.
               if (info.getNumberOfConsumers() <= 0) {
                  if (!props.containsProperty(ManagementHelper.HDR_DISTANCE)) {
                     logger.debug("PostOffice notification / CONSUMER_CLOSED: HDR_DISTANCE not defined");
                     return;
                  }

                  int distance = props.getIntProperty(ManagementHelper.HDR_DISTANCE);

                  if (distance == 0) {
                     SimpleString queueName = props.getSimpleStringProperty(ManagementHelper.HDR_ROUTING_NAME);

                     if (queueName == null) {
                        logger.debug("PostOffice notification / CONSUMER_CLOSED: No queue name");
                        return;
                     }

                     Binding binding = addressManager.getBinding(queueName);

                     if (binding == null) {
                        logger.debug("PostOffice notification / CONSUMER_CLOSED: Could not find queue {}", queueName);
                        return;
                     }

                     Queue queue = (Queue) binding.getBindable();

                     AddressSettings addressSettings = addressSettingsRepository.getMatch(binding.getAddress().toString());

                     long redistributionDelay = addressSettings.getRedistributionDelay();

                     if (redistributionDelay != -1) {
                        queue.addRedistributor(redistributionDelay);
                     }
                  }
               }

               break;
            }
            default: {
               break;
            }
         }
      }
   }

   // PostOffice implementation -----------------------------------------------

   @Override
   public void reloadAddressInfo(AddressInfo addressInfo) throws Exception {
      internalAddressInfo(addressInfo, true);
   }

   @Override
   public boolean addAddressInfo(AddressInfo addressInfo) throws Exception {
      return internalAddressInfo(addressInfo, false);
   }

   private boolean internalAddressInfo(AddressInfo addressInfo, boolean reload) throws Exception {
      synchronized (this) {
         if (server.hasBrokerAddressPlugins()) {
            server.callBrokerAddressPlugins(plugin -> plugin.beforeAddAddress(addressInfo, reload));
         }

         boolean result;
         if (reload) {
            result = addressManager.reloadAddressInfo(addressInfo);
         } else {
            result = addressManager.addAddressInfo(addressInfo);
         }
         // only register address if it is new
         if (result) {
            if (!reload && mirrorControllerSource != null) {
               mirrorControllerSource.addAddress(addressInfo);
            }

            try {
               managementService.registerAddress(addressInfo);

               if (server.hasBrokerAddressPlugins()) {
                  server.callBrokerAddressPlugins(plugin -> plugin.afterAddAddress(addressInfo, reload));
               }
               long retroactiveMessageCount = addressSettingsRepository.getMatch(addressInfo.getName().toString()).getRetroactiveMessageCount();
               if (retroactiveMessageCount > 0 && !addressInfo.isInternal() && !ResourceNames.isRetroactiveResource(server.getInternalNamingPrefix(), addressInfo.getName())) {
                  createRetroactiveResources(addressInfo.getName(), retroactiveMessageCount, reload);
               }
               if (ResourceNames.isRetroactiveResource(server.getInternalNamingPrefix(), addressInfo.getName())) {
                  registerRepositoryListenerForRetroactiveAddress(addressInfo.getName());
               }
            } catch (Exception e) {
               e.printStackTrace();
            }
         }
         return result;
      }
   }

   private void registerRepositoryListenerForRetroactiveAddress(SimpleString name) {
      HierarchicalRepositoryChangeListener repositoryChangeListener = new NamedHierarchicalRepositoryChangeListener(name) {
         @Override
         public void onChange() {
            String prefix = server.getInternalNamingPrefix();
            String delimiter = server.getConfiguration().getWildcardConfiguration().getDelimiterString();
            String address = ResourceNames.decomposeRetroactiveResourceAddressName(prefix, delimiter, name.toString());
            AddressSettings settings = addressSettingsRepository.getMatch(address);
            Queue internalAnycastQueue = server.locateQueue(ResourceNames.getRetroactiveResourceQueueName(prefix, delimiter, SimpleString.of(address), RoutingType.ANYCAST));
            if (internalAnycastQueue != null && internalAnycastQueue.getRingSize() != settings.getRetroactiveMessageCount()) {
               internalAnycastQueue.setRingSize(settings.getRetroactiveMessageCount());
            }
            Queue internalMulticastQueue = server.locateQueue(ResourceNames.getRetroactiveResourceQueueName(prefix, delimiter, SimpleString.of(address), RoutingType.MULTICAST));
            if (internalMulticastQueue != null && internalMulticastQueue.getRingSize() != settings.getRetroactiveMessageCount()) {
               internalMulticastQueue.setRingSize(settings.getRetroactiveMessageCount());
            }
         }
      };
      addressSettingsRepository.registerListener(repositoryChangeListener);
      server.getAddressInfo(name).setRepositoryChangeListener(repositoryChangeListener);
   }

   private void createRetroactiveResources(final SimpleString retroactiveAddressName, final long retroactiveMessageCount, final boolean reload) throws Exception {
      String prefix = server.getInternalNamingPrefix();
      String delimiter = server.getConfiguration().getWildcardConfiguration().getDelimiterString();
      final SimpleString internalAddressName = ResourceNames.getRetroactiveResourceAddressName(prefix, delimiter, retroactiveAddressName);
      final SimpleString internalAnycastQueueName = ResourceNames.getRetroactiveResourceQueueName(prefix, delimiter, retroactiveAddressName, RoutingType.ANYCAST);
      final SimpleString internalMulticastQueueName = ResourceNames.getRetroactiveResourceQueueName(prefix, delimiter, retroactiveAddressName, RoutingType.MULTICAST);
      final SimpleString internalDivertName = ResourceNames.getRetroactiveResourceDivertName(prefix, delimiter, retroactiveAddressName);

      if (!reload) {
         AddressInfo addressInfo = new AddressInfo(internalAddressName)
            .addRoutingType(RoutingType.MULTICAST)
            .addRoutingType(RoutingType.ANYCAST)
            .setInternal(false);
         addAddressInfo(addressInfo);

         server.createQueue(QueueConfiguration.of(internalMulticastQueueName)
                               .setAddress(internalAddressName)
                               .setRoutingType(RoutingType.MULTICAST)
                               .setMaxConsumers(0)
                               .setRingSize(retroactiveMessageCount));

         server.createQueue(QueueConfiguration.of(internalAnycastQueueName)
                               .setAddress(internalAddressName)
                               .setRoutingType(RoutingType.ANYCAST)
                               .setMaxConsumers(0)
                               .setRingSize(retroactiveMessageCount));

      }
      server.deployDivert(new DivertConfiguration()
                             .setName(internalDivertName.toString())
                             .setAddress(retroactiveAddressName.toString())
                             .setExclusive(false)
                             .setForwardingAddress(internalAddressName.toString())
                             .setRoutingType(ComponentConfigurationRoutingType.PASS));
   }

   private void removeRetroactiveResources(SimpleString address) throws Exception {
      String prefix = server.getInternalNamingPrefix();
      String delimiter = server.getConfiguration().getWildcardConfiguration().getDelimiterString();

      SimpleString internalDivertName = ResourceNames.getRetroactiveResourceDivertName(prefix, delimiter, address);
      if (addressManager.getBinding(internalDivertName) != null) {
         server.destroyDivert(internalDivertName, true);
      }

      SimpleString internalAnycastQueueName = ResourceNames.getRetroactiveResourceQueueName(prefix, delimiter, address, RoutingType.ANYCAST);
      if (server.locateQueue(internalAnycastQueueName) != null) {
         server.destroyQueue(internalAnycastQueueName);
      }

      SimpleString internalMulticastQueueName = ResourceNames.getRetroactiveResourceQueueName(prefix, delimiter, address, RoutingType.MULTICAST);
      if (server.locateQueue(internalMulticastQueueName) != null) {
         server.destroyQueue(internalMulticastQueueName);
      }

      SimpleString internalAddressName = ResourceNames.getRetroactiveResourceAddressName(prefix, delimiter, address);
      if (server.getAddressInfo(internalAddressName) != null) {
         server.removeAddressInfo(internalAddressName, null);
      }
   }

   @Override
   @Deprecated
   public QueueBinding updateQueue(SimpleString name,
                                   RoutingType routingType,
                                   Filter filter,
                                   Integer maxConsumers,
                                   Boolean purgeOnNoConsumers,
                                   Boolean exclusive,
                                   Boolean groupRebalance,
                                   Integer groupBuckets,
                                   SimpleString groupFirstKey,
                                   Boolean nonDestructive,
                                   Integer consumersBeforeDispatch,
                                   Long delayBeforeDispatch,
                                   SimpleString user,
                                   Boolean configurationManaged) throws Exception {
      return updateQueue(name, routingType, filter, maxConsumers, purgeOnNoConsumers, exclusive, groupRebalance, groupBuckets, groupFirstKey, nonDestructive, consumersBeforeDispatch, delayBeforeDispatch, user, configurationManaged, null);
   }

   @Override
   @Deprecated
   public QueueBinding updateQueue(SimpleString name,
                                   RoutingType routingType,
                                   Filter filter,
                                   Integer maxConsumers,
                                   Boolean purgeOnNoConsumers,
                                   Boolean exclusive,
                                   Boolean groupRebalance,
                                   Integer groupBuckets,
                                   SimpleString groupFirstKey,
                                   Boolean nonDestructive,
                                   Integer consumersBeforeDispatch,
                                   Long delayBeforeDispatch,
                                   SimpleString user,
                                   Boolean configurationManaged,
                                   Long ringSize) throws Exception {
      return updateQueue(QueueConfiguration.of(name)
                            .setRoutingType(routingType)
                            .setFilterString(filter.getFilterString())
                            .setMaxConsumers(maxConsumers)
                            .setPurgeOnNoConsumers(purgeOnNoConsumers)
                            .setExclusive(exclusive)
                            .setGroupRebalance(groupRebalance)
                            .setGroupBuckets(groupBuckets)
                            .setGroupFirstKey(groupFirstKey)
                            .setNonDestructive(nonDestructive)
                            .setConsumersBeforeDispatch(consumersBeforeDispatch)
                            .setDelayBeforeDispatch(delayBeforeDispatch)
                            .setUser(user)
                            .setConfigurationManaged(configurationManaged)
                            .setRingSize(ringSize));
   }

   @Override
   public QueueBinding updateQueue(QueueConfiguration queueConfiguration) throws Exception {
      return updateQueue(queueConfiguration, false);
   }

   @Override
   public QueueBinding updateQueue(QueueConfiguration queueConfiguration, boolean forceUpdate) throws Exception {
      synchronized (this) {
         final QueueBinding queueBinding = (QueueBinding) addressManager.getBinding(queueConfiguration.getName());
         if (queueBinding == null) {
            return null;
         }

         Bindings bindingsOnQueue = addressManager.getExistingBindingsForRoutingAddress(queueBinding.getAddress());

         try {

            final Queue queue = queueBinding.getQueue();

            boolean changed = false;

            //validate update
            if (queueConfiguration.getMaxConsumers() != null && queueConfiguration.getMaxConsumers() != Queue.MAX_CONSUMERS_UNLIMITED) {
               final int consumerCount = queue.getConsumerCount();
               if (consumerCount > queueConfiguration.getMaxConsumers()) {
                  throw ActiveMQMessageBundle.BUNDLE.invalidMaxConsumersUpdate(queueConfiguration.getName().toString(), queueConfiguration.getMaxConsumers(), consumerCount);
               }
            }
            if (queueConfiguration.getRoutingType() != null) {
               final SimpleString address = queue.getAddress();
               final AddressInfo addressInfo = addressManager.getAddressInfo(address);
               final EnumSet<RoutingType> addressRoutingTypes = addressInfo.getRoutingTypes();
               if (!addressRoutingTypes.contains(queueConfiguration.getRoutingType())) {
                  throw ActiveMQMessageBundle.BUNDLE.invalidRoutingTypeUpdate(queueConfiguration.getName().toString(), queueConfiguration.getRoutingType(), address.toString(), addressRoutingTypes);
               }
            }

            //atomic update
            if ((forceUpdate || queueConfiguration.getMaxConsumers() != null) && !Objects.equals(queue.getMaxConsumers(), queueConfiguration.getMaxConsumers())) {
               changed = true;
               queue.setMaxConsumer(queueConfiguration.getMaxConsumers());
            }
            if ((forceUpdate || queueConfiguration.getRoutingType() != null) && !Objects.equals(queue.getRoutingType(), queueConfiguration.getRoutingType())) {
               changed = true;
               queue.setRoutingType(queueConfiguration.getRoutingType());
            }
            if ((forceUpdate || queueConfiguration.isPurgeOnNoConsumers() != null) && !Objects.equals(queue.isPurgeOnNoConsumers(), queueConfiguration.isPurgeOnNoConsumers())) {
               changed = true;
               queue.setPurgeOnNoConsumers(queueConfiguration.isPurgeOnNoConsumers());
            }
            if ((forceUpdate || queueConfiguration.isEnabled() != null) && !Objects.equals(queue.isEnabled(), queueConfiguration.isEnabled())) {
               changed = true;
               queue.setEnabled(queueConfiguration.isEnabled());
            }
            if ((forceUpdate || queueConfiguration.isExclusive() != null) && !Objects.equals(queue.isExclusive(), queueConfiguration.isExclusive())) {
               changed = true;
               queue.setExclusive(queueConfiguration.isExclusive());
            }
            if ((forceUpdate || queueConfiguration.isGroupRebalance() != null) && !Objects.equals(queue.isGroupRebalance(), queueConfiguration.isGroupRebalance())) {
               changed = true;
               queue.setGroupRebalance(queueConfiguration.isGroupRebalance());
            }
            if ((forceUpdate || queueConfiguration.isGroupRebalancePauseDispatch() != null) && !Objects.equals(queue.isGroupRebalancePauseDispatch(), queueConfiguration.isGroupRebalancePauseDispatch())) {
               changed = true;
               queue.setGroupRebalancePauseDispatch(queueConfiguration.isGroupRebalancePauseDispatch());
            }
            if ((forceUpdate || queueConfiguration.getGroupBuckets() != null) && !Objects.equals(queue.getGroupBuckets(), queueConfiguration.getGroupBuckets())) {
               changed = true;
               queue.setGroupBuckets(queueConfiguration.getGroupBuckets());
            }
            if ((forceUpdate || queueConfiguration.getGroupFirstKey() != null) && !Objects.equals(queueConfiguration.getGroupFirstKey(), queue.getGroupFirstKey())) {
               changed = true;
               queue.setGroupFirstKey(queueConfiguration.getGroupFirstKey());
            }
            if ((forceUpdate || queueConfiguration.isNonDestructive() != null) && !Objects.equals(queue.isNonDestructive(), queueConfiguration.isNonDestructive())) {
               changed = true;
               queue.setNonDestructive(queueConfiguration.isNonDestructive());
            }
            if ((forceUpdate || queueConfiguration.getConsumersBeforeDispatch() != null) && !Objects.equals(queueConfiguration.getConsumersBeforeDispatch(), queue.getConsumersBeforeDispatch())) {
               changed = true;
               queue.setConsumersBeforeDispatch(queueConfiguration.getConsumersBeforeDispatch());
            }
            if ((forceUpdate || queueConfiguration.getDelayBeforeDispatch() != null) && !Objects.equals(queueConfiguration.getDelayBeforeDispatch(), queue.getDelayBeforeDispatch())) {
               changed = true;
               queue.setDelayBeforeDispatch(queueConfiguration.getDelayBeforeDispatch());
            }
            final SimpleString empty = SimpleString.of("");
            Filter oldFilter = FilterImpl.createFilter(queue.getFilter() == null ? empty : queue.getFilter().getFilterString());
            Filter newFilter = FilterImpl.createFilter(Objects.requireNonNullElse(queueConfiguration.getFilterString(), empty));
            if ((forceUpdate || newFilter != oldFilter) && !Objects.equals(oldFilter, newFilter)) {
               changed = true;
               queue.setFilter(newFilter);
               notifyBindingUpdatedForQueue(queueBinding);
            }
            if ((forceUpdate || queueConfiguration.isConfigurationManaged() != null) && !Objects.equals(queueConfiguration.isConfigurationManaged(), queue.isConfigurationManaged())) {
               changed = true;
               queue.setConfigurationManaged(queueConfiguration.isConfigurationManaged());
            }
            if ((forceUpdate || queueConfiguration.getUser() != null) && !Objects.equals(queueConfiguration.getUser(), queue.getUser())) {
               changed = true;
               queue.setUser(queueConfiguration.getUser());
            }
            if ((forceUpdate || queueConfiguration.getRingSize() != null) && !Objects.equals(queueConfiguration.getRingSize(), queue.getRingSize())) {
               changed = true;
               queue.setRingSize(queueConfiguration.getRingSize());
            }

            if (changed) {
               final long txID = storageManager.generateID();
               try {
                  storageManager.updateQueueBinding(txID, queueBinding);
                  storageManager.commitBindings(txID);
               } catch (Throwable throwable) {
                  storageManager.rollback(txID);
                  logger.warn(throwable.getMessage(), throwable);
                  throw throwable;
               }
            }
         } finally {
            if (bindingsOnQueue != null) {
               bindingsOnQueue.updated(queueBinding);
            }
         }

         return queueBinding;
      }
   }

   public void notifyBindingUpdatedForQueue(QueueBinding binding) throws Exception {
      //only the filter could be updated
      TypedProperties props = new TypedProperties();
      props.putSimpleStringProperty(ManagementHelper.HDR_CLUSTER_NAME, binding.getClusterName());
      Filter filter = binding.getFilter();
      if (filter != null) {
         props.putSimpleStringProperty(ManagementHelper.HDR_FILTERSTRING, filter.getFilterString());
      }
      props.putIntProperty(ManagementHelper.HDR_DISTANCE, binding.getDistance());
      props.putSimpleStringProperty(ManagementHelper.HDR_ADDRESS, binding.getAddress());

      String uid = UUIDGenerator.getInstance().generateStringUUID();
      logger.debug("ClusterCommunication::Sending notification for updateBinding {} from server {}", binding, server);
      managementService.sendNotification(new Notification(uid, CoreNotificationType.BINDING_UPDATED, props));
   }

   @Override
   public AddressInfo updateAddressInfo(SimpleString addressName,
                                        EnumSet<RoutingType> routingTypes) throws Exception {
      synchronized (this) {
         if (server.hasBrokerAddressPlugins()) {
            server.callBrokerAddressPlugins(plugin -> plugin.beforeUpdateAddress(addressName, routingTypes));
         }

         final AddressInfo address = addressManager.updateAddressInfo(addressName, routingTypes);
         if (server.hasBrokerAddressPlugins()) {
            server.callBrokerAddressPlugins(plugin -> plugin.afterUpdateAddress(address));
         }

         return address;
      }
   }


   @Override
   public AddressInfo removeAddressInfo(SimpleString address) throws Exception {
      return removeAddressInfo(address, false);
   }

   @Override
   public AddressInfo removeAddressInfo(SimpleString address, boolean force) throws Exception {
      synchronized (this) {
         if (server.hasBrokerAddressPlugins()) {
            server.callBrokerAddressPlugins(plugin -> plugin.beforeRemoveAddress(address));
         }

         final Collection<Binding> bindingsForAddress = getDirectBindings(address);
         if (force) {
            for (Binding binding : bindingsForAddress) {
               if (binding instanceof LocalQueueBinding localQueueBinding) {
                  localQueueBinding.getQueue().deleteQueue(true);
               } else if (binding instanceof RemoteQueueBinding) {
                  removeBinding(binding.getUniqueName(), null, true);
               }
            }

         } else if (!bindingsForAddress.isEmpty()) {
            throw ActiveMQMessageBundle.BUNDLE.addressHasBindings(address);
         }
         managementService.unregisterAddress(address);
         final AddressInfo addressInfo = addressManager.removeAddressInfo(address);

         if (mirrorControllerSource != null && addressInfo != null) {
            mirrorControllerSource.deleteAddress(addressInfo);
         }

         removeRetroactiveResources(address);

         deleteDuplicateCache(address);

         if (server.hasBrokerAddressPlugins()) {
            server.callBrokerAddressPlugins(plugin -> plugin.afterRemoveAddress(address, addressInfo));
         }

         return addressInfo;
      }
   }

   @Override
   public AddressInfo getAddressInfo(SimpleString addressName) {
      return addressManager.getAddressInfo(addressName);
   }

   @Override
   public List<Queue> listQueuesForAddress(SimpleString address) throws Exception {
      Bindings bindingsForAddress = lookupBindingsForAddress(address);
      List<Queue> queues = new ArrayList<>();
      if (bindingsForAddress != null) {
         for (Binding b : bindingsForAddress.getBindings()) {
            if (b instanceof QueueBinding queueBinding) {
               Queue q = queueBinding.getQueue();
               queues.add(q);
            }
         }
      }
      return queues;
   }

   // TODO - needs to be synchronized to prevent happening concurrently with activate()
   // (and possible removeBinding and other methods)
   // Otherwise can have situation where createQueue comes in before failover, then failover occurs
   // and post office is activated but queue remains unactivated after failover so delivery never occurs
   // even though failover is complete
   @Override
   public synchronized void addBinding(final Binding binding) throws Exception {
      if (server.hasBrokerBindingPlugins()) {
         server.callBrokerBindingPlugins(plugin -> plugin.beforeAddBinding(binding));
      }

      addressManager.addBinding(binding);

      TypedProperties props = new TypedProperties();

      props.putIntProperty(ManagementHelper.HDR_BINDING_TYPE, binding.getType().toInt());

      props.putSimpleStringProperty(ManagementHelper.HDR_ADDRESS, binding.getAddress());

      props.putSimpleStringProperty(ManagementHelper.HDR_CLUSTER_NAME, binding.getClusterName());

      props.putSimpleStringProperty(ManagementHelper.HDR_ROUTING_NAME, binding.getRoutingName());

      props.putLongProperty(ManagementHelper.HDR_BINDING_ID, binding.getID());

      props.putIntProperty(ManagementHelper.HDR_DISTANCE, binding.getDistance());

      Filter filter = binding.getFilter();

      if (filter != null) {
         props.putSimpleStringProperty(ManagementHelper.HDR_FILTERSTRING, filter.getFilterString());
      }

      String uid = UUIDGenerator.getInstance().generateStringUUID();

      logger.debug("ClusterCommunication::Sending notification for addBinding {} from server {}", binding, server);

      managementService.sendNotification(new Notification(uid, CoreNotificationType.BINDING_ADDED, props));

      if (server.hasBrokerBindingPlugins()) {
         server.callBrokerBindingPlugins(plugin -> plugin.afterAddBinding(binding));
      }

   }

   @Override
   public synchronized Binding removeBinding(final SimpleString uniqueName,
                                             Transaction tx,
                                             boolean deleteData) throws Exception {

      if (server.hasBrokerBindingPlugins()) {
         server.callBrokerBindingPlugins(plugin -> plugin.beforeRemoveBinding(uniqueName, tx, deleteData));
      }

      try {

         Binding binding = addressManager.removeBinding(uniqueName, tx);

         if (binding == null) {
            throw new ActiveMQNonExistentQueueException();
         }

         if (deleteData && addressManager.getExistingBindingsForRoutingAddress(binding.getAddress()) == null) {
            deleteDuplicateCache(binding.getAddress());
         }

         if (binding.getType() == BindingType.LOCAL_QUEUE) {
            Queue queue = (Queue) binding.getBindable();
            managementService.unregisterQueue(uniqueName, binding.getAddress(), queue.getRoutingType());
         } else if (binding.getType() == BindingType.DIVERT) {
            managementService.unregisterDivert(uniqueName, binding.getAddress());
         }

         AddressInfo addressInfo = getAddressInfo(binding.getAddress());
         if (addressInfo != null) {
            addressInfo.setBindingRemovedTimestamp(System.currentTimeMillis());
         }

         if (binding.getType() != BindingType.DIVERT) {
            TypedProperties props = new TypedProperties();

            props.putSimpleStringProperty(ManagementHelper.HDR_ADDRESS, binding.getAddress());

            props.putSimpleStringProperty(ManagementHelper.HDR_CLUSTER_NAME, binding.getClusterName());

            props.putSimpleStringProperty(ManagementHelper.HDR_ROUTING_NAME, binding.getRoutingName());

            props.putIntProperty(ManagementHelper.HDR_DISTANCE, binding.getDistance());

            props.putLongProperty(ManagementHelper.HDR_BINDING_ID, binding.getID());

            if (binding.getFilter() == null) {
               props.putSimpleStringProperty(ManagementHelper.HDR_FILTERSTRING, null);
            } else {
               props.putSimpleStringProperty(ManagementHelper.HDR_FILTERSTRING, binding.getFilter().getFilterString());
            }

            managementService.sendNotification(new Notification(null, CoreNotificationType.BINDING_REMOVED, props));
         }

         binding.close();

         if (server.hasBrokerBindingPlugins()) {
            server.callBrokerBindingPlugins(plugin -> plugin.afterRemoveBinding(binding, tx, deleteData));
         }

         return binding;
      } finally {
         server.clearAddressCache();
      }
   }

   private void deleteDuplicateCache(SimpleString address) throws Exception {
      DuplicateIDCache cache = duplicateIDCaches.remove(address);

      if (cache != null) {
         cache.clear();
      }

      cache = duplicateIDCaches.remove(BRIDGE_CACHE_STR.concat(address));

      if (cache != null) {
         cache.clear();
      }
   }

   @Override
   public boolean isAddressBound(final SimpleString address) throws Exception {
      Collection<Binding> bindings = getDirectBindings(address);
      return bindings != null && !bindings.isEmpty();
   }

   @Override
   public Bindings getBindingsForAddress(final SimpleString address) throws Exception {
      Bindings bindings = addressManager.getExistingBindingsForRoutingAddress(address);

      if (bindings == null) {
         bindings = createBindings(address);
      }

      return bindings;
   }

   @Override
   public Bindings lookupBindingsForAddress(final SimpleString address) throws Exception {
      return addressManager.getExistingBindingsForRoutingAddress(address);
   }

   @Override
   public LocalQueueBinding findLocalBinding(final long bindingID) {
      return addressManager.findLocalBinding(bindingID);
   }

   @Override
   public synchronized Binding getBinding(final SimpleString name) {
      return addressManager.getBinding(name);
   }

   @Override
   public Collection<Binding> getMatchingBindings(final SimpleString address) throws Exception {
      return addressManager.getMatchingBindings(address);
   }

   @Override
   public Collection<Binding> getDirectBindings(final SimpleString address) throws Exception {
      return addressManager.getDirectBindings(address);
   }

   @Override
   public Stream<Binding> getAllBindings() {
      return addressManager.getBindings();
   }

   @Override
   public RoutingStatus route(final Message message, final boolean direct) throws Exception {
      return route(message, (Transaction) null, direct);
   }

   @Override
   public RoutingStatus route(final Message message, final Transaction tx, final boolean direct) throws Exception {
      return route(message, new RoutingContextImpl(tx), direct);
   }

   @Override
   public RoutingStatus route(Message message,
                              Transaction tx,
                              boolean direct,
                              boolean rejectDuplicates) throws Exception {
      return route(message, new RoutingContextImpl(tx), direct, rejectDuplicates, null);
   }

   @Override
   public RoutingStatus route(final Message message,
                              final Transaction tx,
                              final boolean direct,
                              final boolean rejectDuplicates,
                              final Binding binding) throws Exception {
      return route(message, new RoutingContextImpl(tx), direct, rejectDuplicates, binding);
   }

   @Override
   public RoutingStatus route(final Message message,
                              final RoutingContext context,
                              final boolean direct) throws Exception {
      return route(message, context, direct, true, null, false);
   }

   @Override
   public RoutingStatus route(final Message message,
                              final RoutingContext context,
                              final boolean direct,
                              boolean rejectDuplicates,
                              final Binding bindingMove) throws Exception {

      return route(message, context, direct, rejectDuplicates, bindingMove, false);
   }

   /**
    * The route can call itelf sending to DLA. if a DLA still not found, it should then use previous semantics.
    */
   private RoutingStatus route(final Message message,
                               final RoutingContext context,
                               final boolean direct,
                               final boolean rejectDuplicates,
                               final Binding bindingMove,
                               final boolean sendToDLA) throws Exception {

      // Sanity check
      if (message.getRefCount() > 0) {
         throw new IllegalStateException("Message cannot be routed more than once");
      }

      final SimpleString address = context.getAddress(message);
      final AddressSettings settings = addressSettingsRepository.getMatch(address.toString());
      if (settings != null) {
         applyExpiryDelay(message, settings);
      }

      final boolean startedTX;
      if (context.isDuplicateDetection()) {
         final DuplicateCheckResult duplicateCheckResult = checkDuplicateID(message, context, rejectDuplicates);
         switch (duplicateCheckResult) {

            case DuplicateNotStartedTX:
               return RoutingStatus.DUPLICATED_ID;
            case NoDuplicateStartedTX:
               startedTX = true;
               break;
            case NoDuplicateNotStartedTX:
               startedTX = false;
               //nop
               break;
            default:
               throw new IllegalStateException("Unexpected value: " + duplicateCheckResult);
         }
      } else {
         startedTX = false;
      }
      if (context.getMirrorSource() == null) {
         message.clearAMQPProperties();
      }
      message.clearInternalProperties();
      Bindings bindings;
      final AddressInfo addressInfo = checkAddress(context, address);

      final RoutingStatus status;
      if (bindingMove != null) {
         context.clear();
         context.setReusable(false);
         bindingMove.route(message, context);
         if (addressInfo != null) {
            addressInfo.incrementRoutedMessageCount();
         }
         status = RoutingStatus.OK;
      } else {
         bindings = simpleRoute(address, context, message, addressInfo);
         if (logger.isDebugEnabled()) {
            if (bindings != null) {
               logger.debug("PostOffice::simpleRoute returned bindings with size = {}", bindings.getBindings().size());
            } else {
               logger.debug("PostOffice::simpleRoute null as bindings");
            }
         }
         if (bindings == null) {
            context.setReusable(false);
            context.clear();
            if (addressInfo != null) {
               addressInfo.incrementUnRoutedMessageCount();
            }
            // this is a debug and not warn because this could be a regular scenario on publish-subscribe queues (or topic subscriptions on JMS)
            logger.debug("Couldn't find any bindings for address={} on message={}", address, message);
            status = RoutingStatus.NO_BINDINGS;
         } else {
            status = RoutingStatus.OK;
         }
      }

      if (server.hasBrokerMessagePlugins()) {
         server.callBrokerMessagePlugins(plugin -> plugin.beforeMessageRoute(message, context, direct, rejectDuplicates));
      }

      logger.trace("Message after routed={}\n{}", message, context);

      final RoutingStatus finalStatus;
      try {
         if (status == RoutingStatus.NO_BINDINGS) {
            finalStatus = maybeSendToDLA(message, context, address, sendToDLA);
         } else {
            finalStatus = status;
            try {
               if (context.getQueueCount() > 0) {
                  processRoute(message, context, direct);
               } else {
                  if (message.isLargeMessage()) {
                     ((LargeServerMessage) message).deleteFile();
                  }
               }
            } catch (ActiveMQAddressFullException e) {
               if (startedTX) {
                  context.getTransaction().rollback();
               } else if (context.getTransaction() != null) {
                  context.getTransaction().markAsRollbackOnly(e);
               }
               throw e;
            }
         }
         if (startedTX) {
            context.getTransaction().commit();
         }
         if (server.hasBrokerMessagePlugins()) {
            server.callBrokerMessagePlugins(plugin -> plugin.afterMessageRoute(message, context, direct, rejectDuplicates, finalStatus));
         }
         return finalStatus;
      } catch (Exception e) {
         if (server.hasBrokerMessagePlugins()) {
            server.callBrokerMessagePlugins(plugin -> plugin.onMessageRouteException(message, context, direct, rejectDuplicates, e));
         }
         throw e;
      }
   }

   private AddressInfo checkAddress(RoutingContext context, SimpleString address) throws Exception {
      AddressInfo addressInfo = addressManager.getAddressInfo(address);
      if (addressInfo == null && context.getServerSession() != null) {
         AutoCreateResult autoCreateResult = context.getServerSession().checkAutoCreate(QueueConfiguration.of(address).setRoutingType(context.getRoutingType()));
         if (autoCreateResult == AutoCreateResult.NOT_FOUND) {
            ActiveMQException ex = ActiveMQMessageBundle.BUNDLE.addressDoesNotExist(address);
            if (context.getTransaction() != null) {
               context.getTransaction().markAsRollbackOnly(ex);
            }
            throw ex;
         } else {
            addressInfo = addressManager.getAddressInfo(address);
         }
      }
      return addressInfo;
   }

   Bindings simpleRoute(SimpleString address, RoutingContext context, Message message, AddressInfo addressInfo) throws Exception {
      Bindings bindings = addressManager.getBindingsForRoutingAddress(address);
      if ((bindings == null || !bindings.hasLocalBinding()) && context.getServerSession() != null) {
         AutoCreateResult autoCreateResult = context.getServerSession().checkAutoCreate(QueueConfiguration.of(address).setRoutingType(context.getRoutingType()));
         if (autoCreateResult == AutoCreateResult.NOT_FOUND) {
            ActiveMQException e = ActiveMQMessageBundle.BUNDLE.addressDoesNotExist(address);
            Transaction tx = context.getTransaction();
            if (tx != null) {
               tx.markAsRollbackOnly(e);
            }
            throw e;
         }
         bindings = addressManager.getBindingsForRoutingAddress(address);
      }
      if (bindings != null) {
         bindings.route(message, context);
         if (context.getQueueCount() > 0) {
            if (addressInfo != null) {
               addressInfo.incrementRoutedMessageCount();
            }
         } else {
            return null;
         }
      }
      return bindings;
   }


   private RoutingStatus maybeSendToDLA(final Message message,
                                        final RoutingContext context,
                                        final SimpleString address,
                                        final boolean sendToDLAHint) throws Exception {
      final RoutingStatus status;
      final AddressSettings addressSettings = addressSettingsRepository.getMatch(address.toString());
      final boolean sendToDLA;
      if (sendToDLAHint) {
         // it's already been through here once, giving up now
         sendToDLA = false;
      } else {
         sendToDLA = addressSettings != null ? addressSettings.isSendToDLAOnNoRoute() : AddressSettings.DEFAULT_SEND_TO_DLA_ON_NO_ROUTE;
      }
      if (sendToDLA) {
         // Send to the DLA for the address
         final SimpleString dlaAddress = addressSettings != null ? addressSettings.getDeadLetterAddress() : null;
         logger.debug("sending message to dla address = {}, message={}", dlaAddress, message);
         if (dlaAddress == null) {
            status = RoutingStatus.NO_BINDINGS;
            ActiveMQServerLogger.LOGGER.noDLA(address);
         } else {
            message.referenceOriginalMessage(message, null);

            message.setAddress(dlaAddress);

            message.setRoutingType(null);

            message.reencode();

            route(message, new RoutingContextImpl(context.getTransaction()), false, true, null, true);
            status = RoutingStatus.NO_BINDINGS_DLA;
         }
      } else {
         status = RoutingStatus.NO_BINDINGS;
         logger.debug("Message {} is not going anywhere as it didn't have a binding on address:{}", message, address);
         if (message.isLargeMessage()) {
            ((LargeServerMessage) message).deleteFile();
         }
      }
      return status;
   }

   protected static void applyExpiryDelay(Message message, AddressSettings settings) {
      long expirationOverride = settings.getExpiryDelay();

      if (settings.isNoExpiry()) {
         if (message.getExpiration() != 0) {
            message.setExpiration(0);
            message.reencode();
         }
      } else if (expirationOverride >= 0) {
         // A -1 <expiry-delay> means don't do anything
         if (message.getExpiration() == 0) {
            // only override the expiration on messages where the expiration hasn't been set by the user
            setExpiration(message, expirationOverride);
         }
      } else {
         long minExpiration = settings.getMinExpiryDelay();
         long maxExpiration = settings.getMaxExpiryDelay();

         if (message.getExpiration() == 0) {
            // if the incoming message has NO expiration then apply the max if set and if not set then apply the min if set
            if (maxExpiration != AddressSettings.DEFAULT_MAX_EXPIRY_DELAY) {
               setExpiration(message, maxExpiration);
            } else if (minExpiration != AddressSettings.DEFAULT_MIN_EXPIRY_DELAY) {
               setExpiration(message, minExpiration);
            }
         } else if (maxExpiration != AddressSettings.DEFAULT_MAX_EXPIRY_DELAY && message.getExpiration() > (System.currentTimeMillis() + maxExpiration)) {
            setExpiration(message, maxExpiration);
         } else if (minExpiration != AddressSettings.DEFAULT_MIN_EXPIRY_DELAY && message.getExpiration() < (System.currentTimeMillis() + minExpiration)) {
            setExpiration(message, minExpiration);
         }
      }
   }

   private static void setExpiration(Message m, long expiration) {
      m.setExpiration(System.currentTimeMillis() + expiration);
      m.reencode();
   }

   @Override
   public MessageReference reload(final Message message, final Queue queue, final Transaction tx) throws Exception {

      message.setOwner(pagingManager.getPageStore(message.getAddressSimpleString()));
      MessageReference reference = MessageReference.Factory.createReference(message, queue);

      Long scheduledDeliveryTime;
      if (message.hasScheduledDeliveryTime()) {
         scheduledDeliveryTime = message.getScheduledDeliveryTime();
         if (scheduledDeliveryTime != null) {
            reference.setScheduledDeliveryTime(scheduledDeliveryTime);
         }
      }

      queue.refUp(reference);
      queue.durableUp(message);

      if (tx == null) {
         queue.reload(reference);
      } else {
         List<MessageReference> refs = new ArrayList<>(1);

         refs.add(reference);

         tx.addOperation(new AddOperation(refs));
      }

      return reference;
   }

   /**
    * The redistribution can't process the route right away as we may be dealing with a large message which will need to
    * be processed on a different thread
    */
   @Override
   public Pair<RoutingContext, Message> redistribute(final Message message,
                                                     final Queue originatingQueue) throws Exception {
      Bindings bindings = addressManager.getBindingsForRoutingAddress(message.getAddressSimpleString());

      if (bindings != null && bindings.allowRedistribute()) {
         if (logger.isDebugEnabled()) {
            logger.debug("Redistributing message {}, originatingQueue={}, bindings={}", message, originatingQueue.getName(), bindings);
         }
         RoutingContext context = new RoutingContextImpl(null);

         // the redistributor will make a copy of the message if it can be redistributed
         Message redistributedMessage = bindings.redistribute(message, originatingQueue, context);

         if (redistributedMessage != null) {
            return new Pair<>(context, redistributedMessage);
         } else {
            logger.debug("Redistribution of message {} did not happen because bindings.redistribute returned null", message);
         }
      } else {
         logger.debug("not able to redistribute message={} towards bindings={}", message, bindings);
      }

      return null;
   }

   private int resolveIdCacheSize(SimpleString address) {
      return Objects.requireNonNullElse(addressSettingsRepository.getMatch(address.toString()).getIDCacheSize(), idCacheSize);
   }

   @Override
   public DuplicateIDCache getDuplicateIDCache(final SimpleString address) {
      int resolvedIdCacheSize = resolveIdCacheSize(address);
      return getDuplicateIDCache(address, resolvedIdCacheSize, false);
   }

   @Override
   public DuplicateIDCache getDuplicateIDCache(final SimpleString address, int cacheSizeToUse) {
      return getDuplicateIDCache(address, cacheSizeToUse, true);
   }

   private DuplicateIDCache getDuplicateIDCache(final SimpleString address, int cacheSizeToUse, boolean allowRegistration) {
      DuplicateIDCache cache = duplicateIDCaches.get(address);

      if (cache == null) {
         if (persistIDCache) {
            if (allowRegistration) {
               registerCacheSize(address, cacheSizeToUse);
            }
            cache = DuplicateIDCaches.persistent(address, cacheSizeToUse, storageManager);
         } else {
            cache = DuplicateIDCaches.inMemory(address, cacheSizeToUse);
         }

         DuplicateIDCache oldCache = duplicateIDCaches.putIfAbsent(address, cache);

         if (oldCache != null) {
            cache = oldCache;
         }
      }

      return cache;
   }

   private void registerCacheSize(SimpleString address, int cacheSizeToUse) {
      AbstractPersistedAddressSetting recordedSetting = storageManager.recoverAddressSettings(address);
      if (recordedSetting == null || recordedSetting.getSetting().getIDCacheSize() == null || recordedSetting.getSetting().getIDCacheSize().intValue() != cacheSizeToUse) {
         AddressSettings settings = recordedSetting != null ? recordedSetting.getSetting() : new AddressSettings();
         settings.setIDCacheSize(cacheSizeToUse);
         server.getAddressSettingsRepository().addMatch(address.toString(), settings);
         try {
            storageManager.storeAddressSetting(new PersistedAddressSettingJSON(address, settings, settings.toJSON()));
         } catch (Exception e) {
            // nothing could be done here, we just log
            // if an exception is happening, if IO is compromised the server will eventually be shutdown
            ActiveMQServerLogger.LOGGER.errorRegisteringDuplicateCacheSize(String.valueOf(address), e);
         }
      }
   }

   public ConcurrentMap<SimpleString, DuplicateIDCache> getDuplicateIDCaches() {
      return duplicateIDCaches;
   }

   @Override
   public Object getNotificationLock() {
      return notificationLock;
   }

   @Override
   public Set<SimpleString> getAddresses() {
      return addressManager.getAddresses();
   }

   @Override
   public SimpleString getMatchingQueue(SimpleString address, RoutingType routingType) throws Exception {
      return addressManager.getMatchingQueue(address, routingType);
   }

   @Override
   public SimpleString getMatchingQueue(SimpleString address,
                                        SimpleString queueName,
                                        RoutingType routingType) throws Exception {
      return addressManager.getMatchingQueue(address, queueName, routingType);
   }

   @Override
   public void sendQueueInfoToQueue(final SimpleString queueName, final SimpleString address) throws Exception {
      // We send direct to the queue so we can send it to the same queue that is bound to the notifications address -
      // this is crucial for ensuring
      // that queue infos and notifications are received in a contiguous consistent stream
      Binding binding = addressManager.getBinding(queueName);

      if (binding == null) {
         throw new IllegalStateException("Cannot find queue " + queueName);
      }

      if (logger.isDebugEnabled()) {
         logger.debug("PostOffice.sendQueueInfoToQueue on server={}, queueName={} and address={}", server, queueName, address);
      }

      Queue queue = (Queue) binding.getBindable();

      // Need to lock to make sure all queue info and notifications are in the correct order with no gaps
      synchronized (notificationLock) {
         // First send a reset message

         Message message = new CoreMessage(storageManager.generateID(), 50);

         message.setAddress(queueName);
         message.putBooleanProperty(PostOfficeImpl.HDR_RESET_QUEUE_DATA, true);
         routeQueueInfo(message, queue, false);

         for (QueueInfo info : queueInfos.values()) {
            logger.trace("QueueInfo on sendQueueInfoToQueue = {}", info);

            if (info.matchesAddress(address)) {
               message = createQueueInfoMessage(CoreNotificationType.BINDING_ADDED, queueName);

               message.putStringProperty(ManagementHelper.HDR_ADDRESS, info.getAddress());
               message.putStringProperty(ManagementHelper.HDR_CLUSTER_NAME, info.getClusterName());
               message.putStringProperty(ManagementHelper.HDR_ROUTING_NAME, info.getRoutingName());
               message.putLongProperty(ManagementHelper.HDR_BINDING_ID, info.getID());
               message.putStringProperty(ManagementHelper.HDR_FILTERSTRING, info.getFilterString());
               message.putIntProperty(ManagementHelper.HDR_DISTANCE, info.getDistance());

               routeQueueInfo(message, queue, true);

               int consumersWithFilters = info.getFilterStrings() != null ? info.getFilterStrings().size() : 0;

               for (int i = 0; i < info.getNumberOfConsumers() - consumersWithFilters; i++) {
                  message = createQueueInfoMessage(CoreNotificationType.CONSUMER_CREATED, queueName);

                  message.putStringProperty(ManagementHelper.HDR_ADDRESS, info.getAddress());
                  message.putStringProperty(ManagementHelper.HDR_CLUSTER_NAME, info.getClusterName());
                  message.putStringProperty(ManagementHelper.HDR_ROUTING_NAME, info.getRoutingName());
                  message.putIntProperty(ManagementHelper.HDR_DISTANCE, info.getDistance());

                  routeQueueInfo(message, queue, true);
               }

               if (info.getFilterStrings() != null) {
                  for (SimpleString filterString : info.getFilterStrings()) {
                     message = createQueueInfoMessage(CoreNotificationType.CONSUMER_CREATED, queueName);

                     message.putStringProperty(ManagementHelper.HDR_ADDRESS, info.getAddress());
                     message.putStringProperty(ManagementHelper.HDR_CLUSTER_NAME, info.getClusterName());
                     message.putStringProperty(ManagementHelper.HDR_ROUTING_NAME, info.getRoutingName());
                     message.putStringProperty(ManagementHelper.HDR_FILTERSTRING, filterString);
                     message.putIntProperty(ManagementHelper.HDR_DISTANCE, info.getDistance());

                     routeQueueInfo(message, queue, true);
                  }
               }
            }
         }
         Message completeMessage = new CoreMessage(storageManager.generateID(), 50);

         completeMessage.setAddress(queueName);
         completeMessage.putBooleanProperty(PostOfficeImpl.HDR_RESET_QUEUE_DATA_COMPLETE, true);
         routeQueueInfo(completeMessage, queue, false);
      }

   }

   @Override
   public String toString() {
      return "PostOfficeImpl [server=" + server + "]";
   }

   private void routeQueueInfo(final Message message, final Queue queue, final boolean applyFilters) throws Exception {
      if (!applyFilters || queue.getFilter() == null || queue.getFilter().match(message)) {
         RoutingContext context = new RoutingContextImpl(null);

         queue.route(message, context);

         processRoute(message, context, false);
      }
   }

   private static class PageDelivery extends TransactionOperationAbstract {

      private final Set<Queue> queues = new HashSet<>();

      public void addQueues(List<Queue> queueList) {
         queues.addAll(queueList);
      }

      @Override
      public void afterCommit(Transaction tx) {
         // We need to try delivering async after paging, or nothing may start a delivery after paging since nothing is
         // going towards the queues
         // The queue will try to depage case it's empty
         for (Queue queue : queues) {
            queue.deliverAsync();
         }
      }

      @Override
      public List<MessageReference> getRelatedMessageReferences() {
         return Collections.emptyList();
      }

   }

   @Override
   public void processRoute(final Message message,
                            final RoutingContext context,
                            final boolean direct) throws Exception {
      final ArrayList<MessageReference> refs = new ArrayList<>();

      Transaction tx = context.getTransaction();
      boolean startedTX = false;

      if (tx == null && mirrorControllerSource != null) {
         context.setReusable(false);
         tx = new TransactionImpl(storageManager).setAsync(true);
         context.setTransaction(tx);
         startedTX = true;
      }

      final Long deliveryTime;

      boolean containsDurables = false;

      if (message.hasScheduledDeliveryTime()) {
         deliveryTime = message.getScheduledDeliveryTime();
      } else {
         deliveryTime = null;
      }
      final SimpleString messageAddress = message.getAddressSimpleString();
      final PagingStore owningStore = pagingManager.getPageStore(messageAddress);
      message.setOwner(owningStore);
      for (Map.Entry<SimpleString, RouteContextList> entry : context.getContexListing().entrySet()) {
         final PagingStore store;
         if (entry.getKey().equals(messageAddress)) {
            store = owningStore;
         } else {
            store = pagingManager.getPageStore(entry.getKey());
         }

         if (store != null && storageManager.addToPage(store, message, context.getTransaction(), entry.getValue())) {
            // We need to kick delivery so the Queues may check for the cursors case they are empty
            schedulePageDelivery(tx, entry);
            continue;
         }

         final List<Queue> nonDurableQueues = entry.getValue().getNonDurableQueues();
         if (!nonDurableQueues.isEmpty()) {
            refs.ensureCapacity(nonDurableQueues.size());
            nonDurableQueues.forEach(queue -> {
               final MessageReference reference = MessageReference.Factory.createReference(message, queue);
               if (deliveryTime != null) {
                  reference.setScheduledDeliveryTime(deliveryTime);
               }
               refs.add(reference);
               queue.refUp(reference);
            });
         }

         final List<Queue> durableQueues = entry.getValue().getDurableQueues();
         if (!durableQueues.isEmpty()) {
            processRouteToDurableQueues(message, context, deliveryTime, tx, durableQueues, refs);
            containsDurables = true;
         }
      }

      if (mirrorControllerSource != null && !context.isMirrorDisabled()) {
         // we check for isMirrorDisabled as to avoid recursive loop from there
         mirrorControllerSource.sendMessage(tx, message, context);
      }


      if (tx != null) {
         tx.addOperation(new AddOperation(refs));
      } else if (!containsDurables) {
         processReferences(refs, direct);
      } else {
         // This will use the same thread if there are no pending operations
         // avoiding a context switch on this case
         storageManager.afterCompleteOperations(new IOCallback() {
            @Override
            public void onError(final int errorCode, final String errorMessage) {
               ActiveMQServerLogger.LOGGER.ioErrorAddingReferences(errorCode, errorMessage);
            }

            @Override
            public void done() {
               processReferences(refs, direct);
            }
         });
      }

      if (startedTX) {
         tx.commit();
      }
   }

   public static void processReferences(List<MessageReference> refs, boolean direct) {
      refs.forEach((ref) -> processReference(ref, direct));
   }

   public static void processReference(MessageReference ref, boolean direct) {
      ref.getQueue().addTail(ref, direct);
   }

   private void processRouteToDurableQueues(final Message message,
                                            final RoutingContext context,
                                            final Long deliveryTime,
                                            final Transaction tx,
                                            final List<Queue> durableQueues,
                                            final ArrayList<MessageReference> refs) throws Exception {
      final int durableQueuesCount = durableQueues.size();
      refs.ensureCapacity(durableQueuesCount);
      final Iterator<Queue> iter = durableQueues.iterator();
      for (int i = 0; i < durableQueuesCount; i++) {
         final Queue queue = iter.next();
         final MessageReference reference = MessageReference.Factory.createReference(message, queue);
         if (context.isAlreadyAcked(message, queue)) {
            reference.setAlreadyAcked();
            if (tx != null) {
               queue.acknowledge(tx, reference);
            }
         }
         if (deliveryTime != null) {
            reference.setScheduledDeliveryTime(deliveryTime);
         }
         refs.add(reference);
         queue.refUp(reference);
         if (message.isDurable()) {
            storeDurableReference(storageManager, message, tx, queue, durableQueuesCount - 1 == i);
            if (deliveryTime != null && deliveryTime > 0) {
               if (tx != null) {
                  storageManager.updateScheduledDeliveryTimeTransactional(tx.getID(), reference);
               } else {
                  storageManager.updateScheduledDeliveryTime(reference);
               }
            }
         }
      }
   }

   public static void storeDurableReference(StorageManager storageManager, Message message,
                          Transaction tx,
                          Queue queue, boolean sync) throws Exception {
      assert message.isDurable();

      final int durableRefCount = queue.durableUp(message);
      if (durableRefCount == 1) {
         if (tx != null) {
            storageManager.storeMessageTransactional(tx.getID(), message);
         } else {
            storageManager.storeMessage(message);
         }
      }
      if (tx != null) {
         storageManager.storeReferenceTransactional(tx.getID(), queue.getID(), message.getMessageID());
         tx.setContainsPersistent();
      } else {
         storageManager.storeReference(queue.getID(), message.getMessageID(), sync);
      }
   }

   /**
    * This will kick a delivery async on the queue, so the queue may have a chance to depage messages
    */
   private void schedulePageDelivery(Transaction tx, Map.Entry<SimpleString, RouteContextList> entry) {
      if (tx != null) {
         PageDelivery delivery = (PageDelivery) tx.getProperty(TransactionPropertyIndexes.PAGE_DELIVERY);
         if (delivery == null) {
            delivery = new PageDelivery();
            tx.putProperty(TransactionPropertyIndexes.PAGE_DELIVERY, delivery);
            tx.addOperation(delivery);
         }

         delivery.addQueues(entry.getValue().getDurableQueues());
         delivery.addQueues(entry.getValue().getNonDurableQueues());
      } else {

         List<Queue> durableQueues = entry.getValue().getDurableQueues();
         List<Queue> nonDurableQueues = entry.getValue().getNonDurableQueues();

         final List<Queue> queues = new ArrayList<>(durableQueues.size() + nonDurableQueues.size());

         queues.addAll(durableQueues);
         queues.addAll(nonDurableQueues);

         storageManager.afterCompleteOperations(new IOCallback() {

            @Override
            public void onError(int errorCode, String errorMessage) {
            }

            @Override
            public void done() {
               for (Queue queue : queues) {
                  // in case of paging, we need to kick asynchronous delivery to try delivering
                  queue.deliverAsync();
               }
            }
         });
      }
   }

   private enum DuplicateCheckResult {
      DuplicateNotStartedTX, NoDuplicateStartedTX, NoDuplicateNotStartedTX
   }

   private DuplicateCheckResult checkDuplicateID(final Message message,
                                                 final RoutingContext context,
                                                 final boolean rejectDuplicates) throws Exception {
      // Check the DuplicateCache for the Bridge first
      final Object bridgeDup = message.removeExtraBytesProperty(Message.HDR_BRIDGE_DUPLICATE_ID);
      if (bridgeDup != null) {
         return checkBridgeDuplicateID(message, context, (byte[]) bridgeDup);
      }
      // if used BridgeDuplicate, it's not going to use the regular duplicate
      // since this will would break redistribution (re-setting the duplicateId)
      final byte[] duplicateIDBytes = message.getDuplicateIDBytes();
      if (duplicateIDBytes == null) {
         return DuplicateCheckResult.NoDuplicateNotStartedTX;
      }
      return checkNotBridgeDuplicateID(message, context, rejectDuplicates, duplicateIDBytes);
   }

   private DuplicateCheckResult checkNotBridgeDuplicateID(final Message message,
                                                          final RoutingContext context,
                                                          final boolean rejectDuplicates,
                                                          final byte[] duplicateIDBytes) throws Exception {
      assert duplicateIDBytes != null && Arrays.equals(message.getDuplicateIDBytes(), duplicateIDBytes);
      final DuplicateIDCache cache = getDuplicateIDCache(context.getAddress(message));
      final boolean isDuplicate = cache.contains(duplicateIDBytes);
      if (rejectDuplicates && isDuplicate) {
         ActiveMQServerLogger.LOGGER.duplicateMessageDetected(message);
         if (context.getTransaction() != null) {
            final String warnMessage = "Duplicate message detected - message will not be routed. Message information:" + message;
            context.getTransaction().markAsRollbackOnly(new ActiveMQDuplicateIdException(warnMessage));
         }
         message.usageDown(); // this will cause large message delete
         return DuplicateCheckResult.DuplicateNotStartedTX;
      }
      if (isDuplicate) {
         assert !rejectDuplicates;
         return DuplicateCheckResult.NoDuplicateNotStartedTX;
      }
      final boolean startedTX;
      if (context.getTransaction() == null) {
         // We need to store the duplicate id atomically with the message storage, so we need to create a tx for this
         context.setTransaction(new TransactionImpl(storageManager));
         startedTX = true;
      } else {
         startedTX = false;
      }
      cache.addToCache(duplicateIDBytes, context.getTransaction(), startedTX);
      return startedTX ? DuplicateCheckResult.NoDuplicateStartedTX : DuplicateCheckResult.NoDuplicateNotStartedTX;
   }

   private DuplicateCheckResult checkBridgeDuplicateID(final Message message,
                                                       final RoutingContext context,
                                                       final byte[] bridgeDupBytes) throws Exception {
      assert bridgeDupBytes != null;
      boolean startedTX = false;
      if (context.getTransaction() == null) {
         context.setTransaction(new TransactionImpl(storageManager));
         startedTX = true;
      }
      // if the message is being sent from the bridge, we just ignore the duplicate id, and use the internal one
      final DuplicateIDCache cacheBridge = getDuplicateIDCache(BRIDGE_CACHE_STR.concat(context.getAddress(message).toString()));
      if (!cacheBridge.atomicVerify(bridgeDupBytes, context.getTransaction())) {
         logger.trace("Message {} hit a bridge duplicate", message);
         context.getTransaction().rollback();
         message.usageDown(); // this will cause large message delete
         return DuplicateCheckResult.DuplicateNotStartedTX;
      }
      return startedTX ? DuplicateCheckResult.NoDuplicateStartedTX : DuplicateCheckResult.NoDuplicateNotStartedTX;
   }

   /**
    * The expiry scanner can't be started until the whole server has been started other wise you may get races
    */
   @Override
   public synchronized void startExpiryScanner() {
      if (expiryReaperPeriod > 0) {
         if (expiryReaperRunnable != null)
            expiryReaperRunnable.stop();
         expiryReaperRunnable = new ExpiryReaper(server.getScheduledPool(), server.getExecutorFactory().getExecutor(), expiryReaperPeriod, TimeUnit.MILLISECONDS, false);

         expiryReaperRunnable.start();
      }
   }

   @Override
   public synchronized void startAddressQueueScanner() {
      reapAddresses(true); // we need to check for empty auto-created queues before the acceptors are on
                                      // empty auto-created queues and addresses should be removed right away
      if (addressQueueReaperPeriod > 0) {
         if (addressQueueReaperRunnable != null)
            addressQueueReaperRunnable.stop();
         addressQueueReaperRunnable = new AddressQueueReaper(server.getScheduledPool(), server.getExecutorFactory().getExecutor(), addressQueueReaperPeriod, TimeUnit.MILLISECONDS, false);

         addressQueueReaperRunnable.start();
      }
   }

   private Message createQueueInfoMessage(final NotificationType type, final SimpleString queueName) {
      Message message = new CoreMessage().initBuffer(50).setMessageID(storageManager.generateID());

      message.setAddress(queueName);

      message.putStringProperty(ManagementHelper.HDR_NOTIFICATION_TYPE, SimpleString.of(type.toString()));

      long timestamp = System.currentTimeMillis();
      message.putLongProperty(ManagementHelper.HDR_NOTIFICATION_TIMESTAMP, timestamp);
      message.setTimestamp(timestamp);

      return message;
   }

   private final class ExpiryReaper extends ActiveMQScheduledComponent {

      ExpiryReaper(ScheduledExecutorService scheduledExecutorService,
                   Executor executor,
                   long checkPeriod,
                   TimeUnit timeUnit,
                   boolean onDemand) {
         super(scheduledExecutorService, executor, checkPeriod, timeUnit, onDemand);
      }

      private Iterator<Queue> iterator;

      private Queue currentQueue;

      @Override
      public void run() {
         if (iterator != null) {
            logger.debug("A previous reaping call has not finished yet, and it is currently working on {}", currentQueue);
            return;
         }

         iterator = iterableOf(getLocalQueues()).iterator();

         moveNext();
      }

      private void done() {
         executor.execute(this::moveNext);
      }

      private void moveNext() {
         if (!iterator.hasNext() || !this.isStarted()) {
            iterator = null;
            currentQueue = null;
            return;
         }

         currentQueue = iterator.next();

         if (currentQueue == null) {
            logger.debug("iterator.next returned null on ExpiryReaper, giving up iteration");
            // I don't think this should ever happen, this check should be moot
            // However I preferred to have this in place just in case
            iterator = null;
         } else {
            // we will expire messages on this queue, once done we move to the next queue
            currentQueue.expireReferences(this::done);
         }
      }
   }

   private final class AddressQueueReaper extends ActiveMQScheduledComponent {

      AddressQueueReaper(ScheduledExecutorService scheduledExecutorService,
                         Executor executor,
                         long checkPeriod,
                         TimeUnit timeUnit,
                         boolean onDemand) {
         super(scheduledExecutorService, executor, checkPeriod, timeUnit, onDemand);
      }

      @Override
      public void run() {
         reapAddresses(false);
      }
   }

   private static boolean queueWasUsed(Queue queue, AddressSettings settings) {
      return queue.getMessagesExpired() > 0 || queue.getMessagesAcknowledged() > 0 || queue.getMessagesKilled() > 0 || queue.getConsumerRemovedTimestamp() != -1 || settings.getAutoDeleteQueuesSkipUsageCheck();
   }

   /**
    * To be used by the AddressQueueReaper. It is also exposed for tests through PostOfficeTestAccessor
    */
   void reapAddresses(boolean initialCheck) {
      getLocalQueues().forEach(queue -> {
         AddressSettings settings = addressSettingsRepository.getMatch(queue.getAddress().toString());
         if (!queue.isInternalQueue() && queue.isAutoDelete() && QueueManagerImpl.consumerCountCheck(queue) && (initialCheck || QueueManagerImpl.delayCheck(queue, settings)) && QueueManagerImpl.messageCountCheck(queue) && (initialCheck || queueWasUsed(queue, settings))) {
            // we only reap queues on the initialCheck if they are actually empty
            PagingStore queuePagingStore = queue.getPagingStore();
            boolean isPaging = queuePagingStore != null && queuePagingStore.isStorePaging();
            boolean validInitialCheck = initialCheck && queue.getMessageCount() == 0 && !isPaging;
            if (validInitialCheck || queue.isSwept()) {
               if (logger.isDebugEnabled()) {
                  if (initialCheck) {
                     logger.debug("Removing queue {} during the reload check", queue.getName());
                  } else {
                     logger.debug("Removing queue {} after it being swept twice on reaping process", queue.getName());
                  }
               }
               QueueManagerImpl.performAutoDeleteQueue(server, queue);
            } else {
               queue.setSwept(true);
            }
         } else {
            queue.setSwept(false);
         }
      });

      Set<SimpleString> addresses = addressManager.getAddresses();

      for (SimpleString address : addresses) {
         AddressInfo addressInfo = getAddressInfo(address);
         AddressSettings settings = addressSettingsRepository.getMatch(address.toString());

         try {
            if (addressManager.checkAutoRemoveAddress(addressInfo, settings, initialCheck)) {
               if (initialCheck || addressInfo.isSwept()) {

                  server.autoRemoveAddressInfo(address, null);
               } else {
                  logger.debug("Sweeping address {}", address);
                  addressInfo.setSwept(true);
               }
            } else {
               if (addressInfo != null) {
                  addressInfo.setSwept(false);
               }
            }
         } catch (ActiveMQShutdownException e) {
            // the address and queue reaper is asynchronous so it may happen
            // that the broker is shutting down while the reaper iterates
            // through the addresses, next restart this operation will be retried
            logger.debug(e.getMessage(), e);
         } catch (Exception e) {
            if (e instanceof ActiveMQAddressDoesNotExistException && getAddressInfo(address) == null) {
               // the address and queue reaper is asynchronous so it may happen
               // that the address is removed before the reaper removes it
               logger.debug(e.getMessage(), e);
            } else {
               ActiveMQServerLogger.LOGGER.errorRemovingAutoCreatedDestination("address", address, e);
            }
         }
      }
   }

   private Stream<Queue> getLocalQueues() {
      return addressManager.getBindings()
         .filter(binding -> binding.getType() == BindingType.LOCAL_QUEUE)
         .map(binding -> (Queue) binding.getBindable());
   }

   public static final class AddOperation implements TransactionOperation {

      private final List<MessageReference> refs;

      AddOperation(final List<MessageReference> refs) {
         this.refs = refs;
      }

      @Override
      public void afterCommit(final Transaction tx) {
         for (MessageReference ref : refs) {
            if (!ref.isAlreadyAcked()) {
               ref.getQueue().addTail(ref, false);
            }
         }
      }

      @Override
      public void afterPrepare(final Transaction tx) {
         for (MessageReference ref : refs) {
            if (ref.isAlreadyAcked()) {
               ref.getQueue().referenceHandled(ref);
               ref.getQueue().incrementMesssagesAdded();
            }
         }
      }

      @Override
      public void afterRollback(final Transaction tx) {
      }

      @Override
      public void beforeCommit(final Transaction tx) throws Exception {
      }

      @Override
      public void beforePrepare(final Transaction tx) throws Exception {
      }

      @Override
      public void beforeRollback(final Transaction tx) throws Exception {
         // Reverse the ref counts, and paging sizes

         for (MessageReference ref : refs) {
            ref.getQueue().refDown(ref);
            Message message = ref.getMessage();
            if (message.isDurable() && ref.getQueue().isDurable()) {
               ref.getQueue().durableDown(message);
            }
         }
      }

      @Override
      public List<MessageReference> getRelatedMessageReferences() {
         return refs;
      }

      @Override
      public List<MessageReference> getListOnConsumer(long consumerID) {
         return Collections.emptyList();
      }
   }

   @Override
   public Bindings createBindings(final SimpleString address) {
      GroupingHandler groupingHandler = server.getGroupingHandler();
      BindingsImpl bindings = new BindingsImpl(CompositeAddress.extractAddressName(address), groupingHandler, storageManager);
      if (groupingHandler != null) {
         groupingHandler.addListener(bindings);
      }
      return bindings;
   }

   @Override
   public AddressManager getAddressManager() {
      return addressManager;
   }

   public ActiveMQServer getServer() {
      return server;
   }
}
