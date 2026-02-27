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
package org.apache.activemq.artemis.core.protocol.core.impl;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.lang.invoke.MethodHandles;
import java.security.PrivilegedAction;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueAttributes;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.SendAcknowledgementHandler;
import org.apache.activemq.artemis.core.client.ActiveMQClientLogger;
import org.apache.activemq.artemis.core.client.ActiveMQClientMessageBundle;
import org.apache.activemq.artemis.core.client.impl.AddressQueryImpl;
import org.apache.activemq.artemis.core.client.impl.ClientConsumerImpl;
import org.apache.activemq.artemis.core.client.impl.ClientConsumerInternal;
import org.apache.activemq.artemis.core.client.impl.ClientLargeMessageInternal;
import org.apache.activemq.artemis.core.client.impl.ClientMessageInternal;
import org.apache.activemq.artemis.core.client.impl.ClientProducerCredits;
import org.apache.activemq.artemis.core.client.impl.ClientProducerInternal;
import org.apache.activemq.artemis.core.client.impl.ClientSessionImpl;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.protocol.core.Channel;
import org.apache.activemq.artemis.core.protocol.core.ChannelHandler;
import org.apache.activemq.artemis.core.protocol.core.CommandConfirmationHandler;
import org.apache.activemq.artemis.core.protocol.core.CoreRemotingConnection;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.ResponseHandler;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ActiveMQExceptionMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.CreateAddressMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.CreateProducerMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.CreateQueueMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.CreateQueueMessage_V2;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.CreateSessionMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.CreateSharedQueueMessage_V2;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.DisconnectConsumerMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.DisconnectConsumerWithKillMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ReattachSessionMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ReattachSessionResponseMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.RemoveProducerMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.RollbackMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionAcknowledgeMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionAddMetaDataMessageV2;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionBindingQueryMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionBindingQueryResponseMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionBindingQueryResponseMessage_V2;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionBindingQueryResponseMessage_V3;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionBindingQueryResponseMessage_V4;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionBindingQueryResponseMessage_V5;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionCloseMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionCommitMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionCommitMessage_V2;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionConsumerCloseMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionConsumerFlowCreditMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionCreateConsumerMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionDeleteQueueMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionExpireMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionForceConsumerDelivery;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionIndividualAcknowledgeMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionProducerCreditsFailMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionProducerCreditsMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionQueueQueryMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionQueueQueryResponseMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionQueueQueryResponseMessage_V3;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionReceiveContinuationMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionReceiveLargeMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionReceiveMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionRequestProducerCreditsMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionSendContinuationMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionSendContinuationMessage_V2;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionSendContinuationMessage_V3;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionSendLargeMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionSendMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionSendMessage_1X;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionSendMessage_V2;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionSendMessage_V3;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionUniqueAddMetaDataMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionXAAfterFailedMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionXACommitMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionXAEndMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionXAForgetMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionXAGetInDoubtXidsResponseMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionXAGetTimeoutResponseMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionXAJoinMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionXAPrepareMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionXAResponseMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionXAResumeMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionXARollbackMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionXASetTimeoutMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionXASetTimeoutResponseMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionXAStartMessage;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.apache.activemq.artemis.spi.core.remoting.ReadyListener;
import org.apache.activemq.artemis.spi.core.remoting.SessionContext;
import org.apache.activemq.artemis.utils.TokenBucketLimiterImpl;
import org.apache.activemq.artemis.utils.sm.SecurityManagerShim;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.DISCONNECT_CONSUMER;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.EXCEPTION;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_RECEIVE_CONTINUATION;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_RECEIVE_LARGE_MSG;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_RECEIVE_MSG;

public class ActiveMQSessionContext extends SessionContext {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final Channel sessionChannel;
   private final int serverVersion;
   private int confirmationWindow;
   private String name;
   private boolean killed;

   public Channel getSessionChannel() {
      return sessionChannel;
   }

   protected String getName() {
      return name;
   }

   @Override
   public void resetName(String name) {
      this.name = name;
   }

   protected int getConfirmationWindow() {
      return confirmationWindow;

   }

   public ActiveMQSessionContext(String name,
                                 RemotingConnection remotingConnection,
                                 Channel sessionChannel,
                                 int serverVersion,
                                 int confirmationWindow) {
      super(remotingConnection);

      this.name = name;
      this.sessionChannel = sessionChannel;
      this.serverVersion = serverVersion;
      this.confirmationWindow = confirmationWindow;

      ChannelHandler handler = new ClientSessionPacketHandler();
      sessionChannel.setHandler(handler);

      if (confirmationWindow >= 0) {
         setHandlers();
      }
   }

   @Override
   public int getReconnectID() {
      return sessionChannel.getReconnectID();
   }

   public boolean isKilled() {
      return killed;
   }

   public void kill() {
      this.killed = true;
   }

   private void setHandlers() {
      sessionChannel.setCommandConfirmationHandler(commandConfirmationHandler);

      if (!sessionChannel.getConnection().isVersionBeforeAsyncResponseChange()) {
         sessionChannel.setResponseHandler(responseHandler);
      }
   }

   private final CommandConfirmationHandler commandConfirmationHandler = new CommandConfirmationHandler() {
      @Override
      public void commandConfirmed(Packet packet) {
         responseHandler.handleResponse(packet, null);
      }
   };

   private final ResponseHandler responseHandler = new ResponseHandler() {
      @Override
      public void handleResponse(Packet packet, Packet response) {
         final ActiveMQException activeMQException;
         if (response != null && response.getType() == PacketImpl.EXCEPTION) {
            ActiveMQExceptionMessage exceptionResponseMessage = (ActiveMQExceptionMessage) response;
            activeMQException = exceptionResponseMessage.getException();
         } else {
            activeMQException = null;
         }

         if (packet.getType() == PacketImpl.SESS_SEND) {
            SessionSendMessage ssm = (SessionSendMessage) packet;
            callSendAck(ssm.getHandler(), ssm.getMessage(), activeMQException);
         } else if (packet.getType() == PacketImpl.SESS_SEND_CONTINUATION) {
            SessionSendContinuationMessage scm = (SessionSendContinuationMessage) packet;
            if (!scm.isContinues()) {
               callSendAck(scm.getHandler(), scm.getMessage(), activeMQException);
            }
         }
      }

      private void callSendAck(SendAcknowledgementHandler handler, final Message message, final Exception exception) {
         if (handler != null) {
            if (exception == null) {
               handler.sendAcknowledged(message);
            } else {
               handler.sendFailed(message, exception);
            }
         }
      }
   };

