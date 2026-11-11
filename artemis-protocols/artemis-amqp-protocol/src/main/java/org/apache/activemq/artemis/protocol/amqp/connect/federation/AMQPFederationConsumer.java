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

import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.DETACH_FORCED;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.NOT_FOUND;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.RESOURCE_DELETED;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationMetrics.ConsumerMetrics;

import java.lang.invoke.MethodHandles;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.config.TransformerConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.transformer.Transformer;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPException;
import org.apache.activemq.artemis.protocol.amqp.federation.FederationConsumer;
import org.apache.activemq.artemis.protocol.amqp.federation.FederationConsumerInfo;
import org.apache.activemq.artemis.protocol.amqp.federation.FederationConsumerInfo.Role;
import org.apache.activemq.artemis.protocol.amqp.federation.FederationReceiveFromResourcePolicy;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPConnectionContext;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPSessionContext;
import org.apache.activemq.artemis.protocol.amqp.proton.ProtonAbstractReceiver;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.Modified;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.messaging.Released;
import org.apache.qpid.proton.amqp.transport.Detach;
import org.apache.qpid.proton.engine.Link;
import org.apache.qpid.proton.engine.Receiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for AMQP Federation consumers that implements some of the common functionality.
 */
public abstract class AMQPFederationConsumer implements FederationConsumer {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   protected static final Symbol[] OUTCOMES = new Symbol[]{Accepted.DESCRIPTOR_SYMBOL, Rejected.DESCRIPTOR_SYMBOL,
                                                           Released.DESCRIPTOR_SYMBOL, Modified.DESCRIPTOR_SYMBOL};

   protected static final Modified DEFAULT_OUTCOME;
   static {
      DEFAULT_OUTCOME = new Modified();
      DEFAULT_OUTCOME.setDeliveryFailed(true);
   }

   // Sequence ID value used to keep links that would otherwise have the same name from overlapping
   // this generally occurs when a remote link detach is delayed and new demand is added before it
   // arrives resulting in an unintended link stealing scenario in the proton engine but can also
   // occur when consumers on the same queue have differing filters.
   protected static final AtomicLong LINK_SEQUENCE_ID = new AtomicLong();

   protected final AMQPFederationLocalPolicyManager manager;
   protected final AMQPFederation federation;
   protected final AMQPFederationConsumerConfiguration configuration;
   protected final FederationConsumerInfo consumerInfo;
   protected final AMQPConnectionContext connection;
   protected final AMQPSessionContext session;
   protected final Predicate<Link> remoteCloseInterceptor = this::remoteLinkClosedInterceptor;
   protected final Transformer transformer;
   protected final AtomicBoolean closed = new AtomicBoolean();
   protected final ConsumerMetrics metrics;

   protected volatile boolean initialized;
   protected ProtonAbstractReceiver receiver;
   protected Receiver protonReceiver;
   protected Consumer<AMQPFederationConsumer> remoteOpenHandler;
   protected Consumer<AMQPFederationConsumer> remoteCloseHandler;

   public AMQPFederationConsumer(AMQPFederationLocalPolicyManager manager, AMQPFederationConsumerConfiguration configuration,
                                 AMQPSessionContext session, FederationConsumerInfo consumerInfo,
                                 FederationReceiveFromResourcePolicy policy, ConsumerMetrics metrics) {
      this.manager = manager;
      this.federation = manager.getFederation();
      this.consumerInfo = consumerInfo;
      this.connection = session.getAMQPConnectionContext();
      this.session = session;
      this.configuration = configuration;
      this.metrics = metrics;

      final TransformerConfiguration transformerConfiguration = policy.getTransformerConfiguration();
      if (transformerConfiguration != null) {
         this.transformer = federation.getServer().getServiceRegistry().getFederationTransformer(policy.getPolicyName(), transformerConfiguration);
      } else {
         this.transformer = (m) -> m;
      }
   }

   /**
    * {@return the type of federation consumer being represented}
    */
   public final Role getRole() {
      return consumerInfo.getRole();
   }

   /**
    * {@return the number of messages this consumer has received from the remote during its lifetime}
    */
   public final long getMessagesReceived() {
      return metrics.getMessagesReceived();
   }

   /**
    * {@return the federation policy manager that created this consumer instance}
    */
   public AMQPFederationLocalPolicyManager getPolicyManager() {
      return manager;
   }

   /**
    * {@return the consumer configuration that was assigned to this federation consumer}
    */
   public AMQPFederationConsumerConfiguration getConfiguration() {
      return configuration;
   }

   /**
    * {@return the idle timeout value that is used applied to quiesced receivers}
    */
   public abstract int getReceiverIdleTimeout();

   @Override
   public final AMQPFederation getFederation() {
      return federation;
   }

   @Override
   public final FederationConsumerInfo getConsumerInfo() {
      return consumerInfo;
   }

