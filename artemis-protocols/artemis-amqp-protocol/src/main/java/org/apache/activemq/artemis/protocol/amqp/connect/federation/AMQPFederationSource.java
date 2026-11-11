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

package org.apache.activemq.artemis.protocol.amqp.connect.federation;

import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_CONTROL_LINK;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_EVENT_LINK;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_NAME;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_V2;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_VERSION;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_CONFIGURATION;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.AMQP_LINK_INITIALIZER_KEY;

import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.protocol.amqp.connect.AMQPBrokerConnection;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPException;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPIllegalStateException;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPInternalErrorException;
import org.apache.activemq.artemis.protocol.amqp.federation.FederationConstants;
import org.apache.activemq.artemis.protocol.amqp.federation.FederationReceiveFromAddressPolicy;
import org.apache.activemq.artemis.protocol.amqp.federation.FederationReceiveFromQueuePolicy;
import org.apache.activemq.artemis.protocol.amqp.logger.ActiveMQAMQPProtocolMessageBundle;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPConnectionContext;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPSessionContext;
import org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport;
import org.apache.activemq.artemis.protocol.amqp.proton.ProtonServerSenderContext;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.DeleteOnClose;
import org.apache.qpid.proton.amqp.messaging.Source;
import org.apache.qpid.proton.amqp.messaging.Target;
import org.apache.qpid.proton.amqp.messaging.TerminusDurability;
import org.apache.qpid.proton.amqp.messaging.TerminusExpiryPolicy;
import org.apache.qpid.proton.amqp.transport.ReceiverSettleMode;
import org.apache.qpid.proton.amqp.transport.SenderSettleMode;
import org.apache.qpid.proton.engine.Connection;
import org.apache.qpid.proton.engine.Receiver;
import org.apache.qpid.proton.engine.Sender;
import org.apache.qpid.proton.engine.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is the initiating side of a broker federation that occurs over an AMQP broker connection.
 * <p>
 * This endpoint will create a control link to the remote peer that is a sender of federation commands which can be used
 * to instruct the remote to initiate federation operations back to this peer over the same connection and without the
 * need for local configuration.
 */
public class AMQPFederationSource extends AMQPFederation {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   // Capabilities we desire from the target federation instance, some can be required and others can
   // be optional, checks must be performed once the remote attaches.
   private static final Symbol[] CONTROL_LINK_DESIRED_CAPABILITIES = new Symbol[] {FEDERATION_CONTROL_LINK};

   // Capabilities set on the events links used to react to federation resources updates
   private static final Symbol[] EVENT_LINK_CAPABILITIES = new Symbol[] {FEDERATION_EVENT_LINK};

   private final AMQPBrokerConnection brokerConnection;

   // Remote policies that should be conveyed to the remote server for reciprocal federation operations.
   private final Map<String, FederationReceiveFromQueuePolicy> remoteQueueMatchPolicies = new HashMap<>();
   private final Map<String, FederationReceiveFromAddressPolicy> remoteAddressMatchPolicies = new HashMap<>();

   private final Map<String, Object> properties;

   private volatile AMQPFederationConfiguration configuration;
   private volatile AMQPFederationCapabilities capabilities;
   private volatile AMQPFederationCommandDispatcher controlLink;

   /**
    * Creates a new AMQP Federation instance that will manage the state of a single AMQP broker federation instance
    * using an AMQP broker connection as the IO channel.
    *
    * @param name       The name of this federation instance.
    * @param properties A set of optional properties that provide additional configuration.
    * @param connection The broker connection over which this federation will occur.
    */
   public AMQPFederationSource(String name, Map<String, Object> properties, AMQPBrokerConnection connection) {
      super(name, connection.getServer());

      if (properties == null || properties.isEmpty()) {
         this.properties = Collections.emptyMap();
      } else {
         this.properties = (Map<String, Object>) Collections.unmodifiableMap(new HashMap<>(properties));
      }

      this.brokerConnection = connection;
      this.brokerConnection.addLinkClosedInterceptor(getName(), this::invokeLinkClosedInterceptors);
   }

   /**
    * {@return the {@link AMQPBrokerConnection} that this federation is attached to}
    */
   public AMQPBrokerConnection getBrokerConnection() {
      return brokerConnection;
   }

   @Override
   public synchronized AMQPSessionContext getSessionContext() {
      if (!connected) {
         throw new IllegalStateException("Cannot access session while federation is not connected");
      }

      return session;
   }

