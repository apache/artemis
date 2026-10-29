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

package org.apache.artemis.database.worker;

import java.lang.invoke.MethodHandles;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.activemq.artemis.core.journal.IOCompletion;
import org.apache.artemis.database.ActiveMQDatabaseLogger;
import org.apache.artemis.database.DatabaseProvider;
import org.apache.artemis.database.DatabaseTX;
import org.apache.artemis.database.JDBCUtils;
import org.apache.artemis.database.data.DBData;
import org.apache.artemis.database.queries.MessagesPendingDeliverQueryForUpdate;
import org.apache.artemis.database.statements.DeleteAddressStatement;
import org.apache.artemis.database.statements.DeletePrepTxStatement;
import org.apache.artemis.database.statements.DeleteGenericDataStatement;
import org.apache.artemis.database.statements.DeleteMessageStatement;
import org.apache.artemis.database.statements.DeleteQueueStatement;
import org.apache.artemis.database.statements.DeleteReferenceStatement;
import org.apache.artemis.database.statements.InsertAddressStatement;
import org.apache.artemis.database.statements.InsertGenericDataStatement;
import org.apache.artemis.database.statements.InsertMessageStatement;
import org.apache.artemis.database.statements.InsertPrepTxStatement;
import org.apache.artemis.database.statements.InsertQueueStatement;
import org.apache.artemis.database.statements.InsertReferencesStatement;
import org.apache.artemis.database.statements.UpdateGenericDataStatement;
import org.apache.artemis.database.statements.UpdateTXGenericDataStatement;
import org.apache.artemis.database.statements.UpdateQueueStatement;
import org.apache.artemis.database.statements.UpdateTXMessageStatement;
import org.apache.artemis.database.statements.UpdateAckTXReferenceStatement;
import org.apache.artemis.database.statements.UpdateTXReferenceStatement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.artemis.database.worker.DataManagerUtil.ioCompletions;

public class DataWorker implements Runnable {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   protected final DatabaseProvider databaseProvider;
   private final String name;

   private final DataManager dataManager;
   public InsertMessageStatement insertMessageStatement;
   public InsertReferencesStatement insertReferencesStatement;
   public DeleteReferenceStatement deleteReferenceStatement;
   public DeleteMessageStatement deleteMessageStatement;
   public DeleteAddressStatement deleteAddressStatement;
   public InsertAddressStatement insertAddressStatement;
   public InsertQueueStatement insertQueueStatement;
   public UpdateQueueStatement updateQueueStatement;
   public DeleteQueueStatement deleteQueueStatement;
   public InsertGenericDataStatement insertGenericDataStatement;
   public UpdateGenericDataStatement updateGenericDataStatement;
   public UpdateTXGenericDataStatement updateTXGenericDataStatement;
   public DeleteGenericDataStatement deleteGenericDataStatement;
   public InsertGenericDataStatement insertBindingsGenericDataStatement;
   public UpdateGenericDataStatement updateBindingsGenericDataStatement;
   public DeleteGenericDataStatement deleteBindingsGenericDataStatement;
   public UpdateTXMessageStatement updateTXMessageStatement;
   public UpdateTXReferenceStatement updateTXReferenceStatement;
   public UpdateAckTXReferenceStatement updateAckTXReferenceStatement;
   public InsertPrepTxStatement insertPrepTxStatement;
   public DeletePrepTxStatement deletePrepTxStatement;
   public MessagesPendingDeliverQueryForUpdate pendingDeliveryQueryForUpdate;
   public ArrayList<DatabaseTX> pendingTX;
   protected Connection connection;
   int batchSize;
   List<DBData> dataList;

   public DataWorker(DataManager dataManager,
                     DatabaseProvider databaseProvider,
                     int batchSize,
                     String name) throws SQLException {
      this.databaseProvider = databaseProvider;
      this.dataManager = dataManager;
      this.name = name;
      this.batchSize = batchSize;
      connect();
   }

   public Connection getConnection() {
      return connection;
   }

