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

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.paging.PageTransactionInfo;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.paging.PagingStoreFactory;
import org.apache.activemq.artemis.core.server.ActiveMQScheduledComponent;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.files.FileStoreMonitor;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.utils.ByteUtil;
import org.apache.activemq.artemis.utils.collections.ConcurrentHashSet;
import org.apache.activemq.artemis.utils.runnables.AtomicRunnable;
import org.jboss.logging.Logger;

public final class PagingManagerImpl implements PagingManager {

   private static final int ARTEMIS_DEBUG_PAGING_INTERVAL = Integer.valueOf(System.getProperty("artemis.debug.paging.interval", "0"));

   private static final Logger logger = Logger.getLogger(PagingManagerImpl.class);

   private volatile boolean started = false;

   /**
    * Lock used at the start of synchronization between a live server and its backup.
    * Synchronization will lock all {@link PagingStore} instances, and so any operation here that
    * requires a lock on a {@link PagingStore} instance needs to take a read-lock on
    * {@link #syncLock} to avoid dead-locks.
    */
   private final ReentrantReadWriteLock syncLock = new ReentrantReadWriteLock();

   private final Set<PagingStore> blockedStored = new ConcurrentHashSet<>();

   private final ConcurrentMap<SimpleString, PagingStore> stores = new ConcurrentHashMap<>();

   private final HierarchicalRepository<AddressSettings> addressSettingsRepository;

   private PagingStoreFactory pagingStoreFactory;

   private final AtomicLong globalSizeBytes = new AtomicLong(0);

   private final AtomicLong numberOfMessages = new AtomicLong(0);

   private final long maxSize;

   private volatile boolean cleanupEnabled = true;

   private volatile boolean diskFull = false;

   private volatile long diskUsableSpace = 0;

   private volatile long diskTotalSpace = 0;

   private final Executor memoryExecutor;

   private final Queue<Runnable> memoryCallback = new ConcurrentLinkedQueue<>();

   private final ConcurrentMap</*TransactionID*/Long, PageTransactionInfo> transactions = new ConcurrentHashMap<>();

   private ActiveMQScheduledComponent scheduledComponent = null;

   private final SimpleString managementAddress;




   // for tests.. not part of the API
   public void replacePageStoreFactory(PagingStoreFactory factory) {
      this.pagingStoreFactory = factory;
   }

   // for tests.. not part of the API
   public PagingStoreFactory getPagingStoreFactory() {
      return pagingStoreFactory;
   }

   public PagingManagerImpl(final PagingStoreFactory pagingSPI,
                            final HierarchicalRepository<AddressSettings> addressSettingsRepository,
                            final long maxSize,
                            final SimpleString managementAddress) {
      pagingStoreFactory = pagingSPI;
      this.addressSettingsRepository = addressSettingsRepository;
      addressSettingsRepository.registerListener(this);
      this.maxSize = maxSize;
      this.memoryExecutor = pagingSPI.newExecutor();
      this.managementAddress = managementAddress;
   }

   public long getMaxSize() {
      return maxSize;
   }

   public PagingManagerImpl(final PagingStoreFactory pagingSPI,
                            final HierarchicalRepository<AddressSettings> addressSettingsRepository) {
      this(pagingSPI, addressSettingsRepository, -1, null);
   }

   public PagingManagerImpl(final PagingStoreFactory pagingSPI,
                            final HierarchicalRepository<AddressSettings> addressSettingsRepository,
                            final SimpleString managementAddress) {
      this(pagingSPI, addressSettingsRepository, -1, managementAddress);
   }

   @Override
   public void addBlockedStore(PagingStore store) {
      blockedStored.add(store);
   }

   @Override
   public void onChange() {
      reapplySettings();
   }

   private void reapplySettings() {
      for (PagingStore store : stores.values()) {
         AddressSettings settings = this.addressSettingsRepository.getMatch(store.getAddress().toString());
         store.applySetting(settings);
      }
   }

   @Override
   public PagingManagerImpl addSize(int size) {

      if (size > 0) {
         numberOfMessages.incrementAndGet();
      } else {
         numberOfMessages.decrementAndGet();
      }

      long newSize = globalSizeBytes.addAndGet(size);

      if (newSize < 0) {
         ActiveMQServerLogger.LOGGER.negativeGlobalAddressSize(newSize);
      }

      if (size < 0) {
         checkMemoryRelease();
      }
      return this;
   }

