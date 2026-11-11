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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import org.apache.activemq.artemis.utils.DataConstants;

/**
 * A Netty decoder specially optimised to to decode messages on the core protocol only
 */
public class ActiveMQFrameDecoder2 extends LengthFieldBasedFrameDecoder {

   public ActiveMQFrameDecoder2() {
      super(Integer.MAX_VALUE, 0, DataConstants.SIZE_INT);
   }

   @Override
   protected ByteBuf extractFrame(ChannelHandlerContext ctx, ByteBuf buffer, int index, int length) {
      // This is a work around on https://github.com/netty/netty/commit/55fbf007f04fbba7bf50028f3c8b35d6c5ea5947
      // Right now we need a copy when sending a message on the server otherwise messages won't be resent to the client
      ByteBuf frame = ctx.alloc().buffer(length);
      frame.writeBytes(buffer, index, length);
      return frame.skipBytes(DataConstants.SIZE_INT);
   }
}
