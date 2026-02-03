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
package org.apache.activemq.artemis.protocol.amqp.proton;

import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import io.netty.buffer.ByteBuf;
import io.netty.channel.EventLoop;
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.persistence.OperationContext;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.security.CheckType;
import org.apache.activemq.artemis.core.security.SecurityAuth;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPConnectionCallback;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPSessionCallback;
import org.apache.activemq.artemis.protocol.amqp.broker.ProtonProtocolManager;
import org.apache.activemq.artemis.protocol.amqp.connect.mirror.AMQPMirrorControllerSource;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPException;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPInternalErrorException;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPSecurityException;
import org.apache.activemq.artemis.protocol.amqp.logger.ActiveMQAMQPProtocolLogger;
import org.apache.activemq.artemis.protocol.amqp.logger.ActiveMQAMQPProtocolMessageBundle;
import org.apache.activemq.artemis.protocol.amqp.proton.handler.EventHandler;
import org.apache.activemq.artemis.protocol.amqp.proton.handler.ExtCapability;
import org.apache.activemq.artemis.protocol.amqp.proton.handler.ProtonHandler;
import org.apache.activemq.artemis.protocol.amqp.sasl.AnonymousServerSASL;
import org.apache.activemq.artemis.protocol.amqp.sasl.ClientSASLFactory;
import org.apache.activemq.artemis.protocol.amqp.sasl.PlainSASLResult;
import org.apache.activemq.artemis.protocol.amqp.sasl.SASLResult;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.remoting.ReadyListener;
import org.apache.activemq.artemis.utils.ByteUtil;
import org.apache.activemq.artemis.utils.VersionLoader;
import org.apache.activemq.artemis.utils.collections.ConcurrentHashSet;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.Source;
import org.apache.qpid.proton.amqp.messaging.TerminusExpiryPolicy;
import org.apache.qpid.proton.amqp.transaction.Coordinator;
import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.engine.Connection;
import org.apache.qpid.proton.engine.Delivery;
import org.apache.qpid.proton.engine.EndpointState;
import org.apache.qpid.proton.engine.Link;
import org.apache.qpid.proton.engine.Receiver;
import org.apache.qpid.proton.engine.Sender;
import org.apache.qpid.proton.engine.Session;
import org.apache.qpid.proton.engine.Transport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_ADDRESS_RECEIVER;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_BASE_VALIDATION_ADDRESS;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_CONTROL_LINK;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_EVENT_LINK;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_QUEUE_RECEIVER;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.AMQP_LINK_INITIALIZER_KEY;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.FAILOVER_SERVER_LIST;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.HOSTNAME;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.NETWORK_HOST;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.PORT;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.SCHEME;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.verifyDesiredCapability;

public class AMQPConnectionContext extends ProtonInitializable implements EventHandler {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public void disableAutoRead() {
      handler.requireHandler();
      connectionCallback.getTransportConnection().setAutoRead(false);
      handler.setReadable(false);
   }

   public void enableAutoRead() {
      handler.requireHandler();
      connectionCallback.getTransportConnection().setAutoRead(true);
      getHandler().setReadable(true);
      flush();
   }

   public static final Symbol CONNECTION_OPEN_FAILED = Symbol.valueOf("amqp:connection-establishment-failed");
   public static final String AMQP_CONTAINER_ID = "amqp-container-id";
   private static final FutureTask<Void> VOID_FUTURE = new FutureTask<>(() -> { }, null);

   protected final ProtonHandler handler;

   private AMQPConnectionCallback connectionCallback;
   private final String containerId;
   private final boolean isIncomingConnection;
   private final ClientSASLFactory saslClientFactory;
   private final Map<Symbol, Object> connectionProperties = new HashMap<>();
   private final Symbol[] offeredCapabilities;
   private final Symbol[] desiredCapabilities;
   private final ScheduledExecutorService scheduledPool;
   private final Map<String, LinkCloseListener> linkCloseListeners = new ConcurrentHashMap<>();
   private final Set<Consumer<AMQPConnectionContext>> remoteOpenedListeners = new ConcurrentHashSet<>();

