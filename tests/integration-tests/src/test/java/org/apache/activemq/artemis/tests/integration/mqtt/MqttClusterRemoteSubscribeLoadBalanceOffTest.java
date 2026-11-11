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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.config.CoreAddressConfiguration;
import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.integration.cluster.distribution.ClusterTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.fusesource.mqtt.client.BlockingConnection;
import org.fusesource.mqtt.client.MQTT;
import org.fusesource.mqtt.client.Message;
import org.fusesource.mqtt.client.QoS;
import org.fusesource.mqtt.client.Topic;
import org.junit.jupiter.api.Test;

public class MqttClusterRemoteSubscribeLoadBalanceOffTest extends ClusterTestBase {

   @Override
   protected boolean isResolveProtocols() {
      return true;
   }

   public boolean isNetty() {
      return true;
   }

   @Test
   public void testPub0Sub1() throws Exception {
      final String TOPIC = "test/1";
      final String clientId1 = "clientId1";
      final String clientId2 = "clientId2";
      Topic[] topics = {new Topic(TOPIC, QoS.AT_MOST_ONCE)};

      setupServers(TOPIC);

      startServers(0, 1);

      final BlockingConnection connection1 = retrieveMQTTConnection("tcp://localhost:61616", clientId1);
      final BlockingConnection connection2 = retrieveMQTTConnection("tcp://localhost:61617", clientId2);

      assertTrue(Wait.waitFor(() -> connection1.isConnected(), 5000, 100), "Should be connected");
      assertTrue(Wait.waitFor(() -> connection2.isConnected(), 5000, 100), "Should be connected");

      waitForTopology(servers[0], "cluster0", 2, 5000);
      waitForTopology(servers[1], "cluster1", 2, 5000);

      // Subscribe to topics
      connection1.subscribe(topics);
      connection2.subscribe(topics);


      waitForBindings(0, TOPIC, 1, 1, false);
      waitForBindings(1, TOPIC, 1, 1, false);

      // Publish Messages
      String payload1 = "This is message 1";
      String payload2 = "This is message 2";

      connection1.publish(TOPIC, payload1.getBytes(), QoS.AT_LEAST_ONCE, false);
      connection2.publish(TOPIC, payload2.getBytes(), QoS.AT_MOST_ONCE, false);

      Message message1 = connection1.receive(5, TimeUnit.SECONDS);
      message1.ack();
      Message message2 = connection1.receive(5, TimeUnit.SECONDS);
      message2.ack();

      message1 = connection2.receive(5, TimeUnit.SECONDS);
      message1.ack();
      message2 = connection2.receive(5, TimeUnit.SECONDS);
      message2.ack();

      String[] topicsStrings = new String[]{TOPIC};
      if (connection1 != null && connection1.isConnected()) {
         connection1.unsubscribe(topicsStrings);
         connection1.disconnect();
      }
      if (connection2 != null && connection2.isConnected()) {
         connection2.unsubscribe(topicsStrings);
         connection2.disconnect();
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

   private void setupServers(String address) throws Exception {

      WildcardConfiguration wildcardConfiguration = createWildCardConfiguration();
      CoreAddressConfiguration coreAddressConfiguration = createAddressConfiguration(address);
      AddressSettings addressSettings = createAddressSettings();

      setupServer(0, false, isNetty());
      servers[0].getConfiguration().addAddressConfiguration(coreAddressConfiguration);
      servers[0].getConfiguration().addAddressSetting("#", addressSettings);
      servers[0].getConfiguration().setWildCardConfiguration(wildcardConfiguration);

      setupServer(1, false, isNetty());
      servers[1].getConfiguration().addAddressConfiguration(coreAddressConfiguration);
      servers[1].getConfiguration().addAddressSetting("#", addressSettings);
      servers[1].getConfiguration().setWildCardConfiguration(wildcardConfiguration);

      setupClusterConnection("cluster0", "", MessageLoadBalancingType.OFF_WITH_REDISTRIBUTION, 1, isNetty(), 0, 1);
      setupClusterConnection("cluster1", "", MessageLoadBalancingType.OFF, 1, isNetty(), 1, 0);
   }

   private AddressSettings createAddressSettings() {
      AddressSettings addressSettings = new AddressSettings();
      addressSettings.setRedistributionDelay(0);
      addressSettings.setDefaultAddressRoutingType(RoutingType.MULTICAST);
      return addressSettings;
   }

   private CoreAddressConfiguration createAddressConfiguration(String TOPIC) {
      CoreAddressConfiguration coreAddressConfiguration = new CoreAddressConfiguration();
      coreAddressConfiguration.addRoutingType(RoutingType.MULTICAST);
      coreAddressConfiguration.setName(TOPIC);
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
}
