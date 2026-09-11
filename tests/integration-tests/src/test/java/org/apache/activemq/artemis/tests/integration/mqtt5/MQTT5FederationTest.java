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

import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPFederatedBrokerConnectionElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPFederationAddressPolicyElement;
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

public class MQTT5FederationTest extends ActiveMQTestBase {

   private static final int BROKER1_PORT = 1883;
   private static final int BROKER2_PORT = 1884;

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      super.setUp();
   }

   private ActiveMQServer createFederatedServer(int serverID, int port, int targetPort, String connectionName) throws Exception {
      ActiveMQServer server = createServer(true, createDefaultConfig(serverID, true));
      server.getConfiguration().getAcceptorConfigurations().clear();
      server.getConfiguration().addAcceptorConfiguration("server", "tcp://localhost:" + port);
      server.getConfiguration().setSecurityEnabled(false);
      server.getConfiguration().setMqttSessionScanInterval(200);

      AddressSettings addressSettings = new AddressSettings();
      addressSettings.setAutoCreateQueues(true);
      addressSettings.setAutoCreateAddresses(true);
      server.getConfiguration().getAddressSettings().put("#", addressSettings);

      final AMQPFederationAddressPolicyElement localAddressPolicy = new AMQPFederationAddressPolicyElement();
      localAddressPolicy.setName("mqtt-address-policy");
      localAddressPolicy.addToIncludes("#");
      localAddressPolicy.setAutoDelete(false);
      localAddressPolicy.setAutoDeleteDelay(-1L);
      localAddressPolicy.setAutoDeleteMessageCount(-1L);

      final AMQPFederatedBrokerConnectionElement federationElement = new AMQPFederatedBrokerConnectionElement();
      federationElement.setName(connectionName);
      federationElement.addLocalAddressPolicy(localAddressPolicy);

      final AMQPBrokerConnectConfiguration amqpConnection =
         new AMQPBrokerConnectConfiguration(connectionName, "tcp://localhost:" + targetPort)
            .setReconnectAttempts(10)
            .setRetryInterval(100);
      amqpConnection.addElement(federationElement);

      server.getConfiguration().addAMQPConnection(amqpConnection);

      return server;
   }

   private ActiveMQServer createBindingsConsumerFederatedServer(int serverID, int port, int targetPort, String connectionName) throws Exception {
      ActiveMQServer server = createServer(true, createDefaultConfig(serverID, true));
      server.getConfiguration().getAcceptorConfigurations().clear();
      server.getConfiguration().addAcceptorConfiguration("server", "tcp://localhost:" + port);
      server.getConfiguration().setSecurityEnabled(false);
      server.getConfiguration().setMqttSessionScanInterval(200);

      AddressSettings addressSettings = new AddressSettings();
      addressSettings.setAutoCreateQueues(true);
      addressSettings.setAutoCreateAddresses(true);
      server.getConfiguration().getAddressSettings().put("#", addressSettings);

      final AMQPFederationAddressPolicyElement localAddressPolicy = new AMQPFederationAddressPolicyElement();
      localAddressPolicy.setName("mqtt-address-policy");
      localAddressPolicy.addToIncludes("#");
      localAddressPolicy.setAutoDelete(false);
      localAddressPolicy.setAutoDeleteDelay(-1L);
      localAddressPolicy.setAutoDeleteMessageCount(-1L);
      localAddressPolicy.addProperty("ignoreAddressBindingFilters", "false");

      final AMQPFederatedBrokerConnectionElement federationElement = new AMQPFederatedBrokerConnectionElement();
      federationElement.setName(connectionName);
      federationElement.addLocalAddressPolicy(localAddressPolicy);

      final AMQPBrokerConnectConfiguration amqpConnection =
         new AMQPBrokerConnectConfiguration(connectionName, "tcp://localhost:" + targetPort)
            .setReconnectAttempts(10)
            .setRetryInterval(100);
      amqpConnection.addElement(federationElement);

      server.getConfiguration().addAMQPConnection(amqpConnection);

      return server;
   }

   private MqttClient createPahoClient(String clientId, int port) throws MqttException {
      return new MqttClient("tcp://localhost:" + port, clientId, new MemoryPersistence());
   }

   @Test
   @Timeout(60)
   public void testPartitionedSubscriptionsWithFederation() throws Exception {
      ActiveMQServer server1 = createFederatedServer(0, BROKER1_PORT, BROKER2_PORT, "federationToServer2");
      ActiveMQServer server2 = createFederatedServer(1, BROKER2_PORT, BROKER1_PORT, "federationToServer1");

      server1.start();
      server1.waitForActivation(10, TimeUnit.SECONDS);
      server2.start();
      server2.waitForActivation(10, TimeUnit.SECONDS);

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

      subA.disconnect();
      subA.close();
      subB.disconnect();
      subB.close();
   }

   @Test
   @Timeout(60)
   public void testRetainedMessageWithFederation() throws Exception {
      ActiveMQServer server1 = createFederatedServer(0, BROKER1_PORT, BROKER2_PORT, "federationToServer2");
      ActiveMQServer server2 = createFederatedServer(1, BROKER2_PORT, BROKER1_PORT, "federationToServer1");

      server1.start();
      server1.waitForActivation(10, TimeUnit.SECONDS);
      server2.start();
      server2.waitForActivation(10, TimeUnit.SECONDS);

      final String topic = "test/retain/federation";
      final String payload = "retained-message-payload";

      // subscribe on broker 2 first — this creates federation demand
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

      // publish retained message on broker 1
      MqttClient producer = createPahoClient("producer", BROKER1_PORT);
      producer.connect();
      producer.publish(topic, payload.getBytes(StandardCharsets.UTF_8), 1, true);
      producer.disconnect();
      producer.close();

      // subscriber on broker 2 should receive the message via federation
      assertTrue(latch.await(10, TimeUnit.SECONDS), "Subscriber on broker 2 should receive the federated message");
      assertEquals(payload, received.get());

      // verify retain queue exists on server1 (local retain handling)
      final String retainQueueName = MQTTUtil.getCoreRetainAddressFromMqttTopic(topic, server1.getConfiguration().getWildcardConfiguration());
      Wait.assertTrue(() -> {
         org.apache.activemq.artemis.core.server.Queue queue = server1.locateQueue(retainQueueName);
         return queue != null && queue.getMessageCount() == 1;
      }, 5000, 100);

      // retain queue should also exist on server2 — populated by federation consumer
      Wait.assertTrue(() -> {
         org.apache.activemq.artemis.core.server.Queue queue = server2.locateQueue(retainQueueName);
         return queue != null && queue.getMessageCount() == 1;
      }, 5000, 100);

      // verify no duplicate retain processing: messagesAdded should be exactly 1
      assertEquals(1, server2.locateQueue(retainQueueName).getMessagesAdded(),
         "Retain queue on server2 should have exactly 1 message added (no duplicate processing)");

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
   public void testClearRetainedMessageWithFederation() throws Exception {
      ActiveMQServer server1 = createFederatedServer(0, BROKER1_PORT, BROKER2_PORT, "federationToServer2");
      ActiveMQServer server2 = createFederatedServer(1, BROKER2_PORT, BROKER1_PORT, "federationToServer1");

      server1.start();
      server1.waitForActivation(10, TimeUnit.SECONDS);
      server2.start();
      server2.waitForActivation(10, TimeUnit.SECONDS);

      final String topic = "test/retain/federation/clear";
      final String payload = "retained-to-clear";
      final String retainQueueName = MQTTUtil.getCoreRetainAddressFromMqttTopic(topic, server1.getConfiguration().getWildcardConfiguration());

      // subscribe on broker 2 to create federation demand
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

      // publish a retained message on broker 1
      MqttClient producer = createPahoClient("producer", BROKER1_PORT);
      producer.connect();
      producer.publish(topic, payload.getBytes(StandardCharsets.UTF_8), 1, true);

      assertTrue(latch.await(10, TimeUnit.SECONDS), "Subscriber on broker 2 should receive the federated message");
      assertEquals(payload, received.get());

      // verify retain queue populated on both brokers
      Wait.assertTrue(() -> {
         org.apache.activemq.artemis.core.server.Queue queue = server1.locateQueue(retainQueueName);
         return queue != null && queue.getMessageCount() == 1;
      }, 5000, 100);
      Wait.assertTrue(() -> {
         org.apache.activemq.artemis.core.server.Queue queue = server2.locateQueue(retainQueueName);
         return queue != null && queue.getMessageCount() == 1;
      }, 5000, 100);

      // clear the retained message with a zero-length payload (MQTT spec §3.3.1.3)
      producer.publish(topic, new byte[0], 1, true);
      producer.disconnect();
      producer.close();

      // retain queue on server1 should be empty
      Wait.assertTrue(() -> {
         org.apache.activemq.artemis.core.server.Queue queue = server1.locateQueue(retainQueueName);
         return queue != null && queue.getMessageCount() == 0;
      }, 5000, 100);

      // retain queue on server2 should also be empty — cleared via federated zero-length message
      Wait.assertTrue(() -> {
         org.apache.activemq.artemis.core.server.Queue queue = server2.locateQueue(retainQueueName);
         return queue != null && queue.getMessageCount() == 0;
      }, 10000, 100);

      sub.disconnect();
      sub.close();

      // a new subscriber on broker 2 should NOT receive a retained message
      CountDownLatch noRetainLatch = new CountDownLatch(1);
      AtomicReference<String> noRetainReceived = new AtomicReference<>();
      MqttClient sub2 = createPahoClient("subscriber2", BROKER2_PORT);
      sub2.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String t, MqttMessage m) {
            noRetainReceived.set(new String(m.getPayload(), StandardCharsets.UTF_8));
            noRetainLatch.countDown();
         }
      });
      sub2.connect();
      sub2.subscribe(topic, 1);

      // publish a regular message so we know the subscription is active
      MqttClient probe = createPahoClient("probe", BROKER1_PORT);
      probe.connect();
      probe.publish(topic, "probe".getBytes(StandardCharsets.UTF_8), 1, false);
      probe.disconnect();
      probe.close();

      assertTrue(noRetainLatch.await(10, TimeUnit.SECONDS), "Subscriber should receive the probe message");
      assertEquals("probe", noRetainReceived.get(), "Only the probe message should arrive — no retained message");

      sub2.disconnect();
      sub2.close();
   }

   @Test
   @Timeout(60)
   public void testRetainedMessageNotHandledWithFederationBindingsConsumer() throws Exception {
      ActiveMQServer server1 = createBindingsConsumerFederatedServer(0, BROKER1_PORT, BROKER2_PORT, "federationToServer2");
      ActiveMQServer server2 = createBindingsConsumerFederatedServer(1, BROKER2_PORT, BROKER1_PORT, "federationToServer1");

      server1.start();
      server1.waitForActivation(10, TimeUnit.SECONDS);
      server2.start();
      server2.waitForActivation(10, TimeUnit.SECONDS);

      final String topic = "test/retain/federation/bindings";
      final String payload = "retained-via-bindings-consumer";

      // two subscribers on broker 2 — the bindings consumer routes directly to
      // bindings via processRoute() which does not fire broker plugins, so the
      // MQTTRetainMessagePlugin will not populate a retain queue on server2
      CountDownLatch latch = new CountDownLatch(2);
      CopyOnWriteArrayList<String> received = new CopyOnWriteArrayList<>();
      MqttClient subA = createPahoClient("subscriberA", BROKER2_PORT);
      subA.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String t, MqttMessage m) {
            received.add(new String(m.getPayload(), StandardCharsets.UTF_8));
            latch.countDown();
         }
      });
      subA.connect();
      subA.subscribe(topic, 1);

      MqttClient subB = createPahoClient("subscriberB", BROKER2_PORT);
      subB.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String t, MqttMessage m) {
            received.add(new String(m.getPayload(), StandardCharsets.UTF_8));
            latch.countDown();
         }
      });
      subB.connect();
      subB.subscribe(topic, 1);

      // publish retained on broker 1
      MqttClient producer = createPahoClient("producer", BROKER1_PORT);
      producer.connect();
      producer.publish(topic, payload.getBytes(StandardCharsets.UTF_8), 1, true);
      producer.disconnect();
      producer.close();

      assertTrue(latch.await(10, TimeUnit.SECONDS), "Both subscribers on broker 2 should receive the federated message");
      assertEquals(2, received.size());

      // retain queue on server1 is populated locally by the plugin on the publishing broker
      final String retainQueueName = MQTTUtil.getCoreRetainAddressFromMqttTopic(topic, server1.getConfiguration().getWildcardConfiguration());
      Wait.assertTrue(() -> {
         org.apache.activemq.artemis.core.server.Queue queue = server1.locateQueue(retainQueueName);
         return queue != null && queue.getMessageCount() == 1;
      }, 5000, 100);

      subA.disconnect();
      subA.close();
      subB.disconnect();
      subB.close();

      // retain queue on server2 should NOT exist — the bindings consumer uses
      // processRoute() which bypasses broker plugins (unlike the conduit consumer
      // which uses route() and fires the MQTTRetainMessagePlugin naturally)
      assertNull(server2.locateQueue(retainQueueName),
         "Retain queue should not be created on server2 via the bindings consumer path");

      // a new subscriber on broker 2 should NOT receive a retained message
      CountDownLatch probeLatch = new CountDownLatch(1);
      AtomicReference<String> probeReceived = new AtomicReference<>();
      MqttClient sub2 = createPahoClient("subscriber2", BROKER2_PORT);
      sub2.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String t, MqttMessage m) {
            probeReceived.set(new String(m.getPayload(), StandardCharsets.UTF_8));
            probeLatch.countDown();
         }
      });
      sub2.connect();
      sub2.subscribe(topic, 1);

      // send a non-retained probe to confirm the subscription is active
      MqttClient probe = createPahoClient("probe", BROKER1_PORT);
      probe.connect();
      probe.publish(topic, "probe".getBytes(StandardCharsets.UTF_8), 1, false);
      probe.disconnect();
      probe.close();

      assertTrue(probeLatch.await(10, TimeUnit.SECONDS), "Subscriber should receive the probe message");
      assertEquals("probe", probeReceived.get(), "Only the probe message should arrive — no retained message");

      sub2.disconnect();
      sub2.close();
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
