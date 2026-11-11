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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.tests.integration.mqtt5.MQTT5TestSupport;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptionsBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class MessageDeliveryRetryTests extends MQTT5TestSupport {

   /*
    * [MQTT-4.4.0-1] When a Client reconnects with Clean Start set to 0 and a session is present, both the Client and
    * Server MUST resend any unacknowledged PUBLISH packets (where QoS > 0) and PUBREL packets using their original
    * Packet Identifiers. This is the only circumstance where a Client or Server is REQUIRED to resend messages.
    * Clients and Servers MUST NOT resend messages at any other time.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testCleanStartFalseWithReconnect() throws Exception {
      final String CONSUMER_ID = RandomUtil.randomUUIDString();
      final String TOPIC = this.getTopicName();

      final CountDownLatch latch = new CountDownLatch(1);
      MqttClient producer = createPahoClient(RandomUtil.randomUUIDString());
      MqttClient consumer = createPahoClient(CONSUMER_ID);
      MqttConnectionOptions options = new MqttConnectionOptionsBuilder()
         .cleanStart(false)
         .sessionExpiryInterval(300L)
         .build();
      consumer.connect(options);
      consumer.subscribe(TOPIC, 2);
      consumer.disconnect();

      // session should still exist since session expiry interval > 0
      assertEquals(1, getSessionStates().size());
      assertNotNull(getSessionStates().get(CONSUMER_ID));

      producer.connect();
      producer.publish(TOPIC, "hello".getBytes(), 2, false);
      producer.disconnect();
      producer.close();

      // session should still exist since session expiry interval > 0
      assertEquals(1, getSessionStates().size());
      assertNotNull(getSessionStates().get(CONSUMER_ID));

      // consumer should resume previous session (i.e. get the messages sent to the queue where it was previously subscribed)
      consumer.setCallback(new LatchedMqttCallback(latch));
      consumer.connect(options);
      waitForLatch(latch);
      consumer.disconnect();
      consumer.close();

      // session should still exist since session expiry interval > 0
      assertEquals(1, getSessionStates().size());
      assertNotNull(getSessionStates().get(CONSUMER_ID));
   }

   /*
    * [MQTT-4.4.0-2] If PUBACK or PUBREC is received containing a Reason Code of 0x80 or greater the corresponding
    * PUBLISH packet is treated as acknowledged, and MUST NOT be retransmitted.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testTopicFilter() throws Exception {
      final String CONSUMER_ID = RandomUtil.randomUUIDString();
      final String TOPIC = this.getTopicName();

      final CountDownLatch latch = new CountDownLatch(1);
      MqttClient consumer = createPahoClient(CONSUMER_ID);
      consumer.connect();
      consumer.setCallback(new LatchedMqttCallback(latch));
      consumer.subscribe(TOPIC, 1);

      MqttClient producer = createPahoClient("producer");
      producer.connect();
      producer.publish(TOPIC, "hello".getBytes(), 1, false);
      producer.disconnect();
      producer.close();

      assertTrue(latch.await(1, TimeUnit.SECONDS));
      consumer.disconnect();
      consumer.close();
   }
}
