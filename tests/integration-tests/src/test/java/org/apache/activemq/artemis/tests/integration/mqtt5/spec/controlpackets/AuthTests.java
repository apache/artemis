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

import org.apache.activemq.artemis.tests.integration.mqtt5.MQTT5TestSupport;
import org.junit.jupiter.api.Disabled;

/*
 * Fulfilled by client or Netty codec (i.e. not tested here):
 *
 * [MQTT-3.15.1-1] Bits 3,2,1 and 0 of the Fixed Header of the AUTH packet are reserved and MUST all be set to 0. The Client or Server MUST treat any other value as malformed and close the Network Connection.
 *
 *
 * The broker doesn't send any "Reason String" or "User Property" in the AUTH packet for any reason. Therefore, these are not tested here:
 *
 * [MQTT-3.15.2-2] The sender MUST NOT send this property if it would increase the size of the AUTH packet beyond the Maximum Packet Size specified by the receiver
 * [MQTT-3.15.2-3] The sender MUST NOT send this property if it would increase the size of the AUTH packet beyond the Maximum Packet Size specified by the receiver.
 *
 *
 * Not implemented.
 *
 * [MQTT-3.15.2-1] The sender of the AUTH Packet MUST use one of the Authenticate Reason Codes.
 */

@Disabled
public class AuthTests extends MQTT5TestSupport {
}
