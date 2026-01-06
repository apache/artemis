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
package org.apache.activemq.artemis.core.server.impl;

import javax.transaction.xa.XAException;
import javax.transaction.xa.Xid;
import java.lang.invoke.MethodHandles;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import org.apache.activemq.artemis.Closeable;
import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.ActiveMQAddressDoesNotExistException;
import org.apache.activemq.artemis.api.core.ActiveMQAddressExistsException;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQIOErrorException;
import org.apache.activemq.artemis.api.core.ActiveMQIllegalStateException;
import org.apache.activemq.artemis.api.core.ActiveMQNonExistentQueueException;
import org.apache.activemq.artemis.api.core.ActiveMQQueueExistsException;
import org.apache.activemq.artemis.api.core.AutoCreateResult;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.management.CoreNotificationType;
import org.apache.activemq.artemis.api.core.management.ManagementHelper;
import org.apache.activemq.artemis.core.exception.ActiveMQXAException;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.filter.impl.FilterImpl;
import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.management.impl.view.ProducerField;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.persistence.OperationContext;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.LargeServerMessageImpl;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.BindingType;
import org.apache.activemq.artemis.core.postoffice.PostOffice;
import org.apache.activemq.artemis.core.postoffice.QueueBinding;
import org.apache.activemq.artemis.core.postoffice.RoutingStatus;
import org.apache.activemq.artemis.core.protocol.core.CoreRemotingConnection;
import org.apache.activemq.artemis.core.remoting.CloseListener;
import org.apache.activemq.artemis.core.remoting.FailureListener;
import org.apache.activemq.artemis.core.security.CheckType;
import org.apache.activemq.artemis.core.security.SecurityAuth;
import org.apache.activemq.artemis.core.security.SecurityStore;
import org.apache.activemq.artemis.core.server.ActiveMQMessageBundle;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.AddressQueryResult;
import org.apache.activemq.artemis.core.server.BindingQueryResult;
import org.apache.activemq.artemis.core.server.LargeServerMessage;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.QueueQueryResult;
import org.apache.activemq.artemis.core.server.RoutingContext;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.core.server.ServerProducer;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.server.TempResourceObserver;
import org.apache.activemq.artemis.core.server.files.FileStoreMonitor;
import org.apache.activemq.artemis.core.server.management.ManagementService;
import org.apache.activemq.artemis.core.server.management.Notification;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.transaction.ResourceManager;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.core.transaction.Transaction.State;
import org.apache.activemq.artemis.core.transaction.TransactionOperationAbstract;
import org.apache.activemq.artemis.core.transaction.TransactionPropertyIndexes;
import org.apache.activemq.artemis.core.transaction.impl.TransactionImpl;
import org.apache.activemq.artemis.json.JsonArrayBuilder;
import org.apache.activemq.artemis.json.JsonObjectBuilder;
import org.apache.activemq.artemis.json.JsonValue;
import org.apache.activemq.artemis.logs.AuditLogger;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.protocol.SessionCallback;
import org.apache.activemq.artemis.utils.ByteUtil;
import org.apache.activemq.artemis.utils.CertificateUtil;
import org.apache.activemq.artemis.utils.CompositeAddress;
import org.apache.activemq.artemis.utils.JsonLoader;
import org.apache.activemq.artemis.utils.PrefixUtil;
import org.apache.activemq.artemis.utils.collections.TypedProperties;
import org.apache.activemq.artemis.utils.critical.CriticalComponentImpl;
import org.apache.activemq.artemis.utils.runnables.AtomicRunnable;
import org.apache.activemq.artemis.utils.runnables.RunnableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server side Session implementation
 */
public class ServerSessionImpl extends CriticalComponentImpl implements ServerSession, FailureListener {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final int CRITICAL_PATH_CLOSE = 0;

   private boolean securityEnabled = true;

   private final String securityDomain;

   protected final String username;

   protected final String password;

   protected final String validatedUser;

   private final int minLargeMessageSize;

   protected boolean autoCommitSends;

   protected boolean autoCommitAcks;

   protected final boolean preAcknowledge;

   protected final boolean strictUpdateDeliveryCount;

   protected RemotingConnection remotingConnection;

   protected final Map<Long, ServerConsumer> consumers = new ConcurrentHashMap<>();

   private final RunnableList blockedRunnables = new RunnableList();

   protected final ServerProducers serverProducers;

   protected volatile Transaction tx;

   /**
    * This will store the Transaction between xaEnd and xaPrepare or xaCommit. In a failure scenario (client is gone),
    * this will be held between xaEnd and xaCommit.
    */
   protected volatile Transaction pendingTX;

   protected boolean xa;

   protected final PagingManager pagingManager;

   protected final StorageManager storageManager;

   private final ResourceManager resourceManager;

   public final PostOffice postOffice;

   private final SecurityStore securityStore;

   protected final ManagementService managementService;

   protected volatile boolean started = false;

   protected final Map<SimpleString, TempResourceCleanerUpper> tempQueueCleanerUppers = new HashMap<>();

   protected final Map<SimpleString, TempResourceCleanerUpper> tempAddressCleanerUppers = new HashMap<>();

   protected final String name;

   protected final ActiveMQServer server;

   private final SimpleString managementAddress;

   protected final RoutingContext routingContext = new RoutingContextImpl(null).setServerSession(this);

   protected final SessionCallback callback;

   private volatile SimpleString defaultAddress;

   private volatile int timeoutSeconds;

   private Map<String, String> metaData;

   private final OperationContext context;

   private final long creationTime = System.currentTimeMillis();

   // to prevent session from being closed twice.
   // this can happen when a session close from client just
   // arrives while the connection failure is detected at the
   // server. Both the request and failure listener will
   // try to close one session from different threads
   // concurrently.
   private volatile boolean closing = false;

   // When the doClose is called, we will make it actually closed
   private volatile boolean closed = false;

   private boolean prefixEnabled = false;

   private Map<SimpleString, RoutingType> prefixes;

   private Set<Closeable> closeables;

   private final Executor sessionExecutor;

   public ServerSessionImpl(final String name,
                            final String username,
                            final String password,
                            final String validatedUser,
                            final int minLargeMessageSize,
                            final boolean autoCommitSends,
                            final boolean autoCommitAcks,
                            final boolean preAcknowledge,
                            final boolean strictUpdateDeliveryCount,
                            final boolean xa,
                            final RemotingConnection remotingConnection,
                            final ActiveMQServer server,
                            final SimpleString defaultAddress,
                            final SessionCallback callback,
                            final OperationContext context,
                            final Map<SimpleString, RoutingType> prefixes,
                            final String securityDomain,
                            boolean isLegacyProducer) throws Exception {
      super(server.getCriticalAnalyzer(), 1);

      this.username = username;

      this.password = password;

      this.validatedUser = validatedUser;

      this.minLargeMessageSize = minLargeMessageSize;

      this.autoCommitSends = autoCommitSends;

      this.autoCommitAcks = autoCommitAcks;

      this.preAcknowledge = preAcknowledge;

      this.remotingConnection = remotingConnection;

      this.storageManager = server.getStorageManager();

      this.postOffice = server.getPostOffice();

      this.resourceManager = server.getResourceManager();

      this.securityStore = server.getSecurityStore();

      this.pagingManager = server.getPagingManager();

      timeoutSeconds = resourceManager.getTimeoutSeconds();
      this.xa = xa;

      this.strictUpdateDeliveryCount = strictUpdateDeliveryCount;

      this.managementService = server.getManagementService();

      this.name = name;

      this.server = server;

      this.prefixes = prefixes;
      if (this.prefixes != null && !this.prefixes.isEmpty()) {
         prefixEnabled = true;
      }

      this.managementAddress = managementService.getManagementAddress();

      this.callback = callback;

      this.defaultAddress = defaultAddress;

      this.context = context;

      this.sessionExecutor = server.getExecutorFactory().getExecutor();

      if (!xa) {
         tx = newTransaction();
      }
      //When the ServerSessionImpl initialization is complete, need to create and send a SESSION_CREATED notification.
      sendSessionNotification(CoreNotificationType.SESSION_CREATED);

      this.securityDomain = securityDomain;
      if (isLegacyProducer) {
         serverProducers = new ServerLegacyProducersImpl(this);
      } else {
         serverProducers = new ServerProducersImpl();
      }
   }

   // ServerSession implementation ---------------------------------------------------------------------------
   @Override
   public void enableSecurity() {
      this.securityEnabled = true;
   }

   @Override
   public void addCloseable(Closeable closeable) {
      if (closeables == null) {
         closeables = new HashSet<>();
      }
      this.closeables.add(closeable);
   }

   // for testing
   public final Set<Closeable> getCloseables() {
      return closeables;
   }

   public Map<SimpleString, TempResourceCleanerUpper> getTempQueueCleanUppers() {
      return tempQueueCleanerUppers;
   }

   @Override
   public Executor getSessionExecutor() {
      return sessionExecutor;
   }

   @Override
   public void disableSecurity() {
      this.securityEnabled = false;
   }

   @Override
   public boolean isClosed() {
      return closing;
   }

   @Override
   public OperationContext getSessionContext() {
      return context;
   }

   @Override
   public String getUsername() {
      return username;
   }

   @Override
   public String getPassword() {
      return password;
   }

   @Override
   public int getMinLargeMessageSize() {
      return minLargeMessageSize;
   }

   @Override
   public String getName() {
      return name;
   }

   @Override
   public Object getConnectionID() {
      return remotingConnection.getID();
   }

   @Override
   public Set<ServerConsumer> getServerConsumers() {
      Set<ServerConsumer> consumersClone = new HashSet<>(consumers.values());
      return Collections.unmodifiableSet(consumersClone);
   }

   @Override
   public void markTXFailed(Throwable e) {
      Transaction currentTX = this.tx;
      if (currentTX != null) {
         if (e instanceof ActiveMQException activeMQException) {
            currentTX.markAsRollbackOnly(activeMQException);
         } else {
            ActiveMQException exception = new ActiveMQException(e.getMessage());
            exception.initCause(e);
            currentTX.markAsRollbackOnly(exception);
         }
      }
   }

   @Override
   public boolean removeConsumer(final long consumerID) throws Exception {
      return consumers.remove(consumerID) != null;
   }

   protected void doClose(final boolean failed) throws Exception {
      blockedRunnables.cancel();

      if (callback != null) {
         callback.close(failed);
      }
      synchronized (this) {
         if (closed) {
            return;
         }
         closed = true;
         if (server.hasBrokerSessionPlugins()) {
            server.callBrokerSessionPlugins(plugin -> plugin.beforeCloseSession(this, failed));
         }
         this.setStarted(false);
         if (failed) {

            Transaction txToRollback = tx;
            if (txToRollback != null) {
               if (txToRollback.tryRollback() && txToRollback.getXid() != null) {
                  resourceManager.removeTransaction(txToRollback.getXid(), remotingConnection);
               }
            }

            txToRollback = pendingTX;

            if (txToRollback != null) {
               if (txToRollback.tryRollback() && txToRollback.getXid() != null) {
                  resourceManager.removeTransaction(txToRollback.getXid(), remotingConnection);
               }
            }

         } else {
            if (tx != null && tx.getXid() == null) {
               // We only rollback local txs on close, not XA tx branches

               try {
                  rollback(failed, false);
               } catch (Exception e) {
                  ActiveMQServerLogger.LOGGER.unableToRollbackOnClose(e);
               }
            }
         }
      }

      //putting closing of consumers outside the sync block
      Set<ServerConsumer> consumersClone = new HashSet<>(consumers.values());

      for (ServerConsumer consumer : consumersClone) {
         try {
            consumer.close(failed);
         } catch (Throwable e) {
            ActiveMQServerLogger.LOGGER.unableToCloseConsumer(e);
            try {
               consumer.removeItself();
            } catch (Throwable e2) {
               ActiveMQServerLogger.LOGGER.unableToRemoveConsumer(e2);
            }
         }
      }

      try {
         if (consumers != null) {
            consumers.clear();
         }
         if (serverProducers != null) {
            serverProducers.clear();
         }
      } catch (Throwable e) {
         logger.warn(e.getMessage(), e);
      }


      if (closeables != null) {
         for (Closeable closeable : closeables) {
            closeable.close(failed);
         }
      }

      synchronized (this) {
         server.removeSession(name);

         remotingConnection.removeFailureListener(this);

         if (callback != null) {
            callback.closed();
         }

         //When the ServerSessionImpl is closed, need to create and send a SESSION_CLOSED notification.
         sendSessionNotification(CoreNotificationType.SESSION_CLOSED);

         if (server.hasBrokerSessionPlugins()) {
            server.callBrokerSessionPlugins(plugin -> plugin.afterCloseSession(this, failed));
         }
      }
   }

