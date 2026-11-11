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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.LinkedList;

import org.apache.activemq.artemis.tests.util.Wait;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.jgroups.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class MqttAcknowledgementTest extends MQTTTestSupport {

   private volatile LinkedList<Integer> messageIds = new LinkedList<>();
   private volatile boolean messageArrived = false;

   private MqttClient subscriber;
   private MqttClient sender;

   @AfterEach
   public void clean() throws MqttException {
      messageArrived = false;
      messageIds.clear();
      if (subscriber.isConnected()) {
         subscriber.disconnect();
      }
      if (sender.isConnected()) {
         sender.disconnect();
      }
      subscriber.close();
      sender.close();
   }

   @Test
   @Timeout(60)
   public void testAcknowledgementQOS1() throws Exception {
      test(1);
   }

   @Test
   @Timeout(60)
   public void testAcknowledgementQOS0() throws Exception {
      assertThrows(AssertionError.class, () -> {
         test(0);
      });
   }

   private void test(int qos) throws Exception {
      String subscriberId = UUID.randomUUID().toString();
      String senderId = UUID.randomUUID().toString();
      String topic = UUID.randomUUID().toString();

      subscriber = createMqttClient(subscriberId);
      subscriber.subscribe(topic, qos);

      sender = createMqttClient(senderId);
      sender.publish(topic, UUID.randomUUID().toString().getBytes(), qos, false);
      sender.publish(topic, UUID.randomUUID().toString().getBytes(), qos, false);

      boolean satisfied = Wait.waitFor(() -> messageIds.size() == 2, 5_000);
      if (!satisfied) {
         fail();
      }

      subscriber.messageArrivedComplete(messageIds.getLast(), qos);
      subscriber.disconnect();
      subscriber.close();
      messageArrived = false;

      satisfied = Wait.waitFor(() -> {
         try {
            subscriber = createMqttClient(subscriberId);
            return true;
         } catch (MqttException e) {
            return false;
         }
      }, 60_000);
      if (!satisfied) {
         fail();
      }

      satisfied = Wait.waitFor(() -> messageArrived == true, 5_000);
      if (!satisfied) {
         fail();
      }
   }

   private MqttClient createMqttClient(String clientId) throws MqttException {
      MqttClient client = new MqttClient("tcp://localhost:" + getPort(), clientId, new MemoryPersistence());
      client.setCallback(createCallback());
      client.setManualAcks(true);
      MqttConnectOptions options = new MqttConnectOptions();
      options.setCleanSession(false);
      client.connect(options);
      return client;
   }

   private MqttCallback createCallback() {
      return new MqttCallback() {

         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            messageIds.add(message.getId());
            messageArrived = true;
         }

         @Override
         public void deliveryComplete(IMqttDeliveryToken token) {
         }

         @Override
         public void connectionLost(Throwable cause) {
         }
      };
   }
}
