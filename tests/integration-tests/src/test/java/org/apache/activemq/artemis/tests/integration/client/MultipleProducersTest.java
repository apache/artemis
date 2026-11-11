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
package org.apache.activemq.artemis.tests.integration.client;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import java.lang.invoke.MethodHandles;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.settings.impl.SlowConsumerPolicy;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MultipleProducersTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public Connection conn;
   public Queue queueOne = null;
   public Queue queueTwo = null;
   public Session session = null;

   public SimpleString dlq = SimpleString.of("DLQ");
   public SimpleString expiryQueue = SimpleString.of("ExpiryQueue");

   public SimpleString queueOneName = SimpleString.of("queueOne");
   public SimpleString queueTwoName = SimpleString.of("queueTwo");

   ActiveMQServer server;

   @BeforeEach
   public void setupServer() throws Exception {

      server = createServer(false, true);

      AddressSettings addressSettings = new AddressSettings();

      addressSettings.setAddressFullMessagePolicy(AddressFullMessagePolicy.FAIL);
      addressSettings.setExpiryAddress(dlq);
      addressSettings.setDeadLetterAddress(expiryQueue);
      addressSettings.setRedeliveryDelay(0);
      addressSettings.setMessageCounterHistoryDayLimit(2);
      addressSettings.setDefaultLastValueQueue(false);
      addressSettings.setMaxDeliveryAttempts(10);
      addressSettings.setMaxSizeBytes(Integer.MAX_VALUE);
      addressSettings.setMaxSizeMessages(5);
      addressSettings.setPageSizeBytes(2097152);
      addressSettings.setRedistributionDelay(-1);
      addressSettings.setSendToDLAOnNoRoute(false);
      addressSettings.setSlowConsumerCheckPeriod(5);
      addressSettings.setSlowConsumerPolicy(SlowConsumerPolicy.NOTIFY);
      addressSettings.setSlowConsumerThreshold(-1);

      server.getConfiguration().getAddressSettings().clear();
      server.getConfiguration().getAddressSettings().put("#", addressSettings);

      server.getConfiguration().addQueueConfiguration(QueueConfiguration.of(queueOneName).setAddress(queueOneName).setRoutingType(RoutingType.ANYCAST));
      server.getConfiguration().addQueueConfiguration(QueueConfiguration.of(queueTwoName).setAddress(queueTwoName).setRoutingType(RoutingType.ANYCAST));

      server.start();

   }

   @Test
   public void wrongQueue() throws Exception {

      ConnectionFactory cf = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616");

      conn = cf.createConnection();
      conn.start();
      runAfter(conn::close);

      session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      queueOne = session.createQueue(queueOneName.toString());

      queueTwo = session.createQueue(queueTwoName.toString());

      org.apache.activemq.artemis.core.server.Queue serverQueueOne = server.locateQueue(queueOneName);
      org.apache.activemq.artemis.core.server.Queue serverQueueTwo = server.locateQueue(queueTwoName);

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         try {
            while (true) {
               sendMessage(queueOne, session);
            }
         } catch (Exception expected) {
            expected.printStackTrace();
         }
      }

      Wait.assertTrue(serverQueueOne.getPagingStore()::isFull, 5000, 100);

      conn.close();
      conn = cf.createConnection();
      runAfter(conn::close);
      conn.start();
      session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      // send a message to a queue which is already full
      // result an exception
      assertThrows(JMSException.class, () -> sendMessage(queueOne, session));

      // send 5 message to queueTwo
      // there should be 5 messages on queueTwo
      for (int i = 0; i < 5; i++) {
         sendMessage(queueTwo, session);
      }

      Wait.assertEquals(5L, serverQueueOne::getMessageCount, 5000, 100);

      consumeMessages(queueOne, session, 5);

      Wait.assertEquals(0L, serverQueueOne::getMessageCount, 5000, 100);
      Wait.assertFalse(serverQueueOne.getPagingStore()::isFull, 5000, 100);

      conn.close();
      conn = cf.createConnection();
      runAfter(conn::close);
      conn.start();
      session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      for (int i = 0; i < 5; i++) {
         logger.info("Sending message {}", i);
         sendMessage(queueOne, session);
      }

      session.close();

      conn.close();

      Wait.assertEquals(5L, serverQueueTwo::getMessageCount, 5000, 100);
   }

   private void consumeMessages(Queue queue, Session session, int numberOfMessages) throws Exception {
      MessageConsumer consumer = session.createConsumer(queue);
      for (int i = 0; i < numberOfMessages; i++) {
         Message message = consumer.receive(5000);
         assertNotNull(message);
      }
   }

   private void sendMessage(Queue queue, Session session) throws Exception {

      MessageProducer mp = session.createProducer(queue);

      try {
         mp.send(session.createTextMessage("This is message for " + queue.getQueueName()));
      } finally {

         mp.close();
      }
   }
}
