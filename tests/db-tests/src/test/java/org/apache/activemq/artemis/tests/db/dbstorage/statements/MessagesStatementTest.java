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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.persistence.OperationContext;
import org.apache.activemq.artemis.core.persistence.impl.database.DatabaseStorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.OperationContextImpl;
import org.apache.activemq.artemis.core.transaction.impl.TransactionImpl;
import org.apache.activemq.artemis.tests.db.dbstorage.CountDownCompletion;
import org.apache.activemq.artemis.tests.db.dbstorage.VariableCountCompletion;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.artemis.database.DatabaseProvider;
import org.apache.artemis.database.data.MessageData;
import org.apache.artemis.database.data.MessageReferenceData;
import org.apache.artemis.database.queries.MessageDeliveryUpdater;
import org.apache.artemis.database.queries.MessagesJDBCQuery;
import org.apache.artemis.database.queries.MessagesPendingDeliverQueryForUpdate;
import org.apache.artemis.database.queries.QueryUtil;
import org.apache.artemis.database.statements.InsertMessageStatement;
import org.apache.artemis.database.statements.InsertReferencesStatement;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.activemq.artemis.core.persistence.impl.database.DatabaseStorageManager.encodeMessage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledIf("isNoDatabaseSelected")
@ExtendWith(ParameterizedTestExtension.class)
public class MessagesStatementTest extends AbstractStatementTest {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @TestTemplate
   public void testReferencesDirectly() throws Exception {
      DatabaseStorageManager databaseStorageManager = new DatabaseStorageManager(configuration, criticalAnalyzer, executorFactory, executorFactory, scheduledExecutorService, executorService, null);
      databaseStorageManager.start();
      try {
         DatabaseProvider databaseProvider = storageConfiguration.getDatabaseProvider();

         int nrecords = 100;

         CountDownCompletion latch = new CountDownCompletion(nrecords);

         try (Connection connection = databaseProvider.getConnection()) {
            connection.setAutoCommit(false);
            InsertReferencesStatement insertReferencesStatement = new InsertReferencesStatement(databaseProvider, connection, 100);
            for (int i = 1; i <= nrecords; i++) {
               MessageReferenceData task = databaseStorageManager.getDataManager().newReferenceTask(i, 1, false, i % 2 == 0 ? (long) i : null, latch);
               insertReferencesStatement.addElement(task, latch);
            }
            insertReferencesStatement.flushPending(true);

            assertEquals(nrecords, selectCount(connection, "DB_REFERENCES"));
         }

         assertTrue(latch.await(10, TimeUnit.SECONDS));
      } finally {
         databaseStorageManager.stop();
      }
   }

   @TestTemplate
   public void testMessagesDirectly() throws Exception {
      DatabaseStorageManager databaseStorageManager = new DatabaseStorageManager(configuration, criticalAnalyzer, executorFactory, executorFactory, scheduledExecutorService, executorService, null);
      databaseStorageManager.start();
      try {
         DatabaseProvider databaseProvider = storageConfiguration.getDatabaseProvider();

         int nrecords = 100;

         CountDownCompletion latch = new CountDownCompletion(nrecords);

         try (Connection connection = databaseProvider.getConnection()) {
            connection.setAutoCommit(false);
            InsertMessageStatement insertMessageStatement = new InsertMessageStatement(databaseProvider, connection, 100);
            for (int i = 1; i <= nrecords; i++) {
               CoreMessage message = new CoreMessage().initBuffer(1024).setDurable(true);
               message.setMessageID(i);
               message.getBodyBuffer().writeByte((byte) 'Z');
               MessageData task = databaseStorageManager.getDataManager().newMessageTask(message.getMessageID(), () -> encodeMessage(message), message.getMemoryEstimate(), false, false, null, latch);
               insertMessageStatement.addElement(task, latch);
            }
            insertMessageStatement.flushPending(true);

            assertEquals(nrecords, selectCount(connection, "DB_MESSAGES"));
         }

         assertTrue(latch.await(10, TimeUnit.SECONDS));
      } finally {
         databaseStorageManager.stop();
      }
   }

