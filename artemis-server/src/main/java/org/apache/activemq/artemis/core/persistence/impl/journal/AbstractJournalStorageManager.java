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

import javax.transaction.xa.Xid;
import java.lang.invoke.MethodHandles;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.netty.buffer.Unpooled;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.buffers.impl.ChannelBufferWrapper;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.DivertConfiguration;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.io.IOCriticalErrorListener;
import org.apache.activemq.artemis.core.io.OperationConsistencyLevel;
import org.apache.activemq.artemis.core.journal.IOCompletion;
import org.apache.activemq.artemis.core.journal.Journal;
import org.apache.activemq.artemis.core.journal.JournalLoadInformation;
import org.apache.activemq.artemis.core.journal.PreparedTransactionInfo;
import org.apache.activemq.artemis.core.journal.RecordInfo;
import org.apache.activemq.artemis.core.paging.PageTransactionInfo;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.paging.cursor.PagePosition;
import org.apache.activemq.artemis.core.paging.cursor.PageSubscription;
import org.apache.activemq.artemis.core.paging.cursor.QueryPagedReferenceImpl;
import org.apache.activemq.artemis.core.paging.impl.PageTransactionInfoImpl;
import org.apache.activemq.artemis.core.persistence.AddressBindingInfo;
import org.apache.activemq.artemis.core.persistence.AddressQueueStatus;
import org.apache.activemq.artemis.core.persistence.CoreMessageObjectPools;
import org.apache.activemq.artemis.core.persistence.GroupingInfo;
import org.apache.activemq.artemis.core.persistence.OperationContext;
import org.apache.activemq.artemis.core.persistence.Persister;
import org.apache.activemq.artemis.core.persistence.QueueBindingInfo;
import org.apache.activemq.artemis.core.persistence.config.AbstractPersistedAddressSetting;
import org.apache.activemq.artemis.core.persistence.config.PersistedAddressSetting;
import org.apache.activemq.artemis.core.persistence.config.PersistedAddressSettingJSON;
import org.apache.activemq.artemis.core.persistence.config.PersistedBridgeConfiguration;
import org.apache.activemq.artemis.core.persistence.config.PersistedConfiguration;
import org.apache.activemq.artemis.core.persistence.config.PersistedConnector;
import org.apache.activemq.artemis.core.persistence.config.PersistedDivertConfiguration;
import org.apache.activemq.artemis.core.persistence.config.PersistedKeyValuePair;
import org.apache.activemq.artemis.core.persistence.config.PersistedRole;
import org.apache.activemq.artemis.core.persistence.config.PersistedSecuritySetting;
import org.apache.activemq.artemis.core.persistence.config.PersistedUser;
import org.apache.activemq.artemis.core.persistence.impl.AbstractStorageManager;
import org.apache.activemq.artemis.core.persistence.impl.PageCountPending;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.AddressStatusEncoding;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.CursorAckRecordEncoding;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.DeliveryCountUpdateEncoding;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.DuplicateIDEncoding;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.FinishPageMessageOperation;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.GroupingEncoding;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.HeuristicCompletionEncoding;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.LargeMessagePersister;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.PageCountPendingImpl;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.PageCountRecord;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.PageCountRecordInc;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.PageUpdateTXEncoding;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.PendingLargeMessageEncoding;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.PersistentAddressBindingEncoding;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.PersistentQueueBindingEncoding;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.QueueStatusEncoding;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.RefEncoding;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.ScheduledDeliveryEncoding;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.XidEncoding;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.DuplicateIDCache;
import org.apache.activemq.artemis.core.postoffice.PostOffice;
import org.apache.activemq.artemis.core.server.ActiveMQMessageBundle;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.LargeServerMessage;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.RouteContextList;
import org.apache.activemq.artemis.core.server.group.impl.GroupBinding;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.impl.JournalLoader;
import org.apache.activemq.artemis.core.transaction.ResourceManager;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.core.transaction.TransactionPropertyIndexes;
import org.apache.activemq.artemis.core.transaction.impl.TransactionImpl;
import org.apache.activemq.artemis.spi.core.protocol.MessagePersister;
import org.apache.activemq.artemis.utils.ArtemisCloseable;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.apache.activemq.artemis.utils.IDGenerator;
import org.apache.activemq.artemis.utils.collections.ConcurrentLongHashMap;
import org.apache.activemq.artemis.utils.collections.SparseArrayLinkedList;
import org.apache.activemq.artemis.utils.critical.CriticalAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.activemq.artemis.api.core.SimpleString.ByteBufSimpleStringPool.DEFAULT_MAX_LENGTH;
import static org.apache.activemq.artemis.api.core.SimpleString.ByteBufSimpleStringPool.DEFAULT_POOL_CAPACITY;
import static org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds.ACKNOWLEDGE_CURSOR;
import static org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds.ADD_LARGE_MESSAGE_PENDING;
import static org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds.DUPLICATE_ID;
import static org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds.PAGE_CURSOR_COUNTER_INC;
import static org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds.SET_SCHEDULED_DELIVERY_TIME;

/**
 * Controls access to the journals and other storage files such as the ones used to store pages and large messages.
 * This class must control writing of any non-transient data, as it is the key point for synchronizing any replicating
 * backup server.
 * <p>
 * Using this class also ensures that locks are acquired in the right order, avoiding dead-locks.
 */
public abstract class AbstractJournalStorageManager extends AbstractStorageManager {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public enum JournalContent {
      BINDINGS((byte) 0), MESSAGES((byte) 1);

      public final byte typeByte;

      JournalContent(byte b) {
         typeByte = b;
      }

      public static JournalContent getType(byte type) {
         if (MESSAGES.typeByte == type) {
            return MESSAGES;
         }
         if (BINDINGS.typeByte == type) {
            return BINDINGS;
         }
         throw new InvalidParameterException("invalid byte: " + type);
      }
   }

   private static final long CHECKPOINT_BATCH_SIZE = Integer.MAX_VALUE;

   private volatile PagingStore mirrorSNFPagingStore;

   protected BatchingIDGenerator idGenerator;

   protected Journal messageJournal;

   protected Journal bindingsJournal;

   private final boolean syncTransactional;

   private final boolean syncNonTransactional;

   protected boolean journalLoaded = false;

   protected final Configuration config;

   public Configuration getConfig() {
      return config;
   }

   protected final ConcurrentMap<String, ConcurrentMap<String, PersistedKeyValuePair>> mapPersistedKeyValuePairs = new ConcurrentHashMap<>();

   protected final ConcurrentLongHashMap<LargeServerMessage> largeMessagesToDelete = new ConcurrentLongHashMap<>();

   public AbstractJournalStorageManager(Configuration config,
                                        CriticalAnalyzer analyzer,
                                        ExecutorFactory executorFactory,
                                        ScheduledExecutorService scheduledExecutorService,
                                        ExecutorFactory ioExecutorFactory,
                                        IOCriticalErrorListener criticalErrorListener) {
      super(analyzer, CRITICAL_PATHS, executorFactory, scheduledExecutorService, ioExecutorFactory, criticalErrorListener);

      this.config = config;

      syncNonTransactional = config.isJournalSyncNonTransactional();
      syncTransactional = config.isJournalSyncTransactional();

      init(config, criticalErrorListener);

      idGenerator = new BatchingIDGenerator(0, CHECKPOINT_BATCH_SIZE, this);
   }

   @Override
   public long getMaxRecordSize() {
      return messageJournal.getMaxRecordSize();
   }

   @Override
   public long getWarningRecordSize() {
      return messageJournal.getWarningRecordSize();
   }


   /**
    * Called during initialization.  Used by implementations to setup Journals, Stores etc...
    */
   protected abstract void init(Configuration config, IOCriticalErrorListener criticalErrorListener);

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
   public void deletePendingLargeMessage(long recordID) throws Exception {
      try (ArtemisCloseable lock = closeableReadLock()) {
         messageJournal.tryAppendDeleteRecord(recordID, true, this::messageUpdateCallback, getContext());
      }
   }

   @Override
   public void storeMapRecord(long id,
                              byte recordType,
                              Persister persister,
                              Object record,
                              boolean sync,
                              IOCompletion completionCallback) throws Exception {
      try (ArtemisCloseable lock = closeableReadLock()) {
         messageJournal.appendAddRecord(id, recordType, persister, record, sync, completionCallback);
      }

   }

   @Override
   public void storeMapRecord(long id,
                              byte recordType,
                              Persister persister,
                              Object record,
                              boolean sync) throws Exception {
      try (ArtemisCloseable lock = closeableReadLock()) {
         messageJournal.appendAddRecord(id, recordType, persister, record, sync);
      }
   }

   @Override
   public void deleteMapRecord(long id, boolean sync) throws Exception {
      try (ArtemisCloseable lock = closeableReadLock()) {
         messageJournal.appendDeleteRecord(id, sync);
      }
   }

   @Override
   public void deleteMapRecordTx(long txid, long id) throws Exception {
      try (ArtemisCloseable lock = closeableReadLock()) {
         messageJournal.appendDeleteRecordTransactional(txid, id);
      }

   }

