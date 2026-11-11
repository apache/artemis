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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import javax.jms.CompletionListener;
import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.InvalidDestinationException;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import java.io.Serializable;
import java.util.EnumSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.tests.message.SimpleJMSMessage;
import org.apache.activemq.artemis.jms.tests.message.SimpleJMSTextMessage;
import org.apache.activemq.artemis.jms.tests.util.ProxyAssertSupport;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class MessageProducerTest extends JMSTestCase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Test
   public void testSendForeignWithForeignDestinationSet() throws Exception {
      Connection conn = createConnection();

      Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageProducer p = sess.createProducer(queue1);

      MessageConsumer c = sess.createConsumer(queue1);

      conn.start();

      Message foreign = new SimpleJMSMessage(new SimpleDestination());

      foreign.setJMSDestination(new SimpleDestination());

      // the producer destination should override the foreign destination and the send should succeed

      p.send(foreign);

      Message m = c.receive(1000);

      ProxyAssertSupport.assertNotNull(m);
   }

   private static class SimpleDestination implements Destination, Serializable {

      private static final long serialVersionUID = -2553676986492799801L;
   }

   @Test
   public void testSendToQueuePersistent() throws Exception {
      sendToQueue(true);
   }

   @Test
   public void testSendToQueueNonPersistent() throws Exception {
      sendToQueue(false);
   }

   private void sendToQueue(final boolean persistent) throws Exception {
      Connection pconn = null;
      Connection cconn = null;

      try {
         pconn = createConnection();
         cconn = createConnection();

         Session ps = pconn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session cs = cconn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer p = ps.createProducer(queue1);
         MessageConsumer c = cs.createConsumer(queue1);

         cconn.start();

         TextMessage m = ps.createTextMessage("test");
         p.send(m);

         TextMessage r = (TextMessage) c.receive(3000);

         ProxyAssertSupport.assertEquals(m.getJMSMessageID(), r.getJMSMessageID());
         ProxyAssertSupport.assertEquals("test", r.getText());
      } finally {
         if (pconn != null) {
            pconn.close();
         }
         if (cconn != null) {
            cconn.close();
         }
      }
   }

   @Test
   public void testTransactedSendPersistent() throws Exception {
      transactedSend(true);
   }

   @Test
   public void testTransactedSendNonPersistent() throws Exception {
      transactedSend(false);
   }

   private void transactedSend(final boolean persistent) throws Exception {
      Connection pconn = null;
      Connection cconn = null;

      try {
         pconn = createConnection();
         cconn = createConnection();

         cconn.start();

         Session ts = pconn.createSession(true, -1);
         Session cs = cconn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer p = ts.createProducer(queue1);
         MessageConsumer c = cs.createConsumer(queue1);

         TextMessage m = ts.createTextMessage("test");
         p.send(m);

         ts.commit();

         TextMessage r = (TextMessage) c.receive();

         ProxyAssertSupport.assertEquals(m.getJMSMessageID(), r.getJMSMessageID());
         ProxyAssertSupport.assertEquals("test", r.getText());
      } finally {
         pconn.close();
         cconn.close();
      }
   }

   // I moved this into it's own class so we can catch any exception that occurs
   // Since this test intermittently fails.
   // (As an aside, technically this test is invalid anyway since the sessions is used for sending
   // and consuming concurrently - and sessions are supposed to be single threaded)
   private class Sender implements Runnable {

      volatile Exception ex;

      MessageProducer prod;

      Message m;

      Sender(final MessageProducer prod, final Message m) {
         this.prod = prod;

         this.m = m;
      }

      @Override
      public synchronized void run() {
         try {
            prod.send(m);
         } catch (Exception e) {
            logger.error(e.getMessage(), e);

            ex = e;
         }
      }
   }

   @Test
   public void testPersistentSendToTopic() throws Exception {
      sendToTopic(true);
   }

   @Test
   public void testNonPersistentSendToTopic() throws Exception {
      sendToTopic(false);
   }

   private void sendToTopic(final boolean persistent) throws Exception {

      Connection pconn = createConnection();
      Connection cconn = createConnection();

      try {
         Session ps = pconn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session cs = cconn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         final MessageProducer p = ps.createProducer(ActiveMQServerTestCase.topic1);

         p.setDeliveryMode(persistent ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT);

         MessageConsumer c = cs.createConsumer(ActiveMQServerTestCase.topic1);

         cconn.start();

         TextMessage m1 = ps.createTextMessage("test");

         Sender sender = new Sender(p, m1);

         Thread t = new Thread(sender, "Producer Thread");

         t.start();

         TextMessage m2 = (TextMessage) c.receive(5000);

         if (sender.ex != null) {
            // If an exception was caught in sending we rethrow here so as not to lose it
            throw sender.ex;
         }

         ProxyAssertSupport.assertEquals(m2.getJMSMessageID(), m1.getJMSMessageID());
         ProxyAssertSupport.assertEquals("test", m2.getText());

         t.join();
      } finally {
         pconn.close();
         cconn.close();
      }
   }

   /**
    * Test sending via anonymous producer
    */
   @Test
   public void testSendDestination() throws Exception {
      Connection pconn = createConnection();
      Connection cconn = createConnection();

      try {
         Session ps = pconn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session cs = cconn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer c2 = cs.createConsumer(ActiveMQServerTestCase.topic2);
         final Message m1 = ps.createMessage();

         cconn.start();

         final MessageProducer anonProducer = ps.createProducer(null);

         new Thread(() -> {
            try {
               anonProducer.send(ActiveMQServerTestCase.topic2, m1);
            } catch (Exception e) {
               logger.error(e.getMessage(), e);
            }
         }, "Producer Thread").start();

         Message m2 = c2.receive(3000);
         ProxyAssertSupport.assertEquals(m1.getJMSMessageID(), m2.getJMSMessageID());

         logger.debug("ending test");
      } finally {
         pconn.close();
         cconn.close();
      }
   }

   @Test
   public void testSendForeignMessage() throws Exception {
      Connection pconn = createConnection();
      Connection cconn = createConnection();

      try {
         Session ps = pconn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session cs = cconn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer p = ps.createProducer(queue1);
         MessageConsumer c = cs.createConsumer(queue1);

         // send a message that is not created by the session

         cconn.start();

         Message m = new SimpleJMSTextMessage("something");
         p.send(m);

         TextMessage rec = (TextMessage) c.receive(3000);

         ProxyAssertSupport.assertEquals("something", rec.getText());

      } finally {
         pconn.close();
         cconn.close();
      }
   }

   @Test
   public void testGetDestination() throws Exception {
      Connection pconn = createConnection();

      try {
         Session ps = pconn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer p = ps.createProducer(ActiveMQServerTestCase.topic1);
         Destination dest = p.getDestination();
         ProxyAssertSupport.assertEquals(dest, ActiveMQServerTestCase.topic1);
      } finally {
         pconn.close();
      }
   }

   @Test
   public void testGetDestinationOnClosedProducer() throws Exception {
      Connection pconn = createConnection();

      try {
         Session ps = pconn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer p = ps.createProducer(ActiveMQServerTestCase.topic1);
         p.close();

         try {
            p.getDestination();
            ProxyAssertSupport.fail("should throw exception");
         } catch (javax.jms.IllegalStateException e) {
            // OK
         }
      } finally {
         pconn.close();
      }
   }

   @Test
   public void testCreateProducerOnInexistentDestination() throws Exception {
      getJmsServer().getAddressSettingsRepository().addMatch("#", new AddressSettings().setAutoCreateQueues(false));
      getJmsServer().getAddressSettingsRepository().addMatch("#", new AddressSettings().setAutoCreateAddresses(false));
      Connection pconn = createConnection();
      try {
         Session ps = pconn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         try {
            ps.createProducer(ActiveMQJMSClient.createTopic("NoSuchTopic"));
            ProxyAssertSupport.fail("should throw exception");
         } catch (InvalidDestinationException e) {
            // OK
         }
      } finally {
         pconn.close();
      }
   }

   //
   // disabled MessageID tests
   //

   @Test
   public void testGetDisableMessageID() throws Exception {
      Connection pconn = createConnection();

      try {
         Session ps = pconn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer p = ps.createProducer(ActiveMQServerTestCase.topic1);

         ProxyAssertSupport.assertFalse(p.getDisableMessageID());
      } finally {
         pconn.close();
      }
   }

   @Test
   public void testGetDisableMessageIDOnClosedProducer() throws Exception {
      Connection pconn = createConnection();

      try {
         Session ps = pconn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer p = ps.createProducer(ActiveMQServerTestCase.topic1);

         p.close();

         try {
            p.getDisableMessageID();
            ProxyAssertSupport.fail("should throw exception");
         } catch (javax.jms.IllegalStateException e) {
            // OK
         }
      } finally {
         pconn.close();
      }
   }

   //
   // disabled timestamp tests
   //

   @Test
   public void testDefaultTimestampDisabled() throws Exception {
      Connection pconn = createConnection();

      try {
         Session ps = pconn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer tp = ps.createProducer(ActiveMQServerTestCase.topic1);
         MessageProducer qp = ps.createProducer(queue1);
         ProxyAssertSupport.assertFalse(tp.getDisableMessageTimestamp());
         ProxyAssertSupport.assertFalse(qp.getDisableMessageTimestamp());
      } finally {
         pconn.close();
      }
   }

   @Test
   public void testSetTimestampDisabled() throws Exception {
      Connection pconn = createConnection();
      Connection cconn = createConnection();

      try {
         Session ps = pconn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session cs = cconn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer p = ps.createProducer(queue1);
         MessageConsumer c = cs.createConsumer(queue1);

         cconn.start();

         p.setDisableMessageTimestamp(true);
         ProxyAssertSupport.assertTrue(p.getDisableMessageTimestamp());

         p.send(ps.createMessage());

         Message m = c.receive(3000);

         ProxyAssertSupport.assertEquals(0L, m.getJMSTimestamp());

         p.setDisableMessageTimestamp(false);
         ProxyAssertSupport.assertFalse(p.getDisableMessageTimestamp());

         long t1 = System.currentTimeMillis();

         p.send(ps.createMessage());

         m = c.receive(3000);

         long t2 = System.currentTimeMillis();
         long timestamp = m.getJMSTimestamp();

         ProxyAssertSupport.assertTrue(timestamp >= t1);
         ProxyAssertSupport.assertTrue(timestamp <= t2);
      } finally {
         pconn.close();
         cconn.close();
      }
   }

   @Test
   public void testGetTimestampDisabledOnClosedProducer() throws Exception {
      Connection pconn = createConnection();

      try {
         Session ps = pconn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer p = ps.createProducer(ActiveMQServerTestCase.topic1);

         p.close();

         try {
            p.getDisableMessageTimestamp();
            ProxyAssertSupport.fail("should throw exception");
         } catch (javax.jms.IllegalStateException e) {
            // OK
         }
      } finally {
         pconn.close();
      }
   }

   //
   // DeliverMode tests
   //

   @Test
   public void testDefaultDeliveryMode() throws Exception {
      Connection pconn = createConnection();

      try {
         Session ps = pconn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer tp = ps.createProducer(ActiveMQServerTestCase.topic1);
         MessageProducer qp = ps.createProducer(queue1);

         ProxyAssertSupport.assertEquals(DeliveryMode.PERSISTENT, tp.getDeliveryMode());
         ProxyAssertSupport.assertEquals(DeliveryMode.PERSISTENT, qp.getDeliveryMode());
      } finally {
         pconn.close();
      }
   }

   @Test
   public void testSetDeliveryMode() throws Exception {
      Connection pconn = createConnection();

      try {
         Session ps = pconn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer p = ps.createProducer(ActiveMQServerTestCase.topic1);

         p.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
         ProxyAssertSupport.assertEquals(DeliveryMode.NON_PERSISTENT, p.getDeliveryMode());

         p.setDeliveryMode(DeliveryMode.PERSISTENT);
         ProxyAssertSupport.assertEquals(DeliveryMode.PERSISTENT, p.getDeliveryMode());
      } finally {
         pconn.close();
      }
   }

   @Test
   public void testGetDeliveryModeOnClosedProducer() throws Exception {
      Connection pconn = createConnection();

      try {
         Session ps = pconn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer p = ps.createProducer(ActiveMQServerTestCase.topic1);

         p.close();

         try {
            p.getDeliveryMode();
            ProxyAssertSupport.fail("should throw exception");
         } catch (javax.jms.IllegalStateException e) {
            // OK
         }
      } finally {
         pconn.close();
      }
   }

   //
   // Priority tests
   //

   @Test
   public void testDefaultPriority() throws Exception {
      Connection pconn = createConnection();

      try {
         Session ps = pconn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer tp = ps.createProducer(ActiveMQServerTestCase.topic1);
         MessageProducer qp = ps.createProducer(queue1);

         ProxyAssertSupport.assertEquals(4, tp.getPriority());
         ProxyAssertSupport.assertEquals(4, qp.getPriority());
      } finally {
         pconn.close();
      }
   }

   @Test
   public void testSetPriority() throws Exception {
      Connection pconn = createConnection();

      try {
         Session ps = pconn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer p = ps.createProducer(ActiveMQServerTestCase.topic1);

         p.setPriority(9);
         ProxyAssertSupport.assertEquals(9, p.getPriority());

         p.setPriority(0);
         ProxyAssertSupport.assertEquals(0, p.getPriority());
      } finally {
         pconn.close();
      }
   }

   @Test
   public void testGetPriorityOnClosedProducer() throws Exception {
      Connection pconn = createConnection();

      try {
         Session ps = pconn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer p = ps.createProducer(ActiveMQServerTestCase.topic1);

         p.close();

         try {
            p.getPriority();
            ProxyAssertSupport.fail("should throw exception");
         } catch (javax.jms.IllegalStateException e) {
            // OK
         }
      } finally {
         pconn.close();
      }
   }

   //
   // TimeToLive test
   //

   @Test
   public void testDefaultTimeToLive() throws Exception {
      Connection pconn = createConnection();
      Session ps = pconn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageProducer tp = ps.createProducer(ActiveMQServerTestCase.topic1);
      MessageProducer qp = ps.createProducer(queue1);

      ProxyAssertSupport.assertEquals(0L, tp.getTimeToLive());
      ProxyAssertSupport.assertEquals(0L, qp.getTimeToLive());
   }

   @Test
   public void testSetTimeToLive() throws Exception {
      Connection pconn = createConnection();
      Session ps = pconn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageProducer p = ps.createProducer(ActiveMQServerTestCase.topic1);

      p.setTimeToLive(100L);
      ProxyAssertSupport.assertEquals(100L, p.getTimeToLive());

      p.setTimeToLive(0L);
      ProxyAssertSupport.assertEquals(0L, p.getTimeToLive());
   }

   @Test
   public void testGetTimeToLiveOnClosedProducer() throws Exception {
      Connection pconn = createConnection();

      Session ps = pconn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageProducer p = ps.createProducer(ActiveMQServerTestCase.topic1);

      p.close();

      try {
         p.setTimeToLive(100L);
         ProxyAssertSupport.fail("should throw exception");
      } catch (javax.jms.IllegalStateException e) {
         // OK
      }
   }

   @Test
   public void testProducerCloseInCompletionListener() throws Exception {
      Connection pconn = createConnection();

      Session ps = pconn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageProducer p = ps.createProducer(ActiveMQServerTestCase.topic1);

      CountDownLatch latch = new CountDownLatch(1);
      CloseCompletionListener listener = new CloseCompletionListener(p, latch);

      p.send(ps.createMessage(), DeliveryMode.NON_PERSISTENT, Message.DEFAULT_PRIORITY, 0L, listener);

      ProxyAssertSupport.assertTrue(latch.await(5, TimeUnit.SECONDS));

      ProxyAssertSupport.assertNotNull(listener.exception);

      ProxyAssertSupport.assertTrue(listener.exception instanceof javax.jms.IllegalStateException);
   }

   @Test
   public void testConnectionCloseInCompletionListener() throws Exception {
      Connection pconn = createConnection();

      Session ps = pconn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageProducer p = ps.createProducer(ActiveMQServerTestCase.topic1);

      CountDownLatch latch = new CountDownLatch(1);
      ConnectionCloseCompletionListener listener = new ConnectionCloseCompletionListener(pconn, latch);

      p.send(ps.createMessage(), DeliveryMode.NON_PERSISTENT, Message.DEFAULT_PRIORITY, 0L, listener);

      ProxyAssertSupport.assertTrue(latch.await(5, TimeUnit.SECONDS));

      ProxyAssertSupport.assertNotNull(listener.exception);

      ProxyAssertSupport.assertTrue(listener.exception instanceof javax.jms.IllegalStateException);
   }

   @Test
   public void testSessionCloseInCompletionListener() throws Exception {
      Connection pconn = createConnection();

      Session ps = pconn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageProducer p = ps.createProducer(ActiveMQServerTestCase.topic1);

      CountDownLatch latch = new CountDownLatch(1);
      SessionCloseCompletionListener listener = new SessionCloseCompletionListener(ps, latch);

      p.send(ps.createMessage(), DeliveryMode.NON_PERSISTENT, Message.DEFAULT_PRIORITY, 0L, listener);

      ProxyAssertSupport.assertTrue(latch.await(5, TimeUnit.SECONDS));

      ProxyAssertSupport.assertNotNull(listener.exception);

      ProxyAssertSupport.assertTrue(listener.exception instanceof javax.jms.IllegalStateException);
   }

   @Test
   public void testSendToQueueOnlyWhenTopicWithSameAddress() throws Exception {
      SimpleString addr = SimpleString.of("testAddr");

      EnumSet<RoutingType> supportedRoutingTypes = EnumSet.of(RoutingType.ANYCAST, RoutingType.MULTICAST);

      servers.get(0).getActiveMQServer().addAddressInfo(new AddressInfo(addr, supportedRoutingTypes));
      servers.get(0).getActiveMQServer().createQueue(QueueConfiguration.of(addr).setRoutingType(RoutingType.ANYCAST).setDurable(false));

      Connection pconn = createConnection();
      pconn.start();

      Session ps = pconn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      Queue queue = ps.createQueue(addr.toString());
      Topic topic = ps.createTopic(addr.toString());

      MessageConsumer queueConsumer = ps.createConsumer(queue);
      MessageConsumer topicConsumer = ps.createConsumer(topic);

      MessageProducer queueProducer = ps.createProducer(queue);
      queueProducer.send(ps.createMessage());

      assertNotNull(queueConsumer.receive(1000));
      assertNull(topicConsumer.receiveNoWait());

      MessageProducer topicProducer = ps.createProducer(topic);
      topicProducer.send(ps.createMessage());

      assertNull(queueConsumer.receiveNoWait());
      assertNotNull(topicConsumer.receive(1000));
   }


   private static class CloseCompletionListener implements CompletionListener {

      private MessageProducer p;
      private CountDownLatch latch;
      private JMSException exception;

      private CloseCompletionListener(MessageProducer p, CountDownLatch latch) {
         this.p = p;
         this.latch = latch;
      }

      @Override
      public void onCompletion(Message message) {
         try {
            p.close();
         } catch (JMSException e) {
            this.exception = e;
         }
         latch.countDown();
      }

      @Override
      public void onException(Message message, Exception exception) {
      }
   }

   private static class ConnectionCloseCompletionListener implements CompletionListener {

      private CountDownLatch latch;
      private JMSException exception;
      private Connection conn;

      private ConnectionCloseCompletionListener(Connection conn, CountDownLatch latch) {
         this.conn = conn;
         this.latch = latch;
      }

      @Override
      public void onCompletion(Message message) {
         try {
            conn.close();
         } catch (JMSException e) {
            this.exception = e;
         }
         latch.countDown();
      }

      @Override
      public void onException(Message message, Exception exception) {
      }
   }

   private static class SessionCloseCompletionListener implements CompletionListener {

      private CountDownLatch latch;
      private JMSException exception;
      private Session session;

      private SessionCloseCompletionListener(Session session, CountDownLatch latch) {
         this.session = session;
         this.latch = latch;
      }

      @Override
      public void onCompletion(Message message) {
         try {
            session.close();
         } catch (JMSException e) {
            this.exception = e;
         }
         latch.countDown();
      }

      @Override
      public void onException(Message message, Exception exception) {
      }
   }
}
