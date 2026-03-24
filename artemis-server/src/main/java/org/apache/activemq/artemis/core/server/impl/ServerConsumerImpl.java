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

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQIllegalStateException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.management.CoreNotificationType;
import org.apache.activemq.artemis.api.core.management.ManagementHelper;
import org.apache.activemq.artemis.core.client.impl.ClientConsumerImpl;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.message.LargeBodyReader;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.QueueBinding;
import org.apache.activemq.artemis.core.protocol.core.CoreRemotingConnection;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.core.server.ActiveMQMessageBundle;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.CoreLargeServerMessage;
import org.apache.activemq.artemis.core.server.HandleStatus;
import org.apache.activemq.artemis.core.server.LargeServerMessage;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.server.SlowConsumerDetectionListener;
import org.apache.activemq.artemis.core.server.management.ManagementService;
import org.apache.activemq.artemis.core.server.management.Notification;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.core.transaction.impl.TransactionImpl;
import org.apache.activemq.artemis.logs.AuditLogger;
import org.apache.activemq.artemis.spi.core.protocol.SessionCallback;
import org.apache.activemq.artemis.spi.core.remoting.ReadyListener;
import org.apache.activemq.artemis.utils.FutureLatch;
import org.apache.activemq.artemis.utils.ReusableLatch;
import org.apache.activemq.artemis.utils.collections.LinkedListIterator;
import org.apache.activemq.artemis.utils.collections.TypedProperties;
import org.jboss.logging.Logger;

/**
 * Concrete implementation of a ClientConsumer.
 */
public class ServerConsumerImpl implements ServerConsumer, ReadyListener {


   private static final Logger logger = Logger.getLogger(ServerConsumerImpl.class);


   private final long id;

   private final long sequentialID;

   protected final Queue messageQueue;

   private final Filter filter;

   private final int priority;

   private final int minLargeMessageSize;

   private final ServerSession session;

   protected final Object lock = new Object();

   private final boolean supportLargeMessage;

   private Object protocolData;

   private Object protocolContext;

   private final ActiveMQServer server;

   private SlowConsumerDetectionListener slowConsumerListener;

   private final ReusableLatch pendingDelivery = new ReusableLatch(0);

   private volatile AtomicInteger availableCredits = new AtomicInteger(0);

   private boolean started;

   private volatile CoreLargeMessageDeliverer largeMessageDeliverer = null;

   @Override
   public String debug() {
      String debug = toString() + "::Delivering ";
      synchronized (lock) {
         return debug + this.deliveringRefs.size();
      }
   }

   /**
    * if we are a browse only consumer we don't need to worry about acknowledgements or being
    * started/stopped by the session.
    */
   private final boolean browseOnly;

   protected BrowserDeliverer browserDeliverer;

   private final boolean strictUpdateDeliveryCount;

   private final StorageManager storageManager;

   private final java.util.Deque<MessageReference> deliveringRefs = new ArrayDeque<>();

   private final SessionCallback callback;

   private boolean preAcknowledge;

   private final ManagementService managementService;

   private final Binding binding;

   private boolean transferring = false;

   private final long creationTime;

   private AtomicLong consumerRateCheckTime = new AtomicLong(System.currentTimeMillis());

   private AtomicLong messageConsumedSnapshot = new AtomicLong(0);

   private long acks;

   private boolean requiresLegacyPrefix = false;

   private boolean anycast = false;

   private boolean isClosed = false;


   public ServerConsumerImpl(final long id,
                             final ServerSession session,
                             final QueueBinding binding,
                             final Filter filter,
                             final boolean started,
                             final boolean browseOnly,
                             final StorageManager storageManager,
                             final SessionCallback callback,
                             final boolean preAcknowledge,
                             final boolean strictUpdateDeliveryCount,
                             final ManagementService managementService,
                             final ActiveMQServer server) throws Exception {
      this(id, session, binding, filter, started, browseOnly, storageManager, callback, preAcknowledge, strictUpdateDeliveryCount, managementService, true, null, server);
   }

   public ServerConsumerImpl(final long id,
                             final ServerSession session,
                             final QueueBinding binding,
                             final Filter filter,
                             final boolean started,
                             final boolean browseOnly,
                             final StorageManager storageManager,
                             final SessionCallback callback,
                             final boolean preAcknowledge,
                             final boolean strictUpdateDeliveryCount,
                             final ManagementService managementService,
                             final boolean supportLargeMessage,
                             final Integer credits,
                             final ActiveMQServer server) throws Exception {
      this(id, session, binding, filter, ActiveMQDefaultConfiguration.getDefaultConsumerPriority(), started, browseOnly, storageManager, callback, preAcknowledge, strictUpdateDeliveryCount, managementService, supportLargeMessage, credits, server);
   }

