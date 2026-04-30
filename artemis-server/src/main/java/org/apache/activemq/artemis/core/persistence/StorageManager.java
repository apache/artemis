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
package org.apache.activemq.artemis.core.persistence;

import javax.transaction.xa.Xid;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.DivertConfiguration;
import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.io.OperationConsistencyLevel;
import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.journal.Journal;
import org.apache.activemq.artemis.core.journal.JournalLoadInformation;
import org.apache.activemq.artemis.core.journal.RecordInfo;
import org.apache.activemq.artemis.core.journal.collections.MapStorageManager;
import org.apache.activemq.artemis.core.paging.PageTransactionInfo;
import org.apache.activemq.artemis.core.paging.PagedMessage;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.paging.cursor.PagePosition;
import org.apache.activemq.artemis.core.persistence.config.AbstractPersistedAddressSetting;
import org.apache.activemq.artemis.core.persistence.config.PersistedAddressSettingJSON;
import org.apache.activemq.artemis.core.persistence.config.PersistedBridgeConfiguration;
import org.apache.activemq.artemis.core.persistence.config.PersistedConnector;
import org.apache.activemq.artemis.core.persistence.config.PersistedDivertConfiguration;
import org.apache.activemq.artemis.core.persistence.config.PersistedKeyValuePair;
import org.apache.activemq.artemis.core.persistence.config.PersistedRole;
import org.apache.activemq.artemis.core.persistence.config.PersistedSecuritySetting;
import org.apache.activemq.artemis.core.persistence.config.PersistedUser;
import org.apache.activemq.artemis.core.persistence.impl.PageCountPending;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.PostOffice;
import org.apache.activemq.artemis.core.replication.ReplicationManager;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.core.server.LargeServerMessage;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.RouteContextList;
import org.apache.activemq.artemis.core.server.files.FileStoreMonitor;
import org.apache.activemq.artemis.core.server.group.impl.GroupBinding;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.impl.JournalLoader;
import org.apache.activemq.artemis.core.transaction.ResourceManager;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.utils.ArtemisCloseable;
import org.apache.activemq.artemis.utils.IDGenerator;

/**
 * A StorageManager
 * <p>
 * Note about IDGEnerator
 * <p>
 * I've changed StorageManager to extend IDGenerator, because in some places all we needed from the StorageManager was
 * the idGeneration. I couldn't just get the IDGenerator from the inner part because the NullPersistent has its own
 * sequence. So the best was to add the interface and adjust the callers for the method
 */
public interface StorageManager extends MapStorageManager, IDGenerator, ActiveMQComponent {

   default long getMaxRecordSize() {
      // Null journal is pretty much memory
      return Long.MAX_VALUE;
   }

   default boolean isReplicated() {
      return false;
   }

   default long getWarningRecordSize() {
      // Null journal is pretty much memory
      return Long.MAX_VALUE;
   }

   default void recoverLargeMessagesOnFolder(Set<Long> files) throws Exception {
   }

   default void writeLock() {

   }

   default void writeUnlock() {

   }

   default SequentialFileFactory getJournalSequentialFileFactory() {
      return null;
   }

   void criticalError(Throwable error);

   /**
    * Get the context associated with the thread for later reuse
    */
   OperationContext getContext();

   /**
    * It just creates an OperationContext without associating it
    */
   OperationContext newContext(Executor executor);

   OperationContext newSingleThreadContext();

   /**
    * Set the context back to the thread
    */
   void setContext(OperationContext context);

   /**
    * Stop this {@code StorageManager}
    *
    * @param ioCriticalError is the server being stopped due to an IO critical error.
    * @param sendFailover    this is to send the replication stopping in case of replication.
    */
   void stop(boolean ioCriticalError, boolean sendFailover) throws Exception;

   default Set<RemotingConnection> getUsedConnections() {
      return Collections.emptySet();
   }

   // Message related operations

   void pageClosed(SimpleString address, long pageNumber);

   void pageDeleted(SimpleString address, long pageNumber);

   void pageWrite(SimpleString address, PagedMessage message, long pageNumber, boolean storageUp, boolean originallyReplicated);

   void afterCompleteOperations(IOCallback run);

   void afterCompleteOperations(IOCallback run, OperationConsistencyLevel consistencyLevel);
   /**
    * This is similar to afterComplete, however this only cares about the journal part.
    */
   void afterStoreOperations(IOCallback run);

