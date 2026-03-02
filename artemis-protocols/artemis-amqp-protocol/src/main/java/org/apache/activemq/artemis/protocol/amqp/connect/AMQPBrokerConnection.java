/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.protocol.amqp.connect;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBridgeBrokerConnectionElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectionAddressType;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectionElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPFederatedBrokerConnectionElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPFederationAddressPolicyElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPFederationQueuePolicyElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPMirrorBrokerConnectionElement;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.QueueBinding;
import org.apache.activemq.artemis.core.remoting.CloseListener;
import org.apache.activemq.artemis.core.remoting.FailureListener;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnection;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnector;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.BrokerConnection;
import org.apache.activemq.artemis.core.server.Consumer;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.lock.LockCoordinator;
import org.apache.activemq.artemis.core.server.mirror.MirrorController;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerQueuePlugin;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPMessage;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPSessionCallback;
import org.apache.activemq.artemis.protocol.amqp.broker.ActiveMQProtonRemotingConnection;
import org.apache.activemq.artemis.protocol.amqp.broker.ProtonProtocolManager;
import org.apache.activemq.artemis.protocol.amqp.broker.ProtonProtocolManagerFactory;
import org.apache.activemq.artemis.protocol.amqp.connect.AMQPBrokerConnectionManager.ClientProtocolManagerWithAMQP;
import org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeManagers;
import org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationPolicySupport;
import org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationSource;
import org.apache.activemq.artemis.protocol.amqp.connect.mirror.AMQPMirrorControllerAggregation;
import org.apache.activemq.artemis.protocol.amqp.connect.mirror.AMQPMirrorControllerSource;
import org.apache.activemq.artemis.protocol.amqp.connect.mirror.ReferenceIDSupplier;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPException;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPInternalErrorException;
import org.apache.activemq.artemis.protocol.amqp.logger.ActiveMQAMQPProtocolLogger;
import org.apache.activemq.artemis.protocol.amqp.logger.ActiveMQAMQPProtocolMessageBundle;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPLargeMessageWriter;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPMessageWriter;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPSessionContext;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPTunneledCoreLargeMessageWriter;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPTunneledCoreMessageWriter;
import org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport;
import org.apache.activemq.artemis.protocol.amqp.proton.MessageWriter;
import org.apache.activemq.artemis.protocol.amqp.proton.ProtonServerReceiverContext;
import org.apache.activemq.artemis.protocol.amqp.proton.ProtonServerSenderContext;
import org.apache.activemq.artemis.protocol.amqp.proton.SenderController;
import org.apache.activemq.artemis.protocol.amqp.sasl.ClientSASL;
import org.apache.activemq.artemis.protocol.amqp.sasl.ClientSASLFactory;
import org.apache.activemq.artemis.protocol.amqp.sasl.scram.SCRAMClientSASL;
import org.apache.activemq.artemis.spi.core.protocol.ConnectionEntry;
import org.apache.activemq.artemis.spi.core.remoting.ClientConnectionLifeCycleListener;
import org.apache.activemq.artemis.spi.core.remoting.ClientProtocolManager;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.apache.activemq.artemis.spi.core.security.scram.SCRAM;
import org.apache.activemq.artemis.utils.CertificateUtil;
import org.apache.activemq.artemis.utils.ConfigurationHelper;
import org.apache.activemq.artemis.utils.UUIDGenerator;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.Source;
import org.apache.qpid.proton.amqp.messaging.Target;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.amqp.transport.ReceiverSettleMode;
import org.apache.qpid.proton.amqp.transport.SenderSettleMode;
import org.apache.qpid.proton.engine.EndpointState;
import org.apache.qpid.proton.engine.Link;
import org.apache.qpid.proton.engine.Receiver;
import org.apache.qpid.proton.engine.Sender;
import org.apache.qpid.proton.engine.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.activemq.artemis.protocol.amqp.connect.AMQPBrokerConnectionConstants.BROKER_CONNECTION_INFO;
import static org.apache.activemq.artemis.protocol.amqp.connect.AMQPBrokerConnectionConstants.CONNECTION_NAME;
import static org.apache.activemq.artemis.protocol.amqp.connect.AMQPBrokerConnectionConstants.NODE_ID;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.AMQP_LINK_INITIALIZER_KEY;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.SHARED_SUBS;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.verifyCapabilities;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.verifyOfferedCapabilities;

import java.lang.invoke.MethodHandles;

public class AMQPBrokerConnection implements ClientConnectionLifeCycleListener, ActiveMQServerQueuePlugin, BrokerConnection {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   /**
    * Default value for the core message tunneling feature that indicates if core protocol messages should be streamed
    * as binary blobs as the payload of an custom AMQP message which avoids any conversions of the messages to / from
    * AMQP.
    */
   public static final boolean DEFAULT_CORE_MESSAGE_TUNNELING_ENABLED = true;

   private static final NettyConnectorFactory CONNECTOR_FACTORY = new NettyConnectorFactory().setServerConnector(true);
   private static final String SASL_MECHANISMS_KEY = "saslMechanisms";

   private final ProtonProtocolManagerFactory protonProtocolManagerFactory;
   private final ReferenceIDSupplier referenceIdSupplier;
   private final AMQPBrokerConnectConfiguration brokerConnectConfiguration;
   private final ActiveMQServer server;
   private final List<TransportConfiguration> configurations;
   private final AMQPBrokerConnectionManager connectionManager;
   private NettyConnection connection;
   private Session session;
   private AMQPSessionContext sessionContext;
   private ActiveMQProtonRemotingConnection protonRemotingConnection;
   private volatile boolean started = false;
   private AMQPMirrorControllerSource mirrorControllerSource;
   private AMQPFederationSource brokerFederation;
   private AMQPBridgeManagers bridgeManagers;
   private int retryCounter = 0;
   private int lastRetryCounter;
   private int connectionTimeout;
   private boolean connecting = false;
   private volatile ScheduledFuture<?> reconnectFuture;
   private final Set<Queue> senders = new HashSet<>();
   private final Set<Queue> receivers = new HashSet<>();
   private final Map<String, Predicate<Link>> linkClosedInterceptors = new ConcurrentHashMap<>();
   private LockCoordinator lockCoordinator;

   /**
    * This will be false if the lock coordinator is in place and it is not holding the lock
    */
   private volatile boolean enabled = true;

   /**
    * This will return false when the LockCoordinator lose the lock.
    *  */
   public boolean isEnabled() {
      return enabled;
   }

   private void enable() {
      enabled = true;
   }

   private void disable() {
      enabled = false;
   }

   @Override
   public LockCoordinator getLockCoordinator() {
      return lockCoordinator;
   }

   @Override
   public AMQPBrokerConnection setLockCoordinator(LockCoordinator lockCoordinator) {
      this.lockCoordinator = lockCoordinator;
      return this;
   }

