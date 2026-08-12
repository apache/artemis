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
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntFunction;

import org.apache.activemq.artemis.api.core.ActiveMQDuplicateIdException;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.postoffice.DuplicateIDCache;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.core.transaction.TransactionOperationAbstract;
import org.apache.activemq.artemis.utils.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

import static org.apache.activemq.artemis.core.postoffice.impl.IntegerCache.boxedInts;

/**
 * {@link InMemoryDuplicateIDCache} and {@link PersistentDuplicateIDCache} impls have been separated for performance and
 * memory footprint reasons.
 * <p>
 * Instead of using a single {@link DuplicateIDCache} impl, we've let 2 different impls to contain just the bare minimum
 * data in order to have 2 different memory footprint costs at runtime, while making easier to track dependencies eg
 * in-memory cache won't need any {@link StorageManager} because no storage operations are expected to happen.
 */
final class InMemoryDuplicateIDCache implements DuplicateIDCache {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final Map<ByteArray, Integer> cache = new ConcurrentHashMap<>();

   private final SimpleString address;

   private final List<ByteArray> ids;

   private final IntFunction<Integer> cachedBoxedInts;

   private int pos;

   private final int cacheSize;

   InMemoryDuplicateIDCache(final SimpleString address, final int size) {
      this.address = address;

      cacheSize = size;

      ids = new ArrayList<>(size);

      cachedBoxedInts = boxedInts(size);
   }

   @Override
   public void load(List<Pair<byte[], Long>> ids) throws Exception {
      logger.debug("address = {} ignore loading ids: in memory cache won't load previously stored ids", address);
   }

   @Override
   public boolean deleteFromCache(byte[] duplicateID) {
      return deleteFromCache(new ByteArray(duplicateID));
   }

   private boolean deleteFromCache(final ByteArray duplicateID) {
      if (logger.isTraceEnabled()) {
         logger.trace("deleting id = {}", describeID(duplicateID.bytes));
      }

      Integer posUsed = cache.remove(duplicateID);

      if (posUsed != null) {
         ByteArray id;

         synchronized (this) {
            final int index = posUsed.intValue();
            id = ids.get(index);

            if (id.equals(duplicateID)) {
               ids.set(index, null);
               if (logger.isTraceEnabled()) {
                  logger.trace("address = {} deleting id={}", address, describeID(duplicateID.bytes));
               }
            }
         }
         return true;
      }

      return false;
   }

   private static String describeID(byte[] duplicateID) {
      return ByteUtil.bytesToHex(duplicateID, 4) + ", simpleString=" + ByteUtil.toSimpleString(duplicateID);
   }

   @Override
   public boolean contains(final byte[] duplID) {
      return contains(new ByteArray(duplID));
   }

   private boolean contains(final ByteArray id) {
      boolean contains = cache.containsKey(id);

      if (logger.isTraceEnabled()) {
         if (contains) {
            logger.trace("address = {} found a duplicate {}", address, describeID(id.bytes));
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
   public synchronized boolean atomicVerify(final byte[] duplID, final Transaction tx) {
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

   private synchronized void addToCache(final ByteArray holder, final Transaction tx, boolean instantAdd) {
      if (tx == null) {
         addToCacheInMemory(holder);
      } else {
         if (logger.isTraceEnabled()) {
            logger.trace("address = {} adding duplicateID TX operation for {}, tx = {}", address, describeID(holder.bytes), tx);
         }

         if (instantAdd) {
            addToCacheInMemory(holder);
            tx.addOperation(new AddDuplicateIDOperation(holder, false));
         } else {
            // For a tx, it's important that the entry is not added to the cache until commit
            // since if the client fails then resends them tx we don't want it to get rejected
            tx.afterStore(new AddDuplicateIDOperation(holder, true));
         }
      }
   }

   @Override
   public void load(final Transaction tx, final byte[] duplID) {
      tx.addOperation(new AddDuplicateIDOperation(new ByteArray(duplID), true));
   }

   private synchronized void addToCacheInMemory(final ByteArray holder) {
      if (logger.isTraceEnabled()) {
         logger.trace("address = {} adding {}", address, describeID(holder.bytes));
      }

      cache.put(holder, cachedBoxedInts.apply(pos));

      if (pos < ids.size()) {
         // Need fast array style access here -hence ArrayList typing
         final ByteArray id = ids.set(pos, holder);

         // The id here might be null if it was explicit deleted
         if (id != null) {
            if (logger.isTraceEnabled()) {
               logger.trace("address = {} removing excess duplicateDetection {}", address, describeID(id.bytes));
            }

            cache.remove(id);
         }

         if (logger.isTraceEnabled()) {
            logger.trace("address = {} replacing old duplicateID by {}", address, describeID(holder.bytes));
         }

      } else {
         if (logger.isTraceEnabled()) {
            logger.trace("address = {} adding new duplicateID {}", address, describeID(holder.bytes));
         }

         ids.add(holder);
      }

      if (pos++ == cacheSize - 1) {
         pos = 0;
      }
   }

   @Override
   public synchronized void clear() throws Exception {
      logger.debug("address = {} removing duplicate ID data", address);

      ids.clear();
      cache.clear();
      pos = 0;
   }

   @Override
   public synchronized List<Pair<byte[], Long>> getMap() {
      final int idsSize = ids.size();
      List<Pair<byte[], Long>> copy = new ArrayList<>(idsSize);
      for (int i = 0; i < idsSize; i++) {
         final ByteArray id = ids.get(i);
         // in case the id has been removed
         if (id != null) {
            copy.add(new Pair<>(id.bytes, null));
         }
      }
      return copy;
   }

   private final class AddDuplicateIDOperation extends TransactionOperationAbstract {

      final ByteArray id;

      volatile boolean done;

      private final boolean afterCommit;

      AddDuplicateIDOperation(final ByteArray id, boolean afterCommit) {
         this.id = id;
         this.afterCommit = afterCommit;
      }

      private void process() {
         if (!done) {
            addToCacheInMemory(id);

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
            deleteFromCache(id);
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
