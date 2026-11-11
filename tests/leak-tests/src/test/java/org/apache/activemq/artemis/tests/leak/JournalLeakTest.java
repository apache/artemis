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
package org.apache.activemq.artemis.tests.leak;

import static org.apache.activemq.artemis.tests.leak.MemoryAssertions.assertMemory;
import static org.apache.activemq.artemis.tests.leak.MemoryAssertions.basicMemoryAsserts;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.checkleak.core.CheckLeak;
import org.apache.activemq.artemis.core.journal.impl.JournalFileImpl;
import org.apache.activemq.artemis.core.journal.impl.JournalImpl;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager;
import org.apache.activemq.artemis.core.protocol.core.impl.RemotingConnectionImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.core.server.impl.ServerStatus;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * At the time this test was written JournalFileImpl was leaking through JournalFileImpl::negative creating a linked
 * list (or leaked-list, pun intended)
 */
public class JournalLeakTest extends AbstractLeakTest {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   ActiveMQServer server;

   @BeforeAll
   public static void beforeClass() throws Exception {
      assumeTrue(CheckLeak.isLoaded());
   }

   @AfterEach
   public void validateServer() throws Exception {
      CheckLeak checkLeak = new CheckLeak();

      // I am doing this check here because the test method might hold a client connection
      // so this check has to be done after the test, and before the server is stopped
      assertMemory(checkLeak, 0, RemotingConnectionImpl.class.getName());

      server.stop();

      server = null;

      clearServers();
      ServerStatus.clear();

      assertMemory(checkLeak, 0, ActiveMQServerImpl.class.getName());
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      server = createServer(true, createDefaultConfig(1, true));
      server.getConfiguration().setJournalPoolFiles(4).setJournalMinFiles(2);
      server.start();
   }

   @Test
   public void testAMQP() throws Exception {
      doTest("AMQP");
   }

   @Test
   public void testCore() throws Exception {
      doTest("CORE");
   }

   @Test
   public void testOpenWire() throws Exception {
      doTest("OPENWIRE");
   }

   private void doTest(String protocol) throws Exception {
      int MESSAGES = 10000;
      int MESSAGE_SIZE = 104;
      basicMemoryAsserts();

      ExecutorService executorService = Executors.newFixedThreadPool(2);
      runAfter(executorService::shutdownNow);

      ConnectionFactory cf = CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616");

      final AtomicInteger errors = new AtomicInteger(0);
      CountDownLatch done = new CountDownLatch(2);

      executorService.execute(() -> {
         try {
            try (Connection connection = cf.createConnection()) {
               Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
               MessageConsumer consumer = session.createConsumer(session.createQueue(getName()));
               connection.start();

               for (int i = 0; i < MESSAGES; i++) {
                  Message message = consumer.receive(5000);
                  assertNotNull(message);
                  assertEquals(i, message.getIntProperty("i"));
                  if (i > 0 && i % 100 == 0) {
                     session.commit();
                  }
               }
               session.commit();
            }
         } catch (Throwable e) {
            errors.incrementAndGet();
            logger.warn(e.getMessage(), e);
         } finally {
            done.countDown();
         }
      });

      executorService.execute(() -> {
         try {
            try (Connection connection = cf.createConnection()) {
               Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
               MessageProducer producer = session.createProducer(session.createQueue(getName()));
               connection.start();

               for (int i = 0; i < MESSAGES; i++) {
                  BytesMessage message = session.createBytesMessage();
                  message.writeBytes(new byte[MESSAGE_SIZE]);
                  message.setIntProperty("i", i);
                  producer.send(message);
                  if (i > 0 && i % 100 == 0) {
                     session.commit();
                  }
               }
               session.commit();
            }
         } catch (Throwable e) {
            errors.incrementAndGet();
            logger.warn(e.getMessage(), e);
         } finally {
            done.countDown();
         }
      });

      assertTrue(done.await(1, TimeUnit.MINUTES));
      assertEquals(0, errors.get());

      basicMemoryAsserts();

      JournalStorageManager journalStorageManager = (JournalStorageManager) server.getStorageManager();
      JournalImpl journalImpl = (JournalImpl) journalStorageManager.getMessageJournal();
      int totalFiles = journalImpl.getFilesRepository().getDataFiles().size() + journalImpl.getFilesRepository().getFreeFilesCount() + journalImpl.getOpenedFilesCount();

      // on this particular leak we would have 100 files. I am allowing a little cushion as they will be from currentFile and some other async opens
      assertMemory(new CheckLeak(), totalFiles + 5, JournalFileImpl.class.getName());
   }
}