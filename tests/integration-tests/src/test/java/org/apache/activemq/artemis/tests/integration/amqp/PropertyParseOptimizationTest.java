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

package org.apache.activemq.artemis.tests.integration.amqp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPMessage;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPMessageBrokerAccessor;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.collections.LinkedListIterator;
import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpMessage;
import org.apache.activemq.transport.amqp.client.AmqpReceiver;
import org.apache.activemq.transport.amqp.client.AmqpSender;
import org.apache.activemq.transport.amqp.client.AmqpSession;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * This test will validate if application properties are only parsed when there's a filter. You have to disable
 * duplciate-detction to have this optimization working.
 */
public class PropertyParseOptimizationTest extends AmqpClientTestSupport {

   private String noDuplicateAcceptor = new String("tcp://localhost:" + (AMQP_PORT + 8));

   @Override
   protected void addAdditionalAcceptors(ActiveMQServer server) throws Exception {
      server.getConfiguration().addAcceptorConfiguration("noDuplicate", noDuplicateAcceptor + "?protocols=AMQP;useEpoll=false;amqpDuplicateDetection=false");
   }

   @Test
   @Timeout(60)
   public void testSendWithPropertiesAndFilter() throws Exception {
      int size = 10 * 1024;
      AmqpClient client = createAmqpClient(new URI(noDuplicateAcceptor));
      AmqpConnection connection = client.createConnection();
      addConnection(connection);
      connection.connect();

      AmqpSession session = connection.createSession();

      AmqpSender sender = session.createSender(getQueueName());

      Queue queueView = getProxyToQueue(getQueueName());

      LinkedListIterator<MessageReference> iterator = queueView.iterator();

      iterator.close();
      assertNotNull(queueView);
      assertEquals(0, queueView.getMessageCount());

      session.begin();
      for (int m = 0; m < 10; m++) {
         AmqpMessage message = new AmqpMessage();
         message.setDurable(true);
         message.setApplicationProperty("odd", (m % 2 == 0));
         byte[] bytes = new byte[size];
         for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) 'z';
         }

         message.setBytes(bytes);
         sender.send(message);
      }
      session.commit();

      Wait.assertEquals(10, queueView::getMessageCount);

      while (iterator.hasNext()) {

         MessageReference reference = iterator.next();
         AMQPMessage message = (AMQPMessage) reference.getMessage();
         // if this rule fails it means something is requesting the application property for the message,
         // or the optimization is gone.
         // be careful if you decide to change this rule, as we have done extensive test to get this in place.
         assertNull(AMQPMessageBrokerAccessor.getDecodedApplicationProperties(message), "Application properties on AMQP Messages should only be parsed over demand");
      }

      AmqpReceiver receiver = session.createReceiver(getQueueName(), "odd=true");
      receiver.flow(10);
      for (int i = 0; i < 5; i++) {
         AmqpMessage msgReceived = receiver.receive(10, TimeUnit.SECONDS);
         assertNotNull(msgReceived);
         Data body = (Data) msgReceived.getWrappedMessage().getBody();
         byte[] bodyArray = body.getValue().getArray();
         for (int bI = 0; bI < size; bI++) {
            assertEquals((byte) 'z', bodyArray[bI]);
         }
         msgReceived.accept(true);
      }

      receiver.flow(1);
      assertNull(receiver.receiveNoWait());
      Wait.assertEquals(5, queueView::getMessageCount);

      receiver.close();

      connection.close();

   }

}
