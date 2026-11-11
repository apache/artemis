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
package org.apache.activemq.artemis.core.protocol.core.impl.wireformat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.junit.jupiter.api.Test;

public class ConnectResponseMessageTest {

   static final int TEST_VERSION = 456;

   @Test
   public void testRoundTripWithServerVersion() {
      ConnectResponseMessage encoded = new ConnectResponseMessage(true, TEST_VERSION);

      ConnectResponseMessage decoded = roundTrip(encoded);

      assertTrue(decoded.isOkToFailover());
      assertEquals(TEST_VERSION, decoded.getServerVersion());
   }

   @Test
   public void testRoundTripWithoutServerVersion() {
      ConnectResponseMessage encoded = new ConnectResponseMessage(false, 0);

      ConnectResponseMessage decoded = roundTrip(encoded);

      assertFalse(decoded.isOkToFailover());
      assertEquals(0, decoded.getServerVersion());
   }

   @Test
   public void testDecodeIgnoresUnknownFutureSections() {
      ConnectResponseMessage encoded = new ConnectResponseMessage(true, TEST_VERSION);
      ActiveMQBuffer buffer = ActiveMQBuffers.dynamicBuffer(256);
      encoded.encodeRest(buffer);
      buffer.writeByte((byte) 99);
      buffer.writeInt(42);

      ConnectResponseMessage decoded = new ConnectResponseMessage();
      decoded.decodeRest(buffer);

      assertTrue(decoded.isOkToFailover());
      assertEquals(TEST_VERSION, decoded.getServerVersion());
   }

   private static ConnectResponseMessage roundTrip(ConnectResponseMessage encoded) {
      ActiveMQBuffer buffer = ActiveMQBuffers.dynamicBuffer(256);
      encoded.encodeRest(buffer);

      ConnectResponseMessage decoded = new ConnectResponseMessage();
      decoded.decodeRest(buffer);
      return decoded;
   }
}
