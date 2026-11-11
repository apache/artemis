/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.server.routing.pools;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.server.routing.targets.Target;
import org.apache.activemq.artemis.core.server.routing.targets.TargetFactory;
import org.apache.activemq.artemis.core.server.routing.targets.TargetMonitor;
import org.apache.activemq.artemis.core.server.routing.targets.TargetProbe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;

public abstract class AbstractPool implements Pool {
   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final TargetFactory targetFactory;

   private final ScheduledExecutorService scheduledExecutor;

   private final int checkPeriod;

   private final List<TargetProbe> targetProbes = new ArrayList<>();

   private final Map<Target, TargetMonitor> targets = new ConcurrentHashMap<>();

   private final List<TargetMonitor> targetMonitors = new CopyOnWriteArrayList<>();

   private String username;

   private String password;

   private int quorumSize;

   private int quorumTimeout;

   private long quorumTimeoutNanos;

   private final long quorumParkNanos = TimeUnit.MILLISECONDS.toNanos(100);

   private volatile boolean started = false;


   @Override
   public String getUsername() {
      return username;
   }

   @Override
   public void setUsername(String username) {
      this.username = username;
   }

   @Override
   public String getPassword() {
      return password;
   }

   @Override
   public void setPassword(String password) {
      this.password = password;
   }

   @Override
   public int getCheckPeriod() {
      return checkPeriod;
   }

   @Override
   public int getQuorumSize() {
      return quorumSize;
   }

   @Override
   public int getQuorumTimeout() {
      return quorumTimeout;
   }

   @Override
   public void setQuorumTimeout(int quorumTimeout) {
      this.quorumTimeout = quorumTimeout;
      this.quorumTimeoutNanos = TimeUnit.MILLISECONDS.toNanos(quorumTimeout);
   }

   @Override
   public void setQuorumSize(int quorumSize) {
      this.quorumSize = quorumSize;
   }

   @Override
   public List<Target> getAllTargets() {
      return targetMonitors.stream().map(targetMonitor -> targetMonitor.getTarget()).collect(Collectors.toList());
   }

   @Override
   public List<Target> getTargets() {
      List<Target> targets = targetMonitors.stream().filter(targetMonitor -> targetMonitor.isTargetReady())
         .map(targetMonitor -> targetMonitor.getTarget()).collect(Collectors.toList());

      if (quorumTimeout > 0 && targets.size() < quorumSize) {
         final long deadline = System.nanoTime() + quorumTimeoutNanos;
         while (targets.size() < quorumSize && (System.nanoTime() - deadline) < 0) {
            targets = targetMonitors.stream().filter(targetMonitor -> targetMonitor.isTargetReady())
               .map(targetMonitor -> targetMonitor.getTarget()).collect(Collectors.toList());

            LockSupport.parkNanos(quorumParkNanos);
         }
      }

      if (logger.isDebugEnabled()) {
         logger.debug("Ready targets are {} / {} and quorumSize is {}", targets, targetMonitors, quorumSize);
      }

      return targets.size() < quorumSize ? Collections.emptyList() : targets;
   }

   @Override
   public List<TargetProbe> getTargetProbes() {
      return targetProbes;
   }

   @Override
   public boolean isStarted() {
      return started;
   }


   public AbstractPool(TargetFactory targetFactory, ScheduledExecutorService scheduledExecutor, int checkPeriod) {
      this.targetFactory = targetFactory;

      this.scheduledExecutor = scheduledExecutor;

      this.checkPeriod = checkPeriod;
   }

   @Override
   public Target getTarget(String nodeId) {
      for (TargetMonitor targetMonitor : targetMonitors) {
         if (nodeId.equals(targetMonitor.getTarget().getNodeID())) {
            return targetMonitor.getTarget();
         }
      }

      return null;
   }

   @Override
   public boolean isTargetReady(Target target) {
      TargetMonitor targetMonitor = targets.get(target);

      return targetMonitor != null ? targetMonitor.isTargetReady() : false;
   }

   @Override
   public Target getReadyTarget(String nodeId) {
      int readyTargets;
      long deadline = quorumTimeout > 0 ? System.nanoTime() + quorumTimeoutNanos : 0;

      do {
         readyTargets = 0;
         for (TargetMonitor targetMonitor : targetMonitors) {
            if (targetMonitor.isTargetReady()) {
               readyTargets++;
               if (nodeId.equals(targetMonitor.getTarget().getNodeID())) {
                  return targetMonitor.getTarget();
               }
            }
         }
      }
      while(readyTargets < quorumSize && deadline > 0 && (System.nanoTime() - deadline) < 0);

      return null;
   }

   @Override
   public void addTargetProbe(TargetProbe probe) {
      targetProbes.add(probe);
   }

   @Override
   public void removeTargetProbe(TargetProbe probe) {
      targetProbes.remove(probe);
   }

   @Override
   public void start() throws Exception {
      started = true;

      for (TargetMonitor targetMonitor : targetMonitors) {
         targetMonitor.start();
      }
   }

   @Override
   public void stop() throws Exception {
      started = false;

      List<TargetMonitor> targetMonitors = new ArrayList<>(this.targetMonitors);

      for (TargetMonitor targetMonitor : targetMonitors) {
         removeTarget(targetMonitor.getTarget());
      }
   }

   protected void addTarget(TransportConfiguration connector, String nodeID) {
      addTarget(targetFactory.createTarget(connector, nodeID));
   }

   @Override
   public boolean addTarget(Target target) {
      TargetMonitor targetMonitor = new TargetMonitor(scheduledExecutor, checkPeriod, target, targetProbes);

      if (targets.putIfAbsent(target, targetMonitor) != null) {
         return false;
      }

      targetMonitors.add(targetMonitor);

      if (started) {
         targetMonitor.start();
      }

      return true;
   }

   @Override
   public boolean removeTarget(Target target) {
      TargetMonitor targetMonitor = targets.remove(target);

      if (targetMonitor == null) {
         return false;
      }

      targetMonitors.remove(targetMonitor);

      targetMonitor.stop();

      return true;
   }
}
