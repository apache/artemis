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
package org.apache.activemq.artemis.core.paging.impl;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.paging.PageTransactionInfo;
import org.apache.activemq.artemis.core.paging.PagedMessage;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.paging.PagingStoreFactory;
import org.apache.activemq.artemis.core.paging.cursor.LivePageCache;
import org.apache.activemq.artemis.core.paging.cursor.PageCursorProvider;
import org.apache.activemq.artemis.core.paging.cursor.impl.LivePageCacheImpl;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.replication.ReplicationManager;
import org.apache.activemq.artemis.core.server.ActiveMQMessageBundle;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.LargeServerMessage;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.RouteContextList;
import org.apache.activemq.artemis.core.server.impl.MessageReferenceImpl;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.core.transaction.TransactionOperation;
import org.apache.activemq.artemis.core.transaction.TransactionPropertyIndexes;
import org.apache.activemq.artemis.utils.FutureLatch;
import org.apache.activemq.artemis.utils.actors.ArtemisExecutor;
import org.apache.activemq.artemis.utils.runnables.AtomicRunnable;
import org.jboss.logging.Logger;

/**
 * @see PagingStore
 */
public class PagingStoreImpl implements PagingStore {

   private static final Logger logger = Logger.getLogger(PagingStoreImpl.class);

   private final SimpleString address;

   private final StorageManager storageManager;

   private final DecimalFormat format = new DecimalFormat("000000000");

   //it's being guarded by lock.writeLock().lock() and never read concurrently
   private int currentPageSize = 0;

   private final SimpleString storeName;

   // The FileFactory is created lazily as soon as the first write is attempted
   private volatile SequentialFileFactory fileFactory;

   private final PagingStoreFactory storeFactory;

   // Used to schedule sync threads
   private final PageSyncTimer syncTimer;

   private long maxSize;

   private int pageSize;

   private volatile AddressFullMessagePolicy addressFullMessagePolicy;

   private boolean printedDropMessagesWarning;

   private final PagingManager pagingManager;

   private final boolean usingGlobalMaxSize;

   private final ArtemisExecutor executor;

   // Bytes consumed by the queue on the memory
   private final AtomicLong sizeInBytes = new AtomicLong();

   private int numberOfPages;

   private int firstPageId;

   private volatile int currentPageId;

   private volatile Page currentPage;

   private volatile boolean paging = false;

   private final PageCursorProvider cursorProvider;

   private final ReadWriteLock lock = new ReentrantReadWriteLock();

   private volatile boolean running = false;

   private final boolean syncNonTransactional;

   private volatile boolean blocking = false;

   private long rejectThreshold;

   public PagingStoreImpl(final SimpleString address,
                          final ScheduledExecutorService scheduledExecutor,
                          final long syncTimeout,
                          final PagingManager pagingManager,
                          final StorageManager storageManager,
                          final SequentialFileFactory fileFactory,
                          final PagingStoreFactory storeFactory,
                          final SimpleString storeName,
                          final AddressSettings addressSettings,
                          final ArtemisExecutor executor,
                          final boolean syncNonTransactional) {
      this(address, scheduledExecutor, syncTimeout, pagingManager, storageManager, fileFactory, storeFactory, storeName, addressSettings, executor, executor, syncNonTransactional);
   }

