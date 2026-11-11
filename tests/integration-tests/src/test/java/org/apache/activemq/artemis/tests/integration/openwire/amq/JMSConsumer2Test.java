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
package org.apache.activemq.artemis.tests.integration.openwire.amq;

import javax.jms.IllegalStateException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import java.io.IOException;
import java.net.Socket;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQMessageConsumer;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.tests.integration.openwire.BasicOpenWireTest;
import org.apache.activemq.artemis.utils.ActiveMQThreadFactory;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.command.ActiveMQDestination;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.util.IdGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * adapted from: org.apache.activemq.JMSConsumerTest
 */
public class JMSConsumer2Test extends BasicOpenWireTest {

   @Test
   public void testMessageListenerWithConsumerCanBeStoppedConcurently() throws Exception {

      final AtomicInteger counter = new AtomicInteger(0);
      final CountDownLatch closeDone = new CountDownLatch(1);

      connection.start();
      Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
      ActiveMQDestination destination = createDestination(session, ActiveMQDestination.QUEUE_TYPE);

      // preload the queue
      sendMessages(session, destination, 2000);

      final ActiveMQMessageConsumer consumer = (ActiveMQMessageConsumer) session.createConsumer(destination);

      final Map<Thread, Throwable> exceptions = Collections.synchronizedMap(new HashMap<>());
      Thread.setDefaultUncaughtExceptionHandler((t, e) -> exceptions.put(t, e));

      final class AckAndClose implements Runnable {

         private final Message message;

         AckAndClose(Message m) {
            this.message = m;
         }

         @Override
         public void run() {
            try {
               int count = counter.incrementAndGet();
               if (count == 590) {
                  // close in a separate thread is ok by jms
                  consumer.close();
                  closeDone.countDown();
               }
               if (count % 200 == 0) {
                  // ensure there are some outstanding messages
                  // ack every 200
                  message.acknowledge();
               }
            } catch (Exception e) {
               e.printStackTrace();
               if (!(e instanceof IllegalStateException)) { // The consumer is closed may happen
                  exceptions.put(Thread.currentThread(), e);
               }
            }
         }
      }

      final ExecutorService executor = Executors.newCachedThreadPool(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName()));
      consumer.setMessageListener(m -> {
         // ack and close eventually in separate thread
         executor.execute(new AckAndClose(m));
      });