   @Override
   public synchronized AMQPConnectionContext getConnectionContext() {
      if (!connected) {
         throw new IllegalStateException("Cannot access connection while federation is not connected");
      }

      return connection;
   }

   @Override
   public synchronized AMQPFederationConfiguration getConfiguration() {
      if (!connected) {
         throw new IllegalStateException("Cannot access connection while federation is not connected");
      }

      return configuration;
   }

   @Override
   public synchronized AMQPFederationCapabilities getCapabilities() {
      if (!connected) {
         throw new IllegalStateException("Cannot access connection while federation is not connected");
      }

      return capabilities;
   }

   /**
    * Adds a new {@link FederationReceiveFromQueuePolicy} entry to the set of policies that the remote end of this
    * federation will use to create demand on the this server when local demand is present.
    *
    * @param queuePolicy The policy to add to the set of configured {@link FederationReceiveFromQueuePolicy} instance.
    * @return this {@link AMQPFederationSource} instance
    */
   public synchronized AMQPFederationSource addRemoteQueueMatchPolicy(FederationReceiveFromQueuePolicy queuePolicy) {
      remoteQueueMatchPolicies.putIfAbsent(queuePolicy.getPolicyName(), queuePolicy);

      return this;
   }

   /**
    * Adds a new {@link FederationReceiveFromAddressPolicy} entry to the set of policies that the remote end of this
    * federation will use to create demand on the this server when local demand is present.
    *
    * @param addressPolicy The policy to add to the set of configured {@link FederationReceiveFromAddressPolicy}
    *                      instance.
    * @return this {@link AMQPFederationSource} instance
    */
   public synchronized AMQPFederationSource addRemoteAddressMatchPolicy(FederationReceiveFromAddressPolicy addressPolicy) {
      remoteAddressMatchPolicies.putIfAbsent(addressPolicy.getPolicyName(), addressPolicy);

      return this;
   }

   /**
    * Called by the parent broker connection when the connection has failed and this federation should tear down any
    * active resources and await a reconnect if one is allowed.
    *
    * @throws ActiveMQException if an error occurs processing the connection interrupted event
    */
   public final synchronized void connectionInterrupted() throws ActiveMQException {
      connected = false;

      final AtomicReference<Exception> errorCaught = new AtomicReference<>();

      localQueuePolicyManagers.forEach((k, v) -> {
         try {
            v.connectionInterrupted();
         } catch (Exception ex) {
            errorCaught.compareAndExchange(null, ex);
         }
      });

      localAddressPolicyManagers.forEach((k, v) -> {
         try {
            v.connectionInterrupted();
         } catch (Exception ex) {
            errorCaught.compareAndExchange(null, ex);
         }
      });

      remoteQueuePolicyManagers.forEach((k, v) -> {
         try {
            v.connectionInterrupted();
         } catch (Exception ex) {
            errorCaught.compareAndExchange(null, ex);
         }
      });

      remoteAddressPolicyManagers.forEach((k, v) -> {
         try {
            v.connectionInterrupted();
         } catch (Exception ex) {
            errorCaught.compareAndExchange(null, ex);
         }
      });

      try {
         eventDispatcher.close(false);
      } catch (Exception ex) {
         errorCaught.compareAndExchange(null, ex);
      } finally {
         eventDispatcher = null;
      }

      try {
         eventProcessor.close(null);
      } catch (Exception ex) {
         errorCaught.compareAndExchange(null, ex);
      } finally {
         eventProcessor = null;
      }

      try {
         controlLink.close(false);
      } catch (Exception ex) {
         errorCaught.compareAndExchange(null, ex);
      } finally {
         controlLink = null;
      }

      connection = null;
      session = null;

      if (errorCaught.get() != null) {
         final Exception error = errorCaught.get();
         if (error instanceof ActiveMQException exception) {
            throw exception;
         } else {
            throw (ActiveMQException) new ActiveMQException(error.getMessage()).initCause(error);
         }
      }
   }

