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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.activemq.artemis.core.config.BridgeConfiguration;
import org.apache.activemq.artemis.core.config.DivertConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.integration.amqp.AmqpClientTestSupport;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpMessage;
import org.apache.activemq.transport.amqp.client.AmqpReceiver;
import org.apache.activemq.transport.amqp.client.AmqpSender;
import org.apache.activemq.transport.amqp.client.AmqpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class AMQPLargeMessageOverCoreBridgeTest extends AmqpClientTestSupport {

   private ActiveMQServer server2;

   @BeforeEach
   public void setServers() throws Exception {
      server.setIdentity("server1");
      server.getConfiguration().addAcceptorConfiguration("flow", "tcp://localhost:6666" + "?protocols=CORE");
      server.start();
      createAddressAndQueues(server);

      server2 = createServer(AMQP_PORT + 1, false);
      server2.start();
      createAddressAndQueues(server2);
   }


   private void receiveTextMessages(int port, String queue, String text, int numberOfMessages) throws Exception {
      AmqpClient client = createAmqpClient(new URI("tcp://localhost:" + port));

      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpReceiver receiver = session.createReceiver(queue);

      receiver.flow(numberOfMessages + 1);
      for (int i = 0; i < numberOfMessages; i++) {
         AmqpMessage message = receiver.receive(10, TimeUnit.SECONDS);
         assertNotNull(message);
         assertEquals(text, message.getText());
         message.accept();
      }
      assertNull(receiver.receiveNoWait());
      receiver.close();

      connection.close();
   }

   @Override
   protected ActiveMQServer createServer() throws Exception {
      return createServer(AMQP_PORT, false);
   }

   public void sendTextMessages(int port, String destinationName, String text, int count) throws Exception {
      AmqpClient client = createAmqpClient(new URI("tcp://localhost:" + (port)));
      AmqpConnection connection = addConnection(client.connect());
      try {
         AmqpSession session = connection.createSession();
         AmqpSender sender = session.createSender(destinationName);

         for (int i = 0; i < count; ++i) {
            AmqpMessage message = new AmqpMessage();
            message.setMessageId("MessageID:" + i);
            message.setDurable(true);

            message.setText(text);
            sender.send(message);
         }
      } finally {
         connection.close();
      }
   }

   @Test
   @Timeout(60)
   public void testCoreBridgeDivert() throws Exception {
      internalTest(true);
   }

   @Test
   @Timeout(60)
   public void testCoreBridgeNoDivert() throws Exception {
      internalTest(false);
   }

   private void internalTest(boolean useDivert) throws Exception {
      server2.getConfiguration().addConnectorConfiguration("otherside", "tcp://localhost:6666");

      if (useDivert) {
         DivertConfiguration divertConf = new DivertConfiguration().setName("divert1").setRoutingName("divert1").setAddress(getQueueName(0)).setForwardingAddress(getQueueName(1));
         server2.deployDivert(divertConf);
      }
      server2.deployBridge(new BridgeConfiguration().setName(getTestName()).setQueueName(getQueueName(1)).setForwardingAddress(getQueueName(2)).setConfirmationWindowSize(10).setStaticConnectors(Arrays.asList("otherside")));

      String largeText = RandomUtil.randomAlphaNumericString(300 * 1024);

      sendTextMessages(AMQP_PORT + 1, getQueueName(useDivert ? 0 : 1), largeText, 10);
      server.stop();
      server.start();
      receiveTextMessages(AMQP_PORT, getQueueName(2), largeText, 10);
      if (useDivert) {
         // We diverted, so messages were copied, we need to make sure we consume from the original queue
         receiveTextMessages(AMQP_PORT + 1, getQueueName(0), largeText, 10);
      } else {
         // no messages should been routed to 0
         receiveTextMessages(AMQP_PORT + 1, getQueueName(0), largeText, 0);
      }

      // messages should have been transferred between servers
      receiveTextMessages(AMQP_PORT + 1, getQueueName(1), largeText, 0);

   }

}
