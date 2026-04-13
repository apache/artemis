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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import org.apache.activemq.artemis.api.core.ActiveMQAddressDoesNotExistException;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException;
import org.apache.activemq.artemis.api.core.AutoCreateResult;
import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.protocol.stomp.v10.StompFrameHandlerV10;
import org.apache.activemq.artemis.core.protocol.stomp.v12.StompFrameHandlerV12;
import org.apache.activemq.artemis.core.remoting.FailureListener;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.selector.filter.FilterException;
import org.apache.activemq.artemis.selector.impl.SelectorParser;
import org.apache.activemq.artemis.spi.core.protocol.AbstractRemotingConnection;
import org.apache.activemq.artemis.spi.core.remoting.Acceptor;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.apache.activemq.artemis.spi.core.remoting.ReadyListener;
import org.apache.activemq.artemis.utils.ConfigurationHelper;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.apache.activemq.artemis.utils.VersionLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.activemq.artemis.core.protocol.stomp.ActiveMQStompProtocolMessageBundle.BUNDLE;
import static org.apache.activemq.artemis.reader.MessageUtil.CONNECTION_ID_PROPERTY_NAME_STRING;

public final class StompConnection extends AbstractRemotingConnection {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final String SERVER_NAME = "ActiveMQ-Artemis/" + VersionLoader.getVersion().getFullVersion() +
      " ActiveMQ Artemis Messaging Engine";

   private final StompProtocolManager manager;

   private String login;

   private String passcode;

   //this means login is valid. (stomp connection ok)
   private boolean valid;

   private static final AtomicIntegerFieldUpdater<StompConnection> DESTROYED_UPDATER = AtomicIntegerFieldUpdater.newUpdater(StompConnection.class, "destroyed");
   private volatile int destroyed;

   private final Acceptor acceptorUsed;

   private final boolean enableMessageID;

   private final int minLargeMessageSize;

   private StompVersions version;

   private VersionedStompFrameHandler frameHandler;

   //this means the version negotiation done.
   private boolean initialized;

   private FrameEventListener stompListener;

   private final Object sendLock = new Object();

   private final ScheduledExecutorService scheduledExecutorService;

   private final ExecutorFactory executorFactory;

   public VersionedStompFrameHandler getStompVersionHandler() {
      return frameHandler;
   }


   public StompFrame decode(ActiveMQBuffer buffer) throws ActiveMQStompException {
      StompFrame frame = null;
      try {
         frame = frameHandler.decode(buffer);
      } catch (ActiveMQStompException e) {
         switch (e.getCode()) {
            case ActiveMQStompException.INVALID_EOL_V10:
               if (version != null) {
                  throw e;
               }
               frameHandler = new StompFrameHandlerV12(this, scheduledExecutorService, executorFactory);
               buffer.resetReaderIndex();
               frame = decode(buffer);
               break;
            case ActiveMQStompException.INVALID_COMMAND:
            case ActiveMQStompException.UNDEFINED_ESCAPE:
               frameHandler.onError(e);
               break;
            default:
               throw e;
         }
      }
      return frame;
   }

   @Override
   public boolean isWritable(ReadyListener callback) {
      return transportConnection.isWritable(callback) && transportConnection.isOpen();
   }

   public boolean hasBytes() {
      return frameHandler.hasBytes();
   }

   StompConnection(final Acceptor acceptorUsed,
                   final Connection transportConnection,
                   final StompProtocolManager manager,
                   final ScheduledExecutorService scheduledExecutorService,
                   final ExecutorFactory executorFactory) {
      super(transportConnection, null);

      this.scheduledExecutorService = scheduledExecutorService;

      this.executorFactory = executorFactory;

      this.manager = manager;

      this.frameHandler = new StompFrameHandlerV10(this, scheduledExecutorService, executorFactory);

      this.acceptorUsed = acceptorUsed;

      this.enableMessageID = ConfigurationHelper.getBooleanProperty(TransportConstants.STOMP_ENABLE_MESSAGE_ID_DEPRECATED, false, acceptorUsed.getConfiguration()) || ConfigurationHelper.getBooleanProperty(TransportConstants.STOMP_ENABLE_MESSAGE_ID, false, acceptorUsed.getConfiguration());
      this.minLargeMessageSize = ConfigurationHelper.getIntProperty(TransportConstants.STOMP_MIN_LARGE_MESSAGE_SIZE, ConfigurationHelper.getIntProperty(TransportConstants.STOMP_MIN_LARGE_MESSAGE_SIZE_DEPRECATED, ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE, acceptorUsed.getConfiguration()), acceptorUsed.getConfiguration());
   }

