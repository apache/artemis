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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.netty.handler.codec.mqtt.MqttConnAckVariableHeader;
import io.netty.handler.codec.mqtt.MqttMessageType;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTInterceptor;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTReasonCodes;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTUtil;
import org.apache.activemq.artemis.core.remoting.server.impl.RemotingServiceImpl;
import org.apache.activemq.artemis.spi.core.protocol.ConnectionEntry;
import org.apache.activemq.artemis.tests.integration.mqtt5.MQTT5TestSupport;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptionsBuilder;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.packet.MqttReturnCode;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/*
 * Fulfilled by client or Netty codec (i.e. not tested here):
 *
 * [MQTT-3.2.2-1] Byte 1 is the "Connect Acknowledge Flags". Bits 7-1 are reserved and MUST be set to 0.
 * [MQTT-3.2.2-4] If the Client does not have Session State and receives Session Present set to 1 it MUST close the Network Connection.
 * [MQTT-3.2.2-5] If the Client does have Session State and receives Session Present set to 0 it MUST discard its Session State if it continues with the Network Connection.
 * [MQTT-3.2.2-11] If a Client receives a Maximum QoS from a Server, it MUST NOT send PUBLISH packets at a QoS level exceeding the Maximum QoS level specified.
 * [MQTT-3.2.2-14] A Client receiving Retain Available set to 0 from the Server MUST NOT send a PUBLISH packet with the RETAIN flag set to 1. *
 * [MQTT-3.2.2-17] The Client MUST NOT send a Topic Alias in a PUBLISH packet to the Server greater than this value.
 *
 *
 * Unsure how to test as this it's a negative:
 *
 * [MQTT-3.2.0-2] The Server MUST NOT send more than one CONNACK in a Network Connection.
 *
 *
 * Not tested because the broker supports all QoS values:
 *
 * [MQTT-3.2.2-9] If a Server does not support QoS 1 or QoS 2 PUBLISH packets it MUST send a Maximum QoS in the CONNACK packet specifying the highest QoS it supports.
 * [MQTT-3.2.2-10] A Server that does not support QoS 1 or QoS 2 PUBLISH packets MUST still accept SUBSCRIBE packets containing a Requested QoS of 0, 1 or 2.
 * [MQTT-3.2.2-12] If a Server receives a CONNECT packet containing a Will QoS that exceeds its capabilities, it MUST reject the connection. It SHOULD use a CONNACK packet with Reason Code 0x9B (QoS not supported) as described in section 4.13 Handling errors, and MUST close the Network Connection.
 *
 *
 * Not tested because the broker always supports retained messages:
 *
 * [MQTT-3.2.2-13] If a Server receives a CONNECT packet containing a Will Message with the Will Retain 1, and it does not support retained messages, the Server MUST reject the connection request. It SHOULD send CONNACK with Reason Code 0x9A (Retain not supported) and then it MUST close the Network Connection. *
 *
 *
 * The broker doesn't send any "Reason String" or "User Property" in the CONNACK packet for any reason. Therefore, these are not tested here:
 *
 * [MQTT-3.2.2-19] The Server MUST NOT send this (i.e. Reason String) property if it would increase the size of the CONNACK packet beyond the Maximum Packet Size specified by the Client.
 * [MQTT-3.2.2-20] The Server MUST NOT send this (i.e. User Property) property if it would increase the size of the CONNACK packet beyond the Maximum Packet Size specified by the Client.
 */

public class ConnAckTests extends MQTT5TestSupport {

