/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.tests.performance.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.client.DefaultConnectionProperties;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

public class SendReceiveMultiThreadTest extends ActiveMQTestBase {

   final String DIRECTORY = "./target/journaltmp";

   ConnectionFactory cf;

   Destination destination;

   AtomicInteger received = new AtomicInteger(0);

   AtomicInteger sent = new AtomicInteger(0);

   int NUMBER_OF_THREADS = 400;
   int NUMBER_OF_MESSAGES = 5000;

   CountDownLatch receivedLatch = new CountDownLatch(NUMBER_OF_MESSAGES * NUMBER_OF_THREADS);

   @Test
   public void testMultipleWrites() throws Exception {
      deleteDirectory(new File(DIRECTORY));
      ActiveMQServer server = createServer(true);
      server.getConfiguration().setJournalFileSize(10 * 1024 * 1024);
      server.getConfiguration().setJournalMinFiles(2);
      server.getConfiguration().setJournalCompactMinFiles(ActiveMQDefaultConfiguration.getDefaultJournalCompactMinFiles());
      server.getConfiguration().setJournalCompactPercentage(ActiveMQDefaultConfiguration.getDefaultJournalCompactPercentage());
      server.getConfiguration().setJournalType(JournalType.ASYNCIO);
      server.getConfiguration().addAcceptorConfiguration("core", DefaultConnectionProperties.DEFAULT_BROKER_BIND_URL);
      server.getConfiguration().setJournalDirectory(DIRECTORY + "/journal");
      server.getConfiguration().setBindingsDirectory(DIRECTORY + "/bindings");
      server.getConfiguration().setPagingDirectory(DIRECTORY + "/paging");
      server.getConfiguration().setLargeMessagesDirectory(DIRECTORY + "/largemessage");
      server.getConfiguration().setJournalMaxIO_AIO(200);

      // TODO Setup Acceptors

      server.start();

      Queue queue = server.createQueue(QueueConfiguration.of("performanceQueue").setRoutingType(RoutingType.ANYCAST));

      Queue queue2 = server.createQueue(QueueConfiguration.of("stationaryQueue").setRoutingType(RoutingType.ANYCAST));

      MyThread[] threads = new MyThread[NUMBER_OF_THREADS];

      ConsumerThread[] cthreads = new ConsumerThread[NUMBER_OF_THREADS];

      final CountDownLatch alignFlag = new CountDownLatch(NUMBER_OF_THREADS);
      final CountDownLatch startFlag = new CountDownLatch(1);
      final CountDownLatch finishFlag = new CountDownLatch(NUMBER_OF_THREADS);

      cf = new ActiveMQConnectionFactory();

      Thread slowSending = new Thread(() -> {
         Connection conn = null;
         try {
            conn = cf.createConnection();
            Session session = conn.createSession(true, Session.SESSION_TRANSACTED);
            MessageProducer producer = session.createProducer(ActiveMQJMSClient.createQueue("stationaryQueue"));

            conn.start();
            MessageConsumer consumer = session.createConsumer(ActiveMQJMSClient.createQueue("stationaryQueue"));

            while (true) {
               for (int i = 0; i < 10; i++) {
                  System.out.println("stationed message");
                  producer.send(session.createTextMessage("stationed"));
                  session.commit();

                  Thread.sleep(1000);
               }

               for (int i = 0; i < 10; i++) {
                  consumer.receive(5000);
                  session.commit();
                  System.out.println("Receiving stationed");
                  Thread.sleep(1000);
               }
            }
         } catch (Exception e) {
            e.printStackTrace();
         } finally {
            try {
               conn.close();
            } catch (Exception ignored) {

            }
         }

      });

      slowSending.start();

      destination = ActiveMQJMSClient.createQueue("performanceQueue");

      for (int i = 0; i < threads.length; i++) {
         threads[i] = new MyThread("sender::" + i, NUMBER_OF_MESSAGES, alignFlag, startFlag, finishFlag);
         cthreads[i] = new ConsumerThread(NUMBER_OF_MESSAGES);
      }

      for (ConsumerThread t : cthreads) {
         t.start();
      }

      for (MyThread t : threads) {
         t.start();
      }

      assertEquals(NUMBER_OF_THREADS, queue.getConsumerCount());

      alignFlag.await();

      long startTime = System.currentTimeMillis();
      startFlag.countDown();

      // I'm using a countDown to avoid measuring time spent on thread context from join.
      // i.e. i want to measure as soon as the loops are done
      finishFlag.await();
      long endtime = System.currentTimeMillis();

      receivedLatch.await();
      long endTimeConsuming = System.currentTimeMillis();

      for (ConsumerThread t : cthreads) {
         t.join();
         assertEquals(0, t.errors);
      }

      for (MyThread t : threads) {
         t.join();
         assertEquals(0, t.errors.get());
      }

      slowSending.interrupt();
      slowSending.join();

      server.stop();

      System.out.println("Time on sending:: " + (endtime - startTime));
      System.out.println("Time on consuming:: " + (endTimeConsuming - startTime));
   }

   class ConsumerThread extends Thread {

      final int numberOfMessages;

      Connection connection;
      Session session;

      MessageConsumer consumer;

      ConsumerThread(int numberOfMessages) throws Exception {
         super("consumerthread");
         this.numberOfMessages = numberOfMessages;

         connection = cf.createConnection();
         session = connection.createSession(true, Session.SESSION_TRANSACTED);
         consumer = session.createConsumer(destination);
         connection.start();
      }

      int errors = 0;

      @Override
      public void run() {
         try {

            for (int i = 0; i < numberOfMessages; i++) {
               Message message = consumer.receive(50000);
               if (message == null) {
                  System.err.println("Could not receive message at i = " + numberOfMessages);
                  errors++;
                  break;
               }

               int r = received.incrementAndGet();

               if (r % 1000 == 0) {
                  System.out.println("Received " + r + " messages");
               }

               if (i % 50 == 0) {
                  session.commit();
               }

               receivedLatch.countDown();
            }
            session.commit();
            connection.close();
         } catch (Exception e) {
            e.printStackTrace();
            errors++;
         }

      }
   }

   class MyThread extends Thread {

      final int numberOfMessages;
      final AtomicInteger errors = new AtomicInteger(0);

      final CountDownLatch align;
      final CountDownLatch start;
      final CountDownLatch finish;

      MyThread(String name, int numberOfMessages, CountDownLatch align, CountDownLatch start, CountDownLatch finish) {
         super(name);
         this.numberOfMessages = numberOfMessages;
         this.align = align;
         this.start = start;
         this.finish = finish;
      }

      @Override
      public void run() {
         try {

            Connection connection = cf.createConnection();

            Session session = connection.createSession(true, Session.SESSION_TRANSACTED);

            MessageProducer producer = session.createProducer(destination);

            align.countDown();
            start.await();

            for (int i = 0; i < numberOfMessages; i++) {
               BytesMessage msg = session.createBytesMessage();
               msg.writeBytes(new byte[1024]);
               producer.send(msg);
               session.commit();

               int s = sent.incrementAndGet();
               if (s % 1000 == 0) {
                  System.out.println("Sent " + s);
               }
            }

            connection.close();
            System.out.println("Send " + numberOfMessages + " messages on thread " + Thread.currentThread().getName());
         } catch (Exception e) {
            e.printStackTrace();
            errors.incrementAndGet();
         } finally {
            finish.countDown();
         }
      }
   }
}