   @Override
   public void destroy() {
      if (DESTROYED_UPDATER.compareAndSet(this, 0, 1)) {
         internalClose();
      }
   }

   public Acceptor getAcceptorUsed() {
      return acceptorUsed;
   }

   private void internalClose() {
      if (frameHandler != null) {
         frameHandler.disconnect();
      }

      transportConnection.close();

      manager.cleanup(this);

      synchronized (sendLock) {
         callClosingListeners();
      }
   }

   @Override
   public void fail(final ActiveMQException me) {
      if (DESTROYED_UPDATER.compareAndSet(this, 0, 1)) {
         StompFrame frame = frameHandler.createStompFrame(Stomp.Responses.ERROR);
         frame.addHeader(Stomp.Headers.Error.MESSAGE, me.getMessage());
         sendFrame(frame, null);
         ActiveMQServerLogger.LOGGER.connectionFailureDetected(me.getMessage(), me.getType());

         // Then call the listeners
         callFailureListeners(me);

         internalClose();
      }
   }

   @Override
   public Future asyncFail(ActiveMQException me) {

      FutureTask<Void> task = new FutureTask(() -> {
         fail(me);
         return null;
      });

      if (this.executorFactory == null) {
         // only tests cases can do this
         task.run();
      } else {
         executorFactory.getExecutor().execute(task);
      }

      return task;
   }

   @Override
   public void fail(final ActiveMQException me, String scaleDownTargetNodeID) {
      fail(me);
   }

   @Override
   public List<FailureListener> getFailureListeners() {
      // we do not return the listeners otherwise the remoting service
      // would NOT destroy the connection.
      return Collections.emptyList();
   }

   @Override
   public void bufferReceived(Object connectionID, ActiveMQBuffer buffer) {
      super.bufferReceived(connectionID, buffer);
      manager.handleBuffer(this, buffer);
   }

   public String getLogin() {
      return login;
   }

   public void setLogin(String login) {
      this.login = login;
   }

   public String getPasscode() {
      return passcode;
   }

   public void setPasscode(String passcode) {
      this.passcode = passcode;
   }

   public boolean isValid() {
      return valid;
   }

   public void setValid(boolean valid) {
      this.valid = valid;
   }

   private void callFailureListeners(final ActiveMQException me) {
      final List<FailureListener> listenersClone = new ArrayList<>(failureListeners);

      for (final FailureListener listener : listenersClone) {
         try {
            listener.connectionFailed(me, false);
         } catch (final Throwable t) {
            // Failure of one listener to execute shouldn't prevent others from executing
            ActiveMQServerLogger.LOGGER.errorCallingFailureListener(t);
         }
      }
   }