   // Failover utility methods

   @Override
   public void returnBlocking(ActiveMQException cause) {
      sessionChannel.returnBlocking(cause);
   }

   @Override
   public void lockCommunications() {
      sessionChannel.lock();
   }

   @Override
   public void releaseCommunications() {
      sessionChannel.setTransferring(false);
      sessionChannel.unlock();
   }

   @Override
   public void cleanup() {
      sessionChannel.close();

      // if the server is sending a disconnect
      // any pending blocked operation could hang without this
      sessionChannel.returnBlocking();
   }

   @Override
   public void linkFlowControl(SimpleString address, ClientProducerCredits clientProducerCredits) {
      // nothing to be done here... Flow control here is done on the core side
   }

   @Override
   public void setSendAcknowledgementHandler(final SendAcknowledgementHandler handler) {
      setHandlers();

      this.sendAckHandler = session.wrap(handler);
   }

   @Override
   public SendAcknowledgementHandler getSendAcknowledgementHandler() {
      return this.sendAckHandler;
   }

   @Deprecated
   @Override
   public void createSharedQueue(SimpleString address,
                                 SimpleString queueName,
                                 RoutingType routingType,
                                 SimpleString filterString,
                                 boolean durable,
                                 Integer maxConsumers,
                                 Boolean purgeOnNoConsumers,
                                 Boolean exclusive,
                                 Boolean lastValue) throws ActiveMQException {
      createSharedQueue(QueueConfiguration.of(queueName)
                           .setAddress(address)
                           .setRoutingType(routingType)
                           .setFilterString(filterString)
                           .setDurable(durable)
                           .setMaxConsumers(maxConsumers)
                           .setPurgeOnNoConsumers(purgeOnNoConsumers)
                           .setExclusive(exclusive)
                           .setLastValue(lastValue));
   }

   @Deprecated
   @Override
   public void createSharedQueue(SimpleString address,
                                 SimpleString queueName,
                                 QueueAttributes queueAttributes) throws ActiveMQException {
      createSharedQueue(queueAttributes.toQueueConfiguration().setName(queueName).setAddress(address));
   }

   @Override
   public void createSharedQueue(QueueConfiguration queueConfiguration) throws ActiveMQException {
      sessionChannel.sendBlocking(new CreateSharedQueueMessage_V2(queueConfiguration, true), PacketImpl.NULL_RESPONSE);
   }

   @Deprecated
   @Override
   public void createSharedQueue(SimpleString address,
                                 SimpleString queueName,
                                 RoutingType routingType,
                                 SimpleString filterString,
                                 boolean durable) throws ActiveMQException {
      createSharedQueue(QueueConfiguration.of(queueName)
                           .setAddress(address)
                           .setRoutingType(routingType)
                           .setFilterString(filterString)
                           .setDurable(durable));
   }

   @Deprecated
   @Override
   public void createSharedQueue(SimpleString address,
                                 SimpleString queueName,
                                 SimpleString filterString,
                                 boolean durable) throws ActiveMQException {
      createSharedQueue(QueueConfiguration.of(queueName)
                           .setAddress(address)
                           .setFilterString(filterString)
                           .setDurable(durable));
   }

   @Override
   public void deleteQueue(final SimpleString queueName) throws ActiveMQException {
      sessionChannel.sendBlocking(new SessionDeleteQueueMessage(queueName), PacketImpl.NULL_RESPONSE);
   }

   @Override
   public ClientSession.QueueQuery queueQuery(final SimpleString queueName) throws ActiveMQException {
      SessionQueueQueryResponseMessage response;
      if (sessionChannel.getConnection().isVersionBeforeAddressChange()) {
         SessionQueueQueryMessage request = new SessionQueueQueryMessage(queueName);
         response = (SessionQueueQueryResponseMessage) sessionChannel.sendBlocking(request, PacketImpl.SESS_QUEUEQUERY_RESP_V2);
      } else {
         SessionQueueQueryMessage request = new SessionQueueQueryMessage(queueName);
         response = (SessionQueueQueryResponseMessage) sessionChannel.sendBlocking(request, PacketImpl.SESS_QUEUEQUERY_RESP_V3);
      }

      return response.toQueueQuery();
   }

   @Override
   public boolean isWritable(ReadyListener callback) {
      return remotingConnection.isWritable(callback);
   }

   @Override
   public void createProducer(ClientProducerInternal producer) {
      if (!sessionChannel.getConnection().isBeforeProducerMetricsChanged()) {
         sessionChannel.send(new CreateProducerMessage(producer.getID(), producer.getAddress()));
      }
   }

   @Override
   public void removeProducer(int id) {
      if (!sessionChannel.getConnection().isBeforeProducerMetricsChanged()) {
         sessionChannel.send(new RemoveProducerMessage(id));
      }
   }

