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

import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.QUEUE_CAPABILITY;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.TOPIC_CAPABILITY;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.verifyOfferedCapabilities;

import java.lang.invoke.MethodHandles;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPMessage;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPSessionCallback;
import org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationMetrics.ProducerMetrics;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPException;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPIllegalStateException;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPNotImplementedException;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPLargeMessageWriter;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPMessageWriter;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPSessionContext;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPTunneledCoreLargeMessageWriter;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPTunneledCoreMessageWriter;
import org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport;
import org.apache.activemq.artemis.protocol.amqp.proton.MessageWriter;
import org.apache.activemq.artemis.protocol.amqp.proton.ProtonServerSenderContext;
import org.apache.activemq.artemis.protocol.amqp.proton.SenderController;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.Source;
import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.engine.Sender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A base class abstract {@link SenderController} implementation for use by federation address and queue senders that
 * provides some common functionality used between both.
 */
public abstract class AMQPFederationSenderController implements SenderController {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public enum Role {
      /**
       * Producer created from a remote server based on a match for configured address federation policy.
       */
      ADDRESS_PRODUCER,

      /**
       * Producer created from a remote server based on a match for configured queue federation policy.
       */
      QUEUE_PRODUCER
   }

   protected final AMQPFederationRemotePolicyManager manager;
   protected final AMQPSessionContext session;
   protected final AMQPSessionCallback sessionSPI;
   protected final AMQPFederation federation;
   protected final ProducerMetrics metrics;
   protected final String controllerId = UUID.randomUUID().toString();

   protected MessageWriter standardMessageWriter;
   protected MessageWriter largeMessageWriter;
   protected MessageWriter coreMessageWriter;
   protected MessageWriter coreLargeMessageWriter;
   protected ProtonServerSenderContext senderContext;
   protected ServerConsumer serverConsumer;

   protected boolean tunnelCoreMessages; // only enabled if remote offers support.

   protected Consumer<ErrorCondition> resourceDeletedAction;
   protected final Consumer<AMQPFederationSenderController> closedListener;

   public AMQPFederationSenderController(AMQPFederationRemotePolicyManager manager, ProducerMetrics metrics, Consumer<AMQPFederationSenderController> closedListener) throws ActiveMQAMQPException {
      this.manager = manager;
      this.federation = manager.getFederation();
      this.metrics = metrics;
      this.session = federation.getSessionContext();
      this.sessionSPI = session.getSessionSPI();
      this.closedListener = closedListener;
   }

   /**
    * {@return an enumeration describing the role of the sender controller implementation}
    */
   public abstract Role getRole();

   public final long getMessagesSent() {
      return metrics.getMessagesSent();
   }

   public final ActiveMQServer getServer() {
      return session.getServer();
   }

   public final ProtonServerSenderContext getSenderContext() {
      return senderContext;
   }

   public final ServerConsumer getServerConsumer() {
      return serverConsumer;
   }

   public final AMQPSessionContext getSessionContext() {
      return session;
   }

   public final AMQPSessionCallback getSessionCallback() {
      return sessionSPI;
   }

   public final AMQPFederation getFederation() {
      return federation;
   }

   public final AMQPFederationRemotePolicyManager getPolicyManager() {
      return manager;
   }

   @Override
   public final ServerConsumer init(ProtonServerSenderContext senderContext) throws Exception {
      final Sender sender = senderContext.getSender();
      final Source source = (Source) sender.getRemoteSource();

      if (federation == null) {
         throw new ActiveMQAMQPIllegalStateException("Cannot create a federation link from non-federation connection");
      }

      if (source == null) {
         throw new ActiveMQAMQPNotImplementedException("Null source lookup not supported on federation links.");
      }

      this.senderContext = senderContext;

      // We need to check that the remote offers ability to read tunneled core messages and
      // if not we must not send them but instead convert all messages to AMQP messages first.
      tunnelCoreMessages = verifyOfferedCapabilities(sender, AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT);

      serverConsumer = createServerConsumer(senderContext);

      registerRemoteLinkClosedInterceptor(sender);
      registerSenderManagement();

      return serverConsumer;
   }

   /**
    * The subclass must implement this and create an appropriately configured server consumer based on the properties of
    * the AMQP link and the role of the implemented sender type.
    *
    * @param senderContext The server sender context that this controller was created for.
    * @return a new {@link ServerConsumer} instance that will send messages to the remote peer
    * @throws Exception if an error occurs while creating the server consumer.
    */
   protected abstract ServerConsumer createServerConsumer(ProtonServerSenderContext senderContext) throws Exception;

