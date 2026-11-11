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
package org.apache.activemq.artemis.tests.integration.largemessage;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.core.journal.Journal;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.PendingLargeMessageEncoding;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validate that large message pending records are cleared after a restart.
 * {@link org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds#ADD_LARGE_MESSAGE_PENDING} is not used any longer
 * however if this record is in the journal the server should clear any previous data
 */
public class PendingLargeMessageTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Test
   public void testPendingRecords() throws Exception {
      ActiveMQServer server = createServer(true);

      server.start();

      Journal journal = server.getStorageManager().getMessageJournal();

      for (int i = 0; i < 100; i++) {
         long recordID = server.getStorageManager().generateID();
         long fakeMessageID = server.getStorageManager().generateID();
         // this is storing the record that we used to use
         journal.appendAddRecord(recordID, JournalRecordIds.ADD_LARGE_MESSAGE_PENDING, new PendingLargeMessageEncoding(fakeMessageID), true, server.getStorageManager().getContext());
      }

      server.getStorageManager().getContext().waitCompletion();

      server.stop();
      try (AssertionLoggerHandler assertionLoggerHandler = new AssertionLoggerHandler()) {
         server.start();
         assertFalse(assertionLoggerHandler.findText("large message"));
         assertFalse(assertionLoggerHandler.findText("AMQ221005"));
      }

      // compact to get rid of old records from the journal
      server.getStorageManager().getMessageJournal().scheduleCompactAndBlock(10_000);

      HashMap<Integer, AtomicInteger> records = countJournal(server.getConfiguration());

      AtomicInteger recordsForPending = records.get(Integer.valueOf(JournalRecordIds.ADD_LARGE_MESSAGE_PENDING));
      assertTrue(recordsForPending == null || recordsForPending.get() == 0);
   }
}