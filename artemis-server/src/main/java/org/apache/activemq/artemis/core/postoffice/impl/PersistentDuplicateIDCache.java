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
package org.apache.activemq.artemis.core.postoffice.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntFunction;

import org.apache.activemq.artemis.api.core.ActiveMQDuplicateIdException;
import org.apache.activemq.artemis.api.core.ObjLongPair;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.postoffice.DuplicateIDCache;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.core.transaction.TransactionOperationAbstract;
import org.apache.activemq.artemis.utils.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

import static org.apache.activemq.artemis.api.core.ObjLongPair.NIL;
import static org.apache.activemq.artemis.core.postoffice.impl.IntegerCache.boxedInts;

/**
 * {@link InMemoryDuplicateIDCache} and {@link PersistentDuplicateIDCache} impls have been separated for performance and
 * memory footprint reasons.
 * <p>
 * Instead of using a single {@link DuplicateIDCache} impl, we've let 2 different impls to contain just the bare minimum
 * data in order to have 2 different memory footprint costs at runtime, while making easier to track dependencies eg
 * in-memory cache won't need any {@link StorageManager} because no storage operations are expected to happen.
 */
final class PersistentDuplicateIDCache implements DuplicateIDCache {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final Map<ByteArray, Integer> cache = new ConcurrentHashMap<>();

   private final SimpleString address;

   private final List<ObjLongPair<ByteArray>> ids;

   private final IntFunction<Integer> cachedBoxedInts;

   private int pos;

   private final int cacheSize;

   private final StorageManager storageManager;

   PersistentDuplicateIDCache(final SimpleString address, final int size, final StorageManager storageManager) {
      this.address = address;

      cacheSize = size;

      ids = new ArrayList<>(size);

      cachedBoxedInts = boxedInts(size);

      this.storageManager = storageManager;
   }

   @Override
   public synchronized void load(final List<Pair<byte[], Long>> ids) throws Exception {
      if (!cache.isEmpty()) {
         throw new IllegalStateException("load is valid only on empty cache");
      }
      // load only ids that fit this cache:
      // - in term of remaining capacity
      // - ignoring (and reporting) ids unpaired with record ID
      // Then, delete the exceeding ones.

      long txID = -1;

      int toNotBeAdded = ids.size() - cacheSize;
      if (toNotBeAdded < 0) {
         toNotBeAdded = 0;
      }

      for (Pair<byte[], Long> id : ids) {
         if (id.getB() == null) {
            if (logger.isTraceEnabled()) {
               logger.trace("ignoring id = {} because without record ID", describeID(id.getA()));
            }
            if (toNotBeAdded > 0) {
               toNotBeAdded--;
            }
            continue;
         }
         assert id.getB() != null && id.getB().longValue() != NIL;
         if (toNotBeAdded > 0) {
            if (txID == -1) {
               txID = storageManager.generateID();
            }
            if (logger.isTraceEnabled()) {
               logger.trace("deleting id = {}", describeID(id.getA(), id.getB()));
            }

            storageManager.deleteDuplicateIDTransactional(txID, id.getB());
            toNotBeAdded--;
         } else {
            ByteArray bah = new ByteArray(id.getA());

            ObjLongPair<ByteArray> pair = new ObjLongPair<>(bah, id.getB());

            cache.put(bah, cachedBoxedInts.apply(this.ids.size()));

            this.ids.add(pair);
            if (logger.isTraceEnabled()) {
               logger.trace("loading id = {}", describeID(id.getA(), id.getB()));
            }
         }

      }

      if (txID != -1) {
         storageManager.commit(txID);
      }

      pos = this.ids.size();

      if (pos == cacheSize) {
         pos = 0;
      }

   }

   @Override
   public boolean deleteFromCache(byte[] duplicateID) throws Exception {
      return deleteFromCache(new ByteArray(duplicateID));
   }

   private boolean deleteFromCache(final ByteArray duplicateID) throws Exception {
      if (logger.isTraceEnabled()) {
         logger.trace("deleting id = {}", describeID(duplicateID.bytes));
      }

      final Integer posUsed = cache.remove(duplicateID);

      if (posUsed != null) {
         synchronized (this) {
            final ObjLongPair<ByteArray> id = ids.get(posUsed.intValue());

            if (id.getA().equals(duplicateID)) {
               final long recordID = id.getB();
               id.setA(null);
               id.setB(NIL);
               if (logger.isTraceEnabled()) {
                  logger.trace("address = {} deleting id = {}", address, describeID(duplicateID.bytes, id.getB()));
               }
               storageManager.deleteDuplicateID(recordID);
            }
         }
         return true;
      }

      return false;
   }

   private static String describeID(byte[] duplicateID) {
      return ByteUtil.bytesToHex(duplicateID, 4) + ", simpleString=" + ByteUtil.toSimpleString(duplicateID);
   }

   private static String describeID(byte[] duplicateID, long id) {
      return ByteUtil.bytesToHex(duplicateID, 4) + ", simpleString=" + ByteUtil.toSimpleString(duplicateID) + ", id=" + id;
   }

   @Override
   public boolean contains(final byte[] duplID) {
      return contains(new ByteArray(duplID));
   }

   private boolean contains(final ByteArray duplID) {
      final boolean contains = cache.containsKey(duplID);
      if (contains) {
         if (logger.isTraceEnabled()) {
            logger.trace("address = {} found a duplicate {}", address, describeID(duplID.bytes));
         }
      }

      return contains;
   }

   @Override
   public void addToCache(final byte[] duplID) throws Exception {
      addToCache(duplID, null, false);
   }

   @Override
   public void addToCache(final byte[] duplID, final Transaction tx) throws Exception {
      addToCache(duplID, tx, false);
   }

