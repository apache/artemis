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
package org.apache.activemq.artemis.core.persistence;

import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.io.OperationConsistencyLevel;
import org.apache.activemq.artemis.core.journal.IOCompletion;

/**
 * This represents a set of operations done as part of replication.
 * <p>
 * When the entire set is done, a group of Runnables can be executed.
 */
public interface OperationContext extends IOCompletion {

   /**
    * Execute the task when all IO operations are complete, Or execute it immediately if nothing is pending. Notice it's
    * possible to pass a consistencyLevel to what should be waited before completing the operation.
    */
   void executeOnCompletion(IOCallback runnable, OperationConsistencyLevel consistencyLevel);

   /**
    * Execute the task when all IO operations are complete, Or execute it immediately if nothing is pending.
    *
    * @param runnable the tas to be executed.
    */
   void executeOnCompletion(IOCallback runnable);

   void replicationLineUp();

   void replicationDone();

   void pageSyncLineUp();

   void pageSyncDone();

   void waitCompletion() throws Exception;

   /**
    * Wait for the completion of this operation.
    *
    * @param timeout how long to wait in milliseconds
    * @return {@code true} if the operation completed within the specified timeout; {@code false} if not
    */
   boolean waitCompletion(long timeout) throws Exception;

   default void reset() {

   }
}
