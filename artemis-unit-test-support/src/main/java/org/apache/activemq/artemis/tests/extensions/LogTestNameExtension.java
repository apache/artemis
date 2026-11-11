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

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.List;

import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.junit.platform.commons.support.AnnotationSupport;
import org.junit.platform.commons.support.HierarchyTraversalMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogTestNameExtension implements BeforeAllCallback, BeforeEachCallback, AfterEachCallback, TestWatcher, Extension {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private boolean parameterizedTestClass = false;

   @Override
   public void beforeAll(ExtensionContext context) throws Exception {
      final List<Method> parameterProviders = AnnotationSupport.findAnnotatedMethods(context.getRequiredTestClass(), Parameters.class, HierarchyTraversalMode.TOP_DOWN);

      parameterizedTestClass = !parameterProviders.isEmpty();
   }

   @Override
   public void beforeEach(ExtensionContext context) throws Exception {
      final String testClass = context.getRequiredTestClass().getSimpleName();
      final String testMethod = context.getRequiredTestMethod().getName();

      if (parameterizedTestClass) {
         logger.info("*** start test {} {}() {} ***", testClass, testMethod, context.getDisplayName());
      } else {
         logger.info("*** start test {} {}() ***", testClass, testMethod);
      }
   }

   @Override
   public void afterEach(ExtensionContext context) throws Exception {
      final String testClass = context.getRequiredTestClass().getSimpleName();
      final String testMethod = context.getRequiredTestMethod().getName();

      if (parameterizedTestClass) {
         logger.info("*** end test {} {}() {} ***", testClass, testMethod, context.getDisplayName());
      } else {
         logger.info("*** end test {} {}() ***", testClass, testMethod);
      }
   }

   @Override
   public void testFailed(ExtensionContext context, Throwable cause) {
      final String testMethod = context.getRequiredTestMethod().getName();

      if (parameterizedTestClass) {
         logger.error("### Failure in test {}() {} ###", testMethod, context.getDisplayName());
      } else {
         logger.error("### Failure in test {}() ###", testMethod);
      }
   }

   @Override
   public void testAborted(ExtensionContext context, Throwable cause) {
      final String testMethod = context.getRequiredTestMethod().getName();

      if (parameterizedTestClass) {
         logger.error("### Aborted test {}() {} ###", testMethod, context.getDisplayName());
      } else {
         logger.error("### Aborted test {}() ###", testMethod);
      }
   }
}
