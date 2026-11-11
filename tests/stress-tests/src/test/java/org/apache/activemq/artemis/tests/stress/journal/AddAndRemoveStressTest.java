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
package org.apache.activemq.artemis.tests.stress.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.io.aio.AIOSequentialFileFactory;
import org.apache.activemq.artemis.core.journal.LoaderCallback;
import org.apache.activemq.artemis.core.journal.PreparedTransactionInfo;
import org.apache.activemq.artemis.core.journal.RecordInfo;
import org.apache.activemq.artemis.core.journal.impl.JournalImpl;
import org.apache.activemq.artemis.tests.unit.core.journal.impl.fakes.SimpleEncoding;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

public class AddAndRemoveStressTest extends ActiveMQTestBase {



   private static final LoaderCallback dummyLoader = new LoaderCallback() {

      @Override
      public void addPreparedTransaction(final PreparedTransactionInfo preparedTransaction) {
      }

      @Override
      public void addRecord(final RecordInfo info) {
      }

      @Override
      public void deleteRecord(final long id) {
      }

      @Override
      public void updateRecord(final RecordInfo info) {
      }

      @Override
      public void failedTransaction(final long transactionID,
                                    final List<RecordInfo> records,
                                    final List<RecordInfo> recordsToDelete) {
      }
   };

   private static final long NUMBER_OF_MESSAGES = 210000L;

   private static final int NUMBER_OF_FILES_ON_JOURNAL = 6;




   @Test
   public void testInsertAndLoad() throws Exception {

      SequentialFileFactory factory = new AIOSequentialFileFactory(getTestDirfile(), 1000);
      JournalImpl impl = new JournalImpl(10 * 1024 * 1024, AddAndRemoveStressTest.NUMBER_OF_FILES_ON_JOURNAL, AddAndRemoveStressTest.NUMBER_OF_FILES_ON_JOURNAL, 0, 0, factory, "amq", "amq", 1000);

      impl.start();

      impl.load(AddAndRemoveStressTest.dummyLoader);

      for (long i = 1; i <= AddAndRemoveStressTest.NUMBER_OF_MESSAGES; i++) {
         if (i % 10000 == 0) {
            System.out.println("Append " + i);
         }
         impl.appendAddRecord(i, (byte) 0, new SimpleEncoding(1024, (byte) 'f'), false);
      }

      impl.stop();

      factory = new AIOSequentialFileFactory(getTestDirfile(), 1000);
      impl = new JournalImpl(10 * 1024 * 1024, AddAndRemoveStressTest.NUMBER_OF_FILES_ON_JOURNAL, AddAndRemoveStressTest.NUMBER_OF_FILES_ON_JOURNAL, 0, 0, factory, "amq", "amq", 1000);

      impl.start();

      impl.load(AddAndRemoveStressTest.dummyLoader);

      for (long i = 1; i <= AddAndRemoveStressTest.NUMBER_OF_MESSAGES; i++) {
         if (i % 10000 == 0) {
            System.out.println("Delete " + i);
         }

         impl.appendDeleteRecord(i, false);
      }

      impl.stop();

      factory = new AIOSequentialFileFactory(getTestDirfile(), 1000);
      impl = new JournalImpl(10 * 1024 * 1024, AddAndRemoveStressTest.NUMBER_OF_FILES_ON_JOURNAL, AddAndRemoveStressTest.NUMBER_OF_FILES_ON_JOURNAL, 0, 0, factory, "amq", "amq", 1000);

      impl.start();

      List<RecordInfo> info = new ArrayList<>();
      List<PreparedTransactionInfo> trans = new ArrayList<>();

      impl.load(info, trans, null);

      impl.forceMoveNextFile();

      if (!info.isEmpty()) {
         System.out.println("Info ID: " + info.get(0).id);
      }

      impl.stop();

      assertEquals(0, info.size());
      assertEquals(0, trans.size());

      assertEquals(0, impl.getDataFilesCount());

   }

   @Test
   public void testInsertUpdateAndLoad() throws Exception {

      SequentialFileFactory factory = new AIOSequentialFileFactory(getTestDirfile(), 1000);
      JournalImpl impl = new JournalImpl(10 * 1024 * 1024, AddAndRemoveStressTest.NUMBER_OF_FILES_ON_JOURNAL, AddAndRemoveStressTest.NUMBER_OF_FILES_ON_JOURNAL, 0, 0, factory, "amq", "amq", 1000);

      impl.start();

      impl.load(AddAndRemoveStressTest.dummyLoader);

      for (long i = 1; i <= AddAndRemoveStressTest.NUMBER_OF_MESSAGES; i++) {
         if (i % 10000 == 0) {
            System.out.println("Append " + i);
         }
         impl.appendAddRecord(i, (byte) 21, new SimpleEncoding(40, (byte) 'f'), false);
         impl.appendUpdateRecord(i, (byte) 22, new SimpleEncoding(40, (byte) 'g'), false);
      }

      impl.stop();

      factory = new AIOSequentialFileFactory(getTestDirfile(), 1000);
      impl = new JournalImpl(10 * 1024 * 1024, 10, 10, 0, 0, factory, "amq", "amq", 1000);

      impl.start();

      impl.load(AddAndRemoveStressTest.dummyLoader);

      for (long i = 1; i <= AddAndRemoveStressTest.NUMBER_OF_MESSAGES; i++) {
         if (i % 10000 == 0) {
            System.out.println("Delete " + i);
         }

         impl.appendDeleteRecord(i, false);
      }

      impl.stop();

      factory = new AIOSequentialFileFactory(getTestDirfile(), 1000);
      impl = new JournalImpl(10 * 1024 * 1024, AddAndRemoveStressTest.NUMBER_OF_FILES_ON_JOURNAL, AddAndRemoveStressTest.NUMBER_OF_FILES_ON_JOURNAL, 0, 0, factory, "amq", "amq", 1000);

      impl.start();

      List<RecordInfo> info = new ArrayList<>();
      List<PreparedTransactionInfo> trans = new ArrayList<>();

      impl.load(info, trans, null);

      if (!info.isEmpty()) {
         System.out.println("Info ID: " + info.get(0).id);
      }

      impl.forceMoveNextFile();
      impl.checkReclaimStatus();

      impl.stop();

      assertEquals(0, info.size());
      assertEquals(0, trans.size());
      assertEquals(0, impl.getDataFilesCount());

      System.out.println("Size = " + impl.getDataFilesCount());

   }
}