   /*
    * accept-version value takes form of "v1,v2,v3..."
    * we need to return the highest supported version
    */
   public void negotiateVersion(StompFrame frame) throws ActiveMQStompException {
      String acceptVersion = frame.getHeader(Stomp.Headers.ACCEPT_VERSION);

      if (acceptVersion == null) {
         this.version = StompVersions.V1_0;
      } else {
         StringTokenizer tokenizer = new StringTokenizer(acceptVersion, ",");
         Set<String> requestVersions = new HashSet<>(tokenizer.countTokens());
         while (tokenizer.hasMoreTokens()) {
            requestVersions.add(tokenizer.nextToken());
         }

         if (requestVersions.contains(StompVersions.V1_2.toString())) {
            this.version = StompVersions.V1_2;
         } else if (requestVersions.contains(StompVersions.V1_1.toString())) {
            this.version = StompVersions.V1_1;
         } else if (requestVersions.contains(StompVersions.V1_0.toString())) {
            this.version = StompVersions.V1_0;
         } else {
            //not a supported version!
            ActiveMQStompException error = BUNDLE.versionNotSupported(acceptVersion).setHandler(frameHandler);
            error.addHeader(Stomp.Headers.Error.VERSION, manager.getSupportedVersionsAsErrorVersion());
            error.addHeader(Stomp.Headers.CONTENT_TYPE, "text/plain");
            error.setBody("Supported protocol versions are " + manager.getSupportedVersionsAsString());
            error.setDisconnect(true);
            throw error;
         }
      }

      if (this.version != (StompVersions.V1_0)) {
         VersionedStompFrameHandler newHandler = VersionedStompFrameHandler.getHandler(this, this.version, scheduledExecutorService, executorFactory);
         newHandler.initDecoder(this.frameHandler);
         this.frameHandler = newHandler;
      }
      this.initialized = true;
   }

   //reject if the host doesn't match
   public void setHost(String host) throws ActiveMQStompException {
      if (host == null) {
         ActiveMQStompException error = BUNDLE.nullHostHeader().setHandler(frameHandler);
         error.setBody(BUNDLE.hostCannotBeNull());
         throw error;
      }

      String localHost = manager.getVirtualHostName();
      if (!host.equals(localHost)) {
         ActiveMQStompException error = BUNDLE.hostNotMatch().setHandler(frameHandler);
         error.setBody(BUNDLE.hostNotMatchDetails(host));
         throw error;
      }
   }

   public void handleFrame(StompFrame request) {
      StompFrame reply = null;

      if (stompListener != null) {
         stompListener.requestAccepted(request);
      }

      String cmd = request.getCommand();
      try {
         if (isDestroyed()) {
            throw BUNDLE.connectionDestroyed().setHandler(frameHandler);
         }
         if (!initialized) {
            if (!(Stomp.Commands.CONNECT.equals(cmd) || Stomp.Commands.STOMP.equals(cmd))) {
               throw BUNDLE.connectionNotEstablished().setHandler(frameHandler);
            }
            //decide version
            negotiateVersion(request);
         }

         reply = frameHandler.handleFrame(request);
      } catch (ActiveMQStompException e) {
         reply = e.getFrame();
      }

      if (reply != null) {
         sendFrame(reply, null);
      }

      if (Stomp.Commands.DISCONNECT.equals(cmd)) {
         this.disconnect(false);
      }
   }

   public void logFrame(StompFrame request, boolean in) {
      if (logger.isDebugEnabled()) {
         StringBuilder message = new StringBuilder()
            .append("STOMP(")
            .append(getRemoteAddress())
            .append(", ")
            .append(this.getID())
            .append("):");

         if (in) {
            message.append(" IN << ");
         } else {
            message.append("OUT >> ");
         }

         message.append(request);

         logger.debug(message.toString());
      }
   }

   public void sendFrame(StompFrame frame, StompPostReceiptFunction function) {
      manager.sendReply(this, frame, function);
   }

   public CoreMessage createServerMessage() {
      return manager.createServerMessage();
   }

   public StompSession getSession() throws ActiveMQStompException, ActiveMQSecurityException {
      return getSession(null);
   }

   public StompSession getSession(String txID) throws ActiveMQStompException, ActiveMQSecurityException {
      StompSession session = null;
      try {
         if (txID == null) {
            session = manager.getSession(this);
         } else {
            session = manager.getTransactedSession(this, txID);
         }
      } catch (ActiveMQSecurityException e) {
         throw e;
      } catch (Exception e) {
         throw BUNDLE.errorGetSession(e).setHandler(frameHandler);
      }

      return session;
   }

   protected void validate() throws ActiveMQStompException {
      if (!this.valid) {
         throw BUNDLE.invalidConnection().setHandler(frameHandler);
      }
   }

