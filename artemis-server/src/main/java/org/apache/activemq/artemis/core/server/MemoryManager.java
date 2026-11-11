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
package org.apache.activemq.artemis.core.server;

import org.apache.activemq.artemis.utils.SizeFormatterUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * A memory usage watcher.
 * <p>
 * This class will run a thread monitoring memory usage and log warnings in case we are low on memory.
 */
public class MemoryManager implements ActiveMQComponent {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final Runtime runtime;

   private final long measureInterval;

   private final int memoryWarningThreshold;

   private volatile boolean started;

   private Thread thread;

   private volatile boolean low;

   public MemoryManager(final int memoryWarningThreshold, final long measureInterval) {
      runtime = Runtime.getRuntime();

      this.measureInterval = measureInterval;

      this.memoryWarningThreshold = memoryWarningThreshold;
   }

   public boolean isMemoryLow() {
      return low;
   }

   @Override
   public synchronized boolean isStarted() {
      return started;
   }

   @Override
   public synchronized void start() {
      if (logger.isDebugEnabled()) {
         logger.debug("Starting MemoryManager with MEASURE_INTERVAL: {} FREE_MEMORY_PERCENT: {}", measureInterval, memoryWarningThreshold);
      }

      if (started) {
         // Already started
         return;
      }

      started = true;

      thread = new Thread(new MemoryRunnable(), "activemq-memory-manager-thread");

      thread.setDaemon(true);

      thread.start();
   }

   @Override
   public synchronized void stop() {
      if (!started) {
         // Already stopped
         return;
      }

      started = false;

      thread.interrupt();

      try {
         thread.join();
      } catch (InterruptedException ignore) {
      }
   }

   private class MemoryRunnable implements Runnable {

      @Override
      public void run() {
         while (true) {
            try {
               if (thread.isInterrupted() && !started) {
                  break;
               }

               Thread.sleep(measureInterval);
            } catch (InterruptedException ignore) {
               if (!started) {
                  return;
               }
            }

            long maxMemory = runtime.maxMemory();

            long totalMemory = runtime.totalMemory();

            long freeMemory = runtime.freeMemory();

            long availableMemory = freeMemory + maxMemory - totalMemory;

            double availableMemoryPercent = 100.0 * availableMemory / maxMemory;

            final boolean availableBelowThreshold = availableMemoryPercent <= memoryWarningThreshold;
            final boolean prepareInfo = availableBelowThreshold || logger.isDebugEnabled();

            final StringBuilder info = prepareInfo ? new StringBuilder() : null;
            if (prepareInfo) {
               info.append(String.format("free memory:      %s%n", SizeFormatterUtil.sizeof(freeMemory)));
               info.append(String.format("max memory:       %s%n", SizeFormatterUtil.sizeof(maxMemory)));
               info.append(String.format("total memory:     %s%n", SizeFormatterUtil.sizeof(totalMemory)));
               info.append(String.format("available memory: %.2f%%%n", availableMemoryPercent));
            }

            if (prepareInfo && logger.isDebugEnabled()) {
               logger.debug(info.toString());
            }

            if (availableBelowThreshold) {
               ActiveMQServerLogger.LOGGER.memoryError(memoryWarningThreshold, info.toString());

               low = true;
            } else {
               low = false;
            }

         }
      }
   }
}
