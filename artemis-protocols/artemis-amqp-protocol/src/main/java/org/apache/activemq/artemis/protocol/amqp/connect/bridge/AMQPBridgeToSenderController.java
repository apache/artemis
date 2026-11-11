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

package org.apache.activemq.artemis.protocol.amqp.connect.bridge;

import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.QUEUE_CAPABILITY;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.TOPIC_CAPABILITY;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.verifyCapabilities;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.verifyOfferedCapabilities;

import java.lang.invoke.MethodHandles;
import java.util.UUID;
import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPMessage;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPSessionCallback;
import org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeMetrics.SenderMetrics;
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
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.engine.Sender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A base class abstract {@link SenderController} implementation for use by bridge address and
 * queue senders that provides some common functionality used between both.
 */
public abstract class AMQPBridgeToSenderController implements SenderController {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public enum SenderRole {
      /**
       * Producer created from a remote server based on a match for configured address bridge policy.
       */
      ADDRESS_SENDER,

      /**
       * Producer created from a remote server based on a match for configured queue bridge policy.
       */
      QUEUE_SENDER
   }

   protected final AMQPBridgeToPolicyManager policyManager;
   protected final AMQPBridgeSenderConfiguration configuration;
   protected final AMQPBridgePolicy policy;
   protected final AMQPBridgeSenderInfo senderInfo;
   protected final AMQPSessionContext session;
   protected final AMQPSessionCallback sessionSPI;
   protected final AMQPBridgeManager bridgeManager;
   protected final SenderMetrics metrics;
   protected final String controllerId = UUID.randomUUID().toString();

   protected MessageWriter standardMessageWriter;
   protected MessageWriter largeMessageWriter;
   protected MessageWriter coreMessageWriter;
   protected MessageWriter coreLargeMessageWriter;
   protected ProtonServerSenderContext senderContext;
   protected ServerConsumer serverConsumer;

   protected boolean tunnelCoreMessages; // only enabled if remote offers support.

   public AMQPBridgeToSenderController(AMQPBridgeSenderInfo senderInfo, AMQPBridgeSenderConfiguration configuration, AMQPBridgeToPolicyManager policyManager, AMQPSessionContext session, SenderMetrics metrics) throws ActiveMQAMQPException {
      this.senderInfo = senderInfo;
      this.policyManager = policyManager;
      this.configuration = configuration;
      this.policy = policyManager.getPolicy();
      this.bridgeManager = policyManager.getBridgeManager();
      this.metrics = metrics;
      this.session = session;
      this.sessionSPI = session.getSessionSPI();
   }

   /**
    * {@return an enumeration describing the role of the sender controller implementation}
    */
   public abstract SenderRole getRole();

   public final long getMessagesSent() {
      return metrics.getMessagesSent();
   }

   public final ActiveMQServer getServer() {
      return session.getServer();
   }

   public final AMQPBridgeSenderInfo getSenderInfo() {
      return senderInfo;
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

   public final AMQPBridgeManager getBridgeManager() {
      return bridgeManager;
   }

   public final AMQPBridgeToPolicyManager getPolicyManager() {
      return policyManager;
   }

   @Override
   public final ServerConsumer init(ProtonServerSenderContext senderContext) throws Exception {
      final Sender sender = senderContext.getSender();
      final Source source = (Source) sender.getRemoteSource();

      if (bridgeManager == null) {
         throw new ActiveMQAMQPIllegalStateException("Cannot create a bridge link from non-bridge connection");
      }

      if (source == null) {
         throw new ActiveMQAMQPNotImplementedException("Null source lookup not supported on bridge links.");
      }

      this.senderContext = senderContext;

      // We need to check that the remote offers ability to read tunneled core messages and also
      // check if we offered it which we might not based on configuration. If neither of those is
      // true then we cannot send messages as tunneled core messages and instead convert all of
      // the outbound messages to AMQP versions.
      tunnelCoreMessages = verifyOfferedCapabilities(sender, AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT) &&
                           verifyCapabilities(sender.getDesiredCapabilities(), AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT);

      serverConsumer = createServerConsumer(senderContext);

      registerSenderManagement();

      return serverConsumer;
   }

   /**
    * The subclass must implement this and create an appropriately configured server consumer
    * based on the properties of the AMQP link and the role of the implemented sender type.
    *
    * @param senderContext
    *    The server sender context that this controller was created for.
    *
    * @return a new {@link ServerConsumer} instance that will send messages to the remote peer.
    *
    * @throws Exception if an error occurs while creating the server consumer.
    */
   protected abstract ServerConsumer createServerConsumer(ProtonServerSenderContext senderContext) throws Exception;

   @Override
   public final void close(boolean remoteClose) throws Exception {
      try {
         if (bridgeManager != null) {
            bridgeManager.removeLinkClosedInterceptor(controllerId);
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
         if (bridgeManager != null) {
            bridgeManager.removeLinkClosedInterceptor(controllerId);
         }
      } finally {
         unregisterSenderManagement();
         handleLinkLocallyClosed(error);
      }
   }

   /**
    * Subclasses should react to link local close by cleaning up resources.
    *
    * @param error
    *       The error that triggered the local close or null if no error.
    */
   protected void handleLinkLocallyClosed(ErrorCondition error) {
      // default implementation does nothing
   }

   private void registerSenderManagement() {
      try {
         AMQPBridgeManagementSupport.registerBridgeSender(this);
      } catch (Exception e) {
         logger.trace("Ignored exception while adding sender to management: ", e);
      }
   }

   private void unregisterSenderManagement() {
      try {
         AMQPBridgeManagementSupport.unregisterBridgeSender(this);
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
      private final SenderMetrics metrics;

      CountedMessageWrites(MessageWriter wrapped, SenderMetrics metrics) {
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
