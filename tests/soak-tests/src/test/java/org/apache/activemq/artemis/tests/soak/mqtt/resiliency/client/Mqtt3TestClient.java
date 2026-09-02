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
package org.apache.activemq.artemis.tests.soak.mqtt.resiliency.client;

import java.util.function.Consumer;

import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.Mqtt3BlockingClient;

public class Mqtt3TestClient implements MqttTestClient {

   private final Mqtt3BlockingClient client;

   public Mqtt3TestClient(Mqtt3BlockingClient client) {
      this.client = client;
   }

   @Override
   public void connect() {
      client.connectWith().cleanSession(false).send();
   }

   @Override
   public void connectClean() {
      client.connectWith().cleanSession(true).send();
   }

   @Override
   public void subscribe(String topicFilter, MqttQos qos) {
      client.subscribeWith().topicFilter(topicFilter).qos(qos).send();
   }

   @Override
   public void publish(String topic, MqttQos qos, byte[] payload) {
      // the MQTT 3 blocking publish returns void and throws directly if the broker reports an error
      client.publishWith().topic(topic).qos(qos).payload(payload).send();
   }

   @Override
   public void onMessage(Consumer<byte[]> handler) {
      client.toAsync().publishes(MqttGlobalPublishFilter.ALL, publish -> handler.accept(publish.getPayloadAsBytes()));
   }

   @Override
   public void disconnect() {
      client.disconnect();
   }

   @Override
   public String getClientId() {
      return client.getConfig().getClientIdentifier().get().toString();
   }

   @Override
   public boolean isConnected() {
      return client.getConfig().getState().isConnected();
   }
}
