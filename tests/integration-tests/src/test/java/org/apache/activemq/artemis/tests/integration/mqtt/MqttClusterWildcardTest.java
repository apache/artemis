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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.integration.cluster.distribution.ClusterTestBase;
import org.fusesource.mqtt.client.BlockingConnection;
import org.fusesource.mqtt.client.MQTT;
import org.fusesource.mqtt.client.Message;
import org.fusesource.mqtt.client.QoS;
import org.fusesource.mqtt.client.Topic;
import org.junit.jupiter.api.Test;

public class MqttClusterWildcardTest extends ClusterTestBase {

   @Override
   protected boolean isResolveProtocols() {
      return true;
   }

   public boolean isNetty() {
      return true;
   }

   @Test
   public void loadBalanceRequests() throws Exception {
      final String TOPIC = "test/+/some/#";

      WildcardConfiguration wildcardConfiguration = new WildcardConfiguration();
      wildcardConfiguration.setAnyWords('#');
      wildcardConfiguration.setDelimiter('/');
      wildcardConfiguration.setRoutingEnabled(true);
      wildcardConfiguration.setSingleWord('+');

      setupServer(0, false, isNetty());
      servers[0].getConfiguration().setWildCardConfiguration(wildcardConfiguration);
      setupServer(1, false, isNetty());
      servers[1].getConfiguration().setWildCardConfiguration(wildcardConfiguration);

      setupClusterConnection("cluster0", "", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 0, 1);
      setupClusterConnection("cluster1", "", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 1, 0);

      startServers(0, 1);

      BlockingConnection connection1 = null;
      BlockingConnection connection2 = null;
      try {
         connection1 = retrieveMQTTConnection("tcp://localhost:61616");
         connection2 = retrieveMQTTConnection("tcp://localhost:61617");

         // Subscribe to topics
         Topic[] topics = {new Topic(TOPIC, QoS.AT_MOST_ONCE)};
         connection1.subscribe(topics);
         connection2.subscribe(topics);

         waitForBindings(0, TOPIC, 1, 1, true);
         waitForBindings(1, TOPIC, 1, 1, true);

         waitForBindings(0, TOPIC, 1, 1, false);
         waitForBindings(1, TOPIC, 1, 1, false);

         // Publish Messages
         String payload1 = "This is message 1";
         String payload2 = "This is message 2";
         String payload3 = "This is message 3";

         connection1.publish("test/1/some/la", payload1.getBytes(), QoS.AT_LEAST_ONCE, false);
         connection1.publish("test/1/some/la", payload2.getBytes(), QoS.AT_MOST_ONCE, false);
         connection1.publish("test/1/some/la", payload3.getBytes(), QoS.AT_MOST_ONCE, false);

         Message message1 = connection1.receive(5, TimeUnit.SECONDS);
         Message message2 = connection1.receive(5, TimeUnit.SECONDS);
         Message message3 = connection1.receive(5, TimeUnit.SECONDS);
         Message message4 = connection2.receive(5, TimeUnit.SECONDS);
         Message message5 = connection2.receive(5, TimeUnit.SECONDS);
         Message message6 = connection2.receive(5, TimeUnit.SECONDS);

         assertEquals(payload1, new String(message1.getPayload()));
         assertEquals(payload2, new String(message2.getPayload()));
         assertEquals(payload3, new String(message3.getPayload()));
         assertEquals(payload1, new String(message4.getPayload()));
         assertEquals(payload2, new String(message5.getPayload()));
         assertEquals(payload3, new String(message6.getPayload()));

         assertNonWildcardTopic(message1);
         assertNonWildcardTopic(message2);
         assertNonWildcardTopic(message3);
         assertNonWildcardTopic(message4);
         assertNonWildcardTopic(message5);
         assertNonWildcardTopic(message6);


      } finally {
         String[] topics = new String[]{TOPIC};
         if (connection1 != null) {
            connection1.unsubscribe(topics);
            connection1.disconnect();
         }
         if (connection2 != null) {
            connection2.unsubscribe(topics);
            connection2.disconnect();
         }
      }
   }

