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
import javax.jms.JMSException;
import javax.jms.JMSSecurityException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.QueueBrowser;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.lang.invoke.MethodHandles;
import java.net.URI;

import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

public class JMSConnectionWithSecurityTest extends JMSClientTestSupport {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Override
   protected boolean isSecurityEnabled() {
      return true;
   }

   @Override
   protected String getJmsConnectionURIOptions() {
      return "amqp.saslMechanisms=PLAIN";
   }

   @Test
   @Timeout(10)
   public void testNoUserOrPassword() throws Exception {
      Connection connection = null;
      try {
         connection = createConnection("", "", null, false);
         connection.start();
         fail("Expected JMSException");
      } catch (JMSSecurityException ex) {
         logger.debug("Failed to authenticate connection with no user / password.", ex);

      } finally {
         if (connection != null) {
            connection.close();
         }
      }
   }

   @Test
   @Timeout(10)
   public void testNoUserOrPasswordWithoutSaslRestrictions() throws Exception {
      Connection connection = null;
      JmsConnectionFactory factory = new JmsConnectionFactory(new URI("amqp://localhost:" + AMQP_PORT));
      try {
         connection = factory.createConnection();
         connection.start();
         fail("Expected Exception");
      } catch (JMSSecurityException ex) {
         logger.debug("Failed to authenticate connection with no user / password.");
      } catch (Exception ex) {
         fail("Expected JMSSecurityException");
      } finally {
         if (connection != null) {
            connection.close();
         }
      }
   }

   @Test
   @Timeout(10)
   public void testUnknownUser() throws Exception {
      Connection connection = null;
      try {
         connection = createConnection("nosuchuser", "blah", null, false);
         connection.start();
         fail("Expected JMSException");
      } catch (JMSSecurityException ex) {
         logger.debug("Failed to authenticate connection with unknown user ID");
      } finally {
         if (connection != null) {
            connection.close();
         }
      }
   }

   @Test
   @Timeout(10)
   public void testKnownUserWrongPassword() throws Exception {
      Connection connection = null;
      try {
         connection = createConnection(fullUser, "wrongPassword", null, false);
         connection.start();
         fail("Expected JMSException");
      } catch (JMSSecurityException ex) {
         logger.debug("Failed to authenticate connection with incorrect password.");
      } finally {
         if (connection != null) {
            connection.close();
         }
      }
   }

   @Test
   @Timeout(30)
   public void testRepeatedWrongPasswordAttempts() throws Exception {
      for (int i = 0; i < 25; ++i) {
         Connection connection = null;
         try {
            connection = createConnection(fullUser, "wrongPassword", null, false);
            connection.start();
            fail("Expected JMSException");
         } catch (JMSSecurityException ex) {
            logger.debug("Failed to authenticate connection with incorrect password.");
         } finally {
            if (connection != null) {
               connection.close();
            }
         }
      }
   }

   @Test
   @Timeout(30)
   public void testSendReceive() throws Exception {
      Connection connection = createConnection(fullUser, fullPass);

      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         javax.jms.Queue queue = session.createQueue(getQueueName());
         MessageProducer p = session.createProducer(queue);
         TextMessage message = null;
         message = session.createTextMessage();
         String messageText = "hello  sent at " + new java.util.Date().toString();
         message.setText(messageText);
         p.send(message);

         // Get the message we just sent
         MessageConsumer consumer = session.createConsumer(queue);
         connection.start();
         Message msg = consumer.receive(5000);
         assertNotNull(msg);
         assertInstanceOf(TextMessage.class, msg);
         TextMessage textMessage = (TextMessage) msg;
         assertEquals(messageText, textMessage.getText());
      } finally {
         connection.close();
      }
   }

   @Test
   @Timeout(30)
   public void testConsumerNotAuthorized() throws Exception {
      Connection connection = createConnection(noprivUser, noprivPass);

      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         javax.jms.Queue queue = session.createQueue(getQueueName());
         try {
            session.createConsumer(queue);
            fail("Should not be able to consume here.");
         } catch (JMSSecurityException jmsSE) {
            logger.debug("Caught expected exception");
         }
      } finally {
         connection.close();
      }
   }

   @Test
   @Timeout(30)
   public void testBrowserNotAuthorized() throws Exception {
      Connection connection = createConnection(noprivUser, noprivPass);

      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         javax.jms.Queue queue = session.createQueue(getQueueName());
         try {
            QueueBrowser browser = session.createBrowser(queue);
            // Browser is not created until an enumeration is requesteda
            browser.getEnumeration();
            fail("Should not be able to consume here.");
         } catch (JMSSecurityException jmsSE) {
            logger.debug("Caught expected exception");
         }
      } finally {
         connection.close();
      }
   }

   @Test
   @Timeout(30)
   public void testConsumerNotAuthorizedToCreateQueues() throws Exception {
      Connection connection = createConnection(noprivUser, noprivPass);

      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         javax.jms.Queue queue = session.createQueue(getQueueName(getPrecreatedQueueSize() + 1));
         try {
            session.createConsumer(queue);
            fail("Should not be able to consume here.");
         } catch (JMSSecurityException jmsSE) {
            logger.debug("Caught expected exception");
         }
      } finally {
         connection.close();
      }
   }

   @Test
   @Timeout(30)
   public void testProducerNotAuthorized() throws Exception {
      Connection connection = createConnection(guestUser, guestPass);

      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         javax.jms.Queue queue = session.createQueue(getQueueName());
         try {
            session.createProducer(queue);
            fail("Should not be able to produce here.");
         } catch (JMSSecurityException jmsSE) {
            logger.debug("Caught expected exception");
         }
      } finally {
         connection.close();
      }
   }

   @Test
   @Timeout(30)
   public void testAnonymousProducerNotAuthorized() throws Exception {
      Connection connection = createConnection(guestUser, guestPass);

      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         javax.jms.Queue queue = session.createQueue(getQueueName());
         MessageProducer producer = session.createProducer(null);

         try {
            producer.send(queue, session.createTextMessage());
            fail("Should not be able to produce here.");
         } catch (JMSSecurityException jmsSE) {
            logger.debug("Caught expected exception");
         }
      } finally {
         connection.close();
      }
   }

   @Test
   @Timeout(30)
   public void testCreateTemporaryQueueNotAuthorized() throws JMSException {
      Connection connection = createConnection(guestUser, guestPass);

      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         try {
            session.createTemporaryQueue();
         } catch (JMSSecurityException jmsse) {
            logger.debug("Client should have thrown a JMSSecurityException but only threw JMSException");
         }

         // Should not be fatal
         assertNotNull(connection.createSession(false, Session.AUTO_ACKNOWLEDGE));
      } finally {
         connection.close();
      }
   }

   @Test
   @Timeout(30)
   public void testCreateTemporaryTopicNotAuthorized() throws JMSException {
      Connection connection = createConnection(guestUser, guestPass);

      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         try {
            session.createTemporaryTopic();
         } catch (JMSSecurityException jmsse) {
            logger.debug("Client should have thrown a JMSSecurityException but only threw JMSException");
         }

         // Should not be fatal
         assertNotNull(connection.createSession(false, Session.AUTO_ACKNOWLEDGE));
      } finally {
         connection.close();
      }
   }
}