   public ServerConsumerImpl(final long id,
                             final ServerSession session,
                             final QueueBinding binding,
                             final Filter filter,
                             final int priority,
                             final boolean started,
                             final boolean browseOnly,
                             final StorageManager storageManager,
                             final SessionCallback callback,
                             final boolean preAcknowledge,
                             final boolean strictUpdateDeliveryCount,
                             final ManagementService managementService,
                             final boolean supportLargeMessage,
                             final Integer credits,
                             final ActiveMQServer server) throws Exception {
      this.id = id;

      this.sequentialID = server.getStorageManager().generateID();

      this.filter = filter;

      this.priority = priority;

      this.session = session;

      this.binding = binding;

      messageQueue = binding.getQueue();

      this.started = browseOnly || started;

      this.browseOnly = browseOnly;

      this.storageManager = storageManager;

      this.callback = callback;

      this.preAcknowledge = preAcknowledge;

      this.managementService = managementService;

      minLargeMessageSize = session.getMinLargeMessageSize();

      this.strictUpdateDeliveryCount = strictUpdateDeliveryCount;

      this.creationTime = System.currentTimeMillis();

      this.supportLargeMessage = supportLargeMessage;

      if (credits != null) {
         if (credits == -1) {
            availableCredits = null;
         } else {
            availableCredits.set(credits);
         }
      }

      this.server = server;

      if (browseOnly) {
         browserDeliverer = new BrowserDeliverer(messageQueue.browserIterator());
      } else {
         messageQueue.addConsumer(this);
      }

      if (session.getRemotingConnection() instanceof CoreRemotingConnection) {
         CoreRemotingConnection coreRemotingConnection = (CoreRemotingConnection) session.getRemotingConnection();
         if (session.getMetaData(ClientSession.JMS_SESSION_IDENTIFIER_PROPERTY) != null && coreRemotingConnection.getChannelVersion() < PacketImpl.ADDRESSING_CHANGE_VERSION) {
            requiresLegacyPrefix = true;
            if (getQueue().getRoutingType().equals(RoutingType.ANYCAST)) {
               anycast = true;
            }
         }
      }
   }

   @Override
   public void readyForWriting() {
      promptDelivery();
   }

   // ServerConsumer implementation
   // ----------------------------------------------------------------------


   @Override
   public boolean allowReferenceCallback() {
      if (browseOnly) {
         return false;
      } else {
         return messageQueue.allowsReferenceCallback();
      }
   }

   @Override
   public long sequentialID() {
      return sequentialID;
   }

   @Override
   public Object getProtocolData() {
      return protocolData;
   }

   @Override
   public void setProtocolData(Object protocolData) {
      this.protocolData = protocolData;
   }

   @Override
   public void setlowConsumerDetection(SlowConsumerDetectionListener listener) {
      this.slowConsumerListener = listener;
   }

   @Override
   public SlowConsumerDetectionListener getSlowConsumerDetecion() {
      return slowConsumerListener;
   }

   @Override
   public void fireSlowConsumer() {
      if (slowConsumerListener != null) {
         slowConsumerListener.onSlowConsumer(this);
      }
   }

   @Override
   public Object getProtocolContext() {
      return protocolContext;
   }

   @Override
   public void setProtocolContext(Object protocolContext) {
      this.protocolContext = protocolContext;
   }

   @Override
   public long getID() {
      return id;
   }

   @Override
   public boolean isBrowseOnly() {
      return browseOnly;
   }

   @Override
   public long getCreationTime() {
      return creationTime;
   }

   @Override
   public String getConnectionID() {
      return this.session.getConnectionID().toString();
   }

   @Override
   public String getSessionID() {
      return this.session.getName();
   }

   @Override
   public List<MessageReference> getDeliveringMessages() {
      synchronized (lock) {
         int expectedSize = 0;
         List<MessageReference> refsOnConsumer = session.getInTXMessagesForConsumer(this.id);
         if (refsOnConsumer != null) {
            expectedSize = refsOnConsumer.size();
         }
         expectedSize += deliveringRefs.size();
         final List<MessageReference> refs = new ArrayList<>(expectedSize);
         if (refsOnConsumer != null) {
            refs.addAll(refsOnConsumer);
         }
         refs.addAll(deliveringRefs);
         return refs;
      }
   }

   /** i
    *
    * @see SessionCallback#supportsDirectDelivery()
    */
   @Override
   public boolean supportsDirectDelivery() {
      return callback.supportsDirectDelivery();
   }

   @Override
   public void errorProcessing(Throwable e, MessageReference deliveryObject) {
      messageQueue.errorProcessing(this, e, deliveryObject);
   }

