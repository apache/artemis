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

package org.apache.activemq.artemis.utils.pools;

import java.util.Queue;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A simple encapsulation to provide a pool of objects.
 */
public abstract class Pool<T> {

   private final Queue<T> internalPool;

   private final Consumer<T> cleaner;
   private final Supplier<T> supplier;

   public Pool(int maxSize, Consumer<T> cleaner, Supplier<T> supplier) {
      internalPool = createQueue(maxSize);
      this.cleaner = cleaner;
      this.supplier = supplier;
   }

   abstract Queue<T> createQueue(int maxSize);

   /**
    * Use this to instantiate or return objects from the pool
    */
   public final T borrow() {
      if (internalPool == null) {
         return supplier.get();
      }

      T returnObject = internalPool.poll();

      if (returnObject == null) {
         returnObject = supplier.get();
      } else {
         cleaner.accept(returnObject);
      }

      return returnObject;
   }

   /**
    * Return objects to the pool, they will be either reused or ignored by the max size
    */
   public final void release(T object) {
      if (internalPool != null) {
         internalPool.offer(object);
      }
   }

   /**
    * @return an internal pool size or 0 if internal pool is null
    */
   public final int size() {
      if (internalPool != null) {
         return internalPool.size();
      }
      return 0;
   }
}
