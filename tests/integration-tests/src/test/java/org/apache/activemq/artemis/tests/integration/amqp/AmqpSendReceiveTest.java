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

import static org.apache.activemq.transport.amqp.AmqpSupport.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.jms.Topic;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.BridgeConfiguration;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpMessage;
import org.apache.activemq.transport.amqp.client.AmqpReceiver;
import org.apache.activemq.transport.amqp.client.AmqpSender;
import org.apache.activemq.transport.amqp.client.AmqpSession;
import org.apache.activemq.transport.amqp.client.AmqpValidator;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.UnsignedByte;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.Header;
import org.apache.qpid.proton.amqp.messaging.Source;
import org.apache.qpid.proton.amqp.messaging.TerminusDurability;
import org.apache.qpid.proton.engine.Sender;
import org.apache.qpid.proton.message.Message;
import org.jgroups.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * Test basic send and receive scenarios using only AMQP sender and receiver links.
 */
public class AmqpSendReceiveTest extends AmqpClientTestSupport {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Override
   protected boolean isAutoCreateQueues() {
      return false;
   }

   @Override
   protected boolean isAutoCreateAddresses() {
      return false;
   }

   @Test
   @Timeout(60)
   public void testAcceptWithoutSettling() throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpReceiver receiver = session.createReceiver(getQueueName());

      sendMessages(getQueueName(), 10);

      for (int i = 0; i < 10; i++) {
         receiver.flow(1);
         AmqpMessage receive = receiver.receive();
         receive.accept(false);
         receive.settle();
      }

      receiver.close();
      connection.close();