   @Override
   public HandleStatus handle(final MessageReference ref) throws Exception {
      // available credits can be set back to null with a flow control option.
      AtomicInteger checkInteger = availableCredits;
      if (callback != null && !callback.hasCredits(this) || checkInteger != null && checkInteger.get() <= 0) {
         if (logger.isDebugEnabled()) {
            logger.debug(this + " is busy for the lack of credits. Current credits = " +
                            availableCredits +
                            " Can't receive reference " +
                            ref);
         }

         return HandleStatus.BUSY;
      }
      if (server.hasBrokerMessagePlugins() && !server.callBrokerMessagePluginsCanAccept(this, ref)) {
         if (logger.isTraceEnabled()) {
            logger.trace("Reference " + ref + " is not allowed to be consumed by " + this + " due to message plugin filter.");
         }
         return HandleStatus.NO_MATCH;
      }

      synchronized (lock) {
         // If the consumer is stopped then we don't accept the message, it
         // should go back into the
         // queue for delivery later.
         // TCP-flow control has to be done first than everything else otherwise we may lose notifications
         if ((callback != null && !callback.isWritable(this, protocolContext)) || !started || transferring) {
            return HandleStatus.BUSY;
         }

         // If there is a pendingLargeMessage we can't take another message
         // This has to be checked inside the lock as the set to null is done inside the lock
         if (largeMessageDeliverer != null) {
            if (logger.isDebugEnabled()) {
               logger.debug(this + " is busy delivering large message " +
                               largeMessageDeliverer +
                               ", can't deliver reference " +
                               ref);
            }
            return HandleStatus.BUSY;
         }
         final Message message = ref.getMessage();

         if (!message.acceptsConsumer(sequentialID())) {
            return HandleStatus.NO_MATCH;
         }

         if (filter != null && !filter.match(message)) {
            if (logger.isTraceEnabled()) {
               logger.trace("Reference " + ref + " is a noMatch on consumer " + this);
            }
            return HandleStatus.NO_MATCH;
         }

         if (logger.isTraceEnabled()) {
            logger.trace("ServerConsumerImpl::" + this + " Handling reference " + ref);
         }
         if (!browseOnly) {
            if (!preAcknowledge) {
               deliveringRefs.add(ref);
            }

            ref.handled();

            ref.setConsumerId(this.id);

            ref.incrementDeliveryCount();

            // If updateDeliveries = false (set by strict-update),
            // the updateDeliveryCountAfterCancel would still be updated after c
            if (strictUpdateDeliveryCount && !ref.isPaged()) {
               if (ref.getMessage().isDurable() && ref.getQueue().isDurable() &&
                  !ref.getQueue().isInternalQueue() &&
                  !ref.isPaged()) {
                  storageManager.updateDeliveryCount(ref);
               }
            }

            // The deliverer will increase the usageUp, so the preAck has to be done after this is created
            // otherwise we may have a removed message early on
            if (message instanceof CoreLargeServerMessage && this.supportLargeMessage) {
               largeMessageDeliverer = new CoreLargeMessageDeliverer((LargeServerMessage) message, ref);
            }

            if (preAcknowledge) {
               // With pre-ack, we ack *before* sending to the client
               ref.getQueue().acknowledge(ref, this);
               acks++;
            }

         }

         pendingDelivery.countUp();

         return HandleStatus.HANDLED;
      }
   }

   @Override
   public void proceedDeliver(MessageReference reference) throws Exception {
      try {
         Message message = reference.getMessage();

         if (AuditLogger.isMessageLoggingEnabled()) {
            AuditLogger.coreConsumeMessage(session.getRemotingConnection().getAuditSubject(), session.getRemotingConnection().getRemoteAddress(), getQueueName().toString(), reference.toString());
         }
         if (server.hasBrokerMessagePlugins()) {
            server.callBrokerMessagePlugins(plugin -> plugin.beforeDeliver(this, reference));
         }

         if (message instanceof CoreLargeServerMessage && supportLargeMessage) {
            if (largeMessageDeliverer == null) {
               // This can't really happen as handle had already crated the deliverer
               // instead of throwing an exception in weird cases there is no problem on just go ahead and create it
               // again here
               largeMessageDeliverer = new CoreLargeMessageDeliverer((LargeServerMessage) message, reference);
            }
            // The deliverer was prepared during handle, as we can't have more than one pending large message
            // as it would return busy if there is anything pending
            largeMessageDeliverer.deliver();
         } else {
            deliverStandardMessage(reference, message);
         }
      } finally {
         pendingDelivery.countDown();
         callback.afterDelivery();
         if (server.hasBrokerMessagePlugins()) {
            server.callBrokerMessagePlugins(plugin -> plugin.afterDeliver(this, reference));
         }
      }

   }

   @Override
   public Filter getFilter() {
      return filter;
   }

   @Override
   public int getPriority() {
      return priority;
   }

   @Override
   public SimpleString getFilterString() {
      return filter == null ? null : filter.getFilterString();
   }

   @Override
   public synchronized void close(final boolean failed) throws Exception {

      // Close should only ever be done once per consumer.
      if (isClosed) return;
      isClosed = true;

      if (logger.isTraceEnabled()) {
         logger.trace("ServerConsumerImpl::" + this + " being closed with failed=" + failed, new Exception("trace"));
      }

      if (server.hasBrokerConsumerPlugins()) {
         server.callBrokerConsumerPlugins(plugin -> plugin.beforeCloseConsumer(this, failed));
      }

      setStarted(false);

      CoreLargeMessageDeliverer del = largeMessageDeliverer;

      if (del != null) {
         del.finish();
      }

      removeItself();

      List<MessageReference> refs = cancelRefs(failed, false, null);

      Transaction tx = new TransactionImpl(storageManager);

      refs.forEach(ref -> {
         if (logger.isTraceEnabled()) {
            logger.trace("ServerConsumerImpl::" + this + " cancelling reference " + ref);
         }
         ref.getQueue().cancel(tx, ref, true);
      });

      tx.rollback();

      addLingerRefs();

      if (!browseOnly) {
         TypedProperties props = new TypedProperties();

         props.putSimpleStringProperty(ManagementHelper.HDR_ADDRESS, binding.getAddress());

         props.putSimpleStringProperty(ManagementHelper.HDR_CLUSTER_NAME, binding.getClusterName());

         props.putSimpleStringProperty(ManagementHelper.HDR_ROUTING_NAME, binding.getRoutingName());

         props.putSimpleStringProperty(ManagementHelper.HDR_FILTERSTRING, filter == null ? null : filter.getFilterString());

         props.putIntProperty(ManagementHelper.HDR_DISTANCE, binding.getDistance());

         props.putIntProperty(ManagementHelper.HDR_CONSUMER_COUNT, messageQueue.getConsumerCount());

         // HORNETQ-946
         props.putSimpleStringProperty(ManagementHelper.HDR_USER, SimpleString.toSimpleString(session.getUsername()));

         props.putSimpleStringProperty(ManagementHelper.HDR_REMOTE_ADDRESS, SimpleString.toSimpleString(((ServerSessionImpl) session).getRemotingConnection().getRemoteAddress()));

         props.putSimpleStringProperty(ManagementHelper.HDR_SESSION_NAME, SimpleString.toSimpleString(session.getName()));

         Notification notification = new Notification(null, CoreNotificationType.CONSUMER_CLOSED, props);

         managementService.sendNotification(notification);
      }


      // The check here has to be done after the notification is sent, otherwise the queue will be removed before the consumer.close reach other nodes on a cluster
      messageQueue.recheckRefCount(session.getSessionContext());

      if (server.hasBrokerConsumerPlugins()) {
         server.callBrokerConsumerPlugins(plugin -> plugin.afterCloseConsumer(this, failed));
      }
   }

