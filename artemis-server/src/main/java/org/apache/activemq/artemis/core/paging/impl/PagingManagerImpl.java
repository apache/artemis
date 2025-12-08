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

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;

import io.netty.util.collection.LongObjectHashMap;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.paging.PageTransactionInfo;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.paging.PagingStoreFactory;
import org.apache.activemq.artemis.core.paging.cursor.impl.PageCounterRebuildManager;
import org.apache.activemq.artemis.core.server.ActiveMQScheduledComponent;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.files.FileStoreMonitor;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.utils.ByteUtil;
import org.apache.activemq.artemis.utils.CompositeAddress;
import org.apache.activemq.artemis.utils.SizeAwareMetric;
import org.apache.activemq.artemis.utils.collections.ConcurrentHashSet;
import org.apache.activemq.artemis.utils.runnables.AtomicRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.activemq.artemis.core.server.files.FileStoreMonitor.FileStoreMonitorType;
import static org.apache.activemq.artemis.core.server.files.FileStoreMonitor.FileStoreMonitorType.MaxDiskUsage;

public final class PagingManagerImpl implements PagingManager {

   private static final int PAGE_TX_CLEANUP_PRINT_LIMIT = 1000;

   private static final int ARTEMIS_PAGING_COUNTER_SNAPSHOT_INTERVAL = Integer.parseInt(System.getProperty("artemis.paging.counter.snapshot.interval", "60"));

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private volatile boolean started = false;

   /**
    * Lock used at the start of synchronization between a primary server and its backup. Synchronization will lock all
    * {@link PagingStore} instances, and so any operation here that requires a lock on a {@link PagingStore} instance
    * needs to take a read-lock on {@link #syncLock} to avoid dead-locks.
    */
   private final ReentrantReadWriteLock syncLock = new ReentrantReadWriteLock();

   private final Set<PagingStore> blockedStored = new ConcurrentHashSet<>();

   private final ConcurrentMap<SimpleString, PagingStore> stores = new ConcurrentHashMap<>();

   private final HierarchicalRepository<AddressSettings> addressSettingsRepository;

   private final ActiveMQServer server;

   private PagingStoreFactory pagingStoreFactory;

   private volatile boolean globalFull;

   private void setGlobalFull(boolean globalFull) {
      synchronized (memoryCallback) {
         this.globalFull = globalFull;
         checkMemoryRelease();
      }
   }

   private final SizeAwareMetric globalSizeMetric;

   private long maxSize;

   private long maxMessages;

   private volatile boolean cleanupEnabled = true;

   private volatile boolean diskFull = false;

   private volatile long diskUsableSpace = 0;

   private volatile long diskTotalSpace = 0;

   private final Executor managerExecutor;

   private final Queue<Runnable> memoryCallback = new ConcurrentLinkedQueue<>();

   private final ConcurrentMap</*TransactionID*/Long, PageTransactionInfo> transactions = new ConcurrentHashMap<>();

