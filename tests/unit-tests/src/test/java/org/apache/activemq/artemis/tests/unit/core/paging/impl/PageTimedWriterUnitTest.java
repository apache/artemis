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

package org.apache.activemq.artemis.tests.unit.core.paging.impl;

import javax.transaction.xa.Xid;
import java.lang.invoke.MethodHandles;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.io.OperationConsistencyLevel;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.journal.EncodingSupport;
import org.apache.activemq.artemis.core.journal.Journal;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.paging.PagedMessage;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.paging.PagingStoreFactory;
import org.apache.activemq.artemis.core.paging.impl.PageTimedWriter;
import org.apache.activemq.artemis.core.paging.impl.PagedMessageImpl;
import org.apache.activemq.artemis.core.paging.impl.PagingStoreImpl;
import org.apache.activemq.artemis.core.persistence.OperationContext;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManagerAccessor;
import org.apache.activemq.artemis.core.persistence.impl.journal.OperationContextImpl;
import org.apache.activemq.artemis.core.replication.ReplicationManager;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.RouteContextList;
import org.apache.activemq.artemis.core.server.impl.RoutingContextImpl;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.core.transaction.TransactionOperation;
import org.apache.activemq.artemis.core.transaction.TransactionOperationAbstract;
import org.apache.activemq.artemis.core.transaction.impl.TransactionImpl;
import org.apache.activemq.artemis.core.transaction.impl.TransactionImplAccessor;
import org.apache.activemq.artemis.tests.unit.core.journal.impl.fakes.FakeSequentialFileFactory;
import org.apache.activemq.artemis.tests.util.ArtemisTestCase;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.ReusableLatch;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.artemis.utils.actors.OrderedExecutorFactory;
import org.apache.activemq.artemis.utils.critical.CriticalAnalyzer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PageTimedWriterUnitTest extends ArtemisTestCase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final SimpleString ADDRESS = SimpleString.of("someAddress");

   AtomicInteger outOfContextWrite = new AtomicInteger(0);
   ScheduledExecutorService scheduledExecutorService;
   ExecutorService executorService;
   OrderedExecutorFactory executorFactory;
   OperationContext context;

   RouteContextList routeContextList;

   // almost real as we use an extension that can allow mocking internal objects such as journals and replicationManager
   JournalStorageManager realJournalStorageManager;

   PagingStoreImpl pageStore;

   CountDownLatch allowRunning;

   final ReusableLatch enteredSync = new ReusableLatch(1);
   final ReusableLatch allowSync = new ReusableLatch(0);
   final AtomicInteger doneSync = new AtomicInteger(0);

   final AtomicInteger pageWrites = new AtomicInteger(0);

   PageTimedWriter timer;

   Configuration configuration;

   SequentialFileFactory inMemoryFileFactory;

   Journal mockBindingsJournal;
   Journal mockMessageJournal;

   AtomicInteger numberOfCommitsMessageJournal = new AtomicInteger(0);
   AtomicInteger numberOfPreparesMessageJournal = new AtomicInteger(0);

   AtomicBoolean useReplication = new AtomicBoolean(false);
   AtomicBoolean returnSynchronizing = new AtomicBoolean(false);
   ReplicationManager mockReplicationManager;

   Consumer<PagedMessage> directWriteInterceptor = null;
   Runnable ioSyncInterceptor = null;

   private class MockableJournalStorageManager extends JournalStorageManager {

      MockableJournalStorageManager(Configuration config,
                                    Journal bindingsJournal,
                                    Journal messagesJournal,
                                    ExecutorFactory executorFactory,
                                    ExecutorFactory ioExecutors) {
         super(config, Mockito.mock(CriticalAnalyzer.class), executorFactory, ioExecutors);
         this.bindingsJournal = bindingsJournal;
         this.messageJournal = messagesJournal;
      }

      @Override
      public void start() throws Exception {
         super.start();
         idGenerator.forceNextID(1);
      }

      @Override
      protected void createDirectories() {
         // not creating any folders
      }

      @Override
      public void pageWrite(final SimpleString address,
                            final PagedMessage message,
                            final long pageNumber,
                            boolean storageUp,
                            boolean originallyReplicated) {
         super.pageWrite(address, message, pageNumber, storageUp, originallyReplicated);
         pageWrites.incrementAndGet();
      }
   }

   private CoreMessage createMessage() {
      long id = realJournalStorageManager.generateID();
      ActiveMQBuffer buffer = RandomUtil.randomBuffer(10);
      CoreMessage msg = new CoreMessage(id, 50 + buffer.capacity());

      msg.setAddress(ADDRESS);

      msg.getBodyBuffer().resetReaderIndex();
      msg.getBodyBuffer().resetWriterIndex();

      msg.getBodyBuffer().writeBytes(buffer, buffer.capacity());

      return msg;
   }

   private int commitCall() {
      return numberOfCommitsMessageJournal.incrementAndGet();
   }

   private int prepareCall() {
      return numberOfPreparesMessageJournal.incrementAndGet();
   }

   @AfterEach
   public void validateWrites() {
      assertEquals(0, outOfContextWrite.get(), "Wrong executor in use at page writes");
   }

   @BeforeEach
   public void setupMocks() throws Exception {
      outOfContextWrite.set(0);
      configuration = new ConfigurationImpl();
      configuration.setJournalType(JournalType.NIO);
      scheduledExecutorService = Executors.newScheduledThreadPool(10);
      executorService = Executors.newFixedThreadPool(10);
      runAfter(scheduledExecutorService::shutdownNow);
      runAfter(executorService::shutdownNow);
      executorFactory = new OrderedExecutorFactory(executorService);
      context = OperationContextImpl.getContext(executorFactory);
      assertNotNull(context);

      mockBindingsJournal = Mockito.mock(Journal.class);
      mockMessageJournal = Mockito.mock(Journal.class);

      Mockito.doAnswer(a -> commitCall()).when(mockMessageJournal).appendCommitRecord(Mockito.anyLong(), Mockito.anyBoolean());
      Mockito.doAnswer(a -> commitCall()).when(mockMessageJournal).appendCommitRecord(Mockito.anyLong(), Mockito.anyBoolean(), Mockito.any());
      Mockito.doAnswer(a -> commitCall()).when(mockMessageJournal).appendCommitRecord(Mockito.anyLong(), Mockito.anyBoolean(), Mockito.any(), Mockito.anyBoolean());

      Mockito.doAnswer(a -> prepareCall()).when(mockMessageJournal).appendPrepareRecord(Mockito.anyLong(), (byte[]) Mockito.any(), Mockito.anyBoolean());
      Mockito.doAnswer(a -> prepareCall()).when(mockMessageJournal).appendPrepareRecord(Mockito.anyLong(), Mockito.any(EncodingSupport.class), Mockito.anyBoolean(), Mockito.any());
      Mockito.doAnswer(a -> prepareCall()).when(mockMessageJournal).appendPrepareRecord(Mockito.anyLong(), Mockito.any(EncodingSupport.class), Mockito.anyBoolean());

      mockReplicationManager = Mockito.mock(ReplicationManager.class);
      Mockito.when(mockReplicationManager.isStarted()).thenAnswer(a -> useReplication.get());
      Mockito.when(mockReplicationManager.isSynchronizing()).thenAnswer(a -> returnSynchronizing.get());
      Mockito.doAnswer(a -> {
         if (useReplication.get()) {
            OperationContext ctx = OperationContextImpl.getContext();
            if (ctx != null) {
               ctx.replicationDone();
            }
         }
         return null;
      }).when(mockReplicationManager).pageWrite(Mockito.any(SimpleString.class), Mockito.any(PagedMessage.class), Mockito.anyLong(), Mockito.anyBoolean());

      realJournalStorageManager = new MockableJournalStorageManager(configuration, mockBindingsJournal, mockMessageJournal, executorFactory, executorFactory);
      realJournalStorageManager.start();

      JournalStorageManagerAccessor.setReplicationManager(realJournalStorageManager, mockReplicationManager);

      allowRunning = new CountDownLatch(1);

      inMemoryFileFactory = new FakeSequentialFileFactory();

      PagingStoreFactory mockPageStoreFactory = Mockito.mock(PagingStoreFactory.class);
      PagingManager mockPagingManager = Mockito.mock(PagingManager.class);

      final int MAX_SIZE = 1024 * 10;

      AddressSettings settings = new AddressSettings().setPageSizeBytes(MAX_SIZE).setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE);

      pageStore = new PagingStoreImpl(ADDRESS, scheduledExecutorService, 100, mockPagingManager, realJournalStorageManager, inMemoryFileFactory, mockPageStoreFactory, ADDRESS, settings, executorFactory.getExecutor(), false, () -> true) {
         @Override
         protected PageTimedWriter createPageTimedWriter(ScheduledExecutorService scheduledExecutor, long syncTimeout) {

            PageTimedWriter timer = new PageTimedWriter(100_000, realJournalStorageManager, this, scheduledExecutorService, getExecutor(), true, 100) {
               @Override
               public void run() {
                  try {
                     allowRunning.await();
                  } catch (InterruptedException e) {
                     logger.warn(e.getMessage(), e);
                     Thread.currentThread().interrupt();

                  }
                  super.run();
               }

               @Override
               protected void performSync() throws Exception {
                  enteredSync.countDown();
                  super.performSync();
                  try {
                     allowSync.await();
                  } catch (InterruptedException e) {
                     logger.warn(e.getMessage(), e);
                  }
                  doneSync.incrementAndGet();
               }
            };

            timer.start();
            return timer;
         }

         @Override
         protected void directWritePage(PagedMessage pagedMessage,
                                        boolean lineUp,
                                        boolean originalReplicated) throws Exception {
            if (directWriteInterceptor != null) {
               directWriteInterceptor.accept(pagedMessage);
            }

            if (!pageStore.getExecutor().inHandler()) {
               logger.warn("directWritePage is not using the correct executor", new Exception("trace"));
               outOfContextWrite.incrementAndGet();
            }
            super.directWritePage(pagedMessage, lineUp, originalReplicated);
         }

         @Override
         public void ioSync() throws Exception {
            if (ioSyncInterceptor != null) {
               ioSyncInterceptor.run();
            }
            super.ioSync();
         }
      };

      timer = pageStore.getPageTimedWriter();

      pageStore.start();
      pageStore.startPaging();

      routeContextList = new RoutingContextImpl.ContextListing();
      Queue mockQueue = Mockito.mock(Queue.class);
      Mockito.when(mockQueue.getID()).thenReturn(1L);
      routeContextList.addAckedQueue(mockQueue);

      OperationContextImpl.clearContext();
      runAfter(OperationContextImpl::clearContext);
   }

   @AfterEach
   public void clearContext() {
      OperationContextImpl.clearContext();
   }

   // a test to validate if the Mocks are correctly setup
   @Test
   public void testValidateMocks() throws Exception {
      TransactionImpl tx = new TransactionImpl(realJournalStorageManager);
      tx.setContainsPersistent();
      AtomicInteger count = new AtomicInteger(0);
      tx.addOperation(new TransactionOperationAbstract() {
         @Override
         public void afterCommit(Transaction tx) {
            count.incrementAndGet();
         }
      });
      assertEquals(0, count.get());
      tx.commit();
      assertEquals(1, count.get(), "tx.commit is not correctly wired on mocking");

      realJournalStorageManager.afterCompleteOperations(new IOCallback() {
         @Override
         public void done() {
            count.incrementAndGet();
         }

         @Override
         public void onError(int errorCode, String errorMessage) {

         }
      });

      realJournalStorageManager.afterCompleteOperations(new IOCallback() {
         @Override
         public void done() {
            count.incrementAndGet();
         }

         @Override
         public void onError(int errorCode, String errorMessage) {

         }
      }, OperationConsistencyLevel.FULL);

      assertEquals(3, count.get(), "afterCompletion is not correctly wired on mocking");

      long id = realJournalStorageManager.generateID();
      long newID = realJournalStorageManager.generateID();
      assertEquals(1L, newID - id);
   }

   PagedMessage createPagedMessage() {
      return new PagedMessageImpl(createMessage(), new long[]{1}, -1);
   }

   @Test
   public void testIOCompletion() throws Exception {
      CountDownLatch latch = new CountDownLatch(1);

      OperationContextImpl.setContext(context);
      assertTrue(realJournalStorageManager.addToPage(pageStore, createMessage(), null, Mockito.mock(RouteContextList.class)));

      context.executeOnCompletion(new IOCallback() {
         @Override
         public void done() {
            latch.countDown();
         }

         @Override
         public void onError(int errorCode, String errorMessage) {
         }
      });

      assertFalse(latch.await(10, TimeUnit.MILLISECONDS));
      allowRunning.countDown();
      assertTrue(latch.await(10, TimeUnit.SECONDS));
   }

   @Test
   public void testIOCompletionWhileReplica() throws Exception {
      CountDownLatch latch = new CountDownLatch(1);

      useReplication.set(true);

      OperationContextImpl.setContext(context);
      assertTrue(realJournalStorageManager.addToPage(pageStore, createMessage(), null, Mockito.mock(RouteContextList.class)));

      context.executeOnCompletion(new IOCallback() {
         @Override
         public void done() {
            latch.countDown();
         }

         @Override
         public void onError(int errorCode, String errorMessage) {
         }
      });

      assertFalse(latch.await(10, TimeUnit.MILLISECONDS));
      allowRunning.countDown();
      assertTrue(latch.await(10, TimeUnit.SECONDS));
   }

   @Test
   public void testTXCompletionWhileReplica() throws Exception {
      CountDownLatch latch = new CountDownLatch(1);

      useReplication.set(true);

      TransactionImpl transaction = new TransactionImpl(realJournalStorageManager);
      transaction.setContainsPersistent();
      transaction.addOperation(new TransactionOperationAbstract() {
         @Override
         public void afterCommit(Transaction tx) {
            super.afterCommit(tx);
            latch.countDown();
         }
      });

      OperationContextImpl.setContext(context);
      assertTrue(realJournalStorageManager.addToPage(pageStore, createMessage(), transaction, Mockito.mock(RouteContextList.class)));

      transaction.commit();

      assertFalse(latch.await(10, TimeUnit.MILLISECONDS));
      allowRunning.countDown();
      assertTrue(latch.await(10, TimeUnit.SECONDS));
   }

   @Test
   public void testAllowWriteWhileSyncPending() throws Exception {
      int numberOfMessages = 100;
      CountDownLatch latch = new CountDownLatch(numberOfMessages);

      allowRunning.countDown();
      useReplication.set(false);

      assertEquals(0, doneSync.get());
      enteredSync.setCount(1);
      allowSync.setCount(1);

      for (int i = 0; i < numberOfMessages; i++) {
         TransactionImpl newTX = new TransactionImpl(realJournalStorageManager);
         newTX.setContainsPersistent();
         newTX.addOperation(new TransactionOperationAbstract() {
            @Override
            public void afterCommit(Transaction tx) {
               super.afterCommit(tx);
               latch.countDown();
            }
         });
         pageStore.page(createMessage(), newTX, routeContextList);
         newTX.commit();

         if (i == 0) {
            assertTrue(enteredSync.await(10, TimeUnit.SECONDS));
            assertEquals(0, doneSync.get()); // it should not complete until allowSync is released
         }
      }

      assertFalse(latch.await(10, TimeUnit.MILLISECONDS));
      allowSync.countDown();
      assertTrue(latch.await(10, TimeUnit.SECONDS));

      assertEquals(numberOfMessages, pageWrites.get());
   }

   @Test
   public void testVerifyTimedWriterIsStopped() throws Exception {
      allowRunning.countDown();
      useReplication.set(false);
      pageStore.stop();
      assertFalse(pageStore.getPageTimedWriter().isStarted());
   }

   @Test
   public void testTXCompletionWhileDisableReplica() throws Exception {
      CountDownLatch latch = new CountDownLatch(1);

      useReplication.set(true);

      TransactionImpl transaction = new TransactionImpl(realJournalStorageManager);
      transaction.setContainsPersistent();
      transaction.addOperation(new TransactionOperationAbstract() {
         @Override
         public void afterCommit(Transaction tx) {
            super.afterCommit(tx);
            latch.countDown();
         }
      });

      OperationContextImpl.setContext(context);
      assertTrue(realJournalStorageManager.addToPage(pageStore, createMessage(), transaction, Mockito.mock(RouteContextList.class)));

      numberOfCommitsMessageJournal.set(0); // it should been 0 before anyway but since I have no real reason to require it to be zero before, I'm doing this just in case it ever changes
      transaction.commit();
      Assertions.assertEquals(0, numberOfCommitsMessageJournal.get());
      assertFalse(latch.await(10, TimeUnit.MILLISECONDS));
      useReplication.set(false);
      allowRunning.countDown();
      assertTrue(latch.await(10, TimeUnit.SECONDS));
      Wait.assertEquals(1, numberOfCommitsMessageJournal::get, 5000, 100);
   }

   @Test
   public void testTXCompletionPrepare() throws Exception {
      CountDownLatch latch = new CountDownLatch(1);

      useReplication.set(true);

      TransactionImpl transaction = new TransactionImpl(Mockito.mock(Xid.class), realJournalStorageManager, -1);
      transaction.setContainsPersistent();
      transaction.addOperation(new TransactionOperationAbstract() {
         @Override
         public void afterPrepare(Transaction tx) {
            super.afterCommit(tx);
            latch.countDown();
         }
      });

      OperationContextImpl.setContext(context);
      assertTrue(realJournalStorageManager.addToPage(pageStore, createMessage(), transaction, Mockito.mock(RouteContextList.class)));

      numberOfCommitsMessageJournal.set(0); // it should been 0 before anyway but since I have no real reason to require it to be zero before, I'm doing this just in case it ever changes
      numberOfPreparesMessageJournal.set(0);
      transaction.prepare();
      Assertions.assertEquals(0, numberOfCommitsMessageJournal.get());
      Assertions.assertEquals(0, numberOfPreparesMessageJournal.get());
      assertFalse(latch.await(10, TimeUnit.MILLISECONDS));
      allowRunning.countDown();
      assertTrue(latch.await(10, TimeUnit.SECONDS));
      Assertions.assertEquals(0, numberOfCommitsMessageJournal.get());
      Wait.assertEquals(1, numberOfPreparesMessageJournal::get, 5000, 100);
   }

   // add a task while replicating, process it when no longer replicating (disconnect a node scenario)
   @Test
   public void testDisableReplica() throws Exception {
      CountDownLatch latch = new CountDownLatch(1);

      useReplication.set(true);

      OperationContextImpl.setContext(context);
      assertTrue(realJournalStorageManager.addToPage(pageStore, createMessage(), null, Mockito.mock(RouteContextList.class)));

      context.executeOnCompletion(new IOCallback() {
         @Override
         public void done() {
            latch.countDown();
         }

         @Override
         public void onError(int errorCode, String errorMessage) {
         }
      });

      assertFalse(latch.await(10, TimeUnit.MILLISECONDS));
      useReplication.set(false);
      allowRunning.countDown();
      assertTrue(latch.await(10, TimeUnit.SECONDS));
   }

   // add a task while not replicating, process it when is now replicating (reconnect a node scenario)
   // this is the oppostie from testDisableReplica
   @Test
   public void testEnableReplica() throws Exception {
      CountDownLatch latch = new CountDownLatch(1);

      useReplication.set(false);

      OperationContextImpl.setContext(context);
      assertTrue(realJournalStorageManager.addToPage(pageStore, createMessage(), null, Mockito.mock(RouteContextList.class)));

      context.executeOnCompletion(new IOCallback() {
         @Override
         public void done() {
            latch.countDown();
         }

         @Override
         public void onError(int errorCode, String errorMessage) {
         }
      });

      assertFalse(latch.await(10, TimeUnit.MILLISECONDS));
      useReplication.set(true);
      allowRunning.countDown();
      assertTrue(latch.await(10, TimeUnit.SECONDS));
   }

   @Test
   public void testTXCompletion() throws Exception {
      OperationContextImpl.clearContext();

      OperationContext context = OperationContextImpl.getContext(executorFactory);

      CountDownLatch latch = new CountDownLatch(1);

      Transaction tx = new TransactionImpl(realJournalStorageManager, Integer.MAX_VALUE);
      tx.setContainsPersistent();

      OperationContextImpl.setContext(context);
      assertTrue(realJournalStorageManager.addToPage(pageStore, createMessage(), tx, Mockito.mock(RouteContextList.class)));
      tx.addOperation(new TransactionOperationAbstract() {
         @Override
         public void afterCommit(Transaction tx) {
            latch.countDown();
         }
      });
      tx.commit();

      assertFalse(latch.await(10, TimeUnit.MILLISECONDS));

      allowRunning.countDown();

      assertTrue(latch.await(10, TimeUnit.SECONDS));
   }

   @Test
   public void testDelayNonPersistent() throws Exception {
      internalDelay(false);
   }

   @Test
   public void testDelayPersistent() throws Exception {
      internalDelay(true);
   }

   private void internalDelay(boolean persistent) throws Exception {
      TransactionImpl tx = new TransactionImpl(realJournalStorageManager);

      final AtomicInteger afterRollback = new AtomicInteger(0);
      final AtomicInteger afterCommit = new AtomicInteger(0);

      tx.addOperation(new TransactionOperation() {
         @Override
         public void beforePrepare(Transaction tx) throws Exception {

         }

         @Override
         public void afterPrepare(Transaction tx) {

         }

         @Override
         public void beforeCommit(Transaction tx) throws Exception {

         }

         @Override
         public void afterCommit(Transaction tx) {
            afterCommit.incrementAndGet();
         }

         @Override
         public void beforeRollback(Transaction tx) throws Exception {

         }

         @Override
         public void afterRollback(Transaction tx) {
            afterRollback.incrementAndGet();
         }

         @Override
         public List<MessageReference> getRelatedMessageReferences() {
            return null;
         }

         @Override
         public List<MessageReference> getListOnConsumer(long consumerID) {
            return null;
         }
      });
      TransactionImplAccessor.setContainsPersistent(tx, persistent);
      tx.delay();
      tx.commit();
      assertEquals(0, afterCommit.get());
      assertEquals(0, afterRollback.get());
      tx.delayDone();
      Wait.assertEquals(1, afterCommit::get, 5000, 100);
   }

   @Test
   public void testRollbackCancelDelay() throws Exception {
      testRollback(true);
   }

   @Test
   public void testMarkRollbackCancelDelay() throws Exception {
      testRollback(false);
   }

   private void testRollback(boolean rollback) throws Exception {
      TransactionImpl tx = new TransactionImpl(realJournalStorageManager);

      final AtomicInteger afterRollback = new AtomicInteger(0);
      final AtomicInteger afterCommit = new AtomicInteger(0);

      tx.addOperation(new TransactionOperation() {
         @Override
         public void beforePrepare(Transaction tx) throws Exception {

         }

         @Override
         public void afterPrepare(Transaction tx) {

         }

         @Override
         public void beforeCommit(Transaction tx) throws Exception {

         }

         @Override
         public void afterCommit(Transaction tx) {
            afterCommit.incrementAndGet();
         }

         @Override
         public void beforeRollback(Transaction tx) throws Exception {

         }

         @Override
         public void afterRollback(Transaction tx) {
            afterRollback.incrementAndGet();
         }

         @Override
         public List<MessageReference> getRelatedMessageReferences() {
            return null;
         }

         @Override
         public List<MessageReference> getListOnConsumer(long consumerID) {
            return null;
         }
      });
      TransactionImplAccessor.setContainsPersistent(tx, true);
      tx.delay();
      tx.commit();
      assertEquals(0, afterCommit.get());
      assertEquals(0, afterRollback.get());
      if (rollback) {
         tx.markAsRollbackOnly(new ActiveMQException("duh!"));
      } else {
         tx.rollback();
      }
      tx.delayDone();
      Wait.assertEquals(0, afterCommit::get, 5000, 100);

      if (!rollback) {
         tx.rollback();
      }

      Wait.assertEquals(0, afterCommit::get, 5000, 100);
      Wait.assertEquals(0, afterRollback::get, 5000, 100);
   }

   @Test
   public void testSimulateFlow() throws Exception {
      AtomicBoolean notSupposedToWrite = new AtomicBoolean(false);

      AtomicInteger errors = new AtomicInteger(0);

      int sleepTime = 1;
      int totalTime = 500;
      AtomicBoolean pageStoreThrowsExceptions = new AtomicBoolean(false);

      LinkedHashSet<String> interceptedWrite = new LinkedHashSet<>();
      LinkedHashSet<String> sentWrite = new LinkedHashSet<>();

      directWriteInterceptor = m -> {
         if (pageStoreThrowsExceptions.get()) {
            throw new NullPointerException("simulating a NPE on directWrite");
         }
         String messageID = m.getMessage().getStringProperty("testId");
         if (messageID == null) {
            logger.warn("no messageID defined on message");
            errors.incrementAndGet();
         }
         if (notSupposedToWrite.get()) {
            logger.warn("Not supposed to write message {}", m.getMessage().getStringProperty("testId"));
            errors.incrementAndGet();
         }
         interceptedWrite.add(m.getMessage().getStringProperty("testId"));
      };
      ioSyncInterceptor = () -> {
         if (pageStoreThrowsExceptions.get()) {
            throw new NullPointerException("simulating a NPE on ioSync");
         }
      };

      allowRunning.countDown();
      // I don't want to mess with the Executor simulating to be on the
      ExecutorService testExecutor = Executors.newFixedThreadPool(1);

      AtomicBoolean running = new AtomicBoolean(true);
      runAfter(() -> running.set(false));
      runAfter(testExecutor::shutdownNow);

      CountDownLatch runLatch = new CountDownLatch(1);
      CyclicBarrier flagStart = new CyclicBarrier(2);

      // stop.. start.. stop ... start..
      testExecutor.execute(() -> {
         try {
            flagStart.await(10, TimeUnit.SECONDS);
            while (running.get()) {
               Thread.sleep(sleepTime);
               logger.debug("Forcing page");

               // this is simulating what would happen on replication, we lock the storage
               // and force another page
               // also no writing should happen while we hold the lock
               realJournalStorageManager.writeLock();
               try {
                  notSupposedToWrite.set(true);
                  Thread.sleep(sleepTime);
                  pageStore.forceAnotherPage(false);
                  notSupposedToWrite.set(false);
               } finally {
                  realJournalStorageManager.writeUnlock();
               }
               Thread.sleep(sleepTime);
               logger.debug("stopping");
               timer.stop();
               pageStoreThrowsExceptions.set(true);
               Thread.sleep(sleepTime);
               pageStoreThrowsExceptions.set(false);
               logger.debug("starting");
               timer.start();
            }
         } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
            errors.incrementAndGet();
         } finally {
            runLatch.countDown();
         }
      });

      flagStart.await(10, TimeUnit.SECONDS);

      long timeout = System.currentTimeMillis() + totalTime;

      RouteContextList routeContextListMocked = Mockito.mock(RouteContextList.class);

      CountDownLatch committed = new CountDownLatch(1);

      TransactionImpl tx = new TransactionImpl(realJournalStorageManager);
      tx.afterStore(new TransactionOperationAbstract() {
         @Override
         public void afterCommit(Transaction tx) {
            committed.countDown();
         }
      });
      TransactionImplAccessor.setContainsPersistent(tx, true);

      int sentNumber = 0;
      while (timeout > System.currentTimeMillis()) {
         try {
            Message message = createMessage();
            message.putStringProperty("testId", String.valueOf(sentNumber));
            OperationContextImpl.setContext(context);
            assertTrue(realJournalStorageManager.addToPage(pageStore, message, tx, routeContextListMocked));
            sentWrite.add(String.valueOf(sentNumber));
            sentNumber++;
            if (sentNumber % 1000 == 0) {
               logger.info("Sent {}", sentNumber);
            }
         } catch (IllegalStateException notStarted) {
            logger.debug("Retrying {}", sentNumber);
            // ok
         }
      }

      running.set(false);
      assertTrue(runLatch.await(10, TimeUnit.SECONDS));
      assertTrue(timer.isStarted());
      timer.delay(); // calling one more delay as the last one done could still be missing

      assertEquals(0, errors.get());

      Wait.assertEquals(0, tx::getPendingDelay, 5000, 100);
      // not supposed to throw any exceptions
      tx.commit();
      assertTrue(committed.await(10, TimeUnit.SECONDS));

      Wait.assertTrue(() -> interceptedWrite.size() == sentWrite.size(), 5000, 100);

      int interceptorOriginalSize = interceptedWrite.size();
      int sentOriginalSize = sentWrite.size();

      interceptedWrite.forEach(s -> {
         sentWrite.remove(s);
      });
      sentWrite.forEach(m -> {
         logger.warn("message {} missed", m);
      });

      assertEquals(interceptorOriginalSize, sentOriginalSize);

      assertEquals(0, sentWrite.size());

      assertEquals(interceptorOriginalSize, sentOriginalSize);
      assertEquals(sentNumber, interceptorOriginalSize);
   }

   @Test
   public void testLockWhileFlowControlled() throws Exception {
      AtomicInteger errors = new AtomicInteger(0);

      LinkedHashSet<String> interceptedWrite = new LinkedHashSet<>();
      LinkedHashSet<String> sentWrite = new LinkedHashSet<>();

      directWriteInterceptor = m -> {
         String messageID = m.getMessage().getStringProperty("testId");
         if (messageID == null) {
            logger.warn("no messageID defined on message");
            errors.incrementAndGet();
         }
         interceptedWrite.add(m.getMessage().getStringProperty("testId"));
      };

      // I don't want to mess with the Executor simulating to be on the
      ExecutorService testExecutor = Executors.newFixedThreadPool(1);

      AtomicBoolean running = new AtomicBoolean(true);
      runAfter(() -> running.set(false));
      runAfter(testExecutor::shutdownNow);

      CountDownLatch runLatch = new CountDownLatch(1);
      CyclicBarrier flagStart = new CyclicBarrier(2);

      AtomicInteger sentNumber = new AtomicInteger(0);

      // sending messages
      testExecutor.execute(() -> {
         try {
            flagStart.await(10, TimeUnit.SECONDS);

            RouteContextList routeContextListMocked = Mockito.mock(RouteContextList.class);

            while (running.get()) {
               Message message = createMessage();
               message.putStringProperty("testId", String.valueOf(sentNumber));
               OperationContextImpl.setContext(context);
               assertTrue(realJournalStorageManager.addToPage(pageStore, message, null, routeContextListMocked));
               sentWrite.add(String.valueOf(sentNumber.get()));
               sentNumber.incrementAndGet();
               if (sentNumber.get() % 1000 == 0) {
                  logger.info("Sent {}", sentNumber);
               }
            }
         } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
            errors.incrementAndGet();
         } finally {
            runLatch.countDown();
         }
      });

      flagStart.await(10, TimeUnit.SECONDS);

      // getting a base credit for most messages
      int baseCreditSize = createMessage().putStringProperty("testID", "00000").getEncodeSize();

      // Waiting messages to stop flowing
      Wait.assertTrue(() -> timer.getAvailablePermits() < baseCreditSize, 5_000, 10);

      // this is simulating certain operations that will need to get a writeLock (example replication start)
      // we still must be able to get a writeLock while paging is flow controller
      // otherwise we could starve or deadlock in some scenarios
      realJournalStorageManager.writeLock();
      realJournalStorageManager.writeUnlock();

      allowRunning.countDown(); // allowing messages to flow again
      running.set(false);
      assertTrue(runLatch.await(10, TimeUnit.SECONDS));
      assertTrue(timer.isStarted());
      timer.delay(); // calling one more delay as the last one done could still be missing
      assertEquals(0, errors.get());
      Wait.assertTrue(() -> interceptedWrite.size() == sentWrite.size(), 5000, 100);
      int interceptorOriginalSize = interceptedWrite.size();
      int sentOriginalSize = sentWrite.size();

      interceptedWrite.forEach(s -> {
         sentWrite.remove(s);
      });
      sentWrite.forEach(m -> {
         logger.warn("message {} missed", m);
      });

      assertEquals(interceptorOriginalSize, sentOriginalSize);

      assertEquals(0, sentWrite.size());

      assertEquals(interceptorOriginalSize, sentOriginalSize);
      assertEquals(sentNumber.get(), interceptorOriginalSize);
   }
}