   protected void sendServerMessage(ICoreMessage message, String txID) throws ActiveMQStompException {
      try {
         StompSession stompSession = getSession(txID);

         // only set the connection ID property if we have a noLocal subscription
         if (stompSession.getNoLocalSubscriptionCount() > 0) {
            message.putStringProperty(CONNECTION_ID_PROPERTY_NAME_STRING, getID().toString());
         }
         if (isEnableMessageID()) {
            message.putStringProperty("amqMessageId", "STOMP" + message.getMessageID());
         }
         if (minLargeMessageSize == -1 || (message.getBodyBuffer().writerIndex() < minLargeMessageSize)) {
            stompSession.sendInternal(message, false);
         } else {
            stompSession.sendInternalLarge((CoreMessage)message, false);
         }
      } catch (Exception e) {
         throw BUNDLE.errorSendMessage(message, e).setHandler(frameHandler);
      }
   }

   @Override
   public void disconnect(final boolean criticalError) {
      disconnect(null, criticalError);
   }

   @Override
   public void disconnect(String scaleDownNodeID, final boolean criticalError) {
      destroy();
   }

   protected void beginTransaction(String txID) throws ActiveMQStompException {
      try {
         manager.beginTransaction(this, txID);
      } catch (ActiveMQStompException e) {
         throw e;
      } catch (Exception e) {
         throw BUNDLE.errorBeginTx(txID, e).setHandler(frameHandler);
      }
   }

   public void commitTransaction(String txID) throws ActiveMQStompException {
      try {
         manager.commitTransaction(this, txID);
      } catch (Exception e) {
         throw BUNDLE.errorCommitTx(txID, e).setHandler(frameHandler);
      }
   }

   public void abortTransaction(String txID) throws ActiveMQStompException {
      try {
         manager.abortTransaction(this, txID);
      } catch (ActiveMQStompException e) {
         throw e;
      } catch (Exception e) {
         throw BUNDLE.errorAbortTx(txID, e).setHandler(frameHandler);
      }
   }

   StompPostReceiptFunction subscribe(String destination,
                                      String selector,
                                      String ack,
                                      String id,
                                      String durableSubscriptionName,
                                      boolean noLocal,
                                      RoutingType subscriptionType,
                                      Integer consumerWindowSize) throws ActiveMQStompException {
      validateSelector(selector);
      checkAutoCreate(destination, subscriptionType);
      String subscriptionID = getSubscriptionID(destination, id);

      try {
         return manager.subscribe(this,
                                  subscriptionID,
                                  durableSubscriptionName,
                                  destination,
                                  getSelector(selector, noLocal),
                                  Objects.requireNonNullElse(ack, Stomp.Headers.Subscribe.AckModeValues.AUTO),
                                  noLocal,
                                  consumerWindowSize);
      } catch (ActiveMQStompException e) {
         throw e;
      } catch (Exception e) {
         throw BUNDLE.errorCreatingSubscription(subscriptionID, e).setHandler(frameHandler);
      }
   }

   protected void checkAutoCreate(String destination, RoutingType subscriptionType) throws ActiveMQStompException {
      AutoCreateResult autoCreateResult;
      try {
         RoutingType routingType = getSubscriptionRoutingType(destination, subscriptionType);
         autoCreateResult = getSession().getCoreSession().checkAutoCreate(QueueConfiguration.of(destination).setRoutingType(routingType));
      } catch (Exception e) {
         logger.debug("Exception while auto-creating destination", e);
         throw new ActiveMQStompException(e.getMessage(), e).setHandler(frameHandler);
      }
      if (autoCreateResult == AutoCreateResult.NOT_FOUND) {
         throw BUNDLE.destinationNotExist(destination).setHandler(frameHandler);
      }
   }

   private RoutingType getSubscriptionRoutingType(String destination, RoutingType subscriptionType) {
      if (subscriptionType == null) {
         return getManager().getServer().getAddressSettingsRepository().getMatch(destination).getDefaultAddressRoutingType();
      } else {
         return subscriptionType;
      }
   }

