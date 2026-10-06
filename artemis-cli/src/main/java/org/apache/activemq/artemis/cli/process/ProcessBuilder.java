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
package org.apache.activemq.artemis.cli.process;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ProcessBuilder {

   static Set<Process> processes = ConcurrentHashMap.newKeySet();

   static {
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
         for (Process p : processes) {
            p.destroy();
         }
      }));
   }

   /**
    * it will lookup for process that are dead already, eliminating leaks.
    */
   public static void cleanupProcess() {
      for (Process p : processes) {
         processes.remove(p);
      }
   }

   /**
    * @param logname  the prefix for log output
    * @param location The location where this command is being executed from
    * @param hook     it will finish the process upon shutdown of the VM
    * @param args     The arguments being passwed to the the CLI tool
    */
   public static Process build(String logname, File location, boolean hook, String... args) throws Exception {
      boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().trim().startsWith("win");

      String[] newArgs;
      if (IS_WINDOWS) {
         newArgs = rebuildArgs(args, "cmd", "/c", "artemis.cmd");
      } else {
         newArgs = rebuildArgs(args, "./artemis");
      }

      java.lang.ProcessBuilder builder = new java.lang.ProcessBuilder(newArgs);

      builder.directory(new File(location, "bin"));

      Process process = builder.start();

      ProcessLogger outputLogger = new ProcessLogger(true, process.getInputStream(), logname, false);
      outputLogger.start();

      // Adding a reader to System.err, so the VM won't hang on a System.err.println as identified on this forum thread:
      ProcessLogger errorLogger = new ProcessLogger(true, process.getErrorStream(), logname, true);
      errorLogger.start();

      processes.add(process);

      cleanupProcess();

      return process;
   }

   public static String[] rebuildArgs(String[] args, String... prefixArgs) {
      String[] resultArgs = new String[args.length + prefixArgs.length];

      int i = 0;

      for (String arg : prefixArgs) {
         resultArgs[i++] = arg;
      }

      for (String arg : args) {
         resultArgs[i++] = arg;
      }

      return resultArgs;
   }

   /**
    * Redirect the input stream to a logger (as debug logs)
    */
   static class ProcessLogger extends Thread {

      private final InputStream is;

      private final String logName;

      private final boolean print;

      private final boolean sendToErr;

      ProcessLogger(final boolean print,
                    final InputStream is,
                    final String logName,
                    final boolean sendToErr) throws ClassNotFoundException {
         this.is = is;
         this.print = print;
         this.logName = logName;
         this.sendToErr = sendToErr;
         setDaemon(false);
      }

      @Override
      public void run() {
         try {
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);
            String line;
            while ((line = br.readLine()) != null) {
               if (print) {
                  if (sendToErr) {
                     System.err.println(logName + "-err:" + line);
                  } else {
                     System.out.println(logName + "-out:" + line);
                  }
               }
            }
         } catch (IOException e) {
            // ok, stream closed
         }
      }
   }
}
