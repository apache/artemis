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
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class LargeJournalStressTest extends ActiveMQTestBase {



   private static final String AD1 = "ad1";

   private static final String AD2 = "ad2";

   private static final String Q1 = "q1";

   private static final String Q2 = "q2";

   private ActiveMQServer server;

   private ClientSessionFactory sf;

   private ServerLocator locator;



   @Test
   public void testMultiProducerAndCompactAIO() throws Throwable {
      internalTestMultiProducer(JournalType.ASYNCIO);
   }

   @Test
   public void testMultiProducerAndCompactNIO() throws Throwable {
      internalTestMultiProducer(JournalType.NIO);
   }

   public void internalTestMultiProducer(final JournalType journalType) throws Throwable {

      setupServer(journalType);

      final AtomicInteger numberOfMessages = new AtomicInteger(0);
      final int SLOW_INTERVAL = 25000;
      final int NUMBER_OF_FAST_MESSAGES = SLOW_INTERVAL * 50;

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
               ClientProducer prod = session.createProducer(LargeJournalStressTest.AD2);
               ClientProducer slowProd = sessionSlow.createProducer(LargeJournalStressTest.AD1);
               for (int i = 0; i < NUMBER_OF_FAST_MESSAGES; i++) {
                  if (i % SLOW_INTERVAL == 0) {
                     System.out.println("Sending slow message, msgs = " + i +
                                           " slowMessages = " +
                                           numberOfMessages.get());

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
               ClientConsumer cons = session.createConsumer(LargeJournalStressTest.Q2);
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

      ClientSession sess = sf.createSession(true, true);

      ClientConsumer cons = sess.createConsumer(LargeJournalStressTest.Q1);

      sess.start();

      for (int i = 0; i < numberOfMessages.intValue(); i++) {
         ClientMessage msg = cons.receive(10000);
         assertNotNull(msg);
         msg.acknowledge();
      }

      assertNull(cons.receiveImmediate());

      cons.close();

      cons = sess.createConsumer(LargeJournalStressTest.Q2);

      assertNull(cons.receiveImmediate());

      sess.close();

   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      clearDataRecreateServerDirs();

      locator = createInVMNonHALocator().setBlockOnAcknowledge(false).setBlockOnNonDurableSend(false).setBlockOnDurableSend(false);
   }

   private void setupServer(final JournalType journalType) throws Exception {
      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false).setJournalFileSize(ActiveMQDefaultConfiguration.getDefaultJournalFileSize()).setJournalType(journalType).setJournalCompactMinFiles(0).setJournalCompactPercentage(50);

      server = createServer(true, config);

      server.start();

      sf = createSessionFactory(locator);

      ClientSession sess = sf.createSession();

      try {
         sess.createQueue(QueueConfiguration.of(LargeJournalStressTest.Q1).setAddress(LargeJournalStressTest.AD1));
      } catch (Exception ignored) {
      }

      try {
         sess.createQueue(QueueConfiguration.of(LargeJournalStressTest.Q2).setAddress(LargeJournalStressTest.AD2));
      } catch (Exception ignored) {
      }

      sess.close();

      sf = createSessionFactory(locator);
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      locator.close();

      if (sf != null) {
         sf.close();
      }

      if (server != null) {
         server.stop();
      }

      // We don't super.tearDown here because in case of failure, the data may be useful for debug
      // so, we only clear data on setup.
      // super.tearDown();
   }

}
