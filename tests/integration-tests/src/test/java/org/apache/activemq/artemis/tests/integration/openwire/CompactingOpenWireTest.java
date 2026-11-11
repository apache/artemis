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
package org.apache.activemq.artemis.tests.integration.openwire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class CompactingOpenWireTest extends BasicOpenWireTest {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      realStore = true;
      super.setUp();
      System.setProperty("org.apache.activemq.transport.AbstractInactivityMonitor.keepAliveTime", "2");
      createFactories();

      for (int i = 0; i < 30; i++) {
         SimpleString coreQueue = SimpleString.of(queueName + i);
         this.server.createQueue(QueueConfiguration.of(coreQueue).setRoutingType(RoutingType.ANYCAST));
         testQueues.put(queueName, coreQueue);
      }
   }

   @Override
   protected String getConnectionUrl() {
      return "failover:" + urlString;
   }

   @Override
   protected void extraServerConfig(Configuration serverConfig) {
      super.extraServerConfig(serverConfig);
      serverConfig.setIDCacheSize(500);
      serverConfig.setPersistIDCache(true);
      serverConfig.setJournalSyncTransactional(false);
      serverConfig.setJournalSyncNonTransactional(false);
      serverConfig.setJournalFileSize(10 * 1024);
      serverConfig.setJournalCompactMinFiles(1);
      serverConfig.setJournalCompactPercentage(0);
      serverConfig.setJournalType(JournalType.MAPPED);
      serverConfig.setJournalBufferTimeout_NIO(0);
   }

   @Test
   public void testTransactCompact() throws Exception {
      final int THREADS = 30;
      AtomicInteger errors = new AtomicInteger(0);
      AtomicBoolean running = new AtomicBoolean(true);
      ExecutorService executorService = Executors.newFixedThreadPool(THREADS + 1);
      CountDownLatch compactDone = new CountDownLatch(1);
      executorService.execute(() -> {
         while (running.get()) {
            try {
               server.getStorageManager().getMessageJournal().scheduleCompactAndBlock(10_000);
            } catch (Exception e) {
               e.printStackTrace();
               errors.incrementAndGet();
            }
         }
         compactDone.countDown();
      });
      CountDownLatch latchDone = new CountDownLatch(THREADS);
      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {

         String space1k = new String(new char[5]).replace('\0', ' ');
         for (int i = 0; i < THREADS; i++) {
            final int id = i % 10;
            executorService.submit(() -> {
               try (Connection connection = factory.createConnection()) {

                  Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
                  Session consumerSession = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

                  Queue queue = session.createQueue(queueName + id);
                  MessageProducer producer = session.createProducer(queue);
                  MessageConsumer consumer = consumerSession.createConsumer(queue);
                  connection.start();

                  for (int j = 0; j < 1000 && running.get(); j++) {
                     TextMessage textMessage = session.createTextMessage("test");
                     textMessage.setStringProperty("1k", space1k);
                     producer.send(textMessage);
                     if (j % 2 == 0) {
                        session.commit();
                        TextMessage message = (TextMessage) consumer.receive(5000);
                        assertNotNull(message);

                        assertEquals("test", message.getText());

                        message.acknowledge();
                     } else {
                        session.rollback();
                     }

                  }
                  logger.debug("Done! ");

               } catch (Throwable t) {
                  errors.incrementAndGet();
                  t.printStackTrace();
               } finally {
                  latchDone.countDown();
               }
            });
         }
         latchDone.await(10, TimeUnit.MINUTES);
         running.set(false);
         compactDone.await(10, TimeUnit.MINUTES);
         executorService.shutdownNow();
         assertEquals(0, errors.get());
         assertFalse(loggerHandler.findText("AMQ144003")); // error compacting
         assertFalse(loggerHandler.findText("AMQ222055")); // records not found
         assertFalse(loggerHandler.findText("AMQ222302")); // string conversion issue
      } finally {
         running.set(false);
         executorService.shutdownNow();
      }

      connection.close();

      server.stop();

      server.getConfiguration().setPersistIDCache(false);
      server.getConfiguration().setJournalPoolFiles(2);

      server.start();
      server.waitForActivation(1, TimeUnit.SECONDS);
      server.getStorageManager().getMessageJournal().scheduleCompactAndBlock(60_000);
      server.stop();

      Map<Integer, AtomicInteger> counts = countJournal(server.getConfiguration());
      counts.forEach((a, b) -> System.out.println(a + " = " + b));
      AtomicInteger duplicateIDCounts = counts.get((int)JournalRecordIds.DUPLICATE_ID);
      assertTrue(duplicateIDCounts == null || duplicateIDCounts.get() == 0, "There are duplicate IDs on the journal even though the system was reconfigured to not persist them::" + duplicateIDCounts);

   }
}
