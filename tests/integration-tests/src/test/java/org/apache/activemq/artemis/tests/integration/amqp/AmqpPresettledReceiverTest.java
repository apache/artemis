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

import java.lang.invoke.MethodHandles;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpMessage;
import org.apache.activemq.transport.amqp.client.AmqpReceiver;
import org.apache.activemq.transport.amqp.client.AmqpSender;
import org.apache.activemq.transport.amqp.client.AmqpSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test various behaviors of AMQP receivers with the broker.
 */
public class AmqpPresettledReceiverTest extends AmqpClientTestSupport {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Test
   @Timeout(60)
   public void testPresettledReceiverAndNonPresettledReceiverOnSameQueue() throws Exception {
      final int MSG_COUNT = 2;
      sendMessages(getQueueName(), MSG_COUNT);

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpReceiver receiver1 = session.createReceiver(getQueueName(), null, false, true);
      AmqpReceiver receiver2 = session.createReceiver(getQueueName());

      final Queue queueView = getProxyToQueue(getQueueName());
      assertEquals(MSG_COUNT, queueView.getMessageCount());

      receiver1.flow(1);
      receiver2.flow(1);

      AmqpMessage message1 = receiver1.receive(5, TimeUnit.SECONDS);
      AmqpMessage message2 = receiver2.receive(5, TimeUnit.SECONDS);

      assertNotNull(message1);
      assertNotNull(message2);

      // Receiver 1 is presettled so messages are not accepted.
      assertTrue(message1.getWrappedDelivery().remotelySettled());

      // Receiver 2 is not presettled so it needs to accept.
      message2.accept();

      receiver1.close();
      receiver2.close();

      logger.debug("Message Count after all consumed: {}", queueView.getMessageCount());

      // Should be nothing left on the Queue
      AmqpReceiver receiver3 = session.createReceiver(getQueueName());
      receiver3.flow(1);

      AmqpMessage received = receiver3.receive(5, TimeUnit.SECONDS);
      if (received != null) {
         logger.debug("Message read: {}", received.getMessageId());
      }
      assertNull(received);

      assertEquals(0, queueView.getMessageCount());

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testPresettledReceiverReadsAllMessages() throws Exception {
      final int MSG_COUNT = 100;
      sendMessages(getQueueName(), MSG_COUNT);

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpReceiver receiver = session.createReceiver(getQueueName(), null, false, true);

      final Queue queueView = getProxyToQueue(getQueueName());
      assertEquals(MSG_COUNT, queueView.getMessageCount());

      receiver.flow(MSG_COUNT);
      for (int i = 0; i < MSG_COUNT; ++i) {
         assertNotNull(receiver.receive(5, TimeUnit.SECONDS));
      }
      receiver.close();

      logger.debug("Message Count after all consumed: {}", queueView.getMessageCount());

      // Open a new receiver and see if any message are left on the Queue
      receiver = session.createReceiver(getQueueName());
      receiver.flow(1);
      AmqpMessage received = receiver.receive(5, TimeUnit.SECONDS);
      if (received != null) {
         logger.debug("Message read: {}", received.getMessageId());
      }
      assertNull(received);

      assertEquals(0, queueView.getMessageCount());

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testPresettledReceiverReadsAllMessagesInWhenReadInBatches() throws Exception {
      final int MSG_COUNT = 100;
      sendMessages(getQueueName(), MSG_COUNT);

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpReceiver receiver = session.createReceiver(getQueueName(), null, false, true);

      final Queue queueView = getProxyToQueue(getQueueName());
      assertEquals(MSG_COUNT, queueView.getMessageCount());

      // Consume all 100 but do so in batches by flowing only limited credit.

      receiver.flow(20);
      // consume less that flow
      for (int j = 0; j < 10; j++) {
         assertNotNull(receiver.receive(5, TimeUnit.SECONDS));
      }

      // flow more and consume all
      receiver.flow(10);
      for (int j = 0; j < 20; j++) {
         assertNotNull(receiver.receive(5, TimeUnit.SECONDS));
      }

      // remainder
      receiver.flow(70);
      for (int j = 0; j < 70; j++) {
         assertNotNull(receiver.receive(5, TimeUnit.SECONDS));
      }

      receiver.close();

      logger.debug("Message Count after all consumed: {}", queueView.getMessageCount());

      // Open a new receiver and see if any message are left on the Queue
      receiver = session.createReceiver(getQueueName());
      receiver.flow(1);
      AmqpMessage received = receiver.receive(5, TimeUnit.SECONDS);
      if (received != null) {
         logger.debug("Message read: {}", received.getMessageId());
      }
      assertNull(received);

      assertEquals(0, queueView.getMessageCount());

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testPresettledReceiverWithinBoundsOfActiveTXWithCommit() throws Exception {
      doTestPresettledReceiverWithinBoundsOfActiveTX(true);
   }

   @Test
   @Timeout(60)
   public void testPresettledReceiverWithinBoundsOfActiveTXWithRollback() throws Exception {
      doTestPresettledReceiverWithinBoundsOfActiveTX(false);
   }

   private void doTestPresettledReceiverWithinBoundsOfActiveTX(boolean commit) throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpSender sender = session.createSender(getQueueName());
      final Queue queue = getProxyToQueue(getQueueName());

      AmqpMessage message = new AmqpMessage();
      message.setText("Test-Message");
      sender.send(message);

      Wait.assertEquals(1, queue::getMessageCount);

      AmqpReceiver receiver = session.createReceiver(getQueueName(), null, false, true);

      session.begin();

      receiver.flow(1);
      AmqpMessage received = receiver.receive(5, TimeUnit.SECONDS);
      assertNotNull(received);
      assertTrue(received.getWrappedDelivery().remotelySettled());

      if (commit) {
         session.commit();
      } else {
         session.rollback();
      }

      assertEquals(0, queue.getMessageCount());

      sender.close();
      connection.close();
   }

   @Test
   @Timeout(60)
   public void testPresettledReceiverWithinBoundsOfActiveTXWithSendAndRollback() throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpSender sender = session.createSender(getQueueName());
      final Queue queue = getProxyToQueue(getQueueName());

      AmqpMessage message = new AmqpMessage();
      message.setText("Test-Message");
      sender.send(message);

      Wait.assertEquals(1, queue::getMessageCount);

      AmqpReceiver receiver = session.createReceiver(getQueueName(), null, false, true);

      session.begin();

      receiver.flow(1);
      AmqpMessage received = receiver.receive(5, TimeUnit.SECONDS);
      assertNotNull(received);
      assertTrue(received.getWrappedDelivery().remotelySettled());

      message = new AmqpMessage();
      message.setText("Test-Message - Rolled Back");
      sender.send(message);

      session.rollback();

      assertEquals(0, queue.getMessageCount());

      sender.close();
      connection.close();
   }
}