   protected void connect() throws SQLException {
      connection = databaseProvider.getConnection();
      connection.setAutoCommit(false);
      insertMessageStatement = new InsertMessageStatement(databaseProvider, connection, batchSize, this::removeDuplicate);
      insertReferencesStatement = new InsertReferencesStatement(databaseProvider, connection, batchSize, this::removeDuplicate);
      deleteReferenceStatement = new DeleteReferenceStatement(databaseProvider, connection, batchSize);
      deleteMessageStatement = new DeleteMessageStatement(databaseProvider, connection, batchSize);
      deleteAddressStatement = new DeleteAddressStatement(databaseProvider, connection, batchSize);
      insertAddressStatement = new InsertAddressStatement(databaseProvider, connection, batchSize, this::removeDuplicate);
      insertQueueStatement = new InsertQueueStatement(databaseProvider, connection, batchSize, this::removeDuplicate);
      updateQueueStatement = new UpdateQueueStatement(databaseProvider, connection, batchSize);
      deleteQueueStatement = new DeleteQueueStatement(databaseProvider, connection, batchSize);
      insertGenericDataStatement = new InsertGenericDataStatement(databaseProvider, connection, batchSize, this::removeDuplicate);
      updateGenericDataStatement = new UpdateGenericDataStatement(databaseProvider, connection, batchSize);
      updateTXGenericDataStatement = new UpdateTXGenericDataStatement(databaseProvider, connection, batchSize);
      deleteGenericDataStatement = new DeleteGenericDataStatement(databaseProvider, connection, batchSize);
      updateTXMessageStatement = new UpdateTXMessageStatement(databaseProvider, connection, batchSize);
      updateTXReferenceStatement = new UpdateTXReferenceStatement(databaseProvider, connection, batchSize);
      updateAckTXReferenceStatement = new UpdateAckTXReferenceStatement(databaseProvider, connection, batchSize);
      insertPrepTxStatement = new InsertPrepTxStatement(databaseProvider, connection, batchSize, this::removeDuplicate);
      deletePrepTxStatement = new DeletePrepTxStatement(databaseProvider, connection, batchSize);
      String bindingsTable = databaseProvider.getSqlProvider().getConfigData();
      insertBindingsGenericDataStatement = new InsertGenericDataStatement(databaseProvider, connection, batchSize, bindingsTable, this::removeDuplicate);
      updateBindingsGenericDataStatement = new UpdateGenericDataStatement(databaseProvider, connection, batchSize, bindingsTable);
      deleteBindingsGenericDataStatement = new DeleteGenericDataStatement(databaseProvider, connection, batchSize, bindingsTable);
      pendingDeliveryQueryForUpdate = new MessagesPendingDeliverQueryForUpdate(databaseProvider, connection, dataManager.isPageJoinFetch());
      pendingDeliveryQueryForUpdate.prepare();
      pendingTX = new ArrayList<>();
   }

   private void removeDuplicate(DBData element) {
      dataList.remove(element);
      element.getContext().done();
   }

   public void setTaskList(List<DBData> dataList) {
      this.dataList = dataList;
   }

   SQLException reconnect(Throwable cause) {
      disconnect(cause);
      Throwable lastException = null;
      for (int retryI = 0; retryI < dataManager.getMaxRetries(); retryI++) {
         try {
            if (retryI > 0) {
               long interval = dataManager.getRetryIntervalMillis();
               ActiveMQDatabaseLogger.LOGGER.retrySQLAction(retryI + 1, dataManager.getMaxRetries(), interval, lastException.getMessage(), lastException);
               if (interval > 0) {
                  Thread.sleep(interval);
               }
            }
            connect();
            return null;
         } catch (Throwable e) {
            if (e instanceof InterruptedException) {
               return new SQLException(e.getMessage(), e);
            }
            logger.warn(e.getMessage(), e);
            lastException = e;
         }
      }
      return new SQLException("Failed to reconnect after " + dataManager.getMaxRetries() + " retries", lastException);
   }

   SQLException executeWithRetry(SQLConsumer<DataWorker> action) {
      Throwable lastException = null;
      for (int retryI = 0; retryI < dataManager.getMaxRetries(); retryI++) {
         try {
            if (retryI > 0) {
               long interval = dataManager.getRetryIntervalMillis();
               ActiveMQDatabaseLogger.LOGGER.retrySQLAction(retryI + 1, dataManager.getMaxRetries(), interval, lastException.getMessage(), lastException);
               if (interval > 0) {
                  if (!JDBCUtils.isConstraintViolation(lastException)) {
                     // no need to wait if the retry is because of a primary key
                     Thread.sleep(interval);
                  }
               }
               connect();
            }
            action.accept(this);
            return null;
         } catch (Throwable e) { // Some JDBC drivers throw non-SQLException (e.g. NPE) when the connection drops mid-operation
            if (e instanceof InterruptedException) {
               return new SQLException(e.getMessage(), e);
            }
            logger.warn(e.getMessage(), e);
            lastException = e;
            disconnect(e);
         }
      }
      return new SQLException("Failed after " + dataManager.getMaxRetries() + " retries", lastException);
   }

   @Override
   public void run() {
      try {
         SQLException retryException = executeWithRetry(w -> doBeforeCommit());
         if (retryException == null) {
            // the commit could fail on the way back from the database after the data was already written,
            // so we cannot safely retry here. A failure at this point triggers a critical error.
            connection.commit();
            doAfterCommit();
         } else {
            doError(retryException);
            dataManager.criticalError(retryException);
         }
      } catch (Exception e) {
         logger.warn(e.getMessage(), e);
         dataManager.criticalError(e);
      } finally {
         doCleanup();
      }
   }

   protected void disconnect(Throwable e) {
      logger.warn("Retrying Connection:: {}", e.getMessage(), e);
      try {
         connection.rollback();
      } catch (Throwable ignored) {
      }

      try {
         connection.close();
      } catch (Throwable ignored) {
      }
   }

