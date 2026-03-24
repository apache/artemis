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
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryImpl;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryInternal;
import org.apache.activemq.artemis.core.client.impl.ClientSessionInternal;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorInternal;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.ComponentConfigurationRoutingType;
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
import org.jboss.logging.Logger;

/**
 * A Core BridgeImpl
 */

public class BridgeImpl implements Bridge, SessionFailureListener, SendAcknowledgementHandler, ReadyListener, ClientProducerFlowCallback {


   private static final Logger logger = Logger.getLogger(BridgeImpl.class);



   protected final ServerLocatorInternal serverLocator;

   protected final Executor executor;

   protected final ScheduledExecutorService scheduledExecutor;

   private final ReusableLatch pendingAcks = new ReusableLatch(0);

   private final UUID nodeUUID;

   private final long sequentialID;

   private final SimpleString name;

   protected final Queue queue;

   private final Filter filter;

   private final SimpleString forwardingAddress;

   final java.util.Map<Long, MessageReference> refs = new LinkedHashMap<>();

   private final Transformer transformer;

   private final Object connectionGuard = new Object();

   private final boolean useDuplicateDetection;

   private final String user;

   private final String password;

   private final int reconnectAttempts;

   private final int reconnectAttemptsSameNode;

   private final long retryInterval;

   private final double retryMultiplier;

   private final long maxRetryInterval;

   private boolean blockedOnFlowControl;

   /**
    * Used when there's a scheduled reconnection
    */
   protected ScheduledFuture<?> futureScheduledReconnection;

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

   private volatile boolean started;

   private volatile boolean stopping = false;

   private volatile boolean active;

   private boolean deliveringLargeMessage;

   private int reconnectAttemptsInUse;

   private int retryCount = 0;

   private NotificationService notificationService;

   private boolean keepConnecting = true;

   private ActiveMQServer server;

   private final BridgeMetrics metrics = new BridgeMetrics();

   private final ComponentConfigurationRoutingType routingType;

   public BridgeImpl(final ServerLocatorInternal serverLocator,
                     final int initialConnectAttempts,
                     final int reconnectAttempts,
                     final int reconnectAttemptsSameNode,
                     final long retryInterval,
                     final double retryMultiplier,
                     final long maxRetryInterval,
                     final UUID nodeUUID,
                     final SimpleString name,
                     final Queue queue,
                     final Executor executor,
                     final Filter filter,
                     final SimpleString forwardingAddress,
                     final ScheduledExecutorService scheduledExecutor,
                     final Transformer transformer,
                     final boolean useDuplicateDetection,
                     final String user,
                     final String password,
                     final ActiveMQServer server,
                     final ComponentConfigurationRoutingType routingType) {

      this.sequentialID = server.getStorageManager().generateID();

      this.reconnectAttempts = reconnectAttempts;

      this.reconnectAttemptsInUse = initialConnectAttempts;

      this.reconnectAttemptsSameNode = reconnectAttemptsSameNode;

      this.retryInterval = retryInterval;

      this.retryMultiplier = retryMultiplier;

      this.maxRetryInterval = maxRetryInterval;

      this.serverLocator = serverLocator;

      this.nodeUUID = nodeUUID;

      this.name = name;

      this.queue = queue;

      this.executor = executor;

      this.scheduledExecutor = scheduledExecutor;

      this.filter = filter;

      this.forwardingAddress = forwardingAddress;

      this.transformer = transformer;

      this.useDuplicateDetection = useDuplicateDetection;

      this.user = user;

      this.password = password;

      this.server = server;

      this.routingType = routingType;
   }

   /** For tests mainly */
   public boolean isBlockedOnFlowControl() {
      return blockedOnFlowControl;
   }

   public static final byte[] getDuplicateBytes(final UUID nodeUUID, final long messageID) {
      byte[] bytes = new byte[24];

      ByteBuffer bb = ByteBuffer.wrap(bytes);

      bb.put(nodeUUID.asBytes());

      bb.putLong(messageID);

      return bytes;
   }

   // for tests
   public ClientSessionFactory getSessionFactory() {
      return csf;
   }

   /* (non-Javadoc)
    * @see org.apache.activemq.artemis.core.server.Consumer#getDeliveringMessages()
    */
   @Override
   public List<MessageReference> getDeliveringMessages() {
      synchronized (refs) {
         return new ArrayList<>(refs.values());
      }
   }

