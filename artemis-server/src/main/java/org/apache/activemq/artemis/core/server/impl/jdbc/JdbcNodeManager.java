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
package org.apache.activemq.artemis.core.server.impl.jdbc;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.storage.DatabaseStorageConfiguration;
import org.apache.activemq.artemis.core.server.ActivateCallback;
import org.apache.activemq.artemis.core.server.ActiveMQLockAcquisitionTimeoutException;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.NodeManager;
import org.apache.activemq.artemis.core.server.impl.CleaningActivateCallback;
import org.apache.activemq.artemis.jdbc.store.drivers.JDBCConnectionProvider;
import org.apache.activemq.artemis.jdbc.store.sql.PropertySQLProvider;
import org.apache.activemq.artemis.jdbc.store.sql.SQLProvider;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.apache.activemq.artemis.utils.UUID;
import org.apache.activemq.artemis.utils.UUIDGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

import static org.apache.activemq.artemis.core.server.impl.jdbc.LeaseLock.AcquireResult.Timeout;

/**
 * JDBC implementation of {@link NodeManager}.
 */
public final class JdbcNodeManager extends NodeManager {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
   private static final long MAX_PAUSE_MILLIS = 2000L;

   private final Supplier<? extends SharedStateManager> sharedStateManagerFactory;
   private final Supplier<? extends ScheduledLeaseLock> scheduledPrimaryLockFactory;
   private final Supplier<? extends ScheduledLeaseLock> scheduledBackupLockFactory;
   private SharedStateManager sharedStateManager;
   private ScheduledLeaseLock scheduledPrimaryLock;
   private ScheduledLeaseLock scheduledBackupLock;
   private final long lockAcquisitionTimeoutMillis;
   private volatile boolean interrupted = false;
   private final LeaseLock.Pauser pauser;

   public static JdbcNodeManager with(DatabaseStorageConfiguration configuration,
                                      ScheduledExecutorService scheduledExecutorService,
                                      ExecutorFactory executorFactory) {
      validateTimeoutConfiguration(configuration);
      final SQLProvider.Factory sqlProviderFactory;
      if (configuration.getSqlProviderFactory() != null) {
         sqlProviderFactory = configuration.getSqlProviderFactory();
      } else {
         sqlProviderFactory = new PropertySQLProvider.Factory(configuration.getConnectionProvider());
      }
      final String brokerId = java.util.UUID.randomUUID().toString();
      return usingConnectionProvider(brokerId,
                             configuration.getJdbcLockExpirationMillis(),
                             configuration.getJdbcLockRenewPeriodMillis(),
                             configuration.getJdbcLockAcquisitionTimeoutMillis(),
                             configuration.getJdbcAllowedTimeDiff(),
                             configuration.getConnectionProvider(),
                             sqlProviderFactory.create(configuration.getNodeManagerStoreTableName(), SQLProvider.DatabaseStoreType.NODE_MANAGER),
                             scheduledExecutorService,
                             executorFactory);
   }

   private static JdbcNodeManager usingConnectionProvider(String brokerId,
                                                  long lockExpirationMillis,
                                                  long lockRenewPeriodMillis,
                                                  long lockAcquisitionTimeoutMillis,
                                                  long allowedTimeDiff,
                                                  JDBCConnectionProvider connectionProvider,
                                                  SQLProvider provider,
                                                  ScheduledExecutorService scheduledExecutorService,
                                                  ExecutorFactory executorFactory) {
      return new JdbcNodeManager(() -> JdbcSharedStateManager.usingConnectionProvider(brokerId, lockExpirationMillis,
                                                                                      lockRenewPeriodMillis,
                                                                                      allowedTimeDiff,
                                                                                      connectionProvider,
                                                                                      provider),
         lockExpirationMillis,
         lockRenewPeriodMillis,
         lockAcquisitionTimeoutMillis,
         scheduledExecutorService,
         executorFactory);
   }

