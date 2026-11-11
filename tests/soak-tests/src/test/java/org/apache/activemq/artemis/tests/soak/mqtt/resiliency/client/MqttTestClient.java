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

import com.hivemq.client.mqtt.datatypes.MqttQos;

/**
 * A thin, version-agnostic wrapper around the HiveMQ blocking client so that the QoS 2 resiliency soak tests can run
 * against both MQTT 3.1.1 and MQTT 5 without duplicating the test bodies. Only the handful of operations that actually
 * differ between the two protocol versions are abstracted here; everything else in the tests works off the broker-side
 * state keyed by client id.
 */
public interface MqttTestClient {

   /**
    * Connect with a persistent (non-clean) session so that in-flight QoS 2 state survives reconnects.
    * <p>
    * For MQTT 5 this maps to {@code cleanStart(false)} with a non-zero session expiry interval; for MQTT 3.1.1 it maps
    * to {@code cleanSession(false)}.
    */
   void connect();

   /**
    * Connect with a clean session, discarding any prior session state.
    * <p>
    * For MQTT 5 this maps to {@code cleanStart(true)} with a session expiry interval of 0; for MQTT 3.1.1 it maps to
    * {@code cleanSession(true)}.
    */
   void connectClean();

   void subscribe(String topicFilter, MqttQos qos);

   /**
    * Publish a message and throw if the broker reported an error for it.
    */
   void publish(String topic, MqttQos qos, byte[] payload) throws Exception;

   /**
    * Register a callback invoked with the raw payload of every message delivered to this client.
    */
   void onMessage(Consumer<byte[]> handler);

   void disconnect();

   String getClientId();

   boolean isConnected();
}
