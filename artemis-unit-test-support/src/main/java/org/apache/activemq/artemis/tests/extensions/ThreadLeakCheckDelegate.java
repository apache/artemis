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
package org.apache.activemq.artemis.tests.extensions;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * This is useful to make sure you won't have leaking threads between tests
 */
public class ThreadLeakCheckDelegate {

   private static Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static Set<String> knownThreads = new HashSet<>();

   protected boolean enabled = true;

   protected Map<Thread, StackTraceElement[]> previousThreads;

   public void beforeTest() {
      previousThreads = Thread.getAllStackTraces();
   }

   public void disable() {
      enabled = false;
   }

   public void afterTest(Throwable failure, String nameOfTest, Consumer<String> failureCallback) {
      boolean testFailed = failure != null;

      logger.debug("checking thread enabled? {}, testFailed? {}", enabled, testFailed);

      try {
         if (enabled) {
            String failedThread = null;
            boolean failedOnce = false;
            int checks = 0;

            // if the test failed.. there's no point on waiting a full minute.. we will report it once and go
            int timeout = testFailed ? 0 : 60000;

            long deadline = System.currentTimeMillis() + timeout;
            do {
               failedThread = checkThread();

               if (failedThread != null) {
                  checks++;
                  if (timeout > 0 && !failedOnce) {
                     logger.warn("There are unexpected threads remaining. ThreadLeakCheckExtension will retry for {} milliseconds", timeout);
                  }
                  failedOnce = true;

                  forceGC();

                  if (timeout > 0) {
                     try {
                        Thread.sleep(500);
                     } catch (Throwable e) {
                     }
                  }
               }
            }
            while (failedThread != null && deadline > System.currentTimeMillis());

            if (failedThread != null) {
               logger.warn("There are leaked threads: \n{}", failedThread);
               if (!testFailed) {
                  //we only fail on thread leak if test passes.
                  failureCallback.accept("Thread leaked (see log)");
               } else {
                  logger.warn("The test failed and there is a thread leak (see log)", failure);
                  failure.printStackTrace();
                  failureCallback.accept("Test " + nameOfTest + " failed and there is a thread leak (see log) - " + failure.getMessage());
               }
            } else if (failedOnce) {
               logger.info("Threads were cleared after {} checks", checks);
            }
         } else {
            enabled = true;
         }
      } finally {
         // clearing just to help GC
         previousThreads = null;
      }

   }

   private static int failedGCCalls = 0;

   public static void forceGC() {

      if (failedGCCalls >= 10) {
         logger.info("ignoring forceGC call since it seems System.gc is not working anyways");
         return;
      }
      logger.info("#test forceGC");
      CountDownLatch finalized = new CountDownLatch(1);
      WeakReference<DumbReference> dumbReference = new WeakReference<>(new DumbReference(finalized));

      long timeout = System.currentTimeMillis() + 1000;

      // A loop that will wait GC, using the minimal time as possible
      while (!(dumbReference.get() == null && finalized.getCount() == 0) && System.currentTimeMillis() < timeout) {
         System.gc();
         System.runFinalization();
         try {
            finalized.await(100, TimeUnit.MILLISECONDS);
         } catch (InterruptedException e) {
         }
      }

      if (dumbReference.get() != null) {
         failedGCCalls++;
         logger.info("It seems that GC is disabled at your VM");
      } else {
         // a success would reset the count
         failedGCCalls = 0;
      }
      logger.info("#test forceGC Done ");
   }

   public static void forceGC(final Reference<?> ref, final long timeout) {
      long waitUntil = System.currentTimeMillis() + timeout;
      // A loop that will wait GC, using the minimal time as possible
      while (ref.get() != null && System.currentTimeMillis() < waitUntil) {
         List<String> list = new ArrayList<>();
         for (int i = 0; i < 1000; i++) {
            list.add("Some string with garbage with concatenation " + i);
         }
         list.clear();
         list = null;
         System.gc();
         try {
            Thread.sleep(500);
         } catch (InterruptedException e) {
         }
      }
   }

   public static void removeKownThread(String name) {
      knownThreads.remove(name);
   }

   public static void addKownThread(String name) {
      knownThreads.add(name);
   }

