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
package org.apache.activemq.artemis.tests.integration.mqtt5.spec.controlpackets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.management.CoreNotificationType;
import org.apache.activemq.artemis.api.core.management.ManagementHelper;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTReasonCodes;
import org.apache.activemq.artemis.core.security.CheckType;
import org.apache.activemq.artemis.tests.integration.mqtt5.MQTT5TestSupport;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptionsBuilder;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.eclipse.paho.mqttv5.common.packet.MqttUnsubAck;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class SubscribeTestsWithSecurity extends MQTT5TestSupport {

   @Override
   public boolean isSecurityEnabled() {
      return true;
   }

   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testAuthorizationFailure() throws Exception {
      final String CLIENT_ID = "consumer";
      final int SUBSCRIPTION_COUNT = 10;
      MqttConnectionOptions options = new MqttConnectionOptionsBuilder()
         .username(noprivUser)
         .password(noprivPass.getBytes(StandardCharsets.UTF_8))
         .build();
      MqttClient client = createPahoClient(CLIENT_ID);
      client.connect(options);

      MqttSubscription[] subscriptions = new MqttSubscription[SUBSCRIPTION_COUNT];
      for (int i = 0; i < SUBSCRIPTION_COUNT; i++) {
         MqttSubscription subscription = new MqttSubscription(RandomUtil.randomUUIDString(), RandomUtil.randomInterval(0, 3));
         subscriptions[i] = subscription;
      }

      IMqttToken token = client.subscribe(subscriptions);
      int[] reasonCodes = token.getResponse().getReasonCodes();
      assertEquals(SUBSCRIPTION_COUNT, reasonCodes.length);
      for (int reasonCode : reasonCodes) {
         assertEquals(MQTTReasonCodes.NOT_AUTHORIZED, (byte) reasonCode);
      }
   }

   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testAuthorizationSuccess() throws Exception {
      final String CLIENT_ID = "consumer";
      final int SUBSCRIPTION_COUNT = 10;
      MqttConnectionOptions options = new MqttConnectionOptionsBuilder()
         .username(fullUser)
         .password(fullPass.getBytes(StandardCharsets.UTF_8))
         .build();
      MqttClient client = createPahoClient(CLIENT_ID);
      client.connect(options);

      MqttSubscription[] subscriptions = new MqttSubscription[SUBSCRIPTION_COUNT];
      int[] requestedQos = new int[SUBSCRIPTION_COUNT];
      for (int i = 0; i < SUBSCRIPTION_COUNT; i++) {
         requestedQos[i] = RandomUtil.randomInterval(0, 3);
         MqttSubscription subscription = new MqttSubscription(RandomUtil.randomUUIDString(), requestedQos[i]);
         subscriptions[i] = subscription;
      }

      IMqttToken token = client.subscribe(subscriptions);
      int[] reasonCodes = token.getResponse().getReasonCodes();
      assertEquals(SUBSCRIPTION_COUNT, reasonCodes.length);
      for (int i = 0; i < reasonCodes.length; i++) {
         assertEquals(requestedQos[i], reasonCodes[i]);
      }

      client.disconnect();
   }

   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testImplicitSubscriptionQueueRemoved() throws Exception {
      final String CONSUMER_ID = "consumer";
      MqttConnectionOptions options = new MqttConnectionOptionsBuilder()
         .username(noDeleteUser)
         .password(noDeletePass.getBytes(StandardCharsets.UTF_8))
         .build();
      MqttClient client = createPahoClient(CONSUMER_ID);
      client.connect(options);

      client.subscribe(getTopicName(), 0).waitForCompletion();
      client.disconnect();

      Wait.assertTrue(() -> getSubscriptionQueue(getTopicName(), CONSUMER_ID) == null, 2000, 100);
   }

   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testExplicitUnsubscribeAuthorizationFailure() throws Exception {
      final String CONSUMER_ID = "consumer";
      final String TOPIC = getTopicName();
      final CountDownLatch latch = new CountDownLatch(1);

      server.getManagementService().addNotificationListener(notification -> {
         if (notification.getType() == CoreNotificationType.SECURITY_PERMISSION_VIOLATION && CheckType.valueOf(notification.getProperties().getSimpleStringProperty(ManagementHelper.HDR_CHECK_TYPE).toString()) == CheckType.DELETE_DURABLE_QUEUE) {
            latch.countDown();
         }
      });

      MqttConnectionOptions options = new MqttConnectionOptionsBuilder()
         .username(noDeleteUser)
         .password(noDeletePass.getBytes(StandardCharsets.UTF_8))
         .build();
      MqttAsyncClient client = createAsyncPahoClient(CONSUMER_ID);
      client.connect(options).waitForCompletion();
      client.subscribe(TOPIC, 0).waitForCompletion();

      assertNotNull(getSubscriptionQueue(TOPIC, CONSUMER_ID));

      IMqttToken token = client.unsubscribe(TOPIC);
      token.waitForCompletion();
      MqttUnsubAck response = (MqttUnsubAck) token.getResponse();
      assertEquals(MQTTReasonCodes.UNSPECIFIED_ERROR, (byte) response.getReturnCodes()[0]);

      assertTrue(latch.await(2, TimeUnit.SECONDS));

      // the client wasn't authorized to delete the durable queue backing its subscription so it should still exist
      assertNotNull(getSubscriptionQueue(TOPIC, CONSUMER_ID));

      client.disconnect().waitForCompletion();
      client.close();
   }

   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testExplicitUnsubscribeAuthorizationSuccess() throws Exception {
      final String CONSUMER_ID = "consumer";
      final String TOPIC = getTopicName();

      MqttConnectionOptions options = new MqttConnectionOptionsBuilder()
         .username(fullUser)
         .password(fullPass.getBytes(StandardCharsets.UTF_8))
         .build();
      MqttAsyncClient client = createAsyncPahoClient(CONSUMER_ID);
      client.connect(options).waitForCompletion();
      client.subscribe(TOPIC, 0).waitForCompletion();

      assertNotNull(getSubscriptionQueue(TOPIC, CONSUMER_ID));

      IMqttToken token = client.unsubscribe(TOPIC);
      token.waitForCompletion();
      MqttUnsubAck response = (MqttUnsubAck) token.getResponse();
      assertEquals(MQTTReasonCodes.SUCCESS, (byte) response.getReturnCodes()[0]);

      // the client was authorized to delete the durable queue backing its subscription so it should be gone
      assertNull(getSubscriptionQueue(TOPIC, CONSUMER_ID));

      client.disconnect().waitForCompletion();
      client.close();
   }
}
