/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.server.impl.jdbc;

import java.util.function.Supplier;

import org.apache.activemq.artemis.utils.UUID;

/**
 * Facade to abstract the operations on the shared state (inter-process and/or inter-thread) necessary to coordinate
 * broker nodes.
 */
interface SharedStateManager extends AutoCloseable {

   enum State {
      ACTIVE, PAUSED, FAILING_BACK, NOT_STARTED, FIRST_TIME_START
   }

   LeaseLock primaryLock();

   LeaseLock backupLock();

   UUID readNodeId();

   void writeNodeId(UUID nodeId);

   /**
    * Purpose of this method is to setup the environment to provide a shared state between primary/backup servers.
    * That means:
    * - check if a shared state exist and create it/wait for it if not
    * - check if a nodeId exists and create it if not
    *
    * @param nodeIdFactory used to create the nodeId if needed
    * @return the newly created NodeId or the old one if already present
    * @throws IllegalStateException if not able to setup the NodeId properly
    */
   UUID setup(Supplier<? extends UUID> nodeIdFactory);

   State readState();

   void writeState(State state);

   @Override
   default void close() throws Exception {

   }
}
