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

import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.DETACH_FORCED;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.NOT_FOUND;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.RESOURCE_DELETED;

import java.io.Closeable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeMetrics.SenderMetrics;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPException;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPConnectionContext;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPSessionContext;
import org.apache.activemq.artemis.protocol.amqp.proton.ProtonServerSenderContext;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.transport.Detach;
import org.apache.qpid.proton.engine.Link;
import org.apache.qpid.proton.engine.Sender;

/**
 * Base implementation for AMQP Bridge sender implementations
 */
public abstract class AMQPBridgeSender implements Closeable {

   // Sequence ID value used to keep links that would otherwise have the same name from overlapping
   // this generally occurs when a remote link detach is delayed and new resources are added before it
   // arrives resulting in an unintended link stealing scenario in the proton engine.
   protected static final AtomicLong LINK_SEQUENCE_ID = new AtomicLong();

   protected final AMQPBridgeManager bridgeManager;
   protected final AMQPBridgeToPolicyManager policyManager;
   protected final AMQPBridgeSenderConfiguration configuration;
   protected final AMQPBridgeSenderInfo senderInfo;
   protected final AMQPBridgePolicy policy;
   protected final AMQPConnectionContext connection;
   protected final AMQPSessionContext session;
   protected final SenderMetrics metrics;
   protected final AtomicBoolean closed = new AtomicBoolean();

   protected ProtonServerSenderContext senderContext;
   protected Sender protonSender;
   protected volatile boolean initialized;
   protected Consumer<AMQPBridgeSender> remoteOpenHandler;
   protected Consumer<AMQPBridgeSender> remoteCloseHandler;

   public AMQPBridgeSender(AMQPBridgeToPolicyManager policyManager,
                           AMQPBridgeSenderConfiguration configuration,
                           AMQPSessionContext session,
                           AMQPBridgeSenderInfo senderInfo,
                           SenderMetrics metrics) {
      this.policyManager = policyManager;
      this.bridgeManager = policyManager.getBridgeManager();
      this.senderInfo = senderInfo;
      this.policy = policyManager.getPolicy();
      this.connection = session.getAMQPConnectionContext();
      this.session = session;
      this.configuration = configuration;
      this.metrics = metrics;
   }

   public boolean isClosed() {
      return closed.get();
   }

   /**
    * {@return <code>true</code> if the receiver has previously been initialized}
    */
   public final boolean isInitialized() {
      return initialized;
   }

   /**
    * Called to initialize the AMQP bridge sender which will trigger an asynchronous
    * task to attach the link and handle all setup sender and eventually start the flow
    * of credit to the remote. This method should be called once after the basic configuration
    * of the sender is complete and should not be called again after that.
    */
   public void initialize() {
      if (initialized) {
         throw new IllegalStateException("A sender should only be initialized once");
      }

      initialized = true;
      connection.runLater(this::doCreateSender);
   }

   /**
    * Close the AMQP bridge sender instance and cleans up its resources. This method
    * should not block and the actual resource shutdown work should occur asynchronously.
    */
   @Override
   public final synchronized void close() {
      if (closed.compareAndSet(false, true)) {
         connection.runNow(() -> {
            if (senderContext != null) {
               try {
                  senderContext.close(null);
               } catch (ActiveMQAMQPException e) {
               } finally {
                  senderContext = null;
               }
            }

            // Need to track the proton senderContext and close it here as the default
            // context implementation doesn't do that and could result in no detach
            // being sent in some cases and possible resources leaks.
            if (protonSender != null) {
               try {
                  protonSender.close();
               } finally {
                  protonSender = null;
               }
            }

            connection.flush();
         });
      }
   }

   /**
    * {@return the policy that this sender was configured to use}
    */
   public AMQPBridgePolicy getPolicy() {
      return policy;
   }

   /**
    * {@return the {@link AMQPBridgeManager} that this sender operates under}
    */
   public final AMQPBridgeManager getBridgeManager() {
      return bridgeManager;
   }

   /**
    * {@return the {@link AMQPBridgeToPolicyManager} that this sender operates under}
    */
   public AMQPBridgeToPolicyManager getPolicyManager() {
      return policyManager;
   }

   /**
    * {@return the {@link AMQPBridgeSenderConfiguration} that this sender operates under}
    */
   public AMQPBridgeSenderConfiguration getConfiguration() {
      return configuration;
   }

   /**
    * {@return an information object that defines the characteristics of the {@link AMQPBridgeSender}}
    */
   public final AMQPBridgeSenderInfo getSenderInfo() {
      return senderInfo;
   }

   /**
    * Provides and event point for notification of the sender having been opened successfully
    * by the remote. This handler will not be called if the remote rejects the link attach and
    * a {@link Detach} is expected to follow.
    *
    * @param handler
    *    The handler that will be invoked when the remote opens this sender.
    *
    * @return this sender instance.
    */
   public final AMQPBridgeSender setRemoteOpenHandler(Consumer<AMQPBridgeSender> handler) {
      if (initialized) {
         throw new IllegalStateException("Cannot set a remote open handler after the senderContext is initialized");
      }

      this.remoteOpenHandler = handler;
      return this;
   }

   /**
    * Provides and event point for notification of the sender having been closed by
    * the remote.
    *
    * @param handler
    *    The handler that will be invoked when the remote closes this sender.
    *
    * @return this sender instance.
    */
   public final AMQPBridgeSender setRemoteClosedHandler(Consumer<AMQPBridgeSender> handler) {
      if (initialized) {
         throw new IllegalStateException("Cannot set a remote close handler after the senderContext is initialized");
      }

      this.remoteCloseHandler = handler;
      return this;
   }

   /**
    * Handles the create of the actual AMQP sender link on the connection thread.
    */
   protected abstract void doCreateSender();

   protected final Symbol[] getRemoteTerminusCapabilities() {
      if (policy.getRemoteTerminusCapabilities() != null && !policy.getRemoteTerminusCapabilities().isEmpty()) {
         return policy.getRemoteTerminusCapabilities().toArray(new Symbol[0]);
      } else {
         return null;
      }
   }

   protected final boolean remoteLinkClosedInterceptor(Link link) {
      if (link == protonSender && link.getRemoteCondition() != null && link.getRemoteCondition().getCondition() != null) {
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
}