   private void sendSessionNotification(final CoreNotificationType type) throws Exception {
      if (server.getConfiguration().isSuppressSessionNotifications()) {
         return;
      }
      // Send notification in a new thread to avoid blocking and potentially soft dead-locking during heavy session open/close
      final RemotingConnection stableRemotingConnection = remotingConnection;
      sessionExecutor.execute(() -> {
         final TypedProperties props = new TypedProperties();
         if (stableRemotingConnection.getID() != null) {
            props.putSimpleStringProperty(ManagementHelper.HDR_CONNECTION_NAME, SimpleString.of(stableRemotingConnection.getID().toString()));
         }
         props.putSimpleStringProperty(ManagementHelper.HDR_USER, SimpleString.of(this.getUsername()));
         props.putSimpleStringProperty(ManagementHelper.HDR_SESSION_NAME, SimpleString.of(this.getName()));

         props.putSimpleStringProperty(ManagementHelper.HDR_CLIENT_ID, SimpleString.of(stableRemotingConnection.getClientID()));
         props.putSimpleStringProperty(ManagementHelper.HDR_PROTOCOL_NAME, SimpleString.of(stableRemotingConnection.getProtocolName()));
         props.putSimpleStringProperty(ManagementHelper.HDR_ADDRESS, managementService.getManagementNotificationAddress());
         props.putIntProperty(ManagementHelper.HDR_DISTANCE, 0);
         try {
            managementService.sendNotification(new Notification(null, type, props));
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.failedToSendNotification(e);
         }
      });
   }

   private void securityCheck(SimpleString address, CheckType checkType, SecurityAuth auth) throws Exception {
      if (securityEnabled) {
         securityStore.check(address, checkType, auth);
      }
   }

   private void securityCheck(SimpleString address, SimpleString queue, CheckType checkType, SecurityAuth auth) throws Exception {
      if (securityEnabled) {
         securityStore.check(address, queue, checkType, auth);
      }
   }

   @Override
   public ServerConsumer createConsumer(final long consumerID,
                                        final SimpleString queueName,
                                        final SimpleString filterString,
                                        final boolean browseOnly) throws Exception {
      return this.createConsumer(consumerID, queueName, filterString, browseOnly, true, null);
   }

   @Override
   public ServerConsumer createConsumer(final long consumerID,
                                        final SimpleString queueName,
                                        final SimpleString filterString,
                                        final boolean browseOnly,
                                        final boolean supportLargeMessage,
                                        final Integer credits) throws Exception {
      return this.createConsumer(consumerID, queueName, filterString, ActiveMQDefaultConfiguration.getDefaultConsumerPriority(), browseOnly, supportLargeMessage, credits);
   }

   @Override
   public ServerConsumer createConsumer(final long consumerID,
                                        final SimpleString queueName,
                                        final SimpleString filterString,
                                        final int priority,
                                        final boolean browseOnly,
                                        final boolean supportLargeMessage,
                                        final Integer credits) throws Exception {
      return this.createConsumer(consumerID, queueName, filterString, priority, browseOnly, supportLargeMessage, credits, true);
   }

   public ServerConsumer createInternalConsumer(final SimpleString queueName) throws Exception {
      return this.createConsumer(storageManager.generateID(), queueName, null, ActiveMQDefaultConfiguration.getDefaultConsumerPriority(), false, false, -1, false);
   }

