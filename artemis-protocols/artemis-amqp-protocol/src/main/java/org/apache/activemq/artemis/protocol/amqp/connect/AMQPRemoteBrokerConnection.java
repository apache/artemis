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
package org.apache.activemq.artemis.protocol.amqp.connect;

import static org.apache.activemq.artemis.protocol.amqp.connect.AMQPBrokerConnectionConstants.BROKER_CONNECTION_INFO;
import static org.apache.activemq.artemis.protocol.amqp.connect.AMQPBrokerConnectionConstants.CONNECTION_NAME;
import static org.apache.activemq.artemis.protocol.amqp.connect.AMQPBrokerConnectionConstants.NODE_ID;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQIllegalStateException;
import org.apache.activemq.artemis.core.remoting.FailureListener;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.RemoteBrokerConnection;
import org.apache.activemq.artemis.protocol.amqp.broker.ActiveMQProtonRemotingConnection;
import org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationTarget;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPConnectionContext;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.engine.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Utility class that represents the remote end of an incoming AMQP broker connection used to provide a common root
 * for services that are active on a given incoming broker connection.
 */
public class AMQPRemoteBrokerConnection implements RemoteBrokerConnection {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final String REMOTE_BROKER_CONNECTION_RECORD = "REMOTE_BROKER_CONNECTION_RECORD";

   private enum State {
      UNINITIALIZED,
      STARTED,
      SHUTDOWN
   }

   private List<AMQPFederationTarget> federations = new ArrayList<>();
   private final ActiveMQServer server;
   private final AMQPConnectionContext connection;
   private final String remoteNodeId;
   private final String remoteConnectionName;

   private State state = State.UNINITIALIZED;

   public AMQPRemoteBrokerConnection(ActiveMQServer server, AMQPConnectionContext connection, String remoteNodeId, String remoteConnectionName) {
      this.server = server;
      this.connection = connection;
      this.remoteNodeId = remoteNodeId;
      this.remoteConnectionName = remoteConnectionName;
   }

   /**
    * {@return the server instance that owns this remote broker connection}
    */
   public ActiveMQServer getServer() {
      return server;
   }

   /**
    * {@return the connection context for this incoming broker connection}
    */
   public AMQPConnectionContext getConnection() {
      return connection;
   }

   /**
    * @return the remote node ID if it was provided or null if not supplied on connect
    */
   @Override
   public String getNodeId() {
      return remoteNodeId;
   }

   /**
    * @return the remote broker connection name if it was provided or null if not supplied on connect
    */
   @Override
   public String getName() {
      return remoteConnectionName;
   }

   @Override
   public String getProtocol() {
      return "AMQP";
   }

   /**
    * {@return {@code true} if the remote broker connection was established with enough information to uniquely identify
    * the connection source for purposes of adding the remote connection into the server management services}
    */
   public boolean isManagable() {
      return remoteConnectionName != null &&
             !remoteConnectionName.isBlank() &&
             remoteNodeId != null &&
             !remoteNodeId.isBlank();
   }

   /**
    * {@return has this remote broker connection been initialized}
    */
   public boolean isInitialized() {
      return state.ordinal() > State.UNINITIALIZED.ordinal();
   }

   /**
    * {@return has this remote broker connection been started}
    */
   public boolean isStarted() {
      return state == State.STARTED;
   }

   /**
    * {@return has this remote broker connection been shutdown}
    */
   public boolean isShutdown() {
      return state == State.SHUTDOWN;
   }

   /**
    * Initialize the remote broker connection object which moves it to the started state where it will remain until
    * shutdown.
    *
    * @throws ActiveMQException if an error occurs on initialization.
    */
   @Override
   public synchronized void initialize() throws ActiveMQException {
      failIfShutdown();

      if (state == State.UNINITIALIZED) {
         state = State.STARTED;

         if (isManagable()) {
            try {
               server.getManagementService().registerRemoteBrokerConnection(this);
            } catch (Exception e) {
               logger.warn("Ignoring error while attempting to register remote broker connection with management services");
            }
         }
      }
   }

