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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpMessage;
import org.apache.activemq.transport.amqp.client.AmqpReceiver;
import org.apache.activemq.transport.amqp.client.AmqpSender;
import org.apache.activemq.transport.amqp.client.AmqpSession;
import org.apache.qpid.proton.amqp.Symbol;
import org.junit.jupiter.api.Test;

/**
 * This is testing a double transfer (copy). First messages will expire, then DLQ. This will validate the data added to
 * the queues.
 */
public class DLQAfterExpiredMessageTest extends AmqpClientTestSupport {

   protected String getExpiryQueue() {
      return "ActiveMQ.Expiry";
   }

   @Override
   protected void createAddressAndQueues(ActiveMQServer server) throws Exception {
      // Default Queue
      server.addAddressInfo(new AddressInfo(SimpleString.of(getQueueName()), RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(getQueueName()).setRoutingType(RoutingType.ANYCAST));

      // Default DLQ
      server.addAddressInfo(new AddressInfo(SimpleString.of(getDeadLetterAddress()), RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(getDeadLetterAddress()).setRoutingType(RoutingType.ANYCAST));

      // Expiry
      server.addAddressInfo(new AddressInfo(SimpleString.of(getExpiryQueue()), RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(getExpiryQueue()).setRoutingType(RoutingType.ANYCAST));

      // Default Topic
      server.addAddressInfo(new AddressInfo(SimpleString.of(getTopicName()), RoutingType.MULTICAST));
      server.createQueue(QueueConfiguration.of(getTopicName()));

      // Additional Test Queues
      for (int i = 0; i < getPrecreatedQueueSize(); ++i) {
         server.addAddressInfo(new AddressInfo(SimpleString.of(getQueueName(i)), RoutingType.ANYCAST));
         server.createQueue(QueueConfiguration.of(getQueueName(i)).setRoutingType(RoutingType.ANYCAST));
      }
   }

   @Override
   protected void configureAddressPolicy(ActiveMQServer server) {
      // Address configuration
      AddressSettings addressSettings = new AddressSettings();

      addressSettings.setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE);
      addressSettings.setAutoCreateQueues(isAutoCreateQueues());
      addressSettings.setAutoCreateAddresses(isAutoCreateAddresses());
      addressSettings.setDeadLetterAddress(SimpleString.of(getDeadLetterAddress()));
      addressSettings.setExpiryAddress(SimpleString.of(getExpiryQueue()));
      addressSettings.setMaxDeliveryAttempts(1);
      server.getConfiguration().getAddressSettings().put("#", addressSettings);
      server.getConfiguration().getAddressSettings().put(getExpiryQueue(), addressSettings);
   }

   @Test
   public void testDoubleTransfer() throws Throwable {

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      try {
         AmqpSession session = connection.createSession();

         AmqpSender sender = session.createSender(getQueueName());

         // Get the Queue View early to avoid racing the delivery.
         final Queue queueView = getProxyToQueue(getQueueName());
         assertNotNull(queueView);

         AmqpMessage message = new AmqpMessage();
         message.setTimeToLive(1);
         message.setText("Test-Message");
         message.setDurable(true);
         message.setApplicationProperty("key1", "Value1");
         sender.send(message);
         sender.close();

         Wait.assertEquals(1, queueView::getMessagesExpired);
         Wait.assertEquals(0, queueView::getConsumerCount);

         final Queue expiryView = getProxyToQueue(getExpiryQueue());
         assertNotNull(expiryView);
         Wait.assertEquals(1, expiryView::getMessageCount);

         Map<String, Object> annotations = new HashMap<>();

         AmqpReceiver receiverDLQ = session.createReceiver(getExpiryQueue(), "\"m." + AMQPMessageSupport.HDR_ORIGINAL_ADDRESS_ANNOTATION + "\"='" + getQueueName() + "'");
         receiverDLQ.flow(1);
         AmqpMessage received = receiverDLQ.receive(5, TimeUnit.SECONDS);
         assertNotNull(received);
         Map<Symbol, Object> avAnnotations = received.getWrappedMessage().getMessageAnnotations().getValue();
         avAnnotations.forEach((key, value) -> {
            annotations.put(key.toString(), value);
         });
         received.reject();
         receiverDLQ.close();


         // Redo the selection
         receiverDLQ = session.createReceiver(getDeadLetterAddress(), "\"m." + AMQPMessageSupport.HDR_ORIGINAL_ADDRESS_ANNOTATION + "\"='" + getExpiryQueue() + "'");
         receiverDLQ.flow(1);
         received = receiverDLQ.receive(5, TimeUnit.SECONDS);
         assertNotNull(received);
         received.accept();

         assertEquals(0, received.getTimeToLive());
         assertNotNull(received);
         assertEquals("Value1", received.getApplicationProperty("key1"));
      } catch (Throwable e) {
         e.printStackTrace();
         throw e;
      } finally {
         connection.close();
      }
   }
}
