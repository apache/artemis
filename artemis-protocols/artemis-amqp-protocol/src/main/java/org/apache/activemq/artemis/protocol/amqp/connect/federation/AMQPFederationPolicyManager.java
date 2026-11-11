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

import java.util.Objects;

import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.protocol.amqp.federation.Federation;
import org.apache.activemq.artemis.protocol.amqp.federation.FederationType;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPSessionContext;

/**
 * Base Federation policy manager that declares some common APIs that address or queue policy managers must provide
 * implementations for.
 */
public abstract class AMQPFederationPolicyManager {

   public enum State {
      UNINITIALIZED,
      STOPPED,
      STARTED,
      SHUTDOWN
   }

   protected final AMQPFederationMetrics metrics;
   protected final ActiveMQServer server;
   protected final AMQPFederation federation;
   protected final String policyName;
   protected final FederationType policyType;

   protected volatile State state = State.UNINITIALIZED;
   protected boolean connected;
   protected AMQPSessionContext session;

   public AMQPFederationPolicyManager(AMQPFederation federation, AMQPFederationMetrics metrics, String policyName, FederationType policyType) {
      Objects.requireNonNull(federation, "The Federation instance cannot be null");

      this.federation = federation;
      this.server = federation.getServer();
      this.policyName = policyName;
      this.policyType = policyType;
      this.metrics = metrics;
   }

   public final synchronized void initialize() {
      failIfShutdown();

      if (state.ordinal() < State.STOPPED.ordinal()) {
         state = State.STOPPED;
         handleManagerInitialized();
      }
   }

   /**
    * Start the federation policy manager which will initiate a scan of all broker bindings and create and matching
    * remote receivers. Start on a policy manager should only be called after its parent {@link Federation} is started
    * and the federation connection has been established.
    */
   public final synchronized void start() {
      if (!federation.isStarted()) {
         throw new IllegalStateException("Cannot start a federation policy manager when the federation is stopped.");
      }

      if (state == State.UNINITIALIZED) {
         throw new IllegalStateException("Federation policy manager has not been initialized");
      }

      failIfShutdown();

      if (state.ordinal() < State.STARTED.ordinal()) {
         state = State.STARTED;
         handleManagerStarted();
      }
   }

   /**
    * Stops the queue policy manager which will close any open remote receivers that are active for local queue demand.
    * Stop should generally be called whenever the parent {@link Federation} loses its connection to the remote.
    */
   public final synchronized void stop() {
      if (state == State.UNINITIALIZED) {
         throw new IllegalStateException("Federation policy manager has not been initialized");
      }

      if (state == State.STARTED) {
         state = State.STOPPED;
         handleManagerStopped();
      }
   }

   /**
    * Shutdown the manager and cleanup any in use resources.
    */
   public final synchronized void shutdown() {
      if (state.ordinal() < State.SHUTDOWN.ordinal()) {
         state = State.SHUTDOWN;
         handleManagerShutdown();
      }
   }

   /**
    * {@return {@code true} if the policy is started at the time this method was called}
    */
   public final boolean isStarted() {
      return state == State.STARTED;
   }

   /**
    * {@return the metrics instance tied to this federation policy manager instance}
    */
   public AMQPFederationMetrics getMetrics() {
      return metrics;
   }

   /**
    * {@return the federation type this policy manager implements}
    */
   public final FederationType getPolicyType() {
      return policyType;
   }

   /**
    * {@return the assigned name of the policy that is being managed}
    */
   public final String getPolicyName() {
      return policyName;
   }

   /**
    * {@return the {@link AMQPFederation} instance that owns this policy manager}
    */
   public AMQPFederation getFederation() {
      return federation;
   }

   /**
    * Called by the parent federation instance when the connection has failed and this federation should tear down any
    * active resources and await a reconnect if one is allowed.
    */
   public final synchronized void connectionInterrupted() {
      connected = false;
      handleConnectionInterrupted();
   }

   /**
    * Called by the parent federation instance when the connection has been established and this policy manager should
    * build up its active state based on the configuration.
    */
   public final synchronized void connectionRestored() {
      connected = true;
      session = federation.getSessionContext();
      handleConnectionRestored();
   }

   /**
    * Checks if shut down already and throws an {@link IllegalStateException} if so.
    */
   protected final void failIfShutdown() throws IllegalStateException {
      if (state == State.SHUTDOWN) {
         throw new IllegalStateException("The federation policy manager has already been shutdown");
      }
   }

   /**
    * This method must always be called under lock to ensure the state returned is accurate.
    *
    * @return {@code true} if the policy manager is both started and the connected state is {@code true}
    */
   protected final boolean isActive() {
      return connected && state == State.STARTED;
   }

   /**
    * This method must be called under lock to ensure the returned state is accurate.
    *
    * @return the state of the connected flag based on the last update from the connection APIs
    */
   protected final boolean isConnected() {
      return connected;
   }

   /**
    * On initialize a federation policy manager needs to perform any specific initialization actions it requires to
    * begin tracking broker resources.
    */
   protected abstract void handleManagerInitialized();

   /**
    * On start a federation policy manager needs to perform any specific startup actions it requires to begin tracking
    * broker resources.
    */
   protected abstract void handleManagerStarted();

   /**
    * On stop a federation policy manager needs to perform any specific stopped actions it requires to cease tracking
    * broker resources and cleanup.
    */
   protected abstract void handleManagerStopped();

   /**
    * On shutdown a federation policy manager needs to perform any specific shutdown actions it requires to cease
    * tracking broker resources.
    */
   protected abstract void handleManagerShutdown();

   /**
    * On connection interrupted a federation policy manager needs to perform any specific actions to pause of cleanup
    * current resources based on the connection being closed.
    */
   protected abstract void handleConnectionInterrupted();

   /**
    * On connection restoration a federation policy manager needs to perform any specific actions to resume service
    * based on a new connection having been established.
    */
   protected abstract void handleConnectionRestored();

}
