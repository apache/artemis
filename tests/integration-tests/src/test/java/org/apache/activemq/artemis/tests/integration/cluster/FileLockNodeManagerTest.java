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

import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.activemq.artemis.core.server.impl.FileLockNodeManager;
import org.apache.activemq.artemis.utils.SpawnedVMSupport;

public class FileLockNodeManagerTest extends NodeManagerTest {

   @Override
   public void performWork(NodeManagerAction... actions) throws Exception {
      List<Process> processes = new ArrayList<>();
      for (NodeManagerAction action : actions) {
         final String[] args = new String[action.works() + 1];
         args[0] = getTemporaryDir();
         action.getWork(args, 1);
         Process p = SpawnedVMSupport.spawnVM(this.getClass().getName(), "-Xms50m", "-Xmx512m", new String[0], true, true, args);
         processes.add(p);
      }
      for (Process process : processes) {
         process.waitFor();
      }
      for (Process process : processes) {
         if (process.exitValue() == 9) {
            fail("failed see output");
         }
      }

   }

   public static void main(String[] args) throws Exception {
      NodeManagerAction.execute(Arrays.copyOfRange(args, 1, args.length), new FileLockNodeManager(new File(args[0]), false));
   }
}
