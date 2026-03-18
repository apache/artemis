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
package org.apache.activemq.artemis.core.server.cluster.impl;

import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.activemq.artemis.api.core.ActiveMQAddressFullException;
import org.apache.activemq.artemis.api.core.ActiveMQDisconnectedException;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.ActiveMQInterruptedException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.RefCountMessage;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ClusterTopologyListener;
import org.apache.activemq.artemis.api.core.client.SendAcknowledgementHandler;
import org.apache.activemq.artemis.api.core.client.SessionFailureListener;
import org.apache.activemq.artemis.api.core.client.TopologyMember;
import org.apache.activemq.artemis.api.core.management.CoreNotificationType;
import org.apache.activemq.artemis.core.client.impl.ClientProducerCredits;
import org.apache.activemq.artemis.core.client.impl.ClientProducerFlowCallback;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryInternal;
import org.apache.activemq.artemis.core.client.impl.ClientSessionInternal;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorInternal;
import org.apache.activemq.artemis.core.config.BridgeConfiguration;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.filter.impl.FilterImpl;
import org.apache.activemq.artemis.core.persistence.OperationContext;
import org.apache.activemq.artemis.core.persistence.impl.journal.OperationContextImpl;
import org.apache.activemq.artemis.core.server.ActiveMQMessageBundle;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.HandleStatus;
import org.apache.activemq.artemis.core.server.LargeServerMessage;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.cluster.Bridge;
import org.apache.activemq.artemis.core.server.impl.QueueImpl;
import org.apache.activemq.artemis.core.server.management.Notification;
import org.apache.activemq.artemis.core.server.management.NotificationService;
import org.apache.activemq.artemis.core.server.transformer.Transformer;
import org.apache.activemq.artemis.spi.core.protocol.EmbedMessageUtil;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.remoting.ReadyListener;
import org.apache.activemq.artemis.utils.FutureLatch;
import org.apache.activemq.artemis.utils.ReusableLatch;
import org.apache.activemq.artemis.utils.UUID;
import org.apache.activemq.artemis.utils.collections.TypedProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BridgeImpl implements Bridge, SessionFailureListener, SendAcknowledgementHandler, ReadyListener, ClientProducerFlowCallback {

   public enum State {
      STARTING, STARTED, PAUSING, PAUSED, STOPPING, STOPPED
   }

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   protected final ServerLocatorInternal serverLocator;

   protected final Executor executor;

   protected final ScheduledExecutorService scheduledExecutor;

   private final ReusableLatch pendingAcks = new ReusableLatch(0);

   private final UUID nodeUUID;

   private final long sequentialID;

   protected final Queue queue;

   private final Filter filter;

   final java.util.Map<Long, MessageReference> refs = new LinkedHashMap<>();

   private final Transformer transformer;

   private final Object connectionGuard = new Object();

   private boolean blockedOnFlowControl;

   protected ScheduledFuture<?> scheduledReconnection;

   protected volatile ClientSessionInternal session;

   // on cases where sub-classes need a consumer
   protected volatile ClientSessionInternal sessionConsumer;

   // this will happen if a disconnect happened
   // upon reconnection we need to send the nodeUP back into the topology
   protected volatile boolean disconnectedAndDown = false;

   protected String targetNodeID;

   protected TopologyMember targetNode;

   private volatile ClientSessionFactoryInternal csf;

   private volatile ClientProducer producer;

   private volatile State state = State.STOPPED;

   private boolean deliveringLargeMessage;

   private int reconnectAttemptsInUse;

   private int retryCount = 0;

   private NotificationService notificationService;

   private boolean keepConnecting = true;

   private final ActiveMQServer server;

   private final BridgeMetrics metrics = new BridgeMetrics();

   private final BridgeConfiguration configuration;

   private final OperationContextImpl bridgeContext;

   private final Lock statusLock = new ReentrantLock();

   public BridgeImpl(final ServerLocatorInternal serverLocator,
                     final BridgeConfiguration configuration,
                     final UUID nodeUUID,
                     final Queue queue,
                     final Executor executor,
                     final ScheduledExecutorService scheduledExecutor,
                     final ActiveMQServer server) throws ActiveMQException {

      this.sequentialID = server.getStorageManager().generateID();

      this.configuration = configuration;

      this.reconnectAttemptsInUse = configuration.getInitialConnectAttempts();

      this.serverLocator = serverLocator;

      this.nodeUUID = nodeUUID;

      this.queue = queue;

      this.executor = executor;

      this.scheduledExecutor = scheduledExecutor;

      this.transformer = server.getServiceRegistry().getBridgeTransformer(configuration.getName(), configuration.getTransformerConfiguration());

      this.filter = FilterImpl.createFilter(configuration.getFilterString());

      this.server = server;

      this.bridgeContext = new OperationContextImpl(executor);
   }

   public static final byte[] getDuplicateBytes(final UUID nodeUUID, final long messageID) {
      byte[] bytes = new byte[24];

      ByteBuffer bb = ByteBuffer.wrap(bytes);

      bb.put(nodeUUID.asBytes());

      bb.putLong(messageID);

      return bytes;
   }

   // for tests
   public boolean isBlockedOnFlowControl() {
      return blockedOnFlowControl;
   }

   // for tests
   public ClientSessionFactory getSessionFactory() {
      return csf;
   }

   // for tests
   public ServerLocatorInternal getServerLocator() {
      return serverLocator;
   }

   @Override
   public List<MessageReference> getDeliveringMessages() {
      synchronized (refs) {
         return new ArrayList<>(refs.values());
      }
   }

   private static void cleanUpSessionFactory(ClientSessionFactoryInternal factory) {
      if (factory != null) {
         factory.cleanup();
      }
   }

   @Override
   public void setNotificationService(final NotificationService notificationService) {
      this.notificationService = notificationService;
   }

   @Override
   public void onCreditsFlow(boolean blocked, ClientProducerCredits producerCredits) {
      if (logger.isTraceEnabled()) {
         logger.trace("Bridge {} received credits, with blocked = {}", configuration.getName(), blocked);
      }
      this.blockedOnFlowControl = blocked;
      if (!blocked) {
         queue.deliverAsync();
      }
   }

   @Override
   public void onCreditsFail(ClientProducerCredits producerCredits) {
      ActiveMQServerLogger.LOGGER.bridgeAddressFull(String.valueOf(producerCredits.getAddress()), configuration.getName());
      disconnect();
   }

   @Override
   public long sequentialID() {
      return sequentialID;
   }

   @Override
   public void start() throws Exception {
      statusLock.lock();
      try {
         State localState = this.state;
         if (localState == State.STARTING || localState == State.STARTED || localState == State.STOPPING || localState == State.PAUSING) {
            logger.debug("Bridge {} state is {}. Ignoring call to start.", configuration.getName(), localState);
            if (localState == State.STOPPING || localState == State.PAUSING) {
               throw ActiveMQMessageBundle.BUNDLE.bridgeOperationCannotBeExecuted(configuration.getName(), "started", localState);
            } else {
               return;
            }
         }

         state = State.STARTING;

         logger.debug("Bridge {} is starting", configuration.getName());

         executor.execute(new ConnectRunnable());

         sendNotification(CoreNotificationType.BRIDGE_STARTED);
      } finally {
         statusLock.unlock();
      }
   }

   @Override
   public String debug() {
      return toString();
   }

   private void cancelRefs() {
      LinkedList<MessageReference> list;

      synchronized (refs) {
         list = new LinkedList<>(refs.values());
         refs.clear();
      }

      if (logger.isTraceEnabled()) {
         logger.trace("BridgeImpl::cancelRefs cancelling {} references", list.size());
      }

      if (logger.isTraceEnabled() && list.isEmpty()) {
         logger.trace("didn't have any references to cancel on bridge {}", this);
         return;
      }

      ListIterator<MessageReference> listIterator = list.listIterator(list.size());

      Queue refqueue;

      long timeBase = System.currentTimeMillis();

      while (listIterator.hasPrevious()) {
         MessageReference ref = listIterator.previous();

         logger.trace("BridgeImpl::cancelRefs Cancelling reference {} on bridge {}", ref, this);

         refqueue = ref.getQueue();

         try {
            refqueue.cancel(ref, timeBase);
         } catch (Exception e) {
            // There isn't much we can do besides log an error
            ActiveMQServerLogger.LOGGER.errorCancellingRefOnBridge(ref, e);
         }
      }
   }

   @Override
   public void flushExecutor() {
      // Wait for any create objects runnable to complete
      FutureLatch future = new FutureLatch();

      executor.execute(future);

      boolean ok = future.await(10000);

      if (!ok) {
         ActiveMQServerLogger.LOGGER.timedOutWaitingToStopBridge();
      }
   }

   @Override
   public void disconnect() {
      executor.execute(() -> {
         if (session != null) {
            try {
               session.cleanUp(false);
            } catch (Exception dontcare) {
               logger.debug(dontcare.getMessage(), dontcare);
            }
            session = null;
         }
         if (sessionConsumer != null) {
            try {
               sessionConsumer.cleanUp(false);
            } catch (Exception dontcare) {
               logger.debug(dontcare.getMessage(), dontcare);
            }
            sessionConsumer = null;
         }
      });
   }

   @Override
   public boolean isConnected() {
      return session != null && !session.isClosed();
   }

   /**
    * The cluster manager needs to use the same executor to close the serverLocator, otherwise the stop will break. This
    * method is intended to expose this executor to the ClusterManager
    */
   public Executor getExecutor() {
      return executor;
   }

   @Override
   public void stop() throws Exception {
      statusLock.lock();
      try {
         State localState = state;
         if (localState == State.STOPPING || localState == State.STOPPED || localState == State.PAUSING) {
            logger.debug("Bridge {} state is {}. Ignoring call to stop.", configuration.getName(), localState);
            if (localState == State.PAUSING) {
               throw ActiveMQMessageBundle.BUNDLE.bridgeOperationCannotBeExecuted(configuration.getName(), "stopped", localState);
            } else {
               return;
            }
         }

         state = State.STOPPING;

         logger.debug("Bridge {} is stopping", configuration.getName());

         if (scheduledReconnection != null) {
            scheduledReconnection.cancel(true);
         }

         executor.execute(new StopRunnable());
      } finally {
         statusLock.unlock();
      }
   }

   @Override
   public void pause() throws Exception {
      statusLock.lock();
      try {
         State localState = state;
         if (localState == State.STOPPING || localState == State.STOPPED || localState == State.PAUSING || localState == State.PAUSED) {
            logger.debug("Bridge {} state is {}. Ignoring call to pause.", configuration.getName(), localState);
            if (localState == State.STOPPING || localState == State.STOPPED) {
               throw ActiveMQMessageBundle.BUNDLE.bridgeOperationCannotBeExecuted(configuration.getName(), "paused", localState);
            } else {
               return;
            }
         }

         state = State.PAUSING;

         logger.info("Bridge {} is pausing", configuration.getName());

         executor.execute(new PauseRunnable());
      } finally {
         statusLock.unlock();
      }
   }

   @Override
   public void resume() throws Exception {
      queue.addConsumer(BridgeImpl.this);
      queue.deliverAsync();
   }

   @Override
   public boolean isStarted() {
      return state == State.STARTING || state == State.STARTED;
   }

   @Override
   public SimpleString getName() {
      return SimpleString.of(configuration.getName());
   }

   @Override
   public Queue getQueue() {
      return queue;
   }

   @Override
   public Filter getFilter() {
      return filter;
   }

   @Override
   public SimpleString getForwardingAddress() {
      return SimpleString.of(configuration.getForwardingAddress());
   }

   @Override
   public RemotingConnection getForwardingConnection() {
      if (session == null) {
         return null;
      } else {
         return session.getConnection();
      }
   }

   @Override
   public void sendFailed(Message message, Exception e) {
      ActiveMQServerLogger.LOGGER.bridgeFailedToSend(configuration.getName(), message.toString(), e.getClass().getSimpleName(), e.getMessage());
      if (e instanceof ActiveMQAddressFullException) {
         failed(e);
      }
   }

   @Override
   public void sendAcknowledged(final Message message) {
      OperationContext oldContext = OperationContextImpl.getContext();

      try {
         OperationContextImpl.setContext(bridgeContext);
         logger.debug("Bridge {} received confirmation for message {}", configuration.getName(), message);

         State localState = state;
         if (localState == State.STARTED || localState == State.STOPPING || localState == State.PAUSING) {
            try {

               final MessageReference ref;

               synchronized (refs) {
                  ref = refs.remove(message.getMessageID());
               }

               if (ref != null) {
                  if (logger.isTraceEnabled()) {
                     logger.trace("BridgeImpl::sendAcknowledged bridge {} Acking {} on queue {}", this, ref, ref.getQueue());
                  }
                  ref.getQueue().acknowledge(ref);
                  pendingAcks.countDown();
                  metrics.incrementMessagesAcknowledged();

                  if (server.hasBrokerBridgePlugins()) {
                     server.callBrokerBridgePlugins(plugin -> plugin.afterAcknowledgeBridge(this, ref));
                  }
               } else {
                  logger.trace("BridgeImpl::sendAcknowledged bridge {} could not find reference for message {}", this, message);
               }
            } catch (Exception e) {
               ActiveMQServerLogger.LOGGER.bridgeFailedToAck(e);
            }
         } else {
            logger.debug("Bridge {} state is {}. Ignoring call to sendAcknowledged.", configuration.getName(), localState);
         }
      } finally {
         OperationContextImpl.setContext(oldContext);
      }
   }

   @Override
   public void failed(Throwable t) {
      if (t instanceof ActiveMQException activeMQException) {
         connectionFailed(activeMQException, false);
      } else {
         ActiveMQException exception = new ActiveMQException(t.getMessage());
         exception.initCause(t);
         connectionFailed(exception, false);
      }
   }

   /**
    * Hook for processing message before forwarding
    */
   protected Message beforeForward(Message message, final SimpleString forwardingAddress) throws ActiveMQException {
      message = message.copy();
      ((RefCountMessage)message).setParentRef((RefCountMessage)message);

      return beforeForwardingNoCopy(message, forwardingAddress);
   }

   /**
    * ClusterConnectionBridge already makes a copy of the message. So I needed I hook where the message is not copied.
    */
   protected Message beforeForwardingNoCopy(Message message, SimpleString forwardingAddress) {
      if (configuration.isUseDuplicateDetection()) {
         // We keep our own DuplicateID for the Bridge, so bouncing back and forth will work fine
         byte[] bytes = getDuplicateBytes(nodeUUID, message.getMessageID());

         message.putExtraBytesProperty(Message.HDR_BRIDGE_DUPLICATE_ID, bytes);
      }

      if (forwardingAddress != null) {
         // for AMQP messages this modification will be transient
         message.setAddress(forwardingAddress);
      }

      switch (configuration.getRoutingType()) {
         case ANYCAST:
            message.setRoutingType(RoutingType.ANYCAST);
            break;
         case MULTICAST:
            message.setRoutingType(RoutingType.MULTICAST);
            break;
         case STRIP:
            message.setRoutingType(null);
            break;
         case PASS:
            break;
      }

      message.messageChanged();

      if (transformer != null) {
         final Message transformedMessage = transformer.transform(message);
         if (transformedMessage != message) {
            logger.debug("The transformer {} made a copy of the message {} as transformedMessage", transformer, message);
         }
         return EmbedMessageUtil.embedAsCoreMessage(transformedMessage);
      } else {
         return EmbedMessageUtil.embedAsCoreMessage(message);
      }
   }

   @Override
   public void readyForWriting() {
      queue.deliverAsync();
   }

   @Override
   public HandleStatus handle(final MessageReference ref) throws Exception {
      if (RefCountMessage.isRefTraceEnabled() && ref.getMessage() instanceof RefCountMessage) {
         RefCountMessage.deferredDebug(ref.getMessage(), "Going through the bridge");
      }
      if (filter != null && !filter.match(ref.getMessage())) {
         logger.trace("message reference {} is no match for bridge {}", ref, configuration.getName());
         return HandleStatus.NO_MATCH;
      }

      if (statusLock.tryLock()) {
         try {
            if (state != State.STARTED || !session.isWritable(this)) {
               if (logger.isDebugEnabled()) {
                  logger.debug("{}::Ignoring reference on bridge as it is set to inactive ref {}, active = false", this, ref);
               }
               return HandleStatus.BUSY;
            }

            if (blockedOnFlowControl) {
               logger.debug("Bridge {} is blocked on flow control, cannot receive {}", configuration.getName(), ref);
               return HandleStatus.BUSY;
            }

            if (deliveringLargeMessage) {
               logger.trace("Bridge {} is busy delivering a large message", configuration.getName());
               return HandleStatus.BUSY;
            }

            logger.trace("Bridge {} is handling reference {} ", configuration.getName(), ref);

            ref.handled();

            synchronized (refs) {
               refs.put(ref.getMessage().getMessageID(), ref);
            }

            final SimpleString dest;

            if (configuration.getForwardingAddress() != null) {
               dest = SimpleString.of(configuration.getForwardingAddress());
            } else {
               // Preserve the original address
               dest = ref.getMessage().getAddressSimpleString();
            }

            final Message message;
            try {
               message = beforeForward(ref.getMessage(), dest);
            } catch (ActiveMQException ex) {
               ActiveMQServerLogger.LOGGER.bridgeUnableToHandleMessage(getName(), ref.getMessage().toString(), ex.getMessage());
               ref.getQueue().sendToDeadLetterAddress(null, ref);
               synchronized (refs) {
                  refs.remove(ref.getMessageID());
               }
               return HandleStatus.HANDLED;
            }

            pendingAcks.countUp();

            try {
               if (server.hasBrokerBridgePlugins()) {
                  server.callBrokerBridgePlugins(plugin -> plugin.beforeDeliverBridge(this, ref));
               }

               final HandleStatus status;
               if (message.isLargeMessage()) {
                  deliveringLargeMessage = true;
                  deliverLargeMessage(dest, ref, (LargeServerMessage) message);
                  status = HandleStatus.HANDLED;
               } else {
                  status = deliverStandardMessage(dest, ref, message, ref.getMessage());
               }

               //Only increment messages pending acknowledgement if handled by bridge
               if (status == HandleStatus.HANDLED) {
                  metrics.incrementMessagesPendingAcknowledgement();
               }

               if (server.hasBrokerBridgePlugins()) {
                  server.callBrokerBridgePlugins(plugin -> plugin.afterDeliverBridge(this, ref, status));
               }

               return status;
            } catch (Exception e) {
               // If an exception happened, we must count down immediately
               pendingAcks.countDown();
               throw e;
            }
         } finally {
            statusLock.unlock();
         }
      } else {
         return HandleStatus.BUSY;
      }
   }

   // FailureListener implementation --------------------------------

   @Override
   public void proceedDeliver(MessageReference ref) {
      // no op
   }

   @Override
   public void connectionFailed(final ActiveMQException me, boolean failedOver) {
      connectionFailed(me, failedOver, null);
   }

   @Override
   public void connectionFailed(final ActiveMQException me, boolean failedOver, String scaleDownTargetNodeID) {
      if (server.isStarted()) {
         if (me instanceof ActiveMQDisconnectedException) {
            ActiveMQServerLogger.LOGGER.bridgeConnectionClosed(failedOver);
         } else {
            ActiveMQServerLogger.LOGGER.bridgeConnectionFailed(failedOver);
         }
      }

      synchronized (connectionGuard) {
         keepConnecting = true;
      }

      try {
         if (producer != null) {
            producer.close();
         }

         cleanUpSessionFactory(csf);
      } catch (Throwable ignored) {
      }

      try {
         session.cleanUp(false);
      } catch (Throwable ignored) {
      }

      if (scaleDownTargetNodeID != null && !scaleDownTargetNodeID.equals(nodeUUID.toString())) {
         scaleDown(scaleDownTargetNodeID);
      } else if (scaleDownTargetNodeID != null) {
         // the disconnected node is scaling down to me, no need to reconnect to it
         logger.debug("Received scaleDownTargetNodeID: {}; cancelling reconnect.", scaleDownTargetNodeID);
         fail(true, true);
      } else {
         logger.debug("Received null scaleDownTargetNodeID");
         fail(me.getType() == ActiveMQExceptionType.DISCONNECTED, false);
      }

      tryScheduleRetryReconnect(me.getType());
   }

   protected void scaleDown(String scaleDownTargetNodeID) {
      statusLock.lock();
      try {
         if (logger.isDebugEnabled()) {
            logger.debug("Moving {} messages from {} to {}", queue.getMessageCount(), queue.getName(), scaleDownTargetNodeID);
         }
         ((QueueImpl) queue).moveReferencesBetweenSnFQueues(SimpleString.of(scaleDownTargetNodeID));

         // stop the bridge from trying to reconnect and clean up all the bindings
         fail(true, true);

      } catch (Exception e) {
         logger.warn(e.getMessage(), e);
      } finally {
         statusLock.unlock();
      }
   }

   protected void tryScheduleRetryReconnect(final ActiveMQExceptionType type) {
      scheduleRetryConnect();
   }

   @Override
   public void beforeReconnect(final ActiveMQException exception) {
   }

   private void deliverLargeMessage(final SimpleString dest,
                                    final MessageReference ref,
                                    final LargeServerMessage message) {
      executor.execute(() -> {
         logger.trace("going to send large message: {} from {}", message, queue);

         try {
            producer.send(dest, message.toMessage());

            // as soon as we are done sending the large message
            // we unset the delivery flag and we will call the deliveryAsync on the queue
            // so the bridge will be able to resume work
            unsetLargeMessageDelivery();

            if (queue != null) {
               queue.deliverAsync();
            }
         } catch (final ActiveMQException e) {
            unsetLargeMessageDelivery();
            ActiveMQServerLogger.LOGGER.bridgeUnableToSendMessage(ref, e);

            connectionFailed(e, false);
         }
      });
   }

   private HandleStatus deliverStandardMessage(SimpleString dest, final MessageReference ref, Message message, Message originalMessage) {
      // if we failover during send then there is a chance that the
      // that this will throw a disconnect, we need to remove the message
      // from the acks so it will get resent, duplicate detection will cope
      // with any messages resent

      logger.trace("going to send message: {} from {}", message, queue);

      try {
         producer.send(dest, message);
      } catch (final ActiveMQException e) {
         ActiveMQServerLogger.LOGGER.bridgeUnableToSendMessage(ref, e);

         synchronized (refs) {
            // We remove this reference as we are returning busy which means the reference will never leave the Queue.
            // because of this we have to remove the reference here
            refs.remove(message.getMessageID());

            // The delivering count should also be decreased as to avoid inconsistencies
            ((QueueImpl) ref.getQueue()).decDelivering(ref);
         }

         connectionFailed(e, false);

         return HandleStatus.BUSY;
      } finally {
         originalMessage.usageDown();
      }

      return HandleStatus.HANDLED;
   }

   /**
    * for use in tests mainly
    */
   public TopologyMember getTargetNodeFromTopology() {
      return this.targetNode;
   }

   @Override
   public BridgeMetrics getMetrics() {
      return this.metrics;
   }

   @Override
   public String toString() {
      return this.getClass().getSimpleName() + "@" +
         Integer.toHexString(System.identityHashCode(this)) +
         " [name=" +
         configuration.getName() +
         ", queue=" +
         queue +
         " targetConnector=" +
         this.serverLocator +
         "]";
   }

   @Override
   public String toManagementString() {
      return this.getClass().getSimpleName() +
         " [name=" +
         configuration.getName() +
         ", queue=" +
         queue.getName() + "/" + queue.getID() + "]";
   }

   public Transformer getTransformer() {
      return transformer;
   }

   @Override
   public BridgeConfiguration getConfiguration() {
      return configuration;
   }

   public State getState() {
      return state;
   }

   protected void fail(final boolean permanently, boolean scaleDown) {
      logger.debug("{}\n\t::fail being called, permanently={}", this, permanently);
      //we need to make sure we remove the node from the topology so any incoming quorum requests are voted correctly
      if (targetNodeID != null) {
         this.disconnectedAndDown = true;
         serverLocator.notifyNodeDown(System.currentTimeMillis(), targetNodeID);
      }
      if (queue != null) {
         try {
            logger.trace("Removing consumer on fail {} from queue {}", this, queue);

            queue.removeConsumer(this);
         } catch (Exception dontcare) {
            logger.debug(dontcare.getMessage(), dontcare);
         }
      }

      cancelRefs();
      if (queue != null) {
         queue.deliverAsync();
      }
   }

   /**
    * Hook for doing extra stuff after connection
    */
   protected void afterConnect() throws Exception {
      if (disconnectedAndDown && targetNodeID != null && targetNode != null) {
         serverLocator.notifyNodeUp(System.currentTimeMillis(), targetNodeID, targetNode.getBackupGroupName(), targetNode.getScaleDownGroupName(),
                                    new Pair<>(targetNode.getPrimary(), targetNode.getBackup()), false);
         disconnectedAndDown = false;
      }
      retryCount = 0;
      reconnectAttemptsInUse = configuration.getReconnectAttempts();
      if (scheduledReconnection != null) {
         scheduledReconnection.cancel(true);
         scheduledReconnection = null;
      }
   }

   /**
    * Hook for creating session factory
    */
   protected ClientSessionFactoryInternal createSessionFactory() throws Exception {
      if (targetNodeID != null && (this.configuration.getReconnectAttemptsOnSameNode() < 0 || retryCount <= this.configuration.getReconnectAttemptsOnSameNode())) {
         csf = reconnectOnOriginalNode();
      } else {
         serverLocator.resetToInitialConnectors();
         csf = (ClientSessionFactoryInternal) serverLocator.createSessionFactory();
      }

      // null here means the targetNodeIS is not available yet
      if (csf != null) {
         csf.setReconnectAttempts(0);
      }

      return csf;
   }

   protected ClientSessionFactoryInternal reconnectOnOriginalNode() throws Exception {
      String targetNodeIdUse = targetNodeID;
      TopologyMember nodeUse = targetNode;
      if (targetNodeIdUse != null && nodeUse != null) {
         TransportConfiguration[] configs = new TransportConfiguration[2]; // primary and backup
         int numberOfConfigs = 0;

         if (nodeUse.getPrimary() != null) {
            configs[numberOfConfigs++] = nodeUse.getPrimary();
         }
         if (nodeUse.getBackup() != null) {
            configs[numberOfConfigs++] = nodeUse.getBackup();
         }

         if (numberOfConfigs > 0) {
            // It will bounce between all the available configs
            int nodeTry = (retryCount - 1) % numberOfConfigs;

            return (ClientSessionFactoryInternal) serverLocator.createSessionFactory(configs[nodeTry]);
         }
      }

      return null;
   }

   protected void setSessionFactory(ClientSessionFactoryInternal sfi) {
      csf = sfi;
   }

   protected void scheduleRetryConnect() {
      if (serverLocator.isClosed()) {
         ActiveMQServerLogger.LOGGER.bridgeLocatorShutdown();
         return;
      }

      if (state == State.STOPPING || state == State.PAUSING) {
         ActiveMQServerLogger.LOGGER.bridgeWillNotRetry(state == State.STOPPING ? "stopping" : "pausing");
         return;
      }

      if (reconnectAttemptsInUse >= 0 && retryCount > reconnectAttemptsInUse) {
         ActiveMQServerLogger.LOGGER.bridgeAbortStart(configuration.getName(), retryCount, configuration.getReconnectAttempts());
         fail(true, false);
         return;
      }

      long timeout = (long) (this.configuration.getRetryInterval() * Math.pow(this.configuration.getRetryIntervalMultiplier(), retryCount));
      if (timeout == 0) {
         timeout = this.configuration.getRetryInterval();
      }
      if (timeout > configuration.getMaxRetryInterval()) {
         timeout = configuration.getMaxRetryInterval();
      }

      if (logger.isDebugEnabled()) {
         logger.debug("Bridge {} retrying connection #{}, maxRetry={}, timeout={}", this, retryCount, reconnectAttemptsInUse, timeout);
      }

      scheduleRetryConnectFixedTimeout(timeout);
   }


   // To be called by the topology update
   // This logic will be updated on the cluster connection
   protected void nodeUP(TopologyMember member, boolean last) {
      if (member != null) {
         ClientSessionInternal sessionToUse = session;
         RemotingConnection connectionToUse = sessionToUse != null ? sessionToUse.getConnection() : null;
         if (this.targetNodeID != null && this.targetNodeID.equals(member.getNodeId())) {
            // this could be an update of the topology say after a backup started
            BridgeImpl.this.targetNode = member;
         } else {
            // we don't need synchronization here, but we need to make sure we won't get a NPE on races
            if (connectionToUse != null && member.isMember(connectionToUse)) {
               this.targetNode = member;
               this.targetNodeID = member.getNodeId();
            }
         }
      }
   }

   protected void scheduleRetryConnectFixedTimeout(final long milliseconds) {
      try {
         cleanUpSessionFactory(csf);
      } catch (Throwable ignored) {
      }

      if (state == State.STOPPING || state == State.STOPPED || state == State.PAUSING || state == State.PAUSED) {
         return;
      }

      if (logger.isDebugEnabled()) {
         logger.debug("Scheduling retry for bridge {} in {} milliseconds", configuration.getName(), milliseconds);
      }

      scheduledReconnection = scheduledExecutor.schedule(new ScheduledConnectRunnable(), milliseconds, TimeUnit.MILLISECONDS);
   }

   private void internalCancelReferences() {
      cancelRefs();

      if (queue != null) {
         queue.deliverAsync();
      }
   }

   private void unsetLargeMessageDelivery() {
      deliveringLargeMessage = false;
   }

   private class ScheduledConnectRunnable implements Runnable {

      @Override
      public void run() {
         if (isStarted()) {
            // The scheduling will still use the main executor here
            executor.execute(new ConnectRunnable());
         }
      }
   }

   private class ConnectRunnable implements Runnable {

      @Override
      public void run() {
         if (state == State.STOPPING || state == State.PAUSING) {
            logger.debug("Bridge {} state is {}. Ignoring call to connect.", configuration.getName(), state);
            return;
         }

         synchronized (connectionGuard) {
            if (!keepConnecting) {
               return;
            }

            if (logger.isDebugEnabled()) {
               logger.debug("Connecting  {} to its destination [{}], csf={}", this, nodeUUID, csf);
            }
            retryCount++;

            try {
               if (csf == null || csf.isClosed()) {
                  if (state == State.STOPPING || state == State.PAUSING) {
                     return;
                  }
                  if (csf != null && csf.isClosed()) {
                     // ensure we release any references to the existing ClientSessionFactory before creating a new one otherwise we will leak
                     serverLocator.factoryClosed(csf);
                  }
                  csf = createSessionFactory();
                  if (csf == null) {
                     // Retrying. This probably means the node is not available (for the cluster connection case)
                     scheduleRetryConnect();
                     return;
                  }
                  // Session is pre-acknowledge
                  session = (ClientSessionInternal) csf.createSession(configuration.getUser(), configuration.getPassword(), false, true, true, true, 1, configuration.getClientId());
                  session.getProducerCreditManager().setCallback(BridgeImpl.this);
                  sessionConsumer = (ClientSessionInternal) csf.createSession(configuration.getUser(), configuration.getPassword(), false, true, true, true, 1, configuration.getClientId());
               }

               if (configuration.getForwardingAddress() != null) {
                  ClientSession.AddressQuery query = null;

                  try {
                     query = session.addressQuery(SimpleString.of(configuration.getForwardingAddress()));
                  } catch (Throwable e) {
                     ActiveMQServerLogger.LOGGER.errorQueryingBridge(configuration.getName(), e);
                     // This was an issue during startup, we will not count this retry
                     retryCount--;

                     scheduleRetryConnectFixedTimeout(100);
                     return;
                  }

                  if (!query.isExists()) {
                     ActiveMQServerLogger.LOGGER.errorQueryingBridge(configuration.getForwardingAddress(), retryCount);
                     scheduleRetryConnect();
                     return;
                  }
               }

               // need to reset blockedOnFlowControl after creating a new producer
               // otherwise in case the bridge was blocked before a previous failure
               // this would never resume
               blockedOnFlowControl = false;
               producer = session.createProducer();
               session.addFailureListener(BridgeImpl.this);

               session.setSendAcknowledgementHandler(BridgeImpl.this);

               afterConnect();

               state = State.STARTED;

               queue.addConsumer(BridgeImpl.this);
               queue.deliverAsync();

               ActiveMQServerLogger.LOGGER.bridgeConnected(BridgeImpl.this);

               serverLocator.addClusterTopologyListener(new ClusterTopologyListener() {
                  @Override
                  public void nodeUP(TopologyMember member, boolean last) {
                     BridgeImpl.this.nodeUP(member, last);
                  }

                  @Override
                  public void nodeDown(long eventUID, String nodeID) {
                  }
               });

               keepConnecting = false;
            } catch (ActiveMQException e) {
               // the session was created while its server was starting, retry it:
               if (e.getType() == ActiveMQExceptionType.SESSION_CREATION_REJECTED) {
                  ActiveMQServerLogger.LOGGER.errorStartingBridge(configuration.getName());

                  // We are not going to count this one as a retry
                  retryCount--;

                  scheduleRetryConnectFixedTimeout(configuration.getRetryInterval());
               } else {
                  ActiveMQServerLogger.LOGGER.errorConnectingBridgeRetry(BridgeImpl.this);
                  logger.debug("Underlying bridge connection failure", e);

                  scheduleRetryConnect();
               }
            } catch (ActiveMQInterruptedException | InterruptedException e) {
               ActiveMQServerLogger.LOGGER.errorConnectingBridge(BridgeImpl.this, e);
            } catch (Exception e) {
               ActiveMQServerLogger.LOGGER.errorConnectingBridge(BridgeImpl.this, e);
               if (csf != null) {
                  try {
                     csf.close();
                     csf = null;
                  } catch (Throwable ignored) {
                  }
               }
               fail(false, false);
               scheduleRetryConnect();
            }
         }
      }
   }

   private class StopRunnable implements Runnable {

      @Override
      public void run() {
         try {
            logger.debug("stopping bridge {}", BridgeImpl.this);
            logger.trace("Removing consumer on stopRunnable {} from queue {}", this, queue);
            queue.removeConsumer(BridgeImpl.this);

            if (!pendingAcks.await(configuration.getPendingAckTimeout(), TimeUnit.MILLISECONDS)) {
               ActiveMQServerLogger.LOGGER.timedOutWaitingForSendAcks("Stopping", configuration.getName(), pendingAcks.getCount());
            }

            statusLock.lock();
            try {
               state = State.STOPPED;
            } finally {
               statusLock.unlock();
            }

            if (session != null) {
               logger.debug("Cleaning up session {} for bridge {}", session, configuration.getName());
               session.removeFailureListener(BridgeImpl.this);
               try {
                  session.close();
                  session = null;
               } catch (ActiveMQException ignored) {
               }
            }

            if (sessionConsumer != null) {
               logger.debug("Cleaning up session {}", session);
               try {
                  sessionConsumer.close();
                  sessionConsumer = null;
               } catch (ActiveMQException ignored) {
               }
            }

            internalCancelReferences();

            if (csf != null) {
               csf.cleanup();
            }

            synchronized (connectionGuard) {
               keepConnecting = true;
            }

            sendNotification(CoreNotificationType.BRIDGE_STOPPED);

            ActiveMQServerLogger.LOGGER.bridgeStopped(configuration.getName());
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.errorStoppingBridge(configuration.getName(), e);
         }
      }
   }

   private class PauseRunnable implements Runnable {

      @Override
      public void run() {
         try {
            logger.debug("pausing bridge {}", BridgeImpl.this);
            logger.trace("Removing consumer on pauseRunnable {} from queue {}", this, queue);
            queue.removeConsumer(BridgeImpl.this);

            if (!pendingAcks.await(configuration.getPendingAckTimeout(), TimeUnit.MILLISECONDS)) {
               ActiveMQServerLogger.LOGGER.timedOutWaitingForSendAcks("Pausing", configuration.getName(), pendingAcks.getCount());
            }

            statusLock.lock();
            try {
               state = State.PAUSED;
            } finally {
               statusLock.unlock();
            }

            internalCancelReferences();

            sendNotification(CoreNotificationType.BRIDGE_STOPPED);

            ActiveMQServerLogger.LOGGER.bridgePaused(configuration.getName());
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.errorPausingBridge(configuration.getName(), e);
         }
      }
   }

   private void sendNotification(CoreNotificationType type) {
      if (notificationService != null) {
         TypedProperties props = new TypedProperties();
         props.putSimpleStringProperty(SimpleString.of("name"), getName());
         Notification notification = new Notification(nodeUUID.toString(), type, props);
         try {
            notificationService.sendNotification(notification);
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.notificationBridgeError(configuration.getName(), type, e);
         }
      }
   }
}
