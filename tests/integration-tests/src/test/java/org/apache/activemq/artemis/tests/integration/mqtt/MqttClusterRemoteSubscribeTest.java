/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.tests.integration.mqtt;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.MessageHandler;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.management.CoreNotificationType;
import org.apache.activemq.artemis.api.core.management.ManagementHelper;
import org.apache.activemq.artemis.core.config.CoreAddressConfiguration;
import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTProtocolManager;
import org.apache.activemq.artemis.core.remoting.impl.AbstractAcceptor;
import org.apache.activemq.artemis.core.remoting.server.impl.RemotingServiceImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.spi.core.protocol.ProtocolManager;
import org.apache.activemq.artemis.spi.core.remoting.Acceptor;
import org.apache.activemq.artemis.tests.integration.cluster.distribution.ClusterTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.fusesource.mqtt.client.BlockingConnection;
import org.fusesource.mqtt.client.MQTT;
import org.fusesource.mqtt.client.Message;
import org.fusesource.mqtt.client.QoS;
import org.fusesource.mqtt.client.Topic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MqttClusterRemoteSubscribeTest extends ClusterTestBase {

   @Override
   protected boolean isResolveProtocols() {
      return true;
   }

   public boolean isNetty() {
      return true;
   }

   @Test
   public void useSameClientIdAndAnycastSubscribeRemoteQueue() throws Exception {
      final String ANYCAST_TOPIC = "anycast/test/1/some/la";
      final String subClientId = "subClientId";
      final String pubClientId = "pubClientId";

      setupServers(ANYCAST_TOPIC);

      startServers(0, 1);

      BlockingConnection subConnection1 = null;
      BlockingConnection subConnection2 = null;
      BlockingConnection pubConnection = null;
      try {
         Thread.sleep(1000);
         Topic[] topics = {new Topic(ANYCAST_TOPIC, QoS.AT_MOST_ONCE)};
         subConnection1 = retrieveMQTTConnection("tcp://localhost:61616", subClientId);

         Wait.assertEquals(1, locateMQTTPM(servers[0]).getStateManager().getConnectedClients()::size);

         subConnection2 = retrieveMQTTConnection("tcp://localhost:61617", subClientId);
         pubConnection = retrieveMQTTConnection("tcp://localhost:61616", pubClientId);

         //Waiting for the first sub connection be closed
         assertTrue(waitConnectionClosed(subConnection1));
         Wait.assertEquals(1, locateMQTTPM(servers[1]).getStateManager().getConnectedClients()::size);
         subConnection1 = null;
         subConnection2.subscribe(topics);

         waitForBindings(0, ANYCAST_TOPIC, 1, 0, true);
         waitForBindings(1, ANYCAST_TOPIC, 1, 1, true);

         waitForBindings(0, ANYCAST_TOPIC, 1, 1, false);
         waitForBindings(1, ANYCAST_TOPIC, 1, 0, false);

         // Publish Messages
         String payload1 = "This is message 1";
         String payload2 = "This is message 2";
         String payload3 = "This is message 3";

         pubConnection.publish(ANYCAST_TOPIC, payload1.getBytes(), QoS.AT_LEAST_ONCE, false);
         pubConnection.publish(ANYCAST_TOPIC, payload2.getBytes(), QoS.AT_MOST_ONCE, false);
         pubConnection.publish(ANYCAST_TOPIC, payload3.getBytes(), QoS.AT_MOST_ONCE, false);

         Message message1 = subConnection2.receive(5, TimeUnit.SECONDS);
         message1.ack();
         Message message2 = subConnection2.receive(5, TimeUnit.SECONDS);
         message2.ack();
         Message message3 = subConnection2.receive(5, TimeUnit.SECONDS);
         message3.ack();

         assertEquals(payload1, new String(message1.getPayload()));
         assertEquals(payload2, new String(message2.getPayload()));
         assertEquals(payload3, new String(message3.getPayload()));

         subConnection2.unsubscribe(new String[]{ANYCAST_TOPIC});

         waitForBindings(0, ANYCAST_TOPIC, 1, 0, true);
         waitForBindings(1, ANYCAST_TOPIC, 1, 0, true);

         waitForBindings(0, ANYCAST_TOPIC, 1, 0, false);
         waitForBindings(1, ANYCAST_TOPIC, 1, 0, false);

         pubConnection.publish(ANYCAST_TOPIC, payload1.getBytes(), QoS.AT_LEAST_ONCE, false);
         pubConnection.publish(ANYCAST_TOPIC, payload2.getBytes(), QoS.AT_MOST_ONCE, false);
         pubConnection.publish(ANYCAST_TOPIC, payload3.getBytes(), QoS.AT_MOST_ONCE, false);

         Message message11 = subConnection2.receive(100, TimeUnit.MILLISECONDS);
         assertNull(message11);
         Message message21 = subConnection2.receive(100, TimeUnit.MILLISECONDS);
         assertNull(message21);
         Message message31 = subConnection2.receive(100, TimeUnit.MILLISECONDS);
         assertNull(message31);

      } finally {
         String[] topics = new String[]{ANYCAST_TOPIC};
         if (subConnection1 != null && subConnection1.isConnected()) {
            subConnection1.unsubscribe(topics);
            subConnection1.disconnect();
         }
         if (subConnection2 != null && subConnection2.isConnected()) {
            subConnection2.unsubscribe(topics);
            subConnection2.disconnect();
         }
         if (pubConnection != null && pubConnection.isConnected()) {
            pubConnection.disconnect();
         }
      }

   }

   @Test
   public void useDiffClientIdAndAnycastSubscribeRemoteQueue() throws Exception {
      final String ANYCAST_TOPIC = "anycast/test/1/some/la";
      final String clientId1 = "clientId1";
      final String clientId2 = "clientId2";

      setupServers(ANYCAST_TOPIC);

      startServers(0, 1);

      BlockingConnection connection1 = null;
      BlockingConnection connection2 = null;
      try {
         //Waiting for resource initialization to complete
         Thread.sleep(1000);
         Topic[] topics = {new Topic(ANYCAST_TOPIC, QoS.AT_MOST_ONCE)};
         connection1 = retrieveMQTTConnection("tcp://localhost:61616", clientId1);
         connection2 = retrieveMQTTConnection("tcp://localhost:61617", clientId2);
         // Subscribe to topics
         connection1.subscribe(topics);

         waitForBindings(0, ANYCAST_TOPIC, 1, 1, true);
         waitForBindings(1, ANYCAST_TOPIC, 1, 0, true);

         waitForBindings(0, ANYCAST_TOPIC, 1, 0, false);
         waitForBindings(1, ANYCAST_TOPIC, 1, 1, false);

         connection2.subscribe(topics);

         waitForBindings(0, ANYCAST_TOPIC, 1, 1, true);
         waitForBindings(1, ANYCAST_TOPIC, 1, 1, true);

         waitForBindings(0, ANYCAST_TOPIC, 1, 1, false);
         waitForBindings(1, ANYCAST_TOPIC, 1, 1, false);


         // Publish Messages
         String payload1 = "This is message 1";
         String payload2 = "This is message 2";
         String payload3 = "This is message 3";

         connection1.publish(ANYCAST_TOPIC, payload1.getBytes(), QoS.AT_LEAST_ONCE, false);
         connection1.publish(ANYCAST_TOPIC, payload2.getBytes(), QoS.AT_MOST_ONCE, false);
         connection1.publish(ANYCAST_TOPIC, payload3.getBytes(), QoS.AT_MOST_ONCE, false);


         Message message1 = connection1.receive(5, TimeUnit.SECONDS);
         message1.ack();
         Message message2 = connection2.receive(5, TimeUnit.SECONDS);
         message2.ack();
         Message message3 = connection1.receive(5, TimeUnit.SECONDS);
         message3.ack();

         assertEquals(payload1, new String(message1.getPayload()));
         assertEquals(payload2, new String(message2.getPayload()));
         assertEquals(payload3, new String(message3.getPayload()));

         connection2.unsubscribe(new String[]{ANYCAST_TOPIC});

         waitForBindings(0, ANYCAST_TOPIC, 1, 1, true);
         waitForBindings(1, ANYCAST_TOPIC, 1, 0, true);

         waitForBindings(0, ANYCAST_TOPIC, 1, 0, false);
         waitForBindings(1, ANYCAST_TOPIC, 1, 1, false);

         connection1.publish(ANYCAST_TOPIC, payload1.getBytes(), QoS.AT_LEAST_ONCE, false);
         connection1.publish(ANYCAST_TOPIC, payload2.getBytes(), QoS.AT_MOST_ONCE, false);
         connection1.publish(ANYCAST_TOPIC, payload3.getBytes(), QoS.AT_MOST_ONCE, false);

         Message message11 = connection1.receive(5, TimeUnit.SECONDS);
         message11.ack();
         Message message21 = connection1.receive(5, TimeUnit.SECONDS);
         message21.ack();
         Message message31 = connection1.receive(5, TimeUnit.SECONDS);
         message31.ack();

         String message11String = new String(message11.getPayload());
         String message21String = new String(message21.getPayload());
         String message31String = new String(message31.getPayload());
         assertTrue(payload1.equals(message11String) || payload1.equals(message21String) || payload1.equals(message31String));
         assertTrue(payload2.equals(message11String) || payload2.equals(message21String) || payload2.equals(message31String));
         assertTrue(payload3.equals(message11String) || payload3.equals(message21String) || payload3.equals(message31String));

      } finally {
         String[] topics = new String[]{ANYCAST_TOPIC};
         if (connection1 != null && connection1.isConnected()) {
            connection1.unsubscribe(topics);
            connection1.disconnect();
         }
         if (connection2 != null && connection2.isConnected()) {
            connection2.unsubscribe(topics);
            connection2.disconnect();
         }
      }

   }

   @Test
   public void useSameClientIdAndMulticastSubscribeRemoteQueue() throws Exception {
      final String MULTICAST_TOPIC = "multicast/test/1/some/la";
      final String ANYCAST_TOPIC = "anycast/test/1/some/la";
      final String subClientId = "subClientId";
      final String pubClientId = "pubClientId";
      final String notifications = "notifications";

      setupServers(ANYCAST_TOPIC);

      startServers(0, 1);

      ServerLocator locator = addServerLocator(ActiveMQClient.createServerLocator("tcp://localhost:61617"));
      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession session = addClientSession(sf.createSession(false, true, true));
      servers[1].createQueue(QueueConfiguration.of(notifications).setAddress(servers[1].getConfiguration().getManagementNotificationAddress()).setRoutingType(RoutingType.MULTICAST));
      ClientConsumer consumer = session.createConsumer(notifications);
      AtomicInteger notificationCount = new AtomicInteger(0);
      consumer.setMessageHandler(new MessageHandler() {
         @Override
         public void onMessage(ClientMessage message) {
            CoreNotificationType type = CoreNotificationType.valueOf(message.getSimpleStringProperty(ManagementHelper.HDR_NOTIFICATION_TYPE).toString());
            SimpleString protocol = message.getSimpleStringProperty(ManagementHelper.HDR_PROTOCOL_NAME);
            if (type == CoreNotificationType.SESSION_CREATED && protocol.equals(SimpleString.of("MQTT"))) {
               notificationCount.incrementAndGet();
            }
         }
      });
      session.start();

      BlockingConnection subConnection1 = null;
      BlockingConnection subConnection2 = null;
      BlockingConnection pubConnection = null;
      try {
         //Waiting for resource initialization to complete
         Thread.sleep(1000);
         Topic[] topics = {new Topic(MULTICAST_TOPIC, QoS.AT_MOST_ONCE)};
         subConnection1 = retrieveMQTTConnection("tcp://localhost:61616", subClientId);

         Wait.assertEquals(1, locateMQTTPM(servers[0]).getStateManager().getConnectedClients()::size);
         Wait.assertEquals(1, () -> notificationCount.get());

         subConnection2 = retrieveMQTTConnection("tcp://localhost:61617", subClientId);
         pubConnection = retrieveMQTTConnection("tcp://localhost:61616", pubClientId);

         //Waiting for the first sub connection be closed
         assertTrue(waitConnectionClosed(subConnection1));
         Wait.assertEquals(1, locateMQTTPM(servers[1]).getStateManager().getConnectedClients()::size);
         subConnection1 = null;


         subConnection2.subscribe(topics);

         waitForBindings(0, MULTICAST_TOPIC, 0, 0, true);
         waitForBindings(1, MULTICAST_TOPIC, 1, 1, true);

         waitForBindings(0, MULTICAST_TOPIC, 1, 1, false);
         waitForBindings(1, MULTICAST_TOPIC, 0, 0, false);

         // Publish Messages
         String payload1 = "This is message 1";
         String payload2 = "This is message 2";
         String payload3 = "This is message 3";

         pubConnection.publish(MULTICAST_TOPIC, payload1.getBytes(), QoS.AT_LEAST_ONCE, false);
         pubConnection.publish(MULTICAST_TOPIC, payload2.getBytes(), QoS.AT_MOST_ONCE, false);
         pubConnection.publish(MULTICAST_TOPIC, payload3.getBytes(), QoS.AT_MOST_ONCE, false);

         Message message1 = subConnection2.receive(5, TimeUnit.SECONDS);
         message1.ack();
         Message message2 = subConnection2.receive(5, TimeUnit.SECONDS);
         message2.ack();
         Message message3 = subConnection2.receive(5, TimeUnit.SECONDS);
         message3.ack();

         assertEquals(payload1, new String(message1.getPayload()));
         assertEquals(payload2, new String(message2.getPayload()));
         assertEquals(payload3, new String(message3.getPayload()));

         subConnection2.unsubscribe(new String[]{MULTICAST_TOPIC});

         waitForBindings(0, MULTICAST_TOPIC, 0, 0, true);
         waitForBindings(1, MULTICAST_TOPIC, 0, 0, true);

         waitForBindings(0, MULTICAST_TOPIC, 0, 0, false);
         waitForBindings(1, MULTICAST_TOPIC, 0, 0, false);

         pubConnection.publish(MULTICAST_TOPIC, payload1.getBytes(), QoS.AT_LEAST_ONCE, false);
         pubConnection.publish(MULTICAST_TOPIC, payload2.getBytes(), QoS.AT_MOST_ONCE, false);
         pubConnection.publish(MULTICAST_TOPIC, payload3.getBytes(), QoS.AT_MOST_ONCE, false);

         Message message11 = subConnection2.receive(100, TimeUnit.MILLISECONDS);
         assertNull(message11);
         Message message21 = subConnection2.receive(100, TimeUnit.MILLISECONDS);
         assertNull(message21);
         Message message31 = subConnection2.receive(100, TimeUnit.MILLISECONDS);
         assertNull(message31);

      } finally {
         String[] topics = new String[]{MULTICAST_TOPIC};
         if (subConnection1 != null && subConnection1.isConnected()) {
            subConnection1.unsubscribe(topics);
            subConnection1.disconnect();
         }
         if (subConnection2 != null && subConnection2.isConnected()) {
            subConnection2.unsubscribe(topics);
            subConnection2.disconnect();
         }
         if (pubConnection != null && pubConnection.isConnected()) {
            pubConnection.disconnect();
         }
      }

   }

   @Test
   public void useDiffClientIdAndMulticastSubscribeRemoteQueue() throws Exception {
      final String MULTICAST_TOPIC = "multicast/test/1/some/la";
      final String ANYCAST_TOPIC = "anycast/test/1/some/la";
      final String clientId1 = "clientId1";
      final String clientId2 = "clientId2";

      setupServers(ANYCAST_TOPIC);

      startServers(0, 1);

      BlockingConnection connection1 = null;
      BlockingConnection connection2 = null;
      try {
         //Waiting for resource initialization to complete
         Thread.sleep(1000);
         Topic[] topics = {new Topic(MULTICAST_TOPIC, QoS.AT_MOST_ONCE)};
         connection1 = retrieveMQTTConnection("tcp://localhost:61616", clientId1);
         connection2 = retrieveMQTTConnection("tcp://localhost:61617", clientId2);
         // Subscribe to topics
         connection1.subscribe(topics);

         waitForBindings(0, MULTICAST_TOPIC, 1, 1, true);
         waitForBindings(1, MULTICAST_TOPIC, 0, 0, true);

         waitForBindings(0, MULTICAST_TOPIC, 0, 0, false);
         waitForBindings(1, MULTICAST_TOPIC, 1, 1, false);

         connection2.subscribe(topics);

         waitForBindings(0, MULTICAST_TOPIC, 1, 1, true);
         waitForBindings(1, MULTICAST_TOPIC, 1, 1, true);

         waitForBindings(0, MULTICAST_TOPIC, 1, 1, false);
         waitForBindings(1, MULTICAST_TOPIC, 1, 1, false);

         // Publish Messages
         String payload1 = "This is message 1";
         String payload2 = "This is message 2";
         String payload3 = "This is message 3";

         connection1.publish(MULTICAST_TOPIC, payload1.getBytes(), QoS.AT_LEAST_ONCE, false);
         connection1.publish(MULTICAST_TOPIC, payload2.getBytes(), QoS.AT_MOST_ONCE, false);
         connection1.publish(MULTICAST_TOPIC, payload3.getBytes(), QoS.AT_MOST_ONCE, false);

         Message message11 = connection1.receive(5, TimeUnit.SECONDS);
         message11.ack();
         Message message12 = connection1.receive(5, TimeUnit.SECONDS);
         message12.ack();
         Message message13 = connection1.receive(5, TimeUnit.SECONDS);
         message13.ack();

         assertEquals(payload1, new String(message11.getPayload()));
         assertEquals(payload2, new String(message12.getPayload()));
         assertEquals(payload3, new String(message13.getPayload()));

         Message message21 = connection2.receive(5, TimeUnit.SECONDS);
         message21.ack();
         Message message22 = connection2.receive(5, TimeUnit.SECONDS);
         message22.ack();
         Message message23 = connection2.receive(5, TimeUnit.SECONDS);
         message23.ack();

         assertEquals(payload1, new String(message21.getPayload()));
         assertEquals(payload2, new String(message22.getPayload()));
         assertEquals(payload3, new String(message23.getPayload()));

         connection2.unsubscribe(new String[]{MULTICAST_TOPIC});

         waitForBindings(0, MULTICAST_TOPIC, 1, 1, true);
         waitForBindings(1, MULTICAST_TOPIC, 0, 0, true);

         waitForBindings(0, MULTICAST_TOPIC, 0, 0, false);
         waitForBindings(1, MULTICAST_TOPIC, 1, 1, false);

         connection1.publish(MULTICAST_TOPIC, payload1.getBytes(), QoS.AT_LEAST_ONCE, false);
         connection1.publish(MULTICAST_TOPIC, payload2.getBytes(), QoS.AT_MOST_ONCE, false);
         connection1.publish(MULTICAST_TOPIC, payload3.getBytes(), QoS.AT_MOST_ONCE, false);

         Message message31 = connection1.receive(5, TimeUnit.SECONDS);
         message31.ack();
         Message message32 = connection1.receive(5, TimeUnit.SECONDS);
         message32.ack();
         Message message33 = connection1.receive(5, TimeUnit.SECONDS);
         message33.ack();

         assertEquals(payload1, new String(message31.getPayload()));
         assertEquals(payload2, new String(message32.getPayload()));
         assertEquals(payload3, new String(message33.getPayload()));

      } finally {
         String[] topics = new String[]{MULTICAST_TOPIC};
         if (connection1 != null && connection1.isConnected()) {
            connection1.unsubscribe(topics);
            connection1.disconnect();
         }
         if (connection2 != null && connection2.isConnected()) {
            connection2.unsubscribe(topics);
            connection2.disconnect();
         }
      }

   }

   @Test
   public void useSameClientIdAndAnycastSubscribeRemoteQueueWildCard() throws Exception {
      final String ANYCAST_TOPIC = "anycast/test/+/some/#";
      final String subClientId = "subClientId";
      final String pubClientId = "pubClientId";

      setupServers(ANYCAST_TOPIC);

      startServers(0, 1);

      BlockingConnection subConnection1 = null;
      BlockingConnection subConnection2 = null;
      BlockingConnection pubConnection = null;
      try {
         //Waiting for resource initialization to complete
         Thread.sleep(1000);
         Topic[] topics = {new Topic(ANYCAST_TOPIC, QoS.AT_MOST_ONCE)};
         subConnection1 = retrieveMQTTConnection("tcp://localhost:61616", subClientId);

         Wait.assertEquals(1, locateMQTTPM(servers[0]).getStateManager().getConnectedClients()::size);

         subConnection2 = retrieveMQTTConnection("tcp://localhost:61617", subClientId);
         pubConnection = retrieveMQTTConnection("tcp://localhost:61616", pubClientId);

         //Waiting for the first sub connection be closed
         assertTrue(waitConnectionClosed(subConnection1));
         Wait.assertEquals(1, locateMQTTPM(servers[1]).getStateManager().getConnectedClients()::size);
         subConnection1 = null;

         subConnection2.subscribe(topics);

         waitForBindings(0, ANYCAST_TOPIC, 1, 0, true);
         waitForBindings(1, ANYCAST_TOPIC, 1, 1, true);

         waitForBindings(0, ANYCAST_TOPIC, 1, 1, false);
         waitForBindings(1, ANYCAST_TOPIC, 1, 0, false);

         // Publish Messages
         String payload1 = "This is message 1";
         String payload2 = "This is message 2";
         String payload3 = "This is message 3";

         pubConnection.publish("anycast/test/1/some/la", payload1.getBytes(), QoS.AT_LEAST_ONCE, false);
         pubConnection.publish("anycast/test/1/some/la", payload2.getBytes(), QoS.AT_MOST_ONCE, false);
         pubConnection.publish("anycast/test/1/some/la", payload3.getBytes(), QoS.AT_MOST_ONCE, false);

         // pub queue gets auto created, the routing type set on the message to reflect that and the
         // message does not get routed to the sub queue that has anycast
         subConnection2.unsubscribe(new String[]{ANYCAST_TOPIC});

         waitForBindings(0, ANYCAST_TOPIC, 1, 0, true);
         waitForBindings(1, ANYCAST_TOPIC, 1, 0, true);

         waitForBindings(0, ANYCAST_TOPIC, 1, 0, false);
         waitForBindings(1, ANYCAST_TOPIC, 1, 0, false);

         pubConnection.publish("anycast/test/1/some/la", payload1.getBytes(), QoS.AT_LEAST_ONCE, false);
         pubConnection.publish("anycast/test/1/some/la", payload2.getBytes(), QoS.AT_MOST_ONCE, false);
         pubConnection.publish("anycast/test/1/some/la", payload3.getBytes(), QoS.AT_MOST_ONCE, false);

      } finally {
         String[] topics = new String[]{ANYCAST_TOPIC};
         if (subConnection1 != null && subConnection1.isConnected()) {
            subConnection1.unsubscribe(topics);
            subConnection1.disconnect();
         }
         if (subConnection2 != null && subConnection2.isConnected()) {
            subConnection2.unsubscribe(topics);
            subConnection2.disconnect();
         }
         if (pubConnection != null && pubConnection.isConnected()) {
            pubConnection.disconnect();
         }
      }

   }

   @Test
   public void useDiffClientIdAndAnycastSubscribeRemoteQueueWildCard() throws Exception {
      final String ANYCAST_TOPIC = "anycast/test/+/some/#";
      final String clientId1 = "clientId1";
      final String clientId2 = "clientId2";

      setupServers(ANYCAST_TOPIC);

      startServers(0, 1);

      BlockingConnection connection1 = null;
      BlockingConnection connection2 = null;
      try {
         //Waiting for resource initialization to complete
         Thread.sleep(1000);
         Topic[] topics = {new Topic(ANYCAST_TOPIC, QoS.AT_MOST_ONCE)};
         connection1 = retrieveMQTTConnection("tcp://localhost:61616", clientId1);
         connection2 = retrieveMQTTConnection("tcp://localhost:61617", clientId2);
         // Subscribe to topics
         connection1.subscribe(topics);

         waitForBindings(0, ANYCAST_TOPIC, 1, 1, true);
         waitForBindings(1, ANYCAST_TOPIC, 1, 0, true);

         waitForBindings(0, ANYCAST_TOPIC, 1, 0, false);
         waitForBindings(1, ANYCAST_TOPIC, 1, 1, false);

         connection2.subscribe(topics);

         waitForBindings(0, ANYCAST_TOPIC, 1, 1, true);
         waitForBindings(1, ANYCAST_TOPIC, 1, 1, true);

         waitForBindings(0, ANYCAST_TOPIC, 1, 1, false);
         waitForBindings(1, ANYCAST_TOPIC, 1, 1, false);


         // Publish Messages
         String payload1 = "This is message 1";
         String payload2 = "This is message 2";
         String payload3 = "This is message 3";

         connection1.publish("anycast/test/1/some/la", payload1.getBytes(), QoS.AT_LEAST_ONCE, false);
         connection1.publish("anycast/test/1/some/la", payload2.getBytes(), QoS.AT_MOST_ONCE, false);
         connection1.publish("anycast/test/1/some/la", payload3.getBytes(), QoS.AT_MOST_ONCE, false);


         // the pub queue is auto created and the message multicast routing type won't match the anycast sub queue
         // so nothing gets routed to this queue

         connection2.unsubscribe(new String[]{ANYCAST_TOPIC});

         waitForBindings(0, ANYCAST_TOPIC, 1, 1, true);
         waitForBindings(1, ANYCAST_TOPIC, 1, 0, true);

         waitForBindings(0, ANYCAST_TOPIC, 1, 0, false);
         waitForBindings(1, ANYCAST_TOPIC, 1, 1, false);

         connection1.publish("anycast/test/1/some/la", payload1.getBytes(), QoS.AT_LEAST_ONCE, false);
         connection1.publish("anycast/test/1/some/la", payload2.getBytes(), QoS.AT_MOST_ONCE, false);
         connection1.publish("anycast/test/1/some/la", payload3.getBytes(), QoS.AT_MOST_ONCE, false);

      } finally {
         String[] topics = new String[]{ANYCAST_TOPIC};
         if (connection1 != null && connection1.isConnected()) {
            connection1.unsubscribe(topics);
            connection1.disconnect();
         }
         if (connection2 != null && connection2.isConnected()) {
            connection2.unsubscribe(topics);
            connection2.disconnect();
         }
      }

   }

   MQTTProtocolManager locateMQTTPM(ActiveMQServer server) {

      RemotingServiceImpl impl = (RemotingServiceImpl) server.getRemotingService();
      for (Acceptor acceptor : impl.getAcceptors().values()) {
         AbstractAcceptor abstractAcceptor = (AbstractAcceptor) acceptor;
         for (ProtocolManager manager : abstractAcceptor.getProtocolMap().values()) {
            if (manager instanceof MQTTProtocolManager mqttProtocolManager) {
               return mqttProtocolManager;
            }
         }
      }
      return null;
   }

   @Test
   public void useSameClientIdAndMulticastSubscribeRemoteQueueWildCard() throws Exception {
      final String MULTICAST_TOPIC = "multicast/test/+/some/#";
      final String ANYCAST_TOPIC = "anycast/test/+/some/#";
      final String subClientId = "subClientId";
      final String pubClientId = "pubClientId";

      setupServers(ANYCAST_TOPIC);

      startServers(0, 1);

      BlockingConnection subConnection1 = null;
      BlockingConnection subConnection2 = null;
      BlockingConnection pubConnection = null;
      try {
         //Waiting for resource initialization to complete
         Thread.sleep(1000);
         Topic[] topics = {new Topic(MULTICAST_TOPIC, QoS.AT_MOST_ONCE)};
         subConnection1 = retrieveMQTTConnection("tcp://localhost:61616", subClientId);
         Wait.assertEquals(1, locateMQTTPM(servers[0]).getStateManager().getConnectedClients()::size);
         subConnection2 = retrieveMQTTConnection("tcp://localhost:61617", subClientId);
         pubConnection = retrieveMQTTConnection("tcp://localhost:61616", pubClientId);

         //Waiting for the first sub connection be closed
         assertTrue(waitConnectionClosed(subConnection1));

         subConnection2.subscribe(topics);

         waitForBindings(0, MULTICAST_TOPIC, 0, 0, true);
         waitForBindings(1, MULTICAST_TOPIC, 1, 1, true);

         waitForBindings(0, MULTICAST_TOPIC, 1, 1, false);
         waitForBindings(1, MULTICAST_TOPIC, 0, 0, false);

         // Publish Messages
         String payload1 = "This is message 1";
         String payload2 = "This is message 2";
         String payload3 = "This is message 3";

         pubConnection.publish("multicast/test/1/some/la", payload1.getBytes(), QoS.AT_LEAST_ONCE, false);
         pubConnection.publish("multicast/test/1/some/la", payload2.getBytes(), QoS.AT_MOST_ONCE, false);
         pubConnection.publish("multicast/test/1/some/la", payload3.getBytes(), QoS.AT_MOST_ONCE, false);

         Message message1 = subConnection2.receive(5, TimeUnit.SECONDS);
         message1.ack();
         Message message2 = subConnection2.receive(5, TimeUnit.SECONDS);
         message2.ack();
         Message message3 = subConnection2.receive(5, TimeUnit.SECONDS);
         message3.ack();

         assertEquals(payload1, new String(message1.getPayload()));
         assertEquals(payload2, new String(message2.getPayload()));
         assertEquals(payload3, new String(message3.getPayload()));

         subConnection2.unsubscribe(new String[]{MULTICAST_TOPIC});

         waitForBindings(0, MULTICAST_TOPIC, 0, 0, true);
         waitForBindings(1, MULTICAST_TOPIC, 0, 0, true);

         waitForBindings(0, MULTICAST_TOPIC, 0, 0, false);
         waitForBindings(1, MULTICAST_TOPIC, 0, 0, false);

         pubConnection.publish("multicast/test/1/some/la", payload1.getBytes(), QoS.AT_LEAST_ONCE, false);
         pubConnection.publish("multicast/test/1/some/la", payload2.getBytes(), QoS.AT_MOST_ONCE, false);
         pubConnection.publish("multicast/test/1/some/la", payload3.getBytes(), QoS.AT_MOST_ONCE, false);

         Message message11 = subConnection2.receive(100, TimeUnit.MILLISECONDS);
         assertNull(message11);
         Message message21 = subConnection2.receive(100, TimeUnit.MILLISECONDS);
         assertNull(message21);
         Message message31 = subConnection2.receive(100, TimeUnit.MILLISECONDS);
         assertNull(message31);

      } finally {
         String[] topics = new String[]{MULTICAST_TOPIC};
         if (subConnection1 != null && subConnection1.isConnected()) {
            subConnection1.unsubscribe(topics);
            subConnection1.disconnect();
         }
         if (subConnection2 != null && subConnection2.isConnected()) {
            subConnection2.unsubscribe(topics);
            subConnection2.disconnect();
         }
         if (pubConnection != null && pubConnection.isConnected()) {
            pubConnection.disconnect();
         }
      }

   }

   @Test
   public void useDiffClientIdAndMulticastSubscribeRemoteQueueWildCard() throws Exception {
      final String MULTICAST_TOPIC = "multicast/test/+/some/#";
      final String ANYCAST_TOPIC = "anycast/test/+/some/#";
      final String clientId1 = "clientId1";
      final String clientId2 = "clientId2";

      setupServers(ANYCAST_TOPIC);

      startServers(0, 1);

      BlockingConnection connection1 = null;
      BlockingConnection connection2 = null;
      try {
         //Waiting for resource initialization to complete
         Thread.sleep(1000);
         Topic[] topics = {new Topic(MULTICAST_TOPIC, QoS.AT_MOST_ONCE)};
         connection1 = retrieveMQTTConnection("tcp://localhost:61616", clientId1);
         Wait.assertEquals(1, locateMQTTPM(servers[0]).getStateManager().getConnectedClients()::size);
         connection2 = retrieveMQTTConnection("tcp://localhost:61617", clientId2);
         Wait.assertEquals(1, locateMQTTPM(servers[1]).getStateManager().getConnectedClients()::size);
         // Subscribe to topics
         connection1.subscribe(topics);

         waitForBindings(0, MULTICAST_TOPIC, 1, 1, true);
         waitForBindings(1, MULTICAST_TOPIC, 0, 0, true);

         waitForBindings(0, MULTICAST_TOPIC, 0, 0, false);
         waitForBindings(1, MULTICAST_TOPIC, 1, 1, false);

         connection2.subscribe(topics);

         waitForBindings(0, MULTICAST_TOPIC, 1, 1, true);
         waitForBindings(1, MULTICAST_TOPIC, 1, 1, true);

         waitForBindings(0, MULTICAST_TOPIC, 1, 1, false);
         waitForBindings(1, MULTICAST_TOPIC, 1, 1, false);

         // Publish Messages
         String payload1 = "This is message 1";
         String payload2 = "This is message 2";
         String payload3 = "This is message 3";

         connection1.publish("multicast/test/1/some/la", payload1.getBytes(), QoS.AT_LEAST_ONCE, false);
         connection1.publish("multicast/test/1/some/la", payload2.getBytes(), QoS.AT_MOST_ONCE, false);
         connection1.publish("multicast/test/1/some/la", payload3.getBytes(), QoS.AT_MOST_ONCE, false);

         Message message11 = connection1.receive(5, TimeUnit.SECONDS);
         message11.ack();
         Message message12 = connection1.receive(5, TimeUnit.SECONDS);
         message12.ack();
         Message message13 = connection1.receive(5, TimeUnit.SECONDS);
         message13.ack();

         assertEquals(payload1, new String(message11.getPayload()));
         assertEquals(payload2, new String(message12.getPayload()));
         assertEquals(payload3, new String(message13.getPayload()));

         Message message21 = connection2.receive(5, TimeUnit.SECONDS);
         message21.ack();
         Message message22 = connection2.receive(5, TimeUnit.SECONDS);
         message22.ack();
         Message message23 = connection2.receive(5, TimeUnit.SECONDS);
         message23.ack();

         assertEquals(payload1, new String(message21.getPayload()));
         assertEquals(payload2, new String(message22.getPayload()));
         assertEquals(payload3, new String(message23.getPayload()));

         connection2.unsubscribe(new String[]{MULTICAST_TOPIC});

         waitForBindings(0, MULTICAST_TOPIC, 1, 1, true);
         waitForBindings(1, MULTICAST_TOPIC, 0, 0, true);

         waitForBindings(0, MULTICAST_TOPIC, 0, 0, false);
         waitForBindings(1, MULTICAST_TOPIC, 1, 1, false);

         connection1.publish("multicast/test/1/some/la", payload1.getBytes(), QoS.AT_LEAST_ONCE, false);
         connection1.publish("multicast/test/1/some/la", payload2.getBytes(), QoS.AT_MOST_ONCE, false);
         connection1.publish("multicast/test/1/some/la", payload3.getBytes(), QoS.AT_MOST_ONCE, false);

         Message message31 = connection1.receive(5, TimeUnit.SECONDS);
         message31.ack();
         Message message32 = connection1.receive(5, TimeUnit.SECONDS);
         message32.ack();
         Message message33 = connection1.receive(5, TimeUnit.SECONDS);
         message33.ack();

         assertEquals(payload1, new String(message31.getPayload()));
         assertEquals(payload2, new String(message32.getPayload()));
         assertEquals(payload3, new String(message33.getPayload()));


      } finally {
         String[] topics = new String[]{MULTICAST_TOPIC};
         if (connection1 != null && connection1.isConnected()) {
            connection1.unsubscribe(topics);
            connection1.disconnect();
         }
         if (connection2 != null && connection2.isConnected()) {
            connection2.unsubscribe(topics);
            connection2.disconnect();
         }
      }

   }

   @Test
   public void useDiffClientIdSubscribeRemoteQueueMultipleSubscriptions() throws Exception {
      final String ANYCAST_TOPIC = "anycast/test/1/some/la";
      final String TOPIC2 = "sample";
      final String clientId1 = "clientId1";
      final String clientId2 = "clientId2";

      setupServers(ANYCAST_TOPIC);

      startServers(0, 1);

      BlockingConnection connection1 = null;
      BlockingConnection connection2 = null;
      try {
         //Waiting for resource initialization to complete
         Thread.sleep(1000);
         connection1 = retrieveMQTTConnection("tcp://localhost:61616", clientId1);
         connection2 = retrieveMQTTConnection("tcp://localhost:61617", clientId2);
         // Subscribe to topics
         connection1.subscribe(new Topic[]{new Topic(ANYCAST_TOPIC, QoS.AT_MOST_ONCE)});

         waitForBindings(0, ANYCAST_TOPIC, 1, 1, true);
         waitForBindings(1, ANYCAST_TOPIC, 1, 0, true);

         waitForBindings(0, ANYCAST_TOPIC, 1, 0, false);
         waitForBindings(1, ANYCAST_TOPIC, 1, 1, false);

         connection2.subscribe(new Topic[]{new Topic(ANYCAST_TOPIC, QoS.AT_MOST_ONCE), new Topic(TOPIC2, QoS.AT_MOST_ONCE)});

         waitForBindings(0, ANYCAST_TOPIC, 1, 1, true);
         waitForBindings(1, ANYCAST_TOPIC, 1, 1, true);

         waitForBindings(0, ANYCAST_TOPIC, 1, 1, false);
         waitForBindings(1, ANYCAST_TOPIC, 1, 1, false);

         // Publish Messages
         String payload1 = "This is message 1";
         String payload2 = "This is message 2";
         String payload3 = "This is message 3";
         String payload4 = "This is message 4";

         connection1.publish(ANYCAST_TOPIC, payload1.getBytes(), QoS.AT_LEAST_ONCE, false);
         connection1.publish(ANYCAST_TOPIC, payload2.getBytes(), QoS.AT_MOST_ONCE, false);
         connection1.publish(ANYCAST_TOPIC, payload3.getBytes(), QoS.AT_MOST_ONCE, false);
         connection1.publish(TOPIC2, payload4.getBytes(), QoS.AT_MOST_ONCE, false);


         Message message1 = connection1.receive(5, TimeUnit.SECONDS);
         message1.ack();
         Message message2 = connection2.receive(5, TimeUnit.SECONDS);
         message2.ack();
         Message message3 = connection1.receive(5, TimeUnit.SECONDS);
         message3.ack();
         Message message4 = connection2.receive(5, TimeUnit.SECONDS);
         message4.ack();

         assertEquals(payload1, new String(message1.getPayload()));
         assertEquals(payload2, new String(message2.getPayload()));
         assertEquals(payload3, new String(message3.getPayload()));
         assertEquals(payload4, new String(message4.getPayload()));

         connection2.unsubscribe(new String[]{ANYCAST_TOPIC});

         waitForBindings(0, ANYCAST_TOPIC, 1, 1, true);
         waitForBindings(1, ANYCAST_TOPIC, 1, 0, true);

         waitForBindings(0, ANYCAST_TOPIC, 1, 0, false);
         waitForBindings(1, ANYCAST_TOPIC, 1, 1, false);

         connection1.publish(ANYCAST_TOPIC, payload1.getBytes(), QoS.AT_LEAST_ONCE, false);
         connection1.publish(ANYCAST_TOPIC, payload2.getBytes(), QoS.AT_MOST_ONCE, false);
         connection1.publish(ANYCAST_TOPIC, payload3.getBytes(), QoS.AT_MOST_ONCE, false);
         connection1.publish(TOPIC2, payload4.getBytes(), QoS.AT_MOST_ONCE, false);

         Message message11 = connection1.receive(5, TimeUnit.SECONDS);
         message11.ack();
         Message message21 = connection1.receive(5, TimeUnit.SECONDS);
         message21.ack();
         Message message31 = connection1.receive(5, TimeUnit.SECONDS);
         message31.ack();
         Message message41 = connection2.receive(5, TimeUnit.SECONDS);
         message41.ack();

         String message11String = new String(message31.getPayload());
         String message21String = new String(message21.getPayload());
         String message31String = new String(message11.getPayload());
         assertTrue(payload1.equals(message11String) || payload1.equals(message21String) || payload1.equals(message31String));
         assertTrue(payload2.equals(message11String) || payload2.equals(message21String) || payload2.equals(message31String));
         assertTrue(payload3.equals(message11String) || payload3.equals(message21String) || payload3.equals(message31String));
         assertEquals(payload4, new String(message41.getPayload()));


      } finally {
         if (connection1 != null && connection1.isConnected()) {
            connection1.unsubscribe(new String[]{ANYCAST_TOPIC});
            connection1.disconnect();
         }
         if (connection2 != null && connection2.isConnected()) {
            connection2.unsubscribe(new String[]{ANYCAST_TOPIC, TOPIC2});
            connection2.disconnect();
         }
      }

   }

   @Test
   public void useSameClientIdSubscribeRemoteQueueMultipleSubscriptions() throws Exception {
      final String ANYCAST_TOPIC = "anycast/test/1/some/la";
      final String TOPIC2 = "sample";
      final String subClientId = "subClientId";
      final String pubClientId = "pubClientId";

      setupServers(ANYCAST_TOPIC);

      startServers(0, 1);

      BlockingConnection subConnection1 = null;
      BlockingConnection subConnection2 = null;
      BlockingConnection pubConnection = null;
      try {
         //Waiting for resource initialization to complete
         Thread.sleep(1000);
         subConnection1 = retrieveMQTTConnection("tcp://localhost:61616", subClientId);

         Wait.assertEquals(1, locateMQTTPM(servers[0]).getStateManager().getConnectedClients()::size);

         subConnection2 = retrieveMQTTConnection("tcp://localhost:61617", subClientId);
         pubConnection = retrieveMQTTConnection("tcp://localhost:61616", pubClientId);

         //Waiting for the first sub connection be closed
         assertTrue(waitConnectionClosed(subConnection1));

         subConnection2.subscribe(new Topic[]{new Topic(ANYCAST_TOPIC, QoS.AT_MOST_ONCE), new Topic(TOPIC2, QoS.AT_MOST_ONCE)});

         waitForBindings(0, ANYCAST_TOPIC, 1, 0, true);
         waitForBindings(1, ANYCAST_TOPIC, 1, 1, true);

         waitForBindings(0, ANYCAST_TOPIC, 1, 1, false);
         waitForBindings(1, ANYCAST_TOPIC, 1, 0, false);

         // Publish Messages
         String payload1 = "This is message 1";
         String payload2 = "This is message 2";
         String payload3 = "This is message 3";
         String payload4 = "This is message 4";

         pubConnection.publish(ANYCAST_TOPIC, payload1.getBytes(), QoS.AT_LEAST_ONCE, false);
         pubConnection.publish(ANYCAST_TOPIC, payload2.getBytes(), QoS.AT_MOST_ONCE, false);
         pubConnection.publish(ANYCAST_TOPIC, payload3.getBytes(), QoS.AT_MOST_ONCE, false);
         pubConnection.publish(TOPIC2, payload4.getBytes(), QoS.AT_MOST_ONCE, false);

         Message message1 = subConnection2.receive(5, TimeUnit.SECONDS);
         message1.ack();
         Message message2 = subConnection2.receive(5, TimeUnit.SECONDS);
         message2.ack();
         Message message3 = subConnection2.receive(5, TimeUnit.SECONDS);
         message3.ack();
         Message message4 = subConnection2.receive(5, TimeUnit.SECONDS);
         message4.ack();

         String messageStr1 = new String(message1.getPayload());
         String messageStr2 = new String(message2.getPayload());
         String messageStr3 = new String(message3.getPayload());
         String messageStr4 = new String(message4.getPayload());
         assertTrue(payload1.equals(messageStr1) || payload1.equals(messageStr2) || payload1.equals(messageStr3) || payload1.equals(messageStr4));
         assertTrue(payload2.equals(messageStr1) || payload2.equals(messageStr2) || payload2.equals(messageStr3) || payload2.equals(messageStr4));
         assertTrue(payload3.equals(messageStr1) || payload3.equals(messageStr2) || payload3.equals(messageStr3) || payload3.equals(messageStr4));
         assertTrue(payload4.equals(messageStr1) || payload4.equals(messageStr2) || payload4.equals(messageStr3) || payload4.equals(messageStr4));

         subConnection2.unsubscribe(new String[]{ANYCAST_TOPIC});

         waitForBindings(0, ANYCAST_TOPIC, 1, 0, true);
         waitForBindings(1, ANYCAST_TOPIC, 1, 0, true);

         waitForBindings(0, ANYCAST_TOPIC, 1, 0, false);
         waitForBindings(1, ANYCAST_TOPIC, 1, 0, false);

         pubConnection.publish(ANYCAST_TOPIC, payload1.getBytes(), QoS.AT_LEAST_ONCE, false);
         pubConnection.publish(ANYCAST_TOPIC, payload2.getBytes(), QoS.AT_MOST_ONCE, false);
         pubConnection.publish(ANYCAST_TOPIC, payload3.getBytes(), QoS.AT_MOST_ONCE, false);
         pubConnection.publish(TOPIC2, payload4.getBytes(), QoS.AT_MOST_ONCE, false);

         Message message11 = subConnection2.receive(5, TimeUnit.SECONDS);
         message11.ack();
         assertEquals(payload4, new String(message11.getPayload()));

         Message message21 = subConnection2.receive(100, TimeUnit.MILLISECONDS);
         assertNull(message21);
         Message message31 = subConnection2.receive(100, TimeUnit.MILLISECONDS);
         assertNull(message31);
         Message message41 = subConnection2.receive(100, TimeUnit.MILLISECONDS);
         assertNull(message41);

      } finally {
         if (subConnection1 != null && subConnection1.isConnected()) {
            subConnection1.unsubscribe(new String[]{ANYCAST_TOPIC});
            subConnection1.disconnect();
         }
         if (subConnection2 != null && subConnection2.isConnected()) {
            subConnection2.unsubscribe(new String[]{ANYCAST_TOPIC, TOPIC2});
            subConnection2.disconnect();
         }
         if (pubConnection != null && pubConnection.isConnected()) {
            pubConnection.disconnect();
         }
      }

   }

   @Test
   public void useSameClientIdSubscribeExistingQueue() throws Exception {
      final String ANYCAST_TOPIC = "anycast/test/1/some/la";
      final String subClientId = "subClientId";
      final String pubClientId = "pubClientId";

      setupServers(ANYCAST_TOPIC);

      startServers(0, 1);

      BlockingConnection subConnection1 = null;
      BlockingConnection subConnection2 = null;
      BlockingConnection subConnection3 = null;
      BlockingConnection pubConnection = null;
      try {
         //Waiting for resource initialization to complete
         Thread.sleep(1000);
         pubConnection = retrieveMQTTConnection("tcp://localhost:61616", pubClientId);

         subConnection1 = retrieveMQTTConnection("tcp://localhost:61616", subClientId);
         Wait.assertEquals(2, locateMQTTPM(servers[0]).getStateManager().getConnectedClients()::size);
         subConnection2 = retrieveMQTTConnection("tcp://localhost:61617", subClientId);

         //Waiting for the first sub connection be closed
         assertTrue(waitConnectionClosed(subConnection1));

         subConnection3 = retrieveMQTTConnection("tcp://localhost:61617", subClientId);

         //Waiting for the second sub connection be closed
         assertTrue(waitConnectionClosed(subConnection1));

         // Subscribe to topics
         Topic[] topics = {new Topic(ANYCAST_TOPIC, QoS.AT_MOST_ONCE)};

         subConnection3.subscribe(topics);

         waitForBindings(0, ANYCAST_TOPIC, 1, 0, true);
         waitForBindings(1, ANYCAST_TOPIC, 1, 1, true);

         waitForBindings(0, ANYCAST_TOPIC, 1, 1, false);
         waitForBindings(1, ANYCAST_TOPIC, 1, 0, false);

         // Publish Messages
         String payload1 = "This is message 1";
         String payload2 = "This is message 2";
         String payload3 = "This is message 3";
         String payload4 = "This is message 4";

         pubConnection.publish(ANYCAST_TOPIC, payload1.getBytes(), QoS.AT_LEAST_ONCE, false);
         pubConnection.publish(ANYCAST_TOPIC, payload2.getBytes(), QoS.AT_MOST_ONCE, false);
         pubConnection.publish(ANYCAST_TOPIC, payload3.getBytes(), QoS.AT_MOST_ONCE, false);
         pubConnection.publish(ANYCAST_TOPIC, payload4.getBytes(), QoS.AT_MOST_ONCE, false);

         Message message1 = subConnection3.receive(5, TimeUnit.SECONDS);
         message1.ack();
         Message message2 = subConnection3.receive(5, TimeUnit.SECONDS);
         message2.ack();
         Message message3 = subConnection3.receive(5, TimeUnit.SECONDS);
         message3.ack();
         Message message4 = subConnection3.receive(5, TimeUnit.SECONDS);
         message4.ack();

         assertEquals(payload1, new String(message1.getPayload()));
         assertEquals(payload2, new String(message2.getPayload()));
         assertEquals(payload3, new String(message3.getPayload()));
         assertEquals(payload4, new String(message4.getPayload()));

         subConnection3.unsubscribe(new String[]{ANYCAST_TOPIC});

         waitForBindings(0, ANYCAST_TOPIC, 1, 0, true);
         waitForBindings(1, ANYCAST_TOPIC, 1, 0, true);

         waitForBindings(0, ANYCAST_TOPIC, 1, 0, false);
         waitForBindings(1, ANYCAST_TOPIC, 1, 0, false);

         pubConnection.publish(ANYCAST_TOPIC, payload1.getBytes(), QoS.AT_LEAST_ONCE, false);
         pubConnection.publish(ANYCAST_TOPIC, payload2.getBytes(), QoS.AT_MOST_ONCE, false);
         pubConnection.publish(ANYCAST_TOPIC, payload3.getBytes(), QoS.AT_MOST_ONCE, false);

         Message message11 = subConnection3.receive(100, TimeUnit.MILLISECONDS);
         assertNull(message11);
         Message message21 = subConnection3.receive(100, TimeUnit.MILLISECONDS);
         assertNull(message21);
         Message message31 = subConnection3.receive(100, TimeUnit.MILLISECONDS);
         assertNull(message31);


      } finally {
         String[] topics = new String[]{ANYCAST_TOPIC};
         if (subConnection1 != null && subConnection1.isConnected()) {
            subConnection1.unsubscribe(topics);
            subConnection1.disconnect();
         }
         if (subConnection2 != null && subConnection2.isConnected()) {
            subConnection2.unsubscribe(topics);
            subConnection2.disconnect();
         }
         if (subConnection3 != null && subConnection3.isConnected()) {
            subConnection3.unsubscribe(topics);
            subConnection3.disconnect();
         }
         if (pubConnection != null && pubConnection.isConnected()) {
            pubConnection.unsubscribe(topics);
            pubConnection.disconnect();
         }
      }

   }

   @Test
   public void useDiffClientIdSubscribeExistingQueue() throws Exception {
      final String ANYCAST_TOPIC = "anycast/test/1/some/la";
      final String clientId1 = "clientId1";
      final String clientId2 = "clientId2";
      final String clientId3 = "clientId3";

      setupServers(ANYCAST_TOPIC);

      startServers(0, 1);
      BlockingConnection connection1 = null;
      BlockingConnection connection2 = null;
      BlockingConnection connection3 = null;
      try {
         //Waiting for resource initialization to complete
         Thread.sleep(1000);
         connection1 = retrieveMQTTConnection("tcp://localhost:61616", clientId1);
         connection2 = retrieveMQTTConnection("tcp://localhost:61617", clientId2);
         connection3 = retrieveMQTTConnection("tcp://localhost:61617", clientId3);
         // Subscribe to topics
         Topic[] topics = {new Topic(ANYCAST_TOPIC, QoS.AT_MOST_ONCE)};
         connection1.subscribe(topics);

         waitForBindings(0, ANYCAST_TOPIC, 1, 1, true);
         waitForBindings(1, ANYCAST_TOPIC, 1, 0, true);

         waitForBindings(0, ANYCAST_TOPIC, 1, 0, false);
         waitForBindings(1, ANYCAST_TOPIC, 1, 1, false);

         connection2.subscribe(topics);

         waitForBindings(0, ANYCAST_TOPIC, 1, 1, true);
         waitForBindings(1, ANYCAST_TOPIC, 1, 1, true);

         waitForBindings(0, ANYCAST_TOPIC, 1, 1, false);
         waitForBindings(1, ANYCAST_TOPIC, 1, 1, false);

         connection3.subscribe(topics);

         waitForBindings(0, ANYCAST_TOPIC, 1, 1, true);
         waitForBindings(1, ANYCAST_TOPIC, 1, 2, true);

         waitForBindings(0, ANYCAST_TOPIC, 1, 2, false);
         waitForBindings(1, ANYCAST_TOPIC, 1, 1, false);

         // Publish Messages
         String payload1 = "This is message 1";
         String payload2 = "This is message 2";
         String payload3 = "This is message 3";
         String payload4 = "This is message 4";

         connection1.publish(ANYCAST_TOPIC, payload1.getBytes(), QoS.AT_LEAST_ONCE, false);
         connection1.publish(ANYCAST_TOPIC, payload2.getBytes(), QoS.AT_MOST_ONCE, false);
         connection1.publish(ANYCAST_TOPIC, payload3.getBytes(), QoS.AT_MOST_ONCE, false);
         connection1.publish(ANYCAST_TOPIC, payload4.getBytes(), QoS.AT_MOST_ONCE, false);

         Message message1 = connection1.receive(5, TimeUnit.SECONDS);
         message1.ack();
         Message message2 = connection2.receive(5, TimeUnit.SECONDS);
         message2.ack();
         Message message3 = connection1.receive(5, TimeUnit.SECONDS);
         message3.ack();
         Message message4 = connection3.receive(5, TimeUnit.SECONDS);
         message4.ack();

         assertEquals(payload1, new String(message1.getPayload()));
         assertEquals(payload2, new String(message2.getPayload()));
         assertEquals(payload3, new String(message3.getPayload()));
         assertEquals(payload4, new String(message4.getPayload()));

         connection2.unsubscribe(new String[]{ANYCAST_TOPIC});

         waitForBindings(0, ANYCAST_TOPIC, 1, 1, true);
         waitForBindings(1, ANYCAST_TOPIC, 1, 1, true);

         waitForBindings(0, ANYCAST_TOPIC, 1, 1, false);
         waitForBindings(1, ANYCAST_TOPIC, 1, 1, false);

         connection1.publish(ANYCAST_TOPIC, payload1.getBytes(), QoS.AT_LEAST_ONCE, false);
         connection1.publish(ANYCAST_TOPIC, payload2.getBytes(), QoS.AT_MOST_ONCE, false);
         connection1.publish(ANYCAST_TOPIC, payload3.getBytes(), QoS.AT_MOST_ONCE, false);

         Message message11 = connection1.receive(5, TimeUnit.SECONDS);
         message11.ack();
         Message message21 = connection3.receive(5, TimeUnit.SECONDS);
         message21.ack();
         Message message31 = connection1.receive(5, TimeUnit.SECONDS);
         message31.ack();

         String message11String = new String(message11.getPayload());
         String message21String = new String(message21.getPayload());
         String message31String = new String(message31.getPayload());
         assertTrue(payload1.equals(message11String) || payload1.equals(message21String) || payload1.equals(message31String));
         assertTrue(payload2.equals(message11String) || payload2.equals(message21String) || payload2.equals(message31String));
         assertTrue(payload3.equals(message11String) || payload3.equals(message21String) || payload3.equals(message31String));

      } finally {
         String[] topics = new String[]{ANYCAST_TOPIC};
         if (connection1 != null && connection1.isConnected()) {
            connection1.unsubscribe(topics);
            connection1.disconnect();
         }
         if (connection2 != null && connection2.isConnected()) {
            connection2.unsubscribe(topics);
            connection2.disconnect();
         }
         if (connection3 != null && connection3.isConnected()) {
            connection3.unsubscribe(topics);
            connection3.disconnect();
         }

      }

   }

   private static BlockingConnection retrieveMQTTConnection(String host, String clientId) throws Exception {
      MQTT mqtt = new MQTT();
      mqtt.setHost(host);
      mqtt.setClientId(clientId);
      mqtt.setConnectAttemptsMax(0);
      mqtt.setReconnectAttemptsMax(0);
      BlockingConnection connection = mqtt.blockingConnection();
      connection.connect();
      return connection;
   }

   private void setupServers(String TOPIC) throws Exception {
      WildcardConfiguration wildcardConfiguration = createWildCardConfiguration();
      CoreAddressConfiguration coreAddressConfiguration = createAddressConfiguration(TOPIC);
      AddressSettings addressSettings = createAddressSettings();


      setupServer(0, false, isNetty());
      servers[0].getConfiguration().setWildCardConfiguration(wildcardConfiguration);
      servers[0].getConfiguration().addAddressConfiguration(coreAddressConfiguration);
      servers[0].getConfiguration().addAddressSetting("#", addressSettings);
      setupServer(1, false, isNetty());
      servers[1].getConfiguration().setWildCardConfiguration(wildcardConfiguration);
      servers[1].getConfiguration().addAddressConfiguration(coreAddressConfiguration);
      servers[1].getConfiguration().addAddressSetting("#", addressSettings);

      setupClusterConnection("cluster0", "", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 0, 1);
      setupClusterConnection("cluster1", "", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 1, 0);
   }

   private AddressSettings createAddressSettings() {
      AddressSettings addressSettings = new AddressSettings();
      addressSettings.setRedistributionDelay(0);
      addressSettings.setDefaultAddressRoutingType(RoutingType.ANYCAST);
      return addressSettings;
   }

   private CoreAddressConfiguration createAddressConfiguration(String TOPIC) {
      CoreAddressConfiguration coreAddressConfiguration = new CoreAddressConfiguration();
      coreAddressConfiguration.addRoutingType(RoutingType.ANYCAST);
      coreAddressConfiguration.setName(TOPIC);
      coreAddressConfiguration.addQueueConfiguration(QueueConfiguration.of(TOPIC).setRoutingType(RoutingType.ANYCAST));
      return coreAddressConfiguration;
   }

   private WildcardConfiguration createWildCardConfiguration() {
      WildcardConfiguration wildcardConfiguration = new WildcardConfiguration();
      wildcardConfiguration.setAnyWords('#');
      wildcardConfiguration.setDelimiter('/');
      wildcardConfiguration.setRoutingEnabled(true);
      wildcardConfiguration.setSingleWord('+');
      return wildcardConfiguration;
   }

   private boolean waitConnectionClosed(BlockingConnection connection) throws Exception {
      return Wait.waitFor(() -> !connection.isConnected());
   }
}