   /**
    * Called by the parent broker connection when the connection has been established and this federation should build
    * up its active state based on the configuration.
    *
    * @param connection The new {@link Connection} that represents the currently active connection.
    * @param session    The new {@link Session} that was created for use by broker connection resources.
    * @throws ActiveMQException if an error occurs processing the connection restored event
    */
   public final synchronized void connectionRestored(AMQPConnectionContext connection, AMQPSessionContext session) throws ActiveMQException {
      final Connection protonConnection = session.getSession().getConnection();
      final org.apache.qpid.proton.engine.Record attachments = protonConnection.attachments();

      if (attachments.get(FEDERATION_INSTANCE_RECORD, AMQPFederation.class) != null) {
         throw new ActiveMQAMQPIllegalStateException("An existing federation instance was found on the connection");
      }

      this.connection = connection;
      this.session = session;
      this.configuration = new AMQPFederationConfiguration(connection, properties);

      // Assign an federation instance to the connection which incoming federation links can look for
      // to indicate this is a valid AMQP federation endpoint.
      attachments.set(FEDERATION_INSTANCE_RECORD, AMQPFederationSource.class, this);

      // Create the control link and the outcome will then dictate if the configured
      // policy managers are started or not.
      asyncCreateControlLink();
   }

   @Override
   protected void signalResourceCreateError(Exception cause) {
      brokerConnection.connectError(cause);
   }

   @Override
   protected void signalError(Exception cause) {
      brokerConnection.runtimeError(cause);
   }

   @Override
   boolean isFederationSource() {
      return true;
   }

   @Override
   void registerFederationManagement() throws Exception {
      AMQPFederationManagementSupport.registerFederationSource(this);
   }

   @Override
   void unregisterFederationManagement() throws Exception {
      AMQPFederationManagementSupport.unregisterFederationSource(this);
   }

   @Override
   void registerLocalPolicyManagement(AMQPFederationLocalPolicyManager manager) throws Exception {
      AMQPFederationManagementSupport.registerLocalPolicyOnSource(brokerConnection.getName(), manager);
   }

   @Override
   void unregisterLocalPolicyManagement(AMQPFederationLocalPolicyManager manager) throws Exception {
      AMQPFederationManagementSupport.unregisterLocalPolicyOnSource(brokerConnection.getName(), manager);
   }

   @Override
   void registerRemotePolicyManagement(AMQPFederationRemotePolicyManager manager) throws Exception {
      AMQPFederationManagementSupport.registerRemotePolicyOnSource(brokerConnection.getName(), manager);
   }

   @Override
   void unregisterRemotePolicyManagement(AMQPFederationRemotePolicyManager manager) throws Exception {
      AMQPFederationManagementSupport.unregisterRemotePolicyOnSource(brokerConnection.getName(), manager);
   }

   @Override
   void registerFederationConsumerManagement(AMQPFederationConsumer consumer) throws Exception {
      AMQPFederationManagementSupport.registerFederationSourceConsumer(brokerConnection.getName(), consumer);
   }

   @Override
   void unregisterFederationConsumerManagement(AMQPFederationConsumer consumer) throws Exception {
      AMQPFederationManagementSupport.unregisterFederationSourceConsumer(brokerConnection.getName(), consumer);
   }

   @Override
   void registerFederationProducerManagement(AMQPFederationSenderController sender) throws Exception {
      AMQPFederationManagementSupport.registerFederationSourceProducer(brokerConnection.getName(), sender);
   }

   @Override
   void unregisterFederationProducerManagement(AMQPFederationSenderController sender) throws Exception {
      AMQPFederationManagementSupport.unregisterFederationSourceProducer(brokerConnection.getName(), sender);
   }

