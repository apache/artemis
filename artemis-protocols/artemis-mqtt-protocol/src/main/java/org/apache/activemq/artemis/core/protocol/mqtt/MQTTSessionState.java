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

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttSubscriptionOption;
import io.netty.handler.codec.mqtt.MqttTopicSubscription;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.postoffice.Address;
import org.apache.activemq.artemis.core.postoffice.impl.AddressImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MQTTSessionState {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final MQTTSessionState DEFAULT = new MQTTSessionState((String) null);

   private MQTTSession session;

   private final String clientId;

   private final ConcurrentMap<String, SubscriptionItem> subscriptions = new ConcurrentHashMap<>();

   // Used to store Packet ID of Publish QoS1 and QoS2 message.  See spec: 4.3.3 QoS 2: Exactly once delivery.  Method B.
   private final Map<Integer, MQTTMessageInfo> messageRefStore = new ConcurrentHashMap<>();

   private final ConcurrentMap<String, Map<Long, Integer>> addressMessageMap = new ConcurrentHashMap<>();

   private final Set<Integer> pubRec = new HashSet<>();

   private boolean attached = false;

   private long disconnectedTime = 0;

   private final OutboundStore outboundStore = new OutboundStore();

   private int clientSessionExpiryInterval;

   private boolean isWill = false;

   private ByteBuf willMessage;

   private String willTopic;

   private int willQoSLevel;

   private boolean willRetain = false;

   private long willDelayInterval = 0;

   private List<? extends MqttProperties.MqttProperty> willUserProperties;

   private WillStatus willStatus = WillStatus.NOT_SENT;

   private boolean failed = false;

   private int clientMaxPacketSize = 0;

   private Map<Integer, String> clientTopicAliases;

   private Integer clientTopicAliasMaximum;

   private Map<String, Integer> serverTopicAliases;

   public MQTTSessionState(String clientId) {
      this.clientId = clientId;
   }

   /**
    * This constructor deserializes subscription data from a message. The format is as follows.
    * <ul>
    * <li>byte: version
    * <li>int: subscription count
    * </ul>
    * There may be 0 or more subscriptions. The subscription format is as follows.
    * <ul>
    * <li>String: topic name
    * <li>int: QoS
    * <li>boolean: no-local
    * <li>boolean: retain as published
    * <li>int: retain handling
    * <li>int (nullable): subscription identifier
    * </ul>
    * After the subscriptions there is:
    * <ul>
    * <li>int (nullable): session expiry interval
    * </ul>
    *
    * @param message the message holding the MQTT session data
    */
   public MQTTSessionState(CoreMessage message) {
      logger.debug("Deserializing MQTT subscriptions from {}", message);
      this.clientId = message.getStringProperty(Message.HDR_LAST_VALUE_NAME);
      ActiveMQBuffer buf = message.getDataBuffer();

      // no need to use the version at this point
      byte version = buf.readByte();

      int subscriptionCount = buf.readInt();
      logger.debug("Deserializing {} subscriptions", subscriptionCount);
      for (int i = 0; i < subscriptionCount; i++) {
         String topicName = buf.readString();
         MqttQoS qos = MqttQoS.valueOf(buf.readInt());
         boolean nolocal = buf.readBoolean();
         boolean retainAsPublished = buf.readBoolean();
         MqttSubscriptionOption.RetainedHandlingPolicy retainedHandlingPolicy = MqttSubscriptionOption.RetainedHandlingPolicy.valueOf(buf.readInt());
         Integer subscriptionId = buf.readNullableInt();

         subscriptions.put(topicName, new SubscriptionItem(new MqttTopicSubscription(topicName, new MqttSubscriptionOption(qos, nolocal, retainAsPublished, retainedHandlingPolicy)), subscriptionId));
      }
      if (buf.readable()) {
         clientSessionExpiryInterval = buf.readNullableInt();
      } else {
         // this is for old records where we don't know the session expiry interval and can't risk removing a subscription illegitimately
         clientSessionExpiryInterval = session.getProtocolManager().getDefaultMqttSessionExpiryInterval();
      }
      disconnectedTime = System.currentTimeMillis();
   }

   // TODO: create a way to send a message in the old format in order to test upgrade functionality

   public MQTTSession getSession() {
      return session;
   }

   public void setSession(MQTTSession session) {
      this.session = session;
   }

   public synchronized void clear() throws Exception {
      subscriptions.clear();
      messageRefStore.clear();
      addressMessageMap.clear();
      pubRec.clear();
      outboundStore.clear();
      disconnectedTime = 0;
      if (willMessage != null) {
         willMessage.clear();
         willMessage = null;
      }
      willStatus = WillStatus.NOT_SENT;
      failed = false;
      willDelayInterval = 0;
      willRetain = false;
      willTopic = null;
      clientMaxPacketSize = 0;
      clearTopicAliases();
      clientTopicAliasMaximum = 0;
   }

   public OutboundStore getOutboundStore() {
      return outboundStore;
   }

   public Set<Integer> getPubRec() {
      return pubRec;
   }

   public boolean isAttached() {
      return attached;
   }

   public void setAttached(boolean attached) {
      this.attached = attached;
   }

   public Collection<MqttTopicSubscription> getSubscriptions() {
      Collection<MqttTopicSubscription> result = new HashSet<>();
      for (SubscriptionItem item : subscriptions.values()) {
         result.add(item.getSubscription());
      }
      return result;
   }

   public Map<String, SubscriptionItem> getSubscriptionsPlusID() {
      return new HashMap<>(subscriptions);
   }

   public boolean addSubscription(MqttTopicSubscription subscription, WildcardConfiguration wildcardConfiguration, Integer subscriptionIdentifier) throws Exception {
      // synchronized to prevent race with removeSubscription
      synchronized (subscriptions) {
         addressMessageMap.putIfAbsent(MQTTUtil.getCoreAddressFromMqttTopic(subscription.topicFilter(), wildcardConfiguration), new ConcurrentHashMap<>());

         SubscriptionItem existingSubscription = subscriptions.get(subscription.topicFilter());
         if (existingSubscription != null) {
            if (subscription.qualityOfService().value() > existingSubscription.getSubscription().qualityOfService().value()
               || !Objects.equals(subscriptionIdentifier, existingSubscription.getId())) {
               existingSubscription.update(subscription, subscriptionIdentifier);
               return true;
            } else {
               return false;
            }
         } else {
            subscriptions.put(subscription.topicFilter(), new SubscriptionItem(subscription, subscriptionIdentifier));
            return true;
         }
      }
   }

   public void removeSubscription(String address) throws Exception {
      // synchronized to prevent race with addSubscription
      synchronized (subscriptions) {
         subscriptions.remove(address);
         addressMessageMap.remove(address);
      }
   }

   public MqttTopicSubscription getSubscription(String address) {
      return subscriptions.get(address) != null ? subscriptions.get(address).getSubscription() : null;
   }

   public SubscriptionItem getSubscriptionPlusID(String address) {
      return subscriptions.get(address);
   }

   public List<Integer> getMatchingSubscriptionIdentifiers(String address) {
      String topic = MQTTUtil.getMqttTopicFromCoreAddress(address, session.getServer().getConfiguration().getWildcardConfiguration());
      List<Integer> result = null;
      for (SubscriptionItem item : subscriptions.values()) {
         Integer matchingId = item.getMatchingId(topic);
         if (matchingId != null) {
            if (result == null) {
               result = new ArrayList<>();
            }
            result.add(matchingId);
         }
      }
      return result;
   }

   public String getClientId() {
      return clientId;
   }

   public long getDisconnectedTime() {
      return disconnectedTime;
   }

   public void setDisconnectedTime(long disconnectedTime) {
      this.disconnectedTime = disconnectedTime;
   }

   public int getClientSessionExpiryInterval() {
      return clientSessionExpiryInterval;
   }

   public void setClientSessionExpiryInterval(int sessionExpiryInterval) throws Exception {
      if (session != null && (sessionExpiryInterval != 0 || this.clientSessionExpiryInterval != 0)) {
         session.getStateManager().storeDurableSessionState(this);
      }
      this.clientSessionExpiryInterval = sessionExpiryInterval;
   }

   public boolean isWill() {
      return isWill;
   }

   public void setWill(boolean will) {
      isWill = will;
   }

   public ByteBuf getWillMessage() {
      return willMessage;
   }

   public void setWillMessage(ByteBuf willMessage) {
      this.willMessage = willMessage;
   }

   public String getWillTopic() {
      return willTopic;
   }

   public void setWillTopic(String willTopic) {
      this.willTopic = willTopic;
   }

   public int getWillQoSLevel() {
      return willQoSLevel;
   }

   public void setWillQoSLevel(int willQoSLevel) {
      this.willQoSLevel = willQoSLevel;
   }

   public boolean isWillRetain() {
      return willRetain;
   }

   public void setWillRetain(boolean willRetain) {
      this.willRetain = willRetain;
   }

   public long getWillDelayInterval() {
      return willDelayInterval;
   }

   public void setWillDelayInterval(long willDelayInterval) {
      this.willDelayInterval = willDelayInterval;
   }

   public void setWillUserProperties(List<? extends MqttProperties.MqttProperty> userProperties) {
      this.willUserProperties = userProperties;
   }

   public List<? extends MqttProperties.MqttProperty> getWillUserProperties() {
      return willUserProperties;
   }

   public WillStatus getWillStatus() {
      return willStatus;
   }

   public void setWillStatus(WillStatus willStatus) {
      this.willStatus = willStatus;
   }

   public boolean isFailed() {
      return failed;
   }

   public void setFailed(boolean failed) {
      this.failed = failed;
   }

   public int getClientMaxPacketSize() {
      return clientMaxPacketSize;
   }

   public void setClientMaxPacketSize(int clientMaxPacketSize) {
      this.clientMaxPacketSize = clientMaxPacketSize;
   }

   public void putClientTopicAlias(Integer alias, String topicName) {
      if (clientTopicAliases == null) {
         clientTopicAliases = new HashMap<>();
      }
      clientTopicAliases.put(alias, topicName);
   }

   public String getClientTopicAlias(Integer alias) {
      String result;

      if (clientTopicAliases == null) {
         result = null;
      } else {
         result = clientTopicAliases.get(alias);
      }

      return result;
   }

   public Integer getClientTopicAliasMaximum() {
      return clientTopicAliasMaximum;
   }

   public void setClientTopicAliasMaximum(Integer clientTopicAliasMaximum) {
      this.clientTopicAliasMaximum = clientTopicAliasMaximum;
   }

   public Integer addServerTopicAlias(String topicName) {
      if (serverTopicAliases == null) {
         serverTopicAliases = new ConcurrentHashMap<>();
      }
      Integer alias = serverTopicAliases.size() + 1;
      if (alias <= clientTopicAliasMaximum) {
         serverTopicAliases.put(topicName, alias);
         return alias;
      } else {
         return null;
      }
   }

   public Integer getServerTopicAlias(String topicName) {
      return serverTopicAliases == null ? null : serverTopicAliases.get(topicName);
   }

   void removeMessageRef(Integer mqttId) {
      MQTTMessageInfo info = messageRefStore.remove(mqttId);
      if (info != null) {
         Map<Long, Integer> addressMap = addressMessageMap.get(info.getAddress());
         if (addressMap != null) {
            addressMap.remove(info.getServerMessageId());
         }
      }
   }

   public void clearTopicAliases() {
      if (clientTopicAliases != null) {
         clientTopicAliases.clear();
         clientTopicAliases = null;
      }
      if (serverTopicAliases != null) {
         serverTopicAliases.clear();
         serverTopicAliases = null;
      }
   }

   @Override
   public String toString() {
      return "MQTTSessionState[session=" + session +
         ", clientId=" + clientId +
         ", subscriptions=" + subscriptions +
         ", messageRefStore=" + messageRefStore +
         ", addressMessageMap=" + addressMessageMap +
         ", pubRec=" + pubRec +
         ", attached=" + attached +
         ", outboundStore=" + outboundStore +
         ", disconnectedTime=" + disconnectedTime +
         ", sessionExpiryInterval=" + clientSessionExpiryInterval +
         ", isWill=" + isWill +
         ", willMessage=" + willMessage +
         ", willTopic=" + willTopic +
         ", willQoSLevel=" + willQoSLevel +
         ", willRetain=" + willRetain +
         ", willDelayInterval=" + willDelayInterval +
         ", failed=" + failed +
         ", maxPacketSize=" + clientMaxPacketSize +
         "]@" + System.identityHashCode(this);
   }

   public static class OutboundStore {
      private final Map<Pair<Long, Long>, Integer> artemisToMqttMessageMap = new HashMap<>();

      private final Map<Integer, Pair<Long, Long>> mqttToServerIds = new HashMap<>();

      private final Object dataStoreLock = new Object();

      private static final int INITIAL_ID = 0;

      private int currentId = INITIAL_ID;

      // track send quota independently because it's reset when the client disconnects, but other state must remain in tact
      private int sendQuota = 0;

      private Pair<Long, Long> generateKey(long messageId, long consumerID) {
         return new Pair<>(messageId, consumerID);
      }

      public int generateMqttId(long messageId, long consumerId) {
         synchronized (dataStoreLock) {
            Integer id = artemisToMqttMessageMap.get(generateKey(messageId, consumerId));
            if (id == null) {
               final int start = currentId;
               do {
                  // wrap around to the start if we reach the max
                  if (++currentId > MQTTUtil.TWO_BYTE_INT_MAX) {
                     currentId = INITIAL_ID;
                  }
                  // check to see if we looped all the way back around to where we started
                  if (start == currentId) {
                     // this detects an edge case where the same ID is acked & then generated again
                     if (currentId != INITIAL_ID && !mqttToServerIds.containsKey(currentId)) {
                        break;
                     }
                     throw MQTTBundle.BUNDLE.unableToGenerateID();
                  }
               }
               while (mqttToServerIds.containsKey(currentId) || currentId == INITIAL_ID);
               id = currentId;
            }
            return id;
         }
      }

      public void publish(int mqtt, long messageId, long consumerId) {
         synchronized (dataStoreLock) {
            Pair<Long, Long> key = generateKey(messageId, consumerId);
            artemisToMqttMessageMap.put(key, mqtt);
            mqttToServerIds.put(mqtt, key);
            sendQuota++;
         }
      }

      public Pair<Long, Long> publishAckd(int mqtt) {
         synchronized (dataStoreLock) {
            Pair<Long, Long> p = mqttToServerIds.remove(mqtt);
            if (p != null) {
               sendQuota--;
               artemisToMqttMessageMap.remove(p);
            }
            return p;
         }
      }

      public Pair<Long, Long> publishReceived(int mqtt) {
         return publishAckd(mqtt);
      }

      public void publishReleasedSent(int mqttId, long serverMessageId) {
         synchronized (dataStoreLock) {
            mqttToServerIds.put(mqttId, new Pair<>(serverMessageId, 0L));
            sendQuota++;
         }
      }

      public Pair<Long, Long> publishComplete(int mqtt) {
         return publishAckd(mqtt);
      }

      public void clear() {
         synchronized (dataStoreLock) {
            artemisToMqttMessageMap.clear();
            mqttToServerIds.clear();
            currentId = INITIAL_ID;
            sendQuota = 0;
         }
      }

      public int getSendQuota() {
         synchronized (dataStoreLock) {
            return sendQuota;
         }
      }

      public void resetSendQuota() {
         synchronized (dataStoreLock) {
            sendQuota = 0;
         }
      }
   }

   public enum WillStatus {
      NOT_SENT, SENT, SENDING;

      public byte getStatus() {
         return switch (this) {
            case NOT_SENT -> 0;
            case SENT -> 1;
            case SENDING -> 2;
            default -> -1;
         };
      }

      public static WillStatus getStatus(byte status) {
         return switch (status) {
            case 0 -> NOT_SENT;
            case 1 -> SENT;
            case 2 -> SENDING;
            default -> null;
         };
      }
   }

   public static class SubscriptionItem {
      private MqttTopicSubscription subscription;
      private Integer id;
      private Address address;

      public SubscriptionItem(MqttTopicSubscription subscription, Integer id) {
         update(subscription, id);
      }

      public MqttTopicSubscription getSubscription() {
         return subscription;
      }

      public Integer getId() {
         return id;
      }

      public Integer getMatchingId(String topic) {
         if (id != null && new AddressImpl(SimpleString.of(topic), MQTTUtil.MQTT_WILDCARD).matches(address)) {
            return id;
         } else {
            return null;
         }
      }

      private void update(MqttTopicSubscription newSub, Integer newId) {
         if (newId != null && !newId.equals(id)) {
            if (this.address == null || !subscription.topicFilter().equals(newSub.topicFilter())) {
               String topicFilter = newSub.topicFilter();
               if (MQTTUtil.isSharedSubscription(topicFilter)) {
                  topicFilter = MQTTUtil.decomposeSharedSubscriptionTopicFilter(newSub.topicFilter()).getB();
               }
               address = new AddressImpl(SimpleString.of(topicFilter), MQTTUtil.MQTT_WILDCARD);
            }
         }
         subscription = newSub;
         id = newId;
      }
   }
}
