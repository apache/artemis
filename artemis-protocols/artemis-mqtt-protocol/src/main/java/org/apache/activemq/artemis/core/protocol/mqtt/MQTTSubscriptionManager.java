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
package org.apache.activemq.artemis.core.protocol.mqtt;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.netty.handler.codec.mqtt.MqttSubscriptionOption;
import io.netty.handler.codec.mqtt.MqttTopicSubscription;
import org.apache.activemq.artemis.api.core.ActiveMQQueueExistsException;
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException;
import org.apache.activemq.artemis.api.core.FilterConstants;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.ActiveMQMessageBundle;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.BindingQueryResult;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.utils.CompositeAddress;

import static org.apache.activemq.artemis.core.protocol.mqtt.MQTTUtil.DOLLAR;
import static org.apache.activemq.artemis.reader.MessageUtil.CONNECTION_ID_PROPERTY_NAME_STRING;

public class MQTTSubscriptionManager {

   private final MQTTSession session;

   private final MQTTStateManager stateManager;

   private final ConcurrentMap<Long, Integer> consumerQoSLevels;

   private final ConcurrentMap<String, ServerConsumer> consumers;

   // We filter out certain messages (e.g. management messages, notifications)
   private final SimpleString messageFilter;

   /*
    * We can also filter out messages from any address starting with '$'. This is because MQTT clients can do silly
    * things like subscribe to '#' which matches ever address on the broker.
    */
   private final SimpleString messageFilterNoDollar;

   private final char singleWord;

   private final char anyWords;

   public MQTTSubscriptionManager(MQTTSession session, MQTTStateManager stateManager) {
      this.session = session;
      this.stateManager = stateManager;

      singleWord = session.getServer().getConfiguration().getWildcardConfiguration().getSingleWord();
      anyWords = session.getServer().getConfiguration().getWildcardConfiguration().getAnyWords();

      consumers = new ConcurrentHashMap<>();
      consumerQoSLevels = new ConcurrentHashMap<>();

      // Create filter string to ignore certain messages
      StringBuilder baseFilter = new StringBuilder();
      baseFilter.append("NOT (");
      baseFilter.append("(").append(FilterConstants.ACTIVEMQ_ADDRESS).append(" = '").append(session.getServer().getConfiguration().getManagementAddress()).append("')");
      baseFilter.append(" OR ");
      baseFilter.append("(").append(FilterConstants.ACTIVEMQ_ADDRESS).append(" = '").append(session.getServer().getConfiguration().getManagementNotificationAddress()).append("')");

      StringBuilder messageFilter = new StringBuilder(baseFilter);
      messageFilter.append(")");
      this.messageFilter = SimpleString.of(messageFilter.toString());

      // [MQTT-4.7.2-1]
      StringBuilder messageFilterNoDollar = new StringBuilder(baseFilter);
      messageFilterNoDollar.append(" OR ");
      messageFilterNoDollar.append("(").append(FilterConstants.ACTIVEMQ_ADDRESS).append(" LIKE '").append(DOLLAR).append("%')");
      messageFilterNoDollar.append(")");
      this.messageFilterNoDollar = SimpleString.of(messageFilterNoDollar.toString());
   }

   synchronized void start() throws Exception {
      for (MqttTopicSubscription subscription : session.getState().getSubscriptions()) {
         addSubscription(subscription, null, true);
      }
   }

