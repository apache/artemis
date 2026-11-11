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
import java.lang.invoke.MethodHandles;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.activemq.artemis.jms.tests.util.ProxyAssertSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * The most comprehensive, yet simple, unit test.
 */
public class JMSTest extends JMSTestCase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   Connection conn = null;

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      try {
         if (conn != null) {
            conn.close();
            conn = null;
         }
      } finally {
         super.tearDown();
      }
   }

   @Test
   public void test_NonPersistent_NonTransactional() throws Exception {
      conn = createConnection();

      Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageProducer prod = session.createProducer(queue1);
      prod.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

      TextMessage m = session.createTextMessage("message one");

      prod.send(m);

      conn.close();

      conn = createConnection();

      session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageConsumer cons = session.createConsumer(queue1);

      conn.start();

      TextMessage rm = (TextMessage) cons.receive();

      ProxyAssertSupport.assertNotNull(rm);

      ProxyAssertSupport.assertEquals("message one", rm.getText());
   }

   @Test
   public void testCreateTextMessageNull() throws Exception {
      conn = createConnection();

      Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageProducer prod = session.createProducer(queue1);
      prod.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

      TextMessage m = session.createTextMessage();

      m.setText("message one");

      prod.send(m);

      conn.close();

      conn = createConnection();

      session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageConsumer cons = session.createConsumer(queue1);

      conn.start();

      TextMessage rm = (TextMessage) cons.receive();

      ProxyAssertSupport.assertEquals("message one", rm.getText());
   }

   @Test
   public void testPersistent_NonTransactional() throws Exception {
      conn = createConnection();

      Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageProducer prod = session.createProducer(queue1);
      prod.setDeliveryMode(DeliveryMode.PERSISTENT);

      TextMessage m = session.createTextMessage("message one");

      prod.send(m);

      conn.close();

      conn = createConnection();

      session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageConsumer cons = session.createConsumer(queue1);

      conn.start();

      TextMessage rm = (TextMessage) cons.receive();

      ProxyAssertSupport.assertEquals("message one", rm.getText());
   }

   @Test
   public void testNonPersistent_Transactional_Send() throws Exception {
      conn = createConnection();

      Session session = conn.createSession(true, Session.SESSION_TRANSACTED);

      MessageProducer prod = session.createProducer(queue1);
      prod.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

      TextMessage m = session.createTextMessage("message one");
      prod.send(m);
      m = session.createTextMessage("message two");
      prod.send(m);

      session.commit();

      conn.close();

      conn = createConnection();

      session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageConsumer cons = session.createConsumer(queue1);

      conn.start();

      TextMessage rm = (TextMessage) cons.receive();
      ProxyAssertSupport.assertEquals("message one", rm.getText());
      rm = (TextMessage) cons.receive();
      ProxyAssertSupport.assertEquals("message two", rm.getText());
   }

   @Test
   public void testPersistent_Transactional_Send() throws Exception {
      conn = createConnection();

      Session session = conn.createSession(true, Session.SESSION_TRANSACTED);

      MessageProducer prod = session.createProducer(queue1);
      prod.setDeliveryMode(DeliveryMode.PERSISTENT);

      TextMessage m = session.createTextMessage("message one");
      prod.send(m);
      m = session.createTextMessage("message two");
      prod.send(m);

      session.commit();

      conn.close();

      conn = createConnection();

      session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageConsumer cons = session.createConsumer(queue1);

      conn.start();

      TextMessage rm = (TextMessage) cons.receive();
      ProxyAssertSupport.assertEquals("message one", rm.getText());
      rm = (TextMessage) cons.receive();
      ProxyAssertSupport.assertEquals("message two", rm.getText());
   }

   @Test
   public void testNonPersistent_Transactional_Acknowledgment() throws Exception {
      conn = createConnection();

      Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageProducer prod = session.createProducer(queue1);
      prod.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
      TextMessage m = session.createTextMessage("one");
      prod.send(m);

      conn.close();

      conn = createConnection();

      session = conn.createSession(true, Session.SESSION_TRANSACTED);

      MessageConsumer cons = session.createConsumer(queue1);

      conn.start();

      TextMessage rm = (TextMessage) cons.receive();
      ProxyAssertSupport.assertEquals("one", rm.getText());

      session.commit();
   }

   @Test
   public void testAsynchronous_to_Client() throws Exception {
      conn = createConnection();

      final Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      final MessageConsumer cons = session.createConsumer(queue1);

      conn.start();

      final AtomicReference<Message> message = new AtomicReference<>();
      final CountDownLatch latch = new CountDownLatch(1);

      new Thread(() -> {
         try {
            // sleep a little bit to ensure that
            // prod.send will be called before cons.reveive
            Thread.sleep(500);

            synchronized (session) {
               Message m = cons.receive(5000);
               if (m != null) {
                  message.set(m);
                  latch.countDown();
               }
            }
         } catch (Exception e) {
            logger.error("receive failed", e);
         }

      }, "Receiving Thread").start();

      synchronized (session) {
         MessageProducer prod = session.createProducer(queue1);
         prod.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

         TextMessage m = session.createTextMessage("message one");

         prod.send(m);
      }

      boolean gotMessage = latch.await(5000, TimeUnit.MILLISECONDS);
      ProxyAssertSupport.assertTrue(gotMessage);
      TextMessage rm = (TextMessage) message.get();

      ProxyAssertSupport.assertEquals("message one", rm.getText());
   }

   @Test
   public void testMessageListener() throws Exception {
      conn = createConnection();

      Session sessionConsumer = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageConsumer cons = sessionConsumer.createConsumer(queue1);

      final AtomicReference<Message> message = new AtomicReference<>();
      final CountDownLatch latch = new CountDownLatch(1);

      cons.setMessageListener(m -> {
         message.set(m);
         latch.countDown();
      });

      conn.start();

      Session sessionProducer = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageProducer prod = sessionProducer.createProducer(queue1);
      prod.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
      TextMessage m = sessionProducer.createTextMessage("one");
      prod.send(m);

      boolean gotMessage = latch.await(5000, MILLISECONDS);
      ProxyAssertSupport.assertTrue(gotMessage);
      TextMessage rm = (TextMessage) message.get();

      ProxyAssertSupport.assertEquals("one", rm.getText());
   }

   @Test
   public void testClientAcknowledge() throws Exception {
      conn = createConnection();

      Session session = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);
      MessageProducer p = session.createProducer(queue1);
      p.send(session.createTextMessage("CLACK"));

      MessageConsumer cons = session.createConsumer(queue1);

      conn.start();

      TextMessage m = (TextMessage) cons.receive(1000);

      ProxyAssertSupport.assertEquals("CLACK", m.getText());

      // make sure the message is still in "delivering" state
      assertRemainingMessages(1);

      m.acknowledge();

      assertRemainingMessages(0);
   }
}