   @Test
   public void verifyRedistribution() throws Exception {
      final String TOPIC = "test/+/some/#";
      final String clientId = "SubOne";

      WildcardConfiguration wildcardConfiguration = new WildcardConfiguration();
      wildcardConfiguration.setAnyWords('#');
      wildcardConfiguration.setDelimiter('/');
      wildcardConfiguration.setRoutingEnabled(true);
      wildcardConfiguration.setSingleWord('+');

      setupServer(0, false, isNetty());
      servers[0].getConfiguration().setWildCardConfiguration(wildcardConfiguration);

      setupServer(1, false, isNetty());
      servers[1].getConfiguration().setWildCardConfiguration(wildcardConfiguration);

      // allow redistribution
      AddressSettings addressSettings = new AddressSettings();
      addressSettings.setRedistributionDelay(0);
      servers[0].getConfiguration().addAddressSetting("#", addressSettings);
      servers[1].getConfiguration().addAddressSetting("#", addressSettings);

      setupClusterConnection("cluster0", "", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 0, 1);
      setupClusterConnection("cluster1", "", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 1, 0);

      startServers(0, 1);

      BlockingConnection connection1 = null;
      BlockingConnection connection2 = null;
      try {
         connection1 = retrieveMQTTConnection("tcp://localhost:61616");
         connection2 = retrieveMQTTConnection("tcp://localhost:61617", clientId);

         // Subscribe to topics
         Topic[] topics = {new Topic(TOPIC, QoS.EXACTLY_ONCE)};
         connection2.subscribe(topics);

         waitForBindings(0, TOPIC, 0, 0, true);
         waitForBindings(1, TOPIC, 1, 1, true);

         waitForBindings(0, TOPIC, 1, 1, false);
         waitForBindings(1, TOPIC, 0, 0, false);

         // Publish Messages
         String payload1 = "This is message 1";
         String payload2 = "This is message 2";
         String payload3 = "This is message 3";

         connection1.publish("test/1/some/la", payload1.getBytes(), QoS.EXACTLY_ONCE, false);
         connection1.publish("test/1/some/la", payload2.getBytes(), QoS.EXACTLY_ONCE, false);
         connection1.publish("test/1/some/la", payload3.getBytes(), QoS.EXACTLY_ONCE, false);


         waitForMessages(1, TOPIC, 3);

         connection2.disconnect();

         // force redistribution
         connection2 = retrieveMQTTConnection("tcp://localhost:61616", clientId);
         connection2.subscribe(topics);

         Message message4 = connection2.receive(15, TimeUnit.SECONDS);
         Message message5 = connection2.receive(5, TimeUnit.SECONDS);
         Message message6 = connection2.receive(5, TimeUnit.SECONDS);

         assertEquals(payload1, new String(message4.getPayload()));
         assertEquals(payload2, new String(message5.getPayload()));
         assertEquals(payload3, new String(message6.getPayload()));

         assertNonWildcardTopic(message4);
         assertNonWildcardTopic(message5);
         assertNonWildcardTopic(message6);

      } finally {
         String[] topics = new String[]{TOPIC};
         if (connection1 != null) {
            connection1.unsubscribe(topics);
            connection1.disconnect();
         }
         if (connection2 != null) {
            connection2.unsubscribe(topics);
            connection2.disconnect();
         }
      }
   }

