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
package org.apache.activemq.artemis.core.journal.impl;

import java.util.Objects;

/**
 * This holds the relationship a record has with other files in regard to reference counting. Note: This class used to
 * be called PosFiles
 * <p>
 * Used on the ref-count for reclaiming
 */
public class JournalRecord {

   // use a very small size to account for near empty cases
   private static int INITIAL_FILES_CAPACITY = 5;
   private final JournalFile addFile;
   private final int size;

   // use this singleton to save using a separated boolean field to mark the "deleted" state
   // that would enlarge JournalRecord of several bytes
   private static final ObjIntIntArrayList<JournalFile> DELETED = new ObjIntIntArrayList<>(0);
   private ObjIntIntArrayList<JournalFile> fileUpdates;

   public JournalRecord(final JournalFile addFile, final int size) {
      this.addFile = Objects.requireNonNull(addFile);

      this.size = size;

      addFile.incPosCount();

      addFile.addSize(size);

      addFile.incAddRecord();
   }

   void addUpdateFile(final JournalFile updateFile, final int bytes, boolean replaceableUpdate) {
      checkNotDeleted();
      if (bytes == 0) {
         return;
      }
      if (fileUpdates == null) {
         fileUpdates = new ObjIntIntArrayList<>(INITIAL_FILES_CAPACITY);
      }
      final int files = fileUpdates.size();
      if (files > 0) {
         final int lastIndex = files - 1;
         if (fileUpdates.addToIntsIfMatch(lastIndex, updateFile, bytes, 1)) {
            updateFile.incPosCount();
            updateFile.addSize(bytes);
            return;
         }
      }
      fileUpdates.add(updateFile, bytes, 1);
      updateFile.incPosCount();
      updateFile.addSize(bytes);
      if (replaceableUpdate) {
         updateFile.incReplaceableCount();
      }
   }

   void delete(final JournalFile file) {
      checkNotDeleted();
      final ObjIntIntArrayList<JournalFile> fileUpdates = this.fileUpdates;
      try {
         file.incNegCount(addFile);
         addFile.decSize(size);
         if (fileUpdates != null) {
            // not-capturing lambda to save allocation
            fileUpdates.forEach((updFile, bytes, posCount, f) -> {
               f.incNegCount(updFile, posCount);
               updFile.decSize(bytes);
            }, file);
         }
      } finally {
         if (fileUpdates != null) {
            fileUpdates.clear();
            this.fileUpdates = DELETED;
         }
      }
   }

   @Override
   public String toString() {
      StringBuilder buffer = new StringBuilder();
      buffer.append("JournalRecord(add=" + addFile.getFile().getFileName());

      final ObjIntIntArrayList<JournalFile> fileUpdates = this.fileUpdates;
      if (fileUpdates != null) {
         if (fileUpdates == DELETED) {
            buffer.append(", deleted");
         } else {
            fileUpdates.forEach((file, ignoredA, ignoredB, builder) -> builder.append(", update=").append(file.getFile().getFileName()), buffer);
         }
      }
      buffer.append(")");

      return buffer.toString();
   }

   private void checkNotDeleted() {
      if (fileUpdates == DELETED) {
         throw new IllegalStateException("the record is already deleted");
      }
   }

}
