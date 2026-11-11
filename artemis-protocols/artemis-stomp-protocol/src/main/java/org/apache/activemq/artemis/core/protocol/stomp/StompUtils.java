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
package org.apache.activemq.artemis.core.protocol.stomp;

import static org.apache.activemq.artemis.api.core.Message.HDR_SCHEDULED_DELIVERY_TIME;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.client.impl.ClientMessageImpl;
import org.apache.activemq.artemis.reader.MessageUtil;

public class StompUtils {

   private static final String DEFAULT_MESSAGE_PRIORITY = "4";


   public static void copyStandardHeadersFromFrameToMessage(StompFrame frame, Message msg, String prefix) throws Exception {
      Map<String, String> headers = new HashMap<>(frame.getHeadersMap());

      String priority = headers.remove(Stomp.Headers.Send.PRIORITY);
      if (priority != null) {
         msg.setPriority(Byte.parseByte(priority));
      } else {
         msg.setPriority(Byte.parseByte(DEFAULT_MESSAGE_PRIORITY));
      }
      String persistent = headers.remove(Stomp.Headers.Send.PERSISTENT);
      if (persistent != null) {
         msg.setDurable(Boolean.parseBoolean(persistent));
      }
      String correlationID = headers.remove(Stomp.Headers.Send.CORRELATION_ID);
      if (correlationID != null) {
         msg.putObjectProperty(MessageUtil.CORRELATIONID_HEADER_NAME, correlationID);
      }
      String type = headers.remove(Stomp.Headers.Send.TYPE);
      if (type != null) {
         msg.putObjectProperty(MessageUtil.TYPE_HEADER_NAME, type);
      }
      String groupID = headers.remove(MessageUtil.JMSXGROUPID);
      if (groupID != null) {
         msg.putStringProperty(Message.HDR_GROUP_ID, SimpleString.of(groupID));
      }
      String contentType = headers.remove(Stomp.Headers.CONTENT_TYPE);
      if (contentType != null) {
         msg.putStringProperty(Message.HDR_CONTENT_TYPE, SimpleString.of(contentType));
      }
      Object replyTo = headers.remove(Stomp.Headers.Send.REPLY_TO);
      if (replyTo != null) {
         msg.putStringProperty(ClientMessageImpl.REPLYTO_HEADER_NAME, SimpleString.of((String) replyTo));
      }
      String expiration = headers.remove(Stomp.Headers.Send.EXPIRATION_TIME);
      if (expiration != null) {
         msg.setExpiration(Long.parseLong(expiration));
      }

      // Extension headers
      String scheduledDelay = headers.remove(Stomp.Headers.Send.AMQ_SCHEDULED_DELAY);
      if (scheduledDelay != null) {
         long delay = Long.parseLong(scheduledDelay);
         if (delay > 0) {
            msg.putLongProperty(HDR_SCHEDULED_DELIVERY_TIME, System.currentTimeMillis() + delay);
         }
      }

      String scheduledTime = headers.remove(Stomp.Headers.Send.AMQ_SCHEDULED_TIME);
      if (scheduledTime != null) {
         long deliveryTime = Long.parseLong(scheduledTime);
         if (deliveryTime > 0) {
            msg.putLongProperty(HDR_SCHEDULED_DELIVERY_TIME, deliveryTime);
         }
      }

      if (prefix != null) {
         msg.putStringProperty(Message.HDR_PREFIX, prefix);
      }

      // now the general headers
      for (Entry<String, String> entry : headers.entrySet()) {
         String name = entry.getKey();
         Object value = entry.getValue();
         msg.putObjectProperty(name, value);
      }
   }

   public static void copyStandardHeadersFromMessageToFrame(Message message,
                                                            StompFrame command,
                                                            int deliveryCount) {
      SimpleString prefix = message.getSimpleStringProperty(Message.HDR_PREFIX);
      command.addHeader(Stomp.Headers.Message.DESTINATION, Objects.requireNonNullElse(prefix, "") + message.getAddress());

      if (message.getObjectProperty(MessageUtil.CORRELATIONID_HEADER_NAME) != null) {
         command.addHeader(Stomp.Headers.Message.CORRELATION_ID, message.getObjectProperty(MessageUtil.CORRELATIONID_HEADER_NAME).toString());
      }
      command.addHeader(Stomp.Headers.Message.EXPIRATION_TIME, "" + message.getExpiration());
      command.addHeader(Stomp.Headers.Message.REDELIVERED, String.valueOf(deliveryCount > 1));
      command.addHeader(Stomp.Headers.Message.PRIORITY, "" + message.getPriority());
      command.addHeader(Stomp.Headers.Message.PERSISTENT, "" + message.isDurable());
      if (message.getStringProperty(ClientMessageImpl.REPLYTO_HEADER_NAME) != null) {
         command.addHeader(Stomp.Headers.Message.REPLY_TO, message.getStringProperty(ClientMessageImpl.REPLYTO_HEADER_NAME));
      }
      command.addHeader(Stomp.Headers.Message.TIMESTAMP, "" + message.getTimestamp());

      if (message.getObjectProperty(MessageUtil.TYPE_HEADER_NAME) != null) {
         command.addHeader(Stomp.Headers.Message.TYPE, message.getObjectProperty(MessageUtil.TYPE_HEADER_NAME).toString());
      }
      if (message.getStringProperty(Message.HDR_CONTENT_TYPE.toString()) != null) {
         command.addHeader(Stomp.Headers.CONTENT_TYPE, message.getStringProperty(Message.HDR_CONTENT_TYPE.toString()));
      }
      if (message.getValidatedUserID() != null) {
         command.addHeader(Stomp.Headers.Message.VALIDATED_USER, message.getValidatedUserID());
      }
      if (message.containsProperty(Message.HDR_ROUTING_TYPE)) {
         command.addHeader(Stomp.Headers.Send.DESTINATION_TYPE, RoutingType.getType(message.getByteProperty(Message.HDR_ROUTING_TYPE.toString())).toString());
      }
      if (message.containsProperty(Message.HDR_INGRESS_TIMESTAMP)) {
         command.addHeader(Stomp.Headers.Message.INGRESS_TIMESTAMP, Long.toString(message.getLongProperty(Message.HDR_INGRESS_TIMESTAMP)));
      }

      // now let's add all the rest of the message headers
      Set<SimpleString> names = message.getPropertyNames();
      for (SimpleString name : names) {
         if (name.equals(ClientMessageImpl.REPLYTO_HEADER_NAME) ||
            name.equals(Message.HDR_CONTENT_TYPE) ||
            name.equals(Message.HDR_VALIDATED_USER) ||
            name.equals(Message.HDR_ROUTING_TYPE) ||
            name.equals(Message.HDR_PREFIX) ||
            name.equals(MessageUtil.TYPE_HEADER_NAME) ||
            name.equals(MessageUtil.CORRELATIONID_HEADER_NAME) ||
            name.toString().equals(Stomp.Headers.Message.DESTINATION)) {
            continue;
         }

         Object value = message.getObjectProperty(name);
         if (value != null) {
            command.addHeader(name.toString(), value.toString());
         } else {
            command.addHeader(name.toString(), "");
         }
      }
   }
}
