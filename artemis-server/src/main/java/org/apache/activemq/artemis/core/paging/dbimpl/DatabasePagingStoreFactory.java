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

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.paging.PagingStoreFactory;
import org.apache.activemq.artemis.core.paging.cursor.PageCursorProvider;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.server.files.FileStoreMonitor;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.apache.activemq.artemis.utils.actors.ArtemisExecutor;

public class DatabasePagingStoreFactory implements PagingStoreFactory {

   private PagingManager pagingManager;

   private final StorageManager storageManager;

   private final ScheduledExecutorService scheduledExecutor;

   private final long syncTimeout;

   private final ExecutorFactory executorFactory;

   private final boolean syncNonTransactional;

   private final Function<SimpleString, AddressInfo> addressInfoProvider;

   public DatabasePagingStoreFactory(final StorageManager storageManager,
                                     final long syncTimeout,
                                     final ScheduledExecutorService scheduledExecutor,
                                     final ExecutorFactory executorFactory,
                                     final boolean syncNonTransactional,
                                     final Function<SimpleString, AddressInfo> addressInfoProvider) {
      this.storageManager = storageManager;
      this.syncTimeout = syncTimeout;
      this.scheduledExecutor = scheduledExecutor;
      this.executorFactory = executorFactory;
      this.syncNonTransactional = syncNonTransactional;
      this.addressInfoProvider = addressInfoProvider;
   }

   @Override
   public PagingStore newStore(final SimpleString address, final AddressSettings settings) {
      return new DatabasePagingStoreImpl(storageManager, pagingManager, address, settings, executorFactory.getExecutor().setFair(true));
   }

   @Override
   public PageCursorProvider newCursorProvider(PagingStore store,
                                               StorageManager storageManager,
                                               AddressSettings addressSettings,
                                               ArtemisExecutor executor) {
      throw new UnsupportedOperationException();
   }

   @Override
   public void stop() throws InterruptedException {
   }

   @Override
   public void setPagingManager(final PagingManager pagingManager) {
      this.pagingManager = pagingManager;
   }

   @Override
   public List<PagingStore> reloadStores(final HierarchicalRepository<AddressSettings> addressSettingsRepository) throws Exception {
      // TODO: reload paging stores from database
      return Collections.emptyList();
   }

   @Override
   public SequentialFileFactory newFileFactory(final SimpleString address) throws Exception {
      return null;
   }

   @Override
   public void removeFileFactory(SequentialFileFactory fileFactory) throws Exception {
   }

   @Override
   public void injectMonitor(FileStoreMonitor monitor) throws Exception {
   }

   @Override
   public ScheduledExecutorService getScheduledExecutor() {
      return scheduledExecutor;
   }

   @Override
   public Executor newExecutor() {
      return executorFactory.getExecutor();
   }
}