   @Override
   public ClientConsumerInternal createConsumer(SimpleString queueName,
                                                SimpleString filterString,
                                                int priority,
                                                int windowSize,
                                                int maxRate,
                                                int ackBatchSize,
                                                boolean browseOnly,
                                                Executor executor,
                                                Executor flowControlExecutor,
                                                int onMessageCloseTimeout) throws ActiveMQException {
      long consumerID = idGenerator.generateID();

      ActiveMQConsumerContext consumerContext = new ActiveMQConsumerContext(consumerID);

      SessionCreateConsumerMessage request = new SessionCreateConsumerMessage(consumerID, queueName, filterString, priority, browseOnly, true);

      SessionQueueQueryResponseMessage queueInfo;

      if (sessionChannel.getConnection().isVersionBeforeAddressChange()) {
         queueInfo = (SessionQueueQueryResponseMessage) sessionChannel.sendBlocking(request, PacketImpl.SESS_QUEUEQUERY_RESP_V2);
      } else {
         queueInfo = (SessionQueueQueryResponseMessage) sessionChannel.sendBlocking(request, PacketImpl.SESS_QUEUEQUERY_RESP_V3);
      }

      // The actual windows size that gets used is determined by the user since
      // could be overridden on the queue settings
      // The value we send is just a hint
      final int consumerWindowSize = windowSize == ActiveMQClient.DEFAULT_CONSUMER_WINDOW_SIZE ? this.getDefaultConsumerWindowSize(queueInfo) : windowSize;

      return new ClientConsumerImpl(session, consumerContext, queueName, filterString, priority, browseOnly, consumerWindowSize, calcWindowSize(consumerWindowSize), ackBatchSize, maxRate > 0 ? new TokenBucketLimiterImpl(maxRate, false) : null, executor, flowControlExecutor, this, queueInfo.toQueueQuery(), lookupTCCL(), onMessageCloseTimeout);
   }

   @Override
   public int getServerVersion() {
      return serverVersion;
   }

   @Override
   public ClientSession.AddressQuery addressQuery(final SimpleString address) throws ActiveMQException {
      if (sessionChannel.supports(PacketImpl.SESS_BINDINGQUERY_RESP_V5, getServerVersion())) {
         Packet packet = sessionChannel.sendBlocking(new SessionBindingQueryMessage(address), PacketImpl.SESS_BINDINGQUERY_RESP_V5);
         SessionBindingQueryResponseMessage_V5 response = (SessionBindingQueryResponseMessage_V5) packet;
         return new AddressQueryImpl(response.isExists(), response.getQueueNames(), response.isAutoCreateQueues(), response.isAutoCreateAddresses(), response.isDefaultPurgeOnNoConsumers(), response.getDefaultMaxConsumers(), response.isDefaultExclusive(), response.isDefaultLastValue(), response.getDefaultLastValueKey(), response.isDefaultNonDestructive(), response.getDefaultConsumersBeforeDispatch(), response.getDefaultDelayBeforeDispatch(), response.isSupportsMulticast(), response.isSupportsAnycast());
      } else if (sessionChannel.supports(PacketImpl.SESS_BINDINGQUERY_RESP_V4, getServerVersion())) {
         Packet packet = sessionChannel.sendBlocking(new SessionBindingQueryMessage(address), PacketImpl.SESS_BINDINGQUERY_RESP_V4);
         SessionBindingQueryResponseMessage_V4 response = (SessionBindingQueryResponseMessage_V4) packet;
         return new AddressQueryImpl(response.isExists(), response.getQueueNames(), response.isAutoCreateQueues(), response.isAutoCreateAddresses(), response.isDefaultPurgeOnNoConsumers(), response.getDefaultMaxConsumers(), response.isDefaultExclusive(), response.isDefaultLastValue(), response.getDefaultLastValueKey(), response.isDefaultNonDestructive(), response.getDefaultConsumersBeforeDispatch(), response.getDefaultDelayBeforeDispatch(), true, true);
      } else if (sessionChannel.supports(PacketImpl.SESS_BINDINGQUERY_RESP_V3, getServerVersion())) {
         Packet packet = sessionChannel.sendBlocking(new SessionBindingQueryMessage(address), PacketImpl.SESS_BINDINGQUERY_RESP_V3);
         SessionBindingQueryResponseMessage_V3 response = (SessionBindingQueryResponseMessage_V3) packet;
         return new AddressQueryImpl(response.isExists(), response.getQueueNames(), response.isAutoCreateQueues(), response.isAutoCreateAddresses(), ActiveMQDefaultConfiguration.getDefaultPurgeOnNoConsumers(), ActiveMQDefaultConfiguration.getDefaultMaxQueueConsumers(), null, null, null, null, null, null, true, true);
      } else if (sessionChannel.supports(PacketImpl.SESS_BINDINGQUERY_RESP_V2, getServerVersion())) {
         Packet packet = sessionChannel.sendBlocking(new SessionBindingQueryMessage(address), PacketImpl.SESS_BINDINGQUERY_RESP_V2);
         SessionBindingQueryResponseMessage_V2 response = (SessionBindingQueryResponseMessage_V2) packet;
         return new AddressQueryImpl(response.isExists(), response.getQueueNames(), response.isAutoCreateQueues(), false, ActiveMQDefaultConfiguration.getDefaultPurgeOnNoConsumers(), ActiveMQDefaultConfiguration.getDefaultMaxQueueConsumers(), null, null, null, null, null, null, true, true);
      } else {
         Packet packet = sessionChannel.sendBlocking(new SessionBindingQueryMessage(address), PacketImpl.SESS_BINDINGQUERY_RESP);
         SessionBindingQueryResponseMessage response = (SessionBindingQueryResponseMessage) packet;
         return new AddressQueryImpl(response.isExists(), response.getQueueNames(), false, false, ActiveMQDefaultConfiguration.getDefaultPurgeOnNoConsumers(), ActiveMQDefaultConfiguration.getDefaultMaxQueueConsumers(), null, null, null, null, null, null, true, true);
      }
   }

   @Override
   public void closeConsumer(final ClientConsumer consumer) throws ActiveMQException {
      sessionChannel.sendBlocking(new SessionConsumerCloseMessage(getConsumerID(consumer)), PacketImpl.NULL_RESPONSE);
   }

   @Override
   public void sendConsumerCredits(final ClientConsumer consumer, final int credits) {
      sessionChannel.send(new SessionConsumerFlowCreditMessage(getConsumerID(consumer), credits));
   }

   @Override
   public void forceDelivery(final ClientConsumer consumer, final long sequence) throws ActiveMQException {
      SessionForceConsumerDelivery request = new SessionForceConsumerDelivery(getConsumerID(consumer), sequence);
      sessionChannel.send(request);
   }

   @Override
   public void simpleCommit() throws ActiveMQException {
      simpleCommit(true);
   }

