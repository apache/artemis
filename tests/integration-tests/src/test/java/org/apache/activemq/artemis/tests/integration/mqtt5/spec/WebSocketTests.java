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

import org.apache.activemq.artemis.tests.integration.mqtt5.MQTT5TestSupport;
import org.junit.jupiter.api.Disabled;

/*
 * Fulfilled by client (i.e. not tested here):
 *
 * [MQTT-6.0.0-1] MQTT Control Packets MUST be sent in WebSocket binary data frames. If any other type of data frame is received the recipient MUST close the Network Connection.
 * [MQTT-6.0.0-3] The Client MUST include “mqtt” in the list of WebSocket Sub Protocols it offers.
 *
 *
 * Fulfilled by the Netty codec on broker (i.e. not explicitly tested here):
 *
 * [MQTT-6.0.0-2] A single WebSocket data frame can contain multiple or partial MQTT Control Packets. The receiver MUST NOT assume that MQTT Control Packets are aligned on WebSocket frame boundaries.
 *
 *
 * This is tested implicitly as almost all tests are run using both TCP and WebSocket connections. The subprotocol is defined in org.apache.activemq.artemis.core.protocol.mqtt.MQTTProtocolManager#websocketRegistryNames:
 *
 * [MQTT-6.0.0-4] The WebSocket Subprotocol name selected and returned by the Server MUST be “mqtt”.
 */

@Disabled
public class WebSocketTests extends MQTT5TestSupport {
}
