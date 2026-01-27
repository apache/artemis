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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.activemq.artemis.util.JVMArgumentParser;
import picocli.CommandLine.Command;

@Command(name = "upgrade", description = "Update a broker instance to the current artemis.home, keeping all the data and broker.xml. Warning: backup your instance before using this command and compare the files.")
public class Upgrade extends InstallAbstract {

   // These are the JVM arguments we must keep between upgrades
   private static final String[] KEEPING_JVM_ARGUMENTS = new String[]{"-Xmx", "-Djava.security.auth.login.config", "-Dhawtio.roles="};

   // These are the renamed JVM arguments we must keep between upgrades
   // The keys MUST be one of the current KEEPING_JVM_ARGUMENTS args above, the values then being its old variant to be renamed.
   private static final Map<String, String> KEEPING_JVM_ARGUMENTS_ALTERNATES = Map.of("-Dhawtio.roles=", "-Dhawtio.role=");

   // this is the prefix where we can find the JDK arguments in Windows script
   private static final String JDK_PREFIX_WINDOWS = "IF \"%JAVA_ARGS%\"==\"\" (set JAVA_ARGS=";

   // this is the prefix where we can find the JDK arguments in Linux script
   private static final String JDK_PREFIX_LINUX = "JAVA_ARGS=";

   public static final String OLD_LOGGING_PROPERTIES = "logging.properties";

   /**
    * Checks that the directory provided either exists and is writable or doesn't exist but can be created.
    */
   protected void checkDirectory() {
      if (!directory.exists()) {
         throw new RuntimeException(String.format("Could not find path '%s' to upgrade.", directory));
      } else if (!directory.canWrite()) {
         throw new RuntimeException(String.format("The path '%s' is not writable.", directory));
      }
   }

   @Override
   public Object execute(ActionContext context) throws Exception {
      this.checkDirectory();
      super.execute(context);

      return run(context);
   }

