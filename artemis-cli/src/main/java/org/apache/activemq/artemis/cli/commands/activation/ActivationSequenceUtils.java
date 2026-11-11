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
package org.apache.activemq.artemis.cli.commands.activation;

import java.io.File;

import org.apache.activemq.artemis.core.server.NodeManager;
import org.apache.activemq.artemis.core.server.impl.FileLockNodeManager;

final class ActivationSequenceUtils {

   private ActivationSequenceUtils() {

   }

   public static NodeManager applyCoordinationId(final String nodeId,
                                                 final NodeManager nodeManager,
                                                 final File nodeManagerLockLocation) throws Exception {
      final long activationSequence = nodeManager.getNodeActivationSequence();
      nodeManager.stop();
      // REVISIT: this is quite clunky, also in backup activation, we just need new nodeID persisted!
      FileLockNodeManager replicatedNodeManager = new FileLockNodeManager(nodeManagerLockLocation, true);
      replicatedNodeManager.start();
      replicatedNodeManager.setNodeID(nodeId);
      // create and persist server.lock file
      replicatedNodeManager.stopBackup();
      // despite NM is restarted as "replicatedBackup" we need the last written activation sequence value in-memory
      final long freshActivationSequence = replicatedNodeManager.readNodeActivationSequence();
      assert freshActivationSequence == activationSequence;
      return replicatedNodeManager;
   }

}