   private String getSelector(String selector, boolean noLocal) {
      if (noLocal) {
         String noLocalFilter = "(" + CONNECTION_ID_PROPERTY_NAME_STRING + " <> '" + getID().toString() + "' OR " + CONNECTION_ID_PROPERTY_NAME_STRING + " IS NULL)";
         if (selector == null) {
            selector = noLocalFilter;
         } else {
            selector = "(" + selector + ") AND " + noLocalFilter;
         }
      }
      return selector;
   }

   private String getSubscriptionID(String destination, String id) throws ActiveMQStompException {
      String subscriptionID = null;
      if (id != null) {
         subscriptionID = id;
      } else {
         if (destination == null) {
            throw BUNDLE.noDestination().setHandler(frameHandler);
         }
         subscriptionID = "subscription/" + destination;
      }
      return subscriptionID;
   }

   private void validateSelector(String selector) throws ActiveMQStompException {
      // user may not specify a selector; that's ok
      if (selector == null) {
         return;
      }
      if (selector.isEmpty()) {
         throw new ActiveMQStompException("Selector cannot be empty").setHandler(frameHandler);
      }
      try {
         SelectorParser.parse(selector);
      } catch (FilterException e) {
         throw new ActiveMQStompException("Invalid selector \"" + selector + "\"", e).setHandler(frameHandler);
      }
   }

   public void unsubscribe(String subscriptionID, String durableSubscriptionName) throws ActiveMQStompException {
      try {
         manager.unsubscribe(this, subscriptionID, durableSubscriptionName);
      } catch (ActiveMQAddressDoesNotExistException e) {
         // this could happen if multiple clients unsubscribe simultaneously and auto-delete-addresses = true
      } catch (ActiveMQStompException e) {
         throw e;
      } catch (Exception e) {
         throw BUNDLE.errorUnsubscribing(subscriptionID, e).setHandler(frameHandler);
      }
   }

   public void acknowledge(String messageID, String subscriptionID) throws ActiveMQStompException {
      try {
         manager.acknowledge(this, messageID, subscriptionID);
      } catch (ActiveMQStompException e) {
         throw e;
      } catch (Exception e) {
         throw BUNDLE.errorAck(messageID, e).setHandler(frameHandler);
      }
   }

   public String getVersion() {
      return String.valueOf(version);
   }

   public String getActiveMQServerName() {
      return SERVER_NAME;
   }

   public StompFrame createStompMessage(ICoreMessage message,
                                        StompSubscription subscription,
                                        ServerConsumer consumer,
                                        int deliveryCount) throws ActiveMQException {
      return frameHandler.createMessageFrame(message, subscription, consumer, deliveryCount);
   }

   public void addStompEventListener(FrameEventListener listener) {
      this.stompListener = listener;
   }

   //send a ping stomp frame
   public void ping(StompFrame pingFrame) {
      manager.sendReply(this, pingFrame, null);
   }

   public void physicalSend(StompFrame frame) throws Exception {
      ActiveMQBuffer buffer = frame.toActiveMQBuffer();
      synchronized (sendLock) {
         getTransportConnection().write(buffer, false, false);
      }

      if (stompListener != null) {
         stompListener.replySent(frame);
      }

      if (frame.getCommand().equals(Stomp.Responses.ERROR)) {
         String message = "no message header";
         if (frame.hasHeader(Stomp.Headers.Error.MESSAGE)) {
            message = frame.getHeader(Stomp.Headers.Error.MESSAGE);
         }
         ActiveMQStompProtocolLogger.LOGGER.sentErrorToClient(getTransportConnection().getRemoteAddress(), message);
      }
   }

   public VersionedStompFrameHandler getFrameHandler() {
      return this.frameHandler;
   }

   public boolean isEnableMessageID() {
      return enableMessageID;
   }

   public int getMinLargeMessageSize() {
      return minLargeMessageSize;
   }

   public StompProtocolManager getManager() {
      return manager;
   }

   @Override
   public String getProtocolName() {
      return StompProtocolManagerFactory.STOMP_PROTOCOL_NAME;
   }
}
