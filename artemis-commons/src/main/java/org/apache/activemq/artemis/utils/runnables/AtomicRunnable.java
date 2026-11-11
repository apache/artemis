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

package org.apache.activemq.artemis.utils.runnables;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.Consumer;

public abstract class AtomicRunnable implements Runnable {

   public static AtomicRunnable checkAtomic(Runnable run) {
      if (run instanceof AtomicRunnable atomicRunnable) {
         return atomicRunnable;
      } else {
         return new AtomicRunnableWithDelegate(run);
      }
   }

   private RunnableList acceptedList;
   private Consumer<AtomicRunnable> cancelTask;

   public RunnableList getAcceptedList() {
      return acceptedList;
   }

   public AtomicRunnable setAcceptedList(RunnableList acceptedList) {
      this.acceptedList = acceptedList;
      return this;
   }

   public Consumer<AtomicRunnable> getCancel() {
      return cancelTask;
   }

   public AtomicRunnable setCancel(Consumer<AtomicRunnable> cancelTask) {
      this.cancelTask = cancelTask;
      return this;
   }

   private volatile int ran;

   private static final AtomicIntegerFieldUpdater<AtomicRunnable> RAN_UPDATE =
      AtomicIntegerFieldUpdater.newUpdater(AtomicRunnable.class, "ran");

   public AtomicRunnable reset() {
      RAN_UPDATE.set(this, 0);
      return this;
   }

   public AtomicRunnable setRan() {
      RAN_UPDATE.set(this, 1);
      return this;
   }

   public boolean isRun() {
      return RAN_UPDATE.get(this) == 1;
   }

   @Override
   public void run() {
      if (RAN_UPDATE.compareAndSet(this, 0, 1)) {
         try {
            atomicRun();
         } finally {
            if (acceptedList != null) {
               acceptedList.remove(AtomicRunnable.this);
            }
         }
      }
   }

   public void cancel() {
      if (RAN_UPDATE.compareAndSet(this, 0, 1)) {
         if (cancelTask != null) {
            cancelTask.accept(AtomicRunnable.this);
         }
      }
   }

   public abstract void atomicRun();
}

