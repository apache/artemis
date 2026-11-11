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

import org.apache.activemq.artemis.core.io.SequentialFile;

public interface JournalFile {

   default boolean isReclaimable() {
      return true;
   }

   default void setReclaimable(boolean reclaimable) {
   }

   int getNegCount(JournalFile file);

   void incNegCount(JournalFile file);

   void incNegCount(JournalFile file, int delta);

   int getPosCount();

   void incPosCount();

   void decPosCount();

   int getReplaceableCount();

   void incReplaceableCount();

   void incAddRecord();

   int getAddRecord();

   void addSize(int bytes);

   void decSize(int bytes);

   int getLiveSize();

   /**
    * The total number of deletes this file has
    */
   int getTotalNegativeToOthers();

   /**
    * Callback for when a file is removed. to cleanup negatives and avoid leaks.
    */
   void fileRemoved(JournalFile fileRemoved);

   /**
    * Whether this file additions all have a delete in some other file
    */
   boolean isPosReclaimCriteria();

   void setPosReclaimCriteria();

   /**
    * Whether this file deletes are on files that are either marked for reclaim or have already been reclaimed
    */
   boolean isNegReclaimCriteria();

   void setNegReclaimCriteria();

   /**
    * Whether this file's contents can deleted and the file reused.
    *
    * @return {@code true} if the file can already be deleted
    */
   boolean isCanReclaim();

   /**
    * This is a field to identify that records on this file actually belong to the current file. The possible
    * implementation for this is fileID &amp; Integer.MAX_VALUE
    */
   int getRecordID();

   long getFileID();

   int getJournalVersion();

   SequentialFile getFile();
}
