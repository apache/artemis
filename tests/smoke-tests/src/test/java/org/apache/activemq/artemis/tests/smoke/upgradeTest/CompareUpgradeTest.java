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

package org.apache.activemq.artemis.tests.smoke.upgradeTest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

import org.apache.activemq.artemis.cli.commands.ActionContext;
import org.apache.activemq.artemis.cli.commands.Create;
import org.apache.activemq.artemis.cli.commands.Upgrade;
import org.apache.activemq.artemis.utils.FileUtil;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This test will compare expected values at the upgraded servers.
 */
public class CompareUpgradeTest {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final String basedir = System.getProperty("basedir");


   @Test
   public void testCompareWindowsFreshInstance() throws Exception {
      String windows = basedir + "/target/classes/servers/windowsUpgrade";
      String windowsBin = windows + "/bin";
      String windowsETC = basedir + "/target/classes/servers/windowsUpgradeETC";

      String windowsExpected = basedir + "/target/classes/servers/windowsUpgradeExpected";
      String windowsExpectedBin = windowsExpected + "/bin";
      String windowsExpectedETC = basedir + "/target/classes/servers/windowsUpgradeETCExpected";

      compareDirectories(true, windowsExpectedBin, windowsBin);
      compareDirectories(true, windowsExpectedETC, windowsETC, "broker.xml", "artemis-users.properties", "management.xml");

      String referenceBin = basedir + "/target/reference-for-backup-check/servers/windowsUpgrade/bin";
      String referenceEtc = basedir + "/target/reference-for-backup-check/servers/windowsUpgradeETC";

      verifyBackupFiles(windows + "/old-config-bkp.0/bin", referenceBin, Create.ARTEMIS_CMD, Create.ARTEMIS_SERVICE_EXE, Create.ARTEMIS_SERVICE_EXE_CONFIG, Create.ARTEMIS_SERVICE_XML);
      verifyBackupFiles(windows + "/old-config-bkp.0/etc", referenceEtc, Create.ETC_ARTEMIS_PROFILE_CMD, Create.ETC_BOOTSTRAP_XML, Upgrade.OLD_LOGGING_PROPERTIES);
   }

   @Test
   public void testCompareLinuxFreshInstance() throws Exception {
      String linux = basedir + "/target/classes/servers/linuxUpgrade";
      String linuxBin = linux + "/bin";
      String linuxETC = basedir + "/target/classes/servers/linuxUpgradeETC";

      String linuxExpected = basedir + "/target/classes/servers/linuxUpgradeExpected";
      String linuxExpectedBin = linuxExpected + "/bin";
      String linuxExpectedETC = basedir + "/target/classes/servers/linuxUpgradeETCExpected";

      compareDirectories(true, linuxExpectedBin, linuxBin);
      compareDirectories(true, linuxExpectedETC, linuxETC, "broker.xml", "artemis-users.properties", "management.xml");

      String referenceBin = basedir + "/target/reference-for-backup-check/servers/linuxUpgrade/bin";
      String referenceEtc = basedir + "/target/reference-for-backup-check/servers/linuxUpgradeETC";

      verifyBackupFiles(linux + "/old-config-bkp.0/bin", referenceBin, Create.ARTEMIS, Create.ARTEMIS_SERVICE);
      verifyBackupFiles(linux + "/old-config-bkp.0/etc", referenceEtc, Create.ETC_ARTEMIS_PROFILE, Create.ETC_BOOTSTRAP_XML, Upgrade.OLD_LOGGING_PROPERTIES);
   }

   private void verifyBackupFiles(String backupFolder, String referenceFolder, String... files) throws Exception {
      assertTrue(files.length > 0, "Files to check must be specified");

      File bck = new File(backupFolder);
      if (!(bck.exists() && bck.isDirectory())) {
         fail("Backup folder does not exist at: " + bck.getAbsolutePath());
      }

      File[] backupFiles = bck.listFiles();
      assertNotNull(backupFiles, "Some backup files must exist");
      int backupFilesCount = backupFiles.length;
      assertTrue(backupFilesCount > 0, "Some backup files must exist");
      assertEquals(files.length, backupFilesCount, "Different number of backup files found than specified for inspection, update test if backup procedure changed");

      for (String f : files) {
         File bf = new File(backupFolder, f);
         if (!bf.exists()) {
            fail("Expected backup file does not exist at: " + bf.getAbsolutePath());
         }

         File reference = new File(referenceFolder, bf.getName());
         if (!reference.exists()) {
            fail("Reference file does not exist at: " + reference.getAbsolutePath());
         }

         assertArrayEquals(Files.readAllBytes(bf.toPath()), Files.readAllBytes(reference.toPath()), bf.getName() + " backup contents do not match reference file");
      }
   }

