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
package org.apache.activemq.artemis.tests.integration.mqtt5;

import org.apache.activemq.artemis.core.protocol.mqtt.MQTTUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class SubscriptionQueueNameCollisionTest extends MQTT5TestSupport {

   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testCollision() throws Exception {
      final String clientID1 = "a.b";
      final String topic1 = "x/y";
      createSub(clientID1, topic1);

      final String clientID2 = "a";
      final String topic2 = "b/x/y";
      createSub(clientID2, topic2);

      assertNotEquals(getSubscriptionQueue(topic1, clientID1).getName(), getSubscriptionQueue(topic2, clientID2).getName());
   }

   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testShareCollision() throws Exception {
      final String sharedSub1 = MQTTUtil.SHARED_SUBSCRIPTION_PREFIX + "a.b/x/y";
      final String sharedSub2 = MQTTUtil.SHARED_SUBSCRIPTION_PREFIX + "a/b/x/y";

      createSub(RandomUtil.randomUUIDString(), sharedSub1);
      createSub(RandomUtil.randomUUIDString(), sharedSub2);

      assertNotEquals(getSharedSubscriptionQueue(sharedSub1).getName(), getSharedSubscriptionQueue(sharedSub2).getName());
   }

   private void createSub(String clientId, String topic) throws Exception {
      MqttClient subscriber = createPahoClient(clientId);
      subscriber.connect();
      runAfter(() -> {
         disconnectSafely(subscriber);
         subscriber.close();
      });
      subscriber.subscribe(topic, AT_LEAST_ONCE);
      Wait.assertNotNull(() -> getSubscriptionQueue(topic, clientId));
   }
}
