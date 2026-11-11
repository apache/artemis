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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.Mqtt5ClientConfig;
import com.hivemq.client.mqtt.mqtt5.advanced.interceptor.qos2.Mqtt5IncomingQos2Interceptor;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.client.mqtt.mqtt5.message.publish.pubcomp.Mqtt5PubCompBuilder;
import com.hivemq.client.mqtt.mqtt5.message.publish.pubrec.Mqtt5PubRecBuilder;
import com.hivemq.client.mqtt.mqtt5.message.publish.pubrec.Mqtt5PubRecReasonCode;
import com.hivemq.client.mqtt.mqtt5.message.publish.pubrel.Mqtt5PubRel;
import io.reactivex.schedulers.Schedulers;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttPubAckMessage;
import io.netty.handler.codec.mqtt.MqttPubReplyMessageVariableHeader;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTInterceptor;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTReasonCodes;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTUtil;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.tests.integration.mqtt5.MQTT5TestSupport;
import org.apache.activemq.artemis.utils.ByteUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/*
 * Fulfilled by client or Netty codec (i.e. not tested here):
 *
 * [MQTT-4.3.1-1] In the QoS 0 delivery protocol, the sender MUST send a PUBLISH packet with QoS 0 and DUP flag set to 0.
 * [MQTT-4.3.2-1] In the QoS 1 delivery protocol, the sender MUST assign an unused Packet Identifier each time it has a new Application Message to publish.
 * [MQTT-4.3.3-1] In the QoS 2 delivery protocol, the sender MUST assign an unused Packet Identifier when it has a new Application Message to publish.
 * [MQTT-4.3.3-2] In the QoS 2 delivery protocol, the sender MUST send a PUBLISH packet containing this Packet Identifier with QoS 2 and DUP flag set to 0.
 *
 *
 * Unsure how to test:
 *
 * [MQTT-4.3.2-5] In the QoS 1 delivery protocol, the receiver after it has sent a PUBACK packet the receiver MUST treat any incoming PUBLISH packet that contains the same Packet Identifier as being a new Application Message, irrespective of the setting of its DUP flag.
 * [MQTT-4.3.3-6] In the QoS 2 delivery protocol, the sender MUST NOT re-send the PUBLISH once it has sent the corresponding PUBREL packet.
 * [MQTT-4.3.3-9] In the QoS 2 delivery protocol, the receiver if it has sent a PUBREC with a Reason Code of 0x80 or greater, the receiver MUST treat any subsequent PUBLISH packet that contains that Packet Identifier as being a new Application Message.
 * [MQTT-4.3.3-12] In the QoS 2 delivery protocol, the receiver After it has sent a PUBCOMP, the receiver MUST treat any subsequent PUBLISH packet that contains that Packet Identifier as being a new Application Message.
 */

public class QoSTests extends MQTT5TestSupport {