   final Executor connectExecutor;
   final ScheduledExecutorService scheduledExecutorService;

   /**
    * This is just for logging. the actual connection will come from the amqpConnection configuration
    */
   String host;

   /**
    * This is just for logging. the actual connection will come from the amqpConnection configuration
    */
   int port;

   public AMQPBrokerConnection(AMQPBrokerConnectionManager connectionManager,
                               AMQPBrokerConnectConfiguration brokerConnectConfiguration,
                               ProtonProtocolManagerFactory protonProtocolManagerFactory,
                               ActiveMQServer server) throws Exception {
      this.connectionManager = connectionManager;
      this.brokerConnectConfiguration = brokerConnectConfiguration;
      this.server = server;
      this.configurations = brokerConnectConfiguration.getTransportConfigurations();
      this.connectExecutor = server.getExecutorFactory().getExecutor();
      this.scheduledExecutorService = server.getScheduledPool();
      this.protonProtocolManagerFactory = protonProtocolManagerFactory;
      this.referenceIdSupplier = new ReferenceIDSupplier(server);
   }

   @Override
   public String getName() {
      return brokerConnectConfiguration.getName();
   }

   @Override
   public String getProtocol() {
      return "AMQP";
   }

   @Override
   public AMQPBrokerConnectConfiguration getConfiguration() {
      return brokerConnectConfiguration;
   }

   @Override
   public boolean isConnected() {
      final NettyConnection connection = this.connection;

      if (connection != null) {
         return connection.isOpen();
      } else {
         return false;
      }
   }

   @Override
   public boolean isStarted() {
      return started;
   }

   public boolean isConnecting() {
      return connecting;
   }

   public int getConnectionTimeout() {
      return connectionTimeout;
   }

   @Override
   public synchronized void initialize() throws Exception {
      try {
         server.registerBrokerConnection(this);
         server.getManagementService().registerBrokerConnection(this);

         if (brokerConnectConfiguration != null && brokerConnectConfiguration.getConnectionElements() != null) {
            for (AMQPBrokerConnectionElement connectionElement : brokerConnectConfiguration.getConnectionElements()) {
               final AMQPBrokerConnectionAddressType elementType = connectionElement.getType();

               if (elementType == AMQPBrokerConnectionAddressType.FEDERATION) {
                  installFederation((AMQPFederatedBrokerConnectionElement) connectionElement, server);
               } else if (elementType == AMQPBrokerConnectionAddressType.BRIDGE) {
                  installBridgeManager((AMQPBridgeBrokerConnectionElement) connectionElement, server);
               }
            }
         }
      } catch (Throwable e) {
         logger.warn(e.getMessage(), e);
      }
   }

   @Override
   public synchronized void start() throws Exception {

      if (lockCoordinator != null) {
         disable();
         lockCoordinator.onLockAcquired(this::resume);
         lockCoordinator.onLockReleased(this::pause);
      } else {
         enable();
      }

      this.internalStart();
   }

