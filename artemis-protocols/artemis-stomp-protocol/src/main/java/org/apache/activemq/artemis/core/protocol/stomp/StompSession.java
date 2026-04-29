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
package org.apache.activemq.artemis.core.protocol.stomp;

import java.lang.invoke.MethodHandles;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.channel.EventLoop;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQQueueExistsException;
import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.persistence.OperationContext;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.LargeServerMessage;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.core.server.ServerProducer;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.server.impl.ServerSessionImpl;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.protocol.SessionCallback;
import org.apache.activemq.artemis.spi.core.remoting.ReadyListener;
import org.apache.activemq.artemis.utils.CompositeAddress;
import org.apache.activemq.artemis.utils.ConfigurationHelper;
import org.apache.activemq.artemis.utils.PendingTask;
import org.apache.activemq.artemis.utils.UUIDGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.activemq.artemis.core.protocol.stomp.ActiveMQStompProtocolMessageBundle.BUNDLE;
import static org.apache.activemq.artemis.core.protocol.stomp.StompProtocolManagerFactory.STOMP_PROTOCOL_NAME;

public class StompSession implements SessionCallback {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final String senderName = UUIDGenerator.getInstance().generateUUID().toString();

   private boolean createProducer = true;

   private final StompProtocolManager manager;

   private final StompConnection connection;

   private ServerSession session;

   private final OperationContext sessionContext;

   private final BlockingDeque<PendingTask> afterDeliveryTasks = new LinkedBlockingDeque<>();

   private final Map<Long, StompSubscription> subscriptions = new ConcurrentHashMap<>();

   // key = consumer ID and message ID, value = frame length
   private final Map<Pair<Long, Long>, Integer> messagesToAck = new ConcurrentHashMap<>();

   private AtomicInteger noLocalSubscriptionCount = new AtomicInteger(0);

   private boolean txPending = false;

   private final EventLoop eventLoop;

   public synchronized void begin() {
      txPending = true;
   }

   public synchronized boolean isTxPending() {
      return txPending;
   }

   public synchronized void end() {
      txPending = false;
   }

   StompSession(final StompConnection connection, final StompProtocolManager manager, OperationContext sessionContext) {
      this.connection = connection;
      this.manager = manager;
      this.sessionContext = sessionContext;
      eventLoop = connection.getTransportConnection().getEventLoop();
   }

   @Override
   public boolean supportsDirectDelivery() {
      return false;
   }

   @Override
   public boolean isWritable(ReadyListener callback, Object protocolContext) {
      return connection.isWritable(callback);
   }

   void setServerSession(ServerSession session) {
      this.session = session;
   }

   public ServerSession getCoreSession() {
      return session;
   }

   @Override
   public boolean hasCredits(ServerConsumer consumerID) {
      return true;
   }

   @Override
   public void sendProducerCreditsMessage(int credits, SimpleString address) {
   }

   @Override
   public void sendProducerCreditsFailMessage(int credits, SimpleString address) {
   }

   @Override
   public void afterDelivery() throws Exception {
      PendingTask task;
      while ((task = afterDeliveryTasks.poll()) != null) {
         task.run();
      }
   }

   @Override
   public void browserFinished(ServerConsumer consumer) {

   }

   @Override
   public boolean updateDeliveryCountAfterCancel(ServerConsumer consumer, MessageReference ref, boolean failed) {
      return false;
   }

