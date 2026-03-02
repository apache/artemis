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
package org.apache.activemq.artemis.protocol.amqp.connect;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.Lock;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.netty.channel.ChannelPipeline;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectionAddressType;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectionElement;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnection;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.lock.LockCoordinator;
import org.apache.activemq.artemis.protocol.amqp.broker.ProtonProtocolManager;
import org.apache.activemq.artemis.protocol.amqp.broker.ProtonProtocolManagerFactory;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.remoting.ClientConnectionLifeCycleListener;
import org.apache.activemq.artemis.spi.core.remoting.ClientProtocolManager;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.apache.activemq.artemis.spi.core.remoting.SessionContext;
import org.apache.activemq.artemis.spi.core.remoting.TopologyResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

public class AMQPBrokerConnectionManager implements ActiveMQComponent, ClientConnectionLifeCycleListener {
   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final ProtonProtocolManagerFactory protonProtocolManagerFactory;
   private final ActiveMQServer server;
   private volatile boolean started = false;

   private final Map<String, AMQPBrokerConnectConfiguration> amqpConnectionsConfig;
   private final Map<String, AMQPBrokerConnection> amqpBrokerConnections = new HashMap<>();

   public AMQPBrokerConnectionManager(ProtonProtocolManagerFactory factory, List<AMQPBrokerConnectConfiguration> amqpConnectionsConfig, ActiveMQServer server) {
      this.amqpConnectionsConfig =
         amqpConnectionsConfig.stream()
                              .collect(Collectors.toMap(c -> c.getName(), Function.identity()));

      this.server = server;
      this.protonProtocolManagerFactory = factory;
   }

   @Override
   public void start() throws Exception {
      if (!started) {
         started = true;

         for (AMQPBrokerConnectConfiguration configuration : amqpConnectionsConfig.values()) {
            createBrokerConnection(configuration, configuration.isAutostart());
         }
      }
   }

   /**
    * {@return the number of configured broker connection configurations}
    */
   public int getConfiguredConnectionsCount() {
      return amqpConnectionsConfig.size();
   }

   private void createBrokerConnection(AMQPBrokerConnectConfiguration configuration, boolean start) throws Exception {
      AMQPBrokerConnection amqpBrokerConnection = new AMQPBrokerConnection(this, configuration, protonProtocolManagerFactory, server);
      amqpBrokerConnections.put(configuration.getName(), amqpBrokerConnection);
      if (configuration.getLockCoordinator() != null) {
         LockCoordinator lockCoordinator = server.getLockCoordinator(configuration.getLockCoordinator());
         if (lockCoordinator == null) {
            throw new IllegalStateException("lock coordinator " + configuration.getName() + " not found");
         }
         amqpBrokerConnection.setLockCoordinator(lockCoordinator);
      }

      amqpBrokerConnection.initialize();

      if (start) {
         amqpBrokerConnection.start();
      }
   }

   /**
    * Updates the configuration of any / all broker connections in the broker connection manager based on updated broker
    * configuration.
    *
    * @param configurations A list of broker connection configurations after a broker configuration update.
    */
   public void updateConfiguration(List<AMQPBrokerConnectConfiguration> configurations) throws Exception {
      final List<AMQPBrokerConnectConfiguration> updatedConfigurations = Objects.requireNonNullElse(configurations, Collections.emptyList());

      // We want to shutdown all broker connections before starting any new ones just to ensure
      // we do not have any overlapping connections to the same broker from old to new configurations.
      final Map<AMQPBrokerConnectConfiguration, Boolean> newConnections = new HashMap<>();

      // Find any updated configurations and stop / and recreate as needed.
      for (AMQPBrokerConnectConfiguration configuration : updatedConfigurations) {
         final AMQPBrokerConnectConfiguration previous = amqpConnectionsConfig.get(configuration.getName());

         if (previous == null || !configuration.equals(previous)) {
            // We don't currently allow updating broker connections with mirror configurations
            // as that could break the mirroring or leak resources until properly implemented.
            // This means that a mirror configuration and a federation configuration on the
            // same broker connection cannot update the federation portion.
            //
            // This does allow mirroring to be added to an existing broker connection configuration
            // once added though, the broker connection cannot be updated or removed without a full
            // restart of the broker.
            if (previous != null && containsMirrorConfiguration(previous)) {
               logger.info("Skipping update of broker connection {} which contains a mirror " +
                           "configuration which are not reloadable.", previous.getName());
               continue;
            } else {
               amqpConnectionsConfig.put(configuration.getName(), configuration);
            }

            // If this was an update and the connection is active meaning the manager is
            // started then we need to stop the old one if it exists before attempting to
            // start a new version with the new configuration.
            final AMQPBrokerConnection connection = amqpBrokerConnections.remove(configuration.getName());
            // Defer to previous started state as we want to restore that if it was already
            // started and the configuration was updated.
            final boolean autoStart = connection == null ?
               configuration.isAutostart() : connection.isStarted() || configuration.isAutostart();

            if (connection != null) {
               connection.shutdown();
            }

            if (started) {
               newConnections.put(configuration, autoStart);
            }
         }
      }

      // Find any removed configurations and remove them from the current set

      final Map<String, AMQPBrokerConnectConfiguration> brokerConfigurations =
         updatedConfigurations.stream()
                              .collect(Collectors.toMap(c -> c.getName(), Function.identity()));

      final List<AMQPBrokerConnectConfiguration> removedList = amqpConnectionsConfig.values()
         .stream()
         .filter(c -> !brokerConfigurations.containsKey(c.getName()))
         .collect(Collectors.toList());

      for (AMQPBrokerConnectConfiguration toRemove : removedList) {
         // We don't allow removal of broker connections with Mirror elements as that leaves
         // behind mirror controllers in the broker service that no longer have a target that
         // will ever be recovered. We could do work to remove the mirror controller source but
         // that code is not thread safe and would require more work which is likely still ripe
         // with issues.
         if (containsMirrorConfiguration(toRemove)) {
            logger.info("Skipping remove of broker connection {} which contains a mirror configuration which are not reloadable.", toRemove.getName());
            continue;
         }

         amqpConnectionsConfig.remove(toRemove.getName());

         final AMQPBrokerConnection connection = amqpBrokerConnections.remove(toRemove.getName());

         if (connection != null) {
            connection.shutdown();
         }
      }

      // Start all new or updated broker connections now that all old connection have been shutdown.
      for (Map.Entry<AMQPBrokerConnectConfiguration, Boolean> entry : newConnections.entrySet()) {
         createBrokerConnection(entry.getKey(), entry.getValue());
      }
   }

