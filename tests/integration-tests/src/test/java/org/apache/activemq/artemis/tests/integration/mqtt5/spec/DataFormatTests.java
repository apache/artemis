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
 * Fulfilled by client or Netty codec (i.e. not explicitly tested here):
 *
 * [MQTT-1.5.4-1] The character data in a UTF-8 Encoded String MUST be well-formed UTF-8 as defined by the Unicode specification [Unicode] and restated in RFC 3629 [RFC3629]. In particular, the character data MUST NOT include encodings of code points between U+D800 and U+DFFF.
 * [MQTT-1.5.4-2] A UTF-8 Encoded String MUST NOT include an encoding of the null character U+0000.
 * [MQTT-1.5.4-3] A UTF-8 encoded sequence 0xEF 0xBB 0xBF is always interpreted as U+FEFF ("ZERO WIDTH NO-BREAK SPACE") wherever it appears in a string and MUST NOT be skipped over or stripped off by a packet receiver.
 * [MQTT-1.5.5-1] The encoded value MUST use the minimum number of bytes necessary to represent the value.
 * [MQTT-1.5.7-1] Both strings MUST comply with the requirements for UTF-8 Encoded Strings.
 */

@Disabled
public class DataFormatTests extends MQTT5TestSupport {
}
