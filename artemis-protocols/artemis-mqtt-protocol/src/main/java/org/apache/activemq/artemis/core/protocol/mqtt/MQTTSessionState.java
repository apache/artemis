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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttSubscriptionOption;
import io.netty.handler.codec.mqtt.MqttTopicSubscription;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
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

   private final ConcurrentMap<String, SubscriptionItem> subscriptionItems = new ConcurrentHashMap<>();

   /**
    * Records packet IDs of inbound QoS2 PUBLISH messages (client &rarr; broker). The ID is added when the broker
    * receives the PUBLISH and removed when the client completes the handshake with PUBREL, preventing duplicate
    * processing of the same message.
    */
   protected PacketIdCache publishCache;

   /**
    * Records packet IDs of outbound QoS2 messages (broker &rarr; client) that have reached the PUBREC stage. The
    * ID is added when the broker receives PUBREC and removed when the client sends PUBCOMP, ensuring the broker can
    * resume the handshake after a reconnect.
    */
   protected PacketIdCache pubRecCache;

   private boolean attached = false;

   private long disconnectedTime = 0;

   private PacketIdGenerator packetIdGenerator;

   private int clientSessionExpiryInterval;

   private boolean isWill = false;

   private ByteBuf willMessage;

   private String willTopic;

   private int willQoSLevel;

   private boolean willRetain = false;

   private long willDelayInterval = 0;

   private MqttProperties willPublishProperties = MqttProperties.NO_PROPERTIES;

   private WillStatus willStatus = WillStatus.NOT_SENT;

   private boolean failed = false;

   private int clientMaxPacketSize = 0;

   private Map<Integer, String> clientTopicAliases;

   private Integer clientTopicAliasMaximum;

   private Map<String, Integer> serverTopicAliases;

   private Map<Integer, CoreDeliveryInfo> coreDeliveryInfos;

   private static final AtomicIntegerFieldUpdater<MQTTSessionState> SEND_QUOTA_UPDATER = AtomicIntegerFieldUpdater.newUpdater(MQTTSessionState.class, "sendQuota");
   private volatile int sendQuota = 0;

   public MQTTSessionState(String clientId) {
      this.clientId = clientId;
   }

   /**
    * This constructor deserializes subscription data from a message. The format is as follows.
    * <ul>
    * <li>byte: version
    * <li>int: subscription count
    * </ul>
    *  There may be 0 or more subscriptions. The subscription format is as follows.
    * <ul>
    * <li>String: topic name
    * <li>int: QoS
    * <li>boolean: no-local
    * <li>boolean: retain as published
    * <li>int: retain handling
    * <li>int (nullable): subscription identifier
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

         subscriptionItems.put(topicName, SubscriptionItem.of(new MqttTopicSubscription(topicName, new MqttSubscriptionOption(qos, nolocal, retainAsPublished, retainedHandlingPolicy)), subscriptionId));
      }

      if (buf.readable()) {
         clientSessionExpiryInterval = buf.readInt();
         disconnectedTime = System.currentTimeMillis();
      }
   }

   public MQTTSession getSession() {
      return session;
   }

   public void setSession(MQTTSession session) {
      this.session = session;
   }

   public synchronized void clear() throws Exception {
      subscriptionItems.clear();
      if (publishCache != null) {
         publishCache.clear();
      }
      if (pubRecCache != null) {
         pubRecCache.clear();
      }
      if (packetIdGenerator != null) {
         packetIdGenerator.clear();
      }
      disconnectedTime = 0;
      if (willMessage != null) {
         willMessage.clear();
         willMessage = null;
      }
      willStatus = WillStatus.NOT_SENT;
      failed = false;
      willDelayInterval = 0;
      willPublishProperties = MqttProperties.NO_PROPERTIES;
      willRetain = false;
      willTopic = null;
      clientMaxPacketSize = 0;
      clearTopicAliases();
      clientTopicAliasMaximum = 0;
   }

   public int generatePacketId() {
      if (packetIdGenerator == null) {
         packetIdGenerator = new PacketIdGenerator();
      }
      int result = packetIdGenerator.generatePacketId();
      return result;
   }

   public boolean isAttached() {
      return attached;
   }

   public void setAttached(boolean attached) {
      this.attached = attached;
   }

   public Map<String, SubscriptionItem> getSubscriptionsPlusID() {
      return new HashMap<>(subscriptionItems);
   }

   public Collection<SubscriptionItem> getSubscriptionItems() {
      return new HashSet(subscriptionItems.values());
   }

   public void addSubscription(SubscriptionItem item) {
      subscriptionItems.put(item.getSubscription().topicFilter(), item);
   }

   public void removeSubscription(String topicFilter) throws Exception {
      subscriptionItems.remove(topicFilter);
   }

   public SubscriptionItem getSubscriptionItem(String topicFilter) {
      return subscriptionItems.get(topicFilter);
   }

   public List<Integer> getMatchingSubscriptionIdentifiers(String address) {
      String topic = MQTTUtil.getMqttTopicFromCoreAddress(address, session.getServer().getConfiguration().getWildcardConfiguration());
      Address topicToMatch = new AddressImpl(SimpleString.of(topic), MQTTUtil.MQTT_WILDCARD);
      List<Integer> result = null;
      for (SubscriptionItem item : subscriptionItems.values()) {
         Integer matchingId = item.getMatchingId(topicToMatch);
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

   public void setClientSessionExpiryInterval(int sessionExpiryInterval) {
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

   public void setWillPublishProperties(MqttProperties willPublishProperties) {
      this.willPublishProperties = willPublishProperties;
   }

   public MqttProperties getWillPublishProperties() {
      return willPublishProperties;
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

   public CoreDeliveryInfo getCoreDeliveryInfo(Integer packetId) {
      return coreDeliveryInfos == null ? null : coreDeliveryInfos.get(packetId);
   }

   public void putCoreDeliveryInfo(Integer packetId, CoreDeliveryInfo coreDeliveryInfo) {
      if (coreDeliveryInfos == null) {
         coreDeliveryInfos = new ConcurrentHashMap<>();
      }
      coreDeliveryInfos.put(packetId, coreDeliveryInfo);
   }

   public CoreDeliveryInfo removeCoreDeliveryInfo(Integer packetId) {
      if (coreDeliveryInfos != null) {
         return coreDeliveryInfos.remove(packetId);
      } else {
         return null;
      }
   }

   public boolean coreDeliveryInfoExists(Integer packetId) {
      return coreDeliveryInfos == null ? false : coreDeliveryInfos.containsKey(packetId);
   }

   public void clearCoreDeliveryInfo() {
      if (coreDeliveryInfos != null) {
         coreDeliveryInfos.clear();
      }
   }

   @Override
   public String toString() {
      return "MQTTSessionState[session=" + session +
         ", clientId=" + clientId +
         ", subscriptionItems=" + subscriptionItems +
         ", publishCache=" + publishCache +
         ", pubRecCache=" + pubRecCache +
         ", attached=" + attached +
         ", packetIdGenerator=" + packetIdGenerator +
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

   public PacketIdCache getPublishCache() {
      Objects.requireNonNull(session, "session is null");
      if (publishCache == null) {
         publishCache = new PacketIdCache(session, PacketIdCache.TYPE.PUBLISH);
      }
      return publishCache;
   }

   public PacketIdCache getPubRecCache() {
      Objects.requireNonNull(session, "session is null");
      if (pubRecCache == null) {
         pubRecCache = new PacketIdCache(session, PacketIdCache.TYPE.PUBREC);
      }
      return pubRecCache;
   }

   public int getSendQuota() {
      return sendQuota;
   }

   public void incrementSendQuota() {
      SEND_QUOTA_UPDATER.incrementAndGet(this);
   }

   public void decrementSendQuota() {
      SEND_QUOTA_UPDATER.decrementAndGet(this);
   }

   public void resetSendQuota() {
      SEND_QUOTA_UPDATER.set(this, 0);
   }

   private class PacketIdGenerator {
      private static final int INITIAL_ID = 0;

      private int currentId = INITIAL_ID;

      private int generatePacketId() {
         final int start = currentId;
         do {
            // wrap around to the start if we reach the max
            if (++currentId > MQTTUtil.TWO_BYTE_INT_MAX) {
               currentId = INITIAL_ID;
            }
            // check to see if we looped all the way back around to where we started
            if (start == currentId) {
               // this detects an edge case where the same ID is acked & then generated again
               if (currentId != INITIAL_ID && !packetIdInUse(currentId)) {
                  break;
               }
               throw MQTTBundle.BUNDLE.unableToGenerateID();
            }
         }
         while (packetIdInUse(currentId) || currentId == INITIAL_ID);
         return currentId;
      }

      /**
       * Checks to see if the packet ID is in use already for either QoS 1 or QoS 2
       */
      private boolean packetIdInUse(int packetId) {
         // coreDeliveryInfoExists is redundant but is an O(1) short-circuit for the O(n) containsValue in packetIdCorrelationExists
         return coreDeliveryInfoExists(packetId) ||
            (session != null &&
               session.getStateManager() != null &&
               session.getStateManager().packetIdCorrelationExistsForClient(clientId) &&
               session.getStateManager().packetIdCorrelationExists(clientId, packetId)) ||
            (pubRecCache != null &&
               pubRecCache.contains(packetId));
      }

      private void clear() {
         currentId = INITIAL_ID;
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
}
