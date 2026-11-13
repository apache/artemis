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

import java.io.IOException;
import java.net.URI;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.settings.impl.ResourceQuota;
import org.apache.activemq.artemis.core.settings.impl.ResourceQuotaConfig;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpMessage;
import org.apache.activemq.transport.amqp.client.AmqpReceiver;
import org.apache.activemq.transport.amqp.client.AmqpSender;
import org.apache.activemq.transport.amqp.client.AmqpSession;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Verifies that resource quotas (bytes, addresses, queues) are enforced
 * for AMQP and MQTT clients, not just the CORE protocol.
 */
public class MultiProtocolByteQuotaTest extends ActiveMQTestBase {

   private static final int MESSAGE_SIZE = 1024;
   private static final long BYTE_QUOTA_LIMIT = 50 * 1024L;
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

   // --- Byte quota tests ---

   @Test
   public void testAmqpSenderBytesQuota() throws Exception {
      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("test-quota");
      quotaConfig.setMaxMessageBytes(BYTE_QUOTA_LIMIT);
      ActiveMQServer server = createServerWithQuota(quotaConfig, "quotaBytes.#");

      try {
         SimpleString address = SimpleString.of("quotaBytes.amqp");
         server.addAddressInfo(new AddressInfo(address, RoutingType.ANYCAST));
         server.createQueue(QueueConfiguration.of(address).setAddress(address).setRoutingType(RoutingType.ANYCAST));

         AmqpClient client = new AmqpClient(new URI("tcp://127.0.0.1:" + NETTY_PORT), null, null);
         AmqpConnection connection = client.connect();

         try {
            AmqpSession session = connection.createSession();
            AmqpSender sender = session.createSender(address.toString());

            boolean rejected = false;
            int messagesSent = 0;

            for (int i = 0; i < 100; i++) {
               AmqpMessage message = new AmqpMessage();
               message.setBytes(new byte[MESSAGE_SIZE]);
               try {
                  sender.send(message);
                  messagesSent++;
               } catch (IOException e) {
                  rejected = true;
                  break;
               }
            }

            Assertions.assertTrue(rejected,
               "AMQP sender should be rejected when byte quota exceeded. Sent " + messagesSent + " messages.");
            Assertions.assertTrue(messagesSent > 0,
               "Should have sent at least some messages before quota limit");

            ResourceQuota quota = server.getResourceQuotaService().getQuotaByName("test-quota");
            Assertions.assertNotNull(quota);
            Assertions.assertTrue(quota.getCurrentMessageBytes() > 0, "Quota should have tracked bytes");
         } finally {
            connection.close();
         }
      } finally {
         server.stop();
      }
   }

   @Test
   public void testMqttSenderBytesQuota() throws Exception {
      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("test-quota");
      quotaConfig.setMaxMessageBytes(BYTE_QUOTA_LIMIT);
      ActiveMQServer server = createServerWithQuota(quotaConfig, "quotaBytes.#");

      try {
         MqttClient mqttClient = createMqttClient("quota-test-client");
         try {
            mqttClient.subscribe("quotaBytes/mqtt", 1);

            for (int i = 0; i < 100; i++) {
               try {
                  mqttClient.publish("quotaBytes/mqtt", new byte[MESSAGE_SIZE], 1, false);
               } catch (MqttException e) {
                  // expect puback with QUOTA reason code but seems to be ignored
                  break;
               }
            }

            String queueName = "quota-test-client.quotaBytes.mqtt";
            long queueCount = server.locateQueue(SimpleString.of(queueName)).getMessageCount();
            Assertions.assertTrue(queueCount > 0, "Some messages should be stored before quota limit");
            Assertions.assertTrue(queueCount < 80, "Quota should limit stored messages, but queue has " + queueCount);
         } finally {
            closeMqttClient(mqttClient);
         }
      } finally {
         server.stop();
      }
   }

   // --- Address quota tests ---