   /**
    * {@return {@code true} if the consumer has previously been initialized}
    */
   public final boolean isInitialized() {
      return initialized;
   }

   /**
    * Called to initialize the AMQP federation consumer which will trigger an asynchronous task to attach the link and
    * handle all setup receiver and eventually start the flow of credit to the remote. This method should be called once
    * after the basic configuration of the consumer is complete and should not be called again after that.
    */
   public void initialize() {
      if (initialized) {
         throw new IllegalStateException("A receiver should only be initialized once");
      }

      initialized = true;
      connection.runLater(this::doCreateReceiver);
   }

   /**
    * Called during the initialization of the consumer to trigger an asynchronous link attach of the underlying AMQP
    * receiver that backs this federation consumer. The new receiver should be initialized in a started state. This
    * method executes on the connection thread and should not block. This method will be called from the thread of the
    * connection this consumer operates on.
    */
   protected abstract void doCreateReceiver();

   /**
    * Asynchronously starts a previously stopped federation consumer which should trigger a grant of credit to the
    * remote thereby allowing new incoming messages to be federated. In general the start should only happen when the
    * receiver is know to be stopped but given the asynchronous nature of the receiver handling this won't always be the
    * case, below the outcomes of various cases that could result from calls to this method. The completion methods are
    * always called from a different thread than this method is called in which means the caller should ensure that the
    * handling accounts for thread safety of those methods.
    * <p>
    * Calling start on an already closed consumer should immediately throw an {@link IllegalStateException} immediately.
    * Calling start on an non-initialized consumer should immediately throw an {@link IllegalStateException}
    * immediately.
    * <p>
    * Calling start on a stopped consumer should start the consumer and signal success to the completion. Calling start
    * on an already started consumer should simply signal success to the completion. Calling start on a stopping
    * consumer should fail the completion with an {@link IllegalStateException}. Calling start on a consumer that closes
    * while the start is in-flight should fail the completion with an {@link IllegalStateException}
    *
    * @param completion A {@link AMQPFederationAsyncCompletion} that will be notified when the stop request succeeds or
    *                   fails.
    */
   public final void startAsync(AMQPFederationAsyncCompletion<AMQPFederationConsumer> completion) {
      Objects.requireNonNull(completion, "The asynchronous completion object cannot be null");

      if (closed.get()) {
         throw new IllegalStateException("The consumer has already been closed.");
      }

      if (!initialized) {
         throw new IllegalStateException("A consumer must be initialized before a start call");
      }

      connection.runLater(() -> {
         try {
            if (receiver == null) {
               throw new IllegalStateException("The consumer was either not initialized or the receiver create failed");
            }

            receiver.start();
            completion.onComplete(this);
         } catch (Exception error) {
            completion.onException(this, error);
         }

         connection.flush();
      });
   }

   /**
    * Stops message consumption on this consumer instance but leaves the consumer in a state where it could be restarted
    * by a call to {@link #startAsync(AMQPFederationAsyncCompletion)} once the consumer enters the stopped state.
    * <p>
    * Since the request to stop can take time to complete and this method cannot block a completion must be provided by
    * the caller that will respond when the consumer has fully come to rest and all pending work is complete. Before the
    * stopped completion is signaled the state of the underlying consumer will be stopping and attempt to restart it
    * should fail until the stopped state has been reached.
    * <p>
    * The supplied {@link AMQPFederationAsyncCompletion} will be completed successfully once the underling AMQP receiver
    * has drained and pending work is completed. If the stop does not complete by the supplied timeout the completion
    * will be signaled that a failure has occurred with a {@link TimeoutException}. The completion methods are always
    * called from a different thread than this method is called in which means the caller should ensure that the
    * handling accounts for thread safety of those methods.
    *
    * @param completion A {@link AMQPFederationAsyncCompletion} that will be notified when the stop request succeeds or
    *                   fails.
    */
   public final void stopAsync(AMQPFederationAsyncCompletion<AMQPFederationConsumer> completion) {
      Objects.requireNonNull(completion, "The asynchronous completion object cannot be null");

      if (!initialized) {
         throw new IllegalStateException("A receiver must be initialized before a stop call");
      }

      connection.runLater(() -> {
         try {
            if (receiver == null) {
               throw new IllegalStateException("The consumer was either not yet initialized or the receiver create failed");
            }

            receiver.stop(configuration.getReceiverQuiesceTimeout(), (rcvr, stopped) -> {
               try {
                  if (stopped) {
                     completion.onComplete(this);
                  } else {
                     completion.onException(this, new TimeoutException("Timed out waiting for the AMQP link to stop"));
                  }
               } catch (Exception ex) {
                  logger.trace("Caught error running provided completion callback: ", ex);
               }
            });
         } catch (Exception error) {
            completion.onException(this, error);
         }

         connection.flush();
      });
   }

