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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.core.protocol.mqtt.MQTTUtil;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.tests.integration.mqtt5.MQTT5TestSupport;
import org.apache.activemq.artemis.utils.Wait;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/*
 * Fulfilled by client or Netty codec (i.e. not tested here):
 *
 * [MQTT-4.8.2-1] A Shared Subscription's Topic Filter MUST start with $share/ and MUST contain a ShareName that is at least one character long.
 * [MQTT-4.8.2-2] The ShareName MUST NOT contain the characters "/", "+" or "#", but MUST be followed by a "/" character. This "/" character MUST be followed by a Topic Filter.
 *
 *
 * These requirements are related to shared subscriptions and consumption via QoS 2. These are not tested:
 *
 * [MQTT-4.8.2-4] The Server MUST complete the delivery of the message to that Client when it reconnects.
 * [MQTT-4.8.2-5] If the Client's Session terminates before the Client reconnects, the Server MUST NOT send the Application Message to any other subscribed Client.
 */

public class SubscriptionTests extends MQTT5TestSupport {

   /**
    * [MQTT-4.8.2-3] The Server MUST respect the granted QoS for the Client's subscription.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testSharedSubscriptionRespectQoS() throws Exception {
      final String TOPIC = "myTopic";
      final String SUB_NAME = "myShare";
      final String SHARED_SUB = MQTTUtil.SHARED_SUBSCRIPTION_PREFIX + SUB_NAME + "/" + TOPIC;
      final int MESSAGE_COUNT = 100;
      final AtomicInteger consumer1MessagesReceived = new AtomicInteger(0);
      final AtomicInteger consumer2MessagesReceived = new AtomicInteger(0);

      MqttClient consumer1 = createPahoClient("consumer1");
      consumer1.connect();
      consumer1.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String incomingTopic, MqttMessage message) throws Exception {
            if (message.getQos() == 0) {
               consumer1MessagesReceived.incrementAndGet();
            } else {
               fail("Wrong QoS for consumer 1: " + message.getId() + " " + message.getQos());
            }
         }
      });
      consumer1.subscribe(SHARED_SUB, 0);

      Queue sharedSubQueue = server.locateQueue(SUB_NAME.concat(".").concat(TOPIC));
      assertNotNull(sharedSubQueue);
      assertEquals(TOPIC, sharedSubQueue.getAddress().toString());

      MqttClient consumer2 = createPahoClient("consumer2");
      consumer2.connect();
      consumer2.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String incomingTopic, MqttMessage message) throws Exception {
            if (message.getQos() == 1) {
               consumer2MessagesReceived.incrementAndGet();
            } else {
               fail("Wrong QoS for consumer 2: " + message.getId() + " " + message.getQos());
            }
         }
      });
      consumer2.subscribe(SHARED_SUB, 1);

      assertEquals(2, sharedSubQueue.getConsumerCount());

      MqttClient producer = createPahoClient("producer");
      producer.connect();
      for (int i = 0; i < MESSAGE_COUNT; i++) {
         producer.publish(TOPIC, new byte[0], 1, false);
      }
      producer.disconnect();
      producer.close();

      Wait.assertTrue(() -> consumer1MessagesReceived.get() > 0, 2000, 100);
      Wait.assertTrue(() -> consumer2MessagesReceived.get() > 0, 2000, 100);
      Wait.assertEquals(MESSAGE_COUNT, () -> consumer1MessagesReceived.get() + consumer2MessagesReceived.get(), 2000, 100);
      consumer1.disconnect();
      consumer1.close();
      consumer2.disconnect();
      consumer2.close();
   }

   /**
    * [MQTT-4.8.2-6] If a Client responds with a PUBACK or PUBREC containing a Reason Code of 0x80 or greater to a
    * PUBLISH packet from the Server, the Server MUST discard the Application Message and not attempt to send it to any
    * other Subscriber.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testSharedSubscriptionWithAck() throws Exception {
      final String TOPIC = "myTopic";
      final String SUB_NAME = "myShare";
      final String SHARED_SUB = MQTTUtil.SHARED_SUBSCRIPTION_PREFIX + SUB_NAME + "/" + TOPIC;
      CountDownLatch ackLatch = new CountDownLatch(1);
      CountDownLatch negativeAckLatch = new CountDownLatch(1);

      MqttClient consumer1 = createPahoClient("consumer1");
      consumer1.connect();
      consumer1.setCallback(new LatchedMqttCallback(ackLatch));
      consumer1.subscribe(SHARED_SUB, 1);

      Queue sharedSubQueue = server.locateQueue(SUB_NAME.concat(".").concat(TOPIC));
      assertNotNull(sharedSubQueue);
      assertEquals(TOPIC, sharedSubQueue.getAddress().toString());
      assertEquals(1, sharedSubQueue.getConsumerCount());

      MqttClient producer = createPahoClient("producer");
      producer.connect();
      producer.publish(TOPIC, new byte[0], 1, false);
      producer.disconnect();
      producer.close();

      MqttClient consumer2 = createPahoClient("consumer2");
      consumer2.connect();
      consumer2.setCallback(new LatchedMqttCallback(negativeAckLatch));
      consumer2.subscribe(SHARED_SUB, 1);

      assertTrue(ackLatch.await(2, TimeUnit.SECONDS));
      assertFalse(negativeAckLatch.await(2, TimeUnit.SECONDS));
      consumer1.disconnect();
      consumer1.close();
      consumer2.disconnect();
      consumer2.close();
   }
}