   @Test
   public void testAmqpAddressQuota() throws Exception {
      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("addr-quota");
      quotaConfig.setMaxAddresses(2);
      ActiveMQServer server = createServerWithQuota(quotaConfig, "addrQuota.#");

      try {
         AmqpClient client = new AmqpClient(new URI("tcp://127.0.0.1:" + NETTY_PORT), null, null);
         AmqpConnection connection = client.connect();

         try {
            AmqpSession session = connection.createSession();

            AmqpSender sender1 = session.createSender("addrQuota.addr1");
            Assertions.assertNotNull(sender1, "First sender should attach successfully");

            AmqpSender sender2 = session.createSender("addrQuota.addr2");
            Assertions.assertNotNull(sender2, "Second sender should attach successfully");

            boolean rejected = false;
            try {
               session.createSender("addrQuota.addr3");
            } catch (Exception expected) {
               rejected = true;
            }

            Assertions.assertTrue(rejected, "AMQP sender attach should fail when address quota exceeded");
            Assertions.assertTrue(server.getResourceQuotaService().getQuotaByName("addr-quota").isAddressLimitReached());
         } finally {
            connection.close();
         }
      } finally {
         server.stop();
      }
   }

   @Test
   public void testMqttAddressQuota() throws Exception {
      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("addr-quota");
      quotaConfig.setMaxAddresses(2);
      ActiveMQServer server = createServerWithQuota(quotaConfig, "addrQuota.#");

      try {
         server.addAddressInfo(new AddressInfo(SimpleString.of("addrQuota.addr1"), RoutingType.MULTICAST));
         server.addAddressInfo(new AddressInfo(SimpleString.of("addrQuota.addr2"), RoutingType.MULTICAST));

         MqttClient mqttClient = createMqttClient("addr-quota-client");
         try {
            try {
               mqttClient.publish("addrQuota/addr3", new byte[10], 1, false);
            } catch (MqttException e) {
               // expected but not forthcoming from paho
            }

            Assertions.assertNull(server.getAddressInfo(SimpleString.of("addrQuota.addr3")),
               "Third address should not be created - quota exceeded");
            Assertions.assertEquals(2, server.getResourceQuotaService().getQuotaByName("addr-quota").getAddressCount());
         } finally {
            closeMqttClient(mqttClient);
         }
      } finally {
         server.stop();
      }
   }

   // --- Queue quota tests ---

   @Test
   public void testAmqpQueueQuota() throws Exception {
      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("queue-quota");
      quotaConfig.setMaxQueues(2);
      ActiveMQServer server = createServerWithQuota(quotaConfig, "queueQuota.#");

      try {
         AmqpClient client = new AmqpClient(new URI("tcp://127.0.0.1:" + NETTY_PORT), null, null);
         AmqpConnection connection = client.connect();

         try {
            AmqpSession session = connection.createSession();

            AmqpReceiver receiver1 = session.createReceiver("queueQuota.addr1");
            Assertions.assertNotNull(receiver1, "First receiver should attach successfully");

            AmqpReceiver receiver2 = session.createReceiver("queueQuota.addr2");
            Assertions.assertNotNull(receiver2, "Second receiver should attach successfully");

            boolean rejected = false;
            try {
               session.createReceiver("queueQuota.addr3");
            } catch (Exception e) {
               rejected = true;
            }

            Assertions.assertTrue(rejected, "AMQP receiver attach should fail when queue quota exceeded");
         } finally {
            connection.close();
         }
      } finally {
         server.stop();
      }
   }

   @Test
   public void testMqttQueueQuota() throws Exception {
      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("queue-quota");
      quotaConfig.setMaxQueues(2);
      ActiveMQServer server = createServerWithQuota(quotaConfig, "queueQuota.#");

      try {
         MqttClient mqttClient = createMqttClient("queue-quota-client");
         try {
            mqttClient.subscribe("queueQuota/topic1", 1);
            mqttClient.subscribe("queueQuota/topic2", 1);

            try {
               mqttClient.subscribe("queueQuota/topic3", 1);
            } catch (MqttException expected) {
               // expected
            }

            ResourceQuota quota = server.getResourceQuotaService().getQuotaByName("queue-quota");
            Assertions.assertNotNull(quota);
            Assertions.assertTrue(quota.getCurrentQueueCount() <= 2,
               "Queue count should not exceed quota limit, got " + quota.getCurrentQueueCount());
         } finally {
            closeMqttClient(mqttClient);
         }
      } finally {
         server.stop();
      }
   }
}
