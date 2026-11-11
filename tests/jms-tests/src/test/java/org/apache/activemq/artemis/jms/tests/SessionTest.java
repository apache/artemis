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
package org.apache.activemq.artemis.jms.tests;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicConnection;
import javax.jms.TopicSession;
import javax.jms.XAConnection;
import javax.jms.XASession;

import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.tests.util.ProxyAssertSupport;
import org.junit.jupiter.api.Test;

public class SessionTest extends ActiveMQServerTestCase {



   @Test
   public void testCreateProducer() throws Exception {
      Connection conn = getConnectionFactory().createConnection();
      Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      sess.createProducer(ActiveMQServerTestCase.topic1);
      conn.close();
   }

   @Test
   public void testCreateProducerOnNullQueue() throws Exception {
      Connection conn = getConnectionFactory().createConnection();
      Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Message m = sess.createTextMessage("something");

      MessageProducer p = sess.createProducer(null);

      p.send(queue1, m);

      MessageConsumer c = sess.createConsumer(queue1);
      conn.start();

      // receiveNoWait is not guaranteed to return message immediately
      TextMessage rm = (TextMessage) c.receive(1000);

      ProxyAssertSupport.assertEquals("something", rm.getText());

      conn.close();
   }

   @Test
   public void testCreateConsumer() throws Exception {
      Connection conn = getConnectionFactory().createConnection();
      Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      sess.createConsumer(ActiveMQServerTestCase.topic1);
      conn.close();
   }

   @Test
   public void testGetSession2() throws Exception {
      deployConnectionFactory(0, JMSFactoryType.CF, "ConnectionFactory", "/ConnectionFactory");
      XAConnection conn = getXAConnectionFactory().createXAConnection();
      XASession sess = conn.createXASession();

      sess.getSession();
      conn.close();
   }

   //
   // createQueue()/createTopic()
   //

   @Test
   public void testCreateNonExistentQueue() throws Exception {
      AddressSettings addressSettings = new AddressSettings();
      addressSettings.setAutoCreateQueues(false);
      getJmsServer().getAddressSettingsRepository().addMatch("#", addressSettings);

      Connection conn = getConnectionFactory().createConnection();
      Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      try {
         sess.createQueue("QueueThatDoesNotExist");
         ProxyAssertSupport.fail();
      } catch (JMSException e) {
      }
      conn.close();
   }

   @Test
   public void testCreateQueueOnATopicSession() throws Exception {
      TopicConnection c = (TopicConnection) getConnectionFactory().createConnection();
      TopicSession s = c.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);

