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
import java.util.List;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extension to check for specific method name(s) about run.
 * <p>
 * Useful in subclasses where a determination must be made before the method, for use by utility code executed 'early'
 * due to BeforeEach activity triggered by a superclass, before any local BeforeEach implementation can run and check
 * the method name.
 */
public class TestMethodNameMatchExtension implements BeforeEachCallback, Extension {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final List<String> methodNames;
   private boolean matches = false;

   public TestMethodNameMatchExtension(String... methodNames) {
      this.methodNames = List.of(methodNames);

      if (this.methodNames.isEmpty()) {
         throw new IllegalArgumentException("At least one method name to match against must be specified");
      }
   }

   @Override
   public void beforeEach(ExtensionContext context) throws Exception {
      String testName = context.getRequiredTestMethod().getName();

      matches = methodNames.contains(testName);

      logger.trace("Match for test method {}: {}", context.getRequiredTestMethod().getName(), matches);
   }

   public boolean matches() {
      return matches;
   }
}
