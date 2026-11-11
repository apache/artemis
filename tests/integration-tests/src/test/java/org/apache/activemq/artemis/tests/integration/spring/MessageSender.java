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
package org.apache.activemq.artemis.tests.integration.spring;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

public class MessageSender {

   private ConnectionFactory connectionFactory;
   private Destination destination;

   public ConnectionFactory getConnectionFactory() {
      return connectionFactory;
   }

   public void setConnectionFactory(ConnectionFactory connectionFactory) {
      this.connectionFactory = connectionFactory;
   }

   public Destination getDestination() {
      return destination;
   }

   public void setDestination(Destination destination) {
      this.destination = destination;
   }

   public void send(String msg) {
      try {
         Connection conn = connectionFactory.createConnection();
         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer producer = session.createProducer(destination);
         TextMessage message = session.createTextMessage(msg);
         producer.send(message);
         conn.close();
      } catch (Exception ex) {
         ex.printStackTrace();
      }
   }
}
