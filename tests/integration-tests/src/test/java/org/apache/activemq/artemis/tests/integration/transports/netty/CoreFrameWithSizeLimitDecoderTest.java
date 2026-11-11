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
package org.apache.activemq.artemis.tests.integration.transports.netty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.TooLongFrameException;
import org.apache.activemq.artemis.core.protocol.core.impl.CoreProtocolManager;
import org.apache.activemq.artemis.core.remoting.impl.netty.CoreFrameWithSizeLimitDecoder;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.DataConstants;
import org.junit.jupiter.api.Test;

public class CoreFrameWithSizeLimitDecoderTest extends ActiveMQTestBase {

   private static final int FRAME_PAYLOAD_SIZE = CoreProtocolManager.CORE_INITIAL_MAX_FRAME_SIZE + 1;

   @Test
   public void testRejectsOversizedFrameBeforeAuthentication() {
      final EmbeddedChannel decoder = new EmbeddedChannel(new CoreFrameWithSizeLimitDecoder(CoreProtocolManager.CORE_INITIAL_MAX_FRAME_SIZE));

      assertThrows(TooLongFrameException.class, () -> decoder.writeInbound(frameBuffer(FRAME_PAYLOAD_SIZE)));
   }

   @Test
   public void testRejectsOversizedFrameFromLengthPrefixOnly() {
      final EmbeddedChannel decoder = new EmbeddedChannel(new CoreFrameWithSizeLimitDecoder(CoreProtocolManager.CORE_INITIAL_MAX_FRAME_SIZE));

      final ByteBuf lengthPrefixOnly = Unpooled.buffer(DataConstants.SIZE_INT);
      lengthPrefixOnly.writeInt(FRAME_PAYLOAD_SIZE);

      assertThrows(TooLongFrameException.class, () -> decoder.writeInbound(lengthPrefixOnly));
      assertNull(decoder.readInbound());
   }

   @Test
   public void testAcceptsLargerFrameAfterLimitRaised() throws Exception {
      final EmbeddedChannel decoder = new EmbeddedChannel(new CoreFrameWithSizeLimitDecoder(CoreProtocolManager.CORE_INITIAL_MAX_FRAME_SIZE));

      CoreFrameWithSizeLimitDecoder.setFrameSizeLimit(decoder, CoreProtocolManager.CORE_MAX_FRAME_SIZE);

      decoder.writeInbound(frameBuffer(FRAME_PAYLOAD_SIZE));

      final ByteBuf frame = decoder.readInbound();
      assertNotNull(frame);
      assertEquals(FRAME_PAYLOAD_SIZE, frame.readableBytes());
      frame.release();
      assertNull(decoder.readInbound());
   }

   private static ByteBuf frameBuffer(final int payloadSize) {
      final ByteBuf buffer = Unpooled.buffer(4 + payloadSize);
      buffer.writeInt(payloadSize);
      buffer.writeZero(payloadSize);
      return buffer;
   }
}