   @Override
   public void simpleCommit(boolean block) throws ActiveMQException {
      if (block) {
         if (!sessionChannel.getConnection().isVersionSupportCommitV2()) {
            sessionChannel.sendBlocking(new SessionCommitMessage(), PacketImpl.NULL_RESPONSE);
         } else {
            sessionChannel.sendBlocking(new SessionCommitMessage_V2(), PacketImpl.NULL_RESPONSE);
         }
      } else {
         sessionChannel.sendBatched(new PacketImpl(PacketImpl.SESS_COMMIT));
      }
   }

   @Override
   public void simpleRollback(boolean lastMessageAsDelivered) throws ActiveMQException {
      sessionChannel.sendBlocking(new RollbackMessage(lastMessageAsDelivered), PacketImpl.NULL_RESPONSE);
   }

   @Override
   public void sessionStart() throws ActiveMQException {
      sessionChannel.send(new PacketImpl(PacketImpl.SESS_START));
   }

   @Override
   public void sessionStop() throws ActiveMQException {
      sessionChannel.sendBlocking(new PacketImpl(PacketImpl.SESS_STOP), PacketImpl.NULL_RESPONSE);
   }

   @Override
   public void addSessionMetadata(String key, String data) throws ActiveMQException {
      sessionChannel.sendBlocking(new SessionAddMetaDataMessageV2(key, data), PacketImpl.NULL_RESPONSE);
   }

   @Override
   public void addUniqueMetaData(String key, String data) throws ActiveMQException {
      sessionChannel.sendBlocking(new SessionUniqueAddMetaDataMessage(key, data), PacketImpl.NULL_RESPONSE);
   }

   @Override
   public void xaCommit(Xid xid, boolean onePhase) throws XAException, ActiveMQException {
      SessionXACommitMessage packet = new SessionXACommitMessage(xid, onePhase);
      SessionXAResponseMessage response = (SessionXAResponseMessage) sessionChannel.sendBlocking(packet, PacketImpl.SESS_XA_RESP);

      if (response.isError()) {
         throw new XAException(response.getResponseCode());
      }

      if (logger.isTraceEnabled()) {
         logger.trace("finished commit on {} with response = {}", ClientSessionImpl.convert(xid), response);
      }
   }

   @Override
   public void xaEnd(Xid xid, int flags) throws XAException, ActiveMQException {
      Packet packet;
      if (flags == XAResource.TMSUSPEND) {
         packet = new PacketImpl(PacketImpl.SESS_XA_SUSPEND);
      } else if (flags == XAResource.TMSUCCESS) {
         packet = new SessionXAEndMessage(xid, false);
      } else if (flags == XAResource.TMFAIL) {
         packet = new SessionXAEndMessage(xid, true);
      } else {
         throw new XAException(XAException.XAER_INVAL);
      }

      SessionXAResponseMessage response = (SessionXAResponseMessage) sessionChannel.sendBlocking(packet, PacketImpl.SESS_XA_RESP);

      if (response.isError()) {
         throw new XAException(response.getResponseCode());
      }
   }

   @Override
   public void sendProducerCreditsMessage(final int credits, final SimpleString address) {
      sessionChannel.send(new SessionRequestProducerCreditsMessage(credits, address));
   }

   /**
    * Apache Artemis does support large messages
    */
   @Override
   public boolean supportsLargeMessage() {
      return true;
   }

   @Override
   public int getCreditsOnSendingFull(Message msgI) {
      return msgI.getEncodeSize();
   }

   @Override
   public void sendFullMessage(ICoreMessage msgI,
                               boolean sendBlocking,
                               SendAcknowledgementHandler handler,
                               SimpleString defaultAddress,
                               int senderID) throws ActiveMQException {
      final SessionSendMessage packet;
      if (sessionChannel.getConnection().isVersionBeforeAddressChange()) {
         packet = new SessionSendMessage_1X(msgI, sendBlocking, handler);
      } else if (sessionChannel.getConnection().isVersionBeforeAsyncResponseChange()) {
         packet = new SessionSendMessage(msgI, sendBlocking, handler);
      } else if (sessionChannel.getConnection().isBeforeProducerMetricsChanged()) {
         boolean responseRequired = confirmationWindow != -1 || sendBlocking;
         packet = new SessionSendMessage_V2(msgI, responseRequired, handler);
      } else {
         boolean responseRequired = confirmationWindow != -1 || sendBlocking;
         packet = new SessionSendMessage_V3(msgI, responseRequired, handler, senderID);
      }
      if (sendBlocking) {
         sessionChannel.sendBlocking(packet, PacketImpl.NULL_RESPONSE);
      } else {
         sessionChannel.sendBatched(packet);
      }
   }

   @Override
   public int sendInitialChunkOnLargeMessage(Message msgI) throws ActiveMQException {
      SessionSendLargeMessage initialChunk = new SessionSendLargeMessage(msgI);

      sessionChannel.send(initialChunk);

      return ((CoreMessage)msgI).getHeadersAndPropertiesEncodeSize();
   }

   @Override
   public int sendLargeMessageChunk(Message msgI,
                                    long messageBodySize,
                                    boolean sendBlocking,
                                    boolean lastChunk,
                                    byte[] chunk,
                                    int reconnectID,
                                    int senderID,
                                    SendAcknowledgementHandler messageHandler) throws ActiveMQException {
      return sendSessionSendContinuationMessage(this.sessionChannel, msgI, messageBodySize, sendBlocking, lastChunk, chunk, senderID,  messageHandler);
   }

   @Override
   public int sendServerLargeMessageChunk(Message msgI,
                                          long messageBodySize,
                                          boolean sendBlocking,
                                          boolean lastChunk,
                                          byte[] chunk,
                                          int senderID,
                                          SendAcknowledgementHandler messageHandler) throws ActiveMQException {
      return sendSessionSendContinuationMessage(this.sessionChannel, msgI, messageBodySize, sendBlocking, lastChunk, chunk, senderID, messageHandler);
   }

   @Override
   public void sendACK(boolean individual,
                       boolean block,
                       final ClientConsumer consumer,
                       final Message message) throws ActiveMQException {
      PacketImpl messagePacket;
      if (individual) {
         messagePacket = new SessionIndividualAcknowledgeMessage(getConsumerID(consumer), message.getMessageID(), block);
      } else {
         messagePacket = new SessionAcknowledgeMessage(getConsumerID(consumer), message.getMessageID(), block);
      }

      if (block) {
         sessionChannel.sendBlocking(messagePacket, PacketImpl.NULL_RESPONSE);
      } else {
         sessionChannel.sendBatched(messagePacket);
      }
   }

