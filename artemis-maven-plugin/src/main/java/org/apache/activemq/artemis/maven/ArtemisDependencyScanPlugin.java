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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

@Mojo(name = "dependency-scan", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class ArtemisDependencyScanPlugin extends ArtemisAbstractPlugin {

   @Parameter
   String name;

   @Parameter
   private String[] libListWithDeps;

   @Parameter
   private String[] libList;

   @Parameter
   private String variableName;

   @Parameter
   private String file;

   @Parameter
   private String pathSeparator = File.pathSeparator;

   /**
    * Where to copy the exploded dependencies.
    */
   @Parameter
   private File targetFolder;

   private MavenProject project;

   @Parameter
   private boolean optional = false;

   @Override
   protected boolean isIgnore() {
      return false;
   }

   @Override
   protected void doExecute() throws MojoExecutionException, MojoFailureException {

      super.doExecute();

      getLog().debug("Local " + localRepository);
      project = (MavenProject) getPluginContext().get("project");

      Map properties = getPluginContext();

      Set<Map.Entry> entries = properties.entrySet();

      getLog().debug("Entries.size " + entries.size());
      for (Map.Entry entry : entries) {
         getLog().debug("... key=" + entry.getKey() + " = " + entry.getValue());
      }

      try {
         StringBuilder sb = new StringBuilder();
         Set<File> filesSet = resolveDependencies(libListWithDeps, libList);

         if (variableName != null || file != null) {
            String separatorUsed = "";
            for (File f : filesSet) {
               sb.append(separatorUsed);
               sb.append(f.getAbsolutePath());
               separatorUsed = pathSeparator;
            }

            String classPathGenerated = sb.toString();
            setVariable(classPathGenerated);

            if (file != null) {
               File fileOutput = new File(file);
               try {
                  if (getLog().isDebugEnabled()) {
                     getLog().debug("Generating file " + file + " with classpath output for " + variableName);
                     getLog().debug(classPathGenerated);
                  }
                  PrintStream printStream = new PrintStream(new BufferedOutputStream(new FileOutputStream(fileOutput)));
                  printStream.print(classPathGenerated);
                  printStream.close();
               } catch (Exception e) {
                  getLog().error("could not generate file with classpath", e);
               }
            }

         }

         if (getLog().isDebugEnabled()) {
            getLog().debug("targetFolder=" + targetFolder);
         }

         if (targetFolder != null) {
            if (getLog().isDebugEnabled()) {
               getLog().debug("copying libraries into " + targetFolder);
            }
            targetFolder.mkdirs();
            for (File file : filesSet) {
               File targetFile = new File(targetFolder, file.getName());
               if (getLog().isDebugEnabled()) {
                  getLog().debug("copying " + file.toPath() + " into " + targetFile.toPath());
               }
               Files.copy(file.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
         }
      } catch (Throwable e) {
         getLog().error(e);
         if (optional) {
            setVariable("");
         } else {
            throw new MojoFailureException(e.getMessage());
         }
      } finally {
         done();
      }


   }

   private void setVariable(String classPathGenerated) {
      if (variableName != null) {
         project.getProperties().setProperty(variableName, classPathGenerated);
         getLog().debug("dependency-scan setting: -D" + variableName + "=\"" + classPathGenerated + "\"");
      }
   }

}
