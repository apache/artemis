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
package org.apache.activemq.artemis.tests.stress.journal;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.nativo.jlibaio.LibaioContext;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

public class CompactingStressTest extends ActiveMQTestBase {



   private static final String AD1 = "ad1";

   private static final String AD2 = "ad2";

   private static final String AD3 = "ad3";

   private static final String Q1 = "q1";

   private static final String Q2 = "q2";

   private static final String Q3 = "q3";

   private static final int TOT_AD3 = 5000;

   private ActiveMQServer server;

   private ClientSessionFactory sf;




   @Test
   public void testCleanupAIO() throws Throwable {
      if (LibaioContext.isLoaded()) {
         internalTestCleanup(JournalType.ASYNCIO);
      }
   }

   @Test
   public void testCleanupNIO() throws Throwable {
      internalTestCleanup(JournalType.NIO);
      tearDown();
      setUp();
   }

   private void internalTestCleanup(final JournalType journalType) throws Throwable {
      setupServer(journalType);

      ClientSession session = sf.createSession(false, true, true);

      ClientProducer prod = session.createProducer(CompactingStressTest.AD1);

      for (int i = 0; i < 500; i++) {
         prod.send(session.createMessage(true));
      }

      session.commit();

      prod.close();

      ClientConsumer cons = session.createConsumer(CompactingStressTest.Q2);
      prod = session.createProducer(CompactingStressTest.AD2);

      session.start();

      for (int i = 0; i < 200; i++) {
         System.out.println("Iteration " + i);
         // Sending non transactionally, so it would test non transactional stuff on the journal
         for (int j = 0; j < 1000; j++) {
            Message msg = session.createMessage(true);
            msg.getBodyBuffer().writeBytes(new byte[1024]);

            prod.send(msg);
         }

         // I need to guarantee a roundtrip to the server, to make sure everything is persisted
         session.commit();

         for (int j = 0; j < 1000; j++) {
            ClientMessage msg = cons.receive(2000);
            assertNotNull(msg);
            msg.acknowledge();
         }

         // I need to guarantee a roundtrip to the server, to make sure everything is persisted
         session.commit();

      }

      assertNull(cons.receiveImmediate());

      session.close();

      server.stop();

      setupServer(journalType);

      server.start();

      session = sf.createSession(false, true, true);
      cons = session.createConsumer(CompactingStressTest.Q1);
      session.start();

      for (int i = 0; i < 500; i++) {
         ClientMessage msg = cons.receive(1000);
         assertNotNull(msg);
         msg.acknowledge();
      }

      assertNull(cons.receiveImmediate());

      prod = session.createProducer(CompactingStressTest.AD2);

      session.close();

   }

   @Test
   public void testMultiProducerAndCompactAIO() throws Throwable {
      if (LibaioContext.isLoaded()) {
         internalTestMultiProducer(JournalType.ASYNCIO);
      }
   }

   @Test
   public void testMultiProducerAndCompactNIO() throws Throwable {
      internalTestMultiProducer(JournalType.NIO);
   }

