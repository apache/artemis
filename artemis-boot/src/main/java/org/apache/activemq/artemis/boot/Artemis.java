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
package org.apache.activemq.artemis.boot;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A main class which setups up a classpath and then passes execution off to the Apache Artemis cli main.
 */
public class Artemis {

   public static final String EXTRA_LIBS_SYSTEM_PROPERTY = "artemis.extra.libs";
   public static final String EXTRA_LIBS_ENVIRONMENT_VARIABLE = "ARTEMIS_EXTRA_LIBS";

   public static void main(String[] args) throws Throwable {
      String home = System.getProperty("artemis.home");

      File fileHome = home != null ? new File(home) : null;

      String instance = System.getProperty("artemis.instance");
      File fileInstance = instance != null ? new File(instance) : null;


      String brokerEtc = System.getProperty("artemis.instance.etc");
      File fileBrokerETC = null;
      if (brokerEtc != null) {
         brokerEtc = brokerEtc.replace("\\", "/");
         fileBrokerETC = new File(brokerEtc);
      } else {
         if (instance != null) {
            brokerEtc = instance + "/etc";
            fileBrokerETC = new File(brokerEtc);
         }
      }



      Object result = execute(fileHome, fileInstance, fileBrokerETC, true, true, args);
      if (result instanceof Exception) {
         // Set a nonzero status code for the exceptions caught and printed by org.apache.activemq.artemis.cli.Artemis.execute
         System.exit(1);
      }
   }

   /**
    * This is a good method for booting an embedded command
    */
   public static Object execute(File artemisHome, File artemisInstance, File fileBrokerETC, boolean useSystemOut, boolean enableShell, List<String> args) throws Throwable {
      return execute(artemisHome, artemisInstance, fileBrokerETC, useSystemOut, enableShell, args.toArray(new String[args.size()]));
   }

   /**
    * This is a good method for booting an embedded command
    */
   public static Object execute(File fileHome, File fileInstance, File fileBrokerETC, boolean useSystemOut, boolean enableShell, String... args) throws Throwable {
      List<File> dirs = new ArrayList<>();
      if (fileHome != null) {
         dirs.add(new File(fileHome, "lib"));
      }
      if (fileInstance != null) {
         dirs.add(new File(fileInstance, "lib"));
      }

      String extraLibs = System.getProperty(EXTRA_LIBS_SYSTEM_PROPERTY);
      if (extraLibs == null) {
         extraLibs = System.getenv(EXTRA_LIBS_ENVIRONMENT_VARIABLE);
      }
      if (extraLibs != null) {
         for (String extraLib: extraLibs.split(",")) {
            dirs.add(new File(extraLib));
         }
      }

      List<URL> urls = new ArrayList<>();

      // Without the etc on the config, things like JGroups configuration wouldn't be loaded
      if (fileBrokerETC == null && fileInstance != null) {
         // the fileBrokerETC could be null if execute is called directly from an external module
         fileBrokerETC = new File(fileInstance, "etc");
      }

      if (fileBrokerETC != null) {
         // Adding etc to the classLoader so modules can lookup for their configs
         urls.add(fileBrokerETC.toURI().toURL());
      }

      if (fileHome != null) {
         File etcFile = new File(fileHome, "etc");
         // Adding etc to the classLoader so modules can lookup for their configs
         urls.add(etcFile.toURI().toURL());
      }
      for (File bootdir : dirs) {
         if (bootdir.exists() && bootdir.isDirectory()) {

            // Find the jar files in the directory..
            List<File> files = new ArrayList<>();
            for (File f : bootdir.listFiles()) {
               if (f.getName().endsWith(".jar") || f.getName().endsWith(".zip")) {
                  files.add(f);
               }
            }

            // Sort the list by file name..
            Collections.sort(files, Comparator.comparing(File::getName));

            for (File f : files) {
               add(urls, f);
            }

         }
      }

      if (System.getProperty("java.io.tmpdir") == null && fileInstance != null) {
         System.setProperty("java.io.tmpdir", new File(fileInstance, "tmp").getCanonicalPath());
      }

      ClassLoader originalCL = Thread.currentThread().getContextClassLoader();

      // Now setup our classloader..
      URLClassLoader loader = new URLClassLoader(urls.toArray(new URL[urls.size()]));
      Thread.currentThread().setContextClassLoader(loader);
      Class<?> clazz = loader.loadClass("org.apache.activemq.artemis.cli.Artemis");
      Method method = clazz.getMethod("execute", Boolean.TYPE, Boolean.TYPE, Boolean.TYPE, File.class, File.class, File.class, args.getClass());

      try {
         return method.invoke(null, useSystemOut, useSystemOut, enableShell, fileHome, fileInstance, fileBrokerETC, args);
      } catch (InvocationTargetException e) {
         throw e.getTargetException();
      } finally {
         Thread.currentThread().setContextClassLoader(originalCL);
      }

   }

   private static void add(List<URL> urls, File file) {
      try {
         urls.add(file.toURI().toURL());
      } catch (MalformedURLException e) {
         e.printStackTrace();
      }
   }

}
