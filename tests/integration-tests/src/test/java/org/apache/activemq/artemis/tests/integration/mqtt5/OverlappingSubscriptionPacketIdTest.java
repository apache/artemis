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
package org.apache.activemq.artemis.tests.integration.mqtt5;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTInterceptor;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptionsBuilder;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OverlappingSubscriptionPacketIdTest extends MQTT5TestSupport {

   private static final long DEFAULT_TIMEOUT_SEC = 30;

   private static final String TOPIC = "a/b";
   private static final String OVERLAPPING_FILTER = "a/#";
   private static final String SUBSCRIBER_CLIENT_ID = "overlapping-subscriber";
   private static final String PUBLISHER_CLIENT_ID = "overlapping-publisher";

   /**
    * Two overlapping subscriptions receiving the same core message must get two distinct packet IDs and two distinct
    * persisted correlations.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testOverlappingSubscriptionsGetDistinctCorrelations() throws Exception {
      final Set<Integer> publishedPacketIds = ConcurrentHashMap.newKeySet();
      final CountDownLatch publishLatch = new CountDownLatch(2);

      // hold both deliveries in flight by dropping the subscriber's PUBREC before the broker can process it
      server.getRemotingService().addIncomingInterceptor(blockPubRec());
      server.getRemotingService().addOutgoingInterceptor(collectPublishes(publishedPacketIds, publishLatch, false));

      MqttClient subscriber = connectSubscriber();
      publishOneMessage();

      assertTrue(publishLatch.await(DEFAULT_TIMEOUT_SEC, TimeUnit.SECONDS), "Expected the message on both overlapping subscriptions");
      assertEquals(2, publishedPacketIds.size(), "Overlapping subscriptions must each get their own packet ID, but got " + publishedPacketIds);

      Wait.assertEquals(2, () -> getProtocolManager().getStateManager().getPacketIdCorrelationSize(SUBSCRIBER_CLIENT_ID), 2000, 25);
      for (int packetId : publishedPacketIds) {
         assertTrue(getProtocolManager().getStateManager().packetIdCorrelationExists(SUBSCRIBER_CLIENT_ID, packetId), "No correlation persisted for packet ID " + packetId);
      }

      // let the flow finish and confirm both correlations are released
      server.getRemotingService().clearInterceptors();
      reconnectSafely(subscriber);
      assertBothDeliveriesSettled();
      disconnectSafely(subscriber);
      subscriber.close();
   }

   /**
    * The persisted correlations exist so that a redelivery reuses the packet ID the client was originally given. With
    * overlapping subscriptions that has to hold for every delivery, not just the last one to be stored.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testOverlappingSubscriptionsReusePacketIdsAfterRestart() throws Exception {
      final Set<Integer> originalPacketIds = ConcurrentHashMap.newKeySet();
      final CountDownLatch publishLatch = new CountDownLatch(2);

      server.getRemotingService().addIncomingInterceptor(blockPubRec());
      server.getRemotingService().addOutgoingInterceptor(collectPublishes(originalPacketIds, publishLatch, false));

      MqttClient subscriber = connectSubscriber();
      publishOneMessage();

      assertTrue(publishLatch.await(DEFAULT_TIMEOUT_SEC, TimeUnit.SECONDS), "Expected the message on both overlapping subscriptions");
      assertEquals(2, originalPacketIds.size(), "Overlapping subscriptions must each get their own packet ID, but got " + originalPacketIds);

      Wait.assertEquals(2, () -> getProtocolManager().getStateManager().getPacketIdCorrelationSize(SUBSCRIBER_CLIENT_ID), 2000, 25);

      server.getRemotingService().clearInterceptors();
      server.stop();
      waitForServerToStop(server);
      server.start();
      waitForServerToStart(server);

      // both correlations must have survived the restart
      assertEquals(2, getProtocolManager().getStateManager().getPacketIdCorrelationSize(SUBSCRIBER_CLIENT_ID));

      final Set<Integer> redeliveredPacketIds = ConcurrentHashMap.newKeySet();
      final CountDownLatch redeliveryLatch = new CountDownLatch(2);
      server.getRemotingService().addOutgoingInterceptor(collectPublishes(redeliveredPacketIds, redeliveryLatch, true));

      reconnectSafely(subscriber);

      assertTrue(redeliveryLatch.await(DEFAULT_TIMEOUT_SEC, TimeUnit.SECONDS), "Expected both deliveries to be redelivered, got " + redeliveredPacketIds);
      assertEquals(originalPacketIds, redeliveredPacketIds, "Each overlapping subscription must be redelivered with the packet ID it was originally assigned");

      assertBothDeliveriesSettled();
      disconnectSafely(subscriber);
      subscriber.close();
   }

   private MqttClient connectSubscriber() throws Exception {
      MqttClient subscriber = createPahoClient(SUBSCRIBER_CLIENT_ID);
      subscriber.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) {
            logger.debug("messageArrived({}, {})", topic, message);
         }
      });
      MqttConnectionOptions options = new MqttConnectionOptionsBuilder()
         .cleanStart(false)
         .sessionExpiryInterval(300L)
         .build();
      subscriber.connect(options);
      subscriber.subscribe(TOPIC, EXACTLY_ONCE);
      subscriber.subscribe(OVERLAPPING_FILTER, EXACTLY_ONCE);
      return subscriber;
   }

   private void publishOneMessage() throws Exception {
      MqttClient publisher = createPahoClient(PUBLISHER_CLIENT_ID);
      publisher.connect();
      publisher.publish(TOPIC, RandomUtil.randomBytes(), EXACTLY_ONCE, false);
      publisher.disconnect();
      publisher.close();
   }

   /**
    * Drops every inbound PUBREC so that QoS 2 deliveries stay in flight and their correlations stay in the journal.
    */
   private MQTTInterceptor blockPubRec() {
      return (packet, connection) -> packet.fixedHeader().messageType() != MqttMessageType.PUBREC;
   }

   private MQTTInterceptor collectPublishes(Set<Integer> packetIds, CountDownLatch latch, boolean dupOnly) {
      return (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBLISH && packet instanceof MqttPublishMessage publish) {
            if (!dupOnly || publish.fixedHeader().isDup()) {
               if (packetIds.add(publish.variableHeader().packetId())) {
                  latch.countDown();
               }
            }
         }
         return true;
      };
   }

   private void assertBothDeliveriesSettled() throws Exception {
      Wait.assertEquals(0L, () -> getSubscriptionQueue(TOPIC, SUBSCRIBER_CLIENT_ID).getMessageCount(), 5000, 25);
      Wait.assertEquals(0L, () -> getSubscriptionQueue(OVERLAPPING_FILTER, SUBSCRIBER_CLIENT_ID).getMessageCount(), 5000, 25);
      Wait.assertEquals(0, () -> getProtocolManager().getStateManager().getPacketIdCorrelationSize(SUBSCRIBER_CLIENT_ID), 5000, 25);
      Wait.assertEquals(0, () -> getPubRecCacheSize(SUBSCRIBER_CLIENT_ID), 5000, 25);
   }
}
