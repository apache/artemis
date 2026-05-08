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

package org.apache.activemq.artemis.cli.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.cli.Shell;
import org.apache.activemq.artemis.tests.util.ArtemisTestCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ShellTest extends ArtemisTestCase {

   @TempDir
   File temporaryFolder;

   @Test
   public void testDefaultAllowHistory() throws Exception {
      testDefaultHistory(true);
   }

   @Test
   public void testDefaultDontAllowHistory() throws Exception {
      testDefaultHistory(false);
   }

   public void testDefaultHistory(boolean allowHistory) throws Exception {
      System.setProperty("artemis.instance", temporaryFolder.getAbsolutePath());
      File etcFolder = new File(temporaryFolder, "etc");
      assertTrue(etcFolder.mkdirs());

      String input;
      if (allowHistory) {
         input = "Y\n";
      } else {
         input = "N\n";
      }

      File shellHistory = new File(etcFolder, Shell.DEFAULT_HISTORY_FILE);
      executeShell(input, shellHistory);

      if (!allowHistory) {
         String historyContent = new String(Files.readAllBytes(shellHistory.toPath()), StandardCharsets.UTF_8);
         assertTrue(historyContent.contains("NO_HISTORY"), "History file should contain NO_HISTORY marker");
      }
   }

   private static void executeShell(String input, File outputHistory) throws InterruptedException, IOException {
      ByteArrayInputStream inputStream = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));

      try {
         CountDownLatch shellFinished = new CountDownLatch(1);

         Thread shellThread = new Thread(() -> {
            InputStream originlInput = System.in;
            try {
               // Run the shell with custom streams for testing
               System.setIn(inputStream);
               Shell.runShell(false, null, inputStream);
            } finally {
               shellFinished.countDown();
               System.setIn(originlInput);
            }
         });

         shellThread.start();

         // Wait for shell to finish
         assertTrue(shellFinished.await(30, TimeUnit.SECONDS), "Shell did not finish in time");
         shellThread.join(5000);

         assertTrue(outputHistory.exists());

         // Validate file permissions are 600 (owner read/write only)
         Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(outputHistory.toPath());
         assertTrue(permissions.contains(PosixFilePermission.OWNER_READ), "File should be readable by owner");
         assertTrue(permissions.contains(PosixFilePermission.OWNER_WRITE), "File should be writable by owner");
         // Explicitly verify group and others cannot read
         assertFalse(permissions.contains(PosixFilePermission.GROUP_READ), "File should not be readable by group");
         assertFalse(permissions.contains(PosixFilePermission.GROUP_WRITE), "File should not be writable by group");
         assertFalse(permissions.contains(PosixFilePermission.GROUP_EXECUTE), "File should not be executable by group");
         assertFalse(permissions.contains(PosixFilePermission.OTHERS_READ), "File should not be readable by others");
         assertFalse(permissions.contains(PosixFilePermission.OTHERS_WRITE), "File should not be writable by others");
         assertFalse(permissions.contains(PosixFilePermission.OTHERS_EXECUTE), "File should not be executable by others");

      } finally {
         System.clearProperty("artemis.instance");
      }
   }

}
