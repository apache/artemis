/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.tests.integration.amqp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpMessage;
import org.apache.activemq.transport.amqp.client.AmqpReceiver;
import org.apache.activemq.transport.amqp.client.AmqpSender;
import org.apache.activemq.transport.amqp.client.AmqpSession;
import org.apache.activemq.transport.amqp.client.AmqpValidator;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.transaction.TransactionalState;
import org.apache.qpid.proton.amqp.transport.DeliveryState;
import org.apache.qpid.proton.engine.Delivery;
import org.apache.qpid.proton.engine.Sender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * Test various aspects of Transaction support.
 */
public class AmqpTransactionTest extends AmqpClientTestSupport {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Test
   @Timeout(30)
   public void testBeginAndCommitTransaction() throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();
      assertNotNull(session);

      session.begin();
      assertTrue(session.isInTransaction());
      session.commit();

      connection.close();
   }

   @Test
   @Timeout(30)
   public void testCoordinatorReplenishesCredit() throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();
      assertNotNull(session);

      for (int i = 0; i < 1000; ++i) {
         session.begin();
         assertTrue(session.isInTransaction());
         session.commit();
      }

      connection.close();
   }

   @Test
   @Timeout(30)
   public void testSentTransactionalMessageIsSettleWithTransactionalDisposition() throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();
      assertNotNull(session);

      AmqpSender sender = session.createSender(getQueueName());
      sender.setStateInspector(new AmqpValidator() {

         @Override
         public void inspectDeliveryUpdate(Sender sender, Delivery delivery) {
            if (delivery.remotelySettled()) {
               DeliveryState state = delivery.getRemoteState();
               if (state instanceof TransactionalState) {
                  logger.debug("Remote settled with TX state: {}", state);
               } else {
                  logger.warn("Remote settled with non-TX state: {}", state);
                  markAsInvalid("Remote did not settled with TransactionState.");
               }
            }
         }
      });

      session.begin();

      assertTrue(session.isInTransaction());

      AmqpMessage message = new AmqpMessage();
      message.setText("Test-Message");
      sender.send(message);

      session.commit();

      sender.getStateInspector().assertValid();

      connection.close();
   }

   @Test
   @Timeout(30)
   public void testBeginAndRollbackTransaction() throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();
      assertNotNull(session);

      session.begin();
      assertTrue(session.isInTransaction());
      session.rollback();

      connection.close();

      System.err.println("Closed");
   }

   @Test
   @Timeout(60)
   public void testSendMessageToQueueWithCommit() throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpSender sender = session.createSender(getQueueName());
      final Queue queue = getProxyToQueue(getQueueName());

      session.begin();

      AmqpMessage message = new AmqpMessage();
      message.setText("Test-Message");
      sender.send(message);

      assertEquals(0, queue.getMessageCount());

      session.commit();

      Wait.assertEquals(1, queue::getMessageCount);

      sender.close();
      connection.close();
   }

   @Test
   @Timeout(60)
   public void testSendMessageToQueueWithRollback() throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpSender sender = session.createSender(getQueueName());
      final Queue queue = getProxyToQueue(getQueueName());

      session.begin();

      AmqpMessage message = new AmqpMessage();
      message.setText("Test-Message");
      sender.send(message);

      assertEquals(0, queue.getMessageCount());

      session.rollback();

      assertEquals(0, queue.getMessageCount());

      sender.close();
      connection.close();
   }

   @Test
   @Timeout(60)
   public void testReceiveMessageWithCommit() throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpSender sender = session.createSender(getQueueName());
      final Queue queue = getProxyToQueue(getQueueName());

      AmqpMessage message = new AmqpMessage();
      message.setText("Test-Message");
      sender.send(message);

      Wait.assertEquals(1, queue::getMessageCount);

      AmqpReceiver receiver = session.createReceiver(getQueueName());

      session.begin();

      receiver.flow(1);
      AmqpMessage received = receiver.receive(5, TimeUnit.SECONDS);
      assertNotNull(received);
      received.accept();

      session.commit();

      assertEquals(0, queue.getMessageCount());

      sender.close();
      connection.close();
   }

   @Test
   @Timeout(60)
   public void testReceiveAfterConnectionClose() throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpSender sender = session.createSender(getQueueName());
      final Queue queue = getProxyToQueue(getQueueName());

      AmqpMessage message = new AmqpMessage();
      message.setText("Test-Message");
      sender.send(message);

      Wait.assertEquals(1, queue::getMessageCount);

      AmqpReceiver receiver = session.createReceiver(getQueueName());

      session.begin();

      receiver.flow(1);
      AmqpMessage received = receiver.receive(5, TimeUnit.SECONDS);
      assertNotNull(received);
      received.accept();

      // this will force a rollback on the TX (It should at least)
      connection.close();

      connection = addConnection(client.connect());
      session = connection.createSession();
      receiver = session.createReceiver(getQueueName());
      session.begin();
      receiver.flow(1);

      received = receiver.receive(5, TimeUnit.SECONDS);
      assertNotNull(received);
      received.accept();

      session.commit();

      Wait.assertEquals(0, queue::getMessageCount);

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testReceiveMessageWithRollback() throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpSender sender = session.createSender(getQueueName());
      final Queue queue = getProxyToQueue(getQueueName());

      AmqpMessage message = new AmqpMessage();
      message.setText("Test-Message");
      sender.send(message);

      Wait.assertEquals(1, queue::getMessageCount);

      AmqpReceiver receiver = session.createReceiver(getQueueName());

      session.begin();

      receiver.flow(1);
      AmqpMessage received = receiver.receive(5, TimeUnit.SECONDS);
      assertNotNull(received);
      received.accept();

      session.rollback();

      Wait.assertEquals(1, queue::getMessageCount);

      sender.close();
      connection.close();
   }

   @Test
   @Timeout(60)
   public void testMultipleSessionReceiversInSingleTXNWithCommit() throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());

      // Load up the Queue with some messages
      {
         AmqpSession session = connection.createSession();
         AmqpSender sender = session.createSender(getQueueName());
         AmqpMessage message = new AmqpMessage();
         message.setText("Test-Message");
         sender.send(message);
         sender.send(message);
         sender.send(message);
         sender.close();
      }

      // Root TXN session controls all TXN send lifetimes.
      AmqpSession txnSession = connection.createSession();

      // Create some sender sessions
      AmqpSession session1 = connection.createSession();
      AmqpSession session2 = connection.createSession();
      AmqpSession session3 = connection.createSession();

      // Sender linked to each session
      AmqpReceiver receiver1 = session1.createReceiver(getQueueName());
      AmqpReceiver receiver2 = session2.createReceiver(getQueueName());
      AmqpReceiver receiver3 = session3.createReceiver(getQueueName());

      final Queue queue = getProxyToQueue(getQueueName());
      Wait.assertEquals(3, queue::getMessageCount);

      // Begin the transaction that all senders will operate in.
      txnSession.begin();

      assertTrue(txnSession.isInTransaction());

      receiver1.flow(1);
      receiver2.flow(1);
      receiver3.flow(1);

      AmqpMessage message1 = receiver1.receive(5, TimeUnit.SECONDS);
      AmqpMessage message2 = receiver2.receive(5, TimeUnit.SECONDS);
      AmqpMessage message3 = receiver3.receive(5, TimeUnit.SECONDS);

      message1.accept(txnSession);
      message2.accept(txnSession);
      message3.accept(txnSession);

      Wait.assertEquals(3, queue::getMessageCount);

      txnSession.commit();

      Wait.assertEquals(0, queue::getMessageCount);
   }

   @Test
   @Timeout(60)
   public void testMultipleSessionReceiversInSingleTXNWithRollback() throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());

      // Load up the Queue with some messages
      {
         AmqpSession session = connection.createSession();
         AmqpSender sender = session.createSender(getQueueName());
         AmqpMessage message = new AmqpMessage();
         message.setText("Test-Message");
         sender.send(message);
         sender.send(message);
         sender.send(message);
         sender.close();
      }

      // Root TXN session controls all TXN send lifetimes.
      AmqpSession txnSession = connection.createSession();

      // Create some sender sessions
      AmqpSession session1 = connection.createSession();
      AmqpSession session2 = connection.createSession();
      AmqpSession session3 = connection.createSession();

      // Sender linked to each session
      AmqpReceiver receiver1 = session1.createReceiver(getQueueName());
      AmqpReceiver receiver2 = session2.createReceiver(getQueueName());
      AmqpReceiver receiver3 = session3.createReceiver(getQueueName());

      final Queue queue = getProxyToQueue(getQueueName());
      Wait.assertEquals(3, queue::getMessageCount);

      // Begin the transaction that all senders will operate in.
      txnSession.begin();

      assertTrue(txnSession.isInTransaction());

      receiver1.flow(1);
      receiver2.flow(1);
      receiver3.flow(1);

      AmqpMessage message1 = receiver1.receive(5, TimeUnit.SECONDS);
      AmqpMessage message2 = receiver2.receive(5, TimeUnit.SECONDS);
      AmqpMessage message3 = receiver3.receive(5, TimeUnit.SECONDS);

      message1.accept(txnSession);
      message2.accept(txnSession);
      message3.accept(txnSession);

      Wait.assertEquals(3, queue::getMessageCount);

      txnSession.rollback();

      Wait.assertEquals(3, queue::getMessageCount);
   }

   @Test
   @Timeout(60)
   public void testMultipleSessionSendersInSingleTXNWithCommit() throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());

      // Root TXN session controls all TXN send lifetimes.
      AmqpSession txnSession = connection.createSession();

      // Create some sender sessions
      AmqpSession session1 = connection.createSession();
      AmqpSession session2 = connection.createSession();
      AmqpSession session3 = connection.createSession();

      // Sender linked to each session
      AmqpSender sender1 = session1.createSender(getQueueName());
      AmqpSender sender2 = session2.createSender(getQueueName());
      AmqpSender sender3 = session3.createSender(getQueueName());

      final Queue queue = getProxyToQueue(getQueueName());
      assertEquals(0, queue.getMessageCount());

      // Begin the transaction that all senders will operate in.
      txnSession.begin();

      AmqpMessage message = new AmqpMessage();
      message.setText("Test-Message");

      assertTrue(txnSession.isInTransaction());

      sender1.send(message, txnSession.getTransactionId());
      sender2.send(message, txnSession.getTransactionId());
      sender3.send(message, txnSession.getTransactionId());

      Wait.assertEquals(0, queue::getMessageCount);

      txnSession.commit();

      Wait.assertEquals(3, queue::getMessageCount);
   }

   @Test
   @Timeout(60)
   public void testMultipleSessionSendersInSingleTXNWithRollback() throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());

      // Root TXN session controls all TXN send lifetimes.
      AmqpSession txnSession = connection.createSession();

      // Create some sender sessions
      AmqpSession session1 = connection.createSession();
      AmqpSession session2 = connection.createSession();
      AmqpSession session3 = connection.createSession();

      // Sender linked to each session
      AmqpSender sender1 = session1.createSender(getQueueName());
      AmqpSender sender2 = session2.createSender(getQueueName());
      AmqpSender sender3 = session3.createSender(getQueueName());

      final Queue queue = getProxyToQueue(getQueueName());
      assertEquals(0, queue.getMessageCount());

      // Begin the transaction that all senders will operate in.
      txnSession.begin();

      AmqpMessage message = new AmqpMessage();
      message.setText("Test-Message");

      assertTrue(txnSession.isInTransaction());

      sender1.send(message, txnSession.getTransactionId());
      sender2.send(message, txnSession.getTransactionId());
      sender3.send(message, txnSession.getTransactionId());

      Wait.assertEquals(0, queue::getMessageCount);

      txnSession.rollback();

      Wait.assertEquals(0, queue::getMessageCount);
   }

   //----- Tests Ported from AmqpNetLite client -----------------------------//

   @Test
   @Timeout(60)
   public void testSendersCommitAndRollbackWithMultipleSessionsInSingleTX() throws Exception {
      final int NUM_MESSAGES = 5;

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());

      // Root TXN session controls all TXN send lifetimes.
      AmqpSession txnSession = connection.createSession();

      // Normal Session which won't create an TXN itself
      AmqpSession session = connection.createSession();

      AmqpSender sender = session.createSender(getQueueName());

      // Commit TXN work from a sender.
      txnSession.begin();
      for (int i = 0; i < NUM_MESSAGES; ++i) {
         AmqpMessage message = new AmqpMessage();
         message.setText("Test-Message");
         sender.send(message, txnSession.getTransactionId());
      }
      txnSession.commit();

      // Rollback an additional batch of TXN work from a sender.
      txnSession.begin();
      for (int i = 0; i < NUM_MESSAGES; ++i) {
         AmqpMessage message = new AmqpMessage();
         message.setText("Test-Message");
         sender.send(message, txnSession.getTransactionId());
      }
      txnSession.rollback();

      // Commit more TXN work from a sender.
      txnSession.begin();
      for (int i = 0; i < NUM_MESSAGES; ++i) {
         AmqpMessage message = new AmqpMessage();
         message.setText("Test-Message");
         sender.send(message, txnSession.getTransactionId());
      }
      txnSession.commit();

      AmqpReceiver receiver = session.createReceiver(getQueueName());
      receiver.flow(NUM_MESSAGES * 2);
      for (int i = 0; i < NUM_MESSAGES * 2; ++i) {
         AmqpMessage message = receiver.receive(5, TimeUnit.SECONDS);
         assertNotNull(message);
         message.accept(txnSession);
      }

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testReceiversCommitAndRollbackWithMultipleSessionsInSingleTX() throws Exception {
      final int NUM_MESSAGES = 10;

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());

      try {

         // Root TXN session controls all TXN send lifetimes.
         AmqpSession txnSession = connection.createSession();

         // Normal Session which won't create an TXN itself
         AmqpSession session = connection.createSession();
         AmqpSender sender = session.createSender(getQueueName());

         for (int i = 0; i < NUM_MESSAGES + 1; ++i) {
            AmqpMessage message = new AmqpMessage();
            message.setText("Test-Message");
            message.setApplicationProperty("msgId", i);
            sender.send(message, txnSession.getTransactionId());
         }

         // Read all messages from the Queue, do not accept them yet.
         AmqpReceiver receiver = session.createReceiver(getQueueName());
         List<AmqpMessage> messages = new ArrayList<>(NUM_MESSAGES);
         receiver.flow((NUM_MESSAGES + 2) * 2);
         for (int i = 0; i < NUM_MESSAGES; ++i) {
            AmqpMessage message = receiver.receive(5, TimeUnit.SECONDS);
            assertNotNull(message);
            messages.add(message);
         }

         // Commit half the consumed messages
         txnSession.begin();
         for (int i = 0; i < NUM_MESSAGES / 2; ++i) {
            messages.get(i).accept(txnSession);
         }
         txnSession.commit();

         // Rollback the other half the consumed messages
         txnSession.begin();
         for (int i = NUM_MESSAGES / 2; i < NUM_MESSAGES; ++i) {
            messages.get(i).accept(txnSession, false);
         }
         txnSession.rollback();

         {
            AmqpMessage message = receiver.receive(5, TimeUnit.SECONDS);
            assertNotNull(message);
            assertEquals(NUM_MESSAGES, message.getApplicationProperty("msgId"));
            message.release();
         }

         // The final message should still be pending.
         {
            AmqpMessage message = receiver.receive(5, TimeUnit.SECONDS);
            receiver.flow(1);
            assertNotNull(message);
            assertEquals(NUM_MESSAGES, message.getApplicationProperty("msgId"));
            message.release();
         }

      } finally {
         connection.close();
      }
   }

   @Test
   @Timeout(60)
   public void testCommitAndRollbackWithMultipleSessionsInSingleTX() throws Exception {
      final int NUM_MESSAGES = 10;

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());

      // Root TXN session controls all TXN send lifetimes.
      AmqpSession txnSession = connection.createSession();

      // Normal Session which won't create an TXN itself
      AmqpSession session = connection.createSession();
      AmqpSender sender = session.createSender(getQueueName());

      for (int i = 0; i < NUM_MESSAGES; ++i) {
         AmqpMessage message = new AmqpMessage();
         message.setText("Test-Message");
         message.setApplicationProperty("msgId", i);
         sender.send(message, txnSession.getTransactionId());
      }

      // Read all messages from the Queue, do not accept them yet.
      AmqpReceiver receiver = session.createReceiver(getQueueName());
      receiver.flow(2);
      AmqpMessage message1 = receiver.receive(5, TimeUnit.SECONDS);
      AmqpMessage message2 = receiver.receive(5, TimeUnit.SECONDS);

      // Accept the first one in a TXN and send a new message in that TXN as well
      txnSession.begin();
      {
         message1.accept(txnSession);

         AmqpMessage message = new AmqpMessage();
         message.setText("Test-Message");
         message.setApplicationProperty("msgId", NUM_MESSAGES);

         sender.send(message, txnSession.getTransactionId());
      }
      txnSession.commit();

      // Accept the second one in a TXN and send a new message in that TXN as well but rollback
      txnSession.begin();
      {
         message2.accept(txnSession);

         AmqpMessage message = new AmqpMessage();
         message.setText("Test-Message");
         message.setApplicationProperty("msgId", NUM_MESSAGES + 1);
         sender.send(message, txnSession.getTransactionId());
      }
      txnSession.rollback();

      // Variation here from .NET code, the client settles the accepted message where
      // the .NET client does not and instead releases here to have it redelivered.

      receiver.flow(NUM_MESSAGES);
      for (int i = 1; i <= NUM_MESSAGES; ++i) {
         AmqpMessage message = receiver.receive(5, TimeUnit.SECONDS);
         assertNotNull(message);
         assertEquals(i, message.getApplicationProperty("msgId"));
         message.accept();
      }

      // Should be nothing left.
      assertNull(receiver.receive(1, TimeUnit.SECONDS));

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testReceiversCommitAndRollbackWithMultipleSessionsInSingleTXNoSettlement() throws Exception {
      final int NUM_MESSAGES = 10;

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = client.connect();

      try {

         // Root TXN session controls all TXN send lifetimes.
         AmqpSession txnSession = connection.createSession();

         // Normal Session which won't create an TXN itself
         AmqpSession session = connection.createSession();
         AmqpSender sender = session.createSender(getQueueName());

         for (int i = 0; i < NUM_MESSAGES + 1; ++i) {
            AmqpMessage message = new AmqpMessage();
            message.setText("Test-Message");
            message.setApplicationProperty("msgId", i);
            sender.send(message, txnSession.getTransactionId());
         }

         // Read all messages from the Queue, do not accept them yet.
         AmqpReceiver receiver = session.createReceiver(getQueueName());
         List<AmqpMessage> messages = new ArrayList<>(NUM_MESSAGES);
         receiver.flow((NUM_MESSAGES + 2) * 2);
         for (int i = 0; i < NUM_MESSAGES; ++i) {
            AmqpMessage message = receiver.receive(5, TimeUnit.SECONDS);
            logger.debug("Read message: {}", message.getApplicationProperty("msgId"));
            assertNotNull(message);
            messages.add(message);
         }

         // Commit half the consumed messages [0, 1, 2, 3, 4]
         txnSession.begin();
         for (int i = 0; i < NUM_MESSAGES / 2; ++i) {
            logger.debug("Commit: Accepting message: {}", messages.get(i).getApplicationProperty("msgId"));
            messages.get(i).accept(txnSession, false);
         }
         txnSession.commit();

         // Rollback the other half the consumed messages [5, 6, 7, 8, 9]
         txnSession.begin();
         for (int i = NUM_MESSAGES / 2; i < NUM_MESSAGES; ++i) {
            logger.debug("Rollback: Accepting message: {}", messages.get(i).getApplicationProperty("msgId"));
            messages.get(i).accept(txnSession, false);
         }
         txnSession.rollback();

         // After rollback messages should still be acquired so we read last sent message [10]
         {
            AmqpMessage message = receiver.receive(5, TimeUnit.SECONDS);
            logger.debug("Read message: {}", message.getApplicationProperty("msgId"));
            assertNotNull(message);
            assertEquals(NUM_MESSAGES, message.getApplicationProperty("msgId"));
            message.release();
         }

         // Commit the other half the consumed messages [5, 6, 7, 8, 9] which should still be acquired
         txnSession.begin();
         for (int i = NUM_MESSAGES / 2; i < NUM_MESSAGES; ++i) {
            messages.get(i).accept(txnSession);
         }
         txnSession.commit();

         // The final message [10] should still be pending as we released it previously and committed
         // the previously accepted but not settled messages [5, 6, 7, 8, 9] in a new TX
         {
            receiver.flow(1);
            AmqpMessage message = receiver.receive(5, TimeUnit.SECONDS);
            logger.debug("Read message: {}", message.getApplicationProperty("msgId"));
            assertNotNull(message);
            assertEquals(NUM_MESSAGES, message.getApplicationProperty("msgId"));
            message.accept();
         }

         // We should have now drained the Queue
         receiver.flow(1);
         AmqpMessage message = receiver.receive(1, TimeUnit.SECONDS);
         if (message != null) {
            logger.debug("Read message: {}", message.getApplicationProperty("msgId"));
         }
         assertNull(message);
      } finally {
         connection.close();
      }
   }

   @Test
   @Timeout(60)
   public void testCommitAndRollbackWithMultipleSessionsInSingleTXNoSettlement() throws Exception {
      final int NUM_MESSAGES = 10;

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = client.connect();

      // Root TXN session controls all TXN send lifetimes.
      AmqpSession txnSession = connection.createSession();

      // Normal Session which won't create an TXN itself
      AmqpSession session = connection.createSession();
      AmqpSender sender = session.createSender(getQueueName());

      for (int i = 0; i < NUM_MESSAGES; ++i) {
         AmqpMessage message = new AmqpMessage();
         message.setText("Test-Message");
         message.setApplicationProperty("msgId", i);
         sender.send(message, txnSession.getTransactionId());
      }

      // Read all messages from the Queue, do not accept them yet.
      AmqpReceiver receiver = session.createReceiver(getQueueName());
      receiver.flow(2);
      AmqpMessage message1 = receiver.receive(5, TimeUnit.SECONDS);
      AmqpMessage message2 = receiver.receive(5, TimeUnit.SECONDS);

      // Accept the first one in a TXN and send a new message in that TXN as well
      txnSession.begin();
      {
         // This will result in message [0[ being consumed once we commit.
         message1.accept(txnSession, false);
         logger.debug("Commit: accepting message: {}", message1.getApplicationProperty("msgId"));

         AmqpMessage message = new AmqpMessage();
         message.setText("Test-Message");
         message.setApplicationProperty("msgId", NUM_MESSAGES);

         sender.send(message, txnSession.getTransactionId());
      }
      txnSession.commit();

      // Accept the second one in a TXN and send a new message in that TXN as well but rollback
      txnSession.begin();
      {
         message2.accept(txnSession, false);
         logger.debug("Rollback: accepting message: {}", message2.getApplicationProperty("msgId"));

         AmqpMessage message = new AmqpMessage();
         message.setText("Test-Message");
         message.setApplicationProperty("msgId", NUM_MESSAGES + 1);
         sender.send(message, txnSession.getTransactionId());
      }
      txnSession.rollback();

      // This releases message [1]
      message2.release();

      // Should be ten message available for dispatch given that we sent and committed one, and
      // releases another we had previously received.
      receiver.flow(10);
      for (int i = 1; i <= NUM_MESSAGES; ++i) {
         AmqpMessage message = receiver.receive(5, TimeUnit.SECONDS);
         assertNotNull(message, "Expected a message for: " + i);
         logger.debug("Accepting message: {}", message.getApplicationProperty("msgId"));
         assertEquals(i, message.getApplicationProperty("msgId"));
         message.accept();
      }

      // Should be nothing left.
      receiver.flow(1);
      assertNull(receiver.receive(1, TimeUnit.SECONDS));

      connection.close();
   }

   @Test
   @Timeout(120)
   public void testSendPersistentTX() throws Exception {
      int MESSAGE_COUNT = 2000;
      AtomicInteger errors = new AtomicInteger(0);
      server.createQueue(QueueConfiguration.of("q1").setRoutingType(RoutingType.ANYCAST).setMaxConsumers(1));
      ConnectionFactory factory = new JmsConnectionFactory("amqp://localhost:" + AMQP_PORT);
      Connection sendConnection = factory.createConnection();
      Connection consumerConnection = factory.createConnection();
      try {

         Thread receiverThread = new Thread(() -> {
            try {
               consumerConnection.start();
               Session consumerSession = consumerConnection.createSession(true, Session.SESSION_TRANSACTED);
               javax.jms.Queue q1 = consumerSession.createQueue("q1");

               MessageConsumer consumer = consumerSession.createConsumer(q1);

               for (int i = 1; i <= MESSAGE_COUNT; i++) {
                  Message message = consumer.receive(5000);
                  if (message == null) {
                     throw new IOException("No message read in time.");
                  }

                  if (i % 100 == 0) {
                     if (i % 1000 == 0) {
                        logger.debug("Read message {}", i);
                     }
                     consumerSession.commit();
                  }
               }

               // Assure that all messages are consumed
               consumerSession.commit();
            } catch (Exception e) {
               e.printStackTrace();
               errors.incrementAndGet();
            }

         });

         receiverThread.start();

         Session sendingSession = sendConnection.createSession(true, Session.SESSION_TRANSACTED);

         javax.jms.Queue q1 = sendingSession.createQueue("q1");
         MessageProducer producer = sendingSession.createProducer(q1);
         producer.setDeliveryDelay(DeliveryMode.NON_PERSISTENT);
         for (int i = 0; i < MESSAGE_COUNT; i++) {
            producer.send(sendingSession.createTextMessage("message " + i), DeliveryMode.PERSISTENT, Message.DEFAULT_PRIORITY, Message.DEFAULT_TIME_TO_LIVE);
            if (i % 100 == 0) {
               if (i % 1000 == 0) {
                  logger.debug("Sending {}", i);
               }
               sendingSession.commit();
            }
         }

         sendingSession.commit();

         receiverThread.join(50000);
         assertFalse(receiverThread.isAlive());

         assertEquals(0, errors.get());

      } catch (Exception e) {
         e.printStackTrace();
      } finally {
         sendConnection.close();
         consumerConnection.close();
      }
   }

   @Test
   @Timeout(30)
   public void testUnsettledTXMessageGetTransactedDispostion() throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();
      assertNotNull(session);

      AmqpSender sender = session.createSender(getQueueName());
      AmqpMessage message = new AmqpMessage();
      message.setText("Test-Message");
      sender.send(message);

      AmqpReceiver receiver = session.createReceiver(getQueueName());
      receiver.setStateInspector(new AmqpValidator() {

         @Override
         public void inspectDeliveryUpdate(Sender sender, Delivery delivery) {
            if (delivery.remotelySettled()) {
               logger.debug("Receiver got delivery update for: {}", delivery);
               if (!(delivery.getRemoteState() instanceof TransactionalState txState)) {
                  markAsInvalid("Transactionally acquire work no tagged as being in a transaction.");
               } else {
                  if (!(txState.getOutcome() instanceof Accepted)) {
                     markAsInvalid("Transaction state lacks any outcome");
                  } else if (txState.getTxnId() == null) {
                     markAsInvalid("Transaction state lacks any TX Id");
                  }
               }

               if (!(delivery.getLocalState() instanceof TransactionalState txState)) {
                  markAsInvalid("Transactionally acquire work no tagged as being in a transaction.");
               } else {
                  if (!(txState.getOutcome() instanceof Accepted)) {
                     markAsInvalid("Transaction state lacks any outcome");
                  } else if (txState.getTxnId() == null) {
                     markAsInvalid("Transaction state lacks any TX Id");
                  }
               }

               TransactionalState localTxState = (TransactionalState) delivery.getLocalState();
               TransactionalState remoteTxState = (TransactionalState) delivery.getRemoteState();

               if (!localTxState.getTxnId().equals(remoteTxState)) {
                  markAsInvalid("Message not enrolled in expected transaction");
               }
            }
         }
      });

      session.begin();

      assertTrue(session.isInTransaction());

      receiver.flow(1);
      AmqpMessage received = receiver.receive(2, TimeUnit.SECONDS);
      assertNotNull(received);
      received.accept(false);

      session.commit();

      sender.getStateInspector().assertValid();

      connection.close();
   }
}
