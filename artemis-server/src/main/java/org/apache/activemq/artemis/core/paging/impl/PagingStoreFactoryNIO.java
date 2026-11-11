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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.io.IOCriticalErrorListener;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.io.nio.NIOSequentialFileFactory;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.paging.PagingStoreFactory;
import org.apache.activemq.artemis.core.paging.cursor.PageCursorProvider;
import org.apache.activemq.artemis.core.paging.cursor.impl.PageCursorProviderImpl;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.files.FileMoveManager;
import org.apache.activemq.artemis.core.server.files.FileStoreMonitor;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.apache.activemq.artemis.utils.FileUtil;
import org.apache.activemq.artemis.utils.UUIDGenerator;
import org.apache.activemq.artemis.utils.actors.ArtemisExecutor;

/**
 * Integration point between Paging and NIO
 */
public class PagingStoreFactoryNIO implements PagingStoreFactory {


   public static final String ADDRESS_FILE = "address.txt";


   private final File directory;

   private final ExecutorFactory executorFactory;

   private final boolean syncNonTransactional;

   private PagingManager pagingManager;

   private final ScheduledExecutorService scheduledExecutor;

   private final long syncTimeout;

   private final StorageManager storageManager;

   private final IOCriticalErrorListener critialErrorListener;

   private final Supplier<Boolean> purgePageFolders;

   public File getDirectory() {
      return directory;
   }

   public ExecutorFactory getExecutorFactory() {
      return executorFactory;
   }

   public boolean isSyncNonTransactional() {
      return syncNonTransactional;
   }

   public PagingManager getPagingManager() {
      return pagingManager;
   }

   public long getSyncTimeout() {
      return syncTimeout;
   }

   public StorageManager getStorageManager() {
      return storageManager;
   }

   public IOCriticalErrorListener getCritialErrorListener() {
      return critialErrorListener;
   }

   public PagingStoreFactoryNIO(final StorageManager storageManager,
                                final File directory,
                                final long syncTimeout,
                                final ScheduledExecutorService scheduledExecutor,
                                final ExecutorFactory executorFactory,
                                final boolean syncNonTransactional,
                                final IOCriticalErrorListener critialErrorListener) {
      this(storageManager, directory, syncTimeout, scheduledExecutor, executorFactory, syncNonTransactional, critialErrorListener, () -> false);
   }

   public PagingStoreFactoryNIO(final StorageManager storageManager,
                                final File directory,
                                final long syncTimeout,
                                final ScheduledExecutorService scheduledExecutor,
                                final ExecutorFactory executorFactory,
                                final boolean syncNonTransactional,
                                final IOCriticalErrorListener critialErrorListener,
                                final Supplier<Boolean> purgePageFolders) {
      this.storageManager = storageManager;
      this.directory = directory;
      this.executorFactory = executorFactory;
      this.syncNonTransactional = syncNonTransactional;
      this.scheduledExecutor = scheduledExecutor;
      this.syncTimeout = syncTimeout;
      this.critialErrorListener = critialErrorListener;
      this.purgePageFolders = purgePageFolders;
   }


   @Override
   public ScheduledExecutorService getScheduledExecutor() {
      return scheduledExecutor;
   }

   @Override
   public Executor newExecutor() {
      return executorFactory.getExecutor();
   }

   @Override
   public void stop() {
   }

   @Override
   public void injectMonitor(FileStoreMonitor monitor) throws Exception {
      monitor.addStore(this.directory);
   }

   @Override
   public PageCursorProvider newCursorProvider(PagingStore store,
                                               StorageManager storageManager,
                                               AddressSettings addressSettings,
                                               ArtemisExecutor executor) {
      return new PageCursorProviderImpl(store, storageManager);
   }

   @Override
   public synchronized PagingStore newStore(final SimpleString address, final AddressSettings settings) {
      return new PagingStoreImpl(address, scheduledExecutor, syncTimeout, pagingManager, storageManager, null, this, address, settings, executorFactory.getExecutor().setFair(true), syncNonTransactional, purgePageFolders);
   }

   @Override
   public synchronized SequentialFileFactory newFileFactory(final SimpleString address) throws Exception {

      String guid = UUIDGenerator.getInstance().generateStringUUID();

      SequentialFileFactory factory = newFileFactory(guid);

      factory.createDirs();

      File fileWithID = new File(directory, guid + File.separatorChar + PagingStoreFactoryNIO.ADDRESS_FILE);

      try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileWithID)))) {
         writer.write(address.toString());
         writer.newLine();
      }

      return factory;
   }

   @Override
   public synchronized void removeFileFactory(SequentialFileFactory fileFactory) throws Exception {
      File directory = fileFactory.getDirectory();
      if (directory.exists()) {
         FileUtil.deleteDirectory(directory);
      }
   }

   @Override
   public void setPagingManager(final PagingManager pagingManager) {
      this.pagingManager = pagingManager;
   }

   @Override
   public List<PagingStore> reloadStores(final HierarchicalRepository<AddressSettings> addressSettingsRepository) throws Exception {
      File[] files = directory.listFiles();

      if (files == null) {
         return Collections.<PagingStore>emptyList();
      } else {
         List<PagingStore> storesReturn = new ArrayList<>(files.length);

         for (File file : files) {

            final String guid = file.getName();

            final File addressFile = new File(file, PagingStoreFactoryNIO.ADDRESS_FILE);

            if (!addressFile.exists()) {

               // This means this folder is from a replication copy, nothing to worry about it, we just skip it
               if (!file.getName().contains(FileMoveManager.PREFIX)) {
                  ActiveMQServerLogger.LOGGER.pageStoreFactoryNoIdFile(file.toString(), PagingStoreFactoryNIO.ADDRESS_FILE);
               }
               continue;
            }

            String addressString;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(addressFile)))) {
               addressString = reader.readLine();
            }

            // there's no address listed in the file so we just skip it
            if (addressString == null) {
               ActiveMQServerLogger.LOGGER.emptyAddressFile(PagingStoreFactoryNIO.ADDRESS_FILE, file.toString());
               continue;
            }

            SimpleString address = SimpleString.of(addressString);

            SequentialFileFactory factory = newFileFactory(guid);

            AddressSettings settings = addressSettingsRepository.getMatch(address.toString());

            PagingStore store = new PagingStoreImpl(address, scheduledExecutor, syncTimeout, pagingManager, storageManager, factory, this, address, settings, executorFactory.getExecutor(), syncNonTransactional, purgePageFolders);

            storesReturn.add(store);
         }

         return storesReturn;
      }
   }

   protected SequentialFileFactory newFileFactory(final String directoryName) {

      return new NIOSequentialFileFactory(new File(directory, directoryName), false, critialErrorListener, 1);
   }
}