   /*
    * [MQTT-4.3.2-2] In the QoS 1 delivery protocol, the sender MUST send a PUBLISH packet containing this Packet
    * Identifier with QoS 1 and DUP flag set to 0.
    *
    * This test looks at the PUBLISH packet coming from *the broker* to the client
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testQoS1andDupFlag() throws Exception {
      final String TOPIC = RandomUtil.randomUUIDString();

      final CountDownLatch latch = new CountDownLatch(1);
      MqttClient consumer = createPahoClient("consumer");
      consumer.connect();
      consumer.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String incomingTopic, MqttMessage message) throws Exception {
            assertEquals(1, message.getQos());
            assertFalse(message.isDuplicate());
            latch.countDown();
         }
      });
      consumer.subscribe(TOPIC, 1);

      MqttClient producer = createPahoClient("producer");
      producer.connect();
      producer.publish(TOPIC, RandomUtil.randomUUIDString().getBytes(), 1, false);
      producer.disconnect();
      producer.close();

      assertTrue(latch.await(2, TimeUnit.SECONDS));
      consumer.disconnect();
      consumer.close();
   }

   /*
    * [MQTT-4.3.2-3] In the QoS 1 delivery protocol, the sender MUST treat the PUBLISH packet as “unacknowledged” until
    * it has received the corresponding PUBACK packet from the receiver.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testQoS1PubAck() throws Exception {
      final String TOPIC = RandomUtil.randomUUIDString();
      final String CONSUMER_ID = "consumer";
      final CountDownLatch ackLatch = new CountDownLatch(1);
      final AtomicInteger packetId = new AtomicInteger();

      MQTTInterceptor incomingInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBACK) {
            // ensure the message is still in the queue before we get the ack from the client
            assertEquals(1, getSubscriptionQueue(TOPIC, CONSUMER_ID).getMessageCount());
            assertEquals(1, getSubscriptionQueue(TOPIC, CONSUMER_ID).getDeliveringCount());

            // ensure the ids match so we know this is the "corresponding" PUBACK for the previous PUBLISH
            assertEquals(packetId.get(), ((MqttPubReplyMessageVariableHeader)packet.variableHeader()).messageId());

            ackLatch.countDown();
         }
         return true;
      };

      MQTTInterceptor outgoingInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBLISH) {
            packetId.set(((MqttPublishMessage)packet).variableHeader().packetId());
         }
         return true;
      };

      server.getRemotingService().addIncomingInterceptor(incomingInterceptor);
      server.getRemotingService().addOutgoingInterceptor(outgoingInterceptor);

      final CountDownLatch latch = new CountDownLatch(1);
      MqttClient consumer = createPahoClient(CONSUMER_ID);
      consumer.connect();
      consumer.setCallback(new LatchedMqttCallback(latch));
      consumer.subscribe(TOPIC, 1);

      MqttClient producer = createPahoClient("producer");
      producer.connect();
      producer.publish(TOPIC, RandomUtil.randomUUIDString().getBytes(), 1, false);
      producer.disconnect();
      producer.close();

      assertTrue(ackLatch.await(2, TimeUnit.SECONDS));
      assertTrue(latch.await(2, TimeUnit.SECONDS));
      assertEquals(0, getSubscriptionQueue(TOPIC, CONSUMER_ID).getMessageCount());
      assertEquals(0, getSubscriptionQueue(TOPIC, CONSUMER_ID).getDeliveringCount());
      consumer.disconnect();
      consumer.close();
   }

   /*
    * [MQTT-4.3.2-4] In the QoS 1 delivery protocol, the receiver MUST respond with a PUBACK packet containing the
    * Packet Identifier from the incoming PUBLISH packet, having accepted ownership of the Application Message.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testQoS1PubAckId() throws Exception {
      final String TOPIC = RandomUtil.randomUUIDString();
      final CountDownLatch ackLatch = new CountDownLatch(1);
      final AtomicInteger packetId = new AtomicInteger();

      MQTTInterceptor incomingInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBLISH) {
            packetId.set(((MqttPublishMessage)packet).variableHeader().packetId());
         }
         return true;
      };

      MQTTInterceptor outgoingInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBACK) {
            assertEquals(packetId.get(), ((MqttPubAckMessage)packet).variableHeader().messageId());
            ackLatch.countDown();
         }
         return true;
      };
      server.getRemotingService().addIncomingInterceptor(incomingInterceptor);
      server.getRemotingService().addOutgoingInterceptor(outgoingInterceptor);

      final CountDownLatch latch = new CountDownLatch(1);
      MqttClient consumer = createPahoClient("consumer");
      consumer.connect();
      consumer.setCallback(new LatchedMqttCallback(latch));
      consumer.subscribe(TOPIC, 1);

      MqttClient producer = createPahoClient("producer");
      producer.connect();
      producer.publish(TOPIC, RandomUtil.randomUUIDString().getBytes(), 1, false);
      producer.disconnect();
      producer.close();

      assertTrue(ackLatch.await(2, TimeUnit.SECONDS));
      assertTrue(latch.await(2, TimeUnit.SECONDS));
      consumer.disconnect();
      consumer.close();
   }

   /*
    * QoS 2 exactly-once delivery semantics. This diagram was adapted from:
    *   https://docs.oasis-open.org/mqtt/mqtt/v5.0/os/mqtt-v5.0-os.html#_Toc3901237
    *
    * ================================================================================
    * |      Sender Action      |   MQTT Control Packet    |     Receiver Action     |
    * |-------------------------|--------------------------|-------------------------|
    * | Store Message           |                          |                         |
    * --------------------------------------------------------------------------------
    * | PUBLISH QoS 2, DUP=0    |                          |                         |
    * | <Packet ID>             |                          |                         |
    * |-------------------------|--------------------------|-------------------------|
    * |                         |          =====>          |                         |
    * |-------------------------|--------------------------|-------------------------|
    * |                         |                          |Store <Packet ID> then   |
    * |                         |                          |initiate onward delivery |
    * |                         |                          |of the Application       |
    * |                         |                          |Message                  |
    * |-------------------------|--------------------------|-------------------------|
    * |                         |                          |PUBREC <Packet ID>       |
    * |                         |                          |<Reason Code>            |
    * |-------------------------|--------------------------|-------------------------|
    * |                         |          <=====          |                         |
    * |-------------------------|--------------------------|-------------------------|
    * | Discard message, store  |                          |                         |
    * | PUBREC <Packet ID>      |                          |                         |
    * |-------------------------|--------------------------|-------------------------|
    * | PUBREL <Packet ID>      |                          |                         |
    * |-------------------------|--------------------------|-------------------------|
    * |                         |          =====>          |                         |
    * |-------------------------|--------------------------|-------------------------|
    * |                         |                          |Discard <Packet ID>      |
    * |-------------------------|--------------------------|-------------------------|
    * |                         |                          |Send PUBCOMP <Packet ID> |
    * |-------------------------|--------------------------|-------------------------|
    * |                         |          <=====          |                         |
    * |-------------------------|--------------------------|-------------------------|
    * | Discard stored state    |                          |                         |
    * ================================================================================
    */

