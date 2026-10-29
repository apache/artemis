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
package org.apache.activemq.artemis.core.transaction;

import javax.transaction.xa.Xid;
import java.util.List;
import java.util.function.Supplier;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.core.journal.StorageTX;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.AckReason;
import org.apache.activemq.artemis.core.server.impl.RefsOperation;

/**
 * An Apache Artemis internal transaction
 */
public interface Transaction {

   enum State {
      ACTIVE, PREPARED, COMMITTED, ROLLEDBACK, SUSPENDED, ROLLBACK_ONLY
   }

   StorageTX getStorageTx();

   Object getProtocolData();

   /**
    * Protocol managers can use this field to store any object needed. An example would be the Session used by the
    * transaction on openwire
    */
   void setProtocolData(Object data);

   boolean isEffective();

   void prepare() throws Exception;

   void commit() throws Exception;

   void commit(boolean onePhase) throws Exception;

   void rollback() throws Exception;

   /**
    * In a ServerSession failure scenario,\ we may try to rollback, however only if it's not prepared. In case it's
    * prepared, we will just let it be and let the transaction manager to deal with it
    */
   boolean tryRollback();

   long getID();

   Xid getXid();

   void suspend();

   void resume();

   State getState();

   void setState(State state);

   void markAsRollbackOnly(ActiveMQException exception);

   long getCreateTime();

   void addOperation(TransactionOperation sync);

   void afterWired(Runnable runnable);

   /**
    * This is an operation that will be called right after the storage is completed. addOperation could only happen
    * after paging and replication, while these operations will just be about the storage
    */
   void afterStore(TransactionOperation sync);

   List<TransactionOperation> getAllOperations();

   boolean hasTimedOut(long currentTime, int defaultTimeout);

   /**
    * To validate if the Transaction had previously timed out. This is to check the reason why a TX has been rolled
    * back.
    */
   boolean hasTimedOut();

   void putProperty(int index, Object property);

   Object getProperty(int index);

   default <T extends TransactionOperation> T getOrCreateOperation(int propertyIndex, Supplier<T> supplier) {
      return null;
   }

   default <T extends Runnable> T getOrCreateAfterWireRunnable(int propertyIndex, Supplier<T> supplier) {
      return null;
   }

   default <T extends TransactionOperation> T getOrCreateAfterStore(int propertyIndex, Supplier<T> supplier) {
      return null;
   }

   boolean isContainsPersistent();

   void setContainsPersistent();

   void setTimeout(int timeout);

   RefsOperation createRefsOperation(Queue queue, AckReason reason);

   boolean isAsync();

   /**
    * To be used on control transactions that are meant as internal and don't really require a hard sync.
    */
   Transaction setAsync(boolean async);

   default boolean isAllowPageTransaction() {
      return true;
   }

   /**
    * Calling this will defer the storage of the commit or prepare until delayDone is called.
    */
   void delay();

   /**
    * This is to be called when the delay portion is done.
    */
   void delayDone();


   void setMessagesSent();

   boolean isAckedMessages();

   void setAckedMessages();

   boolean isPagedMessages();

   void setPagedMessages();


}
