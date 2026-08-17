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

import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBridgeAddressPolicyElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBridgeBrokerConnectionElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTUtil;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests MQTT retained message behaviour over a unidirectional AMQP bridge.
 * Broker 2 bridges FROM broker 1 — when a subscriber on broker 2 creates
 * demand, messages (including retained) are pulled from broker 1.
 * The {@code MQTTRetainMessagePlugin} fires via {@code afterMessageRoute}
 * on the bridge consumer's routing path, so retained messages should be
 * populated in the local retain queue on broker 2 automatically.
 */
public class MQTT5BridgeTest extends ActiveMQTestBase {

   private static final int BROKER1_PORT = 1883;
   private static final int BROKER2_PORT = 1884;

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      super.setUp();
   }

   private ActiveMQServer createServer(int serverID, int port) throws Exception {
      ActiveMQServer server = createServer(true, createDefaultConfig(serverID, true));
      server.getConfiguration().getAcceptorConfigurations().clear();
      server.getConfiguration().addAcceptorConfiguration("server", "tcp://localhost:" + port);
      server.getConfiguration().setSecurityEnabled(false);
      server.getConfiguration().setMqttSessionScanInterval(200);

      AddressSettings addressSettings = new AddressSettings();
      addressSettings.setAutoCreateQueues(true);
      addressSettings.setAutoCreateAddresses(true);
      server.getConfiguration().getAddressSettings().put("#", addressSettings);

      return server;
   }

   private ActiveMQServer createBridgedServer() throws Exception {
      ActiveMQServer server = createServer(1, MQTT5BridgeTest.BROKER2_PORT);

      final AMQPBridgeAddressPolicyElement addressPolicy = new AMQPBridgeAddressPolicyElement();
      addressPolicy.setName("mqtt-bridge-policy");
      addressPolicy.addToIncludes("#");

      final AMQPBridgeBrokerConnectionElement bridgeElement = new AMQPBridgeBrokerConnectionElement();
      bridgeElement.setName("bridgeFromServer1");
      bridgeElement.addBridgeFromAddressPolicy(addressPolicy);

      final AMQPBrokerConnectConfiguration amqpConnection =
         new AMQPBrokerConnectConfiguration("bridgeFromServer1", "tcp://localhost:" + MQTT5BridgeTest.BROKER1_PORT)
            .setReconnectAttempts(10)
            .setRetryInterval(100);
      amqpConnection.addElement(bridgeElement);

      server.getConfiguration().addAMQPConnection(amqpConnection);

      return server;
   }

   private MqttClient createPahoClient(String clientId, int port) throws MqttException {
      return new MqttClient("tcp://localhost:" + port, clientId, new MemoryPersistence());
   }

   @Test
   @Timeout(60)
   public void testRetainedMessageOverBridge() throws Exception {
      // broker 1 is a plain server (source); broker 2 bridges FROM broker 1
      ActiveMQServer server1 = createServer(0, BROKER1_PORT);
      ActiveMQServer server2 = createBridgedServer();

      server1.start();
      server1.waitForActivation(10, TimeUnit.SECONDS);
      server2.start();
      server2.waitForActivation(10, TimeUnit.SECONDS);

      final String topic = "test/retain/bridge";
      final String payload = "retained-via-bridge";

      // subscribe on broker 2 first — this creates demand so the bridge pulls from broker 1
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

      // subscriber on broker 2 should receive the message via the bridge
      assertTrue(latch.await(10, TimeUnit.SECONDS), "Subscriber on broker 2 should receive the bridged message");
      assertEquals(payload, received.get());

      // verify retain queue on server1 (local MQTT retain handling)
      final String retainQueueName = MQTTUtil.getCoreRetainAddressFromMqttTopic(topic, server1.getConfiguration().getWildcardConfiguration());
      Wait.assertTrue(() -> {
         org.apache.activemq.artemis.core.server.Queue queue = server1.locateQueue(retainQueueName);
         return queue != null && queue.getMessageCount() == 1;
      }, 5000, 100);

      // retain queue should also exist on server2 — populated by the afterMessageRoute plugin
      Wait.assertTrue(() -> {
         org.apache.activemq.artemis.core.server.Queue queue = server2.locateQueue(retainQueueName);
         return queue != null && queue.getMessageCount() == 1;
      }, 5000, 100);

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
   public void testRegularMessageOverBridge() throws Exception {
      ActiveMQServer server1 = createServer(0, BROKER1_PORT);
      ActiveMQServer server2 = createBridgedServer();

      server1.start();
      server1.waitForActivation(10, TimeUnit.SECONDS);
      server2.start();
      server2.waitForActivation(10, TimeUnit.SECONDS);

      final String topic = "test/bridge/regular";
      final String payload = "regular-via-bridge";

      // subscribe on broker 2 to create demand
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

      // subscriber on broker 2 should receive the message via the bridge
      assertTrue(latch.await(10, TimeUnit.SECONDS), "Subscriber on broker 2 should receive the bridged message");
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
