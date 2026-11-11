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

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.extension.AnnotatedElementContext;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDirFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for use with TempDir JUnit 5 extension to control the parent dir to be ./target/tmp/.
 */
public class TargetTempDirFactory implements TempDirFactory {
   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final String TARGET_TMP = "./target/tmp";

   @Override
   public Path createTempDirectory(AnnotatedElementContext elementContext, ExtensionContext extensionContext) throws IOException {
      String testClassName = extensionContext.getRequiredTestClass().getSimpleName();

      Path parentDir = Paths.get(TARGET_TMP, testClassName);
      parentDir.toFile().mkdirs();

      Path createdTempDirectory = Files.createTempDirectory(parentDir, "junit");

      logger.trace("TargetTempDirFactory created temporary directory: {}", createdTempDirectory);

      return createdTempDirectory;
   }
}