   private void asyncCreateTargetEventsSender(AMQPFederationCommandDispatcher commandLink) {
      // If no remote policies configured then we don't need an events sender link
      // currently, if some other use is added for this link this code must be
      // removed and tests updated to expect this link to always be created.
      if (remoteAddressMatchPolicies.isEmpty() && remoteQueueMatchPolicies.isEmpty()) {
         return;
      }

      // Schedule the outgoing event link creation on the connection event loop thread.
      //
      // Eventual establishment of the outgoing events link or refusal informs this side
      // of the connection as to whether the remote side supports receiving events for
      // resources that it attempted to federate but they did not exist at the time and
      // were subsequently added or for resources that might have been later removed via
      // management and then subsequently re-added.
      //
      // Once the outcome of the event link is known then send any remote address or queue
      // federation policies so that the remote can start federation of local addresses or
      // queues to itself. This ordering prevents a race on creation of the events link
      // and any federation consumer creation from the remote.
      connection.runLater(() -> {
         if (!isStarted()) {
            return;
         }

         try {
            final Sender sender = session.getSession().sender(
               "federation-events-sender:" + getName() + ":" + server.getNodeID() + ":" + UUID.randomUUID());
            final Target target = new Target();
            final Source source = new Source();

            target.setDynamic(true);
            target.setCapabilities(new Symbol[] {AmqpSupport.TEMP_TOPIC_CAPABILITY});
            target.setDurable(TerminusDurability.NONE);
            target.setExpiryPolicy(TerminusExpiryPolicy.LINK_DETACH);
            // Set the dynamic node lifetime-policy to indicate this needs to be destroyed on close
            // we don't want event links nodes remaining once a federation connection is closed.
            final Map<Symbol, Object> dynamicNodeProperties = new HashMap<>();
            dynamicNodeProperties.put(AmqpSupport.LIFETIME_POLICY, DeleteOnClose.getInstance());
            target.setDynamicNodeProperties(dynamicNodeProperties);

            sender.setSenderSettleMode(SenderSettleMode.SETTLED);
            sender.setReceiverSettleMode(ReceiverSettleMode.FIRST);
            sender.setDesiredCapabilities(EVENT_LINK_CAPABILITIES);
            sender.setTarget(target);
            sender.setSource(source);
            sender.open();

            final ScheduledFuture<?> futureTimeout;
            final AtomicBoolean cancelled = new AtomicBoolean(false);

            if (brokerConnection.getConnectionTimeout() > 0) {
               futureTimeout = brokerConnection.getServer().getScheduledPool().schedule(() -> {
                  cancelled.set(true);
                  brokerConnection.connectError(ActiveMQAMQPProtocolMessageBundle.BUNDLE.brokerConnectionTimeout());
               }, brokerConnection.getConnectionTimeout(), TimeUnit.MILLISECONDS);
            } else {
               futureTimeout = null;
            }

            // Using attachments to set up a Runnable that will be executed inside the remote link opened handler
            sender.attachments().set(AMQP_LINK_INITIALIZER_KEY, Runnable.class, () -> {
               try {
                  if (cancelled.get()) {
                     return;
                  }

                  if (futureTimeout != null) {
                     futureTimeout.cancel(false);
                  }

                  if (sender.getRemoteTarget() == null || !AmqpSupport.verifyOfferedCapabilities(sender)) {
                     // Sender rejected or not an event link endpoint so close as we will
                     // not support sending events to the remote but otherwise will operate
                     // as normal.
                     sender.close();
                  } else {
                     session.addFederationEventDispatcher(sender);
                  }

                  // Prepare any remote policy managers for new connection operations as we
                  // are about to transmit remote policies to the peer which can result in new
                  // federation links immediately forming.

                  remoteQueuePolicyManagers.forEach((k, v) -> {
                     try {
                        v.connectionRestored();
                     } catch (Exception e) {
                        brokerConnection.error(e);
                     }
                  });
                  remoteAddressPolicyManagers.forEach((k, v) -> {
                     try {
                        v.connectionRestored();
                     } catch (Exception e) {
                        brokerConnection.error(e);
                     }
                  });

                  // Once we know whether the events support is active or not we can send
                  // the remote federation policies and allow the remote federation links
                  // to start forming.

                  remoteQueueMatchPolicies.forEach((key, policy) -> {
                     try {
                        commandLink.sendPolicy(policy);
                     } catch (Exception e) {
                        brokerConnection.error(e);
                     }
                  });

                  remoteAddressMatchPolicies.forEach((key, policy) -> {
                     try {
                        commandLink.sendPolicy(policy);
                     } catch (Exception e) {
                        brokerConnection.error(e);
                     }
                  });

               } catch (Exception e) {
                  brokerConnection.error(e);
               }
            });
         } catch (Exception e) {
            brokerConnection.error(e);
         }

         connection.flush();
      });
   }

