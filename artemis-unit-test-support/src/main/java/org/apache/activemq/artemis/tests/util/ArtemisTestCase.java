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
package org.apache.activemq.artemis.tests.util;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;

import org.apache.activemq.artemis.tests.extensions.CleanupSystemPropertiesExtension;
import org.apache.activemq.artemis.tests.extensions.FilesLeftBehindCheckExtension;
import org.apache.activemq.artemis.tests.extensions.LogTestNameExtension;
import org.apache.activemq.artemis.tests.extensions.OpenFilesCheckExtension;
import org.apache.activemq.artemis.tests.extensions.ThreadLeakCheckExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parent class with basic utilities around creating unit etc test classes.
 */
@ExtendWith(LogTestNameExtension.class)
@ExtendWith(CleanupSystemPropertiesExtension.class)
public abstract class ArtemisTestCase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @RegisterExtension
   protected static ThreadLeakCheckExtension threadLeakCheckExtension = new ThreadLeakCheckExtension();

   @RegisterExtension
   protected static OpenFilesCheckExtension openFilesCheckExtension = new OpenFilesCheckExtension(1000);

   @RegisterExtension
   protected static FilesLeftBehindCheckExtension filesLeftBehindCheckExtension = new FilesLeftBehindCheckExtension("data", "null");

   private static String testClassName = "not-yet-set";

   public String name;

   public interface TestCompletionTask {
      void run() throws Exception;
   }

   private List<TestCompletionTask> runAfter;

   /**
    * Use this method to cleanup your resources by passing a TestCleanupTask.
    * <p>
    * Exceptions thrown from your tasks will just be logged and not passed as failures.
    *
    * @param completionTask A TestCleanupTask that will be passed, possibly from a lambda
    */
   protected void runAfter(TestCompletionTask completionTask) {
      assertNotNull(completionTask);
      runAfterEx(() -> {
         try {
            completionTask.run();
         } catch (Throwable e) {
            logger.warn("Lambda {} is throwing an exception", completionTask.toString(), e);
         }
      });
   }

   /**
    * Use this method to cleanup your resources and validating exceptional results by passing a TestCompletionTask.
    * <p>
    * An exception thrown from a task will be thrown to JUnit. If more than one task is present, all tasks will be be
    * executed, however only the exception of the first one will then be thrown the JUnit runner. All will be logged as
    * they occur.
    *
    * @param completionTask A TestCompletionTask that will be passed, possibly from a lambda method
    */
   protected synchronized void runAfterEx(TestCompletionTask completionTask) {
      assertNotNull(completionTask);
      if (runAfter == null) {
         runAfter = new ArrayList<>();
      }
      runAfter.add(completionTask);
   }

   @BeforeAll
   public static void doBeforeTestClass(TestInfo testInfo) {
      testClassName = testInfo.getTestClass().get().getName();
   }

   @BeforeEach
   public void doBeforeTestMethod(TestInfo testInfo) {
      name = testInfo.getTestMethod().get().getName();
   }

   @AfterEach
   public synchronized void doRunAfter() throws Throwable {
      List<Throwable> throwables = new ArrayList<>();
      List<TestCompletionTask> localRunAfter = runAfter;
      runAfter = null;
      if (localRunAfter != null) {
         localRunAfter.forEach((r) -> {
            try {
               r.run();
            } catch (Throwable e) {
               logger.warn(e.getMessage(), e);
               throwables.add(e);
            }
         });
      }

      if (!throwables.isEmpty()) {
         throw throwables.get(0);
      }
   }

   public static void forceGC() {
      ThreadLeakCheckExtension.forceGC();
   }

   public MBeanServer createMBeanServer() {
      MBeanServer mBeanServer = MBeanServerFactory.createMBeanServer();
      runAfter(() -> MBeanServerFactory.releaseMBeanServer(mBeanServer));
      return mBeanServer;
   }

   public String getTestMethodName() {
      return name;
   }

   public static String getTestClassName() {
      return testClassName;
   }
}
