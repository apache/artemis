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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttReasonCodeAndPropertiesVariableHeader;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTInterceptor;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTReasonCodes;
import org.apache.activemq.artemis.tests.integration.mqtt5.MQTT5TestSupport;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptionsBuilder;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/*
 * Fulfilled by client or Netty codec (i.e. not tested here):
 *
 * [MQTT-3.14.1-1] The Client or Server MUST validate that reserved bits are set to 0. If they are not zero it sends a DISCONNECT packet with a Reason code of 0x81 (Malformed Packet).
 * [MQTT-3.14.4-1] After sending a DISCONNECT packet the sender MUST NOT send any more MQTT Control Packets on that Network Connection.
 * [MQTT-3.14.4-2] After sending a DISCONNECT packet the sender MUST close the Network Connection.
 *
 *
 * Not sure how to test this since it's a negative:
 *
 * [MQTT-3.14.0-1] A Server MUST NOT send a DISCONNECT until after it has sent a CONNACK with Reason Code of less than 0x80.
 *
 *
 * The broker doesn't send any "Reason String" or "User Property" in the DISCONNECT packet for any reason. Therefore, these are not tested here:
 *
 * [MQTT-3.14.2-3] The sender MUST NOT use this Property if it would increase the size of the DISCONNECT packet beyond the Maximum Packet Size specified by the receiver.
 * [MQTT-3.14.2-4] The sender MUST NOT send this property if it would increase the size of the DISCONNECT packet beyond the Maximum Packet Size specified by the receiver.
 */

public class DisconnectTests extends MQTT5TestSupport {

   /*
    * [MQTT-3.14.2-1] The Client or Server sending the DISCONNECT packet MUST use one of the DISCONNECT Reason Codes.
    *
    * [MQTT-3.14.2-2] The Session Expiry Interval MUST NOT be sent on a DISCONNECT by the Server.
    *
    * Currently the only way to trigger a DISCONNECT from the broker is to "take over" an existing session at which
    * point the broker will disconnect the existing session.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testDisconnectReasonCode() throws Exception {
      final CountDownLatch latch = new CountDownLatch(1);

      MQTTInterceptor outgoingInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.DISCONNECT) {
            System.out.println(packet);
            assertEquals(MQTTReasonCodes.SESSION_TAKEN_OVER, ((MqttReasonCodeAndPropertiesVariableHeader)packet.variableHeader()).reasonCode());
            assertNull(((MqttReasonCodeAndPropertiesVariableHeader)packet.variableHeader()).properties().getProperty(MqttProperties.MqttPropertyType.SESSION_EXPIRY_INTERVAL.value()));
            latch.countDown();
         }
         return true;
      };
      server.getRemotingService().addOutgoingInterceptor(outgoingInterceptor);

      MqttClient publisher1 = createPahoClient("publisher");
      publisher1.connect();
      MqttClient publisher2 = createPahoClient("publisher");
      publisher2.connect();

      assertTrue(latch.await(2, TimeUnit.SECONDS));

      publisher2.disconnect();
      publisher2.close();
   }

   /*
    * [MQTT-3.14.4-3] On receipt of DISCONNECT with a Reason Code of 0x00 (Success) the Server MUST discard any Will
    * Message associated with the current Connection without publishing it.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testWillMessageRemovedOnDisconnect() throws Exception {
      final String CLIENT_ID = RandomUtil.randomUUIDString();
      final byte[] WILL = RandomUtil.randomBytes();

      MqttClient client = createPahoClient(CLIENT_ID);
      MqttConnectionOptions options = new MqttConnectionOptionsBuilder()
         .will("/topic/foo", new MqttMessage(WILL))
         .build();
      client.connect(options);

      assertNotNull(getSessionStates().get(CLIENT_ID).getWillMessage());

      client.disconnect();

      // normal disconnect removes all session state if session expiration interval is 0
      assertNull(getSessionStates().get(CLIENT_ID));

      client.close();
   }
}
