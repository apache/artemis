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
package org.apache.activemq.artemis.core.protocol.core.impl;

import org.apache.activemq.artemis.api.core.DisconnectReason;
import org.apache.activemq.artemis.core.protocol.core.CoreRemotingConnection;
import org.apache.activemq.artemis.core.server.ActiveMQMessageBundle;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.routing.RoutingHandler;

public class ActiveMQRoutingHandler extends RoutingHandler<ActiveMQRoutingContext> {

   public ActiveMQRoutingHandler(ActiveMQServer server) {
      super(server);
   }

   public boolean route(CoreRemotingConnection connection, String username) throws Exception {
      if (!connection.isVersionSupportRouting()) {
         throw ActiveMQMessageBundle.BUNDLE.incompatibleClientServer();
      }

      return route(new ActiveMQRoutingContext(connection, username));
   }

   @Override
   public void refuse(ActiveMQRoutingContext context) throws Exception {
      switch (context.getResult().getStatus()) {
         case REFUSED_UNAVAILABLE:
            throw ActiveMQMessageBundle.BUNDLE.connectionRouterNotReady(context.getRouter());
         case REFUSED_USE_ANOTHER:
            throw ActiveMQMessageBundle.BUNDLE.connectionRejected(context.getRouter());
      }
   }

   @Override
   public void redirect(ActiveMQRoutingContext context) throws Exception {
      context.getConnection().disconnect(DisconnectReason.REDIRECT, context.getTarget().getNodeID(), context.getTarget().getConnector());

      throw ActiveMQMessageBundle.BUNDLE.connectionRedirected(context.getRouter(), context.getTarget().getConnector());
   }
}
