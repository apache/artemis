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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

public abstract class AbstractLatch {

   /**
    * Look at the doc and examples provided by AbstractQueuedSynchronizer for more information
    *
    * @see AbstractQueuedSynchronizer
    */
   @SuppressWarnings("serial")
   protected static class CountSync extends AbstractQueuedSynchronizer {

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
         for (;;) {
            int actualState = getState();
            int newState = actualState + 1;
            if (compareAndSetState(actualState, newState)) {
               return;
            }
         }
      }

      @Override
      public boolean tryReleaseShared(final int numberOfReleases) {
         for (;;) {
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

   protected final CountSync control;

   public AbstractLatch() {
      this(0);
   }

   public AbstractLatch(final int count) {
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

   public abstract void countDown();

   public abstract void countDown(int count);

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