   public PagingStoreImpl(final SimpleString address,
                          final ScheduledExecutorService scheduledExecutor,
                          final long syncTimeout,
                          final PagingManager pagingManager,
                          final StorageManager storageManager,
                          final SequentialFileFactory fileFactory,
                          final PagingStoreFactory storeFactory,
                          final SimpleString storeName,
                          final AddressSettings addressSettings,
                          final ArtemisExecutor executor,
                          final ArtemisExecutor ioExecutor,
                          final boolean syncNonTransactional) {
      if (pagingManager == null) {
         throw new IllegalStateException("Paging Manager can't be null");
      }

      this.address = address;

      this.storageManager = storageManager;

      this.storeName = storeName;

      applySetting(addressSettings);

      if (addressFullMessagePolicy == AddressFullMessagePolicy.PAGE && maxSize != -1 && pageSize >= maxSize) {
         throw new IllegalStateException("pageSize for address " + address +
                                            " >= maxSize. Normally pageSize should" +
                                            " be significantly smaller than maxSize, ms: " +
                                            maxSize +
                                            " ps " +
                                            pageSize);
      }

      this.executor = executor;

      this.pagingManager = pagingManager;

      this.fileFactory = fileFactory;

      this.storeFactory = storeFactory;

      this.syncNonTransactional = syncNonTransactional;

      if (scheduledExecutor != null && syncTimeout > 0) {
         this.syncTimer = new PageSyncTimer(this, scheduledExecutor, ioExecutor, syncTimeout);
      } else {
         this.syncTimer = null;
      }

      this.cursorProvider = storeFactory.newCursorProvider(this, this.storageManager, addressSettings, executor);

      this.usingGlobalMaxSize = pagingManager.isUsingGlobalSize();
   }

   /**
    * @param addressSettings
    */
   @Override
   public void applySetting(final AddressSettings addressSettings) {
      maxSize = addressSettings.getMaxSizeBytes();

      pageSize = addressSettings.getPageSizeBytes();

      addressFullMessagePolicy = addressSettings.getAddressFullMessagePolicy();

      rejectThreshold = addressSettings.getMaxSizeBytesRejectThreshold();

      if (cursorProvider != null) {
         cursorProvider.setCacheMaxSize(addressSettings.getPageCacheMaxSize());
      }
   }

   @Override
   public String toString() {
      return "PagingStoreImpl(" + this.address + ")";
   }

   @Override
   public boolean lock(long timeout) {
      if (timeout == -1) {
         lock.writeLock().lock();
         return true;
      }
      try {
         return lock.writeLock().tryLock(timeout, TimeUnit.MILLISECONDS);
      } catch (InterruptedException e) {
         return false;
      }
   }

   @Override
   public void unlock() {
      lock.writeLock().unlock();
   }

   @Override
   public PageCursorProvider getCursorProvider() {
      return cursorProvider;
   }

   @Override
   public long getFirstPage() {
      return firstPageId;
   }

   @Override
   public SimpleString getAddress() {
      return address;
   }

   @Override
   public long getAddressSize() {
      return sizeInBytes.get();
   }

   @Override
   public long getMaxSize() {
      if (maxSize < 0) {
         // if maxSize < 0, we will return 2 pages for depage purposes
         return pageSize * 2;
      } else {
         return maxSize;
      }
   }

   @Override
   public AddressFullMessagePolicy getAddressFullMessagePolicy() {
      return addressFullMessagePolicy;
   }

   @Override
   public int getPageSizeBytes() {
      return pageSize;
   }

   @Override
   public File getFolder() {
      final SequentialFileFactory factoryUsed = this.fileFactory;
      if (factoryUsed != null) {
         return factoryUsed.getDirectory();
      } else {
         return null;
      }
   }

   @Override
   public boolean isPaging() {
      AddressFullMessagePolicy policy = this.addressFullMessagePolicy;
      if (policy == AddressFullMessagePolicy.BLOCK) {
         return false;
      }
      if (policy == AddressFullMessagePolicy.FAIL) {
         return isFull();
      }
      if (policy == AddressFullMessagePolicy.DROP) {
         return isFull();
      }
      return paging;
   }

   @Override
   public int getNumberOfPages() {
      return numberOfPages;
   }

   @Override
   public int getCurrentWritingPage() {
      return currentPageId;
   }

   @Override
   public SimpleString getStoreName() {
      return storeName;
   }

   @Override
   public void sync() throws Exception {
      if (syncTimer != null) {
         syncTimer.addSync(storageManager.getContext());
      } else {
         ioSync();
      }

   }

   @Override
   public void ioSync() throws Exception {
      lock.readLock().lock();

      try {
         final Page page = currentPage;
         if (page != null) {
            page.sync();
         }
      } finally {
         lock.readLock().unlock();
      }
   }

   @Override
   public void processReload() throws Exception {
      cursorProvider.processReload();
   }

