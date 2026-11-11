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
package org.apache.activemq.artemis.tests.integration.bridge;

import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.SendAcknowledgementHandler;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.paging.impl.PageTimedWriter;
import org.apache.activemq.artemis.core.paging.impl.PagingStoreImpl;
import org.apache.activemq.artemis.core.paging.impl.PagingStoreImplAccessor;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * this test will simulate what a bridge sender would do with auto completes
 */
public class BridgeSimulationTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private ActiveMQServer server;

   private final SimpleString address = SimpleString.of("address");

   private final SimpleString queueName = SimpleString.of("queue");

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      server = createServer(true);
      server.start();
   }

   @Test
   public void testSendAcknowledgements() throws Exception {
      ServerLocator locator = createInVMNonHALocator();

      locator.setConfirmationWindowSize(100 * 1024);

      try (ClientSessionFactory csf = createSessionFactory(locator); ClientSession session = csf.createSession(null, null, false, true, true, false, 1)) {

         Queue queue = server.createQueue(QueueConfiguration.of(queueName).setAddress(address).setRoutingType(RoutingType.ANYCAST).setDurable(true));

         ClientProducer prod = session.createProducer(address);

         PagingStoreImpl pagingStore = (PagingStoreImpl) queue.getPagingStore();

         CountDownLatch allowRunning = new CountDownLatch(1);
         CountDownLatch enteredSync = new CountDownLatch(1);
         CountDownLatch allowSync = new CountDownLatch(1);
         AtomicInteger doneSync = new AtomicInteger(0);

         runAfter(allowRunning::countDown);
         runAfter(allowSync::countDown);

         PageTimedWriter newWriter = new PageTimedWriter(100_000, server.getStorageManager(), pagingStore, server.getScheduledPool(), server.getExecutorFactory().getExecutor(), true, 100) {
            @Override
            public void run() {
               logger.info("newWriter waiting to run");
               try {
                  allowRunning.await();
               } catch (InterruptedException e) {
                  logger.warn(e.getMessage(), e);
                  Thread.currentThread().interrupt();

               }
               logger.info("newWriter running");
               super.run();
               logger.info("newWriter ran");
            }

            @Override
            protected void performSync() throws Exception {
               logger.info("newWriter waiting to perform sync");
               enteredSync.countDown();
               super.performSync();
               try {
                  allowSync.await();
               } catch (InterruptedException e) {
                  logger.warn(e.getMessage(), e);
               }
               doneSync.incrementAndGet();
               logger.info("newWriter done with sync");
            }
         };

         runAfter(newWriter::stop);

         PageTimedWriter olderWriter = pagingStore.getPageTimedWriter();
         olderWriter.stop();

         newWriter.start();

         PagingStoreImplAccessor.replacePageTimedWriter(pagingStore, newWriter);

         CountDownLatch ackDone = new CountDownLatch(1);

         pagingStore.startPaging();

         SendAcknowledgementHandler handler = new SendAcknowledgementHandler() {
            @Override
            public void sendAcknowledged(Message message) {
               logger.info("ACK Confirmation from {}", message);
               ackDone.countDown();
            }
         };

         session.setSendAcknowledgementHandler(handler);

         ClientMessage message = session.createMessage(true);
         message.putExtraBytesProperty(Message.HDR_ROUTE_TO_IDS, ByteBuffer.allocate(8).putLong(queue.getID()).array());
         message.putExtraBytesProperty(Message.HDR_BRIDGE_DUPLICATE_ID, RandomUtil.randomBytes(10));
         prod.send(message);

         logger.info("Sent..");

         assertEquals(0, queue.getMessageCount());
         assertFalse(ackDone.await(20, TimeUnit.MILLISECONDS));
         allowRunning.countDown();
         assertFalse(ackDone.await(20, TimeUnit.MILLISECONDS));
         assertEquals(0, queue.getMessageCount());
         assertTrue(enteredSync.await(10, TimeUnit.SECONDS));
         assertFalse(ackDone.await(20, TimeUnit.MILLISECONDS));
         assertEquals(0, queue.getMessageCount());
         verifyReceive(locator, false);
         allowSync.countDown();
         assertTrue(ackDone.await(1000, TimeUnit.MILLISECONDS));
         Wait.assertEquals(1L, queue::getMessageCount, 5000, 100);
         verifyReceive(locator, true);
      }

   }

   private void verifyReceive(ServerLocator locator, boolean receive) throws Exception {
      try (ClientSessionFactory factory = locator.createSessionFactory(); ClientSession session = factory.createSession(false, true)) {
         ClientConsumer consumer = session.createConsumer(queueName);
         session.start();
         if (receive) {
            if (consumer.receive(5_000) == null) {
               logger.warn("No message received");
               fail("no message received");
            }
         } else {
            if (consumer.receiveImmediate() != null) {
               logger.warn("message was received");
               fail("message was received");
            }
         }
      }

   }
}
