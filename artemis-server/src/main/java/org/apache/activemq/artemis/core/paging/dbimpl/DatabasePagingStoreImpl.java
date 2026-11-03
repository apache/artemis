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
package org.apache.activemq.artemis.core.paging.dbimpl;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.paging.cursor.PageCursorProvider;
import org.apache.activemq.artemis.core.paging.cursor.PageSubscription;
import org.apache.activemq.artemis.core.paging.impl.AddressSizeLimiter;
import org.apache.activemq.artemis.core.paging.impl.Page;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.persistence.impl.database.DatabaseStorageManager;
import org.apache.activemq.artemis.core.replication.ReplicationManager;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.RouteContextList;
import org.apache.activemq.artemis.core.server.StorageMessageReader;
import org.apache.activemq.artemis.core.server.impl.DatabaseStorageMessageReader;
import org.apache.activemq.artemis.core.server.impl.QueueImpl;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.settings.impl.PageFullMessagePolicy;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.utils.actors.ArtemisExecutor;
import org.apache.artemis.database.worker.DataManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Database-backed PagingStore implementation.
 */
public class DatabasePagingStoreImpl extends AddressSizeLimiter implements PagingStore {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final Set<DatabaseStorageMessageReader> readers = ConcurrentHashMap.newKeySet();

   public DatabasePagingStoreImpl(StorageManager storageManager,
                                  PagingManager pagingManager,
                                  SimpleString address,
                                  AddressSettings addressSettings,
                                  ArtemisExecutor executor) {
      super(storageManager, pagingManager, address, addressSettings, executor);
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
   public PageFullMessagePolicy getPageFullMessagePolicy() {
      return null;
   }

   @Override
   public Long getPageLimitMessages() {
      return 0L;
   }

   @Override
   public Long getPageLimitBytes() {
      return 0L;
   }

   @Override
   public void pageFull(PageSubscription subscription) {

   }

   @Override
   public boolean isPageFull() {
      return false;
   }

   @Override
   public void checkPageLimit(long numberOfMessages) {

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
   public void applySetting(AddressSettings addressSettings) {

   }

   @Override
   public void ioSync() throws Exception {

   }

   @Override
   public boolean isDeliveryFrozen() {
      return isStorePaging();
   }

   @Override
   public boolean page(Message message, Transaction tx, RouteContextList listCtx) throws Exception {
      return page(message, tx, listCtx, null) >= 0;
   }

   @Override
   public int page(Message message,
                   Transaction tx,
                   RouteContextList listCtx,
                   Function<Message, Message> pageDecorator) throws Exception {

      Integer policiesResult = checkFullPolicies(message);
      if (policiesResult != null) {
         return policiesResult;
      }

      return -1;
   }

   @Override
   public Page usePage(long page) {
      return null;
   }

   @Override
   public Page usePage(long page, boolean create) {
      return null;
   }

   @Override
   public Page usePage(long page, boolean createEntry, boolean createFile) {
      return null;
   }

   @Override
   public Page newPageObject(long page) throws Exception {
      return null;
   }

   @Override
   public boolean checkPageFileExists(long page) throws Exception {
      return false;
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
   public Page removePage(int pageId) {
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
   public void counterSnapshot() {

   }

   @Override
   public void stopPaging() throws Exception {
      writeLock();
      try {
         paging = false;
      } finally {
         writeUnlock();
      }
   }

   @Override
   public Collection<Integer> getCurrentIds() throws Exception {
      return List.of();
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
   public void destroy() throws Exception {

   }

   @Override
   public StorageMessageReader createStorageMessageReader(QueueImpl queue) {
      DatabaseStorageMessageReader reader = new DatabaseStorageMessageReader(queue, (DatabaseStorageManager) storageManager, this);
      readers.add(reader);
      return reader;
   }

   public void removeReader(DatabaseStorageMessageReader reader) {
      readers.remove(reader);
   }

   public void checkStopPaging() {
      if (!paging) {
         return;
      }

      for (DatabaseStorageMessageReader reader : readers) {
         if (reader.getPagedMessages() > 0) {
            return;
         }
      }

      DataManager dataManager = ((DatabaseStorageManager) storageManager).getDataManager();
      if (dataManager.hasPendingData()) {
         return;
      }

      writeLock();
      try {
         if (!paging) {
            return;
         }

         for (DatabaseStorageMessageReader reader : readers) {
            if (reader.getPagedMessages() > 0) {
               return;
            }
         }

         if (dataManager.hasPendingData()) {
            return;
         }

         paging = false;
         ActiveMQServerLogger.LOGGER.pageStoreStop(getAddress(), getInfo());
      } finally {
         writeUnlock();
      }
   }

   @Override
   public void durableUp(Message message, int durableCount) {

   }

   @Override
   public void durableDown(Message message, int durableCount) {

   }

   @Override
   public void refUp(Message message, int nonDurableCount) {

   }

   @Override
   public void refDown(Message message, int nonDurableCount) {
   }
}