   @Override
   public PagingManager getPagingManager() {
      return pagingManager;
   }

   @Override
   public boolean isStarted() {
      return running;
   }

   @Override
   public synchronized void stop() throws Exception {
      if (running) {
         cursorProvider.flushExecutors();
         cursorProvider.stop();

         final List<Runnable> pendingTasks = new ArrayList<>();

         // TODO we could have a parameter to use this
         final int pendingTasksWhileShuttingDown = executor.shutdownNow(pendingTasks::add, 30, TimeUnit.SECONDS);
         if (pendingTasksWhileShuttingDown > 0) {
            logger.tracef("Try executing %d pending tasks on stop", pendingTasksWhileShuttingDown);
            for (Runnable pendingTask : pendingTasks) {
               try {
                  pendingTask.run();
               } catch (Throwable t) {
                  logger.warn("Error while executing a pending task on shutdown", t);
               }
            }
         }

         running = false;

         final Page page = currentPage;
         if (page != null) {
            page.close(false);
            currentPage = null;
         }
      }
   }

   @Override
   public void flushExecutors() {
      cursorProvider.flushExecutors();

      FutureLatch future = new FutureLatch();

      executor.execute(future);

      if (!future.await(60000)) {
         ActiveMQServerLogger.LOGGER.pageStoreTimeout(address);
      }
   }

   public int getNumberOfFiles() throws Exception {
      final SequentialFileFactory fileFactory = this.fileFactory;
      if (fileFactory != null) {
         List<String> files = fileFactory.listFiles("page");
         return files.size();
      }

      return 0;
   }

   @Override
   public void start() throws Exception {
      lock.writeLock().lock();

      try {

         if (running) {
            // don't throw an exception.
            // You could have two threads adding PagingStore to a
            // ConcurrentHashMap,
            // and having both threads calling init. One of the calls should just
            // need to be ignored
            return;
         } else {
            running = true;
            firstPageId = Integer.MAX_VALUE;

            // There are no files yet on this Storage. We will just return it empty
            final SequentialFileFactory fileFactory = this.fileFactory;
            if (fileFactory != null) {

               int pageId = 0;
               currentPageId = pageId;
               final Page oldPage = currentPage;
               if (oldPage != null) {
                  oldPage.close(false);
               }
               currentPage = null;

               List<String> files = fileFactory.listFiles("page");

               numberOfPages = files.size();

               for (String fileName : files) {
                  final int fileId = PagingStoreImpl.getPageIdFromFileName(fileName);

                  if (fileId > pageId) {
                     pageId = fileId;
                  }

                  if (fileId < firstPageId) {
                     firstPageId = fileId;
                  }
               }

               currentPageId = pageId;

               if (pageId != 0) {
                  reloadLivePage(pageId);
               }

               // We will not mark it for paging if there's only a single empty file
               final Page page = currentPage;
               if (page != null && !(numberOfPages == 1 && page.getSize() == 0)) {
                  startPaging();
               }
            }
         }

      } finally {
         lock.writeLock().unlock();
      }
   }

   protected void reloadLivePage(int pageId) throws Exception {
      Page page = createPage(pageId);
      page.open();

      final List<PagedMessage> messages = page.read(storageManager);

      final PagedMessage[] initialMessages = messages.toArray(new PagedMessage[messages.size()]);

      final LivePageCache pageCache = new LivePageCacheImpl(pageId, initialMessages);

      page.setLiveCache(pageCache);

      currentPageSize = page.getSize();

      currentPage = page;

      cursorProvider.addLivePageCache(pageCache);

      /**
       * The page file might be incomplete in the cases: 1) last message incomplete 2) disk damaged.
       * In case 1 we can keep writing the file. But in case 2 we'd better not bcs old data might be overwritten.
       * Here we open a new page so the incomplete page would be reserved for recovery if needed.
       */
      if (page.getSize() != page.getFile().size()) {
         openNewPage();
      }
   }

