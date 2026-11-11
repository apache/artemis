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
package org.apache.activemq.artemis.tests.integration.jms.multiprotocol;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import java.util.Arrays;
import java.util.Collection;

import org.apache.activemq.artemis.api.core.SimpleString;
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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@ExtendWith(ParameterizedTestExtension.class)
public class JMSSharedDurableConsumerTest extends MultiprotocolJMSClientTestSupport {

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

   private void testSharedDurableConsumer(Connection connection1, Connection connection2) throws JMSException {
      try {
         Session session1 = connection1.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session session2 = connection2.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Topic topic = session1.createTopic(getTopicName());
         Topic topic2 = session2.createTopic(getTopicName());

         final MessageConsumer consumer1 = session1.createSharedDurableConsumer(topic, "SharedConsumer");
         final MessageConsumer consumer2 = session2.createSharedDurableConsumer(topic2, "SharedConsumer");

         MessageProducer producer = session1.createProducer(topic);
         producer.setDeliveryMode(DeliveryMode.PERSISTENT);
         connection1.start();

         TextMessage message = session1.createTextMessage();
         message.setText("hello");
         producer.send(message);

         Message message1 = consumer1.receive(100);
         Message message2 = consumer2.receive(100);

         Message received = null;
         if (message1 != null) {
            assertNull(message2, "Message should only be delivered once per subscribtion but see twice");
            received = message1;
         } else {
            received = message2;
         }
         assertNotNull(received, "Should have received a message by now.");
         assertInstanceOf(TextMessage.class, received, "Should be an instance of TextMessage");

         consumer1.close();
         consumer2.close();
         session1.unsubscribe("SharedConsumer");
      } finally {
         connection1.close();
         connection2.close();
      }
   }

   @TestTemplate
   @Timeout(30)
   public void testSharedDurableConsumer() throws Exception {
      Connection connection = createConnection(); //AMQP
      Connection connection2 = createConnection(); //AMQP

      testSharedDurableConsumer(connection, connection2);
   }

   @TestTemplate
   @Timeout(30)
   public void testSharedDurableConsumerWithArtemisClient() throws Exception {

      Connection connection = createCoreConnection(); //CORE
      Connection connection2 = createCoreConnection(); //CORE

      testSharedDurableConsumer(connection, connection2);

   }

   @TestTemplate
   @Timeout(30)
   public void testSharedDurableConsumerWithAMQPClientAndArtemisClient() throws Exception {
      assumeTrue(amqpUseCoreSubscriptionNaming);

      Connection connection = createConnection(); //AMQP
      Connection connection2 = createCoreConnection(); //CORE

      testSharedDurableConsumer(connection, connection2);

   }

   @TestTemplate
   @Timeout(30)
   public void testSharedDurableConsumerWithArtemisClientAndAMQPClient() throws Exception {
      assumeTrue(amqpUseCoreSubscriptionNaming);

      Connection connection = createCoreConnection(); //CORE
      Connection connection2 = createConnection(); //AMQP

      testSharedDurableConsumer(connection, connection2);

   }

   @TestTemplate
   @Timeout(30)
   public void testSharedDurableConsumerWithSelectorChange() throws Exception {
      SimpleString qName = amqpUseCoreSubscriptionNaming ? SimpleString.of("SharedConsumer") : SimpleString.of("SharedConsumer:global");
      Connection connection = createConnection(true);
      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Topic topic = session.createTopic(getTopicName());

         MessageConsumer consumer = session.createSharedDurableConsumer(topic, "SharedConsumer", "a='1'");
         MessageProducer producer = session.createProducer(session.createTopic(getTopicName()));
         Message message = session.createMessage();
         message.setStringProperty("a", "1");
         producer.send(message);
         QueueImpl queue = (QueueImpl) server.getPostOffice().getBinding(qName).getBindable();
         Wait.assertEquals(1, queue::getMessageCount);
         assertEquals(-1, queue.getMaxConsumers());
         consumer.close();
         MessageConsumer consumer2 = session.createSharedDurableConsumer(topic, "SharedConsumer", "a=b and b=c");
         queue = (QueueImpl) server.getPostOffice().getBinding(qName).getBindable();
         Wait.assertEquals(0, queue::getMessageCount);
         assertEquals(-1, queue.getMaxConsumers());
      } finally {
         connection.close();
      }
   }
}
