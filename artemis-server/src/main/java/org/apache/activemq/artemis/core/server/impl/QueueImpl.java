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

import javax.security.auth.Subject;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQNullRefException;
import org.apache.activemq.artemis.api.core.ActiveMQQueueExistsException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RefCountMessage;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.management.CoreNotificationType;
import org.apache.activemq.artemis.api.core.management.ManagementHelper;
import org.apache.activemq.artemis.core.PriorityAware;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.filter.impl.FilterImpl;
import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.paging.cursor.PageIterator;
import org.apache.activemq.artemis.core.paging.cursor.PagePosition;
import org.apache.activemq.artemis.core.paging.cursor.PageSubscription;
import org.apache.activemq.artemis.core.paging.cursor.PagedReference;
import org.apache.activemq.artemis.core.persistence.AddressQueueStatus;
import org.apache.activemq.artemis.core.persistence.OperationContext;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.LargeServerMessageImpl;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.Bindings;
import org.apache.activemq.artemis.core.postoffice.DuplicateIDCache;
import org.apache.activemq.artemis.core.postoffice.PostOffice;
import org.apache.activemq.artemis.core.postoffice.RoutingStatus;
import org.apache.activemq.artemis.core.postoffice.impl.LocalQueueBinding;
import org.apache.activemq.artemis.core.postoffice.impl.PostOfficeImpl;
import org.apache.activemq.artemis.core.remoting.server.RemotingService;
import org.apache.activemq.artemis.core.server.ActiveMQMessageBundle;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.Consumer;
import org.apache.activemq.artemis.core.server.HandleStatus;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.QueueFactory;
import org.apache.activemq.artemis.core.server.RoutingContext;
import org.apache.activemq.artemis.core.server.RoutingContext.MirrorOption;
import org.apache.activemq.artemis.core.server.ScheduledDeliveryHandler;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.server.cluster.RemoteQueueBinding;
import org.apache.activemq.artemis.core.server.cluster.impl.Redistributor;
import org.apache.activemq.artemis.core.server.management.ManagementService;
import org.apache.activemq.artemis.core.server.management.Notification;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.core.settings.HierarchicalRepositoryChangeListener;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.settings.impl.NamedHierarchicalRepositoryChangeListener;
import org.apache.activemq.artemis.core.settings.impl.SlowConsumerPolicy;
import org.apache.activemq.artemis.core.settings.impl.SlowConsumerThresholdMeasurementUnit;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.core.transaction.TransactionOperationAbstract;
import org.apache.activemq.artemis.core.transaction.TransactionPropertyIndexes;
import org.apache.activemq.artemis.core.transaction.impl.BindingsTransactionImpl;
import org.apache.activemq.artemis.core.transaction.impl.TransactionImpl;
import org.apache.activemq.artemis.logs.AuditLogger;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.security.jaas.UserPrincipal;
import org.apache.activemq.artemis.utils.ArtemisCloseable;
import org.apache.activemq.artemis.utils.BooleanUtil;
import org.apache.activemq.artemis.utils.Env;
import org.apache.activemq.artemis.utils.ReferenceCounter;
import org.apache.activemq.artemis.utils.ReusableLatch;
import org.apache.activemq.artemis.utils.SizeAwareMetric;
import org.apache.activemq.artemis.utils.actors.ArtemisExecutor;
import org.apache.activemq.artemis.utils.collections.ConcurrentHashSet;
import org.apache.activemq.artemis.utils.collections.LinkedListIterator;
import org.apache.activemq.artemis.utils.collections.NodeStoreFactory;
import org.apache.activemq.artemis.utils.collections.PriorityLinkedList;
import org.apache.activemq.artemis.utils.collections.PriorityLinkedListImpl;
import org.apache.activemq.artemis.utils.collections.TypedProperties;
import org.apache.activemq.artemis.utils.critical.CriticalComponentImpl;
import org.jctools.queues.MpscUnboundedArrayQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.activemq.artemis.utils.collections.IterableStream.iterableOf;

/**
 * Implementation of a Queue
 * <p>
 * Completely non blocking between adding to queue and delivering to consumers.
 */
public class QueueImpl extends CriticalComponentImpl implements Queue {

   protected static final int CRITICAL_PATHS = 5;
   protected static final int CRITICAL_PATH_ADD_TAIL = 0;
   protected static final int CRITICAL_PATH_ADD_HEAD = 1;
   protected static final int CRITICAL_DELIVER = 2;
   protected static final int CRITICAL_CONSUMER = 3;
   protected static final int CRITICAL_CHECK_DEPAGE = 4;

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
   private static final AtomicIntegerFieldUpdater<QueueImpl> dispatchingUpdater = AtomicIntegerFieldUpdater.newUpdater(QueueImpl.class, "dispatching");
   private static final AtomicLongFieldUpdater<QueueImpl> dispatchStartTimeUpdater = AtomicLongFieldUpdater.newUpdater(QueueImpl.class, "dispatchStartTime");
   private static final AtomicLongFieldUpdater<QueueImpl> consumerRemovedTimestampUpdater = AtomicLongFieldUpdater.newUpdater(QueueImpl.class, "consumerRemovedTimestamp");
   private static final AtomicReferenceFieldUpdater<QueueImpl, Filter> filterUpdater = AtomicReferenceFieldUpdater.newUpdater(QueueImpl.class, Filter.class, "filter");

   public static final int NUM_PRIORITIES = 10;

   public static final int MAX_DELIVERIES_IN_LOOP = 1000;

   public static final int CHECK_QUEUE_SIZE_PERIOD = 1000;

   /**
    * If The system gets slow for any reason, this is the maximum time a Delivery or or depage executor should be
    * hanging on
    */
   public static final int DELIVERY_TIMEOUT = 1000;

   public static final int DEFAULT_FLUSH_LIMIT = 500;

   private volatile Filter filter;

   private final PostOffice postOffice;

   private volatile boolean queueDestroyed = false;

   // Variable to control if we should print a flow controlled message or not. Once it was flow controlled, we will stop
   // warning until it's cleared once again
   private volatile boolean pageFlowControlled = false;

   private volatile long pageFlowControlledLastLog = 0;

   // It is not expected to have a user really changing this. This is a system property now in case users disagree and
   // find value on changing it. In case there is in fact value on changing it we may consider bringing it as an
   // address-settings in broker.xml
   private static final long PAGE_FLOW_CONTROL_PRINT_INTERVAL = Long.parseLong(System.getProperty("ARTEMIS_PAGE_FLOW_CONTROL_PRINT_INTERVAL", "60000"));

   // Once we delivered messages from paging we need to call asyncDelivery upon acks if we flow control paging, ack more
   // messages will open the space to deliver more messages hence we will need this flag to determine if it was paging
   // before.
   private volatile boolean pageDelivered = false;

   private final PagingStore pagingStore;

   protected final PageSubscription pageSubscription;

   private final ReferenceCounter refCountForConsumers;

   private final PageIterator pageIterator;

   private volatile boolean printErrorExpiring = false;

   private boolean mirrorController;

   private volatile boolean hasUnMatchedPending = false;

   // Messages will first enter intermediateMessageReferences before they are added to messageReferences. This is to
   // avoid locking the queue on the producer
   private final MpscUnboundedArrayQueue<MessageReference> intermediateMessageReferences;

   // This is where messages are stored
   protected final PriorityLinkedList<MessageReference> messageReferences = new PriorityLinkedListImpl<>(QueueImpl.NUM_PRIORITIES, MessageReferenceImpl.getSequenceComparator());

   private NodeStoreFactory<MessageReference> nodeStoreFactory;

   private void checkIDSupplier(NodeStoreFactory<MessageReference> nodeStoreFactory) {
      if (this.nodeStoreFactory == null) {
         this.nodeStoreFactory = nodeStoreFactory;
         messageReferences.setNodeStore(() -> nodeStoreFactory.newNodeStore().setName(String.valueOf(queueConfiguration.getName())));
      }
   }

   // The quantity of pagedReferences on messageReferences priority list
   private final AtomicInteger pagedReferences = new AtomicInteger(0);

   final SizeAwareMetric queueMemorySize = new SizeAwareMetric();

   protected final QueueMessageMetrics pendingMetrics = new QueueMessageMetrics(this, "pending");

   private final QueueMessageMetrics deliveringMetrics = new QueueMessageMetrics(this, "delivering");

   protected final ScheduledDeliveryHandler scheduledDeliveryHandler;

   private final AtomicLong messagesAdded = new AtomicLong(0);

   private final AtomicLong messagesAcknowledged = new AtomicLong(0);

   private final AtomicLong ackAttempts = new AtomicLong(0);

   private final AtomicLong messagesExpired = new AtomicLong(0);

   private final AtomicLong messagesKilled = new AtomicLong(0);

   private final AtomicLong messagesReplaced = new AtomicLong(0);

   private boolean paused;

   private long pauseStatusRecord = -1;

   private static final int MAX_SCHEDULED_RUNNERS = 1;
   private static final int MAX_DEPAGE_NUM = MAX_DELIVERIES_IN_LOOP * MAX_SCHEDULED_RUNNERS;

   // We don't ever need more than two DeliverRunner on the executor's list
   // that is getting the worse scenario possible when one runner is almost finishing before the second started
   // for that we keep a counter of scheduled instances
   private final AtomicInteger scheduledRunners = new AtomicInteger(0);

   private final Runnable deliverRunner = new DeliverRunner();

   //This lock is used to prevent deadlocks between direct and async deliveries
   private final ReentrantLock deliverLock = new ReentrantLock();

   private final ReentrantLock depageLock = new ReentrantLock();

   private volatile boolean depagePending = false;

   private final StorageManager storageManager;

   // Instead of looking up the AddressSettings every time, we cache and monitor it through onChange
   private volatile AddressSettings cachedAddressSettings;

   private final ActiveMQServer server;

   private final ScheduledExecutorService scheduledExecutor;

   // redistributor singleton goes in the consumers list
   private ConsumerHolder<Redistributor> redistributor;

   private ScheduledFuture<?> redistributorFuture;

   // This is used by an AtomicFieldUpdater
   private volatile long consumerRemovedTimestamp = -1;

   private final QueueConsumers<ConsumerHolder<? extends Consumer>> consumers = new QueueConsumersImpl<>();

   private MessageGroups<Consumer> groups;

   private volatile Consumer exclusiveConsumer;

   private final ArtemisExecutor executor;

   private volatile long lastDirectDeliveryCheck = 0;

   private volatile boolean directDeliver = true;

   private volatile boolean supportsDirectDeliver = false;

   private HierarchicalRepository<AddressSettings> addressSettingsRepository;

   private HierarchicalRepositoryChangeListener addressSettingsRepositoryListener;

   private final ReusableLatch deliveriesInTransit = new ReusableLatch(0);

   private final AtomicLong queueRateCheckTime = new AtomicLong(System.currentTimeMillis());

   private final AtomicLong messagesAddedSnapshot = new AtomicLong(0);

   private final AtomicLong queueSequence = new AtomicLong(0);

   private ScheduledFuture slowConsumerReaperFuture;

   private SlowConsumerReaperRunnable slowConsumerReaperRunnable;

   private final AddressInfo addressInfo;

   private final QueueFactory factory;

   public volatile int dispatching = 0;

   public volatile long dispatchStartTime = -1;

   private volatile boolean swept;

   private volatile long createdTimestamp = -1;

   // should it print logging in case no messages are routed
   private volatile boolean noRouteLogging = true;

   private final int initialQueueBufferSize;

   protected final QueueConfiguration queueConfiguration;

   @Override
   public boolean isSwept() {
      return swept;
   }

   @Override
   public void setSwept(boolean swept) {
      this.swept = swept;
   }

   /**
    * This is to avoid multi-thread races on calculating direct delivery, to guarantee ordering will be always be
    * correct
    */
   private final Object directDeliveryGuard = new Object();

   private final ConcurrentHashSet<String> lingerSessionIds = new ConcurrentHashSet<>();

   public String debug() {
      StringWriter str = new StringWriter();
      PrintWriter out = new PrintWriter(str);

      out.println("queueMemorySize=" + queueMemorySize);

      for (ConsumerHolder holder : consumers) {
         out.println("consumer: " + holder.consumer.debug());
      }

      out.println("Intermediate reference size is " + intermediateMessageReferences.size());

      boolean foundRef = false;

      synchronized (this) {
         try (LinkedListIterator<MessageReference> iter = messageReferences.iterator()) {
            while (iter.hasNext()) {
               foundRef = true;
               out.println("reference = " + iter.next());
            }
         }
      }

      if (!foundRef) {
         out.println("No permanent references on queue");
      }

      System.out.println(str.toString());

      return str.toString();
   }

   public QueueImpl(final QueueConfiguration queueConfiguration,
                    final Filter filter,
                    final PagingStore pagingStore,
                    final PageSubscription pageSubscription,
                    final ScheduledExecutorService scheduledExecutor,
                    final PostOffice postOffice,
                    final StorageManager storageManager,
                    final HierarchicalRepository<AddressSettings> addressSettingsRepository,
                    final ArtemisExecutor executor,
                    final ActiveMQServer server,
                    final QueueFactory factory) {
      super(server.getCriticalAnalyzer(), CRITICAL_PATHS);

      this.createdTimestamp = System.currentTimeMillis();

      this.queueConfiguration = QueueConfiguration.of(queueConfiguration);

      this.mirrorController = queueConfiguration.isMirrorQueue();

      QueueConfigurationUtils.applyStaticDefaults(this.queueConfiguration);

      this.refCountForConsumers = this.queueConfiguration.isTransient() ? new TransientQueueManagerImpl(server, this.queueConfiguration.getName()) : new QueueManagerImpl(server, this.queueConfiguration.getName());

      this.addressInfo = postOffice == null ? null : postOffice.getAddressInfo(this.queueConfiguration.getAddress());

      try {
         this.filter = filter == null ? FilterImpl.createFilter(this.queueConfiguration.getFilterString()) : filter;
      } catch (ActiveMQException e) {
         throw new RuntimeException(e);
      }

      this.pagingStore = pagingStore;

      this.pageSubscription = pageSubscription;

      this.groups = groupMap(this.queueConfiguration.getGroupBuckets());

      this.postOffice = postOffice;

      this.storageManager = storageManager;

      this.scheduledExecutor = scheduledExecutor;

      this.server = server;

      scheduledDeliveryHandler = new ScheduledDeliveryHandlerImpl(scheduledExecutor, this);

      if (addressSettingsRepository != null) {
         this.addressSettingsRepository = addressSettingsRepository;
         addressSettingsRepositoryListener = new NamedHierarchicalRepositoryChangeListener(queueConfiguration.getName()) {
            @Override
            public void onChange() {
               cachedAddressSettings = QueueImpl.this.addressSettingsRepository.getMatch(queueConfiguration.getAddress().toString());
               checkDeadLetterAddressAndExpiryAddress();
               configureSlowConsumerReaper();
            }
         };
         this.addressSettingsRepository.registerListener(addressSettingsRepositoryListener);
         this.cachedAddressSettings = addressSettingsRepository.getMatch(queueConfiguration.getAddress().toString());
      } else {
         this.cachedAddressSettings = new AddressSettings();
      }

      if (pageSubscription != null) {
         pageSubscription.setQueue(this);
         this.pageIterator = pageSubscription.iterator();
      } else {
         this.pageIterator = null;
      }

      this.executor = executor;

      this.factory = factory;

      if (this.addressInfo != null && this.addressInfo.isPaused()) {
         this.pause(false);
      }

      this.initialQueueBufferSize = this.cachedAddressSettings.getInitialQueueBufferSize() == null
         ? ActiveMQDefaultConfiguration.INITIAL_QUEUE_BUFFER_SIZE
         : this.cachedAddressSettings.getInitialQueueBufferSize();
      this.intermediateMessageReferences = new MpscUnboundedArrayQueue<>(initialQueueBufferSize);

      verifyDisabledConfiguration();
   }

   private void verifyDisabledConfiguration() {
      if (noRouteLogging && !this.queueConfiguration.isEnabled()) {
         ActiveMQServerLogger.LOGGER.noRouteMessagesWillBeDropped(this.getAddress(), this.getName());
      }
   }


   @Override
   public boolean allowsReferenceCallback() {
      // non destructive queues will reuse the same reference between multiple consumers
      // so you cannot really use the callback from the MessageReference
      return !queueConfiguration.isNonDestructive();
   }

   @Override
   public boolean isMirrorController() {
      return mirrorController;
   }

   @Override
   public void setMirrorController(boolean mirrorController) {
      this.mirrorController = mirrorController;
   }

   public SimpleString getRoutingName() {
      return queueConfiguration.getName();
   }

   public SimpleString getUniqueName() {
      return queueConfiguration.getName();
   }

   @Override
   public SimpleString getUser() {
      return queueConfiguration.getUser();
   }

   @Override
   public void setUser(SimpleString user) {
      queueConfiguration.setUser(user);
   }

   @Override
   public boolean isExclusive() {
      return queueConfiguration.isExclusive();
   }

   @Override
   public synchronized void setExclusive(boolean exclusive) {
      queueConfiguration.setExclusive(exclusive);
      if (!exclusive) {
         exclusiveConsumer = null;
      }
   }

   @Override
   public int getConsumersBeforeDispatch() {
      return queueConfiguration.getConsumersBeforeDispatch();
   }

   @Override
   public synchronized void setConsumersBeforeDispatch(int consumersBeforeDispatch) {
      queueConfiguration.setConsumersBeforeDispatch(consumersBeforeDispatch);
   }

   @Override
   public long getDelayBeforeDispatch() {
      return queueConfiguration.getDelayBeforeDispatch();
   }

   @Override
   public synchronized void setDelayBeforeDispatch(long delayBeforeDispatch) {
      queueConfiguration.setDelayBeforeDispatch(delayBeforeDispatch);
   }

   @Override
   public long getDispatchStartTime() {
      return dispatchStartTimeUpdater.get(this);
   }

   @Override
   public boolean isDispatching() {
      return BooleanUtil.toBoolean(dispatchingUpdater.get(this));
   }

