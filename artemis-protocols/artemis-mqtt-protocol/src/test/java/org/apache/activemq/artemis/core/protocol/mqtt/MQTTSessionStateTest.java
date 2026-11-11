/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.activemq.artemis.core.protocol.mqtt;

import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MQTTSessionStateTest {

   @Test
   public void testGenerateMqttIdOrder() {
      MQTTSessionState mqttSessionState = new MQTTSessionState(RandomUtil.randomUUIDString());
      for (int i = 1; i <= MQTTUtil.TWO_BYTE_INT_MAX; i++) {
         assertEquals(i, mqttSessionState.generatePacketId());
         mqttSessionState.putCoreDeliveryInfo(i, CoreDeliveryInfo.of(RandomUtil.randomLong(), PacketIdCorrelationKey.of(RandomUtil.randomLong(), RandomUtil.randomUUIDSimpleString())));
      }
   }

   @Test
   public void testGenerateMqttIdWithRandomAcks() {
      MQTTSessionState mqttSessionState = createMqttSessionStateWithFullOutboundStore();
      for (int i = 0; i < 10_000; i++) {
         int random = RandomUtil.randomInterval(1, MQTTUtil.TWO_BYTE_INT_MAX);
         // acknowledge a random ID
         mqttSessionState.removeCoreDeliveryInfo(random);
         // the ID that was acked is now the only one available so ensure generator finds it
         assertEquals(random, mqttSessionState.generatePacketId());
         // "publish" a new message with the ID to ensure the store is full for the next loop
         mqttSessionState.putCoreDeliveryInfo(random, CoreDeliveryInfo.of(RandomUtil.randomLong(), PacketIdCorrelationKey.of(RandomUtil.randomLong(), RandomUtil.randomUUIDSimpleString())));
      }
   }

   @Test
   public void testGenerateMqttIdExhausted() {
      MQTTSessionState mqttSessionState = createMqttSessionStateWithFullOutboundStore();
      // ensure we throw an exception when we try generate a new ID once the store is full
      assertThrows(IllegalStateException.class, () -> mqttSessionState.generatePacketId());
   }

   private static MQTTSessionState createMqttSessionStateWithFullOutboundStore() {
      MQTTSessionState mqttSessionState = new MQTTSessionState(RandomUtil.randomUUIDString());
      for (int i = 1; i <= MQTTUtil.TWO_BYTE_INT_MAX; i++) {
         mqttSessionState.putCoreDeliveryInfo(i, CoreDeliveryInfo.of(RandomUtil.randomLong(), PacketIdCorrelationKey.of(RandomUtil.randomLong(), RandomUtil.randomUUIDSimpleString())));
      }
      return mqttSessionState;
   }
}
