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
package org.apache.activemq.artemis.core.protocol.openwire;

import javax.jms.IllegalStateException;
import javax.jms.InvalidDestinationException;
import javax.jms.JMSSecurityException;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import org.apache.activemq.advisory.AdvisorySupport;
import org.apache.activemq.artemis.api.core.ActiveMQAddressDoesNotExistException;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.ActiveMQNonExistentQueueException;
import org.apache.activemq.artemis.api.core.ActiveMQRemoteDisconnectException;
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException;
import org.apache.activemq.artemis.api.core.AutoCreateResult;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.client.ActiveMQClientLogger;
import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.persistence.CoreMessageObjectPools;
import org.apache.activemq.artemis.core.persistence.OperationContext;
import org.apache.activemq.artemis.core.protocol.openwire.amq.AMQCompositeConsumerBrokerExchange;
import org.apache.activemq.artemis.core.protocol.openwire.amq.AMQConnectionContext;
import org.apache.activemq.artemis.core.protocol.openwire.amq.AMQConsumer;
import org.apache.activemq.artemis.core.protocol.openwire.amq.AMQConsumerBrokerExchange;
import org.apache.activemq.artemis.core.protocol.openwire.amq.AMQProducerBrokerExchange;
import org.apache.activemq.artemis.core.protocol.openwire.amq.AMQSession;
import org.apache.activemq.artemis.core.protocol.openwire.amq.AMQSingleConsumerBrokerExchange;
import org.apache.activemq.artemis.core.protocol.openwire.util.OpenWireUtil;
import org.apache.activemq.artemis.core.remoting.FailureListener;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.security.SecurityAuth;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.server.SlowConsumerDetectionListener;
import org.apache.activemq.artemis.core.server.TempResourceObserver;
import org.apache.activemq.artemis.core.server.impl.RefsOperation;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.transaction.ResourceManager;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.core.transaction.TransactionOperationAbstract;
import org.apache.activemq.artemis.core.transaction.TransactionPropertyIndexes;
import org.apache.activemq.artemis.logs.AuditLogger;
import org.apache.activemq.artemis.spi.core.protocol.AbstractRemotingConnection;
import org.apache.activemq.artemis.spi.core.protocol.ConnectionEntry;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.apache.activemq.artemis.utils.UUIDGenerator;
import org.apache.activemq.artemis.utils.actors.ThresholdActor;
import org.apache.activemq.artemis.utils.collections.ConcurrentHashSet;
import org.apache.activemq.command.ActiveMQDestination;
import org.apache.activemq.command.ActiveMQMessage;
import org.apache.activemq.command.ActiveMQTempQueue;
import org.apache.activemq.command.ActiveMQTempTopic;
import org.apache.activemq.command.ActiveMQTopic;
import org.apache.activemq.command.BrokerInfo;
import org.apache.activemq.command.BrokerSubscriptionInfo;
import org.apache.activemq.command.Command;
import org.apache.activemq.command.ConnectionControl;
import org.apache.activemq.command.ConnectionError;
import org.apache.activemq.command.ConnectionId;
import org.apache.activemq.command.ConnectionInfo;
import org.apache.activemq.command.ConsumerControl;
import org.apache.activemq.command.ConsumerId;
import org.apache.activemq.command.ConsumerInfo;
import org.apache.activemq.command.ControlCommand;
import org.apache.activemq.command.DataArrayResponse;
import org.apache.activemq.command.DestinationInfo;
import org.apache.activemq.command.ExceptionResponse;
import org.apache.activemq.command.FlushCommand;
import org.apache.activemq.command.IntegerResponse;
import org.apache.activemq.command.KeepAliveInfo;
import org.apache.activemq.command.Message;
import org.apache.activemq.command.MessageAck;
import org.apache.activemq.command.MessageDispatch;
import org.apache.activemq.command.MessageDispatchNotification;
import org.apache.activemq.command.MessagePull;
import org.apache.activemq.command.ProducerAck;
import org.apache.activemq.command.ProducerId;
import org.apache.activemq.command.ProducerInfo;
import org.apache.activemq.command.RemoveInfo;
import org.apache.activemq.command.RemoveSubscriptionInfo;
import org.apache.activemq.command.Response;
import org.apache.activemq.command.SessionId;
import org.apache.activemq.command.SessionInfo;
import org.apache.activemq.command.ShutdownInfo;
import org.apache.activemq.command.TransactionId;
import org.apache.activemq.command.TransactionInfo;
import org.apache.activemq.command.WireFormatInfo;
import org.apache.activemq.command.XATransactionId;
import org.apache.activemq.openwire.OpenWireFormat;
import org.apache.activemq.state.CommandVisitor;
import org.apache.activemq.state.ConnectionState;
import org.apache.activemq.state.ConsumerState;
import org.apache.activemq.state.SessionState;
import org.apache.activemq.transport.TransmitCallback;
import org.apache.activemq.util.ByteSequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents an activemq connection.
 */
public class OpenWireConnection extends AbstractRemotingConnection implements SecurityAuth, TempResourceObserver {


   private final Object lockSend = new Object();

   // to be used on the packet size estimate processing for the ThresholdActor
   private static final int MINIMAL_SIZE_ESTIAMTE = 1024;

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final KeepAliveInfo PING = new KeepAliveInfo();

   private final OpenWireProtocolManager protocolManager;

   private volatile ScheduledFuture ttlCheck;

   //separated in/out wireFormats allow deliveries (eg async and consumers) to not slow down bufferReceived
   private final OpenWireFormat inWireFormat;

   private final OpenWireFormat outWireFormat;

   private AMQConnectionContext context;

   private final int actorThresholdBytes;

   private final AtomicBoolean stopping = new AtomicBoolean(false);

   private final Map<String, SessionId> sessionIdMap = new ConcurrentHashMap<>();

   private final Map<ConsumerId, AMQConsumerBrokerExchange> consumerExchanges = new ConcurrentHashMap<>();
   private final Map<ProducerId, AMQProducerBrokerExchange> producerExchanges = new ConcurrentHashMap<>();

   private final Map<SessionId, AMQSession> sessions = new ConcurrentHashMap<>();

   private final CoreMessageObjectPools coreMessageObjectPools = new CoreMessageObjectPools();

   private volatile ConnectionState state;

   private volatile boolean noLocal;

   /**
    * Openwire doesn't sen transactions associated with any sessions. It will however send beingTX / endTX as it would
    * be doing it with XA Transactions. But always without any association with Sessions. This collection will hold
    * nonXA transactions. Hopefully while they are in transit only.
    */
   private final Map<TransactionId, Transaction> txMap = new ConcurrentHashMap<>();

   private final ActiveMQServer server;

   /**
    * This is to be used with connection operations that don't have a session. Such as TM operations.
    */
   private ServerSession internalSession;

   private final OperationContext operationContext;

   private static final AtomicLongFieldUpdater<OpenWireConnection> LAST_SENT_UPDATER = AtomicLongFieldUpdater.newUpdater(OpenWireConnection.class, "lastSent");
   private volatile long lastSent = -1;

   private volatile boolean autoRead = true;

   private ConnectionEntry connectionEntry;
   private boolean useKeepAlive;
   private long maxInactivityDuration;
   private volatile ThresholdActor<Command> openWireActor;

   private final Set<SimpleString> knownDestinations = new ConcurrentHashSet<>();

   private final AtomicBoolean disableTtl = new AtomicBoolean(false);

   private String validatedUser = null;

   public void block() {
      openWireActor.pauseProcessing();
   }

   public void unblock() {
      openWireActor.resumeProcessing();
   }