   @Override
   public void stopPaging() {
      lock.writeLock().lock();
      try {
         final boolean isPaging = this.paging;
         if (isPaging) {
            paging = false;
            ActiveMQServerLogger.LOGGER.pageStoreStop(storeName, sizeInBytes.get(), maxSize, pagingManager.getGlobalSize());
         }
         this.cursorProvider.onPageModeCleared();
      } finally {
         lock.writeLock().unlock();
      }
   }

   @Override
   public boolean startPaging() {
      if (!running) {
         return false;
      }

      lock.readLock().lock();
      try {
         // I'm not calling isPaging() here because i need to be atomic and hold a lock.
         if (paging) {
            return false;
         }
      } finally {
         lock.readLock().unlock();
      }

      // if the first check failed, we do it again under a global currentPageLock
      // (writeLock) this time
      lock.writeLock().lock();

      try {
         // Same notes from previous if (paging) on this method will apply here
         if (paging) {
            return false;
         }

         if (currentPage == null) {
            try {
               openNewPage();
            } catch (Exception e) {
               // If not possible to starting page due to an IO error, we will just consider it non paging.
               // This shouldn't happen anyway
               ActiveMQServerLogger.LOGGER.pageStoreStartIOError(e);
               return false;
            }
         }
         paging = true;
         ActiveMQServerLogger.LOGGER.pageStoreStart(storeName, sizeInBytes.get(), maxSize, pagingManager.getGlobalSize());

         return true;
      } finally {
         lock.writeLock().unlock();
      }
   }

   @Override
   public Page getCurrentPage() {
      return currentPage;
   }

   @Override
   public boolean checkPageFileExists(final int pageNumber) {
      String fileName = createFileName(pageNumber);

      SequentialFileFactory factory = null;
      try {
         factory = checkFileFactory();
         SequentialFile file = factory.createSequentialFile(fileName);
         return file.exists() && file.size() > 0;
      } catch (Exception ignored) {
         // never supposed to happen, but just in case
         logger.warn("PagingStoreFactory::checkPageFileExists never-throws assumption failed.", ignored);
         return true; // returning false would make the acks to the page to go missing.
                      // since we are not sure on the result for this case, we just return true
      }
   }

   @Override
   public Page createPage(final int pageNumber) throws Exception {
      String fileName = createFileName(pageNumber);

      SequentialFileFactory factory = checkFileFactory();

      SequentialFile file = factory.createSequentialFile(fileName);

      Page page = new Page(storeName, storageManager, factory, file, pageNumber);

      // To create the file
      file.open();

      file.position(0);

      file.close(false, false);

      return page;
   }

   private SequentialFileFactory checkFileFactory() throws Exception {
      SequentialFileFactory factory = fileFactory;
      if (factory == null) {
         factory = storeFactory.newFileFactory(getStoreName());
         fileFactory = factory;
      }
      return factory;
   }

   @Override
   public void forceAnotherPage() throws Exception {
      openNewPage();
   }

   /**
    * Returns a Page out of the Page System without reading it.
    * <p>
    * The method calling this method will remove the page and will start reading it outside of any
    * locks. This method could also replace the current file by a new file, and that process is done
    * through acquiring a writeLock on currentPageLock.
    * </p>
    * <p>
    * Observation: This method is used internally as part of the regular depage process, but
    * externally is used only on tests, and that's why this method is part of the Testable Interface
    * </p>
    */
   @Override
   public Page depage() throws Exception {
      lock.writeLock().lock(); // Make sure no checks are done on currentPage while we are depaging
      try {
         if (!running) {
            return null;
         }

         if (numberOfPages == 0) {
            return null;
         } else {
            numberOfPages--;

            final Page returnPage;

            // We are out of old pages, all that is left now is the current page.
            // On that case we need to replace it by a new empty page, and return the current page immediately
            if (currentPageId == firstPageId) {
               firstPageId = Integer.MAX_VALUE;

               if (currentPage == null) {
                  // sanity check... it shouldn't happen!
                  throw new IllegalStateException("CurrentPage is null");
               }

               returnPage = currentPage;
               returnPage.close(false);
               currentPage = null;

               // The current page is empty... which means we reached the end of the pages
               if (returnPage.getNumberOfMessages() == 0) {
                  stopPaging();
                  returnPage.open();
                  returnPage.delete(null);

                  // This will trigger this address to exit the page mode,
                  // and this will make ActiveMQ Artemis start using the journal again
                  return null;
               } else {
                  // We need to create a new page, as we can't lock the address until we finish depaging.
                  openNewPage();
               }

               return returnPage;
            } else {
               returnPage = createPage(firstPageId++);
            }

            return returnPage;
         }
      } finally {
         lock.writeLock().unlock();
      }

   }

