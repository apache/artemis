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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.handler.codec.mqtt.MqttMessageType;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTInterceptor;
import org.apache.activemq.artemis.tests.integration.mqtt5.MQTT5TestSupport;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptionsBuilder;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.eclipse.paho.mqttv5.common.packet.MqttPublish;
import org.eclipse.paho.mqttv5.common.packet.MqttWireMessage;
import org.eclipse.paho.mqttv5.common.packet.UserProperty;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * Fulfilled by client or Netty codec (i.e. not tested here):
 *
 * [MQTT-3.3.2-1] The Topic Name MUST be present as the first field in the PUBLISH packet Variable Header. It MUST be a UTF-8 Encoded String.
 * [MQTT-3.3.2-9] A Client MUST NOT send a PUBLISH packet with a Topic Alias greater than the Topic Alias Maximum value returned by the Server in the CONNACK packet.
 * [MQTT-3.3.2-10] A Client MUST accept all Topic Alias values greater than 0 and less than or equal to the Topic Alias Maximum value that it sent in the CONNECT packet.
 * [MQTT-3.3.2-13] The Response Topic MUST be a UTF-8 Encoded String.
 * [MQTT-3.3.2-14] The Response Topic MUST NOT contain wildcard characters.
 * [MQTT-3.3.2-19] The Content Type MUST be a UTF-8 Encoded String.
 * [MQTT-3.3.4-6] A PUBLISH packet sent from a Client to a Server MUST NOT contain a Subscription Identifier.
 * [MQTT-3.3.4-7] The Client MUST NOT send more than Receive Maximum QoS 1 and QoS 2 PUBLISH packets for which it has not received PUBACK, PUBCOMP, or PUBREC with a Reason Code of 128 or greater from the Server.
 * [MQTT-3.3.4-8] The Client MUST NOT delay the sending of any packets other than PUBLISH packets due to having sent Receive Maximum PUBLISH packets without receiving acknowledgements for them.
 *
 *
 * Unsure how to test this since it's a negative:
 *
 * [MQTT-3.3.1-4] A PUBLISH Packet MUST NOT have both QoS bits set to 1.
 * [MQTT-3.3.2-2] The Topic Name in the PUBLISH packet MUST NOT contain wildcard characters.
 *
 *
 * I can't force the Paho client to set a Topic Alias of 0. It automatically adjusts it to 1 (confirmed via Wireshark), therefore this is not tested:
 *
 * [MQTT-3.3.2-8] A sender MUST NOT send a PUBLISH packet containing a Topic Alias which has the value 0.
 */

