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

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;

import org.junit.jupiter.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Utility adapted from: org.apache.activemq.util.Wait
 */
public class Wait {
   private static Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final long MAX_WAIT_MILLIS = 30 * 1000;
   public static final int SLEEP_MILLIS = 100;
   public static final String DEFAULT_FAILURE_MESSAGE = "Condition wasn't met";

   private static Method tdUtilMethod;
   static {
      try {
         Class<?> clazz = Class.forName("org.apache.activemq.artemis.utils.ThreadDumpUtil");
         tdUtilMethod = clazz.getMethod("threadDump", String.class);
      } catch (ClassNotFoundException | NoSuchMethodException | SecurityException e) {
         if (logger.isDebugEnabled()) {
            logger.debug("Wait util was unable to locate ThreadDumpUtil class/method", e);
         } else {
            logger.info("Wait util was unable to locate ThreadDumpUtil class/method due to: {}: {}", e.getClass().getName(), e.getMessage());
         }
      }
   }

   public interface Condition {
      boolean isSatisfied() throws Exception;
   }

   public interface LongCondition {
      long getCount() throws Exception;
   }

   public interface ExceptionCondition {
      void isSatisfied() throws Exception;
   }

   public interface ObjectCondition {
      Object getObject() throws Exception;
   }

   public interface IntCondition {
      int getCount() throws Exception;
   }

   public static boolean waitFor(Condition condition) throws Exception {
      return waitFor(condition, MAX_WAIT_MILLIS);
   }

   public static void assertEquals(Object obj, ObjectCondition condition) throws Exception {
      assertEquals(obj, condition, MAX_WAIT_MILLIS, SLEEP_MILLIS, null);
   }


   public static void assertEquals(long size, LongCondition condition) throws Exception {
      assertEquals(size, condition, MAX_WAIT_MILLIS);
   }

   public static void assertEquals(long size, LongCondition condition, long timeout) throws Exception {
      assertEquals(size, condition, timeout, SLEEP_MILLIS);
   }

   public static void assertEquals(Long size, LongCondition condition, long timeout, long sleepMillis) throws Exception {
      assertEquals(size, condition, timeout, sleepMillis, false);
   }

   public static void assertEquals(Long size, LongCondition condition, long timeout, long sleepMillis, boolean printThreadDump) throws Exception {
      checkForThreadDumpUtil(printThreadDump);

      boolean result = waitFor(() -> condition.getCount() == size, timeout, sleepMillis, printThreadDump);
      if (!result) {
         if (printThreadDump) {
            callThreadDumpUtil("thread dump");
         }
         Assertions.assertEquals(size.longValue(), condition.getCount());
      }
   }


   public static void assertEquals(int size, IntCondition condition) throws Exception {
      assertEquals(size, condition, MAX_WAIT_MILLIS);
   }

   public static void assertEquals(int size, IntCondition condition, long timeout) throws Exception {
      assertEquals(size, condition, timeout, SLEEP_MILLIS);
   }


   public static void assertEquals(Object obj, ObjectCondition condition, long timeout, long sleepMillis) throws Exception {
      assertEquals(obj, condition, timeout, sleepMillis, null);
   }

   public static void assertEquals(Object obj, ObjectCondition condition, long timeout, long sleepMillis, Supplier<String> messageSupplier) throws Exception {
      boolean result = waitFor(() -> (obj == condition || (obj != null && obj.equals(condition.getObject()))), timeout, sleepMillis);

      if (!result) {
         Assertions.assertEquals(obj, condition.getObject(), messageSupplier);
      }
   }

   public static void assertEquals(int size, IntCondition condition, long timeout, long sleepMillis) throws Exception {
      boolean result = waitFor(() -> condition.getCount() == size, timeout, sleepMillis);

      if (!result) {
         Assertions.assertEquals(size, condition.getCount());
      }
   }

   public static void assertEquals(int size, IntCondition condition, long timeout, long sleepMillis, Supplier<String> messageSupplier) throws Exception {
      boolean result = waitFor(() -> condition.getCount() == size, timeout, sleepMillis);

      if (!result) {
         Assertions.assertEquals(size, condition.getCount(), messageSupplier);
      }
   }

   public static void assertThrows(Class type, ExceptionCondition condition, long timeout, long sleepMillis, Supplier<String> messageSupplier) throws Exception {
      AtomicReference<Exception> ref = new AtomicReference();
      boolean result = waitFor(() -> {
         try {
            condition.isSatisfied();
         } catch (Exception e) {
            ref.set(e);
            if (type.isInstance(e)) {
               return true;
            }
         }
         return false;
      }, timeout, sleepMillis);

      if (!result) {
         Assertions.assertThrows(type, () -> {
            if (ref.get() != null) {
               throw ref.get();
            } else {
               return;
            }
         }, messageSupplier);
      }
   }

