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

import static java.lang.management.ManagementFactory.getRuntimeMXBean;
import static org.apache.activemq.artemis.tests.integration.cluster.NodeManagerAction.AWAIT_PRIMARY;
import static org.apache.activemq.artemis.tests.integration.cluster.NodeManagerAction.CHECK_ID;
import static org.apache.activemq.artemis.tests.integration.cluster.NodeManagerAction.CRASH_PRIMARY;
import static org.apache.activemq.artemis.tests.integration.cluster.NodeManagerAction.DOESNT_HAVE_BACKUP;
import static org.apache.activemq.artemis.tests.integration.cluster.NodeManagerAction.DOESNT_HAVE_PRIMARY;
import static org.apache.activemq.artemis.tests.integration.cluster.NodeManagerAction.HAS_BACKUP;
import static org.apache.activemq.artemis.tests.integration.cluster.NodeManagerAction.HAS_PRIMARY;
import static org.apache.activemq.artemis.tests.integration.cluster.NodeManagerAction.PAUSE_PRIMARY;
import static org.apache.activemq.artemis.tests.integration.cluster.NodeManagerAction.RELEASE_BACKUP;
import static org.apache.activemq.artemis.tests.integration.cluster.NodeManagerAction.START_BACKUP;
import static org.apache.activemq.artemis.tests.integration.cluster.NodeManagerAction.START_PRIMARY;
import static org.apache.activemq.artemis.tests.integration.cluster.NodeManagerAction.STOP_BACKUP;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;

import org.apache.activemq.artemis.core.server.NodeManager;
import org.apache.activemq.artemis.core.server.impl.InVMNodeManager;
import org.apache.activemq.artemis.tests.util.SpawnedTestBase;
import org.junit.jupiter.api.Test;

public class NodeManagerTest extends SpawnedTestBase {

   @Test
   public void testID() throws Exception {
      NodeManagerAction live1 = new NodeManagerAction(CHECK_ID);
      performWork(live1);
   }

   @Test
   public void testPrimary() throws Exception {
      NodeManagerAction live1 = new NodeManagerAction(START_PRIMARY, HAS_PRIMARY, DOESNT_HAVE_BACKUP, CRASH_PRIMARY, DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY, START_PRIMARY, HAS_PRIMARY, DOESNT_HAVE_BACKUP, CRASH_PRIMARY, DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY);
      performWork(live1);
   }

   @Test
   public void testSimplePrimaryAndBackup() throws Exception {
      NodeManagerAction primary1 = new NodeManagerAction(START_PRIMARY, HAS_PRIMARY, DOESNT_HAVE_BACKUP, CRASH_PRIMARY, DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY);
      NodeManagerAction backup1 = new NodeManagerAction(DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY, START_BACKUP, HAS_BACKUP, AWAIT_PRIMARY, RELEASE_BACKUP, HAS_PRIMARY, PAUSE_PRIMARY, DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY);
      performWork(primary1, backup1);
   }

   @Test
   public void testSimpleBackupAndPrimary() throws Exception {
      NodeManagerAction live1 = new NodeManagerAction(START_PRIMARY, HAS_PRIMARY, DOESNT_HAVE_BACKUP, CRASH_PRIMARY, DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY);
      NodeManagerAction backup1 = new NodeManagerAction(DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY, START_BACKUP, HAS_BACKUP, AWAIT_PRIMARY, RELEASE_BACKUP, HAS_PRIMARY, PAUSE_PRIMARY, DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY);
      performWork(backup1, live1);
   }

   @Test
   public void testSimplePrimaryAnd2Backups() throws Exception {
      NodeManagerAction live1 = new NodeManagerAction(START_PRIMARY, HAS_PRIMARY, DOESNT_HAVE_BACKUP, CRASH_PRIMARY, DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY);
      NodeManagerAction backup1 = new NodeManagerAction(DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY, START_BACKUP, HAS_BACKUP, AWAIT_PRIMARY, RELEASE_BACKUP, HAS_PRIMARY, CRASH_PRIMARY, DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY);
      NodeManagerAction backup2 = new NodeManagerAction(DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY, START_BACKUP, HAS_BACKUP, AWAIT_PRIMARY, RELEASE_BACKUP, HAS_PRIMARY, CRASH_PRIMARY, DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY);
      performWork(live1, backup1, backup2);
   }