   private final Map<Session, AMQPSessionContext> sessions = new ConcurrentHashMap<>();

   private final ProtonProtocolManager protocolManager;

   private final boolean useCoreSubscriptionNaming;

   private final boolean brokerConnection;

   private final ScheduleOperator scheduleOp = new ScheduleOperator(new ScheduleRunnable());
   private final AtomicReference<Future<?>> scheduledFutureRef = new AtomicReference<>(VOID_FUTURE);

   private String user;
   private String password;
   private String validatedUser;

   public AMQPConnectionContext(ProtonProtocolManager protocolManager,
                                AMQPConnectionCallback connectionSP,
                                String containerId,
                                int idleTimeout,
                                int maxFrameSize,
                                int channelMax,
                                boolean useCoreSubscriptionNaming,
                                ScheduledExecutorService scheduledPool,
                                boolean isIncomingConnection,
                                ClientSASLFactory saslClientFactory,
                                Map<Symbol, Object> connectionProperties,
                                Symbol[] offeredCapabilities,
                                Symbol[] desiredCapabilities) {
      this(protocolManager, connectionSP, containerId, idleTimeout, maxFrameSize, channelMax, useCoreSubscriptionNaming, scheduledPool, isIncomingConnection, saslClientFactory, connectionProperties, offeredCapabilities, desiredCapabilities, false);
   }

   public AMQPConnectionContext(ProtonProtocolManager protocolManager,
                                AMQPConnectionCallback connectionSP,
                                String containerId,
                                int idleTimeout,
                                int maxFrameSize,
                                int channelMax,
                                boolean useCoreSubscriptionNaming,
                                ScheduledExecutorService scheduledPool,
                                boolean isIncomingConnection,
                                ClientSASLFactory saslClientFactory,
                                Map<Symbol, Object> connectionProperties,
                                Symbol[] offeredCapabilities,
                                Symbol[] desiredCapabilities,
                                boolean brokerConnection) {
      this.protocolManager = protocolManager;
      this.brokerConnection = brokerConnection;
      this.connectionCallback = connectionSP;
      this.useCoreSubscriptionNaming = useCoreSubscriptionNaming;
      this.containerId = (containerId != null) ? containerId : UUID.randomUUID().toString();
      this.isIncomingConnection = isIncomingConnection;
      this.saslClientFactory = saslClientFactory;

      this.connectionProperties.put(AmqpSupport.PRODUCT, "apache-activemq-artemis");
      this.connectionProperties.put(AmqpSupport.VERSION, VersionLoader.getVersion().getFullVersion());

      if (connectionProperties != null) {
         this.connectionProperties.putAll(connectionProperties);
      }

      if (offeredCapabilities != null && offeredCapabilities.length > 0) {
         this.offeredCapabilities = Arrays.copyOf(offeredCapabilities, offeredCapabilities.length);
      } else {
         this.offeredCapabilities = null;
      }

      if (desiredCapabilities != null && desiredCapabilities.length > 0) {
         this.desiredCapabilities = Arrays.copyOf(desiredCapabilities, desiredCapabilities.length);
      } else {
         this.desiredCapabilities = null;
      }

      this.scheduledPool = scheduledPool;
      connectionCallback.setConnection(this);
      EventLoop nettyExecutor = connectionCallback.getTransportConnection().getEventLoop();
      this.handler = new ProtonHandler(nettyExecutor, protocolManager.getServer().getExecutorFactory().getExecutor(), isIncomingConnection && saslClientFactory == null);
      handler.addEventHandler(this);
      Transport transport = handler.getTransport();
      transport.setEmitFlowEventOnSend(false);
      if (idleTimeout > 0) {
         transport.setIdleTimeout(idleTimeout);
      }
      transport.setChannelMax(channelMax);
      transport.setInitialRemoteMaxFrameSize(protocolManager.getInitialRemoteMaxFrameSize());
      transport.setMaxFrameSize(maxFrameSize);
      transport.setOutboundFrameSizeLimit(maxFrameSize);
      if (saslClientFactory != null) {
         handler.createClientSASL();
      }
   }

