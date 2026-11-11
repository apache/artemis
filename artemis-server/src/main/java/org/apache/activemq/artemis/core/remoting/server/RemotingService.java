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
package org.apache.activemq.artemis.core.remoting.server;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.activemq.artemis.api.core.BaseInterceptor;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.protocol.core.CoreRemotingConnection;
import org.apache.activemq.artemis.core.security.ActiveMQPrincipal;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.spi.core.protocol.ConnectionEntry;
import org.apache.activemq.artemis.spi.core.protocol.ProtocolManagerFactory;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.remoting.Acceptor;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.apache.activemq.artemis.utils.ReusableLatch;

public interface RemotingService {

   /**
    * Remove a connection from the connections held by the remoting service. <strong>This method must be used only from
    * the management API. RemotingConnections are removed from the remoting service when their connectionTTL is
    * hit.</strong>
    *
    * @param remotingConnectionID the ID of the RemotingConnection to removed
    * @return the removed RemotingConnection
    */
   RemotingConnection removeConnection(Object remotingConnectionID);

   RemotingConnection getConnection(Object remotingConnectionID);

   Set<RemotingConnection> getConnections();

   /**
    * {@return the number of clients connected to this server}
    */
   default int getConnectionCount() {
      final Set<RemotingConnection> connections = getConnections();
      return connections == null ? 0 : getConnections().size();
   }

   /**
    * {@return the number of clients which have connected to this server since it was started}
    */
   long getTotalConnectionCount();

   ReusableLatch getConnectionCountLatch();

   void addIncomingInterceptor(BaseInterceptor interceptor);

   List<BaseInterceptor> getIncomingInterceptors();

   void addOutgoingInterceptor(BaseInterceptor interceptor);

   List<BaseInterceptor> getOutgoinInterceptors();

   boolean removeIncomingInterceptor(BaseInterceptor interceptor);

   boolean removeOutgoingInterceptor(BaseInterceptor interceptor);

   void clearInterceptors();

   void notifyStop();

   /** The Prepare stop will close all the connections however it will use the one used by storage manager */
   void prepareStop(boolean criticalError, Set<RemotingConnection> ignoreConnections) throws Exception;

   void stop(boolean criticalError) throws Exception;

   void start() throws Exception;

   void startAcceptors() throws Exception;

   boolean isStarted();

   Map<String, ProtocolManagerFactory> getProtocolFactoryMap();

   /**
    * Allow acceptors to use this as their default security Principal if applicable.
    * <p>
    * Used by AS7 integration code.
    */
   void allowInvmSecurityOverride(ActiveMQPrincipal principal);

   /**
    * Pauses the acceptors so that no more connections can be made to the server
    */
   void pauseAcceptors();

   /**
    * Pauses the acceptors so that no more connections can be made to the server
    */
   boolean isPaused();

   /**
    * Freezes and then disconnects all connections except the given one and tells the client where else it might connect
    * (only applicable if server is in a cluster and uses scaleDown-on-failover=true).
    */
   void freeze(String scaleDownNodeID, CoreRemotingConnection remotingConnection);

   /**
    * {@return the acceptor identified by its {@code name} or {@code null} if it does not exists}
    *
    * @param name the name of the acceptor
    */
   Acceptor getAcceptor(String name);

   Map<String, Acceptor> getAcceptors();

   Acceptor createAcceptor(String name, String uri) throws Exception;

   Acceptor createAcceptor(TransportConfiguration transportConfiguration);

   void destroyAcceptor(String name) throws Exception;

   void loadProtocolServices(List<ActiveMQComponent> protocolServices);

   /**
    * Provides an entry point for protocol services offered by this service instance to react to configuration updates.
    * If the service implementation does not have any managed services or its services do not respond to updates it can
    * ignore this call. services added should be added to the provided protocolServices list, and any removed should be
    * found and removed from the list.
    *
    * @param protocolServices The list of protocol services known to the broker.
    * @throws Exception if an error is thrown during the services updates.
    */
   void updateProtocolServices(List<ActiveMQComponent> protocolServices) throws Exception;

   void addConnectionEntry(Connection connection, ConnectionEntry entry);
}
