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
package org.apache.activemq.artemis.core.paging.cursor;

import org.apache.activemq.artemis.core.transaction.Transaction;

public interface PageSubscriptionCounter {

   //incremental counter of messages added
   long getValueAdded();

   long getValue();

   long getPersistentSizeAdded();

   long getPersistentSize();

   void increment(Transaction tx, int add, long persistentSize) throws Exception;

   void loadValue(long recordValueID, long value, long persistentSize);

   void loadInc(long recordInd, int add, long persistentSize);

   void applyIncrementOnTX(Transaction tx, int add, long persistentSize);

   void markRebuilding();

   void finishRebuild();

   boolean isRebuilding();

   /**
    * This will process the reload
    */
   void processReload();

   // used when deleting the counter
   void delete() throws Exception;

   void snapshot();

   // used when leaving page mode, so the counters are deleted in batches
   // for each queue on the address
   void delete(Transaction tx) throws Exception;

   PageSubscriptionCounter setSubscription(PageSubscription subscription);

}