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

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.impl.RoutingContextImpl;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPMirrorBrokerConnectionElement;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTUtil;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.Wait;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MQTT5MirrorTest extends ActiveMQTestBase {

   private static final int BROKER1_PORT = 1883;
   private static final int BROKER2_PORT = 1884;

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      super.setUp();
   }

   private ActiveMQServer createMirroredServer(int serverID, int port, int targetPort, String connectionName) throws Exception {
      ActiveMQServer server = createServer(true, createDefaultConfig(serverID, true));
      server.getConfiguration().getAcceptorConfigurations().clear();
      server.getConfiguration().addAcceptorConfiguration("server", "tcp://localhost:" + port);
      server.getConfiguration().setSecurityEnabled(false);
      server.getConfiguration().setMqttSessionScanInterval(200);

      AddressSettings addressSettings = new AddressSettings();
      addressSettings.setAutoCreateQueues(true);
      addressSettings.setAutoCreateAddresses(true);
      server.getConfiguration().getAddressSettings().put("#", addressSettings);

      AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration(connectionName, "tcp://localhost:" + targetPort)
         .setReconnectAttempts(-1)
         .setRetryInterval(100);
      amqpConnection.addElement(new AMQPMirrorBrokerConnectionElement().setDurable(true));
      server.getConfiguration().addAMQPConnection(amqpConnection);

      return server;
   }

   private ActiveMQServer createMessageOnlyMirroredServer(int serverID, int port, int targetPort, String connectionName) throws Exception {
      ActiveMQServer server = createServer(true, createDefaultConfig(serverID, true));
      server.getConfiguration().getAcceptorConfigurations().clear();
      server.getConfiguration().addAcceptorConfiguration("server", "tcp://localhost:" + port);
      server.getConfiguration().setSecurityEnabled(false);
      server.getConfiguration().setMqttSessionScanInterval(200);

      AddressSettings addressSettings = new AddressSettings();
      addressSettings.setAutoCreateQueues(true);
      addressSettings.setAutoCreateAddresses(true);
      server.getConfiguration().getAddressSettings().put("#", addressSettings);

      AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration(connectionName, "tcp://localhost:" + targetPort)
         .setReconnectAttempts(-1)
         .setRetryInterval(100);
      amqpConnection.addElement(new AMQPMirrorBrokerConnectionElement()
         .setDurable(true)
         .setQueueCreation(false)
         .setQueueRemoval(false)
         .setMessageAcknowledgements(false));
      server.getConfiguration().addAMQPConnection(amqpConnection);

      return server;
   }

   private MqttClient createPahoClient(String clientId, int port) throws MqttException {
      return new MqttClient("tcp://localhost:" + port, clientId, new MemoryPersistence());
   }

   @Test
   @Timeout(60)
   public void testRetainedMessageMirrored() throws Exception {
      ActiveMQServer server1 = createMirroredServer(0, BROKER1_PORT, BROKER2_PORT, "mirrorToServer2");
      ActiveMQServer server2 = createMirroredServer(1, BROKER2_PORT, BROKER1_PORT, "mirrorToServer1");

      server1.start();
      server1.waitForActivation(10, TimeUnit.SECONDS);
      server2.start();
      server2.waitForActivation(10, TimeUnit.SECONDS);

      Wait.assertTrue(() -> server1.locateQueue("$ACTIVEMQ_ARTEMIS_MIRROR_mirrorToServer2") != null);
      Wait.assertTrue(() -> server2.locateQueue("$ACTIVEMQ_ARTEMIS_MIRROR_mirrorToServer1") != null);

      final String topic = "test/retain/mirror";
      final String payload = "retained-message-payload";

      // publish retained message to broker 1
      MqttClient producer = createPahoClient("producer", BROKER1_PORT);
      producer.connect();
      producer.publish(topic, payload.getBytes(StandardCharsets.UTF_8), 1, true);
      producer.disconnect();
      producer.close();

      // verify the retain queue exists on server1
      final String retainQueueName = MQTTUtil.getCoreRetainAddressFromMqttTopic(topic, server1.getConfiguration().getWildcardConfiguration());
      Wait.assertTrue(() -> {
         org.apache.activemq.artemis.core.server.Queue queue = server1.locateQueue(retainQueueName);
         return queue != null && queue.getMessageCount() == 1;
      }, 5000, 100);

      // subscribe on broker 1 - should get the retained message
      CountDownLatch latch1 = new CountDownLatch(1);
      AtomicReference<String> received1 = new AtomicReference<>();
      MqttClient sub1 = createPahoClient("subscriber1", BROKER1_PORT);
      sub1.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String t, MqttMessage m) {
            received1.set(new String(m.getPayload(), StandardCharsets.UTF_8));
            latch1.countDown();
         }
      });
      sub1.connect();
      sub1.subscribe(topic, 1);
      assertTrue(latch1.await(5, TimeUnit.SECONDS), "Subscriber on broker 1 should receive the retained message");
      assertEquals(payload, received1.get());
      sub1.disconnect();
      sub1.close();

      // wait for the retained message to appear on broker 2 via mirroring
      Wait.assertTrue(() -> {
         org.apache.activemq.artemis.core.server.Queue queue = server2.locateQueue(retainQueueName);
         return queue != null && queue.getMessageCount() == 1;
      }, 10000, 100);

      // subscribe on broker 2 - should also get the retained message
      CountDownLatch latch2 = new CountDownLatch(1);
      AtomicReference<String> received2 = new AtomicReference<>();
      MqttClient sub2 = createPahoClient("subscriber2", BROKER2_PORT);
      sub2.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String t, MqttMessage m) {
            received2.set(new String(m.getPayload(), StandardCharsets.UTF_8));
            latch2.countDown();
         }
      });
      sub2.connect();
      sub2.subscribe(topic, 1);
      assertTrue(latch2.await(5, TimeUnit.SECONDS), "Subscriber on broker 2 should receive the mirrored retained message");
      assertEquals(payload, received2.get());
      sub2.disconnect();
      sub2.close();

      // publish a second retained message - should replace the first on both brokers
      final String payload2 = "retained-message-payload-2";
      MqttClient producer2 = createPahoClient("producer2", BROKER1_PORT);
      producer2.connect();
      producer2.publish(topic, payload2.getBytes(StandardCharsets.UTF_8), 1, true);
      producer2.disconnect();
      producer2.close();

      // verify server1 retain queue replaced: still exactly 1 message with the new payload
      Wait.assertTrue(() -> {
         org.apache.activemq.artemis.core.server.Queue queue = server1.locateQueue(retainQueueName);
         return queue != null && queue.getMessageCount() == 1 && queue.getMessagesAdded() == 2;
      }, 5000, 100);

      // verify server2 retain queue replaced via mirroring: exactly 1 message
      Wait.assertTrue(() -> {
         org.apache.activemq.artemis.core.server.Queue queue = server2.locateQueue(retainQueueName);
         return queue != null && queue.getMessageCount() == 1;
      }, 10000, 100);

      // subscribe on broker 1 - should get the second retained message
      CountDownLatch latch3 = new CountDownLatch(1);
      AtomicReference<String> received3 = new AtomicReference<>();
      MqttClient sub3 = createPahoClient("subscriber3", BROKER1_PORT);
      sub3.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String t, MqttMessage m) {
            received3.set(new String(m.getPayload(), StandardCharsets.UTF_8));
            latch3.countDown();
         }
      });
      sub3.connect();
      sub3.subscribe(topic, 1);
      assertTrue(latch3.await(5, TimeUnit.SECONDS), "Subscriber on broker 1 should receive the second retained message");
      assertEquals(payload2, received3.get());
      sub3.disconnect();
      sub3.close();

      // subscribe on broker 2 - should get the second retained message
      CountDownLatch latch4 = new CountDownLatch(1);
      AtomicReference<String> received4 = new AtomicReference<>();
      MqttClient sub4 = createPahoClient("subscriber4", BROKER2_PORT);
      sub4.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String t, MqttMessage m) {
            received4.set(new String(m.getPayload(), StandardCharsets.UTF_8));
            latch4.countDown();
         }
      });
      sub4.connect();
      sub4.subscribe(topic, 1);
      assertTrue(latch4.await(5, TimeUnit.SECONDS), "Subscriber on broker 2 should receive the second retained message");
      assertEquals(payload2, received4.get());
      sub4.disconnect();
      sub4.close();
   }

   @Test
   @Timeout(60)
   public void testClearRetainedMessageMirrored() throws Exception {
      ActiveMQServer server1 = createMirroredServer(0, BROKER1_PORT, BROKER2_PORT, "mirrorToServer2");
      ActiveMQServer server2 = createMirroredServer(1, BROKER2_PORT, BROKER1_PORT, "mirrorToServer1");

      server1.start();
      server1.waitForActivation(10, TimeUnit.SECONDS);
      server2.start();
      server2.waitForActivation(10, TimeUnit.SECONDS);

      Wait.assertTrue(() -> server1.locateQueue("$ACTIVEMQ_ARTEMIS_MIRROR_mirrorToServer2") != null);
      Wait.assertTrue(() -> server2.locateQueue("$ACTIVEMQ_ARTEMIS_MIRROR_mirrorToServer1") != null);

      final String topic = "test/retain/clear";
      final String payload = "retained-to-clear";
      final String retainQueueName = MQTTUtil.getCoreRetainAddressFromMqttTopic(topic, server1.getConfiguration().getWildcardConfiguration());

      // publish a retained message on broker 1
      MqttClient producer = createPahoClient("producer", BROKER1_PORT);
      producer.connect();
      producer.publish(topic, payload.getBytes(StandardCharsets.UTF_8), 1, true);

      // verify retain queue populated on both brokers
      Wait.assertTrue(() -> {
         org.apache.activemq.artemis.core.server.Queue queue = server1.locateQueue(retainQueueName);
         return queue != null && queue.getMessageCount() == 1;
      }, 5000, 100);
      Wait.assertTrue(() -> {
         org.apache.activemq.artemis.core.server.Queue queue = server2.locateQueue(retainQueueName);
         return queue != null && queue.getMessageCount() == 1;
      }, 10000, 100);

      // publish a zero-length retained message to clear the retain (MQTT spec §3.3.1.3)
      producer.publish(topic, new byte[0], 1, true);
      producer.disconnect();
      producer.close();

      // retain queue on server1 should be empty (cleared locally by MQTTRetainMessageManager)
      Wait.assertTrue(() -> {
         org.apache.activemq.artemis.core.server.Queue queue = server1.locateQueue(retainQueueName);
         return queue != null && queue.getMessageCount() == 0;
      }, 5000, 100);

      // retain queue on server2 should also be empty (cleared via mirrored zero-length message)
      Wait.assertTrue(() -> {
         org.apache.activemq.artemis.core.server.Queue queue = server2.locateQueue(retainQueueName);
         return queue != null && queue.getMessageCount() == 0;
      }, 10000, 100);

      // a new subscriber on broker 2 should NOT receive a retained message
      CountDownLatch latch = new CountDownLatch(1);
      AtomicReference<String> received = new AtomicReference<>();
      MqttClient sub = createPahoClient("subscriber", BROKER2_PORT);
      sub.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String t, MqttMessage m) {
            received.set(new String(m.getPayload(), StandardCharsets.UTF_8));
            latch.countDown();
         }
      });
      sub.connect();
      sub.subscribe(topic, 1);

      // publish a regular (non-retained) message so we know the subscription is active
      MqttClient probe = createPahoClient("probe", BROKER2_PORT);
      probe.connect();
      probe.publish(topic, "probe".getBytes(StandardCharsets.UTF_8), 1, false);
      probe.disconnect();
      probe.close();

      assertTrue(latch.await(5, TimeUnit.SECONDS), "Subscriber should receive the probe message");
      assertEquals("probe", received.get(), "Only the probe message should arrive — no retained message");

      sub.disconnect();
      sub.close();
   }

   @Test
   @Timeout(60)
   public void testRegularMessageMirrored() throws Exception {
      ActiveMQServer server1 = createMirroredServer(0, BROKER1_PORT, BROKER2_PORT, "mirrorToServer2");
      ActiveMQServer server2 = createMirroredServer(1, BROKER2_PORT, BROKER1_PORT, "mirrorToServer1");

      server1.start();
      server1.waitForActivation(10, TimeUnit.SECONDS);
      server2.start();
      server2.waitForActivation(10, TimeUnit.SECONDS);

      Wait.assertTrue(() -> server1.locateQueue("$ACTIVEMQ_ARTEMIS_MIRROR_mirrorToServer2") != null);
      Wait.assertTrue(() -> server2.locateQueue("$ACTIVEMQ_ARTEMIS_MIRROR_mirrorToServer1") != null);

      final String topic = "test/mirror/regular";
      final String payload = "regular-message-payload";

      // subscribe on broker 2 first so the queue exists when the mirrored message arrives
      CountDownLatch latch = new CountDownLatch(1);
      AtomicReference<String> received = new AtomicReference<>();
      MqttClient sub = createPahoClient("subscriber", BROKER2_PORT);
      sub.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String t, MqttMessage m) {
            received.set(new String(m.getPayload(), StandardCharsets.UTF_8));
            latch.countDown();
         }
      });
      sub.connect();
      sub.subscribe(topic, 1);

      // publish a regular (non-retained) message on broker 1
      MqttClient producer = createPahoClient("producer", BROKER1_PORT);
      producer.connect();
      producer.publish(topic, payload.getBytes(StandardCharsets.UTF_8), 1, false);
      producer.disconnect();
      producer.close();

      // subscriber on broker 2 should receive the mirrored message
      assertTrue(latch.await(5, TimeUnit.SECONDS), "Subscriber on broker 2 should receive the mirrored message");
      assertEquals(payload, received.get());
      sub.disconnect();
      sub.close();
   }

   @Test
   @Timeout(60)
   public void testRetainedAndRegularInterleavedMirrored() throws Exception {
      ActiveMQServer server1 = createMirroredServer(0, BROKER1_PORT, BROKER2_PORT, "mirrorToServer2");
      ActiveMQServer server2 = createMirroredServer(1, BROKER2_PORT, BROKER1_PORT, "mirrorToServer1");

      server1.start();
      server1.waitForActivation(10, TimeUnit.SECONDS);
      server2.start();
      server2.waitForActivation(10, TimeUnit.SECONDS);

      Wait.assertTrue(() -> server1.locateQueue("$ACTIVEMQ_ARTEMIS_MIRROR_mirrorToServer2") != null);
      Wait.assertTrue(() -> server2.locateQueue("$ACTIVEMQ_ARTEMIS_MIRROR_mirrorToServer1") != null);

      final String topic = "test/interleave/mirror";
      final String retainQueueName = MQTTUtil.getCoreRetainAddressFromMqttTopic(topic, server1.getConfiguration().getWildcardConfiguration());

      // subscribe on broker 2 to receive live messages
      CountDownLatch latch = new CountDownLatch(4);
      CopyOnWriteArrayList<String> receivedMessages = new CopyOnWriteArrayList<>();
      MqttClient sub = createPahoClient("subscriber", BROKER2_PORT);
      sub.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String t, MqttMessage m) {
            receivedMessages.add(new String(m.getPayload(), StandardCharsets.UTF_8));
            latch.countDown();
         }
      });
      sub.connect();
      sub.subscribe(topic, 1);

      // interleave retained and regular publishes on broker 1
      MqttClient producer = createPahoClient("producer", BROKER1_PORT);
      producer.connect();
      producer.publish(topic, "retained-1".getBytes(StandardCharsets.UTF_8), 1, true);
      producer.publish(topic, "regular-1".getBytes(StandardCharsets.UTF_8), 1, false);
      producer.publish(topic, "retained-2".getBytes(StandardCharsets.UTF_8), 1, true);
      producer.publish(topic, "regular-2".getBytes(StandardCharsets.UTF_8), 1, false);
      producer.disconnect();
      producer.close();

      // subscriber on broker 2 should receive all 4 messages
      assertTrue(latch.await(10, TimeUnit.SECONDS), "Subscriber on broker 2 should receive all 4 mirrored messages");
      assertEquals(4, receivedMessages.size());
      assertTrue(receivedMessages.contains("retained-1"), "Should receive retained-1");
      assertTrue(receivedMessages.contains("regular-1"), "Should receive regular-1");
      assertTrue(receivedMessages.contains("retained-2"), "Should receive retained-2");
      assertTrue(receivedMessages.contains("regular-2"), "Should receive regular-2");
      sub.disconnect();
      sub.close();

      // verify retain queue on server2 has exactly 1 message (retained-2 replaced retained-1)
      Wait.assertTrue(() -> {
         org.apache.activemq.artemis.core.server.Queue queue = server2.locateQueue(retainQueueName);
         return queue != null && queue.getMessageCount() == 1;
      }, 10000, 100);

      // a new subscriber on broker 2 should get only the latest retained message
      CountDownLatch retainLatch = new CountDownLatch(1);
      AtomicReference<String> retainedReceived = new AtomicReference<>();
      MqttClient sub2 = createPahoClient("subscriber2", BROKER2_PORT);
      sub2.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String t, MqttMessage m) {
            retainedReceived.set(new String(m.getPayload(), StandardCharsets.UTF_8));
            retainLatch.countDown();
         }
      });
      sub2.connect();
      sub2.subscribe(topic, 1);
      assertTrue(retainLatch.await(5, TimeUnit.SECONDS), "New subscriber on broker 2 should receive the retained message");
      assertEquals("retained-2", retainedReceived.get());
      sub2.disconnect();
      sub2.close();
   }

   @Test
   @Timeout(60)
   public void testPartitionedSubscriptionsWithMirroredPublishes() throws Exception {
      ActiveMQServer server1 = createMirroredServer(0, BROKER1_PORT, BROKER2_PORT, "mirrorToServer2");
      ActiveMQServer server2 = createMirroredServer(1, BROKER2_PORT, BROKER1_PORT, "mirrorToServer1");

      server1.start();
      server1.waitForActivation(10, TimeUnit.SECONDS);
      server2.start();
      server2.waitForActivation(10, TimeUnit.SECONDS);

      Wait.assertTrue(() -> server1.locateQueue("$ACTIVEMQ_ARTEMIS_MIRROR_mirrorToServer2") != null);
      Wait.assertTrue(() -> server2.locateQueue("$ACTIVEMQ_ARTEMIS_MIRROR_mirrorToServer1") != null);

      final String sportsTopic = "news/sports";
      final String techTopic = "news/tech";

      // subscribers are partitioned across brokers: sub-a on broker 1, sub-b on broker 2
      CountDownLatch sportsLatch = new CountDownLatch(2);
      CopyOnWriteArrayList<String> sportsReceived = new CopyOnWriteArrayList<>();
      MqttClient subA = createPahoClient("sub-a", BROKER1_PORT);
      subA.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String t, MqttMessage m) {
            sportsReceived.add(new String(m.getPayload(), StandardCharsets.UTF_8));
            sportsLatch.countDown();
         }
      });
      subA.connect();
      subA.subscribe(sportsTopic, 1);

      CountDownLatch techLatch = new CountDownLatch(2);
      CopyOnWriteArrayList<String> techReceived = new CopyOnWriteArrayList<>();
      MqttClient subB = createPahoClient("sub-b", BROKER2_PORT);
      subB.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String t, MqttMessage m) {
            techReceived.add(new String(m.getPayload(), StandardCharsets.UTF_8));
            techLatch.countDown();
         }
      });
      subB.connect();
      subB.subscribe(techTopic, 1);

      // wait for subscription queues to be mirrored to both brokers
      String sportsQueueName = MQTTUtil.getCoreQueueFromMqttTopic(sportsTopic, "sub-a", server1.getConfiguration().getWildcardConfiguration());
      String techQueueName = MQTTUtil.getCoreQueueFromMqttTopic(techTopic, "sub-b", server2.getConfiguration().getWildcardConfiguration());
      Wait.assertTrue(() -> server2.locateQueue(sportsQueueName) != null, 5000, 100);
      Wait.assertTrue(() -> server1.locateQueue(techQueueName) != null, 5000, 100);

      // publishers can connect to either broker — messages reach the right subscriber via mirroring
      MqttClient pub1 = createPahoClient("pub1", BROKER2_PORT);
      pub1.connect();
      pub1.publish(sportsTopic, "goal".getBytes(StandardCharsets.UTF_8), 1, false);
      pub1.disconnect();
      pub1.close();

      MqttClient pub2 = createPahoClient("pub2", BROKER1_PORT);
      pub2.connect();
      pub2.publish(techTopic, "update".getBytes(StandardCharsets.UTF_8), 1, false);
      pub2.disconnect();
      pub2.close();

      // local publishes also work
      MqttClient pub3 = createPahoClient("pub3", BROKER1_PORT);
      pub3.connect();
      pub3.publish(sportsTopic, "try".getBytes(StandardCharsets.UTF_8), 1, false);
      pub3.disconnect();
      pub3.close();

      MqttClient pub4 = createPahoClient("pub4", BROKER2_PORT);
      pub4.connect();
      pub4.publish(techTopic, "release".getBytes(StandardCharsets.UTF_8), 1, false);
      pub4.disconnect();
      pub4.close();

      assertTrue(sportsLatch.await(10, TimeUnit.SECONDS), "sub-a should receive both sports messages");
      assertTrue(techLatch.await(10, TimeUnit.SECONDS), "sub-b should receive both tech messages");

      assertEquals(2, sportsReceived.size());
      assertTrue(sportsReceived.contains("goal"), "sub-a should receive 'goal' published on broker 2");
      assertTrue(sportsReceived.contains("try"), "sub-a should receive 'try' published on broker 1");

      assertEquals(2, techReceived.size());
      assertTrue(techReceived.contains("update"), "sub-b should receive 'update' published on broker 1");
      assertTrue(techReceived.contains("release"), "sub-b should receive 'release' published on broker 2");

      subA.disconnect();
      subA.close();
      subB.disconnect();
      subB.close();
   }

   @Test
   @Timeout(60)
   public void testPartitionedSubscriptionsMessageOnlyMirror() throws Exception {
      ActiveMQServer server1 = createMessageOnlyMirroredServer(0, BROKER1_PORT, BROKER2_PORT, "mirrorToServer2");
      ActiveMQServer server2 = createMessageOnlyMirroredServer(1, BROKER2_PORT, BROKER1_PORT, "mirrorToServer1");

      server1.start();
      server1.waitForActivation(10, TimeUnit.SECONDS);
      server2.start();
      server2.waitForActivation(10, TimeUnit.SECONDS);

      Wait.assertTrue(() -> server1.locateQueue("$ACTIVEMQ_ARTEMIS_MIRROR_mirrorToServer2") != null);
      Wait.assertTrue(() -> server2.locateQueue("$ACTIVEMQ_ARTEMIS_MIRROR_mirrorToServer1") != null);

      final String sportsTopic = "news/sports";
      final String techTopic = "news/tech";

      // subscribers are partitioned: sub-a on broker 1 for sports, sub-b on broker 2 for tech
      CountDownLatch sportsLatch = new CountDownLatch(2);
      CopyOnWriteArrayList<String> sportsReceived = new CopyOnWriteArrayList<>();
      MqttClient subA = createPahoClient("sub-a", BROKER1_PORT);
      subA.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String t, MqttMessage m) {
            sportsReceived.add(new String(m.getPayload(), StandardCharsets.UTF_8));
            sportsLatch.countDown();
         }
      });
      subA.connect();
      subA.subscribe(sportsTopic, 1);

      CountDownLatch techLatch = new CountDownLatch(2);
      CopyOnWriteArrayList<String> techReceived = new CopyOnWriteArrayList<>();
      MqttClient subB = createPahoClient("sub-b", BROKER2_PORT);
      subB.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String t, MqttMessage m) {
            techReceived.add(new String(m.getPayload(), StandardCharsets.UTF_8));
            techLatch.countDown();
         }
      });
      subB.connect();
      subB.subscribe(techTopic, 1);

      // no waiting for queue mirroring — subscription queues are NOT mirrored
      // the mirror source forwards messages even with no local bindings

      // publish on the broker that has NO local subscriber for that topic
      MqttClient pub1 = createPahoClient("pub1", BROKER2_PORT);
      pub1.connect();
      pub1.publish(sportsTopic, "goal".getBytes(StandardCharsets.UTF_8), 1, false);
      pub1.disconnect();
      pub1.close();

      MqttClient pub2 = createPahoClient("pub2", BROKER1_PORT);
      pub2.connect();
      pub2.publish(techTopic, "update".getBytes(StandardCharsets.UTF_8), 1, false);
      pub2.disconnect();
      pub2.close();

      // also publish locally to verify local delivery still works
      MqttClient pub3 = createPahoClient("pub3", BROKER1_PORT);
      pub3.connect();
      pub3.publish(sportsTopic, "try".getBytes(StandardCharsets.UTF_8), 1, false);
      pub3.disconnect();
      pub3.close();

      MqttClient pub4 = createPahoClient("pub4", BROKER2_PORT);
      pub4.connect();
      pub4.publish(techTopic, "release".getBytes(StandardCharsets.UTF_8), 1, false);
      pub4.disconnect();
      pub4.close();

      assertTrue(sportsLatch.await(10, TimeUnit.SECONDS), "sub-a should receive both sports messages");
      assertTrue(techLatch.await(10, TimeUnit.SECONDS), "sub-b should receive both tech messages");

      assertEquals(2, sportsReceived.size());
      assertTrue(sportsReceived.contains("goal"), "sub-a should receive 'goal' published on broker 2");
      assertTrue(sportsReceived.contains("try"), "sub-a should receive 'try' published on broker 1");

      assertEquals(2, techReceived.size());
      assertTrue(techReceived.contains("update"), "sub-b should receive 'update' published on broker 1");
      assertTrue(techReceived.contains("release"), "sub-b should receive 'release' published on broker 2");

      // verify subscription queues were NOT mirrored
      String sportsQueueName = MQTTUtil.getCoreQueueFromMqttTopic(sportsTopic, "sub-a", server1.getConfiguration().getWildcardConfiguration());
      String techQueueName = MQTTUtil.getCoreQueueFromMqttTopic(techTopic, "sub-b", server2.getConfiguration().getWildcardConfiguration());
      assertNull(server2.locateQueue(sportsQueueName), "sports queue should NOT exist on broker 2");
      assertNull(server1.locateQueue(techQueueName), "tech queue should NOT exist on broker 1");

      subA.disconnect();
      subA.close();
      subB.disconnect();
      subB.close();
   }

   @Test
   @Timeout(60)
   public void testOnlyMulticastMirroredWithNoBindings() throws Exception {
      ActiveMQServer server1 = createMessageOnlyMirroredServer(0, BROKER1_PORT, BROKER2_PORT, "mirrorToServer2");
      ActiveMQServer server2 = createMessageOnlyMirroredServer(1, BROKER2_PORT, BROKER1_PORT, "mirrorToServer1");

      server1.start();
      server1.waitForActivation(10, TimeUnit.SECONDS);
      server2.start();
      server2.waitForActivation(10, TimeUnit.SECONDS);

      Wait.assertTrue(() -> server1.locateQueue("$ACTIVEMQ_ARTEMIS_MIRROR_mirrorToServer2") != null);
      Wait.assertTrue(() -> server2.locateQueue("$ACTIVEMQ_ARTEMIS_MIRROR_mirrorToServer1") != null);

      // set up an anycast address on broker 1 (no queue) and a queue on broker 2 to receive if mirrored
      server1.addAddressInfo(new AddressInfo(SimpleString.of("test.anycast"), RoutingType.ANYCAST));
      server2.createQueue(QueueConfiguration.of("test.anycast").setRoutingType(RoutingType.ANYCAST));

      // set up a multicast subscriber on broker 2 — no local sub on broker 1
      final String topic = "test/multicast";
      CountDownLatch multicastLatch = new CountDownLatch(1);
      AtomicReference<String> multicastReceived = new AtomicReference<>();
      MqttClient sub = createPahoClient("subscriber", BROKER2_PORT);
      sub.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String t, MqttMessage m) {
            multicastReceived.set(new String(m.getPayload(), StandardCharsets.UTF_8));
            multicastLatch.countDown();
         }
      });
      sub.connect();
      sub.subscribe(topic, 1);

      // send an anycast message on broker 1 with no local queue — should NOT be mirrored
      CoreMessage anycastMsg = new CoreMessage().initBuffer(1024);
      anycastMsg.setAddress("test.anycast");
      anycastMsg.getBodyBuffer().writeString("anycast-payload");
      anycastMsg.setMessageID(server1.getStorageManager().generateID());
      server1.getPostOffice().route(anycastMsg, new RoutingContextImpl(null), true);

      // publish a multicast message on broker 1 with no local subscriber — should be mirrored
      MqttClient pub = createPahoClient("producer", BROKER1_PORT);
      pub.connect();
      pub.publish(topic, "multicast-payload".getBytes(StandardCharsets.UTF_8), 1, false);
      pub.disconnect();
      pub.close();

      // multicast message arrives on broker 2 — proves the mirror link works
      assertTrue(multicastLatch.await(10, TimeUnit.SECONDS), "multicast message should be mirrored to broker 2");
      assertEquals("multicast-payload", multicastReceived.get());

      // anycast queue on broker 2 should still be empty — anycast was NOT mirrored
      assertEquals(0, server2.locateQueue("test.anycast").getMessageCount(),
         "anycast message with no local bindings should NOT be mirrored");

      sub.disconnect();
      sub.close();
   }

   @Test
   @Timeout(60)
   public void testConcurrentRetainedUpdatesOnMirrorPair() throws Exception {
      ActiveMQServer server1 = createMirroredServer(0, BROKER1_PORT, BROKER2_PORT, "mirrorToServer2");
      ActiveMQServer server2 = createMirroredServer(1, BROKER2_PORT, BROKER1_PORT, "mirrorToServer1");

      server1.start();
      server1.waitForActivation(10, TimeUnit.SECONDS);
      server2.start();
      server2.waitForActivation(10, TimeUnit.SECONDS);

      Wait.assertTrue(() -> server1.locateQueue("$ACTIVEMQ_ARTEMIS_MIRROR_mirrorToServer2") != null);
      Wait.assertTrue(() -> server2.locateQueue("$ACTIVEMQ_ARTEMIS_MIRROR_mirrorToServer1") != null);

      final String topic = "test/retain/concurrent";
      final String retainQueueName = MQTTUtil.getCoreRetainAddressFromMqttTopic(topic, server1.getConfiguration().getWildcardConfiguration());

      // publish different retained messages from each broker concurrently
      CountDownLatch startLatch = new CountDownLatch(1);
      CountDownLatch doneLatch = new CountDownLatch(2);

      Thread t1 = new Thread(() -> {
         try {
            MqttClient pub = createPahoClient("pub1", BROKER1_PORT);
            pub.connect();
            startLatch.await(5, TimeUnit.SECONDS);
            pub.publish(topic, "from-broker-1".getBytes(StandardCharsets.UTF_8), 1, true);
            pub.disconnect();
            pub.close();
         } catch (Exception e) {
            throw new RuntimeException(e);
         } finally {
            doneLatch.countDown();
         }
      });

      Thread t2 = new Thread(() -> {
         try {
            MqttClient pub = createPahoClient("pub2", BROKER2_PORT);
            pub.connect();
            startLatch.await(5, TimeUnit.SECONDS);
            pub.publish(topic, "from-broker-2".getBytes(StandardCharsets.UTF_8), 1, true);
            pub.disconnect();
            pub.close();
         } catch (Exception e) {
            throw new RuntimeException(e);
         } finally {
            doneLatch.countDown();
         }
      });

      t1.start();
      t2.start();
      startLatch.countDown();
      assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Both publishers should complete");

      // wait for retain queues to be populated on both brokers (may have >1 message
      // due to concurrent mirroring — MQTTRetainMessageManager handles this by
      // browsing to the last message and cleaning up extras on delivery)
      Wait.assertTrue(() -> {
         org.apache.activemq.artemis.core.server.Queue q1 = server1.locateQueue(retainQueueName);
         org.apache.activemq.artemis.core.server.Queue q2 = server2.locateQueue(retainQueueName);
         return q1 != null && q1.getMessageCount() >= 1 && q2 != null && q2.getMessageCount() >= 1;
      }, 10000, 100);

      // both brokers should serve a valid retained message to new subscribers
      CountDownLatch latch1 = new CountDownLatch(1);
      AtomicReference<String> received1 = new AtomicReference<>();
      MqttClient sub1 = createPahoClient("sub1", BROKER1_PORT);
      sub1.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String t, MqttMessage m) {
            received1.set(new String(m.getPayload(), StandardCharsets.UTF_8));
            latch1.countDown();
         }
      });
      sub1.connect();
      sub1.subscribe(topic, 1);
      assertTrue(latch1.await(5, TimeUnit.SECONDS), "Subscriber on broker 1 should receive the retained message");
      sub1.disconnect();
      sub1.close();

      CountDownLatch latch2 = new CountDownLatch(1);
      AtomicReference<String> received2 = new AtomicReference<>();
      MqttClient sub2 = createPahoClient("sub2", BROKER2_PORT);
      sub2.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String t, MqttMessage m) {
            received2.set(new String(m.getPayload(), StandardCharsets.UTF_8));
            latch2.countDown();
         }
      });
      sub2.connect();
      sub2.subscribe(topic, 1);
      assertTrue(latch2.await(5, TimeUnit.SECONDS), "Subscriber on broker 2 should receive the retained message");
      sub2.disconnect();
      sub2.close();

      // both brokers should serve a valid retained message (either "from-broker-1" or "from-broker-2")
      assertTrue("from-broker-1".equals(received1.get()) || "from-broker-2".equals(received1.get()),
         "Broker 1 should serve a valid retained message, got: " + received1.get());
      assertTrue("from-broker-1".equals(received2.get()) || "from-broker-2".equals(received2.get()),
         "Broker 2 should serve a valid retained message, got: " + received2.get());
   }

   @Test
   @Timeout(60)
   public void testRetainedMessageWithMessageOnlyMirror() throws Exception {
      ActiveMQServer server1 = createMessageOnlyMirroredServer(0, BROKER1_PORT, BROKER2_PORT, "mirrorToServer2");
      ActiveMQServer server2 = createMessageOnlyMirroredServer(1, BROKER2_PORT, BROKER1_PORT, "mirrorToServer1");

      server1.start();
      server1.waitForActivation(10, TimeUnit.SECONDS);
      server2.start();
      server2.waitForActivation(10, TimeUnit.SECONDS);

      Wait.assertTrue(() -> server1.locateQueue("$ACTIVEMQ_ARTEMIS_MIRROR_mirrorToServer2") != null);
      Wait.assertTrue(() -> server2.locateQueue("$ACTIVEMQ_ARTEMIS_MIRROR_mirrorToServer1") != null);

      final String topic = "test/retain/msgonly";
      final String payload = "retained-msg-only-mirror";
      final String retainQueueName = MQTTUtil.getCoreRetainAddressFromMqttTopic(topic, server1.getConfiguration().getWildcardConfiguration());

      // subscribe on broker 2 first so the subscription queue exists locally
      CountDownLatch latch = new CountDownLatch(1);
      AtomicReference<String> received = new AtomicReference<>();
      MqttClient sub = createPahoClient("subscriber", BROKER2_PORT);
      sub.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String t, MqttMessage m) {
            received.set(new String(m.getPayload(), StandardCharsets.UTF_8));
            latch.countDown();
         }
      });
      sub.connect();
      sub.subscribe(topic, 1);

      // publish retained on broker 1
      MqttClient producer = createPahoClient("producer", BROKER1_PORT);
      producer.connect();
      producer.publish(topic, payload.getBytes(StandardCharsets.UTF_8), 1, true);
      producer.disconnect();
      producer.close();

      assertTrue(latch.await(10, TimeUnit.SECONDS), "Subscriber on broker 2 should receive the mirrored message");
      assertEquals(payload, received.get());

      // retain queue on server1 should be populated
      Wait.assertTrue(() -> {
         org.apache.activemq.artemis.core.server.Queue queue = server1.locateQueue(retainQueueName);
         return queue != null && queue.getMessageCount() == 1;
      }, 5000, 100);

      // retain queue on server2 should be created locally by the plugin (not via queue creation mirroring)
      Wait.assertTrue(() -> {
         org.apache.activemq.artemis.core.server.Queue queue = server2.locateQueue(retainQueueName);
         return queue != null && queue.getMessageCount() == 1;
      }, 10000, 100);

      sub.disconnect();
      sub.close();

      // a new subscriber on broker 2 should get the retained message from the local retain queue
      CountDownLatch retainLatch = new CountDownLatch(1);
      AtomicReference<String> retainReceived = new AtomicReference<>();
      MqttClient sub2 = createPahoClient("subscriber2", BROKER2_PORT);
      sub2.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String t, MqttMessage m) {
            retainReceived.set(new String(m.getPayload(), StandardCharsets.UTF_8));
            retainLatch.countDown();
         }
      });
      sub2.connect();
      sub2.subscribe(topic, 1);
      assertTrue(retainLatch.await(5, TimeUnit.SECONDS), "New subscriber on broker 2 should receive the retained message");
      assertEquals(payload, retainReceived.get());
      sub2.disconnect();
      sub2.close();
   }

   @Test
   @Timeout(60)
   public void testRetainedMessageSurvivesBrokerStop() throws Exception {
      ActiveMQServer server1 = createMirroredServer(0, BROKER1_PORT, BROKER2_PORT, "mirrorToServer2");
      ActiveMQServer server2 = createMirroredServer(1, BROKER2_PORT, BROKER1_PORT, "mirrorToServer1");

      server1.start();
      server1.waitForActivation(10, TimeUnit.SECONDS);
      server2.start();
      server2.waitForActivation(10, TimeUnit.SECONDS);

      Wait.assertTrue(() -> server1.locateQueue("$ACTIVEMQ_ARTEMIS_MIRROR_mirrorToServer2") != null);
      Wait.assertTrue(() -> server2.locateQueue("$ACTIVEMQ_ARTEMIS_MIRROR_mirrorToServer1") != null);

      final String topic = "test/retain/failover";
      final String payload = "retained-survives-stop";
      final String retainQueueName = MQTTUtil.getCoreRetainAddressFromMqttTopic(topic, server1.getConfiguration().getWildcardConfiguration());

      // publish retained on broker 1
      MqttClient producer = createPahoClient("producer", BROKER1_PORT);
      producer.connect();
      producer.publish(topic, payload.getBytes(StandardCharsets.UTF_8), 1, true);
      producer.disconnect();
      producer.close();

      // wait for retain queue on both brokers
      Wait.assertTrue(() -> {
         org.apache.activemq.artemis.core.server.Queue queue = server1.locateQueue(retainQueueName);
         return queue != null && queue.getMessageCount() == 1;
      }, 5000, 100);
      Wait.assertTrue(() -> {
         org.apache.activemq.artemis.core.server.Queue queue = server2.locateQueue(retainQueueName);
         return queue != null && queue.getMessageCount() == 1;
      }, 10000, 100);

      // stop broker 1
      server1.stop();

      // a new subscriber on broker 2 should still get the retained message from the local retain queue
      CountDownLatch latch = new CountDownLatch(1);
      AtomicReference<String> received = new AtomicReference<>();
      MqttClient sub = createPahoClient("subscriber", BROKER2_PORT);
      sub.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String t, MqttMessage m) {
            received.set(new String(m.getPayload(), StandardCharsets.UTF_8));
            latch.countDown();
         }
      });
      sub.connect();
      sub.subscribe(topic, 1);
      assertTrue(latch.await(5, TimeUnit.SECONDS), "Subscriber on broker 2 should receive the retained message after broker 1 stopped");
      assertEquals(payload, received.get());
      sub.disconnect();
      sub.close();
   }

   @Test
   @Timeout(60)
   public void testRetainedMessageMirroredWithNoLocalSubscribers() throws Exception {
      ActiveMQServer server1 = createMessageOnlyMirroredServer(0, BROKER1_PORT, BROKER2_PORT, "mirrorToServer2");
      ActiveMQServer server2 = createMessageOnlyMirroredServer(1, BROKER2_PORT, BROKER1_PORT, "mirrorToServer1");

      server1.start();
      server1.waitForActivation(10, TimeUnit.SECONDS);
      server2.start();
      server2.waitForActivation(10, TimeUnit.SECONDS);

      Wait.assertTrue(() -> server1.locateQueue("$ACTIVEMQ_ARTEMIS_MIRROR_mirrorToServer2") != null);
      Wait.assertTrue(() -> server2.locateQueue("$ACTIVEMQ_ARTEMIS_MIRROR_mirrorToServer1") != null);

      final String topic = "test/retain/nosub";
      final String payload = "retained-no-subscribers";
      final String retainQueueName = MQTTUtil.getCoreRetainAddressFromMqttTopic(topic, server1.getConfiguration().getWildcardConfiguration());

      // publish a retained message on broker 1 with NO subscribers anywhere
      MqttClient producer = createPahoClient("producer", BROKER1_PORT);
      producer.connect();
      producer.publish(topic, payload.getBytes(StandardCharsets.UTF_8), 1, true);
      producer.disconnect();
      producer.close();

      // retain queue on server1 should be populated locally by the plugin
      Wait.assertTrue(() -> {
         org.apache.activemq.artemis.core.server.Queue queue = server1.locateQueue(retainQueueName);
         return queue != null && queue.getMessageCount() == 1;
      }, 5000, 100);

      // retain queue on server2 should also be populated — message mirrored via NO_BINDINGS path
      Wait.assertTrue(() -> {
         org.apache.activemq.artemis.core.server.Queue queue = server2.locateQueue(retainQueueName);
         return queue != null && queue.getMessageCount() == 1;
      }, 10000, 100);

      // a new subscriber on broker 2 should receive the retained message
      CountDownLatch latch = new CountDownLatch(1);
      AtomicReference<String> received = new AtomicReference<>();
      MqttClient sub = createPahoClient("subscriber", BROKER2_PORT);
      sub.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String t, MqttMessage m) {
            received.set(new String(m.getPayload(), StandardCharsets.UTF_8));
            latch.countDown();
         }
      });
      sub.connect();
      sub.subscribe(topic, 1);
      assertTrue(latch.await(5, TimeUnit.SECONDS), "New subscriber on broker 2 should receive the retained message");
      assertEquals(payload, received.get());
      sub.disconnect();
      sub.close();
   }

   private interface DefaultMqttCallback extends MqttCallback {

      @Override
      default void disconnected(MqttDisconnectResponse disconnectResponse) {
      }

      @Override
      default void mqttErrorOccurred(MqttException exception) {
      }

      @Override
      default void messageArrived(String topic, MqttMessage message) {
      }

      @Override
      default void deliveryComplete(IMqttToken token) {
      }

      @Override
      default void connectComplete(boolean reconnect, String serverURI) {
      }

      @Override
      default void authPacketArrived(int reasonCode, MqttProperties properties) {
      }
   }
}
