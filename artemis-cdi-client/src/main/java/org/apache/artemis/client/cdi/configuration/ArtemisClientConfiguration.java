/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.artemis.client.cdi.configuration;

import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory;

public interface ArtemisClientConfiguration {

   String IN_VM_CONNECTOR = InVMConnectorFactory.class.getName();
   String REMOTE_CONNECTOR = NettyConnectorFactory.class.getName();

   /**
    * {@return if present, sends a username for the connection}
    */
   String getUsername();

   /**
    * {@return the password for the connection.  If username is set, password must be set}
    */
   String getPassword();

   /**
    * Either url should be set, or host, port, connector factory should be set.
    *
    * @return if set, will be used in the server locator to look up the server instead of the hostname/port combination
    */
   String getUrl();

   /**
    * {@return The hostname to connect to}
    */
   String getHost();

   /**
    * {@return the port number to connect to}
    */
   Integer getPort();

   /**
    * {@return the connector factory to use for connections}
    */
   String getConnectorFactory();

   /**
    * {@return whether to start the embedded broker}
    */
   boolean startEmbeddedBroker();

   /**
    * {@return whether this is an HA connection}
    */
   boolean isHa();

   /**
    * {@return whether the authentication parameters should be used}
    */
   boolean hasAuthentication();
}