   @Override
   public void expireMessage(final ClientConsumer consumer, Message message) throws ActiveMQException {
      SessionExpireMessage messagePacket = new SessionExpireMessage(getConsumerID(consumer), message.getMessageID());

      sessionChannel.send(messagePacket);
   }

   @Override
   public void sessionClose() throws ActiveMQException {
      sessionChannel.sendBlocking(new SessionCloseMessage(), PacketImpl.NULL_RESPONSE);
   }

   @Override
   public void xaForget(Xid xid) throws XAException, ActiveMQException {
      SessionXAResponseMessage response = (SessionXAResponseMessage) sessionChannel.sendBlocking(new SessionXAForgetMessage(xid), PacketImpl.SESS_XA_RESP);

      if (response.isError()) {
         throw new XAException(response.getResponseCode());
      }
   }

   @Override
   public int xaPrepare(Xid xid) throws XAException, ActiveMQException {
      SessionXAPrepareMessage packet = new SessionXAPrepareMessage(xid);

      SessionXAResponseMessage response = (SessionXAResponseMessage) sessionChannel.sendBlocking(packet, PacketImpl.SESS_XA_RESP);

      if (response.isError()) {
         throw new XAException(response.getResponseCode());
      } else {
         return response.getResponseCode();
      }
   }

   @Override
   public Xid[] xaScan() throws ActiveMQException {
      SessionXAGetInDoubtXidsResponseMessage response = (SessionXAGetInDoubtXidsResponseMessage) sessionChannel.sendBlocking(new PacketImpl(PacketImpl.SESS_XA_INDOUBT_XIDS), PacketImpl.SESS_XA_INDOUBT_XIDS_RESP);

      List<Xid> xids = response.getXids();

      Xid[] xidArray = xids.toArray(new Xid[xids.size()]);

      if (logger.isTraceEnabled()) {
         StringBuilder builder = new StringBuilder();
         for (int i = 0; i < xidArray.length; i++) {
            builder.append(xidArray[i].toString());
            if (i + 1 < xidArray.length) {
               builder.append(",");
            }
         }
         logger.trace("xaScan returning {} xids = [{}]", xidArray.length, builder.toString());
      }

      return xidArray;
   }

   @Override
   public void xaRollback(Xid xid, boolean wasStarted) throws ActiveMQException, XAException {
      SessionXARollbackMessage packet = new SessionXARollbackMessage(xid);

      SessionXAResponseMessage response = (SessionXAResponseMessage) sessionChannel.sendBlocking(packet, PacketImpl.SESS_XA_RESP);

      if (response.isError()) {
         throw new XAException(response.getResponseCode());
      }
   }

   @Override
   public void xaStart(Xid xid, int flags) throws XAException, ActiveMQException {
      Packet packet;
      if (flags == XAResource.TMJOIN) {
         packet = new SessionXAJoinMessage(xid);
      } else if (flags == XAResource.TMRESUME) {
         packet = new SessionXAResumeMessage(xid);
      } else if (flags == XAResource.TMNOFLAGS) {
         // Don't need to flush since the previous end will have done this
         packet = new SessionXAStartMessage(xid);
      } else {
         throw new XAException(XAException.XAER_INVAL);
      }

      SessionXAResponseMessage response = (SessionXAResponseMessage) sessionChannel.sendBlocking(packet, PacketImpl.SESS_XA_RESP);

      if (response.isError()) {
         ActiveMQClientLogger.LOGGER.errorCallingStart(response.getMessage(), response.getResponseCode());
         throw new XAException(response.getResponseCode());
      }
   }

   @Override
   public boolean configureTransactionTimeout(int seconds) throws ActiveMQException {
      SessionXASetTimeoutResponseMessage response = (SessionXASetTimeoutResponseMessage) sessionChannel.sendBlocking(new SessionXASetTimeoutMessage(seconds), PacketImpl.SESS_XA_SET_TIMEOUT_RESP);

      return response.isOK();
   }

   @Override
   public int recoverSessionTimeout() throws ActiveMQException {
      SessionXAGetTimeoutResponseMessage response = (SessionXAGetTimeoutResponseMessage) sessionChannel.sendBlocking(new PacketImpl(PacketImpl.SESS_XA_GET_TIMEOUT), PacketImpl.SESS_XA_GET_TIMEOUT_RESP);

      return response.getTimeoutSeconds();
   }

   @Override
   public void createAddress(SimpleString address,
                             Set<RoutingType> routingTypes,
                             final boolean autoCreated) throws ActiveMQException {
      createAddress(address, EnumSet.copyOf(routingTypes), autoCreated);
   }

   @Override
   public void createAddress(SimpleString address,
                             EnumSet<RoutingType> routingTypes,
                             final boolean autoCreated) throws ActiveMQException {
      CreateAddressMessage request = new CreateAddressMessage(address, routingTypes, autoCreated, true);
      if (!sessionChannel.getConnection().isVersionBeforeAddressChange()) {
         sessionChannel.sendBlocking(request, PacketImpl.NULL_RESPONSE);
      }
   }

   @Deprecated
   @Override
   public void createQueue(SimpleString address,
                           SimpleString queueName,
                           SimpleString filterString,
                           boolean durable,
                           boolean temp,
                           boolean autoCreated) throws ActiveMQException {
      createQueue(QueueConfiguration.of(queueName)
                     .setAddress(address)
                     .setFilterString(filterString)
                     .setDurable(durable)
                     .setTemporary(temp)
                     .setAutoCreated(autoCreated));
   }

   @Deprecated
   @Override
   public void createQueue(SimpleString address,
                           SimpleString queueName,
                           boolean temp,
                           boolean autoCreated,
                           QueueAttributes queueAttributes) throws ActiveMQException {
      createQueue(queueAttributes
                     .toQueueConfiguration()
                     .setName(queueName)
                     .setAddress(address)
                     .setTemporary(temp)
                     .setAutoCreated(autoCreated));
   }

