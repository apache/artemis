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
 * Fulfilled by client or Netty codec (i.e. not tested here):
 *
 * [MQTT-4.6.0-1] When the Client re-sends any PUBLISH packets, it MUST re-send them in the order in which the original PUBLISH packets were sent (this applies to QoS 1 and QoS 2 messages).
 * [MQTT-4.6.0-2] The Client MUST send PUBACK packets in the order in which the corresponding PUBLISH packets were received (QoS 1 messages).
 * [MQTT-4.6.0-3] The Client MUST send PUBREC packets in the order in which the corresponding PUBLISH packets were received (QoS 2 messages).
 * [MQTT-4.6.0-4] The Client MUST send PUBREL packets in the order in which the corresponding PUBREC packets were received (QoS 2 messages).
 *
 *
 * Message order is one of the fundamental semantics of a queue and since subscriptions are queues the messages are therefore ordered implicitly so I'm not testing these here.
 *
 * [MQTT-4.6.0-5] When a Server processes a message that has been published to an Ordered Topic, it MUST send PUBLISH packets to consumers (for the same Topic and QoS) in the order that they were received from any given Client.
 * [MQTT-4.6.0-6] A Server MUST treat every, Topic as an Ordered Topic when it is forwarding messages on Non‑shared Subscriptions.
 */

@Disabled
public class MessageOrderingTests extends MQTT5TestSupport {
}
