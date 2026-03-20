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
package org.apache.activemq.artemis.tests.db.dbstorage.integration;

import java.sql.Connection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.persistence.impl.database.DatabaseStorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.OperationContextImpl;
import org.apache.activemq.artemis.core.transaction.impl.TransactionImpl;
import org.apache.activemq.artemis.tests.db.dbstorage.statements.AbstractStatementTest;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.artemis.database.DatabaseProvider;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledIf("isNoDatabaseSelected")
@ExtendWith(ParameterizedTestExtension.class)
public class DatabaseStorageManagerTest extends AbstractStatementTest {

   @TestTemplate
   public void testStoreMessage() throws Exception {
      DatabaseStorageManager databaseStorageManager = new DatabaseStorageManager(configuration, criticalAnalyzer, executorFactory, executorFactory, scheduledExecutorService, executorFactory.getExecutor(), null);
      databaseStorageManager.start();
      try {
         CoreMessage message = new CoreMessage().initBuffer(10 * 1024).setDurable(true);

         message.setMessageID(333);
         message.getBodyBuffer().writeByte((byte) 'Z');

         databaseStorageManager.storeMessage(message);
         databaseStorageManager.storeReference(1, 333, false, true);

         CountDownLatch done = new CountDownLatch(1);
         databaseStorageManager.getContext().executeOnCompletion(new IOCallback() {
            @Override
            public void done() {
               done.countDown();
            }

            @Override
            public void onError(int errorCode, String errorMessage) {

            }
         });

         assertTrue(done.await(10, TimeUnit.SECONDS));
      } finally {
         databaseStorageManager.stop();
      }
   }

   @TestTemplate
   public void testStoreMessageOnBatchableStatement() throws Exception {
      DatabaseStorageManager databaseStorageManager = new DatabaseStorageManager(configuration, criticalAnalyzer, executorFactory, executorFactory, scheduledExecutorService, executorFactory.getExecutor(), null);
      databaseStorageManager.start();
      try {
         DatabaseProvider databaseStorageProvider = storageConfiguration.getDatabaseProvider();

         int nrecords = 10;

         try (Connection connection = databaseStorageProvider.getConnection()) {
            connection.setAutoCommit(false);
            for (int i = 1; i <= nrecords; i++) {
               CoreMessage message = new CoreMessage().initBuffer(1024).setDurable(true);
               message.setMessageID(i);
               message.getBodyBuffer().writeByte((byte) 'Z');
               databaseStorageManager.storeMessage(message);
            }
            OperationContextImpl.getContext().waitCompletion();
            assertEquals(nrecords, selectCount(connection, databaseStorageProvider.getSqlProvider().getMessages()));
         }
      } finally {
         databaseStorageManager.stop();
      }
   }

   @TestTemplate
   public void testStoreMessageOnBatchableStatementTX() throws Exception {
      DatabaseStorageManager databaseStorageManager = new DatabaseStorageManager(configuration, criticalAnalyzer, executorFactory, executorFactory, scheduledExecutorService, executorFactory.getExecutor(), null);
      databaseStorageManager.start();
      try {
         DatabaseProvider databaseStorageProvider = storageConfiguration.getDatabaseProvider();

         int nrecords = 10;

         try (Connection connection = databaseStorageProvider.getConnection()) {
            connection.setAutoCommit(false);
            for (int i = 1; i <= 10; i++) {
               TransactionImpl tx = new TransactionImpl(databaseStorageManager);
               CoreMessage message = new CoreMessage().initBuffer(1024).setDurable(true);
               message.setMessageID(i);
               message.getBodyBuffer().writeByte((byte) 'Z');
               databaseStorageManager.storeMessageTransactional(tx, message);
               databaseStorageManager.storeReferenceTransactional(tx, 3, message.getMessageID(), false);
               databaseStorageManager.commit(tx);
               assertTrue(OperationContextImpl.getContext().waitCompletion(5000));
            }
            assertEquals(nrecords, selectCount(connection, databaseStorageProvider.getSqlProvider().getMessages()));
         }
      } finally {
         databaseStorageManager.stop();
      }
   }

}