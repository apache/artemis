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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.activemq.artemis.core.config.storage.DatabaseStorageConfiguration;
import org.apache.activemq.artemis.core.server.NodeManager;
import org.apache.activemq.artemis.core.server.impl.jdbc.JdbcNodeManager;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.apache.activemq.artemis.utils.actors.OrderedExecutorFactory;

public class JdbcNodeManagerTest extends NodeManagerTest {

   @Override
   public void performWork(NodeManagerAction... actions) throws Exception {
      List<NodeRunner> nodeRunners = new ArrayList<>();
      final ThreadFactory daemonThreadFactory = t -> {
         final Thread th = new Thread(t);
         th.setDaemon(true);
         return th;
      };
      Thread[] threads = new Thread[actions.length];
      List<NodeManager> nodeManagers = new ArrayList<>(actions.length * 2);
      AtomicBoolean failedRenew = new AtomicBoolean(false);
      for (NodeManagerAction action : actions) {
         final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(daemonThreadFactory);
         final ExecutorService executor = Executors.newFixedThreadPool(2, daemonThreadFactory);
         final DatabaseStorageConfiguration dbConf = createDefaultDatabaseStorageConfiguration();
         final ExecutorFactory executorFactory = new OrderedExecutorFactory(executor);
         JdbcNodeManager nodeManager = JdbcNodeManager.with(dbConf, scheduledExecutorService, executorFactory);
         nodeManager.start();
         NodeRunner nodeRunner = new NodeRunner(nodeManager, action);
         nodeRunners.add(nodeRunner);
         nodeManagers.add(nodeManager);
         runAfter(scheduledExecutorService::shutdownNow);
         runAfter(executor::shutdownNow);
      }
      for (int i = 0, nodeRunnersSize = nodeRunners.size(); i < nodeRunnersSize; i++) {
         NodeRunner nodeRunner = nodeRunners.get(i);
         threads[i] = new Thread(nodeRunner);
         threads[i].start();
      }
      boolean isDebug = isDebug();
      for (Thread thread : threads) {
         try {
            if (isDebug) {
               thread.join();
            } else {
               thread.join(60_000);
            }
         } catch (InterruptedException e) {
            //
         }
         if (thread.isAlive()) {
            thread.interrupt();
            fail("thread still running");
         }
      }
      // forcibly stop node managers
      nodeManagers.forEach(nodeManager -> {
         try {
            nodeManager.stop();
         } catch (Exception e) {
            // won't prevent the test to complete
            e.printStackTrace();
         }
      });


      for (NodeRunner nodeRunner : nodeRunners) {
         if (nodeRunner.e != null) {
            nodeRunner.e.printStackTrace();
            fail(nodeRunner.e.getMessage());
         }
      }
      assertFalse(failedRenew.get(), "Some of the lease locks has failed to renew the locks");
   }

}
