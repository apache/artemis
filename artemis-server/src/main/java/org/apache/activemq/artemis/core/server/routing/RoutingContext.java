/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.server.routing;

import org.apache.activemq.artemis.core.server.routing.targets.Target;
import org.apache.activemq.artemis.core.server.routing.targets.TargetResult;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.remoting.Connection;

public class RoutingContext {
   private final RemotingConnection connection;

   private final String clientID;

   private final String username;

   private TargetResult result;

   public RemotingConnection getConnection() {
      return connection;
   }

   public String getRouter() {
      return connection.getTransportConnection().getRouter();
   }

   public Connection getTransportConnection() {
      return connection.getTransportConnection();
   }

   public String getClientID() {
      return clientID;
   }

   public String getUsername() {
      return username;
   }

   public Target getTarget() {
      return result.getTarget();
   }

   public TargetResult getResult() {
      return result;
   }

   public void setResult(TargetResult result) {
      this.result = result;
   }

   public RoutingContext(RemotingConnection connection, String clientID, String username) {
      this.connection = connection;
      this.clientID = clientID;
      this.username = username;
   }
}