   /**
    * Close the federation consumer instance and cleans up its resources. This method should not block and the actual
    * resource shutdown work should occur asynchronously however the closed state should be indicated immediately and
    * any further attempts start the consumer should result in an exception being thrown.
    */
   public final void close() {
      if (closed.compareAndSet(false, true)) {
         connection.runLater(() -> {
            federation.removeLinkClosedInterceptor(consumerInfo.getId());

            if (receiver != null) {
               try {
                  receiver.close(false);
               } catch (ActiveMQAMQPException e) {
               } finally {
                  receiver = null;
               }
            }

            // Need to track the proton receiver and close it here as the default
            // context implementation doesn't do that and could result in no detach
            // being sent in some cases and possible resources leaks.
            if (protonReceiver != null) {
               try {
                  protonReceiver.close();
               } finally {
                  protonReceiver = null;
               }
            }

            connection.flush();
         });
      }
   }

   /**
    * {@return {@code true} if the receiver has already been closed}
    */
   public boolean isClosed() {
      return closed.get();
   }

   /**
    * Provides and event point for notification of the receiver having been opened successfully by the remote. This
    * handler will not be called if the remote rejects the link attach and a {@link Detach} is expected to follow.
    *
    * @param handler The handler that will be invoked when the remote opens this receiver.
    * @return this receiver instance
    */
   public final AMQPFederationConsumer setRemoteOpenHandler(Consumer<AMQPFederationConsumer> handler) {
      if (protonReceiver != null) {
         throw new IllegalStateException("Cannot set a remote opened handler after the consumer is started");
      }

      this.remoteOpenHandler = handler;
      return this;
   }

   /**
    * Provides and event point for notification of the consumer having been closed by the remote.
    *
    * @param handler The handler that will be invoked when the remote closes this consumer.
    * @return this consumer instance
    */
   public final AMQPFederationConsumer setRemoteClosedHandler(Consumer<AMQPFederationConsumer> handler) {
      if (protonReceiver != null) {
         throw new IllegalStateException("Cannot set a remote close handler after the consumer is started");
      }

      this.remoteCloseHandler = handler;
      return this;
   }

   protected final boolean remoteLinkClosedInterceptor(Link link) {
      if (link == protonReceiver && link.getRemoteCondition() != null && link.getRemoteCondition().getCondition() != null) {
         final Symbol errorCondition = link.getRemoteCondition().getCondition();

         // Cases where remote link close is not considered terminal, additional checks
         // should be added as needed for cases where the remote has closed the link either
         // during the attach or at some point later.

         if (RESOURCE_DELETED.equals(errorCondition)) {
            // Remote side manually deleted this queue.
            return true;
         } else if (NOT_FOUND.equals(errorCondition)) {
            // Remote did not have a queue that matched.
            return true;
         } else if (DETACH_FORCED.equals(errorCondition)) {
            // Remote operator forced the link to detach.
            return true;
         }
      }

      return false;
   }

   /**
    * Called from a subclass upon handling an incoming federated message from the remote.
    *
    * @param message The original message that arrived from the remote.
    */
   protected final void recordFederatedMessageReceived(Message message) {
      metrics.incrementMessagesReceived();
   }

   /**
    * Called before the message is dispatched to the broker for processing.
    *
    * @param message The message after any processing which is about to be dispatched.
    * @throws ActiveMQException if any broker plugin throws an exception during its processing.
    */
   protected final void signalPluginBeforeFederationConsumerMessageHandled(Message message) throws ActiveMQException {
      try {
         federation.getServer().callBrokerAMQPFederationPlugins((plugin) -> {
            if (plugin instanceof ActiveMQServerAMQPFederationPlugin federationPlugin) {
               federationPlugin.beforeFederationConsumerMessageHandled(this, message);
            }
         });
      } catch (ActiveMQException t) {
         ActiveMQServerLogger.LOGGER.federationPluginExecutionError("beforeFederationConsumerMessageHandled", t);
      }
   }

   /**
    * Called after the message is dispatched to the broker for processing.
    *
    * @param message The message after any processing which has been dispatched to the broker.
    * @throws ActiveMQException if any broker plugin throws an exception during its processing.
    */
   protected final void signalPluginAfterFederationConsumerMessageHandled(Message message) throws ActiveMQException {
      try {
         federation.getServer().callBrokerAMQPFederationPlugins((plugin) -> {
            if (plugin instanceof ActiveMQServerAMQPFederationPlugin federationPlugin) {
               federationPlugin.afterFederationConsumerMessageHandled(this, message);
            }
         });
      } catch (ActiveMQException t) {
         ActiveMQServerLogger.LOGGER.federationPluginExecutionError("afterFederationConsumerMessageHandled", t);
      }
   }
}
