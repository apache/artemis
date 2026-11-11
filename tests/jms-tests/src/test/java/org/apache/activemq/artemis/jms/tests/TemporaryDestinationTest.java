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
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import javax.jms.TextMessage;
import javax.naming.NamingException;

import org.apache.activemq.artemis.jms.tests.util.ProxyAssertSupport;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class TemporaryDestinationTest extends JMSTestCase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());



   @Test
   public void testTemp() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();

         Session producerSession = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Session consumerSession = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         TemporaryTopic tempTopic = producerSession.createTemporaryTopic();

         MessageProducer producer = producerSession.createProducer(tempTopic);

         MessageConsumer consumer = consumerSession.createConsumer(tempTopic);

         conn.start();

         final String messageText = "This is a message";

         Message m = producerSession.createTextMessage(messageText);

         producer.send(m);

         TextMessage m2 = (TextMessage) consumer.receive(2000);

         ProxyAssertSupport.assertNotNull(m2);

         ProxyAssertSupport.assertEquals(messageText, m2.getText());

         try {
            tempTopic.delete();
            ProxyAssertSupport.fail();
         } catch (javax.jms.IllegalStateException e) {
            // Can't delete temp dest if there are open consumers
         }

         consumer.close();

         tempTopic.delete();
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   @Test
   public void testTemporaryQueueBasic() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();

         Session producerSession = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Session consumerSession = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         TemporaryQueue tempQueue = producerSession.createTemporaryQueue();

         MessageProducer producer = producerSession.createProducer(tempQueue);

         MessageConsumer consumer = consumerSession.createConsumer(tempQueue);

         conn.start();

         final String messageText = "This is a message";

         Message m = producerSession.createTextMessage(messageText);

         producer.send(m);

         TextMessage m2 = (TextMessage) consumer.receive(2000);

         ProxyAssertSupport.assertNotNull(m2);

         ProxyAssertSupport.assertEquals(messageText, m2.getText());
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   @Test
   public void testTemporaryQueueOnClosedSession() throws Exception {
      Connection producerConnection = null;

      try {
         producerConnection = createConnection();

         Session producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         producerSession.close();

         try {
            producerSession.createTemporaryQueue();
            ProxyAssertSupport.fail("should throw exception");
         } catch (javax.jms.IllegalStateException e) {
            // OK
         }
      } finally {
         if (producerConnection != null) {
            producerConnection.close();
         }
      }
   }

   @Test
   public void testTemporaryQueueDeleteWithConsumer() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();

         Session producerSession = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Session consumerSession = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         TemporaryQueue tempQueue = producerSession.createTemporaryQueue();

         MessageConsumer consumer = consumerSession.createConsumer(tempQueue);

         try {
            tempQueue.delete();

            ProxyAssertSupport.fail("Should throw JMSException");
         } catch (JMSException e) {
            // Should fail - you can't delete a temp queue if it has active consumers
         }

         consumer.close();
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   @Test
   public void testTemporaryTopicDeleteWithConsumer() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();

         Session producerSession = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Session consumerSession = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         TemporaryTopic tempTopic = producerSession.createTemporaryTopic();

         MessageConsumer consumer = consumerSession.createConsumer(tempTopic);

         try {
            tempTopic.delete();

            ProxyAssertSupport.fail("Should throw JMSException");
         } catch (JMSException e) {
            // Should fail - you can't delete a temp topic if it has active consumers
         }

         consumer.close();
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   @Test
   public void testTemporaryQueueDeleted() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();

         Session producerSession = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Session consumerSession = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         // Make sure temporary queue cannot be used after it has been deleted

         TemporaryQueue tempQueue = producerSession.createTemporaryQueue();

         MessageProducer producer = producerSession.createProducer(tempQueue);

         MessageConsumer consumer = consumerSession.createConsumer(tempQueue);

         conn.start();

         final String messageText = "This is a message";

         Message m = producerSession.createTextMessage(messageText);

         producer.send(m);

         TextMessage m2 = (TextMessage) consumer.receive(2000);

         ProxyAssertSupport.assertNotNull(m2);

         ProxyAssertSupport.assertEquals(messageText, m2.getText());

         consumer.close();

         tempQueue.delete();
         conn.close();
         conn = createConnection("guest", "guest");
         try {
            producer.send(m);
            ProxyAssertSupport.fail();
         } catch (JMSException e) {
         }
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   @Test
   public void testTemporaryTopicBasic() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();

         Session producerSession = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Session consumerSession = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         TemporaryTopic tempTopic = producerSession.createTemporaryTopic();

         final MessageProducer producer = producerSession.createProducer(tempTopic);

         MessageConsumer consumer = consumerSession.createConsumer(tempTopic);

         conn.start();

         final String messageText = "This is a message";

         final Message m = producerSession.createTextMessage(messageText);

         Thread t = new Thread(() -> {
            try {
               // this is needed to make sure the main thread has enough time to block
               Thread.sleep(500);
               producer.send(m);
            } catch (Exception e) {
               logger.error(e.getMessage(), e);
            }
         }, "Producer");
         t.start();

         TextMessage m2 = (TextMessage) consumer.receive(3000);

         ProxyAssertSupport.assertNotNull(m2);

         ProxyAssertSupport.assertEquals(messageText, m2.getText());

         t.join();
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   @Test
   public void testTemporaryTopicOnClosedSession() throws Exception {
      Connection producerConnection = null;

      try {
         producerConnection = createConnection();

         Session producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         producerSession.close();

         try {
            producerSession.createTemporaryTopic();
            ProxyAssertSupport.fail("should throw exception");
         } catch (javax.jms.IllegalStateException e) {
            // OK
         }
      } finally {
         if (producerConnection != null) {
            producerConnection.close();
         }
      }
   }

   @Test
   public void testTemporaryTopicShouldNotBeInJNDI() throws Exception {
      Connection producerConnection = createConnection();

      Session producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

      TemporaryTopic tempTopic = producerSession.createTemporaryTopic();
      String topicName = tempTopic.getTopicName();

      try {
         ic.lookup("/topic/" + topicName);
         ProxyAssertSupport.fail("The temporary queue should not be bound to JNDI");
      } catch (NamingException e) {
         // Expected
      }
   }

   @Test
   public void testTemporaryQueueShouldNotBeInJNDI() throws Exception {
      Connection producerConnection = createConnection();

      Session producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

      TemporaryQueue tempQueue = producerSession.createTemporaryQueue();
      String queueName = tempQueue.getQueueName();

      try {
         ic.lookup("/queue/" + queueName);
         ProxyAssertSupport.fail("The temporary queue should not be bound to JNDI");
      } catch (NamingException e) {
         // Expected
      }
   }

   @Test
   public void testCanNotCreateConsumerFromAnotherConnectionForTemporaryQueue() throws Exception {
      Connection conn = createConnection();

      Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      TemporaryQueue tempQueue = sess.createTemporaryQueue();

      Connection anotherConn = createConnection();

      Session sessFromAnotherConn = anotherConn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      try {
         sessFromAnotherConn.createConsumer(tempQueue);
         ProxyAssertSupport.fail("Only temporary destination's own connection is allowed to create MessageConsumers for them.");
      } catch (JMSException e) {
      }

      conn.close();
      anotherConn.close();
   }

   @Test
   public void testCanNotCreateConsumerFromAnotherCnnectionForTemporaryTopic() throws Exception {
      Connection conn = createConnection();

      Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      TemporaryTopic tempTopic = sess.createTemporaryTopic();

      Connection anotherConn = createConnection();

      Session sessFromAnotherConn = anotherConn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      try {
         sessFromAnotherConn.createConsumer(tempTopic);
         ProxyAssertSupport.fail("Only temporary destination's own connection is allowed to create MessageConsumers for them.");
      } catch (JMSException e) {
      }
   }


}