   public void internalTestMultiProducer(final JournalType journalType) throws Throwable {

      setupServer(journalType);

      ClientSession session = sf.createSession(false, false);

      try {
         ClientProducer producer = session.createProducer(CompactingStressTest.AD3);

         ClientMessage msg = session.createMessage(true);

         for (int i = 0; i < CompactingStressTest.TOT_AD3; i++) {
            producer.send(msg);
            if (i % 100 == 0) {
               session.commit();
            }
         }

         session.commit();
      } finally {
         session.close();
      }

      server.stop();

      setupServer(journalType);

      final AtomicInteger numberOfMessages = new AtomicInteger(0);
      final int NUMBER_OF_FAST_MESSAGES = 100000;
      final int SLOW_INTERVAL = 100;

      final CountDownLatch latchReady = new CountDownLatch(2);
      final CountDownLatch latchStart = new CountDownLatch(1);

      class FastProducer extends Thread {

         Throwable e;

         FastProducer() {
            super("Fast-Thread");
         }

         @Override
         public void run() {
            ClientSession session = null;
            ClientSession sessionSlow = null;
            latchReady.countDown();
            try {
               ActiveMQTestBase.waitForLatch(latchStart);
               session = sf.createSession(true, true);
               sessionSlow = sf.createSession(false, false);
               ClientProducer prod = session.createProducer(CompactingStressTest.AD2);
               ClientProducer slowProd = sessionSlow.createProducer(CompactingStressTest.AD1);
               for (int i = 0; i < NUMBER_OF_FAST_MESSAGES; i++) {
                  if (i % SLOW_INTERVAL == 0) {
                     if (numberOfMessages.incrementAndGet() % 5 == 0) {
                        sessionSlow.commit();
                     }
                     slowProd.send(session.createMessage(true));
                  }
                  ClientMessage msg = session.createMessage(true);

                  prod.send(msg);
               }
               sessionSlow.commit();
            } catch (Throwable e) {
               this.e = e;
            } finally {
               try {
                  session.close();
               } catch (Throwable e) {
                  this.e = e;
               }
               try {
                  sessionSlow.close();
               } catch (Throwable e) {
                  this.e = e;
               }
            }
         }
      }

      class FastConsumer extends Thread {

         Throwable e;

         FastConsumer() {
            super("Fast-Consumer");
         }

         @Override
         public void run() {
            ClientSession session = null;
            latchReady.countDown();
            try {
               ActiveMQTestBase.waitForLatch(latchStart);
               session = sf.createSession(true, true);
               session.start();
               ClientConsumer cons = session.createConsumer(CompactingStressTest.Q2);
               for (int i = 0; i < NUMBER_OF_FAST_MESSAGES; i++) {
                  ClientMessage msg = cons.receive(60 * 1000);
                  msg.acknowledge();
               }

               assertNull(cons.receiveImmediate());
            } catch (Throwable e) {
               this.e = e;
            } finally {
               try {
                  session.close();
               } catch (Throwable e) {
                  this.e = e;
               }
            }
         }
      }

      FastConsumer f1 = new FastConsumer();
      f1.start();

      FastProducer p1 = new FastProducer();
      p1.start();

      ActiveMQTestBase.waitForLatch(latchReady);
      latchStart.countDown();

      p1.join();

      if (p1.e != null) {
         throw p1.e;
      }

      f1.join();

      if (f1.e != null) {
         throw f1.e;
      }

      sf.close();

      server.stop();

      setupServer(journalType);

      ClientSession sess = null;

      try {

         sess = sf.createSession(true, true);

         ClientConsumer cons = sess.createConsumer(CompactingStressTest.Q1);

         sess.start();

         for (int i = 0; i < numberOfMessages.intValue(); i++) {
            ClientMessage msg = cons.receive(60000);
            assertNotNull(msg);
            msg.acknowledge();
         }

         assertNull(cons.receiveImmediate());

         cons.close();

         cons = sess.createConsumer(CompactingStressTest.Q2);

         assertNull(cons.receiveImmediate());

         cons.close();

         cons = sess.createConsumer(CompactingStressTest.Q3);

         for (int i = 0; i < CompactingStressTest.TOT_AD3; i++) {
            ClientMessage msg = cons.receive(60000);
            assertNotNull(msg);
            msg.acknowledge();
         }

         assertNull(cons.receiveImmediate());

      } finally {
         try {
            sess.close();
         } catch (Throwable ignored) {
         }
      }
   }

   private void setupServer(final JournalType journalType) throws Exception {
      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false).setJournalFileSize(ActiveMQDefaultConfiguration.getDefaultJournalFileSize()).setJournalType(journalType).setJournalCompactMinFiles(10).setJournalCompactPercentage(50);

      server = createServer(true, config);

      server.start();

      ServerLocator locator = createInVMNonHALocator().setBlockOnDurableSend(false).setBlockOnAcknowledge(false);

      sf = createSessionFactory(locator);
      ClientSession sess = addClientSession(sf.createSession());

      try {
         sess.createQueue(QueueConfiguration.of(CompactingStressTest.Q1).setAddress(CompactingStressTest.AD1));
      } catch (Exception ignored) {
      }

      try {
         sess.createQueue(QueueConfiguration.of(CompactingStressTest.Q2).setAddress(CompactingStressTest.AD2));
      } catch (Exception ignored) {
      }

      try {
         sess.createQueue(QueueConfiguration.of(CompactingStressTest.Q3).setAddress(CompactingStressTest.AD3));
      } catch (Exception ignored) {
      }

      sess.close();
   }
}
