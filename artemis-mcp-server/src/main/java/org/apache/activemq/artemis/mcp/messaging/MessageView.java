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
package org.apache.activemq.artemis.mcp.messaging;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.TextMessage;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MessageView {

   private static final int BODY_PREVIEW_LIMIT = 512;

   private MessageView() {
   }

   public static Map<String, Object> describe(Message message) throws JMSException {
      Map<String, Object> view = new LinkedHashMap<>();
      view.put("messageId", message.getJMSMessageID());
      view.put("correlationId", message.getJMSCorrelationID());
      view.put("timestamp", message.getJMSTimestamp());
      view.put("priority", message.getJMSPriority());
      view.put("redelivered", message.getJMSRedelivered());
      view.put("type", message.getClass().getSimpleName());
      if (message instanceof TextMessage textMessage) {
         view.put("body", truncate(textMessage.getText()));
      }
      Map<String, Object> properties = new LinkedHashMap<>();
      Enumeration<?> names = message.getPropertyNames();
      while (names.hasMoreElements()) {
         String name = (String) names.nextElement();
         properties.put(name, message.getObjectProperty(name));
      }
      if (!properties.isEmpty()) {
         view.put("properties", properties);
      }
      return view;
   }

   private static String truncate(String body) {
      if (body == null) {
         return null;
      }
      if (body.length() <= BODY_PREVIEW_LIMIT) {
         return body;
      }
      return body.substring(0, BODY_PREVIEW_LIMIT) + "...[truncated]";
   }
}
