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

import io.netty.channel.ChannelPipeline;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.protocol.amqp.broker.ProtonProtocolManager;
import org.apache.activemq.artemis.protocol.amqp.broker.ProtonProtocolManagerFactory;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.remoting.ClientProtocolManager;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.apache.activemq.artemis.spi.core.remoting.SessionContext;
import org.apache.activemq.artemis.spi.core.remoting.TopologyResponseHandler;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.Lock;

/**
 * Handles proton protocol management for clients, mapping the {@link ProtonProtocolManager} to the
 * {@link org.apache.activemq.artemis.spi.core.remoting.ClientProtocolManager} API. This is currently very basic and
 * only supports Connecting to a broker, which will be useful in scenarios where the broker needs to connect to another
 * broker through AMQP into another broker (like Interconnect) that will perform extra functionality.
 */
public class ProtonClientProtocolManager extends ProtonProtocolManager implements ClientProtocolManager {

   public ProtonClientProtocolManager(ProtonProtocolManagerFactory factory, ActiveMQServer server) {
      super(factory, server, Collections.emptyList(), Collections.emptyList());
   }

   @Override
   public ClientProtocolManager setExecutor(Executor executor) {
      return null;
   }

   @Override
   public void stop() {
      throw new UnsupportedOperationException();
   }

   @Override
   public RemotingConnection connect(Connection transportConnection, long callTimeout, long callFailoverTimeout, List<Interceptor> incomingInterceptors, List<Interceptor> outgoingInterceptors, TopologyResponseHandler topologyResponseHandler) {
      throw new UnsupportedOperationException();
   }

   @Override
   public RemotingConnection getCurrentConnection() {
      throw new UnsupportedOperationException();
   }

   @Override
   public Lock lockSessionCreation() {
      throw new UnsupportedOperationException();
   }

   @Override
   public boolean waitOnLatch(long milliseconds) throws InterruptedException {
      throw new UnsupportedOperationException();
   }

   @Override
   public boolean isAlive() {
      throw new UnsupportedOperationException();
   }

   @Override
   public void addChannelHandlers(ChannelPipeline pipeline) {
   }

   @Override
   public void sendSubscribeTopology(boolean isServer) {
      throw new UnsupportedOperationException();
   }

   @Override
   public void ping(long connectionTTL) {
      throw new UnsupportedOperationException();
   }

   @Override
   public SessionContext createSessionContext(String name, String username, String password, boolean xa, boolean autoCommitSends, boolean autoCommitAcks, boolean preAcknowledge, int minLargeMessageSize, int confirmationWindowSize, String clientID) throws ActiveMQException {
      throw new UnsupportedOperationException();
   }

   @Override
   public boolean cleanupBeforeFailover(ActiveMQException cause) {
      throw new UnsupportedOperationException();
   }

   @Override
   public boolean sendConnect(String nodeID, String connectionUser, String connectionPassword) throws ActiveMQException {
      throw new UnsupportedOperationException();
   }

   @Override
   public void setSessionFactory(ClientSessionFactory factory) {
      throw new UnsupportedOperationException();
   }

   @Override
   public ClientSessionFactory getSessionFactory() {
      throw new UnsupportedOperationException();
   }

   @Override
   public String getName() {
      throw new UnsupportedOperationException();
   }
}
