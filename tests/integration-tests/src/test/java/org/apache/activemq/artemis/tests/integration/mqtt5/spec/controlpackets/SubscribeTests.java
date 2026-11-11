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

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.handler.codec.mqtt.MqttMessageIdAndPropertiesVariableHeader;
import io.netty.handler.codec.mqtt.MqttMessageType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTInterceptor;
import org.apache.activemq.artemis.tests.integration.mqtt5.MQTT5TestSupport;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.eclipse.paho.mqttv5.common.packet.MqttSubAck;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/*
 * Fulfilled by client or Netty codec (i.e. not tested here):
 *
 * [MQTT-3.8.1-1] Bits 3,2,1 and 0 of the Fixed Header of the SUBSCRIBE packet are reserved and MUST be set to 0,0,1 and 0 respectively. The Server MUST treat any other value as malformed and close the Network Connection
 * [MQTT-3.8.3-1] The Topic Filters MUST be a UTF-8 Encoded String.
 * [MQTT-3.8.3-2] The Payload MUST contain at least one Topic Filter and Subscription Options pair.
 * [MQTT-3.8.3-4] It is a Protocol Error to set the No Local bit to 1 on a Shared Subscription.
 * [MQTT-3.8.3-5] The Server MUST treat a SUBSCRIBE packet as malformed if any of Reserved bits in the Payload are non-zero.
 */

public class SubscribeTests extends MQTT5TestSupport {

