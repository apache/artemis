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
package org.apache.activemq.artemis.tests.integration.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.MessageHandler;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.client.impl.ClientConsumerImpl;
import org.apache.activemq.artemis.core.client.impl.ClientConsumerInternal;
import org.apache.activemq.artemis.core.client.impl.ClientSessionImpl;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.Bindings;
import org.apache.activemq.artemis.core.postoffice.QueueBinding;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Consumer;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.server.impl.ServerConsumerImpl;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConsumerWindowSizeTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final SimpleString addressA = SimpleString.of("addressA");

   private final SimpleString queueA = SimpleString.of("queueA");

   private final int TIMEOUT = 5;

   private ServerLocator locator;

   protected boolean isNetty() {
      return false;
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      locator = createFactory(isNetty());
   }

   private int getMessageEncodeSize(final SimpleString address) throws Exception {
      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession session = cf.createSession(false, true, true);
      ClientMessage message = session.createMessage(false);
      // we need to set the destination so we can calculate the encodesize correctly
      message.setAddress(address);
      int encodeSize = message.getEncodeSize();
      session.close();
      cf.close();
      return encodeSize;
   }

   @Test
   public void testReceiveImmediateWithZeroWindow() throws Exception {
      ActiveMQServer server = createServer(false, isNetty());

      server.start();

      locator.setConsumerWindowSize(0);

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, false, false);
      session.createQueue(QueueConfiguration.of("testWindow"));
      session.close();

      int numConsumers = 5;

      List<ClientConsumer> consumers = new ArrayList<>();
      List<ClientSession> sessions = new ArrayList<>();
      for (int i = 0; i < numConsumers; i++) {
         ClientSession session1 = sf.createSession();
         ClientConsumer consumer = session1.createConsumer("testWindow");
         consumers.add(consumer);
         session1.start();
         sessions.add(session1);
         consumer.receiveImmediate();

      }

      ClientSession senderSession = sf.createSession(false, false);

      ClientProducer producer = senderSession.createProducer("testWindow");

      ClientMessage sent = senderSession.createMessage(true);
      sent.putStringProperty("hello", "world");
      producer.send(sent);

      senderSession.commit();

      senderSession.start();

      ClientConsumer consumer = consumers.get(2);
      ClientMessage received = consumer.receive(1000);
      assertNotNull(received);

      for (ClientSession tmpSess : sessions) {
         tmpSess.close();
      }

      senderSession.close();

   }

   @Test
   public void testReceiveImmediateWithZeroWindow2() throws Exception {
      ActiveMQServer server = createServer(true);
      try (ServerLocator locator = createInVMNonHALocator()) {
         server.start();

         locator.setConsumerWindowSize(0);

         ClientSessionFactory sf = createSessionFactory(locator);
         ClientSession session = sf.createSession(false, false, false);
         session.createQueue(QueueConfiguration.of("testReceive"));
         session.close();

         ClientSession sessionProd = sf.createSession(false, false);
         ClientMessage msg = sessionProd.createMessage(true);
         msg.putStringProperty("hello", "world");
         ClientProducer prod = sessionProd.createProducer("testReceive");

         prod.send(msg);
         sessionProd.commit();

         ClientSession session1 = sf.createSession();
         ClientConsumer consumer = session1.createConsumer("testReceive");
         session1.start();

         Thread.sleep(1000);
         ClientMessage message = null;
         message = consumer.receiveImmediate();
         // message = consumer.receive(1000); // the test will pass if used receive(1000) instead of receiveImmediate
         assertNotNull(message);
         message.acknowledge();

         prod.send(msg);
         sessionProd.commit();

         message = consumer.receive(10000);
         assertNotNull(message);
         message.acknowledge();

         session.close();
         session1.close();
         sessionProd.close();
      }
   }

   @Test
   public void testReceiveImmediateWithZeroWindow3() throws Exception {
      ActiveMQServer server = createServer(false, isNetty());

      server.start();

      locator.setConsumerWindowSize(0);

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, false, false);
      session.createQueue(QueueConfiguration.of("testWindow"));
      session.close();

      int numConsumers = 5;

      List<ClientConsumer> consumers = new ArrayList<>();
      List<ClientSession> sessions = new ArrayList<>();
      for (int i = 0; i < numConsumers; i++) {
         ClientSession session1 = sf.createSession();
         ClientConsumer consumer = session1.createConsumer("testWindow");
         consumers.add(consumer);
         session1.start();
         sessions.add(session1);
         consumer.receive(10);

      }

      ClientSession senderSession = sf.createSession(false, false);

      ClientProducer producer = senderSession.createProducer("testWindow");

      ClientMessage sent = senderSession.createMessage(true);
      sent.putStringProperty("hello", "world");

      producer.send(sent);

      senderSession.commit();

      senderSession.start();

      ClientConsumer consumer = consumers.get(2);
      ClientMessage received = consumer.receive(1000);
      assertNotNull(received);

      for (ClientSession tmpSess : sessions) {
         tmpSess.close();
      }

      senderSession.close();
   }

   @Test
   public void testReceiveImmediateWithZeroWindow4() throws Exception {
      ActiveMQServer server = createServer(false, isNetty());

      server.start();

      locator.setConsumerWindowSize(0);

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, false, false);
      session.createQueue(QueueConfiguration.of("testWindow"));
      session.close();

      int numConsumers = 5;

      List<ClientConsumer> consumers = new ArrayList<>();
      List<ClientSession> sessions = new ArrayList<>();
      for (int i = 0; i < numConsumers; i++) {
         ClientSession session1 = sf.createSession();
         ClientConsumer consumer = session1.createConsumer("testWindow");
         consumers.add(consumer);
         session1.start();
         sessions.add(session1);
         assertNull(consumer.receive(10));

      }

      ClientSession senderSession = sf.createSession(false, false);

      ClientProducer producer = senderSession.createProducer("testWindow");

      ClientMessage sent = senderSession.createMessage(true);
      sent.putStringProperty("hello", "world");

      producer.send(sent);

      senderSession.commit();

      senderSession.start();

      ClientConsumer consumer = consumers.get(2);
      ClientMessage received = consumer.receive(5000);
      assertNotNull(received);

      for (ClientSession tmpSess : sessions) {
         tmpSess.close();
      }

      senderSession.close();
   }

   @Test
   public void testMultipleImmediate() throws Exception {

      final int NUMBER_OF_MESSAGES = 200;
      ActiveMQServer server = createServer(false, isNetty());

      server.start();

      locator.setConsumerWindowSize(0);

      final ClientSessionFactory sf = createSessionFactory(locator);

      {
         ClientSession session = sf.createSession(false, false, false);
         session.createQueue(QueueConfiguration.of("testWindow"));
         session.close();
      }

      Thread[] threads = new Thread[10];
      final AtomicInteger errors = new AtomicInteger(0);
      final CountDownLatch latchStart = new CountDownLatch(1);
      final AtomicInteger received = new AtomicInteger(0);

      for (int i = 0; i < threads.length; i++) {
         threads[i] = new Thread(() -> {
            try {
               ClientSession session = sf.createSession(false, false);
               ClientConsumer consumer = session.createConsumer("testWindow");
               session.start();
               latchStart.await(10, TimeUnit.SECONDS);

               while (true) {

                  if (received.incrementAndGet() > NUMBER_OF_MESSAGES) {
                     received.decrementAndGet();
                     break;
                  }
                  ClientMessage msg = consumer.receive(1000);
                  msg.acknowledge();

                  session.commit();


               }

            } catch (Throwable e) {
               e.printStackTrace();
               errors.incrementAndGet();
            }
         });

         threads[i].start();
      }

      ClientSession senderSession = sf.createSession(false, false);

      ClientProducer producer = senderSession.createProducer("testWindow");

      ClientMessage sent = senderSession.createMessage(true);
      sent.putStringProperty("hello", "world");
      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         producer.send(sent);
         senderSession.commit();
      }

      latchStart.countDown();

      for (Thread t : threads) {
         t.join();
      }

      assertEquals(0, errors.get());

      assertEquals(NUMBER_OF_MESSAGES, received.get());
   }

   @Test
   public void testSingleImmediate() throws Exception {

      final int NUMBER_OF_MESSAGES = 200;
      ActiveMQServer server = createServer(false, isNetty());

      server.start();

      locator.setConsumerWindowSize(0);

      final ClientSessionFactory sf = createSessionFactory(locator);

      {
         ClientSession session = sf.createSession(false, false, false);
         session.createQueue(QueueConfiguration.of("testWindow"));
         session.close();
      }

      final AtomicInteger received = new AtomicInteger(0);

      ClientSession senderSession = sf.createSession(false, false);

      ClientProducer producer = senderSession.createProducer("testWindow");

      ClientMessage sent = senderSession.createMessage(true);
      sent.putStringProperty("hello", "world");
      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         producer.send(sent);
      }

      senderSession.commit();

      ClientSession session = sf.createSession(false, false);
      ClientConsumer consumer = session.createConsumer("testWindow");
      session.start();

      while (true) {

         ClientMessage msg = consumer.receiveImmediate();
         if (msg == null) {
            logger.debug("Returning null");
            break;
         }
         msg.acknowledge();

         session.commit();

         received.incrementAndGet();

      }

      assertEquals(NUMBER_OF_MESSAGES, received.get());
   }

   /**
    * tests send window size. we do this by having 2 receivers on the q. since we roundrobin the consumer for delivery
    * we know if consumer 1 has received n messages then consumer 2 must have also have received n messages or at least
    * up to its window size
    */
   @Test
   public void testSendWindowSize() throws Exception {
      ActiveMQServer messagingService = createServer(false, isNetty());
      locator.setBlockOnNonDurableSend(false);

      messagingService.start();
      int numMessage = 100;
      locator.setConsumerWindowSize(numMessage * getMessageEncodeSize(addressA));
      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession sendSession = cf.createSession(false, true, true);
      ClientSession receiveSession = cf.createSession(false, true, true);
      sendSession.createQueue(QueueConfiguration.of(queueA).setAddress(addressA).setDurable(false));
      ClientConsumer receivingConsumer = receiveSession.createConsumer(queueA);

      ClientSession session = cf.createSession(false, true, true);
      ClientProducer cp = sendSession.createProducer(addressA);
      ClientConsumer cc = session.createConsumer(queueA);
      session.start();
      receiveSession.start();
      for (int i = 0; i < numMessage * 4; i++) {
         cp.send(sendSession.createMessage(false));
      }

      for (int i = 0; i < numMessage * 2; i++) {
         ClientMessage m = receivingConsumer.receive(5000);
         assertNotNull(m);
         m.acknowledge();
      }
      receiveSession.close();

      for (int i = 0; i < numMessage * 2; i++) {
         ClientMessage m = cc.receive(5000);
         assertNotNull(m);
         m.acknowledge();
      }

      session.close();
      sendSession.close();

      assertEquals(0, getMessageCount(messagingService, queueA.toString()));
   }

   @Test
   public void testSlowConsumerBufferingOne() throws Exception {
      ActiveMQServer server = createServer(false, isNetty());

      ClientSession sessionB = null;
      ClientSession session = null;

      try {
         final int numberOfMessages = 100;

         server.start();

         locator.setConsumerWindowSize(1);

         ClientSessionFactory sf = createSessionFactory(locator);

         session = sf.createSession(false, true, true);

         SimpleString ADDRESS = addressA;

         session.createQueue(QueueConfiguration.of(ADDRESS));

         sessionB = sf.createSession(false, true, true);
         sessionB.start();

         session.start();

         ClientConsumer consNeverUsed = sessionB.createConsumer(ADDRESS);

         ClientConsumer cons1 = session.createConsumer(ADDRESS);

         ClientProducer prod = session.createProducer(ADDRESS);

         for (int i = 0; i < numberOfMessages; i++) {
            prod.send(createTextMessage(session, "Msg" + i));
         }

         for (int i = 0; i < numberOfMessages - 1; i++) {
            ClientMessage msg = cons1.receive(1000);
            assertNotNull(msg, "expected message at i = " + i);
            msg.acknowledge();
         }

         ClientMessage msg = consNeverUsed.receive(500);
         assertNotNull(msg);
         msg.acknowledge();

         session.close();
         session = null;

         sessionB.close();
         sessionB = null;

         assertEquals(0, getMessageCount(server, ADDRESS.toString()));

      } finally {
         try {
            if (session != null) {
               session.close();
            }
            if (sessionB != null) {
               sessionB.close();
            }
         } catch (Exception ignored) {
         }
      }
   }

   @Test
   public void testSlowConsumerNoBuffer() throws Exception {
      internalTestSlowConsumerNoBuffer(false);
   }

   @Test
   @Disabled("I believe this test became invalid after we started using another thread to deliver the large message")
   public void testSlowConsumerNoBufferLargeMessages() throws Exception {
      internalTestSlowConsumerNoBuffer(true);
   }

   private void internalTestSlowConsumerNoBuffer(final boolean largeMessages) throws Exception {
      ActiveMQServer server = createServer(false, isNetty());

      ClientSession sessionB = null;
      ClientSession session = null;

      try {
         final int numberOfMessages = 100;

         server.start();

         locator.setConsumerWindowSize(0);
         if (largeMessages) {
            locator.setMinLargeMessageSize(100);
         }

         ClientSessionFactory sf = createSessionFactory(locator);

         session = sf.createSession(false, true, true);

         SimpleString ADDRESS = addressA;

         session.createQueue(QueueConfiguration.of(ADDRESS));

         sessionB = sf.createSession(false, true, true);
         sessionB.start();

         session.start();

         ClientConsumerInternal consNeverUsed = (ClientConsumerInternal) sessionB.createConsumer(ADDRESS);

         ClientProducer prod = session.createProducer(ADDRESS);

         // This will force a credit to be sent, but if the message wasn't received we need to take out that credit from
         // the server
         // or the client will be buffering messages
         assertNull(consNeverUsed.receive(1));

         ClientMessage msg = createTextMessage(session, "This one will expire");
         if (largeMessages) {
            msg.getBodyBuffer().writeBytes(new byte[600]);
         }

         msg.setExpiration(System.currentTimeMillis() + 100);
         prod.send(msg);

         msg = createTextMessage(session, "First-on-non-buffered");

         prod.send(msg);

         Thread.sleep(110);

         // It will be able to receive another message, but it shouldn't send a credit again, as the credit was already
         // sent
         msg = consNeverUsed.receive(TIMEOUT * 1000);
         assertNotNull(msg);
         assertEquals("First-on-non-buffered", getTextMessage(msg));
         msg.acknowledge();

         ClientConsumer cons1 = session.createConsumer(ADDRESS);

         for (int i = 0; i < numberOfMessages; i++) {
            msg = createTextMessage(session, "Msg" + i);

            if (largeMessages) {
               msg.getBodyBuffer().writeBytes(new byte[600]);
            }

            prod.send(msg);
         }

         for (int i = 0; i < numberOfMessages; i++) {
            msg = cons1.receive(1000);
            assertNotNull(msg, "expected message at i = " + i);
            assertEquals("Msg" + i, getTextMessage(msg));
            msg.acknowledge();
         }

         assertEquals(0, consNeverUsed.getBufferSize());

         session.close();
         session = null;

         sessionB.close();
         sessionB = null;

         assertEquals(0, getMessageCount(server, ADDRESS.toString()));

      } finally {
         try {
            if (session != null) {
               session.close();
            }
            if (sessionB != null) {
               sessionB.close();
            }
         } catch (Exception ignored) {
         }
      }
   }

   @Test
   public void testSlowConsumerNoBuffer2() throws Exception {
      internalTestSlowConsumerNoBuffer2(false);
   }

   @Test
   public void testSlowConsumerNoBuffer2LargeMessages() throws Exception {
      internalTestSlowConsumerNoBuffer2(true);
   }

   private void internalTestSlowConsumerNoBuffer2(final boolean largeMessages) throws Exception {
      ActiveMQServer server = createServer(false, isNetty());

      ClientSession session1 = null;
      ClientSession session2 = null;

      try {
         final int numberOfMessages = 10;

         server.start();

         locator.setConsumerWindowSize(0);

         if (largeMessages) {
            locator.setMinLargeMessageSize(100);
         }

         ClientSessionFactory sf = createSessionFactory(locator);

         session1 = sf.createSession(false, true, true);

         session2 = sf.createSession(false, true, true);

         session1.start();

         session2.start();

         SimpleString ADDRESS = SimpleString.of("some-queue");

         session1.createQueue(QueueConfiguration.of(ADDRESS));

         ClientConsumerInternal cons1 = (ClientConsumerInternal) session1.createConsumer(ADDRESS);

         // Note we make sure we send the messages *before* cons2 is created

         ClientProducer prod = session1.createProducer(ADDRESS);

         for (int i = 0; i < numberOfMessages; i++) {
            ClientMessage msg = createTextMessage(session1, "Msg" + i);
            if (largeMessages) {
               msg.getBodyBuffer().writeBytes(new byte[600]);
            }
            prod.send(msg);
         }

         ClientConsumerInternal cons2 = (ClientConsumerInternal) session2.createConsumer(ADDRESS);

         for (int i = 0; i < numberOfMessages / 2; i++) {
            ClientMessage msg = cons1.receive(1000);
            assertNotNull(msg, "expected message at i = " + i);

            String str = getTextMessage(msg);
            assertEquals("Msg" + i, str);

            msg.acknowledge();

            assertEquals(0, cons1.getBufferSize(), "A slow consumer shouldn't buffer anything on the client side!");
         }

         for (int i = numberOfMessages / 2; i < numberOfMessages; i++) {
            ClientMessage msg = cons2.receive(1000);

            assertNotNull(msg, "expected message at i = " + i);

            String str = getTextMessage(msg);

            assertEquals("Msg" + i, str);

            msg.acknowledge();

            assertEquals(0, cons2.getBufferSize(), "A slow consumer shouldn't buffer anything on the client side!");
         }

         session1.close(); // just to make sure everything is flushed and no pending packets on the sending buffer, or
         // the getMessageCount would fail
         session2.close();

         session1 = sf.createSession(false, true, true);
         session1.start();
         session2 = sf.createSession(false, true, true);
         session2.start();

         prod = session1.createProducer(ADDRESS);

         assertEquals(0, getMessageCount(server, ADDRESS.toString()));

         // This should also work the other way around

         cons1.close();

         cons2.close();

         cons1 = (ClientConsumerInternal) session1.createConsumer(ADDRESS);

         // Note we make sure we send the messages *before* cons2 is created

         for (int i = 0; i < numberOfMessages; i++) {
            ClientMessage msg = createTextMessage(session1, "Msg" + i);
            if (largeMessages) {
               msg.getBodyBuffer().writeBytes(new byte[600]);
            }
            prod.send(msg);
         }

         cons2 = (ClientConsumerInternal) session2.createConsumer(ADDRESS);

         // Now we receive on cons2 first

         for (int i = 0; i < numberOfMessages / 2; i++) {
            ClientMessage msg = cons2.receive(1000);
            assertNotNull(msg, "expected message at i = " + i);

            assertEquals("Msg" + i, msg.getBodyBuffer().readString());

            msg.acknowledge();

            assertEquals(0, cons2.getBufferSize(), "A slow consumer shouldn't buffer anything on the client side!");

         }

         for (int i = numberOfMessages / 2; i < numberOfMessages; i++) {
            ClientMessage msg = cons1.receive(1000);

            assertNotNull(msg, "expected message at i = " + i);

            assertEquals("Msg" + i, msg.getBodyBuffer().readString());

            msg.acknowledge();

            assertEquals(0, cons1.getBufferSize(), "A slow consumer shouldn't buffer anything on the client side!");
         }

         session1.close();
         session1 = null;
         session2.close();
         session2 = null;
         assertEquals(0, getMessageCount(server, ADDRESS.toString()));

      } finally {
         try {
            if (session1 != null) {
               session1.close();
            }
            if (session2 != null) {
               session2.close();
            }
         } catch (Exception ignored) {
         }
      }
   }

   @Test
   public void testSaveBuffersOnLargeMessage() throws Exception {
      ActiveMQServer server = createServer(false, isNetty());

      ClientSession session1 = null;

      try {
         final int numberOfMessages = 10;

         server.start();

         locator.setConsumerWindowSize(0).setMinLargeMessageSize(100);

         ClientSessionFactory sf = createSessionFactory(locator);

         session1 = sf.createSession(false, true, true);

         session1.start();

         SimpleString ADDRESS = SimpleString.of("some-queue");

         session1.createQueue(QueueConfiguration.of(ADDRESS));

         ClientConsumerInternal cons1 = (ClientConsumerInternal) session1.createConsumer(ADDRESS);

         // Note we make sure we send the messages *before* cons2 is created

         ClientProducer prod = session1.createProducer(ADDRESS);

         for (int i = 0; i < numberOfMessages; i++) {
            ClientMessage msg = session1.createMessage(true);
            msg.getBodyBuffer().writeBytes(new byte[600]);
            prod.send(msg);
         }

         for (int i = 0; i < numberOfMessages; i++) {
            ClientMessage msg = cons1.receive(1000);
            assertNotNull(msg, "expected message at i = " + i);

            msg.saveToOutputStream(new FakeOutputStream());

            msg.acknowledge();

            assertEquals(0, cons1.getBufferSize(), "A slow consumer shouldn't buffer anything on the client side!");
         }

         session1.close(); // just to make sure everything is flushed and no pending packets on the sending buffer, or
         session1.close();
         session1 = null;
         assertEquals(0, getMessageCount(server, ADDRESS.toString()));

      } finally {
         try {
            if (session1 != null) {
               session1.close();
            }
         } catch (Exception ignored) {
         }
      }
   }

   class FakeOutputStream extends OutputStream {

      @Override
      public void write(int b) throws IOException {
      }

   }

   @Test
   public void testSlowConsumerOnMessageHandlerNoBuffers() throws Exception {
      internalTestSlowConsumerOnMessageHandlerNoBuffers(false);
   }

   @Test
   public void testSlowConsumerOnMessageHandlerNoBuffersLargeMessage() throws Exception {
      internalTestSlowConsumerOnMessageHandlerNoBuffers(true);
   }

   @Test
   public void testFlowControl() throws Exception {
      internalTestFlowControlOnRollback(false);
   }

   @Test
   public void testFlowControlLargeMessage() throws Exception {
      internalTestFlowControlOnRollback(true);
   }

   private void internalTestFlowControlOnRollback(final boolean isLargeMessage) throws Exception {

      ActiveMQServer server = createServer(false, isNetty());

      AddressSettings settings = new AddressSettings().setMaxDeliveryAttempts(-1);
      server.getAddressSettingsRepository().addMatch("#", settings);

      ClientSession session = null;

      try {
         final int numberOfMessages = 100;

         server.start();

         locator.setConsumerWindowSize(300000);

         if (isLargeMessage) {
            // something to ensure we are using large messages
            locator.setMinLargeMessageSize(100);
         } else {
            // To make sure large messages won't kick in, we set anything large
            locator.setMinLargeMessageSize(Integer.MAX_VALUE);
         }

         ClientSessionFactory sf = createSessionFactory(locator);

         session = sf.createSession(false, false, false);

         SimpleString ADDRESS = SimpleString.of("some-queue");

         session.createQueue(QueueConfiguration.of(ADDRESS));

         ClientProducer producer = session.createProducer(ADDRESS);

         for (int i = 0; i < numberOfMessages; i++) {
            ClientMessage msg = session.createMessage(true);
            msg.putIntProperty("count", i);
            msg.getBodyBuffer().writeBytes(new byte[1024]);
            producer.send(msg);
         }

         session.commit();

         ClientConsumerInternal consumer = (ClientConsumerInternal) session.createConsumer(ADDRESS);

         session.start();

         for (int repeat = 0; repeat < 100; repeat++) {
            long timeout = System.currentTimeMillis() + 2000;
            // At least 10 messages on the buffer
            while (timeout > System.currentTimeMillis() && consumer.getBufferSize() <= 10) {
               Thread.sleep(10);
            }
            assertTrue(consumer.getBufferSize() >= 10);

            ClientMessage msg = consumer.receive(500);
            msg.getBodyBuffer().readByte();
            assertNotNull(msg);
            msg.acknowledge();
            session.rollback();
         }

         for (int i = 0; i < numberOfMessages; i++) {
            ClientMessage msg = consumer.receive(5000);
            assertNotNull(msg);
            msg.getBodyBuffer().readByte();
            msg.acknowledge();
            session.commit();
         }

      } finally {
         try {
            if (session != null) {
               session.close();
            }
         } catch (Exception ignored) {
         }
      }
   }

   public void internalTestSlowConsumerOnMessageHandlerNoBuffers(final boolean largeMessages) throws Exception {
      ActiveMQServer server = createServer(false, isNetty());

      ClientSession sessionB = null;
      ClientSession session = null;

      try {
         final int numberOfMessages = 100;

         server.start();

         locator.setConsumerWindowSize(0);

         if (largeMessages) {
            locator.setMinLargeMessageSize(100);
         }

         ClientSessionFactory sf = createSessionFactory(locator);

         session = sf.createSession(false, true, true);

         SimpleString ADDRESS = SimpleString.of("some-queue");

         session.createQueue(QueueConfiguration.of(ADDRESS));

         sessionB = sf.createSession(false, true, true);
         sessionB.start();

         session.start();

         ClientConsumerInternal consReceiveOneAndHold = (ClientConsumerInternal) sessionB.createConsumer(ADDRESS);

         final CountDownLatch latchReceived = new CountDownLatch(2);

         final CountDownLatch latchDone = new CountDownLatch(1);

         // It can't close the session while the large message is being read
         final CountDownLatch latchRead = new CountDownLatch(1);

         // It should receive two messages and then give up
         class LocalHandler implements MessageHandler {

            boolean failed = false;

            int count = 0;

            @Override
            public synchronized void onMessage(final ClientMessage message) {
               try {
                  String str = getTextMessage(message);

                  failed = failed || !str.equals("Msg" + count);

                  message.acknowledge();
                  latchReceived.countDown();

                  if (count++ == 1) {
                     // it will hold here for a while
                     if (!latchDone.await(TIMEOUT, TimeUnit.SECONDS)) {
                        // a timed wait, so if the test fails, one less thread around
                        new Exception("ClientConsuemrWindowSizeTest Handler couldn't receive signal in less than 5 seconds").printStackTrace();
                        failed = true;
                     }

                     if (largeMessages) {
                        message.getBodyBuffer().readBytes(new byte[600]);
                     }

                     latchRead.countDown();
                  }
               } catch (Exception e) {
                  e.printStackTrace(); // Hudson / JUnit report
                  failed = true;
               }
            }
         }

         LocalHandler handler = new LocalHandler();

         ClientConsumer cons1 = session.createConsumer(ADDRESS);

         ClientProducer prod = session.createProducer(ADDRESS);

         for (int i = 0; i < numberOfMessages; i++) {
            ClientMessage msg = createTextMessage(session, "Msg" + i);
            if (largeMessages) {
               msg.getBodyBuffer().writeBytes(new byte[600]);
            }
            prod.send(msg);
         }

         consReceiveOneAndHold.setMessageHandler(handler);

         assertTrue(latchReceived.await(TIMEOUT, TimeUnit.SECONDS));

         assertEquals(0, consReceiveOneAndHold.getBufferSize());

         for (int i = 2; i < numberOfMessages; i++) {
            ClientMessage msg = cons1.receive(1000);
            assertNotNull(msg, "expected message at i = " + i);
            assertEquals("Msg" + i, getTextMessage(msg));
            msg.acknowledge();
         }

         assertEquals(0, consReceiveOneAndHold.getBufferSize());

         latchDone.countDown();

         // The test can' t close the session while the message is still being read, or it could interrupt the data
         assertTrue(latchRead.await(10, TimeUnit.SECONDS));

         session.close();
         session = null;

         sessionB.close();
         sessionB = null;

         assertEquals(0, getMessageCount(server, ADDRESS.toString()));

         assertFalse(handler.failed, "MessageHandler received a failure");

      } finally {
         try {
            if (session != null) {
               session.close();
            }
            if (sessionB != null) {
               sessionB.close();
            }
         } catch (Exception ignored) {
         }
      }
   }

   @Test
   public void testSlowConsumerOnMessageHandlerBufferOne() throws Exception {
      internalTestSlowConsumerOnMessageHandlerBufferOne(false);
   }

   private void internalTestSlowConsumerOnMessageHandlerBufferOne(final boolean largeMessage) throws Exception {
      ActiveMQServer server = createServer(false, isNetty());

      ClientSession sessionB = null;
      ClientSession session = null;

      try {
         final int numberOfMessages = 100;

         server.start();

         locator.setConsumerWindowSize(1);

         if (largeMessage) {
            locator.setMinLargeMessageSize(100);
         }

         ClientSessionFactory sf = createSessionFactory(locator);

         session = sf.createSession(false, true, true);

         SimpleString ADDRESS = SimpleString.of("some-queue");

         session.createQueue(QueueConfiguration.of(ADDRESS));

         sessionB = sf.createSession(false, true, true);
         sessionB.start();

         session.start();

         ClientConsumerInternal consReceiveOneAndHold = (ClientConsumerInternal) sessionB.createConsumer(ADDRESS);

         final CountDownLatch latchReceived = new CountDownLatch(2);
         final CountDownLatch latchReceivedBuffered = new CountDownLatch(3);

         final CountDownLatch latchDone = new CountDownLatch(1);

         // It should receive two messages and then give up
         class LocalHandler implements MessageHandler {

            boolean failed = false;

            int count = 0;

            @Override
            public synchronized void onMessage(final ClientMessage message) {
               try {
                  String str = getTextMessage(message);

                  failed = failed || !str.equals("Msg" + count);

                  message.acknowledge();
                  latchReceived.countDown();
                  latchReceivedBuffered.countDown();

                  if (count++ == 1) {
                     // it will hold here for a while
                     if (!latchDone.await(TIMEOUT, TimeUnit.SECONDS)) {
                        new Exception("ClientConsuemrWindowSizeTest Handler couldn't receive signal in less than 5 seconds").printStackTrace();
                        failed = true;
                     }
                  }
               } catch (Exception e) {
                  e.printStackTrace(); // Hudson / JUnit report
                  failed = true;
               }
            }
         }

         LocalHandler handler = new LocalHandler();

         ClientProducer prod = session.createProducer(ADDRESS);

         for (int i = 0; i < numberOfMessages; i++) {
            ClientMessage msg = createTextMessage(session, "Msg" + i);
            if (largeMessage) {
               msg.getBodyBuffer().writeBytes(new byte[600]);
            }
            prod.send(msg);
         }

         consReceiveOneAndHold.setMessageHandler(handler);

         assertTrue(latchReceived.await(TIMEOUT, TimeUnit.SECONDS));

         long timeout = System.currentTimeMillis() + 1000 * TIMEOUT;
         while (consReceiveOneAndHold.getBufferSize() == 0 && System.currentTimeMillis() < timeout) {
            Thread.sleep(10);
         }

         assertEquals(1, consReceiveOneAndHold.getBufferSize());

         ClientConsumer cons1 = session.createConsumer(ADDRESS);

         for (int i = 3; i < numberOfMessages; i++) {
            ClientMessage msg = cons1.receive(1000);
            assertNotNull(msg, "expected message at i = " + i);
            String text = getTextMessage(msg);
            assertEquals("Msg" + i, text);
            msg.acknowledge();
         }

         latchDone.countDown();

         assertTrue(latchReceivedBuffered.await(TIMEOUT, TimeUnit.SECONDS));

         session.close();
         session = null;

         sessionB.close();
         sessionB = null;

         assertEquals(0, getMessageCount(server, ADDRESS.toString()));

         assertFalse(handler.failed, "MessageHandler received a failure");

      } finally {
         try {
            if (session != null) {
               session.close();
            }
            if (sessionB != null) {
               sessionB.close();
            }
         } catch (Exception ignored) {
            ignored.printStackTrace();
         }
      }
   }

   @Test
   public void testNoWindowRoundRobin() throws Exception {
      testNoWindowRoundRobin(false);
   }

   private void testNoWindowRoundRobin(final boolean largeMessages) throws Exception {

      ActiveMQServer server = createServer(false, isNetty());

      ClientSession sessionA = null;
      ClientSession sessionB = null;

      try {
         final int numberOfMessages = 100;

         server.start();

         locator.setConsumerWindowSize(-1);
         if (largeMessages) {
            locator.setMinLargeMessageSize(100);
         }

         ClientSessionFactory sf = createSessionFactory(locator);

         sessionA = sf.createSession(false, true, true);

         SimpleString ADDRESS = SimpleString.of("some-queue");

         sessionA.createQueue(QueueConfiguration.of(ADDRESS));

         sessionB = sf.createSession(false, true, true);

         sessionA.start();
         sessionB.start();

         ClientConsumerInternal consA = (ClientConsumerInternal) sessionA.createConsumer(ADDRESS);

         ClientConsumerInternal consB = (ClientConsumerInternal) sessionB.createConsumer(ADDRESS);

         {
            // We can only guarantee round robing with WindowSize = -1, after the ServerConsumer object received
            // SessionConsumerFlowCreditMessage(-1)
            // Since that is done asynchronously we verify that the information was received before we proceed on
            // sending messages or else the distribution won't be
            // even as expected by the test
            Bindings bindings = server.getPostOffice().getBindingsForAddress(ADDRESS);

            assertEquals(1, bindings.getBindings().size());

            for (Binding binding : bindings.getBindings()) {
               Collection<Consumer> consumers = ((QueueBinding) binding).getQueue().getConsumers();

               for (Consumer consumer : consumers) {
                  ServerConsumerImpl consumerImpl = (ServerConsumerImpl) consumer;
                  long timeout = System.currentTimeMillis() + 5000;
                  while (timeout > System.currentTimeMillis() && consumerImpl.getAvailableCredits() != null) {
                     Thread.sleep(10);
                  }

                  assertNull(consumerImpl.getAvailableCredits());
               }
            }
         }

         ClientProducer prod = sessionA.createProducer(ADDRESS);

         for (int i = 0; i < numberOfMessages; i++) {
            ClientMessage msg = createTextMessage(sessionA, "Msg" + i);
            if (largeMessages) {
               msg.getBodyBuffer().writeBytes(new byte[600]);
            }
            prod.send(msg);
         }

         long timeout = System.currentTimeMillis() + TIMEOUT * 1000;

         boolean foundA = false;
         boolean foundB = false;

         do {
            foundA = consA.getBufferSize() == numberOfMessages / 2;
            foundB = consB.getBufferSize() == numberOfMessages / 2;

            Thread.sleep(10);
         }
         while ((!foundA || !foundB) && System.currentTimeMillis() < timeout);

         assertTrue(foundA, "ConsumerA didn't receive the expected number of messages on buffer (consA=" + consA.getBufferSize() +
                              ", consB=" +
                              consB.getBufferSize() +
                              ") foundA = " +
                              foundA +
                              " foundB = " +
                              foundB);
         assertTrue(foundB, "ConsumerB didn't receive the expected number of messages on buffer (consA=" + consA.getBufferSize() +
                              ", consB=" +
                              consB.getBufferSize() +
                              ") foundA = " +
                              foundA +
                              " foundB = " +
                              foundB);

      } finally {
         try {
            if (sessionA != null) {
               sessionA.close();
            }
            if (sessionB != null) {
               sessionB.close();
            }
         } catch (Exception ignored) {
         }
      }
   }

   @Test
   public void testDefaultConsumerWindowSize() throws Exception {
      ActiveMQServer messagingService = createServer(false, isNetty());

      messagingService.start();
      messagingService.createQueue(QueueConfiguration.of(queueA).setRoutingType(RoutingType.ANYCAST));

      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession session = cf.createSession(false, true, true);
      ClientConsumerImpl consumer = (ClientConsumerImpl) session.createConsumer(queueA);

      consumer.start();

      assertEquals(ActiveMQClient.DEFAULT_CONSUMER_WINDOW_SIZE / 2, consumer.getClientWindowSize());
   }

   @Test
   public void testConsumerWindowSizeAddressSettings() throws Exception {
      ActiveMQServer messagingService = createServer(false, isNetty());

      final int defaultConsumerWindowSize = 1024 * 5;
      final AddressSettings settings = new AddressSettings();
      settings.setDefaultConsumerWindowSize(defaultConsumerWindowSize);
      messagingService.getConfiguration()
            .getAddressSettings().put(queueA.toString(), settings);

      messagingService.start();
      messagingService.createQueue(QueueConfiguration.of(queueA).setRoutingType(RoutingType.ANYCAST));

      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession session = cf.createSession(false, true, true);
      ClientConsumerImpl consumer = (ClientConsumerImpl) session.createConsumer(queueA);

      session.start();

      assertEquals(defaultConsumerWindowSize / 2, consumer.getClientWindowSize());
   }

   @Test
   public void testConsumerWindowSizeAddressSettingsDifferentAddressAndQueueName() throws Exception {
      ActiveMQServer messagingService = createServer(false, isNetty());

      final int defaultConsumerWindowSize = 1024 * 5;
      final AddressSettings settings = new AddressSettings();
      settings.setDefaultConsumerWindowSize(defaultConsumerWindowSize);
      messagingService.getConfiguration()
            .getAddressSettings().put(addressA.toString(), settings);

      messagingService.start();
      messagingService.createQueue(QueueConfiguration.of(queueA).setAddress(addressA).setRoutingType(RoutingType.ANYCAST));

      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession session = cf.createSession(false, true, true);
      ClientConsumerImpl consumer = (ClientConsumerImpl) session.createConsumer(queueA);

      session.start();

      assertEquals(defaultConsumerWindowSize / 2, consumer.getClientWindowSize());

      ServerSession ss = messagingService.getSessionByID(((ClientSessionImpl)session).getName());
      ServerConsumerImpl cons = (ServerConsumerImpl) ss.locateConsumer(consumer.getConsumerContext().getId());

      assertTrue(Wait.waitFor(() -> cons.getAvailableCredits().get() == consumer.getClientWindowSize() * 2, 5000, 50));
   }

   @Test
   public void testConsumerWindowSizeAddressSettingsWildCard() throws Exception {
      ActiveMQServer messagingService = createServer(false, isNetty());

      final int defaultConsumerWindowSize = 1024 * 5;
      final AddressSettings settings = new AddressSettings();
      settings.setDefaultConsumerWindowSize(defaultConsumerWindowSize);
      messagingService.getConfiguration()
         .getAddressSettings().put("#", settings);

      messagingService.start();
      messagingService.createQueue(QueueConfiguration.of(queueA).setRoutingType(RoutingType.ANYCAST));

      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession session = cf.createSession(false, true, true);
      ClientConsumerImpl consumer = (ClientConsumerImpl) session.createConsumer(queueA);
      ClientConsumerImpl consumer2 = (ClientConsumerImpl) session.createConsumer(queueA);

      session.start();

      assertEquals(defaultConsumerWindowSize / 2, consumer.getClientWindowSize());
      assertEquals(defaultConsumerWindowSize / 2, consumer2.getClientWindowSize());
   }
}
