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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public class ExecuteUtil {

   public static class ProcessHolder {

      Thread inputStreamReader;
      Thread errorStreamReader;
      Process process;

      public void kill() throws InterruptedException {
         process.destroy();
         errorStreamReader.join();
         inputStreamReader.join();
      }

      public long pid() throws Exception {
         return process.pid();
      }

      public int waitFor(long timeout, TimeUnit unit) throws InterruptedException {
         if (!process.waitFor(timeout, unit)) {
            logger.warn("could not complete execution in time");
            return -1;
         }

         errorStreamReader.join();
         inputStreamReader.join();

         return process.exitValue();
      }

      public int waitFor() throws InterruptedException {
         process.waitFor();

         errorStreamReader.join();
         inputStreamReader.join();

         return process.exitValue();
      }

   }

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static int runCommand(boolean logOutput, String... command) throws Exception {
      return runCommand(logOutput, 10, TimeUnit.SECONDS, command);
   }

   public static int runCommand(boolean logOutput,
                                long timeout,
                                TimeUnit timeoutUnit,
                                String... command) throws Exception {

      final ProcessHolder processHolder = run(logOutput, command);

      return processHolder.waitFor(timeout, timeoutUnit);
   }

   public static ProcessHolder run(boolean logOutput, String... command) throws IOException {
      logCommand(command);

      // it did not work with a simple isReachable, it could be because there's no root access, so we will try ping executable
      ProcessBuilder processBuilder = new ProcessBuilder(command);
      final ProcessHolder processHolder = new ProcessHolder();
      processHolder.process = processBuilder.start();

      processHolder.inputStreamReader = new Thread(() -> {
         try {
            readStream(processHolder.process.getInputStream(), true, logOutput);
         } catch (Exception dontCare) {

         }
      });
      processHolder.errorStreamReader = new Thread(() -> {
         try {
            readStream(processHolder.process.getErrorStream(), true, logOutput);
         } catch (Exception dontCare) {

         }
      });
      processHolder.errorStreamReader.start();
      processHolder.inputStreamReader.start();
      return processHolder;
   }

   private static void logCommand(String[] command) {
      StringBuilder logCommand = new StringBuilder();
      for (String c : command) {
         logCommand.append(c + " ");
      }
      System.out.println("command::" + logCommand.toString());
   }

   private static void readStream(InputStream stream, boolean error, boolean logOutput) throws IOException {
      BufferedReader reader = new BufferedReader(new InputStreamReader(stream));

      String inputLine;
      while ((inputLine = reader.readLine()) != null) {
         if (logOutput) {
            System.out.println(inputLine);
         } else {
            if (error) {
               logger.warn(inputLine);
            } else {
               logger.trace(inputLine);
            }
         }
      }

      reader.close();
   }

}