   @TestTemplate
   public void testMessagesStorageManager() throws Exception {
      DatabaseStorageManager databaseStorageManager = new DatabaseStorageManager(configuration, criticalAnalyzer, executorFactory, executorFactory, scheduledExecutorService, executorService, null);
      databaseStorageManager.start();
      try {
         DatabaseProvider databaseProvider = storageConfiguration.getDatabaseProvider();

         Connection connection = databaseProvider.getConnection();
         runAfter(connection::close);

         int nrecords = 100;

         OperationContext context = databaseStorageManager.getContext();
         runAfter(OperationContextImpl::clearContext);

         for (int i = 1; i <= nrecords; i++) {
            CoreMessage message = new CoreMessage().initBuffer(1024).setDurable(true);
            message.setMessageID(i);
            message.getBodyBuffer().writeByte((byte) 'Z');

            if (i % 2 == 0) {
               databaseStorageManager.storeMessage(message);
            } else {
               TransactionImpl tx = new TransactionImpl(databaseStorageManager);
               databaseStorageManager.storeMessageTransactional(tx, message);
               databaseStorageManager.commit(tx);
            }
         }

         assertTrue(context.waitCompletion(5000));

         assertEquals(nrecords, selectCount(connection, databaseProvider.getSqlProvider().getMessages()));

         int recordsToDelete = 20;

         for (int i = 1; i <= recordsToDelete; i++) {
            databaseStorageManager.deleteMessage(i);
         }

         assertTrue(context.waitCompletion(5000));
         assertEquals(nrecords - recordsToDelete, selectCount(connection, databaseProvider.getSqlProvider().getMessages()));
      } finally {
         databaseStorageManager.stop();
      }
   }

   @TestTemplate
   public void testMessagesJDBCQueryRoundTrip() throws Exception {

      DatabaseStorageManager databaseStorageManager = new DatabaseStorageManager(configuration, criticalAnalyzer, executorFactory, executorFactory, scheduledExecutorService, executorService, null);
      databaseStorageManager.start();
      try {
         DatabaseProvider databaseProvider = storageConfiguration.getDatabaseProvider();

         Connection connection = databaseProvider.getConnection();
         runAfter(connection::close);

         int nrecords = 100;

         OperationContext context = databaseStorageManager.getContext();
         runAfter(OperationContextImpl::clearContext);

         for (int i = 1; i <= nrecords * 2; i++) {
            CoreMessage message = new CoreMessage().initBuffer(1024).setDurable(true);
            message.setMessageID(i);
            message.putStringProperty("test", "t" + i);
            message.getBodyBuffer().writeByte((byte) 'Z');
            databaseStorageManager.storeMessage(message);

            if (i > nrecords) {
               // those shouldn't be counted as they are pending
               databaseStorageManager.storeReference(1, i, true, false);
               databaseStorageManager.storeReference(2, i, true, false);
               databaseStorageManager.storeReference(3, i, true, false);
            } else {
               databaseStorageManager.storeReference(1, i, false, false);
               databaseStorageManager.storeReference(2, i, false, false);
               databaseStorageManager.storeReference(3, i, true, false);
            }
         }

         assertTrue(context.waitCompletion(5000));

         MessagesJDBCQuery query = new MessagesJDBCQuery(databaseProvider, connection);
         java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger();
         query.query(m -> {
            count.incrementAndGet();
            logger.debug("queried message {}", m.messageID);
            assertTrue(m.messageID >= 1 && m.messageID <= nrecords, "messageID out of range: " + m.messageID);
         });
         assertEquals(nrecords, count.get());
      } finally {
         databaseStorageManager.stop();
      }
   }

