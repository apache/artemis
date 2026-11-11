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
package org.apache.activemq.artemis.tests.soak.replicationflow;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.tests.soak.SoakTestBase;
import org.apache.activemq.artemis.util.ServerUtil;
import org.apache.activemq.artemis.utils.ReusableLatch;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class ReplicationFlowControlTest extends SoakTestBase {


   public static final String SERVER_NAME_0 = "replicated-static0";
   public static final String SERVER_NAME_1 = "replicated-static1";

   @BeforeAll
   public static void createServers() throws Exception {
      {
         File serverLocation = getFileServerLocation(SERVER_NAME_0);
         deleteDirectory(serverLocation);

         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer.setAllowAnonymous(true).setNoWeb(true).setArtemisInstance(serverLocation);
         cliCreateServer.setConfiguration("./src/main/resources/servers/replicated-static0");
         cliCreateServer.createServer();
      }
      {
         File serverLocation = getFileServerLocation(SERVER_NAME_1);
         deleteDirectory(serverLocation);

         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer.setAllowAnonymous(true).setNoWeb(true).setArtemisInstance(serverLocation);
         cliCreateServer.setConfiguration("./src/main/resources/servers/replicated-static1");
         cliCreateServer.createServer();
      }
   }

   List<Consumer> consumers = new ArrayList<>();
   private static Process server0;
   private static Process server1;

   static final int NUM_MESSAGES = 50_000;
   static final int START_CONSUMERS = 10_000;
   static final int START_SERVER = 15_000;
   static final int NUMBER_OF_CONSUMERS = 10;
   static final ReusableLatch latch = new ReusableLatch(NUM_MESSAGES);

   static AtomicBoolean running = new AtomicBoolean(true);
   static AtomicInteger totalConsumed = new AtomicInteger(0);


   @BeforeEach
   public void before() throws Exception {
      cleanupData(SERVER_NAME_0);
      cleanupData(SERVER_NAME_1);
      disableCheckThread();
   }

   @AfterEach
   @Override
   public void after() throws Exception {
      super.after();
   }

   @Test
   @Timeout(value = 5, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   public void testPageWhileSynchronizingReplica() throws Exception {
      internalTest(false);
   }

   @Test
   @Timeout(value = 5, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   public void testPageWhileSyncFailover() throws Exception {
      internalTest(true);
   }

   private void internalTest(boolean failover) throws Exception {

      int KILL_SERVER = failover ? 50_000 : -1;

      Connection connection = null;

      try {
         server0 = startServer(SERVER_NAME_0, 0, 30000);

         ConnectionFactory connectionFactory = new ActiveMQConnectionFactory();

         connection = connectionFactory.createConnection();

         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);

         Queue queue = session.createQueue("exampleQueue");

         MessageProducer producer = session.createProducer(queue);

         BytesMessage bytesMessage = session.createBytesMessage();
         bytesMessage.writeBytes(new byte[20 * 1024]);

         for (int i = 0; i < NUM_MESSAGES; i++) {

            producer.send(bytesMessage);
            if (i % 1000 == 0) {
               System.out.println("Sent " + i + " messages, consumed=" + totalConsumed.get());
               session.commit();
            }

            if (i == START_CONSUMERS) {
               System.out.println("Starting consumers");
               startConsumers(!failover); // if failover, no AMQP
            }

            if (KILL_SERVER >= 0 && i == KILL_SERVER) {
               session.commit();
               System.out.println("Killing server");
               ServerUtil.killServer(server0, true);
               Thread.sleep(2000);
               connection.close();
               connection = connectionFactory.createConnection();

               session = connection.createSession(true, Session.SESSION_TRANSACTED);

               queue = session.createQueue("exampleQueue");

               producer = session.createProducer(queue);

            }

            if (i == START_SERVER) {
               System.out.println("Starting extra server");
               server1 = startServer(SERVER_NAME_1, 0, 30000);
            }

         }

         session.commit();

         System.out.println("Awaiting all consumers to finish");
         while (!latch.await(10, TimeUnit.SECONDS)) {
            fail("couldn't receive all messages");
         }

         running.set(false);

         for (Consumer consumer: consumers) {
            consumer.join(10000);
            if (consumer.isAlive()) {
               consumer.interrupt();
            }
         }

      } finally {

         running.set(false);

         if (connection != null) {
            connection.close();
         }

         for (Consumer consumer : consumers) {
            consumer.interrupt();
            consumer.join();
         }
      }

      assertRetentionFolder(getServerLocation(SERVER_NAME_0));
      assertRetentionFolder(getServerLocation(SERVER_NAME_1));
   }

   private void assertRetentionFolder(String serverLocation) {
      File retentionFolder = new File(serverLocation + "/data/retention");
      System.out.println("retention folder = " + retentionFolder.getAbsolutePath());
      File[] files = retentionFolder.listFiles();
      // it should be max = 2, however I'm giving some extra due to async factors..
      assertTrue(files != null && files.length <= 10, retentionFolder.getAbsolutePath() + " has " + (files == null ? "no files" : files.length + " elements"));
   }

   void startConsumers(boolean useAMQP) {
      for (int i = 0; i < NUMBER_OF_CONSUMERS; i++) {
         Consumer consumer = new Consumer(useAMQP && i % 2 == 0, i);
         consumer.start();
         consumers.add(consumer);
      }
   }

   static class Consumer extends Thread {

      ConnectionFactory factory;
      Connection connection;
      Session session;
      Queue queue;
      MessageConsumer consumer;
      int count = 0;
      int totalCount = 0;

      final int consumerID;

      final boolean amqp;

      Consumer(boolean amqp, int id) {
         super("amqp=" + amqp + ", id=" + id);
         this.amqp = amqp;
         this.consumerID = id;
      }

      @Override
      public String toString() {
         return "Consumer " + consumerID + ", amqp::" + amqp;
      }

      void connect() throws Exception {
         if (connection != null) {
            connection.close();
         }
         count = 0;
         if (amqp) {
            factory = new JmsConnectionFactory("amqp://localhost:61616");
         } else {
            factory = new ActiveMQConnectionFactory(); // using default is fine here
         }

         connection = factory.createConnection();
         session = connection.createSession(true, Session.SESSION_TRANSACTED);
         queue = session.createQueue("exampleQueue");
         consumer = session.createConsumer(queue);
         connection.start();
      }

      @Override
      public void run() {
         try {
            while (running.get()) {
               try {
                  if (connection == null) {
                     connect();
                  }

                  totalCount++;
                  if (totalCount % 1000 == 0) {
                     System.out.println(this + " received " + totalCount + " messages");
                  }

                  BytesMessage message = (BytesMessage) consumer.receive(5000);
                  if (message == null) {
                     System.out.println("Consumer " + this + " couldn't get a message");
                     if (count > 0) {
                        session.commit();
                        latch.countDown(count);
                        totalConsumed.addAndGet(count);
                        count = 0;
                     }
                  } else {
                     count++;

                     if (count == 100) {
                        session.commit();
                        latch.countDown(count);
                        totalConsumed.addAndGet(count);
                        count = 0;
                     }
                  }

               } catch (Exception e) {
                  e.printStackTrace();
               }
            }
         } finally {
            try {
               session.commit();
               connection.close();
            } catch (Throwable ignored) {
            }
         }

         System.out.println("Giving up the loop " + this);

      }

   }
}
