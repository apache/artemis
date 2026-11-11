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
package org.apache.activemq.artemis.core.postoffice.impl;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.postoffice.DuplicateIDCache;

public final class DuplicateIDCaches {

   private DuplicateIDCaches() {

   }

   public static DuplicateIDCache persistent(final SimpleString address,
                                             final int size,
                                             final StorageManager storageManager) {
      if (size == 0) {
         return new NoOpDuplicateIDCache();
      } else {
         return new PersistentDuplicateIDCache(address, size, storageManager);
      }
   }

   public static DuplicateIDCache inMemory(final SimpleString address, final int size) {
      if (size == 0) {
         return new NoOpDuplicateIDCache();
      } else {
         return new InMemoryDuplicateIDCache(address, size);
      }
   }
}
