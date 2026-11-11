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

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import org.apache.activemq.artemis.core.persistence.QueueBindingInfo;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager;
import org.apache.activemq.artemis.core.postoffice.PostOffice;
import org.apache.activemq.artemis.tests.unit.core.server.impl.fakes.FakeJournalLoader;
import org.apache.activemq.artemis.tests.unit.core.server.impl.fakes.FakePostOffice;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.apache.activemq.artemis.utils.critical.EmptyCriticalAnalyzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestartSMTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   ExecutorService executor;

   ExecutorFactory execFactory;




   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      execFactory = getOrderedExecutor();
   }

   @Test
   public void testRestartStorageManager() throws Exception {
      File testdir = new File(getTestDir());
      deleteDirectory(testdir);

      PostOffice postOffice = new FakePostOffice();

      final JournalStorageManager journal = new JournalStorageManager(createDefaultInVMConfig(), EmptyCriticalAnalyzer.getInstance(), execFactory, execFactory);

      try {

         journal.start();

         List<QueueBindingInfo> queueBindingInfos = new ArrayList<>();

         journal.loadBindingJournal(queueBindingInfos, new ArrayList<>(), new ArrayList<>());

         journal.loadMessageJournal(postOffice, null, null, null, null, null, null, new FakeJournalLoader());

         journal.stop();

         deleteDirectory(testdir);

         journal.start();

         journal.loadMessageJournal(postOffice, null, null, null, null, null, null, new FakeJournalLoader());

         queueBindingInfos = new ArrayList<>();

         journal.loadBindingJournal(queueBindingInfos, new ArrayList<>(), new ArrayList<>());

         journal.start();
      } finally {

         try {
            journal.stop();
         } catch (Exception ex) {
            logger.warn(ex.getMessage(), ex);
         }
      }
   }

}
