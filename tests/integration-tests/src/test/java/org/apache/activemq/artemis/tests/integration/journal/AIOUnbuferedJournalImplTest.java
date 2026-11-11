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

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;

import org.apache.activemq.artemis.ArtemisConstants;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.io.aio.AIOSequentialFileFactory;
import org.apache.activemq.artemis.nativo.jlibaio.LibaioContext;
import org.apache.activemq.artemis.tests.unit.core.journal.impl.JournalImplTestUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

/**
 * A RealJournalImplTest you need to define {@code -Djava.library.path=${project-root}/native/src/.libs} when calling
 * the JVM If you are running this test in eclipse you should do:
 * <ol>
 * <li>Run->Open Run Dialog
 * <li>Find the class on the list (you will find it if you already tried running this testcase before)
 * <li>Add {@code -Djava.library.path=<your project place>/native/src/.libs}
 * </ol>
 */
public class AIOUnbuferedJournalImplTest extends JournalImplTestUnit {

   @Override
   protected boolean suportsRetention() {
      return false;
   }

   @BeforeAll
   public static void hasAIO() {
      assumeTrue(AIOSequentialFileFactory.isSupported(), "Test case needs AIO to run");
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      if (!LibaioContext.isLoaded()) {
         fail(String.format("libAIO is not loaded on %s %s %s", System.getProperty("os.name"), System.getProperty("os.arch"), System.getProperty("os.version")));
      }
   }

   @Override
   protected SequentialFileFactory getFileFactory() throws Exception {
      File file = new File(getTestDir());

      deleteDirectory(file);

      file.mkdir();

      // forcing the alignment to be 512, as this test was hard coded around this size.
      return new AIOSequentialFileFactory(getTestDirfile(), ArtemisConstants.DEFAULT_JOURNAL_BUFFER_SIZE_AIO, 0, 10, false).setAlignment(512);
   }

   @Override
   protected int getAlignment() {
      return 512;
   }

}
