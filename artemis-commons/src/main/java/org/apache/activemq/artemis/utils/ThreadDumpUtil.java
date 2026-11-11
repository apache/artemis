/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.activemq.artemis.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

public final class ThreadDumpUtil {
   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static String threadDump(final String msg) {

      try (
         StringWriter str = new StringWriter();
         PrintWriter out = new PrintWriter(str)
      ) {

         ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

         out.println("*******************************************************************************");
         out.println("Complete Thread dump " + msg);

         for (ThreadInfo threadInfo : threadMXBean.dumpAllThreads(true, true)) {
            out.println(threadInfoToString(threadInfo));
         }

         long[] deadlockedThreads = threadMXBean.findDeadlockedThreads();

         if (deadlockedThreads != null && deadlockedThreads.length > 0) {
            out.println("Deadlock detected!");
            out.println();

            for (ThreadInfo threadInfo : threadMXBean.getThreadInfo(deadlockedThreads, true, true)) {
               out.println(threadInfoToString(threadInfo));
            }
         }

         out.println("===============================================================================");
         out.println("End Thread dump " + msg);
         out.println("*******************************************************************************");

         return str.toString();

      } catch (IOException e) {
         logger.error("Exception thrown during generating of thread dump.", e);
      }

      return "Generating of thread dump failed " + msg;

   }

   private static String threadInfoToString(ThreadInfo threadInfo) {
      StringBuilder sb = new StringBuilder("\"" + threadInfo.getThreadName() + "\"" +
            " Id=" + threadInfo.getThreadId() + " " +
            threadInfo.getThreadState());
      if (threadInfo.getLockName() != null) {
         sb.append(" on " + threadInfo.getLockName());
      }
      if (threadInfo.getLockOwnerName() != null) {
         sb.append(" owned by \"" + threadInfo.getLockOwnerName() +
               "\" Id=" + threadInfo.getLockOwnerId());
      }
      if (threadInfo.isSuspended()) {
         sb.append(" (suspended)");
      }
      if (threadInfo.isInNative()) {
         sb.append(" (in native)");
      }
      sb.append('\n');
      int i = 0;
      for (; i < threadInfo.getStackTrace().length; i++) {
         StackTraceElement ste = threadInfo.getStackTrace()[i];
         sb.append("\tat " + ste.toString());
         sb.append('\n');
         if (i == 0 && threadInfo.getLockInfo() != null) {
            Thread.State ts = threadInfo.getThreadState();
            switch (ts) {
               case BLOCKED:
                  sb.append("\t-  blocked on " + threadInfo.getLockInfo());
                  sb.append('\n');
                  break;
               case WAITING:
               case TIMED_WAITING:
                  sb.append("\t-  waiting on " + threadInfo.getLockInfo());
                  sb.append('\n');
                  break;
               default:
            }
         }

         for (MonitorInfo mi : threadInfo.getLockedMonitors()) {
            if (mi.getLockedStackDepth() == i) {
               sb.append("\t-  locked " + mi);
               sb.append('\n');
            }
         }
      }

      LockInfo[] locks = threadInfo.getLockedSynchronizers();
      if (locks.length > 0) {
         sb.append("\n\tNumber of locked synchronizers = " + locks.length);
         sb.append('\n');
         for (LockInfo li : locks) {
            sb.append("\t- " + li);
            sb.append('\n');
         }
      }
      sb.append('\n');
      return sb.toString();
   }

}