   /**
    * Block until the operations are done. Warning: Don't use it inside an ordered executor, otherwise the system may
    * lock up in case of the pools are full
    */
   boolean waitOnOperations(long timeout) throws Exception;

   /**
    * Block until the operations are done. Warning: Don't use it inside an ordered executor, otherwise the system may
    * lock up in case of the pools are full
    */
   void waitOnOperations() throws Exception;

   /**
    * AIO has an optimized buffer which has a method to release it instead of the way NIO will release data based on GC.
    * These methods will use that buffer if the inner method supports it
    */
   ByteBuffer allocateDirectBuffer(int size);

   /**
    * AIO has an optimized buffer which has a method to release it instead of the way NIO will release data based on GC.
    * These methods will use that buffer if the inner method supports it
    */
   void freeDirectBuffer(ByteBuffer buffer);

   void clearContext();

   void deletePendingLargeMessage(long recordID) throws Exception;

   void storeMessage(Message message) throws Exception;

   void storeReference(long queueID, long messageID, boolean last) throws Exception;

   void deleteMessage(long messageID) throws Exception;

   void storeAcknowledge(long queueID, long messageID) throws Exception;

   void storeCursorAcknowledge(long queueID, PagePosition position) throws Exception;

   void updateDeliveryCount(MessageReference ref) throws Exception;

   void updateScheduledDeliveryTime(MessageReference ref) throws Exception;

   void storeDuplicateID(SimpleString address, byte[] duplID, long recordID) throws Exception;

   void deleteDuplicateID(long recordID) throws Exception;

   void storeMessageTransactional(long txID, Message message) throws Exception;

   void storeReferenceTransactional(long txID, long queueID, long messageID) throws Exception;

   void storeAcknowledgeTransactional(long txID, long queueID, long messageID) throws Exception;

   void storeCursorAcknowledgeTransactional(long txID, long queueID, PagePosition position) throws Exception;

   void deleteCursorAcknowledgeTransactional(long txID, long ackID) throws Exception;

   void deleteCursorAcknowledge(long ackID) throws Exception;

   void storePageCompleteTransactional(long txID, long queueID, PagePosition position) throws Exception;

   void deletePageComplete(long ackID) throws Exception;

   void updateScheduledDeliveryTimeTransactional(long txID, MessageReference ref) throws Exception;

   void storeDuplicateIDTransactional(long txID, SimpleString address, byte[] duplID, long recordID) throws Exception;

   void updateDuplicateIDTransactional(long txID, SimpleString address, byte[] duplID, long recordID) throws Exception;

   void deleteDuplicateIDTransactional(long txID, long recordID) throws Exception;

   LargeServerMessage createCoreLargeMessage();

   /**
    * Creates a new LargeServerMessage for the core Protocol with the given id.
    *
    * @param message This is a temporary message that holds the parsed properties. The remoting layer can't create a
    *                ServerMessage directly, then this will be replaced.
    * @return a large message object
    */
   LargeServerMessage createCoreLargeMessage(long id, Message message) throws Exception;

   /**
    * Other protocols may inform the storage manager when a large message was created.
    */
   LargeServerMessage onLargeMessageCreate(long id, LargeServerMessage largeMessage) throws Exception;

   enum LargeMessageExtension {
      DURABLE(".msg"), TEMPORARY(".tmp"), SYNC(".sync");
      final String extension;

      LargeMessageExtension(String extension) {
         this.extension = extension;
      }

      public String getExtension() {
         return extension;
      }
   }

   /**
    * Instantiates a SequentialFile to be used for storing a {@link LargeServerMessage}.
    *
    * @param messageID the id of the message
    * @param extension the extension to add to the file
    */
   SequentialFile createFileForLargeMessage(long messageID, LargeMessageExtension extension);

   void largeMessageClosed(LargeServerMessage largeServerMessage) throws ActiveMQException;

   void deleteLargeMessageBody(LargeServerMessage largeServerMessage) throws ActiveMQException;

   default SequentialFile createFileForLargeMessage(long messageID, boolean durable) {
      if (durable) {
         return createFileForLargeMessage(messageID, LargeMessageExtension.DURABLE);
      } else {
         return createFileForLargeMessage(messageID, LargeMessageExtension.TEMPORARY);
      }
   }


   void prepare(long txID, Xid xid) throws Exception;

   void commit(long txID) throws Exception;

   void commit(long txID, boolean lineUpContext) throws Exception;

   void asyncCommit(long txID) throws Exception;