   @Override
   public synchronized void setDispatching(boolean dispatching) {
      if (dispatchingUpdater.compareAndSet(this, BooleanUtil.toInt(!dispatching), BooleanUtil.toInt(dispatching))) {
         if (dispatching) {
            dispatchStartTimeUpdater.set(this, System.currentTimeMillis());
         } else {
            dispatchStartTimeUpdater.set(this, -1);
         }
      }
   }


   @Override
   public boolean isLastValue() {
      return queueConfiguration.isLastValue();
   }

   @Override
   public SimpleString getLastValueKey() {
      return queueConfiguration.getLastValueKey();
   }

   @Override
   public boolean isNonDestructive() {
      return queueConfiguration.isNonDestructive();
   }

   @Override
   public synchronized void setNonDestructive(boolean nonDestructive) {
      queueConfiguration.setNonDestructive(nonDestructive);
   }

   @Override
   public void route(final Message message, final RoutingContext context) throws Exception {
      if (!queueConfiguration.isEnabled()) {
         if (noRouteLogging) {
            noRouteLogging = false;
            ActiveMQServerLogger.LOGGER.noRouteDisabledQueue(this.getAddress(), this.getName());
         }
         context.setReusable(false);
         return;
      }
      if (queueConfiguration.isPurgeOnNoConsumers()) {
         context.setReusable(false);
         if (getConsumerCount() == 0) {
            if (noRouteLogging) {
               noRouteLogging = false;
               ActiveMQServerLogger.LOGGER.noRouteNoConsumers(this.getAddress(), this.getName());
            }
            return;
         }
      }

      noRouteLogging = true;

      context.addQueue(queueConfiguration.getAddress(), this);
   }

   @Override
   public void routeWithAck(Message message, RoutingContext context) {
      context.addQueueWithAck(queueConfiguration.getAddress(), this);
   }

   // Queue implementation ----------------------------------------------------------------------------------------
   @Override
   public ReferenceCounter getConsumersRefCount() {
      return refCountForConsumers;
   }

   @Override
   public boolean isDurable() {
      return queueConfiguration.isDurable();
   }

   @Override
   public boolean isDurableMessage() {
      return isDurable() && isPurgeOnNoConsumers();
   }

   @Override
   public boolean isAutoDelete() {
      return queueConfiguration.isAutoDelete();
   }

   @Override
   public long getAutoDeleteDelay() {
      return queueConfiguration.getAutoDeleteDelay();
   }

   @Override
   public long getAutoDeleteMessageCount() {
      return queueConfiguration.getAutoDeleteMessageCount();
   }

   @Override
   public boolean isTemporary() {
      return queueConfiguration.isTemporary();
   }

   @Override
   public boolean isAutoCreated() {
      return queueConfiguration.isAutoCreated();
   }

   @Override
   public boolean isPurgeOnNoConsumers() {
      return queueConfiguration.isPurgeOnNoConsumers();
   }

   @Override
   public synchronized void setPurgeOnNoConsumers(boolean value) {
      queueConfiguration.setPurgeOnNoConsumers(value);
   }

   @Override
   public boolean isEnabled() {
      return queueConfiguration.isEnabled();
   }

   @Override
   public synchronized void setEnabled(boolean value) {
      queueConfiguration.setEnabled(value);
      verifyDisabledConfiguration();
   }

   @Override
   public int getMaxConsumers() {
      return queueConfiguration.getMaxConsumers();
   }

   @Override
   public synchronized void setMaxConsumer(int maxConsumers) {
      queueConfiguration.setMaxConsumers(maxConsumers);
   }

   @Override
   public int getGroupBuckets() {
      return queueConfiguration.getGroupBuckets();
   }

   @Override
   public synchronized void setGroupBuckets(int groupBuckets) {
      if (this.queueConfiguration.getGroupBuckets() != groupBuckets) {
         this.groups = groupMap(groupBuckets);
         this.queueConfiguration.setGroupBuckets(groupBuckets);
      }
   }

   @Override
   public boolean isGroupRebalance() {
      return queueConfiguration.isGroupRebalance();
   }

   @Override
   public synchronized void setGroupRebalance(boolean groupRebalance) {
      queueConfiguration.setGroupRebalance(groupRebalance);
   }

   @Override
   public boolean isGroupRebalancePauseDispatch() {
      return queueConfiguration.isGroupRebalancePauseDispatch();
   }

   @Override
   public synchronized void setGroupRebalancePauseDispatch(boolean groupRebalancePauseDispatch) {
      queueConfiguration.setGroupRebalancePauseDispatch(groupRebalancePauseDispatch);
   }

   @Override
   public SimpleString getGroupFirstKey() {
      return queueConfiguration.getGroupFirstKey();
   }

   @Override
   public synchronized void setGroupFirstKey(SimpleString groupFirstKey) {
      queueConfiguration.setGroupFirstKey(groupFirstKey);
   }


   @Override
   public boolean isConfigurationManaged() {
      return queueConfiguration.isConfigurationManaged();
   }

   @Override
   public synchronized void setConfigurationManaged(boolean configurationManaged) {
      queueConfiguration.setConfigurationManaged(configurationManaged);
   }

   @Override
   public SimpleString getName() {
      return queueConfiguration.getName();
   }

   @Override
   public SimpleString getAddress() {
      return queueConfiguration.getAddress();
   }

   @Override
   public Long getID() {
      return queueConfiguration.getId();
   }


   @Override
   public int durableUp(Message message) {
      return message.durableUp();
   }

   @Override
   public int durableDown(Message message) {
      return message.durableDown();
   }

   @Override
   public void refUp(MessageReference messageReference) {
      int count = messageReference.getMessage().refUp();
      PagingStore owner = (PagingStore) messageReference.getMessage().getOwner();
      if (count == 1) {
         if (owner != null) {
            owner.addSize(messageReference.getMessageMemoryEstimate(), false);
            messageReference.getMessage().routed();
         }
      }
      if (pagingStore != null) {
         if (isMirrorController() && owner != null && pagingStore != owner) {
            // When using mirror in this situation, it means the address belong to another queue
            // it's acting as if the message is being copied
            pagingStore.addSize(messageReference.getMessage().getOriginalEstimate(), false, false);
         }

         pagingStore.refUp(messageReference.getMessage(), count);
      }
   }

   @Override
   public void refDown(MessageReference messageReference) {
      int count = messageReference.getMessage().refDown();
      PagingStore owner = (PagingStore) messageReference.getMessage().getOwner();
      if (count == 0 && owner != null) {
         owner.addSize(-messageReference.getMessageMemoryEstimate(), false);
      }
      if (pagingStore != null) {
         if (isMirrorController() && owner != null && pagingStore != owner) {
            // When using mirror in this situation, it means the address belong to another queue
            // it's acting as if the message is being copied
            pagingStore.addSize(-messageReference.getMessage().getOriginalEstimate(), false, false);
         }
         pagingStore.refDown(messageReference.getMessage(), count);
      }
   }

   @Override
   public PagingStore getPagingStore() {
      return pagingStore;
   }

   @Override
   public PageSubscription getPageSubscription() {
      return pageSubscription;
   }

   @Override
   public RoutingType getRoutingType() {
      return queueConfiguration.getRoutingType();
   }

   @Override
   public int getInitialQueueBufferSize() {
      return this.initialQueueBufferSize;
   }

   @Override
   public void setRoutingType(RoutingType routingType) {
      if (addressInfo.getRoutingTypes().contains(routingType)) {
         queueConfiguration.setRoutingType(routingType);
      }
   }

   @Override
   public Filter getFilter() {
      return filterUpdater.get(this);
   }

   @Override
   public void setFilter(Filter filter) {
      filterUpdater.set(this, filter);
   }

   @Override
   public void unproposed(final SimpleString groupID) {
      if (groupID.toString().endsWith("." + this.getName())) {
         // this means this unproposed belongs to this routing, so we will
         // remove this group

         // This is removing the name and a . added, giving us the original groupID used
         // this is because a groupID is stored per queue, and only this queue is expiring at this point
         final SimpleString groupIDToRemove = (SimpleString) groupID.subSequence(0, groupID.length() - getName().length() - 1);
         // using an executor so we don't want to hold anyone just because of this
         getExecutor().execute(() -> {
            synchronized (QueueImpl.this) {
               if (groups.remove(groupIDToRemove) != null) {
                  logger.debug("Removing group after unproposal {} from queue {}", groupID, QueueImpl.this);
               } else {
                  logger.debug("Couldn't remove Removing group {} after unproposal on queue {}", groupIDToRemove, QueueImpl.this);
               }
            }
         });
      }
   }

   @Override
   public void addHead(final MessageReference ref, boolean scheduling) {
      if (logger.isTraceEnabled()) {
         logger.trace("AddHead, size = {}, intermediate size = {}, references size = {}\nreference={}", queueMemorySize, intermediateMessageReferences.size(), messageReferences.size(), ref);
      }

      try (ArtemisCloseable metric = measureCritical(CRITICAL_PATH_ADD_HEAD)) {
         synchronized (this) {
            if (queueConfiguration.getRingSize() != -1) {
               enforceRing(ref, scheduling, true);
            }

            if (!ref.isAlreadyAcked()) {
               if (!scheduling && scheduledDeliveryHandler.checkAndSchedule(ref, false)) {
                  return;
               }

               internalAddHead(ref);

               directDeliver = false;
            }
         }
      }
   }

   @Override
   public void addSorted(final MessageReference ref, boolean scheduling) {
      if (logger.isTraceEnabled()) {
         logger.trace("addSorted, size = {}, intermediate size = {}, references size = {}\nreference={}", queueMemorySize, intermediateMessageReferences.size(), messageReferences.size(), ref);
      }

      try (ArtemisCloseable metric = measureCritical(CRITICAL_PATH_ADD_HEAD)) {
         synchronized (QueueImpl.this) {
            if (queueConfiguration.getRingSize() != -1) {
               enforceRing(ref, false, true);
            }

            if (!ref.isAlreadyAcked()) {
               if (!scheduling && scheduledDeliveryHandler.checkAndSchedule(ref, false)) {
                  return;
               }
               internalAddSorted(ref);

               directDeliver = false;
            }
         }
      }
   }

   @Override
   public void addHead(final List<MessageReference> refs, boolean scheduling) {
      try (ArtemisCloseable metric = measureCritical(CRITICAL_PATH_ADD_HEAD)) {
         synchronized (this) {
            for (MessageReference ref : refs) {
               addHead(ref, scheduling);
            }

            resetAllIterators();

            deliverAsync();
         }
      }
   }

   @Override
   public void addSorted(final List<MessageReference> refs, boolean scheduling) {
      if (refs.size() > MAX_DELIVERIES_IN_LOOP) {
         logger.debug("Switching addSorted call to addSortedLargeTX on queue {}", queueConfiguration.getName());
         addSortedLargeTX(refs, scheduling);
         return;
      }
      try (ArtemisCloseable metric = measureCritical(CRITICAL_PATH_ADD_HEAD)) {
         synchronized (this) {
            for (MessageReference ref : refs) {
               addSorted(ref, scheduling);
            }

            resetAllIterators();

            deliverAsync();
         }
      }
   }

   // Perhaps we could just replace addSorted by addSortedLargeTX
   // However I am not 100% confident we could always resetAllIterators
   // we certainly can in the case of a rollback in a huge TX.
   // so I am just playing safe and keeping the original semantic for small transactions.
   private void addSortedLargeTX(final List<MessageReference> refs, boolean scheduling) {
      for (MessageReference ref : refs) {
         // When dealing with large transactions, we are not holding a synchronization lock here.
         // addSorted will lock for each individual adds
         addSorted(ref, scheduling);
      }

      if (logger.isDebugEnabled()) {
         logger.debug("addSortedHugeLoad finished on queue {}", queueConfiguration.getName());
      }

      synchronized (this) {

         resetAllIterators();

         deliverAsync();
      }
   }

   @Override
   public synchronized void reload(final MessageReference ref) {
      queueMemorySize.addSize(ref.getMessageMemoryEstimate());
      if (!scheduledDeliveryHandler.checkAndSchedule(ref, true)) {
         internalAddTail(ref);
      }

      directDeliver = false;

      if (!ref.isPaged()) {
         incrementMesssagesAdded();
      }
   }

   @Override
   public void reloadSequence(final MessageReference ref) {
      ref.setSequence(queueSequence.incrementAndGet());
   }

   @Override
   public void addTail(final MessageReference ref) {
      addTail(ref, false);
   }

   @Override
   public void flushOnIntermediate(Runnable runnable) {
      intermediateMessageReferences.add(new MessageReferenceImpl() {
         @Override
         public boolean skipDelivery() {
            runnable.run();
            return true;
         }
      });
      deliverAsync();
   }

   @Override
   public void addTail(final MessageReference ref, final boolean direct) {
      try (ArtemisCloseable metric = measureCritical(CRITICAL_PATH_ADD_TAIL)) {
         if (scheduleIfPossible(ref)) {
            return;
         }
         if (RefCountMessage.isRefTraceEnabled()) {
            RefCountMessage.deferredDebug(ref.getMessage(), "add tail queue {}", this.getName());
         }

         if (direct && supportsDirectDeliver && !directDeliver && System.currentTimeMillis() - lastDirectDeliveryCheck > CHECK_QUEUE_SIZE_PERIOD) {
            logger.trace("Checking to re-enable direct deliver on queue {}", queueConfiguration.getName());

            lastDirectDeliveryCheck = System.currentTimeMillis();
            synchronized (directDeliveryGuard) {
               // The checkDirect flag is periodically set to true, if the delivery is specified as direct then this causes the
               // directDeliver flag to be re-computed resulting in direct delivery if the queue is empty
               // We don't recompute it on every delivery since executing isEmpty is expensive for a ConcurrentQueue

               if (deliveriesInTransit.getCount() == 0 && getExecutor().isFlushed() &&
                  intermediateMessageReferences.isEmpty() && messageReferences.isEmpty() &&
                  pageIterator != null && !pageIterator.hasNext() &&
                  pageSubscription != null && !pageSubscription.isStorePaging()) {
                  // We must block on the executor to ensure any async deliveries have completed or we might get out of order
                  // deliveries
                  // Go into direct delivery mode
                  directDeliver = supportsDirectDeliver;
                  if (logger.isTraceEnabled()) {
                     logger.trace("Setting direct deliverer to {} on queue {}", supportsDirectDeliver, queueConfiguration.getName());
                  }
               } else {
                  logger.trace("Couldn't set direct deliver back on queue {}", queueConfiguration.getName());
               }
            }
         }

         if (direct && supportsDirectDeliver && directDeliver && deliveriesInTransit.getCount() == 0 && deliverDirect(ref)) {
            return;
         }

         // We only add queueMemorySize if not being delivered directly
         queueMemorySize.addSize(ref.getMessageMemoryEstimate());

         intermediateMessageReferences.add(ref);

         directDeliver = false;

         // Delivery async will both poll for intermediate reference and deliver to clients
         deliverAsync();
      }
   }

   protected boolean scheduleIfPossible(MessageReference ref) {
      if (scheduledDeliveryHandler.checkAndSchedule(ref, true)) {
         synchronized (this) {
            if (!ref.isPaged()) {
               incrementMesssagesAdded();
            }
         }

         return true;
      }
      return false;
   }

   /**
    * This will wait for any pending deliveries to finish
    */
   private boolean flushDeliveriesInTransit() {
      try {
         if (deliveriesInTransit.await(DELIVERY_TIMEOUT)) {
            return true;
         } else {
            ActiveMQServerLogger.LOGGER.timeoutFlushInTransit(getName().toString(), getAddress().toString());
            return false;
         }
      } catch (Exception e) {
         ActiveMQServerLogger.LOGGER.unableToFlushDeliveries(e);
         return false;
      }
   }

   @Override
   public void forceDelivery() {
      if (pageSubscription != null && pageSubscription.isStorePaging()) {
         logger.trace("Force delivery scheduling depage");
         scheduleDepage(false);
      }

      logger.trace("Force delivery delivering async");

      deliverAsync();
   }

   @Override
   public void deliverAsync() {
      deliverAsync(false);
   }

   private void deliverAsync(boolean noWait) {
      if (scheduledRunners.get() < MAX_SCHEDULED_RUNNERS) {
         scheduledRunners.incrementAndGet();
         try {
            getExecutor().execute(deliverRunner);
         } catch (RejectedExecutionException ignored) {
            // no-op
            scheduledRunners.decrementAndGet();
         }
      }
   }

   @Override
   public void close() throws Exception {
      getExecutor().execute(this::cancelRedistributor);

      addressSettingsRepository.unRegisterListener(addressSettingsRepositoryListener);
   }

   @Override
   public ArtemisExecutor getExecutor() {
      if (pageSubscription != null && pageSubscription.isStorePaging()) {
         // When in page mode, we don't want to have concurrent IO on the same PageStore
         return pageSubscription.getPagingStore().getExecutor();
      } else {
         return executor;
      }
   }

   // Only used on tests
   public void deliverNow() {
      deliverAsync();

      flushExecutor();
   }

   @Override
   public boolean flushExecutor() {
      boolean ok = internalFlushExecutor(10000, true);

      if (!ok) {
         ActiveMQServerLogger.LOGGER.errorFlushingExecutorsOnQueue();
      }

      return ok;
   }

   private boolean internalFlushExecutor(long timeout, boolean log) {

      if (!getExecutor().flush(timeout, TimeUnit.MILLISECONDS)) {
         if (log) {
            ActiveMQServerLogger.LOGGER.queueBusy(this.queueConfiguration.getName().toString(), timeout);
         }
         return false;
      } else {
         return true;
      }
   }

   private boolean canDispatch() {
      boolean canDispatch = BooleanUtil.toBoolean(dispatchingUpdater.get(this));
      if (canDispatch) {
         return true;
      } else {

         //Dont change that we can dispatch until inflight's are handled avoids issues with out of order messages.
         if (inFlightMessages()) {
            return false;
         }

         if (getConsumerCount() >= queueConfiguration.getConsumersBeforeDispatch()) {
            if (dispatchingUpdater.compareAndSet(this, BooleanUtil.toInt(false), BooleanUtil.toInt(true))) {
               dispatchStartTimeUpdater.set(this, System.currentTimeMillis());
            }
            return true;
         }

         long currentDispatchStartTime = dispatchStartTimeUpdater.get(this);
         if (currentDispatchStartTime != -1 && currentDispatchStartTime < System.currentTimeMillis()) {
            dispatchingUpdater.set(this, BooleanUtil.toInt(true));
            return true;
         }
         return false;
      }
   }

