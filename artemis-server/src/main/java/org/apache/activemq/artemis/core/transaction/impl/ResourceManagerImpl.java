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
package org.apache.activemq.artemis.core.transaction.impl;

import javax.transaction.xa.Xid;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.transaction.ResourceManager;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;

public class ResourceManagerImpl implements ResourceManager {

   private final ConcurrentMap<Xid, Transaction> transactions = new ConcurrentHashMap<>();

   private final List<HeuristicCompletionHolder> heuristicCompletions = new ArrayList<>();

   private final int defaultTimeoutSeconds;

   private boolean started = false;

   private TxTimeoutHandler task;

   private final long txTimeoutScanPeriod;

   private final ScheduledExecutorService scheduledThreadPool;

   private final ActiveMQServer server;

   public ResourceManagerImpl(final ActiveMQServer server,
                              final int defaultTimeoutSeconds,
                              final long txTimeoutScanPeriod,
                              final ScheduledExecutorService scheduledThreadPool) {
      this.server = server;
      this.defaultTimeoutSeconds = defaultTimeoutSeconds;
      this.txTimeoutScanPeriod = txTimeoutScanPeriod;
      this.scheduledThreadPool = scheduledThreadPool;
   }

   // ActiveMQComponent implementation

   @Override
   public int size() {
      return transactions.size();
   }

   @Override
   public void start() throws Exception {
      if (started) {
         return;
      }
      task = new TxTimeoutHandler();
      Future<?> future = scheduledThreadPool.scheduleAtFixedRate(task, txTimeoutScanPeriod, txTimeoutScanPeriod, TimeUnit.MILLISECONDS);
      task.setFuture(future);

      started = true;
   }

   @Override
   public void stop() throws Exception {
      if (!started) {
         return;
      }
      if (task != null) {
         task.close();
      }

      started = false;
   }

   @Override
   public boolean isStarted() {
      return started;
   }

   // ResourceManager implementation ---------------------------------------------

   @Override
   public Transaction getTransaction(final Xid xid) {
      return transactions.get(xid);
   }

   @Override
   public boolean putTransaction(final Xid xid, final Transaction tx, RemotingConnection remotingConnection) throws ActiveMQException {
      if (server.hasBrokerResourcePlugins()) {
         server.callBrokerResourcePlugins(plugin -> plugin.beforePutTransaction(xid, tx, remotingConnection));
      }

      boolean result = transactions.putIfAbsent(xid, tx) == null;

      if (server.hasBrokerResourcePlugins()) {
         server.callBrokerResourcePlugins(plugin -> plugin.afterPutTransaction(xid, tx, remotingConnection));
      }

      return result;
   }

   @Override
   public Transaction removeTransaction(final Xid xid, RemotingConnection remotingConnection) throws ActiveMQException {
      if (server.hasBrokerResourcePlugins()) {
         server.callBrokerResourcePlugins(plugin -> plugin.beforeRemoveTransaction(xid, remotingConnection));
      }

      Transaction transaction = transactions.remove(xid);

      if (server.hasBrokerResourcePlugins()) {
         server.callBrokerResourcePlugins(plugin -> plugin.afterRemoveTransaction(xid, remotingConnection));
      }

      return transaction;
   }

   @Override
   public int getTimeoutSeconds() {
      return defaultTimeoutSeconds;
   }

   @Override
   public List<Xid> getPreparedTransactions() {
      List<Xid> xids = new ArrayList<>();

      for (Map.Entry<Xid, Transaction> entry : transactions.entrySet()) {
         if (entry.getValue().getState() == Transaction.State.PREPARED) {
            xids.add(entry.getKey());
         }
      }
      return xids;
   }

   @Override
   public Map<Xid, Long> getPreparedTransactionsWithCreationTime() {
      Map<Xid, Long> xidsWithCreationTime = new HashMap<>();

      for (Map.Entry<Xid, Transaction> entry : transactions.entrySet()) {
         if (entry.getValue().getState() == Transaction.State.PREPARED) {
            xidsWithCreationTime.put(entry.getKey(), entry.getValue().getCreateTime());
         }
      }
      return xidsWithCreationTime;
   }

   @Override
   public void putHeuristicCompletion(final long recordID, final Xid xid, final boolean isCommit) {
      heuristicCompletions.add(new HeuristicCompletionHolder(recordID, xid, isCommit));
   }

   @Override
   public List<Xid> getHeuristicCommittedTransactions() {
      return getHeuristicCompletedTransactions(true);
   }

   @Override
   public List<Xid> getHeuristicRolledbackTransactions() {
      return getHeuristicCompletedTransactions(false);
   }

   @Override
   public long removeHeuristicCompletion(final Xid xid) {
      Iterator<HeuristicCompletionHolder> iterator = heuristicCompletions.iterator();
      while (iterator.hasNext()) {
         ResourceManagerImpl.HeuristicCompletionHolder holder = iterator.next();
         if (holder.xid.equals(xid)) {
            iterator.remove();
            return holder.recordID;
         }
      }
      return -1;
   }

   @Override
   public List<Xid> getInDoubtTransactions() {
      List<Xid> xids = new LinkedList<>();

      xids.addAll(getPreparedTransactions());
      xids.addAll(getHeuristicCommittedTransactions());
      xids.addAll(getHeuristicRolledbackTransactions());

      return xids;
   }

   private List<Xid> getHeuristicCompletedTransactions(final boolean isCommit) {
      List<Xid> xids = new ArrayList<>();
      for (HeuristicCompletionHolder holder : heuristicCompletions) {
         if (holder.isCommit == isCommit) {
            xids.add(holder.xid);
         }
      }
      return xids;
   }

   private class TxTimeoutHandler implements Runnable {

      private boolean closed = false;

      private Future<?> future;

      @Override
      public void run() {
         if (closed) {
            return;
         }

         Set<Transaction> timedoutTransactions = new HashSet<>();

         long now = System.currentTimeMillis();

         for (Transaction tx : transactions.values()) {

            if (tx.hasTimedOut(now, defaultTimeoutSeconds)) {
               Transaction removedTX = null;
               try {
                  removedTX = removeTransaction(tx.getXid(), null);
               } catch (ActiveMQException e) {
                  ActiveMQServerLogger.LOGGER.errorRemovingTX(tx.getXid(), e);
               }
               if (removedTX != null) {
                  ActiveMQServerLogger.LOGGER.timedOutXID(removedTX.getXid());
                  timedoutTransactions.add(removedTX);
               }
            }
         }

         for (Transaction failedTransaction : timedoutTransactions) {
            try {
               failedTransaction.rollback();
            } catch (Exception e) {
               ActiveMQServerLogger.LOGGER.errorTimingOutTX(failedTransaction.getXid(), e);
            }
         }
      }

      synchronized void setFuture(final Future<?> future) {
         this.future = future;
      }

      void close() {
         if (future != null) {
            future.cancel(false);
         }

         closed = true;
      }

   }

   private static final class HeuristicCompletionHolder {

      public final boolean isCommit;

      public final Xid xid;

      public final long recordID;

      private HeuristicCompletionHolder(final long recordID, final Xid xid, final boolean isCommit) {
         this.recordID = recordID;
         this.xid = xid;
         this.isCommit = isCommit;
      }
   }
}
