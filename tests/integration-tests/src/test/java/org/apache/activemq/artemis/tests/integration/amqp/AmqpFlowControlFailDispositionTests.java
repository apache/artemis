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

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameter;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpMessage;
import org.apache.activemq.transport.amqp.client.AmqpSender;
import org.apache.activemq.transport.amqp.client.AmqpSession;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.Modified;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

@ExtendWith(ParameterizedTestExtension.class)
public class AmqpFlowControlFailDispositionTests extends JMSClientTestSupport {

   private static final int MIN_LARGE_MESSAGE_SIZE = 16 * 1024;

   @Parameter(index = 0)
   public boolean useModified;

   @Parameter(index = 1)
   public Symbol[] outcomes;

   @Parameter(index = 2)
   public String expectedMessage;

   @Parameters(name = "useModified={0}")
   public static Collection<Object[]> parameters() {
      return Arrays.asList(new Object[][] {
            {true, new Symbol[]{Accepted.DESCRIPTOR_SYMBOL, Rejected.DESCRIPTOR_SYMBOL, Modified.DESCRIPTOR_SYMBOL}, "failure at remote"},
            {true, new Symbol[]{Accepted.DESCRIPTOR_SYMBOL, Rejected.DESCRIPTOR_SYMBOL}, "[condition = amqp:resource-limit-exceeded]"},
            {false, new Symbol[]{Accepted.DESCRIPTOR_SYMBOL, Rejected.DESCRIPTOR_SYMBOL, Modified.DESCRIPTOR_SYMBOL}, "[condition = amqp:resource-limit-exceeded]"},
            {false, new Symbol[]{}, "[condition = amqp:resource-limit-exceeded]"}
      });
   }

   @Override
   protected void configureAddressPolicy(ActiveMQServer server) {
      AddressSettings addressSettings = server.getAddressSettingsRepository().getMatch("#");
      addressSettings.setAddressFullMessagePolicy(AddressFullMessagePolicy.FAIL);
      addressSettings.setMaxSizeBytes(1000);
      server.getAddressSettingsRepository().addMatch("#", addressSettings);
   }

   @Override
   protected void configureAMQPAcceptorParameters(Map<String, Object> params) {
      params.put("amqpUseModifiedForTransientDeliveryErrors", useModified);
      params.put("amqpMinLargeMessageSize", MIN_LARGE_MESSAGE_SIZE);
   }

   @TestTemplate
   @Timeout(10)
   public void testAddressFullDisposition() throws Exception {
      AmqpClient client = createAmqpClient(getBrokerAmqpConnectionURI());
      AmqpConnection connection = client.connect();
      try {
         AmqpSession session = connection.createSession();
         AmqpSender sender = session.createSender(getQueueName(), null, null, outcomes);
         boolean rejected = false;
         for (int i = 0; i < 1000; i++) {
            final AmqpMessage message = new AmqpMessage();
            byte[] payload = new byte[10];
            message.setBytes(payload);
            try {
               sender.send(message);
            } catch (IOException e) {
               rejected = true;
               assertTrue(e.getMessage().contains(expectedMessage),
                          String.format("Unexpected message expected %s to contain %s", e.getMessage(), expectedMessage));
               break;
            }
         }

         assertTrue(rejected, "Expected messages to be refused by broker");
      } finally {
         connection.close();
      }
   }

   @TestTemplate
   @Timeout(60)
   public void testFailedLargeMessageSendWhenNoSpaceCleansUpLargeFile() throws Exception {
      AmqpClient client = createAmqpClient(getBrokerAmqpConnectionURI());
      AmqpConnection connection = client.connect();

      int expectedRemainingLargeMessageFiles = 0;

      try {
         AmqpSession session = connection.createSession();
         AmqpSender sender = session.createSender(getQueueName(), null, null, outcomes);
         AmqpMessage message = createAmqpLargeMessage();
         boolean rejected = false;

         for (int i = 0; i < 1000; i++) {
            try {
               sender.send(message);
               expectedRemainingLargeMessageFiles++;
            } catch (IOException e) {
               rejected = true;
               assertTrue(e.getMessage().contains(expectedMessage),
                          String.format("Unexpected message expected %s to contain %s", e.getMessage(), expectedMessage));
               break;
            }
         }

         assertTrue(rejected, "Expected messages to be refused by broker");
      } finally {
         connection.close();
      }

      validateNoFilesOnLargeDir(getLargeMessagesDir(), expectedRemainingLargeMessageFiles);
   }

   private AmqpMessage createAmqpLargeMessage() {
      AmqpMessage message = new AmqpMessage();

      byte[] payload = new byte[MIN_LARGE_MESSAGE_SIZE * 2];
      for (int i = 0; i < payload.length; i++) {
         payload[i] = (byte) 65;
      }

      message.setMessageAnnotation("x-opt-big-blob", new String(payload, StandardCharsets.UTF_8));
      message.setText("test");

      return message;
   }
}