public class PublishTests extends MQTT5TestSupport {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   /**
    * [MQTT-3.3.1-1] The DUP flag MUST be set to 1 by the Client or Server when it attempts to re-deliver a PUBLISH
    * packet.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testDupFlag() throws Exception {
      final String CONSUMER_ID = RandomUtil.randomUUIDString();
      final String TOPIC = this.getTopicName();
      final AtomicBoolean intercepted = new AtomicBoolean(false);

      // prevent the first ack from working so redelivery happens
      MQTTInterceptor incomingInterceptor = (packet, connection) -> {
         if (!intercepted.get() && packet.fixedHeader().messageType() == MqttMessageType.PUBACK) {
            intercepted.set(true);
            return false;
         }
         return true;
      };
      server.getRemotingService().addIncomingInterceptor(incomingInterceptor);

      final CountDownLatch latch = new CountDownLatch(1);
      MqttClient producer = createPahoClient("producer");
      MqttClient consumer = createPahoClient(CONSUMER_ID);
      MqttConnectionOptions options = new MqttConnectionOptionsBuilder()
         .cleanStart(false)
         .sessionExpiryInterval(300L)
         .build();
      consumer.connect(options);
      consumer.subscribe(TOPIC, 1);
      consumer.disconnect();

      // session should still exist since session expiry interval > 0
      assertEquals(1, getSessionStates().size());
      assertNotNull(getSessionStates().get(CONSUMER_ID));

      producer.connect();
      producer.publish(TOPIC, "hello".getBytes(), 1, false);
      producer.disconnect();
      producer.close();

      // session should still exist since session expiry interval > 0
      assertEquals(1, getSessionStates().size());
      assertNotNull(getSessionStates().get(CONSUMER_ID));

      // consumer should resume previous session (i.e. get the messages sent to the queue where it was previously subscribed)
      consumer.setCallback(new LatchedMqttCallback(latch, true));
      consumer.connect(options);
      waitForLatch(latch);
      consumer.disconnect();

      // session should still exist since session expiry interval > 0
      assertEquals(1, getSessionStates().size());
      assertNotNull(getSessionStates().get(CONSUMER_ID));

      final CountDownLatch latch2 = new CountDownLatch(1);
      consumer.setCallback(new LatchedMqttCallback(latch2, false));
      consumer.connect(options);
      waitForLatch(latch2);
      consumer.disconnect();
      consumer.close();
   }

   /**
    * [MQTT-3.3.1-2] The DUP flag MUST be set to 0 for all QoS 0 messages.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testDupFlagQoSZero() throws Exception {
      final String CONSUMER_ID = RandomUtil.randomUUIDString();
      final String TOPIC = this.getTopicName();

      final CountDownLatch latch = new CountDownLatch(1);
      MqttClient consumer = createPahoClient(CONSUMER_ID);
      consumer.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            assertFalse(message.isDuplicate());
            latch.countDown();
         }
      });
      consumer.connect();
      consumer.subscribe(TOPIC, 2);

      MqttClient producer = createPahoClient("producer");
      producer.connect();
      producer.publish(TOPIC, new byte[0], 0, false);
      producer.disconnect();
      producer.close();

      assertTrue(latch.await(3, TimeUnit.SECONDS));
      consumer.disconnect();
      consumer.close();
   }

   /**
    * [MQTT-3.3.1-3] The DUP flag in the outgoing PUBLISH packet is set independently to the incoming PUBLISH packet,
    * its value MUST be determined solely by whether the outgoing PUBLISH packet is a retransmission.
    *
    * The value of the DUP flag from an incoming PUBLISH packet is not propagated when the PUBLISH packet is sent to
    * subscribers by the Server.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testDupFlagNotPropagated() throws Exception {
      final String CONSUMER_ID = RandomUtil.randomUUIDString();
      final String TOPIC = this.getTopicName();

      final CountDownLatch latch = new CountDownLatch(1);
      MqttClient producer = createPahoClient("producer");
      MqttClient consumer = createPahoClient(CONSUMER_ID);
      consumer.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            assertFalse(message.isDuplicate());
            latch.countDown();
         }
      });
      consumer.connect();
      consumer.subscribe(TOPIC, 2);

      producer.connect();
      MqttMessage m = new MqttMessage();
      m.setDuplicate(true);
      m.setPayload("hello".getBytes());
      m.setQos(2);
      m.setRetained(false);
      producer.publish(TOPIC, m);
      producer.disconnect();
      producer.close();
      assertTrue(latch.await(3, TimeUnit.SECONDS));
      consumer.disconnect();
      consumer.close();
   }

   /**
    * [MQTT-3.3.1-5] If the RETAIN flag is set to 1 in a PUBLISH packet sent by a Client to a Server, the Server MUST
    * replace any existing retained message for this topic and store the Application Message.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testRetainFlag() throws Exception {
      final String CONSUMER_ID = RandomUtil.randomUUIDString();
      final String TOPIC = this.getTopicName();

      MqttClient producer = createPahoClient("producer");
      producer.connect();
      // send first retained message
      producer.publish(TOPIC, "retain1".getBytes(), 2, true);

      // send second retained message; should replace the first
      producer.publish(TOPIC, "retain2".getBytes(), 2, true);
      producer.disconnect();
      producer.close();

      final CountDownLatch latch = new CountDownLatch(1);
      MqttClient consumer = createPahoClient(CONSUMER_ID);
      consumer.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            assertEqualsByteArrays("retain2".getBytes(StandardCharsets.UTF_8), message.getPayload());
            latch.countDown();
         }
      });
      consumer.connect();
      consumer.subscribe(TOPIC, 2);

      assertTrue(latch.await(3, TimeUnit.SECONDS));
      consumer.disconnect();
      consumer.close();
   }

   /**
    * [MQTT-3.3.1-6] If the Payload contains zero bytes it is processed normally by the Server but any retained message
    * with the same topic name MUST be removed and any future subscribers for the topic will not receive a retained
    * message.
    *
    * [MQTT-3.3.1-7] A retained message with a Payload containing zero bytes MUST NOT be stored as a retained message on
    * the Server.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testRetainFlagWithEmptyMessage() throws Exception {
      final String CONSUMER_ID = RandomUtil.randomUUIDString();
      final String TOPIC = this.getTopicName();

      assertNull(getRetainedMessageQueue(TOPIC));

      MqttClient producer = createPahoClient("producer");
      producer.connect();
      // send first retained message
      producer.publish(TOPIC, "retain1".getBytes(), 2, true);

      Wait.assertTrue(() -> getRetainedMessageQueue(TOPIC).getMessageCount() == 1, 2000, 100);

      // send second retained message; should *remove* the first
      producer.publish(TOPIC, new byte[0], 2, true);

      producer.disconnect();
      producer.close();

      Wait.assertTrue(() -> getRetainedMessageQueue(TOPIC).getMessageCount() == 0, 2000, 100);

      final CountDownLatch latch = new CountDownLatch(1);
      MqttClient consumer = createPahoClient(CONSUMER_ID);
      consumer.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            latch.countDown();
         }
      });
      consumer.connect();
      consumer.subscribe(TOPIC, 2);

      assertFalse(latch.await(2, TimeUnit.SECONDS));
      consumer.disconnect();
      consumer.close();
   }

   /**
    * [MQTT-3.3.1-8] If the RETAIN flag is 0 in a PUBLISH packet sent by a Client to a Server, the Server MUST NOT store
    * the message as a retained message and MUST NOT remove or replace any existing retained message.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testRetainFlagFalse() throws Exception {
      final String CONSUMER_ID = RandomUtil.randomUUIDString();
      final String TOPIC = this.getTopicName();
      final String RETAINED_PAYLOAD = RandomUtil.randomUUIDString();
      final String UNRETAINED_PAYLOAD = RandomUtil.randomUUIDString();

      assertNull(getRetainedMessageQueue(TOPIC));

      MqttClient producer = createPahoClient("producer");
      producer.connect();

      // send retained message
      producer.publish(TOPIC, RETAINED_PAYLOAD.getBytes(), 2, true);
      Wait.assertTrue(() -> getRetainedMessageQueue(TOPIC).getMessageCount() == 1, 1000, 100);

      // send an unretained message; should *not* remove the existing retained message
      producer.publish(TOPIC, UNRETAINED_PAYLOAD.getBytes(), 2, false);

      producer.disconnect();
      producer.close();

      Wait.assertFalse(() -> getRetainedMessageQueue(TOPIC).getMessageCount() > 1, 1000, 100);

      final CountDownLatch latch = new CountDownLatch(1);
      MqttClient consumer = createPahoClient(CONSUMER_ID);
      consumer.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            assertEqualsByteArrays(RETAINED_PAYLOAD.getBytes(StandardCharsets.UTF_8), message.getPayload());
            latch.countDown();
         }
      });
      consumer.connect();
      consumer.subscribe(TOPIC, 2);

      assertTrue(latch.await(2, TimeUnit.SECONDS));
      consumer.disconnect();
      consumer.close();
   }

   /**
    * When a new Non‑shared Subscription is made, the last retained message, if any, on each matching topic name is sent
    * to the Client as directed by the Retain Handling Subscription Option. These messages are sent with the RETAIN flag
    * set to 1. Which retained messages are sent is controlled by the Retain Handling Subscription Option. At the time
    * of the Subscription...
    *
    * [MQTT-3.3.1-9] If Retain Handling is set to 0 the Server MUST send the retained messages matching the Topic Filter
    * of the subscription to the Client.
    *
    * Simple test with just one subscription.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testRetainHandlingZeroWithOneSubscription() throws Exception {
      internalTestRetainHandlingZero(false, 1);
   }

   /**
    * When a new Non‑shared Subscription is made, the last retained message, if any, on each matching topic name is sent
    * to the Client as directed by the Retain Handling Subscription Option. These messages are sent with the RETAIN flag
    * set to 1. Which retained messages are sent is controlled by the Retain Handling Subscription Option. At the time
    * of the Subscription...
    *
    * [MQTT-3.3.1-9] If Retain Handling is set to 0 the Server MUST send the retained messages matching the Topic Filter
    * of the subscription to the Client.
    *
    * Testing with lots of individual subscriptions from a single client.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testRetainHandlingZeroWithMultipleSubscriptions() throws Exception {
      internalTestRetainHandlingZero(false, 25);
   }

   /**
    * When a new Non‑shared Subscription is made, the last retained message, if any, on each matching topic name is sent
    * to the Client as directed by the Retain Handling Subscription Option. These messages are sent with the RETAIN flag
    * set to 1. Which retained messages are sent is controlled by the Retain Handling Subscription Option. At the time
    * of the Subscription...
    *
    * [MQTT-3.3.1-9] If Retain Handling is set to 0 the Server MUST send the retained messages matching the Topic Filter
    * of the subscription to the Client.
    *
    * Testing topic filter subscription.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testRetainHandlingZeroWithTopicFilterSubscription() throws Exception {
      internalTestRetainHandlingZero(true, 25);
   }

   public void internalTestRetainHandlingZero(boolean filter, int subscriptionCount) throws Exception {
      final int SUBSCRIPTION_COUNT = subscriptionCount;
      final String CONSUMER_ID = RandomUtil.randomUUIDString();
      final String PREFIX = "myTopic/";
      String[] topicNames = new String[SUBSCRIPTION_COUNT];
      for (int i = 0; i < SUBSCRIPTION_COUNT; i++) {
         topicNames[i] = PREFIX + this.getTopicName() + i;
      }
      String[] retainedPayloads = new String[SUBSCRIPTION_COUNT];
      for (int i = 0; i < SUBSCRIPTION_COUNT; i++) {
         retainedPayloads[i] = RandomUtil.randomUUIDString();
      }

      for (int i = 0; i < SUBSCRIPTION_COUNT; i++) {
         assertNull(getRetainedMessageQueue(topicNames[i]));
      }

      // send retained messages
      MqttClient producer = createPahoClient("producer");
      producer.connect();
      for (int i = 0; i < SUBSCRIPTION_COUNT; i++) {
         final String topicName = topicNames[i];
         producer.publish(topicName, retainedPayloads[i].getBytes(), 2, true);
         Wait.assertTrue(() -> getRetainedMessageQueue(topicName).getMessageCount() == 1, 2000, 100);
      }
      producer.disconnect();
      producer.close();

      final CountDownLatch latch = new CountDownLatch(SUBSCRIPTION_COUNT);
      MqttClient consumer = createPahoClient(CONSUMER_ID);
      consumer.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            boolean payloadMatched = false;
            for (int i = 0; i < SUBSCRIPTION_COUNT; i++) {
               if (new String(message.getPayload()).equals(retainedPayloads[i])) {
                  payloadMatched = true;
                  break;
               }
            }
            assertTrue(payloadMatched);
            assertTrue(message.isRetained());
            latch.countDown();
         }
      });
      consumer.connect();
      if (filter) { // use a filter instead of bunch of individual subscriptions
         MqttSubscription sub = new MqttSubscription(PREFIX + "#", 2);
         sub.setRetainHandling(0);
         consumer.subscribe(new MqttSubscription[]{sub});
      } else {
         MqttSubscription[] subscriptions = new MqttSubscription[SUBSCRIPTION_COUNT];
         for (int i = 0; i < SUBSCRIPTION_COUNT; i++) {
            MqttSubscription subscription = new MqttSubscription(topicNames[i], 2);
            subscription.setRetainHandling(0);
            subscriptions[i] = subscription;
         }
         consumer.subscribe(subscriptions);
      }

      assertTrue(latch.await(2, TimeUnit.SECONDS));
      consumer.disconnect();
      consumer.close();
   }

   /**
    * When a new Non‑shared Subscription is made, the last retained message, if any, on each matching topic name is sent
    * to the Client as directed by the Retain Handling Subscription Option. These messages are sent with the RETAIN flag
    * set to 1. Which retained messages are sent is controlled by the Retain Handling Subscription Option. At the time
    * of the Subscription...
    *
    * [MQTT-3.3.1-10]  If Retain Handling is set to 1 then if the subscription did not already exist, the Server MUST
    * send all retained message matching the Topic Filter of the subscription to the Client, and if the subscription did
    * exist the Server MUST NOT send the retained messages.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testRetainHandlingOne() throws Exception {
      final String CONSUMER_ID = RandomUtil.randomUUIDString();
      final String TOPIC = this.getTopicName();

      assertNull(getRetainedMessageQueue(TOPIC));

      // send retained messages
      MqttClient producer = createPahoClient("producer");
      producer.connect();
      producer.publish(TOPIC, "retained".getBytes(), 2, true);

      Wait.assertTrue(() -> getRetainedMessageQueue(TOPIC).getMessageCount() == 1, 2000, 100);
      producer.disconnect();
      producer.close();

      // options for consumers
      MqttConnectionOptions options = new MqttConnectionOptionsBuilder()
         .cleanStart(false)
         .sessionExpiryInterval(300L)
         .build();

      // consume retained message
      MqttClient consumer = createPahoClient(CONSUMER_ID);
      consumer.connect(options);
      final CountDownLatch latch = new CountDownLatch(1);
      consumer.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) {
            assertEqualsByteArrays("retained".getBytes(StandardCharsets.UTF_8), message.getPayload());
            assertTrue(message.isRetained());
            latch.countDown();
         }
      });
      MqttSubscription subscription = new MqttSubscription(TOPIC, 2);
      subscription.setRetainHandling(1);
      consumer.subscribe(new MqttSubscription[]{subscription});
      assertTrue(latch.await(2, TimeUnit.SECONDS));

      // ensure the retained message has been successfully acknowledge and removed from the subscription queue
      Wait.assertTrue(() -> getSubscriptionQueue(TOPIC, CONSUMER_ID).getMessageCount() == 0, 2000, 100);

      consumer.disconnect();

      // consumer should resume previous session but it should *not* get the retained message again
      final CountDownLatch latch2 = new CountDownLatch(1);
      consumer.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            latch2.countDown();
         }
      });
      consumer.connect(options);
      assertFalse(latch2.await(2, TimeUnit.SECONDS));
      consumer.disconnect();
      consumer.close();
   }

   /**
    * When a new Non‑shared Subscription is made, the last retained message, if any, on each matching topic name is sent
    * to the Client as directed by the Retain Handling Subscription Option. These messages are sent with the RETAIN flag
    * set to 1. Which retained messages are sent is controlled by the Retain Handling Subscription Option. At the time
    * of the Subscription...
    *
    * [MQTT-3.3.1-11] If Retain Handling is set to 2, the Server MUST NOT send the retained messages
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testRetainHandlingTwo() throws Exception {
      final String CONSUMER_ID = RandomUtil.randomUUIDString();
      final String TOPIC = this.getTopicName();

      assertNull(getRetainedMessageQueue(TOPIC));

      // send first retained message
      MqttClient producer = createPahoClient("producer");
      producer.connect();
      producer.publish(TOPIC, "retained".getBytes(), 2, true);
      Wait.assertTrue(() -> getRetainedMessageQueue(TOPIC).getMessageCount() == 1, 2000, 100);
      producer.disconnect();
      producer.close();

      // create consumer
      final CountDownLatch latch = new CountDownLatch(1);
      MqttClient consumer = createPahoClient(CONSUMER_ID);
      consumer.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            latch.countDown();
         }
      });
      consumer.connect();
      MqttSubscription subscription = new MqttSubscription(TOPIC, 2);
      subscription.setRetainHandling(2);
      consumer.subscribe(new MqttSubscription[]{subscription});

      assertFalse(latch.await(2, TimeUnit.SECONDS));
      consumer.disconnect();
      consumer.close();
   }

   /**
    * The setting of the RETAIN flag in an Application Message forwarded by the Server from an *established* connection
    * is controlled by the Retain As Published subscription option.
    *
    * [MQTT-3.3.1-12] If the value of Retain As Published subscription option is set to 0, the Server MUST set the
    * RETAIN flag to 0 when forwarding an Application Message regardless of how the RETAIN flag was set in the received
    * PUBLISH packet.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testRetainAsPublishedZeroOnEstablishedSubscription() throws Exception {
      final String CONSUMER_ID = RandomUtil.randomUUIDString();
      final String TOPIC = this.getTopicName();

      // create consumer
      final CountDownLatch latch = new CountDownLatch(1);
      MqttClient consumer = createPahoClient(CONSUMER_ID);
      consumer.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            assertEqualsByteArrays("retained".getBytes(StandardCharsets.UTF_8), message.getPayload());
            assertFalse(message.isRetained());
            latch.countDown();
         }
      });
      consumer.connect();
      MqttSubscription subscription = new MqttSubscription(TOPIC, 2);
      subscription.setRetainAsPublished(false);
      consumer.subscribe(new MqttSubscription[]{subscription});

      assertNull(getRetainedMessageQueue(TOPIC));

      // send retained message
      MqttClient producer = createPahoClient("producer");
      producer.connect();
      producer.publish(TOPIC, "retained".getBytes(), 2, true);
      Wait.assertTrue(() -> getRetainedMessageQueue(TOPIC).getMessageCount() == 1, 2000, 100);
      producer.disconnect();
      producer.close();

      assertTrue(latch.await(2, TimeUnit.SECONDS));
      consumer.disconnect();
      consumer.close();
   }

   /**
    * The setting of the RETAIN flag in an Application Message forwarded by the Server from an *established* connection
    * is controlled by the Retain As Published subscription option.
    *
    * [MQTT-3.3.1-13] If the value of Retain As Published subscription option is set to 1, the Server MUST set the
    * RETAIN flag equal to the RETAIN flag in the received PUBLISH packet.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testRetainAsPublishedOneOnEstablishedSubscription() throws Exception {
      final String CONSUMER_ID = RandomUtil.randomUUIDString();
      final String TOPIC = this.getTopicName();

      // create consumer
      final CountDownLatch latchOne = new CountDownLatch(1);
      final CountDownLatch latchTwo = new CountDownLatch(1);
      MqttClient consumer = createPahoClient(CONSUMER_ID);
      final AtomicBoolean first = new AtomicBoolean(true);
      consumer.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            if (first.getAndSet(false)) {
               assertEqualsByteArrays("retained".getBytes(StandardCharsets.UTF_8), message.getPayload());
               assertTrue(message.isRetained());
               latchOne.countDown();
            } else {
               assertEqualsByteArrays("unretained".getBytes(StandardCharsets.UTF_8), message.getPayload());
               assertFalse(message.isRetained());
               latchTwo.countDown();
            }
         }
      });
      consumer.connect();
      MqttSubscription subscription = new MqttSubscription(TOPIC, 2);
      subscription.setRetainAsPublished(true);
      consumer.subscribe(new MqttSubscription[]{subscription});

      assertNull(getRetainedMessageQueue(TOPIC));

      // send retained message
      MqttClient producer = createPahoClient("producer");
      producer.connect();
      producer.publish(TOPIC, "retained".getBytes(), 2, true);
      Wait.assertTrue(() -> getRetainedMessageQueue(TOPIC).getMessageCount() == 1, 2000, 100);
      producer.disconnect();
      producer.close();

      assertTrue(latchOne.await(2, TimeUnit.SECONDS));

      producer = createPahoClient("producer");
      producer.connect();
      producer.publish(TOPIC, "unretained".getBytes(), 2, false);
      producer.disconnect();
      producer.close();

      assertTrue(latchTwo.await(2, TimeUnit.SECONDS));

      consumer.disconnect();
      consumer.close();
   }

   /**
    * [MQTT-3.3.1-13] as above, for a subscription whose topic filter is not the topic name of the message.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testRetainAsPublishedOneOnEstablishedWildcardSubscription() throws Exception {
      testRetainAsPublishedOnEstablishedSubscription(this.getTopicName() + "/#", true);
   }

   /**
    * [MQTT-3.3.1-12] as above, for a subscription whose topic filter is not the topic name of the message.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testRetainAsPublishedZeroOnEstablishedWildcardSubscription() throws Exception {
      testRetainAsPublishedOnEstablishedSubscription(this.getTopicName() + "/#", false);
   }

   /**
    * [MQTT-3.3.1-13] as above, for a shared subscription.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testRetainAsPublishedOneOnEstablishedSharedSubscription() throws Exception {
      testRetainAsPublishedOnEstablishedSubscription("$share/group/" + this.getTopicName() + "/a", true);
   }

   private void testRetainAsPublishedOnEstablishedSubscription(String topicFilter, boolean retainAsPublished) throws Exception {
      final String CONSUMER_ID = RandomUtil.randomUUIDString();
      final String TOPIC = this.getTopicName() + "/a";

      // create consumer
      final CountDownLatch latch = new CountDownLatch(1);
      final AtomicBoolean retained = new AtomicBoolean(!retainAsPublished);
      MqttClient consumer = createPahoClient(CONSUMER_ID);
      consumer.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            assertEqualsByteArrays("retained".getBytes(StandardCharsets.UTF_8), message.getPayload());
            retained.set(message.isRetained());
            latch.countDown();
         }
      });
      consumer.connect();
      MqttSubscription subscription = new MqttSubscription(topicFilter, 1);
      subscription.setRetainAsPublished(retainAsPublished);
      consumer.subscribe(new MqttSubscription[]{subscription});

      // send retained message
      MqttClient producer = createPahoClient("producer");
      producer.connect();
      producer.publish(TOPIC, "retained".getBytes(), 1, true);
      producer.disconnect();
      producer.close();

      assertTrue(latch.await(2, TimeUnit.SECONDS));
      assertEquals(retainAsPublished, retained.get());
      consumer.disconnect();
      consumer.close();
   }

   /**
    * [MQTT-3.3.2-3] The Topic Name in a PUBLISH packet sent by a Server to a subscribing Client MUST match the
    * Subscription’s Topic Filter.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testTopicFilter() throws Exception {
      final String PREFIX = "myTopic/";
      final String TOPIC = PREFIX + RandomUtil.randomUUIDString();
      final String TOPIC_FILTER = PREFIX + "#";

      final CountDownLatch latch = new CountDownLatch(1);
      MqttClient consumer = createPahoClient("consumer");
      MqttConnectionOptions options = new MqttConnectionOptionsBuilder()
         .cleanStart(false)
         .sessionExpiryInterval(300L)
         .build();
      consumer.connect(options);
      consumer.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            System.out.println(topic + ", " + PREFIX);
            assertTrue(topic.startsWith(PREFIX));
            latch.countDown();
         }
      });
      consumer.subscribe(TOPIC_FILTER, 1);

      MqttClient producer = createPahoClient("producer");
      producer.connect();
      producer.publish(TOPIC, "hello".getBytes(), 1, false);
      producer.disconnect();
      producer.close();

      assertTrue(latch.await(2, TimeUnit.SECONDS));
      consumer.disconnect();
      consumer.close();
   }

   /**
    * [MQTT-3.3.2-3] The Topic Name in a PUBLISH packet sent by a Server to a subscribing Client MUST match the
    * Subscription’s Topic Filter.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testX() throws Exception {
      final String PREFIX = "";
      final String TOPIC = PREFIX + RandomUtil.randomUUIDString();

      final CountDownLatch latch = new CountDownLatch(1);
      MqttClient consumer = createPahoClient("consumer");
      MqttConnectionOptions options = new MqttConnectionOptionsBuilder()
         .cleanStart(false)
         .sessionExpiryInterval(300L)
         .build();
      consumer.connect(options);
      consumer.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            System.out.println(topic + ", " + PREFIX);
            assertTrue(topic.startsWith(PREFIX));
            latch.countDown();
         }
      });
      consumer.subscribe(TOPIC, 1);

      MqttClient producer = createPahoClient("producer");
      producer.connect();
      producer.publish(TOPIC, "hello".getBytes(), 1, false);
      producer.disconnect();
      producer.close();

      assertTrue(latch.await(2, TimeUnit.SECONDS));
      consumer.disconnect();
      consumer.close();
   }

   /**
    * [MQTT-3.3.2-4] A Server MUST send the Payload Format Indicator unaltered to all subscribers receiving the message.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testPayloadFormatIndicatorTrue() throws Exception {
      internalTestPayloadFormatIndicator(true);
   }

   /**
    * [MQTT-3.3.2-4] A Server MUST send the Payload Format Indicator unaltered to all subscribers receiving the message.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testPayloadFormatIndicatorFalse() throws Exception {
      internalTestPayloadFormatIndicator(false);
   }

   private void internalTestPayloadFormatIndicator(boolean payloadFormatIndicator) throws Exception {
      final String CONSUMER_ID = RandomUtil.randomUUIDString();
      final String TOPIC = this.getTopicName();

      final CountDownLatch latch = new CountDownLatch(1);
      MqttClient consumer = createPahoClient(CONSUMER_ID);
      MqttConnectionOptions options = new MqttConnectionOptionsBuilder()
         .cleanStart(false)
         .sessionExpiryInterval(300L)
         .build();
      consumer.connect(options);
      consumer.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            if (payloadFormatIndicator) {
               assertTrue(message.getProperties().getPayloadFormat());
            } else {
               assertFalse(message.getProperties().getPayloadFormat());
            }
            latch.countDown();
         }
      });
      consumer.subscribe(TOPIC, 2);

      MqttClient producer = createPahoClient("producer");
      producer.connect();
      MqttMessage m = new MqttMessage();
      MqttProperties props = new MqttProperties();
      props.setPayloadFormat(payloadFormatIndicator);
      m.setProperties(props);
      m.setQos(2);
      m.setPayload("foo".getBytes(StandardCharsets.UTF_8));
      producer.publish(TOPIC, m);
      producer.disconnect();
      producer.close();

      assertTrue(latch.await(2, TimeUnit.SECONDS));
      consumer.disconnect();
      consumer.close();
   }

   /**
    * [MQTT-3.3.2-5] If the Message Expiry Interval has passed and the Server has not managed to start onward delivery
    * to a matching subscriber, then it MUST delete the copy of the message for that subscriber.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testMessageExpiryIntervalElapsed() throws Exception {
      server.createQueue(QueueConfiguration.of(EXPIRY_ADDRESS).setRoutingType(RoutingType.ANYCAST));
      final String CONSUMER_ID = RandomUtil.randomUUIDString();
      final String TOPIC = this.getTopicName();

      final CountDownLatch latch = new CountDownLatch(1);
      MqttClient consumer = createPahoClient(CONSUMER_ID);
      MqttConnectionOptions options = new MqttConnectionOptionsBuilder()
         .cleanStart(false)
         .sessionExpiryInterval(300L)
         .build();
      consumer.connect(options);
      consumer.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            latch.countDown();
         }
      });
      consumer.subscribe(TOPIC, 2);
      consumer.disconnect();

      MqttClient producer = createPahoClient("producer");
      producer.connect();
      MqttMessage m = new MqttMessage();
      MqttProperties props = new MqttProperties();
      props.setMessageExpiryInterval(2L);
      m.setProperties(props);
      m.setQos(2);
      m.setPayload("foo".getBytes(StandardCharsets.UTF_8));
      producer.publish(TOPIC, m);
      producer.disconnect();
      producer.close();

      Wait.assertEquals(1L, () -> getSubscriptionQueue(TOPIC, CONSUMER_ID).getMessageCount(), 1000, 100);
      Wait.assertEquals(1L, () -> server.locateQueue("EXPIRY").getMessageCount(), 3000, 100);
      Wait.assertEquals(0L, () -> getSubscriptionQueue(TOPIC, CONSUMER_ID).getMessageCount(), 1000, 100);

      consumer.connect(options);
      assertFalse(latch.await(1, TimeUnit.SECONDS));
      consumer.disconnect();
   }

   /**
    * [MQTT-3.3.2-6] The PUBLISH packet sent to a Client by the Server MUST contain a Message Expiry Interval set to the
    * received value minus the time that the message has been waiting in the Server.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testMessageExpiryIntervalReturnValue() throws Exception {
      server.createQueue(QueueConfiguration.of(EXPIRY_ADDRESS).setRoutingType(RoutingType.ANYCAST));
      final String CONSUMER_ID = RandomUtil.randomUUIDString();
      final String TOPIC = this.getTopicName();
      final long EXPIRY_INTERVAL = 5L;
      final long SLEEP = 1000;

      final CountDownLatch latch = new CountDownLatch(1);
      MqttClient consumer = createPahoClient(CONSUMER_ID);
      MqttConnectionOptions options = new MqttConnectionOptionsBuilder()
         .cleanStart(false)
         .sessionExpiryInterval(300L)
         .build();
      consumer.connect(options);
      consumer.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            long messageExpiryInterval = message.getProperties().getMessageExpiryInterval();
            System.out.println(messageExpiryInterval);
            assertTrue(messageExpiryInterval <= EXPIRY_INTERVAL + (SLEEP / 1000));
            assertTrue(messageExpiryInterval > 0);
            latch.countDown();
         }
      });
      consumer.subscribe(TOPIC, 2);
      consumer.disconnect();

      MqttClient producer = createPahoClient("producer");
      producer.connect();
      MqttMessage m = new MqttMessage();
      MqttProperties props = new MqttProperties();
      props.setMessageExpiryInterval(EXPIRY_INTERVAL);
      m.setProperties(props);
      m.setQos(2);
      m.setPayload("foo".getBytes(StandardCharsets.UTF_8));
      producer.publish(TOPIC, m);
      producer.disconnect();
      producer.close();

      Wait.assertEquals(1L, () -> getSubscriptionQueue(TOPIC, CONSUMER_ID).getMessageCount(), 500, 100);

      Thread.sleep(SLEEP);

      consumer.connect(options);
      assertTrue(latch.await(1, TimeUnit.SECONDS));
      consumer.disconnect();
   }

   /**
    * [MQTT-3.3.2-11] A Server MUST NOT send a PUBLISH packet with a Topic Alias greater than the Topic Alias Maximum
    * value sent by the Client in the CONNECT packet.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testClientTopicAliasMaxFromServer() throws Exception {
      final String CONSUMER_ID = RandomUtil.randomUUIDString();
      final String TOPIC = this.getTopicName();
      final int MESSAGE_COUNT = 25;
      final int ALIAS_MAX = 5;

      MqttClient consumer = createPahoClient(CONSUMER_ID);
      MqttConnectionOptions options = new MqttConnectionOptionsBuilder()
         .topicAliasMaximum(ALIAS_MAX)
         .build();
      consumer.connect(options);
      CountDownLatch latch = new CountDownLatch(MESSAGE_COUNT);
      consumer.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            Integer topicAlias = message.getProperties().getTopicAlias();
            if (topicAlias != null) {
               assertTrue(topicAlias <= ALIAS_MAX);
            }
            latch.countDown();
         }
      });
      consumer.subscribe(TOPIC + "/#", 2);

      MqttClient producer = createPahoClient("producer");
      producer.connect();
      for (int i = 0; i < MESSAGE_COUNT; i++) {
         producer.publish(TOPIC + "/" + i, ("foo" + i).getBytes(StandardCharsets.UTF_8), 2, false);
      }
      producer.disconnect();
      producer.close();

      assertTrue(latch.await(2, TimeUnit.SECONDS));
      consumer.disconnect();
   }

   /**
    * [MQTT-3.3.2-7] A receiver MUST NOT carry forward any Topic Alias mappings from one Network Connection to another.
    *
    * Unfortunately the Paho MQTT 5 client performs automatic validation and therefore refuses to send a message with an
    * empty topic even though the topic-alias is set appropriately. Therefore, this test won't actually run with the
    * current client implementation. However, I built the client locally omitting the validation logic and the test
    * passed as expected.
    *
    * I'm leaving this test here with @Ignore to demonstrate how to theoretically re-produce the problem. The problem
    * was originally discovered using https://github.com/eclipse/paho.mqtt.testing/tree/master/interoperability to run
    * the command:
    *
    *  python3 client_test5.py Test.test_client_topic_alias
    */
   @Disabled
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testTopicAliasesNotCarriedForward() throws Exception {
      final String TOPIC = "myTopicName";

      MqttProperties properties = new MqttProperties();
      properties.setTopicAlias(1);

      MqttClient producer = createPahoClient("producer");
      MqttConnectionOptions options = new MqttConnectionOptionsBuilder()
         .topicAliasMaximum(2)
         .sessionExpiryInterval(999L)
         .cleanStart(false)
         .build();
      producer.connect(options);
      MqttMessage m = new MqttMessage();
      m.setProperties(properties);
      producer.publish(TOPIC, m);
      m = new MqttMessage();
      m.setProperties(properties);
      producer.publish("", m);
      producer.disconnect();
      // reconnect back to the old session, but the topic alias should now be gone so publishing should fail
      producer.connect(options);
      m = new MqttMessage();
      m.setProperties(properties);
      try {
         producer.publish("", m);
         fail("Publishing should fail here due to an invalid topic alias");
      } catch (Exception e) {
         // ignore
      }
      assertFalse(producer.isConnected());
      producer.close();
   }

