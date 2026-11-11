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
package org.apache.activemq.artemis.utils;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

public class ReferenceCounterUtil implements ReferenceCounter, AutoCloseable {

   private Runnable task;

   /**
    * If executor is null the runnable will be called within the same thread, otherwise the executor will be used
    */
   private final Executor executor;

   private volatile int use = 0;

   private static final AtomicIntegerFieldUpdater<ReferenceCounterUtil> useUpdater = AtomicIntegerFieldUpdater.newUpdater(ReferenceCounterUtil.class, "use");

   public ReferenceCounterUtil() {
      this.executor = null;
      this.task = null;
   }

   public ReferenceCounterUtil(Executor executor) {
      this.executor = executor;
   }

   public ReferenceCounterUtil(Runnable runnable, Executor executor) {
      this.setTask(runnable);
      this.executor = executor;
   }

   public ReferenceCounterUtil(Runnable runnable) {
      this.setTask(runnable);
      this.executor = null;
   }

   @Override
   public void setTask(Runnable task) {
      this.task = task;
   }

   @Override
   public Runnable getTask() {
      return task;
   }

   @Override
   public int increment() {
      return useUpdater.incrementAndGet(this);
   }

   @Override
   public int decrement() {
      int value = useUpdater.decrementAndGet(this);
      if (value == 0) {
         execute();
      }

      return value;
   }

   /**
    * it will set the value all the way to 0, and execute the task meant for when the value was 0.
    */
   public void exhaust() {
      execute();
      useUpdater.set(this, 0);
   }

   private void execute() {
      if (task != null) {
         if (executor != null) {
            executor.execute(task);
         } else {
            task.run();
         }
      }
   }

   @Override
   public void check() {
      if (getCount() <= 0) {
         execute();
      }
   }

   @Override
   public int getCount() {
      return useUpdater.get(this);
   }

   @Override
   public void close() {
      decrement();
   }
}
