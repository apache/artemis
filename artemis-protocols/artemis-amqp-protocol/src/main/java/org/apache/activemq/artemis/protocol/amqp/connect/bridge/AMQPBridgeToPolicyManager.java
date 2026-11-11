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

import java.lang.invoke.MethodHandles;

import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerBasePlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for AMQP bridge policy managers that bridge to the remote peer from
 * this peer for some configured resource.
 */
public abstract class AMQPBridgeToPolicyManager extends AMQPBridgePolicyManager implements ActiveMQServerBasePlugin {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   protected volatile AMQPBridgeSenderConfiguration configuration;

   public AMQPBridgeToPolicyManager(AMQPBridgeManager bridge, AMQPBridgeMetrics metrics, String policyName, AMQPBridgeType policyType) {
      super(bridge, metrics, policyName, policyType);
   }

   @Override
   protected void handleManagerInitialized() {
      server.registerBrokerPlugin(this);

      try {
         AMQPBridgeManagementSupport.registerBridgePolicyManager(this);
      } catch (Exception e) {
         logger.trace("Error while attempting to add sender policy control to management", e);
      }
   }

   @Override
   protected void handleManagerStarted() {
      if (isActive()) {
         scanManagedResources();
      }
   }

   @Override
   protected void handleManagerStopped() {
      safeCleanupManagerResources();
   }

   @Override
   protected void handleManagerShutdown() {
      server.unRegisterBrokerPlugin(this);

      try {
         AMQPBridgeManagementSupport.unregisterBridgePolicyManager(this);
      } catch (Exception e) {
         logger.trace("Error while attempting to remove sender policy control from management", e);
      }

      safeCleanupManagerResources();
   }

   @Override
   protected void handleConnectionInterrupted() {
      safeCleanupManagerResources();
   }

   @Override
   protected void handleConnectionRestored(AMQPBridgeConfiguration configuration) {
      // Capture state for the current connection on each connection as different URIs could have
      // different options we need to capture in the current configuration state.
      this.configuration = new AMQPBridgeSenderConfiguration(configuration, getPolicy().getProperties());

      if (isActive()) {
         scanManagedResources();
      }
   }

   /**
    * Scans all server resources and push them through the normal checks that
    * would be done on an add. This allows for checks on demand after a start or
    * after a connection is restored.
    */
   protected abstract void scanManagedResources();

   /**
    * The subclass implements this method and should remove all tracked AMQP bridge
    * resource data and also close all links immediately.
    */
   protected abstract void safeCleanupManagerResources();

}
