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
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.federation.FederationStreamConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.federation.address.FederatedAddress;
import org.apache.activemq.artemis.core.server.federation.queue.FederatedQueue;

@Deprecated(forRemoval = true)
public abstract class AbstractFederationStream implements FederationStream {

   @Deprecated(forRemoval = true)
   protected final ActiveMQServer server;
   @Deprecated(forRemoval = true)
   protected final Federation federation;
   @Deprecated(forRemoval = true)
   protected final SimpleString name;
   @Deprecated(forRemoval = true)
   protected final FederationConnection connection;
   private FederationStreamConfiguration config;
   @Deprecated(forRemoval = true)
   protected Map<String, FederatedQueue> federatedQueueMap = new HashMap<>();
   @Deprecated(forRemoval = true)
   protected Map<String, FederatedAddress> federatedAddressMap = new HashMap<>();


   @Deprecated(forRemoval = true)
   public AbstractFederationStream(ActiveMQServer server, Federation federation, String name, FederationStreamConfiguration config) {
      this(server, federation, name, config, null);
   }

   @Deprecated(forRemoval = true)
   public AbstractFederationStream(final ActiveMQServer server, final Federation federation, final String name, final FederationStreamConfiguration config,
                                   final FederationConnection connection) {
      this.server = server;
      this.federation = federation;
      Objects.requireNonNull(config.getName());
      this.name = SimpleString.of(config.getName());
      this.config = config;
      this.connection = Objects.requireNonNullElseGet(connection, () -> new FederationConnection(server.getConfiguration(), name, config.getConnectionConfiguration()));
   }

   @Deprecated(forRemoval = true)
   @Override
   public synchronized void start() {
      if (connection != null) {
         connection.start();
      }
   }

   @Deprecated(forRemoval = true)
   @Override
   public synchronized void stop() {
      if (connection != null) {
         connection.stop();
      }
   }

   @Deprecated(forRemoval = true)
   @Override
   public Federation getFederation() {
      return federation;
   }

   @Deprecated(forRemoval = true)
   @Override
   public FederationStreamConfiguration getConfig() {
      return config;
   }

   @Deprecated(forRemoval = true)
   @Override
   public SimpleString getName() {
      return name;
   }

   @Deprecated(forRemoval = true)
   @Override
   public FederationConnection getConnection() {
      return connection;
   }


   @Deprecated(forRemoval = true)
   @Override
   public String getUser() {
      String user = config.getConnectionConfiguration().getUsername();
      if (user == null || user.isEmpty()) {
         return federation.getFederationUser();
      } else {
         return user;
      }
   }

   @Deprecated(forRemoval = true)
   @Override
   public String getPassword() {
      String password = config.getConnectionConfiguration().getPassword();
      if (password == null || password.isEmpty()) {
         return federation.getFederationPassword();
      } else {
         return password;
      }
   }

   @Deprecated(forRemoval = true)
   @Override
   public int getPriorityAdjustment() {
      return config.getConnectionConfiguration().getPriorityAdjustment();
   }

   @Deprecated(forRemoval = true)
   protected void callFederationStreamStartedPlugins() {
      if (server.hasBrokerFederationPlugins()) {
         try {
            server.callBrokerFederationPlugins(plugin -> plugin.federationStreamStarted(this));
         } catch (ActiveMQException t) {
            ActiveMQServerLogger.LOGGER.federationPluginExecutionError("federationStreamStarted", t);
            throw new IllegalStateException(t.getMessage(), t.getCause());
         }
      }
   }

   @Deprecated(forRemoval = true)
   protected void callFederationStreamStoppedPlugins() {
      if (server.hasBrokerFederationPlugins()) {
         try {
            server.callBrokerFederationPlugins(plugin -> plugin.federationStreamStopped(this));
         } catch (ActiveMQException t) {
            ActiveMQServerLogger.LOGGER.federationPluginExecutionError("federationStreamStopped", t);
            throw new IllegalStateException(t.getMessage(), t.getCause());
         }
      }
   }
}
