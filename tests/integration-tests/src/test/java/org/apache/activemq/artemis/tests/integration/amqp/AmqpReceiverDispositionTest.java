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
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpMessage;
import org.apache.activemq.transport.amqp.client.AmqpReceiver;
import org.apache.activemq.transport.amqp.client.AmqpSender;
import org.apache.activemq.transport.amqp.client.AmqpSession;
import org.apache.qpid.proton.message.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Test various behaviors of AMQP receivers with the broker.
 * <p>
 * See also {@link AmqpReceiverDispositionRejectAsUnmodifiedModeTests} for some testing of configurable alternative
 * behaviour.
 */
public class AmqpReceiverDispositionTest extends AmqpClientTestSupport {

   private final int MIN_LARGE_MESSAGE_SIZE = 2048;

   @Override
   protected void configureAMQPAcceptorParameters(Map<String, Object> params) {
      params.put("amqpMinLargeMessageSize", MIN_LARGE_MESSAGE_SIZE);
   }

   @Test
   @Timeout(30)
   public void testReleasedDisposition() throws Exception {
      sendMessages(getQueueName(), 1);

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpReceiver receiver1 = session.createReceiver(getQueueName());
      receiver1.flow(1);

      AmqpMessage message = receiver1.receive(5, TimeUnit.SECONDS);

      AmqpReceiver receiver2 = session.createReceiver(getQueueName());

      assertNotNull(message, "did not receive message first time");
      assertEquals("MessageID:0", message.getMessageId());

      Message protonMessage = message.getWrappedMessage();
      assertNotNull(protonMessage);
      assertEquals(0, protonMessage.getDeliveryCount(), "Unexpected initial value for AMQP delivery-count");

      receiver2.flow(1);
      message.release();

      // Read the message again and validate its state
      message = receiver2.receive(10, TimeUnit.SECONDS);
      assertNotNull(message, "did not receive message again");
      assertEquals("MessageID:0", message.getMessageId());

      message.accept();

      protonMessage = message.getWrappedMessage();
      assertNotNull(protonMessage);
      assertEquals(0, protonMessage.getDeliveryCount(), "Unexpected updated value for AMQP delivery-count");

      connection.close();
   }

   @Test
   @Timeout(30)
   public void testRejectedDisposition() throws Exception {
      sendMessages(getQueueName(), 1);

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpReceiver receiver1 = session.createReceiver(getQueueName());
      receiver1.flow(1);

      AmqpMessage message = receiver1.receive(5, TimeUnit.SECONDS);
      assertNotNull(message, "did not receive message first time");
      assertEquals("MessageID:0", message.getMessageId());

      Message protonMessage = message.getWrappedMessage();
      assertNotNull(protonMessage);
      assertEquals(0, protonMessage.getDeliveryCount(), "Unexpected initial value for AMQP delivery-count");

      message.reject();

      // Reject is a terminal outcome and should not be redelivered to the rejecting receiver
      // or any other as it should move to the archived state.
      receiver1.flow(1);
      message = receiver1.receiveNoWait();
      assertNull(message, "Should not receive message again");

      // Attempt to Read the message again with another receiver to validate it is archived.
      AmqpReceiver receiver2 = session.createReceiver(getQueueName());
      receiver2.flow(1);
      assertNull(receiver2.receiveNoWait());

      connection.close();
   }

   @Test
   @Timeout(30)
   public void testModifiedDispositionWithDeliveryFailedWithoutUndeliverableHereFieldsSet() throws Exception {
      doModifiedDispositionTestImpl(Boolean.TRUE, null);
   }

   @Test
   @Timeout(30)
   public void testModifiedDispositionWithoutDeliveryFailedWithoutUndeliverableHereFieldsSet() throws Exception {
      doModifiedDispositionTestImpl(null, null);
   }

   @Test
   @Timeout(30)
   public void testModifiedDispositionWithoutDeliveryFailedWithUndeliverableHereFieldsSet() throws Exception {
      doModifiedDispositionTestImpl(null, Boolean.TRUE);
   }

   @Test
   @Timeout(30)
   public void testModifiedDispositionWithDeliveryFailedWithUndeliverableHereFieldsSet() throws Exception {
      doModifiedDispositionTestImpl(Boolean.TRUE, Boolean.TRUE);
   }

