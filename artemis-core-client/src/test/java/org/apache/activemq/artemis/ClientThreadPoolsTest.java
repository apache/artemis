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
package org.apache.activemq.artemis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorImpl;
import org.apache.activemq.artemis.utils.ActiveMQThreadFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ClientThreadPoolsTest {

   private static Properties systemProperties;

   @BeforeAll
   public static void setup() {
      systemProperties = System.getProperties();
   }

   @AfterAll
   public static void tearDown() {
      System.clearProperty(ActiveMQClient.THREAD_POOL_MAX_SIZE_PROPERTY_KEY);
      System.clearProperty(ActiveMQClient.SCHEDULED_THREAD_POOL_SIZE_PROPERTY_KEY);
      ActiveMQClient.initializeGlobalThreadPoolProperties();
      ActiveMQClient.clearThreadPools();
      assertEquals(ActiveMQClient.DEFAULT_GLOBAL_THREAD_POOL_MAX_SIZE, ActiveMQClient.getGlobalThreadPoolSize());
   }

   @Test
   public void testSystemPropertyThreadPoolSettings() throws Exception {
      int threadPoolMaxSize = 100;
      int scheduledThreadPoolSize = 10;
      int flowControlThreadPoolMaxSize = 99;

      System.setProperty(ActiveMQClient.THREAD_POOL_MAX_SIZE_PROPERTY_KEY, "" + threadPoolMaxSize);
      System.setProperty(ActiveMQClient.SCHEDULED_THREAD_POOL_SIZE_PROPERTY_KEY, "" + scheduledThreadPoolSize);
      System.setProperty(ActiveMQClient.FLOW_CONTROL_THREAD_POOL_SIZE_PROPERTY_KEY, "" + flowControlThreadPoolMaxSize);
      ActiveMQClient.initializeGlobalThreadPoolProperties();
      ActiveMQClient.clearThreadPools();

      testSystemPropertiesThreadPoolSettings(threadPoolMaxSize, scheduledThreadPoolSize, flowControlThreadPoolMaxSize);
   }

   @Test
   public void testShutdownPoolInUse() throws Exception {
      ActiveMQClient.setGlobalThreadPoolProperties(10, 1, 2);
      ActiveMQClient.clearThreadPools();

      final CountDownLatch inUse = new CountDownLatch(1);
      final CountDownLatch neverLeave = new CountDownLatch(1);

      ActiveMQClient.getGlobalThreadPool().execute(() -> {
         try {
            inUse.countDown();
            neverLeave.await();
         } catch (Exception e) {
            e.printStackTrace();
            neverLeave.countDown();
         }
      });

      assertTrue(inUse.await(10, TimeUnit.SECONDS));
      ActiveMQClient.clearThreadPools(100, TimeUnit.MILLISECONDS);
      assertTrue(neverLeave.await(10, TimeUnit.SECONDS));
   }

   @Test
   public void testInjectPools() throws Exception {
      ActiveMQClient.clearThreadPools();

      ThreadPoolExecutor poolExecutor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());

      ThreadPoolExecutor flowControlPoolExecutor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());

      ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(1, ActiveMQThreadFactory.defaultThreadFactory(getClass().getName()));

      ActiveMQClient.injectPools(poolExecutor, scheduledThreadPoolExecutor, flowControlPoolExecutor);

      final CountDownLatch inUse = new CountDownLatch(1);
      final CountDownLatch neverLeave = new CountDownLatch(1);

      ActiveMQClient.getGlobalThreadPool().execute(() -> {
         try {
            inUse.countDown();
            neverLeave.await();
         } catch (Exception e) {
            e.printStackTrace();
            neverLeave.countDown();
         }
      });

      assertTrue(inUse.await(10, TimeUnit.SECONDS));
      poolExecutor.shutdownNow();
      scheduledThreadPoolExecutor.shutdownNow();
      flowControlPoolExecutor.shutdownNow();
      assertTrue(neverLeave.await(10, TimeUnit.SECONDS));

      assertTrue(inUse.await(10, TimeUnit.SECONDS));
      assertTrue(neverLeave.await(10, TimeUnit.SECONDS));

      ActiveMQClient.clearThreadPools(100, TimeUnit.MILLISECONDS);
   }

   @Test
   public void testStaticPropertiesThreadPoolSettings() throws Exception {

      int testMaxSize = 999;
      int testScheduleSize = 9;
      int testFlowControlMaxSize = 888;

      ActiveMQClient.setGlobalThreadPoolProperties(testMaxSize, testScheduleSize, testFlowControlMaxSize);
      ActiveMQClient.clearThreadPools();
      testSystemPropertiesThreadPoolSettings(testMaxSize, testScheduleSize, testFlowControlMaxSize);
   }

   @Test
   public void testSmallPool() throws Exception {

      int testMaxSize = 2;
      int testScheduleSize = 9;
      int testFlowControlMaxSize = 8;

      ActiveMQClient.setGlobalThreadPoolProperties(testMaxSize, testScheduleSize, testFlowControlMaxSize);
      ActiveMQClient.clearThreadPools();
      testSystemPropertiesThreadPoolSettings(testMaxSize, testScheduleSize, testFlowControlMaxSize);
   }

   private void testSystemPropertiesThreadPoolSettings(int expectedMax, int expectedScheduled, int expectedFlowControl) throws Exception {
      ServerLocatorImpl serverLocator = new ServerLocatorImpl(false);
      serverLocator.isUseGlobalPools();

      Method setThreadPools = ServerLocatorImpl.class.getDeclaredMethod("setThreadPools");
      setThreadPools.setAccessible(true);
      setThreadPools.invoke(serverLocator);

      // TODO: I would get this from the ActiveMQClient
      Field threadPoolField = ServerLocatorImpl.class.getDeclaredField("threadPool");
      Field scheduledThreadPoolField = ServerLocatorImpl.class.getDeclaredField("scheduledThreadPool");
      Field flowControlThreadPoolField = ServerLocatorImpl.class.getDeclaredField("flowControlThreadPool");

      threadPoolField.setAccessible(true);
      scheduledThreadPoolField.setAccessible(true);
      flowControlThreadPoolField.setAccessible(true);

      ThreadPoolExecutor threadPool = (ThreadPoolExecutor) ActiveMQClient.getGlobalThreadPool();

      final CountDownLatch doneMax = new CountDownLatch(expectedMax);
      final CountDownLatch latch = new CountDownLatch(1);
      final CountDownLatch latchTotal = new CountDownLatch(expectedMax * 3); // we will schedule 3 * max, so all runnables should execute
      final AtomicInteger errors = new AtomicInteger(0);

      // Set this to true if you need to debug why executions are not being performed.
      final boolean debugExecutions = false;

      for (int i = 0; i < expectedMax * 3; i++) {
         final int localI = i;
         threadPool.execute(() -> {
            try {

               if (debugExecutions) {
                  System.out.println("runnable " + localI);
               }
               doneMax.countDown();
               latch.await();
               latchTotal.countDown();
            } catch (Exception e) {
               errors.incrementAndGet();
            } finally {
               if (debugExecutions) {
                  System.out.println("done " + localI);
               }
            }
         });
      }

      assertTrue(doneMax.await(5, TimeUnit.SECONDS));
      latch.countDown();
      assertTrue(latchTotal.await(5, TimeUnit.SECONDS));

      ScheduledThreadPoolExecutor scheduledThreadPool = (ScheduledThreadPoolExecutor) scheduledThreadPoolField.get(serverLocator);
      ThreadPoolExecutor flowControlThreadPool = (ThreadPoolExecutor) flowControlThreadPoolField.get(serverLocator);

      assertEquals(expectedMax, threadPool.getMaximumPoolSize());
      assertEquals(expectedScheduled, scheduledThreadPool.getCorePoolSize());
      assertEquals(expectedFlowControl, flowControlThreadPool.getMaximumPoolSize());
   }

   @Test
   public void testThreadPoolInjection() throws Exception {

      ServerLocator serverLocator = new ServerLocatorImpl(false);

      ThreadPoolExecutor threadPool = new ThreadPoolExecutor(1, 1, 60L, TimeUnit.SECONDS, new SynchronousQueue<>());
      ScheduledThreadPoolExecutor scheduledThreadPool = new ScheduledThreadPoolExecutor(1);
      ThreadPoolExecutor flowControlThreadPool = new ThreadPoolExecutor(1, 1, 60L, TimeUnit.SECONDS, new SynchronousQueue<>());
      serverLocator.setThreadPools(threadPool, scheduledThreadPool, flowControlThreadPool);

      Field threadPoolField = ServerLocatorImpl.class.getDeclaredField("threadPool");
      Field scheduledThreadPoolField = ServerLocatorImpl.class.getDeclaredField("scheduledThreadPool");
      Field flowControlThreadPoolField = ServerLocatorImpl.class.getDeclaredField("flowControlThreadPool");

      serverLocator.initialize();

      threadPoolField.setAccessible(true);
      scheduledThreadPoolField.setAccessible(true);
      flowControlThreadPoolField.setAccessible(true);

      ThreadPoolExecutor tpe = (ThreadPoolExecutor) threadPoolField.get(serverLocator);
      ScheduledThreadPoolExecutor stpe = (ScheduledThreadPoolExecutor) scheduledThreadPoolField.get(serverLocator);
      ThreadPoolExecutor fctpe = (ThreadPoolExecutor) flowControlThreadPoolField.get(serverLocator);

      assertEquals(threadPool, tpe);
      assertEquals(scheduledThreadPool, stpe);
      assertEquals(flowControlThreadPool, fctpe);
   }

   @AfterEach
   public void cleanup() {
      // Resets the global thread pool properties back to default.
      System.setProperties(systemProperties);
      ActiveMQClient.initializeGlobalThreadPoolProperties();
      ActiveMQClient.clearThreadPools();
   }

}