   private ActiveMQScheduledComponent snapshotUpdater = null;

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
                            final long maxMessages,
                            final SimpleString managementAddress,
                            final ActiveMQServer server) {
      pagingStoreFactory = pagingSPI;
      this.addressSettingsRepository = addressSettingsRepository;
      addressSettingsRepository.registerListener(this);
      this.maxSize = maxSize;
      this.maxMessages = maxMessages;
      this.globalSizeMetric = new SizeAwareMetric(maxSize, maxSize, maxMessages, maxMessages);
      globalSizeMetric.setOverCallback(() -> setGlobalFull(true));
      globalSizeMetric.setUnderCallback(() -> setGlobalFull(false));
      this.managerExecutor = pagingSPI.newExecutor();
      this.managementAddress = managementAddress;
      this.server = server;
   }

   SizeAwareMetric getSizeAwareMetric() {
      return globalSizeMetric;
   }

   /**
    * To be used in tests only called through PagingManagerTestAccessor
    */
   void resetMaxSize(long maxSize, long maxMessages) {
      this.maxSize = maxSize;
      this.maxMessages = maxMessages;
      this.globalSizeMetric.setMax(maxSize, maxSize, maxMessages, maxMessages);
   }

   @Override
   public long getMaxSize() {
      return maxSize;
   }

   @Override
   public long getMaxMessages() {
      return maxMessages;
   }

   public PagingManagerImpl(final PagingStoreFactory pagingSPI,
                            final HierarchicalRepository<AddressSettings> addressSettingsRepository) {
      this(pagingSPI, addressSettingsRepository, -1, -1, null, null);
   }

   public PagingManagerImpl(final PagingStoreFactory pagingSPI,
                            final HierarchicalRepository<AddressSettings> addressSettingsRepository,
                            final SimpleString managementAddress) {
      this(pagingSPI, addressSettingsRepository, -1, -1, managementAddress, null);
   }

   @Override
   public void addBlockedStore(PagingStore store) {
      blockedStored.add(store);
   }

   public Set<PagingStore> getBlockedSet() {
      return new HashSet<>(blockedStored);
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
   public PagingManagerImpl addSize(int size, boolean sizeOnly) {
      long newSize = globalSizeMetric.addSize(size, sizeOnly);

      if (newSize < 0) {
         ActiveMQServerLogger.LOGGER.negativeGlobalAddressSize(newSize);
      }

      return this;
   }

   @Override
   public long getGlobalSize() {
      return globalSizeMetric.getSize();
   }

   @Override
   public long getGlobalMessages() {
      return globalSizeMetric.getElements();
   }

   protected void checkMemoryRelease() {
      if (!diskFull && (maxSize < 0 || !globalFull) && !blockedStored.isEmpty()) {
         if (!memoryCallback.isEmpty()) {
            if (managerExecutor != null) {
               managerExecutor.execute(this::memoryReleased);
            } else {
               memoryReleased();
            }
         }
         blockedStored.removeIf(PagingStore::checkReleasedMemory);
      }
   }

   @Override
   public void execute(Runnable run) {
      managerExecutor.execute(run);
   }

   @Override
   public void injectMonitor(FileStoreMonitor monitor) throws Exception {
      pagingStoreFactory.injectMonitor(monitor);
      monitor.addCallback(new LocalMonitor());
   }

   class LocalMonitor implements FileStoreMonitor.Callback {

      private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

      @Override
      public void tick(long usableSpace, long totalSpace, boolean withinLimit, FileStoreMonitorType type) {
         diskUsableSpace = usableSpace;
         diskTotalSpace = totalSpace;
         if (logger.isTraceEnabled()) {
            logger.trace("Tick:: usable space at {}, total space at {}", ByteUtil.getHumanReadableByteCount(usableSpace), ByteUtil.getHumanReadableByteCount(totalSpace));
         }
         if (withinLimit) {
            final boolean diskFull = PagingManagerImpl.this.diskFull;
            if (diskFull || !blockedStored.isEmpty() || !memoryCallback.isEmpty()) {
               if (diskFull) {
                  if (type == MaxDiskUsage) {
                     ActiveMQServerLogger.LOGGER.maxDiskUsageRestored(ByteUtil.getHumanReadableByteCount(usableSpace), ByteUtil.getHumanReadableByteCount(totalSpace), String.format("%.1f%%", FileStoreMonitor.calculateUsage(usableSpace, totalSpace) * 100));
                  } else {
                     ActiveMQServerLogger.LOGGER.minDiskFreeRestored(ByteUtil.getHumanReadableByteCount(usableSpace));
                  }
                  PagingManagerImpl.this.diskFull = false;
               }
               checkMemoryRelease();
            }
         } else {
            if (!diskFull) {
               if (type == MaxDiskUsage) {
                  ActiveMQServerLogger.LOGGER.maxDiskUsageReached(ByteUtil.getHumanReadableByteCount(usableSpace), ByteUtil.getHumanReadableByteCount(totalSpace), String.format("%.1f%%", FileStoreMonitor.calculateUsage(usableSpace, totalSpace) * 100));
               } else {
                  ActiveMQServerLogger.LOGGER.minDiskFreeReached(ByteUtil.getHumanReadableByteCount(usableSpace));
               }
               diskFull = true;
            }
         }
      }
   }

   // For tests only!
   protected void setDiskFull(boolean diskFull) {
      this.diskFull = diskFull;
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
      return diskFull || maxSize > 0 && globalFull;
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

   private void stopStore(SimpleString storeName, PagingStore store) {
      try {
         store.stop();
      } catch (Throwable ok) {
         logger.debug(ok.getMessage(), ok);
      }
   }

   @Override
   public void reloadStores() throws Exception {
      lock();
      try {
         stores.forEach(this::stopStore);
         stores.clear();

         List<PagingStore> reloadedStores = pagingStoreFactory.reloadStores(addressSettingsRepository);
         for (PagingStore store : reloadedStores) {
            store.getCursorProvider().counterRebuildStarted();
            store.start();
            stores.put(store.getStoreName(), store);
         }
      } finally {
         unlock();
      }
   }

   @Override
   public void deletePageStore(final SimpleString storeName) throws Exception {
      PagingStore store;
      syncLock.readLock().lock();
      try {
         store = stores.remove(CompositeAddress.extractAddressName(storeName));
      } finally {
         syncLock.readLock().unlock();
      }

      if (store != null) {
         store.destroy();
      }
   }

   @Override
   public int cleanupOrphanedPageStores() {
      if (server == null) {
         logger.warn("Server reference not available, cannot cleanup orphaned page stores");
         return 0;
      }

      logger.debug("Starting orphaned paging store cleanup check. Total stores: {}", stores.size());

      int removed = 0;
      syncLock.readLock().lock();
      try {
         // Get a snapshot of current store names
         Set<SimpleString> storeNames = new HashSet<>(stores.keySet());

         for (SimpleString storeName : storeNames) {
            // Check if the address still exists
            if (server.getAddressInfo(storeName) == null) {
               try {
                  logger.debug("Removing orphaned paging store for: {}", storeName);
                  // Remove directly from stores map - we already hold the read lock
                  PagingStore store = stores.remove(CompositeAddress.extractAddressName(storeName));
                  if (store != null) {
                     store.destroy();
                     removed++;
                  }
               } catch (Exception e) {
                  logger.warn("Error removing orphaned paging store for {}: {}", storeName, e.getMessage(), e);
               }
            }
         }

         if (removed > 0) {
            logger.info("Cleaned up {} orphaned paging store(s)", removed);
         } else {
            logger.debug("Finished orphaned paging store cleanup check. No orphaned stores found.");
         }
      } finally {
         syncLock.readLock().unlock();
      }

      return removed;
   }

   /**
    * This method creates a new store if not exist.
    */
   @Override
   public PagingStore getPageStore(final SimpleString rawStoreName) throws Exception {
      final SimpleString storeName = CompositeAddress.extractAddressName(rawStoreName);
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
         logger.trace("Adding pageTransaction {}", pageTransaction.getTransactionID());
      }
      transactions.put(pageTransaction.getTransactionID(), pageTransaction);
   }

   @Override
   public void removeTransaction(final long id) {
      if (logger.isTraceEnabled()) {
         logger.trace("Removing pageTransaction {}", id);
      }
      transactions.remove(id);
   }

   @Override
   public PageTransactionInfo getTransaction(final long id) {
      if (logger.isTraceEnabled()) {
         logger.trace("looking up pageTX = {}", id);
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

   private volatile boolean rebuildingPageCounters;


   @Override
   public boolean isRebuildingCounters() {
      return rebuildingPageCounters;
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

         if (ARTEMIS_PAGING_COUNTER_SNAPSHOT_INTERVAL > 0) {
            this.snapshotUpdater = new ActiveMQScheduledComponent(pagingStoreFactory.getScheduledExecutor(), pagingStoreFactory.newExecutor(), ARTEMIS_PAGING_COUNTER_SNAPSHOT_INTERVAL, TimeUnit.SECONDS, false) {
               @Override
               public void run() {
                  try {
                     logger.debug("Updating counter snapshots");
                     counterSnapshot();
                  } catch (Throwable e) {
                     logger.warn(e.getMessage(), e);
                  }
               }
            };

            this.snapshotUpdater.start();

         }

         started = true;

      } finally {
         unlock();
      }
   }

   @Override
   public void counterSnapshot() {
      for (PagingStore store : stores.values()) {
         store.counterSnapshot();
      }
   }

   @Override
   public synchronized void stop() throws Exception {
      if (!started) {
         return;
      }
      started = false;

      if (snapshotUpdater != null) {
         this.snapshotUpdater.stop();
         this.snapshotUpdater = null;
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
      logger.debug("Processing reload");
      for (PagingStore store : stores.values()) {
         logger.debug("Processing reload on page store {}", store.getAddress());
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

   @Override
   public void forEachTransaction(BiConsumer<Long, PageTransactionInfo> transactionConsumer) {
      transactions.forEach(transactionConsumer);
   }

   @Override
   public Future<Object> rebuildCounters(Set<Long> storedLargeMessages) {
      if (rebuildingPageCounters) {
         logger.debug("Rebuild page counters is already underway, ignoring call");
      }
      Map<Long, PageTransactionInfo> transactionsSet = new LongObjectHashMap();
      // making a copy
      transactions.forEach((a, b) -> {
         transactionsSet.put(a, b);
         b.setOrphaned(true);
      });
      AtomicLong minLargeMessageID = new AtomicLong(Long.MAX_VALUE);

      // make a copy of the stores
      Map<SimpleString, PagingStore> currentStoreMap = new HashMap<>();
      stores.forEach(currentStoreMap::put);

      if (logger.isDebugEnabled()) {
         logger.debug("Page Transactions during rebuildCounters:");
         transactionsSet.forEach((a, b) -> logger.debug("{} = {}", a, b));
      }

      currentStoreMap.forEach((address, pgStore) -> {
         PageCounterRebuildManager rebuildManager = new PageCounterRebuildManager(this, pgStore, transactionsSet, storedLargeMessages, minLargeMessageID);
         logger.debug("Setting destination {} to rebuild counters", address);
         managerExecutor.execute(rebuildManager);
      });

      managerExecutor.execute(() -> cleanupPageTransactions(transactionsSet, currentStoreMap));

      FutureTask<Object> task = new FutureTask<>(() -> null);
      managerExecutor.execute(task);

      managerExecutor.execute(() -> rebuildingPageCounters = false);

      return task;
   }

   private void cleanupPageTransactions(Map<Long, PageTransactionInfo> transactionSet, Map<SimpleString, PagingStore> currentStoreMap) {
      if (server == null) {
         logger.warn("Server attribute was not set, cannot proceed with page transaction cleanup");
      }
      AtomicBoolean proceed = new AtomicBoolean(true);
      ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
      // I'm now checking if all pagingStore have finished rebuilding the page counter.
      // this would be only false if some exception happened on previous executions on the rebuild manager.
      // so if any exception happened, the cleanup is not going to be done
      currentStoreMap.forEach((a, b) -> {
         if (!b.getCursorProvider().isRebuildDone()) {
            logger.warn("cannot proceed on cleaning up page transactions as page cursor for {} is not done rebuilding it", b.getAddress());
            proceed.set(false);
         }
      });

      if (!proceed.get()) {
         return;
      }

      AtomicLong txRemoved = new AtomicLong(0);

      transactionSet.forEach((a, b) -> {
         if (b.isOrphaned()) {
            b.onUpdate(b.getNumberOfMessages(), server.getStorageManager(), this);
            txRemoved.incrementAndGet();

            // I'm pringing up to 1000 records, id by ID..
            if (txRemoved.get() < PAGE_TX_CLEANUP_PRINT_LIMIT) {
               ActiveMQServerLogger.LOGGER.removeOrphanedPageTransaction(a);
            } else {
               // after a while, I start just printing counters to speed up things a bit
               if (txRemoved.get() % PAGE_TX_CLEANUP_PRINT_LIMIT == 0) {
                  ActiveMQServerLogger.LOGGER.cleaningOrphanedTXCleanup(txRemoved.get());
               }

            }
         }
      });

      if (txRemoved.get() > 0) {
         ActiveMQServerLogger.LOGGER.completeOrphanedTXCleanup(txRemoved.get());
      } else {
         logger.debug("Complete cleanupPageTransactions with no orphaned records found");
      }
   }
}
