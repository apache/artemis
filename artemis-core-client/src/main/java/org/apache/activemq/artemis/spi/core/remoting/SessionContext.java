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
package org.apache.activemq.artemis.spi.core.remoting;

import javax.transaction.xa.XAException;
import javax.transaction.xa.Xid;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueAttributes;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.SendAcknowledgementHandler;
import org.apache.activemq.artemis.core.client.impl.ClientConsumerInternal;
import org.apache.activemq.artemis.core.client.impl.ClientLargeMessageInternal;
import org.apache.activemq.artemis.core.client.impl.ClientMessageInternal;
import org.apache.activemq.artemis.core.client.impl.ClientProducerCredits;
import org.apache.activemq.artemis.core.client.impl.ClientProducerInternal;
import org.apache.activemq.artemis.core.client.impl.ClientSessionInternal;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionQueueQueryResponseMessage;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.utils.IDGenerator;
import org.apache.activemq.artemis.utils.SimpleIDGenerator;

public abstract class SessionContext {

   protected ClientSessionInternal session;

   protected SendAcknowledgementHandler sendAckHandler;

   protected volatile RemotingConnection remotingConnection;

   protected final IDGenerator idGenerator = new SimpleIDGenerator(0);

   public SessionContext(RemotingConnection remotingConnection) {
      this.remotingConnection = remotingConnection;
   }

   public ClientSessionInternal getSession() {
      return session;
   }

   public void setSession(ClientSessionInternal session) {
      this.session = session;
   }

   public abstract void resetName(String name);

   public abstract int getReconnectID();

   /**
    * Transfers the session context to the given newConnection on the client-side
    */
   public abstract void transferConnection(RemotingConnection newConnection);

   public RemotingConnection getRemotingConnection() {
      return remotingConnection;
   }

   public abstract void closeConsumer(ClientConsumer consumer) throws ActiveMQException;

   public abstract void sendConsumerCredits(ClientConsumer consumer, int credits);

   public abstract boolean supportsLargeMessage();

   protected void handleReceiveLargeMessage(ConsumerContext consumerID,
                                            ClientLargeMessageInternal clientLargeMessage,
                                            long largeMessageSize) throws Exception {
      ClientSessionInternal session = this.session;
      if (session != null) {
         session.handleReceiveLargeMessage(consumerID, clientLargeMessage, largeMessageSize);
      }
   }

   protected void handleReceiveMessage(ConsumerContext consumerID,
                                       ClientMessageInternal message) throws Exception {

      ClientSessionInternal session = this.session;
      if (session != null) {
         session.handleReceiveMessage(consumerID, message);
      }
   }

   protected void handleReceiveContinuation(ConsumerContext consumerID,
                                            byte[] chunk,
                                            int flowControlSize,
                                            boolean isContinues) throws Exception {
      ClientSessionInternal session = this.session;
      if (session != null) {
         session.handleReceiveContinuation(consumerID, chunk, flowControlSize, isContinues);
      }
   }

   protected void handleReceiveProducerCredits(SimpleString address, int credits) {
      ClientSessionInternal session = this.session;
      if (session != null) {
         session.handleReceiveProducerCredits(address, credits);
      }

   }

   protected void handleReceiveProducerFailCredits(SimpleString address, int credits) {
      ClientSessionInternal session = this.session;
      if (session != null) {
         session.handleReceiveProducerFailCredits(address, credits);
      }

   }

   public abstract int getCreditsOnSendingFull(Message msgI);

   public abstract void sendFullMessage(ICoreMessage msgI,
                                        boolean sendBlocking,
                                        SendAcknowledgementHandler handler,
                                        SimpleString defaultAddress,
                                        int senderID) throws ActiveMQException;

   /**
    * it should return the number of credits (or bytes) used to send this packet
    */
   public abstract int sendInitialChunkOnLargeMessage(Message msgI) throws ActiveMQException;

