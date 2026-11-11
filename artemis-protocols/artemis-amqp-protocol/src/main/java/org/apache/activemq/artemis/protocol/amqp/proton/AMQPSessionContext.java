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

import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederation.FEDERATION_INSTANCE_RECORD;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_CONFIGURATION;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_NAME;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_POLICY_NAME;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.protocol.amqp.connect.AMQPRemoteBrokerConnection;
import org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederation;
import org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationCapabilities;
import org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationCommandProcessor;
import org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConfiguration;
import org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationEventDispatcher;
import org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationEventProcessor;
import org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationRemoteAddressPolicyManager;
import org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationRemoteQueuePolicyManager;
import org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationTarget;
import org.apache.activemq.artemis.protocol.amqp.connect.mirror.AMQPMirrorControllerTarget;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPSessionCallback;
import org.apache.activemq.artemis.protocol.amqp.client.ProtonClientSenderContext;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPException;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPIllegalStateException;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPInternalErrorException;
import org.apache.activemq.artemis.protocol.amqp.proton.transaction.ProtonTransactionHandler;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.transaction.Coordinator;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.engine.Connection;
import org.apache.qpid.proton.engine.EndpointState;
import org.apache.qpid.proton.engine.Receiver;
import org.apache.qpid.proton.engine.Sender;
import org.apache.qpid.proton.engine.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class AMQPSessionContext extends ProtonInitializable {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
   protected final AMQPConnectionContext connection;

   protected final AMQPSessionCallback sessionSPI;

   protected final Session session;

   protected Map<Receiver, ProtonAbstractReceiver> receivers = new ConcurrentHashMap<>();

   protected Map<Sender, ProtonServerSenderContext> senders = new ConcurrentHashMap<>();

   protected boolean closed = false;

   protected final AmqpTransferTagGenerator tagCache = new AmqpTransferTagGenerator();

   protected final ActiveMQServer server;

   public AMQPSessionContext(AMQPSessionCallback sessionSPI, AMQPConnectionContext connection, Session session, ActiveMQServer server) {
      this.connection = connection;
      this.sessionSPI = sessionSPI;
      this.session = session;
      this.server = server;
   }

   protected Map<Object, ProtonServerSenderContext> serverSenders = new ConcurrentHashMap<>();

   public AMQPSessionCallback getSessionSPI() {
      return sessionSPI;
   }

   public AMQPConnectionContext getAMQPConnectionContext() {
      return connection;
   }

   public Session getSession() {
      return session;
   }

   public ActiveMQServer getServer() {
      return server;
   }

   @Override
   public void initialize() throws Exception {
      if (!isInitialized()) {
         initialized = true;

         if (sessionSPI != null) {
            try {
               sessionSPI.init(this, connection.getSASLResult());
            } catch (ActiveMQSecurityException e) {
               throw e;
            } catch (Exception e) {
               throw new ActiveMQAMQPInternalErrorException(e.getMessage(), e);
            }
         }
      }
   }

   public void disconnect(Object consumer, String queueName) {
      ProtonServerSenderContext protonConsumer = senders.remove(consumer);
      if (protonConsumer != null) {
         serverSenders.remove(protonConsumer.getBrokerConsumer());

         try {
            protonConsumer.close(false);
         } catch (ActiveMQAMQPException e) {
            protonConsumer.getSender().setTarget(null);
            protonConsumer.getSender().setCondition(new ErrorCondition(e.getAmqpError(), e.getMessage()));
         }
      }
   }

   public byte[] getTag() {
      return tagCache.getNextTag();
   }

   public void replaceTag(byte[] tag) {
      tagCache.returnTag(tag);
   }

   public void close() {
      if (closed) {
         return;
      }

      // Making a copy to avoid ConcurrentModificationException during the iteration
      Set<ProtonAbstractReceiver> receiversCopy = new HashSet<>();
      receiversCopy.addAll(receivers.values());

      for (ProtonAbstractReceiver protonProducer : receiversCopy) {
         try {
            protonProducer.close(false);
         } catch (Exception e) {
            logger.warn(e.getMessage(), e);
         }
      }
      receivers.clear();

      Set<ProtonServerSenderContext> protonSendersClone = new HashSet<>();
      protonSendersClone.addAll(senders.values());

      for (ProtonServerSenderContext protonConsumer : protonSendersClone) {
         try {
            protonConsumer.close(false);
         } catch (Exception e) {
            logger.warn(e.getMessage(), e);
         }
      }
      senders.clear();
      serverSenders.clear();
      try {
         if (sessionSPI != null) {
            sessionSPI.close();
         }
      } catch (Exception e) {
         logger.warn(e.getMessage(), e);
      }
      closed = true;

      connection.runNow(() -> {
         // Only close proactively if the connection and session are active to avoid introducing
         // changes in behaviors around normal remote close and some other local close pathways
         // that can trip over themselves if this closes a session on a closed connection before
         // a flush of the engine.
         if (session.getConnection().getLocalState() == EndpointState.ACTIVE && session.getLocalState() == EndpointState.ACTIVE) {
            try {
               session.close();
            } finally {
               connection.flush();
            }
         }
      });
   }

   public void removeReceiver(Receiver receiver) {
      receivers.remove(receiver);
   }

   public void addTransactionHandler(Coordinator coordinator, Receiver receiver) {
      ProtonTransactionHandler transactionHandler = new ProtonTransactionHandler(sessionSPI, connection);

      coordinator.setCapabilities(Symbol.getSymbol("amqp:local-transactions"), Symbol.getSymbol("amqp:multi-txns-per-ssn"), Symbol.getSymbol("amqp:multi-ssns-per-txn"));

      receiver.setContext(transactionHandler);
      connection.runNow(() -> {
         receiver.open();
         receiver.flow(connection.getAmqpCredits());
         connection.flush();
      });
   }

   public void addFederationEventDispatcher(Sender sender) throws Exception {
      addSender(sender, (c, s) -> {
         final Connection protonConnection = sender.getSession().getConnection();
         final org.apache.qpid.proton.engine.Record attachments = protonConnection.attachments();
         final AMQPFederation federation = attachments.get(FEDERATION_INSTANCE_RECORD, AMQPFederation.class);

         try {
            if (federation == null) {
               throw new ActiveMQAMQPIllegalStateException(
                  "Unexpected federation event processor opened on connection without a federation instance active");
            }

            final AMQPFederationEventDispatcher senderController =
               new AMQPFederationEventDispatcher(federation, this.getSessionSPI(), sender);

            return new ProtonServerSenderContext(connection, sender, this, this.getSessionSPI(), senderController);
         } catch (ActiveMQException e) {
            final ActiveMQAMQPException cause;

            if (e instanceof ActiveMQAMQPException activeMQAMQPException) {
               cause = activeMQAMQPException;
            } else {
               cause = new ActiveMQAMQPInternalErrorException(e.getMessage());
            }

            throw new RuntimeException(e.getMessage(), cause);
         }
      });
   }

   public void addFederationAddressSender(Sender sender) throws Exception {
      addSender(sender, (c, s) -> {
         final Connection protonConnection = sender.getSession().getConnection();
         final org.apache.qpid.proton.engine.Record attachments = protonConnection.attachments();
         final AMQPFederation federation = attachments.get(FEDERATION_INSTANCE_RECORD, AMQPFederation.class);

         try {
            if (federation == null) {
               throw new ActiveMQAMQPIllegalStateException(
                  "Unexpected federation Address sender opened on connection without a federation instance active");
            }

            final String policyName;

            if (sender.getRemoteProperties() != null &&
                sender.getRemoteProperties().get(FEDERATION_POLICY_NAME) != null) {
               policyName = sender.getRemoteProperties().get(FEDERATION_POLICY_NAME).toString();
            } else {
               policyName = AMQPFederationRemoteAddressPolicyManager.DEFAULT_REMOTE_ADDRESS_POLICY_NAME;
            }

            final AMQPFederationRemoteAddressPolicyManager policyManager = federation.getRemoteAddressPolicyManager(policyName);

            return new ProtonServerSenderContext(connection, sender, this, this.getSessionSPI(), policyManager.newSenderController());
         } catch (ActiveMQException e) {
            final ActiveMQAMQPException cause;

            if (e instanceof ActiveMQAMQPException) {
               cause = (ActiveMQAMQPException) e;
            } else {
               cause = new ActiveMQAMQPInternalErrorException(e.getMessage());
            }

            throw new RuntimeException(e.getMessage(), cause);
         }
      });
   }

   public void addFederationQueueSender(Sender sender) throws Exception {
      addSender(sender, (c, s) -> {
         final Connection protonConnection = sender.getSession().getConnection();
         final org.apache.qpid.proton.engine.Record attachments = protonConnection.attachments();
         final AMQPFederation federation = attachments.get(FEDERATION_INSTANCE_RECORD, AMQPFederation.class);

         try {
            if (federation == null) {
               throw new ActiveMQAMQPIllegalStateException(
                  "Unexpected federation Queue sender opened on connection without a federation instance active");
            }

            final String policyName;

            if (sender.getRemoteProperties() != null &&
                sender.getRemoteProperties().get(FEDERATION_POLICY_NAME) != null) {
               policyName = sender.getRemoteProperties().get(FEDERATION_POLICY_NAME).toString();
            } else {
               policyName = AMQPFederationRemoteQueuePolicyManager.DEFAULT_REMOTE_QUEUE_POLICY_NAME;
            }

            final AMQPFederationRemoteQueuePolicyManager policyManager = federation.getRemoteQueuePolicyManager(policyName);

            return new ProtonServerSenderContext(connection, sender, this, this.getSessionSPI(), policyManager.newSenderController());
         } catch (ActiveMQException e) {
            final ActiveMQAMQPException cause;

            if (e instanceof ActiveMQAMQPException) {
               cause = (ActiveMQAMQPException) e;
            } else {
               cause = new ActiveMQAMQPInternalErrorException(e.getMessage());
            }

            throw new RuntimeException(e.getMessage(), cause);
         }
      });
   }

   public void addSender(Sender sender) throws Exception {
      addSender(sender, (c, s) -> {
         return new ProtonServerSenderContext(connection, sender, this, sessionSPI, null);
      });
   }

   public void addSender(Sender sender, SenderController senderController) throws Exception {
      addSender(sender, (c, s) -> {
         final boolean outgoing = (sender.getContext() != null && sender.getContext().equals(true));

         final ProtonServerSenderContext protonSender = outgoing ?
            new ProtonClientSenderContext(connection, sender, this, sessionSPI) :
            new ProtonServerSenderContext(connection, sender, this, sessionSPI, senderController);

         return protonSender;
      });
   }

   public void addSender(Sender sender, ProtonServerSenderContext protonSender) throws Exception {
      addSender(sender, (c, s) -> {
         return protonSender;
      });
   }

   @SuppressWarnings("unchecked")
   public <T extends ProtonServerSenderContext> T addSender(Sender sender, BiFunction<AMQPSessionContext, Sender, T> senderBuilder) throws Exception {
      ProtonServerSenderContext protonSender = null;

      try {
         try {
            protonSender = senderBuilder.apply(this, sender);
         } catch (RuntimeException e) {
            if (e.getCause() instanceof ActiveMQAMQPException) {
               throw (ActiveMQAMQPException) e.getCause();
            } else if (e.getCause() != null) {
               throw new ActiveMQAMQPInternalErrorException(e.getCause().getMessage(), e.getCause());
            } else {
               throw new ActiveMQAMQPInternalErrorException(e.getMessage(), e);
            }
         }

         protonSender.initialize();

         senders.put(sender, protonSender);
         serverSenders.put(protonSender.getBrokerConsumer(), protonSender);
         sender.setContext(protonSender);

         if (sender.getLocalState() != EndpointState.ACTIVE) {
            connection.runNow(() -> {
               sender.open();
               connection.flush();
            });
         }

         protonSender.start();

         return (T) protonSender;
      } catch (ActiveMQAMQPException e) {
         senders.remove(sender);
         if (protonSender != null && protonSender.getBrokerConsumer() != null) {
            serverSenders.remove(protonSender.getBrokerConsumer());
         }
         sender.setSource(null);
         sender.setCondition(new ErrorCondition(e.getAmqpError(), e.getMessage()));
         connection.runNow(() -> {
            sender.close();
            connection.flush();
         });

         return null;
      }
   }

   public void removeSender(Sender sender) throws ActiveMQAMQPException {
      ProtonServerSenderContext senderRemoved = senders.remove(sender);
      if (senderRemoved != null) {
         serverSenders.remove(senderRemoved.getBrokerConsumer());
      }
   }

   public void addReplicaTarget(Receiver receiver) throws Exception {
      addReceiver(receiver, (r, s) -> {
         final AMQPMirrorControllerTarget protonReceiver =
            new AMQPMirrorControllerTarget(sessionSPI, connection, this, receiver, server);

         return protonReceiver;
      });
   }

   public void addFederationEventProcessor(Receiver receiver) throws Exception {
      addReceiver(receiver, (r, s) -> {
         final Connection protonConnection = receiver.getSession().getConnection();
         final org.apache.qpid.proton.engine.Record attachments = protonConnection.attachments();
         final AMQPFederation federation = attachments.get(FEDERATION_INSTANCE_RECORD, AMQPFederation.class);

         try {
            if (federation == null) {
               throw new ActiveMQAMQPIllegalStateException(
                  "Unexpected federation event processor opened on connection without a federation instance active");
            }

            final AMQPFederationEventProcessor eventsProcessor =
               new AMQPFederationEventProcessor(federation, this, receiver);

            return eventsProcessor;
         } catch (ActiveMQException e) {
            final ActiveMQAMQPException cause;

            if (e instanceof ActiveMQAMQPException activeMQAMQPException) {
               cause = activeMQAMQPException;
            } else {
               cause = new ActiveMQAMQPInternalErrorException(e.getMessage());
            }

            throw new RuntimeException(e.getMessage(), cause);
         }
      });
   }

   @SuppressWarnings("unchecked")
   public void addFederationCommandProcessor(Receiver receiver) throws Exception {
      addReceiver(receiver, (r, s) -> {
         final Connection protonConnection = receiver.getSession().getConnection();
         final org.apache.qpid.proton.engine.Record attachments = protonConnection.attachments();

         try {
            if (attachments.get(FEDERATION_INSTANCE_RECORD, AMQPFederation.class) != null) {
               throw new ActiveMQAMQPIllegalStateException(
                  "Unexpected federation instance found on connection when creating control link processor");
            }

            final Map<Symbol, Object> receiverProperties = Objects.requireNonNullElseGet(receiver.getRemoteProperties(), () -> Collections.emptyMap());
            final Map<String, Object> federationConfigurationMap =
               (Map<String, Object>) receiverProperties.getOrDefault(FEDERATION_CONFIGURATION, Collections.emptyMap());

            final String remoteFederationName = (String) receiverProperties.getOrDefault(FEDERATION_NAME, receiver.getName());

            final AMQPRemoteBrokerConnection brokerConnection =
               AMQPRemoteBrokerConnection.getOrCreateRemoteBrokerConnection(server, connection, protonConnection);
            final AMQPFederationConfiguration configuration = new AMQPFederationConfiguration(connection, federationConfigurationMap);
            final AMQPFederationCapabilities capabilities = new AMQPFederationCapabilities();
            final AMQPFederationTarget federation = new AMQPFederationTarget(brokerConnection, remoteFederationName, configuration, capabilities, this);

            federation.initialize();

            brokerConnection.addFederationTarget(federation);

            final AMQPFederationCommandProcessor commandProcessor =
               new AMQPFederationCommandProcessor(federation, this, receiver);

            attachments.set(FEDERATION_INSTANCE_RECORD, AMQPFederationTarget.class, federation);

            return commandProcessor;
         } catch (ActiveMQException e) {
            final ActiveMQAMQPException cause;

            if (e instanceof ActiveMQAMQPException activeMQAMQPException) {
               cause = activeMQAMQPException;
            } else {
               cause = new ActiveMQAMQPInternalErrorException(e.getMessage());
            }

            throw new RuntimeException(e.getMessage(), cause);
         }
      });
   }

   public void addReceiver(Receiver receiver) throws Exception {
      addReceiver(receiver, (r, s) -> {
         return new ProtonServerReceiverContext(sessionSPI, connection, this, receiver);
      });
   }

   @SuppressWarnings("unchecked")
   public <T extends ProtonAbstractReceiver> T addReceiver(Receiver receiver, BiFunction<AMQPSessionContext, Receiver, T> receiverBuilder) throws Exception {
      try {
         final ProtonAbstractReceiver protonReceiver;
         try {
            protonReceiver = receiverBuilder.apply(this, receiver);
         } catch (RuntimeException e) {
            if (e.getCause() instanceof ActiveMQAMQPException) {
               throw (ActiveMQAMQPException) e.getCause();
            } else if (e.getCause() != null) {
               throw new ActiveMQAMQPInternalErrorException(e.getCause().getMessage(), e.getCause());
            } else {
               throw new ActiveMQAMQPInternalErrorException(e.getMessage(), e);
            }
         }

         protonReceiver.initialize();
         receivers.put(receiver, protonReceiver);
         sessionSPI.addProducer(receiver.getName(), receiver.getTarget().getAddress());
         receiver.setContext(protonReceiver);
         connection.runNow(() -> {
            receiver.open();
            connection.flush();
         });

         return (T) protonReceiver;
      } catch (ActiveMQAMQPException e) {
         receivers.remove(receiver);
         receiver.setTarget(null);
         receiver.setCondition(new ErrorCondition(e.getAmqpError(), e.getMessage()));
         connection.runNow(() -> {
            receiver.close();
            connection.flush();
         });

         return null;
      }
   }

   public int getReceiverCount() {
      return receivers.size();
   }

   public Map<Receiver, ProtonAbstractReceiver> getReceivers() {
      return receivers;
   }

   public int getSenderCount() {
      return senders.size();
   }
}