   private void compareDirectories(boolean allowExpectedWord, String expectedFolder, String upgradeFolder, String... ignoredFiles) throws Exception {
      File expectedFolderFile = new File(expectedFolder);
      File[] foundFiles = expectedFolderFile.listFiles(pathname -> {
         for (String i :ignoredFiles) {
            if (pathname.getName().contains(i)) {
               return false;
            }
         }
         return true;
      });


      File upgradeFolderFile = new File(upgradeFolder);

      for (File f : foundFiles) {
         File upgradeFile = new File(upgradeFolderFile, f.getName());
         if (!upgradeFile.exists()) {
            fail(upgradeFile.getAbsolutePath() + " not does exist");
         }


         if (f.getName().endsWith(".exe")) {
            assertArrayEquals(Files.readAllBytes(f.toPath()), Files.readAllBytes(upgradeFile.toPath()), f.getName() + " is different after upgrade");
         } else {
            compareFiles(allowExpectedWord, f, upgradeFile);
         }
      }
   }

   private static void compareFiles(boolean allowExpectedWord, File expectedFile, File upgradeFile) throws IOException {
      logger.debug("comparing: {} to {}", upgradeFile, upgradeFile);
      try (Stream<String> expectedStream = Files.lines(expectedFile.toPath());
           Stream<String> upgradeStream = Files.lines(upgradeFile.toPath())) {

         Iterator<String> expectedIterator = expectedStream.iterator();
         Iterator<String> upgradeIterator = upgradeStream.iterator();

         int line = 1;

         while (expectedIterator.hasNext()) {
            assertTrue(upgradeIterator.hasNext());

            String expectedString = expectedIterator.next().trim();
            String upgradeString = upgradeIterator.next().trim();

            // there's a test in this class that will use a different name ID. on that case we replace Expected by ""
            // on the comparison
            if (allowExpectedWord) {
               expectedString = expectedString.replace("Expected", "");
            }

            assertEquals(expectedString, upgradeString, "error on line " + line + " at " + upgradeFile);
            line++;
         }

         assertFalse(upgradeIterator.hasNext());
      }
   }

