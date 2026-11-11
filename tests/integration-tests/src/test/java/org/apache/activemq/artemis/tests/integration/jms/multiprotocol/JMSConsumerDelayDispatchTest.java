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
package org.apache.activemq.artemis.tests.integration.jms.multiprotocol;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.junit.jupiter.api.Test;

public class JMSConsumerDelayDispatchTest extends MultiprotocolJMSClientTestSupport {

   private SimpleString queueName = SimpleString.of("jms.consumer.delay.queue");
   private SimpleString normalQueueName = SimpleString.of("jms.normal.queue");

   private static final long DELAY_BEFORE_DISPATCH = 2000L;

   @Override
   protected void createAddressAndQueues(ActiveMQServer server) throws Exception {
      super.createAddressAndQueues(server);
      server.createQueue(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST).setExclusive(true).setConsumersBeforeDispatch(2).setDelayBeforeDispatch(DELAY_BEFORE_DISPATCH));
      server.createQueue(QueueConfiguration.of(normalQueueName).setRoutingType(RoutingType.ANYCAST).setExclusive(true));
   }

   @Test
   public void testNoDelayOnDefaultAMQP() throws Exception {
      testNoDelayOnDefault(AMQPConnection);
   }

   @Test
   public void testNoDelayOnDefaultOpenWire() throws Exception {
      testNoDelayOnDefault(OpenWireConnection);
   }

   @Test
   public void testNoDelayOnDefaultCore() throws Exception {
      testNoDelayOnDefault(CoreConnection);
   }

   private void testNoDelayOnDefault(ConnectionSupplier supplier) throws Exception {
      sendMessage(normalQueueName, supplier);

      Connection connection = supplier.createConnection();

      try {
         Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         connection.start();

         Destination queue = session.createQueue(normalQueueName.toString());
         MessageConsumer consumer = session.createConsumer(queue);

         assertNotNull(consumer.receive(1000));
      } finally {
         connection.close();
      }
   }

   @Test
   public void testDelayBeforeDispatchAMQP() throws Exception {
      testDelayBeforeDispatch(AMQPConnection);
   }

   @Test
   public void testDelayBeforeDispatchOpenWire() throws Exception {
      testDelayBeforeDispatch(OpenWireConnection);
   }

   @Test
   public void testDelayBeforeDispatchCore() throws Exception {
      testDelayBeforeDispatch(CoreConnection);
   }

   private void testDelayBeforeDispatch(ConnectionSupplier supplier) throws Exception {
      sendMessage(queueName, supplier);

      Connection connection = supplier.createConnection();

      try {
         Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         connection.start();

         Destination queue = session.createQueue(queueName.toString());
         MessageConsumer consumer = session.createConsumer(queue);

         assertNull(consumer.receiveNoWait());
         Thread.sleep(DELAY_BEFORE_DISPATCH);

         assertNotNull(consumer.receive(DELAY_BEFORE_DISPATCH));
      } finally {
         connection.close();
      }
   }

   @Test
   public void testConsumersBeforeDispatchAMQP() throws Exception {
      testConsumersBeforeDispatch(AMQPConnection);
   }

   @Test
   public void testConsumersBeforeDispatchOpenWire() throws Exception {
      testConsumersBeforeDispatch(OpenWireConnection);
   }

   @Test
   public void testConsumersBeforeDispatchCore() throws Exception {
      testConsumersBeforeDispatch(CoreConnection);
   }

   private void testConsumersBeforeDispatch(ConnectionSupplier supplier) throws Exception {
      sendMessage(queueName, supplier);

      Connection connection = supplier.createConnection();

      try {
         Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         connection.start();
         Destination queue = session.createQueue(queueName.toString());

         MessageConsumer consumer1 = session.createConsumer(queue);

         assertNull(consumer1.receiveNoWait());

         MessageConsumer consumer2 = session.createConsumer(queue);

         assertTrue(consumer1.receive(1000) != null || consumer2.receive(1000) != null);
      } finally {
         connection.close();
      }
   }

   @Test
   public void testContinueAndResetConsumerAMQP() throws Exception {
      testContinueAndResetConsumer(AMQPConnection);
   }

   @Test
   public void testContinueAndResetConsumerOpenWire() throws Exception {
      testContinueAndResetConsumer(OpenWireConnection);
   }

   @Test
   public void testContinueAndResetConsumerCore() throws Exception {
      testContinueAndResetConsumer(CoreConnection);
   }

   private void testContinueAndResetConsumer(ConnectionSupplier supplier) throws Exception {
      sendMessage(queueName, supplier);

      Connection connection = supplier.createConnection();

      try {
         Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         connection.start();
         Destination queue = session.createQueue(queueName.toString());

         MessageConsumer consumer1 = session.createConsumer(queue);

         assertNull(consumer1.receiveNoWait());

         MessageConsumer consumer2 = session.createConsumer(queue);

         assertTrue(consumer1.receive(1000) != null || consumer2.receive(1000) != null);

         consumer2.close();

         //Ensure that now dispatch is active, if we close a consumer, dispatching continues.
         sendMessage(queueName, supplier);

         assertNotNull(consumer1.receiveNoWait());

         //Stop all consumers, which should reset dispatch rules.
         consumer1.close();
         session.close();
         session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         //Ensure that once all consumers are stopped, that dispatch rules reset and wait for min consumers.
         sendMessage(queueName, supplier);

         MessageConsumer consumer3 = session.createConsumer(queue);

         assertNull(consumer3.receiveNoWait());

         MessageConsumer consumer4 = session.createConsumer(queue);

         assertTrue(consumer3.receive(1000) != null || consumer4.receive(1000) != null);


         //Stop all consumers, which should reset dispatch rules.
         consumer3.close();
         consumer4.close();
         session.close();
         session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         //Ensure that once all consumers are stopped, that dispatch rules reset and wait for delay.
         sendMessage(queueName, supplier);

         MessageConsumer consumer5 = session.createConsumer(queue);

         assertNull(consumer5.receiveNoWait());

         Thread.sleep(DELAY_BEFORE_DISPATCH);

         assertNotNull(consumer5.receive(DELAY_BEFORE_DISPATCH));

      } finally {
         connection.close();
      }
   }

   public void sendMessage(SimpleString queue, ConnectionSupplier supplier) throws Exception {
      Connection connection = supplier.createConnection();
      try {

         Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         connection.start();

         Destination destination = session.createQueue(queue.toString());
         MessageProducer producer = session.createProducer(destination);

         TextMessage message = session.createTextMessage();
         message.setText("Message");
         producer.send(message);
      } finally {
         connection.close();
      }
   }

}
