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
package org.apache.activemq.artemis.core.client.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ClusterTopologyListener;
import org.apache.activemq.artemis.core.client.ActiveMQClientLogger;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.remoting.Connector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * A {@code Topology} describes the other cluster nodes that this server knows about.
 */
public final class Topology {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final String PRE_AUTH_NODE_ID = "00000000-0000-0000-0000-000000000000";
   public static final String PRE_AUTH_CONNECTOR_NAME = "PRE_AUTH_CONNECTOR";

   private final Set<ClusterTopologyListener> topologyListeners;

   private Executor executor;

   /**
    * Used to debug operations.
    * <p>
    * Someone may argue this is not needed. But it's impossible to debug anything related to topology without knowing
    * what node or what object missed a Topology update. Hence I added some information to locate debugging here.
    */
   private volatile Object owner;

   private final TopologyManager manager;

   /**
    * Keys are node IDs. Values are a pair of primary/backup transport configurations.
    */
   private final Map<String, TopologyMemberImpl> topology;

   private Map<String, Long> mapDelete;

   private static final class DirectExecutor implements Executor {

      @Override
      public void execute(final Runnable runnable) {
         runnable.run();
      }
   }

   public Topology(final Object owner) {
      this(owner, new DirectExecutor());
   }

   public Topology(final Object owner, final Executor executor) {
      this.topologyListeners = new HashSet<>();
      this.topology = new ConcurrentHashMap<>();
      if (executor == null) {
         throw new IllegalArgumentException("Executor is required");
      }
      this.executor = executor;
      this.owner = owner;
      if (owner instanceof TopologyManager topologyManager) {
         manager = topologyManager;
      } else {
         manager = null;
      }
      if (logger.isTraceEnabled()) {
         logger.trace("Topology@{} CREATE", Integer.toHexString(System.identityHashCode(this)), new Exception("trace"));
      }
   }

   public Topology setExecutor(Executor executor) {
      this.executor = executor;
      return this;
   }

   /**
    * It will remove all elements as if it haven't received anyone from the server.
    */
   public synchronized void clear() {
      topology.clear();
   }

   public void addClusterTopologyListener(final ClusterTopologyListener listener) {
      if (logger.isTraceEnabled()) {
         logger.trace("{}::Adding topology listener {}", this, listener, new Exception("Trace"));
      }
      synchronized (topologyListeners) {
         topologyListeners.add(listener);
      }
      this.sendTopology(listener);
   }

   public void removeClusterTopologyListener(final ClusterTopologyListener listener) {
      if (logger.isTraceEnabled()) {
         logger.trace("{}::Removing topology listener {}", this, listener, new Exception("Trace"));
      }
      synchronized (topologyListeners) {
         topologyListeners.remove(listener);
      }
   }

   /**
    * This is called by the server when the node is activated from backup state. It will always succeed
    */
   public void updateAsPrimary(final String nodeId, final TopologyMemberImpl memberInput) {
      synchronized (this) {
         if (logger.isDebugEnabled()) {
            logger.debug("{}::node {}={}", this, nodeId, memberInput);
         }
         memberInput.setUniqueEventID(System.currentTimeMillis());
         topology.remove(nodeId);
         topology.put(nodeId, memberInput);
         sendMemberUp(nodeId, memberInput);
      }
   }

   /**
    * After the node is started, it will resend the notifyPrimary a couple of times to avoid gossip between two servers
    */
   public void resendNode(final String nodeId) {
      synchronized (this) {
         TopologyMemberImpl memberInput = topology.get(nodeId);
         if (memberInput != null) {
            memberInput.setUniqueEventID(System.currentTimeMillis());
            sendMemberUp(nodeId, memberInput);
         }
      }
   }

   /**
    * This is called by the server when the node is activated from backup state. It will always succeed
    */
   public TopologyMemberImpl updateBackup(final TopologyMemberImpl memberInput) {
      final String nodeId = memberInput.getNodeId();
      if (logger.isTraceEnabled()) {
         logger.trace("{}::updateBackup::{}, memberInput={}", this, nodeId, memberInput);
      }

      synchronized (this) {
         TopologyMemberImpl currentMember = getMember(nodeId);
         if (currentMember == null) {
            if (logger.isTraceEnabled()) {
               logger.trace("There's no primary to be updated on backup update, node={} memberInput={}", nodeId, memberInput, new Exception("trace"));
            }

            currentMember = memberInput;
            topology.put(nodeId, currentMember);
         }

         TopologyMemberImpl newMember = new TopologyMemberImpl(nodeId, currentMember.getBackupGroupName(), currentMember.getScaleDownGroupName(), currentMember.getPrimary(), memberInput.getBackup());
         newMember.setUniqueEventID(System.currentTimeMillis());
         topology.remove(nodeId);
         topology.put(nodeId, newMember);
         sendMemberUp(nodeId, newMember);

         return newMember;
      }
   }

