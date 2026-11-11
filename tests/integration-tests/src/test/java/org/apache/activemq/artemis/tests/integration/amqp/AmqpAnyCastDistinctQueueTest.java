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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.artemis.api.core.QueueConfiguration;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.tests.util.CFUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Make sure auto create will not create a queue when the queue and its address have a distinct name in anycast.
 */
public class AmqpAnyCastDistinctQueueTest extends AmqpClientTestSupport {

   @Override
   protected void addAdditionalAcceptors(ActiveMQServer server) throws Exception {
   }

   @Test
   @Timeout(60)
   public void testDistinctQueueAddressAnyCast() throws Exception {
      String ADDRESS_NAME = "DISTINCT_ADDRESS_testDistinctAddressAnyCast";
      String QUEUE_NAME = "DISTINCT_QUEUE_testDistinctQUEUE_AnyCast";
      server.addAddressInfo(new AddressInfo(ADDRESS_NAME).setAutoCreated(false).addRoutingType(RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(QUEUE_NAME).setAddress(ADDRESS_NAME).setDurable(true).setRoutingType(RoutingType.ANYCAST));

      final int NUMBER_OF_MESSAGES = 100;

      ConnectionFactory jmsCF = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);
      try (Connection connection = jmsCF.createConnection()) {
         Session session = connection.createSession();
         Queue queueSending = session.createQueue(ADDRESS_NAME);
         MessageProducer producer = session.createProducer(queueSending);
         for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            producer.send(session.createTextMessage("hello " + i));
         }

         Queue queueReceiving = session.createQueue(QUEUE_NAME);
         connection.start();
         MessageConsumer consumer = session.createConsumer(queueReceiving);
         for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            TextMessage message = (TextMessage)consumer.receive(5000);
            assertNotNull(message);
            assertEquals("hello " + i, message.getText());
         }
         assertNull(consumer.receiveNoWait());
      }

   }
}