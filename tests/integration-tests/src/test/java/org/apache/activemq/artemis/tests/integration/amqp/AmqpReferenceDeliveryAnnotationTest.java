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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerMessagePlugin;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpMessage;
import org.apache.activemq.transport.amqp.client.AmqpReceiver;
import org.apache.activemq.transport.amqp.client.AmqpSender;
import org.apache.activemq.transport.amqp.client.AmqpSession;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.DeliveryAnnotations;
import org.junit.jupiter.api.Test;

/**
 * Test broker behavior when creating AMQP senders
 */
public class AmqpReferenceDeliveryAnnotationTest extends AmqpClientTestSupport {

   @Override
   protected void addAdditionalAcceptors(ActiveMQServer server) throws Exception {
   }

   @Test
   public void testReceiveAnnotations() throws Exception {
      internalReceiveAnnotations(false, false);
   }

   @Test
   public void testReceiveAnnotationsLargeMessage() throws Exception {
      internalReceiveAnnotations(true, false);
   }

   @Test
   public void testReceiveAnnotationsReboot() throws Exception {
      internalReceiveAnnotations(false, true);
   }

   @Test
   public void testReceiveAnnotationsLargeMessageReboot() throws Exception {
      internalReceiveAnnotations(true, true);
   }

   public void internalReceiveAnnotations(boolean largeMessage, boolean reboot) throws Exception {

      final String uuid = RandomUtil.randomUUIDString();

      server.getConfiguration().registerBrokerPlugin(new ActiveMQServerMessagePlugin() {

         @Override
         public void beforeDeliver(ServerConsumer consumer, MessageReference reference) throws ActiveMQException {
            Map<Symbol, Object> symbolObjectMap = new HashMap<>();
            DeliveryAnnotations deliveryAnnotations = new DeliveryAnnotations(symbolObjectMap);
            symbolObjectMap.put(Symbol.getSymbol("KEY"), uuid);
            reference.setProtocolData(DeliveryAnnotations.class, deliveryAnnotations);
         }
      });

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpSender sender = session.createSender(getQueueName());

      AmqpMessage message = new AmqpMessage();

      String body = largeMessage ? "*".repeat(1024 * 1024) : "test-message";

      message.setMessageId("msg" + 1);
      message.setText(body);
      message.setDurable(true);
      sender.send(message);

      message = new AmqpMessage();
      message.setMessageId("msg" + 2);
      message.setDurable(true);
      message.setText(body);
      sender.send(message);
      sender.close();

      if (reboot) {
         connection.close();
         server.stop();
         server.start();
         client = createAmqpClient();
         connection = addConnection(client.connect());
         session = connection.createSession();
      }

      AmqpReceiver receiver = session.createReceiver(getQueueName());
      receiver.flow(2);
      AmqpMessage received = receiver.receive(10, TimeUnit.SECONDS);
      assertNotNull(received, "Should have read message");
      assertEquals("msg1", received.getMessageId());
      assertEquals(uuid, received.getDeliveryAnnotation("KEY"));
      received.accept();

      received = receiver.receive(10, TimeUnit.SECONDS);
      assertNotNull(received, "Should have read message");
      assertEquals("msg2", received.getMessageId());
      assertEquals(uuid, received.getDeliveryAnnotation("KEY"));
      received.accept();

      assertNull(receiver.receiveNoWait());

      receiver.close();

      connection.close();
   }
}