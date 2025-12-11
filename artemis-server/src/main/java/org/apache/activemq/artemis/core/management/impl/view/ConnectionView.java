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
package org.apache.activemq.artemis.core.management.impl.view;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import org.apache.activemq.artemis.core.management.impl.view.predicate.ConnectionFilterPredicate;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyServerConnection;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.json.JsonObjectBuilder;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.utils.JsonLoader;
import org.apache.activemq.artemis.utils.StringUtil;

public class ConnectionView extends ActiveMQAbstractView<RemotingConnection> {

   private static final String defaultSortField = ConnectionField.CONNECTION_ID.getName();

   private final ActiveMQServer server;

   public ConnectionView(ActiveMQServer server) {
      super();
      this.server = server;
      this.predicate = new ConnectionFilterPredicate(server);
   }

   @Override
   public Class getClassT() {
      return RemotingConnection.class;
   }

   @Override
   public JsonObjectBuilder toJson(RemotingConnection connection) {

      List<ServerSession> sessions = server.getSessions(connection.getID().toString());
      Set<String> users = new TreeSet<>();
      for (ServerSession session : sessions) {
         users.add(Objects.requireNonNullElse(session.getUsername(), ""));
      }

      return JsonLoader.createObjectBuilder()
         .add(ConnectionField.CONNECTION_ID.getName(), toString(connection.getID()))
         .add(ConnectionField.REMOTE_ADDRESS.getName(), toString(connection.getRemoteAddress()))
         .add(ConnectionField.USERS.getName(), StringUtil.joinStringList(users, ","))
         .add(ConnectionField.CREATION_TIME.getName(), new Date(connection.getCreationTime()).toString())
         .add(ConnectionField.IMPLEMENTATION.getName(), toString(connection.getClass().getSimpleName()))
         .add(ConnectionField.PROTOCOL.getName(), toString(connection.getProtocolName()))
         .add(ConnectionField.CLIENT_ID.getName(), toString(connection.getClientID()))
         .add(ConnectionField.LOCAL_ADDRESS.getName(), toString(connection.getTransportLocalAddress()))
         .add(ConnectionField.SESSION_COUNT.getName(), sessions.size())
         .add(ConnectionField.PROXY_ADDRESS.getName(), toString(NettyServerConnection.getProxyAddress(connection.getTransportConnection())))
         .add(ConnectionField.PROXY_PROTOCOL_VERSION.getName(), toString(NettyServerConnection.getProxyProtocolVersion(connection.getTransportConnection())));
   }

   @Override
   public Object getField(RemotingConnection connection, String fieldName) {
      ConnectionField field = ConnectionField.valueOfName(fieldName);

      switch (field) {
         case CONNECTION_ID:
            return connection.getID();
         case REMOTE_ADDRESS:
            return connection.getRemoteAddress();
         case USERS:
            Set<String> users = new TreeSet<>();
            List<ServerSession> sessions = server.getSessions(connection.getID().toString());
            for (ServerSession session : sessions) {
               users.add(Objects.requireNonNullElse(session.getUsername(), ""));
            }
            return StringUtil.joinStringList(users, ",");
         case CREATION_TIME:
            return new Date(connection.getCreationTime());
         case IMPLEMENTATION:
            return connection.getClass().getSimpleName();
         case PROTOCOL:
            return connection.getProtocolName();
         case CLIENT_ID:
            return connection.getClientID();
         case LOCAL_ADDRESS:
            return connection.getTransportLocalAddress();
         case SESSION_COUNT:
            return server.getSessions(connection.getID().toString()).size();
         case PROXY_ADDRESS:
            return NettyServerConnection.getProxyAddress(connection.getTransportConnection());
         case PROXY_PROTOCOL_VERSION:
            return NettyServerConnection.getProxyProtocolVersion(connection.getTransportConnection());
         default:
            throw new IllegalArgumentException("Unsupported field, " + fieldName);
      }
   }

   @Override
   public String getDefaultOrderColumn() {
      return defaultSortField;
   }
}