   private String checkThread() {
      boolean failedThread = false;

      StringWriter stringWriter = new StringWriter();
      PrintWriter writer = new PrintWriter(stringWriter);


      Map<Thread, StackTraceElement[]> postThreads = Thread.getAllStackTraces();

      if (postThreads != null && previousThreads != null && postThreads.size() > previousThreads.size()) {

         for (Thread aliveThread : postThreads.keySet()) {
            if (aliveThread.isAlive() && !isExpectedThread(aliveThread) && !previousThreads.containsKey(aliveThread)) {
               if (!failedThread) {
                  writer.println("*********************************************************************************");
                  writer.println("LEAKING THREADS");
               }
               failedThread = true;
               writer.println("=============================================================================");
               writer.println("Thread " + aliveThread + " is still alive with the following stackTrace:");
               StackTraceElement[] elements = postThreads.get(aliveThread);
               for (StackTraceElement el : elements) {
                  writer.println(el);
               }
               writer.println("*********************************************************************************");
               writer.println("ThreadGroup: " + aliveThread.getThreadGroup());

               aliveThread.interrupt();
            }

         }
         if (failedThread) {
            writer.println("*********************************************************************************");
            return stringWriter.toString();
         }
      }

      return null;
   }

   /**
    * if it's an expected thread... we will just move along ignoring it
    */
   private boolean isExpectedThread(Thread thread) {
      final String threadName = thread.getName();
      final ThreadGroup group = thread.getThreadGroup();
      final boolean isSystemThread = group != null && ("system".equals(group.getName()) || "InnocuousThreadGroup".equals(group.getName()));
      final String javaVendor = System.getProperty("java.vendor");

      if (threadName.contains("SunPKCS11")) {
         return true;
      } else if (threadName.contains("Keep-Alive-Timer")) {
         return true;
      } else if (threadName.contains("Attach Listener")) {
         return true;
      } else if ((javaVendor.contains("IBM") || isSystemThread) && threadName.startsWith("process reaper")) {
         return true;
      } else if ((javaVendor.contains("IBM") || isSystemThread) && threadName.equals("ClassCache Reaper")) {
         return true;
      } else if (javaVendor.contains("IBM") && threadName.equals("MemoryPoolMXBean notification dispatcher")) {
         return true;
      } else if (threadName.contains("MemoryMXBean")) {
         return true;
      } else if (threadName.contains("globalEventExecutor")) {
         return true;
      } else if (threadName.contains("activemq-remoting") || threadName.contains("activemq-client-remoting")) {
         // This is ok as we use EventLoopGroup.shutdownGracefully() which will shutdown things with a bit of delay
         // if the EventLoop's are still busy.
         return true;
      } else if (threadName.contains("threadDeathWatcher")) {
         //another netty thread
         return true;
      } else if (threadName.contains("Abandoned connection cleanup thread")) {
         // MySQL Engine checks for abandoned connections
         return true;
      } else if (threadName.contains("hawtdispatch") || (group != null && group.getName().contains("hawtdispatch"))) {
         // Static workers used by MQTT client.
         return true;
      } else if (threadName.contains("ObjectCleanerThread")) {
         // Required since upgrade to Netty 4.1.22 maybe because https://github.com/netty/netty/commit/739e70398ccb6b11ffa97c6b5f8d55e455a2165e
         return true;
      } else if (threadName.contains("RMI TCP")) {
         return true;
      } else if (threadName.contains("RMI Scheduler")) {
         return true;
      } else if (threadName.contains("RMI RenewClean")) {
         return true;
      } else if (threadName.contains("Signal Dispatcher")) {
         return true;
      } else if (threadName.contains("ForkJoinPool.commonPool")) {
         return true;
      } else if (threadName.contains("GC Daemon")) {
         return true;
      } else if (threadName.contains("junit-jupiter-timeout-watcher")) {
         return true;
      } else {
         // validating for known stack traces
         for (StackTraceElement element : thread.getStackTrace()) {
            if (element.getClassName().contains("org.jboss.byteman.agent.TransformListener") ||
                element.getClassName().contains("jdk.internal.ref.CleanerImpl")) {
               return true;
            }
         }

         for (String known : knownThreads) {
            if (threadName.contains(known)) {
               return true;
            }
         }

         return false;
      }
   }

   protected static class DumbReference {

      private CountDownLatch finalized;

      public DumbReference(CountDownLatch finalized) {
         this.finalized = finalized;
      }

      @Override
      public void finalize() throws Throwable {
         finalized.countDown();
         super.finalize();
      }
   }

}
