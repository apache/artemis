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
package org.apache.activemq.artemis.cli.commands;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;

import org.apache.activemq.artemis.cli.commands.util.input.InputReader;
import org.apache.activemq.artemis.tests.extensions.TargetTempDirFactory;
import org.apache.activemq.artemis.utils.DefaultSensitiveStringCodec;
import org.apache.activemq.artemis.utils.PasswordMaskingUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.cli.test.TestActionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CreateTestIndividualSettings {

   @TempDir(factory = TargetTempDirFactory.class)
   public File temporaryFolder;

   public TestActionContext context;
   public File testInstance;

   @BeforeEach
   public void setUp() {
      context = new TestActionContext();
      testInstance = new File(temporaryFolder, "test-instance");
   }

   @Test
   public void testJournalMaxIo() throws Exception {
      int journalMaxIo = RandomUtil.randomInt();

      Create c = new Create();
      c.setAio(true);
      c.setJournalMaxIo(journalMaxIo);
      c.setInstance(testInstance);
      c.execute(context);

      assertTrue(fileContains(new File(testInstance, "etc/" + Create.ETC_BROKER_XML), "<journal-max-io>" + journalMaxIo + "</journal-max-io>"));
   }

   @Test
   public void testJournalMaxIoNegative() throws Exception {
      int journalMaxIo = RandomUtil.randomInt();

      Create c = new Create();
      c.setNio(true);
      c.setJournalMaxIo(journalMaxIo);
      c.setInstance(testInstance);
      c.execute(context);

      assertFalse(fileContains(new File(testInstance, "etc/" + Create.ETC_BROKER_XML), "<journal-max-io>" + journalMaxIo + "</journal-max-io>"));
      assertTrue(fileContains(new File(testInstance, "etc/" + Create.ETC_BROKER_XML), "<journal-max-io>1</journal-max-io>"));
   }

   private boolean fileContains(File file, String search) {
      try (BufferedReader br = new BufferedReader(new FileReader(file))) {
         String line;
         while ((line = br.readLine()) != null) {
            if (line.contains(search)) {
               return true;
            }
         }
      } catch (IOException e) {
      }
      return false;
   }

   @Test
   public void testMaskClusterPassword() throws Exception {
      final String password = RandomUtil.randomUUIDString();

      Create c = new Create();
      Create.enableInput();
      try {
         InputReader inputReader = Mockito.mock(InputReader.class);
         Mockito.when(inputReader.readPassword(Mockito.anyString())).thenReturn(password);
         c.setLineReader(inputReader);

         assertEquals(PasswordMaskingUtil.wrap(PasswordMaskingUtil.getDefaultCodec().encode(password)), c.getClusterPassword());
      } finally {
         Create.disableInput();
      }
   }

   @Test
   public void testMaskAdminPassword() throws Exception {
      final String password = RandomUtil.randomUUIDString();

      Create c = new Create();
      Create.enableInput();
      try {
         InputReader inputReader = Mockito.mock(InputReader.class);
         Mockito.when(inputReader.readPassword(Mockito.anyString())).thenReturn(password);
         c.setLineReader(inputReader);

         assertTrue(PasswordMaskingUtil.getDefaultCodec(Map.of(DefaultSensitiveStringCodec.ALGORITHM, DefaultSensitiveStringCodec.ONE_WAY)).verify(password.toCharArray(), PasswordMaskingUtil.unwrap(c.getPassword())));
      } finally {
         Create.disableInput();
      }
   }
}