   private void asnycCreateTargetEventsReceiver() {
      // If no local policies configured then we don't need an events receiver link
      // currently, if some other use is added for this link this code must be
      // removed and tests updated to expect this link to always be created.
      if (localAddressPolicyManagers.isEmpty() && localQueuePolicyManagers.isEmpty()) {
         return;
      }

      // Schedule the incoming event link creation on the connection event loop thread.
      //
      // Eventual establishment of the incoming event link or refusal informs this side
      // of the connection as to whether the remote will send events for addresses or
      // queues that were not present when a federation consumer attempt had failed and
      // were later added or an existing federation consumer was closed due to management
      // action and those resource are once again available for federation.
      //
      // Once the outcome of the event link is known then start all the policy managers
      // which will start federation from remote addresses and queues to this broker.
      // This ordering prevents any races around the events receiver creation and creation
      // of federation consumers on the remote.
      connection.runLater(() -> {
         if (!isStarted()) {
            return;
         }

         try {
            final Receiver receiver = session.getSession().receiver(
               "federation-events-receiver:" + getName() + ":" + server.getNodeID() + ":" + UUID.randomUUID());

            final Target target = new Target();
            final Source source = new Source();

            source.setDynamic(true);
            source.setCapabilities(new Symbol[] {AmqpSupport.TEMP_TOPIC_CAPABILITY});
            source.setDurable(TerminusDurability.NONE);
            source.setExpiryPolicy(TerminusExpiryPolicy.LINK_DETACH);
            // Set the dynamic node lifetime-policy to indicate this needs to be destroyed on close
            // we don't want event links nodes remaining once a federation connection is closed.
            final Map<Symbol, Object> dynamicNodeProperties = new HashMap<>();
            dynamicNodeProperties.put(AmqpSupport.LIFETIME_POLICY, DeleteOnClose.getInstance());
            source.setDynamicNodeProperties(dynamicNodeProperties);

            receiver.setSenderSettleMode(SenderSettleMode.SETTLED);
            receiver.setReceiverSettleMode(ReceiverSettleMode.FIRST);
            receiver.setDesiredCapabilities(EVENT_LINK_CAPABILITIES);
            receiver.setTarget(target);
            receiver.setSource(source);
            receiver.open();

            final ScheduledFuture<?> futureTimeout;
            final AtomicBoolean cancelled = new AtomicBoolean(false);

            if (brokerConnection.getConnectionTimeout() > 0) {
               futureTimeout = brokerConnection.getServer().getScheduledPool().schedule(() -> {
                  cancelled.set(true);
                  brokerConnection.connectError(ActiveMQAMQPProtocolMessageBundle.BUNDLE.brokerConnectionTimeout());
               }, brokerConnection.getConnectionTimeout(), TimeUnit.MILLISECONDS);
            } else {
               futureTimeout = null;
            }

            // Using attachments to set up a Runnable that will be executed inside the remote link opened handler
            receiver.attachments().set(AMQP_LINK_INITIALIZER_KEY, Runnable.class, () -> {
               try {
                  if (cancelled.get()) {
                     return;
                  }

                  if (futureTimeout != null) {
                     futureTimeout.cancel(false);
                  }

                  if (receiver.getRemoteSource() == null || !AmqpSupport.verifyOfferedCapabilities(receiver)) {
                     // Receiver rejected or not an event link endpoint so close as we will
                     // not be receiving events from the remote but otherwise will operate
                     // as normal.
                     receiver.close();
                  } else {
                     session.addFederationEventProcessor(receiver);
                  }

                  // Once we know whether the events support is active or not we can start the
                  // local federation policies and allow the outgoing federation links to start
                  // forming.
                  //
                  // Attempt to start the policy managers in another thread to avoid blocking the IO thread
                  scheduler.execute(() -> {
                     // Sync action with federation start / stop otherwise we could get out of sync
                     synchronized (AMQPFederationSource.this) {
                        if (isStarted()) {
                           localQueuePolicyManagers.forEach((k, v) -> v.connectionRestored());
                           localAddressPolicyManagers.forEach((k, v) -> v.connectionRestored());
                        }
                     }
                  });
               } catch (Exception e) {
                  brokerConnection.error(e);
               }
            });
         } catch (Exception e) {
            brokerConnection.error(e);
         }

         connection.flush();
      });
   }