   private void addSubscription(MqttTopicSubscription subscription, Integer subscriptionIdentifier, boolean initialStart) throws Exception {
      String rawTopicName = CompositeAddress.extractAddressName(subscription.topicFilter());
      String parsedTopicName = MQTTUtil.decomposeSharedSubscriptionTopicFilter(rawTopicName).getB();
      boolean isFullyQualified = CompositeAddress.isFullyQualified(subscription.topicFilter());

      Queue q = createQueueForSubscription(rawTopicName, parsedTopicName, isFullyQualified);

      int qos = subscription.qualityOfService().value();

      try {
         if (initialStart) {
            createConsumerForSubscriptionQueue(q, parsedTopicName, qos, subscription.option().isNoLocal(), null);
         } else {
            MqttTopicSubscription existingSubscription = session.getState().getSubscription(parsedTopicName);
            if (existingSubscription == null) {
               createConsumerForSubscriptionQueue(q, parsedTopicName, qos, subscription.option().isNoLocal(), null);
            } else {
               Long existingConsumerId = consumers.get(parsedTopicName).getID();
               consumerQoSLevels.put(existingConsumerId, qos);
               if (existingSubscription.option().isNoLocal() != subscription.option().isNoLocal()) {
                  createConsumerForSubscriptionQueue(q, parsedTopicName, qos, subscription.option().isNoLocal(), existingConsumerId);
               }
            }

            if (subscription.option().retainHandling() == MqttSubscriptionOption.RetainedHandlingPolicy.SEND_AT_SUBSCRIBE || (subscription.option().retainHandling() == MqttSubscriptionOption.RetainedHandlingPolicy.SEND_AT_SUBSCRIBE_IF_NOT_YET_EXISTS && existingSubscription == null)) {
               session.getRetainMessageManager().addRetainedMessagesToQueue(q, parsedTopicName);
            }

            session.getState().addSubscription(subscription, session.getWildcardConfiguration(), subscriptionIdentifier);
         }
      } catch (Exception e) {
         // if anything broke during the creation of the consumer (or otherwise) then ensure the subscription queue
         q.deleteQueue();
         throw e;
      }
   }

   synchronized void stop() throws Exception {
      for (ServerConsumer consumer : consumers.values()) {
         consumer.setStarted(false);
         consumer.disconnect();
         consumer.getQueue().removeConsumer(consumer);
         consumer.close(false);
      }
   }

   private Queue createQueueForSubscription(String rawTopicName, String parsedTopicName, boolean isFullyQualified) throws Exception {
      String coreAddress = MQTTUtil.getCoreAddressFromMqttTopic(parsedTopicName, session.getWildcardConfiguration());
      String coreQueue = MQTTUtil.getCoreQueueFromMqttTopic(rawTopicName, session.getState().getClientId(), session.getWildcardConfiguration());

      // check to see if a subscription queue already exists
      Queue q = session.getServer().locateQueue(coreQueue);

      // the subscription queue does not exist so we need to create it
      if (q == null) {
         SimpleString sAddress = SimpleString.of(coreAddress);

         // only check if we can auto create queues if it's FQQN
         BindingQueryResult bindingQueryResult = session.getServerSession().executeBindingQuery(sAddress);
         if (isFullyQualified && !bindingQueryResult.isAutoCreateQueues()) {
            throw ActiveMQMessageBundle.BUNDLE.noSuchQueue(sAddress);
         }

         // check that the address exists, if not we try to auto create it (if allowed).
         AddressInfo addressInfo = session.getServerSession().getAddress(sAddress);
         if (addressInfo == null) {
            if (!bindingQueryResult.isAutoCreateAddresses()) {
               throw ActiveMQMessageBundle.BUNDLE.addressDoesNotExist(sAddress);
            }
            addressInfo = session.getServerSession().createAddress(sAddress, RoutingType.MULTICAST, true);
         }
         return findOrCreateQueue(bindingQueryResult, addressInfo, coreQueue);
      }
      return q;
   }

