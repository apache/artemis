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
package org.apache.activemq.artemis.tests.integration.quota;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttPubReplyMessageVariableHeader;
import io.netty.handler.codec.mqtt.MqttSubAckPayload;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTInterceptor;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTReasonCodes;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.settings.impl.ResourceQuotaConfig;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Verifies that MQTT 5 clients receive the correct QUOTA_EXCEEDED (0x97)
 * reason code in PUBACK, PUBREC, and SUBACK responses when resource
 * quotas are exceeded.
 */
public class MqttQuotaReasonCodeTest extends ActiveMQTestBase {

   private static final int NETTY_PORT = 61616;

   private ActiveMQServer createServerWithQuota(ResourceQuotaConfig quotaConfig, String addressPattern) throws Exception {
      Configuration config = createDefaultConfig(true);
      config.addResourceQuota(quotaConfig.getName(), quotaConfig);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota(quotaConfig.getName());
      settings.setMaxSizeBytes(-1L);
      settings.setAddressFullMessagePolicy(AddressFullMessagePolicy.FAIL);
      settings.setAutoCreateAddresses(true);
      settings.setAutoCreateQueues(true);
      config.addAddressSetting(addressPattern, settings);

      ActiveMQServer server = createServer(true, config);
      server.start();
      return server;
   }

   private MqttClient createMqttClient(String clientId) throws MqttException {
      MqttClient client = new MqttClient("tcp://localhost:" + NETTY_PORT, clientId, new MemoryPersistence());
      MqttConnectionOptions options = new MqttConnectionOptions();
      options.setCleanStart(true);
      options.setConnectionTimeout(10);
      client.connect(options);
      return client;
   }

   private void closeMqttClient(MqttClient client) {
      try {
         if (client.isConnected()) {
            client.disconnect();
         }
      } catch (MqttException e) {
         // ignore
      }
      try {
         client.close();
      } catch (MqttException e) {
         // ignore
      }
   }

   @Test
   public void testPubAckQuotaExceededReasonCode() throws Exception {
      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("byte-quota");
      quotaConfig.setMaxMessageBytes(10L);
      ActiveMQServer server = createServerWithQuota(quotaConfig, "rc.#");

      try {
         final CountDownLatch latch = new CountDownLatch(1);
         final byte[] capturedReasonCode = new byte[1];

         MQTTInterceptor outgoingInterceptor = (packet, connection) -> {
            if (packet.fixedHeader().messageType() == MqttMessageType.PUBACK) {
               capturedReasonCode[0] = ((MqttPubReplyMessageVariableHeader) packet.variableHeader()).reasonCode();
               latch.countDown();
            }
            return true;
         };
         server.getRemotingService().addOutgoingInterceptor(outgoingInterceptor);

         MqttClient mqttClient = createMqttClient("puback-rc-client");
         try {
            mqttClient.subscribe("rc/puback", 1);

            try {
               mqttClient.publish("rc/puback", new byte[1024], 1, false);
            } catch (MqttException e) {
               // expected
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS), "PUBACK interceptor should have fired");
            assertEquals(MQTTReasonCodes.QUOTA_EXCEEDED, capturedReasonCode[0]);
         } finally {
            closeMqttClient(mqttClient);
         }
      } finally {
         server.stop();
      }
   }

   @Test
   public void testPubRecQuotaExceededReasonCode() throws Exception {
      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("byte-quota");
      quotaConfig.setMaxMessageBytes(10L);
      ActiveMQServer server = createServerWithQuota(quotaConfig, "rc.#");

      try {
         MqttClient mqttClient = createMqttClient("pubrec-rc-client");
         try {
            mqttClient.subscribe("rc/pubrec", 2);

            try {
               mqttClient.publish("rc/pubrec", new byte[1024], 2, false);
               fail("QoS 2 publish should throw MqttException when byte quota exceeded");
            } catch (MqttException e) {
               assertEquals(MQTTReasonCodes.QUOTA_EXCEEDED, (byte) e.getReasonCode());
            }
         } finally {
            closeMqttClient(mqttClient);
         }
      } finally {
         server.stop();
      }
   }

   @Test
   public void testSubAckQuotaExceededReasonCode() throws Exception {
      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("queue-quota");
      quotaConfig.setMaxQueues(1);
      ActiveMQServer server = createServerWithQuota(quotaConfig, "rc.#");

      try {
         final CountDownLatch latch = new CountDownLatch(1);
         final byte[] capturedReasonCode = new byte[1];

         MQTTInterceptor outgoingInterceptor = (packet, connection) -> {
            if (packet.fixedHeader().messageType() == MqttMessageType.SUBACK) {
               List<Integer> codes = ((MqttSubAckPayload) packet.payload()).reasonCodes();
               byte rc = codes.get(codes.size() - 1).byteValue();
               if (rc == MQTTReasonCodes.QUOTA_EXCEEDED) {
                  capturedReasonCode[0] = rc;
                  latch.countDown();
               }
            }
            return true;
         };
         server.getRemotingService().addOutgoingInterceptor(outgoingInterceptor);

         MqttClient mqttClient = createMqttClient("suback-rc-client");
         try {
            mqttClient.subscribe("rc/topic1", 1);

            try {
               mqttClient.subscribe("rc/topic2", 1);
            } catch (MqttException e) {
               // Paho 1.2.5 rejects QUOTA_EXCEEDED (0x97) in SUBACK as an unknown return code
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS), "SUBACK with QUOTA_EXCEEDED should have been sent");
            assertEquals(MQTTReasonCodes.QUOTA_EXCEEDED, capturedReasonCode[0]);
         } finally {
            closeMqttClient(mqttClient);
         }
      } finally {
         server.stop();
      }
   }
}