   private void asyncCreateControlLink() {
      // Schedule the control link creation on the connection event loop thread
      // Eventual establishment of the control link indicates successful connection
      // to a remote peer that can support AMQP federation requirements.
      connection.runLater(() -> {
         try {
            final Sender sender = session.getSession().sender(
               "federation-control-link:" + getName() + ":" + server.getNodeID() + ":" + UUID.randomUUID());
            final Target target = new Target();

            // The control link should be dynamic and the node is destroyed if the connection drops
            target.setDynamic(true);
            target.setCapabilities(new Symbol[] {Symbol.valueOf("temporary-topic")});
            target.setDurable(TerminusDurability.NONE);
            target.setExpiryPolicy(TerminusExpiryPolicy.LINK_DETACH);
            // Set the dynamic node lifetime-policy to indicate this needs to be destroyed on close
            // we don't want control links remaining once a federation connection is closed.
            final Map<Symbol, Object> dynamicNodeProperties = new HashMap<>();
            dynamicNodeProperties.put(AmqpSupport.LIFETIME_POLICY, DeleteOnClose.getInstance());
            target.setDynamicNodeProperties(dynamicNodeProperties);

            // Send our local configuration data to the remote side of the control link
            // for use when creating remote federation resources.
            final Map<Symbol, Object> senderProperties = new HashMap<>();
            senderProperties.put(FEDERATION_VERSION, FEDERATION_V2);
            senderProperties.put(FEDERATION_CONFIGURATION, configuration.toConfigurationMap());
            senderProperties.put(FEDERATION_NAME, getName());

            sender.setSenderSettleMode(SenderSettleMode.UNSETTLED);
            sender.setReceiverSettleMode(ReceiverSettleMode.FIRST);
            sender.setDesiredCapabilities(CONTROL_LINK_DESIRED_CAPABILITIES);
            sender.setProperties(senderProperties);
            sender.setTarget(target);
            sender.setSource(new Source());
            sender.open();

            final ScheduledFuture<?> futureTimeout;
            final AtomicBoolean cancelled = new AtomicBoolean(false);

            if (brokerConnection.getConnectionTimeout() > 0) {
               futureTimeout = brokerConnection.getServer().getScheduledPool().schedule(() -> {
                  cancelled.set(true);
                  brokerConnection.connectError(ActiveMQAMQPProtocolMessageBundle.BUNDLE.brokerConnectionTimeout());
               }, brokerConnection.getConnectionTimeout(), TimeUnit.MILLISECONDS);
            } else {
               futureTimeout = null;
            }

            // Using attachments to set up a Runnable that will be executed inside the remote link opened handler
            sender.attachments().set(AMQP_LINK_INITIALIZER_KEY, Runnable.class, () -> {
               try {
                  if (cancelled.get()) {
                     return;
                  }

                  if (futureTimeout != null) {
                     futureTimeout.cancel(false);
                  }

                  if (sender.getRemoteTarget() == null) {
                     brokerConnection.connectError(
                        ActiveMQAMQPProtocolMessageBundle.BUNDLE.federationControlLinkRefused(sender.getName()));
                     return;
                  }

                  if (!AmqpSupport.verifyOfferedCapabilities(sender, FEDERATION_CONTROL_LINK)) {
                     brokerConnection.connectError(
                        ActiveMQAMQPProtocolMessageBundle.BUNDLE.missingOfferedCapability(FEDERATION_CONTROL_LINK.toString()));
                     return;
                  }

                  // We tag the session with the Federation marker as there could be incoming receivers created
                  // under it from a remote federation target if remote federation policies are configured. This
                  // allows the policy managers to then determine if local demand is from a federation target or
                  // not and based on configuration choose when to create remote receivers.
                  //
                  // This currently is a session global tag which means any consumer created from this session in
                  // response to remote attach of said receiver is going to get caught by the filtering but as of
                  // now we shouldn't be creating consumers other than federation consumers but if that were to
                  // change we'd either need single new session for this federation instance or a session per
                  // consumer at the extreme which then requires that the protocol handling code add the metadata
                  // during the receiver attach on the remote.
                  try {
                     session.getSessionSPI().addMetaData(FederationConstants.FEDERATION_NAME, getName());
                  } catch (ActiveMQAMQPException e) {
                     throw e;
                  } catch (Exception e) {
                     logger.trace("Exception on add of federation Metadata: ", e);
                     throw new ActiveMQAMQPInternalErrorException("Error while configuring interal session metadata");
                  }

                  controlLink = new AMQPFederationCommandDispatcher(sender, getServer(), session.getSessionSPI());

                  final ProtonServerSenderContext senderContext =
                     new ProtonServerSenderContext(connection, sender, session, session.getSessionSPI(), controlLink);

                  session.addSender(sender, senderContext);

                  capabilities = new AMQPFederationCapabilities().initialize(sender);
                  connected = true;

                  // Setup events sender link to the target if there are any remote policies and
                  // then send those polices to start remote federation.
                  asyncCreateTargetEventsSender(controlLink);

                  // Setup events receiver link from the target if there are any local policies
                  // and then start the policy managers to begin tracking local demand.
                  asnycCreateTargetEventsReceiver();
               } catch (Exception e) {
                  brokerConnection.error(e);
               }
            });
         } catch (Exception e) {
            brokerConnection.error(e);
         }

         connection.flush();
      });
   }
}
