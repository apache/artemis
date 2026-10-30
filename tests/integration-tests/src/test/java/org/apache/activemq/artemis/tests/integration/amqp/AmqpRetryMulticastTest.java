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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.collections.LinkedListIterator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Retrying a message from the DLQ that was dead-lettered from one subscription queue of a multicast address must route
 * it back to that queue only, not to every subscription of the address.
 */
public class AmqpRetryMulticastTest extends AmqpClientTestSupport {

   private static final String ADDRESS = "retry.topic";
   private static final String Q1 = "retry.q1";
   private static final String Q2 = "retry.q2";

   @Test
   @Timeout(60)
   public void testRetryMessagesAMQP() throws Exception {
      doTestRetryToSubscriptionQueue("AMQP", false);
   }

   @Test
   @Timeout(60)
   public void testRetryMessageByIdAMQP() throws Exception {
      doTestRetryToSubscriptionQueue("AMQP", true);
   }

   @Test
   @Timeout(60)
   public void testRetryMessagesCore() throws Exception {
      doTestRetryToSubscriptionQueue("CORE", false);
   }

   @Test
   @Timeout(60)
   public void testRetryMessageByIdCore() throws Exception {
      doTestRetryToSubscriptionQueue("CORE", true);
   }

   private void doTestRetryToSubscriptionQueue(String protocol, boolean byId) throws Exception {
      server.createQueue(QueueConfiguration.of(Q1).setAddress(ADDRESS).setRoutingType(RoutingType.MULTICAST));
      server.createQueue(QueueConfiguration.of(Q2).setAddress(ADDRESS).setRoutingType(RoutingType.MULTICAST));

      final Queue q1 = server.locateQueue(Q1);
      final Queue q2 = server.locateQueue(Q2);
      final Queue dlq = server.locateQueue(getDeadLetterAddress());
      final QueueControl dlqControl = (QueueControl) server.getManagementService().getResource(ResourceNames.QUEUE + getDeadLetterAddress());

      ConnectionFactory factory = CFUtil.createConnectionFactory(protocol, getBrokerAmqpConnectionURI().toString());
      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer producer = session.createProducer(session.createTopic(ADDRESS));
         producer.send(session.createTextMessage("hello-" + protocol));

         Wait.assertEquals(1L, q1::getMessageCount);
         Wait.assertEquals(1L, q2::getMessageCount);

         assertEquals(1, q1.sendMessagesToDeadLetterAddress(null));
         Wait.assertEquals(1L, dlq::getMessageCount);
         Wait.assertEquals(0L, q1::getMessageCount);

         if (byId) {
            long messageID = dlq.peekFirstMessage().getMessage().getMessageID();
            assertTrue(dlqControl.retryMessage(messageID));
         } else {
            assertEquals(1, dlqControl.retryMessages());
         }

         Wait.assertEquals(0L, dlq::getMessageCount);
         Wait.assertEquals(1L, q1::getMessageCount);
         assertEquals(1L, q2.getMessageCount(), "retry must not route the message to the other subscription");

         assertNoRouteToProperty(q1);
         assertNoRouteToProperty(q2);

         connection.start();
         for (String queue : new String[] {Q1, Q2}) {
            MessageConsumer consumer = session.createConsumer(session.createQueue(ADDRESS + "::" + queue));
            TextMessage received = (TextMessage) consumer.receive(5000);
            assertNotNull(received, queue);
            assertEquals("hello-" + protocol, received.getText());
            assertFalse(received.propertyExists(Message.HDR_ROUTE_TO_IDS.toString()), queue + " message carries " + Message.HDR_ROUTE_TO_IDS);
            assertNull(consumer.receiveNoWait(), queue);
            consumer.close();
         }
      }

      Wait.assertEquals(0L, q1::getMessageCount);
      Wait.assertEquals(0L, q2::getMessageCount);
   }

   private static void assertNoRouteToProperty(Queue queue) {
      try (LinkedListIterator<MessageReference> iterator = queue.browserIterator()) {
         while (iterator.hasNext()) {
            Message message = iterator.next().getMessage();
            assertFalse(message.getPropertyNames().contains(Message.HDR_ROUTE_TO_IDS), queue.getName() + " holds " + message);
         }
      }
   }
}
