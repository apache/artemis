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
package org.apache.activemq.artemis.utils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/**
 * Utility that detects various properties specific to the current runtime environment, such as JVM bitness and OS
 * type.
 */
public final class Env {

   private static final int OS_PAGE_SIZE;

   static {
      //most common OS page size value
      int osPageSize = 4096;
      sun.misc.Unsafe instance;
      try {
         Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
         field.setAccessible(true);
         instance = (sun.misc.Unsafe) field.get(null);
      } catch (Throwable t) {
         try {
            Constructor<sun.misc.Unsafe> c = sun.misc.Unsafe.class.getDeclaredConstructor(new Class[0]);
            c.setAccessible(true);
            instance = c.newInstance(new Object[0]);
         } catch (Throwable t1) {
            instance = null;
         }
      }
      if (instance != null) {
         osPageSize = instance.pageSize();
      }
      OS_PAGE_SIZE = osPageSize;
   }

   /**
    * The system will change a few logs and semantics to be suitable to run a long testsuite. Like a few log entries
    * that are only valid during a production system. or a few cases we need to know as warn on the testsuite and as log
    * in production.
    */
   private static boolean testEnv = false;

   private static final String OS = System.getProperty("os.name").toLowerCase();
   private static final boolean IS_LINUX = OS.startsWith("linux");
   private static final boolean IS_MAC = OS.startsWith("mac");
   private static final boolean IS_WINDOWS = OS.startsWith("windows");

   private Env() {

   }

   /**
    * Return the size in bytes of a OS memory page. This value will always be a power of two.
    */
   public static int osPageSize() {
      return OS_PAGE_SIZE;
   }

   public static boolean isTestEnv() {
      return testEnv;
   }

   public static void setTestEnv(boolean testEnv) {
      Env.testEnv = testEnv;
   }

   public static boolean isLinuxOs() {
      return IS_LINUX == true;
   }

   public static boolean isMacOs() {
      return IS_MAC == true;
   }

   public static boolean isWindowsOs() {
      return IS_WINDOWS == true;
   }
}
