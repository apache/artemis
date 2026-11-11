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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.StoreConfiguration;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.PostOfficeJournalLoader;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.unit.core.postoffice.impl.fakes.FakeQueue;
import org.apache.activemq.artemis.tests.unit.core.server.impl.fakes.FakePostOffice;
import org.apache.activemq.artemis.utils.critical.EmptyCriticalAnalyzer;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(ParameterizedTestExtension.class)
public class DeleteMessagesOnStartupTest extends StorageManagerTestBase {

   List<Long> deletedMessage = new ArrayList<>();

   public DeleteMessagesOnStartupTest(StoreConfiguration.StoreType storeType) {
      super(storeType);
   }

   // This is only applicable for FILE based store, as the database storage manager will automatically delete records.
   @Parameters(name = "storeType")
   public static Collection<Object[]> data() {
      Object[][] params = new Object[][]{{StoreConfiguration.StoreType.FILE}};
      return Arrays.asList(params);
   }

   @TestTemplate
   public void testDeleteMessagesOnStartup() throws Exception {
      Queue theQueue = new FakeQueue(SimpleString.of(""));
      Map<Long, Queue> queues = new HashMap<>();
      queues.put(100L, theQueue);

      Message msg = new CoreMessage(1, 100);

      journal.storeMessage(msg);

      for (int i = 2; i < 100; i++) {
         journal.storeMessage(new CoreMessage(i, 100));
      }

      journal.storeReference(100, 1, true);

      journal.stop();

      journal.start();

      journal.loadBindingJournal(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

      FakePostOffice postOffice = new FakePostOffice();

      journal.loadMessageJournal(postOffice, null, null, null, null, null, null, null, new PostOfficeJournalLoader(postOffice, null, journal, null, null, null, null, null, queues));

      assertEquals(98, deletedMessage.size());

      for (Long messageID : deletedMessage) {
         assertTrue(messageID.longValue() >= 2 && messageID <= 99, "messageID = " + messageID);
      }
   }

   @Override
   protected JournalStorageManager createJournalStorageManager(Configuration configuration) {
      return new JournalStorageManager(configuration, EmptyCriticalAnalyzer.getInstance(), execFactory, execFactory) {
         @Override
         public void deleteMessage(final long messageID) throws Exception {
            deletedMessage.add(messageID);
            super.deleteMessage(messageID);
         }
      };
   }
}
