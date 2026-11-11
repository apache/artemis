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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.handler.codec.mqtt.MqttMessageIdAndPropertiesVariableHeader;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttPubAckMessage;
import io.netty.handler.codec.mqtt.MqttPubReplyMessageVariableHeader;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttSubscribeMessage;
import io.netty.handler.codec.mqtt.MqttUnsubscribeMessage;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTInterceptor;
import org.apache.activemq.artemis.tests.integration.mqtt5.MQTT5TestSupport;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/*
 * Fulfilled by client or Netty codec (i.e. not explicitly tested here):
 *
 * [MQTT-2.1.3-1] Where a flag bit is marked as “Reserved” it is reserved for future use and MUST be set to the value listed.
 * [MQTT-2.2.2-1] If there are no properties, this MUST be indicated by including a Property Length of zero.
 * [MQTT-2.2.1-3] Each time a Client sends a new SUBSCRIBE, UNSUBSCRIBE,or PUBLISH (where QoS > 0) MQTT Control Packet it MUST assign it a non-zero Packet Identifier that is currently unused.
 */

public class ControlPacketFormatTests extends MQTT5TestSupport {

   /*
    * [MQTT-2.2.1-2] A PUBLISH packet MUST NOT contain a Packet Identifier if its QoS value is set to 0.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testPacketIdQoSZero() throws Exception {
      final String TOPIC = this.getTopicName();
      final String CONSUMER_CLIENT_ID = "consumer";
      final int MESSAGE_COUNT = 100;

      final CountDownLatch latch = new CountDownLatch(MESSAGE_COUNT);
      MqttClient consumer = createPahoClient(CONSUMER_CLIENT_ID);
      consumer.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            assertEquals(0, message.getId());
            assertEquals(0, message.getQos());
            latch.countDown();
         }
      });
      consumer.connect();
      consumer.subscribe(TOPIC, 0);

      MqttClient producer = createPahoClient("producer");
      producer.connect();
      for (int i = 0; i < MESSAGE_COUNT; i++) {
         producer.publish(TOPIC, ("foo" + i).getBytes(), 0, false);
      }
      Wait.assertEquals(MESSAGE_COUNT, () -> getSubscriptionQueue(TOPIC, CONSUMER_CLIENT_ID).getMessagesAdded());
      producer.disconnect();
      producer.close();

      assertTrue(latch.await(3, TimeUnit.SECONDS));
      consumer.disconnect();
      consumer.close();
   }

   /*
    * [MQTT-2.2.1-4] Each time a Server sends a new PUBLISH (with QoS > 0) MQTT Control Packet it MUST assign it a non
    * zero Packet Identifier that is currently unused.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testPacketIdQoSGreaterThanZero() throws Exception {
      final String CONSUMER_ID = RandomUtil.randomUUIDString();
      final String TOPIC = this.getTopicName();
      final int MESSAGE_COUNT = 10;
      final List IDS = new ArrayList();
      final Object lock = new Object();

      final CountDownLatch latch = new CountDownLatch(MESSAGE_COUNT);
      MqttClient consumer = createPahoClient(CONSUMER_ID);
      consumer.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            synchronized (lock) {
               System.out.println(message.getId());
               assertFalse(IDS.contains(message.getId()));
               IDS.add(message.getId());
               latch.countDown();
            }
         }
      });
      consumer.connect();
      consumer.subscribe(TOPIC, 2);
      Wait.assertTrue(() -> getSubscriptionQueue(TOPIC, CONSUMER_ID) != null);
      Wait.assertEquals(1, () -> getSubscriptionQueue(TOPIC, CONSUMER_ID).getConsumerCount());

      MqttClient producer = createPahoClient("producer");
      producer.connect();
      for (int i = 0; i < MESSAGE_COUNT; i++) {
         producer.publish(TOPIC, ("foo" + i).getBytes(), (RandomUtil.randomPositiveInt() % 2) + 1, false);
      }
      Wait.assertEquals(MESSAGE_COUNT, () -> getSubscriptionQueue(TOPIC, CONSUMER_ID).getMessagesAdded());
      producer.disconnect();
      producer.close();

      assertTrue(latch.await(3, TimeUnit.SECONDS));
      consumer.disconnect();
      consumer.close();
   }

   /*
    * [MQTT-2.2.1-5] A PUBACK, PUBREC, PUBREL, or PUBCOMP packet MUST contain the same Packet Identifier as the PUBLISH
    * packet that was originally sent.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testPacketIdPubAckQoS2() throws Exception {
      AtomicInteger id = new AtomicInteger(0);
      AtomicBoolean failed = new AtomicBoolean(false);
      AtomicInteger packetCount = new AtomicInteger(0);

      MQTTInterceptor incomingInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBLISH) {
            id.set(((MqttPublishMessage)packet).variableHeader().packetId());
            packetCount.incrementAndGet();
         }
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBREL || packet.fixedHeader().messageType() == MqttMessageType.PUBREC || packet.fixedHeader().messageType() == MqttMessageType.PUBCOMP) {
            if (((MqttPubReplyMessageVariableHeader)packet.variableHeader()).messageId() != id.get()) {
               failed.set(true);
            }
            packetCount.incrementAndGet();
         }
         return true;
      };

      MQTTInterceptor outgoingInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBLISH) {
            if (((MqttPublishMessage)packet).variableHeader().packetId() != id.get()) {
               failed.set(true);
            }
            packetCount.incrementAndGet();
         }
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBREL || packet.fixedHeader().messageType() == MqttMessageType.PUBREC || packet.fixedHeader().messageType() == MqttMessageType.PUBCOMP) {
            if (((MqttPubAckMessage)packet).variableHeader().messageId() != id.get()) {
               failed.set(true);
            }
            packetCount.incrementAndGet();
         }
         return true;
      };
      server.getRemotingService().addIncomingInterceptor(incomingInterceptor);
      server.getRemotingService().addOutgoingInterceptor(outgoingInterceptor);

      final String TOPIC = this.getTopicName();

      final CountDownLatch latch = new CountDownLatch(1);
      final String CONSUMER_ID = "consumer";
      MqttClient consumer = createPahoClient(CONSUMER_ID);
      consumer.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            latch.countDown();
         }
      });
      consumer.connect();
      consumer.subscribe(TOPIC, 2);

      MqttClient producer = createPahoClient("producer");
      producer.connect();
      producer.publish(TOPIC, "foo".getBytes(StandardCharsets.UTF_8), 2, false);
      Wait.assertEquals((long) 1, () -> getSubscriptionQueue(TOPIC, CONSUMER_ID).getMessagesAdded(), 2000, 100);
      producer.disconnect();
      producer.close();

      Wait.assertEquals(1L, () -> getSubscriptionQueue(TOPIC, CONSUMER_ID).getMessagesAcknowledged(), 15000, 100);
      assertTrue(latch.await(15, TimeUnit.SECONDS));
      Wait.assertFalse(() -> failed.get(), 2000, 100);
      Wait.assertEquals(8, () -> packetCount.get());
      consumer.disconnect();
      consumer.close();
   }

   /*
    * [MQTT-2.2.1-5] A PUBACK, PUBREC, PUBREL, or PUBCOMP packet MUST contain the same Packet Identifier as the PUBLISH
    * packet that was originally sent.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testPacketIdPubAckQoS1() throws Exception {
      AtomicInteger id = new AtomicInteger(0);
      AtomicBoolean failed = new AtomicBoolean(false);
      AtomicInteger packetCount = new AtomicInteger(0);

      MQTTInterceptor incomingInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBLISH) {
            id.set(((MqttPublishMessage)packet).variableHeader().packetId());
            packetCount.incrementAndGet();
         }
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBACK) {
            if (((MqttPubReplyMessageVariableHeader)packet.variableHeader()).messageId() != id.get()) {
               failed.set(true);
            }
            packetCount.incrementAndGet();
         }
         return true;
      };

      MQTTInterceptor outgoingInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBLISH) {
            if (((MqttPublishMessage)packet).variableHeader().packetId() != id.get()) {
               failed.set(true);
            }
            packetCount.incrementAndGet();
         }
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBACK) {
            if (((MqttPubAckMessage)packet).variableHeader().messageId() != id.get()) {
               failed.set(true);
            }
            packetCount.incrementAndGet();
         }
         return true;
      };
      server.getRemotingService().addIncomingInterceptor(incomingInterceptor);
      server.getRemotingService().addOutgoingInterceptor(outgoingInterceptor);

      final String TOPIC = this.getTopicName();

      final CountDownLatch latch = new CountDownLatch(1);
      final String CONSUMER_ID = "consumer";
      MqttClient consumer = createPahoClient(CONSUMER_ID);
      consumer.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            latch.countDown();
         }
      });
      consumer.connect();
      consumer.subscribe(TOPIC, 1);

      MqttClient producer = createPahoClient("producer");
      producer.connect();
      producer.publish(TOPIC, "foo".getBytes(StandardCharsets.UTF_8), 1, false);
      Wait.assertEquals((long) 1, () -> getSubscriptionQueue(TOPIC, CONSUMER_ID).getMessagesAdded(), 2000, 100);
      producer.disconnect();
      producer.close();

      Wait.assertEquals(1L, () -> getSubscriptionQueue(TOPIC, CONSUMER_ID).getMessagesAcknowledged(), 15000, 100);
      assertTrue(latch.await(15, TimeUnit.SECONDS));
      Wait.assertFalse(() -> failed.get(), 2000, 100);
      Wait.assertEquals(4, () -> packetCount.get());
      consumer.disconnect();
      consumer.close();
   }

   /*
    * [MQTT-2.2.1-6] A SUBACK and UNSUBACK MUST contain the Packet Identifier that was used in the corresponding
    * SUBSCRIBE and UNSUBSCRIBE packet respectively.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testPacketIdSubAckAndUnsubAck() throws Exception {
      AtomicInteger subId = new AtomicInteger(0);
      AtomicInteger unsubId = new AtomicInteger(0);
      AtomicInteger packetCount = new AtomicInteger(0);
      AtomicBoolean failed = new AtomicBoolean(false);

      MQTTInterceptor incomingInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.SUBSCRIBE) {
            subId.set(((MqttSubscribeMessage)packet).variableHeader().messageId());
            packetCount.incrementAndGet();
         }
         if (packet.fixedHeader().messageType() == MqttMessageType.UNSUBSCRIBE) {
            unsubId.set(((MqttUnsubscribeMessage)packet).variableHeader().messageId());
            packetCount.incrementAndGet();
         }
         return true;
      };

      MQTTInterceptor outgoingInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.SUBACK) {
            if (((MqttMessageIdAndPropertiesVariableHeader)packet.variableHeader()).messageId() != subId.get()) {
               failed.set(true);
            }
            packetCount.incrementAndGet();
         } else if (packet.fixedHeader().messageType() == MqttMessageType.UNSUBACK) {
            if (((MqttMessageIdAndPropertiesVariableHeader)packet.variableHeader()).messageId() != unsubId.get()) {
               failed.set(true);
            }
            packetCount.incrementAndGet();
         }
         return true;
      };
      server.getRemotingService().addIncomingInterceptor(incomingInterceptor);
      server.getRemotingService().addOutgoingInterceptor(outgoingInterceptor);

      final String TOPIC = this.getTopicName();

      MqttClient consumer = createPahoClient("consumer");
      consumer.connect();
      consumer.subscribe(TOPIC, 1);
      consumer.unsubscribe(TOPIC);
      Wait.assertFalse(() -> failed.get(), 2000, 100);
      Wait.assertEquals(4, () -> packetCount.get());
      consumer.disconnect();
      consumer.close();
   }
}
