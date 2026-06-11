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

package org.apache.activemq.artemis.protocol.amqp.broker;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.utils.collections.TypedProperties;

/**
 * Temporary holder for AMQP message metadata during decoding.
 * <p>
 * When decoding metadata from {@link AMQPMessageMetadataPersister}, certain values
 * (e.g., messageID on large messages and messageFormat on regular messages) are needed before the Message
 * object can be created. This class stores those values in a ThreadLocal until the
 * Message is instantiated and the metadata can be applied.
 */
public class AMQPMetadataDecodingState {

   protected long messageID; // always present
   protected long messageFormat; // always present
   protected long messageExpiration; // always present
   protected int memoryEstimate; // always present
   protected byte priority; // always present
   protected boolean isDurable; // always present
   protected SimpleString address; // variable size based on the string size
   protected TypedProperties extraProperties; // variable size based on the byte array size
   protected boolean isReencoded; // used on large messages only

   public AMQPMetadataDecodingState() {
   }




}
