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
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.DuplicateIDEncoding;
import org.apache.activemq.artemis.core.postoffice.DuplicateIDCache;
import org.apache.activemq.artemis.core.postoffice.PostOffice;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.core.transaction.impl.TransactionImpl;
import org.apache.activemq.artemis.utils.XidCodecSupport;
import org.apache.artemis.database.DatabaseProvider;
import org.apache.artemis.database.DatabaseTX;
import org.apache.artemis.database.data.DeleteReferenceData;
import org.apache.artemis.database.data.GenericData;
import org.apache.artemis.database.data.MessageData;
import org.apache.artemis.database.data.MessageReferenceData;
import org.apache.artemis.database.data.PrepareTXData;
import org.apache.artemis.database.data.TXStatus;
import org.apache.artemis.database.queries.GenericDataJDBCQuery;
import org.apache.artemis.database.queries.MessagesAndReferencesDBQuery;
import org.apache.artemis.database.queries.PreparedTXQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PreparedTXLoader {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public Collection<TransactionImpl> loadPreparedTransactions(DatabaseProvider dbProvider,
                                                               Connection connection,
                                                               DatabaseStorageManager storageManager,
                                                               PostOffice postOffice,
                                                               Map<Long, Queue> queues) throws Exception {
      PreparedTXQuery txQuery = new PreparedTXQuery(dbProvider, connection);
      ArrayList<PrepareTXData> list = new ArrayList<>();
      txQuery.query(list::add);

      if (list.isEmpty()) {
         return Collections.emptyList();
      }

      Map<Long, TransactionImpl> transactionMap = new HashMap<>();
      for (PrepareTXData data : list) {
         Xid xid = XidCodecSupport.decodeXid(data.xid);
         TransactionImpl transaction = new TransactionImpl(data.getTx().getId(), xid, storageManager);
         transaction.setState(Transaction.State.PREPARED);
         DatabaseTX databaseTX = (DatabaseTX) transaction.getStorageTx();
         databaseTX.setStatus(TXStatus.prepared);
         databaseTX.addData(new PrepareTXData(databaseTX, data.xid.duplicate(), null));
         transactionMap.put(data.getTx().getId(), transaction);
      }

      GenericDataJDBCQuery genericDataJDBCQuery = new GenericDataJDBCQuery(dbProvider, connection, dbProvider.getSqlProvider().getBrokerData(), false, dbProvider.getSqlProvider().whereClauseLoadTXGenericData());
      genericDataJDBCQuery.query(d -> {
         if (d.getTx() != null) { // we should only have transactioned ones. this is defensive code
            TransactionImpl txImpl = transactionMap.get(d.getTx().getId());
            if (txImpl != null) {
               DatabaseTX databaseTX = (DatabaseTX) txImpl.getStorageTx();
               Object decoded = DatabaseStorageManager.describeGenericData(d.id, d.recordType, d.dataSupplier != null ? d.dataSupplier.get() : null);
               if (decoded instanceof DuplicateIDEncoding encoding) {
                  GenericData genericData = new GenericData(d.id, d.recordType, d.dataSupplier, false, null);
                  databaseTX.addData(genericData);
                  DuplicateIDCache cache = postOffice.getDuplicateIDCache(encoding.address);
                  cache.load(txImpl, encoding.duplID);
               } else {
                  logger.warn("Unexpected record type {} in prepared transaction {}", d.recordType, d.getTx().getId());
               }
            }
         }
      });

      MessagesAndReferencesDBQuery dbQuery = new MessagesAndReferencesDBQuery(dbProvider, connection, dbProvider.getSqlProvider().whereClauseLoadTXMessages());
      dbQuery.query(d -> {
         if (d.tx() != null) {
            TransactionImpl txImpl = transactionMap.get(d.tx());
            if (txImpl != null) {
               DatabaseTX databaseTX = (DatabaseTX) txImpl.getStorageTx();

               MessageData messageData = new MessageData(d.messageID(), d.messageBufferSupplier(), d.memoryEstimate(), d.isLarge(), d.isCore(), d.largeBodySupplier(), null);
               databaseTX.addData(messageData);
               Message message = DatabaseStorageManager.decodeMessage(d.messageID(), d.isLarge(), d.isCore(), d.messageBufferSupplier(), d.largeBodySupplier(), storageManager);

               for (var ref : d.queues()) {
                  if (ref.ackTx() == null) {
                     MessageReferenceData referenceData = new MessageReferenceData(d.messageID(), ref.queueID(), ref.paged(), d.tx(), null);
                     databaseTX.addData(referenceData);
                     if (!ref.paged()) {
                        Queue queue = queues.get(ref.queueID());
                        if (queue == null) {
                           ActiveMQServerLogger.LOGGER.journalMessageInPreparedTX(ref.queueID());
                           continue;
                        }
                        try {
                           postOffice.reload(message, queue, txImpl);
                        } catch (Exception e) {
                           logger.warn(e.getMessage(), e);
                        }
                     }
                  }
               }

            }
         } else {
            for (var ref : d.queues()) {
               if (ref.ackTx() != null) {
                  TransactionImpl txImpl = transactionMap.get(ref.ackTx());
                  if (txImpl != null) {
                     DatabaseTX databaseTX = (DatabaseTX) txImpl.getStorageTx();
                     DeleteReferenceData referenceData = new DeleteReferenceData(ref.queueID(), d.messageID(), null);
                     databaseTX.addData(referenceData);
                     Queue queue = queues.get(ref.queueID());
                     if (queue == null) {
                        ActiveMQServerLogger.LOGGER.journalMessageAckMissingQueueInPreparedTX(ref.queueID());
                     } else {
                        try {
                           MessageReference removed = queue.removeReferenceWithID(d.messageID());
                           if (removed == null) {
                              ActiveMQServerLogger.LOGGER.journalErrorRemovingRef(d.messageID());
                           } else {
                              queue.reacknowledge(txImpl, removed);
                           }
                        } catch (Exception e) {
                           logger.warn(e.getMessage(), e);
                        }
                     }
                  }
               }
            }

         }
      });

      return transactionMap.values();
   }

}