   /**
    * [MQTT-3.3.2-12] A Server MUST accept all Topic Alias values greater than 0 and less than or equal to the Topic
    * Alias Maximum value that it returned in the CONNACK packet.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testServerTopicAliasMax() throws Exception {
      final String CONSUMER_ID = RandomUtil.randomUUIDString();
      final String TOPIC = this.getTopicName();
      final int MESSAGE_COUNT = 25;
      final int ALIAS_MAX = 5;
      setAcceptorProperty("topicAliasMaximum=" + ALIAS_MAX);

      MqttClient consumer = createPahoClient(CONSUMER_ID);
      MqttConnectionOptions options = new MqttConnectionOptionsBuilder()
         .topicAliasMaximum(ALIAS_MAX)
         .build();
      consumer.connect(options);
      CountDownLatch latch = new CountDownLatch(MESSAGE_COUNT);
      consumer.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            Integer topicAlias = message.getProperties().getTopicAlias();
            if (topicAlias != null) {
               assertTrue(topicAlias <= ALIAS_MAX);
            }
            latch.countDown();
         }
      });
      consumer.subscribe(TOPIC + "/#", 2);

      MqttClient producer = createPahoClient("producer");
      producer.connect();
      for (int i = 0; i < MESSAGE_COUNT; i++) {
         producer.publish(TOPIC + "/" + i, ("foo" + i).getBytes(StandardCharsets.UTF_8), 2, false);
      }
      producer.disconnect();
      producer.close();

      assertTrue(latch.await(2, TimeUnit.SECONDS));
      consumer.disconnect();
   }

   /**
    * From section 3.3.2.3.4 of the MQTT 5 specification:
    *
    * A sender can modify the Topic Alias mapping by sending another PUBLISH in the same Network Connection with the
    * same Topic Alias value and a different non-zero length Topic Name.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testModifiedTopicAlias() throws Exception {
      final String TOPIC_1 = this.getTopicName() + "1";
      final String TOPIC_2 = this.getTopicName() + "2";

      MqttClient consumer1 = createPahoClient("consumer1");
      CountDownLatch latch1 = new CountDownLatch(1);
      consumer1.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            String payload = new String(message.getPayload());
            if (payload.equals("first")) {
               latch1.countDown();
            }
         }
      });
      consumer1.connect();
      consumer1.subscribe(TOPIC_1, 1);

      MqttClient consumer2 = createPahoClient("consumer2");
      CountDownLatch latch2 = new CountDownLatch(1);
      consumer2.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            String payload = new String(message.getPayload());
            if (payload.equals("second")) {
               latch2.countDown();
            }
         }
      });
      consumer2.connect();
      consumer2.subscribe(TOPIC_2, 1);

      MqttClient producer = createPahoClient("producer");
      producer.connect();

      MqttProperties properties = new MqttProperties();
      properties.setTopicAlias(1);
      MqttMessage m = new MqttMessage();
      m.setProperties(properties);
      m.setQos(1);
      m.setRetained(false);
      m.setPayload("first".getBytes(StandardCharsets.UTF_8));
      producer.publish(TOPIC_1, m);
      m.setPayload("second".getBytes(StandardCharsets.UTF_8));
      producer.publish(TOPIC_2, m);

      producer.disconnect();
      producer.close();

      assertTrue(latch1.await(2, TimeUnit.SECONDS));
      assertTrue(latch2.await(2, TimeUnit.SECONDS));

      consumer1.disconnect();
      consumer1.close();
      consumer2.disconnect();
      consumer2.close();
   }

   /**
    * [MQTT-3.3.2-15] The Server MUST send the Response Topic unaltered to all subscribers receiving the Application
    * Message.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testResponseTopicUnaltered() throws Exception {
      final String CONSUMER_ID = RandomUtil.randomUUIDString();
      final String TOPIC = this.getTopicName();
      final String RESPONSE_TOPIC = "myResponseTopic/a";

      final CountDownLatch latch = new CountDownLatch(1);
      MqttClient consumer = createPahoClient(CONSUMER_ID);
      consumer.connect();
      consumer.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            assertEquals(RESPONSE_TOPIC, message.getProperties().getResponseTopic());
            latch.countDown();
         }
      });
      consumer.subscribe(TOPIC, 2);

      MqttClient producer = createPahoClient("producer");
      producer.connect();
      MqttMessage m = new MqttMessage();
      m.setQos(2);
      MqttProperties properties = new MqttProperties();
      properties.setResponseTopic(RESPONSE_TOPIC);
      m.setProperties(properties);
      producer.publish(TOPIC, m);
      producer.disconnect();
      producer.close();

      assertTrue(latch.await(2, TimeUnit.SECONDS));
      consumer.disconnect();
      consumer.close();
   }

   /**
    * [MQTT-3.3.2-16] The Server MUST send the Correlation Data unaltered to all subscribers receiving the Application
    * Message.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testCorrelationDataUnaltered() throws Exception {
      final String CONSUMER_ID = RandomUtil.randomUUIDString();
      final String TOPIC = this.getTopicName();
      final byte[] CORRELATION_DATA = "myCorrelationData".getBytes(StandardCharsets.UTF_8);

      final CountDownLatch latch = new CountDownLatch(1);
      MqttClient consumer = createPahoClient(CONSUMER_ID);
      consumer.connect();
      consumer.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            assertEqualsByteArrays(CORRELATION_DATA, message.getProperties().getCorrelationData());
            latch.countDown();
         }
      });
      consumer.subscribe(TOPIC, 2);

      MqttClient producer = createPahoClient("producer");
      producer.connect();
      MqttMessage m = new MqttMessage();
      m.setQos(2);
      MqttProperties properties = new MqttProperties();
      properties.setCorrelationData(CORRELATION_DATA);
      m.setProperties(properties);
      producer.publish(TOPIC, m);
      producer.disconnect();
      producer.close();

      assertTrue(latch.await(2, TimeUnit.SECONDS));
      consumer.disconnect();
      consumer.close();
   }

   /**
    * [MQTT-3.3.2-17] The Server MUST send all User Properties unaltered in a PUBLISH packet when forwarding the
    * Application Message to a Client.
    *
    * [MQTT-3.3.2-18] The Server MUST maintain the order of User Properties when forwarding the Application Message.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testUserProperties() throws Exception {
      final String CONSUMER_ID = RandomUtil.randomUUIDString();
      final String TOPIC = this.getTopicName();
      final int USER_PROPERTY_COUNT = 10;
      List<UserProperty> userProperties = new ArrayList<>();
      for (int i = 0; i < USER_PROPERTY_COUNT; i++) {
         userProperties.add(new UserProperty(RandomUtil.randomUUIDString(), RandomUtil.randomUUIDString()));
      }

      final CountDownLatch latch = new CountDownLatch(1);
      MqttClient consumer = createPahoClient(CONSUMER_ID);
      consumer.connect();
      consumer.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            List<UserProperty> receivedUserProperties = message.getProperties().getUserProperties();
            for (int i = 0; i < USER_PROPERTY_COUNT; i++) {
               assertEquals(userProperties.get(i).getKey(), receivedUserProperties.get(i).getKey());
               assertEquals(userProperties.get(i).getValue(), receivedUserProperties.get(i).getValue());
            }
            latch.countDown();
         }
      });
      consumer.subscribe(TOPIC, 2);

      MqttClient producer = createPahoClient("producer");
      producer.connect();
      MqttMessage m = new MqttMessage();
      m.setQos(2);
      MqttProperties properties = new MqttProperties();
      properties.setUserProperties(userProperties);
      m.setProperties(properties);
      producer.publish(TOPIC, m);
      producer.disconnect();
      producer.close();

      assertTrue(latch.await(2, TimeUnit.SECONDS));
      consumer.disconnect();
      consumer.close();
   }

   /**
    * [MQTT-3.3.2-20] A Server MUST send the Content Type unaltered to all subscribers receiving the Application
    * Message.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testContentTypeUnaltered() throws Exception {
      final String CONSUMER_ID = RandomUtil.randomUUIDString();
      final String TOPIC = this.getTopicName();
      final String CONTENT_TYPE = "myContentType";

      final CountDownLatch latch = new CountDownLatch(1);
      MqttClient consumer = createPahoClient(CONSUMER_ID);
      consumer.connect();
      consumer.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            assertEquals(CONTENT_TYPE, message.getProperties().getContentType());
            latch.countDown();
         }
      });
      consumer.subscribe(TOPIC, 2);

      MqttClient producer = createPahoClient("producer");
      producer.connect();
      MqttMessage m = new MqttMessage();
      m.setQos(2);
      MqttProperties properties = new MqttProperties();
      properties.setContentType(CONTENT_TYPE);
      m.setProperties(properties);
      producer.publish(TOPIC, m);
      producer.disconnect();
      producer.close();

      assertTrue(latch.await(2, TimeUnit.SECONDS));
      consumer.disconnect();
      consumer.close();
   }

   /**
    * [MQTT-3.3.4-1] The receiver of a PUBLISH Packet MUST respond with the packet as determined by the QoS in the
    * PUBLISH Packet.
    *
    * Spec says response should be PUBREC: https://docs.oasis-open.org/mqtt/mqtt/v5.0/os/mqtt-v5.0-os.html#_Toc3901120
    * However, the Paho client returns a PUBCOMP instead which is the *end result* of the QoS 2 protocol exchange
    * described at https://docs.oasis-open.org/mqtt/mqtt/v5.0/os/mqtt-v5.0-os.html#_QoS_2:_Exactly.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testQoS2() throws Exception {
      internalTestQoS(2);
   }

   /**
    * [MQTT-3.3.4-1] The receiver of a PUBLISH Packet MUST respond with the packet as determined by the QoS in the
    * PUBLISH Packet.
    *
    * Spec says response should be PUBACK: https://docs.oasis-open.org/mqtt/mqtt/v5.0/os/mqtt-v5.0-os.html#_Toc3901120
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testQoS1() throws Exception {
      internalTestQoS(1);
   }

   /**
    * [MQTT-3.3.4-1] The receiver of a PUBLISH Packet MUST respond with the packet as determined by the QoS in the
    * PUBLISH Packet.
    *
    * Spec says there should be no response: https://docs.oasis-open.org/mqtt/mqtt/v5.0/os/mqtt-v5.0-os.html#_Toc3901120
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testQoS0() throws Exception {
      internalTestQoS(0);
   }

   private void internalTestQoS(int qos) throws Exception {
      final String TOPIC = this.getTopicName();

      MqttClient producer = createPahoClient("producer");
      producer.connect();
      CountDownLatch latch = new CountDownLatch(1);
      producer.setCallback(new DefaultMqttCallback() {
         @Override
         public void deliveryComplete(IMqttToken token) {
            int qos = ((MqttPublish)token.getRequestMessage()).getQoS();
            if (qos == 0) {
               assertNull(token.getResponse());
            } else if (qos == 1) {
               assertEquals(MqttWireMessage.MESSAGE_TYPE_PUBACK, token.getResponse().getType());
            } else if (qos == 2) {
               assertEquals(MqttWireMessage.MESSAGE_TYPE_PUBCOMP, token.getResponse().getType());
            } else {
               fail("unrecognized qos");
            }
            latch.countDown();
         }
      });
      producer.publish(TOPIC, new byte[0], qos, false);
      assertTrue(latch.await(2, TimeUnit.SECONDS));
      producer.disconnect();
      producer.close();
   }

   /**
    * [MQTT-3.3.4-2] When Clients make subscriptions with Topic Filters that include wildcards, it is possible for a
    * Client’s subscriptions to overlap so that a published message might match multiple filters. In this case the
    * Server MUST deliver the message to the Client respecting the maximum QoS of all the matching subscriptions.
    *
    * This test exercises 9 different scenarios represented visually by this matrix:
    *
    *  =========================================================
    *  | QoS of publish | QoS of subscription | QoS of receive |
    *  |================|=====================|================|
    *  |       0        |          0          |        0       |
    *  |       0        |          1          |        0       |
    *  |       0        |          2          |        0       |
    *  |       1        |          0          |        0       |
    *  |       1        |          1          |        1       |
    *  |       1        |          2          |        1       |
    *  |       2        |          0          |        0       |
    *  |       2        |          1          |        1       |
    *  |       2        |          2          |        2       |
    *  |================|=====================|================|
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testOverlappingSubscriptionsWithDifferentQoSMaximums() throws Exception {
      final String TOPIC = "foo/a/b/c";

      final CountDownLatch consumerLatch = new CountDownLatch(9);

      MqttClient consumer2 = createPahoClient(RandomUtil.randomUUIDString());
      consumer2.connect();
      consumer2.setCallback(new TestCallback(consumerLatch, 2));
      consumer2.subscribe("foo/a/b/#", 2);

      MqttClient consumer1 = createPahoClient(RandomUtil.randomUUIDString());
      consumer1.connect();
      consumer1.setCallback(new TestCallback(consumerLatch, 1));
      consumer1.subscribe("foo/a/#", 1);

      MqttClient consumer0 = createPahoClient(RandomUtil.randomUUIDString());
      consumer0.connect();
      consumer0.setCallback(new TestCallback(consumerLatch, 0));
      consumer0.subscribe("foo/#", 0);

      MqttClient producer = createPahoClient("producer");
      producer.connect();
      for (int i = 0; i < 3; i++) {
         producer.publish(TOPIC, Integer.toString(i).getBytes(StandardCharsets.UTF_8), i, false);
      }
      producer.disconnect();
      producer.close();

      assertTrue(consumerLatch.await(2, TimeUnit.SECONDS));
      consumer2.disconnect();
      consumer2.close();
      consumer1.disconnect();
      consumer1.close();
      consumer0.disconnect();
      consumer0.close();
   }

   protected class TestCallback implements MQTT5TestSupport.DefaultMqttCallback {
      private CountDownLatch latch;
      private int qosOfSubscription;

      public TestCallback(CountDownLatch latch, int qosOfSubscription) {
         this.latch = latch;
         this.qosOfSubscription = qosOfSubscription;
      }
      @Override
      public void messageArrived(String topic, MqttMessage message) throws Exception {
         int sentAs = Integer.parseInt(new String(message.getPayload(), StandardCharsets.UTF_8));
         logger.info("QoS of publish: {}; QoS of subscription: {}; QoS of receive: {}", sentAs, qosOfSubscription, message.getQos());
         if (sentAs == 0) {
            assertEquals(0, message.getQos());
         } else if (sentAs == 1) {
            assertEquals((qosOfSubscription == 0 ? 0 : 1), message.getQos());
         } else if (sentAs == 2) {
            assertEquals(qosOfSubscription, message.getQos());
         } else {
            fail("invalid qos");
         }
         latch.countDown();
      }
   }

   /**
    * [MQTT-3.3.4-3] If the Client specified a Subscription Identifier for any of the overlapping subscriptions the
    * Server MUST send those Subscription Identifiers in the message which is published as the result of the
    * subscriptions.
    *
    * [MQTT-3.3.4-4] If the Server sends a single copy of the message it MUST include in the PUBLISH packet the
    * Subscription Identifiers for all matching subscriptions which have a Subscription Identifiers, their order is not
    * significant.
    *
    * [MQTT-3.3.4-5] If the Server sends multiple PUBLISH packets it MUST send, in each of them, the Subscription
    * Identifier of the matching subscription if it has a Subscription Identifier.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testSubscriptionIdentifierMultiLevel() throws Exception {
      // even though only 3 messages are sent, 6 messages will be received due to the overlapping subscriptions
      final CountDownLatch consumerLatch = new CountDownLatch(6);

      MqttAsyncClient consumer = createAsyncPahoClient(RandomUtil.randomUUIDString());
      consumer.connect().waitForCompletion();
      consumer.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            try {
               List<Integer> subscriptionIdentifiers = message.getProperties() != null ? message
                  .getProperties()
                  .getSubscriptionIdentifiers() : null;
               System.out.println("subscriptionIdentifiers: " + subscriptionIdentifiers + "; message: " + message);
               switch (new String(message.getPayload(), StandardCharsets.UTF_8)) {
                  case "foo" -> {
                     assertTrue(subscriptionIdentifiers.contains(1));
                     assertEquals(1, subscriptionIdentifiers.size());
                  }
                  case "foo/a" -> {
                     assertTrue(subscriptionIdentifiers.contains(1));
                     assertTrue(subscriptionIdentifiers.contains(2));
                     assertEquals(2, subscriptionIdentifiers.size());
                  }
                  case "foo/a/b" -> {
                     assertTrue(subscriptionIdentifiers.contains(1));
                     assertTrue(subscriptionIdentifiers.contains(2));
                     assertTrue(subscriptionIdentifiers.contains(3));
                     assertEquals(3, subscriptionIdentifiers.size());
                  }
                  case "foo/a/b/c" -> {
                     assertTrue(subscriptionIdentifiers.contains(1));
                     assertTrue(subscriptionIdentifiers.contains(2));
                     assertTrue(subscriptionIdentifiers.contains(3));
                     assertTrue(subscriptionIdentifiers.contains(4));
                     assertEquals(4, subscriptionIdentifiers.size());
                  }
                  default -> fail("invalid subscription identifier");
               }

               consumerLatch.countDown();
            } catch (Throwable t) {
               t.printStackTrace();
            }
         }
      });

      MqttProperties subscription1Properties = new MqttProperties();
      subscription1Properties.setSubscriptionIdentifier(1);
      consumer.subscribe(new MqttSubscription[]{new MqttSubscription("foo/#", 2)}, null, null, subscription1Properties).waitForCompletion();

      MqttProperties subscription2Properties = new MqttProperties();
      subscription2Properties.setSubscriptionIdentifier(2);
      consumer.subscribe(new MqttSubscription[]{new MqttSubscription("foo/a/#", 2)}, null, null, subscription2Properties).waitForCompletion();

      MqttProperties subscription3Properties = new MqttProperties();
      subscription3Properties.setSubscriptionIdentifier(3);
      consumer.subscribe(new MqttSubscription[]{new MqttSubscription("foo/a/b/#", 2)}, null, null, subscription3Properties).waitForCompletion();

      MqttProperties subscription4Properties = new MqttProperties();
      subscription4Properties.setSubscriptionIdentifier(4);
      consumer.subscribe(new MqttSubscription[]{new MqttSubscription("foo/a/b/c/#", 2)}, null, null, subscription4Properties).waitForCompletion();

      MqttClient producer = createPahoClient("producer");
      producer.connect();
      producer.publish("foo", "foo".getBytes(StandardCharsets.UTF_8), 2, false);
      producer.publish("foo/a", "foo/a".getBytes(StandardCharsets.UTF_8), 2, false);
      producer.publish("foo/a/b", "foo/a/b".getBytes(StandardCharsets.UTF_8), 2, false);
      producer.publish("foo/a/b/c", "foo/a/b/c".getBytes(StandardCharsets.UTF_8), 2, false);
      producer.disconnect();
      producer.close();

      assertTrue(consumerLatch.await(1, TimeUnit.SECONDS));
      consumer.disconnect().waitForCompletion();
      consumer.close();
   }

   /**
    * [MQTT-3.3.4-3] If the Client specified a Subscription Identifier for any of the overlapping subscriptions the
    * Server MUST send those Subscription Identifiers in the message which is published as the result of the
    * subscriptions.
    *
    * [MQTT-3.3.4-4] If the Server sends a single copy of the message it MUST include in the PUBLISH packet the
    * Subscription Identifiers for all matching subscriptions which have a Subscription Identifiers, their order is not
    * significant.
    *
    * [MQTT-3.3.4-5] If the Server sends multiple PUBLISH packets it MUST send, in each of them, the Subscription
    * Identifier of the matching subscription if it has a Subscription Identifier.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testSubscriptionIdentifierSingleLevel() throws Exception {
      final CountDownLatch consumerLatch = new CountDownLatch(6);

      MqttAsyncClient consumer = createAsyncPahoClient(RandomUtil.randomUUIDString());
      consumer.connect().waitForCompletion();
      consumer.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            try {
               List<Integer> subscriptionIdentifiers = message.getProperties() != null ? message
                  .getProperties()
                  .getSubscriptionIdentifiers() : null;
               System.out.println("subscriptionIdentifiers: " + subscriptionIdentifiers + "; message: " + message);
               if (isPayloadEqual(message, "foo/a")) {
                  assertTrue(subscriptionIdentifiers.contains(3));
                  assertEquals(1, subscriptionIdentifiers.size());
               } else if (isPayloadEqual(message, "foo/a-reset")) {
                  assertEquals(0, subscriptionIdentifiers.size());
               } else if (isPayloadEqual(message, "foo/a/b") || isPayloadEqual(message, "foo/a/b-reset")) {
                  assertTrue(subscriptionIdentifiers.contains(2));
                  assertEquals(1, subscriptionIdentifiers.size());
               } else if (isPayloadEqual(message, "foo/a/b/c") || isPayloadEqual(message, "foo/a/b/c-reset")) {
                  assertTrue(subscriptionIdentifiers.contains(1));
                  assertEquals(1, subscriptionIdentifiers.size());
               } else {
                  fail("invalid subscription identifier");
               }

               consumerLatch.countDown();
            } catch (Throwable t) {
               t.printStackTrace();
            }
         }
      });

      MqttProperties subscription1Properties = new MqttProperties();
      subscription1Properties.setSubscriptionIdentifier(1);
      consumer.subscribe(new MqttSubscription[]{new MqttSubscription("foo/a/b/+", 2)}, null, null, subscription1Properties).waitForCompletion();

      MqttProperties subscription2Properties = new MqttProperties();
      subscription2Properties.setSubscriptionIdentifier(2);
      consumer.subscribe(new MqttSubscription[]{new MqttSubscription("foo/a/+", 2)}, null, null, subscription2Properties).waitForCompletion();

      MqttProperties subscription3Properties = new MqttProperties();
      subscription3Properties.setSubscriptionIdentifier(3);
      consumer.subscribe(new MqttSubscription[]{new MqttSubscription("foo/+", 2)}, null, null, subscription3Properties).waitForCompletion();

      MqttClient producer = createPahoClient("producer");
      producer.connect();
      producer.publish("foo/a", "foo/a".getBytes(StandardCharsets.UTF_8), 2, false);
      producer.publish("foo/a/b", "foo/a/b".getBytes(StandardCharsets.UTF_8), 2, false);
      producer.publish("foo/a/b/c", "foo/a/b/c".getBytes(StandardCharsets.UTF_8), 2, false);

      //remove association with subscription ID from the topic `foo/+`
      consumer.subscribe(new MqttSubscription[]{new MqttSubscription("foo/+", 2)}, null, null, null).waitForCompletion();
      producer.publish("foo/a", "foo/a-reset".getBytes(StandardCharsets.UTF_8), 2, false);
      producer.publish("foo/a/b", "foo/a/b-reset".getBytes(StandardCharsets.UTF_8), 2, false);
      producer.publish("foo/a/b/c", "foo/a/b/c-reset".getBytes(StandardCharsets.UTF_8), 2, false);


      producer.disconnect();
      producer.close();

      assertTrue(consumerLatch.await(1, TimeUnit.SECONDS));
      consumer.disconnect().waitForCompletion();
      consumer.close();
   }

   private static boolean isPayloadEqual(MqttMessage message, String compare) {
      return Arrays.equals(message.getPayload(), compare.getBytes(StandardCharsets.UTF_8));
   }

   /**
    * [MQTT-3.3.4-9] The Server MUST NOT send more than Receive Maximum QoS 1 and QoS 2 PUBLISH packets for which it has
    * not received PUBACK, PUBCOMP, or PUBREC with a Reason Code of 128 or greater from the Client.
    *
    * This is impossible to test with the Paho client directly because it doesn't invoke the callback concurrently.
    * Therefore, we must use interceptors as a kind of hack to determine whether we're implementing flow control
    * properly.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testReceiveMaximumSetByClient() throws Exception {
      testReceiveMaximum(false);
   }

   /**
    * @see PublishTests#testReceiveMaximumSetByClient()
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testImplicitReceiveMaximumByDefaultMaximumInFlightPublishMessages() throws Exception {
      testReceiveMaximum(true);
   }

   private void testReceiveMaximum(boolean useDefault) throws Exception {
      AtomicInteger count = new AtomicInteger(0);
      AtomicBoolean failed = new AtomicBoolean(false);
      final int MESSAGE_COUNT = 50;
      final int RECEIVE_MAXIMUM = 10;
      MQTTInterceptor incomingInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBACK || packet.fixedHeader().messageType() == MqttMessageType.PUBREC) {
            count.decrementAndGet();
         }
         return true;
      };

      MQTTInterceptor outgoingInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBLISH) {
            if (count.incrementAndGet() > RECEIVE_MAXIMUM) {
               failed.set(true);
            }
         }
         return true;
      };
      server.getRemotingService().addIncomingInterceptor(incomingInterceptor);
      server.getRemotingService().addOutgoingInterceptor(outgoingInterceptor);

      if (useDefault) {
         getProtocolManager().setDefaultMaximumInFlightPublishMessages(RECEIVE_MAXIMUM); // must be used when missing Receive Maximum from the client
      } else {
         getProtocolManager().setDefaultMaximumInFlightPublishMessages(1); // Receive Maximum from the client must override this
      }

      final String TOPIC = this.getTopicName();

      final CountDownLatch latch = new CountDownLatch(MESSAGE_COUNT);
      final String CONSUMER_ID = "consumer";
      MqttAsyncClient consumer = createAsyncPahoClient(CONSUMER_ID);
      MqttConnectionOptions options = new MqttConnectionOptions();
      if (!useDefault) {
         options.setReceiveMaximum(RECEIVE_MAXIMUM);
      }
      consumer.connect(options).waitForCompletion();
      consumer.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            Thread.sleep(250);
            latch.countDown();
         }
      });
      consumer.subscribe(TOPIC, 2).waitForCompletion();

      MqttClient producer = createPahoClient("producer");
      producer.connect();
      for (int i = 0; i < MESSAGE_COUNT; i++) {
         producer.publish(TOPIC, "foo".getBytes(StandardCharsets.UTF_8), (RandomUtil.randomPositiveInt() % 2) + 1, false);
      }
      Wait.assertEquals((long) MESSAGE_COUNT, () -> getSubscriptionQueue(TOPIC, CONSUMER_ID).getMessagesAdded(), 2000, 100);
      producer.disconnect();
      producer.close();

      Wait.assertEquals(0L, () -> getSubscriptionQueue(TOPIC, CONSUMER_ID).getMessageCount(), 15000, 100);
      assertTrue(latch.await(15, TimeUnit.SECONDS));
      assertFalse(failed.get());
      consumer.disconnect();
      consumer.close();
   }

   /**
    * [MQTT-3.3.4-9] The Server MUST NOT send more than Receive Maximum QoS 1 and QoS 2 PUBLISH packets for which it has
    * not received PUBACK, PUBCOMP, or PUBREC with a Reason Code of 128 or greater from the Client.
    *
    * Flow control isn't enforced on QoS 0 messages.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testReceiveMaximumQoS0() throws Exception {
      AtomicInteger count = new AtomicInteger(0);
      AtomicBoolean succeeded = new AtomicBoolean(false);
      final int MESSAGE_COUNT = 50;
      final int RECEIVE_MAXIMUM = 10;
      final String TOPIC = this.getTopicName();

      MQTTInterceptor incomingInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBACK || packet.fixedHeader().messageType() == MqttMessageType.PUBREC) {
            count.decrementAndGet();
         }
         return true;
      };

      MQTTInterceptor outgoingInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBLISH) {
            if (count.incrementAndGet() > RECEIVE_MAXIMUM) {
               succeeded.set(true);
            }
         }
         return true;
      };
      server.getRemotingService().addIncomingInterceptor(incomingInterceptor);
      server.getRemotingService().addOutgoingInterceptor(outgoingInterceptor);
      getProtocolManager().setDefaultMaximumInFlightPublishMessages(1); // must not be taken into account for QoS 0

      final CountDownLatch latch = new CountDownLatch(MESSAGE_COUNT);
      final String CONSUMER_ID = "consumer";
      MqttAsyncClient consumer = createAsyncPahoClient(CONSUMER_ID);
      MqttConnectionOptions options = new MqttConnectionOptions();
      options.setReceiveMaximum(RECEIVE_MAXIMUM);
      consumer.connect(options).waitForCompletion();
      consumer.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            Thread.sleep(100);
            latch.countDown();
         }
      });
      consumer.subscribe(TOPIC, 0).waitForCompletion();

      MqttClient producer = createPahoClient("producer");
      producer.connect();
      for (int i = 0; i < MESSAGE_COUNT; i++) {
         producer.publish(TOPIC, ("foo" + i).getBytes(StandardCharsets.UTF_8), 0, false);
      }
      Wait.assertEquals((long) MESSAGE_COUNT, () -> getSubscriptionQueue(TOPIC, CONSUMER_ID).getMessagesAdded(), 2000, 100);
      producer.disconnect();
      producer.close();

      Wait.assertEquals(0L, () -> getSubscriptionQueue(TOPIC, CONSUMER_ID).getMessageCount(), 8000, 100);
      assertTrue(latch.await(8, TimeUnit.SECONDS));
      assertTrue(succeeded.get());
      consumer.disconnect();
      consumer.close();
   }

   /**
    * [MQTT-3.3.4-10] The Server MUST NOT delay the sending of any packets other than PUBLISH packets due to having sent
    * Receive Maximum PUBLISH packets without receiving acknowledgements for them.
    *
    * Conducting this test with a PINGRESP packet. If the broker sends a PINGRESP to the client while it is delaying
    * PUBLISH packets then it is a success.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testPacketDelayReceiveMaximum() throws Exception {
      AtomicBoolean succeeded = new AtomicBoolean(false);
      final int MESSAGE_COUNT = 2;
      final int RECEIVE_MAXIMUM = 1;
      final String TOPIC = this.getTopicName();
      final String CONSUMER_ID = "consumer";
      final AtomicBoolean messageArrived = new AtomicBoolean(false);

      MQTTInterceptor outgoingInterceptor = (packet, connection) -> {
         if (messageArrived.get() && packet.fixedHeader().messageType() == MqttMessageType.PINGRESP) {
            succeeded.set(true);
         }
         return true;
      };
      server.getRemotingService().addOutgoingInterceptor(outgoingInterceptor);

      final CountDownLatch latch = new CountDownLatch(1);
      MqttClient consumer = createPahoClient(CONSUMER_ID);
      MqttConnectionOptions options = new MqttConnectionOptions();
      options.setReceiveMaximum(RECEIVE_MAXIMUM);
      options.setKeepAliveInterval(2);
      consumer.connect(options);
      consumer.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            messageArrived.set(true);
            latch.await();
         }
      });
      consumer.subscribe(TOPIC, 2);

      MqttClient producer = createPahoClient("producer");
      producer.connect();
      for (int i = 0; i < MESSAGE_COUNT; i++) {
         producer.publish(TOPIC, "foo".getBytes(StandardCharsets.UTF_8), 2, false);
      }
      Wait.assertEquals((long) MESSAGE_COUNT, () -> getSubscriptionQueue(TOPIC, CONSUMER_ID).getMessagesAdded(), 2000, 100);
      producer.disconnect();
      producer.close();

      Wait.assertTrue(() -> succeeded.get(), 40000, 100);
      latch.countDown();
      consumer.disconnect();
      consumer.close();
   }
}