   public abstract int sendLargeMessageChunk(Message msgI,
                                             long messageBodySize,
                                             boolean sendBlocking,
                                             boolean lastChunk,
                                             byte[] chunk,
                                             int reconnectID,
                                             int senderID,
                                             SendAcknowledgementHandler messageHandler) throws ActiveMQException;

   public abstract int sendServerLargeMessageChunk(Message msgI,
                                                   long messageBodySize,
                                                   boolean sendBlocking,
                                                   boolean lastChunk,
                                                   byte[] chunk,
                                                   int senderID,
                                                   SendAcknowledgementHandler messageHandler) throws ActiveMQException;

   public abstract void setSendAcknowledgementHandler(SendAcknowledgementHandler handler);

   public abstract SendAcknowledgementHandler getSendAcknowledgementHandler();

   /**
    * Creates a shared queue using the routing type set by the Address.  If the Address supports more than one type of
    * delivery then the default delivery mode (MULTICAST) is used.
    */
   @Deprecated
   public abstract void createSharedQueue(SimpleString address,
                                          SimpleString queueName,
                                          RoutingType routingType,
                                          SimpleString filterString,
                                          boolean durable,
                                          Integer maxConsumers,
                                          Boolean purgeOnNoConsumers,
                                          Boolean exclusive,
                                          Boolean lastValue) throws ActiveMQException;

   /**
    * Creates a shared queue using the routing type set by the Address.  If the Address supports more than one type of
    * delivery then the default delivery mode (MULTICAST) is used.
    */
   @Deprecated
   public abstract void createSharedQueue(SimpleString address,
                                          SimpleString queueName,
                                          QueueAttributes queueAttributes) throws ActiveMQException;

   @Deprecated
   public abstract void createSharedQueue(SimpleString address,
                                          SimpleString queueName,
                                          RoutingType routingType,
                                          SimpleString filterString,
                                          boolean durable) throws ActiveMQException;

   @Deprecated
   public abstract void createSharedQueue(SimpleString address,
                                   SimpleString queueName,
                                   SimpleString filterString,
                                   boolean durable) throws ActiveMQException;

   public abstract void createSharedQueue(QueueConfiguration queueConfiguration) throws ActiveMQException;

   public abstract void deleteQueue(SimpleString queueName) throws ActiveMQException;

   @Deprecated
   public abstract void createAddress(SimpleString address, Set<RoutingType> routingTypes, boolean autoCreated) throws ActiveMQException;

   public abstract void createAddress(SimpleString address, EnumSet<RoutingType> routingTypes, boolean autoCreated) throws ActiveMQException;

   @Deprecated
   public abstract void createQueue(SimpleString address,
                                    SimpleString queueName,
                                    SimpleString filterString,
                                    boolean durable,
                                    boolean temp,
                                    boolean autoCreated) throws ActiveMQException;

   @Deprecated
   public abstract void createQueue(SimpleString address,
                                    RoutingType routingType,
                                    SimpleString queueName,
                                    SimpleString filterString,
                                    boolean durable,
                                    boolean temp,
                                    int maxConsumers,
                                    boolean purgeOnNoConsumers,
                                    boolean autoCreated) throws ActiveMQException;

   public abstract void createQueue(SimpleString address,
                                    RoutingType routingType,
                                    SimpleString queueName,
                                    SimpleString filterString,
                                    boolean durable,
                                    boolean temp,
                                    int maxConsumers,
                                    boolean purgeOnNoConsumers,
                                    boolean autoCreated,
                                    Boolean exclusive,
                                    Boolean lastVale) throws ActiveMQException;

   public abstract void createQueue(SimpleString address,
                                    SimpleString queueName,
                                    boolean temp,
                                    boolean autoCreated,
                                    QueueAttributes queueAttributes) throws ActiveMQException;

   public abstract void createQueue(QueueConfiguration queueConfiguration) throws ActiveMQException;

   public abstract ClientSession.QueueQuery queueQuery(SimpleString queueName) throws ActiveMQException;

   public abstract void forceDelivery(ClientConsumer consumer, long sequence) throws ActiveMQException;