      assertTrue(closeDone.await(20, TimeUnit.SECONDS));
      // await possible exceptions
      Thread.sleep(1000);
      assertTrue(exceptions.isEmpty(), "no exceptions: " + exceptions);
      executor.shutdown();
   }

   @Test
   public void testDupsOkConsumer() throws Exception {

      // Receive a message with the JMS API
      connection.start();
      Session session = connection.createSession(false, Session.DUPS_OK_ACKNOWLEDGE);
      ActiveMQDestination destination = createDestination(session, ActiveMQDestination.QUEUE_TYPE);
      MessageConsumer consumer = session.createConsumer(destination);

      // Send the messages
      sendMessages(session, destination, 4);

      // Make sure only 4 message are delivered.
      for (int i = 0; i < 4; i++) {
         Message m = consumer.receive(1000);
         assertNotNull(m);
      }
      assertNull(consumer.receive(1000));

      // Close out the consumer.. no other messages should be left on the queue.
      consumer.close();

      consumer = session.createConsumer(destination);
      assertNull(consumer.receive(1000));
   }

   @Test
   public void testRedispatchOfUncommittedTx() throws Exception {
      connection.start();
      Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
      ActiveMQDestination destination = createDestination(session, ActiveMQDestination.QUEUE_TYPE);

      sendMessages(connection, destination, 2);

      MessageConsumer consumer = session.createConsumer(destination);
      Message m = consumer.receive(1000);
      assertNotNull(m);
      m = consumer.receive(5000);
      assertNotNull(m);
      assertFalse(m.getJMSRedelivered(), "redelivered flag set");

      // install another consumer while message dispatch is unacked/uncommitted
      Session redispatchSession = connection.createSession(true, Session.SESSION_TRANSACTED);
      MessageConsumer redispatchConsumer = redispatchSession.createConsumer(destination);

      // no commit so will auto rollback and get re-dispatched to
      // redisptachConsumer
      session.close();

      Message msg = redispatchConsumer.receive(3000);
      assertNotNull(msg);

      assertTrue(msg.getJMSRedelivered(), "redelivered flag set");
      assertEquals(2, msg.getLongProperty("JMSXDeliveryCount"));

      msg = redispatchConsumer.receive(1000);
      assertNotNull(msg);
      assertTrue(msg.getJMSRedelivered());
      assertEquals(2, msg.getLongProperty("JMSXDeliveryCount"));
      redispatchSession.commit();

      assertNull(redispatchConsumer.receive(500));
      redispatchSession.close();
   }

   @Test
   public void testRedispatchOfRolledbackTx() throws Exception {

      connection.start();
      Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
      ActiveMQDestination destination = createDestination(session, ActiveMQDestination.QUEUE_TYPE);

      sendMessages(connection, destination, 2);

      MessageConsumer consumer = session.createConsumer(destination);
      assertNotNull(consumer.receive(1000));
      assertNotNull(consumer.receive(1000));

      // install another consumer while message dispatch is unacked/uncommitted
      Session redispatchSession = connection.createSession(true, Session.SESSION_TRANSACTED);
      MessageConsumer redispatchConsumer = redispatchSession.createConsumer(destination);

      session.rollback();
      session.close();

      Message msg = redispatchConsumer.receive(1000);
      assertNotNull(msg);
      assertTrue(msg.getJMSRedelivered());
      assertEquals(2, msg.getLongProperty("JMSXDeliveryCount"));
      msg = redispatchConsumer.receive(1000);
      assertNotNull(msg);
      assertTrue(msg.getJMSRedelivered());
      assertEquals(2, msg.getLongProperty("JMSXDeliveryCount"));
      redispatchSession.commit();

      assertNull(redispatchConsumer.receive(500));
      redispatchSession.close();
   }

   @Test
   public void testRedeliveryOnServerConnectionFailWithPendingAckInLocalTx() throws Exception {
      // Send a message to the broker.
      connection.start();
      sendMessages(connection, new ActiveMQQueue(queueName), 1);
      connection.close();

      factory.setWatchTopicAdvisories(false);
      factory.setNonBlockingRedelivery(true);
      connection = (ActiveMQConnection) factory.createConnection();
      connection.start();

      Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
      ActiveMQDestination destination = createDestination(session, ActiveMQDestination.QUEUE_TYPE);
      MessageConsumer consumer = session.createConsumer(destination);

      final CountDownLatch gotMessage = new CountDownLatch(1);
      consumer.setMessageListener(message -> gotMessage.countDown());

      assertTrue(gotMessage.await(1, TimeUnit.SECONDS));

      // want to ensure the ack has had a chance to get back to the broker
      final Queue queueInstance = server.locateQueue(SimpleString.of(queueName));
      Wait.waitFor(() -> queueInstance.getAcknowledgeAttempts() > 0);

      // whack the connection so there is no transaction outcome
      try {
         connection.getTransport().narrow(Socket.class).close();
      } catch (IOException e) {
         e.printStackTrace();
      }
      try {
         connection.close();
      } catch (Exception expected) {
      }

      // expect rollback and redelivery on new consumer
      connection = (ActiveMQConnection) factory.createConnection();
      connection.start();

      session = connection.createSession(true, Session.SESSION_TRANSACTED);
      consumer = session.createConsumer(destination);

      assertNotNull(consumer.receive(2000));
      session.commit();

      connection.close();
   }

   @Test
   public void testSelectorWithJMSMessageID() throws Exception {
      Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
      ActiveMQDestination destination = createDestination(session, ActiveMQDestination.QUEUE_TYPE);
      sendMessages(connection, destination, 1);
      /*
       * The OpenWire client uses the hostname in the JMSMessageID so the test
       * uses the same method for the selector so that the test will work on
       * any host.
       */
      MessageConsumer consumer = session.createConsumer(destination, "JMSMessageID like '%" + IdGenerator.getHostName() + "%'");
      connection.start();
      Message m = consumer.receive(500);
      assertNotNull(m);
   }
}