   void rollback(long txID) throws Exception;

   void rollbackBindings(long txID) throws Exception;

   void commitBindings(long txID) throws Exception;

   void storePageTransaction(long txID, PageTransactionInfo pageTransaction) throws Exception;

   void updatePageTransaction(long txID, PageTransactionInfo pageTransaction, int depage) throws Exception;

   void deletePageTransactional(long recordID) throws Exception;

   default JournalLoadInformation loadMessageJournal(PostOffice postOffice,
                                             PagingManager pagingManager,
                                             ResourceManager resourceManager,
                                             Map<Long, QueueBindingInfo> queueInfos,
                                             Map<SimpleString, List<Pair<byte[], Long>>> duplicateIDMap,
                                             Set<Pair<Long, Long>> pendingLargeMessages,
                                             List<PageCountPending> pendingNonTXPageCounter,
                                             JournalLoader journalLoader) throws Exception {
      return loadMessageJournal(postOffice, pagingManager, resourceManager, queueInfos, duplicateIDMap, pendingLargeMessages, null, pendingNonTXPageCounter, journalLoader);
   }

   default JournalLoadInformation loadMessageJournal(PostOffice postOffice,
                                                     PagingManager pagingManager,
                                                     ResourceManager resourceManager,
                                                     Map<Long, QueueBindingInfo> queueInfos,
                                                     Map<SimpleString, List<Pair<byte[], Long>>> duplicateIDMap,
                                                     Set<Pair<Long, Long>> pendingLargeMessages,
                                                     Set<Long> largeMessagesInFolder,
                                                     List<PageCountPending> pendingNonTXPageCounter,
                                                     JournalLoader journalLoader) throws Exception {
      return loadMessageJournal(postOffice, pagingManager, resourceManager, queueInfos, duplicateIDMap, pendingLargeMessages, largeMessagesInFolder, pendingNonTXPageCounter, journalLoader, null);

   }

   JournalLoadInformation loadMessageJournal(PostOffice postOffice,
                                                     PagingManager pagingManager,
                                                     ResourceManager resourceManager,
                                                     Map<Long, QueueBindingInfo> queueInfos,
                                                     Map<SimpleString, List<Pair<byte[], Long>>> duplicateIDMap,
                                                     Set<Pair<Long, Long>> pendingLargeMessages,
                                                     Set<Long> largeMessagesInFolder,
                                                     List<PageCountPending> pendingNonTXPageCounter,
                                                     JournalLoader journalLoader,
                                                     List<Consumer<RecordInfo>> extraRecordsLoader) throws Exception;

   long storeHeuristicCompletion(Xid xid, boolean isCommit) throws Exception;

   void deleteHeuristicCompletion(long id) throws Exception;

   // BindingsImpl related operations

   void addQueueBinding(long tx, Binding binding) throws Exception;

   void updateQueueBinding(long tx, Binding binding) throws Exception;

   void deleteQueueBinding(long tx, long queueBindingID) throws Exception;

   /**
    * Store a queue's status.
    * @param queueID The id of the queue
    * @param status The current status of the queue. (Reserved for future use, ATM we only use this record for PAUSED)
    * @return the id of the journal
    */
   long storeQueueStatus(long queueID, AddressQueueStatus status) throws Exception;

   void deleteQueueStatus(long recordID) throws Exception;

   long storeAddressStatus(long addressID, AddressQueueStatus status) throws Exception;

   void deleteAddressStatus(long recordID) throws Exception;

   void addAddressBinding(long tx, AddressInfo addressInfo) throws Exception;

   void deleteAddressBinding(long tx, long addressBindingID) throws Exception;

   JournalLoadInformation loadBindingJournal(List<QueueBindingInfo> queueBindingInfos,
                                             List<GroupingInfo> groupingInfos,
                                             List<AddressBindingInfo> addressBindingInfos) throws Exception;

   // grouping related operations
   void addGrouping(GroupBinding groupBinding) throws Exception;

   void deleteGrouping(long tx, GroupBinding groupBinding) throws Exception;

   void storeAddressSetting(PersistedAddressSettingJSON addressSetting) throws Exception;

   void deleteAddressSetting(SimpleString addressMatch) throws Exception;

   List<AbstractPersistedAddressSetting> recoverAddressSettings() throws Exception;

   AbstractPersistedAddressSetting recoverAddressSettings(SimpleString address);

   void storeSecuritySetting(PersistedSecuritySetting persistedRoles) throws Exception;

