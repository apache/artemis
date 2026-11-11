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
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.QueueBrowser;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.lang.invoke.MethodHandles;
import java.util.Enumeration;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.qpid.jms.JmsConnection;
import org.apache.qpid.jms.policy.JmsDefaultPrefetchPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for various QueueBrowser scenarios with an AMQP JMS client.
 */
public class JMSQueueBrowserTest extends JMSClientTestSupport {

   protected static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Test
   @Timeout(60)
   public void testBrowseAllInQueueZeroPrefetch() throws Exception {

      final int MSG_COUNT = 5;

      JmsConnection connection = (JmsConnection) createConnection();
      ((JmsDefaultPrefetchPolicy) connection.getPrefetchPolicy()).setAll(0);

      connection.start();

      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      assertNotNull(session);
      javax.jms.Queue queue = session.createQueue(getQueueName());
      sendMessages(name, MSG_COUNT, false);

      Queue queueView = getProxyToQueue(getQueueName());
      Wait.assertEquals(MSG_COUNT, queueView::getMessageCount);

      QueueBrowser browser = session.createBrowser(queue);
      assertNotNull(browser);
      Enumeration<?> enumeration = browser.getEnumeration();
      int count = 0;
      while (count < MSG_COUNT && enumeration.hasMoreElements()) {
         Message msg = (Message) enumeration.nextElement();
         assertNotNull(msg);
         logger.debug("Recv: {}", msg);
         count++;
      }

      logger.debug("Received all expected message, checking that hasMoreElements returns false");
      assertFalse(enumeration.hasMoreElements());
      assertEquals(5, count);
   }

   @Test
   @Timeout(40)
   public void testCreateQueueBrowser() throws Exception {
      Connection connection = createConnection();
      connection.start();

      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      assertNotNull(session);
      javax.jms.Queue queue = session.createQueue(getQueueName());
      session.createConsumer(queue).close();

      QueueBrowser browser = session.createBrowser(queue);
      assertNotNull(browser);

      Queue queueView = getProxyToQueue(getQueueName());
      assertEquals(0, queueView.getMessageCount());
   }

   @Test
   @Timeout(40)
   public void testNoMessagesBrowserHasNoElements() throws Exception {
      Connection connection = createConnection();
      connection.start();

      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      assertNotNull(session);
      javax.jms.Queue queue = session.createQueue(getQueueName());
      session.createConsumer(queue).close();

      QueueBrowser browser = session.createBrowser(queue);
      assertNotNull(browser);

      Queue queueView = getProxyToQueue(getQueueName());
      assertEquals(0, queueView.getMessageCount());

      Enumeration<?> enumeration = browser.getEnumeration();
      assertFalse(enumeration.hasMoreElements());
   }

   @Test
   @Timeout(30)
   public void testBroseOneInQueue() throws Exception {
      Connection connection = createConnection();
      connection.start();

      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      javax.jms.Queue queue = session.createQueue(getQueueName());
      MessageProducer producer = session.createProducer(queue);
      producer.send(session.createTextMessage("hello"));
      producer.close();

      QueueBrowser browser = session.createBrowser(queue);
      Enumeration<?> enumeration = browser.getEnumeration();
      while (enumeration.hasMoreElements()) {
         Message m = (Message) enumeration.nextElement();
         assertInstanceOf(TextMessage.class, m);
         logger.debug("Browsed message {} from Queue {}", m, queue);
      }

      browser.close();

      MessageConsumer consumer = session.createConsumer(queue);
      Message msg = consumer.receive(5000);
      assertNotNull(msg);
      assertInstanceOf(TextMessage.class, msg);
   }

   @Test
   @Timeout(60)
   public void testBrowseAllInQueue() throws Exception {
      Connection connection = createConnection();
      connection.start();

      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      assertNotNull(session);
      javax.jms.Queue queue = session.createQueue(getQueueName());
      sendMessages(name, 5, false);

      Queue queueView = getProxyToQueue(getQueueName());
      Wait.assertEquals(5, queueView::getMessageCount);

      QueueBrowser browser = session.createBrowser(queue);
      assertNotNull(browser);
      Enumeration<?> enumeration = browser.getEnumeration();
      int count = 0;
      while (enumeration.hasMoreElements()) {
         Message msg = (Message) enumeration.nextElement();
         assertNotNull(msg);
         logger.debug("Recv: {}", msg);
         count++;
         TimeUnit.MILLISECONDS.sleep(50);
      }
      assertFalse(enumeration.hasMoreElements());
      assertEquals(5, count);
   }

