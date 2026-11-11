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

   /**
    * This test verifies that when a client sends a session expiry interval in the DISCONNECT packet, that value is
    * properly persisted so that it survives a broker restart.
    * <p>
    * Note: The Paho client doesn't support sending session expiry interval in DISCONNECT, so this test uses an incoming
    * interceptor to inject the property into the DISCONNECT packet.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testSessionExpiryIntervalOnDisconnectPersisted() throws Exception {
      final String CLIENT_ID = RandomUtil.randomUUIDString();
      final long CONNECT_SESSION_EXPIRY = 300L;
      final long DISCONNECT_SESSION_EXPIRY = 600L;

      MQTTInterceptor incomingInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.DISCONNECT) {
            MqttReasonCodeAndPropertiesVariableHeader header = (MqttReasonCodeAndPropertiesVariableHeader) packet.variableHeader();
            header.properties().add(new MqttProperties.IntegerProperty(MqttProperties.MqttPropertyType.SESSION_EXPIRY_INTERVAL.value(), (int) DISCONNECT_SESSION_EXPIRY));
         }
         return true;
      };
      server.getRemotingService().addIncomingInterceptor(incomingInterceptor);

      MqttClient client = createPahoClient(CLIENT_ID);
      MqttConnectionOptions options = new MqttConnectionOptionsBuilder()
         .sessionExpiryInterval(CONNECT_SESSION_EXPIRY)
         .build();
      client.connect(options);

      assertNotNull(getSessionStates().get(CLIENT_ID));
      assertEquals(CONNECT_SESSION_EXPIRY, getSessionStates().get(CLIENT_ID).getClientSessionExpiryInterval());

      client.disconnect();

      assertNotNull(getSessionStates().get(CLIENT_ID), "Session should exist after disconnect with non-zero expiry");
      assertEquals(DISCONNECT_SESSION_EXPIRY, getSessionStates().get(CLIENT_ID).getClientSessionExpiryInterval(), "Session expiry from DISCONNECT should be persisted");

      client.close();

      server.stop();
      waitForServerToStop(server);
      server.start();
      waitForServerToStart(server);

      assertNotNull(getSessionStates().get(CLIENT_ID), "Session should survive broker restart");
      assertEquals(DISCONNECT_SESSION_EXPIRY, getSessionStates().get(CLIENT_ID).getClientSessionExpiryInterval(), "Session expiry from DISCONNECT should survive restart");
   }

   /*
    * Section 3.14.2.2.2 states, "If the Session Expiry Interval in the CONNECT packet was zero, then it is a Protocol
    * Error to set a non-zero Session Expiry Interval in the DISCONNECT packet sent by the Client. If such a non-zero
    * Session Expiry Interval is received by the Server, it does not treat it as a valid DISCONNECT packet. The Server
    * uses DISCONNECT with Reason Code 0x82 (Protocol Error)..."
    * <p>
    * This test verifies that the broker properly rejects the protocol error by sending DISCONNECT with reason code 0x82
    * (Protocol Error).
    * <p>
    * Note: The Paho client doesn't support sending session expiry interval in DISCONNECT, so this test uses an incoming
    * interceptor to inject the property into the DISCONNECT packet.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testSessionExpiryIntervalProtocolError() throws Exception {
      final String CLIENT_ID = RandomUtil.randomUUIDString();
      final long INVALID_DISCONNECT_SESSION_EXPIRY = 300L;
      final CountDownLatch protocolErrorLatch = new CountDownLatch(1);

      MQTTInterceptor incomingInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.DISCONNECT) {
            MqttReasonCodeAndPropertiesVariableHeader header = (MqttReasonCodeAndPropertiesVariableHeader) packet.variableHeader();
            header.properties().add(new MqttProperties.IntegerProperty(MqttProperties.MqttPropertyType.SESSION_EXPIRY_INTERVAL.value(), (int) INVALID_DISCONNECT_SESSION_EXPIRY));
         }
         return true;
      };

      MQTTInterceptor outgoingInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.DISCONNECT) {
            MqttReasonCodeAndPropertiesVariableHeader header = (MqttReasonCodeAndPropertiesVariableHeader) packet.variableHeader();
            if (header.reasonCode() == MQTTReasonCodes.PROTOCOL_ERROR) {
               protocolErrorLatch.countDown();
            }
         }
         return true;
      };

      server.getRemotingService().addIncomingInterceptor(incomingInterceptor);
      server.getRemotingService().addOutgoingInterceptor(outgoingInterceptor);

      MqttClient client = createPahoClient(CLIENT_ID);
      client.connect();

      assertNotNull(getSessionStates().get(CLIENT_ID));
      assertEquals(0, getSessionStates().get(CLIENT_ID).getClientSessionExpiryInterval(), "Session expiry should be 0 from CONNECT");

      client.disconnect();

      assertTrue(protocolErrorLatch.await(2, TimeUnit.SECONDS), "Broker should send DISCONNECT with Protocol Error reason code");
      assertNull(getSessionStates().get(CLIENT_ID), "Session should be cleaned up since session expiry was 0");

      client.close();
   }

   /**
    * This test verifies that when a client overrides a non-zero session expiry interval with zero in the DISCONNECT
    * packet, the session is cleaned up immediately rather than persisting for the original duration.
    * <p>
    * Note: The Paho client doesn't support sending session expiry interval in DISCONNECT, so this test uses an incoming
    * interceptor to inject the property into the DISCONNECT packet.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testSessionExpiryIntervalZeroOnDisconnect() throws Exception {
      final String CLIENT_ID = RandomUtil.randomUUIDString();
      final long CONNECT_SESSION_EXPIRY = 300L;
      final long DISCONNECT_SESSION_EXPIRY = 0L;

      MQTTInterceptor incomingInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.DISCONNECT) {
            MqttReasonCodeAndPropertiesVariableHeader header = (MqttReasonCodeAndPropertiesVariableHeader) packet.variableHeader();
            header.properties().add(new MqttProperties.IntegerProperty(MqttProperties.MqttPropertyType.SESSION_EXPIRY_INTERVAL.value(), (int) DISCONNECT_SESSION_EXPIRY));
         }
         return true;
      };
      server.getRemotingService().addIncomingInterceptor(incomingInterceptor);

      MqttClient client = createPahoClient(CLIENT_ID);
      MqttConnectionOptions options = new MqttConnectionOptionsBuilder()
         .sessionExpiryInterval(CONNECT_SESSION_EXPIRY)
         .build();
      client.connect(options);

      assertNotNull(getSessionStates().get(CLIENT_ID));
      assertEquals(CONNECT_SESSION_EXPIRY, getSessionStates().get(CLIENT_ID).getClientSessionExpiryInterval());

      client.disconnect();

      assertNull(getSessionStates().get(CLIENT_ID), "Session should be cleaned up immediately when session expiry is set to 0 in DISCONNECT");

      client.close();
   }
}
