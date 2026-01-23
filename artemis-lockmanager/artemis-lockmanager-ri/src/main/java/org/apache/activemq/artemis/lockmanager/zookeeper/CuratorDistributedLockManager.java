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
package org.apache.activemq.artemis.lockmanager.zookeeper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.apache.activemq.artemis.lockmanager.DistributedLock;
import org.apache.activemq.artemis.lockmanager.DistributedLockManager;
import org.apache.activemq.artemis.lockmanager.MutableLong;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.atomic.DistributedAtomicLong;
import org.apache.curator.framework.recipes.locks.InterProcessSemaphoreV2;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.curator.retry.RetryForever;
import org.apache.curator.retry.RetryNTimes;
import org.apache.curator.utils.DebugUtils;

import static java.util.Objects.requireNonNull;

public class CuratorDistributedLockManager implements DistributedLockManager, ConnectionStateListener {

   enum PrimitiveType {
      lock, mutableLong;

      static <T extends CuratorDistributedPrimitive> T validatePrimitiveInstance(T primitive) {
         if (primitive == null) {
            return null;
         }
         boolean valid = switch (primitive.getId().type) {
            case lock -> primitive instanceof CuratorDistributedLock;
            case mutableLong -> primitive instanceof CuratorMutableLong;
         };
         if (!valid) {
            throw new AssertionError("Implementation error: " + primitive.getClass() + " is wrongly considered " + primitive.getId().type);
         }
         return primitive;
      }
   }

   static final class PrimitiveId {

      final String id;
      final PrimitiveType type;

      private PrimitiveId(String id, PrimitiveType type) {
         this.id = requireNonNull(id);
         this.type = requireNonNull(type);
      }

      static PrimitiveId of(String id, PrimitiveType type) {
         return new PrimitiveId(id, type);
      }

      @Override
      public boolean equals(Object obj) {
         if (this == obj) {
            return true;
         }
         if (!(obj instanceof PrimitiveId other)) {
            return false;
         }

         return Objects.equals(id, other.id) &&
                type == other.type;
      }

      @Override
      public int hashCode() {
         return Objects.hash(id, type);
      }
   }

   private CuratorFramework client;
   private final Map<PrimitiveId, CuratorDistributedPrimitive> primitives;
   private List<UnavailableManagerListener> listeners;
   private boolean unavailable;
   private boolean handlingEvents;
   private final CuratorFrameworkFactory.Builder curatorBuilder;

   static {
      // this is going to set curator/zookeeper log level as per https://cwiki.apache.org/confluence/display/CURATOR/TN8
      if (System.getProperty(DebugUtils.PROPERTY_LOG_EVENTS) == null) {
         System.setProperty(DebugUtils.PROPERTY_LOG_EVENTS, "false");
      }
   }

   CuratorDistributedLockManager(String connectString,
                                         String namespace,
                                         int sessionMs,
                                         int sessionPercent,
                                         int connectionMs,
                                         int retries,
                                         int retriesMs) {
      curatorBuilder = CuratorFrameworkFactory.builder()
         .connectString(connectString)
         .namespace(namespace)
         .sessionTimeoutMs(sessionMs)
         .connectionTimeoutMs(connectionMs)
         .retryPolicy(retries >= 0 ? new RetryNTimes(retries, retriesMs) : new RetryForever(retriesMs))
         .simulatedSessionExpirationPercent(sessionPercent);
      this.primitives = new HashMap<>();
      this.listeners = null;
      this.unavailable = false;
      this.handlingEvents = false;
   }

   @Override
   public synchronized boolean isStarted() {
      checkHandlingEvents();
      return client != null;
   }

   @Override
   public synchronized void addUnavailableManagerListener(UnavailableManagerListener listener) {
      checkHandlingEvents();
      if (listeners == null) {
         return;
      }
      listeners.add(listener);
      if (unavailable) {
         startHandlingEvents();
         try {
            listener.onUnavailableManagerEvent();
         } finally {
            completeHandlingEvents();
         }
      }
   }

   @Override
   public synchronized void removeUnavailableManagerListener(UnavailableManagerListener listener) {
      checkHandlingEvents();
      if (listeners == null) {
         return;
      }
      listeners.remove(listener);
   }

