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
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.persistence.impl.database.DatabaseStorageManager;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.core.transaction.impl.TransactionImpl;
import org.apache.activemq.artemis.jdbc.store.drivers.JDBCConnectionProvider;
import org.apache.activemq.artemis.tests.db.dbstorage.CountDownCompletion;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledIf("isNoDatabaseSelected")
@ExtendWith(ParameterizedTestExtension.class)
public class AddressInfoTest extends AbstractStatementTest {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @TestTemplate
   public void testAddressInfoDirectly() throws Exception {
      DatabaseStorageManager databaseStorageManager = new DatabaseStorageManager(configuration, criticalAnalyzer, executorFactory, executorFactory, scheduledExecutorService, executorService, null);
      databaseStorageManager.start();
      try {
         JDBCConnectionProvider connectionProvider = storageConfiguration.getConnectionProvider();

         int nrecords = 5;

         try (Connection connection = connectionProvider.getConnection()) {
            connection.setAutoCommit(false);
            for (int i = 0; i < nrecords; i++) {
               AddressInfo info = new AddressInfo("Orders" + i);
               info.addRoutingType(RoutingType.ANYCAST);
               info.addRoutingType(RoutingType.MULTICAST);
               Transaction tx = new TransactionImpl(databaseStorageManager);

               databaseStorageManager.addAddressBinding(tx, info);
               databaseStorageManager.commit(tx, true);
            }

            CountDownCompletion completion = new CountDownCompletion(1);
            databaseStorageManager.getContext().executeOnCompletion(completion);
            assertTrue(completion.await(10, TimeUnit.SECONDS));

            assertEquals(nrecords, selectCount(connection, "DB_ADDRESS"));
         }
      } finally {
         databaseStorageManager.stop();
      }
   }
}