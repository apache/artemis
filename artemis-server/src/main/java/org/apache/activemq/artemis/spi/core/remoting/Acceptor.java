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
package org.apache.activemq.artemis.spi.core.remoting;

import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.api.core.BaseInterceptor;
import org.apache.activemq.artemis.core.protocol.ProtocolHandler;
import org.apache.activemq.artemis.core.security.ActiveMQPrincipal;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.core.server.cluster.ClusterConnection;
import org.apache.activemq.artemis.core.server.lock.LockCoordinator;
import org.apache.activemq.artemis.core.server.management.NotificationService;

import static org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.DEFAULT_AUTO_START;

/**
 * An Acceptor is used by the RemotingService to allow clients to connect. It should take care of dispatching client
 * requests to the RemotingService's Dispatcher.
 */
public interface Acceptor extends ActiveMQComponent {

   /**
    * The name of the acceptor used on the configuration. for logging and debug purposes.
    */
   String getName();

   default Acceptor setLockCoordinator(LockCoordinator lockCoordinator) {
      return this;
   }

   default LockCoordinator getLockCoordinator() {
      return null;
   }

   /**
    * Pause the acceptor and stop it from receiving client requests.
    */
   void pause();

   void notifyStop();

   /**
    * This will update the list of interceptors for each ProtocolManager inside the acceptor.
    */
   void updateInterceptors(List<BaseInterceptor> incomingInterceptors, List<BaseInterceptor> outgoingInterceptors);

   /**
    * @return the cluster connection associated with this Acceptor
    */
   ClusterConnection getClusterConnection();

   Map<String, Object> getConfiguration();

   /**
    * Set the notification service for this acceptor to use.
    *
    * @param notificationService the notification service
    */
   void setNotificationService(NotificationService notificationService);

   /**
    * Set the default security Principal to be used when no user/pass are defined, only for InVM
    */
   void setDefaultActiveMQPrincipal(ActiveMQPrincipal defaultActiveMQPrincipal);

   /**
    * Whether this acceptor allows insecure connections.
    *
    * @throws java.lang.IllegalStateException if false @setDefaultActiveMQPrincipal
    */
   boolean isUnsecurable();

   /**
    * Re-create the acceptor with the existing configuration values. Useful, for example, for reloading key/trust stores
    * on acceptors which support SSL.
    */
   void reload();

   default ProtocolHandler getProtocolHandler() {
      return null;
   }

   /**
    * This is a utility method for Socket-based acceptor implementations to get the actual port used. This is useful for
    * configurations which specify a port number of 0 which allows the JVM to select an ephemeral port.
    *
    * @return the actual port used if using a Socket-based acceptor implementation; -1 otherwise
    */
   default int getActualPort() {
      return -1;
   }

   default boolean isAutoStart() {
      return DEFAULT_AUTO_START;
   }
}
