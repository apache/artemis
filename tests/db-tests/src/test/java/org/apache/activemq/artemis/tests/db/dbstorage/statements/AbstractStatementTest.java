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
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.StoreConfiguration;
import org.apache.activemq.artemis.core.config.storage.DatabaseStorageConfiguration;
import org.apache.activemq.artemis.tests.db.common.Database;
import org.apache.activemq.artemis.tests.db.common.ParameterDBTestBase;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.artemis.utils.actors.OrderedExecutorFactory;
import org.apache.activemq.artemis.utils.critical.CriticalAnalyzer;
import org.apache.artemis.database.DatabaseProvider;
import org.apache.artemis.database.sql.SQLProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisabledIf("isNoDatabaseSelected")
@ExtendWith(ParameterizedTestExtension.class)
public class AbstractStatementTest extends ParameterDBTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   protected Configuration configuration;
   protected DatabaseStorageConfiguration storageConfiguration;

   protected ExecutorService executorService;

   protected ScheduledExecutorService scheduledExecutorService;

   protected ExecutorFactory executorFactory;

   protected CriticalAnalyzer criticalAnalyzer;

   @Parameters(name = "db={0}")
   public static Collection<Object[]> parameters() {
      List<Database> dbList = Database.selectedList();
      return convertParameters(dbList);
   }

   // Used in @DisabledIf on class, avoids no-params failure with only -PDB-hsql-tests
   public static boolean isNoDatabaseSelected() {
      return parameters().isEmpty();
   }

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      super.setUp();
      if (isDropDatabase()) {
         dropDatabase();
      }
   }


   @Override
   protected final String getJDBCClassName() {
      return database.getDriverClass();
   }

   @BeforeEach
   public void setupTest() throws Exception {
      storageConfiguration = createDefaultDatabaseStorageConfiguration();
      storageConfiguration.setStoreType(StoreConfiguration.StoreType.NEW_DATABASE);
      this.configuration = createDefaultNettyConfig();
      this.configuration.setStoreConfiguration(storageConfiguration);

      executorService = Executors.newFixedThreadPool(5);
      runAfter(executorService::shutdownNow);

      scheduledExecutorService = Executors.newScheduledThreadPool(5);
      runAfter(scheduledExecutorService::shutdownNow);

      executorFactory = new OrderedExecutorFactory(executorService);
      criticalAnalyzer = Mockito.mock(CriticalAnalyzer.class);
   }



   public void checkMessageCounts(int messageCount, boolean async) throws Exception {
      DatabaseProvider databaseProvider = storageConfiguration.getDatabaseProvider();
      SQLProvider sqlProvider = databaseProvider.getSqlProvider();
      try (Connection jdbcconnection = databaseProvider.getConnection()) {
         if (async) {
            Wait.assertEquals(messageCount, () -> selectCount(jdbcconnection, sqlProvider.getMessages()), 5000, 100);
            Wait.assertEquals(messageCount, () -> selectCount(jdbcconnection, sqlProvider.getRefs()), 5000, 100);
         } else {
            assertEquals(messageCount, selectCount(jdbcconnection, sqlProvider.getMessages()));
            assertEquals(messageCount, selectCount(jdbcconnection, sqlProvider.getRefs()));
         }
      }
   }
}