   private void addLingerRefs() throws Exception {
      if (!browseOnly) {
         List<MessageReference> lingerRefs = session.getInTXMessagesForConsumer(this.id);
         if (lingerRefs != null && !lingerRefs.isEmpty()) {
            session.addLingerConsumer(this);
         }
      }
   }

   @Override
   public void removeItself() throws Exception {
      if (browseOnly) {
         browserDeliverer.close();
      } else {
         messageQueue.removeConsumer(this);
      }

      session.removeConsumer(id);
   }

   /**
    * Prompt delivery and send a "forced delivery" message to the consumer.
    * <p>
    * When the consumer receives such a "forced delivery" message, it discards it and knows that
    * there are no other messages to be delivered.
    */
   @Override
   public void forceDelivery(final long sequence)  {
      forceDelivery(sequence, () -> {
         Message forcedDeliveryMessage = new CoreMessage(storageManager.generateID(), 50);
         MessageReference reference = MessageReference.Factory.createReference(forcedDeliveryMessage, messageQueue);
         reference.setDeliveryCount(0);

         forcedDeliveryMessage.putLongProperty(ClientConsumerImpl.FORCED_DELIVERY_MESSAGE, sequence);
         forcedDeliveryMessage.setAddress(messageQueue.getName());

         applyPrefixForLegacyConsumer(forcedDeliveryMessage);
         callback.sendMessage(reference, forcedDeliveryMessage, ServerConsumerImpl.this, 0);
      });
   }

   public synchronized void forceDelivery(final long sequence, final Runnable r) {
      promptDelivery();

      // JBPAPP-6030 - Using the executor to avoid distributed dead locks
      messageQueue.getExecutor().execute(new Runnable() {
         @Override
         public void run() {
            try {
               // We execute this on the same executor to make sure the force delivery message is written after
               // any delivery is completed

               synchronized (lock) {
                  if (transferring) {
                     // Case it's transferring (reattach), we will retry later
                     messageQueue.getExecutor().execute(new Runnable() {
                        @Override
                        public void run() {
                           forceDelivery(sequence, r);
                        }
                     });
                     return;
                  }
               }
               r.run();
            } catch (Exception e) {
               ActiveMQServerLogger.LOGGER.errorSendingForcedDelivery(e);
            }
         }
      });
   }

   @Override
   public List<MessageReference> cancelRefs(final boolean failed,
                                            final boolean lastConsumedAsDelivered,
                                            final Transaction tx) throws Exception {
      boolean performACK = lastConsumedAsDelivered;

      try {
         CoreLargeMessageDeliverer pendingLargeMessageDeliverer = largeMessageDeliverer;
         if (pendingLargeMessageDeliverer != null) {
            pendingLargeMessageDeliverer.finish();
         }
      } catch (Throwable e) {
         ActiveMQServerLogger.LOGGER.errorResttingLargeMessage(e, largeMessageDeliverer);
      } finally {
         largeMessageDeliverer = null;
      }

      synchronized (lock) {
         if (deliveringRefs.isEmpty()) {
            return Collections.emptyList();
         }
         final List<MessageReference> refs = new ArrayList<>(deliveringRefs.size());
         MessageReference ref;
         while ((ref = deliveringRefs.poll()) != null) {
            if (performACK) {
               ref.acknowledge(tx, this);

               performACK = false;
            } else {
               refs.add(ref);
               updateDeliveryCountForCanceledRef(ref, failed);
            }

            if (logger.isTraceEnabled()) {
               logger.trace("ServerConsumerImpl::" + this + " Preparing Cancelling list for messageID = " + ref.getMessage().getMessageID() + ", ref = " + ref);
            }
         }

         return refs;
      }

   }

   protected void updateDeliveryCountForCanceledRef(MessageReference ref, boolean failed) {
      // We first update the deliveryCount at the protocol callback...
      // if that wasn't updated (if there is no specific logic, then we apply the default logic used on most protocols
      if (!callback.updateDeliveryCountAfterCancel(this, ref, failed)) {
         if (!failed) {
            // We don't decrement delivery count if the client failed, since there's a possibility that refs
            // were actually delivered but we just didn't get any acks for them
            // before failure
            ref.decrementDeliveryCount();
         }
      }
   }

