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
package org.apache.activemq.artemis.tests.integration.cluster.failover;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.NodeManager;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReplicatedPagedFailoverTest extends ReplicatedFailoverTest {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Override
   protected ActiveMQServer createInVMFailoverServer(final boolean realFiles,
                                                     final Configuration configuration,
                                                     final NodeManager nodeManager,
                                                     int id) {
      return createInVMFailoverServer(realFiles, configuration, PAGE_SIZE, PAGE_MAX, new HashMap<>(), nodeManager, id);
   }

   @Test
   public void testFailWithBrowserWithClose() throws Exception {
      internalBrowser(1);
   }

   @Test
   public void testFailWithBrowserWithDelete() throws Exception {
      internalBrowser(2);
   }

   @Override
   @Test
   @Timeout(120)
   public void testReplicatedFailback() throws Exception {
      super.testReplicatedFailback();
   }

   //
   // 0 - no tamper
   // 1 - close files
   // 2 - remove files
   private void internalBrowser(int tamperMode) throws Exception {
      int numMessages = 50;
      int messagesPerPage = 10;
      createSessionFactory();
      ClientSession session = createSession(sf, true, true);

      session.createQueue(QueueConfiguration.of(FailoverTestBase.ADDRESS).setDurable(true));

      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      Queue queue = primaryServer.getServer().locateQueue(FailoverTest.ADDRESS);

      queue.getPageSubscription().getPagingStore().startPaging();
      assertNotNull(queue);

      for (int i = 0; i < numMessages; i++) {
         // some are durable, some are not!
         producer.send(createMessage(session, i, i % 2 == 0));
         if (i > 0 && i % messagesPerPage == 0) {
            queue.getPageSubscription().getPagingStore().forceAnotherPage(true);
         }
      }

      try (ClientConsumer consumer = session.createConsumer(FailoverTestBase.ADDRESS, true)) {
         session.start();

         for (int i = 0; i < numMessages; i++) {
            ClientMessage msg = consumer.receive(500);
            Assertions.assertNotNull(msg);
         }
      }

      PagingStore store = queue.getPageSubscription().getPagingStore();

      // tampering with the system's file
      if (tamperMode == 1) {
         for (long pageID = store.getFirstPage(); pageID <= store.getCurrentPage().getPageId() + 10; pageID++) {
            primaryServer.getServer().getStorageManager().pageClosed(store.getStoreName(), (int) pageID);
         }
      } else if (tamperMode == 2) {
         for (long pageID = store.getFirstPage(); pageID <= store.getCurrentPage().getPageId() + 10; pageID++) {
            primaryServer.getServer().getStorageManager().pageDeleted(store.getStoreName(), (int) pageID);
         }
      }

      try (ClientConsumer consumer = session.createConsumer(FailoverTestBase.ADDRESS, false)) {
         session.start();

         for (int i = 0; i < numMessages; i++) {
            ClientMessage msg = consumer.receive(500);
            if (msg == null) { // the system was tampered, we will receive fewer messages than expected
               break;
            }
            msg.acknowledge();
         }
      }

      Wait.assertFalse(queue.getPageSubscription().getPagingStore()::isPaging);
   }

   @Test
   public void testMultipleCleanup() throws Exception {
      int numMessages = 50;
      int repeats = 3;
      createSessionFactory();
      try (ClientSession session = createSession(sf, false, false)) {

         session.createQueue(QueueConfiguration.of(FailoverTestBase.ADDRESS).setDurable(true));

         ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

         Queue queue = primaryServer.getServer().locateQueue(FailoverTest.ADDRESS);

         for (int runNumber = 0; runNumber < repeats; runNumber++) {

            logger.info("Repeat #{}", runNumber);

            assertTrue(queue.getPageSubscription().getPagingStore().startPaging());
            assertNotNull(queue);

            for (int i = 0; i < numMessages; i++) {
               logger.debug("Send {}", i);
               producer.send(createMessage(session, i, true));
            }
            session.commit();

            try (ClientConsumer consumer = session.createConsumer(FailoverTestBase.ADDRESS, false)) {
               session.start();

               for (int i = 0; i < numMessages; i++) {
                  ClientMessage msg = consumer.receive(500);
                  Assertions.assertNotNull(msg);
                  msg.acknowledge();
                  logger.debug("consumed {}", i);
               }
               assertNull(consumer.receiveImmediate());
            }
            session.commit();

            Wait.assertFalse(queue.getPageSubscription().getPagingStore()::isPaging, 5000, 100);
         }
      }
   }

}