   @TestTemplate
   public void testLoadPendingDeliveries() throws Exception {

      DatabaseStorageManager databaseStorageManager = new DatabaseStorageManager(configuration, criticalAnalyzer, executorFactory, executorFactory, scheduledExecutorService, executorService, null);
      databaseStorageManager.start();
      try {
         DatabaseProvider databaseProvider = storageConfiguration.getDatabaseProvider();

         Connection connection = databaseProvider.getConnection();
         connection.setAutoCommit(false);
         runAfter(connection::close);

         long queueID = RandomUtil.randomInterval(100, 1000);
         int nrecords = 100;

         OperationContext context = databaseStorageManager.getContext();
         runAfter(OperationContextImpl::clearContext);

         for (int i = 1; i <= nrecords; i++) {
            CoreMessage message = new CoreMessage().initBuffer(1024).setDurable(true);
            message.setMessageID(i);
            message.putStringProperty("test", "t" + i);
            message.getBodyBuffer().writeByte((byte) 'Z');
            databaseStorageManager.storeMessage(message);

            databaseStorageManager.storeReference(queueID, i, true, false);
         }

         assertTrue(context.waitCompletion(5000));

         validateNewDBTotalMessages(databaseProvider, nrecords, nrecords);

         try (Connection secondaryConnection = databaseProvider.getConnection()) {
            secondaryConnection.setAutoCommit(false);
            MessageDeliveryUpdater messageDeliveryUpdater = new MessageDeliveryUpdater(databaseProvider, secondaryConnection);
            messageDeliveryUpdater.init();
            MessagesPendingDeliverQueryForUpdate pendingDeliveryLoad = new MessagesPendingDeliverQueryForUpdate(databaseProvider, connection, false);
            pendingDeliveryLoad.prepare();

            java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger();
            ResultSet resultSet = pendingDeliveryLoad.execute(queueID);
            ArrayList<String> ids = new ArrayList<>();
            while (resultSet.next()) {
               String id = resultSet.getString(1);
               ids.add(id);
            }
            resultSet.close();

            ResultSet blobsResultSet = connection.createStatement().executeQuery(databaseProvider.getSqlProvider().selectMessagesBlob("DB_MESSAGES", ids));
            while (blobsResultSet.next()) {
               MessageData messageData = QueryUtil.readMessageData(blobsResultSet, 1, 2, -1);
               messageDeliveryUpdater.updateDelivery(queueID, messageData.messageID);
               count.incrementAndGet();
            }

            messageDeliveryUpdater.flush();
            secondaryConnection.commit();
            assertEquals(nrecords, count.get());
            connection.commit();

            count.set(0);
            MessagesJDBCQuery query = new MessagesJDBCQuery(databaseProvider, connection);
            query.query(m -> {
               count.incrementAndGet();
               logger.debug("queried message {}", m.messageID);
               assertTrue(m.messageID >= 1 && m.messageID <= nrecords, "messageID out of range: " + m.messageID);
            });
            assertEquals(nrecords, count.get());
         }
      } finally {
         databaseStorageManager.stop();
      }
   }

   @TestTemplate
   public void testMessagesAckTX() throws Exception {
      DatabaseStorageManager databaseStorageManager = new DatabaseStorageManager(configuration, criticalAnalyzer, executorFactory, executorFactory, scheduledExecutorService, executorService, null);
      databaseStorageManager.start();
      try {
         DatabaseProvider databaseProvider = storageConfiguration.getDatabaseProvider();

         Connection connection = databaseProvider.getConnection();
         runAfter(connection::close);

         int nrecords = 1000;

         OperationContext context = databaseStorageManager.getContext();
         runAfter(OperationContextImpl::clearContext);

         TransactionImpl tx = new TransactionImpl(databaseStorageManager);
         for (int i = 1; i <= nrecords; i++) {
            databaseStorageManager.storeReferenceTransactional(tx, 1, i, false);
         }
         databaseStorageManager.commit(tx);

         assertTrue(context.waitCompletion(5000));

         assertEquals(nrecords, selectCount(connection, databaseProvider.getSqlProvider().getRefs()));

         tx = new TransactionImpl(databaseStorageManager);
         for (int i = 1; i <= nrecords; i++) {
            databaseStorageManager.storeAcknowledgeTransactional(tx, 1, i);
         }
         databaseStorageManager.commit(tx);
         assertTrue(context.waitCompletion(5000));

         assertEquals(0, selectCount(connection, databaseProvider.getSqlProvider().getMessages()));
      } finally {
         databaseStorageManager.stop();
      }
   }

