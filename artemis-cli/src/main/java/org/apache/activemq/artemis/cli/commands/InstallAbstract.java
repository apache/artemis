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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.activemq.artemis.cli.CLIException;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

public class InstallAbstract extends InputAbstract {

   protected static final String LOG_DIRNAME = "log";
   protected static final String OOM_DUMP_FILENAME = "oom_dump.hprof";

   @Parameters(description = "The instance directory to hold the broker's configuration and data. Path must be writable.")
   protected File directory;

   @Option(names = "--etc", description = "Directory where Apache Artemis configuration is located. Paths can be absolute or relative to artemis.instance directory. Default: etc.")
   protected String etc = "etc";

   @Option(names = "--data", description = "Directory where Apache Artemis data is stored. Paths can be absolute or relative to artemis.instance directory. Default: data.")
   protected String data = "data";

   @Option(names = "--home", description = "Directory where Apache Artemis is installed.")
   protected File home;

   @Option(names = "--encoding", description = "The encoding that text files should use. Default: UTF-8.")
   protected String encoding = "UTF-8";

   @Option(names = "--windows", description = "Force Windows script creation. Default: based on your actual system.")
   protected boolean windows = false;

   @Option(names = {"--cygwin", "--linux"}, description = "Force Linux or Cygwin script creation. Default: based on your actual system.")
   protected boolean nix = false;

   @Option(names = "--java-options", description = "Extra Java options to be passed to the profile.")
   protected List<String> javaOptions;

   @Option(names = "--java-utility-options", description = "Extra Java options to be passed to the utility profile.")
   protected List<String> javaUtilityOptions;

   @Option(names = "--java-memory", description = "Define the -Xmx memory parameter for the broker. Default: 2G.")
   protected String javaMemory = "2G";

   // "calculated" during run
   protected boolean IS_WINDOWS;
   // "calculated" during run
   protected boolean IS_NIX;

   protected String getJavaOptions() {
      StringBuilder builder = new StringBuilder();
      if (javaOptions != null) {
         javaOptions.forEach(s -> builder.append(s).append(" "));
      }
      return builder.toString();
   }

   protected String getJavaUtilityOptions() {
      StringBuilder builder = new StringBuilder();
      if (javaUtilityOptions != null) {
         javaUtilityOptions.forEach(s -> builder.append(s).append(" "));
      }
      return builder.toString();
   }

   public String getEncoding() {
      return encoding;
   }

   public void setEncoding(String encoding) {
      this.encoding = encoding;
   }

   public File getInstance() {
      return directory;
   }

   public void setInstance(File directory) {
      this.directory = directory;
   }

   public File getHome() {
      if (home == null) {
         home = new File(getBrokerHome());
      }
      return home;
   }

   public File getDirectory() {
      return directory;
   }

   public InstallAbstract setDirectory(File directory) {
      this.directory = directory;
      return this;
   }

   public boolean isWindows() {
      return windows;
   }

   public InstallAbstract setWindows(boolean windows) {
      this.windows = windows;
      return this;
   }

   public boolean isNix() {
      return nix;
   }

   public InstallAbstract setNix(boolean nix) {
      this.nix = nix;
      return this;
   }

   public Object run(ActionContext context) throws Exception {
      IS_NIX = false;
      IS_WINDOWS = false;
      if (nix) {
         IS_NIX = true;
         return null;
      }
      if (windows) {
         IS_WINDOWS = true;
         return null;
      }
      if ("cygwin".equals(System.getenv("OSTYPE"))) {
         IS_NIX = true;
         return null;
      }
      if (System.getProperty("os.name").toLowerCase().trim().startsWith("win")) {
         IS_WINDOWS = true;
         return null;
      }
      // Fallback to *nix
      IS_NIX = true;
      return null;
   }

   protected static String applyFilters(String content, Map<String, String> filters) {
      if (filters != null) {
         for (Map.Entry<String, String> entry : filters.entrySet()) {
            content = replace(content, entry.getKey(), entry.getValue());
         }
      }
      return content;
   }

   protected static String replace(String content, String key, String value) {
      return content.replaceAll(Pattern.quote(key), Matcher.quoteReplacement(value));
   }

   protected static void copy(InputStream is, OutputStream os) throws IOException {
      byte[] buffer = new byte[1024 * 4];
      int c = is.read(buffer);
      while (c >= 0) {
         os.write(buffer, 0, c);
         c = is.read(buffer);
      }
   }

   protected void write(String source,
                      File target,
                      Map<String, String> filters,
                      boolean unixTarget, boolean force) throws Exception {
      if (target.exists() && !force) {
         throw new CLIException(String.format("The file '%s' already exists.  Use --force to overwrite.", target));
      }

      String content = readTextFile(source, filters);

      if (content == null) {
         new Exception(source + " not found").printStackTrace();
      }

      // and then writing out in the new target encoding..  Let's also replace \n with the values
      // that is correct for the current platform.
      String separator = unixTarget && IS_NIX ? "\n" : System.lineSeparator();
      content = content.replaceAll("\\r?\\n", Matcher.quoteReplacement(separator));
      ByteArrayInputStream in = new ByteArrayInputStream(content.getBytes(encoding));
      try (FileOutputStream fout = new FileOutputStream(target)) {
         copy(in, fout);
      }
   }

   protected static String readTextFile(String source, Map<String, String> filters) throws IOException {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      try (InputStream in = openStream(source)) {
         if (in == null) {
            throw new IOException("could not find resource " + source);
         }
         copy(in, out);
      }
      return applyFilters(new String(out.toByteArray(), StandardCharsets.UTF_8), filters);
   }

   protected void write(String source, boolean force) throws IOException {
      File target = new File(directory, source);
      if (target.exists() && !force) {
         throw new RuntimeException(String.format("The file '%s' already exists.  Use --force to overwrite.", target));
      }
      try (FileOutputStream fout = new FileOutputStream(target)) {
         try (InputStream in = openStream(source)) {
            copy(in, fout);
         }
      }
   }

   protected static InputStream openStream(String source) {
      return InputAbstract.class.getResourceAsStream(source);
   }
}
