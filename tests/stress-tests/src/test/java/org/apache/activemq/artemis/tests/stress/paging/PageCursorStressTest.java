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
package org.apache.activemq.artemis.tests.stress.paging;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.paging.cursor.PageCursorProvider;
import org.apache.activemq.artemis.core.paging.cursor.PageSubscription;
import org.apache.activemq.artemis.core.paging.cursor.PagedReference;
import org.apache.activemq.artemis.core.paging.impl.PagingStoreImpl;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.OperationContextImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.RoutingContext;
import org.apache.activemq.artemis.core.server.impl.RoutingContextImpl;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.core.transaction.impl.TransactionImpl;
import org.apache.activemq.artemis.selector.filter.Filterable;
import org.apache.activemq.artemis.tests.unit.core.postoffice.impl.fakes.FakeQueue;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.collections.LinkedListIterator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PageCursorStressTest extends ActiveMQTestBase {



   private final SimpleString ADDRESS = SimpleString.of("test-add");

   private ActiveMQServer server;

   private Queue queue;

   private List<Queue> queueList;

   private ReadLock lock;

   private static final int PAGE_MAX = -1;

   private static final int PAGE_SIZE = 10 * 1024 * 1024;


   @Test
   public void testSimpleCursor() throws Exception {

      final int NUM_MESSAGES = 100;

      PageSubscription cursor = lookupPageStore(ADDRESS).getCursorProvider().getSubscription(queue.getID());

      Iterator<PagedReference> iterEmpty = cursor.iterator();

      long numberOfPages = addMessages(NUM_MESSAGES, 1024 * 1024);

      System.out.println("NumberOfPages = " + numberOfPages);

      PagedReference msg;

      LinkedListIterator<PagedReference> iterator = cursor.iterator();
      int key = 0;
      while ((msg = iterator.next()) != null) {
         assertEquals(key++, msg.getMessage().getIntProperty("key").intValue());
         cursor.confirmPosition(msg.getPagedMessage().newPositionObject());
      }
      assertEquals(NUM_MESSAGES, key);

      server.getStorageManager().waitOnOperations();

      waitCleanup();

      assertFalse(lookupPageStore(ADDRESS).isPaging());

      assertEquals(1, lookupPageStore(ADDRESS).getNumberOfPages());

      server.stop();
      createServer();
      waitCleanup();
      assertEquals(1, lookupPageStore(ADDRESS).getNumberOfPages());

   }

   @Test
   public void testSimpleCursorWithFilter() throws Exception {

      final int NUM_MESSAGES = 100;

      PageSubscription cursorEven = createNonPersistentCursor(new Filter() {

         @Override
         public boolean match(Message message) {
            Boolean property = message.getBooleanProperty("even");
            if (property == null) {
               return false;
            } else {
               return property.booleanValue();
            }
         }

         @Override
         public boolean match(Map<String, String> map) {
            return false;
         }

         @Override
         public boolean match(Filterable filterable) {
            return false;
         }

         @Override
         public SimpleString getFilterString() {
            return SimpleString.of("even=true");
         }

      });

      PageSubscription cursorOdd = createNonPersistentCursor(new Filter() {

         @Override
         public boolean match(Message message) {
            Boolean property = message.getBooleanProperty("even");
            if (property == null) {
               return false;
            } else {
               return !property.booleanValue();
            }
         }

         @Override
         public boolean match(Map<String, String> map) {
            return false;
         }

         @Override
         public boolean match(Filterable filterable) {
            return false;
         }

         @Override
         public SimpleString getFilterString() {
            return SimpleString.of("even=true");
         }

      });

      long numberOfPages = addMessages(NUM_MESSAGES, 1024 * 1024);

      System.out.println("NumberOfPages = " + numberOfPages);

      queue.getPageSubscription().destroy();

      PagedReference msg;

      LinkedListIterator<PagedReference> iteratorEven = cursorEven.iterator();

      LinkedListIterator<PagedReference> iteratorOdd = cursorOdd.iterator();

      int key = 0;
      while ((msg = iteratorEven.next()) != null) {
         System.out.println("Received" + msg);
         assertEquals(key, msg.getMessage().getIntProperty("key").intValue());
         assertTrue(msg.getMessage().getBooleanProperty("even").booleanValue());
         key += 2;
         cursorEven.confirmPosition(msg.getPagedMessage().newPositionObject());
      }
      assertEquals(NUM_MESSAGES, key);

      key = 1;
      while ((msg = iteratorOdd.next()) != null) {
         assertEquals(key, msg.getMessage().getIntProperty("key").intValue());
         assertFalse(msg.getMessage().getBooleanProperty("even").booleanValue());
         key += 2;
         cursorOdd.confirmPosition(msg.getPagedMessage().newPositionObject());
      }
      assertEquals(NUM_MESSAGES + 1, key);

      server.stop();
      createServer();
      waitCleanup();
      assertEquals(1, lookupPageStore(ADDRESS).getNumberOfPages());

   }

   @Test
   public void testRestartWithHoleOnAck() throws Exception {

      final int NUM_MESSAGES = 1000;

      long numberOfPages = addMessages(NUM_MESSAGES, 10 * 1024);

      System.out.println("Number of pages = " + numberOfPages);

      PageCursorProvider cursorProvider = this.server.getPagingManager().getPageStore(ADDRESS).getCursorProvider();
      System.out.println("cursorProvider = " + cursorProvider);

      PageSubscription cursor = this.server.getPagingManager().getPageStore(ADDRESS).getCursorProvider().getSubscription(queue.getID());

      System.out.println("Cursor: " + cursor);
      LinkedListIterator<PagedReference> iterator = cursor.iterator();
      for (int i = 0; i < 100; i++) {
         PagedReference msg = iterator.next();
         assertEquals(i, msg.getMessage().getIntProperty("key").intValue());
         if (i < 10 || i > 20) {
            cursor.ack(msg);
         }
      }

      server.getStorageManager().waitOnOperations();

      server.stop();

      OperationContextImpl.clearContext();

      server.start();

      cursor = this.server.getPagingManager().getPageStore(ADDRESS).getCursorProvider().getSubscription(queue.getID());
      iterator = cursor.iterator(true);

      for (int i = 10; i <= 20; i++) {
         PagedReference msg = iterator.next();
         assertEquals(i, msg.getMessage().getIntProperty("key").intValue());
         cursor.ack(msg);
      }

      for (int i = 100; i < NUM_MESSAGES; i++) {
         PagedReference msg = iterator.next();
         assertEquals(i, msg.getMessage().getIntProperty("key").intValue());
         cursor.ack(msg);
      }

      server.stop();
      createServer();
      waitCleanup();
      assertEquals(1, lookupPageStore(ADDRESS).getNumberOfPages());

   }

   @Test
   public void testRestartWithHoleOnAckAndTransaction() throws Exception {
      final int NUM_MESSAGES = 1000;

      long numberOfPages = addMessages(NUM_MESSAGES, 10 * 1024);

      System.out.println("Number of pages = " + numberOfPages);

      PageCursorProvider cursorProvider = this.server.getPagingManager().getPageStore(ADDRESS).getCursorProvider();
      System.out.println("cursorProvider = " + cursorProvider);

      PageSubscription cursor = this.server.getPagingManager().getPageStore(ADDRESS).getCursorProvider().getSubscription(queue.getID());

      System.out.println("Cursor: " + cursor);

      Transaction tx = new TransactionImpl(server.getStorageManager(), 60 * 1000);

      LinkedListIterator<PagedReference> iterator = cursor.iterator();

      for (int i = 0; i < 100; i++) {
         PagedReference msg = iterator.next();
         assertEquals(i, msg.getMessage().getIntProperty("key").intValue());
         if (i < 10 || i > 20) {
            cursor.ackTx(tx, msg);
         }
      }

      tx.commit();

      server.stop();

      OperationContextImpl.clearContext();

      server.start();

      cursor = this.server.getPagingManager().getPageStore(ADDRESS).getCursorProvider().getSubscription(queue.getID());

      tx = new TransactionImpl(server.getStorageManager(), 60 * 1000);
      iterator = cursor.iterator(true);

      for (int i = 10; i <= 20; i++) {
         PagedReference msg = iterator.next();
         assertEquals(i, msg.getMessage().getIntProperty("key").intValue());
         cursor.ackTx(tx, msg);
      }

      for (int i = 100; i < NUM_MESSAGES; i++) {
         PagedReference msg = iterator.next();
         assertEquals(i, msg.getMessage().getIntProperty("key").intValue());
         cursor.ackTx(tx, msg);
      }

      tx.commit();

      server.stop();
      createServer();
      waitCleanup();
      assertEquals(1, lookupPageStore(ADDRESS).getNumberOfPages());

   }

   @Test
   public void testConsumeLivePage() throws Exception {
      PagingStoreImpl pageStore = lookupPageStore(ADDRESS);

      pageStore.startPaging();

      final int NUM_MESSAGES = 100;

      final int messageSize = 1024 * 1024;

      PageCursorProvider cursorProvider = this.server.getPagingManager().getPageStore(ADDRESS).getCursorProvider();
      System.out.println("cursorProvider = " + cursorProvider);

      PageSubscription cursor = this.server.getPagingManager().getPageStore(ADDRESS).getCursorProvider().getSubscription(queue.getID());

      System.out.println("Cursor: " + cursor);

      RoutingContextImpl ctx = generateCTX();

      LinkedListIterator<PagedReference> iterator = cursor.iterator();

      for (int i = 0; i < NUM_MESSAGES; i++) {
         // if (i % 100 == 0)
         System.out.println("read/written " + i);

         ActiveMQBuffer buffer = RandomUtil.randomBuffer(messageSize, i + 1L);

         Message msg = new CoreMessage(i, buffer.writerIndex());
         msg.putIntProperty("key", i);

         msg.getBodyBuffer().writeBytes(buffer, 0, buffer.writerIndex());

         assertTrue(pageStore.page(msg, ctx.getTransaction(), ctx.getContextListing(ADDRESS)));

         PagedReference readMessage = iterator.next();

         assertNotNull(readMessage);

         assertEquals(i, readMessage.getMessage().getIntProperty("key").intValue());

         assertNull(iterator.next());
      }

      OperationContextImpl.clearContext();

      ctx = generateCTX();

      pageStore = lookupPageStore(ADDRESS);

      cursor = this.server.getPagingManager().getPageStore(ADDRESS).getCursorProvider().getSubscription(queue.getID());
      iterator = cursor.iterator();

      for (int i = 0; i < NUM_MESSAGES * 2; i++) {
         if (i % 100 == 0) {
            System.out.println("Paged " + i);
         }

         if (i >= NUM_MESSAGES) {

            ActiveMQBuffer buffer = RandomUtil.randomBuffer(messageSize, i + 1L);

            Message msg = new CoreMessage(i, buffer.writerIndex());
            msg.putIntProperty("key", i);

            msg.getBodyBuffer().writeBytes(buffer, 0, buffer.writerIndex());

            assertTrue(pageStore.page(msg, ctx.getTransaction(), ctx.getContextListing(ADDRESS)));
         }

         PagedReference readMessage = iterator.next();

         assertNotNull(readMessage);

         assertEquals(i, readMessage.getMessage().getIntProperty("key").intValue());
      }

      OperationContextImpl.clearContext();

      pageStore = lookupPageStore(ADDRESS);

      cursor = this.server.getPagingManager().getPageStore(ADDRESS).getCursorProvider().getSubscription(queue.getID());
      iterator = cursor.iterator();

      for (int i = 0; i < NUM_MESSAGES * 3; i++) {
         if (i % 100 == 0) {
            System.out.println("Paged " + i);
         }

         if (i >= NUM_MESSAGES * 2 - 1) {

            ActiveMQBuffer buffer = RandomUtil.randomBuffer(messageSize, i + 1L);

            Message msg = new CoreMessage(i, buffer.writerIndex());
            msg.putIntProperty("key", i + 1);

            msg.getBodyBuffer().writeBytes(buffer, 0, buffer.writerIndex());

            assertTrue(pageStore.page(msg, ctx.getTransaction(), ctx.getContextListing(ADDRESS)));
         }

         PagedReference readMessage = iterator.next();

         assertNotNull(readMessage);

         cursor.ack(readMessage);

         assertEquals(i, readMessage.getMessage().getIntProperty("key").intValue());
      }

      PagedReference readMessage = iterator.next();

      assertEquals(NUM_MESSAGES * 3, readMessage.getMessage().getIntProperty("key").intValue());

      cursor.ack(readMessage);

      server.getStorageManager().waitOnOperations();

      pageStore.flushExecutors();

      assertFalse(pageStore.isPaging());

      server.stop();
      createServer();

      assertFalse(pageStore.isPaging());

      waitCleanup();

      assertFalse(lookupPageStore(ADDRESS).isPaging());

   }

   @Test
   public void testConsumeLivePageMultiThread() throws Exception {
      final PagingStoreImpl pageStore = lookupPageStore(ADDRESS);

      pageStore.startPaging();

      final int NUM_TX = 100;

      final int MSGS_TX = 100;

      final int TOTAL_MSG = NUM_TX * MSGS_TX;

      final int messageSize = 1024;

      PageCursorProvider cursorProvider = this.server.getPagingManager().getPageStore(ADDRESS).getCursorProvider();
      System.out.println("cursorProvider = " + cursorProvider);

      PageSubscription cursor = this.server.getPagingManager().getPageStore(ADDRESS).getCursorProvider().getSubscription(queue.getID());

      System.out.println("Cursor: " + cursor);

      final StorageManager storage = this.server.getStorageManager();

      final AtomicInteger exceptions = new AtomicInteger(0);

      Thread t1 = new Thread(() -> {
         try {
            int count = 0;

            for (int txCount = 0; txCount < NUM_TX; txCount++) {

               Transaction tx = null;

               if (txCount % 2 == 0) {
                  tx = new TransactionImpl(storage);
               }

               RoutingContext ctx = generateCTX(tx);

               for (int i = 0; i < MSGS_TX; i++) {
                  //System.out.println("Sending " + count);
                  ActiveMQBuffer buffer = RandomUtil.randomBuffer(messageSize, count);

                  Message msg = new CoreMessage(i, buffer.writerIndex());
                  msg.putIntProperty("key", count++);

                  msg.getBodyBuffer().writeBytes(buffer, 0, buffer.writerIndex());

                  assertTrue(pageStore.page(msg, ctx.getTransaction(), ctx.getContextListing(ADDRESS)));
               }

               if (tx != null) {
                  tx.commit();
               }

            }
         } catch (Throwable e) {
            e.printStackTrace();
            exceptions.incrementAndGet();
         }
      });

      t1.start();

      LinkedListIterator<PagedReference> iterator = cursor.iterator();

      for (int i = 0; i < TOTAL_MSG; i++) {
         assertEquals(0, exceptions.get());
         PagedReference ref = null;
         for (int repeat = 0; repeat < 5; repeat++) {
            ref = iterator.next();
            if (ref == null) {
               Thread.sleep(1000);
            } else {
               break;
            }
         }
         assertNotNull(ref);

         ref.acknowledge();
         assertNotNull(ref);

         System.out.println("Consuming " + ref.getMessage().getIntProperty("key"));
         //assertEquals(i, ref.getMessage().getIntProperty("key").intValue());
      }

      assertEquals(0, exceptions.get());
   }

   private RoutingContextImpl generateCTX() {
      return generateCTX(null);
   }

   private RoutingContextImpl generateCTX(Transaction tx) {
      RoutingContextImpl ctx = new RoutingContextImpl(tx);
      ctx.addQueue(ADDRESS, queue);

      for (Queue q : this.queueList) {
         ctx.addQueue(ADDRESS, q);
      }

      return ctx;
   }

   private void waitCleanup() throws Exception {
      // The cleanup is done asynchronously, so we need to wait some time
      long timeout = System.currentTimeMillis() + 10000;

      while (System.currentTimeMillis() < timeout && lookupPageStore(ADDRESS).getNumberOfPages() != 1) {
         Thread.sleep(100);
      }

      assertTrue(lookupPageStore(ADDRESS).getNumberOfPages() <= 2, "expected " + lookupPageStore(ADDRESS).getNumberOfPages());
   }

   @Test
   public void testLazyCommit() throws Exception {
      PagingStoreImpl pageStore = lookupPageStore(ADDRESS);

      pageStore.startPaging();

      final int NUM_MESSAGES = 100;

      final int messageSize = 100 * 1024;

      PageCursorProvider cursorProvider = this.server.getPagingManager().getPageStore(ADDRESS).getCursorProvider();
      System.out.println("cursorProvider = " + cursorProvider);

      PageSubscription cursor = this.server.getPagingManager().getPageStore(ADDRESS).getCursorProvider().getSubscription(queue.getID());
      LinkedListIterator<PagedReference> iterator = cursor.iterator();

      System.out.println("Cursor: " + cursor);

      StorageManager storage = this.server.getStorageManager();

      long pgtxLazy = storage.generateID();

      Transaction txLazy = pgMessages(storage, pageStore, pgtxLazy, 0, NUM_MESSAGES, messageSize);

      addMessages(100, NUM_MESSAGES, messageSize);

      System.out.println("Number of pages - " + pageStore.getNumberOfPages());

      // First consume what's already there without any tx as nothing was committed
      for (int i = 100; i < 200; i++) {
         PagedReference pos = iterator.next();
         assertNotNull(pos, "Null at position " + i);
         assertEquals(i, pos.getMessage().getIntProperty("key").intValue());
         cursor.ack(pos);
      }

      assertNull(iterator.next());

      txLazy.commit();

      storage.waitOnOperations();

      for (int i = 0; i < 100; i++) {
         PagedReference pos = iterator.next();
         assertNotNull(pos, "Null at position " + i);
         assertEquals(i, pos.getMessage().getIntProperty("key").intValue());
         cursor.ack(pos);
      }

      assertNull(iterator.next());

      waitCleanup();

      server.stop();
      createServer();
      waitCleanup();
      assertEquals(1, lookupPageStore(ADDRESS).getNumberOfPages());

   }

   private int tstProperty(Message msg) {
      return msg.getIntProperty("key").intValue();
   }

   @Test
   public void testMultipleIterators() throws Exception {

      final int NUM_MESSAGES = 10;

      long numberOfPages = addMessages(NUM_MESSAGES, 1024 * 1024);

      System.out.println("NumberOfPages = " + numberOfPages);

      PageCursorProvider cursorProvider = lookupCursorProvider();

      PageSubscription cursor = cursorProvider.getSubscription(queue.getID());

      LinkedListIterator<PagedReference> iter = cursor.iterator();

      LinkedListIterator<PagedReference> iter2 = cursor.iterator();

      assertTrue(iter.hasNext());

      PagedReference msg1 = iter.next();

      PagedReference msg2 = iter2.next();

      assertEquals(tstProperty(msg1.getMessage()), tstProperty(msg2.getMessage()));

      System.out.println("property = " + tstProperty(msg1.getMessage()));

      msg1 = iter.next();

      assertEquals(1, tstProperty(msg1.getMessage()));

      iter.remove();

      msg2 = iter2.next();

      assertEquals(2, tstProperty(msg2.getMessage()));

      iter2.repeat();

      msg2 = iter2.next();

      assertEquals(2, tstProperty(msg2.getMessage()));

      iter2.repeat();

      assertEquals(2, tstProperty(msg2.getMessage()));

      msg1 = iter.next();

      assertEquals(2, tstProperty(msg1.getMessage()));

      iter.repeat();

      msg1 = iter.next();

      assertEquals(2, tstProperty(msg1.getMessage()));

      assertTrue(iter2.hasNext());

   }

   private long addMessages(final int numMessages, final int messageSize) throws Exception {
      return addMessages(0, numMessages, messageSize);
   }

   private long addMessages(final int start, final int numMessages, final int messageSize) throws Exception {
      PagingStoreImpl pageStore = lookupPageStore(ADDRESS);

      pageStore.startPaging();

      RoutingContext ctx = generateCTX();

      for (int i = start; i < start + numMessages; i++) {
         if (i % 100 == 0) {
            System.out.println("Paged " + i);
         }
         ActiveMQBuffer buffer = RandomUtil.randomBuffer(messageSize, i + 1L);

         Message msg = new CoreMessage(i, buffer.writerIndex());
         msg.putIntProperty("key", i);
         // to be used on tests that are validating filters
         msg.putBooleanProperty("even", i % 2 == 0);

         msg.getBodyBuffer().writeBytes(buffer, 0, buffer.writerIndex());

         assertTrue(pageStore.page(msg, ctx.getTransaction(), ctx.getContextListing(ADDRESS)));
      }

      return pageStore.getNumberOfPages();
   }

   private PagingStoreImpl lookupPageStore(SimpleString address) throws Exception {
      return (PagingStoreImpl) server.getPagingManager().getPageStore(address);
   }



   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      OperationContextImpl.clearContext();
      queueList = new ArrayList<>();

      createServer();
      lock = new ReentrantReadWriteLock().readLock();
   }

   private void createServer() throws Exception {
      OperationContextImpl.clearContext();

      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(true);

      server = createServer(true, config, PAGE_SIZE, PAGE_MAX, new HashMap<>());

      server.start();

      queueList.clear();

      try {
         queue = server.createQueue(QueueConfiguration.of(ADDRESS).setRoutingType(RoutingType.ANYCAST));
         queue.pause();
      } catch (Exception ignored) {
      }
   }

   private PageSubscription createNonPersistentCursor(Filter filter) throws Exception {
      long id = server.getStorageManager().generateID();
      FakeQueue queue = new FakeQueue(SimpleString.of(filter.toString()), id);
      queueList.add(queue);

      PageSubscription subs = lookupCursorProvider().createSubscription(id, filter, false);

      queue.setPageSubscription(subs);

      return subs;
   }

   private PageCursorProvider lookupCursorProvider() throws Exception {
      return lookupPageStore(ADDRESS).getCursorProvider();
   }

   private Transaction pgMessages(StorageManager storage,
                                  PagingStoreImpl pageStore,
                                  long pgParameter,
                                  int start,
                                  final int NUM_MESSAGES,
                                  final int messageSize) throws Exception {

      TransactionImpl txImpl = new TransactionImpl(pgParameter, null, storage);

      RoutingContext ctx = generateCTX(txImpl);

      for (int i = start; i < start + NUM_MESSAGES; i++) {
         ActiveMQBuffer buffer = RandomUtil.randomBuffer(messageSize, i + 1L);
         Message msg = new CoreMessage(storage.generateID(), buffer.writerIndex());
         msg.getBodyBuffer().writeBytes(buffer, 0, buffer.writerIndex());
         msg.putIntProperty("key", i);
         pageStore.page(msg, ctx.getTransaction(), ctx.getContextListing(ADDRESS));
      }

      return txImpl;

   }
}
