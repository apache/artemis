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

package org.apache.activemq.artemis.core.persistence.impl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.io.IOCriticalErrorListener;
import org.apache.activemq.artemis.core.io.OperationConsistencyLevel;
import org.apache.activemq.artemis.core.persistence.OperationContext;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.persistence.config.AbstractPersistedAddressSetting;
import org.apache.activemq.artemis.core.persistence.config.PersistedBridgeConfiguration;
import org.apache.activemq.artemis.core.persistence.config.PersistedConnector;
import org.apache.activemq.artemis.core.persistence.config.PersistedDivertConfiguration;
import org.apache.activemq.artemis.core.persistence.config.PersistedRole;
import org.apache.activemq.artemis.core.persistence.config.PersistedSecuritySetting;
import org.apache.activemq.artemis.core.persistence.config.PersistedUser;
import org.apache.activemq.artemis.core.persistence.impl.journal.OperationContextImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.utils.ArtemisCloseable;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.apache.activemq.artemis.utils.critical.CriticalAnalyzer;
import org.apache.activemq.artemis.utils.critical.CriticalCloseable;
import org.apache.activemq.artemis.utils.critical.CriticalComponentImpl;
import org.apache.activemq.artemis.utils.critical.CriticalMeasure;

public abstract class AbstractStorageManager extends CriticalComponentImpl implements StorageManager {

   protected static final int CRITICAL_PATHS = 3;
   protected static final int CRITICAL_STORE = 0;
   protected static final int CRITICAL_STOP = 1;
   protected static final int CRITICAL_STOP_2 = 2;

   protected final ExecutorFactory ioExecutorFactory;

   protected final ScheduledExecutorService scheduledExecutorService;

   protected final ExecutorFactory executorFactory;

   protected final Executor executor;

   protected final ReentrantReadWriteLock storageManagerLock = new ReentrantReadWriteLock(false);

   private static final ThreadLocal<Boolean> reentrant = ThreadLocal.withInitial(() -> false);

   protected final IOCriticalErrorListener ioCriticalErrorListener;

   protected volatile boolean started;

   // Persisted core configuration
   protected final Map<String, PersistedSecuritySetting> mapPersistedSecuritySettings = new ConcurrentHashMap<>();

   protected final Map<String, AbstractPersistedAddressSetting> mapPersistedAddressSettings = new ConcurrentHashMap<>();

   protected final Map<String, PersistedDivertConfiguration> mapPersistedDivertConfigurations = new ConcurrentHashMap<>();

   protected final Map<String, PersistedBridgeConfiguration> mapPersistedBridgeConfigurations = new ConcurrentHashMap<>();

   protected final Map<String, PersistedConnector> mapPersistedConnectors = new ConcurrentHashMap<>();

   protected final Map<String, PersistedUser> mapPersistedUsers = new ConcurrentHashMap<>();

   protected final Map<String, PersistedRole> mapPersistedRoles = new ConcurrentHashMap<>();

   @Override
   public void clearContext() {
      OperationContextImpl.clearContext();
   }

   @Override
   public OperationContext getContext() {
      return OperationContextImpl.getContext(executorFactory);
   }

   @Override
   public void setContext(final OperationContext context) {
      OperationContextImpl.setContext(context);
   }

   @Override
   public OperationContext newSingleThreadContext() {
      return newContext(executor);
   }

   @Override
   public OperationContext newContext(final Executor executor1) {
      return new OperationContextImpl(executor1);
   }

   @Override
   public void afterCompleteOperations(final IOCallback run) {
      getContext().executeOnCompletion(run);
   }

   @Override
   public void afterCompleteOperations(final IOCallback run, OperationConsistencyLevel consistencyLevel) {
      getContext().executeOnCompletion(run, consistencyLevel);
   }

   @Override
   public void afterStoreOperations(IOCallback run) {
      getContext().executeOnCompletion(run, OperationConsistencyLevel.STORAGE);
   }

   public AbstractStorageManager(CriticalAnalyzer analyzer, int numberOfPaths, ExecutorFactory executorFactory, ScheduledExecutorService scheduledExecutorService, ExecutorFactory ioExecutorFactory) {
      this(analyzer, numberOfPaths, executorFactory, scheduledExecutorService, ioExecutorFactory, null);
   }

   public AbstractStorageManager(CriticalAnalyzer analyzer, int numberOfPaths, ExecutorFactory executorFactory, ScheduledExecutorService scheduledExecutorService, ExecutorFactory ioExecutorFactory, IOCriticalErrorListener ioCriticalErrorListener) {
      super(analyzer, numberOfPaths);
      this.ioExecutorFactory = ioExecutorFactory;
      this.scheduledExecutorService = scheduledExecutorService;
      this.executorFactory = executorFactory;
      this.ioCriticalErrorListener = ioCriticalErrorListener;

      executor = executorFactory.getExecutor();
   }

   @Override
   public void criticalError(Throwable error) {
      if (ioCriticalErrorListener != null) {
         ioCriticalErrorListener.onIOException(error, error.getMessage(), null);
      }
   }

   @Override
   public synchronized boolean isStarted() {
      if (ioCriticalErrorListener != null) {
         return started && !ioCriticalErrorListener.isPreviouslyFailed();
      } else {
         return started;
      }
   }

   @Override
   public synchronized void start() throws Exception {
      if (started) {
         return;
      }
      started = true;
   }

   @Override
   public void stop() throws Exception {
      stop(false, true);
   }

   @Override
   public synchronized void stop(boolean ioCriticalError, boolean sendFailover) throws Exception {
      if (!started) {
         return;
      }
      started = false;
   }

   @Override
   public final void waitOnOperations() throws Exception {
      if (!started) {
         return;
      }
      waitOnOperations(0);
   }

   @Override
   public final boolean waitOnOperations(final long timeout) throws Exception {
      if (!started) {
         ActiveMQServerLogger.LOGGER.serverIsStopped();
         throw new IllegalStateException("Server is stopped");
      }
      return getContext().waitCompletion(timeout);
   }

   private void unlockCloseable() {
      storageManagerLock.readLock().unlock();
      reentrant.set(false);
   }

   @Override
   public void writeLock() {
      storageManagerLock.writeLock().lock();
   }

   @Override
   public void writeUnlock() {
      storageManagerLock.writeLock().unlock();
   }

   @Override
   public ArtemisCloseable closeableReadLock(boolean tryLock) {
      if (reentrant.get()) {
         return dummyCloseable;
      }

      CriticalCloseable measure = measureCritical(CRITICAL_STORE);

      if (tryLock) {
         if (!storageManagerLock.readLock().tryLock()) {
            return null;
         }
      } else {
         storageManagerLock.readLock().lock();
      }

      reentrant.set(true);

      if (CriticalMeasure.isDummy(measure)) {
         // The next statement could have been called like this:
         // return storageManagerLock.readLock()::unlock;
         // However I wasn't 100% sure the JDK would take good care
         // of caching for me.
         // Since this is important to me here, I decided to play safe and
         // cache it myself
         return unlockCloseable;
      } else {
         // Same applies to the next statement here
         // measure.beforeClose(storageManagerLock.readLock()::unlock);
         // I'm just playing safe and caching it myself
         measure.beforeClose(unlockCloseable);
         return measure;
      }
   }


   // I would rather cache the Closeable instance here..
   // I never know when the JRE decides to create a new instance on every call.
   // So I'm playing safe here. That's all
   protected final ArtemisCloseable unlockCloseable = this::unlockCloseable;
   protected static final ArtemisCloseable dummyCloseable = () -> { };


}