   private Queue findOrCreateQueue(BindingQueryResult bindingQueryResult, AddressInfo addressInfo, String queue) throws Exception {
      /*
       * MQTT 3.1 and 3.1.1 clients using a clean session should have a *non-durable* subscription queue. If the broker
       * restarts the queue should be removed. This is due to [MQTT-3.1.2-6] which states that the session (and any
       * state) must last only as long as the network connection.
       */
      boolean durable = session.getVersion() == MQTTVersion.MQTT_5 || (session.getVersion() != MQTTVersion.MQTT_5 && !session.isClean());
      if (addressInfo.getRoutingTypes().contains(RoutingType.MULTICAST)) {
         return session.getServerSession().createQueue(QueueConfiguration.of(queue).setAddress(addressInfo.getName()).setRoutingType(RoutingType.MULTICAST).setFilterString(getMessageFilter(addressInfo.getName())).setDurable(durable));
      }

      if (addressInfo.getRoutingTypes().contains(RoutingType.ANYCAST)) {
         if (!bindingQueryResult.getQueueNames().isEmpty()) {
            SimpleString name = null;
            for (SimpleString qName : bindingQueryResult.getQueueNames()) {
               if (name == null) {
                  name = qName;
               } else if (qName.equals(addressInfo.getName())) {
                  name = qName;
               }
            }
            return session.getServer().locateQueue(name);
         } else {
            try {
               return session.getServerSession().createQueue(QueueConfiguration.of(addressInfo.getName()).setRoutingType(RoutingType.ANYCAST).setFilterString(getMessageFilter(addressInfo.getName())).setDurable(durable));
            } catch (ActiveMQQueueExistsException e) {
               return session.getServer().locateQueue(addressInfo.getName());
            }
         }
      }

      throw ActiveMQMessageBundle.BUNDLE.invalidRoutingTypeForAddress(addressInfo.getRoutingType(), addressInfo.getName().toString(), EnumSet.allOf(RoutingType.class));
   }

   private SimpleString getMessageFilter(SimpleString addressName) {
      /*
       * By the time we get here wildcards in the MQTT topic filter have already been translated into their core
       * equivalents. This check is to enforce [MQTT-4.7.2-1].
       */
      if (addressName.startsWith(singleWord) || addressName.startsWith(anyWords)) {
         return messageFilterNoDollar;
      } else {
         return messageFilter;
      }
   }

   private void createConsumerForSubscriptionQueue(Queue queue, String topicFilter, int qos, boolean noLocal, Long existingConsumerId) throws Exception {
      long cid = Objects.requireNonNullElseGet(existingConsumerId, () -> session.getServer().getStorageManager().generateID());

      // for noLocal support we use the MQTT *client id* rather than the connection ID, but we still use the existing property name
      ServerConsumer consumer = session.getServerSession().createConsumer(cid, queue.getName(), noLocal ? SimpleString.of(CONNECTION_ID_PROPERTY_NAME_STRING + " <> '" + session.getState().getClientId() + "'") : null, false, false, -1);

      ServerConsumer existingConsumer = consumers.put(topicFilter, consumer);
      if (existingConsumer != null) {
         existingConsumer.setStarted(false);
         existingConsumer.close(false);
      }

      consumer.setStarted(true);

      consumerQoSLevels.put(cid, qos);
   }

   short[] removeSubscriptions(List<String> topics, boolean enforceSecurity) throws Exception {
      if (topics == null || topics.size() == 0) {
         return new short[0];
      }

      short[] reasonCodes;
      MQTTSessionState state = session.getState();

      synchronized (state) {
         reasonCodes = new short[topics.size()];
         for (int i = 0; i < topics.size(); i++) {
            if (state.getSubscription(topics.get(i)) == null) {
               reasonCodes[i] = MQTTReasonCodes.NO_SUBSCRIPTION_EXISTED;
               continue;
            }

            short reasonCode = MQTTReasonCodes.SUCCESS;

            try {
               state.removeSubscription(topics.get(i));
               ServerConsumer removed = consumers.remove(MQTTUtil.decomposeSharedSubscriptionTopicFilter(topics.get(i)).getB());
               if (removed != null) {
                  removed.close(false);
                  consumerQoSLevels.remove(removed.getID());
               }

               cleanSubscriptionQueue(topics.get(i), state.getClientId(), session.getServer(), (q) -> session.getServerSession().deleteQueue(q, enforceSecurity));
            } catch (Exception e) {
               MQTTLogger.LOGGER.errorRemovingSubscription(e);
               reasonCode = MQTTReasonCodes.UNSPECIFIED_ERROR;
            }

            reasonCodes[i] =  reasonCode;
         }

         // deal with durable state after *all* requested subscriptions have been removed in memory
         if (state.getSubscriptions().size() > 0) {
            // if there are some subscriptions left then update the state
            stateManager.storeDurableSessionState(state);
         } else {
            // if there are no subscriptions left then remove the state entirely
            stateManager.removeDurableSessionState(state.getClientId());
         }
      }

      return reasonCodes;
   }

