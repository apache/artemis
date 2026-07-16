/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.tests.performance.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.paging.cursor.PageCursorProvider;
import org.apache.activemq.artemis.core.paging.cursor.PageSubscription;
import org.apache.activemq.artemis.core.paging.impl.Page;
import org.apache.activemq.artemis.core.persistence.OperationContext;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.replication.ReplicationManager;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.core.server.RouteContextList;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.settings.impl.PageFullMessagePolicy;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.actors.ArtemisExecutor;
import org.apache.activemq.artemis.utils.runnables.AtomicRunnable;
import org.junit.jupiter.api.Test;

public class PersistMultiThreadTest extends ActiveMQTestBase {

   final String DIRECTORY = "./target/journaltmp";

   FakePagingStore fakePagingStore = new FakePagingStore();

   @Test
   public void testMultipleWrites() throws Exception {
      deleteDirectory(new File(DIRECTORY));
      ActiveMQServer server = createServer(true);
      server.getConfiguration().setJournalCompactMinFiles(ActiveMQDefaultConfiguration.getDefaultJournalCompactMinFiles());
      server.getConfiguration().setJournalCompactPercentage(ActiveMQDefaultConfiguration.getDefaultJournalCompactPercentage());
      server.getConfiguration().setJournalDirectory(DIRECTORY + "/journal");
      server.getConfiguration().setBindingsDirectory(DIRECTORY + "/bindings");
      server.getConfiguration().setPagingDirectory(DIRECTORY + "/paging");
      server.getConfiguration().setLargeMessagesDirectory(DIRECTORY + "/largemessage");

      server.getConfiguration().setJournalFileSize(10 * 1024 * 1024);
      server.getConfiguration().setJournalMinFiles(2);
      server.getConfiguration().setJournalType(JournalType.ASYNCIO);

      server.start();

      StorageManager storage = server.getStorageManager();

      long msgID = storage.generateID();
      System.out.println("msgID=" + msgID);

      int NUMBER_OF_THREADS = 50;
      int NUMBER_OF_MESSAGES = 5000;

      MyThread[] threads = new MyThread[NUMBER_OF_THREADS];

      final CountDownLatch alignFlag = new CountDownLatch(NUMBER_OF_THREADS);
      final CountDownLatch startFlag = new CountDownLatch(1);
      final CountDownLatch finishFlag = new CountDownLatch(NUMBER_OF_THREADS);

      MyDeleteThread deleteThread = new MyDeleteThread("deleteThread", storage, NUMBER_OF_MESSAGES * NUMBER_OF_THREADS * 10);
      deleteThread.start();

      for (int i = 0; i < threads.length; i++) {
         threads[i] = new MyThread("writer::" + i, storage, NUMBER_OF_MESSAGES, alignFlag, startFlag, finishFlag);
      }

      for (MyThread t : threads) {
         t.start();
      }

      alignFlag.await();

      long startTime = System.currentTimeMillis();
      startFlag.countDown();

      // I'm using a countDown to avoid measuring time spent on thread context from join.
      // i.e. i want to measure as soon as the loops are done
      finishFlag.await();
      long endtime = System.currentTimeMillis();

      System.out.println("Time:: " + (endtime - startTime));

      for (MyThread t : threads) {
         t.join();
         assertEquals(0, t.errors.get());
      }

      deleteThread.join();
      assertEquals(0, deleteThread.errors.get());

   }

   LinkedBlockingDeque<Long> deletes = new LinkedBlockingDeque<>();

   class MyThread extends Thread {

      final StorageManager storage;
      final int numberOfMessages;
      final AtomicInteger errors = new AtomicInteger(0);

      final CountDownLatch align;
      final CountDownLatch start;
      final CountDownLatch finish;

      MyThread(String name,
               StorageManager storage,
               int numberOfMessages,
               CountDownLatch align,
               CountDownLatch start,
               CountDownLatch finish) {
         super(name);
         this.storage = storage;
         this.numberOfMessages = numberOfMessages;
         this.align = align;
         this.start = start;
         this.finish = finish;
      }

