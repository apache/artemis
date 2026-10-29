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

package org.apache.artemis.database.statements;

import java.lang.invoke.MethodHandles;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.persistence.Persister;
import org.apache.activemq.artemis.utils.ActiveMQBufferInputStream;
import org.apache.artemis.database.ActiveMQDatabaseLogger;
import org.apache.artemis.database.DatabaseProvider;
import org.apache.artemis.database.JDBCUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BatchableStatement<E> {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   final DatabaseProvider connectionProvider;
   final Connection connection;
   final String statement;
   protected PreparedStatement preparedStatement;
   protected Consumer<E> duplicateReaper;

   List<E> pendingList;
   List<IOCallback> callbacks;

   public BatchableStatement(DatabaseProvider connectionProvider,
                             Connection connection,
                             String statement,
                             int expectedSize,
                             Consumer<E> duplicateReaper) throws SQLException {
      this.connectionProvider = connectionProvider;
      this.connection = connection;
      this.pendingList = new ArrayList<>(expectedSize);
      this.callbacks = new ArrayList<>(expectedSize);
      this.statement = statement;
      this.duplicateReaper = duplicateReaper;
      init();
   }

   protected static ActiveMQBufferInputStream blobInputStream(ActiveMQBuffer buffer) {
      return new ActiveMQBufferInputStream(buffer);
   }

   protected void init() throws SQLException {
      this.preparedStatement = connection.prepareStatement(statement);
   }

   public void addElement(E element, IOCallback callback) {
      pendingList.add(element);
      callbacks.add(callback);
   }

   public void flushPendingNoCommit() {
      try {
         flushPending(false);
      } catch (Throwable e) {
         logger.warn(e.getMessage(), e);
      }
   }

   public void flushPending(boolean commit) throws SQLException {
      if (pendingList.isEmpty()) {
         return;
      }

      pendingList.forEach(this::flushOne);
      try {
         preparedStatement.executeBatch();
      } catch (SQLException e) {
         logger.warn(e.getMessage(), e);
         if (commit) {
            try {
               connection.rollback();
            } catch (Throwable ignored) {
            }
         }
         if (JDBCUtils.isConstraintViolation(e)) {
            connection.rollback();
            cleanupDuplicates();
         }
         throw e;
      }
      if (commit) {
         connection.commit();
         confirmData();
      }
   }

   // It will try element by element, however always committing
   // any duplicates will be removed from the list
   private void cleanupDuplicates() throws SQLException {
      preparedStatement.clearParameters();
      preparedStatement.clearBatch();
      ArrayList<E> duplicates = new ArrayList<>();
      for (E element : pendingList) {
         try {
            doOne(element);
            preparedStatement.executeUpdate();
         } catch (Exception inner) {
            if (JDBCUtils.isConstraintViolation(inner)) {
               logger.debug("Removing {} from the list", element);
               ActiveMQDatabaseLogger.LOGGER.constraintViolation(element, inner);
               duplicates.add(element);
            }
            connection.rollback();
         }
      }

      connection.rollback();

      for (E duplicate : duplicates) {
         if (duplicateReaper != null) {
            duplicateReaper.accept(duplicate);
         }
         pendingList.remove(duplicate);
      }
   }

   public void onError(Exception e) {
      callbacks.forEach(c -> {
         if (c != null) {
            c.onError(-1, e.getMessage());
         }
      });
   }

   public void confirmData() {
      callbacks.forEach(this::okCallback);
      clear();
   }

   public void clear() {
      pendingList.clear();
      callbacks.clear();
   }

   private void okCallback(IOCallback callback) {
      if (callback != null) {
         callback.done();
      }
   }

   protected void flushOne(E element) {
      try {
         doOne(element);
         preparedStatement.addBatch();
      } catch (Exception e) {
         throw new RuntimeException(e.getMessage(), e);
      }
   }

   public ActiveMQBuffer getPersistedBuffer(Persister persister, Object record) {
      int size = persister.getEncodeSize(record);
      ActiveMQBuffer encodedBuffer = ActiveMQBuffers.fixedBuffer(size);
      persister.encode(encodedBuffer, record);
      return encodedBuffer;
   }

   protected abstract void doOne(E element) throws Exception;

}