   private boolean inFlightMessages() {
      return consumers.stream().mapToInt(c -> c.consumer().getDeliveringMessages().size()).sum() != 0;
   }

   @Override
   public void addConsumer(final Consumer consumer) throws Exception {
      logger.debug("{} adding consumer {}", this, consumer);

      this.setSwept(false);

      try (ArtemisCloseable metric = measureCritical(CRITICAL_CONSUMER)) {
         synchronized (this) {
            if (queueConfiguration.getMaxConsumers() != MAX_CONSUMERS_UNLIMITED && getConsumerCount() >= queueConfiguration.getMaxConsumers()) {
               throw ActiveMQMessageBundle.BUNDLE.maxConsumerLimitReachedForQueue(queueConfiguration.getAddress(), queueConfiguration.getName());
            }

            if (consumers.isEmpty()) {
               this.supportsDirectDeliver = consumer.supportsDirectDelivery();
            } else {
               if (!consumer.supportsDirectDelivery()) {
                  this.supportsDirectDeliver = false;
               }
            }

            cancelRedistributor();

            if (queueConfiguration.isGroupRebalance()) {
               if (queueConfiguration.isGroupRebalancePauseDispatch()) {
                  stopDispatch();
               }
               groups.removeAll();
            }

            ConsumerHolder<Consumer> newConsumerHolder = new ConsumerHolder<>(consumer, this);
            if (consumers.add(newConsumerHolder)) {
               if (queueConfiguration.getDelayBeforeDispatch() >= 0) {
                  dispatchStartTimeUpdater.compareAndSet(this, -1, queueConfiguration.getDelayBeforeDispatch() + System.currentTimeMillis());
               }
               refCountForConsumers.increment();
            }
         }
      }
   }

   @Override
   public void addLingerSession(String sessionId) {
      lingerSessionIds.add(sessionId);
   }

   @Override
   public void removeLingerSession(String sessionId) {
      lingerSessionIds.remove(sessionId);
   }

   @Override
   public void removeConsumer(final Consumer consumer) {
      logger.debug("Removing consumer {}", consumer);

      try (ArtemisCloseable metric = measureCritical(CRITICAL_CONSUMER)) {
         synchronized (QueueImpl.this) {

            boolean consumerRemoved = false;
            for (ConsumerHolder holder : consumers) {
               if (holder.consumer == consumer) {
                  if (holder.iter != null) {
                     holder.iter.close();
                     holder.iter = null;
                  }
                  consumers.remove(holder);
                  consumerRemoved = true;
                  break;
               }
            }

            this.supportsDirectDeliver = checkConsumerDirectDeliver();

            if (consumerRemoved) {
               consumerRemovedTimestampUpdater.set(this, System.currentTimeMillis());
               if (refCountForConsumers.decrement() == 0) {
                  stopDispatch();
               }
            }

            if (consumer == exclusiveConsumer) {

               // await context completion such that any delivered are returned
               // to the queue. Preserve an ordered view for the next exclusive
               // consumer
               storageManager.afterCompleteOperations(new IOCallback() {

                  @Override
                  public void onError(final int errorCode, final String errorMessage) {
                     releaseExclusiveConsumer();
                  }

                  @Override
                  public void done() {
                     releaseExclusiveConsumer();
                  }

               });
            }

            groups.removeIf(consumer::equals);

         }
      }
   }

   private void releaseExclusiveConsumer() {
      synchronized (QueueImpl.this) {
         exclusiveConsumer = null;
         resetAllIterators();
      }
      deliverAsync();
   }

   private void stopDispatch() {
      boolean stopped = dispatchingUpdater.compareAndSet(this, BooleanUtil.toInt(true), BooleanUtil.toInt(false));
      if (stopped) {
         dispatchStartTimeUpdater.set(this, -1);
      }
   }

   private boolean checkConsumerDirectDeliver() {
      if (consumers.isEmpty()) {
         return false;
      }
      boolean supports = true;
      for (ConsumerHolder consumerCheck : consumers) {
         if (!consumerCheck.consumer.supportsDirectDelivery()) {
            supports = false;
         }
      }
      return supports;
   }

   public synchronized Redistributor getRedistributor() {
      return redistributor == null ? null : redistributor.consumer;
   }

   @Override
   public synchronized void addRedistributor(final long delay) {
      if (isInternalQueue()) {
         logger.debug("Queue {} is internal, can't be redistributed!", queueConfiguration.getName());
         return;
      }

      if (queueConfiguration.getAddress().startsWith(server.getConfiguration().getManagementAddress())) {
         logger.debug("Queue {} is a management address, ignoring it for redistribution", queueConfiguration.getAddress());
         return;
      }

      clearRedistributorFuture();

      if (redistributor != null) {
         // Just prompt delivery
         deliverAsync();
         return;
      }

      if (delay > 0) {
         if (consumers.isEmpty() || hasUnMatchedPending) {
            DelayedAddRedistributor dar = new DelayedAddRedistributor(executor);

            redistributorFuture = scheduledExecutor.schedule(dar, delay, TimeUnit.MILLISECONDS);
         }
      } else {
         internalAddRedistributor();
      }
   }

   private void clearRedistributorFuture() {
      ScheduledFuture<?> future = redistributorFuture;
      redistributorFuture = null;
      if (future != null) {
         future.cancel(false);
      }
   }

   @Override
   public synchronized void cancelRedistributor() {
      clearRedistributorFuture();
      hasUnMatchedPending = false;
      if (redistributor != null) {
         try {
            redistributor.consumer.stop();
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.unableToCancelRedistributor(e);
         } finally {
            if (redistributor.iter != null) {
               redistributor.iter.close();
               redistributor.iter = null;
            }
            consumers.remove(redistributor);
            redistributor = null;
         }
      }
   }

   @Override
   public int getConsumerCount() {
      return refCountForConsumers.getCount();
   }

   @Override
   public long getConsumerRemovedTimestamp() {
      return consumerRemovedTimestampUpdater.get(this);
   }

   @Override
   public long getRingSize() {
      return queueConfiguration.getRingSize();
   }

   @Override
   public synchronized void setRingSize(long ringSize) {
      queueConfiguration.setRingSize(ringSize);
   }

   @Override
   public long getCreatedTimestamp() {
      return createdTimestamp;
   }

   @Override
   public long getPendingMessageCount() {
      return (long) pendingMetrics.getMessageCount();
   }

   @Override
   public Set<Consumer> getConsumers() {
      Set<Consumer> consumersSet = new HashSet<>(this.consumers.size());
      for (ConsumerHolder<? extends Consumer> consumerHolder : consumers) {
         consumersSet.add(consumerHolder.consumer);
      }
      return consumersSet;
   }

   @Override
   public synchronized Map<SimpleString, Consumer> getGroups() {
      return groups.toMap();
   }

   @Override
   public synchronized void resetGroup(SimpleString groupId) {
      groups.remove(groupId);
   }

   @Override
   public synchronized void resetAllGroups() {
      groups.removeAll();
   }

   @Override
   public synchronized int getGroupCount() {
      return groups.size();
   }

   @Override
   public boolean hasMatchingConsumer(final Message message) {
      for (ConsumerHolder holder : consumers) {
         Consumer consumer = holder.consumer;

         if (consumer instanceof Redistributor) {
            continue;
         }

         Filter filter1 = consumer.getFilter();

         if (filter1 == null) {
            return true;
         } else {
            if (filter1.match(message)) {
               return true;
            }
         }
      }
      return false;
   }

   @Override
   public LinkedListIterator<MessageReference> iterator() {
      return new SynchronizedIterator(messageReferences.iterator());
   }

   @Override
   public QueueBrowserIterator browserIterator() {
      return new QueueBrowserIterator();
   }

   @Override
   public MessageReference peekFirstMessage() {
      synchronized (this) {
         if (messageReferences != null) {
            return messageReferences.peek();
         }
      }

      return null;
   }

   @Override
   public MessageReference peekFirstScheduledMessage() {
      synchronized (this) {
         if (scheduledDeliveryHandler != null) {
            return scheduledDeliveryHandler.peekFirstScheduledMessage();
         }
      }

      return null;
   }

   @Override
   public synchronized MessageReference removeReferenceWithID(final long id1) throws Exception {
      try (LinkedListIterator<MessageReference> iterator = iterator()) {

         MessageReference removed = null;

         while (iterator.hasNext()) {
            MessageReference ref = iterator.next();

            if (ref.getMessage().getMessageID() == id1) {
               iterator.remove();
               refRemoved(ref);

               removed = ref;

               break;
            }
         }

         if (removed == null) {
            // Look in scheduled deliveries
            removed = scheduledDeliveryHandler.removeReferenceWithID(id1);
         }

         return removed;
      }
   }

   @Override
   public long getMessageCount() {
      if (pageSubscription != null) {
         // messageReferences will have depaged messages which we need to discount from the counter as they are
         // counted on the pageSubscription as well
         long returnValue = (long) pendingMetrics.getNonPagedMessageCount() + scheduledDeliveryHandler.getNonPagedScheduledCount() + deliveringMetrics.getNonPagedMessageCount() + pageSubscription.getMessageCount();
         if (logger.isDebugEnabled()) {
            logger.debug("Queue={}/{} returning getMessageCount \n\treturning {}. \n\tpendingMetrics.getMessageCount() = {}, \n\tgetScheduledCount() = {}, \n\tpageSubscription.getMessageCount()={}, \n\tpageSubscription.getCounter().getValue()={}",
                         queueConfiguration.getName(), queueConfiguration.getId(), returnValue, pendingMetrics.getMessageCount(),  scheduledDeliveryHandler.getNonPagedScheduledCount(), pageSubscription.getMessageCount(), pageSubscription.getCounter().getValue());
         }
         return returnValue;
      } else {
         return (long) pendingMetrics.getMessageCount() + getScheduledCount() + getDeliveringCount();
      }
   }

   @Override
   public long getPersistentSize() {
      if (pageSubscription != null) {
         // messageReferences will have depaged messages which we need to discount from the counter as they are
         // counted on the pageSubscription as well
         return pendingMetrics.getNonPagedPersistentSize() + scheduledDeliveryHandler.getNonPagedScheduledSize() + deliveringMetrics.getNonPagedPersistentSize() + pageSubscription.getPersistentSize();
      } else {
         return pendingMetrics.getPersistentSize() + getScheduledSize() + getDeliveringSize();
      }
   }

   @Override
   public long getDurableMessageCount() {
      if (isDurable()) {
         if (pageSubscription != null) {
            return (long) pendingMetrics.getNonPagedDurableMessageCount() + scheduledDeliveryHandler.getNonPagedDurableScheduledCount() + deliveringMetrics.getNonPagedDurableMessageCount() + pageSubscription.getMessageCount();
         } else {
            return (long) pendingMetrics.getDurableMessageCount() + getDurableScheduledCount() + getDurableDeliveringCount();
         }
      }
      return 0;
   }

   @Override
   public long getDurablePersistentSize() {
      if (isDurable()) {
         if (pageSubscription != null) {
            return pendingMetrics.getDurablePersistentSize() + scheduledDeliveryHandler.getNonPagedDurableScheduledSize() + deliveringMetrics.getNonPagedDurablePersistentSize() + pageSubscription.getPersistentSize();
         } else {
            return pendingMetrics.getDurablePersistentSize() + getDurableScheduledSize() + getDurableDeliveringSize();
         }
      }
      return 0;
   }

   @Override
   public int getScheduledCount() {
      return scheduledDeliveryHandler.getScheduledCount();
   }

   @Override
   public long getScheduledSize() {
      return scheduledDeliveryHandler.getScheduledSize();
   }

   @Override
   public int getDurableScheduledCount() {
      return scheduledDeliveryHandler.getDurableScheduledCount();
   }

   @Override
   public long getDurableScheduledSize() {
      return scheduledDeliveryHandler.getDurableScheduledSize();
   }

   @Override
   public synchronized List<MessageReference> getScheduledMessages() {
      return scheduledDeliveryHandler.getScheduledReferences();
   }

   @Override
   public Map<String, List<MessageReference>> getDeliveringMessages() {
      final Iterator<ConsumerHolder<? extends Consumer>> consumerHolderIterator;
      synchronized (this) {
         consumerHolderIterator = consumers.iterator();
      }

      Map<String, List<MessageReference>> mapReturn = new HashMap<>();

      while (consumerHolderIterator.hasNext()) {
         ConsumerHolder holder = consumerHolderIterator.next();
         List<MessageReference> msgs = holder.consumer.getDeliveringMessages();
         if (msgs != null && !msgs.isEmpty()) {
            mapReturn.put(holder.consumer.toManagementString(), msgs);
         }
      }

      for (String lingerSessionId : lingerSessionIds) {
         ServerSession serverSession = server.getSessionByID(lingerSessionId);
         List<MessageReference> refs = serverSession == null ? null : serverSession.getInTxLingerMessages();
         if (refs != null && !refs.isEmpty()) {
            mapReturn.put(serverSession.toManagementString(), refs);
         }
      }

      return mapReturn;
   }

   @Override
   public int getDeliveringCount() {
      return deliveringMetrics.getMessageCount();
   }

   @Override
   public long getDeliveringSize() {
      return deliveringMetrics.getPersistentSize();
   }

   @Override
   public int getDurableDeliveringCount() {
      return deliveringMetrics.getDurableMessageCount();
   }

   @Override
   public long getDurableDeliveringSize() {
      return deliveringMetrics.getDurablePersistentSize();
   }

   @Override
   public void acknowledge(final MessageReference ref) throws Exception {
      acknowledge(ref, null);
   }

   @Override
   public void acknowledge(final MessageReference ref, final ServerConsumer consumer) throws Exception {
      acknowledge(ref, AckReason.NORMAL, consumer);
   }

   @Override
   public void acknowledge(final MessageReference ref, final AckReason reason, final ServerConsumer consumer) throws Exception {
      acknowledge(null, ref, reason, consumer, true);
   }

   @Override
   public void acknowledge(final Transaction tx, final MessageReference ref) throws Exception {
      acknowledge(tx, ref, AckReason.NORMAL, null, true);
   }

   /**
    * The parameter delivering can be sent as false in situation where the ack is coming outside of the context of
    * delivering. Example: Mirror replication will call the ack here without any consumer involved. On that case no
    * previous delivery happened, hence no information about delivering statistics should be updated.
    */
   @Override
   public void acknowledge(final Transaction tx, final MessageReference ref, final AckReason reason, final ServerConsumer consumer, boolean delivering) throws Exception {
      final boolean transactional = tx != null;
      RefsOperation refsOperation = null;
      if (transactional) {
         refsOperation = getRefsOperation(tx, reason, false, delivering);
      }

      if (logger.isTraceEnabled()) {
         logger.trace("queue.acknowledge serverIdentity={}, queue={} acknowledge tx={} ref={}, reason={}, consumer={}", server.getIdentity(), this.getName(), transactional, ref, reason, consumer);
      }

      postOffice.preAcknowledge(tx, ref, reason);

      if (queueConfiguration.isNonDestructive() && reason == AckReason.NORMAL) {
         if (transactional) {
            refsOperation.addOnlyRefAck(ref);
         } else {
            decDelivering(ref);
         }

         logger.debug("acknowledge tx ignored nonDestructive=true and reason=NORMAL");
      } else {
         if (ref.isPaged()) {
            if (transactional) {
               pageSubscription.ackTx(tx, (PagedReference) ref);
               refsOperation.addAck(ref);
            } else {
               pageSubscription.ack((PagedReference) ref);
               postAcknowledge(ref, reason, delivering);
            }
         } else {
            Message message = ref.getMessage();

            boolean durableRef = message.isDurable() && isDurable();

            if (durableRef) {
               if (transactional) {
                  storageManager.storeAcknowledgeTransactional(tx.getID(), queueConfiguration.getId(), message.getMessageID());
                  tx.setContainsPersistent();
               } else {
                  storageManager.storeAcknowledge(queueConfiguration.getId(), message.getMessageID());
               }
            }
            if (transactional) {
               ackAttempts.incrementAndGet();
               refsOperation.addAck(ref);
            } else {
               postAcknowledge(ref, reason, delivering);
            }
         }

         if (!transactional) {
            ackAttempts.incrementAndGet();
         }

         if (AuditLogger.isMessageLoggingEnabled()) {
            // it's possible for the consumer to be null (e.g. acking the message administratively)
            final ServerSession session = consumer != null ? server.getSessionByID(consumer.getSessionID()) : null;
            final Subject subject;
            final String remoteAddress;
            if (session == null) {
               subject = new Subject();
               subject.getPrincipals().add(new UserPrincipal("system"));
               remoteAddress = "internal";
            } else {
               subject = session.getRemotingConnection().getSubject();
               remoteAddress = session.getRemotingConnection().getRemoteAddress();
            }

            if (transactional) {
               AuditLogger.addAckToTransaction(subject, remoteAddress, getName().toString(), ref.getMessage().toString(), tx.toString());
               tx.addOperation(new TransactionOperationAbstract() {
                  @Override
                  public void afterCommit(Transaction tx) {
                     auditLogAck(subject, remoteAddress, ref, tx);
                  }

                  @Override
                  public void afterRollback(Transaction tx) {
                     AuditLogger.rolledBackTransaction(subject, remoteAddress, tx.toString(), ref.toString());
                  }
               });
            } else {
               auditLogAck(subject, remoteAddress, ref, tx);
            }
         }
         if (server.hasBrokerMessagePlugins()) {
            server.callBrokerMessagePlugins(plugin -> plugin.messageAcknowledged(tx, ref, reason, consumer));
         }
      }
   }

   private void auditLogAck(Subject subject, String remoteAddress, MessageReference ref, Transaction tx) {
      AuditLogger.coreAcknowledgeMessage(subject, remoteAddress, getName().toString(), ref.getMessage().toString(), Objects.toString(tx, null));
   }

   @Override
   public void reacknowledge(final Transaction tx, final MessageReference ref) throws Exception {
      Message message = ref.getMessage();

      if (message.isDurable() && isDurable()) {
         tx.setContainsPersistent();
      }

      getRefsOperation(tx, AckReason.NORMAL).addAck(ref);

      incDelivering(ref);

      messagesAcknowledged.incrementAndGet();
   }

