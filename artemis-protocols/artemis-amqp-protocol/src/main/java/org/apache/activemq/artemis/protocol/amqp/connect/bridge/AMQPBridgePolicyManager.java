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

import java.util.Objects;

import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPSessionContext;
import org.apache.qpid.proton.engine.Session;

/**
 * Base API for a bridged resource policy manager.
 */
public abstract class AMQPBridgePolicyManager {

   public enum State {
      UNINITIALIZED,
      STOPPED,
      STARTED,
      SHUTDOWN
   }

   protected final AMQPBridgeMetrics metrics;
   protected final ActiveMQServer server;
   protected final AMQPBridgeManager bridge;
   protected final String policyName;
   protected final AMQPBridgeType policyType;

   protected volatile State state = State.UNINITIALIZED;
   protected boolean connected;
   protected AMQPSessionContext session;

   public AMQPBridgePolicyManager(AMQPBridgeManager bridge, AMQPBridgeMetrics metrics, String policyName, AMQPBridgeType policyType) {
      Objects.requireNonNull(bridge, "The bridge manager instance cannot be null");

      this.bridge = bridge;
      this.server = bridge.getServer();
      this.policyName = policyName;
      this.policyType = policyType;
      this.metrics = metrics;
   }

   /**
    * {@return <code>true</code> if the policy is started at the time this method was called}
    */
   public final boolean isStarted() {
      return state == State.STARTED;
   }

   /**
    * Returns if the policy manager has been marked as connected to the remote peer. This method
    * must be called under lock to ensure the returned state is accurate.
    *
    * @return the state of the connected flag based on the last update from the connection APIs
    */
   protected final boolean isConnected() {
      return connected;
   }

   /**
    * {@return the {@link AMQPBridgeManager} that owns this send to policy manager}
    */
   public AMQPBridgeManager getBridgeManager() {
      return bridge;
   }

   /**
    * {@return the metrics instance tied to this bridge policy manager instance}
    */
   public AMQPBridgeMetrics getMetrics() {
      return metrics;
   }

   /**
    * {@return the {@link AMQPBridgePolicy} that configured this policy manager}
    */
   public abstract AMQPBridgePolicy getPolicy();

   /**
    * {@return the bridge type this policy manager implements}
    */
   public final AMQPBridgeType getPolicyType() {
      return policyType;
   }

   /**
    * {@return the assigned name of the policy that is being managed}
    */
   public final String getPolicyName() {
      return policyName;
   }

   /**
    * Initialize the bridge policy manager creating any resource needed and
    * registering any services offered.
    */
   public final synchronized void initialize() {
      failIfShutdown();

      if (state.ordinal() < State.STOPPED.ordinal()) {
         state = State.STOPPED;
         handleManagerInitialized();
      }
   }

   /**
    * Start the bridge policy manager which will initiate a scan of all broker bindings and
    * create and matching remote senders or receivers. Start on a policy manager should only be
    * called after its parent {@link AMQPBridgeManager} is started and the bridge connection
    * has been established.
    */
   public final synchronized void start() {
      if (!bridge.isStarted()) {
         throw new IllegalStateException("Cannot start a bridge policy manager when the bridge manager is stopped.");
      }

      if (state == State.UNINITIALIZED) {
         throw new IllegalStateException("Bridge policy manager has not been initialized");
      }

      failIfShutdown();

      if (state.ordinal() < State.STARTED.ordinal()) {
         state = State.STARTED;
         handleManagerStarted();
      }
   }

   /**
    * Stops the bridge policy manager which will close any open remote senders or receivers that
    * are active for local queue demand. Stop should generally be called whenever the parent
    * {@link AMQPBridgeManager} loses its connection to the remote.
    */
   public final synchronized void stop() {
      if (state == State.UNINITIALIZED) {
         throw new IllegalStateException("Bridge policy manager has not been initialized");
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
    * Called by the parent AMQP bridge manager when the connection has failed and this AMQP policy
    * manager should tear down any active resources and await a reconnect if one is allowed.
    */
   public final synchronized void connectionInterrupted() {
      connected = false;
      handleConnectionInterrupted();
   }

   /**
    * Called by the parent AMQP bridge manager when the connection has been established and this
    * AMQP policy manager should build up its active state based on the configuration. If not
    * started when a connection is established the policy manager services should remain stopped.
    *
    * @param session
    *    The new {@link Session} that was created for use by broker connection resources.
    * @param configuration
    *    The bridge configuration that hold state relative to the new active connection.
    */
   public final synchronized void connectionRestored(AMQPSessionContext session, AMQPBridgeConfiguration configuration) {
      this.connected = true;
      this.session = session;

      handleConnectionRestored(configuration);
   }

   /**
    * Checks if shut down already and throws an {@link IllegalStateException} if so.
    */
   protected final void failIfShutdown() throws IllegalStateException {
      if (state == State.SHUTDOWN) {
         throw new IllegalStateException("The bridge policy manager has already been shutdown");
      }
   }

   /**
    * Returns <code>true</code> if the policy manager is both started and marked as connected to
    * the remote peer. This method must always be called under lock to ensure the state returned
    * is accurate.
    *
    * @return <code>true</code> if the manager is both started and the connected state is <code>true</code>
    */
   protected final boolean isActive() {
      return connected && state == State.STARTED;
   }

   /**
    * On initialize a bridge policy manager needs to perform any specific initialization actions
    * it requires to begin tracking broker resources.
    */
   protected abstract void handleManagerInitialized();

   /**
    * On start a bridge policy manager needs to perform any specific startup actions
    * it requires to begin tracking broker resources.
    */
   protected abstract void handleManagerStarted();

   /**
    * On stop a bridge policy manager needs to perform any specific stopped actions
    * it requires to cease tracking broker resources and cleanup.
    */
   protected abstract void handleManagerStopped();

   /**
    * On shutdown a bridge policy manager needs to perform any specific shutdown actions
    * it requires to cease tracking broker resources.
    */
   protected abstract void handleManagerShutdown();

   /**
    * On connection interrupted a bridge policy manager needs to perform any specific
    * actions to pause of cleanup current resources based on the connection being closed.
    */
   protected abstract void handleConnectionInterrupted();

   /**
    * On connection restoration a bridge policy manager needs to perform any specific
    * actions to resume service based on a new connection having been established.
    *
    * @param configuration
    *    The bridge configuration relative to the currently established connection.
    */
   protected abstract void handleConnectionRestored(AMQPBridgeConfiguration configuration);

}
