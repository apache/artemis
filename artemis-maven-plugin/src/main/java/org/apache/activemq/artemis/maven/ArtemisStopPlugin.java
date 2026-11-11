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
import java.util.concurrent.TimeUnit;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

@Mojo(name = "stop", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class ArtemisStopPlugin extends ArtemisAbstractPlugin {

   private static final String STOP = "stop";

   @Parameter(defaultValue = "${noServer}")
   boolean ignore;

   @Parameter(defaultValue = "server")
   String name;

   @Parameter(defaultValue = "${activemq.basedir}", required = true)
   private File home;

   @Parameter(defaultValue = "${activemq.basedir}/artemis-distribution/target/apache-artemis-${project.version}-bin/apache-artemis-${project.version}/", required = true)
   private File alternateHome;

   @Parameter(defaultValue = "${basedir}/target/server0", required = true)
   private File location;

   @Parameter(defaultValue = "30000")
   private long spawnTimeout;

   @Parameter
   boolean useSystemOutput = getLog().isDebugEnabled();

   @Override
   protected boolean isIgnore() {
      return ignore;
   }

   @Override
   protected void doExecute() throws MojoExecutionException, MojoFailureException {
      MavenProject project = (MavenProject) getPluginContext().get("project");

      home = findArtemisHome(home, alternateHome);

      try {
         final Process process = org.apache.activemq.artemis.cli.process.ProcessBuilder.build(name, location, true, new String[] {STOP});
         Runtime.getRuntime().addShutdownHook(new Thread(() -> process.destroy()));

         boolean complete = process.waitFor(spawnTimeout, TimeUnit.MILLISECONDS);
         if (!complete) {
            getLog().error("Stop process did not exit within the spawnTimeout of " + spawnTimeout);

            throw new MojoExecutionException("Stop process did not exit within the spawnTimeout of " + spawnTimeout);
         }

         Thread.sleep(600);
      } catch (Throwable e) {
         throw new MojoExecutionException(e.getMessage(), e);
      } finally {
         org.apache.activemq.artemis.cli.process.ProcessBuilder.cleanupProcess();
      }
   }
}
