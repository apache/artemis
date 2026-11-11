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
package org.apache.activemq.artemis.tests.integration.mqtt5.spec.controlpackets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttPubReplyMessageVariableHeader;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTInterceptor;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTReasonCodes;
import org.apache.activemq.artemis.tests.integration.mqtt5.MQTT5TestSupport;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/*
 * Fulfilled by client or Netty codec (i.e. not tested here):
 *
 * [MQTT-3.6.1-1] Bits 3,2,1 and 0 of the Fixed Header in the PUBREL packet are reserved and MUST be set to 0,0,1 and 0 respectively. The Server MUST treat any other value as malformed and close the Network Connection.
 *
 *
 * The broker doesn't send any "Reason String" or "User Property" in the PUBREL packet for any reason. Therefore, these are not tested here:
 *
 * [MQTT-3.6.2-2] The sender MUST NOT send this Property if it would increase the size of the PUBREL packet beyond the Maximum Packet Size specified by the receiver.
 * [MQTT-3.6.2-3] The sender MUST NOT send this property if it would increase the size of the PUBREL packet beyond the Maximum Packet Size specified by the receiver.
 */

public class PubRelTests extends MQTT5TestSupport {

   /*
    * [MQTT-3.6.2-1] The Client or Server sending the PUBREL packet MUST use one of the PUBREL Reason Codes.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testPubRelReasonCode() throws Exception {
      final String TOPIC = RandomUtil.randomUUIDString();
      final CountDownLatch latch = new CountDownLatch(2);

      MQTTInterceptor outgoingInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PUBREL) {
            assertEquals(MQTTReasonCodes.SUCCESS, ((MqttPubReplyMessageVariableHeader)packet.variableHeader()).reasonCode());
            latch.countDown();
         }
         return true;
      };
      server.getRemotingService().addOutgoingInterceptor(outgoingInterceptor);

      MqttClient consumer = createPahoClient("consumer");
      consumer.connect();
      consumer.setCallback(new LatchedMqttCallback(latch));
      consumer.subscribe(TOPIC, 2);

      MqttClient publisher = createPahoClient("publisher");
      publisher.connect();
      publisher.publish(TOPIC, new byte[0], 2, false);

      assertTrue(latch.await(2, TimeUnit.SECONDS));

      publisher.disconnect();
      publisher.close();
   }
}
