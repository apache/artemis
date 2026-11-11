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
package org.apache.activemq.artemis.tests.integration.amqp;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.qpid.jms.JmsConnection;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test connections can be established to remote peers via WebSockets
 */
public class JMSWebSocketConnectionTest extends JMSClientTestSupport {

   @Override
   public boolean isUseWebSockets() {
      return true;
   }

   @Test
   @Timeout(30)
   public void testCreateConnectionAndStart() throws Exception {
      JmsConnectionFactory factory = new JmsConnectionFactory(getBrokerQpidJMSConnectionURI());
      JmsConnection connection = (JmsConnection) factory.createConnection();
      assertNotNull(connection);
      connection.start();
      connection.close();
   }

   @Test
   @Timeout(30)
   public void testSendReceiveOverWS() throws Exception {
      JmsConnectionFactory factory = new JmsConnectionFactory(getBrokerQpidJMSConnectionURI());
      JmsConnection connection = (JmsConnection) factory.createConnection();

      try {
         Session session = connection.createSession();
         Queue queue = session.createQueue(getQueueName());

         MessageProducer producer = session.createProducer(queue);
         producer.send(session.createMessage());
         producer.close();

         connection.start();

         MessageConsumer consumer = session.createConsumer(queue);
         Message message = consumer.receive(1000);

         assertNotNull(message);
      } finally {
         connection.close();
      }
   }

   @Test
   @Timeout(30)
   public void testSendLargeMessageToClientFromOpenWire() throws Exception {
      JmsConnectionFactory factory = new JmsConnectionFactory(getBrokerQpidJMSConnectionURI());
      JmsConnection connection = (JmsConnection) factory.createConnection();

      sendLargeMessageViaOpenWire();

      try {
         Session session = connection.createSession();
         Queue queue = session.createQueue(getQueueName());
         connection.start();

         MessageConsumer consumer = session.createConsumer(queue);
         Message message = consumer.receive(1000);

         assertNotNull(message);
         assertInstanceOf(BytesMessage.class, message);
      } finally {
         connection.close();
      }
   }

   @Disabled("Broker can't accept messages over 65535 right now")
   @Test
   @Timeout(30)
   public void testSendLargeMessageToClientFromAMQP() throws Exception {
      JmsConnectionFactory factory = new JmsConnectionFactory(getBrokerQpidJMSConnectionURI());
      JmsConnection connection = (JmsConnection) factory.createConnection();

      sendLargeMessageViaAMQP();

      try {
         Session session = connection.createSession();
         Queue queue = session.createQueue(getQueueName());
         connection.start();

         MessageConsumer consumer = session.createConsumer(queue);
         Message message = consumer.receive(1000);

         assertNotNull(message);
         assertInstanceOf(BytesMessage.class, message);
      } finally {
         connection.close();
      }
   }

   protected void sendLargeMessageViaOpenWire() throws Exception {
      ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(getBrokerOpenWireConnectionURI());
      doSendLargeMessageViaOpenWire(factory.createConnection());
   }

   protected void sendLargeMessageViaAMQP() throws Exception {
      JmsConnectionFactory factory = new JmsConnectionFactory(getBrokerQpidJMSConnectionURI());
      doSendLargeMessageViaOpenWire(factory.createConnection());
   }

   protected void doSendLargeMessageViaOpenWire(Connection connection) throws Exception {
      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queue = session.createQueue(getQueueName());
         MessageProducer producer = session.createProducer(queue);

         // Normal netty default max frame size is 65535 so bump up the size a bit
         // to see if we can handle it
         byte[] payload = new byte[65535 + 8192];
         for (int i = 0; i < payload.length; ++i) {
            payload[i] = (byte) (i % 256);
         }
         BytesMessage message = session.createBytesMessage();
         message.writeBytes(payload);

         producer.send(message);
      } finally {
         connection.close();
      }
   }
}
