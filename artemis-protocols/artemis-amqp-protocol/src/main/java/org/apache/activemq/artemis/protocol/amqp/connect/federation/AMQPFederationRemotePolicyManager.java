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

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPException;
import org.apache.activemq.artemis.protocol.amqp.federation.FederationType;
import org.apache.activemq.artemis.protocol.amqp.proton.ProtonServerSenderContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the remote policy manager that is interacting with this server. A remote policy manager provides a view
 * into the metrics for AMQP senders dispatching message from the local broker to the remote where the local federation
 * address and queue policy managers create receivers based on local demand.
 */
public abstract class AMQPFederationRemotePolicyManager extends AMQPFederationPolicyManager {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final List<AMQPFederationSenderController> controllers = new ArrayList<>();

   /**
    * Create the remote policy manager instance with the given configuration.
    *
    * @param federation The federation endpoint this policy manager operates within.
    * @param metrics    A metric object used to track work done under this policy
    * @param policyName The name assigned to this policy by the configuration on the remote.
    * @param policyType The type of policy being managed here, address or queue.
    */
   public AMQPFederationRemotePolicyManager(AMQPFederation federation, AMQPFederationMetrics metrics, String policyName, FederationType policyType) {
      super(federation, metrics, policyName, policyType);
   }

   /**
    * Create a new {@link AMQPFederationSenderController} instance for use by newly opened AMQP federation sender links
    * initiated from the remote broker based on federation policies that have been configured or sent to that broker
    * instance.
    *
    * @return a new {@link AMQPFederationSenderController} to be assigned to a sender context
    * @throws ActiveMQAMQPException if an error occurs while creating the controller
    */
   public final synchronized AMQPFederationSenderController newSenderController() throws ActiveMQAMQPException {
      final AMQPFederationSenderController controller = createSenderController(this::handleSenderControllerClosed);

      controllers.add(controller); // Track for cleanup on connection drop etc.

      return controller;
   }

   /**
    * Subclass creates the actual type of federation sender controller specific to that manager type.
    *
    * @param closedListener The closed listener to provide to the new sender controller.
    * @return a new {@link AMQPFederationSenderController} to be assigned to a sender context
    * @throws ActiveMQAMQPException if an error occurs while creating the controller
    */
   protected abstract AMQPFederationSenderController createSenderController(Consumer<AMQPFederationSenderController> closedListener) throws ActiveMQAMQPException;

   @Override
   protected void handleManagerInitialized() {
      try {
         federation.registerRemotePolicyManagement(this);
      } catch (Exception e) {
         logger.trace("Error while attempting to add remote policy control to management", e);
      }
   }

   @Override
   protected void handleManagerStarted() {
      // Nothing yet needed for this state transition
   }

   @Override
   protected void handleManagerStopped() {
      closeAllTrackedControllers();
   }

   @Override
   protected void handleManagerShutdown() {
      try {
         federation.unregisterRemotePolicyManagement(this);
      } catch (Exception e) {
         logger.trace("Error while attempting to add remote policy control to management", e);
      }

      closeAllTrackedControllers();
   }

   @Override
   protected void handleConnectionInterrupted() {
      closeAllTrackedControllers();
   }

   @Override
   protected void handleConnectionRestored() {
      // Nothing yet needed for this state transition
   }

   private synchronized void handleSenderControllerClosed(AMQPFederationSenderController controller) {
      controllers.remove(controller);
   }

   private void closeAllTrackedControllers() {
      final List<AMQPFederationSenderController> controllers = new ArrayList<>(this.controllers);

      for (AMQPFederationSenderController controller : controllers) {
         final ProtonServerSenderContext context = controller.getSenderContext();

         if (context != null && context.isInitialized()) {
            try {
               context.close(false);
            } catch (ActiveMQAMQPException e) {
               logger.trace("Ignored exception while closing federation sender controller on connection drop:", e);
            }
         }
      }

      this.controllers.clear();
   }
}
