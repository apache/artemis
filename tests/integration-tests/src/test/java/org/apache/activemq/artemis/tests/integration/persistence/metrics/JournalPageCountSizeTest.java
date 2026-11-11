/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.tests.integration.persistence.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedList;
import java.util.List;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.journal.PreparedTransactionInfo;
import org.apache.activemq.artemis.core.journal.RecordInfo;
import org.apache.activemq.artemis.core.journal.TransactionFailureCallback;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.PageCountRecord;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.PageCountRecordInc;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class JournalPageCountSizeTest extends ActiveMQTestBase {

   private ActiveMQServer server;

   @BeforeEach
   public void init() throws Exception {
      server = createServer(true);

      server.start();
   }

   @Override
   protected ConfigurationImpl createBasicConfig(int serverID) {
      return super.createBasicConfig(serverID);
   }

   @AfterEach
   public void destroy() throws Exception {
      server.stop();
   }

   @Test
   public void testPageCountRecordSize() throws Exception {

      long tx = server.getStorageManager().generateID();
      server.getStorageManager().storePageCounter(tx, 1, 1, 100);
      server.getStorageManager().commit(tx);
      server.getStorageManager().stop();

      JournalStorageManager journalStorageManager = (JournalStorageManager) server.getStorageManager();
      List<RecordInfo> committedRecords = new LinkedList<>();
      List<PreparedTransactionInfo> preparedTransactions = new LinkedList<>();

      try {
         journalStorageManager.getMessageJournal().start();
         journalStorageManager.getMessageJournal().load(committedRecords, preparedTransactions, transactionFailure);

         ActiveMQBuffer buff = ActiveMQBuffers.wrappedBuffer(committedRecords.get(0).data);
         PageCountRecord encoding = new PageCountRecord();
         encoding.decode(buff);

         assertEquals(100, encoding.getPersistentSize());
      } finally {
         journalStorageManager.getMessageJournal().stop();
      }

   }

   @Test
   public void testPageCursorCounterRecordSize() throws Exception {

      server.getStorageManager().storePageCounterInc(1, 1, 1000);
      server.getStorageManager().stop();

      JournalStorageManager journalStorageManager = (JournalStorageManager) server.getStorageManager();
      List<RecordInfo> committedRecords = new LinkedList<>();
      List<PreparedTransactionInfo> preparedTransactions = new LinkedList<>();

      try {
         journalStorageManager.getMessageJournal().start();
         journalStorageManager.getMessageJournal().load(committedRecords, preparedTransactions, transactionFailure);

         ActiveMQBuffer buff = ActiveMQBuffers.wrappedBuffer(committedRecords.get(0).data);
         PageCountRecordInc encoding = new PageCountRecordInc();
         encoding.decode(buff);

         assertEquals(1000, encoding.getPersistentSize());
      } finally {
         journalStorageManager.getMessageJournal().stop();
      }

   }

   @Test
   public void testPageCursorCounterRecordSizeTX() throws Exception {

      long tx = server.getStorageManager().generateID();
      server.getStorageManager().storePageCounterInc(tx, 1, 1, 1000);
      server.getStorageManager().commit(tx);
      server.getStorageManager().stop();

      JournalStorageManager journalStorageManager = (JournalStorageManager) server.getStorageManager();
      List<RecordInfo> committedRecords = new LinkedList<>();
      List<PreparedTransactionInfo> preparedTransactions = new LinkedList<>();

      try {
         journalStorageManager.getMessageJournal().start();
         journalStorageManager.getMessageJournal().load(committedRecords, preparedTransactions, transactionFailure);

         ActiveMQBuffer buff = ActiveMQBuffers.wrappedBuffer(committedRecords.get(0).data);
         PageCountRecordInc encoding = new PageCountRecordInc();
         encoding.decode(buff);

         assertEquals(1000, encoding.getPersistentSize());
      } finally {
         journalStorageManager.getMessageJournal().stop();
      }

   }

   private TransactionFailureCallback transactionFailure = (transactionID, records, recordsToDelete) -> { };
}