   @Override
   public void storeMessage(final Message message) throws Exception {
      if (message.getMessageID() <= 0) {
         // Sanity check only... this shouldn't happen unless there is a bug
         throw ActiveMQMessageBundle.BUNDLE.messageIdNotAssigned();
      }

      try (ArtemisCloseable lock = closeableReadLock()) {         // Note that we don't sync, the add reference that comes immediately after will sync if
         // appropriate

         if (message.isLargeMessage() && message instanceof LargeServerMessageImpl) {
            messageJournal.appendAddRecord(message.getMessageID(), JournalRecordIds.ADD_LARGE_MESSAGE, LargeMessagePersister.getInstance(), message, false, getContext(false));
         } else {
            messageJournal.appendAddRecord(message.getMessageID(), JournalRecordIds.ADD_MESSAGE_PROTOCOL, message.getPersister(), message, false, getContext(false));
         }
      }
   }

   @Override
   public void storeReference(final long queueID, final long messageID, final boolean pendingDelivery, final boolean last) throws Exception {
      try (ArtemisCloseable lock = closeableReadLock()) {
         messageJournal.tryAppendUpdateRecord(messageID, JournalRecordIds.ADD_REF, new RefEncoding(queueID), last && syncNonTransactional, false, this::messageUpdateCallback, getContext(last && syncNonTransactional));
      }
   }
   @Override
   public void storeAcknowledge(final long queueID, final long messageID) throws Exception {
      try (ArtemisCloseable lock = closeableReadLock()) {
         messageJournal.tryAppendUpdateRecord(messageID, JournalRecordIds.ACKNOWLEDGE_REF, new RefEncoding(queueID), syncNonTransactional, false, this::messageUpdateCallback, getContext(syncNonTransactional));
      }
   }

   @Override
   public void storeCursorAcknowledge(long queueID, PagePosition position) throws Exception {
      try (ArtemisCloseable lock = closeableReadLock()) {
         long ackID = idGenerator.generateID();
         position.setRecordID(ackID);
         messageJournal.appendAddRecord(ackID, JournalRecordIds.ACKNOWLEDGE_CURSOR, new CursorAckRecordEncoding(queueID, position), syncNonTransactional, getContext(syncNonTransactional));
      }
   }

   @Override
   public void deleteMessage(final long messageID) throws Exception {
      try (ArtemisCloseable lock = closeableReadLock()) {
         // Messages are deleted on postACK, one after another.
         // If these deletes are synchronized, we would build up messages on the Executor
         // increasing chances of losing deletes.
         // The StorageManager should verify messages without references
         messageJournal.tryAppendDeleteRecord(messageID, false, this::messageUpdateCallback, getContext(false));
      }
   }

   private void deleteRecordAsync(long journalId) throws Exception {
      deleteRecord(journalId, false);
   }

   private void deleteRecordSync(long journalId) throws Exception {
      deleteRecord(journalId, true);
   }

   private void deleteRecord(long journalId, boolean sync) throws Exception {
      try (ArtemisCloseable lock = closeableReadLock()) {
         bindingsJournal.tryAppendDeleteRecord(journalId, this::recordNotFoundCallback, sync);
      }
   }

   private void messageUpdateCallback(long id, boolean found) {
      if (!found) {
         ActiveMQServerLogger.LOGGER.cannotFindMessageOnJournal(id, new Exception("trace"));
      }
   }

   private void recordNotFoundCallback(long id, boolean found) {
      if (!found) {
         if (logger.isDebugEnabled()) {
            logger.debug("Record {} not found", id);
         }
      }
   }

   @Override
   public void updateScheduledDeliveryTime(final MessageReference ref) throws Exception {
      if (config.getMaxRedeliveryRecords() >= 0 && ref.getDeliveryCount() > config.getMaxRedeliveryRecords()) {
         return;
      }
      ScheduledDeliveryEncoding encoding = new ScheduledDeliveryEncoding(ref.getScheduledDeliveryTime(), ref.getQueue().getID());
      try (ArtemisCloseable lock = closeableReadLock()) {
         messageJournal.tryAppendUpdateRecord(ref.getMessage().getMessageID(), JournalRecordIds.SET_SCHEDULED_DELIVERY_TIME, encoding, syncNonTransactional, true, this::recordNotFoundCallback, getContext(syncNonTransactional));
      }
   }

   @Override
   public void storeDuplicateID(final SimpleString address, final byte[] duplID, final long recordID) throws Exception {
      try (ArtemisCloseable lock = closeableReadLock()) {
         DuplicateIDEncoding encoding = new DuplicateIDEncoding(address, duplID);

         messageJournal.appendAddRecord(recordID, JournalRecordIds.DUPLICATE_ID, encoding, syncNonTransactional, getContext(syncNonTransactional));
      }
   }

   @Override
   public void deleteDuplicateID(final long recordID) throws Exception {
      try (ArtemisCloseable lock = closeableReadLock()) {
         messageJournal.tryAppendDeleteRecord(recordID, syncNonTransactional, this::recordNotFoundCallback, getContext(syncNonTransactional));
      }
   }

   // Transactional operations

   @Override
   public void storeMessageTransactional(final Transaction tx, final Message message) throws Exception {
      if (message.getMessageID() <= 0) {
         throw ActiveMQMessageBundle.BUNDLE.messageIdNotAssigned();
      }

      try (ArtemisCloseable lock = closeableReadLock()) {
         if (message.isLargeMessage() && message instanceof LargeServerMessageImpl) {
            // this is a core large message
            messageJournal.appendAddRecordTransactional(tx.getID(), message.getMessageID(), JournalRecordIds.ADD_LARGE_MESSAGE, LargeMessagePersister.getInstance(), message);
         } else {
            messageJournal.appendAddRecordTransactional(tx.getID(), message.getMessageID(), JournalRecordIds.ADD_MESSAGE_PROTOCOL, message.getPersister(), message);
         }

      }
   }

   @Override
   public void storePageTransaction(final Transaction tx, final PageTransactionInfo pageTransaction) throws Exception {
      try (ArtemisCloseable lock = closeableReadLock()) {
         pageTransaction.setRecordID(generateID());
         messageJournal.appendAddRecordTransactional(tx.getID(), pageTransaction.getRecordID(), JournalRecordIds.PAGE_TRANSACTION, pageTransaction);
      }
   }

   @Override
   public void updatePageTransaction(final Transaction tx,
                                     final PageTransactionInfo pageTransaction,
                                     final int depages) throws Exception {
      try (ArtemisCloseable lock = closeableReadLock()) {
         messageJournal.appendUpdateRecordTransactional(tx.getID(), pageTransaction.getRecordID(), JournalRecordIds.PAGE_TRANSACTION, new PageUpdateTXEncoding(pageTransaction.getTransactionID(), depages));
      }
   }

   @Override
   public void storeReferenceTransactional(final Transaction tx, final long queueID, final long messageID, final boolean pendingDelivery) throws Exception {
      try (ArtemisCloseable lock = closeableReadLock()) {
         messageJournal.appendUpdateRecordTransactional(tx.getID(), messageID, JournalRecordIds.ADD_REF, new RefEncoding(queueID));
      }
   }

   @Override
   public void storeAcknowledgeTransactional(final Transaction tx,
                                             final long queueID,
                                             final long messageID) throws Exception {
      try (ArtemisCloseable lock = closeableReadLock()) {
         messageJournal.appendUpdateRecordTransactional(tx.getID(), messageID, JournalRecordIds.ACKNOWLEDGE_REF, new RefEncoding(queueID));
      }
   }

   @Override
   public void storeCursorAcknowledgeTransactional(final Transaction tx, long queueID, PagePosition position) throws Exception {
      try (ArtemisCloseable lock = closeableReadLock()) {
         long ackID = idGenerator.generateID();
         position.setRecordID(ackID);
         messageJournal.appendAddRecordTransactional(tx.getID(), ackID, JournalRecordIds.ACKNOWLEDGE_CURSOR, new CursorAckRecordEncoding(queueID, position));
      }
   }

   @Override
   public void storePageCompleteTransactional(final Transaction tx, long queueID, PagePosition position) throws Exception {
      long recordID = idGenerator.generateID();
      position.setRecordID(recordID);
      messageJournal.appendAddRecordTransactional(tx.getID(), recordID, JournalRecordIds.PAGE_CURSOR_COMPLETE, new CursorAckRecordEncoding(queueID, position));
   }

   @Override
   public void deletePageComplete(long ackID) throws Exception {
      messageJournal.tryAppendDeleteRecord(ackID, this::recordNotFoundCallback, false);
   }

   @Override
   public void deleteCursorAcknowledgeTransactional(final Transaction tx, long ackID) throws Exception {
      try (ArtemisCloseable lock = closeableReadLock()) {
         messageJournal.appendDeleteRecordTransactional(tx.getID(), ackID);
      }
   }

   @Override
   public void deleteCursorAcknowledge(long ackID) throws Exception {
      messageJournal.tryAppendDeleteRecord(ackID, this::recordNotFoundCallback, false);
   }

   @Override
   public long storeHeuristicCompletion(final Xid xid, final boolean isCommit) throws Exception {
      try (ArtemisCloseable lock = closeableReadLock()) {
         long id = generateID();

         messageJournal.appendAddRecord(id, JournalRecordIds.HEURISTIC_COMPLETION, new HeuristicCompletionEncoding(xid, isCommit), true, getContext(true));
         return id;
      }
   }

   @Override
   public void deleteHeuristicCompletion(final long id) throws Exception {
      try (ArtemisCloseable lock = closeableReadLock()) {
         messageJournal.tryAppendDeleteRecord(id, true, this::recordNotFoundCallback, getContext(true));
      }
   }

   @Override
   public void deletePageTransactional(final long recordID) throws Exception {
      try (ArtemisCloseable lock = closeableReadLock()) {
         messageJournal.tryAppendDeleteRecord(recordID, this::recordNotFoundCallback, false);
      }
   }

