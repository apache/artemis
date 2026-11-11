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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.ComponentConfigurationRoutingType;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.transformer.Transformer;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpMessage;
import org.apache.activemq.transport.amqp.client.AmqpReceiver;
import org.apache.activemq.transport.amqp.client.AmqpSender;
import org.apache.activemq.transport.amqp.client.AmqpSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class AmqpMessageDivertsTest extends AmqpClientTestSupport implements Transformer {

   static final AtomicInteger divertCount = new AtomicInteger(0);

   static final String largeString = RandomUtil.randomAlphaNumericString(500 * 1024);


   @Test
   @Timeout(60)
   public void testQueueReceiverReadMessageWithDivert() throws Exception {
      runQueueReceiverReadMessageWithDivert(ComponentConfigurationRoutingType.ANYCAST.toString());
   }

   @Test
   @Timeout(60)
   public void testQueueReceiverReadMessageWithDivertDefaultRouting() throws Exception {
      runQueueReceiverReadMessageWithDivert(ActiveMQDefaultConfiguration.getDefaultDivertRoutingType());
   }

   public void runQueueReceiverReadMessageWithDivert(String routingType) throws Exception {
      final String forwardingAddress = getQueueName() + "Divert";
      final SimpleString simpleForwardingAddress = SimpleString.of(forwardingAddress);
      server.createQueue(QueueConfiguration.of(simpleForwardingAddress).setRoutingType(RoutingType.ANYCAST));
      server.getActiveMQServerControl().createDivert("name", "routingName", getQueueName(), forwardingAddress, true, null, null, routingType);

      sendMessages(getQueueName(), 1);

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpReceiver receiver = session.createReceiver(forwardingAddress);

      Queue queueView = getProxyToQueue(forwardingAddress);
      assertEquals(1, queueView.getMessageCount());

      receiver.flow(1);
      assertNotNull(receiver.receive(5, TimeUnit.SECONDS));
      receiver.close();

      assertEquals(1, queueView.getMessageCount());

      connection.close();
   }

   @Test
   public void testDivertTransformerWithProperties() throws Exception {
      testDivertTransformerWithProperties(false);
   }

   @Test
   public void testDivertTransformerWithPropertiesRebootServer() throws Exception {
      testDivertTransformerWithProperties(true);
   }

   public void testDivertTransformerWithProperties(boolean rebootServer) throws Exception {
      divertCount.set(0);
      final String forwardingAddress = getQueueName() + "Divert";
      final SimpleString simpleForwardingAddress = SimpleString.of(forwardingAddress);
      server.createQueue(QueueConfiguration.of(simpleForwardingAddress).setRoutingType(RoutingType.ANYCAST));
      server.getActiveMQServerControl().createDivert("name", "routingName", getQueueName(),
                                                     forwardingAddress, true, null, AmqpMessageDivertsTest.class.getName(),
                                                     ComponentConfigurationRoutingType.ANYCAST.toString());

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      Queue queueView = getProxyToQueue(forwardingAddress);
      AmqpSender sender = session.createSender(getQueueName());
      AmqpMessage message = new AmqpMessage();
      message.setDurable(true);
      message.setApplicationProperty("addLarge", false);
      message.setApplicationProperty("always", "here");
      message.setBytes(new byte[10]); // one small
      sender.send(message);
      Wait.assertEquals(1, queueView::getMessageCount);

      message = new AmqpMessage();
      message.setDurable(true);
      message.setApplicationProperty("addLarge", false);
      message.setApplicationProperty("always", "here");
      message.setBytes(new byte[300 * 1024]); // one large
      sender.send(message);
      Wait.assertEquals(2, queueView::getMessageCount);

      if (rebootServer) {
         Wait.assertEquals(2, divertCount::get);
         connection.close();
         server.stop();
         server.start();

         // reopen connections
         client = createAmqpClient();
         connection = addConnection(client.connect());
         session = connection.createSession();
      } else {
         message = new AmqpMessage();
         message.setDurable(false);
         message.setBytes(new byte[300 * 1024]); // one large
         message.setApplicationProperty("addLarge", true);
         message.setApplicationProperty("always", "here");
         sender.send(message);
         Wait.assertEquals(3, divertCount::get);
      }


      AmqpReceiver receiver = session.createReceiver(forwardingAddress);

      queueView = getProxyToQueue(forwardingAddress);
      assertEquals(rebootServer ? 2 : 3, queueView.getMessageCount());

      receiver.flow(2);
      for (int i = 0; i < 2; i++) {
         AmqpMessage receivedMessage = receiver.receive(5, TimeUnit.SECONDS);
         assertNotNull(receivedMessage);
         assertEquals("here", receivedMessage.getApplicationProperty("always"));
         assertEquals("mundo", receivedMessage.getApplicationProperty("oi"));
         receivedMessage.accept();
      }

      if (!rebootServer) {
         // if we did not reboot the server a third message was sent
         receiver.flow(1);
         AmqpMessage receivedMessage = receiver.receive(5, TimeUnit.SECONDS);
         receivedMessage.accept();
         assertEquals("mundo", receivedMessage.getApplicationProperty("oi"));
         assertEquals(largeString, receivedMessage.getApplicationProperty("largeString"));

      }

      receiver.close();

      Wait.assertEquals(0, queueView::getMessageCount);

      connection.close();
   }

   @Override
   public Message transform(Message message) {
      divertCount.incrementAndGet();
      if (message.getBooleanProperty("addLarge")) {
         message.putStringProperty("largeString", largeString);
      }
      message.putBooleanProperty("oi", true);
      message.putStringProperty("oi", "mundo");
      message.reencode();
      return message;
   }
}
