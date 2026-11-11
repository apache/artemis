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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.tests.util.SpawnedTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PageCountSyncOnNonTXTest extends SpawnedTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final String WORD_START = "&*STARTED&*";

   // We will add a random factor on the wait time
   private long timeToRun;

   Process process;

   private AssertionLoggerHandler loggerHandler;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      timeToRun = 5000L + RandomUtil.randomPositiveInt() % 1000;
      loggerHandler = new AssertionLoggerHandler();
   }

   @AfterEach
   public void checkLoggerEnd() throws Exception {
      if (loggerHandler != null) {
         try {
            // These are the message errors for the negative size address size
            assertFalse(loggerHandler.findText("AMQ222214"));
            assertFalse(loggerHandler.findText("AMQ222215"));
         } finally {
            loggerHandler.close();
         }
      }
   }

   @Test
   public void testSendNoTx() throws Exception {
      String QUEUE_NAME = "myQueue";

      final CountDownLatch latch = new CountDownLatch(1);
      Runnable runnable = latch::countDown;

      process = PageCountSyncServer.spawnVMWithLogMacher(WORD_START, runnable, getTestDir(), timeToRun);
      assertTrue(latch.await(30, TimeUnit.SECONDS), "Server didn't start in 30 seconds");

      ServerLocator locator = createNettyNonHALocator();

      try {
         locator = createNettyNonHALocator().setReconnectAttempts(0).setInitialConnectAttempts(10).setRetryInterval(500).setBlockOnDurableSend(false);

         ClientSessionFactory factory = locator.createSessionFactory();
         ClientSession session = factory.createSession(true, true);
         session.createQueue(QueueConfiguration.of(QUEUE_NAME));
         ClientProducer producer = session.createProducer(QUEUE_NAME);
         ClientConsumer consumer = session.createConsumer(QUEUE_NAME);
         session.start();

         ClientSession sessionTransacted = factory.createSession(false, false);
         ClientProducer producerTransacted = sessionTransacted.createProducer(QUEUE_NAME);
         ClientConsumer consumerTransacted = sessionTransacted.createConsumer(QUEUE_NAME);
         sessionTransacted.start();

         long start = System.currentTimeMillis();

         long nmsgs = 0;

         try {
            while (true) {

               int size = RandomUtil.randomPositiveInt() % 1024;

               if (size == 0) {
                  size = 1024;
               }
               ClientMessage msg = session.createMessage(true);
               msg.getBodyBuffer().writeBytes(new byte[size]);

               producer.send(msg);

               if (++nmsgs % 100 == 0) {
                  // complicating the test a bit with transacted sends and consuming
                  producerTransacted.send(msg);

                  for (int i = 0; i < 50; i++) {
                     msg = consumerTransacted.receive(100);
                     if (msg != null) {
                        msg.acknowledge();
                     }
                  }

                  sessionTransacted.commit();

                  msg = consumer.receive(100);
                  if (msg != null) {
                     msg.acknowledge();
                  }
               }

               if (System.currentTimeMillis() - start > timeToRun) {
                  // this will ensure to capture a failure since the server will have crashed
                  session.commit();
               }
            }
         } catch (Exception expected) {
            logger.info("expected exception {}", expected.toString(), expected);
         }

      } finally {
         locator.close();
      }
      assertEquals(1, process.waitFor(), "Process didn't end as expected");

      ActiveMQServer server = SpawnedServerSupport.createServer(getTestDir());

      try {
         server.start();

         Wait.assertTrue(server::isActive);

         locator = createNettyNonHALocator();

         try {
            Queue queue = server.locateQueue(SimpleString.of(QUEUE_NAME));

            assertNotNull(queue);

            long msgs = getMessageCount(queue);

            ClientSessionFactory factory = locator.createSessionFactory();

            ClientSession session = factory.createSession(false, false);

            ClientConsumer consumer = session.createConsumer(QUEUE_NAME, false);

            session.start();

            for (int i = 0; i < msgs; i++) {
               ClientMessage msg = consumer.receive(5000);
               assertNotNull(msg);
               //  msg.acknowledge(); -- we don't ack
               // because in case of a failure we can check data with print-data
            }

            assertNull(consumer.receiveImmediate());

            session.close();

         } finally {
            locator.close();
         }

      } finally {
         server.stop();
      }

   }

}
