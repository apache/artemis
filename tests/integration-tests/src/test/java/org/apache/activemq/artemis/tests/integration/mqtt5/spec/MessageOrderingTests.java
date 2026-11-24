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
package org.apache.activemq.artemis.tests.integration.mqtt5.spec;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.tests.integration.mqtt5.MQTT5TestSupport;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptionsBuilder;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/*
 * Fulfilled by client or Netty codec (i.e. not tested here):
 *
 * [MQTT-4.6.0-1] When the Client re-sends any PUBLISH packets, it MUST re-send them in the order in which the original PUBLISH packets were sent (this applies to QoS 1 and QoS 2 messages).
 * [MQTT-4.6.0-2] The Client MUST send PUBACK packets in the order in which the corresponding PUBLISH packets were received (QoS 1 messages).
 * [MQTT-4.6.0-3] The Client MUST send PUBREC packets in the order in which the corresponding PUBLISH packets were received (QoS 2 messages).
 * [MQTT-4.6.0-4] The Client MUST send PUBREL packets in the order in which the corresponding PUBREC packets were received (QoS 2 messages).
 */

public class MessageOrderingTests extends MQTT5TestSupport {

   /**
    * The number of PUBLISH packets the producer writes back-to-back before waiting for any acknowledgement.
    */
   private static final int BATCH_SIZE = 5;

   /**
    * Ordering problems in the outbound path are timing-dependent, so a single batch is not enough to expose them
    * reliably. Each batch is drained before the next one is published, which keeps the subscription queue close to
    * empty and therefore forces a fresh dispatch of the broker's delivery logic for every batch.
    */
   private static final int BATCH_COUNT = 500;

   /**
    * [MQTT-4.6.0-5] When a Server processes a message that has been published to an Ordered Topic, it MUST send
    * PUBLISH packets to consumers (for the same Topic and QoS) in the order that they were received from any given
    * Client.
    * <p>
    * [MQTT-4.6.0-6] A Server MUST treat every Topic as an Ordered Topic when it is forwarding messages on Non-shared
    * Subscriptions.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testOrderedDeliveryForPipelinedPublisherQoS1() throws Exception {
      testOrderedDeliveryForPipelinedPublisher(1);
   }

   /**
    * [MQTT-4.6.0-5] When a Server processes a message that has been published to an Ordered Topic, it MUST send
    * PUBLISH packets to consumers (for the same Topic and QoS) in the order that they were received from any given
    * Client.
    * <p>
    * [MQTT-4.6.0-6] A Server MUST treat every Topic as an Ordered Topic when it is forwarding messages on Non-shared
    * Subscriptions.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testOrderedDeliveryForPipelinedPublisherQoS2() throws Exception {
      testOrderedDeliveryForPipelinedPublisher(2);
   }

   /**
    * Publishes batches of messages without waiting for an acknowledgement between each PUBLISH and verifies that the
    * broker forwards them to a non-shared subscription in publication order.
    */
   private void testOrderedDeliveryForPipelinedPublisher(int qos) throws Exception {
      final String TOPIC = this.getTopicName();
      final List<Integer> received = new CopyOnWriteArrayList<>();
      final CountDownLatch[] batchLatch = new CountDownLatch[1];

      MqttClient consumer = createPahoClient(RandomUtil.randomUUIDString());
      consumer.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) {
            received.add(Integer.parseInt(new String(message.getPayload(), StandardCharsets.UTF_8)));
            batchLatch[0].countDown();
         }
      });
      MqttConnectionOptions options = new MqttConnectionOptionsBuilder()
         .cleanStart(true)
         .build();
      consumer.connect(options);
      consumer.subscribe(TOPIC, qos);

      /*
       * Use the asynchronous client so that every PUBLISH in a batch is written to the socket without waiting for the
       * corresponding PUBACK/PUBCOMP. Paho still writes the packets in the order they were submitted.
       */
      MqttAsyncClient producer = createAsyncPahoClient(RandomUtil.randomUUIDString());
      producer.connect().waitForCompletion(DEFAULT_TIMEOUT_SEC * 1000);

      List<String> failures = new ArrayList<>();
      try {
         for (int batch = 0; batch < BATCH_COUNT; batch++) {
            final int base = batch * BATCH_SIZE;
            received.clear();
            batchLatch[0] = new CountDownLatch(BATCH_SIZE);

            List<Integer> expected = new ArrayList<>(BATCH_SIZE);
            for (int i = 0; i < BATCH_SIZE; i++) {
               expected.add(base + i);
               producer.publish(TOPIC, String.valueOf(base + i).getBytes(StandardCharsets.UTF_8), qos, false);
            }

            assertTrue(batchLatch[0].await(DEFAULT_TIMEOUT_SEC, TimeUnit.SECONDS), "Timed out waiting for batch " + batch + "; expected " + expected + " but received " + received);

            List<Integer> actual = new ArrayList<>(received);
            if (!actual.equals(expected)) {
               failures.add("batch " + batch + ": expected " + expected + " but received " + actual);
            }
         }
      } finally {
         producer.disconnect().waitForCompletion(DEFAULT_TIMEOUT_SEC * 1000);
         producer.close();
         consumer.disconnect();
         consumer.close();
      }

      if (!failures.isEmpty()) {
         fail(failures.size() + " of " + BATCH_COUNT + " QoS " + qos + " batches were delivered out of order:" + System.lineSeparator() + String.join(System.lineSeparator(), failures.subList(0, Math.min(10, failures.size()))));
      }
   }
}