   private final Queue<Runnable> onMemoryFreedRunnables = new ConcurrentLinkedQueue<>();

   private void memoryReleased() {
      Runnable runnable;

      while ((runnable = onMemoryFreedRunnables.poll()) != null) {
         runnable.run();
      }
   }

   @Override
   public boolean checkMemory(final Runnable runWhenAvailable) {
      return checkMemory(true, runWhenAvailable, null);
   }

   @Override
   public boolean checkMemory(boolean runOnFailure, final Runnable runWhenAvailable, Runnable runWhenBlocking) {

      if (addressFullMessagePolicy == AddressFullMessagePolicy.FAIL && (maxSize != -1 || usingGlobalMaxSize || pagingManager.isDiskFull())) {
         if (isFull()) {
            if (runOnFailure && runWhenAvailable != null) {
               onMemoryFreedRunnables.add(AtomicRunnable.checkAtomic(runWhenAvailable));
            }
            return false;
         }
      } else if (pagingManager.isDiskFull() || addressFullMessagePolicy == AddressFullMessagePolicy.BLOCK && (maxSize != -1 || usingGlobalMaxSize)) {
         if (pagingManager.isDiskFull() || maxSize > 0 && sizeInBytes.get() >= maxSize || pagingManager.isGlobalFull()) {
            if (runWhenBlocking != null) {
               runWhenBlocking.run();
            }

            onMemoryFreedRunnables.add(AtomicRunnable.checkAtomic(runWhenAvailable));

            // We check again to avoid a race condition where the size can come down just after the element
            // has been added, but the check to execute was done before the element was added
            // NOTE! We do not fix this race by locking the whole thing, doing this check provides
            // MUCH better performance in a highly concurrent environment
            if (!pagingManager.isGlobalFull() && (sizeInBytes.get() <= maxSize || maxSize < 0)) {
               // run it now
               runWhenAvailable.run();
            } else {
               if (usingGlobalMaxSize || pagingManager.isDiskFull()) {
                  pagingManager.addBlockedStore(this);
               }

               if (!blocking) {
                  if (pagingManager.isDiskFull()) {
                     ActiveMQServerLogger.LOGGER.blockingDiskFull(address);
                  } else {
                     ActiveMQServerLogger.LOGGER.blockingMessageProduction(address, sizeInBytes.get(), maxSize, pagingManager.getGlobalSize());
                  }
                  blocking = true;
               }
            }
            return true;
         }
      }

      if (runWhenAvailable != null) {
         runWhenAvailable.run();
      }

      return true;
   }

   @Override
   public void addSize(final int size) {
      boolean globalFull = pagingManager.addSize(size).isGlobalFull();
      long newSize = sizeInBytes.addAndGet(size);

      if (newSize < 0) {
         ActiveMQServerLogger.LOGGER.negativeAddressSize(newSize, address.toString());
      }

      if (addressFullMessagePolicy == AddressFullMessagePolicy.BLOCK || addressFullMessagePolicy == AddressFullMessagePolicy.FAIL) {
         if (usingGlobalMaxSize && !globalFull || maxSize != -1) {
            checkReleaseMemory(globalFull, newSize);
         }

         return;
      } else if (addressFullMessagePolicy == AddressFullMessagePolicy.PAGE) {
         if (size > 0) {
            if (maxSize != -1 && newSize > maxSize || globalFull) {
               startPaging();
            }
         }

         return;
      }
   }

   @Override
   public boolean checkReleasedMemory() {
      return checkReleaseMemory(pagingManager.isGlobalFull(), sizeInBytes.get());
   }