   private static void cleanUpSessionFactory(ClientSessionFactoryInternal factory) {
      if (factory != null)
         factory.cleanup();
   }

   @Override
   public void setNotificationService(final NotificationService notificationService) {
      this.notificationService = notificationService;
   }

   @Override
   public void onCreditsFlow(boolean blocked, ClientProducerCredits producerCredits) {
      this.blockedOnFlowControl = blocked;
      if (!blocked) {
         queue.deliverAsync();
      }
   }

   @Override
   public void onCreditsFail(ClientProducerCredits producerCredits) {
      ActiveMQServerLogger.LOGGER.bridgeAddressFull("" + producerCredits.getAddress(), "" + this.getName());
      disconnect();
   }

   @Override
   public long sequentialID() {
      return sequentialID;
   }

   @Override
   public synchronized void start() throws Exception {
      if (started) {
         return;
      }

      started = true;

      stopping = false;

      activate();

      if (notificationService != null) {
         TypedProperties props = new TypedProperties();
         props.putSimpleStringProperty(new SimpleString("name"), name);
         Notification notification = new Notification(nodeUUID.toString(), CoreNotificationType.BRIDGE_STARTED, props);
         notificationService.sendNotification(notification);
      }
   }

   @Override
   public String debug() {
      return toString();
   }