   public OpenWireConnection(Connection connection,
                             ActiveMQServer server,
                             OpenWireProtocolManager openWireProtocolManager,
                             OpenWireFormat wf,
                             Executor executor) {
      this(connection, server, openWireProtocolManager, wf, executor, TransportConstants.DEFAULT_TCP_RECEIVEBUFFER_SIZE);
   }

   public OpenWireConnection(Connection connection,
                             ActiveMQServer server,
                             OpenWireProtocolManager openWireProtocolManager,
                             OpenWireFormat wf,
                             Executor executor,
                             int actorThresholdBytes) {
      super(connection, executor);
      this.server = server;
      this.operationContext = server.newOperationContext();
      this.protocolManager = openWireProtocolManager;
      this.inWireFormat = wf;
      this.outWireFormat = wf.copy();
      this.useKeepAlive = openWireProtocolManager.isUseKeepAlive();
      this.maxInactivityDuration = openWireProtocolManager.getMaxInactivityDuration();
      this.actorThresholdBytes = actorThresholdBytes;
   }

   // SecurityAuth implementation
   @Override
   public String getUsername() {
      ConnectionInfo info = getConnectionInfo();
      if (info == null) {
         return null;
      }
      return info.getUserName();
   }


   public OperationContext getOperationContext() {
      return operationContext;
   }

   // SecurityAuth implementation
   @Override
   public OpenWireConnection getRemotingConnection() {
      return this;
   }

   // SecurityAuth implementation
   @Override
   public String getSecurityDomain() {
      return protocolManager.getSecurityDomain();
   }

   // SecurityAuth implementation
   @Override
   public String getPassword() {
      ConnectionInfo info = getConnectionInfo();
      if (info == null) {
         return null;
      }
      return info.getPassword();
   }

   private ConnectionInfo getConnectionInfo() {
      if (state == null) {
         return null;
      }
      return state.getInfo();
   }

   //tells the connection that
   //some bytes just sent
   private void bufferSent() {
      //much cheaper than a volatile set if contended, but less precise (ie allows stale loads)
      LAST_SENT_UPDATER.lazySet(this, System.currentTimeMillis());
   }

   @Override
   public void bufferReceived(Object connectionID, ActiveMQBuffer buffer) {
      super.bufferReceived(connectionID, buffer);

      try {
         Command command = (Command) inWireFormat.unmarshal(buffer);

         logCommand(command, true);

         final ThresholdActor<Command> localVisibleActor = openWireActor;
         if (localVisibleActor != null) {
            localVisibleActor.act(command);
         } else {
            act(command);
         }
      } catch (Exception e) {
         logger.debug(e.getMessage(), e);
         sendException(e);
      }

   }

   public void restoreAutoRead() {
      if (!autoRead) {
         autoRead = true;
         openWireActor.flush();
      }
   }

   public void blockConnection() {
      autoRead = false;
      disableAutoRead();
   }

   private void disableAutoRead() {
      getTransportConnection().setAutoRead(false);
      disableTtl();
   }

   protected void flushedActor() {
      getTransportConnection().setAutoRead(autoRead);
      if (autoRead) {
         enableTtl();
      }
   }


   private void act(Command command) {
      try {
         recoverOperationContext();

         if (AuditLogger.isAnyLoggingEnabled()) {
            AuditLogger.setRemoteAddress(getRemoteAddress());
         }

         if (this.protocolManager.invokeIncoming(command, this) != null) {
            logger.debug("Interceptor rejected OpenWire command: {}", command);
            disconnect(true);
            return;
         }

         boolean responseRequired = command.isResponseRequired();
         int commandId = command.getCommandId();

         // ignore pings
         if (command.getClass() != KeepAliveInfo.class) {
            Response response = null;

            try {
               setLastCommand(command);
               response = command.visit(commandProcessorInstance);
            } catch (Exception e) {
               logger.warn("Errors occurred during the buffering operation ", e);
               if (responseRequired) {
                  response = convertException(e);
               }
            } finally {
               setLastCommand(null);
            }

            if (response instanceof ExceptionResponse exceptionResponse) {
               Throwable cause = exceptionResponse.getException();
               if (!responseRequired) {
                  serviceException(cause);
                  response = null;
               }
               // If there was an exception when processing ConnectionInfo we should
               // stop the connection to prevent dangling sockets
               if (command instanceof ConnectionInfo) {
                  delayedStop(2000, cause.getMessage(), cause);
               }
            }

            if (responseRequired) {
               if (response == null) {
                  response = new Response();
                  response.setCorrelationId(commandId);
               }
            }

            // The context may have been flagged so that the response is not
            // sent.
            if (context != null) {
               if (context.isDontSendReponse()) {
                  context.setDontSendReponse(false);
                  response = null;
               }
            }

            sendAsyncResponse(commandId, response);
         }
      } catch (Exception e) {
         logger.debug(e.getMessage(), e);

         sendException(e);
      } finally {
         clearupOperationContext();
      }
   }

   /**
    * It will send the response through the operation context, as soon as everything is confirmed on disk
    */
   private void sendAsyncResponse(final int commandId, final Response response) throws Exception {
      if (response != null) {
         operationContext.executeOnCompletion(new IOCallback() {
            @Override
            public void done() {
               if (!protocolManager.isStopping()) {
                  try {
                     response.setCorrelationId(commandId);
                     dispatchSync(response);
                  } catch (Exception e) {
                     sendException(e);
                  }
               }
            }

            @Override
            public void onError(int errorCode, String errorMessage) {
               sendException(new IOException(errorCode + "-" + errorMessage));
            }
         });
      }
   }

   public void sendException(Exception e) {
      Response resp = convertException(e);
      if (context != null) {
         Command command = context.getLastCommand();
         if (command != null) {
            resp.setCorrelationId(command.getCommandId());
         }
      }
      try {
         dispatch(resp);
      } catch (IOException e2) {
         logger.warn(e.getMessage(), e2);
      }
   }

   private Response convertException(Exception e) {
      Response resp;
      if (e instanceof ActiveMQSecurityException) {
         resp = new ExceptionResponse(new JMSSecurityException(e.getMessage()));
      } else if (e instanceof ActiveMQNonExistentQueueException) {
         resp = new ExceptionResponse(new InvalidDestinationException(e.getMessage()));
      } else {
         resp = new ExceptionResponse(e);
      }
      return resp;
   }

   private void setLastCommand(Command command) {
      if (context != null) {
         context.setLastCommand(command);
      }
   }

   @Override
   public void destroy() {
      fail(null, null);
   }

   @Override
   public void disconnect(boolean criticalError) {
      this.disconnect(null, null, criticalError);
   }

   @Override
   public void flush() {
      checkInactivity();
   }

   @Override
   public void close() {
      destroy();
   }

   private void checkInactivity() {
      if (!this.useKeepAlive) {
         return;
      }

      long dur = System.currentTimeMillis() - lastSent;
      if (dur >= this.maxInactivityDuration / 2) {
         this.sendCommand(PING);
      }
   }

   private void callFailureListeners(final ActiveMQException me) {
      final List<FailureListener> listenersClone = new ArrayList<>(failureListeners);

      for (final FailureListener listener : listenersClone) {
         try {
            listener.connectionFailed(me, false);
         } catch (final Throwable t) {
            // Failure of one listener to execute shouldn't prevent others
            // from
            // executing
            ActiveMQServerLogger.LOGGER.errorCallingFailureListener(t);
         }
      }
   }

   // send a WireFormatInfo to the peer
   public void sendHandshake() {
      WireFormatInfo info = inWireFormat.getPreferedWireFormatInfo();
      sendCommand(info);
   }

   public ConnectionState getState() {
      return state;
   }