   public boolean isLargeMessageSync() {
      return connectionCallback.isLargeMessageSync();
   }

   @Override
   public void initialize() throws Exception {
      initialized = true;
   }

   /**
    * Adds a listener that will be invoked any time an AMQP link is remotely closed before having been closed on this
    * end of the connection.
    *
    * @param id                A unique ID assigned to the listener used to later remove it if needed.
    * @param linkCloseListener The instance of a closed listener.
    * @return this connection context instance
    */
   public AMQPConnectionContext addLinkRemoteCloseListener(String id, LinkCloseListener linkCloseListener) {
      linkCloseListeners.put(id, linkCloseListener);
      return this;
   }

   /**
    * Remove the link remote close listener that is identified by the given ID.
    *
    * @param id The unique ID assigned to the listener when it was added.
    */
   public void removeLinkRemoteCloseListener(String id) {
      linkCloseListeners.remove(id);
   }

   /**
    * Clear all link remote close listeners, usually done before connection termination to avoid any remote close events
    * triggering processing after the connection shutdown has already started.
    */
   public void clearLinkRemoteCloseListeners() {
      linkCloseListeners.clear();
   }

   public boolean isBrokerConnection() {
      return brokerConnection;
   }

   public void requireInHandler() {
      handler.requireHandler();
   }

   public boolean isHandler() {
      return handler.isHandler();
   }

   public void scheduledFlush() {
      handler.scheduledFlush();
   }

   public boolean isIncomingConnection() {
      return isIncomingConnection;
   }

   public ClientSASLFactory getSaslClientFactory() {
      return saslClientFactory;
   }

   protected AMQPSessionContext newSessionExtension(Session realSession) throws ActiveMQAMQPException {
      AMQPSessionCallback sessionSPI = connectionCallback.createSessionCallback(this);
      AMQPSessionContext protonSession = new AMQPSessionContext(sessionSPI, this, realSession, protocolManager.getServer());

      return protonSession;
   }

   public Map<Session, AMQPSessionContext> getSessions() {
      return sessions;
   }

   public SecurityAuth getSecurityAuth() {
      return new LocalSecurity();
   }

   public SASLResult getSASLResult() {
      return handler.getSASLResult();
   }

   public void inputBuffer(ByteBuf buffer) {
      if (logger.isTraceEnabled()) {
         ByteUtil.debugFrame(logger, "Buffer Received ", buffer);
      }

      handler.inputBuffer(buffer);
   }

   public ProtonHandler getHandler() {
      return handler;
   }

   public String getUser() {
      return user;
   }

   public String getPassword() {
      return password;
   }

   public String getValidatedUser() {
      return validatedUser;
   }

   public void destroy() {
      handler.runNow(() -> connectionCallback.close());
   }

   public boolean isSyncOnFlush() {
      return false;
   }

   public void instantFlush() {
      handler.instantFlush();
   }
   public void flush() {
      handler.flush();
   }

   public void afterFlush(Runnable runnable) {
      handler.afterFlush(runnable);
   }

   public void close(ErrorCondition errorCondition) {
      Future<?> scheduledFuture = scheduledFutureRef.getAndSet(null);

      if (scheduledPool instanceof ThreadPoolExecutor executor && scheduledFuture != null &&
         scheduledFuture != VOID_FUTURE && scheduledFuture instanceof Runnable runnable) {
         if (!executor.remove(runnable) &&
            !scheduledFuture.isCancelled() && !scheduledFuture.isDone()) {
            ActiveMQAMQPProtocolLogger.LOGGER.cantRemovingScheduledTask();
         }
      }

      handler.close(errorCondition, this);
   }

   public AMQPSessionContext getSessionExtension(Session realSession) throws ActiveMQAMQPException {
      AMQPSessionContext sessionExtension = sessions.get(realSession);
      if (sessionExtension == null) {
         sessionExtension = newSessionExtension(realSession);
         realSession.setContext(sessionExtension);
         sessions.put(realSession, sessionExtension);
      }
      return sessionExtension;
   }