   @Override
   public Object run(ActionContext context) throws Exception {
      super.run(context);
      context.out.println("*******************************************************************************************************************************");
      context.out.println("Upgrading broker instance " + directory + " to use artemis.home=" + getBrokerHome());

      final File bkpFolder = findBackup(context);

      final File binBkp = new File(bkpFolder, "bin");
      final File etcBkp = new File(bkpFolder, "etc");
      final File tmp = new File(bkpFolder, "tmp");
      Files.createDirectory(binBkp.toPath());
      Files.createDirectory(etcBkp.toPath());
      Files.createDirectory(tmp.toPath());

      final File bin = new File(directory, "bin");
      File etcFolder = new File(directory, etc);
      File dataFolder = new File(directory, data);
      File logFolder = new File(directory, LOG_DIRNAME);
      File oomeDumpFile = new File(logFolder, OOM_DUMP_FILENAME);

      final File artemisCmdScript = new File(bin, Create.ARTEMIS_CMD);
      final File artemisScript = new File(bin, Create.ARTEMIS);

      if (etc == null || etc.equals("etc")) {
         if (IS_WINDOWS) {
            String pattern = "set ARTEMIS_INSTANCE_ETC=";
            etcFolder = getETC(context, etcFolder, artemisCmdScript, pattern);
         } else {
            String pattern = "ARTEMIS_INSTANCE_ETC=";
            etcFolder = getETC(context, etcFolder, artemisScript, pattern);
         }
      }

      if (bin == null || !bin.exists()) { // it can't be null, just being cautious
         throw new IOException(bin + " does not exist for binary");
      }

      if (etcFolder == null || !etcFolder.exists()) { // it can't be null, just being cautious
         throw new IOException(etcFolder + " does not exist for etc");
      }

      Map<String, String> filters = new HashMap<>();
      Create.addScriptFilters(filters, getHome(), getInstance(), etcFolder, dataFolder, oomeDumpFile, javaMemory, getJavaOptions(), getJavaUtilityOptions(), "NA");

      if (IS_WINDOWS) {
         // recreating the service.exe and config in case we ever upgrade it
         final File serviceExe = new File(directory, Create.BIN_ARTEMIS_SERVICE_EXE);
         final File serviceExeBkp = new File(bkpFolder, Create.BIN_ARTEMIS_SERVICE_EXE);

         context.out.println("Copying " + serviceExe + " to " + serviceExeBkp);
         Files.copy(serviceExe.toPath(), serviceExeBkp.toPath(), StandardCopyOption.REPLACE_EXISTING);

         context.out.println("Updating " + serviceExe.toPath());
         write(Create.BIN_ARTEMIS_SERVICE_EXE, true);

         final File serviceExeConfig = new File(directory, Create.BIN_ARTEMIS_SERVICE_EXE_CONFIG);
         final File serviceExeConfigBkp = new File(bkpFolder, Create.BIN_ARTEMIS_SERVICE_EXE_CONFIG);
         if (serviceExeConfig.exists()) {
            // It didnt exist until more recently
            context.out.println("Copying " + serviceExeConfig + " to " + serviceExeConfigBkp);
            Files.copy(serviceExeConfig.toPath(), serviceExeConfigBkp.toPath(), StandardCopyOption.REPLACE_EXISTING);
         }

         context.out.println("Updating " + serviceExeConfig);
         write(Create.BIN_ARTEMIS_SERVICE_EXE_CONFIG, true);

         final File artemisCmdScriptTmp = new File(tmp, Create.ARTEMIS_CMD);
         final File artemisCmdScriptBkp = new File(binBkp, Create.ARTEMIS_CMD);

         write(Create.BIN_ARTEMIS_CMD, artemisCmdScriptTmp, filters, false, false);
         upgrade(context, artemisCmdScriptTmp, artemisCmdScript, artemisCmdScriptBkp, "set ARTEMIS_INSTANCE_ETC=");

         final File serviceXmlTmp = new File(tmp, Create.ARTEMIS_SERVICE_XML);
         final File serviceXml = new File(bin, Create.ARTEMIS_SERVICE_XML);
         final File serviceXmlBkp = new File(binBkp, Create.ARTEMIS_SERVICE_XML);

         final Map<String, String> keepPrefixAlternates = Map.of("<startargument>-Dhawtio.roles=", "<startargument>-Dhawtio.role=");

         write(Create.BIN_ARTEMIS_SERVICE_XML, serviceXmlTmp, filters, false, false);
         upgrade(context, serviceXmlTmp, serviceXml, serviceXmlBkp, keepPrefixAlternates,
                 "<env name=\"ARTEMIS_INSTANCE\"", "<env name=\"ARTEMIS_INSTANCE_ETC\"",
                 "<env name=\"ARTEMIS_DATA_DIR\"", "<logpath>", "<startargument>-Xmx",
                 "<name>", "<id>", "<startargument>-Dhawtio.roles=");

         final File artemisProfileCmdTmp = new File(tmp, Create.ETC_ARTEMIS_PROFILE_CMD);
         final File artemisProfileCmd = new File(etcFolder, Create.ETC_ARTEMIS_PROFILE_CMD);
         final File artemisProfileCmdBkp = new File(etcBkp, Create.ETC_ARTEMIS_PROFILE_CMD);

         write("etc/" + Create.ETC_ARTEMIS_PROFILE_CMD, artemisProfileCmdTmp, filters, false, false);
         upgradeJDK(context, JDK_PREFIX_WINDOWS, "", KEEPING_JVM_ARGUMENTS_ALTERNATES, KEEPING_JVM_ARGUMENTS, artemisProfileCmdTmp, artemisProfileCmd, artemisProfileCmdBkp,
                    Collections.emptyMap(), "set ARTEMIS_INSTANCE=\"", "set ARTEMIS_DATA_DIR=", "set ARTEMIS_OOME_DUMP=");

         File artemisUtilityProfileCmd = new File(etcFolder, Create.ETC_ARTEMIS_UTILITY_PROFILE_CMD);
         File artemisUtilityProfileCmdTmp = new File(tmp, Create.ETC_ARTEMIS_UTILITY_PROFILE_CMD);
         File artemisUtilityProfileCmdBkp = new File(etcBkp, Create.ETC_ARTEMIS_UTILITY_PROFILE_CMD);

         dataFolder = getDATA(context, dataFolder, artemisProfileCmd, "set ARTEMIS_DATA_DIR=");
         renameDataFiles(dataFolder);

         if (artemisUtilityProfileCmd.exists()) {
            write("etc/" + Create.ETC_ARTEMIS_UTILITY_PROFILE_CMD, artemisUtilityProfileCmdTmp, filters, false, false);
            upgradeJDK(context, JDK_PREFIX_WINDOWS, "", KEEPING_JVM_ARGUMENTS_ALTERNATES, KEEPING_JVM_ARGUMENTS, artemisUtilityProfileCmdTmp, artemisUtilityProfileCmd, artemisUtilityProfileCmdBkp,
                       Collections.emptyMap(), "set ARTEMIS_INSTANCE=\"", "set ARTEMIS_DATA_DIR=");
         } else {
            if (data == null || data.equals("data")) {
               Create.addScriptFilters(filters, getHome(), getInstance(), etcFolder, dataFolder, oomeDumpFile, javaMemory, getJavaOptions(), getJavaUtilityOptions(), "NA");
            }

            context.out.println("Creating " + artemisUtilityProfileCmd);
            write("etc/" + Create.ETC_ARTEMIS_UTILITY_PROFILE_CMD, artemisUtilityProfileCmd, filters, false, false);
         }
      }

      if (IS_NIX) {
         final File artemisScriptTmp = new File(tmp, Create.ARTEMIS);
         final File artemisScriptBkp = new File(binBkp, Create.ARTEMIS);

         write(Create.BIN_ARTEMIS, artemisScriptTmp, filters, false, false);
         upgrade(context, artemisScriptTmp, artemisScript, artemisScriptBkp, "ARTEMIS_INSTANCE_ETC=");

         final File artemisService = new File(bin, Create.ARTEMIS_SERVICE);
         final File artemisServiceTmp = new File(tmp, Create.ARTEMIS_SERVICE);
         final File artemisServiceBkp = new File(binBkp, Create.ARTEMIS_SERVICE);

         write(Create.BIN_ARTEMIS_SERVICE, artemisServiceTmp, filters, false, false);
         upgrade(context, artemisServiceTmp, artemisService, artemisServiceBkp); // we replace the whole thing

         final Map<String, String> keepPrefixAlternates = Map.of("HAWTIO_ROLES=", "HAWTIO_ROLE=");

         File artemisProfile = new File(etcFolder, Create.ETC_ARTEMIS_PROFILE);
         File artemisProfileTmp = new File(tmp, Create.ETC_ARTEMIS_PROFILE);
         File artemisProfileBkp = new File(etcBkp, Create.ETC_ARTEMIS_PROFILE);

         write("etc/" + Create.ETC_ARTEMIS_PROFILE, artemisProfileTmp, filters, false, false);
         upgradeJDK(context, JDK_PREFIX_LINUX, "\"", KEEPING_JVM_ARGUMENTS_ALTERNATES, KEEPING_JVM_ARGUMENTS, artemisProfileTmp, artemisProfile, artemisProfileBkp,
               keepPrefixAlternates, "ARTEMIS_INSTANCE=", "ARTEMIS_DATA_DIR=", "ARTEMIS_OOME_DUMP=", "HAWTIO_ROLES=");

         File artemisUtilityProfile = new File(etcFolder, Create.ETC_ARTEMIS_UTILITY_PROFILE);
         File artemisUtilityProfileTmp = new File(tmp, Create.ETC_ARTEMIS_UTILITY_PROFILE);
         File artemisUtilityProfileBkp = new File(etcBkp, Create.ETC_ARTEMIS_UTILITY_PROFILE);

         dataFolder = getDATA(context, dataFolder, artemisProfile, "ARTEMIS_DATA_DIR=");

         renameDataFiles(dataFolder);

         if (artemisUtilityProfile.exists()) {
            write("etc/" + Create.ETC_ARTEMIS_UTILITY_PROFILE, artemisUtilityProfileTmp, filters, false, false);
            upgradeJDK(context, JDK_PREFIX_LINUX, "\"", KEEPING_JVM_ARGUMENTS_ALTERNATES, KEEPING_JVM_ARGUMENTS, artemisUtilityProfileTmp, artemisUtilityProfile, artemisUtilityProfileBkp,
                  keepPrefixAlternates, "ARTEMIS_INSTANCE=", "ARTEMIS_DATA_DIR=");
         } else {
            if (data == null || data.equals("data")) {
               Create.addScriptFilters(filters, getHome(), getInstance(), etcFolder, dataFolder, oomeDumpFile, javaMemory, getJavaOptions(), getJavaUtilityOptions(), "NA");
            }

            context.out.println("Creating " + artemisUtilityProfile);
            write("etc/" + Create.ETC_ARTEMIS_UTILITY_PROFILE, artemisUtilityProfile, filters, false, false);
         }
      }

      final File bootstrapXml = new File(etcFolder, Create.ETC_BOOTSTRAP_XML);
      final File bootstrapXmlTmp = new File(tmp, Create.ETC_BOOTSTRAP_XML);
      final File bootstrapXmlBkp = new File(etcBkp, Create.ETC_BOOTSTRAP_XML);

      Files.copy(bootstrapXml.toPath(), bootstrapXmlTmp.toPath());
      replaceLines(context, bootstrapXmlTmp, bootstrapXml, bootstrapXmlBkp,
         "^(.*)<web path.*$", "$1<web path=\"web\" rootRedirectLocation=\"console\">",
         "^(.*)<binding uri=\"http://localhost:8161\"(.*)$", "$1<binding name=\"artemis\" uri=\"http://localhost:8161\"$2",
         "^(.*)<app url=(.*branding.*)$", "$1<app name=\"branding\" url=$2",
         "^(.*)<app url=(.*plugin.*)$", "$1<app name=\"plugin\" url=$2",
         "^(.*)<app url=\"([^\"]+)\"(.*)$", "$1<app name=\"$2\" url=\"$2\"$3");

      //we remove the unwanted wars after updating them above just in case we are upgrading from an older version where the format was different
      removeWars(context, bootstrapXml);

      upgradeLogging(context, etcFolder, etcBkp);

      context.out.println();
      context.out.println("*******************************************************************************************************************************");

      return null;
   }

