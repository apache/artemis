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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import org.apache.activemq.artemis.utils.TestParameters;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class QoS2CombinedResiliencySoakTest extends QoS2ResiliencySoakTestSupport {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final String TEST_NAME = "QOS2_COMBINED_RESILIENCY_SOAK";

   private static final int NUM_PUBLISHERS = TestParameters.testProperty(TEST_NAME, "NUM_PUBLISHERS", 5);
   private static final int NUM_SUBSCRIBERS = TestParameters.testProperty(TEST_NAME, "NUM_SUBSCRIBERS", 5);
   private static final int NUM_MESSAGES = TestParameters.testProperty(TEST_NAME, "NUM_MESSAGES", 2_000);
   private static final int RESTART_PAUSE = TestParameters.testProperty(TEST_NAME, "RESTART_PAUSE", 2_000);
   private static final int TIMEOUT_SECONDS = TestParameters.testProperty(TEST_NAME, "TIMEOUT_SECONDS", 180);

   @Test
   @Timeout(value = 5, unit = TimeUnit.MINUTES)
   public void testQoS2CombinedResiliency() throws Exception {
      disableProtocolLogging();
      final String PUB_CLIENT_ID_PREFIX = "pub-";
      final String SUB_CLIENT_ID_PREFIX = "sub-";

      logger.info("{} publishers, {} subscribers, {} messages/publisher, {} total expected per subscriber", NUM_PUBLISHERS, NUM_SUBSCRIBERS, NUM_MESSAGES, NUM_PUBLISHERS * NUM_MESSAGES);

      // create and subscribe all subscribers
      final List<Mqtt5BlockingClient> subscribers = new ArrayList<>(NUM_SUBSCRIBERS);
      runAfter(() -> subscribers.forEach(c -> {
         try {
            c.disconnect();
         } catch (Exception ignored) {
         }
      }));

      final Map<String, Set<String>> receivedPerSubscriber = new HashMap<>();
      final Map<String, Set<String>> duplicatesPerSubscriber = new HashMap<>();
      final AtomicLong lastReceiveTime = new AtomicLong(System.currentTimeMillis());

      for (int i = 0; i < NUM_SUBSCRIBERS; i++) {
         String clientId = SUB_CLIENT_ID_PREFIX + i;
         Mqtt5BlockingClient subscriber = createHiveMQClient(clientId, true);
         final Set<String> received = ConcurrentHashMap.newKeySet(NUM_PUBLISHERS * NUM_MESSAGES);
         receivedPerSubscriber.put(clientId, received);
         final Set<String> duplicates = ConcurrentHashMap.newKeySet();
         duplicatesPerSubscriber.put(clientId, duplicates);
         subscriber.toAsync().publishes(MqttGlobalPublishFilter.ALL, publish -> {
            String payload = new String(publish.getPayloadAsBytes(), StandardCharsets.UTF_8);
            if (!received.add(payload)) {
               logger.warn("Subscriber {} received duplicate: {}", clientId, payload);
               duplicates.add(payload);
            }
            lastReceiveTime.set(System.currentTimeMillis());
         });
         subscriber.connectWith()
            .cleanStart(false)
            .sessionExpiryInterval(300)
            .send();
         subscriber.subscribeWith()
            .topicFilter(TOPIC)
            .qos(MqttQos.EXACTLY_ONCE)
            .send();
         subscribers.add(subscriber);
         assertNotNull(getSubscriptionQueue(TOPIC, clientId));
         logger.info("Subscriber {} connected and subscribed", clientId);
      }

      // create and connect publishers
      final List<Mqtt5BlockingClient> publishers = new ArrayList<>();
      runAfter(() -> publishers.forEach(c -> {
         try {
            c.disconnect();
         } catch (Exception ignored) {
         }
      }));
      for (int i = 0; i < NUM_PUBLISHERS; i++) {
         String clientId = PUB_CLIENT_ID_PREFIX + i;
         Mqtt5BlockingClient publisher = createHiveMQClient(clientId, true);
         publisher.connectWith()
            .cleanStart(false)
            .sessionExpiryInterval(300)
            .send();
         publishers.add(publisher);
         logger.info("Publisher {} connected", clientId);
      }

      // start publisher tasks
      PublishResult publishResult = startPublishing(publishers, NUM_MESSAGES);

      // start broker restart task
      BrokerRestartTask restartTask = startBrokerRestartTask(RESTART_PAUSE, () -> {
         logger.info("===========");
         for (Map.Entry<String, Set<String>> entry : receivedPerSubscriber.entrySet()) {
            logger.info("Subscriber {} received {}/{} messages", entry.getKey(), entry.getValue().size(), NUM_PUBLISHERS * NUM_MESSAGES);
         }
         logger.info("Last message received {}ms ago.", System.currentTimeMillis() - lastReceiveTime.get());
         logger.info("===========");
      });

      // wait for all publishers to finish sending
      publishResult.executor().shutdown();
      assertTrue(publishResult.executor().awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS), "Publishers did not finish in time");
      logger.info("All publishers finished. Total messages sent: {}. Publish errors: {}", publishResult.sentMessages().size(), publishResult.publishErrors().get());

      // wait for all subscribers to receive all messages
      final long STALL_TIMEOUT_MS = 20_000;
      Wait.assertTrue(() -> {
         if (System.currentTimeMillis() - lastReceiveTime.get() > STALL_TIMEOUT_MS) {
            for (Map.Entry<String, Set<String>> entry : receivedPerSubscriber.entrySet()) {
               logger.warn("Subscriber {} received {}/{} messages", entry.getKey(), entry.getValue().size(), NUM_PUBLISHERS * NUM_MESSAGES);
            }
            throw new AssertionError("No subscriber has received a message in " + STALL_TIMEOUT_MS / 1000 + " seconds");
         }
         for (Map.Entry<String, Set<String>> duplicates : duplicatesPerSubscriber.entrySet()) {
            assertEquals(0, duplicates.getValue().size(), "Subscriber " + duplicates.getKey() + " received duplicates: " + duplicates.getValue());
         }
         for (Set<String> received : receivedPerSubscriber.values()) {
            if (received.size() < NUM_PUBLISHERS * NUM_MESSAGES) {
               return false;
            }
         }
         return true;
      }, TIMEOUT_SECONDS * 1000L, 100);

      disableProtocolLogging();

      // stop reconnection task, ensure broker is running
      stopBrokerRestartTask(restartTask);

      // verify all expected messages received with no duplicates
      for (Mqtt5BlockingClient subscriber : subscribers) {
         String clientId = getClientId(subscriber);
         assertEquals(0, duplicatesPerSubscriber.get(clientId).size(), "Subscriber " + clientId + " received duplicates: " + duplicatesPerSubscriber.get(clientId));
         assertEquals(NUM_PUBLISHERS * NUM_MESSAGES, receivedPerSubscriber.get(clientId).size(), "Subscriber " + clientId + " didn't receive: " + getMissingMessages(publishResult.sentMessages(), receivedPerSubscriber.get(clientId)));
         Wait.assertEquals(0L, () -> getSubscriptionQueue(TOPIC, clientId).getMessageCount(), 2000, 20, () -> "Subscription queue for " + clientId + " has incorrect message count");
         assertEquals(0, getProtocolManager().getStateManager().getPacketIdCorrelationSize(clientId));
         assertEquals(0, getSubCacheSize(clientId));
         cleanDisconnect(subscriber);
         assertNull(getSubCache(clientId), "Sub cache should be null after clean start for " + clientId);
      }

      for (Mqtt5BlockingClient publisher : publishers) {
         String clientId = getClientId(publisher);
         Wait.assertEquals(0, () -> getPubCacheSize(clientId), 5000, 100);
         cleanDisconnect(publisher);
         assertNull(getPubCache(clientId), "Pub cache should be null after clean start for " + clientId);
      }
   }
}