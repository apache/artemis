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

import java.util.concurrent.Future;
import java.util.function.Consumer;

import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.paging.PagedMessage;

/**
 * The provider of Cursor for a given Address
 */
public interface PageCursorProvider {

   PagedReference newReference(PagedMessage msg, PageSubscription sub);

   /**
    * @param queueId The cursorID should be the same as the queueId associated for persistence
    */
   PageSubscription getSubscription(long queueId);

   void forEachSubscription(Consumer<PageSubscription> consumer);

   PageSubscription createSubscription(long queueId, Filter filter, boolean durable);

   void processReload() throws Exception;

   void stop();

   void counterSnapshot();

   void flushExecutors();

   Future<Boolean> scheduleCleanup();

   void disableCleanup();

   void resumeCleanup();

   /**
    * Cleanup stuff as paging mode is being cleared
    */
   void onPageModeCleared();

   void close(PageSubscription pageCursorImpl);

   void checkClearPageLimit();

   void counterRebuildStarted();

   void counterRebuildDone();

   boolean isRebuildDone();

}
