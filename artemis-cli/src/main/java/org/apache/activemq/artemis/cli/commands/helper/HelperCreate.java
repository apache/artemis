/*false
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

package org.apache.activemq.artemis.cli.commands.helper;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import org.apache.activemq.artemis.cli.Artemis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a class simulating what the Artemis Maven Plugin does. You may use by creating a new instance, filling the
 * properties, and calling the method create
 */
public class HelperCreate extends HelperBase {

   public HelperCreate(String homeProperty) {
      super(homeProperty);
   }

   public HelperCreate(File artemisHome) {
      super(artemisHome);
   }

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private File configuration;

   private String[] replacePairs;

   private boolean noWeb = true;

   private String user = "guest";

   private String password = "guest";

   private String role = "guest";

   private String javaOptions = "";

   private int portOffset = 0;

   private boolean allowAnonymous = true;

   private boolean replicated = false;

   private boolean sharedStore = false;

   private boolean clustered = false;

   private boolean backup = false;

   private String staticCluster;

   private boolean purgePageFolders;

   String dataFolder = "./data";

   private boolean useAIO = "Linux".equals(System.getProperty("os.name"));

   private boolean failoverOnShutdown = false;

   private boolean noAutoTune = true;

   private String messageLoadBalancing = "ON_DEMAND";

   public String[] getReplacePairs() {
      return replacePairs;
   }

   public HelperCreate setReplacePairs(String[] replacePairs) {
      this.replacePairs = replacePairs;
      return this;
   }

   public boolean isUseAIO() {
      return useAIO;
   }

   public HelperCreate setUseAIO(boolean useAIO) {
      this.useAIO = useAIO;
      return this;
   }

   public boolean isNoWeb() {
      return noWeb;
   }

   public HelperCreate setNoWeb(boolean noWeb) {
      this.noWeb = noWeb;
      return this;
   }

   public String getUser() {
      return user;
   }

   public HelperCreate setUser(String user) {
      this.user = user;
      return this;
   }

   public String getPassword() {
      return password;
   }

   public HelperCreate setPassword(String password) {
      this.password = password;
      return this;
   }

   public String getRole() {
      return role;
   }

   public HelperCreate setRole(String role) {
      this.role = role;
      return this;
   }

   public String getJavaOptions() {
      return javaOptions;
   }

   public HelperCreate setJavaOptions(String javaOptions) {
      this.javaOptions = javaOptions;
      return this;
   }

   public boolean isPurgePageFolders() {
      return purgePageFolders;
   }

   public void setPurgePageFolders(boolean purgePageFolders) {
      this.purgePageFolders = purgePageFolders;
   }

   public int getPortOffset() {
      return portOffset;
   }

   public HelperCreate setPortOffset(int portOffset) {
      this.portOffset = portOffset;
      return this;
   }

   public boolean isAllowAnonymous() {
      return allowAnonymous;
   }

   public HelperCreate setAllowAnonymous(boolean allowAnonymous) {
      this.allowAnonymous = allowAnonymous;
      return this;
   }

   public boolean isReplicated() {
      return replicated;
   }

   public HelperCreate setReplicated(boolean replicated) {
      this.replicated = replicated;
      return this;
   }

   public boolean isSharedStore() {
      return sharedStore;
   }

   public HelperCreate setSharedStore(boolean sharedStore) {
      this.sharedStore = sharedStore;
      return this;
   }

   public boolean isClustered() {
      return clustered;
   }

   public HelperCreate setClustered(boolean clustered) {
      this.clustered = clustered;
      return this;
   }

   public boolean isBackup() {
      return backup;
   }

   public HelperCreate setBackup(boolean backup) {
      this.backup = backup;
      return this;
   }

   public String getStaticCluster() {
      return staticCluster;
   }

   public HelperCreate setStaticCluster(String staticCluster) {
      this.staticCluster = staticCluster;
      return this;
   }

   public String getDataFolder() {
      return dataFolder;
   }

   public HelperCreate setDataFolder(String dataFolder) {
      this.dataFolder = new File(dataFolder).getAbsolutePath();
      return this;
   }

   public boolean isFailoverOnShutdown() {
      return failoverOnShutdown;
   }

   public HelperCreate setFailoverOnShutdown(boolean failoverOnShutdown) {
      this.failoverOnShutdown = failoverOnShutdown;
      return this;
   }

   public boolean isNoAutoTune() {
      return noAutoTune;
   }