   private boolean containsMirrorConfiguration(AMQPBrokerConnectConfiguration configuration) {
      for (AMQPBrokerConnectionElement element : configuration.getConnectionElements()) {
         if (AMQPBrokerConnectionAddressType.MIRROR.equals(element.getType())) {
            return true;
         }
      }

      return false;
   }

   public void connected(NettyConnection nettyConnection, AMQPBrokerConnection bridgeConnection) {
   }

   @Override
   public void stop() throws Exception {
      if (started) {
         started = false;
         try {
            for (AMQPBrokerConnection connection : amqpBrokerConnections.values()) {
               connection.shutdown();
            }
         } finally {
            amqpBrokerConnections.clear();
         }
      }
   }

   @Override
   public boolean isStarted() {
      return started;
   }

   @Override
   public void connectionCreated(ActiveMQComponent component, Connection connection, ClientProtocolManager protocol) {
   }

   @Override
   public void connectionDestroyed(Object connectionID, boolean failed) {
      for (AMQPBrokerConnection connection : amqpBrokerConnections.values()) {
         if (connection.getConnection() != null && connectionID.equals(connection.getConnection().getID())) {
            connection.connectionDestroyed(connectionID, failed);
         }
      }
   }

   @Override
   public void connectionException(Object connectionID, ActiveMQException me) {
      for (AMQPBrokerConnection connection : amqpBrokerConnections.values()) {
         if (connection.getConnection() != null && connectionID.equals(connection.getConnection().getID())) {
            connection.connectionException(connectionID, me);
         }
      }
   }

   @Override
   public void connectionReadyForWrites(Object connectionID, boolean ready) {
      for (AMQPBrokerConnection connection : amqpBrokerConnections.values()) {
         if (connection.getConnection().getID().equals(connectionID)) {
            connection.connectionReadyForWrites(connectionID, ready);
         }
      }
   }

   /**
    * The Client Protocol Manager is used for Core Clients. As we are reusing the NettyConnector the API requires a
    * ClientProtocolManager. This is to give us the reference for the AMQPConnection used.
    */
   public static class ClientProtocolManagerWithAMQP implements ClientProtocolManager {
      public final ProtonProtocolManager protonPM;

      public ClientProtocolManagerWithAMQP(ProtonProtocolManager protonPM) {
         this.protonPM = protonPM;
      }

      public ProtonProtocolManager getProtonPM() {
         return protonPM;
      }

      @Override
      public ClientProtocolManager setExecutor(Executor executor) {
         return null;
      }

      @Override
      public RemotingConnection connect(Connection transportConnection,
                                        long callTimeout,
                                        long callFailoverTimeout,
                                        List<Interceptor> incomingInterceptors,
                                        List<Interceptor> outgoingInterceptors,
                                        TopologyResponseHandler topologyResponseHandler) {
         return null;
      }

      @Override
      public RemotingConnection getCurrentConnection() {
         return null;
      }

      @Override
      public Lock lockSessionCreation() {
         return null;
      }

      @Override
      public boolean waitOnLatch(long milliseconds) throws InterruptedException {
         return false;
      }

      @Override
      public void stop() {

      }

      @Override
      public boolean isAlive() {
         return false;
      }

      @Override
      public void addChannelHandlers(ChannelPipeline pipeline) {

      }

      @Override
      public void sendSubscribeTopology(boolean isServer) {

      }

      @Override
      public void ping(long connectionTTL) {

      }

      @Override
      public SessionContext createSessionContext(String name,
                                                 String username,
                                                 String password,
                                                 boolean xa,
                                                 boolean autoCommitSends,
                                                 boolean autoCommitAcks,
                                                 boolean preAcknowledge,
                                                 int minLargeMessageSize,
                                                 int confirmationWindowSize,
                                                 String clientID) throws ActiveMQException {
         return null;
      }

      @Override
      public boolean cleanupBeforeFailover(ActiveMQException cause) {
         return false;
      }

      @Override
      public boolean checkForFailover(String nodeID) throws ActiveMQException {
         return false;
      }

      @Override
      public void setSessionFactory(ClientSessionFactory factory) {

      }

      @Override
      public ClientSessionFactory getSessionFactory() {
         return null;
      }

      @Override
      public String getName() {
         return null;
      }
   }
}
