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

import static org.apache.qpid.jms.provider.amqp.message.AmqpDestinationHelper.QUEUE_CAPABILITY;
import static org.apache.qpid.jms.provider.amqp.message.AmqpDestinationHelper.TOPIC_CAPABILITY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.postoffice.Bindings;
import org.apache.activemq.artemis.core.postoffice.impl.LocalQueueBinding;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.QueueImpl;
import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpMessage;
import org.apache.activemq.transport.amqp.client.AmqpReceiver;
import org.apache.activemq.transport.amqp.client.AmqpSession;
import org.apache.qpid.proton.amqp.messaging.Source;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class ClientDefinedAnycastConsumerTest extends AmqpClientTestSupport {

   SimpleString address = SimpleString.of("testAddress");

   @Test
   @Timeout(60)
   public void testConsumeFromSingleQueueOnAddressSameName() throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      Source source = new Source();
      source.setAddress(address.toString());
      source.setCapabilities(QUEUE_CAPABILITY);

      AmqpReceiver receiver = session.createReceiver(source);
      sendMessages(address.toString(), 1);
      receiver.flow(1);
      AmqpMessage amqpMessage = receiver.receive(5, TimeUnit.SECONDS);
      assertNotNull(amqpMessage);
      assertEquals(1, ((QueueImpl)server.getPostOffice().getBinding(address).getBindable()).getConsumerCount());

      receiver.close();
      connection.close();
   }

   @Test
   @Timeout(60)
   public void testConsumeFromSingleQueueOnAddressSameNameNegativeValidation() throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      Source source = new Source();
      source.setAddress(address.toString());
      source.setCapabilities(TOPIC_CAPABILITY);

      AmqpReceiver receiver = session.createReceiver(source);
      sendMessages(address.toString(), 1);
      receiver.flow(1);
      AmqpMessage amqpMessage = receiver.receive(5, TimeUnit.SECONDS);
      assertNotNull(amqpMessage);
      Bindings bindings = server.getPostOffice().getBindingsForAddress(address);
      assertEquals(1, bindings.getBindings().size());
      bindings.getBindings().forEach((binding) -> {
         final Queue localQueue = ((LocalQueueBinding) binding).getQueue();
         assertEquals(1, localQueue.getConsumerCount());
         assertEquals(RoutingType.MULTICAST, localQueue.getRoutingType());
      });

      receiver.close();
      connection.close();
   }
}