   @Override
   public synchronized boolean start(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException {
      checkHandlingEvents();
      if (timeout >= 0) {
         if (timeout > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("curator manager won't support too long timeout ie >" + Integer.MAX_VALUE);
         }
         requireNonNull(unit);
      }
      if (client != null) {
         return true;
      }
      final CuratorFramework client = curatorBuilder.build();
      try {
         client.start();
         if (!client.blockUntilConnected((int) timeout, unit)) {
            client.close();
            return false;
         }
         this.client = client;
         this.listeners = new CopyOnWriteArrayList<>();
         client.getConnectionStateListenable().addListener(this);
         return true;
      } catch (InterruptedException e) {
         client.close();
         throw e;
      }
   }

   @Override
   public synchronized void start() throws InterruptedException, ExecutionException {
      start(-1, null);
   }

   @Override
   public synchronized void stop() {
      checkHandlingEvents();
      final CuratorFramework client = this.client;
      if (client == null) {
         return;
      }
      this.client = null;
      unavailable = false;
      listeners.clear();
      this.listeners = null;
      client.getConnectionStateListenable().removeListener(this);
      primitives.forEach((id, primitive) -> {
         try {
            primitive.onRemoved();
         } catch (Throwable t) {
            // TODO log?
         }
      });
      primitives.clear();
      client.close();
   }

   private synchronized <T extends CuratorDistributedPrimitive> T getPrimitive(PrimitiveId id,
                                                                               Function<PrimitiveId, ? extends T> primitiveFactory) {
      checkHandlingEvents();
      requireNonNull(id);
      if (client == null) {
         throw new IllegalStateException("manager isn't started yet!");
      }
      final CuratorDistributedPrimitive primitive = PrimitiveType.validatePrimitiveInstance(primitives.get(id));
      if (primitive != null) {
         return (T) primitive;
      }
      final T newPrimitive = PrimitiveType.validatePrimitiveInstance(primitiveFactory.apply(id));
      primitives.put(id, newPrimitive);
      if (unavailable) {
         startHandlingEvents();
         try {
            newPrimitive.onLost();
         } finally {
            completeHandlingEvents();
         }
      }
      return newPrimitive;
   }

   @Override
   public DistributedLock getDistributedLock(String lockId) {
      return getPrimitive(PrimitiveId.of(lockId, PrimitiveType.lock),
                          id -> new CuratorDistributedLock(id, this,
                                                           new InterProcessSemaphoreV2(client, "/" + id.id + "/locks", 1)));
   }

   @Override
   public MutableLong getMutableLong(String mutableLongId) {
      return getPrimitive(PrimitiveId.of(mutableLongId, PrimitiveType.mutableLong),
                          id -> new CuratorMutableLong(id, this,
                                                       new DistributedAtomicLong(client, "/" + mutableLongId + "/activation-sequence", new RetryNTimes(0, 0))));
   }

   protected void startHandlingEvents() {
      handlingEvents = true;
   }

   protected void completeHandlingEvents() {
      handlingEvents = false;
   }

   protected void checkHandlingEvents() {
      if (client == null) {
         return;
      }
      if (handlingEvents) {
         throw new IllegalStateException("UnavailableManagerListener isn't supposed to modify the manager or its primitives on event handling!");
      }
   }

   @Override
   public synchronized void stateChanged(CuratorFramework client, ConnectionState newState) {
      if (this.client != client) {
         return;
      }
      if (unavailable) {
         return;
      }
      startHandlingEvents();
      try {
         switch (newState) {
            case LOST:
               unavailable = true;
               listeners.forEach(listener -> listener.onUnavailableManagerEvent());
               primitives.forEach((id, primitive) -> primitive.onLost());
               break;
            case RECONNECTED:
               primitives.forEach((id, primitive) -> primitive.onReconnected());
               break;
            case SUSPENDED:
               primitives.forEach((id, primitive) -> primitive.onSuspended());
               break;
         }
      } finally {
         completeHandlingEvents();
      }
   }

   /**
    * Used for testing purposes
    */
   public synchronized CuratorFramework getCurator() {
      checkHandlingEvents();
      return client;
   }

   public synchronized void remove(CuratorDistributedPrimitive primitive) {
      checkHandlingEvents();
      primitives.remove(primitive.getId());
   }
}
