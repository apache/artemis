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
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.RefCountMessageListener;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.paging.cursor.PageCursorProvider;
import org.apache.activemq.artemis.core.paging.impl.Page;
import org.apache.activemq.artemis.core.replication.ReplicationManager;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.core.server.RouteContextList;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.transaction.Transaction;

/**
 * <p>
 * The implementation will take care of details such as PageSize.
 * <p>
 * The producers will write directly to PagingStore, and the store will decide what Page file should
 * be used based on configured size.
 * </p>
 *
 * @see PagingManager
 */
public interface PagingStore extends ActiveMQComponent, RefCountMessageListener {

   SimpleString getAddress();

   int getNumberOfPages();

   /**
    * Returns the page id of the current page in which the system is writing files.
    */
   int getCurrentWritingPage();

   SimpleString getStoreName();

   File getFolder();

   AddressFullMessagePolicy getAddressFullMessagePolicy();

   long getFirstPage();

   int getPageSizeBytes();

   long getAddressSize();

   long getMaxSize();

   void applySetting(AddressSettings addressSettings);

   /** This method will look if the current state of paging is not paging,
    * without using a lock.
    * For cases where you need absolutely atomic results, check it directly on the internal variables while requiring a readLock.
    *
    * It's ok to look for this with an estimate on starting a task or not, but you will need to recheck on actual paging operations. */
   boolean isPaging();

   /**
    * Schedules sync to the file storage.
    */
   void sync() throws Exception;

   /**
    * Performs a real sync on the current IO file.
    */
   void ioSync() throws Exception;

   /**
    * Write message to page if we are paging.
    *
    * @param readLock a read lock from the storage manager. This is an encapsulation violation made
    *                 to keep the code less complex. If give {@code null} the method will throw a
    *                 {@link NullPointerException}
    * @return {@code true} if we are paging and have handled the data, {@code false} if the data
    * needs to be sent to the journal
    * @throws NullPointerException if {@code readLock} is null
    */
   boolean page(Message message, Transaction tx, RouteContextList listCtx, ReadLock readLock) throws Exception;

   Page createPage(int page) throws Exception;

   boolean checkPageFileExists(int page) throws Exception;

   PagingManager getPagingManager();

   PageCursorProvider getCursorProvider();

   void processReload() throws Exception;

   /**
    * Remove the first page from the Writing Queue.
    * The file will still exist until Page.delete is called,
    * So, case the system is reloaded the same Page will be loaded back if delete is not called.
    *
    * @throws Exception Note: This should still be part of the interface, even though ActiveMQ Artemis only uses through the
    */
   Page depage() throws Exception;

   void forceAnotherPage() throws Exception;

   Page getCurrentPage();

   /**
    * @return true if paging was started, or false if paging was already started before this call
    */
   boolean startPaging() throws Exception;

   void stopPaging() throws Exception;

   void addSize(int size);

   boolean checkMemory(Runnable runnable);

   boolean checkMemory(boolean runOnFailure, Runnable runnable, Runnable runWhenBlocking);

   boolean isFull();

   boolean isRejectingMessages();

   /**
    * It will return true if the destination is leaving blocking.
    */
   boolean checkReleasedMemory();

   /**
    * Write lock the PagingStore.
    *
    * @param timeout milliseconds to wait for the lock. If value is {@literal -1} then wait
    *                indefinitely.
    * @return {@code true} if the lock was obtained, {@code false} otherwise
    */
   boolean lock(long timeout);

   /**
    * Releases locks acquired with {@link PagingStore#lock(long)}.
    */
   void unlock();

   /**
    * This is used mostly by tests.
    * We will wait any pending runnable to finish its execution
    */
   void flushExecutors();

   /**
    * Files to synchronize with a remote backup.
    *
    * @return a collection of page IDs which must be synchronized with a replicating backup
    * @throws Exception
    */
   Collection<Integer> getCurrentIds() throws Exception;

   /**
    * Sends the pages with given IDs to the {@link ReplicationManager}.
    * <p>
    * Sending is done here to avoid exposing the internal {@link org.apache.activemq.artemis.core.io.SequentialFile}s.
    *
    * @param replicator
    * @param pageIds
    * @throws Exception
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
}
