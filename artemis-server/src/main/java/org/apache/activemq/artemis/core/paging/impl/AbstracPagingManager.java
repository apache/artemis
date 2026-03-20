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
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.paging.PagingStoreFactory;
import org.apache.activemq.artemis.core.server.ActiveMQScheduledComponent;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.utils.CompositeAddress;
import org.apache.activemq.artemis.utils.SizeAwareMetric;
import org.apache.activemq.artemis.utils.runnables.AtomicRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstracPagingManager implements PagingManager {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   protected volatile boolean started = false;

   /**
    * Lock used at the start of synchronization between a primary server and its backup. Synchronization will lock all
    * {@link PagingStore} instances, and so any operation here that requires a lock on a {@link PagingStore} instance
    * needs to take a read-lock on {@link #syncLock} to avoid dead-locks.
    */
   protected final ReentrantReadWriteLock syncLock = new ReentrantReadWriteLock();

   protected final Set<AddressSizeLimiter> blockedStored = ConcurrentHashMap.newKeySet();

   protected final ConcurrentMap<SimpleString, PagingStore> stores = new ConcurrentHashMap<>();

   protected final HierarchicalRepository<AddressSettings> addressSettingsRepository;

   protected final ActiveMQServer server;

   protected PagingStoreFactory pagingStoreFactory;

   protected volatile boolean globalFull;

   protected final SizeAwareMetric globalSizeMetric;

   protected long maxSize;

   protected long maxMessages;

   protected final Queue<Runnable> memoryCallback = new ConcurrentLinkedQueue<>();

   protected ActiveMQScheduledComponent snapshotUpdater = null;

   protected final SimpleString managementAddress;

   protected AbstracPagingManager(final PagingStoreFactory pagingStoreFactory,
                                  final HierarchicalRepository<AddressSettings> addressSettingsRepository,
                                  final long maxSize,
                                  final long maxMessages,
                                  final SimpleString managementAddress,
                                  final ActiveMQServer server) {
      this.pagingStoreFactory = pagingStoreFactory;
      this.addressSettingsRepository = addressSettingsRepository;
      this.maxSize = maxSize;
      this.maxMessages = maxMessages;
      this.globalSizeMetric = new SizeAwareMetric(maxSize, maxSize, maxMessages, maxMessages);
      globalSizeMetric.setOverCallback(() -> setGlobalFull(true));
      globalSizeMetric.setUnderCallback(() -> setGlobalFull(false));
      this.managementAddress = managementAddress;
      this.server = server;
   }

   protected void setGlobalFull(boolean globalFull) {
      synchronized (memoryCallback) {
         this.globalFull = globalFull;
         checkMemoryRelease();
      }
   }

   SizeAwareMetric getSizeAwareMetric() {
      return globalSizeMetric;
   }

   @Override
   public long getMaxSize() {
      return maxSize;
   }

   @Override
   public long getMaxMessages() {
      return maxMessages;
   }

   @Override
   public void addBlockedStore(AddressSizeLimiter store) {
      blockedStored.add(store);
   }

   public Set<AddressSizeLimiter> getBlockedSet() {
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
   public PagingManager simpleAdd(long elements, long size) {
      globalSizeMetric.simpleAdd(elements, size);
      return this;
   }

   @Override
   public PagingManager addSize(int size, boolean sizeOnly) {
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
      if (!isGlobalFull() && !blockedStored.isEmpty()) {
         if (!memoryCallback.isEmpty()) {
            onCheckMemoryRelease();
         }
         blockedStored.removeIf(AddressSizeLimiter::checkReleasedMemory);
      }
   }

   /**
    * Called by {@link #checkMemoryRelease()} when there are pending memory callbacks to drain.
    * Subclasses that manage an executor should override this to dispatch {@link #memoryReleased()}
    * on their executor rather than running it inline.
    */
   protected void onCheckMemoryRelease() {
      memoryReleased();
   }

   @Override
   public void checkMemory(final Runnable runWhenAvailable) {
      if (isGlobalFull()) {
         memoryCallback.add(AtomicRunnable.checkAtomic(runWhenAvailable));
         return;
      }
      runWhenAvailable.run();
   }

   protected void memoryReleased() {
      Runnable runnable;

      while ((runnable = memoryCallback.poll()) != null) {
         runnable.run();
      }
   }

   /**
    * Returns true if global memory limits are exceeded.
    * Subclasses may override to add further conditions (e.g. disk full in {@link PagingManagerImpl}).
    */
   @Override
   public boolean isGlobalFull() {
      return maxSize > 0 && globalFull;
   }

   @Override
   public SimpleString[] getStoreNames() {
      Set<SimpleString> names = stores.keySet();
      return names.toArray(new SimpleString[names.size()]);
   }

   protected void stopStore(SimpleString storeName, PagingStore store) {
      try {
         store.stop();
      } catch (Throwable ok) {
         logger.debug(ok.getMessage(), ok);
      }
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
      // only if store is null we use computeIfAbsent
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

   // any caller that calls this method must guarantee the store doesn't exist.
   protected PagingStore newStore(final SimpleString address) throws Exception {
      assert managementAddress == null || !address.startsWith(managementAddress);
      syncLock.readLock().lock();
      try {
         PagingStore store = pagingStoreFactory.newStore(address, addressSettingsRepository.getMatch(address.toString()));
         store.start();
         return store;
      } finally {
         syncLock.readLock().unlock();
      }
   }

   @Override
   public boolean isUsingGlobalSize() {
      return maxSize > 0;
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
   public boolean isStarted() {
      return started;
   }
}
