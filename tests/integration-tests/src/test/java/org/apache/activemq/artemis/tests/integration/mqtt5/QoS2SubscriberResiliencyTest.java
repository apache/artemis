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
import org.apache.activemq.artemis.utils.ByteUtil;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for QoS 2 protocol resiliency with subscriber reconnections or broker restarts.
 * <p>
 * QoS 2 Protocol Flow (broker sending to subscriber):
 * <ol>
 * <li>Broker sends PUBLISH (QoS=2)</li>
 * <li>Subscriber sends PUBREC</li>
 * <li>Broker sends PUBREL</li>
 * <li>Subscriber sends PUBCOMP</li>
 * </ol>
 * These tests verify that the protocol maintains exactly-once delivery semantics when either clients
 * reconnect or the broker is restarted at each stage of the QoS 2 flow.
 */
public class QoS2SubscriberResiliencyTest extends MQTT5TestSupport {

   protected static final long DEFAULT_TIMEOUT_SEC = 10;

   @Override
   public boolean isProtocolLoggingEnabled() {
      return true;
   }

   /**
    * Verifies that the broker will re-use the same packet ID if it sends a PUBLISH but fails to receive the
    * corresponding PUBREC.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testQoS2BrokerRestartBeforePubRecSent() throws Exception {
      testQoS2FailureBeforePubRecSent(true);
   }

   /**
    * Same test as {@link testQoS2BrokerRestartBeforePubRecSent} but disconnecting the client instead of restarting the
    * broker.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testQoS2ClientDisconnectBeforePubRecSent() throws Exception {
      testQoS2FailureBeforePubRecSent(false);
   }

   public void testQoS2FailureBeforePubRecSent(boolean restart) throws Exception {
      final String TOPIC = "test/resiliency";
      final String SUBSCRIBER_CLIENT_ID = "subscriber";
      final String PUBLISHER_CLIENT_ID = "publisher";

      // Set up interceptor to block the *second* incoming PUBREC.
      // Allow 1 PUBREC so the packet ID goes up to 2 for testing.
      final CountDownLatch pubRecLatch = new CountDownLatch(1);
      AtomicInteger pubRecCount = new AtomicInteger(0);
      final CountDownLatch stopLatch = new CountDownLatch(1);
      MQTTInterceptor pubRecInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBREC && pubRecCount.incrementAndGet() > 1) {
            pubRecLatch.countDown();
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
      server.getRemotingService().addIncomingInterceptor(pubRecInterceptor);

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
      subscriber.subscribe(TOPIC, EXACTLY_ONCE);

      // Producer
      MqttClient producer = createPahoClient(PUBLISHER_CLIENT_ID);
      producer.connect();

      // Send 2 messages to ensure the packet ID is preserved by the broker.
      // If we just send 1 message it won't be clear if the broker just started generating packet IDs from scratch.
      producer.publish(TOPIC, RandomUtil.randomBytes(), EXACTLY_ONCE, false);
      producer.publish(TOPIC, RandomUtil.randomBytes(), EXACTLY_ONCE, false);

      Wait.assertEquals(0, () -> getPubCacheSize(PUBLISHER_CLIENT_ID));

      producer.disconnect();
      producer.close();

      assertNull(getPubCache(PUBLISHER_CLIENT_ID));

      assertTrue(pubRecLatch.await(5, TimeUnit.SECONDS));
      stopLatch.countDown();

      if (restart) {
         server.stop();
         waitForServerToStop(server);
         server.start();
         waitForServerToStart(server);
      } else {
         server.getRemotingService().clearInterceptors();
         Wait.assertEquals(0, () -> server.getRemotingService().getConnections().size(), 1000, 10);
      }

      assertTrue(getProtocolManager().getStateManager().packetIdCorrelationExists(SUBSCRIBER_CLIENT_ID, 2));

      final CountDownLatch pubCompLatch = new CountDownLatch(1);
      MQTTInterceptor pubCompInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBCOMP) {
            pubCompLatch.countDown();
         }
         return true;
      };
      server.getRemotingService().addIncomingInterceptor(pubCompInterceptor);

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
      assertTrue(pubCompLatch.await(5, TimeUnit.SECONDS));

      Wait.assertEquals(0L, () -> getSubscriptionQueue(TOPIC, SUBSCRIBER_CLIENT_ID).getMessageCount(), 500, 25);

      assertEquals(0, getProtocolManager().getStateManager().getPacketIdCorrelationSize(SUBSCRIBER_CLIENT_ID));
      Wait.assertEquals(0, () -> getPubRecCacheSize(SUBSCRIBER_CLIENT_ID));

      subscriber.disconnect();

      // connect again to clean the session which will completely remove the cache from memory and disk
      subscriber.connect(new MqttConnectionOptionsBuilder().cleanStart(true).sessionExpiryInterval(0L).build());
      assertNull(getPubRecCache(SUBSCRIBER_CLIENT_ID));
      subscriber.disconnect();
      subscriber.close();
   }

   /**
    * Verifies that the broker correctly completes the QoS 2 flow if it receives the PUBREC but the corresponding
    * PUBREL is not sent to the consumer before the broker restarts.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testQoS2BrokerRestartAfterPubRecSent() throws Exception {
      testQoS2FailureAfterPubRecSent(true);
   }

   /**
    * Same test as {@link testQoS2BrokerRestartAfterPubRecSent} but disconnecting the client instead of restarting the
    * broker.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testQoS2ClientDisconnectAfterPubRecSent() throws Exception {
      testQoS2FailureAfterPubRecSent(false);
   }

   public void testQoS2FailureAfterPubRecSent(boolean restart) throws Exception {
      final String TOPIC = "test/resiliency";
      final String SUBSCRIBER_CLIENT_ID = "subscriber";
      final String PUBLISHER_CLIENT_ID = "publisher";
      final CountDownLatch pubRelLatch = new CountDownLatch(1);
      final CountDownLatch pubCompLatch = new CountDownLatch(1);
      AtomicInteger messageCount = new AtomicInteger(0);

      // Block the outgoing PUBREL so the broker has processed PUBREC but the consumer never receives PUBREL
      MQTTInterceptor pubRelInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBREL) {
            pubRelLatch.countDown();
            logger.info("Blocking outgoing {}", packet.fixedHeader().messageType());
            return false;
         }
         logger.info("Allowing outgoing {}", packet.fixedHeader().messageType());
         return true;
      };
      server.getRemotingService().addOutgoingInterceptor(pubRelInterceptor);

      // Consumer with persistent session
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
      subscriber.subscribe(TOPIC, EXACTLY_ONCE);

      // Producer
      MqttClient producer = createPahoClient(PUBLISHER_CLIENT_ID);
      producer.connect();

      producer.publish(TOPIC, RandomUtil.randomBytes(), EXACTLY_ONCE, false);

      Wait.assertEquals(0, () -> getPubCacheSize(PUBLISHER_CLIENT_ID));

      producer.disconnect();
      producer.close();

      assertNull(getPubCache(PUBLISHER_CLIENT_ID));

      assertTrue(pubRelLatch.await(5, TimeUnit.SECONDS));

      if (restart) {
         server.stop();
         waitForServerToStop(server);
         server.start();
         waitForServerToStart(server);
      } else {
         server.getRemotingService().clearInterceptors();
         server.getActiveMQServerControl().closeConnectionWithID(server.getActiveMQServerControl().listConnectionIDs()[0]);
      }

      assertEquals(0, getProtocolManager().getStateManager().getPacketIdCorrelationSize(SUBSCRIBER_CLIENT_ID));
      assertTrue(getPubRecCache(SUBSCRIBER_CLIENT_ID).contains(ByteUtil.intToBytes(1)));

      MQTTInterceptor pubCompInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBCOMP) {
            pubCompLatch.countDown();
         }
         return true;
      };
      server.getRemotingService().addIncomingInterceptor(pubCompInterceptor);

      reconnectSafely(subscriber);

      assertTrue(pubCompLatch.await(5, TimeUnit.SECONDS));

      Wait.assertEquals(0L, () -> getSubscriptionQueue(TOPIC, SUBSCRIBER_CLIENT_ID).getMessageCount(), 500, 25);

      assertEquals(0, getProtocolManager().getStateManager().getPacketIdCorrelationSize(SUBSCRIBER_CLIENT_ID));
      Wait.assertEquals(0, () -> getPubRecCacheSize(SUBSCRIBER_CLIENT_ID));
      assertEquals(1, messageCount.get());

      subscriber.disconnect();

      // connect again to clean the session which will completely remove the cache from memory and disk
      subscriber.connect(new MqttConnectionOptionsBuilder().cleanStart(true).sessionExpiryInterval(0L).build());
      assertNull(getPubRecCache(SUBSCRIBER_CLIENT_ID));
      subscriber.disconnect();
      subscriber.close();
   }

   /**
    * Verifies that the broker correctly completes the QoS 2 flow if it sends the PUBREL but fails to receive the
    * corresponding PUBCOMP before the broker restarts.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testQoS2BrokerRestartAfterPubRelSent() throws Exception {
      testQoS2FailureAfterPubRelSent(true);
   }

   /**
    * Same test as {@link testQoS2BrokerRestartAfterPubRelSent} but disconnecting the client instead of restarting the
    * broker.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testQoS2ClientDisconnectAfterPubRelSent() throws Exception {
      testQoS2FailureAfterPubRelSent(false);
   }

   public void testQoS2FailureAfterPubRelSent(boolean restart) throws Exception {
      final String TOPIC = "test/resiliency";
      final String SUBSCRIBER_CLIENT_ID = "subscriber";
      final String PUBLISHER_CLIENT_ID = "publisher";
      final CountDownLatch pubCompBlockedLatch = new CountDownLatch(1);
      final CountDownLatch stopLatch = new CountDownLatch(1);
      final CountDownLatch pubCompLatch = new CountDownLatch(1);
      AtomicInteger messageCount = new AtomicInteger(0);

      // Block the incoming PUBCOMP so the broker has sent PUBREL but never processes the PUBCOMP
      MQTTInterceptor pubCompBlocker = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBCOMP) {
            pubCompBlockedLatch.countDown();
            try {
               stopLatch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
               throw new RuntimeException(e);
            }
            return false;
         }
         return true;
      };
      server.getRemotingService().addIncomingInterceptor(pubCompBlocker);

      // Consumer with persistent session
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
      subscriber.subscribe(TOPIC, EXACTLY_ONCE);

      // Producer
      MqttClient producer = createPahoClient(PUBLISHER_CLIENT_ID);
      producer.connect();

      producer.publish(TOPIC, RandomUtil.randomBytes(), EXACTLY_ONCE, false);

      producer.disconnect();
      producer.close();

      assertTrue(pubCompBlockedLatch.await(5, TimeUnit.SECONDS));
      stopLatch.countDown();

      if (restart) {
         server.stop();
         waitForServerToStop(server);
         server.start();
         waitForServerToStart(server);
      } else {
         server.getRemotingService().clearInterceptors();
         Wait.assertEquals(0, () -> server.getRemotingService().getConnections().size(), 1000, 10);
      }

      assertEquals(0, getProtocolManager().getStateManager().getPacketIdCorrelationSize(SUBSCRIBER_CLIENT_ID));
      assertTrue(getPubRecCache(SUBSCRIBER_CLIENT_ID).contains(ByteUtil.intToBytes(1)));

      MQTTInterceptor pubCompInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBCOMP) {
            pubCompLatch.countDown();
         }
         return true;
      };
      server.getRemotingService().addIncomingInterceptor(pubCompInterceptor);

      reconnectSafely(subscriber);

      assertTrue(pubCompLatch.await(5, TimeUnit.SECONDS));

      Wait.assertEquals(0L, () -> getSubscriptionQueue(TOPIC, SUBSCRIBER_CLIENT_ID).getMessageCount(), 500, 25);

      assertEquals(0, getProtocolManager().getStateManager().getPacketIdCorrelationSize(SUBSCRIBER_CLIENT_ID));
      Wait.assertEquals(0, () -> getPubRecCacheSize(SUBSCRIBER_CLIENT_ID));
      assertEquals(1, messageCount.get());

      subscriber.disconnect();

      // connect again to clean the session which will completely remove the cache from memory and disk
      subscriber.connect(new MqttConnectionOptionsBuilder().cleanStart(true).sessionExpiryInterval(0L).build());
      assertNull(getPubRecCache(SUBSCRIBER_CLIENT_ID));
      subscriber.disconnect();
      subscriber.close();
   }

   /**
    * Verifies that after a complete QoS 2 protocol exchange (PUBLISH, PUBREC, PUBREL, PUBCOMP), a broker restart does
    * not cause re-delivery.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testQoS2BrokerRestartAfterPubCompSent() throws Exception {
      testQoS2FailureAfterPubCompSent(true);
   }

   /**
    * Same test as {@link testQoS2BrokerRestartAfterPubCompSent} but disconnecting the client instead of restarting the
    * broker.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testQoS2ClientDisconnectAfterPubCompSent() throws Exception {
      testQoS2FailureAfterPubCompSent(false);
   }

   public void testQoS2FailureAfterPubCompSent(boolean restart) throws Exception {
      final String TOPIC = "test/resiliency";
      final String SUBSCRIBER_CLIENT_ID = "subscriber";
      final String PUBLISHER_CLIENT_ID = "publisher";
      final CountDownLatch pubCompLatch = new CountDownLatch(1);
      AtomicInteger messageCount = new AtomicInteger(0);

      // Consumer with persistent session
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
      subscriber.subscribe(TOPIC, EXACTLY_ONCE);

      // Track PUBCOMPs to know when both QoS 2 flows are complete
      MQTTInterceptor pubCompInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBCOMP) {
            pubCompLatch.countDown();
         }
         return true;
      };
      server.getRemotingService().addIncomingInterceptor(pubCompInterceptor);

      // Producer
      MqttClient producer = createPahoClient(PUBLISHER_CLIENT_ID);
      producer.connect();

      producer.publish(TOPIC, RandomUtil.randomBytes(), EXACTLY_ONCE, false);

      producer.disconnect();
      producer.close();

      // Wait for both QoS 2 flows to complete
      assertTrue(pubCompLatch.await(5, TimeUnit.SECONDS));
      Wait.assertEquals(0L, () -> getSubscriptionQueue(TOPIC, SUBSCRIBER_CLIENT_ID).getMessageCount(), 500, 25);

      assertEquals(0, getProtocolManager().getStateManager().getPacketIdCorrelationSize(SUBSCRIBER_CLIENT_ID));
      assertEquals(0, getPubRecCacheSize(SUBSCRIBER_CLIENT_ID));

      if (restart) {
         server.stop();
         waitForServerToStop(server);
         server.start();
         waitForServerToStart(server);
      } else {
         server.getRemotingService().clearInterceptors();
         server.getActiveMQServerControl().closeConnectionWithID(
            server.getActiveMQServerControl().listConnectionIDs()[0]);
      }

      int countBeforeReconnect = messageCount.get();
      reconnectSafely(subscriber);

      // Verify no unexpected re-delivery
      assertFalse(Wait.waitFor(() -> messageCount.get() > countBeforeReconnect, 500, 25), "Unexpected message delivered after restart");
      Wait.assertEquals(0L, () -> getSubscriptionQueue(TOPIC, SUBSCRIBER_CLIENT_ID).getMessageCount(), 500, 25);

      assertEquals(0, getProtocolManager().getStateManager().getPacketIdCorrelationSize(SUBSCRIBER_CLIENT_ID));
      if (restart) {
         assertNull(getPubRecCache(SUBSCRIBER_CLIENT_ID));
      } else {
         assertEquals(0, getPubRecCacheSize(SUBSCRIBER_CLIENT_ID));
      }

      subscriber.disconnect();

      // connect again to clean the session which will completely remove the cache from memory and disk
      subscriber.connect(new MqttConnectionOptionsBuilder().cleanStart(true).sessionExpiryInterval(0L).build());
      assertNull(getPubRecCache(SUBSCRIBER_CLIENT_ID));
      subscriber.disconnect();
      subscriber.close();
   }

   /**
    * Verifies that incomplete QoS 2 flow state (packet ID correlations) is removed when a client with session expiry
    * interval 0 is disconnected before PUBREC is processed.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testQoS2PacketIdCorrelationsRemovedOnSessionExpiryZeroDisconnect() throws Exception {
      final String TOPIC = "test/resiliency";
      final String SUBSCRIBER_CLIENT_ID = "subscriber";
      final String PUBLISHER_CLIENT_ID = "publisher";

      // Block the incoming PUBREC so the packet ID correlation is never removed by the normal QoS 2 flow.
      // Returning false from an incoming interceptor disconnects the client.
      final CountDownLatch pubRecLatch = new CountDownLatch(1);
      MQTTInterceptor pubRecInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBREC) {
            pubRecLatch.countDown();
            return false;
         }
         return true;
      };
      server.getRemotingService().addIncomingInterceptor(pubRecInterceptor);

      MqttClient subscriber = createPahoClient(SUBSCRIBER_CLIENT_ID);
      subscriber.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
         }
      });
      subscriber.connect(new MqttConnectionOptionsBuilder().cleanStart(true).sessionExpiryInterval(0L).build());
      subscriber.subscribe(TOPIC, EXACTLY_ONCE);

      MqttClient producer = createPahoClient(PUBLISHER_CLIENT_ID);
      producer.connect();
      producer.publish(TOPIC, RandomUtil.randomBytes(), EXACTLY_ONCE, false);
      Wait.assertEquals(0, () -> getPubCacheSize(PUBLISHER_CLIENT_ID));
      producer.disconnect();
      producer.close();

      // Wait for PUBREC to be intercepted (which disconnects the subscriber)
      assertTrue(pubRecLatch.await(5, TimeUnit.SECONDS));
      server.getRemotingService().clearInterceptors();
      Wait.assertEquals(0, () -> server.getRemotingService().getConnections().size(), 1000, 10);

      // Since session expiry is 0, the session was cleaned up on disconnect.
      // The packet ID correlations should have been removed.
      assertEquals(0, getProtocolManager().getStateManager().getPacketIdCorrelationSize(SUBSCRIBER_CLIENT_ID));

      subscriber.close();
   }

   /**
    * Verifies that incomplete QoS 2 flow state (packet ID correlations) is removed when a session expires via the
    * session expiry scanner.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testQoS2PacketIdCorrelationsRemovedOnSessionExpiry() throws Exception {
      final String TOPIC = "test/resiliency";
      final String SUBSCRIBER_CLIENT_ID = "subscriber";
      final String PUBLISHER_CLIENT_ID = "publisher";

      final CountDownLatch pubRecLatch = new CountDownLatch(1);
      MQTTInterceptor pubRecInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBREC) {
            pubRecLatch.countDown();
            return false;
         }
         return true;
      };
      server.getRemotingService().addIncomingInterceptor(pubRecInterceptor);

      MqttClient subscriber = createPahoClient(SUBSCRIBER_CLIENT_ID);
      subscriber.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
         }
      });
      subscriber.connect(new MqttConnectionOptionsBuilder().cleanStart(true).sessionExpiryInterval(1L).build());
      subscriber.subscribe(TOPIC, EXACTLY_ONCE);

      MqttClient producer = createPahoClient(PUBLISHER_CLIENT_ID);
      producer.connect();
      producer.publish(TOPIC, RandomUtil.randomBytes(), EXACTLY_ONCE, false);
      Wait.assertEquals(0, () -> getPubCacheSize(PUBLISHER_CLIENT_ID));
      producer.disconnect();
      producer.close();

      assertTrue(pubRecLatch.await(5, TimeUnit.SECONDS));
      server.getRemotingService().clearInterceptors();
      Wait.assertEquals(0, () -> server.getRemotingService().getConnections().size(), 1000, 10);

      // Packet ID correlations should exist since PUBREC was never processed
      assertTrue(getProtocolManager().getStateManager().getPacketIdCorrelationSize(SUBSCRIBER_CLIENT_ID) > 0);

      // Wait for the session to expire then trigger the scanner
      Thread.sleep(1500);
      scanSessions();

      // The session expiry scanner should have cleaned up the packet ID correlations
      assertEquals(0, getProtocolManager().getStateManager().getPacketIdCorrelationSize(SUBSCRIBER_CLIENT_ID));
      assertNull(getSessionStates().get(SUBSCRIBER_CLIENT_ID));

      subscriber.close();
   }

   /**
    * Verifies that incomplete QoS 2 flow state (packet ID correlations) is removed when a client reconnects with
    * clean start = true.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testQoS2PacketIdCorrelationsRemovedOnCleanStartReconnect() throws Exception {
      final String TOPIC = "test/resiliency";
      final String SUBSCRIBER_CLIENT_ID = "subscriber";
      final String PUBLISHER_CLIENT_ID = "publisher";

      final CountDownLatch pubRecLatch = new CountDownLatch(1);
      MQTTInterceptor pubRecInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBREC) {
            pubRecLatch.countDown();
            return false;
         }
         return true;
      };
      server.getRemotingService().addIncomingInterceptor(pubRecInterceptor);

      MqttClient subscriber = createPahoClient(SUBSCRIBER_CLIENT_ID);
      subscriber.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
         }
      });
      subscriber.connect(new MqttConnectionOptionsBuilder().cleanStart(true).sessionExpiryInterval(300L).build());
      subscriber.subscribe(TOPIC, EXACTLY_ONCE);

      MqttClient producer = createPahoClient(PUBLISHER_CLIENT_ID);
      producer.connect();
      producer.publish(TOPIC, RandomUtil.randomBytes(), EXACTLY_ONCE, false);
      Wait.assertEquals(0, () -> getPubCacheSize(PUBLISHER_CLIENT_ID));
      producer.disconnect();
      producer.close();

      assertTrue(pubRecLatch.await(5, TimeUnit.SECONDS));
      server.getRemotingService().clearInterceptors();
      Wait.assertEquals(0, () -> server.getRemotingService().getConnections().size(), 1000, 10);

      // Packet ID correlations should exist since PUBREC was never processed
      assertTrue(getProtocolManager().getStateManager().getPacketIdCorrelationSize(SUBSCRIBER_CLIENT_ID) > 0);

      // Reconnect with clean start to discard previous session state
      subscriber.connect(new MqttConnectionOptionsBuilder().cleanStart(true).sessionExpiryInterval(0L).build());

      // The clean start should have cleared the packet ID correlations
      assertEquals(0, getProtocolManager().getStateManager().getPacketIdCorrelationSize(SUBSCRIBER_CLIENT_ID));

      subscriber.disconnect();
      subscriber.close();
   }
}