      @Override
      public void run() {
         try {
            align.countDown();
            start.await();

            long id = storage.generateID();
            long txID = storage.generateID();

            // each thread will store a single message that will never be deleted, trying to force compacting to happen
            storeMessage(txID, id);
            storage.commit(txID);

            OperationContext ctx = storage.getContext();

            for (int i = 0; i < numberOfMessages; i++) {

               txID = storage.generateID();

               long[] messageID = new long[10];

               for (int msgI = 0; msgI < 10; msgI++) {
                  id = storage.generateID();

                  messageID[msgI] = id;

                  storeMessage(txID, id);
               }

               storage.commit(txID);
               ctx.waitCompletion();

               for (long deleteID : messageID) {
                  deletes.add(deleteID);
               }
            }
         } catch (Exception e) {
            e.printStackTrace();
            errors.incrementAndGet();
         } finally {
            finish.countDown();
         }

      }

      private void storeMessage(long txID, long id) throws Exception {
         Message message = new CoreMessage(id, 10 * 1024);

         message.getBodyBuffer().writeBytes(new byte[104]);
         message.putStringProperty("hello", "" + id);

         storage.storeMessageTransactional(txID, message);
         storage.storeReferenceTransactional(txID, 1, id);

         message.refDown();
      }

   }

   class MyDeleteThread extends Thread {

      final StorageManager storage;
      final int numberOfMessages;
      final AtomicInteger errors = new AtomicInteger(0);

      MyDeleteThread(String name, StorageManager storage, int numberOfMessages) {
         super(name);
         this.storage = storage;
         this.numberOfMessages = numberOfMessages;
      }

      @Override
      public void run() {
         long deletesNr = 0;
         try {

            for (int i = 0; i < numberOfMessages; i++) {
               if (i % 1000 == 0) {
                  //                        storage.getContext().waitCompletion();
                  //                        deletesNr = 0;
                  //                        Thread.sleep(200);
               }
               deletesNr++;
               Long deleteID = deletes.poll(10, TimeUnit.MINUTES);
               if (deleteID == null) {
                  System.err.println("Coudn't poll delete info");
                  errors.incrementAndGet();
                  break;
               }

               storage.storeAcknowledge(1, deleteID);
               storage.deleteMessage(deleteID);
            }
         } catch (Exception e) {
            e.printStackTrace(System.out);
            errors.incrementAndGet();
         } finally {
            System.err.println("Finished the delete loop!!!! deleted " + deletesNr);
         }
      }
   }

   class FakePagingStore implements PagingStore {

      @Override
      public PageFullMessagePolicy getPageFullMessagePolicy() {
         return null;
      }

      @Override
      public Long getPageLimitMessages() {
         return null;
      }

      @Override
      public Long getPageLimitBytes() {
         return null;
      }

      @Override
      public void pageFull(PageSubscription subscription) {

      }

      @Override
      public Page usePage(long page, boolean createEntry, boolean createFile) {
         return null;
      }

      @Override
      public boolean isPageFull() {
         return false;
      }

      @Override
      public void checkPageLimit(long numberOfMessages) {

      }

      @Override
      public void readLock() {

      }

      @Override
      public boolean readLock(long timeout) {
         return true;
      }

      @Override
      public void readUnlock() {

      }

      @Override
      public void counterSnapshot() {
      }

      @Override
      public void execute(Runnable runnable) {
         runnable.run();
      }

      @Override
      public ArtemisExecutor getExecutor() {
         return null;
      }

      @Override
      public void durableDown(Message message, int durableCount) {

      }

      @Override
      public void durableUp(Message message, int durableCount) {

      }

      @Override
      public void refUp(Message message, int nonDurableCoun) {

      }

      @Override
      public void refDown(Message message, int nonDurableCoun) {

      }

      @Override
      public Page removePage(int pageId) {
         return null;
      }

      @Override
      public SimpleString getAddress() {
         return null;
      }

      @Override
      public long getNumberOfPages() {
         return 0;
      }

      @Override
      public long getCurrentWritingPage() {
         return 0;
      }