   @Override
   public void updateScheduledDeliveryTimeTransactional(final Transaction tx, final MessageReference ref) throws Exception {
      ScheduledDeliveryEncoding encoding = new ScheduledDeliveryEncoding(ref.getScheduledDeliveryTime(), ref.getQueue().getID());
      try (ArtemisCloseable lock = closeableReadLock()) {
         messageJournal.appendUpdateRecordTransactional(tx.getID(), ref.getMessage().getMessageID(), JournalRecordIds.SET_SCHEDULED_DELIVERY_TIME, encoding);
      }
   }

   @Override
   public void prepare(final Transaction tx, final Xid xid) throws Exception {
      try (ArtemisCloseable lock = closeableReadLock()) {
         messageJournal.appendPrepareRecord(tx.getID(), new XidEncoding(xid), syncTransactional, getContext(syncTransactional));
      }
   }

   @Override
   public void commit(final Transaction tx) throws Exception {
      commit(tx, true);
   }

   @Override
   public void commitBindings(final Transaction tx) throws Exception {
      bindingsJournal.appendCommitRecord(tx.getID(), true, getContext(true), true);
   }

   @Override
   public void rollbackBindings(final Transaction tx) throws Exception {
      // no need to sync, it's going away anyways
      bindingsJournal.appendRollbackRecord(tx.getID(), false);
   }

   @Override
   public void commit(final Transaction tx, final boolean lineUpContext) throws Exception {
      try (ArtemisCloseable lock = closeableReadLock()) {
         messageJournal.appendCommitRecord(tx.getID(), syncTransactional, getContext(syncTransactional), lineUpContext);
         if (!lineUpContext && !syncTransactional) {
            if (logger.isTraceEnabled()) {
               logger.trace("calling getContext(true).done() for txID={}, lineupContext={} syncTransactional={}... forcing call on getContext(true).done",
                  tx.getID(), lineUpContext, syncTransactional);
            }
            /*
             * If lineUpContext == false, it means that we have previously lined up a context somewhere else
             * (specifically see TransactionImpl#asyncAppendCommit), hence we need to mark it as done even if
             * syncTransactional = false as in this case getContext(syncTransactional=false) would pass a dummy context
             * to the messageJournal.appendCommitRecord(...) call above.
             */
            getContext(true).done();
         }
      }
   }

   @Override
   public void asyncCommit(final Transaction tx) throws Exception {
      try (ArtemisCloseable lock = closeableReadLock()) {
         messageJournal.appendCommitRecord(tx.getID(), false, getContext(true), true);
      }
   }

   @Override
   public void rollback(final Transaction tx) throws Exception {
      try (ArtemisCloseable lock = closeableReadLock()) {
         messageJournal.appendRollbackRecord(tx.getID(), syncTransactional, getContext(syncTransactional));
      }
   }

   @Override
   public void storeDuplicateIDTransactional(final Transaction tx,
                                             final SimpleString address,
                                             final byte[] duplID,
                                             final long recordID) throws Exception {
      DuplicateIDEncoding encoding = new DuplicateIDEncoding(address, duplID);

      try (ArtemisCloseable lock = closeableReadLock()) {
         messageJournal.appendAddRecordTransactional(tx.getID(), recordID, JournalRecordIds.DUPLICATE_ID, encoding);
      }
   }

   @Override
   public void updateDuplicateIDTransactional(final Transaction tx,
                                              final SimpleString address,
                                              final byte[] duplID,
                                              final long recordID) throws Exception {
      DuplicateIDEncoding encoding = new DuplicateIDEncoding(address, duplID);

      try (ArtemisCloseable lock = closeableReadLock()) {
         messageJournal.appendUpdateRecordTransactional(tx.getID(), recordID, JournalRecordIds.DUPLICATE_ID, encoding);
      }
   }

   @Override
   public void deleteDuplicateIDTransactional(final Transaction tx, final long recordID) throws Exception {
      try (ArtemisCloseable lock = closeableReadLock()) {
         messageJournal.appendDeleteRecordTransactional(tx.getID(), recordID);
      }
   }

   // Other operations

   @Override
   public void updateDeliveryCount(final MessageReference ref) throws Exception {
      // no need to store if it's the same value
      // otherwise the journal will get OME in case of lots of redeliveries
      if (ref.getDeliveryCount() == ref.getPersistedCount()) {
         return;
      }

      if (config.getMaxRedeliveryRecords() >= 0 && ref.getDeliveryCount() > config.getMaxRedeliveryRecords()) {
         return;
      }

      ref.setPersistedCount(ref.getDeliveryCount());
      DeliveryCountUpdateEncoding updateInfo = new DeliveryCountUpdateEncoding(ref.getQueue().getID(), ref.getDeliveryCount());

      try (ArtemisCloseable lock = closeableReadLock()) {
         messageJournal.tryAppendUpdateRecord(ref.getMessage().getMessageID(), JournalRecordIds.UPDATE_DELIVERY_COUNT, updateInfo, syncNonTransactional, true, this::messageUpdateCallback, getContext(syncNonTransactional));
      }
   }

   @Override
   public void storeAddressSetting(PersistedAddressSettingJSON addressSetting) throws Exception {
      storeConfiguration(addressSetting, mapPersistedAddressSettings);
   }

   @Override
   public List<AbstractPersistedAddressSetting> recoverAddressSettings() throws Exception {
      return new ArrayList<>(mapPersistedAddressSettings.values());
   }

   @Override
   public AbstractPersistedAddressSetting recoverAddressSettings(SimpleString address) {
      return mapPersistedAddressSettings.get(address.toString());
   }

   @Override
   public void storeSecuritySetting(PersistedSecuritySetting persistedRoles) throws Exception {
      storeConfiguration(persistedRoles, mapPersistedSecuritySettings);
   }

   @Override
   public List<PersistedSecuritySetting> recoverSecuritySettings() throws Exception {
      return new ArrayList<>(mapPersistedSecuritySettings.values());
   }

   @Override
   public void storeDivertConfiguration(PersistedDivertConfiguration persistedDivertConfiguration) throws Exception {
      storeConfiguration(persistedDivertConfiguration, mapPersistedDivertConfigurations);
   }

   @Override
   public void deleteDivertConfiguration(String divertName) throws Exception {
      PersistedDivertConfiguration old = mapPersistedDivertConfigurations.remove(divertName);
      if (old != null) {
         deleteRecordAsync(old.getStoreId());
      }
   }

   @Override
   public List<PersistedDivertConfiguration> recoverDivertConfigurations() {
      return new ArrayList<>(mapPersistedDivertConfigurations.values());
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
      storeConfiguration(persistedBridgeConfiguration, mapPersistedBridgeConfigurations);
   }

   @Override
   public void deleteBridgeConfiguration(String bridgeName) throws Exception {
      PersistedBridgeConfiguration old = mapPersistedBridgeConfigurations.remove(bridgeName);
      if (old != null) {
         deleteRecordAsync(old.getStoreId());
      }
   }

   @Override
   public List<PersistedBridgeConfiguration> recoverBridgeConfigurations() {
      return new ArrayList<>(mapPersistedBridgeConfigurations.values());
   }

   @Override
   public void storeConnector(PersistedConnector persistedConnector) throws Exception {
      storeConfiguration(persistedConnector, mapPersistedConnectors);
   }

   @Override
   public void deleteConnector(String connectorName) throws Exception {
      PersistedConnector old = mapPersistedConnectors.remove(connectorName);
      if (old != null) {
         deleteRecordAsync(old.getStoreId());
      }
   }

   @Override
   public List<PersistedConnector> recoverConnectors() {
      return new ArrayList<>(mapPersistedConnectors.values());
   }

   @Override
   public void storeUser(PersistedUser persistedUser) throws Exception {
      storeConfiguration(persistedUser, mapPersistedUsers);
   }

   @Override
   public void deleteUser(String username) throws Exception {
      PersistedUser old = mapPersistedUsers.remove(username);
      if (old != null) {
         deleteRecordAsync(old.getStoreId());
      }
   }

   @Override
   public Map<String, PersistedUser> getPersistedUsers() {
      return new HashMap<>(mapPersistedUsers);
   }

   @Override
   public void storeRole(PersistedRole persistedRole) throws Exception {
      storeConfiguration(persistedRole, mapPersistedRoles);
   }

   @Override
   public void deleteRole(String username) throws Exception {
      PersistedRole old = mapPersistedRoles.remove(username);
      if (old != null) {
         deleteRecordAsync(old.getStoreId());
      }
   }

   @Override
   public Map<String, PersistedRole> getPersistedRoles() {
      return new HashMap<>(mapPersistedRoles);
   }

   @Override
   public void storeKeyValuePair(PersistedKeyValuePair persistedKeyValuePair) throws Exception {
      storeConfiguration(persistedKeyValuePair, () -> insertPersistedKeyValuePair(persistedKeyValuePair));
   }

   @Override
   public void deleteKeyValuePair(String mapId, String key) throws Exception {
      Map<String, PersistedKeyValuePair> persistedKeyValuePairs = mapPersistedKeyValuePairs.get(mapId);
      if (persistedKeyValuePairs != null) {
         PersistedKeyValuePair old = persistedKeyValuePairs.remove(key);
         if (old != null) {
            deleteRecordAsync(old.getStoreId());
         }
      }
   }