   /*
    * [MQTT-3.1.3-6] A Server MAY allow a Client to supply a ClientID that has a length of zero bytes, however if it
    * does so the Server MUST treat this as a special case and assign a unique ClientID to that Client.
    *
    * [MQTT-3.1.3-7] It MUST then process the CONNECT packet as if the Client had provided that unique ClientID, and
    * MUST return the Assigned Client Identifier in the CONNACK packet.
    *
    * [MQTT-3.2.2-16] If the Client connects using a zero length Client Identifier, the Server MUST respond with a
    * CONNACK containing an Assigned Client Identifier. The Assigned Client Identifier MUST be a new Client Identifier
    * not used by any other Session currently in the Server.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testEmptyClientID() throws Exception {

      // no session should exist
      assertEquals(0, getSessionStates().size());

      MqttClient client = createPahoClient("");
      IMqttToken result = client.connectWithResult(null);
      assertFalse(result.getSessionPresent());
      String assignedClientID = result.getResponseProperties().getAssignedClientIdentifier();
      assertNotNull(assignedClientID);

      // session should exist
      assertEquals(1, getSessionStates().size());
      assertNotNull(getSessionStates().get(assignedClientID));

      client.disconnect();
   }

   /*
    * [MQTT-3.1.4-4] The Server MUST perform the processing of Clean Start.
    *
    * [MQTT-3.2.2-3] If the Server accepts a connection with Clean Start set to 0 and the Server has Session State for
    * the ClientID, it MUST set Session Present to 1 in the CONNACK packet, otherwise it MUST set Session Present to 0
    * in the CONNACK packet. In both cases it MUST set a 0x00 (Success) Reason Code in the CONNACK packet.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testConnackWhenCleanStartFalse() throws Exception {
      final String CONSUMER_ID = RandomUtil.randomUUIDString();

      // no session should exist
      assertEquals(0, getSessionStates().size());

      MqttClient consumer = createPahoClient(CONSUMER_ID);
      MqttConnectionOptions options = new MqttConnectionOptionsBuilder()
         .cleanStart(false)
         .sessionExpiryInterval(MQTTUtil.FOUR_BYTE_INT_MAX)
         .build();
      IMqttToken result = consumer.connectWithResult(options);
      assertFalse(result.getSessionPresent());
      assertTrue(getListOfCodes(result.getResponse().getReasonCodes()).contains(MqttReturnCode.RETURN_CODE_SUCCESS));
      consumer.disconnect();

      // session should still exist
      assertEquals(1, getSessionStates().size());
      assertNotNull(getSessionStates().get(CONSUMER_ID));

      result = consumer.connectWithResult(options);
      assertTrue(result.getSessionPresent());
      assertTrue(getListOfCodes(result.getResponse().getReasonCodes()).contains(MqttReturnCode.RETURN_CODE_SUCCESS));
   }

   /*
    * [MQTT-3.1.4-4] The Server MUST perform the processing of Clean Start.
    *
    * [MQTT-3.2.2-2] If the Server accepts a connection with Clean Start set to 1, the Server MUST set Session Present
    * to 0 in the CONNACK packet in addition to setting a 0x00 (Success) Reason Code in the CONNACK packet.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testConnackWhenCleanStartTrue() throws Exception {
      final String CONSUMER_ID = RandomUtil.randomUUIDString();

      // no session should exist
      assertEquals(0, getSessionStates().size());

      MqttClient consumer = createPahoClient(CONSUMER_ID);
      MqttConnectionOptions options = new MqttConnectionOptionsBuilder()
         .cleanStart(true)
         .build();
      IMqttToken result = consumer.connectWithResult(options);
      assertFalse(result.getSessionPresent());
      assertTrue(getListOfCodes(result.getResponse().getReasonCodes()).contains(MqttReturnCode.RETURN_CODE_SUCCESS));
      consumer.disconnect();
   }

   /*
    * [MQTT-3.1.4-4] The Server MUST perform the processing of Clean Start.
    *
    * From section 3.1.2.11.2:
    *
    * If the Session Expiry Interval is absent the value 0 is used. If it is set to 0, or is absent, the Session ends
    * when the Network Connection is closed.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testCleanStartFalseWithAbsentSessionExpiryInterval() throws Exception {
      final String CONSUMER_ID = RandomUtil.randomUUIDString();
      final String TOPIC = this.getTopicName();

      MqttClient consumer = createPahoClient(CONSUMER_ID);

      // do not set the session expiry interval
      MqttConnectionOptions options = new MqttConnectionOptionsBuilder()
         .cleanStart(false)
         .build();
      consumer.connect(options);
      consumer.subscribe(TOPIC, 2);
      consumer.disconnect();

      // session should *not* still exist since session expiry interval was absent
      assertEquals(0, getSessionStates().size());
      assertNull(getSessionStates().get(CONSUMER_ID));
   }

   /*
    * [MQTT-3.1.4-4] The Server MUST perform the processing of Clean Start.
    *
    * From section 3.1.2.11.2:
    *
    * If the Session Expiry Interval is 0xFFFFFFFF (UINT_MAX), the Session does not expire.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testCleanStartFalseWithMaxSessionExpiryInterval() throws Exception {
      final String CONSUMER_ID = RandomUtil.randomUUIDString();
      final String TOPIC = this.getTopicName();
      final long EXPIRY_INTERVAL = 2000L;

      MqttClient consumer = createPahoClient(CONSUMER_ID);
      MqttConnectionOptions options = new MqttConnectionOptionsBuilder()
         .cleanStart(false)
         .sessionExpiryInterval(MQTTUtil.FOUR_BYTE_INT_MAX)
         .build();
      consumer.connect(options);
      consumer.subscribe(TOPIC, 2);
      consumer.disconnect();

      // session should still exist since session expiry interval used the max value
      assertFalse(Wait.waitFor(() -> getSessionStates().isEmpty(), EXPIRY_INTERVAL * 2, 100));
      assertNotNull(getSessionStates().get(CONSUMER_ID));
   }

   /*
    * [MQTT-3.2.0-1] The Server MUST send a CONNACK with a 0x00 (Success) Reason Code before sending any Packet other
    * than AUTH.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testConnackSentFirst() throws Exception {
      final String CONSUMER_ID = RandomUtil.randomUUIDString();
      CountDownLatch latch = new CountDownLatch(1);
      AtomicBoolean failed = new AtomicBoolean(false);

      MQTTInterceptor outgoingInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.CONNACK) {
            latch.countDown();
         } else {
            failed.set(true);
         }
         return true;
      };
      server.getRemotingService().addOutgoingInterceptor(outgoingInterceptor);

      MqttClient consumer = createPahoClient(CONSUMER_ID);
      consumer.connect();
      assertTrue(latch.await(2, TimeUnit.SECONDS));
      assertFalse(failed.get());
      consumer.disconnect();
   }

   /*
    * [MQTT-3.2.2-6] If a Server sends a CONNACK packet containing a non-zero Reason Code it MUST set Session Present to
    * 0.
    *
    * [MQTT-3.2.2-7] If a Server sends a CONNACK packet containing a Reason code of 0x80 or greater it MUST then close
    * the Network Connection.
    *
    * This test *only* exercises one scenario where a CONNACK packet contains a non-zero Reason Code (i.e. when the
    * client ID is invalid)
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testSessionPresentWithNonZeroConnackReasonCode() throws Exception {
      CountDownLatch latch = new CountDownLatch(1);

      MQTTInterceptor outgoingInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.CONNACK) {
            assertFalse(((MqttConnAckVariableHeader)packet.variableHeader()).isSessionPresent());
            latch.countDown();
         }
         return true;
      };
      server.getRemotingService().addOutgoingInterceptor(outgoingInterceptor);

      // no session should exist
      assertEquals(0, getSessionStates().size());

      MqttClient client = createPahoClient("");
      MqttConnectionOptions options = new MqttConnectionOptionsBuilder()
         .cleanStart(false)
         .build();
      try {
         client.connect(options);
         fail("Should throw exception about invalid client identifier");
      } catch (MqttException e) {
         assertEquals(MQTTReasonCodes.CLIENT_IDENTIFIER_NOT_VALID, (byte) e.getReasonCode());
      }

      assertFalse(client.isConnected());
      assertEquals(0, getSessionStates().size());
   }

   /*
    * [MQTT-3.2.2-8] The Server sending the CONNACK packet MUST use one of the Connect Reason Code values.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testConnackReasonCode() throws Exception {
      CountDownLatch latch = new CountDownLatch(1);

      MQTTInterceptor outgoingInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.CONNACK) {
            assertNotNull(((MqttConnAckVariableHeader)packet.variableHeader()).connectReturnCode());
            latch.countDown();
         }
         return true;
      };
      server.getRemotingService().addOutgoingInterceptor(outgoingInterceptor);

      MqttClient client = createPahoClient(RandomUtil.randomUUIDString());
      client.connect();

      assertTrue(latch.await(2, TimeUnit.SECONDS));
   }

   /*
    * [MQTT-3.2.2-15] The Client MUST NOT send packets exceeding Maximum Packet Size to the Server.
    *
    * The normal use-case with a postive maximum packet size.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testMaxPacketSize() throws Exception {
      final int SIZE = 256;
      setAcceptorProperty("maximumPacketSize=" + SIZE);
      final String TOPIC = RandomUtil.randomUUIDString();
      final CountDownLatch latch = new CountDownLatch(1);
      byte[] bytes = "=".repeat(SIZE * 2).getBytes(StandardCharsets.UTF_8);

      MqttClient producer = createPahoClient(RandomUtil.randomUUIDString());
      producer.connect();
      producer.setCallback(new DefaultMqttCallback() {
         @Override
         public void disconnected(MqttDisconnectResponse disconnectResponse) {
            assertEquals(MQTTReasonCodes.PACKET_TOO_LARGE, (byte) disconnectResponse.getReturnCode());
            latch.countDown();
         }
      });

      try {
         producer.publish(TOPIC, bytes, 1, false);
         fail("Publishing should have failed with an MqttException");
      } catch (MqttException e) {
         // expected
      } catch (Exception e) {
         fail("Should have thrown an MqttException");
      }

      assertTrue(latch.await(2, TimeUnit.SECONDS));

      assertFalse(producer.isConnected());
   }

   /*
    * [MQTT-3.2.2-15] The Client MUST NOT send packets exceeding Maximum Packet Size to the Server.
    *
    * Disable maximum packet size on the broker so that it doesn't appear in the CONNACK at all.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testMaxPacketSizeNegativeOne() throws Exception {
      final int SIZE = -1;
      setAcceptorProperty("maximumPacketSize=" + SIZE);

      MqttClient client = createPahoClient(RandomUtil.randomUUIDString());
      IMqttToken result = client.connectWithResult(null);
      assertNotNull(result.getResponseProperties());
      assertNull(result.getResponseProperties().getMaximumPacketSize());
   }

   /*
    * From section 3.2.2.3.6:
    *
    *   It is a Protocol Error to include the Maximum Packet Size more than once, or for the value to be set to zero.
    *
    * I expected the Paho client to validate the returning maximumPacketSize in the CONNACK, but it doesn't. :(
    */
   @Disabled
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testMaxPacketSizeZero() throws Exception {
      final int SIZE = 0;
      setAcceptorProperty("maximumPacketSize=" + SIZE);

      MqttClient producer = createPahoClient(RandomUtil.randomUUIDString());
      try {
         producer.connect();
         fail("Connecting should have thrown an exception");
      } catch (Exception e) {
         // expected
      }

      Wait.assertFalse(() -> producer.isConnected(), 2000, 100);
   }

