/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.activemq.artemis.core.paging.cursor.impl;

import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.core.paging.cursor.PageCursorProvider;
import org.apache.activemq.artemis.utils.SimpleFutureImpl;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class PageCursorProviderTestAccessor {

   public static void cleanup(PageCursorProvider provider) {
      SimpleFutureImpl<Object> simpleFuture = new SimpleFutureImpl<>();
      ((PageCursorProviderImpl)provider).pagingStore.execute(() -> {
         ((PageCursorProviderImpl)provider).cleanup();
         simpleFuture.set(new Object());
      });
      try {
         Object value = simpleFuture.get(30, TimeUnit.SECONDS);
         assertNotNull(value, "There was a timeout on calling cleanup");
      } catch (Throwable e) {
         e.printStackTrace();
      }
   }

   public static Long getNumberOfMessagesOnSubscriptions(PageCursorProvider provider) {
      return ((PageCursorProviderImpl)provider).getNumberOfMessagesOnSubscriptions();
   }
}