   private void doBeforeCommit() throws SQLException {
      logger.info("Worker {} running with {} tasks", name, dataList.size());
      dataList.forEach(this::doStore);
      insertMessageStatement.flushPending(false);
      updateTXMessageStatement.flushPending(false);
      insertReferencesStatement.flushPending(false);
      updateTXReferenceStatement.flushPending(false);
      updateAckTXReferenceStatement.flushPending(false);
      deleteReferenceStatement.flushPending(false);
      deleteMessageStatement.flushPending(false);
      deleteAddressStatement.flushPending(false);
      insertAddressStatement.flushPending(false);
      insertQueueStatement.flushPending(false);
      updateQueueStatement.flushPending(false);
      deleteQueueStatement.flushPending(false);
      insertGenericDataStatement.flushPending(false);
      updateGenericDataStatement.flushPending(false);
      updateTXGenericDataStatement.flushPending(false);
      deleteGenericDataStatement.flushPending(false);
      insertBindingsGenericDataStatement.flushPending(false);
      updateBindingsGenericDataStatement.flushPending(false);
      deleteBindingsGenericDataStatement.flushPending(false);
      insertPrepTxStatement.flushPending(false);
      deletePrepTxStatement.flushPending(false);
   }

   private void doAfterCommit() {
      insertMessageStatement.confirmData();
      updateTXMessageStatement.confirmData();
      insertReferencesStatement.confirmData();
      updateTXReferenceStatement.confirmData();
      updateAckTXReferenceStatement.confirmData();
      deleteReferenceStatement.confirmData();
      deleteMessageStatement.confirmData();
      insertReferencesStatement.confirmData();
      deleteAddressStatement.confirmData();
      insertAddressStatement.confirmData();
      insertQueueStatement.confirmData();
      updateQueueStatement.confirmData();
      deleteQueueStatement.confirmData();
      insertGenericDataStatement.confirmData();
      updateGenericDataStatement.confirmData();
      updateTXGenericDataStatement.confirmData();
      deleteGenericDataStatement.confirmData();
      insertBindingsGenericDataStatement.confirmData();
      updateBindingsGenericDataStatement.confirmData();
      deleteBindingsGenericDataStatement.confirmData();
      insertPrepTxStatement.confirmData();
      deletePrepTxStatement.confirmData();
      pendingTX.forEach(DatabaseTX::afterIO);
   }

   private void doError(Exception exception) {
      insertMessageStatement.onError(exception);
      updateTXMessageStatement.onError(exception);
      insertReferencesStatement.onError(exception);
      updateTXReferenceStatement.onError(exception);
      updateAckTXReferenceStatement.onError(exception);
      deleteReferenceStatement.onError(exception);
      deleteMessageStatement.onError(exception);
      insertReferencesStatement.onError(exception);
      deleteAddressStatement.onError(exception);
      insertAddressStatement.onError(exception);
      insertQueueStatement.onError(exception);
      updateQueueStatement.onError(exception);
      deleteQueueStatement.onError(exception);
      insertGenericDataStatement.onError(exception);
      updateGenericDataStatement.onError(exception);
      updateTXGenericDataStatement.onError(exception);
      deleteGenericDataStatement.onError(exception);
      insertBindingsGenericDataStatement.onError(exception);
      updateBindingsGenericDataStatement.onError(exception);
      deleteBindingsGenericDataStatement.onError(exception);
      insertPrepTxStatement.onError(exception);
      deletePrepTxStatement.onError(exception);
   }

   private void doCleanup() {
      insertMessageStatement.clear();
      updateTXMessageStatement.clear();
      insertReferencesStatement.clear();
      updateTXReferenceStatement.clear();
      updateAckTXReferenceStatement.clear();
      deleteReferenceStatement.clear();
      deleteMessageStatement.clear();
      deleteAddressStatement.clear();
      insertAddressStatement.clear();
      insertQueueStatement.clear();
      updateQueueStatement.clear();
      deleteQueueStatement.clear();
      insertGenericDataStatement.clear();
      updateGenericDataStatement.clear();
      updateTXGenericDataStatement.clear();
      deleteGenericDataStatement.clear();
      insertBindingsGenericDataStatement.clear();
      updateBindingsGenericDataStatement.clear();
      deleteBindingsGenericDataStatement.clear();
      insertPrepTxStatement.clear();
      deletePrepTxStatement.clear();
      pendingTX.clear();
      boolean locked = dataManager.acquireLock();
      try {
         // Signal IOCompletions even if the lock wasn't acquired (e.g. interrupted during shutdown),
         // to prevent data starvation — callers waiting on these completions must not block forever.
         ioCompletions(dataList).forEach(IOCompletion::workDone);
      } finally {
         if (locked) {
            dataManager.releaseLock();
         }
      }
      this.dataList = null;
      dataManager.workerDone(this);
   }

   public void doStore(DBData data) {
      data.perform(this);
   }

   public void commit() throws SQLException {
      connection.commit();
   }

   public void close() {
      try {
         connection.close();
      } catch (Exception e) {
         logger.warn(e.getMessage(), e);
      }
   }

}