   public void physicalSend(Command command) throws IOException {
      if (this.protocolManager.invokeOutgoing(command, this) != null) {
         return;
      }

      logCommand(command, false);

      try {
         final ByteSequence bytes = outWireFormat.marshal(command);
         final int bufferSize = bytes.length;
         final int maxChunkSize = protocolManager.getOpenwireMaxPacketChunkSize();

         // We can't let any other packet to sneak in while chunkSend is happening.
         // otherwise we may get wrong packts delivered
         synchronized (lockSend) {
            if (maxChunkSize > 0 && bufferSize > maxChunkSize) {
               chunkSend(bytes, bufferSize, maxChunkSize);
            } else {
               final ActiveMQBuffer buffer = transportConnection.createTransportBuffer(bufferSize);
               buffer.writeBytes(bytes.data, bytes.offset, bufferSize);
               transportConnection.write(buffer, false, false);
            }
         }
         bufferSent();
      } catch (IOException e) {
         throw e;
      } catch (Throwable t) {
         logger.error("error sending", t);
      }

   }

   private void chunkSend(final ByteSequence bytes, final int bufferSize, final int maxChunkSize) {
      if (logger.isTraceEnabled()) {
         logger.trace("Sending a big packet sized as {} with smaller packets of {}", bufferSize, maxChunkSize);
      }
      while (bytes.remaining() > 0) {
         int chunkSize = Math.min(bytes.remaining(), maxChunkSize);
         if (logger.isTraceEnabled()) {
            logger.trace("Sending a partial packet of {} bytes, starting at {}", chunkSize, bytes.remaining());
         }
         final ActiveMQBuffer chunk = transportConnection.createTransportBuffer(chunkSize);
         chunk.writeBytes(bytes.data, bytes.offset, chunkSize);
         transportConnection.write(chunk, false, false);
         bytes.setOffset(bytes.getOffset() + chunkSize);
      }
   }

   public void dispatchAsync(Command message) throws Exception {
      dispatchSync(message);
   }

   public void dispatchSync(Command message) throws Exception {
      processDispatch(message);
   }

   public void serviceException(Throwable e) throws Exception {
      ConnectionError ce = new ConnectionError();
      ce.setException(e);
      dispatchAsync(ce);
   }

   public void dispatch(Command command) throws IOException {
      this.physicalSend(command);
   }

   protected void processDispatch(Command command) throws IOException {
      MessageDispatch messageDispatch = (MessageDispatch) (command.isMessageDispatch() ? command : null);
      try {
         if (!stopping.get()) {
            if (messageDispatch != null) {
               protocolManager.preProcessDispatch(messageDispatch);
            }
            dispatch(command);
         }
      } catch (IOException e) {
         if (messageDispatch != null) {
            TransmitCallback sub = messageDispatch.getTransmitCallback();
            protocolManager.postProcessDispatch(messageDispatch);
            if (sub != null) {
               sub.onFailure();
            }
            messageDispatch = null;
            throw e;
         }
      } finally {
         if (messageDispatch != null) {
            TransmitCallback sub = messageDispatch.getTransmitCallback();
            protocolManager.postProcessDispatch(messageDispatch);
            if (sub != null) {
               sub.onSuccess();
            }
         }
      }
   }

   private void addConsumerBrokerExchange(ConsumerId id, AMQSession amqSession, List<AMQConsumer> consumerList) {
      AMQConsumerBrokerExchange result = consumerExchanges.get(id);
      if (result == null) {
         if (consumerList.size() == 1) {
            result = new AMQSingleConsumerBrokerExchange(amqSession, consumerList.get(0));
         } else {
            result = new AMQCompositeConsumerBrokerExchange(amqSession, consumerList);
         }
         synchronized (consumerExchanges) {
            consumerExchanges.put(id, result);
         }
      }
   }

   private AMQProducerBrokerExchange getProducerBrokerExchange(ProducerId id) throws IOException {
      AMQProducerBrokerExchange result = producerExchanges.get(id);
      if (result == null) {
         synchronized (producerExchanges) {
            result = new AMQProducerBrokerExchange();
            result.setConnectionContext(context);
            //todo implement reconnect https://issues.apache.org/jira/browse/ARTEMIS-194
            //todo: this used to check for  && this.acceptorUsed.isAuditNetworkProducers()
            if (context.isReconnect() || (context.isNetworkConnection())) {
               // once implemented ARTEMIS-194, we need to set the storedSequenceID here somehow
               // We have different semantics on Artemis Journal, but we could adapt something for this
               // TBD during the implementation of ARTEMIS-194
               result.setLastStoredSequenceId(0);
            }
            SessionState ss = state.getSessionState(id.getParentId());
            if (ss != null) {
               result.setProducerState(ss.getProducerState(id));
            }
            producerExchanges.put(id, result);
         }
      }
      return result;
   }

   public void deliverMessage(MessageDispatch dispatch) {
      Message m = dispatch.getMessage();
      if (m != null) {
         long endTime = System.currentTimeMillis();
         m.setBrokerOutTime(endTime);
      }

      sendCommand(dispatch);
   }

   public OpenWireFormat wireFormat() {
      return this.inWireFormat;
   }

   private void rollbackInProgressLocalTransactions() {

      for (Transaction tx : txMap.values()) {
         tx.tryRollback();
      }
   }

   private void shutdown(boolean fail) {

      try {
         if (fail) {
            transportConnection.forceClose();
         } else {
            transportConnection.close();
         }
      } finally {
         ScheduledFuture ttlCheckToCancel = this.ttlCheck;
         this.ttlCheck = null;
         if (ttlCheckToCancel != null) {
            ttlCheckToCancel.cancel(true);
         }
      }
   }

