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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
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
 * This implementation looks for any available node, once tried with no success it is marked as tried and the next
 * available is used.
 */
public class AnyNodeLocatorForReplication extends NodeLocator {

   private final Lock lock = new ReentrantLock();
   private final Condition condition = lock.newCondition();
   private final ActiveMQServerImpl server;
   private final long retryReplicationWait;
   Map<String, Pair<TransportConfiguration, TransportConfiguration>> untriedConnectors = new HashMap<>();
   Map<String, Pair<TransportConfiguration, TransportConfiguration>> triedConnectors = new HashMap<>();

   private String nodeID;

   public AnyNodeLocatorForReplication(BackupRegistrationListener backupRegistrationListener,
                                       ActiveMQServerImpl server, long retryReplicationWait) {
      super(backupRegistrationListener);
      this.server = server;
      this.retryReplicationWait = retryReplicationWait;
   }

   @Override
   public void locateNode() throws ActiveMQException {
      locateNode(-1L);
   }

   @Override
   public void locateNode(long timeout) throws ActiveMQException {
      //first time
      try {
         lock.lock();
         if (untriedConnectors.isEmpty()) {
            try {
               if (timeout != -1L) {
                  ConcurrentUtil.await(condition, timeout);
               } else {
                  while (untriedConnectors.isEmpty()) {
                     condition.await(retryReplicationWait, TimeUnit.MILLISECONDS);
                     untriedConnectors.putAll(triedConnectors);
                     triedConnectors.clear();
                  }
               }
            } catch (InterruptedException e) {

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
         Pair<TransportConfiguration, TransportConfiguration> connector = new Pair<>(topologyMember.getPrimary(), topologyMember.getBackup());

         if (server.checkBrokerIsNotColocated(topologyMember.getNodeId())) {
            untriedConnectors.put(topologyMember.getNodeId(), connector);
            condition.signal();
         }
      } finally {
         lock.unlock();
      }
   }

   /**
    * if a node goes down we try all the connectors again as one may now be available for replication
    * <p>
    * TODO: there will be a better way to do this by finding which nodes backup has gone down.
    */
   @Override
   public void nodeDown(long eventUID, String nodeID) {
      try {
         lock.lock();
         untriedConnectors.putAll(triedConnectors);
         triedConnectors.clear();
         if (!untriedConnectors.isEmpty()) {
            condition.signal();
         }
      } finally {
         lock.unlock();
      }
   }

   @Override
   public String getNodeID() {
      return nodeID;
   }

   @Override
   public Pair<TransportConfiguration, TransportConfiguration> getPrimaryConfiguration() {
      try {
         lock.lock();
         Iterator<String> iterator = untriedConnectors.keySet().iterator();
         //sanity check but this should never happen
         if (iterator.hasNext()) {
            nodeID = iterator.next();
         }
         return untriedConnectors.get(nodeID);
      } finally {
         lock.unlock();
      }
   }

   @Override
   public void notifyRegistrationFailed(boolean alreadyReplicating) {
      try {
         lock.lock();
         Pair<TransportConfiguration, TransportConfiguration> tc = untriedConnectors.remove(nodeID);
         //it may have been removed
         if (tc != null) {
            triedConnectors.put(nodeID, tc);
         }
      } finally {
         lock.unlock();
      }
      super.notifyRegistrationFailed(alreadyReplicating);
   }
}
