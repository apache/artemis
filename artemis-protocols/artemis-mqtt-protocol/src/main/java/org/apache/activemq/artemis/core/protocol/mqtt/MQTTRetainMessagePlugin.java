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
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.RoutingContext;
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

   /**
    * reacting to a retain header on all messages published, useful when a mqtt message is forwarded from
    * another broker. It won't use the mqtt protocol head in that case.
    * The MQTTRetainMessageManager delegates to this plugin when it is present in broker config.
    * see org.apache.activemq.artemis.core.protocol.mqtt.MQTTRetainMessageManager#handleRetainedMessage
    */
   @Override
   public void beforeMessageRoute(Message message, RoutingContext context, boolean direct, boolean rejectDuplicates) throws ActiveMQException {
      try {
         Boolean isRetain = message.getBooleanProperty(MQTTUtil.MQTT_MESSAGE_RETAIN_KEY);
         if (isRetain == null || !isRetain) {
            return;
         }

         final String address = message.getAddress();
         if (address == null) {
            return;
         }

         final String retainAddress = MQTTUtil.MQTT_RETAIN_ADDRESS_PREFIX + address;
         final Queue queue = server.createQueue(QueueConfiguration.of(retainAddress).setAutoCreated(true), true);

         queue.deleteAllReferences();

         // retain only if non-empty
         if (message.isLargeMessage() || message.toCore().getBodyBufferSize() > 0) {
            final StorageManager storageManager = server.getStorageManager();
            MQTTUtil.sendMessageDirectlyToQueue(storageManager, server.getPostOffice(), message.copy(storageManager.generateID()), queue, context.getTransaction());
         }
      } catch (Exception e) {
         logger.warn("Failed to handle MQTT retained message for address {}: {}", message.getAddress(), e.getMessage(), e);
      }
   }

   @Override
   public int hashCode() {
      return System.identityHashCode(MQTTRetainMessagePlugin.class);
   }

   @Override
   public boolean equals(Object obj) {
      return obj instanceof MQTTRetainMessagePlugin;
   }
}