   private static void validateTimeoutConfiguration(DatabaseStorageConfiguration configuration) {
      final long lockExpiration = configuration.getJdbcLockExpirationMillis();
      if (lockExpiration <= 0) {
         throw new IllegalArgumentException("jdbc-lock-expiration should be positive");
      }
      final long lockRenewPeriod = configuration.getJdbcLockRenewPeriodMillis();
      if (lockRenewPeriod <= 0) {
         throw new IllegalArgumentException("jdbc-lock-renew-period should be positive");
      }
      if (lockRenewPeriod >= lockExpiration) {
         throw new IllegalArgumentException("jdbc-lock-renew-period should be < jdbc-lock-expiration");
      }
      final int networkTimeout = configuration.getJdbcNetworkTimeout();
      if (networkTimeout >= 0) {
         if (networkTimeout > lockExpiration) {
            logger.warn("jdbc-network-timeout isn't properly configured: the recommended value is <= jdbc-lock-expiration");
         }
      } else {
         logger.warn("jdbc-network-timeout isn't properly configured: the recommended value is <= jdbc-lock-expiration");
      }
   }

   private JdbcNodeManager(Supplier<? extends SharedStateManager> sharedStateManagerFactory,
                           long lockExpirationMillis,
                           long lockRenewPeriodMillis,
                           long lockAcquisitionTimeoutMillis,
                           ScheduledExecutorService scheduledExecutorService,
                           ExecutorFactory executorFactory) {
      super(false);
      this.lockAcquisitionTimeoutMillis = lockAcquisitionTimeoutMillis;
      this.pauser = LeaseLock.Pauser.sleep(Math.min(lockRenewPeriodMillis, MAX_PAUSE_MILLIS), TimeUnit.MILLISECONDS);
      this.sharedStateManagerFactory = sharedStateManagerFactory;
      this.scheduledPrimaryLockFactory = () -> ScheduledLeaseLock.of(
         scheduledExecutorService,
         executorFactory != null ? executorFactory.getExecutor() : null,
         "primary",
         this.sharedStateManager.primaryLock(),
         lockRenewPeriodMillis,
         this::notifyLostLock);
      this.scheduledBackupLockFactory = () -> ScheduledLeaseLock.of(
         scheduledExecutorService,
         executorFactory != null ?
            executorFactory.getExecutor() : null,
         "backup",
         this.sharedStateManager.backupLock(),
         lockRenewPeriodMillis,
         this::notifyLostLock);
      this.sharedStateManager = null;
      this.scheduledPrimaryLock = null;
      this.scheduledBackupLock = null;
   }

   @Override
   protected synchronized void notifyLostLock() {
      try {
         super.notifyLostLock();
      } finally {
         // if any of the notified listener has stopped the node manager or
         // the node manager was already stopped
         if (!isStarted()) {
            return;
         }
         try {
            stop();
         } catch (Exception ex) {
            logger.warn("Stopping node manager has errored on lost lock notification", ex);
         }
      }
   }

   @Override
   public synchronized void start() throws Exception {
      try {
         if (isStarted()) {
            return;
         }
         this.sharedStateManager = sharedStateManagerFactory.get();
         logger.debug("setup sharedStateManager on start");
         final UUID nodeId = sharedStateManager.setup(UUIDGenerator.getInstance()::generateUUID);
         setUUID(nodeId);
         this.scheduledPrimaryLock = scheduledPrimaryLockFactory.get();
         this.scheduledBackupLock = scheduledBackupLockFactory.get();
         super.start();
      } catch (IllegalStateException e) {
         this.sharedStateManager = null;
         this.scheduledPrimaryLock = null;
         this.scheduledBackupLock = null;
         throw e;
      }
   }

   @Override
   public synchronized void stop() throws Exception {
      if (isStarted()) {
         try {
            this.scheduledPrimaryLock.stop();
            this.scheduledBackupLock.stop();
         } finally {
            super.stop();
            this.sharedStateManager.close();
            this.sharedStateManager = null;
            this.scheduledPrimaryLock = null;
            this.scheduledBackupLock = null;
         }
      }
   }

   @Override
   protected void finalize() throws Throwable {
      stop();
   }

   @Override
   public boolean isAwaitingFailback() throws NodeManagerException {
      checkStarted();
      logger.debug("ENTER isAwaitingFailback");
      try {
         return readSharedState() == SharedStateManager.State.FAILING_BACK;
      } catch (IllegalStateException e) {
         logger.warn("cannot retrieve shared state: assume it's not yet failed back", e);
         return false;
      } finally {
         logger.debug("EXIT isAwaitingFailback");
      }
   }

