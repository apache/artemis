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
package org.apache.activemq.artemis.core.client.impl;

import java.io.File;
import java.io.IOException;
import java.security.PrivilegedAction;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQInterruptedException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.MessageHandler;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.client.ActiveMQClientLogger;
import org.apache.activemq.artemis.core.client.ActiveMQClientMessageBundle;
import org.apache.activemq.artemis.spi.core.remoting.ConsumerContext;
import org.apache.activemq.artemis.spi.core.remoting.SessionContext;
import org.apache.activemq.artemis.utils.FutureLatch;
import org.apache.activemq.artemis.utils.ReusableLatch;
import org.apache.activemq.artemis.utils.TokenBucketLimiter;
import org.apache.activemq.artemis.utils.collections.LinkedListIterator;
import org.apache.activemq.artemis.utils.collections.PriorityLinkedList;
import org.apache.activemq.artemis.utils.collections.PriorityLinkedListImpl;
import org.apache.activemq.artemis.utils.sm.SecurityManagerShim;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public final class ClientConsumerImpl implements ClientConsumerInternal {


   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final int NUM_PRIORITIES = 10;

   public static final SimpleString FORCED_DELIVERY_MESSAGE = SimpleString.of("_hornetq.forced.delivery.seq");

   private final ClientSessionInternal session;

   private final SessionContext sessionContext;

   private final ConsumerContext consumerContext;

   private final SimpleString filterString;

   private final int priority;

   private final SimpleString queueName;

   private final boolean browseOnly;

   private final Executor sessionExecutor;

   // For failover we can't send credits back
   // while holding a lock or failover could dead lock eventually
   // And we can't use the sessionExecutor as that's being used for message handlers
   // for that reason we have a separate flowControlExecutor that's using the thread pool
   // Which is an OrderedExecutor
   private final Executor flowControlExecutor;

   // Number of pending calls on flow control
   private final ReusableLatch pendingFlowControl = new ReusableLatch(0);

   private final int initialWindow;

   private final int clientWindowSize;

   private final int ackBatchSize;

   private final PriorityLinkedList<ClientMessageInternal> buffer = new PriorityLinkedListImpl<>(ClientConsumerImpl.NUM_PRIORITIES);

   private final Runner runner = new Runner();

   private LargeMessageControllerImpl currentLargeMessageController;

   // When receiving LargeMessages, the user may choose to not read the body, on this case we need to discard the body
   // before moving to the next message.
   private ClientMessageInternal largeMessageReceived;

   private final TokenBucketLimiter rateLimiter;

   private volatile Thread receiverThread;

   private volatile Thread onMessageThread;

   private volatile MessageHandler handler;

   private volatile boolean closing;

   private volatile boolean closed;

   private int creditsToSend;

   private volatile boolean failedOver;

   private volatile Exception lastException;

   private int ackBytes;

   private volatile ClientMessageInternal lastAckedMessage;

   private boolean stopped = false;

   private AtomicLong forceDeliveryCount = new AtomicLong(0);

   private final ClientSession.QueueQuery queueInfo;

   private volatile boolean ackIndividually;

   private final ClassLoader contextClassLoader;
   private volatile boolean manualFlowManagement;

   private final int onMessageCloseTimeout;

   public ClientConsumerImpl(final ClientSessionInternal session,
                             final ConsumerContext consumerContext,
                             final SimpleString queueName,
                             final SimpleString filterString,
                             final int priority,
                             final boolean browseOnly,
                             final int initialWindow,
                             final int clientWindowSize,
                             final int ackBatchSize,
                             final TokenBucketLimiter rateLimiter,
                             final Executor executor,
                             final Executor flowControlExecutor,
                             final SessionContext sessionContext,
                             final ClientSession.QueueQuery queueInfo,
                             final ClassLoader contextClassLoader,
                             final int onMessageCloseTimeout) {
      this.consumerContext = consumerContext;

      this.queueName = queueName;

      this.filterString = filterString;

      this.priority = priority;

      this.browseOnly = browseOnly;

      this.sessionContext = sessionContext;

      this.session = session;

      this.rateLimiter = rateLimiter;

      sessionExecutor = executor;

      this.initialWindow = initialWindow;

      this.clientWindowSize = clientWindowSize;

      this.ackBatchSize = ackBatchSize;

      this.queueInfo = queueInfo;

      this.contextClassLoader = contextClassLoader;

      this.flowControlExecutor = flowControlExecutor;

      this.onMessageCloseTimeout = onMessageCloseTimeout;

      if (logger.isTraceEnabled()) {
         logger.trace("{}:: being created at", this, new Exception("trace"));
      }
   }

   // ClientConsumer implementation
   // -----------------------------------------------------------------

   @Override
   public ConsumerContext getConsumerContext() {
      return consumerContext;
   }

   private ClientMessage receive(final long timeout, final boolean forcingDelivery) throws ActiveMQException {
      if (logger.isTraceEnabled()) {
         logger.trace("{}::receive({}, {})", this, timeout, forcingDelivery);
      }

      checkClosed();

      if (largeMessageReceived != null) {
         if (logger.isTraceEnabled()) {
            logger.trace("{}::receive({}, {}) -> discard LargeMessage body for {}", this, timeout, forcingDelivery, largeMessageReceived);
         }
         // Check if there are pending packets to be received
         largeMessageReceived.discardBody();
         largeMessageReceived = null;
      }

      if (rateLimiter != null) {
         rateLimiter.limit();
      }

      if (handler != null) {
         if (logger.isTraceEnabled()) {
            logger.trace("{}::receive({}, {}) -> throwing messageHandlerSet", this, timeout, forcingDelivery);
         }
         throw ActiveMQClientMessageBundle.BUNDLE.messageHandlerSet();
      }

      if (clientWindowSize == 0) {
         if (logger.isTraceEnabled()) {
            logger.trace("{}::receive({}, {}) -> start slowConsumer", this, timeout, forcingDelivery);
         }
         startSlowConsumer();
      }

      receiverThread = Thread.currentThread();

      // To verify if deliveryForced was already call
      boolean deliveryForced = false;
      // To control when to call deliveryForce
      boolean callForceDelivery = false;

      long start = -1;

      long toWait = timeout == 0 ? Long.MAX_VALUE : timeout;

      try {
         while (true) {
            ClientMessageInternal m = null;

            synchronized (this) {
               while ((stopped || (m = buffer.poll()) == null) && !closed && toWait > 0) {
                  if (start == -1) {
                     start = System.currentTimeMillis();
                  }

                  if (m == null && forcingDelivery) {
                     if (stopped) {
                        break;
                     }

                     // we only force delivery once per call to receive
                     if (!deliveryForced) {
                        callForceDelivery = true;
                        break;
                     }
                  }

                  try {
                     wait(toWait);
                  } catch (InterruptedException e) {
                     throw new ActiveMQInterruptedException(e);
                  }

                  if (m != null || closed) {
                     break;
                  }

                  long now = System.currentTimeMillis();

                  toWait -= now - start;

                  start = now;
               }
            }

            if (failedOver) {
               if (m == null) {
                  if (logger.isTraceEnabled()) {
                     logger.trace("{}::receive({}, {}) -> m == null and failover", this, timeout, forcingDelivery);
                  }

                  // if failed over and the buffer is null, we reset the state and try it again
                  failedOver = false;
                  deliveryForced = false;
                  toWait = timeout == 0 ? Long.MAX_VALUE : timeout;
                  continue;
               } else {
                  if (logger.isTraceEnabled()) {
                     logger.trace("{}::receive({}, {}) -> failedOver, but m != null, being {}", this, timeout, forcingDelivery, m);
                  }
                  failedOver = false;
               }
            }

            if (callForceDelivery) {
               logger.trace("{}::Forcing delivery", this);
               // Calling forceDelivery outside of the lock to avoid distributed dead locks
               sessionContext.forceDelivery(this, forceDeliveryCount.getAndIncrement());
               callForceDelivery = false;
               deliveryForced = true;
               continue;
            }

            if (m != null) {
               session.workDone();

               if (m.containsProperty(ClientConsumerImpl.FORCED_DELIVERY_MESSAGE)) {
                  long seq = m.getLongProperty(ClientConsumerImpl.FORCED_DELIVERY_MESSAGE);

                  // Need to check if forceDelivery was called at this call
                  // As we could be receiving a message that came from a previous call
                  if (forcingDelivery && deliveryForced && seq == forceDeliveryCount.get() - 1) {
                     // forced delivery messages are discarded, nothing has been delivered by the queue
                     resetIfSlowConsumer();
                     logger.trace("{}::There was nothing on the queue, leaving it now:: returning null", this);

                     return null;
                  } else {
                     logger.trace("{}::Ignored force delivery answer as it belonged to another call", this);
                     // Ignore the message
                     continue;
                  }
               }
               // if we have already pre acked we can't expire
               boolean expired = m.isExpired();

               flowControlBeforeConsumption(m);

               if (expired) {
                  m.discardBody();

                  session.expire(this, m);

                  if (clientWindowSize == 0) {
                     startSlowConsumer();
                  }

                  if (toWait > 0) {
                     continue;
                  } else {
                     return null;
                  }
               }

               if (m.isLargeMessage()) {
                  largeMessageReceived = m;
               }

               logger.trace("{}::Returning {}", this, m);

               return m;
            } else {
               logger.trace("{}::Returning null", this);
               resetIfSlowConsumer();
               return null;
            }
         }
      } finally {
         receiverThread = null;
      }
   }

   @Override
   public ClientMessage receive(final long timeout) throws ActiveMQException {
      logger.trace("{}:: receive({})", this, timeout);
      ClientMessage msg = receive(timeout, false);

      if (msg == null && !closed) {
         if (logger.isTraceEnabled()) {
            logger.trace("{}:: receive({}) -> null, trying again with receive(0)", this, timeout);
         }
         msg = receive(0, true);
      }

      logger.trace("{}:: returning {}", this, msg);

      return msg;
   }

   @Override
   public ClientMessage receive() throws ActiveMQException {
      return receive(0, false);
   }

   @Override
   public ClientMessage receiveImmediate() throws ActiveMQException {
      return receive(0, true);
   }

   @Override
   public MessageHandler getMessageHandler() throws ActiveMQException {
      checkClosed();

      return handler;
   }

   @Override
   public Thread getCurrentThread() {
      if (onMessageThread != null) {
         return onMessageThread;
      }
      return receiverThread;
   }

   @Override
   public ClientConsumer setManualFlowMessageHandler(final MessageHandler theHandler) throws ActiveMQException {
      checkClosed();
      this.handler = theHandler;
      this.manualFlowManagement = true;
      return this;
   }

   // Must be synchronized since messages may be arriving while handler is being set and might otherwise end
   // up not queueing enough executors - so messages get stranded
   @Override
   public synchronized ClientConsumerImpl setMessageHandler(final MessageHandler theHandler) throws ActiveMQException {
      checkClosed();

      if (receiverThread != null) {
         throw ActiveMQClientMessageBundle.BUNDLE.inReceive();
      }

      boolean noPreviousHandler = handler == null;

      if (handler != theHandler && clientWindowSize == 0) {
         startSlowConsumer();
      }

      handler = theHandler;

      // if no previous handler existed queue up messages for delivery
      if (handler != null && noPreviousHandler) {
         requeueExecutors();
      } else if (handler == null && !noPreviousHandler) {
         // if unsetting a previous handler may be in onMessage so wait for completion
         waitForOnMessageToComplete(true);
      }

      return this;
   }

   @Override
   public void close() throws ActiveMQException {
      doCleanUp(true);
   }

   /**
    * To be used by MDBs to stop any more handling of messages.
    *
    * @param future the future to run once the onMessage Thread has completed
    */
   @Override
   public Thread prepareForClose(final FutureLatch future) throws ActiveMQException {
      closing = true;

      resetLargeMessageController();

      //execute the future after the last onMessage call
      sessionExecutor.execute(future::run);

      return onMessageThread;
   }

   @Override
   public void cleanUp() {
      try {
         doCleanUp(false);
      } catch (ActiveMQException e) {
         ActiveMQClientLogger.LOGGER.failedCleaningUp(this.toString());
      }
   }

   @Override
   public boolean isClosed() {
      return closed;
   }

   @Override
   public void stop(final boolean waitForOnMessage) throws ActiveMQException {
      if (browseOnly) {
         // stop shouldn't affect browser delivery
         return;
      }

      synchronized (this) {
         if (stopped) {
            return;
         }

         stopped = true;
      }
      waitForOnMessageToComplete(waitForOnMessage);
   }

   @Override
   public void clearAtFailover() {
      logger.trace("{}::ClearAtFailover", this);
      clearBuffer();

      // failover will issue a start later
      this.stopped = true;

      resetLargeMessageController();

      lastAckedMessage = null;

      creditsToSend = 0;

      failedOver = true;

      ackIndividually = false;
   }

   @Override
   public synchronized void start() {
      stopped = false;

      requeueExecutors();
   }

   @Override
   public Exception getLastException() {
      return lastException;
   }

   // ClientConsumerInternal implementation
   // --------------------------------------------------------------

   @Override
   public ClientSession.QueueQuery getQueueInfo() {
      return queueInfo;
   }

   @Override
   public long getForceDeliveryCount() {
      return forceDeliveryCount.get();
   }

   @Override
   public SimpleString getFilterString() {
      return filterString;
   }

   @Override
   public int getPriority() {
      return priority;
   }

   @Override
   public SimpleString getQueueName() {
      return queueName;
   }

   @Override
   public boolean isBrowseOnly() {
      return browseOnly;
   }

   @Override
   public synchronized void handleMessage(final ClientMessageInternal message) throws Exception {
      if (closing) {
         // This is ok - we just ignore the message
         return;
      }

      if (message.getBooleanProperty(Message.HDR_LARGE_COMPRESSED)) {
         handleCompressedMessageSentAsRegular(message);
      } else {
         handleRegularMessage(message);
      }
   }

   private void handleRegularMessage(ClientMessageInternal message) {
      if (message.getAddress() == null) {
         message.setAddress(queueInfo.getAddress());
      }

      message.onReceipt(this);

      if (!ackIndividually && message.getPriority() != 4 && !message.containsProperty(ClientConsumerImpl.FORCED_DELIVERY_MESSAGE)) {
         // We have messages of different priorities so we need to ack them individually since the order
         // of them in the ServerConsumerImpl delivery list might not be the same as the order they are
         // consumed in, which means that acking all up to won't work
         ackIndividually = true;
      }

      // Add it to the buffer
      buffer.addTail(message, message.getPriority());

      if (handler != null) {
         // Execute using executor
         if (!stopped) {
            queueExecutor();
         }
      } else {
         notify();
      }
   }

   /**
    * This method deals with messages arrived as regular message but its contents are compressed. Such messages come
    * from message senders who are configured to compress large messages, and if some of the messages are compressed
    * below the min-large-message-size limit, they are sent as regular messages.
    * <p>
    * However when decompressing the message, we are not sure how large the message could be.. for that reason we fake a
    * large message controller that will deal with the message as it was a large message
    * <p>
    * Say that you sent a 1G message full of spaces. That could be just bellow 100K compressed but you wouldn't have
    * enough memory to decompress it
    */
   private void handleCompressedMessageSentAsRegular(final ClientMessageInternal clMessage) throws Exception {
      ClientLargeMessageImpl largeMessage = new ClientLargeMessageImpl();
      largeMessage.retrieveExistingData(clMessage);

      File largeMessageCache = null;

      if (session.isCacheLargeMessageClient()) {
         largeMessageCache = createLargeMessageCache(largeMessage.getMessageID());
      }

      ClientSessionFactory sf = session.getSessionFactory();
      ServerLocator locator = sf.getServerLocator();
      long callTimeout = locator.getCallTimeout();

      currentLargeMessageController = new LargeMessageControllerImpl(this, largeMessage.getLargeMessageSize(), callTimeout, largeMessageCache);
      currentLargeMessageController.setOriginallyRegular(true);

      //sets the packet
      ActiveMQBuffer qbuff = clMessage.toCore().getBodyBuffer();
      final int bytesToRead = qbuff.writerIndex() - qbuff.readerIndex();
      final byte[] body = new byte[bytesToRead];
      qbuff.readBytes(body);
      largeMessage.setLargeMessageController(new CompressedLargeMessageControllerImpl(currentLargeMessageController));
      // this is refeeding the packet after decompressed, hence the flow control must be 0
      currentLargeMessageController.addPacket(body, 0, false);
      currentLargeMessageController = null;

      handleRegularMessage(largeMessage);
   }

   private File createLargeMessageCache(long messageId) throws IOException {
      File largeMessageCache = File.createTempFile("tmp-large-message-" + messageId + "-", ".tmp");
      largeMessageCache.setReadable(false, false);
      largeMessageCache.setExecutable(false, false);
      largeMessageCache.setWritable(false, false);
      largeMessageCache.setReadable(true, true);
      largeMessageCache.setWritable(true, true);
      largeMessageCache.deleteOnExit();
      return largeMessageCache;
   }

   @Override
   public synchronized void handleLargeMessage(final ClientLargeMessageInternal clientLargeMessage,
                                               long largeMessageSize) throws Exception {
      if (closing) {
         // This is ok - we just ignore the message
         return;
      }

      // Flow control for the first packet, we will have others
      File largeMessageCache = null;

      if (session.isCacheLargeMessageClient()) {
         largeMessageCache = createLargeMessageCache(clientLargeMessage.getMessageID());
      }

      ClientSessionFactory sf = session.getSessionFactory();
      ServerLocator locator = sf.getServerLocator();
      long callTimeout = locator.getCallTimeout();

      currentLargeMessageController = new LargeMessageControllerImpl(this, largeMessageSize, callTimeout, largeMessageCache);

      if (clientLargeMessage.isCompressed()) {
         clientLargeMessage.setLargeMessageController(new CompressedLargeMessageControllerImpl(currentLargeMessageController));
      } else {
         clientLargeMessage.setLargeMessageController(currentLargeMessageController);
      }

      handleRegularMessage(clientLargeMessage);
   }

   @Override
   public synchronized void handleLargeMessageContinuation(final byte[] chunk,
                                                           final int flowControlSize,
                                                           final boolean isContinues) throws Exception {
      if (closing) {
         return;
      }
      if (currentLargeMessageController == null) {
         if (logger.isTraceEnabled()) {
            logger.trace("{}::Sending back credits for largeController = null {}", this, flowControlSize);
         }
         flowControl(flowControlSize, false);
      } else {
         currentLargeMessageController.addPacket(chunk, flowControlSize, isContinues);
      }
   }

   @Override
   public void clear(boolean waitForOnMessage) throws ActiveMQException {
      synchronized (this) {
         // Need to send credits for the messages in the buffer

         try (LinkedListIterator<ClientMessageInternal> iter = buffer.iterator()) {
            while (iter.hasNext()) {
               ClientMessageInternal message = iter.next();

               if (message.isLargeMessage()) {
                  ClientLargeMessageInternal largeMessage = (ClientLargeMessageInternal) message;
                  largeMessage.getLargeMessageController().cancel();
               }

               flowControlBeforeConsumption(message);
            }
         } catch (Exception e) {
            ActiveMQClientLogger.LOGGER.errorClearingMessages(e);
         }

         clearBuffer();

         try {
            resetLargeMessageController();
         } catch (Throwable e) {
            // nothing that could be done here
            ActiveMQClientLogger.LOGGER.errorClearingMessages(e);
         }
      }

      // Need to send credits for the messages in the buffer

      waitForOnMessageToComplete(waitForOnMessage);
   }

   private void resetLargeMessageController() {

      LargeMessageController controller = currentLargeMessageController;
      if (controller != null) {
         controller.cancel();
         currentLargeMessageController = null;
      }
   }

   @Override
   public int getInitialWindowSize() {
      return initialWindow;
   }

   @Override
   public int getClientWindowSize() {
      return clientWindowSize;
   }

   @Override
   public int getBufferSize() {
      return buffer.size();
   }

   @Override
   public void acknowledge(final ClientMessage message) throws ActiveMQException {
      ClientMessageInternal cmi = (ClientMessageInternal) message;

      if (ackIndividually) {
         individualAcknowledge(message);
      } else {

         ackBytes += message.getEncodeSize();

         if (logger.isTraceEnabled()) {
            logger.trace("{}::acknowledge ackBytes={} and ackBatchSize={}, encodeSize={}", this, ackBytes, ackBatchSize, message.getEncodeSize());
         }

         if (ackBytes >= ackBatchSize) {
            logger.trace("{}:: acknowledge acking {}", this, cmi);
            doAck(cmi);
         } else {
            logger.trace("{}:: acknowledge setting lastAckedMessage = {}", this, cmi);
            lastAckedMessage = cmi;
         }
      }
   }

   @Override
   public void individualAcknowledge(ClientMessage message) throws ActiveMQException {
      if (lastAckedMessage != null) {
         flushAcks();
      }

      session.individualAcknowledge(this, message);
   }

   @Override
   public void flushAcks() throws ActiveMQException {
      if (lastAckedMessage != null) {
         logger.trace("{}::FlushACK acking lastMessage::{}", this, lastAckedMessage);
         doAck(lastAckedMessage);
      }
   }

   /**
    * LargeMessageBuffer will call flowcontrol here, while other handleMessage will also be calling flowControl. So,
    * this operation needs to be atomic.
    *
    * @param discountSlowConsumer When dealing with slowConsumers, we need to discount one credit that was pre-sent when
    *                             the first receive was called. For largeMessage that is only done at the latest packet
    */
   @Override
   public void flowControl(final int messageBytes, final boolean discountSlowConsumer) throws ActiveMQException {
      if (clientWindowSize >= 0) {
         creditsToSend += messageBytes;

         if (creditsToSend >= clientWindowSize) {
            if (clientWindowSize == 0 && discountSlowConsumer) {
               if (logger.isTraceEnabled()) {
                  logger.trace("{}::FlowControl::Sending {}-1, for slow consumer", this, creditsToSend);
               }

               // sending the credits - 1 initially send to fire the slow consumer, or the slow consumer would be
               // always buffering one after received the first message
               final int credits = creditsToSend - 1;

               creditsToSend = 0;

               if (credits > 0) {
                  sendCredits(credits);
               }
            } else {
               if (logger.isDebugEnabled()) {
                  logger.debug("Sending {} from flow-control", messageBytes);
               }

               final int credits = creditsToSend;

               creditsToSend = 0;

               if (credits > 0) {
                  sendCredits(credits);
               }
            }
         }
      }
   }

   /**
    * Sending an initial credit for slow consumers
    */
   private void startSlowConsumer() {
      logger.trace("{}::Sending 1 credit to start delivering of one message to slow consumer", this);
      sendCredits(1);
      try {
         // We use an executor here to guarantee the messages will arrive in order.
         // However when starting a slow consumer, we have to guarantee the credit was sent before we can perform any
         // operations like forceDelivery
         pendingFlowControl.await(10, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
         // will just ignore and forward the ignored
         Thread.currentThread().interrupt();
      }
   }

   @Override
   public void resetIfSlowConsumer() {
      if (clientWindowSize == 0) {
         sendCredits(0);

         // If resetting a slow consumer, we need to wait the execution
         final CountDownLatch latch = new CountDownLatch(1);
         flowControlExecutor.execute(latch::countDown);

         try {
            latch.await(10, TimeUnit.SECONDS);
         } catch (InterruptedException e) {
            throw new ActiveMQInterruptedException(e);
         }
      }
   }

   private void requeueExecutors() {
      for (int i = 0; i < buffer.size(); i++) {
         queueExecutor();
      }
   }

   private void queueExecutor() {
      logger.trace("{}::Adding Runner on Executor for delivery", this);
      sessionExecutor.execute(runner);
   }

   private void sendCredits(final int credits) {
      pendingFlowControl.countUp();
      flowControlExecutor.execute(() -> {
         try {
            sessionContext.sendConsumerCredits(ClientConsumerImpl.this, credits);
         } finally {
            pendingFlowControl.countDown();
         }
      });
   }

   private void waitForOnMessageToComplete(boolean waitForOnMessage) {
      if (handler == null) {
         return;
      }

      if (!waitForOnMessage || Thread.currentThread() == onMessageThread) {
         // If called from inside onMessage then return immediately - otherwise would block
         return;
      }

      FutureLatch future = new FutureLatch();

      sessionExecutor.execute(future);

      if (onMessageCloseTimeout == -1) {
         future.await();
      } else {
         if (!future.await(onMessageCloseTimeout)) {
            ActiveMQClientLogger.LOGGER.timeOutWaitingForProcessing(onMessageCloseTimeout);
         }
      }
   }

   private void checkClosed() throws ActiveMQException {
      if (closed) {
         throw ActiveMQClientMessageBundle.BUNDLE.consumerClosed();
      }
   }

   private void callOnMessage() throws Exception {
      if (closing || stopped) {
         return;
      }

      session.workDone();

      // We pull the message from the buffer from inside the Runnable so we can ensure priority
      // ordering. If we just added a Runnable with the message to the executor immediately as we get it
      // we could not do that

      ClientMessageInternal message;

      // Must store handler in local variable since might get set to null
      // otherwise while this is executing and give NPE when calling onMessage
      MessageHandler theHandler = handler;

      if (theHandler != null) {
         if (rateLimiter != null) {
            rateLimiter.limit();
         }

         failedOver = false;

         synchronized (this) {
            message = buffer.poll();
         }

         if (message != null) {
            if (message.containsProperty(ClientConsumerImpl.FORCED_DELIVERY_MESSAGE)) {
               //Ignore, this could be a relic from a previous receiveImmediate();
               return;
            }

            boolean expired = message.isExpired();

            flowControlBeforeConsumption(message);

            if (!expired) {
               logger.trace("{}::Calling handler.onMessage", this);
               final ClassLoader originalLoader = safeInstallContextClassLoader();

               onMessageThread = Thread.currentThread();
               try {
                  theHandler.onMessage(message);
               } finally {
                  try {
                     safeRestoreContextClassLoader(originalLoader);
                  } catch (Exception e) {
                     ActiveMQClientLogger.LOGGER.failedPerformPostActionsOnMessage(e);
                  }

                  onMessageThread = null;
               }

               logger.trace("{}::Handler.onMessage done", this);

            } else {
               theHandler.onMessageExpired(message);

               logger.trace("{}::Handler.onMessageExpired done", this);

               session.expire(this, message);
            }

            if (message.isLargeMessage()) {
               message.discardBody();
            }

            // If slow consumer, we need to send 1 credit to make sure we get another message
            if (clientWindowSize == 0) {
               startSlowConsumer();
            }
         }
      }
   }

   private ClassLoader unsafeInstallContextClassLoader() {
      final Thread currentThread = Thread.currentThread();
      final ClassLoader originalCl = currentThread.getContextClassLoader();
      if (Objects.equals(originalCl, contextClassLoader)) {
         return originalCl;
      }
      currentThread.setContextClassLoader(contextClassLoader);
      return originalCl;
   }

   private ClassLoader safeInstallContextClassLoader() {
      if (!SecurityManagerShim.isSecurityManagerEnabled()) {
         try {
            return unsafeInstallContextClassLoader();
         } catch (SecurityException ignored) {
            // racy security manager set
         }
      }
      return SecurityManagerShim.doPrivileged((PrivilegedAction<ClassLoader>) this::unsafeInstallContextClassLoader);
   }

   private void safeRestoreContextClassLoader(final ClassLoader originalClassLoader) {
      if (Objects.equals(originalClassLoader, contextClassLoader)) {
         return;
      }
      if (!SecurityManagerShim.isSecurityManagerEnabled()) {
         try {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
            return;
         } catch (SecurityException ignored) {
            // racy security manager set
         }
      }
      SecurityManagerShim.doPrivileged((PrivilegedAction<Void>) () -> {
         Thread.currentThread().setContextClassLoader(originalClassLoader);
         return null;
      });
   }

   private void flowControlBeforeConsumption(final ClientMessageInternal message) throws ActiveMQException {
      if (manualFlowManagement) {
         return;
      }
      // Chunk messages will execute the flow control while receiving the chunks
      if (message.getFlowControlSize() != 0) {
         // on large messages we should discount 1 on the first packets as we need continuity until the last packet
         flowControl(message.getFlowControlSize(), !message.isLargeMessage() || message.isOriginallyRegular());
      }
   }

   private void doCleanUp(final boolean sendCloseMessage) throws ActiveMQException {
      try {
         if (closed) {
            return;
         }

         // We need an extra flag closing, since we need to prevent any more messages getting queued to execute
         // after this and we can't just set the closed flag to true here, since after/in onmessage the message
         // might be acked and if the consumer is already closed, the ack will be ignored
         closing = true;

         // Now we wait for any current handler runners to run.
         waitForOnMessageToComplete(true);

         resetLargeMessageController();

         closed = true;

         synchronized (this) {
            if (receiverThread != null) {
               // Wake up any receive() thread that might be waiting
               notify();
            }

            handler = null;

            receiverThread = null;
         }

         flushAcks();

         clearBuffer();

         if (sendCloseMessage) {
            sessionContext.closeConsumer(this);
         }
      } catch (Throwable t) {
         // Consumer close should always return without exception
      }

      session.removeConsumer(this);
   }

   private void clearBuffer() {
      buffer.clear();
   }

   private void doAck(final ClientMessageInternal message) throws ActiveMQException {
      ackBytes = 0;

      lastAckedMessage = null;

      logger.trace("{}::Acking message {}", this, message);

      session.acknowledge(this, message);
   }

   @Override
   public String toString() {
      return super.toString() + "{" +
         "consumerContext=" + consumerContext +
         ", queueName=" + queueName +
         '}';
   }

   private class Runner implements Runnable {

      @Override
      public void run() {
         try {
            callOnMessage();
         } catch (Exception e) {
            ActiveMQClientLogger.LOGGER.onMessageError(e);

            lastException = e;
         }
      }
   }

}
