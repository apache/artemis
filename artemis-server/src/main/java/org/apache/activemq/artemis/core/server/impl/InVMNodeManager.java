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

import java.io.File;
import java.util.concurrent.Semaphore;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.ActivateCallback;
import org.apache.activemq.artemis.utils.UUIDGenerator;

import static org.apache.activemq.artemis.core.server.impl.InVMNodeManager.State.FAILING_BACK;
import static org.apache.activemq.artemis.core.server.impl.InVMNodeManager.State.ACTIVE;
import static org.apache.activemq.artemis.core.server.impl.InVMNodeManager.State.NOT_STARTED;
import static org.apache.activemq.artemis.core.server.impl.InVMNodeManager.State.PAUSED;

/**
 * NodeManager used to run multiple servers in the same VM.
 * <p>
 * We use the {@link org.apache.activemq.artemis.core.server.impl.InVMNodeManager} instead of
 * {@link org.apache.activemq.artemis.core.server.impl.FileLockNodeManager} when multiple servers are run inside the
 * same VM and File Locks can not be shared in the same VM (it would cause a shared lock violation).
 */
public final class InVMNodeManager extends FileBasedNodeManager {

   private final Semaphore primaryLock;

   private final Semaphore backupLock;

   public enum State {
      ACTIVE, PAUSED, FAILING_BACK, NOT_STARTED
   }

   public volatile State state = NOT_STARTED;

   public long failoverPause = 0L;

   public InVMNodeManager(boolean replicatedBackup) {
      this(replicatedBackup, null);
      if (replicatedBackup) {
         throw new RuntimeException("if replicated-backup, we need its journal directory");
      }
   }

   public InVMNodeManager(boolean replicatedBackup, File directory) {
      super(replicatedBackup, directory);
      primaryLock = new Semaphore(1);
      backupLock = new Semaphore(1);
      setUUID(UUIDGenerator.getInstance().generateUUID());
   }

   @Override
   public synchronized void start() throws Exception {
      if (isStarted()) {
         return;
      }
      super.start();
      readNodeActivationSequence();
   }

   @Override
   public void awaitPrimaryNode() throws InterruptedException {
      do {
         while (state == NOT_STARTED) {
            Thread.sleep(10);
         }

         primaryLock.acquire();

         if (state == PAUSED) {
            primaryLock.release();
            Thread.sleep(10);
         } else if (state == FAILING_BACK) {
            primaryLock.release();
            Thread.sleep(10);
         } else if (state == ACTIVE) {
            break;
         }
      }
      while (true);
      if (failoverPause > 0L) {
         Thread.sleep(failoverPause);
      }
   }

   @Override
   public void awaitActiveStatus() throws InterruptedException {
      while (state != ACTIVE) {
         Thread.sleep(10);
      }
   }

   @Override
   public void startBackup() throws InterruptedException {
      backupLock.acquire();
   }

   @Override
   public ActivateCallback startPrimaryNode() throws InterruptedException {
      state = FAILING_BACK;
      primaryLock.acquire();
      return new CleaningActivateCallback() {
         @Override
         public void activationComplete() {
            state = ACTIVE;
         }
      };
   }

   @Override
   public void pausePrimaryServer() {
      state = PAUSED;
      primaryLock.release();
   }

   @Override
   public void crashPrimaryServer() {
      primaryLock.release();
   }

   @Override
   public boolean isAwaitingFailback() {
      return state == FAILING_BACK;
   }

   @Override
   public boolean isBackupActive() {
      return primaryLock.availablePermits() == 0;
   }

   @Override
   public void interrupt() {
      //
   }

   @Override
   public void releaseBackup() {
      backupLock.release();
   }

   @Override
   public SimpleString readNodeId() {
      return getNodeId();
   }
}
