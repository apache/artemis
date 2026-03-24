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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.storage.DatabaseStorageConfiguration;
import org.apache.activemq.artemis.core.io.IOCriticalErrorListener;
import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.paging.PagingStoreFactory;
import org.apache.activemq.artemis.core.paging.cursor.PageCursorProvider;
import org.apache.activemq.artemis.core.paging.cursor.impl.PageCursorProviderImpl;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.server.files.FileStoreMonitor;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jdbc.store.file.JDBCSequentialFile;
import org.apache.activemq.artemis.jdbc.store.file.JDBCSequentialFileFactory;
import org.apache.activemq.artemis.jdbc.store.sql.PropertySQLProvider;
import org.apache.activemq.artemis.jdbc.store.sql.SQLProvider;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.apache.activemq.artemis.utils.actors.ArtemisExecutor;

/**
 * Integration point between Paging and JDBC
 */
public class PagingStoreFactoryDatabase implements PagingStoreFactory {


   private static final String ADDRESS_FILE = "address.txt";

   private static final String DIRECTORY_NAME = "directory.txt";



   protected final boolean syncNonTransactional;

   private PagingManager pagingManager;

   private final ScheduledExecutorService scheduledExecutor;

   private final long syncTimeout;

   protected final StorageManager storageManager;

   private DatabaseStorageConfiguration dbConf;

   private ExecutorFactory executorFactory;

   private JDBCSequentialFileFactory pagingFactoryFileFactory;

   private final boolean readWholePage;

   @Override
   public ScheduledExecutorService getScheduledExecutor() {
      return scheduledExecutor;
   }

   @Override
   public Executor newExecutor() {
      return executorFactory.getExecutor();
   }

   private boolean started = false;

   private final IOCriticalErrorListener criticalErrorListener;

   private final Map<SequentialFileFactory, String> factoryToTableName;

   public PagingStoreFactoryDatabase(final DatabaseStorageConfiguration dbConf,
                                     final StorageManager storageManager,
                                     final long syncTimeout,
                                     final ScheduledExecutorService scheduledExecutor,
                                     final ExecutorFactory executorFactory,
                                     final boolean syncNonTransactional,
                                     final IOCriticalErrorListener criticalErrorListener) throws Exception {
      this(dbConf, storageManager, syncTimeout, scheduledExecutor, executorFactory, syncNonTransactional, criticalErrorListener, false);
   }

   public PagingStoreFactoryDatabase(final DatabaseStorageConfiguration dbConf,
                                     final StorageManager storageManager,
                                     final long syncTimeout,
                                     final ScheduledExecutorService scheduledExecutor,
                                     final ExecutorFactory executorFactory,
                                     final boolean syncNonTransactional,
                                     final IOCriticalErrorListener criticalErrorListener,
                                     final boolean readWholePage) throws Exception {
      this.storageManager = storageManager;
      this.executorFactory = executorFactory;
      this.syncNonTransactional = syncNonTransactional;
      this.scheduledExecutor = scheduledExecutor;
      this.syncTimeout = syncTimeout;
      this.dbConf = dbConf;
      this.criticalErrorListener = criticalErrorListener;
      this.factoryToTableName = new HashMap<>();
      this.readWholePage = readWholePage;
      start();
   }

   public synchronized void start() throws Exception {
      if (!started) {
         //fix to prevent page table names to be longer than 30 chars (upper limit for Oracle12c identifiers length)
         final String pageStoreTableNamePrefix = dbConf.getPageStoreTableName();
         if (pageStoreTableNamePrefix.length() > 10) {
            throw new IllegalStateException("The maximum name size for the page store table prefix is 10 characters: THE PAGING STORE CAN'T START");
         }
         SQLProvider.Factory sqlProviderFactory = dbConf.getSqlProviderFactory();
         if (sqlProviderFactory == null) {
            sqlProviderFactory = new PropertySQLProvider.Factory(dbConf.getConnectionProvider());
         }
         pagingFactoryFileFactory = new JDBCSequentialFileFactory(dbConf.getConnectionProvider(), sqlProviderFactory.create(pageStoreTableNamePrefix, SQLProvider.DatabaseStoreType.PAGE), executorFactory.getExecutor(), criticalErrorListener);
         pagingFactoryFileFactory.start();
         started = true;
      }
   }

   @Override
   public synchronized void stop() {
      if (started) {
         pagingFactoryFileFactory.stop();
         started = false;
      }
   }

   @Override
   public void injectMonitor(FileStoreMonitor monitor) throws Exception {
   }

   @Override
   public PageCursorProvider newCursorProvider(PagingStore store,
                                               StorageManager storageManager,
                                               AddressSettings addressSettings,
                                               ArtemisExecutor executor) {
      return new PageCursorProviderImpl(store, storageManager, executor, addressSettings.getPageCacheMaxSize(), readWholePage);
   }

   @Override
   public synchronized PagingStore newStore(final SimpleString address, final AddressSettings settings) {

      return new PagingStoreImpl(address, scheduledExecutor, syncTimeout, pagingManager, storageManager, null, this, address, settings, executorFactory.getExecutor(), syncNonTransactional);
   }