   private void cancelRefs() {
      LinkedList<MessageReference> list = new LinkedList<>();

      synchronized (refs) {
         list.addAll(refs.values());
         refs.clear();
      }

      if (logger.isTraceEnabled()) {
         logger.trace("BridgeImpl::cancelRefs cancelling " + list.size() + " references");
      }

      if (logger.isTraceEnabled() && list.isEmpty()) {
         logger.trace("didn't have any references to cancel on bridge " + this);
         return;
      }

      ListIterator<MessageReference> listIterator = list.listIterator(list.size());

      Queue refqueue;

      long timeBase = System.currentTimeMillis();

      while (listIterator.hasPrevious()) {
         MessageReference ref = listIterator.previous();

         if (logger.isTraceEnabled()) {
            logger.trace("BridgeImpl::cancelRefs Cancelling reference " + ref + " on bridge " + this);
         }

         refqueue = ref.getQueue();

         try {
            refqueue.cancel(ref, timeBase);
         } catch (Exception e) {
            // There isn't much we can do besides log an error
            ActiveMQServerLogger.LOGGER.errorCancellingRefOnBridge(e, ref);
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
      executor.execute(new Runnable() {
         @Override
         public void run() {
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
         }
      });
   }

   @Override
   public boolean isConnected() {
      return session != null;
   }

   /**
    * The cluster manager needs to use the same executor to close the serverLocator, otherwise the stop will break.
    * This method is intended to expose this executor to the ClusterManager
    */
   public Executor getExecutor() {
      return executor;
   }

   @Override
   public void stop() throws Exception {
      if (stopping) {
         return;
      }

      stopping = true;

      if (logger.isDebugEnabled()) {
         logger.debug("Bridge " + this.name + " being stopped");
      }

      if (futureScheduledReconnection != null) {
         futureScheduledReconnection.cancel(true);
      }

      executor.execute(new StopRunnable());

      if (notificationService != null) {
         TypedProperties props = new TypedProperties();
         props.putSimpleStringProperty(new SimpleString("name"), name);
         Notification notification = new Notification(nodeUUID.toString(), CoreNotificationType.BRIDGE_STOPPED, props);
         try {
            notificationService.sendNotification(notification);
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.broadcastBridgeStoppedError(e);
         }
      }
   }

   @Override
   public void pause() throws Exception {
      if (logger.isDebugEnabled()) {
         logger.debug("Bridge " + this.name + " being paused");
      }

      executor.execute(new PauseRunnable());

      if (notificationService != null) {
         TypedProperties props = new TypedProperties();
         props.putSimpleStringProperty(new SimpleString("name"), name);
         Notification notification = new Notification(nodeUUID.toString(), CoreNotificationType.BRIDGE_STOPPED, props);
         try {
            notificationService.sendNotification(notification);
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.notificationBridgeStoppedError(e);
         }
      }
   }

   @Override
   public void resume() throws Exception {
      queue.addConsumer(BridgeImpl.this);
      queue.deliverAsync();
   }

   @Override
   public boolean isStarted() {
      return started;
   }

   public synchronized void activate() {
      executor.execute(new ConnectRunnable(this));
   }

   @Override
   public SimpleString getName() {
      return name;
   }

   @Override
   public Queue getQueue() {
      return queue;
   }

   @Override
   public Filter getFilter() {
      return filter;
   }

   // SendAcknowledgementHandler implementation ---------------------

   @Override
   public SimpleString getForwardingAddress() {
      return forwardingAddress;
   }

   // For testing only
   @Override
   public RemotingConnection getForwardingConnection() {
      if (session == null) {
         return null;
      } else {
         return session.getConnection();
      }
   }

   // Consumer implementation ---------------------------------------

   @Override
   public void sendAcknowledged(final Message message) {
      if (logger.isTraceEnabled()) {
         logger.trace("BridgeImpl::sendAcknowledged received confirmation for message " + message);
      }
      if (active) {
         try {

            final MessageReference ref;

            synchronized (refs) {
               ref = refs.remove(message.getMessageID());
            }

            if (ref != null) {
               if (logger.isTraceEnabled()) {
                  logger.trace("BridgeImpl::sendAcknowledged bridge " + this + " Acking " + ref + " on queue " + ref.getQueue());
               }
               ref.getQueue().acknowledge(ref);
               pendingAcks.countDown();
               metrics.incrementMessagesAcknowledged();

               if (server.hasBrokerBridgePlugins()) {
                  server.callBrokerBridgePlugins(plugin -> plugin.afterAcknowledgeBridge(this, ref));
               }
            } else {
               if (logger.isTraceEnabled()) {
                  logger.trace("BridgeImpl::sendAcknowledged bridge " + this + " could not find reference for message " + message);
               }
            }
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.bridgeFailedToAck(e);
         }
      }
   }

   /* Hook for processing message before forwarding */
   protected Message beforeForward(Message message, final SimpleString forwardingAddress) {
      message = message.copy();
      ((RefCountMessage)message).setParentRef((RefCountMessage)message);

      return beforeForwardingNoCopy(message, forwardingAddress);
   }

   /** ClusterConnectionBridge already makes a copy of the message.
    * So I needed I hook where the message is not copied. */
   protected Message beforeForwardingNoCopy(Message message, SimpleString forwardingAddress) {
      if (useDuplicateDetection) {
         // We keep our own DuplicateID for the Bridge, so bouncing back and forth will work fine
         byte[] bytes = getDuplicateBytes(nodeUUID, message.getMessageID());

         message.putExtraBytesProperty(Message.HDR_BRIDGE_DUPLICATE_ID, bytes);
      }

      if (forwardingAddress != null) {
         // for AMQP messages this modification will be transient
         message.setAddress(forwardingAddress);
      }

      switch (routingType) {
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
            if (logger.isDebugEnabled()) {
               logger.debug("The transformer " + transformer +
                               " made a copy of the message " +
                               message +
                               " as transformedMessage");
            }
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
      if (filter != null && !filter.match(ref.getMessage())) {
         return HandleStatus.NO_MATCH;
      }

      synchronized (this) {
         if (!active || !session.isWritable(this)) {
            if (logger.isDebugEnabled()) {
               logger.debug(this + "::Ignoring reference on bridge as it is set to inactive ref=" + ref);
            }
            return HandleStatus.BUSY;
         }

         if (blockedOnFlowControl) {
            return HandleStatus.BUSY;
         }

         if (deliveringLargeMessage) {
            return HandleStatus.BUSY;
         }

         if (logger.isTraceEnabled()) {
            logger.trace("Bridge " + this + " is handling reference=" + ref);
         }

         ref.handled();

         synchronized (refs) {
            refs.put(ref.getMessage().getMessageID(), ref);
         }

         final SimpleString dest;

         if (forwardingAddress != null) {
            dest = forwardingAddress;
         } else {
            // Preserve the original address
            dest = ref.getMessage().getAddressSimpleString();
         }

         final Message message = beforeForward(ref.getMessage(), dest);

         pendingAcks.countUp();

         try {
            if (server.hasBrokerBridgePlugins()) {
               server.callBrokerBridgePlugins(plugin -> plugin.beforeDeliverBridge(this, ref));
            }

            final HandleStatus status;
            if (message.isLargeMessage()) {
               deliveringLargeMessage = true;
               deliverLargeMessage(dest, ref, (LargeServerMessage) message, ref.getMessage());
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
         ActiveMQServerLogger.LOGGER.bridgeConnectionFailed(failedOver);
      }

      synchronized (connectionGuard) {
         keepConnecting = true;
      }

      try {
         if (producer != null) {
            producer.close();
         }

         cleanUpSessionFactory(csf);
      } catch (Throwable dontCare) {
      }

      try {
         session.cleanUp(false);
      } catch (Throwable dontCare) {
      }

      if (scaleDownTargetNodeID != null && !scaleDownTargetNodeID.equals(nodeUUID.toString())) {
         scaleDown(scaleDownTargetNodeID);
      } else if (scaleDownTargetNodeID != null) {
         // the disconnected node is scaling down to me, no need to reconnect to it
         logger.debug("Received scaleDownTargetNodeID: " + scaleDownTargetNodeID + "; cancelling reconnect.");
         fail(true, true);
      } else {
         logger.debug("Received invalid scaleDownTargetNodeID: " + scaleDownTargetNodeID);

         fail(me.getType() == ActiveMQExceptionType.DISCONNECTED, false);
      }

      tryScheduleRetryReconnect(me.getType());
   }

   protected void scaleDown(String scaleDownTargetNodeID) {
      synchronized (this) {
         try {
            logger.debug("Moving " + queue.getMessageCount() + " messages from " + queue.getName() + " to " + scaleDownTargetNodeID);
            ((QueueImpl) queue).moveReferencesBetweenSnFQueues(SimpleString.toSimpleString(scaleDownTargetNodeID));

            // stop the bridge from trying to reconnect and clean up all the bindings
            fail(true, true);

         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.warn(e.getMessage(), e);
         }
      }
   }

   protected void tryScheduleRetryReconnect(final ActiveMQExceptionType type) {
      scheduleRetryConnect();
   }

   @Override
   public void beforeReconnect(final ActiveMQException exception) {
      // log.warn(name + "::Connection failed before reconnect ", exception);
      // fail(false);
   }

   private void deliverLargeMessage(final SimpleString dest,
                                    final MessageReference ref,
                                    final LargeServerMessage message,
                                    final Message originalMessage) {
      executor.execute(new Runnable() {
         @Override
         public void run() {
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
               ActiveMQServerLogger.LOGGER.bridgeUnableToSendMessage(e, ref);

               connectionFailed(e, false);
            }
         }
      });
   }

   /**
    * @param ref
    * @param message
    * @return
    */
   private HandleStatus deliverStandardMessage(SimpleString dest, final MessageReference ref, Message message, Message originalMessage) {
      // if we failover during send then there is a chance that the
      // that this will throw a disconnect, we need to remove the message
      // from the acks so it will get resent, duplicate detection will cope
      // with any messages resent

      if (logger.isTraceEnabled()) {
         logger.trace("going to send message: " + message + " from " + this.getQueue());
      }

      try {
         producer.send(dest, message);
      } catch (final ActiveMQException e) {
         ActiveMQServerLogger.LOGGER.bridgeUnableToSendMessage(e, ref);

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
    *
    * @return
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
         name +
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
         name +
         ", queue=" +
         queue.getName() + "/" + queue.getID() + "]";
   }

   public ClientSessionFactoryImpl getCSF() {
      return (ClientSessionFactoryImpl) csf;
   }

   public Transformer getTransformer() {
      return transformer;
   }

   protected void fail(final boolean permanently, boolean scaleDown) {
      logger.debug(this + "\n\t::fail being called, permanently=" + permanently);
      //we need to make sure we remove the node from the topology so any incoming quorum requests are voted correctly
      if (targetNodeID != null) {
         this.disconnectedAndDown = true;
         serverLocator.notifyNodeDown(System.currentTimeMillis(), targetNodeID);
      }
      if (queue != null) {
         try {
            if (logger.isTraceEnabled()) {
               logger.trace("Removing consumer on fail " + this + " from queue " + queue);
            }
            queue.removeConsumer(this);
         } catch (Exception dontcare) {
            logger.debug(dontcare);
         }
      }

      cancelRefs();
      if (queue != null) {
         queue.deliverAsync();
      }
   }

   /* Hook for doing extra stuff after connection */
   protected void afterConnect() throws Exception {
      if (disconnectedAndDown && targetNodeID != null && targetNode != null) {
         serverLocator.notifyNodeUp(System.currentTimeMillis(), targetNodeID, targetNode.getBackupGroupName(), targetNode.getScaleDownGroupName(),
                                    new Pair<>(targetNode.getLive(), targetNode.getBackup()), false);
         disconnectedAndDown = false;
      }
      retryCount = 0;
      reconnectAttemptsInUse = reconnectAttempts;
      if (futureScheduledReconnection != null) {
         futureScheduledReconnection.cancel(true);
         futureScheduledReconnection = null;
      }
   }

   /* Hook for creating session factory */
   protected ClientSessionFactoryInternal createSessionFactory() throws Exception {
      if (targetNodeID != null && (this.reconnectAttemptsSameNode < 0 || retryCount <= this.reconnectAttemptsSameNode)) {
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
         TransportConfiguration[] configs = new TransportConfiguration[2]; // live and backup
         int numberOfConfigs = 0;

         if (nodeUse.getLive() != null) {
            configs[numberOfConfigs++] = nodeUse.getLive();
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

   /* This is called only when the bridge is activated */
   protected void connect() {
      if (stopping)
         return;

      synchronized (connectionGuard) {
         if (!keepConnecting)
            return;

         logger.debug("Connecting  " + this + " to its destination [" + nodeUUID.toString() + "], csf=" + this.csf);

         retryCount++;

         try {
            if (csf == null || csf.isClosed()) {
               if (stopping)
                  return;
               csf = createSessionFactory();
               if (csf == null) {
                  // Retrying. This probably means the node is not available (for the cluster connection case)
                  scheduleRetryConnect();
                  return;
               }
               // Session is pre-acknowledge
               session = (ClientSessionInternal) csf.createSession(user, password, false, true, true, true, 1);
               session.getProducerCreditManager().setCallback(this);
               sessionConsumer = (ClientSessionInternal) csf.createSession(user, password, false, true, true, true, 1);
            }

            if (forwardingAddress != null) {
               ClientSession.AddressQuery query = null;

               try {
                  query = session.addressQuery(forwardingAddress);
               } catch (Throwable e) {
                  ActiveMQServerLogger.LOGGER.errorQueryingBridge(e, name);
                  // This was an issue during startup, we will not count this retry
                  retryCount--;

                  scheduleRetryConnectFixedTimeout(100);
                  return;
               }

               if (!query.isExists()) {
                  ActiveMQServerLogger.LOGGER.errorQueryingBridge(forwardingAddress, retryCount);
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

            active = true;

            queue.addConsumer(BridgeImpl.this);
            queue.deliverAsync();

            ActiveMQServerLogger.LOGGER.bridgeConnected(this);

            serverLocator.addClusterTopologyListener(new TopologyListener());

            keepConnecting = false;
            return;
         } catch (ActiveMQException e) {
            // the session was created while its server was starting, retry it:
            if (e.getType() == ActiveMQExceptionType.SESSION_CREATION_REJECTED) {
               ActiveMQServerLogger.LOGGER.errorStartingBridge(name);

               // We are not going to count this one as a retry
               retryCount--;

               scheduleRetryConnectFixedTimeout(this.retryInterval);
               return;
            } else {
               ActiveMQServerLogger.LOGGER.errorConnectingBridgeRetry(this);
               if (logger.isDebugEnabled()) {
                  logger.debug("Underlying bridge connection failure", e);
               }
               scheduleRetryConnect();
            }
         } catch (ActiveMQInterruptedException | InterruptedException e) {
            ActiveMQServerLogger.LOGGER.errorConnectingBridge(e, this);
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.errorConnectingBridge(e, this);
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

   protected void scheduleRetryConnect() {
      if (serverLocator.isClosed()) {
         ActiveMQServerLogger.LOGGER.bridgeLocatorShutdown();
         return;
      }

      if (stopping) {
         ActiveMQServerLogger.LOGGER.bridgeStopping();
         return;
      }

      if (reconnectAttemptsInUse >= 0 && retryCount > reconnectAttemptsInUse) {
         ActiveMQServerLogger.LOGGER.bridgeAbortStart(name, retryCount, reconnectAttempts);
         fail(true, false);
         return;
      }

      long timeout = (long) (this.retryInterval * Math.pow(this.retryMultiplier, retryCount));
      if (timeout == 0) {
         timeout = this.retryInterval;
      }
      if (timeout > maxRetryInterval) {
         timeout = maxRetryInterval;
      }

      logger.debug("Bridge " + this +
                      " retrying connection #" +
                      retryCount +
                      ", maxRetry=" +
                      reconnectAttemptsInUse +
                      ", timeout=" +
                      timeout);

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

      if (stopping)
         return;

      if (logger.isDebugEnabled()) {
         logger.debug("Scheduling retry for bridge " + this.name + " in " + milliseconds + " milliseconds");
      }

      futureScheduledReconnection = scheduledExecutor.schedule(new FutureConnectRunnable(executor, this), milliseconds, TimeUnit.MILLISECONDS);
   }

   private void internalCancelReferences() {
      cancelRefs();

      if (queue != null) {
         queue.deliverAsync();
      }
   }

   /**
    * just set deliveringLargeMessage to false
    */
   private synchronized void unsetLargeMessageDelivery() {
      deliveringLargeMessage = false;
   }

   // The scheduling will still use the main executor here
   private static class FutureConnectRunnable implements Runnable {

      private final BridgeImpl bridge;

      private final Executor executor;

      private FutureConnectRunnable(Executor exe, BridgeImpl bridge) {
         executor = exe;
         this.bridge = bridge;
      }

      @Override
      public void run() {
         if (bridge.isStarted())
            executor.execute(new ConnectRunnable(bridge));
      }
   }

   private static final class ConnectRunnable implements Runnable {

      private final BridgeImpl bridge;

      private ConnectRunnable(BridgeImpl bridge2) {
         bridge = bridge2;
      }

      @Override
      public void run() {
         bridge.connect();
      }
   }

   private class StopRunnable implements Runnable {

      @Override
      public void run() {
         logger.debug("stopping bridge " + BridgeImpl.this);
         queue.removeConsumer(BridgeImpl.this);

         synchronized (BridgeImpl.this) {
            logger.debug("Closing Session for bridge " + BridgeImpl.this.name);

            started = false;

            active = false;

         }

         if (session != null) {
            logger.debug("Cleaning up session " + session);
            session.removeFailureListener(BridgeImpl.this);
            try {
               session.close();
               session = null;
            } catch (ActiveMQException dontcare) {
            }
         }

         if (sessionConsumer != null) {
            logger.debug("Cleaning up session " + session);
            try {
               sessionConsumer.close();
               sessionConsumer = null;
            } catch (ActiveMQException dontcare) {
            }
         }

         internalCancelReferences();

         if (csf != null) {
            csf.cleanup();
         }

         synchronized (connectionGuard) {
            keepConnecting = true;
         }

         if (logger.isTraceEnabled()) {
            logger.trace("Removing consumer on stopRunnable " + this + " from queue " + queue);
         }
         ActiveMQServerLogger.LOGGER.bridgeStopped(name);
      }
   }

   private class PauseRunnable implements Runnable {

      @Override
      public void run() {
         try {
            queue.removeConsumer(BridgeImpl.this);

            if (!pendingAcks.await(60, TimeUnit.SECONDS)) {
               ActiveMQServerLogger.LOGGER.timedOutWaitingCompletions(BridgeImpl.this.toString(), pendingAcks.getCount());
            }

            synchronized (BridgeImpl.this) {
               started = false;
               active = false;
            }

            internalCancelReferences();

            ActiveMQServerLogger.LOGGER.bridgePaused(name);
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.errorPausingBridge(e);
         }
      }

   }

   private class TopologyListener implements ClusterTopologyListener {

      // ClusterListener
      @Override
      public void nodeUP(TopologyMember member, boolean last) {
         BridgeImpl.this.nodeUP(member, last);
      }

      @Override
      public void nodeDown(long eventUID, String nodeID) {

      }
   }
}
