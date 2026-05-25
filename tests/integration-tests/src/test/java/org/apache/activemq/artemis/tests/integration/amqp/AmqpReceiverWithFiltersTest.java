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

import static org.apache.activemq.transport.amqp.AmqpSupport.findFilter;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport;
import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpMessage;
import org.apache.activemq.transport.amqp.client.AmqpReceiver;
import org.apache.activemq.transport.amqp.client.AmqpSender;
import org.apache.activemq.transport.amqp.client.AmqpSession;
import org.apache.activemq.transport.amqp.client.AmqpUnknownFilterType;
import org.apache.activemq.transport.amqp.client.AmqpValidator;
import org.apache.qpid.proton.amqp.DescribedType;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.UnsignedInteger;
import org.apache.qpid.proton.amqp.messaging.Source;
import org.apache.qpid.proton.amqp.messaging.TerminusDurability;
import org.apache.qpid.proton.amqp.messaging.TerminusExpiryPolicy;
import org.apache.qpid.proton.engine.Receiver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test various behaviors of AMQP receivers with the broker.
 */
public class AmqpReceiverWithFiltersTest extends AmqpClientTestSupport {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Test
   @Timeout(60)
   public void testUnsupportedFiltersAreNotListedAsSupported() throws Exception {
      AmqpClient client = createAmqpClient();

      client.setValidator(new AmqpValidator() {

         @SuppressWarnings("unchecked")
         @Override
         public void inspectOpenedResource(Receiver receiver) {

            if (receiver.getRemoteSource() == null) {
               markAsInvalid("Link opened with null source.");
            }

            Source source = (Source) receiver.getRemoteSource();
            Map<Symbol, Object> filters = source.getFilter();

            if (findFilter(filters, AmqpUnknownFilterType.UNKNOWN_FILTER_IDS) != null) {
               markAsInvalid("Broker should not return unsupported filter on attach.");
            }
         }
      });

      Map<Symbol, DescribedType> filters = new HashMap<>();
      filters.put(AmqpUnknownFilterType.UNKNOWN_FILTER_NAME, AmqpUnknownFilterType.UNKNOWN_FILTER);

      Source source = new Source();
      source.setAddress(getQueueName());
      source.setFilter(filters);
      source.setDurable(TerminusDurability.NONE);
      source.setExpiryPolicy(TerminusExpiryPolicy.LINK_DETACH);

      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      session.createReceiver(source);

      assertEquals(1, server.getTotalConsumerCount());

      connection.getStateInspector().assertValid();
      connection.close();
   }

   @Test
   @Timeout(60)
   public void testSupportedFiltersAreListedAsSupported() throws Exception {
      AmqpClient client = createAmqpClient();

      client.setValidator(new AmqpValidator() {

         @SuppressWarnings("unchecked")
         @Override
         public void inspectOpenedResource(Receiver receiver) {

            if (receiver.getRemoteSource() == null) {
               markAsInvalid("Link opened with null source.");
            }

            Source source = (Source) receiver.getRemoteSource();
            Map<Symbol, Object> filters = source.getFilter();

            if (findFilter(filters, AmqpSupport.JMS_SELECTOR_FILTER_IDS) == null) {
               markAsInvalid("Broker should return selector filter on attach.");
            }
         }
      });

      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      session.createReceiver(getQueueName(), "color = red");

      connection.getStateInspector().assertValid();
      connection.close();
   }

   @Test
   @Timeout(60)
   public void testReceivedUnsignedFilter() throws Exception {
      final int NUM_MESSAGES = 100;

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = client.connect();

      try {
         // Normal Session which won't create an TXN itself
         AmqpSession session = connection.createSession();
         AmqpSender sender = session.createSender(getQueueName());

         for (int i = 0; i < NUM_MESSAGES + 1; ++i) {
            AmqpMessage message = new AmqpMessage();
            message.setText("Test-Message");
            message.setApplicationProperty("myNewID", new UnsignedInteger(i));
            sender.send(message);
         }

         // Read all messages from the Queue, do not accept them yet.
         AmqpReceiver receiver = session.createReceiver(getQueueName(), "myNewID < " + (NUM_MESSAGES / 2));
         List<AmqpMessage> messages = new ArrayList<>(NUM_MESSAGES);
         receiver.flow((NUM_MESSAGES + 2) * 2);
         for (int i = 0; i < NUM_MESSAGES / 2; ++i) {
            AmqpMessage message = receiver.receive(5, TimeUnit.SECONDS);
            assertNotNull(message);
            logger.debug("Read message: {}", message.getApplicationProperty("myNewID"));
            assertNotNull(message);
            messages.add(message);
         }

         assertNull(receiver.receiveNoWait());

      } finally {
         connection.close();
      }
   }
}