   @Override
   public boolean isBackupActive() throws NodeManagerException {
      checkStarted();
      logger.debug("ENTER isBackupActive");
      try {
         //is anyone holding the primary lock?
         return this.scheduledPrimaryLock.lock().isHeld();
      } catch (IllegalStateException e) {
         throw new NodeManagerException(e);
      } finally {
         logger.debug("EXIT isBackupActive");
      }
   }

   @Override
   public void interrupt() {
      logger.debug("ENTER interrupted");
      //need to be volatile: must be called concurrently to work as expected
      interrupted = true;
      logger.debug("EXIT interrupted");
   }

   @Override
   public void releaseBackup() throws NodeManagerException {
      checkStarted();
      logger.debug("ENTER releaseBackup");
      try {
         if (this.scheduledBackupLock.isStarted()) {
            logger.debug("scheduledBackupLock is running: stop it and release backup lock");
            this.scheduledBackupLock.stop();
            this.scheduledBackupLock.lock().release();
         } else {
            logger.debug("scheduledBackupLock is not running");
         }
      } catch (IllegalStateException e) {
         throw new NodeManagerException(e);
      } finally {
         logger.debug("EXIT releaseBackup");
      }
   }

   /**
    * Try to acquire a lock
    */
   private void lock(LeaseLock lock) throws ActiveMQLockAcquisitionTimeoutException, InterruptedException {
      final long lockAcquisitionTimeoutNanos = lockAcquisitionTimeoutMillis >= 0 ?
         TimeUnit.MILLISECONDS.toNanos(lockAcquisitionTimeoutMillis) : -1;
      LeaseLock.AcquireResult acquireResult = null;
      final long start = System.nanoTime();
      while (acquireResult == null) {
         checkStarted();
         // measure distance from the timeout
         final long remainingNanos = remainingNanos(start, lockAcquisitionTimeoutNanos);
         if (remainingNanos == 0) {
            acquireResult = Timeout;
            continue;
         }
         final long remainingMillis = remainingNanos > 0 ? TimeUnit.NANOSECONDS.toMillis(remainingNanos) : -1;
         try {
            acquireResult = lock.tryAcquire(remainingMillis, this.pauser, () -> !this.interrupted);
         } catch (IllegalStateException e) {
            logger.warn("Errored while trying to acquire lock", e);
            if (remainingNanos(start, lockAcquisitionTimeoutNanos) == 0) {
               acquireResult = Timeout;
               continue;
            }
            // that's not precise, but it's ok: it can trigger the timeout right after the pause,
            // depending by the pause length. The sole purpose of the pause is to save
            // hammering with requests the DBMS if the connection is down
            this.pauser.idle();
         }
      }
      switch (acquireResult) {
         case Timeout:
            throw new ActiveMQLockAcquisitionTimeoutException("timed out waiting for lock");
         case Exit:
            this.interrupted = false;
            throw new InterruptedException("LeaseLock was interrupted");
         case Done:
            break;
         default:
            throw new AssertionError(acquireResult + " not managed");
      }

   }

   private static long remainingNanos(long start, long timeoutNanos) {
      if (timeoutNanos > 0) {
         final long elapsedNanos = (System.nanoTime() - start);
         if (elapsedNanos < timeoutNanos) {
            return timeoutNanos - elapsedNanos;
         } else {
            return 0;
         }
      } else {
         assert timeoutNanos == -1;
         return -1;
      }
   }

   private void checkInterrupted(Supplier<String> message) throws InterruptedException {
      if (this.interrupted) {
         interrupted = false;
         throw new InterruptedException(message.get());
      }
   }

   private void renewLock(ScheduledLeaseLock lock) {
      boolean lostLock = true;
      IllegalStateException renewEx = null;
      try {
         lostLock = !this.scheduledPrimaryLock.lock().renew();
      } catch (IllegalStateException e) {
         renewEx = e;
      }
      if (lostLock) {
         notifyLostLock();
         if (renewEx == null) {
            renewEx = new IllegalStateException(lock.lockName() + " lock isn't renewed");
         }
         throw renewEx;
      }
   }

