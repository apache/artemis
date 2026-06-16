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
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.Session;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.mcp.connection.ArtemisConnectionManager;

public final class ArtemisBrowseClient {

   private final ArtemisConnectionManager connectionManager;

   public ArtemisBrowseClient(ArtemisConnectionManager connectionManager) {
      this.connectionManager = connectionManager;
   }

   public List<Map<String, Object>> browse(String queueName, int limit, String selector) throws JMSException {
      List<Map<String, Object>> messages = new ArrayList<>();
      try (Connection connection = connectionManager.newStartedConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queue = session.createQueue(queueName);
         QueueBrowser browser = (selector == null || selector.isBlank())
            ? session.createBrowser(queue)
            : session.createBrowser(queue, selector);
         try {
            Enumeration<?> enumeration = browser.getEnumeration();
            int count = 0;
            while (enumeration.hasMoreElements() && count < limit) {
               Message message = (Message) enumeration.nextElement();
               messages.add(MessageView.describe(message));
               count++;
            }
         } finally {
            browser.close();
         }
      }
      return messages;
   }
}
