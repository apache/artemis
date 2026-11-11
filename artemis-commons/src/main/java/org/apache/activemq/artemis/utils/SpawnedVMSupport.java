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
package org.apache.activemq.artemis.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class SpawnedVMSupport {

   static ConcurrentHashMap<Process, String> startedProcesses = null;

   public static Process spawnVM(final String className, final String... args) throws Exception {
      return SpawnedVMSupport.spawnVM(className, new String[0], true, args);
   }

   public static Process spawnVM(final String className,
                                 final boolean logOutput,
                                 final String... args) throws Exception {
      return SpawnedVMSupport.spawnVM(className, new String[0], logOutput, args);
   }
   public static Process spawnVM(final String classPath,
                                 final String className,
                                 final boolean logOutput,
                                 final String... args) throws Exception {
      return SpawnedVMSupport.spawnVM(classPath, className, new String[0], logOutput, args);
   }

   public static Process spawnVM(final String className, final String[] vmargs, final String... args) throws Exception {
      return SpawnedVMSupport.spawnVM(className, vmargs, true, args);
   }

   public static Process spawnVM(final String className,
                                 final String[] vmargs,
                                 final boolean logOutput,
                                 final String... args) throws Exception {
      return SpawnedVMSupport.spawnVM(className, "-Xms512m", "-Xmx512m", vmargs, logOutput, true, args);
   }

   public static Process spawnVM(final String classpath,
                                 final String className,
                                 final String[] vmargs,
                                 final boolean logOutput,
                                 final String... args) throws Exception {
      return SpawnedVMSupport.spawnVM(classpath, className, "-Xms512m", "-Xmx512m", vmargs, logOutput, true, args);
   }

   public static Process spawnVMWithLogMacher(String wordMatch,
                                              Runnable runnable,
                                              final String className,
                                              final String[] vmargs,
                                              final boolean logOutput,
                                              final String... args) throws Exception {
      return SpawnedVMSupport.spawnVM(wordMatch, runnable, className, "-Xms512m", "-Xmx512m", vmargs, logOutput, true, args);
   }

   public static Process spawnVM(final String className,
                                 final String memoryArg1,
                                 final String memoryArg2,
                                 final String[] vmargs,
                                 final boolean logOutput,
                                 final boolean logErrorOutput,
                                 final String... args) throws Exception {
      return spawnVM(null, null, className, memoryArg1, memoryArg2, vmargs, logOutput, logErrorOutput, args);
   }

   public static Process spawnVM(final String classPath,
                                 final String className,
                                 final String memoryArg1,
                                 final String memoryArg2,
                                 final String[] vmargs,
                                 final boolean logOutput,
                                 final boolean logErrorOutput,
                                 final String... args) throws Exception {
      return spawnVM(classPath, null, null, className, memoryArg1, memoryArg2, vmargs, logOutput, logErrorOutput, args);
   }

   public static Process spawnVM(final String wordMatch,
                                 final Runnable wordRunning,
                                 final String className,
                                 final String memoryArg1,
                                 final String memoryArg2,
                                 final String[] vmargs,
                                 final boolean logOutput,
                                 final boolean logErrorOutput,
                                 final String... args) throws Exception {
      return spawnVM(getClassPath(), wordMatch, wordRunning, className, memoryArg1, memoryArg2, vmargs, logOutput, logErrorOutput, args);

   }

   public static Process spawnVM(String classPath,
                                 String wordMatch,
                                 Runnable wordRunning,
                                 String className,
                                 String memoryArg1,
                                 String memoryArg2,
                                 String[] vmargs,
                                 boolean logOutput,
                                 boolean logErrorOutput,
                                 String... args) throws IOException, ClassNotFoundException {
      return spawnVM(classPath, wordMatch, wordRunning, className, memoryArg1, memoryArg2, vmargs, logOutput, logErrorOutput, -1, args);
   }

   public static String getClassPath() {
      return System.getProperty("java.class.path");
   }

   public static String getClassPath(File libfolder) {
      if (libfolder == null) {
         return getClassPath();
      }

      StringBuilder stringBuilder = new StringBuilder();
      boolean empty = true;
      File[] files = libfolder.listFiles();

      if (files == null) {
         return getClassPath();
      } else {
         for (File f : files) {
            if (f.getName().endsWith(".jar") || f.getName().endsWith(".zip")) {
               if (!empty) {
                  stringBuilder.append(File.pathSeparator);
               }
               empty = false;
               stringBuilder.append(f.toString());
            }
         }
      }

      return stringBuilder.toString();
   }

   /**
    * Spawns a VM
    *
    * @param debugPort if &lt;=0 it means no debug {@return a {@code Process} based on the input parameters}
    */
   public static Process spawnVM(String classPath,
                                 String wordMatch,
                                 Runnable wordRunning,
                                 String className,
                                 String memoryArg1,
                                 String memoryArg2,
                                 String[] vmargs,
                                 boolean logOutput,
                                 boolean logErrorOutput,
                                 long debugPort,
                                 String... args) throws IOException, ClassNotFoundException {
      final String javaPath = Paths.get(System.getProperty("java.home"), "bin", "java").toAbsolutePath().toString();
      ProcessBuilder builder = new ProcessBuilder();
      if (memoryArg1 == null) {
         memoryArg1 = "-Xms128m";
      }
      if (memoryArg2 == null) {
         memoryArg2 = "-Xmx128m";
      }
      builder.command(javaPath, memoryArg1, memoryArg2);
      if (debugPort > 0) {
         builder.command().add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=" + debugPort);
      }
      builder.environment().put("CLASSPATH", classPath);

      List<String> commandList = builder.command();

      if (vmargs != null) {
         for (String arg : vmargs) {
            commandList.add(arg);
         }
      }

      commandList.add("-Djava.io.tmpdir=" + System.getProperty("java.io.tmpdir", "./tmp"));
      commandList.add("-Djava.library.path=" + System.getProperty("java.library.path", "./native/bin"));

      String loggingConfigFile = System.getProperty("log4j2.configurationFile");
      if (loggingConfigFile != null) {
         commandList.add("-Dlog4j2.configurationFile=" + loggingConfigFile);
      }

      String jacocoAgent = System.getProperty("jacoco.agent");
      if (jacocoAgent != null && !jacocoAgent.isEmpty()) {
         commandList.add(jacocoAgent);
      }

      commandList.add(className);
      for (String arg : args) {
         commandList.add(arg);
      }

      Process process = builder.start();

      spawnLoggers(wordMatch, wordRunning, className, logOutput, logErrorOutput, process);

      if (startedProcesses != null) {
         startedProcesses.put(process, className);
      }
      return process;
   }

   public static void spawnLoggers(String wordMatch,
                                 Runnable wordRunning,
                                 String className,
                                 boolean logOutput,
                                 boolean logErrorOutput,
                                 Process process) throws ClassNotFoundException {
      SpawnedVMSupport.startLogger(logOutput, wordMatch, wordRunning, className, process);

      // Adding a reader to System.err, so the VM won't hang on a System.err.println
      ProcessLogger errorLogger = new ProcessLogger(logErrorOutput, process.getErrorStream(), className, wordMatch, wordRunning);
      errorLogger.start();
   }

   /**
    * it will return a pair of dead / alive servers
    */
   private static Set<Process> getAliveProcesses() {

      Set<Process> aliveProcess = new HashSet<>();

      if (startedProcesses != null) {
         for (;;) {
            try {
               aliveProcess.clear();
               for (Process process : startedProcesses.keySet()) {
                  if (process.isAlive()) {
                     aliveProcess.add(process);
                     process.destroyForcibly();
                  }
               }
               break;
            } catch (Throwable e) {
               e.printStackTrace();
            }
         }
      }

      return aliveProcess;

   }

   public static void forceKill() {

      Set<Process> aliveProcess = getAliveProcesses();

      for (Process alive : aliveProcess) {
         for (int i = 0; i < 5; i++) {
            alive.destroyForcibly();
            try {
               alive.waitFor(5, TimeUnit.SECONDS);
            } catch (Exception e) {
            }
         }
      }

   }

   public static void enableCheck() {
      startedProcesses = new ConcurrentHashMap<>();
   }

   /**
    * Check if all spawned processes are finished.
    */
   public static boolean checkProcess() {

      Set<Process> aliveProcess = getAliveProcesses();

      try {
         if (!aliveProcess.isEmpty()) {
            for (Process alive : aliveProcess) {
               alive.destroyForcibly();
            }
            return false;
         }
      } finally {
         startedProcesses = null;
      }

      return true;
   }

   public static void startLogger(final boolean print,
                                  final String wordMatch,
                                  final Runnable wordRunanble,
                                  final String className,
                                  final Process process) throws ClassNotFoundException {
      ProcessLogger outputLogger = new ProcessLogger(print, process.getInputStream(), className, wordMatch, wordRunanble);
      outputLogger.start();
   }

   public static void startLogger(final String className, final Process process) throws ClassNotFoundException {
      startLogger(true, null, null, className, process);
   }

   /**
    * Redirect the input stream to a logger (as debug logs)
    */
   static class ProcessLogger extends Thread {

      private final InputStream is;

      private final String className;

      private final boolean print;

      private final String wordMatch;
      /**
       * This will be executed when wordMatch is within any line on the log
       */
      private final Runnable wordRunner;

      ProcessLogger(final boolean print,
                    final InputStream is,
                    final String className,
                    String wordMatch,
                    Runnable wordRunner) throws ClassNotFoundException {
         this.is = is;
         this.print = print;
         this.className = className;
         this.wordMatch = wordMatch;
         this.wordRunner = wordRunner;
         setDaemon(true);
      }

      @Override
      public void run() {
         try {
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);
            String line = null;
            while ((line = br.readLine()) != null) {
               if (wordMatch != null && wordRunner != null) {
                  if (line.contains(wordMatch)) {
                     wordRunner.run();
                  }
               }
               if (print) {
                  System.out.println(className + ":" + line);
               }
            }
         } catch (IOException ioe) {
            ioe.printStackTrace();
         }
      }
   }
}
