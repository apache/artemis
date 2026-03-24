/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQNonExistentQueueException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.SessionFailureListener;
import org.apache.activemq.artemis.core.client.impl.ClientLargeMessageInternal;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryInternal;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.LargeServerMessage;
import org.apache.activemq.artemis.core.server.transformer.Transformer;
import org.jboss.logging.Logger;

import static org.apache.activemq.artemis.core.client.impl.LargeMessageControllerImpl.LargeData;

public class FederatedQueueConsumerImpl implements FederatedQueueConsumer, SessionFailureListener {

   private static final Logger logger = Logger.getLogger(FederatedQueueConsumerImpl.class);
   private final ActiveMQServer server;
   private final Federation federation;
   private final FederatedConsumerKey key;
   private final Transformer transformer;
   private final FederationUpstream upstream;
   private final AtomicInteger count = new AtomicInteger();
   private final ScheduledExecutorService scheduledExecutorService;
   private final int intialConnectDelayMultiplier = 2;
   private final int intialConnectDelayMax = 30;
   private final ClientSessionCallback clientSessionCallback;

   private ClientSessionFactoryInternal clientSessionFactory;
   private ClientSession clientSession;
   private ClientConsumer clientConsumer;

   public FederatedQueueConsumerImpl(Federation federation, ActiveMQServer server, Transformer transformer, FederatedConsumerKey key, FederationUpstream upstream, ClientSessionCallback clientSessionCallback) {
      this.federation = federation;
      this.server = server;
      this.key = key;
      this.transformer = transformer;
      this.upstream = upstream;
      this.scheduledExecutorService = server.getScheduledPool();
      this.clientSessionCallback = clientSessionCallback;
   }

   @Override
   public FederationUpstream getFederationUpstream() {
      return upstream;
   }

   @Override
   public Federation getFederation() {
      return federation;
   }

   @Override
   public FederatedConsumerKey getKey() {
      return key;
   }

   @Override
   public ClientSession getClientSession() {
      return clientSession;
   }

   @Override
   public int incrementCount() {
      return count.incrementAndGet();
   }

   @Override
   public int decrementCount() {
      return count.decrementAndGet();
   }

   @Override
   public void start() {
      scheduleConnect(0);
   }

   private void scheduleConnect(int delay) {
      scheduledExecutorService.schedule(() -> {
         try {
            connect();
         } catch (Exception e) {
            scheduleConnect(FederatedQueueConsumer.getNextDelay(delay, intialConnectDelayMultiplier, intialConnectDelayMax));
         }
      }, delay, TimeUnit.SECONDS);
   }

   private void connect() throws Exception {
      try {
         if (clientConsumer == null) {
            synchronized (this) {
               this.clientSessionFactory = (ClientSessionFactoryInternal) upstream.getConnection().clientSessionFactory();
               this.clientSession = clientSessionFactory.createSession(upstream.getUser(), upstream.getPassword(), false, true, true, clientSessionFactory.getServerLocator().isPreAcknowledge(), clientSessionFactory.getServerLocator().getAckBatchSize());
               this.clientSession.addFailureListener(this);
               this.clientSession.addMetaData(FEDERATION_NAME, federation.getName().toString());
               this.clientSession.addMetaData(FEDERATION_UPSTREAM_NAME, upstream.getName().toString());
               this.clientSession.start();
               if (clientSessionCallback != null) {
                  clientSessionCallback.callback(clientSession);
               }
               if (clientSession.queueQuery(key.getQueueName()).isExists()) {
                  this.clientConsumer = clientSession.createConsumer(key.getQueueName(), key.getFilterString(), key.getPriority(), false);
                  this.clientConsumer.setMessageHandler(this);
               } else {
                  throw new ActiveMQNonExistentQueueException("Queue " + key.getQueueName() + " does not exist on remote");
               }
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
   public void close() {
      scheduleDisconnect(0);
   }

   private void scheduleDisconnect(int delay) {
      scheduledExecutorService.schedule(() -> {
         try {
            disconnect();
         } catch (Exception ignored) {
         }
      }, delay, TimeUnit.SECONDS);
   }

   private void disconnect() throws ActiveMQException {
      if (clientConsumer != null) {
         clientConsumer.close();
      }
      if (clientSession != null) {
         clientSession.close();
      }
      clientConsumer = null;
      clientSession = null;

      if (clientSessionFactory != null && (!upstream.getConnection().isSharedConnection() ||
          clientSessionFactory.numSessions() == 0)) {
         clientSessionFactory.close();
         clientSessionFactory = null;
      }
   }

   @Override
   public void onMessage(ClientMessage clientMessage) {
      try {
         Message message = clientMessage;
         if (message instanceof ClientLargeMessageInternal) {

            final StorageManager storageManager = server.getStorageManager();
            LargeServerMessage lsm = storageManager.createLargeMessage(storageManager.generateID(), message);

            LargeData largeData = null;
            do {
               // block on reading all pending chunks, ok as we are called from an executor
               largeData = ((ClientLargeMessageInternal) clientMessage).getLargeMessageController().take();
               lsm.addBytes(largeData.getChunk());
            }
            while (largeData.isContinues());

            message = lsm.toMessage();
            lsm.releaseResources(true, true);
         }

         if (server.hasBrokerFederationPlugins()) {
            try {
               server.callBrokerFederationPlugins(plugin -> plugin.beforeFederatedQueueConsumerMessageHandled(this, clientMessage));
            } catch (ActiveMQException t) {
               ActiveMQServerLogger.LOGGER.federationPluginExecutionError(t, "beforeFederatedQueueConsumerMessageHandled");
               throw new IllegalStateException(t.getMessage(), t.getCause());
            }
         }

         message = transformer == null ? message : transformer.transform(message);
         if (message != null) {
            server.getPostOffice().route(message, true);
         }
         clientMessage.acknowledge();

         if (server.hasBrokerFederationPlugins()) {
            try {
               server.callBrokerFederationPlugins(plugin -> plugin.afterFederatedQueueConsumerMessageHandled(this, clientMessage));
            } catch (ActiveMQException t) {
               ActiveMQServerLogger.LOGGER.federationPluginExecutionError(t, "afterFederatedQueueConsumerMessageHandled");
               throw new IllegalStateException(t.getMessage(), t.getCause());
            }
         }
      } catch (Exception e) {
         ActiveMQServerLogger.LOGGER.federationDispatchError(e, clientMessage.toString());
         try {
            clientSession.rollback();
         } catch (ActiveMQException e1) {
         }
      }
   }

   @Override
   public void connectionFailed(ActiveMQException exception, boolean failedOver) {
      connectionFailed(exception, failedOver, null);
   }

   @Override
   public void connectionFailed(ActiveMQException exception, boolean failedOver, String scaleDownTargetNodeID) {
      try {
         clientSessionFactory.cleanup();
         clientSessionFactory.close();
         clientConsumer = null;
         clientSession = null;
         clientSessionFactory = null;
      } catch (Throwable dontCare) {
      }
      start();
   }

   @Override
   public void beforeReconnect(ActiveMQException exception) {
   }

   public interface ClientSessionCallback {
      void callback(ClientSession clientSession) throws ActiveMQException;
   }
}