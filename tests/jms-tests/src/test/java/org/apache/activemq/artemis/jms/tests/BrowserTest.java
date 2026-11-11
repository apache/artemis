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
import javax.jms.InvalidDestinationException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.QueueBrowser;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.Enumeration;

import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.tests.util.ProxyAssertSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class BrowserTest extends JMSTestCase {

   Connection conn;

   @Test
   public void testCreateBrowserOnNullDestination() throws Exception {
      conn = getConnectionFactory().createConnection();

      Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      try {
         session.createBrowser(null);
         ProxyAssertSupport.fail("should throw exception");
      } catch (InvalidDestinationException e) {
         // OK
      }
   }

   @Test
   public void testCreateBrowserOnNonExistentQueue() throws Exception {
      Connection pconn = getConnectionFactory().createConnection();

      try {
         Session ps = pconn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         try {
            ps.createBrowser(() -> "NoSuchQueue");
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
   public void testBrowse2() throws Exception {
      conn = getConnectionFactory().createConnection();

      Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageProducer producer = session.createProducer(queue1);

      ActiveMQConnectionFactory cf1 = (ActiveMQConnectionFactory) getConnectionFactory();

      ClientSession coreSession = cf1.getServerLocator().createSessionFactory().createSession(true, true);

      coreSession.start();

      ClientConsumer browser = coreSession.createConsumer("Queue1", true);

      conn.start();

      Message m = session.createMessage();
      m.setIntProperty("cnt", 0);
      producer.send(m);

      assertNotNull(browser.receiveImmediate());

      coreSession.close();

      drainDestination(getConnectionFactory(), queue1);
   }

   @Test
   public void testBrowse() throws Exception {
      conn = getConnectionFactory().createConnection();

      Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageProducer producer = session.createProducer(queue1);

      QueueBrowser browser = session.createBrowser(queue1);

      ProxyAssertSupport.assertEquals(browser.getQueue(), queue1);

      ProxyAssertSupport.assertNull(browser.getMessageSelector());

      Enumeration<Message> en = browser.getEnumeration();

      conn.start();

      Message m = session.createMessage();
      m.setIntProperty("cnt", 0);
      producer.send(m);
      Message m2 = en.nextElement();

      assertNotNull(m2);

      drainDestination(getConnectionFactory(), queue1);
   }

   @Test
   public void testBrowseWithSelector() throws Exception {
      try {
         conn = getConnectionFactory().createConnection();

         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer producer = session.createProducer(queue1);

         final int numMessages = 100;

         for (int i = 0; i < numMessages; i++) {
            Message m = session.createMessage();
            m.setIntProperty("test_counter", i + 1);
            producer.send(m);
         }
      } finally {
         removeAllMessages(queue1.getQueueName(), true);
      }
   }

   @Test
   public void testGetEnumeration() throws Exception {
      try {
         conn = getConnectionFactory().createConnection();

         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer producer = session.createProducer(queue1);

         // send a message to the queue

         Message m = session.createTextMessage("A");
         producer.send(m);

         // make sure we can browse it

         QueueBrowser browser = session.createBrowser(queue1);

         Enumeration en = browser.getEnumeration();

         ProxyAssertSupport.assertTrue(en.hasMoreElements());

         TextMessage rm = (TextMessage) en.nextElement();

         ProxyAssertSupport.assertNotNull(rm);
         ProxyAssertSupport.assertEquals("A", rm.getText());

         ProxyAssertSupport.assertFalse(en.hasMoreElements());

         // create a *new* enumeration, that should reset it

         en = browser.getEnumeration();

         ProxyAssertSupport.assertTrue(en.hasMoreElements());

         rm = (TextMessage) en.nextElement();

         ProxyAssertSupport.assertNotNull(rm);
         ProxyAssertSupport.assertEquals("A", rm.getText());

         ProxyAssertSupport.assertFalse(en.hasMoreElements());
      } finally {
         removeAllMessages(queue1.getQueueName(), true);
      }
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      try {
         if (conn != null) {
            conn.close();
         }
      } finally {
         super.tearDown();
      }
   }
}
