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

import java.lang.invoke.MethodHandles;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import org.apache.activemq.artemis.core.io.SequentialFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JournalFileImpl implements JournalFile {

   private final SequentialFile file;

   private final long fileID;

   private final int recordID;

   private long offset;

   boolean reclaimable = true;

   @Override
   public void setReclaimable(boolean reclaimable) {
      this.reclaimable = reclaimable;
   }

   private static final AtomicIntegerFieldUpdater<JournalFileImpl> replaceableCountUpdater = AtomicIntegerFieldUpdater.newUpdater(JournalFileImpl.class, "replaceableCountField");
   private static final AtomicIntegerFieldUpdater<JournalFileImpl> posCountUpdater = AtomicIntegerFieldUpdater.newUpdater(JournalFileImpl.class, "posCountField");
   private static final AtomicIntegerFieldUpdater<JournalFileImpl> addRecordUpdate = AtomicIntegerFieldUpdater.newUpdater(JournalFileImpl.class, "addRecordField");
   private static final AtomicIntegerFieldUpdater<JournalFileImpl> liveBytesUpdater = AtomicIntegerFieldUpdater.newUpdater(JournalFileImpl.class, "liveBytesField");

   private volatile int replaceableCountField = 0;
   private volatile int posCountField = 0;
   private volatile int addRecordField = 0;
   private volatile int liveBytesField = 0;

   // Flags to be used by determine if the journal file can be reclaimed
   private boolean posReclaimCriteria = false;
   private boolean negReclaimCriteria = false;

   private final AtomicInteger totalNegativeToOthers = new AtomicInteger(0);

   private final int version;

   private final ConcurrentMap<JournalFile, AtomicInteger> negCounts = new ConcurrentHashMap<>();

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public JournalFileImpl(final SequentialFile file, final long fileID, final int version) {
      this.file = Objects.requireNonNull(file);

      this.fileID = fileID;

      this.version = version;

      recordID = (int) (fileID & Integer.MAX_VALUE);
   }

   @Override
   public int getPosCount() {
      return posCountUpdater.get(this);
   }

   @Override
   public int getReplaceableCount() {
      return replaceableCountUpdater.get(this);
   }

   @Override
   public void incReplaceableCount() {
      replaceableCountUpdater.incrementAndGet(this);
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
      return reclaimable && posReclaimCriteria && negReclaimCriteria;
   }

   @Override
   public void incNegCount(final JournalFile file) {
      incNegCount(file, 1);
   }

   @Override
   public void incNegCount(final JournalFile file, int delta) {
      if (delta <= 0) {
         throw new IllegalArgumentException("delta must be > 0");
      }
      if (file != this) {
         totalNegativeToOthers.addAndGet(delta);
      }
      // GC-free path: including capturing lambdas
      AtomicInteger previous = negCounts.get(file);
      if (previous != null) {
         previous.addAndGet(delta);
         return;
      }
      // no counter yet: slow path, allocating
      previous = negCounts.putIfAbsent(file, new AtomicInteger(delta));
      // racy attempt to create the counter
      if (previous != null) {
         previous.addAndGet(delta);
      }
   }

   @Override
   public int getNegCount(final JournalFile file) {
      AtomicInteger count = negCounts.get(file);

      if (count == null) {
         return 0;
      } else {
         return count.intValue();
      }
   }

   @Override
   public int getJournalVersion() {
      return version;
   }

   @Override
   public void incPosCount() {
      posCountUpdater.incrementAndGet(this);
   }

   @Override
   public void incAddRecord() {
      addRecordUpdate.incrementAndGet(this);
   }

   @Override
   public int getAddRecord() {
      return addRecordUpdate.get(this);
   }

   @Override
   public void decPosCount() {
      posCountUpdater.decrementAndGet(this);
   }

   public long getOffset() {
      return offset;
   }

   @Override
   public long getFileID() {
      return fileID;
   }

   @Override
   public int getRecordID() {
      return recordID;
   }

   public void setOffset(final long offset) {
      this.offset = offset;
   }

   @Override
   public SequentialFile getFile() {
      return file;
   }

   @Override
   public String toString() {
      try {
         return "JournalFileImpl: (" + file.getFileName() + " id = " + fileID + ", recordID = " + recordID + ")";
      } catch (Exception e) {
         logger.warn("Error during method invocation", e.getMessage(), e);
         return "Error:" + e.toString();
      }
   }

   /**
    * Receive debug information about the journal
    */
   public String debug() {
      StringBuilder builder = new StringBuilder();

      for (Entry<JournalFile, AtomicInteger> entry : negCounts.entrySet()) {
         builder.append(" file = " + entry.getKey() + " negcount value = " + entry.getValue() + "\n");
      }

      return builder.toString();
   }

   @Override
   public void addSize(final int bytes) {
      liveBytesUpdater.addAndGet(this, bytes);
   }

   @Override
   public void decSize(final int bytes) {
      liveBytesUpdater.addAndGet(this, -bytes);
   }

   @Override
   public int getLiveSize() {
      return liveBytesUpdater.get(this);
   }

   @Override
   public int getTotalNegativeToOthers() {
      return totalNegativeToOthers.get();
   }

   @Override
   public void fileRemoved(JournalFile fileRemoved) {
      negCounts.remove(fileRemoved);
   }

}
