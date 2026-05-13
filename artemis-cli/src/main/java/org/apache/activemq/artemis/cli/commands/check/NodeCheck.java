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

package org.apache.activemq.artemis.cli.commands.check;

import java.util.ArrayList;
import java.util.List;

import org.apache.activemq.artemis.api.core.management.NodeInfo;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "node", description = "Check a node.")
public class NodeCheck extends CheckAbstract {

   @Option(names = "--up", description = "Check that the node is started. This check is executed by default if there are no other checks.")
   private boolean up;

   @Option(names = "--diskUsage", description = "Disk usage percentage to check or -1 to use the max-disk-usage.")
   private Integer diskUsage;

   @Option(names = "--memoryUsage", description = "Memory usage percentage to check.")
   private Integer memoryUsage;

   @Deprecated(forRemoval = true)
   @Option(names = "--live", description = "Check that the node has a connected live.")
   private boolean live;

   @Option(names = "--primary", description = "Check that the node has a connected live.")
   private boolean primary;

   @Option(names = "--backup", description = "Check that the node has a connected backup.")
   private boolean backup;

   @Option(names = "--peers", description = "Number of peers to check.")
   private Integer peers;

   public boolean isUp() {
      return up;
   }

   public void setUp(boolean up) {
      this.up = up;
   }

   public Integer getDiskUsage() {
      return diskUsage;
   }

   public void setDiskUsage(Integer diskUsage) {
      this.diskUsage = diskUsage;
   }

   public Integer getMemoryUsage() {
      return memoryUsage;
   }

   public void setMemoryUsage(Integer memoryUsage) {
      this.memoryUsage = memoryUsage;
   }

   public boolean isPrimary() {
      return live || primary;
   }

   public void setLive(boolean live) {
      this.live = live;
   }

   public void setPrimary(boolean primary) {
      this.primary = primary;
   }

   public boolean isBackup() {
      return backup;
   }

   public void setBackup(boolean backup) {
      this.backup = backup;
   }

   public Integer getPeers() {
      return peers;
   }

   public void setPeers(Integer peers) {
      this.peers = peers;
   }

   @Override
   protected CheckTask[] getCheckTasks() {
      List<CheckTask> checkTasks = new ArrayList<>();

      if (primary || live) {
         checkTasks.add(new CheckTask("the node has a primary", this::checkNodePrimary));
      }

      if (backup) {
         checkTasks.add(new CheckTask("the node has a backup", this::checkNodeBackup));
      }

      if (peers != null) {
         if (peers > 0) {
            checkTasks.add(new CheckTask(String.format("there are %d peers", peers), this::checkNodePeers));
         } else {
            throw new IllegalArgumentException("Invalid peers number to check: " + peers);
         }
      }

      if (diskUsage != null) {
         if (diskUsage == -1) {
            checkTasks.add(new CheckTask("the disk usage is less then the max-disk-usage", this::checkNodeDiskUsage));
         } else if (diskUsage > 0 && diskUsage < 100) {
            checkTasks.add(new CheckTask("the disk usage is less then " + diskUsage, this::checkNodeDiskUsage));
         } else {
            throw new IllegalArgumentException("Invalid disk usage percentage: " + diskUsage);
         }
      }

      if (memoryUsage != null) {
         if (memoryUsage > 0 && memoryUsage < 100) {
            checkTasks.add(new CheckTask("the memory usage is less then " + memoryUsage, this::checkNodeMemoryUsage));
         } else {
            throw new IllegalArgumentException("Invalid memory usage percentage: " + memoryUsage);
         }
      }

      if (up || checkTasks.isEmpty()) {
         checkTasks.add(0, new CheckTask("the node is started", this::checkNodeUp));
      }

      return checkTasks.toArray(new CheckTask[checkTasks.size()]);
   }

   private void checkNodeUp(final CheckContext context) throws Exception {
      if (!context.getManagementProxy().getAttribute("broker", "Started", Boolean.class, 0)) {
         throw new CheckException("The node isn't started.");
      }
   }

   private void checkNodePrimary(final CheckContext context) throws Exception {
      String nodeId = getName();

      if (nodeId == null) {
         nodeId = context.getNodeId();
      }

      NodeInfo node = context.getTopology().get(nodeId);

      if (node == null || node.getPrimary() == null) {
         throw new CheckException("No primary found for the node " + nodeId);
      }
   }

   private void checkNodeBackup(final CheckContext context) throws Exception {
      String nodeId = getName();

      if (nodeId == null) {
         nodeId = context.getNodeId();
      }

      NodeInfo node = context.getTopology().get(nodeId);

      if (node == null || node.getBackup() == null) {
         throw new CheckException("No backup found for the node " + nodeId);
      }
   }

   private void checkNodePeers(final CheckContext context) throws Exception {
      int topologyPeers = context.getTopology().values().stream().
         mapToInt(node -> (node.getPrimary() != null ? 1 : 0) +
            (node.getBackup() != null ? 1 : 0)).sum();

      if (topologyPeers < peers) {
         throw new CheckException("Insufficient peers: " + peers);
      }
   }

   private void checkNodeDiskUsage(final CheckContext context) throws Exception {
      Integer maxDiskUsage;

      if (diskUsage == -1) {
         maxDiskUsage = context.getManagementProxy().
            getAttribute("broker", "MaxDiskUsage", Integer.class, 0);
      } else {
         maxDiskUsage = diskUsage;
      }

      Double diskStoreUsage = context.getManagementProxy().
         getAttribute("broker", "DiskStoreUsage", Double.class, 0);

      checkNodeResourceUsage("DiskStoreUsage", (int)(diskStoreUsage * 100), maxDiskUsage);
   }

   private void checkNodeMemoryUsage(final CheckContext context) throws Exception {
      int addressMemoryUsagePercentage = context.getManagementProxy().
         getAttribute("broker", "AddressMemoryUsagePercentage", Integer.class, 0);

      checkNodeResourceUsage("MemoryUsage", addressMemoryUsagePercentage, memoryUsage);
   }

   private void checkNodeResourceUsage(final String resourceName, final int usageValue, final int thresholdValue) throws Exception {
      if (usageValue > thresholdValue) {
         throw new CheckException("The " + resourceName + " " + usageValue + " is less than " + thresholdValue);
      }
   }
}
