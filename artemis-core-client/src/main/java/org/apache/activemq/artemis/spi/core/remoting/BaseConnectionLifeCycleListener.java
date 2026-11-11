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

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;

/**
 * A ConnectionLifeCycleListener is called by the remoting implementation to notify of connection events.
 */
public interface BaseConnectionLifeCycleListener<ProtocolClass> {

   /**
    * This method is used both by client connector creation and server connection creation through acceptors. On the
    * client side the {@code component} parameter is normally passed as {@code null}.
    * <p>
    * Leaving this method here and adding a different one at {@code ServerConnectionLifeCycleListener} is a compromise
    * for a reasonable split between the activemq-server and activemq-client packages while avoiding to pull too much
    * into activemq-core. The pivotal point keeping us from removing the method is {@link ConnectorFactory} and the
    * usage of it.
    *
    * @param component  This will probably be an {@code Acceptor} and only used on the server side.
    * @param connection the connection that has been created
    * @param protocol   the messaging protocol type this connection uses
    */
   void connectionCreated(ActiveMQComponent component, Connection connection, ProtocolClass protocol);

   /**
    * Called when a connection is destroyed.
    *
    * @param connectionID the connection being destroyed.
    */
   void connectionDestroyed(Object connectionID, boolean failed);

   /**
    * Called when an error occurs on the connection.
    *
    * @param connectionID the id of the connection.
    * @param me           the exception.
    */
   void connectionException(Object connectionID, ActiveMQException me);

   void connectionReadyForWrites(Object connectionID, boolean ready);
}
