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

import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.netty.handler.codec.mqtt.MqttMessageType;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTInterceptor;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTReasonCodes;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTSessionState;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTUtil;
import org.apache.activemq.artemis.tests.integration.mqtt5.MQTT5TestSupport;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptionsBuilder;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.eclipse.paho.mqttv5.common.packet.MqttReturnCode;
import org.eclipse.paho.mqttv5.common.packet.UserProperty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * Fulfilled by client or Netty codec (i.e. not tested here):
 *
 * [MQTT-3.1.0-1] After a Network Connection is established by a Client to a Server, the first packet sent from the Client to the Server MUST be a CONNECT packet. *
 * [MQTT-3.1.2-1] The protocol name MUST be the UTF-8 String "MQTT". If the Server does not want to accept the CONNECT, and wishes to reveal that it is an MQTT Server it MAY send a CONNACK packet with Reason Code of 0x84 (Unsupported Protocol Version), and then it MUST close the Network Connection.
 * [MQTT-3.1.2-3] The Server MUST validate that the reserved flag in the CONNECT packet is set to 0.
 * [MQTT-3.1.2-9] If the Will Flag is set to 1, the Will QoS and Will Retain fields in the Connect Flags will be used by the Server, and the Will Properties, Will Topic and Will Message fields MUST be present in the Payload.
 * [MQTT-3.1.2-11] If the Will Flag is set to 0, then the Will QoS MUST be set to 0 (0x00).
 * [MQTT-3.1.2-12] If the Will Flag is set to 1, the value of Will QoS can be 0 (0x00), 1 (0x01), or 2 (0x02).
 * [MQTT-3.1.2-13] If the Will Flag is set to 0, then Will Retain MUST be set to 0.
 * [MQTT-3.1.2-16] If the User Name Flag is set to 0, a User Name MUST NOT be present in the Payload.
 * [MQTT-3.1.2-17] If the User Name Flag is set to 1, a User Name MUST be present in the Payload.
 * [MQTT-3.1.2-18] If the Password Flag is set to 0, a Password MUST NOT be present in the Payload.
 * [MQTT-3.1.2-19] If the Password Flag is set to 1, a Password MUST be present in the Payload.
 * [MQTT-3.1.2-20] If Keep Alive is non-zero and in the absence of sending any other MQTT Control Packets, the Client MUST send a PINGREQ packet.
 * [MQTT-3.1.2-21] If the Server returns a Server Keep Alive on the CONNACK packet, the Client MUST use that value instead of the value it sent as the Keep Alive.
 * [MQTT-3.1.2-30] If a Client sets an Authentication Method in the CONNECT, the Client MUST NOT send any packets other than AUTH or DISCONNECT packets until it has received a CONNACK packet.
 * [MQTT-3.1.3-3] The ClientID MUST be present and is the first field in the CONNECT packet Payload.
 * [MQTT-3.1.3-4] The ClientID MUST be a UTF-8 Encoded String.
 * [MQTT-3.1.3-1] The Payload of the CONNECT packet contains one or more length-prefixed fields, whose presence is determined by the flags in the Variable Header. These fields, if present, MUST appear in the order Client Identifier, Will Topic, Will Message, User Name, Password.
 * [MQTT-3.1.3-5] The Server MUST allow ClientIDs which are between 1 and 23 UTF-8 encoded bytes in length, and that contain only the characters "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".
 * [MQTT-3.1.3-11] The Will Topic MUST be a UTF-8 Encoded String.
 * [MQTT-3.1.3-12] If the User Name Flag is set to 1, the User Name is the next field in the Payload. The User Name MUST be a UTF-8 Encoded String.
 * [MQTT-3.1.4-1] The Server MUST validate that the CONNECT packet matches the format described in section 3.1 and close the Network Connection if it does not match.
 *
 *
 * Unsure how to test these as the Paho client doesn't provide the necessary low-level control:
 *
 * [MQTT-3.1.0-2] The Server MUST process a second CONNECT packet sent from a Client as a Protocol Error and close the Network Connection.
 * [MQTT-3.1.2-2] If the Protocol Version is not 5 and the Server does not want to accept the CONNECT packet, the Server MAY send a CONNACK packet with Reason Code 0x84 (Unsupported Protocol Version) and then MUST close the Network Connection
 * [MQTT-3.1.4-6] If the Server rejects the CONNECT, it MUST NOT process any data sent by the Client after the CONNECT packet except AUTH packets.
 *
 *
 * This is not tested here because the Paho client doesn't actually expose the ability to set the "will retain" connect flag
 *
 * [MQTT-3.1.2-15] If the Will Flag is set to 1 and Will Retain is set to 1, the Server MUST publish the Will Message as a retained message.
 *
 *
 * Unsure how to test this since it's a negative:
 *
 * [MQTT-3.1.2-26] The Server MUST NOT send a Topic Alias in a PUBLISH packet to the Client greater than Topic Alias Maximum.
 *
 *
 * This is not tested as the broker *never* sends "Response Information". Its implementation is optional:
 *
 * [MQTT-3.1.2-28] A value of 0 indicates that the Server MUST NOT return Response Information.
 *
 *
 * This is not tested as the broker *never* sends Reason Strings or User Properties on any control packets. Its implementation is optional:
 *
 * [MQTT-3.1.2-29] If the value of Request Problem Information is 0, the Server MAY return a Reason String or User Properties on a CONNACK or DISCONNECT packet, but MUST NOT send a Reason String or User Properties on any packet other than PUBLISH, CONNACK, or DISCONNECT.
 */
