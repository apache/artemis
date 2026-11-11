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

import static org.junit.jupiter.api.Assertions.fail;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.activemq.artemis.nativo.jlibaio.LibaioContext;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is useful to make sure you won't have LibaioContext between tests
 */
public class LibaioContextCheckExtension implements Extension, BeforeAllCallback, AfterAllCallback {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final int INTERVAL = 100;
   private static final int WAIT = 20_000;

   private static String previouslyFailedTotalMaxIoMessage;

   public LibaioContextCheckExtension() {
   }

   @Override
   public void beforeAll(ExtensionContext context) throws Exception {
      checkLibaioBefore(context.getRequiredTestClass().getName());
   }

   @Override
   public void afterAll(ExtensionContext context) throws Exception {
      checkLibaioAfter(context.getRequiredTestClass().getName());
   }

   public static void checkLibaioBefore(String testClassName) {
      if (previouslyFailedTotalMaxIoMessage != null) {
         // Fail immediately if this is already set.
         fail(previouslyFailedTotalMaxIoMessage);
      }

      long totalMaxIO = LibaioContext.getTotalMaxIO();
      if (totalMaxIO != 0) {
         failDueToLibaioContextCheck("LibaioContext TotalMaxIO > 0 leak detected BEFORE class %s, TotalMaxIO=%s. Check prior test classes for issue (not possible to be sure of which here).", testClassName, totalMaxIO);
      }
   }

   public static void checkLibaioAfter(String testClassName) {
      if (previouslyFailedTotalMaxIoMessage != null) {
         // Test class was already failed if this is set, nothing to do here.
         return;
      }

      try {
         if (!waitForLibaioContextTotalMaxIo(testClassName)) {
            final long totalMaxIO = LibaioContext.getTotalMaxIO();
            failDueToLibaioContextCheck("LibaioContext TotalMaxIO > 0 leak detected AFTER class %s, TotalMaxIO=%s.", testClassName, totalMaxIO);
         }
      } catch (Exception e) {
         fail("Exception while checking Libaio after tests in class: " + testClassName);
      }
   }

   private static boolean waitForLibaioContextTotalMaxIo(String testClassName) {
      AtomicBoolean firstCheck = new AtomicBoolean();
      return Wait.waitFor(() -> {
         final boolean totalIsZero = LibaioContext.getTotalMaxIO() == 0;
         if (!totalIsZero && firstCheck.compareAndSet(false, true)) {
            logger.info("Waiting for LibaioContext TotalMaxIO to become 0 after class {}", testClassName);
         }

         return totalIsZero;
      }, WAIT, INTERVAL);
   }

   private static void failDueToLibaioContextCheck(String currentFailureMessageFormat, String testClassName, long totalMaxIO) {
      // Set message to immediately-fail subsequent tests with
      previouslyFailedTotalMaxIoMessage = String.format("Aborting, LibaioContext TotalMaxIO > 0 issue previously detected by test class %s, see its output.", testClassName);

      // Now fail this run
      String message = String.format(currentFailureMessageFormat, testClassName, totalMaxIO);
      logger.error(message);

      fail(message);
   }
}
