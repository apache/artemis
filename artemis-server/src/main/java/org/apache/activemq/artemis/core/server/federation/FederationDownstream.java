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
package org.apache.activemq.artemis.core.server.federation;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.SessionFailureListener;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryInternal;
import org.apache.activemq.artemis.core.client.impl.ClientSessionInternal;
import org.apache.activemq.artemis.core.config.FederationConfiguration;
import org.apache.activemq.artemis.core.config.federation.FederationDownstreamConfiguration;
import org.apache.activemq.artemis.core.protocol.core.Channel;
import org.apache.activemq.artemis.core.protocol.core.CoreRemotingConnection;
import org.apache.activemq.artemis.core.protocol.core.impl.ChannelImpl.CHANNEL_ID;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.FederationDownstreamConnectMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.FederationStreamConnectMessage;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;

public class FederationDownstream extends AbstractFederationStream implements SessionFailureListener {

   private FederationDownstreamConfiguration config;
   private ClientSessionFactoryInternal clientSessionFactory;
   private ClientSessionInternal clientSession;
   private Channel channel;
   private AtomicBoolean initialized = new AtomicBoolean();
   private final ScheduledExecutorService scheduledExecutorService;
   private final int intialConnectDelayMultiplier = 2;
   private final int intialConnectDelayMax = 30;

   public static final String FEDERATION_DOWNSTREAM_NAME = "federation-downstream-name";
   private AtomicBoolean started = new AtomicBoolean();
   private FederationConfiguration federationConfiguration;

   public FederationDownstream(ActiveMQServer server, Federation federation, String name, FederationDownstreamConfiguration config,
                               final FederationConnection connection) {
      super(server, federation, name, config, connection);
      this.config = config;
      this.scheduledExecutorService = server.getScheduledPool();
   }

   @Override
   public synchronized void start() {
      super.start();
      callFederationStreamStartedPlugins();
      try {
         deploy(federationConfiguration);
      } catch (ActiveMQException e) {
         throw new IllegalStateException(e.getMessage(), e);
      }
   }

   @Override
   public synchronized void stop() {
      super.stop();
      callFederationStreamStoppedPlugins();
   }

   public void deploy(FederationConfiguration federationConfiguration)
      throws ActiveMQException {

      this.federationConfiguration = federationConfiguration;

      if (connection.isStarted() && started.compareAndSet(false, true)) {
         final FederationStreamConnectMessage message = new FederationDownstreamConnectMessage();
         message.setName(federationConfiguration.getName());
         message.setCredentials(federationConfiguration.getCredentials());
         message.setStreamConfiguration(config);
         message.setFederationPolicyMap(federationConfiguration.getFederationPolicyMap());
         message.setTransformerConfigurationMap(federationConfiguration.getTransformerConfigurationMap());

         if (config.getUpstreamConfigurationRef() != null
            && config.getUpstreamConfiguration() == null) {
            TransportConfiguration[] configs = server.getConfiguration()
               .getTransportConfigurations(config.getUpstreamConfigurationRef());
            if (configs != null && configs.length > 0) {
               config.setUpstreamConfiguration(configs[0]);
            } else {
               ActiveMQServerLogger.LOGGER.federationCantFindUpstreamConnector(config.getName(), config
                  .getUpstreamConfigurationRef());
               throw new ActiveMQException("Could not locate upstream transport configuration for federation downstream: " + config.getName()
                                           + "; upstream ref: " + config.getUpstreamConfigurationRef());
            }
         }

         try {
            scheduleConnect(0, message);
         } catch (Exception e) {
            throw new ActiveMQException(e.getMessage(), e, ActiveMQExceptionType.GENERIC_EXCEPTION);
         }

         ActiveMQServerLogger.LOGGER.federationDownstreamDeployed(config.getName());
      }
   }

   public void undeploy() {
      try {
         if (started.compareAndSet(true, false)) {
            disconnect();
            ActiveMQServerLogger.LOGGER.federationDownstreamUnDeployed(config.getName());
         }
      } catch (ActiveMQException e) {
         throw new IllegalStateException(e.getMessage(), e);
      }
   }

   private void scheduleConnect(final int delay, final FederationStreamConnectMessage message) {
      scheduledExecutorService.schedule(() -> {
         try {
            connect();
            if (initialized.compareAndSet(false, true)) {
               channel.send(message);
            }
         } catch (Exception e) {
            scheduleConnect(FederatedQueueConsumer.getNextDelay(delay, intialConnectDelayMultiplier, intialConnectDelayMax),
                            message);
         }
      }, delay, TimeUnit.SECONDS);
   }

   private void connect() throws Exception {
      try {
         if (clientSession == null) {
            synchronized (this) {
               this.clientSessionFactory = (ClientSessionFactoryInternal) getConnection().clientSessionFactory();
               this.clientSession = (ClientSessionInternal) clientSessionFactory.createSession(getUser(), getPassword(), false, true, true, clientSessionFactory.getServerLocator().isPreAcknowledge(), clientSessionFactory.getServerLocator().getAckBatchSize());
               this.clientSession.addFailureListener(this);
               this.clientSession.addMetaData(FederatedQueueConsumer.FEDERATION_NAME, federation.getName().toString());
               this.clientSession.addMetaData(FEDERATION_DOWNSTREAM_NAME, config.getName());
               this.clientSession.start();

               CoreRemotingConnection connection = (CoreRemotingConnection) clientSessionFactory.getConnection();
               channel = connection.getChannel(CHANNEL_ID.FEDERATION.id, -1);
            }
         }
      } catch (Exception e) {
         try {
            if (clientSessionFactory != null) {
               clientSessionFactory.cleanup();
            }
            disconnect();
         } catch (ActiveMQException ignored) {
         }
         throw e;
      }
   }

   @Override
   public void connectionFailed(ActiveMQException exception, boolean failedOver) {
      connectionFailed(exception, failedOver, null);
   }

   @Override
   public void connectionFailed(ActiveMQException exception, boolean failedOver, String scaleDownTargetNodeID) {
      try {
         started.set(false);
         initialized.set(false);
         channel.close();
         clientSessionFactory.cleanup();
         clientSessionFactory.close();
         channel = null;
         clientSession = null;
         clientSessionFactory = null;
      } catch (Throwable dontCare) {
      }
      start();
   }

   private void disconnect() throws ActiveMQException {
      initialized.set(false);

      if (channel != null) {
         channel.close();
      }
      if (clientSession != null) {
         clientSession.close();
      }
      channel = null;
      clientSession = null;

      if (clientSessionFactory != null && (!getConnection().isSharedConnection() || clientSessionFactory.numSessions() == 0)) {
         clientSessionFactory.close();
         clientSessionFactory = null;
      }
   }

   @Override
   public void beforeReconnect(ActiveMQException exception) {
   }

}
