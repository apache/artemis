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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.management.CoreNotificationType;
import org.apache.activemq.artemis.api.core.management.ManagementHelper;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTReasonCodes;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTUtil;
import org.apache.activemq.artemis.core.security.CheckType;
import org.apache.activemq.artemis.tests.integration.mqtt5.MQTT5TestSupport;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptionsBuilder;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class PublishTestsWithSecurity extends MQTT5TestSupport {

   @Override
   public boolean isSecurityEnabled() {
      return true;
   }

   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testCreateAddressAuthorizationFailure() throws Exception {
      final String CLIENT_ID = "publisher";
      final CountDownLatch latch = new CountDownLatch(1);
      MqttConnectionOptions options = new MqttConnectionOptionsBuilder()
         .username(noprivUser)
         .password(noprivPass.getBytes(StandardCharsets.UTF_8))
         .build();
      MqttClient client = createPahoClient(CLIENT_ID);
      client.connect(options);

      server.getManagementService().addNotificationListener(notification -> {
         if (notification.getType() == CoreNotificationType.SECURITY_PERMISSION_VIOLATION && CheckType.valueOf(notification.getProperties().getSimpleStringProperty(ManagementHelper.HDR_CHECK_TYPE).toString()) == CheckType.CREATE_ADDRESS) {
            latch.countDown();
         }
      });

      try {
         client.publish("/foo", new byte[0], 2, false);
         fail("Publishing should have failed with a security problem");
      } catch (MqttException e) {
         assertEquals(MQTTReasonCodes.NOT_AUTHORIZED, (byte) e.getReasonCode());
      } catch (Exception e) {
         fail("Should have thrown an MqttException");
      }

      assertTrue(latch.await(2, TimeUnit.SECONDS));

      assertFalse(client.isConnected());
   }

   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testSendAuthorizationFailure() throws Exception {
      final String CLIENT_ID = "publisher";
      final String TOPIC = "/foo";
      final CountDownLatch latch = new CountDownLatch(1);
      MqttConnectionOptions options = new MqttConnectionOptionsBuilder()
         .username(createAddressUser)
         .password(createAddressPass.getBytes(StandardCharsets.UTF_8))
         .build();
      MqttClient client = createPahoClient(CLIENT_ID);
      client.connect(options);

      server.getManagementService().addNotificationListener(notification -> {
         if (notification.getType() == CoreNotificationType.SECURITY_PERMISSION_VIOLATION && CheckType.valueOf(notification.getProperties().getSimpleStringProperty(ManagementHelper.HDR_CHECK_TYPE).toString()) == CheckType.SEND) {
            latch.countDown();
         }
      });

      try {
         client.publish(TOPIC, new byte[0], 2, false);
         fail("Publishing should have failed with a security problem");
      } catch (MqttException e) {
         assertEquals(MQTTReasonCodes.NOT_AUTHORIZED, (byte) e.getReasonCode());
      } catch (Exception e) {
         fail("Should have thrown an MqttException");
      }

      assertTrue(latch.await(2, TimeUnit.SECONDS));

      assertFalse(client.isConnected());

      Wait.assertTrue(() -> server.getAddressInfo(SimpleString.of(MQTTUtil.getCoreAddressFromMqttTopic(TOPIC, server.getConfiguration().getWildcardConfiguration()))) != null, 2000, 100);
   }

   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testAuthorizationSuccess() throws Exception {
      final String CLIENT_ID = "publisher";
      MqttConnectionOptions options = new MqttConnectionOptionsBuilder()
         .username(fullUser)
         .password(fullPass.getBytes(StandardCharsets.UTF_8))
         .build();
      MqttClient client = createPahoClient(CLIENT_ID);
      client.connect(options);

      try {
         client.publish("/foo", new byte[0], 2, false);
      } catch (MqttException e) {
         fail("Publishing should not have failed with a security problem");
      } catch (Exception e) {
         fail("Should have thrown an MqttException");
      }

      client.isConnected();
   }

   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testWillAuthorizationSuccess() throws Exception {
      internalTestWillAuthorization(fullUser, fullPass, true);
   }

   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testWillAuthorizationFailure() throws Exception {
      internalTestWillAuthorization(noprivUser, noprivPass, false);
   }

   private void internalTestWillAuthorization(String username, String password, boolean succeed) throws Exception {
      final byte[] WILL = RandomUtil.randomBytes();
      final String TOPIC = RandomUtil.randomUUIDString();

      // consumer of the will message
      MqttClient client1 = createPahoClient("willConsumer");
      CountDownLatch latch = new CountDownLatch(1);
      client1.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) {
            latch.countDown();
         }
      });
      MqttConnectionOptions options = new MqttConnectionOptionsBuilder()
         .username(fullUser)
         .password(fullPass.getBytes(StandardCharsets.UTF_8))
         .build();
      client1.connect(options);
      client1.subscribe(TOPIC, 1);

      // consumer to generate the will
      MqttClient client2 = createPahoClient("willGenerator");
      options = new MqttConnectionOptionsBuilder()
         .username(username)
         .password(password.getBytes(StandardCharsets.UTF_8))
         .will(TOPIC, new MqttMessage(WILL))
         .build();
      client2.connect(options);
      client2.disconnectForcibly(0, 0, false);

      assertEquals(succeed, latch.await(2, TimeUnit.SECONDS));
   }
}
