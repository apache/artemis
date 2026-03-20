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
import java.lang.invoke.MethodHandles;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.paging.PageTransactionInfo;
import org.apache.activemq.artemis.core.paging.PagedMessage;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.paging.PagingStoreFactory;
import org.apache.activemq.artemis.core.paging.cursor.PageCursorProvider;
import org.apache.activemq.artemis.core.paging.cursor.PageSubscription;
import org.apache.activemq.artemis.core.persistence.OperationContext;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.OperationContextImpl;
import org.apache.activemq.artemis.core.replication.ReplicationManager;
import org.apache.activemq.artemis.core.server.ActiveMQMessageBundle;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.LargeServerMessage;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.RouteContextList;
import org.apache.activemq.artemis.core.server.StorageMessageReader;
import org.apache.activemq.artemis.core.server.impl.MessageReferenceImpl;
import org.apache.activemq.artemis.core.server.impl.PageStorageMessageReader;
import org.apache.activemq.artemis.core.server.impl.QueueImpl;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.settings.impl.DiskFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.PageFullMessagePolicy;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.core.transaction.TransactionOperation;
import org.apache.activemq.artemis.core.transaction.TransactionPropertyIndexes;
import org.apache.activemq.artemis.utils.ArtemisCloseable;
import org.apache.activemq.artemis.utils.SimpleFutureImpl;
import org.apache.activemq.artemis.utils.actors.ArtemisExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * File-based {@link org.apache.activemq.artemis.core.paging.PagingStore} implementation.
 * Creates {@link FilePage} instances for page storage.
 *
 * @see PagingStore
 */
public class PagingStoreImpl extends AddressSizeLimiter implements PagingStore {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final SimpleString address;

   private final DecimalFormat format = new DecimalFormat("000000000");

   private final PageCache usedPages = new PageCache(this);

   // This is updated and read by the Page's executor thread
   private long currentPageSize = 0;

   private final SimpleString storeName;

   // The FileFactory is created lazily as soon as the first write is attempted
   private volatile SequentialFileFactory fileFactory;

   private final PagingStoreFactory storeFactory;

   // this is used to batch and sync into paging asynchronously
   private PageTimedWriter timedWriter;

   private volatile boolean pageFull;

   private Long pageLimitBytes;

   private Long estimatedMaxPages;

   private Long pageLimitMessages;

   private PageFullMessagePolicy pageFullMessagePolicy;

   private int pageSize;

   private long numberOfPages;

   private long firstPageId;

   private volatile long currentPageId;

   private volatile Page currentPage;

   private final PageCursorProvider cursorProvider;

   private final boolean syncNonTransactional;

