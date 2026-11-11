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
package org.apache.activemq.artemis.core.paging.impl;

import java.util.function.Consumer;

import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.paging.PagingStore;

public class PagingStoreTestAccessor {
   public static SequentialFileFactory getFileFactory(PagingStore store) throws Exception {
      return ((PagingStoreImpl) store).getFileFactory();
   }

   public static int getUsedPagesSize(PagingStore store) {
      return ((PagingStoreImpl)store).getUsedPagesSize();
   }

   public static void forEachUsedPage(PagingStore store, Consumer<Page> consumer) {
      PagingStoreImpl impl = (PagingStoreImpl) store;
      impl.forEachUsedPage(consumer);
   }


}
