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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.AddressQueryResult;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport;
import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpMessage;
import org.apache.activemq.transport.amqp.client.AmqpReceiver;
import org.apache.activemq.transport.amqp.client.AmqpSender;
import org.apache.activemq.transport.amqp.client.AmqpSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class AmqpAnonymousRelayTest extends AmqpClientTestSupport {

   private static final String AUTO_CREATION_QUEUE_PREFIX = "AmqpAnonymousRelayTest-AutoCreateQueues.";
   private static final String AUTO_CREATION_TOPIC_PREFIX = "AmqpAnonymousRelayTest-AutoCreateTopics.";

   // Disable auto-creation in the general config created by the superclass, we add specific prefixed areas with it enabled
   @Override
   protected boolean isAutoCreateQueues() {
      return false;
   }

   @Override
   protected boolean isAutoCreateAddresses() {
      return false;
   }

   // Additional address configuration for auto creation of queues and topics
   @Override
   protected void configureAddressPolicy(ActiveMQServer server) {
      super.configureAddressPolicy(server);

      AddressSettings autoCreateQueueAddressSettings = new AddressSettings();
      autoCreateQueueAddressSettings.setAutoCreateQueues(true);
      autoCreateQueueAddressSettings.setAutoCreateAddresses(true);
      autoCreateQueueAddressSettings.setDefaultAddressRoutingType(RoutingType.ANYCAST);
      autoCreateQueueAddressSettings.setDefaultQueueRoutingType(RoutingType.ANYCAST);

      server.getConfiguration().getAddressSettings().put(AUTO_CREATION_QUEUE_PREFIX + "#", autoCreateQueueAddressSettings);

      AddressSettings autoCreateTopicAddressSettings = new AddressSettings();
      autoCreateTopicAddressSettings.setAutoCreateQueues(true);
      autoCreateTopicAddressSettings.setAutoCreateAddresses(true);
      autoCreateTopicAddressSettings.setDefaultAddressRoutingType(RoutingType.MULTICAST);
      autoCreateTopicAddressSettings.setDefaultQueueRoutingType(RoutingType.MULTICAST);

      server.getConfiguration().getAddressSettings().put(AUTO_CREATION_TOPIC_PREFIX + "#", autoCreateTopicAddressSettings);
   }

   @Test
   @Timeout(60)
   public void testSendMessageOnAnonymousProducerCausesQueueAutoCreation() throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());

      // We use an address in the QUEUE prefixed auto-creation area to ensure the broker picks this up
      // and creates a queue, in the absense of any other message annotation / terminus capability config.
      String queueName = AUTO_CREATION_QUEUE_PREFIX + getQueueName();
      try {
         AmqpSession session = connection.createSession();

         AmqpSender sender = session.createAnonymousSender();
         AmqpMessage message = new AmqpMessage();
         message.setAddress(queueName);
         message.setText(getTestName());

         AddressQueryResult addressQueryResult = server.addressQuery(SimpleString.of(queueName));
         assertFalse(addressQueryResult.isExists());

         sender.send(message);
         sender.close();

         addressQueryResult = server.addressQuery(SimpleString.of(queueName));
         assertTrue(addressQueryResult.isExists());
         assertTrue(addressQueryResult.getRoutingTypes().contains(RoutingType.ANYCAST));
         assertTrue(addressQueryResult.isAutoCreated());

         // Create a receiver and verify it can consume the message from the auto-created queue
         AmqpReceiver receiver = session.createReceiver(queueName);
         receiver.flow(1);
         AmqpMessage received = receiver.receive(10, TimeUnit.SECONDS);
         assertNotNull(received, "Should have read message");
         assertEquals(getTestName(), received.getText());
         received.accept();

         receiver.close();
      } finally {
         connection.close();
      }
   }

   @Test
   @Timeout(60)
   public void testSendMessageOnAnonymousProducerCausesTopicAutoCreation() throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());

      // We use an address in the TOPIC prefixed auto-creation area to ensure the broker picks this up
      // and creates a topic, in the absense of any other message annotation / terminus capability config.
      String topicName = AUTO_CREATION_TOPIC_PREFIX + getTopicName();

      try {
         AmqpSession session = connection.createSession();

         AmqpSender sender = session.createAnonymousSender();
         AmqpMessage message = new AmqpMessage();

         message.setAddress(topicName);
         message.setText("creating-topic-address");

         AddressQueryResult addressQueryResult = server.addressQuery(SimpleString.of(topicName));
         assertFalse(addressQueryResult.isExists());

         sender.send(message);

         addressQueryResult = server.addressQuery(SimpleString.of(topicName));
         assertTrue(addressQueryResult.isExists());
         assertTrue(addressQueryResult.getRoutingTypes().contains(RoutingType.MULTICAST));
         assertTrue(addressQueryResult.isAutoCreated());

         // Create 2 receivers and verify they can both consume a new message sent to the auto-created topic
         AmqpReceiver receiver1 = session.createReceiver(topicName);
         AmqpReceiver receiver2 = session.createReceiver(topicName);
         receiver1.flow(1);
         receiver2.flow(1);

         AmqpMessage message2 = new AmqpMessage();
         message2.setAddress(topicName);
         message2.setText(getTestName());

         sender.send(message2);

         AmqpMessage received1 = receiver1.receive(10, TimeUnit.SECONDS);
         assertNotNull(received1, "Should have read message");
         assertEquals(getTestName(), received1.getText());
         received1.accept();

         AmqpMessage received2 = receiver2.receive(10, TimeUnit.SECONDS);
         assertNotNull(received2, "Should have read message");
         assertEquals(getTestName(), received2.getText());
         received1.accept();

         receiver1.close();
         receiver2.close();
         sender.close();
      } finally {
         connection.close();
      }
   }

   @Test
   @Timeout(60)
   public void testSendMessageOnAnonymousProducerWithDestinationTypeAnnotationCausesQueueAutoCreation() throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());

      try {
         AmqpSession session = connection.createSession();

         AmqpSender sender = session.createAnonymousSender();
         AmqpMessage message = new AmqpMessage();
         message.setMessageAnnotation(AMQPMessageSupport.JMS_DEST_TYPE_MSG_ANNOTATION.toString(), AMQPMessageSupport.QUEUE_TYPE);

         // We deliberately use the TOPIC prefixed auto-creation area, not the QUEUE prefix, to ensure
         // we get a queue because the broker inspects the value we send on the message, and not just
         // because it was taken as a default from the address settings.
         String queueName = AUTO_CREATION_TOPIC_PREFIX + getQueueName();

         message.setAddress(queueName);
         message.setText(getTestName());

         AddressQueryResult addressQueryResult = server.addressQuery(SimpleString.of(queueName));
         assertFalse(addressQueryResult.isExists());

         sender.send(message);
         sender.close();

         addressQueryResult = server.addressQuery(SimpleString.of(queueName));
         assertTrue(addressQueryResult.isExists());
         assertTrue(addressQueryResult.getRoutingTypes().contains(RoutingType.ANYCAST));
         assertTrue(addressQueryResult.isAutoCreated());

         // Create a receiver and verify it can consume the message from the auto-created queue
         AmqpReceiver receiver = session.createReceiver(queueName);
         receiver.flow(1);
         AmqpMessage received = receiver.receive(10, TimeUnit.SECONDS);
         assertNotNull(received, "Should have read message");
         assertEquals(getTestName(), received.getText());
         received.accept();

         receiver.close();
      } finally {
         connection.close();
      }
   }

   @Test
   @Timeout(60)
   public void testSendMessageOnAnonymousProducerWithDestinationTypeAnnotationCausesTopicAutoCreation() throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());

      try {
         AmqpSession session = connection.createSession();

         AmqpSender sender = session.createAnonymousSender();
         AmqpMessage message = new AmqpMessage();
         message.setMessageAnnotation(AMQPMessageSupport.JMS_DEST_TYPE_MSG_ANNOTATION.toString(), AMQPMessageSupport.TOPIC_TYPE);

         // We deliberately use the QUEUE prefixed auto-creation area, not the TOPIC prefix, to ensure
         // we get a topic because the broker inspects the value we send on the message, and not just
         // because it was taken as a default from the address settings.
         String topicName = AUTO_CREATION_QUEUE_PREFIX + getTopicName();
         message.setAddress(topicName);
         message.setText("creating-topic-address");

         AddressQueryResult addressQueryResult = server.addressQuery(SimpleString.of(topicName));
         assertFalse(addressQueryResult.isExists());

         sender.send(message);

         addressQueryResult = server.addressQuery(SimpleString.of(topicName));
         assertTrue(addressQueryResult.isExists());
         assertTrue(addressQueryResult.getRoutingTypes().contains(RoutingType.MULTICAST));
         assertTrue(addressQueryResult.isAutoCreated());

         // Create 2 receivers and verify they can both consume a new message sent to the auto-created topic
         AmqpReceiver receiver1 = session.createReceiver(topicName);
         AmqpReceiver receiver2 = session.createReceiver(topicName);
         receiver1.flow(1);
         receiver2.flow(1);

         AmqpMessage message2 = new AmqpMessage();
         message2.setMessageAnnotation(AMQPMessageSupport.JMS_DEST_TYPE_MSG_ANNOTATION.toString(), AMQPMessageSupport.TOPIC_TYPE);
         message2.setAddress(topicName);
         message2.setText(getTestName());

         sender.send(message2);

         AmqpMessage received1 = receiver1.receive(10, TimeUnit.SECONDS);
         assertNotNull(received1, "Should have read message");
         assertEquals(getTestName(), received1.getText());
         received1.accept();

         AmqpMessage received2 = receiver2.receive(10, TimeUnit.SECONDS);
         assertNotNull(received2, "Should have read message");
         assertEquals(getTestName(), received2.getText());
         received1.accept();

         receiver1.close();
         receiver2.close();
         sender.close();
      } finally {
         connection.close();
      }
   }

   @Test
   @Timeout(60)
   public void testSendMessageOnAnonymousRelayLinkUsingMessageTo() throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());

      try {
         AmqpSession session = connection.createSession();

         AmqpSender sender = session.createAnonymousSender();
         AmqpMessage message = new AmqpMessage();

         message.setAddress(getQueueName());
         message.setMessageId("msg" + 1);
         message.setText("Test-Message");

         sender.send(message);
         sender.close();

         AmqpReceiver receiver = session.createReceiver(getQueueName());
         receiver.flow(1);
         AmqpMessage received = receiver.receive(10, TimeUnit.SECONDS);
         assertNotNull(received, "Should have read message");
         assertEquals("msg1", received.getMessageId());
         received.accept();

         receiver.close();
      } finally {
         connection.close();
      }
   }

   @Test
   @Timeout(60)
   public void testSendMessageFailsOnAnonymousRelayLinkWhenNoToValueSet() throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());

      try {
         AmqpSession session = connection.createSession();

         AmqpSender sender = session.createAnonymousSender();
         AmqpMessage message = new AmqpMessage();

         message.setMessageId("msg" + 1);
         message.setText("Test-Message");

         try {
            sender.send(message);
            fail("Should not be able to send, message should be rejected");
         } catch (Exception ex) {
            ex.printStackTrace();
         } finally {
            sender.close();
         }
      } finally {
         connection.close();
      }
   }

   @Test
   @Timeout(60)
   public void testSendMessageFailsOnAnonymousRelayWhenToFieldHasNonExistingAddress() throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());

      try {
         AmqpSession session = connection.createSession();

         AmqpSender sender = session.createAnonymousSender();
         AmqpMessage message = new AmqpMessage();

         message.setAddress("exampleQueu-not-in-service");
         message.setMessageId("msg" + 1);
         message.setText("Test-Message");

         try {
            sender.send(message);
            fail("Should not be able to send, message should be rejected");
         } catch (Exception ex) {
            ex.printStackTrace();
         } finally {
            sender.close();
         }
      } finally {
         connection.close();
      }
   }
}
