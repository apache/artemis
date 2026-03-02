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
package org.apache.activemq.artemis.core.server;

import org.apache.activemq.artemis.core.config.brokerConnectivity.BrokerConnectConfiguration;
import org.apache.activemq.artemis.core.server.lock.LockCoordinator;

/**
 * A broker connection defines a server connection created to provide services between this server and another
 * instance.
 */
public interface BrokerConnection extends ActiveMQComponent {

   default void initialize() throws Exception {
      // Subclass should override and perform needed initialization.
   }

   default void shutdown() throws Exception {
      // Subclass should override and perform needed cleanup.
   }

   LockCoordinator getLockCoordinator();

   BrokerConnection setLockCoordinator(LockCoordinator lockCoordinator);

   /**
    * {@return the unique name of the broker connection}
    */
   String getName();

   /**
    * {@return the protocol that underlies the broker connection implementation}
    */
   String getProtocol();

   /**
    * {@return {@code true} if the broker connection is currently connected to the remote}
    */
   boolean isConnected();

   /**
    * {@return the configuration that was used to create this broker connection}
    */
   BrokerConnectConfiguration getConfiguration();

}
