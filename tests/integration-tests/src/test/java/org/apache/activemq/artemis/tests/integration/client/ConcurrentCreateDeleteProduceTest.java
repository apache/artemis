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

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The delete queue was resetting some fields on the Queue what would eventually turn a NPE. this test would eventually
 * fail without the fix but it was a rare event as in most of the time the NPE happened during depaging what let the
 * server to recover itself on the next depage. To verify a fix on this test against the previous version of QueueImpl
 * look for NPEs on System.err
 */
public class ConcurrentCreateDeleteProduceTest extends ActiveMQTestBase {

   volatile boolean running = true;

   private final SimpleString ADDRESS = SimpleString.of("ADQUEUE");

   AtomicInteger sequence = new AtomicInteger(0);
   private ActiveMQServer server;
   private ServerLocator locator;

   private static final int PAGE_MAX = 100 * 1024;

   private static final int PAGE_SIZE = 10 * 1024;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false).setJournalSyncTransactional(false);

      server = createServer(true, config, PAGE_SIZE, PAGE_MAX, new HashMap<>());
      server.start();
      locator = createNonHALocator(false).setBlockOnDurableSend(false).setBlockOnAcknowledge(true);
   }

   @Test
   public void testConcurrentProduceCreateAndDelete() throws Throwable {
      locator.setBlockOnDurableSend(false).setBlockOnNonDurableSend(false);
      ClientSessionFactory factory = locator.createSessionFactory();
      ClientSession session = factory.createSession(true, true);
      ClientProducer producer = session.createProducer(ADDRESS);

      // just to make it page forever
      Queue serverQueue = server.createQueue(QueueConfiguration.of("everPage").setAddress(ADDRESS).setRoutingType(RoutingType.ANYCAST));
      serverQueue.getPageSubscription().getPagingStore().startPaging();

      Consumer[] consumers = new Consumer[10];

      for (int i = 0; i < consumers.length; i++) {
         consumers[i] = new Consumer();
         consumers[i].start();
      }

      for (int i = 0; i < 1500 && running; i++) {
         producer.send(session.createMessage(true));
         //Thread.sleep(10);
      }

      session.close();

      running = false;

      for (Consumer consumer : consumers) {
         consumer.join();
         if (consumer.ex != null) {
            throw consumer.ex;
         }
      }

   }

   class Consumer extends Thread {

      volatile Throwable ex;

      @Override
      public void run() {
         ClientSessionFactory factory;
         ClientSession session;
         try {
            factory = locator.createSessionFactory();
            session = factory.createSession(false, false);
            session.start();

            int msgcount = 0;

            for (int i = 0; i < 100 && running; i++) {
               SimpleString queueName = ADDRESS.concat("_" + sequence.incrementAndGet());
               session.createQueue(QueueConfiguration.of(queueName).setAddress(ADDRESS));
               ClientConsumer consumer = session.createConsumer(queueName);
               while (running) {
                  ClientMessage msg = consumer.receive(500);
                  if (msg == null) {
                     if (running) {
                        continue;
                     } else {
                        break;
                     }
                  }
                  if (msgcount++ == 500) {
                     msgcount = 0;
                     break;
                  }
               }
               consumer.close();
               session.commit();
               session.deleteQueue(queueName);
            }
            session.close();
         } catch (Throwable e) {
            this.ex = e;
            e.printStackTrace();
            running = false;
         }
      }

   }

}
