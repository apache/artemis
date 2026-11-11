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
package org.apache.activemq.artemis.core.persistence.impl.journal;

import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.storage.DatabaseStorageConfiguration;
import org.apache.activemq.artemis.core.io.IOCriticalErrorListener;
import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.io.nio.NIOSequentialFileFactory;
import org.apache.activemq.artemis.core.persistence.OperationContext;
import org.apache.activemq.artemis.jdbc.store.drivers.JDBCConnectionProvider;
import org.apache.activemq.artemis.jdbc.store.file.JDBCSequentialFileFactory;
import org.apache.activemq.artemis.jdbc.store.journal.JDBCJournalImpl;
import org.apache.activemq.artemis.jdbc.store.sql.PropertySQLProvider;
import org.apache.activemq.artemis.jdbc.store.sql.SQLProvider;
import org.apache.activemq.artemis.utils.ArtemisCloseable;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.apache.activemq.artemis.utils.critical.CriticalAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JDBCJournalStorageManager extends JournalStorageManager {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public JDBCJournalStorageManager(Configuration config,
                                    CriticalAnalyzer analyzer,
                                    ExecutorFactory executorFactory,
                                    ExecutorFactory ioExecutorFactory,
                                    ScheduledExecutorService scheduledExecutorService) {
      super(config, analyzer, executorFactory, scheduledExecutorService, ioExecutorFactory);
   }

   public JDBCJournalStorageManager(final Configuration config,
                                    final CriticalAnalyzer analyzer,
                                    final ScheduledExecutorService scheduledExecutorService,
                                    final ExecutorFactory executorFactory,
                                    final ExecutorFactory ioExecutorFactory,
                                    final IOCriticalErrorListener criticalErrorListener) {
      super(config, analyzer, executorFactory, scheduledExecutorService, ioExecutorFactory, criticalErrorListener);
   }

   @Override
   protected synchronized void init(Configuration config, IOCriticalErrorListener criticalErrorListener) {
      try {
         final DatabaseStorageConfiguration dbConf = (DatabaseStorageConfiguration) config.getStoreConfiguration();
         final JDBCConnectionProvider connectionProvider = dbConf.getConnectionProvider();
         final JDBCJournalImpl bindingsJournal;
         final JDBCJournalImpl messageJournal;
         final JDBCSequentialFileFactory largeMessagesFactory;
         SQLProvider.Factory sqlProviderFactory = dbConf.getSqlProviderFactory();
         if (sqlProviderFactory == null) {
            sqlProviderFactory = new PropertySQLProvider.Factory(connectionProvider);
         }
         bindingsJournal = new JDBCJournalImpl(
                 connectionProvider,
                 sqlProviderFactory.create(dbConf.getBindingsTableName(), SQLProvider.DatabaseStoreType.BINDINGS_JOURNAL),
                 scheduledExecutorService,
                 executorFactory.getExecutor(),
                 criticalErrorListener, dbConf.getJdbcJournalSyncPeriodMillis());
         messageJournal = new JDBCJournalImpl(
                 connectionProvider,
                 sqlProviderFactory.create(dbConf.getMessageTableName(), SQLProvider.DatabaseStoreType.MESSAGE_JOURNAL),
                 scheduledExecutorService, executorFactory.getExecutor(),
                 criticalErrorListener,
                 dbConf.getJdbcJournalSyncPeriodMillis());
         largeMessagesFactory = new JDBCSequentialFileFactory(
                 connectionProvider,
                 sqlProviderFactory.create(dbConf.getLargeMessageTableName(), SQLProvider.DatabaseStoreType.LARGE_MESSAGE),
                 executorFactory.getExecutor(),
                 scheduledExecutorService,
                 dbConf.getJdbcJournalSyncPeriodMillis(),
                 criticalErrorListener);
         this.bindingsJournal = bindingsJournal;
         this.messageJournal = messageJournal;
         this.largeMessagesFactory = largeMessagesFactory;
         largeMessagesFactory.start();
      } catch (Exception e) {
         logger.warn(e.getMessage(), e);
         if (criticalErrorListener != null) {
            criticalErrorListener.onIOException(e, e.getMessage(), null);
         } else {
            throw new RuntimeException(e.getMessage(), e);
         }
      }
   }


   @Override
   public final void addBytesToLargeMessage(final SequentialFile file,
                                            final long messageId,
                                            final byte[] bytes) throws Exception {
      // we override the method as we must get the context and increment it
      // we also remove some of the replicator and retention stuff that does not make sense in JDBC
      try (ArtemisCloseable lock = closeableReadLock()) {
         OperationContext context = getContext(true);
         context.storeLineUp();
         file.writeDirect(ByteBuffer.wrap(bytes), false, context);
      }
   }

   @Override
   public final void addBytesToLargeMessage(final SequentialFile file,
                                            final long messageId,
                                            final ActiveMQBuffer bytes) throws Exception {
      // we override the method as we cannot set the position of the file in JDBC Files. otherwise multiple
      // scheduleWrites would get the content wrong, unless we synchronized (wait to completion) before the next writes.
      // we also remove some of the replicator and retention stuff that does not make sense in JDBC
      try (ArtemisCloseable lock = closeableReadLock()) {
         final byte[] copiedBytes = new byte[bytes.readableBytes()];
         bytes.readBytes(copiedBytes);
         addBytesToLargeMessage(file, messageId, copiedBytes);
      }
   }

   @Override
   public synchronized void stop(boolean ioCriticalError, boolean sendFailover) throws Exception {
      if (!started) {
         return;
      }

      if (!ioCriticalError) {
         performCachedLargeMessageDeletes();
         // Must call close to make sure last id is persisted
         if (journalLoaded && idGenerator != null) {
            idGenerator.persistCurrentID();
         }
      }

      final CountDownLatch latch = new CountDownLatch(1);
      executor.execute(latch::countDown);

      latch.await(30, TimeUnit.SECONDS);

      beforeStop();

      bindingsJournal.stop();
      messageJournal.stop();
      largeMessagesFactory.stop();

      journalLoaded = false;

      started = false;
   }

   @Override
   public ByteBuffer allocateDirectBuffer(int size) {
      return NIOSequentialFileFactory.allocateDirectByteBuffer(size);
   }

   @Override
   public void freeDirectBuffer(ByteBuffer buffer) {
   }
}
