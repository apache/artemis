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

import org.apache.activemq.artemis.lockmanager.UnavailableStateException;
import org.apache.activemq.artemis.lockmanager.zookeeper.CuratorDistributedLockManager.PrimitiveId;

import static org.apache.activemq.artemis.lockmanager.zookeeper.CuratorDistributedLockManager.PrimitiveType.validatePrimitiveInstance;

public abstract class CuratorDistributedPrimitive implements AutoCloseable {

   // this is used to prevent deadlocks on close
   private final CuratorDistributedLockManager manager;
   private final PrimitiveId id;

   private boolean unavailable;
   private boolean closed;

   protected CuratorDistributedPrimitive(PrimitiveId id, CuratorDistributedLockManager manager) {
      this.id = id;
      this.manager = manager;
      this.closed = false;
      this.unavailable = false;
      validatePrimitiveInstance(this);
   }

   final PrimitiveId getId() {
      return id;
   }

   final void onReconnected() {
      synchronized (manager) {
         if (closed || unavailable) {
            return;
         }
         handleReconnected();
      }
   }

   protected void handleReconnected() {

   }

   final void onLost() {
      synchronized (manager) {
         if (closed || unavailable) {
            return;
         }
         unavailable = true;
         handleLost();
      }
   }

   protected void handleLost() {

   }

   final void onSuspended() {
      synchronized (manager) {
         if (closed || unavailable) {
            return;
         }
         handleSuspended();
      }
   }

   protected void handleSuspended() {

   }

   final void onRemoved() {
      close(false);
   }

   private void checkNotClosed() {
      if (closed) {
         throw new IllegalStateException("This lock is closed");
      }
   }

   @FunctionalInterface
   protected interface PrimitiveAction<R, T extends Throwable> {

      R call() throws T;
   }

   @FunctionalInterface
   protected interface InterruptablePrimitiveAction<R, T extends Throwable> {

      R call() throws InterruptedException, T;
   }

   protected final void checkUnavailable() throws UnavailableStateException {
      if (unavailable) {
         throw new UnavailableStateException(id.type + " with id = " + id.id + " isn't available");
      }
   }

   protected final void fireUnavailableListener(Runnable task) {
      run(() -> {
         if (!unavailable) {
            return false;
         }
         manager.startHandlingEvents();
         try {
            task.run();
         } finally {
            manager.completeHandlingEvents();
         }
         return true;
      });
   }

   protected final <R, T extends Throwable> R run(PrimitiveAction<R, T> action) throws T {
      synchronized (manager) {
         manager.checkHandlingEvents();
         checkNotClosed();
         return action.call();
      }
   }

   protected final <R, T extends Throwable> R tryRun(InterruptablePrimitiveAction<R, T> action) throws InterruptedException, T {
      synchronized (manager) {
         manager.checkHandlingEvents();
         checkNotClosed();
         return action.call();
      }
   }

   private void close(boolean remove) {
      synchronized (manager) {
         manager.checkHandlingEvents();
         if (closed) {
            return;
         }
         closed = true;
         if (remove) {
            manager.remove(this);
         }
         handleClosed();
      }
   }

   protected void handleClosed() {

   }

   protected final boolean isUnavailable() {
      synchronized (manager) {
         return unavailable;
      }
   }

   @Override
   public final void close() {
      close(true);
   }
}