   public void runOnPool(Runnable run) {
      handler.runOnPool(run);
   }

   public void runNow(Runnable run) {
      handler.runNow(run);
   }

   public void runLater(Runnable run) {
      handler.runLater(run);
   }

   protected boolean validateConnection(Connection connection) {
      return connectionCallback.validateConnection(connection, handler.getSASLResult());
   }

   public boolean checkDataReceived() {
      return handler.checkDataReceived();
   }

   public long getCreationTime() {
      return handler.getCreationTime();
   }

   public String getRemoteContainer() {
      return handler.getConnection().getRemoteContainer();
   }

   public String getPubSubPrefix() {
      return null;
   }

   protected void initInternal() throws Exception {
   }

   public AMQPConnectionCallback getConnectionCallback() {
      return connectionCallback;
   }

   protected void remoteLinkOpened(Link link) throws Exception {
      final AMQPSessionContext protonSession = getSessionExtension(link.getSession());

      // The operation context is needed in case there are auto-created destinations.
      // We need to ensure the binding storage operation is complete before sending the remoteLinkOpened response.
      OperationContext oldContext = protonSession.sessionSPI.recoverContext();

      try {

         final Runnable runnable = link.attachments().get(AMQP_LINK_INITIALIZER_KEY, Runnable.class);
         if (runnable != null) {
            link.attachments().set(AMQP_LINK_INITIALIZER_KEY, Runnable.class, null);
            runnable.run();
            return;
         }

         if (link.getLocalState() == EndpointState.ACTIVE) { // if already active it's probably from the AMQP bridge and hence we just ignore it
            return;
         }

         link.setSource(link.getRemoteSource());
         link.setTarget(link.getRemoteTarget());

         if (link instanceof Receiver receiver) {
            if (link.getRemoteTarget() instanceof Coordinator coordinator) {
               protonSession.addTransactionHandler(coordinator, receiver);
            } else if (isReplicaTarget(receiver)) {
               handleReplicaTargetLinkOpened(protonSession, receiver);
            } else if (isFederationControlLink(receiver)) {
               handleFederationControlLinkOpened(protonSession, receiver);
            } else if (isFederationEventLink(receiver)) {
               protonSession.addFederationEventProcessor(receiver);
            } else {
               protonSession.addReceiver(receiver);
            }
         } else {
            final Sender sender = (Sender) link;
            if (isFederationAddressReceiver(sender)) {
               protonSession.addFederationAddressSender(sender);
            } else if (isFederationQueueReceiver(sender)) {
               protonSession.addFederationQueueSender(sender);
            } else if (isFederationEventLink(sender)) {
               protonSession.addFederationEventDispatcher(sender);
            } else {
               protonSession.addSender(sender);
            }
         }
      } finally {
         protonSession.sessionSPI.resetContext(oldContext);
      }
   }

   private void handleReplicaTargetLinkOpened(AMQPSessionContext protonSession, Receiver receiver) throws Exception {
      try {
         try {
            protonSession.getSessionSPI().check(SimpleString.of(receiver.getTarget().getAddress()), CheckType.SEND, getSecurityAuth());
         } catch (ActiveMQSecurityException e) {
            throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.securityErrorCreatingProducer(e.getMessage());
         }

         if (!verifyDesiredCapability(receiver, AMQPMirrorControllerSource.MIRROR_CAPABILITY)) {
            throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.missingDesiredCapability(AMQPMirrorControllerSource.MIRROR_CAPABILITY.toString());
         }
      } catch (ActiveMQAMQPException e) {
         logger.warn(e.getMessage(), e);

         receiver.setTarget(null);
         receiver.setCondition(new ErrorCondition(e.getAmqpError(), e.getMessage()));
         receiver.close();

         return;
      }

      protonSession.addReplicaTarget(receiver);
   }

