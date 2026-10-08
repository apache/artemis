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

import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.management.VMOption;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/**
 * Utility that detects various properties specific to the current runtime environment, such as JVM bitness and OS
 * type.
 */
public final class Env {

   private static final int OS_PAGE_SIZE = getOsPageSize();
   private static final int MEM_PAGE_SIZE = getMemPageSize();

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

   public static int getOsPageSize() {
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
      return osPageSize;
   }

   public static int getJvmLargePageSize() {
      //Returns 0 if disabled on either the OS or the JVM level.
      try {
         // Access the internal HotSpot Diagnostic Bean
         HotSpotDiagnosticMXBean hsBean = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);

         // check if large pages are enabled in VM options
         VMOption option = hsBean.getVMOption("UseLargePages");
         boolean largePagesVMEnabled = Boolean.parseBoolean(option.getValue());

         // check if large pages are enabled on OS level
         boolean largePagesOSEnabled = true;

         if (largePagesVMEnabled && largePagesOSEnabled) {
            // Query the VM option value directly
            // hsBean.getVMOption returns huge page size in bytes even if there are no large pages configured/available on the OS level
            String largePageSize = hsBean.getVMOption("LargePageSizeInBytes").getValue();
            return Integer.parseInt(largePageSize);
         } else {
            return 0; // Large pages not enabled or not available for user running the JVM
         }
      } catch (Throwable t) {
         return 0; // Large pages not explicitly configured or supported on this JVM instance
      }
   }

   public static int getMemPageSize() {
      // fall back to OS PAGE SIZE if huge pages are disabled/not available
      int hugeMemPageSize = getJvmLargePageSize();
      return hugeMemPageSize > 0 ? hugeMemPageSize : OS_PAGE_SIZE;
   }

   private Env() {

   }

   /**
    * Return the base hardware page size of the operating system's virtual memory management unit (MMU). This value will always be a power of two.
    */
   public static int osPageSize() {
      return OS_PAGE_SIZE;
   }

   /**
    * Return the size in bytes of a memory page.
    */
   public static int memPageSize() {
      return MEM_PAGE_SIZE;
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