   private File getETC(ActionContext context, File etcFolder, File cmd, String prefix) throws IOException {
      return getPathFromFile(context, etcFolder, cmd, prefix, "ETC");
   }

   private File getDATA(ActionContext context, File etcFolder, File profile, String prefix) throws IOException {
      return getPathFromFile(context, etcFolder, profile, prefix, "DATA");
   }

   private String getLine(File cmd, String pattern) throws IOException {
      try (Stream<String> lines = Files.lines(cmd.toPath())) {
         Iterator<String> iterator = lines.iterator();
         while (iterator.hasNext()) {
            String line = iterator.next();

            if (line.trim().startsWith(pattern)) {
               return line;
            }
         }
      }

      return null;
   }

   private File getPathFromFile(ActionContext context, File defaultPath, File file, String prefix, String name) throws IOException {
      String pathEntryLine = getLine(file, prefix);
      if (pathEntryLine != null) {
         String pathEntry = pathEntryLine.trim().substring(prefix.length() + 1, pathEntryLine.length() - 1);
         File path = new File(pathEntry);
         context.out.println(name + " found as " + path);

         return path;
      }

      return defaultPath;
   }

   private void upgradeJDK(ActionContext context, String jdkPrefix, String endOfLine, Map<String, String> keepArgumentAlternates, String[] keepArguments, File tmpFile, File targetFile, File bkpFile, Map<String, String> keepPrefixAlternates, String... keepingPrefixes) throws Exception {
      final HashMap<String, String> replaceMatrix = new HashMap<>();
      final HashMap<String, String> currentArguments = new HashMap<>();

      doUpgrade(context, tmpFile, targetFile, bkpFile,
                oldLine -> {
                   if (oldLine.trim().startsWith(jdkPrefix)) {
                      JVMArgumentParser.parseOriginalArgs(jdkPrefix, endOfLine, oldLine, keepArgumentAlternates, keepArguments, currentArguments);
                      return;
                   } else {
                      for (String prefix : keepingPrefixes) {
                         if (oldLine.trim().startsWith(prefix)) {
                            replaceMatrix.put(prefix, oldLine);
                         } else if (keepPrefixAlternates.containsKey(prefix)) {
                            String oldPrefix = keepPrefixAlternates.get(prefix);
                            if (oldLine.trim().startsWith(oldPrefix)) {
                               String renamedOldLine = oldLine.replaceFirst(oldPrefix, prefix);
                               replaceMatrix.put(prefix, renamedOldLine);
                            }
                         }
                      }
                   }
                },
                newLine -> {
                   if (newLine.trim().startsWith(jdkPrefix)) {
                      String result =  JVMArgumentParser.parseNewLine(jdkPrefix, endOfLine, newLine, keepArguments, currentArguments);
                      return result;
                   } else {
                      for (String prefix : keepingPrefixes) {
                         if (newLine.trim().startsWith(prefix)) {
                            String originalLine = replaceMatrix.get(prefix);
                            return originalLine;
                         }
                      }
                      return newLine;
                   }
                });
   }