   /*
    * [MQTT-3.2.2-18] If Topic Alias Maximum is absent or 0, the Client MUST NOT send any Topic Aliases on to the
    * Server.
    *
    * This doesn't test whether the client actually sends topic aliases as that's up to the client
    * implementation. This just tests that the expected property value is returned to the client based on the broker's
    * setting.
    *
    * Disable topic alias maximum on the broker so that it is absent from the CONNACK.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testTopicAliasMaxNegativeOne() throws Exception {
      final int SIZE = -1;
      setAcceptorProperty("topicAliasMaximum=" + SIZE);

      MqttClient client = createPahoClient(RandomUtil.randomUUIDString());
      IMqttToken result = client.connectWithResult(null);
      assertNotNull(result.getResponseProperties());
      assertNull(result.getResponseProperties().getTopicAliasMaximum());
   }

   /*
    * [MQTT-3.2.2-18] Topic Alias Maximum is absent, the Client MUST NOT send any Topic Aliases on to the Server.
    *
    * This doesn't test whether the client actually sends topic aliases as that's up to the client
    * implementation. This just tests that the expected property value is returned to the client based on the broker's
    * setting.
    *
    * Disable topic alias maximum on the broker.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testTopicAliasMaxZero() throws Exception {
      final int SIZE = 0;
      setAcceptorProperty("topicAliasMaximum=" + SIZE);

      MqttClient client = createPahoClient(RandomUtil.randomUUIDString());
      IMqttToken result = client.connectWithResult(null);
      assertNotNull(result.getResponseProperties());
      assertEquals(0, result.getResponseProperties().getTopicAliasMaximum().intValue());
   }

   /*
    * [MQTT-3.2.2-21] If the Server sends a Server Keep Alive on the CONNACK packet, the Client MUST use this value
    * instead of the Keep Alive value the Client sent on CONNECT.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testServerKeepAlive() throws Exception {
      final int SERVER_KEEP_ALIVE = 123;
      setAcceptorProperty("serverKeepAlive=" + SERVER_KEEP_ALIVE);

      MqttClient client = createPahoClient(RandomUtil.randomUUIDString());
      MqttConnectionOptions options = new MqttConnectionOptions();
      options.setKeepAliveInterval(1234);
      IMqttToken result = client.connectWithResult(options);
      assertNotNull(result.getResponseProperties());
      assertEquals(SERVER_KEEP_ALIVE, (long) result.getResponseProperties().getServerKeepAlive());
      client.disconnect();
   }

   /*
    * [MQTT-3.2.2-22] If the Server does not send the Server Keep Alive, the Server MUST use the Keep Alive value set by
    * the Client on CONNECT.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testServerKeepAliveNegativeOne() throws Exception {
      final int KEEP_ALIVE = 1234;
      setAcceptorProperty("serverKeepAlive=-1");

      MqttClient client = createPahoClient(RandomUtil.randomUUIDString());
      MqttConnectionOptions options = new MqttConnectionOptions();
      options.setKeepAliveInterval(KEEP_ALIVE);
      IMqttToken result = client.connectWithResult(options);
      assertNull(result.getResponseProperties().getServerKeepAlive());
      boolean found = false;
      // make sure the keep-alive set by the client is used by the server (multiplied by 1500 because the client uses milliseconds instead of seconds and the value is modified by 1.5 per the spec)
      for (ConnectionEntry entry : ((RemotingServiceImpl)getServer().getRemotingService()).getConnectionEntries()) {
         assertEquals(KEEP_ALIVE * MQTTUtil.KEEP_ALIVE_ADJUSTMENT, entry.ttl);
         found = true;
      }
      assertTrue(found);
      client.disconnect();
   }

   /*
    * [MQTT-3.2.2-22] If the Server does not send the Server Keep Alive, the Server MUST use the Keep Alive value set by
    * the Client on CONNECT.
    *
    * serverKeepAlive=0 completely disables keep alives no matter the client's keep alive value.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testServerKeepAliveZero() throws Exception {
      final int SERVER_KEEP_ALIVE = 0;
      setAcceptorProperty("serverKeepAlive=" + SERVER_KEEP_ALIVE);

      MqttClient client = createPahoClient(RandomUtil.randomUUIDString());
      MqttConnectionOptions options = new MqttConnectionOptions();
      options.setKeepAliveInterval(1234);
      IMqttToken result = client.connectWithResult(options);
      assertEquals(SERVER_KEEP_ALIVE, (long) result.getResponseProperties().getServerKeepAlive());
      boolean found = false;
      for (ConnectionEntry entry : ((RemotingServiceImpl)getServer().getRemotingService()).getConnectionEntries()) {
         assertEquals(-1, entry.ttl);
         found = true;
      }
      assertTrue(found);
      client.disconnect();
   }

   /*
    * [MQTT-3.2.2-22] If the Server does not send the Server Keep Alive, the Server MUST use the Keep Alive value set by
    * the Client on CONNECT.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testServerKeepAliveWithClientKeepAliveZero() throws Exception {
      final int SERVER_KEEP_ALIVE = 123;
      setAcceptorProperty("serverKeepAlive=" + SERVER_KEEP_ALIVE);

      MqttClient client = createPahoClient(RandomUtil.randomUUIDString());
      MqttConnectionOptions options = new MqttConnectionOptions();
      options.setKeepAliveInterval(0);
      IMqttToken result = client.connectWithResult(options);
      assertEquals(SERVER_KEEP_ALIVE, (long) result.getResponseProperties().getServerKeepAlive());
      boolean found = false;
      for (ConnectionEntry entry : ((RemotingServiceImpl)getServer().getRemotingService()).getConnectionEntries()) {
         assertEquals(SERVER_KEEP_ALIVE * MQTTUtil.KEEP_ALIVE_ADJUSTMENT, entry.ttl);
         found = true;
      }
      assertTrue(found);
      client.disconnect();
   }

   private List<Integer> getListOfCodes(int[] codes) {
      return IntStream.of(codes).boxed().collect(Collectors.toList());
   }
}
