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
package org.apache.activemq.artemis.core.server.impl;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ClusterTopologyListener;
import org.apache.activemq.artemis.api.core.client.TopologyMember;

final class BackupTopologyListener implements ClusterTopologyListener {

   private final CountDownLatch latch = new CountDownLatch(1);
   private final String ownId;
   private static final int WAIT_TIMEOUT = 60;

   // Transport configuration of this node
   private final TransportConfiguration myTc;

   BackupTopologyListener(String ownId, TransportConfiguration nodeTransportConfig) {
      this.ownId = ownId;
      this.myTc = nodeTransportConfig;
   }

   @Override
   public void nodeUP(TopologyMember topologyMember, boolean last) {

      // If the backup is this node then ignore.
      if (myTc.equals(topologyMember.getBackup())) {
         return;
      }

      final String nodeID = topologyMember.getNodeId();

      if (ownId.equals(nodeID) && topologyMember.getBackup() != null) {
         latch.countDown();
      }
   }

   @Override
   public void nodeDown(long eventUID, String nodeID) {
      // no-op
   }

   boolean waitForBackup() {
      try {
         return latch.await(WAIT_TIMEOUT, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
         return false;
      }
   }
}
