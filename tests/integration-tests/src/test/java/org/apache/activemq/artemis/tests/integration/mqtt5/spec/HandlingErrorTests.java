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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import org.apache.activemq.artemis.core.protocol.mqtt.MQTTReasonCodes;
import org.apache.activemq.artemis.tests.integration.mqtt5.MQTT5TestSupport;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptionsBuilder;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/*
 * Fulfilled by client or Netty codec (i.e. not tested here):
 *
 * [MQTT-4.13.1-1] When a Server detects a Malformed Packet or Protocol Error, and a Reason Code is given in the specification, it MUST close the Network Connection.
 */

public class HandlingErrorTests extends MQTT5TestSupport {

   /**
    * [MQTT-4.13.2-1] The CONNACK and DISCONNECT packets allow a Reason Code of 0x80 or greater to indicate that the
    * Network Connection will be closed. If a Reason Code of 0x80 or greater is specified, then the Network Connection
    * MUST be closed whether the CONNACK or DISCONNECT is sent.
    *
    * This is one possible error condition where a Reason Code > 0x80 is specified and the network connection is closed.
    */
   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testEmptyClientIDWithoutCleanStart() throws Exception {
      MqttClient client = createPahoClient("");
      MqttConnectionOptions options = new MqttConnectionOptionsBuilder()
         .cleanStart(false)
         .build();
      try {
         client.connect(options);
         fail("Should throw exception about invalid client identifier");
      } catch (MqttException e) {
         assertEquals(Byte.toUnsignedInt(MQTTReasonCodes.CLIENT_IDENTIFIER_NOT_VALID), e.getReasonCode());
      }

      assertFalse(client.isConnected());
      assertEquals(0, getSessionStates().size());
   }
}