   public abstract ClientSession.AddressQuery addressQuery(SimpleString address) throws ActiveMQException;

   public abstract void simpleCommit() throws ActiveMQException;

   public abstract void simpleCommit(boolean block) throws ActiveMQException;


   /**
    * If we are doing a simple rollback on the RA, we need to ack the last message sent to the consumer,
    * otherwise DLQ won't work.
    * <p>
    * this is because we only ACK after on the RA, We may review this if we always acked earlier.
    */
   public abstract void simpleRollback(boolean lastMessageAsDelivered) throws ActiveMQException;

   public abstract void sessionStart() throws ActiveMQException;

   public abstract void sessionStop() throws ActiveMQException;

   public abstract void sendACK(boolean individual,
                                boolean block,
                                ClientConsumer consumer,
                                Message message) throws ActiveMQException;

   public abstract void expireMessage(ClientConsumer consumer, Message message) throws ActiveMQException;

   public abstract void sessionClose() throws ActiveMQException;

   public abstract void addSessionMetadata(String key, String data) throws ActiveMQException;

   public abstract void addUniqueMetaData(String key, String data) throws ActiveMQException;

   public abstract void sendProducerCreditsMessage(int credits, SimpleString address);

   public abstract void xaCommit(Xid xid, boolean onePhase) throws XAException, ActiveMQException;

   public abstract void xaEnd(Xid xid, int flags) throws XAException, ActiveMQException;

   public abstract void xaForget(Xid xid) throws XAException, ActiveMQException;

   public abstract int xaPrepare(Xid xid) throws XAException, ActiveMQException;

   public abstract Xid[] xaScan() throws ActiveMQException;

   public abstract void xaRollback(Xid xid, boolean wasStarted) throws ActiveMQException, XAException;

   public abstract void xaStart(Xid xid, int flags) throws XAException, ActiveMQException;

   public abstract boolean configureTransactionTimeout(int seconds) throws ActiveMQException;

   public abstract ClientConsumerInternal createConsumer(SimpleString queueName,
                                                         SimpleString filterString,
                                                         int priority,
                                                         int windowSize,
                                                         int maxRate,
                                                         int ackBatchSize,
                                                         boolean browseOnly,
                                                         Executor executor,
                                                         Executor flowControlExecutor,
                                                         int onMessageCloseTimeout) throws ActiveMQException;

   /**
    * Performs a round trip to the server requesting what is the current tx timeout on the session
    */
   public abstract int recoverSessionTimeout() throws ActiveMQException;

   public abstract int getServerVersion();

   public abstract void recreateSession(String username,
                                        String password,
                                        int minLargeMessageSize,
                                        boolean xa,
                                        boolean autoCommitSends,
                                        boolean autoCommitAcks,
                                        boolean preAcknowledge) throws ActiveMQException;

   public abstract void recreateConsumerOnServer(ClientConsumerInternal consumerInternal, long consumerId, boolean isSessionStarted) throws ActiveMQException;

   public abstract void xaFailed(Xid xid) throws ActiveMQException;

   public abstract void restartSession() throws ActiveMQException;

   public abstract void resetMetadata(Map<String, String> metaDataToSend);

   public abstract int getDefaultConsumerWindowSize(SessionQueueQueryResponseMessage response) throws ActiveMQException;

   // Failover utility classes

   /**
    * Interrupt and return any blocked calls
    */
   public abstract void returnBlocking(ActiveMQException cause);

   /**
    * it will lock the communication channel of the session avoiding anything to come while failover is happening. It
    * happens on preFailover from ClientSessionImpl
    */
   public abstract void lockCommunications();

   public abstract void releaseCommunications();

   public abstract void cleanup();

   public abstract void linkFlowControl(SimpleString address, ClientProducerCredits clientProducerCredits);

   public abstract boolean isWritable(ReadyListener callback);

   public abstract void createProducer(ClientProducerInternal producer);

   public abstract void removeProducer(int id);
}
