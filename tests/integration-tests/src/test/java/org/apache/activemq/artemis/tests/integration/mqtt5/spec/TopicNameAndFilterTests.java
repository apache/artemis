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
package org.apache.activemq.artemis.tests.integration.mqtt5.spec;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.EnumSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.tests.integration.mqtt5.MQTT5TestSupport;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/*
 * Fulfilled by client or Netty codec (i.e. not tested here):
 *
 * [MQTT-4.7.0-1] The wildcard characters can be used in Topic Filters, but MUST NOT be used within a Topic Name.
 * [MQTT-4.7.1-1] The multi-level wildcard character MUST be specified either on its own or following a topic level separator. In either case it MUST be the last character specified in the Topic Filter.
 * [MQTT-4.7.1-2] The single-level wildcard can be used at any level in the Topic Filter, including first and last levels. Where it is used, it MUST occupy an entire level of the filter.
 * [MQTT-4.7.3-1] All Topic Names and Topic Filters MUST be at least one character long.
 * [MQTT-4.7.3-2] Topic Names and Topic Filters MUST NOT include the null character (Unicode U+0000).
 * [MQTT-4.7.3-3] Topic Names and Topic Filters are UTF-8 Encoded Strings; they MUST NOT encode to more than 65,535 bytes.
 *
 *
 * I'm not sure how to test this since it's a negative:
 *
 * [MQTT-4.7.3-4] When it performs subscription matching the Server MUST NOT perform any normalization of Topic Names or Topic Filters, or any modification or substitution of unrecognized characters.
 */

public class TopicNameAndFilterTests extends MQTT5TestSupport {

   /*
    * [MQTT-4.7.2-1] The Server MUST NOT match Topic Filters starting with a wildcard character (# or +) with Topic
    * Names beginning with a $ character.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testMatchingHash() throws Exception {
      testMatchingWildcard("#");
   }

   /*
    * [MQTT-4.7.2-1] The Server MUST NOT match Topic Filters starting with a wildcard character (# or +) with Topic
    * Names beginning with a $ character.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testMatchingPlus() throws Exception {
      testMatchingWildcard("+");
   }

   private void testMatchingWildcard(String wildcard) throws Exception {
      final CountDownLatch latch = new CountDownLatch(1);

      MqttClient consumer = createPahoClient("consumer");
      consumer.connect();
      consumer.setCallback(new DefaultMqttCallback() {
         @Override
         public void messageArrived(String incomingTopic, MqttMessage message) throws Exception {
            latch.countDown();
         }
      });
      consumer.subscribe(wildcard, 0);

      final SimpleString DOLLAR_TOPIC = SimpleString.of("$foo");
      ServerLocator locator = ActiveMQClient.createServerLocator("vm://0");
      ClientSessionFactory csf = locator.createSessionFactory();
      ClientSession s = csf.createSession();
      s.createAddress(DOLLAR_TOPIC, EnumSet.allOf(RoutingType.class), false);
      s.createProducer(DOLLAR_TOPIC).send(s.createMessage(true));

      assertFalse(latch.await(2, TimeUnit.SECONDS));
   }
}
