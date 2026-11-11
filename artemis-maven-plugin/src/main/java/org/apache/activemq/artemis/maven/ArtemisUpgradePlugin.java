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
package org.apache.activemq.artemis.maven;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.activemq.artemis.boot.Artemis;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

@Mojo(name = "upgrade", defaultPhase = LifecyclePhase.TEST_COMPILE, threadSafe = true)
public class ArtemisUpgradePlugin extends ArtemisAbstractPlugin {

   @Parameter
   String name;

   @Parameter(defaultValue = "${activemq.basedir}", required = true)
   private File home;

   @Parameter(defaultValue = "${activemq.basedir}/artemis-distribution/target/apache-artemis-${project.version}-bin/apache-artemis-${project.version}/", required = true)
   private File alternateHome;

   @Parameter(defaultValue = "${basedir}/target/server0", required = true)
   private File instance;

   @Parameter
   private String[] args;


   @Override
   protected boolean isIgnore() {
      return false;
   }

   @Parameter boolean useSystemOutput = getLog().isDebugEnabled();

   private void add(List<String> list, String... str) {
      for (String s : str) {
         list.add(s);
      }
   }

   @Override
   protected void doExecute() throws MojoExecutionException, MojoFailureException {
      getLog().debug("Local " + localRepository);
      MavenProject project = (MavenProject) getPluginContext().get("project");

      home = findArtemisHome(home, alternateHome);

      Map properties = getPluginContext();

      Set<Map.Entry> entries = properties.entrySet();

      if (getLog().isDebugEnabled()) {
         getLog().debug("Entries.size " + entries.size());
         for (Map.Entry entry : entries) {
            getLog().debug("... key=" + entry.getKey() + " = " + entry.getValue());
         }
      }

      List<String> listCommands = new ArrayList<>();

      add(listCommands, "upgrade");

      add(listCommands, instance.getAbsolutePath());

      if (args != null) {
         for (String a : args) {
            add(listCommands, a);
         }
      }
      getLog().debug("***** Server upgrading at " + instance + " with home=" + home + " *****");

      try {
         Artemis.execute(home, null, null, useSystemOutput, false, listCommands);
      } catch (Throwable e) {
         getLog().error(e);
         throw new MojoFailureException(e.getMessage());
      }
   }
}
