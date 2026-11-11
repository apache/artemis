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
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.artemis.jms.tests.util.ProxyAssertSupport;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class PersistenceTest extends JMSTestCase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   /**
    * Test that the messages in a persistent queue survive starting and stopping and server,
    */
   @Test
   public void testQueuePersistence() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();
         Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer prod = sess.createProducer(queue1);
         prod.setDeliveryMode(DeliveryMode.PERSISTENT);

         for (int i = 0; i < 10; i++) {
            TextMessage tm = sess.createTextMessage("message" + i);
            prod.send(tm);
         }

         conn.close();

         stop();

         startNoDelete();

         // Apache Artemis server restart implies new ConnectionFactory lookup
         deployAndLookupAdministeredObjects();

         conn = createConnection();
         sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         conn.start();
         MessageConsumer cons = sess.createConsumer(queue1);
         for (int i = 0; i < 10; i++) {
            TextMessage tm = (TextMessage) cons.receive(3000);

            ProxyAssertSupport.assertNotNull(tm);
            if (tm == null) {
               break;
            }
            ProxyAssertSupport.assertEquals("message" + i, tm.getText());
         }
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   /**
    * Test that the JMSRedelivered and delivery count survives a restart
    */
   @Test
   public void testJMSRedeliveredRestart() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();
         Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer prod = sess.createProducer(queue1);
         prod.setDeliveryMode(DeliveryMode.PERSISTENT);

         for (int i = 0; i < 10; i++) {
            TextMessage tm = sess.createTextMessage("message" + i);
            prod.send(tm);
         }

         Session sess2 = conn.createSession(true, Session.SESSION_TRANSACTED);

         MessageConsumer cons = sess2.createConsumer(queue1);

         conn.start();

         for (int i = 0; i < 10; i++) {
            TextMessage tm = (TextMessage) cons.receive(1000);

            ProxyAssertSupport.assertNotNull(tm);

            ProxyAssertSupport.assertEquals("message" + i, tm.getText());

            ProxyAssertSupport.assertFalse(tm.getJMSRedelivered());

            ProxyAssertSupport.assertEquals(1, tm.getIntProperty("JMSXDeliveryCount"));
         }

         // rollback
         sess2.rollback();

         for (int i = 0; i < 10; i++) {
            TextMessage tm = (TextMessage) cons.receive(1000);

            ProxyAssertSupport.assertNotNull(tm);

            ProxyAssertSupport.assertEquals("message" + i, tm.getText());

            ProxyAssertSupport.assertTrue(tm.getJMSRedelivered());

            ProxyAssertSupport.assertEquals(2, tm.getIntProperty("JMSXDeliveryCount"));
         }

         conn.close();

         stop();

         startNoDelete();

         // Apache Artemis server restart implies new ConnectionFactory lookup
         deployAndLookupAdministeredObjects();

         conn = createConnection();
         sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         conn.start();
         cons = sess.createConsumer(queue1);
         for (int i = 0; i < 10; i++) {
            TextMessage tm = (TextMessage) cons.receive(3000);

            ProxyAssertSupport.assertNotNull(tm);

            ProxyAssertSupport.assertEquals("message" + i, tm.getText());

            ProxyAssertSupport.assertTrue(tm.getJMSRedelivered());

            ProxyAssertSupport.assertEquals(3, tm.getIntProperty("JMSXDeliveryCount"));
         }
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   /**
    * First test that message order survives a restart
    */
   @Test
   public void testMessageOrderPersistence_1() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();
         Session sessSend = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer prod = sessSend.createProducer(queue1);

         TextMessage m0 = sessSend.createTextMessage("a");
         TextMessage m1 = sessSend.createTextMessage("b");
         TextMessage m2 = sessSend.createTextMessage("c");
         TextMessage m3 = sessSend.createTextMessage("d");
         TextMessage m4 = sessSend.createTextMessage("e");
         TextMessage m5 = sessSend.createTextMessage("f");
         TextMessage m6 = sessSend.createTextMessage("g");
         TextMessage m7 = sessSend.createTextMessage("h");
         TextMessage m8 = sessSend.createTextMessage("i");
         TextMessage m9 = sessSend.createTextMessage("j");

         prod.send(m0, DeliveryMode.PERSISTENT, 0, 0);
         prod.send(m1, DeliveryMode.PERSISTENT, 1, 0);
         prod.send(m2, DeliveryMode.PERSISTENT, 2, 0);
         prod.send(m3, DeliveryMode.PERSISTENT, 3, 0);
         prod.send(m4, DeliveryMode.PERSISTENT, 4, 0);
         prod.send(m5, DeliveryMode.PERSISTENT, 5, 0);
         prod.send(m6, DeliveryMode.PERSISTENT, 6, 0);
         prod.send(m7, DeliveryMode.PERSISTENT, 7, 0);
         prod.send(m8, DeliveryMode.PERSISTENT, 8, 0);
         prod.send(m9, DeliveryMode.PERSISTENT, 9, 0);

         conn.close();

         stop();

         startNoDelete();

         // Apache Artemis server restart implies new ConnectionFactory lookup
         deployAndLookupAdministeredObjects();

         conn = createConnection();
         Session sessReceive = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         conn.start();
         MessageConsumer cons = sessReceive.createConsumer(queue1);

         {
            TextMessage t = (TextMessage) cons.receive(1000);
            ProxyAssertSupport.assertNotNull(t);
            ProxyAssertSupport.assertEquals("j", t.getText());
         }
         {
            TextMessage t = (TextMessage) cons.receive(1000);
            ProxyAssertSupport.assertNotNull(t);
            ProxyAssertSupport.assertEquals("i", t.getText());
         }
         {
            TextMessage t = (TextMessage) cons.receive(1000);
            ProxyAssertSupport.assertNotNull(t);
            ProxyAssertSupport.assertEquals("h", t.getText());
         }
         {
            TextMessage t = (TextMessage) cons.receive(1000);
            ProxyAssertSupport.assertNotNull(t);
            ProxyAssertSupport.assertEquals("g", t.getText());
         }
         {
            TextMessage t = (TextMessage) cons.receive(1000);
            ProxyAssertSupport.assertNotNull(t);
            ProxyAssertSupport.assertEquals("f", t.getText());
         }
         {
            TextMessage t = (TextMessage) cons.receive(1000);
            ProxyAssertSupport.assertNotNull(t);
            ProxyAssertSupport.assertEquals("e", t.getText());
         }
         {
            TextMessage t = (TextMessage) cons.receive(1000);
            ProxyAssertSupport.assertNotNull(t);
            ProxyAssertSupport.assertEquals("d", t.getText());
         }
         {
            TextMessage t = (TextMessage) cons.receive(1000);
            ProxyAssertSupport.assertNotNull(t);
            ProxyAssertSupport.assertEquals("c", t.getText());
         }
         {
            TextMessage t = (TextMessage) cons.receive(1000);
            ProxyAssertSupport.assertNotNull(t);
            ProxyAssertSupport.assertEquals("b", t.getText());
         }
         {
            TextMessage t = (TextMessage) cons.receive(1000);
            ProxyAssertSupport.assertNotNull(t);
            ProxyAssertSupport.assertEquals("a", t.getText());
         }
         {
            TextMessage t = (TextMessage) cons.receiveNoWait();
            ProxyAssertSupport.assertNull(t);
         }
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   /**
    * Second test that message order survives a restart
    */
   @Test
   public void testMessageOrderPersistence_2() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();
         Session sessSend = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer prod = sessSend.createProducer(queue1);

         TextMessage m0 = sessSend.createTextMessage("a");
         TextMessage m1 = sessSend.createTextMessage("b");
         TextMessage m2 = sessSend.createTextMessage("c");
         TextMessage m3 = sessSend.createTextMessage("d");
         TextMessage m4 = sessSend.createTextMessage("e");
         TextMessage m5 = sessSend.createTextMessage("f");
         TextMessage m6 = sessSend.createTextMessage("g");
         TextMessage m7 = sessSend.createTextMessage("h");
         TextMessage m8 = sessSend.createTextMessage("i");
         TextMessage m9 = sessSend.createTextMessage("j");

         prod.send(m0, DeliveryMode.PERSISTENT, 0, 0);
         prod.send(m1, DeliveryMode.PERSISTENT, 0, 0);
         prod.send(m2, DeliveryMode.PERSISTENT, 0, 0);
         prod.send(m3, DeliveryMode.PERSISTENT, 3, 0);
         prod.send(m4, DeliveryMode.PERSISTENT, 3, 0);
         prod.send(m5, DeliveryMode.PERSISTENT, 4, 0);
         prod.send(m6, DeliveryMode.PERSISTENT, 4, 0);
         prod.send(m7, DeliveryMode.PERSISTENT, 5, 0);
         prod.send(m8, DeliveryMode.PERSISTENT, 5, 0);
         prod.send(m9, DeliveryMode.PERSISTENT, 6, 0);

         conn.close();

         stop();

         startNoDelete();

         deployAndLookupAdministeredObjects();

         conn = createConnection();
         Session sessReceive = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         conn.start();
         MessageConsumer cons = sessReceive.createConsumer(queue1);

         {
            TextMessage t = (TextMessage) cons.receive(1000);
            ProxyAssertSupport.assertNotNull(t);
            ProxyAssertSupport.assertEquals("j", t.getText());
         }
         {
            TextMessage t = (TextMessage) cons.receive(1000);
            ProxyAssertSupport.assertNotNull(t);
            ProxyAssertSupport.assertEquals("h", t.getText());
         }
         {
            TextMessage t = (TextMessage) cons.receive(1000);
            ProxyAssertSupport.assertNotNull(t);
            ProxyAssertSupport.assertEquals("i", t.getText());
         }
         {
            TextMessage t = (TextMessage) cons.receive(1000);
            ProxyAssertSupport.assertNotNull(t);
            ProxyAssertSupport.assertEquals("f", t.getText());
         }
         {
            TextMessage t = (TextMessage) cons.receive(1000);
            ProxyAssertSupport.assertNotNull(t);
            ProxyAssertSupport.assertEquals("g", t.getText());
         }
         {
            TextMessage t = (TextMessage) cons.receive(1000);
            ProxyAssertSupport.assertNotNull(t);
            ProxyAssertSupport.assertEquals("d", t.getText());
         }
         {
            TextMessage t = (TextMessage) cons.receive(1000);
            ProxyAssertSupport.assertNotNull(t);
            ProxyAssertSupport.assertEquals("e", t.getText());
         }
         {
            TextMessage t = (TextMessage) cons.receive(1000);
            ProxyAssertSupport.assertNotNull(t);
            ProxyAssertSupport.assertEquals("a", t.getText());
         }
         {
            TextMessage t = (TextMessage) cons.receive(1000);
            ProxyAssertSupport.assertNotNull(t);
            ProxyAssertSupport.assertEquals("b", t.getText());
         }
         {
            TextMessage t = (TextMessage) cons.receive(1000);
            ProxyAssertSupport.assertNotNull(t);
            ProxyAssertSupport.assertEquals("c", t.getText());
         }
         {
            TextMessage t = (TextMessage) cons.receiveNoWait();
            ProxyAssertSupport.assertNull(t);
         }
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   /**
    * Test durable subscription state survives a server crash
    */
   @Test
   public void testDurableSubscriptionPersistence_1() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();
         conn.setClientID("five");

         Session s = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageConsumer ds = s.createDurableSubscriber(ActiveMQServerTestCase.topic1, "sub", null, false);

         MessageProducer p = s.createProducer(ActiveMQServerTestCase.topic1);
         p.setDeliveryMode(DeliveryMode.PERSISTENT);
         TextMessage tm = s.createTextMessage("thebody");

         p.send(tm);
         logger.debug("message sent");

         conn.close();

         stop();

         startNoDelete();

         deployAndLookupAdministeredObjects();

         conn = createConnection();
         conn.setClientID("five");

         s = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         conn.start();

         ds = s.createDurableSubscriber(ActiveMQServerTestCase.topic1, "sub", null, false);

         TextMessage rm = (TextMessage) ds.receive(3000);
         ProxyAssertSupport.assertNotNull(rm);
         ProxyAssertSupport.assertEquals("thebody", rm.getText());

         ds.close();

         s.unsubscribe("sub");
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   /**
    * Test durable subscription state survives a restart
    */
   @Test
   public void testDurableSubscriptionPersistence_2() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();
         conn.setClientID("Sausages");

         Session sessConsume = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageConsumer sub1 = sessConsume.createDurableSubscriber(ActiveMQServerTestCase.topic1, "sub1", null, false);
         MessageConsumer sub2 = sessConsume.createDurableSubscriber(ActiveMQServerTestCase.topic1, "sub2", null, false);
         MessageConsumer sub3 = sessConsume.createDurableSubscriber(ActiveMQServerTestCase.topic1, "sub3", null, false);

         Session sessSend = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer prod = sessSend.createProducer(ActiveMQServerTestCase.topic1);
         prod.setDeliveryMode(DeliveryMode.PERSISTENT);

         for (int i = 0; i < 10; i++) {
            TextMessage tm = sessSend.createTextMessage("message" + i);
            prod.send(tm);
         }

         conn.close();

         stop();

         startNoDelete();

         // Apache Artemis server restart implies new ConnectionFactory lookup
         deployAndLookupAdministeredObjects();

         conn = createConnection();
         conn.setClientID("Sausages");

         sessConsume = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         conn.start();

         sub1 = sessConsume.createDurableSubscriber(ActiveMQServerTestCase.topic1, "sub1", null, false);
         sub2 = sessConsume.createDurableSubscriber(ActiveMQServerTestCase.topic1, "sub2", null, false);
         sub3 = sessConsume.createDurableSubscriber(ActiveMQServerTestCase.topic1, "sub3", null, false);

         for (int i = 0; i < 10; i++) {
            TextMessage tm1 = (TextMessage) sub1.receive(3000);
            ProxyAssertSupport.assertNotNull(tm1);
            if (tm1 == null) {
               break;
            }
            ProxyAssertSupport.assertEquals("message" + i, tm1.getText());

            TextMessage tm2 = (TextMessage) sub2.receive(3000);
            ProxyAssertSupport.assertNotNull(tm2);
            if (tm2 == null) {
               break;
            }
            ProxyAssertSupport.assertEquals("message" + i, tm2.getText());

            TextMessage tm3 = (TextMessage) sub3.receive(3000);
            ProxyAssertSupport.assertNotNull(tm3);
            if (tm3 == null) {
               break;
            }
            ProxyAssertSupport.assertEquals("message" + i, tm3.getText());
         }

         sub1.close();
         sub2.close();
         sub3.close();

         sessConsume.unsubscribe("sub1");
         sessConsume.unsubscribe("sub2");
         sessConsume.unsubscribe("sub3");
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }
}
