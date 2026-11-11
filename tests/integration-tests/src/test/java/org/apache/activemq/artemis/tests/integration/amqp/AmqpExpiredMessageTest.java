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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.DivertConfiguration;
import org.apache.activemq.artemis.core.config.TransformerConfiguration;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.transformer.Transformer;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.collections.LinkedListIterator;
import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpMessage;
import org.apache.activemq.transport.amqp.client.AmqpReceiver;
import org.apache.activemq.transport.amqp.client.AmqpSender;
import org.apache.activemq.transport.amqp.client.AmqpSession;
import org.apache.qpid.proton.amqp.UnsignedInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AmqpExpiredMessageTest extends AmqpClientTestSupport {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Test
   @Timeout(60)
   public void testSendMessageThatIsAlreadyExpiredUsingAbsoluteTime() throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpSender sender = session.createSender(getQueueName());

      // Get the Queue View early to avoid racing the delivery.
      final Queue queueView = getProxyToQueue(getQueueName());
      assertNotNull(queueView);

      AmqpMessage message = new AmqpMessage();
      message.setAbsoluteExpiryTime(System.currentTimeMillis() - 5000);
      message.setText("Test-Message");
      sender.send(message);
      sender.close();

      // Now try and get the message
      AmqpReceiver receiver = session.createReceiver(getQueueName());
      receiver.flow(1);
      AmqpMessage received = receiver.receiveNoWait();
      assertNull(received);

      Wait.assertEquals(0, queueView::getMessageCount);
      Wait.assertEquals(1, queueView::getMessagesExpired);

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testExpiryThroughTTL() throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpSender sender = session.createSender(getQueueName());

      // Get the Queue View early to avoid racing the delivery.
      final Queue queueView = getProxyToQueue(getQueueName());
      assertNotNull(queueView);

      AmqpMessage message = new AmqpMessage();
      message.setTimeToLive(1);
      message.setText("Test-Message");
      message.setDurable(true);
      message.setApplicationProperty("key1", "Value1");
      sender.send(message);
      sender.close();

      Thread.sleep(100);

      // Now try and get the message
      AmqpReceiver receiver = session.createReceiver(getQueueName());
      receiver.flow(1);
      AmqpMessage received = receiver.receiveNoWait();
      assertNull(received);

      Wait.assertEquals(1, queueView::getMessagesExpired);

      connection.close();

      // This will stop and start the server
      // to make sure the message is decoded again from its binary format
      // avoiding any parsing cached at the server.
      server.stop();
      server.start();

      final Queue dlqView = getProxyToQueue(getDeadLetterAddress());
      assertNotNull(dlqView);
      Wait.assertEquals(1, dlqView::getMessageCount);

      client = createAmqpClient();
      connection = addConnection(client.connect());
      session = connection.createSession();

      AmqpReceiver receiverDLQ = session.createReceiver(getDeadLetterAddress());
      receiverDLQ.flow(1);
      received = receiverDLQ.receive(5, TimeUnit.SECONDS);

      assertNotNull(received, "Should have read message from DLQ");
      assertEquals(0, received.getTimeToLive());
      assertNotNull(received);
      assertEquals("Value1", received.getApplicationProperty("key1"));

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testRetryExpiry() throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpSender sender = session.createSender(getQueueName());

      // Get the Queue View early to avoid racing the delivery.
      final Queue queueView = getProxyToQueue(getQueueName());
      assertNotNull(queueView);

      AmqpMessage message = new AmqpMessage();
      message.setTimeToLive(1);
      message.setText("Test-Message");
      message.setDurable(true);
      message.setApplicationProperty("key1", "Value1");
      sender.send(message);

      message = new AmqpMessage();
      message.setTimeToLive(1);
      message.setBytes(new byte[500 * 1024]);
      sender.send(message);
      sender.close();

      final Queue dlqView = getProxyToQueue(getDeadLetterAddress());

      Wait.assertEquals(2, dlqView::getMessageCount);
      assertEquals(2, dlqView.retryMessages(null));
      Wait.assertEquals(0, dlqView::getMessageCount);
      Wait.assertEquals(2, queueView::getMessageCount);


      AmqpReceiver receiver = session.createReceiver(getQueueName());
      // Now try and get the message
      receiver.flow(2);
      for (int i = 0; i < 2; i++) {
         AmqpMessage received = receiver.receive(5, TimeUnit.SECONDS);
         assertNotNull(received);
         received.accept();
      }
      connection.close();
      Wait.assertEquals(0, queueView::getMessageCount);
      Wait.assertEquals(0, dlqView::getMessageCount);
   }

   /**
    * This test is validating a broker feature where the message copy through the DLQ will receive an annotation. It is
    * also testing filter on that annotation.
    */
   @Test
   @Timeout(60)
   public void testExpiryThroughTTLValidateAnnotation() throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpSender sender = session.createSender(getQueueName());

      // Get the Queue View early to avoid racing the delivery.
      final Queue queueView = getProxyToQueue(getQueueName());
      assertNotNull(queueView);

      AmqpMessage message = new AmqpMessage();
      message.setTimeToLive(1);
      message.setText("Test-Message");
      message.setDurable(true);
      message.setApplicationProperty("key1", "Value1");
      sender.send(message);
      sender.close();

      Thread.sleep(100);

      // Now try and get the message
      AmqpReceiver receiver = session.createReceiver(getQueueName());
      receiver.flow(1);
      AmqpMessage received = receiver.receiveNoWait();
      assertNull(received);

      Wait.assertEquals(1, queueView::getMessagesExpired);

      connection.close();

      // This will stop and start the server
      // to make sure the message is decoded again from its binary format
      // avoiding any parsing cached at the server.
      server.stop();
      server.start();

      final Queue dlqView = getProxyToQueue(getDeadLetterAddress());
      assertNotNull(dlqView);
      Wait.assertEquals(1, dlqView::getMessageCount);

      client = createAmqpClient();
      connection = addConnection(client.connect());
      session = connection.createSession();

      AmqpReceiver receiverDLQ = session.createReceiver(getDeadLetterAddress(), "\"m.x-opt-ORIG-ADDRESS\"='" + getQueueName() + "'");
      receiverDLQ.flow(1);
      received = receiverDLQ.receive(5, TimeUnit.SECONDS);
      assertNotNull(received);
      assertEquals(getQueueName(), received.getMessageAnnotation("x-opt-ORIG-ADDRESS"));
      // close without accepting on purpose, it will issue a redelivery on the second filter
      receiverDLQ.close();

      // Redo the selection, however now using the extra-properties, since the broker will store these as extra properties on AMQP Messages
      receiverDLQ = session.createReceiver(getDeadLetterAddress(), "_AMQ_ORIG_ADDRESS='" + getQueueName() + "'");
      receiverDLQ.flow(1);
      received = receiverDLQ.receive(5, TimeUnit.SECONDS);
      assertEquals(getQueueName(), received.getMessageAnnotation("x-opt-ORIG-ADDRESS"));
      assertNotNull(received);
      received.accept();

      assertNotNull(received, "Should have read message from DLQ");
      assertEquals(0, received.getTimeToLive());
      assertNotNull(received);
      assertEquals("Value1", received.getApplicationProperty("key1"));

      connection.close();
   }

   /**
    * This test is validating a broker feature where the message copy through the DLQ will receive an annotation. It is
    * also testing filter on that annotation.
    */
   @Test
   @Timeout(60)
   public void testExpiryQpidJMS() throws Exception {
      ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", getBrokerAmqpConnectionURI().toString());
      Connection connection = factory.createConnection();
      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         javax.jms.Queue queue = session.createQueue(getQueueName());
         MessageProducer sender = session.createProducer(queue);

         // Get the Queue View early to avoid racing the delivery.
         final Queue queueView = getProxyToQueue(getQueueName());
         assertNotNull(queueView);

         sender.setTimeToLive(1);
         TextMessage message = session.createTextMessage("Test-Message");
         message.setStringProperty("key1", "Value1");
         sender.send(message);
         sender.close();

         Wait.assertEquals(1, queueView::getMessagesExpired);
         final Queue dlqView = getProxyToQueue(getDeadLetterAddress());
         assertNotNull(dlqView);
         Wait.assertEquals(1, dlqView::getMessageCount);

         connection.start();
         javax.jms.Queue queueDLQ = session.createQueue(getDeadLetterAddress());
         MessageConsumer receiverDLQ = session.createConsumer(queueDLQ, "\"m.x-opt-ORIG-ADDRESS\"='" + getQueueName() + "'");
         Message received = receiverDLQ.receive(5000);
         assertNotNull(received);
         receiverDLQ.close();
      } finally {
         connection.close();
      }

   }

   @Test
   @Timeout(60)
   public void testSendMessageThatIsNotExpiredUsingAbsoluteTime() throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpSender sender = session.createSender(getQueueName());

      // Get the Queue View early to avoid racing the delivery.
      final Queue queueView = getProxyToQueue(getQueueName());
      assertNotNull(queueView);

      AmqpMessage message = new AmqpMessage();
      message.setAbsoluteExpiryTime(System.currentTimeMillis() + 5000);
      message.setText("Test-Message");
      sender.send(message);
      sender.close();

      Wait.assertEquals(1, queueView::getMessageCount);

      // Now try and get the message
      AmqpReceiver receiver = session.createReceiver(getQueueName());
      receiver.flow(1);
      AmqpMessage received = receiver.receive(5, TimeUnit.SECONDS);
      assertNotNull(received);

      assertEquals(0, queueView.getMessagesExpired());

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testSendMessageThatIsExpiredUsingAbsoluteTimeWithLongTTL() throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpSender sender = session.createSender(getQueueName());

      // Get the Queue View early to avoid racing the delivery.
      final Queue queueView = getProxyToQueue(getQueueName());
      assertNotNull(queueView);

      AmqpMessage message = new AmqpMessage();
      message.setAbsoluteExpiryTime(System.currentTimeMillis() - 5000);
      // AET should override any TTL set
      message.setTimeToLive(60000);
      message.setText("Test-Message");
      sender.send(message);
      sender.close();

      Wait.assertEquals(1, queueView::getMessageCount);

      // Now try and get the message
      AmqpReceiver receiver = session.createReceiver(getQueueName());
      receiver.flow(1);
      AmqpMessage received = receiver.receiveNoWait();
      assertNull(received);

      Wait.assertEquals(1, queueView::getMessagesExpired);

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testSendMessageThatIsExpiredUsingTTLWhenAbsoluteIsZero() throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpSender sender = session.createSender(getQueueName());

      // Get the Queue View early to avoid racing the delivery.
      final Queue queueView = getProxyToQueue(getQueueName());
      assertNotNull(queueView);

      AmqpMessage message = new AmqpMessage();
      message.setAbsoluteExpiryTime(0);
      // AET should override any TTL set
      message.setTimeToLive(100);
      message.setText("Test-Message");
      sender.send(message);
      sender.close();

      Wait.assertEquals(1L, queueView::getMessagesExpired, 10000, 10);

      // Now try and get the message
      AmqpReceiver receiver = session.createReceiver(getQueueName());
      receiver.flow(1);
      AmqpMessage received = receiver.receiveNoWait();
      assertNull(received);

      Wait.assertEquals(1, queueView::getMessagesExpired);

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testSendMessageThatIsNotExpiredUsingAbsoluteTimeWithElapsedTTL() throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpSender sender = session.createSender(getQueueName());

      // Get the Queue View early to avoid racing the delivery.
      final Queue queueView = getProxyToQueue(getQueueName());
      assertNotNull(queueView);

      AmqpMessage message = new AmqpMessage();
      message.setAbsoluteExpiryTime(System.currentTimeMillis() + 5000);
      // AET should override any TTL set
      message.setTimeToLive(10);
      message.setText("Test-Message");
      sender.send(message);
      sender.close();

      Wait.assertEquals(1, queueView::getMessageCount);

      // Now try and get the message
      AmqpReceiver receiver = session.createReceiver(getQueueName());
      receiver.flow(1);
      AmqpMessage received = receiver.receive(5, TimeUnit.SECONDS);
      assertNotNull(received);

      Wait.assertEquals(0, queueView::getMessagesExpired);

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testSendMessageThatIsNotExpiredUsingTimeToLive() throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpSender sender = session.createSender(getQueueName());

      // Get the Queue View early to avoid racing the delivery.
      final Queue queueView = getProxyToQueue(getQueueName());
      assertNotNull(queueView);

      AmqpMessage message = new AmqpMessage();
      message.setTimeToLive(5000);
      message.setText("Test-Message");
      sender.send(message);
      sender.close();

      Wait.assertEquals(1, queueView::getMessageCount);

      // Now try and get the message
      AmqpReceiver receiver = session.createReceiver(getQueueName());
      receiver.flow(1);
      AmqpMessage received = receiver.receive(5, TimeUnit.SECONDS);
      assertNotNull(received);

      Wait.assertEquals(0, queueView::getMessagesExpired);

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testSendMessageThatIsNotExpiredUsingTimeToLiveOfMaxUInt() throws Exception {
      doTestSendMessageThatIsNotExpiredUsingTimeToLive(UnsignedInteger.MAX_VALUE);
   }

   @Test
   @Timeout(60)
   public void testSendMessageThatIsNotExpiredUsingTimeToLiveOfMaxIntValue() throws Exception {
      doTestSendMessageThatIsNotExpiredUsingTimeToLive(UnsignedInteger.valueOf(Integer.MAX_VALUE));
   }

   @Test
   @Timeout(60)
   public void testSendMessageThatIsNotExpiredUsingTimeToLiveOfMinusOne() throws Exception {
      doTestSendMessageThatIsNotExpiredUsingTimeToLive(UnsignedInteger.valueOf(-1));
   }

   private void doTestSendMessageThatIsNotExpiredUsingTimeToLive(UnsignedInteger ttl) throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpSender sender = session.createSender(getQueueName());

      // Get the Queue View early to avoid racing the delivery.
      final Queue queueView = getProxyToQueue(getQueueName());
      assertNotNull(queueView);

      AmqpMessage message = new AmqpMessage();
      message.setTimeToLive(ttl.longValue());
      message.setText("Test-Message");
      sender.send(message);
      sender.close();

      Wait.assertEquals(1, queueView::getMessageCount);

      // Now try and get the message
      AmqpReceiver receiver = session.createReceiver(getQueueName());
      receiver.flow(1);
      AmqpMessage received = receiver.receive(5, TimeUnit.SECONDS);
      assertNotNull(received, "Should have read message but it seems to have timed out.");
      assertEquals(ttl.longValue(), received.getTimeToLive());

      Wait.assertEquals(0, queueView::getMessagesExpired);

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testSendMessageThenAllowToExpiredUsingTimeToLive() throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpSender sender = session.createSender(getQueueName());

      // Get the Queue View early to avoid racing the delivery.
      final Queue queueView = getProxyToQueue(getQueueName());
      assertNotNull(queueView);

      AmqpMessage message = new AmqpMessage();
      message.setTimeToLive(10);
      message.setText("Test-Message");
      sender.send(message);
      sender.close();

      Thread.sleep(50);

      Wait.assertEquals(0, queueView::getMessageCount);

      // Now try and get the message
      AmqpReceiver receiver = session.createReceiver(getQueueName());
      receiver.flow(1);
      AmqpMessage received = receiver.receiveNoWait();
      assertNull(received);

      Wait.assertEquals(1, queueView::getMessagesExpired);

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testExpiredMessageLandsInDLQ() throws Throwable {
      internalSendExpiry(false);
   }

   @Test
   @Timeout(60)
   public void testExpiredMessageLandsInDLQAndExistsAfterRestart() throws Throwable {
      internalSendExpiry(true);
   }

   public void internalSendExpiry(boolean restartServer) throws Throwable {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = client.connect();

      try {

         // Normal Session which won't create an TXN itself
         AmqpSession session = connection.createSession();
         AmqpSender sender = session.createSender(getQueueName());

         AmqpMessage message = new AmqpMessage();
         message.setDurable(true);
         message.setText("Test-Message");
         message.setDeliveryAnnotation("shouldDisappear", 1);
         message.setAbsoluteExpiryTime(System.currentTimeMillis() + 250);
         sender.send(message);

         Queue dlq = getProxyToQueue(getDeadLetterAddress());
         assertTrue(Wait.waitFor(() -> dlq.getMessageCount() > 0, 7000, 500), "Message not moved to DLQ");

         connection.close();

         if (restartServer) {
            server.stop();
            server.start();
         }

         connection = client.connect();
         session = connection.createSession();

         // Read all messages from the Queue
         AmqpReceiver receiver = session.createReceiver(getDeadLetterAddress());
         receiver.flow(20);

         message = receiver.receive(5, TimeUnit.SECONDS);
         assertNotNull(message);
         assertEquals(getQueueName(), message.getMessageAnnotation("x-opt-ORIG-QUEUE"));
         assertNull(message.getDeliveryAnnotation("shouldDisappear"));
         assertNull(receiver.receiveNoWait());
      } finally {
         connection.close();
      }
   }

   @Test
   @Timeout(60)
   public void testExpirationAfterDivert() throws Throwable {
      final String FORWARDING_ADDRESS = RandomUtil.randomUUIDString();
      server.createQueue(QueueConfiguration.of(FORWARDING_ADDRESS).setRoutingType(RoutingType.ANYCAST));
      server.deployDivert(new DivertConfiguration()
                             .setName(RandomUtil.randomUUIDString())
                             .setAddress(getQueueName())
                             .setForwardingAddress(FORWARDING_ADDRESS)
                             .setTransformerConfiguration(new TransformerConfiguration(MyTransformer.class.getName()))
                             .setExclusive(true));
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = client.connect();

      try {

         // Normal Session which won't create an TXN itself
         AmqpSession session = connection.createSession();
         AmqpSender sender = session.createSender(getQueueName());

         AmqpMessage message = new AmqpMessage();
         message.setDurable(true);
         message.setText("Test-Message");
         message.setDeliveryAnnotation("shouldDisappear", 1);
         message.setMessageAnnotation("x-opt-routing-type", (byte) 1);

         logger.debug("*******************************************************************************************************************************");
         logger.debug("message being sent {}", message);
         sender.send(message);
         logger.debug("*******************************************************************************************************************************");

         Queue forward = getProxyToQueue(FORWARDING_ADDRESS);
         assertTrue(Wait.waitFor(() -> forward.getMessagesAdded() > 0, 7000, 500), "Message not diverted");

         Queue dlq = getProxyToQueue(getDeadLetterAddress());
         assertTrue(Wait.waitFor(() -> dlq.getMessageCount() > 0, 7000, 500), "Message not moved to DLQ");

         connection.close();

         connection = client.connect();
         session = connection.createSession();

         // Read all messages from the Queue
         AmqpReceiver receiver = session.createReceiver(getDeadLetterAddress());
         receiver.flow(20);

         message = receiver.receive(5, TimeUnit.SECONDS);
         assertNotNull(message);
         assertEquals(FORWARDING_ADDRESS, message.getMessageAnnotation("x-opt-ORIG-QUEUE"));
         assertNull(message.getDeliveryAnnotation("shouldDisappear"));
         assertNull(receiver.receiveNoWait());
      } finally {
         connection.close();
      }
   }

   public static class MyTransformer implements Transformer {
      public MyTransformer() {
      }

      @Override
      public org.apache.activemq.artemis.api.core.Message transform(org.apache.activemq.artemis.api.core.Message message) {
         return message.setExpiration(System.currentTimeMillis() + 250);
      }
   }

   @Test
   @Timeout(60)
   public void testDLQdMessageCanBeRedeliveredMultipleTimes() throws Throwable {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = client.connect();

      try {
         AmqpSession session = connection.createSession();
         AmqpSender sender = session.createSender(getQueueName());

         AmqpMessage message = new AmqpMessage();
         message.setDurable(true);
         message.setTimeToLive(250);
         message.setText("Test-Message");
         message.setMessageId(UUID.randomUUID().toString());
         message.setApplicationProperty("key", "value");

         sender.send(message);

         Queue dlqView = getProxyToQueue(getDeadLetterAddress());
         assertTrue(Wait.waitFor(() -> dlqView.getMessageCount() > 0, 7000, 200), "Message not moved to DLQ");

         // Read and Modify the message for redelivery repeatedly
         AmqpReceiver receiver = session.createReceiver(getDeadLetterAddress());
         receiver.flow(20);

         message = receiver.receive(5, TimeUnit.SECONDS);
         assertNotNull(message);
         assertEquals(0, message.getWrappedMessage().getDeliveryCount());

         message.modified(true, false);

         message = receiver.receive(5, TimeUnit.SECONDS);
         assertNotNull(message);
         assertEquals(1, message.getWrappedMessage().getDeliveryCount());

         message.modified(true, false);

         message = receiver.receive(5, TimeUnit.SECONDS);
         assertNotNull(message);
         assertEquals(2, message.getWrappedMessage().getDeliveryCount());

         message.modified(true, false);

         message = receiver.receive(5, TimeUnit.SECONDS);
         assertNotNull(message);
         assertEquals(3, message.getWrappedMessage().getDeliveryCount());
      } finally {
         connection.close();
      }
   }

   @Test
   @Timeout(60)
   public void testExpireThroughAddressSettings() throws Exception {
      testExpireThroughAddressSettings(false);
   }

   @Test
   @Timeout(60)
   public void testExpireThroughAddressSettingsRebootServer() throws Exception {
      testExpireThroughAddressSettings(true);
   }

   private void testExpireThroughAddressSettings(boolean reboot) throws Exception {

      // Address configuration
      AddressSettings addressSettings = new AddressSettings();

      addressSettings.setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE);
      addressSettings.setAutoCreateQueues(isAutoCreateQueues());
      addressSettings.setAutoCreateAddresses(isAutoCreateAddresses());
      addressSettings.setDeadLetterAddress(SimpleString.of(getDeadLetterAddress()));
      addressSettings.setExpiryAddress(SimpleString.of(getDeadLetterAddress()));
      addressSettings.setExpiryDelay(1000L);

      server.getAddressSettingsRepository().clear();
      server.getAddressSettingsRepository().addMatch("#", addressSettings);

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpSender sender = session.createSender(getQueueName());

      // Get the Queue View early to avoid racing the delivery.
      final Queue queueView = getProxyToQueue(getQueueName());
      assertNotNull(queueView);

      AmqpMessage message = new AmqpMessage();
      message.setText("Test-Message");
      message.setDurable(true);
      message.setApplicationProperty("key1", "Value1");
      sender.send(message);

      message = new AmqpMessage();
      message.setBytes(new byte[500 * 1024]);
      message.setDurable(true);
      sender.send(message);
      sender.close();
      connection.close();

      if (reboot) {
         server.stop();
         server.getConfiguration().setMessageExpiryScanPeriod(100);
         server.start();
      }

      final Queue serverQueue = server.locateQueue(getQueueName());

      try (LinkedListIterator<MessageReference> referenceIterator = serverQueue.iterator()) {
         while (referenceIterator.hasNext()) {
            MessageReference ref = referenceIterator.next();
            assertEquals(ref.getMessage().getExpiration(), ref.getMessage().toCore().getExpiration());
            assertTrue(ref.getMessage().getExpiration() > 0);
            assertTrue(ref.getMessage().toCore().getExpiration() > 0);
         }
      }

      final Queue dlqView = getProxyToQueue(getDeadLetterAddress());

      Wait.assertEquals(2, dlqView::getMessageCount);
   }

   @Test
   public void testPreserveExpirationOnTTL() throws Exception {

      // Address configuration
      AddressSettings addressSettings = new AddressSettings();

      addressSettings.setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE);
      addressSettings.setAutoCreateQueues(isAutoCreateQueues());
      addressSettings.setAutoCreateAddresses(isAutoCreateAddresses());
      addressSettings.setDeadLetterAddress(SimpleString.of(getDeadLetterAddress()));
      addressSettings.setExpiryAddress(SimpleString.of(getDeadLetterAddress()));
      addressSettings.setExpiryDelay(1000L);

      server.getAddressSettingsRepository().clear();
      server.getAddressSettingsRepository().addMatch("#", addressSettings);

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpSender sender = session.createSender(getQueueName());

      // Get the Queue View early to avoid racing the delivery.
      final Queue queueView = getProxyToQueue(getQueueName());
      assertNotNull(queueView);

      AmqpMessage message = new AmqpMessage();
      message.setText("Test-Message");
      message.setDurable(true);
      message.setTimeToLive(3600 * 1000);
      message.setApplicationProperty("id", "0");
      sender.send(message);

      message = new AmqpMessage();
      message.setBytes(new byte[500 * 1024]);
      message.setDurable(true);
      message.setTimeToLive(3600 * 1000);
      message.setApplicationProperty("id", "1");
      sender.send(message);

      Wait.assertEquals(2, queueView::getMessageCount);
      LinkedListIterator<MessageReference> linkedListIterator = queueView.iterator();
      Map<String, Long> dataSet = new HashMap<>();
      int count = 0;
      while (linkedListIterator.hasNext()) {
         count++;
         MessageReference ref = linkedListIterator.next();
         String idUsed = ref.getMessage().getStringProperty("id");
         dataSet.put(idUsed, ref.getMessage().getExpiration());
      }

      assertEquals(2, count);
      linkedListIterator.close();

      server.stop();

      Thread.sleep(500); // we need some time passing, as the TTL can't be recalculated here
      server.getConfiguration().setMessageExpiryScanPeriod(100);
      server.start();

      final Queue queueViewAfterRestart = getProxyToQueue(getQueueName());

      Wait.assertEquals(2, queueViewAfterRestart::getMessageCount);
      Wait.assertTrue(server::isActive);

      linkedListIterator = queueViewAfterRestart.iterator();
      count = 0;
      while (linkedListIterator.hasNext()) {
         count++;
         MessageReference ref = linkedListIterator.next();
         String idUsed = ref.getMessage().getStringProperty("id");
         long originalExpiration = dataSet.get(idUsed);
         logger.info("original Expiration = {} while this expiration = {}", originalExpiration, ref.getMessage().getExpiration());
         assertEquals(originalExpiration, ref.getMessage().getExpiration());
      }
      assertEquals(2, count);
      linkedListIterator.close();
   }
}