   private void handleFederationControlLinkOpened(AMQPSessionContext protonSession, Receiver receiver) throws Exception {
      try {
         try {
            protonSession.getSessionSPI().check(SimpleString.of(FEDERATION_BASE_VALIDATION_ADDRESS), CheckType.SEND, getSecurityAuth());
         } catch (ActiveMQSecurityException e) {
            throw new ActiveMQAMQPSecurityException(
               "User does not have permission to attach to the federation control address");
         }

         protonSession.addFederationCommandProcessor(receiver);
      } catch (ActiveMQAMQPException e) {
         logger.warn(e.getMessage(), e);

         receiver.setTarget(null);
         receiver.setCondition(new ErrorCondition(e.getAmqpError(), e.getMessage()));
         receiver.close();

         return;
      }
   }

   private static boolean isReplicaTarget(Link link) {
      return link != null && link.getTarget() != null && link.getTarget().getAddress() != null && link.getTarget().getAddress().startsWith(ProtonProtocolManager.MIRROR_ADDRESS);
   }

   private static boolean isFederationControlLink(Receiver receiver) {
      return verifyDesiredCapability(receiver, FEDERATION_CONTROL_LINK);
   }

   private static boolean isFederationEventLink(Sender sender) {
      return verifyDesiredCapability(sender, FEDERATION_EVENT_LINK);
   }

   private static boolean isFederationEventLink(Receiver receiver) {
      return verifyDesiredCapability(receiver, FEDERATION_EVENT_LINK);
   }

   private static boolean isFederationQueueReceiver(Sender sender) {
      return verifyDesiredCapability(sender, FEDERATION_QUEUE_RECEIVER);
   }

   private static boolean isFederationAddressReceiver(Sender sender) {
      return verifyDesiredCapability(sender, FEDERATION_ADDRESS_RECEIVER);
   }

   private Symbol[] getOfferedCapabilitiesForResponse() {
      return offeredCapabilities != null ? offeredCapabilities : ExtCapability.getCapabilities();
   }

   private Map<Symbol, Object> getConnectionPropertiesForResponse() {
      final URI tc = connectionCallback.getFailoverList();

      if (tc != null) {
         Map<Symbol, Object> hostDetails = new HashMap<>();
         hostDetails.put(NETWORK_HOST, tc.getHost());
         boolean isSSL = tc.getQuery().contains(TransportConstants.SSL_ENABLED_PROP_NAME + "=true");
         if (isSSL) {
            hostDetails.put(SCHEME, "amqps");
         } else {
            hostDetails.put(SCHEME, "amqp");
         }
         hostDetails.put(HOSTNAME, tc.getHost());
         hostDetails.put(PORT, tc.getPort());

         connectionProperties.put(FAILOVER_SERVER_LIST, Arrays.asList(hostDetails));
      }

      return Collections.unmodifiableMap(connectionProperties);
   }

   public void open() {
      handler.open(containerId, connectionProperties, offeredCapabilities, desiredCapabilities);
   }

   public String getContainer() {
      return containerId;
   }

   public void addEventHandler(EventHandler eventHandler) {
      handler.addEventHandler(eventHandler);
   }

   public ProtonProtocolManager getProtocolManager() {
      return protocolManager;
   }

   public int getAmqpLowCredits() {
      if (protocolManager != null) {
         return protocolManager.getAmqpLowCredits();
      } else {
         // this is for tests only...
         return AmqpSupport.AMQP_LOW_CREDITS_DEFAULT;
      }
   }

   public int getAmqpCredits() {
      if (protocolManager != null) {
         return protocolManager.getAmqpCredits();
      } else {
         // this is for tests only...
         return AmqpSupport.AMQP_CREDITS_DEFAULT;
      }
   }

   public boolean isUseCoreSubscriptionNaming() {
      return useCoreSubscriptionNaming;
   }

