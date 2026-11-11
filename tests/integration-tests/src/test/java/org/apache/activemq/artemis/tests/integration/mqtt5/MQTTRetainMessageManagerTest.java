/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.tests.integration.mqtt5;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class MQTTRetainMessageManagerTest extends MQTT5TestSupport {

   private MqttClient mqttPublisher;

   private MqttClient mqttConsumerBeforePublish;
   private MqttClient mqttConsumerAfterPublish;
   private MqttClient mqttConsumerAfterPublish2;

   private final AtomicInteger arrivedCountBeforePublish = new AtomicInteger();
   private final AtomicInteger arrivedCountAferPublish = new AtomicInteger();
   private final AtomicInteger arrivedCountAferPublish2 = new AtomicInteger();

   private final AtomicReference<MqttMessage> lastMessagePublished = new AtomicReference<>();
   private final AtomicReference<MqttMessage> lastMessageArrivedOnConsumerBeforePublish = new AtomicReference<>();
   private final AtomicReference<MqttMessage> lastMessageArrivedOnConsumerAfterPublish = new AtomicReference<>();
   private final AtomicReference<MqttMessage> lastMessageArrivedOnConsumerAfterPublish2 = new AtomicReference<>();

   private final String topic = "fact";

   private final int numberOfMessages = 1000;
   private final int numberOfTests = 10;

   @BeforeEach
   public void beforeEach() throws MqttException {
      mqttPublisher = createPahoClient("publisher");
      mqttPublisher.connect();

      final MqttMessage clearRetainedMessage = new MqttMessage(new byte[] {});
      clearRetainedMessage.setRetained(true);
      clearRetainedMessage.setQos(1);
      mqttPublisher.publish(topic, clearRetainedMessage);

      arrivedCountBeforePublish.set(0);
      mqttConsumerBeforePublish = createPahoClient("consumer-before");
      mqttConsumerBeforePublish.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) {
            lastMessageArrivedOnConsumerBeforePublish.set(message);
            arrivedCountBeforePublish.incrementAndGet();
         }
      });
      mqttConsumerBeforePublish.connect();

      arrivedCountAferPublish.set(0);
      mqttConsumerAfterPublish = createPahoClient("consumer-after");
      mqttConsumerAfterPublish.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) {
            lastMessageArrivedOnConsumerAfterPublish.set(message);
            arrivedCountAferPublish.incrementAndGet();
         }
      });
      mqttConsumerAfterPublish.connect();

      arrivedCountAferPublish2.set(0);
      mqttConsumerAfterPublish2 = createPahoClient("consumer-after2");
      mqttConsumerAfterPublish2.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) {
            lastMessageArrivedOnConsumerAfterPublish2.set(message);
            arrivedCountAferPublish2.incrementAndGet();
         }
      });
      mqttConsumerAfterPublish2.connect();
   }

   @AfterEach
   public void afterEach() throws MqttException {
      mqttPublisher.disconnect();
      mqttPublisher.close();

      mqttConsumerBeforePublish.unsubscribe(topic);
      mqttConsumerBeforePublish.disconnect();
      mqttConsumerBeforePublish.close();

      mqttConsumerAfterPublish.unsubscribe(topic);
      mqttConsumerAfterPublish.disconnect();
      mqttConsumerAfterPublish.close();

      mqttConsumerAfterPublish2.unsubscribe(topic);
      mqttConsumerAfterPublish2.disconnect();
      mqttConsumerAfterPublish2.close();
   }

   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testAtMostOnce() {
      IntStream.of(numberOfTests).forEach(i -> test(0));
   }

   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testAtLeastOnce() {
      IntStream.of(numberOfTests).forEach(i -> test(1));
   }

   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testExactlyOnce() {
      IntStream.of(numberOfTests).forEach(i -> test(2));
   }

   private void test(int qos) {
      try {
         mqttConsumerBeforePublish.subscribe(topic, qos);
         for (int i = 0; i < numberOfMessages; i++) {
            final MqttMessage message = new MqttMessage();
            message.setQos(qos);
            message.setRetained(true);
            message.setPayload(RandomUtil.randomUUIDString().getBytes(StandardCharsets.UTF_8));
            mqttPublisher.publish(topic, message);
            lastMessagePublished.set(message);
         }
         Wait.waitFor(() -> server.getAddressInfo(SimpleString.of(topic)).getRoutedMessageCount() >= numberOfMessages, 5000, 100);
         mqttConsumerAfterPublish.subscribe(topic, qos);
         mqttConsumerAfterPublish2.subscribe(topic, qos);
         Wait.waitFor(() -> lastMessageArrivedOnConsumerAfterPublish.get() != null, 5000, 100);
         Wait.waitFor(() -> lastMessageArrivedOnConsumerAfterPublish2.get() != null, 5000, 100);

         assertEquals(1, arrivedCountAferPublish.get());
         assertArrayEquals(lastMessagePublished.get().getPayload(),
                           lastMessageArrivedOnConsumerBeforePublish.get().getPayload(),
                           String.format("\nMessage that arrived on consumer subscribed before the publish is different from the last published message!\nPublished: %s\nArrived  : %s\n",
                                         new String(lastMessagePublished.get().getPayload(), StandardCharsets.UTF_8),
                                         new String(lastMessageArrivedOnConsumerBeforePublish.get().getPayload(), StandardCharsets.UTF_8)));
         assertArrayEquals(lastMessagePublished.get().getPayload(),
                           lastMessageArrivedOnConsumerAfterPublish.get().getPayload(),
                           String.format("\nMessage that arrived on consumer subscribed after the publish is different from the last published message!\nPublished: %s\nArrived  : %s\n",
                                         new String(lastMessagePublished.get().getPayload(), StandardCharsets.UTF_8),
                                         new String(lastMessageArrivedOnConsumerAfterPublish.get().getPayload(), StandardCharsets.UTF_8)));
         assertArrayEquals(lastMessagePublished.get().getPayload(),
                           lastMessageArrivedOnConsumerAfterPublish2.get().getPayload(),
                           String.format("\nMessage that arrived on consumer subscribed after the publish (2) is different from the last published message!\nPublished: %s\nArrived  : %s\n",
                                         new String(lastMessagePublished.get().getPayload(), StandardCharsets.UTF_8),
                                         new String(lastMessageArrivedOnConsumerAfterPublish2.get().getPayload(), StandardCharsets.UTF_8)));
      } catch (MqttException e) {
         fail(e.getMessage());
      }
   }
}