   /**
    * Update a member
    *
    * @param uniqueEventID an unique identifier for when the change was made. We will use current time millis for
    *                      starts, and a ++ of that number for shutdown.
    * @return {@code true} if an update did take place. Note that backups are *always* updated
    */
   public boolean updateMember(final long uniqueEventID, final String nodeId, final TopologyMemberImpl memberInput) {

      Long deleteTme = getMapDelete().get(nodeId);
      if (deleteTme != null && uniqueEventID != 0 && uniqueEventID < deleteTme) {
         if (logger.isDebugEnabled()) {
            logger.debug("Update uniqueEvent={}, nodeId={}, memberInput={} being rejected as there was a delete done after that",
                         uniqueEventID, nodeId, memberInput);
         }
         return false;
      }

      if (manager != null && !manager.updateMember(uniqueEventID, nodeId, memberInput)) {
         logger.debug("TopologyManager rejected the update towards {}", memberInput);
         return false;
      }

      synchronized (this) {
         TopologyMemberImpl currentMember = topology.get(nodeId);

         if (currentMember == null) {
            if (logger.isTraceEnabled()) {
               logger.trace("{}::NewMemberAdd nodeId={} member = {}", this, nodeId, memberInput, new Exception("trace"));
            }
            memberInput.setUniqueEventID(uniqueEventID);
            topology.put(nodeId, memberInput);
            sendMemberUp(nodeId, memberInput);
            return true;
         }
         if (uniqueEventID > currentMember.getUniqueEventID() || (currentMember.getPrimary() == null && memberInput.getPrimary() != null)) {
            TopologyMemberImpl newMember = new TopologyMemberImpl(nodeId, memberInput.getBackupGroupName(), memberInput.getScaleDownGroupName(), memberInput.getPrimary(), memberInput.getBackup());

            if (newMember.getPrimary() == null && currentMember.getPrimary() != null) {
               newMember.setPrimary(currentMember.getPrimary());
            }

            if (newMember.getBackup() == null && currentMember.getBackup() != null) {
               newMember.setBackup(currentMember.getBackup());
            }

            if (logger.isTraceEnabled()) {
               logger.trace("{}::updated currentMember=nodeID={}, currentMember={}, memberInput={} newMember={}",
                            this, nodeId, currentMember, memberInput, newMember, new Exception("trace"));
            }

            if (uniqueEventID > currentMember.getUniqueEventID()) {
               newMember.setUniqueEventID(uniqueEventID);
            } else {
               newMember.setUniqueEventID(currentMember.getUniqueEventID());
            }

            topology.remove(nodeId);
            topology.put(nodeId, newMember);
            sendMemberUp(nodeId, newMember);

            return true;
         }
         /*
          * always add the backup, better to try to reconnect to something that's not there then to
          * not know about it at all
          */
         if (currentMember.getBackup() == null && memberInput.getBackup() != null) {
            currentMember.setBackup(memberInput.getBackup());
         }
         return false;
      }
   }

   private void sendMemberUp(final String nodeId, final TopologyMemberImpl memberToSend) {
      final List<ClusterTopologyListener> copy = copyListeners();

      if (logger.isTraceEnabled()) {
         logger.trace("{}::prepare to send {} to {} elements", this, nodeId, copy.size());
      }

      if (!copy.isEmpty()) {
         executor.execute(() -> {
            for (ClusterTopologyListener listener : copy) {
               if (logger.isTraceEnabled()) {
                  logger.trace("{} informing {} about node up = {} connector = {}", Topology.this, listener, nodeId, memberToSend.getConnector());
               }

               try {
                  listener.nodeUP(memberToSend, false);
               } catch (Throwable e) {
                  ActiveMQClientLogger.LOGGER.errorSendingTopology(e);
               }
            }
         });
      }
   }

   private List<ClusterTopologyListener> copyListeners() {
      List<ClusterTopologyListener> listenersCopy;
      synchronized (topologyListeners) {
         listenersCopy = new ArrayList<>(topologyListeners);
      }
      return listenersCopy;
   }

