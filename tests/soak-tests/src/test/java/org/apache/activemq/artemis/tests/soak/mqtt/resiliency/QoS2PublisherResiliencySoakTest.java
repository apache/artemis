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
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

public class QoS2PublisherResiliencySoakTest extends QoS2ResiliencySoakTestSupport {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final String TEST_NAME = "QOS2_PUBLISHER_RESILIENCY_SOAK";

   private static final int NUM_PUBLISHERS = TestParameters.testProperty(TEST_NAME, "NUM_PUBLISHERS", 5);
   private static final int NUM_MESSAGES = TestParameters.testProperty(TEST_NAME, "NUM_MESSAGES", 10_000);
   private static final int RESTART_PAUSE = TestParameters.testProperty(TEST_NAME, "RESTART_PAUSE", 2_000);
   private static final int TIMEOUT_SECONDS = TestParameters.testProperty(TEST_NAME, "TIMEOUT_SECONDS", 180);

   @Test
   @Timeout(value = 5, unit = TimeUnit.MINUTES)
   public void testQoS2PublisherResiliency() throws Exception {
      disableProtocolLogging();
      final String PUB_CLIENT_ID_PREFIX = "pub-";
      final String SUB_CLIENT_ID = "sub";

      // create subscription queue for consuming messages later
      Mqtt5BlockingClient subscriber = createHiveMQClient(SUB_CLIENT_ID, false);
      subscriber.connectWith()
         .cleanStart(false)
         .sessionExpiryInterval(300)
         .send();
      subscriber.subscribeWith()
         .topicFilter(TOPIC)
         .qos(MqttQos.AT_LEAST_ONCE)
         .send();
      subscriber.disconnect();

      assertNotNull(getSubscriptionQueue(TOPIC, SUB_CLIENT_ID));

      logger.info("{} publishers, {} messages/publisher, {} total expected", NUM_PUBLISHERS, NUM_MESSAGES, NUM_PUBLISHERS * NUM_MESSAGES);

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

      // start broker restart task
      BrokerRestartTask restartTask = startBrokerRestartTask(RESTART_PAUSE, () -> {
         logger.info("===========");
         logger.info("Subscription queue received {}/{} messages", getSubscriptionQueue(TOPIC, SUB_CLIENT_ID).getMessageCount(), NUM_PUBLISHERS * NUM_MESSAGES);
         logger.info("===========");
      });

      // enableProtocolLogging();

      // start publisher tasks
      PublishResult publishResult = startPublishing(publishers, NUM_MESSAGES);

      // wait for all publishers to finish
      publishResult.executor().shutdown();
      assertTrue(publishResult.executor().awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS), "Publishers did not finish in time");
      logger.info("All publishers finished. Total messages sent: {}. Publish errors: {}", publishResult.sentMessages().size(), publishResult.publishErrors().get());

      disableProtocolLogging();

      // stop restart task and ensure broker is running
      stopBrokerRestartTask(restartTask);

      final long messageCount = getSubscriptionQueue(TOPIC, SUB_CLIENT_ID).getMessageCount();

      // reconnect subscriber to verify there are no duplicates
      Set<String> consumedMessages = ConcurrentHashMap.newKeySet(NUM_MESSAGES * NUM_PUBLISHERS);
      AtomicInteger duplicateCount = new AtomicInteger(0);
      subscriber.toAsync().publishes(MqttGlobalPublishFilter.ALL, publish -> {
         String payload = new String(publish.getPayloadAsBytes(), StandardCharsets.UTF_8);
         if (consumedMessages.contains(payload)) {
            logger.warn("Duplicate message: {}", payload);
            duplicateCount.incrementAndGet();
         }
         consumedMessages.add(payload);
         publishResult.sentMessages().remove(payload);
      });

      subscriber.connectWith()
         .cleanStart(false)
         .sessionExpiryInterval(300)
         .send();

      Wait.waitFor(() -> consumedMessages.size() == messageCount);

      cleanDisconnect(subscriber);

      assertEquals(0, duplicateCount.get());
      assertEquals(0, publishResult.sentMessages().size(), "These messages were published, but were not on the broker: " + publishResult.sentMessages());
      assertEquals(NUM_PUBLISHERS * NUM_MESSAGES, consumedMessages.size());

      for (Mqtt5BlockingClient publisher : publishers) {
         String clientId = getClientId(publisher);
         Wait.assertEquals(0, () -> getPubCacheSize(clientId), 5000, 100);
         cleanDisconnect(publisher);
         assertNull(getPubCache(clientId), "Pub cache should be null after clean start for " + clientId);
      }
   }
}