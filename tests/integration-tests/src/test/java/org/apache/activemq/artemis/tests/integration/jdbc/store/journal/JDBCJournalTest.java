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
package org.apache.activemq.artemis.tests.integration.jdbc.store.journal;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.core.config.storage.DatabaseStorageConfiguration;
import org.apache.activemq.artemis.core.journal.IOCompletion;
import org.apache.activemq.artemis.core.journal.PreparedTransactionInfo;
import org.apache.activemq.artemis.core.journal.RecordInfo;
import org.apache.activemq.artemis.jdbc.store.drivers.JDBCUtils;
import org.apache.activemq.artemis.jdbc.store.journal.JDBCJournalImpl;
import org.apache.activemq.artemis.jdbc.store.sql.SQLProvider;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameter;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@ExtendWith(ParameterizedTestExtension.class)
public class JDBCJournalTest extends ActiveMQTestBase {

   private JDBCJournalImpl journal;

   private ScheduledExecutorService scheduledExecutorService;

   private ExecutorService executorService;

   private SQLProvider sqlProvider;

   private DatabaseStorageConfiguration dbConf;

   @Parameter(index = 0)
   public boolean useAuthentication;

   @Parameters(name = "authentication = {0}")
   public static Collection<Object[]> data() {
      return Arrays.asList(new Object[][]{{false}, {true}});
   }

   @AfterEach
   @Override
   public void tearDown() throws Exception {
      super.tearDown();
      journal.destroy();
      scheduledExecutorService.shutdownNow();
      scheduledExecutorService = null;
      executorService.shutdown();
      executorService = null;
   }

   @Override
   protected String getJDBCUser() {
      if (useAuthentication) {
         return System.getProperty("jdbc.user", "testuser");
      } else {
         return null;
      }
   }

   @Override
   protected String getJDBCPassword() {
      if (useAuthentication) {
         return System.getProperty("jdbc.password", "testpassword");
      } else {
         return null;
      }
   }

   @BeforeEach
   public void setup() throws Exception {
      dbConf = createDefaultDatabaseStorageConfiguration();
      if (useAuthentication) {
         dbConf.setJdbcUser(getJDBCUser());
         dbConf.setJdbcPassword(getJDBCPassword());
      }
      sqlProvider = JDBCUtils.getSQLProvider(
         dbConf.getJdbcDriverClassName(),
         dbConf.getMessageTableName(),
         SQLProvider.DatabaseStoreType.MESSAGE_JOURNAL);
      scheduledExecutorService = new ScheduledThreadPoolExecutor(5);
      executorService = Executors.newSingleThreadExecutor();
      journal = new JDBCJournalImpl(dbConf.getConnectionProvider(), sqlProvider, scheduledExecutorService, executorService, (code, message, file) -> {

      }, 5);
      journal.start();
   }

   @TestTemplate
   public void testRestartEmptyJournal() throws SQLException {
      assertTrue(journal.isStarted());
      assertTrue(journal.isStarted());
      journal.stop();
      journal.start();
      assertTrue(journal.isStarted());
   }

   @TestTemplate
   public void testConcurrentEmptyJournal() throws SQLException {
      assertTrue(journal.isStarted());
      assertEquals(0, journal.getNumberOfRecords());
      final JDBCJournalImpl secondJournal = new JDBCJournalImpl(dbConf.getConnectionProvider(),
                                                                          sqlProvider, scheduledExecutorService,
                                                                          executorService, (code, message, file) -> {
         fail(message);
      }, 5);
      secondJournal.start();
      try {
         assertTrue(secondJournal.isStarted());
      } finally {
         secondJournal.stop();
      }
   }

   @TestTemplate
   public void testInsertRecords() throws Exception {
      int noRecords = 10;
      for (int i = 0; i < noRecords; i++) {
         journal.appendAddRecord(i, (byte) 1, new byte[0], true);
      }

      assertEquals(noRecords, journal.getNumberOfRecords());
   }

   @TestTemplate
   public void testCleanupTxRecords() throws Exception {
      journal.appendDeleteRecordTransactional(1, 1);
      journal.appendCommitRecord(1, true);
      assertEquals(0, journal.getNumberOfRecords());
   }

   @TestTemplate
   public void testCleanupTxRecords4TransactionalRecords() throws Exception {
      // add committed transactional record e.g. paging
      journal.appendAddRecordTransactional(152, 154, (byte) 13, new byte[0]);
      journal.appendCommitRecord(152, true);
      assertEquals(2, journal.getNumberOfRecords());

      // delete transactional record in new transaction e.g. depaging
      journal.appendDeleteRecordTransactional(236, 154);
      journal.appendCommitRecord(236, true);
      assertEquals(0, journal.getNumberOfRecords());
   }

   @TestTemplate
   public void testCallbacks() throws Exception {
      final int noRecords = 10;
      final CountDownLatch done = new CountDownLatch(noRecords);

      IOCompletion completion = new IOCompletion() {
         @Override
         public void storeLineUp() {
         }

         @Override
         public void done() {
            done.countDown();
         }

         @Override
         public void onError(int errorCode, String errorMessage) {

         }
      };

      for (int i = 0; i < noRecords; i++) {
         journal.appendAddRecord(1, (byte) 1, new FakeEncodingSupportImpl(new byte[0]), true, completion);
      }
      journal.sync();

      done.await(5, TimeUnit.SECONDS);
      assertEquals(0, done.getCount());
   }

   @TestTemplate
   public void testReadJournal() throws Exception {
      int noRecords = 100;

      // Standard Add Records
      for (int i = 0; i < noRecords; i++) {
         journal.appendAddRecord(i, (byte) i, new byte[i], true);
      }

      // TX Records
      int noTx = 10;
      int noTxRecords = 100;
      for (int i = 1000; i < 1000 + noTx; i++) {
         for (int j = 0; j < noTxRecords; j++) {
            journal.appendAddRecordTransactional(i, Long.parseLong(i + "" + j), (byte) 1, new byte[0]);
         }
         journal.appendPrepareRecord(i, new byte[0], true);
         journal.appendCommitRecord(i, true);
      }

      Thread.sleep(2000);
      List<RecordInfo> recordInfos = new ArrayList<>();
      List<PreparedTransactionInfo> txInfos = new ArrayList<>();

      journal.load(recordInfos, txInfos, null);

      assertEquals(noRecords + (noTxRecords * noTx), recordInfos.size());
   }

}
