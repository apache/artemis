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
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Allows a Java Client to be run which must hve a static main(String[] args) method
 */
@Mojo(name = "runClient", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class ArtemisClientPlugin extends ArtemisAbstractPlugin {

   @Parameter
   String clientClass;

   @Parameter
   String[] args;


   @Parameter
   String classPath;

   @Parameter(defaultValue = "${noClient}")
   boolean ignore;

   @Override
   protected boolean isIgnore() {
      return ignore;
   }


   protected ClassLoader defineClassLoader(String classPath) throws Exception {
      String[] classPathArray = classPath.split(File.pathSeparator);
      URL[] elements = new URL[classPathArray.length];
      for (int i = 0; i < classPathArray.length; i++) {
         elements[i] = new File(classPathArray[i]).toPath().toUri().toURL();
      }
      return new URLClassLoader(elements);
   }


   @Override
   protected void doExecute() throws MojoExecutionException, MojoFailureException {
      try {
         Class aClass;
         if (classPath != null) {
            ClassLoader loader = defineClassLoader(classPath);
            aClass = loader.loadClass(clientClass);
         } else {
            aClass = Class.forName(clientClass);
         }
         Method method = aClass.getDeclaredMethod("main", new Class[]{String[].class});
         method.invoke(null, new Object[]{args});
      } catch (Exception e) {
         getLog().error(e);
         throw new MojoFailureException(e.getMessage());
      }
   }
}