      try {
         s.createQueue("TestQueue");
         ProxyAssertSupport.fail("should throw IllegalStateException");
      } catch (javax.jms.IllegalStateException e) {
         // OK
      }
      c.close();
   }

   @Test
   public void testCreateQueueWhileTopicWithSameNameExists() throws Exception {
      AddressSettings addressSettings = new AddressSettings();
      addressSettings.setAutoCreateQueues(false);
      addressSettings.setAutoCreateAddresses(false);
      getJmsServer().getAddressSettingsRepository().addMatch("#", addressSettings);

      Connection conn = getConnectionFactory().createConnection();
      Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      try {
         sess.createQueue("TestTopic");
         ProxyAssertSupport.fail("should throw JMSException");
      } catch (JMSException e) {
         // OK
      }
      conn.close();
   }

   @Test
   public void testCreateQueue() throws Exception {
      Connection conn = getConnectionFactory().createConnection();
      Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Queue queue = sess.createQueue("Queue1");

      MessageProducer producer = sess.createProducer(queue);
      MessageConsumer consumer = sess.createConsumer(queue);
      conn.start();

      Message m = sess.createTextMessage("testing");
      producer.send(m);

      Message m2 = consumer.receive(3000);

      ProxyAssertSupport.assertNotNull(m2);
      conn.close();
   }

   @Test
   public void testCreateNonExistentTopic() throws Exception {
      getJmsServer().getAddressSettingsRepository().addMatch("#", new AddressSettings().setAutoCreateQueues(false));
      getJmsServer().getAddressSettingsRepository().addMatch("#", new AddressSettings().setAutoCreateAddresses(false));
      Connection conn = getConnectionFactory().createConnection();
      Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      try {
         sess.createTopic("TopicThatDoesNotExist");
         ProxyAssertSupport.fail("should throw JMSException");
      } catch (JMSException e) {
         // OK
      }
      conn.close();
   }

   @Test
   public void testCreateTopicOnAQueueSession() throws Exception {
      QueueConnection c = (QueueConnection) getConnectionFactory().createConnection();
      QueueSession s = c.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);

      try {
         s.createTopic("TestTopic");
         ProxyAssertSupport.fail("should throw IllegalStateException");
      } catch (javax.jms.IllegalStateException e) {
         // OK
      }
      c.close();
   }

   @Test
   public void testCreateTopicWhileQueueWithSameNameExists() throws Exception {
      getJmsServer().getAddressSettingsRepository().addMatch("#", new AddressSettings().setAutoCreateQueues(false));
      getJmsServer().getAddressSettingsRepository().addMatch("#", new AddressSettings().setAutoCreateAddresses(false));
      Connection conn = getConnectionFactory().createConnection();
      Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      try {
         sess.createTopic("TestQueue");
         ProxyAssertSupport.fail("should throw JMSException");
      } catch (JMSException e) {
         // OK
      }
      conn.close();
   }

   @Test
   public void testCreateTopic() throws Exception {
      Connection conn = getConnectionFactory().createConnection();
      Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      Topic topic = sess.createTopic("Topic1");

      MessageProducer producer = sess.createProducer(topic);
      producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

      MessageConsumer consumer = sess.createConsumer(topic);
      conn.start();

      class TestRunnable implements Runnable {

         boolean exceptionThrown;

         public Message m;

         MessageConsumer consumer;

         TestRunnable(final MessageConsumer consumer) {
            this.consumer = consumer;
         }

         @Override
         public void run() {
            try {
               m = consumer.receive(3000);
            } catch (Exception e) {
               exceptionThrown = true;
            }
         }
      }

      TestRunnable tr1 = new TestRunnable(consumer);
      Thread t1 = new Thread(tr1);
      t1.start();

      Message m = sess.createTextMessage("testing");
      producer.send(m);

      t1.join();

      ProxyAssertSupport.assertFalse(tr1.exceptionThrown);
      ProxyAssertSupport.assertNotNull(tr1.m);

      conn.close();
   }

   @Test
   public void testGetXAResource2() throws Exception {
      XAConnection conn = getXAConnectionFactory().createXAConnection();
      XASession sess = conn.createXASession();

      sess.getXAResource();
      conn.close();
   }

   @Test
   public void testIllegalState() throws Exception {
      // IllegalStateException should be thrown if commit or rollback
      // is invoked on a non transacted session
      Connection conn = getConnectionFactory().createConnection();
      Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageProducer prod = sess.createProducer(queue1);
      prod.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
      Message m = sess.createTextMessage("hello");
      prod.send(m);

      try {
         sess.rollback();
         ProxyAssertSupport.fail();
      } catch (javax.jms.IllegalStateException e) {
      }

      try {
         sess.commit();
         ProxyAssertSupport.fail();
      } catch (javax.jms.IllegalStateException e) {
      }

      conn.close();

      removeAllMessages(queue1.getQueueName(), true);
   }

   //
   // Test session state
   //

   @Test
   public void testCreateTwoSessions() throws Exception {
      Connection conn = getConnectionFactory().createConnection();
      Session sessionOne = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);
      ProxyAssertSupport.assertFalse(sessionOne.getTransacted());
      Session sessionTwo = conn.createSession(true, -1);
      ProxyAssertSupport.assertTrue(sessionTwo.getTransacted());

      // this test whether session's transacted state is correctly scoped per instance (by an
      // interceptor or othewise)
      ProxyAssertSupport.assertFalse(sessionOne.getTransacted());

      conn.close();
   }

   @Test
   public void testCloseAndCreateSession() throws Exception {
      Connection c = getConnectionFactory().createConnection();
      Session s = c.createSession(false, Session.AUTO_ACKNOWLEDGE);

      s.close();

      // this test whether session's closed state is correctly scoped per instance (by an
      // interceptor or othewise)
      s = c.createSession(true, -1);

      c.close();
   }

   @Test
   public void testCloseNoClientAcknowledgment() throws Exception {
      // send a message to the queue

      Connection conn = getConnectionFactory().createConnection();
      Session s = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      s.createProducer(queue1).send(s.createTextMessage("wont_ack"));
      conn.close();

      conn = getConnectionFactory().createConnection();
      s = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);
      conn.start();

      TextMessage m = (TextMessage) s.createConsumer(queue1).receive(1000);

      ProxyAssertSupport.assertEquals("wont_ack", m.getText());

      // Do NOT ACK

      s.close(); // this should cancel the delivery

      // get the message again
      s = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      m = (TextMessage) s.createConsumer(queue1).receive(1000);

      ProxyAssertSupport.assertEquals("wont_ack", m.getText());

      conn.close();
   }

   @Test
   public void testCloseInTransaction() throws Exception {
      // send a message to the queue

      Connection conn = getConnectionFactory().createConnection();
      Session s = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      s.createProducer(queue1).send(s.createTextMessage("bex"));
      conn.close();

      conn = getConnectionFactory().createConnection();
      Session session = conn.createSession(true, -1);
      conn.start();

      TextMessage m = (TextMessage) session.createConsumer(queue1).receive(1000);

      ProxyAssertSupport.assertEquals("bex", m.getText());

      // make sure the acknowledment hasn't been sent to the channel
      assertRemainingMessages(1);

      // close the session
      session.close();

      // JMS 1.1 4.4.1: "Closing a transacted session must roll back its transaction in progress"

      assertRemainingMessages(1);

      conn.close();

      // make sure I can still get the right message

      conn = getConnectionFactory().createConnection();
      s = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      conn.start();
      TextMessage rm = (TextMessage) s.createConsumer(queue1).receive(1000);

      ProxyAssertSupport.assertEquals("bex", rm.getText());

      conn.close();
   }
}
