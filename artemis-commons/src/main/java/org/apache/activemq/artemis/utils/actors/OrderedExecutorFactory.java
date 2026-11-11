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
package org.apache.activemq.artemis.utils.actors;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.utils.ExecutorFactory;

/**
 * A factory for producing executors that run all tasks in order, which delegate to a single common executor instance.
 */
public final class OrderedExecutorFactory implements ExecutorFactory {

   final Executor parent;

   private boolean fair;

   public static boolean flushExecutor(Executor executor) {
      return flushExecutor(executor, 30, TimeUnit.SECONDS);
   }

   public static boolean flushExecutor(Executor executor, long timeout, TimeUnit unit) {
      final CountDownLatch latch = new CountDownLatch(1);
      executor.execute(latch::countDown);
      try {
         return latch.await(timeout, unit);
      } catch (Exception e) {
         return false;
      }
   }

   /**
    * Construct a new instance delegating to the given parent executor.
    *
    * @param parent the parent executor
    */
   public OrderedExecutorFactory(final Executor parent) {
      this.parent = parent;
   }

   /**
    * Get an executor that always executes tasks in order.
    *
    * @return an ordered executor
    */
   @Override
   public ArtemisExecutor getExecutor() {
      return new OrderedExecutor(parent).setFair(fair);
   }

   /**
    * I couldn't figure out how to make a new method to return a generic Actor with a given type
    */
   public Executor getParent() {
      return parent;
   }

   public boolean isFair() {
      return fair;
   }

   public OrderedExecutorFactory setFair(boolean fair) {
      this.fair = fair;
      return this;
   }
}

