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

public class PagingWithFailoverServer extends SpawnedServerSupport {

   public static Process spawnVM(final String testDir, final int thisPort, final int otherPort) throws Exception {
      return spawnVM(testDir, thisPort, otherPort, false);
   }

   public static Process spawnVM(final String testDir,
                                 final int thisPort,
                                 final int otherPort,
                                 final boolean isBackup) throws Exception {
      return SpawnedVMSupport.spawnVM(PagingWithFailoverServer.class.getName(), testDir, Integer.toString(thisPort), Integer.toString(otherPort), Boolean.toString(isBackup));
   }

   private ActiveMQServer server;

   public ActiveMQServer getServer() {
      return server;
   }

   public void perform(final String folder,
                       final int thisPort,
                       final int otherPort,
                       final boolean isBackup) throws Exception {
      try {
         server = createServer(folder, thisPort, otherPort, isBackup);

         server.start();

         System.out.println("Server started!!!");
      } catch (Exception e) {
         e.printStackTrace();
         System.exit(-1);
      }
   }

   public static ActiveMQServer createServer(String folder, int thisPort, int otherPort, boolean isBackup) {
      return createSharedFolderServer(folder, thisPort, otherPort, isBackup);
   }

   public static void main(String[] arg) {
      if (arg.length != 4) {
         System.out.println("expected folder portThisServer portOtherServer isBackup");
      }
      PagingWithFailoverServer server = new PagingWithFailoverServer();

      try {
         server.perform(arg[0], Integer.parseInt(arg[1]), Integer.parseInt(arg[2]), Boolean.parseBoolean(arg[3]));
      } catch (Throwable e) {
         e.printStackTrace();
         System.exit(-1);
      }
   }

}
