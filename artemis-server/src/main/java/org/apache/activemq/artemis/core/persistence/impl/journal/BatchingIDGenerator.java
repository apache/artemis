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
package org.apache.activemq.artemis.core.persistence.impl.journal;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.core.journal.EncodingSupport;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.utils.DataConstants;
import org.apache.activemq.artemis.utils.IDGenerator;
import org.jboss.logging.Logger;

/**
 * An ID generator that allocates a batch of IDs of size {@link #checkpointSize} and records the ID
 * in the journal only when starting a new batch.
 *
 * @see IDGenerator
 */
public final class BatchingIDGenerator implements IDGenerator {

   private static final Logger logger = Logger.getLogger(BatchingIDGenerator.class);

   private final AtomicLong counter;

   private final long checkpointSize;

   private volatile long nextID;

   private final StorageManager storageManager;

   private List<Long> cleanupRecords = null;

   public BatchingIDGenerator(final long start, final long checkpointSize, final StorageManager storageManager) {
      counter = new AtomicLong(start);

      // as soon as you generate the first ID, the nextID should be updated
      nextID = start;

      this.checkpointSize = checkpointSize;

      this.storageManager = storageManager;
   }

   public void persistCurrentID() {
      final long recordID = counter.incrementAndGet();
      storeID(recordID, recordID);
   }

   /**
    * A method to cleanup old records after started
    */
   public void cleanup() {
      if (cleanupRecords != null) {
         Iterator<Long> iterRecord = cleanupRecords.iterator();
         while (iterRecord.hasNext()) {
            Long record = iterRecord.next();
            if (iterRecord.hasNext()) {
               // we don't want to remove the last record
               deleteID(record.longValue());
            }
         }
         cleanupRecords.clear(); // help GC
         cleanupRecords = null;
      }
   }

   public void loadState(final long journalID, final ActiveMQBuffer buffer) {
      addCleanupRecord(journalID);
      IDCounterEncoding encoding = new IDCounterEncoding();

      encoding.decode(buffer);

      // Keep nextID and counter the same, the next generateID will update the checkpoint
      nextID = encoding.id + 1;

      counter.set(nextID);
   }

   // for testcases
   public void forceNextID(long nextID) {
      long idJournal = counter.incrementAndGet();
      counter.set(nextID);
      storeID(idJournal, nextID);

   }

   @Override
   public long generateID() {
      long id = counter.getAndIncrement();

      if (id >= nextID) {
         saveCheckPoint(id);
      }
      return id;
   }

   @Override
   public long getCurrentID() {
      return counter.get();
   }

   private synchronized void saveCheckPoint(final long id) {
      if (id >= nextID) {
         nextID += checkpointSize;

         if (!storageManager.isStarted()) {
            // This could happen after the server is stopped
            // while notifications are being sent and ID generated.
            // If the ID is intended to the journal you would know soon enough
            // so we just ignore this for now
            logger.debug("The journalStorageManager is not loaded. " + "This is probably ok as long as it's a notification being sent after shutdown");
         } else {
            storeID(counter.getAndIncrement(), nextID);
         }
      }
   }

   private void addCleanupRecord(long id) {
      if (cleanupRecords == null) {
         cleanupRecords = new LinkedList<>();
      }

      cleanupRecords.add(id);
   }

   private void storeID(final long journalID, final long id) {
      try {
         storageManager.storeID(journalID, id);
      } catch (Exception e) {
         ActiveMQServerLogger.LOGGER.batchingIdError(e);
      }
   }

   private void deleteID(final long journalID) {
      try {
         storageManager.deleteID(journalID);
      } catch (Exception e) {
         ActiveMQServerLogger.LOGGER.batchingIdError(e);
      }
   }

   public static EncodingSupport createIDEncodingSupport(final long id) {
      return new IDCounterEncoding(id);
   }


   protected static final class IDCounterEncoding implements EncodingSupport {

      private long id;

      @Override
      public String toString() {
         return "IDCounterEncoding [id=" + id + "]";
      }

      private IDCounterEncoding(final long id) {
         this.id = id;
      }

      IDCounterEncoding() {
      }

      @Override
      public void decode(final ActiveMQBuffer buffer) {
         id = buffer.readLong();
      }

      @Override
      public void encode(final ActiveMQBuffer buffer) {
         buffer.writeLong(id);
      }

      @Override
      public int getEncodeSize() {
         return DataConstants.SIZE_LONG;
      }
   }
}