   @Test
   @Timeout(60)
   public void testBrowseAllInQueuePrefetchOne() throws Exception {
      Connection connection = createConnection();
      connection.start();

      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      assertNotNull(session);
      javax.jms.Queue queue = session.createQueue(getQueueName());
      sendMessages(name, 5, false);

      Queue queueView = getProxyToQueue(getQueueName());
      Wait.assertEquals(5, queueView::getMessageCount);

      QueueBrowser browser = session.createBrowser(queue);
      assertNotNull(browser);
      Enumeration<?> enumeration = browser.getEnumeration();
      int count = 0;
      while (enumeration.hasMoreElements()) {
         Message msg = (Message) enumeration.nextElement();
         assertNotNull(msg);
         logger.debug("Recv: {}", msg);
         count++;
      }
      assertFalse(enumeration.hasMoreElements());
      assertEquals(5, count);
   }

   @Test
   @Timeout(40)
   public void testBrowseAllInQueueTxSession() throws Exception {
      Connection connection = createConnection();
      connection.start();

      Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
      assertNotNull(session);
      javax.jms.Queue queue = session.createQueue(getQueueName());
      sendMessages(name, 5, false);

      Queue queueView = getProxyToQueue(getQueueName());
      Wait.assertEquals(5, queueView::getMessageCount);

      QueueBrowser browser = session.createBrowser(queue);
      assertNotNull(browser);
      Enumeration<?> enumeration = browser.getEnumeration();
      int count = 0;
      while (enumeration.hasMoreElements()) {
         Message msg = (Message) enumeration.nextElement();
         assertNotNull(msg);
         logger.debug("Recv: {}", msg);
         count++;
      }
      assertFalse(enumeration.hasMoreElements());
      assertEquals(5, count);
   }

   @Test
   @Timeout(40)
   public void testQueueBrowserInTxSessionLeavesOtherWorkUnaffected() throws Exception {
      Connection connection = createConnection();
      connection.start();

      Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
      assertNotNull(session);
      javax.jms.Queue queue = session.createQueue(getQueueName());
      sendMessages(name, 5, false);

      Queue queueView = getProxyToQueue(getQueueName());
      Wait.assertEquals(5, queueView::getMessageCount);

      // Send some TX work but don't commit.
      MessageProducer txProducer = session.createProducer(queue);
      for (int i = 0; i < 5; ++i) {
         txProducer.send(session.createMessage());
      }

      assertEquals(5, queueView.getMessageCount());

      QueueBrowser browser = session.createBrowser(queue);
      assertNotNull(browser);
      Enumeration<?> enumeration = browser.getEnumeration();
      int count = 0;
      while (enumeration.hasMoreElements()) {
         Message msg = (Message) enumeration.nextElement();
         assertNotNull(msg);
         logger.debug("Recv: {}", msg);
         count++;
      }

      assertFalse(enumeration.hasMoreElements());
      assertEquals(5, count);

      browser.close();

      // Now check that all browser work did not affect the session transaction.
      Wait.assertEquals(5, queueView::getMessageCount);
      session.commit();
      Wait.assertEquals(10, queueView::getMessageCount);
   }

   @Test
   @Timeout(60)
   public void testBrowseAllInQueueSmallPrefetch() throws Exception {
      Connection connection = createConnection();
      connection.start();

      final int MSG_COUNT = 30;

      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      assertNotNull(session);
      javax.jms.Queue queue = session.createQueue(getQueueName());
      sendMessages(name, MSG_COUNT, false);

      Queue queueView = getProxyToQueue(getQueueName());
      Wait.assertEquals(MSG_COUNT, queueView::getMessageCount);

      QueueBrowser browser = session.createBrowser(queue);
      assertNotNull(browser);
      Enumeration<?> enumeration = browser.getEnumeration();
      int count = 0;
      while (enumeration.hasMoreElements()) {
         Message msg = (Message) enumeration.nextElement();
         assertNotNull(msg);
         logger.debug("Recv: {}", msg);
         count++;
      }
      assertFalse(enumeration.hasMoreElements());
      assertEquals(MSG_COUNT, count);
   }
}