   private synchronized void internalStart() throws Exception {
      if (!started) {
         started = true;
         server.getConfiguration().registerBrokerPlugin(this);

         try {
            if (brokerConnectConfiguration != null && brokerConnectConfiguration.getConnectionElements() != null) {
               for (AMQPBrokerConnectionElement connectionElement : brokerConnectConfiguration.getConnectionElements()) {
                  final AMQPBrokerConnectionAddressType elementType = connectionElement.getType();

                  // Preserve old behavior where mirror controller wasn't installed until the first time
                  // the broker connection was started. This won't add a new controller if stopped and then
                  // restarted, once added a controller cannot currently be rmoved.
                  if (elementType == AMQPBrokerConnectionAddressType.MIRROR) {
                     installMirrorController((AMQPMirrorBrokerConnectionElement) connectionElement, server);
                  }
               }
            }
         } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
         }

         if (isEnabled()) {
            if (brokerFederation != null) {
               try {
                  brokerFederation.start();
               } catch (ActiveMQException e) {
                  logger.warn("Error caught while starting federation instance.", e);
               }
            }

            if (bridgeManagers != null) {
               try {
                  bridgeManagers.start();
               } catch (ActiveMQException e) {
                  logger.warn("Error caught while starting bridge managers instance.", e);
               }
            }
         }

         connectExecutor.execute(() -> doConnect());
      }
   }

   @Override
   public synchronized void stop() {
      // NOTE: We don't set enabled=false in the stop for the following reason:
      //       Mirror is supposed to keep capturing events as the broker is being shutdown or
      //       the connection is being stopped, or even after stopped.
      //       Say as the broker is going down a message is received and recorded
      //       if the mirror stopped capturing that event, you would miss it for next time you restart the broker
      //       and our tests would eventually miss an event, or more importantly users would lose a message during this process
      if (started) {
         started = false;
         server.getConfiguration().unRegisterBrokerPlugin(this);

         if (protonRemotingConnection != null) {
            protonRemotingConnection.fail(new ActiveMQException("Stopping Broker Connection"));
            protonRemotingConnection = null;
            connection = null;
         }

         final ScheduledFuture<?> scheduledFuture = reconnectFuture;
         reconnectFuture = null;
         if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
         }

         if (brokerFederation != null) {
            try {
               brokerFederation.stop();
            } catch (Exception e) {
               logger.debug("Error caught while stopping federation instance.", e);
            }
         }

         if (bridgeManagers != null) {
            try {
               bridgeManagers.stop();
            } catch (Exception e) {
               logger.warn("Error caught while stopping bridge managers instance.", e);
            }
         }
      }
   }

   @Override
   public synchronized void shutdown() throws Exception {
      try {
         stop();
      } finally {
         if (brokerFederation != null) {
            try {
               brokerFederation.shutdown();
            } catch (ActiveMQException e) {
               logger.debug("Error caught while shutting down federation instance.", e);
            } finally {
               brokerFederation = null;
            }
         }

         if (bridgeManagers != null) {
            try {
               bridgeManagers.shutdown();
            } catch (Exception e) {
               logger.debug("Error caught while shutting down bridge managers instance.", e);
            } finally {
               bridgeManagers = null;
            }
         }

         server.unregisterBrokerConnection(this);
         server.getManagementService().unregisterBrokerConnection(getName());
      }
   }

   private synchronized void pause() throws Exception {
      disable();
      if (bridgeManagers != null) {
         bridgeManagers.stop();
      }
      if (brokerFederation != null) {
         brokerFederation.stop();
      }
   }


   private synchronized void resume() throws Exception {
      enable();
      if (bridgeManagers != null) {
         bridgeManagers.start();
      }
      if (brokerFederation != null) {
         brokerFederation.start();
      }
   }


   public ActiveMQServer getServer() {
      return server;
   }

   public NettyConnection getConnection() {
      return connection;
   }

   @Override
   public void afterCreateQueue(Queue queue) {
      connectExecutor.execute(() -> {
         for (AMQPBrokerConnectionElement connectionElement : brokerConnectConfiguration.getConnectionElements()) {
            validateMatching(queue, connectionElement);
         }
      });
   }

   public void validateMatching(Queue queue, AMQPBrokerConnectionElement connectionElement) {
      if (connectionElement.getType() == AMQPBrokerConnectionAddressType.SENDER ||
          connectionElement.getType() == AMQPBrokerConnectionAddressType.RECEIVER ||
          connectionElement.getType() == AMQPBrokerConnectionAddressType.PEER) {
         if (connectionElement.getQueueName() != null) {
            if (queue.getName().equals(connectionElement.getQueueName())) {
               createLink(queue, connectionElement);
            }
         } else if (connectionElement.match(queue.getAddress(), server.getConfiguration().getWildcardConfiguration())) {
            createLink(queue, connectionElement);
         }
      }
   }

   public void createLink(Queue queue, AMQPBrokerConnectionElement connectionElement) {
      if (connectionElement.getType() == AMQPBrokerConnectionAddressType.PEER) {
         Symbol[] dispatchCapability = new Symbol[]{AMQPMirrorControllerSource.QPID_DISPATCH_WAYPOINT_CAPABILITY};
         connectSender(queue, queue.getAddress().toString(), null, null, null, null, dispatchCapability, null);
         connectReceiver(protonRemotingConnection, session, sessionContext, queue, dispatchCapability);
      } else if (connectionElement.getType() == AMQPBrokerConnectionAddressType.SENDER) {
         connectSender(queue, queue.getAddress().toString(), null, null, null, null, null, null);
      } else if (connectionElement.getType() == AMQPBrokerConnectionAddressType.RECEIVER) {
         connectReceiver(protonRemotingConnection, session, sessionContext, queue);
      }
   }

   SimpleString getMirrorSNF(AMQPMirrorBrokerConnectionElement mirrorElement) {
      SimpleString snf = mirrorElement.getMirrorSNF();
      if (snf == null) {
         snf = SimpleString.of(ProtonProtocolManager.getMirrorAddress(this.brokerConnectConfiguration.getName()));
         mirrorElement.setMirrorSNF(snf);
      }
      return snf;
   }

   /**
    * Adds a remote link closed event interceptor that can intercept the closed event and if it returns {@code true}
    * indicate that the close has been handled and that normal broker connection remote link closed handling should be
    * ignored.
    *
    * @param id          A unique Id value that identifies the intercepter for later removal.
    * @param interceptor The predicate that will be called for any link close.
    * @return this broker connection instance
    */
   public AMQPBrokerConnection addLinkClosedInterceptor(String id, Predicate<Link> interceptor) {
      linkClosedInterceptors.put(id, interceptor);
      return this;
   }

   /**
    * Remove a previously registered link close interceptor from the broker connection.
    *
    * @param id The id of the interceptor to remove
    * @return this broker connection instance
    */
   public AMQPBrokerConnection removeLinkClosedInterceptor(String id) {
      linkClosedInterceptors.remove(id);
      return this;
   }

   private void linkClosed(Link link) {
      for (Map.Entry<String, Predicate<Link>> interceptor : linkClosedInterceptors.entrySet()) {
         if (interceptor.getValue().test(link)) {
            logger.trace("Remote link[{}] close intercepted and handled by interceptor: {}", link.getName(), interceptor.getKey());
            return;
         }
      }

      if (link.getLocalState() == EndpointState.ACTIVE) {
         error(ActiveMQAMQPProtocolMessageBundle.BUNDLE.brokerConnectionRemoteLinkClosed(), lastRetryCounter);
      }
   }

   private void doConnect() {
      try {
         connecting = true;

         TransportConfiguration configuration = configurations.get(retryCounter % configurations.size());
         host = ConfigurationHelper.getStringProperty(TransportConstants.HOST_PROP_NAME, TransportConstants.DEFAULT_HOST, configuration.getParams());
         port = ConfigurationHelper.getIntProperty(TransportConstants.PORT_PROP_NAME, TransportConstants.DEFAULT_PORT, configuration.getParams());

         ProtonProtocolManager protonProtocolManager =
            (ProtonProtocolManager)protonProtocolManagerFactory.createProtocolManager(server, configuration.getExtraParams(), null, null);
         NettyConnector connector = (NettyConnector)CONNECTOR_FACTORY.createConnector(
            configuration.getParams(), null, this, server.getExecutorFactory().getExecutor(), server.getThreadPool(), server.getScheduledPool(), new ClientProtocolManagerWithAMQP(protonProtocolManager));
         connector.start();

         logger.debug("Connecting {}", configuration);

         connectionTimeout = connector.getConnectTimeoutMillis();
         try {
            connection = (NettyConnection) connector.createConnection();
            if (connection == null) {
               retryConnection();
               return;
            }
         } finally {
            if (connection == null) {
               try {
                  connector.close();
               } catch (Exception ex) {
               }
            }
         }

         lastRetryCounter = retryCounter;

         retryCounter = 0;

         reconnectFuture = null;

         // before we retry the connection we need to remove any previous links
         // as they will need to be recreated
         senders.clear();
         receivers.clear();

         final String[] enabledSaslMechanisms = configuration.getExtraParams().containsKey(SASL_MECHANISMS_KEY) ? protonProtocolManager.getSaslMechanisms() : null;
         final ClientSASLFactory saslFactory = new SaslFactory(connection, brokerConnectConfiguration, enabledSaslMechanisms);

         final Map<String, Object> brokerConnectionInfo = new HashMap<>();

         brokerConnectionInfo.put(CONNECTION_NAME, getName());
         brokerConnectionInfo.put(NODE_ID, server.getNodeID().toString());

         final Map<Symbol, Object> brokerConnectionProperties = new HashMap<>();

         brokerConnectionProperties.put(BROKER_CONNECTION_INFO, brokerConnectionInfo);

         final Symbol[] brokerDesiredCapabilities = bridgeManagers == null ? null : new Symbol[] {SHARED_SUBS};

         NettyConnectorCloseHandler connectorCloseHandler = new NettyConnectorCloseHandler(connector, connectExecutor);
         ConnectionEntry entry = protonProtocolManager.createOutgoingConnectionEntry(connection, saslFactory, brokerConnectionProperties, null, brokerDesiredCapabilities);
         server.getRemotingService().addConnectionEntry(connection, entry);
         protonRemotingConnection = (ActiveMQProtonRemotingConnection) entry.connection;
         protonRemotingConnection.getAmqpConnection().addLinkRemoteCloseListener(getName(), this::linkClosed);
         protonRemotingConnection.addCloseListener(connectorCloseHandler);
         protonRemotingConnection.addFailureListener(connectorCloseHandler);

         connection.getChannel().pipeline().addLast(new AMQPBrokerConnectionChannelHandler(connector.getChannelGroup(), protonRemotingConnection.getAmqpConnection().getHandler(), this, server.getExecutorFactory().getExecutor()));

         session = protonRemotingConnection.getAmqpConnection().getHandler().getConnection().session();
         sessionContext = protonRemotingConnection.getAmqpConnection().getSessionExtension(session);

         protonRemotingConnection.getAmqpConnection().runLater(() -> {
            protonRemotingConnection.getAmqpConnection().addRemoteOpenedListener(c -> {
               try {
                  // Starting the Bridge triggers rebuild of AMQP sender and receiver links based on current broker state.
                  // This requires in some cases knowing the remote offered capabilities which can't be tested until after
                  // the remote sends its Open performative carrying those.
                  if (bridgeManagers != null) {
                     bridgeManagers.connectionRestored(sessionContext);
                  }
               } catch (Throwable e) {
                  error(e);
               } finally {
                  protonRemotingConnection.getAmqpConnection().flush();
               }
            });
            protonRemotingConnection.getAmqpConnection().open();
            session.open();
            protonRemotingConnection.getAmqpConnection().flush();
         });

         if (brokerConnectConfiguration.getConnectionElements() != null) {
            Stream<Binding> bindingStream = server.getPostOffice().getAllBindings();

            bindingStream.forEach(binding -> {
               if (binding instanceof QueueBinding queueBinding) {
                  Queue queue = queueBinding.getQueue();
                  for (AMQPBrokerConnectionElement connectionElement : brokerConnectConfiguration.getConnectionElements()) {
                     validateMatching(queue, connectionElement);
                  }
               }
            });

            for (AMQPBrokerConnectionElement connectionElement : brokerConnectConfiguration.getConnectionElements()) {
               if (connectionElement.getType() == AMQPBrokerConnectionAddressType.MIRROR) {
                  AMQPMirrorBrokerConnectionElement replica = (AMQPMirrorBrokerConnectionElement)connectionElement;

                  final Queue queue = server.locateQueue(getMirrorSNF(replica));

                  final boolean coreTunnelingEnabled = isCoreMessageTunnelingEnabled(replica);
                  final Symbol[] desiredCapabilities;

                  if (coreTunnelingEnabled) {
                     desiredCapabilities = new Symbol[] {AMQPMirrorControllerSource.MIRROR_CAPABILITY,
                                                         AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT};
                  } else {
                     desiredCapabilities = new Symbol[] {AMQPMirrorControllerSource.MIRROR_CAPABILITY};
                  }

                  final Symbol[] requiredOfferedCapabilities = new Symbol[] {AMQPMirrorControllerSource.MIRROR_CAPABILITY};

                  connectSender(queue,
                                queue.getName().toString(),
                                mirrorControllerSource::setLink,
                                (r) -> AMQPMirrorControllerSource.validateProtocolData(protonProtocolManager.getReferenceIDSupplier(), r, getMirrorSNF(replica)),
                                server.getNodeID().toString(),
                                desiredCapabilities,
                                null,
                                requiredOfferedCapabilities);
               } else if (connectionElement.getType() == AMQPBrokerConnectionAddressType.FEDERATION) {
                  // Signal the Federation instance to start a rebuild of federation links
                  // based on current broker state.
                  brokerFederation.connectionRestored(protonRemotingConnection.getAmqpConnection(), sessionContext);
               }
            }
         }

         protonRemotingConnection.getAmqpConnection().flush();

         connectionManager.connected(connection, this);

         ActiveMQAMQPProtocolLogger.LOGGER.successReconnect(brokerConnectConfiguration.getName(), host + ":" + port, lastRetryCounter);

         connecting = false;
      } catch (Throwable e) {
         error(e);
      }
   }

   public void retryConnection() {
      lastRetryCounter = retryCounter;
      if (connectionManager.isStarted() && started) {
         if (brokerConnectConfiguration.getReconnectAttempts() < 0 || retryCounter < brokerConnectConfiguration.getReconnectAttempts()) {
            retryCounter++;
            ActiveMQAMQPProtocolLogger.LOGGER.retryConnection(brokerConnectConfiguration.getName(), host + ":" + port, retryCounter, brokerConnectConfiguration.getReconnectAttempts());
            if (logger.isDebugEnabled()) {
               logger.debug("Reconnecting in {}, this is the {} of {}", brokerConnectConfiguration.getRetryInterval(), retryCounter, brokerConnectConfiguration.getReconnectAttempts());
            }
            reconnectFuture = scheduledExecutorService.schedule(() -> connectExecutor.execute(() -> doConnect()), brokerConnectConfiguration.getRetryInterval(), TimeUnit.MILLISECONDS);
         } else {
            retryCounter = 0;
            started = false;
            connecting = false;
            ActiveMQAMQPProtocolLogger.LOGGER.retryConnectionFailed(brokerConnectConfiguration.getName(), host + ":" +  port, lastRetryCounter);
            if (logger.isDebugEnabled()) {
               logger.debug("no more reconnections as the retry counter reached {} out of {}", retryCounter, brokerConnectConfiguration.getReconnectAttempts());
            }
         }
      }
   }

   private static void uninstallMirrorController(AMQPMirrorBrokerConnectionElement replicaConfig, ActiveMQServer server) {
      // TODO implement this as part of https://issues.apache.org/jira/browse/ARTEMIS-2965
   }

   private Queue installMirrorController(AMQPMirrorBrokerConnectionElement replicaConfig, ActiveMQServer server) throws Exception {

      MirrorController currentMirrorController = server.getMirrorController();

      // This following block is to avoid a duplicate on mirror controller
      if (currentMirrorController != null && currentMirrorController instanceof AMQPMirrorControllerSource source) {
         Queue queue = checkCurrentMirror(this, source);
         // on this case we already had a mirror installed before, we won't duplicate it
         if (queue != null) {
            queue.deliverAsync();
            return queue;
         }
      } else if (currentMirrorController != null && currentMirrorController instanceof AMQPMirrorControllerAggregation aggregation) {

         for (AMQPMirrorControllerSource source : aggregation.getPartitions()) {
            Queue queue = checkCurrentMirror(this, source);
            // on this case we already had a mirror installed before, we won't duplicate it
            if (queue != null) {
               return queue;
            }
         }
      }

      AddressInfo addressInfo = server.getAddressInfo(getMirrorSNF(replicaConfig));
      if (addressInfo == null) {
         addressInfo = new AddressInfo(getMirrorSNF(replicaConfig)).addRoutingType(RoutingType.ANYCAST).setAutoCreated(false).setTemporary(!replicaConfig.isDurable()).setInternal(true);
         server.addAddressInfo(addressInfo);
      }

      if (addressInfo.getRoutingType() != RoutingType.ANYCAST) {
         throw new IllegalArgumentException(addressInfo.getName() + " has " + addressInfo.getRoutingType() + " instead of ANYCAST");
      }

      Queue mirrorControlQueue = server.locateQueue(getMirrorSNF(replicaConfig));

      if (mirrorControlQueue == null) {
         mirrorControlQueue = server.createQueue(QueueConfiguration.of(getMirrorSNF(replicaConfig)).setAddress(getMirrorSNF(replicaConfig)).setRoutingType(RoutingType.ANYCAST).setDurable(replicaConfig.isDurable()).setInternal(true), true);
      }

      try {
         server.registerQueueOnManagement(mirrorControlQueue);
      } catch (Throwable ignored) {
         logger.debug(ignored.getMessage(), ignored);
      }

      logger.debug("Mirror queue {}", mirrorControlQueue.getName());

      mirrorControlQueue.setMirrorController(true);

      QueueBinding snfReplicaQueueBinding = (QueueBinding)server.getPostOffice().getBinding(getMirrorSNF(replicaConfig));
      if (snfReplicaQueueBinding == null) {
         logger.warn("Queue does not exist even after creation! {}", replicaConfig);
         throw new IllegalAccessException("Cannot start replica");
      }

      Queue snfQueue = snfReplicaQueueBinding.getQueue();

      if (!snfQueue.getAddress().equals(getMirrorSNF(replicaConfig))) {
         logger.warn("Queue {} belong to a different address ({}), while we expected it to be {}", snfQueue, snfQueue.getAddress(), addressInfo.getName());
         throw new IllegalAccessException("Cannot start replica");
      }

      AMQPMirrorControllerSource newPartition = new AMQPMirrorControllerSource(referenceIdSupplier, snfQueue, server, replicaConfig, this);

      this.mirrorControllerSource = newPartition;

      server.scanAddresses(newPartition);

      if (currentMirrorController == null) {
         server.installMirrorController(newPartition);
      } else {
         // Replace a standard implementation by an aggregated supporting multiple targets
         if (currentMirrorController instanceof AMQPMirrorControllerSource source) {
            // replacing the simple mirror control for an aggregator
            AMQPMirrorControllerAggregation remoteAggregation = new AMQPMirrorControllerAggregation();
            remoteAggregation.addPartition(source);
            currentMirrorController = remoteAggregation;
            server.installMirrorController(remoteAggregation);
         }
         ((AMQPMirrorControllerAggregation) currentMirrorController).addPartition(newPartition);
      }

      return snfQueue;
   }

   private static Queue checkCurrentMirror(AMQPBrokerConnection brokerConnection,
                                           AMQPMirrorControllerSource currentMirrorController) {
      AMQPMirrorControllerSource source = currentMirrorController;
      if (source.getBrokerConnection() == brokerConnection) {
         return source.getSnfQueue();
      }

      return null;
   }

   private void installBridgeManager(AMQPBridgeBrokerConnectionElement connectionElement, ActiveMQServer server) throws Exception {
      if (bridgeManagers == null) {
         bridgeManagers = new AMQPBridgeManagers(this);
      }

      bridgeManagers.addBridgeManager(connectionElement);
   }

   private void installFederation(AMQPFederatedBrokerConnectionElement connectionElement, ActiveMQServer server) throws Exception {
      final AMQPFederationSource federation = new AMQPFederationSource(connectionElement.getName(), connectionElement.getProperties(), this);

      // Ensure basic federation resources are initialized before adding policies and moving on
      federation.initialize();

      // Broker federation configuration for local resources that should be receiving from remote resources
      // when there is local demand.
      final Set<AMQPFederationAddressPolicyElement> localAddressPolicies = connectionElement.getLocalAddressPolicies();
      if (!localAddressPolicies.isEmpty()) {
         for (AMQPFederationAddressPolicyElement policy : localAddressPolicies) {
            federation.addAddressMatchPolicy(
               AMQPFederationPolicySupport.create(policy, federation.getWildcardConfiguration()));
         }
      }
      final Set<AMQPFederationQueuePolicyElement> localQueuePolicies = connectionElement.getLocalQueuePolicies();
      if (!localQueuePolicies.isEmpty()) {
         for (AMQPFederationQueuePolicyElement policy : localQueuePolicies) {
            federation.addQueueMatchPolicy(
               AMQPFederationPolicySupport.create(policy, federation.getWildcardConfiguration()));
         }
      }

      // Broker federation configuration for remote resources that should be receiving from local resources
      // when there is demand on the remote.
      final Set<AMQPFederationAddressPolicyElement> remoteAddressPolicies = connectionElement.getRemoteAddressPolicies();
      if (!remoteAddressPolicies.isEmpty()) {
         for (AMQPFederationAddressPolicyElement policy : remoteAddressPolicies) {
            federation.addRemoteAddressMatchPolicy(
               AMQPFederationPolicySupport.create(policy, federation.getWildcardConfiguration()));
         }
      }
      final Set<AMQPFederationQueuePolicyElement> remoteQueuePolicies = connectionElement.getRemoteQueuePolicies();
      if (!remoteQueuePolicies.isEmpty()) {
         for (AMQPFederationQueuePolicyElement policy : remoteQueuePolicies) {
            federation.addRemoteQueueMatchPolicy(
               AMQPFederationPolicySupport.create(policy, federation.getWildcardConfiguration()));
         }
      }

      this.brokerFederation = federation;
   }

   private void connectReceiver(ActiveMQProtonRemotingConnection protonRemotingConnection,
                                Session session,
                                AMQPSessionContext sessionContext,
                                Queue queue,
                                Symbol... capabilities) {
      logger.debug("Connecting inbound for {}", queue);

      if (session == null) {
         logger.debug("session is null");
         return;
      }

      protonRemotingConnection.getAmqpConnection().runLater(() -> {
         if (!receivers.add(queue)) {
            logger.debug("Receiver for queue {} already exists, just giving up", queue);
            return;
         }

         try {
            final String linkName = queue.getAddress().toString() + ":" + UUIDGenerator.getInstance().generateStringUUID();
            final Receiver receiver = session.receiver(linkName);
            final String queueAddress = queue.getAddress().toString();

            final Target target = new Target();
            target.setAddress(queueAddress);
            final Source source = new Source();
            source.setAddress(queueAddress);
            if (capabilities != null) {
               source.setCapabilities(capabilities);
            }

            receiver.setSenderSettleMode(SenderSettleMode.UNSETTLED);
            receiver.setReceiverSettleMode(ReceiverSettleMode.FIRST);
            receiver.setTarget(target);
            receiver.setSource(source);
            receiver.open();

            final ScheduledFuture<?> openTimeoutTask;
            final AtomicBoolean openTimedOut = new AtomicBoolean(false);

            if (getConnectionTimeout() > 0) {
               openTimeoutTask = server.getScheduledPool().schedule(() -> {
                  openTimedOut.set(true);
                  error(ActiveMQAMQPProtocolMessageBundle.BUNDLE.brokerConnectionTimeout(), lastRetryCounter);
               }, getConnectionTimeout(), TimeUnit.MILLISECONDS);
            } else {
               openTimeoutTask = null;
            }

            // Await the remote attach before creating the broker receiver in order to impose a timeout
            // on the attach response and then try and create the local server receiver context and finish
            // the wiring.
            receiver.attachments().set(AMQP_LINK_INITIALIZER_KEY, Runnable.class, () -> {
               try {
                  if (openTimeoutTask != null) {
                     openTimeoutTask.cancel(false);
                  }

                  if (openTimedOut.get()) {
                     return; // Timed out before remote attach arrived
                  }

                  if (receiver.getRemoteSource() != null) {
                     logger.trace("AMQP Broker Connection Receiver {} completed open", linkName);
                  } else {
                     logger.debug("AMQP Broker Connection Receiver {} rejected by remote", linkName);
                     error(ActiveMQAMQPProtocolMessageBundle.BUNDLE.receiverLinkRefused(queueAddress), lastRetryCounter);
                     return;
                  }

                  sessionContext.addReceiver(receiver, (r, s) -> {
                     // Returns a customized server receiver context that will respect the locally initiated state
                     // when the receiver is initialized vs the remotely sent target as we want to ensure we attach
                     // the receiver to the address we set in our local state.
                     return new ProtonServerReceiverContext(sessionContext.getSessionSPI(),
                                                            sessionContext.getAMQPConnectionContext(),
                                                            sessionContext, receiver) {

                        @Override
                        public void initialize() throws Exception {
                           initialized = true;
                           address = SimpleString.of(target.getAddress());
                           explicitRoutingType = queue.getRoutingType();
                           implicitRoutingType = queue.getRoutingType();

                           try {
                              // Check if the queue that triggered the attach still exists or has it been removed
                              // before the attach response arrived from the remote peer.
                              if (!sessionSPI.queueQuery(queue.getName(), queue.getRoutingType(), false).isExists()) {
                                 throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.addressDoesntExist(address.toString());
                              }
                           } catch (ActiveMQAMQPException e) {
                              receivers.remove(queue);
                              throw e;
                           } catch (Exception e) {
                              logger.debug(e.getMessage(), e);
                              receivers.remove(queue);
                              throw new ActiveMQAMQPInternalErrorException(e.getMessage(), e);
                           }

                           topUpCreditIfNeeded();
                        }
                     };
                  });
               } catch (Exception e) {
                  error(e);
               }
            });
         } catch (Exception e) {
            error(e);
         }

         protonRemotingConnection.getAmqpConnection().flush();
      });
   }

   private void connectSender(Queue queue,
                              String targetName,
                              java.util.function.Consumer<Sender> senderConsumer,
                              java.util.function.Consumer<? super MessageReference> beforeDeliver,
                              String brokerID,
                              Symbol[] desiredCapabilities,
                              Symbol[] targetCapabilities,
                              Symbol[] requiredOfferedCapabilities) {
      logger.debug("Connecting outbound for {}", queue);

      if (session == null) {
         logger.debug("Session is null");
         return;
      }

      protonRemotingConnection.getAmqpConnection().runLater(() -> {
         try {
            if (senders.contains(queue)) {
               logger.debug("Sender for queue {} already exists, just giving up", queue);
               return;
            }
            senders.add(queue);
            Sender sender = session.sender(targetName + ":" + UUIDGenerator.getInstance().generateStringUUID());
            sender.setSenderSettleMode(SenderSettleMode.UNSETTLED);
            sender.setReceiverSettleMode(ReceiverSettleMode.FIRST);
            Target target = new Target();
            target.setAddress(targetName);
            if (targetCapabilities != null) {
               target.setCapabilities(targetCapabilities);
            }
            sender.setTarget(target);

            Source source = new Source();
            source.setAddress(queue.getAddress().toString());
            sender.setSource(source);
            if (brokerID != null) {
               Map<Symbol, Object> mapProperties = new HashMap<>(1, 1); // this map is expected to have a single element, so load factor = 1
               mapProperties.put(AMQPMirrorControllerSource.BROKER_ID, brokerID);
               sender.setProperties(mapProperties);
            }

            if (desiredCapabilities != null) {
               sender.setDesiredCapabilities(desiredCapabilities);
            }

            AMQPOutgoingController outgoingInitializer = new AMQPOutgoingController(queue, sender, sessionContext.getSessionSPI());

            sender.open();

            final ScheduledFuture<?> futureTimeout;

            AtomicBoolean cancelled = new AtomicBoolean(false);

            if (getConnectionTimeout() > 0) {
               futureTimeout = server.getScheduledPool().schedule(() -> {
                  cancelled.set(true);
                  error(ActiveMQAMQPProtocolMessageBundle.BUNDLE.brokerConnectionTimeout(), lastRetryCounter);
               }, getConnectionTimeout(), TimeUnit.MILLISECONDS);
            } else {
               futureTimeout = null;
            }

            // Using attachments to set up a Runnable that will be executed inside AMQPBrokerConnection::remoteLinkOpened
            sender.attachments().set(AMQP_LINK_INITIALIZER_KEY, Runnable.class, () -> {
               ProtonServerSenderContext senderContext = new ProtonServerSenderContext(protonRemotingConnection.getAmqpConnection(), sender, sessionContext, sessionContext.getSessionSPI(), outgoingInitializer).setBeforeDelivery(beforeDeliver);
               try {
                  if (!cancelled.get()) {
                     if (futureTimeout != null) {
                        futureTimeout.cancel(false);
                     }
                     if (sender.getRemoteTarget() == null) {
                        error(ActiveMQAMQPProtocolMessageBundle.BUNDLE.senderLinkRefused(sender.getTarget().getAddress()), lastRetryCounter);
                        return;
                     }
                     if (requiredOfferedCapabilities != null) {
                        if (!verifyOfferedCapabilities(sender, requiredOfferedCapabilities)) {
                           error(ActiveMQAMQPProtocolMessageBundle.BUNDLE.missingOfferedCapability(Arrays.toString(requiredOfferedCapabilities)), lastRetryCounter);
                           return;
                        }
                     }
                     if (brokerID != null) {
                        if (sender.getRemoteProperties() == null || !sender.getRemoteProperties().containsKey(AMQPMirrorControllerSource.BROKER_ID)) {
                           error(ActiveMQAMQPProtocolMessageBundle.BUNDLE.missingBrokerID(), lastRetryCounter);
                           return;
                        }

                        Object remoteBrokerID = sender.getRemoteProperties().get(AMQPMirrorControllerSource.BROKER_ID);
                        if (remoteBrokerID.equals(brokerID)) {
                           error(ActiveMQAMQPProtocolMessageBundle.BUNDLE.brokerConnectionMirrorItself(), lastRetryCounter);
                           return;
                        }
                     }
                     sessionContext.addSender(sender, senderContext);
                     if (senderConsumer != null) {
                        senderConsumer.accept(sender);
                     }
                  }
               } catch (Exception e) {
                  error(e);
               }
            });
         } catch (Exception e) {
            error(e);
         }
         protonRemotingConnection.getAmqpConnection().flush();
      });
   }

   public void error(Throwable e) {
      error(e, 0);
   }

   /**
    * Provides an error API for resources of the broker connection that encounter errors during the normal operation of
    * the resource that represent a terminal outcome for the connection. The connection retry counter will be reset to
    * zero for these types of errors as these indicate a connection interruption that should initiate the start of a
    * reconnect cycle if reconnection is configured.
    *
    * @param error The exception that describes the terminal connection error.
    */
   public void runtimeError(Throwable error) {
      error(error, 0);
   }

   /**
    * Provides an error API for resources of the broker connection that encounter errors during the connection /
    * resource initialization phase that should constitute a terminal outcome for the connection. The connection retry
    * counter will be incremented for these types of errors which can result in eventual termination of reconnect
    * attempts when the limit is exceeded.
    *
    * @param error The exception that describes the terminal connection error.
    */
   public void connectError(Throwable error) {
      error(error, lastRetryCounter);
   }

   // the retryCounter is passed here
   // in case the error happened after the actual connection
   // say the connection is invalid due to an invalid attribute or wrong password
   // but the max retry should not be affected by such cases
   // otherwise we would always retry from 0 and never reach a max
   protected void error(Throwable e, int retryCounter) {
      this.retryCounter = retryCounter;
      connecting = false;
      logger.warn(e.getMessage(), e);
      redoConnection();
   }

   private class AMQPOutgoingController implements SenderController {

      final Queue queue;
      final Sender sender;
      final AMQPSessionCallback sessionSPI;

      protected boolean tunnelCoreMessages;

      protected AMQPMessageWriter standardMessageWriter;
      protected AMQPLargeMessageWriter largeMessageWriter;

      protected AMQPTunneledCoreMessageWriter coreMessageWriter;
      protected AMQPTunneledCoreLargeMessageWriter coreLargeMessageWriter;

      AMQPOutgoingController(Queue queue, Sender sender, AMQPSessionCallback sessionSPI) {
         this.queue = queue;
         this.sessionSPI = sessionSPI;
         this.sender = sender;
      }

      @Override
      public Consumer init(ProtonServerSenderContext senderContext) throws Exception {
         SimpleString queueName = queue.getName();

         // Did we ask for core tunneling? If so did the remote offer it in return
         tunnelCoreMessages = verifyCapabilities(sender.getDesiredCapabilities(), AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT) &&
                              verifyOfferedCapabilities(sender, AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT);

         return sessionSPI.createSender(senderContext, queueName, null, false);
      }

      @Override
      public void close(boolean remoteClose) throws Exception {

      }

      @Override
      public void close(ErrorCondition error) {
         // If remote closed already than normal broker connection link closed handling will already kick in,
         // if not then the connection should be locally force closed as it would otherwise sit in a zombie state
         // never consuming from the SNF Queue again.
         if (sender.getRemoteState() != EndpointState.CLOSED) {
            AMQPBrokerConnection.this.runtimeError(new ActiveMQAMQPInternalErrorException(
               "Broker connection mirror consumer locally closed unexpectedly: " + error.getCondition().toString()));
         }
      }

      @Override
      public MessageWriter selectOutgoingMessageWriter(ProtonServerSenderContext sender, MessageReference reference) {
         final MessageWriter selected;
         final Message message = reference.getMessage();

         if (message instanceof AMQPMessage) {
            if (message.isLargeMessage()) {
               selected = largeMessageWriter != null ? largeMessageWriter :
                  (largeMessageWriter = new AMQPLargeMessageWriter(sender));
            } else {
               selected = standardMessageWriter != null ? standardMessageWriter :
                  (standardMessageWriter = new AMQPMessageWriter(sender));
            }
         } else if (tunnelCoreMessages) {
            if (message.isLargeMessage()) {
               selected = coreLargeMessageWriter != null ? coreLargeMessageWriter :
                  (coreLargeMessageWriter = new AMQPTunneledCoreLargeMessageWriter(sender));
            } else {
               selected = coreMessageWriter != null ? coreMessageWriter :
                  (coreMessageWriter = new AMQPTunneledCoreMessageWriter(sender));
            }
         } else {
            selected = standardMessageWriter != null ? standardMessageWriter :
               (standardMessageWriter = new AMQPMessageWriter(sender));
         }

         return selected;
      }
   }

   public void disconnect() throws Exception {
      redoConnection();
   }

   @Override
   public void connectionCreated(ActiveMQComponent component, Connection connection, ClientProtocolManager protocol) {
   }

   @Override
   public void connectionDestroyed(Object connectionID, boolean failed) {
      server.getRemotingService().removeConnection(connectionID);
      redoConnection();
   }

   @Override
   public void connectionException(Object connectionID, ActiveMQException me) {
      redoConnection();
   }

   private void redoConnection() {
      // avoiding retro-feeding an error call from the close after anything else that happened.
      final ActiveMQProtonRemotingConnection remotingConnection = this.protonRemotingConnection;

      if (remotingConnection != null) {
         remotingConnection.getAmqpConnection().clearLinkRemoteCloseListeners();
      }

      final AMQPFederationSource federation = this.brokerFederation;

      if (federation != null) {
         try {
            federation.connectionInterrupted();
         } catch (ActiveMQException e) {
            logger.debug("Broker Federation on connection {} threw an error on stop before connection attempt", getName());
         }
      }

      if (bridgeManagers != null) {
         bridgeManagers.connectionInterrupted();
      }

      // we need to use the connectExecutor to initiate a redoConnection
      // otherwise we would need to add synchronized blocks along this class
      // to control when connecting becomes true and when it becomes false
      // keeping a single executor thread to this purpose would simplify things
      connectExecutor.execute(() -> {
         if (connecting) {
            logger.debug("Broker connection {} was already in retry mode, exception or retry not captured", getName());
            return;
         }
         connecting = true;

         try {
            if (protonRemotingConnection != null) {
               protonRemotingConnection.fail(new ActiveMQException("Connection being recreated"));
               connection = null;
               protonRemotingConnection = null;
            }
         } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
         }

         retryConnection();
      });
   }

   @Override
   public void connectionReadyForWrites(Object connectionID, boolean ready) {
      protonRemotingConnection.flush();
   }

   private static final String EXTERNAL = "EXTERNAL";
   private static final String PLAIN = "PLAIN";
   private static final String ANONYMOUS = "ANONYMOUS";
   private static final String XOAUTH2 = "XOAUTH2";
   private static final byte[] EMPTY = new byte[0];

   private static class PlainSASLMechanism implements ClientSASL {

      private final byte[] initialResponse;

      PlainSASLMechanism(String username, String password) {
         byte[] usernameBytes = username.getBytes(StandardCharsets.UTF_8);
         byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);
         byte[] encoded = new byte[usernameBytes.length + passwordBytes.length + 2];
         System.arraycopy(usernameBytes, 0, encoded, 1, usernameBytes.length);
         System.arraycopy(passwordBytes, 0, encoded, usernameBytes.length + 2, passwordBytes.length);
         initialResponse = encoded;
      }

      @Override
      public String getName() {
         return PLAIN;
      }

      @Override
      public byte[] getInitialResponse() {
         return initialResponse;
      }

      @Override
      public byte[] getResponse(byte[] challenge) {
         return EMPTY;
      }

      public static boolean isApplicable(final String username, final String password) {
         return username != null && !username.isEmpty() && password != null && !password.isEmpty();
      }
   }

   private static class AnonymousSASLMechanism implements ClientSASL {

      @Override
      public String getName() {
         return ANONYMOUS;
      }

      @Override
      public byte[] getInitialResponse() {
         return EMPTY;
      }

      @Override
      public byte[] getResponse(byte[] challenge) {
         return EMPTY;
      }
   }

   private static class ExternalSASLMechanism implements ClientSASL {

      @Override
      public String getName() {
         return EXTERNAL;
      }

      @Override
      public byte[] getInitialResponse() {
         return EMPTY;
      }

      @Override
      public byte[] getResponse(byte[] challenge) {
         return EMPTY;
      }

      public static boolean isApplicable(final NettyConnection connection) {
         return CertificateUtil.getLocalPrincipalFromConnection(connection) != null;
      }
   }

   private static class XOAuth2SASLMechanism implements ClientSASL {

      private final String userName;
      private final String token;

      XOAuth2SASLMechanism(String userName, String token) {
         this.userName = userName;
         this.token = token;
      }

      @Override
      public String getName() {
         return XOAUTH2;
      }

      @Override
      public byte[] getInitialResponse() {
         String response = String.format("user=%s\u0001auth=Bearer %s\u0001\u0001", userName, token);
         return response.getBytes(StandardCharsets.UTF_8);
      }

      @Override
      public byte[] getResponse(byte[] challenge) {
         return EMPTY;
      }

      public static boolean isApplicable(final String username, final String password) {
         return username != null && !username.isEmpty() && password != null && !password.isEmpty();
      }
   }

   private static final class SaslFactory implements ClientSASLFactory {

      private final NettyConnection connection;
      private final AMQPBrokerConnectConfiguration brokerConnectConfiguration;
      private final String[] enabledSaslMechanisms;

      SaslFactory(NettyConnection connection, AMQPBrokerConnectConfiguration brokerConnectConfiguration, String[] enabledSaslMechanisms) {
         this.connection = connection;
         this.brokerConnectConfiguration = brokerConnectConfiguration;
         this.enabledSaslMechanisms = enabledSaslMechanisms;
      }

      @Override
      public ClientSASL chooseMechanism(String[] offeredMechanisms) {
         Set<String> offeredMechanismsSet = filterOfferedMechanisms(offeredMechanisms, enabledSaslMechanisms);

         if (offeredMechanismsSet.contains(EXTERNAL) && ExternalSASLMechanism.isApplicable(connection)) {
            return new ExternalSASLMechanism();
         }

         if (SCRAMClientSASL.isApplicable(brokerConnectConfiguration.getUser(),
                                          brokerConnectConfiguration.getPassword())) {
            for (SCRAM scram : SCRAM.values()) {
               if (offeredMechanismsSet.contains(scram.getName())) {
                  return new SCRAMClientSASL(scram, brokerConnectConfiguration.getUser(),
                                             brokerConnectConfiguration.getPassword());
               }
            }
         }

         if (offeredMechanismsSet.contains(PLAIN) && PlainSASLMechanism.isApplicable(brokerConnectConfiguration.getUser(), brokerConnectConfiguration.getPassword())) {
            return new PlainSASLMechanism(brokerConnectConfiguration.getUser(), brokerConnectConfiguration.getPassword());
         }

         if (offeredMechanismsSet.contains(XOAUTH2) && XOAuth2SASLMechanism.isApplicable(brokerConnectConfiguration.getUser(), brokerConnectConfiguration.getPassword())) {
            return new XOAuth2SASLMechanism(brokerConnectConfiguration.getUser(), brokerConnectConfiguration.getPassword());
         }

         if (offeredMechanismsSet.contains(ANONYMOUS)) {
            return new AnonymousSASLMechanism();
         }

         return null;
      }

      private Set<String> filterOfferedMechanisms(String[] offeredSaslMechanisms, String[] enabledSaslMechanisms) {
         Set<String> offeredSaslMechanismsSet = offeredSaslMechanisms == null ? Collections.emptySet() : new HashSet<>(Arrays.asList(offeredSaslMechanisms));

         if (enabledSaslMechanisms == null || enabledSaslMechanisms.length == 0) {
            return offeredSaslMechanismsSet;
         }

         Set<String> enabledSaslMechanismsSet = new HashSet<>(Arrays.asList(enabledSaslMechanisms));
         enabledSaslMechanismsSet.retainAll(offeredSaslMechanismsSet);

         return enabledSaslMechanismsSet;
      }
   }

   public static boolean isCoreMessageTunnelingEnabled(AMQPMirrorBrokerConnectionElement configuration) {
      final Object property = configuration.getProperties().get(AmqpSupport.TUNNEL_CORE_MESSAGES);

      if (property instanceof Boolean booleanValue) {
         return booleanValue;
      } else if (property instanceof String string) {
         return Boolean.parseBoolean(string);
      } else {
         return DEFAULT_CORE_MESSAGE_TUNNELING_ENABLED;
      }
   }

   public static class NettyConnectorCloseHandler implements FailureListener, CloseListener {

      private final NettyConnector connector;
      private final Executor connectionExecutor;

      public NettyConnectorCloseHandler(NettyConnector connector, Executor connectionExecutor) {
         this.connector = connector;
         this.connectionExecutor = connectionExecutor;
      }

      @Override
      public void connectionClosed() {
         doCloseConnector();
      }

      @Override
      public void connectionFailed(ActiveMQException exception, boolean failedOver) {
         doCloseConnector();
      }

      @Override
      public void connectionFailed(ActiveMQException exception, boolean failedOver, String scaleDownTargetNodeID) {
         doCloseConnector();
      }

      private void doCloseConnector() {
         connectionExecutor.execute(() -> {
            try {
               connector.close();
            } catch (Exception ex) {
            }
         });
      }
   }
}
