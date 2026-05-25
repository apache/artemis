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

package org.apache.activemq.artemis.tests.integration.crossprotocol;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import javax.jms.TextMessage;
import javax.jms.Topic;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.integration.openwire.OpenWireTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.activemq.artemis.tests.util.CFUtil.createConnectionFactory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(ParameterizedTestExtension.class)
public class RequestReplyMultiProtocolTest extends OpenWireTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   String protocolSender;
   String protocolConsumer;
   ConnectionFactory senderCF;
   ConnectionFactory consumerCF;
   private static final SimpleString queueName = SimpleString.of("RequestReplyQueueTest");
   private static final SimpleString topicName = SimpleString.of("RequestReplyTopicTest");
   private static final SimpleString replyQueue = SimpleString.of("ReplyOnRequestReplyQueueTest");

   public RequestReplyMultiProtocolTest(String protocolSender, String protocolConsumer) {
      this.protocolSender = protocolSender;
      this.protocolConsumer = protocolConsumer;
   }

   @Parameters(name = "senderProtocol={0},receiverProtocol={1}")
   public static Iterable<Object[]> data() {
      return Arrays.asList(new Object[][] {
         {"OPENWIRE", "OPENWIRE"},
         {"OPENWIRE", "CORE"},
         {"OPENWIRE", "AMQP"},
         {"CORE", "OPENWIRE"},
         {"CORE", "CORE"},
         {"CORE", "AMQP"},
         {"AMQP", "OPENWIRE"},
         {"AMQP", "CORE"},
         {"AMQP", "AMQP"},
      });
   }



   @BeforeEach
   @Override
   public void setUp() throws Exception {
      super.setUp();

      senderCF = createConnectionFactory(protocolSender, urlString);
      consumerCF = createConnectionFactory(protocolConsumer, urlString);

      Wait.assertTrue(server::isStarted);
      Wait.assertTrue(server::isActive);
      this.server.createQueue(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST));
      this.server.createQueue(QueueConfiguration.of(replyQueue).setRoutingType(RoutingType.ANYCAST));
      AddressInfo info = new AddressInfo(topicName, RoutingType.MULTICAST);
      this.server.addAddressInfo(info);
   }


   @TestTemplate
   public void testReplyToUsingQueue() throws Throwable {
      testReplyTo(false);
   }

   @TestTemplate
   public void testReplyToUsingTopic() throws Throwable {
      testReplyTo(true);
   }

   private void testReplyTo(boolean useTopic) throws Throwable {
      Connection senderConn = senderCF.createConnection();
      Connection consumerConn = consumerCF.createConnection();
      consumerConn.setClientID("consumer");
      try {
         Session consumerSess = consumerConn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Destination consumerDestination;
         if (useTopic) {
            consumerDestination = consumerSess.createTopic(topicName.toString());
         } else {
            consumerDestination = consumerSess.createQueue(queueName.toString());
         }
         MessageConsumer consumer;

         if (useTopic) {
            consumer = consumerSess.createDurableSubscriber((Topic) consumerDestination, "test");
         } else {
            consumer = consumerSess.createConsumer(consumerDestination);
         }
         consumerConn.start();


         Session senderSess = senderConn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         List<Destination> replyToDestinations = new LinkedList<>();
         replyToDestinations.add(senderSess.createQueue(replyQueue.toString()));
         replyToDestinations.add(senderSess.createTopic(topicName.toString()));
         replyToDestinations.add(senderSess.createTemporaryQueue());
         replyToDestinations.add(senderSess.createTemporaryTopic());
         Destination senderDestination;

         if (useTopic) {
            senderDestination = senderSess.createTopic(topicName.toString());
         } else {
            senderDestination = senderSess.createQueue(queueName.toString());
         }
         MessageProducer sender = senderSess.createProducer(senderDestination);

         int i = 0;
         for (Destination destination : replyToDestinations) {
            TextMessage message = senderSess.createTextMessage("hello " + (i++));
            message.setJMSReplyTo(destination);
            sender.send(message);
         }


         i = 0;
         for (Destination destination : replyToDestinations) {
            TextMessage received = (TextMessage)consumer.receive(5000);

            assertNotNull(received);
            logger.debug("Destination::{}", received.getJMSDestination());

            if (useTopic) {
               assertInstanceOf(Topic.class, received.getJMSDestination(), "JMSDestination type is " + received.getJMSDestination().getClass());
            } else {
               assertInstanceOf(Queue.class, received.getJMSDestination(), "JMSDestination type is " + received.getJMSDestination().getClass());
            }

            assertNotNull(received.getJMSReplyTo());
            assertEquals("hello " + (i++), received.getText());

            logger.debug("received {} and {}", received.getText(), received.getJMSReplyTo());

            if (destination instanceof Queue queue) {
               assertInstanceOf(Queue.class, received.getJMSReplyTo(), "Type is " + received.getJMSReplyTo().getClass().toString());
               assertEquals(queue.getQueueName(), ((Queue)received.getJMSReplyTo()).getQueueName());
            }
            if (destination instanceof Topic topic) {
               assertInstanceOf(Topic.class, received.getJMSReplyTo(), "Type is " + received.getJMSReplyTo().getClass().toString());
               assertEquals(topic.getTopicName(), ((Topic)received.getJMSReplyTo()).getTopicName());
            }
            if (destination instanceof TemporaryQueue temporaryQueue) {
               assertInstanceOf(TemporaryQueue.class, received.getJMSReplyTo(), "Type is " + received.getJMSReplyTo().getClass().toString());
               assertEquals(temporaryQueue.getQueueName(), ((TemporaryQueue)received.getJMSReplyTo()).getQueueName());
            }
            if (destination instanceof TemporaryTopic temporaryTopic) {
               assertInstanceOf(TemporaryTopic.class, received.getJMSReplyTo(), "Type is " + received.getJMSReplyTo().getClass().toString());
               assertEquals(temporaryTopic.getTopicName(), ((TemporaryTopic)received.getJMSReplyTo()).getTopicName());
            }
         }
      } catch (Throwable e) {
         e.printStackTrace();
         throw e;
      } finally {
         try {
            senderConn.close();
         } catch (Throwable e) {
            e.printStackTrace();
         }
         try {
            consumerConn.close();
         } catch (Throwable e) {
            e.printStackTrace();
         }
      }

   }

}


