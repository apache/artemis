/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.tests.integration.mqtt;

import java.util.concurrent.CountDownLatch;

import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTInterceptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

public class MQTTRejectingInterceptorTest extends MQTTTestSupport {

   @Test
   @Timeout(60)
   public void testRejectedMQTTMessage() throws Exception {
      final String addressQueue = name;
      final String msgText = "Test rejected message";

      final MQTTClientProvider subscribeProvider = getMQTTClientProvider();
      initializeConnection(subscribeProvider);
      subscribeProvider.subscribe(addressQueue, AT_MOST_ONCE);

      MQTTInterceptor incomingInterceptor = (packet, connection) -> {
         if (packet.getClass() == MqttPublishMessage.class) {
            return false;
         } else {
            return true;
         }
      };

      server.getRemotingService().addIncomingInterceptor(incomingInterceptor);

      final MQTTClientProvider publishProvider = getMQTTClientProvider();
      initializeConnection(publishProvider);
      publishProvider.publish(addressQueue, msgText.getBytes(), AT_MOST_ONCE, false);
      assertNull(subscribeProvider.receive(3000));

      subscribeProvider.disconnect();
      publishProvider.disconnect();
   }

   @Test
   @Timeout(60)
   public void testRejectedMqttConnectMessage() throws Exception {
      CountDownLatch publishThreadReady = new CountDownLatch(1);

      server.getRemotingService().addIncomingInterceptor((MQTTInterceptor) (packet, connection) -> {
         if (packet.getClass() == MqttConnectMessage.class) {
            return false;
         } else {
            return true;
         }
      });

      Thread publishThread = new Thread(() -> {
         MQTTClientProvider publishProvider = getMQTTClientProvider();

         publishThreadReady.countDown();

         try {
            initializeConnection(publishProvider);
            publishProvider.disconnect();
            fail("The connection should be rejected!");
         } catch (Exception ignore) {
         }
      });

      publishThread.start();

      publishThreadReady.await();

      publishThread.join(3000);

      if (publishThread.isAlive()) {
         fail("The connection is stuck!");
      }
   }
}