   public boolean checkReleaseMemory(boolean globalOversized, long newSize) {
      if (!globalOversized && (newSize <= maxSize || maxSize < 0)) {
         if (!onMemoryFreedRunnables.isEmpty()) {
            executor.execute(this::memoryReleased);
            if (blocking) {
               ActiveMQServerLogger.LOGGER.unblockingMessageProduction(address, sizeInBytes.get(), maxSize);
               blocking = false;
               return true;
            }
         }
      }

      return false;
   }

   @Override
   public boolean page(Message message,
                       final Transaction tx,
                       RouteContextList listCtx,
                       final ReadLock managerLock) throws Exception {

      if (!running) {
         return false;
      }

      boolean full = isFull();

      if (addressFullMessagePolicy == AddressFullMessagePolicy.DROP || addressFullMessagePolicy == AddressFullMessagePolicy.FAIL) {
         if (full) {
            if (!printedDropMessagesWarning) {
               printedDropMessagesWarning = true;

               ActiveMQServerLogger.LOGGER.pageStoreDropMessages(storeName, sizeInBytes.get(), maxSize, pagingManager.getGlobalSize());
            }

            if (message.isLargeMessage()) {
               ((LargeServerMessage) message).deleteFile();
            }

            if (addressFullMessagePolicy == AddressFullMessagePolicy.FAIL) {
               throw ActiveMQMessageBundle.BUNDLE.addressIsFull(address.toString());
            }

            // Address is full, we just pretend we are paging, and drop the data
            return true;
         } else {
            return false;
         }
      } else if (addressFullMessagePolicy == AddressFullMessagePolicy.BLOCK) {
         return false;
      }

      // We need to ensure a read lock, as depage could change the paging state
      lock.readLock().lock();

      try {
         // First check done concurrently, to avoid synchronization and increase throughput
         if (!paging) {
            return false;
         }
      } finally {
         lock.readLock().unlock();
      }

      if (managerLock != null) {
         managerLock.lock();
      }
      try {
         lock.writeLock().lock();

         try {
            if (!paging) {
               return false;
            }

            final long transactionID = tx == null ? -1 : tx.getID();
            PagedMessage pagedMessage = new PagedMessageImpl(message, routeQueues(tx, listCtx), transactionID);

            if (message.isLargeMessage()) {
               ((LargeServerMessage) message).setPaged();
            }

            int bytesToWrite = pagedMessage.getEncodeSize() + Page.SIZE_RECORD;

            currentPageSize += bytesToWrite;
            if (currentPageSize > pageSize && currentPage.getNumberOfMessages() > 0) {
               // Make sure nothing is currently validating or using currentPage
               openNewPage();
               currentPageSize += bytesToWrite;
            }

            if (tx != null) {
               installPageTransaction(tx, listCtx);
            }

            // the apply counter will make sure we write a record on journal
            // especially on the case for non transactional sends and paging
            // doing this will give us a possibility of recovering the page counters
            long persistentSize = pagedMessage.getPersistentSize() > 0 ? pagedMessage.getPersistentSize() : 0;
            final Page page = currentPage;
            applyPageCounters(tx, page, listCtx, persistentSize);

            page.write(pagedMessage);

            if (tx == null && syncNonTransactional && message.isDurable()) {
               sync();
            }

            if (logger.isTraceEnabled()) {
               logger.tracef("Paging message %s on pageStore %s pageNr=%d", pagedMessage, getStoreName(), page.getPageId());
            }

            return true;
         } finally {
            lock.writeLock().unlock();
         }
      } finally {
         if (managerLock != null) {
            managerLock.unlock();
         }
      }
   }

   /**
    * This method will disable cleanup of pages. No page will be deleted after this call.
    */
   @Override
   public void disableCleanup() {
      getCursorProvider().disableCleanup();
   }

   /**
    * This method will re-enable cleanup of pages. Notice that it will also start cleanup threads.
    */
   @Override
   public void enableCleanup() {
      getCursorProvider().resumeCleanup();
   }

