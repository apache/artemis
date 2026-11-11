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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.tests.integration.mqtt5.MQTT5TestSupport;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/*
 * Fulfilled by client or Netty codec (i.e. not tested here):
 *
 * [MQTT-4.5.0-2] The Client MUST acknowledge any Publish packet it receives according to the applicable QoS rules regardless of whether it elects to process the Application Message that it contains.
 */

public class MessageReceiptTests extends MQTT5TestSupport {

   /**
    * [MQTT-4.5.0-1] When a Server takes ownership of an incoming Application Message it MUST add it to the Session
    * State for those Clients that have matching Subscriptions.
    *
    * The spec here is speaking in terms of an implementation detail. To be clear, messages are not added "to the
    * Session State" specifically. They are added to the queue(s) associated with the matching client subscriptions.
    *
    * This test is pretty generic. It just creates a bunch of individual consumers and sends a message to each one.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testMessageReceipt() throws Exception {
      final String TOPIC = RandomUtil.randomUUIDString();
      final String CONSUMER_ID = "consumer";
      final int CONSUMER_COUNT = 25;
      final MqttClient[] consumers = new MqttClient[CONSUMER_COUNT];

      final CountDownLatch latch = new CountDownLatch(CONSUMER_COUNT);
      for (int i = 0; i < CONSUMER_COUNT; i++) {
         MqttClient consumer = createPahoClient(CONSUMER_ID + i);
         consumers[i] = consumer;
         consumer.connect();
         int finalI = i;
         consumer.setCallback(new DefaultMqttCallback() {
            @Override
            public void messageArrived(String incomingTopic, MqttMessage message) throws Exception {
               System.out.println("=== Message: " + message + " from: " + incomingTopic);
               assertEquals(TOPIC + finalI, incomingTopic);
               assertEquals("hello" + finalI, new String(message.getPayload()));
               latch.countDown();
            }
         });
         consumer.subscribe(TOPIC + i, 0);
      }

      MqttClient producer = createPahoClient("producer");
      producer.connect();
      for (int i = 0; i < CONSUMER_COUNT; i++) {
         producer.publish(TOPIC + i, ("hello" + i).getBytes(), 0, false);
      }
      Wait.assertEquals((long) CONSUMER_COUNT, () -> {
         int totalMessagesAdded = 0;
         for (int i = 0; i < CONSUMER_COUNT; i++) {
            totalMessagesAdded += getSubscriptionQueue(TOPIC + i, CONSUMER_ID + i).getMessagesAdded();
         }
         return totalMessagesAdded;
      }, 2000, 100);
      producer.disconnect();
      producer.close();

      assertTrue(latch.await(30, TimeUnit.SECONDS));
      for (int i = 0; i < CONSUMER_COUNT; i++) {
         consumers[i].disconnect();
         consumers[i].close();
      }
   }
}