   @Override
   public ServerConsumer createConsumer(final long consumerID,
                                        final SimpleString queueName,
                                        final SimpleString filterString,
                                        final int priority,
                                        final boolean browseOnly,
                                        final boolean supportLargeMessage,
                                        final Integer credits,
                                        final boolean enforceSecurity) throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.createCoreConsumer(this, remotingConnection.getSubject(), remotingConnection.getRemoteAddress(), consumerID, queueName, filterString, priority, browseOnly, supportLargeMessage, credits);
      }
      final SimpleString unPrefixedQueueName = removePrefix(queueName);

      Binding binding = postOffice.getBinding(unPrefixedQueueName);

      if (binding == null || binding.getType() != BindingType.LOCAL_QUEUE) {
         throw ActiveMQMessageBundle.BUNDLE.noSuchQueue(unPrefixedQueueName);
      }

      SimpleString address = removePrefix(binding.getAddress());
      if (enforceSecurity) {
         try {
            securityCheck(address, unPrefixedQueueName, browseOnly ? CheckType.BROWSE : CheckType.CONSUME, this);
         } catch (Exception e) {
            /*
             * This is here for backwards compatibility with the pre-FQQN syntax from ARTEMIS-592.
             * We only want to do this check if an exact match exists in the security-settings.
             * This code is deprecated and should be removed at the release of the next major version.
             */
            SimpleString exactMatch = address.concat(".").concat(unPrefixedQueueName);
            if (server.getSecurityRepository().containsExactMatch(exactMatch.toString())) {
               securityCheck(exactMatch, unPrefixedQueueName, browseOnly ? CheckType.BROWSE : CheckType.CONSUME, this);
            } else {
               throw e;
            }
         }
      }

      Filter filter = FilterImpl.createFilter(filterString);

      if (server.hasBrokerConsumerPlugins()) {
         server.callBrokerConsumerPlugins(plugin -> plugin.beforeCreateConsumer(consumerID, (QueueBinding) binding,
               filterString, browseOnly, supportLargeMessage));
      }

      ServerConsumer consumer;
      synchronized (this) {
         if (closing) {
            throw ActiveMQMessageBundle.BUNDLE.cannotCreateConsumerOnClosedSession(queueName);
         }
         consumer = new ServerConsumerImpl(consumerID, this, (QueueBinding) binding, filter, priority, started, browseOnly, storageManager, callback, preAcknowledge, strictUpdateDeliveryCount, managementService, supportLargeMessage, credits, server);
         consumers.put(consumer.getID(), consumer);
      }

      if (server.hasBrokerConsumerPlugins()) {
         server.callBrokerConsumerPlugins(plugin -> plugin.afterCreateConsumer(consumer));
      }

      if (!browseOnly) {
         TypedProperties props = new TypedProperties();

         props.putSimpleStringProperty(ManagementHelper.HDR_ADDRESS, CompositeAddress.isFullyQualified(unPrefixedQueueName) ? unPrefixedQueueName : address);

         props.putSimpleStringProperty(ManagementHelper.HDR_CLUSTER_NAME, binding.getClusterName());

         props.putSimpleStringProperty(ManagementHelper.HDR_ROUTING_NAME, binding.getRoutingName());

         props.putIntProperty(ManagementHelper.HDR_DISTANCE, binding.getDistance());

         Queue theQueue = (Queue) binding.getBindable();

         props.putIntProperty(ManagementHelper.HDR_CONSUMER_COUNT, theQueue.getConsumerCount());

         props.putSimpleStringProperty(ManagementHelper.HDR_USER, SimpleString.of(username));

         props.putSimpleStringProperty(ManagementHelper.HDR_VALIDATED_USER, SimpleString.of(validatedUser));

         String certSubjectDN = "unavailable";
         X509Certificate[] certs = CertificateUtil.getCertsFromConnection(this.remotingConnection);
         if (certs != null && certs.length > 0 && certs[0] != null) {
            certSubjectDN = certs[0].getSubjectDN().getName();
         }

         props.putSimpleStringProperty(ManagementHelper.HDR_CERT_SUBJECT_DN, SimpleString.of(certSubjectDN));

         props.putSimpleStringProperty(ManagementHelper.HDR_REMOTE_ADDRESS, SimpleString.of(this.remotingConnection.getRemoteAddress()));

         props.putSimpleStringProperty(ManagementHelper.HDR_SESSION_NAME, SimpleString.of(name));

         if (filter != null) {
            props.putSimpleStringProperty(ManagementHelper.HDR_FILTERSTRING, filter.getFilterString());
         }

         if (remotingConnection.getClientID() != null) {
            props.putSimpleStringProperty(ManagementHelper.HDR_CLIENT_ID, SimpleString.of(remotingConnection.getClientID()));
         }

         props.putLongProperty(ManagementHelper.HDR_CONSUMER_NAME, consumer.getID());

         Notification notification = new Notification(null, CoreNotificationType.CONSUMER_CREATED, props);

         if (logger.isDebugEnabled()) {
            logger.debug("Session with user={}, connection={} created a consumer on queue {}, filter = {}",
                  username, remotingConnection, unPrefixedQueueName, filterString);
         }

         managementService.sendNotification(notification);
      }

      return consumer;
   }

   /**
    * Some protocols may chose to hold their transactions outside of the ServerSession. This can be used to replace the
    * transaction. Notice that we set autoCommitACK and autoCommitSends to true if tx == null
    */
   @Override
   public synchronized void resetTX(Transaction transaction) {
      this.tx = transaction;
      this.autoCommitAcks = transaction == null;
      this.autoCommitSends = transaction == null;
   }

   @Deprecated
   @Override
   public Queue createQueue(final SimpleString address,
                            final SimpleString name,
                            final SimpleString filterString,
                            final boolean temporary,
                            final boolean durable) throws Exception {
      AddressSettings as = server.getAddressSettingsRepository().getMatch(address.toString());
      return createQueue(address, name, as.getDefaultQueueRoutingType(), filterString, temporary, durable, as.getDefaultMaxConsumers(), as.isDefaultPurgeOnNoConsumers(), false);
   }

   @Deprecated
   @Override
   public Queue createQueue(final SimpleString address,
                            final SimpleString name,
                            final RoutingType routingType,
                            final SimpleString filterString,
                            final boolean temporary,
                            final boolean durable) throws Exception {
      AddressSettings as = server.getAddressSettingsRepository().getMatch(address.toString());
      return createQueue(address, name, routingType, filterString, temporary, durable, as.getDefaultMaxConsumers(), as.isDefaultPurgeOnNoConsumers(), false);
   }

   @Deprecated
   @Override
   public Queue createQueue(AddressInfo addressInfo, SimpleString name, SimpleString filterString, boolean temporary, boolean durable) throws Exception {
      AddressSettings as = server.getAddressSettingsRepository().getMatch(addressInfo.getName().toString());
      return createQueue(addressInfo, name, filterString, temporary, durable, as.getDefaultMaxConsumers(), as.isDefaultPurgeOnNoConsumers(), as.isDefaultExclusiveQueue(), as.isDefaultGroupRebalance(), as.getDefaultGroupBuckets(), as.getDefaultGroupFirstKey(), as.isDefaultLastValueQueue(), as.getDefaultLastValueKey(), as.isDefaultNonDestructive(), as.getDefaultConsumersBeforeDispatch(), as.getDefaultDelayBeforeDispatch(), ActiveMQServerImpl.isAutoDelete(false, as), as.getAutoDeleteQueuesDelay(), as.getAutoDeleteQueuesMessageCount(), false, ActiveMQDefaultConfiguration.getDefaultRingSize());
   }

   @Deprecated
   public Queue createQueue(final AddressInfo addressInfo,
                            final SimpleString name,
                            final SimpleString filterString,
                            final boolean temporary,
                            final boolean durable,
                            final int maxConsumers,
                            final boolean purgeOnNoConsumers,
                            final boolean exclusive,
                            final boolean groupRebalance,
                            final int groupBuckets,
                            final SimpleString groupFirstKey,
                            final boolean lastValue,
                            SimpleString lastValueKey,
                            final boolean nonDestructive,
                            final int consumersBeforeDispatch,
                            final long delayBeforeDispatch,
                            final boolean autoDelete,
                            final long autoDeleteDelay,
                            final long autoDeleteMessageCount,
                            final boolean autoCreated,
                            final long ringSize) throws Exception {
      return createQueue(QueueConfiguration.of(name)
                            .setAddress(addressInfo.getName())
                            .setRoutingType(addressInfo.getRoutingType())
                            .setFilterString(filterString)
                            .setUser(getUsername())
                            .setDurable(durable)
                            .setTemporary(temporary)
                            .setAutoCreated(autoCreated)
                            .setMaxConsumers(maxConsumers)
                            .setPurgeOnNoConsumers(purgeOnNoConsumers)
                            .setExclusive(exclusive)
                            .setGroupRebalance(groupRebalance)
                            .setGroupBuckets(groupBuckets)
                            .setGroupFirstKey(groupFirstKey)
                            .setLastValue(lastValue)
                            .setLastValueKey(lastValueKey)
                            .setNonDestructive(nonDestructive)
                            .setConsumersBeforeDispatch(consumersBeforeDispatch)
                            .setDelayBeforeDispatch(delayBeforeDispatch)
                            .setAutoDelete(autoDelete)
                            .setAutoDeleteDelay(autoDeleteDelay)
                            .setAutoDeleteMessageCount(autoDeleteMessageCount)
                            .setRingSize(ringSize));
   }

   @Override
   public Queue createQueue(QueueConfiguration queueConfiguration) throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.createQueue(this, remotingConnection.getSubject(), remotingConnection.getRemoteAddress(), queueConfiguration);
      }

      if (queueConfiguration.getAddress().equals(server.getConfiguration().getManagementNotificationAddress())) {
         queueConfiguration.setInternal(true);
      }

      queueConfiguration
         .setRoutingType(getRoutingTypeFromPrefix(queueConfiguration.getAddress(), queueConfiguration.getRoutingType()))
         .setAddress(removePrefix(queueConfiguration.getAddress()))
         .setName(removePrefix(queueConfiguration.getName()));

      // make sure the user has privileges to create this queue
      securityCheck(queueConfiguration.getAddress(), queueConfiguration.getName(), queueConfiguration.isDurable() ? CheckType.CREATE_DURABLE_QUEUE : CheckType.CREATE_NON_DURABLE_QUEUE, this);

      AddressSettings as = server.getAddressSettingsRepository().getMatch(queueConfiguration.getAddress().toString());

      AddressInfo addressInfo = server.getAddressInfo(queueConfiguration.getAddress());

      if (as.isAutoCreateAddresses() && (addressInfo == null || !addressInfo.getRoutingTypes().contains(queueConfiguration.getRoutingType()))) {
         securityCheck(queueConfiguration.getAddress(), queueConfiguration.getName(), CheckType.CREATE_ADDRESS, this);
      }

      server.checkQueueCreationLimit(getValidatedUser());

      Queue queue = server.createQueue(queueConfiguration.setUser(getValidatedUser()));

      if (queueConfiguration.isTemporary()) {
         handleTempResource(queueConfiguration.getName(), true);
      }

      if (logger.isDebugEnabled()) {
         logger.debug("Queue {} created on address {} with filter={} temporary = {} durable={} on session user={}, connection={}",
               queueConfiguration.getName(), queueConfiguration.getAddress(), queueConfiguration.getFilterString(),
               queueConfiguration.isTemporary(), queueConfiguration.isDurable(), username, remotingConnection);
      }

      return queue;
   }

   private void handleTempResource(SimpleString name, boolean queue) {
      // Temporary resource in core simply means the resource will be deleted if the remoting connection dies. It does
      // not mean it will get deleted automatically when the session is closed. It is up to the user to delete the
      // resource when finished with it

      TempResourceCleanerUpper cleaner = new TempResourceCleanerUpper(server, name);
      if (remotingConnection instanceof TempResourceObserver observer) {
         cleaner.setObserver(observer);
      }

      remotingConnection.addCloseListener(cleaner);
      remotingConnection.addFailureListener(cleaner);

      if (queue) {
         tempQueueCleanerUppers.put(name, cleaner);
      } else {
         tempAddressCleanerUppers.put(name, cleaner);
      }
   }

   @Deprecated
   @Override
   public Queue createQueue(final SimpleString address,
                            final SimpleString name,
                            final RoutingType routingType,
                            final SimpleString filterString,
                            final boolean temporary,
                            final boolean durable,
                            final int maxConsumers,
                            final boolean purgeOnNoConsumers,
                            final boolean autoCreated) throws Exception {
      AddressSettings as = server.getAddressSettingsRepository().getMatch(address.toString());
      return createQueue(new AddressInfo(address, routingType), name, filterString, temporary, durable, maxConsumers, purgeOnNoConsumers, as.isDefaultExclusiveQueue(), as.isDefaultGroupRebalance(), as.getDefaultGroupBuckets(), as.getDefaultGroupFirstKey(), as.isDefaultLastValueQueue(), as.getDefaultLastValueKey(), as.isDefaultNonDestructive(), as.getDefaultConsumersBeforeDispatch(), as.getDefaultDelayBeforeDispatch(), ActiveMQServerImpl.isAutoDelete(autoCreated, as), as.getAutoDeleteQueuesDelay(), as.getAutoDeleteQueuesMessageCount(), autoCreated, as.getDefaultRingSize());
   }

   @Deprecated
   @Override
   public Queue createQueue(final SimpleString address,
                            final SimpleString name,
                            final RoutingType routingType,
                            final SimpleString filterString,
                            final boolean temporary,
                            final boolean durable,
                            final int maxConsumers,
                            final boolean purgeOnNoConsumers,
                            final Boolean exclusive,
                            final Boolean lastValue,
                            final boolean autoCreated) throws Exception {
      return createQueue(address, name, routingType, filterString, temporary, durable, maxConsumers, purgeOnNoConsumers, exclusive, null, null, lastValue, null, null, null, null, null, null, null, autoCreated);
   }

   @Deprecated
   @Override
   public Queue createQueue(final SimpleString address,
                            final SimpleString name,
                            final RoutingType routingType,
                            final SimpleString filterString,
                            final boolean temporary,
                            final boolean durable,
                            final int maxConsumers,
                            final boolean purgeOnNoConsumers,
                            final Boolean exclusive,
                            final Boolean groupRebalance,
                            final Integer groupBuckets,
                            final Boolean lastValue,
                            final SimpleString lastValueKey,
                            final Boolean nonDestructive,
                            final Integer consumersBeforeDispatch,
                            final Long delayBeforeDispatch,
                            final Boolean autoDelete,
                            final Long autoDeleteDelay,
                            final Long autoDeleteMessageCount,
                            final boolean autoCreated) throws Exception {
      return createQueue(address, name, routingType, filterString, temporary, durable, maxConsumers, purgeOnNoConsumers, exclusive, groupRebalance, groupBuckets, null, lastValue, lastValueKey, nonDestructive, consumersBeforeDispatch, delayBeforeDispatch, autoDelete, autoDeleteDelay, autoDeleteMessageCount, autoCreated);
   }

   @Deprecated
   @Override
   public Queue createQueue(final SimpleString address,
                            final SimpleString name,
                            final RoutingType routingType,
                            final SimpleString filterString,
                            final boolean temporary,
                            final boolean durable,
                            final int maxConsumers,
                            final boolean purgeOnNoConsumers,
                            final Boolean exclusive,
                            final Boolean groupRebalance,
                            final Integer groupBuckets,
                            final SimpleString groupFirstKey,
                            final Boolean lastValue,
                            final SimpleString lastValueKey,
                            final Boolean nonDestructive,
                            final Integer consumersBeforeDispatch,
                            final Long delayBeforeDispatch,
                            final Boolean autoDelete,
                            final Long autoDeleteDelay,
                            final Long autoDeleteMessageCount,
                            final boolean autoCreated) throws Exception {
      return createQueue(address, name, routingType, filterString, temporary, durable, maxConsumers, purgeOnNoConsumers, exclusive, groupRebalance, groupBuckets, null, lastValue, lastValueKey, nonDestructive, consumersBeforeDispatch, delayBeforeDispatch, autoDelete, autoDeleteDelay, autoDeleteMessageCount, autoCreated, null);
   }

   @Deprecated
   @Override
   public Queue createQueue(final SimpleString address,
                            final SimpleString name,
                            final RoutingType routingType,
                            final SimpleString filterString,
                            final boolean temporary,
                            final boolean durable,
                            final int maxConsumers,
                            final boolean purgeOnNoConsumers,
                            final Boolean exclusive,
                            final Boolean groupRebalance,
                            final Integer groupBuckets,
                            final SimpleString groupFirstKey,
                            final Boolean lastValue,
                            final SimpleString lastValueKey,
                            final Boolean nonDestructive,
                            final Integer consumersBeforeDispatch,
                            final Long delayBeforeDispatch,
                            final Boolean autoDelete,
                            final Long autoDeleteDelay,
                            final Long autoDeleteMessageCount,
                            final boolean autoCreated,
                            final Long ringSize) throws Exception {
      if (exclusive == null || groupRebalance == null || groupBuckets == null || groupFirstKey == null || lastValue == null || lastValueKey == null || nonDestructive == null || consumersBeforeDispatch == null || delayBeforeDispatch == null || autoDelete == null || autoDeleteDelay == null || autoDeleteMessageCount == null || ringSize == null) {
         AddressSettings as = server.getAddressSettingsRepository().getMatch(address.toString());
         return createQueue(new AddressInfo(address, routingType), name, filterString, temporary, durable, maxConsumers, purgeOnNoConsumers,
                 Objects.requireNonNullElse(exclusive, as.isDefaultExclusiveQueue()),
                 Objects.requireNonNullElse(groupRebalance, as.isDefaultGroupRebalance()),
                 Objects.requireNonNullElse(groupBuckets, as.getDefaultGroupBuckets()),
                 Objects.requireNonNullElse(groupFirstKey, as.getDefaultGroupFirstKey()),
                 Objects.requireNonNullElse(lastValue, as.isDefaultLastValueQueue()),
                 Objects.requireNonNullElse(lastValueKey, as.getDefaultLastValueKey()),
                 Objects.requireNonNullElse(nonDestructive, as.isDefaultNonDestructive()),
                 Objects.requireNonNullElse(consumersBeforeDispatch, as.getDefaultConsumersBeforeDispatch()),
                 Objects.requireNonNullElse(delayBeforeDispatch, as.getDefaultDelayBeforeDispatch()),
                 Objects.requireNonNullElse(autoDelete, ActiveMQServerImpl.isAutoDelete(autoCreated, as)),
                 Objects.requireNonNullElse(autoDeleteDelay, as.getAutoDeleteQueuesDelay()),
                 Objects.requireNonNullElse(autoDeleteMessageCount, as.getAutoDeleteQueuesMessageCount()),
                 autoCreated,
                 Objects.requireNonNullElse(ringSize, as.getDefaultRingSize()));
      } else {
         return createQueue(new AddressInfo(address, routingType), name, filterString, temporary, durable, maxConsumers, purgeOnNoConsumers,
                 exclusive, groupRebalance, groupBuckets, groupFirstKey, lastValue, lastValueKey, nonDestructive, consumersBeforeDispatch, delayBeforeDispatch, autoDelete, autoDeleteDelay, autoDeleteMessageCount, autoCreated, ringSize);
      }
   }

   @Deprecated
   @Override
   public Queue createQueue(SimpleString address,
                            SimpleString name,
                            RoutingType routingType,
                            SimpleString filterString,
                            boolean temporary,
                            boolean durable,
                            boolean autoCreated) throws Exception {
      AddressSettings as = server.getAddressSettingsRepository().getMatch(address.toString());
      return createQueue(address, name, routingType, filterString, temporary, durable, as.getDefaultMaxConsumers(), as.isDefaultPurgeOnNoConsumers(), autoCreated);
   }

   @Deprecated
   @Override
   public Queue createQueue(AddressInfo addressInfo, SimpleString name, SimpleString filterString, boolean temporary, boolean durable, boolean autoCreated) throws Exception {
      AddressSettings as = server.getAddressSettingsRepository().getMatch(addressInfo.getName().toString());
      return createQueue(addressInfo, name, filterString, temporary, durable, as.getDefaultMaxConsumers(), as.isDefaultPurgeOnNoConsumers(), as.isDefaultExclusiveQueue(), as.isDefaultGroupRebalance(), as.getDefaultGroupBuckets(), as.getDefaultGroupFirstKey(), as.isDefaultLastValueQueue(), as.getDefaultLastValueKey(), as.isDefaultNonDestructive(), as.getDefaultConsumersBeforeDispatch(), as.getDefaultDelayBeforeDispatch(), ActiveMQServerImpl.isAutoDelete(autoCreated, as), as.getAutoDeleteQueuesDelay(), as.getAutoDeleteQueuesMessageCount(), autoCreated, as.getDefaultRingSize());
   }

   @Deprecated
   @Override
   public Queue createQueue(AddressInfo addressInfo, SimpleString name, SimpleString filterString, boolean temporary, boolean durable, Boolean exclusive, Boolean lastValue, boolean autoCreated) throws Exception {
      AddressSettings as = server.getAddressSettingsRepository().getMatch(addressInfo.getName().toString());
      return createQueue(addressInfo, name, filterString, temporary, durable, as.getDefaultMaxConsumers(), as.isDefaultPurgeOnNoConsumers(),
                         exclusive == null ? as.isDefaultExclusiveQueue() : exclusive, as.isDefaultGroupRebalance(), as.getDefaultGroupBuckets(), as.getDefaultGroupFirstKey(), lastValue == null ? as.isDefaultLastValueQueue() : lastValue, as.getDefaultLastValueKey(), as.isDefaultNonDestructive(), as.getDefaultConsumersBeforeDispatch(), as.getDefaultDelayBeforeDispatch(), ActiveMQServerImpl.isAutoDelete(autoCreated, as), as.getAutoDeleteQueuesDelay(), as.getAutoDeleteQueuesMessageCount(), autoCreated, as.getDefaultRingSize());
   }

   @Override
   public AddressInfo createAddress(final SimpleString address,
                                    EnumSet<RoutingType> routingTypes,
                                    final boolean autoCreated) throws Exception {
      return createAddress(new AddressInfo(address, routingTypes), autoCreated);
   }

   @Override
   public AddressInfo createAddress(final SimpleString address,
                                    RoutingType routingType,
                                    final boolean autoCreated) throws Exception {
      return createAddress(new AddressInfo(address, routingType), autoCreated);
   }

   @Override
   public AddressInfo createAddress(AddressInfo addressInfo, boolean autoCreated) throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.serverSessionCreateAddress(this.getName(), remotingConnection.getSubject(), remotingConnection.getRemoteAddress(), addressInfo, autoCreated);
      }

      AddressInfo art = getAddressAndRoutingType(addressInfo);
      securityCheck(art.getName(), CheckType.CREATE_ADDRESS, this);
      server.addOrUpdateAddressInfo(art.setAutoCreated(autoCreated));
      if (art.isTemporary()) {
         handleTempResource(addressInfo.getName(), false);
      }
      return server.getAddressInfo(art.getName());
   }

   @Deprecated
   @Override
   public void createSharedQueue(SimpleString address,
                                 SimpleString name,
                                 RoutingType routingType,
                                 SimpleString filterString,
                                 boolean durable,
                                 Integer maxConsumers,
                                 Boolean purgeOnNoConsumers,
                                 Boolean exclusive,
                                 Boolean lastValue) throws Exception {
      createSharedQueue(address, name, routingType, filterString, durable, maxConsumers, purgeOnNoConsumers, exclusive, null, null, lastValue, null, null, null, null, null, null, null);
   }

   @Deprecated
   @Override
   public void createSharedQueue(SimpleString address,
                                 SimpleString name,
                                 RoutingType routingType,
                                 SimpleString filterString,
                                 boolean durable,
                                 Integer maxConsumers,
                                 Boolean purgeOnNoConsumers,
                                 Boolean exclusive,
                                 Boolean groupRebalance,
                                 Integer groupBuckets,
                                 Boolean lastValue,
                                 SimpleString lastValueKey,
                                 Boolean nonDestructive,
                                 Integer consumersBeforeDispatch,
                                 Long delayBeforeDispatch,
                                 Boolean autoDelete,
                                 Long autoDeleteDelay,
                                 Long autoDeleteMessageCount) throws Exception {
      createSharedQueue(address, name, routingType, filterString, durable, maxConsumers, purgeOnNoConsumers, exclusive, groupRebalance, groupBuckets, null, lastValue, lastValueKey, nonDestructive, consumersBeforeDispatch, delayBeforeDispatch, autoDelete, autoDeleteDelay, autoDeleteMessageCount);
   }

   @Deprecated
   @Override
   public void createSharedQueue(SimpleString address,
                                 SimpleString name,
                                 RoutingType routingType,
                                 SimpleString filterString,
                                 boolean durable,
                                 Integer maxConsumers,
                                 Boolean purgeOnNoConsumers,
                                 Boolean exclusive,
                                 Boolean groupRebalance,
                                 Integer groupBuckets,
                                 SimpleString groupFirstKey,
                                 Boolean lastValue,
                                 SimpleString lastValueKey,
                                 Boolean nonDestructive,
                                 Integer consumersBeforeDispatch,
                                 Long delayBeforeDispatch,
                                 Boolean autoDelete,
                                 Long autoDeleteDelay,
                                 Long autoDeleteMessageCount) throws Exception {
      createSharedQueue(QueueConfiguration.of(name)
                                  .setAddress(address)
                                  .setFilterString(filterString)
                                  .setUser(getUsername())
                                  .setDurable(durable)
                                  .setMaxConsumers(maxConsumers)
                                  .setPurgeOnNoConsumers(purgeOnNoConsumers)
                                  .setExclusive(exclusive)
                                  .setGroupRebalance(groupRebalance)
                                  .setGroupBuckets(groupBuckets)
                                  .setLastValue(lastValue)
                                  .setLastValueKey(lastValueKey)
                                  .setNonDestructive(nonDestructive)
                                  .setConsumersBeforeDispatch(consumersBeforeDispatch)
                                  .setDelayBeforeDispatch(delayBeforeDispatch)
                                  .setAutoDelete(autoDelete)
                                  .setAutoDeleteDelay(autoDeleteDelay)
                                  .setAutoDeleteMessageCount(autoDeleteMessageCount));
   }

   @Override
   public void createSharedQueue(QueueConfiguration queueConfiguration) throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.createSharedQueue(this, remotingConnection.getSubject(), remotingConnection.getRemoteAddress(), queueConfiguration);
      }
      queueConfiguration.setAddress(removePrefix(queueConfiguration.getAddress()));

      securityCheck(queueConfiguration.getAddress(), queueConfiguration.getName(), queueConfiguration.isDurable() ? CheckType.CREATE_DURABLE_QUEUE : CheckType.CREATE_NON_DURABLE_QUEUE, this);

      server.checkQueueCreationLimit(getValidatedUser());

      server.createSharedQueue(queueConfiguration.setUser(getValidatedUser()));
   }

   @Deprecated
   @Override
   public void createSharedQueue(SimpleString address,
                                 final SimpleString name,
                                 final RoutingType routingType,
                                 boolean durable,
                                 final SimpleString filterString) throws Exception {

      createSharedQueue(address, name, routingType, filterString, durable, null, null, null, null);
   }

   @Deprecated
   @Override
   public void createSharedQueue(final SimpleString address,
                                 final SimpleString name,
                                 boolean durable,
                                 final SimpleString filterString) throws Exception {
      createSharedQueue(address, name, null, durable, filterString);
   }

   @Override
   public RemotingConnection getRemotingConnection() {
      return remotingConnection;
   }

   @Override
   public void transferConnection(RemotingConnection newConnection) {
      synchronized (this) {
         // Remove failure listeners from old connection
         remotingConnection.removeFailureListener(this);
         tempQueueCleanerUppers.values()
                 .forEach(cleanerUpper -> {
                    remotingConnection.removeCloseListener(cleanerUpper);
                    remotingConnection.removeFailureListener(cleanerUpper);
                 });

         // Set the new connection
         remotingConnection = newConnection;

         // Add failure listeners to new connection
         newConnection.addFailureListener(this);
         tempQueueCleanerUppers.values()
                 .forEach(cleanerUpper -> {
                    newConnection.addCloseListener(cleanerUpper);
                    newConnection.addFailureListener(cleanerUpper);
                 });
      }
   }

   @Override
   public String getSecurityDomain() {
      return securityDomain;
   }

   public static class TempResourceCleanerUpper implements CloseListener, FailureListener {

      private final SimpleString resourceName;

      private final ActiveMQServer server;

      private TempResourceObserver observer;

      public TempResourceCleanerUpper(final ActiveMQServer server, final SimpleString resourceName) {
         this.server = server;
         this.resourceName = resourceName;
      }

      public void setObserver(TempResourceObserver observer) {
         this.observer = observer;
      }

      private void run() {
         try {
            logger.debug("deleting temporary resource {}", resourceName);
            try {
               Queue q = server.locateQueue(resourceName);
               if (q != null && q.isTemporary()) {
                  AddressInfo a = server.getAddressInfo(q.getAddress());
                  server.destroyQueue(resourceName, null, false, false, a == null || a.isTemporary());
                  if (observer != null) {
                     observer.tempQueueDeleted(resourceName);
                  }
               }
            } catch (ActiveMQException e) {
               // that's fine.. it can happen due to resource already been deleted
               logger.debug(e.getMessage(), e);
            }
            try {
               AddressInfo a = server.getAddressInfo(resourceName);
               if (a != null && a.isTemporary()) {
                  server.removeAddressInfo(resourceName, null);
                  if (observer != null) {
                     observer.tempAddressDeleted(resourceName);
                  }
               }
            } catch (ActiveMQException e) {
               // that's fine.. it can happen due to resource already been deleted
               logger.debug(e.getMessage(), e);
            }
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.errorRemovingTempResource(resourceName, e);
         }
      }

      @Override
      public void connectionFailed(ActiveMQException exception, boolean failedOver) {
         run();
      }

      @Override
      public void connectionFailed(final ActiveMQException me, boolean failedOver, String scaleDownTargetNodeID) {
         connectionFailed(me, failedOver);
      }

      @Override
      public void connectionClosed() {
         run();
      }

      @Override
      public String toString() {
         return "Temporary Cleaner for resource " + resourceName;
      }

   }

   @Override
   public void deleteQueue(final SimpleString queueToDelete) throws Exception {
      deleteQueue(queueToDelete, true);
   }

   @Override
   public void deleteQueue(final SimpleString queueToDelete, boolean enforceSecurity) throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.destroyQueue(this, remotingConnection.getSubject(), remotingConnection.getRemoteAddress(), queueToDelete);
      }
      final SimpleString unPrefixedQueueName = removePrefix(queueToDelete);

      Binding binding = postOffice.getBinding(unPrefixedQueueName);

      if (binding == null || binding.getType() != BindingType.LOCAL_QUEUE) {
         throw new ActiveMQNonExistentQueueException();
      }

      server.destroyQueue(unPrefixedQueueName, enforceSecurity ? this : null, true, false, true);

      TempResourceCleanerUpper cleaner = this.tempQueueCleanerUppers.remove(unPrefixedQueueName);

      if (cleaner != null) {
         remotingConnection.removeCloseListener(cleaner);

         remotingConnection.removeFailureListener(cleaner);
      }
   }

   @Override
   public void deleteAddress(final SimpleString addressToDelete) throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.destroyAddress(this, remotingConnection.getSubject(), remotingConnection.getRemoteAddress(), addressToDelete);
      }
      final SimpleString unPrefixedAddressName = removePrefix(addressToDelete);

      AddressInfo addressInfo = server.getAddressInfo(unPrefixedAddressName);

      if (addressInfo == null) {
         throw new ActiveMQAddressDoesNotExistException();
      }

      server.removeAddressInfo(unPrefixedAddressName, this);

      TempResourceCleanerUpper cleaner = this.tempAddressCleanerUppers.remove(unPrefixedAddressName);

      if (cleaner != null) {
         remotingConnection.removeCloseListener(cleaner);

         remotingConnection.removeFailureListener(cleaner);
      }
   }

   @Override
   public QueueQueryResult executeQueueQuery(final SimpleString name) throws Exception {
      return server.queueQuery(removePrefix(name));
   }

   @Override
   public AddressQueryResult executeAddressQuery(SimpleString name) throws Exception {
      return server.addressQuery(removePrefix(name));
   }

   @Override
   public BindingQueryResult executeBindingQuery(final SimpleString address) throws Exception {

      boolean newFQQN = true;

      // remotingConnection could be null on UnitTests
      // that's why I'm checking for null here, and it's best to do so
      if (remotingConnection != null && remotingConnection instanceof CoreRemotingConnection coreRemotingConnection) {
         newFQQN = coreRemotingConnection.isVersionNewFQQN();
      }

      return server.bindingQuery(removePrefix(address), newFQQN);
   }

   @Override
   public void forceConsumerDelivery(final long consumerID, final long sequence) throws Exception {
      ServerConsumer consumer = locateConsumer(consumerID);

      // this would be possible if the server consumer was closed by pings/pongs.. etc
      if (consumer != null) {
         consumer.forceDelivery(sequence);
      }
   }

   @Override
   public List<Long> acknowledge(final long consumerID, final long messageID) throws Exception {
      ServerConsumer consumer = findConsumer(consumerID);
      List<Long> ackedRefs = null;

      if (tx != null && tx.getState() == State.ROLLEDBACK) {
         // If we let stuff to be acked on a rolled back TX, we will just have these messages to be stuck on the limbo
         // until the server is restarted. The tx has already timed out, so we need to ack and rollback immediately.
         Transaction newTX = newTransaction();
         try {
            ackedRefs = consumer.acknowledge(newTX, messageID);
         } catch (Exception e) {
            // just ignored
            // will log it just in case
            logger.debug("Ignored exception while acking messageID {} on a rolledback TX", messageID, e);
         }
         newTX.rollback();
      } else {
         ackedRefs = consumer.acknowledge(autoCommitAcks ? null : tx, messageID);
      }

      return ackedRefs;
   }

   @Override
   public ServerConsumer locateConsumer(long consumerID) {
      return consumers.get(consumerID);
   }

   private ServerConsumer findConsumer(long consumerID) throws Exception {
      ServerConsumer consumer = locateConsumer(consumerID);

      if (consumer == null) {
         Transaction currentTX = tx;
         ActiveMQIllegalStateException exception = ActiveMQMessageBundle.BUNDLE.consumerDoesntExist(consumerID);

         if (currentTX != null) {
            currentTX.markAsRollbackOnly(exception);
         }

         throw exception;
      }
      return consumer;
   }

   @Override
   public void individualAcknowledge(final long consumerID, final long messageID) throws Exception {
      ServerConsumer consumer = findConsumer(consumerID);

      if (tx != null && tx.getState() == State.ROLLEDBACK) {
         // If we let stuff to be acked on a rolled back TX, we will just have these messages to be stuck on the limbo
         // until the server is restarted. The tx has already timed out, so we need to ack and rollback immediately.
         Transaction newTX = newTransaction();
         consumer.individualAcknowledge(tx, messageID);
         newTX.rollback();
      } else {
         consumer.individualAcknowledge(autoCommitAcks ? null : tx, messageID);
      }

   }

   @Override
   public void individualCancel(final long consumerID, final long messageID, boolean failed) throws Exception {
      ServerConsumer consumer = locateConsumer(consumerID);

      if (consumer != null) {
         consumer.individualCancel(messageID, failed);
      }

   }

   @Override
   public void expire(final long consumerID, final long messageID) throws Exception {
      final ServerConsumer consumer = locateConsumer(consumerID);
      MessageReference ref = consumer.removeReferenceByID(messageID);

      if (ref != null) {
         ref.getQueue().expire(ref, consumer, true);
      }
   }

   @Override
   public synchronized void commit() throws Exception {
      logger.trace("Calling commit");

      try {
         if (tx != null) {
            tx.commit();
         }
      } finally {
         if (xa) {
            tx = null;
         } else {
            tx = newTransaction();
         }
      }
   }

   @Override
   public void rollback(final boolean considerLastMessageAsDelivered) throws Exception {
      rollback(false, considerLastMessageAsDelivered);
   }

   /**
    * @param clientFailed If the client has failed, we can't decrease the delivery-counts, and the close may issue a
    *                     rollback
    */
   private synchronized void rollback(final boolean clientFailed,
                                      final boolean considerLastMessageAsDelivered) throws Exception {
      if (tx == null) {
         // Might be null if XA

         tx = newTransaction();
      }

      doRollback(clientFailed, considerLastMessageAsDelivered, tx);

      if (xa) {
         tx = null;
      } else {
         tx = newTransaction();
      }
   }

   @Override
   public Transaction newTransaction() {
      return new TransactionImpl(null, storageManager, timeoutSeconds);
   }

   private Transaction newTransaction(final Xid xid) {
      return new TransactionImpl(xid, storageManager, timeoutSeconds);
   }

   @Override
   public synchronized void xaCommit(final Xid xid, final boolean onePhase) throws Exception {
      this.pendingTX = null;

      if (tx != null && tx.getXid().equals(xid)) {
         final String msg = "Cannot commit, session is currently doing work in transaction " + tx.getXid();

         throw new ActiveMQXAException(XAException.XAER_PROTO, msg);
      } else {
         Transaction theTx = resourceManager.removeTransaction(xid, remotingConnection);

         logger.trace("XAcommit into {}, xid={}", theTx, xid);

         if (theTx == null) {
            // checked heuristic committed transactions
            if (resourceManager.getHeuristicCommittedTransactions().contains(xid)) {
               throw new ActiveMQXAException(XAException.XA_HEURCOM, "transaction has been heuristically committed: " + xid);
            } else if (resourceManager.getHeuristicRolledbackTransactions().contains(xid)) {
               // checked heuristic rolled back transactions
               throw new ActiveMQXAException(XAException.XA_HEURRB, "transaction has been heuristically rolled back: " + xid);
            } else {
               logger.trace("XAcommit into {}, xid={} cannot find it", theTx, xid);

               throw new ActiveMQXAException(XAException.XAER_NOTA, "Cannot find xid in resource manager: " + xid);
            }
         } else {
            if (theTx.getState() == Transaction.State.SUSPENDED) {
               // Put it back
               resourceManager.putTransaction(xid, theTx, remotingConnection);

               throw new ActiveMQXAException(XAException.XAER_PROTO, "Cannot commit transaction, it is suspended " + xid);
            } else {
               theTx.commit(onePhase);
            }
         }
      }
   }

   @Override
   public synchronized void xaEnd(final Xid xid) throws Exception {
      if (tx != null && tx.getXid().equals(xid)) {
         if (tx.getState() == Transaction.State.SUSPENDED) {
            final String msg = "Cannot end, transaction is suspended";

            throw new ActiveMQXAException(XAException.XAER_PROTO, msg);
         } else if (tx.getState() == Transaction.State.ROLLEDBACK) {
            final String msg = "Cannot end, transaction is rolled back";

            final boolean timeout = tx.hasTimedOut();
            tx = null;

            if (timeout) {
               throw new ActiveMQXAException(XAException.XA_RBTIMEOUT, msg);
            } else {
               throw new ActiveMQXAException(XAException.XAER_PROTO, msg);
            }
         } else {
            this.pendingTX = tx;
            tx = null;
         }
      } else {
         // It's also legal for the TM to call end for a Xid in the suspended
         // state
         // See JTA 1.1 spec 3.4.4 - state diagram
         // Although in practice TMs rarely do this.
         Transaction theTx = resourceManager.getTransaction(xid);

         if (theTx == null) {
            final String msg = "Cannot find suspended transaction to end " + xid;

            throw new ActiveMQXAException(XAException.XAER_NOTA, msg);
         } else {
            if (theTx.getState() == State.ACTIVE) {
               // nothing to be done on this case, it's already active, we just ignore it and keep live as usual
               // TM 1.2 specs expects this as a regular scenario and it should just be ignored by TM Spec
               return;
            } else if (theTx.getState() != Transaction.State.SUSPENDED) {

               final String msg = "Transaction is not suspended " + xid;

               throw new ActiveMQXAException(XAException.XAER_PROTO, msg);
            } else {
               theTx.resume();
            }
         }
      }
   }

   @Override
   public synchronized void xaForget(final Xid xid) throws Exception {
      long id = resourceManager.removeHeuristicCompletion(xid);

      if (id != -1) {
         try {
            storageManager.deleteHeuristicCompletion(id);
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.unableToDeleteHeuristicCompletion(e);
            throw new ActiveMQXAException(XAException.XAER_RMFAIL);
         }
      } else {
         throw new ActiveMQXAException(XAException.XAER_NOTA);
      }
   }

   @Override
   public synchronized void xaJoin(final Xid xid) throws Exception {
      Transaction theTx = resourceManager.getTransaction(xid);

      if (theTx == null) {
         final String msg = "Cannot find xid in resource manager: " + xid;

         throw new ActiveMQXAException(XAException.XAER_NOTA, msg);
      } else {
         if (theTx.getState() == Transaction.State.SUSPENDED) {
            throw new ActiveMQXAException(XAException.XAER_PROTO, "Cannot join tx, it is suspended " + xid);
         } else {
            tx = theTx;
         }
      }
   }

   @Override
   public synchronized void xaResume(final Xid xid) throws Exception {
      if (tx != null) {
         final String msg = "Cannot resume, session is currently doing work in a transaction " + tx.getXid();

         throw new ActiveMQXAException(XAException.XAER_PROTO, msg);
      } else {
         Transaction theTx = resourceManager.getTransaction(xid);

         if (theTx == null) {
            final String msg = "Cannot find xid in resource manager: " + xid;

            throw new ActiveMQXAException(XAException.XAER_NOTA, msg);
         } else {
            if (theTx.getState() != Transaction.State.SUSPENDED) {
               throw new ActiveMQXAException(XAException.XAER_PROTO, "Cannot resume transaction, it is not suspended " + xid);
            } else {
               tx = theTx;

               tx.resume();
            }
         }
      }
   }

   @Override
   public synchronized void xaRollback(final Xid xid) throws Exception {
      this.pendingTX = null;

      if (tx != null && tx.getXid().equals(xid)) {
         final String msg = "Cannot roll back, session is currently doing work in a transaction " + tx.getXid();

         throw new ActiveMQXAException(XAException.XAER_PROTO, msg);
      } else {
         Transaction theTx = resourceManager.removeTransaction(xid, remotingConnection);
         logger.trace("xarollback into {}", theTx);

         if (theTx == null) {
            // checked heuristic committed transactions
            if (resourceManager.getHeuristicCommittedTransactions().contains(xid)) {
               throw new ActiveMQXAException(XAException.XA_HEURCOM, "transaction has ben heuristically committed: " + xid);
            } else if (resourceManager.getHeuristicRolledbackTransactions().contains(xid)) {
               // checked heuristic rolled back transactions
               throw new ActiveMQXAException(XAException.XA_HEURRB, "transaction has ben heuristically rolled back: " + xid);
            } else {
               throw new ActiveMQXAException(XAException.XAER_NOTA, "Cannot find xid in resource manager: " + xid);
            }
         } else {
            if (theTx.getState() == Transaction.State.SUSPENDED) {
               logger.trace("xarollback into {} sending tx back as it was suspended", theTx);

               // Put it back
               resourceManager.putTransaction(xid, tx, remotingConnection);

               throw new ActiveMQXAException(XAException.XAER_PROTO, "Cannot rollback transaction, it is suspended " + xid);
            } else {
               doRollback(false, false, theTx);
            }
         }
      }
   }

   @Override
   public synchronized void xaStart(final Xid xid) throws Exception {
      if (tx != null) {
         ActiveMQServerLogger.LOGGER.xidReplacedOnXStart(tx.getXid().toString(), xid.toString());

         try {
            if (tx.getState() != Transaction.State.PREPARED) {
               // we don't want to rollback anything prepared here
               if (tx.getXid() != null) {
                  resourceManager.removeTransaction(tx.getXid(), remotingConnection);
               }
               tx.rollback();
            }
         } catch (Exception e) {
            logger.debug("An exception happened while we tried to debug the previous tx, we can ignore this exception", e);
         }
      }

      tx = newTransaction(xid);

      logger.trace("xastart into tx= {}", tx);

      boolean added = resourceManager.putTransaction(xid, tx, remotingConnection);

      if (!added) {
         final String msg = "Cannot start, there is already a xid " + tx.getXid();

         throw new ActiveMQXAException(XAException.XAER_DUPID, msg);
      }
   }

   @Override
   public synchronized void xaFailed(final Xid xid) throws Exception {
      Transaction theTX = resourceManager.getTransaction(xid);

      if (theTX == null) {
         theTX = newTransaction(xid);
         resourceManager.putTransaction(xid, theTX, remotingConnection);
      }

      if (theTX.isEffective()) {
         logger.debug("Client failed with Xid {} but the server already had it {}", xid, theTX.getState());
         tx = null;
      } else {
         theTX.markAsRollbackOnly(new ActiveMQException("Can't commit as a Failover happened during the operation"));
         tx = theTX;
      }

      logger.trace("xastart into tx= {}", tx);
   }

   @Override
   public synchronized void xaSuspend() throws Exception {
      logger.trace("xasuspend on {}", tx);

      if (tx == null) {
         final String msg = "Cannot suspend, session is not doing work in a transaction ";

         throw new ActiveMQXAException(XAException.XAER_PROTO, msg);
      } else {
         if (tx.getState() == Transaction.State.SUSPENDED) {
            final String msg = "Cannot suspend, transaction is already suspended " + tx.getXid();

            throw new ActiveMQXAException(XAException.XAER_PROTO, msg);
         } else {
            tx.suspend();

            tx = null;
         }
      }
   }

   @Override
   public synchronized void xaPrepare(final Xid xid) throws Exception {
      if (tx != null && tx.getXid().equals(xid)) {
         final String msg = "Cannot commit, session is currently doing work in a transaction " + tx.getXid();

         throw new ActiveMQXAException(XAException.XAER_PROTO, msg);
      } else {
         Transaction theTx = resourceManager.getTransaction(xid);

         logger.trace("xaprepare into xid={}, tx={}", xid, tx);

         if (theTx == null) {
            final String msg = "Cannot find xid in resource manager: " + xid;

            throw new ActiveMQXAException(XAException.XAER_NOTA, msg);
         } else {
            if (theTx.getState() == Transaction.State.SUSPENDED) {
               throw new ActiveMQXAException(XAException.XAER_PROTO, "Cannot prepare transaction, it is suspended " + xid);
            } else if (theTx.getState() == Transaction.State.PREPARED) {
               ActiveMQServerLogger.LOGGER.ignoringPrepareOnXidAlreadyCalled(xid.toString());
            } else {
               theTx.prepare();
            }
         }
      }
   }

   @Override
   public List<Xid> xaGetInDoubtXids() {
      return resourceManager.getInDoubtTransactions();
   }

   @Override
   public int xaGetTimeout() {
      return resourceManager.getTimeoutSeconds();
   }

   @Override
   public void xaSetTimeout(final int timeout) {
      timeoutSeconds = timeout;
      if (tx != null) {
         tx.setTimeout(timeout);
      }
   }

   @Override
   public void start() {
      setStarted(true);
   }

   @Override
   public void stop() {
      setStarted(false);
   }

   @Override
   public void close(final boolean failed) {
      close(failed, false);
   }

   @Override
   public void close(final boolean failed, final boolean force) {
      synchronized (this) {
         if (closing) {
            return;
         }
         closing = true;
      }

      if (force) {
         context.reset();
      }

      // We only add the session as component on the critical analyzer
      // while the close is happening between the user's thread and the context's thread.
      // Once finishClose is called to complete the operation, leaveCritical is called
      // and the session is removed from the component's list on the critical analyzer.
      enterCritical(CRITICAL_PATH_CLOSE);
      getCriticalAnalyzer().add(this);

      context.executeOnCompletion(new IOCallback() {
         @Override
         public void onError(int errorCode, String errorMessage) {
            finishClose(failed);
         }

         @Override
         public void done() {
            finishClose(failed);
         }
      });
   }

   private void finishClose(boolean failed) {
      try {
         doClose(failed);
      } catch (Exception e) {
         ActiveMQServerLogger.LOGGER.errorClosingSession(e);
      } finally {
         leaveCritical(CRITICAL_PATH_CLOSE);
         getCriticalAnalyzer().remove(this);
      }
   }

   @Override
   public void closeConsumer(final long consumerID) throws Exception {
      final ServerConsumer consumer = locateConsumer(consumerID);

      if (consumer != null) {
         consumer.close(false);
      } else {
         ActiveMQServerLogger.LOGGER.cannotFindConsumer(consumerID);
      }
   }

   @Override
   public void receiveConsumerCredits(final long consumerID, final int credits) throws Exception {
      ServerConsumer consumer = locateConsumer(consumerID);

      if (consumer == null) {
         logger.debug("There is no consumer with id {}", consumerID);

         return;
      }

      consumer.receiveCredits(credits);
   }

   @Override
   public Transaction getCurrentTransaction() {
      return tx;
   }

   @Override
   public AutoCreateResult checkAutoCreate(final QueueConfiguration queueConfig) throws Exception {
      AutoCreateResult result;
      SimpleString unPrefixedAddress = removePrefix(queueConfig.getAddress());
      SimpleString unPrefixedQueue = removePrefix(queueConfig.getName());
      AddressSettings addressSettings =  server.getAddressSettingsRepository().getMatch(unPrefixedAddress.toString());

      if (unPrefixedAddress.equals(server.getManagementService().getManagementAddress())) {
         return AutoCreateResult.EXISTED;
      }

      /*
       * This is only here to maintain backwards compatibility with the previous implementation.
       *
       * TODO: figure out how to get rid of this
       */
      if (queueConfig.getRoutingType() == null) {
         return AutoCreateResult.EXISTED;
      }

      // No matter what routing-type is used the address must exist already or be automatically created.
      AddressInfo addressInfo = server.getAddressInfo(unPrefixedAddress);
      if (addressInfo == null) {
         // The address doesn't exist.
         if (addressSettings.isAutoCreateAddresses() || queueConfig.isTemporary()) {
            // Try to create the address if possible.
            try {
               createAddress(new AddressInfo(queueConfig.getAddress()).addRoutingType(queueConfig.getRoutingType()).setTemporary(queueConfig.isTemporary()), true);
            } catch (ActiveMQAddressExistsException e) {
               // The address may have been created by another thread in the mean time. Catch and do nothing.
            }
            result = AutoCreateResult.CREATED;
         } else {
            // If the address doesn't exist and can't be autocreated then return that result immediately.
            return AutoCreateResult.NOT_FOUND;
         }
      } else {
         // The address exists.
         if (addressInfo.getRoutingTypes().contains(queueConfig.getRoutingType())) {
            // The existing address supports the requested routing-type.
            result = AutoCreateResult.EXISTED;
         } else {
            // The existing address doesn't support the requested routing-type.
            if (addressSettings.isAutoCreateAddresses() || queueConfig.isTemporary()) {
               // Try to update the address with the new routing-type if possible.
               try {
                  createAddress(new AddressInfo(addressInfo.getName(), EnumSet.copyOf(addressInfo.getRoutingTypes())).addRoutingType(queueConfig.getRoutingType()).setTemporary(addressInfo.isTemporary()), true);
               } catch (ActiveMQAddressExistsException e) {
                  // The address may have been created by another thread in the mean-time. Catch and do nothing.
               }
               result = AutoCreateResult.UPDATED;
            } else {
               // If the address exists but doesn't support the requested routing-type and can't be updated with the new routing-type then return that result immediately.
               return AutoCreateResult.NOT_FOUND;
            }
         }
      }

      if (queueConfig.getRoutingType() == RoutingType.ANYCAST || queueConfig.isFqqn()) {
         Queue q = server.locateQueue(unPrefixedQueue);
         if (q == null) {
            // The queue doesn't exist.
            if (!queueConfig.isFqqn() && server.getPostOffice().getMatchingQueue(unPrefixedAddress, queueConfig.getRoutingType()) != null) {
               // The address has a local, non-wildcard queue with a different name, which is fine. Just ignore it.
               result = AutoCreateResult.EXISTED;
            } else if (addressSettings.isAutoCreateQueues() || queueConfig.isTemporary()) {
               // Try to create the queue.
               try {
                  createQueue(QueueConfiguration.of(queueConfig).setAutoCreated(true));
               } catch (ActiveMQQueueExistsException e) {
                  // The queue may have been created by another thread in the mean-time. Catch and do nothing.
               }
               result = AutoCreateResult.CREATED;
            } else {
               // The queue doesn't exist, and we can't auto-create it so return that result immediately.
               return AutoCreateResult.NOT_FOUND;
            }
         } else {
            // The queue exists.
            if (q.getRoutingType() != RoutingType.ANYCAST && !queueConfig.isFqqn()) {
               // The queue exists, but it does not support the requested routing type, and it's not FQQN.
               return AutoCreateResult.NOT_FOUND;
            } else {
               // The queue exists, and it supports the requested routing type or it's FQQN so it doesn't matter.
               result = AutoCreateResult.EXISTED;
            }
         }
      }

      return result;
   }


   @Override
   public RoutingStatus send(final Message message, final boolean direct, final String senderName) throws Exception {
      return send(message, direct, senderName, false);
   }

   @Override
   public RoutingStatus send(final Message message,
                             final boolean direct,
                             final String senderName,
                             boolean noAutoCreateQueue) throws Exception {
      return send(getCurrentTransaction(), message, direct, senderName, noAutoCreateQueue);
   }

   @Override
   public synchronized RoutingStatus send(Transaction tx,
                                          Message message,
                                          final boolean direct,
                                          final String senderName,
                                          boolean noAutoCreateQueue) throws Exception {
      return send(tx, message, direct, senderName, noAutoCreateQueue, routingContext);
   }

   @Override
   public synchronized RoutingStatus send(Transaction tx,
                                          Message message,
                                          final boolean direct,
                                          final String senderName,
                                          boolean noAutoCreateQueue,
                                          RoutingContext routingContext) throws Exception {
      return send(tx, message.getAddressSimpleString(), message.getRoutingType(), message, direct, senderName, noAutoCreateQueue, routingContext);
   }

   @Override
   public synchronized RoutingStatus send(Transaction tx,
                                          SimpleString address,
                                          RoutingType routingType,
                                          Message msg,
                                          final boolean direct,
                                          final String senderName,
                                          boolean noAutoCreateQueue,
                                          RoutingContext routingContext) throws Exception {
      final Message theMessage = LargeServerMessageImpl.checkLargeMessage(msg, storageManager);

      if (server.hasBrokerMessagePlugins()) {
         server.callBrokerMessagePlugins(plugin -> plugin.beforeSend(this, tx, theMessage, direct, noAutoCreateQueue));
      }

      final RoutingStatus result;
      try {
         // If the protocol doesn't support flow control, we have no choice other than fail the communication
         if (!this.getRemotingConnection().isSupportsFlowControl() && pagingManager.isDiskFull()) {
            long usableSpace = pagingManager.getDiskUsableSpace();
            long totalSpace = pagingManager.getDiskTotalSpace();
            ActiveMQIOErrorException exception = ActiveMQMessageBundle.BUNDLE.diskBeyondLimit(ByteUtil.getHumanReadableByteCount(usableSpace), ByteUtil.getHumanReadableByteCount(totalSpace), String.format("%.1f%%", FileStoreMonitor.calculateUsage(usableSpace, totalSpace) * 100));
            this.getRemotingConnection().fail(exception);
            throw exception;
         }

         //large message may come from StompSession directly, in which
         //case the id header already generated.
         if (!theMessage.isLargeMessage()) {
            long id = storageManager.generateID();
            // This will re-encode the message
            theMessage.setMessageID(id);
         }

         if (defaultAddress == null && address != null) {
            defaultAddress = address;
         }

         if (address == null) {
            // We don't want to force a re-encode when the message gets sent to the consumer
            theMessage.setAddress(defaultAddress);
         } else {
            // We need to carry forward the chosen address from the caller to the later send stages.
            theMessage.setAddress(address);
         }

         if (logger.isTraceEnabled()) {
            logger.trace("send(message={}, direct={}) being called", theMessage, direct);
         }

         if (theMessage.getAddress() == null) {
            // This could happen with some tests that are ignoring messages
            throw ActiveMQMessageBundle.BUNDLE.noAddress();
         }

         if (theMessage.getAddressSimpleString().equals(managementAddress)) {
            // It's a management message

            result = handleManagementMessage(tx, theMessage, direct);
         } else {
            try {
               result = doSend(tx, routingType, theMessage, address, direct, senderName, noAutoCreateQueue, routingContext);
            } catch (ActiveMQIOErrorException e) {
               if (tx != null) {
                  tx.markAsRollbackOnly(e);
               }
               if (theMessage.isLargeMessage()) {
                  ((LargeServerMessage)theMessage).deleteFile();
               }
               throw e;
            }
         }

         if (AuditLogger.isMessageLoggingEnabled()) {
            if (tx != null && !autoCommitSends) {
               AuditLogger.addSendToTransaction(remotingConnection.getSubject(), remotingConnection.getRemoteAddress(), theMessage.toString(), tx.toString());
               tx.addOperation(new TransactionOperationAbstract() {
                  @Override
                  public void afterCommit(Transaction tx) {
                     auditLogSend(theMessage, tx);
                  }

                  @Override
                  public void afterRollback(Transaction tx) {
                     AuditLogger.rolledBackTransaction(remotingConnection.getSubject(), remotingConnection.getRemoteAddress(), tx.toString(), theMessage.toString());
                  }
               });
            } else {
               auditLogSend(theMessage, null);
            }
         }

      } catch (Exception e) {
         if (server.hasBrokerMessagePlugins()) {
            server.callBrokerMessagePlugins(plugin -> plugin.onSendException(this, tx, theMessage, direct, noAutoCreateQueue, e));
         }
         throw e;
      }
      if (server.hasBrokerMessagePlugins()) {
         server.callBrokerMessagePlugins(plugin -> plugin.afterSend(this, autoCommitSends ? null : tx, theMessage, direct, noAutoCreateQueue, result));
      }
      return result;
   }

   private void auditLogSend(Message message, Transaction tx) {
      AuditLogger.coreSendMessage(remotingConnection.getSubject(), remotingConnection.getRemoteAddress(), message.toString(), routingContext, Objects.toString(tx, null));
   }

   @Override
   public void requestProducerCredits(SimpleString address, final int credits) throws Exception {
      final SimpleString addr = removePrefix(address);
      PagingStore store = server.getPagingManager().getPageStore(addr);

      if (store == null) {
         callback.sendProducerCreditsMessage(credits, address);
      } else if (!store.checkMemory(new AtomicRunnable() {
         @Override
         public void atomicRun() {
            callback.sendProducerCreditsMessage(credits, address);
         }
      }, blockedRunnables::add)) {
         callback.sendProducerCreditsFailMessage(credits, address);
      }
   }

   @Override
   public void setTransferring(final boolean transferring) {
      Set<ServerConsumer> consumersClone = new HashSet<>(consumers.values());

      for (ServerConsumer consumer : consumersClone) {
         consumer.setTransferring(transferring);
      }
   }

   @Override
   public void addMetaData(String key, String data) throws Exception {
      if (server.hasBrokerSessionPlugins()) {
         server.callBrokerSessionPlugins(plugin -> plugin.beforeSessionMetadataAdded(this, key, data));
      }

      if (metaData == null) {
         metaData = new HashMap<>();
      }
      metaData.put(key, data);

      if (server.hasBrokerSessionPlugins()) {
         server.callBrokerSessionPlugins(plugin -> plugin.afterSessionMetadataAdded(this, key, data));
      }
   }

   @Override
   public boolean addUniqueMetaData(String key, String data) throws Exception {
      ServerSession sessionWithMetaData = server.lookupSession(key, data);
      if (sessionWithMetaData != null && sessionWithMetaData != this) {
         // There is a duplication of this property
         if (server.hasBrokerSessionPlugins()) {
            server.callBrokerSessionPlugins(plugin -> plugin.duplicateSessionMetadataFailure(this, key, data));
         }
         return false;
      } else {
         addMetaData(key, data);
         if (key.equals(ClientSession.JMS_SESSION_CLIENT_ID_PROPERTY)) {
            remotingConnection.setClientID(data);
         }
         return true;
      }
   }

   @Override
   public String getMetaData(String key) {
      String data = null;
      if (metaData != null) {
         data = metaData.get(key);
      }

      return data;
   }

   @Override
   public Map<String, String> getMetaData() {
      return metaData;
   }

   @Override
   public long getCreationTime() {
      return this.creationTime;
   }

   public StorageManager getStorageManager() {
      return this.storageManager;
   }

   @Override
   public void describeProducersInfo(JsonArrayBuilder array) {
      Map<String, ServerProducer> targetCopy = cloneProducers();
      for (Map.Entry<String, ServerProducer> entry : targetCopy.entrySet()) {
         String uuid = null;
         if (entry.getValue().getLastProducedMessageID() != null) {
            uuid = entry.getValue().getLastProducedMessageID().toString();
         }
         JsonObjectBuilder producerInfo = JsonLoader.createObjectBuilder()
               .add(ProducerField.ID.getName(), String.valueOf(entry.getValue().getID()))
               .add(ProducerField.NAME.getName(), entry.getValue().getName())
               .add(ProducerField.CONNECTION_ID.getName(), this.getConnectionID().toString())
               .add(ProducerField.SESSION.getAlternativeName(), this.getName())
               .add(ProducerField.CREATION_TIME.getName(), String.valueOf(entry.getValue().getCreationTime()))
               .add(ProducerField.ADDRESS.getAlternativeName(), entry.getValue().getAddress())
               .add(ProducerField.LAST_PRODUCED_MESSAGE_ID.getName(), uuid, JsonValue.NULL)
               .add(ProducerField.MESSAGE_SENT.getName(),  entry.getValue().getMessagesSent())
               .add(ProducerField.MESSAGE_SENT_SIZE.getName(), entry.getValue().getMessagesSentSize());
         array.add(producerInfo);
      }
   }

   @Override
   public String getValidatedUser() {
      /*
       * Security is often disabled in tests so if the validated user is null
       * then just return the username supplied directly from the client.
       */
      return validatedUser != null ? validatedUser : username;
   }

   @Override
   public SimpleString getMatchingQueue(SimpleString address, RoutingType routingType) throws Exception {
      return server.getPostOffice().getMatchingQueue(address, routingType);
   }

   @Override
   public SimpleString getMatchingQueue(SimpleString address,
                                        SimpleString queueName,
                                        RoutingType routingType) throws Exception {
      return server.getPostOffice().getMatchingQueue(address, queueName, routingType);
   }

   @Override
   public AddressInfo getAddress(SimpleString address) {
      return server.getPostOffice().getAddressInfo(removePrefix(address));
   }

   @Override
   public String toString() {
      StringBuilder sb = new StringBuilder();
      try {
         long txID;
         if (tx != null) {
            txID = tx.getID();
         } else {
            txID = -1L;
         }
         sb.append("name=").append(name).append(",");
         sb.append("consumers=").append(consumers.size()).append(",");
         sb.append("txID=").append(txID).append(",");
         sb.append("remotingConnection=").append(remotingConnection);
      } catch (Throwable justLogit) {
         logger.debug(justLogit.getMessage(), justLogit);
      }

      insertMetadata(sb);

      // This will actually appear on some management operations
      // so please don't clog this with debug objects
      // unless you provide a special way for management to translate sessions
      return "ServerSessionImpl(" + sb + ")";
   }

   private void insertMetadata(StringBuilder sb) {
      if (this.metaData != null) {
         for (Map.Entry<String, String> value : metaData.entrySet()) {
            if (!sb.isEmpty()) {
               sb.append(",");
            }
            Object tmpValue = value.getValue();
            if (tmpValue == null || tmpValue.toString().isEmpty()) {
               sb.append(value.getKey() + "=*N/A*");
            } else {
               sb.append(value.getKey() + "=" + tmpValue);
            }
         }
      }
   }

   @Override
   public void connectionFailed(final ActiveMQException me, boolean failedOver) {
      /*
       * This can be invoked from Netty (via channelInactive) when the connection has already been closed causing
       * spurious logging about clearing up resources for failed client connections.
       */
      if (closing)
         return;

      try {
         ActiveMQServerLogger.LOGGER.clientConnectionFailed(name);

         close(true);

         ActiveMQServerLogger.LOGGER.clientConnectionFailedClearingSession(name);
      } catch (Throwable t) {
         ActiveMQServerLogger.LOGGER.errorClosingConnection(this);
      }
   }

   @Override
   public void connectionFailed(final ActiveMQException me, boolean failedOver, String scaleDownTargetNodeID) {
      connectionFailed(me, failedOver);
   }

   public Map<String, ServerProducer> cloneProducers() {
      return serverProducers.cloneProducers();
   }

   private void setStarted(final boolean s) {
      Set<ServerConsumer> consumersClone = new HashSet<>(consumers.values());

      for (ServerConsumer consumer : consumersClone) {
         consumer.setStarted(s);
      }

      started = s;
   }

   private RoutingStatus handleManagementMessage(final Transaction tx,
                                                 final Message message,
                                                 final boolean direct) throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.handleManagementMessage(this.getName(), remotingConnection.getSubject(), remotingConnection.getRemoteAddress(), tx, message, direct);
      }
      try {
         securityCheck(removePrefix(message.getAddressSimpleString()), CheckType.MANAGE, this);
      } catch (ActiveMQException e) {
         if (!autoCommitSends) {
            tx.markAsRollbackOnly(e);
         }
         throw e;
      }

      Message reply = managementService.handleMessage(this, message);

      SimpleString replyTo = message.getReplyTo();

      if (replyTo != null) {
         // TODO: move this check somewhere else? this is a JMS-specific bit of logic in the core impl
         if (replyTo.toString().startsWith("queue://") || replyTo.toString().startsWith("topic://")) {
            replyTo = SimpleString.of(replyTo.toString().substring(8));
         } else if (replyTo.toString().startsWith("temp-queue://") || replyTo.toString().startsWith("temp-topic://")) {
            replyTo = SimpleString.of(replyTo.toString().substring(13));
         }
         reply.setAddress(replyTo);

         doSend(tx, reply, null, direct, null, false, routingContext);
      }
      return RoutingStatus.OK;
   }

   private void doRollback(final boolean clientFailed,
                           final boolean lastMessageAsDelived,
                           final Transaction theTx) throws Exception {
      boolean wasStarted = started;

      List<MessageReference> toCancel = new ArrayList<>();

      for (ServerConsumer consumer : consumers.values()) {
         if (wasStarted) {
            consumer.setStarted(false);
         }

         toCancel.addAll(consumer.cancelRefs(clientFailed, lastMessageAsDelived, theTx));
      }

      //we need to check this before we cancel the refs and add them to the tx, any delivering refs will have been delivered
      //after the last tx was rolled back so we should handle them separately. if not they
      //will end up added to the tx but never ever handled even tho they were removed from the consumers delivering refs.
      //we add them to a new tx and roll them back as the calling client will assume that this has happened.
      if (theTx.getState() == State.ROLLEDBACK) {
         Transaction newTX = newTransaction();
         cancelAndRollback(clientFailed, newTX, wasStarted, toCancel);
      } else {
         cancelAndRollback(clientFailed, theTx, wasStarted, toCancel);
      }
   }

   private void cancelAndRollback(boolean clientFailed,
                                  Transaction theTx,
                                  boolean wasStarted,
                                  List<MessageReference> toCancel) throws Exception {
      for (MessageReference ref : toCancel) {
         ref.getQueue().cancel(theTx, ref);
      }
      //if we failed don't restart as an attempt to deliver messages may be made before we actually close the consumer
      if (wasStarted && !clientFailed) {
         theTx.addOperation(new TransactionOperationAbstract() {

            @Override
            public void afterRollback(Transaction tx) {
               for (ServerConsumer consumer : consumers.values()) {
                  consumer.setStarted(true);
               }
            }

         });
      }

      theTx.rollback();
   }


   @Override
   public synchronized RoutingStatus doSend(final Transaction tx,
                                            final Message msg,
                                            final SimpleString originalAddress,
                                            final boolean direct,
                                            final String senderName,
                                            final boolean noAutoCreateQueue) throws Exception {
      return doSend(tx, msg, originalAddress, direct, senderName, noAutoCreateQueue, routingContext);
   }

   @Override
   public synchronized RoutingStatus doSend(final Transaction tx,
                                            final Message msg,
                                            final SimpleString originalAddress,
                                            final boolean direct,
                                            final String senderName,
                                            final boolean noAutoCreateQueue,
                                            final RoutingContext routingContext) throws Exception {
      return doSend(tx, msg.getRoutingType(), msg, originalAddress, direct, senderName, noAutoCreateQueue, routingContext);
   }

   @Override
   public synchronized RoutingStatus doSend(final Transaction tx,
                                            final RoutingType routingType,
                                            final Message message,
                                            final SimpleString originalAddress,
                                            final boolean direct,
                                            final String senderName,
                                            final boolean noAutoCreateQueue,
                                            final RoutingContext routingContext) throws Exception {

      RoutingStatus result = RoutingStatus.OK;

      final AddressInfo targetFromMessage = new AddressInfo(message.getAddressSimpleString(), routingType);
      final AddressInfo art = getAddressAndRoutingType(targetFromMessage);

      if (art != targetFromMessage) {
         // remove the prefix from the message, with the address model change, only non prefixed addresses exist on the broker
         message.setAddress(art.getName());
      }

      final SimpleString messageAddress = message.getAddressSimpleString();

      // check the user has write access to this address (and potentially queue).
      try {
         securityCheck(CompositeAddress.extractAddressName(messageAddress), CompositeAddress.isFullyQualified(messageAddress) ? CompositeAddress.extractQueueName(messageAddress) : null, CheckType.SEND, this);
      } catch (ActiveMQException e) {
         if (!autoCommitSends && tx != null) {
            tx.markAsRollbackOnly(e);
         }
         throw e;
      }

      if (server.getConfiguration().isPopulateValidatedUser() && validatedUser != null) {
         message.setValidatedUserID(validatedUser);
      }

      if (server.getConfiguration().isRejectEmptyValidatedUser() && message.getValidatedUserID() == null) {
         throw ActiveMQMessageBundle.BUNDLE.rejectEmptyValidatedUser();
      }

      if (server.getAddressSettingsRepository().getMatch(message.getAddress()).isEnableIngressTimestamp()) {
         message.setIngressTimestamp();
         message.reencode();
      }

      if (tx == null || autoCommitSends) {
         routingContext.setTransaction(null);
      } else {
         routingContext.setTransaction(tx);
      }

      try {
         routingContext.setAddress(art.getName());
         routingContext.setRoutingType(art.getRoutingType());

         // Retrieve message size for metrics update before routing,
         // since large message backing files may be closed once routing completes
         int mSize = message instanceof LargeServerMessageImpl lsmi ? lsmi.getBodyBufferSize() : message.getEncodeSize();

         result = postOffice.route(message, routingContext, direct);

         logger.debug("Routing result for {} = {}", message, result);

         updateProducerMetrics(message, senderName, mSize);
      } finally {
         if (!routingContext.isReusable()) {
            routingContext.clear();
         }
      }

      return result;
   }

   @Override
   public List<MessageReference> getInTXMessagesForConsumer(long consumerId) {
      if (this.tx != null) {
         RefsOperation oper = (RefsOperation) tx.getProperty(TransactionPropertyIndexes.REFS_OPERATION);

         if (oper == null) {
            return Collections.emptyList();
         } else {
            return oper.getListOnConsumer(consumerId);
         }
      } else {
         //amqp handles the transaction in callback
         if (callback != null) {
            Transaction transaction = callback.getCurrentTransaction();
            if (transaction != null) {
               RefsOperation operation = (RefsOperation) transaction.getProperty(TransactionPropertyIndexes.REFS_OPERATION);
               if (operation != null) {
                  return operation.getListOnConsumer(consumerId);
               }
            }
         }
         return Collections.emptyList();
      }
   }

   @Override
   public List<MessageReference> getInTxLingerMessages() {
      Transaction transaction = tx;
      if (transaction == null && callback != null) {
         transaction = callback.getCurrentTransaction();
      }
      RefsOperation operation = transaction == null ? null : (RefsOperation) transaction.getProperty(TransactionPropertyIndexes.REFS_OPERATION);

      return operation == null ? null : operation.getLingerMessages();
   }

   @Override
   public void addLingerConsumer(ServerConsumer consumer) {
      Transaction transaction = tx;
      if (transaction == null && callback != null) {
         transaction = callback.getCurrentTransaction();
      }
      if (transaction != null) {
         synchronized (transaction) {
            // Transaction might be committed/rolledback, we need to synchronize and judge state
            if (transaction.getState() != State.COMMITTED && transaction.getState() != State.ROLLEDBACK) {
               RefsOperation operation = (RefsOperation) transaction.getProperty(TransactionPropertyIndexes.REFS_OPERATION);
               List<MessageReference> refs = operation == null ? null : operation.getListOnConsumer(consumer.getID());
               if (refs != null && !refs.isEmpty()) {
                  for (MessageReference ref : refs) {
                     ref.emptyConsumerID();
                  }
                  operation.setLingerSession(name);
                  consumer.getQueue().addLingerSession(name);
               }
            }
         }
      }
   }

   @Override
   public SimpleString removePrefix(SimpleString address) {
      if (prefixEnabled && address != null) {
         return PrefixUtil.getAddress(address, prefixes);
      }
      return address;
   }

   @Override
   public SimpleString getPrefix(SimpleString address) {
      if (prefixEnabled && address != null) {
         return PrefixUtil.getPrefix(address, prefixes);
      }
      return null;
   }

   @Override
   public AddressInfo getAddressAndRoutingType(AddressInfo addressInfo) {
      if (prefixEnabled) {
         return addressInfo.getAddressAndRoutingType(prefixes);
      }
      return addressInfo;
   }

   @Override
   public RoutingType getRoutingTypeFromPrefix(SimpleString address, RoutingType defaultRoutingType) {
      if (prefixEnabled) {
         for (Map.Entry<SimpleString, RoutingType> entry : prefixes.entrySet()) {
            if (address.startsWith(entry.getKey())) {
               return entry.getValue();
            }
         }
      }
      return defaultRoutingType;
   }

   @Override
   public Pair<SimpleString, EnumSet<RoutingType>> getAddressAndRoutingTypes(SimpleString address,
                                                                         EnumSet<RoutingType> defaultRoutingTypes) {
      if (prefixEnabled) {
         return PrefixUtil.getAddressAndRoutingTypes(address, defaultRoutingTypes, prefixes);
      }
      return new Pair<>(address, defaultRoutingTypes);
   }

   @Override
   public void addProducer(String name, String protocol, String address) {
      ServerProducer producer = new ServerProducerImpl(name, protocol, Objects.requireNonNullElse(address, ServerProducer.ANONYMOUS));
      producer.setSessionID(getName());
      producer.setConnectionID(Objects.toString(getConnectionID(), null));
      serverProducers.put(name, producer);
   }

   @Override
   public void removeProducer(String ID) {
      serverProducers.remove(ID);
   }

   @Override
   public Collection<ServerProducer>   getServerProducers() {
      return serverProducers.getServerProducers();
   }

   @Override
   public String getDefaultAddress() {
      return Objects.toString(defaultAddress, null);
   }

   @Override
   public int getConsumerCount() {
      return getServerConsumers().size();
   }

   @Override
   public int getProducerCount() {
      return getServerProducers().size();
   }

   @Override
   public int getDefaultConsumerWindowSize(SimpleString address) {
      AddressSettings as = server.getAddressSettingsRepository().getMatch(address.toString());
      return as.getDefaultConsumerWindowSize();
   }

   @Override
   public String toManagementString() {
      return "ServerSession [id=" + getConnectionID() + ":" + getName() + "]";
   }

   private void updateProducerMetrics(Message msg, String senderName, int mSize) {
      ServerProducer serverProducer = serverProducers.getServerProducer(senderName, msg, this);
      if (serverProducer != null) {
         serverProducer.updateMetrics(msg.getUserID(), mSize);
      }
   }


}
