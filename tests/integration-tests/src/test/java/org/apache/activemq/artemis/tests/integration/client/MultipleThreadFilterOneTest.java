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

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Multiple Threads producing Messages, with Multiple Consumers with different queues, each queue with a different
 * filter This is similar to MultipleThreadFilterTwoTest but it uses multiple queues
 */
public class MultipleThreadFilterOneTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   final String ADDRESS = "ADDRESS";

   final int numberOfMessages = 2000;

   final int nThreads = 4;

   private static final int PAGE_MAX = 100 * 1024;

   private static final int PAGE_SIZE = 10 * 1024;

   private boolean isNetty = false;




   class SomeProducer extends Thread {

      final ClientSessionFactory factory;

      final ServerLocator locator;

      final ClientSession prodSession;

      public final AtomicInteger errors = new AtomicInteger(0);

      SomeProducer() throws Exception {
         locator = createNonHALocator(isNetty);
         factory = locator.createSessionFactory();
         prodSession = factory.createSession(false, false);
         sendMessages(numberOfMessages / 2);
      }

      @Override
      public void run() {
         try {
            sendMessages(numberOfMessages / 2);
         } catch (Throwable e) {
            e.printStackTrace();
            errors.incrementAndGet();
         } finally {
            try {
               prodSession.close();
               locator.close();
            } catch (Throwable ignored) {
               ignored.printStackTrace();
            }

         }
      }

      private void sendMessages(int msgs) throws ActiveMQException {
         ClientProducer producer = prodSession.createProducer(ADDRESS);

         for (int i = 0; i < msgs; i++) {
            ClientMessage message = prodSession.createMessage(true);
            message.putIntProperty("prodNR", i % nThreads);
            producer.send(message);

            if (i % 100 == 0) {
               prodSession.commit();
            }
         }
         prodSession.commit();

         producer.close();
      }
   }

   class SomeConsumer extends Thread {

      final ClientSessionFactory factory;

      final ServerLocator locator;

      final ClientSession consumerSession;

      ClientConsumer consumer;

      final int nr;

      final AtomicInteger errors = new AtomicInteger(0);

      SomeConsumer(int nr) throws Exception {
         locator = createNonHALocator(isNetty);
         factory = locator.createSessionFactory();
         consumerSession = factory.createSession(false, false);
         consumerSession.createQueue(QueueConfiguration.of("Q" + nr).setAddress(ADDRESS).setFilterString("prodNR=" + nr));
         consumer = consumerSession.createConsumer("Q" + nr);
         consumerSession.start();
         this.nr = nr;
      }

      @Override
      public void run() {
         try {
            consumerSession.start();

            for (int i = 0; i < numberOfMessages; i++) {
               ClientMessage msg = consumer.receive(15000);
               assertNotNull(msg);
               assertEquals(nr, msg.getIntProperty("prodNR").intValue());
               msg.acknowledge();

               if (i % 500 == 0) {
                  logger.debug("Consumed {}", i);
                  consumerSession.commit();
               }
            }

            assertNull(consumer.receiveImmediate());

            consumerSession.commit();
         } catch (Throwable e) {
            e.printStackTrace();
            errors.incrementAndGet();
         } finally {
            close();

         }
      }

      public void close() {
         try {
            consumerSession.close();
            locator.close();
         } catch (Throwable ignored) {
            ignored.printStackTrace();
         }
      }
   }

   @Test
   public void testSendingNetty() throws Exception {
      testSending(true, false);
   }

   @Test
   public void testSendingNettyPaging() throws Exception {
      testSending(true, true);
   }

   @Test
   public void testSendingInVM() throws Exception {
      testSending(false, false);
   }

   @Test
   public void testSendingInVMPaging() throws Exception {
      testSending(false, true);
   }

   private void testSending(boolean isNetty, boolean isPaging) throws Exception {
      boolean useDeadConsumer = true;
      this.isNetty = isNetty;
      ActiveMQServer server;

      if (isPaging) {
         server = createServer(true, createDefaultConfig(isNetty), PAGE_SIZE, PAGE_MAX, -1, -1, new HashMap<>());
      } else {
         server = createServer(true, isNetty);
      }

      server.getConfiguration().setMessageExpiryScanPeriod(1000);

      server.start();

      SomeConsumer[] consumers = new SomeConsumer[nThreads];
      SomeProducer[] producers = new SomeProducer[nThreads];

      SomeConsumer[] deadConsumers = null;

      try {

         for (int i = 0; i < nThreads; i++) {
            consumers[i] = new SomeConsumer(i);
         }

         for (int i = 0; i < nThreads; i++) {
            producers[i] = new SomeProducer();
         }

         if (useDeadConsumer) {
            deadConsumers = new SomeConsumer[20];
            for (int i = 0; i < 20; i++) {
               deadConsumers[i] = new SomeConsumer(i + nThreads);
            }
         }

         for (int i = 0; i < nThreads; i++) {
            consumers[i].start();
            producers[i].start();
         }

         for (SomeProducer producer : producers) {
            producer.join();
            assertEquals(0, producer.errors.get());
         }

         for (SomeConsumer consumer : consumers) {
            consumer.join();
            assertEquals(0, consumer.errors.get());
         }

         if (useDeadConsumer) {
            for (SomeConsumer cons : deadConsumers) {
               cons.close();
            }
         }

         waitForNotPaging(server.locateQueue(SimpleString.of("Q1")));

      } finally {
         server.stop();
      }
   }

}