   @Override
   public long getGlobalSize() {
      return globalSizeBytes.get();
   }

   protected void checkMemoryRelease() {
      if (!diskFull && (maxSize < 0 || globalSizeBytes.get() < maxSize) && !blockedStored.isEmpty()) {
         if (!memoryCallback.isEmpty()) {
            if (memoryExecutor != null) {
               memoryExecutor.execute(this::memoryReleased);
            } else {
               memoryReleased();
            }
         }
         blockedStored.removeIf(PagingStore::checkReleasedMemory);
      }
   }

   @Override
   public void injectMonitor(FileStoreMonitor monitor) throws Exception {
      pagingStoreFactory.injectMonitor(monitor);
      monitor.addCallback(new LocalMonitor());
   }

   class LocalMonitor implements FileStoreMonitor.Callback {

      private final Logger logger = Logger.getLogger(LocalMonitor.class);

      @Override
      public void tick(long usableSpace, long totalSpace) {
         diskUsableSpace = usableSpace;
         diskTotalSpace = totalSpace;
         logger.tracef("Tick:: usable space at %s, total space at %s", ByteUtil.getHumanReadableByteCount(usableSpace), ByteUtil.getHumanReadableByteCount(totalSpace));
      }

      @Override
      public void over(long usableSpace, long totalSpace) {
         if (!diskFull) {
            ActiveMQServerLogger.LOGGER.diskBeyondCapacity(ByteUtil.getHumanReadableByteCount(usableSpace), ByteUtil.getHumanReadableByteCount(totalSpace), String.format("%.1f%%", FileStoreMonitor.calculateUsage(usableSpace, totalSpace) * 100));
            diskFull = true;
         }
      }

      @Override
      public void under(long usableSpace, long totalSpace) {
         final boolean diskFull = PagingManagerImpl.this.diskFull;
         if (diskFull || !blockedStored.isEmpty() || !memoryCallback.isEmpty()) {
            if (diskFull) {
               ActiveMQServerLogger.LOGGER.diskCapacityRestored(ByteUtil.getHumanReadableByteCount(usableSpace), ByteUtil.getHumanReadableByteCount(totalSpace), String.format("%.1f%%", FileStoreMonitor.calculateUsage(usableSpace, totalSpace) * 100));
               PagingManagerImpl.this.diskFull = false;
            }
            checkMemoryRelease();
         }
      }
   }

   @Override
   public boolean isDiskFull() {
      return diskFull;
   }

   @Override
   public long getDiskUsableSpace() {
      return diskUsableSpace;
   }

   @Override
   public long getDiskTotalSpace() {
      return diskTotalSpace;
   }

   @Override
   public boolean isUsingGlobalSize() {
      return maxSize > 0;
   }

   @Override
   public void checkMemory(final Runnable runWhenAvailable) {

      if (isGlobalFull()) {
         memoryCallback.add(AtomicRunnable.checkAtomic(runWhenAvailable));
         return;
      }
      runWhenAvailable.run();
   }

   @Override
   public void checkStorage(Runnable runWhenAvailable) {
      if (diskFull) {
         memoryCallback.add(AtomicRunnable.checkAtomic(runWhenAvailable));
         return;
      }
      runWhenAvailable.run();
   }

   private void memoryReleased() {
      Runnable runnable;

      while ((runnable = memoryCallback.poll()) != null) {
         runnable.run();
      }
   }



   @Override
   public boolean isGlobalFull() {
      return diskFull || maxSize > 0 && globalSizeBytes.get() > maxSize;
   }

   @Override
   public void disableCleanup() {
      if (!cleanupEnabled) {
         return;
      }

      lock();
      try {
         cleanupEnabled = false;
         for (PagingStore store : stores.values()) {
            store.disableCleanup();
         }
      } finally {
         unlock();
      }
   }

   @Override
   public void resumeCleanup() {
      if (cleanupEnabled) {
         return;
      }

      lock();
      try {
         cleanupEnabled = true;
         for (PagingStore store : stores.values()) {
            store.enableCleanup();
         }
      } finally {
         unlock();
      }
   }

   @Override
   public SimpleString[] getStoreNames() {
      Set<SimpleString> names = stores.keySet();
      return names.toArray(new SimpleString[names.size()]);
   }