   @Override
   public void setStarted(final boolean started) {
      synchronized (lock) {
         this.started = browseOnly || started;
      }

      // Outside the lock
      if (started) {
         promptDelivery();
      } else {
         flushDelivery();
      }
   }

   private boolean flushDelivery() {
      try {
         if (!pendingDelivery.await(30, TimeUnit.SECONDS)) {
            ActiveMQServerLogger.LOGGER.timeoutLockingConsumer(this.toString(), session.getRemotingConnection().getTransportConnection().getRemoteAddress());
            if (server != null) {
               server.threadDump();
            }
            return false;
         }
         return true;
      } catch (Exception e) {
         ActiveMQServerLogger.LOGGER.failedToFinishDelivery(e);
         return false;
      }
   }

   @Override
   public void setTransferring(final boolean transferring) {
      synchronized (lock) {
         this.transferring = transferring;
      }

      // Outside the lock
      if (transferring) {
         // And we must wait for any force delivery to be executed - this is executed async so we add a future to the
         // executor and
         // wait for it to complete

         FutureLatch future = new FutureLatch();

         messageQueue.getExecutor().execute(future);

         boolean ok = future.await(10000);

         if (!ok) {
            ActiveMQServerLogger.LOGGER.errorTransferringConsumer();
         }
      }

      if (!transferring) {
         promptDelivery();
      } else {
         flushDelivery();
      }
   }

   @Override
   public void receiveCredits(final int credits) {
      if (credits == -1) {
         if (logger.isDebugEnabled()) {
            logger.debug(this + ":: FlowControl::Received disable flow control message");
         }
         // No flow control
         availableCredits = null;

         // There may be messages already in the queue
         promptDelivery();
      } else if (credits == 0) {
         // reset, used on slow consumers
         logger.debug(this + ":: FlowControl::Received reset flow control message");
         availableCredits.set(0);
      } else {
         int previous = availableCredits.getAndAdd(credits);

         if (logger.isDebugEnabled()) {
            logger.debug(this + "::FlowControl::Received " +
                            credits +
                            " credits, previous value = " +
                            previous +
                            " currentValue = " +
                            availableCredits.get());
         }

         if (previous <= 0 && previous + credits > 0) {
            if (logger.isTraceEnabled()) {
               logger.trace(this + "::calling promptDelivery from receiving credits");
            }
            promptDelivery();
         }
      }
   }

   @Override
   public Queue getQueue() {
      return messageQueue;
   }

   /**
    * Remove references based on the protocolData.
    * there will be an interval defined between protocolDataStart and protocolDataEnd.
    * This method will fetch the delivering references, remove them from the delivering list and return a list.
    *
    * This will be useful for other protocols that will need this such as openWire or MQTT.
    */
   @Override
   public synchronized List<MessageReference> scanDeliveringReferences(boolean remove,
                                                                        Function<MessageReference, Boolean> startFunction,
                                                                        Function<MessageReference, Boolean> endFunction) {
      LinkedList<MessageReference> retReferences = new LinkedList<>();
      boolean hit = false;
      synchronized (lock) {
         Iterator<MessageReference> referenceIterator = deliveringRefs.iterator();

         while (referenceIterator.hasNext()) {
            MessageReference reference = referenceIterator.next();
            if (!hit && startFunction.apply(reference)) {
               hit = true;
            }

            if (hit) {
               if (remove) {
                  referenceIterator.remove();
               }

               retReferences.add(reference);

               if (endFunction.apply(reference)) {
                  break;
               }
            }

         }
      }

      return retReferences;
   }

   @Override
   public synchronized List<Long> acknowledge(Transaction tx, final long messageID) throws Exception {
      if (browseOnly) {
         return null;
      }

      List<Long> ackedRefs = null;

      // Acknowledge acknowledges all refs delivered by the consumer up to and including the one explicitly
      // acknowledged

      // We use a transaction here as if the message is not found, we should rollback anything done
      // This could eventually happen on retries during transactions, and we need to make sure we don't ACK things we are not supposed to acknowledge

      boolean startedTransaction = false;

      if (tx == null) {
         startedTransaction = true;
         tx = new TransactionImpl(storageManager);
      }

      try {

         MessageReference ref;
         ackedRefs = new ArrayList<>();
         do {
            synchronized (lock) {
               ref = deliveringRefs.poll();
            }

            if (logger.isTraceEnabled()) {
               logger.trace("ACKing ref " + ref + " on tx= " + tx + ", consumer=" + this);
            }

            if (ref == null) {
               ActiveMQIllegalStateException ils = ActiveMQMessageBundle.BUNDLE.consumerNoReference(id, messageID, messageQueue.getName());
               tx.markAsRollbackOnly(ils);
               throw ils;
            }

            ref.acknowledge(tx, this);
            ackedRefs.add(ref.getMessageID());

            acks++;
         }
         while (ref.getMessageID() != messageID);

         if (startedTransaction) {
            tx.commit();
         }
      } catch (ActiveMQException e) {
         if (startedTransaction) {
            tx.rollback();
         } else {
            tx.markAsRollbackOnly(e);
         }
         throw e;
      } catch (Throwable e) {
         ActiveMQServerLogger.LOGGER.errorAckingMessage((Exception) e);
         ActiveMQException activeMQIllegalStateException = new ActiveMQIllegalStateException(e.getMessage());
         if (startedTransaction) {
            tx.rollback();
         } else {
            tx.markAsRollbackOnly(activeMQIllegalStateException);
         }
         throw activeMQIllegalStateException;
      }

      return ackedRefs;
   }

