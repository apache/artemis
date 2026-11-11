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
package org.apache.activemq.artemis.tests.integration.mqtt;

import org.fusesource.mqtt.client.BlockingConnection;
import org.fusesource.mqtt.client.MQTT;
import org.fusesource.mqtt.client.Message;
import org.fusesource.mqtt.client.QoS;
import org.fusesource.mqtt.client.Topic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class MQTTMaxInFlightPublishesTest extends MQTTTestSupport {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Test
   @Timeout(60)
   public void testCustomDefaultMaximumInFlightPublishMessages() throws Exception {
      final MQTT subscriberClient = createMQTTConnection("MQTT-Sub-Client", false);
      final MQTT publisherClient = createMQTTConnection("MQTT-Pub-Client", false);

      BlockingConnection subscriberConn = subscriberClient.blockingConnection();
      subscriberConn.connect();

      String topic = "TopicA";
      subscriberConn.subscribe(new Topic[]{new Topic(topic, QoS.AT_LEAST_ONCE)});

      BlockingConnection publisherConn = publisherClient.blockingConnection();
      publisherConn.connect();

      publisherConn.publish(topic, new byte[]{1}, QoS.AT_LEAST_ONCE, false);
      publisherConn.publish(topic, new byte[]{2}, QoS.AT_LEAST_ONCE, false);
      publisherConn.publish(topic, new byte[]{3}, QoS.AT_LEAST_ONCE, false);
      publisherConn.publish(topic, new byte[]{4}, QoS.AT_LEAST_ONCE, false);

      Message msg1 = subscriberConn.receive();
      assertEquals(1, msg1.getPayload()[0]);
      Message msg2 = subscriberConn.receive();
      assertEquals(2, msg2.getPayload()[0]);

      Message msg3 = subscriberConn.receive(100, TimeUnit.MILLISECONDS);
      assertNull(msg3);

      msg1.ack();
      msg3 = subscriberConn.receive();
      assertEquals(3, msg3.getPayload()[0]);

      Message msg4 = subscriberConn.receive(100, TimeUnit.MILLISECONDS);
      assertNull(msg4);

      msg3.ack();
      msg4 = subscriberConn.receive();
      assertEquals(4, msg4.getPayload()[0]);


      subscriberConn.disconnect();
      publisherConn.disconnect();

   }

   @Override
   protected void addMQTTConnector() throws Exception {
      server.getConfiguration().addAcceptorConfiguration("MQTT", "tcp://localhost:" + port + "?protocols=MQTT;anycastPrefix=anycast:;multicastPrefix=multicast:;defaultMaximumInFlightPublishMessages=2");

      logger.debug("Added MQTT connector to broker");
   }
}