   /**
    * Lock primary lock and check for active state, taking care to renew it (if needed) or releasing it otherwise
    */
   private boolean lockPrimaryAndCheckActiveState() throws ActiveMQLockAcquisitionTimeoutException, InterruptedException {
      try {
         lock(this.scheduledPrimaryLock.lock());
         //check if the state is active
         while (true) {
            try {
               final SharedStateManager.State stateWhileLocked = readSharedState();
               final long localExpirationTime = this.scheduledPrimaryLock.lock().localExpirationTime();
               if (System.currentTimeMillis() > localExpirationTime) {
                  // the lock can be assumed to be expired,
                  // so the state isn't worthy to be considered
                  return false;
               }
               if (stateWhileLocked == SharedStateManager.State.ACTIVE) {
                  // TODO need some tolerance//renew here?
                  return true;
               } else {
                  // state is not active: can (try to) release the lock
                  this.scheduledPrimaryLock.lock().release();
                  return false;
               }
            } catch (IllegalStateException e) {
               logger.error("error while holding the primary node lock and tried to read the shared state or to release the lock", e);
               checkStarted();
               checkInterrupted(() -> "interrupt on error while checking state");
               pauser.idle();
               final long localExpirationTime = this.scheduledPrimaryLock.lock().localExpirationTime();
               if (System.currentTimeMillis() > localExpirationTime) {
                  return false;
               }
            }
         }
      } catch (InterruptedException e) {
         throw e;
      }
   }

   @Override
   public void awaitPrimaryNode() throws NodeManagerException, InterruptedException {
      checkStarted();
      logger.debug("ENTER awaitPrimaryNode");
      try {
         boolean primaryWhileLocked = false;
         while (!primaryWhileLocked) {
            //check first without holding any lock
            SharedStateManager.State state = null;
            try {
               state = readSharedState();
            } catch (IllegalStateException e) {
               logger.warn("Errored while reading shared state", e);
            }
            if (state == SharedStateManager.State.ACTIVE) {
               //verify if the state is active while holding the primary node lock too
               primaryWhileLocked = lockPrimaryAndCheckActiveState();
            } else {
               logger.debug("state while awaiting primary lock: {}", state);
            }
            if (!primaryWhileLocked) {
               checkStarted();
               checkInterrupted(() -> "awaitPrimaryNode got interrupted!");
               pauser.idle();
            }
         }
         //state is active and primary lock is acquired and valid
         logger.debug("acquired primary lock while state is {}: starting scheduledPrimaryLock", SharedStateManager.State.ACTIVE);
         this.scheduledPrimaryLock.start();
      } catch (InterruptedException e) {
         throw e;
      } catch (ActiveMQLockAcquisitionTimeoutException | IllegalStateException e) {
         throw new NodeManagerException(e);
      } finally {
         logger.debug("EXIT awaitPrimaryNode");
      }
   }

   @Override
   public void startBackup() throws NodeManagerException, InterruptedException {
      checkStarted();
      logger.debug("ENTER startBackup");
      try {
         ActiveMQServerLogger.LOGGER.waitingToBecomeBackup();
         lock(scheduledBackupLock.lock());
         scheduledBackupLock.start();
         ActiveMQServerLogger.LOGGER.gotBackupLock();
         if (getUUID() == null) {
            readNodeId();
         }
      } catch (InterruptedException ie) {
         throw ie;
      } catch (ActiveMQLockAcquisitionTimeoutException | IllegalStateException e) {
         throw new NodeManagerException(e);
      } finally {
         logger.debug("EXIT startBackup");
      }
   }

