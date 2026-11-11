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
package org.apache.activemq.artemis.core.management.impl.view.predicate;

import org.apache.activemq.artemis.core.management.impl.view.ConnectionField;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyServerConnection;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

public class ConnectionPredicateFilterPart extends PredicateFilterPart<RemotingConnection> {
   private final ActiveMQServer server;

   private ConnectionField f;

   public ConnectionPredicateFilterPart(ActiveMQServer server, String field, String operation, String value) {
      super(operation, value);
      this.server = server;
      if (field != null && !field.isEmpty()) {
         this.f = ConnectionField.valueOfName(field);

         //for backward compatibility
         if (this.f == null) {
            this.f = ConnectionField.valueOf(field);
         }
      }
   }

   @Override
   public boolean filterPart(RemotingConnection connection) throws Exception {
      return switch (f) {
         case CONNECTION_ID -> matches(connection.getID());
         case CLIENT_ID -> matches(connection.getClientID());
         case USERS -> matchAny(collectFromSessions(connection.getID().toString(), s -> s.getUsername()));
         case PROTOCOL -> matches(connection.getProtocolName());
         case SESSION_COUNT -> matchesLong(server.getSessions(connection.getID().toString()).size());
         case REMOTE_ADDRESS -> matches(connection.getTransportConnection().getRemoteAddress());
         case LOCAL_ADDRESS -> matches(connection.getTransportConnection().getLocalAddress());
         case SESSION_ID -> matchAny(server.getSessions(connection.getID().toString()));
         case CREATION_TIME -> matchesLong(connection.getCreationTime());
         case IMPLEMENTATION -> matches(connection.getClass().getSimpleName());
         case PROXY_ADDRESS -> matches(NettyServerConnection.getProxyAddress(connection.getTransportConnection()));
         case PROXY_PROTOCOL_VERSION -> matches(NettyServerConnection.getProxyProtocolVersion(connection.getTransportConnection()));
      };
   }

   Set<String> collectFromSessions(String connectionId, Function<ServerSession, String> getter) {
      List<ServerSession> sessions = server.getSessions(connectionId);
      Set<String> sessionAttributes = new HashSet<>();
      for (ServerSession session : sessions) {
         String value = getter.apply(session);
         String string = Objects.requireNonNullElse(value, "");
         sessionAttributes.add(string);
      }
      return sessionAttributes;
   }

}
