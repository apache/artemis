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

import java.lang.invoke.MethodHandles;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpMessage;
import org.apache.activemq.transport.amqp.client.AmqpReceiver;
import org.apache.activemq.transport.amqp.client.AmqpSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests various behaviors of broker side drain support.
 */
public class AmqpReceiverDrainTest extends AmqpClientTestSupport {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Test
   @Timeout(60)
   public void testReceiverCanDrainMessagesQueue() throws Exception {
      doTestReceiverCanDrainMessages(false);
   }

   @Test
   @Timeout(60)
   public void testReceiverCanDrainMessagesTopic() throws Exception {
      doTestReceiverCanDrainMessages(true);
   }

   private void doTestReceiverCanDrainMessages(boolean topic) throws Exception {
      final String destinationName;
      if (topic) {
         destinationName = getTopicName();
      } else {
         destinationName = getQueueName();
      }

      int MSG_COUNT = 20;

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpReceiver receiver = session.createReceiver(destinationName);

      sendMessages(destinationName, MSG_COUNT);

      Queue queueView = getProxyToQueue(destinationName);

      Wait.assertEquals(MSG_COUNT, queueView::getMessageCount);
      Wait.assertEquals(0, queueView::getDeliveringCount);

      receiver.drain(MSG_COUNT);
      for (int i = 0; i < MSG_COUNT; ++i) {
         AmqpMessage message = receiver.receive(5, TimeUnit.SECONDS);
         assertNotNull(message, "Failed to read message: " + (i + 1));
         logger.info("Read message: {}", message.getMessageId());
         message.accept();
      }
      receiver.close();

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testPullWithNoMessageGetDrainedQueue() throws Exception {
      doTestPullWithNoMessageGetDrained(false);
   }

   @Test
   @Timeout(60)
   public void testPullWithNoMessageGetDrainedTopic() throws Exception {
      doTestPullWithNoMessageGetDrained(true);
   }

   private void doTestPullWithNoMessageGetDrained(boolean topic) throws Exception {

      final String destinationName;
      if (topic) {
         destinationName = getTopicName();
      } else {
         destinationName = getQueueName();
      }

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpReceiver receiver = session.createReceiver(destinationName);

      receiver.flow(10);

      Queue queueView = getProxyToQueue(destinationName);

      assertEquals(0, queueView.getMessageCount());
      assertEquals(0, queueView.getMessagesAcknowledged());

      assertEquals(10, receiver.getReceiver().getRemoteCredit());

      assertNull(receiver.pull(1, TimeUnit.SECONDS));

      assertEquals(0, receiver.getReceiver().getRemoteCredit());

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testPullOneFromRemoteQueue() throws Exception {
      doTestPullOneFromRemote(false);
   }

   @Test
   @Timeout(60)
   public void testPullOneFromRemoteTopic() throws Exception {
      doTestPullOneFromRemote(true);
   }

   private void doTestPullOneFromRemote(boolean topic) throws Exception {

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      final String destinationName;
      if (topic) {
         destinationName = getTopicName();
      } else {
         destinationName = getQueueName();
      }

      AmqpReceiver receiver = session.createReceiver(destinationName);

      int MSG_COUNT = 20;
      sendMessages(destinationName, MSG_COUNT);

      Queue queueView = getProxyToQueue(destinationName);
      assertEquals(MSG_COUNT, queueView.getMessageCount());
      assertEquals(0, queueView.getDeliveringCount());

      assertEquals(0, receiver.getReceiver().getRemoteCredit());

      AmqpMessage message = receiver.pull(5, TimeUnit.SECONDS);
      assertNotNull(message);
      message.accept();

      assertEquals(0, receiver.getReceiver().getRemoteCredit());

      receiver.close();

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testMultipleZeroResultPullsQueue() throws Exception {
      doTestMultipleZeroResultPulls(false);
   }

   @Test
   @Timeout(60)
   public void testMultipleZeroResultPullsTopic() throws Exception {
      doTestMultipleZeroResultPulls(true);
   }

   private void doTestMultipleZeroResultPulls(boolean topic) throws Exception {

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      final String destinationName;
      if (topic) {
         destinationName = getTopicName();
      } else {
         destinationName = getQueueName();
      }

      AmqpReceiver receiver = session.createReceiver(destinationName);

      receiver.flow(10);

      Queue queueView = getProxyToQueue(destinationName);
      assertEquals(0, queueView.getMessageCount());
      assertEquals(0, queueView.getDeliveringCount());

      assertEquals(10, receiver.getReceiver().getRemoteCredit());

      assertNull(receiver.pull(1, TimeUnit.SECONDS));

      assertEquals(0, receiver.getReceiver().getRemoteCredit());

      assertNull(receiver.pull(1, TimeUnit.SECONDS));
      assertNull(receiver.pull(1, TimeUnit.SECONDS));

      assertEquals(0, receiver.getReceiver().getRemoteCredit());

      connection.close();
   }
}