   @Deprecated
   @Override
   public void createQueue(SimpleString address,
                           RoutingType routingType,
                           SimpleString queueName,
                           SimpleString filterString,
                           boolean durable,
                           boolean temp,
                           int maxConsumers,
                           boolean purgeOnNoConsumers,
                           boolean autoCreated,
                           Boolean exclusive,
                           Boolean lastValue) throws ActiveMQException {
      createQueue(QueueConfiguration.of(queueName)
                     .setAddress(address)
                     .setTemporary(temp)
                     .setAutoCreated(autoCreated)
                     .setRoutingType(routingType)
                     .setFilterString(filterString)
                     .setDurable(durable)
                     .setMaxConsumers(maxConsumers)
                     .setPurgeOnNoConsumers(purgeOnNoConsumers)
                     .setExclusive(exclusive)
                     .setLastValue(lastValue));
   }

   @Deprecated
   @Override
   public void createQueue(SimpleString address,
                           RoutingType routingType,
                           SimpleString queueName,
                           SimpleString filterString,
                           boolean durable,
                           boolean temp,
                           int maxConsumers,
                           boolean purgeOnNoConsumers,
                           boolean autoCreated) throws ActiveMQException {
      createQueue(QueueConfiguration.of(queueName)
                     .setAddress(address)
                     .setRoutingType(routingType)
                     .setFilterString(filterString)
                     .setDurable(durable)
                     .setTemporary(temp)
                     .setMaxConsumers(maxConsumers)
                     .setPurgeOnNoConsumers(purgeOnNoConsumers)
                     .setAutoCreated(autoCreated));
   }

   @Override
   public void createQueue(QueueConfiguration queueConfiguration) throws ActiveMQException {
      // Set the non nullable (CreateQueueMessage_V2) queue attributes (all others have static defaults or get defaulted if null by address settings server side).
      if (queueConfiguration.getMaxConsumers() == null) {
         queueConfiguration.setMaxConsumers(ActiveMQDefaultConfiguration.getDefaultMaxQueueConsumers());
      }
      if (queueConfiguration.isPurgeOnNoConsumers() == null) {
         queueConfiguration.setPurgeOnNoConsumers(ActiveMQDefaultConfiguration.getDefaultPurgeOnNoConsumers());
      }

      if (sessionChannel.getConnection().isVersionBeforeAddressChange()) {
         CreateQueueMessage request = new CreateQueueMessage(queueConfiguration, true);
         sessionChannel.sendBlocking(request, PacketImpl.NULL_RESPONSE);
      } else {
         CreateQueueMessage request = new CreateQueueMessage_V2(queueConfiguration, true);
         sessionChannel.sendBlocking(request, PacketImpl.NULL_RESPONSE);
      }
   }

   @Override
   public void transferConnection(RemotingConnection newConnection) {
      this.remotingConnection = newConnection;
      sessionChannel.transferConnection((CoreRemotingConnection) newConnection);
   }

   @Override
   public boolean reattachOnNewConnection(RemotingConnection newConnection) throws ActiveMQException {

      transferConnection(newConnection);

      Packet request = new ReattachSessionMessage(name, sessionChannel.getLastConfirmedCommandID());

      Channel channel1 = getCoreConnection().getChannel(1, -1);

      ReattachSessionResponseMessage response = (ReattachSessionResponseMessage) channel1.sendBlocking(request, PacketImpl.REATTACH_SESSION_RESP);

      if (response.isReattached()) {
         logger.debug("Replaying commands for channelID={} with lastCommandID from the server={}", sessionChannel.getID(), response.getLastConfirmedCommandID());
         // The session was found on the server - we reattached transparently ok

         sessionChannel.replayCommands(response.getLastConfirmedCommandID());

         return true;
      } else {
         logger.debug("Couldn't reattach session {}, performing as a failover operation now and recreating objects", sessionChannel.getID());

         sessionChannel.clearCommands();

         return false;
      }

   }

   @Override
   public void recreateSession(final String username,
                               final String password,
                               final int minLargeMessageSize,
                               final boolean xa,
                               final boolean autoCommitSends,
                               final boolean autoCommitAcks,
                               final boolean preAcknowledge) throws ActiveMQException {
      Packet createRequest = newCreateSession(username, password, minLargeMessageSize, xa, autoCommitSends, autoCommitAcks, preAcknowledge);
      boolean retry;
      do {
         try {
            getCreateChannel().sendBlocking(createRequest, PacketImpl.CREATESESSION_RESP);
            retry = false;
         } catch (ActiveMQException e) {
            // the session was created while its server was starting, retry it:
            if (e.getType() == ActiveMQExceptionType.SESSION_CREATION_REJECTED) {
               ActiveMQClientLogger.LOGGER.retryCreateSessionSeverStarting(name);
               retry = true;
               // sleep a little bit to avoid spinning too much
               try {
                  Thread.sleep(10);
               } catch (InterruptedException ie) {
                  Thread.currentThread().interrupt();
                  throw e;
               }
            } else {
               throw e;
            }
         }
      }
      while (retry && !session.isClosing());
   }

   protected CreateSessionMessage newCreateSession(String username,
                                                   String password,
                                                   int minLargeMessageSize,
                                                   boolean xa,
                                                   boolean autoCommitSends,
                                                   boolean autoCommitAcks,
                                                   boolean preAcknowledge) {
      return new CreateSessionMessage(name, sessionChannel.getID(), getServerVersion(), username, password, minLargeMessageSize, xa, autoCommitSends, autoCommitAcks, preAcknowledge, confirmationWindow, null);
   }