   private long[] routeQueues(Transaction tx, RouteContextList ctx) throws Exception {
      List<org.apache.activemq.artemis.core.server.Queue> durableQueues = ctx.getDurableQueues();
      List<org.apache.activemq.artemis.core.server.Queue> nonDurableQueues = ctx.getNonDurableQueues();
      long[] ids = new long[durableQueues.size() + nonDurableQueues.size()];
      int i = 0;

      for (org.apache.activemq.artemis.core.server.Queue q : durableQueues) {
         q.getPageSubscription().notEmpty();
         ids[i++] = q.getID();
      }

      for (org.apache.activemq.artemis.core.server.Queue q : nonDurableQueues) {
         q.getPageSubscription().notEmpty();
         ids[i++] = q.getID();
      }
      return ids;
   }

   /**
    * This is done to prevent non tx to get out of sync in case of failures
    *
    * @param tx
    * @param page
    * @param ctx
    * @throws Exception
    */
   private void applyPageCounters(Transaction tx, Page page, RouteContextList ctx, long size) throws Exception {
      List<org.apache.activemq.artemis.core.server.Queue> durableQueues = ctx.getDurableQueues();
      List<org.apache.activemq.artemis.core.server.Queue> nonDurableQueues = ctx.getNonDurableQueues();
      for (org.apache.activemq.artemis.core.server.Queue q : durableQueues) {
         if (tx == null) {
            // non transactional writes need an intermediate place
            // to avoid the counter getting out of sync
            q.getPageSubscription().getCounter().pendingCounter(page, 1, size);
         } else {
            // null tx is treated through pending counters
            q.getPageSubscription().getCounter().increment(tx, 1, size);
         }
      }

      for (org.apache.activemq.artemis.core.server.Queue q : nonDurableQueues) {
         q.getPageSubscription().getCounter().increment(tx, 1, size);
      }

   }

   @Override
   public void durableDown(Message message, int durableCount) {
      refDown(message, durableCount);
   }

   @Override
   public void durableUp(Message message, int durableCount) {
      refUp(message, durableCount);
   }

   @Override
   public void refUp(Message message, int count) {
      this.addSize(MessageReferenceImpl.getMemoryEstimate());
   }

   @Override
   public void refDown(Message message, int count) {
      if (count < 0) {
         // this could happen on paged messages since they are not routed and refUp is never called
         return;
      }
      this.addSize(-MessageReferenceImpl.getMemoryEstimate());
   }

   private void installPageTransaction(final Transaction tx, final RouteContextList listCtx) throws Exception {
      FinishPageMessageOperation pgOper = (FinishPageMessageOperation) tx.getProperty(TransactionPropertyIndexes.PAGE_TRANSACTION);
      if (pgOper == null) {
         PageTransactionInfo pgTX = new PageTransactionInfoImpl(tx.getID());
         pagingManager.addTransaction(pgTX);
         pgOper = new FinishPageMessageOperation(pgTX, storageManager, pagingManager);
         tx.putProperty(TransactionPropertyIndexes.PAGE_TRANSACTION, pgOper);
         tx.addOperation(pgOper);
      }

      pgOper.addStore(this);
      pgOper.pageTransaction.increment(listCtx.getNumberOfDurableQueues(), listCtx.getNumberOfNonDurableQueues());

      return;
   }

   @Override
   public void destroy() throws Exception {
      SequentialFileFactory factory = fileFactory;
      if (factory != null) {
         storeFactory.removeFileFactory(factory);
      }
   }

   private static class FinishPageMessageOperation implements TransactionOperation {

      private final PageTransactionInfo pageTransaction;
      private final StorageManager storageManager;
      private final PagingManager pagingManager;
      private final Set<PagingStore> usedStores = new HashSet<>();

      private boolean stored = false;

      public void addStore(PagingStore store) {
         this.usedStores.add(store);
      }

      private FinishPageMessageOperation(final PageTransactionInfo pageTransaction,
                                         final StorageManager storageManager,
                                         final PagingManager pagingManager) {
         this.pageTransaction = pageTransaction;
         this.storageManager = storageManager;
         this.pagingManager = pagingManager;
      }