   @Override
   public final void close(boolean remoteClose) throws Exception {
      try {
         if (federation != null) {
            federation.removeLinkClosedInterceptor(controllerId);
         }

         if (closedListener != null) {
            closedListener.accept(this);
         }
      } finally {
         unregisterSenderManagement();
         if (remoteClose) {
            handleLinkRemotelyClosed();
         } else {
            handleLinkLocallyClosed(null);
         }
      }
   }

   /**
    * Subclasses should react to link remote close by cleaning up any resources
    */
   protected void handleLinkRemotelyClosed() {
      // default implementation does nothing
   }

   @Override
   public final void close(ErrorCondition error) {
      try {
         if (error != null && AmqpError.RESOURCE_DELETED.equals(error.getCondition())) {
            if (resourceDeletedAction != null) {
               resourceDeletedAction.accept(error);
            }
         }

         if (federation != null) {
            federation.removeLinkClosedInterceptor(controllerId);
         }

         if (closedListener != null) {
            closedListener.accept(this);
         }
      } finally {
         unregisterSenderManagement();
         handleLinkLocallyClosed(error);
      }
   }

   /**
    * Subclasses should react to link local close by cleaning up resources.
    *
    * @param error The error that triggered the local close or null if no error.
    */
   protected void handleLinkLocallyClosed(ErrorCondition error) {
      // default implementation does nothing
   }

   private void registerSenderManagement() {
      try {
         federation.registerFederationProducerManagement(this);
      } catch (Exception e) {
         logger.trace("Ignored exception while adding sender to management: ", e);
      }
   }

   private void unregisterSenderManagement() {
      try {
         federation.unregisterFederationProducerManagement(this);
      } catch (Exception e) {
         logger.trace("Ignored exception while removing sender from management: ", e);
      }
   }

   @Override
   public final MessageWriter selectOutgoingMessageWriter(ProtonServerSenderContext sender, MessageReference reference) {
      final MessageWriter selected;
      final Message message = reference.getMessage();

      if (message instanceof AMQPMessage) {
         if (message.isLargeMessage()) {
            selected = largeMessageWriter != null ? largeMessageWriter :
               (largeMessageWriter = new CountedMessageWrites(new AMQPLargeMessageWriter(sender), metrics));
         } else {
            selected = standardMessageWriter != null ? standardMessageWriter :
               (standardMessageWriter = new CountedMessageWrites(new AMQPMessageWriter(sender), metrics));
         }
      } else if (tunnelCoreMessages) {
         if (message.isLargeMessage()) {
            selected = coreLargeMessageWriter != null ? coreLargeMessageWriter :
               (coreLargeMessageWriter = new CountedMessageWrites(new AMQPTunneledCoreLargeMessageWriter(sender), metrics));
         } else {
            selected = coreMessageWriter != null ? coreMessageWriter :
               (coreMessageWriter = new CountedMessageWrites(new AMQPTunneledCoreMessageWriter(sender), metrics));
         }
      } else {
         selected = standardMessageWriter != null ? standardMessageWriter :
            (standardMessageWriter = new CountedMessageWrites(new AMQPMessageWriter(sender), metrics));
      }

      return selected;
   }

   protected final void registerRemoteLinkClosedInterceptor(Sender protonSender) {
      Objects.requireNonNull(federation, "Initialization should have validated federation state before adding an interceptor");

      federation.addLinkClosedInterceptor(controllerId, (link) -> {
         // Normal close from remote due to demand being removed is handled here but remote close with an error is left
         // to the parent federation instance to decide on how it should be handled.
         if (link == protonSender && (link.getRemoteCondition() == null || link.getRemoteCondition().getCondition() == null)) {
            return true;
         }

         return false;
      });
   }

   protected static RoutingType getRoutingType(Source source) {
      if (source != null) {
         if (source.getCapabilities() != null) {
            for (Symbol capability : source.getCapabilities()) {
               if (TOPIC_CAPABILITY.equals(capability)) {
                  return RoutingType.MULTICAST;
               } else if (QUEUE_CAPABILITY.equals(capability)) {
                  return RoutingType.ANYCAST;
               }
            }
         }
      }

      return ActiveMQDefaultConfiguration.getDefaultRoutingType();
   }

   private static class CountedMessageWrites implements MessageWriter {

      private final MessageWriter wrapped;
      private final ProducerMetrics metrics;

      CountedMessageWrites(MessageWriter wrapped, ProducerMetrics metrics) {
         this.wrapped = wrapped;
         this.metrics = metrics;
      }

      @Override
      public void close() {
         wrapped.close();
      }

      @Override
      public MessageWriter open(MessageReference reference) {
         wrapped.open(reference);
         return this;
      }

      @Override
      public boolean isWriting() {
         return wrapped.isWriting();
      }

      @Override
      public void writeBytes(MessageReference messageReference) {
         try {
            wrapped.writeBytes(messageReference);
         } finally {
            metrics.incrementMessagesSent();
         }
      }
   }
}
