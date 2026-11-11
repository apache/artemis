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
package org.apache.activemq.artemis.core.remoting.impl.invm;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.core.server.ActiveMQMessageBundle;
import org.apache.activemq.artemis.spi.core.remoting.AbstractConnector;
import org.apache.activemq.artemis.spi.core.remoting.Acceptor;
import org.apache.activemq.artemis.spi.core.remoting.BaseConnectionLifeCycleListener;
import org.apache.activemq.artemis.spi.core.remoting.BufferHandler;
import org.apache.activemq.artemis.spi.core.remoting.ClientConnectionLifeCycleListener;
import org.apache.activemq.artemis.spi.core.remoting.ClientProtocolManager;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.apache.activemq.artemis.spi.core.remoting.ConnectionLifeCycleListener;
import org.apache.activemq.artemis.utils.ActiveMQThreadFactory;
import org.apache.activemq.artemis.utils.ActiveMQThreadPoolExecutor;
import org.apache.activemq.artemis.utils.ConfigurationHelper;
import org.apache.activemq.artemis.utils.actors.ArtemisExecutor;
import org.apache.activemq.artemis.utils.actors.OrderedExecutorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class InVMConnector extends AbstractConnector {

   public static String INVM_CONNECTOR_TYPE = "IN-VM";

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final Map<String, Object> DEFAULT_CONFIG;

   static {
      Map<String, Object> config = new HashMap<>();
      config.put(TransportConstants.SERVER_ID_PROP_NAME, TransportConstants.DEFAULT_SERVER_ID);
      DEFAULT_CONFIG = Collections.unmodifiableMap(config);
   }

   // Used for testing failure only
   public static volatile boolean failOnCreateConnection;

   public static volatile int numberOfFailures = -1;

   private static volatile int failures;

   public static synchronized void resetFailures() {
      InVMConnector.failures = 0;
      InVMConnector.failOnCreateConnection = false;
      InVMConnector.numberOfFailures = -1;
   }

   private static synchronized void incFailures() {
      InVMConnector.failures++;
      if (InVMConnector.failures == InVMConnector.numberOfFailures) {
         InVMConnector.resetFailures();
      }
   }

   protected final int id;

   private final ClientProtocolManager protocolManager;

   private final BufferHandler handler;

   private final BaseConnectionLifeCycleListener listener;

   private final InVMAcceptor acceptor;

   private final ConcurrentMap<String, Connection> connections = new ConcurrentHashMap<>();

   private volatile boolean started;

   protected final OrderedExecutorFactory executorFactory;

   private final Executor closeExecutor;

   private final boolean bufferPoolingEnabled;

   private static ExecutorService executorService;

   public static synchronized void resetThreadPool() {
      if (executorService != null) {
         executorService.shutdownNow();
         if (executorService instanceof ThreadPoolExecutor threadPoolExecutor && threadPoolExecutor.getThreadFactory() instanceof ActiveMQThreadFactory activeMQThreadFactory) {
            if (!activeMQThreadFactory.join(10, TimeUnit.SECONDS)) {
               // resetThreadPool is only used on tests.
               // no need to use a logger method, this is just fine.
               logger.warn("Thread pool is still busy. couldn't stop on time");
            }
         }
         executorService = null;
      }
   }

   private static synchronized ExecutorService getInVMExecutor() {
      if (executorService == null) {
         if (ActiveMQClient.getGlobalThreadPoolSize() <= -1) {
            executorService = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<>(), ActiveMQThreadFactory.defaultThreadFactory(InVMConnector.class.getName()));
         } else {
            executorService = new ActiveMQThreadPoolExecutor(0, ActiveMQClient.getGlobalThreadPoolSize(), 60L, TimeUnit.SECONDS, ActiveMQThreadFactory.defaultThreadFactory(InVMConnector.class.getName()));
         }
      }
      return executorService;
   }

   public InVMConnector(final Map<String, Object> configuration,
                        final BufferHandler handler,
                        final ClientConnectionLifeCycleListener listener,
                        final Executor closeExecutor,
                        final Executor threadPool,
                        ClientProtocolManager protocolManager) {
      super(configuration);
      this.listener = listener;

      id = ConfigurationHelper.getIntProperty(TransportConstants.SERVER_ID_PROP_NAME, 0, configuration);

      bufferPoolingEnabled = ConfigurationHelper.getBooleanProperty(TransportConstants.BUFFER_POOLING, TransportConstants.DEFAULT_BUFFER_POOLING, configuration);

      this.handler = handler;

      this.closeExecutor = closeExecutor;

      executorFactory = new OrderedExecutorFactory(getInVMExecutor());

      InVMRegistry registry = InVMRegistry.instance;

      acceptor = registry.getAcceptor(id);

      this.protocolManager = protocolManager;
   }

   public Acceptor getAcceptor() {
      return acceptor;
   }

   @Override
   public synchronized void close() {
      if (!started) {
         return;
      }

      for (Connection connection : connections.values()) {
         listener.connectionDestroyed(connection.getID(), false);
      }

      started = false;
   }

   @Override
   public boolean isStarted() {
      return started;
   }

   @Override
   public Connection createConnection() {
      if (InVMConnector.failOnCreateConnection) {
         InVMConnector.incFailures();

         logger.debug("Returning null on InVMConnector for tests");
         // For testing only
         return null;
      }

      if (acceptor == null) {
         return null;
      }

      if (acceptor.getConnectionsAllowed() == -1 || acceptor.getConnectionCount() < acceptor.getConnectionsAllowed()) {
         Connection conn = internalCreateConnection(acceptor.getHandler(), new Listener(), acceptor.getExecutorFactory().getExecutor());

         acceptor.connect((String) conn.getID(), handler, this, executorFactory.getExecutor());
         return conn;
      } else {
         if (logger.isDebugEnabled()) {
            logger.debug("Connection limit of {} reached. Refusing connection.", acceptor.getConnectionsAllowed());
         }
         return null;
      }
   }

   @Override
   public synchronized void start() {
      started = true;
      logger.debug("Started InVM Connector");
   }

   public BufferHandler getHandler() {
      return handler;
   }

   public void disconnect(final String connectionID) {
      if (!started) {
         return;
      }

      Connection conn = connections.get(connectionID);

      if (conn != null) {
         conn.close();
      }
   }

   // This may be an injection point for mocks on tests
   protected Connection internalCreateConnection(final BufferHandler handler,
                                                 final ClientConnectionLifeCycleListener listener,
                                                 final ArtemisExecutor serverExecutor) {
      // No acceptor on a client connection
      InVMConnection inVMConnection = new InVMConnection(id, handler, listener, serverExecutor);
      inVMConnection.setEnableBufferPooling(bufferPoolingEnabled);

      listener.connectionCreated(null, inVMConnection, protocolManager);
      return inVMConnection;
   }

   @Override
   public boolean isEquivalent(Map<String, Object> configuration) {
      int serverId = ConfigurationHelper.getIntProperty(TransportConstants.SERVER_ID_PROP_NAME, 0, configuration);
      return id == serverId;
   }

   private class Listener implements ClientConnectionLifeCycleListener {

      @Override
      public void connectionCreated(final ActiveMQComponent component,
                                    final Connection connection,
                                    final ClientProtocolManager protocol) {
         if (connections.putIfAbsent((String) connection.getID(), connection) != null) {
            throw ActiveMQMessageBundle.BUNDLE.connectionExists(connection.getID());
         }

         //noinspection deprecation
         if (listener instanceof ConnectionLifeCycleListener) {
            listener.connectionCreated(component, connection, protocol.getName());
         } else {
            listener.connectionCreated(component, connection, protocol);
         }

      }

      @Override
      public void connectionDestroyed(final Object connectionID, boolean failed) {
         if (connections.remove(connectionID) != null) {
            // Close the corresponding connection on the other side
            acceptor.disconnect((String) connectionID);

            // Execute on different thread to avoid deadlocks
            closeExecutor.execute(() -> listener.connectionDestroyed(connectionID, failed));
         }
      }

      @Override
      public void connectionException(final Object connectionID, final ActiveMQException me) {
         // Execute on different thread to avoid deadlocks
         closeExecutor.execute(() -> listener.connectionException(connectionID, me));
      }

      @Override
      public void connectionReadyForWrites(Object connectionID, boolean ready) {
      }
   }

}
