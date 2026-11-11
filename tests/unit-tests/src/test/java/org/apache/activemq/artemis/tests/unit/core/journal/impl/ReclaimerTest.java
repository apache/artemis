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
package org.apache.activemq.artemis.tests.unit.core.journal.impl;

import static org.apache.activemq.artemis.core.journal.impl.Reclaimer.scan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.journal.impl.JournalFile;
import org.apache.activemq.artemis.core.journal.impl.JournalImpl;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ReclaimerTest extends ActiveMQTestBase {

   private JournalFile[] files;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
   }

   @Test
   public void testOneFilePosNegAll() throws Exception {
      setup(1);

      setupPosNeg(0, 10, 10);

      scan(files);

      assertCanDelete(0);
   }

   @Test
   public void testOneFilePosNegNotAll() throws Exception {
      setup(1);

      setupPosNeg(0, 10, 7);

      scan(files);

      assertCantDelete(0);
   }

   @Test
   public void testOneFilePosOnly() throws Exception {
      setup(1);

      setupPosNeg(0, 10);

      scan(files);

      assertCantDelete(0);
   }

   @Test
   public void testOneFileNegOnly() throws Exception {
      setup(1);

      setupPosNeg(0, 0, 10);

      scan(files);

      assertCanDelete(0);
   }

   @Test
   public void testTwoFilesPosNegAllDifferentFiles() throws Exception {
      setup(2);

      setupPosNeg(0, 10);
      setupPosNeg(1, 0, 10);

      scan(files);

      assertCanDelete(0);
      assertCanDelete(1);

   }

   @Test
   public void testTwoFilesPosNegAllSameFiles() throws Exception {
      setup(2);

      setupPosNeg(0, 10, 10);
      setupPosNeg(1, 10, 0, 10);

      scan(files);

      assertCanDelete(0);
      assertCanDelete(1);

   }

   @Test
   public void testTwoFilesPosNegMixedFiles() throws Exception {
      setup(2);

      setupPosNeg(0, 10, 7);
      setupPosNeg(1, 10, 3, 10);

      scan(files);

      assertCanDelete(0);
      assertCanDelete(1);
   }

   @Test
   public void testTwoFilesPosNegAllFirstFile() throws Exception {
      setup(2);

      setupPosNeg(0, 10, 10);
      setupPosNeg(1, 10);

      scan(files);

      assertCanDelete(0);
      assertCantDelete(1);
   }

   @Test
   public void testTwoFilesPosNegAllSecondFile() throws Exception {
      setup(2);

      setupPosNeg(0, 10);
      setupPosNeg(1, 10, 0, 10);

      scan(files);

      assertCantDelete(0);
      assertCanDelete(1);
   }

   @Test
   public void testTwoFilesPosOnly() throws Exception {
      setup(2);

      setupPosNeg(0, 10);
      setupPosNeg(1, 10);

      scan(files);

      assertCantDelete(0);
      assertCantDelete(1);
   }

   @Test
   public void testTwoFilesxyz() throws Exception {
      setup(2);

      setupPosNeg(0, 10);
      setupPosNeg(1, 10, 10);

      scan(files);

      assertCanDelete(0);
      assertCantDelete(1);
   }

   // Can-can-can

   @Test
   public void testThreeFiles1() throws Exception {
      setup(3);

      setupPosNeg(0, 10, 10, 0, 0);
      setupPosNeg(1, 10, 0, 10, 0);
      setupPosNeg(2, 10, 0, 0, 10);

      scan(files);

      assertCanDelete(0);
      assertCanDelete(1);
      assertCanDelete(2);
   }

   @Test
   public void testThreeFiles2() throws Exception {
      setup(3);

      setupPosNeg(0, 10, 7, 0, 0);
      setupPosNeg(1, 10, 3, 5, 0);
      setupPosNeg(2, 10, 0, 5, 10);

      scan(files);

      assertCanDelete(0);
      assertCanDelete(1);
      assertCanDelete(2);
   }

   @Test
   public void testThreeFiles3() throws Exception {
      setup(3);

      setupPosNeg(0, 10, 1, 0, 0);
      setupPosNeg(1, 10, 6, 5, 0);
      setupPosNeg(2, 10, 3, 5, 10);

      scan(files);

      assertCanDelete(0);
      assertCanDelete(1);
      assertCanDelete(2);
   }

   @Test
   public void testThreeFiles3_1() throws Exception {
      setup(3);

      setupPosNeg(0, 10, 1, 0, 0);
      setupPosNeg(1, 10, 6, 5, 0);
      setupPosNeg(2, 0, 3, 5, 0);

      scan(files);

      assertCanDelete(0);
      assertCanDelete(1);
      assertCanDelete(2);
   }

   @Test
   public void testThreeFiles3_2() throws Exception {
      setup(3);

      setupPosNeg(0, 10, 1, 0, 0);
      setupPosNeg(1, 0, 6, 0, 0);
      setupPosNeg(2, 0, 3, 0, 0);

      scan(files);

      assertCanDelete(0);
      assertCanDelete(1);
      assertCanDelete(2);
   }

   // Cant-can-can

   @Test
   public void testThreeFiles4() throws Exception {
      setup(3);

      setupPosNeg(0, 10, 3, 0, 0);
      setupPosNeg(1, 10, 0, 5, 0);
      setupPosNeg(2, 10, 0, 5, 10);

      scan(files);

      assertCantDelete(0);
      assertCanDelete(1);
      assertCanDelete(2);
   }

   @Test
   public void testThreeFiles5() throws Exception {
      setup(3);

      setupPosNeg(0, 10, 3, 0, 0);
      setupPosNeg(1, 10, 0, 5, 0);
      setupPosNeg(2, 0, 0, 5, 0);

      scan(files);

      assertCantDelete(0);
      assertCanDelete(1);
      assertCanDelete(2);
   }

   @Test
   public void testThreeFiles6() throws Exception {
      setup(3);

      setupPosNeg(0, 10, 0, 0, 0);
      setupPosNeg(1, 10, 0, 5, 0);
      setupPosNeg(2, 0, 0, 5, 10);

      scan(files);

      assertCantDelete(0);
      assertCanDelete(1);
      assertCanDelete(2);
   }

   @Test
   public void testThreeFiles7() throws Exception {
      setup(3);

      setupPosNeg(0, 10, 0, 0, 0);
      setupPosNeg(1, 10, 0, 5, 0);
      setupPosNeg(2, 0, 0, 5, 0);

      scan(files);

      assertCantDelete(0);
      assertCanDelete(1);
      assertCanDelete(2);
   }

   // Cant can cant

   @Test
   public void testThreeFiles8() throws Exception {
      setup(3);

      setupPosNeg(0, 10, 3, 0, 0);
      setupPosNeg(1, 10, 0, 10, 0);
      setupPosNeg(2, 10, 0, 0, 2);

      scan(files);

      assertCantDelete(0);
      assertCanDelete(1);
      assertCantDelete(2);
   }

   @Test
   public void testThreeFiles9() throws Exception {
      setup(3);

      setupPosNeg(0, 10, 3, 0, 0);
      setupPosNeg(1, 10, 0, 10, 0);
      setupPosNeg(2, 10, 1, 0, 2);

      scan(files);

      assertCantDelete(0);
      assertCanDelete(1);
      assertCantDelete(2);
   }

   @Test
   public void testThreeFiles10() throws Exception {
      setup(3);

      setupPosNeg(0, 10, 3, 0, 0);
      setupPosNeg(1, 10, 0, 10, 0);
      setupPosNeg(2, 10, 1, 0, 0);

      scan(files);

      assertCantDelete(0);
      assertCanDelete(1);
      assertCantDelete(2);
   }

   @Test
   public void testThreeFiles11() throws Exception {
      setup(3);

      setupPosNeg(0, 10, 0, 0, 0);
      setupPosNeg(1, 10, 0, 10, 0);
      setupPosNeg(2, 10, 0, 0, 0);

      scan(files);

      assertCantDelete(0);
      assertCanDelete(1);
      assertCantDelete(2);
   }

   @Test
   public void testThreeFiles12() throws Exception {
      setup(3);

      setupPosNeg(0, 10, 0, 0, 0);
      setupPosNeg(1, 10, 0, 10, 0);
      setupPosNeg(2, 0, 3, 0, 0);

      scan(files);

      assertCantDelete(0);
      assertCanDelete(1);
      assertCantDelete(2);
   }

   // Cant-cant-cant

   @Test
   public void testThreeFiles13() throws Exception {
      setup(3);

      setupPosNeg(0, 10, 3, 0, 0);
      setupPosNeg(1, 10, 2, 3, 0);
      setupPosNeg(2, 10, 1, 5, 7);

      scan(files);

      assertCantDelete(0);
      assertCantDelete(1);
      assertCantDelete(2);
   }

   @Test
   public void testThreeFiles14() throws Exception {
      setup(3);

      setupPosNeg(0, 10, 3, 0, 0);
      setupPosNeg(1, 0, 2, 0, 0);
      setupPosNeg(2, 10, 1, 0, 7);

      scan(files);

      assertCantDelete(0);
      assertCantDelete(1);
      assertCantDelete(2);
   }

   @Test
   public void testThreeFiles15() throws Exception {
      setup(3);

      setupPosNeg(0, 10, 3, 0, 0);
      setupPosNeg(1, 10, 2, 3, 0);
      setupPosNeg(2, 0, 1, 5, 0);

      scan(files);

      assertCantDelete(0);
      assertCantDelete(1);
      assertCantDelete(2);
   }

   @Test
   public void testThreeFiles16() throws Exception {
      setup(3);

      setupPosNeg(0, 10, 3, 0, 0);
      setupPosNeg(1, 0, 2, 0, 0);
      setupPosNeg(2, 0, 1, 0, 0);

      scan(files);

      assertCantDelete(0);
      assertCantDelete(1);
      assertCantDelete(2);
   }

   @Test
   public void testThreeFiles17() throws Exception {
      setup(3);

      setupPosNeg(0, 10, 3, 0, 0);
      setupPosNeg(1, 10, 0, 3, 0);
      setupPosNeg(2, 10, 1, 5, 7);

      scan(files);

      assertCantDelete(0);
      assertCantDelete(1);
      assertCantDelete(2);
   }

   @Test
   public void testThreeFiles18() throws Exception {
      setup(3);

      setupPosNeg(0, 10, 3, 0, 0);
      setupPosNeg(1, 10, 0, 3, 0);
      setupPosNeg(2, 10, 1, 0, 7);

      scan(files);

      assertCantDelete(0);
      assertCantDelete(1);
      assertCantDelete(2);
   }

   @Test
   public void testThreeFiles19() throws Exception {
      setup(3);

      setupPosNeg(0, 10, 3, 0, 0);
      setupPosNeg(1, 10, 0, 3, 0);
      setupPosNeg(2, 10, 1, 0, 0);

      scan(files);

      assertCantDelete(0);
      assertCantDelete(1);
      assertCantDelete(2);
   }

   @Test
   public void testThreeFiles20() throws Exception {
      setup(3);

      setupPosNeg(0, 10, 3, 0, 0);
      setupPosNeg(1, 10, 0, 0, 0);
      setupPosNeg(2, 10, 1, 0, 0);

      scan(files);

      assertCantDelete(0);
      assertCantDelete(1);
      assertCantDelete(2);
   }

   @Test
   public void testThreeFiles21() throws Exception {
      setup(3);

      setupPosNeg(0, 10, 0, 0, 0);
      setupPosNeg(1, 10, 0, 0, 0);
      setupPosNeg(2, 10, 0, 0, 0);

      scan(files);

      assertCantDelete(0);
      assertCantDelete(1);
      assertCantDelete(2);
   }

   // Can-can-cant

   @Test
   public void testThreeFiles22() throws Exception {
      setup(3);

      setupPosNeg(0, 10, 10, 0, 0);
      setupPosNeg(1, 10, 0, 10, 0);
      setupPosNeg(2, 10, 0, 0, 0);

      scan(files);

      assertCanDelete(0);
      assertCanDelete(1);
      assertCantDelete(2);
   }

   @Test
   public void testThreeFiles23() throws Exception {
      setup(3);

      setupPosNeg(0, 10, 10, 0, 0);
      setupPosNeg(1, 10, 0, 10, 0);
      setupPosNeg(2, 10, 3, 0, 0);

      scan(files);

      assertCanDelete(0);
      assertCanDelete(1);
      assertCantDelete(2);
   }

   @Test
   public void testThreeFiles24() throws Exception {
      setup(3);

      setupPosNeg(0, 10, 7, 0, 0);
      setupPosNeg(1, 10, 3, 10, 0);
      setupPosNeg(2, 10, 3, 0, 0);

      scan(files);

      assertCanDelete(0);
      assertCanDelete(1);
      assertCantDelete(2);
   }

   @Test
   public void testThreeFiles25() throws Exception {
      setup(3);

      setupPosNeg(0, 10, 7, 0, 0);
      setupPosNeg(1, 0, 3, 10, 0);
      setupPosNeg(2, 10, 3, 0, 0);

      scan(files);

      assertCanDelete(0);
      assertCanDelete(1);
      assertCantDelete(2);
   }

   @Test
   public void testThreeFiles26() throws Exception {
      setup(3);

      setupPosNeg(0, 10, 7, 0, 0);
      setupPosNeg(1, 0, 3, 10, 0);
      setupPosNeg(2, 10, 0, 0, 0);

      scan(files);

      assertCanDelete(0);
      assertCanDelete(1);
      assertCantDelete(2);
   }

   // Can-cant-cant

   @Test
   public void testThreeFiles27() throws Exception {
      setup(3);

      setupPosNeg(0, 10, 10, 0, 0);
      setupPosNeg(1, 10, 0, 0, 0);
      setupPosNeg(2, 10, 0, 0, 0);

      scan(files);

      assertCanDelete(0);
      assertCantDelete(1);
      assertCantDelete(2);
   }

   @Test
   public void testThreeFiles28() throws Exception {
      setup(3);

      setupPosNeg(0, 10, 10, 0, 0);
      setupPosNeg(1, 10, 0, 3, 0);
      setupPosNeg(2, 10, 0, 0, 5);

      scan(files);

      assertCanDelete(0);
      assertCantDelete(1);
      assertCantDelete(2);
   }

   @Test
   public void testThreeFiles29() throws Exception {
      setup(3);

      setupPosNeg(0, 10, 10, 0, 0);
      setupPosNeg(1, 10, 0, 3, 0);
      setupPosNeg(2, 10, 0, 6, 5);

      scan(files);

      assertCanDelete(0);
      assertCantDelete(1);
      assertCantDelete(2);
   }

   @Test
   public void testThreeFiles30() throws Exception {
      setup(3);

      setupPosNeg(0, 10, 10, 0, 0);
      setupPosNeg(1, 10, 0, 3, 0);
      setupPosNeg(2, 0, 0, 6, 0);

      scan(files);

      assertCanDelete(0);
      assertCantDelete(1);
      assertCantDelete(2);
   }

   // Private
   // ------------------------------------------------------------------------

   private void setup(final int numFiles) {
      files = new JournalFile[numFiles];

      for (int i = 0; i < numFiles; i++) {
         files[i] = new MockJournalFile();
      }
   }

   private void setupPosNeg(final int fileNumber, final int pos, final int... neg) {
      JournalFile file = files[fileNumber];

      int totalDep = file.getTotalNegativeToOthers();

      for (int i = 0; i < pos; i++) {
         file.incPosCount();
      }

      for (int i = 0; i < neg.length; i++) {
         JournalFile reclaimable2 = files[i];

         for (int j = 0; j < neg[i]; j++) {
            file.incNegCount(reclaimable2);
            totalDep++;
         }
      }

      assertEquals(totalDep, file.getTotalNegativeToOthers());
   }

   private void assertCanDelete(final int... fileNumber) {
      for (int num : fileNumber) {
         assertTrue(files[num].isCanReclaim());
      }
   }

   private void assertCantDelete(final int... fileNumber) {
      for (int num : fileNumber) {
         assertFalse(files[num].isCanReclaim());
      }
   }

   static final class MockJournalFile implements JournalFile {

      private final Map<JournalFile, Integer> negCounts = new HashMap<>();

      private int posCount;

      private int totalDep;

      private boolean posReclaimCriteria;
      private boolean negReclaimCriteria;

      @Override
      public SequentialFile getFile() {
         return null;
      }

      @Override
      public long getFileID() {
         return 0;
      }

      @Override
      public int getNegCount(final JournalFile file) {
         Integer count = negCounts.get(file);

         if (count != null) {
            return count.intValue();
         } else {
            return 0;
         }
      }

      @Override
      public void fileRemoved(JournalFile fileRemoved) {
      }

      @Override
      public int getReplaceableCount() {
         return 0;
      }

      @Override
      public void incReplaceableCount() {

      }

      @Override
      public void incAddRecord() {

      }

      @Override
      public int getAddRecord() {
         return 0;
      }

      @Override
      public void incNegCount(final JournalFile file) {
         incNegCount(file, 1);
      }

      @Override
      public void incNegCount(JournalFile file, int delta) {
         Integer count = negCounts.get(file);

         int c = count == null ? delta : count.intValue() + delta;

         negCounts.put(file, c);

         totalDep += delta;
      }

      @Override
      public int getPosCount() {
         return posCount;
      }

      @Override
      public void incPosCount() {
         posCount++;
      }

      @Override
      public void decPosCount() {
         posCount--;
      }

      @Override
      public boolean isPosReclaimCriteria() {
         return posReclaimCriteria;
      }

      @Override
      public void setPosReclaimCriteria() {
         this.posReclaimCriteria = true;
      }

      @Override
      public boolean isNegReclaimCriteria() {
         return negReclaimCriteria;
      }

      @Override
      public void setNegReclaimCriteria() {
         this.negReclaimCriteria = true;
      }

      @Override
      public boolean isCanReclaim() {
         return posReclaimCriteria && negReclaimCriteria;
      }

      @Override
      public void addSize(final int bytes) {
      }

      @Override
      public void decSize(final int bytes) {
      }

      @Override
      public int getLiveSize() {
         return 0;
      }

      @Override
      public int getRecordID() {
         return 0;
      }

      @Override
      public int getTotalNegativeToOthers() {
         return totalDep;
      }

      @Override
      public int getJournalVersion() {
         return JournalImpl.FORMAT_VERSION;
      }

   }
}
