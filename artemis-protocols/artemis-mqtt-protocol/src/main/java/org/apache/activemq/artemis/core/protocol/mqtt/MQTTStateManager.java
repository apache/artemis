/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.activemq.artemis.core.protocol.mqtt;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.handler.codec.mqtt.MqttTopicSubscription;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.filter.impl.FilterImpl;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.utils.collections.LinkedListIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MQTTStateManager {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
   private final ActiveMQServer server;
   private final Map<String, MQTTSessionState> sessionStates = new ConcurrentHashMap<>();
   private final Queue sessionStore;
   private static final Map<Integer, MQTTStateManager> INSTANCES = new HashMap<>();
   private final Map<String, MQTTConnection> connectedClients  = new ConcurrentHashMap<>();
   private final boolean subscriptionPersistenceEnabled;

   /*
    * Even though there may be multiple instances of MQTTProtocolManager (e.g. for MQTT on different ports) we only want
    * one instance of MQTTSessionStateManager per-broker with the understanding that there can be multiple brokers in
    * the same JVM.
    */
   public static synchronized MQTTStateManager getInstance(ActiveMQServer server) throws Exception {
      MQTTStateManager instance = INSTANCES.get(System.identityHashCode(server));
      if (instance == null) {
         instance = new MQTTStateManager(server);
         INSTANCES.put(System.identityHashCode(server), instance);
      }

      return instance;
   }

   public static synchronized void removeInstance(ActiveMQServer server) {
      INSTANCES.remove(System.identityHashCode(server));
   }

   private MQTTStateManager(ActiveMQServer server) throws Exception {
      this.server = server;
      this.subscriptionPersistenceEnabled = server.getConfiguration().isMqttSubscriptionPersistenceEnabled();
      if (subscriptionPersistenceEnabled) {
         this.sessionStore = server.createQueue(QueueConfiguration.of(MQTTUtil.MQTT_SESSION_STORE).setRoutingType(RoutingType.ANYCAST).setLastValue(true).setDurable(true).setInternal(true).setAutoCreateAddress(true), true);

         // load subscription data from queue
         try (LinkedListIterator<MessageReference> iterator = sessionStore.browserIterator()) {
            while (iterator.hasNext()) {
               Message message = iterator.next().getMessage();
               if (!(message instanceof CoreMessage)) {
                  MQTTLogger.LOGGER.sessionStateMessageIncorrectType(message.getClass().getName());
                  continue;
               }
               String clientId = message.getStringProperty(Message.HDR_LAST_VALUE_NAME);
               if (clientId == null || clientId.isEmpty()) {
                  MQTTLogger.LOGGER.sessionStateMessageBadClientId();
                  continue;
               }
               MQTTSessionState sessionState;
               try {
                  sessionState = new MQTTSessionState((CoreMessage) message);
               } catch (Exception e) {
                  MQTTLogger.LOGGER.errorDeserializingStateMessage(e);
                  continue;
               }
               sessionStates.put(clientId, sessionState);
            }
         } catch (NoSuchElementException ignored) {
            // this could happen through paging browsing
         }
      } else {
         this.sessionStore = null;
      }
   }

   public void scanSessions() {
      List<String> toRemove = new ArrayList<>();
      for (Map.Entry<String, MQTTSessionState> entry : sessionStates.entrySet()) {
         MQTTSessionState state = entry.getValue();
         logger.debug("Inspecting session: {}", state);
         int sessionExpiryInterval = state.getClientSessionExpiryInterval();
         if (!state.isAttached() && (sessionExpiryInterval == 0 || (sessionExpiryInterval > 0 && state.getDisconnectedTime() + (sessionExpiryInterval * 1000) < System.currentTimeMillis()))) {
            toRemove.add(entry.getKey());
         }
         if (state.isWill() && !state.isAttached() && state.isFailed() && state.getWillDelayInterval() > 0 && state.getDisconnectedTime() + (state.getWillDelayInterval() * 1000) < System.currentTimeMillis()) {
            state.getSession().sendWillMessage();
         }
      }

      for (String key : toRemove) {
         logger.info("Removing expired session: {}", key);
         try {
            MQTTSessionState state = removeSessionState(key);
            if (state != null) {
               if (state.isWill() && !state.isAttached() && state.isFailed()) {
                  state.getSession().sendWillMessage();
               }
               MQTTSession session = state.getSession();
               if (session != null) {
                  session.clean(false);
               } else {
                  // if the in-memory session doesn't exist, then we need to ensure that any other state is cleaned up
                  for (MqttTopicSubscription mqttTopicSubscription : state.getSubscriptions()) {
                     MQTTSubscriptionManager.cleanSubscriptionQueue(mqttTopicSubscription.topicFilter(), state.getClientId(), server, (q) -> server.destroyQueue(q, null, true, false, true));
                  }
                  Queue qos2ManagementQueue = server.locateQueue(MQTTPublishManager.getQoS2ManagementAddressName(SimpleString.of(state.getClientId())));
                  if (qos2ManagementQueue != null) {
                     qos2ManagementQueue.deleteQueue();
                  }
               }
            }
         } catch (Exception e) {
            MQTTLogger.LOGGER.failedToRemoveSessionState(key, e);
         }
      }
   }

   public MQTTSessionState getSessionState(String clientId) throws Exception {
      // [MQTT-3.1.2-4] Attach an existing session if one exists otherwise create a new one.
      if (sessionStates.containsKey(clientId)) {
         return sessionStates.get(clientId);
      } else {
         MQTTSessionState sessionState = new MQTTSessionState(clientId);
         logger.debug("Adding MQTT session state for: {}", clientId);
         sessionStates.put(clientId, sessionState);
         return sessionState;
      }
   }

   public MQTTSessionState removeSessionState(String clientId) throws Exception {
      logger.debug("Removing MQTT session state for: {}", clientId);
      if (clientId == null) {
         return null;
      }
      MQTTSessionState removed = sessionStates.remove(clientId);
      if (removed != null && removed.getSubscriptions().size() > 0) {
         removeDurableSessionState(clientId);
      }
      return removed;
   }

   public void removeDurableSessionState(String clientId) throws Exception {
      if (subscriptionPersistenceEnabled) {
         int deletedCount = sessionStore.deleteMatchingReferences(FilterImpl.createFilter(new StringBuilder(Message.HDR_LAST_VALUE_NAME).append(" = '").append(clientId).append("'").toString()));
         logger.debug("Removed {} durable MQTT session record(s) for: {}", deletedCount, clientId);
      }
   }

   public Map<String, MQTTSessionState> getSessionStates() {
      return new HashMap<>(sessionStates);
   }

   @Override
   public String toString() {
      return "MQTTSessionStateManager@" + Integer.toHexString(System.identityHashCode(this));
   }

   public void storeDurableSessionState(MQTTSessionState state) throws Exception {
      if (subscriptionPersistenceEnabled) {
         logger.debug("Adding durable MQTT session record for: {}", state.getClientId());
         StorageManager storageManager = server.getStorageManager();
         MQTTUtil.sendMessageDirectlyToQueue(storageManager, server.getPostOffice(), serializeState(state, storageManager.generateID()), sessionStore, null);
      }
   }

   public long getDurableSessionStateCount() {
      if (subscriptionPersistenceEnabled) {
         return sessionStore.getMessageCount();
      } else {
         return 0;
      }
   }

   public static CoreMessage serializeState(MQTTSessionState state, long messageID) {
      CoreMessage message = new CoreMessage().initBuffer(50).setMessageID(messageID);
      message.setAddress(MQTTUtil.MQTT_SESSION_STORE);
      message.setDurable(true);
      message.putStringProperty(Message.HDR_LAST_VALUE_NAME, state.getClientId());
      Map<String, MQTTSessionState.SubscriptionItem> subscriptions = state.getSubscriptionsPlusID();
      ActiveMQBuffer buf = message.getBodyBuffer();

      /*
       * This byte represents the "version". If the payload changes at any point in the future then we can detect that
       * and adjust so that when users are upgrading we can still read the old data format.
       */
      buf.writeByte((byte) 0);

      buf.writeInt(subscriptions.size());
      logger.debug("Serializing {} subscriptions", subscriptions.size());
      for (MQTTSessionState.SubscriptionItem item : subscriptions.values()) {
         MqttTopicSubscription sub = item.getSubscription();
         buf.writeString(sub.topicFilter());
         buf.writeInt(sub.option().qos().value());
         buf.writeBoolean(sub.option().isNoLocal());
         buf.writeBoolean(sub.option().isRetainAsPublished());
         buf.writeInt(sub.option().retainHandling().value());
         buf.writeNullableInt(item.getId());
      }

      buf.writeNullableInt(state.getClientSessionExpiryInterval());

      return message;
   }

   public boolean isClientConnected(String clientId, MQTTConnection connection) {
      MQTTConnection connectedConn = connectedClients.get(clientId);

      if (connectedConn != null) {
         return connectedConn.equals(connection);
      }

      return false;
   }

   public boolean isClientConnected(String clientId) {
      return connectedClients.containsKey(clientId);
   }

   public void removeConnectedClient(String clientId) {
      connectedClients.remove(clientId);
   }

   /**
    * {@return the {@code MQTTConnection} that the added connection replaced or null if there was no previous entry for
    * the {@code clientId}}
    */
   public MQTTConnection addConnectedClient(String clientId, MQTTConnection connection) {
      return connectedClients.put(clientId, connection);
   }

   public MQTTConnection getConnectedClient(String clientId) {
      return connectedClients.get(clientId);
   }

   /**
    * For DEBUG only
    */
   public Map<String, MQTTConnection> getConnectedClients() {
      return connectedClients;
   }
}