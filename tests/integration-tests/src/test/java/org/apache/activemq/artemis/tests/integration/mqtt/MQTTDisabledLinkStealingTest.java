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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.fusesource.mqtt.client.BlockingConnection;
import org.fusesource.mqtt.client.MQTT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class MQTTDisabledLinkStealingTest extends MQTTTestSupport {

   @Test
   @Timeout(60)
   public void testDisabledLinkStealing() throws Exception {
      final String clientId = RandomUtil.randomUUIDString();
      MQTT mqtt = createMQTTConnection(clientId, false);
      mqtt.setKeepAlive((short) 2);

      final BlockingConnection connection1 = mqtt.blockingConnection();
      connection1.connect();

      final BlockingConnection connection2 = mqtt.blockingConnection();
      try {
         connection2.connect();
         fail("Should have thrown an exception on connect due to disabled link stealing");
      } catch (Exception e) {
         // ignore expected exception
      }

      assertTrue(Wait.waitFor(() -> connection1.isConnected(), 3000, 200), "Client no longer connected!");
      connection1.disconnect();
   }

   @Override
   protected void addMQTTConnector() throws Exception {
      server.getConfiguration().addAcceptorConfiguration("MQTT", "tcp://localhost:" + port + "?protocols=MQTT;anycastPrefix=anycast:;multicastPrefix=multicast:;allowLinkStealing=false");
   }
}