   private void disconnect(ActiveMQException me, String reason, boolean fail) {
      ThresholdActor<Command> localActor = openWireActor;
      if (localActor != null) {
         localActor.flush();
      }

      if (context == null || destroyed) {
         return;
      }

      // Don't allow things to be added to the connection state while we
      // are shutting down.
      // is it necessary? even, do we need state at all?
      state.shutdown();

      try {
         for (SessionId sessionId : sessionIdMap.values()) {
            AMQSession session = sessions.get(sessionId);
            if (session != null) {
               session.close(fail);
            }
         }
         internalSession.close(false);
      } catch (Exception e) {
         logger.warn(e.getMessage(), e);
      }

      // Then call the listeners
      // this should closes underlying sessions
      callFailureListeners(me);

      // this should clean up temp dests
      callClosingListeners();

      destroyed = true;

      //before closing transport, sendCommand the last response if any
      Command command = context.getLastCommand();
      if (command != null && command.isResponseRequired()) {
         Response lastResponse = new Response();
         lastResponse.setCorrelationId(command.getCommandId());
         try {
            dispatchSync(lastResponse);
         } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
         }
      }
      if (fail) {
         shutdown(fail);
      }
   }

   @Override
   public void disconnect(String reason, boolean fail) {
      this.disconnect(null, reason, fail);
   }

   @Override
   public void fail(ActiveMQException me, String message) {
      final ThresholdActor<Command> localVisibleActor = openWireActor;
      if (localVisibleActor != null) {
         localVisibleActor.requestShutdown();
      }

      if (executor != null) {
         executor.execute(() -> doFail(me, message));
      } else {
         doFail(me, message);
      }
   }

   private void doFail(ActiveMQException me, String message) {

      recoverOperationContext();

      try {
         if (me != null) {
            //filter it like the other protocols
            if (!(me instanceof ActiveMQRemoteDisconnectException)) {
               ActiveMQClientLogger.LOGGER.connectionFailureDetected(this.transportConnection.getProtocolConnection().getProtocolName(), this.transportConnection.getRemoteAddress(), me.getMessage(), me.getType());
            }
         }
         try {
            if (this.getConnectionInfo() != null) {
               protocolManager.removeConnection(getClientID(), this);
            }
         } finally {
            try {
               disconnect(false);
            } catch (Throwable e) {
               // it should never happen, but never say never
               logger.debug("OpenWireConnection::disconnect failure", e);
            }

            // there may be some transactions not associated with sessions
            // deal with them after sessions are removed via connection removal
            operationContext.executeOnCompletion(new IOCallback() {
               @Override
               public void done() {
                  rollbackInProgressLocalTransactions();
               }

               @Override
               public void onError(int errorCode, String errorMessage) {
                  rollbackInProgressLocalTransactions();
               }
            });
         }
         shutdown(true);
      } finally {
         try {
            transportConnection.close();
         } catch (Throwable e2) {
            logger.warn(e2.getMessage(), e2);
         }
      }
   }

   private void delayedStop(final int waitTimeMillis, final String reason, Throwable cause) {
      if (waitTimeMillis > 0) {
         try {
            protocolManager.getScheduledPool().schedule(() -> {
               fail(new ActiveMQException(reason, cause, ActiveMQExceptionType.GENERIC_EXCEPTION), reason);
               logger.warn("Stopping {} because {}", transportConnection.getRemoteAddress(), reason);
            }, waitTimeMillis, TimeUnit.MILLISECONDS);
         } catch (Throwable t) {
            logger.warn("Cannot stop connection. This exception will be ignored.", t);
         }
      }
   }

   public AMQConnectionContext getContext() {
      return this.context;
   }

   public void updateClient(ConnectionControl control) throws Exception {
      if (protocolManager.isUpdateClusterClients()) {
         dispatchAsync(control);
      }
   }

   public AMQConnectionContext initContext(ConnectionInfo info) throws Exception {
      WireFormatInfo wireFormatInfo = inWireFormat.getPreferedWireFormatInfo();
      // Older clients should have been defaulting this field to true.. but
      // they were not.
      if (wireFormatInfo != null && wireFormatInfo.getVersion() <= 2) {
         info.setClientMaster(true);
      }

      state = new ConnectionState(info);

      context = new AMQConnectionContext();

      state.reset(info);

      // Setup the context.
      String clientId = info.getClientId();
      context.setBroker(protocolManager);
      context.setClientId(clientId);
      context.setClientMaster(info.isClientMaster());
      context.setConnection(this);
      context.setConnectionId(info.getConnectionId());
      // for now we pass the manager as the connector and see what happens
      // it should be related to activemq's Acceptor
      context.setFaultTolerant(info.isFaultTolerant());
      context.setUserName(info.getUserName());
      context.setWireFormatInfo(wireFormatInfo);
      context.setReconnect(info.isFailoverReconnect());
      context.setConnectionState(state);
      if (info.getClientIp() == null) {
         info.setClientIp(getRemoteAddress());
      }

      createInternalSession(info);

      // the actor can only be used after the WireFormat has been initialized with versioning
      this.openWireActor = new ThresholdActor<>(executor, this::act, actorThresholdBytes, OpenWireConnection::getSize, this::disableAutoRead, this::flushedActor);

      return context;
   }

   private static int getSize(Command command) {
      if (command instanceof ActiveMQMessage activeMQMessage) {
         return activeMQMessage.getSize();
      } else {
         return MINIMAL_SIZE_ESTIAMTE;
      }
   }

   private void createInternalSession(ConnectionInfo info) throws Exception {
      internalSession = server.createSession(UUIDGenerator.getInstance().generateStringUUID(), context.getUserName(), info.getPassword(), -1, this, true, false, false, false, null, null, true, operationContext, protocolManager.getPrefixes(), protocolManager.getSecurityDomain(), validatedUser, false);
   }

   /**
    * This will answer with commands to the client
    */
   public boolean sendCommand(final Command command) {
      logger.trace("sending {}", command);

      if (isDestroyed()) {
         return false;
      }

      try {
         physicalSend(command);
      } catch (Throwable t) {
         return false;
      }
      return true;
   }

   public void addDestination(DestinationInfo info) throws Exception {
      boolean created = false;
      ActiveMQDestination dest = info.getDestination();
      if (!protocolManager.isSupportAdvisory() && AdvisorySupport.isAdvisoryTopic(dest)) {
         return;
      }

      SimpleString qName = SimpleString.of(dest.getPhysicalName());

      AutoCreateResult autoCreateResult = internalSession.checkAutoCreate(QueueConfiguration.of(qName)
                                                                             .setRoutingType(dest.isQueue() ? RoutingType.ANYCAST : RoutingType.MULTICAST)
                                                                             .setDurable(!dest.isTemporary())
                                                                             .setTemporary(dest.isTemporary()));
      if (autoCreateResult == AutoCreateResult.CREATED) {
         created = true;
         if (AdvisorySupport.isAdvisoryTopic(dest) && protocolManager.isSuppressInternalManagementObjects()) {
            internalSession.getAddress(qName).setInternal(true);
         }
      } else if (autoCreateResult == AutoCreateResult.NOT_FOUND) {
         throw new InvalidDestinationException(dest.getDestinationTypeAsString() + " " + dest.getPhysicalName() + " does not exist.");
      }

      if (dest.isTemporary()) {
         //Openwire needs to store the DestinationInfo in order to send Advisory messages to clients
         if (!tempDestinationExists(info.getDestination().getPhysicalName())) {
            this.state.addTempDestination(info);
            if (logger.isDebugEnabled()) {
               logger.debug("{} added temp destination to state: {} ; {}",
                  this, info.getDestination().getPhysicalName(), state.getTempDestinations().size());
            }
         }
      }

      if (created && !AdvisorySupport.isAdvisoryTopic(dest)) {
         AMQConnectionContext context = getContext();
         DestinationInfo advInfo = new DestinationInfo(context.getConnectionId(), DestinationInfo.ADD_OPERATION_TYPE, dest);

         ActiveMQTopic topic = AdvisorySupport.getDestinationAdvisoryTopic(dest);
         protocolManager.fireAdvisory(context, topic, advInfo);
      }
   }

   public void updateConsumer(ConsumerControl consumerControl) {
      ConsumerId consumerId = consumerControl.getConsumerId();
      AMQConsumerBrokerExchange exchange = this.consumerExchanges.get(consumerId);
      if (exchange != null) {
         exchange.updateConsumerPrefetchSize(consumerControl.getPrefetch());
      }
   }

   public void addConsumer(ConsumerInfo info) throws Exception {
      // Todo: add a destination interceptors holder here (amq supports this)
      SessionId sessionId = info.getConsumerId().getParentId();
      ConnectionId connectionId = sessionId.getParentId();
      ConnectionState cs = getState();
      if (cs == null) {
         throw new IllegalStateException("Cannot add a consumer to a connection that had not been registered: " + connectionId);
      }
      SessionState ss = cs.getSessionState(sessionId);
      if (ss == null) {
         throw new IllegalStateException(server + " Cannot add a consumer to a session that had not been registered: " + sessionId);
      }
      // Avoid replaying dup commands
      if (!ss.getConsumerIds().contains(info.getConsumerId())) {

         AMQSession amqSession = sessions.get(sessionId);
         if (amqSession == null) {
            throw new IllegalStateException("Session not exist! : " + sessionId);
         }

         List<AMQConsumer> consumersList = amqSession.createConsumer(info, new SlowConsumerDetection());

         this.addConsumerBrokerExchange(info.getConsumerId(), amqSession, consumersList);
         ss.addConsumer(info);
         info.setLastDeliveredSequenceId(RemoveInfo.LAST_DELIVERED_UNKNOWN);

         if (consumersList.isEmpty()) {
            return;
         }

         consumersList.forEach((c) -> c.start());

         if (AdvisorySupport.isAdvisoryTopic(info.getDestination())) {
            //advisory for temp destinations
            if (AdvisorySupport.isTempDestinationAdvisoryTopic(info.getDestination())) {
               // Replay the temporary destinations.
               List<DestinationInfo> tmpDests = this.protocolManager.getTemporaryDestinations();
               for (DestinationInfo di : tmpDests) {
                  ActiveMQTopic topic = AdvisorySupport.getDestinationAdvisoryTopic(di.getDestination());
                  String originalConnectionId = di.getConnectionId().getValue();
                  protocolManager.fireAdvisory(context, topic, di, info.getConsumerId(), originalConnectionId);
               }
            }
         }
      }
   }

   public void setConnectionEntry(ConnectionEntry connectionEntry) {
      this.connectionEntry = connectionEntry;
   }

   @Override
   public boolean checkDataReceived() {
      if (disableTtl.get()) {
         return true;
      }
      return super.checkDataReceived();
   }

   public void setUpTtl(final long inactivityDuration,
                        final long inactivityDurationInitialDelay,
                        final boolean useKeepAlive) {
      this.useKeepAlive = useKeepAlive;
      this.maxInactivityDuration = inactivityDuration;

      if (this.useKeepAlive) {
         ttlCheck = protocolManager.getScheduledPool().schedule(() -> {
            if (inactivityDuration >= 0) {
               connectionEntry.ttl = inactivityDuration;
            }
         }, inactivityDurationInitialDelay, TimeUnit.MILLISECONDS);
         checkInactivity();
      }
   }

   public void addKnownDestination(final SimpleString address) {
      AddressSettings addressSettings = server.getAddressSettingsRepository().getMatch(address.toString());
      if (!addressSettings.isAutoDeleteAddresses() && !addressSettings.isAutoDeleteQueues()) {
         knownDestinations.add(address);
      }
   }

   public boolean containsKnownDestination(final SimpleString address) {
      return knownDestinations.contains(address);
   }

   @Override
   public void tempQueueDeleted(SimpleString queueName) {
      tempDestinationDeleted(queueName, new ActiveMQTempQueue(queueName.toString()));
   }

   @Override
   public void tempAddressDeleted(SimpleString addressName) {
      tempDestinationDeleted(addressName, new ActiveMQTempTopic(addressName.toString()));
   }

   private void tempDestinationDeleted(SimpleString name, ActiveMQDestination dest) {
      state.removeTempDestination(dest);
      if (logger.isDebugEnabled()) {
         logger.debug("{} removed temp destination from state: {} ; {}", this, name, state.getTempDestinations().size());
      }

      if (!AdvisorySupport.isAdvisoryTopic(dest)) {
         AMQConnectionContext context = getContext();
         DestinationInfo advInfo = new DestinationInfo(context.getConnectionId(), DestinationInfo.REMOVE_OPERATION_TYPE, dest);

         ActiveMQTopic topic = AdvisorySupport.getDestinationAdvisoryTopic(dest);
         try {
            protocolManager.fireAdvisory(context, topic, advInfo);
         } catch (Exception e) {
            logger.warn("Failed to fire advisory on {}", topic, e);
         }
      }
   }

   public void disableTtl() {
      disableTtl.set(true);
   }

   public void enableTtl() {
      disableTtl.set(false);
   }

   public boolean isNoLocal() {
      return noLocal;
   }

   public void setNoLocal(boolean noLocal) {
      this.noLocal = noLocal;
   }

   public List<DestinationInfo> getTemporaryDestinations() {
      return state.getTempDestinations();
   }

   public boolean isSuppressInternalManagementObjects() {
      return protocolManager.isSuppressInternalManagementObjects();
   }

   public boolean isSuppportAdvisory() {
      return protocolManager.isSupportAdvisory();
   }

   public String getValidatedUser() {
      return validatedUser;
   }

   public void setValidatedUser(String validatedUser) {
      this.validatedUser = validatedUser;
   }

   class SlowConsumerDetection implements SlowConsumerDetectionListener {

      @Override
      public void onSlowConsumer(ServerConsumer consumer) {
         if (consumer.getProtocolData() != null && consumer.getProtocolData() instanceof AMQConsumer amqConsumer) {
            ActiveMQTopic topic = AdvisorySupport.getSlowConsumerAdvisoryTopic(amqConsumer.getOpenwireDestination());
            ActiveMQMessage advisoryMessage = new ActiveMQMessage();
            try {
               advisoryMessage.setStringProperty(AdvisorySupport.MSG_PROPERTY_CONSUMER_ID, amqConsumer.getId().toString());
               protocolManager.fireAdvisory(context, topic, advisoryMessage, amqConsumer.getId(), null);
            } catch (Exception e) {
               logger.warn("Error during method invocation", e);
            }
         }
      }
   }

   public void addSessions(Set<SessionId> sessionSet) {
      for (SessionId sid : sessionSet) {
         addSession(getState().getSessionState(sid).getInfo());
      }
   }

   public AMQSession addSession(SessionInfo ss) {
      AMQSession amqSession = new AMQSession(getState().getInfo(), ss, server, this, protocolManager, coreMessageObjectPools);
      amqSession.initialize();
      amqSession.start();

      sessions.put(ss.getSessionId(), amqSession);
      sessionIdMap.put(amqSession.getCoreSession().getName(), ss.getSessionId());
      return amqSession;
   }

   public void removeSession(AMQConnectionContext context, SessionInfo info) throws Exception {
      AMQSession session = sessions.remove(info.getSessionId());
      if (session != null) {
         sessionIdMap.remove(session.getCoreSession().getName());
         session.close();
      }
   }

   public AMQSession getSession(SessionId sessionId) {
      return sessions.get(sessionId);
   }

   public void removeDestination(ActiveMQDestination dest) throws Exception {
      if (!dest.isTemporary()) {
         // this should not really happen so I'm not creating a Logger for this
         logger.warn("OpenWire client sending a queue remove towards {}", dest.getPhysicalName());
      }
      if (dest.isQueue()) {
         try {
            server.destroyQueue(SimpleString.of(dest.getPhysicalName()), getRemotingConnection());
         } catch (ActiveMQNonExistentQueueException neq) {
            //this is ok, ActiveMQ 5 allows this and will actually do it quite often
            logger.debug("queue never existed");
         }
      } else {
         try {
            server.removeAddressInfo(SimpleString.of(dest.getPhysicalName()), getRemotingConnection(), true);
         } catch (ActiveMQAddressDoesNotExistException neq) {
            logger.debug("address never existed");
         }
      }

      if (!AdvisorySupport.isAdvisoryTopic(dest)) {
         AMQConnectionContext context = getContext();
         DestinationInfo advInfo = new DestinationInfo(context.getConnectionId(), DestinationInfo.REMOVE_OPERATION_TYPE, dest);

         ActiveMQTopic topic = AdvisorySupport.getDestinationAdvisoryTopic(dest);
         protocolManager.fireAdvisory(context, topic, advInfo);
      }
   }

   private void propagateLastSequenceId(SessionState sessionState, long lastDeliveredSequenceId) {
      for (ConsumerState consumerState : sessionState.getConsumerStates()) {
         consumerState.getInfo().setLastDeliveredSequenceId(lastDeliveredSequenceId);
      }
   }

   private boolean tempDestinationExists(String name) {
      boolean result = false;

      for (DestinationInfo destinationInfo : state.getTempDestinations()) {
         if (destinationInfo.getDestination().getPhysicalName().equals(name)) {
            result = true;
            break;
         }
      }

      return result;
   }

   CommandProcessor commandProcessorInstance = new CommandProcessor();

   // This will listen for commands through the protocolmanager
   public class CommandProcessor implements CommandVisitor {

      public AMQConnectionContext getContext() {
         return OpenWireConnection.this.getContext();
      }

      @Override
      public Response processAddConnection(ConnectionInfo info) throws Exception {
         try {
            protocolManager.validateUser(OpenWireConnection.this, info);
            if (transportConnection.getRouter() != null) {
               if (protocolManager.getRoutingHandler().route(OpenWireConnection.this, info)) {
                  shutdown(true);
                  return null;
               }
            }

            // In certain circumstances (e.g. JCA use-cases with the ActiveMQ RA) an OpenWire client can "clean up" a
            // connection by sending a RemoveInfo command (which will "destroy" this broker-side connection) followed
            // shortly by a ConnectionInfo command. In order to deal with this we "undestroy" the broker-side connection
            // here. If we do not "undestroy" the connection here then creating the internal Core session will fail.
            // See org.apache.activemq.ConnectionCleanupTest#testChangeClientID for a test that executes this scenario.
            destroyed = false;

            protocolManager.addConnection(OpenWireConnection.this, info);
         } catch (Exception e) {
            Response resp = new ExceptionResponse(e);
            return resp;
         }
         if (info.isManageable() && protocolManager.isUpdateClusterClients()) {
            // send ConnectionCommand
            ConnectionControl command = protocolManager.newConnectionControl();
            command.setFaultTolerant(protocolManager.isFaultTolerantConfiguration());
            if (info.isFailoverReconnect()) {
               command.setRebalanceConnection(false);
            }
            dispatchAsync(command);
         }
         return null;

      }

      @Override
      public Response processBrokerSubscriptionInfo(BrokerSubscriptionInfo brokerSubscriptionInfo) throws Exception {
         // TBD
         return null;
      }

      @Override
      public Response processAddProducer(ProducerInfo info) throws Exception {
         SessionId sessionId = info.getProducerId().getParentId();
         ConnectionState cs = getState();

         if (cs == null) {
            throw new IllegalStateException("Cannot add a producer to a connection that had not been registered: " + sessionId.getParentId());
         }

         SessionState ss = cs.getSessionState(sessionId);
         if (ss == null) {
            throw new IllegalStateException("Cannot add a producer to a session that had not been registered: " + sessionId);
         }

         // Avoid replaying dup commands
         if (!ss.getProducerIds().contains(info.getProducerId())) {
            final ActiveMQDestination destination = info.getDestination();
            final AMQSession session = getSession(info.getProducerId().getParentId());

            if (destination != null) {
               session.checkDestinationForSendPermission(destination);

               if (!AdvisorySupport.isAdvisoryTopic(destination)) {
                  OpenWireConnection.this.addDestination(new DestinationInfo(getContext().getConnectionId(), DestinationInfo.ADD_OPERATION_TYPE, destination));
               }
            }

            ss.addProducer(info);
            session.getCoreSession().addProducer(info.getProducerId().toString(),
                                                 OpenWireProtocolManagerFactory.OPENWIRE_PROTOCOL_NAME,
                                                 info.getDestination() != null ? info.getDestination().getPhysicalName() : null);
         }
         return null;
      }

      @Override
      public Response processAddConsumer(ConsumerInfo info) throws Exception {
         addConsumer(info);
         return null;
      }

      @Override
      public Response processRemoveDestination(DestinationInfo info) throws Exception {
         ActiveMQDestination dest = info.getDestination();
         removeDestination(dest);
         return null;
      }

      @Override
      public Response processRemoveProducer(ProducerId id) throws Exception {
         ConnectionState cs = getState();
         if (cs != null) {
            SessionState ss = cs.getSessionState(id.getParentId());
            if (ss != null) {
               ss.removeProducer(id);
            }
         }
         synchronized (producerExchanges) {
            producerExchanges.remove(id);
            getSession(id.getParentId()).getCoreSession().removeProducer(id.toString());
         }
         return null;
      }

      @Override
      public Response processRemoveSession(SessionId id, long lastDeliveredSequenceId) throws Exception {
         SessionState session = state.getSessionState(id);
         if (session == null) {
            throw new IllegalStateException("Cannot remove session that had not been registered: " + id);
         }
         // Don't let new consumers or producers get added while we are closing
         // this down.
         session.shutdown();

         for (ProducerId producerId : session.getProducerIds()) {
            processRemoveProducer(producerId);
         }

         for (ConsumerId consumerId : session.getConsumerIds()) {
            processRemoveConsumer(consumerId, lastDeliveredSequenceId);
         }

         state.removeSession(id);
         propagateLastSequenceId(session, lastDeliveredSequenceId);
         removeSession(context, session.getInfo());
         return null;
      }

      @Override
      public Response processRemoveSubscription(RemoveSubscriptionInfo subInfo) throws Exception {
         SimpleString subQueueName = org.apache.activemq.artemis.jms.client.ActiveMQDestination.createQueueNameForSubscription(true, subInfo.getClientId(), subInfo.getSubscriptionName());
         server.destroyQueue(subQueueName);

         return null;
      }

      @Override
      public Response processRollbackTransaction(TransactionInfo info) throws Exception {
         Transaction tx = lookupTX(info.getTransactionId(), null);

         if (tx == null) {
            throw new IllegalStateException("Transaction not started, " + info.getTransactionId());
         }

         final AMQSession amqSession;
         if (tx != null) {
            amqSession = (AMQSession) tx.getProtocolData();
         } else {
            amqSession = null;
         }

         if (info.getTransactionId().isXATransaction() && tx == null) {
            throw newXAException("Transaction '" + info.getTransactionId() + "' has not been started.", XAException.XAER_NOTA);
         } else if (tx != null) {

            if (amqSession != null) {
               amqSession.getCoreSession().resetTX(tx);

               tx.addOperation(new TransactionOperationAbstract() {
                  @Override
                  public void beforeRollback(Transaction tx) throws Exception {
                     returnReferences(tx, amqSession);
                  }
               });
            }
         }

         if (info.getTransactionId().isXATransaction()) {
            ResourceManager resourceManager = server.getResourceManager();
            Xid xid = OpenWireUtil.toXID(info.getTransactionId());

            if (tx == null) {
               if (resourceManager.getHeuristicCommittedTransactions().contains(xid)) {
                  XAException ex = new XAException("transaction has been heuristically committed: " + xid);
                  ex.errorCode = XAException.XA_HEURCOM;
                  throw ex;
               } else if (resourceManager.getHeuristicRolledbackTransactions().contains(xid)) {
                  // checked heuristic rolled back transactions
                  XAException ex = new XAException("transaction has been heuristically rolled back: " + xid);
                  ex.errorCode = XAException.XA_HEURRB;
                  throw ex;
               } else {
                  logger.trace("xarollback into {}, xid={} forcing a rollback regular", tx, xid);

                  try {
                     if (amqSession != null) {
                        amqSession.getCoreSession().rollback(false);
                     }
                  } catch (Exception e) {
                     logger.warn(e.getMessage(), e);
                  }

                  XAException ex = new XAException("Cannot find xid in resource manager: " + xid);
                  ex.errorCode = XAException.XAER_NOTA;
                  throw ex;
               }
            } else {
               if (tx.getState() == Transaction.State.SUSPENDED) {
                  logger.trace("xarollback into {} sending tx back as it was suspended", tx);

                  // Put it back
                  resourceManager.putTransaction(xid, tx, OpenWireConnection.this);
                  XAException ex = new XAException("Cannot commit transaction, it is suspended " + xid);
                  ex.errorCode = XAException.XAER_PROTO;
                  throw ex;
               } else {
                  tx.rollback();
               }
            }
         } else {
            if (tx != null) {
               // returnReferences() is only interested in acked messages already in the tx, hence we bypass
               // getCoreSession().rollback() logic for CORE which would add any prefetched/delivered which have
               // not yet been processed by the openwire client.
               tx.rollback();
            }
         }

         return null;
      }

      /**
       * Openwire will redeliver rolled back references. We need to return those here.
       */
      private void returnReferences(Transaction tx, AMQSession session) throws Exception {
         if (session == null || session.isClosed()) {
            return;
         }

         RefsOperation oper = (RefsOperation) tx.getProperty(TransactionPropertyIndexes.REFS_OPERATION);

         if (oper != null) {
            List<MessageReference> ackRefs = oper.getReferencesToAcknowledge();

            for (ListIterator<MessageReference> referenceIterator = ackRefs.listIterator(ackRefs.size()); referenceIterator.hasPrevious(); ) {
               MessageReference ref = referenceIterator.previous();

               ServerConsumer consumer = null;
               if (ref.hasConsumerId()) {
                  consumer = session.getCoreSession().locateConsumer(ref.getConsumerId());
               }

               if (consumer != null) {
                  referenceIterator.remove();
                  ref.incrementDeliveryCount();
                  consumer.backToDelivering(ref);
                  final AMQConsumer amqConsumer = (AMQConsumer) consumer.getProtocolData();
                  amqConsumer.addRolledback(ref);
               }
            }
         }
      }

      @Override
      public Response processShutdown(ShutdownInfo info) throws Exception {
         OpenWireConnection.this.shutdown(false);
         return null;
      }

      @Override
      public Response processWireFormat(WireFormatInfo command) throws Exception {
         inWireFormat.renegotiateWireFormat(command);
         outWireFormat.renegotiateWireFormat(command);
         //throw back a brokerInfo here
         protocolManager.sendBrokerInfo(OpenWireConnection.this);
         protocolManager.configureInactivityParams(OpenWireConnection.this, command);
         return null;
      }

      @Override
      public Response processAddDestination(DestinationInfo dest) throws Exception {
         Response resp = null;
         try {
            addDestination(dest);
         } catch (Exception e) {
            if (e instanceof ActiveMQSecurityException) {
               resp = new ExceptionResponse(new JMSSecurityException(e.getMessage()));
            } else {
               resp = new ExceptionResponse(e);
            }
         }
         return resp;
      }

      @Override
      public Response processAddSession(SessionInfo info) throws Exception {
         // Avoid replaying dup commands
         if (state != null && !state.getSessionIds().contains(info.getSessionId())) {
            addSession(info);
            state.addSession(info);
         }
         return null;
      }

      @Override
      public Response processBeginTransaction(TransactionInfo info) throws Exception {
         final TransactionId txID = info.getTransactionId();
         try {
            internalSession.resetTX(null);
            if (txID.isXATransaction()) {
               final Xid xid = OpenWireUtil.toXID(txID);
               internalSession.xaStart(xid);
               final ResourceManager resourceManager = server.getResourceManager();
               final Transaction transaction = resourceManager.getTransaction(xid);
               transaction.addOperation(new TransactionOperationAbstract() {
                  @Override
                  public void afterCommit(Transaction tx) {
                     removeFromResourceManager();
                  }

                  @Override
                  public void afterRollback(Transaction tx) {
                     removeFromResourceManager();
                  }

                  private void removeFromResourceManager() {
                     try {
                        resourceManager.removeTransaction(xid, getRemotingConnection());
                     } catch (ActiveMQException bestEffort) {
                     }
                  }
               });
            } else {
               final Transaction transaction = internalSession.newTransaction();
               txMap.put(txID, transaction);
               transaction.addOperation(new TransactionOperationAbstract() {
                  @Override
                  public void afterCommit(Transaction tx) {
                     removeFromTxMap();
                  }

                  @Override
                  public void afterRollback(Transaction tx) {
                     removeFromTxMap();
                  }

                  private void removeFromTxMap() {
                     txMap.remove(txID);
                  }
               });
            }
         } finally {
            internalSession.resetTX(null);
         }
         return null;
      }

      @Override
      public Response processCommitTransactionOnePhase(TransactionInfo info) throws Exception {
         return processCommit(info, true);
      }

      private Response processCommit(TransactionInfo info, boolean onePhase) throws Exception {
         TransactionId txID = info.getTransactionId();

         Transaction tx = lookupTX(txID, null);

         if (tx == null) {
            throw new IllegalStateException("Transaction not started, " + txID);
         }

         if (txID.isXATransaction()) {
            ResourceManager resourceManager = server.getResourceManager();
            Xid xid = OpenWireUtil.toXID(txID);

            logger.trace("XAcommit into {}, xid={}", tx, xid);

            if (tx == null) {
               if (resourceManager.getHeuristicCommittedTransactions().contains(xid)) {
                  XAException ex = new XAException("transaction has been heuristically committed: " + xid);
                  ex.errorCode = XAException.XA_HEURCOM;
                  throw ex;
               } else if (resourceManager.getHeuristicRolledbackTransactions().contains(xid)) {
                  // checked heuristic rolled back transactions
                  XAException ex = new XAException("transaction has been heuristically rolled back: " + xid);
                  ex.errorCode = XAException.XA_HEURRB;
                  throw ex;
               } else {
                  logger.trace("XAcommit into {}, xid={} cannot find it", tx, xid);
                  XAException ex = new XAException("Cannot find xid in resource manager: " + xid);
                  ex.errorCode = XAException.XAER_NOTA;
                  throw ex;
               }
            } else {
               if (tx.getState() == Transaction.State.SUSPENDED) {
                  // Put it back
                  resourceManager.putTransaction(xid, tx, OpenWireConnection.this);
                  XAException ex = new XAException("Cannot commit transaction, it is suspended " + xid);
                  ex.errorCode = XAException.XAER_PROTO;
                  throw ex;
               } else {
                  tx.commit(onePhase);
               }
            }
         } else {
            if (tx != null) {
               AMQSession amqSession = (AMQSession) tx.getProtocolData();
               if (amqSession != null) {
                  amqSession.getCoreSession().resetTX(tx);
                  amqSession.getCoreSession().commit();
               } else {
                  tx.commit(true);
               }
            }
         }

         return null;
      }

      @Override
      public Response processCommitTransactionTwoPhase(TransactionInfo info) throws Exception {
         return processCommit(info, false);
      }

      @Override
      public Response processForgetTransaction(TransactionInfo info) throws Exception {
         TransactionId txID = info.getTransactionId();

         if (txID.isXATransaction()) {
            try {
               Xid xid = OpenWireUtil.toXID(info.getTransactionId());
               internalSession.xaForget(xid);
            } catch (Exception e) {
               logger.warn("Error during method invocation", e);
               throw e;
            }
         } else {
            txMap.remove(txID);
         }

         return null;
      }

      @Override
      public Response processPrepareTransaction(TransactionInfo info) throws Exception {
         TransactionId txID = info.getTransactionId();

         try {
            if (txID.isXATransaction()) {
               try {
                  Xid xid = OpenWireUtil.toXID(info.getTransactionId());
                  internalSession.xaPrepare(xid);
               } catch (Exception e) {
                  logger.warn("Error during method invocation", e);
                  throw e;
               }
            } else {
               Transaction tx = lookupTX(txID, null);
               tx.prepare();
            }
         } finally {
            internalSession.resetTX(null);
         }

         return new IntegerResponse(XAResource.XA_OK);
      }

      @Override
      public Response processEndTransaction(TransactionInfo info) throws Exception {
         TransactionId txID = info.getTransactionId();

         if (txID.isXATransaction()) {
            try {
               Transaction tx = lookupTX(txID, null);
               internalSession.resetTX(tx);
               try {
                  Xid xid = OpenWireUtil.toXID(info.getTransactionId());
                  internalSession.xaEnd(xid);
               } finally {
                  internalSession.resetTX(null);
               }
            } catch (Exception e) {
               logger.warn("Error during method invocation", e);
               throw e;
            }
         }

         return null;
      }

      @Override
      public Response processBrokerInfo(BrokerInfo arg0) throws Exception {
         throw new IllegalStateException("not implemented! ");
      }

      @Override
      public Response processConnectionControl(ConnectionControl connectionControl) throws Exception {
         //activemq5 keeps a var to remember only the faultTolerant flag
         //this can be sent over a reconnected transport as the first command
         //before restoring the connection.
         return null;
      }

      @Override
      public Response processConnectionError(ConnectionError arg0) throws Exception {
         throw new IllegalStateException("not implemented! ");
      }

      @Override
      public Response processConsumerControl(ConsumerControl consumerControl) throws Exception {
         //amq5 clients send this command to restore prefetchSize
         //after successful reconnect
         try {
            updateConsumer(consumerControl);
         } catch (Exception e) {
            //log error
         }
         return null;
      }

      @Override
      public Response processControlCommand(ControlCommand arg0) throws Exception {
         throw new IllegalStateException("not implemented! ");
      }

      @Override
      public Response processFlush(FlushCommand arg0) throws Exception {
         throw new IllegalStateException("not implemented! ");
      }

      @Override
      public Response processKeepAlive(KeepAliveInfo arg0) throws Exception {
         throw new IllegalStateException("not implemented! ");
      }

      @Override
      public Response processMessage(Message messageSend) throws Exception {
         ProducerId producerId = messageSend.getProducerId();
         AMQProducerBrokerExchange producerExchange = getProducerBrokerExchange(producerId);
         final AMQConnectionContext pcontext = producerExchange.getConnectionContext();
         final ProducerInfo producerInfo = producerExchange.getProducerState().getInfo();
         boolean sendProducerAck = !messageSend.isResponseRequired() && producerInfo.getWindowSize() > 0 && !pcontext.isInRecoveryMode();

         AMQSession session = getSession(producerId.getParentId());

         Transaction tx = lookupTX(messageSend.getTransactionId(), session);

         if (messageSend.getTransactionId() != null && tx == null) {
            throw new IllegalStateException("Transaction not started, " + messageSend.getTransactionId());
         }

         session.getCoreSession().resetTX(tx);
         try {
            session.send(producerInfo, messageSend, sendProducerAck);
         } catch (Exception e) {
            if (tx != null) {
               tx.markAsRollbackOnly(new ActiveMQException(e.getMessage()));
            } else if (e instanceof ActiveMQNonExistentQueueException && producerInfo.getDestination() == null) {
               //Send exception for non transacted anonymous producers using an incorrect destination
               sendException(e);
            }
            throw e;
         }

         return null;
      }

      @Override
      public Response processMessageAck(MessageAck ack) throws Exception {
         AMQSession session = getSession(ack.getConsumerId().getParentId());
         Transaction tx = lookupTX(ack.getTransactionId(), session);

         if (ack.getTransactionId() != null && tx == null) {
            throw new IllegalStateException("Transaction not started, " + ack.getTransactionId());
         }

         session.getCoreSession().resetTX(tx);

         try {
            AMQConsumerBrokerExchange consumerBrokerExchange = consumerExchanges.get(ack.getConsumerId());
            consumerBrokerExchange.acknowledge(ack);
         } catch (Exception e) {
            if (tx != null) {
               tx.markAsRollbackOnly(new ActiveMQException(e.getMessage()));
            }
         }
         return null;
      }

      @Override
      public Response processMessageDispatch(MessageDispatch arg0) throws Exception {
         return null;
      }

      @Override
      public Response processMessageDispatchNotification(MessageDispatchNotification arg0) throws Exception {
         return null;
      }

      @Override
      public Response processMessagePull(MessagePull arg0) throws Exception {
         AMQConsumerBrokerExchange amqConsumerBrokerExchange = consumerExchanges.get(arg0.getConsumerId());
         if (amqConsumerBrokerExchange == null) {
            throw new IllegalStateException("Consumer does not exist");
         }
         amqConsumerBrokerExchange.processMessagePull(arg0);
         return null;
      }

      @Override
      public Response processProducerAck(ProducerAck arg0) throws Exception {
         // a broker doesn't do producers.. this shouldn't happen
         return null;
      }

      @Override
      public Response processRecoverTransactions(TransactionInfo info) throws Exception {
         List<Xid> xids = server.getResourceManager().getInDoubtTransactions();
         List<TransactionId> recovered = new ArrayList<>();
         for (Xid xid : xids) {
            XATransactionId amqXid = new XATransactionId(xid);
            recovered.add(amqXid);
         }
         return new DataArrayResponse(recovered.toArray(new TransactionId[recovered.size()]));
      }

      @Override
      public Response processRemoveConnection(ConnectionId id, long lastDeliveredSequenceId) throws Exception {
         //we let protocol manager to handle connection add/remove
         for (SessionState sessionState : state.getSessionStates()) {
            propagateLastSequenceId(sessionState, lastDeliveredSequenceId);
         }
         try {
            protocolManager.removeConnection(getClientID(), OpenWireConnection.this);
         } finally {
            OpenWireConnection.this.disconnect(false);
         }
         return null;
      }

      @Override
      public Response processRemoveConsumer(ConsumerId id, long lastDeliveredSequenceId) throws Exception {
         if (destroyed) {
            return null;
         }
         SessionId sessionId = id.getParentId();
         SessionState ss = state.getSessionState(sessionId);
         if (ss == null) {
            throw new IllegalStateException("Cannot remove a consumer from a session that had not been registered: " + sessionId);
         }
         ConsumerState consumerState = ss.removeConsumer(id);
         if (consumerState == null) {
            throw new IllegalStateException("Cannot remove a consumer that had not been registered: " + id);
         }
         ConsumerInfo info = consumerState.getInfo();
         info.setLastDeliveredSequenceId(lastDeliveredSequenceId);

         AMQConsumerBrokerExchange consumerBrokerExchange = consumerExchanges.remove(id);

         consumerBrokerExchange.removeConsumer();

         return null;
      }

   }

   private void   recoverOperationContext() {
      server.getStorageManager().setContext(this.operationContext);
   }

   private void clearupOperationContext() {
      if (server != null && server.getStorageManager() != null) {
         server.getStorageManager().clearContext();
      }
   }

   private Transaction lookupTX(TransactionId txID, AMQSession session) throws Exception {
      if (txID == null) {
         return null;
      }

      Transaction transaction;
      if (txID.isXATransaction()) {
         final Xid xid = OpenWireUtil.toXID(txID);
         transaction = server.getResourceManager().getTransaction(xid);
      } else {
         transaction = txMap.get(txID);
      }

      if (transaction == null) {
         return null;
      }

      if (session != null && transaction.getProtocolData() != session) {
         transaction.setProtocolData(session);
      }

      return transaction;
   }

   public static XAException newXAException(String s, int errorCode) {
      XAException xaException = new XAException(s + " " + "xaErrorCode:" + errorCode);
      xaException.errorCode = errorCode;
      return xaException;
   }

   @Override
   public boolean isSupportsFlowControl() {
      return true;
   }

   @Override
   public String getProtocolName() {
      return OpenWireProtocolManagerFactory.OPENWIRE_PROTOCOL_NAME;
   }

   @Override
   public String getClientID() {
      return context != null ? context.getClientId() : null;
   }

   public CoreMessageObjectPools getCoreMessageObjectPools() {
      return coreMessageObjectPools;
   }

   public void logCommand(Command command, boolean in) {
      if (logger.isTraceEnabled()) {
         StringBuilder message = new StringBuilder()
            .append("OpenWire(")
            .append(getRemoteAddress())
            .append(", ")
            .append(this.getID())
            .append("):");

         if (in) {
            message.append(" IN << ");
         } else {
            message.append("OUT >> ");
         }

         message.append((Objects.requireNonNullElse(command, "NULL")));

         logger.trace(message.toString());
      }
   }
}
