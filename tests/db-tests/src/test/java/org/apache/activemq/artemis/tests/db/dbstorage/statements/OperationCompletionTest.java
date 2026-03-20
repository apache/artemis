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
package org.apache.activemq.artemis.tests.db.dbstorage.statements;

import java.lang.invoke.MethodHandles;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.persistence.OperationContext;
import org.apache.activemq.artemis.core.persistence.impl.database.DatabaseStorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.OperationContextImpl;
import org.apache.activemq.artemis.core.transaction.TransactionOperationAbstract;
import org.apache.activemq.artemis.core.transaction.impl.TransactionImpl;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.artemis.database.DatabaseProvider;
import org.apache.artemis.database.data.DBData;
import org.apache.artemis.database.data.DeleteMessageData;
import org.apache.artemis.database.worker.DataManager;
import org.apache.artemis.database.worker.DataManagerAccessor;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledIf("isNoDatabaseSelected")
@ExtendWith(ParameterizedTestExtension.class)
public class OperationCompletionTest extends AbstractStatementTest {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @TestTemplate
   public void testCommitFlowRefDeleteBeforeMessageDelete() throws Exception {
      DatabaseStorageManager databaseStorageManager = new DatabaseStorageManager(configuration, criticalAnalyzer, executorFactory, executorFactory, scheduledExecutorService, executorService, null);
      databaseStorageManager.start();
      try {
         DatabaseProvider databaseProvider = storageConfiguration.getDatabaseProvider();

         Connection connection = databaseProvider.getConnection();
         runAfter(connection::close);

         OperationContext context = databaseStorageManager.getContext();
         runAfter(OperationContextImpl::clearContext);

         long messageID = 1;
         long queueID = 1;

         // Step 1: Store a message + reference transactionally
         CoreMessage message = new CoreMessage().initBuffer(1024).setDurable(true);
         message.setMessageID(messageID);
         message.getBodyBuffer().writeByte((byte) 'Z');

         TransactionImpl storeTx = new TransactionImpl(databaseStorageManager);
         databaseStorageManager.storeMessageTransactional(storeTx, message);
         databaseStorageManager.storeReferenceTransactional(storeTx, queueID, messageID, false);
         databaseStorageManager.commit(storeTx);

         assertTrue(context.waitCompletion(5000));

         assertEquals(1, selectCount(connection, databaseProvider.getSqlProvider().getMessages()));
         assertEquals(1, selectCount(connection, databaseProvider.getSqlProvider().getRefs()));

         // Step 2: Pause auto-flush so we can prove the callback is deferred until JDBC commit
         DataManager dataManager = databaseStorageManager.getDataManager();
         dataManager.setPeriod(1, TimeUnit.HOURS);

         TransactionImpl ackTx = new TransactionImpl(databaseStorageManager);
         ackTx.setContainsPersistent();
         databaseStorageManager.storeAcknowledgeTransactional(ackTx, queueID, messageID);

         CountDownLatch afterCommitLatch = new CountDownLatch(1);
         AtomicBoolean refsDeletedBeforeMessageDelete = new AtomicBoolean(false);

         ackTx.addOperation(new TransactionOperationAbstract() {
            @Override
            public void afterCommit(org.apache.activemq.artemis.core.transaction.Transaction tx) {
               try {
                  int refCount = selectCount(connection, databaseProvider.getSqlProvider().getRefs());
                  refsDeletedBeforeMessageDelete.set(refCount == 0);
                  databaseStorageManager.deleteMessage(messageID);
               } catch (Exception e) {
                  logger.warn(e.getMessage(), e);
               } finally {
                  afterCommitLatch.countDown();
               }
            }
         });

         // ackTx.commit() queues the ref DELETE into DataManager but does NOT flush yet
         ackTx.commit();

         // Prove the callback has NOT fired — the data is queued but no JDBC commit happened
         assertFalse(afterCommitLatch.await(500, TimeUnit.MILLISECONDS), "afterCommit should NOT fire before the DataManager flushes");
         assertEquals(1, selectCount(connection, databaseProvider.getSqlProvider().getRefs()), "Refs should still be in DB before flush");

         // Now trigger the flush manually — this runs the DataWorker: execute statements → JDBC commit → completeIO → afterCommit fires
         dataManager.setPeriod(10, TimeUnit.MILLISECONDS);
         dataManager.delay();

         assertTrue(afterCommitLatch.await(10, TimeUnit.SECONDS), "afterCommit should fire after DataManager flush");
         assertTrue(refsDeletedBeforeMessageDelete.get(), "References should be deleted from DB before afterCommit fires");

         // Wait for deleteMessage to complete in the next flush cycle
         Wait.assertEquals(0, () -> selectCount(connection, databaseProvider.getSqlProvider().getMessages()), 5000, 100);
         assertEquals(0, selectCount(connection, databaseProvider.getSqlProvider().getRefs()));
      } finally {
         databaseStorageManager.stop();
      }
   }

   @TestTemplate
   public void testExtractTaskListSkipsWorkingContext() throws Exception {
      DatabaseStorageManager databaseStorageManager = new DatabaseStorageManager(configuration, criticalAnalyzer, executorFactory, executorFactory, scheduledExecutorService, executorService, null);
      databaseStorageManager.start();
      try {
         DataManager dataManager = databaseStorageManager.getDataManager();
         dataManager.setPeriod(1, TimeUnit.HOURS);

         OperationContextImpl workingCtx = new OperationContextImpl(Runnable::run);
         OperationContextImpl freeCtx = new OperationContextImpl(Runnable::run);

         DBData workingData1 = new DeleteMessageData(1, workingCtx);
         DBData workingData2 = new DeleteMessageData(2, workingCtx);
         DBData freeData = new DeleteMessageData(3, freeCtx);
         DBData nullCtxData = new DeleteMessageData(4, null);

         ArrayList<DBData> pendingData = DataManagerAccessor.getPendingData(dataManager);

         pendingData.add(workingData1);
         pendingData.add(freeData);
         pendingData.add(workingData2);
         pendingData.add(nullCtxData);

         workingCtx.workUp();
         assertTrue(workingCtx.isWorking());
         assertFalse(freeCtx.isWorking());

         List<DBData> extracted = DataManagerAccessor.extractTaskList(dataManager);

         assertEquals(2, extracted.size());
         assertTrue(extracted.contains(freeData));
         assertTrue(extracted.contains(nullCtxData));
         assertFalse(extracted.contains(workingData1));
         assertFalse(extracted.contains(workingData2));

         assertEquals(2, pendingData.size());
         assertTrue(pendingData.contains(workingData1));
         assertTrue(pendingData.contains(workingData2));

         workingCtx.workDone();
         assertFalse(workingCtx.isWorking());

         List<DBData> extracted2 = DataManagerAccessor.extractTaskList(dataManager);
         assertEquals(2, extracted2.size());
         assertTrue(extracted2.contains(workingData1));
         assertTrue(extracted2.contains(workingData2));
         assertEquals(0, pendingData.size());
      } finally {
         databaseStorageManager.stop();
      }
   }
}