   @Override
   public int sendMessage(MessageReference ref, final ServerConsumer consumer, int deliveryCount) {
      ICoreMessage message = ref.getMessage().toCore();
      try {
         StompSubscription subscription = subscriptions.get(consumer.getID());
         // subscription might be null if the consumer was closed
         if (subscription == null) {
            return 0;
         }
         StompFrame frame;

         frame = connection.createStompMessage(message, subscription, consumer, deliveryCount);

         int length = frame.getEncodedSize();

         if (subscription.getAck().equals(Stomp.Headers.Subscribe.AckModeValues.AUTO)) {
            if (manager.send(connection, frame)) {
               final long messageID = message.getMessageID();
               final long consumerID = consumer.getID();

               // this will be called after the delivery is complete
               // we can't call session.ack within the delivery
               // as it could dead lock.
               afterDeliveryTasks.offer(new PendingTask() {
                  @Override
                  public void run() throws Exception {
                     eventLoop.execute(() -> {
                        try {
                           //we ack and commit only if the send is successful
                           session.acknowledge(consumerID, messageID);
                           session.commit();
                        } catch (Throwable e) {
                           logger.warn(e.getMessage(), e);
                        }
                     });
                  }
               });
            }
         } else {
            messagesToAck.put(new Pair<>(consumer.getID(), message.getMessageID()), length);
            // Must send AFTER adding to messagesToAck - or could get acked from client BEFORE it's been added!
            manager.send(connection, frame);
         }

         return length;
      } catch (Exception e) {
         ActiveMQStompProtocolLogger.LOGGER.unableToSendMessageToClient(message, e);
         return 0;
      }
   }

   @Override
   public int sendLargeMessageContinuation(ServerConsumer consumer,
                                           byte[] body,
                                           boolean continues,
                                           boolean requiresResponse) {
      return 0;
   }

   @Override
   public int sendLargeMessage(MessageReference ref,
                               ServerConsumer consumer,
                               long bodySize,
                               int deliveryCount) {
      return 0;
   }

   @Override
   public void closed() {
      session.removeProducer(senderName);
   }

   @Override
   public void disconnect(ServerConsumer consumerId, String errorDescription) {
      StompSubscription stompSubscription = subscriptions.remove(consumerId.getID());
      if (stompSubscription != null) {
         if (stompSubscription.isNoLocal()) {
            noLocalSubscriptionCount.decrementAndGet();
         }
         StompFrame frame = connection.getFrameHandler().createStompFrame(Stomp.Responses.ERROR);
         frame.addHeader(Stomp.Headers.CONTENT_TYPE, "text/plain");
         frame.setBody("consumer with ID " + consumerId + " disconnected by server");
         connection.sendFrame(frame, null);
      }
   }

   public static final char MESSAGE_ID_SEPARATOR = ',';

   private static Pair<Long, Long> splitAndParse(String stompMessageID) {
      int separatorPosition = stompMessageID.indexOf(MESSAGE_ID_SEPARATOR);
      if (separatorPosition == -1) {
         return null;
      }
      long consumerID = Long.parseLong(stompMessageID, 0, separatorPosition, 10);
      long messageID = Long.parseLong(stompMessageID, separatorPosition + 1, stompMessageID.length(), 10);
      return new Pair<>(consumerID, messageID);
   }

   public void acknowledge(String messageID, String subscriptionID) throws Exception {
      Pair<Long, Long> pair = splitAndParse(messageID);
      Integer length = messagesToAck.remove(pair);

      if (length == null) {
         throw BUNDLE.failToAckMissingID(messageID).setHandler(connection.getFrameHandler());
      }

      long consumerID = pair.getA();
      long id = pair.getB();

      StompSubscription sub = subscriptions.get(consumerID);

      if (subscriptionID != null) {
         if (!sub.getID().equals(subscriptionID)) {
            throw BUNDLE.subscriptionIDMismatch(subscriptionID, sub.getID()).setHandler(connection.getFrameHandler());
         }
      }

      if (sub.getAck().equals(Stomp.Headers.Subscribe.AckModeValues.CLIENT_INDIVIDUAL)) {
         session.individualAcknowledge(consumerID, id);

         if (sub.getConsumerWindowSize() != -1) {
            session.receiveConsumerCredits(consumerID, length);
         }
      } else {
         List<Long> ackedRefs = session.acknowledge(consumerID, id);

         if (sub.getConsumerWindowSize() != -1) {
            session.receiveConsumerCredits(consumerID, length);
            for (Long ackedID : ackedRefs) {
               if (ackedID != id) {
                  length = messagesToAck.remove(new Pair<>(consumerID, ackedID));
                  if (length != null) {
                     session.receiveConsumerCredits(consumerID, length);
                  }
               }
            }
         } else {
            for (Long ackedID : ackedRefs) {
               if (ackedID != id) {
                  messagesToAck.remove(new Pair<>(consumerID, ackedID));
               }
            }
         }
      }

      session.commit();
   }