   @Override
   public Map<String, PersistedKeyValuePair> getPersistedKeyValuePairs(String mapId) {
      Map<String, PersistedKeyValuePair> persistedKeyValuePairs = mapPersistedKeyValuePairs.get(mapId);
      return persistedKeyValuePairs != null ? new HashMap<>(persistedKeyValuePairs) : new HashMap<>();
   }

   private <T extends PersistedConfiguration> void storeConfiguration(T persistedConfiguration, Map<String, T> map) throws Exception {
      storeConfiguration(persistedConfiguration, () -> map.put(persistedConfiguration.getName(), persistedConfiguration));
   }

   private <T extends PersistedConfiguration> void storeConfiguration(T persistedConfiguration, Supplier<T> s) throws Exception {
      try (ArtemisCloseable lock = closeableReadLock()) {
         final long recordID = idGenerator.generateID();
         persistedConfiguration.setStoreId(recordID);
         T old = s.get();
         if (old != null) {
            final long txID = idGenerator.generateID();
            Transaction tx = new TransactionImpl(txID, null, this);
            bindingsJournal.appendDeleteRecordTransactional(txID, old.getStoreId());
            bindingsJournal.appendAddRecordTransactional(txID, recordID, persistedConfiguration.getRecordType(), persistedConfiguration);
            commitBindings(tx);
         } else {
            bindingsJournal.appendAddRecord(recordID, persistedConfiguration.getRecordType(), persistedConfiguration, true);
         }
      }
   }

   @Override
   public void storeID(final long journalID, final long id) throws Exception {
      try (ArtemisCloseable lock = closeableReadLock()) {
         bindingsJournal.appendAddRecord(journalID, JournalRecordIds.ID_COUNTER_RECORD, BatchingIDGenerator.createIDEncodingSupport(id), true, getContext(true));
      }
   }

   @Override
   public void deleteID(long journalID) throws Exception {
      deleteRecordAsync(journalID);
   }

   @Override
   public void deleteAddressSetting(SimpleString addressMatch) throws Exception {
      AbstractPersistedAddressSetting old = mapPersistedAddressSettings.remove(addressMatch.toString());
      if (old != null) {
         deleteRecordAsync(old.getStoreId());
      }
   }

   @Override
   public void deleteSecuritySetting(SimpleString addressMatch) throws Exception {
      PersistedSecuritySetting old = mapPersistedSecuritySettings.remove(addressMatch.toString());
      if (old != null) {
         deleteRecordAsync(old.getStoreId());
      }
   }

