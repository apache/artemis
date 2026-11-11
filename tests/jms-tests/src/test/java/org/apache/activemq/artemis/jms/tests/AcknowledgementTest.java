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

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.TopicConnection;
import javax.jms.TopicPublisher;
import javax.jms.TopicSession;
import javax.jms.TopicSubscriber;
import java.util.concurrent.CountDownLatch;

import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.jms.tests.util.ProxyAssertSupport;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class AcknowledgementTest extends JMSTestCase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   /**
    * Topics shouldn't hold on to messages if there are no subscribers
    */
   @Test
   public void testPersistentMessagesForTopicDropped() throws Exception {
      TopicConnection topicConn = createTopicConnection();
      TopicSession sess = topicConn.createTopicSession(true, 0);
      TopicPublisher pub = sess.createPublisher(ActiveMQServerTestCase.topic1);
      pub.setDeliveryMode(DeliveryMode.PERSISTENT);

      Message m = sess.createTextMessage("testing123");
      pub.publish(m);
      sess.commit();

      topicConn.close();

      checkEmpty(ActiveMQServerTestCase.topic1);
   }

   /**
    * Topics shouldn't hold on to messages when the non-durable subscribers close
    */
   @Test
   public void testPersistentMessagesForTopicDropped2() throws Exception {
      TopicConnection topicConn = createTopicConnection();
      topicConn.start();
      TopicSession sess = topicConn.createTopicSession(true, 0);
      TopicPublisher pub = sess.createPublisher(ActiveMQServerTestCase.topic1);
      TopicSubscriber sub = sess.createSubscriber(ActiveMQServerTestCase.topic1);
      pub.setDeliveryMode(DeliveryMode.PERSISTENT);

      Message m = sess.createTextMessage("testing123");
      pub.publish(m);
      sess.commit();

      // receive but rollback
      TextMessage m2 = (TextMessage) sub.receive(3000);

      ProxyAssertSupport.assertNotNull(m2);
      ProxyAssertSupport.assertEquals("testing123", m2.getText());

      sess.rollback();

      topicConn.close();

      checkEmpty(ActiveMQServerTestCase.topic1);
   }

   @Test
   public void testRollbackRecover() throws Exception {
      TopicConnection topicConn = createTopicConnection();
      TopicSession sess = topicConn.createTopicSession(true, 0);
      TopicPublisher pub = sess.createPublisher(ActiveMQServerTestCase.topic1);
      TopicSubscriber cons = sess.createSubscriber(ActiveMQServerTestCase.topic1);
      topicConn.start();

      Message m = sess.createTextMessage("testing123");
      pub.publish(m);
      sess.commit();

      TextMessage m2 = (TextMessage) cons.receive(3000);
      ProxyAssertSupport.assertNotNull(m2);
      ProxyAssertSupport.assertEquals("testing123", m2.getText());

      sess.rollback();

      m2 = (TextMessage) cons.receive(3000);
      ProxyAssertSupport.assertNotNull(m2);
      ProxyAssertSupport.assertEquals("testing123", m2.getText());

      topicConn.close();

      topicConn = createTopicConnection();
      topicConn.start();

      // test 2

      TopicSession newsess = topicConn.createTopicSession(true, 0);
      TopicPublisher newpub = newsess.createPublisher(ActiveMQServerTestCase.topic1);
      TopicSubscriber newcons = newsess.createSubscriber(ActiveMQServerTestCase.topic1);

      Message m3 = newsess.createTextMessage("testing456");
      newpub.publish(m3);
      newsess.commit();

      TextMessage m4 = (TextMessage) newcons.receive(3000);
      ProxyAssertSupport.assertNotNull(m4);
      ProxyAssertSupport.assertEquals("testing456", m4.getText());

      newsess.commit();

      newpub.publish(m3);
      newsess.commit();

      TextMessage m5 = (TextMessage) newcons.receive(3000);
      ProxyAssertSupport.assertNotNull(m5);
      ProxyAssertSupport.assertEquals("testing456", m5.getText());

      newsess.rollback();

      TextMessage m6 = (TextMessage) newcons.receive(3000);
      ProxyAssertSupport.assertNotNull(m6);
      ProxyAssertSupport.assertEquals("testing456", m6.getText());

      newsess.commit();
   }

   @Test
   public void testTransactionalAcknowledgement() throws Exception {
      Connection conn = createConnection();

      Session producerSess = conn.createSession(true, Session.SESSION_TRANSACTED);
      MessageProducer producer = producerSess.createProducer(queue1);

      Session consumerSess = conn.createSession(true, Session.SESSION_TRANSACTED);
      MessageConsumer consumer = consumerSess.createConsumer(queue1);
      conn.start();

      final int NUM_MESSAGES = 20;

      // Send some messages
      for (int i = 0; i < NUM_MESSAGES; i++) {
         Message m = producerSess.createMessage();
         producer.send(m);
      }

      assertRemainingMessages(0);

      producerSess.rollback();

      // Send some messages
      for (int i = 0; i < NUM_MESSAGES; i++) {
         Message m = producerSess.createMessage();
         producer.send(m);
      }
      assertRemainingMessages(0);

      producerSess.commit();

      assertRemainingMessages(NUM_MESSAGES);

      int count = 0;
      while (true) {
         Message m = consumer.receive(200);
         if (m == null) {
            break;
         }
         count++;
      }

      assertRemainingMessages(NUM_MESSAGES);

      ProxyAssertSupport.assertEquals(count, NUM_MESSAGES);

      consumerSess.rollback();

      assertRemainingMessages(NUM_MESSAGES);

      int i = 0;
      for (; i < NUM_MESSAGES; i++) {
         consumer.receive();
      }

      assertRemainingMessages(NUM_MESSAGES);

      // if I don't receive enough messages, the test will timeout

      consumerSess.commit();

      assertRemainingMessages(0);

      checkEmpty(queue1);
   }

   /**
    * Send some messages, don't acknowledge them and verify that they are re-sent on recovery.
    */
   @Test
   public void testClientAcknowledgeNoAcknowledgement() throws Exception {
      Connection conn = createConnection();

      Session producerSess = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);
      MessageProducer producer = producerSess.createProducer(queue1);

      Session consumerSess = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);
      MessageConsumer consumer = consumerSess.createConsumer(queue1);
      conn.start();

      final int NUM_MESSAGES = 20;

      // Send some messages
      for (int i = 0; i < NUM_MESSAGES; i++) {
         Message m = producerSess.createMessage();
         producer.send(m);
      }

      assertRemainingMessages(NUM_MESSAGES);

      logger.trace("Sent messages");

      int count = 0;
      while (true) {
         Message m = consumer.receive(1000);
         if (m == null) {
            break;
         }
         count++;
      }

      assertRemainingMessages(NUM_MESSAGES);

      logger.trace("Received {} messages", count);

      ProxyAssertSupport.assertEquals(count, NUM_MESSAGES);

      consumerSess.recover();

      assertRemainingMessages(NUM_MESSAGES);

      logger.trace("Session recover called");

      Message m = null;

      int i = 0;
      for (; i < NUM_MESSAGES; i++) {
         m = consumer.receive();
         logger.trace("Received message {}", i);

      }

      assertRemainingMessages(NUM_MESSAGES);

      // if I don't receive enough messages, the test will timeout

      logger.trace("Received {} messages after recover", i);

      m.acknowledge();

      assertRemainingMessages(0);

      // make sure I don't receive anything else

      checkEmpty(queue1);

      conn.close();
   }

   /**
    * Send some messages, acknowledge them individually and verify they are not resent after recovery.
    */
   @Test
   public void testIndividualClientAcknowledge() throws Exception {
      Connection conn = createConnection();

      Session producerSess = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);
      MessageProducer producer = producerSess.createProducer(queue1);

      Session consumerSess = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);
      MessageConsumer consumer = consumerSess.createConsumer(queue1);
      conn.start();

      final int NUM_MESSAGES = 20;

      for (int i = 0; i < NUM_MESSAGES; i++) {
         Message m = producerSess.createMessage();
         producer.send(m);
      }

      assertRemainingMessages(NUM_MESSAGES);

      for (int i = 0; i < NUM_MESSAGES; i++) {
         Message m = consumer.receive(200);

         ProxyAssertSupport.assertNotNull(m);

         assertRemainingMessages(NUM_MESSAGES - i);

         m.acknowledge();

         assertRemainingMessages(NUM_MESSAGES - (i + 1));
      }

      assertRemainingMessages(0);

      consumerSess.recover();

      Message m = consumer.receiveNoWait();
      ProxyAssertSupport.assertNull(m);
   }

   /**
    * Send some messages, acknowledge them once after all have been received verify they are not resent after recovery
    */
   @Test
   public void testBulkClientAcknowledge() throws Exception {
      Connection conn = createConnection();

      Session producerSess = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);
      MessageProducer producer = producerSess.createProducer(queue1);

      Session consumerSess = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);
      MessageConsumer consumer = consumerSess.createConsumer(queue1);
      conn.start();

      final int NUM_MESSAGES = 20;

      // Send some messages
      for (int i = 0; i < NUM_MESSAGES; i++) {
         Message m = producerSess.createMessage();
         producer.send(m);
      }

      assertRemainingMessages(NUM_MESSAGES);

      logger.trace("Sent messages");

      Message m = null;
      int count = 0;
      for (int i = 0; i < NUM_MESSAGES; i++) {
         m = consumer.receive(200);
         if (m == null) {
            break;
         }
         count++;
      }

      assertRemainingMessages(NUM_MESSAGES);

      ProxyAssertSupport.assertNotNull(m);

      m.acknowledge();

      assertRemainingMessages(0);

      logger.trace("Received {} messages", count);

      ProxyAssertSupport.assertEquals(count, NUM_MESSAGES);

      consumerSess.recover();

      logger.trace("Session recover called");

      m = consumer.receiveNoWait();

      logger.trace("Message is: {}", m);

      ProxyAssertSupport.assertNull(m);
   }

   /**
    * Send some messages, acknowledge some of them, and verify that the others are resent after delivery
    */
   @Test
   public void testPartialClientAcknowledge() throws Exception {
      Connection conn = null;
      try {
         conn = createConnection();

         Session producerSess = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         MessageProducer producer = producerSess.createProducer(queue1);

         Session consumerSess = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         MessageConsumer consumer = consumerSess.createConsumer(queue1);
         conn.start();

         final int NUM_MESSAGES = 20;
         final int ACKED_MESSAGES = 11;

         // Send some messages
         for (int i = 0; i < NUM_MESSAGES; i++) {
            Message m = producerSess.createMessage();
            producer.send(m);
         }

         assertRemainingMessages(NUM_MESSAGES);

         logger.trace("Sent messages");

         int count = 0;

         Message m = null;
         for (int i = 0; i < NUM_MESSAGES; i++) {
            m = consumer.receive(200);
            if (m == null) {
               break;
            }
            if (count == ACKED_MESSAGES - 1) {
               m.acknowledge();
            }
            count++;
         }

         assertRemainingMessages(NUM_MESSAGES - ACKED_MESSAGES);

         ProxyAssertSupport.assertNotNull(m);

         logger.trace("Received {} messages", count);

         ProxyAssertSupport.assertEquals(count, NUM_MESSAGES);

         consumerSess.recover();

         logger.trace("Session recover called");

         count = 0;
         while (true) {
            m = consumer.receive(200);
            if (m == null) {
               break;
            }
            count++;
         }

         ProxyAssertSupport.assertEquals(NUM_MESSAGES - ACKED_MESSAGES, count);
      } finally {
         if (conn != null) {
            conn.close();
         }

         removeAllMessages(queue1.getQueueName(), true);
      }
   }

   /**
    * Send some messages, consume them and verify the messages are not sent upon recovery
    */
   @Test
   public void testAutoAcknowledge() throws Exception {
      Connection conn = createConnection();

      Session producerSess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageProducer producer = producerSess.createProducer(queue1);

      Session consumerSess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageConsumer consumer = consumerSess.createConsumer(queue1);
      conn.start();

      final int NUM_MESSAGES = 20;

      // Send some messages
      for (int i = 0; i < NUM_MESSAGES; i++) {
         Message m = producerSess.createMessage();
         producer.send(m);
      }

      assertRemainingMessages(NUM_MESSAGES);

      int count = 0;

      Message m = null;
      for (int i = 0; i < NUM_MESSAGES; i++) {
         assertRemainingMessages(NUM_MESSAGES - i);

         m = consumer.receive(200);

         assertRemainingMessages(NUM_MESSAGES - (i + 1));

         if (m == null) {
            break;
         }
         count++;
      }

      assertRemainingMessages(0);

      ProxyAssertSupport.assertNotNull(m);

      logger.trace("Received {} messages", count);

      ProxyAssertSupport.assertEquals(count, NUM_MESSAGES);

      consumerSess.recover();

      logger.trace("Session recover called");

      m = consumer.receiveNoWait();

      logger.trace("Message is: {}", m);

      ProxyAssertSupport.assertNull(m);
   }

   @Test
   public void testDupsOKAcknowledgeQueue() throws Exception {
      Connection conn = createConnection();

      Session producerSess = conn.createSession(false, Session.DUPS_OK_ACKNOWLEDGE);

      MessageProducer producer = producerSess.createProducer(queue1);

      Session consumerSess = conn.createSession(false, Session.DUPS_OK_ACKNOWLEDGE);

      MessageConsumer consumer = consumerSess.createConsumer(queue1);
      conn.start();

      final int NUM_MESSAGES = 20;

      // Send some messages
      for (int i = 0; i < NUM_MESSAGES; i++) {
         Message m = producerSess.createMessage();
         producer.send(m);
      }

      assertRemainingMessages(NUM_MESSAGES);

      int count = 0;

      Message m = null;
      for (int i = 0; i < NUM_MESSAGES; i++) {
         m = consumer.receive(200);

         if (m == null) {
            break;
         }
         count++;
      }

      assertRemainingMessages(NUM_MESSAGES);

      ProxyAssertSupport.assertNotNull(m);

      logger.trace("Received {} messages", count);

      ProxyAssertSupport.assertEquals(count, NUM_MESSAGES);

      consumerSess.recover();

      logger.trace("Session recover called");

      m = consumer.receiveNoWait();

      logger.trace("Message is: {}", m);

      ProxyAssertSupport.assertNull(m);

      conn.close();

      assertRemainingMessages(0);
   }

   @Test
   public void testDupsOKAcknowledgeTopic() throws Exception {
      final int BATCH_SIZE = 10;

      deployConnectionFactory(null, "MyConnectionFactory2", -1, -1, -1, -1, false, false, BATCH_SIZE, true, "mycf");
      Connection conn = null;
      try {

         ConnectionFactory myCF = (ConnectionFactory) ic.lookup("/mycf");

         conn = myCF.createConnection();

         Session producerSess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer producer = producerSess.createProducer(ActiveMQServerTestCase.topic1);

         Session consumerSess = conn.createSession(false, Session.DUPS_OK_ACKNOWLEDGE);
         MessageConsumer consumer = consumerSess.createConsumer(ActiveMQServerTestCase.topic1);
         conn.start();

         // Send some messages
         for (int i = 0; i < 19; i++) {
            Message m = producerSess.createMessage();
            producer.send(m);
         }

         logger.trace("Sent messages");

         Message m = null;
         for (int i = 0; i < 19; i++) {
            m = consumer.receive(200);

            ProxyAssertSupport.assertNotNull(m);
         }

         consumerSess.close();
      } finally {

         if (conn != null) {
            conn.close();
         }

         ActiveMQServerTestCase.undeployConnectionFactory("MyConnectionFactory2");
      }

   }

   @Test
   public void testMessageListenerAutoAck() throws Exception {
      Connection conn = createConnection();
      Session sessSend = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageProducer prod = sessSend.createProducer(queue1);

      logger.trace("Sending messages");

      TextMessage tm1 = sessSend.createTextMessage("a");
      TextMessage tm2 = sessSend.createTextMessage("b");
      TextMessage tm3 = sessSend.createTextMessage("c");
      prod.send(tm1);
      prod.send(tm2);
      prod.send(tm3);

      logger.trace("Sent messages");

      sessSend.close();

      assertRemainingMessages(3);

      conn.start();

      Session sessReceive = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      logger.trace("Creating consumer");

      MessageConsumer cons = sessReceive.createConsumer(queue1);

      logger.trace("Created consumer");

      MessageListenerAutoAck listener = new MessageListenerAutoAck(sessReceive);

      logger.trace("Setting message listener");

      cons.setMessageListener(listener);

      logger.trace("Set message listener");

      listener.waitForMessages();

      Thread.sleep(500);

      assertRemainingMessages(0);

      ProxyAssertSupport.assertFalse(listener.failed);
   }

   /*
    * This test will: - Send two messages over a producer - Receive one message over a consumer - Call Recover - Receive
    * the second message - The queue should be empty after that Note: testMessageListenerAutoAck will test a similar
    * case using MessageListeners
    */
   @Test
   public void testRecoverAutoACK() throws Exception {
      Connection conn = createConnection();
      Session s = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageProducer p = s.createProducer(queue1);
      p.setDeliveryMode(DeliveryMode.PERSISTENT);
      Message m = s.createTextMessage("one");
      p.send(m);
      m = s.createTextMessage("two");
      p.send(m);
      conn.close();

      conn = null;

      assertRemainingMessages(2);

      conn = createConnection();

      conn.start();

      Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageConsumer consumer = session.createConsumer(queue1);

      TextMessage messageReceived = (TextMessage) consumer.receive(1000);

      ProxyAssertSupport.assertNotNull(messageReceived);

      ProxyAssertSupport.assertEquals("one", messageReceived.getText());

      session.recover();

      messageReceived = (TextMessage) consumer.receive(1000);

      ProxyAssertSupport.assertEquals("two", messageReceived.getText());

      messageReceived = (TextMessage) consumer.receiveNoWait();

      if (messageReceived != null) {
         logger.debug("Message received {}", messageReceived.getText());
      }
      assertNull(messageReceived);

      consumer.close();

      // I can't call xasession.close for this test as JCA layer would cache the session
      // So.. keep this close commented!
      // xasession.close();

      assertRemainingMessages(0);
   }

   @Test
   public void testMessageListenerDupsOK() throws Exception {
      Connection conn = createConnection();
      Session sessSend = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageProducer prod = sessSend.createProducer(queue1);

      logger.trace("Sending messages");

      TextMessage tm1 = sessSend.createTextMessage("a");
      TextMessage tm2 = sessSend.createTextMessage("b");
      TextMessage tm3 = sessSend.createTextMessage("c");
      prod.send(tm1);
      prod.send(tm2);
      prod.send(tm3);

      logger.trace("Sent messages");

      sessSend.close();

      assertRemainingMessages(3);

      conn.start();

      Session sessReceive = conn.createSession(false, Session.DUPS_OK_ACKNOWLEDGE);

      logger.trace("Creating consumer");

      MessageConsumer cons = sessReceive.createConsumer(queue1);

      logger.trace("Created consumer");

      MessageListenerDupsOK listener = new MessageListenerDupsOK(sessReceive);

      logger.trace("Setting message listener");

      cons.setMessageListener(listener);

      logger.trace("Set message listener");

      listener.waitForMessages();

      cons.close();

      assertRemainingMessages(0);

      ProxyAssertSupport.assertFalse(listener.failed);
   }

   @Test
   public void testMessageListenerClientAck() throws Exception {
      Connection conn = createConnection();
      Session sessSend = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageProducer prod = sessSend.createProducer(queue1);

      TextMessage tm1 = sessSend.createTextMessage("a");
      TextMessage tm2 = sessSend.createTextMessage("b");
      TextMessage tm3 = sessSend.createTextMessage("c");
      prod.send(tm1);
      prod.send(tm2);
      prod.send(tm3);
      sessSend.close();

      assertRemainingMessages(3);

      conn.start();
      Session sessReceive = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);
      MessageConsumer cons = sessReceive.createConsumer(queue1);
      MessageListenerClientAck listener = new MessageListenerClientAck(sessReceive);
      cons.setMessageListener(listener);

      listener.waitForMessages();

      Thread.sleep(500);

      assertRemainingMessages(0);

      conn.close();

      ProxyAssertSupport.assertFalse(listener.failed);
   }

   @Test
   public void testMessageListenerTransactionalAck() throws Exception {
      Connection conn = createConnection();
      Session sessSend = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageProducer prod = sessSend.createProducer(queue1);

      TextMessage tm1 = sessSend.createTextMessage("a");
      TextMessage tm2 = sessSend.createTextMessage("b");
      TextMessage tm3 = sessSend.createTextMessage("c");
      prod.send(tm1);
      prod.send(tm2);
      prod.send(tm3);
      sessSend.close();

      assertRemainingMessages(3);

      conn.start();
      Session sessReceive = conn.createSession(true, Session.SESSION_TRANSACTED);
      MessageConsumer cons = sessReceive.createConsumer(queue1);
      MessageListenerTransactionalAck listener = new MessageListenerTransactionalAck(sessReceive);
      cons.setMessageListener(listener);
      listener.waitForMessages();

      Thread.sleep(500);

      assertRemainingMessages(0);

      conn.close();

      ProxyAssertSupport.assertFalse(listener.failed);
   }

   private abstract class LatchListener implements MessageListener {

      protected CountDownLatch latch = new CountDownLatch(1);

      protected Session sess;

      protected int count = 0;

      boolean failed;

      LatchListener(final Session sess) {
         this.sess = sess;
      }

      public void waitForMessages() throws InterruptedException {
         ProxyAssertSupport.assertTrue("failed to receive all messages", latch.await(2000, MILLISECONDS));
      }

      @Override
      public abstract void onMessage(Message m);

   }

   private class MessageListenerAutoAck extends LatchListener {

      MessageListenerAutoAck(final Session sess) {
         super(sess);
      }

      @Override
      public void onMessage(final Message m) {
         try {
            count++;

            TextMessage tm = (TextMessage) m;

            // Receive first three messages then recover() session
            // Only last message should be redelivered
            if (count == 1) {
               assertRemainingMessages(3);

               if (!"a".equals(tm.getText())) {
                  failed = true;
                  latch.countDown();
               }
            }
            if (count == 2) {
               assertRemainingMessages(2);

               if (!"b".equals(tm.getText())) {
                  failed = true;
                  latch.countDown();
               }
            }
            if (count == 3) {
               assertRemainingMessages(1);

               if (!"c".equals(tm.getText())) {
                  failed = true;
                  latch.countDown();
               }
               sess.recover();
            }
            if (count == 4) {
               assertRemainingMessages(1);

               if (!"c".equals(tm.getText())) {
                  failed = true;
                  latch.countDown();
               }
               latch.countDown();
            }

         } catch (Exception e) {
            failed = true;
            latch.countDown();
         }
      }

   }

   private class MessageListenerDupsOK extends LatchListener {

      MessageListenerDupsOK(final Session sess) {
         super(sess);
      }

      @Override
      public void onMessage(final Message m) {
         try {
            count++;

            TextMessage tm = (TextMessage) m;

            // Receive first three messages then recover() session
            // Only last message should be redelivered
            if (count == 1) {
               assertRemainingMessages(3);

               if (!"a".equals(tm.getText())) {
                  failed = true;
                  latch.countDown();
               }
            }
            if (count == 2) {
               assertRemainingMessages(3);

               if (!"b".equals(tm.getText())) {
                  failed = true;
                  latch.countDown();
               }
            }
            if (count == 3) {
               assertRemainingMessages(3);

               if (!"c".equals(tm.getText())) {
                  failed = true;
                  latch.countDown();
               }
               sess.recover();
            }
            if (count == 4) {
               // Recover forces an ack, so there will be only one left
               assertRemainingMessages(1);

               if (!"c".equals(tm.getText())) {
                  failed = true;
                  latch.countDown();
               }
               latch.countDown();
            }

         } catch (Exception e) {
            failed = true;
            latch.countDown();
         }
      }

   }

   private class MessageListenerClientAck extends LatchListener {

      MessageListenerClientAck(final Session sess) {
         super(sess);
      }

      @Override
      public void onMessage(final Message m) {
         try {
            count++;

            TextMessage tm = (TextMessage) m;

            if (count == 1) {
               assertRemainingMessages(3);
               if (!"a".equals(tm.getText())) {
                  logger.trace("Expected a but got {}", tm.getText());
                  failed = true;
                  latch.countDown();
               }
            }
            if (count == 2) {
               assertRemainingMessages(3);
               if (!"b".equals(tm.getText())) {
                  logger.trace("Expected b but got {}", tm.getText());
                  failed = true;
                  latch.countDown();
               }
            }
            if (count == 3) {
               assertRemainingMessages(3);
               if (!"c".equals(tm.getText())) {
                  logger.trace("Expected c but got {}", tm.getText());
                  failed = true;
                  latch.countDown();
               }
               logger.trace("calling recover");
               sess.recover();
            }
            if (count == 4) {
               assertRemainingMessages(3);
               if (!"a".equals(tm.getText())) {
                  logger.trace("Expected a but got {}", tm.getText());
                  failed = true;
                  latch.countDown();
               }
               logger.trace("*** calling acknowledge");
               tm.acknowledge();
               assertRemainingMessages(2);
               logger.trace("calling recover");
               sess.recover();
            }
            if (count == 5) {
               assertRemainingMessages(2);
               if (!"b".equals(tm.getText())) {
                  logger.trace("Expected b but got {}", tm.getText());
                  failed = true;
                  latch.countDown();
               }
               logger.trace("calling recover");
               sess.recover();
            }
            if (count == 6) {
               assertRemainingMessages(2);
               if (!"b".equals(tm.getText())) {
                  logger.trace("Expected b but got {}", tm.getText());
                  failed = true;
                  latch.countDown();
               }
            }
            if (count == 7) {
               assertRemainingMessages(2);
               if (!"c".equals(tm.getText())) {
                  logger.trace("Expected c but got {}", tm.getText());
                  failed = true;
                  latch.countDown();
               }
               tm.acknowledge();
               assertRemainingMessages(0);
               latch.countDown();
            }

         } catch (Exception e) {
            logger.error("Caught exception", e);
            failed = true;
            latch.countDown();
         }
      }

   }

   private class MessageListenerTransactionalAck extends LatchListener {

      MessageListenerTransactionalAck(final Session sess) {
         super(sess);
      }

      @Override
      public void onMessage(final Message m) {
         try {
            count++;

            TextMessage tm = (TextMessage) m;

            if (count == 1) {
               assertRemainingMessages(3);
               if (!"a".equals(tm.getText())) {
                  failed = true;
                  latch.countDown();
               }
            }
            if (count == 2) {
               assertRemainingMessages(3);
               if (!"b".equals(tm.getText())) {
                  failed = true;
                  latch.countDown();
               }
            }
            if (count == 3) {
               assertRemainingMessages(3);
               if (!"c".equals(tm.getText())) {
                  failed = true;
                  latch.countDown();
               }
               logger.trace("Rollback");
               sess.rollback();
            }
            if (count == 4) {
               assertRemainingMessages(3);
               if (!"a".equals(tm.getText())) {
                  failed = true;
                  latch.countDown();
               }
            }
            if (count == 5) {
               assertRemainingMessages(3);
               if (!"b".equals(tm.getText())) {
                  failed = true;
                  latch.countDown();
               }
               logger.trace("commit");
               sess.commit();
               assertRemainingMessages(1);
            }
            if (count == 6) {
               assertRemainingMessages(1);
               if (!"c".equals(tm.getText())) {
                  failed = true;
                  latch.countDown();
               }
               logger.trace("recover");
               sess.rollback();
            }
            if (count == 7) {
               assertRemainingMessages(1);
               if (!"c".equals(tm.getText())) {
                  failed = true;
                  latch.countDown();
               }
               logger.trace("Commit");
               sess.commit();
               assertRemainingMessages(0);
               latch.countDown();
            }
         } catch (Exception e) {
            // logger.error(e.getMessage(), e);
            failed = true;
            latch.countDown();
         }
      }

   }

   @Test
   public void testTransactionalIgnoreACK() throws Exception {
      Connection conn = createConnection();

      Session producerSess = conn.createSession(true, Session.SESSION_TRANSACTED);
      MessageProducer producer = producerSess.createProducer(queue1);

      Session consumerSess = conn.createSession(true, Session.SESSION_TRANSACTED);
      MessageConsumer consumer = consumerSess.createConsumer(queue1);
      conn.start();

      final int NUM_MESSAGES = 20;

      // Send some messages
      for (int i = 0; i < NUM_MESSAGES; i++) {
         Message m = producerSess.createMessage();
         m.acknowledge(); // This is invalid but should be ignored accordingly to the javadoc
         producer.send(m);
      }

      assertRemainingMessages(0);

      producerSess.rollback();

      // Send some messages
      for (int i = 0; i < NUM_MESSAGES; i++) {
         Message m = producerSess.createMessage();
         m.acknowledge(); // / should be ignored
         producer.send(m);
      }
      assertRemainingMessages(0);

      producerSess.commit();

      assertRemainingMessages(NUM_MESSAGES);

      int count = 0;
      while (true) {
         Message m = consumer.receive(200);
         if (m == null) {
            break;
         }
         m.acknowledge();
         count++;
      }

      assertRemainingMessages(NUM_MESSAGES);

      ProxyAssertSupport.assertEquals(count, NUM_MESSAGES);

      consumerSess.rollback();

      assertRemainingMessages(NUM_MESSAGES);

      int i = 0;
      for (; i < NUM_MESSAGES; i++) {
         consumer.receive();
      }

      assertRemainingMessages(NUM_MESSAGES);

      // if I don't receive enough messages, the test will timeout

      consumerSess.commit();

      assertRemainingMessages(0);

      checkEmpty(queue1);
   }

   /**
    * Ensure no blocking calls in acknowledge flow when block on acknowledge = false. This is done by checking the
    * performance compared to blocking is much improved.
    */
   @Test
   public void testNonBlockingAckPerf() throws Exception {
      ConnectionFactory cf1 = ActiveMQJMSClient.createConnectionFactory("tcp://127.0.0.1:61616?blockOnNonDurableSend=true&blockOnAcknowledge=false", "testsuitecf1");
      ConnectionFactory cf2 = ActiveMQJMSClient.createConnectionFactory("tcp://127.0.0.1:61616?blockOnNonDurableSend=true&blockOnAcknowledge=true", "testsuitecf2");

      int messageCount = 100;

      long sendT1 = send(cf1, queue3, messageCount);
      long sendT2 = send(cf2, queue4, messageCount);

      long time1 = consume(cf1, queue3, messageCount);
      long time2 = consume(cf2, queue4, messageCount);

      logger.debug("BlockOnAcknowledge=false MessageCount={} TimeToConsume={}", messageCount, time1);
      logger.debug("BlockOnAcknowledge=true MessageCount={} TimeToConsume={}", messageCount, time2);

      assertTrue(time1 < (time2 / 2));

   }

   private long send(ConnectionFactory connectionFactory, Destination destination, int messageCount) throws JMSException {
      try (Connection connection = connectionFactory.createConnection()) {
         connection.start();
         try (Session session = connection.createSession(true, Session.CLIENT_ACKNOWLEDGE)) {
            MessageProducer producer = session.createProducer(destination);
            Message m = session.createTextMessage("testing123");
            long start = System.nanoTime();
            for (int i = 0; i < messageCount; i++) {
               producer.send(m);
            }
            session.commit();
            long end = System.nanoTime();
            return end - start;
         }
      }
   }

   private long consume(ConnectionFactory connectionFactory, Destination destination, int messageCount) throws JMSException {
      try (Connection connection = connectionFactory.createConnection()) {
         connection.start();
         try (Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE)) {
            MessageConsumer consumer = session.createConsumer(destination);
            long start = System.nanoTime();
            for (int i = 0; i < messageCount; i++) {
               Message message = consumer.receive(100);
               if (message != null) {
                  message.acknowledge();
               }
            }
            long end = System.nanoTime();
            return end - start;
         }
      }
   }
}
