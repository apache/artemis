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

import java.util.List;

import org.apache.activemq.artemis.core.server.MessageReference;

/**
 * A TransactionOperation
 */
public interface TransactionOperation {

   void beforePrepare(Transaction tx) throws Exception;

   /**
    * After prepare shouldn't throw any exception.
    * <p>
    * Any verification has to be done on before prepare
    */
   void afterPrepare(Transaction tx);

   void beforeCommit(Transaction tx) throws Exception;

   /**
    * After commit shouldn't throw any exception.
    * <p>
    * Any verification has to be done on before commit
    */
   void afterCommit(Transaction tx);

   void beforeRollback(Transaction tx) throws Exception;

   /**
    * After rollback shouldn't throw any exception.
    * <p>
    * Any verification has to be done on before rollback
    */
   void afterRollback(Transaction tx);

   List<MessageReference> getRelatedMessageReferences();

   List<MessageReference> getListOnConsumer(long consumerID);

}