   @Override
   public void recreateConsumerOnServer(ClientConsumerInternal consumerInternal,
                                        long consumerId,
                                        boolean isSessionStarted) throws ActiveMQException {
      ClientSession.QueueQuery queueInfo = consumerInternal.getQueueInfo();

      // We try to recreate any non-durable or auto-created queues, since they might not be there on failover/reconnect.
      // This allows e.g. JMS non durable subs and temporary queues to continue to be used after failover/reconnection
      if (!queueInfo.isDurable() || queueInfo.isAutoCreated()) {
         CreateQueueMessage_V2 createQueueRequest = new CreateQueueMessage_V2(queueInfo.getAddress(), queueInfo.getName(), queueInfo.getRoutingType(), queueInfo.getFilterString(), queueInfo.isDurable(), queueInfo.isTemporary(), queueInfo.getMaxConsumers(), queueInfo.isPurgeOnNoConsumers(), queueInfo.isAutoCreated(), false, queueInfo.isExclusive(), queueInfo.isGroupRebalance(), queueInfo.isGroupRebalancePauseDispatch(), queueInfo.getGroupBuckets(), queueInfo.getGroupFirstKey(), queueInfo.isLastValue(), queueInfo.getLastValueKey(), queueInfo.isNonDestructive(), queueInfo.getConsumersBeforeDispatch(), queueInfo.getDelayBeforeDispatch(), queueInfo.isAutoDelete(), queueInfo.getAutoDeleteDelay(), queueInfo.getAutoDeleteMessageCount(), queueInfo.getRingSize(), queueInfo.isEnabled());

         sendPacketWithoutLock(sessionChannel, createQueueRequest);
      }

      SessionCreateConsumerMessage createConsumerRequest = new SessionCreateConsumerMessage(getConsumerID(consumerInternal), consumerInternal.getQueueName(), consumerInternal.getFilterString(), consumerInternal.getPriority(), consumerInternal.isBrowseOnly(), false);

      sendPacketWithoutLock(sessionChannel, createConsumerRequest);

      int clientWindowSize = consumerInternal.getClientWindowSize();

      if (clientWindowSize != 0) {
         SessionConsumerFlowCreditMessage packet = new SessionConsumerFlowCreditMessage(getConsumerID(consumerInternal), clientWindowSize);

         sendPacketWithoutLock(sessionChannel, packet);
      } else {
         SessionConsumerFlowCreditMessage packet = new SessionConsumerFlowCreditMessage(getConsumerID(consumerInternal), 1);
         sendPacketWithoutLock(sessionChannel, packet);
      }

      //force a delivery to avoid a infinite waiting
      //it can happen when the consumer sends a 'forced delivery' then
      //waiting forever, while the connection is broken and the server's
      //'forced delivery' message never gets to consumer. If session
      //is reconnected, its consumer never knows and stays waiting.
      //note this message will either be ignored by consumer (forceDeliveryCount
      //doesn't match, which is fine) or be caught by consumer
      //(in which case the consumer will wake up, thus avoid the infinite waiting).
      if (isSessionStarted && consumerInternal.getForceDeliveryCount() > 0) {
         SessionForceConsumerDelivery forceDel = new SessionForceConsumerDelivery(consumerId, consumerInternal.getForceDeliveryCount() - 1);
         sendPacketWithoutLock(sessionChannel, forceDel);
      }
   }

   @Override
   public void xaFailed(Xid xid) throws ActiveMQException {
      sendPacketWithoutLock(sessionChannel, new SessionXAAfterFailedMessage(xid));
   }

   @Override
   public void restartSession() throws ActiveMQException {
      sendPacketWithoutLock(sessionChannel, new PacketImpl(PacketImpl.SESS_START));
   }

   @Override
   public void resetMetadata(Map<String, String> metaDataToSend) {
      // Resetting the metadata after failover
      for (Map.Entry<String, String> entries : metaDataToSend.entrySet()) {
         sendPacketWithoutLock(sessionChannel, new SessionAddMetaDataMessageV2(entries.getKey(), entries.getValue(), false));
      }
   }

   @Override
   public int getDefaultConsumerWindowSize(SessionQueueQueryResponseMessage response) throws ActiveMQException {
      if (response instanceof SessionQueueQueryResponseMessage_V3 v3) {
         return Objects.requireNonNullElse(v3.getDefaultConsumerWindowSize(), ActiveMQClient.DEFAULT_CONSUMER_WINDOW_SIZE);
      } else {
         return ActiveMQClient.DEFAULT_CONSUMER_WINDOW_SIZE;
      }
   }

   private Channel getCreateChannel() {
      return getCoreConnection().getChannel(1, -1);
   }

   private CoreRemotingConnection getCoreConnection() {
      return (CoreRemotingConnection) remotingConnection;
   }

   /**
    * This doesn't apply to other protocols probably, so it will be an Apache Artemis exclusive feature
    */
   private void handleConsumerDisconnected(DisconnectConsumerMessage packet) throws ActiveMQException {
      DisconnectConsumerMessage message = packet;

      session.handleConsumerDisconnect(new ActiveMQConsumerContext(message.getConsumerId()));
   }

   private void handleReceivedMessagePacket(SessionReceiveMessage messagePacket) throws Exception {
      ClientMessageInternal msgi = (ClientMessageInternal) messagePacket.getMessage();

      msgi.setDeliveryCount(messagePacket.getDeliveryCount());

      msgi.setFlowControlSize(messagePacket.getPacketSize());

      handleReceiveMessage(new ActiveMQConsumerContext(messagePacket.getConsumerID()), msgi);
   }

   private void handleReceiveLargeMessage(SessionReceiveLargeMessage serverPacket) throws Exception {
      ClientLargeMessageInternal clientLargeMessage = (ClientLargeMessageInternal) serverPacket.getLargeMessage();

      clientLargeMessage.setFlowControlSize(serverPacket.getPacketSize());

      clientLargeMessage.setDeliveryCount(serverPacket.getDeliveryCount());

      handleReceiveLargeMessage(new ActiveMQConsumerContext(serverPacket.getConsumerID()), clientLargeMessage, serverPacket.getLargeMessageSize());
   }

   private void handleReceiveContinuation(SessionReceiveContinuationMessage continuationPacket) throws Exception {
      handleReceiveContinuation(new ActiveMQConsumerContext(continuationPacket.getConsumerID()), continuationPacket.getBody(), continuationPacket.getPacketSize(), continuationPacket.isContinues());
   }

   protected void handleReceiveProducerCredits(SessionProducerCreditsMessage message) {
      handleReceiveProducerCredits(message.getAddress(), message.getCredits());
   }

