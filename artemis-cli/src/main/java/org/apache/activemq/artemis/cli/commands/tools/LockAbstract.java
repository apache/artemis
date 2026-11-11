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
package org.apache.activemq.artemis.cli.commands.tools;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;

import org.apache.activemq.artemis.cli.CLIException;
import org.apache.activemq.artemis.cli.commands.ActionContext;

public abstract class LockAbstract extends DataAbstract {

   // There should be one lock per VM
   // These will be locked as long as the VM is running
   private static RandomAccessFile serverLockFile = null;
   private static FileLock serverLockLock = null;

   public static void unlock() {
      try {
         if (serverLockFile != null) {
            serverLockFile.close();
            serverLockFile = null;
         }

         if (serverLockLock != null) {
            serverLockLock.close();
            serverLockLock = null;
         }
      } catch (Exception ignored) {
      }
   }

   @Override
   public Object execute(ActionContext context) throws Exception {
      super.execute(context);

      if (getBrokerInstance() == null) {
         context.err.println("Warning: You are running a data tool outside of any broker instance. Modifying data on a running server might break the server's data");
         context.err.println();
      } else {
         lockCLI(getLockPlace());
      }

      return null;
   }

   @Override
   public void done() {
      super.done();
      unlock();
   }

   void lockCLI(File lockPlace) throws Exception {
      if (lockPlace != null) {
         lockPlace.mkdirs();
         if (serverLockFile == null) {
            File fileLock = new File(lockPlace, "cli.lock");
            serverLockFile = new RandomAccessFile(fileLock, "rw");
         }
         try {
            FileLock lock = serverLockFile.getChannel().tryLock();
            if (lock == null) {
               throw new CLIException("Error: There is another process using the server at " + lockPlace + ". Cannot start the process!");
            }
            serverLockLock = lock;
         } catch (OverlappingFileLockException e) {
            throw new CLIException("Error: There is another process using the server at " + lockPlace + ". Cannot start the process!");
         }
      }
   }

   private File getLockPlace() throws Exception {
      String brokerInstance = getBrokerInstance();
      if (brokerInstance != null) {
         return new File(new File(brokerInstance), "lock");
      } else {
         return null;
      }
   }
}
