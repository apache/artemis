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
package org.apache.activemq.artemis.core.persistence.impl.journal.codec;

import org.apache.activemq.artemis.core.paging.PageTransactionInfo;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.core.transaction.TransactionOperation;
import org.apache.activemq.artemis.core.transaction.TransactionOperationAbstract;
import org.apache.activemq.artemis.core.transaction.TransactionPropertyIndexes;

/**
 * This is only used when loading a transaction.
 * <p>
 * it might be possible to merge the functionality of this class with {@link FinishPageMessageOperation}
 */
// TODO: merge this class with the one on the PagingStoreImpl
public class FinishPageMessageOperation extends TransactionOperationAbstract implements TransactionOperation {

   @Override
   public void afterCommit(final Transaction tx) {
      // If part of the transaction goes to the queue, and part goes to paging, we can't let depage start for the
      // transaction until all the messages were added to the queue
      // or else we could deliver the messages out of order

      PageTransactionInfo pageTransaction = (PageTransactionInfo) tx.getProperty(TransactionPropertyIndexes.PAGE_TRANSACTION);

      if (pageTransaction != null) {
         pageTransaction.commit();
      }
   }

   @Override
   public void afterRollback(final Transaction tx) {
      PageTransactionInfo pageTransaction = (PageTransactionInfo) tx.getProperty(TransactionPropertyIndexes.PAGE_TRANSACTION);

      if (tx.getState() == Transaction.State.PREPARED && pageTransaction != null) {
         pageTransaction.rollback();
      }
   }
}
