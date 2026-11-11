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
package org.apache.activemq.artemis.tests.soak.mqtt.resiliency;

import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import org.apache.activemq.artemis.tests.soak.mqtt.resiliency.client.MqttTestClient;
import org.apache.activemq.artemis.tests.soak.mqtt.resiliency.client.MqttVersion;
import org.apache.activemq.artemis.utils.TestParameters;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class QoS2SubscriberResiliencySoakTest extends QoS2ResiliencySoakTestSupport {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final String TEST_NAME = "QOS2_SUBSCRIBER_RESILIENCY_SOAK";

   private static final int NUM_SUBSCRIBERS = TestParameters.testProperty(TEST_NAME, "NUM_SUBSCRIBERS", 5);
   private static final int NUM_MESSAGES = TestParameters.testProperty(TEST_NAME, "NUM_MESSAGES", 10_000);
   private static final int RESTART_PAUSE = TestParameters.testProperty(TEST_NAME, "RESTART_PAUSE", 2_000);
   private static final int TIMEOUT_SECONDS = TestParameters.testProperty(TEST_NAME, "TIMEOUT_SECONDS", 180);

   @Test
   @Timeout(value = 5, unit = TimeUnit.MINUTES)
   public void testQoS2SubscriberResiliencyMqtt3() throws Exception {
      testQoS2SubscriberResiliency(MqttVersion.MQTT3);
   }

   @Test
   @Timeout(value = 5, unit = TimeUnit.MINUTES)
   public void testQoS2SubscriberResiliencyMqtt5() throws Exception {
      testQoS2SubscriberResiliency(MqttVersion.MQTT5);
   }

   private void testQoS2SubscriberResiliency(MqttVersion version) throws Exception {
      disableProtocolLogging();
      logger.info("{} {} subscribers, {} messages/subscriber", version, NUM_SUBSCRIBERS, NUM_MESSAGES);

      // create and subscribe then disconnect to leave idle subscriptions on the broker
      // (disconnection is handled centrally in tearDown)
      final List<MqttTestClient> subscribers = new ArrayList<>(NUM_SUBSCRIBERS);
      for (int i = 0; i < NUM_SUBSCRIBERS; i++) {
         String clientId = "sub-" + i;
         MqttTestClient subscriber = createClient(version, clientId, true);
         subscriber.connect();
         subscriber.subscribe(TOPIC, MqttQos.EXACTLY_ONCE);
         subscriber.disconnect();
         subscribers.add(subscriber);
         assertNotNull(getSubscriptionQueue(TOPIC, subscriber.getClientId()));
      }

      // send messages using QoS 2
      final Set<String> sentMessages = new HashSet<>(NUM_MESSAGES);
      MqttTestClient publisher = createClient(version, "pub", false);
      publisher.connectClean();
      logger.info("Publishing {} messages...", NUM_MESSAGES);
      for (int seq = 0; seq < NUM_MESSAGES; seq++) {
         String payload = String.valueOf(seq);
         sentMessages.add(payload);
         publisher.publish(TOPIC, MqttQos.EXACTLY_ONCE, payload.getBytes(StandardCharsets.UTF_8));
      }
      logger.info("Published {} messages.", NUM_MESSAGES);
      cleanDisconnect(publisher);

      for (MqttTestClient subscriber : subscribers) {
         assertEquals(NUM_MESSAGES, getSubscriptionQueue(TOPIC, subscriber.getClientId()).getMessageCount());
      }

      final Map<String, Set<String>> receivedPerSubscriber = new HashMap<>();
      final Map<String, Set<String>> duplicatesPerSubscriber = new HashMap<>();
      final AtomicLong lastReceiveTime = new AtomicLong(System.currentTimeMillis());

      for (MqttTestClient subscriber : subscribers) {
         final String clientId = subscriber.getClientId();
         final Set<String> received = ConcurrentHashMap.newKeySet(NUM_MESSAGES);
         receivedPerSubscriber.put(clientId, received);
         final Set<String> duplicates = ConcurrentHashMap.newKeySet();
         duplicatesPerSubscriber.put(clientId, duplicates);
         subscriber.onMessage(bytes -> {
            String payload = new String(bytes, StandardCharsets.UTF_8);
            if (!received.add(payload)) {
               logger.warn("Subscriber {} received duplicate: {}", clientId, payload);
               duplicates.add(payload);
            }
            lastReceiveTime.set(System.currentTimeMillis());
         });
         subscriber.connect();
         logger.info("Subscriber {} reconnected", clientId);
      }

      // start broker restart task
      BrokerRestartTask restartTask = startBrokerRestartTask(RESTART_PAUSE, () -> {
         logger.info("===========");
         for (Map.Entry<String, Set<String>> entry : receivedPerSubscriber.entrySet()) {
            logger.info("Subscriber {} received {}/{} messages", entry.getKey(), entry.getValue().size(), NUM_MESSAGES);
         }
         logger.info("Last message received {}ms ago.", System.currentTimeMillis() - lastReceiveTime.get());
         logger.info("===========");
      });

      final long SUBSCRIBER_TIMEOUT = 20_000;
      Wait.assertTrue(() -> {
         // quit early if subscribers are dead/stalled for some reason
         if (System.currentTimeMillis() - lastReceiveTime.get() > SUBSCRIBER_TIMEOUT) {
            for (Map.Entry<String, Set<String>> entry : receivedPerSubscriber.entrySet()) {
               logger.warn("Subscriber {} received {}/{} messages", entry.getKey(), entry.getValue().size(), NUM_MESSAGES);
            }
            throw new AssertionError("No subscriber has received a message in " + SUBSCRIBER_TIMEOUT / 1000 + " seconds");
         }
         // any duplicate is a failure, no need to wait until the end
         for (Map.Entry<String, Set<String>> duplicates : duplicatesPerSubscriber.entrySet()) {
            assertEquals(0, duplicates.getValue().size(), "Subscriber " + duplicates.getKey() + " received duplicates: " + duplicates.getValue());
         }
         for (Set<String> received : receivedPerSubscriber.values()) {
            if (received.size() < NUM_MESSAGES) {
               return false;
            }
         }
         return true;
      }, TIMEOUT_SECONDS * 1000L, 100);

      disableProtocolLogging();

      // stop reconnection task, ensure broker is running
      stopBrokerRestartTask(restartTask);

      // verify all expected messages received with no duplicates
      for (MqttTestClient subscriber : subscribers) {
         String clientId = subscriber.getClientId();
         assertEquals(0, duplicatesPerSubscriber.get(clientId).size(), "Subscriber " + clientId + " received duplicates: " + duplicatesPerSubscriber.get(clientId));
         assertEquals(NUM_MESSAGES, receivedPerSubscriber.get(clientId).size(), "Subscriber " + clientId + " didn't receive: " + getMissingMessages(sentMessages, receivedPerSubscriber.get(clientId)));
         Wait.assertEquals(0L, () -> getSubscriptionQueue(TOPIC, clientId).getMessageCount(), 2000, 20, () -> "Subscription queue for " + clientId + " has incorrect message count");
         assertEquals(0, getProtocolManager().getStateManager().getPacketIdCorrelationSize(clientId));
         Wait.assertEquals(0, () -> getSubCacheSize(clientId), 2000, 20, () -> "Sub cache for " + clientId + " has incorrect size");
         cleanDisconnect(subscriber);
         assertNull(getSubCache(clientId), "Sub cache should be null after clean start for " + clientId);
      }
   }
}