   private RefsOperation getRefsOperation(final Transaction tx, AckReason ackReason) {
      return getRefsOperation(tx, ackReason, false, true);
   }

   private RefsOperation getRefsOperation(final Transaction tx, AckReason ackReason, boolean ignoreRedlieveryCheck, boolean delivering) {
      synchronized (tx) {
         RefsOperation oper = (RefsOperation) tx.getProperty(TransactionPropertyIndexes.REFS_OPERATION);

         if (oper == null) {
            oper = tx.createRefsOperation(this, ackReason);

            tx.putProperty(TransactionPropertyIndexes.REFS_OPERATION, oper);

            tx.addOperation(oper);
         }

         if (ignoreRedlieveryCheck) {
            oper.setIgnoreRedeliveryCheck();
         }

         oper.setDelivering(delivering);

         return oper;
      }
   }

   @Override
   public void cancel(final Transaction tx, final MessageReference reference) {
      cancel(tx, reference, false);
   }

   @Override
   public void cancel(final Transaction tx, final MessageReference reference, boolean ignoreRedeliveryCheck) {
      getRefsOperation(tx, AckReason.NORMAL, ignoreRedeliveryCheck, true).addAck(reference);
   }

   @Override
   public synchronized void cancel(final MessageReference reference, final long timeBase) throws Exception {
      Pair<Boolean, Boolean> redeliveryResult = checkRedelivery(reference, timeBase, false);
      if (redeliveryResult.getA()) {
         if (!scheduledDeliveryHandler.checkAndSchedule(reference, false)) {
            internalAddSorted(reference);
         }

         resetAllIterators();
      } else if (!redeliveryResult.getB()) {
         decDelivering(reference);
      }
   }

   @Override
   public void expire(final MessageReference ref) throws Exception {
      expire(ref, null, true);
   }

   /**
    * The parameter delivering can be sent as false in situation where the ack is coming outside of the context of
    * delivering. Example: Mirror replication will call the ack here without any consumer involved. On that case no
    * previous delivery happened, hence no information about delivering statistics should be updated.
    */
   @Override
   public void expire(final MessageReference ref, final ServerConsumer consumer, boolean delivering) throws Exception {
      expire(null, ref, consumer, delivering);
   }

   private void expire(final Transaction tx, final MessageReference ref, final ServerConsumer consumer, boolean delivering) throws Exception {
      AddressSettings settingsToUse = getMessageAddressSettings(ref.getMessage());
      SimpleString expiryAddress = settingsToUse.getExpiryAddress();

      if (logger.isDebugEnabled()) {
         logger.debug("expire on {}::{}, consumer={}, expiryAddress={}", queueConfiguration.getAddress(), queueConfiguration.getName(), consumer, expiryAddress);
      }

      if (expiryAddress != null && !expiryAddress.isEmpty()) {
         String messageAddress = ref.getMessage().getAddress();

         if (messageAddress == null) {
            // in the unlikely event where a message does not have an address stored on the message itself,
            // we will get the address from the current queue
            messageAddress = String.valueOf(getAddress());
         }
         createExpiryResources(messageAddress, settingsToUse);

         Bindings bindingList = postOffice.lookupBindingsForAddress(expiryAddress);

         if (bindingList == null || bindingList.getBindings().isEmpty()) {
            if (!printErrorExpiring) {
               // print this only once
               ActiveMQServerLogger.LOGGER.errorExpiringReferencesNoBindings(expiryAddress);
               printErrorExpiring = true;
            }
            acknowledge(tx, ref, AckReason.EXPIRED, null, delivering);
         } else {
            move(tx, expiryAddress, null, ref, false, AckReason.EXPIRED, consumer, null, delivering);
         }
      } else {
         if (!printErrorExpiring) {
            printErrorExpiring = true;
            // print this only once
            ActiveMQServerLogger.LOGGER.errorExpiringReferencesNoAddress(queueConfiguration.getName());
         }

         acknowledge(tx, ref, AckReason.EXPIRED, consumer, delivering);
      }

      // potentially auto-delete this queue if this expired the last message
      refCountForConsumers.check();

      if (server.hasBrokerMessagePlugins()) {
         if (tx == null) {
            server.callBrokerMessagePlugins(plugin -> plugin.messageExpired(ref, settingsToUse.getExpiryAddress(), consumer));
         } else {
            ExpiryLogger expiryLogger = (ExpiryLogger) tx.getProperty(TransactionPropertyIndexes.EXPIRY_LOGGER);
            if (expiryLogger == null) {
               expiryLogger = new ExpiryLogger();
               tx.putProperty(TransactionPropertyIndexes.EXPIRY_LOGGER, expiryLogger);
               tx.addOperation(expiryLogger);
            }

            expiryLogger.addExpiry(queueConfiguration.getAddress(), ref);

            // potentially auto-delete this queue if this expired the last message
            tx.addOperation(new TransactionOperationAbstract() {
               @Override
               public void afterCommit(Transaction tx) {
                  refCountForConsumers.check();
               }
            });
         }
      }
   }

   private AddressSettings getMessageAddressSettings(Message message) {
      if (message.getAddress() == null || message.getAddress().equals(String.valueOf(queueConfiguration.getAddress()))) {
         return cachedAddressSettings;
      } else {
         return server.getAddressSettingsRepository().getMatch(message.getAddress());
      }
   }

   @Override
   public SimpleString getExpiryAddress() {
      return this.cachedAddressSettings.getExpiryAddress();
   }

   @Override
   public SimpleString getDeadLetterAddress() {
      return this.cachedAddressSettings.getDeadLetterAddress();
   }

   @Override
   public void referenceHandled(MessageReference ref) {
      incDelivering(ref);
   }

   @Override
   public void incrementMesssagesAdded() {
      messagesAdded.incrementAndGet();
   }

   @Override
   public void deliverScheduledMessages() throws ActiveMQException {
      internalDeliverScheduleMessages(scheduledDeliveryHandler.cancel(ref -> true));
   }

   @Override
   public void deliverScheduledMessages(String filterString) throws ActiveMQException {
      final Filter filter = filterString == null || filterString.isEmpty() ? null : FilterImpl.createFilter(filterString);
      internalDeliverScheduleMessages(scheduledDeliveryHandler.cancel(ref -> filter == null ? true : filter.match(ref.getMessage())));
   }

   @Override
   public void deliverScheduledMessage(long messageId) throws ActiveMQException {
      internalDeliverScheduleMessages(scheduledDeliveryHandler.cancel(ref -> ref.getMessageID() == messageId));
   }

   private void internalDeliverScheduleMessages(List<MessageReference> scheduledMessages) {
      if (scheduledMessages != null && !scheduledMessages.isEmpty()) {
         for (MessageReference ref : scheduledMessages) {
            ref.getMessage().setScheduledDeliveryTime(ref.getScheduledDeliveryTime());
            ref.setScheduledDeliveryTime(0);
         }
         this.addHead(scheduledMessages, true);
      }
   }

   @Override
   public long getMessagesAdded() {
      if (pageSubscription != null) {
         return messagesAdded.get() + pageSubscription.getCounter().getValueAdded();
      } else {
         return messagesAdded.get();
      }
   }

   @Override
   public long getMessagesAcknowledged() {
      return messagesAcknowledged.get();
   }

   @Override
   public long getAcknowledgeAttempts() {
      return ackAttempts.get();
   }

   @Override
   public long getMessagesExpired() {
      return messagesExpired.get();
   }

   @Override
   public long getMessagesKilled() {
      return messagesKilled.get();
   }

   @Override
   public long getMessagesReplaced() {
      return messagesReplaced.get();
   }

   @Override
   public int deleteAllReferences() throws Exception {
      return deleteAllReferences(DEFAULT_FLUSH_LIMIT);
   }

   @Override
   public int deleteAllReferences(final int flushLimit) throws Exception {
      return deleteMatchingReferences(flushLimit, null);
   }

   @Override
   public int deleteMatchingReferences(Filter filter) throws Exception {
      return deleteMatchingReferences(DEFAULT_FLUSH_LIMIT, filter);
   }

   @Override
   public int deleteMatchingReferences(final int flushLimit, final Filter filter1, AckReason ackReason) throws Exception {
      return iterQueue(flushLimit, filter1, createDeleteMatchingAction(ackReason));
   }

   QueueIterateAction createDeleteMatchingAction(AckReason ackReason) {
      return new QueueIterateAction() {
         @Override
         public boolean actMessage(Transaction tx, MessageReference ref) throws Exception {
            incDelivering(ref);
            acknowledge(tx, ref, ackReason, null, true);
            return true;
         }
      };
   }

   /**
    * This is a generic method for any method interacting on the Queue to move or delete messages Instead of duplicate
    * the feature we created an abstract class where you pass the logic for each message.
    */
   private int iterQueue(final int flushLimit,
                         final Filter filter1,
                         QueueIterateAction messageAction) throws Exception {
      int count = 0;
      int txCount = 0;

      if (filter1 != null) {
         messageAction.addFilter(filter1);
      }

      // This is to avoid scheduling depaging while iterQueue is happening
      // this should minimize the use of the paged executor.
      depagePending = true;

      depageLock.lock();

      try {
         Transaction tx = new TransactionImpl(storageManager);

         synchronized (this) {
            // ensure all messages are moved from intermediateMessageReferences so that they can be seen by the iterator
            doInternalPoll();

            try (LinkedListIterator<MessageReference> iter = iterator()) {

               while (iter.hasNext() && !messageAction.expectedHitsReached(count)) {
                  MessageReference ref = iter.next();

                  if (messageAction.match(ref)) {
                     if (messageAction.actMessage(tx, ref)) {
                        iter.remove();
                        refRemoved(ref);
                     }
                     txCount++;
                     count++;
                  }
               }

               if (txCount > 0) {
                  tx.commit();

                  tx = new TransactionImpl(storageManager);

                  txCount = 0;
               }

               if (messageAction.expectedHitsReached(count)) {
                  return count;
               }

               List<MessageReference> cancelled = scheduledDeliveryHandler.cancel(messageAction::match);
               for (MessageReference messageReference : cancelled) {
                  messageAction.actMessage(tx, messageReference);
                  count++;
                  txCount++;
                  if (messageAction.expectedHitsReached(count)) {
                     break;
                  }
               }

               if (txCount > 0) {
                  tx.commit();
                  tx = new TransactionImpl(storageManager);
                  txCount = 0;
               }
            }
         }

         if (pageIterator != null) {
            while (pageIterator.hasNext() && !messageAction.expectedHitsReached(count)) {
               PagedReference reference = pageIterator.next();
               pageIterator.remove();

               if (messageAction.match(reference)) {
                  if (!messageAction.actMessage(tx, reference)) {
                     addTail(reference, false);
                  }
                  txCount++;
                  count++;
               } else {
                  addTail(reference, false);
               }

               if (txCount > 0 && txCount % flushLimit == 0) {
                  tx.commit();
                  tx = new TransactionImpl(storageManager);
                  txCount = 0;
               }
            }
         }

         if (txCount > 0) {
            tx.commit();
         }

         if (filter != null && !queueDestroyed && pageSubscription != null) {
            scheduleDepage(false);
         }

         return count;
      } finally {
         depageLock.unlock();
         // to resume flow of depages, just in case
         // as we disabled depaging during the execution of this method
         depagePending = false;
         forceDelivery();
      }
   }

   @Override
   public void destroyPaging() throws Exception {
      // it could be null on embedded or certain unit tests
      if (pageSubscription != null) {
         if (logger.isTraceEnabled()) {
            logger.trace("Destroying paging for {}", queueConfiguration.getName(), new Exception("trace"));
         }
         pageSubscription.destroy();
         pageSubscription.cleanupEntries(true);
      }
   }

   @Override
   public synchronized boolean deleteReference(final long messageID) throws Exception {
      boolean deleted = false;

      Transaction tx = new TransactionImpl(storageManager);

      try (LinkedListIterator<MessageReference> iter = iterator()) {

         while (iter.hasNext()) {
            MessageReference ref = iter.next();
            if (ref.getMessage().getMessageID() == messageID) {
               incDelivering(ref);
               acknowledge(tx, ref);
               iter.remove();
               refRemoved(ref);
               deleted = true;
               break;
            }
         }

         if (!deleted) {
            // Look in scheduled deliveries
            deleted = scheduledDeliveryHandler.removeReferenceWithID(messageID, tx) != null ? true : false;
         }

         tx.commit();

         return deleted;
      }
   }

   @Override
   public void deleteQueue() throws Exception {
      deleteQueue(false);
   }

   @Override
   public void removeAddress() throws Exception {
      server.removeAddressInfo(getAddress(), null);
   }

   @Override
   public void deleteQueue(boolean removeConsumers) throws Exception {
      synchronized (this) {
         if (this.queueDestroyed)
            return;
         this.queueDestroyed = true;
      }

      Transaction tx = new BindingsTransactionImpl(storageManager);

      try {
         deleteAllReferences();

         destroyPaging();

         postOffice.removeBinding(queueConfiguration.getName(), tx, true);

         if (removeConsumers) {
            for (ConsumerHolder consumerHolder : consumers) {
               consumerHolder.consumer.disconnect();
            }
         }

         if (isDurable()) {
            storageManager.deleteQueueBinding(tx.getID(), getID());
            tx.setContainsPersistent();
         }

         if (slowConsumerReaperFuture != null) {
            slowConsumerReaperFuture.cancel(false);
         }

         tx.commit();
      } catch (Exception e) {
         tx.rollback();
         throw e;
      } finally {
         if (factory != null) {
            factory.queueRemoved(this);
         }
      }
   }

   @Override
   public synchronized boolean expireReference(final long messageID) throws Exception {
      if (isExpiryDisabled()) {
         return false;
      }

      try (LinkedListIterator<MessageReference> iter = iterator()) {

         while (iter.hasNext()) {
            MessageReference ref = iter.next();
            if (ref.getMessage().getMessageID() == messageID) {
               incDelivering(ref);
               expire(ref);
               iter.remove();
               refRemoved(ref);
               return true;
            }
         }
         return false;
      }
   }

   @Override
   public synchronized int expireReferences(final Filter filter) throws Exception {
      if (isExpiryDisabled()) {
         return 0;
      }

      Transaction tx = new TransactionImpl(storageManager);

      int count = 0;

      try (LinkedListIterator<MessageReference> iter = iterator()) {

         while (iter.hasNext()) {
            MessageReference ref = iter.next();
            if (filter == null || filter.match(ref.getMessage())) {
               incDelivering(ref);
               expire(tx, ref, null, true);
               iter.remove();
               refRemoved(ref);
               count++;
            }
         }

         tx.commit();

         return count;
      }
   }

   @Override
   public void expireReferences(Runnable done) {
      if (isExpiryDisabled()) {
         if (done != null) {
            done.run();
         }
         return;
      }

      if (!queueDestroyed) {
         getExecutor().execute(new ExpiryScanner(done));
      } else {
         // queue is destroyed, move on
         if (done != null) {
            done.run();
         }
      }
   }

   private boolean isExpiryDisabled() {
      final SimpleString expiryAddress = cachedAddressSettings.getExpiryAddress();
      if (expiryAddress != null && expiryAddress.equals(queueConfiguration.getAddress())) {
         // check expire with itself would be silly (waste of time)
         logger.trace("Redundant expiration from {} to {}", queueConfiguration.getAddress(), expiryAddress);

         return true;
      }

      if (isMirrorController()) {
         logger.trace("Mirror SNF queues are not supposed to expire messages. Address={}, Queue={}", queueConfiguration.getAddress(), queueConfiguration.getName());
         return true;
      }

      return false;
   }

   class ExpiryScanner implements Runnable {

      private final Runnable doneCallback;

      ExpiryScanner(Runnable doneCallback) {
         this.doneCallback = doneCallback;
      }

      LinkedListIterator<MessageReference> iter = null;

      @Override
      public void run() {
         boolean expired = false;
         boolean hasElements = false;
         int elementsIterated = 0;
         int elementsExpired = 0;

         boolean rescheduled = false;

         LinkedList<MessageReference> expiredMessages = new LinkedList<>();
         synchronized (QueueImpl.this) {
            logger.debug("Scanning for expires on {}", queueConfiguration.getName());

            if (iter == null) {
               if (server.hasBrokerQueuePlugins()) {
                  try {
                     server.callBrokerQueuePlugins((p) -> p.beforeExpiryScan(QueueImpl.this));
                  } catch (Exception e) {
                     logger.warn(e.getMessage(), e);
                  }
               }
               iter = iterator();
            }

            try {
               while (!queueDestroyed && postOffice.isStarted() && iter.hasNext()) {
                  hasElements = true;
                  MessageReference ref = iter.next();
                  if (ref.getMessage().isExpired()) {
                     elementsExpired++;
                     incDelivering(ref);
                     expired = true;
                     expiredMessages.add(ref);
                     iter.remove();
                  }
                  if (++elementsIterated >= MAX_DELIVERIES_IN_LOOP) {
                     logger.debug("Expiry Scanner on {} ran for {} iteration, scheduling a new one", QueueImpl.this.getName(), elementsIterated);
                     rescheduled = true;
                     getExecutor().execute(this);
                     break;
                  }
               }
            } finally {
               if (!rescheduled) {
                  logger.debug("Scanning for expires on {} done", queueConfiguration.getName());

                  if (server.hasBrokerQueuePlugins()) {
                     try {
                        server.callBrokerQueuePlugins((p) -> p.afterExpiryScan(QueueImpl.this));
                     } catch (Exception e) {
                        logger.warn(e.getMessage(), e);
                     }
                  }

                  iter.close();
                  iter = null;

                  if (doneCallback != null) {
                     doneCallback.run();
                  }
               }
            }
         }

         if (!expiredMessages.isEmpty()) {
            final Transaction tx = new TransactionImpl(storageManager);
            for (MessageReference ref : expiredMessages) {
               try {
                  expire(tx, ref, null, true);
                  refRemoved(ref);
               } catch (Exception e) {
                  ActiveMQServerLogger.LOGGER.errorExpiringReferencesOnQueue(ref, e);
               }
            }

            try {
               tx.commit();
            } catch (Exception e) {
               ActiveMQServerLogger.LOGGER.unableToCommitTransaction(e);
            }

            if (logger.isDebugEnabled()) {
               logger.debug("Expired {} references", elementsExpired);
            }
         }

         // If empty we need to schedule depaging to make sure we would depage expired messages as well
         if ((!hasElements || expired) && pageIterator != null && pageIterator.tryNext() != PageIterator.NextResult.noElements) {
            scheduleDepage(true);
         }
      }
   }

