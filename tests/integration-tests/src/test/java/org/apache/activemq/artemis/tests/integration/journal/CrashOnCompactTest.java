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
package org.apache.activemq.artemis.tests.integration.journal;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.io.nio.NIOSequentialFileFactory;
import org.apache.activemq.artemis.core.journal.PreparedTransactionInfo;
import org.apache.activemq.artemis.core.journal.RecordInfo;
import org.apache.activemq.artemis.core.journal.impl.AbstractJournalUpdateTask;
import org.apache.activemq.artemis.core.journal.impl.JournalCompactor;
import org.apache.activemq.artemis.core.journal.impl.JournalFile;
import org.apache.activemq.artemis.core.journal.impl.JournalImpl;
import org.apache.activemq.artemis.tests.util.SpawnedTestBase;
import org.apache.activemq.artemis.utils.SpawnedVMSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CrashOnCompactTest extends SpawnedTestBase {

   static int OK = 2;
   static int NOT_OK = 3;

   @BeforeEach
   public void setup() throws Exception {
   }

   @Test
   public void testCrashCompact() throws Exception {
      Process process = SpawnedVMSupport.spawnVM(CrashOnCompactTest.class.getCanonicalName(), getTestDirfile().getAbsolutePath());
      assertEquals(OK, process.waitFor());
      checkJournalSize();
   }

   @Test
   public void testAddJournal() throws Exception {
      addJournal(getTestDirfile(), false);
      checkJournalSize();
   }

   private void checkJournalSize() throws Exception {
      JournalImpl journal = createJournal(getTestDirfile(), false);
      List<RecordInfo> info = new ArrayList<>();
      List<PreparedTransactionInfo> txInfo = new ArrayList<>();
      journal.load(info, txInfo, (transactionID, records, recordsToDelete) -> { });
      assertEquals(900, info.size());
   }

   private static void addJournal(File folder, boolean crash) throws Exception {
      JournalImpl journal = createJournal(folder, crash);
      journal.loadInternalOnly();
      for (int i = 0; i < 1000; i++) {
         journal.appendAddRecord(i, (byte) 1, new byte[5], true);
      }

      for (int i = 0; i < 100; i++) {
         journal.appendDeleteRecord(i, true);
      }

      journal.compact();
      journal.stop();
   }

   public static void main(String[] arg) {

      try {
         addJournal(new File(arg[0]), true);
      } catch (Exception e) {
         e.printStackTrace();
      }
      System.exit(NOT_OK);

   }

   private static JournalImpl createJournal(File folder, boolean crash) throws Exception {
      NIOSequentialFileFactory factory = new NIOSequentialFileFactory(folder, 10);
      JournalImpl journal = new JournalImpl(100 * 1024, 2, 2, 0, 0, factory, "jrntest", "jrn", 512) {

         @Override
         protected SequentialFile writeControlFile(final SequentialFileFactory fileFactory,
                                                   final List<JournalFile> files,
                                                   final List<JournalFile> newFiles,
                                                   final List<Pair<String, String>> renames) throws Exception {

            if (crash) {
               SequentialFile controlFile = fileFactory.createSequentialFile(AbstractJournalUpdateTask.FILE_COMPACT_CONTROL);
               controlFile.open();
               controlFile.close();
               System.err.println("crashing after creation of control file");
               System.exit(OK);
            }
            return JournalCompactor.writeControlFile(fileFactory, files, newFiles, renames);
         }

      };
      journal.setAutoReclaim(false);

      journal.start();
      return journal;

   }

}
