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
package org.apache.activemq.artemis.core.server.files;

import static org.apache.activemq.artemis.core.server.files.FileStoreMonitor.FileStoreMonitorType.MaxDiskUsage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.tests.util.ServerTestBase;
import org.apache.activemq.artemis.utils.ReusableLatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileStoreMonitorTest extends ServerTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private ScheduledExecutorService scheduledExecutorService;
   private ExecutorService executorService;

   @BeforeEach
   public void startScheduled() {
      scheduledExecutorService = new ScheduledThreadPoolExecutor(5);
      executorService = Executors.newSingleThreadExecutor();
   }

   @AfterEach
   public void stopScheduled() {
      scheduledExecutorService.shutdown();
      scheduledExecutorService = null;
      executorService.shutdown();
   }

   @Test
   public void testSimpleTickForMaxDiskUsage() throws Exception {
      File garbageFile = new File(getTestDirfile(), "garbage.bin");

      try (FileOutputStream garbage = new FileOutputStream(garbageFile);
           BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(garbage);
           PrintStream out = new PrintStream(bufferedOutputStream)) {

         // This is just to make sure there is at least something on the device.
         // If the testsuite is running with an empty tempFS, it would return 0 and the assertion would fail.
         for (int i = 0; i < 100; i++) {
            out.println("Garbage " + i);
         }
      }

      final AtomicInteger overMaxUsage = new AtomicInteger(0);
      final AtomicInteger underMaxUsage = new AtomicInteger(0);
      final AtomicInteger tick = new AtomicInteger(0);

      FileStoreMonitor.Callback callback = (usableSpace, totalSpace, ok, type) -> {
         tick.incrementAndGet();
         if (ok) {
            underMaxUsage.incrementAndGet();
         } else {
            overMaxUsage.incrementAndGet();
         }
         logger.debug("tick:: usableSpace: " + usableSpace + ", totalSpace:" + totalSpace);
      };
      FileStoreMonitor storeMonitor = new FileStoreMonitor(scheduledExecutorService, executorService, 100L, TimeUnit.MILLISECONDS, 0.999, null, MaxDiskUsage);
      storeMonitor.addCallback(callback);
      storeMonitor.addStore(getTestDirfile());

      storeMonitor.tick();

      assertEquals(0, overMaxUsage.get());
      assertEquals(1, tick.get());
      assertEquals(1, underMaxUsage.get());

      storeMonitor.setMaxUsage(0);

      storeMonitor.tick();

      assertEquals(1, overMaxUsage.get());
      assertEquals(2, tick.get());
      assertEquals(1, underMaxUsage.get());
   }

   @Test
   public void testSimpleTickForMinDiskFree() throws Exception {
      File garbageFile = new File(getTestDirfile(), "garbage.bin");

      try (FileOutputStream garbage = new FileOutputStream(garbageFile);
           BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(garbage);
           PrintStream out = new PrintStream(bufferedOutputStream)) {

         // This is just to make sure there is at least something on the device.
         // If the testsuite is running with an empty tempFS, it would return 0 and the assertion would fail.
         for (int i = 0; i < 100; i++) {
            out.println("Garbage " + i);
         }
      }

      final AtomicInteger underMinDiskFree = new AtomicInteger(0);
      final AtomicInteger overMinDiskFree = new AtomicInteger(0);
      final AtomicInteger tick = new AtomicInteger(0);

      FileStoreMonitor.Callback callback = (usableSpace, totalSpace, ok, type) -> {
         tick.incrementAndGet();
         if (ok) {
            overMinDiskFree.incrementAndGet();
         } else {
            underMinDiskFree.incrementAndGet();
         }
      };
      FileStoreMonitor storeMonitor = new FileStoreMonitor(scheduledExecutorService, executorService, 100L, TimeUnit.MILLISECONDS, 0, null, FileStoreMonitor.FileStoreMonitorType.MinDiskFree);
      storeMonitor.addCallback(callback);
      storeMonitor.addStore(getTestDirfile());

      storeMonitor.tick();

      assertEquals(0, underMinDiskFree.get());
      assertEquals(1, tick.get());
      assertEquals(1, overMinDiskFree.get());

      storeMonitor.setMinDiskFree(Long.MAX_VALUE);

      storeMonitor.tick();

      assertEquals(1, underMinDiskFree.get());
      assertEquals(2, tick.get());
      assertEquals(1, overMinDiskFree.get());
   }

   @Test
   public void testSchedulerForMaxDiskUsage() throws Exception {

      FileStoreMonitor storeMonitor = new FileStoreMonitor(scheduledExecutorService, executorService, 20, TimeUnit.MILLISECONDS, 0.9, null, MaxDiskUsage);

      final ReusableLatch latch = new ReusableLatch(5);
      storeMonitor.addStore(getTestDirfile());
      storeMonitor.addCallback((usableSpace, totalSpace, ok, type) -> {
         logger.debug("Tick");
         latch.countDown();
      });
      storeMonitor.start();

      assertTrue(latch.await(1, TimeUnit.SECONDS));

      storeMonitor.stop();

      latch.setCount(1);

      assertFalse(latch.await(100, TimeUnit.MILLISECONDS));

   }

   @Test
   public void testSchedulerForMinDiskFree() throws Exception {

      FileStoreMonitor storeMonitor = new FileStoreMonitor(scheduledExecutorService, executorService, 20, TimeUnit.MILLISECONDS, 500000000, null, FileStoreMonitor.FileStoreMonitorType.MinDiskFree);

      final ReusableLatch latch = new ReusableLatch(5);
      storeMonitor.addStore(getTestDirfile());
      storeMonitor.addCallback((usableSpace, totalSpace, ok, type) -> {
         logger.debug("Tick");
         latch.countDown();
      });
      storeMonitor.start();

      assertTrue(latch.await(1, TimeUnit.SECONDS));

      storeMonitor.stop();

      latch.setCount(1);

      assertFalse(latch.await(100, TimeUnit.MILLISECONDS));

   }
}