   @Override
   public JournalLoadInformation loadMessageJournal(final PostOffice postOffice,
                                                    final PagingManager pagingManager,
                                                    final ResourceManager resourceManager,
                                                    Map<Long, QueueBindingInfo> queueInfos,
                                                    final Map<SimpleString, List<Pair<byte[], Long>>> duplicateIDMap,
                                                    final Set<Pair<Long, Long>> pendingLargeMessages,
                                                    final Set<Long> storedLargeMessages,
                                                    List<PageCountPending> pendingNonTXPageCounter,
                                                    final JournalLoader journalLoader,
                                                    final List<Consumer<RecordInfo>> journalRecordsListener) throws Exception {
      SparseArrayLinkedList<RecordInfo> records = new SparseArrayLinkedList<>();

      List<PreparedTransactionInfo> preparedTransactions = new ArrayList<>();

      Set<PageTransactionInfo> invalidPageTransactions = new HashSet<>();

      Map<Long, Message> messages = new HashMap<>();
      try (ArtemisCloseable lock = closeableReadLock()) {
         messageJournal.setRemoveExtraFilesOnLoad(true);
         JournalLoadInformation info = messageJournal.load(records, preparedTransactions, new LargeMessageTXFailureCallback(this));

         List<LargeServerMessage> largeMessages = new ArrayList<>();

         Map<Long, Map<Long, AddMessageRecord>> queueMap = new HashMap<>();

         Map<Long, PageSubscription> pageSubscriptions = new HashMap<>();

         final long totalSize = records.size();

         final class MutableLong {

            long value;
         }

         final MutableLong recordNumber = new MutableLong();
         final CoreMessageObjectPools pools;
         if (totalSize > 0) {
            final int addresses = (int) Math.max(DEFAULT_POOL_CAPACITY, queueInfos == null ? 0 : queueInfos.values().stream().map(qInfo -> qInfo.getQueueConfiguration().getAddress()).filter(addr -> addr.length() <= DEFAULT_MAX_LENGTH).count() * 2);
            pools = new CoreMessageObjectPools(addresses, DEFAULT_POOL_CAPACITY, 128, 128);
         } else {
            pools = null;
         }
         // This will free up memory sooner while reading the records
         records.clear(record -> {
            try {
               // It will show log.info only with large journals (more than 1 million records)
               if (recordNumber.value > 0 && recordNumber.value % 1000000 == 0) {
                  long percent = (long) ((((double) recordNumber.value) / ((double) totalSize)) * 100f);

                  ActiveMQServerLogger.LOGGER.percentLoaded(percent);
               }
               recordNumber.value++;

               byte[] data = record.data;

               // We can make this byte[] buffer releasable, because subsequent methods using it are not supposed
               // to release it. It saves creating useless UnreleasableByteBuf wrappers
               ChannelBufferWrapper buff = new ChannelBufferWrapper(Unpooled.wrappedBuffer(data), true);

               byte recordType = record.getUserRecordType();

               switch (recordType) {
                  case JournalRecordIds.ADD_LARGE_MESSAGE_PENDING: {
                     PendingLargeMessageEncoding pending = new PendingLargeMessageEncoding();

                     pending.decode(buff);

                     if (pendingLargeMessages != null) {
                        // it could be null on tests, and we don't need anything on that case
                        pendingLargeMessages.add(new Pair<>(record.id, pending.largeMessageID));
                     }
                     break;
                  }
                  case JournalRecordIds.ADD_LARGE_MESSAGE: {
                     LargeServerMessage largeMessage = parseLargeMessage(buff);

                     messages.put(record.id, largeMessage.toMessage());

                     if (storedLargeMessages != null) {
                        storedLargeMessages.remove(largeMessage.getMessageID());
                     }

                     largeMessages.add(largeMessage);

                     break;
                  }
                  case JournalRecordIds.ADD_MESSAGE: {
                     throw new IllegalStateException("This is using old journal data, export your data and import at the correct version");
                  }

                  case JournalRecordIds.ADD_MESSAGE_PROTOCOL: {

                     Message message = decodeMessage(pools, buff);

                     if (message.isLargeMessage() && storedLargeMessages != null) {
                        storedLargeMessages.remove(message.getMessageID());
                     }

                     if (message.isLargeMessage()) {
                        largeMessages.add((LargeServerMessage) message);
                     }

                     messages.put(record.id, message);

                     break;
                  }
                  case JournalRecordIds.ADD_REF: {
                     long messageID = record.id;

                     RefEncoding encoding = new RefEncoding();

                     encoding.decode(buff);

                     Map<Long, AddMessageRecord> queueMessages = queueMap.get(encoding.queueID);

                     if (queueMessages == null) {
                        queueMessages = new LinkedHashMap<>();

                        queueMap.put(encoding.queueID, queueMessages);
                     }

                     Message message = messages.get(messageID);

                     if (message == null) {
                        ActiveMQServerLogger.LOGGER.cannotFindMessage(record.id);
                     } else {
                        queueMessages.put(messageID, new AddMessageRecord(message));
                     }

                     break;
                  }
                  case JournalRecordIds.ACKNOWLEDGE_REF: {
                     long messageID = record.id;

                     RefEncoding encoding = new RefEncoding();

                     encoding.decode(buff);

                     Map<Long, AddMessageRecord> queueMessages = queueMap.get(encoding.queueID);

                     if (queueMessages == null) {
                        ActiveMQServerLogger.LOGGER.journalCannotFindQueue(encoding.queueID, messageID);
                     } else {
                        AddMessageRecord rec = queueMessages.remove(messageID);

                        if (rec == null) {
                           ActiveMQServerLogger.LOGGER.cannotFindMessage(messageID);
                        }
                     }

                     break;
                  }
                  case JournalRecordIds.UPDATE_DELIVERY_COUNT: {
                     long messageID = record.id;

                     DeliveryCountUpdateEncoding encoding = new DeliveryCountUpdateEncoding();

                     encoding.decode(buff);

                     Map<Long, AddMessageRecord> queueMessages = queueMap.get(encoding.queueID);

                     if (queueMessages == null) {
                        ActiveMQServerLogger.LOGGER.journalCannotFindQueueDelCount(encoding.queueID);
                     } else {
                        AddMessageRecord rec = queueMessages.get(messageID);

                        if (rec == null) {
                           ActiveMQServerLogger.LOGGER.journalCannotFindMessageDelCount(messageID);
                        } else {
                           rec.setDeliveryCount(encoding.count);
                        }
                     }

                     break;
                  }
                  case JournalRecordIds.PAGE_TRANSACTION: {
                     PageTransactionInfo invalidPGTx = null;
                     if (record.isUpdate) {
                        PageUpdateTXEncoding pageUpdate = new PageUpdateTXEncoding();

                        pageUpdate.decode(buff);

                        PageTransactionInfo pageTX = pagingManager.getTransaction(pageUpdate.pageTX);

                        if (pageTX == null) {
                           ActiveMQServerLogger.LOGGER.journalCannotFindPageTX(pageUpdate.pageTX);
                        } else {
                           if (!pageTX.onUpdate(pageUpdate.records, null, null)) {
                              invalidPGTx = pageTX;
                           }
                        }
                     } else {
                        PageTransactionInfoImpl pageTransactionInfo = new PageTransactionInfoImpl();

                        pageTransactionInfo.decode(buff);

                        pageTransactionInfo.setRecordID(record.id);

                        pagingManager.addTransaction(pageTransactionInfo);

                        if (!pageTransactionInfo.checkSize(null, null)) {
                           invalidPGTx = pageTransactionInfo;
                        }
                     }

                     if (invalidPGTx != null) {
                        invalidPageTransactions.add(invalidPGTx);
                     }

                     break;
                  }
                  case JournalRecordIds.SET_SCHEDULED_DELIVERY_TIME: {
                     long messageID = record.id;

                     ScheduledDeliveryEncoding encoding = new ScheduledDeliveryEncoding();

                     encoding.decode(buff);

                     Map<Long, AddMessageRecord> queueMessages = queueMap.get(encoding.queueID);

                     if (queueMessages == null) {
                        ActiveMQServerLogger.LOGGER.journalCannotFindQueueScheduled(encoding.queueID, messageID);
                     } else {

                        AddMessageRecord rec = queueMessages.get(messageID);

                        if (rec == null) {
                           ActiveMQServerLogger.LOGGER.cannotFindMessage(messageID);
                        } else {
                           rec.setScheduledDeliveryTime(encoding.scheduledDeliveryTime);
                        }
                     }

                     break;
                  }
                  case JournalRecordIds.DUPLICATE_ID: {
                     DuplicateIDEncoding encoding = new DuplicateIDEncoding();

                     encoding.decode(buff);

                     List<Pair<byte[], Long>> ids = duplicateIDMap.get(encoding.address);

                     if (ids == null) {
                        ids = new ArrayList<>();

                        duplicateIDMap.put(encoding.address, ids);
                     }

                     ids.add(new Pair<>(encoding.duplID, record.id));

                     break;
                  }
                  case JournalRecordIds.HEURISTIC_COMPLETION: {
                     HeuristicCompletionEncoding encoding = new HeuristicCompletionEncoding();
                     encoding.decode(buff);
                     resourceManager.putHeuristicCompletion(record.id, encoding.xid, encoding.isCommit);
                     break;
                  }
                  case JournalRecordIds.ACKNOWLEDGE_CURSOR: {
                     CursorAckRecordEncoding encoding = new CursorAckRecordEncoding();
                     encoding.decode(buff);

                     encoding.position.setRecordID(record.id);

                     PageSubscription sub = locateSubscription(encoding.queueID, pageSubscriptions, queueInfos, pagingManager);

                     if (sub == null) {
                        ActiveMQServerLogger.LOGGER.journalCannotFindQueueReloading(encoding.queueID);
                        messageJournal.tryAppendDeleteRecord(record.id, this::recordNotFoundCallback, false);
                     } else {
                        if (encoding.position.getPageNr() >= sub.getPagingStore().getFirstPage() && encoding.position.getPageNr() <= sub.getPagingStore().getCurrentWritingPage()) {
                           sub.reloadACK(encoding.position);
                        } else {
                           ActiveMQServerLogger.LOGGER.cannotFindPageFileDuringPageAckReload(encoding.position.getPageNr(), sub.getPagingStore().getStoreName(), record.id);
                           messageJournal.tryAppendDeleteRecord(record.id, this::recordNotFoundCallback, false);
                        }
                     }

                     break;
                  }
                  case JournalRecordIds.PAGE_CURSOR_COUNTER_VALUE: {
                     PageCountRecord encoding = new PageCountRecord();

                     encoding.decode(buff);

                     PageSubscription sub = locateSubscription(encoding.getQueueID(), pageSubscriptions, queueInfos, pagingManager);

                     if (sub != null) {
                        sub.getCounter().loadValue(record.id, encoding.getValue(), encoding.getPersistentSize());
                        if (encoding.getValue() > 0) {
                           sub.notEmpty();
                        }
                     } else {
                        ActiveMQServerLogger.LOGGER.journalCannotFindQueueReloadingPage(encoding.getQueueID());
                        messageJournal.tryAppendDeleteRecord(record.id, this::recordNotFoundCallback, false);
                     }

                     break;
                  }

                  case JournalRecordIds.PAGE_CURSOR_COUNTER_INC: {
                     PageCountRecordInc encoding = new PageCountRecordInc();

                     encoding.decode(buff);

                     PageSubscription sub = locateSubscription(encoding.getQueueID(), pageSubscriptions, queueInfos, pagingManager);

                     if (sub != null) {
                        sub.getCounter().loadInc(record.id, encoding.getValue(), encoding.getPersistentSize());
                     } else {
                        ActiveMQServerLogger.LOGGER.journalCannotFindQueueReloadingPageCursor(encoding.getQueueID());
                        messageJournal.tryAppendDeleteRecord(record.id, this::recordNotFoundCallback, false);
                     }

                     break;
                  }

                  case JournalRecordIds.PAGE_CURSOR_COMPLETE: {
                     CursorAckRecordEncoding encoding = new CursorAckRecordEncoding();
                     encoding.decode(buff);

                     encoding.position.setRecordID(record.id);

                     PageSubscription sub = locateSubscription(encoding.queueID, pageSubscriptions, queueInfos, pagingManager);

                     if (sub != null) {
                        if (!sub.reloadPageCompletion(encoding.position)) {
                           if (logger.isDebugEnabled()) {
                              logger.debug("Complete page {} doesn't exist on page manager {}", encoding.position.getPageNr(), sub.getPagingStore().getAddress());
                           }
                           messageJournal.tryAppendDeleteRecord(record.id, this::recordNotFoundCallback, false);
                        }
                     } else {
                        ActiveMQServerLogger.LOGGER.cantFindQueueOnPageComplete(encoding.queueID);
                        messageJournal.tryAppendDeleteRecord(record.id, this::recordNotFoundCallback, false);
                     }

                     break;
                  }

                  case JournalRecordIds.PAGE_CURSOR_PENDING_COUNTER: {

                     PageCountPendingImpl pendingCountEncoding = new PageCountPendingImpl();

                     pendingCountEncoding.decode(buff);
                     pendingCountEncoding.setID(record.id);
                     PageSubscription sub = locateSubscription(pendingCountEncoding.getQueueID(), pageSubscriptions, queueInfos, pagingManager);
                     if (sub != null) {
                        sub.notEmpty();
                     }
                     // This can be null on testcases not interested on this outcome
                     if (pendingNonTXPageCounter != null) {
                        pendingNonTXPageCounter.add(pendingCountEncoding);
                     }

                     break;
                  }

                  default: {
                     logger.debug("Extra record type {}", record.userRecordType);
                     if (journalRecordsListener != null) {
                        journalRecordsListener.forEach(f -> f.accept(record));
                     }
                  }
               }
            } catch (RuntimeException e) {
               throw e;
            } catch (Exception e) {
               throw new RuntimeException(e);
            }
         });

         // Release the memory as soon as not needed any longer
         records = null;

         journalLoader.handleAddMessage(queueMap);

         loadPreparedTransactions(postOffice, pagingManager, resourceManager, queueInfos, preparedTransactions, this::failedToPrepareException, pageSubscriptions, pendingLargeMessages, storedLargeMessages, journalLoader);

         for (PageSubscription sub : pageSubscriptions.values()) {
            sub.getCounter().processReload();
         }

         for (LargeServerMessage msg : largeMessages) {
            if (storedLargeMessages != null && storedLargeMessages.remove(msg.getMessageID())) {
               if (logger.isDebugEnabled()) {
                  logger.debug("Large message in folder removed on {}", msg.getMessageID());
               }
            }
            if (msg.toMessage().getRefCount() == 0 && msg.toMessage().getDurableCount() == 0) {
               ActiveMQServerLogger.LOGGER.largeMessageWithNoRef(msg.getMessageID());
               msg.toMessage().usageDown();
            }
         }

         journalLoader.handleNoMessageReferences(messages);

         // To recover positions on Iterators
         if (pagingManager != null) {
            // it could be null on certain tests that are not dealing with paging
            // This could also be the case in certain embedded conditions
            pagingManager.processReload();
         }

         journalLoader.postLoad(messageJournal, resourceManager, duplicateIDMap);

         checkInvalidPageTransactions(pagingManager, invalidPageTransactions);

         journalLoaded = true;
         return info;
      }
   }

   private void failedToPrepareException(PreparedTransactionInfo txInfo, Throwable e) {
      XidEncoding encodingXid = null;
      try {
         encodingXid = new XidEncoding(txInfo.getExtraData());
      } catch (Throwable ignored) {
      }

      ActiveMQServerLogger.LOGGER.failedToLoadPreparedTX(String.valueOf(encodingXid != null ? encodingXid.xid : null), e);

      try {
         Transaction tx = new TransactionImpl(txInfo.getId(), null, this);
         rollback(tx);
      } catch (Throwable e2) {
         logger.warn(e.getMessage(), e2);
      }
   }

   private Message decodeMessage(CoreMessageObjectPools pools, ActiveMQBuffer buff) {
      Message message = MessagePersister.getInstance().decode(buff, null, pools, this);
      return message;
   }

   public void checkInvalidPageTransactions(PagingManager pagingManager,
                                            Set<PageTransactionInfo> invalidPageTransactions) {
      if (invalidPageTransactions != null && !invalidPageTransactions.isEmpty()) {
         for (PageTransactionInfo pginfo : invalidPageTransactions) {
            pginfo.checkSize(this, pagingManager);
         }
      }
   }