   @Override
   public synchronized SequentialFileFactory newFileFactory(final SimpleString address) throws Exception {
      String tableName = "" + storageManager.generateID();
      SequentialFileFactory factory = newFileFactory(tableName, true);
      factory.start();

      SequentialFile file = factory.createSequentialFile(PagingStoreFactoryDatabase.ADDRESS_FILE);
      file.open();

      ActiveMQBuffer buffer = ActiveMQBuffers.fixedBuffer(SimpleString.sizeofNullableString(address));
      buffer.writeSimpleString(address);
      file.write(buffer, true);
      file.close();
      return factory;
   }

   @Override
   public synchronized void removeFileFactory(SequentialFileFactory fileFactory) throws Exception {
      ((JDBCSequentialFileFactory)fileFactory).destroy();
      String tableName = factoryToTableName.remove(fileFactory);
      if (tableName != null) {
         SimpleString removeTableName = SimpleString.toSimpleString(tableName);
         JDBCSequentialFile directoryList = (JDBCSequentialFile) pagingFactoryFileFactory.createSequentialFile(DIRECTORY_NAME);
         directoryList.open();

         int size = ((Long) directoryList.size()).intValue();
         ActiveMQBuffer buffer = readActiveMQBuffer(directoryList, size);

         ActiveMQBuffer writeBuffer = ActiveMQBuffers.fixedBuffer(size);

         while (buffer.readableBytes() > 0) {
            SimpleString table = buffer.readSimpleString();
            if (!removeTableName.equals(table)) {
               writeBuffer.writeSimpleString(table);
            }
         }

         directoryList.write(writeBuffer, true, null, false);
         directoryList.close();
      }
   }

   @Override
   public void setPagingManager(final PagingManager pagingManager) {
      this.pagingManager = pagingManager;
   }

   @Override
   public synchronized List<PagingStore> reloadStores(final HierarchicalRepository<AddressSettings> addressSettingsRepository) throws Exception {
      // We assume the directory list < Integer.MAX_VALUE (this is only a list of addresses).
      JDBCSequentialFile directoryList = (JDBCSequentialFile) pagingFactoryFileFactory.createSequentialFile(DIRECTORY_NAME);
      directoryList.open();

      int size = ((Long) directoryList.size()).intValue();
      ActiveMQBuffer buffer = readActiveMQBuffer(directoryList, size);

      ArrayList<PagingStore> storesReturn = new ArrayList<>();

      while (buffer.readableBytes() > 0) {
         SimpleString table = buffer.readSimpleString();

         JDBCSequentialFileFactory factory = (JDBCSequentialFileFactory) newFileFactory(table.toString(), false);
         factory.start();

         JDBCSequentialFile addressFile = (JDBCSequentialFile) factory.createSequentialFile(ADDRESS_FILE);
         addressFile.open();

         size = ((Long) addressFile.size()).intValue();
         if (size == 0) {
            continue;
         }

         ActiveMQBuffer addrBuffer = readActiveMQBuffer(addressFile, size);
         SimpleString address = addrBuffer.readSimpleString();

         AddressSettings settings = addressSettingsRepository.getMatch(address.toString());

         PagingStore store = new PagingStoreImpl(address, scheduledExecutor, syncTimeout, pagingManager, storageManager, factory, this, address, settings, executorFactory.getExecutor(), syncNonTransactional);

         storesReturn.add(store);
      }
      directoryList.close();
      return storesReturn;
   }

   private synchronized SequentialFileFactory newFileFactory(final String directoryName, boolean writeToDirectory) throws Exception {
      JDBCSequentialFile directoryList = (JDBCSequentialFile) pagingFactoryFileFactory.createSequentialFile(DIRECTORY_NAME);
      directoryList.open();
      SimpleString simpleString = SimpleString.toSimpleString(directoryName);
      ActiveMQBuffer buffer = ActiveMQBuffers.fixedBuffer(simpleString.sizeof());
      buffer.writeSimpleString(simpleString);
      if (writeToDirectory) directoryList.write(buffer, true);
      directoryList.close();

      final SQLProvider sqlProvider;
      final SQLProvider.Factory sqlProviderFactory;
      if (dbConf.getSqlProviderFactory() != null) {
         sqlProviderFactory = dbConf.getSqlProviderFactory();
      } else {
         sqlProviderFactory = new PropertySQLProvider.Factory(dbConf.getConnectionProvider());
      }
      sqlProvider = sqlProviderFactory.create(getTableNameForGUID(directoryName), SQLProvider.DatabaseStoreType.PAGE);
      final JDBCSequentialFileFactory fileFactory = new JDBCSequentialFileFactory(dbConf.getConnectionProvider(), sqlProvider, executorFactory.getExecutor(), criticalErrorListener);
      factoryToTableName.put(fileFactory, directoryName);
      return fileFactory;
   }

   private String getTableNameForGUID(String guid) {
      return dbConf.getPageStoreTableName() + guid.replace("-", "");
   }

   private ActiveMQBuffer readActiveMQBuffer(SequentialFile file, int size) throws Exception {
      ByteBuffer byteBuffer = ByteBuffer.allocate(size);
      byteBuffer.mark();
      file.read(byteBuffer);
      byteBuffer.reset();

      ActiveMQBuffer buffer = ActiveMQBuffers.wrappedBuffer(byteBuffer);
      buffer.writerIndex(size);
      return buffer;
   }
}
