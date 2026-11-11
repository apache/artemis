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
package org.apache.activemq.artemis.core.server.protocol.websocket;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

/**
 * This class uses the maximum frame payload size to packetize/frame outbound websocket messages into continuation
 * frames.
 */
public class WebSocketFrameEncoder extends ChannelOutboundHandlerAdapter {

   private int maxFramePayloadLength;
   private WebSocketFrameEncoderType type;

   public WebSocketFrameEncoder(int maxFramePayloadLength, WebSocketFrameEncoderType type) {
      this.maxFramePayloadLength = maxFramePayloadLength;
      this.type = type;
   }

   @Override
   public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
      if (msg instanceof ByteBuf buf) {
         writeContinuationFrame(ctx, buf, promise);
      } else {
         super.write(ctx, msg, promise);
      }
   }

   private void writeContinuationFrame(ChannelHandlerContext ctx, ByteBuf byteBuf, ChannelPromise promise) {
      int count = byteBuf.readableBytes();
      int length = Math.min(count, maxFramePayloadLength);
      boolean finalFragment = length == count;
      ByteBuf fragment = Unpooled.buffer(length);
      byteBuf.readBytes(fragment, length);
      if (type == WebSocketFrameEncoderType.BINARY) {
         ctx.writeAndFlush(new BinaryWebSocketFrame(finalFragment, 0, fragment), promise);
      } else {
         ctx.writeAndFlush(new TextWebSocketFrame(finalFragment, 0, fragment), promise);
      }

      while ((count = byteBuf.readableBytes()) > 0) {
         length = Math.min(count, maxFramePayloadLength);
         finalFragment = length == count;
         fragment = Unpooled.buffer(length);
         byteBuf.readBytes(fragment, length);
         ctx.writeAndFlush(new ContinuationWebSocketFrame(finalFragment, 0, fragment), promise);
      }

      byteBuf.release();
   }
}