   @Override
   public synchronized void individualAcknowledge(Transaction tx, final long messageID) throws Exception {
      if (browseOnly) {
         return;
      }

      boolean startedTransaction = false;

      if (logger.isTraceEnabled()) {
         logger.trace("individualACK messageID=" + messageID);
      }

      if (tx == null) {
         if (logger.isTraceEnabled()) {
            logger.trace("individualACK starting new TX");
         }
         startedTransaction = true;
         tx = new TransactionImpl(storageManager);
      }

      try {

         MessageReference ref;
         ref = removeReferenceByID(messageID);

         if (logger.isTraceEnabled()) {
            logger.trace("ACKing ref " + ref + " on tx= " + tx + ", consumer=" + this);
         }

         if (ref == null) {
            ActiveMQIllegalStateException ils = ActiveMQMessageBundle.BUNDLE.consumerNoReference(id, messageID, messageQueue.getName());
            tx.markAsRollbackOnly(ils);
            throw ils;
         }

         ref.acknowledge(tx, this);

         acks++;

         if (startedTransaction) {
            tx.commit();
         }
      } catch (ActiveMQException e) {
         if (startedTransaction) {
            tx.rollback();
         } else if (tx != null) {
            tx.markAsRollbackOnly(e);
         }
         throw e;
      } catch (Throwable e) {
         ActiveMQServerLogger.LOGGER.errorAckingMessage((Exception) e);
         ActiveMQIllegalStateException hqex = new ActiveMQIllegalStateException(e.getMessage());
         if (startedTransaction) {
            tx.rollback();
         } else if (tx != null) {
            tx.markAsRollbackOnly(hqex);
         }
         throw hqex;
      }

   }

   @Override
   public synchronized void individualCancel(final long messageID, boolean failed) throws Exception {
      if (browseOnly) {
         return;
      }

      MessageReference ref = removeReferenceByID(messageID);

      if (ref == null) {
         throw new IllegalStateException("Cannot find ref to ack " + messageID);
      }

      if (!failed) {
         ref.decrementDeliveryCount();
      }

      ref.getQueue().cancel(ref, System.currentTimeMillis());
   }


   @Override
   public synchronized void reject(final long messageID) throws Exception {
      if (browseOnly) {
         return;
      }

      MessageReference ref = removeReferenceByID(messageID);

      if (ref == null) {
         return; // nothing to be done
      }

      ref.getQueue().sendToDeadLetterAddress(null, ref);
   }

   @Override
   public synchronized void backToDelivering(MessageReference reference) {
      synchronized (lock) {
         deliveringRefs.addFirst(reference);
      }
   }

   @Override
   public synchronized MessageReference removeReferenceByID(final long messageID) throws Exception {
      if (browseOnly) {
         return null;
      }

      // Expiries can come in out of sequence with respect to delivery order

      synchronized (lock) {
         // This is an optimization, if the reference is the first one, we just poll it.
         // But first we need to make sure deliveringRefs isn't empty
         if (deliveringRefs.isEmpty()) {
            return null;
         }

         if (deliveringRefs.peek().getMessage().getMessageID() == messageID) {
            return deliveringRefs.poll();
         }
         //slow path in a separate method
         return removeDeliveringRefById(messageID);
      }
   }

   private MessageReference removeDeliveringRefById(long messageID) {
      assert deliveringRefs.peek().getMessage().getMessageID() != messageID;

      Iterator<MessageReference> iter = deliveringRefs.iterator();

      MessageReference ref = null;

      while (iter.hasNext()) {
         MessageReference theRef = iter.next();

         if (theRef.getMessage().getMessageID() == messageID) {
            iter.remove();

            ref = theRef;

            break;
         }
      }
      return ref;
   }

   /**
    * To be used on tests only
    */
   public AtomicInteger getAvailableCredits() {
      return availableCredits;
   }

   /* (non-Javadoc)
    * @see java.lang.Object#toString()
    */
   @Override
   public String toString() {
      return "ServerConsumerImpl [id=" + id + ", filter=" + filter + ", binding=" + binding + "]";
   }

   @Override
   public String toManagementString() {
      return "ServerConsumer [id=" + getConnectionID() + ":" + getSessionID() + ":" + id + ", filter=" + filter + ", binding=" + binding.toManagementString() + "]";
   }

   @Override
   public void disconnect() {
      callback.disconnect(this, getQueue().getName());
   }

   public float getRate() {
      float timeSlice = ((System.currentTimeMillis() - consumerRateCheckTime.getAndSet(System.currentTimeMillis())) / 1000.0f);
      if (timeSlice == 0) {
         messageConsumedSnapshot.getAndSet(acks);
         return 0.0f;
      }
      return BigDecimal.valueOf((acks - messageConsumedSnapshot.getAndSet(acks)) / timeSlice).setScale(2, BigDecimal.ROUND_UP).floatValue();
   }


