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
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.Divert;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerBindingPlugin;
import org.apache.activemq.artemis.protocol.amqp.federation.FederationConsumer;
import org.apache.activemq.artemis.protocol.amqp.federation.FederationConsumerInfo;
import org.apache.activemq.artemis.protocol.amqp.federation.FederationReceiveFromResourcePolicy;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPSessionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A federation policy manager for policies that operate on the local side of this broker connection. These policies
 * will create consumers on the remote which federate message back to this broker instance.
 */
public abstract class AMQPFederationLocalPolicyManager extends AMQPFederationPolicyManager implements ActiveMQServerBindingPlugin {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final WildcardConfiguration wildcardConfiguration;

   protected volatile AMQPFederationConsumerConfiguration configuration;
   protected volatile AMQPFederationCapabilities capabilities;

   public AMQPFederationLocalPolicyManager(AMQPFederation federation, AMQPFederationMetrics metrics, FederationReceiveFromResourcePolicy policy) throws ActiveMQException {
      super(federation, metrics, policy.getPolicyName(), policy.getPolicyType());

      this.wildcardConfiguration = federation.getWildcardConfiguration();
   }

   /**
    * {@return the immutable federation policy configuration that backs this manager}
    */
   public abstract FederationReceiveFromResourcePolicy getPolicy();

   /**
    * {@return the active configuration at this time the method is called}
    */
   protected AMQPFederationConsumerConfiguration getConfiguration() {
      return configuration;
   }

   /**
    * {@return the known connection capabilities at this time the method is called}
    */
   protected AMQPFederationCapabilities getCapabilities() {
      return capabilities;
   }

   /**
    * {@return the wild-card configuration the federation was configured with when created}
    */
   protected WildcardConfiguration getWildcardConfiguration() {
      return wildcardConfiguration;
   }

   @Override
   protected final void handleManagerInitialized() {
      server.registerBrokerPlugin(this);

      try {
         federation.registerLocalPolicyManagement(this);
      } catch (Exception e) {
         logger.trace("Error while attempting to add local policy control to management", e);
      }
   }

   @Override
   protected final void handleManagerStarted() {
      if (isActive()) {
         scanAllBindings();
      }
   }

   @Override
   protected final void handleManagerStopped() {
      safeCleanupManagerResources(false);
   }

   @Override
   protected final void handleManagerShutdown() {
      server.unRegisterBrokerPlugin(this);

      try {
         federation.unregisterLocalPolicyManagement(this);
      } catch (Exception e) {
         logger.trace("Error while attempting to remove local policy control to management", e);
      }

      safeCleanupManagerResources(false);
   }

   @Override
   protected final void handleConnectionInterrupted() {
      // Connection is gone so consumers can be cleared immediately without a stop
      safeCleanupManagerResources(true);
   }

   @Override
   protected final void handleConnectionRestored() {
      // Capture state for the current connection on each connection as different URIs could have different options we
      // need to capture in the current configuration state.
      configuration = new AMQPFederationConsumerConfiguration(federation.getConfiguration(), getPolicy().getProperties());
      capabilities = federation.getCapabilities();

      updateStateAfterConnect(configuration, session);

      if (isActive()) {
         scanAllBindings();
      }
   }

   /**
    * Allows the policy manager implementation to update internal state after (re)connection and before the
    * policy manager triggers a scan of all bindings to check for existing or new demand.
    *
    * @param configuration
    *    The updated configuration based on the current connection.
    * @param session
    *    The session that matches the current connection.
    */
   protected abstract void updateStateAfterConnect(AMQPFederationConsumerConfiguration configuration, AMQPSessionContext session);

   /**
    * Scans all bindings and push them through the normal bindings checks that would be done on an add. This allows for
    * checks on demand after a start or after a connection is restored.
    */
   protected abstract void scanAllBindings();

   /**
    * The subclass implements this method and should remove all tracked federation consumer data and also close all
    * consumers either by first safely stopping the consumer or if offline simply closing the consumer. If the force
    * flag is set to true the implementation should close the consumer without attempting to stop it by draining link
    * credit before the close.
    *
    * @param force Should the implementation simply close the consumers without attempting a stop.
    */
   protected abstract void safeCleanupManagerResources(boolean force);

   /**
    * Attempts to close a federation consumer and signals the installed federation plugin of the impending and post
    * closed state. The method will not double close a consumer as it checks the closed state. The method is
    * synchronized to allow for use in asynchronous call backs from federation consumers.
    *
    * @param federationConsuner A federation consumer to close, or null in which case no action is taken.
    */
   protected synchronized void tryCloseFederationConsumer(AMQPFederationConsumer federationConsuner) {
      if (federationConsuner != null) {
         try {
            if (!federationConsuner.isClosed()) {
               signalPluginBeforeCloseFederationConsumer(federationConsuner);
               federationConsuner.close();
               signalPluginAfterCloseFederationConsumer(federationConsuner);
            }
         } catch (Exception ignore) {
            logger.trace("Caught error on attempted close of existing federation consumer", ignore);
         }
      }
   }

   /**
    * Signal any registered plugins for this federation instance that a remote consumer is being created.
    *
    * @param info The {@link FederationConsumerInfo} that describes the remote federation consumer
    */
   protected final void signalPluginBeforeCreateFederationConsumer(FederationConsumerInfo info) {
      try {
         server.callBrokerAMQPFederationPlugins((plugin) -> {
            if (plugin instanceof ActiveMQServerAMQPFederationPlugin) {
               ((ActiveMQServerAMQPFederationPlugin) plugin).beforeCreateFederationConsumer(info);
            }
         });
      } catch (ActiveMQException t) {
         ActiveMQServerLogger.LOGGER.federationPluginExecutionError("beforeCreateFederationConsumer", t);
      }
   }