   private static PageSubscription locateSubscription(final long queueID,
                                                      final Map<Long, PageSubscription> pageSubscriptions,
                                                      final Map<Long, QueueBindingInfo> queueInfos,
                                                      final PagingManager pagingManager) throws Exception {

      PageSubscription subs = pageSubscriptions.get(queueID);
      if (subs == null) {
         QueueBindingInfo queueInfo = queueInfos.get(queueID);

         if (queueInfo != null) {
            SimpleString address = queueInfo.getQueueConfiguration().getAddress();
            PagingStore store = pagingManager.getPageStore(address);
            if (store == null) {
               return null;
            }
            subs = store.getCursorProvider().getSubscription(queueID);
            pageSubscriptions.put(queueID, subs);
         }
      }

      return subs;
   }

   // grouping handler operations
   @Override
   public void addGrouping(final GroupBinding groupBinding) throws Exception {
      GroupingEncoding groupingEncoding = new GroupingEncoding(groupBinding.getId(), groupBinding.getGroupId(), groupBinding.getClusterName());
      try (ArtemisCloseable lock = closeableReadLock()) {
         bindingsJournal.appendAddRecord(groupBinding.getId(), JournalRecordIds.GROUP_RECORD, groupingEncoding, true);
      }
   }

   @Override
   public void deleteGrouping(Transaction tx, final GroupBinding groupBinding) throws Exception {
      try (ArtemisCloseable lock = closeableReadLock()) {
         bindingsJournal.appendDeleteRecordTransactional(tx.getID(), groupBinding.getId());
      }
   }

   // BindingsImpl operations

   @Override
   public void updateQueueBinding(Transaction tx, Binding binding, AddressInfo addressInfo) throws Exception {
      internalQueueBinding(true, tx.getID(), binding);
   }

   @Override
   public void addQueueBinding(Transaction tx, final Binding binding, AddressInfo addressInfo) throws Exception {
      internalQueueBinding(false, tx.getID(), binding);
   }

   private void internalQueueBinding(boolean update, final long tx, final Binding binding) throws Exception {
      Queue queue = (Queue) binding.getBindable();

      Filter filter = queue.getFilter();

      SimpleString filterString = filter == null ? null : filter.getFilterString();

      PersistentQueueBindingEncoding bindingEncoding = new PersistentQueueBindingEncoding(queue.getQueueConfiguration());

      try (ArtemisCloseable lock = closeableReadLock()) {
         if (update) {
            bindingsJournal.appendUpdateRecordTransactional(tx, binding.getID(), JournalRecordIds.QUEUE_BINDING_RECORD, bindingEncoding);
         } else {
            bindingsJournal.appendAddRecordTransactional(tx, binding.getID(), JournalRecordIds.QUEUE_BINDING_RECORD, bindingEncoding);
         }
      }
   }

   @Override
   public void deleteQueueBinding(Transaction tx, final long queueBindingID) throws Exception {
      try (ArtemisCloseable lock = closeableReadLock()) {
         bindingsJournal.appendDeleteRecordTransactional(tx.getID(), queueBindingID);
      }
   }

   @Override
   public long storeQueueStatus(long queueID, AddressQueueStatus status) throws Exception {
      long recordID = idGenerator.generateID();

      try (ArtemisCloseable lock = closeableReadLock()) {
         bindingsJournal.appendAddRecord(recordID, JournalRecordIds.QUEUE_STATUS_RECORD, new QueueStatusEncoding(queueID, status), true);
      }

      return recordID;
   }

   @Override
   public void deleteQueueStatus(long recordID) throws Exception {
      deleteRecordSync(recordID);
   }

   @Override
   public long storeAddressStatus(long addressID, AddressQueueStatus status) throws Exception {
      long recordID = idGenerator.generateID();

      try (ArtemisCloseable lock = closeableReadLock()) {
         bindingsJournal.appendAddRecord(recordID, JournalRecordIds.ADDRESS_STATUS_RECORD, new AddressStatusEncoding(addressID, status), true);
      }

      return recordID;
   }

   @Override
   public void deleteAddressStatus(long recordID) throws Exception {
      deleteRecordSync(recordID);
   }

   @Override
   public void addAddressBinding(Transaction tx, final AddressInfo addressInfo) throws Exception {
      PersistentAddressBindingEncoding bindingEncoding = new PersistentAddressBindingEncoding(addressInfo.getName(), addressInfo.getRoutingTypes(), addressInfo.isAutoCreated(), addressInfo.isInternal());

      try (ArtemisCloseable lock = closeableReadLock()) {
         long recordID = idGenerator.generateID();
         bindingEncoding.setId(recordID);
         addressInfo.setId(recordID);
         bindingsJournal.appendAddRecordTransactional(tx.getID(), recordID, JournalRecordIds.ADDRESS_BINDING_RECORD, bindingEncoding);
      }
   }

   @Override
   public void deleteAddressBinding(Transaction tx, final long addressBindingID) throws Exception {
      try (ArtemisCloseable lock = closeableReadLock()) {
         bindingsJournal.appendDeleteRecordTransactional(tx.getID(), addressBindingID);
      }
   }

   @Override
   public long storePageCounterInc(Transaction tx, long queueID, int value, long persistentSize) throws Exception {
      try (ArtemisCloseable lock = closeableReadLock()) {
         long recordID = idGenerator.generateID();
         messageJournal.appendAddRecordTransactional(tx.getID(), recordID, JournalRecordIds.PAGE_CURSOR_COUNTER_INC, new PageCountRecordInc(queueID, value, persistentSize));
         return recordID;
      }
   }

   @Override
   public long storePageCounterInc(long queueID, int value, long persistentSize) throws Exception {
      try (ArtemisCloseable lock = closeableReadLock()) {
         final long recordID = idGenerator.generateID();
         messageJournal.appendAddRecord(recordID, JournalRecordIds.PAGE_CURSOR_COUNTER_INC, new PageCountRecordInc(queueID, value, persistentSize), true, getContext());
         return recordID;
      }
   }

   @Override
   public long storePageCounter(Transaction tx, long queueID, long value, long persistentSize) throws Exception {
      try (ArtemisCloseable lock = closeableReadLock()) {
         final long recordID = idGenerator.generateID();
         messageJournal.appendAddRecordTransactional(tx.getID(), recordID, JournalRecordIds.PAGE_CURSOR_COUNTER_VALUE, new PageCountRecord(queueID, value, persistentSize));
         return recordID;
      }
   }

   @Override
   public long storePendingCounter(final long queueID, final long pageID) throws Exception {
      try (ArtemisCloseable lock = closeableReadLock()) {
         final long recordID = idGenerator.generateID();
         PageCountPendingImpl pendingInc = new PageCountPendingImpl(queueID, pageID);
         // We must guarantee the record sync before we actually write on the page otherwise we may get out of sync
         // on the counter
         messageJournal.appendAddRecord(recordID, JournalRecordIds.PAGE_CURSOR_PENDING_COUNTER, pendingInc, true);
         return recordID;
      }
   }

   @Override
   public void deleteIncrementRecord(final Transaction tx, long recordID) throws Exception {
      try (ArtemisCloseable lock = closeableReadLock()) {
         messageJournal.appendDeleteRecordTransactional(tx.getID(), recordID);
      }
   }

   @Override
   public void deletePageCounter(final Transaction tx, long recordID) throws Exception {
      try (ArtemisCloseable lock = closeableReadLock()) {
         messageJournal.appendDeleteRecordTransactional(tx.getID(), recordID);
      }
   }

   @Override
   public void deletePendingPageCounter(final Transaction tx, long recordID) throws Exception {
      try (ArtemisCloseable lock = closeableReadLock()) {
         messageJournal.appendDeleteRecordTransactional(tx.getID(), recordID);
      }
   }

