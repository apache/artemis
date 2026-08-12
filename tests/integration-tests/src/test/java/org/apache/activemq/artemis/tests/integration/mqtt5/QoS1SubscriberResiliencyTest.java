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
package org.apache.activemq.artemis.tests.integration.mqtt5;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTInterceptor;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptionsBuilder;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for QoS 1 protocol resiliency with subscriber reconnections or broker restarts.
 * <p>
 * QoS 1 Protocol Flow (broker sending to subscriber):
 * <ol>
 * <li>Broker sends PUBLISH (QoS=1)</li>
 * <li>Subscriber sends PUBACK</li>
 * </ol>
 * These tests verify that the protocol maintains at-least-once delivery semantics when either clients
 * reconnect or the broker is restarted at each stage of the QoS 1 flow.
 */
public class QoS1SubscriberResiliencyTest extends MQTT5TestSupport {

   protected static final long DEFAULT_TIMEOUT_SEC = 10;

   @Override
   public boolean isProtocolLoggingEnabled() {
      return true;
   }

   /**
    * Verifies that the broker will re-use the same packet ID if it sends a PUBLISH but fails to receive the
    * corresponding PUBACK.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testQoS1BrokerRestartBeforePubAckSent() throws Exception {
      testQoS1FailureBeforePubAckSent(true);
   }

   /**
    * Same test as {@link testQoS1BrokerRestartBeforePubAckSent} but disconnecting the client instead of restarting the
    * broker.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testQoS1ClientDisconnectBeforePubAckSent() throws Exception {
      testQoS1FailureBeforePubAckSent(false);
   }

   public void testQoS1FailureBeforePubAckSent(boolean restart) throws Exception {
      final String TOPIC = "test/resiliency";
      final String SUBSCRIBER_CLIENT_ID = "subscriber";
      final String PUBLISHER_CLIENT_ID = "publisher";

      // Set up interceptor to block the *second* incoming PUBACK.
      // We allow 1 PUBACK so the packet ID goes up to 2 for testing.
      final CountDownLatch pubAckLatch = new CountDownLatch(1);
      AtomicInteger pubAckCount = new AtomicInteger(0);
      final CountDownLatch stopLatch = new CountDownLatch(1);
      MQTTInterceptor pubAckInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBACK && pubAckCount.incrementAndGet() > 1) {
            pubAckLatch.countDown();
            logger.info("Blocking incoming {}", packet.fixedHeader().messageType());
            try {
               stopLatch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
               throw new RuntimeException(e);
            }
            return false;
         }
         logger.info("Allowing incoming {}", packet.fixedHeader().messageType());
         return true;
      };
      server.getRemotingService().addIncomingInterceptor(pubAckInterceptor);

      // Consumer with persistent session
      MqttClient subscriber = createPahoClient(SUBSCRIBER_CLIENT_ID);
      subscriber.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            logger.info("messageArrived({}, {})", topic, message);
         }
      });
      MqttConnectionOptions subscriberOptions = new MqttConnectionOptionsBuilder()
         .cleanStart(false)
         .sessionExpiryInterval(300L)
         .build();
      subscriber.connect(subscriberOptions);
      subscriber.subscribe(TOPIC, 1);

      // Producer
      MqttClient producer = createPahoClient(PUBLISHER_CLIENT_ID);
      producer.connect();

      // Send 2 messages to ensure the packet ID is preserved by the broker.
      // If we just send 1 message it won't be clear if the broker just started generating packet IDs from scratch.
      producer.publish(TOPIC, RandomUtil.randomBytes(), 1, false);
      producer.publish(TOPIC, RandomUtil.randomBytes(), 1, false);

      producer.disconnect();
      producer.close();

      assertTrue(pubAckLatch.await(5, TimeUnit.SECONDS));

      stopLatch.countDown();
      if (restart) {
         server.stop();
         waitForServerToStop(server);
         server.start();
         waitForServerToStart(server);
      } else {
         server.getRemotingService().clearInterceptors();
         server.getActiveMQServerControl().closeConnectionWithID(server.getActiveMQServerControl().listConnectionIDs()[0]);
      }

      assertTrue(getProtocolManager().getStateManager().packetIdCorrelationExists(SUBSCRIBER_CLIENT_ID, 2));

      CountDownLatch packetIdLatch = new CountDownLatch(1);
      MQTTInterceptor pubInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBLISH) {
            if (((MqttPublishMessage)packet).variableHeader().packetId() == 2 && ((MqttPublishMessage)packet).fixedHeader().isDup()) {
               packetIdLatch.countDown();
            }
         }
         return true;
      };
      server.getRemotingService().addOutgoingInterceptor(pubInterceptor);

      reconnectSafely(subscriber);

      assertTrue(packetIdLatch.await(5, TimeUnit.SECONDS), "Didn't find a duplicate PUBLISH with the expected packet id");

      Wait.assertEquals(0L, () -> getSubscriptionQueue(TOPIC, SUBSCRIBER_CLIENT_ID).getMessageCount(), 500, 25);
      assertEquals(0, getProtocolManager().getStateManager().getPacketIdCorrelationSize(SUBSCRIBER_CLIENT_ID));

      subscriber.disconnect();
      subscriber.close();
   }

   /**
    * Verifies that after a complete QoS 1 protocol exchange (PUBLISH, PUBACK), a broker restart does
    * not cause re-delivery.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testQoS1BrokerRestartAfterPubAckSent() throws Exception {
      testQoS1FailureAfterPubAckSent(true);
   }

   /**
    * Same test as {@link testQoS1BrokerRestartAfterPubAckSent} but disconnecting the client instead of restarting the
    * broker.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testQoS1ClientDisconnectAfterPubAckSent() throws Exception {
      testQoS1FailureAfterPubAckSent(false);
   }

   public void testQoS1FailureAfterPubAckSent(boolean restart) throws Exception {
      final String TOPIC = "test/resiliency";
      final String SUBSCRIBER_CLIENT_ID = "subscriber";
      final String PUBLISHER_CLIENT_ID = "publisher";
      AtomicInteger messageCount = new AtomicInteger(0);

      // Subscriber with persistent session
      MqttClient subscriber = createPahoClient(SUBSCRIBER_CLIENT_ID);
      subscriber.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            messageCount.incrementAndGet();
            logger.info("messageArrived({}, {})", topic, message);
         }
      });
      MqttConnectionOptions subscriberOptions = new MqttConnectionOptionsBuilder()
         .cleanStart(false)
         .sessionExpiryInterval(300L)
         .build();
      subscriber.connect(subscriberOptions);
      subscriber.subscribe(TOPIC, 1);

      MqttClient producer = createPahoClient(PUBLISHER_CLIENT_ID);
      producer.connect();
      producer.publish(TOPIC, RandomUtil.randomBytes(), 1, false);
      producer.disconnect();
      producer.close();

      Wait.assertEquals(1L, () -> messageCount.get(), 500, 25);
      Wait.assertEquals(1L, () -> getSubscriptionQueue(TOPIC, SUBSCRIBER_CLIENT_ID).getMessagesAcknowledged(), 500, 25);
      Wait.assertEquals(0L, () -> getSubscriptionQueue(TOPIC, SUBSCRIBER_CLIENT_ID).getMessageCount(), 500, 25);

      assertEquals(0, getProtocolManager().getStateManager().getPacketIdCorrelationSize(SUBSCRIBER_CLIENT_ID));

      if (restart) {
         server.stop();
         waitForServerToStop(server);
         server.start();
         waitForServerToStart(server);
      } else {
         server.getRemotingService().clearInterceptors();
         server.getActiveMQServerControl().closeConnectionWithID(server.getActiveMQServerControl().listConnectionIDs()[0]);
      }

      reconnectSafely(subscriber);

      // Check for any unexpected re-delivery
      assertFalse(Wait.waitFor(() -> messageCount.get() > 1, 500, 25));

      assertEquals(0, getProtocolManager().getStateManager().getPacketIdCorrelationSize(SUBSCRIBER_CLIENT_ID));

      subscriber.disconnect();
      subscriber.close();
   }
}