   /**
    * Signal any registered plugins for this federation instance that a remote consumer has been created.
    *
    * @param consumer The {@link FederationConsumerInfo} that describes the remote consumer
    */
   protected final void signalPluginAfterCreateFederationConsumer(FederationConsumer consumer) {
      try {
         server.callBrokerAMQPFederationPlugins((plugin) -> {
            if (plugin instanceof ActiveMQServerAMQPFederationPlugin) {
               ((ActiveMQServerAMQPFederationPlugin) plugin).afterCreateFederationConsumer(consumer);
            }
         });
      } catch (ActiveMQException t) {
         ActiveMQServerLogger.LOGGER.federationPluginExecutionError("afterCreateFederationConsumer", t);
      }
   }

   /**
    * Signal any registered plugins for this federation instance that a remote consumer is about to be closed.
    *
    * @param consumer The {@link FederationConsumer} that that is about to be closed.
    */
   protected final void signalPluginBeforeCloseFederationConsumer(FederationConsumer consumer) {
      try {
         server.callBrokerAMQPFederationPlugins((plugin) -> {
            if (plugin instanceof ActiveMQServerAMQPFederationPlugin) {
               ((ActiveMQServerAMQPFederationPlugin) plugin).beforeCloseFederationConsumer(consumer);
            }
         });
      } catch (ActiveMQException t) {
         ActiveMQServerLogger.LOGGER.federationPluginExecutionError("beforeCloseFederationConsumer", t);
      }
   }

   /**
    * Signal any registered plugins for this federation instance that a remote consumer has now been closed.
    *
    * @param consumer The {@link FederationConsumer} that that has been closed.
    */
   protected final void signalPluginAfterCloseFederationConsumer(FederationConsumer consumer) {
      try {
         server.callBrokerAMQPFederationPlugins((plugin) -> {
            if (plugin instanceof ActiveMQServerAMQPFederationPlugin) {
               ((ActiveMQServerAMQPFederationPlugin) plugin).afterCloseFederationConsumer(consumer);
            }
         });
      } catch (ActiveMQException t) {
         ActiveMQServerLogger.LOGGER.federationPluginExecutionError("afterCloseFederationConsumer", t);
      }
   }

   /**
    * Query all registered plugins for this federation instance to determine if any wish to prevent a federation
    * consumer from being created for the given resource.
    *
    * @param address The address on which the manager is intending to create a remote consumer for.
    * @return true if any registered plugin signaled that creation should be suppressed
    */
   protected final boolean isPluginBlockingFederationConsumerCreate(AddressInfo address) {
      final AtomicBoolean canCreate = new AtomicBoolean(true);

      try {
         server.callBrokerAMQPFederationPlugins((plugin) -> {
            if (plugin instanceof ActiveMQServerAMQPFederationPlugin) {
               if (canCreate.get()) {
                  canCreate.set(((ActiveMQServerAMQPFederationPlugin) plugin).shouldCreateFederationConsumerForAddress(address));
               }
            }
         });
      } catch (ActiveMQException t) {
         ActiveMQServerLogger.LOGGER.federationPluginExecutionError("shouldCreateFederationConsumerForAddress", t);
      }

      return !canCreate.get();
   }

   /**
    * Query all registered plugins for this federation instance to determine if any wish to prevent a federation
    * consumer from being created for the given Queue.
    *
    * @param divert The {@link Divert} that triggered the manager to attempt to create a remote consumer.
    * @param queue  The {@link Queue} that triggered the manager to attempt to create a remote consumer.
    * @return true if any registered plugin signaled that creation should be suppressed
    */
   protected final boolean isPluginBlockingFederationConsumerCreate(Divert divert, Queue queue) {
      final AtomicBoolean canCreate = new AtomicBoolean(true);

      try {
         server.callBrokerAMQPFederationPlugins((plugin) -> {
            if (plugin instanceof ActiveMQServerAMQPFederationPlugin) {
               if (canCreate.get()) {
                  canCreate.set(((ActiveMQServerAMQPFederationPlugin) plugin).shouldCreateFederationConsumerForDivert(divert, queue));
               }
            }
         });
      } catch (ActiveMQException t) {
         ActiveMQServerLogger.LOGGER.federationPluginExecutionError("shouldCreateFederationConsumerForDivert", t);
      }

      return !canCreate.get();
   }

   /**
    * Query all registered plugins for this federation instance to determine if any wish to prevent a federation
    * consumer from being created for the given Queue.
    *
    * @param queue The {@link Queue} that triggered the manager to attempt to create a remote consumer.
    * @return true if any registered plugin signaled that creation should be suppressed
    */
   protected final boolean isPluginBlockingFederationConsumerCreate(Queue queue) {
      final AtomicBoolean canCreate = new AtomicBoolean(true);

      try {
         server.callBrokerAMQPFederationPlugins((plugin) -> {
            if (plugin instanceof ActiveMQServerAMQPFederationPlugin) {
               if (canCreate.get()) {
                  canCreate.set(((ActiveMQServerAMQPFederationPlugin) plugin).shouldCreateFederationConsumerForQueue(queue));
               }
            }
         });
      } catch (ActiveMQException t) {
         ActiveMQServerLogger.LOGGER.federationPluginExecutionError("shouldCreateFederationConsumerForQueue", t);
      }

      return !canCreate.get();
   }
}
