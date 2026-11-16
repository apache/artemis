/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.tests.integration.amqp.largemessages;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.Map;

import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.tests.integration.amqp.AmqpClientTestSupport;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpMessage;
import org.apache.activemq.transport.amqp.client.AmqpReceiver;
import org.apache.activemq.transport.amqp.client.AmqpSender;
import org.apache.activemq.transport.amqp.client.AmqpSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Tests delivery of an AMQP large message whose stored body is shorter than the body size recorded for it, which
 * previously left the broker retrying the same impossible delivery forever.
 */
public class AmqpTruncatedLargeMessageBodyTest extends AmqpClientTestSupport {

   private static final int MIN_LARGE_MESSAGE_SIZE = 10 * 1024;
   private static final int PAYLOAD_SIZE = 100 * 1024;
   private static final int TRUNCATED_SIZE = 1024;

   @Override
   protected void configureAMQPAcceptorParameters(Map<String, Object> params) {
      params.put("amqpMinLargeMessageSize", MIN_LARGE_MESSAGE_SIZE);
   }

   @Test
   @Timeout(60)
   public void testTruncatedBodyIsDeadLetteredInsteadOfRetriedForever() throws Exception {
      final AmqpClient client = createAmqpClient();
      final AmqpConnection connection = addConnection(client.connect());

      try {
         final AmqpSession session = connection.createSession();

         // Send with no consumer attached so the whole body is spooled to disk before anything reads it
         final AmqpSender sender = session.createSender(getQueueName());
         final AmqpMessage message = new AmqpMessage();

         message.setDurable(true);
         message.setBytes(new byte[PAYLOAD_SIZE]);

         sender.send(message);
         sender.close();

         final Queue queue = getProxyToQueue(getQueueName());
         final Queue deadLetterQueue = getProxyToQueue(getDeadLetterAddress());

         Wait.assertEquals(1L, queue::getMessageCount, 5000, 100);

         truncateStoredBodies();

         // Attaching a consumer is what triggers the read of the body that is no longer there
         final AmqpReceiver receiver = session.createReceiver(getQueueName());

         receiver.flow(1);

         // The body can never satisfy the recorded size, so the message has to leave the queue for the dead
         // letter address rather than being handed back for the next consumer to fail on in the same way
         Wait.assertEquals(1L, deadLetterQueue::getMessageCount, 10000, 100);
         Wait.assertEquals(0L, queue::getMessageCount, 10000, 100);
      } finally {
         connection.close();
      }
   }

   private void truncateStoredBodies() throws Exception {
      final File largeMessagesDir = new File(getLargeMessagesDir());
      final File[] bodies = largeMessagesDir.listFiles((dir, name) -> name.endsWith(".msg"));

      assertNotNull(bodies, "no large messages directory was created for the broker");
      assertTrue(bodies.length > 0, "expected the large message body to have been stored");

      for (File body : bodies) {
         try (RandomAccessFile file = new RandomAccessFile(body, "rw")) {
            file.setLength(TRUNCATED_SIZE);
         }
      }
   }
}
