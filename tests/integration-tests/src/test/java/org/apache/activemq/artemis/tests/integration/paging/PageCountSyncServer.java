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
package org.apache.activemq.artemis.tests.integration.paging;

import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.utils.SpawnedVMSupport;

/**
 * This is a sub process of the test {@link PageCountSyncOnNonTXTest}. The System.out calls here are meant to be here as
 * they will appear on the process output and test output. It helps to identify what happened on the test in case of
 * failures.
 */
public class PageCountSyncServer extends SpawnedServerSupport {

   public static Process spawnVM(final String testDir, final long timeToRun) throws Exception {
      return SpawnedVMSupport.spawnVM(PageCountSyncServer.class.getName(), testDir, "" + timeToRun);
   }

   public static Process spawnVMWithLogMacher(final String wordMatch,
                                              final Runnable runnable,
                                              final String testDir,
                                              final long timeToRun) throws Exception {
      return SpawnedVMSupport.spawnVMWithLogMacher(wordMatch, runnable, PageCountSyncServer.class.getName(), null, true, testDir, "" + timeToRun);
   }

   public void perform(final String folder, final long timeToRun) throws Exception {

      try {
         ActiveMQServer server = createServer(folder);

         server.start();

         System.out.println(PageCountSyncOnNonTXTest.WORD_START);
         System.out.println("Server started!!!");

         System.out.println("Waiting " + timeToRun + " seconds");
         Thread.sleep(timeToRun);

         System.out.println("Going down now!!!");
         System.exit(1);
      } catch (Exception e) {
         e.printStackTrace();
         System.exit(-1);
      }
   }

   public static void main(String[] args) throws Exception {
      PageCountSyncServer ss = new PageCountSyncServer();

      System.out.println("Args.length = " + args.length);
      for (String arg : args) {
         System.out.println("Argument: " + arg);
      }

      if (args.length == 2) {
         ss.perform(args[0], Long.parseLong(args[1]));
      } else {
         System.err.println("you were expected to pass getTestDir as an argument on SpawnVMSupport");
      }
   }

}
