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

/**
 * This class will use the framework provided to by AbstractQueuedSynchronizer.
 * <p>
 * AbstractQueuedSynchronizer is the framework for any sort of concurrent synchronization, such as Semaphores, events,
 * etc, based on AtomicIntegers.
 * <p>
 * This class works just like CountDownLatch, with the difference you can also increase the counter
 * <p>
 * It could be used for sync points when one process is feeding the latch while another will wait when everything is
 * done. (e.g. waiting IO completions to finish)
 * <p>
 * On Apache Artemis we have the requirement of increment and decrement a counter until the user fires a ready event
 * (commit). At that point we just act as a regular countDown.
 * <p>
 * Note: This latch is reusable. Once it reaches zero, you can call up again, and reuse it on further waits.
 * <p>
 * For example: prepareTransaction will wait for the current completions, and further adds will be called on the latch.
 * Later on when commit is called you can reuse the same latch.
 */
public class ReusableLatch extends AbstractLatch {

   public ReusableLatch() {
      super();
   }

   public ReusableLatch(int count) {
      super(count);
   }

   @Override
   public void countDown() {
      control.releaseShared(1);
   }

   @Override
   public void countDown(final int count) {
      control.releaseShared(count);
   }
}