   @Override
   public void onAuthInit(ProtonHandler handler, Connection connection, boolean sasl) {
      if (sasl) {
         // configured mech in decreasing order of preference
         String[] mechanisms = connectionCallback.getSaslMechanisms();
         if (mechanisms == null || mechanisms.length == 0) {
            mechanisms = AnonymousServerSASL.ANONYMOUS_MECH;
         }
         handler.createServerSASL(mechanisms);
      } else {
         if (!connectionCallback.isSupportsAnonymous()) {
            connectionCallback.sendSASLSupported();
            connectionCallback.close();
            handler.close(null, this);
         }
      }
   }

   @Override
   public void onSaslRemoteMechanismChosen(ProtonHandler handler, String mech) {
      handler.setChosenMechanism(connectionCallback.getServerSASL(mech));
   }

   @Override
   public void onSaslMechanismsOffered(final ProtonHandler handler, final String[] mechanisms) {
      if (saslClientFactory != null) {
         handler.setClientMechanism(saslClientFactory.chooseMechanism(mechanisms));
      }
   }

   @Override
   public void onAuthFailed(final ProtonHandler protonHandler, final Connection connection) {
      connectionCallback.close();
      handler.close(null, this);
   }

   @Override
   public void onAuthSuccess(final ProtonHandler protonHandler, final Connection connection) {
      connection.open();
   }

   @Override
   public void onTransport(Transport transport) {
      handler.flushBytes();
   }

   @Override
   public void pushBytes(ByteBuf bytes) {
      connectionCallback.onTransport(bytes, this);
   }

   @Override
   public boolean flowControl(ReadyListener readyListener) {
      return connectionCallback.isWritable(readyListener);
   }

   @Override
   public String getRemoteAddress() {
      return connectionCallback.getTransportConnection().getRemoteAddress();
   }

   public AMQPConnectionContext addRemoteOpenedListener(Consumer<AMQPConnectionContext> listener) {
      if (handler.getConnection() != null && EndpointState.ACTIVE.equals(handler.getConnection().getRemoteState())) {
         try {
            listener.accept(this);
         } catch (Exception e) {
            logger.debug("Caught exception from remote opened handler", e);
         }
      } else {
         remoteOpenedListeners.add(listener);
      }

      return this;
   }

   @Override
   public void onRemoteOpen(Connection connection) throws Exception {
      handler.requireHandler();
      try {
         initInternal();
      } catch (Exception e) {
         logger.error("Error init connection", e);
      }

      if (!validateUser(connection) || (connectionCallback.getTransportConnection().getRouter() != null
         && protocolManager.getRoutingHandler().route(this, connection)) || !validateConnection(connection)) {
         connection.close();
      } else {
         connection.setContext(AMQPConnectionContext.this);
         // An outgoing connection would have already configured the connection and opened or is in the
         // processing of doing so when a simultaneous open occurred so we protect against altering the
         // sent values or preventing the in process open from configuring the values it wants.
         if (isIncomingConnection) {
            connection.setContainer(containerId);
            connection.setProperties(getConnectionPropertiesForResponse());
            connection.setOfferedCapabilities(getOfferedCapabilitiesForResponse());
            connection.setDesiredCapabilities(desiredCapabilities);
            connection.open();
         }
      }
      initialize();

      /*
       * This can be null which is in effect an empty map, also we really don't need to check this for in bound
       * connections but its here in case we add support for outbound connections.
       */
      if (connection.getRemoteProperties() == null || !connection.getRemoteProperties().containsKey(CONNECTION_OPEN_FAILED)) {
         long nextKeepAliveTime = handler.tick(true);

         if (nextKeepAliveTime != 0 && scheduledPool != null) {
            scheduleOp.setDelay(nextKeepAliveTime - TimeUnit.NANOSECONDS.toMillis(System.nanoTime()));

            scheduledFutureRef.getAndUpdate(scheduleOp);
         }

         remoteOpenedListeners.forEach(consumer -> {
            try {
               consumer.accept(this);
            } catch (Exception e) {
               logger.debug("Caught exception from remote opened handler", e);
            }
         });

         remoteOpenedListeners.clear();
      }
   }

