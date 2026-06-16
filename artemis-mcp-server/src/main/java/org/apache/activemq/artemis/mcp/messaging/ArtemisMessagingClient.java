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

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.mcp.connection.ArtemisConnectionManager;

public final class ArtemisMessagingClient {

   private final ArtemisConnectionManager connectionManager;

   public ArtemisMessagingClient(ArtemisConnectionManager connectionManager) {
      this.connectionManager = connectionManager;
   }

   public Map<String, Object> send(String target, String body, Map<String, Object> properties, boolean durable)
      throws JMSException {
      try (Connection connection = connectionManager.newStartedConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queue = session.createQueue(target);
         MessageProducer producer = session.createProducer(queue);
         producer.setDeliveryMode(durable ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT);
         TextMessage message = session.createTextMessage(body);
         if (properties != null) {
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
               message.setObjectProperty(entry.getKey(), entry.getValue());
            }
         }
         producer.send(message);
         Map<String, Object> result = new LinkedHashMap<>();
         result.put("status", "ok");
         result.put("messageId", message.getJMSMessageID());
         result.put("timestamp", message.getJMSTimestamp());
         return result;
      }
   }

   public List<Map<String, Object>> consume(String queueName, int limit, String selector, long timeoutMillis)
      throws JMSException {
      List<Map<String, Object>> messages = new ArrayList<>();
      try (Connection connection = connectionManager.newStartedConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queue = session.createQueue(queueName);
         MessageConsumer consumer = (selector == null || selector.isBlank())
            ? session.createConsumer(queue)
            : session.createConsumer(queue, selector);
         try {
            for (int count = 0; count < limit; count++) {
               Message message = consumer.receive(timeoutMillis);
               if (message == null) {
                  break;
               }
               messages.add(MessageView.describe(message));
            }
         } finally {
            consumer.close();
         }
      }
      return messages;
   }
}
