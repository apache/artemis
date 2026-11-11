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
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.TopicConnection;
import javax.jms.TopicConnectionFactory;
import javax.jms.TopicSession;
import javax.jms.TopicSubscriber;

import org.apache.activemq.artemis.jms.tests.util.ProxyAssertSupport;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * All tests related to closing a Connection.
 */
public class ConnectionClosedTest extends JMSTestCase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());


   @Test
   public void testCloseOnce() throws Exception {
      Connection conn = createConnection();
      conn.close();
   }

   @Test
   public void testCloseTwice() throws Exception {
      Connection conn = createConnection();
      conn.close();
      conn.close();
   }

   /**
    * See TCK test: topicconntests.connNotStartedTopicTest
    */
   @Test
   public void testCannotReceiveMessageOnStoppedConnection() throws Exception {
      TopicConnection conn1 = ((TopicConnectionFactory) topicCf).createTopicConnection();
      TopicConnection conn2 = ((TopicConnectionFactory) topicCf).createTopicConnection();

      TopicSession sess1 = conn1.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);
      TopicSession sess2 = conn2.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);

      TopicSubscriber sub1 = sess1.createSubscriber(ActiveMQServerTestCase.topic1);
      TopicSubscriber sub2 = sess2.createSubscriber(ActiveMQServerTestCase.topic1);

      conn1.start();

      Connection conn3 = createConnection();

      Session sess3 = conn3.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageProducer prod = sess3.createProducer(ActiveMQServerTestCase.topic1);
      prod.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

      final int NUM_MESSAGES = 10;

      for (int i = 0; i < NUM_MESSAGES; i++) {
         TextMessage tm = sess3.createTextMessage("hello");
         prod.send(tm);
      }

      for (int i = 0; i < NUM_MESSAGES; i++) {
         TextMessage tm = (TextMessage) sub1.receive(500);
         ProxyAssertSupport.assertNotNull(tm);
         ProxyAssertSupport.assertEquals("hello", tm.getText());
      }

      Message m = sub2.receiveNoWait();

      ProxyAssertSupport.assertNull(m);

      conn2.start();

      for (int i = 0; i < NUM_MESSAGES; i++) {
         TextMessage tm = (TextMessage) sub2.receive(500);
         ProxyAssertSupport.assertNotNull(tm);
         ProxyAssertSupport.assertEquals("hello", tm.getText());
      }

      logger.debug("all messages received by sub2");

      conn1.close();

      conn2.close();

      conn3.close();

   }

   /**
    * A close terminates all pending message receives on the connection's session's  consumers. The receives may return
    * with a message or null depending on whether there was a message available at the time of the close.
    */
   @Test
   public void testCloseWhileReceiving() throws Exception {
      Connection conn = createConnection();

      Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      conn.start();

      final MessageConsumer consumer = session.createConsumer(ActiveMQServerTestCase.topic1);

      class TestRunnable implements Runnable {

         public String failed;

         @Override
         public void run() {
            try {
               long start = System.currentTimeMillis();
               Message m = consumer.receive(2100);
               if (System.currentTimeMillis() - start >= 2000) {
                  // It timed out
                  failed = "Timed out";
               } else {
                  if (m != null) {
                     failed = "Message Not null";
                  }
               }
            } catch (Exception e) {
               logger.error(e.getMessage(), e);
               failed = e.getMessage();
            }
         }
      }

      TestRunnable runnable = new TestRunnable();
      Thread t = new Thread(runnable);
      t.start();

      Thread.sleep(1000);

      conn.close();

      t.join();

      if (runnable.failed != null) {
         ProxyAssertSupport.fail(runnable.failed);
      }

   }

   @Test
   public void testGetMetadataOnClosedConnection() throws Exception {
      Connection connection = createConnection();

      connection.close();

      try {
         connection.getMetaData();
         ProxyAssertSupport.fail("should throw exception");
      } catch (javax.jms.IllegalStateException e) {
         // OK
      }
   }

   @Test
   public void testCreateSessionOnClosedConnection() throws Exception {
      Connection conn = createConnection();
      conn.close();

      try {
         conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         ProxyAssertSupport.fail("Did not throw javax.jms.IllegalStateException");
      } catch (javax.jms.IllegalStateException e) {
         // OK
      }
   }

   /**
    * Test that close() hierarchically closes all child objects
    */
   @Test
   public void testCloseHierarchy() throws Exception {
      Connection conn = createConnection();
      Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageConsumer consumer = sess.createConsumer(ActiveMQServerTestCase.topic1);
      MessageProducer producer = sess.createProducer(ActiveMQServerTestCase.topic1);
      sess.createBrowser(queue1);
      Message m = sess.createMessage();

      conn.close();

      // Session

      /*
       * If the session is closed then any method invocation apart from close()
       * will throw an IllegalStateException
       */
      try {
         sess.createMessage();
         ProxyAssertSupport.fail("Session is not closed");
      } catch (javax.jms.IllegalStateException e) {
      }

      try {
         sess.getAcknowledgeMode();
         ProxyAssertSupport.fail("should throw IllegalStateException");
      } catch (javax.jms.IllegalStateException e) {
         // OK
      }

      try {
         sess.getTransacted();
         ProxyAssertSupport.fail("should throw IllegalStateException");
      } catch (javax.jms.IllegalStateException e) {
         // OK
      }

      try {
         sess.getMessageListener();
         ProxyAssertSupport.fail("should throw IllegalStateException");
      } catch (javax.jms.IllegalStateException e) {
         // OK
      }

      try {
         sess.createProducer(queue1);
         ProxyAssertSupport.fail("should throw IllegalStateException");
      } catch (javax.jms.IllegalStateException e) {
         // OK
      }

      try {
         sess.createConsumer(queue1);
         ProxyAssertSupport.fail("should throw IllegalStateException");
      } catch (javax.jms.IllegalStateException e) {
         // OK
      }

      // Producer

      /*
       * If the producer is closed then any method invocation apart from close()
       * will throw an IllegalStateException
       */
      try {
         producer.send(m);
         ProxyAssertSupport.fail("Producer is not closed");
      } catch (javax.jms.IllegalStateException e) {
      }

      try {
         producer.getDisableMessageID();
         ProxyAssertSupport.fail("should throw IllegalStateException");
      } catch (javax.jms.IllegalStateException e) {
         // OK
      }

      try {
         producer.getPriority();
         ProxyAssertSupport.fail("should throw IllegalStateException");
      } catch (javax.jms.IllegalStateException e) {
         // OK
      }

      try {
         producer.getDestination();
         ProxyAssertSupport.fail("should throw IllegalStateException");
      } catch (javax.jms.IllegalStateException e) {
         // OK
      }

      try {
         producer.getTimeToLive();
         ProxyAssertSupport.fail("should throw IllegalStateException");
      } catch (javax.jms.IllegalStateException e) {
         // OK
      }

      // ClientConsumer

      try {
         consumer.getMessageSelector();
         ProxyAssertSupport.fail("should throw exception");
      } catch (javax.jms.IllegalStateException e) {
         // OK
      }

      try {
         consumer.getMessageListener();
         ProxyAssertSupport.fail("should throw exception");
      } catch (javax.jms.IllegalStateException e) {
         // OK
      }

      try {
         consumer.receive();
         ProxyAssertSupport.fail("should throw exception");
      } catch (javax.jms.IllegalStateException e) {
         // OK
      }

      // Browser

   }

}
