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

package org.apache.activemq.artemis.core.protocol.hornetq;

import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.CreateQueueMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.CreateSharedQueueMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionCreateConsumerMessage;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.utils.SelectorTranslator;

public class HQFilterConversionInterceptor implements Interceptor {
   @Override
   public boolean intercept(Packet packet, RemotingConnection connection) {
      if (packet.getType() == PacketImpl.SESS_CREATECONSUMER) {
         handleMessage((SessionCreateConsumerMessage) packet);
      } else if (packet.getType() == PacketImpl.CREATE_QUEUE || packet.getType() == PacketImpl.CREATE_QUEUE_V2) {
         handleMessage((CreateQueueMessage) packet);
      } else if (packet.getType() == PacketImpl.CREATE_SHARED_QUEUE || packet.getType() == PacketImpl.CREATE_SHARED_QUEUE_V2) {
         handleMessage((CreateSharedQueueMessage) packet);
      }
      return true;
   }

   private void handleMessage(SessionCreateConsumerMessage message) {
      message.setFilterString(replaceFilterString(message.getFilterString()));
   }

   private void handleMessage(CreateQueueMessage message) {
      message.setFilterString(replaceFilterString(message.getFilterString()));
   }

   private void handleMessage(CreateSharedQueueMessage message) {
      message.setFilterString(replaceFilterString(message.getFilterString()));
   }

   private SimpleString replaceFilterString(SimpleString filterString) {
      if (filterString == null) {
         return null;
      }
      return SimpleString.of(convertHQToActiveMQFilterString(filterString.toString()));
   }


   public static String convertHQToActiveMQFilterString(final String hqFilterString) {
      if (hqFilterString == null) {
         return null;
      }

      String filterString = SelectorTranslator.parse(hqFilterString, "HQDurable", "AMQDurable");
      filterString = SelectorTranslator.parse(filterString, "HQPriority", "AMQPriority");
      filterString = SelectorTranslator.parse(filterString, "HQTimestamp", "AMQTimestamp");
      filterString = SelectorTranslator.parse(filterString, "HQUserID", "AMQUserID");
      filterString = SelectorTranslator.parse(filterString, "HQExpiration", "AMQExpiration");

      return filterString;

   }

}
