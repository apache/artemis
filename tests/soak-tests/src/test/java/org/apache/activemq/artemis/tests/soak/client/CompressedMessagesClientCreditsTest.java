/**
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

package org.apache.activemq.artemis.tests.soak.client;

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.server.impl.ServerConsumerImpl;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class CompressedMessagesClientCreditsTest extends ActiveMQTestBase {

   ActiveMQServer server;

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      super.setUp();

      this.server = this.createServer(true, true);
      server.getAddressSettingsRepository().clear();
      server.getAddressSettingsRepository().addMatch("#", new AddressSettings().setMaxDeliveryAttempts(-1));
      server.start();
   }

   @Test
   public void testRollbackOnCompressedButRegularMessages() throws Exception {
      // this will make it compress really well, up to the point the message will be regular
      internalTest(" ".repeat(500 * 1024));
   }

   @Test
   public void testRollbackOnCompressedStillLarge() throws Exception {
      // the string will still be large even after compression
      internalTest(RandomUtil.randomAlphaNumericString(500 * 1024));
   }

   private void internalTest(String originalString) throws Exception {
      final String queueName = "queue";

      ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("tcp://localhost:61616");
      connectionFactory.setCompressLargeMessage(true);
      connectionFactory.setCompressionLevel(6);
      connectionFactory.setConsumerWindowSize(0);
      connectionFactory.setMinLargeMessageSize(1024 * 10);

      try (Connection connection = connectionFactory.createConnection()) {
         connection.start();

         Session producerSession = connection.createSession(Session.AUTO_ACKNOWLEDGE);
         MessageProducer producer = producerSession.createProducer(producerSession.createQueue(queueName));

         TextMessage sendMessage = producerSession.createTextMessage();
         sendMessage.setText(originalString);

         Session consumerSession = connection.createSession(Session.SESSION_TRANSACTED);
         MessageConsumer consumer = consumerSession.createConsumer(consumerSession.createQueue(queueName));

         Queue queue = server.locateQueue(queueName);

         ServerConsumerImpl serverConsumer = null;
         for (ServerSession s : server.getSessions()) {
            if (s.getConsumerCount() > 0) {
               serverConsumer = (ServerConsumerImpl) s.getServerConsumers().iterator().next();
            }
         }

         int numberOfMessages = 10;

         for (int i = 0; i < numberOfMessages; i++) {
            producer.send(sendMessage);
         }
         Wait.assertEquals((long) numberOfMessages, queue::getMessageCount, 5000, 100);

         int rollbackLoop = 10;

         assertEquals(0, serverConsumer.getAvailableCredits().get());
         for (int i = 0; i < rollbackLoop; i++) {
            assertNotNull(consumer.receive(1000));
            queue.flushExecutor();
            Wait.assertEquals(0, serverConsumer.getAvailableCredits()::get, 5000, 100);
            Wait.assertEquals(1, queue::getDeliveringCount, 5000, 100);
            consumerSession.rollback();
         }

         for (int i = 0; i < 10; i++) {
            assertNotNull(consumer.receive(5000));
         }
         assertNull(consumer.receiveNoWait());
         consumerSession.commit();

      }
   }
}
