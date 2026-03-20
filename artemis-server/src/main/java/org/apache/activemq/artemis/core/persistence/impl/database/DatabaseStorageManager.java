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

package org.apache.activemq.artemis.core.persistence.impl.database;

import javax.transaction.xa.Xid;
import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.DivertConfiguration;
import org.apache.activemq.artemis.core.config.storage.DatabaseStorageConfiguration;
import org.apache.activemq.artemis.core.io.IOCriticalErrorListener;
import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.journal.EncodingSupport;
import org.apache.activemq.artemis.core.journal.IOCompletion;
import org.apache.activemq.artemis.core.journal.Journal;
import org.apache.activemq.artemis.core.journal.JournalLoadInformation;
import org.apache.activemq.artemis.core.journal.RecordInfo;
import org.apache.activemq.artemis.core.journal.StorageTX;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.paging.PageTransactionInfo;
import org.apache.activemq.artemis.core.paging.PagedMessage;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.paging.cursor.PagePosition;
import org.apache.activemq.artemis.core.paging.dbimpl.DatabasePagingManager;
import org.apache.activemq.artemis.core.paging.dbimpl.DatabasePagingStoreFactory;
import org.apache.activemq.artemis.core.persistence.AddressBindingInfo;
import org.apache.activemq.artemis.core.persistence.AddressQueueStatus;
import org.apache.activemq.artemis.core.persistence.GroupingInfo;
import org.apache.activemq.artemis.core.persistence.OperationContext;
import org.apache.activemq.artemis.core.persistence.Persister;
import org.apache.activemq.artemis.core.persistence.QueueBindingInfo;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.persistence.config.AbstractPersistedAddressSetting;
import org.apache.activemq.artemis.core.persistence.config.PersistedAddressSettingJSON;
import org.apache.activemq.artemis.core.persistence.config.PersistedBridgeConfiguration;
import org.apache.activemq.artemis.core.persistence.config.PersistedConnector;
import org.apache.activemq.artemis.core.persistence.config.PersistedDivertConfiguration;
import org.apache.activemq.artemis.core.persistence.config.PersistedKeyValuePair;
import org.apache.activemq.artemis.core.persistence.config.PersistedRole;
import org.apache.activemq.artemis.core.persistence.config.PersistedSecuritySetting;
import org.apache.activemq.artemis.core.persistence.config.PersistedUser;
import org.apache.activemq.artemis.core.persistence.impl.AbstractStorageManager;
import org.apache.activemq.artemis.core.persistence.impl.PageCountPending;
import org.apache.activemq.artemis.core.persistence.impl.database.browserIterator.DatabasePendingMessagesIterator;
import org.apache.activemq.artemis.core.persistence.impl.database.sequential.MemorySequentialFile;
import org.apache.activemq.artemis.core.persistence.impl.journal.AbstractJournalStorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.BatchingIDGenerator;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds;
import org.apache.activemq.artemis.core.persistence.impl.journal.LargeServerMessageImpl;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.AddressStatusEncoding;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.DuplicateIDEncoding;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.GroupingEncoding;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.LargeMessagePersister;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.PersistentAddressBindingEncoding;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.PersistentQueueBindingEncoding;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.QueueStatusEncoding;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.PostOffice;
import org.apache.activemq.artemis.core.postoffice.impl.LocalQueueBinding;
import org.apache.activemq.artemis.core.replication.ReplicationManager;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.LargeServerMessage;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.RouteContextList;
import org.apache.activemq.artemis.core.server.StorageMessageReader;
import org.apache.activemq.artemis.core.server.files.FileStoreMonitor;
import org.apache.activemq.artemis.core.server.group.impl.GroupBinding;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.impl.DatabaseStorageMessageReader;
import org.apache.activemq.artemis.core.server.impl.JournalLoader;
import org.apache.activemq.artemis.core.transaction.ResourceManager;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.core.transaction.impl.TransactionImpl;
import org.apache.activemq.artemis.spi.core.protocol.MessagePersister;
import org.apache.activemq.artemis.utils.ArtemisCloseable;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.apache.activemq.artemis.utils.IDGenerator;
import org.apache.activemq.artemis.utils.collections.CloseableIterator;
import org.apache.activemq.artemis.utils.critical.CriticalAnalyzer;
import org.apache.artemis.database.DatabaseProvider;
import org.apache.artemis.database.DatabaseTX;
import org.apache.artemis.database.data.DBData;
import org.apache.artemis.database.data.DeleteGenericData;
import org.apache.artemis.database.data.DeleteMessageData;
import org.apache.artemis.database.data.DeletePrepareTXData;
import org.apache.artemis.database.data.DeleteReferenceData;
import org.apache.artemis.database.data.GenericData;
import org.apache.artemis.database.data.MessageData;
import org.apache.artemis.database.data.MessageReferenceData;
import org.apache.artemis.database.data.PrepareTXData;
import org.apache.artemis.database.data.RollbackRefAckData;
import org.apache.artemis.database.data.TXStatus;
import org.apache.artemis.database.queries.AddressJDBCQuery;
import org.apache.artemis.database.queries.CountPagedMessageQuery;
import org.apache.artemis.database.queries.GenericDataJDBCQuery;
import org.apache.artemis.database.queries.MessagesJDBCQuery;
import org.apache.artemis.database.queries.QueueJDBCQuery;
import org.apache.artemis.database.queries.ReferencesJDBCQuery;
import org.apache.artemis.database.worker.DataManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseStorageManager extends AbstractStorageManager {
   @Override
   public PagingManager createPagingManager(ActiveMQServer server) throws Exception {
      DatabasePagingStoreFactory factory = new DatabasePagingStoreFactory(this, server.getConfiguration().getPageSyncTimeout(), scheduledExecutorService, executorFactory, server.getConfiguration().isJournalSyncNonTransactional(), server::getAddressInfo);
      DatabasePagingManager pagingManager = new DatabasePagingManager(factory, server.getAddressSettingsRepository(), server.getConfiguration().getGlobalMaxSize(), server.getConfiguration().getGlobalMaxMessages(), server.getConfiguration().getManagementAddress(), server);
      factory.setPagingManager(pagingManager);
      return pagingManager;
   }

   @Override
   public boolean flowControl(PagingStore pagingStore, Runnable block, Runnable release) {
      // The database paging store does not use a PageTimedWriter, so page-level
      // flow control is not active. Kept here for future use if database paging
      // gains its own write-side back-pressure.
      // if (pagingStore != null && pagingStore.flowControl(block, release)) {
      //    return true;
      // }
      return dataManager.flowControl(block, release);
   }

   @Override
   public boolean supportsDirectDeliver() {
      return false;
   }

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   // TODO: provide configuration for this
   final int batchSize = 1000;

   final Executor executorService;
   final Configuration configuration;
   DatabaseProvider databaseProvider;
   DatabaseStorageConfiguration databaseConfiguration;

   BatchingIDGenerator idGenerator;

   // the plan is to have many of these in a pool, for now while I bootstrap things I'm just having one
   DataManager dataManager;

   final ConcurrentMap<String, ConcurrentMap<String, PersistedKeyValuePair>> mapPersistedKeyValuePairs = new ConcurrentHashMap<>();

   public Configuration getConfig() {
      return configuration;
   }

   public DataManager getDataManager() {
      return dataManager;
   }

   public long getDatabaseReadIdleTimeout() {
      return databaseConfiguration.getDatabaseReadIdleTimeout();
   }

   @Override
   public void stop() throws Exception {
      super.stop();
      stop(false, true);
   }

   public DatabaseProvider getDatabaseProvider() {
      return databaseProvider;
   }

   public DatabaseStorageConfiguration getDatabaseConfiguration() {
      return databaseConfiguration;
   }

   @SuppressWarnings("unchecked")
   @Override
   public <T extends CloseableIterator<MessageReference>> T pendingMessagesIterator(Queue queue, int prefetch) {
      return (T) new DatabasePendingMessagesIterator(this, queue, prefetch);
   }

   @Override
   public synchronized void stop(boolean ioCriticalError, boolean sendFailover) throws Exception {
      idGenerator.stop();
      dataManager.flush();
      waitOnOperations();
      dataManager.close();
      databaseConfiguration.checkDatasource();
      super.stop(ioCriticalError, sendFailover);
   }

   public DatabaseStorageManager(Configuration configuration,
                                 CriticalAnalyzer analyzer,
                                 ExecutorFactory executorFactory,
                                 ExecutorFactory ioExecutorFactory,
                                 ScheduledExecutorService scheduledExecutorService,
                                 Executor executorService,
                                 IOCriticalErrorListener ioCriticalErrorListener) {
      super(analyzer, 1, executorFactory, scheduledExecutorService, ioExecutorFactory, ioCriticalErrorListener);
      this.configuration = configuration;
      this.executorService = executorService;
      this.idGenerator = new BatchingIDGenerator(0, Integer.MAX_VALUE, this);
   }

   @Override
   public void start() throws Exception {
      this.databaseConfiguration = (DatabaseStorageConfiguration) configuration.getStoreConfiguration();
      this.databaseProvider = databaseConfiguration.getDatabaseProvider();
      initSchema();
      super.start();
   }

   @Override
   public void persistIdGenerator() {
      idGenerator.persistCurrentID();
   }

   public JournalLoadInformation[] loadInternalOnly() throws Exception {
      return null;
   }

   @Override
   public Journal getMessageJournal() {
      throw new UnsupportedOperationException();
   }

   @Override
   public Journal getBindingsJournal() {
      throw new UnsupportedOperationException();
   }

   @Override
   public boolean addToPage(PagingStore store, Message msg, Transaction tx, RouteContextList listCtx) throws Exception {
      int credits;
      try (ArtemisCloseable closeable = closeableReadLock()) {
         credits = store.page(msg, tx, listCtx, null);
      }

      return credits >= 0;
   }

   private void initSchema() throws Exception {

      databaseProvider.createSchema();

      logger.info("Timeout:: {}", databaseConfiguration.getDatabaseFlushPeriodNanos());
      dataManager = new DataManager(scheduledExecutorService, executorFactory.getExecutor(), executorService, databaseConfiguration.getDatabaseFlushPeriodNanos(), databaseProvider, batchSize, databaseConfiguration.getDatabaseConnections(), databaseConfiguration.getDatabaseMaxReadConnections(), databaseConfiguration.isPageJoinFetch(), databaseConfiguration::getDatabaseMaxRetries, databaseConfiguration::getDatabaseRetryIntervalMillis, this::criticalError);
      dataManager.setMaxCredits(databaseConfiguration.getMaxPendingWrites());
      dataManager.start();

   }

   @Override
   public StorageTX generateTX(long tx) {
      return new DatabaseTX(tx);
   }

   public IDGenerator getIDGenerator() {
      return idGenerator;
   }

   @Override
   public long generateID() {
      return idGenerator.generateID();
   }

   @Override
   public long getCurrentID() {
      return idGenerator.getCurrentID();
   }

   @Override
   public void storeMapRecord(long id,
                              byte recordType,
                              Persister persister,
                              Object record,
                              boolean sync,
                              IOCompletion completionCallback) throws Exception {
      dataManager.storeGenericData(id, recordType, () -> encodeGenericRecord(persister, record), false, completionCallback);
   }

   @Override
   public void storeMapRecord(long id,
                              byte recordType,
                              Persister persister,
                              Object record,
                              boolean sync) throws Exception {
      dataManager.storeGenericData(id, recordType, () -> encodeGenericRecord(persister, record), false, getContext());
   }

   @Override
   public void deleteMapRecord(long id, boolean sync) throws Exception {
      dataManager.deleteGenericData(id, false, getContext());
   }

   @Override
   public void deleteMapRecordTx(long txid, long id) throws Exception {
      dataManager.deleteGenericData(id, false, getContext());
   }

   private static ActiveMQBuffer encodeGenericRecord(Persister persister, Object record) {
      int size = persister.getEncodeSize(record);
      ActiveMQBuffer buffer = ActiveMQBuffers.fixedBuffer(size);
      persister.encode(buffer, record);
      return buffer;
   }

   @Override
   public void storeMessage(Message message) throws Exception {
      boolean isCore = message instanceof CoreMessage;
      dataManager.storeMessage(message.getMessageID(), () -> encodeMessage(message), message.getMemoryEstimate(), message.isLargeMessage(), isCore, () -> getLargeBody(message), getContext());
   }

   public static ActiveMQBuffer getLargeBody(Message message) {
      if (message.isLargeMessage()) {
         LargeServerMessage largeServerMessage = (LargeServerMessage) message;
         return largeServerMessage.getLargeBody().getBodyBuffer();
      } else {
         return null;
      }
   }

   public static Message decodeMessage(long messageID, boolean isLarge, boolean isCore, Supplier<ActiveMQBuffer> encodeBufferSupplier, Supplier<ActiveMQBuffer> largeBodyBuffer, StorageManager storageManager) {
      if (isLarge && isCore) {
         MemorySequentialFile memorySequentialFile = new MemorySequentialFile("file-" + messageID, largeBodyBuffer.get());
         LargeServerMessageImpl largeServerMessage = new LargeServerMessageImpl((byte)0, messageID, storageManager, memorySequentialFile);
         LargeMessagePersister.getInstance().decode(encodeBufferSupplier.get(), largeServerMessage, null);
         return largeServerMessage;
      } else {
         Message message = MessagePersister.getInstance().decode(encodeBufferSupplier.get(), null, null, storageManager);
         if (message.isLargeMessage()) {
            MemorySequentialFile memorySequentialFile = new MemorySequentialFile("file-" + messageID, largeBodyBuffer.get());
            LargeServerMessage largeServerMessage = (LargeServerMessage) message;
            largeServerMessage.getLargeBody().replaceFile(memorySequentialFile);
         }
         message.setMessageID(messageID);
         return message;
      }
   }

   public static ActiveMQBuffer encodeMessage(Message message) {
      if (message.isLargeMessage() && message instanceof LargeServerMessageImpl) {
         LargeServerMessage largeServerMessage = (LargeServerMessage) message;
         int encodeSize = LargeMessagePersister.getInstance().getEncodeSize((LargeServerMessage) message);
         ActiveMQBuffer buffer = ActiveMQBuffers.fixedBuffer(encodeSize);
         LargeMessagePersister.getInstance().encode(buffer, largeServerMessage);
         return buffer;
      } else {
         int size = message.getPersister().getEncodeSize(message);
         ActiveMQBuffer buffer = ActiveMQBuffers.fixedBuffer(size);
         message.getPersister().encode(buffer, message);
         return buffer;
      }
   }

   @Override
   public void storeReference(long queueID, long messageID, boolean pendingDelivery, boolean last) throws Exception {
      dataManager.storeReference(messageID, queueID, pendingDelivery, null, getContext());
   }

   @Override
   public void deleteMessage(long messageID) throws Exception {
      logger.debug("DeleteMessage messgeID={}", messageID);
      dataManager.deleteMessage(messageID, getContext());
   }

   @Override
   public void updateScheduledDeliveryTime(MessageReference ref) throws Exception {
   }

   @Override
   public void storeDuplicateID(SimpleString address, byte[] duplID, long recordID) throws Exception {
      DuplicateIDEncoding encoding = new DuplicateIDEncoding(address, duplID);
      dataManager.storeGenericData(recordID, JournalRecordIds.DUPLICATE_ID, () -> encodeDuplicateID(encoding), false, getContext());
   }

   @Override
   public void deleteDuplicateID(long recordID) throws Exception {
      dataManager.deleteGenericData(recordID, false, getContext());
   }

   private static ActiveMQBuffer encodeDuplicateID(DuplicateIDEncoding encoding) {
      ActiveMQBuffer buffer = ActiveMQBuffers.fixedBuffer(encoding.getEncodeSize());
      encoding.encode(buffer);
      return buffer;
   }

   @Override
   public void storeAcknowledge(long queueID, long messageID) throws Exception {
      dataManager.ackMessage(queueID, messageID, getContext());
   }

   @Override
   public void storeAcknowledgeTransactional(Transaction tx, long queueID, long messageID) throws Exception {
      dataManager.ackMessage(tx.getStorageTx(), tx.getID(), queueID, messageID, getContext());
   }

   @Override
   public void storeCursorAcknowledge(long queueID, PagePosition position) throws Exception {
   }

   @Override
   public void storeMessageTransactional(Transaction tx, Message message) throws Exception {
      boolean isCore = message instanceof CoreMessage;
      dataManager.storeMessage(tx.getStorageTx(), message.getMessageID(), () -> encodeMessage(message), message.getMemoryEstimate(), message.isLargeMessage(), isCore, () -> getLargeBody(message), getContext());
   }

   @Override
   public void storePageTransaction(Transaction tx, PageTransactionInfo pageTransaction) throws Exception {
   }

   @Override
   public void updatePageTransaction(Transaction tx,
                                     PageTransactionInfo pageTransaction,
                                     int depages) throws Exception {
   }

   @Override
   public void storeReferenceTransactional(Transaction tx, long queueID, long messageID, boolean pendingDelivery) throws Exception {
      dataManager.storeReference(tx.getStorageTx(), messageID, queueID, pendingDelivery, tx.getID(), getContext());
   }

   @Override
   public void deletePendingLargeMessage(long recordID) throws Exception {
   }

   @Override
   public void storeCursorAcknowledgeTransactional(Transaction tx,
                                                   long queueID,
                                                   PagePosition position) throws Exception {
   }

   @Override
   public void storePageCompleteTransactional(Transaction tx, long queueID, PagePosition position) throws Exception {
   }

   @Override
   public void deletePageComplete(long ackID) throws Exception {
   }

   @Override
   public void deleteCursorAcknowledgeTransactional(Transaction tx, long ackID) throws Exception {
   }

   @Override
   public void deleteCursorAcknowledge(long ackID) throws Exception {
   }

   @Override
   public long storeHeuristicCompletion(Xid xid, boolean isCommit) throws Exception {
      // TBD XA
      return 0L;
   }

   @Override
   public void deleteHeuristicCompletion(long id) throws Exception {
      // TBD XA
   }

   @Override
   public void deletePageTransactional(long recordID) throws Exception {
   }

   @Override
   public void updateScheduledDeliveryTimeTransactional(Transaction tx, MessageReference ref) throws Exception {
      // TBD
   }

   @Override
   public void prepare(Transaction tx, Xid xid) throws Exception {
      if (xid == null) {
         throw new IllegalArgumentException("xid is required");
      }
      OperationContext context = getContext();
      context.storeLineUp();
      dataManager.storePrepareTx(tx.getStorageTx(), xid, context);
      tx.getStorageTx().setContext(context);
      dataManager.storeTX(tx.getStorageTx());
   }

   @Override
   public void commit(Transaction tx) throws Exception {
      commit(tx, true);
   }

   @Override
   public void commitBindings(Transaction tx) throws Exception {
      dataManager.storeTX(tx.getStorageTx());
   }

   @Override
   public void rollbackBindings(Transaction tx) throws Exception {
      // no op
   }

   @Override
   public void commit(Transaction tx, boolean lineUpContext) throws Exception {
      tx.getStorageTx().beforeIO();
      OperationContext context = getContext();
      context.storeLineUp();
      tx.getStorageTx().setContext(context);
      dataManager.storeTX(tx.getStorageTx());
   }

   @Override
   public void asyncCommit(Transaction tx) throws Exception {
      commit(tx, false);
   }

   @Override
   public void rollback(Transaction tx) throws Exception {
      DatabaseTX databaseTX = (DatabaseTX) tx.getStorageTx();

      if (databaseTX.getStatus() == TXStatus.prepared) {
         List<DBData> rollbackOperations = new ArrayList<>();
         for (DBData data : databaseTX.dataList) {
            if (data instanceof MessageData msg) {
               rollbackOperations.add(new DeleteMessageData(msg.messageID, getContext()));
            } else if (data instanceof MessageReferenceData ref) {
               rollbackOperations.add(new DeleteReferenceData(ref.queueID, ref.messageID, getContext()));
            } else if (data instanceof PrepareTXData ptx) {
               rollbackOperations.add(new DeletePrepareTXData(ptx.getTx().getId(), getContext()));
            } else if (data instanceof DeleteReferenceData refDelete) { // the acks
               rollbackOperations.add(new RollbackRefAckData(refDelete.queueID, refDelete.messageID, getContext()));
            } else if (data instanceof GenericData genericData) { // Duplicate detection, hashMaps...
               rollbackOperations.add(new DeleteGenericData(genericData.id, false, getContext()));
            }
         }
         dataManager.flushData(rollbackOperations);
      }
   }

   @Override
   public void storeDuplicateIDTransactional(Transaction tx,
                                             SimpleString address,
                                             byte[] duplID,
                                             long recordID) throws Exception {
      DuplicateIDEncoding encoding = new DuplicateIDEncoding(address, duplID);
      dataManager.storeGenericData(tx.getStorageTx(), recordID, JournalRecordIds.DUPLICATE_ID, () -> encodeDuplicateID(encoding), false, getContext());
   }

   @Override
   public void updateDuplicateIDTransactional(Transaction tx,
                                              SimpleString address,
                                              byte[] duplID,
                                              long recordID) throws Exception {
      DuplicateIDEncoding encoding = new DuplicateIDEncoding(address, duplID);
      dataManager.updateGenericData(tx.getStorageTx(), recordID, tx.getID(), () -> encodeDuplicateID(encoding), getContext());
   }

   @Override
   public void deleteDuplicateIDTransactional(Transaction tx, long recordID) throws Exception {
      dataManager.deleteGenericData(tx.getStorageTx(), recordID, false, getContext());
   }

   @Override
   public void updateDeliveryCount(MessageReference ref) throws Exception {
      // TBD
   }

   @Override
   public void storeAddressSetting(PersistedAddressSettingJSON addressSetting) throws Exception {
      long recordID = generateID();
      addressSetting.setStoreId(recordID);
      AbstractPersistedAddressSetting old = mapPersistedAddressSettings.put(addressSetting.getName(), addressSetting);
      if (old != null) {
         long txID = generateID();
         StorageTX storageTX = generateTX(txID);
         dataManager.deleteBindingsGenericData(storageTX, old.getStoreId(), getContext());
         dataManager.storeBindingsGenericData(storageTX, recordID, addressSetting.getRecordType(), () -> encodeEncodingSupport(addressSetting), getContext());
         dataManager.storeTX(storageTX);
      } else {
         dataManager.storeBindingsGenericData(recordID, addressSetting.getRecordType(), () -> encodeEncodingSupport(addressSetting), getContext());
      }
   }

   @Override
   public List<AbstractPersistedAddressSetting> recoverAddressSettings() throws Exception {
      return new java.util.ArrayList<>(mapPersistedAddressSettings.values());
   }

   @Override
   public AbstractPersistedAddressSetting recoverAddressSettings(SimpleString address) {
      return mapPersistedAddressSettings.get(address.toString());
   }

   @Override
   public List<PersistedSecuritySetting> recoverSecuritySettings() throws Exception {
      return new java.util.ArrayList<>(mapPersistedSecuritySettings.values());
   }

   @Override
   public void storeSecuritySetting(PersistedSecuritySetting persistedRoles) throws Exception {
      long recordID = generateID();
      persistedRoles.setStoreId(recordID);
      PersistedSecuritySetting old = mapPersistedSecuritySettings.put(persistedRoles.getName(), persistedRoles);
      if (old != null) {
         long txID = generateID();
         StorageTX storageTX = generateTX(txID);
         dataManager.deleteBindingsGenericData(storageTX, old.getStoreId(), getContext());
         dataManager.storeBindingsGenericData(storageTX, recordID, persistedRoles.getRecordType(), () -> encodeEncodingSupport(persistedRoles), getContext());
         dataManager.storeTX(storageTX);
      } else {
         dataManager.storeBindingsGenericData(recordID, persistedRoles.getRecordType(), () -> encodeEncodingSupport(persistedRoles), getContext());
      }
   }

   @Override
   public void storeDivertConfiguration(PersistedDivertConfiguration persistedDivertConfiguration) throws Exception {
      long recordID = generateID();
      persistedDivertConfiguration.setStoreId(recordID);
      PersistedDivertConfiguration old = mapPersistedDivertConfigurations.put(persistedDivertConfiguration.getName(), persistedDivertConfiguration);
      if (old != null) {
         long txID = generateID();
         StorageTX storageTX = generateTX(txID);
         dataManager.deleteBindingsGenericData(storageTX, old.getStoreId(), getContext());
         dataManager.storeBindingsGenericData(storageTX, recordID, persistedDivertConfiguration.getRecordType(), () -> encodeEncodingSupport(persistedDivertConfiguration), getContext());
         dataManager.storeTX(storageTX);
      } else {
         dataManager.storeBindingsGenericData(recordID, persistedDivertConfiguration.getRecordType(), () -> encodeEncodingSupport(persistedDivertConfiguration), getContext());
      }
   }

   @Override
   public void deleteDivertConfiguration(String divertName) throws Exception {
      PersistedDivertConfiguration old = mapPersistedDivertConfigurations.remove(divertName);
      if (old != null) {
         dataManager.deleteBindingsGenericData(old.getStoreId(), getContext());
      }
   }

   @Override
   public List<PersistedDivertConfiguration> recoverDivertConfigurations() {
      return new java.util.ArrayList<>(mapPersistedDivertConfigurations.values());
   }

   @Override
   public DivertConfiguration getDivertConfiguration(String name) {
      PersistedDivertConfiguration persistedDivertConfiguration = mapPersistedDivertConfigurations.get(name);
      if (persistedDivertConfiguration != null) {
         return new DivertConfiguration(persistedDivertConfiguration.getDivertConfiguration());
      } else {
         return null;
      }
   }

   @Override
   public void storeBridgeConfiguration(PersistedBridgeConfiguration persistedBridgeConfiguration) throws Exception {
      long recordID = generateID();
      persistedBridgeConfiguration.setStoreId(recordID);
      PersistedBridgeConfiguration old = mapPersistedBridgeConfigurations.put(persistedBridgeConfiguration.getName(), persistedBridgeConfiguration);
      if (old != null) {
         long txID = generateID();
         StorageTX storageTX = generateTX(txID);
         dataManager.deleteBindingsGenericData(storageTX, old.getStoreId(), getContext());
         dataManager.storeBindingsGenericData(storageTX, recordID, persistedBridgeConfiguration.getRecordType(), () -> encodeEncodingSupport(persistedBridgeConfiguration), getContext());
         dataManager.storeTX(storageTX);
      } else {
         dataManager.storeBindingsGenericData(recordID, persistedBridgeConfiguration.getRecordType(), () -> encodeEncodingSupport(persistedBridgeConfiguration), getContext());
      }
   }

   @Override
   public void deleteBridgeConfiguration(String bridgeName) throws Exception {
      PersistedBridgeConfiguration old = mapPersistedBridgeConfigurations.remove(bridgeName);
      if (old != null) {
         dataManager.deleteBindingsGenericData(old.getStoreId(), getContext());
      }
   }

   @Override
   public List<PersistedBridgeConfiguration> recoverBridgeConfigurations() {
      return new java.util.ArrayList<>(mapPersistedBridgeConfigurations.values());
   }

   @Override
   public void storeConnector(PersistedConnector persistedConnector) throws Exception {
      long recordID = generateID();
      persistedConnector.setStoreId(recordID);
      PersistedConnector old = mapPersistedConnectors.put(persistedConnector.getName(), persistedConnector);
      if (old != null) {
         long txID = generateID();
         StorageTX storageTX = generateTX(txID);
         dataManager.deleteBindingsGenericData(storageTX, old.getStoreId(), getContext());
         dataManager.storeBindingsGenericData(storageTX, recordID, persistedConnector.getRecordType(), () -> encodeEncodingSupport(persistedConnector), getContext());
         dataManager.storeTX(storageTX);
      } else {
         dataManager.storeBindingsGenericData(recordID, persistedConnector.getRecordType(), () -> encodeEncodingSupport(persistedConnector), getContext());
      }
   }

   @Override
   public void deleteConnector(String connectorName) throws Exception {
      PersistedConnector old = mapPersistedConnectors.remove(connectorName);
      if (old != null) {
         dataManager.deleteBindingsGenericData(old.getStoreId(), getContext());
      }
   }

   @Override
   public List<PersistedConnector> recoverConnectors() {
      return new java.util.ArrayList<>(mapPersistedConnectors.values());
   }

   @Override
   public void storeUser(PersistedUser persistedUser) throws Exception {
      long recordID = generateID();
      persistedUser.setStoreId(recordID);
      PersistedUser old = mapPersistedUsers.put(persistedUser.getName(), persistedUser);
      if (old != null) {
         long txID = generateID();
         StorageTX storageTX = generateTX(txID);
         dataManager.deleteBindingsGenericData(storageTX, old.getStoreId(), getContext());
         dataManager.storeBindingsGenericData(storageTX, recordID, persistedUser.getRecordType(), () -> encodeEncodingSupport(persistedUser), getContext());
         dataManager.storeTX(storageTX);
      } else {
         dataManager.storeBindingsGenericData(recordID, persistedUser.getRecordType(), () -> encodeEncodingSupport(persistedUser), getContext());
      }
   }

   @Override
   public void deleteUser(String username) throws Exception {
      PersistedUser old = mapPersistedUsers.remove(username);
      if (old != null) {
         dataManager.deleteBindingsGenericData(old.getStoreId(), getContext());
      }
   }

   @Override
   public Map<String, PersistedUser> getPersistedUsers() {
      return mapPersistedUsers;
   }

   @Override
   public void storeRole(PersistedRole persistedRole) throws Exception {
      long recordID = generateID();
      persistedRole.setStoreId(recordID);
      PersistedRole old = mapPersistedRoles.put(persistedRole.getName(), persistedRole);
      if (old != null) {
         long txID = generateID();
         StorageTX storageTX = generateTX(txID);
         dataManager.deleteBindingsGenericData(storageTX, old.getStoreId(), getContext());
         dataManager.storeBindingsGenericData(storageTX, recordID, persistedRole.getRecordType(), () -> encodeEncodingSupport(persistedRole), getContext());
         dataManager.storeTX(storageTX);
      } else {
         dataManager.storeBindingsGenericData(recordID, persistedRole.getRecordType(), () -> encodeEncodingSupport(persistedRole), getContext());
      }
   }

   @Override
   public void deleteRole(String username) throws Exception {
      PersistedRole old = mapPersistedRoles.remove(username);
      if (old != null) {
         dataManager.deleteBindingsGenericData(old.getStoreId(), getContext());
      }
   }

   @Override
   public Map<String, PersistedRole> getPersistedRoles() {
      return mapPersistedRoles;
   }

   @Override
   public void storeKeyValuePair(PersistedKeyValuePair persistedKeyValuePair) throws Exception {
      long recordID = generateID();
      persistedKeyValuePair.setStoreId(recordID);
      PersistedKeyValuePair old = insertPersistedKeyValuePair(persistedKeyValuePair);
      if (old != null) {
         long txID = generateID();
         StorageTX storageTX = generateTX(txID);
         dataManager.deleteGenericData(storageTX, old.getStoreId(), false, getContext());
         dataManager.storeGenericData(storageTX, recordID, persistedKeyValuePair.getRecordType(), () -> encodeEncodingSupport(persistedKeyValuePair), false, getContext());
         dataManager.storeTX(storageTX);
      } else {
         dataManager.storeGenericData(recordID, persistedKeyValuePair.getRecordType(), () -> encodeEncodingSupport(persistedKeyValuePair), false, getContext());
      }
   }

   @Override
   public void deleteKeyValuePair(String mapId, String key) throws Exception {
      Map<String, PersistedKeyValuePair> persistedKeyValuePairs = mapPersistedKeyValuePairs.get(mapId);
      if (persistedKeyValuePairs != null) {
         PersistedKeyValuePair old = persistedKeyValuePairs.remove(key);
         if (old != null) {
            dataManager.deleteGenericData(old.getStoreId(), false, getContext());
         }
      }
   }

   @Override
   public Map<String, PersistedKeyValuePair> getPersistedKeyValuePairs(String mapId) {
      Map<String, PersistedKeyValuePair> persistedKeyValuePairs = mapPersistedKeyValuePairs.get(mapId);
      return persistedKeyValuePairs != null ? new HashMap<>(persistedKeyValuePairs) : new HashMap<>();
   }

   private PersistedKeyValuePair insertPersistedKeyValuePair(PersistedKeyValuePair keyValuePair) {
      Map<String, PersistedKeyValuePair> persistedKeyValuePairs = mapPersistedKeyValuePairs.get(keyValuePair.getMapId());
      if (persistedKeyValuePairs == null) {
         ConcurrentMap<String, PersistedKeyValuePair> newMap = new ConcurrentHashMap<>();
         Map<String, PersistedKeyValuePair> existingMap = mapPersistedKeyValuePairs.putIfAbsent(keyValuePair.getMapId(), newMap);
         persistedKeyValuePairs = Objects.requireNonNullElse(existingMap, newMap);
      }
      return persistedKeyValuePairs.put(keyValuePair.getKey(), keyValuePair);
   }

   @Override
   public void storeID(long journalID, long id) throws Exception {
      dataManager.storeBindingsGenericData(journalID, JournalRecordIds.ID_COUNTER_RECORD, () -> encodeID(id), getContext());
      waitOnOperations(10_000);
   }

   @Override
   public void deleteID(long journalD) throws Exception {
      dataManager.deleteBindingsGenericData(journalD, getContext());
   }

   private static ActiveMQBuffer encodeEncodingSupport(EncodingSupport encoding) {
      ActiveMQBuffer buffer = ActiveMQBuffers.fixedBuffer(encoding.getEncodeSize());
      encoding.encode(buffer);
      return buffer;
   }

   private static ActiveMQBuffer encodeID(long id) {
      ActiveMQBuffer buffer = ActiveMQBuffers.fixedBuffer(Long.BYTES);
      buffer.writeLong(id);
      return buffer;
   }

   public static Object describeGenericData(long id, byte recordType, ActiveMQBuffer buffer) {
      if (buffer == null) {
         return null;
      }
      switch (recordType) {
         case JournalRecordIds.ADDRESS_SETTING_RECORD_JSON:
            return AbstractJournalStorageManager.newPersistedConfigurationEncoding(PersistedAddressSettingJSON.class, id, buffer);
         case JournalRecordIds.SECURITY_SETTING_RECORD:
            return AbstractJournalStorageManager.newPersistedConfigurationEncoding(PersistedSecuritySetting.class, id, buffer);
         case JournalRecordIds.DIVERT_RECORD:
            return AbstractJournalStorageManager.newPersistedConfigurationEncoding(PersistedDivertConfiguration.class, id, buffer);
         case JournalRecordIds.BRIDGE_RECORD:
            return AbstractJournalStorageManager.newPersistedConfigurationEncoding(PersistedBridgeConfiguration.class, id, buffer);
         case JournalRecordIds.CONNECTOR_RECORD:
            return AbstractJournalStorageManager.newPersistedConfigurationEncoding(PersistedConnector.class, id, buffer);
         case JournalRecordIds.USER_RECORD:
            return AbstractJournalStorageManager.newPersistedConfigurationEncoding(PersistedUser.class, id, buffer);
         case JournalRecordIds.ROLE_RECORD:
            return AbstractJournalStorageManager.newPersistedConfigurationEncoding(PersistedRole.class, id, buffer);
         case JournalRecordIds.GROUP_RECORD:
            return AbstractJournalStorageManager.newGroupEncoding(id, buffer);
         case JournalRecordIds.QUEUE_STATUS_RECORD:
            return AbstractJournalStorageManager.newQueueStatusEncoding(id, buffer);
         case JournalRecordIds.ADDRESS_STATUS_RECORD:
            return AbstractJournalStorageManager.newAddressStatusEncoding(id, buffer);
         case JournalRecordIds.KEY_VALUE_PAIR_RECORD: {
            PersistedKeyValuePair kvp = new PersistedKeyValuePair();
            kvp.setStoreId(id);
            kvp.decode(buffer);
            return kvp;
         }
         case JournalRecordIds.DUPLICATE_ID: {
            DuplicateIDEncoding encoding = new DuplicateIDEncoding();
            encoding.decode(buffer);
            return encoding;
         }
         case JournalRecordIds.ID_COUNTER_RECORD: {
            BatchingIDGenerator.IDCounterEncoding encoding = new BatchingIDGenerator.IDCounterEncoding();
            encoding.decode(buffer);
            return encoding;
         }
         default:
            return null;
      }
   }

   @Override
   public void deleteAddressSetting(SimpleString addressMatch) throws Exception {
      AbstractPersistedAddressSetting old = mapPersistedAddressSettings.remove(addressMatch.toString());
      if (old != null) {
         dataManager.deleteBindingsGenericData(old.getStoreId(), getContext());
      }
   }

   @Override
   public void deleteSecuritySetting(SimpleString addressMatch) throws Exception {
      PersistedSecuritySetting old = mapPersistedSecuritySettings.remove(addressMatch.toString());
      if (old != null) {
         dataManager.deleteBindingsGenericData(old.getStoreId(), getContext());
      }
   }

   @Override
   public JournalLoadInformation loadBindingJournal(List<QueueBindingInfo> queueBindingInfos,
                                                    List<GroupingInfo> groupingInfos,
                                                    List<AddressBindingInfo> addressBindingInfos) throws Exception {
      try (Connection connection = this.databaseProvider.getConnection()) {
         Map<Long, PersistentAddressBindingEncoding> mapAddressBindings = new HashMap<>();
         AddressJDBCQuery addressQuery = new AddressJDBCQuery(databaseProvider, connection);
         addressQuery.query(data -> {
            // TODO: add internal and auto-create to the query
            PersistentAddressBindingEncoding info = new PersistentAddressBindingEncoding();
            info.setId(data.id);
            info.setName(SimpleString.of(data.address));

            if (data.isMulticast) {
               info.getRoutingTypes().add(RoutingType.MULTICAST);
            }

            if (data.isAnycast) {
               info.getRoutingTypes().add(RoutingType.ANYCAST);
            }

            addressBindingInfos.add(info);
            mapAddressBindings.put(data.id, info);
         });

         Map<Long, PersistentQueueBindingEncoding> mapBindings = new HashMap<>();
         QueueJDBCQuery queueQuery = new QueueJDBCQuery(databaseProvider, connection);
         queueQuery.query(data -> {
            // TODO: add internal and auto-create to the query
            QueueConfiguration queueConfiguration = data.toQueueConfiguration();
            PersistentQueueBindingEncoding queueBindingEncoding = new PersistentQueueBindingEncoding(queueConfiguration);
            queueBindingInfos.add(queueBindingEncoding);
            mapBindings.put(queueConfiguration.getId(), queueBindingEncoding);
         });

         GenericDataJDBCQuery bindingsGenericQuery = new GenericDataJDBCQuery(databaseProvider, connection, databaseProvider.getSqlProvider().getConfigData(), true);
         bindingsGenericQuery.query(data -> {
            Object decoded = describeGenericData(data.id, data.recordType, data.dataSupplier != null ? data.dataSupplier.get() : null);
            if (decoded instanceof BatchingIDGenerator.IDCounterEncoding counterEncoding) {
               idGenerator.loadState(data.id, counterEncoding.id);
            } else if (decoded instanceof PersistedAddressSettingJSON setting) {
               mapPersistedAddressSettings.put(setting.getName(), setting);
            } else if (decoded instanceof PersistedSecuritySetting setting) {
               mapPersistedSecuritySettings.put(setting.getName(), setting);
            } else if (decoded instanceof PersistedDivertConfiguration divert) {
               mapPersistedDivertConfigurations.put(divert.getName(), divert);
            } else if (decoded instanceof PersistedBridgeConfiguration bridge) {
               mapPersistedBridgeConfigurations.put(bridge.getName(), bridge);
            } else if (decoded instanceof PersistedConnector connector) {
               mapPersistedConnectors.put(connector.getName(), connector);
            } else if (decoded instanceof PersistedUser user) {
               mapPersistedUsers.put(user.getName(), user);
            } else if (decoded instanceof PersistedRole role) {
               mapPersistedRoles.put(role.getName(), role);
            } else if (decoded instanceof GroupingEncoding encoding) {
               groupingInfos.add(encoding);
            } else if (decoded instanceof QueueStatusEncoding statusEncoding) {
               PersistentQueueBindingEncoding queueBindingEncoding = mapBindings.get(statusEncoding.queueID);
               if (queueBindingEncoding != null) {
                  queueBindingEncoding.addQueueStatusEncoding(statusEncoding);
               } else {
                  ActiveMQServerLogger.LOGGER.infoNoQueueWithID(statusEncoding.queueID, statusEncoding.getId());
                  try {
                     deleteQueueStatus(statusEncoding.getId());
                  } catch (Exception e) {
                     logger.warn(e.getMessage(), e);
                  }
               }
            } else if (decoded instanceof AddressStatusEncoding statusEncoding) {
               PersistentAddressBindingEncoding addressBindingEncoding = mapAddressBindings.get(statusEncoding.getAddressId());
               if (addressBindingEncoding != null) {
                  addressBindingEncoding.setAddressStatusEncoding(statusEncoding);
               } else {
                  ActiveMQServerLogger.LOGGER.infoNoAddressWithID(statusEncoding.getAddressId(), statusEncoding.getId());
                  try {
                     deleteAddressStatus(statusEncoding.getId());
                  } catch (Exception e) {
                     logger.warn(e.getMessage(), e);
                  }
               }
            }
         });

         return null;

      }
   }

   @Override
   public JournalLoadInformation loadMessageJournal(PostOffice postOffice,
                                                    PagingManager pagingManager,
                                                    ResourceManager resourceManager,
                                                    Map<Long, QueueBindingInfo> queueInfos,
                                                    Map<SimpleString, List<Pair<byte[], Long>>> duplicateIDMap,
                                                    Set<Pair<Long, Long>> pendingLargeMessages,
                                                    Set<Long> storedLargeMessages,
                                                    List<PageCountPending> pendingNonTXPageCounter,
                                                    JournalLoader journalLoader,
                                                    List<Consumer<RecordInfo>> journalRecordsListener) throws Exception {

      Map<Long, Message> loadedMessages = new HashMap<>();
      try (Connection connection = this.databaseProvider.getConnection()) {
         {
            MessagesJDBCQuery query = new MessagesJDBCQuery(databaseProvider, connection);
            logger.info("Querying messages");
            query.query(data -> {
               loadedMessages.put(data.messageID, decodeMessage(data.messageID, data.isLarge, data.isCore, data.messageBufferSupplier, data.largeBodySupplier, DatabaseStorageManager.this));
            });
         }

         {
            CountPagedMessageQuery query = new CountPagedMessageQuery(databaseProvider, connection);
            query.query(true, d -> {
               Queue queue = postOffice.findQueue(d.queueId());
               if (queue == null) {
                  ActiveMQServerLogger.LOGGER.queueNotFoundOnDatabaseReload(d.queueId());
                  return;
               }

               StorageMessageReader storageMessageReader = queue.getStorageMessageReader();
               assert storageMessageReader instanceof DatabaseStorageMessageReader;

               storageMessageReader.addPending(d.msgCount(), d.memEstimate());
            });
         }

         logger.info("Querying references");
         ReferencesJDBCQuery referencesQuery = new ReferencesJDBCQuery(databaseProvider, connection);
         referencesQuery.query(d -> {
            Message message = loadedMessages.get(d.messageID);
            if (message != null) {
               try {
                  journalLoader.handleJDBCAdd(message, d);
               } catch (Exception e) {
                  // TODO-IMPORTANT Critical Error?
                  logger.warn(e.getMessage(), e);
               }
            }
         });

         handleOrphanedMessages(connection);

         GenericDataJDBCQuery genericQuery = new GenericDataJDBCQuery(databaseProvider, connection, databaseProvider.getSqlProvider().getBrokerData(), false);
         genericQuery.query(data -> {
            Object decoded = describeGenericData(data.id, data.recordType, data.dataSupplier != null ? data.dataSupplier.get() : null);
            if (decoded instanceof PersistedKeyValuePair kvp) {
               insertPersistedKeyValuePair(kvp);
            } else if (decoded instanceof DuplicateIDEncoding encoding) {
               List<Pair<byte[], Long>> ids = duplicateIDMap.get(encoding.address);
               if (ids == null) {
                  ids = new ArrayList<>();
                  duplicateIDMap.put(encoding.address, ids);
               }
               ids.add(new Pair<>(encoding.duplID, data.id));
            } else if (decoded == null) {
               if (journalRecordsListener != null) {
                  byte[] recordData;
                  if (data.dataSupplier != null) {
                     ActiveMQBuffer buffer = data.dataSupplier.get();
                     recordData = new byte[buffer.readableBytes()];
                     buffer.readBytes(recordData);
                  } else {
                     recordData = new byte[0];
                  }
                  RecordInfo recordInfo = new RecordInfo(data.id, data.recordType, recordData, false, false, (short) 0);
                  journalRecordsListener.forEach(f -> f.accept(recordInfo));
               }
            }
         });

         PreparedTXLoader loader = new PreparedTXLoader();
         Map<Long, Queue> queues = new HashMap<>();
         for (Long queueID : queueInfos.keySet()) {
            Queue queue = postOffice.findQueue(queueID);
            if (queue != null) {
               queues.put(queueID, queue);
            }
         }

         Collection<TransactionImpl> preparedTransactions = loader.loadPreparedTransactions(databaseProvider, connection, this, postOffice, queues);
         for (TransactionImpl tx : preparedTransactions) {
            resourceManager.putTransaction(tx.getXid(), tx, null);
         }
      }


      idGenerator.cleanup();

      return null;
   }


   @Override
   public void addGrouping(GroupBinding groupBinding) throws Exception {
      GroupingEncoding groupingEncoding = new GroupingEncoding(groupBinding.getId(), groupBinding.getGroupId(), groupBinding.getClusterName());
      dataManager.storeBindingsGenericData(groupBinding.getId(), JournalRecordIds.GROUP_RECORD, () -> encodeEncodingSupport(groupingEncoding), getContext());
   }

   @Override
   public void deleteGrouping(Transaction tx, GroupBinding groupBinding) throws Exception {
      dataManager.deleteBindingsGenericData(tx.getStorageTx(), groupBinding.getId(), getContext());
   }

   @Override
   public void updateQueueBinding(Transaction tx, Binding binding, AddressInfo addressInfo) throws Exception {
      RoutingType routingType = null;
      String queueConfigJson = null;
      if (binding instanceof LocalQueueBinding localQueueBinding) {
         routingType = localQueueBinding.getQueue().getRoutingType();
         queueConfigJson = localQueueBinding.getQueue().getQueueConfiguration().toJSON();
      }
      dataManager.updateQueue(tx.getStorageTx(), addressInfo.getId(), binding.getID(), String.valueOf(binding.getUniqueName()), binding.getFilter() != null ? String.valueOf(binding.getFilter().getFilterString()) : null, routingType, queueConfigJson, getContext());
   }

   @Override
   public void addQueueBinding(Transaction tx, Binding binding, AddressInfo addressInfo) throws Exception {
      RoutingType routingType = null;
      String queueConfigJson = null;
      if (binding instanceof LocalQueueBinding localQueueBinding) {
         routingType = localQueueBinding.getQueue().getRoutingType();
         queueConfigJson = localQueueBinding.getQueue().getQueueConfiguration().toJSON();
      }
      dataManager.storeQueue(tx.getStorageTx(), addressInfo.getId(), binding.getID(), String.valueOf(binding.getUniqueName()), binding.getFilter() != null ? String.valueOf(binding.getFilter().getFilterString()) : null, routingType, queueConfigJson, getContext());
   }

   @Override
   public void deleteQueueBinding(Transaction tx, long queueBindingID) throws Exception {
      dataManager.deleteQueue(tx.getStorageTx(), queueBindingID, getContext());
   }

   @Override
   public long storeQueueStatus(long queueID, AddressQueueStatus status) throws Exception {
      long recordID = generateID();
      dataManager.storeBindingsGenericData(recordID, JournalRecordIds.QUEUE_STATUS_RECORD, () -> encodeEncodingSupport(new QueueStatusEncoding(queueID, status)), getContext());
      return recordID;
   }

   @Override
   public void deleteQueueStatus(long recordID) throws Exception {
      dataManager.deleteBindingsGenericData(recordID, getContext());
   }

   @Override
   public long storeAddressStatus(long addressID, AddressQueueStatus status) throws Exception {
      long recordID = generateID();
      dataManager.storeBindingsGenericData(recordID, JournalRecordIds.ADDRESS_STATUS_RECORD, () -> encodeEncodingSupport(new AddressStatusEncoding(addressID, status)), getContext());
      return recordID;
   }

   @Override
   public void deleteAddressStatus(long recordID) throws Exception {
      dataManager.deleteBindingsGenericData(recordID, getContext());
   }

   @Override
   public void addAddressBinding(Transaction tx, AddressInfo addressInfo) throws Exception {
      addressInfo.setId(generateID());
      EnumSet<RoutingType> routingTypes = addressInfo.getRoutingTypes();
      dataManager.storeAddressInfo(tx.getStorageTx(), addressInfo.getId(), String.valueOf(addressInfo.getName()), routingTypes.contains(RoutingType.MULTICAST), routingTypes.contains(RoutingType.ANYCAST), getContext());
   }

   @Override
   public void deleteAddressBinding(Transaction tx, long addressBindingID) throws Exception {
      dataManager.deleteAddress(tx.getStorageTx(), addressBindingID, getContext());
   }

   @Override
   public long storePageCounterInc(Transaction tx, long queueID, int value, long persistentSize) throws Exception {
      return 0L;
   }

   @Override
   public long storePageCounterInc(long queueID, int value, long persistentSize) throws Exception {
      return 0L;
   }

   @Override
   public long storePageCounter(Transaction tx, long queueID, long value, long persistentSize) throws Exception {
      return 0L;
   }

   @Override
   public long storePendingCounter(long queueID, long pageID) throws Exception {
      return 0L;
   }

   @Override
   public void deleteIncrementRecord(Transaction tx, long recordID) throws Exception {
   }

   @Override
   public void deletePageCounter(Transaction tx, long recordID) throws Exception {
   }

   @Override
   public void deletePendingPageCounter(Transaction tx, long recordID) throws Exception {
   }

   @Override
   public void pageClosed(SimpleString address, long pageNumber) {

   }

   @Override
   public void pageDeleted(SimpleString address, long pageNumber) {
   }

   @Override
   public void pageWrite(SimpleString address,
                         PagedMessage message,
                         long pageNumber,
                         boolean storageUp,
                         boolean originallyReplicated) {

   }

   @Override
   public ByteBuffer allocateDirectBuffer(int size) {
      return null;
   }

   @Override
   public void freeDirectBuffer(ByteBuffer buffer) {

   }

   @Override
   public LargeServerMessage createCoreLargeMessage() {
      LargeServerMessageImpl msg = new LargeServerMessageImpl(this);
      return msg;
   }

   @Override
   public LargeServerMessage createCoreLargeMessage(long id, Message message) throws Exception {
      LargeServerMessageImpl largeMessage = (LargeServerMessageImpl) createCoreLargeMessage();

      largeMessage.moveHeadersAndProperties(message);

      return onLargeMessageCreate(id, largeMessage);
   }

   @Override
   public LargeServerMessage onLargeMessageCreate(long id, LargeServerMessage largeMessage) throws Exception {
      largeMessage.setMessageID(id);
      return largeMessage;
   }

   @Override
   public SequentialFile createFileForLargeMessage(long messageID, LargeMessageExtension extension) {
      return new MemorySequentialFile("file " + messageID);
   }

   @Override
   public void largeMessageClosed(LargeServerMessage largeServerMessage) throws ActiveMQException {

   }

   @Override
   public void deleteLargeMessageBody(LargeServerMessage largeServerMessage) throws ActiveMQException {

   }

   @Override
   public void startReplication(ReplicationManager replicationManager,
                                PagingManager pagingManager,
                                String nodeID,
                                boolean autoFailBack,
                                long initialReplicationSyncTimeout) throws Exception {

   }

   @Override
   public void stopReplication() {

   }

   @Override
   public void addBytesToLargeMessage(SequentialFile appendFile, long messageID, byte[] bytes) throws Exception {
      if (appendFile != null) {
         if (!appendFile.isOpen()) {
            appendFile.open();
         }
         appendFile.writeDirect(ByteBuffer.wrap(bytes), false);
      }
   }

   @Override
   public void addBytesToLargeMessage(SequentialFile file, long messageId, ActiveMQBuffer bytes) throws Exception {
      if (file != null) {
         if (!file.isOpen()) {
            file.open();
         }
         file.writeDirect(bytes.toByteBuffer(), false);
      }
   }

   @Override
   public void injectMonitor(FileStoreMonitor monitor) throws Exception {

   }

   // A separate connection is used for deletes because some databases (e.g. PostgreSQL)
   // invalidate the cursor when a DML statement is executed on the same connection.
   public void handleOrphanedMessages(Connection queryConnection) throws Exception {
      final String deleteSQL = databaseProvider.getSqlProvider().deleteMessages(databaseProvider.getSqlProvider().getMessages(), databaseProvider.getSqlProvider().getRefs());
      final int flushCommit = 1000;
      final AtomicInteger countPending = new AtomicInteger(0);
      final Connection[] deleteConnectionHolder = new Connection[1];
      final java.sql.PreparedStatement[] deleteStatementHolder = new java.sql.PreparedStatement[1];
      try {
         MessagesJDBCQuery query = new MessagesJDBCQuery(databaseProvider, queryConnection);
         query.queryOrphaned(data -> {
            ActiveMQServerLogger.LOGGER.journalUnreferencedMessage(data.messageID);
            try {
               if (deleteConnectionHolder[0] == null) {
                  deleteConnectionHolder[0] = databaseProvider.getConnection();
                  deleteConnectionHolder[0].setAutoCommit(false);
                  deleteStatementHolder[0] = deleteConnectionHolder[0].prepareStatement(deleteSQL);
               }
               deleteStatementHolder[0].setLong(1, data.messageID);
               deleteStatementHolder[0].executeUpdate();
               if (countPending.incrementAndGet() > flushCommit) {
                  countPending.set(0);
                  deleteConnectionHolder[0].commit();
               }
            } catch (java.sql.SQLException e) {
               ActiveMQServerLogger.LOGGER.journalErrorDeletingMessage(data.messageID, e);
            }
         });
         if (deleteConnectionHolder[0] != null) {
            deleteConnectionHolder[0].commit();
         }
      } finally {
         if (deleteStatementHolder[0] != null) {
            deleteStatementHolder[0].close();
         }
         if (deleteConnectionHolder[0] != null) {
            deleteConnectionHolder[0].close();
         }
      }
   }

}