   public static void assertTrue(Condition condition) {
      assertTrue(DEFAULT_FAILURE_MESSAGE, condition);
   }

   public static void assertFalse(Condition condition) {
      assertTrue(() -> !condition.isSatisfied());
   }

   public static void assertFalse(String failureMessage, Condition condition) {
      assertTrue(failureMessage, () -> !condition.isSatisfied());
   }

   public static void assertFalse(String failureMessage, Condition condition, final long duration) {
      assertTrue(failureMessage, () -> !condition.isSatisfied(), duration, SLEEP_MILLIS);
   }

   public static void assertFalse(Supplier<String> failureMessage, Condition condition, final long duration, final long sleep) {
      assertTrue(failureMessage, () -> !condition.isSatisfied(), duration, sleep);
   }

   public static void assertFalse(Condition condition, final long duration, final long sleep) {
      assertTrue(DEFAULT_FAILURE_MESSAGE, () -> !condition.isSatisfied(), duration, sleep);
   }

   public static void assertTrue(Condition condition, final long duration) {
      assertTrue(DEFAULT_FAILURE_MESSAGE, condition, duration, SLEEP_MILLIS);
   }

   public static void assertTrue(String failureMessage, Condition condition) {
      assertTrue(failureMessage, condition, MAX_WAIT_MILLIS);
   }

   public static void assertTrue(String failureMessage, Condition condition, final long duration) {
      assertTrue(failureMessage, condition, duration, SLEEP_MILLIS);
   }

   public static <T> T assertNotNull(Supplier<T> supplier, final long duration, final long sleep) throws Exception {
      Assertions.assertTrue(waitFor(() -> supplier.get() != null, duration, sleep));
      return supplier.get();
   }

   public static void assertNull(ObjectCondition condition, final long duration, final long sleep) throws Exception {
      boolean result = waitFor(() -> (condition.getObject() == null), duration, sleep);

      if (!result) {
         Assertions.assertNull(condition.getObject());
      }
   }

   public static void assertTrue(Condition condition, final long duration, final long sleep) {
      assertTrue(DEFAULT_FAILURE_MESSAGE, condition, duration, sleep);
   }

   public static void assertTrue(String failureMessage, Condition condition, final long duration, final long sleep) {
      assertTrue(() -> failureMessage, condition, duration, sleep);
   }

   public static void assertTrue(Supplier<String> failureMessage, Condition condition, final long duration, final long sleep) {

      boolean result = waitFor(condition, duration, sleep);

      if (!result) {
         fail(failureMessage.get());
      }
   }

   public static boolean waitFor(final Condition condition, final long duration) throws Exception {
      return waitFor(condition, duration, SLEEP_MILLIS);
   }

   public static boolean waitFor(final Condition condition,
                                 final long durationMillis,
                                 final long sleepMillis) {
      return waitFor(condition, durationMillis, sleepMillis, false);
   }

   public static boolean waitFor(final Condition condition,
                                 final long durationMillis,
                                 final long sleepMillis,
                                 final boolean printThreadDump) {
      checkForThreadDumpUtil(printThreadDump);
      try {
         final long expiry = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(durationMillis);
         boolean conditionSatisified = condition.isSatisfied();
         while (!conditionSatisified && System.nanoTime() - expiry < 0) {
            if (sleepMillis == 0) {
               Thread.yield();
            } else {
               LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(sleepMillis));
            }
            conditionSatisified = condition.isSatisfied();
         }
         if (!conditionSatisified && printThreadDump) {
            callThreadDumpUtil("thread dump");
         }
         return conditionSatisified;
      } catch (Exception e) {
         throw new IllegalStateException(e);
      }
   }

   private static void checkForThreadDumpUtil(boolean printThreadDump) {
      if (printThreadDump && tdUtilMethod == null) {
         throw new IllegalStateException("Unable to identify ThreadDumpUtil class/method.");
      }
   }

   private static void callThreadDumpUtil(String msg) {
      checkForThreadDumpUtil(true);

      try {
         Object threadDump = tdUtilMethod.invoke(null, msg);
         System.out.println(threadDump);
      } catch (Exception e) {
         throw new RuntimeException("Failure running ThreadDumpUtil", e);
      }
   }
}
