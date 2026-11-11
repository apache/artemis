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

import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.io.nio.NIOSequentialFileFactory;
import org.apache.activemq.artemis.core.journal.EncodingSupport;
import org.apache.activemq.artemis.tests.unit.core.journal.impl.JournalImplTestBase;
import org.apache.activemq.artemis.tests.unit.core.journal.impl.fakes.SimpleEncoding;
import org.junit.jupiter.api.Test;

public class NIOImportExportTest extends JournalImplTestBase {

   @Override
   protected SequentialFileFactory getFileFactory() throws Exception {
      return new NIOSequentialFileFactory(getTestDirfile(), true, 1);
   }

   @Test
   public void testExportImport() throws Exception {
      setup(10, 10 * 4096, true);

      createJournal();

      startJournal();

      load();

      add(1, 2);

      journal.forceMoveNextFile();

      delete(1, 2);

      add(3, 4);

      journal.forceMoveNextFile();

      addTx(5, 6, 7, 8);

      journal.forceMoveNextFile();

      addTx(5, 9);

      commit(5);

      journal.forceMoveNextFile();

      deleteTx(10, 6, 7, 8, 9);

      commit(10);

      addTx(11, 11, 12);
      updateTx(11, 11, 12);
      commit(11);

      journal.forceMoveNextFile();

      update(11, 12);

      stopJournal();

      exportImportJournal();

      createJournal();

      startJournal();

      loadAndCheck();

   }

   @Test
   public void testExportImport3() throws Exception {
      setup(10, 10 * 4096, true);

      createJournal();

      startJournal();

      load();

      add(1, 2);

      journal.forceMoveNextFile();

      delete(1, 2);

      add(3, 4);

      journal.forceMoveNextFile();

      addTx(5, 6, 7, 8);

      journal.forceMoveNextFile();

      addTx(5, 9);

      commit(5);

      journal.forceMoveNextFile();

      deleteTx(10, 6, 7, 8, 9);

      commit(10);

      addTx(11, 12, 13);

      EncodingSupport xid = new SimpleEncoding(10, (byte) 0);
      prepare(11, xid);

      stopJournal();

      exportImportJournal();

      createJournal();

      startJournal();

      loadAndCheck();

      commit(11);

      stopJournal();

      exportImportJournal();

      createJournal();

      startJournal();

      loadAndCheck();

   }

   @Test
   public void testExportImport2() throws Exception {
      setup(10, 10 * 4096, true);

      createJournal();

      startJournal();

      load();

      add(1);

      stopJournal();

      exportImportJournal();

      createJournal();

      startJournal();

      loadAndCheck();

   }

}
