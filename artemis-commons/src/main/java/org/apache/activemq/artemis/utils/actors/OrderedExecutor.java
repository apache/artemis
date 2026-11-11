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

import org.apache.activemq.artemis.api.core.ActiveMQInterruptedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * An executor that always runs all tasks in order, using a delegate executor to run the tasks.
 * <p>
 * More specifically, any call B to the {@link #execute(Runnable)} method that happens-after another call A to the
 * same method, will result in B's task running after A's.
 */
public class OrderedExecutor extends ProcessorBase<Runnable> implements ArtemisExecutor {

   public OrderedExecutor(Executor delegate) {
      super(delegate);
   }

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private boolean fair;

   @Override
   public boolean isFair() {
      return fair;
   }

   /**
    * If this OrderedExecutor is fair, it will yield for another executors after each task ran
    */
   @Override
   public OrderedExecutor setFair(boolean fair) {
      this.fair = fair;
      return this;
   }

   @Override
   protected final void doTask(Runnable task) {
      try {
         task.run();
         if (fair) {
            this.yield();
         }
      } catch (ActiveMQInterruptedException e) {
         // This could happen during shutdowns. Nothing to be concerned about here
         logger.debug("Interrupted Thread", e);
      } catch (Throwable t) {
         logger.warn(t.getMessage(), t);
      }

   }

   @Override
   public final void execute(Runnable run) {
      task(run);
   }

   @Override
   public String toString() {
      return "OrderedExecutor(tasks=" + tasks + ")";
   }

}