      @Override
      public SimpleString getStoreName() {
         return null;
      }

      @Override
      public File getFolder() {
         return null;
      }

      @Override
      public AddressFullMessagePolicy getAddressFullMessagePolicy() {
         return null;
      }

      @Override
      public long getFirstPage() {
         return 0;
      }

      @Override
      public int getPageSizeBytes() {
         return 0;
      }

      @Override
      public long getAddressSize() {
         return 0;
      }

      @Override
      public long getAddressElements() {
         return 0;
      }

      @Override
      public long getMaxSize() {
         return 0;
      }

      @Override
      public boolean isFull() {
         return false;
      }

      @Override
      public boolean isRejectingMessages() {
         return false;
      }

      @Override
      public void applySetting(AddressSettings addressSettings) {

      }

      @Override
      public boolean isPaging() {
         return false;
      }

      @Override
      public boolean isStorePaging() {
         return false;
      }

      @Override
      public Page usePage(long page, boolean create) {
         return null;
      }

      @Override
      public Page usePage(long page) {
         return null;
      }

      @Override
      public Page newPageObject(long page) throws Exception {
         return null;
      }

      @Override
      public void ioSync() throws Exception {

      }

      @Override
      public int getMaxPageReadBytes() {
         return 0;
      }

      @Override
      public int getMaxPageReadMessages() {
         return 0;
      }

      @Override
      public int getPrefetchPageBytes() {
         return 0;
      }

      @Override
      public int getPrefetchPageMessages() {
         return 0;
      }

      @Override
      public boolean page(Message message,
                          Transaction tx,
                          RouteContextList listCtx) throws Exception {
         return false;
      }

      @Override
      public int page(Message message,
                          Transaction tx,
                          RouteContextList listCtx,
                          Function<Message, Message> pageDecorator,
                          boolean useFlowControl) throws Exception {
         return -1;
      }

      @Override
      public boolean checkPageFileExists(long page) throws Exception {
         return false;
      }

      @Override
      public PagingManager getPagingManager() {
         return null;
      }

      @Override
      public PageCursorProvider getCursorProvider() {
         return null;
      }

      @Override
      public void processReload() throws Exception {

      }

      @Override
      public Page depage() throws Exception {
         return null;
      }

      @Override
      public void forceAnotherPage(boolean useExecutor) throws Exception {

      }

      @Override
      public Page getCurrentPage() {
         return null;
      }

      @Override
      public boolean startPaging() throws Exception {
         return false;
      }

      @Override
      public void stopPaging() throws Exception {
      }

      @Override
      public void addSize(int size, boolean sizeOnly, boolean affectGlobal) {
      }

      @Override
      public void addSize(int size) {

      }

      @Override
      public boolean checkMemory(boolean runOnFailure, Runnable runnable, Runnable ignoredRunnable, Consumer<AtomicRunnable> accepted) {
         return false;
      }

      @Override
      public boolean checkMemory(Runnable runnable, Consumer<AtomicRunnable> accepted) {
         return false;
      }

      @Override
      public void writeLock() {
      }

      @Override
      public boolean writeLock(long timeout) {
         return false;
      }

      @Override
      public void writeUnlock() {

      }

      @Override
      public void flushExecutors() {

      }

      @Override
      public Collection<Integer> getCurrentIds() throws Exception {
         return null;
      }

      @Override
      public void sendPages(ReplicationManager replicator, Collection<Integer> pageIds) throws Exception {

      }

      @Override
      public void disableCleanup() {

      }

      @Override
      public void enableCleanup() {

      }

      @Override
      public void start() throws Exception {

      }

      @Override
      public void stop() throws Exception {

      }

      @Override
      public boolean isStarted() {
         return false;
      }

      @Override
      public boolean checkReleasedMemory() {
         return true;
      }

      @Override
      public void destroy() throws Exception {
      }

      @Override
      public int getAddressLimitPercent() {
         return 0;
      }

      @Override
      public void block() {
      }

      @Override
      public void unblock() {
      }

      @Override
      public boolean isBlockedViaManagement() {
         return false;
      }
   }
}