      Queue queue = getProxyToQueue(getQueueName());
      assertNotNull(queue);
      Wait.assertEquals(0, queue::getMessageCount);
   }

   @Test
   @Timeout(60)
   public void testQueueReceiverReadMessage() throws Exception {
      sendMessages(getQueueName(), 1);

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpReceiver receiver = session.createReceiver(getQueueName());

      Queue queueView = getProxyToQueue(getQueueName());
      assertEquals(1, queueView.getMessageCount());

      receiver.flow(1);
      assertNotNull(receiver.receive(5, TimeUnit.SECONDS));
      receiver.close();

      assertEquals(1, queueView.getMessageCount());

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testCoreBridge() throws Exception {
      server.getRemotingService().createAcceptor("acceptor", "vm://0").start();
      server.getConfiguration().addConnectorConfiguration("connector", "vm://0");
      server.deployBridge(new BridgeConfiguration()
                             .setName(getTestName())
                             .setQueueName(getQueueName())
                             .setForwardingAddress(getQueueName(1))
                             .setConfirmationWindowSize(10)
                             .setStaticConnectors(Arrays.asList("connector")));
      sendMessages(getQueueName(), 1);

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpReceiver receiver = session.createReceiver(getQueueName(1));

      Queue queueView = getProxyToQueue(getQueueName());
      Wait.assertEquals(1, queueView::getConsumerCount);
      Wait.assertEquals(0, queueView::getMessageCount);

      queueView = getProxyToQueue(getQueueName(1));
      Wait.assertEquals(1, queueView::getConsumerCount);
      Wait.assertEquals(1, queueView::getMessageCount);

      receiver.flow(1);
      AmqpMessage message = receiver.receive(5, TimeUnit.SECONDS);
      assertNotNull(message);
      message.accept();
      receiver.close();

      Wait.assertEquals(0, queueView::getMessageCount);

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testMessageDurableFalse() throws Exception {
      sendMessages(getQueueName(), 1, false);

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpReceiver receiver = session.createReceiver(getQueueName());

      Queue queueView = getProxyToQueue(getQueueName());
      assertEquals(1, queueView.getMessageCount());

      receiver.flow(1);
      AmqpMessage receive = receiver.receive(5, TimeUnit.SECONDS);
      assertNotNull(receive);
      assertFalse(receive.isDurable());
      receiver.close();

      assertEquals(1, queueView.getMessageCount());

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testMessageDurableTrue() throws Exception {
      assertNotNull(server.locateQueue(getQueueName()));
      sendMessages(getQueueName(), 1, true);

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpReceiver receiver = session.createReceiver(getQueueName());

      Queue queueView = getProxyToQueue(getQueueName());
      assertEquals(1, queueView.getMessageCount());

      receiver.flow(1);
      AmqpMessage receive = receiver.receive(5, TimeUnit.SECONDS);
      assertNotNull(receive);
      assertTrue(receive.isDurable());
      receiver.close();

      assertEquals(1, queueView.getMessageCount());

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testTwoQueueReceiversOnSameConnectionReadMessagesNoDispositions() throws Exception {
      int MSG_COUNT = 4;
      sendMessages(getQueueName(), MSG_COUNT);

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpReceiver receiver1 = session.createReceiver(getQueueName());

      Queue queueView = getProxyToQueue(getQueueName());
      assertEquals(MSG_COUNT, queueView.getMessageCount());

      receiver1.flow(2);
      assertNotNull(receiver1.receive(5, TimeUnit.SECONDS));
      assertNotNull(receiver1.receive(5, TimeUnit.SECONDS));

      AmqpReceiver receiver2 = session.createReceiver(getQueueName());

      assertEquals(2, server.getTotalConsumerCount());

      receiver2.flow(2);
      assertNotNull(receiver2.receive(5, TimeUnit.SECONDS));
      assertNotNull(receiver2.receive(5, TimeUnit.SECONDS));

      assertEquals(0, queueView.getMessagesAcknowledged());

      receiver1.close();
      receiver2.close();

      assertEquals(MSG_COUNT, queueView.getMessageCount());

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testTwoQueueReceiversOnSameConnectionReadMessagesAcceptOnEach() throws Exception {
      int MSG_COUNT = 4;
      sendMessages(getQueueName(), MSG_COUNT);

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpReceiver receiver1 = session.createReceiver(getQueueName());

      final Queue queueView = getProxyToQueue(getQueueName());
      assertEquals(MSG_COUNT, queueView.getMessageCount());

      receiver1.flow(2);
      AmqpMessage message = receiver1.receive(5, TimeUnit.SECONDS);
      assertNotNull(message);
      message.accept();
      message = receiver1.receive(5, TimeUnit.SECONDS);
      assertNotNull(message);
      message.accept();

      assertTrue(Wait.waitFor(() -> queueView.getMessagesAcknowledged() == 2, TimeUnit.SECONDS.toMillis(5), TimeUnit.MILLISECONDS.toMillis(50)), "Should have ack'd two");

      AmqpReceiver receiver2 = session.createReceiver(getQueueName());

      assertEquals(2, server.getTotalConsumerCount());

      receiver2.flow(2);
      message = receiver2.receive(5, TimeUnit.SECONDS);
      assertNotNull(message);
      message.accept();
      message = receiver2.receive(5, TimeUnit.SECONDS);
      assertNotNull(message);
      message.accept();

      assertTrue(Wait.waitFor(() -> queueView.getMessagesAcknowledged() == 4, TimeUnit.SECONDS.toMillis(15), TimeUnit.MILLISECONDS.toMillis(10)), "Queue should be empty now");

      receiver1.close();
      receiver2.close();

      Wait.assertEquals(0, queueView::getMessageCount);

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testSecondReceiverOnQueueGetsAllUnconsumedMessages() throws Exception {
      int MSG_COUNT = 20;
      sendMessages(getQueueName(), MSG_COUNT);

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpReceiver receiver1 = session.createReceiver(getQueueName());

      final Queue queueView = getProxyToQueue(getQueueName());
      assertEquals(MSG_COUNT, queueView.getMessageCount());

      receiver1.flow(20);

      assertTrue(Wait.waitFor(() -> queueView.getDeliveringCount() >= 2, TimeUnit.SECONDS.toMillis(5), TimeUnit.MILLISECONDS.toMillis(50)), "Should have dispatch to prefetch");

      receiver1.close();

      AmqpReceiver receiver2 = session.createReceiver(getQueueName());

      assertEquals(1, server.getTotalConsumerCount());

      receiver2.flow(MSG_COUNT * 2);
      AmqpMessage message = receiver2.receive(5, TimeUnit.SECONDS);
      assertNotNull(message);
      message.accept();
      message = receiver2.receive(5, TimeUnit.SECONDS);
      assertNotNull(message);
      message.accept();

      assertTrue(Wait.waitFor(() -> queueView.getMessagesAcknowledged() == 2, TimeUnit.SECONDS.toMillis(5), TimeUnit.MILLISECONDS.toMillis(50)), "Should have ack'd two");

      receiver2.close();

      Wait.assertEquals(MSG_COUNT - 2, queueView::getMessageCount);

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testSimpleSendOneReceiveOne() throws Exception {

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpSender sender = session.createSender(getQueueName());

      AmqpMessage message = new AmqpMessage();

      message.setMessageId("msg" + 1);
      message.setMessageAnnotation("serialNo", 1);
      message.setText("Test-Message");

      sender.send(message);
      sender.close();

      logger.debug("Attempting to read message with receiver");
      AmqpReceiver receiver = session.createReceiver(getQueueName());
      receiver.flow(2);
      AmqpMessage received = receiver.receive(10, TimeUnit.SECONDS);
      assertNotNull(received, "Should have read message");
      assertEquals("msg1", received.getMessageId());
      received.accept();

      receiver.close();

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testSendFilterAnnotation() throws Exception {

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpSender sender = session.createSender(getQueueName());

      AmqpMessage message = new AmqpMessage();

      message.setMessageId("msg" + 1);
      message.setMessageAnnotation("x-opt-serialNo", 1);
      message.setText("Test-Message");
      sender.send(message);

      message = new AmqpMessage();
      message.setMessageId("msg" + 2);
      message.setMessageAnnotation("x-opt-serialNo", 2);
      message.setText("Test-Message 2");
      sender.send(message);
      sender.close();

      logger.debug("Attempting to read message with receiver");
      AmqpReceiver receiver = session.createReceiver(getQueueName(), "\"m.x-opt-serialNo\"=2");
      receiver.flow(2);
      AmqpMessage received = receiver.receive(10, TimeUnit.SECONDS);
      assertNotNull(received, "Should have read message");
      assertEquals("msg2", received.getMessageId());
      received.accept();

      assertNull(receiver.receiveNoWait());

      receiver.close();

      connection.close();
   }


   @Test
   @Timeout(60)
   public void testCloseBusyReceiver() throws Exception {
      final int MSG_COUNT = 20;

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();
      AmqpSender sender = session.createSender(getQueueName());

      for (int i = 0; i < MSG_COUNT; i++) {
         AmqpMessage message = new AmqpMessage();

         message.setMessageId("msg" + i);
         message.setMessageAnnotation("serialNo", i);
         message.setText("Test-Message");

         logger.debug("Sending message: {}", message.getMessageId());

         sender.send(message);
      }

      sender.close();

      Queue queue = getProxyToQueue(getQueueName());
      Wait.assertEquals(MSG_COUNT, queue::getMessageCount);

      AmqpReceiver receiver1 = session.createReceiver(getQueueName());
      receiver1.flow(MSG_COUNT);
      AmqpMessage received = receiver1.receive(5, TimeUnit.SECONDS);
      assertNotNull(received, "Should have got a message");
      assertEquals("msg0", received.getMessageId());
      receiver1.close();

      AmqpReceiver receiver2 = session.createReceiver(getQueueName());
      receiver2.flow(200);
      for (int i = 0; i < MSG_COUNT; ++i) {
         received = receiver2.receive(5, TimeUnit.SECONDS);
         assertNotNull(received, "Should have got a message");
         logger.debug("Read message: {}", received.getMessageId());
         assertEquals("msg" + i, received.getMessageId());
      }

      receiver2.close();
      connection.close();
   }

   @Test
   @Timeout(60)
   public void testReceiveWithJMSSelectorFilter() throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpMessage message1 = new AmqpMessage();
      message1.setGroupId("abcdefg");
      message1.setApplicationProperty("sn", 100);

      AmqpMessage message2 = new AmqpMessage();
      message2.setGroupId("hijklm");
      message2.setApplicationProperty("sn", 200);

      AmqpSender sender = session.createSender(getQueueName());
      sender.send(message1);
      sender.send(message2);
      sender.close();

      AmqpReceiver receiver = session.createReceiver(getQueueName(), "sn = 100");
      receiver.flow(2);
      AmqpMessage received = receiver.receive(5, TimeUnit.SECONDS);
      assertNotNull(received, "Should have read a message");
      assertEquals(100, received.getApplicationProperty("sn"));
      assertEquals("abcdefg", received.getGroupId());
      received.accept();

      assertNull(receiver.receive(1, TimeUnit.SECONDS));

      receiver.close();
      connection.close();
   }

   @Test
   @Timeout(60)
   public void testReceiveWithJMSSelectorFilterOnJMSType() throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpMessage message1 = new AmqpMessage();
      message1.setText("msg:1");

      AmqpMessage message2 = new AmqpMessage();
      message2.setSubject("target");
      message2.setText("msg:2");

      AmqpSender sender = session.createSender(getQueueName());
      sender.send(message1);
      sender.send(message2);
      sender.close();

      AmqpReceiver receiver = session.createReceiver(getQueueName(), "JMSType = 'target'");
      receiver.flow(2);
      AmqpMessage received = receiver.receive(5, TimeUnit.SECONDS);
      assertNotNull(received, "Should have read a message");
      assertEquals("target", received.getSubject());
      received.accept();

      assertNull(receiver.receive(1, TimeUnit.SECONDS));

      receiver.close();
      connection.close();
   }

   @Test
   @Timeout(60)
   public void testAdvancedLinkFlowControl() throws Exception {
      final int MSG_COUNT = 20;

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpSender sender = session.createSender(getQueueName());

      for (int i = 0; i < MSG_COUNT; i++) {
         AmqpMessage message = new AmqpMessage();

         message.setMessageId("msg" + i);
         message.setMessageAnnotation("serialNo", i);
         message.setText("Test-Message");

         sender.send(message);
      }

      sender.close();

      logger.debug("Attempting to read first two messages with receiver #1");
      AmqpReceiver receiver1 = session.createReceiver(getQueueName());
      receiver1.flow(2);
      AmqpMessage message1 = receiver1.receive(10, TimeUnit.SECONDS);
      AmqpMessage message2 = receiver1.receive(10, TimeUnit.SECONDS);
      assertNotNull(message1, "Should have read message 1");
      assertNotNull(message2, "Should have read message 2");
      assertEquals("msg0", message1.getMessageId());
      assertEquals("msg1", message2.getMessageId());
      message1.accept();
      message2.accept();

      logger.debug("Attempting to read next two messages with receiver #2");
      AmqpReceiver receiver2 = session.createReceiver(getQueueName());
      receiver2.flow(2);
      AmqpMessage message3 = receiver2.receive(10, TimeUnit.SECONDS);
      AmqpMessage message4 = receiver2.receive(10, TimeUnit.SECONDS);
      assertNotNull(message3, "Should have read message 3");
      assertNotNull(message4, "Should have read message 4");
      assertEquals("msg2", message3.getMessageId());
      assertEquals("msg3", message4.getMessageId());
      message3.accept();
      message4.accept();

      logger.debug("Attempting to read remaining messages with receiver #1");
      receiver1.flow(MSG_COUNT - 4);
      for (int i = 4; i < MSG_COUNT; i++) {
         AmqpMessage message = receiver1.receive(10, TimeUnit.SECONDS);
         assertNotNull(message, "Should have read a message");
         assertEquals("msg" + i, message.getMessageId());
         message.accept();
      }

      receiver1.close();
      receiver2.close();

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testDispatchOrderWithPrefetchOfOne() throws Exception {
      final int MSG_COUNT = 20;

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpSender sender = session.createSender(getQueueName());

      for (int i = 0; i < MSG_COUNT; i++) {
         AmqpMessage message = new AmqpMessage();

         message.setMessageId("msg" + i);
         message.setMessageAnnotation("serialNo", i);
         message.setText("Test-Message");

         sender.send(message);
      }

      sender.close();

      AmqpReceiver receiver1 = session.createReceiver(getQueueName());
      receiver1.flow(1);

      AmqpReceiver receiver2 = session.createReceiver(getQueueName());
      receiver2.flow(1);

      AmqpMessage message1 = receiver1.receive(10, TimeUnit.SECONDS);
      AmqpMessage message2 = receiver2.receive(10, TimeUnit.SECONDS);
      assertNotNull(message1, "Should have read message 1");
      assertNotNull(message2, "Should have read message 2");
      assertEquals("msg0", message1.getMessageId());
      assertEquals("msg1", message2.getMessageId());
      message1.accept();
      message2.accept();

      receiver1.flow(1);
      AmqpMessage message3 = receiver1.receive(10, TimeUnit.SECONDS);
      receiver2.flow(1);
      AmqpMessage message4 = receiver2.receive(10, TimeUnit.SECONDS);
      assertNotNull(message3, "Should have read message 3");
      assertNotNull(message4, "Should have read message 4");
      assertEquals("msg2", message3.getMessageId());
      assertEquals("msg3", message4.getMessageId());
      message3.accept();
      message4.accept();

      logger.debug("*** Attempting to read remaining messages with both receivers");
      int splitCredit = (MSG_COUNT - 4) / 2;

      logger.debug("**** Receiver #1 granting credit[{}] for its block of messages", splitCredit);
      receiver1.flow(splitCredit);
      for (int i = 0; i < splitCredit; i++) {
         AmqpMessage message = receiver1.receive(10, TimeUnit.SECONDS);
         assertNotNull(message, "Receiver #1 should have read a message");
         logger.debug("Receiver #1 read message: {}", message.getMessageId());
         message.accept();
      }

      logger.debug("**** Receiver #2 granting credit[{}] for its block of messages", splitCredit);
      receiver2.flow(splitCredit);
      for (int i = 0; i < splitCredit; i++) {
         AmqpMessage message = receiver2.receive(10, TimeUnit.SECONDS);
         assertNotNull(message, "Receiver #2 should have read message[" + i + "]");
         logger.debug("Receiver #2 read message: {}", message.getMessageId());
         message.accept();
      }

      receiver1.close();
      receiver2.close();

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testReceiveMessageAndRefillCreditBeforeAccept() throws Exception {
      AmqpClient client = createAmqpClient();

      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      final String address = getQueueName();

      AmqpReceiver receiver = session.createReceiver(address);
      AmqpSender sender = session.createSender(address);

      for (int i = 0; i < 2; i++) {
         AmqpMessage message = new AmqpMessage();
         message.setMessageId("msg" + i);
         sender.send(message);
      }
      sender.close();

      receiver.flow(1);
      AmqpMessage received = receiver.receive(5, TimeUnit.SECONDS);
      assertNotNull(received);

      receiver.flow(1);
      received.accept();

      received = receiver.receive(10, TimeUnit.SECONDS);
      assertNotNull(received);
      received.accept();

      receiver.close();
      connection.close();
   }

   @Test
   @Timeout(60)
   public void testReceiveMessageAndRefillCreditBeforeAcceptOnQueueAsync() throws Exception {
      final AmqpClient client = createAmqpClient();
      final LinkedList<Throwable> errors = new LinkedList<>();
      final CountDownLatch receiverReady = new CountDownLatch(1);
      ExecutorService executorService = Executors.newCachedThreadPool();

      final String address = getQueueName();

      executorService.submit(() -> {
         try {
            logger.debug("Starting consumer connection");
            AmqpConnection connection = addConnection(client.connect());
            AmqpSession session = connection.createSession();
            AmqpReceiver receiver = session.createReceiver(address);
            receiver.flow(1);
            receiverReady.countDown();
            AmqpMessage received = receiver.receive(5, TimeUnit.SECONDS);
            assertNotNull(received);

            receiver.flow(1);
            received.accept();

            received = receiver.receive(5, TimeUnit.SECONDS);
            assertNotNull(received);
            received.accept();

            receiver.close();
            connection.close();

         } catch (Exception error) {
            errors.add(error);
         }
      });

      // producer
      executorService.submit(() -> {
         try {
            receiverReady.await(20, TimeUnit.SECONDS);
            AmqpConnection connection = addConnection(client.connect());
            AmqpSession session = connection.createSession();

            AmqpSender sender = session.createSender(address);
            for (int i = 0; i < 2; i++) {
               AmqpMessage message = new AmqpMessage();
               message.setMessageId("msg" + i);
               sender.send(message);
            }
            sender.close();
            connection.close();
         } catch (Exception ignored) {
            ignored.printStackTrace();
         }
      });

      executorService.shutdown();
      executorService.awaitTermination(20, TimeUnit.SECONDS);
      assertTrue(errors.isEmpty(), "no errors: " + errors);
   }

   @Test
   @Timeout(60)
   public void testMessageDurabliltyFollowsSpec() throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpSender sender = session.createSender(getQueueName());
      AmqpReceiver receiver1 = session.createReceiver(getQueueName());

      Queue queue = getProxyToQueue(getQueueName());

      // Create default message that should be sent as non-durable
      AmqpMessage message1 = new AmqpMessage();
      message1.setText("Test-Message -> non-durable");
      message1.setDurable(false);
      message1.setMessageId("ID:Message:1");
      sender.send(message1);

      Wait.assertEquals(1, queue::getMessageCount);
      receiver1.flow(1);
      message1 = receiver1.receive(50, TimeUnit.SECONDS);
      assertNotNull(message1, "Should have read a message");
      assertFalse(message1.isDurable(), "First message sent should not be durable");
      message1.accept();

      // Create default message that should be sent as durable
      AmqpMessage message2 = new AmqpMessage();
      message2.setText("Test-Message -> durable");
      message2.setDurable(true);
      message2.setMessageId("ID:Message:2");
      sender.send(message2);

      Wait.assertEquals(1, queue::getMessageCount);
      receiver1.flow(1);
      message2 = receiver1.receive(50, TimeUnit.SECONDS);
      assertNotNull(message2, "Should have read a message");
      assertTrue(message2.isDurable(), "Second message sent should be durable");
      message2.accept();

      sender.close();
      connection.close();
   }

   @Test
   @Timeout(60)
   public void testMessageWithHeaderMarkedDurableIsPersisted() throws Exception {
      doTestBrokerRestartAndDurability(true, true, false);
   }

   @Test
   @Timeout(60)
   public void testMessageWithHeaderMarkedNonDurableIsNotPersisted() throws Exception {
      doTestBrokerRestartAndDurability(false, true, true);
   }

   @Test
   @Timeout(60)
   public void testMessageWithHeaderDefaultedNonDurableIsNotPersisted() throws Exception {
      doTestBrokerRestartAndDurability(false, true, false);
   }

   @Test
   @Timeout(60)
   public void testMessageWithNoHeaderIsNotPersisted() throws Exception {
      doTestBrokerRestartAndDurability(false, false, false);
   }

   private void doTestBrokerRestartAndDurability(boolean durable, boolean enforceHeader, boolean explicitSetNonDurable) throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpSender sender = session.createSender(getQueueName());

      final Queue queueView1 = getProxyToQueue(getQueueName());

      Message protonMessage = Message.Factory.create();
      protonMessage.setMessageId("ID:Message:1");
      protonMessage.setBody(new AmqpValue("Test-Message -> " + (durable ? "durable" : "non-durable")));
      if (durable || enforceHeader) {
         Header header = new Header();
         if (durable) {
            header.setDurable(true);
         } else {
            if (explicitSetNonDurable) {
               header.setDurable(false);
            } else {
               // Set priority so the durable field gets defaulted
               header.setPriority(UnsignedByte.valueOf((byte) 5));
               assertNull(header.getDurable());
            }
         }

         protonMessage.setHeader(header);
      } else {
         assertNull(protonMessage.getHeader(), "Should not have a header");
      }

      AmqpMessage message = new AmqpMessage(protonMessage);

      sender.send(message);
      connection.close();

      Wait.assertEquals(1, queueView1::getMessageCount);

      // Restart the server and the Queue should be empty
      // if the message was non-durable
      server.stop();
      server.start();

      // Reconnect now
      connection = addConnection(client.connect());
      session = connection.createSession();
      AmqpReceiver receiver = session.createReceiver(getQueueName());

      final Queue queueView2 = getProxyToQueue(getQueueName());
      if (durable) {
         Wait.assertTrue("Message should not have returned", () -> queueView2.getMessageCount() == 1);
      } else {
         Wait.assertTrue("Message should have been restored", () -> queueView2.getMessageCount() == 0);
      }

      receiver.flow(1);
      message = receiver.receive(1, TimeUnit.SECONDS);

      if (durable) {
         assertNotNull(message, "Should have read a message");
      } else {
         assertNull(message, "Should not have read a message");
      }

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testReceiveMessageBeyondAckedAmountQueue() throws Exception {
      final int MSG_COUNT = 50;

      AmqpClient client = createAmqpClient();

      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      final String address = getQueueName();

      AmqpReceiver receiver = session.createReceiver(address);
      AmqpSender sender = session.createSender(address);

      final Queue destinationView = getProxyToQueue(address);

      for (int i = 0; i < MSG_COUNT; i++) {
         AmqpMessage message = new AmqpMessage();
         message.setMessageId("msg" + i);
         sender.send(message);
      }

      List<AmqpMessage> pendingAcks = new ArrayList<>();

      for (int i = 0; i < MSG_COUNT; i++) {
         receiver.flow(1);
         AmqpMessage received = receiver.receive(5, TimeUnit.SECONDS);
         assertNotNull(received);
         pendingAcks.add(received);
      }

      // Send one more to check in-flight stays at zero with no credit and all
      // pending messages settled.
      AmqpMessage message = new AmqpMessage();
      message.setMessageId("msg-final");
      sender.send(message);

      for (AmqpMessage pendingAck : pendingAcks) {
         pendingAck.accept();
      }

      Wait.assertEquals(0, destinationView::getDeliveringCount);

      sender.close();
      receiver.close();
      connection.close();
   }

   @Test
   @Timeout(60)
   public void testTwoPresettledReceiversReceiveAllMessages() throws Exception {
      final int MSG_COUNT = 100;

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      final String address = getQueueName();

      AmqpSender sender = session.createSender(address);
      AmqpReceiver receiver1 = session.createReceiver(address, null, false, true);
      AmqpReceiver receiver2 = session.createReceiver(address, null, false, true);

      for (int i = 0; i < MSG_COUNT; i++) {
         AmqpMessage message = new AmqpMessage();
         message.setMessageId("msg" + i);
         sender.send(message);
      }

      logger.debug("Attempting to read first two messages with receiver #1");
      receiver1.flow(2);
      AmqpMessage message1 = receiver1.receive(10, TimeUnit.SECONDS);
      AmqpMessage message2 = receiver1.receive(10, TimeUnit.SECONDS);
      assertNotNull(message1, "Should have read message 1");
      assertNotNull(message2, "Should have read message 2");
      assertEquals("msg0", message1.getMessageId());
      assertEquals("msg1", message2.getMessageId());
      message1.accept();
      message2.accept();

      logger.debug("Attempting to read next two messages with receiver #2");
      receiver2.flow(2);
      AmqpMessage message3 = receiver2.receive(10, TimeUnit.SECONDS);
      AmqpMessage message4 = receiver2.receive(10, TimeUnit.SECONDS);
      assertNotNull(message3, "Should have read message 3");
      assertNotNull(message4, "Should have read message 4");
      assertEquals("msg2", message3.getMessageId());
      assertEquals("msg3", message4.getMessageId());
      message3.accept();
      message4.accept();

      logger.debug("*** Attempting to read remaining messages with both receivers");
      int splitCredit = (MSG_COUNT - 4) / 2;

      logger.debug("**** Receiver #1 granting credit[{}] for its block of messages", splitCredit);
      receiver1.flow(splitCredit);
      for (int i = 0; i < splitCredit; i++) {
         AmqpMessage message = receiver1.receive(10, TimeUnit.SECONDS);
         assertNotNull(message, "Receiver #1 should have read a message");
         logger.debug("Receiver #1 read message: {}", message.getMessageId());
         message.accept();
      }

      logger.debug("**** Receiver #2 granting credit[{}] for its block of messages", splitCredit);
      receiver2.flow(splitCredit);
      for (int i = 0; i < splitCredit; i++) {
         AmqpMessage message = receiver2.receive(10, TimeUnit.SECONDS);
         assertNotNull(message, "Receiver #2 should have read a message[" + i + "]");
         logger.debug("Receiver #2 read message: {}", message.getMessageId());
         message.accept();
      }

      receiver1.close();
      receiver2.close();

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testDeliveryDelayOfferedWhenRequested() throws Exception {
      AmqpClient client = createAmqpClient();
      client.setValidator(new AmqpValidator() {

         @Override
         public void inspectOpenedResource(Sender sender) {

            Symbol[] offered = sender.getRemoteOfferedCapabilities();
            if (!contains(offered, AmqpSupport.DELAYED_DELIVERY)) {
               markAsInvalid("Broker did not indicate it support delayed message delivery");
            }
         }
      });

      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpSender sender = session.createSender(getQueueName(), new Symbol[] {AmqpSupport.DELAYED_DELIVERY});
      assertNotNull(sender);

      connection.getStateInspector().assertValid();

      sender.close();
      connection.close();
   }

   @Test
   @Timeout(60)
   public void testMessageWithToFieldSetToSenderAddress() throws Exception {
      doTestMessageWithToFieldSet(false, getQueueName());
   }

   @Test
   @Timeout(60)
   public void testMessageWithToFieldSetToRandomAddress() throws Exception {
      doTestMessageWithToFieldSet(false, UUID.randomUUID().toString());
   }

   @Test
   @Timeout(60)
   public void testMessageWithToFieldSetToEmpty() throws Exception {
      doTestMessageWithToFieldSet(false, "");
   }

   @Test
   @Timeout(60)
   public void testMessageWithToFieldSetToNull() throws Exception {
      doTestMessageWithToFieldSet(false, null);
   }

   @Test
   @Timeout(60)
   public void testMessageWithToFieldSetWithAnonymousSender() throws Exception {
      doTestMessageWithToFieldSet(true, getQueueName());
   }

   private void doTestMessageWithToFieldSet(boolean anonymous, String expected) throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      final String address = getQueueName();

      AmqpSender sender = session.createSender(anonymous ? null : address);

      AmqpMessage message = new AmqpMessage();
      message.setAddress(expected);
      message.setMessageId("msg:1");
      sender.send(message);

      AmqpReceiver receiver = session.createReceiver(address);

      Queue queueView = getProxyToQueue(address);
      Wait.assertEquals(1, queueView::getMessageCount);

      receiver.flow(1);
      AmqpMessage received = receiver.receive(5, TimeUnit.SECONDS);
      assertNotNull(received);
      assertEquals(expected, received.getAddress());
      receiver.close();

      Wait.assertEquals(1, queueView::getMessageCount);

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testLinkDetatchErrorIsCorrectWhenQueueDoesNotExists() throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      Exception expectedException = null;
      try {
         session.createSender("AnAddressThatDoesNotExist");
         fail("Creating a sender here on an address that doesn't exist should fail");
      } catch (Exception e) {
         expectedException = e;
      }

      assertNotNull(expectedException);
      assertTrue(expectedException.getMessage().contains("amqp:not-found"));
      assertTrue(expectedException.getMessage().contains("target address AnAddressThatDoesNotExist does not exist"));

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testSendingAndReceivingToQueueWithDifferentAddressAndQueueName() throws Exception {
      String queueName = "TestQueueName";
      String address = "TestAddress";
      server.addAddressInfo(new AddressInfo(SimpleString.of(address), RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(queueName).setAddress(address).setRoutingType(RoutingType.ANYCAST));

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());

      try {
         AmqpSession session = connection.createSession();
         AmqpSender sender = session.createSender(address);
         AmqpReceiver receiver = session.createReceiver(address);
         receiver.flow(1);

         AmqpMessage message = new AmqpMessage();
         message.setText("TestPayload");
         sender.send(message);

         AmqpMessage receivedMessage = receiver.receive(5000, TimeUnit.MILLISECONDS);
         assertNotNull(receivedMessage);
      } finally {
         connection.close();
      }
   }

   @Test
   @Timeout(60)
   public void testSendReceiveLotsOfDurableMessagesOnQueue() throws Exception {
      doTestSendReceiveLotsOfDurableMessages(Queue.class);
   }

   @Test
   @Timeout(60)
   public void testSendReceiveLotsOfDurableMessagesOnTopic() throws Exception {
      doTestSendReceiveLotsOfDurableMessages(Topic.class);
   }

   private void doTestSendReceiveLotsOfDurableMessages(Class<?> destType) throws Exception {
      final int MSG_COUNT = 1000;

      AmqpClient client = createAmqpClient();

      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      final CountDownLatch done = new CountDownLatch(MSG_COUNT);
      final AtomicBoolean error = new AtomicBoolean(false);
      final ExecutorService executor = Executors.newSingleThreadExecutor();

      final String address;
      if (Queue.class.equals(destType)) {
         address = getQueueName();
      } else {
         address = getTopicName();
      }

      final AmqpReceiver receiver = session.createReceiver(address);
      receiver.flow(MSG_COUNT);

      AmqpSender sender = session.createSender(address);

      Queue queueView = getProxyToQueue(address);

      executor.execute(() -> {
         for (int i = 0; i < MSG_COUNT; i++) {
            try {
               AmqpMessage received = receiver.receive(5, TimeUnit.SECONDS);
               received.accept();
               done.countDown();
            } catch (Exception ex) {
               logger.debug("Caught error: {}", ex.getClass().getSimpleName());
               error.set(true);
            }
         }
      });

      for (int i = 0; i < MSG_COUNT; i++) {
         AmqpMessage message = new AmqpMessage();
         message.setMessageId("msg" + i);
         sender.send(message);
      }

      assertTrue(done.await(10, TimeUnit.SECONDS), "did not read all messages, waiting on: " + done.getCount());
      assertFalse(error.get(), "should not be any errors on receive");
      Wait.assertEquals(0, queueView::getDeliveringCount);

      sender.close();
      receiver.close();
      connection.close();
   }

   @Test
   @Timeout(60)
   public void testReceiveRejecting() throws Exception {
      final int MSG_COUNT = 1000;

      AmqpClient client = createAmqpClient();

      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      final String address = getQueueName();


      AmqpSender sender = session.createSender(address);
      for (int i = 0; i < MSG_COUNT; i++) {
         AmqpMessage message = new AmqpMessage();
         message.setMessageId("msg" + i);
         sender.send(message);
      }

      Queue queueView = getProxyToQueue(address);

      for (int i = 0; i < MSG_COUNT; i++) {
         final AmqpReceiver receiver = session.createReceiver(address);

         receiver.flow(MSG_COUNT);
         AmqpMessage received = receiver.receive(5, TimeUnit.SECONDS);
         assertNotNull(received);
         assertEquals("msg" + i, received.getMessageId());
         received.accept();
         receiver.close();
      }
      final AmqpReceiver receiver = session.createReceiver(address);
      receiver.flow(MSG_COUNT);

      assertNull(receiver.receive(1, TimeUnit.MILLISECONDS));

      Wait.assertEquals(0, queueView::getDeliveringCount);

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testCreateTopicReceiverOnAddressThatDoesNotExistOnPreviousAttempt() throws Exception {
      final AmqpClient client = createAmqpClient();
      final AmqpConnection connection = addConnection(client.connect());
      final AmqpSession session = connection.createSession();
      final String address = "test";

      final Source source = new Source();
      source.setDurable(TerminusDurability.UNSETTLED_STATE);
      source.setCapabilities(Symbol.getSymbol("topic"));
      source.setAddress(address);

      try {
         session.createReceiver(source, "test-receiver-subscription");
         fail("Should not be able to create the receiver");
      } catch (Exception ex) {
         // Expected
      }

      server.addAddressInfo(new AddressInfo(SimpleString.of(address), RoutingType.MULTICAST));

      AmqpReceiver receiver = null;

      try {
         receiver = session.createReceiver(source, "test-receiver-subscription");
         receiver.flow(1);
      } catch (Exception ex) {
         fail("Should be able to create the receiver");
      }

      final AmqpSender sender = session.createSender(address);
      final AmqpMessage message = new AmqpMessage();
      message.setText("TestPayload");

      sender.send(message);

      assertNotNull(receiver.receive(5, TimeUnit.SECONDS));

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testCreateQueueReceiverOnAddressThenRedoAsTopicReceiverAfterAddressUpdated() throws Exception {
      final AmqpClient client = createAmqpClient();
      final AmqpConnection connection = addConnection(client.connect());
      final AmqpSession session = connection.createSession();
      final String address = "test";

      server.addAddressInfo(new AddressInfo(SimpleString.of(address), RoutingType.ANYCAST));

      final Source source = new Source();
      source.setDurable(TerminusDurability.UNSETTLED_STATE);
      source.setCapabilities(Symbol.getSymbol("topic"));
      source.setAddress(address);

      try {
         session.createReceiver(source, "test-receiver-subscription");
         fail("Should not be able to create the receiver");
      } catch (Exception ex) {
         // Expected
      }

      server.removeAddressInfo(SimpleString.of(address), null);
      server.addAddressInfo(new AddressInfo(SimpleString.of(address), RoutingType.MULTICAST));

      AmqpReceiver receiver = null;

      try {
         receiver = session.createReceiver(source, "test-receiver-subscription");
         receiver.flow(1);
      } catch (Exception ex) {
         fail("Should be able to create the receiver");
      }

      final AmqpSender sender = session.createSender(address);
      final AmqpMessage message = new AmqpMessage();
      message.setText("TestPayload");

      sender.send(message);

      assertNotNull(receiver.receive(5, TimeUnit.SECONDS));

      connection.close();
   }
}
