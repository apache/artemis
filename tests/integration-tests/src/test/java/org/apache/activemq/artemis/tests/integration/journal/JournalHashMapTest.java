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

package org.apache.activemq.artemis.tests.integration.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.core.io.nio.NIOSequentialFileFactory;
import org.apache.activemq.artemis.core.journal.IOCompletion;
import org.apache.activemq.artemis.core.journal.Journal;
import org.apache.activemq.artemis.core.journal.PreparedTransactionInfo;
import org.apache.activemq.artemis.core.journal.RecordInfo;
import org.apache.activemq.artemis.core.journal.collections.AbstractHashMapPersister;
import org.apache.activemq.artemis.core.journal.collections.MapStorageManager;
import org.apache.activemq.artemis.core.journal.collections.JournalHashMap;
import org.apache.activemq.artemis.core.journal.collections.JournalHashMapProvider;
import org.apache.activemq.artemis.core.journal.impl.JournalImpl;
import org.apache.activemq.artemis.core.persistence.Persister;
import org.apache.activemq.artemis.core.persistence.impl.journal.OperationContextImpl;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.DataConstants;
import org.apache.activemq.artemis.utils.actors.OrderedExecutorFactory;
import org.junit.jupiter.api.Test;

public class JournalHashMapTest extends ActiveMQTestBase {


   static class JournalManager implements MapStorageManager {
      final Journal journal;

      JournalManager(Journal journal) {
         this.journal = journal;
      }

      @Override
      public void storeMapRecord(long id,
                                 byte recordType,
                                 Persister persister,
                                 Object record,
                                 boolean sync,
                                 IOCompletion completionCallback) throws Exception {
         journal.appendAddRecord(id, recordType, persister, record, sync, completionCallback);
      }

      @Override
      public void storeMapRecord(long id,
                                 byte recordType,
                                 Persister persister,
                                 Object record,
                                 boolean sync) throws Exception {
         journal.appendAddRecord(id, recordType, persister, record, sync);
      }

      @Override
      public void deleteMapRecord(long id, boolean sync) throws Exception {
         journal.appendDeleteRecord(id, sync);
      }

      @Override
      public void deleteMapRecordTx(long txid, long id) throws Exception {
         journal.appendDeleteRecordTransactional(txid, id);

      }
   }

   @Test
   public void testHashMap() throws Exception {
      ExecutorService service = Executors.newFixedThreadPool(10);
      runAfter(service::shutdownNow);
      OrderedExecutorFactory executorFactory = new OrderedExecutorFactory(service);

      NIOSequentialFileFactory factory = new NIOSequentialFileFactory(getTestDirfile(), 1);

      JournalImpl journal = new JournalImpl(executorFactory, 10 * 1024, 10, 10, 3, 0, 50_000, factory, "coll", "data", 1, 0);

      journal.start();
      runAfter(journal::stop);

      journal.loadInternalOnly();

      AtomicLong sequence = new AtomicLong(1);

      JournalHashMapProvider<Long, Long, Object> journalHashMapProvider = new JournalHashMapProvider(sequence::incrementAndGet, new JournalManager(journal), new LongPersister(), (byte)3, OperationContextImpl::getContext, l -> null, (e, m, f) -> {
         e.printStackTrace();
      });

      JournalHashMap<Long, Long, Object> journalHashMap = journalHashMapProvider.getMap(1);

      for (long i = 0; i < 1000; i++) {
         journalHashMap.put(i, RandomUtil.randomLong());
      }

      /// repeating to make sure the remove works fine
      for (long i = 0; i < 1000; i++) {
         journalHashMap.put(i, RandomUtil.randomLong());
      }


      journal.flush();

      journal.stop();

      journalHashMapProvider.clear();

      journal.start();


      List<RecordInfo> recordInfos = new ArrayList<>();
      List<PreparedTransactionInfo> preparedTransactions = new ArrayList<>();
      journal.load(recordInfos, preparedTransactions, (a, b, c) -> { }, true);

      List<JournalHashMap.MapRecord<Long, Long>> records = new ArrayList<>();
      recordInfos.forEach(r -> {
         assertEquals((byte)3, r.userRecordType);
         journalHashMapProvider.reload(r);
      });

      List<JournalHashMap<Long, Long, Object>> existingLists = journalHashMapProvider.getMaps();
      assertEquals(1, existingLists.size());
      JournalHashMap<Long, Long, Object> reloadedList = existingLists.get(0);

      assertEquals(journalHashMap.size(), reloadedList.size());

      journalHashMap.forEach((a, b) -> assertEquals(b, reloadedList.get(a)));

   }


   private static class LongPersister extends AbstractHashMapPersister<Long, Long> {

      @Override
      public byte getID() {
         return 0;
      }

      @Override
      protected int getKeySize(Long key) {
         return DataConstants.SIZE_LONG;
      }

      @Override
      protected void encodeKey(ActiveMQBuffer buffer, Long key) {
         buffer.writeLong(key);

      }

      @Override
      protected Long decodeKey(ActiveMQBuffer buffer) {
         return buffer.readLong();
      }

      @Override
      protected int getValueSize(Long value) {
         return DataConstants.SIZE_LONG;
      }

      @Override
      protected void encodeValue(ActiveMQBuffer buffer, Long value) {
         buffer.writeLong(value);
      }

      @Override
      protected Long decodeValue(ActiveMQBuffer buffer, Long key) {
         return buffer.readLong();
      }
   }
}
