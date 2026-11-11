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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpMessage;
import org.apache.activemq.transport.amqp.client.AmqpReceiver;
import org.apache.activemq.transport.amqp.client.AmqpSender;
import org.apache.activemq.transport.amqp.client.AmqpSession;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class AmqpFlowControlFailOrdinaryTests extends JMSClientTestSupport {

   @Override
   protected void configureAddressPolicy(ActiveMQServer server) {
      AddressSettings addressSettings = server.getAddressSettingsRepository().getMatch("#");
      addressSettings.setAddressFullMessagePolicy(AddressFullMessagePolicy.FAIL);
      addressSettings.setMaxSizeBytes(1000);
      server.getAddressSettingsRepository().addMatch("#", addressSettings);
   }

   @Test
   public void testMessagesNotSent() throws Exception {
      AmqpClient client = createAmqpClient(getBrokerAmqpConnectionURI());
      AmqpConnection connection = client.connect();
      int messagesSent = 0;
      try {
         AmqpSession session = connection.createSession();
         AmqpSender sender = session.createSender(getQueueName());
         boolean rejected = false;
         for (int i = 0; i < 1000; i++) {
            final AmqpMessage message = new AmqpMessage();
            byte[] payload = new byte[10];
            message.setBytes(payload);
            try {
               sender.send(message);
               messagesSent++;
            } catch (IOException e) {
               rejected = true;
               break;
            }
         }
         assertTrue(rejected);
         rejected = false;
         AmqpSession session2 = connection.createSession();
         AmqpReceiver receiver = session2.createReceiver(getQueueName());
         receiver.flow(messagesSent);
         for (int i = 0; i < messagesSent; i++) {
            AmqpMessage receive = receiver.receive(5, TimeUnit.SECONDS);
            assertNotNull(receive);
            receive.accept();
         }
         receiver.close();
         session2.close();

         for (int i = 0; i < 1000; i++) {
            final AmqpMessage message = new AmqpMessage();
            byte[] payload = new byte[100];
            message.setBytes(payload);
            try {
               sender.send(message);
            } catch (IOException e) {
               rejected = true;
               break;
            }
         }
         assertTrue(rejected);
      } finally {
         connection.close();
      }
   }
}
