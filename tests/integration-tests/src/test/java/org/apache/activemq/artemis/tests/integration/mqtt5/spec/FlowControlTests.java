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
 * The MQTT 5 specification discusses a "send quota," but this is really an implementation detail and therefore not explicitly tested here:
 *
 * [MQTT-4.9.0-1] The Client or Server MUST set its initial send quota to a non-zero value not exceeding the Receive Maximum.
 * [MQTT-4.9.0-2] Each time the Client or Server sends a PUBLISH packet at QoS > 0, it decrements the send quota. If the send quota reaches zero, the Client or Server MUST NOT send any more PUBLISH packets with QoS > 0.
 *
 *
 * This is tested in org.apache.activemq.artemis.tests.integration.mqtt5.spec.controlpackets.PublishTests#testPacketDelayReceiveMaximum():
 *
 * [MQTT-4.9.0-3] The Client and Server MUST continue to process and respond to all other MQTT Control Packets even if the quota is zero.
 */

@Disabled
public class FlowControlTests extends MQTT5TestSupport {
}
