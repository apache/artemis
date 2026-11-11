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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.lockmanager.DistributedLock;
import org.apache.activemq.artemis.lockmanager.UnavailableStateException;
import org.apache.activemq.artemis.lockmanager.zookeeper.CuratorDistributedLockManager.PrimitiveId;
import org.apache.curator.framework.recipes.locks.InterProcessSemaphoreV2;
import org.apache.curator.framework.recipes.locks.Lease;

final class CuratorDistributedLock extends CuratorDistributedPrimitive implements DistributedLock {

   private final InterProcessSemaphoreV2 ipcSem;
   private final List<UnavailableLockListener> listeners;
   private Lease lease;
   private byte[] leaseVersion;

   CuratorDistributedLock(PrimitiveId id, CuratorDistributedLockManager manager, InterProcessSemaphoreV2 ipcSem) {
      super(id, manager);
      this.ipcSem = ipcSem;
      this.listeners = new CopyOnWriteArrayList<>();
      this.leaseVersion = null;
   }

   @Override
   protected void handleReconnected() {
      super.handleReconnected();
      if (leaseVersion != null) {
         assert lease != null;
         try {
            if (Arrays.equals(lease.getData(), leaseVersion)) {
               return;
            }
            onLost();
         } catch (Exception e) {
            onLost();
         }
      }
   }

   @Override
   protected void handleLost() {
      super.handleLost();
      lease = null;
      leaseVersion = null;
      for (UnavailableLockListener listener : listeners) {
         listener.onUnavailableLockEvent();
      }
   }

   @Override
   public String getLockId() {
      return getId().id;
   }

   @Override
   public boolean isHeldByCaller() throws UnavailableStateException {
      return run(() -> {
         checkUnavailable();
         if (lease == null) {
            return false;
         }
         assert leaseVersion != null;
         try {
            return Arrays.equals(lease.getData(), leaseVersion);
         } catch (Throwable t) {
            throw new UnavailableStateException(t);
         }
      });
   }

   @Override
   public boolean tryLock() throws UnavailableStateException, InterruptedException {
      return tryRun(() -> {
         if (lease != null) {
            throw new IllegalStateException("unlock first");
         }
         checkUnavailable();
         try {
            final byte[] leaseVersion = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
            ipcSem.setNodeData(leaseVersion);
            lease = ipcSem.acquire(0, TimeUnit.NANOSECONDS);
            if (lease == null) {
               ipcSem.setNodeData(null);
               return false;
            }
            this.leaseVersion = leaseVersion;
            assert Arrays.equals(lease.getData(), leaseVersion);
            return true;
         } catch (InterruptedException ie) {
            throw ie;
         } catch (Throwable e) {
            throw new UnavailableStateException(e);
         }
      });
   }

   @Override
   public void unlock() throws UnavailableStateException {
      run(() -> {
         checkUnavailable();
         final Lease lease = this.lease;
         if (lease != null) {
            this.lease = null;
            this.leaseVersion = null;
            try {
               ipcSem.returnLease(lease);
            } catch (Throwable e) {
               throw new UnavailableStateException(e);
            }
         }
         return null;
      });
   }

   @Override
   public void addListener(UnavailableLockListener listener) {
      run(() -> {
         listeners.add(listener);
         fireUnavailableListener(listener::onUnavailableLockEvent);
         return null;
      });
   }

   @Override
   public void removeListener(UnavailableLockListener listener) {
      run(() -> {
         listeners.remove(listener);
         return null;
      });
   }

   @Override
   protected void handleClosed() {
      super.handleClosed();
      listeners.clear();
      final Lease lease = this.lease;
      if (lease == null) {
         return;
      }
      this.lease = null;
      if (isUnavailable()) {
         return;
      }
      try {
         ipcSem.returnLease(lease);
      } catch (Throwable t) {
         // TODO silent, but debug ;)
      }
   }
}
