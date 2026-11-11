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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A MultiThreadConsumerStressTest
 * <p>
 * This test validates consuming / sending messages while compacting is working
 */
public class MultiThreadConsumerStressTest extends ActiveMQTestBase {


   final SimpleString ADDRESS = SimpleString.of("SomeAddress");

   final SimpleString QUEUE = SimpleString.of("SomeQueue");

   private ActiveMQServer server;

   private ClientSessionFactory sf;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      setupServer(JournalType.NIO);
   }

   @Test
   public void testProduceAndConsume() throws Throwable {
      int numberOfConsumers = 5;
      // this test assumes numberOfConsumers == numberOfProducers
      int numberOfProducers = numberOfConsumers;
      int produceMessage = 10000;
      int commitIntervalProduce = 100;
      int consumeMessage = (int) (produceMessage * 0.9);
      int commitIntervalConsume = 100;

      ClientSession session = sf.createSession(false, false);
      session.createQueue(QueueConfiguration.of("compact-queue").setAddress("compact"));

      ClientProducer producer = session.createProducer("compact");

      for (int i = 0; i < 100; i++) {
         producer.send(session.createMessage(true));
      }

      session.commit();

      // Number of messages expected to be received after restart
      int numberOfMessagesExpected = (produceMessage - consumeMessage) * numberOfConsumers;

      CountDownLatch latchReady = new CountDownLatch(numberOfConsumers + numberOfProducers);

      CountDownLatch latchStart = new CountDownLatch(1);

      List<BaseThread> threads = new ArrayList<>();

      ProducerThread[] prod = new ProducerThread[numberOfProducers];
      for (int i = 0; i < numberOfProducers; i++) {
         prod[i] = new ProducerThread(i, latchReady, latchStart, produceMessage, commitIntervalProduce);
         prod[i].start();
         threads.add(prod[i]);
      }

      ConsumerThread[] cons = new ConsumerThread[numberOfConsumers];

      for (int i = 0; i < numberOfConsumers; i++) {
         cons[i] = new ConsumerThread(i, latchReady, latchStart, consumeMessage, commitIntervalConsume);
         cons[i].start();
         threads.add(cons[i]);
      }

      ActiveMQTestBase.waitForLatch(latchReady);
      latchStart.countDown();

      for (BaseThread t : threads) {
         t.join();
         if (t.e != null) {
            throw t.e;
         }
      }

      server.stop();

      setupServer(JournalType.NIO);

      ClientSession sess = sf.createSession(true, true);

      ClientConsumer consumer = sess.createConsumer(QUEUE);

      sess.start();

      for (int i = 0; i < numberOfMessagesExpected; i++) {
         ClientMessage msg = consumer.receive(5000);
         assertNotNull(msg);

         if (i % 1000 == 0) {
            System.out.println("Received #" + i + "  on thread before end");
         }
         msg.acknowledge();
      }

      assertNull(consumer.receiveImmediate());

      sess.close();

   }

   private void setupServer(final JournalType journalType) throws Exception {
      Configuration config = createDefaultNettyConfig().setJournalType(journalType).setJournalFileSize(ActiveMQDefaultConfiguration.getDefaultJournalFileSize()).setJournalMinFiles(ActiveMQDefaultConfiguration.getDefaultJournalMinFiles()).setJournalCompactMinFiles(2).setJournalCompactPercentage(50);

      server = createServer(true, config);

      server.start();

      ServerLocator locator = createNettyNonHALocator().setBlockOnDurableSend(false).setBlockOnNonDurableSend(false).setBlockOnAcknowledge(false);

      sf = createSessionFactory(locator);

      ClientSession sess = sf.createSession();

      try {
         sess.createQueue(QueueConfiguration.of(QUEUE).setAddress(ADDRESS));
      } catch (Exception ignored) {
      }

      sess.close();
      locator.close();
      locator = createInVMNonHALocator();
      sf = createSessionFactory(locator);
   }



   class BaseThread extends Thread {

      Throwable e;

      final CountDownLatch latchReady;

      final CountDownLatch latchStart;

      final int numberOfMessages;

      final int commitInterval;

      BaseThread(final String name,
                 final CountDownLatch latchReady,
                 final CountDownLatch latchStart,
                 final int numberOfMessages,
                 final int commitInterval) {
         super(name);
         this.latchReady = latchReady;
         this.latchStart = latchStart;
         this.commitInterval = commitInterval;
         this.numberOfMessages = numberOfMessages;
      }

   }

   class ProducerThread extends BaseThread {

      ProducerThread(final int id,
                     final CountDownLatch latchReady,
                     final CountDownLatch latchStart,
                     final int numberOfMessages,
                     final int commitInterval) {
         super("ClientProducer:" + id, latchReady, latchStart, numberOfMessages, commitInterval);
      }

      @Override
      public void run() {
         ClientSession session = null;
         latchReady.countDown();
         try {
            ActiveMQTestBase.waitForLatch(latchStart);
            session = sf.createSession(false, false);
            ClientProducer prod = session.createProducer(ADDRESS);
            for (int i = 0; i < numberOfMessages; i++) {
               if (i % commitInterval == 0) {
                  session.commit();
               }
               if (i % 1000 == 0) {
                  // System.out.println(Thread.currentThread().getName() + "::received #" + i);
               }
               ClientMessage msg = session.createMessage(true);
               prod.send(msg);
            }

            session.commit();

            System.out.println("Thread " + Thread.currentThread().getName() +
                                  " sent " +
                                  numberOfMessages +
                                  "  messages");
         } catch (Throwable e) {
            e.printStackTrace();
            this.e = e;
         } finally {
            try {
               session.close();
            } catch (Throwable e) {
               e.printStackTrace();
            }
         }
      }
   }

   class ConsumerThread extends BaseThread {

      ConsumerThread(final int id,
                     final CountDownLatch latchReady,
                     final CountDownLatch latchStart,
                     final int numberOfMessages,
                     final int commitInterval) {
         super("ClientConsumer:" + id, latchReady, latchStart, numberOfMessages, commitInterval);
      }

      @Override
      public void run() {
         ClientSession session = null;
         latchReady.countDown();
         try {
            ActiveMQTestBase.waitForLatch(latchStart);
            session = sf.createSession(false, false);
            session.start();
            ClientConsumer cons = session.createConsumer(QUEUE);
            for (int i = 0; i < numberOfMessages; i++) {
               ClientMessage msg = cons.receive(60 * 1000);
               msg.acknowledge();
               if (i % commitInterval == 0) {
                  session.commit();
               }
               if (i % 1000 == 0) {
                  // System.out.println(Thread.currentThread().getName() + "::sent #" + i);
               }
            }

            System.out.println("Thread " + Thread.currentThread().getName() +
                                  " received " +
                                  numberOfMessages +
                                  " messages");

            session.commit();
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

}
