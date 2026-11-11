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
package org.apache.activemq.artemis.tests.integration.amqp.interop;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.MessageHandler;
import org.apache.activemq.artemis.jms.client.ActiveMQSession;
import org.apache.activemq.artemis.tests.integration.amqp.JMSClientTestSupport;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.apache.qpid.jms.JmsTopic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AmqpCoreTest extends JMSClientTestSupport {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Override
   protected String getConfiguredProtocols() {
      return "AMQP,OPENWIRE,CORE";
   }

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      super.setUp();
   }

   @Test
   @Timeout(60)
   public void testMultipleCoreReceiving() throws Exception {

      Connection coreJmsConn = this.createCoreConnection();

      final int total = 100;

      try {
         Session session = coreJmsConn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         ClientSession coreSession = ((ActiveMQSession) session).getCoreSession();
         coreSession.createQueue(QueueConfiguration.of("exampleQueue1").setAddress("exampleQueueAddress"));
         coreSession.createQueue(QueueConfiguration.of("exampleQueue2").setAddress("exampleQueueAddress"));
         coreSession.createQueue(QueueConfiguration.of("exampleQueue3").setAddress("exampleQueueAddress"));

         ClientConsumer consumer1 = coreSession.createConsumer("exampleQueue1");
         CoreMessageHandler handler1 = new CoreMessageHandler(1);
         consumer1.setMessageHandler(handler1);
         ClientConsumer consumer2 = coreSession.createConsumer("exampleQueue2");
         CoreMessageHandler handler2 = new CoreMessageHandler(2);
         consumer2.setMessageHandler(handler2);
         CoreMessageHandler handler3 = new CoreMessageHandler(3);
         ClientConsumer consumer3 = coreSession.createConsumer("exampleQueue3");
         consumer3.setMessageHandler(handler3);

         sendAmqpMessages("exampleQueueAddress", total);

         handler1.assertMessagesReceived(total);
         handler2.assertMessagesReceived(total);
         handler3.assertMessagesReceived(total);
      } finally {
         coreJmsConn.close();
      }
   }

   @Test
   @Timeout(60)
   public void testAmqpFailedConversionFromCore() throws Exception {
      final SimpleString message = RandomUtil.randomUUIDSimpleString();
      Connection coreJmsConn = this.createCoreConnection();
      ConnectionFactory cfAMQP = new JmsConnectionFactory("amqp://127.0.0.1:" + AMQP_PORT);
      Connection connectionAMQP = cfAMQP.createConnection();
      try {
         connectionAMQP.start();
         Session sessionAMQP = connectionAMQP.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Destination destination = sessionAMQP.createQueue(getQueueName());
         MessageConsumer consumer = sessionAMQP.createConsumer(destination);

         Session session = coreJmsConn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         ClientSession coreSession = ((ActiveMQSession) session).getCoreSession();
         ClientProducer producer = coreSession.createProducer(getQueueName());

         /*
          * Create a message that will intentionally fail conversion.
          * The body of a TEXT_TYPE message should be written with writeNullableSimpleString().
          */
         ClientMessage m = coreSession.createMessage(true);
         m.setType(Message.TEXT_TYPE);
         m.getBodyBuffer().writeBytes(message.getData());
         producer.send(m);

         Wait.assertEquals(1L, () -> server.locateQueue(getDeadLetterAddress()).getMessageCount(), 2000, 100);

         m = coreSession.createMessage(true);
         m.setType(Message.TEXT_TYPE);
         m.getBodyBuffer().writeNullableSimpleString(message);
         producer.send(m);

         assertNotNull(consumer.receive(500));

      } finally {
         coreJmsConn.close();
         connectionAMQP.close();
      }
   }

   private void sendAmqpMessages(String address, int total) throws Exception {
      ConnectionFactory cfAMQP = new JmsConnectionFactory("amqp://127.0.0.1:" + AMQP_PORT);
      Connection connectionAMQP = cfAMQP.createConnection();
      try {
         connectionAMQP.start();
         Session sessionAMQP = connectionAMQP.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer sender = sessionAMQP.createProducer(new JmsTopic(address));

         for (int i = 0; i < total; i++) {
            sender.send(sessionAMQP.createTextMessage("hello"));
         }
      } finally {
         connectionAMQP.close();
      }
   }

   private class CoreMessageHandler implements MessageHandler {
      int id;
      AtomicInteger numMsg = new AtomicInteger();
      AtomicBoolean zeroLen = new AtomicBoolean();

      CoreMessageHandler(int id) {
         this.id = id;
      }

      @Override
      public void onMessage(ClientMessage message) {
         logger.debug("received: {}", message.getBodySize());
         if (message.getBodySize() == 0) {
            logger.debug("xxx found zero len message!");
            zeroLen.set(true);
         }

         logger.debug("[receiver {}] recieved: {}", id, numMsg.incrementAndGet());
      }

      public void assertMessagesReceived(int num) throws Exception {
         Wait.assertEquals(num, numMsg::get, 30000);

         assertFalse(zeroLen.get());
      }
   }

}
