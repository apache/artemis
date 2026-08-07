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
package org.apache.artemis.nativo.jlibaio.test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * <p>This class will use the framework provided to by AbstractQueuedSynchronizer.</p>
 * <p>AbstractQueuedSynchronizer is the framework for any sort of concurrent synchronization, such as Semaphores, events, etc, based on AtomicIntegers.</p>
 *
 * <p>This class works just like CountDownLatch, with the difference you can also increase the counter</p>
 *
 * <p>It could be used for sync points when one process is feeding the latch while another will wait when everything is done. (e.g. waiting IO completions to finish)</p>
 *
 * <p>On ActiveMQ Artemis we have the requirement of increment and decrement a counter until the user fires a ready event (commit). At that point we just act as a regular countDown.</p>
 *
 * <p>Note: This latch is reusable. Once it reaches zero, you can call up again, and reuse it on further waits.</p>
 *
 * <p>For example: prepareTransaction will wait for the current completions, and further adds will be called on the latch. Later on when commit is called you can reuse the same latch.</p>
 */
public class ReusableLatch {

   /**
    * Look at the doc and examples provided by AbstractQueuedSynchronizer for more information
    *
    * @see AbstractQueuedSynchronizer
    */
   @SuppressWarnings("serial")
   private static class CountSync extends AbstractQueuedSynchronizer {

      private CountSync(int count) {
         setState(count);
      }

      public int getCount() {
         return getState();
      }

      public void setCount(final int count) {
         setState(count);
      }

      @Override
      public int tryAcquireShared(final int numberOfAqcquires) {
         return getState() == 0 ? 1 : -1;
      }

      public void add() {
         for (; ; ) {
            int actualState = getState();
            int newState = actualState + 1;
            if (compareAndSetState(actualState, newState)) {
               return;
            }
         }
      }

      @Override
      public boolean tryReleaseShared(final int numberOfReleases) {
         for (; ; ) {
            int actualState = getState();
            if (actualState == 0) {
               return true;
            }

            int newState = actualState - numberOfReleases;

            if (newState < 0) {
               newState = 0;
            }

            if (compareAndSetState(actualState, newState)) {
               return newState == 0;
            }
         }
      }
   }

   private final CountSync control;

   public ReusableLatch() {
      this(0);
   }

   public ReusableLatch(final int count) {
      control = new CountSync(count);
   }

   public int getCount() {
      return control.getCount();
   }

   public void setCount(final int count) {
      control.setCount(count);
   }

   public void countUp() {
      control.add();
   }

   public void countDown() {
      control.releaseShared(1);
   }

   public void countDown(final int count) {
      control.releaseShared(count);
   }

   public void await() throws InterruptedException {
      control.acquireSharedInterruptibly(1);
   }

   public boolean await(final long milliseconds) throws InterruptedException {
      return control.tryAcquireSharedNanos(1, TimeUnit.MILLISECONDS.toNanos(milliseconds));
   }

   public boolean await(final long timeWait, TimeUnit timeUnit) throws InterruptedException {
      return control.tryAcquireSharedNanos(1, timeUnit.toNanos(timeWait));
   }
}
