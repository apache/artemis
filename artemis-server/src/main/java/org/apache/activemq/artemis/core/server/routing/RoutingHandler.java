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

import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.routing.targets.TargetResult;

public abstract class RoutingHandler<T extends RoutingContext> {
   private final ActiveMQServer server;


   public ActiveMQServer getServer() {
      return server;
   }


   protected RoutingHandler(ActiveMQServer server) {
      this.server = server;
   }

   protected abstract void refuse(T context) throws Exception;

   protected abstract void redirect(T context) throws Exception;

   protected boolean route(T context) throws Exception {
      ConnectionRouter connectionRouter = getServer().getConnectionRouterManager().getRouter(context.getRouter());

      if (connectionRouter == null) {
         ActiveMQServerLogger.LOGGER.connectionRouterNotFound(context.getRouter());

         refuse(context);

         return true;
      }

      context.setResult(connectionRouter.getTarget(context.getTransportConnection(), context.getClientID(), context.getUsername()));

      if (TargetResult.Status.OK != context.getResult().getStatus()) {
         ActiveMQServerLogger.LOGGER.cannotRouteClientConnection(context.getTransportConnection());

         refuse(context);

         return true;
      }

      ActiveMQServerLogger.LOGGER.routeClientConnection(context.getTransportConnection(), context.getTarget());

      if (!context.getTarget().isLocal()) {
         redirect(context);

         return true;
      }

      return false;
   }
}
