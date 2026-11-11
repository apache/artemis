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

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.JMSProducer;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicPublisher;
import javax.jms.TopicSession;
import javax.jms.TopicSubscriber;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.postoffice.Bindings;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class JMSTopicConsumerTest extends JMSClientTestSupport {

   @Test
   @Timeout(60)
   public void testSendAndReceiveOnTopic() throws Exception {
      Connection connection = createConnection("myClientId");

      try {
         TopicSession session = (TopicSession) connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Topic topic = session.createTopic(getTopicName());
         TopicSubscriber consumer = session.createSubscriber(topic);
         TopicPublisher producer = session.createPublisher(topic);

         TextMessage message = session.createTextMessage("test-message");
         producer.send(message);

         producer.close();
         connection.start();

         message = (TextMessage) consumer.receive(1000);

         assertNotNull(message);
         assertNotNull(message.getText());
         assertEquals("test-message", message.getText());
      } finally {
         connection.close();
      }
   }

   @Test
   @Timeout(60)
   public void testSendAndReceiveOnAutoCreatedTopic() throws Exception {
      Connection connection = createConnection("myClientId");
      String topicName = UUID.randomUUID().toString();
      SimpleString simpleTopicName = SimpleString.of(topicName);

      try {
         TopicSession session = (TopicSession) connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Topic topic = session.createTopic(topicName);
         TopicPublisher producer = session.createPublisher(topic);

         TextMessage message = session.createTextMessage("test-message");
         // this will auto-create the address, but not the subscription queue
         producer.send(message);

         assertNotNull(server.getAddressInfo(simpleTopicName));
         assertEquals(RoutingType.MULTICAST, server.getAddressInfo(simpleTopicName).getRoutingType());
         assertTrue(server.getAddressInfo(simpleTopicName).isAutoCreated());
         assertTrue(server.getPostOffice().getBindingsForAddress(simpleTopicName).getBindings().isEmpty());

         // this will auto-create the subscription queue
         TopicSubscriber consumer = session.createSubscriber(topic);
         assertFalse(server.getPostOffice().getBindingsForAddress(simpleTopicName).getBindings().isEmpty());
         producer.send(message);

         producer.close();
         connection.start();

         message = (TextMessage) consumer.receive(1000);

         assertNotNull(message);
         assertNotNull(message.getText());
         assertEquals("test-message", message.getText());
         consumer.close();
         assertTrue(server.getPostOffice().getBindingsForAddress(simpleTopicName).getBindings().isEmpty());
      } finally {
         connection.close();
      }
   }

   @Test
   @Timeout(60)
   public void testSendAndReceiveOnAutoCreatedTopicJMS2() throws Exception {
      ConnectionFactory cf = new JmsConnectionFactory(getBrokerQpidJMSConnectionURI());
      JMSContext context = cf.createContext();
      String topicName = UUID.randomUUID().toString();
      SimpleString simpleTopicName = SimpleString.of(topicName);

      try {
         Topic topic = context.createTopic(topicName);
         JMSProducer producer = context.createProducer();

         TextMessage message = context.createTextMessage("test-message");
         // this will auto-create the address, but not the subscription queue
         producer.send(topic, message);

         assertNotNull(server.getAddressInfo(simpleTopicName));
         assertEquals(RoutingType.MULTICAST, server.getAddressInfo(simpleTopicName).getRoutingType());
         assertTrue(server.getAddressInfo(simpleTopicName).isAutoCreated());
         assertTrue(server.getPostOffice().getBindingsForAddress(simpleTopicName).getBindings().isEmpty());

         // this will auto-create the subscription queue
         JMSConsumer consumer = context.createConsumer(topic);
         assertFalse(server.getPostOffice().getBindingsForAddress(simpleTopicName).getBindings().isEmpty());
         producer.send(topic, message);

         context.start();

         message = (TextMessage) consumer.receive(1000);

         assertNotNull(message);
         assertNotNull(message.getText());
         assertEquals("test-message", message.getText());
         consumer.close();
         assertTrue(server.getPostOffice().getBindingsForAddress(simpleTopicName).getBindings().isEmpty());
      } finally {
         context.close();
      }
   }

   @Test
   @Timeout(60)
   public void testSendWithMultipleReceiversOnTopic() throws Exception {
      Connection connection = createConnection();

      try {
         TopicSession session = (TopicSession) connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Topic topic = session.createTopic(getTopicName());
         TopicSubscriber consumer1 = session.createSubscriber(topic);
         TopicSubscriber consumer2 = session.createSubscriber(topic);
         TopicPublisher producer = session.createPublisher(topic);

         TextMessage message = session.createTextMessage("test-message");
         producer.send(message);

         producer.close();
         connection.start();

         message = (TextMessage) consumer1.receive(1000);

         assertNotNull(message);
         assertNotNull(message.getText());
         assertEquals("test-message", message.getText());

         message = (TextMessage) consumer2.receive(1000);

         assertNotNull(message);
         assertNotNull(message.getText());
         assertEquals("test-message", message.getText());
      } finally {
         connection.close();
      }
   }

   @Test
   @Timeout(60)
   public void testDurableSubscriptionUnsubscribe() throws Exception {
      Connection connection = createConnection("myClientId");

      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Topic topic = session.createTopic(getTopicName());
         TopicSubscriber myDurSub = session.createDurableSubscriber(topic, "myDurSub");
         session.close();
         connection.close();

         connection = createConnection("myClientId");
         session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         myDurSub = session.createDurableSubscriber(topic, "myDurSub");
         myDurSub.close();

         assertNotNull(server.getPostOffice().getBinding(SimpleString.of("myClientId.myDurSub")));
         session.unsubscribe("myDurSub");
         assertNull(server.getPostOffice().getBinding(SimpleString.of("myClientId.myDurSub")));
         session.close();
         connection.close();
      } finally {
         connection.close();
      }
   }

   @Test
   @Timeout(60)
   public void testDurableSharedSubscriptionUnsubscribe() throws Exception {
      Connection connection = createConnection("myClientId");

      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Topic topic = session.createTopic(getTopicName());
         MessageConsumer myDurSub = session.createSharedDurableConsumer(topic, "myDurSub");
         assertNotNull(server.getPostOffice().getBinding(SimpleString.of("myClientId.myDurSub")));
         myDurSub.close();
         session.unsubscribe("myDurSub");
         session.close();
         connection.close();
         assertNull(server.getPostOffice().getBinding(SimpleString.of("myClientId.myDurSub")));
      } finally {
         connection.close();
      }
   }

   @Test
   @Timeout(60)
   public void testDurableMultipleSharedSubscriptionUnsubscribe() throws Exception {
      Connection connection = createConnection("myClientId");

      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session session2 = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Topic topic = session.createTopic(getTopicName());
         MessageConsumer myDurSub = session.createSharedDurableConsumer(topic, "myDurSub");
         MessageConsumer myDurSub2 = session2.createSharedDurableConsumer(topic, "myDurSub");
         assertNotNull(server.getPostOffice().getBinding(SimpleString.of("myClientId.myDurSub")));
         myDurSub.close();
         try {
            session.unsubscribe("myDurSub");
            fail("should throw exception on active durable subs");
         } catch (JMSException e) {
            //pass
         }
         myDurSub2.close();
         session.unsubscribe("myDurSub");
         session.close();
         connection.close();
         assertNull(server.getPostOffice().getBinding(SimpleString.of("myClientId.myDurSub")));
      } finally {
         connection.close();
      }
   }

   @Test
   @Timeout(60)
   public void testDurableSharedGlobalSubscriptionUnsubscribe() throws Exception {
      Connection connection = createConnection();

      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Topic topic = session.createTopic(getTopicName());
         MessageConsumer myDurSub = session.createSharedDurableConsumer(topic, "myDurSub");
         assertNotNull(server.getPostOffice().getBinding(SimpleString.of("myDurSub:global")));
         myDurSub.close();
         session.unsubscribe("myDurSub");
         session.close();
         connection.close();
         assertNull(server.getPostOffice().getBinding(SimpleString.of("myDurSub:global")));
      } finally {
         connection.close();
      }
   }

   @Test
   @Timeout(60)
   public void testDurableMultipleSharedGlobalSubscriptionUnsubscribe() throws Exception {
      Connection connection = createConnection();
      Connection connection2 = createConnection();

      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session session2 = connection2.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Topic topic = session.createTopic(getTopicName());
         MessageConsumer myDurSub = session.createSharedDurableConsumer(topic, "myDurSub");
         MessageConsumer myDurSub2 = session2.createSharedDurableConsumer(topic, "myDurSub");
         assertNotNull(server.getPostOffice().getBinding(SimpleString.of("myDurSub:global")));
         myDurSub.close();
         session.unsubscribe("myDurSub");
         session.close();
         connection.close();
         assertNotNull(server.getPostOffice().getBinding(SimpleString.of("myDurSub:global")));
         myDurSub2.close();
         session2.unsubscribe("myDurSub");
         session2.close();
         connection2.close();
         assertNull(server.getPostOffice().getBinding(SimpleString.of("myDurSub:global")));
      } finally {
         connection.close();
      }
   }

   @Test
   @Timeout(60)
   public void testTemporarySubscriptionDeleted() throws Exception {
      Connection connection = createConnection();

      try {
         TopicSession session = (TopicSession) connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Topic topic = session.createTopic(getTopicName());
         TopicSubscriber myNonDurSub = session.createSubscriber(topic);
         assertNotNull(myNonDurSub);

         Bindings bindingsForAddress = server.getPostOffice().getBindingsForAddress(SimpleString.of(getTopicName()));
         assertEquals(2, bindingsForAddress.getBindings().size());
         session.close();

         final CountDownLatch latch = new CountDownLatch(1);
         server.getRemotingService().getConnections().iterator().next().addCloseListener(() -> latch.countDown());

         connection.close();
         latch.await(5, TimeUnit.SECONDS);
         Wait.assertEquals(1, () -> server.getPostOffice().getBindingsForAddress(SimpleString.of(getTopicName())).getBindings().size(), 2000, 20);
      } finally {
         connection.close();
      }
   }

   @Test
   @Timeout(60)
   public void testMultipleDurableConsumersSendAndReceive() throws Exception {
      Connection connection = createConnection("myClientId");

      try {
         TopicSession session = (TopicSession) connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Topic topic = session.createTopic(getTopicName());

         int numMessages = 100;
         TopicSubscriber sub1 = session.createDurableSubscriber(topic, "myPubId1");
         TopicSubscriber sub2 = session.createDurableSubscriber(topic, "myPubId2");
         TopicSubscriber sub3 = session.createDurableSubscriber(topic, "myPubId3");

         Session sendSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer producer = sendSession.createProducer(topic);
         connection.start();
         for (int i = 0; i < numMessages; i++) {
            producer.send(sendSession.createTextMessage("message:" + i));
         }

         for (int i = 0; i < numMessages; i++) {
            TextMessage receive = (TextMessage) sub1.receive(5000);
            assertNotNull(receive);
            assertEquals(receive.getText(), "message:" + i);
            receive = (TextMessage) sub2.receive(5000);
            assertNotNull(receive);
            assertEquals(receive.getText(), "message:" + i);
            receive = (TextMessage) sub3.receive(5000);
            assertNotNull(receive);
            assertEquals(receive.getText(), "message:" + i);
         }
      } finally {
         connection.close();
      }
   }

   @Test
   @Timeout(60)
   public void testDurableSubscriptionReconnection() throws Exception {
      Connection connection = createConnection("myClientId");

      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Topic topic = session.createTopic(getTopicName());

         int numMessages = 100;
         TopicSubscriber sub = session.createDurableSubscriber(topic, "myPubId");

         Session sendSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer producer = sendSession.createProducer(topic);
         connection.start();

         for (int i = 0; i < numMessages; i++) {
            producer.send(sendSession.createTextMessage("message:" + i));
         }

         for (int i = 0; i < numMessages; i++) {
            TextMessage receive = (TextMessage) sub.receive(5000);
            assertNotNull(receive);
            assertEquals(receive.getText(), "message:" + i);
         }

         connection.close();
         connection = createConnection("myClientId");
         connection.setExceptionListener(exception -> exception.printStackTrace());
         session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         sub = session.createDurableSubscriber(topic, "myPubId");

         sendSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         producer = sendSession.createProducer(topic);
         connection.start();
         for (int i = 0; i < numMessages; i++) {
            producer.send(sendSession.createTextMessage("message:" + i));
         }
         for (int i = 0; i < numMessages; i++) {
            TextMessage receive = (TextMessage) sub.receive(5000);
            assertNotNull(receive);
            assertEquals(receive.getText(), "message:" + i);
         }
      } finally {
         connection.close();
      }
   }
}
