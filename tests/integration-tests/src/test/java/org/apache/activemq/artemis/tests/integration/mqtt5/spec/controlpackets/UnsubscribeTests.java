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
package org.apache.activemq.artemis.tests.integration.mqtt5.spec.controlpackets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.handler.codec.mqtt.MqttMessageIdAndPropertiesVariableHeader;
import io.netty.handler.codec.mqtt.MqttMessageType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTInterceptor;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.tests.integration.mqtt5.MQTT5TestSupport;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/*
 * Fulfilled by client or Netty codec (i.e. not tested here):
 *
 * [MQTT-3.10.1-1] Bits 3,2,1 and 0 of the Fixed Header of the UNSUBSCRIBE packet are reserved and MUST be set to 0,0,1 and 0 respectively. The Server MUST treat any other value as malformed and close the Network Connection
 * [MQTT-3.10.3-1] The Topic Filters in an UNSUBSCRIBE packet MUST be UTF-8 Encoded Strings.
 * [MQTT-3.10.3-2] The Payload of an UNSUBSCRIBE packet MUST contain at least one Topic Filter.
 *
 *
 * Unsure how to test:
 *
 * [MQTT-3.10.4-3] When a Server receives UNSUBSCRIBE It MUST complete the delivery of any QoS 1 or QoS 2 messages which match the Topic Filters and it has started to send to the Client.
 */

public class UnsubscribeTests extends MQTT5TestSupport {