   private boolean validateUser(Connection connection) throws Exception {
      user = null;
      password = null;
      validatedUser = null;

      SASLResult saslResult = getSASLResult();
      if (saslResult != null) {
         user = saslResult.getUser();
         if (saslResult instanceof PlainSASLResult plainSASLResult) {
            password = plainSASLResult.getPassword();
         }
      }

      if (isIncomingConnection() && saslClientFactory == null && !isBrokerConnection()) {
         try {
            validatedUser = protocolManager.getServer().validateUser(user, password, connectionCallback.getProtonConnectionDelegate(), protocolManager.getSecurityDomain());
         } catch (ActiveMQSecurityException e) {
            ErrorCondition error = new ErrorCondition();
            error.setCondition(AmqpError.UNAUTHORIZED_ACCESS);
            error.setDescription(Objects.requireNonNullElse(e.getMessage(), e.getClass().getSimpleName()));
            connection.setCondition(error);
            connection.setProperties(Collections.singletonMap(AmqpSupport.CONNECTION_OPEN_FAILED, true));

            return false;
         }
      }

      return true;
   }

   class ScheduleOperator implements UnaryOperator<Future<?>> {

      private long delay;
      final ScheduleRunnable scheduleRunnable;

      ScheduleOperator(ScheduleRunnable scheduleRunnable) {
         this.scheduleRunnable = scheduleRunnable;
      }

      @Override
      public Future<?> apply(Future<?> future) {
         return (future != null) ? scheduledPool.schedule(scheduleRunnable, delay, TimeUnit.MILLISECONDS) : null;
      }

      public void setDelay(long delay) {
         this.delay = delay;
      }
   }


   class TickerRunnable implements Runnable {

      @Override
      public void run() {
         Long rescheduleAt = handler.tick(false);

         if (rescheduleAt == null) {
            // this mean tick could not acquire a lock, we will just retry in 10 milliseconds.
            scheduleOp.setDelay(10);

            scheduledFutureRef.getAndUpdate(scheduleOp);
         } else if (rescheduleAt != 0) {
            scheduleOp.setDelay(rescheduleAt - TimeUnit.NANOSECONDS.toMillis(System.nanoTime()));

            scheduledFutureRef.getAndUpdate(scheduleOp);
         }
      }
   }

   class ScheduleRunnable implements Runnable {

      final TickerRunnable tickerRunnable = new TickerRunnable();

      @Override
      public void run() {

         // The actual tick has to happen within a Netty Worker, to avoid requiring a lock
         // this will also be used to flush the data directly into netty connection's executor
         handler.runLater(tickerRunnable);
      }
   }

   @Override
   public void onTransportError(Transport transport) throws Exception {
      final String errorMessage = transport.getCondition() != null && transport.getCondition().getDescription() != null ?
         transport.getCondition().getDescription() : "Unknown Internal Error";

      // Cleanup later after the any pending work gets sent to the remote via an IO flush.
      runNow(() -> connectionCallback.getProtonConnectionDelegate().fail(new ActiveMQAMQPInternalErrorException(errorMessage)));
   }

   @Override
   public void onLocalClose(Connection connection) {
      handler.requireHandler();

      // If the connection delegate is marked as destroyed the IO connection is closed
      // or closing and will never hear back from the remote with a matching Close
      // performative from the peer on the other side. In this case we should allow the
      // sessions a chance to clean up any local sender or receiver bindings.
      if (connectionCallback.getProtonConnectionDelegate().isDestroyed()) {
         for (AMQPSessionContext protonSession : sessions.values()) {
            try {
               protonSession.close();
            } catch (Exception e) {
               // We are closing so ignore errors from attempts to cleanup
               logger.trace("Caught error while handling local connection close: ", e);
            }
         }

         sessions.clear();

         // Try and flush any pending work if the IO hasn't yet been closed.
         handler.flushBytes();
      }
   }

   @Override
   public void onRemoteClose(Connection connection) {
      handler.requireHandler();
      connection.close();
      connection.free();

      for (AMQPSessionContext protonSession : sessions.values()) {
         protonSession.close();
      }
      sessions.clear();

      // We must force write the channel before we actually destroy the connection
      handler.flushBytes();
      destroy();
   }

