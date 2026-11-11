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

import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.xa.XAResource;

class DummyTransaction implements Transaction {
   public boolean rollbackOnly = false;

   public int status = Status.STATUS_ACTIVE;

   @Override
   public void commit() throws RollbackException, HeuristicMixedException, HeuristicRollbackException, SecurityException, SystemException {
   }

   @Override
   public void rollback() throws IllegalStateException, SystemException {
   }

   @Override
   public void setRollbackOnly() throws IllegalStateException, SystemException {
      rollbackOnly = true;
   }

   @Override
   public int getStatus() throws SystemException {
      return status;
   }

   @Override
   public boolean enlistResource(XAResource xaResource) throws RollbackException, IllegalStateException, SystemException {
      return false;
   }

   @Override
   public boolean delistResource(XAResource xaResource, int i) throws IllegalStateException, SystemException {
      return false;
   }

   @Override
   public void registerSynchronization(Synchronization synchronization) throws RollbackException, IllegalStateException, SystemException {
   }
}
