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

import java.lang.invoke.MethodHandles;
import java.lang.ref.WeakReference;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiPredicate;

import org.apache.activemq.artemis.api.config.ServerLocatorConfig;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.ActiveMQInterruptedException;
import org.apache.activemq.artemis.api.core.ActiveMQNotConnectedException;
import org.apache.activemq.artemis.api.core.DisconnectReason;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.FailoverEventListener;
import org.apache.activemq.artemis.api.core.client.FailoverEventType;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.client.SessionFailureListener;
import org.apache.activemq.artemis.core.client.ActiveMQClientLogger;
import org.apache.activemq.artemis.core.client.ActiveMQClientMessageBundle;
import org.apache.activemq.artemis.core.protocol.core.CoreRemotingConnection;
import org.apache.activemq.artemis.core.protocol.core.impl.ActiveMQSessionContext;
import org.apache.activemq.artemis.core.remoting.FailureListener;
import org.apache.activemq.artemis.core.remoting.impl.TransportConfigurationUtil;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnector;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.remoting.BufferHandler;
import org.apache.activemq.artemis.spi.core.remoting.ClientConnectionLifeCycleListener;
import org.apache.activemq.artemis.spi.core.remoting.ClientProtocolManager;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.apache.activemq.artemis.spi.core.remoting.Connector;
import org.apache.activemq.artemis.spi.core.remoting.ConnectorFactory;
import org.apache.activemq.artemis.spi.core.remoting.SessionContext;
import org.apache.activemq.artemis.spi.core.remoting.TopologyResponseHandler;
import org.apache.activemq.artemis.utils.ClassloadingUtil;
import org.apache.activemq.artemis.utils.ConfirmationWindowWarning;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.apache.activemq.artemis.utils.PasswordMaskingUtil;
import org.apache.activemq.artemis.utils.UUIDGenerator;
import org.apache.activemq.artemis.utils.actors.ArtemisExecutor;
import org.apache.activemq.artemis.utils.actors.OrderedExecutorFactory;
import org.apache.activemq.artemis.utils.collections.ConcurrentHashSet;
import org.apache.activemq.artemis.utils.sm.SecurityManagerShim;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientSessionFactoryImpl implements ClientSessionFactoryInternal, ClientConnectionLifeCycleListener {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final ServerLocatorInternal serverLocator;

   private final ClientProtocolManager clientProtocolManager;

   private TransportConfiguration connectorConfig;

   private TransportConfiguration previousConnectorConfig;

   private volatile TransportConfiguration currentConnectorConfig;

   private volatile TransportConfiguration backupConnectorConfig;

   private ConnectorFactory connectorFactory;

   private final long callTimeout;

   private final long callFailoverTimeout;

   private final long clientFailureCheckPeriod;

   private final long connectionTTL;

   private final Set<ClientSessionInternal> sessions = new ConcurrentHashSet<>();

   private final Object createSessionLock = new Object();

   private final Lock newFailoverLock = new ReentrantLock();

   private final Object connectionLock = new Object();

   private final ExecutorFactory orderedExecutorFactory;

   private final Executor threadPool;

   private final ScheduledExecutorService scheduledThreadPool;

   private final ArtemisExecutor closeExecutor;

   private final Executor flowControlExecutor;

   private RemotingConnection connection;

   private final long retryInterval;

   private final double retryIntervalMultiplier; // For exponential backoff

   private volatile boolean topologyReady = false;

   private final CountDownLatch latchFinalTopology = new CountDownLatch(1);

   private final long maxRetryInterval;

   private int reconnectAttempts;

   private int failoverAttempts;

   private final Set<SessionFailureListener> listeners = new ConcurrentHashSet<>();

   private final Set<FailoverEventListener> failoverListeners = new ConcurrentHashSet<>();

   private Connector connector;

   private Future<?> pingerFuture;
   private PingRunnable pingRunnable;

   private final List<Interceptor> incomingInterceptors;

   private final List<Interceptor> outgoingInterceptors;

   private volatile boolean stopPingingAfterOne;

   private volatile boolean closed;

   public static final Set<CloseRunnable> CLOSE_RUNNABLES = Collections.synchronizedSet(new HashSet<>());

   private final ConfirmationWindowWarning confirmationWindowWarning;

   private String primaryNodeID;

   // We need to cache this value here since some listeners may be registered after connectionReadyForWrites was called.
   private boolean connectionReadyForWrites;

   private final TransportConfiguration[] connectorConfigs;

   public ClientSessionFactoryImpl(final ServerLocatorInternal serverLocator,
                                   final TransportConfiguration connectorConfig,
                                   final ServerLocatorConfig locatorConfig,
                                   final int reconnectAttempts,
                                   final Executor threadPool,
                                   final ScheduledExecutorService scheduledThreadPool,
                                   final Executor flowControlThreadPool,
                                   final List<Interceptor> incomingInterceptors,
                                   final List<Interceptor> outgoingInterceptors) {
      this(serverLocator, new Pair<>(connectorConfig, null),
               locatorConfig, reconnectAttempts, threadPool,
               scheduledThreadPool, flowControlThreadPool, incomingInterceptors, outgoingInterceptors);
   }


   ClientSessionFactoryImpl(final ServerLocatorInternal serverLocator,
                            final Pair<TransportConfiguration, TransportConfiguration> connectorConfig,
                            final ServerLocatorConfig locatorConfig,
                            final int reconnectAttempts,
                            final Executor threadPool,
                            final ScheduledExecutorService scheduledThreadPool,
                            final Executor flowControlThreadPool,
                            final List<Interceptor> incomingInterceptors,
                            final List<Interceptor> outgoingInterceptors) {
      this(serverLocator, connectorConfig,
           locatorConfig, reconnectAttempts, threadPool,
           scheduledThreadPool, flowControlThreadPool, incomingInterceptors, outgoingInterceptors, null);
   }

   ClientSessionFactoryImpl(final ServerLocatorInternal serverLocator,
                          final Pair<TransportConfiguration, TransportConfiguration> connectorConfig,
                          final ServerLocatorConfig locatorConfig,
                          final int reconnectAttempts,
                          final Executor threadPool,
                          final ScheduledExecutorService scheduledThreadPool,
                          final Executor flowControlThreadPool,
                          final List<Interceptor> incomingInterceptors,
                          final List<Interceptor> outgoingInterceptors,
                          final TransportConfiguration[] connectorConfigs) {
      this.serverLocator = serverLocator;

      this.clientProtocolManager = serverLocator.newProtocolManager();

      this.clientProtocolManager.setSessionFactory(this);

      this.connectorConfig = connectorConfig.getA();

      this.currentConnectorConfig = connectorConfig.getA();

      connectorFactory = instantiateConnectorFactory(connectorConfig.getA().getFactoryClassName());

      checkTransportKeys(connectorFactory, connectorConfig.getA());

      this.callTimeout = locatorConfig.callTimeout;

      this.callFailoverTimeout = locatorConfig.callFailoverTimeout;

      // If this in an in-vm connection then disable connection monitoring
      if (connectorFactory.isReliable() &&
         locatorConfig.clientFailureCheckPeriod == ActiveMQClient.DEFAULT_CLIENT_FAILURE_CHECK_PERIOD &&
         locatorConfig.connectionTTL == ActiveMQClient.DEFAULT_CONNECTION_TTL) {
         this.clientFailureCheckPeriod = ActiveMQClient.DEFAULT_CLIENT_FAILURE_CHECK_PERIOD_INVM;
         this.connectionTTL = ActiveMQClient.DEFAULT_CONNECTION_TTL_INVM;
      } else {
         this.clientFailureCheckPeriod = locatorConfig.clientFailureCheckPeriod;

         this.connectionTTL = locatorConfig.connectionTTL;
      }

      this.retryInterval = locatorConfig.retryInterval;

      this.retryIntervalMultiplier = locatorConfig.retryIntervalMultiplier;

      this.maxRetryInterval = locatorConfig.maxRetryInterval;

      this.reconnectAttempts = reconnectAttempts;

      this.failoverAttempts = locatorConfig.failoverAttempts;

      this.scheduledThreadPool = scheduledThreadPool;

      this.threadPool = threadPool;

      orderedExecutorFactory = new OrderedExecutorFactory(threadPool);

      flowControlExecutor = new OrderedExecutorFactory(flowControlThreadPool).getExecutor();

      closeExecutor = orderedExecutorFactory.getExecutor();

      this.incomingInterceptors = incomingInterceptors;

      this.outgoingInterceptors = outgoingInterceptors;

      confirmationWindowWarning = new ConfirmationWindowWarning(serverLocator.getConfirmationWindowSize() < 0);

      connectionReadyForWrites = true;

      if (connectorConfig.getB() != null) {
         this.backupConnectorConfig = connectorConfig.getB();
      }

      this.connectorConfigs = connectorConfigs;
   }

   @Override
   public Lock lockFailover() {
      newFailoverLock.lock();
      return newFailoverLock;
   }

   @Override
   public void connect(final int initialConnectAttempts) throws ActiveMQException {
      // Get the connection
      getConnectionWithRetry(initialConnectAttempts, null);

      if (connection == null) {
         StringBuilder msg = new StringBuilder("Unable to connect to server using configuration ").append(currentConnectorConfig);
         if (backupConnectorConfig != null) {
            msg.append(" and backup configuration ").append(backupConnectorConfig);
         }
         throw new ActiveMQNotConnectedException(msg.toString());
      }

   }

   @Deprecated
   @Override
   public void connect(final int initialConnectAttempts,
                       final boolean failoverOnInitialConnection) throws ActiveMQException {
      connect(initialConnectAttempts);
   }

   @Override
   public TransportConfiguration getConnectorConfiguration() {
      return currentConnectorConfig;
   }

   @Override
   public void setBackupConnector(final TransportConfiguration primary, final TransportConfiguration backUp) {
      Connector localConnector = connector;

      // if the connector has never been used (i.e. the getConnection hasn't been called yet), we will need
      // to create a connector just to validate if the parameters are ok.
      // so this will create the instance to be used on the isEquivalent check
      if (localConnector == null) {
         localConnector = connectorFactory.createConnector(currentConnectorConfig.getCombinedParams(), new DelegatingBufferHandler(), this, closeExecutor, threadPool, scheduledThreadPool, clientProtocolManager);
      }

      if (localConnector.isEquivalent(primary.getParams()) && backUp != null && !localConnector.isEquivalent(backUp.getParams())
         // check if a server is trying to set its cluster connector config as backup connector config
         && !(serverLocator.getClusterTransportConfiguration() != null && serverLocator.getClusterTransportConfiguration().isSameParams(backUp))) {
         logger.debug("Setting up backup config = {} for primary = {}", backUp, primary);
         backupConnectorConfig = backUp;
      } else {
         if (logger.isDebugEnabled()) {
            logger.debug("ClientSessionFactoryImpl received backup update for primary/backup pair = {} / {}  but it didn't belong to {}",
                         primary, backUp, currentConnectorConfig);
         }
      }
   }

   @Override
   public Object getBackupConnector() {
      return backupConnectorConfig;
   }

   @Override
   public ClientSession createSession(final String username,
                                      final String password,
                                      final boolean xa,
                                      final boolean autoCommitSends,
                                      final boolean autoCommitAcks,
                                      final boolean preAcknowledge,
                                      final int ackBatchSize,
                                      final String clientID) throws ActiveMQException {
      return createSessionInternal(username, password, xa, autoCommitSends, autoCommitAcks, preAcknowledge, ackBatchSize, clientID);
   }

   @Override
   public ClientSession createSession(final String username,
                                      final String password,
                                      final boolean xa,
                                      final boolean autoCommitSends,
                                      final boolean autoCommitAcks,
                                      final boolean preAcknowledge,
                                      final int ackBatchSize) throws ActiveMQException {
      return createSessionInternal(username, password, xa, autoCommitSends, autoCommitAcks, preAcknowledge, ackBatchSize, null);
   }

   @Override
   public ClientSession createSession(final boolean autoCommitSends,
                                      final boolean autoCommitAcks,
                                      final int ackBatchSize) throws ActiveMQException {
      return createSessionInternal(null, null, false, autoCommitSends, autoCommitAcks, serverLocator.isPreAcknowledge(), ackBatchSize, null);
   }

   @Override
   public ClientSession createXASession() throws ActiveMQException {
      return createSessionInternal(null, null, true, false, false, serverLocator.isPreAcknowledge(), serverLocator.getAckBatchSize(), null);
   }

   @Override
   public ClientSession createTransactedSession() throws ActiveMQException {
      return createSessionInternal(null, null, false, false, false, serverLocator.isPreAcknowledge(), serverLocator.getAckBatchSize(), null);
   }

   @Override
   public ClientSession createSession() throws ActiveMQException {
      return createSessionInternal(null, null, false, true, true, serverLocator.isPreAcknowledge(), serverLocator.getAckBatchSize(), null);
   }

   @Override
   public ClientSession createSession(final boolean autoCommitSends,
                                      final boolean autoCommitAcks) throws ActiveMQException {
      return createSessionInternal(null, null, false, autoCommitSends, autoCommitAcks, serverLocator.isPreAcknowledge(), serverLocator.getAckBatchSize(), null);
   }

   @Override
   public ClientSession createSession(final boolean xa,
                                      final boolean autoCommitSends,
                                      final boolean autoCommitAcks) throws ActiveMQException {
      return createSessionInternal(null, null, xa, autoCommitSends, autoCommitAcks, serverLocator.isPreAcknowledge(), serverLocator.getAckBatchSize(), null);
   }

   @Override
   public ClientSession createSession(final boolean xa,
                                      final boolean autoCommitSends,
                                      final boolean autoCommitAcks,
                                      final boolean preAcknowledge) throws ActiveMQException {
      return createSessionInternal(null, null, xa, autoCommitSends, autoCommitAcks, preAcknowledge, serverLocator.getAckBatchSize(), null);
   }

   // ClientConnectionLifeCycleListener implementation --------------------------------------------------

   @Override
   public void connectionCreated(final ActiveMQComponent component,
                                 final Connection connection,
                                 final ClientProtocolManager protocol) {
   }

   @Override
   public void connectionDestroyed(final Object connectionID, boolean failed) {
      // The exception has to be created in the same thread where it's being called
      // as to avoid a different stack trace cause
      final ActiveMQException ex = ActiveMQClientMessageBundle.BUNDLE.channelDisconnected();

      // It has to use the same executor as the disconnect message is being sent through

      closeExecutor.execute(() -> handleConnectionFailure(connectionID, ex));

   }

   @Override
   public void connectionException(final Object connectionID, final ActiveMQException me) {
      handleConnectionFailure(connectionID, me);
   }

   // Must be synchronized to prevent it happening concurrently with failover which can lead to
   // inconsistencies
   @Override
   public void removeSession(final ClientSessionInternal session, final boolean failingOver) {
      synchronized (sessions) {
         sessions.remove(session);
      }
   }

   @Override
   public void connectionReadyForWrites(final Object connectionID, final boolean ready) {
   }

   @Override
   public synchronized int numConnections() {
      return connection != null ? 1 : 0;
   }

   @Override
   public int numSessions() {
      return sessions.size();
   }

   @Override
   public void addFailureListener(final SessionFailureListener listener) {
      listeners.add(listener);
   }

   @Override
   public boolean removeFailureListener(final SessionFailureListener listener) {
      return listeners.remove(listener);
   }

   @Override
   public ClientSessionFactoryImpl addFailoverListener(FailoverEventListener listener) {
      failoverListeners.add(listener);
      return this;
   }

   @Override
   public boolean removeFailoverListener(FailoverEventListener listener) {
      return failoverListeners.remove(listener);
   }

   @Override
   public void causeExit() {
      clientProtocolManager.stop();
   }

   private void interruptConnectAndCloseAllSessions(boolean close) {
      //release all threads waiting for topology
      latchFinalTopology.countDown();

      clientProtocolManager.stop();

      synchronized (createSessionLock) {
         closeCleanSessions(close);
         closed = true;
      }
   }

   private void closeCleanSessions(boolean close) {
      Set<ClientSessionInternal> sessionsToClose;
      synchronized (sessions) {
         sessionsToClose = new HashSet<>(sessions);
      }
      // work on a copied set. the session will be removed from sessions when session.close() is
      // called
      for (ClientSessionInternal session : sessionsToClose) {
         try {
            if (close) {
               session.close();
            } else {
               session.cleanUp(false);
            }
         } catch (Exception e1) {
            ActiveMQClientLogger.LOGGER.unableToCloseSession(e1);
         }
      }
      checkCloseConnection();
   }

   @Override
   public void close() {
      if (closed) {
         return;
      }
      interruptConnectAndCloseAllSessions(true);

      serverLocator.factoryClosed(this);
   }

   @Override
   public void cleanup() {
      if (closed) {
         return;
      }

      interruptConnectAndCloseAllSessions(false);
   }

   @Override
   public boolean waitForTopology(long timeout, TimeUnit unit) {
      try {
         //latchFinalTopology is decremented on last topology message or on close
         //topologyReady is set to true only on last topology message
         return latchFinalTopology.await(timeout, unit) && topologyReady;
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         if (!isClosed()) {
            ActiveMQClientLogger.LOGGER.unableToReceiveClusterTopology(e);
         }
         return false;
      }
   }

   @Override
   public boolean isClosed() {
      return closed || serverLocator.isClosed();
   }

   @Override
   public ServerLocator getServerLocator() {
      return serverLocator;
   }

   public void stopPingingAfterOne() {
      stopPingingAfterOne = true;
   }

   private void handleConnectionFailure(final Object connectionID, final ActiveMQException me) {
      handleConnectionFailure(connectionID, me, null);
   }

   private void handleConnectionFailure(final Object connectionID,
                                        final ActiveMQException me,
                                        String scaleDownTargetNodeID) {
      try {
         failoverOrReconnect(connectionID, me, scaleDownTargetNodeID);
      } catch (ActiveMQInterruptedException e1) {
         // this is just a debug, since an interrupt is an expected event (in case of a shutdown)
         logger.debug(e1.getMessage(), e1);
      } catch (Throwable t) {
         ActiveMQClientLogger.LOGGER.unableToHandleConnectionFailure(t);
         //for anything else just close so clients are un blocked
         close();
         throw t;
      }
   }

   /**
    * TODO: Maybe this belongs to ActiveMQClientProtocolManager
    */
   private void failoverOrReconnect(final Object connectionID,
                                    final ActiveMQException me,
                                    String scaleDownTargetNodeID) {
      logger.debug("Failure captured on connectionID={}, performing failover or reconnection now", connectionID, me);

      for (ClientSessionInternal session : sessions) {
         SessionContext context = session.getSessionContext();
         if (context instanceof ActiveMQSessionContext sessionContext) {
            if (sessionContext.isKilled()) {
               setReconnectAttempts(0);
            }
         }
      }

      Set<ClientSessionInternal> sessionsToClose = null;
      if (!clientProtocolManager.isAlive()) {
         return;
      }
      Lock localFailoverLock = lockFailover();
      try {
         if (connection == null || !connection.getID().equals(connectionID) || !clientProtocolManager.isAlive()) {
            // We already failed over/reconnected - probably the first failure came in, all the connections were failed
            // over then an async connection exception or disconnect
            // came in for one of the already exitLoop connections, so we return true - we don't want to call the
            // listeners again

            return;
         }

         if (logger.isTraceEnabled()) {
            logger.trace("Client Connection failed, calling failure listeners and trying to reconnect, reconnectAttempts={}", reconnectAttempts);
         }

         callFailoverListeners(FailoverEventType.FAILURE_DETECTED);
         // We call before reconnection occurs to give the user a chance to do cleanup, like cancel messages
         callSessionFailureListeners(me, false, false, scaleDownTargetNodeID);

         // Now get locks on all channel 1s, whilst holding the failoverLock - this makes sure
         // There are either no threads executing in createSession, or one is blocking on a createSession
         // result.

         // Then interrupt the channel 1 that is blocking (could just interrupt them all)

         // Then release all channel 1 locks - this allows the createSession to exit the monitor

         // Then get all channel 1 locks again - this ensures the any createSession thread has executed the section and
         // returned all its connections to the connection manager (the code to return connections to connection manager
         // must be inside the lock

         // Then perform failover

         // Then release failoverLock

         // The other side of the bargain - during createSession:
         // The calling thread must get the failoverLock and get its' connections when this is
         // locked.
         // While this is still locked it must then get the channel1 lock
         // It can then release the failoverLock
         // It should catch ActiveMQException.INTERRUPTED in the call to channel.sendBlocking
         // It should then return its connections, with channel 1 lock still held
         // It can then release the channel 1 lock, and retry (which will cause locking on
         // failoverLock
         // until failover is complete

         if (reconnectAttempts != 0 || failoverAttempts != 0) {

            if (clientProtocolManager.cleanupBeforeFailover(me)) {

               // Now we absolutely know that no threads are executing in or blocked in
               // createSession,
               // and no
               // more will execute it until failover is complete

               // So.. do failover / reconnection

               RemotingConnection oldConnection = connection;

               connection = null;

               Connector localConnector = connector;
               if (localConnector != null) {
                  try {
                     localConnector.close();
                  } catch (Exception ignore) {
                     // no-op
                  }
               }

               cancelScheduledTasks();

               connector = null;

               Set<ClientSessionInternal> sessionsToFailover;
               synchronized (sessions) {
                  sessionsToFailover = new HashSet<>(sessions);
               }

               // Notify sessions before failover.
               for (ClientSessionInternal session : sessionsToFailover) {
                  session.preHandleFailover(connection);
               }


               // Try to reconnect to the current connector pair.
               // Before ARTEMIS-4251 ClientSessionFactoryImpl only tries to reconnect to the current connector pair.
               int reconnectRetries = 0;
               boolean sessionsReconnected = false;
               BiPredicate<Boolean, Integer> reconnectRetryPredicate =
                  (reconnected, retries) -> clientProtocolManager.isAlive() &&
                     !reconnected && (reconnectAttempts == -1 || retries < reconnectAttempts);
               while (reconnectRetryPredicate.test(sessionsReconnected, reconnectRetries)) {

                  int remainingReconnectRetries = reconnectAttempts == -1 ? -1 : reconnectAttempts - reconnectRetries;
                  reconnectRetries += getConnectionWithRetry(remainingReconnectRetries, oldConnection);

                  if (connection != null) {
                     sessionsReconnected = reconnectSessions(sessionsToFailover, oldConnection, me);

                     if (!sessionsReconnected) {
                        if (oldConnection != null) {
                           oldConnection.destroy();
                        }

                        oldConnection = connection;
                        connection = null;
                     }
                  }

                  reconnectRetries++;
                  if (reconnectRetryPredicate.test(sessionsReconnected, reconnectRetries)) {
                     waitForRetry(retryInterval);
                  }
               }


               // Try to connect to other connector pairs.
               // After ARTEMIS-4251 ClientSessionFactoryImpl tries to connect to
               // other connector pairs when reconnection to the current connector pair fails.
               int connectorsCount = 0;
               int failoverRetries = 0;
               long failoverRetryInterval = retryInterval;
               Pair<TransportConfiguration, TransportConfiguration> connectorPair;
               BiPredicate<Boolean, Integer> failoverRetryPredicate =
                  (reconnected, retries) -> clientProtocolManager.isAlive() &&
                     !reconnected && (failoverAttempts == -1 || retries < failoverAttempts);
               while (failoverRetryPredicate.test(sessionsReconnected, failoverRetries)) {

                  connectorsCount++;
                  connectorPair = serverLocator.selectNextConnectorPair();

                  if (connectorPair != null) {
                     connectorConfig = connectorPair.getA();
                     currentConnectorConfig = connectorPair.getA();
                     if (connectorPair.getB() != null) {
                        backupConnectorConfig = connectorPair.getB();
                     }

                     getConnection();
                  }

                  if (connection != null) {
                     sessionsReconnected = reconnectSessions(sessionsToFailover, oldConnection, me);

                     if (!sessionsReconnected) {
                        if (oldConnection != null) {
                           oldConnection.destroy();
                        }

                        oldConnection = connection;
                        connection = null;
                     }
                  }

                  if (connectorsCount >= serverLocator.getConnectorsSize()) {
                     connectorsCount = 0;
                     failoverRetries++;
                     if (failoverRetryPredicate.test(false, failoverRetries)) {
                        waitForRetry(failoverRetryInterval);
                        failoverRetryInterval = serverLocator.getNextRetryInterval(failoverRetryInterval, retryIntervalMultiplier, maxRetryInterval);
                     }
                  }
               }


               // Notify sessions after failover.
               for (ClientSessionInternal session : sessionsToFailover) {
                  session.postHandleFailover(connection, sessionsReconnected);
               }

               if (oldConnection != null) {
                  oldConnection.destroy();
               }

               if (connection != null) {
                  callFailoverListeners(FailoverEventType.FAILOVER_COMPLETED);
               }
            }
         } else {
            RemotingConnection connectionToDestory = connection;
            if (connectionToDestory != null) {
               connectionToDestory.destroy();
            }
            connection = null;
         }

         if (connection == null) {
            synchronized (sessions) {
               sessionsToClose = new HashSet<>(sessions);
            }
            callFailoverListeners(FailoverEventType.FAILOVER_FAILED);
            callSessionFailureListeners(me, true, false, scaleDownTargetNodeID);
         }
      } finally {
         localFailoverLock.unlock();
      }

      // This needs to be outside the failover lock to prevent deadlock
      if (connection != null) {
         callSessionFailureListeners(me, true, true);
      }
      if (sessionsToClose != null) {
         // If connection is null it means we didn't succeed in failing over or reconnecting
         // so we close all the sessions, so they will throw exceptions when attempted to be used

         for (ClientSessionInternal session : sessionsToClose) {
            try {
               session.cleanUp(true);
            } catch (Exception cause) {
               ActiveMQClientLogger.LOGGER.failedToCleanupSession(cause);
            }
         }
      }
   }

   private ClientSession createSessionInternal(final String rawUsername,
                                               final String rawPassword,
                                               final boolean xa,
                                               final boolean autoCommitSends,
                                               final boolean autoCommitAcks,
                                               final boolean preAcknowledge,
                                               final int ackBatchSize,
                                               final String clientID) throws ActiveMQException {
      String username;
      String password;
      String name = UUIDGenerator.getInstance().generateStringUUID();

      try {
         username = PasswordMaskingUtil.resolveMask(rawUsername, serverLocator.getPasswordCodec());
         password = PasswordMaskingUtil.resolveMask(rawPassword, serverLocator.getPasswordCodec());
      } catch (Exception e) {
         throw new ActiveMQException(e.getMessage(), e, ActiveMQExceptionType.GENERIC_EXCEPTION);
      }

      SessionContext context = createSessionChannel(name, username, password, xa, autoCommitSends, autoCommitAcks, preAcknowledge, clientID);

      ClientSessionInternal session = new ClientSessionImpl(this, name, username, password, xa, autoCommitSends, autoCommitAcks, preAcknowledge, serverLocator.isBlockOnAcknowledge(), serverLocator.isAutoGroup(), ackBatchSize, serverLocator.getConsumerWindowSize(), serverLocator.getConsumerMaxRate(), serverLocator.getConfirmationWindowSize(), serverLocator.getProducerWindowSize(), serverLocator.getProducerMaxRate(), serverLocator.isBlockOnNonDurableSend(), serverLocator.isBlockOnDurableSend(), serverLocator.isCacheLargeMessagesClient(), serverLocator.getMinLargeMessageSize(), serverLocator.isCompressLargeMessage(), serverLocator.getCompressionLevel(), serverLocator.getInitialMessagePacketSize(), serverLocator.getGroupID(), serverLocator.getOnMessageCloseTimeout(), context, orderedExecutorFactory.getExecutor(), orderedExecutorFactory.getExecutor(), flowControlExecutor, orderedExecutorFactory.getExecutor());

      synchronized (sessions) {
         if (closed || !clientProtocolManager.isAlive()) {
            session.close();
            throw ActiveMQClientMessageBundle.BUNDLE.unableToCreateSession();
         }
         sessions.add(session);
      }

      return session;

   }

   private void callSessionFailureListeners(final ActiveMQException me,
                                            final boolean afterReconnect,
                                            final boolean failedOver) {
      callSessionFailureListeners(me, afterReconnect, failedOver, null);
   }

   private void callSessionFailureListeners(final ActiveMQException me,
                                            final boolean afterReconnect,
                                            final boolean failedOver,
                                            final String scaleDownTargetNodeID) {
      final List<SessionFailureListener> listenersClone = new ArrayList<>(listeners);

      for (final SessionFailureListener listener : listenersClone) {
         try {
            if (afterReconnect) {
               listener.connectionFailed(me, failedOver, scaleDownTargetNodeID);
            } else {
               listener.beforeReconnect(me);
            }
         } catch (final Throwable t) {
            // Failure of one listener to execute shouldn't prevent others
            // from
            // executing
            ActiveMQClientLogger.LOGGER.failedToExecuteListener(t);
         }
      }
   }

   private void callFailoverListeners(FailoverEventType type) {
      final List<FailoverEventListener> listenersClone = new ArrayList<>(failoverListeners);

      for (final FailoverEventListener listener : listenersClone) {
         try {
            listener.failoverEvent(type);
         } catch (final Throwable t) {
            // Failure of one listener to execute shouldn't prevent others
            // from
            // executing
            ActiveMQClientLogger.LOGGER.failedToExecuteListener(t);
         }
      }
   }

   // Re-attach sessions all pre-existing sessions to the new remoting connection
   private boolean reconnectSessions(final Set<ClientSessionInternal> sessionsToFailover,
                                     final RemotingConnection oldConnection,
                                     final ActiveMQException cause) {
      if (connection == null) {
         if (!clientProtocolManager.isAlive()) {
            ActiveMQClientLogger.LOGGER.failedToConnectToServer();
         }
         return false;
      }

      List<FailureListener> oldListeners = oldConnection.getFailureListeners();

      List<FailureListener> newListeners = new ArrayList<>(connection.getFailureListeners());

      for (FailureListener listener : oldListeners) {
         // Add all apart from the old DelegatingFailureListener
         if (listener instanceof DelegatingFailureListener == false) {
            newListeners.add(listener);
         }
      }

      connection.setFailureListeners(newListeners);

      // This used to be done inside failover
      // it needs to be done on the protocol
      ((CoreRemotingConnection) connection).syncIDGeneratorSequence(((CoreRemotingConnection) oldConnection).getIDGeneratorSequence());

      boolean sessionFailoverError = false;
      for (ClientSessionInternal session : sessionsToFailover) {
         if (sessionFailoverError) {
            // If 1 session had a failover error, just detach the remaining sessions from the old connection so that
            // they are not closed when the old connection is destroyed
            session.getSessionContext().transferConnection(connection);
         } else {
            if (!session.handleFailover(connection, cause)) {
               sessionFailoverError = true;
            }
         }
      }

      return !sessionFailoverError;
   }

   private int getConnectionWithRetry(final int reconnectAttempts, RemotingConnection oldConnection) {
      if (!clientProtocolManager.isAlive()) {
         return 0;
      }
      if (logger.isTraceEnabled()) {
         logger.trace("getConnectionWithRetry::{} with retryInterval = {} multiplier = {}",
                      reconnectAttempts, retryInterval, retryIntervalMultiplier, new Exception("trace"));
      }

      long interval = retryInterval;

      int count = 0;

      while (clientProtocolManager.isAlive()) {
         if (logger.isDebugEnabled()) {
            logger.debug("Trying reconnection attempt {}/{}", count, reconnectAttempts);
         }

         if (getConnection() != null) {
            if (oldConnection != null && oldConnection instanceof CoreRemotingConnection oldRemotingConnection) {
               // transferring old connection version into the new connection
               ((CoreRemotingConnection)connection).setChannelVersion(oldRemotingConnection.getChannelVersion());
            }
            logger.debug("Reconnection successful");
            return count;
         } else {
            // Failed to get connection

            if (reconnectAttempts != 0) {
               count++;

               if (reconnectAttempts != -1 && count == reconnectAttempts) {
                  if (reconnectAttempts != 1) {
                     ActiveMQClientLogger.LOGGER.failedToConnectToServer(reconnectAttempts);
                  }

                  return count;
               }

               if (logger.isTraceEnabled()) {
                  logger.trace("Waiting {} milliseconds before next retry. RetryInterval={} and multiplier={}", interval, retryInterval, retryIntervalMultiplier);
               }

               if (waitForRetry(interval)) {
                  return count;
               }

               interval = serverLocator.getNextRetryInterval(interval, retryIntervalMultiplier, maxRetryInterval);
            } else {
               logger.debug("Could not connect to any server. Didn't have reconnection configured on the ClientSessionFactory");
               return count;
            }
         }
      }

      return count;
   }

   @Override
   public boolean waitForRetry(long interval) {
      try {
         if (clientProtocolManager.waitOnLatch(interval)) {
            return true;
         }
      } catch (InterruptedException ignore) {
         throw new ActiveMQInterruptedException(ignore);
      }
      return false;
   }

   private void cancelScheduledTasks() {
      Future<?> pingerFutureLocal = pingerFuture;
      if (pingerFutureLocal != null) {
         pingerFutureLocal.cancel(false);
      }
      PingRunnable pingRunnableLocal = pingRunnable;
      if (pingRunnableLocal != null) {
         pingRunnableLocal.cancel();
      }
      pingerFuture = null;
      pingRunnable = null;
   }

   private void checkCloseConnection() {
      RemotingConnection connectionInUse = connection;
      Connector connectorInUse = connector;

      if (connectionInUse != null && sessions.isEmpty()) {
         cancelScheduledTasks();

         try {
            connectionInUse.destroy();
         } catch (Throwable ignore) {
         }

         connection = null;

         try {
            if (connectorInUse != null) {
               connectorInUse.close();
            }
         } catch (Throwable ignore) {
         }

         connector = null;
      }
   }

   //The order of connector configs to try to get a connection:
   //currentConnectorConfig, backupConfig and then lastConnectorConfig.
   //On each successful connect, the current and last will be
   //updated properly.
   @Override
   public RemotingConnection getConnection() {
      if (closed) {
         throw new IllegalStateException("ClientSessionFactory is closed!");
      }
      if (!clientProtocolManager.isAlive()) {
         return null;
      }
      synchronized (connectionLock) {
         if (connection != null) {
            // a connection already exists, so returning the same one
            return connection;
         } else {
            RemotingConnection connection = establishNewConnection();

            this.connection = connection;

            //we check if we can actually connect.
            // we do it here as to receive the reply connection has to be not null
            //make sure to reset this.connection == null
            if (connection != null && primaryNodeID != null) {
               try {
                  if (!clientProtocolManager.checkForFailover(primaryNodeID)) {
                     connection.destroy();
                     this.connection = null;
                     return null;
                  }
               } catch (ActiveMQException e) {
                  connection.destroy();
                  this.connection = null;
                  return null;
               }
            }

            if (connection != null && serverLocator.getAfterConnectInternalListener() != null) {
               serverLocator.getAfterConnectInternalListener().onConnection(this);
            }

            if (serverLocator.getTopology() != null) {
               if (connection != null) {
                  logger.trace("{}::Subscribing Topology", this);
                  clientProtocolManager.sendSubscribeTopology(serverLocator.isClusterConnection());
               }
            } else {
               if (logger.isDebugEnabled()) {
                  logger.debug("serverLocator@{} had no topology", System.identityHashCode(serverLocator));
               }
            }

            return connection;
         }
      }
   }

   protected void schedulePing() {
      if (pingerFuture == null) {
         pingRunnable = new ClientSessionFactoryImpl.PingRunnable();

         if (clientFailureCheckPeriod != -1) {
            pingerFuture = scheduledThreadPool.scheduleWithFixedDelay(new ClientSessionFactoryImpl.ActualScheduledPinger(pingRunnable), 0, clientFailureCheckPeriod, TimeUnit.MILLISECONDS);
         }

         // To make sure the first ping will be sent
         pingRunnable.send();
      } else {
         // send a ping every time we create a new remoting connection
         // to set up its TTL on the server side
         pingRunnable.run();
      }
   }

   protected ConnectorFactory instantiateConnectorFactory(final String connectorFactoryClassName) {

      // Will set the instance here to avoid races where cachedFactory is set to null
      ConnectorFactory cachedFactory = connectorFactory;

      // First if cachedFactory had been used already, we take it from the cache.
      if (cachedFactory != null && cachedFactory.getClass().getName().equals(connectorFactoryClassName)) {
         return cachedFactory;
      }
      // else... we will try to instantiate a new one

      return SecurityManagerShim.doPrivileged((PrivilegedAction<ConnectorFactory>) () -> (ConnectorFactory) ClassloadingUtil.newInstanceFromClassLoader(ClientSessionFactoryImpl.class, connectorFactoryClassName, ConnectorFactory.class));
   }

   public class CloseRunnable implements Runnable {

      private final RemotingConnection conn;
      private final DisconnectReason reason;
      private final String targetNodeID;

      public CloseRunnable(RemotingConnection conn, DisconnectReason reason, String targetNodeID) {
         this.conn = conn;
         this.reason = reason;
         this.targetNodeID = targetNodeID;
      }

      // Must be executed on new thread since cannot block the Netty thread for a long time and fail
      // can cause reconnect loop
      @Override
      public void run() {
         try {
            CLOSE_RUNNABLES.add(this);
            if (reason.isRedirect()) {
               conn.fail(ActiveMQClientMessageBundle.BUNDLE.redirected());
            } else if (reason.isScaleDown()) {
               conn.fail(ActiveMQClientMessageBundle.BUNDLE.disconnected(), targetNodeID);
            } else {
               conn.fail(ActiveMQClientMessageBundle.BUNDLE.disconnected());
            }
         } finally {
            CLOSE_RUNNABLES.remove(this);
         }

      }

      public ClientSessionFactoryImpl stop() {
         causeExit();
         CLOSE_RUNNABLES.remove(this);
         return ClientSessionFactoryImpl.this;
      }

   }

   @Override
   public void setReconnectAttempts(final int attempts) {
      reconnectAttempts = attempts;
   }

   public int getReconnectAttempts() {
      return reconnectAttempts;
   }

   @Override
   public Object getConnector() {
      return connector;
   }

   @Override
   public ConfirmationWindowWarning getConfirmationWindowWarning() {
      return confirmationWindowWarning;
   }

   protected Connection openTransportConnection(final Connector connector) {
      connector.start();

      Connection transportConnection = connector.createConnection();

      if (transportConnection == null) {
         logger.debug("Connector towards {} failed", connector);

         try {
            connector.close();
         } catch (Throwable t) {
         }
      }

      return transportConnection;
   }

   protected Connector createConnector(ConnectorFactory connectorFactory, TransportConfiguration configuration) {
      Connector connector = connectorFactory.createConnector(configuration.getCombinedParams(), new DelegatingBufferHandler(), this, closeExecutor, threadPool, scheduledThreadPool, clientProtocolManager);
      if (connector instanceof NettyConnector nettyConnector) {
         if (nettyConnector.getConnectTimeoutMillis() < 0) {
            nettyConnector.setConnectTimeoutMillis((int)serverLocator.getConnectionTTL());
         }

      }

      return connector;
   }

   private void checkTransportKeys(final ConnectorFactory factory, final TransportConfiguration tc) {
   }

   /**
    * It will connect to either primary or backup accordingly to the current configurations it will also switch to
    * backup case it can't connect to primary and there's a backup configured
    */
   protected Connection createTransportConnection() {
      Connection transportConnection = null;

      try {
         //Try to connect with the current connector configuration
         transportConnection = createTransportConnection("current", currentConnectorConfig);
         if (transportConnection != null) {
            return transportConnection;
         }


         if (backupConnectorConfig != null) {
            //Try to connect with the client connector that match the backup connector name
            String backupConnectorName = backupConnectorConfig.getName();
            if (backupConnectorName != null && connectorConfigs != null) {
               for (TransportConfiguration connectorConfig : connectorConfigs) {
                  if (backupConnectorName.equals(connectorConfig.getName())) {
                     //Try to connect with the backup connector configuration
                     transportConnection = createTransportConnection("backup", connectorConfig);
                     if (transportConnection != null) {
                        return transportConnection;
                     }
                  }
               }
            }

            //Try to connect with the backup connector configuration
            transportConnection = createTransportConnection("backup", backupConnectorConfig);
            if (transportConnection != null) {
               return transportConnection;
            }
         }

         if (previousConnectorConfig != null && !currentConnectorConfig.equals(previousConnectorConfig)) {
            //Try to connect with the previous connector configuration
            transportConnection = createTransportConnection("previous", previousConnectorConfig);
            if (transportConnection != null) {
               return transportConnection;
            }
         }

         if (!currentConnectorConfig.equals(connectorConfig)) {
            //Try to connect with the initial connector configuration
            transportConnection = createTransportConnection("initial", connectorConfig);
            if (transportConnection != null) {
               return transportConnection;
            }
         }

         logger.debug("no connection been made, returning null");
         return null;
      } catch (Exception cause) {
         // Sanity catch for badly behaved remoting plugins

         ActiveMQClientLogger.LOGGER.createConnectorException(cause);

         if (connector != null) {
            try {
               connector.close();
            } catch (Throwable t) {
            }
         }
         connector = null;
         return null;
      }

   }

   private Connection createTransportConnection(String name, TransportConfiguration transportConnectorConfig) {
      ConnectorFactory transportConnectorFactory = instantiateConnectorFactory(
         transportConnectorConfig.getFactoryClassName());

      if (logger.isDebugEnabled()) {
         logger.debug("Trying to connect with connectorFactory={} and {}ConnectorConfig: {}", transportConnectorFactory, name, transportConnectorConfig);
      }

      Connector transportConnector = createConnector(transportConnectorFactory, transportConnectorConfig);

      Connection transportConnection = openTransportConnection(transportConnector);

      if (transportConnection != null) {
         logger.debug("Connected with the {}ConnectorConfig={}", name, transportConnectorConfig);

         connector = transportConnector;
         connectorFactory = transportConnectorFactory;
         previousConnectorConfig = currentConnectorConfig;
         currentConnectorConfig = transportConnectorConfig;
      }

      return transportConnection;
   }

   private class DelegatingBufferHandler implements BufferHandler {

      @Override
      public void bufferReceived(final Object connectionID, final ActiveMQBuffer buffer) {
         RemotingConnection theConn = connection;

         if (theConn != null && connectionID.equals(theConn.getID())) {
            try {
               theConn.bufferReceived(connectionID, buffer);
            } catch (final RuntimeException e) {
               ActiveMQClientLogger.LOGGER.disconnectOnErrorDecoding(e);
               threadPool.execute(() -> theConn.fail(new ActiveMQException(e.getMessage())));
            }
         } else {
            logger.debug("TheConn == null on ClientSessionFactoryImpl::DelegatingBufferHandler, ignoring packet");
         }
      }

      @Override
      public void endOfBatch(final Object connectionID) {
         RemotingConnection theConn = connection;

         if (theConn != null && connectionID.equals(theConn.getID())) {
            try {
               theConn.endOfBatch(connectionID);
            } catch (final RuntimeException e) {
               ActiveMQClientLogger.LOGGER.disconnectOnErrorDecoding(e);
               threadPool.execute(() -> theConn.fail(new ActiveMQException(e.getMessage())));
            }
         } else {
            logger.debug("TheConn == null on ClientSessionFactoryImpl::DelegatingBufferHandler, ignoring packet");
         }
      }
   }

   private final class DelegatingFailureListener implements FailureListener {

      private final Object connectionID;

      DelegatingFailureListener(final Object connectionID) {
         this.connectionID = connectionID;
      }

      @Override
      public void connectionFailed(final ActiveMQException me, final boolean failedOver) {
         connectionFailed(me, failedOver, null);
      }

      @Override
      public void connectionFailed(final ActiveMQException me, final boolean failedOver, String scaleDownTargetNodeID) {
         handleConnectionFailure(connectionID, me, scaleDownTargetNodeID);
      }

      @Override
      public String toString() {
         return DelegatingFailureListener.class.getSimpleName() + "('reconnectsOrFailover', hash=" +
            super.hashCode() + ")";
      }
   }

   private static final class ActualScheduledPinger implements Runnable {

      private final WeakReference<PingRunnable> pingRunnable;

      ActualScheduledPinger(final PingRunnable runnable) {
         pingRunnable = new WeakReference<>(runnable);
      }

      @Override
      public void run() {
         PingRunnable runnable = pingRunnable.get();

         if (runnable != null) {
            runnable.run();
         }
      }

   }

   private final class PingRunnable implements Runnable {

      private boolean cancelled;

      private boolean first;

      private long lastCheck = System.currentTimeMillis();

      @Override
      public synchronized void run() {
         if (cancelled || stopPingingAfterOne && !first) {
            return;
         }

         first = false;

         long now = System.currentTimeMillis();

         final RemotingConnection connectionInUse = connection;

         if (connectionInUse != null && clientFailureCheckPeriod != -1 && connectionTTL != -1 && now >= lastCheck + connectionTTL) {
            if (!connectionInUse.checkDataReceived()) {

               // We use a different thread to send the fail
               // but the exception has to be created here to preserve the stack trace
               final ActiveMQException me = ActiveMQClientMessageBundle.BUNDLE.connectionTimedOut(connection.getTransportConnection());

               cancelled = true;

               // Must be executed on different thread
               threadPool.execute(() -> connectionInUse.fail(me));

               return;
            } else {
               lastCheck = now;
            }
         }

         send();
      }

      public void send() {
         clientProtocolManager.ping(connectionTTL);
      }

      public synchronized void cancel() {
         cancelled = true;
      }
   }

   protected RemotingConnection establishNewConnection() {
      Connection transportConnection = createTransportConnection();

      if (transportConnection == null) {
         logger.trace("Neither backup or primary were active, will just give up now");
         return null;
      }

      RemotingConnection newConnection = clientProtocolManager.connect(transportConnection, callTimeout, callFailoverTimeout, incomingInterceptors, outgoingInterceptors, new SessionFactoryTopologyHandler());

      newConnection.addFailureListener(new DelegatingFailureListener(newConnection.getID()));

      schedulePing();

      logger.trace("returning {}", newConnection);

      return newConnection;
   }

   protected SessionContext createSessionChannel(final String name,
                                                 final String username,
                                                 final String password,
                                                 final boolean xa,
                                                 final boolean autoCommitSends,
                                                 final boolean autoCommitAcks,
                                                 final boolean preAcknowledge,
                                                 final String clientID) throws ActiveMQException {
      synchronized (createSessionLock) {
         return clientProtocolManager.createSessionContext(name, username, password, xa, autoCommitSends, autoCommitAcks, preAcknowledge, serverLocator.getMinLargeMessageSize(), serverLocator.getConfirmationWindowSize(), clientID);
      }
   }

   @Override
   public String getPrimaryNodeId() {
      return primaryNodeID;
   }

   class SessionFactoryTopologyHandler implements TopologyResponseHandler {

      @Override
      public void nodeDisconnected(RemotingConnection conn, String nodeID, DisconnectReason reason, String targetNodeID, TransportConfiguration tagetConnector) {

         if (logger.isTraceEnabled()) {
            logger.trace("Disconnect being called on client: server locator = {} notifying node {} as down with reason {}",
                         serverLocator, nodeID, reason, new Exception("trace"));
         }

         serverLocator.notifyNodeDown(System.currentTimeMillis(), nodeID, true);

         if (reason.isRedirect()) {
            if (serverLocator.isHA()) {
               TopologyMemberImpl topologyMember = serverLocator.getTopology().getMember(nodeID);

               if (topologyMember != null) {
                  if (topologyMember.getConnector().getB() != null) {
                     backupConnectorConfig = topologyMember.getConnector().getB();
                  } else {
                     logger.debug("The topology member {} with connector {} has no backup", nodeID, tagetConnector);
                  }
               } else {
                  logger.debug("The topology member {} with connector {} not found", nodeID, tagetConnector);
               }
            }

            currentConnectorConfig = tagetConnector;
         }

         closeExecutor.execute(new CloseRunnable(conn, reason, targetNodeID));

      }

      @Override
      public void notifyNodeUp(long uniqueEventID,
                               String nodeID,
                               String backupGroupName,
                               String scaleDownGroupName,
                               Pair<TransportConfiguration, TransportConfiguration> connectorPair,
                               boolean isLast) {

         try {
            // if it is our connector then set the primary id used for failover
            if (connectorPair.getA() != null && TransportConfigurationUtil.isSameHost(connectorPair.getA(), currentConnectorConfig)) {
               primaryNodeID = nodeID;
            }

            serverLocator.notifyNodeUp(uniqueEventID, nodeID, backupGroupName, scaleDownGroupName, connectorPair, isLast);
         } finally {
            if (isLast) {
               topologyReady = true;
               latchFinalTopology.countDown();
            }
         }

      }

      @Override
      public void notifyNodeDown(long eventTime, String nodeID, boolean disconnect) {
         serverLocator.notifyNodeDown(eventTime, nodeID, disconnect);
      }
   }

   public void flushCloseExecutor(int time, TimeUnit unit) {
      closeExecutor.flush(time, unit);
   }
}