   @Override
   public boolean sendMessageToDeadLetterAddress(final long messageID) throws Exception {

      return iterQueue(DEFAULT_FLUSH_LIMIT, null, new QueueIterateAction(messageID) {

         @Override
         public boolean actMessage(Transaction tx, MessageReference ref) throws Exception {
            incDelivering(ref);
            sendToDeadLetterAddress(tx, ref);
            return true;
         }

      }) == 1;

   }

   @Override
   public int sendMessagesToDeadLetterAddress(Filter filter) throws Exception {

      return iterQueue(DEFAULT_FLUSH_LIMIT, filter, new QueueIterateAction() {

         @Override
         public boolean actMessage(Transaction tx, MessageReference ref) throws Exception {
            incDelivering(ref);
            sendToDeadLetterAddress(tx, ref);
            return true;
         }

      });

   }

   @Override
   public boolean moveReference(final long messageID,
                                final SimpleString toAddress,
                                final Binding binding,
                                final boolean rejectDuplicate) throws Exception {

      return iterQueue(DEFAULT_FLUSH_LIMIT, null, new QueueIterateAction(messageID) {

         @Override
         public boolean actMessage(Transaction tx, MessageReference ref) throws Exception {
            incDelivering(ref);
            move(tx, toAddress, binding, ref, rejectDuplicate, AckReason.NORMAL, null, null, true);
            return true;
         }

      }) == 1;

   }

   @Override
   public int moveReferences(final Filter filter, final SimpleString toAddress, Binding binding) throws Exception {
      return moveReferences(DEFAULT_FLUSH_LIMIT, filter, toAddress, false, binding);
   }

   @Override
   public int moveReferences(final int flushLimit,
                             final Filter filter,
                             final SimpleString toAddress,
                             final boolean rejectDuplicates,
                             final Binding binding) throws Exception {
      return moveReferences(flushLimit, filter, toAddress, rejectDuplicates, -1, binding);
   }

   @Override
   public int moveReferences(final int flushLimit,
                             final Filter filter,
                             final SimpleString toAddress,
                             final boolean rejectDuplicates,
                             final int messageCount,
                             final Binding binding) throws Exception {
      final Integer expectedHits = messageCount > 0 ? messageCount : null;
      final DuplicateIDCache targetDuplicateCache = postOffice.getDuplicateIDCache(toAddress);

      return iterQueue(flushLimit, filter, new QueueIterateAction(expectedHits) {
         @Override
         public boolean actMessage(Transaction tx, MessageReference ref) throws Exception {
            boolean ignored = false;

            incDelivering(ref);

            if (rejectDuplicates) {
               byte[] duplicateBytes = ref.getMessage().getDuplicateIDBytes();
               if (duplicateBytes != null) {
                  if (targetDuplicateCache.contains(duplicateBytes)) {
                     ActiveMQServerLogger.LOGGER.messageWithDuplicateID(ref.getMessage().getDuplicateProperty(), toAddress, queueConfiguration.getAddress(), queueConfiguration.getAddress());
                     acknowledge(tx, ref);
                     ignored = true;
                  }
               }
            }

            if (!ignored) {
               move(tx, toAddress, binding, ref, rejectDuplicates, AckReason.NORMAL, null, null, true);
            }

            return true;
         }
      });
   }

   public int moveReferencesBetweenSnFQueues(final SimpleString queueSuffix) throws Exception {
      return iterQueue(DEFAULT_FLUSH_LIMIT, null, new QueueIterateAction() {
         @Override
         public boolean actMessage(Transaction tx, MessageReference ref) throws Exception {
            return moveBetweenSnFQueues(queueSuffix, tx, ref, null);
         }
      });
   }

   @Override
   public boolean copyReference(final long messageID,
                                final SimpleString toQueue,
                                final Binding binding) throws Exception {

      return iterQueue(DEFAULT_FLUSH_LIMIT, null, new QueueIterateAction(messageID) {

         @Override
         public boolean actMessage(Transaction tx, MessageReference ref) throws Exception {
            copy(tx, toQueue, binding, ref);
            return false;
         }

      }) == 1;

   }

   public int rerouteMessages(final SimpleString queueName, final Filter filter) throws Exception {
      return iterQueue(DEFAULT_FLUSH_LIMIT, filter, new QueueIterateAction() {
         @Override
         public boolean actMessage(Transaction tx, MessageReference ref) throws Exception {
            RoutingContext routingContext = new RoutingContextImpl(tx);
            SimpleString address = server.locateQueue(queueName).getAddress();
            routingContext.setAddress(address);
            Message m = ref.getMessage();
            m.setAddress(address);
            server.getPostOffice().getBinding(queueName).route(m, routingContext);
            postOffice.processRoute(m, routingContext, false);
            return false;
         }
      });
   }

   @Override
   public int retryMessages(Filter filter) throws Exception {
      return retryMessages(filter, null);
   }

   @Override
   public int retryMessages(Filter filter, Integer expectedHits) throws Exception {

      final HashMap<String, Long> queues = new HashMap<>();

      return iterQueue(DEFAULT_FLUSH_LIMIT, filter, new QueueIterateAction(expectedHits) {

         @Override
         public boolean actMessage(Transaction tx, MessageReference ref) throws Exception {

            String originalMessageAddress = ref.getMessage().getAnnotationString(Message.HDR_ORIGINAL_ADDRESS);
            String originalMessageQueue = ref.getMessage().getAnnotationString(Message.HDR_ORIGINAL_QUEUE);
            Binding binding = null;

            if (originalMessageQueue != null) {
               binding = postOffice.getBinding(SimpleString.of(originalMessageQueue));
            }

            if (originalMessageAddress != null && binding != null) {

               incDelivering(ref);

               Long targetQueue = null;
               if (originalMessageQueue != null && !originalMessageQueue.equals(originalMessageAddress)) {
                  targetQueue = queues.get(originalMessageQueue);
                  if (targetQueue == null) {
                     if (binding instanceof LocalQueueBinding localQueueBinding) {
                        targetQueue = localQueueBinding.getID();
                        queues.put(originalMessageQueue, targetQueue);
                     }
                  }
               }

               move(tx, SimpleString.of(originalMessageAddress), null, ref, false, AckReason.NORMAL, null, targetQueue, true);

               return true;
            }

            if (logger.isDebugEnabled()) {
               logger.debug("QueueImpl::retryMessages cannot find targetQueue for message {}", ref.getMessage());
            }
            return false;
         }
      });

   }

   @Override
   public boolean changeReferencePriority(final long messageID, final byte newPriority) throws Exception {

      return iterQueue(DEFAULT_FLUSH_LIMIT, null, new QueueIterateAction(messageID) {

         @Override
         public boolean actMessage(Transaction tx, MessageReference ref) throws Exception {
            ref.getMessage().setPriority(newPriority);
            return false;
         }

      }) == 1;

   }

   @Override
   public int changeReferencesPriority(final Filter filter, final byte newPriority) throws Exception {

      return iterQueue(DEFAULT_FLUSH_LIMIT, filter, new QueueIterateAction() {

         @Override
         public boolean actMessage(Transaction tx, MessageReference ref) throws Exception {
            ref.getMessage().setPriority(newPriority);
            return false;
         }

      });

   }

   @Override
   public synchronized void resetAllIterators() {
      for (ConsumerHolder holder : this.consumers) {
         holder.resetIterator();
      }
   }

   @Override
   public synchronized void pause() {
      pause(false);
   }

   @Override
   public synchronized void reloadPause(long recordID) {
      this.paused = true;
      if (pauseStatusRecord >= 0) {
         try {
            storageManager.deleteQueueStatus(pauseStatusRecord);
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.unableToDeleteQueueStatus(e);
         }
      }
      this.pauseStatusRecord = recordID;
   }

   @Override
   public synchronized void pause(boolean persist) {
      try {
         this.flushDeliveriesInTransit();
         if (persist && isDurable()) {
            if (pauseStatusRecord >= 0) {
               storageManager.deleteQueueStatus(pauseStatusRecord);
            }
            pauseStatusRecord = storageManager.storeQueueStatus(queueConfiguration.getId(), AddressQueueStatus.PAUSED);
         }
      } catch (Exception e) {
         ActiveMQServerLogger.LOGGER.unableToPauseQueue(e);
      }
      paused = true;
   }

   @Override
   public synchronized void resume() {
      paused = false;

      if (pauseStatusRecord >= 0) {
         try {
            storageManager.deleteQueueStatus(pauseStatusRecord);
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.unableToResumeQueue(e);
         }
         pauseStatusRecord = -1;
      }

      deliverAsync();
   }

   @Override
   public synchronized boolean isPaused() {
      return paused || (addressInfo != null && addressInfo.isPaused());
   }

   @Override
   public synchronized boolean isPersistedPause() {
      return this.pauseStatusRecord >= 0;
   }

   @Override
   public boolean isDirectDeliver() {
      return directDeliver && supportsDirectDeliver;
   }

   @Override
   public boolean isInternalQueue() {
      return queueConfiguration.isInternal();
   }

   @Override
   public void setInternalQueue(boolean internalQueue) {
      queueConfiguration.setInternal(internalQueue);
   }