   /**
    * Shutdown the remote broker connection object which removes any management objects and informs any registered
    * services of the shutdown.
    */
   @Override
   public synchronized void shutdown() {
      if (state.ordinal() < State.SHUTDOWN.ordinal()) {
         state = State.SHUTDOWN;

         if (isManagable()) {
            try {
               server.getManagementService().unregisterRemoteBrokerConnection(getNodeId(), getName());
            } catch (Exception e) {
               logger.warn("Ignoring error while attempting to unregister remote broker connection with management services");
            }
         }

         federations.forEach((federation) -> {
            try {
               federation.shutdown();
            } catch (ActiveMQException e) {
               logger.warn("Exception suppressed on remote broker federation shutdown: ", e);
            }
         });
      }
   }

   /**
    * Add a new federation target to this remote broker connection instance which will be started if this federation
    * instance has already been started.
    *
    * @return this remote broker connection instance
    * @throws ActiveMQException if an error occurs while adding the federation to this connection.
    */
   public synchronized AMQPRemoteBrokerConnection addFederationTarget(AMQPFederationTarget federation) throws ActiveMQException {
      failIfShutdown();

      if (isStarted()) {
         federation.start();
      }

      this.federations.add(federation);

      return this;
   }

   /**
    * Utility methods for checking the current connection for an existing remote broker connection instance and
    * returning it, or creating a new instance if none yet exists and initializing it.
    *
    * @param server           The server instance that has accepted the remote broker connection.
    * @param connection       The connection context object that is assigned to the active connection.
    * @param protonConnection The proton connection instance where the connection attachments are stored.
    * @return a remote broker connection instance that has been initialized that is scoped to the active connection
    * @throws ActiveMQException if an error occurs while attempting to get a remote broker connection instance.
    */
   @SuppressWarnings("unchecked")
   public static AMQPRemoteBrokerConnection getOrCreateRemoteBrokerConnection(ActiveMQServer server, AMQPConnectionContext connection, Connection protonConnection) throws ActiveMQException {
      final org.apache.qpid.proton.engine.Record attachments = protonConnection.attachments();

      // There is only one instance per remote broker connection, either this is the first time a service
      // requested it in which case we build it, or we already built it so we can get it from the attachments.
      AMQPRemoteBrokerConnection brokerConnection = attachments.get(REMOTE_BROKER_CONNECTION_RECORD, AMQPRemoteBrokerConnection.class);

      if (brokerConnection == null) {
         final Map<Symbol, Object> connectionProperties = protonConnection.getRemoteProperties() != null ?
            protonConnection.getRemoteProperties() : Collections.EMPTY_MAP;
         final Map<String, Object> brokerConnectionInfo = (Map<String, Object>) connectionProperties.getOrDefault(BROKER_CONNECTION_INFO, Collections.EMPTY_MAP);

         final ActiveMQProtonRemotingConnection remoteingConnection = connection.getConnectionCallback().getProtonConnectionDelegate();
         final String remoteNodeId = (String) brokerConnectionInfo.get(NODE_ID);
         final String remoteConnectionName = (String) brokerConnectionInfo.get(CONNECTION_NAME);

         final AMQPRemoteBrokerConnection newBrokerConnection =
            brokerConnection = new AMQPRemoteBrokerConnection(server, connection, remoteNodeId, remoteConnectionName);

         brokerConnection.initialize();

         // When the connection drops the remote broker connection needs to cleanup all its resources to ensure
         // things like management objects are properly removed and cleaned up.
         remoteingConnection.addCloseListener(() -> handleConnectionShutdown(newBrokerConnection));
         remoteingConnection.addFailureListener(new FailureListener() {

            @Override
            public void connectionFailed(ActiveMQException exception, boolean failedOver, String scaleDownTargetNodeID) {
               handleConnectionShutdown(newBrokerConnection);
            }

            @Override
            public void connectionFailed(ActiveMQException exception, boolean failedOver) {
               handleConnectionShutdown(newBrokerConnection);
            }
         });

         attachments.set(REMOTE_BROKER_CONNECTION_RECORD, AMQPRemoteBrokerConnection.class, brokerConnection);
      }

      return brokerConnection;
   }

   private static void handleConnectionShutdown(AMQPRemoteBrokerConnection connection) {
      try {
         connection.shutdown();
      } catch (Exception e) {
         logger.warn("Exception suppressed on remote broker connection shutdown: ", e);
      }
   }

   private void failIfShutdown() throws ActiveMQIllegalStateException {
      if (state == State.SHUTDOWN) {
         throw new ActiveMQIllegalStateException("The remote broker connection instance has been shutdown");
      }
   }
}
