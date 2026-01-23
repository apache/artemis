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

package org.apache.activemq.artemis.tests.smoke.lockmanager;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.activemq.artemis.tests.extensions.ThreadLeakCheckExtension;
import org.apache.curator.test.InstanceSpec;
import org.apache.curator.test.TestingCluster;
import org.apache.curator.test.TestingZooKeeperServer;

/**
 * This is encapsulating Zookeeper instances for tests
 * */
public class ZookeeperCluster {
   private TestingCluster testingServer;
   private InstanceSpec[] clusterSpecs;
   private int nodes;
   private final File root;

   public ZookeeperCluster(File root, int nodes, int basePort, int serverTickMS) throws IOException {
      this.root = root;
      this.nodes = nodes;
      clusterSpecs = new InstanceSpec[nodes];
      for (int i = 0; i < nodes; i++) {
         clusterSpecs[i] = new InstanceSpec(newFolder(root, "node" + i), basePort + i, -1, -1, true, -1, serverTickMS, -1);
      }
      testingServer = new TestingCluster(clusterSpecs);
   }

   public void start() throws Exception {
      testingServer.start();
   }

   public void stop() throws Exception {
      ThreadLeakCheckExtension.addKownThread("ListenerHandler-");
      testingServer.stop();
   }

   private static File newFolder(File root, String... subDirs) throws IOException {
      String subFolder = String.join("/", subDirs);
      File result = new File(root, subFolder);
      if (!result.mkdirs()) {
         throw new IOException("Couldn't create folders " + root);
      }
      return result;
   }

   public String getConnectString() {
      return testingServer.getConnectString();
   }

   public List<TestingZooKeeperServer> getServers() {
      return testingServer.getServers();
   }

   public int getNodes() {
      return nodes;
   }
}
