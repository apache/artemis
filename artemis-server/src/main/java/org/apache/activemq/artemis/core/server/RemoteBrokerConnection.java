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

/**
 * A remote broker connection defines a view of the remote end of an active {@link BrokerConnection}.
 */
public interface RemoteBrokerConnection {

   /**
    * Handles initialization when the remote connection is setup.
    *
    * @throws Exception if an error occurs during initialization.
    */
   void initialize() throws Exception;

   /**
    * Handles cleanup when the remote connection is closed or becomes disconnect.
    *
    * @throws Exception if an error occurs during shutdown.
    */
   void shutdown() throws Exception;

   /**
    * {@return the name of the broker connection as defined on the remote server; this value is unique on the remote
    * server but is only unique on the local end when combined with the unique node Id from which the broker connection
    * was initiated}
    */
   String getName();

   /**
    * {@return the node Id of the remote broker that created the incoming connection}
    */
   String getNodeId();

   /**
    * {@return the protocol that underlies the broker connection implementation}
    */
   String getProtocol();

}
