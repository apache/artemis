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

import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.boot.Artemis;
import org.apache.activemq.artemis.cli.commands.Run;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

@Mojo(name = "cli", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class ArtemisCLIPlugin extends ArtemisAbstractPlugin {

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


   @Parameter
   private File etc;

   @Parameter
   private String[] args;

   @Parameter
   private boolean spawn = false;

   @Parameter(defaultValue = "300000")
   private long spawnTimeout;

   @Parameter
   private String testURI = null;

   @Parameter
   private String testClientID = null;

   @Parameter
   private String testUser = null;

   @Parameter
   private String testPassword = null;

   @Parameter boolean useSystemOutput = getLog().isDebugEnabled();

   @Override
   protected boolean isIgnore() {
      return ignore;
   }

   @Override
   protected void doExecute() throws MojoExecutionException, MojoFailureException {
      // This is to avoid the Run issuing a kill at any point
      Run.setEmbedded(true);

      MavenProject project = (MavenProject) getPluginContext().get("project");

      home = findArtemisHome(home, alternateHome);

      try {
         if (spawn) {
            final Process process = org.apache.activemq.artemis.cli.process.ProcessBuilder.build(name, location, true, args);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> process.destroy()));

            if (testURI != null) {
               long timeout = System.currentTimeMillis() + spawnTimeout;
               while (System.currentTimeMillis() <= timeout) {
                  try (ServerLocator locator = ActiveMQClient.createServerLocator(testURI)) {
                     if (testUser != null || testPassword != null || testClientID != null) {
                        locator.createSessionFactory(testUser, testPassword).createSession(testUser, testPassword, false, false, false, false, 0, testClientID).close();
                     } else {
                        locator.createSessionFactory().createSession().close();
                     }
                     getLog().info("Server started");
                  } catch (Exception e) {
                     getLog().info("awaiting server to start");
                     Thread.sleep(500);
                     continue;
                  }
                  break;
               }
            }
         } else {
            Artemis.execute(home, location, etc, useSystemOutput, false, args);
         }

         Thread.sleep(600);

         org.apache.activemq.artemis.cli.process.ProcessBuilder.cleanupProcess();
      } catch (Throwable e) {
         throw new MojoExecutionException(e.getMessage(), e);
      }
   }
}
