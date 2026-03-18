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

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/*
 * ActiveMQThreadPoolExecutor: a special ThreadPoolExecutor that combines
 * the benefits of a cached executor and a fixed size executor.
 * Similar to a cached executor, threads exceeding the core size are only created on demand,
 * and will be removed after idling for a specified keep time.
 * But in contrast to a standard cached executor, tasks are queued if the
 * maximum pool size if reached, instead of rejected.
 *
 * This is achieved by using a specialized blocking queue, which checks the
 * state of the associated executor in the offer method to decide whether to
 * queue a task or have the executor create another thread.
 *
 * Since the thread pool's execute method is reentrant, more than one caller
 * could try to offer a task into the queue. There is a small chance that
 * (a few) more threads are created as it should be limited by max pool size.
 * To allow for such a case not to reject a task, the underlying thread pool
 * executor is not limited. Only the offer method checks the configured limit.
 */
public class ActiveMQThreadPoolExecutor extends ThreadPoolExecutor {

   @SuppressWarnings("serial")
   private static class ThreadPoolQueue extends LinkedBlockingQueue<Runnable> {

      private ActiveMQThreadPoolExecutor executor = null;

      // keep track of the difference between the number of idle threads and
      // the number of queued tasks. If the delta is > 0, we have more
      // idle threads than queued tasks and can add more tasks into the queue.
      // The delta is incremented if a thread becomes idle or if a task is taken from the queue.
      // The delta is decremented if a thread leaves idle state or if a task is added to the queue.
      private static final AtomicIntegerFieldUpdater<ThreadPoolQueue> DELTA_UPDATER = AtomicIntegerFieldUpdater.newUpdater(ThreadPoolQueue.class, "threadTaskDelta");
      private volatile int threadTaskDelta = 0;

      public void setExecutor(ActiveMQThreadPoolExecutor executor) {
         this.executor = executor;
      }

      @Override
      public boolean offer(Runnable runnable) {
         boolean retval = false;

         if (threadTaskDelta > 0 || (executor.getPoolSize() >= executor.getMaximumPoolSize())) {
            // A new task will be added to the queue if the maximum number of threads has been reached
            // or if the delta is > 0, which means that there are enough idle threads.

            retval = super.offer(runnable);

            // Only decrement the delta if the task has actually been added to the queue
            if (retval) {
               DELTA_UPDATER.decrementAndGet(this);
            }
         }

         return retval;
      }

      @Override
      public Runnable take() throws InterruptedException {
         // Increment the delta as a thread becomes idle
         // by waiting for a task to take from the queue
         DELTA_UPDATER.incrementAndGet(this);


         Runnable runnable = null;

         try {
            runnable = super.take();
            return runnable;
         } finally {
            // Now the thread is no longer idle waiting for a task
            // If it had taken a task, the delta remains the same
            // (decremented by the thread and incremented by the taken task)
            // Only if no task had been taken, we have to decrement the delta.
            if (runnable == null) {
               DELTA_UPDATER.decrementAndGet(this);
            }
         }
      }

      @Override
      public Runnable poll(long arg0, TimeUnit arg2) throws InterruptedException {
         // Increment the delta as a thread becomes idle
         // by waiting for a task to poll from the queue
         DELTA_UPDATER.incrementAndGet(this);

         Runnable runnable = null;

         try {
            runnable = super.poll(arg0, arg2);
         } finally {
            // Now the thread is no longer idle waiting for a task
            // If it had taken a task, the delta remains the same
            // (decremented by the thread and incremented by the taken task)
            if (runnable == null) {
               DELTA_UPDATER.decrementAndGet(this);
            }
         }

         return runnable;
      }
   }

   private int maxPoolSize;

   public ActiveMQThreadPoolExecutor(int coreSize, int maxSize, long keep, TimeUnit keepUnits, ThreadFactory factory) {
      this(coreSize, maxSize, keep, keepUnits, new ThreadPoolQueue(), factory);
   }

   // private constructor is needed to inject 'this' into the ThreadPoolQueue instance
   private ActiveMQThreadPoolExecutor(int coreSize,
                                      int maxSize,
                                      long keep,
                                      TimeUnit keepUnits,
                                      ThreadPoolQueue myQueue,
                                      ThreadFactory factory) {
      super(coreSize, Integer.MAX_VALUE, keep, keepUnits, myQueue, factory);
      maxPoolSize = maxSize;
      myQueue.setExecutor(this);
   }

   @Override
   public int getMaximumPoolSize() {
      return maxPoolSize;
   }

   @Override
   public void setMaximumPoolSize(int maxSize) {
      maxPoolSize = maxSize;
   }
}
