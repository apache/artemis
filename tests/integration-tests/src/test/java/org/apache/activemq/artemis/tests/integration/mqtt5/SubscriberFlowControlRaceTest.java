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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.activemq.artemis.core.protocol.mqtt.MQTTSessionState;
import org.apache.activemq.artemis.utils.Wait;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This is a best-effort stress/regression test: it can't guarantee it hits the race on every run, but with enough
 * concurrent subscriptions, concurrent publishers, and a tight polling loop it reproduces the overrun reliably.
 */
public class SubscriberFlowControlRaceTest extends MQTT5TestSupport {

   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testSharedInFlightQuotaNeverExceedsReceiveMaximum() throws Exception {
      final String CONSUMER_ID = "consumer";
      final int RECEIVE_MAXIMUM = 5;
      final int SUBSCRIPTION_COUNT = 20;
      final int PRODUCER_THREAD_COUNT = 8;
      final long TARGET_PUBLISH_COUNT = 5000;

      String[] topics = new String[SUBSCRIPTION_COUNT];
      for (int i = 0; i < SUBSCRIPTION_COUNT; i++) {
         topics[i] = "race/topic" + i;
      }

      MqttClient consumer = createPahoClient(CONSUMER_ID);
      MqttConnectionOptions options = new MqttConnectionOptions();
      options.setReceiveMaximum(RECEIVE_MAXIMUM);
      consumer.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
         }
      });
      consumer.connect(options);
      for (String topic : topics) {
         consumer.subscribe(topic, 1);
      }

      MQTTSessionState mqttSessionState = getSessionStates().get(CONSUMER_ID);
      assertNotNull(mqttSessionState);

      AtomicBoolean running = new AtomicBoolean(true);
      AtomicInteger maxObservedQuota = new AtomicInteger(0);
      AtomicLong totalPublished = new AtomicLong(0);

      Thread monitorThread = new Thread(() -> {
         while (running.get()) {
            maxObservedQuota.updateAndGet(max -> Math.max(max, mqttSessionState.getSendQuota()));
            Thread.yield();
         }
      });
      monitorThread.start();

      List<MqttClient> producers = new ArrayList<>();
      List<Thread> producerThreads = new ArrayList<>();
      for (int i = 0; i < PRODUCER_THREAD_COUNT; i++) {
         MqttClient producer = createPahoClient("producer" + i);
         producer.connect();
         producers.add(producer);

         final int producerIndex = i;
         Thread producerThread = new Thread(() -> {
            int topicIndex = producerIndex;
            while (running.get()) {
               try {
                  producer.publish(topics[topicIndex % topics.length], "x".getBytes(StandardCharsets.UTF_8), 1, false);
                  totalPublished.incrementAndGet();
                  topicIndex++;
               } catch (Exception expected) {
                  break;
               }
            }
         });
         producerThreads.add(producerThread);
         producerThread.start();
      }

      try {
         Wait.waitFor(() -> totalPublished.get() >= TARGET_PUBLISH_COUNT, 30000, 10);
      } finally {
         running.set(false);

         for (Thread producerThread : producerThreads) {
            producerThread.join(5000);
         }
         monitorThread.join(5000);

         for (MqttClient producer : producers) {
            producer.disconnect();
            producer.close();
         }
         consumer.disconnect();
         consumer.close();
      }

      assertTrue(totalPublished.get() >= TARGET_PUBLISH_COUNT, "Didn't publish enough messages to meaningfully stress the shared quota; only published " + totalPublished.get());
      assertTrue(maxObservedQuota.get() <= RECEIVE_MAXIMUM, "Observed send quota " + maxObservedQuota.get() + " exceeded Receive Maximum " + RECEIVE_MAXIMUM);
   }
}
