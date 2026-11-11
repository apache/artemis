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
package org.apache.activemq.artemis.utils.actors;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.ToIntFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class ThresholdActor<T> extends ProcessorBase<Object> {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final AtomicIntegerFieldUpdater<ThresholdActor> SIZE_UPDATER = AtomicIntegerFieldUpdater.newUpdater(ThresholdActor.class, "size");
   private volatile int size = 0;

   private static final AtomicIntegerFieldUpdater<ThresholdActor> SCHEDULED_FLUSH_UPDATER = AtomicIntegerFieldUpdater.newUpdater(ThresholdActor.class, "scheduledFlush");
   private volatile int scheduledFlush = 0;

   private static final Object FLUSH = new Object();

   private final int maxSize;
   private final ToIntFunction<T> sizeGetter;
   private final ActorListener<T> listener;
   private final Runnable overThreshold;
   private final Runnable clearThreshold;

   public ThresholdActor(Executor parent, ActorListener<T> listener, int maxSize, ToIntFunction<T> sizeGetter, Runnable overThreshold, Runnable clearThreshold) {
      super(parent);
      this.listener = listener;
      this.maxSize = maxSize;
      this.sizeGetter = sizeGetter;
      this.overThreshold = overThreshold;
      this.clearThreshold = clearThreshold;
   }

   @Override
   protected final void doTask(Object task) {
      if (task == FLUSH) {
         clearThreshold.run();
         // should set to 0 no matter the value. There's a single thread setting this value back to zero
         SCHEDULED_FLUSH_UPDATER.set(this, 0);
         return;
      }

      final T theTask = (T)task;

      int estimateSize = sizeGetter.applyAsInt(theTask);

      try {
         listener.onMessage(theTask);
      } finally {
         if (estimateSize > 0) {
            SIZE_UPDATER.getAndAdd(this, -estimateSize);
         } else {
            logger.debug("element {} returned an invalid size over the Actor during release", theTask);
         }
      }
   }

   public void act(T message) {
      int sizeEstimate = sizeGetter.applyAsInt(message);
      if (sizeEstimate > 0) {
         int size = SIZE_UPDATER.addAndGet(this, sizeGetter.applyAsInt(message));
         if (size > maxSize) {
            flush();
         }
      } else {
         logger.debug("element {} returned an invalid size over the Actor", message);
      }
      task(message);
   }

   @Override
   public void flush() {
      if (SCHEDULED_FLUSH_UPDATER.compareAndSet(this, 0, 1)) {
         overThreshold.run();
         task(FLUSH);
      }
   }

   public void requestShutdown() {
      requestedShutdown = true;
      tasks.clear();
   }
}
