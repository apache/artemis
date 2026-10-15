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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPMirrorBrokerConnectionElement;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTRetainMessagePlugin;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTUtil;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.Wait;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
      server.getConfiguration().registerBrokerPlugin(new MQTTRetainMessagePlugin());
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
      sub1.setCallback(new MQTT5TestSupport.DefaultMqttCallback() {
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
      sub2.setCallback(new MQTT5TestSupport.DefaultMqttCallback() {
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
      sub3.setCallback(new MQTT5TestSupport.DefaultMqttCallback() {
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
      sub4.setCallback(new MQTT5TestSupport.DefaultMqttCallback() {
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
      sub.setCallback(new MQTT5TestSupport.DefaultMqttCallback() {
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
      sub.setCallback(new MQTT5TestSupport.DefaultMqttCallback() {
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
      ActiveMQServer server1 = createMirroredServer(0, BROKER1_PORT, BROKER2_PORT, "mirrorToServer2");
      ActiveMQServer server2 = createMirroredServer(1, BROKER2_PORT, BROKER1_PORT, "mirrorToServer1");

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

      // retain queue on server2 should also be populated via create and mirror
      Wait.assertTrue(() -> {
         org.apache.activemq.artemis.core.server.Queue queue = server2.locateQueue(retainQueueName);
         return queue != null && queue.getMessageCount() == 1;
      }, 10000, 100);

      // a new subscriber on broker 2 should receive the retained message
      CountDownLatch latch = new CountDownLatch(1);
      AtomicReference<String> received = new AtomicReference<>();
      MqttClient sub = createPahoClient("subscriber", BROKER2_PORT);
      sub.setCallback(new MQTT5TestSupport.DefaultMqttCallback() {
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
}
