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
package org.apache.activemq.artemis.tests.integration.cluster;

import java.util.Arrays;

import org.apache.activemq.artemis.core.server.NodeManager;
import org.apache.activemq.artemis.utils.UUID;

public class NodeManagerAction {

   public static final int START_PRIMARY = 0;
   public static final int START_BACKUP = 1;
   public static final int CRASH_PRIMARY = 2;
   public static final int PAUSE_PRIMARY = 3;
   public static final int STOP_BACKUP = 4;
   public static final int AWAIT_PRIMARY = 5;
   public static final int RELEASE_BACKUP = 6;

   public static final int HAS_PRIMARY = 10;
   public static final int HAS_BACKUP = 11;
   public static final int DOESNT_HAVE_PRIMARY = 12;
   public static final int DOESNT_HAVE_BACKUP = 13;
   public static final int CHECK_ID = 14;

   private final int[] work;

   boolean hasPrimaryLock = false;
   boolean hasBackupLock = false;

   public NodeManagerAction(int... work) {
      this.work = work;
   }

   public void performWork(NodeManager nodeManager) throws Exception {
      for (int action : work) {
         switch (action) {
            case START_PRIMARY:
               nodeManager.startPrimaryNode().activationComplete();
               hasPrimaryLock = true;
               hasBackupLock = false;
               break;
            case START_BACKUP:
               nodeManager.startBackup();
               hasBackupLock = true;
               break;
            case CRASH_PRIMARY:
               nodeManager.crashPrimaryServer();
               hasPrimaryLock = false;
               break;
            case PAUSE_PRIMARY:
               nodeManager.pausePrimaryServer();
               hasPrimaryLock = false;
               break;
            case STOP_BACKUP:
               nodeManager.stopBackup();
               hasBackupLock = false;
               break;
            case AWAIT_PRIMARY:
               nodeManager.awaitPrimaryNode();
               hasPrimaryLock = true;
               break;
            case RELEASE_BACKUP:
               nodeManager.releaseBackup();
               hasBackupLock = false;
            case HAS_PRIMARY:
               if (!hasPrimaryLock) {
                  throw new IllegalStateException("live lock not held");
               }
               break;
            case HAS_BACKUP:
               if (!hasBackupLock) {
                  throw new IllegalStateException("backup lock not held");
               }
               break;
            case DOESNT_HAVE_PRIMARY:
               if (hasPrimaryLock) {
                  throw new IllegalStateException("live lock held");
               }
               break;
            case DOESNT_HAVE_BACKUP:
               if (hasBackupLock) {
                  throw new IllegalStateException("backup lock held");
               }
               break;
            case CHECK_ID:
               nodeManager.start();
               UUID id1 = nodeManager.getUUID();
               nodeManager.stop();
               nodeManager.start();
               if (!Arrays.equals(id1.asBytes(), nodeManager.getUUID().asBytes())) {
                  throw new IllegalStateException("getUUID should be the same on restart");
               }
               break;
         }
      }
   }

   public int works() {
      return work.length;
   }

   public int getWork(String[] works, int start) {
      final int workLength = work.length;
      for (int i = 0; i < workLength; i++) {
         works[i + start] = Integer.toString(work[i]);
      }
      return workLength;
   }

   public static void execute(String[] args, NodeManager nodeManager) throws Exception {
      int[] work1 = new int[args.length];
      for (int i = 0; i < args.length; i++) {
         work1[i] = Integer.parseInt(args[i]);

      }
      NodeManagerAction nodeManagerAction = new NodeManagerAction(work1);
      nodeManager.start();
      try {
         nodeManagerAction.performWork(nodeManager);
      } catch (Exception e) {
         e.printStackTrace();
         System.exit(9);
      } finally {
         nodeManager.stop();
      }
   }

}
