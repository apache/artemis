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
package org.apache.activemq.artemis.tests.integration.persistence;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.StoreConfiguration;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.JDBCJournalStorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager;
import org.apache.activemq.artemis.tests.unit.core.server.impl.fakes.FakeJournalLoader;
import org.apache.activemq.artemis.tests.unit.core.server.impl.fakes.FakePostOffice;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.apache.activemq.artemis.utils.critical.EmptyCriticalAnalyzer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public abstract class StorageManagerTestBase extends ActiveMQTestBase {

   protected ExecutorService executor;

   protected ExecutorFactory execFactory;

   protected ScheduledExecutorService scheduledExecutorService;

   protected StorageManager journal;

   protected StoreConfiguration.StoreType storeType;

   public StorageManagerTestBase(StoreConfiguration.StoreType storeType) {
      this.storeType = storeType;
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      execFactory = getOrderedExecutor();

      scheduledExecutorService = new ScheduledThreadPoolExecutor(5);

      createStorage();
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      Exception exception = null;

      if (journal != null) {
         try {
            journal.stop();
         } catch (Exception e) {
            exception = e;
         }

         journal = null;
      }

      scheduledExecutorService.shutdown();

      super.tearDown();
      if (exception != null) {
         throw exception;
      }
   }

   protected void createStorage() throws Exception {

      if (storeType == StoreConfiguration.StoreType.DATABASE) {
         journal = createJDBCJournalStorageManager(createDefaultJDBCConfig(true));
      } else {
         journal = createJournalStorageManager(createDefaultInVMConfig());
      }

      journal.start();

      journal.loadBindingJournal(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

      journal.loadMessageJournal(new FakePostOffice(), null, null, null, null, null, null, null, new FakeJournalLoader());
   }

   /**
    * This forces a reload of the journal from disk
    */
   protected void rebootStorage() throws Exception {
      journal.stop();
      createStorage();
   }

   protected JournalStorageManager createJournalStorageManager(Configuration configuration) {
      JournalStorageManager jsm = new JournalStorageManager(configuration, EmptyCriticalAnalyzer.getInstance(), execFactory, execFactory);
      addActiveMQComponent(jsm);
      return jsm;
   }

   protected JDBCJournalStorageManager createJDBCJournalStorageManager(Configuration configuration) {
      JDBCJournalStorageManager jsm = new JDBCJournalStorageManager(configuration, EmptyCriticalAnalyzer.getInstance(), execFactory, execFactory, scheduledExecutorService);
      addActiveMQComponent(jsm);
      return jsm;
   }
}