   @Override
   public JournalLoadInformation loadBindingJournal(final List<QueueBindingInfo> queueBindingInfos,
                                                    final List<GroupingInfo> groupingInfos,
                                                    final List<AddressBindingInfo> addressBindingInfos) throws Exception {
      SparseArrayLinkedList<RecordInfo> records = new SparseArrayLinkedList<>();

      List<PreparedTransactionInfo> preparedTransactions = new ArrayList<>();

      bindingsJournal.setRemoveExtraFilesOnLoad(true);

      JournalLoadInformation bindingsInfo = bindingsJournal.load(records, preparedTransactions, null);

      Map<Long, PersistentQueueBindingEncoding> mapBindings = new HashMap<>();
      Map<Long, PersistentAddressBindingEncoding> mapAddressBindings = new HashMap<>();

      records.clear(record -> {
         try {
            long id = record.id;

            ActiveMQBuffer buffer = ActiveMQBuffers.wrappedBuffer(record.data);

            byte rec = record.getUserRecordType();

            if (rec == JournalRecordIds.QUEUE_BINDING_RECORD) {
               PersistentQueueBindingEncoding bindingEncoding = newQueueBindingEncoding(id, buffer);
               mapBindings.put(bindingEncoding.getQueueConfiguration().getId(), bindingEncoding);
            } else if (rec == JournalRecordIds.ID_COUNTER_RECORD) {
               idGenerator.loadState(record.id, buffer);
            } else if (rec == JournalRecordIds.ADDRESS_BINDING_RECORD) {
               PersistentAddressBindingEncoding bindingEncoding = newAddressBindingEncoding(id, buffer);
               addressBindingInfos.add(bindingEncoding);
               mapAddressBindings.put(id, bindingEncoding);
            } else if (rec == JournalRecordIds.GROUP_RECORD) {
               GroupingEncoding encoding = newGroupEncoding(id, buffer);
               groupingInfos.add(encoding);
            } else if (rec == JournalRecordIds.ADDRESS_SETTING_RECORD) {
               PersistedAddressSetting setting = newPersistedConfigurationEncoding(PersistedAddressSetting.class, id, buffer);
               mapPersistedAddressSettings.put(setting.getName(), setting);
            } else if (rec == JournalRecordIds.ADDRESS_SETTING_RECORD_JSON) {
               PersistedAddressSettingJSON setting = newPersistedConfigurationEncoding(PersistedAddressSettingJSON.class, id, buffer);
               mapPersistedAddressSettings.put(setting.getName(), setting);
            } else if (rec == JournalRecordIds.SECURITY_SETTING_RECORD) {
               PersistedSecuritySetting roles = newPersistedConfigurationEncoding(PersistedSecuritySetting.class, id, buffer);
               mapPersistedSecuritySettings.put(roles.getName(), roles);
            } else if (rec == JournalRecordIds.QUEUE_STATUS_RECORD) {
               QueueStatusEncoding statusEncoding = newQueueStatusEncoding(id, buffer);
               PersistentQueueBindingEncoding queueBindingEncoding = mapBindings.get(statusEncoding.queueID);
               if (queueBindingEncoding != null) {
                  queueBindingEncoding.addQueueStatusEncoding(statusEncoding);
               } else {
                  // unlikely to happen, so I didn't bother about the Logger method
                  ActiveMQServerLogger.LOGGER.infoNoQueueWithID(statusEncoding.queueID, statusEncoding.getId());
                  this.deleteQueueStatus(statusEncoding.getId());
               }
            } else if (rec == JournalRecordIds.ADDRESS_STATUS_RECORD) {
               AddressStatusEncoding statusEncoding = newAddressStatusEncoding(id, buffer);
               PersistentAddressBindingEncoding addressBindingEncoding = mapAddressBindings.get(statusEncoding.getAddressId());
               if (addressBindingEncoding != null) {
                  addressBindingEncoding.setAddressStatusEncoding(statusEncoding);
               } else {
                  // unlikely to happen, so I didn't bother about the Logger method
                  ActiveMQServerLogger.LOGGER.infoNoAddressWithID(statusEncoding.getAddressId(), statusEncoding.getId());
                  this.deleteAddressStatus(statusEncoding.getId());
               }
            } else if (rec == JournalRecordIds.DIVERT_RECORD) {
               PersistedDivertConfiguration divertConfiguration = newPersistedConfigurationEncoding(PersistedDivertConfiguration.class, id, buffer);
               mapPersistedDivertConfigurations.put(divertConfiguration.getName(), divertConfiguration);
            } else if (rec == JournalRecordIds.BRIDGE_RECORD) {
               PersistedBridgeConfiguration bridgeConfiguration = newPersistedConfigurationEncoding(PersistedBridgeConfiguration.class, id, buffer);
               mapPersistedBridgeConfigurations.put(bridgeConfiguration.getName(), bridgeConfiguration);
            } else if (rec == JournalRecordIds.USER_RECORD) {
               PersistedUser user = newPersistedConfigurationEncoding(PersistedUser.class, id, buffer);
               mapPersistedUsers.put(user.getUsername(), user);
            } else if (rec == JournalRecordIds.ROLE_RECORD) {
               PersistedRole role = newPersistedConfigurationEncoding(PersistedRole.class, id, buffer);
               mapPersistedRoles.put(role.getUsername(), role);
            } else if (rec == JournalRecordIds.KEY_VALUE_PAIR_RECORD) {
               insertPersistedKeyValuePair(newPersistedConfigurationEncoding(PersistedKeyValuePair.class, id, buffer));
            } else if (rec == JournalRecordIds.CONNECTOR_RECORD) {
               PersistedConnector connector = newPersistedConfigurationEncoding(PersistedConnector.class, id, buffer);
               mapPersistedConnectors.put(connector.getName(), connector);
            } else {
               // unlikely to happen
               ActiveMQServerLogger.LOGGER.invalidRecordType(rec, new Exception("invalid record type " + rec));
            }
         } catch (RuntimeException e) {
            throw e;
         } catch (Exception e) {
            throw new RuntimeException(e);
         }
      });

      for (PersistentQueueBindingEncoding queue : mapBindings.values()) {
         queueBindingInfos.add(queue);
      }

      mapBindings.clear(); // just to give a hand to GC

      // This will instruct the IDGenerator to beforeStop old records
      idGenerator.cleanup();

      return bindingsInfo;
   }

   private PersistedKeyValuePair insertPersistedKeyValuePair(final PersistedKeyValuePair keyValuePair) {
      Map<String, PersistedKeyValuePair> persistedKeyValuePairs = mapPersistedKeyValuePairs.get(keyValuePair.getMapId());
      if (persistedKeyValuePairs == null) {
         ConcurrentMap<String, PersistedKeyValuePair> newMap = new ConcurrentHashMap<>();
         Map<String, PersistedKeyValuePair> existingMap = mapPersistedKeyValuePairs.putIfAbsent(keyValuePair.getMapId(), newMap);

         persistedKeyValuePairs = Objects.requireNonNullElse(existingMap, newMap);
      }

      return persistedKeyValuePairs.put(keyValuePair.getKey(), keyValuePair);
   }

   protected abstract void beforeStart() throws Exception;

   @Override
   public synchronized void start() throws Exception {
      if (isStarted()) {
         return;
      }

      beforeStart();

      bindingsJournal.start();

      if (config.getJournalRetentionLocation() != null) {
         messageJournal.getFileFactory().start();
         messageJournal.setHistoryFolder(config.getJournalRetentionLocation(), config.getJournalRetentionMaxBytes(), config.getJournalRetentionPeriod());
      }
      messageJournal.start();

      super.start();
   }

   @Override
   public synchronized void persistIdGenerator() {
      if (journalLoaded && idGenerator != null) {
         // Must call close to make sure last id is persisted
         idGenerator.persistCurrentID();
      }
   }

   /**
    * Assumption is that this is only called with a writeLock on the StorageManager.
    */
   protected abstract void performCachedLargeMessageDeletes();

