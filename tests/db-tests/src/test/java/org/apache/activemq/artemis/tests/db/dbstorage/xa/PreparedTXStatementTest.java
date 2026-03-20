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
package org.apache.activemq.artemis.tests.db.dbstorage.xa;

import javax.transaction.xa.Xid;
import java.lang.invoke.MethodHandles;
import java.sql.Connection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.core.persistence.impl.database.DatabaseStorageManager;
import org.apache.activemq.artemis.core.transaction.impl.TransactionImpl;
import org.apache.activemq.artemis.tests.db.dbstorage.CountDownCompletion;
import org.apache.activemq.artemis.tests.db.dbstorage.statements.AbstractStatementTest;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.utils.XidCodecSupport;
import org.apache.artemis.database.DatabaseProvider;
import org.apache.artemis.database.queries.PreparedTXQuery;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledIf("isNoDatabaseSelected")
@ExtendWith(ParameterizedTestExtension.class)
public class PreparedTXStatementTest extends AbstractStatementTest {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @TestTemplate
   public void testInsertPreparedTX() throws Exception {
      DatabaseStorageManager databaseStorageManager = new DatabaseStorageManager(configuration, criticalAnalyzer, executorFactory, executorFactory, scheduledExecutorService, executorService, null);
      databaseStorageManager.start();
      databaseStorageManager.generateID();
      try {
         DatabaseProvider databaseProvider = storageConfiguration.getDatabaseProvider();

         int nrecords = 10;

         CountDownCompletion latch = new CountDownCompletion(nrecords);

         Set<Xid> xidSet = new HashSet<>();
         Set<Long> xidsToRemove = new HashSet<>();

         for (int i = 1; i <= nrecords; i++) {
            TransactionImpl transaction = new TransactionImpl(databaseStorageManager);
            Xid xid = newXID();
            xidSet.add(xid);
            xidsToRemove.add(transaction.getID());
            databaseStorageManager.getDataManager().storePrepareTx(transaction.getStorageTx(), xid, latch);
            transaction.setContainsPersistent();
            transaction.commit();
         }
         assertTrue(latch.await(10, TimeUnit.SECONDS));

         try (Connection connection = databaseProvider.getConnection()) {
            assertEquals(nrecords, selectCount(connection, "DB_PREP_TX"));
         }

         try (Connection connection = databaseProvider.getConnection()) {
            PreparedTXQuery preparedTXQuery = new PreparedTXQuery(databaseProvider, connection);
            preparedTXQuery.query(record -> {
               ActiveMQBuffer buffer = record.xid;
               Xid decodedXid = XidCodecSupport.decodeXid(buffer);
               assertTrue(xidSet.remove(decodedXid), "XID not found in the expected set: " + decodedXid);
            });
            assertTrue(xidSet.isEmpty(), "Not all XIDs were found in the database, remaining: " + xidSet);
         }

         CountDownCompletion deleteLatch = new CountDownCompletion(xidsToRemove.size());
         for (long txId : xidsToRemove) {
            databaseStorageManager.getDataManager().deletePrepareTX(txId, deleteLatch);
         }
         assertTrue(deleteLatch.await(10, TimeUnit.SECONDS));

         try (Connection connection = databaseProvider.getConnection()) {
            assertEquals(0, selectCount(connection, "DB_PREP_TX"));
         }

      } finally {
         databaseStorageManager.stop();
      }
   }


   @TestTemplate
   public void testTXPrepare() throws Exception {
      DatabaseStorageManager databaseStorageManager = new DatabaseStorageManager(configuration, criticalAnalyzer, executorFactory, executorFactory, scheduledExecutorService, executorService, null);
      databaseStorageManager.setContext(null);
      databaseStorageManager.start();
      databaseStorageManager.generateID();
      try {
         DatabaseProvider databaseProvider = storageConfiguration.getDatabaseProvider();

         Xid xid = newXID();
         TransactionImpl transaction = new TransactionImpl(xid, databaseStorageManager, -1);
         transaction.setContainsPersistent();
         transaction.prepare();
         databaseStorageManager.waitOnOperations();

         try (Connection connection = databaseProvider.getConnection()) {
            assertEquals(1, selectCount(connection, "DB_PREP_TX"));
         }

      } finally {
         databaseStorageManager.stop();
      }
   }

}
