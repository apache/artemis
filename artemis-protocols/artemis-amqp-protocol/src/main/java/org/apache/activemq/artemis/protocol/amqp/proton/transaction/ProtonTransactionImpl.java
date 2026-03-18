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
package org.apache.activemq.artemis.protocol.amqp.proton.transaction;

import javax.transaction.xa.Xid;
import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.AckReason;
import org.apache.activemq.artemis.core.server.impl.RefsOperation;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.core.transaction.TransactionOperationAbstract;
import org.apache.activemq.artemis.core.transaction.impl.TransactionImpl;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPConnectionContext;
import org.apache.activemq.artemis.protocol.amqp.proton.ProtonServerSenderContext;
import org.apache.qpid.proton.engine.Delivery;

/**
 * AMQP Protocol has different TX Rollback behaviour for Acks depending on whether an AMQP delivery has been settled or
 * not.  This class extends the Core TransactionImpl used for normal TX behaviour.  In the case where deliveries have
 * been settled, normal Ack rollback is applied.  For cases where deliveries are unsettled and rolled back, we increment
 * the delivery count and return to the consumer.
 */
public class ProtonTransactionImpl extends TransactionImpl {

   /**
    * We need to track the Message reference against the AMQP objects, so we can check whether the corresponding
    * deliveries have been settled.  We also need to ensure we are settling on the correct link.  Hence why we keep a
    * ref to the ProtonServerSenderContext here.
    */
   final Map<MessageReference, Pair<Delivery, ProtonServerSenderContext>> deliveries = new HashMap<>();

   private boolean discharged;

   private static class TXOperations extends TransactionOperationAbstract {
      final ProtonTransactionImpl protonTransaction;
      final AMQPConnectionContext connection;

      TXOperations(AMQPConnectionContext connection, ProtonTransactionImpl tx) {
         this.protonTransaction = tx;
         this.connection = connection;
      }

      @Override
      public void afterCommit(Transaction tx) {
         super.afterCommit(tx);
         connection.runNow(() -> {
            // Settle all unsettled deliveries if commit is successful
            for (Pair<Delivery, ProtonServerSenderContext> p : protonTransaction.deliveries.values()) {
               if (!p.getA().isSettled()) {
                  p.getB().settle(p.getA());
               }
            }
            connection.flush();
            protonTransaction.deliveries.forEach((a, b) -> b.getA().setContext(null));
            protonTransaction.deliveries.clear();
         });
      }
   }

   public ProtonTransactionImpl(final Xid xid, final StorageManager storageManager, final int timeoutSeconds, final AMQPConnectionContext connection) {
      super(xid, storageManager, timeoutSeconds);
      addOperation(new TXOperations(connection, this));
   }

   @Override
   public RefsOperation createRefsOperation(Queue queue, AckReason reason) {
      return new ProtonTransactionRefsOperation(queue, reason, storageManager);
   }

   @Override
   public void rollback() throws Exception {
      super.rollback();
   }

   public void addDelivery(Delivery delivery, ProtonServerSenderContext context) {
      deliveries.put(((MessageReference) delivery.getContext()), new Pair<>(delivery, context));
   }

   public Map<MessageReference, Pair<Delivery, ProtonServerSenderContext>> getDeliveries() {
      return deliveries;
   }

   @Override
   public void commit() throws Exception {
      super.commit();
   }

   public boolean isDischarged() {
      return discharged;
   }

   public void discharge() {
      discharged = true;
   }
}


