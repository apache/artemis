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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.io.nio.NIOSequentialFileFactory;
import org.apache.activemq.artemis.core.journal.PreparedTransactionInfo;
import org.apache.activemq.artemis.core.journal.RecordInfo;
import org.apache.activemq.artemis.core.journal.impl.JournalImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.nativo.jlibaio.LibaioContext;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class NIOMultiThreadCompactorStressTest extends ActiveMQTestBase {


   final SimpleString ADDRESS = SimpleString.of("SomeAddress");

   final SimpleString QUEUE = SimpleString.of("SomeQueue");

   private ActiveMQServer server;

   private ClientSessionFactory sf;

   private ServerLocator locator;

   protected int getNumberOfIterations() {
      return 3;
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      locator = createInVMNonHALocator().setBlockOnNonDurableSend(false).setBlockOnAcknowledge(false);
   }

   @Test
   public void testMultiThreadCompact() throws Throwable {
      setupServer(getJournalType());
      for (int i = 0; i < getNumberOfIterations(); i++) {
         System.out.println("######################################");
         System.out.println("test # " + i);

         internalTestProduceAndConsume();
         stopServer();

         NIOSequentialFileFactory factory = new NIOSequentialFileFactory(new File(getJournalDir()), 1);
         JournalImpl journal = new JournalImpl(ActiveMQDefaultConfiguration.getDefaultJournalFileSize(), 2, 2, 0, 0, factory, "activemq-data", "amq", 100);

         List<RecordInfo> committedRecords = new ArrayList<>();
         List<PreparedTransactionInfo> preparedTransactions = new ArrayList<>();

         journal.start();
         journal.load(committedRecords, preparedTransactions, null);

         assertEquals(0, committedRecords.size());
         assertEquals(0, preparedTransactions.size());

         System.out.println("DataFiles = " + journal.getDataFilesCount());

         if (i % 2 == 0 && i > 0) {
            System.out.println("DataFiles = " + journal.getDataFilesCount());

            journal.forceMoveNextFile();
            journal.debugWait();
            journal.checkReclaimStatus();

            if (journal.getDataFilesCount() != 0) {
               System.out.println("DebugJournal:" + journal.debug());
            }
            assertEquals(0, journal.getDataFilesCount());
         }

         journal.stop();
         journal = null;

         setupServer(getJournalType());

      }
   }

   protected JournalType getJournalType() {
      return JournalType.NIO;
   }

   private void addEmptyTransaction(final Xid xid) throws Exception {
      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession session = sf.createSession(true, false, false);
      session.start(xid, XAResource.TMNOFLAGS);
      session.end(xid, XAResource.TMSUCCESS);
      session.prepare(xid);
      session.close();
      sf.close();
   }

   private void checkEmptyXID(final Xid xid) throws Exception {
      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession session = sf.createSession(true, false, false);

      Xid[] xids = session.recover(XAResource.TMSTARTRSCAN);
      assertEquals(1, xids.length);
      assertEquals(xid, xids[0]);

      session.rollback(xid);

      session.close();
      sf.close();
   }

   public void internalTestProduceAndConsume() throws Throwable {

      addBogusData(100, "LAZY-QUEUE");

      Xid xid = null;
      xid = newXID();
      addEmptyTransaction(xid);

      System.out.println(getTemporaryDir());
      boolean transactionalOnConsume = true;
      boolean transactionalOnProduce = true;
      int numberOfConsumers = 30;
      // this test assumes numberOfConsumers == numberOfProducers
      int numberOfProducers = numberOfConsumers;
      int produceMessage = 5000;
      int commitIntervalProduce = 100;
      int consumeMessage = (int) (produceMessage * 0.9);
      int commitIntervalConsume = 100;

      System.out.println("ConsumeMessages = " + consumeMessage + " produceMessage = " + produceMessage);

      // Number of messages expected to be received after restart
      int numberOfMessagesExpected = (produceMessage - consumeMessage) * numberOfConsumers;

      CountDownLatch latchReady = new CountDownLatch(numberOfConsumers + numberOfProducers);

      CountDownLatch latchStart = new CountDownLatch(1);

      List<BaseThread> threads = new ArrayList<>();

      ProducerThread[] prod = new ProducerThread[numberOfProducers];
      for (int i = 0; i < numberOfProducers; i++) {
         prod[i] = new ProducerThread(i, latchReady, latchStart, transactionalOnConsume, produceMessage, commitIntervalProduce);
         prod[i].start();
         threads.add(prod[i]);
      }

      ConsumerThread[] cons = new ConsumerThread[numberOfConsumers];

      for (int i = 0; i < numberOfConsumers; i++) {
         cons[i] = new ConsumerThread(i, latchReady, latchStart, transactionalOnProduce, consumeMessage, commitIntervalConsume);
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

      setupServer(getJournalType());

      drainQueue(numberOfMessagesExpected, QUEUE);
      drainQueue(100, SimpleString.of("LAZY-QUEUE"));

      server.stop();

      setupServer(getJournalType());
      drainQueue(0, QUEUE);
      drainQueue(0, SimpleString.of("LAZY-QUEUE"));

      checkEmptyXID(xid);

   }

   private void drainQueue(final int numberOfMessagesExpected, final SimpleString queue) throws ActiveMQException {
      ClientSession sess = sf.createSession(true, true);

      ClientConsumer consumer = sess.createConsumer(queue);

      sess.start();

      for (int i = 0; i < numberOfMessagesExpected; i++) {
         ClientMessage msg = consumer.receive(5000);
         assertNotNull(msg);

         if (i % 100 == 0) {
            // System.out.println("Received #" + i + "  on thread after start");
         }
         msg.acknowledge();
      }

      assertNull(consumer.receiveImmediate());

      sess.close();
   }

   private void addBogusData(final int nmessages, final String queue) throws ActiveMQException {
      ClientSession session = sf.createSession(false, false);
      try {
         session.createQueue(QueueConfiguration.of(queue));
      } catch (Exception ignored) {
      }

      ClientProducer prod = session.createProducer(queue);
      for (int i = 0; i < nmessages; i++) {
         ClientMessage msg = session.createMessage(true);
         msg.getBodyBuffer().writeBytes(new byte[1024]);
         prod.send(msg);
      }
      session.commit();

      session.start();

      ClientConsumer cons = session.createConsumer(queue);
      assertNotNull(cons.receive(1000));
      session.rollback();
      session.close();
   }

   protected void stopServer() throws Exception {
      try {
         if (server != null && server.isStarted()) {
            server.stop();
         }
      } catch (Throwable e) {
         e.printStackTrace(System.out); // System.out => junit reports
      }

      sf = null;
   }

   private void setupServer(JournalType journalType) throws Exception {
      if (!LibaioContext.isLoaded()) {
         journalType = JournalType.NIO;
      }
      if (server == null) {
         Configuration config = createDefaultNettyConfig().setJournalFileSize(ActiveMQDefaultConfiguration.getDefaultJournalFileSize()).setJournalType(journalType).setJMXManagementEnabled(false).setJournalFileSize(ActiveMQDefaultConfiguration.getDefaultJournalFileSize()).setJournalMinFiles(ActiveMQDefaultConfiguration.getDefaultJournalMinFiles()).setJournalCompactMinFiles(ActiveMQDefaultConfiguration.getDefaultJournalCompactMinFiles()).setJournalCompactPercentage(ActiveMQDefaultConfiguration.getDefaultJournalCompactPercentage())
            // This test is supposed to not sync.. All the ACKs are async, and it was supposed to not sync
            .setJournalSyncNonTransactional(false);

         // config.setJournalCompactMinFiles(0);
         // config.setJournalCompactPercentage(0);

         server = createServer(true, config);
      }

      server.start();

      ServerLocator locator = createNettyNonHALocator().setBlockOnDurableSend(false).setBlockOnAcknowledge(false);

      sf = createSessionFactory(locator);

      ClientSession sess = sf.createSession();

      try {
         sess.createQueue(QueueConfiguration.of(QUEUE).setAddress(ADDRESS));
      } catch (Exception ignored) {
      }

      sess.close();
   }




   class BaseThread extends Thread {

      Throwable e;

      final CountDownLatch latchReady;

      final CountDownLatch latchStart;

      final int numberOfMessages;

      final int commitInterval;

      final boolean transactional;

      BaseThread(final String name,
                 final CountDownLatch latchReady,
                 final CountDownLatch latchStart,
                 final boolean transactional,
                 final int numberOfMessages,
                 final int commitInterval) {
         super(name);
         this.transactional = transactional;
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
                     final boolean transactional,
                     final int numberOfMessages,
                     final int commitInterval) {
         super("ClientProducer:" + id, latchReady, latchStart, transactional, numberOfMessages, commitInterval);
      }

      @Override
      public void run() {
         ClientSession session = null;
         latchReady.countDown();
         try {
            ActiveMQTestBase.waitForLatch(latchStart);
            session = sf.createSession(!transactional, !transactional);
            ClientProducer prod = session.createProducer(ADDRESS);
            for (int i = 0; i < numberOfMessages; i++) {
               if (transactional) {
                  if (i % commitInterval == 0) {
                     session.commit();
                  }
               }
               if (i % 100 == 0) {
                  // System.out.println(Thread.currentThread().getName() + "::sent #" + i);
               }
               ClientMessage msg = session.createMessage(true);

               prod.send(msg);
            }

            if (transactional) {
               session.commit();
            }

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
               this.e = e;
            }
         }
      }
   }

   class ConsumerThread extends BaseThread {

      ConsumerThread(final int id,
                     final CountDownLatch latchReady,
                     final CountDownLatch latchStart,
                     final boolean transactional,
                     final int numberOfMessages,
                     final int commitInterval) {
         super("ClientConsumer:" + id, latchReady, latchStart, transactional, numberOfMessages, commitInterval);
      }

      @Override
      public void run() {
         ClientSession session = null;
         latchReady.countDown();
         try {
            ActiveMQTestBase.waitForLatch(latchStart);
            session = sf.createSession(!transactional, !transactional);
            session.start();
            ClientConsumer cons = session.createConsumer(QUEUE);
            for (int i = 0; i < numberOfMessages; i++) {
               ClientMessage msg = cons.receive(60 * 1000);
               msg.acknowledge();
               if (i % commitInterval == 0) {
                  session.commit();
               }
               if (i % 100 == 0) {
                  // System.out.println(Thread.currentThread().getName() + "::received #" + i);
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
