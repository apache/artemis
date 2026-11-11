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
package org.apache.activemq.artemis.core.remoting.impl.netty;

import java.nio.ByteOrder;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.util.AttributeKey;

/**
 * Decoder that enforces a variable maximum frame size limit
 */
public class CoreFrameWithSizeLimitDecoder extends ActiveMQFrameDecoder2 {

   public static final AttributeKey<Integer> FRAME_SIZE_LIMIT =
      AttributeKey.valueOf("frameSizeLimit");

   private final int initialFrameSizeLimit;

   private Channel channel;

   public CoreFrameWithSizeLimitDecoder(final int initialFrameSizeLimit) {
      this.initialFrameSizeLimit = initialFrameSizeLimit;
   }

   public static void setFrameSizeLimit(final Channel channel, final int frameSizeLimit) {
      channel.attr(FRAME_SIZE_LIMIT).set(frameSizeLimit);
   }

   private static int getFrameSizeLimit(final Channel channel) {
      final Integer limit = channel.attr(FRAME_SIZE_LIMIT).get();
      return limit == null ? 0 : limit;
   }

   @Override
   public void handlerAdded(final ChannelHandlerContext ctx) throws Exception {
      channel = ctx.channel();
      setFrameSizeLimit(channel, initialFrameSizeLimit);
      super.handlerAdded(ctx);
   }

   @Override
   protected long getUnadjustedFrameLength(final ByteBuf buf, final int offset, final int length, final ByteOrder order) {
      final long unadjustedFrameLength = super.getUnadjustedFrameLength(buf, offset, length, order);
      final int frameSizeLimit = getFrameSizeLimit(channel);
      if (frameSizeLimit > 0 && unadjustedFrameLength > frameSizeLimit) {
         throw new TooLongFrameException("Frame size exceeded: " + unadjustedFrameLength);
      }
      return unadjustedFrameLength;
   }
}