   public StompPostReceiptFunction addSubscription(long consumerID,
                                                   String subscriptionID,
                                                   String clientID,
                                                   String durableSubscriptionName,
                                                   String destination,
                                                   String selector,
                                                   String ack,
                                                   boolean noLocal,
                                                   Integer consumerWindowSize,
                                                   RoutingType temporaryRoutingType) throws Exception {
      SimpleString address = SimpleString.of(destination);
      SimpleString queueName = SimpleString.of(destination);
      SimpleString selectorSimple = SimpleString.of(selector);
      final int finalConsumerWindowSize;

      if (consumerWindowSize != null) {
         finalConsumerWindowSize = consumerWindowSize;
      } else if (ack.equals(Stomp.Headers.Subscribe.AckModeValues.AUTO)) {
         finalConsumerWindowSize = -1;
      } else {
         finalConsumerWindowSize = ConfigurationHelper.getIntProperty(TransportConstants.STOMP_CONSUMER_WINDOW_SIZE, ConfigurationHelper.getIntProperty(TransportConstants.STOMP_CONSUMERS_CREDIT, TransportConstants.STOMP_DEFAULT_CONSUMER_WINDOW_SIZE, connection.getAcceptorUsed().getConfiguration()), connection.getAcceptorUsed().getConfiguration());
      }

      boolean multicast;
      if (temporaryRoutingType != null) {
         multicast = temporaryRoutingType == RoutingType.MULTICAST;
         if (multicast) {
            queueName = UUIDGenerator.getInstance().generateSimpleStringUUID();
            session.createQueue(QueueConfiguration.of(queueName).setAddress(address).setRoutingType(RoutingType.MULTICAST).setFilterString(selectorSimple).setDurable(false).setTemporary(true));
         } else {
            try {
               session.createQueue(QueueConfiguration.of(address).setAddress(address).setRoutingType(RoutingType.ANYCAST).setFilterString(selectorSimple).setDurable(false).setTemporary(true));
            } catch (ActiveMQQueueExistsException e) {
               // queue may already exist if a sender created it first
            }
            queueName = address;
         }
      } else {
         Set<RoutingType> routingTypes = manager.getServer().getAddressInfo(getCoreSession().removePrefix(address)).getRoutingTypes();
         multicast = routingTypes.size() == 1 && routingTypes.contains(RoutingType.MULTICAST);
         // if the destination is FQQN then the queue will have already been created
         if (multicast && !CompositeAddress.isFullyQualified(destination)) {
            // subscribes to a topic
            if (durableSubscriptionName != null) {
               if (clientID == null) {
                  throw BUNDLE.missingClientID();
               }
               queueName = SimpleString.of(clientID + "." + durableSubscriptionName);
               if (manager.getServer().locateQueue(queueName) == null) {
                  try {
                     session.createQueue(QueueConfiguration.of(queueName).setAddress(address).setFilterString(selectorSimple));
                  } catch (ActiveMQQueueExistsException e) {
                     // ignore; can be caused by concurrent durable subscribers
                  }
               }
            } else {
               queueName = UUIDGenerator.getInstance().generateSimpleStringUUID();
               session.createQueue(QueueConfiguration.of(queueName).setAddress(address).setFilterString(selectorSimple).setDurable(false).setTemporary(true));
            }
         }
      }
      if (noLocal) {
         noLocalSubscriptionCount.incrementAndGet();
      }
      final ServerConsumer consumer = session.createConsumer(consumerID, queueName, multicast ? null : selectorSimple, false, false, 0);
      StompSubscription subscription = new StompSubscription(subscriptionID, ack, queueName, multicast, noLocal, finalConsumerWindowSize);
      subscriptions.put(consumerID, subscription);
      session.start();
      /*
       * If the consumerWindowSize is 0 then we need to supply at least 1 credit otherwise messages will *never* flow.
       * See org.apache.activemq.artemis.core.client.impl.ClientConsumerImpl#startSlowConsumer()
       */
      return () -> consumer.receiveCredits(finalConsumerWindowSize == 0 ? 1 : finalConsumerWindowSize);
   }

