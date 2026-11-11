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

import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.jms.tests.util.ProxyAssertSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class TransactedSessionTest extends JMSTestCase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Test
   public void testSimpleRollback() throws Exception {
      // send a message
      Connection conn = null;

      try {
         conn = createConnection();
         Session s = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         s.createProducer(queue1).send(s.createTextMessage("one"));

         s.close();

         s = conn.createSession(true, Session.SESSION_TRANSACTED);
         MessageConsumer c = s.createConsumer(queue1);
         conn.start();
         Message m = c.receive(1000);
         ProxyAssertSupport.assertNotNull(m);

         ProxyAssertSupport.assertEquals("one", ((TextMessage) m).getText());
         ProxyAssertSupport.assertFalse(m.getJMSRedelivered());
         ProxyAssertSupport.assertEquals(1, m.getIntProperty("JMSXDeliveryCount"));

         s.rollback();

         // get the message again
         m = c.receive(1000);
         ProxyAssertSupport.assertNotNull(m);

         ProxyAssertSupport.assertTrue(m.getJMSRedelivered());
         ProxyAssertSupport.assertEquals(2, m.getIntProperty("JMSXDeliveryCount"));

         conn.close();

         Long i = getMessageCountForQueue("Queue1");

         ProxyAssertSupport.assertEquals(1, i.intValue());
      } finally {
         if (conn != null) {
            conn.close();
         }
         removeAllMessages(queue1.getQueueName(), true);
      }
   }

   @Test
   public void testRedeliveredFlagTopic() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();

         Session sessSend = conn.createSession(true, Session.SESSION_TRANSACTED);
         Session sess1 = conn.createSession(true, Session.SESSION_TRANSACTED);
         MessageConsumer consumer1 = sess1.createConsumer(ActiveMQServerTestCase.topic1);

         MessageProducer producer = sessSend.createProducer(ActiveMQServerTestCase.topic1);
         Message mSent = sessSend.createTextMessage("igloo");
         producer.send(mSent);
         sessSend.commit();

         conn.start();

         TextMessage mRec1 = (TextMessage) consumer1.receive(2000);
         ProxyAssertSupport.assertNotNull(mRec1);

         ProxyAssertSupport.assertEquals("igloo", mRec1.getText());
         ProxyAssertSupport.assertFalse(mRec1.getJMSRedelivered());

         sess1.rollback(); // causes redelivery for session

         mRec1 = (TextMessage) consumer1.receive(2000);
         ProxyAssertSupport.assertEquals("igloo", mRec1.getText());
         ProxyAssertSupport.assertTrue(mRec1.getJMSRedelivered());

         sess1.commit();
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   /**
    * Test redelivery works ok for Topic
    */
   @Test
   public void testRedeliveredTopic() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();

         Session sess = conn.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer producer = sess.createProducer(ActiveMQServerTestCase.topic1);

         MessageConsumer consumer = sess.createConsumer(ActiveMQServerTestCase.topic1);
         conn.start();

         Message mSent = sess.createTextMessage("igloo");
         producer.send(mSent);

         sess.commit();

         TextMessage mRec = (TextMessage) consumer.receive(2000);

         ProxyAssertSupport.assertEquals("igloo", mRec.getText());
         ProxyAssertSupport.assertFalse(mRec.getJMSRedelivered());

         sess.rollback();

         mRec = (TextMessage) consumer.receive(2000);

         ProxyAssertSupport.assertNotNull(mRec);
         ProxyAssertSupport.assertEquals("igloo", mRec.getText());
         ProxyAssertSupport.assertTrue(mRec.getJMSRedelivered());

         sess.commit();
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   @Test
   public void testReceivedRollbackTopic() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();

         Session sess = conn.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer producer = sess.createProducer(ActiveMQServerTestCase.topic1);

         MessageConsumer consumer = sess.createConsumer(ActiveMQServerTestCase.topic1);
         conn.start();

         logger.debug("sending message first time");
         TextMessage mSent = sess.createTextMessage("igloo");
         producer.send(mSent);
         logger.debug("sent message first time");

         sess.commit();

         TextMessage mRec = (TextMessage) consumer.receive(2000);
         ProxyAssertSupport.assertEquals("igloo", mRec.getText());

         sess.commit();

         logger.debug("sending message again");
         mSent.setText("rollback");
         producer.send(mSent);
         logger.debug("sent message again");

         sess.commit();

         mRec = (TextMessage) consumer.receive(2000);
         ProxyAssertSupport.assertEquals("rollback", mRec.getText());
         sess.rollback();

         TextMessage mRec2 = (TextMessage) consumer.receive(2000);

         sess.commit();

         ProxyAssertSupport.assertNotNull(mRec2);

         ProxyAssertSupport.assertEquals(mRec.getText(), mRec2.getText());
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   /**
    * Send some messages in transacted session. Don't commit.
    * Verify message are not received by consumer.
    */
   @Test
   public void testSendNoCommitTopic() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();

         Session producerSess = conn.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer producer = producerSess.createProducer(ActiveMQServerTestCase.topic1);

         Session consumerSess = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         MessageConsumer consumer = consumerSess.createConsumer(ActiveMQServerTestCase.topic1);
         conn.start();

         final int NUM_MESSAGES = 10;

         // Send some messages
         for (int i = 0; i < NUM_MESSAGES; i++) {
            Message m = producerSess.createMessage();
            producer.send(m);
         }

         Message m = consumer.receiveNoWait();
         ProxyAssertSupport.assertNull(m);
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   /**
    * Send some messages in transacted session. Commit.
    * Verify message are received by consumer.
    */
   @Test
   public void testSendCommitTopic() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();

         Session producerSess = conn.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer producer = producerSess.createProducer(ActiveMQServerTestCase.topic1);

         Session consumerSess = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         MessageConsumer consumer = consumerSess.createConsumer(ActiveMQServerTestCase.topic1);
         conn.start();

         final int NUM_MESSAGES = 10;

         // Send some messages
         for (int i = 0; i < NUM_MESSAGES; i++) {
            Message m = producerSess.createMessage();
            producer.send(m);
         }

         producerSess.commit();

         int count = 0;
         while (true) {
            Message m = consumer.receive(500);
            if (m == null) {
               break;
            }
            count++;
         }

         ProxyAssertSupport.assertEquals(NUM_MESSAGES, count);
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   /**
    * Send some messages.
    * Receive them in a transacted session.
    * Commit the receiving session
    * Close the connection
    * Create a new connection, session and consumer - verify messages are not redelivered
    */
   @Test
   public void testAckCommitTopic() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();

         Session producerSess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer producer = producerSess.createProducer(ActiveMQServerTestCase.topic1);

         Session consumerSess = conn.createSession(true, Session.SESSION_TRANSACTED);
         MessageConsumer consumer = consumerSess.createConsumer(ActiveMQServerTestCase.topic1);
         conn.start();

         final int NUM_MESSAGES = 10;

         // Send some messages
         for (int i = 0; i < NUM_MESSAGES; i++) {
            Message m = producerSess.createMessage();
            producer.send(m);
         }
         int count = 0;
         while (true) {
            Message m = consumer.receive(500);
            if (m == null) {
               break;
            }
            count++;
         }

         ProxyAssertSupport.assertEquals(NUM_MESSAGES, count);

         consumerSess.commit();

         conn.stop();
         consumer.close();

         conn.close();

         conn = createConnection();

         consumerSess = conn.createSession(true, Session.CLIENT_ACKNOWLEDGE);
         consumer = consumerSess.createConsumer(queue1);
         conn.start();

         Message m = consumer.receiveNoWait();

         ProxyAssertSupport.assertNull(m);
      } finally {
         if (conn != null) {
            conn.close();
         }
      }

   }

   /*
    * Send some messages in a transacted session.
    * Rollback the session.
    * Verify messages aren't received by consumer.
    */

   @Test
   public void testSendRollbackTopic() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();

         Session producerSess = conn.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer producer = producerSess.createProducer(ActiveMQServerTestCase.topic1);

         Session consumerSess = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         MessageConsumer consumer = consumerSess.createConsumer(ActiveMQServerTestCase.topic1);
         conn.start();

         final int NUM_MESSAGES = 10;

         // Send some messages
         for (int i = 0; i < NUM_MESSAGES; i++) {
            Message m = producerSess.createMessage();
            producer.send(m);
         }

         producerSess.rollback();

         Message m = consumer.receiveNoWait();

         ProxyAssertSupport.assertNull(m);
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   /**
    * Make sure redelivered flag is set on redelivery via rollback
    */
   @Test
   public void testRedeliveredQueue() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();

         Session sess = conn.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer producer = sess.createProducer(queue1);

         MessageConsumer consumer = sess.createConsumer(queue1);
         conn.start();

         Message mSent = sess.createTextMessage("igloo");
         producer.send(mSent);

         sess.commit();

         TextMessage mRec = (TextMessage) consumer.receive(2000);
         ProxyAssertSupport.assertEquals("igloo", mRec.getText());
         ProxyAssertSupport.assertFalse(mRec.getJMSRedelivered());

         sess.rollback();
         mRec = (TextMessage) consumer.receive(2000);
         ProxyAssertSupport.assertEquals("igloo", mRec.getText());
         ProxyAssertSupport.assertTrue(mRec.getJMSRedelivered());

         sess.commit();
      } finally {
         if (conn != null) {
            conn.close();
         }
      }

   }

   /**
    * Make sure redelivered flag is set on redelivery via rollback, different setup: we close the rolled back session
    * and we receive the message whose acknowledgment was cancelled on a new session.
    */
   @Test
   public void testRedeliveredQueue2() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();

         Session sendSession = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer prod = sendSession.createProducer(queue1);
         prod.send(sendSession.createTextMessage("a message"));

         conn.close();

         conn = createConnection();
         Session sess = conn.createSession(true, Session.SESSION_TRANSACTED);

         MessageConsumer cons = sess.createConsumer(queue1);

         conn.start();

         TextMessage tm = (TextMessage) cons.receive(1000);
         ProxyAssertSupport.assertNotNull(tm);

         ProxyAssertSupport.assertEquals("a message", tm.getText());

         ProxyAssertSupport.assertFalse(tm.getJMSRedelivered());
         ProxyAssertSupport.assertEquals(1, tm.getIntProperty("JMSXDeliveryCount"));

         sess.rollback();

         sess.close();

         Session sess2 = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         cons = sess2.createConsumer(queue1);

         tm = (TextMessage) cons.receive(1000);

         ProxyAssertSupport.assertEquals("a message", tm.getText());

         ProxyAssertSupport.assertEquals(2, tm.getIntProperty("JMSXDeliveryCount"));

         ProxyAssertSupport.assertTrue(tm.getJMSRedelivered());
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   @Test
   public void testReceivedRollbackQueue() throws Exception {
      Connection conn = createConnection();

      Session sess = conn.createSession(true, Session.SESSION_TRANSACTED);
      MessageProducer producer = sess.createProducer(queue1);

      MessageConsumer consumer = sess.createConsumer(queue1);
      conn.start();

      TextMessage mSent = sess.createTextMessage("igloo");
      producer.send(mSent);
      logger.trace("sent1");

      sess.commit();

      TextMessage mRec = (TextMessage) consumer.receive(1000);
      ProxyAssertSupport.assertNotNull(mRec);
      logger.trace("Got 1");
      ProxyAssertSupport.assertNotNull(mRec);
      ProxyAssertSupport.assertEquals("igloo", mRec.getText());

      sess.commit();

      mSent.setText("rollback");
      producer.send(mSent);

      sess.commit();

      logger.trace("Receiving 2");
      mRec = (TextMessage) consumer.receive(1000);
      ProxyAssertSupport.assertNotNull(mRec);

      logger.trace("Received 2");
      ProxyAssertSupport.assertNotNull(mRec);
      ProxyAssertSupport.assertEquals("rollback", mRec.getText());

      sess.rollback();

      TextMessage mRec2 = (TextMessage) consumer.receive(1000);
      ProxyAssertSupport.assertNotNull(mRec2);
      ProxyAssertSupport.assertEquals("rollback", mRec2.getText());

      sess.commit();

      ProxyAssertSupport.assertEquals(mRec.getText(), mRec2.getText());

      conn.close();
   }

   /**
    * Send some messages in transacted session. Don't commit.
    * Verify message are not received by consumer.
    */
   @Test
   public void testSendNoCommitQueue() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();

         Session producerSess = conn.createSession(true, Session.CLIENT_ACKNOWLEDGE);
         MessageProducer producer = producerSess.createProducer(queue1);

         Session consumerSess = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         MessageConsumer consumer = consumerSess.createConsumer(queue1);
         conn.start();

         final int NUM_MESSAGES = 10;

         // Send some messages
         for (int i = 0; i < NUM_MESSAGES; i++) {
            Message m = producerSess.createMessage();
            producer.send(m);
         }

         checkEmpty(queue1);
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   /**
    * Send some messages in transacted session. Commit.
    * Verify message are received by consumer.
    */
   @Test
   public void testSendCommitQueue() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();

         Session producerSess = conn.createSession(true, Session.CLIENT_ACKNOWLEDGE);
         MessageProducer producer = producerSess.createProducer(queue1);

         Session consumerSess = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         MessageConsumer consumer = consumerSess.createConsumer(queue1);
         conn.start();

         final int NUM_MESSAGES = 10;

         // Send some messages
         for (int i = 0; i < NUM_MESSAGES; i++) {
            Message m = producerSess.createMessage();
            producer.send(m);
         }

         producerSess.commit();

         int count = 0;
         while (true) {
            Message m = consumer.receive(500);
            if (m == null) {
               break;
            }
            count++;
         }

         ProxyAssertSupport.assertEquals(NUM_MESSAGES, count);
      } finally {
         if (conn != null) {
            conn.close();
         }
         removeAllMessages(queue1.getQueueName(), true);
      }

   }

   @Test
   @Disabled
   public void _testSendCommitQueueCommitsInOrder() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();

         Session producerSess = conn.createSession(true, Session.CLIENT_ACKNOWLEDGE);
         MessageProducer producer = producerSess.createProducer(queue1);
         producer.setDeliveryMode(DeliveryMode.PERSISTENT);

         Session consumerSession = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer consumer = consumerSession.createConsumer(queue1);
         CountDownLatch latch = new CountDownLatch(1);
         conn.start();
         myReceiver myReceiver = new myReceiver(latch, conn);
         consumer.setMessageListener(myReceiver);
         long lastBatchTime = System.currentTimeMillis();
         int sentId = 0;
         boolean started = false;
         // Send some messages
         while (true) {
            try {
               Message m = producerSess.createMessage();
               m.setIntProperty("foo", sentId);
               sentId++;
               producer.send(m);

               if (sentId == 1 || System.currentTimeMillis() - lastBatchTime > 50) {
                  lastBatchTime = System.currentTimeMillis();
                  producerSess.commit();
               }
            } catch (JMSException e) {
               //ignore connection closed by consumer
            }

            // wait for the first message to be received before we continue sending
            if (!started) {
               assertTrue(latch.await(5, TimeUnit.SECONDS));
               started = true;
            } else {
               if (myReceiver.failed) {
                  throw myReceiver.e;
               }
            }
         }

      } finally {
         if (conn != null) {
            conn.close();
         }
         removeAllMessages(queue1.getQueueName(), true);
      }

   }

   class myReceiver implements MessageListener {

      int count = 0;
      boolean started = false;
      private final CountDownLatch startLatch;
      boolean failed = false;
      Exception e = null;

      private final Connection conn;

      myReceiver(CountDownLatch startLatch, Connection conn) {
         this.startLatch = startLatch;
         this.conn = conn;
      }

      @Override
      public void onMessage(Message message) {
         if (!started) {
            startLatch.countDown();
            started = true;
         }
         try {
            int foo = message.getIntProperty("foo");
            if (foo != count) {
               e = new Exception("received out of order expected " + count + " received " + foo);
               failed = true;
               conn.close();
            }
            count++;
         } catch (JMSException e) {

            this.e = e;
            failed = true;
            try {
               conn.close();
            } catch (JMSException e1) {
               e1.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
         }
      }
   }

   /**
    * Test IllegalStateException is thrown if commit is called on a non-transacted session
    */
   @Test
   public void testCommitIllegalState() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();

         Session producerSess = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         boolean thrown = false;
         try {
            producerSess.commit();
         } catch (javax.jms.IllegalStateException e) {
            thrown = true;
         }

         ProxyAssertSupport.assertTrue(thrown);
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   /**
    * Send some messages.
    * Receive them in a transacted session.
    * Do not commit the receiving session.
    * Close the connection
    * Create a new connection, session and consumer - verify messages are redelivered
    */
   @Test
   public void testAckNoCommitQueue() throws Exception {
      Connection conn = null;

      try {

         conn = createConnection();

         Session producerSess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer producer = producerSess.createProducer(queue1);

         Session consumerSess = conn.createSession(true, Session.CLIENT_ACKNOWLEDGE);
         MessageConsumer consumer = consumerSess.createConsumer(queue1);
         conn.start();

         final int NUM_MESSAGES = 10;

         // Send some messages
         for (int i = 0; i < NUM_MESSAGES; i++) {
            Message m = producerSess.createMessage();
            producer.send(m);
         }

         int count = 0;
         while (true) {
            Message m = consumer.receive(500);
            if (m == null) {
               break;
            }
            count++;
         }

         ProxyAssertSupport.assertEquals(NUM_MESSAGES, count);

         conn.stop();
         consumer.close();

         conn.close();

         conn = createConnection();

         consumerSess = conn.createSession(true, Session.CLIENT_ACKNOWLEDGE);
         consumer = consumerSess.createConsumer(queue1);
         conn.start();

         count = 0;

         while (true) {
            Message m = consumer.receive(500);
            if (m == null) {
               break;
            }
            count++;
         }

         ProxyAssertSupport.assertEquals(NUM_MESSAGES, count);
      } finally {
         if (conn != null) {
            conn.close();
         }
         removeAllMessages(queue1.getQueueName(), true);
      }
   }

   /**
    * Send some messages.
    * Receive them in a transacted session.
    * Commit the receiving session
    * Close the connection
    * Create a new connection, session and consumer - verify messages are not redelivered
    */
   @Test
   public void testAckCommitQueue() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();

         Session producerSess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer producer = producerSess.createProducer(queue1);

         Session consumerSess = conn.createSession(true, Session.SESSION_TRANSACTED);
         MessageConsumer consumer = consumerSess.createConsumer(queue1);
         conn.start();

         final int NUM_MESSAGES = 10;

         // Send some messages
         for (int i = 0; i < NUM_MESSAGES; i++) {
            Message m = producerSess.createMessage();
            producer.send(m);
         }

         int count = 0;
         while (true) {
            Message m = consumer.receive(500);
            if (m == null) {
               break;
            }
            count++;
         }

         ProxyAssertSupport.assertEquals(NUM_MESSAGES, count);

         consumerSess.commit();

         conn.stop();
         consumer.close();

         conn.close();

         conn = createConnection();

         consumerSess = conn.createSession(true, Session.CLIENT_ACKNOWLEDGE);
         consumer = consumerSess.createConsumer(queue1);
         conn.start();

         Message m = consumer.receiveNoWait();

         ProxyAssertSupport.assertNull(m);
      } finally {
         if (conn != null) {
            conn.close();
         }
      }

   }

   /*
    * Send some messages in a transacted session.
    * Rollback the session.
    * Verify messages aren't received by consumer.
    */

   @Test
   public void testSendRollbackQueue() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();

         Session producerSess = conn.createSession(true, Session.AUTO_ACKNOWLEDGE);
         MessageProducer producer = producerSess.createProducer(queue1);

         Session consumerSess = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         MessageConsumer consumer = consumerSess.createConsumer(queue1);
         conn.start();

         final int NUM_MESSAGES = 10;

         // Send some messages
         for (int i = 0; i < NUM_MESSAGES; i++) {
            Message m = producerSess.createMessage();
            producer.send(m);
         }

         producerSess.rollback();

         Message m = consumer.receiveNoWait();

         ProxyAssertSupport.assertNull(m);
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   /**
    * Test IllegalStateException is thrown if rollback is called on a non-transacted session
    */
   @Test
   public void testRollbackIllegalState() throws Exception {
      Connection conn = createConnection();

      Session producerSess = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);

      boolean thrown = false;
      try {
         producerSess.rollback();
      } catch (javax.jms.IllegalStateException e) {
         thrown = true;
      }

      ProxyAssertSupport.assertTrue(thrown);
   }

   /*
    * Send some messages.
    * Receive them in a transacted session.
    * Rollback the receiving session
    * Close the connection
    * Create a new connection, session and consumer - verify messages are redelivered
    */

   @Test
   public void testAckRollbackQueue() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();

         Session producerSess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer producer = producerSess.createProducer(queue1);

         Session consumerSess = conn.createSession(true, Session.CLIENT_ACKNOWLEDGE);
         MessageConsumer consumer = consumerSess.createConsumer(queue1);
         conn.start();

         final int NUM_MESSAGES = 10;

         // Send some messages
         for (int i = 0; i < NUM_MESSAGES; i++) {
            Message m = producerSess.createMessage();
            producer.send(m);
         }

         int count = 0;
         while (true) {
            Message m = consumer.receive(500);
            if (m == null) {
               break;
            }
            count++;
         }

         ProxyAssertSupport.assertEquals(NUM_MESSAGES, count);

         consumerSess.rollback();

         conn.stop();
         consumer.close();

         conn.close();

         conn = createConnection();

         consumerSess = conn.createSession(true, Session.CLIENT_ACKNOWLEDGE);
         consumer = consumerSess.createConsumer(queue1);
         conn.start();

         count = 0;
         while (true) {
            Message m = consumer.receive(500);
            if (m == null) {
               break;
            }
            count++;
         }

         ProxyAssertSupport.assertEquals(NUM_MESSAGES, count);

      } finally {
         if (conn != null) {
            conn.close();
         }
         removeAllMessages(queue1.getQueueName(), true);
      }

   }

   /**
    * Send multiple messages in multiple contiguous sessions
    */
   @Test
   public void testSendMultipleQueue() throws Exception {
      Connection conn = createConnection();

      Session producerSess = conn.createSession(true, Session.AUTO_ACKNOWLEDGE);
      MessageProducer producer = producerSess.createProducer(queue1);

      Session consumerSess = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);
      MessageConsumer consumer = consumerSess.createConsumer(queue1);
      conn.start();

      final int NUM_MESSAGES = 10;
      final int NUM_TX = 10;

      // Send some messages

      for (int j = 0; j < NUM_TX; j++) {
         for (int i = 0; i < NUM_MESSAGES; i++) {
            Message m = producerSess.createMessage();
            producer.send(m);
         }

         producerSess.commit();
      }

      int count = 0;
      while (true) {
         Message m = consumer.receive(500);
         if (m == null) {
            break;
         }
         count++;
         m.acknowledge();
      }

      ProxyAssertSupport.assertEquals(NUM_MESSAGES * NUM_TX, count);
   }


}