   @Override
   public void promptDelivery() {
      // largeMessageDeliverer is always set inside a lock
      // if we don't acquire a lock, we will have NPE eventually
      if (largeMessageDeliverer != null) {
         resumeLargeMessage();
      } else {
         forceDelivery();
      }
   }

   private void forceDelivery() {
      if (browseOnly) {
         messageQueue.getExecutor().execute(browserDeliverer);
      } else {
         messageQueue.deliverAsync();
      }
   }

   private void resumeLargeMessage() {
      messageQueue.getExecutor().execute(resumeLargeMessageRunnable);
   }

   /**
    * @param ref
    * @param message
    */
   private void deliverStandardMessage(final MessageReference ref, Message message) throws ActiveMQException {
      applyPrefixForLegacyConsumer(message);
      int packetSize = callback.sendMessage(ref, message, ServerConsumerImpl.this, ref.getDeliveryCount());

      if (availableCredits != null) {
         availableCredits.addAndGet(-packetSize);

         if (logger.isTraceEnabled()) {
            logger.trace(this + "::FlowControl::delivery standard taking " +
                            packetSize +
                            " from credits, available now is " +
                            availableCredits);
         }
      }
   }

   private void applyPrefixForLegacyConsumer(Message message) {
      /**
       * check to see if:
       * 1) This is a "core" connection
       * 2) The "core" connection belongs to a JMS client
       * 3) The JMS client is an "old" client which needs address prefixes
       *
       * If 1, 2, & 3 are true then apply the "old" prefix for queues and topics as appropriate.
       */
      if (requiresLegacyPrefix) {
         if (anycast) {
            if (!message.getAddress().startsWith(PacketImpl.OLD_QUEUE_PREFIX.toString())) {
               message.setAddress(PacketImpl.OLD_QUEUE_PREFIX + message.getAddress());
            }
         } else {
            if (!message.getAddress().startsWith(PacketImpl.OLD_TOPIC_PREFIX.toString())) {
               message.setAddress(PacketImpl.OLD_TOPIC_PREFIX + message.getAddress());
            }
         }
      }
   }

   private final Runnable resumeLargeMessageRunnable = new Runnable() {
      @Override
      public void run() {
         synchronized (lock) {
            try {
               if (largeMessageDeliverer == null || largeMessageDeliverer.deliver()) {
                  forceDelivery();
               }
            } catch (Exception e) {
               ActiveMQServerLogger.LOGGER.errorRunningLargeMessageDeliverer(e);
            }
         }
      }
   };

   public void setPreAcknowledge(boolean preAcknowledge) {
      this.preAcknowledge = preAcknowledge;
   }

   /**
    * Internal encapsulation of the logic on sending LargeMessages.
    * This Inner class was created to avoid a bunch of loose properties about the current LargeMessage being sent
    */
   private final class CoreLargeMessageDeliverer {

      private long sizePendingLargeMessage;

      private LargeServerMessage largeMessage;

      private final MessageReference ref;

      private boolean sentInitialPacket = false;

      /**
       * The current position on the message being processed
       */
      private long positionPendingLargeMessage;

      private LargeBodyReader context;

      private ByteBuffer chunkBytes;

      private CoreLargeMessageDeliverer(final LargeServerMessage message, final MessageReference ref) throws Exception {
         largeMessage = message;

         largeMessage.toMessage().usageUp();

         this.ref = ref;

         this.chunkBytes = null;
      }

      @Override
      public String toString() {
         return "ServerConsumerImpl$LargeMessageDeliverer[ref=[" + ref + "]]";
      }

      private ByteBuffer acquireHeapBodyBuffer(int requiredCapacity) {
         if (this.chunkBytes == null || this.chunkBytes.capacity() != requiredCapacity) {
            this.chunkBytes = ByteBuffer.allocate(requiredCapacity);
         } else {
            this.chunkBytes.clear();
         }
         return this.chunkBytes;
      }

      private void releaseHeapBodyBuffer() {
         this.chunkBytes = null;
      }

