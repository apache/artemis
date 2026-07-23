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
package org.apache.activemq.artemis.core.protocol.mqtt;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.core.postoffice.RoutingStatus;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.RoutingContext;
import org.apache.activemq.artemis.core.server.impl.RoutingContextImpl;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerMessagePlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class MQTTRetainMessagePlugin implements ActiveMQServerMessagePlugin {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private ActiveMQServer server;

   @Override
   public void registered(ActiveMQServer server) {
      this.server = server;
   }

   @Override
   public void afterMessageRoute(Message message, RoutingContext context, boolean direct, boolean rejectDuplicates,
                                 RoutingStatus result) throws ActiveMQException {
      try {
         Boolean isRetain = message.getBooleanProperty(MQTTUtil.MQTT_MESSAGE_RETAIN_KEY);
         if (isRetain == null || !isRetain) {
            return;
         }

         String address = message.getAddress();
         if (address == null) {
            return;
         }

         String retainQueueName = MQTTUtil.MQTT_RETAIN_ADDRESS_PREFIX + address;
         Queue retainQueue = server.createQueue(QueueConfiguration.of(retainQueueName).setAutoCreated(true), true);
         retainQueue.deleteAllReferences();

         if (message.toCore().getBodyBufferSize() > 0) {
            Message copy = message.copy(server.getStorageManager().generateID());
            RoutingContext retainContext = new RoutingContextImpl(context.getTransaction()).setMirrorOption(RoutingContext.MirrorOption.disabled);
            retainQueue.route(copy, retainContext);
            server.getPostOffice().processRoute(copy, retainContext, false);
         }
      } catch (Exception e) {
         logger.warn("Failed to handle MQTT retained message for address {}: {}", message.getAddress(), e.getMessage(), e);
      }
   }

   @Override
   public int hashCode() {
      return MQTTRetainMessagePlugin.class.hashCode();
   }

   @Override
   public boolean equals(Object obj) {
      return obj instanceof MQTTRetainMessagePlugin;
   }
}
