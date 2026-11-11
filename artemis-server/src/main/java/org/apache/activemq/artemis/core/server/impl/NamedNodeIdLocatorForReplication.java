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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.TopologyMember;
import org.apache.activemq.artemis.core.server.NodeLocator;
import org.apache.activemq.artemis.utils.ConcurrentUtil;

/**
 * It looks for a server in the cluster with a specific NodeID
 */
public class NamedNodeIdLocatorForReplication extends NodeLocator {

   private final Lock lock = new ReentrantLock();
   private final Condition condition = lock.newCondition();
   private final String nodeID;
   private final long retryReplicationWait;
   private final Queue<Pair<TransportConfiguration, TransportConfiguration>> configurations = new LinkedList<>();
   private final List<Pair<TransportConfiguration, TransportConfiguration>> triedConfigurations = new ArrayList<>();
   private boolean found;

   public NamedNodeIdLocatorForReplication(String nodeID,
                                           BackupRegistrationListener backupRegistrationListener,
                                           long retryReplicationWait) {
      super(backupRegistrationListener);
      this.nodeID = nodeID;
      this.retryReplicationWait = retryReplicationWait;
   }

   @Override
   public void locateNode() throws ActiveMQException {
      locateNode(-1L);
   }

   @Override
   public void locateNode(long timeout) throws ActiveMQException {
      try {
         lock.lock();
         if (configurations.isEmpty()) {
            try {
               if (timeout != -1L) {
                  ConcurrentUtil.await(condition, timeout);
               } else {
                  while (configurations.isEmpty()) {
                     condition.await(retryReplicationWait, TimeUnit.MILLISECONDS);
                     configurations.addAll(triedConfigurations);
                     triedConfigurations.clear();
                  }
               }
            } catch (InterruptedException e) {
               //ignore
            }
         }
      } finally {
         lock.unlock();
      }
   }

   @Override
   public void nodeUP(TopologyMember topologyMember, boolean last) {
      try {
         lock.lock();
         if (nodeID.equals(topologyMember.getNodeId()) && topologyMember.getPrimary() != null) {
            Pair<TransportConfiguration, TransportConfiguration> configuration = new Pair<>(topologyMember.getPrimary(), topologyMember.getBackup());
            if (!configurations.contains(configuration)) {
               configurations.add(configuration);
            }
            found = true;
            condition.signal();
         }
      } finally {
         lock.unlock();
      }
   }

   @Override
   public void nodeDown(long eventUID, String nodeID) {
      //no op
   }

   @Override
   public String getNodeID() {
      return found ? nodeID : null;
   }

   @Override
   public Pair<TransportConfiguration, TransportConfiguration> getPrimaryConfiguration() {
      return configurations.peek();
   }

   @Override
   public void notifyRegistrationFailed(boolean alreadyReplicating) {
      try {
         lock.lock();
         triedConfigurations.add(configurations.poll());
         super.notifyRegistrationFailed(alreadyReplicating);
      } finally {
         lock.unlock();
      }
   }
}

