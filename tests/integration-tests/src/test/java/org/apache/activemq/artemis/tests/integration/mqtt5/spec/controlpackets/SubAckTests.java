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

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.tests.integration.mqtt5.MQTT5TestSupport;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.eclipse.paho.mqttv5.common.packet.MqttSubAck;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/*
 * The broker doesn't send any "Reason String" or "User Property" in the SUBACK packet for any reason. Therefore, these are not tested here:
 *
 * [MQTT-3.9.2-1] The Server MUST NOT send this Property if it would increase the size of the SUBACK packet beyond the Maximum Packet Size specified by the Client.
 * [MQTT-3.9.2-2] The Server MUST NOT send this property if it would increase the size of the SUBACK packet beyond the Maximum Packet Size specified by the Client.
 */

public class SubAckTests extends MQTT5TestSupport {

   /**
    * [MQTT-3.9.3-1] The order of Reason Codes in the SUBACK packet MUST match the order of Topic Filters in the
    * SUBSCRIBE packet.
    *
    * [MQTT-3.9.3-2] The Server sending the SUBACK packet MUST send one of the Subscribe Reason Code values for each
    * Topic Filter received.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testSubscribeAck() throws Exception {
      final int SUBSCRIPTION_COUNT = 30;
      final String TOPIC = RandomUtil.randomUUIDString();
      SimpleString[] topicNames = new SimpleString[SUBSCRIPTION_COUNT];
      for (int i = 0; i < SUBSCRIPTION_COUNT; i++) {
         topicNames[i] = SimpleString.of(i + "-" + TOPIC);
      }

      MqttAsyncClient consumer = createAsyncPahoClient("consumer");
      consumer.connect().waitForCompletion();
      MqttSubscription[] subscriptions = new MqttSubscription[SUBSCRIPTION_COUNT];
      for (int i = 0; i < SUBSCRIPTION_COUNT; i++) {
         subscriptions[i] = new MqttSubscription(topicNames[i].toString(), RandomUtil.randomInterval(0, 3));
      }
      IMqttToken token = consumer.subscribe(subscriptions);
      token.waitForCompletion();

      MqttSubAck response = (MqttSubAck) token.getResponse();
      assertEquals(subscriptions.length, response.getReturnCodes().length);
      for (int i = 0; i < response.getReturnCodes().length; i++) {
         assertEquals(subscriptions[i].getQos(), response.getReturnCodes()[i]);
      }

      for (int i = 0; i < SUBSCRIPTION_COUNT; i++) {
         assertTrue(server.getPostOffice().isAddressBound(topicNames[i]));
      }

      consumer.disconnect();
      consumer.close();
   }
}