   @Override
   public ActivateCallback startPrimaryNode() throws NodeManagerException, InterruptedException {
      checkStarted();
      logger.debug("ENTER startPrimaryNode");
      try {
         boolean done = false;
         while (!done) {
            try {
               setFailingBack();
               done = true;
            } catch (IllegalStateException e) {
               logger.warn("cannot set failing back state, retry", e);
               pauser.idle();
               checkInterrupted(() -> "interrupt while trying to set failing back state");
            }
         }

         final String timeoutMessage = lockAcquisitionTimeoutMillis == -1 ? "indefinitely" : lockAcquisitionTimeoutMillis + " milliseconds";

         ActiveMQServerLogger.LOGGER.waitingToObtainPrimaryLock(timeoutMessage);

         lock(this.scheduledPrimaryLock.lock());

         this.scheduledPrimaryLock.start();

         ActiveMQServerLogger.LOGGER.obtainedPrimaryLock();

         return new CleaningActivateCallback() {
            @Override
            public void activationComplete() {
               logger.debug("ENTER activationComplete");
               try {
                  //state can be written only if the renew task is running
                  boolean done = false;
                  while (!done) {
                     try {
                        setActive();
                        done = true;
                     } catch (IllegalStateException e) {
                        logger.warn("Errored while trying to setActive", e);
                        checkStarted();
                        pauser.idle();
                        final long localExpirationTime = scheduledPrimaryLock.lock().localExpirationTime();
                        // optimistic: is just to set a deadline while retrying
                        if (System.currentTimeMillis() > localExpirationTime) {
                           throw new IllegalStateException("primary lock is probably expired: failed to setActive");
                        }
                     }
                  }
               } catch (IllegalStateException e) {
                  logger.warn(e.getMessage(), e);
                  throw new NodeManagerException(e);
               } finally {
                  logger.debug("EXIT activationComplete");
               }
            }
         };
      } catch (InterruptedException ie) {
         throw ie;
      } catch (ActiveMQLockAcquisitionTimeoutException | IllegalStateException e) {
         throw new NodeManagerException(e);
      } finally {
         logger.debug("EXIT startPrimaryNode");
      }
   }

   @Override
   public void pausePrimaryServer() throws NodeManagerException {
      checkStarted();
      logger.debug("ENTER pausePrimaryServer");
      try {
         if (scheduledPrimaryLock.isStarted()) {
            logger.debug("scheduledPrimaryLock is running: set paused shared state, stop it and release primary lock");
            setPaused();
            scheduledPrimaryLock.stop();
            scheduledPrimaryLock.lock().release();
         } else {
            logger.debug("scheduledPrimaryLock is not running: try renew primary lock");
            renewLock(scheduledPrimaryLock);
            logger.debug("primary lock renewed: set paused shared state and release primary lock");
            setPaused();
            scheduledPrimaryLock.lock().release();
         }
      } catch (IllegalStateException e) {
         throw new NodeManagerException(e);
      } finally {
         logger.debug("EXIT pausePrimaryServer");
      }
   }

   @Override
   public void crashPrimaryServer() throws NodeManagerException {
      checkStarted();
      logger.debug("ENTER crashPrimaryServer");
      try {
         if (this.scheduledPrimaryLock.isStarted()) {
            logger.debug("scheduledPrimaryLock is running: request stop it and release primary lock");
            this.scheduledPrimaryLock.stop();
            this.scheduledPrimaryLock.lock().release();
         } else {
            logger.debug("scheduledPrimaryLock is not running");
         }
      } finally {
         logger.debug("EXIT crashPrimaryServer");
      }
   }

   @Override
   public void awaitActiveStatus() {
      checkStarted();
      logger.debug("ENTER awaitActiveStatus");
      try {
         SharedStateManager.State state = null;
         while (state != SharedStateManager.State.ACTIVE) {
            try {
               state = readSharedState();
            } catch (IllegalStateException e) {
               logger.warn("Errored while trying to read shared state", e);
            }
            pauser.idle();
            checkStarted();
         }
      } finally {
         logger.debug("EXIT awaitActiveStatus");
      }
   }

   private void setActive() {
      writeSharedState(SharedStateManager.State.ACTIVE);
   }

   private void setFailingBack() {
      writeSharedState(SharedStateManager.State.FAILING_BACK);
   }

   private void setPaused() {
      writeSharedState(SharedStateManager.State.PAUSED);
   }

   private void writeSharedState(SharedStateManager.State state) {
      logger.debug("writeSharedState state = {}", state);
      this.sharedStateManager.writeState(state);
   }

   private SharedStateManager.State readSharedState() {
      final SharedStateManager.State state = this.sharedStateManager.readState();
      logger.debug("readSharedState state = {}", state);
      return state;
   }

   @Override
   public SimpleString readNodeId() {
      checkStarted();
      final UUID nodeId = this.sharedStateManager.readNodeId();
      logger.debug("readNodeId nodeId = {}", nodeId);
      setUUID(nodeId);
      return getNodeId();
   }

}