   public static void cleanSubscriptionQueue(String topic, String clientId, ActiveMQServer server, SubscriptionQueueDeleter<SimpleString> deleter) throws Exception {
      SimpleString internalQueueName = SimpleString.of(MQTTUtil.getCoreQueueFromMqttTopic(topic, clientId, server.getConfiguration().getWildcardConfiguration()));
      Queue queue = server.locateQueue(internalQueueName);
      if (queue != null) {
         if (queue.isConfigurationManaged()) {
            queue.deleteAllReferences();
         } else if (!MQTTUtil.isSharedSubscription(topic) || (MQTTUtil.isSharedSubscription(topic) && queue.getConsumerCount() == 0)) {
            deleter.delete(internalQueueName);
         }
      }
   }

   /**
    * As per MQTT Spec. Subscribes this client to a number of MQTT topics.
    *
    * @return An array of integers representing the list of accepted QoS for each topic
    */
   int[] addSubscriptions(List<MqttTopicSubscription> subscriptions, Integer subscriptionIdentifier) throws Exception {
      MQTTSessionState state = session.getState();
      synchronized (state) {
         int[] qos = new int[subscriptions.size()];

         for (int i = 0; i < subscriptions.size(); i++) {
            try {
               addSubscription(subscriptions.get(i), subscriptionIdentifier, false);
               qos[i] = subscriptions.get(i).qualityOfService().value();
            } catch (ActiveMQSecurityException e) {
               // user is not authorized to create subscription
               if (session.getVersion() == MQTTVersion.MQTT_5) {
                  qos[i] = MQTTReasonCodes.NOT_AUTHORIZED;
               } else if (session.getVersion() == MQTTVersion.MQTT_3_1_1) {
                  qos[i] = MQTTReasonCodes.UNSPECIFIED_ERROR;
               } else {
                  /*
                   * For MQTT 3.1 clients:
                   *
                   * Note that if a server implementation does not authorize a SUBSCRIBE request to be made by a client,
                   * it has no way of informing that client. It must therefore make a positive acknowledgement with a
                   * SUBACK, and the client will not be informed that it was not authorized to subscribe.
                   *
                   *
                   * For MQTT 3.1.1 clients:
                   *
                   * The 3.1.1 spec doesn't directly address the situation where the server does not authorize a
                   * SUBSCRIBE. It really just says this:
                   *
                   * [MQTT-3.8.4-1] When the Server receives a SUBSCRIBE Packet from a Client, the Server MUST respond
                   *  with a SUBACK Packet.
                   */
                  qos[i] = subscriptions.get(i).qualityOfService().value();
               }
            }
         }

         // store state after *all* requested subscriptions have been created in memory
         stateManager.storeDurableSessionState(state);

         return qos;
      }
   }

   Map<Long, Integer> getConsumerQoSLevels() {
      return consumerQoSLevels;
   }

   void clean(boolean enforceSecurity) throws Exception {
      List<String> topics = new ArrayList<>();
      for (MqttTopicSubscription mqttTopicSubscription : session.getState().getSubscriptions()) {
         topics.add(mqttTopicSubscription.topicFilter());
      }
      removeSubscriptions(topics, enforceSecurity);
   }

   @FunctionalInterface
   public interface SubscriptionQueueDeleter<SimpleString> {
      void delete(SimpleString q) throws Exception;
   }
}
