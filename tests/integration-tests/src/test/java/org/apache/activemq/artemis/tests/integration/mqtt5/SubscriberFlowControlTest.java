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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.utils.Wait;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SubscriberFlowControlTest extends MQTT5TestSupport {

   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testUnsubscribeReleasesQuotaAndPromptsOtherSubscriptions() throws Exception {
      final String TOPIC_A = "topicA";
      final String TOPIC_B = "topicB";
      final String CONSUMER_ID = "consumer";
      final int RECEIVE_MAXIMUM = 1;

      AtomicInteger receivedFromTopicB = new AtomicInteger(0);
      CountDownLatch topicBLatch = new CountDownLatch(1);

      MqttClient consumer = createPahoClient(CONSUMER_ID);
      MqttConnectionOptions options = new MqttConnectionOptions();
      options.setReceiveMaximum(RECEIVE_MAXIMUM);
      consumer.setManualAcks(true);
      consumer.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            if (TOPIC_B.equals(topic)) {
               receivedFromTopicB.incrementAndGet();
               topicBLatch.countDown();
            }
         }
      });
      consumer.connect(options);
      consumer.subscribe(TOPIC_A, 1);
      consumer.subscribe(TOPIC_B, 1);

      MqttClient producer = createPahoClient("producer");
      producer.connect();

      // publish to topicA first to fill the quota
      producer.publish(TOPIC_A, "a1".getBytes(StandardCharsets.UTF_8), 1, false);

      // wait until topicA message is in flight
      Queue topicAQueue = getSubscriptionQueue(TOPIC_A, CONSUMER_ID);
      Wait.assertTrue(() -> topicAQueue.getDeliveringCount() > 0, 5000, 10);

      // publish to topicB while quota is full - it should be queued but not delivered
      producer.publish(TOPIC_B, "b1".getBytes(StandardCharsets.UTF_8), 1, false);
      Queue topicBQueue = getSubscriptionQueue(TOPIC_B, CONSUMER_ID);
      Wait.assertTrue(() -> topicBQueue.getMessageCount() > 0, 5000, 10);
      assertEquals(0, receivedFromTopicB.get(), "topicB message should be queued, not delivered");

      // unsubscribe from topicA - this should discard the in-flight message, free quota, and prompt topicB
      consumer.unsubscribe(TOPIC_A);

      // topicB should now receive its message
      assertTrue(topicBLatch.await(5000, TimeUnit.MILLISECONDS));
      assertEquals(1, receivedFromTopicB.get(), "topicB should receive after topicA unsubscribe");

      producer.disconnect();
      producer.close();
      consumer.disconnect();
      consumer.close();
   }

   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testNoLocalChangeReleasesQuotaAndPromptsOtherSubscriptions() throws Exception {
      final String TOPIC_A = "topicA";
      final String TOPIC_B = "topicB";
      final String CONSUMER_ID = "consumer";
      final int RECEIVE_MAXIMUM = 1;

      AtomicInteger receivedFromTopicB = new AtomicInteger(0);
      CountDownLatch topicBLatch = new CountDownLatch(1);

      MqttClient consumer = createPahoClient(CONSUMER_ID);
      MqttConnectionOptions options = new MqttConnectionOptions();
      options.setReceiveMaximum(RECEIVE_MAXIMUM);
      consumer.setManualAcks(true);
      consumer.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            if (TOPIC_B.equals(topic)) {
               receivedFromTopicB.incrementAndGet();
               topicBLatch.countDown();
            }
         }
      });
      consumer.connect(options);

      // subscribe to topicA with no-local = false
      consumer.subscribe(TOPIC_A, 1);
      consumer.subscribe(TOPIC_B, 1);

      MqttClient producer = createPahoClient("producer");
      producer.connect();

      // publish to topicA from the consumer itself to fill the quota;
      // using the consumer's client ID ensures the noLocal filter will block
      // re-delivery after re-subscribe, preventing a race with topicB
      consumer.publish(TOPIC_A, "a1".getBytes(StandardCharsets.UTF_8), 1, false);

      // wait until topicA message is in flight
      Queue topicAQueue = getSubscriptionQueue(TOPIC_A, CONSUMER_ID);
      Wait.assertTrue(() -> topicAQueue.getDeliveringCount() > 0, 5000, 10);

      // publish to topicB while quota is full - it should be queued but not delivered
      producer.publish(TOPIC_B, "b1".getBytes(StandardCharsets.UTF_8), 1, false);
      Queue topicBQueue = getSubscriptionQueue(TOPIC_B, CONSUMER_ID);
      Wait.assertTrue(() -> topicBQueue.getMessageCount() > 0, 5000, 10);
      assertEquals(0, receivedFromTopicB.get(), "topicB message should be queued, not delivered");

      // re-subscribe to topicA with no-local = true
      // this triggers consumer replacement and calls removeCoreDeliveryInfos()
      MqttSubscription sub = new MqttSubscription(TOPIC_A, 1);
      sub.setNoLocal(true);
      consumer.subscribe(new MqttSubscription[]{sub});

      // topicB should now receive its message
      assertTrue(topicBLatch.await(5000, TimeUnit.MILLISECONDS));
      assertEquals(1, receivedFromTopicB.get(), "topicB should receive after topicA no-local change");

      producer.disconnect();
      producer.close();
      consumer.disconnect();
      consumer.close();
   }

   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testBusySubscriptionDoesNotStarveIdleSubscription() throws Exception {
      final String TOPIC_A = "topicA";
      final String TOPIC_B = "topicB";
      final String CONSUMER_ID = "consumer";
      final int RECEIVE_MAXIMUM = 1;

      AtomicInteger receivedFromTopicA = new AtomicInteger(0);

      MqttClient consumer = createPahoClient(CONSUMER_ID);
      MqttConnectionOptions options = new MqttConnectionOptions();
      options.setReceiveMaximum(RECEIVE_MAXIMUM);
      consumer.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            if (TOPIC_A.equals(topic)) {
               receivedFromTopicA.incrementAndGet();
            } else {
               // simulate processing time on the busy subscription
               Thread.sleep(200);
            }
         }
      });
      consumer.connect(options);
      consumer.subscribe(TOPIC_A, 1);
      consumer.subscribe(TOPIC_B, 1);

      MqttClient producer = createPahoClient("producer");
      producer.connect();

      // simulate topicB's steady, high-frequency publisher occupying the session's shared in-flight window
      AtomicBoolean running = new AtomicBoolean(true);
      Thread producerThread = new Thread(() -> {
         try {
            while (running.get()) {
               producer.publish(TOPIC_B, "b".getBytes(StandardCharsets.UTF_8), 1, false);
            }
         } catch (Exception expectedOnDisconnect) {
         }
      });
      producerThread.start();

      try {
         // wait until a topicB message is actually in flight before publishing to topicA, so topicA's single delivery
         // attempt deterministically lands while the flow-control capacity is already used up
         Wait.assertTrue(() -> getSubscriptionQueue(TOPIC_B, CONSUMER_ID).getDeliveringCount() > 0, 5000, 10);

         // simulate topicA's rare publisher sending a single message while topicB keeps flow-control busy
         producer.publish(TOPIC_A, "a".getBytes(StandardCharsets.UTF_8), 1, false);

         // the topicA subscriber should not be starved
         Wait.assertTrue(() -> receivedFromTopicA.get() > 0, 5000, 100);
      } finally {
         running.set(false);
         producerThread.join(5000);
         producer.disconnect();
         producer.close();
         consumer.disconnect();
         consumer.close();
      }
   }
}
