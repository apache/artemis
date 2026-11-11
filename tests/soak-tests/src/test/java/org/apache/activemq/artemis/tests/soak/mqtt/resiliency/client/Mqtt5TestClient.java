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

import java.util.Optional;
import java.util.function.Consumer;

import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PublishResult;

public class Mqtt5TestClient implements MqttTestClient {

   /**
    * Session expiry (in seconds) used for persistent (non-clean) connections so that in-flight QoS 2 state survives
    * reconnects during broker restarts.
    */
   private static final long SESSION_EXPIRY_INTERVAL = 300;

   private final Mqtt5BlockingClient client;

   public Mqtt5TestClient(Mqtt5BlockingClient client) {
      this.client = client;
   }

   @Override
   public void connect() {
      client.connectWith().cleanStart(false).sessionExpiryInterval(SESSION_EXPIRY_INTERVAL).send();
   }

   @Override
   public void connectClean() {
      client.connectWith().cleanStart(true).sessionExpiryInterval(0).send();
   }

   @Override
   public void subscribe(String topicFilter, MqttQos qos) {
      client.subscribeWith().topicFilter(topicFilter).qos(qos).send();
   }

   @Override
   public void publish(String topic, MqttQos qos, byte[] payload) throws Exception {
      Mqtt5PublishResult result = client.publishWith().topic(topic).qos(qos).payload(payload).send();
      throwIfError(result.getError());
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

   private static void throwIfError(Optional<Throwable> error) throws Exception {
      if (error.isPresent()) {
         Throwable t = error.get();
         if (t instanceof Exception e) {
            throw e;
         }
         throw new RuntimeException(t);
      }
   }
}