   @TestTemplate
   public void testMessagesReferencesStorageManager() throws Exception {
      DatabaseStorageManager databaseStorageManager = new DatabaseStorageManager(configuration, criticalAnalyzer, executorFactory, executorFactory, scheduledExecutorService, executorService, null);
      databaseStorageManager.start();
      try {
         DatabaseProvider databaseProvider = storageConfiguration.getDatabaseProvider();

         Connection connection = databaseProvider.getConnection();
         runAfter(connection::close);

         int nrecords = 100;

         OperationContext context = databaseStorageManager.getContext();
         runAfter(OperationContextImpl::clearContext);

         for (int i = 1; i <= nrecords; i++) {
            if (i % 2 == 0) {
               databaseStorageManager.storeReference(1, i, false, true);
            } else {
               TransactionImpl txID = new TransactionImpl(databaseStorageManager);
               databaseStorageManager.storeReferenceTransactional(txID, 1, i, false);
               databaseStorageManager.commit(txID);
            }
         }

         assertTrue(context.waitCompletion(5000));

         assertEquals(nrecords, selectCount(connection, databaseProvider.getSqlProvider().getRefs()));

         int recordsToDelete = 20;

         for (int i = 1; i <= recordsToDelete; i++) {
            if (i % 2 == 1) {
               databaseStorageManager.storeAcknowledge(1, i);
            } else {
               TransactionImpl txID = new TransactionImpl(databaseStorageManager);
               databaseStorageManager.storeAcknowledgeTransactional(txID, 1, i);
               databaseStorageManager.commit(txID);
            }
         }

         assertTrue(context.waitCompletion(5000));

         assertEquals(nrecords - recordsToDelete, selectCount(connection, databaseProvider.getSqlProvider().getRefs()));
      } finally {
         databaseStorageManager.stop();
      }
   }

   @TestTemplate
   public void testTreatExceptionOnError() throws Exception {
      DatabaseStorageManager databaseStorageManager = new DatabaseStorageManager(configuration, criticalAnalyzer, executorFactory, executorFactory, scheduledExecutorService, executorService, null);
      databaseStorageManager.start();
      try {
         DatabaseProvider databaseProvider = storageConfiguration.getDatabaseProvider();

         int nrecords = 100;

         VariableCountCompletion ioCallback = new VariableCountCompletion();

         try (Connection connection = databaseProvider.getConnection()) {
            connection.setAutoCommit(false);
            InsertMessageStatement insertMessageStatement = new InsertMessageStatement(databaseProvider, connection, 100);
            for (int i = 1; i <= nrecords; i++) {
               CoreMessage message = new CoreMessage().initBuffer(1024).setDurable(true);
               message.setMessageID(1); // everything should fail with a DuplicateException
               message.getBodyBuffer().writeByte((byte) 'Z');

               insertMessageStatement.addElement(databaseStorageManager.getDataManager().newMessageTask(message.getMessageID(), () -> encodeMessage(message), message.getMemoryEstimate(), false, false, null, ioCallback), ioCallback);
            }
            assertThrows(SQLException.class, () -> insertMessageStatement.flushPending(true));

            // forcing a commit, even though it failed... it should not commit any success
            connection.commit();

            assertEquals(0, selectCount(connection, "DB_MESSAGES"));
            assertEquals(0, ioCallback.errors.get());
         }
      } finally {
         databaseStorageManager.stop();
      }
   }

}