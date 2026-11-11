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
package org.apache.activemq.artemis.core.protocol.mqtt;

import io.netty.handler.codec.mqtt.MqttProperties;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.routing.RoutingHandler;
import org.apache.activemq.artemis.utils.ConfigurationHelper;

import static io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType.SERVER_REFERENCE;

public class MQTTRoutingHandler extends RoutingHandler<MQTTRoutingContext> {

   protected MQTTRoutingHandler(ActiveMQServer server) {
      super(server);
   }

   public boolean route(MQTTConnection mqttConnection, MQTTSession mqttSession, String username) throws Exception {
      return route(new MQTTRoutingContext(mqttConnection, mqttSession, username));
   }

   @Override
   protected void refuse(MQTTRoutingContext context) {
      switch (context.getResult().getStatus()) {
         case REFUSED_USE_ANOTHER:
            context.getMQTTSession().getProtocolHandler().sendConnack(MQTTReasonCodes.USE_ANOTHER_SERVER);
            break;
         case REFUSED_UNAVAILABLE:
            context.getMQTTSession().getProtocolHandler().sendConnack(MQTTReasonCodes.SERVER_UNAVAILABLE);
            break;
      }
      context.getMQTTSession().getProtocolHandler().disconnect();
   }

   @Override
   protected void redirect(MQTTRoutingContext context) {
      String host = ConfigurationHelper.getStringProperty(TransportConstants.HOST_PROP_NAME, TransportConstants.DEFAULT_HOST, context.getTarget().getConnector().getParams());
      int port = ConfigurationHelper.getIntProperty(TransportConstants.PORT_PROP_NAME, TransportConstants.DEFAULT_PORT, context.getTarget().getConnector().getParams());

      MqttProperties mqttProperties = new MqttProperties();
      mqttProperties.add(new MqttProperties.StringProperty(SERVER_REFERENCE.value(), String.format("%s:%d", host, port)));

      context.getMQTTSession().getProtocolHandler().sendConnack(MQTTReasonCodes.USE_ANOTHER_SERVER, mqttProperties);
      context.getMQTTSession().getProtocolHandler().disconnect();
   }
}