   @Override
   public synchronized boolean atomicVerify(final byte[] duplID, final Transaction tx) throws Exception {
      final ByteArray holder = new ByteArray(duplID);
      if (contains(holder)) {
         if (tx != null) {
            tx.markAsRollbackOnly(new ActiveMQDuplicateIdException());
         }
         return false;
      }
      addToCache(holder, tx, true);
      return true;
   }

   @Override
   public synchronized void addToCache(final byte[] duplID, final Transaction tx, boolean instantAdd) throws Exception {
      addToCache(new ByteArray(duplID), tx, instantAdd);
   }

   private synchronized void addToCache(final ByteArray holder,
                                        final Transaction tx,
                                        boolean instantAdd) throws Exception {
      final long recordID = storageManager.generateID();
      if (tx == null) {
         storageManager.storeDuplicateID(address, holder.bytes, recordID);

         addToCacheInMemory(holder, recordID);
      } else {
         storageManager.storeDuplicateIDTransactional(tx.getID(), address, holder.bytes, recordID);

         tx.setContainsPersistent();

         if (logger.isTraceEnabled()) {
            logger.trace("address = {} adding duplicateID TX operation for {}, tx = {}", address,
                          describeID(holder.bytes, recordID), tx);
         }

         if (instantAdd) {
            addToCacheInMemory(holder, recordID);
            tx.addOperation(new AddDuplicateIDOperation(holder, recordID, false));
         } else {
            // For a tx, it's important that the entry is not added to the cache until commit
            // since if the client fails then resends them tx we don't want it to get rejected
            tx.afterStore(new AddDuplicateIDOperation(holder, recordID, true));
         }
      }
   }

   @Override
   public void load(final Transaction tx, final byte[] duplID) {
      tx.addOperation(new AddDuplicateIDOperation(new ByteArray(duplID), tx.getID(), true));
   }

   private synchronized void addToCacheInMemory(final ByteArray holder, final long recordID) {
      Objects.requireNonNull(holder, "holder must be not null");
      if (recordID < 0) {
         throw new IllegalArgumentException("recordID must be >= 0");
      }
      if (logger.isTraceEnabled()) {
         logger.trace("address = {} adding {}", address, describeID(holder.bytes, recordID));
      }

      cache.put(holder, cachedBoxedInts.apply(pos));

      ObjLongPair<ByteArray> id;

      if (pos < ids.size()) {
         // Need fast array style access here -hence ArrayList typing
         id = ids.get(pos);

         // The id here might be null if it was explicit deleted
         if (id.getA() != null) {
            if (logger.isTraceEnabled()) {
               logger.trace("address = {} removing excess duplicateDetection {}", address, describeID(id.getA().bytes, id.getB()));
            }

            cache.remove(id.getA());

            assert id.getB() != NIL;
            try {
               storageManager.deleteDuplicateID(id.getB());
            } catch (Exception e) {
               ActiveMQServerLogger.LOGGER.errorDeletingDuplicateCache(e);
            }
         }

         id.setA(holder);

         id.setB(recordID);

         if (logger.isTraceEnabled()) {
            logger.trace("address = {} replacing old duplicateID by {}", address, describeID(id.getA().bytes, id.getB()));
         }

      } else {
         id = new ObjLongPair<>(holder, recordID);

         if (logger.isTraceEnabled()) {
            logger.trace("address = {} adding new duplicateID {}", address, describeID(id.getA().bytes, id.getB()));
         }

         ids.add(id);

      }

      if (pos++ == cacheSize - 1) {
         pos = 0;
      }
   }

   @Override
   public synchronized void clear() throws Exception {
      logger.debug("address = {} removing duplicate ID data", address);
      final int idsSize = ids.size();
      if (idsSize > 0) {
         boolean deleted = false;
         long tx = storageManager.generateID();
         for (int i = 0; i < idsSize; i++) {
            final ObjLongPair<ByteArray> id = ids.get(i);
            if (id.getA() != null) {
               assert id.getB() != NIL;
               storageManager.deleteDuplicateIDTransactional(tx, id.getB());
               deleted = true;
            }
         }
         if (deleted) {
            storageManager.commit(tx);
         }
      }

      ids.clear();
      cache.clear();
      pos = 0;
   }

   @Override
   public synchronized List<Pair<byte[], Long>> getMap() {
      final int idsSize = ids.size();
      List<Pair<byte[], Long>> copy = new ArrayList<>(idsSize);
      for (int i = 0; i < idsSize; i++) {
         final ObjLongPair<ByteArray> id = ids.get(i);
         // in case the pair has been removed
         if (id.getA() != null) {
            assert id.getB() != NIL;
            copy.add(new Pair<>(id.getA().bytes, id.getB()));
         }
      }
      return copy;
   }

   private final class AddDuplicateIDOperation extends TransactionOperationAbstract {

      final ByteArray holder;

      final long recordID;

      volatile boolean done;

      private final boolean afterCommit;

      AddDuplicateIDOperation(final ByteArray holder, final long recordID, boolean afterCommit) {
         this.holder = holder;
         this.recordID = recordID;
         this.afterCommit = afterCommit;
      }

      private void process() {
         if (!done) {
            addToCacheInMemory(holder, recordID);

            done = true;
         }
      }

      @Override
      public void afterCommit(final Transaction tx) {
         if (afterCommit) {
            process();
         }
      }

      @Override
      public void beforeRollback(Transaction tx) throws Exception {
         if (!afterCommit) {
            deleteFromCache(holder);
         }
      }

      @Override
      public List<MessageReference> getRelatedMessageReferences() {
         return null;
      }
   }

   @Override
   public int getSize() {
      return cacheSize;
   }

}