   private void replaceLines(ActionContext context, File tmpFile, File targetFile, File bkpFile, String... replacePairs) throws Exception {
      doUpgrade(context, tmpFile, targetFile, bkpFile,
                null,
                newLine -> {
                   String replacedLine = newLine;
                   for (int i = 0; i < replacePairs.length; i += 2) {
                      if (replacedLine.matches(replacePairs[i])) {
                         replacedLine = replacedLine.replaceAll(replacePairs[i], replacePairs[i + 1]);
                      }
                   }
                   return replacedLine;
                });
   }

   private void upgrade(ActionContext context, File tmpFile, File targetFile, File bkpFile, String... keepingPrefixes) throws Exception {
      upgrade(context, tmpFile, targetFile, bkpFile, Collections.emptyMap(), keepingPrefixes);
   }

   private void upgrade(ActionContext context, File tmpFile, File targetFile, File bkpFile, Map<String, String> keepPrefixAlternates, String... keepingPrefixes) throws Exception {
      Map<String, String> replaceMatrix = new HashMap<>();

      doUpgrade(context, tmpFile, targetFile, bkpFile,
              oldLine -> {
                 if (keepingPrefixes.length > 0) {
                    for (String prefix : keepingPrefixes) {
                       if (oldLine.trim().startsWith(prefix)) {
                          replaceMatrix.put(prefix, oldLine);
                       } else if (keepPrefixAlternates.containsKey(prefix)) {
                          String oldPrefix = keepPrefixAlternates.get(prefix);
                          if (oldLine.trim().startsWith(oldPrefix)) {
                             String renamedOldLine = oldLine.replaceFirst(oldPrefix, prefix);
                             replaceMatrix.put(prefix, renamedOldLine);
                          }
                       }
                    }
                 }
              },
            newLine -> {
               if (keepingPrefixes.length > 0) {
                  for (String prefix : keepingPrefixes) {
                     if (newLine.trim().startsWith(prefix)) {
                        String originalLine = replaceMatrix.get(prefix);
                        return originalLine;
                     }
                  }
               }
               return newLine;
            });
   }

