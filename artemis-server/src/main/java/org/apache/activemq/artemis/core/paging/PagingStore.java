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
package org.apache.activemq.artemis.core.paging;

import java.io.File;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.RefCountMessageListener;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.paging.cursor.PageCursorProvider;
import org.apache.activemq.artemis.core.paging.cursor.PageSubscription;
import org.apache.activemq.artemis.core.paging.impl.Page;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.replication.ReplicationManager;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.core.server.RouteContextList;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.settings.impl.PageFullMessagePolicy;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.utils.actors.ArtemisExecutor;
import org.apache.activemq.artemis.utils.runnables.AtomicRunnable;

/**
 * The implementation will take care of details such as PageSize.
 * <p>
 * The producers will write directly to PagingStore, and the store will decide what Page file should be used based on
 * configured size.
 *
 * @see PagingManager
 */
public interface PagingStore extends ActiveMQComponent, RefCountMessageListener {

   SimpleString getAddress();

   long getNumberOfPages();

   /**
    * {@return the page id of the current page in which the system is writing files}
    */
   long getCurrentWritingPage();

   SimpleString getStoreName();

   File getFolder();

   default String getFolderName() {
      return getFolder().getName();
   }

   AddressFullMessagePolicy getAddressFullMessagePolicy();

   default PagingStore enforceAddressFullMessagePolicy(AddressFullMessagePolicy enforcedAddressFullMessagePolicy) {
      return this;
   }

   PageFullMessagePolicy getPageFullMessagePolicy();

   Long getPageLimitMessages();

   Long getPageLimitBytes();

   /**
    * Callback to be used by a counter when the Page is full for that counter
    */
   void pageFull(PageSubscription subscription);

   boolean isPageFull();

   void checkPageLimit(long numberOfMessages);

   long getFirstPage();

   int getPageSizeBytes();

   long getAddressSize();

   long getAddressElements();

   long getMaxSize();

   int getMaxPageReadBytes();

   int getMaxPageReadMessages();

   int getPrefetchPageBytes();

   int getPrefetchPageMessages();

   void applySetting(AddressSettings addressSettings);

   /**
    * This method will look if the current state of paging is not paging, without using a lock. For cases where you need
    * absolutely atomic results, check it directly on the internal variables while requiring a readLock.
    * <p>
    * It's ok to look for this with an estimate on starting a task or not, but you will need to recheck on actual paging
    * operations.
    */
   boolean isPaging();

   /**
    *  isPaging might return true for example if it's DROPPING.
    *  This will only return true if there are files on the page system */
   boolean isStorePaging();

   /**
    * Performs a real sync on the current IO file.
    */
   void ioSync() throws Exception;

   /**
    * Write message to page if we are paging.
    *
    * @return {@code true} if we are paging and have handled the data, {@code false} if the data needs to be sent to the
    * journal
    * @throws NullPointerException if {@code readLock} is null
    */
   boolean page(Message message, Transaction tx, RouteContextList listCtx) throws Exception;

   int page(Message message, Transaction tx, RouteContextList listCtx, Function<Message, Message> pageDecorator, boolean useFlowControl) throws Exception;

   Page usePage(long page);

   /**
    * Use this method when you want to use the cache of used pages. If you are just using offline (e.g. print-data), use
    * the newPageObject method.
    */
   Page usePage(long page, boolean create);
   Page usePage(long page, boolean createEntry, boolean createFile);

   Page newPageObject(long page) throws Exception;

   boolean checkPageFileExists(long page) throws Exception;

   PagingManager getPagingManager();

   PageCursorProvider getCursorProvider();

   void processReload() throws Exception;

   /**
    * Remove the first page from the Writing Queue. The file will still exist until Page.delete is called, So, case the
    * system is reloaded the same Page will be loaded back if delete is not called.
    *
    * @throws Exception Note: This should still be part of the interface, even though Apache Artemis only uses through
    *                   the
    */
   Page depage() throws Exception;

   Page removePage(int pageId);

   default void forceAnotherPage() throws Exception {
      forceAnotherPage(false);
   }

   void forceAnotherPage(boolean useExecutor) throws Exception;

   Page getCurrentPage();

   /**
    * Save snapshots on the counters
    */
   void counterSnapshot();

   /**
    * {@return true if paging was started, or false if paging was already started before this call}
    */
   boolean startPaging() throws Exception;

   default boolean startPaging(long timeout, TimeUnit unit) throws Exception {
      return startPaging();
   }

   void stopPaging() throws Exception;

   default void purgeFolder() {
   }

   /**
    * Add size to this {@code PageStore}.
    *
    * @param sizeOnly if {@code false} we won't increment the number of messages. (add references for example)
    */
   void addSize(int size, boolean sizeOnly, boolean affectGlobal);

   default void addSize(int size, boolean sizeOnly) {
      addSize(size, sizeOnly, true);
   }

   default void addSize(int size) {
      addSize(size, false, true);
   }

   boolean checkMemory(Runnable runnable, Consumer<AtomicRunnable> blockedCallback);

   boolean checkMemory(boolean runOnFailure, Runnable runnable, Runnable runWhenBlocking, Consumer<AtomicRunnable> blockedCallback);

   boolean isFull();

   boolean isRejectingMessages();

   /**
    * It will return true if the destination is leaving blocking.
    */
   boolean checkReleasedMemory();

   void writeLock();

   /**
    * Write lock the PagingStore.
    *
    * @param timeout milliseconds to wait for the lock. If value is {@literal -1} then wait indefinitely.
    * @return {@code true} if the lock was obtained, {@code false} otherwise
    */
   boolean writeLock(long timeout);

   /**
    * Releases locks acquired with {@link PagingStore#writeLock(long)}.
    */
   void writeUnlock();

   void readLock();

   boolean readLock(long timeout);

   void readUnlock();

   /**
    * This is used mostly by tests. We will wait any pending runnable to finish its execution
    */
   void flushExecutors();

   void execute(Runnable runnable);

   ArtemisExecutor getExecutor();

   /**
    * Files to synchronize with a remote backup.
    *
    * @return a collection of page IDs which must be synchronized with a replicating backup
    */
   Collection<Integer> getCurrentIds() throws Exception;

   /**
    * Sends the pages with given IDs to the {@link ReplicationManager}.
    * <p>
    * Sending is done here to avoid exposing the internal {@link org.apache.activemq.artemis.core.io.SequentialFile}s.
    */
   void sendPages(ReplicationManager replicator, Collection<Integer> pageIds) throws Exception;

   /**
    * This method will disable cleanup of pages. No page will be deleted after this call.
    */
   void disableCleanup();

   /**
    * This method will re-enable cleanup of pages. Notice that it will also start cleanup threads.
    */
   void enableCleanup();

   void destroy() throws Exception;

   int getAddressLimitPercent();

   void block();

   void unblock();

   boolean isBlockedViaManagement();

   default boolean hasPendingIO() {
      return false;
   }

   default StorageManager getStorageManager() {
      return null;
   }

   default void writeFlowControl(int credits) {
   }
}
