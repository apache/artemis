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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public interface SimpleFuture<V> extends Future<V> {

   SimpleFuture dumb = new SimpleFuture() {
      @Override
      public void fail(Throwable e) {

      }

      @Override
      public void set(Object o) {

      }

      @Override
      public boolean cancel(boolean mayInterruptIfRunning) {
         return false;
      }

      @Override
      public boolean isCancelled() {
         return false;
      }

      @Override
      public boolean isDone() {
         return false;
      }

      @Override
      public Object get() throws InterruptedException, ExecutionException {
         return null;
      }

      @Override
      public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
         return null;
      }
   };

   static SimpleFuture dumb() {
      return dumb;
   }

   void fail(Throwable e);

   void set(V v);
}
