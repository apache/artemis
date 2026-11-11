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
package org.apache.activemq.artemis.spi.core.protocol;

import java.util.List;
import java.util.Map;

import io.netty.channel.ChannelPipeline;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.BaseInterceptor;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyServerConnection;
import org.apache.activemq.artemis.core.server.routing.RoutingHandler;
import org.apache.activemq.artemis.spi.core.remoting.Acceptor;
import org.apache.activemq.artemis.spi.core.remoting.Connection;

/**
 * Info: ProtocolManager is loaded by
 * {@link
 * org.apache.activemq.artemis.core.remoting.server.impl.RemotingServiceImpl#loadProtocolManagerFactories(Iterable)}
 */
public interface ProtocolManager<P extends BaseInterceptor, R extends RoutingHandler> {

   ProtocolManagerFactory<P> getFactory();

   /**
    * This method will receive all the interceptors on the system and you should filter them out
    */
   void updateInterceptors(List<BaseInterceptor> incomingInterceptors, List<BaseInterceptor> outgoingInterceptors);

   ConnectionEntry createConnectionEntry(Acceptor acceptorUsed, Connection connection);

   default void removeHandler(String name) {
   }

   void handleBuffer(RemotingConnection connection, ActiveMQBuffer buffer);

   void addChannelHandlers(ChannelPipeline pipeline);

   boolean isProtocol(byte[] array);

   /**
    * If this protocols accepts connectoins without an initial handshake. If true this protocol will be the failback
    * case no other connections are made. New designed protocols should always require a handshake. This is only useful
    * for legacy protocols.
    */
   boolean acceptsNoHandshake();

   void handshake(NettyServerConnection connection, ActiveMQBuffer buffer);

   /**
    * A list of the IANA websocket subprotocol identifiers (https://www.iana.org/assignments/websocket/websocket.xhtml)
    * supported by this protocol manager.  These are used during the websocket subprotocol handshake.
    *
    * @return A list of subprotocol ids
    */
   List<String> websocketSubprotocolIdentifiers();

   void setAnycastPrefix(String anycastPrefix);

   void setMulticastPrefix(String multicastPrefix);

   Map<SimpleString, RoutingType> getPrefixes();

   void setSecurityDomain(String securityDomain);

   String getSecurityDomain();

   R getRoutingHandler();
}
