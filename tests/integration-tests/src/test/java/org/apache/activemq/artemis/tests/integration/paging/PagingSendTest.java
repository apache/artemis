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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.ActiveMQAddressFullException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.paging.cursor.PageCursorProvider;
import org.apache.activemq.artemis.core.paging.cursor.impl.PageCursorProviderTestAccessor;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.settings.impl.PageFullMessagePolicy;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.collections.LinkedListIterator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PagingSendTest extends ActiveMQTestBase {

   public static final SimpleString ADDRESS = SimpleString.of("SimpleAddress");

   private ServerLocator locator;

   private ActiveMQServer server;

   protected boolean isNetty() {
      return false;
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      server = newActiveMQServer();

      server.start();
      waitForServerToStart(server);
      locator = createFactory(isNetty());
   }

   private ActiveMQServer newActiveMQServer() throws Exception {
      ActiveMQServer server = createServer(true, isNetty());

      AddressSettings defaultSetting = new AddressSettings().setPageSizeBytes(10 * 1024).setMaxSizeBytes(20 * 1024);

      server.getAddressSettingsRepository().addMatch("#", defaultSetting);

      return server;
   }

   @Test
   public void testSameMessageOverAndOverBlocking() throws Exception {
      dotestSameMessageOverAndOver(true);
   }

   @Test
   public void testSameMessageOverAndOverNonBlocking() throws Exception {
      dotestSameMessageOverAndOver(false);
   }

   public void dotestSameMessageOverAndOver(final boolean blocking) throws Exception {
      // Making it synchronous, just because we want to stop sending messages as soon as the
      // page-store becomes in
      // page mode
      // and we could only guarantee that by setting it to synchronous
      locator.setBlockOnNonDurableSend(blocking).setBlockOnDurableSend(blocking).setBlockOnAcknowledge(blocking);

      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession session = sf.createSession(null, null, false, true, true, false, 0);

      session.createQueue(QueueConfiguration.of(PagingSendTest.ADDRESS));

      ClientProducer producer = session.createProducer(PagingSendTest.ADDRESS);

      ClientMessage message = null;

      message = session.createMessage(true);
      message.getBodyBuffer().writeBytes(new byte[1024]);

      for (int i = 0; i < 200; i++) {
         producer.send(message);
      }

      session.close();

      session = sf.createSession(null, null, false, true, true, false, 0);

      ClientConsumer consumer = session.createConsumer(PagingSendTest.ADDRESS);

      session.start();

      for (int i = 0; i < 200; i++) {
         ClientMessage message2 = consumer.receive(10000);

         assertNotNull(message2);

         if (i == 100) {
            session.commit();
         }

         message2.acknowledge();
      }

      consumer.close();

      session.close();
   }

   @Test
   public void testOrderOverTX() throws Exception {
      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession sessionConsumer = sf.createSession(true, true, 0);

      sessionConsumer.createQueue(QueueConfiguration.of(PagingSendTest.ADDRESS));

      final ClientSession sessionProducer = sf.createSession(false, false);
      final ClientProducer producer = sessionProducer.createProducer(PagingSendTest.ADDRESS);

      final AtomicInteger errors = new AtomicInteger(0);

      final int TOTAL_MESSAGES = 1000;

      // Consumer will be ready after we have commits
      final CountDownLatch ready = new CountDownLatch(1);

      Thread tProducer = new Thread(() -> {
         try {
            int commit = 0;
            for (int i = 0; i < TOTAL_MESSAGES; i++) {
               ClientMessage msg = sessionProducer.createMessage(true);
               msg.getBodyBuffer().writeBytes(new byte[1024]);
               msg.putIntProperty("count", i);
               producer.send(msg);

               if (i % 100 == 0 && i > 0) {
                  sessionProducer.commit();
                  if (commit++ > 2) {
                     ready.countDown();
                  }
               }
            }

            sessionProducer.commit();

         } catch (Exception e) {
            e.printStackTrace();
            errors.incrementAndGet();
         }
      });

      ClientConsumer consumer = sessionConsumer.createConsumer(PagingSendTest.ADDRESS);

      sessionConsumer.start();

      tProducer.start();

      assertTrue(ready.await(10, TimeUnit.SECONDS));

      for (int i = 0; i < TOTAL_MESSAGES; i++) {
         ClientMessage msg = consumer.receive(10000);

         assertNotNull(msg);

         assertEquals(i, msg.getIntProperty("count").intValue());

         msg.acknowledge();
      }

      tProducer.join();

      sessionConsumer.close();

      sessionProducer.close();

      assertEquals(0, errors.get());
   }

   @Test
   public void testPagingDoesNotDuplicateBatchMessages() throws Exception {
      int batchSize = 20;

      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession session = sf.createSession(false, false);

      // Create a queue
      SimpleString queueAddr = SimpleString.of("testQueue");
      session.createQueue(QueueConfiguration.of(queueAddr));

      // Set up paging on the queue address
      AddressSettings addressSettings = new AddressSettings().setPageSizeBytes(10 * 1024);
      /*
       * This actually causes the address to start paging messages after 10 x messages with 1024 payload is sent.
       * Presumably due to additional meta-data, message headers etc...
       */
      addressSettings.setMaxSizeBytes(16 * 1024);
      server.getAddressSettingsRepository().addMatch("#", addressSettings);

      sendMessageBatch(batchSize, session, queueAddr);

      Queue queue = server.locateQueue(queueAddr);

      // Give time Queue.deliverAsync to deliver messages
      assertTrue(waitForMessages(queue, batchSize, 3000), "Messages were not propagated to internal structures.");

      AtomicInteger errors = new AtomicInteger(0);
      CountDownLatch done = new CountDownLatch(1);

      queue.getPagingStore().getExecutor().execute(() -> {
         try {
            checkBatchMessagesAreNotPagedTwice(queue);
            for (int i = 0; i < 10; i++) {
               // execute the same count a couple times. This is to make sure the iterators have no impact regardless
               // the number of times they are called
               assertEquals(batchSize, processCountThroughIterator(queue));
            }

         } catch (Throwable e) {
            e.printStackTrace();
            errors.incrementAndGet();
         }
         done.countDown();
      });
      assertEquals(0, errors.get());

   }

   @Test
   public void testPagingDoesNotDuplicateBatchMessagesAfterPagingStarted() throws Exception {
      int batchSize = 20;

      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession session = sf.createSession(false, false);

      // Create a queue
      SimpleString queueAddr = SimpleString.of("testQueue");
      session.createQueue(QueueConfiguration.of(queueAddr));

      // Set up paging on the queue address
      AddressSettings addressSettings = new AddressSettings().setPageSizeBytes(10 * 1024);
      /*
       * This actually causes the address to start paging messages after 10 x messages with 1024 payload is sent.
       * Presumably due to additional meta-data, message headers etc...
       */
      addressSettings.setMaxSizeBytes(16 * 1024);
      server.getAddressSettingsRepository().addMatch("#", addressSettings);

      int numberOfMessages = 0;
      // ensure the server is paging
      while (!server.getPagingManager().getPageStore(queueAddr).isPaging()) {
         sendMessageBatch(batchSize, session, queueAddr);
         numberOfMessages += batchSize;

      }

      sendMessageBatch(batchSize, session, queueAddr);
      numberOfMessages += batchSize;

      Queue queue = server.locateQueue(queueAddr);
      checkBatchMessagesAreNotPagedTwice(queue);

      for (int i = 0; i < 10; i++) {
         // execute the same count a couple times. This is to make sure the iterators have no impact regardless
         // the number of times they are called
         assertEquals(numberOfMessages, processCountThroughIterator(queue));
      }
   }

   @Test
   public void testPageLimitBytesValidation() throws Exception {
      final String addressName = getTestMethodName();

      try (ClientSessionFactory sf = createSessionFactory(locator)) {
         ClientSession session = sf.createSession(false, false);

         SimpleString queueAddr = SimpleString.of(addressName);
         session.createQueue(QueueConfiguration.of(queueAddr));

         int size = 1048576;
         AddressSettings addressSettings = new AddressSettings();
         addressSettings.setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE);
         addressSettings.setPageFullMessagePolicy(PageFullMessagePolicy.FAIL);
         addressSettings.setPageSizeBytes(size);
         addressSettings.setPageLimitBytes((long) size);
         addressSettings.setMaxSizeBytes(size);

         server.getAddressSettingsRepository().addMatch(addressName, addressSettings);

         int totalMessages = 15;
         int messageSize = 90000;
         sendMessageBatch(totalMessages, messageSize, session, queueAddr);

         Queue queue = server.locateQueue(queueAddr);

         // Give time Queue.deliverAsync to deliver messages
         assertTrue(waitForMessages(queue, totalMessages, 10000));

         PagingStore queuePagingStore = queue.getPagingStore();
         assertTrue(queuePagingStore != null && queuePagingStore.isPaging());

         assertFalse(queuePagingStore.isPageFull());

         // set pageLimitBytes to be smaller than pageSizeBytes
         addressSettings.setPageLimitBytes((long) (size - 1));
         server.getAddressSettingsRepository().addMatch(addressName, addressSettings);

         // check the settings applied
         assertEquals(size - 1, queuePagingStore.getPageLimitBytes());

         // check pageFull is true
         assertTrue(queuePagingStore.isPageFull());

         // send a messages should be immediately blocked (in our case FAIL)
         try {
            sendMessageBatch(1, messageSize, session, queueAddr);
            fail("should be immediate blocked on paging");
         } catch (ActiveMQAddressFullException ex) {
            //ok
         }

         assertTrue(queuePagingStore.isPageFull());

         // set pageLimitBytes to bigger value to unblock paging again
         addressSettings.setPageLimitBytes((long) (size * 2));
         server.getAddressSettingsRepository().addMatch(addressName, addressSettings);

         // now page is enabled again
         assertFalse(queuePagingStore.isPageFull());

         sendMessageBatch(1, messageSize, session, queueAddr);
         assertTrue(waitForMessages(queue, totalMessages + 1, 10000));
      }
   }

   @Test
   public void testPageLimitBytesAndPageLimitMessagesValidationBlockOnLimitMessagesFirst() throws Exception {

      final String queueName = getTestMethodName();

      try (ClientSessionFactory sf = createSessionFactory(locator)) {
         ClientSession session = sf.createSession(true, true);

         SimpleString queueAddr = SimpleString.of(queueName);
         session.createQueue(QueueConfiguration.of(queueAddr));

         final int size = 1024 * 50;
         final Long maxMessages = 10L;
         final int initPageSizeBytes = size;
         final Long initPageLimitBytes = (long) (initPageSizeBytes * 10);

         AddressSettings addressSettings = new AddressSettings();
         addressSettings.setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE);
         addressSettings.setPageFullMessagePolicy(PageFullMessagePolicy.FAIL);
         addressSettings.setPageSizeBytes(initPageSizeBytes);
         addressSettings.setPageLimitBytes(initPageLimitBytes);
         addressSettings.setMaxSizeBytes(size);
         addressSettings.setPageLimitMessages(maxMessages);

         server.getAddressSettingsRepository().addMatch(queueName, addressSettings);

         int totalMessages = 0;
         int messageSize = 1024;
         ClientProducer producer = session.createProducer(queueAddr);
         boolean stop = false;
         while (!stop) {
            try {
               ClientMessage message = createMessage(session, messageSize, totalMessages, null);
               producer.send(message);
               totalMessages++;
               session.commit();
            } catch (ActiveMQAddressFullException ex) {
               stop = true;
            }
         }

         Queue queue = server.locateQueue(queueAddr);
         assertTrue(waitForMessages(queue, totalMessages, 10000));

         PagingStore queuePagingStore = queue.getPagingStore();
         assertTrue(queuePagingStore != null && queuePagingStore.isPageFull());

         // the messages reach the limit
         PageCursorProvider cursorProvider = queuePagingStore.getCursorProvider();
         assertEquals(maxMessages, PageCursorProviderTestAccessor.getNumberOfMessagesOnSubscriptions(cursorProvider));

         // but pages still under limit
         assertEquals(initPageLimitBytes, queuePagingStore.getPageLimitBytes());
         assertEquals(initPageSizeBytes, queuePagingStore.getPageSizeBytes());
         long existingPages = queuePagingStore.getNumberOfPages();
         assertTrue(existingPages <= initPageLimitBytes / initPageSizeBytes);

         server.stop(true);
         waitForServerToStop(server);

         // restart the server the pageFull is still true
         try {
            server.start();
            waitForServerToStart(server);

            queue = server.locateQueue(queueAddr);

            queuePagingStore = queue.getPagingStore();
            assertTrue(queuePagingStore != null && queuePagingStore.isPageFull());

            // but current pages still under limit
            long currentPages = queuePagingStore.getNumberOfPages();
            assertEquals(initPageLimitBytes, queuePagingStore.getPageLimitBytes());
            assertEquals(initPageSizeBytes, queuePagingStore.getPageSizeBytes());
            long maxPages = initPageLimitBytes / initPageSizeBytes;
            assertTrue(currentPages <= maxPages);

            // now increase the max messages to unblock producer
            final Long bigLimitMessages = 1000L;
            addressSettings.setPageLimitMessages(bigLimitMessages);
            server.getAddressSettingsRepository().addMatch(queueName, addressSettings);

            // no longer page full
            assertFalse(queuePagingStore.isPageFull());

            // now send more messages until pagefull
            locator = createFactory(isNetty());
            try (ClientSessionFactory csf = createSessionFactory(locator)) {
               stop = false;
               session = csf.createSession(true, true);
               producer = session.createProducer(queueAddr);
               while (!stop) {
                  try {
                     ClientMessage message = createMessage(session, messageSize, totalMessages, null);
                     producer.send(message);
                     totalMessages++;
                     assertTrue(totalMessages <= bigLimitMessages, "test is broken");
                  } catch (ActiveMQAddressFullException ex) {
                     stop = true;
                  }
               }
            }

            // check the page full
            assertTrue(queuePagingStore.isPageFull());
            // because it reaches pageLimitBytes
            currentPages = queuePagingStore.getNumberOfPages();
            assertEquals(initPageLimitBytes, queuePagingStore.getPageLimitBytes());
            assertEquals(initPageSizeBytes, queuePagingStore.getPageSizeBytes());
            maxPages = initPageLimitBytes / initPageSizeBytes;
            assertTrue(currentPages > maxPages);

            // and messages still below limit messages
            cursorProvider = queuePagingStore.getCursorProvider();
            assertEquals(bigLimitMessages, queuePagingStore.getPageLimitMessages());
            assertTrue(PageCursorProviderTestAccessor.getNumberOfMessagesOnSubscriptions(cursorProvider) < bigLimitMessages);
         } finally {
            server.stop(true);
         }
      }
   }

   @Test
   public void testPageLimitBytesAndPageLimitMessagesValidationBlockOnLimitBytesFirst() throws Exception {

      final String queueName = getTestMethodName();

      try (ClientSessionFactory sf = createSessionFactory(locator)) {
         ClientSession session = sf.createSession(true, true);

         SimpleString queueAddr = SimpleString.of(queueName);
         session.createQueue(QueueConfiguration.of(queueAddr));

         // the numbers should make sure page files reach the pageLimitBytes before pageLimitMessages
         final int size = 1024 * 50;
         final Long maxMessages = 200L;
         final int initPageSizeBytes = size;
         final Long initPageLimitBytes = (long) (initPageSizeBytes * 5);

         AddressSettings addressSettings = new AddressSettings();
         addressSettings.setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE);
         addressSettings.setPageFullMessagePolicy(PageFullMessagePolicy.FAIL);
         addressSettings.setPageSizeBytes(initPageSizeBytes);
         addressSettings.setPageLimitBytes(initPageLimitBytes);
         addressSettings.setMaxSizeBytes(size);
         addressSettings.setPageLimitMessages(maxMessages);

         server.getAddressSettingsRepository().addMatch(queueName, addressSettings);

         int totalMessages = 0;
         int messageSize = 1024;
         ClientProducer producer = session.createProducer(queueAddr);
         boolean stop = false;
         while (!stop) {
            try {
               ClientMessage message = createMessage(session, messageSize, totalMessages, null);
               producer.send(message);
               totalMessages++;
               session.commit();
            } catch (ActiveMQAddressFullException ex) {
               stop = true;
            }
         }

         Queue queue = server.locateQueue(queueAddr);
         assertTrue(waitForMessages(queue, totalMessages, 10000));

         PagingStore queuePagingStore = queue.getPagingStore();
         assertTrue(queuePagingStore != null && queuePagingStore.isPageFull());

         // the pages reach the limit
         long existingPages = queuePagingStore.getNumberOfPages();
         assertEquals(initPageLimitBytes, queuePagingStore.getPageLimitBytes());
         assertEquals(initPageSizeBytes, queuePagingStore.getPageSizeBytes());
         long maxPages = initPageLimitBytes / initPageSizeBytes;
         assertTrue(existingPages > maxPages);

         // but messages still under limit
         PageCursorProvider cursorProvider = queuePagingStore.getCursorProvider();
         Long existingMessages = PageCursorProviderTestAccessor.getNumberOfMessagesOnSubscriptions(cursorProvider);
         assertTrue(existingMessages < maxMessages, "existing " + existingMessages + " should be less than max " + maxMessages);

         server.stop(true);
         waitForServerToStop(server);

         // restart the server the pageFull is still true
         try {
            server.start();
            waitForServerToStart(server);

            queue = server.locateQueue(queueAddr);

            queuePagingStore = queue.getPagingStore();
            assertNotNull(queuePagingStore);
            assertTrue(queuePagingStore.isPageFull());

            // but messages still under limit
            cursorProvider = queuePagingStore.getCursorProvider();
            existingMessages = PageCursorProviderTestAccessor.getNumberOfMessagesOnSubscriptions(cursorProvider);
            assertTrue(existingMessages < maxMessages, "existing " + existingMessages + " should be still less than max " + maxMessages);

            // now increase the pageLimitBytes to unblock producer
            final Long newPageLimitBytes = (long) (size * 20);
            addressSettings.setPageLimitBytes(newPageLimitBytes);
            server.getAddressSettingsRepository().addMatch(queueName, addressSettings);

            // no longer page full
            assertFalse(queuePagingStore.isPageFull());

            // now send more messages until pagefull
            locator = createFactory(isNetty());
            try (ClientSessionFactory csf = createSessionFactory(locator)) {
               stop = false;
               session = csf.createSession(true, true);
               producer = session.createProducer(queueAddr);
               while (!stop) {
                  try {
                     ClientMessage message = createMessage(session, messageSize, totalMessages, null);
                     producer.send(message);
                     totalMessages++;
                  } catch (ActiveMQAddressFullException ex) {
                     stop = true;
                  }
               }
            }

            // check the page full
            assertTrue(queuePagingStore.isPageFull());

            // current pages not exceeds the max pages
            Long currentPages = queuePagingStore.getNumberOfPages();
            assertEquals(newPageLimitBytes, queuePagingStore.getPageLimitBytes());
            assertEquals(initPageSizeBytes, queuePagingStore.getPageSizeBytes());
            maxPages = newPageLimitBytes / initPageSizeBytes;
            assertTrue(currentPages <= maxPages);

            // however messages reaches limit messages
            cursorProvider = queuePagingStore.getCursorProvider();
            assertEquals(maxMessages, queuePagingStore.getPageLimitMessages());
            assertEquals(maxMessages, PageCursorProviderTestAccessor.getNumberOfMessagesOnSubscriptions(cursorProvider));
         } finally {
            server.stop(true);
         }
      }
   }

   @Test
   public void testPageLimitBytesValidationOnRestart() throws Exception {

      final String queueName = getTestMethodName();

      try (ClientSessionFactory sf = createSessionFactory(locator)) {
         ClientSession session = sf.createSession(false, false);

         SimpleString queueAddr = SimpleString.of(queueName);
         session.createQueue(QueueConfiguration.of(queueAddr));

         final int size = 1024 * 50;
         final int initPageSizeBytes = size;
         final Long initPageLimitBytes = (long) (size * 10);
         AddressSettings addressSettings = new AddressSettings();
         addressSettings.setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE);
         addressSettings.setPageFullMessagePolicy(PageFullMessagePolicy.FAIL);
         addressSettings.setPageSizeBytes(initPageSizeBytes);
         addressSettings.setPageLimitBytes(initPageLimitBytes);
         addressSettings.setMaxSizeBytes(size);

         server.getAddressSettingsRepository().addMatch(queueName, addressSettings);

         int totalMessages = 30;
         int messageSize = 1024 * 10;
         sendMessageBatch(totalMessages, messageSize, session, queueAddr);

         Queue queue = server.locateQueue(queueAddr);
         assertTrue(waitForMessages(queue, totalMessages, 10000));

         PagingStore queuePagingStore = queue.getPagingStore();
         assertNotNull(queuePagingStore);
         assertTrue(queuePagingStore.isPaging());
         assertFalse(queuePagingStore.isPageFull());

         long existingPages = queuePagingStore.getNumberOfPages();
         assertTrue(existingPages > 4);

         // restart the server with a smaller pageLimitSize < existing pages.
         server.stop(true);
         waitForServerToStop(server);

         final Long newPageLimitBytes = (long) (size * 4);
         addressSettings.setPageLimitBytes(newPageLimitBytes);
         server.getAddressSettingsRepository().addMatch(queueName, addressSettings);

         // server will start regardless of current page count > (pageLimitBytes / pageSizeBytes)
         try {
            server.start();
            waitForServerToStart(server);

            // verify current situation
            queue = server.locateQueue(queueAddr);

            assertTrue(waitForMessages(queue, totalMessages, 10000));

            queuePagingStore = queue.getPagingStore();
            assertNotNull(queuePagingStore);
            assertTrue(queuePagingStore.isPaging() && queuePagingStore.isPageFull());

            long currentPages = queuePagingStore.getNumberOfPages();
            assertEquals(existingPages, currentPages);

            assertEquals(newPageLimitBytes, queuePagingStore.getPageLimitBytes());
            assertEquals(initPageSizeBytes, queuePagingStore.getPageSizeBytes());
            long maxPages = newPageLimitBytes / initPageSizeBytes;
            assertTrue(currentPages > maxPages);

            //consume messages until current pages goes down to below maxPage
            locator = createFactory(isNetty());
            final int numMessages = 25;
            try (ClientSessionFactory csf = createSessionFactory(locator)) {
               session = csf.createSession(false, true);
               session.start();
               ClientConsumer consumer = session.createConsumer(queueName);
               for (int i = 0; i < numMessages; i++) {
                  ClientMessage message = consumer.receive(5000);
                  assertNotNull(message);
                  message.acknowledge();
                  session.commit();
               }

               currentPages = queuePagingStore.getNumberOfPages();
               // check page store not page full
               assertTrue(queuePagingStore.isPaging());
               assertFalse(queuePagingStore.isPageFull());
               // the current pages should be less or equal to maxPages
               assertTrue(currentPages <= maxPages);

               //send messages one by one until page full
               ClientProducer producer = session.createProducer(queueName);
               boolean isFull = false;
               try {
                  for (int i = 0; i < numMessages; i++) {
                     ClientMessage message = createMessage(session, messageSize, i, null);
                     producer.send(message);
                     session.commit();
                  }
               } catch (ActiveMQAddressFullException e) {
                  isFull = true;
               }
               assertTrue(isFull);
               // now current pages should be one more than maxPages
               assertEquals(maxPages + 1, queuePagingStore.getNumberOfPages());

               // check paging store page full
               assertTrue(queuePagingStore.isPageFull());
            }
         } finally {
            server.stop(true);
         }
      }
   }

   /**
    * checks that there are no message duplicates in the page.  Any IDs found in the ignoreIds field will not be tested
    * this allows us to test only those messages that have been sent after the address has started paging (ignoring any
    * duplicates that may have happened before this point).
    */
   public void checkBatchMessagesAreNotPagedTwice(Queue queue) throws Exception {
      LinkedListIterator<MessageReference> pageIterator = queue.browserIterator();

      Set<String> messageOrderSet = new HashSet<>();


      int duplicates = 0;
      while (pageIterator.hasNext()) {
         MessageReference reference = pageIterator.next();

         String id = reference.getMessage().getStringProperty("id");

         // If add(id) returns true it means that this id was already added to this set.  Hence a duplicate is found.
         if (!messageOrderSet.add(id)) {
            System.out.println("Received a duplicate on " + id);
            duplicates++;
         }
      }
      assertEquals(0, duplicates);
   }

   /**
    * checks that there are no message duplicates in the page.  Any IDs found in the ignoreIds field will not be tested
    * this allows us to test only those messages that have been sent after the address has started paging (ignoring any
    * duplicates that may have happened before this point).
    */
   protected int processCountThroughIterator(Queue queue) throws Exception {
      LinkedListIterator<MessageReference> pageIterator = queue.browserIterator();

      int count = 0;
      while (pageIterator.hasNext()) {
         MessageReference reference = pageIterator.next();
         count++;
      }
      return count;
   }
}