   @Test
   public void wildcardsWithBroker1Disconnected() throws Exception {
      BlockingConnection connection1 = null;
      BlockingConnection connection2 = null;
      final String TOPIC = "test/+/some/#";
      try {

         WildcardConfiguration wildcardConfiguration = new WildcardConfiguration();
         wildcardConfiguration.setAnyWords('#');
         wildcardConfiguration.setDelimiter('/');
         wildcardConfiguration.setRoutingEnabled(true);
         wildcardConfiguration.setSingleWord('+');

         setupServer(0, false, isNetty());
         servers[0].getConfiguration().setWildCardConfiguration(wildcardConfiguration);

         setupClusterConnection("cluster0", "", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 0, 1);

         startServers(0);

         connection1 = retrieveMQTTConnection("tcp://localhost:61616");

         // Subscribe to topics
         Topic[] topics = {new Topic(TOPIC, QoS.AT_MOST_ONCE)};
         connection1.subscribe(topics);

         waitForBindings(0, TOPIC, 1, 1, true);
         waitForBindings(0, TOPIC, 0, 0, false);

         // Publish Messages
         String payload1 = "This is message 1";
         String payload2 = "This is message 2";
         String payload3 = "This is message 3";

         connection1.publish("test/1/some/la", payload1.getBytes(), QoS.AT_LEAST_ONCE, false);
         connection1.publish("test/1/some/la", payload2.getBytes(), QoS.AT_MOST_ONCE, false);
         connection1.publish("test/1/some/la", payload3.getBytes(), QoS.AT_MOST_ONCE, false);

         Message message1 = connection1.receive(5, TimeUnit.SECONDS);

         setupServer(1, false, isNetty());
         servers[1].getConfiguration().setWildCardConfiguration(wildcardConfiguration);

         setupClusterConnection("cluster1", "", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 1, 0);
         startServers(1);

         connection2 = retrieveMQTTConnection("tcp://localhost:61617");
         connection2.subscribe(topics);

         waitForBindings(1, TOPIC, 1, 1, false);
         waitForBindings(1, TOPIC, 1, 1, true);
         waitForBindings(0, TOPIC, 1, 1, false);
         waitForBindings(0, TOPIC, 1, 1, true);

         connection1.publish("test/1/some/la", payload1.getBytes(), QoS.AT_LEAST_ONCE, false);
         connection1.publish("test/1/some/la", payload2.getBytes(), QoS.AT_MOST_ONCE, false);
         connection1.publish("test/1/some/la", payload3.getBytes(), QoS.AT_MOST_ONCE, false);

         Message message2 = connection1.receive(10, TimeUnit.SECONDS);
         assertNotNull(message2);
         Message message3 = connection1.receive(10, TimeUnit.SECONDS);
         assertNotNull(message3);
         Message message4 = connection2.receive(10, TimeUnit.SECONDS);
         assertNotNull(message4);
         Message message5 = connection2.receive(10, TimeUnit.SECONDS);
         assertNotNull(message5);
         Message message6 = connection2.receive(10, TimeUnit.SECONDS);
         assertNotNull(message6);

         assertEquals(payload1, new String(message1.getPayload()));
         assertEquals(payload2, new String(message2.getPayload()));
         assertEquals(payload3, new String(message3.getPayload()));
         assertEquals(payload1, new String(message4.getPayload()));
         assertEquals(payload2, new String(message5.getPayload()));
         assertEquals(payload3, new String(message6.getPayload()));

         assertNonWildcardTopic(message1);
         assertNonWildcardTopic(message2);
         assertNonWildcardTopic(message3);
         assertNonWildcardTopic(message4);
         assertNonWildcardTopic(message5);
         assertNonWildcardTopic(message6);


      } finally {
         String[] topics = new String[]{TOPIC};
         if (connection1 != null) {
            connection1.unsubscribe(topics);
            connection1.disconnect();
         }
         if (connection2 != null) {
            connection2.unsubscribe(topics);
            connection2.disconnect();
         }
      }
   }

   private void assertNonWildcardTopic(Message message1) {
      assertNotNull(message1);
      String payload = new String(message1.getPayload());
      System.err.println("got payload: " + payload);

      assertTrue(payload.contains("message"));
      String topic = message1.getTopic();
      System.err.println("got topic: " + topic);
      assertFalse(topic.contains("+"));
      assertFalse(topic.contains("*"));
      assertFalse(topic.contains("#"));
   }


   private static BlockingConnection retrieveMQTTConnection(String host) throws Exception {
      return retrieveMQTTConnection(host, null);
   }

   private static BlockingConnection retrieveMQTTConnection(String host, String clientId) throws Exception {
      MQTT mqtt = new MQTT();
      mqtt.setHost(host);
      if (clientId != null) {
         mqtt.setClientId(clientId);
         mqtt.setCleanSession(false);
      }
      BlockingConnection connection = mqtt.blockingConnection();
      connection.connect();
      return connection;
   }
}