   private void doModifiedDispositionTestImpl(Boolean deliveryFailed, Boolean undeliverableHere) throws Exception {
      sendMessages(getQueueName(), 1);

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpReceiver receiver1 = session.createReceiver(getQueueName());
      receiver1.flow(1);

      AmqpMessage message = receiver1.receive(5, TimeUnit.SECONDS);
      assertNotNull(message, "did not receive message first time");

      Message protonMessage = message.getWrappedMessage();
      assertNotNull(protonMessage);
      assertEquals(0, protonMessage.getDeliveryCount(), "Unexpected initial value for AMQP delivery-count");

      message.modified(deliveryFailed, undeliverableHere);

      // Remote must not redispatch to the client if undeliverable here is true
      if (Boolean.TRUE.equals(undeliverableHere)) {
         receiver1.flow(1);
         message = receiver1.receive(1, TimeUnit.SECONDS);
         assertNull(message, "Should not receive message again");
      }

      AmqpReceiver receiver2 = session.createReceiver(getQueueName());
      receiver2.flow(1);

      message = receiver2.receive(5, TimeUnit.SECONDS);
      assertNotNull(message, "did not receive message again");

      int expectedDeliveryCount = 0;
      if (Boolean.TRUE.equals(deliveryFailed)) {
         expectedDeliveryCount = 1;
      }

      message.accept();

      Message protonMessage2 = message.getWrappedMessage();
      assertNotNull(protonMessage2);
      assertEquals(expectedDeliveryCount,
            protonMessage2.getDeliveryCount(),
            "Unexpected updated value for AMQP delivery-count");

      connection.close();
   }

   @Test
   @Timeout(30)
   public void testReplayMessageForFQQNRejectedMessage() throws Exception {
      doTestReplayMessageForFQQNRejectedMessage(10);
   }

   @Test
   @Timeout(30)
   public void testReplayMessageForFQQNRejectedLargeMessage() throws Exception {
      doTestReplayMessageForFQQNRejectedMessage(MIN_LARGE_MESSAGE_SIZE);
   }

   public void doTestReplayMessageForFQQNRejectedMessage(int payloadSize) throws Exception {
      server.createQueue(QueueConfiguration.of("A1").setAddress("A")
                                                    .setRoutingType(RoutingType.MULTICAST)
                                                    .setDurable(true));

      final String targetFQQN = "A::A1";
      final AmqpClient client = createAmqpClient();
      final AmqpConnection connection = addConnection(client.connect());
      final AmqpSession session = connection.createSession();
      final AmqpSender sender = session.createSender(targetFQQN);
      final AmqpMessage message = new AmqpMessage();

      final String payload = "#".repeat(payloadSize);

      message.setMessageId("MSG:1");
      message.setText("Test-Message: " + payload);
      message.setDurable(true);

      sender.send(message);

      final AmqpReceiver receiver = session.createReceiver(targetFQQN);
      receiver.flow(1);

      final AmqpMessage rejected = receiver.receive(5, TimeUnit.SECONDS);
      assertNotNull(rejected, "Did not receive message that we want to reject");

      rejected.reject();

      final Queue queueView = server.locateQueue(targetFQQN);
      final Queue dlqView = server.locateQueue("ActiveMQ.DLQ");

      Wait.assertEquals(0L, () -> queueView.getMessageCount(), 5000, 100);
      Wait.assertEquals(1L, () -> dlqView.getMessageCount(), 5000, 100);

      // This call with the message sent to an FQQN results in a new application
      // property being added whose payload is a byte array

      dlqView.retryMessages(null); // No filter so all should match

      Wait.assertEquals(0L, () -> dlqView.getMessageCount(), 5000, 100);
      Wait.assertEquals(1L, () -> queueView.getMessageCount(), 5000, 100);

      receiver.flow(2);

      final AmqpMessage retriedMessage = receiver.receive(5, TimeUnit.SECONDS);

      assertNotNull(retriedMessage);
      assertEquals("MSG:1", retriedMessage.getMessageId());

      connection.close();
   }
}
