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

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpMessage;
import org.apache.activemq.transport.amqp.client.AmqpReceiver;
import org.apache.activemq.transport.amqp.client.AmqpSession;
import org.apache.qpid.proton.message.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class AmqpReceiverDispositionRejectAsUnmodifiedModeTests extends AmqpClientTestSupport {

   @Override
   protected void configureAMQPAcceptorParameters(Map<String, Object> params) {
      params.put("amqpTreatRejectAsUnmodifiedDeliveryFailed", true);
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

      // Owing to the config, the reject should be treat as if it were a
      // Unmodified delivery-failed=true
      message.reject();
      receiver1.flow(1);

      message = receiver1.receive(5, TimeUnit.SECONDS);
      assertNotNull(message, "did not receive message after reject");
      assertEquals("MessageID:0", message.getMessageId());

      protonMessage = message.getWrappedMessage();
      assertNotNull(protonMessage);
      assertEquals(1, protonMessage.getDeliveryCount(), "Unexpected value for AMQP delivery-count after redelivery");

      connection.close();
   }
}