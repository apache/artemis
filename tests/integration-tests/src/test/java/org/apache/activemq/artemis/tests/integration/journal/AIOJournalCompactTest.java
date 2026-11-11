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
package org.apache.activemq.artemis.tests.integration.journal;

import java.io.File;

import org.apache.activemq.artemis.ArtemisConstants;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.io.aio.AIOSequentialFileFactory;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeAll;

public class AIOJournalCompactTest extends NIOJournalCompactTest {

   @BeforeAll
   public static void hasAIO() {
      org.junit.jupiter.api.Assumptions.assumeTrue(AIOSequentialFileFactory.isSupported(), "Test case needs AIO to run");
   }

   @Override
   protected SequentialFileFactory getFileFactory() throws Exception {
      File file = new File(getTestDir());

      ActiveMQTestBase.deleteDirectory(file);

      file.mkdir();

      return new AIOSequentialFileFactory(getTestDirfile(), ArtemisConstants.DEFAULT_JOURNAL_BUFFER_SIZE_AIO, 100000, 10, false);
   }
}