   @Override
   public synchronized void stop(boolean ioCriticalError, boolean sendFailover) throws Exception {
      if (!isStarted()) {
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

      journalLoaded = false;

      super.stop(ioCriticalError, sendFailover);
   }

   protected abstract void beforeStop() throws Exception;

   // TODO: Is this still being used ?
   public JournalLoadInformation[] loadInternalOnly() throws Exception {
      try (ArtemisCloseable lock = closeableReadLock()) {
         JournalLoadInformation[] info = new JournalLoadInformation[2];
         info[0] = bindingsJournal.loadInternalOnly();
         info[1] = messageJournal.loadInternalOnly();

         return info;
      }
   }

   @Override
   public Journal getMessageJournal() {
      return messageJournal;
   }

   @Override
   public Journal getBindingsJournal() {
      return bindingsJournal;
   }

   protected abstract LargeServerMessage parseLargeMessage(ActiveMQBuffer buff) throws Exception;

   private void loadPreparedTransactions(final PostOffice postOffice,
                                         final PagingManager pagingManager,
                                         final ResourceManager resourceManager,
                                         final Map<Long, QueueBindingInfo> queueInfos,
                                         final List<PreparedTransactionInfo> preparedTransactions,
                                         final BiConsumer<PreparedTransactionInfo, Throwable> failedTransactionCallback,
                                         final Map<Long, PageSubscription> pageSubscriptions,
                                         final Set<Pair<Long, Long>> pendingLargeMessages,
                                         final Set<Long> storedLargeMessages,
                                         JournalLoader journalLoader) throws Exception {
      // recover prepared transactions
      final CoreMessageObjectPools pools = new CoreMessageObjectPools();

      for (PreparedTransactionInfo preparedTransaction : preparedTransactions) {
         try {
            loadSinglePreparedTransaction(postOffice, pagingManager, resourceManager, queueInfos, pageSubscriptions, pendingLargeMessages, storedLargeMessages, journalLoader, pools, preparedTransaction);
         } catch (Throwable e) {
            if (failedTransactionCallback != null) {
               failedTransactionCallback.accept(preparedTransaction, e);
            } else {
               logger.warn(e.getMessage(), e);
            }
         }
      }
   }

   private void loadSinglePreparedTransaction(PostOffice postOffice,
                          PagingManager pagingManager,
                          ResourceManager resourceManager,
                          Map<Long, QueueBindingInfo> queueInfos,
                          Map<Long, PageSubscription> pageSubscriptions,
                          Set<Pair<Long, Long>> pendingLargeMessages,
                          final Set<Long> storedLargeMessages,
                          JournalLoader journalLoader,
                          CoreMessageObjectPools pools,
                          PreparedTransactionInfo preparedTransaction) throws Exception {
      XidEncoding encodingXid = new XidEncoding(preparedTransaction.getExtraData());

      Xid xid = encodingXid.xid;

      Transaction tx = new TransactionImpl(preparedTransaction.getId(), xid, this);

      List<MessageReference> referencesToAck = new ArrayList<>();

      Map<Long, Message> messages = new HashMap<>();

      // Use same method as load message journal to prune out acks, so they don't get added.
      // Then have reacknowledge(tx) methods on queue, which needs to add the page size

      // first get any sent messages for this tx and recreate
      for (RecordInfo record : preparedTransaction.getRecords()) {
         byte[] data = record.data;

         ActiveMQBuffer buff = ActiveMQBuffers.wrappedBuffer(data);

         byte recordType = record.getUserRecordType();

         switch (recordType) {
            case JournalRecordIds.ADD_LARGE_MESSAGE: {
               if (storedLargeMessages != null && storedLargeMessages.remove(record.id)) {
                  if (logger.isDebugEnabled()) {
                     logger.debug("PreparedTX/AddLargeMessage load removing stored large message {}", record.id);
                  }
               }
               messages.put(record.id, parseLargeMessage(buff).toMessage());

               break;
            }
            case JournalRecordIds.ADD_MESSAGE: {

               break;
            }
            case JournalRecordIds.ADD_MESSAGE_PROTOCOL: {
               Message message = decodeMessage(pools, buff);
               if (storedLargeMessages != null && message.isLargeMessage() && storedLargeMessages.remove(record.id)) {
                  logger.debug("PreparedTX/AddMessgeProtocol load removing stored large message {}", record.id);
               }

               messages.put(record.id, message);

               break;
            }
            case JournalRecordIds.ADD_REF: {
               long messageID = record.id;

               RefEncoding encoding = new RefEncoding();

               encoding.decode(buff);

               Message message = messages.get(messageID);

               if (message == null) {
                  throw new IllegalStateException("Cannot find message with id " + messageID);
               }

               journalLoader.handlePreparedSendMessage(message, tx, encoding.queueID);

               break;
            }
            case JournalRecordIds.ACKNOWLEDGE_REF: {
               long messageID = record.id;

               RefEncoding encoding = new RefEncoding();

               encoding.decode(buff);

               journalLoader.handlePreparedAcknowledge(messageID, referencesToAck, encoding.queueID);

               break;
            }
            case JournalRecordIds.PAGE_TRANSACTION: {

               PageTransactionInfo pageTransactionInfo = new PageTransactionInfoImpl();

               pageTransactionInfo.decode(buff);

               if (record.isUpdate) {
                  PageTransactionInfo pgTX = pagingManager.getTransaction(pageTransactionInfo.getTransactionID());
                  if (pgTX != null) {
                     pgTX.reloadUpdate(this, pagingManager, tx, pageTransactionInfo.getNumberOfMessages());
                  }
               } else {
                  pageTransactionInfo.reloadPrepared(tx);

                  tx.putProperty(TransactionPropertyIndexes.PAGE_TRANSACTION, pageTransactionInfo);

                  pagingManager.addTransaction(pageTransactionInfo);

                  tx.addOperation(new FinishPageMessageOperation());
               }

               break;
            }
            case SET_SCHEDULED_DELIVERY_TIME: {
               // Do nothing - for prepared txs, the set scheduled delivery time will only occur in a send in which
               // case the message will already have the header for the scheduled delivery time, so no need to do
               // anything.

               break;
            }
            case DUPLICATE_ID: {
               // We need load the duplicate ids at prepare time too
               DuplicateIDEncoding encoding = new DuplicateIDEncoding();

               encoding.decode(buff);

               DuplicateIDCache cache = postOffice.getDuplicateIDCache(encoding.address);

               cache.load(tx, encoding.duplID);

               break;
            }
            case ACKNOWLEDGE_CURSOR: {
               CursorAckRecordEncoding encoding = new CursorAckRecordEncoding();
               encoding.decode(buff);

               encoding.position.setRecordID(record.id);

               PageSubscription sub = locateSubscription(encoding.queueID, pageSubscriptions, queueInfos, pagingManager);

               if (sub != null) {
                  sub.reloadPreparedACK(tx, encoding.position);
                  QueryPagedReferenceImpl reference = new QueryPagedReferenceImpl(encoding.position, null, sub);
                  referencesToAck.add(reference);
                  if (sub.getQueue() != null) {
                     sub.getQueue().reloadSequence(reference);
                  }
               } else {
                  ActiveMQServerLogger.LOGGER.journalCannotFindQueueReloadingACK(encoding.queueID);
               }
               break;
            }
            case PAGE_CURSOR_COUNTER_INC: {
               PageCountRecordInc encoding = new PageCountRecordInc();

               encoding.decode(buff);

               logger.debug("Page cursor counter inc on a prepared TX.");

               // TODO: do I need to remove the record on commit?

               break;
            }

            default: {
               ActiveMQServerLogger.LOGGER.journalInvalidRecordType(recordType);
            }
         }
      }

      for (RecordInfo recordDeleted : preparedTransaction.getRecordsToDelete()) {
         byte[] data = recordDeleted.data;

         if (data.length > 0) {
            ActiveMQBuffer buff = ActiveMQBuffers.wrappedBuffer(data);
            byte b = buff.readByte();

            switch (b) {
               case ADD_LARGE_MESSAGE_PENDING: {
                  // reading just to position the buffer, not used any more
                  buff.readLong();
               }
               default:
                  ActiveMQServerLogger.LOGGER.journalInvalidRecordTypeOnPreparedTX(b);
            }
         }

      }

      journalLoader.handlePreparedTransaction(tx, referencesToAck, xid, resourceManager);
   }

   OperationContext getContext(final boolean sync) {
      if (sync) {
         return getContext();
      } else {
         return DummyOperationContext.getInstance();
      }
   }


   private static final class DummyOperationContext implements OperationContext {

      private static DummyOperationContext instance = new DummyOperationContext();

      public static OperationContext getInstance() {
         return DummyOperationContext.instance;
      }

      @Override
      public void executeOnCompletion(final IOCallback runnable) {
         // There are no executeOnCompletion calls while using the DummyOperationContext
         // However we keep the code here for correctness
         runnable.done();
      }

      @Override
      public void executeOnCompletion(IOCallback runnable, OperationConsistencyLevel consistencyLevel) {
         // There are no executeOnCompletion calls while using the DummyOperationContext
         // However we keep the code here for correctness
         runnable.done();
      }

      @Override
      public void replicationDone() {
      }

      @Override
      public void replicationLineUp() {
      }

      @Override
      public void storeLineUp() {
      }

      @Override
      public void done() {
      }

      @Override
      public void onError(final int errorCode, final String errorMessage) {
      }

      @Override
      public void waitCompletion() {
      }

      @Override
      public boolean waitCompletion(final long timeout) {
         return true;
      }

      @Override
      public void pageSyncLineUp() {
      }

      @Override
      public void pageSyncDone() {
      }
   }

   public static AddressStatusEncoding newAddressStatusEncoding(long id, ActiveMQBuffer buffer) {
      AddressStatusEncoding addressStatus = new AddressStatusEncoding();
      addressStatus.decode(buffer);
      addressStatus.setId(id);
      return addressStatus;
   }

   public static <T extends PersistedConfiguration> T newPersistedConfigurationEncoding(Class<T> clazz, long id, ActiveMQBuffer buffer) {
      try {
         T persistedConfiguration = clazz.getDeclaredConstructor().newInstance();
         persistedConfiguration.decode(buffer);
         persistedConfiguration.setStoreId(id);
         return persistedConfiguration;
      } catch (Exception e) {
         throw new RuntimeException("Error creating instance of " + clazz.getSimpleName(), e);
      }
   }

   public static GroupingEncoding newGroupEncoding(long id, ActiveMQBuffer buffer) {
      GroupingEncoding encoding = new GroupingEncoding();
      encoding.decode(buffer);
      encoding.setId(id);
      return encoding;
   }

   protected static PersistentQueueBindingEncoding newQueueBindingEncoding(long id, ActiveMQBuffer buffer) {
      PersistentQueueBindingEncoding bindingEncoding = new PersistentQueueBindingEncoding();

      bindingEncoding.decode(buffer);

      bindingEncoding.getQueueConfiguration().setId(id);
      return bindingEncoding;
   }

   public static QueueStatusEncoding newQueueStatusEncoding(long id, ActiveMQBuffer buffer) {
      QueueStatusEncoding statusEncoding = new QueueStatusEncoding();

      statusEncoding.decode(buffer);
      statusEncoding.setId(id);

      return statusEncoding;
   }

   protected static PersistentAddressBindingEncoding newAddressBindingEncoding(long id, ActiveMQBuffer buffer) {
      PersistentAddressBindingEncoding bindingEncoding = new PersistentAddressBindingEncoding();

      bindingEncoding.decode(buffer);

      bindingEncoding.setId(id);
      return bindingEncoding;
   }

   @Override
   public void setMirrorSNFPagingStore(PagingStore snfPagingStore) {
      this.mirrorSNFPagingStore = snfPagingStore;
   }

   @Override
   public boolean flowControl(PagingStore store, Runnable block, Runnable release) {
      if (store != null) {
         if (store.flowControl(block, release)) {
            if (logger.isDebugEnabled()) {
               logger.debug("Blocking flowControl from regular storage {}", store.getAddress());
            }
            return true;
         }
      }

      PagingStore snfStore = this.mirrorSNFPagingStore;
      if (snfStore != null && snfStore != store) {
         if (snfStore.flowControl(block, release)) {
            logger.debug("Blocking flowControl from mirrorSNF {}", snfStore.getAddress());
            return true;
         }
      }

      return false;
   }

   @Override
   public boolean addToPage(PagingStore store, Message msg, Transaction tx, RouteContextList listCtx) throws Exception {
      int credits;
      try (ArtemisCloseable closeable = closeableReadLock()) {
         credits = store.page(msg, tx, listCtx, null);
      }

      return credits >= 0;
   }
}
