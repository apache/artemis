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
package org.apache.activemq.artemis.protocol.amqp.proton;

import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.routing.RoutingHandler;
import org.apache.activemq.artemis.utils.ConfigurationHelper;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.transport.ConnectionError;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.engine.Connection;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class AMQPRoutingHandler extends RoutingHandler<AMQPRoutingContext> {

   public AMQPRoutingHandler(ActiveMQServer server) {
      super(server);
   }


   public boolean route(AMQPConnectionContext connectionContext, Connection protonConnection) throws Exception {
      return route(new AMQPRoutingContext(connectionContext, protonConnection));
   }

   @Override
   protected void refuse(AMQPRoutingContext context) {
      ErrorCondition error = new ErrorCondition();
      error.setCondition(ConnectionError.CONNECTION_FORCED);
      switch (context.getResult().getStatus()) {
         case REFUSED_USE_ANOTHER:
            error.setDescription(String.format("Connection router %s rejected this connection", context.getRouter()));
            break;
         case REFUSED_UNAVAILABLE:
            error.setDescription(String.format("Connection router %s is not ready", context.getRouter()));
            break;
      }

      Connection protonConnection = context.getProtonConnection();
      protonConnection.setCondition(error);
      protonConnection.setProperties(Collections.singletonMap(AmqpSupport.CONNECTION_OPEN_FAILED, true));
   }

   @Override
   protected void redirect(AMQPRoutingContext context) {
      String host = ConfigurationHelper.getStringProperty(TransportConstants.HOST_PROP_NAME, TransportConstants.DEFAULT_HOST, context.getTarget().getConnector().getParams());
      int port = ConfigurationHelper.getIntProperty(TransportConstants.PORT_PROP_NAME, TransportConstants.DEFAULT_PORT, context.getTarget().getConnector().getParams());

      ErrorCondition error = new ErrorCondition();
      error.setCondition(ConnectionError.REDIRECT);
      error.setDescription(String.format("Connection router %s redirected this connection to %s:%d", context.getRouter(), host, port));
      Map<Symbol, Object> info = new HashMap<>();
      info.put(AmqpSupport.NETWORK_HOST, host);
      info.put(AmqpSupport.PORT, port);
      error.setInfo(info);

      Connection protonConnection = context.getProtonConnection();
      protonConnection.setCondition(error);
      protonConnection.setProperties(Collections.singletonMap(AmqpSupport.CONNECTION_OPEN_FAILED, true));
   }
}