      @Override
      public void afterCommit(final Transaction tx) {
         // If part of the transaction goes to the queue, and part goes to paging, we can't let depage start for the
         // transaction until all the messages were added to the queue
         // or else we could deliver the messages out of order

         if (pageTransaction != null) {
            pageTransaction.commit();
         }
      }

      @Override
      public void afterPrepare(final Transaction tx) {
      }

      @Override
      public void afterRollback(final Transaction tx) {
         if (pageTransaction != null) {
            pageTransaction.rollback();
         }
      }

      @Override
      public void beforeCommit(final Transaction tx) throws Exception {
         syncStore();
         storePageTX(tx);
      }

      /**
       * @throws Exception
       */
      private void syncStore() throws Exception {
         for (PagingStore store : usedStores) {
            store.sync();
         }
      }

      @Override
      public void beforePrepare(final Transaction tx) throws Exception {
         syncStore();
         storePageTX(tx);
      }

      private void storePageTX(final Transaction tx) throws Exception {
         if (!stored) {
            tx.setContainsPersistent();
            pageTransaction.store(storageManager, pagingManager, tx);
            stored = true;
         }
      }

      @Override
      public void beforeRollback(final Transaction tx) throws Exception {
      }

      @Override
      public List<MessageReference> getRelatedMessageReferences() {
         return Collections.emptyList();
      }

      @Override
      public List<MessageReference> getListOnConsumer(long consumerID) {
         return Collections.emptyList();
      }

   }

   private void openNewPage() throws Exception {
      lock.writeLock().lock();

      try {
         numberOfPages++;

         final int newPageId = currentPageId + 1;

         if (logger.isTraceEnabled()) {
            logger.trace("new pageNr=" + newPageId, new Exception("trace"));
         }

         final Page oldPage = currentPage;
         if (oldPage != null) {
            oldPage.close(true);
         }

         final Page newPage = createPage(newPageId);

         currentPage = newPage;

         final LivePageCache pageCache = new LivePageCacheImpl(newPageId);

         newPage.setLiveCache(pageCache);

         cursorProvider.addLivePageCache(pageCache);

         currentPageSize = 0;

         newPage.open();

         currentPageId = newPageId;

         if (newPageId < firstPageId) {
            firstPageId = newPageId;
         }
      } finally {
         lock.writeLock().unlock();
      }
   }

   /**
    * @param pageID
    * @return
    */
   public String createFileName(final int pageID) {
      /** {@link DecimalFormat} is not thread safe. */
      synchronized (format) {
         return format.format(pageID) + ".page";
      }
   }

   private static int getPageIdFromFileName(final String fileName) {
      return Integer.parseInt(fileName.substring(0, fileName.indexOf('.')));
   }

   // To be used on isDropMessagesWhenFull
   @Override
   public boolean isFull() {
      return maxSize > 0 && getAddressSize() > maxSize || pagingManager.isGlobalFull();
   }

   @Override
   public boolean isRejectingMessages() {
      if (addressFullMessagePolicy != AddressFullMessagePolicy.BLOCK) {
         return false;
      }
      return rejectThreshold != AddressSettings.DEFAULT_ADDRESS_REJECT_THRESHOLD && getAddressSize() > rejectThreshold;
   }

   @Override
   public Collection<Integer> getCurrentIds() throws Exception {
      lock.readLock().lock();
      try {
         List<Integer> ids = new ArrayList<>();
         SequentialFileFactory factory = fileFactory;
         if (factory != null) {
            for (String fileName : factory.listFiles("page")) {
               ids.add(getPageIdFromFileName(fileName));
            }
         }
         return ids;
      } finally {
         lock.readLock().unlock();
      }
   }

   @Override
   public void sendPages(ReplicationManager replicator, Collection<Integer> pageIds) throws Exception {
      final SequentialFileFactory factory = fileFactory;
      for (Integer id : pageIds) {
         SequentialFile sFile = factory.createSequentialFile(createFileName(id));
         if (!sFile.exists()) {
            continue;
         }
         ActiveMQServerLogger.LOGGER.replicaSyncFile(sFile, sFile.size());
         replicator.syncPages(sFile, id, getAddress());
      }
   }

}
