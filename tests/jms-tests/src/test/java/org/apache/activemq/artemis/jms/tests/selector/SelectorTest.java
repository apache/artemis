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
package org.apache.activemq.artemis.jms.tests.selector;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.tests.ActiveMQServerTestCase;
import org.apache.activemq.artemis.jms.tests.util.ProxyAssertSupport;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class SelectorTest extends ActiveMQServerTestCase {

   /**
    * Test case for http://jira.jboss.org/jira/browse/JBMESSAGING-105
    * <br>
    * Two Messages are sent to a queue. There is one receiver on the queue. The receiver only
    * receives one of the messages due to a message selector only matching one of them. The receiver
    * is then closed. A new receiver is now attached to the queue. Redelivery of the remaining
    * message is now attempted. The message should be redelivered.
    */
   @Test
   public void testSelectiveClosingConsumer() throws Exception {
      Connection conn = null;

      try {
         conn = getConnectionFactory().createConnection();
         conn.start();

         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer prod = session.createProducer(queue1);
         prod.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

         String selector = "color = 'red'";
         MessageConsumer redConsumer = session.createConsumer(queue1, selector);
         conn.start();

         Message redMessage = session.createMessage();
         redMessage.setStringProperty("color", "red");

         Message blueMessage = session.createMessage();
         blueMessage.setStringProperty("color", "blue");

         prod.send(redMessage);
         prod.send(blueMessage);

         log.debug("sent message");

         Message rec = redConsumer.receive();
         ProxyAssertSupport.assertEquals(redMessage.getJMSMessageID(), rec.getJMSMessageID());
         ProxyAssertSupport.assertEquals("red", rec.getStringProperty("color"));

         ProxyAssertSupport.assertNull(redConsumer.receiveNoWait());

         redConsumer.close();

         log.debug("closed first consumer");

         MessageConsumer universalConsumer = session.createConsumer(queue1);

         rec = universalConsumer.receive(2000);

         ProxyAssertSupport.assertEquals(rec.getJMSMessageID(), blueMessage.getJMSMessageID());
         ProxyAssertSupport.assertEquals("blue", rec.getStringProperty("color"));
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   @Test
   public void testManyTopic() throws Exception {
      String selector1 = "beatle = 'john'";

      Connection conn = null;

      try {
         conn = getConnectionFactory().createConnection();
         conn.start();

         Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageConsumer cons1 = sess.createConsumer(ActiveMQServerTestCase.topic1, selector1);

         MessageProducer prod = sess.createProducer(ActiveMQServerTestCase.topic1);

         for (int j = 0; j < 100; j++) {
            Message m = sess.createMessage();

            m.setStringProperty("beatle", "john");

            prod.send(m);

            m = sess.createMessage();

            m.setStringProperty("beatle", "kermit the frog");

            prod.send(m);
         }

         for (int j = 0; j < 100; j++) {
            Message m = cons1.receive(1000);

            ProxyAssertSupport.assertNotNull(m);
         }

         Thread.sleep(500);

         Message m = cons1.receiveNoWait();

         ProxyAssertSupport.assertNull(m);
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   @Test
   public void testManyQueue() throws Exception {
      String selector1 = "beatle = 'john'";

      Connection conn = null;

      try {
         conn = getConnectionFactory().createConnection();

         conn.start();

         Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageConsumer cons1 = sess.createConsumer(queue1, selector1);

         MessageProducer prod = sess.createProducer(queue1);

         for (int j = 0; j < 100; j++) {
            Message m = sess.createMessage();

            m.setStringProperty("beatle", "john");

            m.setIntProperty("wibble", j);

            prod.send(m);

            m = sess.createMessage();

            m.setStringProperty("beatle", "kermit the frog");

            m.setIntProperty("wibble", j);

            prod.send(m);
         }

         for (int j = 0; j < 100; j++) {
            Message m = cons1.receive(1000);

            ProxyAssertSupport.assertNotNull(m);

            Assert.assertEquals(j, m.getIntProperty("wibble"));

            ProxyAssertSupport.assertEquals("john", m.getStringProperty("beatle"));

            log.debug("Got message " + j);
         }

         Message m = cons1.receiveNoWait();

         ProxyAssertSupport.assertNull(m);

         String selector2 = "beatle = 'kermit the frog'";

         MessageConsumer cons2 = sess.createConsumer(queue1, selector2);

         for (int j = 0; j < 100; j++) {
            m = cons2.receive(100);

            ProxyAssertSupport.assertNotNull(m);

            Assert.assertEquals(j, m.getIntProperty("wibble"));

            ProxyAssertSupport.assertEquals("kermit the frog", m.getStringProperty("beatle"));
         }
      } finally {
         if (conn != null) {
            conn.close();
         }
      }

   }

   // http://jira.jboss.org/jira/browse/JBMESSAGING-775

   @Test
   public void testManyQueueWithExpired() throws Exception {
      String selector1 = "beatle = 'john'";

      Connection conn = null;

      try {
         conn = getConnectionFactory().createConnection();
         conn.start();

         Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         int NUM_MESSAGES = 2;

         MessageProducer prod = sess.createProducer(queue1);

         for (int j = 0; j < NUM_MESSAGES; j++) {
            Message m = sess.createMessage();

            m.setStringProperty("beatle", "john");

            prod.setTimeToLive(0);

            prod.send(m);

            m = sess.createMessage();

            m.setStringProperty("beatle", "john");

            prod.setTimeToLive(1);

            prod.send(m);

            m = sess.createMessage();

            m.setStringProperty("beatle", "kermit the frog");

            prod.setTimeToLive(0);

            prod.send(m);

            m = sess.createMessage();

            m.setStringProperty("beatle", "kermit the frog");

            m.setJMSExpiration(System.currentTimeMillis());

            prod.setTimeToLive(1);

            prod.send(m);
         }

         MessageConsumer cons1 = sess.createConsumer(queue1, selector1);

         for (int j = 0; j < NUM_MESSAGES; j++) {
            Message m = cons1.receive(100);

            ProxyAssertSupport.assertNotNull(m);

            ProxyAssertSupport.assertEquals("john", m.getStringProperty("beatle"));
         }

         Message m = cons1.receiveNoWait();

         ProxyAssertSupport.assertNull(m);

         String selector2 = "beatle = 'kermit the frog'";

         MessageConsumer cons2 = sess.createConsumer(queue1, selector2);

         for (int j = 0; j < NUM_MESSAGES; j++) {
            m = cons2.receive(1000);

            ProxyAssertSupport.assertNotNull(m);

            ProxyAssertSupport.assertEquals("kermit the frog", m.getStringProperty("beatle"));
         }

         m = cons1.receiveNoWait();

         ProxyAssertSupport.assertNull(m);
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   @Test
   public void testManyRedeliveriesTopic() throws Exception {
      String selector1 = "beatle = 'john'";

      Connection conn = null;

      try {
         conn = getConnectionFactory().createConnection();
         conn.start();

         for (int i = 0; i < 5; i++) {
            Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            MessageConsumer cons1 = sess.createConsumer(ActiveMQServerTestCase.topic1, selector1);

            MessageProducer prod = sess.createProducer(ActiveMQServerTestCase.topic1);

            for (int j = 0; j < 10; j++) {
               Message m = sess.createMessage();

               m.setStringProperty("beatle", "john");

               prod.send(m);

               m = sess.createMessage();

               m.setStringProperty("beatle", "kermit the frog");

               prod.send(m);
            }

            for (int j = 0; j < 10; j++) {
               Message m = cons1.receive(100);

               ProxyAssertSupport.assertNotNull(m);
            }

            Message m = cons1.receiveNoWait();

            ProxyAssertSupport.assertNull(m);

            sess.close();
         }
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   @Test
   public void testManyRedeliveriesQueue() throws Exception {
      String selector1 = "beatle = 'john'";

      Connection conn = null;

      try {
         conn = getConnectionFactory().createConnection();

         conn.start();

         for (int i = 0; i < 5; i++) {
            Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            MessageConsumer cons1 = sess.createConsumer(queue1, selector1);

            MessageProducer prod = sess.createProducer(queue1);

            for (int j = 0; j < 10; j++) {
               TextMessage m = sess.createTextMessage("message-a-" + j);

               m.setStringProperty("beatle", "john");

               prod.send(m);

               m = sess.createTextMessage("messag-b-" + j);

               m.setStringProperty("beatle", "kermit the frog");

               prod.send(m);
            }

            for (int j = 0; j < 10; j++) {
               Message m = cons1.receive(1000);

               ProxyAssertSupport.assertNotNull(m);
            }

            Message m = cons1.receiveNoWait();

            ProxyAssertSupport.assertNull(m);

            sess.close();
         }
      } finally {
         if (conn != null) {
            conn.close();
         }

         removeAllMessages(queue1.getQueueName(), true);
      }
   }

   @Test
   public void testWithSelector() throws Exception {
      String selector1 = "beatle = 'john'";
      String selector2 = "beatle = 'paul'";
      String selector3 = "beatle = 'george'";
      String selector4 = "beatle = 'ringo'";
      String selector5 = "beatle = 'jesus'";

      Connection conn = null;

      try {
         conn = getConnectionFactory().createConnection();
         conn.start();
         Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer cons1 = sess.createConsumer(ActiveMQServerTestCase.topic1, selector1);
         MessageConsumer cons2 = sess.createConsumer(ActiveMQServerTestCase.topic1, selector2);
         MessageConsumer cons3 = sess.createConsumer(ActiveMQServerTestCase.topic1, selector3);
         MessageConsumer cons4 = sess.createConsumer(ActiveMQServerTestCase.topic1, selector4);
         MessageConsumer cons5 = sess.createConsumer(ActiveMQServerTestCase.topic1, selector5);

         Message m1 = sess.createMessage();
         m1.setStringProperty("beatle", "john");

         Message m2 = sess.createMessage();
         m2.setStringProperty("beatle", "paul");

         Message m3 = sess.createMessage();
         m3.setStringProperty("beatle", "george");

         Message m4 = sess.createMessage();
         m4.setStringProperty("beatle", "ringo");

         Message m5 = sess.createMessage();
         m5.setStringProperty("beatle", "jesus");

         MessageProducer prod = sess.createProducer(ActiveMQServerTestCase.topic1);

         prod.send(m1);
         prod.send(m2);
         prod.send(m3);
         prod.send(m4);
         prod.send(m5);

         Message r1 = cons1.receive(500);
         ProxyAssertSupport.assertNotNull(r1);
         Message n = cons1.receiveNoWait();
         ProxyAssertSupport.assertNull(n);

         Message r2 = cons2.receive(500);
         ProxyAssertSupport.assertNotNull(r2);
         n = cons2.receiveNoWait();
         ProxyAssertSupport.assertNull(n);

         Message r3 = cons3.receive(500);
         ProxyAssertSupport.assertNotNull(r3);
         n = cons3.receiveNoWait();
         ProxyAssertSupport.assertNull(n);

         Message r4 = cons4.receive(500);
         ProxyAssertSupport.assertNotNull(r4);
         n = cons4.receiveNoWait();
         ProxyAssertSupport.assertNull(n);

         Message r5 = cons5.receive(500);
         ProxyAssertSupport.assertNotNull(r5);
         n = cons5.receiveNoWait();
         ProxyAssertSupport.assertNull(n);

         ProxyAssertSupport.assertEquals("john", r1.getStringProperty("beatle"));
         ProxyAssertSupport.assertEquals("paul", r2.getStringProperty("beatle"));
         ProxyAssertSupport.assertEquals("george", r3.getStringProperty("beatle"));
         ProxyAssertSupport.assertEquals("ringo", r4.getStringProperty("beatle"));
         ProxyAssertSupport.assertEquals("jesus", r5.getStringProperty("beatle"));
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   @Test
   public void testManyConsumersWithDifferentSelectors() throws Exception {
      Connection conn = null;

      try {
         conn = getConnectionFactory().createConnection();
         Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer p = sess.createProducer(queue1);

         Session cs = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         final MessageConsumer c = cs.createConsumer(queue1, "weight = 1");

         Session cs2 = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         final MessageConsumer c2 = cs2.createConsumer(queue1, "weight = 2");

         for (int i = 0; i < 10; i++) {
            Message m = sess.createTextMessage("message" + i);
            m.setIntProperty("weight", i % 2 + 1);
            p.send(m);
         }

         conn.start();

         final List<Message> received = new ArrayList<>();
         final List<Message> received2 = new ArrayList<>();
         final CountDownLatch latch = new CountDownLatch(1);
         final CountDownLatch latch2 = new CountDownLatch(1);

         new Thread(new Runnable() {
            @Override
            public void run() {
               try {
                  while (true) {
                     Message m = c.receive(1000);
                     if (m != null) {
                        received.add(m);
                     } else {
                        latch.countDown();
                        return;
                     }
                  }
               } catch (Exception e) {
                  log.error("receive failed", e);
               }
            }
         }, "consumer thread 1").start();

         new Thread(new Runnable() {
            @Override
            public void run() {
               try {
                  while (true) {
                     Message m = c2.receive(1000);
                     if (m != null) {
                        received2.add(m);
                     } else {
                        latch2.countDown();
                        return;
                     }
                  }
               } catch (Exception e) {
                  log.error("receive failed", e);
               }
            }
         }, "consumer thread 2").start();

         ActiveMQTestBase.waitForLatch(latch);
         ActiveMQTestBase.waitForLatch(latch2);

         ProxyAssertSupport.assertEquals(5, received.size());
         for (Message m : received) {
            int value = m.getIntProperty("weight");
            ProxyAssertSupport.assertEquals(value, 1);
         }

         ProxyAssertSupport.assertEquals(5, received2.size());
         for (Message m : received2) {
            int value = m.getIntProperty("weight");
            ProxyAssertSupport.assertEquals(value, 2);
         }
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   @Test
   public void testDeliveryModeOnSelector() throws Exception {
      Connection conn = null;

      try {
         conn = getConnectionFactory().createConnection();
         conn.start();

         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer prodNonPersistent = session.createProducer(queue1);
         prodNonPersistent.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

         MessageProducer prodPersistent = session.createProducer(queue1);
         prodPersistent.setDeliveryMode(DeliveryMode.PERSISTENT);

         String selector = "JMSDeliveryMode = 'PERSISTENT'";
         MessageConsumer persistentConsumer = session.createConsumer(queue1, selector);
         conn.start();

         TextMessage msg = session.createTextMessage("NonPersistent");
         prodNonPersistent.send(msg);

         msg = session.createTextMessage("Persistent");
         prodPersistent.send(msg);

         msg = (TextMessage) persistentConsumer.receive(2000);
         ProxyAssertSupport.assertNotNull(msg);
         ProxyAssertSupport.assertEquals(DeliveryMode.PERSISTENT, msg.getJMSDeliveryMode());
         ProxyAssertSupport.assertEquals("Persistent", msg.getText());

         ProxyAssertSupport.assertNull(persistentConsumer.receiveNoWait());

         persistentConsumer.close();

         MessageConsumer genericConsumer = session.createConsumer(queue1);
         msg = (TextMessage) genericConsumer.receive(1000);

         ProxyAssertSupport.assertNotNull(msg);

         ProxyAssertSupport.assertEquals("NonPersistent", msg.getText());
         ProxyAssertSupport.assertEquals(DeliveryMode.NON_PERSISTENT, msg.getJMSDeliveryMode());
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   @Test
   public void testJMSMessageIDOnSelector() throws Exception {
      Connection conn = null;

      try {
         conn = getConnectionFactory().createConnection();
         conn.start();

         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer prod = session.createProducer(queue1);

         TextMessage msg1 = session.createTextMessage("msg1");
         prod.send(msg1);

         TextMessage msg2 = session.createTextMessage("msg2");
         prod.send(msg2);

         String selector = "JMSMessageID = '" + msg2.getJMSMessageID() + "'";

         MessageConsumer cons = session.createConsumer(queue1, selector);

         conn.start();

         TextMessage rec = (TextMessage) cons.receive(10000);

         assertNotNull(rec);

         Assert.assertEquals("msg2", rec.getText());

         assertNull(cons.receiveNoWait());

      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   @Test
   public void testJMSPriorityOnSelector() throws Exception {
      Connection conn = null;

      try {
         conn = getConnectionFactory().createConnection();
         conn.start();

         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer prod = session.createProducer(queue1);

         TextMessage msg1 = session.createTextMessage("msg1");
         prod.send(msg1, DeliveryMode.NON_PERSISTENT, 8, 0);

         TextMessage msg2 = session.createTextMessage("msg2");
         prod.send(msg2, DeliveryMode.NON_PERSISTENT, 2, 0);

         String selector = "JMSPriority = 2";

         MessageConsumer cons = session.createConsumer(queue1, selector);

         conn.start();

         TextMessage rec = (TextMessage) cons.receive(10000);

         assertNotNull(rec);

         Assert.assertEquals("msg2", rec.getText());

         assertNull(cons.receiveNoWait());

      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   @Test
   public void testJMSTimestampOnSelector() throws Exception {
      Connection conn = null;

      try {
         conn = getConnectionFactory().createConnection();
         conn.start();

         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer prod = session.createProducer(queue1);

         TextMessage msg1 = session.createTextMessage("msg1");
         prod.send(msg1);

         Thread.sleep(2);

         TextMessage msg2 = session.createTextMessage("msg2");
         prod.send(msg2);

         String selector = "JMSTimestamp = " + msg2.getJMSTimestamp();

         MessageConsumer cons = session.createConsumer(queue1, selector);

         conn.start();

         TextMessage rec = (TextMessage) cons.receive(10000);

         assertNotNull(rec);

         Assert.assertEquals("msg2", rec.getText());

         assertNull(cons.receiveNoWait());

      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   @Test
   public void testJMSExpirationOnSelector() throws Exception {
      Connection conn = null;

      try {
         conn = getConnectionFactory().createConnection();
         conn.start();

         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer prod = session.createProducer(queue1);

         TextMessage msg1 = session.createTextMessage("msg1");
         prod.send(msg1);

         prod.setTimeToLive(100000);

         TextMessage msg2 = session.createTextMessage("msg2");

         prod.send(msg2);

         long expire = msg2.getJMSExpiration();

         String selector = "JMSExpiration = " + expire;

         MessageConsumer cons = session.createConsumer(queue1, selector);

         conn.start();

         TextMessage rec = (TextMessage) cons.receive(10000);

         assertNotNull(rec);

         Assert.assertEquals("msg2", rec.getText());

         assertNull(cons.receiveNoWait());

      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   @Test
   public void testJMSTypeOnSelector() throws Exception {
      Connection conn = null;

      try {
         conn = getConnectionFactory().createConnection();
         conn.start();

         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer prod = session.createProducer(queue1);

         TextMessage msg1 = session.createTextMessage("msg1");
         msg1.setJMSType("type1");
         prod.send(msg1);

         TextMessage msg2 = session.createTextMessage("msg2");
         msg2.setJMSType("type2");
         prod.send(msg2);

         String selector = "JMSType = 'type2'";

         MessageConsumer cons = session.createConsumer(queue1, selector);

         conn.start();

         TextMessage rec = (TextMessage) cons.receive(10000);

         assertNotNull(rec);

         Assert.assertEquals("msg2", rec.getText());

         assertNull(cons.receiveNoWait());

      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   @Test
   public void testJMSCorrelationIDOnSelector() throws Exception {
      Connection conn = null;

      try {
         conn = getConnectionFactory().createConnection();
         conn.start();

         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer prod = session.createProducer(queue1);

         TextMessage msg1 = session.createTextMessage("msg1");
         msg1.setJMSCorrelationID("cid1");
         prod.send(msg1);

         TextMessage msg2 = session.createTextMessage("msg2");
         msg2.setJMSCorrelationID("cid2");
         prod.send(msg2);

         String selector = "JMSCorrelationID = 'cid2'";

         MessageConsumer cons = session.createConsumer(queue1, selector);

         conn.start();

         TextMessage rec = (TextMessage) cons.receive(10000);

         assertNotNull(rec);

         Assert.assertEquals("msg2", rec.getText());

         assertNull(cons.receiveNoWait());

      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   // Test case proposed by a customer on this user forum:
   // http://community.jboss.org/thread/153426?tstart=0
   // This test needs to be moved away
   @Test
   @Ignore
   public void testMultipleConsumers() throws Exception {
      Connection conn = null;

      try {
         ConnectionFactory factory = getConnectionFactory();
         ActiveMQConnectionFactory hcf = (ActiveMQConnectionFactory) factory;

         hcf.setConsumerWindowSize(0);

         conn = hcf.createConnection();

         conn.setClientID("SomeClientID");

         conn.start();

         Session session = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         MessageProducer msgProducer = session.createProducer(queue1);

         TextMessage tm;
         /* Publish messages */
         tm = session.createTextMessage();
         tm.setText("1");
         tm.setStringProperty("PROP1", "VALUE1");
         msgProducer.send(tm);
         log.debug("Sent message with id [" + tm.getJMSMessageID() + "]");

         tm = session.createTextMessage();
         tm.setText("2");
         tm.setStringProperty("PROP1", "VALUE1");
         msgProducer.send(tm);
         log.debug("Sent message with id [" + tm.getJMSMessageID() + "]");

         tm = session.createTextMessage();
         tm.setText("3");
         tm.setStringProperty("PROP2", "VALUE2");
         msgProducer.send(tm);
         log.debug("Sent message with id [" + tm.getJMSMessageID() + "]");

         tm = session.createTextMessage();
         tm.setText("4");
         tm.setStringProperty("PROP2", "VALUE2");
         msgProducer.send(tm);
         log.debug("Sent message with id [" + tm.getJMSMessageID() + "]");

         tm = session.createTextMessage();
         tm.setText("5");
         tm.setStringProperty("PROP1", "VALUE1");
         msgProducer.send(tm);
         log.debug("Sent message with id [" + tm.getJMSMessageID() + "]");

         tm = session.createTextMessage();
         tm.setText("6");
         tm.setStringProperty("PROP1", "VALUE1");
         tm.setStringProperty("PROP2", "VALUE2");
         msgProducer.send(tm);
         log.debug("Sent message with id [" + tm.getJMSMessageID() + "]");
         msgProducer.close();
         msgProducer = null;

         MessageConsumer msgConsumer = session.createConsumer(queue1, "PROP2 = 'VALUE2'");

         tm = (TextMessage) msgConsumer.receive(5000);

         assertNotNull(tm);

         Assert.assertEquals("3", tm.getText());
         Assert.assertEquals("VALUE2", tm.getStringProperty("PROP2"));

         tm.acknowledge();

         conn.close(); // this should close the consumer, producer and session associated with the connection

         // Reopen the connection and consumer

         conn = getConnectionFactory().createConnection();
         conn.start();

         session = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         msgConsumer = session.createConsumer(queue1);

         tm = (TextMessage) msgConsumer.receive(5000);
         Assert.assertEquals("1", tm.getText());
         Assert.assertEquals("VALUE1", tm.getStringProperty("PROP1"));

         tm = (TextMessage) msgConsumer.receive(5000);
         Assert.assertEquals("2", tm.getText());
         Assert.assertEquals("VALUE1", tm.getStringProperty("PROP1"));

         tm = (TextMessage) msgConsumer.receive(5000);
         Assert.assertEquals("4", tm.getText());
         Assert.assertEquals("VALUE2", tm.getStringProperty("PROP2"));

         tm = (TextMessage) msgConsumer.receive(5000);
         Assert.assertEquals("5", tm.getText());
         Assert.assertEquals("VALUE1", tm.getStringProperty("PROP1"));

         tm = (TextMessage) msgConsumer.receive(5000);
         Assert.assertEquals("6", tm.getText());
         Assert.assertEquals("VALUE1", tm.getStringProperty("PROP1"));
         Assert.assertEquals("VALUE2", tm.getStringProperty("PROP2"));

         tm.acknowledge();

      } finally {
         if (conn != null) {
            conn.close();
         }
      }

   }


}
