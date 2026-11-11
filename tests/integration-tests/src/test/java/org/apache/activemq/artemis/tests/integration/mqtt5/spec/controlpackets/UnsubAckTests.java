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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTReasonCodes;
import org.apache.activemq.artemis.tests.integration.mqtt5.MQTT5TestSupport;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.eclipse.paho.mqttv5.common.packet.MqttUnsubAck;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/*
 * The broker doesn't send any "Reason String" or "User Property" in the UNSUBACK packet for any reason. Therefore, these are not tested here:
 *
 * [MQTT-3.11.2-1] The Server MUST NOT send this Property if it would increase the size of the UNSUBACK packet beyond the Maximum Packet Size specified by the Client.
 * [MQTT-3.11.2-2] The Server MUST NOT send this property if it would increase the size of the UNSUBACK packet beyond the Maximum Packet Size specified by the receiver.
 */

public class UnsubAckTests extends MQTT5TestSupport {

   /*
    * [MQTT-3.11.3-1] The order of Reason Codes in the UNSUBACK packet MUST match the order of Topic Filters in the
    * UNSUBSCRIBE packet.
    *
    * [MQTT-3.11.3-2] The Server sending the UNSUBACK packet MUST use one of the UNSUBSCRIBE Reason Code values for each
    * Topic Filter received.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testUnsubscribeAck() throws Exception {
      final int SUBSCRIPTION_COUNT = 10;
      final String TOPIC = RandomUtil.randomUUIDString();
      SimpleString[] topicNames = new SimpleString[SUBSCRIPTION_COUNT];
      for (int i = 0; i < SUBSCRIPTION_COUNT; i++) {
         topicNames[i] = SimpleString.of(i + "-" + TOPIC);
      }

      MqttAsyncClient consumer = createAsyncPahoClient("consumer");
      consumer.connect().waitForCompletion();
      MqttSubscription[] subscriptions = new MqttSubscription[SUBSCRIPTION_COUNT];
      for (int i = 0; i < SUBSCRIPTION_COUNT; i++) {
         subscriptions[i] = new MqttSubscription(topicNames[i].toString(), 0);
      }
      consumer.subscribe(subscriptions).waitForCompletion();

      for (int i = 0; i < SUBSCRIPTION_COUNT; i++) {
         assertTrue(server.getPostOffice().isAddressBound(topicNames[i]));
      }

      // unsubscribe from all the real subscriptions interleaved with subscriptions that *don't exist*
      String[] unsubTopicNames = new String[SUBSCRIPTION_COUNT * 2];
      for (int i = 0; i <= SUBSCRIPTION_COUNT; i++) {
         if (i != SUBSCRIPTION_COUNT) {
            unsubTopicNames[i * 2] = topicNames[i].toString();
         }
         if (i != 0) {
            unsubTopicNames[(i * 2) - 1] = RandomUtil.randomUUIDString();
         }
      }
      IMqttToken token = consumer.unsubscribe(unsubTopicNames);
      token.waitForCompletion();
      for (int i = 0; i < SUBSCRIPTION_COUNT; i++) {
         assertFalse(server.getPostOffice().isAddressBound(topicNames[i]));
      }

      MqttUnsubAck response = (MqttUnsubAck) token.getResponse();
      assertEquals(unsubTopicNames.length, response.getReturnCodes().length);
      for (int i = 0; i < response.getReturnCodes().length; i++) {
         if (i % 2 == 0) {
            assertEquals(MQTTReasonCodes.SUCCESS, response.getReturnCodes()[i]);
         } else {
            assertEquals(MQTTReasonCodes.NO_SUBSCRIPTION_EXISTED, response.getReturnCodes()[i]);
         }
      }
      System.out.println(response);

      consumer.disconnect();
      consumer.close();
   }
}
