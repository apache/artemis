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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.netty.handler.codec.mqtt.MqttMessageType;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTInterceptor;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.ReusableLatch;
import org.apache.activemq.artemis.utils.Wait;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptionsBuilder;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for QoS 2 protocol resiliency with publisher reconnections or broker restarts.
 * <p>
 * QoS 2 Protocol Flow (publisher sending to broker):
 * <ol>
 * <li>Publisher sends PUBLISH (QoS=2)</li>
 * <li>Broker sends PUBREC</li>
 * <li>Publisher sends PUBREL</li>
 * <li>Broker sends PUBCOMP</li>
 * </ol>
 * These tests verify that the protocol maintains exactly-once delivery semantics when either clients
 * reconnect or the broker is restarted at each stage of the QoS 2 flow.
 */
public class QoS2PublisherResiliencyTest extends MQTT5TestSupport {

   protected static final long DEFAULT_TIMEOUT_SEC = 10;

   @Override
   public boolean isProtocolLoggingEnabled() {
      return true;
   }

   /**
    * Verifies that resending the same message via QoS2 doesn't result in duplicates when the broker is restarted after
    * it receives the PUBLISH but before it sends the PUBREC. In this circumstance the client will reconnect and send
    * the PUBLISH again with the same packet ID and the dup flag set to true.
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
      final String TOPIC = RandomUtil.randomUUIDString();
      final String CLIENTID = "publisher";
      final CountDownLatch publishLatch = new CountDownLatch(1);
      final CountDownLatch stopLatch = new CountDownLatch(1);
      final CountDownLatch pubCompLatch = new CountDownLatch(1);

      // Simulate a subscription queue
      server.createQueue(QueueConfiguration.of(TOPIC)
                            .setAddress(TOPIC)
                            .setRoutingType(RoutingType.MULTICAST)
                            .setDurable(true));

      // Set up interceptor block the initial PUBLISH
      MQTTInterceptor pubRecInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBLISH) {
            publishLatch.countDown();
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

      // Producer with persistent session
      MqttClient publisher = createPahoClient(CLIENTID);
      publisher.setCallback(new DefaultMqttCallback() {
         @Override
         public void disconnected(MqttDisconnectResponse disconnectResponse) {
            logger.info("{} disconnected", CLIENTID);
         }
      });
      MqttConnectionOptions producerOptions = new MqttConnectionOptionsBuilder()
         .cleanStart(false)
         .sessionExpiryInterval(300L)
         .build();
      publisher.connect(producerOptions);

      assertNull(getPubCache(CLIENTID));

      // Send message async as it will block waiting for a PUBREC that won't come
      CompletableFuture.runAsync(() -> {
         try {
            publisher.publish(TOPIC, RandomUtil.randomBytes(), EXACTLY_ONCE, false);
         } catch (MqttException e) {
            logger.info(e.getMessage());
         }
      });

      assertTrue(publishLatch.await(5, TimeUnit.SECONDS));
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

      MQTTInterceptor pubCompInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBCOMP) {
            pubCompLatch.countDown();
         }
         return true;
      };
      server.getRemotingService().addOutgoingInterceptor(pubCompInterceptor);

      assertNull(getPubCache(CLIENTID));

      // The client will automatically re-initiate the QoS2 protocol after reconnecting since it never got a PUBREC
      reconnectSafely(publisher);

      // Wait for the PUBCOMP to confirm QoS2 protocol is done
      assertTrue(pubCompLatch.await(5, TimeUnit.SECONDS));

      // Verify only one message is in the queue despite the QoS2 interruption
      Wait.assertEquals(1L, () -> server.locateQueue(TOPIC).getMessageCount(), 500, 25);

      publisher.disconnect();

      assertEquals(0, getPubCacheSize(CLIENTID));

      // connect again to clean the session which will completely remove the cache from memory and disk
      publisher.connect(new MqttConnectionOptionsBuilder().cleanStart(true).sessionExpiryInterval(0L).build());
      assertNull(getPubCache(CLIENTID));
      publisher.disconnect();
      publisher.close();
   }

   /**
    * Verifies that resending the same message via QoS2 doesn't result in duplicates when the broker is restarted after
    * it receives the PUBLISH and sends the PUBREC but before the client receives the PUBREC. In this circumstance the
    * client will reconnect and send the PUBLISH again with the same packet ID and the dup flag set to true.
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
      final String TOPIC = RandomUtil.randomUUIDString();
      final String CLIENTID = "publisher";
      final CountDownLatch pubRecLatch = new CountDownLatch(1);
      final CountDownLatch pubCompLatch = new CountDownLatch(1);

      // Simulate a subscription queue
      server.createQueue(QueueConfiguration.of(TOPIC)
                            .setAddress(TOPIC)
                            .setRoutingType(RoutingType.MULTICAST)
                            .setDurable(true));

      // Set up interceptor block the initial PUBREC
      MQTTInterceptor pubRecInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBREC) {
            pubRecLatch.countDown();
            logger.info("Blocking outgoing {}", packet.fixedHeader().messageType());
            return false;
         }

         logger.info("Allowing outgoing {}", packet.fixedHeader().messageType());
         return true;
      };
      server.getRemotingService().addOutgoingInterceptor(pubRecInterceptor);

      // Producer with persistent session
      MqttClient publisher = createPahoClient(CLIENTID);
      publisher.setCallback(new DefaultMqttCallback() {
         @Override
         public void disconnected(MqttDisconnectResponse disconnectResponse) {
            logger.info("{} disconnected", CLIENTID);
         }
      });
      MqttConnectionOptions producerOptions = new MqttConnectionOptionsBuilder()
         .cleanStart(false)
         .sessionExpiryInterval(300L)
         .build();
      publisher.connect(producerOptions);

      assertEquals(0, server.locateQueue(TOPIC).getMessageCount());
      assertNull(getPubCache(CLIENTID));

      // Send message async as it will block waiting for a PUBREC that won't come
      CompletableFuture.runAsync(() -> {
         try {
            publisher.publish(TOPIC, RandomUtil.randomBytes(), EXACTLY_ONCE, false);
         } catch (MqttException e) {
            logger.info(e.getMessage());
         }
      });
      assertTrue(pubRecLatch.await(5, TimeUnit.SECONDS));

      if (restart) {
         server.stop();
         waitForServerToStop(server);
         server.start();
         waitForServerToStart(server);
      } else {
         server.getRemotingService().clearInterceptors();
         server.getActiveMQServerControl().closeConnectionWithID(server.getActiveMQServerControl().listConnectionIDs()[0]);
      }

      MQTTInterceptor pubCompInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBCOMP) {
            pubCompLatch.countDown();
         }
         return true;
      };
      server.getRemotingService().addOutgoingInterceptor(pubCompInterceptor);

      assertEquals(1, getPubCacheSize(CLIENTID));

      // The client will automatically re-initiate the QoS2 protocol after reconnecting since it never got a PUBREC
      reconnectSafely(publisher);

      // Wait for the PUBCOMP to confirm QoS2 protocol is done
      assertTrue(pubCompLatch.await(5, TimeUnit.SECONDS));

      // Verify only one message is in the queue despite the QoS2 interruption
      Wait.assertEquals(1L, () -> server.locateQueue(TOPIC).getMessageCount(), 500, 25);

      publisher.disconnect();

      assertEquals(0, getPubCacheSize(CLIENTID));

      // connect again to clean the session which will completely remove the cache from memory and disk
      publisher.connect(new MqttConnectionOptionsBuilder().cleanStart(true).sessionExpiryInterval(0L).build());
      assertNull(getPubCache(CLIENTID));
      publisher.disconnect();
      publisher.close();
   }

   /**
    * Verifies that resending the same message via QoS2 doesn't result in duplicates when the broker is restarted after
    * it receives the PUBREL but before it sends the PUBCOMP.  In this circumstance the client will reconnect and send
    * the PUBREL again with the same packet ID.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testQoS2BrokerRestartBeforePubCompSent() throws Exception {
      testQoS2FailureBeforePubCompSent(true);
   }

   /**
    * Same test as {@link testQoS2BrokerRestartBeforePubCompSent} but disconnecting the client instead of restarting the
    * broker.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testQoS2ClientDisconnectBeforePubCompSent() throws Exception {
      testQoS2FailureBeforePubCompSent(false);
   }

   public void testQoS2FailureBeforePubCompSent(boolean restart) throws Exception {
      final String TOPIC = RandomUtil.randomUUIDString();
      final String CLIENTID = "publisher";
      final CountDownLatch pubRelLatch = new CountDownLatch(1);
      final CountDownLatch stopLatch = new CountDownLatch(1);
      final CountDownLatch pubCompLatch = new CountDownLatch(1);

      // Simulate a subscription queue
      server.createQueue(QueueConfiguration.of(TOPIC)
                            .setAddress(TOPIC)
                            .setRoutingType(RoutingType.MULTICAST)
                            .setDurable(true));

      // Set up interceptor block the initial PUBREL
      MQTTInterceptor pubRelInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBREL) {
            pubRelLatch.countDown();
            logger.info("Blocking incoming {}", packet.fixedHeader().messageType());
            try {
               assertTrue(stopLatch.await(5, TimeUnit.SECONDS));
            } catch (Exception e) {
               throw new RuntimeException(e);
            }
            return false;
         }
         logger.info("Allowing incoming {}", packet.fixedHeader().messageType());
         return true;
      };
      server.getRemotingService().addIncomingInterceptor(pubRelInterceptor);

      // Producer with persistent session
      MqttClient publisher = createPahoClient(CLIENTID);
      publisher.setCallback(new DefaultMqttCallback() {
         @Override
         public void disconnected(MqttDisconnectResponse disconnectResponse) {
            logger.info("{} disconnected", CLIENTID);
         }
      });
      MqttConnectionOptions producerOptions = new MqttConnectionOptionsBuilder()
         .cleanStart(false)
         .sessionExpiryInterval(300L)
         .build();
      publisher.connect(producerOptions);

      assertNull(getPubCache(CLIENTID));

      // Send message async as it will block waiting for a PUBCOMP that won't come
      CompletableFuture.runAsync(() -> {
         try {
            publisher.publish(TOPIC, RandomUtil.randomBytes(), EXACTLY_ONCE, false);
         } catch (MqttException e) {
            logger.info(e.getMessage());
         }
      });
      assertTrue(pubRelLatch.await(5, TimeUnit.SECONDS));

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

      MQTTInterceptor pubCompInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBCOMP) {
            pubCompLatch.countDown();
         }
         return true;
      };
      server.getRemotingService().addOutgoingInterceptor(pubCompInterceptor);

      assertEquals(1, getPubCacheSize(CLIENTID));

      // The client will automatically re-initiate the QoS2 protocol after reconnecting since it never got a PUBCOMP
      reconnectSafely(publisher);

      // Wait for the PUBCOMP to confirm QoS2 protocol is done
      assertTrue(pubCompLatch.await(5, TimeUnit.SECONDS));

      // Verify only one message is in the queue despite the QoS2 interruption
      Wait.assertEquals(1L, () -> server.locateQueue(TOPIC).getMessageCount(), 500, 25);

      publisher.disconnect();

      assertEquals(0, getPubCacheSize(CLIENTID));

      // connect again to clean the session which will completely remove the cache from memory and disk
      publisher.connect(new MqttConnectionOptionsBuilder().cleanStart(true).sessionExpiryInterval(0L).build());
      assertNull(getPubCache(CLIENTID));
      publisher.disconnect();
      publisher.close();
   }

   /**
    * Verifies that resending the same message via QoS2 doesn't result in duplicates when the broker is restarted after
    * it receives the PUBREL and sends the PUBCOMP but before the client receives the PUBCOMP.  In this circumstance the
    * client will reconnect and send the PUBREL again with the same packet ID.
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
      final String TOPIC = RandomUtil.randomUUIDString();
      final String CLIENTID = "publisher";
      final ReusableLatch pubCompLatch = new ReusableLatch(1);

      // Simulate a subscription queue
      server.createQueue(QueueConfiguration.of(TOPIC)
                            .setAddress(TOPIC)
                            .setRoutingType(RoutingType.MULTICAST)
                            .setDurable(true));

      // Set up interceptor block the initial PUBCOMP
      MQTTInterceptor initialPubCompInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBCOMP) {
            pubCompLatch.countDown();
            logger.info("Blocking outgoing {}", packet.fixedHeader().messageType());
            return false;
         }

         logger.info("Allowing outgoing {}", packet.fixedHeader().messageType());
         return true;
      };
      server.getRemotingService().addOutgoingInterceptor(initialPubCompInterceptor);

      // Producer with persistent session
      MqttClient publisher = createPahoClient(CLIENTID);
      publisher.setCallback(new DefaultMqttCallback() {
         @Override
         public void disconnected(MqttDisconnectResponse disconnectResponse) {
            logger.info("{} disconnected", CLIENTID);
         }
      });
      MqttConnectionOptions producerOptions = new MqttConnectionOptionsBuilder()
         .cleanStart(false)
         .sessionExpiryInterval(300L)
         .build();
      publisher.connect(producerOptions);

      assertEquals(0, server.locateQueue(TOPIC).getMessageCount());
      assertNull(getPubCache(CLIENTID));

      // Send message async as it will block waiting for a PUBREC that won't come
      CompletableFuture.runAsync(() -> {
         try {
            publisher.publish(TOPIC, RandomUtil.randomBytes(), EXACTLY_ONCE, false);
         } catch (MqttException e) {
            logger.info(e.getMessage());
         }
      });
      assertTrue(pubCompLatch.await(5, TimeUnit.SECONDS));

      if (restart) {
         server.stop();
         waitForServerToStop(server);
         server.start();
         waitForServerToStart(server);
      } else {
         server.getRemotingService().clearInterceptors();
         server.getActiveMQServerControl().closeConnectionWithID(server.getActiveMQServerControl().listConnectionIDs()[0]);
      }

      pubCompLatch.countUp();
      MQTTInterceptor pubCompInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBCOMP) {
            pubCompLatch.countDown();
         }
         return true;
      };
      server.getRemotingService().addOutgoingInterceptor(pubCompInterceptor);

      if (restart) {
         assertNull(getPubCache(CLIENTID));
      } else {
         assertEquals(0, getPubCacheSize(CLIENTID));
      }

      // The client will automatically re-initiate the QoS2 protocol after reconnecting since it never got a PUBCOMP
      reconnectSafely(publisher);

      // Wait for the PUBCOMP to confirm QoS2 protocol is done
      assertTrue(pubCompLatch.await(5, TimeUnit.SECONDS));

      // Verify only one message is in the queue despite the QoS2 interruption
      Wait.assertEquals(1L, () -> server.locateQueue(TOPIC).getMessageCount(), 500, 25);

      publisher.disconnect();

      if (restart) {
         assertNull(getPubCache(CLIENTID));
         publisher.close();
      } else {
         assertEquals(0, getPubCacheSize(CLIENTID));

         // connect again to clean the session which will completely remove the cache from memory and disk
         publisher.connect(new MqttConnectionOptionsBuilder().cleanStart(true).sessionExpiryInterval(0L).build());
         assertNull(getPubCache(CLIENTID));
         publisher.disconnect();
         publisher.close();
      }
   }
}