   private void doUpgrade(ActionContext context, File tmpFile, File targetFile, File bkpFile, Consumer<String> originalConsumer, Function<String, String> targetFunction) throws Exception {
      context.out.println("Copying " + targetFile + " to " + bkpFile);
      Files.copy(targetFile.toPath(), bkpFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

      // we first scan the original lines on the originalConsumer, giving a chance to the caller to fill out the original matrix
      if (originalConsumer != null) {
         try (Stream<String> originalLines = Files.lines(targetFile.toPath())) {
            originalLines.forEach(line -> {
               originalConsumer.accept(line);
            });
         }
      }

      context.out.println("Updating " + targetFile);

      // now we open the new file from the tmp, and we will give a chance for the targetFunction to replace lines from a matrix
      try (Stream<String> lines = Files.lines(tmpFile.toPath());
           PrintStream streamOutput = new PrintStream(new FileOutputStream(targetFile))) {

         Iterator<String> linesIterator = lines.iterator();
         while (linesIterator.hasNext()) {
            String line = linesIterator.next();
            line = targetFunction.apply(line);
            if (line != null) {
               streamOutput.println(line);
            }
         }
      }
   }

   private void upgradeLogging(ActionContext context, File etcFolder, File bkpFolder) throws Exception {
      File oldLogging = new File(etcFolder, OLD_LOGGING_PROPERTIES);

      if (oldLogging.exists()) {
         File oldLoggingCopy = new File(bkpFolder, OLD_LOGGING_PROPERTIES);
         context.out.println("Copying " + oldLogging.toPath() + " to " + oldLoggingCopy.toPath());

         Files.copy(oldLogging.toPath(), oldLoggingCopy.toPath(), StandardCopyOption.REPLACE_EXISTING);

         context.out.println("Removing " + oldLogging.toPath());
         if (!oldLogging.delete()) {
            context.out.println(oldLogging.toPath() + " could not be removed!");
         }

         File newLogging = new File(etcFolder, Create.ETC_LOG4J2_PROPERTIES);
         if (!newLogging.exists()) {
            context.out.println("Creating " + newLogging);
            try (InputStream inputStream = openStream("etc/" + Create.ETC_LOG4J2_PROPERTIES);
                 OutputStream outputStream = new FileOutputStream(newLogging)) {
               copy(inputStream, outputStream);
            }
         }
      }

      File newUtilityLogging = new File(etcFolder, Create.ETC_LOG4J2_UTILITY_PROPERTIES);
      if (!newUtilityLogging.exists()) {
         context.out.println("Creating " + newUtilityLogging);
         try (InputStream inputStream = openStream("etc/" + Create.ETC_LOG4J2_UTILITY_PROPERTIES);
              OutputStream outputStream = new FileOutputStream(newUtilityLogging)) {
            copy(inputStream, outputStream);
         }
      }
   }

   protected File findBackup(ActionContext context) throws IOException {
      for (int bkp = 0; bkp < 10; bkp++) {
         File bkpFolder = new File(directory, "old-config-bkp." + bkp);
         if (!bkpFolder.exists()) {
            Files.createDirectory(bkpFolder.toPath());
            context.out.println("Using " + bkpFolder.getAbsolutePath() + " as a backup folder for the modified files");
            return bkpFolder;
         }
      }
      throw new RuntimeException("Too many backup folders in place already. Please remove some of the old-config-bkp.* folders");
   }

   private void removeWars(ActionContext context, File bootstrapXml) throws Exception {
      StringBuilder sb = new StringBuilder();
      boolean remove = false;
      try (Stream<String> lines = Files.lines(bootstrapXml.toPath())) {
         Iterator<String> linesIterator = lines.iterator();
         while (linesIterator.hasNext()) {
            String line = linesIterator.next();
            if (line.matches("^(.*)<app name=(.*branding.*)$")) {
               context.out.println("removing branding war as no longer needed");
               remove = true;
            } else if (line.matches("^(.*)<app name=(.*plugin.*)$")) {
               context.out.println("removing plugin war as no longer needed");
               remove = true;
            } else {
               sb.append(line).append(System.lineSeparator());
            }
         }
      }
      if (remove) {
         try (InputStream inputStream = new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.UTF_8));
              OutputStream outputStream = new FileOutputStream(bootstrapXml)) {
            copy(inputStream, outputStream);
         }
      }

   }

   private void renameDataFiles(File dataFolder) throws Exception {

      File journalFilesDirectory = new File(dataFolder, "journal");
      File bindingsFilesDirectory = new File(dataFolder, "bindings");

      for (File file : journalFilesDirectory.listFiles()) {
         String filename = file.getAbsolutePath();
         if (filename.endsWith(".amq")) {
            filename = filename.replace("activemq", "artemis");
            File newFile = new File(filename);
            file.renameTo(newFile);
         }
      }

      for (File file : bindingsFilesDirectory.listFiles()) {
         String filename = file.getAbsolutePath();
         if (filename.endsWith(".bindings")) {
            filename = filename.replace("activemq", "artemis");
            File newFile = new File(filename);
            file.renameTo(newFile);
         }
      }

   }
}
