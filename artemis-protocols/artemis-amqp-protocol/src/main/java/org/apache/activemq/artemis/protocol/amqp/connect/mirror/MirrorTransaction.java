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
package org.apache.activemq.artemis.protocol.amqp.connect.mirror;

import java.util.List;

import org.apache.activemq.artemis.core.io.OperationConsistencyLevel;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.server.mirror.MirrorController;
import org.apache.activemq.artemis.core.transaction.TransactionOperation;
import org.apache.activemq.artemis.core.transaction.impl.TransactionImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * MirrorTransaction disable some syncs in storage, and plays with OperationConsistencyLevel to relax some of the syncs
 * required for Mirroring.
 */
public class MirrorTransaction extends TransactionImpl {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   boolean allowPageTransaction;

   MirrorController controlInUse;

   public MirrorTransaction(StorageManager storageManager) {
      super(storageManager);
      this.controlInUse = AMQPMirrorControllerTarget.getControllerInUse();
      logger.debug("controlTarget = {} transactionID = {}", controlInUse, getID());
   }

   @Override
   protected synchronized void afterCommit(List<TransactionOperation> operationsToComplete) {
      MirrorController beforeController = AMQPMirrorControllerTarget.getControllerInUse();
      AMQPMirrorControllerTarget.setControllerInUse(controlInUse);
      try {
         super.afterCommit(operationsToComplete);
      } finally {
         AMQPMirrorControllerTarget.setControllerInUse(beforeController);
      }
   }

   @Override
   public boolean isAllowPageTransaction() {
      return allowPageTransaction;
   }

   public MirrorTransaction setAllowPageTransaction(boolean allowPageTransaction) {
      this.allowPageTransaction = allowPageTransaction;
      return this;
   }

   @Override
   protected OperationConsistencyLevel getRequiredConsistency() {
      return OperationConsistencyLevel.IGNORE_REPLICATION;
   }
}
