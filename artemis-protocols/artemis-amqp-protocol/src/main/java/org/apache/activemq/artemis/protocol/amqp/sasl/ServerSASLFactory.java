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
package org.apache.activemq.artemis.protocol.amqp.sasl;

import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.protocol.amqp.broker.AmqpInterceptor;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPRoutingHandler;
import org.apache.activemq.artemis.spi.core.protocol.ProtocolManager;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.remoting.Connection;

/**
 * A {@link ServerSASLFactory} is responsible for instantiating a given SASL mechanism
 */
public interface ServerSASLFactory {

   /**
    * {@return the name of the scheme to offer}
    */
   String getMechanism();

   /**
    * creates a new {@link ServerSASL} for the provided context
    *
    * @return a new instance of {@link ServerSASL} that implements the provided mechanism
    */
   ServerSASL create(ActiveMQServer server, ProtocolManager<AmqpInterceptor, AMQPRoutingHandler> manager, Connection connection,
                     RemotingConnection remotingConnection);

   /**
    * {@return the precedence of the given SASL mechanism, the default precedence is zero, where higher means better}
    */
   int getPrecedence();

   /**
    * {@return {@code true} if this mechanism should be part of the servers default permitted protocols or {@code false}
    * if it must be explicitly configured}
    */
   boolean isDefaultPermitted();
}
