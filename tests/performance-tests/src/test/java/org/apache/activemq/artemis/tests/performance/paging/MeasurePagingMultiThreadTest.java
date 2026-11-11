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
package org.apache.activemq.artemis.tests.performance.paging;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

public class MeasurePagingMultiThreadTest extends ActiveMQTestBase {

   @Test
   public void testPagingMultipleSenders() throws Throwable {

      final int NUMBER_OF_THREADS = 18;
      final int NUMBER_OF_MESSAGES = 50000;
      final int SIZE_OF_MESSAGE = 1024;

      Map<String, AddressSettings> settings = new HashMap<>();

      ActiveMQServer messagingService = createServer(true, createDefaultInVMConfig(), 10 * 1024, 20 * 1024, settings);
      messagingService.start();
      ServerLocator locator = createInVMNonHALocator();
      try {
         final ClientSessionFactory factory = createSessionFactory(locator);
         final SimpleString adr = SimpleString.of("test-adr");

         createDestination(factory, adr);

         // Send some messages to make sure the destination is in page mode before we measure
         // And that will also help with VM optimizations
         sendInitialBatch(adr, NUMBER_OF_MESSAGES, SIZE_OF_MESSAGE, factory);

         final CountDownLatch latchAlign = new CountDownLatch(NUMBER_OF_THREADS);

         final CountDownLatch latchStart = new CountDownLatch(1);

         class Sender extends Thread {

            private final ClientSession session;

            private final ClientProducer producer;

            private final ClientMessage msg;

            Throwable e;

            Sender() throws Exception {
               session = factory.createSession(false, true, true);
               producer = session.createProducer(adr);
               msg = session.createMessage(true);
               msg.getBodyBuffer().writeBytes(new byte[SIZE_OF_MESSAGE]);
            }

            // run is not going to close sessions or anything, as we don't want to measure that time
            // so this will be done in a second time
            public void cleanUp() throws Exception {
               session.close();
            }

            @Override
            public void run() {
               try {
                  latchAlign.countDown();
                  ActiveMQTestBase.waitForLatch(latchStart);

                  long start = System.currentTimeMillis();
                  sendMessages(NUMBER_OF_MESSAGES, producer, msg);
                  long end = System.currentTimeMillis();

                  System.out.println("Thread " + Thread.currentThread().getName() +
                                        " finished sending in " +
                                        (end - start) +
                                        " milliseconds");
               } catch (Throwable e) {
                  this.e = e;
               }

            }
         }

         Sender[] senders = new Sender[NUMBER_OF_THREADS];

         for (int i = 0; i < NUMBER_OF_THREADS; i++) {
            senders[i] = new Sender();
            senders[i].start();
         }

         ActiveMQTestBase.waitForLatch(latchAlign);

         long timeStart = System.currentTimeMillis();

         latchStart.countDown();

         for (Thread t : senders) {
            t.join();
         }

         long timeEnd = System.currentTimeMillis();

         System.out.println("Total Time: " + (timeEnd - timeStart) +
                               " milliseconds what represented " +
                               NUMBER_OF_MESSAGES *
                                  NUMBER_OF_THREADS *
                                  1000 / (timeEnd - timeStart) +
                               " per second");

         for (Sender s : senders) {
            if (s.e != null) {
               throw s.e;
            }
            s.cleanUp();
         }

      } finally {
         locator.close();
         messagingService.stop();

      }

   }




   private void sendInitialBatch(final SimpleString adr,
                                 final int nMessages,
                                 final int messageSize,
                                 final ClientSessionFactory factory) throws ActiveMQException {
      ClientSession session = factory.createSession(false, true, true);
      ClientProducer producer = session.createProducer(adr);
      ClientMessage msg = session.createMessage(true);

      msg.getBodyBuffer().writeBytes(new byte[messageSize]);

      sendMessages(nMessages, producer, msg);
   }

   private void sendMessages(final int nMessages,
                             final ClientProducer producer,
                             final ClientMessage msg) throws ActiveMQException {
      for (int i = 0; i < nMessages; i++) {
         producer.send(msg);
      }
   }

   private void createDestination(final ClientSessionFactory factory, final SimpleString adr) throws ActiveMQException {
      {
         ClientSession session = factory.createSession(false, false, false);
         session.createQueue(QueueConfiguration.of(adr));
         session.close();
      }
   }


}
