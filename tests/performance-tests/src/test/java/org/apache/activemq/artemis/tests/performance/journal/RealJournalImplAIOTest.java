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
package org.apache.activemq.artemis.tests.performance.journal;

import java.io.File;

import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.io.aio.AIOSequentialFileFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class RealJournalImplAIOTest extends JournalImplTestUnit {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @BeforeAll
   public static void hasAIO() {
      org.junit.jupiter.api.Assumptions.assumeTrue(AIOSequentialFileFactory.isSupported(), "Test case needs AIO to run");
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
   }

   @Override
   protected SequentialFileFactory getFileFactory() throws Exception {
      File file = new File(getTestDir());

      RealJournalImplAIOTest.logger.debug("deleting directory {}", file);

      deleteDirectory(file);

      file.mkdir();

      return new AIOSequentialFileFactory(getTestDirfile(), 1);
   }

}
