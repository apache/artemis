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
package org.apache.activemq.artemis.tests.integration.plugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.integration.mqtt.MQTTClientProvider;
import org.apache.activemq.artemis.tests.integration.mqtt.MQTTTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_ADD_ADDRESS;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_ADD_BINDING;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_CLOSE_CONSUMER;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_CLOSE_SESSION;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_CREATE_CONNECTION;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_CREATE_CONSUMER;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_CREATE_QUEUE;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_CREATE_SESSION;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_DELIVER;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_DEPLOY_BRIDGE;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_DESTROY_CONNECTION;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_MESSAGE_ROUTE;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_REMOVE_ADDRESS;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_REMOVE_BINDING;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_SEND;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_ADD_ADDRESS;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_ADD_BINDING;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_CLOSE_CONSUMER;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_CLOSE_SESSION;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_CREATE_CONSUMER;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_CREATE_QUEUE;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_CREATE_SESSION;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_DELIVER;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_DEPLOY_BRIDGE;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_MESSAGE_ROUTE;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_REMOVE_ADDRESS;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_REMOVE_BINDING;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_SEND;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.MESSAGE_ACKED;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.MESSAGE_EXPIRED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class MqttPluginTest extends MQTTTestSupport {


   private final Map<String, AtomicInteger> methodCalls = new ConcurrentHashMap<>();
   private final MethodCalledVerifier verifier = new MethodCalledVerifier(methodCalls);

   @Override
   public void configureBroker() throws Exception {
      super.configureBroker();
      server.registerBrokerPlugin(verifier);
      server.getConfiguration().setAddressQueueScanPeriod(100);

      AddressSettings addressSettings = new AddressSettings();
      addressSettings.setAutoDeleteQueues(true).setAutoDeleteAddresses(true);

      server.getAddressSettingsRepository().addMatch("#", addressSettings);
   }

   @Test
   @Timeout(60)
   public void testSendAndReceiveMQTT() throws Exception {
      final MQTTClientProvider subscriptionProvider = getMQTTClientProvider();
      initializeConnection(subscriptionProvider);

      subscriptionProvider.subscribe("foo/bah", AT_MOST_ONCE);

      final CountDownLatch latch = new CountDownLatch(NUM_MESSAGES);

      Thread thread = new Thread(() -> {
         for (int i = 0; i < NUM_MESSAGES; i++) {
            try {
               byte[] payload = subscriptionProvider.receive(10000);
               assertNotNull(payload, "Should get a message");
               latch.countDown();
            } catch (Exception e) {
               e.printStackTrace();
               break;
            }

         }
      });
      thread.start();

      final MQTTClientProvider publishProvider = getMQTTClientProvider();
      initializeConnection(publishProvider);

      for (int i = 0; i < NUM_MESSAGES; i++) {
         String payload = "Message " + i;
         publishProvider.publish("foo/bah", payload.getBytes(), AT_LEAST_ONCE);
      }

      latch.await(10, TimeUnit.SECONDS);
      assertEquals(0, latch.getCount());
      subscriptionProvider.disconnect();
      publishProvider.disconnect();

      verifier.validatePluginMethodsEquals(0, MESSAGE_EXPIRED, BEFORE_DEPLOY_BRIDGE, AFTER_DEPLOY_BRIDGE);
      verifier.validatePluginMethodsAtLeast(1, AFTER_CREATE_CONNECTION, AFTER_DESTROY_CONNECTION, BEFORE_CREATE_SESSION,
            AFTER_CREATE_SESSION, BEFORE_CLOSE_SESSION, AFTER_CLOSE_SESSION, BEFORE_CREATE_CONSUMER,
            AFTER_CREATE_CONSUMER, BEFORE_CLOSE_CONSUMER, AFTER_CLOSE_CONSUMER, BEFORE_CREATE_QUEUE, AFTER_CREATE_QUEUE,
            MESSAGE_ACKED, BEFORE_SEND, AFTER_SEND, BEFORE_MESSAGE_ROUTE, AFTER_MESSAGE_ROUTE, BEFORE_DELIVER,
            AFTER_DELIVER, BEFORE_ADD_ADDRESS, AFTER_ADD_ADDRESS, BEFORE_ADD_BINDING, AFTER_ADD_BINDING,
            BEFORE_REMOVE_BINDING, AFTER_REMOVE_BINDING);
   }

   @Test
   @Timeout(60)
   public void testMQTTAutoCreate() throws Exception {
      final MQTTClientProvider subscriptionProvider = getMQTTClientProvider();
      initializeConnection(subscriptionProvider);

      subscriptionProvider.subscribe("foo/bah", AT_MOST_ONCE);

      subscriptionProvider.disconnect();

      verifier.validatePluginMethodsAtLeast(1,
            BEFORE_ADD_ADDRESS, AFTER_ADD_ADDRESS, BEFORE_REMOVE_ADDRESS, AFTER_REMOVE_ADDRESS);
   }

}