   @Override
   public void reloadStores() throws Exception {
      lock();
      try {
         List<PagingStore> reloadedStores = pagingStoreFactory.reloadStores(addressSettingsRepository);

         for (PagingStore store : reloadedStores) {
            // when reloading, we need to close the previously loaded version of this
            // store
            PagingStore oldStore = stores.remove(store.getStoreName());
            if (oldStore != null) {
               oldStore.stop();
               oldStore = null;
            }
            store.start();
            stores.put(store.getStoreName(), store);
         }
      } finally {
         unlock();
      }

   }

   @Override
   public void deletePageStore(final SimpleString storeName) throws Exception {
      syncLock.readLock().lock();
      try {
         PagingStore store = stores.remove(storeName);
         if (store != null) {
            store.stop();
            store.destroy();
         }
      } finally {
         syncLock.readLock().unlock();
      }
   }

   /**
    * This method creates a new store if not exist.
    */
   @Override
   public PagingStore getPageStore(final SimpleString storeName) throws Exception {
      if (managementAddress != null && storeName.startsWith(managementAddress)) {
         return null;
      }

      PagingStore store = stores.get(storeName);
      if (store != null) {
         return store;
      }
      //only if store is null we use computeIfAbsent
      try {
         return stores.computeIfAbsent(storeName, (s) -> {
            try {
               return newStore(s);
            } catch (Exception e) {
               throw new RuntimeException(e);
            }
         });
      } catch (RuntimeException e) {
         throw (Exception) e.getCause();
      }
   }

   @Override
   public void addTransaction(final PageTransactionInfo pageTransaction) {
      if (logger.isTraceEnabled()) {
         logger.trace("Adding pageTransaction " + pageTransaction.getTransactionID());
      }
      transactions.put(pageTransaction.getTransactionID(), pageTransaction);
   }

   @Override
   public void removeTransaction(final long id) {
      if (logger.isTraceEnabled()) {
         logger.trace("Removing pageTransaction " + id);
      }
      transactions.remove(id);
   }

   @Override
   public PageTransactionInfo getTransaction(final long id) {
      if (logger.isTraceEnabled()) {
         logger.trace("looking up pageTX = " + id);
      }
      return transactions.get(id);
   }

   @Override
   public Map<Long, PageTransactionInfo> getTransactions() {
      return transactions;
   }

   @Override
   public boolean isStarted() {
      return started;
   }

   @Override
   public void start() throws Exception {
      lock();
      try {
         if (started) {
            return;
         }

         pagingStoreFactory.setPagingManager(this);

         reloadStores();

         if (ARTEMIS_DEBUG_PAGING_INTERVAL > 0) {
            this.scheduledComponent = new ActiveMQScheduledComponent(pagingStoreFactory.getScheduledExecutor(), pagingStoreFactory.newExecutor(), ARTEMIS_DEBUG_PAGING_INTERVAL, TimeUnit.SECONDS, false) {
               @Override
               public void run() {
                  debug();
               }
            };

            this.scheduledComponent.start();

         }

         started = true;
      } finally {
         unlock();
      }
   }

   public void debug() {
      logger.info("size = " + globalSizeBytes + " bytes, messages = " + numberOfMessages);
   }

   @Override
   public synchronized void stop() throws Exception {
      if (!started) {
         return;
      }
      started = false;

      if (scheduledComponent != null) {
         this.scheduledComponent.stop();
         this.scheduledComponent = null;
      }

      lock();
      try {

         for (PagingStore store : stores.values()) {
            store.stop();
         }

         pagingStoreFactory.stop();
      } finally {
         unlock();
      }
   }

   @Override
   public void processReload() throws Exception {
      for (PagingStore store : stores.values()) {
         store.processReload();
      }
   }

   //any caller that calls this method must guarantee the store doesn't exist.
   private PagingStore newStore(final SimpleString address) throws Exception {
      assert managementAddress == null || (managementAddress != null && !address.startsWith(managementAddress));
      syncLock.readLock().lock();
      try {
         PagingStore store = pagingStoreFactory.newStore(address, addressSettingsRepository.getMatch(address.toString()));
         store.start();
         if (!cleanupEnabled) {
            store.disableCleanup();
         }
         return store;
      } finally {
         syncLock.readLock().unlock();
      }
   }

   @Override
   public void unlock() {
      syncLock.writeLock().unlock();
   }

   @Override
   public void lock() {
      syncLock.writeLock().lock();
   }

}
