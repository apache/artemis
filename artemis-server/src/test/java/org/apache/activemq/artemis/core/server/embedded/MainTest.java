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
package org.apache.activemq.artemis.core.server.embedded;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainTest {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @TempDir
   Path tempDir;

   private Thread serverThread;

   @AfterEach
   public void stopServer() throws Exception {
      EmbeddedActiveMQ server = Main.getEmbeddedServer();
      if (server != null) {
         // Happens if the startup succeeds (unexpectedly or not) and the test does not stop it, e.g during
         // a test timeout. Clean up to avoid impacting later tests.
         try {
            server.stop();
         } catch (Throwable t) {
            // Log but suppress, allowing original issue be reported as failure.
            logger.warn("Caught issue while stopping the server", t);
         }
      }
      if (serverThread != null) {
         serverThread.join(5000);
      }
   }

   /*
    * Tests what happens when an empty workdir arg is given, expect to throw IllegalArgumentException.
    */
   @Test
   @Timeout(5)
   public void testNull() throws Exception {
      try {
         Main.main(new String[] {""});
         fail("Should have thrown an exception");
      } catch (IllegalArgumentException expected) {
         // Expected
         logger.debug("Caught expected IllegalArgumentException: {}", expected.getMessage());
      } finally {
         EmbeddedActiveMQ server = Main.getEmbeddedServer();
         if (server != null) {
            // Happens if the startup succeeds unexpectedly, but is
            // interrupted before return, e.g during a test timeout.
            // Clean up to avoid impacting later tests.
            try {
               server.stop();
            } catch (Throwable t) {
               // Log but suppress, allowing original issue be reported as failure.
               logger.warn("Caught issue while stopping the unexpectedly-present server", t);
            }
         }
      }
   }

   /*
    * Tests what happens when a null workdir arg is given, expect to throw IllegalArgumentException.
    */
   @Test
   @Timeout(5)
   public void testNullWorkDir() throws Exception {
      IllegalArgumentException expected = assertThrows(IllegalArgumentException.class, () -> Main.main(new String[] {null}));
      logger.debug("Caught expected IllegalArgumentException: {}", expected.getMessage());
   }

   /*
    * Tests what happens when a blank workdir arg is given together with a properties path, expect to throw
    * IllegalArgumentException. Fails if trim() is removed in Main.java check.
    */
   @Test
   @Timeout(5)
   public void testBlankWorkDirWithPropertiesPath() throws Exception {
      IllegalArgumentException expected = assertThrows(IllegalArgumentException.class, () -> Main.main(new String[] {"  ", tempDir.toString()}));
      logger.debug("Caught expected IllegalArgumentException: {}", expected.getMessage());
   }

   /*
    * Tests what happens when an empty properties path arg is given, expect to throw IllegalArgumentException.
    */
   @Test
   @Timeout(5)
   public void testEmptyPropertiesPath() throws Exception {
      IllegalArgumentException expected = assertThrows(IllegalArgumentException.class, () -> Main.main(new String[] {tempDir.toString(), ""}));
      logger.debug("Caught expected IllegalArgumentException: {}", expected.getMessage());
   }

   /*
    * Tests what happens when a null properties path arg is given, expect to throw IllegalArgumentException.
    */
   @Test
   @Timeout(5)
   public void testNullPropertiesPath() throws Exception {
      IllegalArgumentException expected = assertThrows(IllegalArgumentException.class, () -> Main.main(new String[] {tempDir.toString(), null}));
      logger.debug("Caught expected IllegalArgumentException: {}", expected.getMessage());
   }

   /*
    * Tests what happens when a blank properties path arg is given, expect to throw IllegalArgumentException.
    * Fails if trim() is removed in Main.java check.
    */
   @Test
   @Timeout(5)
   public void testBlankPropertiesPath() throws Exception {
      IllegalArgumentException expected = assertThrows(IllegalArgumentException.class, () -> Main.main(new String[] {tempDir.toString(), "  "}));
      logger.debug("Caught expected IllegalArgumentException: {}", expected.getMessage());
   }

   @Test
   @Timeout(5)
   public void testThreeArguments() throws Exception {
      String workDir = tempDir.toString();
      IllegalArgumentException expected = assertThrows(IllegalArgumentException.class, () -> Main.main(new String[] {workDir, workDir, workDir}));
      logger.debug("Caught expected IllegalArgumentException: {}", expected.getMessage());
   }

   /*
    * Tests that the data directories are created under the supplied workdir.
    */
   @Test
   @Timeout(20)
   public void testWorkDir() throws Exception {
      assumeNoDefaultConfigDir();
      String workDir = Files.createDirectories(tempDir.resolve("work")).toString();

      Configuration config = startMain(workDir);

      String dataDir = workDir + "/data";
      assertEquals(dataDir, config.getJournalDirectory());
      assertEquals(dataDir + "/bindings", config.getBindingsDirectory());
      assertEquals(dataDir + "/largemessages", config.getLargeMessagesDirectory());
      assertEquals(dataDir + "/paging", config.getPagingDirectory());
   }

   /*
    * Tests that with a single argument the properties files in workdir/etc are applied.
    */
   @Test
   @Timeout(20)
   public void testWorkDirEtcProperties() throws Exception {
      assumeNoDefaultConfigDir();
      Path workDir = Files.createDirectories(tempDir.resolve("work"));
      Path etcDir = Files.createDirectories(workDir.resolve("etc"));
      Files.writeString(etcDir.resolve("broker.properties"), "name=from-workdir-etc\n");

      Configuration config = startMain(workDir.toString());

      assertEquals("from-workdir-etc", config.getName());
   }

   /*
    * Tests that with two arguments the workdir is still applied and the properties from the supplied path are used.
    */
   @Test
   @Timeout(20)
   public void testWorkDirAndPropertiesConfigPath() throws Exception {
      String workDir = Files.createDirectories(tempDir.resolve("work")).toString();
      Path propertiesDir = Files.createDirectories(tempDir.resolve("properties"));
      Files.writeString(propertiesDir.resolve("broker.properties"), "name=from-properties-path\n");

      Configuration config = startMain(workDir, propertiesDir + "/");

      assertEquals(workDir + "/data", config.getJournalDirectory());
      assertEquals("from-properties-path", config.getName());
   }

   /*
    * Tests that a supplied properties path replaces the default, so properties in workdir/etc are ignored.
    */
   @Test
   @Timeout(20)
   public void testPropertiesConfigPathReplacesWorkDirEtc() throws Exception {
      Path workDir = Files.createDirectories(tempDir.resolve("work"));
      Path etcDir = Files.createDirectories(workDir.resolve("etc"));
      Files.writeString(etcDir.resolve("broker.properties"), "name=from-workdir-etc\n");
      Path propertiesDir = Files.createDirectories(tempDir.resolve("properties"));
      Files.writeString(propertiesDir.resolve("broker.properties"), "name=from-properties-path\n");

      Configuration config = startMain(workDir.toString(), propertiesDir + "/");

      assertEquals("from-properties-path", config.getName());
   }

   /*
    * With a single argument Main also reads properties from the absolute /config/ directory, which is outside the
    * test's control, so skip when it exists on this machine.
    */
   private static void assumeNoDefaultConfigDir() {
      assumeFalse(Files.exists(Path.of("/config")), "/config exists on this machine and would be applied by Main");
   }

   private Configuration startMain(String... args) {
      serverThread = new Thread(() -> {
         try {
            Main.main(args);
         } catch (Exception e) {
            logger.warn("Error executing Main.main", e);
         }
      }, "artemis-main-test");
      serverThread.start();

      Wait.assertTrue("Server failed to start", () -> {
         EmbeddedActiveMQ server = Main.getEmbeddedServer();
         return server != null && server.getActiveMQServer() != null && server.getActiveMQServer().isStarted();
      }, 15_000);

      return Main.getEmbeddedServer().getActiveMQServer().getConfiguration();
   }
}
