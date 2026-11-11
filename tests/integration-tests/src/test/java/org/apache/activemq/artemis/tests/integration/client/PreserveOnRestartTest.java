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
package org.apache.activemq.artemis.tests.integration.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PreserveOnRestartTest extends ActiveMQTestBase {


   private ActiveMQServer server;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      server = createServer(true, true);
      server.getConfiguration().setAddressQueueScanPeriod(500);
      server.getConfiguration().setMessageExpiryScanPeriod(500);
      server.getConfiguration().addAddressSetting("#", new AddressSettings().setAutoDeleteAddresses(true).setAutoDeleteAddressesDelay(360000).setAutoDeleteQueuesDelay(360000).setAutoDeleteQueuesMessageCount(-1).setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE).setMaxSizeMessages(20));
      server.start();
   }

   @Test
   public void testRestartQueueNoPaging() throws Exception {
      testRestartQueue(false);
   }

   @Test
   public void testRestartQueuePaging() throws Exception {
      testRestartQueue(true);
   }

   public void testRestartQueue(boolean paging) throws Exception {
      int NUMBER_OF_MESSAGES = paging ? 100 : 10;
      String queueName = getName();
      ConnectionFactory factory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616");
      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer producer = session.createProducer(session.createQueue(queueName));
         for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            producer.send(session.createTextMessage("hello" + i));
         }
         session.commit();
      }
      Queue serverQueue = server.locateQueue(queueName);
      Wait.assertEquals(NUMBER_OF_MESSAGES, serverQueue::getMessageCount);

      server.stop();
      server.start();

      serverQueue = server.locateQueue(queueName);
      Wait.assertEquals(NUMBER_OF_MESSAGES, serverQueue::getMessageCount);

      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         MessageConsumer consumer = session.createConsumer(session.createQueue(queueName));
         connection.start();
         for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            TextMessage message = (TextMessage)consumer.receive(5000);
            assertNotNull(message);
            assertEquals("hello" + i, message.getText());
         }
         assertNull(consumer.receiveNoWait());
         session.commit();
      }

      Wait.assertEquals(0, serverQueue::getMessageCount);
      Wait.assertFalse(serverQueue.getPagingStore()::isPaging);

      server.stop();
      server.start();
      Wait.assertTrue(() -> server.locateQueue(queueName) == null, 5000, 100);
   }
}
