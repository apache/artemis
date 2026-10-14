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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTInterceptor;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptionsBuilder;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SubscriptionTeardownPacketIdTest extends MQTT5TestSupport {

   private static final long DEFAULT_TIMEOUT_SEC = 60;

   private static final String SUBSCRIBER_CLIENT_ID = "teardown-subscriber";
   private static final String PUBLISHER_CLIENT_ID = "teardown-publisher";

   @Override
   public boolean isProtocolLoggingEnabled() {
      return true;
   }

   /**
    * Unsubscribing while a delivery is unacknowledged must release the packet ID rather than leave it reserved. The
    * subscription queue and the message are deleted along with the subscription, so nothing can ever acknowledge it.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testUnsubscribeReleasesInFlightPacketIds() throws Exception {
      final String topic = "teardown/single";

      MqttClient subscriber = connectSubscriber();
      MqttClient publisher = connectPublisher();

      subscriber.subscribe(topic, AT_LEAST_ONCE);
      publisher.publish(topic, RandomUtil.randomBytes(), AT_LEAST_ONCE, false);
      Wait.assertEquals(1, this::correlationCount, 10000, 25);

      subscriber.unsubscribe(topic);

      Wait.assertEquals(0, this::correlationCount, 10000, 25);
      Wait.assertFalse(() -> getProtocolManager().getStateManager().packetIdCorrelationExists(SUBSCRIBER_CLIENT_ID, 1), 10000, 25);

      disconnect(subscriber, publisher);
   }

   /**
    * The release has to hold across repeated cycles. Every packet ID used by a subscription that has since been removed
    * must end up free again, otherwise a client that subscribes and unsubscribes in a loop walks steadily toward
    * exhausting the packet ID space.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testRepeatedSubscribeUnsubscribeDoesNotLeakPacketIds() throws Exception {
      final String topic = "teardown/repeat";
      final int cycles = 10;
      final Set<Integer> packetIdsUsed = ConcurrentHashMap.newKeySet();
      server.getRemotingService().addOutgoingInterceptor(publishWatcher((topicName, packetId, dup) -> packetIdsUsed.add(packetId)));

      MqttClient subscriber = connectSubscriber();
      MqttClient publisher = connectPublisher();

      for (int i = 0; i < cycles; i++) {
         subscriber.subscribe(topic, AT_LEAST_ONCE);
         publisher.publish(topic, RandomUtil.randomBytes(), AT_LEAST_ONCE, false);
         Wait.assertEquals(1, this::correlationCount, 10000, 25);
         subscriber.unsubscribe(topic);
         Wait.assertEquals(0, this::correlationCount, 10000, 25);
      }

      assertEquals(cycles, packetIdsUsed.size(), "Expected one in-flight delivery per cycle");
      assertEquals(0, correlationCount(), "Packet ID correlations leaked across subscribe and unsubscribe cycles");
      for (int packetId : packetIdsUsed) {
         assertFalse(getProtocolManager().getStateManager().packetIdCorrelationExists(SUBSCRIBER_CLIENT_ID, packetId), "Packet ID " + packetId + " is still reserved after its subscription was removed");
      }

      disconnect(subscriber, publisher);
   }

   /**
    * The release has to be scoped to the subscription being removed. Unsubscribing one subscription must not disturb
    * the in-flight state of another.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testUnsubscribeLeavesOtherSubscriptionsAlone() throws Exception {
      final String removedTopic = "teardown/removed";
      final String keptTopic = "teardown/kept";
      final Map<String, Integer> packetIdByTopic = new ConcurrentHashMap<>();
      server.getRemotingService().addOutgoingInterceptor(publishWatcher((topicName, packetId, dup) -> packetIdByTopic.put(topicName, packetId)));

      MqttClient subscriber = connectSubscriber();
      MqttClient publisher = connectPublisher();

      subscriber.subscribe(removedTopic, AT_LEAST_ONCE);
      subscriber.subscribe(keptTopic, AT_LEAST_ONCE);
      publisher.publish(removedTopic, RandomUtil.randomBytes(), AT_LEAST_ONCE, false);
      publisher.publish(keptTopic, RandomUtil.randomBytes(), AT_LEAST_ONCE, false);

      Wait.assertEquals(2, this::correlationCount, 10000, 25);
      Wait.assertTrue(() -> packetIdByTopic.containsKey(removedTopic) && packetIdByTopic.containsKey(keptTopic), 10000, 25);
      int removedPacketId = packetIdByTopic.get(removedTopic);
      int keptPacketId = packetIdByTopic.get(keptTopic);

      subscriber.unsubscribe(removedTopic);

      Wait.assertEquals(1, this::correlationCount, 10000, 25);
      assertFalse(getProtocolManager().getStateManager().packetIdCorrelationExists(SUBSCRIBER_CLIENT_ID, removedPacketId), "Packet ID of the removed subscription should have been released");
      assertTrue(getProtocolManager().getStateManager().packetIdCorrelationExists(SUBSCRIBER_CLIENT_ID, keptPacketId), "Packet ID of the surviving subscription should have been left alone");

      disconnect(subscriber, publisher);
   }

   /**
    * Replacing a subscription's consumer, which the broker does when a re-subscribe flips the no-local option, cancels
    * its in-flight messages back to the queue for redelivery. That redelivery must reuse the packet ID the client
    * already holds and be flagged as a duplicate, rather than arriving as an apparently new message.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testConsumerReplacementRedeliversWithOriginalPacketId() throws Exception {
      final String topic = "teardown/replace";
      final List<String> publishes = Collections.synchronizedList(new ArrayList<>());
      server.getRemotingService().addOutgoingInterceptor(publishWatcher((topicName, packetId, dup) -> publishes.add(packetId + (dup ? " dup" : " new"))));

      MqttClient subscriber = connectSubscriber();
      MqttClient publisher = connectPublisher();

      MqttSubscription subscription = new MqttSubscription(topic, AT_LEAST_ONCE);
      subscription.setNoLocal(false);
      subscriber.subscribe(new MqttSubscription[]{subscription});

      publisher.publish(topic, RandomUtil.randomBytes(), AT_LEAST_ONCE, false);
      Wait.assertEquals(1, this::correlationCount, 10000, 25);
      Wait.assertEquals(1, publishes::size, 10000, 25);
      String original = publishes.get(0);

      // flipping no-local makes the broker replace the consumer on the very same subscription queue
      MqttSubscription replacement = new MqttSubscription(topic, AT_LEAST_ONCE);
      replacement.setNoLocal(true);
      subscriber.subscribe(new MqttSubscription[]{replacement});

      Wait.assertEquals(2, publishes::size, 10000, 25);
      assertEquals(original.replace(" new", " dup"), publishes.get(1), "Redelivery after a consumer replacement must reuse the original packet ID and set the duplicate flag, but the sequence was " + publishes);
      assertEquals(1, correlationCount(), "Replacing a consumer must not add a second correlation for the same delivery");

      disconnect(subscriber, publisher);
   }

   private interface PublishObserver {
      void accept(String topicName, int packetId, boolean dup);
   }

   private MQTTInterceptor publishWatcher(PublishObserver observer) {
      return (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBLISH && packet instanceof MqttPublishMessage publish) {
            observer.accept(publish.variableHeader().topicName(), publish.variableHeader().packetId(), publish.fixedHeader().isDup());
         }
         return true;
      };
   }

   private int correlationCount() {
      return getProtocolManager().getStateManager().getPacketIdCorrelationSize(SUBSCRIBER_CLIENT_ID);
   }

   /**
    * Connects with manual acknowledgements so that deliveries stay in flight for the duration of the test.
    */
   private MqttClient connectSubscriber() throws Exception {
      MqttClient subscriber = createPahoClient(SUBSCRIBER_CLIENT_ID);
      subscriber.setManualAcks(true);
      subscriber.connect(new MqttConnectionOptionsBuilder().cleanStart(false).sessionExpiryInterval(300L).build());
      return subscriber;
   }

   private MqttClient connectPublisher() throws Exception {
      MqttClient publisher = createPahoClient(PUBLISHER_CLIENT_ID);
      publisher.connect();
      return publisher;
   }

   private void disconnect(MqttClient subscriber, MqttClient publisher) throws Exception {
      publisher.disconnect();
      publisher.close();
      subscriber.disconnect();
      subscriber.close();
   }
}
