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

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.InvalidDestinationException;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.QueueReceiver;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicConnection;
import javax.jms.TopicSession;
import javax.jms.TopicSubscriber;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.jms.tests.util.ProxyAssertSupport;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class MessageConsumerTest extends JMSTestCase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());


   @Test
   public void testReceiveWithClientAckThenCloseSession() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();

         Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer prod = sess.createProducer(queue1);

         final int NUM_MESSAGES = 5;

         for (int i = 0; i < NUM_MESSAGES; i++) {
            TextMessage tm = sess.createTextMessage("message" + i);

            prod.send(tm);
         }

         Session sess2 = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         MessageConsumer cons = sess2.createConsumer(queue1);

         conn.start();

         for (int i = 0; i < NUM_MESSAGES; i++) {
            TextMessage tm = (TextMessage) cons.receive(500);

            ProxyAssertSupport.assertNotNull(tm);

            ProxyAssertSupport.assertEquals("message" + i, tm.getText());
         }

         // Now close the session

         sess2.close();
      } finally {
         if (conn != null) {
            conn.close();
         }

         removeAllMessages(queue1.getQueueName(), true);
      }
   }

   @Test
   public void testRelayMessage() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();

         conn.start();

         final Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         final MessageProducer prod = sess.createProducer(queue2);

         MessageConsumer cons = sess.createConsumer(queue1);

         final int numMessages = 100;

         class MyListener implements MessageListener {

            boolean failed;

            int count;

            @Override
            public synchronized void onMessage(final Message m) {
               try {
                  prod.send(m);

                  count++;

                  if (count == numMessages) {
                     notify();
                  }
               } catch (JMSException e) {
                  failed = true;
               }
            }

            synchronized void waitForMessages() throws Exception {
               while (count < numMessages) {
                  this.wait();
               }
            }
         }

         MyListener listener = new MyListener();

         cons.setMessageListener(listener);

         final Session sess2 = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer prod2 = sess2.createProducer(queue1);

         for (int i = 0; i < numMessages; i++) {
            prod2.send(sess2.createMessage());
         }

         listener.waitForMessages();

         conn.close();

         ProxyAssertSupport.assertFalse(listener.failed);
      } finally {
         if (conn != null) {
            conn.close();
         }

         removeAllMessages(queue2.getQueueName(), true);
      }
   }

   /*
    * If there are two competing consumers on a queue/subscription then if one closes
    * and has unacknowledged deliveries these should be cancelled but also
    * delivery should be prompted on the channel, so the other consumer gets the message since
    * it may be waiting
    * A real use case for this if there is a consumer on a queue/durable sub, which then crashes
    * and before the connection resources are cleaned up, a message is sent
    * The client is quickly brought back.
    * Eventually the first client will have it's resources cleared up so the delivery gets
    * cancelled but we need to prompt deliver() so the reconnected client gets it
    */
   @Test
   public void testRedeliveryToCompetingConsumerOnQueue() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();

         Session sessSend = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer prod = sessSend.createProducer(queue1);

         conn.start();

         Session sessConsume1 = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         MessageConsumer cons1 = sessConsume1.createConsumer(queue1);

         TextMessage tm = sessSend.createTextMessage();

         tm.setText("Your mum");

         prod.send(tm);

         TextMessage tm2 = (TextMessage) cons1.receive();

         ProxyAssertSupport.assertNotNull(tm2);

         ProxyAssertSupport.assertEquals("Your mum", tm2.getText());

         // Don't ack

         // Create another consumer

         Session sessConsume2 = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         MessageConsumer cons2 = sessConsume2.createConsumer(queue1);

         // this should cancel message and cause delivery to other consumer
         sessConsume1.close();

         TextMessage tm3 = (TextMessage) cons2.receive(1000);

         ProxyAssertSupport.assertNotNull(tm3);

         ProxyAssertSupport.assertEquals("Your mum", tm3.getText());

         tm3.acknowledge();
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   /**
    * The simplest possible receive() test for a non-persistent message.
    */
   @Test
   public void testReceive() throws Exception {
      Connection producerConnection = null;

      Connection consumerConnection = null;

      try {
         producerConnection = createConnection();

         consumerConnection = createConnection();

         Session producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Session consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer queueProducer = producerSession.createProducer(queue1);

         MessageConsumer queueConsumer = consumerSession.createConsumer(queue1);

         // start consumer connection before the message is submitted
         consumerConnection.start();

         TextMessage tm = producerSession.createTextMessage("someText");

         queueProducer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

         queueProducer.send(tm);

         TextMessage m = (TextMessage) queueConsumer.receive();

         ProxyAssertSupport.assertEquals(tm.getText(), m.getText());
      } finally {
         if (producerConnection != null) {
            producerConnection.close();
         }
         if (consumerConnection != null) {
            consumerConnection.close();
         }
      }

   }

   @Test
   public void testReceivePersistent() throws Exception {
      Connection producerConnection = null;

      Connection consumerConnection = null;

      try {
         producerConnection = createConnection();

         consumerConnection = createConnection();

         Session producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Session consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer queueProducer = producerSession.createProducer(queue1);

         MessageConsumer queueConsumer = consumerSession.createConsumer(queue1);

         // start consumer connection before the message is submitted
         consumerConnection.start();

         TextMessage tm = producerSession.createTextMessage("someText");

         ProxyAssertSupport.assertEquals(DeliveryMode.PERSISTENT, tm.getJMSDeliveryMode());

         queueProducer.send(tm);

         TextMessage m = (TextMessage) queueConsumer.receive();

         ProxyAssertSupport.assertEquals(tm.getText(), m.getText());
      } finally {
         if (producerConnection != null) {
            producerConnection.close();
         }
         if (consumerConnection != null) {
            consumerConnection.close();
         }
      }
   }

   /**
    * The simplest possible receive(timeout) test.
    */
   @Test
   public void testReceiveTimeout() throws Exception {
      Connection producerConnection = null;

      Connection consumerConnection = null;

      try {
         producerConnection = createConnection();

         consumerConnection = createConnection();

         Session producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Session consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer queueProducer = producerSession.createProducer(queue1);

         MessageConsumer queueConsumer = consumerSession.createConsumer(queue1);

         TextMessage tm = producerSession.createTextMessage("someText");

         queueProducer.send(tm);

         // start consumer connection after the message is submitted
         consumerConnection.start();

         TextMessage m = (TextMessage) queueConsumer.receive(2000);

         ProxyAssertSupport.assertEquals(tm.getText(), m.getText());
      } finally {
         if (producerConnection != null) {
            producerConnection.close();
         }
         if (consumerConnection != null) {
            consumerConnection.close();
         }
      }
   }

   /**
    * The simplest possible receiveNoWait() test.
    */
   @Test
   public void testReceiveNoWait() throws Exception {
      Connection producerConnection = null;

      Connection consumerConnection = null;

      try {
         producerConnection = createConnection();

         consumerConnection = createConnection();

         Session producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Session consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer queueProducer = producerSession.createProducer(queue1);

         MessageConsumer queueConsumer = consumerSession.createConsumer(queue1);

         TextMessage tm = producerSession.createTextMessage("someText");

         queueProducer.send(tm);

         // start consumer connection after the message is submitted
         consumerConnection.start();

         // NOTE! There semantics of receiveNoWait do not guarantee the message is available
         // immediately after the message is sent
         // It will be available some indeterminate time later.
         // This is fine and as per spec.
         // To implement receiveNoWait otherwise would be very costly
         // Also other messaging systems e.g. Sun, Apache Artemis implement it this way

         Thread.sleep(500);

         TextMessage m = (TextMessage) queueConsumer.receiveNoWait();

         ProxyAssertSupport.assertEquals(tm.getText(), m.getText());
      } finally {
         if (producerConnection != null) {
            producerConnection.close();
         }
         if (consumerConnection != null) {
            consumerConnection.close();
         }
      }
   }

   /**
    * The simplest possible message listener test.
    */
   @Test
   public void testReceiveOnListener() throws Exception {
      Connection producerConnection = null;

      Connection consumerConnection = null;

      try {
         producerConnection = createConnection();

         consumerConnection = createConnection();

         Session producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Session consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer queueProducer = producerSession.createProducer(queue1);

         MessageConsumer queueConsumer = consumerSession.createConsumer(queue1);

         TextMessage tm = producerSession.createTextMessage("someText");

         queueProducer.send(tm);

         MessageListenerImpl l = new MessageListenerImpl();

         queueConsumer.setMessageListener(l);

         // start consumer connection after the message is submitted
         consumerConnection.start();

         // wait for the listener to receive the message
         l.waitForMessages();

         TextMessage m = (TextMessage) l.getNextMessage();

         ProxyAssertSupport.assertEquals(tm.getText(), m.getText());
      } finally {
         if (producerConnection != null) {
            producerConnection.close();
         }
         if (consumerConnection != null) {
            consumerConnection.close();
         }
      }
   }

   //
   // Invalid destination test
   //

   @Test
   public void testCreateConsumerOnNonExistentTopic() throws Exception {
      Connection pconn = null;

      try {
         pconn = createConnection();

         Session ps = pconn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         try {
            ps.createConsumer((Topic) () -> "NoSuchTopic");
            ProxyAssertSupport.fail("should throw exception");
         } catch (InvalidDestinationException e) {
            // OK
         }
      } finally {
         if (pconn != null) {
            pconn.close();
         }
      }
   }

   @Test
   public void testCreateConsumerOnNonExistentQueue() throws Exception {
      Connection pconn = null;

      try {
         pconn = createConnection();

         Session ps = pconn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         try {
            ps.createConsumer((Queue) () -> "NoSuchQueue");
            ProxyAssertSupport.fail("should throw exception");
         } catch (InvalidDestinationException e) {
            // OK
         }
      } finally {
         if (pconn != null) {
            pconn.close();
         }
      }
   }

   //
   // closed consumer tests
   //

   /*
    * Test that an ack can be sent after the consumer that received the message has been closed.
    * Acks are scoped per session.
    */

   @Test
   public void testAckAfterConsumerClosed() throws Exception {
      Connection connSend = null;
      Connection connReceive = null;

      try {
         connSend = createConnection();

         connSend.start();

         Session sessSend = connSend.createSession(true, Session.SESSION_TRANSACTED);

         MessageProducer prod = sessSend.createProducer(queue1);

         prod.setDeliveryMode(DeliveryMode.PERSISTENT);

         Message m = sessSend.createTextMessage("hello");

         prod.send(m);

         sessSend.commit();

         connReceive = createConnection();

         connReceive.start();

         Session sessReceive = connReceive.createSession(true, Session.SESSION_TRANSACTED);

         MessageConsumer cons = sessReceive.createConsumer(queue1);

         TextMessage m2 = (TextMessage) cons.receive(1500);

         ProxyAssertSupport.assertNotNull(m2);

         ProxyAssertSupport.assertEquals("hello", m2.getText());

         // It is legal to close the consumer before committing the tx which is when
         // the acks are sent
         cons.close();

         sessReceive.commit();

         connReceive.close();

         logger.trace("Done test");

      } finally {
         if (connSend != null) {
            connSend.close();
         }
         if (connReceive != null) {
            connReceive.close();
         }
      }
   }

   @Test
   public void testClientAcknowledgmentOnClosedConsumer() throws Exception {
      Connection producerConnection = null;

      Connection consumerConnection = null;

      try {
         producerConnection = createConnection();

         consumerConnection = createConnection();

         Session producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Session consumerSession = consumerConnection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         MessageProducer queueProducer = producerSession.createProducer(queue1);

         MessageConsumer queueConsumer = consumerSession.createConsumer(queue1);

         TextMessage tm = producerSession.createTextMessage();

         tm.setText("One");

         queueProducer.send(tm);

         consumerConnection.start();

         TextMessage m = (TextMessage) queueConsumer.receive(1500);

         ProxyAssertSupport.assertEquals(m.getText(), "One");

         queueConsumer.close();

         m.acknowledge();

         try {
            queueConsumer.receive(2000);
            ProxyAssertSupport.fail("should throw exception");
         } catch (javax.jms.IllegalStateException e) {
            // OK
         }
      } finally {
         if (producerConnection != null) {
            producerConnection.close();
         }
         if (consumerConnection != null) {
            consumerConnection.close();
         }
      }
   }

   @Test
   public void testSendMessageAndCloseConsumer1() throws Exception {
      Connection producerConnection = null;

      Connection consumerConnection = null;

      try {
         producerConnection = createConnection();

         consumerConnection = createConnection();

         Session producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Session consumerSession = consumerConnection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         MessageProducer queueProducer = producerSession.createProducer(queue1);

         MessageConsumer queueConsumer = consumerSession.createConsumer(queue1);

         Message m = producerSession.createMessage();

         queueProducer.send(m);

         queueConsumer.close();

         // since no message was received, we expect the message back in the queue

         queueConsumer = consumerSession.createConsumer(queue1);

         consumerConnection.start();

         Message r = queueConsumer.receive(2000);

         ProxyAssertSupport.assertEquals(m.getJMSMessageID(), r.getJMSMessageID());
      } finally {
         if (producerConnection != null) {
            producerConnection.close();
         }
         if (consumerConnection != null) {
            consumerConnection.close();
         }
      }

      removeAllMessages(queue1.getQueueName(), true);
   }

   /**
    * Basically the same test as before, with more than one message and a slightly different way of checking the
    * messages are back in the queue.
    */
   @Test
   public void testSendMessageAndCloseConsumer2() throws Exception {
      Connection producerConnection = null;

      Connection consumerConnection = null;

      try {
         producerConnection = createConnection();

         consumerConnection = createConnection();

         Session producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Session consumerSession = consumerConnection.createSession(true, 0);

         MessageProducer queueProducer = producerSession.createProducer(queue1);

         MessageConsumer queueConsumer = consumerSession.createConsumer(queue1);

         TextMessage tm = producerSession.createTextMessage();

         tm.setText("One");

         queueProducer.send(tm);

         tm.setText("Two");

         queueProducer.send(tm);

         consumerConnection.start();

         TextMessage m = (TextMessage) queueConsumer.receive(1500);

         ProxyAssertSupport.assertEquals("One", m.getText());

         queueConsumer.close();

         consumerSession.commit();

         // I expect that "Two" is still in the queue

         MessageConsumer queueConsumer2 = consumerSession.createConsumer(queue1);

         m = (TextMessage) queueConsumer2.receive(1500);

         ProxyAssertSupport.assertNotNull(m);

         ProxyAssertSupport.assertEquals("Two", m.getText());

         consumerSession.commit();
      } finally {
         if (producerConnection != null) {
            producerConnection.close();
         }
         if (consumerConnection != null) {
            consumerConnection.close();
         }
      }
   }

   @Test
   public void testRedel0() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();
         conn.start();

         Session sess = conn.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer prod = sess.createProducer(queue1);
         TextMessage tm1 = sess.createTextMessage("a");
         TextMessage tm2 = sess.createTextMessage("b");
         TextMessage tm3 = sess.createTextMessage("c");
         prod.send(tm1);
         prod.send(tm2);
         prod.send(tm3);
         sess.commit();

         MessageConsumer cons1 = sess.createConsumer(queue1);

         TextMessage rm1 = (TextMessage) cons1.receive();
         ProxyAssertSupport.assertNotNull(rm1);
         ProxyAssertSupport.assertEquals("a", rm1.getText());

         cons1.close();

         MessageConsumer cons2 = sess.createConsumer(queue1);

         sess.commit();

         TextMessage rm2 = (TextMessage) cons2.receive(1500);
         ProxyAssertSupport.assertNotNull(rm2);
         ProxyAssertSupport.assertEquals("b", rm2.getText());

         TextMessage rm3 = (TextMessage) cons2.receive(1500);
         ProxyAssertSupport.assertNotNull(rm3);
         ProxyAssertSupport.assertEquals("c", rm3.getText());

         sess.commit();
      } finally {
         if (conn != null) {
            conn.close();
         }

         checkEmpty(queue1);

         removeAllMessages(queue1.getQueueName(), true);
      }
   }

   @Test
   public void testRedel1() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();
         conn.start();

         Session sess = conn.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer prod = sess.createProducer(queue1);
         TextMessage tm1 = sess.createTextMessage("hello1");
         TextMessage tm2 = sess.createTextMessage("hello2");
         TextMessage tm3 = sess.createTextMessage("hello3");
         prod.send(tm1);
         prod.send(tm2);
         prod.send(tm3);
         sess.commit();

         MessageConsumer cons1 = sess.createConsumer(queue1);

         TextMessage rm1 = (TextMessage) cons1.receive(1500);
         ProxyAssertSupport.assertNotNull(rm1);
         ProxyAssertSupport.assertEquals("hello1", rm1.getText());

         cons1.close();

         MessageConsumer cons2 = sess.createConsumer(queue1);

         sess.commit();

         TextMessage rm2 = (TextMessage) cons2.receive(1500);
         ProxyAssertSupport.assertNotNull(rm2);
         ProxyAssertSupport.assertEquals("hello2", rm2.getText());

         TextMessage rm3 = (TextMessage) cons2.receive(1500);
         ProxyAssertSupport.assertNotNull(rm3);
         ProxyAssertSupport.assertEquals("hello3", rm3.getText());

         sess.commit();
      } finally {
         if (conn != null) {
            conn.close();
         }

         checkEmpty(queue1);

         removeAllMessages(queue1.getQueueName(), true);
      }
   }

   @Test
   public void testRedel2() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();
         conn.start();

         Session sess = conn.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer prod = sess.createProducer(queue1);
         TextMessage tm1 = sess.createTextMessage("hello1-a");
         TextMessage tm2 = sess.createTextMessage("hello2-a");
         TextMessage tm3 = sess.createTextMessage("hello3-a");
         prod.send(tm1);
         prod.send(tm2);
         prod.send(tm3);
         sess.commit();

         MessageConsumer cons1 = sess.createConsumer(queue1);

         TextMessage rm1 = (TextMessage) cons1.receive(1500);
         ProxyAssertSupport.assertNotNull(rm1);
         ProxyAssertSupport.assertEquals("hello1-a", rm1.getText());

         cons1.close();

         sess.commit();

         MessageConsumer cons2 = sess.createConsumer(queue1);

         TextMessage rm2 = (TextMessage) cons2.receive(1500);
         ProxyAssertSupport.assertNotNull(rm2);
         ProxyAssertSupport.assertEquals("hello2-a", rm2.getText());

         TextMessage rm3 = (TextMessage) cons2.receive(1500);
         ProxyAssertSupport.assertNotNull(rm3);
         ProxyAssertSupport.assertEquals("hello3-a", rm3.getText());

         sess.commit();
      } finally {
         if (conn != null) {
            conn.close();
         }

         checkEmpty(queue1);

         removeAllMessages(queue1.getQueueName(), true);
      }
   }

   @Test
   public void testRedel3() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();
         conn.start();

         Session sess = conn.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer prod = sess.createProducer(queue1);
         TextMessage tm1 = sess.createTextMessage("hello1");
         logger.trace(tm1.getJMSMessageID());
         TextMessage tm2 = sess.createTextMessage("hello2");
         TextMessage tm3 = sess.createTextMessage("hello3");
         prod.send(tm1);
         prod.send(tm2);
         prod.send(tm3);
         sess.commit();

         MessageConsumer cons1 = sess.createConsumer(queue1);

         TextMessage rm1 = (TextMessage) cons1.receive(1500);
         ProxyAssertSupport.assertNotNull(rm1);
         ProxyAssertSupport.assertEquals("hello1", rm1.getText());
         logger.trace(rm1.getJMSMessageID());

         logger.trace("rolling back");
         // rollback should cause redelivery of messages not acked
         sess.rollback();
         logger.trace("rolled back");

         TextMessage rm2 = (TextMessage) cons1.receive(1500);
         ProxyAssertSupport.assertEquals("hello1", rm2.getText());
         logger.trace(rm1.getJMSMessageID());

         TextMessage rm3 = (TextMessage) cons1.receive(1500);
         ProxyAssertSupport.assertEquals("hello2", rm3.getText());

         TextMessage rm4 = (TextMessage) cons1.receive(1500);
         ProxyAssertSupport.assertEquals("hello3", rm4.getText());

         sess.commit();
      } finally {
         if (conn != null) {
            conn.close();
         }

         // This last step is important - there shouldn't be any more messages to receive
         checkEmpty(queue1);

         removeAllMessages(queue1.getQueueName(), true);
      }
   }

   @Test
   public void testRedel4() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();
         conn.start();

         Session sess = conn.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer prod = sess.createProducer(queue1);
         TextMessage tm1 = sess.createTextMessage("hello1");
         TextMessage tm2 = sess.createTextMessage("hello2");
         TextMessage tm3 = sess.createTextMessage("hello3");
         prod.send(tm1);
         prod.send(tm2);
         prod.send(tm3);
         sess.commit();

         MessageConsumer cons1 = sess.createConsumer(queue1);

         TextMessage rm1 = (TextMessage) cons1.receive(1500);
         ProxyAssertSupport.assertNotNull(rm1);
         ProxyAssertSupport.assertEquals("hello1", rm1.getText());

         cons1.close();

         // rollback should cause redelivery of messages

         // in this case redelivery occurs to a different receiver

         sess.rollback();

         MessageConsumer cons2 = sess.createConsumer(queue1);

         TextMessage rm2 = (TextMessage) cons2.receive(1500);
         ProxyAssertSupport.assertNotNull(rm2);
         ProxyAssertSupport.assertEquals("hello1", rm2.getText());

         TextMessage rm3 = (TextMessage) cons2.receive(1500);
         ProxyAssertSupport.assertNotNull(rm3);
         ProxyAssertSupport.assertEquals("hello2", rm3.getText());

         TextMessage rm4 = (TextMessage) cons2.receive(1500);
         ProxyAssertSupport.assertNotNull(rm4);
         ProxyAssertSupport.assertEquals("hello3", rm4.getText());

         sess.commit();
      } finally {
         if (conn != null) {
            conn.close();
         }

         // This last step is important - there shouldn't be any more messages to receive
         checkEmpty(queue1);

         removeAllMessages(queue1.getQueueName(), true);
      }
   }

   @Test
   public void testRedel5() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();
         conn.start();

         Session sess = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         MessageProducer prod = sess.createProducer(queue1);
         TextMessage tm1 = sess.createTextMessage("hello1");
         TextMessage tm2 = sess.createTextMessage("hello2");
         TextMessage tm3 = sess.createTextMessage("hello3");
         prod.send(tm1);
         prod.send(tm2);
         prod.send(tm3);

         MessageConsumer cons1 = sess.createConsumer(queue1);

         TextMessage rm1 = (TextMessage) cons1.receive(1500);
         ProxyAssertSupport.assertNotNull(rm1);
         ProxyAssertSupport.assertEquals("hello1", rm1.getText());

         // redeliver
         sess.recover();

         TextMessage rm2 = (TextMessage) cons1.receive(1500);
         ProxyAssertSupport.assertNotNull(rm2);
         ProxyAssertSupport.assertEquals("hello1", rm2.getText());

         TextMessage rm3 = (TextMessage) cons1.receive(1500);
         ProxyAssertSupport.assertNotNull(rm3);
         ProxyAssertSupport.assertEquals("hello2", rm3.getText());

         TextMessage rm4 = (TextMessage) cons1.receive(1500);
         ProxyAssertSupport.assertNotNull(rm4);
         ProxyAssertSupport.assertEquals("hello3", rm4.getText());

         rm4.acknowledge();
      } finally {
         if (conn != null) {
            conn.close();
         }

         // This last step is important - there shouldn't be any more messages to receive
         checkEmpty(queue1);

         removeAllMessages(queue1.getQueueName(), true);
      }
   }

   @Test
   public void testRedel6() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();
         conn.start();

         Session sess = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         MessageProducer prod = sess.createProducer(queue1);
         TextMessage tm1 = sess.createTextMessage("hello1");
         TextMessage tm2 = sess.createTextMessage("hello2");
         TextMessage tm3 = sess.createTextMessage("hello3");
         prod.send(tm1);
         prod.send(tm2);
         prod.send(tm3);

         MessageConsumer cons1 = sess.createConsumer(queue1);

         TextMessage rm1 = (TextMessage) cons1.receive(1500);
         ProxyAssertSupport.assertNotNull(rm1);
         ProxyAssertSupport.assertEquals("hello1", rm1.getText());

         cons1.close();

         logger.debug("sess.recover()");

         // redeliver
         sess.recover();

         MessageConsumer cons2 = sess.createConsumer(queue1);

         logger.debug("receiving ...");

         TextMessage rm2 = (TextMessage) cons2.receive(1500);
         ProxyAssertSupport.assertNotNull(rm2);
         ProxyAssertSupport.assertEquals("hello1", rm2.getText());

         TextMessage rm3 = (TextMessage) cons2.receive(1500);
         ProxyAssertSupport.assertNotNull(rm3);
         ProxyAssertSupport.assertEquals("hello2", rm3.getText());

         TextMessage rm4 = (TextMessage) cons2.receive(1500);
         ProxyAssertSupport.assertNotNull(rm4);
         ProxyAssertSupport.assertEquals("hello3", rm4.getText());

         rm4.acknowledge();
      } finally {
         if (conn != null) {
            conn.close();
         }

         // This last step is important - there shouldn't be any more messages to receive
         checkEmpty(queue1);

         removeAllMessages(queue1.getQueueName(), true);
      }
   }

   @Test
   public void testRedel7() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();
         conn.start();

         Session sess = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         MessageProducer prod = sess.createProducer(queue1);

         TextMessage tm1 = sess.createTextMessage("1");

         TextMessage tm2 = sess.createTextMessage("2");

         TextMessage tm3 = sess.createTextMessage("3");

         prod.send(tm1);
         prod.send(tm2);
         prod.send(tm3);

         MessageConsumer cons1 = sess.createConsumer(queue1);

         TextMessage r1 = (TextMessage) cons1.receive();

         ProxyAssertSupport.assertEquals(tm1.getText(), r1.getText());

         cons1.close();

         MessageConsumer cons2 = sess.createConsumer(queue1);

         TextMessage r2 = (TextMessage) cons2.receive();

         ProxyAssertSupport.assertEquals(tm2.getText(), r2.getText());

         TextMessage r3 = (TextMessage) cons2.receive();

         ProxyAssertSupport.assertEquals(tm3.getText(), r3.getText());

         r1.acknowledge();
         r2.acknowledge();
         r3.acknowledge();
      } finally {
         if (conn != null) {
            conn.close();
         }
         removeAllMessages(queue1.getQueueName(), true);
      }
   }

   @Test
   public void testRedel8() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();

         Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer prod = sess.createProducer(queue1);

         // Send 3 messages

         prod.send(sess.createTextMessage("1"));
         prod.send(sess.createTextMessage("2"));
         prod.send(sess.createTextMessage("3"));

         conn.start();

         MessageConsumer cons1 = sess.createConsumer(queue1);

         cons1.close();

         MessageConsumer cons2 = sess.createConsumer(queue1);

         Message r1 = cons2.receive();
         Message r2 = cons2.receive();
         Message r3 = cons2.receive();

         // Messages should be received?
         ProxyAssertSupport.assertNotNull(r1);
         ProxyAssertSupport.assertNotNull(r2);
         ProxyAssertSupport.assertNotNull(r3);
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   @Test
   public void testSendAndReceivePersistentDifferentConnections() throws Exception {
      Connection connSend = null;
      Connection connReceive = null;

      try {
         connSend = createConnection();

         connSend.start();

         Session sessSend = connSend.createSession(true, Session.AUTO_ACKNOWLEDGE);

         MessageProducer prod = sessSend.createProducer(null);

         prod.setDeliveryMode(DeliveryMode.PERSISTENT);

         Message m = sessSend.createTextMessage("hello");

         prod.send(queue1, m);

         sessSend.commit();

         connReceive = createConnection();

         connReceive.start();

         Session sessReceive = connReceive.createSession(true, Session.SESSION_TRANSACTED);

         MessageConsumer cons = sessReceive.createConsumer(queue1);

         TextMessage m2 = (TextMessage) cons.receive(1500);

         ProxyAssertSupport.assertNotNull(m2);

         ProxyAssertSupport.assertEquals("hello", m2.getText());

         sessReceive.commit();

         cons.close();

         connReceive.close();

         connReceive = createConnection();

         connReceive.start();

         sessReceive = connReceive.createSession(true, Session.SESSION_TRANSACTED);

         cons = sessReceive.createConsumer(queue1);
      } finally {
         if (connSend != null) {
            connSend.close();
         }
         if (connReceive != null) {
            connReceive.close();
         }

         checkEmpty(queue1);
      }
   }

   @Test
   public void testMultipleConcurrentConsumers() throws Exception {
      Connection producerConnection = null;

      Connection consumerConnection = null;

      try {
         producerConnection = createConnection();

         consumerConnection = createConnection();

         consumerConnection.start();
         Session sess1 = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session sess2 = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session sess3 = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageConsumer cons1 = sess1.createConsumer(ActiveMQServerTestCase.topic1);
         MessageConsumer cons2 = sess2.createConsumer(ActiveMQServerTestCase.topic1);
         MessageConsumer cons3 = sess3.createConsumer(ActiveMQServerTestCase.topic1);

         final int NUM_MESSAGES = 10;

         class Receiver implements Runnable {

            Receiver(final MessageConsumer c1) {
               cons = c1;
            }

            MessageConsumer cons;

            boolean failed;

            @Override
            public void run() {
               try {
                  for (int i = 0; i < NUM_MESSAGES; i++) {
                     TextMessage m = (TextMessage) cons.receive(5000);
                     if (m == null) {
                        logger.error("Didn't receive all the messages");
                        failed = true;
                        break;
                     }
                     if (!m.getText().equals("testing")) {
                        failed = true;
                     }
                  }

               } catch (Exception e) {
                  logger.error("Failed in receiving messages", e);
                  failed = true;
               }
            }
         }

         Receiver rec1 = new Receiver(cons1);
         Receiver rec2 = new Receiver(cons2);
         Receiver rec3 = new Receiver(cons3);

         Thread t1 = new Thread(rec1);
         Thread t2 = new Thread(rec2);
         Thread t3 = new Thread(rec3);

         logger.trace("Starting threads");

         t1.start();
         t2.start();
         t3.start();

         logger.trace("Sending messages to topic");

         producerConnection.start();
         Session prodSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer prod = prodSession.createProducer(ActiveMQServerTestCase.topic1);
         prod.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

         for (int i = 0; i < NUM_MESSAGES; i++) {
            Message m = prodSession.createTextMessage("testing");
            prod.send(m);
            logger.trace("Sent message to topic");
         }

         t1.join(15000);
         t2.join(15000);
         t3.join(15000);

         sess1.close();
         sess2.close();
         sess3.close();
         prodSession.close();

         ProxyAssertSupport.assertTrue(!rec1.failed);
         ProxyAssertSupport.assertTrue(!rec2.failed);
         ProxyAssertSupport.assertTrue(!rec3.failed);
      } finally {
         if (producerConnection != null) {
            producerConnection.close();
         }
         if (consumerConnection != null) {
            consumerConnection.close();
         }
      }
   }

   @Test
   public void testGetSelector() throws Exception {
      Connection consumerConnection = null;

      try {
         consumerConnection = createConnection();

         Session consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         String selector = "JMSType = 'something'";

         MessageConsumer topicConsumer = consumerSession.createConsumer(ActiveMQServerTestCase.topic1, selector);

         ProxyAssertSupport.assertEquals(selector, topicConsumer.getMessageSelector());
      } finally {
         if (consumerConnection != null) {
            consumerConnection.close();
         }
      }
   }

   @Test
   public void testGetSelectorOnClosedConsumer() throws Exception {
      Connection consumerConnection = null;

      try {
         consumerConnection = createConnection();

         Session consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageConsumer topicConsumer = consumerSession.createConsumer(ActiveMQServerTestCase.topic1);

         topicConsumer.close();

         try {
            topicConsumer.getMessageSelector();
            fail("must throw a JMS IllegalStateException");
         } catch (javax.jms.IllegalStateException e) {
            // OK
         }
      } finally {
         if (consumerConnection != null) {
            consumerConnection.close();
         }
      }
   }

   @Test
   public void testGetNoLocalOnClosedConsumer() throws Exception {
      Connection consumerConnection = null;

      try {
         consumerConnection = createConnection();
         TopicConnection tc = (TopicConnection) consumerConnection;

         TopicSession consumerSession = tc.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);

         TopicSubscriber topicConsumer = consumerSession.createSubscriber(ActiveMQServerTestCase.topic1);

         topicConsumer.close();

         try {
            topicConsumer.getNoLocal();
            fail("must throw a JMS IllegalStateException");
         } catch (javax.jms.IllegalStateException e) {
            // OK
         }
      } finally {
         if (consumerConnection != null) {
            consumerConnection.close();
         }
      }
   }

   @Test
   public void testGetTopic() throws Exception {
      Connection consumerConnection = null;

      try {
         consumerConnection = createConnection();

         Session consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageConsumer topicConsumer = consumerSession.createConsumer(ActiveMQServerTestCase.topic1);

         Topic t = ((TopicSubscriber) topicConsumer).getTopic();

         ProxyAssertSupport.assertEquals(ActiveMQServerTestCase.topic1, t);
      } finally {
         if (consumerConnection != null) {
            consumerConnection.close();
         }
      }
   }

   @Test
   public void testGetTopicOnClosedConsumer() throws Exception {
      Connection consumerConnection = null;

      try {
         consumerConnection = createConnection();

         Session consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageConsumer topicConsumer = consumerSession.createConsumer(ActiveMQServerTestCase.topic1);

         topicConsumer.close();

         try {
            ((TopicSubscriber) topicConsumer).getTopic();
            fail("must throw a JMS IllegalStateException");
         } catch (javax.jms.IllegalStateException e) {
            // OK
         }
      } finally {
         if (consumerConnection != null) {
            consumerConnection.close();
         }
      }
   }

   @Test
   public void testGetQueue() throws Exception {
      Connection consumerConnection = null;

      try {
         consumerConnection = createConnection();

         Session consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageConsumer queueConsumer = consumerSession.createConsumer(queue1);

         Queue q = ((QueueReceiver) queueConsumer).getQueue();

         ProxyAssertSupport.assertEquals(queue1, q);
      } finally {
         if (consumerConnection != null) {
            consumerConnection.close();
         }
      }
   }

   @Test
   public void testGetQueueOnClosedConsumer() throws Exception {
      Connection consumerConnection = null;

      try {
         consumerConnection = createConnection();

         Session consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageConsumer queueConsumer = consumerSession.createConsumer(queue1);

         queueConsumer.close();

         try {
            ((QueueReceiver) queueConsumer).getQueue();
            fail("must throw a JMS IllegalStateException");
         } catch (javax.jms.IllegalStateException e) {
            // OK
         }
      } finally {
         if (consumerConnection != null) {
            consumerConnection.close();
         }
      }
   }

   @Test
   public void testReceiveOnTopicTimeoutNoMessage() throws Exception {
      Connection consumerConnection = null;

      try {
         consumerConnection = createConnection();

         Session consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageConsumer topicConsumer = consumerSession.createConsumer(ActiveMQServerTestCase.topic1);

         Message m = topicConsumer.receiveNoWait();

         ProxyAssertSupport.assertNull(m);
      } finally {
         if (consumerConnection != null) {
            consumerConnection.close();
         }
      }
   }

   @Test
   public void testReceiveOnTopicConnectionStopped() throws Exception {
      Connection producerConnection = null;

      Connection consumerConnection = null;

      try {
         producerConnection = createConnection();

         consumerConnection = createConnection();

         Session producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Session consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         final MessageProducer topicProducer = producerSession.createProducer(ActiveMQServerTestCase.topic1);

         MessageConsumer topicConsumer = consumerSession.createConsumer(ActiveMQServerTestCase.topic1);

         final Message m = producerSession.createMessage();
         new Thread(() -> {
            try {
               // this is needed to make sure the main thread has enough time to block
               Thread.sleep(1000);
               topicProducer.send(m);
            } catch (Exception e) {
               logger.error(e.getMessage(), e);
            }
         }, "Producer").start();

         ProxyAssertSupport.assertNull(topicConsumer.receiveNoWait());
      } finally {
         if (producerConnection != null) {
            producerConnection.close();
         }
         if (consumerConnection != null) {
            consumerConnection.close();
         }
      }
   }

   @Test
   public void testReceiveOnTopicTimeout() throws Exception {
      Connection producerConnection = null;

      Connection consumerConnection = null;

      try {
         producerConnection = createConnection();

         consumerConnection = createConnection();

         Session producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Session consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         final MessageProducer topicProducer = producerSession.createProducer(ActiveMQServerTestCase.topic1);

         MessageConsumer topicConsumer = consumerSession.createConsumer(ActiveMQServerTestCase.topic1);

         consumerConnection.start();

         final Message m1 = producerSession.createMessage();
         new Thread(() -> {
            try {
               // this is needed to make sure the main thread has enough time to block
               Thread.sleep(1000);
               topicProducer.send(m1);
            } catch (Exception e) {
               logger.error(e.getMessage(), e);
            }
         }, "Producer").start();

         Message m2 = topicConsumer.receive(1500);
         ProxyAssertSupport.assertEquals(m1.getJMSMessageID(), m2.getJMSMessageID());
      } finally {
         if (producerConnection != null) {
            producerConnection.close();
         }
         if (consumerConnection != null) {
            consumerConnection.close();
         }
      }
   }

   @Test
   public void testReceiveOnTopic() throws Exception {
      Connection producerConnection = null;

      Connection consumerConnection = null;

      try {
         producerConnection = createConnection();

         consumerConnection = createConnection();

         Session producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Session consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         final MessageProducer topicProducer = producerSession.createProducer(ActiveMQServerTestCase.topic1);

         MessageConsumer topicConsumer = consumerSession.createConsumer(ActiveMQServerTestCase.topic1);

         consumerConnection.start();

         final Message m1 = producerSession.createMessage();
         new Thread(() -> {
            try {
               // this is needed to make sure the main thread has enough time to block
               Thread.sleep(1000);
               topicProducer.send(m1);
            } catch (Exception e) {
               logger.error(e.getMessage(), e);
            }
         }, "Producer").start();

         Message m2 = topicConsumer.receive(3000);

         ProxyAssertSupport.assertEquals(m1.getJMSMessageID(), m2.getJMSMessageID());
      } finally {
         if (producerConnection != null) {
            producerConnection.close();
         }
         if (consumerConnection != null) {
            consumerConnection.close();
         }
      }
   }

   @Test
   public void testReceiveNoWaitOnTopic() throws Exception {
      Connection producerConnection = null;

      Connection consumerConnection = null;

      try {
         producerConnection = createConnection();

         consumerConnection = createConnection();

         Session producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Session consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         final MessageProducer topicProducer = producerSession.createProducer(ActiveMQServerTestCase.topic1);

         MessageConsumer topicConsumer = consumerSession.createConsumer(ActiveMQServerTestCase.topic1);

         consumerConnection.start();

         Message m = topicConsumer.receiveNoWait();

         ProxyAssertSupport.assertNull(m);

         Message m1 = producerSession.createMessage();
         topicProducer.send(m1);

         // block this thread for a while to allow ServerConsumerDelegate's delivery thread to kick in
         Thread.sleep(500);

         m = topicConsumer.receiveNoWait();

         ProxyAssertSupport.assertEquals(m1.getJMSMessageID(), m.getJMSMessageID());
      } finally {
         if (producerConnection != null) {
            producerConnection.close();
         }
         if (consumerConnection != null) {
            consumerConnection.close();
         }
      }
   }

   /**
    * The test sends a burst of messages and verifies if the consumer receives all of them.
    */
   @Test
   public void testStressReceiveOnQueue() throws Exception {
      Connection producerConnection = null;

      Connection consumerConnection = null;

      try {
         producerConnection = createConnection();

         consumerConnection = createConnection();

         final Session producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Session consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         final MessageProducer queueProducer = producerSession.createProducer(queue1);

         MessageConsumer queueConsumer = consumerSession.createConsumer(queue1);

         final int count = 100;

         consumerConnection.start();

         new Thread(() -> {
            try {
               for (int i = 0; i < count; i++) {
                  Message m = producerSession.createMessage();
                  queueProducer.send(m);
               }
            } catch (Exception e) {
               logger.error(e.getMessage(), e);
            }
         }, "ProducerTestThread").start();

         for (int i = 0; i < count; i++) {
            Message m = queueConsumer.receive(1500);
            ProxyAssertSupport.assertNotNull(m);
         }
      } finally {
         if (producerConnection != null) {
            producerConnection.close();
         }
         if (consumerConnection != null) {
            consumerConnection.close();
         }

         checkEmpty(queue1);
      }
   }

   /**
    * The test sends a burst of messages and verifies if the consumer receives all of them.
    */
   @Test
   public void testStressReceiveOnTopic() throws Exception {
      Connection producerConnection = null;

      Connection consumerConnection = null;

      try {
         producerConnection = createConnection();

         consumerConnection = createConnection();

         final Session producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Session consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         final MessageProducer topicProducer = producerSession.createProducer(ActiveMQServerTestCase.topic1);

         MessageConsumer topicConsumer = consumerSession.createConsumer(ActiveMQServerTestCase.topic1);

         final int count = 1000;

         consumerConnection.start();

         new Thread(() -> {
            try {
               // this is needed to make sure the main thread has enough time to block
               Thread.sleep(1000);

               for (int i = 0; i < count; i++) {
                  Message m = producerSession.createMessage();
                  topicProducer.send(m);
               }
            } catch (Exception e) {
               logger.error(e.getMessage(), e);
            }
         }, "ProducerTestThread").start();

         for (int i = 0; i < count; i++) {
            Message m = topicConsumer.receive(10000);
            ProxyAssertSupport.assertNotNull(m);
         }

         checkEmpty(ActiveMQServerTestCase.topic1);
      } finally {
         if (producerConnection != null) {
            producerConnection.close();
         }
         if (consumerConnection != null) {
            consumerConnection.close();
         }
      }
   }

   @Test
   public void testReceiveOnClose() throws Exception {
      Connection consumerConnection = null;

      try {
         consumerConnection = createConnection();

         Session consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         final MessageConsumer topicConsumer = consumerSession.createConsumer(ActiveMQServerTestCase.topic1);

         consumerConnection.start();
         final CountDownLatch latch = new CountDownLatch(1);
         Thread closerThread = new Thread(() -> {
            try {
               // this is needed to make sure the main thread has enough time to block
               Thread.sleep(1000);
               topicConsumer.close();
            } catch (Exception e) {
               logger.error(e.getMessage(), e);
            } finally {
               latch.countDown();
            }
         }, "closing thread");
         closerThread.start();

         ProxyAssertSupport.assertNull(topicConsumer.receiveNoWait());

         // wait for the closing thread to finish
         boolean closed = latch.await(5000, TimeUnit.MILLISECONDS);
         ProxyAssertSupport.assertTrue(closed);

      } finally {
         if (consumerConnection != null) {
            consumerConnection.close();
         }
      }
   }

   @AfterEach
   @Override
   public void tearDown() throws Exception {
      super.tearDown();
   }

   /**
    * to be used by testTimeoutReceiveOnClose
    */
   private class ThreadCloser extends Thread {

      final Object waitMonitor;

      long timeToSleep;

      MessageConsumer topicConsumer;

      final AtomicBoolean running;

      private ThreadCloser(final AtomicBoolean running,
                           final Object waitMonitor,
                           final long timeToSleep,
                           final MessageConsumer topicConsumer) {
         this.running = running;
         this.waitMonitor = waitMonitor;
         this.timeToSleep = timeToSleep;
         this.topicConsumer = topicConsumer;
      }

      @Override
      public void run() {
         try {
            synchronized (waitMonitor) {
               while (running.get()) {
                  waitMonitor.wait();
               }
            }

            Thread.sleep(timeToSleep);
            topicConsumer.close();
         } catch (Exception e) {
            logger.error(e.getMessage(), e);
            e.printStackTrace();
         }
      }
   }

   /**
    * to be used by testTimeoutReceiveOnClose
    */
   private class ThreadReceiver extends Thread {

      long timeToWait;

      final Object waitMonitor;

      long t1;

      long t2;

      Object receivedObject;

      MessageConsumer topicConsumer;

      final AtomicBoolean running;

      private ThreadReceiver(final AtomicBoolean running,
                             final Object waitMonitor,
                             final long timeToWait,
                             final MessageConsumer topicConsumer) {
         this.running = running;
         this.waitMonitor = waitMonitor;
         this.timeToWait = timeToWait;
         this.topicConsumer = topicConsumer;
      }

      @Override
      public void run() {
         try {
            synchronized (waitMonitor) {
               while (running.get()) {
                  waitMonitor.wait();
               }
            }
            t1 = System.currentTimeMillis();
            receivedObject = topicConsumer.receive(timeToWait);
            t2 = System.currentTimeMillis();

         } catch (Exception e) {
            logger.error(e.getMessage(), e);
            e.printStackTrace();
         }
      }
   }

   @Test
   public void testTimeoutReceiveOnClose() throws Exception {
      Connection consumerConnection = null;

      try {
         consumerConnection = createConnection();

         Session consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageConsumer topicConsumer = consumerSession.createConsumer(ActiveMQServerTestCase.topic1);

         // This is a really weird test - the received object is always going to be null since no message is sent!!

         logger.trace("testTimeoutReceiveOnClose");

         Object monitor = new Object();
         ThreadCloser closer = null;
         ThreadReceiver receiver = null;

         AtomicBoolean running = new AtomicBoolean(true);

         closer = new ThreadCloser(running, monitor, 1000, topicConsumer);
         receiver = new ThreadReceiver(running, monitor, 2000, topicConsumer);
         closer.start();
         receiver.start();
         Thread.sleep(2000);
         synchronized (monitor) {
            running.set(false);
            monitor.notifyAll();
         }
         closer.join();
         receiver.join();

         ProxyAssertSupport.assertNull(receiver.receivedObject);

         // We need to make sure the
         ProxyAssertSupport.assertTrue("Receive was supposed to receive a notification before 2 seconds", receiver.t2 - receiver.t1 <= 1500);
      } finally {
         if (consumerConnection != null) {
            consumerConnection.close();
         }
      }
   }

   //
   // MessageListener tests
   //

   @Test
   public void testMessageListenerOnTopic() throws Exception {
      Connection producerConnection = null;

      Connection consumerConnection = null;

      try {
         producerConnection = createConnection();

         consumerConnection = createConnection();

         Session producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Session consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer topicProducer = producerSession.createProducer(ActiveMQServerTestCase.topic1);

         MessageConsumer topicConsumer = consumerSession.createConsumer(ActiveMQServerTestCase.topic1);

         MessageListenerImpl l = new MessageListenerImpl();
         topicConsumer.setMessageListener(l);

         consumerConnection.start();

         Message m1 = producerSession.createMessage();
         topicProducer.send(m1);

         // block the current thread until the listener gets something; this is to avoid closing
         // the connection too early
         l.waitForMessages();

         ProxyAssertSupport.assertEquals(m1.getJMSMessageID(), l.getNextMessage().getJMSMessageID());
      } finally {
         if (producerConnection != null) {
            producerConnection.close();
         }
         if (consumerConnection != null) {
            consumerConnection.close();
         }
      }
   }

   @Test
   public void testMessageListenerOnTopicMultipleMessages() throws Exception {
      Connection producerConnection = null;

      Connection consumerConnection = null;

      try {
         producerConnection = createConnection();

         consumerConnection = createConnection();

         Session producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Session consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer topicProducer = producerSession.createProducer(ActiveMQServerTestCase.topic1);

         MessageConsumer topicConsumer = consumerSession.createConsumer(ActiveMQServerTestCase.topic1);

         logger.debug("testMessageListenerOnTopicMultipleMessages");

         int NUM_MESSAGES = 10;

         MessageListenerImpl l = new MessageListenerImpl(NUM_MESSAGES);
         topicConsumer.setMessageListener(l);

         consumerConnection.start();

         for (int i = 0; i < NUM_MESSAGES; i++) {
            TextMessage m = producerSession.createTextMessage("body" + i);
            topicProducer.send(m);
         }

         l.waitForMessages();

         int counter = 0;
         for (Iterator<Message> i = l.getMessages().iterator(); i.hasNext(); counter++) {
            TextMessage m = (TextMessage) i.next();
            ProxyAssertSupport.assertEquals("body" + counter, m.getText());
         }

         logger.debug("testMessageListenerOnTopicMultipleMessages done");

      } finally {
         if (producerConnection != null) {
            producerConnection.close();
         }
         if (consumerConnection != null) {
            consumerConnection.close();
         }
      }
   }

   @Test
   public void testMessageListenerOnQueueMultipleMessages() throws Exception {

      Connection producerConnection = null;

      Connection consumerConnection = null;

      try {
         producerConnection = createConnection();

         consumerConnection = createConnection();

         Session producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Session consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer queueProducer = producerSession.createProducer(queue1);

         MessageConsumer queueConsumer = consumerSession.createConsumer(queue1);
         logger.debug("testMessageListenerOnQueueMultipleMessages");

         int NUM_MESSAGES = 10;

         MessageListenerImpl l = new MessageListenerImpl(NUM_MESSAGES);
         queueConsumer.setMessageListener(l);

         consumerConnection.start();

         for (int i = 0; i < NUM_MESSAGES; i++) {
            TextMessage m = producerSession.createTextMessage("body" + i);
            queueProducer.send(m);
         }

         l.waitForMessages();

         int counter = 0;
         for (Iterator<Message> i = l.getMessages().iterator(); i.hasNext(); counter++) {
            TextMessage m = (TextMessage) i.next();
            ProxyAssertSupport.assertEquals("body" + counter, m.getText());
         }

         logger.debug("testMessageListenerOnTopicMultipleMessages done");

      } finally {
         if (producerConnection != null) {
            producerConnection.close();
         }
         if (consumerConnection != null) {
            consumerConnection.close();
         }
      }
   }

   @Test
   public void testSetMessageListenerTwice() throws Exception {
      Connection producerConnection = null;

      Connection consumerConnection = null;

      try {
         producerConnection = createConnection();

         consumerConnection = createConnection();

         Session producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Session consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer topicProducer = producerSession.createProducer(ActiveMQServerTestCase.topic1);

         MessageConsumer topicConsumer = consumerSession.createConsumer(ActiveMQServerTestCase.topic1);

         MessageListenerImpl listener1 = new MessageListenerImpl();

         topicConsumer.setMessageListener(listener1);

         MessageListenerImpl listener2 = new MessageListenerImpl();

         topicConsumer.setMessageListener(listener2);

         consumerConnection.start();

         Message m1 = producerSession.createMessage();
         topicProducer.send(m1);

         // block the current thread until the listener gets something; this is to avoid closing
         // connection too early

         listener2.waitForMessages();

         ProxyAssertSupport.assertEquals(m1.getJMSMessageID(), listener2.getNextMessage().getJMSMessageID());
         ProxyAssertSupport.assertEquals(0, listener1.size());
      } finally {
         if (producerConnection != null) {
            producerConnection.close();
         }
         if (consumerConnection != null) {
            consumerConnection.close();
         }
      }
   }

   @Test
   public void testSetMessageListenerWhileReceiving() throws Exception {
      Connection consumerConnection = null;

      try {
         consumerConnection = createConnection();

         Session consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         final MessageConsumer topicConsumer = consumerSession.createConsumer(ActiveMQServerTestCase.topic1);

         consumerConnection.start();
         Thread worker1 = new Thread(() -> {
            try {
               topicConsumer.receive(3000);
            } catch (Exception e) {
               e.printStackTrace();
            }
         }, "Receiver");

         worker1.start();

         Thread.sleep(1000);

         try {
            topicConsumer.setMessageListener(new MessageListenerImpl());
            ProxyAssertSupport.fail("should have thrown JMSException");
         } catch (JMSException e) {
            // ok
            logger.trace(e.getMessage());
         }
      } finally {
         if (consumerConnection != null) {
            consumerConnection.close();
         }
      }
   }

   @Test
   public void testStopConnectionDuringOnMessage() throws Exception {
      logger.trace("testStopConnectionWhileOnMessageIsExecuting");

      final AtomicInteger messagesReceived = new AtomicInteger(0);

      CountDownLatch messagesReceivedLatch = new CountDownLatch(1);

      MessageListener myListener = message -> {
         if (messagesReceived.incrementAndGet() == 10) {
            messagesReceivedLatch.countDown();
            try {
               Thread.sleep(200L);
            } catch (InterruptedException e) {
               // Ignore
            }
         }
      };

      Connection producerConnection = null;

      Connection consumerConnection = null;

      try {
         producerConnection = createConnection();

         consumerConnection = createConnection();

         Session producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Session consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer queueProducer = producerSession.createProducer(queue1);

         MessageConsumer queueConsumer = consumerSession.createConsumer(queue1);

         queueConsumer.setMessageListener(myListener);

         logger.trace("Starting consumer connection");
         consumerConnection.start();

         final int MESSAGE_COUNT = 100;

         logger.trace("Sending the first batch of messages");

         for (int i = 0; i < MESSAGE_COUNT / 2; i++) {
            queueProducer.send(producerSession.createTextMessage("Message #" + Integer.toString(i)));
         }

         messagesReceivedLatch.await(500, TimeUnit.MILLISECONDS);

         logger.trace("Stopping consumer connection");
         consumerConnection.stop();

         int countAfterStop = messagesReceived.get();
         ProxyAssertSupport.assertTrue("Should have received some messages before stopping", countAfterStop > 0);

         logger.trace("Sending the second batch of messages");
         for (int i = MESSAGE_COUNT / 2; i < MESSAGE_COUNT; i++) {
            queueProducer.send(producerSession.createTextMessage("Message #" + Integer.toString(i)));
         }

         ProxyAssertSupport.assertFalse("Should not receive any messages after the connection has been stopped", Wait.waitFor(() -> messagesReceived.get() > countAfterStop, 2000, 1000));

         logger.trace("Restarting consumer connection");
         consumerConnection.start();

         ProxyAssertSupport.assertTrue("Should have received all messages after restarting", Wait.waitFor(() -> messagesReceived.get() == MESSAGE_COUNT, 15000, 100));
      } finally {
         if (producerConnection != null) {
            producerConnection.close();
         }
         if (consumerConnection != null) {
            consumerConnection.close();
         }
         removeAllMessages(queue1.getQueueName(), true);
      }
   }

   // Test that stop doesn't in any way break subsequent close

   @Test
   public void testCloseAfterStop() throws Exception {
      Connection producerConnection = null;

      Connection consumerConnection = null;

      try {
         producerConnection = createConnection();

         consumerConnection = createConnection();

         Session producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Session consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer queueProducer = producerSession.createProducer(queue1);

         MessageConsumer queueConsumer = consumerSession.createConsumer(queue1);

         MessageListener myListener = message -> {
            try {
               Thread.sleep(1);
            } catch (InterruptedException e) {
               // Ignore
            }
         };

         queueConsumer.setMessageListener(myListener);

         consumerConnection.start();

         for (int i = 0; i < 100; i++) {
            queueProducer.send(producerSession.createTextMessage("Message #" + Integer.toString(i)));
         }

         consumerConnection.stop();

         consumerConnection.close();

         consumerConnection = null;
      } finally {
         if (producerConnection != null) {
            producerConnection.close();
         }
         if (consumerConnection != null) {
            consumerConnection.close();
         }
         removeAllMessages(queue1.getQueueName(), true);
      }
   }

   //
   // Multiple consumers
   //

   @Test
   public void testTwoConsumersNonTransacted() throws Exception {
      Connection producerConnection = null;

      Connection consumerConnection = null;

      try {
         producerConnection = createConnection();

         Session producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer queueProducer = producerSession.createProducer(queue1);

         TextMessage tm = producerSession.createTextMessage();
         tm.setText("One");
         queueProducer.send(tm);
         tm.setText("Two");
         queueProducer.send(tm);

         // recreate the connection and receive the first message
         consumerConnection = createConnection();
         Session consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer queueConsumer = consumerSession.createConsumer(queue1);
         consumerConnection.start();

         TextMessage m = (TextMessage) queueConsumer.receive(1500);
         ProxyAssertSupport.assertEquals("One", m.getText());

         consumerConnection.close();
         consumerConnection = null;

         // recreate the connection and receive the second message
         consumerConnection = createConnection();
         consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         queueConsumer = consumerSession.createConsumer(queue1);
         consumerConnection.start();

         m = (TextMessage) queueConsumer.receive(1500);
         ProxyAssertSupport.assertEquals("Two", m.getText());
      } finally {
         if (producerConnection != null) {
            producerConnection.close();
         }
         if (consumerConnection != null) {
            consumerConnection.close();
         }
      }
   }

   @Test
   public void testTwoConsumersTransacted() throws Exception {
      Connection producerConnection = null;

      Connection consumerConnection = null;

      try {
         producerConnection = createConnection();

         Session producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer queueProducer = producerSession.createProducer(queue1);

         TextMessage tm = producerSession.createTextMessage();
         tm.setText("One");
         queueProducer.send(tm);
         tm.setText("Two");
         queueProducer.send(tm);

         // recreate the connection and receive the first message
         consumerConnection = createConnection();
         Session consumerSession = consumerConnection.createSession(true, -1);
         MessageConsumer queueConsumer = consumerSession.createConsumer(queue1);
         consumerConnection.start();

         TextMessage m = (TextMessage) queueConsumer.receive(1500);
         ProxyAssertSupport.assertEquals("One", m.getText());

         consumerSession.commit();
         consumerConnection.close();
         consumerConnection = null;

         // recreate the connection and receive the second message
         consumerConnection = createConnection();
         consumerSession = consumerConnection.createSession(true, -1);
         queueConsumer = consumerSession.createConsumer(queue1);
         consumerConnection.start();

         m = (TextMessage) queueConsumer.receive(1500);
         ProxyAssertSupport.assertEquals("Two", m.getText());
      } finally {
         if (producerConnection != null) {
            producerConnection.close();
         }
         if (consumerConnection != null) {
            consumerConnection.close();
         }
         removeAllMessages(queue1.getQueueName(), true);
      }
   }

   //
   // NoLocal
   //

   @Test
   public void testNoLocal() throws Exception {
      logger.trace("testNoLocal");

      Connection conn1 = null;
      Connection conn2 = null;

      try {
         conn1 = createConnection();
         Session sess1 = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer producer1 = sess1.createProducer(ActiveMQServerTestCase.topic1);
         producer1.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
         MessageConsumer consumer1 = sess1.createConsumer(ActiveMQServerTestCase.topic1, null, true);

         conn2 = createConnection();

         Session sess2 = conn2.createSession(false, Session.AUTO_ACKNOWLEDGE);
         ProxyAssertSupport.assertEquals(Session.AUTO_ACKNOWLEDGE, sess2.getAcknowledgeMode());
         MessageConsumer consumer2 = sess2.createConsumer(ActiveMQServerTestCase.topic1, null, true);

         // we need different session because we cannot access the same session from different
         // threads
         Session sess3 = conn2.createSession(false, Session.AUTO_ACKNOWLEDGE);
         ProxyAssertSupport.assertEquals(Session.AUTO_ACKNOWLEDGE, sess3.getAcknowledgeMode());
         MessageConsumer consumer3 = sess3.createConsumer(ActiveMQServerTestCase.topic1, null, true);

         // ClientConsumer 1 should not get the message but consumers 2 and 3 should

         conn1.start();
         conn2.start();

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
                  m = consumer.receive(1500);
               } catch (Exception e) {
                  exceptionThrown = true;
               }
            }
         }

         TestRunnable tr1 = new TestRunnable(consumer1);
         TestRunnable tr2 = new TestRunnable(consumer2);
         TestRunnable tr3 = new TestRunnable(consumer3);

         Thread t1 = new Thread(tr1);
         Thread t2 = new Thread(tr2);
         Thread t3 = new Thread(tr3);

         t1.start();
         t2.start();
         t3.start();

         Message m2 = sess1.createTextMessage("Hello");
         producer1.send(m2);

         t1.join();
         t2.join();
         t3.join();

         ProxyAssertSupport.assertTrue(!tr1.exceptionThrown);
         ProxyAssertSupport.assertTrue(!tr2.exceptionThrown);
         ProxyAssertSupport.assertTrue(!tr3.exceptionThrown);

         ProxyAssertSupport.assertNull(tr1.m);
         ProxyAssertSupport.assertNotNull(tr2.m);
         ProxyAssertSupport.assertNotNull(tr3.m);
      } finally {
         if (conn1 != null) {
            conn1.close();
         }
         if (conn2 != null) {
            conn2.close();
         }
      }
   }

   @Test
   public void testNoLocalMemoryExhaustion() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();

         Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer prod = sess.createProducer(ActiveMQServerTestCase.topic1);

         MessageConsumer cons = sess.createConsumer(ActiveMQServerTestCase.topic1, null, true);

         final int numMessages = 100;

         for (int i = 0; i < numMessages; i++) {
            prod.send(sess.createMessage());
         }

         conn.start();

         Message msg = cons.receiveNoWait();

         ProxyAssertSupport.assertNull(msg);

         checkEmpty(ActiveMQServerTestCase.topic1);
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   /*
    *
    * Also see JMS 1.1 Spec. 6.12
    */
   @Test
   public void testTopicRedelivery() throws Exception {
      Connection conn1 = null;

      try {
         conn1 = createConnection();
         conn1.start();

         // Create 2 non durable subscribers on topic

         Session sess1 = conn1.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         Session sess2 = conn1.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         MessageConsumer cons1 = sess1.createConsumer(ActiveMQServerTestCase.topic1);

         MessageConsumer cons2 = sess2.createConsumer(ActiveMQServerTestCase.topic1);

         conn1.start();

         Session sess3 = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer prod = sess3.createProducer(ActiveMQServerTestCase.topic1);
         prod.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

         TextMessage tm = sess3.createTextMessage("nurse!");
         prod.send(tm);

         TextMessage tm1 = (TextMessage) cons1.receive(1500);
         TextMessage tm2 = (TextMessage) cons2.receive(1500);

         ProxyAssertSupport.assertNotNull(tm1);
         ProxyAssertSupport.assertNotNull(tm2);
         ProxyAssertSupport.assertEquals("nurse!", tm1.getText());
         ProxyAssertSupport.assertEquals("nurse!", tm2.getText());

         // acknowledge tm1
         tm1.acknowledge();

         // tm2 has not been acknowledged
         // so should be redelivered on session.recover

         sess2.recover();

         tm2 = (TextMessage) cons2.receive(1500);
         ProxyAssertSupport.assertNotNull(tm2);
         ProxyAssertSupport.assertEquals("nurse!", tm2.getText());

         // but tm1 should not be redelivered
         tm1 = (TextMessage) cons1.receiveNoWait();
         ProxyAssertSupport.assertNull(tm1);
      } finally {
         if (conn1 != null) {
            logger.trace("closing connection");
            conn1.close();
         }
      }
   }

   /**
    * Topics shouldn't persist messages for non durable subscribers and redeliver them on reconnection even if delivery
    * mode of persistent is specified See JMS spec. sec. 6.12
    */
   @Test
   public void testNoRedeliveryOnNonDurableSubscriber() throws Exception {
      Connection conn1 = null;
      Connection conn2 = null;

      try {

         conn1 = createConnection();
         conn1.start();

         Session sess1 = conn1.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         MessageProducer prod = sess1.createProducer(null);
         prod.setDeliveryMode(DeliveryMode.PERSISTENT);

         final int NUM_MESSAGES = 1;

         MessageConsumer cons = sess1.createConsumer(ActiveMQServerTestCase.topic1);

         for (int i = 0; i < NUM_MESSAGES; i++) {
            TextMessage tm = sess1.createTextMessage("helloxyz");
            prod.send(ActiveMQServerTestCase.topic1, tm);
         }

         // receive but don't ack

         int count = 0;
         while (true) {
            TextMessage tm = (TextMessage) cons.receive(1000);
            if (tm == null) {
               break;
            }
            ProxyAssertSupport.assertEquals(tm.getText(), "helloxyz");
            count++;
         }
         ProxyAssertSupport.assertEquals(NUM_MESSAGES, count);

         conn1.close();

         conn1 = null;

         checkEmpty(ActiveMQServerTestCase.topic1);
      } finally {
         if (conn1 != null) {
            conn1.close();
         }
         if (conn2 != null) {
            conn2.close();
         }
      }
   }

   // Check messages have correct type after being resurrected from persistent storage
   @Test
   public void testPersistedMessageType() throws Exception {
      Connection theConn = null;
      Connection theOtherConn = null;

      try {
         theConn = createConnection();
         theConn.start();

         // Send some persistent messages to a queue with no receivers
         Session sessSend = theConn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer theProducer = sessSend.createProducer(queue1);
         theProducer.setDeliveryMode(DeliveryMode.PERSISTENT);

         Message m = sessSend.createMessage();
         m.setStringProperty("p1", "aardvark");

         BytesMessage bm = sessSend.createBytesMessage();
         bm.writeObject("aardvark");

         MapMessage mm = sessSend.createMapMessage();
         mm.setString("s1", "aardvark");

         ObjectMessage om = sessSend.createObjectMessage();
         om.setObject("aardvark");

         StreamMessage sm = sessSend.createStreamMessage();
         sm.writeString("aardvark");

         TextMessage tm = sessSend.createTextMessage("aardvark");

         theProducer.send(m);
         theProducer.send(bm);
         theProducer.send(mm);
         theProducer.send(om);
         theProducer.send(sm);
         theProducer.send(tm);

         theConn.close();

         theOtherConn = createConnection();
         theOtherConn.start();

         Session sessReceive = theOtherConn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageConsumer theConsumer = sessReceive.createConsumer(queue1);

         Message m2 = theConsumer.receive(1500);

         logger.trace("m2 is {}", m2);

         ProxyAssertSupport.assertNotNull(m2);

         ProxyAssertSupport.assertEquals("aardvark", m2.getStringProperty("p1"));

         BytesMessage bm2 = (BytesMessage) theConsumer.receive(1500);
         ProxyAssertSupport.assertEquals("aardvark", bm2.readUTF());

         MapMessage mm2 = (MapMessage) theConsumer.receive(1500);
         ProxyAssertSupport.assertEquals("aardvark", mm2.getString("s1"));

         ObjectMessage om2 = (ObjectMessage) theConsumer.receive(1500);
         ProxyAssertSupport.assertEquals("aardvark", (String) om2.getObject());

         StreamMessage sm2 = (StreamMessage) theConsumer.receive(1500);
         ProxyAssertSupport.assertEquals("aardvark", sm2.readString());

         TextMessage tm2 = (TextMessage) theConsumer.receive(1500);
         ProxyAssertSupport.assertEquals("aardvark", tm2.getText());
      } finally {
         if (theConn != null) {
            theConn.close();
         }
         if (theOtherConn != null) {
            theOtherConn.close();
         }
      }
   }

   @Test
   public void testDurableSubscriptionSimple() throws Exception {
      final String CLIENT_ID1 = "test-client-id1";

      Connection conn1 = null;

      try {
         conn1 = createConnection();

         conn1.setClientID(CLIENT_ID1);

         Session sess1 = conn1.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer prod = sess1.createProducer(null);
         prod.setDeliveryMode(DeliveryMode.PERSISTENT);

         MessageConsumer durable = sess1.createDurableSubscriber(ActiveMQServerTestCase.topic1, "mySubscription");

         conn1.start();

         final int NUM_MESSAGES = 50;

         for (int i = 0; i < NUM_MESSAGES; i++) {
            TextMessage tm = sess1.createTextMessage("hello");
            prod.send(ActiveMQServerTestCase.topic1, tm);
         }
         sess1.commit();

         for (int count = 0; count < NUM_MESSAGES; count++) {
            TextMessage tm = (TextMessage) durable.receive(1500);
            assertNotNull(tm);
         }

         assertNull(durable.receiveNoWait());

         sess1.commit();

         durable.close();

         sess1.unsubscribe("mySubscription");
      } finally {
         if (conn1 != null) {
            conn1.close();
         }
      }
   }

   @Test
   public void testDurableSubscriptionMultipleSubscriptions() throws Exception {
      final String CLIENT_ID1 = "test-client-id1";

      Connection conn1 = null;
      Connection conn2 = null;
      Connection conn3 = null;

      try {
         conn1 = createConnection();

         conn1.setClientID(CLIENT_ID1);

         Session sess1 = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);

         sess1.createDurableSubscriber(ActiveMQServerTestCase.topic1, "mySubscription1");

         sess1.createDurableSubscriber(ActiveMQServerTestCase.topic1, "mySubscription2");

         conn1.close();

         conn2 = createConnection();
         conn2.setClientID(CLIENT_ID1);

         Session sess2 = conn2.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer producer = sess2.createProducer(ActiveMQServerTestCase.topic1);
         producer.setDeliveryMode(DeliveryMode.PERSISTENT);

         final int NUM_MESSAGES = 50;

         for (int i = 0; i < NUM_MESSAGES; i++) {
            TextMessage tm = sess2.createTextMessage("hello");
            producer.send(tm);
         }

         sess2.unsubscribe("mySubscription1");

         conn2.close();

         conn3 = createConnection();
         conn3.setClientID(CLIENT_ID1);
         conn3.start();
         Session sess3 = conn3.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer durable3 = sess3.createDurableSubscriber(ActiveMQServerTestCase.topic1, "mySubscription2");

         for (int i = 0; i < NUM_MESSAGES; i++) {
            TextMessage tm = (TextMessage) durable3.receive(1000);
            assertNotNull(tm);
            ProxyAssertSupport.assertEquals("hello", tm.getText());
         }
         assertNull(durable3.receiveNoWait());

         logger.debug("received {} messages", NUM_MESSAGES);

         MessageConsumer durable4 = sess3.createDurableSubscriber(ActiveMQServerTestCase.topic1, "mySubscription1");

         Message m = durable4.receiveNoWait();
         ProxyAssertSupport.assertNull(m);

         durable3.close();

         sess3.unsubscribe("mySubscription2");

         durable4.close();

         sess3.unsubscribe("mySubscription1");
      } finally {
         if (conn1 != null) {
            conn1.close();
         }
         if (conn2 != null) {
            conn2.close();
         }
         if (conn3 != null) {
            conn3.close();
         }
      }
   }

   @Test
   public void testDurableSubscriptionDataRemaining() throws Exception {
      final String CLIENT_ID1 = "test-client-id1";

      Connection conn1 = null;
      Connection conn2 = null;
      Connection conn3 = null;
      Connection conn4 = null;
      Connection conn5 = null;
      Connection conn6 = null;

      Session sess1;
      Session sess3;
      Session sess4;
      Session sess6 = null;

      try {
         // Create a durable subscriber on one connection and close it
         conn1 = createConnection();
         conn1.setClientID(CLIENT_ID1);
         sess1 = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer durable = sess1.createDurableSubscriber(ActiveMQServerTestCase.topic1, "mySubscription");
         conn1.close();

         // Send some messages on another connection and close it
         conn2 = createConnection();
         conn2.setClientID(CLIENT_ID1);
         Session sess2 = conn2.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer prod2 = sess2.createProducer(null);
         prod2.setDeliveryMode(DeliveryMode.PERSISTENT);
         final int NUM_MESSAGES = 10;
         for (int i = 0; i < NUM_MESSAGES; i++) {
            TextMessage tm = sess2.createTextMessage("hello");
            prod2.send(ActiveMQServerTestCase.topic1, tm);
         }
         conn2.close();

         // Receive the messages on another connection
         conn3 = createConnection();
         conn3.setClientID(CLIENT_ID1);
         conn3.start();
         sess3 = conn3.createSession(false, Session.AUTO_ACKNOWLEDGE);
         durable = sess3.createDurableSubscriber(ActiveMQServerTestCase.topic1, "mySubscription");
         int count = 0;
         while (true) {
            TextMessage tm = (TextMessage) durable.receive(100);
            if (tm == null) {
               break;
            }
            ProxyAssertSupport.assertEquals("hello", tm.getText());
            count++;
         }
         ProxyAssertSupport.assertEquals(NUM_MESSAGES, count);
         conn3.close();

         // Try and receive them again
         conn4 = createConnection();
         conn4.setClientID(CLIENT_ID1);
         conn4.start();
         sess4 = conn4.createSession(false, Session.AUTO_ACKNOWLEDGE);
         durable = sess4.createDurableSubscriber(ActiveMQServerTestCase.topic1, "mySubscription");

         TextMessage tm = (TextMessage) durable.receiveNoWait();
         ProxyAssertSupport.assertNull(tm);
         conn4.close();

         // Send some more messages and unsubscribe
         conn5 = createConnection();
         conn5.setClientID(CLIENT_ID1);
         conn5.start();
         Session sess5 = conn5.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer prod5 = sess5.createProducer(null);
         prod5.setDeliveryMode(DeliveryMode.PERSISTENT);

         logger.debug("sending.1 {} messages", NUM_MESSAGES);
         for (int i = 0; i < NUM_MESSAGES; i++) {
            TextMessage tm2 = sess5.createTextMessage("hello");
            prod5.send(ActiveMQServerTestCase.topic1, tm2);
         }

         logger.debug("unsubscribing mySubscription");

         durable.close();
         sess5.unsubscribe("mySubscription");
         logger.debug("unsubscribing done");
         conn5.close();

         // Resubscribe with the same name
         conn6 = createConnection();
         conn6.setClientID(CLIENT_ID1);
         conn6.start();
         sess6 = conn6.createSession(false, Session.AUTO_ACKNOWLEDGE);
         durable = sess6.createDurableSubscriber(ActiveMQServerTestCase.topic1, "mySubscription");

         TextMessage tm3 = (TextMessage) durable.receiveNoWait();
         ProxyAssertSupport.assertNull(tm3);

         durable.close();
      } finally {
         if (conn1 != null) {
            conn1.close();
         }
         if (conn2 != null) {
            conn2.close();
         }
         if (conn3 != null) {
            conn3.close();
         }
         if (conn4 != null) {
            conn4.close();
         }
         if (conn5 != null) {
            conn5.close();
         }
         if (sess6 != null) {
            sess6.unsubscribe("mySubscription");
         }
         if (conn6 != null) {
            conn6.close();
         }
      }
   }

   @Test
   public void testDurableSubscriptionReconnect() throws Exception {
      final String CLIENT_ID1 = "test-client-id1";

      Connection conn1 = null;
      Connection conn2 = null;

      try {
         conn1 = createConnection();
         conn1.setClientID(CLIENT_ID1);

         Session sess1 = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer prod = sess1.createProducer(null);
         prod.setDeliveryMode(DeliveryMode.PERSISTENT);

         MessageConsumer durable = sess1.createDurableSubscriber(ActiveMQServerTestCase.topic1, "mySubscription");

         conn1.start();

         final int NUM_MESSAGES = 2;

         for (int i = 0; i < NUM_MESSAGES; i++) {
            TextMessage tm = sess1.createTextMessage("hello");
            prod.send(ActiveMQServerTestCase.topic1, tm);
         }

         final int NUM_TO_RECEIVE = NUM_MESSAGES - 1;

         for (int i = 0; i < NUM_TO_RECEIVE; i++) {
            TextMessage tm = (TextMessage) durable.receive(300);
            ProxyAssertSupport.assertNotNull(tm);
         }

         // Close the connection
         conn1.close();
         conn1 = null;

         conn2 = createConnection();

         conn2.setClientID(CLIENT_ID1);

         Session sess2 = conn2.createSession(false, Session.AUTO_ACKNOWLEDGE);

         // Re-subscribe to the subscription

         MessageConsumer durable2 = sess2.createDurableSubscriber(ActiveMQServerTestCase.topic1, "mySubscription");

         conn2.start();

         int count = 0;
         while (true) {
            TextMessage tm = (TextMessage) durable2.receive(1500);
            if (tm == null) {
               break;
            }
            count++;
         }

         ProxyAssertSupport.assertEquals(NUM_MESSAGES - NUM_TO_RECEIVE, count);

         durable2.close();

         sess2.unsubscribe("mySubscription");
      } finally {
         if (conn1 != null) {
            conn1.close();
         }
         if (conn2 != null) {
            conn2.close();
         }
      }
   }

   @Test
   public void testDurableSubscriptionReconnectDifferentClientID() throws Exception {
      final String CLIENT_ID1 = "test-client-id1";
      final String CLIENT_ID2 = "test-client-id2";

      Connection conn1 = null;
      Connection conn2 = null;

      try {
         conn1 = createConnection();

         conn1.setClientID(CLIENT_ID1);

         Session sess1 = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer prod = sess1.createProducer(null);
         prod.setDeliveryMode(DeliveryMode.PERSISTENT);

         MessageConsumer durable = sess1.createDurableSubscriber(ActiveMQServerTestCase.topic1, "mySubscription");

         conn1.start();

         final int NUM_MESSAGES = 50;

         for (int i = 0; i < NUM_MESSAGES; i++) {
            TextMessage tm = sess1.createTextMessage("hello");
            prod.send(ActiveMQServerTestCase.topic1, tm);
         }

         final int NUM_TO_RECEIVE1 = 22;

         for (int i = 0; i < NUM_TO_RECEIVE1; i++) {
            TextMessage tm = (TextMessage) durable.receive(1500);
            if (tm == null) {
               ProxyAssertSupport.fail();
            }
         }

         // Close the connection
         conn1.close();
         conn1 = null;

         conn2 = createConnection();

         conn2.setClientID(CLIENT_ID2);

         Session sess2 = conn2.createSession(false, Session.AUTO_ACKNOWLEDGE);

         // Re-subscribe to the subscription
         MessageConsumer durable2 = sess2.createDurableSubscriber(ActiveMQServerTestCase.topic1, "mySubscription");

         conn2.start();

         TextMessage tm = (TextMessage) durable2.receiveNoWait();
         ProxyAssertSupport.assertNull(tm);

         durable2.close();

         sess2.unsubscribe("mySubscription");

         // Now need to remove the original subscription

         conn1 = createConnection();

         conn1.setClientID(CLIENT_ID1);

         sess1 = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);

         sess1.unsubscribe("mySubscription");
      } finally {
         if (conn1 != null) {
            conn1.close();
         }
         if (conn2 != null) {
            conn2.close();
         }
      }
   }

   @Test
   public void testDurableSubscriptionInvalidUnsubscribe() throws Exception {
      final String CLIENT_ID1 = "test-client-id1";

      Connection conn1 = null;

      try {
         conn1 = createConnection();

         conn1.setClientID(CLIENT_ID1);

         Session sess1 = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);

         try {
            sess1.unsubscribe("non-existent subscription");
            ProxyAssertSupport.fail();
         } catch (JMSException e) {
         }
      } finally {
         if (conn1 != null) {
            conn1.close();
         }
      }
   }

   @Test
   public void testDurableSubscriptionClientIDNotSet() throws Exception {
      // Client id must be set before creating a durable subscription
      // This assumes we are not setting it in the connection factory which
      // is currently true but may change in the future

      Connection conn1 = null;

      try {

         conn1 = createConnection();

         ProxyAssertSupport.assertNull(conn1.getClientID());

         Session sess1 = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);

         try {
            sess1.createDurableSubscriber(ActiveMQServerTestCase.topic1, "mySubscription");
            ProxyAssertSupport.fail();
         } catch (JMSException e) {
         }

      } finally {
         if (conn1 != null) {
            conn1.close();
         }
      }
   }

   @Test
   public void testRedeliveredDifferentSessions() throws Exception {
      Connection producerConnection = null;

      Connection consumerConnection = null;

      try {
         producerConnection = createConnection();

         consumerConnection = createConnection();

         Session sessProducer = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer prod = sessProducer.createProducer(queue1);
         TextMessage tm = sessProducer.createTextMessage("testRedeliveredDifferentSessions");
         prod.send(tm);

         consumerConnection.start();

         Session sess1 = consumerConnection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         MessageConsumer cons1 = sess1.createConsumer(queue1);
         TextMessage tm2 = (TextMessage) cons1.receive(3000);

         ProxyAssertSupport.assertNotNull(tm2);
         ProxyAssertSupport.assertEquals("testRedeliveredDifferentSessions", tm2.getText());

         // don't acknowledge it
         sess1.close();

         Session sess2 = consumerConnection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         MessageConsumer cons2 = sess2.createConsumer(queue1);
         TextMessage tm3 = (TextMessage) cons2.receive(3000);

         ProxyAssertSupport.assertNotNull(tm3);
         ProxyAssertSupport.assertEquals("testRedeliveredDifferentSessions", tm3.getText());

         ProxyAssertSupport.assertTrue(tm3.getJMSRedelivered());
      } finally {
         if (producerConnection != null) {
            producerConnection.close();
         }
         if (consumerConnection != null) {
            consumerConnection.close();
         }
         removeAllMessages(queue1.getQueueName(), true);
      }
   }

   @Test
   public void testRedelMessageListener1() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();

         conn.start();

         Session sessProducer = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         Session sessConsumer = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         MessageConsumer cons = sessConsumer.createConsumer(queue1);

         RedelMessageListenerImpl listener = new RedelMessageListenerImpl(false);
         listener.sess = sessConsumer;

         cons.setMessageListener(listener);

         MessageProducer prod = sessProducer.createProducer(queue1);
         prod.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
         TextMessage m1 = sessProducer.createTextMessage("a");
         TextMessage m2 = sessProducer.createTextMessage("b");
         TextMessage m3 = sessProducer.createTextMessage("c");

         prod.send(m1);
         prod.send(m2);
         prod.send(m3);

         listener.waitForMessages();

         conn.close();
         conn = null;

         ProxyAssertSupport.assertFalse(listener.failed);
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   @Test
   public void testRedelMessageListener2() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();

         conn.start();

         Session sessSend = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Session sess = conn.createSession(true, Session.SESSION_TRANSACTED);

         MessageConsumer cons = sess.createConsumer(queue1);

         RedelMessageListenerImpl listener = new RedelMessageListenerImpl(true);
         listener.sess = sess;

         cons.setMessageListener(listener);

         MessageProducer prod = sessSend.createProducer(queue1);
         TextMessage m1 = sess.createTextMessage("a");
         TextMessage m2 = sess.createTextMessage("b");
         TextMessage m3 = sess.createTextMessage("c");

         prod.send(m1);
         prod.send(m2);
         prod.send(m3);

         listener.waitForMessages();

         logger.debug(listener.messageOrder);
         ProxyAssertSupport.assertFalse(listener.messageOrder, listener.failed);

         conn.close();

         conn = null;
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   @Test
   public void testExceptionMessageListener1() throws Exception {
      Connection conn = createConnection();

      conn.start();

      Session sessSend = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageConsumer cons = sess.createConsumer(queue1);

      ExceptionRedelMessageListenerImpl listener = new ExceptionRedelMessageListenerImpl(sess);

      cons.setMessageListener(listener);

      MessageProducer prod = sessSend.createProducer(queue1);
      TextMessage m1 = sess.createTextMessage("a");
      TextMessage m2 = sess.createTextMessage("b");
      TextMessage m3 = sess.createTextMessage("c");

      prod.send(m1);
      prod.send(m2);
      prod.send(m3);

      listener.waitForMessages();

      ProxyAssertSupport.assertFalse(listener.message, listener.failed);
   }

   @Test
   public void testExceptionMessageListener2() throws Exception {
      Connection conn = createConnection();

      conn.start();

      Session sessSend = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      Session sess = conn.createSession(false, Session.DUPS_OK_ACKNOWLEDGE);

      MessageConsumer cons = sess.createConsumer(queue1);

      ExceptionRedelMessageListenerImpl listener = new ExceptionRedelMessageListenerImpl(sess);

      cons.setMessageListener(listener);

      MessageProducer prod = sessSend.createProducer(queue1);
      TextMessage m1 = sess.createTextMessage("a");
      TextMessage m2 = sess.createTextMessage("b");
      TextMessage m3 = sess.createTextMessage("c");

      prod.send(m1);
      prod.send(m2);
      prod.send(m3);

      listener.waitForMessages();

      ProxyAssertSupport.assertFalse(listener.message, listener.failed);
   }

   @Test
   public void testExceptionMessageListener3() throws Exception {
      Connection conn = createConnection();

      conn.start();

      Session sessSend = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      Session sess = conn.createSession(true, Session.SESSION_TRANSACTED);

      MessageConsumer cons = sess.createConsumer(queue1);

      ExceptionRedelMessageListenerImpl listener = new ExceptionRedelMessageListenerImpl(sess);

      cons.setMessageListener(listener);

      MessageProducer prod = sessSend.createProducer(queue1);
      TextMessage m1 = sess.createTextMessage("a");
      TextMessage m2 = sess.createTextMessage("b");
      TextMessage m3 = sess.createTextMessage("c");

      prod.send(m1);
      prod.send(m2);
      prod.send(m3);

      listener.waitForMessages();

      ProxyAssertSupport.assertFalse(listener.failed);
      conn.close();
      removeAllMessages(queue1.getQueueName(), true);
   }

   @Test
   public void testExceptionMessageListener4() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();

         conn.start();

         Session sessSend = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Session sess = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         MessageConsumer cons = sess.createConsumer(queue1);

         ExceptionRedelMessageListenerImpl listener = new ExceptionRedelMessageListenerImpl(sess);

         cons.setMessageListener(listener);

         MessageProducer prod = sessSend.createProducer(queue1);
         TextMessage m1 = sess.createTextMessage("a");
         TextMessage m2 = sess.createTextMessage("b");
         TextMessage m3 = sess.createTextMessage("c");

         prod.send(m1);
         prod.send(m2);
         prod.send(m3);

         listener.waitForMessages();

         ProxyAssertSupport.assertFalse(listener.failed);

         conn.close();

         conn = null;

      } finally {
         if (conn != null) {
            conn.close();
         }

         removeAllMessages(queue1.getQueueName(), true);
      }
   }

   @Test
   public void testExceptionMessageListenerCloseConnection() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();

         conn.start();

         Session sessSend = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Session sess = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         MessageConsumer cons = sess.createConsumer(queue1);

         CountDownLatch latch = new CountDownLatch(1);
         ConnectionCloseMessageListener listener = new ConnectionCloseMessageListener(conn, latch);

         cons.setMessageListener(listener);

         conn.start();

         MessageProducer prod = sessSend.createProducer(queue1);
         TextMessage m1 = sess.createTextMessage("a");

         prod.send(m1);

         ProxyAssertSupport.assertTrue(latch.await(5, TimeUnit.SECONDS));

         ProxyAssertSupport.assertNotNull(listener.exception);

         ProxyAssertSupport.assertTrue(listener.exception instanceof javax.jms.IllegalStateException);
      } finally {
         if (conn != null) {
            conn.close();
         }

         removeAllMessages(queue1.getQueueName(), true);
      }
   }

   @Test
   public void testExceptionMessageListenerStopConnection() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();

         conn.start();

         Session sessSend = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Session sess = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         MessageConsumer cons = sess.createConsumer(queue1);

         CountDownLatch latch = new CountDownLatch(1);
         ConnectionStopMessageListener listener = new ConnectionStopMessageListener(conn, latch);

         cons.setMessageListener(listener);

         conn.start();

         MessageProducer prod = sessSend.createProducer(queue1);
         TextMessage m1 = sess.createTextMessage("a");

         prod.send(m1);

         ProxyAssertSupport.assertTrue(latch.await(5, TimeUnit.SECONDS));

         ProxyAssertSupport.assertNotNull(listener.exception);

         ProxyAssertSupport.assertTrue(listener.exception instanceof javax.jms.IllegalStateException);
      } finally {
         if (conn != null) {
            conn.close();
         }

         removeAllMessages(queue1.getQueueName(), true);
      }
   }

   @Test
   public void testExceptionMessageListenerStopSession() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();

         conn.start();

         Session sessSend = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Session sess = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         MessageConsumer cons = sess.createConsumer(queue1);

         CountDownLatch latch = new CountDownLatch(1);
         SessionCloseMessageListener listener = new SessionCloseMessageListener(sess, latch);

         cons.setMessageListener(listener);

         conn.start();

         MessageProducer prod = sessSend.createProducer(queue1);
         TextMessage m1 = sess.createTextMessage("a");

         prod.send(m1);

         ProxyAssertSupport.assertTrue(latch.await(5, TimeUnit.SECONDS));

         ProxyAssertSupport.assertNotNull(listener.exception);

         ProxyAssertSupport.assertTrue(listener.exception instanceof javax.jms.IllegalStateException);
      } finally {
         if (conn != null) {
            conn.close();
         }

         removeAllMessages(queue1.getQueueName(), true);
      }
   }


   private class ConnectionCloseMessageListener implements MessageListener {

      private Connection conn;
      private CountDownLatch latch;
      private JMSException exception;

      private ConnectionCloseMessageListener(Connection conn, CountDownLatch latch) {
         this.conn = conn;
         this.latch = latch;
      }

      @Override
      public void onMessage(Message message) {
         try {
            conn.close();
         } catch (JMSException e) {
            this.exception = e;
         }
         latch.countDown();
      }
   }

   private class ConnectionStopMessageListener implements MessageListener {

      private Connection conn;
      private CountDownLatch latch;
      private JMSException exception;

      private ConnectionStopMessageListener(Connection conn, CountDownLatch latch) {
         this.conn = conn;
         this.latch = latch;
      }

      @Override
      public void onMessage(Message message) {
         try {
            conn.stop();
         } catch (JMSException e) {
            this.exception = e;
         }
         latch.countDown();
      }
   }

   private class SessionCloseMessageListener implements MessageListener {

      private Session session;
      private CountDownLatch latch;
      private JMSException exception;

      private SessionCloseMessageListener(Session session, CountDownLatch latch) {
         this.session = session;
         this.latch = latch;
      }

      @Override
      public void onMessage(Message message) {
         try {
            session.close();
         } catch (JMSException e) {
            this.exception = e;
         }
         latch.countDown();
      }
   }

   private class ExceptionRedelMessageListenerImpl implements MessageListener {

      private final CountDownLatch latch = new CountDownLatch(1);

      private int count;

      private final Session sess;

      private boolean failed;

      String message = "ok";

      private void failed(final String msg) {
         logger.warn(msg);
         failed = true;
         message = msg;
      }

      public void waitForMessages() throws InterruptedException {
         ActiveMQTestBase.waitForLatch(latch);
      }

      private ExceptionRedelMessageListenerImpl(final Session sess) {
         this.sess = sess;
      }

      @Override
      public void onMessage(final Message m) {
         TextMessage tm = (TextMessage) m;
         count++;

         try {
            if (count == 1) {
               if (!"a".equals(tm.getText())) {
                  failed("Should be a but was " + tm.getText());
                  latch.countDown();
               }
               throw new RuntimeException("Aardvark");
            } else if (count == 2) {
               if (sess.getAcknowledgeMode() == Session.AUTO_ACKNOWLEDGE || sess.getAcknowledgeMode() == Session.DUPS_OK_ACKNOWLEDGE) {
                  // Message should be immediately redelivered
                  if (!"a".equals(tm.getText())) {
                     failed("Should be a but was " + tm.getText());
                     latch.countDown();
                  }
                  if (!tm.getJMSRedelivered()) {
                     failed("Message was supposed to be a redelivery");
                     latch.countDown();
                  }
               } else {
                  // Transacted or CLIENT_ACKNOWLEDGE - next message should be delivered
                  if (!"b".equals(tm.getText())) {
                     failed("Should be b but was " + tm.getText());
                     latch.countDown();
                  }
               }
            } else if (count == 3) {
               if (sess.getAcknowledgeMode() == Session.AUTO_ACKNOWLEDGE || sess.getAcknowledgeMode() == Session.DUPS_OK_ACKNOWLEDGE) {
                  if (!"b".equals(tm.getText())) {
                     failed("Should be b but was " + tm.getText());
                     latch.countDown();
                  }
               } else {
                  if (!"c".equals(tm.getText())) {
                     failed("Should be c but was " + tm.getText());
                     latch.countDown();
                  }
                  latch.countDown();
               }
            } else if (count == 4) {
               if (sess.getAcknowledgeMode() == Session.AUTO_ACKNOWLEDGE || sess.getAcknowledgeMode() == Session.DUPS_OK_ACKNOWLEDGE) {
                  if (!"c".equals(tm.getText())) {
                     failed("Should be c but was " + tm.getText());
                     latch.countDown();
                  }
                  latch.countDown();
               } else {
                  // Shouldn't get a 4th message
                  failed("Shouldn't get a 4th message");
                  latch.countDown();
               }
            }
         } catch (JMSException e) {
            logger.error(e.getMessage(), e);
            failed("Got a JMSException " + e.toString());
            latch.countDown();
         }
      }
   }

   private class RedelMessageListenerImpl implements MessageListener {

      private Session sess;

      private int count;

      private boolean failed;

      private String messageOrder = "message sequence is: ";

      private final CountDownLatch latch = new CountDownLatch(1);

      private final boolean transacted;

      private RedelMessageListenerImpl(final boolean transacted) {
         this.transacted = transacted;
      }

      /**
       * Blocks the calling thread until at least a message is received
       */
      public void waitForMessages() throws InterruptedException {
         ActiveMQTestBase.waitForLatch(latch);
      }

      @Override
      public void onMessage(final Message m) {
         try {
            TextMessage tm = (TextMessage) m;

            messageOrder += tm.getText() + " ";
            if (count == 0) {
               if (!"a".equals(tm.getText())) {
                  failed = true;
                  latch.countDown();
               }
               if (transacted) {
                  sess.rollback();
                  messageOrder += "RB ";
               } else {
                  messageOrder += "RC ";
                  sess.recover();
               }
            }

            if (count == 1) {
               if (!"a".equals(tm.getText())) {
                  failed = true;
                  latch.countDown();
               }
               if (!tm.getJMSRedelivered()) {
                  failed = true;
                  latch.countDown();
               }
            }
            if (count == 2) {
               if (!"b".equals(tm.getText())) {
                  failed = true;
                  latch.countDown();
               }
            }
            if (count == 3) {
               if (!"c".equals(tm.getText())) {
                  failed = true;
                  latch.countDown();
               }
               if (transacted) {
                  sess.commit();
               } else {
                  tm.acknowledge();
               }
               latch.countDown();
            }
            count++;
         } catch (JMSException e) {
            failed = true;
            latch.countDown();
         }
      }
   }

   private class MessageListenerImpl implements MessageListener {

      private final List<Message> messages = Collections.synchronizedList(new ArrayList<>());

      private CountDownLatch latch = new CountDownLatch(1);

      private MessageListenerImpl(final int count) {
         latch = new CountDownLatch(count);
      }

      private MessageListenerImpl() {
         this(1);
      }

      /**
       * Blocks the calling thread until at least a message is received
       */
      public void waitForMessages() throws InterruptedException {
         ActiveMQTestBase.waitForLatch(latch);
      }

      @Override
      public void onMessage(final Message m) {
         messages.add(m);
         logger.trace("Added message {} to my list", m);

         latch.countDown();
      }

      public Message getNextMessage() {
         Iterator<Message> i = messages.iterator();
         if (!i.hasNext()) {
            return null;
         }
         Message m = i.next();
         i.remove();
         return m;
      }

      public List<Message> getMessages() {
         return messages;
      }

      public int size() {
         return messages.size();
      }
   }
}
