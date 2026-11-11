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

package org.apache.activemq.artemis.tests.soak.mqtt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.management.SimpleManagement;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTUtil;
import org.apache.activemq.artemis.tests.soak.SoakTestBase;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MQTT5SoakTest extends SoakTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final String SERVER_NAME_0 = "mqtt";



   @BeforeAll
   public static void createServers() throws Exception {
      {
         File serverLocation = getFileServerLocation(SERVER_NAME_0);
         deleteDirectory(serverLocation);

         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer.setRole("amq").setUser("artemis").setPassword("artemis").setAllowAnonymous(true).setNoWeb(false).setArtemisInstance(serverLocation);
         cliCreateServer.setArgs("--addresses", "MQTT_SOAK");
         cliCreateServer.createServer();
      }
   }


   Process serverProcess;

   protected MqttClient createPahoClient(String clientId) throws MqttException {
      return new MqttClient("tcp://localhost:1883", clientId, new MemoryPersistence());
   }

   @BeforeEach
   public void before() throws Exception {
      cleanupData(SERVER_NAME_0);

      serverProcess = startServer(SERVER_NAME_0, 0, 10_000);
   }

   // https://issues.apache.org/jira/browse/ARTEMIS-1184
   @Test
   public void testMaxMessageSize() throws Throwable {

      final String TOPIC = "MQTT_SOAK";
      final int SIZE = MQTTUtil.MAX_PACKET_SIZE - 48;
      AtomicInteger errors = new AtomicInteger(0);

      final CountDownLatch latch = new CountDownLatch(1);

      MqttClient consumer = createPahoClient("consumer");
      consumer.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            byte[] payload = message.getPayload();
            if (payload.length != SIZE) {
               logger.error("payload.length != {}", SIZE);
               errors.incrementAndGet();
            }
            for (int i = 0; i < payload.length; i++) {
               if (payload[i] != (byte)'=') {
                  logger.error("unexpected byte {} at position {} on received payload", payload[i], i);
                  errors.incrementAndGet();
               }
            }
            latch.countDown();
         }

         @Override
         public void connectComplete(boolean reconnect, String serverURI) {
         }

         @Override
         public void mqttErrorOccurred(MqttException exception) {
            exception.printStackTrace();
         }
      });
      consumer.connect();
      consumer.subscribe(TOPIC, 1);

      MqttClient producer = createPahoClient("producer");
      producer.connect();

      { // using a little context to make sure the bytes reference goes away asap saving some memory
         byte[] bytes = new byte[SIZE];
         Arrays.fill(bytes, (byte) '=');
         producer.publish(TOPIC, bytes, 1, false);
      }

      assertTrue(latch.await(30, TimeUnit.SECONDS));

      SimpleManagement simpleManagement = new SimpleManagement("tcp://localhost:61616", null, null);
      Wait.assertEquals(1, () -> simpleManagement.getMessagesAddedOnQueue("consumer.MQTT_SOAK"), 5000);


      consumer.disconnect();

      producer.disconnect();
      producer.close();

      consumer.close();

      assertEquals(0, errors.get());
   }

   protected interface DefaultMqttCallback extends MqttCallback {

      @Override
      default void disconnected(MqttDisconnectResponse disconnectResponse) {
      }

      @Override
      default void mqttErrorOccurred(MqttException exception) {
      }

      @Override
      default void messageArrived(String topic, org.eclipse.paho.mqttv5.common.MqttMessage message) throws Exception {
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
