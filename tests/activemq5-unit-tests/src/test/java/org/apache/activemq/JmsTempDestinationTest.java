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
package org.apache.activemq;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.InvalidDestinationException;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TemporaryQueue;
import javax.jms.TextMessage;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.activemq.util.Wait;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JmsTempDestinationTest {
   private Connection connection;
   private ActiveMQConnectionFactory factory;
   protected List<Connection> connections = Collections.synchronizedList(new ArrayList<>());

   @Before
   public void setUp() throws Exception {
      factory = new ActiveMQConnectionFactory("tcp://localhost:61616");
      factory.setAlwaysSyncSend(true);
      connection = factory.createConnection();
      connections.add(connection);
   }

   @After
   public void tearDown() throws Exception {
      for (Iterator<Connection> iter = connections.iterator(); iter.hasNext(); ) {
         Connection conn = iter.next();
         try {
            conn.close();
         } catch (Throwable e) {
         }
         iter.remove();
      }
   }

   /**
    * Make sure Temp destination can only be consumed by local connection
    */
   @Test
   public void testTempDestOnlyConsumedByLocalConn() throws JMSException {
      connection.start();

      Session tempSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      TemporaryQueue queue = tempSession.createTemporaryQueue();
      MessageProducer producer = tempSession.createProducer(queue);
      producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
      TextMessage message = tempSession.createTextMessage("First");
      producer.send(message);

      // temp destination should not be consume when using another connection
      Connection otherConnection = factory.createConnection();
      connections.add(otherConnection);
      Session otherSession = otherConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      TemporaryQueue otherQueue = otherSession.createTemporaryQueue();
      MessageConsumer consumer = otherSession.createConsumer(otherQueue);
      Message msg = consumer.receive(3000);
      Assert.assertNull(msg);

      // should throw InvalidDestinationException when consuming a temp
      // destination from another connection
      try {
         consumer = otherSession.createConsumer(queue);
         Assert.fail("Send should fail since temp destination should be used from another connection");
      } catch (InvalidDestinationException e) {
         Assert.assertTrue("failed to throw an exception", true);
      }

      // should be able to consume temp destination from the same connection
      consumer = tempSession.createConsumer(queue);
      msg = consumer.receive(3000);
      Assert.assertNotNull(msg);

   }

   /**
    * Make sure that a temp queue does not drop message if there is an active consumers.
    */
   @Test
   public void testTempQueueHoldsMessagesWithConsumers() throws JMSException {
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Queue queue = session.createTemporaryQueue();
      MessageConsumer consumer = session.createConsumer(queue);
      connection.start();

      MessageProducer producer = session.createProducer(queue);
      producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
      TextMessage message = session.createTextMessage("Hello");
      producer.send(message);

      Message message2 = consumer.receive(1000);
      Assert.assertNotNull(message2);
      Assert.assertTrue("Expected message to be a TextMessage", message2 instanceof TextMessage);
      Assert.assertTrue("Expected message to be a '" + message.getText() + "'", ((TextMessage) message2).getText().equals(message.getText()));
   }

   /**
    * Make sure that a temp queue does not drop message if there are no active consumers.
    */
   @Test
   public void testTempQueueHoldsMessagesWithoutConsumers() throws JMSException {

      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Queue queue = session.createTemporaryQueue();
      MessageProducer producer = session.createProducer(queue);
      producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
      TextMessage message = session.createTextMessage("Hello");
      producer.send(message);

      connection.start();
      MessageConsumer consumer = session.createConsumer(queue);
      Message message2 = consumer.receive(3000);
      Assert.assertNotNull(message2);
      Assert.assertTrue("Expected message to be a TextMessage", message2 instanceof TextMessage);
      Assert.assertTrue("Expected message to be a '" + message.getText() + "'", ((TextMessage) message2).getText().equals(message.getText()));

   }

   /**
    * Test temp queue works under load
    */
   @Test
   public void testTmpQueueWorksUnderLoad() throws JMSException {
      int count = 500;
      int dataSize = 1024;

      List<BytesMessage> list = new ArrayList<>(count);
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Queue queue = session.createTemporaryQueue();
      MessageProducer producer = session.createProducer(queue);
      producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

      byte[] data = new byte[dataSize];
      for (int i = 0; i < count; i++) {
         BytesMessage message = session.createBytesMessage();
         message.writeBytes(data);
         message.setIntProperty("c", i);
         producer.send(message);
         list.add(message);
      }

      connection.start();
      MessageConsumer consumer = session.createConsumer(queue);
      for (int i = 0; i < count; i++) {
         Message message2 = consumer.receive(2000);
         Assert.assertTrue(message2 != null);
         Assert.assertEquals(i, message2.getIntProperty("c"));
         Assert.assertTrue(message2.equals(list.get(i)));
      }
   }

   /**
    * Make sure you cannot publish to a temp destination that does not exist anymore.
    */
   @Test
   public void testPublishFailsForClosedConnection() throws Exception {

      Connection tempConnection = factory.createConnection();
      connections.add(tempConnection);

      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      connection.start();

      Session tempSession = tempConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      final TemporaryQueue queue = tempSession.createTemporaryQueue();

      final ActiveMQConnection activeMQConnection = (ActiveMQConnection) connection;
      Assert.assertTrue("creation advisory received in time with async dispatch", Wait.waitFor(() -> activeMQConnection.activeTempDestinations.contains(queue)));

      // This message delivery should work since the temp connection is still
      // open.
      MessageProducer producer = session.createProducer(queue);
      producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
      TextMessage message = session.createTextMessage("First");
      producer.send(message);

      // Closing the connection should destroy the temp queue that was
      // created.
      tempConnection.close();
      Thread.sleep(5000); // Wait a little bit to let the delete take effect.

      // This message delivery NOT should work since the temp connection is
      // now closed.
      try {
         message = session.createTextMessage("Hello");
         producer.send(message);
         Assert.fail("Send should fail since temp destination should not exist anymore.");
      } catch (JMSException e) {
         e.printStackTrace();
      }
   }

   /**
    * Make sure you cannot publish to a temp destination that does not exist anymore.
    */
   @Test
   public void testPublishFailsForDestroyedTempDestination() throws Exception {

      Connection tempConnection = factory.createConnection();
      connections.add(tempConnection);

      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      connection.start();

      //In artemis, if you send a message to a topic where the consumer isn't there yet,
      //message will get lost. So the create temp queue request has to happen
      //after the connection is started (advisory consumer registered).
      Session tempSession = tempConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      final TemporaryQueue queue = tempSession.createTemporaryQueue();

      final ActiveMQConnection activeMQConnection = (ActiveMQConnection) connection;
      Assert.assertTrue("creation advisory received in time with async dispatch", Wait.waitFor(() -> activeMQConnection.activeTempDestinations.contains(queue)));

      // This message delivery should work since the temp connection is still
      // open.
      MessageProducer producer = session.createProducer(queue);
      producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
      TextMessage message = session.createTextMessage("First");
      producer.send(message);

      // deleting the Queue will cause sends to fail
      queue.delete();
      Thread.sleep(5000); // Wait a little bit to let the delete take effect.

      // This message delivery NOT should work since the temp connection is
      // now closed.
      try {
         message = session.createTextMessage("Hello");
         producer.send(message);
         Assert.fail("Send should fail since temp destination should not exist anymore.");
      } catch (JMSException e) {
         Assert.assertTrue("failed to throw an exception", true);
      }
   }

   /**
    * Test you can't delete a Destination with Active Subscribers
    */
   @Test
   public void testDeleteDestinationWithSubscribersFails() throws JMSException {
      Connection connection = factory.createConnection();
      connections.add(connection);
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      TemporaryQueue queue = session.createTemporaryQueue();

      connection.start();

      session.createConsumer(queue);

      // This message delivery should NOT work since the temp connection is
      // now closed.
      try {
         queue.delete();
         Assert.fail("Should fail as Subscribers are active");
      } catch (JMSException e) {
         Assert.assertTrue("failed to throw an exception", true);
      }
   }
}