   @Test
   public void testSimple2BackupsAndPrimary() throws Exception {
      NodeManagerAction live1 = new NodeManagerAction(START_PRIMARY, HAS_PRIMARY, DOESNT_HAVE_BACKUP, CRASH_PRIMARY, DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY);
      NodeManagerAction backup1 = new NodeManagerAction(DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY, START_BACKUP, HAS_BACKUP, AWAIT_PRIMARY, RELEASE_BACKUP, HAS_PRIMARY, CRASH_PRIMARY, DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY);
      NodeManagerAction backup2 = new NodeManagerAction(DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY, START_BACKUP, HAS_BACKUP, AWAIT_PRIMARY, RELEASE_BACKUP, HAS_PRIMARY, CRASH_PRIMARY, DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY);
      performWork(backup1, backup2, live1);
   }

   @Test
   public void testSimplePrimaryAnd2BackupsPaused() throws Exception {
      NodeManagerAction primary1 = new NodeManagerAction(START_PRIMARY, HAS_PRIMARY, DOESNT_HAVE_BACKUP, CRASH_PRIMARY, DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY);
      NodeManagerAction backup1 = new NodeManagerAction(DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY, START_BACKUP, HAS_BACKUP, AWAIT_PRIMARY, RELEASE_BACKUP, HAS_PRIMARY, PAUSE_PRIMARY, START_PRIMARY, HAS_PRIMARY, DOESNT_HAVE_BACKUP, CRASH_PRIMARY, DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY);
      NodeManagerAction backup2 = new NodeManagerAction(DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY, START_BACKUP, HAS_BACKUP, AWAIT_PRIMARY, RELEASE_BACKUP, HAS_PRIMARY, PAUSE_PRIMARY, START_PRIMARY, HAS_PRIMARY, DOESNT_HAVE_BACKUP, CRASH_PRIMARY, DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY);
      performWork(primary1, backup1, backup2);
   }

   @Test
   public void testSimple2BackupsPausedAndPrimary() throws Exception {
      NodeManagerAction live1 = new NodeManagerAction(START_PRIMARY, HAS_PRIMARY, DOESNT_HAVE_BACKUP, CRASH_PRIMARY, DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY);
      NodeManagerAction backup1 = new NodeManagerAction(DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY, START_BACKUP, HAS_BACKUP, AWAIT_PRIMARY, RELEASE_BACKUP, HAS_PRIMARY, PAUSE_PRIMARY, START_PRIMARY, HAS_PRIMARY, DOESNT_HAVE_BACKUP, CRASH_PRIMARY, DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY);
      NodeManagerAction backup2 = new NodeManagerAction(DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY, START_BACKUP, HAS_BACKUP, AWAIT_PRIMARY, RELEASE_BACKUP, HAS_PRIMARY, PAUSE_PRIMARY, START_PRIMARY, HAS_PRIMARY, DOESNT_HAVE_BACKUP, CRASH_PRIMARY, DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY);
      performWork(backup1, backup2, live1);
   }

   @Test
   public void testBackupsOnly() throws Exception {
      NodeManagerAction backup1 = new NodeManagerAction(DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY, START_BACKUP, HAS_BACKUP, STOP_BACKUP, DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY);
      NodeManagerAction backup2 = new NodeManagerAction(DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY, START_BACKUP, HAS_BACKUP, STOP_BACKUP, DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY);
      NodeManagerAction backup3 = new NodeManagerAction(DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY, START_BACKUP, HAS_BACKUP, STOP_BACKUP, DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY);
      NodeManagerAction backup4 = new NodeManagerAction(DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY, START_BACKUP, HAS_BACKUP, STOP_BACKUP, DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY);
      NodeManagerAction backup5 = new NodeManagerAction(DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY, START_BACKUP, HAS_BACKUP, STOP_BACKUP, DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY);
      NodeManagerAction backup6 = new NodeManagerAction(DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY, START_BACKUP, HAS_BACKUP, STOP_BACKUP, DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY);
      NodeManagerAction backup7 = new NodeManagerAction(DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY, START_BACKUP, HAS_BACKUP, STOP_BACKUP, DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY);
      NodeManagerAction backup8 = new NodeManagerAction(DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY, START_BACKUP, HAS_BACKUP, STOP_BACKUP, DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY);
      NodeManagerAction backup9 = new NodeManagerAction(DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY, START_BACKUP, HAS_BACKUP, STOP_BACKUP, DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY);
      NodeManagerAction backup10 = new NodeManagerAction(DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY, START_BACKUP, HAS_BACKUP, STOP_BACKUP, DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY);
      NodeManagerAction backup11 = new NodeManagerAction(DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY, START_BACKUP, HAS_BACKUP, STOP_BACKUP, DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY);
      performWork(backup1, backup2, backup3, backup4, backup5, backup6, backup7, backup8, backup9, backup10, backup11);
   }