   private final Supplier<Boolean> purgePageFolder;

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
      this(address, scheduledExecutor, syncTimeout, pagingManager, storageManager, fileFactory, storeFactory, storeName, addressSettings, executor, syncNonTransactional, () -> false);
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
                          final boolean syncNonTransactional,
                          final Supplier<Boolean> purgePageFolder) {
      super(storageManager, pagingManager, address, addressSettings, executor);

      Objects.requireNonNull(scheduledExecutor, "scheduledExecutor = null");

      Objects.requireNonNull(pagingManager, "Paging Manager can't be null");

      this.address = address;

      this.storeName = storeName;

      this.fileFactory = fileFactory;

      this.purgePageFolder = purgePageFolder;

      this.storeFactory = storeFactory;

      this.syncNonTransactional = syncNonTransactional;

      this.timedWriter = createPageTimedWriter(scheduledExecutor, syncTimeout);

      this.cursorProvider = storeFactory.newCursorProvider(this, this.storageManager, addressSettings, executor);
   }

   // Extension point for unit tests to replace the creation of the PageTimedWriter
   protected PageTimedWriter createPageTimedWriter(ScheduledExecutorService scheduledExecutor, long syncTimeout) {
      Objects.requireNonNull(scheduledExecutor, "scheduledExecutor");
      Objects.requireNonNull(getExecutor(), "executor");
      PageTimedWriter localWriter = new PageTimedWriter(getPageSizeBytes(), getStorageManager(), this, scheduledExecutor, getExecutor(), syncNonTransactional, syncTimeout);
      localWriter.start();
      return localWriter;
   }

   // for tests, used through an accessor
   protected void replacePagedTimedWriter(PageTimedWriter writer) {
      this.timedWriter = writer;
   }

   public PageTimedWriter getPageTimedWriter() {
      return timedWriter;
   }

   @Override
   protected void applySetting(final AddressSettings addressSettings, final boolean firstTime) {
      super.applySetting(addressSettings, firstTime);

      // JDBC has a maximum page size of 100K by default.
      // it can be reconfigured through jdbc-max-page-size-bytes in the JDBC configuration section
      pageSize = storageManager.getAllowedPageSize(addressSettings.getPageSizeBytes());

      pageFullMessagePolicy = addressSettings.getPageFullMessagePolicy();
      pageLimitBytes = addressSettings.getPageLimitBytes();

      if (pageLimitBytes != null && pageLimitBytes < 0) {
         logger.debug("address {} had pageLimitBytes<0, setting it as null", address);
         pageLimitBytes = null;
      }

      Long originalLimitMessages = this.pageLimitMessages;
      this.pageLimitMessages = addressSettings.getPageLimitMessages();

      if (pageLimitMessages != null && pageLimitMessages < 0) {
         logger.debug("address {} had pageLimitMessages<0, setting it as null", address);
         pageLimitMessages = null;
      }

      if (pageLimitBytes == null && pageLimitMessages == null && pageFullMessagePolicy != null) {
         ActiveMQServerLogger.LOGGER.noPageLimitsSet(address, pageFullMessagePolicy);
         this.pageFullMessagePolicy = null;
      }

      if (pageFullMessagePolicy == null) {
         if (pageLimitBytes != null || pageLimitMessages != null) {
            ActiveMQServerLogger.LOGGER.noPagefullPolicySet(address, pageLimitBytes, pageLimitMessages);
         }
         this.pageFullMessagePolicy = null;
         this.pageLimitMessages = null;
         this.pageLimitBytes = null;
      }

      boolean pageLimitMessagesChanged = !Objects.equals(this.pageLimitMessages, originalLimitMessages);
      boolean estimatedMaxPagesChanged = false;

      if (pageLimitBytes != null && pageSize > 0) {
         Long originalEstimatedMaxPages = this.estimatedMaxPages;
         estimatedMaxPages = pageLimitBytes / pageSize;
         logger.debug("Address {} should not allow more than {} pages", storeName, estimatedMaxPages);
         estimatedMaxPagesChanged = !Objects.equals(estimatedMaxPages, originalEstimatedMaxPages);
      }

      if (!firstTime && (estimatedMaxPagesChanged || pageLimitMessagesChanged)) {
         if (estimatedMaxPagesChanged) {
            checkNumberOfPages();
         }
         final PageCursorProvider provider = getCursorProvider();
         if (provider != null) {
            provider.checkClearPageLimit();
         }
      }
   }

   @Override
   public String toString() {
      return getClass().getSimpleName() + "(" + this.address + ")";
   }

   @Override
   public PageFullMessagePolicy getPageFullMessagePolicy() {
      return pageFullMessagePolicy;
   }

   @Override
   public Long getPageLimitMessages() {
      return pageLimitMessages;
   }

   @Override
   public Long getPageLimitBytes() {
      return pageLimitBytes;
   }

   @Override
   public void pageFull(PageSubscription subscription) {
      this.pageFull = true;
      try {
         ActiveMQServerLogger.LOGGER.pageFull(subscription.getQueue().getName(), subscription.getQueue().getAddress(), pageLimitMessages, subscription.getCounter().getValue());
      } catch (Throwable e) {
         // I don't think subscription would ever have a null queue. I'm being cautious here for tests
         logger.warn(e.getMessage(), e);
      }
   }

   @Override
   public boolean isPageFull() {
      return pageFull;
   }

   private boolean isBelowPageLimitBytes() {
      if (estimatedMaxPages != null) {
         return (numberOfPages <= estimatedMaxPages.longValue());
      } else {
         return true;
      }
   }

   private void checkNumberOfPages() {
      if (!isBelowPageLimitBytes()) {
         this.pageFull = true;
         ActiveMQServerLogger.LOGGER.pageFullMaxBytes(storeName, numberOfPages, estimatedMaxPages, pageLimitBytes, pageSize);
      }
   }

   @Override
   public void checkPageLimit(long numberOfMessages) {
      boolean pageMessageMessagesClear = true;
      Long pageLimitMessages = getPageLimitMessages();

      if (pageLimitMessages != null) {
         if (logger.isDebugEnabled()) { // gate to avoid boxing of numberOfMessages
            logger.debug("Address {} has {} messages on the larger queue", storeName, numberOfMessages);
         }

         pageMessageMessagesClear = (numberOfMessages < pageLimitMessages.longValue());
      }

      boolean pageMessageBytesClear = isBelowPageLimitBytes();

      if (pageMessageBytesClear && pageMessageMessagesClear) {
         pageLimitReleased();
      }
   }

   private void pageLimitReleased() {
      if (pageFull) {
         ActiveMQServerLogger.LOGGER.pageFree(getAddress());
         this.pageFull = false;
      }
   }

   @Override
   public PageCursorProvider getCursorProvider() {
      return cursorProvider;
   }

   @Override
   public StorageMessageReader createStorageMessageReader(QueueImpl queue) {
      return new PageStorageMessageReader(queue);
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
   public long getMaxSize() {
      long maxSize = super.getMaxSize();
      if (maxSize <= 0) {
         // if maxSize <= 0, we will return 2 pages for de-page purposes
         return pageSize * 2L;
      } else {
         return maxSize;
      }
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
   public String getFolderName() {
      return fileFactory.getDirectoryName();
   }

   @Override
   public long getNumberOfPages() {
      return numberOfPages;
   }

   @Override
   public long getCurrentWritingPage() {
      return currentPageId;
   }

   @Override
   public SimpleString getStoreName() {
      return storeName;
   }

   @Override
   public void ioSync() throws Exception {
      if (!fileFactory.supportsIndividualContext()) {
         Page page = getCurrentPage();
         if (page != null) {
            page.trySync();
         }
      }
   }

   @Override
   public void processReload() throws Exception {
      final PageCursorProvider provider = getCursorProvider();
      if (provider != null) {
         provider.processReload();
      }
   }

   @Override
   public void counterSnapshot() {
      final PageCursorProvider provider = getCursorProvider();
      if (provider != null) {
         provider.counterSnapshot();
      }
   }

   @Override
   public void stop() throws Exception {
      synchronized (this) {
         if (running) {
            if (timedWriter != null) {
               timedWriter.stop();
            }
            final PageCursorProvider provider = getCursorProvider();
            if (provider != null) {
               provider.stop();
            }
            running = false;
         } else {
            return;
         }
      }

      final List<Runnable> pendingTasks = new ArrayList<>();

      final Page page = currentPage;
      if (page != null) {
         page.close(true);
         currentPage = null;
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
      writeLock();

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
            firstPageId = Long.MAX_VALUE;

            // There are no files yet on this Storage. We will just return it empty
            final SequentialFileFactory fileFactory = this.fileFactory;
            if (fileFactory != null) {

               int pageId = 0;
               currentPageId = pageId;
               assert currentPage == null;
               currentPage = null;

               List<String> files = fileFactory.listFiles("page");

               numberOfPages = files.size();

               checkNumberOfPages();

               for (String fileName : files) {
                  final int fileId = getPageIdFromFileName(fileName);

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

               if (timedWriter != null) {
                  timedWriter.start();
               }
            }
         }

      } finally {
         writeUnlock();
      }
   }

   protected void reloadLivePage(long pageId) throws Exception {
      Page page = newPageObject(pageId);
      page.open(true);

      currentPageSize = page.getSize();

      page.getMessages();

      resetCurrentPage(page);

      /*
       * The page file might be incomplete in the cases: 1) last message incomplete 2) disk damaged. In case 1 we can
       * keep writing the file. But in case 2 we'd better not bcs old data might be overwritten. Here we open a new page
       * so the incomplete page would be reserved for recovery if needed.
       */
      if (page.getSize() != page.storageSize()) {
         openNewPage();
      }
   }

   private void resetCurrentPage(Page newCurrentPage) {

      Page theCurrentPage = this.currentPage;

      if (theCurrentPage != null) {
         theCurrentPage.usageDown();
      }

      if (newCurrentPage != null) {
         newCurrentPage.usageUp();
         injectPage(newCurrentPage);
      }

      this.currentPage = newCurrentPage;
   }

   @Override
   public void stopPaging() {
      logger.debug("stopPaging being called, while isPaging={} on {}", this.paging, this.storeName);
      writeLock();
      try {
         final boolean isPaging = this.paging;
         if (isPaging) {
            assert !(timedWriter != null && timedWriter.hasPendingIO()) : "There is pending IO on PagingStoreImpl while calling stopPaging";
            if (timedWriter != null && timedWriter.hasPendingIO()) {
               // it should not happen, however if it happened we will just ignore the call for stopPaging
               logger.debug("There are pending timed writes. Cannot clear paging now.");
               return;
            }
            paging = false;
            ActiveMQServerLogger.LOGGER.pageStoreStop(storeName, getInfo());
            pageLimitReleased();
         }
         final PageCursorProvider provider = getCursorProvider();
         if (provider != null) {
            provider.onPageModeCleared();
         }
         if (purgePageFolder.get()) {
            execute(this::purgeFolder);
         }
      } finally {
         writeUnlock();
      }
   }

   @Override
   public void purgeFolder() {
      try (ArtemisCloseable readLock = storageManager.closeableReadLock()) {
         writeLock();
         try {
            if (!isStorePaging() && !hasPendingIO() && hasStorage()) {
               ActiveMQServerLogger.LOGGER.purgingPageFolder(getFolderName(), storeName);
               // closing used pages...
               // all files need to be closed before we can remove a folder
               usedPages.forEachUsedPage(this::closePage);
               usedPages.clear();
               closePage(currentPage);
               currentPage = null;
               numberOfPages = 0;
               if (deleteFolder()) {
                  final PageCursorProvider provider = getCursorProvider();
                  if (provider != null) {
                     provider.forEachSubscription(PageSubscription::deleteCursorInfo);
                  }
               }
            }
         } finally {
            writeUnlock();
         }
      }
   }

   private void closePage(Page p) {
      try {
         if (p != null) {
            p.close(true);
         }
      } catch (Exception e) {
         logger.warn(e.getMessage(), e);
      }
   }

   @Override
   protected boolean beginPage() {
      try {
         if (currentPage == null) {
            openNewPage();
         } else {
            if (!currentPage.storageExists() || !currentPage.isOpen()) {
               currentPage.open(false);
            }
         }
      } catch (Exception e) {
         // If not possible to starting page due to an IO error, we will just consider it non paging.
         // This shouldn't happen anyway
         ActiveMQServerLogger.LOGGER.pageStoreStartIOError(e);
         storageManager.criticalError(e);
         return false;
      }
      return true;
   }

   @Override
   public Page getCurrentPage() {
      return currentPage;
   }

   @Override
   public boolean checkPageFileExists(final long pageNumber) {
      if (fileFactory == null) {
         return false;
      }
      String fileName = createFileName(pageNumber);

      try {
         SequentialFileFactory factory = checkFileFactory();
         SequentialFile file = factory.createSequentialFile(fileName);
         return file.exists() && file.size() > 0;
      } catch (Exception ignored) {
         logger.warn("PagingStoreFactory::checkPageFileExists never-throws assumption failed.", ignored);
         return true;
      }
   }

   @Override
   public Page newPageObject(final long pageNumber) throws Exception {
      String fileName = createFileName(pageNumber);

      SequentialFileFactory factory = checkFileFactory();

      SequentialFile file = factory.createSequentialFile(fileName);

      return new FilePage(getStoreName(), getStorageManager(), factory, file, pageNumber);
   }

   @Override
   public final Page usePage(final long pageId) {
      return usePage(pageId, true);
   }

   @Override
   public Page usePage(final long pageId, final boolean create) {
      return usePage(pageId, create, create);
   }

   @Override
   public Page usePage(final long pageId, final boolean createEntry, final boolean createFile) {
      if (!hasStorage()) {
         return null;
      }
      synchronized (usedPages) {
         try {
            Page page = usedPages.get(pageId);
            if (createEntry && page == null) {
               page = newPageObject(pageId);
               if (page.storageExists()) {
                  page.getMessages();
                  injectPage(page);
               } else {
                  if (!createFile) {
                     page = null;
                  }
               }
            }
            if (page != null) {
               page.usageUp();
            }
            return page;
         } catch (Exception e) {
            logger.warn(e.getMessage(), e);
            if (fileFactory != null) {
               SequentialFile file = fileFactory.createSequentialFile(createFileName(pageId));
               fileFactory.onIOError(e, e.getMessage(), file);
            }
            // in most cases this exception will not happen since the onIOError should halt the VM
            // it could eventually happen in tests though
            throw new RuntimeException(e.getMessage(), e);
         }
      }
   }

   protected SequentialFileFactory getFileFactory() throws Exception {
      checkFileFactory();
      return fileFactory;
   }

   protected boolean deleteFolder() {
      SequentialFileFactory sequentialFileFactory = fileFactory;
      try {
         if (sequentialFileFactory != null) {
            List<String> files;
            try {
               files = sequentialFileFactory.listFiles(null);
            } catch (Exception e) {
               sequentialFileFactory.onIOError(e, e.getMessage());
               return false;
            }
            files.forEach(f -> {
               SequentialFile file = sequentialFileFactory.createSequentialFile(f);
               try {
                  logger.debug("Deleting {}", file);
                  file.delete();
               } catch (Exception e) {
                  logger.warn(e.getMessage(), e);
                  sequentialFileFactory.onIOError(e, e.getMessage(), file.getFileName());
               }
            });
            logger.debug("Deleting directory {}", sequentialFileFactory.getDirectory());
            return deleteFolder(sequentialFileFactory);
         }
         return true;
      } finally {
         this.fileFactory = null;
      }
   }

   private boolean deleteFolder(final SequentialFileFactory deletingFolder) {
      if (!deletingFolder.deleteFolder()) {
         ActiveMQServerLogger.LOGGER.failedPurgingFolder(deletingFolder.getDirectory().getAbsolutePath());
         try {
            List<String> filesStillExisting = deletingFolder.listFiles(null);
            filesStillExisting.forEach(f -> logger.info("File {} still on folder {}", f, deletingFolder.getDirectory().getAbsolutePath()));
         } catch (Exception e) {
            logger.warn(e.getMessage(), e);
         }
         return false;
      } else {
         return true;
      }
   }

   private SequentialFileFactory checkFileFactory() throws Exception {
      SequentialFileFactory factory = fileFactory;
      if (factory == null) {
         factory = getStoreFactory().newFileFactory(getStoreName());
         fileFactory = factory;
      }
      return factory;
   }

   @Override
   public void forceAnotherPage() throws Exception {
      forceAnotherPage(false);
   }

   @Override
   public void forceAnotherPage(boolean useExecutor) throws Exception {
      // we need to open a new page inside the executor
      // as the PageTimedWriter will write on the currentPage without holding a writeLock
      // the current page must be changed within the executor's
      if (useExecutor) {
         SimpleFutureImpl future = new SimpleFutureImpl();
         execute(() -> {
            try {
               openNewPage();
               future.set(true);
            } catch (Exception e) {
               future.fail(e);
            }
         });
         future.get();
      } else {
         openNewPage();
      }
   }

   /**
    * Returns a Page out of the Page System without reading it.
    * <p>
    * The method calling this method will remove the page and will start reading it outside of any locks. This method
    * could also replace the current file by a new file, and that process is done through acquiring a writeLock on
    * currentPageLock.
    * <p>
    * Observation: This method is used internally as part of the regular depage process, but externally is used only on
    * tests, and that's why this method is part of the Testable Interface
    */
   @Override
   public Page removePage(int pageId) {
      try {
         if (!running) {
            return null;
         }

         if (currentPageId == pageId) {
            if (logger.isDebugEnabled()) {
               logger.debug("Ignoring remove({}) as this is the current writing page", pageId);
            }
            // we don't deal with the current page, we let that one to be cleared from the regular depage
            return null;
         }

         Page page = usePage(pageId, false);

         if (page == null) {
            page = newPageObject(pageId);
         }

         if (page != null && page.storageExists()) {
            page.usageDown();
            // we only decrement numberOfPages if the file existed
            // it could have been removed by a previous delete
            // on this case we just need to ignore this and move on
            numberOfPages--;
         }

         if (logger.isTraceEnabled()) {
            logger.trace("Removing page {}, now containing numberOfPages={}", pageId, numberOfPages);
         }

         if (numberOfPages == 0) {
            if (logger.isTraceEnabled()) {
               logger.trace("Page has no pages after removing last page {}", pageId, new Exception("Trace"));
            }
         }

         assert numberOfPages >= 0 : "numberOfPages should never be negative. on removePage(" + pageId + "). numberOfPages=" + numberOfPages;

         return page;
      } catch (Throwable e) {
         logger.warn(e.getMessage(), e);
         if (e instanceof AssertionError error) {
            // this will give a chance to callers log an AssertionError if assertion flag is enabled
            throw error;
         }
         storageManager.criticalError(e);
         return null;
      }
   }

   /**
    * Returns a Page out of the Page System without reading it.
    * <p>
    * The method calling this method will remove the page and will start reading it outside of any locks. This method
    * could also replace the current file by a new file, and that process is done through acquiring a writeLock on
    * currentPageLock.
    * </p>
    * <p>
    * Observation: This method is used internally as part of the regular depage process, but externally is used only on
    * tests, and that's why this method is part of the Testable Interface
    */
   @Override
   public Page depage() throws Exception {
      if (!running) {
         return null;
      }

      if (numberOfPages == 0) {
         return null;
      } else {
         final Page returnPage;

         numberOfPages--;

         // We are out of old pages, all that is left now is the current page.
         // On that case we need to replace it by a new empty page, and return the current page immediately
         if (currentPageId == firstPageId) {
            firstPageId = Integer.MAX_VALUE;
            logger.trace("Setting up firstPageID=MAX_VALUE");

            if (currentPage == null) {
               // sanity check... it shouldn't happen!
               throw new IllegalStateException("CurrentPage is null");
            }

            returnPage = currentPage;
            returnPage.close(true);
            resetCurrentPage(null);

            // The current page is empty... which means we reached the end of the pages
            if (returnPage.getNumberOfMessages() == 0 && !hasPendingIO()) {
               stopPaging();
               returnPage.open(true);
               returnPage.delete(null);

               // This will trigger this address to exit the page mode,
               // and this will make Apache Artemis start using the journal again
               return null;
            } else {
               // We need to create a new page, as we can't lock the address until we finish depaging.
               openNewPage();
            }
         } else {
            if (logger.isTraceEnabled()) {
               logger.trace("firstPageId++ = beforeIncrement={}", firstPageId);
            }
            long pageNR = firstPageId++;

            // first we look for the page on the used Pages cache
            // if non existing, we just create a new one outside of the cache
            // as we should not introduce any extras
            Page usedPage = usePage(pageNR, false);
            if (usedPage == null) {
               returnPage = newPageObject(pageNR);
            } else {
               returnPage = usedPage;
            }
         }

         if (!returnPage.storageExists()) {
            // if the file does not exist, we will just increment back to where it was before
            numberOfPages++;
         }

         // we make this assertion after checking the file existed before.
         // this could be eventually negative for a short period of time
         // but after compensating the non existent file the assertion should still hold true
         assert numberOfPages >= 0 : "numberOfPages should never be negative. on depage(). currentPageId=" + currentPageId + ", firstPageId=" + firstPageId + "";

         return returnPage;
      }
   }

   @Override
   public boolean page(Message message, final Transaction tx, RouteContextList listCtx) throws Exception {
      return page(message, tx, listCtx, null) >= 0;
   }

   @Override
   public int page(Message message,
                   final Transaction tx,
                   RouteContextList listCtx,
                   Function<Message, Message> pageDecorator) throws Exception {

      if (!running) {
         return -1;
      }

      Integer policiesResult = checkFullPolicies(message);
      if (policiesResult != null) {
         return policiesResult;
      }

      return writePage(message, tx, listCtx, pageDecorator);
   }

   /**
    * Extends the base address-full check with disk-full and page-full checks.
    * {@inheritDoc}
    */
   @Override
   protected Integer checkFullPolicies(Message message) throws Exception {
      Integer result = super.checkFullPolicies(message);
      if (result != null) {
         return result;
      }
      if ((result = validateDiskFull(message)) != null) {
         return result;
      }
      if ((result = validatePageFull(message)) != null) {
         return result;
      }
      return null;
   }


   /**
    * Checks whether the page is full and applies the configured {@link PageFullMessagePolicy}.
    *
    * @return {@code null} if the page is not full and processing should continue;
    *         {@code 0} if the message was dropped (caller must return this value);
    *         throws {@link org.apache.activemq.artemis.api.core.ActiveMQAddressFullException}
    *         if the policy is {@link PageFullMessagePolicy#FAIL}.
    */
   protected Integer validatePageFull(Message message) throws Exception {
      if (pageFull) {
         if (message.isLargeMessage()) {
            ((LargeServerMessage) message).deleteFile();
         }

         if (pageFullMessagePolicy == PageFullMessagePolicy.FAIL) {
            throw ActiveMQMessageBundle.BUNDLE.addressIsFull(address);
         }

         if (!printedDropMessagesWarning) {
            printedDropMessagesWarning = true;
            ActiveMQServerLogger.LOGGER.pageStoreDropMessages(storeName, getInfo());
         }

         // we are in page mode, if we got to this point, we are dropping the message while still paging
         // we return 0 as in the storage is in "page mode" however no credits are being taken.
         return 0;
      }
      return null;
   }


   /**
    * Checks whether the disk is full and applies the configured {@link DiskFullMessagePolicy}.
    *
    * @return {@code null} if the disk is not full and processing should continue;
    *         {@code 0} if the message was dropped (caller must return this value);
    *         throws {@link org.apache.activemq.artemis.api.core.ActiveMQAddressFullException}
    *         if the policy is {@link DiskFullMessagePolicy#FAIL}.
    */
   protected Integer validateDiskFull(Message message) throws Exception {
      if (diskFullMessagePolicy == DiskFullMessagePolicy.DROP || diskFullMessagePolicy == DiskFullMessagePolicy.FAIL) {
         if (pagingManager.isDiskFull()) {
            message.setDropped(true);

            if (message.isLargeMessage()) {
               ((LargeServerMessage) message).deleteFile();
            }

            if (diskFullMessagePolicy == DiskFullMessagePolicy.FAIL) {
               throw ActiveMQMessageBundle.BUNDLE.addressIsFull(address);
            }

            // Disk is full, just drop the data
            if (!printedDropMessagesWarning) {
               printedDropMessagesWarning = true;
               ActiveMQServerLogger.LOGGER.pageStoreDropMessages(storeName, getInfo());
            }

            return 0;
         }
      }
      return null;
   }


   protected int writePage(Message message,
                           Transaction tx,
                           RouteContextList listCtx,
                           Function<Message, Message> pageDecorator) throws Exception {
      readLock();
      PagedMessage pagedMessage;
      try {
         if (!isStorePaging()) {
            return -1;
         }

         final long transactionID = (tx != null && tx.isAllowPageTransaction()) ? tx.getID() : -1L;

         if (pageDecorator != null) {
            message = pageDecorator.apply(message);
         }

         message.setPaged();

         pagedMessage = new PagedMessageImpl(message, routeQueues(tx, listCtx), transactionID);
         if (tx != null) {
            pagedMessage.setStorageTX(tx.getStorageTx());
         }
         long persistentSize = pagedMessage.getPersistentSize() > 0 ? pagedMessage.getPersistentSize() : 0;

         if (tx != null && tx.isAllowPageTransaction()) {
            installPageTransaction(tx, listCtx);
         }

         timedWriter.incrementTask();

         applyPageCounters(tx, listCtx, persistentSize);

      } finally {
         readUnlock();
      }

      int credits = timedWriter.addTask(getStorageManager().getContext(), pagedMessage, tx, listCtx);

      assert credits >= 0;

      return credits;
   }

   @Override
   public boolean flowControl(Runnable block, Runnable release) {
      if (timedWriter != null) {
         return timedWriter.flowControl(block, release);
      } else {
         return false;
      }
   }

   protected void directWritePage(PagedMessage pagedMessage,
                                  boolean lineUp,
                                  boolean originalReplicated) throws Exception {
      int bytesToWrite = pagedMessage.getEncodeSize() + PageReadWriter.SIZE_RECORD;

      currentPageSize += bytesToWrite;
      if (currentPage == null || currentPageSize > pageSize && currentPage.getNumberOfMessages() > 0) {
         // Make sure nothing is currently validating or using currentPage
         openNewPage();
         currentPageSize += bytesToWrite;
      }

      // the apply counter will make sure we write a record on journal
      // especially on the case for non transactional sends and paging
      // doing this will give us a possibility of recovering the page counters
      final Page page = currentPage;

      if (!page.isOpen()) {
         page.open(false);
      }

      page.write(pagedMessage, lineUp, originalReplicated);

      if (logger.isTraceEnabled()) {
         logger.trace("Paging message {} on pageStore {} pageNr={}", pagedMessage, getStoreName(), page.getPageId());
      }
   }

   /**
    * This method will disable cleanup of pages. No page will be deleted after this call.
    */
   @Override
   public void disableCleanup() {
      final PageCursorProvider provider = getCursorProvider();
      if (provider != null) {
         provider.disableCleanup();
      }
   }

   /**
    * This method will re-enable cleanup of pages. Notice that it will also start cleanup threads.
    */
   @Override
   public void enableCleanup() {
      final PageCursorProvider provider = getCursorProvider();
      if (provider != null) {
         provider.resumeCleanup();
      }
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
    */
   private void applyPageCounters(Transaction tx, RouteContextList ctx, long size) throws Exception {
      List<org.apache.activemq.artemis.core.server.Queue> durableQueues = ctx.getDurableQueues();
      List<org.apache.activemq.artemis.core.server.Queue> nonDurableQueues = ctx.getNonDurableQueues();
      for (org.apache.activemq.artemis.core.server.Queue q : durableQueues) {
         q.getPageSubscription().getCounter().increment(tx, 1, size);
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
      this.addSize(MessageReferenceImpl.getMemoryEstimate(), true);
   }

   @Override
   public void refDown(Message message, int count) {
      if (count < 0) {
         // this could happen on paged messages since they are not routed and refUp is never called
         return;
      }
      this.addSize(-MessageReferenceImpl.getMemoryEstimate(), true);
   }

   private void installPageTransaction(final Transaction tx, final RouteContextList listCtx) throws Exception {
      FinishPageMessageOperation pgOper = (FinishPageMessageOperation) tx.getProperty(TransactionPropertyIndexes.PAGE_TRANSACTION);
      if (pgOper == null) {
         PageTransactionInfo pgTX = new PageTransactionInfoImpl(tx.getID());
         getPagingManager().addTransaction(pgTX);
         pgOper = new FinishPageMessageOperation(pgTX, storageManager, pagingManager);
         tx.putProperty(TransactionPropertyIndexes.PAGE_TRANSACTION, pgOper);
         tx.addOperation(pgOper);
      }

      if (!tx.isAsync()) {
         pgOper.addStore(this);
      }

      pgOper.pageTransaction.increment(listCtx.getNumberOfDurableQueues(), listCtx.getNumberOfNonDurableQueues());
   }

   @Override
   public boolean hasPendingIO() {
      return timedWriter != null && timedWriter.hasPendingIO();
   }

   @Override
   public void destroy() throws Exception {
      if (timedWriter != null) {
         timedWriter.stop();
      }
      // destroy has to be executed in the same executor as the cleanup
      execute(this::internalDestroy);
      OperationContext context = OperationContextImpl.getContext();
      if (context != null) {
         // this is to make clients to wait the delete completion of the storage
         context.storeLineUp();
         execute(context::done);
      }
   }

   private void internalDestroy() {
      try (ArtemisCloseable readLock = storageManager.closeableReadLock()) {
         writeLock();

         try {
            try {
               removeFromStoreFactory();
            } catch (Exception e) {
               logger.warn(e.getMessage(), e);
            }
         } finally {
            writeUnlock();
            try {
               stop();
            } catch (Exception e2) {
               logger.debug(e2.getMessage(), e2);
            }
         }
      }
   }

   private static class FinishPageMessageOperation implements TransactionOperation {

      private final PageTransactionInfo pageTransaction;
      private final StorageManager storageManager;
      private final PagingManager pagingManager;
      private final Set<PagingStore> usedStores = new HashSet<>();

      private boolean stored = false;

      public synchronized void addStore(PagingStore store) {
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
         storePageTX(tx);
      }

      @Override
      public void beforePrepare(final Transaction tx) throws Exception {
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
      numberOfPages++;

      checkNumberOfPages();

      final long newPageId = currentPageId + 1;

      if (logger.isTraceEnabled()) {
         logger.trace("destination {} new pageNr={}", storeName, newPageId);
      }

      final Page oldPage = currentPage;
      if (oldPage != null) {
         oldPage.close(true);
         oldPage.usageDown();
         currentPage = null;
      }

      final Page newPage = newPageObject(newPageId);

      resetCurrentPage(newPage);

      currentPageSize = 0;

      newPage.open(true);

      currentPageId = newPageId;

      if (newPageId < firstPageId) {
         logger.debug("open new page, setting firstPageId = {}, it was {} before", newPageId, firstPageId);
         firstPageId = newPageId;
      }
   }

   protected PagingStoreFactory getStoreFactory() {
      return storeFactory;
   }

   private void removeFromStoreFactory() {
      if (fileFactory != null) {
         try {
            getStoreFactory().removeFileFactory(fileFactory);
         } catch (Exception e) {
            logger.warn(e.getMessage(), e);
         }
      }
   }

   private boolean hasStorage() {
      return fileFactory != null;
   }

   public String createFileName(final long pageID) {
      synchronized (format) {
         return format.format(pageID) + ".page";
      }
   }

   private static int getPageIdFromFileName(final String fileName) {
      return Integer.parseInt(fileName.substring(0, fileName.indexOf('.')));
   }



   @Override
   public Collection<Integer> getCurrentIds() throws Exception {
      readLock();
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
         readUnlock();
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
         ActiveMQServerLogger.LOGGER.replicaSyncFile(sFile, sFile.size(), getStoreName());
         replicator.syncPages(sFile, id, getAddress());
      }
   }
   private void injectPage(Page page) {
      usedPages.injectPage(page);
   }

   protected int getUsedPagesSize() {
      return usedPages.size();
   }

   protected void forEachUsedPage(Consumer<Page> consumerPage) {
      usedPages.forEachUsedPage(consumerPage);
   }

   @Override
   public StorageManager getStorageManager() {
      return storageManager;
   }

}