   /**
    * [MQTT-3.8.3-3] Bit 2 of the Subscription Options represents the No Local option. If the value is 1, Application
    * Messages MUST NOT be forwarded to a connection with a ClientID equal to the ClientID of the publishing connection.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testSubscribeNoLocal() throws Exception {
      final String TOPIC = RandomUtil.randomUUIDString();
      final CountDownLatch latch = new CountDownLatch(1);

      MqttClient client = createPahoClient("nolocal");
      client.connect();
      client.setCallback(new LatchedMqttCallback(latch));
      MqttSubscription sub = new MqttSubscription(TOPIC, 0);
      sub.setNoLocal(true);
      client.subscribe(new MqttSubscription[]{sub});
      client.publish(TOPIC, new byte[0], 0, false);

      Wait.assertEquals(1L, () -> getSubscriptionQueue(TOPIC, "nolocal").getMessagesAdded(), 2000, 100);

      // ensure we *don't* receive the message since noLocal=true
      assertFalse(latch.await(2, TimeUnit.SECONDS));

      client.disconnect();
      client.close();
   }

   /**
    * [MQTT-3.8.3-3]
    *
    * This test was adapted from Test.test_request_response in client_test5.py at https://github.com/eclipse/paho.mqtt.testing/tree/master/interoperability
    *
    * It involves 2 clients subscribing to and performing a request/response on the same topic so it's imperative they
    * don't receive the messages that they send themselves.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testRequestResponseNoLocal() throws Exception {
      final String TOPIC = RandomUtil.randomUUIDString();
      final String REQUEST = "request";
      final String RESPONSE = "response";
      final CountDownLatch aclientLatch = new CountDownLatch(2);
      final CountDownLatch bclientLatch = new CountDownLatch(1);

      MqttClient aclient = createPahoClient("aclientid");
      aclient.connect();
      aclient.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            assertEquals(RESPONSE, new String(message.getPayload()));
            aclientLatch.countDown();
         }
      });

      MqttClient bclient = createPahoClient("bclientid");
      bclient.connect();
      bclient.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            assertEquals(REQUEST, new String(message.getPayload()));
            bclientLatch.countDown();
            MqttMessage m = new MqttMessage();
            m.setPayload(RESPONSE.getBytes(StandardCharsets.UTF_8));
            m.setQos(1);
            MqttProperties properties = new MqttProperties();
            properties.setResponseTopic(TOPIC);
            properties.setCorrelationData("334".getBytes(StandardCharsets.UTF_8));
            m.setProperties(properties);
            bclient.publish(TOPIC, m);
         }
      });

      MqttSubscription sub = new MqttSubscription(TOPIC, 2);
      sub.setNoLocal(true);
      aclient.subscribe(new MqttSubscription[]{sub});
      bclient.subscribe(new MqttSubscription[]{sub});

      MqttMessage m = new MqttMessage();
      m.setPayload(REQUEST.getBytes(StandardCharsets.UTF_8));
      m.setQos(1);
      MqttProperties properties = new MqttProperties();
      properties.setResponseTopic(TOPIC);
      properties.setCorrelationData("334".getBytes(StandardCharsets.UTF_8));
      m.setProperties(properties);
      aclient.publish(TOPIC, m);

      assertTrue(bclientLatch.await(2, TimeUnit.SECONDS));

      Wait.assertEquals(1L, () -> aclientLatch.getCount(), 2000, 100);
      assertFalse(aclientLatch.await(2, TimeUnit.SECONDS));

      aclient.disconnect();
      aclient.close();
      bclient.disconnect();
      bclient.close();
   }

   /**
    * [MQTT-3.8.4-1] When the Server receives a SUBSCRIBE packet from a Client, the Server MUST respond with a SUBACK
    * packet.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testSubAck() throws Exception {
      final String TOPIC = RandomUtil.randomUUIDString();
      final AtomicBoolean subscribed = new AtomicBoolean(false);
      final CountDownLatch latch = new CountDownLatch(1);

      MQTTInterceptor incomingInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.SUBSCRIBE) {
            subscribed.set(true);
         }
         return true;
      };

      MQTTInterceptor outgoingInterceptor = (packet, connection) -> {
         if (subscribed.get() && packet.fixedHeader().messageType() == MqttMessageType.SUBACK) {
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

   /**
    * [MQTT-3.8.4-2] The SUBACK packet MUST have the same Packet Identifier as the SUBSCRIBE packet that it is
    * acknowledging.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testSubAckPacketId() throws Exception {
      final String TOPIC = RandomUtil.randomUUIDString();
      final AtomicBoolean subscribed = new AtomicBoolean(false);
      final AtomicInteger packetId = new AtomicInteger(0);
      final CountDownLatch latch = new CountDownLatch(1);

      MQTTInterceptor incomingInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.SUBSCRIBE) {
            subscribed.set(true);
            packetId.set(((MqttMessageIdAndPropertiesVariableHeader)packet.variableHeader()).messageId());
         }
         return true;
      };

      MQTTInterceptor outgoingInterceptor = (packet, connection) -> {
         if (subscribed.get() && packet.fixedHeader().messageType() == MqttMessageType.SUBACK) {
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

   /**
    * [MQTT-3.8.4-3] If a Server receives a SUBSCRIBE packet containing a Topic Filter that is identical to a Non‑shared
    * Subscription’s Topic Filter for the current Session then it MUST replace that existing Subscription with a new
    * Subscription.
    *
    * The spec goes on to say, "The Topic Filter in the new Subscription will be identical to that in the previous
    * Subscription, although its Subscription Options could be different."
    *
    * This test will be testing a difference in the "no local" subscription option
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testReplaceSubscription() throws Exception {
      final String TOPIC = RandomUtil.randomUUIDString();
      final CountDownLatch latch = new CountDownLatch(2);

      MqttClient client = createPahoClient("nolocal");
      client.connect();
      client.setCallback(new LatchedMqttCallback(latch));
      MqttSubscription sub = new MqttSubscription(TOPIC, 0);
      sub.setNoLocal(false);
      client.subscribe(new MqttSubscription[]{sub});
      client.publish(TOPIC, new byte[0], 0, false);

      Wait.assertEquals(1L, () -> getSubscriptionQueue(TOPIC, "nolocal").getMessagesAdded(), 2000, 100);
      Wait.assertTrue(() -> latch.getCount() == 1, 2000, 100);

      sub = new MqttSubscription(TOPIC, 0);
      sub.setNoLocal(true);
      client.subscribe(new MqttSubscription[]{sub});
      client.publish(TOPIC, new byte[0], 0, false);

      Wait.assertEquals(2L, () -> getSubscriptionQueue(TOPIC, "nolocal").getMessagesAdded(), 2000, 100);
      assertFalse(latch.await(2, TimeUnit.SECONDS));

      client.disconnect();
      client.close();
   }

   /**
    * [MQTT-3.8.4-4] If the Retain Handling option is 0, any existing retained messages matching the Topic Filter MUST
    * be re-sent, but Application Messages MUST NOT be lost due to replacing the Subscription.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testReplaceSubscriptionRetainHandling() throws Exception {
      final String TOPIC = RandomUtil.randomUUIDString();
      final String CLIENT_ID = RandomUtil.randomUUIDString();
      final CountDownLatch latch = new CountDownLatch(2);

      MqttClient client = createPahoClient(CLIENT_ID);
      client.connect();
      client.publish(TOPIC, "retained".getBytes(), 0, true);
      client.setCallback(new LatchedMqttCallback(latch));
      MqttSubscription sub = new MqttSubscription(TOPIC, 0);
      sub.setRetainHandling(1);
      client.subscribe(new MqttSubscription[]{sub});

      // this is the first retained message added when the subscription was first created
      Wait.assertEquals(1L, () -> getSubscriptionQueue(TOPIC, CLIENT_ID).getMessagesAdded(), 2000, 100);

      sub = new MqttSubscription(TOPIC, 0);
      sub.setRetainHandling(0);
      client.subscribe(new MqttSubscription[]{sub});

      // this is the second retained message added because retain handling was changed to 0
      Wait.assertEquals(2L, () -> getSubscriptionQueue(TOPIC, CLIENT_ID).getMessagesAdded(), 2000, 100);
      assertTrue(latch.await(2, TimeUnit.SECONDS));

      client.disconnect();
      client.close();
   }

   /**
    * [MQTT-3.8.4-5] If a Server receives a SUBSCRIBE packet that contains multiple Topic Filters it MUST handle that
    * packet as if it had received a sequence of multiple SUBSCRIBE packets, except that it combines their responses
    * into a single SUBACK response.
    *
    * [MQTT-3.8.4-6] The SUBACK packet sent by the Server to the Client MUST contain a Reason Code for each Topic
    * Filter/Subscription Option pair.
    *
    * [MQTT-3.8.4-7] This Reason Code MUST either show the maximum QoS that was granted for that Subscription or
    * indicate that the subscription failed.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testSubscribeAck() throws Exception {
      final int SUBSCRIPTION_COUNT = 30;
      final String TOPIC = RandomUtil.randomUUIDString();
      final AtomicInteger subAckCount = new AtomicInteger(0);
      SimpleString[] topicNames = new SimpleString[SUBSCRIPTION_COUNT];
      for (int i = 0; i < SUBSCRIPTION_COUNT; i++) {
         topicNames[i] = SimpleString.of(i + "-" + TOPIC);
      }

      MQTTInterceptor outgoingInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.SUBACK) {
            subAckCount.incrementAndGet();
         }
         return true;
      };
      server.getRemotingService().addOutgoingInterceptor(outgoingInterceptor);

      MqttAsyncClient consumer = createAsyncPahoClient("consumer");
      consumer.connect().waitForCompletion();
      MqttSubscription[] subscriptions = new MqttSubscription[SUBSCRIPTION_COUNT];
      for (int i = 0; i < SUBSCRIPTION_COUNT; i++) {
         subscriptions[i] = new MqttSubscription(topicNames[i].toString(), RandomUtil.randomInterval(0, 3));
      }
      IMqttToken token = consumer.subscribe(subscriptions);
      token.waitForCompletion();

      MqttSubAck response = (MqttSubAck) token.getResponse();
      assertEquals(subscriptions.length, response.getReturnCodes().length);
      for (int i = 0; i < response.getReturnCodes().length; i++) {
         assertEquals(subscriptions[i].getQos(), response.getReturnCodes()[i]);
      }

      for (int i = 0; i < SUBSCRIPTION_COUNT; i++) {
         assertTrue(server.getPostOffice().isAddressBound(topicNames[i]));
      }

      assertEquals(1, subAckCount.get());

      consumer.disconnect();
      consumer.close();
   }

   /**
    * [MQTT-3.8.4-8] The QoS of Payload Messages sent in response to a Subscription MUST be the minimum of the QoS of
    * the originally published message and the Maximum QoS granted by the Server.
    *
    * The server grants all QoS values so that isn't a factor here.
    *
    * Keep in mind that the QoS set by the subscriber is the max QoS that it will accept. Messages published at a higher
    * QoS will still be dispatched to the subscriber, just at the subscriber's max QoS.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testSubscriptionQoS() throws Exception {
      final String TOPIC = RandomUtil.randomUUIDString();
      final int PUBLISH_LOOP = 25;
      final int MESSAGES_PER_LOOP = 3;
      final int CONSUMER_COUNT = 3;
      final CountDownLatch qos0Latch = new CountDownLatch(PUBLISH_LOOP * MESSAGES_PER_LOOP);
      final CountDownLatch qos1Latch = new CountDownLatch(PUBLISH_LOOP * MESSAGES_PER_LOOP);
      final CountDownLatch qos2Latch = new CountDownLatch(PUBLISH_LOOP * MESSAGES_PER_LOOP);
      final AtomicInteger qos0Total = new AtomicInteger(0);
      final AtomicInteger qos1Total = new AtomicInteger(0);
      final AtomicInteger qos2Total = new AtomicInteger(0);

      MqttClient qos0Client = createPahoClient("qos0");
      qos0Client.connect();
      qos0Client.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            assertEquals(0, message.getQos());
            qos0Total.incrementAndGet();
            qos0Latch.countDown();
         }
      });
      qos0Client.subscribe(TOPIC, 0);

      MqttClient qos1Client = createPahoClient("qos1");
      qos1Client.connect();
      qos1Client.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            assertTrue(message.getQos() == 0 || message.getQos() == 1);
            if (message.getQos() == 0) {
               qos0Total.incrementAndGet();
            } else if (message.getQos() == 1) {
               qos1Total.incrementAndGet();
            }
            qos1Latch.countDown();
         }
      });
      qos1Client.subscribe(TOPIC, 1);

      MqttClient qos2Client = createPahoClient("qos2");
      qos2Client.connect();
      qos2Client.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            assertTrue(message.getQos() == 0 || message.getQos() == 1 || message.getQos() == 2);
            if (message.getQos() == 0) {
               qos0Total.incrementAndGet();
            } else if (message.getQos() == 1) {
               qos1Total.incrementAndGet();
            } else if (message.getQos() == 2) {
               qos2Total.incrementAndGet();
            }
            qos2Latch.countDown();
         }
      });
      qos2Client.subscribe(TOPIC, 2);

      MqttClient publisher = createPahoClient("publisher");
      publisher.connect();
      for (int i = 0; i < PUBLISH_LOOP; i++) {
         for (int j = 0; j < MESSAGES_PER_LOOP; j++) {
            publisher.publish(TOPIC, new byte[0], j, false);
         }
      }
      publisher.disconnect();
      publisher.close();

      assertTrue(qos0Latch.await(2, TimeUnit.SECONDS));
      assertTrue(qos1Latch.await(2, TimeUnit.SECONDS));
      assertTrue(qos2Latch.await(2, TimeUnit.SECONDS));

      assertEquals(PUBLISH_LOOP * MESSAGES_PER_LOOP * CONSUMER_COUNT, qos0Total.get() + qos1Total.get() + qos2Total.get());

      assertEquals(PUBLISH_LOOP * 5, qos0Total.get());
      assertEquals(PUBLISH_LOOP * 3, qos1Total.get());
      assertEquals(PUBLISH_LOOP * 1, qos2Total.get());

      qos0Client.disconnect();
      qos0Client.close();

      qos1Client.disconnect();
      qos1Client.close();

      qos2Client.disconnect();
      qos2Client.close();
   }
}
