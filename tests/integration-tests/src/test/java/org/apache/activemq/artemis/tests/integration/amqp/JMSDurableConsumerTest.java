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
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.QueueImpl;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameter;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@ExtendWith(ParameterizedTestExtension.class)
public class JMSDurableConsumerTest extends JMSClientTestSupport {

   @Parameters(name = "{index}: amqpUseCoreSubscriptionNaming={0}")
   public static Collection<Object[]> parameters() {
      return Arrays.asList(new Object[][] {
         {true}, {false}
      });
   }

   @Parameter(index = 0)
   public boolean amqpUseCoreSubscriptionNaming;

   @Override
   protected void addConfiguration(ActiveMQServer server) {
      server.getConfiguration().setAmqpUseCoreSubscriptionNaming(amqpUseCoreSubscriptionNaming);
   }

   @TestTemplate
   @Timeout(30)
   public void testDurableConsumerAsync() throws Exception {
      final CountDownLatch latch = new CountDownLatch(1);
      final AtomicReference<Message> received = new AtomicReference<>();
      String durableClientId = getTopicName() + "-ClientId";

      Connection connection = createConnection(durableClientId);

      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Topic topic = session.createTopic(getTopicName());
         MessageConsumer consumer = session.createDurableSubscriber(topic, "DurbaleTopic");
         consumer.setMessageListener(message -> {
            received.set(message);
            latch.countDown();
         });

         MessageProducer producer = session.createProducer(topic);
         producer.setDeliveryMode(DeliveryMode.PERSISTENT);
         connection.start();

         TextMessage message = session.createTextMessage();
         message.setText("hello");
         producer.send(message);

         assertTrue(latch.await(10, TimeUnit.SECONDS));
         assertNotNull(received.get(), "Should have received a message by now.");
         assertInstanceOf(TextMessage.class, received.get(), "Should be an instance of TextMessage");
      } finally {
         connection.close();
      }
   }

   @TestTemplate
   @Timeout(30)
   public void testDurableConsumerSync() throws Exception {
      String durableClientId = getTopicName() + "-ClientId";

      Connection connection = createConnection(durableClientId);
      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Topic topic = session.createTopic(getTopicName());
         final MessageConsumer consumer = session.createDurableSubscriber(topic, "DurbaleTopic");
         MessageProducer producer = session.createProducer(topic);
         producer.setDeliveryMode(DeliveryMode.PERSISTENT);
         connection.start();

         TextMessage message = session.createTextMessage();
         message.setText("hello");
         producer.send(message);

         final AtomicReference<Message> msg = new AtomicReference<>();
         assertTrue(Wait.waitFor(() -> {
            msg.set(consumer.receiveNoWait());
            return msg.get() != null;
         }, TimeUnit.SECONDS.toMillis(25), TimeUnit.MILLISECONDS.toMillis(200)));

         assertNotNull(msg.get(), "Should have received a message by now.");
         assertInstanceOf(TextMessage.class, msg.get(), "Should be an instance of TextMessage");
      } finally {
         connection.close();
      }
   }

   @TestTemplate
   @Timeout(30)
   public void testDurableConsumerUnsubscribe() throws Exception {
      String durableClientId = getTopicName() + "-ClientId";

      Connection connection = createConnection(durableClientId);
      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Topic topic = session.createTopic(getTopicName());
         MessageConsumer consumer = session.createDurableSubscriber(topic, "DurbaleTopic");

         assertTrue(Wait.waitFor(() -> server.getTotalConsumerCount() == 1, TimeUnit.SECONDS.toMillis(20), TimeUnit.MILLISECONDS.toMillis(250)));

         consumer.close();

         assertTrue(Wait.waitFor(() -> server.getTotalConsumerCount() == 0, TimeUnit.SECONDS.toMillis(20), TimeUnit.MILLISECONDS.toMillis(250)));

         session.unsubscribe("DurbaleTopic");
         assertTrue(Wait.waitFor(() -> server.getTotalConsumerCount() == 0, TimeUnit.SECONDS.toMillis(20), TimeUnit.MILLISECONDS.toMillis(250)));
      } finally {
         connection.close();
      }
   }

   @TestTemplate
   @Timeout(30)
   public void testDurableConsumerUnsubscribeWhileNoSubscription() throws Exception {
      Connection connection = createConnection();

      try {
         connection.start();

         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         assertTrue(Wait.waitFor(() -> server.getTotalConsumerCount() == 0, TimeUnit.SECONDS.toMillis(20), TimeUnit.MILLISECONDS.toMillis(250)));

         try {
            session.unsubscribe("DurbaleTopic");
            fail("Should have thrown as subscription is in use.");
         } catch (JMSException ex) {
         }
      } finally {
         connection.close();
      }
   }

   @TestTemplate
   @Timeout(30)
   public void testDurableConsumerUnsubscribeWhileActive() throws Exception {
      String durableClientId = getTopicName() + "-ClientId";

      Connection connection = createConnection(durableClientId);
      try {
         connection.start();

         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Topic topic = session.createTopic(getTopicName());
         MessageConsumer consumer = session.createDurableSubscriber(topic, "DurbaleTopic");

         assertNotNull(consumer);
         assertNull(consumer.receive(10));

         try {
            session.unsubscribe("DurbaleTopic");
            fail("Should have thrown as subscription is in use.");
         } catch (JMSException ex) {
         }
      } finally {
         connection.close();
      }
   }

   @TestTemplate
   @Timeout(30)
   public void testDurableConsumerLarge() throws Exception {
      String durableClientId = getTopicName() + "-ClientId";

      Connection connection = createConnection(durableClientId);
      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Topic topic = session.createTopic(getTopicName());
         final MessageConsumer consumer1 = session.createDurableSubscriber(topic, "DurbaleSub1");
         final MessageConsumer consumer2 = session.createDurableSubscriber(topic, "DurbaleSub2");
         MessageProducer producer = session.createProducer(topic);
         producer.setDeliveryMode(DeliveryMode.PERSISTENT);
         connection.start();

         ObjectMessage objMessage = session.createObjectMessage();
         BigObject bigObject = new BigObject(ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE);
         objMessage.setObject(bigObject);
         producer.send(objMessage);

         ObjectMessage msg1 = (ObjectMessage)consumer1.receive(5000);
         assertNotNull(msg1);
         assertInstanceOf(ObjectMessage.class, msg1, "Should be an instance of TextMessage");

         ObjectMessage msg2 = (ObjectMessage)consumer2.receive(5000);
         assertNotNull(msg2, "Should have received a message by now.");
         assertInstanceOf(ObjectMessage.class, msg2, "Should be an instance of TextMessage");
      } finally {
         connection.close();
      }
   }

   public static class BigObject implements Serializable {

      private char[] contents;

      public BigObject(int size) {
         contents = new char[size];
         for (int i = 0; i < size; i++) {
            contents[i] = 'X';
         }
      }
   }

   @TestTemplate
   @Timeout(30)
   public void testDurableConsumerWithSelectorChange() throws Exception {
      SimpleString qName = SimpleString.of("foo.SharedConsumer");
      Connection connection = createConnection("foo", true);
      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Topic topic = session.createTopic(getTopicName());

         MessageConsumer consumer = session.createDurableConsumer(topic, "SharedConsumer", "a=b", false);
         MessageProducer producer = session.createProducer(session.createTopic(getTopicName()));
         Message message = session.createMessage();
         message.setStringProperty("a", "1");
         message.setStringProperty("b", "1");
         producer.send(message);

         QueueImpl queue = (QueueImpl) server.getPostOffice().getBinding(qName).getBindable();
         assertEquals(1, queue.getMaxConsumers());
         Wait.assertEquals(1, queue::getMessageCount);
         consumer.close();
         MessageConsumer consumer2 = session.createDurableConsumer(topic, "SharedConsumer", "a=b and b=c", false);
         queue = (QueueImpl) server.getPostOffice().getBinding(qName).getBindable();
         assertEquals(1, queue.getMaxConsumers());
         Wait.assertEquals(0, queue::getMessageCount);

         message = session.createMessage();
         message.setStringProperty("a", "2");
         message.setStringProperty("b", "2");
         message.setStringProperty("c", "2");
         producer.send(message);

         Wait.assertEquals(1, queue::getMessageCount);

         connection.start();

         assertNotNull(consumer2.receive(5000));
      } finally {
         connection.close();
      }
   }
}