   @Test
   public void testPrimaryAndBackupActiveForcesFailback() throws Exception {
      NodeManagerAction primary1 = new NodeManagerAction(START_PRIMARY, HAS_PRIMARY, DOESNT_HAVE_BACKUP, CRASH_PRIMARY, DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY, START_PRIMARY, HAS_PRIMARY, DOESNT_HAVE_BACKUP, CRASH_PRIMARY);
      NodeManagerAction backup1 = new NodeManagerAction(DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY, START_BACKUP, HAS_BACKUP, AWAIT_PRIMARY, RELEASE_BACKUP, HAS_PRIMARY, CRASH_PRIMARY, DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY, AWAIT_PRIMARY, HAS_PRIMARY, PAUSE_PRIMARY);
      performWork(primary1, backup1);
   }

   @Test
   public void testPrimaryAnd2BackupsActiveForcesFailback() throws Exception {
      NodeManagerAction primary1 = new NodeManagerAction(START_PRIMARY, HAS_PRIMARY, DOESNT_HAVE_BACKUP, CRASH_PRIMARY, DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY, START_PRIMARY, HAS_PRIMARY, DOESNT_HAVE_BACKUP, CRASH_PRIMARY);
      NodeManagerAction backup1 = new NodeManagerAction(DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY, START_BACKUP, HAS_BACKUP, AWAIT_PRIMARY, RELEASE_BACKUP, HAS_PRIMARY, CRASH_PRIMARY, DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY, AWAIT_PRIMARY, RELEASE_BACKUP, HAS_PRIMARY, CRASH_PRIMARY);
      NodeManagerAction backup2 = new NodeManagerAction(DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY, START_BACKUP, HAS_BACKUP, AWAIT_PRIMARY, RELEASE_BACKUP, HAS_PRIMARY, CRASH_PRIMARY, DOESNT_HAVE_BACKUP, DOESNT_HAVE_PRIMARY, AWAIT_PRIMARY, RELEASE_BACKUP, HAS_PRIMARY, CRASH_PRIMARY);
      performWork(primary1, backup1, backup2);
   }

   public void performWork(NodeManagerAction... actions) throws Exception {
      NodeManager nodeManager = new InVMNodeManager(false);
      List<NodeRunner> nodeRunners = new ArrayList<>();
      Thread[] threads = new Thread[actions.length];
      for (NodeManagerAction action : actions) {
         NodeRunner nodeRunner = new NodeRunner(nodeManager, action);
         nodeRunners.add(nodeRunner);
      }
      for (int i = 0, nodeRunnersSize = nodeRunners.size(); i < nodeRunnersSize; i++) {
         NodeRunner nodeRunner = nodeRunners.get(i);
         threads[i] = new Thread(nodeRunner);
         threads[i].start();
      }

      for (Thread thread : threads) {
         try {
            thread.join(5000);
         } catch (InterruptedException e) {
            //
         }
         if (thread.isAlive()) {
            thread.interrupt();
            fail("thread still running");
         }
      }

      for (NodeRunner nodeRunner : nodeRunners) {
         if (nodeRunner.e != null) {
            nodeRunner.e.printStackTrace();
            fail(nodeRunner.e.getMessage());
         }
      }
   }

   protected static boolean isDebug() {
      return getRuntimeMXBean().getInputArguments().toString().contains("jdwp");
   }

   static class NodeRunner implements Runnable {

      private NodeManagerAction action;
      private NodeManager manager;
      Throwable e;

      NodeRunner(NodeManager nodeManager, NodeManagerAction action) {
         this.manager = nodeManager;
         this.action = action;
      }

      @Override
      public void run() {
         try {
            action.performWork(manager);
         } catch (Throwable e) {
            this.e = e;
         }
      }
   }

}
