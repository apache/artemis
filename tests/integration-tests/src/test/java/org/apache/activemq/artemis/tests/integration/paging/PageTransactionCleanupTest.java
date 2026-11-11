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
package org.apache.activemq.artemis.tests.integration.paging;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.paging.impl.PagingStoreImpl;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A PagingOrderTest.
 * <p>
 * PagingTest has a lot of tests already. I decided to create a newer one more specialized on Ordering and counters
 */
public class PageTransactionCleanupTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());


   @Test
   public void testPageTXCleanup() throws Throwable {
      final int PAGE_MAX = 100 * 1024;
      final int PAGE_SIZE = 10 * 1024;

      Configuration config = createDefaultConfig(true).setJournalSyncNonTransactional(false);

      ActiveMQServer server = createServer(true, config, PAGE_SIZE, PAGE_MAX, new HashMap<>());

      server.start();

      Queue queue1 = server.createQueue(QueueConfiguration.of("test1").setRoutingType(RoutingType.ANYCAST));
      Queue queue2 = server.createQueue(QueueConfiguration.of("test2").setRoutingType(RoutingType.ANYCAST));

      queue1.getPagingStore().startPaging();
      queue2.getPagingStore().startPaging();

      ConnectionFactory factory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616");

      final int NUMBER_OF_MESSAGES = 30;

      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(true, Session.AUTO_ACKNOWLEDGE);
         for (int producerID = 1; producerID <= 2; producerID++) {
            MessageProducer producer = session.createProducer(session.createQueue("test" + producerID));
            for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
               producer.send(session.createTextMessage("hello " + i));
               session.commit();
            }
         }
      }

      PagingStoreImpl store = (PagingStoreImpl) queue1.getPagingStore();
      File folder = store.getFolder();

      server.stop();

      for (String fileName : folder.list((dir, f) -> f.endsWith(".page"))) {
         File fileToRemove = new File(folder, fileName);
         fileToRemove.delete();
         logger.debug("removing file {}", fileToRemove);
      }


      Map<Integer, AtomicInteger> journalCount = countJournal(server.getConfiguration());
      assertEquals(NUMBER_OF_MESSAGES * 2, journalCount.get((int)JournalRecordIds.PAGE_TRANSACTION).get());

      try (AssertionLoggerHandler handler = new AssertionLoggerHandler()) {
         server.start();
         Wait.assertTrue(() -> handler.findText("AMQ224132"));
      }

      // compacting to clean up older records before counting them
      server.getStorageManager().getMessageJournal().scheduleCompactAndBlock(60_000);

      journalCount = countJournal(server.getConfiguration());
      assertEquals(NUMBER_OF_MESSAGES, journalCount.get((int)JournalRecordIds.PAGE_TRANSACTION).get());

      server.stop();
      server.start();

      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(true, Session.AUTO_ACKNOWLEDGE);
         connection.start();
         for (int producerID = 1; producerID <= 2; producerID++) {
            MessageConsumer consumer = session.createConsumer(session.createQueue("test" + producerID));
            for (int i = 0; i < (producerID == 1 ? 0 : NUMBER_OF_MESSAGES); i++) {
               TextMessage message = (TextMessage) consumer.receive(5000);
               assertNotNull(message, "message not received on producer + " + producerID + ", message " + i);
               assertEquals("hello " + i, message.getText(), "could not find message " + i + " on producerID=" + producerID);
               session.commit();
            }
            assertNull(consumer.receiveNoWait());
            consumer.close();
         }
      }
   }

}