   void deleteSecuritySetting(SimpleString addressMatch) throws Exception;

   List<PersistedSecuritySetting> recoverSecuritySettings() throws Exception;

   void storeDivertConfiguration(PersistedDivertConfiguration persistedDivertConfiguration) throws Exception;

   void deleteDivertConfiguration(String divertName) throws Exception;

   List<PersistedDivertConfiguration> recoverDivertConfigurations();

   DivertConfiguration getDivertConfiguration(String name);

   void storeBridgeConfiguration(PersistedBridgeConfiguration persistedBridgeConfiguration) throws Exception;

   void deleteBridgeConfiguration(String bridgeName) throws Exception;

   List<PersistedBridgeConfiguration> recoverBridgeConfigurations();

   void storeConnector(PersistedConnector persistedConnector) throws Exception;

   void deleteConnector(String connectorName) throws Exception;

   List<PersistedConnector> recoverConnectors();

   void storeUser(PersistedUser persistedUser) throws Exception;

   void deleteUser(String username) throws Exception;

   Map<String, PersistedUser> getPersistedUsers();

   void storeRole(PersistedRole persistedRole) throws Exception;

   void deleteRole(String role) throws Exception;

   Map<String, PersistedRole> getPersistedRoles();

   void storeKeyValuePair(PersistedKeyValuePair persistedKeyValuePair) throws Exception;

   void deleteKeyValuePair(String mapId, String key) throws Exception;

   Map<String, PersistedKeyValuePair> getPersistedKeyValuePairs(String mapId);

   /**
    * Store the specificed page counter.
    *
    * @return The ID with the stored counter
    */
   long storePageCounter(long txID, long queueID, long value, long persistentSize) throws Exception;

   long storePendingCounter(long queueID, long pageID) throws Exception;

   void deleteIncrementRecord(long txID, long recordID) throws Exception;

   void deletePageCounter(long txID, long recordID) throws Exception;

   void deletePendingPageCounter(long txID, long recordID) throws Exception;

   /**
    * Store the specificed page counter increment.
    *
    * @return the ID with the increment record
    */
   long storePageCounterInc(long txID, long queueID, int add, long persistentSize) throws Exception;

   /**
    * Store the specificed page counter increment.
    *
    * @return the ID with the increment record
    */
   long storePageCounterInc(long queueID, int add, long size) throws Exception;

   Journal getBindingsJournal();

   Journal getMessageJournal();

   void startReplication(ReplicationManager replicationManager,
                         PagingManager pagingManager,
                         String nodeID,
                         boolean autoFailBack,
                         long initialReplicationSyncTimeout) throws Exception;

   /**
    * Write message to page if we are paging.
    *
    * @return {@code true} if we are paging and have handled the data, {@code false} if the data needs to be sent to the
    * journal
    */
   boolean addToPage(PagingStore store, Message msg, Transaction tx, RouteContextList listCtx) throws Exception;

   /**
    * Stops the replication of data from the primary to the backup.
    * <p>
    * Typical scenario is a broken connection.
    */
   void stopReplication();

   void addBytesToLargeMessage(SequentialFile appendFile, long messageID, byte[] bytes) throws Exception;

   void addBytesToLargeMessage(SequentialFile file,
                               long messageId,
                               ActiveMQBuffer bytes) throws Exception;

   /**
    * Stores the id from IDManager.
    */
   void storeID(long journalID, long id) throws Exception;

   /*
       Deletes the ID from IDManager.
    */
   void deleteID(long journalD) throws Exception;

   default ArtemisCloseable closeableReadLock() {
      return closeableReadLock(false);
   }

   /**
    * Read lock the StorageManager. USE WITH CARE!
    * <p>
    * The main lock is used to write lock the whole manager when starting replication. Sub-systems, say Paging classes,
    * that use locks of their own AND also write through the StorageManager MUST first read lock the storageManager
    * before taking their own locks. Otherwise, we may dead-lock when starting replication sync.
    */
   ArtemisCloseable closeableReadLock(boolean tryLock);

   /**
    * Closes the {@link IDGenerator} persisting the current record ID.
    * <p>
    * Effectively a "pre-stop" method. Necessary due to the "stop"-order at
    * {@link org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl}
    */
   void persistIdGenerator();

   void injectMonitor(FileStoreMonitor monitor) throws Exception;

   default int getAllowedPageSize(int pageSize)  {
      return pageSize;
   }
}
