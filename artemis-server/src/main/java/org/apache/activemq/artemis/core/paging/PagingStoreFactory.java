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

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.paging.cursor.PageCursorProvider;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.server.files.FileStoreMonitor;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.utils.actors.ArtemisExecutor;

/**
 * The integration point between the PagingManger and the File System (aka SequentialFiles)
 */
public interface PagingStoreFactory {

   PagingStore newStore(SimpleString address, AddressSettings addressSettings);

   PageCursorProvider newCursorProvider(PagingStore store,
                                        StorageManager storageManager,
                                        AddressSettings addressSettings,
                                        ArtemisExecutor executor);

   void stop() throws InterruptedException;

   void setPagingManager(PagingManager manager);

   List<PagingStore> reloadStores(HierarchicalRepository<AddressSettings> addressSettingsRepository) throws Exception;

   SequentialFileFactory newFileFactory(SimpleString address) throws Exception;

   void removeFileFactory(SequentialFileFactory fileFactory) throws Exception;

   void injectMonitor(FileStoreMonitor monitor) throws Exception;

   default ScheduledExecutorService getScheduledExecutor() {
      return null;
   }

   default Executor newExecutor() {
      return null;
   }



}