   protected void handleReceiveProducerFailCredits(SessionProducerCreditsFailMessage message) {
      handleReceiveProducerFailCredits(message.getAddress(), message.getCredits());
   }

   protected void handleReceiveSlowConsumerKillMessage(DisconnectConsumerWithKillMessage message) {
      if (message.getNodeID() != null) {
         kill();
      }
   }

   private int sendSessionSendContinuationMessage(Channel channel,
                                                         Message msgI,
                                                         long messageBodySize,
                                                         boolean sendBlocking,
                                                         boolean lastChunk,
                                                         byte[] chunk,
                                                         int senderID,
                                                         SendAcknowledgementHandler messageHandler) throws ActiveMQException {
      final boolean requiresResponse = lastChunk && sendBlocking;
      final SessionSendContinuationMessage chunkPacket;
      if (sessionChannel.getConnection().isVersionBeforeAsyncResponseChange()) {
         chunkPacket = new SessionSendContinuationMessage(msgI, chunk, !lastChunk, requiresResponse, messageBodySize, messageHandler);
      } else if (sessionChannel.getConnection().isBeforeProducerMetricsChanged()) {
         chunkPacket = new SessionSendContinuationMessage_V2(msgI, chunk, !lastChunk, requiresResponse || confirmationWindow != -1, messageBodySize, messageHandler);
      } else {
         chunkPacket = new SessionSendContinuationMessage_V3(msgI, chunk, !lastChunk, requiresResponse || confirmationWindow != -1, messageBodySize, senderID, messageHandler);
      }
      //perform a weak form of flow control to avoid OOM on tight loops
      final CoreRemotingConnection connection = channel.getConnection();
      final long blockingCallTimeoutMillis = Math.max(0, connection.getBlockingCallTimeout());
      final long startFlowControl = System.nanoTime();
      try {
         final boolean isWritable = connection.blockUntilWritable(blockingCallTimeoutMillis);
         if (!isWritable) {
            final long endFlowControl = System.nanoTime();
            final long elapsedFlowControl = endFlowControl - startFlowControl;
            final long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(elapsedFlowControl);
            ActiveMQClientLogger.LOGGER.timeoutStreamingLargeMessage();
            if (logger.isDebugEnabled()) {
               logger.debug("try to write {} bytes after blocked {} ms on a not writable connection: [{}]",
                            chunkPacket.expectedEncodeSize(), elapsedMillis, connection.getID());
            }
         }
         if (requiresResponse) {
            // When sending it blocking, only the last chunk will be blocking.
            channel.sendBlocking(chunkPacket, PacketImpl.NULL_RESPONSE);
         } else {
            channel.send(chunkPacket);
         }
         return chunkPacket.getPacketSize();
      } catch (Throwable e) {
         throw new ActiveMQException(e.getMessage(), e);
      }
   }

   class ClientSessionPacketHandler implements ChannelHandler {

      @Override
      public void handlePacket(final Packet packet) {
         byte type = packet.getType();

         try {
            switch (type) {
               case DISCONNECT_CONSUMER: {
                  handleConsumerDisconnected((DisconnectConsumerMessage) packet);
                  break;
               }
               case SESS_RECEIVE_CONTINUATION: {
                  handleReceiveContinuation((SessionReceiveContinuationMessage) packet);

                  break;
               }
               case SESS_RECEIVE_MSG: {
                  handleReceivedMessagePacket((SessionReceiveMessage) packet);

                  break;
               }
               case SESS_RECEIVE_LARGE_MSG: {
                  handleReceiveLargeMessage((SessionReceiveLargeMessage) packet);

                  break;
               }
               case PacketImpl.SESS_PRODUCER_CREDITS: {
                  handleReceiveProducerCredits((SessionProducerCreditsMessage) packet);

                  break;
               }
               case PacketImpl.SESS_PRODUCER_FAIL_CREDITS: {
                  handleReceiveProducerFailCredits((SessionProducerCreditsFailMessage) packet);

                  break;
               }
               case PacketImpl.DISCONNECT_CONSUMER_KILL: {
                  handleReceiveSlowConsumerKillMessage((DisconnectConsumerWithKillMessage) packet);

                  break;
               }
               case EXCEPTION: {
                  // We can only log these exceptions
                  // maybe we should cache it on SessionContext and throw an exception on any next calls
                  ActiveMQExceptionMessage mem = (ActiveMQExceptionMessage) packet;

                  ActiveMQClientLogger.LOGGER.receivedExceptionAsynchronously(mem.getException());

                  break;
               }
               default: {
                  throw ActiveMQClientMessageBundle.BUNDLE.invalidPacket(type);
               }
            }
         } catch (Exception e) {
            throw ActiveMQClientMessageBundle.BUNDLE.failedToHandlePacket(e);
         }

         sessionChannel.confirm(packet);
      }
   }

   private long getConsumerID(ClientConsumer consumer) {
      return ((ActiveMQConsumerContext) consumer.getConsumerContext()).getId();
   }

   protected ClassLoader lookupTCCL() {
      return SecurityManagerShim.doPrivileged((PrivilegedAction<ClassLoader>) () -> Thread.currentThread().getContextClassLoader());

   }

   protected int calcWindowSize(final int windowSize) {
      int clientWindowSize;
      if (windowSize == -1) {
         // No flow control - buffer can increase without bound! Only use with
         // caution for very fast consumers
         clientWindowSize = -1;
      } else if (windowSize == 0) {
         // Slow consumer - no buffering
         clientWindowSize = 0;
      } else if (windowSize == 1) {
         // Slow consumer = buffer 1
         clientWindowSize = 1;
      } else if (windowSize > 1) {
         // Client window size is half server window size
         clientWindowSize = windowSize >> 1;
      } else {
         throw ActiveMQClientMessageBundle.BUNDLE.invalidWindowSize(windowSize);
      }

      return clientWindowSize;
   }

   private void sendPacketWithoutLock(final Channel parameterChannel, final Packet packet) {
      packet.setChannelID(parameterChannel.getID());

      Connection conn = parameterChannel.getConnection().getTransportConnection();

      ActiveMQBuffer buffer = packet.encode(this.getCoreConnection());

      conn.write(buffer, false, false);
   }

}