   /*
    * [MQTT-3.10.4-1] The Topic Filters (whether they contain wildcards or not) supplied in an UNSUBSCRIBE packet MUST
    * be compared character-by-character with the current set of Topic Filters held by the Server for the Client. If any
    * filter matches exactly then its owning Subscription MUST be deleted.
    *
    * [MQTT-3.10.4-6] If a Server receives an UNSUBSCRIBE packet that contains multiple Topic Filters, it MUST process
    * that packet as if it had received a sequence of multiple UNSUBSCRIBE packets, except that it sends just one
    * UNSUBACK response.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testUnsubscribe() throws Exception {
      final int SUBSCRIPTION_COUNT = 30;
      final String TOPIC = RandomUtil.randomUUIDString();
      final AtomicInteger unsubAckCount = new AtomicInteger(0);
      SimpleString[] topicNames = new SimpleString[SUBSCRIPTION_COUNT];
      for (int i = 0; i < SUBSCRIPTION_COUNT; i++) {
         topicNames[i] = SimpleString.of(i + "-" + TOPIC);
      }

      MQTTInterceptor outgoingInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.UNSUBACK) {
            unsubAckCount.incrementAndGet();
         }
         return true;
      };
      server.getRemotingService().addOutgoingInterceptor(outgoingInterceptor);

      MqttClient consumer = createPahoClient("consumer");
      consumer.connect();
      MqttSubscription[] subscriptions = new MqttSubscription[SUBSCRIPTION_COUNT];
      for (int i = 0; i < SUBSCRIPTION_COUNT; i++) {
         subscriptions[i] = new MqttSubscription(topicNames[i].toString(), 0);
      }
      consumer.subscribe(subscriptions);

      for (int i = 0; i < SUBSCRIPTION_COUNT; i++) {
         assertTrue(server.getPostOffice().isAddressBound(topicNames[i]));
      }

      // unsubscribe from half of the subscriptions
      String[] unsubTopicNames = new String[SUBSCRIPTION_COUNT / 2];
      for (int i = 0; i < SUBSCRIPTION_COUNT / 2; i++) {
         unsubTopicNames[i] = topicNames[i].toString();
      }
      consumer.unsubscribe(unsubTopicNames);
      for (int i = 0; i < SUBSCRIPTION_COUNT / 2; i++) {
         assertFalse(server.getPostOffice().isAddressBound(topicNames[i]));
      }

      // make sure the other half are still there
      for (int i = SUBSCRIPTION_COUNT / 2; i < SUBSCRIPTION_COUNT; i++) {
         assertTrue(server.getPostOffice().isAddressBound(topicNames[i]));
      }

      assertEquals(1, unsubAckCount.get());

      consumer.disconnect();
      consumer.close();
   }

   /*
    * [MQTT-3.10.4-2] When a Server receives UNSUBSCRIBE It MUST stop adding any new messages which match the Topic
    * Filters, for delivery to the Client.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testStopAddingMessagesOnUnsubscribe() throws Exception {
      final String TOPIC = RandomUtil.randomUUIDString();

      MqttClient consumer1 = createPahoClient("consumer1");
      consumer1.connect();
      consumer1.subscribe(TOPIC, 0);

      MqttClient consumer2 = createPahoClient("consumer2");
      consumer2.connect();
      consumer2.subscribe(TOPIC, 0);

      Queue consumer1SubscriptionQueue = getSubscriptionQueue(TOPIC, "consumer1");
      Queue consumer2SubscriptionQueue = getSubscriptionQueue(TOPIC, "consumer2");

      MqttClient producer = createPahoClient("producer");
      producer.connect();
      producer.publish(TOPIC, new byte[0], 0, false);

      Wait.assertEquals(1L, () -> consumer1SubscriptionQueue.getMessagesAdded(), 2000, 100);
      Wait.assertEquals(1L, () -> consumer2SubscriptionQueue.getMessagesAdded(), 2000, 100);

      consumer2.unsubscribe(TOPIC);

      producer.publish(TOPIC, new byte[0], 0, false);

      producer.disconnect();
      producer.close();

      Wait.assertEquals(2L, () -> consumer1SubscriptionQueue.getMessagesAdded(), 2000, 100);
      Wait.assertTrue(() -> getSubscriptionQueue(TOPIC, "consumer2") == null);

      consumer1.disconnect();
      consumer1.close();
      consumer2.disconnect();
      consumer2.close();
   }

   /*
    * [MQTT-3.10.4-4] The Server MUST respond to an UNSUBSCRIBE request by sending an UNSUBACK packet.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testUnsubAck() throws Exception {
      final String TOPIC = RandomUtil.randomUUIDString();
      final AtomicBoolean unsubscribed = new AtomicBoolean(false);
      final CountDownLatch latch = new CountDownLatch(1);

      MQTTInterceptor incomingInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.UNSUBSCRIBE) {
            unsubscribed.set(true);
         }
         return true;
      };

      MQTTInterceptor outgoingInterceptor = (packet, connection) -> {
         if (unsubscribed.get() && packet.fixedHeader().messageType() == MqttMessageType.UNSUBACK) {
            latch.countDown();
         }
         return true;
      };
      server.getRemotingService().addIncomingInterceptor(incomingInterceptor);
      server.getRemotingService().addOutgoingInterceptor(outgoingInterceptor);


      MqttClient consumer = createPahoClient("consumer");
      consumer.connect();
      consumer.subscribe(TOPIC, 0);
      consumer.unsubscribe(TOPIC);

      assertTrue(latch.await(2, TimeUnit.SECONDS));

      consumer.disconnect();
      consumer.close();
   }

   /*
    * [MQTT-3.10.4-5] The UNSUBACK packet MUST have the same Packet Identifier as the UNSUBSCRIBE packet. Even where no
    * Topic Subscriptions are deleted, the Server MUST respond with an UNSUBACK.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testUnsubAckPacketId() throws Exception {
      final String TOPIC = RandomUtil.randomUUIDString();
      final AtomicBoolean unsubscribed = new AtomicBoolean(false);
      final AtomicInteger packetId = new AtomicInteger(0);
      final CountDownLatch latch = new CountDownLatch(1);

      MQTTInterceptor incomingInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.UNSUBSCRIBE) {
            unsubscribed.set(true);
            packetId.set(((MqttMessageIdAndPropertiesVariableHeader)packet.variableHeader()).messageId());
         }
         return true;
      };

      MQTTInterceptor outgoingInterceptor = (packet, connection) -> {
         if (unsubscribed.get() && packet.fixedHeader().messageType() == MqttMessageType.UNSUBACK) {
            assertEquals(packetId.get(), ((MqttMessageIdAndPropertiesVariableHeader)packet.variableHeader()).messageId());
            latch.countDown();
         }
         return true;
      };
      server.getRemotingService().addIncomingInterceptor(incomingInterceptor);
      server.getRemotingService().addOutgoingInterceptor(outgoingInterceptor);


      MqttClient consumer = createPahoClient("consumer");
      consumer.connect();
      consumer.subscribe(TOPIC, 0);
      consumer.unsubscribe(TOPIC);

      assertTrue(latch.await(2, TimeUnit.SECONDS));

      consumer.disconnect();
      consumer.close();
   }
}
