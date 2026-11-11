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
package org.apache.activemq.artemis.core.protocol.openwire;

import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.routing.RoutingHandler;
import org.apache.activemq.artemis.utils.ConfigurationHelper;
import org.apache.activemq.command.ConnectionControl;
import org.apache.activemq.command.ConnectionInfo;

public class OpenWireRoutingHandler extends RoutingHandler<OpenWireRoutingContext> {

   private final OpenWireProtocolManager protocolManager;

   protected OpenWireRoutingHandler(ActiveMQServer server, OpenWireProtocolManager protocolManager) {
      super(server);
      this.protocolManager = protocolManager;
   }

   public boolean route(OpenWireConnection openWireConnection, ConnectionInfo connectionInfo) throws Exception {
      if (!connectionInfo.isFaultTolerant()) {
         throw new java.lang.IllegalStateException("Client not fault tolerant");
      }

      return route(new OpenWireRoutingContext(openWireConnection, connectionInfo));
   }

   @Override
   protected void refuse(OpenWireRoutingContext context) throws Exception {
   }

   @Override
   protected void redirect(OpenWireRoutingContext context) throws Exception {
      String host = ConfigurationHelper.getStringProperty(TransportConstants.HOST_PROP_NAME, TransportConstants.DEFAULT_HOST, context.getTarget().getConnector().getParams());
      int port = ConfigurationHelper.getIntProperty(TransportConstants.PORT_PROP_NAME, TransportConstants.DEFAULT_PORT, context.getTarget().getConnector().getParams());

      ConnectionControl command = protocolManager.newConnectionControl();
      command.setConnectedBrokers(String.format("tcp://%s:%d", host, port));
      command.setRebalanceConnection(true);
      context.getOpenWireConnection().dispatchSync(command);
   }
}