   /*
    * [MQTT-4.3.3-3] In the QoS 2 delivery protocol, the sender MUST treat the PUBLISH packet as “unacknowledged” until
    * it has received the corresponding PUBREC packet from the receiver.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testQoS2PubRec() throws Exception {
      final String TOPIC = RandomUtil.randomUUIDString();
      final String CONSUMER_ID = "consumer";
      final CountDownLatch ackLatch = new CountDownLatch(1);
      final AtomicInteger packetId = new AtomicInteger();

      MQTTInterceptor incomingInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBREC) {
            // ensure the message is still in the queue before we get the ack from the client
            assertEquals(1, getSubscriptionQueue(TOPIC, CONSUMER_ID).getMessageCount());
            assertEquals(1, getSubscriptionQueue(TOPIC, CONSUMER_ID).getDeliveringCount());

            // ensure the ids match so we know this is the "corresponding" PUBREC for the previous PUBLISH
            assertEquals(packetId.get(), ((MqttPubReplyMessageVariableHeader)packet.variableHeader()).messageId());

            ackLatch.countDown();
         }
         return true;
      };

      MQTTInterceptor outgoingInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBLISH) {
            packetId.set(((MqttPublishMessage)packet).variableHeader().packetId());
         }
         return true;
      };

      server.getRemotingService().addIncomingInterceptor(incomingInterceptor);
      server.getRemotingService().addOutgoingInterceptor(outgoingInterceptor);

      final CountDownLatch latch = new CountDownLatch(1);
      MqttClient consumer = createPahoClient(CONSUMER_ID);
      consumer.connect();
      consumer.setCallback(new LatchedMqttCallback(latch));
      consumer.subscribe(TOPIC, 2);

      MqttClient producer = createPahoClient("producer");
      producer.connect();
      producer.publish(TOPIC, RandomUtil.randomUUIDString().getBytes(), 2, false);
      producer.disconnect();
      producer.close();

      assertTrue(ackLatch.await(2, TimeUnit.SECONDS));
      assertTrue(latch.await(2, TimeUnit.SECONDS));
      assertEquals(0, getSubscriptionQueue(TOPIC, CONSUMER_ID).getMessageCount());
      assertEquals(0, getSubscriptionQueue(TOPIC, CONSUMER_ID).getDeliveringCount());
      consumer.disconnect();
      consumer.close();
   }

   /*
    * [MQTT-4.3.3-4] In the QoS 2 delivery protocol, the sender MUST send a PUBREL packet when it receives a PUBREC
    * packet from the receiver with a Reason Code value less than 0x80. This PUBREL packet MUST contain the same Packet
    * Identifier as the original PUBLISH packet.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testQoS2PubRelId() throws Exception {
      final String TOPIC = RandomUtil.randomUUIDString();
      final CountDownLatch ackLatch = new CountDownLatch(1);
      final AtomicInteger packetId = new AtomicInteger();
      final AtomicBoolean pubRecReceived = new AtomicBoolean(false);

      MQTTInterceptor incomingInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBLISH) {
            packetId.set(((MqttPublishMessage)packet).variableHeader().packetId());
         }
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBREC) {
            assertEquals(packetId.get(), ((MqttPubReplyMessageVariableHeader)packet.variableHeader()).messageId());
            assertEquals(MQTTReasonCodes.SUCCESS, ((MqttPubReplyMessageVariableHeader)packet.variableHeader()).reasonCode());
            pubRecReceived.set(true);
         }
         return true;
      };

      MQTTInterceptor outgoingInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBREL) {
            assertTrue(pubRecReceived.get());
            assertEquals(packetId.get(), ((MqttPubAckMessage)packet).variableHeader().messageId());
            ackLatch.countDown();
         }
         return true;
      };
      server.getRemotingService().addIncomingInterceptor(incomingInterceptor);
      server.getRemotingService().addOutgoingInterceptor(outgoingInterceptor);

      final CountDownLatch latch = new CountDownLatch(1);
      MqttClient consumer = createPahoClient("consumer");
      consumer.connect();
      consumer.setCallback(new LatchedMqttCallback(latch));
      consumer.subscribe(TOPIC, 2);

      MqttClient producer = createPahoClient("producer");
      producer.connect();
      producer.publish(TOPIC, RandomUtil.randomUUIDString().getBytes(), 2, false);
      producer.disconnect();
      producer.close();

      assertTrue(ackLatch.await(2, TimeUnit.SECONDS));
      assertTrue(latch.await(2, TimeUnit.SECONDS));
      consumer.disconnect();
      consumer.close();
   }

   /*
    * [MQTT-4.3.3-5] In the QoS 2 delivery protocol, the sender MUST treat the PUBREL packet as “unacknowledged” until
    * it has received the corresponding PUBCOMP packet from the receiver.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testQoS2PubRel() throws Exception {
      final String TOPIC = RandomUtil.randomUUIDString();
      final String CONSUMER_ID = "consumer";
      final CountDownLatch ackLatch = new CountDownLatch(1);
      final AtomicInteger packetId = new AtomicInteger();

      MQTTInterceptor incomingInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBCOMP) {
            // ensure the packet ID is stored in the PUBREC cache before we get the PUBCOMP from the client
            assertTrue(getPubRecCache(CONSUMER_ID).contains(ByteUtil.intToBytes(packetId.get())));

            // ensure the ids match so we know this is the "corresponding" PUBCOMP for the previous PUBLISH
            assertEquals(packetId.get(), ((MqttPubReplyMessageVariableHeader)packet.variableHeader()).messageId());

            ackLatch.countDown();
         }
         return true;
      };

      MQTTInterceptor outgoingInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBLISH) {
            packetId.set(((MqttPublishMessage)packet).variableHeader().packetId());
         }
         return true;
      };

      server.getRemotingService().addIncomingInterceptor(incomingInterceptor);
      server.getRemotingService().addOutgoingInterceptor(outgoingInterceptor);

      final CountDownLatch latch = new CountDownLatch(1);
      MqttClient consumer = createPahoClient(CONSUMER_ID);
      consumer.connect();
      consumer.setCallback(new LatchedMqttCallback(latch));
      consumer.subscribe(TOPIC, 2);

      MqttClient producer = createPahoClient("producer");
      producer.connect();
      producer.publish(TOPIC, RandomUtil.randomUUIDString().getBytes(), 2, false);
      producer.disconnect();
      producer.close();

      assertTrue(ackLatch.await(2, TimeUnit.SECONDS));
      assertTrue(latch.await(2, TimeUnit.SECONDS));
      assertEquals(0, getSubscriptionQueue(TOPIC, CONSUMER_ID).getMessageCount());
      assertEquals(0, getSubscriptionQueue(TOPIC, CONSUMER_ID).getDeliveringCount());
      consumer.disconnect();
      consumer.close();
   }

   /*
    * [MQTT-4.3.3-7] In the QoS 2 delivery protocol, the sender MUST NOT apply Application Message expiry if a PUBLISH
    * packet has been sent.
    *
    * [MQTT-4.3.3-13] In the QoS 2 delivery protocol, the receiver MUST continue the QoS 2 acknowledgement sequence even if it has applied Application Message expiry.
    *
    * Due to the nature of the underlying queue semantics once a message is "in delivery" it's no longer available for
    * expiration. This test demonstrates that.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testQoS2WithExpiration() throws Exception {
      final String TOPIC = "myTopic";
      final String CONSUMER_ID = "consumer";
      final CountDownLatch ackLatch = new CountDownLatch(1);
      final CountDownLatch expireRefsLatch = new CountDownLatch(1);
      final long messageExpiryInterval = 2;

      MQTTInterceptor incomingInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBREC) {
            // ensure the message is still in the queue before we get the PUBREC from the client
            assertEquals(1, getSubscriptionQueue(TOPIC, CONSUMER_ID).getMessageCount());
            assertEquals(1, getSubscriptionQueue(TOPIC, CONSUMER_ID).getDeliveringCount());
            try {
               // ensure enough time has passed for the message to expire
               Thread.sleep(messageExpiryInterval * 1500);
               getSubscriptionQueue(TOPIC, CONSUMER_ID).expireReferences(expireRefsLatch::countDown);
               assertTrue(expireRefsLatch.await(2, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
               e.printStackTrace();
               fail();
            }
            ackLatch.countDown();
         }
         return true;
      };

      server.getRemotingService().addIncomingInterceptor(incomingInterceptor);

      final CountDownLatch latch = new CountDownLatch(1);
      MqttClient consumer = createPahoClient(CONSUMER_ID);
      consumer.connect();
      consumer.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            latch.countDown();
         }
      });
      consumer.subscribe(TOPIC, 2);

      MqttClient producer = createPahoClient("producer");
      producer.connect();
      MqttMessage m = new MqttMessage();
      MqttProperties props = new MqttProperties();
      props.setMessageExpiryInterval(messageExpiryInterval);
      m.setProperties(props);
      m.setQos(2);
      m.setPayload("foo".getBytes(StandardCharsets.UTF_8));
      producer.publish(TOPIC, m);
      producer.disconnect();
      producer.close();

      assertTrue(ackLatch.await(messageExpiryInterval * 2, TimeUnit.SECONDS));
      assertTrue(latch.await(messageExpiryInterval * 2, TimeUnit.SECONDS));
      Wait.assertEquals(0, () -> getSubscriptionQueue(TOPIC, CONSUMER_ID).getMessageCount());
      Wait.assertEquals(0, () -> getSubscriptionQueue(TOPIC, CONSUMER_ID).getDeliveringCount());
      Wait.assertEquals(0, () -> getSubscriptionQueue(TOPIC, CONSUMER_ID).getMessagesExpired());
      consumer.disconnect();
      consumer.close();
   }

   /*
    * [MQTT-4.3.3-8] In the QoS 2 delivery protocol, the receiver MUST respond with a PUBREC containing the Packet
    * Identifier from the incoming PUBLISH packet, having accepted ownership of the Application Message.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testQoS2PubRecId() throws Exception {
      final String TOPIC = RandomUtil.randomUUIDString();
      final CountDownLatch ackLatch = new CountDownLatch(1);
      final AtomicInteger packetId = new AtomicInteger();

      MQTTInterceptor incomingInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBLISH) {
            packetId.set(((MqttPublishMessage)packet).variableHeader().packetId());
         }
         return true;
      };

      MQTTInterceptor outgoingInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBREC) {
            assertEquals(packetId.get(), ((MqttPubAckMessage)packet).variableHeader().messageId());
            ackLatch.countDown();
         }
         return true;
      };
      server.getRemotingService().addIncomingInterceptor(incomingInterceptor);
      server.getRemotingService().addOutgoingInterceptor(outgoingInterceptor);

      final CountDownLatch latch = new CountDownLatch(1);
      MqttClient consumer = createPahoClient("consumer");
      consumer.connect();
      consumer.setCallback(new LatchedMqttCallback(latch));
      consumer.subscribe(TOPIC, 2);

      MqttClient producer = createPahoClient("producer");
      producer.connect();
      producer.publish(TOPIC, RandomUtil.randomUUIDString().getBytes(), 2, false);
      producer.disconnect();
      producer.close();

      assertTrue(ackLatch.await(2, TimeUnit.SECONDS));
      assertTrue(latch.await(2, TimeUnit.SECONDS));
      consumer.disconnect();
      consumer.close();
   }

   /*
    * [MQTT-4.3.3-10] In the QoS 2 delivery protocol, the receiver until it has received the corresponding PUBREL
    * packet, the receiver MUST acknowledge any subsequent PUBLISH packet with the same Packet Identifier by sending a
    * PUBREC. It MUST NOT cause duplicate messages to be delivered to any onward recipients in this case.
    *
    * In this test we simulate a client sending a QoS 2 message but being disconnected before it receives the initial
    * PUBREC. When the client connects again it resends the message, but the broker does not duplicate it. It simply
    * responds withe appropriate PUBREC and the client completes the QoS process.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testQoS2DuplicatePub() throws Exception {
      final String TOPIC = RandomUtil.randomUUIDString();
      final CountDownLatch ackLatch = new CountDownLatch(1);
      final AtomicInteger packetId = new AtomicInteger();
      AtomicInteger count = new AtomicInteger(0);
      final byte[] PAYLOAD = RandomUtil.randomUUIDString().getBytes();

      MQTTInterceptor incomingInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBLISH) {
            packetId.set(((MqttPublishMessage)packet).variableHeader().packetId());
         }
         return true;
      };

      MQTTInterceptor outgoingInterceptor = (packet, connection) -> {
         if (count.get() == 0 && packet.fixedHeader().messageType() == MqttMessageType.PUBREC) {
            ackLatch.countDown();
            count.incrementAndGet();
            connection.disconnect(true);
            return false;
         }
         return true;
      };
      server.getRemotingService().addIncomingInterceptor(incomingInterceptor);
      server.getRemotingService().addOutgoingInterceptor(outgoingInterceptor);

      Queue queue = server.createQueue(QueueConfiguration.of(TOPIC));

      MqttClient producer = createPahoClient("producer");
      MqttConnectionOptions connectionOptions = new MqttConnectionOptions();
      connectionOptions.setCleanStart(false);
      connectionOptions.setSessionExpiryInterval(999L);

      producer.connect(connectionOptions);
      try {
         producer.publish(TOPIC, PAYLOAD, 2, false);
      } catch (Exception e) {
         // ignore
      }
      assertTrue(ackLatch.await(2, TimeUnit.SECONDS));
      producer.disconnectForcibly(1, 1, false);
      producer.close();

      producer = createPahoClient("producer");
      producer.connect(connectionOptions);
      producer.publish(TOPIC, PAYLOAD, 2, false);
      producer.disconnect();
      producer.close();

      assertEquals(1, queue.getMessageCount());
   }

   /*
    * [MQTT-4.3.3-11] In the QoS 2 delivery protocol, the receiver MUST respond to a PUBREL packet by sending a PUBCOMP
    * packet containing the same Packet Identifier as the PUBREL.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testQoS2PubCompId() throws Exception {
      final String TOPIC = RandomUtil.randomUUIDString();
      final CountDownLatch ackLatch = new CountDownLatch(1);
      final AtomicInteger packetId = new AtomicInteger();

      MQTTInterceptor incomingInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBREL) {
            packetId.set(((MqttPubReplyMessageVariableHeader)packet.variableHeader()).messageId());
         }
         return true;
      };

      MQTTInterceptor outgoingInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBCOMP) {
            assertEquals(packetId.get(), ((MqttPubAckMessage)packet).variableHeader().messageId());
            ackLatch.countDown();
         }
         return true;
      };
      server.getRemotingService().addIncomingInterceptor(incomingInterceptor);
      server.getRemotingService().addOutgoingInterceptor(outgoingInterceptor);

      final CountDownLatch latch = new CountDownLatch(1);
      MqttClient consumer = createPahoClient("consumer");
      consumer.connect();
      consumer.setCallback(new LatchedMqttCallback(latch));
      consumer.subscribe(TOPIC, 2);

      MqttClient producer = createPahoClient("producer");
      producer.connect();
      producer.publish(TOPIC, RandomUtil.randomUUIDString().getBytes(), 2, false);
      producer.disconnect();
      producer.close();

      assertTrue(ackLatch.await(2, TimeUnit.SECONDS));
      assertTrue(latch.await(2, TimeUnit.SECONDS));
      consumer.disconnect();
      consumer.close();
   }

   /*
    * [MQTT-4.3.3-13] In the QoS 2 delivery protocol, the receiver MUST continue the QoS 2 acknowledgement sequence even
    * if it has applied Application Message expiry.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testQoS2WithExpiration2() throws Exception {
      final String TOPIC = "myTopic";
      final String CONSUMER_ID = "consumer";
      server.createQueue(QueueConfiguration.of(MQTTUtil.getCoreQueueFromMqttTopic(TOPIC, CONSUMER_ID, server.getConfiguration().getWildcardConfiguration()))
                            .setAddress(MQTTUtil.getCoreAddressFromMqttTopic(TOPIC, server.getConfiguration().getWildcardConfiguration()))
                            .setRoutingType(RoutingType.MULTICAST));
      final CountDownLatch ackLatch = new CountDownLatch(1);
      final CountDownLatch expireRefsLatch = new CountDownLatch(1);
      final long messageExpiryInterval = 1;

      MQTTInterceptor outgoingInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBREC) {
            // ensure the message is in the queue before trying to expire
            Wait.assertTrue(() -> getSubscriptionQueue(TOPIC, CONSUMER_ID).getMessageCount() == 1, 2000, 100);
            try {
               // ensure enough time has passed for the message to expire
               Thread.sleep(messageExpiryInterval * 1500);
               getSubscriptionQueue(TOPIC, CONSUMER_ID).expireReferences(expireRefsLatch::countDown);
               assertTrue(expireRefsLatch.await(2, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
               e.printStackTrace();
               fail();
            }
            ackLatch.countDown();
         }
         return true;
      };

      server.getRemotingService().addOutgoingInterceptor(outgoingInterceptor);

      MqttClient producer = createPahoClient("producer");
      producer.connect();
      MqttMessage m = new MqttMessage();
      MqttProperties props = new MqttProperties();
      props.setMessageExpiryInterval(messageExpiryInterval);
      m.setProperties(props);
      m.setQos(2);
      m.setPayload("foo".getBytes(StandardCharsets.UTF_8));
      producer.publish(TOPIC, m);
      producer.disconnect();
      producer.close();

      assertTrue(ackLatch.await(messageExpiryInterval * 2, TimeUnit.SECONDS));
      Wait.assertEquals(1, () -> getSubscriptionQueue(TOPIC, CONSUMER_ID).getMessagesExpired());
   }

   /*
    * [MQTT-4.3.3-4] In the QoS 2 delivery protocol, the sender MUST send a PUBREL packet when it receives a PUBREC
    * packet from the receiver with a Reason Code value less than 0x80.
    *
    * The converse: when the broker (sender) receives a PUBREC with Reason Code >= 0x80, it MUST NOT send PUBREL.
    * Instead, it should treat the PUBLISH as acknowledged (i.e. discard it without sending PUBREL).
    *
    * This test uses the HiveMQ MQTT client as the Paho client doesn't support the ability to simulate a PUBREC with
    * a reason code greater than 0x80.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testQoS2PubRecError() throws Exception {
      final String TOPIC = RandomUtil.randomUUIDString();
      final String CONSUMER_ID = "consumer";
      final AtomicBoolean pubRelSent = new AtomicBoolean(false);
      final CountDownLatch pubRecReceived = new CountDownLatch(1);
      final AtomicBoolean correlationExistedDuringDelivery = new AtomicBoolean(false);

      MQTTInterceptor incomingInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBREC) {
            pubRecReceived.countDown();
         }
         return true;
      };

      MQTTInterceptor outgoingInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBLISH) {
            correlationExistedDuringDelivery.set(getProtocolManager().getStateManager().getPacketIdCorrelationSize(CONSUMER_ID) > 0);
         }
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBREL) {
            pubRelSent.set(true);
         }
         return true;
      };

      server.getRemotingService().addIncomingInterceptor(incomingInterceptor);
      server.getRemotingService().addOutgoingInterceptor(outgoingInterceptor);

      EpollEventLoopGroup hivemqEventLoop = new EpollEventLoopGroup();
      try {
         Mqtt5BlockingClient consumer = Mqtt5Client.builder()
            .identifier(CONSUMER_ID)
            .serverHost("localhost")
            .serverPort(getPort())
            .executorConfig()
               .nettyExecutor(hivemqEventLoop)
            .applyExecutorConfig()
            .advancedConfig()
               .interceptors()
                  .incomingQos2Interceptor(new Mqtt5IncomingQos2Interceptor() {
                     @Override
                     public void onPublish(Mqtt5ClientConfig clientConfig, Mqtt5Publish publish, Mqtt5PubRecBuilder pubRecBuilder) {
                        pubRecBuilder.reasonCode(Mqtt5PubRecReasonCode.UNSPECIFIED_ERROR);
                     }

                     @Override
                     public void onPubRel(Mqtt5ClientConfig clientConfig, Mqtt5PubRel pubRel, Mqtt5PubCompBuilder pubCompBuilder) {
                     }
                  })
               .applyInterceptors()
            .applyAdvancedConfig()
            .buildBlocking();
         consumer.connect();
         Mqtt5BlockingClient.Mqtt5Publishes publishes = consumer.publishes(MqttGlobalPublishFilter.ALL);
         consumer.subscribeWith()
            .topicFilter(TOPIC)
            .qos(MqttQos.EXACTLY_ONCE)
            .send();

         MqttClient producer = createPahoClient("producer");
         producer.connect();
         producer.publish(TOPIC, RandomUtil.randomUUIDString().getBytes(), 2, false);
         producer.disconnect();
         producer.close();

         assertTrue(pubRecReceived.await(5, TimeUnit.SECONDS), "PUBREC was not received by the broker");
         assertTrue(correlationExistedDuringDelivery.get(), "Packet ID correlation should exist during delivery");
         Wait.assertEquals(0L, () -> getSubscriptionQueue(TOPIC, CONSUMER_ID).getMessageCount(), 2000, 100);
         Wait.assertEquals(1L, () -> getSubscriptionQueue(TOPIC, CONSUMER_ID).getMessagesAcknowledged(), 2000, 100);
         Wait.assertEquals(0, () -> getProtocolManager().getStateManager().getPacketIdCorrelationSize(CONSUMER_ID), 2000, 100);
         assertFalse(pubRelSent.get(), "PUBREL should not be sent in response to an error PUBREC");
         assertNull(getPubRecCache(CONSUMER_ID));

         publishes.close();
         consumer.disconnect();
      } finally {
         hivemqEventLoop.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS).sync();
         Schedulers.shutdown();
      }
   }
}