   @Test
   public void testWindows() throws Exception {
      final String windows = basedir + "/target/classes/servers/windowsUpgrade";
      final String windowsBin = windows + "/bin";
      final String windowsETC = basedir + "/target/classes/servers/windowsUpgradeETC";
      final String windowsData = windows + "/data-custom";
      final String hawtioRoles = "amq";

      checkExpectedValues(windowsBin + "/artemis.cmd", "set ARTEMIS_INSTANCE_ETC=", "\"" + windowsETC + "\"");
      Map<String, String> result = checkExpectedValues(windowsBin + "/artemis-service.xml",
                                                       "<env name=\"ARTEMIS_HOME\" value=", null, // no expected value for this, we will check on the output
                                                       "<env name=\"ARTEMIS_INSTANCE\" value=", "\"" + windows + "\"/>",
                                                       "<env name=\"ARTEMIS_INSTANCE_ETC\" value=", "\"" + windowsETC + "\"/>",
                                                       "<env name=\"ARTEMIS_DATA_DIR\" value=", "\"" + windowsData + "\"/>",
                                                       "<startargument>-Dhawtio.roles=", hawtioRoles + "</startargument>"
      );

      String home = result.get("<env name=\"ARTEMIS_HOME\" value=");
      assertNotNull(home);
      assertFalse(home.contains("must-change"), "home value must be changed during upgrade");

      Map<String, String> brokerProfileResult = checkExpectedValues(windowsETC + "/artemis.profile.cmd",
                                   "set ARTEMIS_HOME=", null, // no expected value for this, we will check on the output
                                   "set ARTEMIS_INSTANCE=", "\"" + windows + "\"",
                                   "set ARTEMIS_DATA_DIR=", "\"" + windowsData + "\"",
                                   "set ARTEMIS_OOME_DUMP=", "\"" + windows + "/log/oom_dump.hprof\"");

      home = brokerProfileResult.get("set ARTEMIS_HOME=");
      assertNotNull(home);
      assertFalse(home.contains("must-change"), "home value must be changed during upgrade");

      checkExpectedValues(windowsETC + "/bootstrap.xml",
                          "<server configuration=", "\"file:" + windowsETC + "//broker.xml\"/>");

      File oldLogging = new File(windowsETC + "/logging.properties");
      File newLogging = new File(windowsETC + "/log4j2.properties");

      assertFalse(oldLogging.exists(), "Old logging must be removed by upgrade");
      assertTrue(newLogging.exists(), "New Logging must be installed by upgrade");

      final String utilityProfilePath = windowsETC + "/artemis-utility.profile.cmd";
      final File utilityProfile = new File(utilityProfilePath);
      assertTrue(utilityProfile.exists(), "New utility profile must exist after upgrade");

      Map<String, String> utilityProfileResult = checkExpectedValues(utilityProfilePath,
            "set ARTEMIS_HOME=", null, // no expected value for this, we will check on the output
            "set ARTEMIS_INSTANCE=", "\"" + windows + "\"",
            "set ARTEMIS_DATA_DIR=", "\"" + windowsData + "\"");

      assertEquals(3, utilityProfileResult.size(), "Unexpected number of results");

      utilityProfileResult.forEach((key, value) -> {
         assertEquals(value, brokerProfileResult.get(key), "Unexpected difference between profile values for key: " + key);
      });
   }


   @Test
   public void testLinux() throws Exception {
      final String instanceDir = basedir + "/target/classes/servers/linuxUpgrade";
      final String bin = instanceDir + "/bin";
      final String etc = basedir + "/target/classes/servers/linuxUpgradeETC";
      final String data = instanceDir + "/data-custom";

      checkExpectedValues(bin + "/artemis", "ARTEMIS_INSTANCE_ETC=", "'" + etc + "'");

      Map<String, String> brokerProfileResult = checkExpectedValues(etc + "/artemis.profile",
                                                       "ARTEMIS_HOME=", null, // no expected value, will check on result
                                                       "ARTEMIS_INSTANCE=", "'" + instanceDir + "'",
                                                       "ARTEMIS_DATA_DIR=", "'" + data + "'",
                                                       "ARTEMIS_OOME_DUMP=", "'" + instanceDir + "/log/oom_dump.hprof'");

      String home = brokerProfileResult.get("ARTEMIS_HOME=");
      assertNotNull(home);
      assertNotEquals("'must-change'", home);

      File oldLogging = new File(etc + "/logging.properties");
      File newLogging = new File(etc + "/log4j2.properties");

      assertFalse(oldLogging.exists(), "Old logging must be removed by upgrade");
      assertTrue(newLogging.exists(), "New Logging must be installed by upgrade");

      final String utilityProfilePath = etc + "/artemis-utility.profile";
      final File utilityProfile = new File(utilityProfilePath);
      assertTrue(utilityProfile.exists(), "New utility profile must exist after upgrade");

      Map<String, String> utilityProfileResult = checkExpectedValues(utilityProfilePath,
            "ARTEMIS_HOME=", null, // no expected value, will check on result
            "ARTEMIS_INSTANCE=", "'" + instanceDir + "'",
            "ARTEMIS_DATA_DIR=", "'" + data + "'");

      assertEquals(3, utilityProfileResult.size(), "Unexpected number of results");

      utilityProfileResult.forEach((key, value) -> {
         assertEquals(value, brokerProfileResult.get(key), "Unexpected difference between profile values for key: " + key);
      });
   }

