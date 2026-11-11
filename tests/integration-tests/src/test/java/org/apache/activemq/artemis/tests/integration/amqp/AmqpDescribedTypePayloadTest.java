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
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Session;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpMessage;
import org.apache.activemq.transport.amqp.client.AmqpNoLocalFilter;
import org.apache.activemq.transport.amqp.client.AmqpReceiver;
import org.apache.activemq.transport.amqp.client.AmqpSender;
import org.apache.activemq.transport.amqp.client.AmqpSession;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test that the broker can pass through an AMQP message with a described type in the message body regardless of
 * transformer in use.
 */
public class AmqpDescribedTypePayloadTest extends JMSClientTestSupport {

   @Test
   @Timeout(60)
   public void testSendMessageWithDescribedTypeInBody() throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpSender sender = session.createSender(getQueueName());
      AmqpMessage message = new AmqpMessage();
      message.setDescribedType(new AmqpNoLocalFilter());
      sender.send(message);
      sender.close();

      Queue queue = getProxyToQueue(getQueueName());
      Wait.assertEquals(1, queue::getMessageCount);

      AmqpReceiver receiver = session.createReceiver(getQueueName());
      receiver.flow(1);
      AmqpMessage received = receiver.receive(5, TimeUnit.SECONDS);
      assertNotNull(received);
      assertNotNull(received.getDescribedType());
      receiver.close();

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testSendMessageWithDescribedTypeInBodyReceiveOverOpenWire() throws Exception {

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpSender sender = session.createSender(getQueueName());
      AmqpMessage message = new AmqpMessage();
      message.setDescribedType(new AmqpNoLocalFilter());
      sender.send(message);
      sender.close();
      connection.close();

      Queue queue = getProxyToQueue(getQueueName());
      Wait.assertEquals(1, queue::getMessageCount);

      ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(getBrokerOpenWireConnectionURI());
      Connection jmsConnection = factory.createConnection();
      try {
         Session jmsSession = jmsConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Destination destination = jmsSession.createQueue(getName());
         MessageConsumer jmsConsumer = jmsSession.createConsumer(destination);
         jmsConnection.start();

         Message received = jmsConsumer.receive(5000);
         assertNotNull(received);
         assertInstanceOf(BytesMessage.class, received);
      } finally {
         jmsConnection.close();
      }
   }

   @Test
   @Timeout(60)
   public void testDescribedTypeMessageRoundTrips() throws Exception {

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      // Send with AMQP client.
      AmqpSender sender = session.createSender(getQueueName());
      AmqpMessage message = new AmqpMessage();
      message.setDescribedType(new AmqpNoLocalFilter());
      sender.send(message);
      sender.close();

      Queue queue = getProxyToQueue(getQueueName());
      Wait.assertEquals(1, queue::getMessageCount);

      // Receive and resend with Qpid JMS client
      JmsConnectionFactory factory = new JmsConnectionFactory(getBrokerQpidJMSConnectionURI());
      Connection jmsConnection = factory.createConnection();
      try {
         Session jmsSession = jmsConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Destination destination = jmsSession.createQueue(getName());
         MessageConsumer jmsConsumer = jmsSession.createConsumer(destination);
         jmsConnection.start();

         Message received = jmsConsumer.receive(5000);
         assertNotNull(received);
         assertInstanceOf(ObjectMessage.class, received);

         MessageProducer jmsProducer = jmsSession.createProducer(destination);
         jmsProducer.send(received);
      } finally {
         jmsConnection.close();
      }

      Wait.assertEquals(1, queue::getMessageCount);

      // Now lets receive it with AMQP and see that we get back what we expected.
      AmqpReceiver receiver = session.createReceiver(getQueueName());
      receiver.flow(1);
      AmqpMessage returned = receiver.receive(5, TimeUnit.SECONDS);
      assertNotNull(returned);
      assertNotNull(returned.getDescribedType());
      receiver.close();
      connection.close();
   }
}