   boolean removeMember(final long uniqueEventID, final String nodeId, final boolean disconnect) {
      TopologyMemberImpl member;


      if (manager != null && !manager.removeMember(uniqueEventID, nodeId, disconnect)) {
         logger.debug("TopologyManager rejected the update towards {}", nodeId);
         return false;
      }

      synchronized (this) {
         member = topology.get(nodeId);
         if (member != null) {
            if (member.getUniqueEventID() > uniqueEventID) {
               logger.debug("The removeMember was issued before the node {} was started, ignoring call", nodeId);
               member = null;
            } else {
               getMapDelete().put(nodeId, uniqueEventID);
               member = topology.remove(nodeId);
            }
         }
      }

      if (logger.isTraceEnabled()) {
         logger.trace("removeMember {} removing nodeID={}, result={}, size = {}",
                      this, nodeId, member, topology.size(), new Exception("trace"));
      }

      if (member != null) {
         final List<ClusterTopologyListener> copy = copyListeners();

         executor.execute(new Runnable() {
            @Override
            public void run() {
               for (ClusterTopologyListener listener : copy) {
                  if (logger.isTraceEnabled()) {
                     logger.trace("{} informing {} about node down = {}", this, listener, nodeId);
                  }
                  try {
                     listener.nodeDown(uniqueEventID, nodeId);
                  } catch (Exception e) {
                     ActiveMQClientLogger.LOGGER.errorSendingTopologyNodedown(e);
                  }
               }
            }
         });
      }
      return member != null;
   }

   public synchronized void sendTopology(final ClusterTopologyListener listener) {
      logger.debug("{} is sending topology to {}", this, listener);

      executor.execute(() -> {
         int count = 0;

         final Map<String, TopologyMemberImpl> copy;

         synchronized (Topology.this) {
            copy = new HashMap<>(topology);
         }

         for (Entry<String, TopologyMemberImpl> entry : copy.entrySet()) {
            if (logger.isDebugEnabled()) {
               logger.debug("{} sending {} / {} to {}", Topology.this, entry.getKey(), entry.getValue().getConnector(), listener);
            }
            listener.nodeUP(entry.getValue(), ++count == copy.size());
         }
      });
   }

   public synchronized TopologyMemberImpl getMember(final String nodeID) {
      return topology.get(nodeID);
   }

   public synchronized TopologyMemberImpl getMember(final RemotingConnection rc) {
      for (TopologyMemberImpl member : topology.values()) {
         if (member.isMember(rc)) {
            return member;
         }
      }

      return null;
   }

   public synchronized boolean isEmpty() {
      return topology.isEmpty();
   }

   public Collection<TopologyMemberImpl> getMembers() {
      List<TopologyMemberImpl> members;
      synchronized (this) {
         members = new ArrayList<>(topology.values());
      }
      return members;
   }

   synchronized int nodes() {
      int count = 0;
      for (TopologyMemberImpl member : topology.values()) {
         if (member.getPrimary() != null) {
            count++;
         }
         if (member.getBackup() != null) {
            count++;
         }
      }
      return count;
   }

   public synchronized String describe() {
      return describe("");
   }

   private synchronized String describe(final String text) {
      StringBuilder desc = new StringBuilder(text + "topology on " + this + ":\n");
      for (Entry<String, TopologyMemberImpl> entry : new HashMap<>(topology).entrySet()) {
         desc.append("\t").append(entry.getKey()).append(" => ").append(entry.getValue()).append("\n");
      }
      desc.append("\t" + "nodes=").append(nodes()).append("\t").append("members=").append(members());
      if (topology.isEmpty()) {
         desc.append("\tEmpty");
      }
      return desc.toString();
   }

   private int members() {
      return topology.size();
   }

   /**
    * The owner exists mainly for debug purposes. When enabling logging and tracing, the Topology updates will include
    * the owner, what will enable to identify what instances are receiving the updates, what will enable better
    * debugging.
    */
   public void setOwner(final Object owner) {
      this.owner = owner;
   }

   public TransportConfiguration getBackupForConnector(final Connector connector) {
      for (TopologyMemberImpl member : topology.values()) {
         if (member.getPrimary() != null && connector.isEquivalent(member.getPrimary().getParams())) {
            return member.getBackup();
         }
      }
      return null;
   }

   @Override
   public String toString() {
      if (owner == null) {
         return "Topology@" + Integer.toHexString(System.identityHashCode(this));
      }
      return "Topology@" + Integer.toHexString(System.identityHashCode(this)) + "[owner=" + owner + "]";
   }

   private synchronized Map<String, Long> getMapDelete() {
      if (mapDelete == null) {
         mapDelete = new ConcurrentHashMap<>();
      }
      return mapDelete;
   }

}
