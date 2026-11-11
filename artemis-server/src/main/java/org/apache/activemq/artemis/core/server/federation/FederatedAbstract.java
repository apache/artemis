/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.server.federation;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.apache.activemq.artemis.core.config.federation.FederationTransformerConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.federation.FederatedQueueConsumerImpl.ClientSessionCallback;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerBasePlugin;
import org.apache.activemq.artemis.core.server.transformer.Transformer;

import static org.apache.activemq.artemis.core.config.WildcardConfiguration.DEFAULT_WILDCARD_CONFIGURATION;

public abstract class FederatedAbstract implements ActiveMQServerBasePlugin {

   protected final Federation federation;
   protected ActiveMQServer server;
   protected FederationUpstream upstream;
   protected WildcardConfiguration wildcardConfiguration;
   protected final Map<FederatedConsumerKey, FederatedQueueConsumer> remoteQueueConsumers = new HashMap<>();
   private boolean started;

   public FederatedAbstract(Federation federation, ActiveMQServer server, FederationUpstream upstream) {
      this.federation = federation;
      this.server = server;
      this.upstream = upstream;
      this.wildcardConfiguration = Objects.requireNonNullElse(server.getConfiguration().getWildcardConfiguration(), DEFAULT_WILDCARD_CONFIGURATION);
   }

   /**
    * The plugin has been registered with the server
    *
    * @param server The ActiveMQServer the plugin has been registered to
    */
   @Override
   public void registered(ActiveMQServer server) {
      start();
   }

   /**
    * The plugin has been unregistered with the server
    *
    * @param server The ActiveMQServer the plugin has been unregistered to
    */
   @Override
   public void unregistered(ActiveMQServer server) {
      stop();
   }

   public synchronized void stop() {
      for (FederatedQueueConsumer remoteQueueConsumer : remoteQueueConsumers.values()) {
         remoteQueueConsumer.close();
      }
      remoteQueueConsumers.clear();
      started = false;
   }

   public synchronized void start() {
      started = true;
   }

   public boolean isStarted() {
      return started;
   }

   protected Transformer mergeTransformers(Transformer left, Transformer right) {
      if (left == null) {
         return right;
      } else if (right == null) {
         return left;
      } else {
         return (m) -> left.transform(right.transform(m));
      }
   }

   protected Transformer getTransformer(String transformerRef) {
      Transformer transformer = null;
      if (transformerRef != null) {
         FederationTransformerConfiguration federationTransformerConfiguration = federation.getConfig().getTransformerConfigurationMap().get(transformerRef);
         if (federationTransformerConfiguration != null) {
            transformer = server.getServiceRegistry().getFederationTransformer(federationTransformerConfiguration.getName(), federationTransformerConfiguration.getTransformerConfiguration());
         }
      }
      return transformer;
   }

   public synchronized void createRemoteConsumer(FederatedConsumerKey key, Transformer transformer, ClientSessionCallback callback) {
      if (started) {
         FederatedQueueConsumer remoteQueueConsumer = remoteQueueConsumers.get(key);
         if (remoteQueueConsumer == null) {
            if (server.hasBrokerFederationPlugins()) {
               try {
                  server.callBrokerFederationPlugins(plugin -> plugin.beforeCreateFederatedQueueConsumer(key));
               } catch (ActiveMQException t) {
                  ActiveMQServerLogger.LOGGER.federationPluginExecutionError("beforeCreateFederatedQueueConsumer", t);
                  throw new IllegalStateException(t.getMessage(), t.getCause());
               }
            }
            remoteQueueConsumer = new FederatedQueueConsumerImpl(federation, server, transformer, key, upstream, callback);
            remoteQueueConsumer.start();
            remoteQueueConsumers.put(key, remoteQueueConsumer);

            if (server.hasBrokerFederationPlugins()) {
               try {
                  final FederatedQueueConsumer finalConsumer = remoteQueueConsumer;
                  server.callBrokerFederationPlugins(plugin -> plugin.afterCreateFederatedQueueConsumer(finalConsumer));
               } catch (ActiveMQException t) {
                  ActiveMQServerLogger.LOGGER.federationPluginExecutionError("afterCreateFederatedQueueConsumer", t);
                  throw new IllegalStateException(t.getMessage(), t.getCause());
               }
            }
         }
         remoteQueueConsumer.incrementCount();
      }
   }


   public synchronized void removeRemoteConsumer(FederatedConsumerKey key) {
      FederatedQueueConsumer remoteQueueConsumer = remoteQueueConsumers.get(key);
      if (remoteQueueConsumer != null) {
         if (server.hasBrokerFederationPlugins()) {
            try {
               server.callBrokerFederationPlugins(plugin -> plugin.beforeCloseFederatedQueueConsumer(remoteQueueConsumer));
            } catch (ActiveMQException t) {
               ActiveMQServerLogger.LOGGER.federationPluginExecutionError("beforeCloseFederatedQueueConsumer", t);
               throw new IllegalStateException(t.getMessage(), t.getCause());
            }
         }
         if (remoteQueueConsumer.decrementCount() <= 0) {
            remoteQueueConsumer.close();
            remoteQueueConsumers.remove(key);
         }
         if (server.hasBrokerFederationPlugins()) {
            try {
               server.callBrokerFederationPlugins(plugin -> plugin.afterCloseFederatedQueueConsumer(remoteQueueConsumer));
            } catch (ActiveMQException t) {
               ActiveMQServerLogger.LOGGER.federationPluginExecutionError("afterCloseFederatedQueueConsumer", t);
               throw new IllegalStateException(t.getMessage(), t.getCause());
            }
         }
      }
   }

}