public class ConnectTests extends MQTT5TestSupport {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   /*
    * [MQTT-3.1.2-7] If the Will Flag is set to 1 this indicates that, a Will Message MUST be stored on the Server and
    * associated with the Session.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testWillFlag() throws Exception {
      final String CLIENT_ID = RandomUtil.randomUUIDString();
      final byte[] WILL = RandomUtil.randomBytes();

      MqttClient client = createPahoClient(CLIENT_ID);
      MqttConnectionOptions options = new MqttConnectionOptionsBuilder()
         .will("/topic/foo", new MqttMessage(WILL))
         .build();
      client.connect(options);

      assertEquals(1, getSessionStates().size());
      assertNotNull(getSessionStates().get(CLIENT_ID));
      assertTrue(getSessionStates().get(CLIENT_ID).isWill());
      byte[] willMessage = new byte[getSessionStates().get(CLIENT_ID).getWillMessage().capacity()];
      getSessionStates().get(CLIENT_ID).getWillMessage().getBytes(0, willMessage);
      assertEqualsByteArrays(WILL, willMessage);

      client.disconnect();
   }

   /*
    * [MQTT-3.1.2-8] The Will Message MUST be published after the Network Connection is subsequently closed and either
    * the Will Delay Interval has elapsed or the Session ends, unless the Will Message has been deleted by the Server
    * on receipt of a DISCONNECT packet with Reason Code 0x00 (Normal disconnection) or a new Network Connection for the
    * ClientID is opened before the Will Delay Interval has elapsed.
    *
    * Publish the will message immediately after forcible disconnect when there is no session expiry or will delay
    * intervals.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testWillMessageWithNoSessionExpiryDelayAndNoWillDelay() throws Exception {
      final String CLIENT_ID_1 = RandomUtil.randomUUIDString();
      final String CLIENT_ID_2 = RandomUtil.randomUUIDString();
      final byte[] WILL = RandomUtil.randomBytes();

      // consumer of the will message
      MqttClient client1 = createPahoClient(CLIENT_ID_1);
      CountDownLatch latch = new CountDownLatch(1);
      client1.setCallback(new LatchedMqttCallback(latch));
      client1.connect();
      client1.subscribe("/topic/foo", 1);

      // consumer to generate the will
      MqttClient client2 = createPahoClient(CLIENT_ID_2);
      MqttConnectionOptions options = new MqttConnectionOptionsBuilder()
         .will("/topic/foo", new MqttMessage(WILL))
         .build();
      client2.connect(options);
      client2.disconnectForcibly(0, 0, false);

      assertTrue(latch.await(2, TimeUnit.SECONDS));
   }

   /*
    * [MQTT-3.1.2-8] The Will Message MUST be published after the Network Connection is subsequently closed and either
    * the Will Delay Interval has elapsed or the Session ends, unless the Will Message has been deleted by the Server
    * on receipt of a DISCONNECT packet with Reason Code 0x00 (Normal disconnection) or a new Network Connection for the
    * ClientID is opened before the Will Delay Interval has elapsed.
    *
    * Publish the will message after forcible disconnect when the will delay interval elapses before the session expiry
    * interval.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testWillFlagWithSessionExpiryDelayAndWillDelay() throws Exception {
      final byte[] WILL = RandomUtil.randomBytes();

      // consumer of the will message
      MqttClient client1 = createPahoClient("willConsumer");
      CountDownLatch latch = new CountDownLatch(1);
      client1.setCallback(new LatchedMqttCallback(latch));
      client1.connect();
      client1.subscribe("/topic/foo", 1);

      // consumer to generate the will
      MqttClient client2 = createPahoClient("willGenerator");
      MqttProperties willMessageProperties = new MqttProperties();
      willMessageProperties.setWillDelayInterval(1L);
      MqttConnectionOptions options = new MqttConnectionOptionsBuilder()
         .sessionExpiryInterval(4L)
         .will("/topic/foo", new MqttMessage(WILL))
         .build();
      options.setWillMessageProperties(willMessageProperties);
      client2.connect(options);
      client2.disconnectForcibly(0, 0, false);
      long start = System.currentTimeMillis();
      assertTrue(latch.await(3, TimeUnit.SECONDS));
      assertTrue(System.currentTimeMillis() - start >= 1000);
   }

   /*
    * [MQTT-3.1.2-8] The Will Message MUST be published after the Network Connection is subsequently closed and either
    * the Will Delay Interval has elapsed or the Session ends, unless the Will Message has been deleted by the Server
    * on receipt of a DISCONNECT packet with Reason Code 0x00 (Normal disconnection) or a new Network Connection for the
    * ClientID is opened before the Will Delay Interval has elapsed.
    *
    * Publish the will message immediately on disconnect even though there's a will delay since there's no session
    * expiry interval.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testWillFlagWithNoSessionExpiryDelayAndWillDelay() throws Exception {
      final byte[] WILL = RandomUtil.randomBytes();

      // consumer of the will message
      MqttClient client1 = createPahoClient("willConsumer");
      CountDownLatch latch = new CountDownLatch(1);
      client1.setCallback(new LatchedMqttCallback(latch));
      client1.connect();
      client1.subscribe("/topic/foo", 1);

      // consumer to generate the will
      MqttClient client2 = createPahoClient("willGenerator");
      MqttProperties willMessageProperties = new MqttProperties();
      willMessageProperties.setWillDelayInterval(1L);
      MqttConnectionOptions options = new MqttConnectionOptionsBuilder()
         .will("/topic/foo", new MqttMessage(WILL))
         .build();
      options.setWillMessageProperties(willMessageProperties);
      client2.connect(options);
      client2.disconnectForcibly(0, 0, false);
      assertTrue(latch.await(500, TimeUnit.MILLISECONDS));
   }

   /*
    * [MQTT-3.1.2-8] The Will Message MUST be published after the Network Connection is subsequently closed and either
    * the Will Delay Interval has elapsed or the Session ends, unless the Will Message has been deleted by the Server
    * on receipt of a DISCONNECT packet with Reason Code 0x00 (Normal disconnection) or a new Network Connection for the
    * ClientID is opened before the Will Delay Interval has elapsed.
    *
    * Publish the will message when the session expiry interval elapses even though the will delay interval hasn't
    * elapsed.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testWillFlagWithShorterSessionExpiryDelayThanWillDelay() throws Exception {
      final byte[] WILL = RandomUtil.randomBytes();

      // consumer of the will message
      MqttClient client1 = createPahoClient("willConsumer");
      CountDownLatch latch = new CountDownLatch(1);
      client1.setCallback(new LatchedMqttCallback(latch));
      client1.connect();
      client1.subscribe("/topic/foo", 1);

      // consumer to generate the will
      MqttClient client2 = createPahoClient("willGenerator");
      MqttProperties willMessageProperties = new MqttProperties();
      willMessageProperties.setWillDelayInterval(2L);
      MqttConnectionOptions options = new MqttConnectionOptionsBuilder()
         .sessionExpiryInterval(1L)
         .will("/topic/foo", new MqttMessage(WILL))
         .build();
      options.setWillMessageProperties(willMessageProperties);
      client2.connect(options);
      client2.disconnectForcibly(0, 0, false);
      assertTrue(latch.await(1500, TimeUnit.MILLISECONDS));
   }

   /*
    * [MQTT-3.1.2-8] The Will Message MUST be published after the Network Connection is subsequently closed and either
    * the Will Delay Interval has elapsed or the Session ends, unless the Will Message has been deleted by the Server
    * on receipt of a DISCONNECT packet with Reason Code 0x00 (Normal disconnection) or a new Network Connection for the
    * ClientID is opened before the Will Delay Interval has elapsed.
    *
    * [MQTT-3.1.3-9] If a new Network Connection to this Session is made before the Will Delay Interval has passed, the
    * Server MUST NOT send the Will Message.
    *
    * Do not publish will message if client reconnects within will delay interval assuming the session expiry interval
    * hasn't elapsed.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testWillFlagWithSessionExpiryDelayAndWillDelayWithReconnect() throws Exception {
      final byte[] WILL = RandomUtil.randomBytes();

      // consumer of the will message
      MqttClient client1 = createPahoClient("willConsumer");
      CountDownLatch latch = new CountDownLatch(1);
      client1.setCallback(new LatchedMqttCallback(latch));
      client1.connect();
      client1.subscribe("/topic/foo", 1);

      // consumer to generate the will
      MqttClient client2 = createPahoClient("willGenerator");
      MqttProperties willMessageProperties = new MqttProperties();
      willMessageProperties.setWillDelayInterval(2L);
      MqttConnectionOptions options = new MqttConnectionOptionsBuilder()
         .sessionExpiryInterval(2L)
         .will("/topic/foo", new MqttMessage(WILL))
         .build();
      options.setWillMessageProperties(willMessageProperties);
      client2.connect(options);
      client2.disconnectForcibly(0, 0, false);
      options = new MqttConnectionOptionsBuilder()
         .cleanStart(false)
         .will("/topic/foo", new MqttMessage(WILL))
         .build();
      client2.connect(options);
      assertFalse(latch.await(2, TimeUnit.SECONDS));
      client2.disconnect();
   }

   /*
    * [MQTT-3.1.2-8] The Will Message MUST be published after the Network Connection is subsequently closed and either
    * the Will Delay Interval has elapsed or the Session ends, unless the Will Message has been deleted by the Server
    * on receipt of a DISCONNECT packet with Reason Code 0x00 (Normal disconnection) or a new Network Connection for the
    * ClientID is opened before the Will Delay Interval has elapsed.
    *
    * Do not publish will message if client disconnects normally.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testWillFlagWithDisconnect() throws Exception {
      final byte[] WILL = RandomUtil.randomBytes();

      // consumer of the will message
      MqttClient client1 = createPahoClient("willConsumer");
      CountDownLatch latch = new CountDownLatch(1);
      client1.setCallback(new LatchedMqttCallback(latch));
      client1.connect();
      client1.subscribe("/topic/foo", 1);

      // consumer to generate the will
      MqttClient client2 = createPahoClient("willGenerator");
      MqttConnectionOptions options = new MqttConnectionOptionsBuilder()
         .will("/topic/foo", new MqttMessage(WILL))
         .build();
      client2.connect(options);
      client2.disconnect();
      assertFalse(latch.await(2, TimeUnit.SECONDS));
   }

   /*
    * [MQTT-3.1.2-10] The Will Message MUST be removed from the stored Session State in the Server once it has been
    * published or the Server has received a DISCONNECT packet with a Reason Code of 0x00 (Normal disconnection) from
    * the Client.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testWillMessageRemovedOnceSent() throws Exception {
      final String WILL_CONSUMER = RandomUtil.randomUUIDString();
      final String WILL_SENDER = RandomUtil.randomUUIDString();
      final byte[] WILL = RandomUtil.randomBytes();

      // consumer of the will message
      MqttClient willConsumer = createPahoClient(WILL_CONSUMER);
      willConsumer.connect();
      willConsumer.subscribe("/topic/foo", 1);

      // consumer to generate the will
      MqttClient willSender = createPahoClient(WILL_SENDER);
      MqttProperties willMessageProperties = new MqttProperties();
      willMessageProperties.setWillDelayInterval(1L);
      MqttConnectionOptions options = new MqttConnectionOptionsBuilder()
         .sessionExpiryInterval(5L)
         .will("/topic/foo", new MqttMessage(WILL))
         .cleanStart(false)
         .build();
      options.setWillMessageProperties(willMessageProperties);

      // ensure the will message is sent & removed on each disconnect even after reconnection to the same session
      for (int i = 0; i < 5; i++) {
         willSender.connect(options);
         MQTTSessionState state = getSessionStates().get(WILL_SENDER);
         assertNotNull(state);
         assertNotNull(state.getWillMessage());
         CountDownLatch latch = new CountDownLatch(1);
         willConsumer.setCallback(new LatchedMqttCallback(latch));
         willSender.disconnectForcibly(0, 0, false);

         assertTrue(latch.await(2, TimeUnit.SECONDS));
         assertNull(state.getWillMessage());
      }

      willSender.close();
      willConsumer.disconnect();
      willConsumer.close();
   }

   /*
    * [MQTT-3.1.2-10] The Will Message MUST be removed from the stored Session State in the Server once it has been
    * published or the Server has received a DISCONNECT packet with a Reason Code of 0x00 (Normal disconnection) from
    * the Client.
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
   }

   /*
    * [MQTT-3.1.2-14] If the Will Flag is set to 1 and Will Retain is set to 0, the Server MUST publish the Will Message
    * as a non-retained message.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testWillFlagWithRetain() throws Exception {
      final String CLIENT_ID = RandomUtil.randomUUIDString();
      final byte[] WILL = RandomUtil.randomBytes();

      MqttClient client = createPahoClient(CLIENT_ID);
      MqttConnectionOptions options = new MqttConnectionOptions();
      options.setWill("/topic/foo", new MqttMessage(WILL));
      MqttProperties willProperties = new MqttProperties();
      options.setWillMessageProperties(willProperties);
      client.connect(options);

      assertEquals(1, getSessionStates().size());
      assertNotNull(getSessionStates().get(CLIENT_ID));
      assertTrue(getSessionStates().get(CLIENT_ID).isWill());
      assertFalse(getSessionStates().get(CLIENT_ID).isWillRetain());

      client.disconnect();
   }

   /*
    * [MQTT-3.1.2-22] If the Keep Alive value is non-zero and the Server does not receive an MQTT Control Packet from
    * the Client within one and a half times the Keep Alive time period, it MUST close the Network Connection to the
    * Client as if the network had failed.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testKeepAlive() throws Exception {
      MQTTInterceptor incomingInterceptor = (packet, connection) -> {
         if (packet.fixedHeader().messageType() == MqttMessageType.PINGREQ) {
            try {
               logger.info("Caught PING so sleeping...");
               Thread.sleep(3000);
            } catch (InterruptedException e) {
               // ignore
            }
         }
         return true;
      };
      server.getRemotingService().addIncomingInterceptor(incomingInterceptor);

      MqttClient client = createPahoClient(RandomUtil.randomUUIDString());
      MqttConnectionOptions options = new MqttConnectionOptions();
      options.setKeepAliveInterval(1);
      client.connect(options);

      Wait.assertEquals(0, () -> server.getConnectionCount(), 2000, 100);
   }

   /*
    * [MQTT-3.1.2-24] The Server MUST NOT send packets exceeding Maximum Packet Size to the Client.
    *
    * [MQTT-3.1.2-25] Where a Packet is too large to send, the Server MUST discard it without sending it and then behave
    * as if it had completed sending that Application Message.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testMaxPacketSizeQoS2() throws Exception {
      testMaxPacketSize(2);
   }

   /*
    * [MQTT-3.1.2-24] The Server MUST NOT send packets exceeding Maximum Packet Size to the Client.
    *
    * [MQTT-3.1.2-25] Where a Packet is too large to send, the Server MUST discard it without sending it and then behave
    * as if it had completed sending that Application Message.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testMaxPacketSizeQoS1() throws Exception {
      testMaxPacketSize(1);
   }

   /*
    * [MQTT-3.1.2-24] The Server MUST NOT send packets exceeding Maximum Packet Size to the Client.
    *
    * [MQTT-3.1.2-25] Where a Packet is too large to send, the Server MUST discard it without sending it and then behave
    * as if it had completed sending that Application Message.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testMaxPacketSizeQoS0() throws Exception {
      testMaxPacketSize(0);
   }

   private void testMaxPacketSize(int qos) throws Exception {
      final String CONSUMER_ID = RandomUtil.randomUUIDString();
      final String TOPIC = this.getTopicName();
      final long SIZE = 1500;
      final byte[] bytes = new byte[(int) SIZE];

      for (int i = 0; i < SIZE * 2; i++) {
         bytes[0] = 0x00;
      }

      final CountDownLatch latch = new CountDownLatch(1);
      MqttClient consumer = createPahoClient(CONSUMER_ID);
      MqttConnectionOptions options = new MqttConnectionOptionsBuilder()
         .maximumPacketSize(SIZE)
         .build();
      consumer.setCallback(new LatchedMqttCallback(latch));
      consumer.connect(options);
      consumer.subscribe(TOPIC, qos);

      MqttClient producer = createPahoClient(RandomUtil.randomUUIDString());
      producer.connect();
      producer.publish(TOPIC, bytes, qos, false);
      producer.disconnect();
      producer.close();
      Wait.assertEquals(1L, () -> getSubscriptionQueue(TOPIC, CONSUMER_ID).getMessagesAdded(), 2000, 100);

      // the client should *not* receive the message
      assertFalse(latch.await(2, TimeUnit.SECONDS));

      // the broker should acknowledge the message since it exceeded the client's max packet size
      Wait.assertEquals(1L, () -> getSubscriptionQueue(TOPIC, CONSUMER_ID).getMessagesAcknowledged(), 2000, 100);
      Wait.assertEquals(0L, () -> getSubscriptionQueue(TOPIC, CONSUMER_ID).getMessageCount(), 2000, 100);
      Wait.assertEquals(0, () -> getProtocolManager().getStateManager().getPacketIdCorrelationSize(CONSUMER_ID), 2000, 100);
      Wait.assertNull(() -> getPubRecCache(CONSUMER_ID), 2000, 100);
      consumer.disconnect();
      consumer.close();
   }

   /*
    * [MQTT-3.1.2-27] If Topic Alias Maximum is absent or zero, the Server MUST NOT send any Topic Aliases to the
    * Client.
    *
    * Explicitly set topic alias maximum to 0 on the client.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testTopicAliasDisabledOnClient() throws Exception {
      testTopicAliasOnClient(true);
   }

   /*
    * [MQTT-3.1.2-27] If Topic Alias Maximum is absent or zero, the Server MUST NOT send any Topic Aliases to the
    * Client.
    *
    * Do not define the topic alias maximum on the client.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testTopicAliasAbsentOnClient() throws Exception {
      testTopicAliasOnClient(true);
   }

   private void testTopicAliasOnClient(boolean disabled) throws Exception {
      final String CONSUMER_ID = RandomUtil.randomUUIDString();
      final String TOPIC = this.getTopicName();
      final int MESSAGE_COUNT = 25;

      MqttClient consumer = createPahoClient(CONSUMER_ID);
      MqttConnectionOptions options = null;
      if (disabled) {
         options = new MqttConnectionOptionsBuilder()
            .topicAliasMaximum(0)
            .build();
      }
      consumer.connect(options);
      CountDownLatch latch = new CountDownLatch(MESSAGE_COUNT);
      consumer.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            assertNull(message.getProperties().getTopicAlias());
            latch.countDown();
         }
      });
      consumer.subscribe(TOPIC + "/#", 2);

      MqttClient producer = createPahoClient("producer");
      producer.connect();
      for (int i = 0; i < MESSAGE_COUNT; i++) {
         producer.publish(TOPIC + "/" + i, ("foo" + i).getBytes(StandardCharsets.UTF_8), 2, false);
      }
      producer.disconnect();
      producer.close();

      assertTrue(latch.await(2, TimeUnit.SECONDS));
      consumer.disconnect();
   }

   /*
    * [MQTT-3.1.3-10] The Server MUST maintain the order of User Properties when forwarding the Application Message.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testUserPropertiesOrder() throws Exception {
      final String CONSUMER_ID = RandomUtil.randomUUIDString();
      final String TOPIC = this.getTopicName();
      final int USER_PROPERTY_COUNT = 10;
      List<UserProperty> userProperties = new ArrayList<>();
      for (int i = 0; i < USER_PROPERTY_COUNT; i++) {
         userProperties.add(new UserProperty(RandomUtil.randomUUIDString(), RandomUtil.randomUUIDString()));
      }

      final CountDownLatch latch = new CountDownLatch(1);
      MqttClient consumer = createPahoClient(CONSUMER_ID);
      consumer.connect();
      consumer.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            List<UserProperty> receivedUserProperties = message.getProperties().getUserProperties();
            for (int i = 0; i < USER_PROPERTY_COUNT; i++) {
               assertEquals(userProperties.get(i).getKey(), receivedUserProperties.get(i).getKey());
               assertEquals(userProperties.get(i).getValue(), receivedUserProperties.get(i).getValue());
            }
            latch.countDown();
         }
      });
      consumer.subscribe(TOPIC, 2);

      MqttClient producer = createPahoClient("producer");
      producer.connect();
      MqttMessage m = new MqttMessage();
      m.setQos(2);
      MqttProperties properties = new MqttProperties();
      properties.setUserProperties(userProperties);
      m.setProperties(properties);
      producer.publish(TOPIC, m);
      producer.disconnect();
      producer.close();

      assertTrue(latch.await(2, TimeUnit.SECONDS));
      consumer.disconnect();
      consumer.close();
   }

   /*
    * [MQTT-3.1.3-2] The ClientID MUST be used by Clients and by Servers to identify state that they hold relating to
    * this MQTT Session between the Client and the Server.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testClientID() throws Exception {
      final String CLIENT_ID = RandomUtil.randomUUIDString();

      // no session should exist
      assertEquals(0, getSessionStates().size());

      MqttClient client = createPahoClient(CLIENT_ID);
      client.connect();

      // session should exist
      assertEquals(1, getSessionStates().size());
      assertNotNull(getSessionStates().get(CLIENT_ID));

      client.disconnect();
   }

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
    * [MQTT-3.1.3-8] If the Server rejects the ClientID it MAY respond to the CONNECT packet with a CONNACK using Reason
    * Code 0x85 (Client Identifier not valid) as described in section 4.13 Handling errors, and then it MUST close the
    * Network Connection.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testEmptyClientIDWithoutCleanStart() throws Exception {
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
    * [MQTT-3.1.4-2] The Server MAY check that the contents of the CONNECT packet meet any further restrictions and
    * SHOULD perform authentication and authorization checks. If any of these checks fail, it MUST close the Network
    * Connection.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testAuthenticationFailure() throws Exception {
      final String CLIENT_ID = RandomUtil.randomUUIDString();

      MqttClient client = createPahoClient(CLIENT_ID);
      MqttConnectionOptions options = new MqttConnectionOptionsBuilder()
         .username("bad")
         .password("bad".getBytes(StandardCharsets.UTF_8))
         .build();
      client.connect(options);
      client.disconnect();
   }

   /*
    * [MQTT-3.1.4-3] If the ClientID represents a Client already connected to the Server, the Server sends a DISCONNECT
    * packet to the existing Client with Reason Code of 0x8E (Session taken over) as described in section 4.13 and MUST
    * close the Network Connection of the existing Client.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testConnectionStealing() throws Exception {
      final String CLIENT_ID = RandomUtil.randomUUIDString();

      MqttClient client = createPahoClient(CLIENT_ID);
      client.connect();
      final int[] reasonCode = new int[1];
      CountDownLatch disconnectedLatch = new CountDownLatch(1);
      client.setCallback(new LatchedMqttCallback(disconnectedLatch) {
         @Override
         public void disconnected(MqttDisconnectResponse disconnectResponse) {
            reasonCode[0] = disconnectResponse.getReturnCode();
            disconnectedLatch.countDown();
         }

         @Override
         public void mqttErrorOccurred(MqttException exception) {
            exception.printStackTrace();
         }
      });

      MqttClient client2 = createPahoClient(CLIENT_ID);
      client2.connect();

      assertTrue(disconnectedLatch.await(500, TimeUnit.MILLISECONDS));
      assertEquals(MQTTReasonCodes.SESSION_TAKEN_OVER, (byte) reasonCode[0]);

      // only 1 session should exist
      assertEquals(1, getSessionStates().size());
      assertNotNull(getSessionStates().get(CLIENT_ID));

      assertFalse(client.isConnected());

      client2.disconnect();
   }

   /*
    * [MQTT-3.1.4-3] If the ClientID represents a Client already connected to the Server, the Server sends a DISCONNECT
    * packet to the existing Client with Reason Code of 0x8E (Session taken over) as described in section 4.13 and MUST
    * close the Network Connection of the existing Client.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testConnectionStealingBy3_1_1() throws Exception {
      final String CLIENT_ID = RandomUtil.randomUUIDString();

      MqttClient client = createPahoClient(CLIENT_ID);
      client.connect();
      final int[] reasonCode = new int[1];
      CountDownLatch disconnectedLatch = new CountDownLatch(1);
      client.setCallback(new LatchedMqttCallback(disconnectedLatch) {
         @Override
         public void disconnected(MqttDisconnectResponse disconnectResponse) {
            reasonCode[0] = disconnectResponse.getReturnCode();
            disconnectedLatch.countDown();
         }

         @Override
         public void mqttErrorOccurred(MqttException exception) {
            exception.printStackTrace();
         }
      });

      org.eclipse.paho.client.mqttv3.MqttClient client2 = createPaho3_1_1Client(CLIENT_ID);
      client2.connect();

      assertTrue(disconnectedLatch.await(500, TimeUnit.MILLISECONDS));
      assertEquals(MQTTReasonCodes.SESSION_TAKEN_OVER, (byte) reasonCode[0]);

      // only 1 session should exist
      assertEquals(1, getSessionStates().size());
      assertNotNull(getSessionStates().get(CLIENT_ID));

      assertFalse(client.isConnected());

      client2.disconnect();
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

   private List<Integer> getListOfCodes(int[] codes) {
      return IntStream.of(codes).boxed().collect(Collectors.toList());
   }

   /*
    * [MQTT-3.1.4-4] The Server MUST perform the processing of Clean Start.
    *
    * [MQTT-3.1.2-6] If a CONNECT packet is received with Clean Start set to 0 and there is no Session associated with
    * the Client Identifier, the Server MUST create a new Session.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testCleanStartFalse() throws Exception {
      final String CONSUMER_ID = RandomUtil.randomUUIDString();

      // no session should exist
      assertEquals(0, getSessionStates().size());

      MqttClient consumer = createPahoClient(CONSUMER_ID);
      MqttConnectionOptions options = new MqttConnectionOptionsBuilder()
         .cleanStart(false)
         .build();
      consumer.connect(options);

      // session should still exist since session expiry interval > 0
      assertEquals(1, getSessionStates().size());
      assertNotNull(getSessionStates().get(CONSUMER_ID));

      consumer.disconnect();
   }

   /*
    * [MQTT-3.1.4-4] The Server MUST perform the processing of Clean Start.
    *
    * [MQTT-3.1.2-5] If a CONNECT packet is received with Clean Start set to 0 and there is a Session associated with
    * the Client Identifier, the Server MUST resume communications with the Client based on state from the existing
    * Session.
    *
    * [MQTT-3.1.2-23] The Client and Server MUST store the Session State after the Network Connection is closed if
    * the Session Expiry Interval is greater than 0.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testCleanStartFalseWithReconnect() throws Exception {
      final String CONSUMER_ID = RandomUtil.randomUUIDString();
      final String TOPIC = this.getTopicName();

      final CountDownLatch latch = new CountDownLatch(1);
      MqttClient producer = createPahoClient(RandomUtil.randomUUIDString());
      MqttClient consumer = createPahoClient(CONSUMER_ID);
      MqttConnectionOptions options = new MqttConnectionOptionsBuilder()
         .cleanStart(false)
         .sessionExpiryInterval(300L)
         .build();
      consumer.connect(options);
      consumer.subscribe(TOPIC, 2);
      consumer.disconnect();

      // session should still exist since session expiry interval > 0
      assertEquals(1, getSessionStates().size());
      assertNotNull(getSessionStates().get(CONSUMER_ID));

      producer.connect();
      producer.publish(TOPIC, "hello".getBytes(), 2, false);
      producer.disconnect();
      producer.close();

      // session should still exist since session expiry interval > 0
      assertEquals(1, getSessionStates().size());
      assertNotNull(getSessionStates().get(CONSUMER_ID));

      // consumer should resume previous session (i.e. get the messages sent to the queue where it was previously subscribed)
      consumer.setCallback(new LatchedMqttCallback(latch));
      consumer.connect(options);
      waitForLatch(latch);
      consumer.disconnect();
      consumer.close();

      // session should still exist since session expiry interval > 0
      assertEquals(1, getSessionStates().size());
      assertNotNull(getSessionStates().get(CONSUMER_ID));
   }

   /*
    * [MQTT-3.1.4-4] The Server MUST perform the processing of Clean Start.
    *
    * [MQTT-3.1.2-5] If a CONNECT packet is received with Clean Start set to 0 and there is a Session associated with
    * the Client Identifier, the Server MUST resume communications with the Client based on state from the existing
    * Session.
    *
    * [MQTT-4.1.0-2] The Server MUST discard the Session State when the Network Connection is closed and the Session
    * Expiry Interval has passed.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testCleanStartFalseWith0SessionExpiryInterval() throws Exception {
      final String CONSUMER_ID = RandomUtil.randomUUIDString();
      final String TOPIC = this.getTopicName();

      MqttClient consumer = createPahoClient(CONSUMER_ID);
      MqttConnectionOptions options = new MqttConnectionOptionsBuilder()
         .cleanStart(false)
         .sessionExpiryInterval(0L)
         .build();
      consumer.connect(options);
      consumer.subscribe(TOPIC, 2);
      consumer.disconnect();

      // session should *not* still exist since session expiry interval = 0
      assertEquals(0, getSessionStates().size());
      assertNull(getSessionStates().get(CONSUMER_ID));
   }

   /*
    * [MQTT-3.1.4-4] The Server MUST perform the processing of Clean Start.
    *
    * [MQTT-3.1.2-5] If a CONNECT packet is received with Clean Start set to 0 and there is a Session associated with
    * the Client Identifier, the Server MUST resume communications with the Client based on state from the existing
    * Session.
    *
    * [MQTT-4.1.0-2] The Server MUST discard the Session State when the Network Connection is closed and the Session
    * Expiry Interval has passed.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testCleanStartFalseWithNon0SessionExpiryInterval() throws Exception {
      final String CONSUMER_ID = RandomUtil.randomUUIDString();
      final String TOPIC = this.getTopicName();
      final long EXPIRY_INTERVAL = 2L;

      MqttClient consumer = createPahoClient(CONSUMER_ID);
      MqttConnectionOptions options = new MqttConnectionOptionsBuilder()
         .cleanStart(false)
         .sessionExpiryInterval(EXPIRY_INTERVAL)
         .build();
      consumer.connect(options);
      consumer.subscribe(TOPIC, 2);
      long start = System.currentTimeMillis();
      consumer.disconnect();

      // ensure the subscription queue still exists since the session hasn't expired
      assertNotNull(getSubscriptionQueue(TOPIC, CONSUMER_ID));

      Wait.assertEquals(0, () -> getSessionStates().size(), EXPIRY_INTERVAL * 1000 * 2, 100);
      assertTrue(System.currentTimeMillis() - start >= (EXPIRY_INTERVAL * 1000));

      // session should *not* still exist since session expiry interval has passed
      assertNull(getSessionStates().get(CONSUMER_ID));

      // ensure the subscription queue is cleaned up when the session expires
      Wait.assertTrue(() -> getSubscriptionQueue(TOPIC, CONSUMER_ID) == null, 2000, 100);
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
      long start = System.currentTimeMillis();
      assertFalse(Wait.waitFor(() -> getSessionStates().isEmpty(), EXPIRY_INTERVAL * 2, 100));
      assertNotNull(getSessionStates().get(CONSUMER_ID));
   }

   /*
    * [MQTT-3.1.4-4] The Server MUST perform the processing of Clean Start.
    *
    * [MQTT-3.1.2-4] If a CONNECT packet is received with Clean Start is set to 1, the Client and Server MUST discard any existing Session and start a new Session.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testCleanStartTrue() throws Exception {
      final String CONSUMER_ID = RandomUtil.randomUUIDString();
      final String TOPIC = this.getTopicName();

      final CountDownLatch latch = new CountDownLatch(1);
      MqttClient producer = createPahoClient(RandomUtil.randomUUIDString());
      MqttClient consumer = createPahoClient(CONSUMER_ID);

      // start with clean start = false and a non-zero session expiry interval to ensure the session stays on the broker
      MqttConnectionOptions options = new MqttConnectionOptionsBuilder()
         .cleanStart(false)
         .sessionExpiryInterval(MQTTUtil.FOUR_BYTE_INT_MAX)
         .build();
      consumer.connect(options);
      consumer.subscribe(TOPIC, 2);
      consumer.disconnect();

      // session should still exist since session expiry interval > 0
      assertEquals(1, getSessionStates().size());
      assertNotNull(getSessionStates().get(CONSUMER_ID));

      producer.connect();
      producer.publish(TOPIC, "hello".getBytes(), 2, false);
      producer.disconnect();
      producer.close();

      /*
       * consumer should *not* resume previous session (i.e. get the messages sent to the queue where it was previously
       * subscribed) since clean start = true
       */
      options.setCleanStart(true);
      consumer.setCallback(new LatchedMqttCallback(latch));
      consumer.connect(options);
      assertFalse(latch.await(3, TimeUnit.SECONDS));
      consumer.disconnect();
      consumer.close();
   }
}
