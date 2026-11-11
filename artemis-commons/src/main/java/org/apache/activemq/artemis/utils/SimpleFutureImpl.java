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
package org.apache.activemq.artemis.utils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class SimpleFutureImpl<V> implements SimpleFuture<V> {

   public SimpleFutureImpl() {
   }

   V value;
   Throwable exception;

   private final CountDownLatch latch = new CountDownLatch(1);

   boolean canceled = false;

   @Override
   public boolean cancel(boolean mayInterruptIfRunning) {
      canceled = true;
      latch.countDown();
      return true;
   }

   @Override
   public boolean isCancelled() {
      return canceled;
   }

   @Override
   public boolean isDone() {
      return latch.getCount() <= 0;
   }

   @Override
   public void fail(Throwable e) {
      this.exception = e;
      latch.countDown();
   }

   @Override
   public V get() throws InterruptedException, ExecutionException {
      latch.await();
      if (this.exception != null) {
         throw new ExecutionException(this.exception);
      }
      return value;
   }

   @Override
   public void set(V v) {
      this.value = v;
      latch.countDown();
   }

   @Override
   public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
      latch.await(timeout, unit);
      return value;
   }

}