   @Override
   public void onLocalOpen(Session session) throws Exception {
      AMQPSessionContext sessionContext = getSessionExtension(session);

      if (brokerConnection) {
         sessionContext.initialize();
      }
   }

   @Override
   public void onRemoteOpen(Session session) throws Exception {
      // If connection already closed then we shouldn't react to the most likely
      // pipelined Begin event.
      if (session.getConnection().getLocalState() != EndpointState.CLOSED) {
         handler.requireHandler();
         getSessionExtension(session).initialize();
         session.open();
      }
   }

   @Override
   public void onRemoteClose(Session session) throws Exception {
      handler.runNow(() -> {
         session.close();
         session.free();

         AMQPSessionContext sessionContext = (AMQPSessionContext) session.getContext();
         if (sessionContext != null) {
            sessionContext.close();
            sessions.remove(session);
            session.setContext(null);
         }
      });
   }

   @Override
   public void onRemoteOpen(Link link) throws Exception {
      // If connection already closed then we shouldn't react to the most likely
      // pipelined Attach event.
      if (link.getSession().getConnection().getLocalState() != EndpointState.CLOSED) {
         remoteLinkOpened(link);
      }
   }

   @Override
   public void onFlow(Link link) throws Exception {
      if (link.getContext() != null) {
         ((ProtonDeliveryHandler) link.getContext()).onFlow(link.getCredit(), link.getDrain());
      }
   }

   @Override
   public void onRemoteClose(Link link) throws Exception {
      handler.requireHandler();

      final AtomicReference<Exception> handlerThrew = new AtomicReference<>();

      linkCloseListeners.forEach((k, v) -> {
         try {
            v.onClose(link);
         } catch (Exception e) {
            handlerThrew.compareAndSet(null, e);
         }
      });

      ProtonDeliveryHandler linkContext = (ProtonDeliveryHandler) link.getContext();
      if (linkContext != null) {
         try {
            linkContext.close(true);
         } catch (ActiveMQAMQPSecurityException e) {
            throw e;
         } catch (Exception e) {
            logger.error(e.getMessage(), e);
         }
      }

      link.close();
      link.free();
      flush();

      if (handlerThrew.get() != null) {
         throw handlerThrew.get();
      }
   }

   @Override
   public void onRemoteDetach(Link link) throws Exception {
      handler.requireHandler();
      boolean handleAsClose = link.getSource() != null && ((Source) link.getSource()).getExpiryPolicy() == TerminusExpiryPolicy.LINK_DETACH;

      if (handleAsClose) {
         onRemoteClose(link);
      } else {
         link.detach();
         link.free();
      }
   }

   @Override
   public void onLocalDetach(Link link) throws Exception {
      handler.requireHandler();
      Object context = link.getContext();
      if (context instanceof ProtonServerSenderContext senderContext) {
         senderContext.close(false);
      }
   }

   @Override
   public void onDelivery(Delivery delivery) throws Exception {
      handler.requireHandler();
      ProtonDeliveryHandler handler = (ProtonDeliveryHandler) delivery.getLink().getContext();
      if (handler != null) {
         handler.onMessage(delivery);
      } else {
         logger.warn("Handler is null, can't delivery {}", delivery, new Exception("tracing location"));
      }
   }

   private class LocalSecurity implements SecurityAuth {
      @Override
      public String getUsername() {
         String username = null;
         SASLResult saslResult = getSASLResult();
         if (saslResult != null) {
            username = saslResult.getUser();
         }

         return username;
      }

      @Override
      public String getPassword() {
         String password = null;
         SASLResult saslResult = getSASLResult();
         if (saslResult != null) {
            if (saslResult instanceof PlainSASLResult plainSASLResult) {
               password = plainSASLResult.getPassword();
            }
         }

         return password;
      }

      @Override
      public RemotingConnection getRemotingConnection() {
         return connectionCallback.getProtonConnectionDelegate();
      }

      @Override
      public String getSecurityDomain() {
         return getProtocolManager().getSecurityDomain();
      }
   }
}