   public HelperCreate setNoAutoTune(boolean noAutoTune) {
      this.noAutoTune = noAutoTune;
      return this;
   }

   public String getMessageLoadBalancing() {
      return messageLoadBalancing;
   }

   public HelperCreate setMessageLoadBalancing(String messageLoadBalancing) {
      this.messageLoadBalancing = messageLoadBalancing;
      return this;
   }

   @Override
   public HelperCreate setArgs(String... args) {
      return (HelperCreate) super.setArgs(args);
   }

   @Override
   public HelperCreate setArtemisHome(File artemisHome) {
      return (HelperCreate) super.setArtemisHome(artemisHome);
   }

   @Override
   public HelperCreate setArtemisInstance(File artemisInstance) {
      return (HelperCreate) super.setArtemisInstance(artemisInstance);
   }

   private void add(List<String> list, String... str) {
      for (String s : str) {
         list.add(s);
      }
   }

   public File getConfiguration() {
      return configuration;
   }

   public HelperCreate setConfiguration(File configuration) {
      this.configuration = configuration;
      if (!configuration.exists()) {
         throw new IllegalArgumentException("Base configuration does not exist");
      }
      return this;
   }

   public HelperCreate setConfiguration(String configuration) {
      File configFile = new File(configuration);
      setConfiguration(configFile);
      return this;
   }

   public void createServer() throws Exception {

      List<String> listCommands = new ArrayList<>();

      add(listCommands, "create", "--silent", "--force", "--user", user, "--password", password, "--role", role, "--port-offset", "" + portOffset, "--data", dataFolder);

      if (allowAnonymous) {
         add(listCommands, "--allow-anonymous");
      } else {
         add(listCommands, "--require-login");
      }

      if (purgePageFolders) {
         add(listCommands, "--purge-page-folders");
      }

      if (staticCluster != null) {
         add(listCommands, "--static-cluster", staticCluster);
      }

      if (!javaOptions.isEmpty()) {
         add(listCommands, "--java-options", javaOptions);
      }

      if (noWeb) {
         add(listCommands, "--no-web");
      }

      if (backup) {
         add(listCommands, "--backup");
      }

      if (replicated) {
         add(listCommands, "--replicated");
      }

      if (sharedStore) {
         add(listCommands, "--shared-store");
      }

      if (clustered) {
         add(listCommands, "--clustered");
         add(listCommands, "--message-load-balancing", messageLoadBalancing);
      }

      if (failoverOnShutdown) {
         add(listCommands, "--failover-on-shutdown");
      }

      if (noAutoTune) {
         add(listCommands, "--no-autotune");
      }

      add(listCommands, "--verbose");

      if (useAIO) {
         add(listCommands, "--aio");
      } else {
         add(listCommands, "--nio");
      }

      for (String str : args) {
         add(listCommands, str);
      }

      add(listCommands, artemisInstance.getAbsolutePath());

      logger.debug("server created at {} with home = {}", artemisInstance, artemisHome);
      artemisInstance.mkdirs();

      Artemis.execute(false, true, false, artemisHome, null, null, listCommands.toArray(new String[listCommands.size()]));

      if (configuration != null) {
         String[] list = configuration.list();

         if (list != null) {
            copyConfigurationFiles(list, configuration.toPath(), artemisInstance.toPath().resolve("etc"));
         }
      }
   }

   private void copyWithReplacements(Path original, Path target) throws IOException {
      String content = Files.readString(original);
      for (int i = 0; i + 1 < replacePairs.length; i += 2) {
         content = content.replaceAll(replacePairs[i], replacePairs[i + 1]);
      }
      Files.writeString(target, content);
   }

   private void copyConfigurationFiles(String[] list,
                                       Path sourcePath,
                                       Path targetPath) throws IOException {
      boolean hasReplacements = false;
      if (replacePairs != null && replacePairs.length > 0) {
         hasReplacements = true;
         if (replacePairs.length % 2 == 1) {
            throw new IllegalArgumentException("You need to pass an even number of replacement pairs");
         }
      }
      for (String file : list) {
         Path target = targetPath.resolve(file);

         Path originalFile = sourcePath.resolve(file);

         if (hasReplacements) {
            copyWithReplacements(originalFile, target);
         } else {
            Files.copy(originalFile, target, StandardCopyOption.REPLACE_EXISTING);
         }

         if (originalFile.toFile().isDirectory()) {
            copyConfigurationFiles(originalFile.toFile().list(), originalFile, target);
         }
      }
   }
}
