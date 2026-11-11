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

import java.io.File;
import java.lang.invoke.MethodHandles;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is useful to make sure you won't have leaking files/directories between tests
 */
public class FilesLeftBehindCheckExtension implements Extension, BeforeAllCallback, AfterAllCallback {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final String[] filesToCheck;

   public FilesLeftBehindCheckExtension(String... filesToCheck) {
      this.filesToCheck = filesToCheck;
   }

   private File checkFiles() {
      for (String f : filesToCheck) {
         File fileCheck = new File(f);
         if (fileCheck.exists()) {
            return fileCheck;
         }
      }

      return null;
   }

   @Override
   public void beforeAll(ExtensionContext context) throws Exception {
      File leaked = checkFiles();
      if (leaked != null) {
         fail("A previous test (unknown) left a file/directory around: " + leaked.getAbsolutePath());
      }
   }

   @Override
   public void afterAll(ExtensionContext context) throws Exception {
      String testName = context.getRequiredTestClass().getName();

      logger.debug("Checking files left behind after {}", testName);

      File leaked = checkFiles();
      if (leaked != null) {
         fail("A file/directory is being left behind by " + testName + ": " + leaked.getAbsolutePath());
      }
   }
}
