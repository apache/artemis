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

import java.io.PrintStream;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.cli.commands.ActionContext;
import org.apache.activemq.artemis.cli.commands.tools.LockAbstract;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.HAPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.DistributedLockManagerConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicationBackupPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicationPrimaryPolicyConfiguration;
import org.apache.activemq.artemis.core.server.NodeManager;
import org.apache.activemq.artemis.core.server.impl.FileLockNodeManager;
import org.apache.activemq.artemis.lockmanager.DistributedLock;
import org.apache.activemq.artemis.lockmanager.DistributedLockManager;
import org.apache.activemq.artemis.lockmanager.MutableLong;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import static org.apache.activemq.artemis.cli.commands.activation.ActivationSequenceUtils.applyCoordinationId;

@Command(name = "set", description = "Set local and/or remote (i.e. coordinated) activation sequence.")
public class ActivationSequenceSet extends LockAbstract {

   private static final int MANAGER_START_TIMEOUT_SECONDS = 60;

   @Option(names = "--node-id", description = "Target sequence for this UUID overwriting the NodeID of this broker too. If not set, broker NodeID is used instead.")
   public String nodeId = null;

   @Option(names = "--remote", description = "Set just remote (i.e. coordinated) activation sequence.")
   public boolean remote = false;

   @Option(names = "--local", description = "Set just local activation sequence.")
   public boolean local = false;

   @Option(names = "--to", description = "The new activation sequence.", required = true)
   public long value;

   @Override
   public Object execute(ActionContext context) throws Exception {
      final Object output = super.execute(context);
      execute(this, getFileConfiguration(), context.out);
      return output;
   }

   /**
    * This has been exposed to ease testing it on integration tests: no need for brokerInstance
    */
   public static void execute(final ActivationSequenceSet command,
                              final Configuration config,
                              final PrintStream out) throws Exception {
      final String nodeId = command.nodeId;
      final boolean remote = command.remote;
      final boolean local = command.local;
      final long value = command.value;
      if (remote && local) {
         throw new IllegalArgumentException("Both --local and --remote cannot be present. To set both sequences just remove --local and --remote.");
      }
      if (value < 0) {
         throw new IllegalArgumentException("--to must be >= 0");
      }
      final HAPolicyConfiguration policyConfig = config.getHAPolicyConfiguration();
      final DistributedLockManagerConfiguration managerConfiguration;
      String coordinationId = nodeId;
      if (policyConfig instanceof ReplicationBackupPolicyConfiguration backupPolicyConfig) {
         managerConfiguration = backupPolicyConfig.getDistributedManagerConfiguration();
      } else if (policyConfig instanceof ReplicationPrimaryPolicyConfiguration primaryPolicyConfig) {
         managerConfiguration = primaryPolicyConfig.getDistributedManagerConfiguration();
         if (primaryPolicyConfig.getCoordinationId() != null) {
            if (nodeId != null) {
               throw new IllegalArgumentException("Forcing NodeID with multi-primary is not supported! Try again without --node-id");
            }
            coordinationId = primaryPolicyConfig.getCoordinationId();
         }
      } else {
         throw new UnsupportedOperationException("This command support just <primary> or <backup> replication configuration");
      }
      Objects.requireNonNull(managerConfiguration);
      NodeManager nodeManager = new FileLockNodeManager(config.getNodeManagerLockLocation(), false);
      nodeManager.start();
      try {
         if (coordinationId != null) {
            // force using coordinationId whatever it is - either for multi-primary or just forced through CLI
            if (nodeManager.getNodeId() == null || !nodeManager.getNodeId().toString().equals(coordinationId)) {
               nodeManager = applyCoordinationId(coordinationId, nodeManager, config.getNodeManagerLockLocation());
            }
         }
         final String localNodeId = nodeManager.getNodeId().toString();
         if (!remote) {
            final long localActivationSequence = nodeManager.getNodeActivationSequence();
            nodeManager.writeNodeActivationSequence(value);
            if (out != null) {
               if (localActivationSequence == NodeManager.NULL_NODE_ACTIVATION_SEQUENCE) {
                  out.println("Forced local activation sequence for NodeID=" + localNodeId + " to " + value);
               } else {
                  out.println("Forced local activation sequence for NodeID=" + localNodeId + " from " + localActivationSequence + " to " + value);
               }
            }
         }
         if (!local) {
            try (DistributedLockManager manager = DistributedLockManager.newInstanceOf(
               managerConfiguration.getClassName(), managerConfiguration.getProperties())) {
               if (!manager.start(MANAGER_START_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                  throw new IllegalStateException("distributed manager isn't started in " + MANAGER_START_TIMEOUT_SECONDS + " seconds");

               }
               try (MutableLong coordinatedActivationSequence = manager.getMutableLong(localNodeId);
                    DistributedLock primaryLock = manager.getDistributedLock(localNodeId)) {
                  if (!primaryLock.tryLock()) {
                     throw new IllegalStateException("Cannot safely set coordinated activation sequence for NodeID=" + localNodeId + ": primary lock is still held.");
                  }
                  final long remoteActivationSequence = coordinatedActivationSequence.get();
                  coordinatedActivationSequence.set(value);
                  if (out != null) {
                     out.println("Forced coordinated activation sequence for NodeID=" + localNodeId + " from " + remoteActivationSequence + " to " + value);
                  }
               }
            }
         }
      } finally {
         nodeManager.stop();
      }
   }

}