      public boolean deliver() throws Exception {
         pendingDelivery.countUp();
         try {
            if (!started) {
               return false;
            }

            LargeServerMessage currentLargeMessage = largeMessage;
            if (currentLargeMessage == null) {
               return true;
            }

            if (availableCredits != null && availableCredits.get() <= 0) {
               if (logger.isTraceEnabled()) {
                  logger.trace(this + "::FlowControl::delivery largeMessage interrupting as there are no more credits, available=" +
                                  availableCredits);
               }
               releaseHeapBodyBuffer();
               return false;
            }

            if (!sentInitialPacket) {
               context = currentLargeMessage.getLargeBodyReader();

               sizePendingLargeMessage = context.getSize();

               context.open();

               sentInitialPacket = true;

               int packetSize = callback.sendLargeMessage(ref, currentLargeMessage.toMessage(), ServerConsumerImpl.this, context.getSize(), ref.getDeliveryCount());

               if (availableCredits != null) {
                  final int credits = availableCredits.addAndGet(-packetSize);

                  if (credits <= 0) {
                     releaseHeapBodyBuffer();
                  }

                  if (logger.isTraceEnabled()) {
                     logger.trace(this + "::FlowControl::" +
                                     " deliver initialpackage with " +
                                     packetSize +
                                     " delivered, available now = " +
                                     availableCredits);
                  }
               }

               // Execute the rest of the large message on a different thread so as not to tie up the delivery thread
               // for too long

               resumeLargeMessage();

               return false;
            } else {
               if (availableCredits != null && availableCredits.get() <= 0) {
                  if (logger.isTraceEnabled()) {
                     logger.trace(this + "::FlowControl::deliverLargeMessage Leaving loop of send LargeMessage because of credits, available=" +
                                     availableCredits);
                  }
                  releaseHeapBodyBuffer();
                  return false;
               }

               final int localChunkLen = (int) Math.min(sizePendingLargeMessage - positionPendingLargeMessage, minLargeMessageSize);

               final ByteBuffer bodyBuffer = acquireHeapBodyBuffer(localChunkLen);

               assert bodyBuffer.remaining() == localChunkLen;

               final int readBytes = context.readInto(bodyBuffer);

               assert readBytes == localChunkLen;

               final byte[] body = bodyBuffer.array();

               assert body.length == readBytes;

               //It is possible to recycle the same heap body buffer because it won't be cached by sendLargeMessageContinuation
               //given that requiresResponse is false: ChannelImpl::send will use the resend cache only if
               //resendCache != null && packet.isRequiresConfirmations()

               int packetSize = callback.sendLargeMessageContinuation(ServerConsumerImpl.this, body, positionPendingLargeMessage + localChunkLen < sizePendingLargeMessage, false);

               int chunkLen = body.length;

               if (availableCredits != null) {
                  final int credits = availableCredits.addAndGet(-packetSize);

                  if (credits <= 0) {
                     releaseHeapBodyBuffer();
                  }

                  if (logger.isTraceEnabled()) {
                     logger.trace(this + "::FlowControl::largeMessage deliver continuation, packetSize=" +
                                     packetSize +
                                     " available now=" +
                                     availableCredits);
                  }
               }

               positionPendingLargeMessage += chunkLen;

               if (positionPendingLargeMessage < sizePendingLargeMessage) {
                  resumeLargeMessage();

                  return false;
               }
            }

            if (logger.isTraceEnabled()) {
               logger.trace("Finished deliverLargeMessage");
            }

            finish();

            return true;
         } finally {
            pendingDelivery.countDown();
         }
      }

      public void finish() throws Exception {
         synchronized (lock) {
            releaseHeapBodyBuffer();

            if (largeMessage == null) {
               // handleClose could be calling close while handle is also calling finish.
               // As a result one of them could get here after the largeMessage is already gone.
               // On that case we just ignore this call
               return;
            }
            if (context != null) {
               context.close();
               context = null;
            }

            largeMessage.releaseResources(false, false);

            largeMessage.toMessage().usageDown();

            largeMessageDeliverer = null;

            largeMessage = null;
         }
      }
   }

   protected class BrowserDeliverer implements Runnable {

      protected MessageReference current = null;

      public BrowserDeliverer(final LinkedListIterator<MessageReference> iterator) {
         this.iterator = iterator;
      }

      public final LinkedListIterator<MessageReference> iterator;

      public synchronized void close() {
         iterator.close();
      }

      @Override
      public synchronized void run() {
         // if the reference was busy during the previous iteration, handle it now
         if (current != null) {
            try {
               HandleStatus status = handle(current);

               if (status == HandleStatus.BUSY) {
                  return;
               }

               if (status == HandleStatus.HANDLED) {
                  proceedDeliver(current);
               }

               current = null;
            } catch (Exception e) {
               ActiveMQServerLogger.LOGGER.errorBrowserHandlingMessage(e, current);
               return;
            }
         }

         MessageReference ref = null;
         HandleStatus status;

         while (true) {
            try {
               ref = null;
               synchronized (messageQueue) {
                  if (!iterator.hasNext()) {
                     callback.browserFinished(ServerConsumerImpl.this);
                     break;
                  }

                  ref = iterator.next();

                  status = handle(ref);
               }

               if (status == HandleStatus.HANDLED) {
                  proceedDeliver(ref);
               } else if (status == HandleStatus.BUSY) {
                  // keep a reference on the current message reference
                  // to handle it next time the browser deliverer is executed
                  current = ref;
                  break;
               }
            } catch (Exception e) {
               ActiveMQServerLogger.LOGGER.errorBrowserHandlingMessage(e, ref);
               break;
            }
         }
      }

      public boolean isBrowsed() {
         messageQueue.deliverAsync();
         boolean b = !iterator.hasNext();
         return b;
      }
   }

   @Override
   public long getSequentialID() {
      return sequentialID;
   }

   @Override
   public SimpleString getQueueName() {
      return getQueue().getName();
   }

   @Override
   public RoutingType getQueueType() {
      return getQueue().getRoutingType();
   }

   @Override
   public SimpleString getQueueAddress() {
      return getQueue().getAddress();
   }

   @Override
   public String getSessionName() {
      return this.session.getName();
   }

   @Override
   public String getConnectionClientID() {
      return this.session.getRemotingConnection().getClientID();
   }

   @Override
   public String getConnectionProtocolName() {
      return this.session.getRemotingConnection().getProtocolName();
   }

   @Override
   public String getConnectionLocalAddress() {
      return this.session.getRemotingConnection().getTransportConnection().getLocalAddress();
   }

   @Override
   public String getConnectionRemoteAddress() {
      return this.session.getRemotingConnection().getTransportConnection().getRemoteAddress();
   }
}