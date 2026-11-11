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
package org.apache.activemq.artemis.tests.integration.ra;

import javax.transaction.Status;
import javax.transaction.Synchronization;
import javax.transaction.TransactionSynchronizationRegistry;

public class DummyTransactionSynchronizationRegistry implements TransactionSynchronizationRegistry {
   private int status = Status.STATUS_NO_TRANSACTION;
   private boolean rollbackOnly = false;

   public DummyTransactionSynchronizationRegistry setStatus(int status) {
      this.status = status;
      return this;
   }

   @Override
   public Object getTransactionKey() {
      return null;
   }

   @Override
   public void putResource(Object key, Object value) {

   }

   @Override
   public Object getResource(Object key) {
      return null;
   }

   @Override
   public void registerInterposedSynchronization(Synchronization sync) {

   }

   @Override
   public int getTransactionStatus() {
      return status;
   }

   @Override
   public void setRollbackOnly() {
      rollbackOnly = true;
   }

   @Override
   public boolean getRollbackOnly() {
      return rollbackOnly;
   }
}
