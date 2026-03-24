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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import org.apache.activemq.artemis.core.persistence.AddressBindingInfo;
import org.apache.activemq.artemis.core.persistence.GroupingInfo;
import org.apache.activemq.artemis.core.persistence.QueueBindingInfo;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager;
import org.apache.activemq.artemis.core.postoffice.PostOffice;
import org.apache.activemq.artemis.tests.unit.core.server.impl.fakes.FakeJournalLoader;
import org.apache.activemq.artemis.tests.unit.core.server.impl.fakes.FakePostOffice;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.apache.activemq.artemis.utils.critical.EmptyCriticalAnalyzer;
import org.junit.Before;
import org.junit.Test;

public class RestartSMTest extends ActiveMQTestBase {



   ExecutorService executor;

   ExecutorFactory execFactory;




   @Override
   @Before
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

         journal.loadBindingJournal(queueBindingInfos, new ArrayList<GroupingInfo>(), new ArrayList<AddressBindingInfo>());

         journal.loadMessageJournal(postOffice, null, null, null, null, null, null, new FakeJournalLoader());

         journal.stop();

         deleteDirectory(testdir);

         journal.start();

         journal.loadMessageJournal(postOffice, null, null, null, null, null, null, new FakeJournalLoader());

         queueBindingInfos = new ArrayList<>();

         journal.loadBindingJournal(queueBindingInfos, new ArrayList<GroupingInfo>(), new ArrayList<AddressBindingInfo>());

         journal.start();
      } finally {

         try {
            journal.stop();
         } catch (Exception ex) {
            instanceLog.warn(ex.getMessage(), ex);
         }
      }
   }

}
