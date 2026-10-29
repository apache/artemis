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
package org.apache.activemq.artemis.core.server;

import org.apache.activemq.artemis.core.server.impl.QueueImpl;
import org.apache.activemq.artemis.core.transaction.Transaction;

public interface StorageMessageReader {

   void scheduleRead(boolean scheduleExpiry);

   void checkRead();

   void lock();

   void unlock();

   boolean allowDirectDelivery();

   int iterateMessages(String operationName, int flushLimit, boolean separatePageIterator, QueueImpl.QueueIterateAction messageAction, int count) throws Exception;

   default void addPendingAfterStorage(long elements, long size, Transaction tx) {
   }

   default boolean addPending(long elements, long size) {
      return true;
   }

   default long getReaderPaged() {
      return 0;
   }
}