   // Public
   // -----------------------------------------------------------------------------
   @Override
   public boolean equals(final Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof QueueImpl other)) {
         return false;
      }

      return Objects.equals(queueConfiguration.getName(), other.queueConfiguration.getName());
   }

   @Override
   public int hashCode() {
      return queueConfiguration.getName().hashCode();
   }

   @Override
   public String toString() {
      return "QueueImpl[name=" + queueConfiguration.getName() + ", postOffice=" + this.postOffice + ", temp=" + queueConfiguration.isTemporary() + "]@" + Integer.toHexString(System.identityHashCode(this));
   }

   private synchronized void internalAddTail(final MessageReference ref) {
      refAdded(ref);
      ref.setSequence(queueSequence.incrementAndGet());
      messageReferences.addTail(ref, getPriority(ref));
      pendingMetrics.incrementMetrics(ref);
      enforceRing(false);
   }

   /**
    * The caller of this method requires synchronized on the queue. I'm not going to add synchronized to this method
    * just for a precaution, as I'm not 100% sure this won't cause any extra runtime.
    */
   private void internalAddHead(final MessageReference ref) {
      if (RefCountMessage.isRefTraceEnabled()) {
         RefCountMessage.deferredDebug(ref.getMessage(), "add head queue {}", this.getAddress());
      }
      queueMemorySize.addSize(ref.getMessageMemoryEstimate());
      pendingMetrics.incrementMetrics(ref);
      refAdded(ref);

      int priority = getPriority(ref);

      messageReferences.addHead(ref, priority);

      ref.setInDelivery(false);
   }

   /**
    * The caller of this method requires synchronized on the queue. I'm not going to add synchronized to this method
    * just for a precaution, as I'm not 100% sure this won't cause any extra runtime.
    */
   private void internalAddSorted(final MessageReference ref) {
      if (RefCountMessage.isRefTraceEnabled()) {
         RefCountMessage.deferredDebug(ref.getMessage(), "add sorted queue {}", this.getAddress());
      }
      queueMemorySize.addSize(ref.getMessageMemoryEstimate());
      pendingMetrics.incrementMetrics(ref);
      refAdded(ref);

      int priority = getPriority(ref);

      messageReferences.addSorted(ref, priority);

      ref.setInDelivery(false);
   }

   private int getPriority(MessageReference ref) {
      if (isInternalQueue()) {
         // if it's an internal queue we need to send the events on their original ordering
         // for example an ACK arriving before the send on a Mirror..
         return 4;
      }
      try {
         return ref.getMessage().getPriority();
      } catch (Throwable e) {
         ActiveMQServerLogger.LOGGER.unableToGetMessagePriority(e);
         return 4; // the default one in case of failure
      }
   }

   synchronized void doInternalPoll() {

      int added = 0;
      MessageReference ref;

      while ((ref = intermediateMessageReferences.poll()) != null) {
         if (ref.skipDelivery()) {
            continue;
         }
         internalAddTail(ref);

         if (!ref.isPaged()) {
            incrementMesssagesAdded();
         }

         if (added++ > MAX_DELIVERIES_IN_LOOP) {
            // if we just keep polling from the intermediate we could starve in case there's a sustained load
            deliverAsync(true);
            return;
         }
      }
   }

   /**
    * This method is to only be used during deliveryAsync when the queue was destroyed and the async process left more
    * messages to be delivered. This is a race between destroying the queue and async sends that came after the
    * deleteQueue already happened.
    */
   private void removeMessagesWhileDelivering() throws Exception {
      assert queueDestroyed : "Method to be used only when the queue was destroyed";
      Transaction tx = new TransactionImpl(storageManager);
      int txCount = 0;

      try (LinkedListIterator<MessageReference> iter = iterator()) {
         while (iter.hasNext()) {
            MessageReference ref = iter.next();

            if (ref.isPaged()) {
               // this means the queue is being removed
               // hence paged references are just going away through
               // page cleanup
               continue;
            }
            acknowledge(tx, ref, AckReason.KILLED, null, true);
            iter.remove();
            refRemoved(ref);
            txCount++;
         }

         if (txCount > 0) {
            tx.commit();
         }
      }
   }

   /**
    * This method will deliver as many messages as possible until all consumers are busy or there
    * are no more matching or available messages.
    */
   private boolean deliver() {
      if (logger.isTraceEnabled()) {
         logger.trace("Queue {} doing deliver. messageReferences={} with consumers={}", queueConfiguration.getName(), messageReferences.size(), getConsumerCount());
      }

      scheduledRunners.decrementAndGet();

      doInternalPoll();

      // Either the iterator is empty or the consumer is busy
      int noDelivery = 0;

      // track filters not matching, used to track when all consumers can't match, redistribution is then an option
      int numNoMatch = 0;
      int numAttempts = 0;

      int handled = 0;

      long timeout = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(DELIVERY_TIMEOUT);
      consumers.reset();
      while (true) {
         if (handled == MAX_DELIVERIES_IN_LOOP || System.nanoTime() - timeout > 0) {
            // Schedule another one - we do this to prevent a single thread getting caught up in this loop for too long
            deliverAsync(true);
            return false;
         }

         MessageReference ref;
         Consumer handledconsumer = null;

         synchronized (QueueImpl.this) {

            if (queueDestroyed) {
               if (messageReferences.isEmpty()) {
                  return false;
               }
               try {
                  removeMessagesWhileDelivering();
               } catch (Exception e) {
                  logger.warn(e.getMessage(), e);
               }
               return false;
            }

            // Need to do these checks inside the synchronized
            if (isPaused() || !canDispatch()) {
               return false;
            }

            if (messageReferences.isEmpty()) {
               break;
            }

            final ConsumerHolder<? extends Consumer> holder;
            final LinkedListIterator<MessageReference> holderIterator;
            if (consumers.hasNext()) {
               holder = consumers.next();
               if (holder == null) {
                  // this shouldn't happen, however I'm adding this check just in case
                  logger.debug("consumers.next() returned null.");
                  deliverAsync(true);
                  return false;
               }
               if (holder.iter == null) {
                  holder.iter = messageReferences.iterator();
               }
               holderIterator = holder.iter;
            } else {
               pruneLastValues();
               break;
            }

            Consumer consumer = holder.consumer;
            Consumer groupConsumer = null;

            // we remove the consumerHolder when the Consumer is closed
            // however the QueueConsumerIterator may hold a reference until the reset is called, which
            // could happen a little later.
            if (consumer.isClosed()) {
               deliverAsync(true);
               return false;
            }

            if (holderIterator.hasNext()) {
               ref = holderIterator.next();
            } else {
               ref = null;
            }

            if (ref == null) {
               noDelivery++;
            } else {
               if (checkExpired(ref)) {
                  logger.trace("Reference {} being expired", ref);

                  removeMessageReference(holder, ref);
                  handled++;
                  consumers.reset();
                  continue;
               }

               logger.trace("Queue {} is delivering reference {}", queueConfiguration.getName(), ref);

               final SimpleString groupID = extractGroupID(ref);
               groupConsumer = getGroupConsumer(groupID);

               if (groupConsumer != null) {
                  consumer = groupConsumer;
               }

               numAttempts++;
               HandleStatus status = handle(ref, consumer);

               if (status == HandleStatus.HANDLED) {

                  // if a message was delivered, any previous negative attempts need to be cleared
                  // this is to avoid breaks on the loop when checking for any other factors.
                  noDelivery = 0;
                  numNoMatch = 0;
                  numAttempts = 0;

                  ref = handleMessageGroup(ref, consumer, groupConsumer, groupID);

                  deliveriesInTransit.countUp();

                  if (!queueConfiguration.isNonDestructive()) {
                     removeMessageReference(holder, ref);
                  }
                  ref.setInDelivery(true);
                  handledconsumer = consumer;
                  handled++;
                  consumers.reset();
               } else if (status == HandleStatus.BUSY) {
                  try {
                     holderIterator.repeat();
                  } catch (NoSuchElementException e) {
                     // this could happen if there was an exception on the queue handling
                     // and it returned BUSY because of that exception
                     //
                     // We will just log it as there's nothing else we can do now.
                     logger.warn(e.getMessage(), e);
                  }

                  noDelivery++;
                  numNoMatch = 0;
                  numAttempts = 0;
                  // no consumers.reset() b/c we skip this consumer
               } else if (status == HandleStatus.NO_MATCH) {
                  consumers.reset();
                  numNoMatch++;
                  // every attempt resulted in noMatch for number of consumers means we tried all consumers for a single message
                  if (numNoMatch == numAttempts && numAttempts == consumers.size() && redistributor == null) {
                     hasUnMatchedPending = true;
                     // one hit of unmatched message is enough, no need to reset counters
                  }
               }
            }

            if (groupConsumer != null) {
               if (noDelivery > 0) {
                  pruneLastValues();
                  break;
               }
               noDelivery = 0;
            } else if (!consumers.hasNext()) {
               // Round robin'd all

               if (noDelivery == this.consumers.size()) {
                  pruneLastValues();

                  if (handledconsumer != null) {
                     // this shouldn't really happen,
                     // however I'm keeping this as an assertion case future developers ever change the logic here on this class
                     ActiveMQServerLogger.LOGGER.nonDeliveryHandled();
                  } else {
                     logger.debug("{}::All the consumers were busy, giving up now", this);
                     break;
                  }
               }

               noDelivery = 0;
            }

         }

         if (handledconsumer != null) {
            proceedDeliver(handledconsumer, ref);
         }
      }

      return true;
   }

   // called with 'this' locked
   protected void pruneLastValues() {
      // interception point for LVQ
   }

   protected void removeMessageReference(ConsumerHolder<? extends Consumer> holder, MessageReference ref) {
      holder.iter.remove();
      refRemoved(ref);
   }

   private void checkDepage() {
      if (queueDestroyed) {
         return;
      }
      if (pageIterator != null && pageSubscription.isStorePaging()) {
         if (logger.isDebugEnabled()) {
            logger.debug("CheckDepage on queue name {}, id={}", queueConfiguration.getName(), queueConfiguration.getId());
         }
         // we will issue a delivery runnable to check for released space from acks and resume depage
         pageDelivered = true;

         if (!depagePending && needsDepage() && pageIterator.tryNext() != PageIterator.NextResult.noElements) {
            scheduleDepage(false);
         }
      } else {
         pageDelivered = false;
      }
   }

   /**
    * This is a check on page sizing.
    */
   private boolean needsDepage() {
      final int maxReadMessages = pageSubscription.getPagingStore().getMaxPageReadMessages();
      final int maxReadBytes = pageSubscription.getPagingStore().getMaxPageReadBytes();
      final int prefetchMessages = pageSubscription.getPagingStore().getPrefetchPageMessages();
      final int prefetchBytes = pageSubscription.getPagingStore().getPrefetchPageBytes();

      if (maxReadMessages <= 0 && maxReadBytes <= 0 && prefetchMessages <= 0 && prefetchBytes <= 0) {
         // if all values are disabled, we will protect the broker using an older semantic
         // where we don't look for deliveringMetrics..
         // this would give users a chance to switch to older protection mode.
         return queueMemorySize.getSize() < pageSubscription.getPagingStore().getMaxSize() &&
            intermediateMessageReferences.size() + messageReferences.size() < MAX_DEPAGE_NUM;
      } else {

         /*
           queueMemorySize.getSize() = How many bytes the messages in memory (read from paging or journal, ready to delivery) are occupying
           queueMemorySize.getElements() = How many elements are in memory ready to be delivered.
           deliveringMetrics.getMessageCount() = How many messages are in the client's buffer for the consumers.
           deliveringMetrics.getPersistentSize() = How many bytes are in the client's buffer for the consumers.

           At all times the four rules have to be satisfied, and they can be switched off.

           Notice in case all of these are -1, we will use the previous semantic on fetching data from paging on the other part of the 'if' in this method.

           Also notice in case needsDepageResult = false, we will check for the maxReadBytes and then print a warning if there are more delivering than we can handle.

           maxRead(Bytes or messages) will limit reading messages by the number of delivering + available messages (bytes or message-count)
           prefetch (bytes or messages) will limit reading messages by the number of available messages, without using the delivering

           prefetch(bytes and messages) should be <= max-read(bytes and messages) at all times.
          */

         boolean needsDepageResult =
            (maxReadBytes <= 0 || (queueMemorySize.getSize() + deliveringMetrics.getPersistentSize()) < maxReadBytes) &&
               (prefetchBytes <= 0 || (queueMemorySize.getSize() < prefetchBytes)) &&
               (maxReadMessages <= 0 || (queueMemorySize.getElements() + deliveringMetrics.getMessageCount()) < maxReadMessages) &&
               (prefetchMessages <= 0 || (queueMemorySize.getElements() < prefetchMessages));

         if (!needsDepageResult) {
            if (!pageFlowControlled && (maxReadBytes > 0 && deliveringMetrics.getPersistentSize() >= maxReadBytes || maxReadMessages > 0 && deliveringMetrics.getMessageCount() >= maxReadMessages)) {
               if (System.currentTimeMillis() - pageFlowControlledLastLog > PAGE_FLOW_CONTROL_PRINT_INTERVAL) {
                  pageFlowControlledLastLog = System.currentTimeMillis();
                  ActiveMQServerLogger.LOGGER.warnPageFlowControl(String.valueOf(queueConfiguration.getAddress()), String.valueOf(queueConfiguration.getName()), deliveringMetrics.getMessageCount(), deliveringMetrics.getPersistentSize(), maxReadMessages, maxReadBytes);
               }
               if (logger.isDebugEnabled()) {
                  logger.debug("Message dispatch from paging is blocked. Address {}/Queue{} will not read any more messages from paging " +
                                  "until pending messages are acknowledged. There are currently {} messages pending ({} bytes) with max reads at " +
                                  "maxPageReadMessages({}) and maxPageReadBytes({}). Either increase reading attributes at the address-settings or change your consumers to acknowledge more often.",
                               queueConfiguration.getAddress(), queueConfiguration.getName(), deliveringMetrics.getMessageCount(), deliveringMetrics.getPersistentSize(), maxReadMessages, maxReadBytes);
               }
               pageFlowControlled = true;
            }
         } else {
            pageFlowControlled = false;
         }

         return needsDepageResult;
      }
   }

   private SimpleString extractGroupID(MessageReference ref) {
      if (queueConfiguration.isInternal() || queueConfiguration.isExclusive() || queueConfiguration.getGroupBuckets() == 0) {
         return null;
      } else {
         try {
            return ref.getMessage().getGroupID();
         } catch (Throwable e) {
            ActiveMQServerLogger.LOGGER.unableToExtractGroupID(e);
            return null;
         }
      }
   }

   private int extractGroupSequence(MessageReference ref) {
      if (queueConfiguration.isInternal()) {
         return 0;
      } else {
         try {
            // But we don't use the groupID on internal queues (clustered queues) otherwise the group map would leak forever
            return ref.getMessage().getGroupSequence();
         } catch (Throwable e) {
            ActiveMQServerLogger.LOGGER.unableToExtractGroupSequence(e);
            return 0;
         }
      }
   }

   protected void refRemoved(MessageReference ref) {
      queueMemorySize.addSize(-ref.getMessageMemoryEstimate());
      pendingMetrics.decrementMetrics(ref);
      if (ref.isPaged()) {
         pagedReferences.decrementAndGet();
      }
   }

   protected void addRefSize(MessageReference ref) {
      queueMemorySize.addSize(ref.getMessageMemoryEstimate());
      pendingMetrics.incrementMetrics(ref);
   }

   protected void refAdded(final MessageReference ref) {
      if (ref.isPaged()) {
         pagedReferences.incrementAndGet();
      }
   }

   private void scheduleDepage(final boolean scheduleExpiry) {
      if (!depagePending) {
         logger.trace("Scheduling depage for queue {}", queueConfiguration.getName());

         depagePending = true;
         pageSubscription.getPagingStore().execute(() -> depage(scheduleExpiry));
      }
   }

   private void depage(final boolean scheduleExpiry) {
      depagePending = false;

      if (!depageLock.tryLock()) {
         return;
      }

      try {
         synchronized (this) {
            if (isPaused() || pageIterator == null) {
               return;
            }
         }

         long timeout = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(DELIVERY_TIMEOUT);

         if (logger.isTraceEnabled()) {
            logger.trace("QueueMemorySize before depage on queue={} is {}", queueConfiguration.getName(), queueMemorySize.getSize());
         }

         this.directDeliver = false;

         int depaged = 0;
         while (timeout - System.nanoTime() > 0 && needsDepage()) {
            PageIterator.NextResult status = pageIterator.tryNext();
            if (status == PageIterator.NextResult.retry) {
               continue;
            } else if (status == PageIterator.NextResult.noElements) {
               break;
            }

            depaged++;
            PagedReference reference = pageIterator.next();
            if (logger.isDebugEnabled()) {
               logger.debug("Depaging reference {} on queue {} depaged::{}", reference, queueConfiguration.getName(), depaged);
            }
            addTail(reference, false);
            pageIterator.remove();
         }

         if (logger.isDebugEnabled()) {
            final int maxSize = pageSubscription.getPagingStore().getPageSizeBytes();

            if (depaged == 0 && queueMemorySize.getSize() >= maxSize) {
               logger.debug("Couldn't depage any message as the maxSize on the queue was achieved. There are too many pending messages to be acked in reference to the page configuration");
            }

            if (logger.isDebugEnabled()) {
               logger.debug("Queue Memory Size after depage on queue={} is {} with maxSize = {}. Depaged {} messages, pendingDelivery={}, intermediateMessageReferences= {}, queueDelivering={}",
                            queueConfiguration.getName(), queueMemorySize.getSize(), maxSize, depaged, messageReferences.size(), intermediateMessageReferences.size(), deliveringMetrics.getMessageCount());
            }
         }

         deliverAsync(true);

         if (depaged > 0 && scheduleExpiry) {
            // This will just call an executor
            expireReferences();
         }
      } finally {
         depageLock.unlock();
      }
   }

   @Override
   public synchronized MessageReference removeWithSuppliedID(String serverID, long id, NodeStoreFactory<MessageReference> nodeStore) {
      checkIDSupplier(nodeStore);
      doInternalPoll(); // we need to flush intermediate references first
      MessageReference reference = messageReferences.removeWithID(serverID, id);
      if (reference != null) {
         refRemoved(reference);
      }
      return reference;
   }

   private void internalAddRedistributor() {
      if (redistributor == null && (consumers.isEmpty() || hasUnMatchedPending)) {
         logger.trace("QueueImpl::Adding redistributor on queue {}", this);

         redistributor = new ConsumerHolder(new Redistributor(this, storageManager.generateID(), postOffice), this);
         redistributor.consumer.start();
         consumers.add(redistributor);
         hasUnMatchedPending = false;

         deliverAsync();
      }
   }
   @Override
   public Pair<Boolean, Boolean> checkRedelivery(final MessageReference reference,
                                                 final long timeBase,
                                                 final boolean ignoreRedeliveryDelay) throws Exception {

      if (queueConfiguration.isInternal()) {
         logger.trace("Queue {} is an internal queue, no checkRedelivery", queueConfiguration.getName());

         // no DLQ check on internal queues
         // we just need to return statistics on the delivering
         decDelivering(reference);
         return new Pair<>(true, false);
      }

      if (!queueConfiguration.isInternal() && reference.isDurable() && isDurable() && !reference.isPaged()) {
         storageManager.updateDeliveryCount(reference);
      }

      int maxDeliveries = cachedAddressSettings.getMaxDeliveryAttempts();
      int deliveryCount = reference.getDeliveryCount();

      // First check DLA
      if (maxDeliveries > 0 && deliveryCount >= maxDeliveries) {
         if (logger.isTraceEnabled()) {
            logger.trace("Sending reference {} to DLA = {} since ref.getDeliveryCount={} and maxDeliveries={} from queue={}",
                         reference, cachedAddressSettings.getDeadLetterAddress(), reference.getDeliveryCount(), maxDeliveries, queueConfiguration.getName());
         }
         boolean dlaResult = sendToDeadLetterAddress(null, reference, cachedAddressSettings.getDeadLetterAddress());

         return new Pair<>(false, dlaResult);
      } else {
         // Second check Redelivery Delay
         long redeliveryDelay = cachedAddressSettings.getRedeliveryDelay();
         if (!ignoreRedeliveryDelay && redeliveryDelay > 0) {
            redeliveryDelay = calculateRedeliveryDelay(cachedAddressSettings, deliveryCount);

            if (logger.isTraceEnabled()) {
               logger.trace("Setting redeliveryDelay={} on reference={}", redeliveryDelay, reference);
            }

            reference.setScheduledDeliveryTime(timeBase + redeliveryDelay);

            if (!reference.isPaged() && reference.isDurable() && isDurable()) {
               storageManager.updateScheduledDeliveryTime(reference);
            }
         }

         decDelivering(reference);

         return new Pair<>(true, false);
      }
   }

   /**
    * Used on testing only *
    */
   public int getNumberOfReferences() {
      return messageReferences.size();
   }

   private RoutingStatus move(final Transaction originalTX,
                              final SimpleString address,
                              final Binding binding,
                              final MessageReference ref,
                              final boolean rejectDuplicate,
                              final AckReason reason,
                              final ServerConsumer consumer,
                              final Long queueID,
                              boolean delivering) throws Exception {
      Transaction tx;

      if (originalTX != null) {
         tx = originalTX;
      } else {
         // if no TX we create a new one to commit at the end
         tx = new TransactionImpl(storageManager);
      }

      Message copyMessage = makeCopy(ref, reason == AckReason.EXPIRED, address);

      Object originalRoutingType = ref.getMessage().getBrokerProperty(Message.HDR_ORIG_ROUTING_TYPE);
      if (originalRoutingType instanceof Byte originalRoutingTypeByte) {
         copyMessage.setRoutingType(RoutingType.getType(originalRoutingTypeByte));
      }

      if (queueID != null) {
         final byte[] encodedBuffer = new byte[Long.BYTES];
         ByteBuffer buffer = ByteBuffer.wrap(encodedBuffer);
         buffer.putLong(0, queueID);
         copyMessage.putBytesProperty(Message.HDR_ROUTE_TO_IDS.toString(), encodedBuffer);
      }

      RoutingStatus routingStatus;
      {
         RoutingContext context = new RoutingContextImpl(tx);
         if (reason == AckReason.EXPIRED) {
            // we Disable mirror on expiration as the target might be also expiring it
            // and this could cause races
            // we will only send the ACK for the expiration with the reason=EXPIRE and the expire will be played on the mirror side
            context.setMirrorOption(MirrorOption.disabled);
         }

         routingStatus = postOffice.route(copyMessage, context, false, rejectDuplicate, binding);
      }

      acknowledge(tx, ref, reason, consumer, delivering);

      if (originalTX == null) {
         tx.commit();
      }

      if (server.hasBrokerMessagePlugins()) {
         server.callBrokerMessagePlugins(plugin -> plugin.messageMoved(tx, ref, reason, address, queueID, consumer, copyMessage, routingStatus));
      }

      return routingStatus;
   }

   private RoutingStatus copy(final Transaction originalTX,
                              final SimpleString address,
                              final Binding binding,
                              final MessageReference ref) throws Exception {
      Transaction tx;

      if (originalTX != null) {
         tx = originalTX;
      } else {
         // if no TX we create a new one to commit at the end
         tx = new TransactionImpl(storageManager);
      }

      Message copyMessage = makeCopy(ref, false, false, address);

      Object originalRoutingType = ref.getMessage().getBrokerProperty(Message.HDR_ORIG_ROUTING_TYPE);
      if (originalRoutingType != null && originalRoutingType instanceof Byte byteValue) {
         copyMessage.setRoutingType(RoutingType.getType(byteValue));
      }

      RoutingStatus routingStatus;
      {
         RoutingContext context = new RoutingContextImpl(tx);
         routingStatus = postOffice.route(copyMessage, context, false, false, binding);
      }

      if (originalTX == null) {
         tx.commit();
      }
      return routingStatus;
   }

   @SuppressWarnings({"ArrayToString", "ArrayToStringConcatenation"})
   private boolean moveBetweenSnFQueues(final SimpleString queueSuffix,
                                        final Transaction tx,
                                        final MessageReference ref,
                                        final SimpleString newAddress) throws Exception {
      Message copyMessage = makeCopy(ref, false, false, newAddress);

      byte[] oldRouteToIDs = null;
      String targetNodeID;
      Binding targetBinding;

      // remove the old route
      for (SimpleString propName : copyMessage.getPropertyNames()) {
         if (propName.startsWith(Message.HDR_ROUTE_TO_IDS)) {
            oldRouteToIDs = (byte[]) copyMessage.removeProperty(propName.toString());

            if (logger.isDebugEnabled()) {
               final String hashcodeToString = oldRouteToIDs.toString(); // don't use Arrays.toString(..) here
               logger.debug("Removed property from message: {} = {} ({})", propName, hashcodeToString, ByteBuffer.wrap(oldRouteToIDs).getLong());
            }

            // there should only be one of these properties so potentially save some loop iterations
            break;
         }
      }

      ByteBuffer oldBuffer = ByteBuffer.wrap(oldRouteToIDs);

      RoutingContext routingContext = new RoutingContextImpl(tx);

      /*
       * this algorithm will look at the old route and find the new remote queue bindings where the messages should go
       * and route them there directly
       */
      while (oldBuffer.hasRemaining()) {
         long oldQueueID = oldBuffer.getLong();

         // look at all the bindings
         Pair<String, Binding> result = locateTargetBinding(queueSuffix, copyMessage, oldQueueID);
         targetBinding = result.getB();
         targetNodeID = result.getA();

         if (targetBinding == null) {
            ActiveMQServerLogger.LOGGER.unableToFindTargetQueue(targetNodeID);
         } else {
            logger.debug("Routing on binding: {}", targetBinding);
            targetBinding.route(copyMessage, routingContext);
         }
      }

      postOffice.processRoute(copyMessage, routingContext, false);

      ref.handled();

      acknowledge(tx, ref);

      storageManager.afterCompleteOperations(new IOCallback() {

         @Override
         public void onError(final int errorCode, final String errorMessage) {
            ActiveMQServerLogger.LOGGER.ioErrorRedistributing(errorCode, errorMessage);
         }

         @Override
         public void done() {
            deliverAsync();
         }
      });

      return true;
   }

   private Pair<String, Binding> locateTargetBinding(SimpleString queueSuffix, Message copyMessage, long oldQueueID) {
      String targetNodeID = null;
      Binding targetBinding = null;

      // we only care about the remote queue bindings
      for (RemoteQueueBinding remoteQueueBinding : iterableOf(postOffice.getAllBindings()
                                                                 .filter(RemoteQueueBinding.class::isInstance)
                                                                 .map(RemoteQueueBinding.class::cast))) {
         // does this remote queue binding point to the same queue as the message?
         if (oldQueueID == remoteQueueBinding.getRemoteQueueID()) {
            // get the name of this queue so we can find the corresponding remote queue binding pointing to the scale down target node
            SimpleString oldQueueName = remoteQueueBinding.getRoutingName();

            // parse the queue name of the remote queue binding to determine the node ID
            String temp = remoteQueueBinding.getQueue().getName().toString();
            targetNodeID = temp.substring(temp.lastIndexOf(".") + 1);

            if (logger.isDebugEnabled()) {
               logger.debug("Message formerly destined for {} with ID: {} on address {} on node {}", oldQueueName, oldQueueID, copyMessage.getAddressSimpleString(), targetNodeID);
            }

            // now that we have the name of the queue we need to look through all the bindings again to find the new remote queue binding
            // again, we only care about the remote queue bindings
            for (RemoteQueueBinding innerRemoteQueueBinding : iterableOf(postOffice.getAllBindings()
                                                                            .filter(RemoteQueueBinding.class::isInstance)
                                                                            .map(RemoteQueueBinding.class::cast))) {
               temp = innerRemoteQueueBinding.getQueue().getName().toString();
               targetNodeID = temp.substring(temp.lastIndexOf(".") + 1);
               if (oldQueueName.equals(innerRemoteQueueBinding.getRoutingName()) && targetNodeID.equals(queueSuffix.toString())) {
                  targetBinding = innerRemoteQueueBinding;
                  if (logger.isDebugEnabled()) {
                     logger.debug("Message now destined for {} with ID: {} on address {} on node {}",
                                  innerRemoteQueueBinding.getRoutingName(), innerRemoteQueueBinding.getRemoteQueueID(), copyMessage.getAddress(), targetNodeID);
                  }
                  break;
               } else {
                  logger.debug("Failed to match: {}", innerRemoteQueueBinding);
               }
            }
         }
      }
      return new Pair<>(targetNodeID, targetBinding);
   }

   private Message makeCopy(final MessageReference ref, final boolean expiry, final SimpleString newAddress) throws Exception {
      return makeCopy(ref, expiry, true, newAddress);
   }

   private Message makeCopy(final MessageReference ref,
                            final boolean expiry,
                            final boolean copyOriginalHeaders,
                            final SimpleString newAddress) throws Exception {
      if (ref == null) {
         ActiveMQServerLogger.LOGGER.nullRefMessage();
         throw new ActiveMQNullRefException("Reference to message is null");
      }

      Message message = ref.getMessage();
      /*
       We copy the message and send that to the dla/expiry queue - this is
       because otherwise we may end up with a ref with the same message id in the
       queue more than once which would barf - this might happen if the same message had been
       expire from multiple subscriptions of a topic for example
       We set headers that hold the original message address, expiry time
       and original message id
      */

      long newID = storageManager.generateID();

      Message copy = message.copy(newID, true);

      if (newAddress != null) {
         // setting it before checkLargeMessage:
         // checkLargeMessage can cause msg encoding and setting it later invalidate it,
         // forcing to be re-encoded later
         copy.setAddress(newAddress);
      }

      if (copyOriginalHeaders) {
         copy.referenceOriginalMessage(message, ref.getQueue().getName());
      }

      copy.setExpiration(0);
      copy.setRoutingType(null);

      if (expiry) {
         copy.setBrokerProperty(Message.HDR_ACTUAL_EXPIRY_TIME, System.currentTimeMillis());
      }

      copy.reencode();

      // in some edge cases a large message can become large during the copy
      return LargeServerMessageImpl.checkLargeMessage(copy, storageManager);
   }

   private class ExpiryLogger extends TransactionOperationAbstract {

      List<Pair<SimpleString, MessageReference>> expiries = new LinkedList<>();

      public void addExpiry(SimpleString address, MessageReference ref) {
         expiries.add(new Pair<>(address, ref));
      }

      @Override
      public void afterCommit(Transaction tx) {
         for (Pair<SimpleString, MessageReference> pair : expiries) {
            try {
               server.callBrokerMessagePlugins(plugin -> plugin.messageExpired(pair.getB(), pair.getA(), null));
            } catch (Throwable e) {
               logger.warn(e.getMessage(), e);
            }
         }
         expiries.clear(); // just giving a hand to GC
      }
   }

   @Override
   public boolean sendToDeadLetterAddress(final Transaction tx, final MessageReference ref) throws Exception {
      return sendToDeadLetterAddress(tx, ref, cachedAddressSettings.getDeadLetterAddress());
   }

   private boolean sendToDeadLetterAddress(final Transaction tx,
                                           final MessageReference ref,
                                           final SimpleString deadLetterAddress) throws Exception {
      if (deadLetterAddress != null) {

         createDeadLetterResources();

         Bindings bindingList = postOffice.lookupBindingsForAddress(deadLetterAddress);

         if (bindingList == null || bindingList.getBindings().isEmpty()) {
            ActiveMQServerLogger.LOGGER.noBindingsOnDLA(ref, deadLetterAddress);
            ref.acknowledge(tx, AckReason.KILLED, null);
         } else {
            ActiveMQServerLogger.LOGGER.sendingMessageToDLA(ref, deadLetterAddress, queueConfiguration.getName());
            RoutingStatus status = move(tx, deadLetterAddress, null, ref, false, AckReason.KILLED, null, null, true);

            // this shouldn't happen, but in case it does it's better to log a message than just drop the message silently
            if (status.equals(RoutingStatus.NO_BINDINGS) && server.getAddressSettingsRepository().getMatch(getAddress().toString()).isAutoCreateDeadLetterResources()) {
               ActiveMQServerLogger.LOGGER.noMatchingBindingsOnDLAWithAutoCreateDLAResources(deadLetterAddress, ref.toString());
            }
            return true;
         }
      } else {
         ActiveMQServerLogger.LOGGER.sendingMessageToDLAnoDLA(ref, queueConfiguration.getName());
         ref.acknowledge(tx, AckReason.KILLED, null);
      }

      return false;
   }

   private void createDeadLetterResources() throws Exception {
      AddressSettings addressSettings = server.getAddressSettingsRepository().getMatch(getAddress().toString());
      createResources(String.valueOf(getAddress()), addressSettings.isAutoCreateDeadLetterResources(), addressSettings.getDeadLetterAddress(), addressSettings.getDeadLetterQueuePrefix(), addressSettings.getDeadLetterQueueSuffix());
   }

   private void createExpiryResources(String address, AddressSettings messageAddressSettings) throws Exception {
      createResources(address, messageAddressSettings.isAutoCreateExpiryResources(), messageAddressSettings.getExpiryAddress(), messageAddressSettings.getExpiryQueuePrefix(), messageAddressSettings.getExpiryQueueSuffix());
   }

   private void createResources(String address, boolean isAutoCreate, SimpleString destinationAddress, SimpleString prefix, SimpleString suffix) throws Exception {
      if (isAutoCreate && !address.equals(destinationAddress)) {
         if (destinationAddress != null && !destinationAddress.isEmpty()) {
            SimpleString destinationQueueName = prefix.concat(address).concat(suffix);
            SimpleString filter = SimpleString.of(String.format("%s = '%s'", Message.HDR_ORIGINAL_ADDRESS, address));
            try {
               logger.debug("Creating Resource queue {}", destinationQueueName);
               server.createQueue(QueueConfiguration.of(destinationQueueName).setAddress(destinationAddress).setFilterString(filter).setAutoCreated(true).setAutoCreateAddress(true), true);
            } catch (ActiveMQQueueExistsException e) {
               logger.debug("resource {} already existed, ignoring outcome", destinationQueueName);
               // ignore
            }
         }
      }
   }

   // This method delivers the reference on the callers thread - this can give us better latency in the case there is nothing in the queue
   private boolean deliverDirect(final MessageReference ref) {
      //The order to enter the deliverLock re QueueImpl::this lock is very important:
      //- acquire deliverLock::lock
      //- acquire QueueImpl::this lock
      //DeliverRunner::run is doing the same to avoid deadlocks.
      //Without deliverLock, a directDeliver happening while a DeliverRunner::run
      //could cause a deadlock.
      //Both DeliverRunner::run and deliverDirect could trigger a ServerConsumerImpl::individualAcknowledge:
      //- deliverDirect first acquire QueueImpl::this, then ServerConsumerImpl::this
      //- DeliverRunner::run first acquire ServerConsumerImpl::this then QueueImpl::this
      if (!deliverLock.tryLock()) {
         logger.trace("Cannot perform a directDelivery because there is a running async deliver");
         return false;
      }
      try {
         return deliver(ref);
      } finally {
         deliverLock.unlock();
      }
   }

   private boolean deliver(final MessageReference ref) {
      synchronized (this) {
         if (!supportsDirectDeliver) {
            return false;
         }
         if (isPaused() || !canDispatch()) {
            return false;
         }

         if (checkExpired(ref)) {
            return true;
         }

         consumers.reset();

         while (consumers.hasNext()) {

            ConsumerHolder<? extends Consumer> holder = consumers.next();
            Consumer consumer = holder.consumer;

            final SimpleString groupID = extractGroupID(ref);
            Consumer groupConsumer = getGroupConsumer(groupID);

            if (groupConsumer != null) {
               consumer = groupConsumer;
            }

            HandleStatus status = handle(ref, consumer);
            if (status == HandleStatus.HANDLED) {
               final MessageReference reference = handleMessageGroup(ref, consumer, groupConsumer, groupID);

               incrementMesssagesAdded();

               deliveriesInTransit.countUp();
               reference.setInDelivery(true);
               proceedDeliver(consumer, reference);
               consumers.reset();
               reference.setSequence(queueSequence.incrementAndGet());
               return true;
            }

            if (groupConsumer != null) {
               break;
            }
         }

         logger.trace("Queue {} is out of direct delivery as no consumers handled a delivery", queueConfiguration.getName());

         return false;
      }
   }

   private Consumer getGroupConsumer(SimpleString groupID) {
      Consumer groupConsumer = null;
      if (queueConfiguration.isExclusive()) {
         // If exclusive is set, then this overrides the consumer chosen round-robin
         groupConsumer = exclusiveConsumer;
      } else {
         // If a group id is set, then this overrides the consumer chosen round-robin
         if (groupID != null) {
            groupConsumer = groups.get(groupID);
         }
      }
      return groupConsumer;
   }

   private MessageReference handleMessageGroup(MessageReference ref, Consumer consumer, Consumer groupConsumer, SimpleString groupID) {
      if (queueConfiguration.isExclusive()) {
         if (groupConsumer == null) {
            exclusiveConsumer = consumer;
            if (queueConfiguration.getGroupFirstKey() != null) {
               return new GroupFirstMessageReference(queueConfiguration.getGroupFirstKey(), ref);
            }
         }
         consumers.repeat();
      } else if (groupID != null) {
         if (extractGroupSequence(ref) == -1) {
            groups.remove(groupID);
            consumers.repeat();
         } else if (groupConsumer == null) {
            groups.put(groupID, consumer);
            if (queueConfiguration.getGroupFirstKey() != null) {
               return new GroupFirstMessageReference(queueConfiguration.getGroupFirstKey(), ref);
            }
         } else {
            consumers.repeat();
         }
      }
      return ref;
   }

   private void proceedDeliver(Consumer consumer, MessageReference reference) {
      try {
         consumer.proceedDeliver(reference);
      } catch (Throwable t) {
         errorProcessing(consumer, t, reference);
      } finally {
         deliveriesInTransit.countDown();
      }
   }

   /**
    * This will print errors and decide what to do with the errored consumer from the protocol layer.
    */
   @Override
   public void errorProcessing(Consumer consumer, Throwable t, MessageReference reference) {
      ActiveMQServerLogger.LOGGER.removingBadConsumer(consumer, reference, t);
      executor.execute(() -> consumer.failed(t));
   }

   private boolean checkExpired(final MessageReference reference) {
      try {
         if (reference.getMessage().isExpired()) {
            logger.trace("Reference {} is expired", reference);

            reference.handled();

            try {
               expire(reference);
            } catch (Exception e) {
               ActiveMQServerLogger.LOGGER.errorExpiringRef(e);
            }

            return true;
         } else {
            return false;
         }
      } catch (Throwable e) {
         ActiveMQServerLogger.LOGGER.unableToCheckIfMessageExpired(e);
         return false;
      }
   }

   private synchronized HandleStatus handle(final MessageReference reference, final Consumer consumer) {
      HandleStatus status;
      try {
         status = consumer.handle(reference);
      } catch (Throwable t) {
         ActiveMQServerLogger.LOGGER.removingBadConsumer(consumer, reference, t);

         // If the consumer throws an exception we remove the consumer
         try {
            errorProcessing(consumer, t, reference);
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.errorRemovingConsumer(e);
         }
         return HandleStatus.BUSY;
      }

      if (status == null) {
         throw new IllegalStateException("ClientConsumer.handle() should never return null");
      }

      return status;
   }

   @Override
   public void postAcknowledge(final MessageReference ref, AckReason reason) {
      postAcknowledge(ref, reason, true);
   }

   /**
    * The parameter delivering can be sent as false in situation where the ack is coming outside of the context of
    * delivering. Example: Mirror replication will call the ack here without any consumer involved. On that case no
    * previous delivery happened, hence no information about delivering statistics should be updated.
    */
   @Override
   public void postAcknowledge(final MessageReference ref, AckReason reason, boolean delivering) {
      QueueImpl queue = (QueueImpl) ref.getQueue();

      try {
         if (delivering) {
            queue.decDelivering(ref);
         }
         if (queueConfiguration.isNonDestructive() && reason == AckReason.NORMAL) {
            // this is done to tell the difference between actual acks and just a closed consumer in the non-destructive use-case
            ref.setInDelivery(false);
            return;
         }

         if (reason == AckReason.EXPIRED) {
            messagesExpired.incrementAndGet();
         } else if (reason == AckReason.KILLED) {
            messagesKilled.incrementAndGet();
         } else if (reason == AckReason.REPLACED) {
            messagesReplaced.incrementAndGet();
         } else {
            messagesAcknowledged.incrementAndGet();
         }

         if (ref.isPaged()) {
            // nothing to be done
            return;
         }

         Message message;

         try {
            message = ref.getMessage();
         } catch (Throwable e) {
            ActiveMQServerLogger.LOGGER.unableToPerformPostAcknowledge(e);
            message = null;
         }

         if (message == null || (queueConfiguration.isNonDestructive() && reason == AckReason.NORMAL))
            return;

         queue.refDown(ref);

         boolean durableRef = message.isDurable() && queue.isDurable();

         if (durableRef) {
            int count = queue.durableDown(message);

            if (count == 0) {
               // Note - we MUST store the delete after the preceding ack has been committed to storage, we cannot combine
               // the last ack and delete into a single delete.
               // This is because otherwise we could have a situation where the same message is being acked concurrently
               // from two different queues on different sessions.
               // One decrements the ref count, then the other stores a delete, the delete gets committed, but the first
               // ack isn't committed, then the server crashes and on
               // recovery the message is deleted even though the other ack never committed

               // also note then when this happens as part of a transaction it is the tx commit of the ack that is
               // important not this

               // Also note that this delete shouldn't sync to disk, or else we would build up the executor's queue
               // as we can't delete each messaging with sync=true while adding messages transactionally.
               // There is a startup check to remove non referenced messages case these deletes fail
               try {
                  storageManager.deleteMessage(message.getMessageID());
               } catch (Exception e) {
                  ActiveMQServerLogger.LOGGER.cannotFindMessageOnJournal(message.getMessageID(), e);
               }
            }
         }
      } finally {
         postOffice.postAcknowledge(ref, reason);
      }
   }

   void postRollback(final LinkedList<MessageReference> refs) {
      //if we have purged then ignore adding the messages back
      if (queueConfiguration.isPurgeOnNoConsumers() && getConsumerCount() == 0) {
         purgeAfterRollback(refs);

         return;
      }

      // if the queue is non-destructive then any ack is ignored so no need to add messages back onto the queue
      if (!isNonDestructive()) {
         addSorted(refs, false);
      }
   }

   private void purgeAfterRollback(LinkedList<MessageReference> refs) {
      try {
         Transaction transaction = new TransactionImpl(storageManager);
         for (MessageReference reference : refs) {
            incDelivering(reference); // post ack will decrement this, so need to inc
            acknowledge(transaction, reference, AckReason.KILLED, null, true);
         }
         transaction.commit();
      } catch (Exception e) {
         logger.warn(e.getMessage(), e);
      }
   }

   private long calculateRedeliveryDelay(final AddressSettings addressSettings, final int deliveryCount) {
      long redeliveryDelay = addressSettings.getRedeliveryDelay();
      long maxRedeliveryDelay = addressSettings.getMaxRedeliveryDelay();
      double redeliveryMultiplier = addressSettings.getRedeliveryMultiplier();
      double collisionAvoidanceFactor = addressSettings.getRedeliveryCollisionAvoidanceFactor();

      int tmpDeliveryCount = deliveryCount > 0 ? deliveryCount - 1 : 0;
      long delay = (long) (redeliveryDelay * (Math.pow(redeliveryMultiplier, tmpDeliveryCount)));
      if (collisionAvoidanceFactor > 0) {
         Random random = ThreadLocalRandom.current();
         double variance = (random.nextBoolean() ? collisionAvoidanceFactor : -collisionAvoidanceFactor) * random.nextDouble();
         delay += (delay * variance);
      }

      if (delay > maxRedeliveryDelay) {
         delay = maxRedeliveryDelay;
      }

      return delay;
   }

   @Override
   public synchronized void resetMessagesAdded() {
      messagesAdded.set(0);
   }

   @Override
   public synchronized void resetMessagesAcknowledged() {
      messagesAcknowledged.set(0);
   }

   @Override
   public synchronized void resetMessagesExpired() {
      messagesExpired.set(0);
   }

   @Override
   public synchronized void resetMessagesKilled() {
      messagesKilled.set(0);
   }

   private float getRate() {
      long locaMessageAdded = getMessagesAdded();
      float timeSlice = ((System.currentTimeMillis() - queueRateCheckTime.getAndSet(System.currentTimeMillis())) / 1000.0f);
      if (timeSlice == 0) {
         messagesAddedSnapshot.getAndSet(locaMessageAdded);
         return 0.0f;
      }
      return BigDecimal.valueOf((locaMessageAdded - messagesAddedSnapshot.getAndSet(locaMessageAdded)) / timeSlice).setScale(2, BigDecimal.ROUND_UP).floatValue();
   }

   @Override
   public void recheckRefCount(OperationContext context) {
      ReferenceCounter refCount = refCountForConsumers;
      if (refCount != null) {
         context.executeOnCompletion(new IOCallback() {
            @Override
            public void done() {
               refCount.check();
            }

            @Override
            public void onError(int errorCode, String errorMessage) {

            }
         });
      }

   }

   public static MessageGroups<Consumer> groupMap(int groupBuckets) {
      if (groupBuckets == -1) {
         return new SimpleMessageGroups<>();
      } else if (groupBuckets == 0) {
         return DisabledMessageGroups.instance();
      } else {
         return new BucketMessageGroups<>(groupBuckets);
      }
   }

   @Override
   public QueueConfiguration getQueueConfiguration() {
      return QueueConfiguration.of(queueConfiguration);
   }

   protected static class ConsumerHolder<T extends Consumer> implements PriorityAware {

      ConsumerHolder(final T consumer, final QueueImpl queue) {
         this.consumer = consumer;
         this.queue = queue;
      }

      final T consumer;
      final QueueImpl queue;

      LinkedListIterator<MessageReference> iter;

      private void resetIterator() {
         if (iter != null) {
            iter.close();
         }
         iter = null;
      }

      private Consumer consumer() {
         return consumer;
      }

      @Override
      public boolean equals(Object obj) {
         if (this == obj) {
            return true;
         }
         if (!(obj instanceof ConsumerHolder<?> other)) {
            return false;
         }

         return Objects.equals(consumer, other.consumer);
      }

      @Override
      public int hashCode() {
         return Objects.hashCode(consumer);
      }

      @Override
      public int getPriority() {
         return consumer.getPriority();
      }

      @Override
      public String toString() {
         return "ConsumerHolder::queue=" + queue + ", consumer=" + consumer;
      }
   }

   private class DelayedAddRedistributor implements Runnable {

      private final ArtemisExecutor executor1;

      DelayedAddRedistributor(final ArtemisExecutor executor) {
         this.executor1 = executor;
      }

      @Override
      public void run() {
         synchronized (QueueImpl.this) {
            internalAddRedistributor();

            clearRedistributorFuture();
         }
      }
   }

   /**
    * There's no need of having multiple instances of this class. a Single instance per QueueImpl should be more than
    * sufficient. previous versions of this class were using a synchronized object. The current version is using the
    * deliverRunner instance, and to avoid confusion on the implementation I'm requesting to keep this single instanced
    * per QueueImpl.
    */
   private final class DeliverRunner implements Runnable {

      @Override
      public void run() {
         try {
            // during the transition between paging and nonpaging, we could have this using a different executor
            // and at this short period we could have more than one delivery thread running in async mode
            // this will avoid that possibility
            // We will be using the deliverRunner instance as the guard object to avoid multiple threads executing
            // an asynchronous delivery
            boolean needCheckDepage = false;
            try (ArtemisCloseable metric = measureCritical(CRITICAL_DELIVER)) {
               deliverLock.lock();
               try {
                  needCheckDepage = deliver();
               } finally {
                  deliverLock.unlock();
               }
            }

            if (needCheckDepage) {
               try (ArtemisCloseable metric = measureCritical(CRITICAL_CHECK_DEPAGE)) {
                  checkDepage();
               }
            }
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.errorDelivering(e);
         }
      }
   }

   /**
    * This will determine the actions that could be done while iterate the queue through iterQueue
    */
   abstract class QueueIterateAction {

      protected Integer expectedHits;
      protected Long messageID;
      protected Filter filter1 = null;
      protected Predicate<MessageReference> match;

      QueueIterateAction(Integer expectedHits) {
         this.expectedHits = expectedHits;
         this.match = ref -> filter1 == null ? true : filter1.match(ref.getMessage());
      }

      QueueIterateAction(Long messageID) {
         this.expectedHits = 1;
         this.match = ref -> ref.getMessage().getMessageID() == messageID;
      }

      QueueIterateAction() {
         this.expectedHits = null;
         this.match = ref -> filter1 == null ? true : filter1.match(ref.getMessage());
      }

      /**
       * The custom action to take on the message from the queue iterator.
       *
       * @param tx  the transaction which the message action should participate in
       * @param ref the message reference which the action should act upon
       * @return true if the action should result in the removal of the message from the queue; false otherwise
       */
      public abstract boolean actMessage(Transaction tx, MessageReference ref) throws Exception;

      public boolean expectedHitsReached(int currentHits) {
         return expectedHits != null && currentHits >= expectedHits.intValue();
      }

      public void addFilter(Filter filter1) {
         this.filter1 = filter1;
      }

      public boolean match(MessageReference ref) {
         return match.test(ref);
      }

   }

   // For external use we need to use a synchronized version since the list is not thread safe
   private class SynchronizedIterator implements LinkedListIterator<MessageReference> {

      private final LinkedListIterator<MessageReference> iter;

      SynchronizedIterator(LinkedListIterator<MessageReference> iter) {
         this.iter = iter;
      }

      @Override
      public void close() {
         synchronized (QueueImpl.this) {
            iter.close();
         }
      }

      @Override
      public void repeat() {
         synchronized (QueueImpl.this) {
            iter.repeat();
         }
      }

      @Override
      public boolean hasNext() {
         synchronized (QueueImpl.this) {
            return iter.hasNext();
         }
      }

      @Override
      public MessageReference next() {
         synchronized (QueueImpl.this) {
            return iter.next();
         }
      }

      @Override
      public MessageReference removeLastElement() {
         synchronized (QueueImpl.this) {
            return iter.removeLastElement();
         }
      }

      @Override
      public void remove() {
         synchronized (QueueImpl.this) {
            iter.remove();
         }
      }
   }

   //Readonly (no remove) iterator over the messages in the queue, in order of
   //paging store, intermediateMessageReferences and MessageReferences
   private final class QueueBrowserIterator implements LinkedListIterator<MessageReference> {

      LinkedListIterator<PagedReference> pagingIterator = null;
      LinkedListIterator<MessageReference> messagesIterator = null;

      private LinkedListIterator<PagedReference> getPagingIterator() {
         if (pagingIterator == null && pageSubscription != null) {
            pagingIterator = pageSubscription.iterator(true);
         }
         return pagingIterator;
      }

      LinkedListIterator<? extends MessageReference> lastIterator = null;

      MessageReference cachedNext = null;
      Set<PagePosition> previouslyBrowsed = new HashSet<>();

      private QueueBrowserIterator() {
         messagesIterator = new SynchronizedIterator(messageReferences.iterator());
      }

      @Override
      public boolean hasNext() {
         if (cachedNext != null) {
            return true;
         }

         if (messagesIterator != null) {
            MessageReference nextMessage = iterate(messagesIterator);
            if (nextMessage != null) {
               cachedNext = nextMessage;
               lastIterator = messagesIterator;
               return true;
            }
         }

         LinkedListIterator<PagedReference> pagingIterator = getPagingIterator();
         if (pagingIterator != null) {
            PagedReference nextMessage = iteratePaging(pagingIterator);
            if (nextMessage != null) {
               cachedNext = nextMessage;
               lastIterator = pagingIterator;
               return true;
            }
         }

         return false;
      }

      private PagedReference iteratePaging(LinkedListIterator<PagedReference> iterator) {
         while (iterator.hasNext()) {
            PagedReference ref = iterator.next();

            // During regular depaging we move messages from paging into QueueImpl::messageReferences
            // later on the PagingIterator will read messages from the page files
            // and this step will avoid reproducing those messages twice.
            // once we found a previouslyBrowsed message we can remove it from this list as it's no longer needed
            // since it won't be read again
            if (!previouslyBrowsed.remove(ref.getPosition())) {
               return ref;
            }
         }
         return null;
      }


      private MessageReference iterate(LinkedListIterator<MessageReference> iterator) {
         while (iterator.hasNext()) {
            MessageReference ref = iterator.next();
            if (ref.isPaged()) {
               previouslyBrowsed.add(((PagedReference)ref).getPosition());
            }
            return ref;
         }
         return null;
      }

      @Override
      public MessageReference next() {

         if (cachedNext != null) {
            try {
               return cachedNext;
            } finally {
               cachedNext = null;
            }

         }

         if (messagesIterator != null && messagesIterator.hasNext()) {
            MessageReference ref = iterate(messagesIterator);
            if (ref != null) {
               return ref;
            }
         }

         LinkedListIterator<PagedReference> pagingIterator = getPagingIterator();
         if (pagingIterator != null) {
            PagedReference ref = iteratePaging(pagingIterator);
            if (ref != null) {
               return ref;
            }
         }

         throw new NoSuchElementException();
      }

      @Override
      public void remove() {
         if (lastIterator != null) {
            lastIterator.remove();
         }
      }

      @Override
      public MessageReference removeLastElement() {
         if (lastIterator != null) {
            return lastIterator.removeLastElement();
         } else {
            return null;
         }
      }

      @Override
      public void repeat() {
      }

      @Override
      public void close() {
         if (getPagingIterator() != null) {
            getPagingIterator().close();
         }
         if (messagesIterator != null) {
            messagesIterator.close();
         }
      }
   }

   public void incDelivering(MessageReference ref) {
      deliveringMetrics.incrementMetrics(ref);
   }

   public void decDelivering(final MessageReference reference) {
      deliveringMetrics.decrementMetrics(reference);
      if (pageDelivered) {
         // We check for async delivery after acks in case paging stopped for lack of space
         deliverAsync();
      }
   }

   private long getPersistentSize(final MessageReference reference) {
      long size = 0;

      try {
         size = reference.getPersistentSize() > 0 ? reference.getPersistentSize() : 0;
      } catch (Throwable e) {
         ActiveMQServerLogger.LOGGER.errorCalculatePersistentSize(e);
      }

      return size;
   }

   private void configureSlowConsumerReaper() {
      if (cachedAddressSettings == null || cachedAddressSettings.getSlowConsumerThreshold() == AddressSettings.DEFAULT_SLOW_CONSUMER_THRESHOLD) {
         if (slowConsumerReaperFuture != null) {
            slowConsumerReaperFuture.cancel(false);
            slowConsumerReaperFuture = null;
            slowConsumerReaperRunnable = null;

            logger.debug("Cancelled slow-consumer-reaper thread for queue \"{}\"", queueConfiguration.getName());
         }
      } else {
         if (slowConsumerReaperRunnable == null) {
            scheduleSlowConsumerReaper(cachedAddressSettings);
         } else if (slowConsumerReaperRunnable.checkPeriod != cachedAddressSettings.getSlowConsumerCheckPeriod() || slowConsumerReaperRunnable.thresholdInMsgPerSecond != cachedAddressSettings.getSlowConsumerThreshold() || !slowConsumerReaperRunnable.policy.equals(cachedAddressSettings.getSlowConsumerPolicy())) {
            if (slowConsumerReaperFuture != null) {
               slowConsumerReaperFuture.cancel(false);
               slowConsumerReaperFuture = null;
            }
            scheduleSlowConsumerReaper(cachedAddressSettings);
         }
      }
   }

   void scheduleSlowConsumerReaper(AddressSettings settings) {
      slowConsumerReaperRunnable = new SlowConsumerReaperRunnable(settings.getSlowConsumerCheckPeriod(), settings.getSlowConsumerThreshold(), settings.getSlowConsumerThresholdMeasurementUnit(), settings.getSlowConsumerPolicy());

      slowConsumerReaperFuture = scheduledExecutor.scheduleWithFixedDelay(slowConsumerReaperRunnable, settings.getSlowConsumerCheckPeriod(), settings.getSlowConsumerCheckPeriod(), TimeUnit.SECONDS);

      if (logger.isDebugEnabled()) {
         logger.debug("Scheduled slow-consumer-reaper thread for queue \"{}\"; slow-consumer-check-period={}, slow-consumer-threshold={}, slow-consumer-threshold-measurement-unit={}, slow-consumer-policy={}",
                      queueConfiguration.getName(), settings.getSlowConsumerCheckPeriod(), settings.getSlowConsumerThreshold(), settings.getSlowConsumerThresholdMeasurementUnit(), settings.getSlowConsumerPolicy());
      }
   }

   private void enforceRing(boolean head) {
      if (queueConfiguration.getRingSize() != -1) { // better escaping & inlining when ring isn't being used
         enforceRing(null, false, head);
      }
   }

   private void enforceRing(MessageReference refToAck, boolean scheduling, boolean head) {
      int adjustment = head ? 1 : 0;

      if (getPendingMessageCount() + adjustment > queueConfiguration.getRingSize()) {
         refToAck = refToAck == null ? messageReferences.poll() : refToAck;

         if (refToAck != null) {
            if (logger.isDebugEnabled()) {
               logger.debug("Preserving ringSize {} by acking message ref {}", queueConfiguration.getRingSize(), refToAck);
            }
            referenceHandled(refToAck);

            try {
               refToAck.acknowledge(null, AckReason.REPLACED, null);
               if (!refToAck.isInDelivery() && !scheduling) {
                  refRemoved(refToAck);
               }
               refToAck.setAlreadyAcked();
            } catch (Exception e) {
               ActiveMQServerLogger.LOGGER.errorAckingOldReference(e);
            }
         } else {
            if (logger.isDebugEnabled()) {
               logger.debug("Cannot preserve ringSize {}; message ref is null", queueConfiguration.getRingSize());
            }
         }
      }
   }

   private void checkDeadLetterAddressAndExpiryAddress() {
      if (!Env.isTestEnv() && !queueConfiguration.isInternal() && !queueConfiguration.getAddress().equals(server.getConfiguration().getManagementNotificationAddress())) {
         if (cachedAddressSettings.getDeadLetterAddress() == null) {
            ActiveMQServerLogger.LOGGER.AddressSettingsNoDLA(queueConfiguration.getName());
         }
         if (cachedAddressSettings.getExpiryAddress() == null) {
            ActiveMQServerLogger.LOGGER.AddressSettingsNoExpiryAddress(queueConfiguration.getName());
         }
      }
   }

   private final class SlowConsumerReaperRunnable implements Runnable {

      private final SlowConsumerPolicy policy;
      private final float thresholdInMsgPerSecond;
      private final long checkPeriod;

      private SlowConsumerReaperRunnable(long checkPeriod, float slowConsumerThreshold, SlowConsumerThresholdMeasurementUnit unit, SlowConsumerPolicy policy) {
         this.checkPeriod = checkPeriod;
         this.policy = policy;
         this.thresholdInMsgPerSecond = slowConsumerThreshold / unit.getValue();
      }

      @Override
      public void run() {
         final float queueRate = getRate();
         final long queueMessages = getMessageCount();

         if (logger.isDebugEnabled()) {
            logger.debug("{}:{} has {} message(s) and {} consumer(s) and is receiving messages at a rate of {} msgs/second.", queueConfiguration.getAddress(), queueConfiguration.getName(), queueMessages, getConsumerCount(), queueRate);
         }

         final int consumerCount = getConsumerCount();
         if (consumerCount == 0) {
            logger.debug("There are no consumers, no need to check slow consumer's rate");
            return;
         } else {
            float queueThreshold = thresholdInMsgPerSecond * consumerCount;

            if (queueRate < queueThreshold && queueMessages < queueThreshold) {
               logger.debug("Insufficient messages received on queue \"{}\" to satisfy slow-consumer-threshold. Skipping inspection of consumer.", queueConfiguration.getName());
               return;
            }
         }

         for (ConsumerHolder consumerHolder : consumers) {
            Consumer consumer = consumerHolder.consumer();
            if (consumer instanceof ServerConsumerImpl serverConsumer) {
               float consumerRate = serverConsumer.getRate();
               if (consumerRate < thresholdInMsgPerSecond || (consumerRate == 0 && thresholdInMsgPerSecond == 0)) {
                  RemotingConnection connection = null;
                  ActiveMQServer server = ((PostOfficeImpl) postOffice).getServer();
                  RemotingService remotingService = server.getRemotingService();

                  for (RemotingConnection potentialConnection : remotingService.getConnections()) {
                     if (potentialConnection.getID().toString().equals(String.valueOf(serverConsumer.getConnectionID()))) {
                        connection = potentialConnection;
                     }
                  }

                  serverConsumer.fireSlowConsumer();

                  if (connection != null) {
                     ActiveMQServerLogger.LOGGER.slowConsumerDetected(serverConsumer.getSessionID(), serverConsumer.getID(), getName().toString(), connection.getRemoteAddress(),
                                                                      thresholdInMsgPerSecond, consumerRate);
                     if (policy.equals(SlowConsumerPolicy.KILL)) {
                        connection.killMessage(server.getNodeID());
                        remotingService.removeConnection(connection.getID());
                        connection.fail(ActiveMQMessageBundle.BUNDLE.connectionsClosedByManagement(connection.getRemoteAddress()));
                     } else if (policy.equals(SlowConsumerPolicy.NOTIFY)) {
                        TypedProperties props = new TypedProperties();

                        props.putIntProperty(ManagementHelper.HDR_CONSUMER_COUNT, getConsumerCount());

                        props.putSimpleStringProperty(ManagementHelper.HDR_ADDRESS, queueConfiguration.getAddress());

                        props.putSimpleStringProperty(ManagementHelper.HDR_REMOTE_ADDRESS, SimpleString.of(connection.getRemoteAddress()));

                        if (connection.getID() != null) {
                           props.putSimpleStringProperty(ManagementHelper.HDR_CONNECTION_NAME, SimpleString.of(connection.getID().toString()));
                        }

                        props.putLongProperty(ManagementHelper.HDR_CONSUMER_NAME, serverConsumer.getID());

                        props.putSimpleStringProperty(ManagementHelper.HDR_SESSION_NAME, SimpleString.of(serverConsumer.getSessionID()));

                        Notification notification = new Notification(null, CoreNotificationType.CONSUMER_SLOW, props);

                        ManagementService managementService = ((PostOfficeImpl) postOffice).getServer().getManagementService();
                        try {
                           managementService.sendNotification(notification);
                        } catch (Exception e) {
                           ActiveMQServerLogger.LOGGER.failedToSendSlowConsumerNotification(notification, e);
                        }
                     }
                  }
               }
            }
         }
      }
   }
}
