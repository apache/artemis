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

import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.core.server.files.FileStoreMonitor;
import org.apache.activemq.artemis.core.settings.HierarchicalRepositoryChangeListener;

/**
 * <PRE>
 *
 * +--------------+      1  +----------------+       N +--------------+       N +--------+       1 +-------------------+
 * | {@link org.apache.activemq.artemis.core.postoffice.PostOffice} |-------&gt; |{@link PagingManager}|-------&gt; |{@link PagingStore} | ------&gt; | {@link org.apache.activemq.artemis.core.paging.impl.Page}  | ------&gt; | {@link org.apache.activemq.artemis.core.io.SequentialFile} |
 * +--------------+         +----------------+         +--------------+         +--------+         +-------------------+
 * |                  1 ^
 * |                    |
 * |                    |
 * |                    | 1
 * |            N +----------+
 * +------------&gt; | {@link org.apache.activemq.artemis.core.postoffice.Address} |
 * +----------+
 * </PRE>
 */
public interface PagingManager extends ActiveMQComponent, HierarchicalRepositoryChangeListener {

   /**
    * {@return the PageStore associated with the address; a new page store is created if necessary}
    */
   PagingStore getPageStore(SimpleString address) throws Exception;

   /**
    * Point to inform/restoring Transactions used when the messages were added into paging
    */
   void addTransaction(PageTransactionInfo pageTransaction);

   /**
    * Point to inform/restoring Transactions used when the messages were added into paging
    */
   PageTransactionInfo getTransaction(long transactionID);

   void removeTransaction(long transactionID);

   Map<Long, PageTransactionInfo> getTransactions();

   /**
    * Reload previously created PagingStores into memory
    */
   void reloadStores() throws Exception;

   SimpleString[] getStoreNames();

   void deletePageStore(SimpleString storeName) throws Exception;

   void processReload() throws Exception;

   void disableCleanup();

   void resumeCleanup();

   void addBlockedStore(PagingStore store);

   void injectMonitor(FileStoreMonitor monitor) throws Exception;

   /**
    * Execute a runnable inside the PagingManager's executor
    */
   default void execute(Runnable runnable) {
      throw new UnsupportedOperationException("not implemented");
   }

   /**
    * Lock the manager. This method should not be called during normal PagingManager usage.
    */
   void lock();

   /**
    * Unlock the manager.
    *
    * @see #lock()
    */
   void unlock();

   /**
    * Add size at the global count level. If sizeOnly = true, only the size portion is updated. If false both the
    * counter for bytes and number of messages is updated.
    */
   PagingManager addSize(int size, boolean sizeOnly);

   /**
    * An utility method to call addSize(size, false); this is a good fit for an IntConsumer.
    */
   default PagingManager addSize(int size) {
      return addSize(size, false);
   }

   /**
    * An utility method to call addSize(size, true); this is a good fit for an IntConsumer.
    */
   default PagingManager addSizeOnly(int size) {
      return addSize(size, true);
   }

   boolean isUsingGlobalSize();

   boolean isGlobalFull();

   boolean isDiskFull();

   long getDiskUsableSpace();

   long getDiskTotalSpace();

   default long getGlobalSize() {
      return 0;
   }

   default long getGlobalMessages() {
      return 0;
   }

   /**
    * Use this when you have no refernce of an address. (anonymous AMQP Producers for example)
    */
   void checkMemory(Runnable runWhenAvailable);

   void counterSnapshot();

   /**
    * Use this when you have no refernce of an address. (anonymous AMQP Producers for example)
    */
   default void checkStorage(Runnable runWhenAvailable) {
      checkMemory(runWhenAvailable);
   }

   default long getMaxSize() {
      return 0;
   }

   default long getMaxMessages() {
      return 0;
   }

   /**
    * Rebuilds all page counters for destinations that are paging in the background.
    */
   default Future<Object> rebuildCounters(Set<Long> storedLargeMessages) {
      return null;
   }

   default void forEachTransaction(BiConsumer<Long, PageTransactionInfo> transactionConsumer) {
   }

   default boolean isRebuildingCounters() {
      return false;
   }

}
