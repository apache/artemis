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
package org.apache.activemq.artemis.protocol.amqp.client;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQRemoteDisconnectException;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.protocol.amqp.broker.ActiveMQProtonRemotingConnection;
import org.apache.activemq.artemis.protocol.amqp.broker.ProtonProtocolManager;
import org.apache.activemq.artemis.protocol.amqp.proton.handler.EventHandler;
import org.apache.activemq.artemis.protocol.amqp.sasl.ClientSASLFactory;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.remoting.BaseConnectionLifeCycleListener;
import org.apache.activemq.artemis.spi.core.remoting.BufferHandler;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the lifecycle of a proton client connection.
 */
public class ProtonClientConnectionManager implements BaseConnectionLifeCycleListener<ProtonProtocolManager>, BufferHandler {
   private final Map<Object, ActiveMQProtonRemotingConnection> connectionMap = new ConcurrentHashMap<>();
   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
   private final AMQPClientConnectionFactory connectionFactory;
   private final Optional<EventHandler> eventHandler;
   private final ClientSASLFactory clientSASLFactory;

   public ProtonClientConnectionManager(AMQPClientConnectionFactory connectionFactory, Optional<EventHandler> eventHandler, ClientSASLFactory clientSASLFactory) {
      this.connectionFactory = connectionFactory;
      this.eventHandler = eventHandler;
      this.clientSASLFactory = clientSASLFactory;
   }

   @Override
   public void connectionCreated(ActiveMQComponent component, Connection connection, ProtonProtocolManager protocolManager) {
      ActiveMQProtonRemotingConnection amqpConnection = connectionFactory.createConnection(protocolManager, connection, eventHandler, clientSASLFactory);
      connectionMap.put(connection.getID(), amqpConnection);
      amqpConnection.open();

      logger.info("Connection {} created", amqpConnection.getRemoteAddress());
   }

   @Override
   public void connectionDestroyed(Object connectionID, boolean failed) {
      RemotingConnection connection = connectionMap.remove(connectionID);
      if (connection != null) {
         logger.info("Connection {} destroyed", connection.getRemoteAddress());
         connection.fail(new ActiveMQRemoteDisconnectException());
      } else {
         logger.error("Connection with id {} not found in connectionDestroyed", connectionID);
      }
   }

   @Override
   public void connectionException(Object connectionID, ActiveMQException me) {
      RemotingConnection connection = connectionMap.get(connectionID);
      if (connection != null) {
         logger.info("Connection {} exception: {}", connection.getRemoteAddress(), me.getMessage());
         connection.fail(me);
      } else {
         logger.error("Connection with id {} not found in connectionException", connectionID);
      }
   }

   @Override
   public void connectionReadyForWrites(Object connectionID, boolean ready) {
      RemotingConnection connection = connectionMap.get(connectionID);
      if (connection != null) {
         logger.info("Connection {} ready", connection.getRemoteAddress());
         connection.getTransportConnection().fireReady(true);
      } else {
         logger.error("Connection with id {} not found in connectionReadyForWrites()!", connectionID);
      }
   }

   public void stop() {
      for (RemotingConnection connection : connectionMap.values()) {
         connection.destroy();
      }
   }

   @Override
   public void bufferReceived(Object connectionID, ActiveMQBuffer buffer) {
      RemotingConnection connection = connectionMap.get(connectionID);
      if (connection != null) {
         connection.bufferReceived(connectionID, buffer);
      } else {
         logger.error("Connection with id {} not found in bufferReceived()!", connectionID);
      }
   }

   public RemotingConnection getConnection(Object connectionId) {
      return connectionMap.get(connectionId);
   }
}
