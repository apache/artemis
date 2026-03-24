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
package org.apache.activemq.artemis.tests.integration.cluster.reattach;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQNotConnectedException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.MessageHandler;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryImpl;
import org.apache.activemq.artemis.core.client.impl.ClientSessionInternal;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMRegistry;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.jms.client.ActiveMQTextMessage;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RetryRule;
import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class RandomReattachTest extends ActiveMQTestBase {

   @Rule
   public RetryRule retryRule = new RetryRule(2);

   private static final Logger log = Logger.getLogger(RandomReattachTest.class);


   private static final int RECEIVE_TIMEOUT = 10000;


   private static final SimpleString ADDRESS = new SimpleString("FailoverTestAddress");

   private ActiveMQServer server;

   private Timer timer;



   @Test
   public void testA() throws Exception {
      runTest(new RunnableT() {
         @Override
         public void run(final ClientSessionFactory sf) throws Exception {
            doTestA(sf);
         }
      });
   }

   @Test
   public void testB() throws Exception {
      runTest(new RunnableT() {
         @Override
         public void run(final ClientSessionFactory sf) throws Exception {
            doTestB(sf);
         }
      });
   }

   @Test
   public void testC() throws Exception {
      runTest(new RunnableT() {
         @Override
         public void run(final ClientSessionFactory sf) throws Exception {
            doTestC(sf);
         }
      });
   }

   @Test
   public void testD() throws Exception {
      runTest(new RunnableT() {
         @Override
         public void run(final ClientSessionFactory sf) throws Exception {
            doTestD(sf);
         }
      });
   }

   @Test
   public void testE() throws Exception {
      runTest(new RunnableT() {
         @Override
         public void run(final ClientSessionFactory sf) throws Exception {
            doTestE(sf);
         }
      });
   }

   @Test
   public void testF() throws Exception {
      runTest(new RunnableT() {
         @Override
         public void run(final ClientSessionFactory sf) throws Exception {
            doTestF(sf);
         }
      });
   }

   @Test
   public void testG() throws Exception {
      runTest(new RunnableT() {
         @Override
         public void run(final ClientSessionFactory sf) throws Exception {
            doTestG(sf);
         }
      });
   }

   @Test
   public void testH() throws Exception {
      runTest(new RunnableT() {
         @Override
         public void run(final ClientSessionFactory sf) throws Exception {
            doTestH(sf);
         }
      });
   }

   @Test
   public void testI() throws Exception {
      runTest(new RunnableT() {
         @Override
         public void run(final ClientSessionFactory sf) throws Exception {
            doTestI(sf);
         }
      });
   }

   @Test
   public void testJ() throws Exception {
      runTest(new RunnableT() {
         @Override
         public void run(final ClientSessionFactory sf) throws Exception {
            doTestJ(sf);
         }
      });
   }

   @Test
   public void testK() throws Exception {
      runTest(new RunnableT() {
         @Override
         public void run(final ClientSessionFactory sf) throws Exception {
            doTestK(sf);
         }
      });
   }

   @Test
   public void testL() throws Exception {
      runTest(new RunnableT() {
         @Override
         public void run(final ClientSessionFactory sf) throws Exception {
            doTestL(sf);
         }
      });
   }

   @Test
   public void testN() throws Exception {
      runTest(new RunnableT() {
         @Override
         public void run(final ClientSessionFactory sf) throws Exception {
            doTestN(sf);
         }
      });
   }

   public void runTest(final RunnableT runnable) throws Exception {
      final int numIts = getNumIterations();

      for (int its = 0; its < numIts; its++) {
         log.debug("####" + getName() + " iteration #" + its);
         start();
         ServerLocator locator = createInVMNonHALocator().setReconnectAttempts(15).setConfirmationWindowSize(1024 * 1024);

         ClientSessionFactory sf = createSessionFactory(locator);

         ClientSession session = sf.createSession(false, false, false);

         Failer failer = startFailer(1000, session);

         do {
            runnable.run(sf);
         }
         while (!failer.isExecuted());
      }
   }



   protected void doTestA(final ClientSessionFactory sf) throws Exception {
      long start = System.currentTimeMillis();

      ClientSession s = sf.createSession(false, false, false);

      final int numMessages = 100;

      final int numSessions = 10;

      Set<ClientConsumer> consumers = new HashSet<>();
      Set<ClientSession> sessions = new HashSet<>();

      for (int i = 0; i < numSessions; i++) {
         SimpleString subName = new SimpleString("sub" + i);

         ClientSession sessConsume = sf.createSession(false, true, true);

         sessConsume.start();

         sessConsume.createQueue(new QueueConfiguration(subName).setAddress(RandomReattachTest.ADDRESS).setDurable(false));

         ClientConsumer consumer = sessConsume.createConsumer(subName);

         consumers.add(consumer);

         sessions.add(sessConsume);
      }

      ClientSession sessSend = sf.createSession(false, true, true);

      ClientProducer producer = sessSend.createProducer(RandomReattachTest.ADDRESS);

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = sessSend.createMessage(ActiveMQTextMessage.TYPE, false, 0, System.currentTimeMillis(), (byte) 1);
         message.putIntProperty(new SimpleString("count"), i);
         producer.send(message);
      }

      class MyHandler extends AssertionCheckMessageHandler {

         final CountDownLatch latch = new CountDownLatch(1);

         int count;

         @Override
         public void onMessageAssert(final ClientMessage message) {
            if (count == numMessages) {
               Assert.fail("Too many messages");
            }

            Assert.assertEquals(count, message.getObjectProperty(new SimpleString("count")));

            count++;

            try {
               message.acknowledge();
            } catch (ActiveMQException me) {
               RandomReattachTest.log.error("Failed to process", me);
            }

            if (count == numMessages) {
               latch.countDown();
            }
         }
      }

      Set<MyHandler> handlers = new HashSet<>();

      for (ClientConsumer consumer : consumers) {
         MyHandler handler = new MyHandler();

         consumer.setMessageHandler(handler);

         handlers.add(handler);
      }

      for (MyHandler handler : handlers) {
         boolean ok = handler.latch.await(5000, TimeUnit.MILLISECONDS);

         handler.checkAssertions();

         Assert.assertTrue("Didn't receive all messages", ok);
      }

      sessSend.close();
      for (ClientSession session : sessions) {
         session.close();
      }

      for (int i = 0; i < numSessions; i++) {
         SimpleString subName = new SimpleString("sub" + i);

         s.deleteQueue(subName);
      }

      s.close();

      long end = System.currentTimeMillis();

      log.debug("duration " + (end - start));
   }

   protected void doTestB(final ClientSessionFactory sf) throws Exception {
      long start = System.currentTimeMillis();

      ClientSession s = sf.createSession(false, false, false);

      final int numMessages = 100;

      final int numSessions = 50;

      Set<ClientConsumer> consumers = new HashSet<>();
      Set<ClientSession> sessions = new HashSet<>();

      for (int i = 0; i < numSessions; i++) {
         SimpleString subName = new SimpleString("sub" + i);

         ClientSession sessConsume = sf.createSession(false, true, true);

         sessConsume.createQueue(new QueueConfiguration(subName).setAddress(RandomReattachTest.ADDRESS).setDurable(false));

         ClientConsumer consumer = sessConsume.createConsumer(subName);

         consumers.add(consumer);

         sessions.add(sessConsume);
      }

      ClientSession sessSend = sf.createSession(false, true, true);

      ClientProducer producer = sessSend.createProducer(RandomReattachTest.ADDRESS);

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = sessSend.createMessage(ActiveMQTextMessage.TYPE, false, 0, System.currentTimeMillis(), (byte) 1);
         message.putIntProperty(new SimpleString("count"), i);
         producer.send(message);
      }

      for (ClientSession session : sessions) {
         session.start();
      }

      class MyHandler extends AssertionCheckMessageHandler {

         final CountDownLatch latch = new CountDownLatch(1);

         int count;

         @Override
         public void onMessageAssert(final ClientMessage message) {
            if (count == numMessages) {
               Assert.fail("Too many messages");
            }

            Assert.assertEquals(count, message.getObjectProperty(new SimpleString("count")));

            count++;

            if (count == numMessages) {
               latch.countDown();
            }
         }
      }

      Set<MyHandler> handlers = new HashSet<>();

      for (ClientConsumer consumer : consumers) {
         MyHandler handler = new MyHandler();

         consumer.setMessageHandler(handler);

         handlers.add(handler);
      }

      for (MyHandler handler : handlers) {
         boolean ok = handler.latch.await(10000, TimeUnit.MILLISECONDS);

         handler.checkAssertions();

         Assert.assertTrue(ok);
      }

      sessSend.close();

      for (ClientSession session : sessions) {
         session.close();
      }

      for (int i = 0; i < numSessions; i++) {
         SimpleString subName = new SimpleString("sub" + i);

         s.deleteQueue(subName);
      }

      s.close();

      long end = System.currentTimeMillis();

      log.debug("duration " + (end - start));

   }

   protected void doTestC(final ClientSessionFactory sf) throws Exception {
      long start = System.currentTimeMillis();

      ClientSession s = sf.createSession(false, false, false);

      final int numMessages = 100;

      final int numSessions = 1;

      Set<ClientConsumer> consumers = new HashSet<>();
      Set<ClientSession> sessions = new HashSet<>();

      for (int i = 0; i < numSessions; i++) {
         SimpleString subName = new SimpleString("sub" + i);

         ClientSession sessConsume = sf.createSession(false, false, false);

         sessConsume.start();

         sessConsume.createQueue(new QueueConfiguration(subName).setAddress(RandomReattachTest.ADDRESS).setDurable(false));

         ClientConsumer consumer = sessConsume.createConsumer(subName);

         consumers.add(consumer);

         sessions.add(sessConsume);
      }

      ClientSession sessSend = sf.createSession(false, false, true);

      ClientProducer producer = sessSend.createProducer(RandomReattachTest.ADDRESS);

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = sessSend.createMessage(ActiveMQTextMessage.TYPE, false, 0, System.currentTimeMillis(), (byte) 1);
         message.putIntProperty(new SimpleString("count"), i);
         producer.send(message);
      }

      sessSend.rollback();

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = sessSend.createMessage(ActiveMQTextMessage.TYPE, false, 0, System.currentTimeMillis(), (byte) 1);
         message.putIntProperty(new SimpleString("count"), i);
         producer.send(message);
      }

      sessSend.commit();

      class MyHandler extends AssertionCheckMessageHandler {

         final CountDownLatch latch = new CountDownLatch(1);

         int count;

         @Override
         public void onMessageAssert(final ClientMessage message) {
            if (count == numMessages) {
               Assert.fail("Too many messages, expected " + count);
            }

            Assert.assertEquals(count, message.getObjectProperty(new SimpleString("count")));

            count++;

            try {
               message.acknowledge();
            } catch (ActiveMQException e) {
               e.printStackTrace();
               throw new RuntimeException(e.getMessage(), e);
            }

            if (count == numMessages) {
               latch.countDown();
            }
         }
      }

      Set<MyHandler> handlers = new HashSet<>();

      for (ClientConsumer consumer : consumers) {
         MyHandler handler = new MyHandler();

         consumer.setMessageHandler(handler);

         handlers.add(handler);
      }

      for (MyHandler handler : handlers) {
         boolean ok = handler.latch.await(10000, TimeUnit.MILLISECONDS);

         Assert.assertTrue(ok);

         handler.checkAssertions();
      }

      handlers.clear();

      // New handlers
      for (ClientConsumer consumer : consumers) {
         MyHandler handler = new MyHandler();

         consumer.setMessageHandler(handler);

         handlers.add(handler);
      }

      for (ClientSession session : sessions) {
         session.rollback();
      }

      for (MyHandler handler : handlers) {
         boolean ok = handler.latch.await(10000, TimeUnit.MILLISECONDS);

         Assert.assertTrue(ok);

         handler.checkAssertions();
      }

      for (ClientSession session : sessions) {
         session.commit();
      }

      sessSend.close();
      for (ClientSession session : sessions) {
         session.close();
      }

      for (int i = 0; i < numSessions; i++) {
         SimpleString subName = new SimpleString("sub" + i);

         s.deleteQueue(subName);
      }

      s.close();

      long end = System.currentTimeMillis();

      log.debug("duration " + (end - start));
   }

   protected void doTestD(final ClientSessionFactory sf) throws Exception {
      long start = System.currentTimeMillis();

      ClientSession s = sf.createSession(false, false, false);

      final int numMessages = 100;

      final int numSessions = 10;

      Set<ClientConsumer> consumers = new HashSet<>();
      Set<ClientSession> sessions = new HashSet<>();

      for (int i = 0; i < numSessions; i++) {
         SimpleString subName = new SimpleString("sub" + i);

         ClientSession sessConsume = sf.createSession(false, false, false);

         sessConsume.createQueue(new QueueConfiguration(subName).setAddress(RandomReattachTest.ADDRESS).setDurable(false));

         ClientConsumer consumer = sessConsume.createConsumer(subName);

         consumers.add(consumer);

         sessions.add(sessConsume);
      }

      ClientSession sessSend = sf.createSession(false, false, true);

      ClientProducer producer = sessSend.createProducer(RandomReattachTest.ADDRESS);

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = sessSend.createMessage(ActiveMQTextMessage.TYPE, false, 0, System.currentTimeMillis(), (byte) 1);
         message.putIntProperty(new SimpleString("count"), i);
         producer.send(message);
      }

      sessSend.rollback();

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = sessSend.createMessage(ActiveMQTextMessage.TYPE, false, 0, System.currentTimeMillis(), (byte) 1);
         message.putIntProperty(new SimpleString("count"), i);
         producer.send(message);
      }

      sessSend.commit();

      for (ClientSession session : sessions) {
         session.start();
      }

      class MyHandler extends AssertionCheckMessageHandler {

         final CountDownLatch latch = new CountDownLatch(1);

         int count;

         @Override
         public void onMessageAssert(final ClientMessage message) {
            if (count == numMessages) {
               Assert.fail("Too many messages, " + count);
            }

            Assert.assertEquals(count, message.getObjectProperty(new SimpleString("count")));

            count++;

            if (count == numMessages) {
               latch.countDown();
            }
         }
      }

      Set<MyHandler> handlers = new HashSet<>();

      for (ClientConsumer consumer : consumers) {
         MyHandler handler = new MyHandler();

         consumer.setMessageHandler(handler);

         handlers.add(handler);
      }

      for (MyHandler handler : handlers) {
         boolean ok = handler.latch.await(20000, TimeUnit.MILLISECONDS);

         Assert.assertTrue(ok);

         handler.checkAssertions();
      }

      handlers.clear();

      // New handlers
      for (ClientConsumer consumer : consumers) {
         MyHandler handler = new MyHandler();

         consumer.setMessageHandler(handler);

         handlers.add(handler);
      }

      for (ClientSession session : sessions) {
         session.rollback();
      }

      for (MyHandler handler : handlers) {
         boolean ok = handler.latch.await(10000, TimeUnit.MILLISECONDS);

         Assert.assertTrue(ok);

         handler.checkAssertions();
      }

      for (ClientSession session : sessions) {
         session.commit();
      }

      sessSend.close();
      for (ClientSession session : sessions) {
         session.close();
      }

      for (int i = 0; i < numSessions; i++) {
         SimpleString subName = new SimpleString("sub" + i);

         s.deleteQueue(subName);
      }

      s.close();

      long end = System.currentTimeMillis();

      log.debug("duration " + (end - start));
   }

   // Now with synchronous receive()

   protected void doTestE(final ClientSessionFactory sf) throws Exception {
      long start = System.currentTimeMillis();

      ClientSession s = sf.createSession(false, false, false);

      final int numMessages = 100;

      final int numSessions = 10;

      Set<ClientConsumer> consumers = new HashSet<>();
      Set<ClientSession> sessions = new HashSet<>();

      for (int i = 0; i < numSessions; i++) {
         SimpleString subName = new SimpleString("sub" + i);

         ClientSession sessConsume = sf.createSession(false, true, true);

         sessConsume.start();

         sessConsume.createQueue(new QueueConfiguration(subName).setAddress(RandomReattachTest.ADDRESS).setDurable(false));

         ClientConsumer consumer = sessConsume.createConsumer(subName);

         consumers.add(consumer);

         sessions.add(sessConsume);
      }

      ClientSession sessSend = sf.createSession(false, true, true);

      ClientProducer producer = sessSend.createProducer(RandomReattachTest.ADDRESS);

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = sessSend.createMessage(ActiveMQTextMessage.TYPE, false, 0, System.currentTimeMillis(), (byte) 1);
         message.putIntProperty(new SimpleString("count"), i);
         producer.send(message);
      }

      for (int i = 0; i < numMessages; i++) {
         for (ClientConsumer consumer : consumers) {
            ClientMessage msg = consumer.receive(RandomReattachTest.RECEIVE_TIMEOUT);

            Assert.assertNotNull(msg);

            Assert.assertEquals(i, msg.getObjectProperty(new SimpleString("count")));

            msg.acknowledge();
         }
      }

      for (int i = 0; i < numMessages; i++) {
         for (ClientConsumer consumer : consumers) {
            ClientMessage msg = consumer.receiveImmediate();

            Assert.assertNull(msg);
         }
      }

      sessSend.close();
      for (ClientSession session : sessions) {
         session.close();
      }

      for (int i = 0; i < numSessions; i++) {
         SimpleString subName = new SimpleString("sub" + i);

         s.deleteQueue(subName);
      }

      s.close();

      long end = System.currentTimeMillis();

      log.debug("duration " + (end - start));
   }

   protected void doTestF(final ClientSessionFactory sf) throws Exception {
      long start = System.currentTimeMillis();

      ClientSession s = sf.createSession(false, false, false);

      final int numMessages = 100;

      final int numSessions = 10;

      Set<ClientConsumer> consumers = new HashSet<>();
      Set<ClientSession> sessions = new HashSet<>();

      for (int i = 0; i < numSessions; i++) {
         SimpleString subName = new SimpleString("sub" + i);

         ClientSession sessConsume = sf.createSession(false, true, true);

         sessConsume.createQueue(new QueueConfiguration(subName).setAddress(RandomReattachTest.ADDRESS).setDurable(false));

         ClientConsumer consumer = sessConsume.createConsumer(subName);

         consumers.add(consumer);

         sessions.add(sessConsume);
      }

      ClientSession sessSend = sf.createSession(false, true, true);

      ClientProducer producer = sessSend.createProducer(RandomReattachTest.ADDRESS);

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = sessSend.createMessage(ActiveMQTextMessage.TYPE, false, 0, System.currentTimeMillis(), (byte) 1);
         message.putIntProperty(new SimpleString("count"), i);
         producer.send(message);
      }

      for (ClientSession session : sessions) {
         session.start();
      }

      for (int i = 0; i < numMessages; i++) {
         for (ClientConsumer consumer : consumers) {
            ClientMessage msg = consumer.receive(RandomReattachTest.RECEIVE_TIMEOUT);

            if (msg == null) {
               throw new IllegalStateException("Failed to receive message " + i);
            }

            Assert.assertNotNull(msg);

            Assert.assertEquals(i, msg.getObjectProperty(new SimpleString("count")));

            msg.acknowledge();
         }
      }

      for (int i = 0; i < numMessages; i++) {
         for (ClientConsumer consumer : consumers) {
            ClientMessage msg = consumer.receiveImmediate();

            Assert.assertNull(msg);
         }
      }

      sessSend.close();
      for (ClientSession session : sessions) {
         session.close();
      }

      for (int i = 0; i < numSessions; i++) {
         SimpleString subName = new SimpleString("sub" + i);

         s.deleteQueue(subName);
      }

      s.close();

      Assert.assertEquals(1, ((ClientSessionFactoryImpl) sf).numSessions());

      long end = System.currentTimeMillis();

      log.debug("duration " + (end - start));
   }

   protected void doTestG(final ClientSessionFactory sf) throws Exception {
      long start = System.currentTimeMillis();

      ClientSession s = sf.createSession(false, false, false);

      final int numMessages = 100;

      final int numSessions = 10;

      Set<ClientConsumer> consumers = new HashSet<>();
      Set<ClientSession> sessions = new HashSet<>();

      for (int i = 0; i < numSessions; i++) {
         SimpleString subName = new SimpleString("sub" + i);

         ClientSession sessConsume = sf.createSession(false, false, false);

         sessConsume.start();

         sessConsume.createQueue(new QueueConfiguration(subName).setAddress(RandomReattachTest.ADDRESS).setDurable(false));

         ClientConsumer consumer = sessConsume.createConsumer(subName);

         consumers.add(consumer);

         sessions.add(sessConsume);
      }

      ClientSession sessSend = sf.createSession(false, false, false);

      ClientProducer producer = sessSend.createProducer(RandomReattachTest.ADDRESS);

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = sessSend.createMessage(ActiveMQTextMessage.TYPE, false, 0, System.currentTimeMillis(), (byte) 1);
         message.putIntProperty(new SimpleString("count"), i);
         producer.send(message);
      }

      sessSend.rollback();

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = sessSend.createMessage(ActiveMQTextMessage.TYPE, false, 0, System.currentTimeMillis(), (byte) 1);
         message.putIntProperty(new SimpleString("count"), i);
         producer.send(message);
      }

      sessSend.commit();

      for (int i = 0; i < numMessages; i++) {
         for (ClientConsumer consumer : consumers) {
            ClientMessage msg = consumer.receive(RandomReattachTest.RECEIVE_TIMEOUT);

            Assert.assertNotNull(msg);

            Assert.assertEquals(i, msg.getObjectProperty(new SimpleString("count")));

            msg.acknowledge();
         }
      }

      for (ClientConsumer consumer : consumers) {
         ClientMessage msg = consumer.receiveImmediate();

         Assert.assertNull(msg);
      }

      for (ClientSession session : sessions) {
         session.rollback();
      }

      for (int i = 0; i < numMessages; i++) {
         for (ClientConsumer consumer : consumers) {
            ClientMessage msg = consumer.receive(RandomReattachTest.RECEIVE_TIMEOUT);

            Assert.assertNotNull(msg);

            Assert.assertEquals(i, msg.getObjectProperty(new SimpleString("count")));

            msg.acknowledge();
         }
      }

      for (int i = 0; i < numMessages; i++) {
         for (ClientConsumer consumer : consumers) {
            ClientMessage msg = consumer.receiveImmediate();

            Assert.assertNull(msg);
         }
      }

      for (ClientSession session : sessions) {
         session.commit();
      }

      sessSend.close();
      for (ClientSession session : sessions) {
         session.close();
      }

      for (int i = 0; i < numSessions; i++) {
         SimpleString subName = new SimpleString("sub" + i);

         s.deleteQueue(subName);
      }

      s.close();

      long end = System.currentTimeMillis();

      log.debug("duration " + (end - start));
   }

   protected void doTestH(final ClientSessionFactory sf) throws Exception {
      long start = System.currentTimeMillis();

      ClientSession s = sf.createSession(false, false, false);

      final int numMessages = 100;

      final int numSessions = 10;

      Set<ClientConsumer> consumers = new HashSet<>();
      Set<ClientSession> sessions = new HashSet<>();

      for (int i = 0; i < numSessions; i++) {
         SimpleString subName = new SimpleString("sub" + i);

         ClientSession sessConsume = sf.createSession(false, false, false);

         sessConsume.createQueue(new QueueConfiguration(subName).setAddress(RandomReattachTest.ADDRESS).setDurable(false));

         ClientConsumer consumer = sessConsume.createConsumer(subName);

         consumers.add(consumer);

         sessions.add(sessConsume);
      }

      ClientSession sessSend = sf.createSession(false, false, false);

      ClientProducer producer = sessSend.createProducer(RandomReattachTest.ADDRESS);

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = sessSend.createMessage(ActiveMQTextMessage.TYPE, false, 0, System.currentTimeMillis(), (byte) 1);
         message.putIntProperty(new SimpleString("count"), i);
         producer.send(message);
      }

      sessSend.rollback();

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = sessSend.createMessage(ActiveMQTextMessage.TYPE, false, 0, System.currentTimeMillis(), (byte) 1);
         message.putIntProperty(new SimpleString("count"), i);
         producer.send(message);
      }

      sessSend.commit();

      for (ClientSession session : sessions) {
         session.start();
      }

      for (int i = 0; i < numMessages; i++) {
         for (ClientConsumer consumer : consumers) {
            ClientMessage msg = consumer.receive(RandomReattachTest.RECEIVE_TIMEOUT);

            Assert.assertNotNull(msg);

            Assert.assertEquals(i, msg.getObjectProperty(new SimpleString("count")));

            msg.acknowledge();
         }
      }

      for (int i = 0; i < numMessages; i++) {
         for (ClientConsumer consumer : consumers) {
            ClientMessage msg = consumer.receiveImmediate();

            Assert.assertNull(msg);
         }
      }

      for (ClientSession session : sessions) {
         session.rollback();
      }

      for (int i = 0; i < numMessages; i++) {
         for (ClientConsumer consumer : consumers) {
            ClientMessage msg = consumer.receive(RandomReattachTest.RECEIVE_TIMEOUT);

            Assert.assertNotNull(msg);

            Assert.assertEquals(i, msg.getObjectProperty(new SimpleString("count")));

            msg.acknowledge();
         }
      }

      for (int i = 0; i < numMessages; i++) {
         for (ClientConsumer consumer : consumers) {
            ClientMessage msg = consumer.receiveImmediate();

            Assert.assertNull(msg);
         }
      }

      for (ClientSession session : sessions) {
         session.commit();
      }

      sessSend.close();
      for (ClientSession session : sessions) {
         session.close();
      }

      for (int i = 0; i < numSessions; i++) {
         SimpleString subName = new SimpleString("sub" + i);

         s.deleteQueue(subName);
      }

      s.close();

      long end = System.currentTimeMillis();

      log.debug("duration " + (end - start));
   }

   protected void doTestI(final ClientSessionFactory sf) throws Exception {
      ClientSession sessCreate = sf.createSession(false, true, true);

      sessCreate.createQueue(new QueueConfiguration(RandomReattachTest.ADDRESS).setDurable(false));

      ClientSession sess = sf.createSession(false, true, true);

      sess.start();

      ClientConsumer consumer = sess.createConsumer(RandomReattachTest.ADDRESS);

      ClientProducer producer = sess.createProducer(RandomReattachTest.ADDRESS);

      ClientMessage message = sess.createMessage(ActiveMQTextMessage.TYPE, false, 0, System.currentTimeMillis(), (byte) 1);
      producer.send(message);

      ClientMessage message2 = consumer.receive(RandomReattachTest.RECEIVE_TIMEOUT);

      Assert.assertNotNull(message2);

      message2.acknowledge();

      sess.close();

      sessCreate.deleteQueue(RandomReattachTest.ADDRESS);

      sessCreate.close();
   }

   protected void doTestJ(final ClientSessionFactory sf) throws Exception {
      ClientSession sessCreate = sf.createSession(false, true, true);

      sessCreate.createQueue(new QueueConfiguration(RandomReattachTest.ADDRESS).setDurable(false));

      ClientSession sess = sf.createSession(false, true, true);

      sess.start();

      ClientConsumer consumer = sess.createConsumer(RandomReattachTest.ADDRESS);

      ClientProducer producer = sess.createProducer(RandomReattachTest.ADDRESS);

      ClientMessage message = sess.createMessage(ActiveMQTextMessage.TYPE, false, 0, System.currentTimeMillis(), (byte) 1);
      producer.send(message);

      ClientMessage message2 = consumer.receive(RandomReattachTest.RECEIVE_TIMEOUT);

      Assert.assertNotNull(message2);

      message2.acknowledge();

      sess.close();

      sessCreate.deleteQueue(RandomReattachTest.ADDRESS);

      sessCreate.close();
   }

   protected void doTestK(final ClientSessionFactory sf) throws Exception {
      ClientSession s = sf.createSession(false, false, false);

      s.createQueue(new QueueConfiguration(RandomReattachTest.ADDRESS).setDurable(false));

      final int numConsumers = 100;

      for (int i = 0; i < numConsumers; i++) {
         ClientConsumer consumer = s.createConsumer(RandomReattachTest.ADDRESS);

         consumer.close();
      }

      s.deleteQueue(RandomReattachTest.ADDRESS);

      s.close();
   }

   protected void doTestL(final ClientSessionFactory sf) throws Exception {
      final int numSessions = 10;

      for (int i = 0; i < numSessions; i++) {
         ClientSession session = sf.createSession(false, false, false);

         session.close();
      }
   }

   protected void doTestN(final ClientSessionFactory sf) throws Exception {
      ClientSession sessCreate = sf.createSession(false, true, true);

      sessCreate.createQueue(new QueueConfiguration(new SimpleString(RandomReattachTest.ADDRESS.toString())).setAddress(RandomReattachTest.ADDRESS).setDurable(false));

      ClientSession sess = sf.createSession(false, true, true);

      sess.stop();

      sess.start();

      sess.stop();

      ClientConsumer consumer = sess.createConsumer(new SimpleString(RandomReattachTest.ADDRESS.toString()));

      ClientProducer producer = sess.createProducer(RandomReattachTest.ADDRESS);

      ClientMessage message = sess.createMessage(ActiveMQTextMessage.TYPE, false, 0, System.currentTimeMillis(), (byte) 1);
      producer.send(message);

      sess.start();

      ClientMessage message2 = consumer.receive(RandomReattachTest.RECEIVE_TIMEOUT);

      Assert.assertNotNull(message2);

      message2.acknowledge();

      sess.stop();

      sess.start();

      sess.close();

      sessCreate.deleteQueue(new SimpleString(RandomReattachTest.ADDRESS.toString()));

      sessCreate.close();
   }

   protected int getNumIterations() {
      return 2;
   }

   @Override
   @Before
   public void setUp() throws Exception {
      super.setUp();

      timer = new Timer(true);
   }

   @Override
   @After
   public void tearDown() throws Exception {
      timer.cancel();

      super.tearDown();
   }


   private Failer startFailer(final long time, final ClientSession session) {
      Failer failer = new Failer((ClientSessionInternal) session);

      timer.schedule(failer, (long) (time * Math.random()), 100);

      return failer;
   }

   private void start() throws Exception {
      server = addServer(ActiveMQServers.newActiveMQServer(createDefaultInVMConfig(), false));
      server.start();
   }

   private void stop() throws Exception {
      server.stop();

      Assert.assertEquals(0, InVMRegistry.instance.size());

      server = null;
   }


   class Failer extends TimerTask {

      private final ClientSessionInternal session;

      private boolean executed;

      Failer(final ClientSessionInternal session) {
         this.session = session;
      }

      @Override
      public synchronized void run() {
         log.debug("** Failing connection");

         session.getConnection().fail(new ActiveMQNotConnectedException("oops"));

         log.debug("** Fail complete");

         cancel();

         executed = true;
      }

      public synchronized boolean isExecuted() {
         return executed;
      }
   }

   public abstract class RunnableT {

      abstract void run(ClientSessionFactory sf) throws Exception;
   }

   abstract static class AssertionCheckMessageHandler implements MessageHandler {

      public void checkAssertions() {
         for (AssertionError e : errors) {
            // it will throw the first error
            throw e;
         }
      }

      private final ArrayList<AssertionError> errors = new ArrayList<>();

      /* (non-Javadoc)
       * @see MessageHandler#onMessage(ClientMessage)
       */
      @Override
      public void onMessage(ClientMessage message) {
         try {
            onMessageAssert(message);
         } catch (AssertionError e) {
            e.printStackTrace(); // System.out -> junit reports
            errors.add(e);
         }
      }

      public abstract void onMessageAssert(ClientMessage message);
   }
}
