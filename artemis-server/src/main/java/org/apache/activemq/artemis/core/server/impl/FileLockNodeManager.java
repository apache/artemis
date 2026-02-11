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
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.Date;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.ActivateCallback;
import org.apache.activemq.artemis.core.server.ActiveMQLockAcquisitionTimeoutException;
import org.apache.activemq.artemis.core.server.ActiveMQScheduledComponent;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.utils.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class FileLockNodeManager extends FileBasedNodeManager {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final int STATE_LOCK_POS = 0;

   private static final int PRIMARY_LOCK_POS = 1;

   private static final int BACKUP_LOCK_POS = 2;

   // value is 'L' for backwards compatibility
   private static final byte ACTIVE = 'L';

   private static final byte FAILINGBACK = 'F';

   private static final byte PAUSED = 'P';

   private static final byte NOT_STARTED = 'N';

   private static final long LOCK_ACCESS_FAILURE_WAIT_TIME_NANOS = TimeUnit.SECONDS.toNanos(2);

   private static final long LOCK_MONITOR_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(2);

   private volatile FileLock primaryLock;

   private FileLock backupLock;

   private final FileChannel[] lockChannels = new FileChannel[3];

   private long serverLockLastModified;

   private final long lockAcquisitionTimeoutNanos;

   protected boolean interrupted = false;

   private final ScheduledExecutorService scheduledPool;

   public FileLockNodeManager(final File directory, boolean replicatedBackup, ScheduledExecutorService scheduledPool) {
      super(replicatedBackup, directory);
      this.scheduledPool = scheduledPool;
      this.lockAcquisitionTimeoutNanos = -1;
   }

   public FileLockNodeManager(final File directory, boolean replicatedBackup) {
      super(replicatedBackup, directory);
      this.scheduledPool = null;
      this.lockAcquisitionTimeoutNanos = -1;
   }

   public FileLockNodeManager(final File directory,
                              boolean replicatedBackup,
                              long lockAcquisitionTimeout,
                              ScheduledExecutorService scheduledPool) {
      super(replicatedBackup, directory);
      this.scheduledPool = scheduledPool;
      this.lockAcquisitionTimeoutNanos = lockAcquisitionTimeout == -1 ? -1 : TimeUnit.MILLISECONDS.toNanos(lockAcquisitionTimeout);
   }

   @Override
   public synchronized void start() throws Exception {
      if (isStarted()) {
         return;
      }
      if (!replicatedBackup) {
         setUpServerLockFile();
      }

      super.start();
      readNodeActivationSequence();
   }

   @Override
   protected synchronized void setUpServerLockFile() throws IOException {
      super.setUpServerLockFile();

      lockChannels[0] = channel;
      serverLockLastModified = serverLockFile.lastModified();

      for (int i = 1; i < 3; i++) {
         if (lockChannels[i] != null && lockChannels[i].isOpen()) {
            continue;
         }
         File fileLock = newFile("serverlock." + i);
         if (!fileLock.exists()) {
            fileLock.createNewFile();
         }
         RandomAccessFile randomFileLock = new RandomAccessFile(fileLock, "rw");
         lockChannels[i] = randomFileLock.getChannel();
      }
   }

   @Override
   public synchronized void stop() throws Exception {
      for (FileChannel channel : lockChannels) {
         if (channel != null && channel.isOpen()) {
            try {
               channel.close();
            } catch (Throwable e) {
               // I do not want to interrupt a shutdown. If anything is wrong here, just log it
               // it could be a critical error or something like that throwing the system down
               logger.warn(e.getMessage(), e);
            }
         }
      }

      super.stop();
   }

   @Override
   public boolean isAwaitingFailback() throws NodeManagerException {
      return getState() == FileLockNodeManager.FAILINGBACK;
   }

   @Override
   public boolean isBackupActive() throws NodeManagerException {
      try {
         FileLock primaryAttemptLock;
         primaryAttemptLock = tryLock(FileLockNodeManager.PRIMARY_LOCK_POS);
         if (primaryAttemptLock == null) {
            return true;
         } else {
            primaryAttemptLock.release();
            return false;
         }
      } catch (IOException e) {
         throw new NodeManagerException(e);
      }
   }

   public boolean isPrimaryLocked() {
      return primaryLock != null;
   }

   @Override
   public void interrupt() {
      interrupted = true;
   }

   @Override
   public final void releaseBackup() throws NodeManagerException {
      try {
         if (backupLock != null) {
            backupLock.release();
            backupLock = null;
         }
      } catch (IOException e) {
         throw new NodeManagerException(e);
      }
   }

   @Override
   public void awaitPrimaryNode() throws NodeManagerException, InterruptedException {
      try {
         logger.debug("awaiting primary node to fail...");
         do {
            byte state = getState();
            while (state == NOT_STARTED || state == FIRST_TIME_START) {
               logger.debug("awaiting primary node startup state = '{}'", (char) state);

               Thread.sleep(2000);
               state = getState();
            }

            primaryLock = lock(PRIMARY_LOCK_POS);
            if (interrupted) {
               interrupted = false;
               throw new InterruptedException("Lock was interrupted");
            }
            state = getState();
            if (state == PAUSED) {
               primaryLock.release();
               logger.debug("awaiting primary node restarting");
               Thread.sleep(2000);
            } else if (state == FAILINGBACK) {
               primaryLock.release();
               logger.debug("awaiting primary node failing back");
               Thread.sleep(2000);
            } else if (state == ACTIVE) {
               // if the backup acquires the file lock and the state is ACTIVE that means the primary died
               logger.debug("acquired primary node lock state = {}", (char) state);
               serverLockFile.setLastModified(System.currentTimeMillis());
               logger.debug("touched {}; new time: {}", serverLockFile.getAbsoluteFile(), serverLockFile.lastModified());
               break;
            }
         }
         while (true);
      } catch (IOException | ActiveMQLockAcquisitionTimeoutException e) {
         throw new NodeManagerException(e);
      }
   }

   @Override
   public void startBackup() throws NodeManagerException {
      assert !replicatedBackup; // should not be called if this is a replicating backup
      ActiveMQServerLogger.LOGGER.waitingToBecomeBackup();
      try {
         backupLock = lock(FileLockNodeManager.BACKUP_LOCK_POS);
      } catch (ClosedChannelException | ActiveMQLockAcquisitionTimeoutException e) {
         throw new NodeManagerException(e);
      }
      ActiveMQServerLogger.LOGGER.gotBackupLock();
      if (getUUID() == null)
         readNodeId();
   }

   @Override
   public ActivateCallback startPrimaryNode() throws NodeManagerException {
      try {
         setFailingBack();

         String timeoutMessage = lockAcquisitionTimeoutNanos == -1 ? "indefinitely" : TimeUnit.NANOSECONDS.toMillis(lockAcquisitionTimeoutNanos) + " milliseconds";

         ActiveMQServerLogger.LOGGER.waitingToObtainPrimaryLock(timeoutMessage);

         primaryLock = lock(FileLockNodeManager.PRIMARY_LOCK_POS);

         ActiveMQServerLogger.LOGGER.obtainedPrimaryLock();

         return new CleaningActivateCallback() {
            @Override
            public void activationComplete() {
               try {
                  setActive();
                  startLockMonitoring();
               } catch (Exception e) {
                  logger.warn(e.getMessage(), e);
                  // that allows to restart/stop the broker if needed
                  throw e;
               }
            }
         };
      } catch (ClosedChannelException | ActiveMQLockAcquisitionTimeoutException e) {
         throw new NodeManagerException(e);
      }
   }

   @Override
   public void pausePrimaryServer() throws NodeManagerException {
      stopLockMonitoring();
      setPaused();
      try {
         if (primaryLock != null) {
            primaryLock.release();
         }
      } catch (IOException e) {
         throw new NodeManagerException(e);
      }
   }

   @Override
   public void crashPrimaryServer() throws NodeManagerException {
      stopLockMonitoring();
      if (primaryLock != null) {
         try {
            primaryLock.release();
         } catch (IOException e) {
            throw new NodeManagerException(e);
         } finally {
            primaryLock = null;
         }
      }
   }

   @Override
   public void awaitActiveStatus() throws NodeManagerException, InterruptedException {
      while (getState() != ACTIVE) {
         Thread.sleep(2000);
      }
   }

   private void setActive() throws NodeManagerException {
      writeFileLockStatus(ACTIVE);
   }

   private void setFailingBack() throws NodeManagerException {
      writeFileLockStatus(FAILINGBACK);
   }

   private void setPaused() throws NodeManagerException {
      writeFileLockStatus(PAUSED);
   }

   private void writeFileLockStatus(byte status) throws NodeManagerException {
      if (replicatedBackup && channel == null) {
         return;
      }

      logger.debug("writing status: {}", (char) status);
      ByteBuffer bb = ByteBuffer.allocateDirect(1);
      bb.put(status);
      bb.position(0);
      try {
         if (channel == null || !channel.isOpen()) {
            setUpServerLockFile();
         }
         FileLock lock = null;
         try {
            lock = lock(STATE_LOCK_POS);
            channel.write(bb, 0);
            channel.force(true);
         } finally {
            if (lock != null) {
               lock.release();
            }
         }
         serverLockLastModified = serverLockFile.lastModified();
         logger.debug("Modified {} at {}", serverLockFile.getName(), serverLockLastModified);
      } catch (IOException | ActiveMQLockAcquisitionTimeoutException e) {
         throw new NodeManagerException(e);
      }
   }

   private byte getState() throws NodeManagerException {
      try {
         byte result;
         logger.debug("getting state...");
         ByteBuffer bb = ByteBuffer.allocateDirect(1);
         int read;
         FileLock lock = null;
         try {
            lock = lock(STATE_LOCK_POS);
            read = channel.read(bb, 0);
            if (read <= 0) {
               result = FileLockNodeManager.NOT_STARTED;
            } else {
               result = bb.get(0);
            }
         } finally {
            if (lock != null && lock.isValid()) {
               lock.release();
            }
         }

         logger.debug("state: {}", (char) result);
         return result;
      } catch (IOException | ActiveMQLockAcquisitionTimeoutException e) {
         throw new NodeManagerException(e);
      }
   }

   @Override
   public final SimpleString readNodeId() throws NodeManagerException {
      try {
         ByteBuffer id = ByteBuffer.allocateDirect(16);
         int read = channel.read(id, 3);
         if (read != 16) {
            throw new IOException("primary server did not write id to file");
         }
         byte[] bytes = new byte[16];
         id.position(0);
         id.get(bytes);
         setUUID(new UUID(UUID.TYPE_TIME_BASED, bytes));
         return getNodeId();
      } catch (IOException e) {
         throw new NodeManagerException(e);
      }
   }

   protected FileLock tryLock(final int lockPos) throws IOException {
      try {
         logger.debug("trying to lock position: {}", lockPos);

         FileLock lock = lockChannels[lockPos].tryLock();
         if (lock != null) {
            logger.debug("locked position: {}", lockPos);
         } else {
            logger.debug("failed to lock position: {}", lockPos);
         }

         return lock;
      } catch (java.nio.channels.OverlappingFileLockException ex) {
         // This just means that another object on the same JVM is holding the lock
         return null;
      }
   }

   protected FileLock lock(final int lockPosition) throws ClosedChannelException, ActiveMQLockAcquisitionTimeoutException {
      long start = System.nanoTime();
      boolean isRecurringFailure = false;

      while (!interrupted) {
         try {
            FileLock lock = tryLock(lockPosition);
            isRecurringFailure = false;

            logger.debug("lock: {}", lock);

            // even if the lock is valid it may have taken too long to acquire
            if (this.lockAcquisitionTimeoutNanos != -1 && (System.nanoTime() - start) > this.lockAcquisitionTimeoutNanos) {
               throw new ActiveMQLockAcquisitionTimeoutException("Timed out waiting for lock. Waited for " + TimeUnit.NANOSECONDS.toSeconds(lockAcquisitionTimeoutNanos));
            }

            if (lock == null) {
               try {
                  Thread.sleep(500);
               } catch (InterruptedException e) {
                  return null;
               }
            } else {
               return lock;
            }
         } catch (ClosedChannelException e) {
            // This is an unrecoverable error as there's no guarantee the channel will ever be opened again.
            throw e;
         } catch (IOException e) {
            // IOException during trylock() may be a temporary issue, e.g. NFS volume not being accessible

            if (isRecurringFailure) {
               logger.debug("Failure when accessing a lock file", e);
            } else {
               logger.warn("Failure when accessing a lock file", e);
            }
            isRecurringFailure = true;

            long waitTime = LOCK_ACCESS_FAILURE_WAIT_TIME_NANOS;
            if (this.lockAcquisitionTimeoutNanos != -1) {
               final long remainingTime = this.lockAcquisitionTimeoutNanos - (System.nanoTime() - start);
               if (remainingTime <= 0) {
                  throw new ActiveMQLockAcquisitionTimeoutException("Timed out waiting for lock. Waited for " + TimeUnit.NANOSECONDS.toSeconds(lockAcquisitionTimeoutNanos));
               }
               waitTime = Math.min(waitTime, remainingTime);
            }

            try {
               TimeUnit.NANOSECONDS.sleep(waitTime);
            } catch (InterruptedException interrupt) {
               return null;
            }
         }
      }

      // presumed interrupted
      return null;
   }

   private synchronized void startLockMonitoring() {
      logger.debug("Starting the lock monitor");
      if (monitorLock == null) {
         monitorLock = new MonitorLock(scheduledPool, LOCK_MONITOR_TIMEOUT_NANOS, LOCK_MONITOR_TIMEOUT_NANOS, TimeUnit.NANOSECONDS, false);
         monitorLock.start();
      } else {
         logger.debug("Lock monitor was already started");
      }
   }

   private synchronized void stopLockMonitoring() {
      logger.debug("Stopping the lock monitor");
      if (monitorLock != null) {
         monitorLock.stop();
         monitorLock = null;
      } else {
         logger.debug("The lock monitor was already stopped");
      }
   }

   @Override
   protected synchronized void notifyLostLock() {
      if (primaryLock != null) {
         super.notifyLostLock();
      }
   }

   // This has been introduced to help ByteMan test testLockMonitorInvalid on JDK 11: sun.nio.ch.FileLockImpl::isValid
   // can affecting setPrimary, causing an infinite loop due to java.nio.channels.OverlappingFileLockException on tryLock
   private boolean isPrimaryLockLost() {
      final FileLock lock = this.primaryLock;
      return (lock != null && !lock.isValid()) || lock == null;
   }

   private MonitorLock monitorLock;

   public class MonitorLock extends ActiveMQScheduledComponent {
      public MonitorLock(ScheduledExecutorService scheduledExecutorService,
                            long initialDelay,
                            long checkPeriod,
                            TimeUnit timeUnit,
                            boolean onDemand) {
         super(scheduledExecutorService, initialDelay, checkPeriod, timeUnit, onDemand);
      }


      @Override
      public void run() {

         boolean lostLock = true;
         try {
            if (primaryLock == null) {
               logger.debug("primarylock is null");
            }
            lostLock = isPrimaryLockLost();
            if (!lostLock) {
               /*
                * Java always thinks the lock is still valid even when there is no filesystem
                * so we perform additional checks...
                */

               /*
                * We should be able to retrieve the status unless something is wrong. When EFS is
                * gone, this locks. Which can be solved but is a lot of threading work where we
                * need to manage the timeout ourselves and interrupt the thread used to claim the
                * lock.
                */
               logger.debug("Lock appears to be valid; double check by reading status");
               byte state = getState();

               logger.debug("Lock appears to be valid; triple check by comparing timestamp");
               if (hasBeenModified(state)) {
                  lostLock = true;
               }
            }
         } catch (Exception exception) {
            // If something went wrong we probably lost the lock
            logger.error(exception.getMessage(), exception);
            lostLock = true;
         }

         if (lostLock) {
            logger.warn("Lost the lock according to the monitor, notifying listeners");
            notifyLostLock();
         }
      }

      private boolean hasBeenModified(byte state) {
         boolean modified = false;

         // Create a new instance of the File object so we can get the most up-to-date information on the file.
         File freshServerLockFile = new File(serverLockFile.getAbsolutePath());

         if (freshServerLockFile.exists()) {
            // the other broker competing for the lock may modify the state as FAILINGBACK when it starts so ensure the state is ACTIVE before returning true
            if (freshServerLockFile.lastModified() > serverLockLastModified && state == ACTIVE) {
               logger.debug("Lock file {} originally locked at {} was modified at {}", serverLockFile.getAbsolutePath(), new Date(serverLockLastModified), new Date(freshServerLockFile.lastModified()));
               modified = true;
            }
         } else {
            logger.debug("Lock file {} does not exist", serverLockFile.getAbsolutePath());
            modified = true;
         }

         return modified;
      }
   }

   protected Channel getChannel() {
      return channel;
   }
}