   @Test
   public void testCompareUpgradeCurrentVersion() throws Throwable {
      File upgradeConfig = new File(basedir + "/target/upgrade/currentVersion");
      File originalConfig = new File(basedir + "/target/upgrade/currentVersionCopy");
      FileUtil.deleteDirectory(originalConfig); // removing eventual previous runs

      // for previous runs
      removeBackups(upgradeConfig);

      // I'm keeping the current configuration as originalConfig, to make a comparison after upgrade is called
      FileUtil.copyDirectory(upgradeConfig, originalConfig);

      // looking up for the ARTEMIS_HOME from the profile file
      Properties properties = new Properties();
      properties.load(new FileInputStream(new File(originalConfig, "/etc/artemis.profile")));
      File home = new File(parseProperty(properties, "ARTEMIS_HOME"));

      Upgrade upgrade = new Upgrade();
      upgrade.setHomeValues(home, null, null);
      upgrade.setDirectory(upgradeConfig);
      upgrade.run(new ActionContext());

      // for current run
      removeBackups(upgradeConfig);

      // Calling upgrade on itself should not cause any changes to *any* file
      // output should be exactly the same
      assertTrue(compareDirectories(false, originalConfig.toPath(), upgradeConfig.toPath()));
   }

   private void removeBackups(File upgradeConfig) {
      File[] bkpFiles = upgradeConfig.listFiles(f -> f.isDirectory() && f.getName().contains("config-bkp"));
      for (File f : bkpFiles) {
         logger.debug("Removing {}", f);
         FileUtil.deleteDirectory(f);
      }
   }

   private static String parseProperty(Properties properties, String name) {
      String property = properties.getProperty(name);
      if (property == null) {
         return null;
      }
      // the property value might have quotes needed for bash. We need to remove those here
      if (property.startsWith("'") && property.endsWith("'")) {
         property = property.substring(1, property.length() - 1);
      }
      return property;
   }


   public static boolean compareDirectories(boolean allowExpectedWord, Path expected, Path upgrade) throws IOException {
      Files.walkFileTree(expected, new SimpleFileVisitor<>() {
         @Override
         public FileVisitResult visitFile(Path expectedFile, BasicFileAttributes attrs) throws IOException {
            FileVisitResult result = super.visitFile(expectedFile, attrs);

            Path relativize = expected.relativize(expectedFile);
            Path upgradeFile = upgrade.resolve(relativize);

            compareFiles(allowExpectedWord, expectedFile.toFile(), upgradeFile.toFile());

            return result;
         }
      });
      return true;
   }

   private Map<String, String> checkExpectedValues(String fileName, String... expectedPairs) throws Exception {
      assertTrue(expectedPairs.length > 0 && expectedPairs.length % 2 == 0, "You must pass a pair of expected values");
      Map<String, String> expectedValues = new HashMap<>();
      Map<String, String> matchingValues = new HashMap<>();

      for (int i = 0; i < expectedPairs.length; i += 2) {
         assertFalse(expectedValues.containsKey(expectedPairs[i]), "Value duplicated on pairs ::" + expectedPairs[i]);
         expectedValues.put(expectedPairs[i], expectedPairs[i + 1]);
      }

      File file = new File(fileName);
      try (Stream<String> lines = Files.lines(file.toPath())) {
         lines.forEach(line -> {
            String trimmedLine = line.trim();
            expectedValues.forEach((key, value) -> {
               if (trimmedLine.startsWith(key)) {
                  String actualValue = trimmedLine.substring(key.length());
                  logger.debug("match = {}", line);
                  matchingValues.put(key, actualValue);

                  if (value == null) {
                     logger.debug("no expected value was defined for {}, we will just fill out the matchingValues for further evaluation", key);
                  } else {
                     if (logger.isDebugEnabled()) {
                        logger.debug("prefix={}, expecting={}, actualValue={}", key, value, actualValue);
                     }
                     assertEquals(value, actualValue, key + " did not match");
                  }
               }
            });
         });
      }

      Object[] expectedKeys = expectedValues.keySet().stream().sorted().toArray();
      Object[] matchedKeys = matchingValues.keySet().stream().sorted().toArray();

      assertArrayEquals(expectedKeys, matchedKeys, "Some elements were not found in the output of " + fileName);
      assertEquals(matchingValues.size(), expectedValues.size(), "Some elements were not found in the output of " + fileName);

      return matchingValues;
   }
}