   public boolean unsubscribe(String id, String durableSubscriptionName, String clientID) throws Exception {
      boolean result = false;
      Iterator<Entry<Long, StompSubscription>> iterator = subscriptions.entrySet().iterator();

      while (iterator.hasNext()) {
         Map.Entry<Long, StompSubscription> entry = iterator.next();
         long consumerID = entry.getKey();
         StompSubscription sub = entry.getValue();
         if (id != null && id.equals(sub.getID())) {
            iterator.remove();
            if (sub.isNoLocal()) {
               noLocalSubscriptionCount.decrementAndGet();
            }
            SimpleString queueName = sub.getQueueName();
            session.closeConsumer(consumerID);
            Queue queue = manager.getServer().locateQueue(queueName);
            if (sub.isMulticast() && queue != null && (durableSubscriptionName == null && !queue.isDurable())) {
               session.deleteQueue(queueName);
            }
            result = true;
         }
      }

      if (durableSubscriptionName != null && clientID != null) {
         SimpleString queueName = SimpleString.of(clientID + "." + durableSubscriptionName);
         if (manager.getServer().locateQueue(queueName) != null) {
            session.deleteQueue(queueName);
         }
         result = true;
      }

      return result;
   }

   boolean containsSubscription(String subscriptionID) {
      for (Entry<Long, StompSubscription> entry : subscriptions.entrySet()) {
         StompSubscription sub = entry.getValue();
         if (sub.getID().equals(subscriptionID)) {
            return true;
         }
      }
      return false;
   }

   public RemotingConnection getConnection() {
      return connection;
   }

   public OperationContext getContext() {
      return sessionContext;
   }

   public int getNoLocalSubscriptionCount() {
      return noLocalSubscriptionCount.get();
   }

   public int getSubscriptionCount() {
      return subscriptions.size();
   }

   public void sendInternal(Message message, boolean direct) throws Exception {
      if (createProducer) {
         session.addProducer(senderName, STOMP_PROTOCOL_NAME, ServerProducer.ANONYMOUS);
         createProducer = false;
      }
      session.send(message, direct, senderName);
   }

   public void sendInternalLarge(CoreMessage message, boolean direct) throws Exception {
      if (createProducer) {
         session.addProducer(senderName, STOMP_PROTOCOL_NAME, ServerProducer.ANONYMOUS);
         createProducer = false;
      }
      int headerSize = message.getHeadersAndPropertiesEncodeSize();
      if (headerSize >= connection.getMinLargeMessageSize()) {
         throw BUNDLE.headerTooBig();
      }

      StorageManager storageManager = ((ServerSessionImpl) session).getStorageManager();
      long id = storageManager.generateID();
      LargeServerMessage largeMessage = storageManager.createCoreLargeMessage(id, message);

      ActiveMQBuffer body = message.getReadOnlyBodyBuffer();
      byte[] bytes = new byte[body.readableBytes()];
      body.readBytes(bytes);

      largeMessage.addBytes(bytes);

      largeMessage.releaseResources(true, true);

      largeMessage.toMessage().putLongProperty(Message.HDR_LARGE_BODY_SIZE, bytes.length);

      session.send(largeMessage.toMessage(), direct, senderName);
   }

}
